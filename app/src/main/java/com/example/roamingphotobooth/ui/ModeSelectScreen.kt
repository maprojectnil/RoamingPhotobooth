package com.example.roamingphotobooth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.roamingphotobooth.settings.AppearanceSettings
import com.example.roamingphotobooth.settings.BackgroundLayer

/**
 * Layar pilih mode booth setelah user menekan "Mulai" di Home: Mobile atau Stand.
 *
 * [appearance] opsional (default = tampilan lama) — lihat catatan yang sama
 * di HomeScreen.
 */
@Composable
fun ModeSelectScreen(
    onMobileClick: () -> Unit,
    onStandClick: () -> Unit,
    onBackClick: () -> Unit,
    appearance: AppearanceSettings = AppearanceSettings()
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        BackgroundLayer(
            background = appearance.modeSelectBackground,
            modifier = Modifier.fillMaxSize()
        )

        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Text(
                text = "←",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Pilih Mode",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
            Button(
                onClick = onMobileClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(appearance.buttonColorArgb))
            ) {
                Text(
                    text = appearance.mobileButtonText,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp)
                )
            }
            Button(
                onClick = onStandClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(appearance.buttonColorArgb))
            ) {
                Text(
                    text = appearance.standButtonText,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp)
                )
            }
        }
    }
}
