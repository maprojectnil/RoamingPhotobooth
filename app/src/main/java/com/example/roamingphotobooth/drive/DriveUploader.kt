package com.example.roamingphotobooth.drive

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Upload byte JPEG langsung ke folder Drive tertentu lewat Drive REST API v3
 * (multipart upload), tanpa perlu library google-api-client yang berat/rawan
 * konflik di Android. BLOCKING -> panggil dari Dispatchers.IO.
 */
class DriveUploader(
    private val auth: DriveAuth,
    private val folderId: String
) {
    fun uploadBytes(fileName: String, jpegBytes: ByteArray): String {
        val accessToken = auth.fetchAccessToken()

        val metadata = JSONObject().apply {
            put("name", fileName)
            put("parents", JSONArray().put(folderId))
        }

        val boundary = "----RoamingPhotobooth${UUID.randomUUID()}"
        val url = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id&supportsAllDrives=true")

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        }

        conn.outputStream.use { out ->
            out.write("--$boundary\r\n".toByteArray())
            out.write("Content-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray())
            out.write(metadata.toString().toByteArray())
            out.write("\r\n--$boundary\r\n".toByteArray())
            out.write("Content-Type: image/jpeg\r\n\r\n".toByteArray())
            out.write(jpegBytes)
            out.write("\r\n--$boundary--".toByteArray())
        }

        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            .bufferedReader().readText()
        conn.disconnect()

        if (code !in 200..299) {
            throw RuntimeException("Upload ke Drive gagal ($code): $text")
        }
        return JSONObject(text).getString("id")
    }
}