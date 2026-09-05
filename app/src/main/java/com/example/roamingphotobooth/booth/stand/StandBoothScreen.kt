package com.example.roamingphotobooth.booth.stand

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.roamingphotobooth.R
import com.example.roamingphotobooth.settings.AppearanceSettings
import com.example.roamingphotobooth.ui.FinalResultScreen

/**
 * Layar booth — dipakai BERSAMA oleh mode STAND dan mode MOBILE (lihat
 * MobileBoothScreen, yang sekarang cuma wrapper tipis di atas composable ini).
 *
 * Tampilan Mobile & Stand SENGAJA DISAMAKAN persis di sini: satu-satunya
 * perbedaan fungsional antara kedua mode ada di BAGAIMANA foto diambil —
 * Stand (dan Mobile saat Developer Mode aktif, lihat DeviceCameraSession) pakai
 * tombol shutter DI LAYAR (software trigger: tap -> countdown 3-2-1 -> app kirim
 * command capture ke kamera), sedangkan Mobile dengan kamera eksternal beneran
 * pakai tombol shutter FISIK di badan kamera (tidak ada tombol di layar sama
 * sekali — lihat [showShutterButton]). Begitu foto masuk (dengan cara apa pun),
 * alurnya SAMA: layar REVIEW muncul (foto + tombol Retake / Lanjut), dan "Lanjut"
 * baru commit foto itu ke slot & lanjut ke slot berikutnya (atau ke hasil akhir
 * kalau itu slot terakhir). Logic pemicu capture & pengisian review ada di
 * MainActivity, bukan di sini.
 *
 * Layout: saat sesi capture, layar dibagi dua (kiri ~30% / kanan ~70%). Kiri
 * nampilin preview frame (frame + foto-foto yang sudah ke-capture, terisi di
 * slot masing-masing — update tiap foto baru masuk). Kanan nampilin live view
 * kamera + (kalau [showShutterButton] true) tombol shutter.
 */
