package com.example.roamingphotobooth.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
 * <-- BARU: menentukan arti tombol utama (ikon panah) di FinalResultScreen.
 * - [SESSION_END] (default, dipakai MobileBoothScreen/StandBoothScreen begitu
 *   sesi photobooth baru selesai): panah MAJU ("Lanjut") -> mulai sesi baru.
 * - [GALLERY_RECALL] (dipakai gallery.GalleryScreen lewat MainActivity.galleryOpenEntry
 *   saat user buka lagi 1 entri riwayat dari Galeri): panah KEMBALI ->
 *   balik ke daftar Galeri, BUKAN mulai sesi baru (tidak relevan karena tidak
 *   sedang di tengah sesi photobooth). Tombol QR & Print tetap sama persis
 *   perilakunya di kedua mode -- QR di-regenerate dari landingUrl yang
 *   tersimpan, Print kirim ulang bitmap resolusi penuh yang sama.
 */
enum class FinalResultMode { SESSION_END, GALLERY_RECALL }

/**
 * Layar penuh yang muncul setelah SEMUA slot foto terisi: nampilin hasil akhir
 * + tombol ikon buat lihat QR (scan/download foto dari Drive), lanjut ke sesi
 * baru, dan test print. Dipakai bareng oleh MobileBoothScreen dan
 * StandBoothScreen (tidak perlu diduplikat, karena tampilannya sama untuk
 * kedua mode — kalau nanti perlu beda, tinggal bikin versi khusus per mode).
 *
 * <-- BARU: 3 tombol (QR, Lanjut, Print) sekarang cuma ikon (tanpa teks) dan
 * ukurannya dipatok 7% dari sisi layar yang lebih pendek, supaya tidak
 * kegedean dan konsisten baik di portrait maupun landscape. QR-nya sendiri
 * dipindah jadi popup beranimasi (fade + scale) yang muncul saat tombol QR
 * ditekan, bukan ditampilkan permanen di layar.
 *
 * <-- BARU: layar ini sekarang JUGA dipakai fitur Galeri (recall foto lama)
 * lewat [mode] = [FinalResultMode.GALLERY_RECALL] -- lihat GalleryScreen.kt.
 * Satu-satunya beda tampilan antara kedua mode ada di tombol ikon panah
 * (lihat [FinalResultMode]); tombol QR & Print (termasuk semua logicnya)
 * dipakai APA ADANYA tanpa perubahan sama sekali.
 */
