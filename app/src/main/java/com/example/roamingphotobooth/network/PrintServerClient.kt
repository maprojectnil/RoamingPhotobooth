package com.example.roamingphotobooth.network

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Client HTTP untuk mengirim print job ke Print Server Windows (PhotoboothPrintServer).
 * Endpoint: POST http://<IP>:<PORT>/print (multipart/form-data: file, copies)
 *
 * Kontrak response server (lihat WebServerService.cs):
 * - Sukses (200): {"success": true, "jobId": "...", "status": "..."}
 * - Gagal   (400): {"success": false, "message": "..."}
 *
 * Fitur test terpisah — tidak menyentuh alur photobooth utama.
 */
object PrintServerClient {

    private const val DEFAULT_PORT = 8080
    private const val PRINT_PATH = "/print"
    private const val JOBS_PATH = "/jobs"

    // Sinkron dengan AllowedExtensions & MaxFileSizeBytes di WebServerService.cs
    private val ALLOWED_EXTENSIONS = setOf("jpg", "jpeg", "png", "bmp")
    private const val MAX_FILE_SIZE_BYTES = 30L * 1024 * 1024 // 30 MB

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    sealed class PrintResult {
        data class Success(val jobId: String, val status: String) : PrintResult()
        data class Failure(val errorMessage: String) : PrintResult()
    }

    sealed class JobStatusResult {
        data class Found(
            val jobId: String,
            val fileName: String,
            val copies: Int,
            val status: String, // "pending" | "printing" | "completed" | "failed"
            val errorMessage: String?
        ) : JobStatusResult()
        data class Failure(val errorMessage: String) : JobStatusResult()
    }

    /**
     * Kirim foto ke print server.
     * onResult dipanggil di BACKGROUND thread (thread callback OkHttp), bukan main thread.
     */
    fun sendPrintJob(
        serverIp: String,
        port: Int = DEFAULT_PORT,
        photoFile: File,
        copies: Int,
        onResult: (PrintResult) -> Unit
    ) {
        if (!photoFile.exists()) {
            onResult(PrintResult.Failure("File foto tidak ditemukan: ${photoFile.absolutePath}"))
            return
        }

        val ext = photoFile.extension.lowercase()
        if (ext !in ALLOWED_EXTENSIONS) {
            onResult(PrintResult.Failure("Format .$ext tidak didukung server. Gunakan JPG, JPEG, PNG, atau BMP."))
            return
        }

        if (photoFile.length() > MAX_FILE_SIZE_BYTES) {
            onResult(PrintResult.Failure("Ukuran file melebihi batas maksimum server (30 MB)."))
            return
        }

        // Server otomatis clamp copies ke 1..20 dan default ke 1 kalau invalid,
        // tapi kita validasi juga di sisi client biar user dapat feedback lebih cepat.
        val safeCopies = copies.coerceIn(1, 20)

        val mediaType = when (ext) {
            "png" -> "image/png".toMediaTypeOrNull()
            "bmp" -> "image/bmp".toMediaTypeOrNull()
            else -> "image/jpeg".toMediaTypeOrNull()
        }

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", photoFile.name, photoFile.asRequestBody(mediaType))
            .addFormDataPart("copies", safeCopies.toString())
            .build()

        val request = Request.Builder()
            .url("http://$serverIp:$port$PRINT_PATH")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(PrintResult.Failure("Gagal terhubung ke print server: ${e.message}"))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    val bodyString = resp.body?.string().orEmpty()
                    val json = try {
                        JSONObject(bodyString)
                    } catch (e: Exception) {
                        null
                    }

                    if (json == null) {
                        onResult(PrintResult.Failure("Respons server tidak valid (HTTP ${resp.code}): $bodyString"))
                        return
                    }

                    val success = json.optBoolean("success", false)
                    if (success) {
                        val jobId = json.optString("jobId", "")
                        val status = json.optString("status", "unknown")
                        if (jobId.isBlank()) {
                            onResult(PrintResult.Failure("Server bilang sukses tapi jobId kosong: $bodyString"))
                        } else {
                            onResult(PrintResult.Success(jobId, status))
                        }
                    } else {
                        val message = json.optString("message", "Print gagal tanpa pesan error (HTTP ${resp.code}).")
                        onResult(PrintResult.Failure(message))
                    }
                }
            }
        })
    }

    /**
     * Bonus opsional: cek status job lewat GET /jobs/{jobId}.
     * Berguna kalau nanti mau polling apakah job sudah "completed" atau "failed"
     * setelah dikirim, bukan cuma "sudah masuk antrean".
     */
    fun getJobStatus(
        serverIp: String,
        port: Int = DEFAULT_PORT,
        jobId: String,
        onResult: (JobStatusResult) -> Unit
    ) {
        val request = Request.Builder()
            .url("http://$serverIp:$port$JOBS_PATH/$jobId")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(JobStatusResult.Failure("Gagal terhubung ke print server: ${e.message}"))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    val bodyString = resp.body?.string().orEmpty()
                    val json = try {
                        JSONObject(bodyString)
                    } catch (e: Exception) {
                        null
                    }

                    if (json == null) {
                        onResult(JobStatusResult.Failure("Respons server tidak valid (HTTP ${resp.code}): $bodyString"))
                        return
                    }

                    if (!resp.isSuccessful) {
                        val message = json.optString("message", "Job tidak ditemukan.")
                        onResult(JobStatusResult.Failure(message))
                        return
                    }

                    onResult(
                        JobStatusResult.Found(
                            jobId = json.optString("jobId"),
                            fileName = json.optString("fileName"),
                            copies = json.optInt("copies", 1),
                            status = json.optString("status", "unknown"),
                            errorMessage = json.optString("errorMessage").ifBlank { null }
                        )
                    )
                }
            }
        })
    }
}