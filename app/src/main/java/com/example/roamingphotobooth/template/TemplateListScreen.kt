package com.example.roamingphotobooth.template

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Layar daftar template — HANYA untuk menelusuri & memilih template yang sudah ada
 * (tidak ada aksi hapus atau buat-baru di layar ini). Tidak ada logika edit
 * slot/bingkai di sini sama sekali; begitu template dipilih, kontrol berpindah ke
 * TemplateEditorScreen lewat callback yang disediakan activity pemanggil.
 *
 * Palet warna & bentuk kartu sengaja disamakan dengan TemplateEditorScreen supaya
 * transisi antar layar terasa satu keluarga visual (dark surface #262A33 di atas
 * gradient #16181D → #1F2229, aksen cyan #4DD0E1, rounded corner besar).
 */
@Composable
fun TemplateListScreen(
    templates: List<PhotoTemplate>,
    frameFileManager: FrameFileManager,
    onTemplateSelected: (PhotoTemplate) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF16181D), Color(0xFF1F2229))
                )
            )
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
                text = "${templates.size} template tersedia",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (templates.isEmpty()) {
            EmptyState()
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(templates) { template ->
                    TemplateListItem(
                        template = template,
                        frameFileManager = frameFileManager,
                        onClick = { onTemplateSelected(template) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF262A33)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.ImageIcon,
                contentDescription = null,
                tint = Color(0xFF4DD0E1)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Belum ada template",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Template akan muncul di sini setelah dibuat.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9AA0AC)
            )
        }
    }
}

/**
 * Baris kartu template — hanya menampilkan thumbnail, nama, jumlah slot, dan aksi
 * pilih (ketuk kartu). Sengaja tidak menyentuh apa pun dari
 * TemplateEditorViewModel/slot editing supaya layar ini tetap ringan dan murni
 * untuk navigasi; tidak ada aksi hapus di sini.
 */
@Composable
private fun TemplateListItem(
    template: PhotoTemplate,
    frameFileManager: FrameFileManager,
    onClick: () -> Unit
) {
    val thumbnail = remember(template.framePngPath) {
        frameFileManager.loadBitmap(template.framePngPath)
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF262A33)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF32363F), Color(0xFF2B2E36))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = "Thumbnail ${template.name}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.ImageIcon,
                        contentDescription = null,
                        tint = Color(0xFF7E8592)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE4E6EA)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${template.slotCount} slot foto",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9AA0AC)
                )
            }
        }
    }
}