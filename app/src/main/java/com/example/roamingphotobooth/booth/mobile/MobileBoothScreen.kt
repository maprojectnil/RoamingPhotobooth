package com.example.roamingphotobooth.booth.mobile

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.roamingphotobooth.ui.FinalResultScreen

/**
 * Layar booth mode MOBILE.
 *
 * File ini SENGAJA TERPISAH dari StandBoothScreen (beda folder/package:
 * booth.mobile vs booth.stand) — supaya gampang diubah salah satu tanpa
 * mempengaruhi yang lain. Kalau nanti mode Mobile butuh tampilan/perilaku
 * beda dari Stand, edit di sini saja.
 *
 * Live view di sini FULL SCREEN (bukan split) dengan frame PNG yang lagi
 * dipilih di-overlay pas di atasnya — supaya user langsung lihat framing-nya
 * pas motret.
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
    frameOverlayBitmap: Bitmap?,
    finalResultBitmap: Bitmap?,
    qrCodeBitmap: Bitmap?,
    onBackClick: () -> Unit,
    onContinueClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        if (finalResultBitmap != null) {
            // Sesi SELESAI: layar penuh nampilin hasil akhir + QR + tombol lanjut.
            FinalResultScreen(
                resultBitmap = finalResultBitmap,
                qrCodeBitmap = qrCodeBitmap,
                onContinueClick = onContinueClick
            )
        } else {
            // Sesi masih jalan (atau belum mulai): live view FULL SCREEN.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                if (liveViewBitmap != null) {
                    Image(
                        bitmap = liveViewBitmap.asImageBitmap(),
                        contentDescription = "Live View",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Overlay frame yang lagi dipilih — numpuk pas di atas live view.
                if (frameOverlayBitmap != null) {
                    Image(
                        bitmap = frameOverlayBitmap.asImageBitmap(),
                        contentDescription = "Frame Overlay",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Tombol back & setting — pojok kanan atas, sejajar.
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onSettingsClick) {
                    Text(
                        text = "⚙️",
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
                IconButton(onClick = onBackClick) {
                    Text(
                        text = "←",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                }
            }
        }

        // Status text — selalu tampil di pojok kiri atas, di kedua state.
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            color = Color.Black.copy(alpha = 0.6f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}