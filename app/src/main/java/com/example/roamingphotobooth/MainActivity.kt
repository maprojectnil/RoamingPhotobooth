package com.example.roamingphotobooth

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.roamingphotobooth.booth.mobile.MobileBoothScreen
import com.example.roamingphotobooth.booth.stand.StandBoothScreen
import com.example.roamingphotobooth.nav.AppScreen
import com.example.roamingphotobooth.nav.BoothMode
import com.example.roamingphotobooth.ptp.NativePtpBridge
import com.example.roamingphotobooth.ptp.PtpDeviceManager
import com.example.roamingphotobooth.ptp.PtpSessionManager
import com.example.roamingphotobooth.ui.theme.RoamingPhotoboothTheme
import com.example.roamingphotobooth.ptp.PtpNativeConnection
import com.example.roamingphotobooth.ptp.BitmapMerger
import com.example.roamingphotobooth.ui.HomeScreen
import com.example.roamingphotobooth.ui.ModeSelectScreen

class MainActivity : ComponentActivity() {

    private lateinit var deviceManager: PtpDeviceManager
    private var sessionManager: PtpSessionManager? = null

    // Client Drive: dipakai buat upload otomatis foto hasil ke Google Drive
    // begitu foto selesai disimpan lokal. Diinisialisasi di onCreate().
    private lateinit var driveUploader: com.example.roamingphotobooth.drive.DriveUploader

    // GUARD: cegah multiple sesi berjalan bersamaan ke device yang sama
    private var isConnecting = false

    private var frameOverlayBitmap = mutableStateOf<Bitmap?>(null)

    private var statusText = mutableStateOf("Menunggu kamera dicolok...")

    private var liveViewBitmap = mutableStateOf<Bitmap?>(null)

    // Preview kiri: frame + foto-foto yang SUDAH ke-capture, terisi di slot masing-masing.
    // Di-update tiap kali ada foto baru masuk. Kalau belum ada template aktif, ini cuma
    // nampilin frameOverlayBitmap polos (tanpa foto, karena tidak ada slot untuk diisi).
    private var previewBitmap = mutableStateOf<Bitmap?>(null)

    // Hasil akhir setelah SEMUA slot terisi. Selama ini non-null, layar full menampilkan
    // hasil ini + tombol "Lanjut" — split-screen live view disembunyikan sampai user
    // menekan lanjut (yang akan reset sesi & bikin field ini balik jadi null).
    private var finalResultBitmap = mutableStateOf<Bitmap?>(null)

    private var activeTemplate = mutableStateOf<com.example.roamingphotobooth.template.PhotoTemplate?>(null)
    private var templateSession: com.example.roamingphotobooth.template.TemplateSessionManager? = null
    private lateinit var templateStorage: com.example.roamingphotobooth.template.TemplateStorage
    private lateinit var frameFileManager: com.example.roamingphotobooth.template.FrameFileManager

    // Navigasi layar: Home -> pilih mode (Mobile/Stand) -> layar booth (live view + capture)
    private var currentScreen = mutableStateOf(AppScreen.HOME)
    private var boothMode = mutableStateOf<BoothMode?>(null)

    // --- State khusus alur capture mode STAND (countdown + shutter software + review) ---

    // Non-null selama countdown 3-2-1 berjalan sebelum shutter ditembak.
    private var standCountdownValue = mutableStateOf<Int?>(null)

    // True selagi command capture udah dikirim ke kamera, nunggu foto ke-download.
    private var standIsCapturing = mutableStateOf(false)

    // Foto yang BARU diambil, nunggu user pilih Retake atau Lanjut. Non-null artinya
    // lagi di layar review. Belum di-commit ke slot template sampai user tekan "Lanjut".
    private var standReviewBitmap = mutableStateOf<Bitmap?>(null)
    private var standReviewPhotoBytes: ByteArray? = null

    // Dipakai buat "menangkap" 1 foto berikutnya dari kamera dan mengarahkannya ke
    // layar review Stand, alih-alih langsung di-commit otomatis ke slot template
    // (yang merupakan behavior default untuk mode Mobile / capture via tombol fisik).
    private var pendingStandCaptureCallback: ((ByteArray) -> Unit)? = null

