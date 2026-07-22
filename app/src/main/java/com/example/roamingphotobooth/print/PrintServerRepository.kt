package com.example.roamingphotobooth.print

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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