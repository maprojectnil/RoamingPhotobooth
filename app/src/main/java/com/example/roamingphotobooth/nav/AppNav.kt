package com.example.roamingphotobooth.nav

/**
 * Layar-layar utama aplikasi: Home -> pilih mode (Mobile/Stand) -> layar booth.
 *
 * <-- BARU: [GALLERY] — riwayat sesi foto yang sudah selesai (lihat
 * com.example.roamingphotobooth.gallery.GalleryScreen), dibuka dari tombol
 * galeri di HomeScreen. Terpisah dari alur Home -> Mode Select -> Booth,
 * jadi bisa diakses kapan saja tanpa perlu mulai sesi baru.
 */
enum class AppScreen { HOME, MODE_SELECT, BOOTH, GALLERY }

/**
 * Mode booth yang dipilih user di [AppScreen.MODE_SELECT]. Menentukan composable
 * mana yang dipakai di [AppScreen.BOOTH] — lihat booth.mobile.MobileBoothScreen
 * dan booth.stand.StandBoothScreen.
 */
enum class BoothMode { MOBILE, STAND }
