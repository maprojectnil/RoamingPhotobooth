package com.example.roamingphotobooth.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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

/** Palet warna siap-pakai yang ditawarkan ke user, di luar hex custom. */
private val PRESET_COLORS = listOf(
    0xFF4DD0E1.toInt(), // cyan (default aksen app)
    0xFFEF5350.toInt(), // merah
    0xFFFFA726.toInt(), // oranye
    0xFFFFEE58.toInt(), // kuning
    0xFF66BB6A.toInt(), // hijau
    0xFF42A5F5.toInt(), // biru
    0xFFAB47BC.toInt(), // ungu
    0xFFEC407A.toInt(), // pink
    0xFFFFFFFF.toInt(), // putih
    0xFF262A33.toInt()  // abu gelap
)

/**
 * Baris label + swatch preset + input hex manual, dipakai untuk 2 pengaturan
 * warna di Appearance (warna tombol & warna aksen). [colorArgb] adalah nilai
 * ARGB Int (hasil android.graphics.Color / Color.toArgb()).
 */
@Composable
fun ColorPickerRow(
    label: String,
    colorArgb: Int,
    onColorChange: (Int) -> Unit
) {
    var hexText by remember(colorArgb) {
        mutableStateOf(String.format("#%06X", 0xFFFFFF and colorArgb))
    }

    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PRESET_COLORS.forEach { preset ->
                val isSelected = preset == colorArgb
                Column(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(preset))
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Color(0xFF4DD0E1) else Color(0x33FFFFFF),
                            shape = CircleShape
                        )
                        .clickable { onColorChange(preset) },
                    verticalArrangement = Arrangement.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = if (preset == 0xFFFFFFFF.toInt()) Color.Black else Color.White,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(18.dp)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Hex:",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9AA0AC)
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = hexText,
                onValueChange = { input ->
                    hexText = input
                    val cleaned = input.removePrefix("#").trim()
                    if (cleaned.length == 6 && cleaned.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                        val parsed = cleaned.toIntOrNull(16)
                        if (parsed != null) {
                            onColorChange((0xFF shl 24) or parsed)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF4DD0E1),
                    unfocusedBorderColor = Color(0x40FFFFFF)
                ),
                modifier = Modifier.width(140.dp)
            )
        }
    }
}
