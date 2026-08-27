package com.example.roamingphotobooth.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import com.example.roamingphotobooth.nav.BoothMode
import com.example.roamingphotobooth.ui.NumericStepper

/**
 * Layar Settings > Session: atur nilai DEFAULT yang dipakai setiap kali sesi
 * booth baru dimulai — Mode Booth (Mobile/Stand, lihat catatan [BoothModeSwitchCard]),
 * durasi Countdown Timer, Mirror Camera (preview + hasil foto), Auto Countdown
 * untuk slot kedua dst (mode Stand), dan ID folder Google Drive tujuan upload
 * (bisa dikosongkan untuk pakai default dari build).
 *
 * Sama seperti [AppearanceScreen]: perubahan hanya disimpan permanen begitu
 * user menekan "Simpan Perubahan" (lewat [onSave]) — sebelum itu cuma state
 * lokal di layar ini.
 */
@Composable
fun SessionSettingsScreen(
    initialSettings: SessionSettings,
    onSave: (SessionSettings) -> Unit
) {
    var settings by remember { mutableStateOf(initialSettings) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "Session",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Pengaturan default untuk setiap sesi booth baru (Mobile & Stand).",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF9AA0AC)
        )

        Spacer(modifier = Modifier.height(24.dp))
        BoothModeSwitchCard(
            boothMode = settings.boothMode,
            onBoothModeChange = { settings = settings.copy(boothMode = it) }
        )

        Spacer(modifier = Modifier.height(16.dp))
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF262A33))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Countdown Timer",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = "Durasi hitung mundur (detik) sebelum foto diambil di mode Stand.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9AA0AC)
                )
                Spacer(modifier = Modifier.height(8.dp))
                NumericStepper(
                    value = settings.countdownSeconds,
                    onValueChange = { settings = settings.copy(countdownSeconds = it) },
                    valueRange = SessionSettings.COUNTDOWN_RANGE
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        SessionToggleCard(
            title = "Mirror Camera",
            description = "ON: preview kamera & hasil foto ditampilkan mirror (efek cermin). " +
                "OFF: tampilan normal. Ini nilai default — bisa diubah lagi per-sesi lewat " +
                "toggle Mirror di Session Preview.",
            checked = settings.mirrorCamera,
            onCheckedChange = { settings = settings.copy(mirrorCamera = it) }
        )

        Spacer(modifier = Modifier.height(16.dp))
        SessionToggleCard(
            title = "Auto Countdown for Next Slots",
            description = "ON: setelah menekan \"Lanjut\" untuk berpindah ke slot berikutnya " +
                "(mulai dari slot kedua), countdown otomatis mulai tanpa perlu tap tombol shutter. " +
                "OFF: countdown tetap harus dimulai manual di setiap slot.",
            checked = settings.autoCountdownNextSlots,
            onCheckedChange = { settings = settings.copy(autoCountdownNextSlots = it) }
        )

        Spacer(modifier = Modifier.height(16.dp))
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF262A33))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Google Drive Folder ID",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = "Folder Drive tujuan upload foto hasil akhir. Ambil dari URL folder " +
                        "Drive-nya (bagian setelah \"folders/\"). Kosongkan untuk pakai folder " +
                        "default yang sudah diatur saat build aplikasi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9AA0AC)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = settings.driveFolderId,
                    onValueChange = { settings = settings.copy(driveFolderId = it) },
                    label = { Text("Folder ID") },
                    placeholder = { Text("(pakai default dari build)") },
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
        }

        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = { onSave(settings) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4DD0E1)),
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

/**
 * Switch Mode Booth (Mobile/Stand) — menggantikan layar "Pilih Mode" yang
 * dulu muncul tiap kali user menekan "Mulai" di Home. Sekarang mode
 * ditentukan SEKALI di sini; begitu user menekan Mulai, app langsung masuk
 * ke pemilihan frame lalu ke sesi booth memakai mode ini (lihat
 * MainActivity.openFramePicker()) -- tidak ada lagi layar perantara.
 */
@Composable
private fun BoothModeSwitchCard(
    boothMode: BoothMode,
    onBoothModeChange: (BoothMode) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF262A33))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Mode Booth",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = "Menggantikan layar \"Pilih Mode\" -- begitu user menekan \"Mulai\" di Home, " +
                    "app langsung masuk ke pemilihan frame lalu ke sesi booth memakai mode ini.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9AA0AC)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF16181D))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                BoothModeOption(
                    label = "📱 Mobile",
                    selected = boothMode == BoothMode.MOBILE,
                    onClick = { onBoothModeChange(BoothMode.MOBILE) },
                    modifier = Modifier.weight(1f)
                )
                BoothModeOption(
                    label = "🖥️ Stand",
                    selected = boothMode == BoothMode.STAND,
                    onClick = { onBoothModeChange(BoothMode.STAND) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BoothModeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) Color(0xFF4DD0E1) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color(0xFF0B0D11) else Color(0xFF9AA0AC)
        )
    }
}

@Composable
private fun SessionToggleCard(
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
