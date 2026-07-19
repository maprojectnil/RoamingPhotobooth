package com.example.roamingphotobooth.template

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp

@Composable
fun TemplateEditorScreen(
    viewModel: TemplateEditorViewModel,
    onSaveClick: () -> Unit
) {
    var containerWidthPx by remember { mutableStateOf(0f) }
    var containerHeightPx by remember { mutableStateOf(0f) }

    val frameBitmap = viewModel.frameBitmap.value

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Editor Bingkai — ${viewModel.slots.size} slot foto")

        // Area preview: bingkai + semua slot yang bisa di-drag/resize.
        // PENTING: tinggi kotak mengikuti rasio aspek ASLI frame (bukan tinggi tetap 400dp),
        // supaya gambar frame (ContentScale.Fit) mengisi PERSIS seluruh kotak tanpa letterbox.
        // Kalau ada letterbox (spasi kosong kiri-kanan/atas-bawah), rasio slot yang dihitung
        // relatif terhadap kotak akan MELESET dari posisi asli di frame saat dirender ulang.
        val frameAspectRatio = if (frameBitmap != null && frameBitmap.height > 0) {
            frameBitmap.width.toFloat() / frameBitmap.height.toFloat()
        } else {
            1f
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(frameAspectRatio)
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
            }

            if (containerWidthPx > 0 && containerHeightPx > 0) {
                viewModel.slots.forEachIndexed { index, slot ->
                    SlotEditorBox(
                        slot = slot,
                        containerWidthPx = containerWidthPx,
                        containerHeightPx = containerHeightPx,
                        onSlotChanged = { updated -> viewModel.updateSlot(index, updated) },
                        onDeleteClick = { viewModel.slots.removeAt(index) },

                    )
                }
            }
        }

        // Kontrol jumlah slot
        Text("Jumlah Foto: ${viewModel.slots.size}", modifier = Modifier.padding(top = 16.dp))
        Row {
            Button(onClick = { viewModel.setSlotCount(viewModel.slots.size + 1) }) {
                Text("+ Tambah Slot")
            }
            Button(
                onClick = { if (viewModel.slots.isNotEmpty()) viewModel.slots.removeAt(viewModel.slots.size - 1) },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("- Kurangi Slot")
            }
        }

        Button(
            onClick = onSaveClick,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("Simpan Template")
        }
    }
}