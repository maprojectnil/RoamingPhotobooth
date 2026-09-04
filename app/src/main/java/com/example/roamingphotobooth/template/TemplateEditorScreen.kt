package com.example.roamingphotobooth.template

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp


/**
 * Layar editor bingkai — SELALU landscape (dipaksa lewat AndroidManifest supaya
 * proporsi 70/30 di bawah ini konsisten & workspace dapat ruang maksimal).
 *
 * Kiri (~70%): workspace — preview bingkai dengan slot foto yang bisa digeser/diresize.
 * Kanan (~30%): panel kontrol — ganti bingkai, nama template, jumlah slot, daftar slot
 * (duplikat/hapus per slot), dan tombol simpan.
 */
@Composable
fun TemplateEditorScreen(
    viewModel: TemplateEditorViewModel,
    onPickFrameClick: () -> Unit,
    onSaveClick: () -> Unit,
    // Dipanggil dengan pesan singkat (Bahasa Indonesia, siap tampil) setiap kali
    // "Deteksi Otomatis Slot" selesai dijalankan (berhasil ATAU gagal) — layar ini
    // sengaja tidak nge-Toast sendiri (Composable murni, tanpa Context/Activity),
    // jadi pemanggil (TemplateEditorActivity) yang tampilkan lewat Toast.
    onAutoDetectResult: (String) -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF16181D), Color(0xFF1F2229))
                )
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        WorkspacePane(
            viewModel = viewModel,
            modifier = Modifier
                .weight(0.68f)
                .fillMaxHeight()
        )
        ControlPanel(
            viewModel = viewModel,
            onPickFrameClick = onPickFrameClick,
            onSaveClick = onSaveClick,
            onAutoDetectResult = onAutoDetectResult,
            modifier = Modifier
                .weight(0.32f)
                .fillMaxHeight()
        )
    }
}

@Composable
private fun WorkspacePane(
    viewModel: TemplateEditorViewModel,
    modifier: Modifier = Modifier
) {
    var containerWidthPx by remember { mutableStateOf(0f) }
    var containerHeightPx by remember { mutableStateOf(0f) }

    // Garis panduan Smart Snap yang lagi aktif (diisi SlotEditorBox pas drag/resize
    // kena snap, dikosongkan lagi begitu gesture selesai) — dirender di atas semua slot.
    var activeSnapGuides by remember { mutableStateOf(SnapGuides.NONE) }

    val frameBitmap = viewModel.frameBitmap.value

    // Hitung berapa banyak slot lain yang berbagi `order` yang sama (dasar tampilan
    // "shared" pada SlotEditorBox — dipakai user buat tahu slot mana yang bakal
    // otomatis keisi foto yang sama).
    val orderCounts = viewModel.slots.groupingBy { it.order }.eachCount()

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF262A33)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.CollectionsBookmark,
                    contentDescription = null,
                    tint = Color(0xFF4DD0E1)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Workspace Bingkai",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${viewModel.slots.size} slot",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFAAB0BC)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Area preview: bingkai + semua slot yang bisa di-drag/resize.
            // PENTING: tinggi kotak mengikuti rasio aspek ASLI frame (bukan tinggi tetap),
            // supaya gambar frame (ContentScale.Fit) mengisi PERSIS seluruh kotak tanpa
            // letterbox. Kalau ada letterbox (spasi kosong kiri-kanan/atas-bawah), rasio
            // slot yang dihitung relatif terhadap kotak akan MELESET dari posisi asli di
            // frame saat dirender ulang.
            val frameAspectRatio = if (frameBitmap != null && frameBitmap.height > 0) {
                frameBitmap.width.toFloat() / frameBitmap.height.toFloat()
            } else {
                4f / 3f
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(frameAspectRatio)
                        .clip(RoundedCornerShape(12.dp))
                        .background(checkerBrush())
                        .onSizeChanged { size ->
                            containerWidthPx = size.width.toFloat()
                            containerHeightPx = size.height.toFloat()
                        }
                ) {
                    if (frameBitmap != null) {
                        Image(
                            bitmap = frameBitmap.asImageBitmap(),
                            contentDescription = "Preview Bingkai",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Belum ada bingkai dipilih",
                                color = Color(0xFF9AA0AC)
                            )
                        }
                    }

                    if (containerWidthPx > 0 && containerHeightPx > 0) {
                        viewModel.slots.forEachIndexed { index, slot ->
                            SlotEditorBox(
                                slot = slot,
                                containerWidthPx = containerWidthPx,
                                containerHeightPx = containerHeightPx,
                                isShared = (orderCounts[slot.order] ?: 0) > 1,
                                snapSettings = viewModel.snapSettings.value,
                                onSlotChanged = { updated -> viewModel.updateSlot(index, updated) },
                                onDuplicateClick = { viewModel.duplicateSlot(index) },
                                onDeleteClick = { viewModel.removeSlotAt(index) },
                                onSnapGuidesChanged = { guides -> activeSnapGuides = guides }
                            )
                        }

                        // Garis panduan Smart Snap — digambar PALING ATAS supaya kelihatan
                        // jelas di atas bingkai & slot saat lagi nge-snap.
                        SnapGuideOverlay(
                            guides = activeSnapGuides,
                            containerWidthPx = containerWidthPx,
                            containerHeightPx = containerHeightPx,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Geser slot untuk memindah • tarik pojok kanan-bawah untuk resize dari " +
                        "tengah (Scale from Center) • ikon salin untuk duplikat slot (1 foto dipakai " +
                        "di beberapa posisi) • slot otomatis nyantol (Smart Snap) ke garis seperempat " +
                        "bingkai secara horizontal",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF7E8592)
            )
        }
    }
}

