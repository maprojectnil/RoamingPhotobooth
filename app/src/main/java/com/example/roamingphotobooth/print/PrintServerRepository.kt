package com.example.roamingphotobooth.print

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class PrintServerRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("print_server_prefs", Context.MODE_PRIVATE)

    private val discovery = PrintServerDiscovery(context)

    private val _server = MutableStateFlow(loadCached())
    val server: StateFlow<PrintServerInfo?> = _server

    private fun loadCached(): PrintServerInfo? {
        val host = prefs.getString(KEY_HOST, null) ?: return null
        val port = prefs.getInt(KEY_PORT, -1)
        if (port <= 0) return null
        return PrintServerInfo(
            serviceName = prefs.getString(KEY_NAME, "Print Server") ?: "Print Server",
            host = host,
            port = port
        )
    }

    private fun save(info: PrintServerInfo) {
        prefs.edit()
            .putString(KEY_HOST, info.host)
            .putInt(KEY_PORT, info.port)
            .putString(KEY_NAME, info.serviceName)
            .apply()
        _server.value = info
    }

    suspend fun discoverAndSave(timeoutMs: Long = 8000): PrintServerInfo? {
        val found = withTimeoutOrNull(timeoutMs) { discovery.discover().first() }
        if (found != null) save(found)
        return found
    }

    suspend fun ensureServer(forceRediscover: Boolean = false): PrintServerInfo? {
        val cached = _server.value
        if (cached != null && !forceRediscover) return cached
        return discoverAndSave()
    }

    /**
     * <-- BARU: dipakai layar Pengaturan > Printer. User memilih salah satu hasil
     * [discoverAvailableServers] lewat dropdown, lalu pilihan itu disimpan di sini
     * sebagai printer default — dipakai lagi oleh FinalResultScreen & PrintServerConnectionManager
     * tanpa perlu cari ulang tiap kali mau print.
     */
    fun selectServer(info: PrintServerInfo) = save(info)

    /**
     * <-- BARU: cari SEMUA Print Server yang merespons di jaringan selama [timeoutMs],
     * untuk mengisi pilihan dropdown di layar Pengaturan. Beda dari [ensureServer]/
     * [discoverAndSave] yang berhenti begitu 1 server ketemu — di sini kita kumpulkan
     * semua supaya user bisa pilih sendiri kalau ada lebih dari satu printer di jaringan.
     * Tidak menyimpan apa pun secara otomatis; simpan lewat [selectServer].
     */
    suspend fun discoverAvailableServers(timeoutMs: Long = 6000): List<PrintServerInfo> {
        val found = LinkedHashMap<String, PrintServerInfo>()
        withTimeoutOrNull(timeoutMs) {
            discovery.discover().collect { info ->
                found["${info.host}:${info.port}"] = info
            }
        }
        return found.values.toList()
    }

    fun clearCache() {
        prefs.edit().clear().apply()
        _server.value = null
    }

    companion object {
        private const val KEY_HOST = "server_host"
        private const val KEY_PORT = "server_port"
        private const val KEY_NAME = "server_name"

        @Volatile private var instance: PrintServerRepository? = null
        fun getInstance(context: Context): PrintServerRepository =
            instance ?: synchronized(this) {
                instance ?: PrintServerRepository(context.applicationContext).also { instance = it }
            }
    }
}