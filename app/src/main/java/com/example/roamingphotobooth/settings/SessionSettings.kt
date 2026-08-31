package com.example.roamingphotobooth.settings

import com.example.roamingphotobooth.nav.BoothMode

/**
 * Pengaturan DEFAULT untuk sesi booth (Mobile & Stand), diatur lewat
 * Settings > Session (lihat [SessionSettingsScreen]) dan disimpan lewat
 * [SessionSettingsStorage].
 *
 * PENTING: ini adalah nilai DEFAULT yang dipakai setiap kali sesi BARU
 * dimulai (lihat MainActivity.loadActiveTemplate() & startNewSession()).
 * Ini BUKAN pengaturan yang "mengunci" sesi yang sedang berjalan — misalnya
 * [mirrorCamera] cuma dipakai untuk MENGISI nilai awal toggle Mirror di
 * Session Preview; begitu sesi jalan, user boleh ubah toggle itu sendiri
 * tanpa mengubah default global di sini (lihat MainActivity.sessionMirrorEnabled).
 */
data class SessionSettings(
    // <-- BARU: menggantikan layar Mode Select (Mobile/Stand) yang sudah
    // dihapus dari alur Home -> Booth. Diatur lewat switch "Mode Booth" di
    // Settings > Session (lihat [SessionSettingsScreen]) -- begitu user
    // menekan "Mulai" di Home, app langsung pakai mode ini tanpa nanya lagi
    // (lihat MainActivity.openFramePicker()).
    val boothMode: BoothMode = BoothMode.STAND,

    // Durasi countdown (detik) sebelum shutter software ditembak di mode Stand.
    // Dipakai sebagai titik awal hitung mundur (mis. 3 -> 3, 2, 1).
    val countdownSeconds: Int = DEFAULT_COUNTDOWN_SECONDS,

    // Default true supaya TIDAK mengubah behavior yang sudah ada sebelum fitur
    // ini ditambahkan — sebelumnya mirror horizontal SELALU aktif (hardcoded)
    // baik di preview live view maupun di foto hasil akhir.
    val mirrorCamera: Boolean = true,

    // Default false: sebelum fitur ini ada, user SELALU harus menekan tombol
    // shutter manual untuk tiap slot (tidak ada auto countdown).
    val autoCountdownNextSlots: Boolean = false,

    // <-- BARU: ID folder Google Drive tujuan upload foto hasil akhir, bisa
    // di-custom LANGSUNG dari app (Settings > Session) tanpa perlu build ulang
    // APK. Kosong ("") berarti "pakai default dari gradle.properties saat build"
    // (BuildConfig.DRIVE_FOLDER_ID yang di-embed lewat project property
    // DRIVE_FOLDER_ID) — lihat DriveUploadWorker.resolveDriveFolderId().
    val driveFolderId: String = "",

    // <-- BARU: kalau ON, tiap sesi foto (1 sesi = 1 foto hasil akhir template)
    // otomatis dapat SUBFOLDER sendiri di dalam [driveFolderId] (mis. "Sesi_2026-08-31_14-05-30"),
    // bukan langsung numpuk semua foto rata di folder yang sama. Default false
    // supaya TIDAK mengubah behavior yang sudah ada sebelum fitur ini ditambahkan
    // -- lihat DriveUploadWorker.doWork() & MainActivity.saveMergedBitmap().
    val createSessionFolder: Boolean = false
) {
    companion object {
        const val DEFAULT_COUNTDOWN_SECONDS = 3
        val COUNTDOWN_RANGE = 1..10
    }
}
