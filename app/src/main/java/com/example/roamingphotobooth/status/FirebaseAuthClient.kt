package com.example.roamingphotobooth.status

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Auth anonim ke Firebase (Identity Toolkit REST API), tanpa perlu Firebase SDK
 * penuh (yang mensyaratkan google-services.json + Play Services). Dipakai supaya
 * write ke Firestore bisa dibatasi rule "request.auth != null" -> mencegah orang
 * random di internet nyampah ke koleksi `photos` lewat browser console, sambil
 * tetap membiarkan siapa pun (pengunjung) BACA dokumen via landing page publik.
 *
 * Pola sama seperti [com.example.roamingphotobooth.drive.DriveAuth]: sign-up
 * anonim sekali (di-cache di SharedPreferences), lalu refresh idToken pakai
 * refresh_token setiap dipakai. BLOCKING -> selalu panggil dari Dispatchers.IO.
 */
class FirebaseAuthClient(
    context: Context,
    private val webApiKey: String
) {
    private val prefs = context.applicationContext
        .getSharedPreferences("firebase_anon_auth", Context.MODE_PRIVATE)

    private var cachedIdToken: String? = null
    private var cachedExpiry: Long = 0L

    fun fetchIdToken(): String {
        val now = System.currentTimeMillis() / 1000
        cachedIdToken?.let { token ->
            if (now < cachedExpiry - 60) return token
        }

        val storedRefreshToken = prefs.getString("refresh_token", null)
        val (idToken, refreshToken, expiresIn) = if (storedRefreshToken != null) {
            try {
                refreshIdToken(storedRefreshToken)
            } catch (e: Exception) {
                // refresh token kadaluarsa/invalid -> daftar ulang sebagai akun anonim baru
                signUpAnonymously()
            }
        } else {
            signUpAnonymously()
        }

        prefs.edit().putString("refresh_token", refreshToken).apply()
        cachedIdToken = idToken
        cachedExpiry = now + expiresIn
        return idToken
    }

    private data class TokenResult(val idToken: String, val refreshToken: String, val expiresIn: Long)

    private fun signUpAnonymously(): TokenResult {
        val url = URL("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$webApiKey")
        val body = JSONObject().apply { put("returnSecureToken", true) }
        val json = postJson(url, body)
        return TokenResult(
            idToken = json.getString("idToken"),
            refreshToken = json.getString("refreshToken"),
            expiresIn = json.optString("expiresIn", "3600").toLong()
        )
    }

    private fun refreshIdToken(refreshToken: String): TokenResult {
        val url = URL("https://securetoken.googleapis.com/v1/token?key=$webApiKey")
        val body = "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(refreshToken, "UTF-8")

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        conn.outputStream.use { it.write(body.toByteArray()) }

        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            .bufferedReader().readText()
        conn.disconnect()

        if (code !in 200..299) throw RuntimeException("Gagal refresh idToken ($code): $text")

        val json = JSONObject(text)
        return TokenResult(
            idToken = json.getString("id_token"),
            refreshToken = json.getString("refresh_token"),
            expiresIn = json.optString("expires_in", "3600").toLong()
        )
    }

    private fun postJson(url: URL, body: JSONObject): JSONObject {
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            .bufferedReader().readText()
        conn.disconnect()

        if (code !in 200..299) throw RuntimeException("Gagal request ke $url ($code): $text")
        return JSONObject(text)
    }
}