@Composable
fun FinalResultScreen(
    resultBitmap: Bitmap,
    qrCodeBitmap: Bitmap?,
    onContinueClick: () -> Unit,
    mode: FinalResultMode = FinalResultMode.SESSION_END,
    // <-- BARU: byte GIF89a animasi slideshow dari foto-foto ASLI (tanpa frame)
    // sesi ini -- lihat MainActivity.saveMergedBitmap -> PhotoGifBuilder. Null
    // selama belum ada (mis. sesi belum selesai, mode GALLERY_RECALL yang tidak
    // menyimpan GIF-nya secara lokal, atau foto mentahnya kurang dari 2). Kalau
    // ada, ditampilkan sebagai preview kecil berdampingan dengan hasil foto
    // berframe -- lihat [GifPreviewCard].
    gifBytes: ByteArray? = null
) {
    val primaryIcon = if (mode == FinalResultMode.GALLERY_RECALL) Icons.Filled.ArrowBack else Icons.Filled.ArrowForward
    val primaryDescription = if (mode == FinalResultMode.GALLERY_RECALL) "Kembali ke Galeri" else "Lanjut (Sesi Baru)"
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

        // <-- BARU: ukuran tombol dipatok ke 7% dari sisi TERPENDEK layar
        // (bukan lebar atau tinggi saja). Kalau dipatok ke lebar saja, di
        // landscape tombolnya jadi kegedean; kalau ke tinggi saja, di
        // portrait jadi kegedean. Pakai sisi terpendek supaya ukurannya tetap
        // proporsional & konsisten pas layar diputar.
        val buttonSize = minOf(maxWidth, maxHeight) * 0.07f

        // <-- BARU: ukuran preview GIF SENGAJA dipatok kecil (proporsional ke sisi
        // terpendek layar, sama pola dengan buttonSize di atas, tapi dibatasi
        // maksimal 140dp) supaya tidak mengganggu/menutupi hasil foto berframe yang
        // tetap jadi konten utama layar ini.
        val gifPreviewSize = (minOf(maxWidth, maxHeight) * 0.22f).coerceAtMost(140.dp)

        var showQrPopup by remember { mutableStateOf(false) }

        if (isLandscape) {
            // Landscape: foto hasil di kiri (mengisi sisa ruang), tombol-tombol
            // ikon ditumpuk vertikal di strip sempit sebelah kanan.
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    bitmap = resultBitmap.asImageBitmap(),
                    contentDescription = "Hasil Akhir",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(16.dp)
                )

                Column(
                    modifier = Modifier
                        .width(buttonSize + 48.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF262A33))
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
                ) {
                    CircleIconButton(
                        icon = Icons.Filled.QrCode2,
                        contentDescription = "Tampilkan QR",
                        size = buttonSize,
                        onClick = { showQrPopup = true }
                    )

                    CircleIconButton(
                        icon = primaryIcon,
                        contentDescription = primaryDescription,
                        size = buttonSize,
                        onClick = onContinueClick
                    )

                    // Fitur test terpisah — tidak menyentuh alur di atas
                    TestPrintButton(resultBitmap = resultBitmap, buttonSize = buttonSize)
                }
            }
        } else {
            // Portrait: foto hasil di atas (mengisi sisa ruang), tombol-tombol
            // ikon berjajar horizontal di strip sempit bawah.
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(buttonSize + 32.dp)
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircleIconButton(
                        icon = Icons.Filled.QrCode2,
                        contentDescription = "Tampilkan QR",
                        size = buttonSize,
                        onClick = { showQrPopup = true }
                    )

                    CircleIconButton(
                        icon = primaryIcon,
                        contentDescription = primaryDescription,
                        size = buttonSize,
                        onClick = onContinueClick
                    )

                    // Fitur test terpisah — tidak menyentuh alur di atas
                    TestPrintButton(resultBitmap = resultBitmap, buttonSize = buttonSize)
                }
            }
        }

        // <-- BARU: preview kecil animasi GIF (slideshow foto-foto ASLI tanpa
        // frame), ditumpuk di pojok kiri-atas layar -- berdampingan dengan hasil
        // foto berframe yang tetap jadi konten utama, di landscape maupun portrait
        // (pojok ini selalu kosong di kedua layout, tidak menutupi tombol ikon
        // ataupun foto hasil). Cuma dirender kalau [gifBytes] tersedia -- lihat
        // dokumentasi param-nya di atas.
        if (gifBytes != null) {
            GifPreviewCard(
                gifBytes = gifBytes,
                size = gifPreviewSize,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            )
        }

        // <-- BARU: popup QR beranimasi, ditumpuk di atas layout manapun
        // (landscape/portrait) karena ditaruh sebagai child terakhir di
        // BoxWithConstraints ini. Muncul dengan fade + scale-in, hilang
        // dengan fade + scale-out saat ditutup.
        AnimatedVisibility(
            visible = showQrPopup,
            enter = fadeIn(animationSpec = tween(200)) + scaleIn(initialScale = 0.85f, animationSpec = tween(220)),
            exit = fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.85f, animationSpec = tween(150)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showQrPopup = false },
                contentAlignment = Alignment.Center
            ) {
                // Box pembungkus kartu supaya klik di kartunya sendiri tidak
                // ikut menutup popup (cuma klik di area gelap sekitarnya).
                Box(
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* no-op, cegah klik tembus ke scrim */ }
                ) {
                    QrCard(qrCodeBitmap = qrCodeBitmap)
                }
            }
        }
    }
}

/**
 * <-- BARU: tombol bundar berisi ikon saja (tanpa teks), dipakai untuk
 * ketiga aksi di layar ini (QR, Lanjut, Print) supaya konsisten & hemat
 * tempat. Ukurannya (size) ditentukan dari luar — 7% sisi terpendek layar.
 */
@Composable
private fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    size: Dp,
    backgroundColor: Color = Color.White,
    iconTint: Color = Color(0xFF262A33),
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = backgroundColor,
        modifier = Modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(size * 0.55f)
            )
        }
    }
}

/**
 * <-- BARU: kartu preview kecil animasi GIF (slideshow foto-foto ASLI tanpa
 * frame dari sesi ini, lihat MainActivity.saveMergedBitmap -> PhotoGifBuilder).
 * Pakai [AndroidView] + ImageDecoder/AnimatedImageDrawable bawaan Android (API
 * 28+, Android 9) supaya animasinya jalan tanpa perlu nambah library GIF pihak
 * ketiga (mis. Coil/Glide) -- di device API 26-27 (minSdk app ini), fallback
 * cuma menampilkan frame PERTAMA GIF-nya secara statis (ImageDecoder/
 * AnimatedImageDrawable belum ada di API itu), tidak crash ataupun kosong.
 */
