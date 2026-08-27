package com.example.roamingphotobooth

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.roamingphotobooth.booth.mobile.MobileBoothScreen
import com.example.roamingphotobooth.booth.stand.StandBoothScreen
import com.example.roamingphotobooth.camera.CameraBackend
import com.example.roamingphotobooth.devicecam.DeviceCameraSession
import com.example.roamingphotobooth.nav.AppScreen
import com.example.roamingphotobooth.nav.BoothMode
import com.example.roamingphotobooth.ptp.EsCameraSession
import com.example.roamingphotobooth.ui.theme.RoamingPhotoboothTheme
import com.example.roamingphotobooth.ptp.BitmapMerger
import com.example.roamingphotobooth.ui.HomeScreen
import com.example.roamingphotobooth.settings.AppearanceSettings
import com.example.roamingphotobooth.settings.AppearanceStorage
import com.example.roamingphotobooth.settings.SettingsActivity

class MainActivity : ComponentActivity() {

    // Komunikasi PTP ke kamera (Canon EOS / Nikon) lewat library es-ptp-camera —
    // lihat EsCameraSession untuk detail adapter & alasan migrasi dari
    // implementasi native/libusb sebelumnya.
    private lateinit var cameraSession: EsCameraSession

    // Sumber kamera untuk Developer Mode (lihat DeveloperModeButton di HomeScreen) —
    // kamera DEPAN perangkat, alternatif dari cameraSession (PTP/USB eksternal).
    // Cuma dibuat begitu Developer Mode diaktifkan pertama kali (lihat
    // enableDeveloperMode()), makanya lateinit + dicek dulu pakai isInitialized.
    private lateinit var deviceCameraSession: DeviceCameraSession

    // Referensi sumber kamera yang SEDANG dipakai (cameraSession atau
    // deviceCameraSession, tergantung developerModeEnabled) -- semua kode yang
    // butuh trigger capture (mis. standStartCountdownAndCapture) baca lewat sini
    // supaya tidak perlu tahu implementasi konkretnya (lihat CameraBackend).
    private lateinit var activeCameraBackend: CameraBackend

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

    // QR code buat scan/download foto dari Drive — muncul di layar hasil akhir
    // (FinalResultScreen) begitu upload ke Drive selesai. Null selama upload masih
    // jalan di background (layar nampilin status "menyiapkan QR" sampai ini terisi).
    private var qrCodeBitmap = mutableStateOf<Bitmap?>(null)

    // <-- BARU: fitur Galeri — riwayat LOKAL sesi foto yang sudah selesai (lihat
    // package .gallery). galleryEntries di-refresh dari galleryRepository setiap
    // kali user membuka AppScreen.GALLERY (bukan realtime -- cukup, karena entri
    // baru cuma ditambah begitu 1 sesi selesai & user balik ke Home dulu sebelum
    // buka Galeri lagi). selectedGalleryEntry non-null berarti user lagi di layar
    // detail (recall) 1 entri tertentu -- lihat galleryOpenEntry()/galleryBack().
    private lateinit var galleryRepository: com.example.roamingphotobooth.gallery.GalleryRepository
    private var galleryEntries = mutableStateOf<List<com.example.roamingphotobooth.gallery.GallerySessionEntry>>(emptyList())
    private var selectedGalleryEntry = mutableStateOf<com.example.roamingphotobooth.gallery.GallerySessionEntry?>(null)
    private var galleryDetailBitmap = mutableStateOf<Bitmap?>(null)
    private var galleryDetailQr = mutableStateOf<Bitmap?>(null)

    private var activeTemplate = mutableStateOf<com.example.roamingphotobooth.template.PhotoTemplate?>(null)
    private var templateSession: com.example.roamingphotobooth.template.TemplateSessionManager? = null
    private lateinit var templateStorage: com.example.roamingphotobooth.template.TemplateStorage
    private lateinit var frameFileManager: com.example.roamingphotobooth.template.FrameFileManager

    // Pengaturan tampilan (background Home/Mode Select, warna tombol & aksen,
    // teks tombol) — diatur lewat Settings > Appearance (lihat SettingsActivity).
    // Dibaca ulang setiap kali balik dari SettingsActivity supaya perubahan
    // langsung kelihatan tanpa perlu restart app.
    private lateinit var appearanceStorage: AppearanceStorage
    private var appearanceSettings = mutableStateOf(AppearanceSettings())

    // Default Session settings (Countdown Timer / Mirror Camera / Auto Countdown),
    // diatur lewat Settings > Session — lihat SessionSettingsScreen. Dibaca ulang
    // begitu balik dari SettingsActivity (sama pola dengan appearanceSettings).
    private lateinit var sessionSettingsStorage: com.example.roamingphotobooth.settings.SessionSettingsStorage
    private var sessionSettings = mutableStateOf(com.example.roamingphotobooth.settings.SessionSettings())

    // Setting Mirror AKTIF untuk sesi yang SEDANG berjalan — diisi dari
    // sessionSettings.value.mirrorCamera setiap kali sesi baru dimulai
    // (loadActiveTemplate/startNewSession), tapi bisa di-override sendiri oleh
    // user lewat toggle Mirror di Session Preview (Mobile/Stand) TANPA mengubah
    // default global di atas. Lihat MobileBoothScreen/StandBoothScreen onMirrorToggle.
    private var sessionMirrorEnabled = mutableStateOf(true)

    // Kiosk Mode — diaktifkan/nonaktifkan lewat tombol gembok di HomeScreen (lihat
    // KioskModeButton). Saat aktif, layar dikunci pakai Android Screen Pinning API
    // (startLockTask) supaya user tidak bisa keluar app lewat Recents/Home; keluar
    // dari Kiosk Mode wajib lewat dialog password di HomeScreen (default "0000").
    private var kioskModeEnabled = mutableStateOf(false)