/**
 * Gambar garis panduan Smart Snap (dashed) di atas workspace, satu garis tegak per
 * entri di [SnapGuides.vertical] dan satu garis mendatar per entri di
 * [SnapGuides.horizontal] — warnanya beda-beda sesuai [SnapType] biar user gampang
 * ngenalin lagi nyantol ke garis jenis apa (Center/Thirds/Quarters/Edges).
 */
@Composable
private fun SnapGuideOverlay(
    guides: SnapGuides,
    containerWidthPx: Float,
    containerHeightPx: Float,
    modifier: Modifier = Modifier
) {
    if (guides.isEmpty) return

    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)

    Canvas(modifier = modifier) {
        guides.vertical.forEach { (ratio, type) ->
            val x = ratio * containerWidthPx
            drawLine(
                color = SmartSnap.colorFor(type),
                start = Offset(x, 0f),
                end = Offset(x, containerHeightPx),
                strokeWidth = 2.5f,
                pathEffect = dashEffect
            )
        }
        guides.horizontal.forEach { (ratio, type) ->
            val y = ratio * containerHeightPx
            drawLine(
                color = SmartSnap.colorFor(type),
                start = Offset(0f, y),
                end = Offset(containerWidthPx, y),
                strokeWidth = 2.5f,
                pathEffect = dashEffect
            )
        }
    }
}

/** Pola kotak-kotak abu-abu, penanda area transparan — mirip kanvas editor gambar. */
@Composable
private fun checkerBrush(): Brush {
    return Brush.linearGradient(
        colors = listOf(Color(0xFF32363F), Color(0xFF2B2E36)),
    )
}

