package com.example.roamingphotobooth.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Layar Appearance: atur background Home & Mode Select (gambar/video dari
 * galeri, di-loop kalau video), warna tombol & aksen, dan teks tombol.
 * Perubahan hanya disimpan permanen ketika user menekan "Simpan Perubahan"
 * (lewat [onSave]) — sebelum itu cuma state lokal di layar ini.
 */
@Composable
fun AppearanceScreen(
    initialSettings: AppearanceSettings,
    mediaFileManager: MediaFileManager,
    onSave: (AppearanceSettings) -> Unit
) {
    var settings by remember { mutableStateOf(initialSettings) }
    var pendingTarget by remember { mutableStateOf<BackgroundTarget?>(null) }

    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val target = pendingTarget
        pendingTarget = null
        if (uri == null || target == null) return@rememberLauncherForActivityResult

        val copiedPath = mediaFileManager.importFromUri(uri) ?: return@rememberLauncherForActivityResult
        val isVideo = mediaFileManager.isVideoMime(uri)
        val newBackground = BackgroundSetting(path = copiedPath, isVideo = isVideo)

        settings = when (target) {
            BackgroundTarget.HOME -> {
                mediaFileManager.deleteMediaFile(settings.homeBackground.path)
                settings.copy(homeBackground = newBackground)
            }
            BackgroundTarget.MODE_SELECT -> {
                mediaFileManager.deleteMediaFile(settings.modeSelectBackground.path)
                settings.copy(modeSelectBackground = newBackground)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "Appearance",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Ubah tampilan layar Home & Pilih Mode.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF9AA0AC)
        )

        Spacer(modifier = Modifier.height(24.dp))
        BackgroundPickerCard(
            title = "Background Home",
            background = settings.homeBackground,
            onPickClick = {
                pendingTarget = BackgroundTarget.HOME
                pickMediaLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                )
            },
            onRemoveClick = {
                mediaFileManager.deleteMediaFile(settings.homeBackground.path)
                settings = settings.copy(homeBackground = BackgroundSetting())
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
        BackgroundPickerCard(
            title = "Background Pilih Mode",
            background = settings.modeSelectBackground,
            onPickClick = {
                pendingTarget = BackgroundTarget.MODE_SELECT
                pickMediaLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                )
            },
            onRemoveClick = {
                mediaFileManager.deleteMediaFile(settings.modeSelectBackground.path)
                settings = settings.copy(modeSelectBackground = BackgroundSetting())
            }
        )

        Spacer(modifier = Modifier.height(28.dp))
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF262A33))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                ColorPickerRow(
                    label = "Warna Tombol",
                    colorArgb = settings.buttonColorArgb,
                    onColorChange = { settings = settings.copy(buttonColorArgb = it) }
                )
                ColorPickerRow(
                    label = "Warna Aksen",
                    colorArgb = settings.accentColorArgb,
                    onColorChange = { settings = settings.copy(accentColorArgb = it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF262A33))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Teks Tombol",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                AppearanceTextField(
                    label = "Tombol Mulai (Home)",
                    value = settings.startButtonText,
                    onValueChange = { settings = settings.copy(startButtonText = it) }
                )
                AppearanceTextField(
                    label = "Tombol Mobile (Pilih Mode)",
                    value = settings.mobileButtonText,
                    onValueChange = { settings = settings.copy(mobileButtonText = it) }
                )
                AppearanceTextField(
                    label = "Tombol Stand (Pilih Mode)",
                    value = settings.standButtonText,
                    onValueChange = { settings = settings.copy(standButtonText = it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = { onSave(settings) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(settings.accentColorArgb)),
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(
                text = "Simpan Perubahan",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

private enum class BackgroundTarget { HOME, MODE_SELECT }

@Composable
private fun BackgroundPickerCard(
    title: String,
    background: BackgroundSetting,
    onPickClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF262A33))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF16181D)),
                contentAlignment = Alignment.Center
            ) {
                if (background.path != null) {
                    BackgroundLayer(background = background, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(
                        imageVector = Icons.Filled.ImageIcon,
                        contentDescription = null,
                        tint = Color(0xFF7E8592)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (background.isVideo) Icons.Filled.Movie else Icons.Filled.ImageIcon,
                        contentDescription = null,
                        tint = Color(0xFF9AA0AC),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when {
                            background.path == null -> "Belum diatur (pakai default)"
                            background.isVideo -> "Video (loop otomatis)"
                            else -> "Gambar"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9AA0AC)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onPickClick) {
                        Text("Pilih dari Galeri")
                    }
                    if (background.path != null) {
                        IconButton(onClick = onRemoveClick) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Hapus background",
                                tint = Color(0xFFEF5350)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedLabelColor = Color(0xFF4DD0E1),
            unfocusedLabelColor = Color(0xFF9AA0AC),
            focusedBorderColor = Color(0xFF4DD0E1),
            unfocusedBorderColor = Color(0x40FFFFFF)
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
