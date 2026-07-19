package com.example.roamingphotobooth.drive

import android.util.Log
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
    /**
     * @param fileId id file di Drive (dipakai kalau perlu operasi lanjutan).
     * @param shareUrl link langsung ke foto, siap di-encode jadi QR — hanya bisa
     *   dibuka orang lain kalau [makePubliclyViewable] berhasil set permission publik.
     */
    data class UploadResult(val fileId: String, val shareUrl: String)

    fun uploadBytes(fileName: String, jpegBytes: ByteArray): UploadResult {
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
        val fileId = JSONObject(text).getString("id")

        // Supaya QR bisa dibuka siapa saja tanpa login akun Drive: set permission
        // "anyone with the link -> reader". Kalau gagal (mis. dibatasi kebijakan
        // organisasi), upload TETAP dianggap sukses -> tidak di-throw, cuma di-log,
        // karena filenya sudah aman tersimpan di Drive walau linknya belum publik.
        makePubliclyViewable(fileId, accessToken)

        val shareUrl = "https://drive.google.com/uc?export=view&id=$fileId"
        return UploadResult(fileId, shareUrl)
    }

    private fun makePubliclyViewable(fileId: String, accessToken: String) {
        try {
            val body = JSONObject().apply {
                put("role", "reader")
                put("type", "anyone")
            }
            val url = URL("https://www.googleapis.com/drive/v3/files/$fileId/permissions?supportsAllDrives=true")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                .bufferedReader().readText()
            conn.disconnect()

            if (code !in 200..299) {
                Log.w("DriveUploader", "Gagal set permission publik utk $fileId ($code): $text")
            }
        } catch (e: Exception) {
            Log.w("DriveUploader", "Gagal set permission publik utk $fileId", e)
        }
    }
}