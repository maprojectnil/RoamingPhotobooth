package com.example.roamingphotobooth.booth.mobile

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import com.example.roamingphotobooth.booth.stand.StandBoothScreen
import com.example.roamingphotobooth.settings.AppearanceSettings

/**
 * Layar booth mode MOBILE.
 *
 * Setelah revisi ini, tampilan Mobile SENGAJA DISAMAKAN persis dengan Stand —
 * composable ini cuma WRAPPER TIPIS di atas [StandBoothScreen] (split-screen
 * 30:70 preview/live-view, layar REVIEW foto sebelum commit ke slot, dst).
 * Satu-satunya perbedaan fungsional Mobile vs Stand ada di BAGAIMANA foto
 * diambil, bukan di tampilannya:
 *
 * - Stand & Mobile-saat-Developer-Mode-aktif: capture dipicu lewat tombol
 *   shutter DI LAYAR (software trigger, ada countdown 3-2-1) — lihat
 *   [showDeviceCameraShutter] / [onDeviceCameraShutterClick], diteruskan ke
 *   StandBoothScreen.showShutterButton / onShutterClick.
 * - Mobile dengan kamera eksternal (kondisi normal, Developer Mode OFF): TIDAK
 *   ADA tombol shutter di layar sama sekali — foto dipicu lewat tombol FISIK
 *   di badan kamera. Begitu foto itu sampai ke app, alurnya tetap masuk ke
 *   layar REVIEW yang sama seperti Stand (bukan auto-commit langsung ke slot
 *   seperti sebelum revisi ini) — lihat MainActivity.onNewPhotoCaptured &
 *   MainActivity.showPhotoInReviewScreen.
 *
 * Sesi berikutnya bisa dipicu 2 cara begitu layar hasil akhir (FinalResultScreen)
 * tampil: (1) tap tombol "Lanjut" di layar, ATAU (2) jepret kamera fisik sekali
 * lagi — foto hasil jepretan itu otomatis dibuang (cuma dipakai sebagai sinyal
 * "mulai sesi baru", bukan foto pertama sesi berikutnya). Logic-nya ada di
 * MainActivity.onNewPhotoCaptured, bukan di sini.
 */
@Composable
fun MobileBoothScreen(
    status: String,
    liveViewBitmap: Bitmap?,
    previewBitmap: Bitmap?,
    finalResultBitmap: Bitmap?,
    qrCodeBitmap: Bitmap?,
    countdownValue: Int?,
    isCapturing: Boolean,
    isProcessing: Boolean,
    reviewBitmap: Bitmap?,
    currentSlotNumber: Int,
    totalSlots: Int,
    // <-- setting Mirror aktif untuk SESI INI (lihat MainActivity.sessionMirrorEnabled).
    mirrorEnabled: Boolean,
    onMirrorToggle: (Boolean) -> Unit,
    onBackClick: () -> Unit,
    onContinueClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRetakeClick: () -> Unit,
    onAcceptClick: () -> Unit,
    // <-- Developer Mode (kamera depan perangkat, lihat DeviceCameraSession) tidak
    // punya tombol shutter FISIK terpisah seperti kamera eksternal -- jadi saat
    // developer mode aktif, tampilkan tombol shutter di layar (software trigger +
    // countdown, sama seperti Stand) supaya user tetap bisa memicu capture. Default
    // false/no-op supaya alur Mobile normal (kamera eksternal, tombol fisik) tidak
    // menampilkan tombol apa pun di layar.
    showDeviceCameraShutter: Boolean = false,
    onDeviceCameraShutterClick: () -> Unit = {},
    // <-- BARU: diteruskan apa adanya ke StandBoothScreen -- dipakai untuk
    // tombol shutter (tampilan disamakan dengan tombol Mulai) & kontrol
    // tampil/sembunyi kotak status (lihat AppearanceSettings.showStatusText).
    appearance: AppearanceSettings = AppearanceSettings(),
    // <-- BARU: fitur "Retake slot sebelumnya", diteruskan apa adanya ke
    // StandBoothScreen (lihat dokumentasi param-nya di sana).
    filledSlots: List<Pair<Int, Bitmap>> = emptyList(),
    onRetakeSlotClick: (Int) -> Unit = {}
) {
    StandBoothScreen(
        status = status,
        liveViewBitmap = liveViewBitmap,
        previewBitmap = previewBitmap,
        finalResultBitmap = finalResultBitmap,
        qrCodeBitmap = qrCodeBitmap,
        countdownValue = countdownValue,
        isCapturing = isCapturing,
        isProcessing = isProcessing,
        reviewBitmap = reviewBitmap,
        currentSlotNumber = currentSlotNumber,
        totalSlots = totalSlots,
        mirrorEnabled = mirrorEnabled,
        onMirrorToggle = onMirrorToggle,
        onBackClick = onBackClick,
        onContinueClick = onContinueClick,
        onShutterClick = onDeviceCameraShutterClick,
        onRetakeClick = onRetakeClick,
        onAcceptClick = onAcceptClick,
        showShutterButton = showDeviceCameraShutter,
        showSettingsButton = true,
        onSettingsClick = onSettingsClick,
        appearance = appearance,
        filledSlots = filledSlots,
        onRetakeSlotClick = onRetakeSlotClick
    )
}
