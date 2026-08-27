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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
 * Layar Appearance: atur background Home (gambar/video dari galeri, ATAU
 * live view kamera -- lihat toggle "Pakai Live View"), overlay PNG opsional
 * di atas background Home, warna tombol/aksen/logo, dan teks tombol Mulai
 * (dipakai sebagai content description logo, lihat ui.HomeScreen).
 *
 * <-- BERUBAH: background & teks tombol untuk layar "Pilih Mode" DIHAPUS --
 * layar itu sudah tidak ada lagi (lihat nav.AppScreen), digantikan switch
 * Mobile/Stand di Settings > Session.
 *
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

    val pickHomeBgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val copiedPath = mediaFileManager.importFromUri(uri) ?: return@rememberLauncherForActivityResult
        val isVideo = mediaFileManager.isVideoMime(uri)
        mediaFileManager.deleteMediaFile(settings.homeBackground.path)
        settings = settings.copy(homeBackground = BackgroundSetting(path = copiedPath, isVideo = isVideo))
    }

    // <-- BARU: overlay PNG digambar di atas background Home (live view ATAU
    // gambar/video statis) -- lihat AppearanceSettings.homeOverlayImagePath &
    // OverlayPngLayer. Selalu gambar diam (bukan video), jadi pakai request
    // ImageOnly, bukan ImageAndVideo seperti picker background.
    val pickOverlayLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val copiedPath = mediaFileManager.importFromUri(uri) ?: return@rememberLauncherForActivityResult
        mediaFileManager.deleteMediaFile(settings.homeOverlayImagePath)
        settings = settings.copy(homeOverlayImagePath = copiedPath)
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
            text = "Ubah tampilan layar Home.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF9AA0AC)
        )

        Spacer(modifier = Modifier.height(24.dp))
        BackgroundPickerCard(
            title = "Background Home",
            background = settings.homeBackground,
            enabled = !settings.useLiveViewAsHomeBackground,
            onPickClick = {
                pickHomeBgLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                )
            },
            onRemoveClick = {
                mediaFileManager.deleteMediaFile(settings.homeBackground.path)
                settings = settings.copy(homeBackground = BackgroundSetting())
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
        // <-- BARU: pakai live view kamera (bitmap yang sama dengan preview di
        // layar Booth) sebagai background Home, bukan gambar/video statis di
        // atas. Kalau ON, kartu "Background Home" di atas diabaikan (dan
        // ditampilkan redup lewat parameter `enabled`) -- lihat ui.HomeScreen.
        AppearanceToggleCard(
            title = "Pakai Live View sebagai Background Home",
            description = "ON: layar Home menampilkan live view kamera secara langsung sebagai " +
                "background (sama seperti preview di layar Booth). OFF: pakai gambar/video statis " +
                "dari \"Background Home\" di atas.",
            checked = settings.useLiveViewAsHomeBackground,
            onCheckedChange = { settings = settings.copy(useLiveViewAsHomeBackground = it) }
        )

        Spacer(modifier = Modifier.height(16.dp))
        OverlayPickerCard(
            overlayPath = settings.homeOverlayImagePath,
            onPickClick = {
                pickOverlayLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onRemoveClick = {
                mediaFileManager.deleteMediaFile(settings.homeOverlayImagePath)
                settings = settings.copy(homeOverlayImagePath = null)
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
                // <-- BARU: warna logo kamera di tombol Mulai (lihat
                // ui.HomeScreen -- di-tint lewat ColorFilter.tint memakai
                // warna ini). Terpisah dari "Warna Tombol" supaya bisa diatur
                // independen (mis. logo putih di atas background gelap).
                ColorPickerRow(
                    label = "Warna Logo Mulai",
                    colorArgb = settings.startButtonIconColorArgb,
                    onColorChange = { settings = settings.copy(startButtonIconColorArgb = it) }
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
                Text(
                    text = "Tombol Mulai sekarang berupa logo kamera (bukan teks) -- nilai di " +
                        "bawah dipakai sebagai label aksesibilitas (screen reader) untuk logo itu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9AA0AC)
                )
                AppearanceTextField(
                    label = "Label Tombol Mulai (Home)",
                    value = settings.startButtonText,
                    onValueChange = { settings = settings.copy(startButtonText = it) }
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

@Composable
private fun BackgroundPickerCard(
    title: String,
    background: BackgroundSetting,
    onPickClick: () -> Unit,
    onRemoveClick: () -> Unit,
    enabled: Boolean = true
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF262A33).copy(alpha = if (enabled) 1f else 0.5f)
        )
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
                            !enabled -> "Nonaktif (Live View sedang dipakai)"
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
                    OutlinedButton(onClick = onPickClick, enabled = enabled) {
                        Text("Pilih dari Galeri")
                    }
                    if (background.path != null) {
                        IconButton(onClick = onRemoveClick, enabled = enabled) {
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

/**
 * <-- BARU: kartu untuk atur overlay PNG opsional di atas background Home
 * (lihat AppearanceSettings.homeOverlayImagePath & settings.OverlayPngLayer).
 * Selalu gambar diam (PNG), tidak ada opsi video seperti [BackgroundPickerCard].
 */
@Composable
private fun OverlayPickerCard(
    overlayPath: String?,
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
                if (overlayPath != null) {
                    OverlayPngLayer(path = overlayPath, modifier = Modifier.fillMaxSize())
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
                    text = "Overlay PNG di Home",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (overlayPath != null) {
                        "PNG ditampilkan di atas background Home (live view atau gambar/video)."
                    } else {
                        "Belum diatur -- tidak ada overlay di atas background Home."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9AA0AC)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onPickClick) {
                        Text("Pilih PNG dari Galeri")
                    }
                    if (overlayPath != null) {
                        IconButton(onClick = onRemoveClick) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Hapus overlay",
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
private fun AppearanceToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9AA0AC)
                )
            }
            Spacer(modifier = Modifier.height(0.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF4DD0E1))
            )
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
