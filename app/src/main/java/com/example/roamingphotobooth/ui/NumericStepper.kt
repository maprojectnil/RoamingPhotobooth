package com.example.roamingphotobooth.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Stepper angka sederhana (tombol "-" / "+") buat pilih jumlah, menggantikan
 * OutlinedTextField manual — user tidak perlu ngetik, tinggal tap, dan
 * hasilnya selalu berupa angka valid di dalam [valueRange] (tidak akan pernah
 * kosong/non-angka seperti risiko input teks bebas).
 */
@Composable
fun NumericStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: IntRange = 1..20,
    enabled: Boolean = true,
    label: String? = null
) {
    Column(modifier = modifier) {
        label?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedIconButton(
                onClick = { if (value > valueRange.first) onValueChange(value - 1) },
                enabled = enabled && value > valueRange.first
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Kurangi")
            }

            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .width(48.dp)
                    .padding(horizontal = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            OutlinedIconButton(
                onClick = { if (value < valueRange.last) onValueChange(value + 1) },
                enabled = enabled && value < valueRange.last
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Tambah")
            }
        }
    }
}
