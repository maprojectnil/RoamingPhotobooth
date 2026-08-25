package com.example.roamingphotobooth.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.example.roamingphotobooth.BuildConfig
import com.example.roamingphotobooth.drive.DriveAuth
import com.example.roamingphotobooth.drive.DriveUploader
import com.example.roamingphotobooth.settings.SessionSettingsStorage
import com.example.roamingphotobooth.status.FirebaseAuthClient
import com.example.roamingphotobooth.status.PhotoStatusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Upload 1 foto ke Drive di background, TERPISAH dari alur tampil-QR (yang
 * sudah instan begitu foto selesai di-merge, lihat MainActivity.saveMergedBitmap).
 *
 * Kenapa WorkManager (bukan coroutine langsung seperti sebelumnya):
 *  - Auto-retry dengan backoff kalau upload gagal (jaringan venue sering putus-putus)
 *  - Constraint NetworkType.CONNECTED -> job otomatis nunggu sampai ada koneksi,
 *    tidak perlu polling manual
 *  - Job tetap tersimpan di WorkManager DB kalau app di-kill oleh OS di tengah
 *    upload -> lanjut lagi begitu proses jalan lagi (coroutine biasa akan hilang
 *    total begitu proses mati)
 *
 * Firestore diupdate di 3 titik: "uploading" di awal (idempotent, jaga-jaga kalau
 * worker retry), "ready" kalau sukses, "failed" kalau semua percobaan habis.
 */
class DriveUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_SLUG = "slug"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_FILE_PATH = "file_path"
        private const val MAX_ATTEMPTS = 8
        private const val TAG = "DriveUploadWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val slug = inputData.getString(KEY_SLUG) ?: return@withContext Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME) ?: return@withContext Result.failure()
        val filePath = inputData.getString(KEY_FILE_PATH) ?: return@withContext Result.failure()
        val file = File(filePath)

        val statusRepo = PhotoStatusRepository(
            projectId = BuildConfig.FIRESTORE_PROJECT_ID,
            authClient = FirebaseAuthClient(applicationContext, BuildConfig.FIREBASE_WEB_API_KEY)
        )

        if (!file.exists()) {
            // File lokal sudah hilang (mis. cache dibersihkan) -> tidak ada yang bisa
            // di-retry lagi, tandai gagal permanen supaya landing page tidak nge-hang
            // di status "uploading" selamanya.
            runCatching { statusRepo.markFailed(slug, "File lokal tidak ditemukan") }
            return@withContext Result.failure()
        }

        try {
            statusRepo.markUploading(slug, fileName, runAttemptCount + 1)

            val driveAuth = DriveAuth(
                clientId = BuildConfig.DRIVE_OAUTH_CLIENT_ID,
                clientSecret = BuildConfig.DRIVE_OAUTH_CLIENT_SECRET,
                refreshToken = BuildConfig.DRIVE_OAUTH_REFRESH_TOKEN
            )
            val uploader = DriveUploader(driveAuth, resolveDriveFolderId())

            val jpegBytes = file.readBytes()
            val result = uploader.uploadBytes(fileName, jpegBytes)

            statusRepo.markReady(slug, result.shareUrl, result.fileId)
            Log.i(TAG, "Sukses upload $fileName (slug=$slug) -> ${result.fileId}")

            // Sudah aman di Drive -> file cache lokal boleh dibuang.
            runCatching { file.delete() }

            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Upload gagal (percobaan ke-${runAttemptCount + 1}) utk $slug", e)

            if (runAttemptCount + 1 >= MAX_ATTEMPTS) {
                runCatching { statusRepo.markFailed(slug, e.message ?: "Upload gagal setelah $MAX_ATTEMPTS percobaan") }
                Result.failure()
            } else {
                // Result.retry() -> WorkManager jadwalkan ulang otomatis dengan backoff
                // (exponential, dikonfigurasi saat enqueue di MainActivity), dan tetap
                // nunggu constraint NetworkType.CONNECTED kalau memang lagi offline.
                Result.retry()
            }
        }
    }

    /**
     * <-- BARU: Folder ID Drive sekarang bisa di-CUSTOM langsung dari app lewat
     * Settings > Session (lihat SessionSettingsScreen/SessionSettingsStorage),
     * tanpa perlu build ulang APK dengan gradle.properties baru. Dibaca ULANG di
     * sini (bukan cuma sekali di awal doWork()) supaya kalau job ini di-retry
     * setelah user sempat ganti settingnya, upload berikutnya pakai folder yang
     * PALING BARU. Kalau field-nya kosong (user belum pernah isi), fallback ke
     * BuildConfig.DRIVE_FOLDER_ID -- nilai yang di-embed dari gradle.properties
     * saat build, supaya app yang belum pernah disentuh setting-nya tetap jalan
     * seperti sebelumnya tanpa perubahan apa pun.
     */
    private fun resolveDriveFolderId(): String {
        val custom = SessionSettingsStorage(applicationContext).load().driveFolderId.trim()
        return custom.ifEmpty { BuildConfig.DRIVE_FOLDER_ID }
    }
}

/** Helper bikin Data input work request, dipanggil dari MainActivity. */
fun buildUploadInputData(slug: String, fileName: String, filePath: String): Data =
    Data.Builder()
        .putString(DriveUploadWorker.KEY_SLUG, slug)
        .putString(DriveUploadWorker.KEY_FILE_NAME, fileName)
        .putString(DriveUploadWorker.KEY_FILE_PATH, filePath)
        .build()
