package com.example.roamingphotobooth.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Password default untuk menonaktifkan Kiosk Mode. Selama tidak ada UI untuk
 * mengubahnya (belum diminta di spesifikasi fitur ini), nilainya konstan di sini.
 */
private const val KIOSK_MODE_DEFAULT_PASSWORD = "0000"

/**
 * Tombol Kiosk Mode di pojok kanan atas Home — logo gembok, opacity ~20% supaya
 * tidak terlalu mengganggu tampilan Home yang sudah ada (lihat HomeScreen).
 *
 * - Tap saat NONAKTIF -> langsung aktifkan (panggil [onEnable]), tanpa password.
 * - Tap saat AKTIF -> munculkan dialog password. Password salah -> Kiosk Mode
 *   TETAP aktif (dialog tidak tertutup, tampil pesan error). Password benar
 *   ("0000" secara default) -> panggil [onDisable] dan tutup dialog.
 */
@Composable
fun KioskModeButton(
    enabled: Boolean,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPasswordDialog by remember { mutableStateOf(false) }

    IconButton(
        onClick = {
            if (enabled) {
                showPasswordDialog = true
            } else {
                onEnable()
            }
        },
        modifier = modifier
    ) {
        Icon(
            imageVector = if (enabled) Icons.Filled.Lock else Icons.Filled.LockOpen,
            contentDescription = if (enabled) "Nonaktifkan Kiosk Mode" else "Aktifkan Kiosk Mode",
            tint = Color.White.copy(alpha = 0.2f)
        )
    }

    if (showPasswordDialog) {
        KioskPasswordDialog(
            onDismiss = { showPasswordDialog = false },
            onPasswordCorrect = {
                showPasswordDialog = false
                onDisable()
            }
        )
    }
}

@Composable
private fun KioskPasswordDialog(
    onDismiss: () -> Unit,
    onPasswordCorrect: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nonaktifkan Kiosk Mode") },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(
                    text = "Masukkan password untuk keluar dari Kiosk Mode.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        showError = false
                    },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = showError,
                    colors = OutlinedTextFieldDefaults.colors(),
                    modifier = Modifier.padding(top = 12.dp)
                )
                if (showError) {
                    Text(
                        text = "Password salah.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (password == KIOSK_MODE_DEFAULT_PASSWORD) {
                    onPasswordCorrect()
                } else {
                    // Password salah -> Kiosk Mode TIDAK boleh dinonaktifkan; dialog
                    // tetap terbuka (tidak memanggil onDismiss) supaya user bisa coba lagi.
                    showError = true
                }
            }) {
                Text("Konfirmasi")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
