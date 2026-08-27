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
 * oleh HomeScreen supaya perubahan langsung terlihat.
 *
 * <-- BERUBAH: [modeSelectBackground], [mobileButtonText], [standButtonText]
 * DIHAPUS -- layar "Pilih Mode" sudah tidak ada lagi di alur app (lihat
 * nav.AppScreen), digantikan switch Mobile/Stand di Settings > Session
 * (lihat SessionSettings.boothMode).
 */
data class AppearanceSettings(
    val homeBackground: BackgroundSetting = BackgroundSetting(),
    // Disimpan sebagai Int hasil android.graphics.Color.argb agar gampang
    // diserialisasi & langsung dipakai sebagai warna Compose (Color(argbInt)).
    val buttonColorArgb: Int = DEFAULT_BUTTON_COLOR,
    val accentColorArgb: Int = DEFAULT_ACCENT_COLOR,
    val startButtonText: String = "Mulai",

    // <-- BARU: warna logo/ikon tombol "Mulai" (lihat ui.HomeScreen -- tombol
    // Mulai sekarang berupa logo kamera SVG/vector, bukan teks lagi, di-tint
    // pakai warna ini lewat ColorFilter.tint). Terpisah dari [buttonColorArgb]
    // supaya user bisa atur warna logo independen dari warna tombol lain.
    val startButtonIconColorArgb: Int = DEFAULT_BUTTON_COLOR,

    // <-- BARU: kalau true, HomeScreen pakai live view kamera (bitmap yang
    // sama dengan preview di layar Booth) sebagai background, BUKAN
    // [homeBackground] (gambar/video statis) -- lihat ui.HomeScreen &
    // MainActivity (parameter liveViewBitmap). Kalau live view belum ada
    // (mis. kamera belum tersambung), HomeScreen tetap jatuh balik ke
    // [homeBackground]/latar hitam seperti biasa.
    val useLiveViewAsHomeBackground: Boolean = false,

    // <-- BARU: path file PNG (hasil copy ke internal storage lewat
    // [MediaFileManager], sama seperti [homeBackground]) yang digambar DI ATAS
    // background Home (baik live view maupun gambar/video statis) -- mis.
    // logo event atau bingkai dekoratif. Null berarti tidak ada overlay.
    val homeOverlayImagePath: String? = null
) {
    companion object {
        const val DEFAULT_BUTTON_COLOR = 0xFF4DD0E1.toInt()
        const val DEFAULT_ACCENT_COLOR = 0xFF4DD0E1.toInt()
    }
}
