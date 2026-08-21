package com.example.roamingphotobooth.gallery

import kotlinx.serialization.Serializable

/**
 * 1 entri riwayat sesi foto yang sudah SELESAI (semua slot terisi & hasil akhir
 * sudah tersimpan) — dipakai fitur Galeri supaya user bisa "recall" (buka lagi)
 * sesi-sesi sebelumnya buat lihat/print ulang hasilnya atau tampilkan lagi QR-nya,
 * tanpa perlu foto itu masih ada di finalResultBitmap sesi yang sedang berjalan
 * (yang di-reset begitu sesi baru dimulai — lihat MainActivity.startNewSession).
 *
 * [mediaUri] menunjuk ke file JPEG PERMANEN yang sudah disimpan ke MediaStore
 * (Pictures/RoamingPhotobooth) lewat MainActivity.saveMergedBitmap -- ini yang
 * dipakai buat re-load bitmap resolusi penuh saat entri dibuka dari Galeri
 * (untuk ditampilkan & untuk dikirim print ulang). BUKAN file cache upload
 * (cacheDir/upload_<slug>.jpg), karena itu terhapus begitu upload ke Drive sukses
 * (lihat DriveUploadWorker) -- MediaStore URI yang permanen sampai user hapus
 * manual lewat galeri foto bawaan HP.
 *
 * [landingUrl] dipakai buat regenerate QR code kapan saja (QR cuma encode URL
 * ini, lihat util.QrCodeGenerator) -- tidak perlu upload ulang atau simpan
 * bitmap QR itu sendiri.
 */
@Serializable
data class GallerySessionEntry(
    val slug: String,
    val fileName: String,
    val mediaUri: String,
    val landingUrl: String,
    val createdAtMillis: Long
)
