package com.example.roamingphotobooth.nav

/**
 * Layar-layar utama aplikasi: Home -> pilih frame -> layar booth.
 *
 * <-- BERUBAH: layar pilih mode (Mobile/Stand) SUDAH DIHAPUS dari alur ini.
 * Mode booth sekarang murni ditentukan lewat switch "Mode Booth" di
 * Settings > Session (lihat [com.example.roamingphotobooth.settings.SessionSettings.boothMode]),
 * jadi begitu user menekan "Mulai" di Home, app langsung masuk ke pemilihan
 * frame (TemplateEditorActivity) memakai mode yang sudah diatur itu -- tidak
 * ada lagi layar perantara buat pilih Mobile/Stand tiap kali mulai sesi.
 *
 * <-- BARU: [GALLERY] — riwayat sesi foto yang sudah selesai (lihat
 * com.example.roamingphotobooth.gallery.GalleryScreen), dibuka dari tombol
 * galeri di HomeScreen. Terpisah dari alur Home -> Booth, jadi bisa diakses
 * kapan saja tanpa perlu mulai sesi baru.
 */
enum class AppScreen { HOME, BOOTH, GALLERY }

/**
 * Mode booth aktif untuk sesi yang sedang/akan berjalan. Sebelumnya dipilih
 * user tiap sesi lewat layar Mode Select; sekarang diambil dari default
 * [com.example.roamingphotobooth.settings.SessionSettings.boothMode] (diatur
 * lewat switch Mobile/Stand di Settings > Session) begitu user menekan
 * "Mulai" -- lihat MainActivity.openFramePicker(). Menentukan composable mana
 * yang dipakai di [AppScreen.BOOTH] — lihat booth.mobile.MobileBoothScreen
 * dan booth.stand.StandBoothScreen.
 */
enum class BoothMode { MOBILE, STAND }
