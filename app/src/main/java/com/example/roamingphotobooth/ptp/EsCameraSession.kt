package com.example.roamingphotobooth.ptp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.util.Log
import androidx.core.content.ContextCompat
import com.extremesolution.esptpcamera.Camera
import com.extremesolution.esptpcamera.PtpConstants
import com.extremesolution.esptpcamera.PtpService
import com.extremesolution.esptpcamera.model.LiveViewData
import com.example.roamingphotobooth.camera.CameraBackend
import java.io.ByteArrayOutputStream

/**
 * Pembungkus tunggal di atas library **es-ptp-camera**
 * (https://github.com/ReemMousaES/es-ptp-camera, JitPack: v1.0.3) — menggantikan
 * implementasi PTP native/libusb kustom sebelumnya (PtpDeviceManager +
 * PtpNativeConnection + PtpSessionManager).
 *
 * Beda utama dari versi lama:
 * - Komunikasi USB pakai UsbManager/UsbDeviceConnection bawaan Android (bukan
 *   libusb via JNI) — library sudah mengurus semua handshake sesi PTP (termasuk
 *   PC-mode Canon EOS) dan retry-nya sendiri.
 * - Mendukung Canon EOS *dan* Nikon otomatis (deteksi vendor ID di dalam
 *   library), tidak perlu cabang kode terpisah di sisi app.
 * - Live view & hasil capture datang sebagai [Bitmap] yang sudah di-decode
 *   library sendiri (bukan ByteArray mentah) — untuk live view kita langsung
 *   pakai Bitmap-nya, untuk hasil capture kita encode ulang ke JPEG supaya
 *   tetap kompatibel dengan [TemplateSessionManager.addPhoto] dan alur
 *   simpan/upload lain di app yang masih berbasis ByteArray.
 *
 * Dipakai dari 1 Activity (bukan Singleton bebas) — buat & `shutdown()` sesuai
 * lifecycle Activity yang memilikinya.
 */
class EsCameraSession(private val context: Context) : Camera.CameraListener, CameraBackend {

    companion object {
        private const val TAG = "EsCameraSession"

        // Live view di-poll manual (bukan lewat event USB) dengan pola
        // double-buffering seperti dicontohkan di README library — jeda kecil
        // di sini (~50ms => ~20fps) supaya tidak membanjiri device dengan
        // request berikutnya sebelum frame sebelumnya selesai diproses.
        private const val LIVE_VIEW_POLL_INTERVAL_MS = 50L

        // Kualitas JPEG saat hasil capture (Bitmap dari library) di-encode ulang
        // jadi ByteArray untuk diteruskan ke TemplateSessionManager/BitmapMerger.
        private const val CAPTURE_JPEG_QUALITY = 95

        // <-- BARU: watchdog live view. Kalau app ditinggal idle cukup lama (layar
        // Android mati sendiri karena screen timeout, sebagian device ikut memotong
        // daya USB-OTG / membatasi CPU lewat Doze saat itu, dll), loop polling live
        // view (lihat onLiveViewData di bawah) bisa diam total TANPA ada event
        // error/detach apa pun dari library -- device dianggap "masih ada" tapi
        // tidak pernah merespons lagi. Watchdog ini jalan terus di latar belakang,
        // mendeteksi sendiri kalau tidak ada frame baru dalam beberapa detik, dan
        // coba pulihkan otomatis -- supaya operator tidak perlu cabut-pasang kabel
        // kamera atau restart app tiap kali balik dari idle.
        private const val WATCHDOG_CHECK_INTERVAL_MS = 4_000L

        // Live view idle lebih lama dari ini -> mulai dianggap macet, coba
        // percobaan "lunak" dulu: re-issue command live view (kemungkinan cuma
        // polling chain-nya yang berhenti, bukan sesi USB-nya).
        private const val WATCHDOG_STALE_THRESHOLD_MS = 8_000L

        // Masih idle setelah percobaan lunak berjalan selama ini -> dianggap sesi
        // USB-nya sendiri yang mati, eskalasi ke reconnect "keras" (buka ulang sesi
        // PTP dari awal, cari device kompatibel yang masih attached).
        private const val WATCHDOG_HARD_RECONNECT_AFTER_MS = 16_000L
    }

