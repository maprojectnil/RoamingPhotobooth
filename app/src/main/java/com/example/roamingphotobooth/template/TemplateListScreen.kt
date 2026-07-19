package com.example.roamingphotobooth.template

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp

@Composable
fun TemplateListScreen(
    templates: List<PhotoTemplate>,
    frameFileManager: FrameFileManager,
    onTemplateSelected: (PhotoTemplate) -> Unit,
    onCreateNewClick: () -> Unit,
    onDeleteClick: (PhotoTemplate) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Daftar Template Bingkai", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)

        Button(
            onClick = onCreateNewClick,
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            Text("+ Buat Template Baru")
        }

        if (templates.isEmpty()) {
            Text("Belum ada template. Klik tombol di atas untuk membuat.")
        } else {
            LazyColumn {
                items(templates) { template ->
                    TemplateListItem(
                        template = template,
                        frameFileManager = frameFileManager,
                        onClick = { onTemplateSelected(template) },
                        onDeleteClick = { onDeleteClick(template) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplateListItem(
    template: PhotoTemplate,
    frameFileManager: FrameFileManager,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val thumbnail = remember(template.framePngPath) {
        frameFileManager.loadBitmap(template.framePngPath)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onClick() }
            .background(androidx.compose.ui.graphics.Color.LightGray.copy(alpha = 0.3f))
            .padding(8.dp)
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = "Thumbnail",
                modifier = Modifier.size(60.dp)
            )
        }

        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(template.name)
            Text("${template.slotCount} slot foto", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        }

        Button(onClick = onDeleteClick) {
            Text("Hapus")
        }
    }
}