@Composable
fun StandBoothScreen(
    status: String,
    liveViewBitmap: Bitmap?,
    previewBitmap: Bitmap?,
    finalResultBitmap: Bitmap?,
    qrCodeBitmap: Bitmap?,
    // <-- BARU: byte GIF89a animasi slideshow dari foto-foto mentah (tanpa frame)
    // sesi yang baru selesai (lihat MainActivity.saveMergedBitmap ->
    // PhotoGifBuilder). Null selama belum ada sesi selesai, atau kalau foto
    // mentahnya kurang dari 2 (tidak ada gunanya dianimasikan) -- diteruskan apa
    // adanya ke FinalResultScreen. Default null supaya pemanggil lama yang belum
    // diupdate (mis. galeri recall) tetap kompilasi.
    gifBytes: ByteArray? = null,
    countdownValue: Int?,
    isCapturing: Boolean,
    isProcessing: Boolean,
    reviewBitmap: Bitmap?,
    currentSlotNumber: Int,
    totalSlots: Int,
    // <-- BARU: setting Mirror aktif untuk SESI INI (lihat MainActivity.sessionMirrorEnabled).
    // Toggle switch-nya ada di Stand Preview (CaptureContent) lewat [onMirrorToggle].
    mirrorEnabled: Boolean,
    onMirrorToggle: (Boolean) -> Unit,
    onBackClick: () -> Unit,
    onContinueClick: () -> Unit,
    onShutterClick: () -> Unit,
    onRetakeClick: () -> Unit,
    onAcceptClick: () -> Unit,
    // <-- BARU: fitur "Retake slot sebelumnya". [filledSlots] = daftar foto yang
    // SUDAH ke-capture (nomor slot + thumbnail-nya, urut nomor -- lihat
    // TemplateSessionManager.capturedPhotosSnapshot), dipakai ngisi dialog pilih
    // slot (lihat RetakeSlotDialog di bawah). [onRetakeSlotClick] dipanggil dengan
    // nomor slot yang user pilih dari dialog itu -- MainActivity yang tanggung
    // jawab buang foto slot itu (lewat TemplateSessionManager.removePhotoAt) &
    // bikin nextSlotOrder() balik ngarah ke situ. Default kosong/no-op supaya
    // pemanggil lama yang belum diupdate tetap kompilasi.
    filledSlots: List<Pair<Int, Bitmap>> = emptyList(),
    onRetakeSlotClick: (Int) -> Unit = {},
    // <-- BARU: kontrol tombol shutter DI LAYAR. Default true (perilaku Stand
    // asli, tidak berubah). Mobile dengan kamera eksternal set ini FALSE karena
    // capture dipicu lewat tombol fisik di kamera, bukan lewat app — lihat
    // MobileBoothScreen.showDeviceCameraShutter (kebalikannya: dipakai buat
    // Developer Mode, yang TETAP butuh tombol di layar karena tidak ada tombol
    // fisik terpisah untuk kamera depan perangkat).
    showShutterButton: Boolean = true,
    // <-- BARU: tombol pengaturan (ganti frame/template) di pojok kanan atas.
    // Default false (Stand tidak pernah menampilkannya — pemilihan frame Stand
    // terjadi SEBELUM masuk sesi lewat MainActivity.openFramePicker()). Mobile set ini true
    // supaya user bisa ganti frame di tengah sesi lewat TemplateEditorActivity.
    showSettingsButton: Boolean = false,
    onSettingsClick: () -> Unit = {},
    // <-- BARU: dipakai untuk (1) tombol shutter di sesi capture -- tampilannya
    // disamakan dengan tombol "Mulai" di HomeScreen, pakai logo & warna yang
    // sama (lihat AppearanceSettings.startButtonIconColorArgb/startButtonSizeDp
    // & CaptureContent di bawah) -- dan (2) kontrol tampil/sembunyi kotak
    // status lewat [AppearanceSettings.showStatusText]. Default instance
    // kosong supaya pemanggil lama yang belum diupdate tetap kompilasi.
    appearance: AppearanceSettings = AppearanceSettings()
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
                    gifBytes = gifBytes,
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
                    mirrorEnabled = mirrorEnabled,
                    showShutterButton = showShutterButton,
                    onShutterClick = onShutterClick,
                    appearance = appearance
                )

                // <-- BARU: tombol "Retake slot sebelumnya" -- pojok kanan atas, sejajar
                // dgn tombol lain. Cuma aktif kalau ada minimal 1 foto yang sudah
                // ke-capture DAN tidak lagi ada capture/proses/countdown berjalan
                // (jangan sampai user buka dialog pilih slot pas kamera lagi nembak).
                var showRetakeDialog by remember { mutableStateOf(false) }
                val retakeEnabled = filledSlots.isNotEmpty() &&
                    countdownValue == null && !isCapturing && !isProcessing

                // Tombol back/setting + toggle Mirror — pojok kanan atas, sejajar.
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MirrorToggle(checked = mirrorEnabled, onCheckedChange = onMirrorToggle)
                    IconButton(
                        onClick = { showRetakeDialog = true },
                        enabled = retakeEnabled
                    ) {
                        Text(
                            text = "🔁",
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (retakeEnabled) Color.White else Color.White.copy(alpha = 0.35f)
                        )
                    }
                    if (showSettingsButton) {
                        IconButton(onClick = onSettingsClick) {
                            // <-- BERUBAH: dulu emoji "⚙️", sekarang diseragamkan
                            // pakai logo setting yang sama di seluruh app (lihat
                            // res/drawable/ic_settings_logo.xml & HomeScreen).
                            Icon(
                                painter = painterResource(id = R.drawable.ic_settings_logo),
                                contentDescription = "Pengaturan",
                                tint = Color.White
                            )
                        }
                    }
                    IconButton(onClick = onBackClick) {
                        Text(
                            text = "←",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White
                        )
                    }
                }

                if (showRetakeDialog) {
                    RetakeSlotDialog(
                        filledSlots = filledSlots,
                        onDismiss = { showRetakeDialog = false },
                        onSlotSelected = { order ->
                            showRetakeDialog = false
                            onRetakeSlotClick(order)
                        }
                    )
                }
            }
        }

        // Status text — pojok kiri atas, bisa disembunyikan lewat setting
        // "Tampilkan Status Text" di Settings > Appearance (default ON, lihat
        // AppearanceSettings.showStatusText).
        if (appearance.showStatusText) {
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
    mirrorEnabled: Boolean,
    // <-- BARU: lihat StandBoothScreen.showShutterButton. False -> tombol shutter
    // di layar disembunyikan total (Mobile kamera eksternal: capture dipicu lewat
    // tombol fisik di kamera, bukan lewat app).
    showShutterButton: Boolean,
    onShutterClick: () -> Unit,
    // <-- BARU: dipakai supaya tombol shutter pakai logo & warna yang SAMA
    // dengan tombol "Mulai" di HomeScreen (lihat komentar di tombol shutter
    // di bawah).
    appearance: AppearanceSettings
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
                    // Mirror horizontal (efek cermin), cuma diterapkan kalau setting
                    // Mirror Camera aktif untuk sesi ini (lihat [mirrorEnabled]) —
                    // konsisten dengan mirror yang diterapkan ke bitmap hasil capture
                    // di TemplateSessionManager.addPhoto (lihat mirrorEnabled param).
                    modifier = Modifier
                        .fillMaxSize()
                        .let { if (mirrorEnabled) it.scale(scaleX = -1f, scaleY = 1f) else it }
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

            // Tombol shutter — bulat, di bawah tengah. Cuma tampil kalau capture
            // dipicu lewat app (software trigger); Mobile kamera eksternal pakai
            // tombol fisik di kamera, jadi tidak butuh tombol ini sama sekali.
            //
            // <-- BERUBAH: tampilannya SEKARANG DISAMAKAN dengan tombol "Mulai"
            // di HomeScreen — logo kamera yang sama (ic_start_camera_logo),
            // di-tint & diukur pakai setting yang sama
            // (appearance.startButtonIconColorArgb / startButtonSizeDp), tanpa
            // latar lingkaran solid seperti sebelumnya. Selagi isCapturing/
            // isProcessing, tombolnya otomatis meredup (mengikuti `enabled`)
            // dan label kecil di bawah logo menjelaskan statusnya.
            if (showShutterButton) {
                val shutterSize = appearance.startButtonSizeDp.dp
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                ) {
                    IconButton(
                        onClick = onShutterClick,
                        enabled = countdownValue == null && !isCapturing && !isProcessing,
                        modifier = Modifier.size(shutterSize)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_start_camera_logo),
                            contentDescription = "Ambil Foto",
                            tint = Color(appearance.startButtonIconColorArgb),
                            modifier = Modifier.size(shutterSize)
                        )
                    }
                    if (isProcessing || isCapturing) {
                        Text(
                            text = if (isProcessing) "💾 Menyimpan..." else "...",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }
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

/**
 * <-- BARU: dialog "Retake slot mana?" -- muncul begitu tombol 🔁 di pojok kanan
 * atas layar capture ditekan. Nampilin semua foto yang SUDAH ke-capture sejauh ini
 * sebagai deretan thumbnail bernomor (bisa di-scroll horizontal kalau slot-nya
 * banyak); tap salah satu = pilih slot itu buat di-retake (foto lama dibuang,
 * jepretan berikutnya masuk ke slot itu lagi -- lihat
 * TemplateSessionManager.removePhotoAt & MainActivity.retakeSlot). Tidak ada
 * konfirmasi tambahan setelah tap thumbnail -- dialog ini SENDIRI sudah jadi langkah
 * konfirmasi (user harus sengaja buka dialog dulu lewat tombol 🔁), jadi menambah
 * satu langkah lagi cuma bikin alurnya lebih lambat tanpa nambah keamanan berarti.
 */
@Composable
private fun RetakeSlotDialog(
    filledSlots: List<Pair<Int, Bitmap>>,
    onDismiss: () -> Unit,
    onSlotSelected: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Retake foto slot mana?") },
        text = {
            Column {
                Text(
                    text = "Pilih salah satu foto di bawah untuk diambil ulang. " +
                        "Foto lama di slot itu akan diganti dengan jepretan baru.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(filledSlots, key = { it.first }) { (order, bitmap) ->
                        RetakeSlotThumbnail(
                            order = order,
                            bitmap = bitmap,
                            onClick = { onSlotSelected(order) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@Composable
private fun RetakeSlotThumbnail(order: Int, bitmap: Bitmap, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Foto slot $order",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(84.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
        )
        Text(
            text = "Slot $order",
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/**
 * Toggle switch Mirror di Stand Preview (lihat spesifikasi Session Preview >
 * Mirror). Mengontrol setting AKTIF untuk sesi yang sedang berjalan — beda
 * dari default global di Settings > Session (lihat SessionSettingsScreen).
 * Perubahan di sini langsung berlaku ke preview live view DAN ke foto hasil
 * capture berikutnya (lewat mirrorEnabled yang diteruskan ke
 * TemplateSessionManager.addPhoto), tanpa mengubah default global.
 */
@Composable
private fun MirrorToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(
        color = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 4.dp)
        ) {
            Text(
                text = "Mirror",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF4DD0E1))
            )
        }
    }
}