@Composable
private fun ControlPanel(
    viewModel: TemplateEditorViewModel,
    onPickFrameClick: () -> Unit,
    onSaveClick: () -> Unit,
    onAutoDetectResult: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Dialog konfirmasi sebelum Deteksi Otomatis MENIMPA slot yang sudah ada —
    // cuma muncul kalau daftar slot sekarang tidak kosong (lihat pemanggilannya di
    // tombol "Deteksi Otomatis Slot" di bawah).
    var showOverwriteConfirm by remember { mutableStateOf(false) }

    fun runAutoDetect() {
        val result = viewModel.autoDetectSlots()
        val message = when (result) {
            is AutoDetectResult.Success ->
                "Deteksi otomatis berhasil: ${result.slotCount} slot ditemukan dari bolongan bingkai. " +
                        "Cek & sesuaikan urutan/posisi tiap slot kalau perlu."
            AutoDetectResult.NoFrame ->
                "Pilih bingkai PNG dulu sebelum pakai deteksi otomatis."
            AutoDetectResult.NoHolesFound ->
                "Tidak ada bolongan transparan yang terdeteksi di PNG ini. Pastikan bingkai " +
                        "punya area transparan (bukan solid) di tempat foto seharusnya muncul."
        }
        onAutoDetectResult(message)
    }

    if (showOverwriteConfirm) {
        AlertDialog(
            onDismissRequest = { showOverwriteConfirm = false },
            title = { Text("Timpa slot yang sudah ada?") },
            text = {
                Text(
                    "Ada ${viewModel.slots.size} slot yang sudah dibuat. Deteksi Otomatis akan " +
                            "menghapus semuanya dan menggantinya dengan slot baru hasil pembacaan " +
                            "bolongan PNG. Lanjutkan?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showOverwriteConfirm = false
                    runAutoDetect()
                }) { Text("Lanjutkan") }
            },
            dismissButton = {
                TextButton(onClick = { showOverwriteConfirm = false }) { Text("Batal") }
            }
        )
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF262A33)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        // PENTING: jendela "Pengaturan Bingkai" ini bisa jadi lebih tinggi dari layar
        // (nama template, jumlah foto, Smart Snap, daftar slot, tombol simpan — makin
        // banyak slot makin panjang), jadi Column-nya dibungkus verticalScroll supaya
        // seluruh isinya tetap bisa dijangkau dengan scroll, gak kepotong/ke-clip.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(18.dp)
        ) {
            Text(
                text = "Pengaturan Bingkai",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Nama template — ketuk untuk memunculkan keyboard
            OutlinedTextField(
                value = viewModel.templateName.value,
                onValueChange = { viewModel.templateName.value = it },
                label = { Text("Nama Template") },
                placeholder = { Text("mis. Wedding Frame 3 Slot") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onPickFrameClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Filled.ImageIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (viewModel.framePath.value == null) "Pilih Bingkai PNG" else "Ganti Bingkai PNG")
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Deteksi Otomatis Slot: baca bolongan (area transparan) di PNG bingkai
            // lalu bikin slot foto otomatis dari situ — user tetap bisa geser/resize/
            // ubah urutan/duplikat/hapus hasilnya seperti slot manual biasa (lihat
            // catatan di TemplateEditorViewModel.autoDetectSlots).
            OutlinedButton(
                onClick = {
                    if (viewModel.slots.isNotEmpty()) {
                        showOverwriteConfirm = true
                    } else {
                        runAutoDetect()
                    }
                },
                enabled = viewModel.framePath.value != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Deteksi Otomatis Slot")
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = Color(0xFF3A3F4A))
            Spacer(modifier = Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Jumlah Foto",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFC9CDD6),
                    modifier = Modifier.weight(1f)
                )
                FilledTonalIconButton(
                    onClick = {
                        if (viewModel.slots.isNotEmpty()) viewModel.removeSlotAt(viewModel.slots.size - 1)
                    }
                ) {
                    Icon(imageVector = Icons.Filled.Remove, contentDescription = "Kurangi slot")
                }
                Text(
                    text = "${viewModel.slots.size}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                FilledTonalIconButton(
                    onClick = { viewModel.setSlotCount(viewModel.slots.size + 1) }
                ) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = "Tambah slot")
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = Color(0xFF3A3F4A))
            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Smart Snap",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFC9CDD6)
            )
            Spacer(modifier = Modifier.height(2.dp))


            val snapSettings = viewModel.snapSettings.value
            SnapToggleRow(
                type = SnapType.QUARTERS,
                checked = snapSettings.enabled,
                onCheckedChange = { viewModel.setSnapEnabled(it) }
            )

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0xFF3A3F4A))
            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Daftar Slot",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFC9CDD6)
            )
            Spacer(modifier = Modifier.height(6.dp))

            val orderCounts = viewModel.slots.groupingBy { it.order }.eachCount()

            // Bukan LazyColumn: panel ini sekarang dibungkus verticalScroll (lihat
            // Column terluar di ControlPanel), jadi daftar slot ikut nge-scroll
            // sebagai bagian dari halaman, bukan area scroll sendiri yang terpisah.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                viewModel.slots.forEachIndexed { index, slot ->
                    val shared = (orderCounts[slot.order] ?: 0) > 1
                    SlotListRow(
                        index = index,
                        slot = slot,
                        allSlots = viewModel.slots,
                        shared = shared,
                        onSwapOrderWith = { otherIndex -> viewModel.swapSlotOrder(index, otherIndex) },
                        onDuplicateFrom = { otherIndex -> viewModel.setSlotSource(index, otherIndex) },
                        onMakeUnique = { viewModel.makeSlotUnique(index) },
                        onDuplicateClick = { viewModel.duplicateSlot(index) },
                        onDeleteClick = { viewModel.removeSlotAt(index) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            ElevatedButton(
                onClick = onSaveClick,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = androidx.compose.material3.ButtonDefaults.elevatedButtonColors(
                    containerColor = Color(0xFF4DD0E1),
                    contentColor = Color(0xFF10131A)
                )
            ) {
                Icon(imageVector = Icons.Filled.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Simpan Template", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** 1 baris toggle untuk 1 jenis Smart Snap — bulatan kecil kiri = warna garis panduannya. */
@Composable
private fun SnapToggleRow(
    type: SnapType,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val guideColor = SmartSnap.colorFor(type)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(guideColor)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = SmartSnap.labelFor(type),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFE4E6EA),
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = guideColor,
                checkedTrackColor = guideColor.copy(alpha = 0.4f)
            )
        )
    }
}

