package com.example.roamingphotobooth.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
 * + tombol "Lanjut" untuk mulai sesi baru. Dipakai bareng oleh MobileBoothScreen
 * dan StandBoothScreen (tidak perlu diduplikat, karena tampilannya sama untuk
 * kedua mode — kalau nanti perlu beda, tinggal bikin versi khusus per mode).
 */
@Composable
fun FinalResultScreen(
    resultBitmap: Bitmap,
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

        Button(
            onClick = onContinueClick,
            modifier = Modifier.padding(24.dp)
        ) {
            Text("➡️ Lanjut (Sesi Baru)")
        }
    }
}