    // Developer Mode — diaktifkan/nonaktifkan lewat tombol logo orang di HomeScreen
    // (lihat DeveloperModeButton). Saat aktif, booth pakai kamera DEPAN perangkat
    // (lihat DeviceCameraSession) alih-alih kamera eksternal PTP/USB (EsCameraSession)
    // -- berguna untuk testing/pemakaian tanpa kamera DSLR/mirrorless fisik.
    private var developerModeEnabled = mutableStateOf(false)

    // Navigasi layar: Home -> pilih mode (Mobile/Stand) -> layar booth (live view + capture)
    private var currentScreen = mutableStateOf(AppScreen.HOME)
    private var boothMode = mutableStateOf<BoothMode?>(null)

    // --- State alur capture "countdown + shutter software + review" — dipakai
    // BERSAMA oleh Stand DAN Mobile (nama variabel masih berawalan "stand" untuk
    // alasan historis, tapi sejak tampilan Mobile disamakan dengan Stand, state
    // ini sekarang mengalir lewat StandBoothScreen untuk kedua mode). Lihat
    // showPhotoInReviewScreen() & standStartCountdownAndCapture(). ---

    // Non-null selama countdown 3-2-1 berjalan sebelum shutter ditembak.
    private var standCountdownValue = mutableStateOf<Int?>(null)

    // True selagi command capture udah dikirim ke kamera, nunggu foto ke-download.
    private var standIsCapturing = mutableStateOf(false)

    // Foto yang BARU diambil, nunggu user pilih Retake atau Lanjut. Non-null artinya
    // lagi di layar review. Belum di-commit ke slot template sampai user tekan "Lanjut".
    private var standReviewBitmap = mutableStateOf<Bitmap?>(null)
    private var standReviewPhotoBytes: ByteArray? = null

    // Dipakai buat "menangkap" 1 foto berikutnya dari kamera dan mengarahkannya ke
    // layar review (StandBoothScreen, dipakai bersama oleh Stand & Mobile) begitu
    // capture-nya dipicu lewat SOFTWARE trigger (tombol shutter di layar: Stand,
    // atau Mobile saat Developer Mode aktif — lihat standStartCountdownAndCapture()).
    // Null selama menunggu shutter DI LAYAR ditekan. Mobile dengan kamera eksternal
    // (tombol shutter FISIK) TIDAK PERNAH mengisi ini -- foto dari situ langsung
    // diarahkan ke showPhotoInReviewScreen() dari onNewPhotoCaptured, lihat sana.
    private var pendingStandCaptureCallback: ((ByteArray) -> Unit)? = null

    // Dipakai standStartCountdownAndCapture() buat validasi timeout jaring pengaman —
    // lihat komentar di fungsi itu.
    private var standCaptureRequestId = 0L

    // True selagi standAcceptPhoto() lagi menggabungkan frame+foto & menyimpan JPEG
    // ke disk di background thread. Dipakai buat cegah double-tap tombol "Lanjut"
    // selama proses itu berjalan (lihat standAcceptPhoto()).
    private var standIsProcessing = mutableStateOf(false)

    // <-- BERUBAH: dulu bernama enteringStandAfterFramePick & cuma dipakai buat
    // alur "Stand" (Mobile langsung ke BOOTH tanpa pilih frame dulu). Sekarang
    // layar "Pilih Mode" sudah dihapus -- Home langsung buka pemilihan frame
    // untuk KEDUA mode (Mobile maupun Stand), jadi flag ini digeneralisasi:
    // true kalau user baru saja menekan "Mulai" di Home dan lagi nunggu hasil
    // pilih-frame — begitu template dipilih, langsung lanjut masuk ke sesi
    // booth (lihat openFramePicker()).
    private var enteringBoothAfterFramePick = false

