package com.example.roamingphotobooth.nav

/**
 * Layar-layar utama aplikasi: Home -> pilih mode (Mobile/Stand) -> layar booth.
 */
enum class AppScreen { HOME, MODE_SELECT, BOOTH }

/**
 * Mode booth yang dipilih user di [AppScreen.MODE_SELECT]. Menentukan composable
 * mana yang dipakai di [AppScreen.BOOTH] — lihat booth.mobile.MobileBoothScreen
 * dan booth.stand.StandBoothScreen.
 */
enum class BoothMode { MOBILE, STAND }