/**
 * 1 baris di "Daftar Slot" untuk slot di [index] (posisi di bingkai TIDAK pernah
 * berubah lewat baris ini — semua aksi di sini cuma memanipulasi `order`, tidak
 * pernah xRatio/yRatio/widthRatio/heightRatio):
 *
 * - Baris 1: label slot + tombol "buat slot baru dari sini" (salin kotak) + hapus.
 * - Baris 2: nomor urut foto slot ini sekarang (`Foto #N`), dikasih tanda kalau lagi
 *   berbagi foto dengan slot lain (hasil "Sumber Foto" / auto-detect yang ditimpa).
 * - Baris 3: dua aksi utama fitur reorder & duplicate-tanpa-slot-baru:
 *     - **Tukar**: pilih slot lain dari dropdown -> `order` KEDUA slot saling
 *       bertukar (reorder murni, posisi tetap).
 *     - **Sumber**: pilih slot lain dari dropdown -> slot INI ikut memakai `order`
 *       slot yang dipilih (jadi "duplikat" tanpa bikin kotak baru, posisi tetap).
 * - Kalau lagi berbagi foto (shared), muncul tombol "Lepas" buat balikin slot ini
 *   ke `order` unik miliknya sendiri lagi.
 */
@Composable
private fun SlotListRow(
    index: Int,
    slot: PhotoSlot,
    allSlots: List<PhotoSlot>,
    shared: Boolean,
    onSwapOrderWith: (Int) -> Unit,
    onDuplicateFrom: (Int) -> Unit,
    onMakeUnique: () -> Unit,
    onDuplicateClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showSwapMenu by remember { mutableStateOf(false) }
    var showSourceMenu by remember { mutableStateOf(false) }

    // Daftar slot LAIN (bukan diri sendiri) buat opsi di dropdown Tukar/Sumber —
    // dihitung ulang tiap kali daftar slot berubah (nama/urutan slot lain bisa
    // berubah tanpa row ini ikut di-recompose sendiri kalau tidak di-key ke allSlots).
    val otherSlots = remember(allSlots, index) {
        allSlots.mapIndexedNotNull { i, s -> if (i != index) i to s else null }
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (shared) Color(0xFF3A2A26) else Color(0xFF2E323C),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Slot ${index + 1}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFE4E6EA),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDuplicateClick) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Buat slot baru (salin kotak dari slot ini)",
                        tint = Color(0xFF4DD0E1)
                    )
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Hapus slot",
                        tint = Color(0xFFEF5350)
                    )
                }
            }

            Text(
                text = if (shared) "Foto #${slot.order} • berbagi foto dgn slot lain" else "Foto #${slot.order}",
                style = MaterialTheme.typography.bodySmall,
                color = if (shared) Color(0xFFFF7A59) else Color(0xFF9AA0AC)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Tukar Urutan — reorder murni: order slot INI & slot yang dipilih
                // saling bertukar, posisi/ukuran keduanya tidak tersentuh.
                Box(modifier = Modifier.weight(1f)) {
                    TextButton(
                        onClick = { showSwapMenu = true },
                        enabled = otherSlots.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Tukar", style = MaterialTheme.typography.labelSmall)
                    }
                    DropdownMenu(expanded = showSwapMenu, onDismissRequest = { showSwapMenu = false }) {
                        Text(
                            text = "Tukar urutan foto dengan:",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF9AA0AC),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                        otherSlots.forEach { (otherIndex, otherSlot) ->
                            DropdownMenuItem(
                                text = { Text("Slot ${otherIndex + 1} (foto #${otherSlot.order})") },
                                onClick = {
                                    showSwapMenu = false
                                    onSwapOrderWith(otherIndex)
                                }
                            )
                        }
                    }
                }

                // Sumber Foto — duplicate TANPA bikin slot baru: order slot INI
                // ditimpa jadi sama dengan slot yang dipilih, posisi/ukuran tetap.
                Box(modifier = Modifier.weight(1f)) {
                    TextButton(
                        onClick = { showSourceMenu = true },
                        enabled = otherSlots.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Sumber", style = MaterialTheme.typography.labelSmall)
                    }
                    DropdownMenu(expanded = showSourceMenu, onDismissRequest = { showSourceMenu = false }) {
                        Text(
                            text = "Pakai foto dari:",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF9AA0AC),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                        otherSlots.forEach { (otherIndex, otherSlot) ->
                            DropdownMenuItem(
                                text = { Text("Slot ${otherIndex + 1} (foto #${otherSlot.order})") },
                                onClick = {
                                    showSourceMenu = false
                                    onDuplicateFrom(otherIndex)
                                }
                            )
                        }
                    }
                }
            }

            if (shared) {
                TextButton(
                    onClick = onMakeUnique,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Icon(imageVector = Icons.Filled.LinkOff, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Lepas (pakai foto sendiri lagi)", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}