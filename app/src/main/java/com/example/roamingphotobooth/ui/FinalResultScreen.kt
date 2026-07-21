package com.example.roamingphotobooth.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.roamingphotobooth.ui.theme.RoamingPhotoboothTheme

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
    // BoxWithConstraints supaya tahu perbandingan lebar vs tinggi area yang
    // tersedia (pola yang sama dipakai di MobileBoothScreen/TemplateListScreen)
    // — dari situ kita tentukan lagi landscape atau bukan, tanpa bergantung ke
    // Configuration.orientation yang kadang tidak akurat di semua device/split-screen.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val isLandscape = maxWidth > maxHeight

        if (isLandscape) {
            // Landscape: 70% buat hasil foto (kiri), 30% buat QR + tombol (kanan).
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    bitmap = resultBitmap.asImageBitmap(),
                    contentDescription = "Hasil Akhir",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .weight(0.7f)
                        .fillMaxHeight()
                        .padding(16.dp)
                )

                Column(
                    modifier = Modifier
                        .weight(0.3f)
                        .fillMaxHeight()
                        .background(Color(0xFF262A33))
                        .padding(25.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    QrCard(qrCodeBitmap = qrCodeBitmap)

                    Button(
                        onClick = onContinueClick,
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text("➡️ Lanjut (Sesi Baru)")
                    }
                }
            }
        } else {
            // Portrait: tetap seperti semula, ditumpuk dari atas ke bawah.
            Column(
                modifier = Modifier.fillMaxSize(),
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

                QrCard(qrCodeBitmap = qrCodeBitmap)

                Button(
                    onClick = onContinueClick,
                    modifier = Modifier.padding(bottom = 24.dp, top = 8.dp)
                ) {
                    Text("➡️ Lanjut (Sesi Baru)")
                }
            }
        }
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun FinalResultScreenPreview() {
    val dummyBitmap = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888).apply {
        eraseColor(android.graphics.Color.GRAY)
    }
    val dummyQr = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply {
        eraseColor(android.graphics.Color.WHITE)
    }
    RoamingPhotoboothTheme {
        FinalResultScreen(
            resultBitmap = dummyBitmap,
            qrCodeBitmap = dummyQr,
            onContinueClick = {}
        )
    }
}

@Preview(showBackground = true, device = "spec:width=891dp,height=411dp")
@Composable
private fun FinalResultScreenLandscapePreview() {
    val dummyBitmap = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888).apply {
        eraseColor(android.graphics.Color.GRAY)
    }
    RoamingPhotoboothTheme {
        FinalResultScreen(
            resultBitmap = dummyBitmap,
            qrCodeBitmap = null,
            onContinueClick = {}
        )
    }
}

/**
 * Kartu QR: begitu foto selesai ke-upload ke Drive, QR-nya langsung muncul
 * di sini supaya user bisa langsung scan buat lihat/download fotonya sendiri.
 * Selama upload masih jalan di background, tampilkan status "menyiapkan".
 * Dipisah jadi composable sendiri karena dipakai bareng oleh layout landscape
 * (Row 70:30) dan portrait (Column ditumpuk).
 */
@Composable
private fun QrCard(qrCodeBitmap: Bitmap?) {
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
}