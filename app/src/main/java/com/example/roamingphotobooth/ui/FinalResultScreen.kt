package com.example.roamingphotobooth.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.roamingphotobooth.network.PrintServerClient
import com.example.roamingphotobooth.ui.theme.RoamingPhotoboothTheme
import java.io.File
import java.io.FileOutputStream

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

                    // Fitur test terpisah — tidak menyentuh alur di atas
                    TestPrintButton(resultBitmap = resultBitmap)
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
                    modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
                ) {
                    Text("➡️ Lanjut (Sesi Baru)")
                }

                // Fitur test terpisah — tidak menyentuh alur di atas
                TestPrintButton(resultBitmap = resultBitmap)
            }
        }
    }
}

/**
 * Tombol "Test Print" — fitur terpisah untuk uji coba kirim foto hasil akhir
 * ke Print Server Windows lewat HTTP. Tidak berpengaruh ke alur photobooth
 * utama (onContinueClick, upload Drive, dsb).
 */
@Composable
private fun TestPrintButton(resultBitmap: Bitmap) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var serverIp by remember { mutableStateOf("192.168.0.106") }
    var copiesText by remember { mutableStateOf("1") }

    Button(
        onClick = { showDialog = true },
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Text("🖨️ Test Print")
    }

    statusMessage?.let {
        Text(
            text = it,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 4.dp)
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSending) showDialog = false },
            title = { Text("Test Print ke Print Server") },
            text = {
                Column {
                    OutlinedTextField(
                        value = serverIp,
                        onValueChange = { serverIp = it },
                        label = { Text("IP Print Server") },
                        enabled = !isSending
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = copiesText,
                        onValueChange = { input -> copiesText = input.filter { it.isDigit() } },
                        label = { Text("Jumlah Copies") },
                        enabled = !isSending
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !isSending,
                    onClick = {
                        val copies = copiesText.toIntOrNull() ?: 1
                        isSending = true
                        statusMessage = "Mengirim ke print server..."

                        val photoFile = bitmapToJpegFile(context, resultBitmap)
                        PrintServerClient.sendPrintJob(
                            serverIp = serverIp.trim(),
                            photoFile = photoFile,
                            copies = copies
                        ) { result ->
                            // Callback OkHttp jalan di background thread -> pindah ke main thread
                            Handler(Looper.getMainLooper()).post {
                                isSending = false
                                statusMessage = when (result) {
                                    is PrintServerClient.PrintResult.Success ->
                                        "✅ Print dikirim! Job ID: ${result.jobId} (status: ${result.status})"
                                    is PrintServerClient.PrintResult.Failure ->
                                        "❌ Print gagal: ${result.errorMessage}"
                                }
                                showDialog = false
                            }
                        }
                    }
                ) {
                    Text(if (isSending) "Mengirim..." else "Kirim")
                }
            },
            dismissButton = {
                Button(onClick = { if (!isSending) showDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

/** Simpan Bitmap hasil akhir ke file JPEG sementara di cache, untuk dikirim ke print server. */
private fun bitmapToJpegFile(context: Context, bitmap: Bitmap): File {
    val file = File(context.cacheDir, "print_test_${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
    }
    return file
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