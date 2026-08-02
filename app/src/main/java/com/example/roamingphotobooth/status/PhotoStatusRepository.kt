package com.example.roamingphotobooth.status

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Nulis status foto (uploading/ready/failed) ke Firestore lewat REST API v1,
 * dipanggil dari [com.example.roamingphotobooth.work.DriveUploadWorker].
 *
 * Kenapa REST, bukan Firebase SDK: codebase ini sudah konsisten pakai
 * HttpURLConnection mentah buat semua panggilan Google API (lihat DriveAuth,
 * DriveUploader) supaya tidak perlu google-services.json / Play Services.
 * Firestore REST API cukup buat kebutuhan sederhana: tulis 1 dokumen per foto.
 *
 * Landing page (Firebase Hosting) baca dokumen yang sama pakai Firestore Web
 * SDK dengan realtime listener -> otomatis update tanpa app ini perlu tahu
 * apa-apa soal landing page.
 *
 * BLOCKING -> selalu panggil dari Dispatchers.IO (CoroutineWorker sudah IO).
 */
class PhotoStatusRepository(
    private val projectId: String,
    private val authClient: FirebaseAuthClient
) {
    private val baseUrl = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/photos"

    fun markUploading(slug: String, fileName: String, attempt: Int) {
        writeFields(
            slug,
            mapOf(
                "status" to "uploading",
                "fileName" to fileName,
                "attempt" to attempt.toString(),
                "updatedAt" to nowIso()
            )
        )
    }

    fun markReady(slug: String, driveUrl: String, driveFileId: String) {
        writeFields(
            slug,
            mapOf(
                "status" to "ready",
                "driveUrl" to driveUrl,
                "driveFileId" to driveFileId,
                "updatedAt" to nowIso()
            )
        )
    }

    fun markFailed(slug: String, reason: String) {
        writeFields(slug, mapOf("status" to "failed", "error" to reason, "updatedAt" to nowIso()))
    }

    private fun nowIso(): String =
        java.time.Instant.now().toString()

    /**
     * PATCH dengan updateMask -> merge field yang dikirim ke dokumen, TIDAK
     * menghapus field lain yang sudah ada (mis. saat markReady() dipanggil,
     * field status="uploading" sebelumnya digantikan, bukan dokumennya
     * ditimpa total). PATCH otomatis membuat dokumen kalau belum ada.
     */
    private fun writeFields(slug: String, fields: Map<String, String>) {
        val idToken = authClient.fetchIdToken()

        val fieldsJson = JSONObject()
        fields.forEach { (key, value) ->
            fieldsJson.put(key, JSONObject().put("stringValue", value))
        }
        val body = JSONObject().put("fields", fieldsJson)

        val mask = fields.keys.joinToString("&") { "updateMask.fieldPaths=" + URLEncoder.encode(it, "UTF-8") }
        val url = URL("$baseUrl/${URLEncoder.encode(slug, "UTF-8")}?$mask")

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "PATCH"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $idToken")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            .bufferedReader().readText()
        conn.disconnect()

        if (code !in 200..299) {
            throw RuntimeException("Gagal tulis status Firestore utk $slug ($code): $text")
        }
        Log.i("PhotoStatusRepo", "Status $slug -> ${fields["status"]}")
    }
}
