package com.example.roamingphotobooth.booth.mobile

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.roamingphotobooth.template.PhotoSlot
import com.example.roamingphotobooth.template.PhotoTemplate
import com.example.roamingphotobooth.ui.FinalResultScreen

/**
 * Layar booth mode MOBILE.
 *
 * File ini SENGAJA TERPISAH dari StandBoothScreen (beda folder/package:
 * booth.mobile vs booth.stand) — supaya gampang diubah salah satu tanpa
 * mempengaruhi yang lain. Kalau nanti mode Mobile butuh tampilan/perilaku
 * beda dari Stand, edit di sini saja.
 *
 * Area bingkai (frame PNG + live view) di-render di dalam kotak yang mengikuti
 * RASIO ASPEK ASLI bingkai (bukan fillMaxSize + Crop seperti sebelumnya) —
 * supaya bingkai selalu tampil UTUH ("contain") pas di layar, tidak kepotong
 * atau ke-stretch di HP dengan rasio layar yang beda-beda. Lihat [FrameCaptureArea].
 *
 * Kalau ada template aktif (activeTemplate != null), live view HANYA muncul di
 * slot yang lagi jadi TARGET foto berikutnya (posisi & ukurannya dihitung dari
 * PhotoSlot.xRatio/yRatio/widthRatio/heightRatio, sama seperti cara TemplateEditorScreen
 * menghitung posisi SlotEditorBox) — bukan di seluruh belakang bingkai. Slot yang
 * sudah terisi foto sebelumnya ditampilkan lewat [previewBitmap] (frame + foto-foto
 * yang sudah ke-capture, sudah digabung TemplateSessionManager.buildPreviewImage).
 * Kalau belum ada template aktif, fallback ke live view full di seluruh area bingkai
 * (behavior lama, dipakai kalau user belum pernah pilih frame).
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
    previewBitmap: Bitmap?,
    activeTemplate: PhotoTemplate?,
    currentSlotNumber: Int,
    totalSlots: Int,
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
            // Sesi masih jalan (atau belum mulai): bingkai + live view, di-fit pas
            // di layar (lihat FrameCaptureArea).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                FrameCaptureArea(
                    liveViewBitmap = liveViewBitmap,
                    frameOverlayBitmap = frameOverlayBitmap,
                    previewBitmap = previewBitmap,
                    activeTemplate = activeTemplate,
                    currentSlotNumber = currentSlotNumber,
                    totalSlots = totalSlots
                )
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

/**
 * Area bingkai + live view, di-render di dalam kotak berukuran mengikuti rasio
 * aspek ASLI bingkai (frameWidthPx / frameHeightPx dari template aktif). Pakai
 * BoxWithConstraints supaya bisa hitung sendiri sisi mana (lebar atau tinggi)
 * yang jadi pembatas ("contain" / letterbox), alih-alih fillMaxSize + Crop yang
 * bikin bingkai kepotong/gepeng tergantung rasio layar HP.
 */