    // True selagi standAcceptPhoto() lagi menggabungkan frame+foto & menyimpan JPEG
    // ke disk di background thread. Dipakai buat cegah double-tap tombol "Lanjut"
    // selama proses itu berjalan (lihat standAcceptPhoto()).
    private var standIsProcessing = mutableStateOf(false)

    // True kalau user baru saja klik "Stand" di Mode Select dan lagi nunggu hasil
    // pilih-frame — begitu template dipilih, langsung lanjut masuk ke sesi Stand.
    private var enteringStandAfterFramePick = false

    private val templatePickerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val templateId = result.data?.getStringExtra("selected_template_id")
            if (templateId != null) {
                loadActiveTemplate(templateId)
                if (enteringStandAfterFramePick) {
                    boothMode.value = BoothMode.STAND
                    currentScreen.value = AppScreen.BOOTH
                }
            }
        }
        enteringStandAfterFramePick = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Setup client upload Drive (OAuth refresh token, atas nama akun Gmail asli
        // supaya kuota penyimpanan ikut akun itu, bukan Service Account).
        val driveAuth = com.example.roamingphotobooth.drive.DriveAuth(
            clientId = BuildConfig.DRIVE_OAUTH_CLIENT_ID,
            clientSecret = BuildConfig.DRIVE_OAUTH_CLIENT_SECRET,
            refreshToken = BuildConfig.DRIVE_OAUTH_REFRESH_TOKEN
        )
        driveUploader = com.example.roamingphotobooth.drive.DriveUploader(
            driveAuth,
            BuildConfig.DRIVE_FOLDER_ID
        )

        // Load frame PNG sekali di awal
        frameOverlayBitmap.value = loadFrameFromAssets("wedding.png")
        previewBitmap.value = frameOverlayBitmap.value

        deviceManager = PtpDeviceManager(this)
        templateStorage = com.example.roamingphotobooth.template.TemplateStorage(this)
        frameFileManager = com.example.roamingphotobooth.template.FrameFileManager(this)

        setContent {
            RoamingPhotoboothTheme {
                when (currentScreen.value) {
                    AppScreen.HOME -> HomeScreen(
                        onMulaiClick = { currentScreen.value = AppScreen.MODE_SELECT },
                        onSettingsClick = {
                            templatePickerLauncher.launch(
                                android.content.Intent(this, com.example.roamingphotobooth.template.TemplateEditorActivity::class.java)
                            )
                        }
                    )

                    AppScreen.MODE_SELECT -> ModeSelectScreen(
                        onMobileClick = {
                            boothMode.value = BoothMode.MOBILE
                            currentScreen.value = AppScreen.BOOTH
                        },
                        onStandClick = { openStandFramePicker() },
                        onBackClick = { currentScreen.value = AppScreen.HOME }
                    )

                    AppScreen.BOOTH -> when (boothMode.value) {
                        BoothMode.MOBILE -> MobileBoothScreen(
                            status = statusText.value,
                            liveViewBitmap = liveViewBitmap.value,
                            frameOverlayBitmap = frameOverlayBitmap.value,
                            finalResultBitmap = finalResultBitmap.value,
                            onBackClick = { currentScreen.value = AppScreen.MODE_SELECT },
                            onContinueClick = { startNewSession() },
                            onSettingsClick = {
                                templatePickerLauncher.launch(
                                    android.content.Intent(this, com.example.roamingphotobooth.template.TemplateEditorActivity::class.java)
                                )
                            }
                        )

                        BoothMode.STAND -> StandBoothScreen(
                            status = statusText.value,
                            liveViewBitmap = liveViewBitmap.value,
                            previewBitmap = previewBitmap.value,
                            finalResultBitmap = finalResultBitmap.value,
                            countdownValue = standCountdownValue.value,
                            isCapturing = standIsCapturing.value,
                            isProcessing = standIsProcessing.value,
                            reviewBitmap = standReviewBitmap.value,
                            currentSlotNumber = (templateSession?.filledSlots ?: 0) + 1,
                            totalSlots = templateSession?.totalSlots ?: 0,
                            onBackClick = { currentScreen.value = AppScreen.MODE_SELECT },
                            onContinueClick = { startNewSession() },
                            onShutterClick = { standStartCountdownAndCapture() },
                            onRetakeClick = { standRetakePhoto() },
                            onAcceptClick = { standAcceptPhoto() }
                        )

                        null -> ModeSelectScreen(
                            onMobileClick = {
                                boothMode.value = BoothMode.MOBILE
                                currentScreen.value = AppScreen.BOOTH
                            },
                            onStandClick = { openStandFramePicker() },
                            onBackClick = { currentScreen.value = AppScreen.HOME }
                        )
                    }
                }
            }
        }

        deviceManager.startListening(
            onReady = { device -> onCameraDeviceReady(device) },
            onDetached = {
                runOnUiThread { statusText.value = "Kamera terputus. Menunggu kamera dicolok..." }
                sessionManager?.closeSession()
                sessionManager = null
                isConnecting = false
            }
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Sengaja dikosongkan - startListening() sudah cukup di onCreate,
        // tidak perlu dipanggil ulang di sini (mencegah race condition / sesi ganda)
    }

    private fun loadActiveTemplate(templateId: String) {
        val template = templateStorage.loadTemplate(templateId) ?: return
        activeTemplate.value = template
        templateSession = com.example.roamingphotobooth.template.TemplateSessionManager(template)

        // Load bitmap frame untuk overlay live view
        val frameBmp = frameFileManager.loadBitmap(template.framePngPath)
        frameOverlayBitmap.value = frameBmp
        finalResultBitmap.value = null
        refreshPreview()

        statusText.value = "Template '${template.name}' aktif (${template.slotCount} slot foto)"
    }

    /**
     * Bangun ulang preview kiri (frame + foto yang sudah masuk sejauh ini) dan simpan
     * ke state supaya Compose langsung re-render. Kalau belum ada template aktif,
     * preview cuma frame polos tanpa foto.
     */
    private fun refreshPreview() {
        val session = templateSession
        val frameBmp = frameOverlayBitmap.value
        previewBitmap.value = if (session != null && frameBmp != null) {
            session.buildPreviewImage(frameBmp)
        } else {
            frameBmp
        }
    }

    /**
     * Dipanggil dari tombol "Lanjut" di layar hasil akhir: reset sesi supaya siap
     * dipakai motret dari slot pertama lagi, dan sembunyikan layar hasil.
     */
    private fun startNewSession() {
        templateSession?.reset()
        finalResultBitmap.value = null
        refreshPreview()
        statusText.value = activeTemplate.value?.let {
            "Template '${it.name}' aktif (${it.slotCount} slot foto)"
        } ?: "Menunggu kamera dicolok..."
    }

    /**
     * Dipanggil dari tombol "Stand" di Mode Select: buka layar pilih frame dulu.
     * Begitu user pilih template, templatePickerLauncher (lihat atas) otomatis
     * lanjut masuk ke sesi Stand karena enteringStandAfterFramePick di-set true.
     */
    private fun openStandFramePicker() {
        enteringStandAfterFramePick = true
        templatePickerLauncher.launch(
            android.content.Intent(this, com.example.roamingphotobooth.template.TemplateEditorActivity::class.java)
        )
    }

    /**
     * Dipanggil dari tombol shutter di layar (mode Stand): jalanin countdown
     * 3-2-1 di UI, lalu kirim command capture ke kamera lewat PtpSessionManager,
     * dan nunggu foto masuk lewat pendingStandCaptureCallback (di-invoke dari
     * session.onNewPhotoCaptured, lihat onCameraDeviceReady) sebelum ditampilkan
     * di layar review.
     */
    private fun standStartCountdownAndCapture() {
        if (standIsCapturing.value || standCountdownValue.value != null || standIsProcessing.value) return // cegah double-tap

        lifecycleScope.launch {
            for (i in 3 downTo 1) {
                standCountdownValue.value = i
                delay(1000)
            }
            standCountdownValue.value = null
            standIsCapturing.value = true

            pendingStandCaptureCallback = { photoBytes ->
                standReviewPhotoBytes = photoBytes
                runOnUiThread {
                    standReviewBitmap.value = BitmapMerger.decodeBitmap(photoBytes)
                    standIsCapturing.value = false
                }
            }

            sessionManager?.capturePhoto()
                ?: runOnUiThread {
                    statusText.value = "⚠️ Kamera belum terkoneksi"
                    standIsCapturing.value = false
                    pendingStandCaptureCallback = null
                }
        }
    }

    /** Tombol "Retake" di layar review: buang foto tadi, siap shutter lagi buat slot yang sama. */
    private fun standRetakePhoto() {
        standReviewBitmap.value = null
        standReviewPhotoBytes = null
    }

    /**
     * Tombol "Lanjut" di layar review: baru sekarang foto beneran di-commit ke
     * slot template. Kalau itu foto terakhir yang dibutuhkan, langsung build &
     * simpan hasil akhir (sama seperti alur Mobile).
     *
     * PENTING: decode/compose bitmap (buildPreviewImage/buildFinalImage) dan simpan
     * JPEG ke disk itu berat (ratusan ms - detik untuk foto DSLR res besar). Kalau
     * dijalankan langsung di UI thread, seluruh UI (termasuk live view) freeze selama
     * itu, dan frame live view yang numpuk selama freeze baru muncul sekaligus begitu
     * lepas -> kelihatan patah-patah. Makanya semua kerja berat itu didorong ke
     * Dispatchers.IO, dan cuma update state UI yang balik ke Dispatchers.Main.
     */
    private fun standAcceptPhoto() {
        if (standIsProcessing.value) return // cegah double-tap selama proses background jalan
        val photoBytes = standReviewPhotoBytes ?: return
        val session = templateSession ?: return

        standReviewBitmap.value = null
        standReviewPhotoBytes = null
        standIsProcessing.value = true

        lifecycleScope.launch(Dispatchers.IO) {
            val added = session.addPhoto(photoBytes) // decode bitmap res-penuh, berat -> background

            if (!added) {
                withContext(Dispatchers.Main) {
                    statusText.value = "⚠️ Semua slot sudah terisi"
                    standIsProcessing.value = false
                }
                return@launch
            }

            val frameBmp = frameOverlayBitmap.value
            val previewImage = frameBmp?.let { session.buildPreviewImage(it) } // compose bitmap, berat -> background

            var finalImage: Bitmap? = null
            var savedName: String? = null
            if (session.isComplete && frameBmp != null) {
                finalImage = session.buildFinalImage(frameBmp) // compose bitmap, berat -> background
                if (finalImage != null) {
                    savedName = saveMergedBitmap(finalImage) // JPEG compress + I/O disk -> background
                }
            }

            withContext(Dispatchers.Main) {
                statusText.value = "📸 Foto ${session.filledSlots}/${session.totalSlots} diterima!"
                if (previewImage != null) previewBitmap.value = previewImage
                if (finalImage != null) {
                    statusText.value = "✅ SEMUA FOTO SELESAI! Tersimpan: $savedName"
                    finalResultBitmap.value = finalImage
                }
                standIsProcessing.value = false
            }
        }
    }

    private fun createTestFrame(width: Int, height: Int): Bitmap {
        val frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frame)
        val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 40f
        }
        canvas.drawRect(20f, 20f, width - 20f, height - 20f, paint)
        return frame
    }

    private fun saveMergedBitmap(bitmap: Bitmap): String {
        val fileName = "photobooth_${System.currentTimeMillis()}.jpg"
        val jpegBytes = java.io.ByteArrayOutputStream().use { baos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, baos)
            baos.toByteArray()
        }

        // simpan ke galeri lokal (MediaStore), sama seperti sebelumnya
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RoamingPhotobooth")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let { contentResolver.openOutputStream(it)?.use { out -> out.write(jpegBytes) } }

        // upload ke Drive di background, tidak nge-block UI/capture berikutnya
        uploadToDriveAsync(fileName, jpegBytes)

        return fileName
    }

    private fun uploadToDriveAsync(fileName: String, jpegBytes: ByteArray) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val fileId = driveUploader.uploadBytes(fileName, jpegBytes)
                Log.i("DriveUpload", "Sukses upload $fileName -> fileId=$fileId")
                withContext(Dispatchers.Main) {
                    statusText.value = "✅ Tersimpan & terupload ke Drive: $fileName"
                }
            } catch (e: Exception) {
                Log.e("DriveUpload", "Gagal upload $fileName", e)
                withContext(Dispatchers.Main) {
                    statusText.value = "⚠️ Tersimpan lokal, gagal upload ke Drive: ${e.message}"
                }
            }
        }
    }

    private fun loadFrameFromAssets(fileName: String): Bitmap? {
        return try {
            assets.open(fileName).use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Gagal load frame dari assets: ${e.message}")
            null
        }
    }

    private fun onCameraDeviceReady(device: UsbDevice) {
        if (isConnecting || sessionManager != null) {
            Log.w("MainActivity", "onCameraDeviceReady dipanggil lagi, DIABAIKAN")
            return
        }
        isConnecting = true

        statusText.value = "Kamera terdeteksi: ${device.deviceName}\nMembuka koneksi (libusb)..."

        val usbManager = getSystemService(USB_SERVICE) as UsbManager
        val usbConnection = PtpNativeConnection(usbManager, device)

        val opened = usbConnection.open()
        if (!opened) {
            statusText.value = "Gagal membuka koneksi libusb ke kamera"
            isConnecting = false
            return
        }

        val session = PtpSessionManager(usbConnection)
        sessionManager = session

        session.onSessionReady = {
            runOnUiThread {
                statusText.value = "✅ Sesi PTP terbuka!\nMemulai live view..."
            }
            isConnecting = false
            session.startLiveView()
        }

        session.onLiveViewFrame = { frameBytes ->
            val bitmap = BitmapMerger.decodeLiveViewFrame(frameBytes)
            if (bitmap != null) {
                liveViewBitmap.value = bitmap
            }
        }

        session.onSessionError = { error ->
            runOnUiThread {
                statusText.value = "❌ Error: $error"
            }
            isConnecting = false
            sessionManager?.closeSession()
            sessionManager = null
        }

        session.onNewPhotoCaptured = merge@{ photoBytes ->
            // Kalau lagi nunggu 1 foto buat layar review Stand (abis shutter software
            // ditembak), arahkan ke situ dan JANGAN auto-commit ke slot template dulu.
            val standCallback = pendingStandCaptureCallback
            if (standCallback != null) {
                pendingStandCaptureCallback = null
                standCallback.invoke(photoBytes)
                return@merge
            }

            val session = templateSession

            if (session == null) {
                // Tidak ada template aktif — fallback ke behavior lama (1 foto, frame test)
                runOnUiThread { statusText.value = "📸 Foto diterima! (tanpa template aktif)" }
                val cameraPhoto = BitmapMerger.decodeBitmap(photoBytes) ?: return@merge
                val testFrame = createTestFrame(cameraPhoto.width, cameraPhoto.height)
                val merged = BitmapMerger.mergeBitmap(cameraPhoto, testFrame)
                val savedUri = saveMergedBitmap(merged)
                runOnUiThread { statusText.value = "✅ Tersimpan: $savedUri" }
                return@merge
            }

            val added = session.addPhoto(photoBytes)
            if (!added) {
                runOnUiThread { statusText.value = "⚠️ Semua slot sudah terisi" }
                return@merge
            }

            runOnUiThread {
                statusText.value = "📸 Foto ${session.filledSlots}/${session.totalSlots} diterima!"
                refreshPreview()
            }

            if (session.isComplete) {
                val frameBmp = frameOverlayBitmap.value
                if (frameBmp != null) {
                    val finalImage = session.buildFinalImage(frameBmp)
                    if (finalImage != null) {
                        val savedUri = saveMergedBitmap(finalImage)
                        runOnUiThread {
                            statusText.value = "✅ SEMUA FOTO SELESAI! Tersimpan: $savedUri"
                            finalResultBitmap.value = finalImage
                        }
                        // Sesi TIDAK di-reset di sini — nunggu user tekan tombol "Lanjut"
                        // di layar hasil supaya foto akhir bisa direview dulu.
                    }
                }
            }
        }

        session.startSession()
    }
}