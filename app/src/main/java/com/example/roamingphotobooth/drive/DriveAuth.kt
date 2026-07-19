package com.example.roamingphotobooth.drive

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Tukar refresh token (didapat sekali lewat OAuth consent manual) jadi access token,
 * supaya upload ke Drive dilakukan atas nama akun Gmail asli (punya kuota), bukan
 * Service Account (yang tidak punya kuota untuk My Drive biasa).
 * BLOCKING -> selalu panggil dari Dispatchers.IO.
 */
class DriveAuth(
    private val clientId: String,
    private val clientSecret: String,
    private val refreshToken: String
) {
    private var cachedToken: String? = null
    private var cachedTokenExpiry: Long = 0L

    fun fetchAccessToken(): String {
        val now = System.currentTimeMillis() / 1000
        cachedToken?.let { token ->
            if (now < cachedTokenExpiry - 60) return token
        }

        val body = "client_id=" + URLEncoder.encode(clientId, "UTF-8") +
                "&client_secret=" + URLEncoder.encode(clientSecret, "UTF-8") +
                "&refresh_token=" + URLEncoder.encode(refreshToken, "UTF-8") +
                "&grant_type=refresh_token"

        val conn = (URL("https://oauth2.googleapis.com/token").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        conn.outputStream.use { it.write(body.toByteArray()) }

        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            .bufferedReader().readText()
        conn.disconnect()

        if (code !in 200..299) {
            throw RuntimeException("Gagal refresh access token ($code): $text")
        }

        val json = JSONObject(text)
        val token = json.getString("access_token")
        cachedToken = token
        cachedTokenExpiry = now + json.optLong("expires_in", 3600)
        return token
    }
}