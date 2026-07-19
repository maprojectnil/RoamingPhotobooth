package com.example.roamingphotobooth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Layar pertama saat aplikasi dibuka: tombol "Mulai" di tengah, dan ikon
 * pengaturan (⚙️) di pojok kiri atas yang membuka pengaturan frame/template.
 */
@Composable
fun HomeScreen(
    onMulaiClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Logo/tombol setting — pojok kiri atas, masuk ke pengaturan frame.
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Text(
                text = "⚙️",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        // Tombol Mulai — tengah layar
        Button(
            onClick = onMulaiClick,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Text(
                text = "Mulai",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }
    }
}
