package com.example.roamingphotobooth.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.roamingphotobooth.print.PrintServerInfo
import com.example.roamingphotobooth.print.PrintServerRepository
import kotlinx.coroutines.launch

/**
 * Pengaturan Printer — dipindah dari FinalResultScreen ke sini supaya urusan
 * "cari & pilih printer" cukup dilakukan SEKALI lewat menu Pengaturan, bukan
 * tiap kali user mau print. FinalResultScreen sekarang cuma baca printer yang
 * sudah tersimpan lewat [PrintServerRepository] (lihat properti [server]-nya),
 * tanpa perlu tahu cara discovery-nya sama sekali.
 *
 * Alur di sini:
 * 1. Begitu layar dibuka, otomatis cari printer di jaringan (mDNS/NSD).
 * 2. Hasilnya ditampilkan sebagai dropdown (tombol + DropdownMenu) — kalau
 *    ada lebih dari satu Print Server di jaringan yang sama, user tinggal pilih.
 * 3. Pilihan otomatis tersimpan sebagai printer default (dipakai lagi oleh
 *    TestPrintButton di FinalResultScreen & PrintServerConnectionManager).
 */
@Composable
fun PrinterSettingsScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { PrintServerRepository.getInstance(context) }

    val selectedServer by repository.server.collectAsState()

    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var availableServers by remember { mutableStateOf<List<PrintServerInfo>>(emptyList()) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    fun searchPrinters() {
        searchError = null
        isSearching = true
        coroutineScope.launch {
            val results = repository.discoverAvailableServers()
            isSearching = false
            availableServers = results
            if (results.isEmpty()) {
                searchError = "Tidak ada Print Server ditemukan. Pastikan PC & tablet di Wi-Fi yang sama."
            }
        }
    }

    // Cari otomatis begitu layar ini pertama kali dibuka.
    LaunchedEffect(Unit) {
        searchPrinters()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = "🖨️ Printer",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Pilih Print Server yang dipakai untuk semua sesi cetak.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        // Dropdown pilihan printer. Selalu menampilkan printer yang sedang
        // tersimpan (kalau ada) sebagai nilai terpilih, walau belum di-refresh.
        val dropdownOptions = remember(availableServers, selectedServer) {
            val combined = LinkedHashMap<String, PrintServerInfo>()
            selectedServer?.let { combined["${it.host}:${it.port}"] = it }
            availableServers.forEach { combined["${it.host}:${it.port}"] = it }
            combined.values.toList()
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { dropdownExpanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedServer?.let { "${it.serviceName} (${it.host}:${it.port})" }
                            ?: if (isSearching) "Mencari printer..." else "Belum ada printer dipilih"
                    )
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Buka pilihan printer")
                }
            }

            DropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                if (dropdownOptions.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Belum ada printer ditemukan") },
                        onClick = { dropdownExpanded = false },
                        enabled = false
                    )
                }
                dropdownOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text("${option.serviceName} (${option.host}:${option.port})") },
                        onClick = {
                            repository.selectServer(option)
                            dropdownExpanded = false
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = { searchPrinters() }, enabled = !isSearching) {
                Text(if (isSearching) "Mencari..." else "🔍 Cari Ulang")
            }
            if (isSearching) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }
        }

        searchError?.let {
            Text(
                text = "⚠️ $it",
                color = Color(0xFFE57373),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