@Composable
private fun FrameCaptureArea(
    liveViewBitmap: Bitmap?,
    frameOverlayBitmap: Bitmap?,
    previewBitmap: Bitmap?,
    activeTemplate: PhotoTemplate?,
    currentSlotNumber: Int,
    totalSlots: Int
) {
    // Bitmap teratas yg digambar di atas live view: preview (bingkai + foto2 yang
    // sudah ke-capture di slot masing2, slot yang belum diisi transparan) kalau ada,
    // fallback ke bingkai polos kalau sesi baru mulai / belum ada foto masuk sama sekali.
    val topBitmap = previewBitmap ?: frameOverlayBitmap

    // Rasio aspek bingkai: utamakan ukuran ASLI dari template aktif (paling akurat,
    // sama persis dengan yang dipakai TemplateEditorScreen buat hitung posisi slot),
    // fallback ke rasio bitmap kalau tidak ada template, fallback lagi ke potret 3:4.
    val frameAspectRatio = when {
        activeTemplate != null && activeTemplate.frameHeightPx > 0 ->
            activeTemplate.frameWidthPx.toFloat() / activeTemplate.frameHeightPx.toFloat()
        topBitmap != null && topBitmap.height > 0 ->
            topBitmap.width.toFloat() / topBitmap.height.toFloat()
        else -> 3f / 4f
    }

    // Slot yang jadi TARGET foto berikutnya. Bisa lebih dari 1 slot kalau ada
    // slot "duplikat" (order sama — lihat PhotoSlot.order) yang harus keisi 1
    // foto yang sama; live view ditampilkan di SEMUA slot itu sekaligus.
    val targetSlots: List<PhotoSlot> =
        if (activeTemplate != null && totalSlots > 0 && currentSlotNumber in 1..totalSlots) {
            activeTemplate.slots.filter { it.order == currentSlotNumber }
        } else {
            emptyList()
        }

    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val availableAspect = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else frameAspectRatio
        // Kalau area yang tersedia lebih "lebar" dari bingkai, tinggi yang jadi
        // pembatas (fillMaxHeight); kalau lebih "kurus/tinggi", lebar yang jadi
        // pembatas (fillMaxWidth). Hasilnya bingkai selalu utuh kelihatan, pas
        // di tengah layar, tidak pernah overflow ke luar layar.
        val fitModifier = if (availableAspect > frameAspectRatio) {
            Modifier.fillMaxHeight()
        } else {
            Modifier.fillMaxWidth()
        }

        var containerWidthPx by remember { mutableStateOf(0f) }
        var containerHeightPx by remember { mutableStateOf(0f) }

        Box(
            modifier = fitModifier
                .aspectRatio(frameAspectRatio)
                .onSizeChanged { size ->
                    containerWidthPx = size.width.toFloat()
                    containerHeightPx = size.height.toFloat()
                }
        ) {
            if (liveViewBitmap != null) {
                if (targetSlots.isNotEmpty() && containerWidthPx > 0f && containerHeightPx > 0f) {
                    // Ada template aktif: live view CUMA muncul di slot yang lagi
                    // dituju — supaya user langsung lihat framing pas buat slot itu,
                    // bukan nebak-nebak posisi dari live view yang penuh 1 layar.
                    targetSlots.forEach { slot ->
                        val xPx = slot.xRatio * containerWidthPx
                        val yPx = slot.yRatio * containerHeightPx
                        val widthPx = slot.widthRatio * containerWidthPx
                        val heightPx = slot.heightRatio * containerHeightPx

                        Box(
                            modifier = Modifier
                                .offset(
                                    x = with(density) { xPx.toDp() },
                                    y = with(density) { yPx.toDp() }
                                )
                                .size(
                                    width = with(density) { widthPx.toDp() },
                                    height = with(density) { heightPx.toDp() }
                                )
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.Black)
                        ) {
                            Image(
                                bitmap = liveViewBitmap.asImageBitmap(),
                                contentDescription = "Live View",
                                contentScale = ContentScale.Crop,
                                // Mirror horizontal (efek cermin) — cuma di layer live
                                // view ini, bitmap asli yang dipakai buat capture/simpan
                                // tidak berubah, ini cuma efek tampilan.
                                modifier = Modifier
                                    .fillMaxSize()
                                    .scale(scaleX = -1f, scaleY = 1f)
                            )
                        }
                    }
                } else {
                    // Belum ada template aktif (fallback lama): live view isi seluruh
                    // area bingkai.
                    Image(
                        bitmap = liveViewBitmap.asImageBitmap(),
                        contentDescription = "Live View",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(scaleX = -1f, scaleY = 1f)
                    )
                }
            }

            // Bingkai (+ foto2 yang sudah terisi di slot lain) numpuk di atas.
            // Pakai Fit (bukan Crop) supaya bingkai selalu utuh sesuai rasio
            // aslinya — kotak pembungkusnya sendiri sudah pas rasio bingkai,
            // jadi Fit di sini otomatis ngisi penuh tanpa letterbox tambahan.
            if (topBitmap != null) {
                Image(
                    bitmap = topBitmap.asImageBitmap(),
                    contentDescription = "Bingkai",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Progress slot — misal "Foto 2 / 4", biar user tau lagi ngisi slot mana.
            if (totalSlots > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp),
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
        }
    }
}
