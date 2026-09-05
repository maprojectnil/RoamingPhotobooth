package com.example.roamingphotobooth.drive

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Upload byte JPEG langsung ke folder Drive tertentu lewat Drive REST API v3
 * (multipart upload), tanpa perlu library google-api-client yang berat/rawan
 * konflik di Android. BLOCKING -> panggil dari Dispatchers.IO.
 */
class DriveUploader(
    private val auth: DriveAuth,
    private val folderId: String
) {
    companion object {
        // <-- FIX: 1 sesi sekarang di-upload lewat BEBERAPA job WorkManager
        // terpisah yang jalan hampir bersamaan (1 foto hasil merge + N foto
        // per-slot, lihat MainActivity.enqueueRawSlotUploads) dengan
        // sessionFolderName yang SAMA persis. Tiap job bikin instance
        // DriveUploader sendiri-sendiri (lihat DriveUploadWorker.doWork), jadi
        // lock-nya WAJIB di level companion object (dibagi lintas instance),
        // bukan instance field -- kalau tidak, tetap race seperti sebelumnya.
        //
        // Tanpa lock ini: resolveSessionFolderId() pola-nya "cari dulu, kalau
        // belum ada baru bikin" -- kalau 2+ job cek BARENGAN sebelum salah satu
        // sempat selesai bikin foldernya, Drive TIDAK melarang folder dengan
        // nama sama dibuat dobel -> tiap job "menang" bikin folder sendiri ->
        // foto 1 sesi kepencar ke beberapa folder yang namanya sama persis.
        //
        // Key = "<folderId dasar>::<nama folder sesi>" supaya lock-nya spesifik
        // per kombinasi folder dasar + nama sesi (jaga-jaga kalau suatu saat ada
        // multi-akun/multi-folder dasar dalam 1 proses app yang sama).
        private val sessionFolderLocks = ConcurrentHashMap<String, Mutex>()
    }

    /**
     * @param fileId id file di Drive (dipakai kalau perlu operasi lanjutan).
     * @param shareUrl link langsung ke foto, siap di-encode jadi QR — hanya bisa
     *   dibuka orang lain kalau [makePubliclyViewable] berhasil set permission publik.
     */
    data class UploadResult(val fileId: String, val shareUrl: String)

    /**
     * @param parentFolderId folder tujuan upload. Default [folderId] (folder dasar
     *   yang sudah diatur lewat Settings/gradle.properties). Isi dengan id folder
     *   sesi (lihat [resolveSessionFolderId]) kalau foto ini mau masuk ke subfolder
     *   sesi, bukan langsung ke folder dasar.
     * @param mimeType Content-Type file yang diupload. Default "image/jpeg" (semua
     *   foto hasil merge & foto mentah per-slot) -- <-- BARU: dipakai juga untuk
     *   upload GIF hasil sesi (lihat MainActivity.enqueueGifUpload), yang butuh
     *   "image/gif" supaya Drive mengenali tipe filenya dengan benar.
     */
    fun uploadBytes(
        fileName: String,
        jpegBytes: ByteArray,
        parentFolderId: String = folderId,
        mimeType: String = "image/jpeg"
    ): UploadResult {
        val accessToken = auth.fetchAccessToken()

        val metadata = JSONObject().apply {
            put("name", fileName)
            put("parents", JSONArray().put(parentFolderId))
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
            out.write("Content-Type: $mimeType\r\n\r\n".toByteArray())
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

    /**
     * Cari subfolder bernama [sessionFolderName] di dalam folder dasar ([folderId]).
     * Kalau belum ada, buat baru. Kalau sudah ada (mis. worker ini di-retry setelah
     * folder sempat berhasil dibuat sebelumnya, ATAU job LAIN dari sesi yang sama
     * sudah lebih dulu bikin foldernya), pakai ulang id yang sama -> TIDAK bikin
     * folder duplikat.
     *
     * DIKUNCI per (folderId dasar + sessionFolderName) lewat [sessionFolderLocks]:
     * job-job lain dari sesi yang SAMA yang jalan bersamaan akan ANTRE nunggu
     * giliran di sini (bukan sama-sama cek+bikin barengan) -> yang pertama selesai
     * bikin/nemuin foldernya, sisanya tinggal nemuin folder yang sudah ada itu.
     * Job dari sesi yang BEDA (nama folder beda) tidak saling nunggu -- lock-nya
     * per key, bukan global.
     *
     * suspend (bukan blocking biasa) karena pakai Mutex coroutine -- tetap aman
     * dipanggil dari Dispatchers.IO seperti [uploadBytes], HTTP call di dalamnya
     * tetap blocking seperti sebelumnya, cuma exclusion-nya sekarang lewat coroutine.
     */
    suspend fun resolveSessionFolderId(sessionFolderName: String): String {
        val lockKey = "$folderId::$sessionFolderName"
        val mutex = sessionFolderLocks.computeIfAbsent(lockKey) { Mutex() }
        return mutex.withLock {
            val accessToken = auth.fetchAccessToken()
            findFolderByName(sessionFolderName, accessToken)
                ?: createFolder(sessionFolderName, accessToken)
        }
    }

    private fun findFolderByName(name: String, accessToken: String): String? {
        // Escape ' dan \ di nama folder supaya tidak merusak query Drive (lihat
        // https://developers.google.com/drive/api/guides/search-files).
        val escapedName = name.replace("\\", "\\\\").replace("'", "\\'")
        val query = "mimeType='application/vnd.google-apps.folder' and trashed=false " +
            "and name='$escapedName' and '$folderId' in parents"
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = URL(
            "https://www.googleapis.com/drive/v3/files?q=$encodedQuery" +
                "&fields=files(id)&supportsAllDrives=true&includeItemsFromAllDrives=true"
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
        }

        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            .bufferedReader().readText()
        conn.disconnect()

        if (code !in 200..299) {
            throw RuntimeException("Cek folder sesi di Drive gagal ($code): $text")
        }
        val files = JSONObject(text).optJSONArray("files") ?: JSONArray()
        return if (files.length() > 0) files.getJSONObject(0).getString("id") else null
    }

    private fun createFolder(name: String, accessToken: String): String {
        val metadata = JSONObject().apply {
            put("name", name)
            put("mimeType", "application/vnd.google-apps.folder")
            put("parents", JSONArray().put(folderId))
        }
        val url = URL("https://www.googleapis.com/drive/v3/files?fields=id&supportsAllDrives=true")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }
        conn.outputStream.use { it.write(metadata.toString().toByteArray()) }

        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            .bufferedReader().readText()
        conn.disconnect()

        if (code !in 200..299) {
            throw RuntimeException("Bikin folder sesi di Drive gagal ($code): $text")
        }
        return JSONObject(text).getString("id")
    }

    /**
     * Set permission folder (BUKAN file) supaya bisa dibuka siapa saja lewat link,
     * sama seperti yang otomatis terjadi ke tiap file lewat [uploadBytes]. Dipanggil
     * manual dari DriveUploadWorker untuk folder SESI, supaya link folder yang
     * ditampilkan di landing page (saat "Folder Terpisah per Sesi" ON) bisa dibuka
     * tanpa perlu login akun Drive.
     *
     * Sama seperti [makePubliclyViewable] internal: kalau gagal, tidak di-throw
     * (cuma di-log) -- foto/folder tetap aman tersimpan, cuma link publiknya yang
     * belum tentu jalan (mis. dibatasi kebijakan organisasi).
     */
    fun makePubliclyViewable(id: String) {
        val accessToken = auth.fetchAccessToken()
        setPublicReaderPermission(id, accessToken)
    }

    private fun makePubliclyViewable(fileId: String, accessToken: String) {
        setPublicReaderPermission(fileId, accessToken)
    }

    private fun setPublicReaderPermission(id: String, accessToken: String) {
        try {
            val body = JSONObject().apply {
                put("role", "reader")
                put("type", "anyone")
            }
            val url = URL("https://www.googleapis.com/drive/v3/files/$id/permissions?supportsAllDrives=true")
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
                Log.w("DriveUploader", "Gagal set permission publik utk $id ($code): $text")
            }
        } catch (e: Exception) {
            Log.w("DriveUploader", "Gagal set permission publik utk $id", e)
        }
    }
}