package com.example.roamingphotobooth.template

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    onSaveClick: () -> Unit
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
                                onSlotChanged = { updated -> viewModel.updateSlot(index, updated) },
                                onDuplicateClick = { viewModel.duplicateSlot(index) },
                                onDeleteClick = { viewModel.removeSlotAt(index) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Geser slot untuk memindah • tarik pojok kanan-bawah untuk resize • " +
                    "ikon salin untuk duplikat slot (1 foto dipakai di beberapa posisi)",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF7E8592)
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
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF262A33)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
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

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Daftar Slot",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFC9CDD6)
            )
            Spacer(modifier = Modifier.height(6.dp))

            val orderCounts = viewModel.slots.groupingBy { it.order }.eachCount()

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(viewModel.slots.size) { index ->
                    val slot = viewModel.slots[index]
                    val shared = (orderCounts[slot.order] ?: 0) > 1
                    SlotListRow(
                        label = "Slot ${index + 1} (foto #${slot.order})",
                        shared = shared,
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

@Composable
private fun SlotListRow(
    label: String,
    shared: Boolean,
    onDuplicateClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (shared) Color(0xFF3A2A26) else Color(0xFF2E323C),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFE4E6EA),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDuplicateClick) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Duplikat slot",
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
    }
}
