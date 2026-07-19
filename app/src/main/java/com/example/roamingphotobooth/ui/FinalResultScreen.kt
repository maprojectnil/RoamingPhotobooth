package com.example.roamingphotobooth.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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

/**
 * Layar penuh yang muncul setelah SEMUA slot foto terisi: nampilin hasil akhir
 * + QR code buat scan/download foto dari Drive + tombol "Lanjut" untuk mulai
 * sesi baru. Dipakai bareng oleh MobileBoothScreen dan StandBoothScreen (tidak
 * perlu diduplikat, karena tampilannya sama untuk kedua mode — kalau nanti
 * perlu beda, tinggal bikin versi khusus per mode).
 */
@Composable
fun FinalResultScreen(
    resultBitmap: Bitmap,
    qrCodeBitmap: Bitmap?,
    onContinueClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            bitmap = resultBitmap.asImageBitmap(),
            contentDescription = "Hasil Akhir",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .padding(16.dp)
        )

        // Kartu QR: begitu foto selesai ke-upload ke Drive, QR-nya langsung muncul
        // di sini supaya user bisa langsung scan buat lihat/download fotonya sendiri.
        // Selama upload masih jalan di background, tampilkan status "menyiapkan".
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            if (qrCodeBitmap != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Image(
                        bitmap = qrCodeBitmap.asImageBitmap(),
                        contentDescription = "QR Foto",
                        modifier = Modifier.size(140.dp)
                    )
                    Text(
                        text = "📱 Scan buat lihat/download foto",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Black,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(20.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text(
                        text = "⏳ Menyiapkan QR (upload ke Drive)...",
                        color = Color.Black,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
        }

        Button(
            onClick = onContinueClick,
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            Text("➡️ Lanjut (Sesi Baru)")
        }
    }
}