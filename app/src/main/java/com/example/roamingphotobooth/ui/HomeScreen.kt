package com.example.roamingphotobooth.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.roamingphotobooth.R
import com.example.roamingphotobooth.settings.AppearanceSettings
import com.example.roamingphotobooth.settings.BackgroundLayer

/**
 * Layar pertama saat aplikasi dibuka: tombol "Mulai" di tengah, dan ikon
 * pengaturan (⚙️) di pojok kiri atas yang membuka pengaturan frame/template.
 *
 * [appearance] opsional (default = latar hitam polos tanpa gambar/video,
 * teks "Mulai") — diisi dari `AppearanceStorage.load()` di MainActivity
 * begitu user sudah pernah mengatur tampilan lewat Settings > Appearance.
 */
@Composable
fun HomeScreen(
    onMulaiClick: () -> Unit,
    onSettingsClick: () -> Unit,
    appearance: AppearanceSettings = AppearanceSettings(),
    // <-- BARU: Kiosk Mode — tombol gembok pojok kanan atas (lihat KioskModeButton).
    // Default aman (nonaktif, no-op) supaya pemanggil lama yang belum diupdate
    // tetap kompilasi & jalan seperti sebelumnya.
    kioskModeEnabled: Boolean = false,
    onEnableKioskMode: () -> Unit = {},
    onDisableKioskMode: () -> Unit = {},
    // <-- BARU: Developer Mode — tombol logo orang pojok kiri bawah (lihat
    // DeveloperModeButton). Saat aktif, booth pakai kamera DEPAN perangkat sendiri,
    // tidak butuh kamera eksternal. Default aman (nonaktif, no-op) sama seperti
    // Kiosk Mode di atas.
    developerModeEnabled: Boolean = false,
    onEnableDeveloperMode: () -> Unit = {},
    onDisableDeveloperMode: () -> Unit = {},
    // <-- BARU: Galeri — riwayat sesi foto yang sudah selesai (recall print & QR
    // lama, lihat gallery.GalleryScreen). Tombol pojok kanan bawah, default no-op
    // supaya pemanggil lama yang belum diupdate tetap kompilasi.
    onGalleryClick: () -> Unit = {},
    liveViewBitmap: Bitmap?,
    mirrorLiveViewBackground: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        BackgroundLayer(
            background = appearance.homeBackground,
            modifier = Modifier.fillMaxSize()
        )

        // Logo/tombol setting — pojok kiri atas, masuk ke pengaturan frame.
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Text(
                text = "⚙️",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        // Tombol Kiosk Mode — pojok kanan atas, opacity rendah supaya tidak
        // mengganggu tampilan Home (lihat KioskModeButton untuk logic password).
        KioskModeButton(
            enabled = kioskModeEnabled,
            onEnable = onEnableKioskMode,
            onDisable = onDisableKioskMode,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        )

        // Tombol Developer Mode — pojok kiri bawah, opacity rendah, ukuran sama
        // seperti tombol Kiosk Mode (lihat DeveloperModeButton). Aktifkan saat
        // booth mau dites/dipakai tanpa kamera eksternal (pakai kamera depan HP).
        DeveloperModeButton(
            enabled = developerModeEnabled,
            onEnable = onEnableDeveloperMode,
            onDisable = onDisableDeveloperMode,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        )

        // Tombol Galeri — pojok kanan bawah, buka riwayat sesi foto yang sudah
        // selesai (recall print/QR lama tanpa perlu mulai sesi baru).
        IconButton(
            onClick = onGalleryClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoLibrary,
                contentDescription = "Galeri",
                tint = Color.White
            )
        }

        // Tombol Mulai — tengah layar (bisa digeser lewat offset X/Y di
        // bawah). Berupa logo kamera SVG/vector (ic_start_camera_logo, lihat
        // res/drawable) yang di-tint memakai appearance.startButtonIconColorArgb,
        // bukan tombol teks lagi (lihat komentar di
        // AppearanceSettings.startButtonIconColorArgb).
        //
        // <-- BARU: ukuran & posisi sekarang bisa diatur user lewat Settings >
        // Appearance (lihat AppearanceSettings.startButtonSizeDp/
        // startButtonOffsetXDp/startButtonOffsetYDp) alih-alih angka tetap.
        // Posisi = pergeseran dari titik tengah layar (Alignment.Center);
        // (0f, 0f) artinya tetap persis di tengah seperti sebelumnya.
        val startButtonSize = appearance.startButtonSizeDp.dp
        IconButton(
            onClick = onMulaiClick,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(
                    x = appearance.startButtonOffsetXDp.dp,
                    y = appearance.startButtonOffsetYDp.dp
                )
                .size(startButtonSize)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_start_camera_logo),
                contentDescription = appearance.startButtonText,
                tint = Color(appearance.startButtonIconColorArgb),
                modifier = Modifier.size(startButtonSize)
            )
        }
    }
}