    private val templatePickerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val templateId = result.data?.getStringExtra("selected_template_id")
            if (templateId != null) {
                // showEmptySlotPlaceholders SELALU true di sini -- tampilan preview kiri
                // (kotak nomor slot yang masih kosong) sekarang SAMA untuk Mobile & Stand
                // (lihat StandBoothScreen, dipakai bersama lewat MobileBoothScreen).
                loadActiveTemplate(templateId, showEmptySlotPlaceholders = true)
                if (enteringBoothAfterFramePick) {
                    // boothMode.value SUDAH diisi dari sessionSettings.value.boothMode
                    // di openFramePicker() SEBELUM launcher ini dipanggil -- tidak perlu
                    // di-set lagi di sini.
                    currentScreen.value = AppScreen.BOOTH
                }
            }
        }
        enteringBoothAfterFramePick = false
    }


    // Buka SettingsActivity (dropdown Frame Editor / Frame List / Appearance) dari
    // ikon setting di HomeScreen. Tidak perlu baca result data — cukup muat ulang
    // appearanceSettings begitu user kembali, supaya perubahan Appearance (kalau
    // ada) langsung ter-refresh di Home/Mode Select.
    private val settingsLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        appearanceSettings.value = appearanceStorage.load()
        // <-- BARU: reload default Session settings juga -- kalau user baru saja
        // ubah Countdown/Mirror/Auto Countdown default di Settings > Session,
        // langsung kepakai untuk sesi berikutnya tanpa perlu restart app.
        sessionSettings.value = sessionSettingsStorage.load()
    }

    // Izin CAMERA runtime -- dibutuhkan Developer Mode (kamera depan perangkat via
    // CameraX, lihat DeviceCameraSession). Manifest sudah punya <uses-permission>
    // CAMERA, tapi tetap wajib diminta saat runtime di Android 6.0+.
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startDeviceCameraSession()
        } else {
            developerModeEnabled.value = false
            statusText.value = "⚠️ Izin kamera diperlukan untuk mengaktifkan Developer Mode"
        }
    }

    /**
     * Aktifkan Kiosk Mode: pakai Android Screen Pinning API (startLockTask) supaya
     * user tidak bisa keluar app lewat tombol Home/Recents/notification shade
     * selagi aktif. Ini API bawaan Android (tidak butuh app jadi Device Owner),
     * jadi cukup dipanggil langsung dari sini — lihat KioskModeButton di HomeScreen
     * untuk UI tombol & dialog password saat menonaktifkan.
     */
    private fun enableKioskMode() {
        kioskModeEnabled.value = true
        try {
            startLockTask()
        } catch (e: Exception) {
            Log.e("MainActivity", "Gagal mengaktifkan Kiosk Mode (startLockTask): ${e.message}")
        }
    }

    /**
     * Nonaktifkan Kiosk Mode — HANYA dipanggil dari KioskModeButton setelah
     * password yang dimasukkan user terverifikasi benar (lihat KioskPasswordDialog).
     */
    private fun disableKioskMode() {
        kioskModeEnabled.value = false
        try {
            stopLockTask()
        } catch (e: Exception) {
            Log.e("MainActivity", "Gagal menonaktifkan Kiosk Mode (stopLockTask): ${e.message}")
        }
    }

    /**
     * Aktifkan Developer Mode: lepas sesi kamera eksternal (PTP/USB) yang lagi
     * aktif, lalu mulai kamera DEPAN perangkat lewat DeviceCameraSession (minta
     * izin CAMERA runtime dulu kalau belum ada -- lihat cameraPermissionLauncher).
     * Dipanggil dari tombol DeveloperModeButton di HomeScreen.
     */
    private fun enableDeveloperMode() {
        developerModeEnabled.value = true
        cameraSession.release()
        liveViewBitmap.value = null
        statusText.value = "Mengaktifkan Developer Mode (kamera depan perangkat)..."

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startDeviceCameraSession()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startDeviceCameraSession() {
        deviceCameraSession = DeviceCameraSession(this, this)
        wireCameraCallbacks(deviceCameraSession)
        activeCameraBackend = deviceCameraSession
        deviceCameraSession.start()
    }

    /**
     * Nonaktifkan Developer Mode: hentikan kamera depan perangkat, lalu balik
     * lagi ke kamera eksternal PTP/USB (EsCameraSession) seperti semula --
     * dibuat ulang dari nol supaya BroadcastReceiver detach & listener PTP
     * ter-registrasi bersih (sama seperti kondisi awal app baru dibuka).
     */
    private fun disableDeveloperMode() {
        developerModeEnabled.value = false
        if (::deviceCameraSession.isInitialized) {
            deviceCameraSession.shutdown()
        }
        liveViewBitmap.value = null

        cameraSession = EsCameraSession(this)
        wireCameraCallbacks(cameraSession)
        activeCameraBackend = cameraSession
        cameraSession.initialize(intent)
        statusText.value = "Menunggu kamera dicolok..."
    }

    private fun updateOrientation() {
        requestedOrientation = when (boothMode.value) {
            BoothMode.STAND -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            BoothMode.MOBILE -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            null -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // Catatan: upload ke Drive TIDAK lagi disiapkan di sini. DriveAuth/DriveUploader
        // sekarang diinisialisasi di dalam DriveUploadWorker (lihat package .work),
        // supaya upload berjalan sebagai background WorkManager job yang bisa
        // di-retry otomatis meski Activity ini sudah tidak hidup.

        // Load frame PNG sekali di awal
        frameOverlayBitmap.value = loadFrameFromAssets("wedding.png")
        previewBitmap.value = frameOverlayBitmap.value

        cameraSession = EsCameraSession(this)
        wireCameraCallbacks(cameraSession)
        activeCameraBackend = cameraSession
        templateStorage = com.example.roamingphotobooth.template.TemplateStorage(this)
        frameFileManager = com.example.roamingphotobooth.template.FrameFileManager(this)
        appearanceStorage = AppearanceStorage(this)
        appearanceSettings.value = appearanceStorage.load()
        sessionSettingsStorage = com.example.roamingphotobooth.settings.SessionSettingsStorage(this)
        sessionSettings.value = sessionSettingsStorage.load()
        sessionMirrorEnabled.value = sessionSettings.value.mirrorCamera
        galleryRepository = com.example.roamingphotobooth.gallery.GalleryRepository(this)

        setContent {
            RoamingPhotoboothTheme {
                when (currentScreen.value) {
                    // <-- BERUBAH: onMulaiClick dulu pindah ke AppScreen.MODE_SELECT
                    // (layar pilih Mobile/Stand). Sekarang layar itu sudah dihapus --
                    // langsung buka pemilihan frame (openFramePicker()) memakai mode
                    // yang sudah diatur lewat switch di Settings > Session, lalu
                    // masuk ke AppScreen.BOOTH begitu template dipilih (lihat
                    // templatePickerLauncher).
                    AppScreen.HOME -> HomeScreen(
                        onMulaiClick = { openFramePicker() },
                        onSettingsClick = {
                            settingsLauncher.launch(
                                android.content.Intent(this, SettingsActivity::class.java)
                            )
                        },
                        appearance = appearanceSettings.value,
                        // <-- BARU: live view kamera terkini, dipakai HomeScreen sebagai
                        // background Home kalau appearance.useLiveViewAsHomeBackground
                        // aktif (lihat Settings > Appearance). Null selama kamera belum
                        // tersambung -- HomeScreen otomatis jatuh balik ke background
                        // statis/hitam di kondisi itu.
                        liveViewBitmap = liveViewBitmap.value,
                        mirrorLiveViewBackground = sessionSettings.value.mirrorCamera,
                        kioskModeEnabled = kioskModeEnabled.value,
                        onEnableKioskMode = { enableKioskMode() },
                        onDisableKioskMode = { disableKioskMode() },
                        developerModeEnabled = developerModeEnabled.value,
                        onEnableDeveloperMode = { enableDeveloperMode() },
                        onDisableDeveloperMode = { disableDeveloperMode() },
                        onGalleryClick = { openGallery() }
                    )

                    AppScreen.BOOTH -> when (boothMode.value) {
                        BoothMode.MOBILE -> MobileBoothScreen(
                            status = statusText.value,
                            liveViewBitmap = liveViewBitmap.value,
                            previewBitmap = previewBitmap.value,
                            finalResultBitmap = finalResultBitmap.value,
                            qrCodeBitmap = qrCodeBitmap.value,
                            // Tampilan Mobile disamakan dengan Stand -- lihat MobileBoothScreen &
                            // StandBoothScreen. State countdown/isCapturing/isProcessing/reviewBitmap
                            // di sini dipakai BERSAMA dengan Stand (lihat deklarasi standXxx di atas),
                            // baik saat capture-nya lewat tombol fisik kamera (langsung masuk review,
                            // lihat showPhotoInReviewScreen) maupun lewat Developer Mode (software
                            // trigger + countdown, sama seperti Stand).
                            countdownValue = standCountdownValue.value,
                            isCapturing = standIsCapturing.value,
                            isProcessing = standIsProcessing.value,
                            reviewBitmap = standReviewBitmap.value,
                            currentSlotNumber = (templateSession?.filledSlots ?: 0) + 1,
                            totalSlots = templateSession?.totalSlots ?: 0,
                            mirrorEnabled = sessionMirrorEnabled.value,
                            onMirrorToggle = { sessionMirrorEnabled.value = it },
                            // <-- BERUBAH: dulu balik ke AppScreen.MODE_SELECT (layar
                            // pilih Mobile/Stand). Sekarang layar itu sudah dihapus --
                            // balik langsung ke Home.
                            onBackClick = { currentScreen.value = AppScreen.HOME },
                            onContinueClick = { startNewSession() },
                            onSettingsClick = {
                                templatePickerLauncher.launch(
                                    android.content.Intent(this, com.example.roamingphotobooth.template.TemplateEditorActivity::class.java)
                                )
                            },
                            onRetakeClick = { standRetakePhoto() },
                            onAcceptClick = { standAcceptPhoto() },
                            // Developer Mode (kamera depan perangkat) tidak punya tombol
                            // shutter fisik terpisah seperti kamera eksternal -- tampilkan
                            // tombol shutter di layar (software trigger + countdown, sama
                            // seperti Stand) supaya user tetap bisa jepret.
                            showDeviceCameraShutter = developerModeEnabled.value,
                            onDeviceCameraShutterClick = { standStartCountdownAndCapture() }
                        )

                        BoothMode.STAND -> StandBoothScreen(
                            status = statusText.value,
                            liveViewBitmap = liveViewBitmap.value,
                            previewBitmap = previewBitmap.value,
                            finalResultBitmap = finalResultBitmap.value,
                            qrCodeBitmap = qrCodeBitmap.value,
                            countdownValue = standCountdownValue.value,
                            isCapturing = standIsCapturing.value,
                            isProcessing = standIsProcessing.value,
                            reviewBitmap = standReviewBitmap.value,
                            currentSlotNumber = (templateSession?.filledSlots ?: 0) + 1,
                            totalSlots = templateSession?.totalSlots ?: 0,
                            mirrorEnabled = sessionMirrorEnabled.value,
                            onMirrorToggle = { sessionMirrorEnabled.value = it },
                            // <-- BERUBAH: sama seperti MobileBoothScreen di atas --
                            // dulu balik ke AppScreen.MODE_SELECT, sekarang langsung
                            // ke Home karena layar Mode Select sudah dihapus.
                            onBackClick = { currentScreen.value = AppScreen.HOME },
                            onContinueClick = { startNewSession() },
                            onShutterClick = { standStartCountdownAndCapture() },
                            onRetakeClick = { standRetakePhoto() },
                            onAcceptClick = { standAcceptPhoto() }
                        )

                        // Failsafe: seharusnya tidak pernah tercapai, karena boothMode.value
                        // SELALU diisi (dari sessionSettings.value.boothMode) di
                        // openFramePicker() SEBELUM currentScreen pindah ke AppScreen.BOOTH
                        // -- lihat templatePickerLauncher & openFramePicker() di atas. Kalau
                        // tetap null (mis. state process-death yang aneh), balik ke Home
                        // dengan aman daripada crash/nge-render layar kosong.
                        null -> {
                            currentScreen.value = AppScreen.HOME
                        }
                    }

                    // <-- BARU: Galeri — riwayat sesi foto yang sudah selesai.
                    // selectedGalleryEntry null -> tampilkan daftar (grid thumbnail);
                    // non-null -> tampilkan detail 1 entri (FinalResultScreen dipakai
                    // ulang dalam mode GALLERY_RECALL, lihat galleryOpenEntry()).
                    // Sementara bitmap full-res-nya masih di-decode di background
                    // (galleryDetailBitmap.value == null), tampilkan indikator loading
                    // supaya FinalResultScreen (yang butuh Bitmap non-null) tidak perlu
                    // dipanggil sebelum siap.
                    AppScreen.GALLERY -> {
                        val entry = selectedGalleryEntry.value
                        val detailBitmap = galleryDetailBitmap.value
                        when {
                            entry == null -> com.example.roamingphotobooth.gallery.GalleryScreen(
                                entries = galleryEntries.value,
                                onEntryClick = { galleryOpenEntry(it) },
                                onBackClick = { currentScreen.value = AppScreen.HOME }
                            )

                            detailBitmap == null -> Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(ComposeColor.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = ComposeColor.White)
                            }

                            else -> com.example.roamingphotobooth.ui.FinalResultScreen(
                                resultBitmap = detailBitmap,
                                qrCodeBitmap = galleryDetailQr.value,
                                onContinueClick = { galleryCloseEntry() },
                                mode = com.example.roamingphotobooth.ui.FinalResultMode.GALLERY_RECALL
                            )
                        }
                    }
                }
            }
        }

        // Coba connect ke kamera yang mungkin sudah tersambung SEBELUM app dibuka,
        // atau (kalau intent ini datang dari filter USB_DEVICE_ATTACHED) langsung
        // pakai device di dalamnya. EsCameraSession.initialize() aman dipanggil
        // berkali-kali -- lihat catatan di PtpService.initialize() (library).
        cameraSession.initialize(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // BEDA dari versi lama: sekarang WAJIB diteruskan ke cameraSession, karena
        // inilah jalur yang dipakai saat kamera dicolok SAAT app sudah berjalan
        // (activity singleTop menerima ulang lewat sini, bukan onCreate lagi) --
        // intent-nya membawa EXTRA_DEVICE dari filter USB_DEVICE_ATTACHED di manifest.
        // Dilewati kalau Developer Mode aktif -- sumber kamera yang dipakai memang
        // kamera depan perangkat (deviceCameraSession), bukan cameraSession, jadi
        // tidak boleh ikut nge-render live view/callback PTP di atasnya.
        if (!developerModeEnabled.value) {
            cameraSession.initialize(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraSession.release()
        if (::deviceCameraSession.isInitialized) {
            deviceCameraSession.shutdown()
        }
    }

    /**
     * Pasang semua callback [CameraBackend] sekali per instance sumber kamera --
     * dipakai baik untuk cameraSession (PTP/USB) maupun deviceCameraSession
     * (kamera depan perangkat, Developer Mode), karena keduanya implement
     * kontrak yang sama (lihat CameraBackend). Isi callback-nya SAMA PERSIS
     * apapun sumbernya -- MainActivity tidak perlu tahu bedanya lagi setelah ini.
     */
    private fun wireCameraCallbacks(backend: CameraBackend) {
        backend.onSessionReady = {
            runOnUiThread {
                statusText.value = if (developerModeEnabled.value) {
                    "✅ Developer Mode aktif — kamera depan perangkat siap!"
                } else {
                    "✅ Sesi PTP terbuka!\nMemulai live view..."
                }
            }
        }

        backend.onLiveViewFrame = { bitmap ->
            liveViewBitmap.value = bitmap
        }

        backend.onSessionError = { error ->
            runOnUiThread {
                statusText.value = "❌ Error: $error"
            }
        }

        // Shutter software (tombol di layar Stand) gagal ke-trigger di kamera —
        // tidak akan pernah ada foto baru yang datang lewat onNewPhotoCaptured,
        // jadi reset di sini supaya tombol shutter tidak nyangkut disabled dan
        // user tinggal tap ulang, bukan cabut-pasang kabel kamera.
        backend.onCaptureFailed = {
            runOnUiThread {
                standIsCapturing.value = false
                pendingStandCaptureCallback = null
                statusText.value = "⚠️ Gagal jepret, coba lagi"
            }
        }

        backend.onDeviceDetached = {
            runOnUiThread { statusText.value = "Kamera terputus. Menunggu kamera dicolok..." }
        }

        backend.onNewPhotoCaptured = merge@{ photoBytes ->
            // MOBILE + lagi nampilin layar hasil akhir (sesi sebelumnya sudah kelar,
            // nunggu user "Lanjut"): jepretan baru dari kamera fisik di-anggap SINYAL
            // "mulai sesi baru" doang, bukan foto pertama sesi berikutnya — jadi foto
            // hasil jepretan ini DIBUANG (cuma dipakai buat trigger, tidak disimpan/
            // di-upload) dan langsung reset sesi + balik ke live view.
            if (boothMode.value == BoothMode.MOBILE && finalResultBitmap.value != null) {
                runOnUiThread {
                    statusText.value = "📸 Terdeteksi jepretan baru → mulai sesi baru..."
                    startNewSession()
                }
                return@merge
            }

            // Kalau lagi nunggu 1 foto buat layar review (abis shutter software
            // ditembak -- Stand ATAU Mobile Developer Mode), arahkan ke situ dan
            // JANGAN auto-commit ke slot template dulu.
            val standCallback = pendingStandCaptureCallback
            if (standCallback != null) {
                pendingStandCaptureCallback = null
                standCallback.invoke(photoBytes)
                return@merge
            }

            // Mobile dengan kamera eksternal (Developer Mode OFF): tidak ada software
            // trigger sama sekali, jadi tidak akan pernah ada pendingStandCaptureCallback
            // yang nunggu -- foto ini datang langsung dari tombol shutter FISIK di kamera.
            // Arahkan langsung ke layar review yang SAMA dengan Stand (lihat
            // showPhotoInReviewScreen), bukan auto-commit ke slot seperti sebelumnya.
            if (boothMode.value == BoothMode.MOBILE && !developerModeEnabled.value && templateSession != null) {
                showPhotoInReviewScreen(photoBytes)
                return@merge
            }

            // Titik ini seharusnya sudah TIDAK PERNAH tercapai lagi selama ada template
            // aktif (Stand & Mobile-kamera-fisik ditangani di atas, Mobile Developer Mode
            // lewat pendingStandCaptureCallback juga di atas) — disisakan sebagai fallback
            // aman kalau boothMode entah kenapa null di titik ini.
            val templateSess = templateSession

            if (templateSess == null) {
                // Tidak ada template aktif — fallback ke behavior lama (1 foto, frame test)
                runOnUiThread { statusText.value = "📸 Foto diterima! (tanpa template aktif)" }
                val decodedPhoto = BitmapMerger.decodeBitmap(photoBytes) ?: return@merge
                val cameraPhoto = if (sessionMirrorEnabled.value) {
                    val mirrored = BitmapMerger.mirrorHorizontal(decodedPhoto)
                    decodedPhoto.recycle()
                    mirrored
                } else {
                    decodedPhoto
                }
                val testFrame = createTestFrame(cameraPhoto.width, cameraPhoto.height)
                val merged = BitmapMerger.mergeBitmap(cameraPhoto, testFrame)
                val savedUri = saveMergedBitmap(merged)
                runOnUiThread { statusText.value = "✅ Tersimpan: $savedUri" }
                return@merge
            }

            val addResult = templateSess.addPhoto(photoBytes, mirror = sessionMirrorEnabled.value)
            if (addResult != com.example.roamingphotobooth.template.TemplateSessionManager.AddPhotoResult.ADDED) {
                val msg = if (addResult == com.example.roamingphotobooth.template.TemplateSessionManager.AddPhotoResult.DECODE_FAILED) {
                    "⚠️ Foto gagal diproses (korup) — jepret ulang untuk slot ini"
                } else {
                    "⚠️ Semua slot sudah terisi"
                }
                runOnUiThread { statusText.value = msg }
                return@merge
            }

            runOnUiThread {
                statusText.value = "📸 Foto ${templateSess.filledSlots}/${templateSess.totalSlots} diterima!"
                refreshPreview()
            }

            if (templateSess.isComplete) {
                val frameBmp = frameOverlayBitmap.value
                if (frameBmp != null) {
                    val finalImage = templateSess.buildFinalImage(frameBmp)
                    if (finalImage != null) {
                        val savedUri = saveMergedBitmap(finalImage)
                        runOnUiThread {
                            statusText.value = "✅ SEMUA FOTO SELESAI! Tersimpan: $savedUri"
                            // QR sudah di-set instan di dalam saveMergedBitmap() di atas -- JANGAN
                            // di-null-kan lagi di sini, nanti nimpa QR yang baru saja muncul.
                            finalResultBitmap.value = finalImage
                        }
                        // Sesi TIDAK di-reset di sini — nunggu user tekan tombol "Lanjut"
                        // di layar hasil supaya foto akhir bisa direview dulu.
                    }
                }
            }
        }
    }

    private fun loadActiveTemplate(templateId: String, showEmptySlotPlaceholders: Boolean) {
        val template = templateStorage.loadTemplate(templateId) ?: return
        activeTemplate.value = template
        templateSession = com.example.roamingphotobooth.template.TemplateSessionManager(template)

        // Load bitmap frame untuk overlay live view
        val frameBmp = frameFileManager.loadBitmap(template.framePngPath)
        frameOverlayBitmap.value = frameBmp
        finalResultBitmap.value = null
        qrCodeBitmap.value = null
        // Sesi baru -> pakai default Mirror dari Settings > Session (bisa
        // di-override lagi per-sesi lewat toggle di Session Preview).
        sessionMirrorEnabled.value = sessionSettings.value.mirrorCamera
        refreshPreview(showEmptySlotPlaceholders)

        statusText.value = "Template '${template.name}' aktif (${template.slotCount} slot foto)"
    }

    /**
     * Bangun ulang preview kiri (frame + foto yang sudah masuk sejauh ini) dan simpan
     * ke state supaya Compose langsung re-render. Kalau belum ada template aktif,
     * preview cuma frame polos tanpa foto.
     *
     * [showEmptySlotPlaceholders] mengontrol kotak nomor untuk slot yang masih kosong
     * (lihat [com.example.roamingphotobooth.template.TemplateSessionManager.buildPreviewImage]) —
     * SELALU `true` sekarang, untuk Mobile MAUPUN Stand: sejak tampilan Mobile disamakan
     * dengan Stand (lihat StandBoothScreen/MobileBoothScreen), preview kiri di kedua mode
     * sama-sama split-screen terpisah dari live view, jadi kotak nomor slot kosong selalu
     * relevan buat nunjukin progress di kedua mode.
     */
    private fun refreshPreview(showEmptySlotPlaceholders: Boolean = true) {
        val session = templateSession
        val frameBmp = frameOverlayBitmap.value
        previewBitmap.value = if (session != null && frameBmp != null) {
            session.buildPreviewImage(
                frameBitmap = frameBmp,
                showEmptySlotPlaceholders = showEmptySlotPlaceholders
            )
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
        qrCodeBitmap.value = null
        // Sesi baru -> balik ke default Mirror dari Settings > Session, buang
        // override per-sesi sebelumnya (contoh di spesifikasi: sesi berikutnya
        // tetap pakai default meski sesi sebelumnya sempat diubah manual).
        sessionMirrorEnabled.value = sessionSettings.value.mirrorCamera
        refreshPreview()
        statusText.value = activeTemplate.value?.let {
            "Template '${it.name}' aktif (${it.slotCount} slot foto)"
        } ?: "Menunggu kamera dicolok..."
    }

    /**
     * <-- BERUBAH: dulu bernama openStandFramePicker(), cuma dipanggil dari
     * tombol "Stand" di layar Mode Select (yang sekarang sudah dihapus).
     * Sekarang dipanggil LANGSUNG dari tombol logo "Mulai" di Home (lihat
     * onMulaiClick di setContent di bawah) untuk KEDUA mode -- boothMode
     * diambil dari default yang diatur lewat switch Mobile/Stand di
     * Settings > Session (lihat SessionSettings.boothMode), BUKAN dipilih
     * user tiap sesi lagi.
     *
     * Set boothMode.value DULU sebelum buka layar pilih frame, supaya kalau
     * user membatalkan (back) tanpa pilih template, kembalinya tetap ke Home
     * seperti biasa (currentScreen tidak diubah sampai template benar-benar
     * terpilih -- lihat templatePickerLauncher di atas).
     */
    private fun openFramePicker() {
        boothMode.value = sessionSettings.value.boothMode
        enteringBoothAfterFramePick = true
        templatePickerLauncher.launch(
            android.content.Intent(this, com.example.roamingphotobooth.template.TemplateEditorActivity::class.java)
        )
    }

    /**
     * Dipanggil dari tombol Galeri di HomeScreen: baca ulang riwayat dari
     * [galleryRepository] (bisa saja ada entri baru sejak terakhir dibuka --
     * mis. sesi photobooth baru saja selesai) lalu pindah ke AppScreen.GALLERY
     * dalam kondisi daftar (bukan detail -- selectedGalleryEntry dipastikan
     * null di sini, jaga-jaga kalau sebelumnya sempat tertinggal terisi).
     */
    private fun openGallery() {
        selectedGalleryEntry.value = null
        galleryDetailBitmap.value = null
        galleryDetailQr.value = null
        galleryEntries.value = galleryRepository.getAll()
        currentScreen.value = AppScreen.GALLERY
    }

    /**
     * Dipanggil dari GalleryScreen saat user tap 1 entri riwayat: masuk ke
     * layar detail (FinalResultScreen mode GALLERY_RECALL). Bitmap resolusi
     * PENUH & QR di-generate ulang di background thread (bukan di UI thread,
     * sama seperti alasan yang sama di standAcceptPhoto()) karena decode JPEG
     * resolusi kamera + encode QR bisa berat -- galleryDetailBitmap tetap null
     * sementara proses ini jalan supaya UI nampilin loading spinner dulu
     * (lihat routing AppScreen.GALLERY di setContent).
     */
    private fun galleryOpenEntry(entry: com.example.roamingphotobooth.gallery.GallerySessionEntry) {
        selectedGalleryEntry.value = entry
        galleryDetailBitmap.value = null
        galleryDetailQr.value = null

        lifecycleScope.launch(Dispatchers.IO) {
            val bitmap = try {
                val uri = android.net.Uri.parse(entry.mediaUri)
                contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Gagal load foto galeri (${entry.fileName}): ${e.message}")
                null
            }
            val qr = com.example.roamingphotobooth.util.QrCodeGenerator.generate(entry.landingUrl)

            withContext(Dispatchers.Main) {
                // Kalau user sudah keburu tap "Kembali" sebelum decode ini selesai
                // (selectedGalleryEntry sudah null lagi / pindah ke entri lain),
                // JANGAN timpa state -- bitmap ini sudah tidak relevan lagi.
                if (selectedGalleryEntry.value?.slug != entry.slug) return@withContext
                if (bitmap == null) {
                    statusText.value = "⚠️ Foto tidak ditemukan (mungkin sudah dihapus dari galeri HP)"
                    selectedGalleryEntry.value = null
                    return@withContext
                }
                galleryDetailBitmap.value = bitmap
                galleryDetailQr.value = qr
            }
        }
    }

    /** Dipanggil dari tombol panah "Kembali ke Galeri" di layar detail: balik ke daftar. */
    private fun galleryCloseEntry() {
        selectedGalleryEntry.value = null
        galleryDetailBitmap.value = null
        galleryDetailQr.value = null
    }

    /**
     * Isi layar REVIEW (foto + tombol Retake/Lanjut, lihat StandBoothScreen) dengan
     * 1 foto yang baru masuk, TANPA commit ke slot template dulu (baru commit kalau
     * user tekan "Lanjut" -- lihat standAcceptPhoto()). Dipakai BERSAMA oleh:
     * - Capture software (Stand & Mobile Developer Mode), dipanggil dari
     *   pendingStandCaptureCallback di standStartCountdownAndCapture().
     * - Capture fisik (Mobile kamera eksternal), dipanggil LANGSUNG dari
     *   onNewPhotoCaptured begitu foto dari tombol shutter fisik kamera masuk.
     */
    private fun showPhotoInReviewScreen(photoBytes: ByteArray) {
        standReviewPhotoBytes = photoBytes
        runOnUiThread {
            // Mirror horizontal di sini juga (bukan cuma addPhoto()) supaya foto
            // yang tampil di layar REVIEW sudah sama persis orientasinya dengan
            // yang bakal masuk ke slot kalau user tekan "Lanjut".
            val decoded = BitmapMerger.decodeBitmap(photoBytes)
            standReviewBitmap.value = decoded?.let {
                if (sessionMirrorEnabled.value) BitmapMerger.mirrorHorizontal(it) else it
            }
            standIsCapturing.value = false
        }
    }

    /**
     * Dipanggil dari tombol shutter DI LAYAR (mode Stand, atau Mobile saat
     * Developer Mode aktif): jalanin countdown 3-2-1 di UI, lalu kirim command
     * capture ke kamera lewat CameraBackend aktif, dan nunggu foto masuk lewat
     * pendingStandCaptureCallback (di-invoke dari backend.onNewPhotoCaptured,
     * lihat wireCameraCallbacks) sebelum ditampilkan di layar review lewat
     * showPhotoInReviewScreen().
     *
     * TIDAK dipakai untuk Mobile dengan kamera eksternal (tombol shutter fisik) --
     * di situ foto langsung masuk lewat onNewPhotoCaptured tanpa command capture
     * dari app sama sekali, lihat showPhotoInReviewScreen() yang dipanggil
     * langsung dari sana.
     */
    private fun standStartCountdownAndCapture() {
        if (standIsCapturing.value || standCountdownValue.value != null || standIsProcessing.value) return // cegah double-tap

        lifecycleScope.launch {
            // Durasi countdown ikut setting default Countdown Timer (Settings > Session,
            // lihat SessionSettingsScreen) -- bukan lagi hardcoded 3 detik.
            for (i in sessionSettings.value.countdownSeconds downTo 1) {
                standCountdownValue.value = i
                delay(1000)
            }
            standCountdownValue.value = null
            standIsCapturing.value = true

            // requestId ini buat bedain "timeout basi dari attempt lama" vs "timeout
            // yang beneran relevan buat attempt sekarang" -- kalau di antara delay 6
            // detik di bawah user sempat retake/shutter ulang (attempt baru bikin
            // requestId baru), timeout attempt lama tidak boleh ikut nge-reset state
            // attempt yang baru itu.
            val requestId = ++standCaptureRequestId

            pendingStandCaptureCallback = { photoBytes -> showPhotoInReviewScreen(photoBytes) }

            if (!activeCameraBackend.capturePhoto()) {
                runOnUiThread {
                    statusText.value = "⚠️ Kamera belum terkoneksi"
                    standIsCapturing.value = false
                    pendingStandCaptureCallback = null
                }
            }

            // Jaring pengaman kedua: kalau capture() di EsCameraSession/library
            // SUKSES (jadi onCaptureFailed tidak kepanggil) tapi foto tetap tidak
            // pernah nongol lewat pollNewObjects() (mis. kamera nyimpen ke lokasi
            // beda, event GetEvent kelewat, dll), standIsCapturing bakal nyangkut
            // true selamanya tanpa ini. 6 detik dipilih jauh di atas waktu normal
            // (shutter + write ke SD + deteksi object baru biasanya <2 detik).
            delay(6000)
            if (standCaptureRequestId == requestId && standIsCapturing.value) {
                Log.w("MainActivity", "standStartCountdownAndCapture: timeout, foto tidak pernah masuk")
                standIsCapturing.value = false
                pendingStandCaptureCallback = null
                statusText.value = "⚠️ Foto tidak terdeteksi, coba lagi"
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
            // decode bitmap res-penuh, berat -> background
            val addResult = session.addPhoto(photoBytes, mirror = sessionMirrorEnabled.value)

            if (addResult != com.example.roamingphotobooth.template.TemplateSessionManager.AddPhotoResult.ADDED) {
                val msg = if (addResult == com.example.roamingphotobooth.template.TemplateSessionManager.AddPhotoResult.DECODE_FAILED) {
                    "⚠️ Foto gagal diproses (korup) — jepret ulang untuk slot ini"
                } else {
                    "⚠️ Semua slot sudah terisi"
                }
                withContext(Dispatchers.Main) {
                    statusText.value = msg
                    standIsProcessing.value = false
                }
                return@launch
            }

            val frameBmp = frameOverlayBitmap.value
            // standAcceptPhoto() cuma dipanggil dari alur STAND, jadi kotak nomor slot
            // kosong SELALU ditampilkan di sini (showEmptySlotPlaceholders = true) —
            // beda dari refreshPreview() yang dipakai bersama Mobile & Stand dan baru
            // baca boothMode.value buat nentuin itu.
            val previewImage = frameBmp?.let {
                session.buildPreviewImage(it, showEmptySlotPlaceholders = true)
            } // compose bitmap, berat -> background

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
                    // QR sudah di-set instan di dalam saveMergedBitmap() di atas -- JANGAN
                    // di-null-kan lagi di sini, nanti nimpa QR yang baru saja muncul.
                    finalResultBitmap.value = finalImage
                }
                standIsProcessing.value = false

                // Auto Countdown for Next Slots (Settings > Session): kalau ON dan
                // masih ada slot berikutnya (belum selesai), langsung mulai countdown
                // otomatis tanpa nunggu user tap shutter lagi. Berlaku mulai dari slot
                // kedua dan seterusnya -- foto PERTAMA tiap sesi tetap wajib manual
                // tap shutter (baru dari sinilah, tiap "Lanjut" berikutnya, alur ini
                // yang memicu countdown slot selanjutnya).
                //
                // HANYA berlaku untuk mode software-trigger (Stand, atau Mobile saat
                // Developer Mode aktif) -- Mobile dengan kamera eksternal TIDAK punya
                // cara memicu capture dari app sama sekali, jadi slot berikutnya wajib
                // dipicu manual lewat tombol shutter fisik di kamera, sama seperti slot
                // pertama.
                val usesSoftwareTrigger = boothMode.value == BoothMode.STAND || developerModeEnabled.value
                if (finalImage == null && usesSoftwareTrigger && sessionSettings.value.autoCountdownNextSlots) {
                    standStartCountdownAndCapture()
                }
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

        // Slug + QR dibuat 100% LOKAL, instan, tidak nunggu upload apapun.
        // QR nunjuk ke landing page kita sendiri (bukan langsung ke Drive), yang
        // nge-track status foto ini secara realtime lewat Firestore.
        val slug = com.example.roamingphotobooth.status.SlugGenerator.newSlug()
        val landingUrl = com.example.roamingphotobooth.status.SlugGenerator.landingUrl(
            BuildConfig.LANDING_BASE_URL, slug
        )
        val qr = com.example.roamingphotobooth.util.QrCodeGenerator.generate(landingUrl)
        runOnUiThread {
            qrCodeBitmap.value = qr
            statusText.value = "✅ Tersimpan: $fileName — mengunggah ke Drive di latar belakang..."
        }

        // <-- BARU: catat sesi ini ke riwayat Galeri (lihat gallery.GalleryRepository)
        // supaya bisa di-"recall" nanti dari HomeScreen > Galeri -- lihat print ulang
        // & tampilkan lagi QR-nya tanpa perlu upload/jepret ulang. Cuma dicatat kalau
        // insert ke MediaStore di atas berhasil (uri != null) -- kalau gagal, tidak
        // ada apa pun buat di-recall nanti karena file JPEG permanennya tidak ada.
        if (uri != null) {
            galleryRepository.addEntry(
                com.example.roamingphotobooth.gallery.GallerySessionEntry(
                    slug = slug,
                    fileName = fileName,
                    mediaUri = uri.toString(),
                    landingUrl = landingUrl,
                    createdAtMillis = System.currentTimeMillis()
                )
            )
        }

        // Simpan JPEG ke cache privat app (bukan cuma MediaStore) supaya WorkManager
        // masih bisa baca file-nya sekalipun proses ini mati & di-restart oleh OS
        // di tengah upload/retry.
        val cacheFile = java.io.File(cacheDir, "upload_$slug.jpg")
        cacheFile.writeBytes(jpegBytes)

        enqueueDriveUpload(slug, fileName, cacheFile.absolutePath)

        return fileName
    }

    /**
     * Enqueue job upload ke Drive lewat WorkManager: retry otomatis dengan
     * backoff eksponensial, dan nunggu sampai ada koneksi internet kalau lagi
     * offline (constraint NetworkType.CONNECTED) -- tidak perlu polling manual.
     * unique work key = slug, supaya foto yang sama tidak ke-enqueue dobel.
     */
    private fun enqueueDriveUpload(slug: String, fileName: String, filePath: String) {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()

        val request = androidx.work.OneTimeWorkRequestBuilder<com.example.roamingphotobooth.work.DriveUploadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                java.util.concurrent.TimeUnit.MILLISECONDS
            )
            .setInputData(
                com.example.roamingphotobooth.work.buildUploadInputData(slug, fileName, filePath)
            )
            .build()

        androidx.work.WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork(slug, androidx.work.ExistingWorkPolicy.KEEP, request)
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

}