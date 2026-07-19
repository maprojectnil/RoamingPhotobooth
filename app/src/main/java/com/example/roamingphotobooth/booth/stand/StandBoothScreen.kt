package com.example.roamingphotobooth.booth.stand

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.roamingphotobooth.ui.FinalResultScreen

/**
 * Layar booth mode STAND.
 *
 * File ini SENGAJA TERPISAH dari MobileBoothScreen (beda folder/package:
 * booth.stand vs booth.mobile) — supaya gampang diubah salah satu tanpa
 * mempengaruhi yang lain.
 *
 * Alurnya (beda dari Mobile): saat sesi capture, layar dibagi dua (kiri ~30% /
 * kanan ~70%). Kiri nampilin preview frame (frame + foto-foto yang sudah
 * ke-capture, terisi di slot masing-masing — update tiap foto baru masuk).
 * Kanan nampilin live view kamera + tombol shutter.
 * Tap shutter -> countdown 3-2-1 -> app kirim command capture ke kamera
 * (software trigger) -> nunggu foto -> layar REVIEW muncul (foto + tombol
 * Retake / Lanjut). "Lanjut" baru commit foto itu ke slot & lanjut ke slot
 * berikutnya (atau ke hasil akhir kalau itu slot terakhir).
 */
@Composable
fun StandBoothScreen(
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
    onBackClick: () -> Unit,
    onContinueClick: () -> Unit,
    onShutterClick: () -> Unit,
    onRetakeClick: () -> Unit,
    onAcceptClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        when {
            finalResultBitmap != null -> {
                // Sesi SELESAI: layar penuh nampilin hasil akhir + QR + tombol lanjut.
                FinalResultScreen(
                    resultBitmap = finalResultBitmap,
                    qrCodeBitmap = qrCodeBitmap,
                    onContinueClick = onContinueClick
                )
            }

            reviewBitmap != null -> {
                // Layar REVIEW: foto yang baru diambil + tombol Retake / Lanjut.
                ReviewContent(
                    reviewBitmap = reviewBitmap,
                    isProcessing = isProcessing,
                    onRetakeClick = onRetakeClick,
                    onAcceptClick = onAcceptClick
                )
            }

            else -> {
                // Sesi capture: split-screen 30:70 (preview frame kiri, live view kanan)
                // + tombol shutter & countdown overlay di sisi live view.
                // Catatan: begitu user tap "Lanjut" di layar review, reviewBitmap langsung
                // di-null-kan (biar UI langsung responsif) SEBELUM proses simpan foto di
                // background selesai — jadi layar ini bisa sempat kelihatan lagi sementara
                // standAcceptPhoto() masih commit foto sebelumnya di background. Makanya
                // tombol shutter tetap harus dikunci pakai isProcessing supaya user tidak
                // bisa mulai capture baru sebelum commit foto sebelumnya beres.
                CaptureContent(
                    liveViewBitmap = liveViewBitmap,
                    previewBitmap = previewBitmap,
                    countdownValue = countdownValue,
                    isCapturing = isCapturing,
                    isProcessing = isProcessing,
                    currentSlotNumber = currentSlotNumber,
                    totalSlots = totalSlots,
                    onShutterClick = onShutterClick
                )

                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "←",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                }
            }
        }

        // Status text — selalu tampil di pojok kiri atas.
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

@Composable
private fun CaptureContent(
    liveViewBitmap: Bitmap?,
    previewBitmap: Bitmap?,
    countdownValue: Int?,
    isCapturing: Boolean,
    isProcessing: Boolean,
    currentSlotNumber: Int,
    totalSlots: Int,
    onShutterClick: () -> Unit
) {
    // Split-screen: kiri ~30% preview frame (frame + foto yang sudah terisi),
    // kanan ~70% live view kamera + kontrol capture (countdown, slot, shutter).
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // === Kiri (30%): preview frame yang akan terisi tiap foto ===
        Box(
            modifier = Modifier
                .weight(0.3f)
                .fillMaxHeight()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (previewBitmap != null) {
                Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = "Preview Frame",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // === Kanan (70%): live view + countdown + slot + shutter ===
        Box(
            modifier = Modifier
                .weight(0.7f)
                .fillMaxHeight()
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

            // Progress slot — misal "Foto 2 / 4"
            if (totalSlots > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Foto $currentSlotNumber / $totalSlots",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // Overlay angka countdown gede di tengah
            if (countdownValue != null) {
                Text(
                    text = countdownValue.toString(),
                    fontSize = 120.sp,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Tombol shutter — bulat, di bawah tengah
            Button(
                onClick = onShutterClick,
                enabled = countdownValue == null && !isCapturing && !isProcessing,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .size(80.dp)
            ) {
                Text(
                    text = when {
                        isProcessing -> "💾" // masih nyimpen foto sebelumnya di background
                        isCapturing -> "..."
                        else -> "📷"
                    },
                    style = MaterialTheme.typography.headlineMedium
                )
            }
        }
    }
}

@Composable
private fun ReviewContent(
    reviewBitmap: Bitmap,
    isProcessing: Boolean,
    onRetakeClick: () -> Unit,
    onAcceptClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            bitmap = reviewBitmap.asImageBitmap(),
            contentDescription = "Preview Foto",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxSize()
                .padding(16.dp)
        )

        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(bottom = 32.dp, top = 8.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onRetakeClick,
                enabled = !isProcessing
            ) {
                Text(
                    text = "🔄 Retake",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            Button(
                onClick = onAcceptClick,
                enabled = !isProcessing
            ) {
                Text(
                    text = if (isProcessing) "⏳ Menyimpan..." else "✅ Lanjut",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}