@Composable
private fun GifPreviewCard(gifBytes: ByteArray, size: Dp, modifier: Modifier = Modifier) {
    Surface(
        color = Color.Black,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .size(size)
            .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    android.widget.ImageView(context).apply {
                        scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                    }
                },
                // update(...) dipanggil ulang tiap [gifBytes] berubah (recomposition
                // key-nya otomatis mengikuti parameter lambda ini) -- di app ini
                // gifBytes cuma di-set sekali per sesi (lihat MainActivity), jadi
                // dalam praktiknya cuma jalan 1x per kemunculan layar ini.
                update = { imageView ->
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        val source = android.graphics.ImageDecoder.createSource(
                            java.nio.ByteBuffer.wrap(gifBytes)
                        )
                        val drawable = android.graphics.ImageDecoder.decodeDrawable(source)
                        imageView.setImageDrawable(drawable)
                        (drawable as? android.graphics.drawable.Animatable)?.start()
                    } else {
                        // Fallback API < 28 -- lihat catatan di dokumentasi composable ini.
                        val staticBitmap = android.graphics.BitmapFactory.decodeByteArray(
                            gifBytes, 0, gifBytes.size
                        )
                        imageView.setImageBitmap(staticBitmap)
                    }
                }
            )

            // Label kecil "GIF" di pojok, sekadar penanda supaya jelas beda dari
            // hasil foto berframe di sebelahnya.
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(bottomEnd = 8.dp),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    text = "GIF",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/**
 * Tombol "Test Print" — fitur terpisah untuk uji coba kirim foto hasil akhir
 * ke Print Server Windows lewat HTTP. Tidak berpengaruh ke alur photobooth
 * utama (onContinueClick, upload Drive, dsb).
 *
 * <-- BARU: sekarang cuma ikon printer (bagian dari 3 tombol ikon 7%),
 * status pengiriman & realtime print ditampilkan sebagai caption kecil di
 * bawah ikonnya saja saat sedang berjalan.
 *
 * <-- pencarian/pemilihan printer TIDAK terjadi di sini — itu sudah
 * dipindah ke Pengaturan > Printer (lihat settings.PrinterSettingsScreen, yang
 * juga yang menyimpan pilihan lewat PrintServerRepository.selectServer()).
 * Di sini kita cuma BACA printer yang sudah tersimpan, lalu user tinggal
 * menentukan jumlah cetak (NumericStepper) dan menekan "Kirim".
 */
@Composable
private fun TestPrintButton(resultBitmap: Bitmap, buttonSize: Dp) {
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

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircleIconButton(
            icon = Icons.Filled.Print,
            contentDescription = "Test Print",
            size = buttonSize,
            onClick = { showDialog = true }
        )

        statusMessage?.let {
            Text(
                text = it,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .width(buttonSize + 72.dp)
            )
        }

        PrintRealtimeStatusText(realtimeStatus, maxWidth = buttonSize + 72.dp)
    }

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
private fun PrintRealtimeStatusText(status: PrintJobStatus, maxWidth: Dp) {
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
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .padding(top = 4.dp)
            .width(maxWidth)
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
 * Kartu QR: begitu foto selesai ke-upload ke Drive, QR-nya ditampilkan di
 * sini supaya user bisa langsung scan buat lihat/download fotonya sendiri.
 * Selama upload masih jalan di background, tampilkan status "menyiapkan".
 *
 * <-- BARU: sekarang cuma dipakai di dalam popup beranimasi (dipicu dari
 * tombol ikon QR), bukan lagi ditampilkan permanen di layar. Ukuran QR
 * dibikin lebih besar dari sebelumnya (180dp) karena sekarang ini konten
 * utama popup, bukan elemen kecil yang berbagi tempat dengan tombol lain.
 */
@Composable
private fun QrCard(qrCodeBitmap: Bitmap?) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(24.dp)
    ) {
        if (qrCodeBitmap != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(20.dp)
            ) {
                Image(
                    bitmap = qrCodeBitmap.asImageBitmap(),
                    contentDescription = "QR Foto",
                    modifier = Modifier.size(180.dp)
                )
                Text(
                    text = "📱 Scan buat lihat/download foto",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Black,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(24.dp)
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