    private val ptpService: PtpService = PtpService.Singleton.getInstance(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var camera: Camera? = null
    private var liveViewActive = false
    private var currentLiveViewData: LiveViewData? = null
    private var previousLiveViewData: LiveViewData? = null

    // <-- BARU: state watchdog live view (lihat runWatchdogCheck()).
    private var lastLiveViewFrameAt: Long = 0L
    private var lastReconnectAttemptAt: Long = 0L
    private var watchdogCancelled = false

    /** Kamera siap dipakai (sesi PTP terbuka) — live view otomatis dimulai setelah ini. */
    override var onSessionReady: (() -> Unit)? = null

    /** Error sesi PTP/USB (device error, permission ditolak, dll). */
    override var onSessionError: ((String) -> Unit)? = null

    /** 1 frame live view baru siap ditampilkan. */
    override var onLiveViewFrame: ((Bitmap) -> Unit)? = null

    /** Foto baru selesai di-retrieve dari kamera, di-encode sebagai JPEG bytes. */
    override var onNewPhotoCaptured: ((ByteArray) -> Unit)? = null

    /** capturePhoto() gagal dikirim, atau hasil retrieve-nya gagal/korup. */
    override var onCaptureFailed: (() -> Unit)? = null

    /** Kamera (fisik) dicabut dari USB. */
    override var onDeviceDetached: (() -> Unit)? = null

    private var isListeningDetach = false

    // Deteksi cabut kabel: library sendiri baru "sadar" device hilang saat sebuah
    // transfer USB gagal (bisa telat beberapa detik) — broadcast detach system ini
    // dipakai supaya UI langsung dapat feedback saat itu juga.
    private val detachReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != UsbManager.ACTION_USB_DEVICE_DETACHED) return
            val device: UsbDevice? = intent.getParcelableExtraCompat(UsbManager.EXTRA_DEVICE)
            if (device != null && !PtpConstants.isCompatibleVendor(device.vendorId)) return
            Log.i(TAG, "Kamera terputus (broadcast detach)")
            liveViewActive = false
            camera = null
            onDeviceDetached?.invoke()
        }
    }

    init {
        ptpService.setCameraListener(this)
        registerDetachReceiver()
        scheduleWatchdogCheck()
    }

    private fun registerDetachReceiver() {
        if (isListeningDetach) return
        val filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(detachReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            ContextCompat.registerReceiver(context, detachReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        }
        isListeningDetach = true
    }

    /**
     * Panggil dari `onCreate()` (dengan `intent` Activity) dan `onNewIntent()`.
     * Kalau `intent` membawa EXTRA_DEVICE (device baru saja dicolok & cocok
     * dengan device_filter.xml), library langsung connect ke situ. Kalau tidak
     * (mis. app dibuka manual & kamera sudah lebih dulu tersambung), library
     * mencari device kompatibel yang sedang attached lalu meminta izin USB.
     */
    fun initialize(intent: Intent) {
        registerDetachReceiver()
        ptpService.initialize(context, intent, true)
    }

    // ============================== Watchdog live view ==============================
    // Lihat catatan panjang di companion object (WATCHDOG_*) soal kenapa ini ada:
    // ringkasnya, live view bisa diam total tanpa event error/detach apa pun dari
    // library kalau app ditinggal idle lama (layar mati sendiri, USB power dipotong
    // OS, dll). Loop ini jalan terus selama instance ini hidup dan mendeteksi +
    // memulihkan kondisi itu sendiri.

    private fun scheduleWatchdogCheck() {
        mainHandler.postDelayed({
            if (watchdogCancelled) return@postDelayed
            runWatchdogCheck()
            scheduleWatchdogCheck()
        }, WATCHDOG_CHECK_INTERVAL_MS)
    }

    private fun runWatchdogCheck() {
        if (!liveViewActive) {
            // Live view memang belum/tidak aktif (mis. belum ada kamera tersambung,
            // baru saja detach, atau baru saja di-request reconnect) -- bukan
            // tanggung jawab watchdog, biarkan alur normal (onCameraStarted /
            // onDeviceDetached / onError) yang urus.
            return
        }

        val now = System.currentTimeMillis()
        val idleMs = now - lastLiveViewFrameAt
        if (idleMs < WATCHDOG_STALE_THRESHOLD_MS) return // masih wajar

        // Kasih jeda antar-percobaan reconnect (jangan spam tiap 4 detik selagi
        // masih menunggu hasil percobaan sebelumnya).
        if (now - lastReconnectAttemptAt < WATCHDOG_CHECK_INTERVAL_MS) return
        lastReconnectAttemptAt = now

        if (idleMs < WATCHDOG_HARD_RECONNECT_AFTER_MS) {
            Log.w(TAG, "Watchdog: live view idle ${idleMs}ms -- coba restart polling (soft retry)")
            onSessionError?.invoke("Live view macet, mencoba menyambungkan ulang...")
            // Kemungkinan cuma chain polling onLiveViewData yang berhenti (mis.
            // postDelayed sempat tertunda lama saat CPU dibatasi Doze), bukan
            // sesi USB-nya -- coba cara paling murah dulu: re-issue live view.
            camera?.setLiveView(true)
        } else {
            Log.w(TAG, "Watchdog: live view idle ${idleMs}ms -- reconnect penuh (hard retry)")
            onSessionError?.invoke("Live view masih macet, membuka ulang sesi kamera...")
            // Sesi USB dianggap mati (percobaan soft di atas tidak memulihkan
            // apa pun dalam WATCHDOG_HARD_RECONNECT_AFTER_MS) -- buka ulang dari
            // awal. Intent kosong = sama seperti app dibuka manual & kamera sudah
            // lebih dulu tersambung (lihat dokumentasi initialize() di atas):
            // library akan mencari device kompatibel yang masih attached & minta
            // izin USB lagi, TANPA perlu user cabut-pasang kabel.
            liveViewActive = false
            camera = null
            ptpService.initialize(context, Intent(), true)
        }
    }

    /** Kirim command jepret ke kamera yang sedang aktif. Return false kalau belum ada kamera. */
    override fun capturePhoto(): Boolean {
        val cam = camera
        if (cam == null) {
            Log.w(TAG, "capturePhoto() dipanggil tapi belum ada kamera aktif")
            return false
        }
        cam.capture()
        return true
    }

    /** Tutup sesi & lepas kamera. Panggil dari onDestroy/onDetached handler pemanggil. */
    fun closeSession() {
        liveViewActive = false
        currentLiveViewData = null
        previousLiveViewData = null
        camera = null
        ptpService.shutdown()
    }

    /** Panggil dari onDestroy Activity supaya BroadcastReceiver tidak bocor. */
    override fun release() {
        watchdogCancelled = true
        closeSession()
        if (isListeningDetach) {
            try {
                context.unregisterReceiver(detachReceiver)
            } catch (e: IllegalArgumentException) {
                // Sudah tidak terdaftar, aman diabaikan.
            }
            isListeningDetach = false
        }
    }

    private fun encodeCaptureToJpeg(bitmap: Bitmap): ByteArray? {
        return try {
            ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, CAPTURE_JPEG_QUALITY, stream)
                stream.toByteArray()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal encode hasil capture ke JPEG: ${e.message}", e)
            null
        }
    }

    // ============================== Camera.CameraListener ==============================

    override fun onCameraStarted(camera: Camera) {
        Log.i(TAG, "Sesi PTP terbuka. Kamera: ${camera.deviceName}")
        this.camera = camera
        onSessionReady?.invoke()
        // Live view dimulai otomatis begitu sesi siap — sama seperti perilaku
        // PtpSessionManager versi lama (session.onSessionReady { startLiveView() }).
        camera.setLiveView(true)
    }

    override fun onCameraStopped(camera: Camera) {
        Log.i(TAG, "Sesi PTP ditutup")
        this.camera = null
        liveViewActive = false
    }

    override fun onNoCameraFound() {
        Log.i(TAG, "Tidak ada kamera kompatibel terdeteksi")
    }

    override fun onCameraFound(device: UsbDevice) {
        Log.i(TAG, "Kamera ditemukan, menunggu izin USB: ${device.deviceName}")
    }

    override fun onError(message: String) {
        Log.e(TAG, "PTP error: $message")
        liveViewActive = false
        onSessionError?.invoke(message)
    }

    override fun onPropertyChanged(property: Int, value: Int) {
        // Tidak dipakai di alur photobooth (tidak ada UI kontrol ISO/aperture/dst).
    }

    override fun onPropertyStateChanged(property: Int, enabled: Boolean) {
        // idem
    }

    override fun onPropertyDescChanged(property: Int, values: IntArray) {
        // idem
    }

    override fun onLiveViewStarted() {
        liveViewActive = true
        currentLiveViewData = null
        previousLiveViewData = null
        // Reset watchdog: kasih grace period penuh dari titik ini, bukan dari
        // kapan instance ini dibuat -- supaya tidak false-positive kalau live
        // view baru mulai (belum sempat dapat frame pertama) pas watchdog check
        // kebetulan jalan.
        lastLiveViewFrameAt = System.currentTimeMillis()
        camera?.getLiveViewPicture(null) // mulai polling
    }

    override fun onLiveViewData(data: LiveViewData?) {
        if (!liveViewActive) return

        if (data == null) {
            camera?.getLiveViewPicture(previousLiveViewData)
            return
        }

        // <-- BARU: frame (walau kosong/data non-null) berarti polling chain masih
        // hidup & device masih merespons -- watchdog boleh tenang lagi.
        lastLiveViewFrameAt = System.currentTimeMillis()

        data.bitmap?.let { onLiveViewFrame?.invoke(it) }

        // Double-buffering: buffer yang barusan jadi "previous" (dipakai ulang
        // oleh library di request berikutnya), sesuai pola contoh di README.
        previousLiveViewData = currentLiveViewData
        currentLiveViewData = data

        mainHandler.postDelayed({
            if (liveViewActive) camera?.getLiveViewPicture(previousLiveViewData)
        }, LIVE_VIEW_POLL_INTERVAL_MS)
    }

    override fun onLiveViewStopped() {
        liveViewActive = false
    }

    override fun onCapturedPictureReceived(
        objectHandle: Int,
        filename: String,
        thumbnail: Bitmap?,
        bitmap: Bitmap?,
        orientation: Int
    ) {
        if (bitmap == null) {
            Log.w(TAG, "onCapturedPictureReceived: bitmap null untuk handle=$objectHandle")
            onCaptureFailed?.invoke()
            return
        }
        val bytes = encodeCaptureToJpeg(bitmap)
        if (bytes == null) {
            onCaptureFailed?.invoke()
            return
        }
        onNewPhotoCaptured?.invoke(bytes)
    }

    override fun onPictureRetrievalFailed(objectHandle: Int, reason: String) {
        Log.w(TAG, "Gagal retrieve foto (handle=$objectHandle): $reason")
        onCaptureFailed?.invoke()
    }

    override fun onBulbStarted() {
        // Mode bulb tidak dipakai di alur photobooth.
    }

    override fun onBulbStopped() {
        // idem
    }

    override fun onObjectAdded(handle: Int, format: Int) {
        // Object baru terdeteksi di kamera (hasil capture() kita ATAU shutter
        // fisik kamera) — abaikan folder/association, retrieve sisanya langsung
        // (thumbnail + gambar penuh + orientasi dalam 1 command, lihat
        // Camera.retrievePicture()).
        if (format == PtpConstants.ObjectFormat.Association) return
        camera?.retrievePicture(handle)
    }
}

/** Helper supaya getParcelableExtra tidak deprecated di Android versi baru. */
private inline fun <reified T : Parcelable> Intent.getParcelableExtraCompat(key: String): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key) as? T
    }
}
