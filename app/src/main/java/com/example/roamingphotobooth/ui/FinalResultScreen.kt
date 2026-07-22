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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import com.example.roamingphotobooth.print.PrintJobStatus
import com.example.roamingphotobooth.print.PrintJobWebSocketClient
import com.example.roamingphotobooth.print.PrintServerInfo
import com.example.roamingphotobooth.print.ActivePrintJobTracker
import androidx.compose.runtime.rememberCoroutineScope
import com.example.roamingphotobooth.print.PrintServerRepository


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
 *
 * <-- BARU: pencarian/pemilihan printer TIDAK lagi terjadi di sini — itu sudah
 * dipindah ke Pengaturan > Printer (lihat settings.PrinterSettingsScreen, yang
 * juga yang menyimpan pilihan lewat PrintServerRepository.selectServer()).
 * Di sini kita cuma BACA printer yang sudah tersimpan, lalu user tinggal
 * menentukan jumlah cetak (NumericStepper) dan menekan "Kirim".
 */
@Composable
private fun TestPrintButton(resultBitmap: Bitmap) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showDialog by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var copies by remember { mutableStateOf(1) }

    // WebSocket client + tracker, hidup selama composable ini ada di layar.
    val wsClient = remember { PrintJobWebSocketClient() }
    val activePrintJobTracker = remember { ActivePrintJobTracker(wsClient, coroutineScope) }
    val realtimeStatus by activePrintJobTracker.status.collectAsState()

    // <-- BARU: cukup baca printer yang sudah dipilih lewat Pengaturan > Printer.
    // Tidak ada discovery/mDNS lagi di layar ini.
    val serverRepository = remember { PrintServerRepository.getInstance(context) }
    val selectedServer by serverRepository.server.collectAsState()

    DisposableEffect(Unit) {
        onDispose { wsClient.disconnect() }
    }

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

    PrintRealtimeStatusText(realtimeStatus)

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSending) showDialog = false },
            title = { Text("Cetak Hasil Foto") },
            text = {
                Column {
                    // <-- BARU: printer sudah pasti hasil pilihan di Pengaturan, jadi
                    // di sini cukup ditampilkan (read-only), bukan dicari lagi.
                    SelectedPrinterSummary(server = selectedServer)

                    Spacer(modifier = Modifier.height(12.dp))
                    NumericStepper(
                        value = copies,
                        onValueChange = { copies = it },
                        enabled = !isSending,
                        label = "Jumlah Cetak"
                    )
                }
            },
            confirmButton = {
                Button(
                    // <-- BARU: tombol kirim aktif hanya kalau ada printer tersimpan.
                    enabled = !isSending && selectedServer != null,
                    onClick = {
                        val server = selectedServer ?: return@Button
                        isSending = true
                        statusMessage = "Mengirim ke print server..."

                        // Pastikan WebSocket tersambung ke server yang sama sebelum job dikirim.
                        wsClient.connect(server)

                        val photoFile = bitmapToJpegFile(context, resultBitmap)
                        PrintServerClient.sendPrintJob(
                            serverIp = server.host, // <-- dari printer tersimpan (Pengaturan), bukan input manual
                            port = server.port,
                            photoFile = photoFile,
                            copies = copies
                        ) { result ->
                            Handler(Looper.getMainLooper()).post {
                                isSending = false
                                statusMessage = when (result) {
                                    is PrintServerClient.PrintResult.Success -> {
                                        activePrintJobTracker.trackJob(result.jobId)
                                        "✅ Print dikirim! Job ID: ${result.jobId} (status: ${result.status})"
                                    }
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

/**
 * <-- BARU: menggantikan PrintServerDiscoveryStatus (yang dulu aktif mencari
 * lewat mDNS di sini). Sekarang cuma menampilkan printer yang SUDAH dipilih
 * lewat Pengaturan > Printer — kalau belum ada, arahkan user ke sana.
 */
@Composable
private fun SelectedPrinterSummary(server: PrintServerInfo?) {
    if (server != null) {
        Column {
            Text(
                text = "🖨️ Printer: ${server.serviceName}",
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = "${server.host}:${server.port}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    } else {
        Text(
            text = "⚠️ Belum ada printer dipilih. Atur dulu lewat menu Pengaturan > Printer.",
            color = Color(0xFFE57373),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun PrintRealtimeStatusText(status: PrintJobStatus) {
    val (label, color) = when (status) {
        is PrintJobStatus.Queued -> "⏳ Menunggu antrean..." to Color(0xFFB0BEC5)
        is PrintJobStatus.Printing -> "🖨️ Sedang mencetak..." to Color(0xFF64B5F6)
        is PrintJobStatus.Completed -> "✅ Selesai dicetak" to Color(0xFF81C784)
        is PrintJobStatus.Failed -> "❌ Gagal: ${status.errorMessage ?: "tidak diketahui"}" to Color(0xFFE57373)
        PrintJobStatus.ConnectingToServer -> "🔌 Menghubungkan..." to Color(0xFFB0BEC5)
        PrintJobStatus.Disconnected -> "⚠️ Terputus dari server" to Color(0xFFFFB74D)
        PrintJobStatus.Idle -> return
    }
    Text(
        text = label,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(top = 4.dp)
    )
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