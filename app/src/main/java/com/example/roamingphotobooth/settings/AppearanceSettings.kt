package com.example.roamingphotobooth.settings

/**
 * Latar belakang untuk 1 layar (Home atau Mode Select): bisa gambar diam,
 * bisa video (di-loop otomatis, tanpa suara). [path] adalah path absolut file
 * hasil copy ke internal storage app (lihat [MediaFileManager]) — BUKAN
 * content:// Uri dari galeri, supaya tidak bergantung pada izin akses Uri yang
 * bisa dicabut sewaktu-waktu oleh sistem.
 */
data class BackgroundSetting(
    val path: String? = null,
    val isVideo: Boolean = false
)

/**
 * Seluruh pengaturan tampilan yang bisa diubah user lewat menu
 * Settings > Appearance. Disimpan lewat [AppearanceStorage] dan dibaca ulang
 * oleh HomeScreen & ModeSelectScreen supaya perubahan langsung terlihat.
 */
data class AppearanceSettings(
    val homeBackground: BackgroundSetting = BackgroundSetting(),
    val modeSelectBackground: BackgroundSetting = BackgroundSetting(),
    // Disimpan sebagai Int hasil android.graphics.Color.argb agar gampang
    // diserialisasi & langsung dipakai sebagai warna Compose (Color(argbInt)).
    val buttonColorArgb: Int = DEFAULT_BUTTON_COLOR,
    val accentColorArgb: Int = DEFAULT_ACCENT_COLOR,
    val startButtonText: String = "Mulai",
    val mobileButtonText: String = "📱 Mobile",
    val standButtonText: String = "🖥️ Stand"
) {
    companion object {
        const val DEFAULT_BUTTON_COLOR = 0xFF4DD0E1.toInt()
        const val DEFAULT_ACCENT_COLOR = 0xFF4DD0E1.toInt()
    }
}
