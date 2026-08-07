package com.example.roamingphotobooth.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Tombol Developer Mode — pojok kiri bawah Home, logo orang, opacity ~20% (sama
 * pola dengan KioskModeButton) supaya tidak mengganggu tampilan Home yang sudah ada.
 *
 * Tap untuk toggle: NONAKTIF -> AKTIF dan sebaliknya, langsung tanpa dialog/password
 * (beda dari Kiosk Mode yang butuh password buat nonaktifkan) -- Developer Mode
 * cuma alat bantu testing/pemakaian tanpa kamera eksternal, bukan pengaman.
 *
 * Saat AKTIF: booth TIDAK butuh kamera eksternal (DSLR/mirrorless via PTP-USB) --
 * app pakai kamera DEPAN perangkat sendiri sebagai sumber live view & capture
 * (lihat DeviceCameraSession + MainActivity.enableDeveloperMode()).
 */
@Composable
fun DeveloperModeButton(
    enabled: Boolean,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = { if (enabled) onDisable() else onEnable() },
        modifier = modifier
    ) {
        Icon(
            imageVector = if (enabled) Icons.Filled.Person else Icons.Filled.PersonOff,
            contentDescription = if (enabled) {
                "Nonaktifkan Developer Mode (pakai kamera depan perangkat)"
            } else {
                "Aktifkan Developer Mode (pakai kamera depan perangkat)"
            },
            tint = Color.White.copy(alpha = 0.2f)
        )
    }
}
