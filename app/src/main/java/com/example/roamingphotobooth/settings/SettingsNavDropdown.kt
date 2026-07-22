package com.example.roamingphotobooth.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Tombol di pojok kiri atas layar Settings yang, saat ditekan, membuka
 * dropdown menu berisi pilihan: Frame Editor, Frame List, Appearance, Printer —
 * masing-masing dengan logo dari assets/ (lihat [SettingsAssetIcons]),
 * fallback ke ikon Material bawaan kalau file logo belum ditaruh di project.
 */
@Composable
fun SettingsNavDropdown(
    selected: SettingsSection,
    onSelect: (SettingsSection) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF262A33))
            .clickable { expanded = true }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        SectionIcon(section = selected)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = selected.label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Filled.ArrowDropDown,
            contentDescription = "Buka menu pengaturan",
            tint = Color(0xFF9AA0AC)
        )
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        modifier = Modifier.background(Color(0xFF262A33))
    ) {
        SettingsSection.values().forEach { section ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = section.label,
                        color = if (section == selected) Color(0xFF4DD0E1) else Color.White
                    )
                },
                leadingIcon = { SectionIcon(section = section) },
                onClick = {
                    expanded = false
                    onSelect(section)
                }
            )
        }
    }
}

@Composable
private fun SectionIcon(section: SettingsSection) {
    val context = LocalContext.current
    val logo = remember(section) { SettingsAssetIcons.load(context, section) }

    if (logo != null) {
        Image(
            bitmap = logo.asImageBitmap(),
            contentDescription = section.label,
            modifier = Modifier.size(22.dp)
        )
    } else {
        // Fallback kalau file logo belum ditaruh di app/src/main/assets/.
        val fallbackIcon = when (section) {
            SettingsSection.FRAME_EDITOR -> Icons.Filled.Tune
            SettingsSection.FRAME_LIST -> Icons.Filled.Collections
            SettingsSection.APPEARANCE -> Icons.Filled.Palette
            // <-- BARU: fallback icon untuk section Printer (dropdown pilih printer).
            SettingsSection.PRINTER -> Icons.Filled.Print
        }
        Icon(
            imageVector = fallbackIcon,
            contentDescription = section.label,
            tint = Color(0xFF4DD0E1),
            modifier = Modifier.size(22.dp)
        )
    }
}
