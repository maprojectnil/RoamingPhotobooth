package com.roamingphotobooth.print

import android.content.Context
import com.example.roamingphotobooth.print.PrintJobStatus
import com.example.roamingphotobooth.print.PrintJobWebSocketClient
import com.example.roamingphotobooth.print.PrintServerRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * Titik masuk tunggal untuk fitur discovery + status realtime.
 * Tidak menyentuh logic pengiriman print job HTTP yang sudah ada.
 */
class PrintServerConnectionManager(context: Context) {

    val repository = PrintServerRepository.getInstance(context)
    private val wsClient = PrintJobWebSocketClient()

    val jobStatus: StateFlow<PrintJobStatus> = wsClient.status

    /** Panggil sekali saat app start / sebelum layar print dibuka. */
    suspend fun connect(forceRediscover: Boolean = false) {
        val server = repository.ensureServer(forceRediscover) ?: return
        wsClient.connect(server)
    }

    suspend fun rediscover() = connect(forceRediscover = true)

    fun disconnect() = wsClient.disconnect()
}