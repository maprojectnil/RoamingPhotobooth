package com.example.roamingphotobooth.print

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Klien WebSocket ke endpoint ws://host:port/ws/status (WebSocketBroadcastService.cs).
 * Server broadcast SEMUA job ke SEMUA client -> filter per-job dilakukan di ActivePrintJobTracker.
 */
class PrintJobWebSocketClient {

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null

    private val _status = MutableStateFlow<PrintJobStatus>(PrintJobStatus.Idle)
    val status: StateFlow<PrintJobStatus> = _status

    private var reconnectAttempt = 0
    private var currentServer: PrintServerInfo? = null
    private var manuallyDisconnected = false

    fun connect(server: PrintServerInfo) {
        manuallyDisconnected = false
        currentServer = server
        openSocket(server)
    }

    private fun openSocket(server: PrintServerInfo) {
        _status.value = PrintJobStatus.ConnectingToServer
        val request = Request.Builder().url(server.wsUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket terhubung ke ${server.wsUrl}")
                reconnectAttempt = 0
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseMessage(text)?.let { _status.value = it }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket ditutup: $reason")
                _status.value = PrintJobStatus.Disconnected
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket error: ${t.message}")
                _status.value = PrintJobStatus.Disconnected
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (manuallyDisconnected) return
        val server = currentServer ?: return
        reconnectAttempt++
        val delayMs = minOf(1000L * reconnectAttempt, 15000L)
        Thread {
            Thread.sleep(delayMs)
            if (!manuallyDisconnected) openSocket(server)
        }.start()
    }

    private fun parseMessage(text: String): PrintJobStatus? {
        return try {
            val json = JSONObject(text)
            if (json.optString("type") != "job_status") return null

            val jobId = json.getString("jobId")
            val fileName = json.optString("fileName", "")
            val copies = json.optInt("copies", 1)
            val errorMessage = if (json.isNull("errorMessage")) null else json.optString("errorMessage", null)

            when (json.optString("status").lowercase()) {
                "queued" -> PrintJobStatus.Queued(jobId, fileName, copies)
                "printing" -> PrintJobStatus.Printing(jobId, fileName, copies)
                "completed" -> PrintJobStatus.Completed(jobId, fileName, copies)
                "failed" -> PrintJobStatus.Failed(jobId, fileName, errorMessage)
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal parse pesan WebSocket: $text", e)
            null
        }
    }

    fun disconnect() {
        manuallyDisconnected = true
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _status.value = PrintJobStatus.Idle
    }

    companion object {
        private const val TAG = "PrintJobWebSocket"
    }
}