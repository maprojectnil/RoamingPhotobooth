package com.example.roamingphotobooth.gif

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

/**
 * Bikin animasi GIF dari foto-foto MENTAH (tanpa frame) 1 sesi -- dipakai di layar
 * hasil akhir (FinalResultScreen) sebagai pelengkap hasil foto berframe, dan
 * diupload ke Drive dengan KETENTUAN YANG SAMA seperti upload foto mentah per-slot
 * (lihat MainActivity.enqueueRawSlotUploads / enqueueGifUpload).
 *
 * Dipanggil dari [com.example.roamingphotobooth.MainActivity.saveMergedBitmap] SEBELUM
 * sesi di-reset -- di titik itu templateSession masih pegang semua foto mentah per slot.
 */
object PhotoGifBuilder {

    // Sisi terpanjang tiap frame GIF -- dikecilkan dari resolusi asli kamera (bisa
    // beresolusi tinggi/DSLR) supaya (a) waktu encode & ukuran file GIF tetap wajar
    // untuk diupload, dan (b) preview di FinalResultScreen memang cuma perlu ukuran
    // kecil (lihat FinalResultScreen -- ukuran preview GIF sengaja dibikin tidak
    // terlalu besar).
    private const val MAX_DIMENSION_PX = 360

    private const val FRAME_DELAY_CENTISECONDS = 70 // 0.7 detik per foto

    /**
     * @param rawPhotos foto mentah per slot, urut nomor slot -- lihat
     *   TemplateSessionManager.capturedPhotosSnapshot(). BUKAN hasil merge/frame.
     * @return byte GIF89a siap disimpan/diupload, atau null kalau foto mentahnya
     *   kurang dari 2 (GIF 1 frame tidak ada gunanya dianimasikan).
     */
    fun build(rawPhotos: List<Pair<Int, Bitmap>>): ByteArray? {
        if (rawPhotos.size < 2) return null

        val reference = rawPhotos.first().second
        val (targetW, targetH) = computeTargetSize(reference.width, reference.height)

        val scaledFrames = rawPhotos.map { (_, bmp) -> centerCropScale(bmp, targetW, targetH) }
        return try {
            GifEncoder.encode(
                frames = scaledFrames,
                frameDelayCentiseconds = FRAME_DELAY_CENTISECONDS,
                loop = true
            )
        } finally {
            // Ini bitmap hasil scale KHUSUS untuk GIF (bukan bitmap asli milik
            // TemplateSessionManager) -- aman & wajib di-recycle di sini.
            scaledFrames.forEach { it.recycle() }
        }
    }

    private fun computeTargetSize(sourceW: Int, sourceH: Int): Pair<Int, Int> {
        val longestSide = maxOf(sourceW, sourceH)
        // Jangan pernah upscale kalau foto sudah lebih kecil dari MAX_DIMENSION_PX.
        val scale = minOf(MAX_DIMENSION_PX.toFloat() / longestSide, 1f)
        val targetW = (sourceW * scale).toInt().coerceAtLeast(2)
        val targetH = (sourceH * scale).toInt().coerceAtLeast(2)
        return targetW to targetH
    }

    /**
     * Scale + center-crop ke ukuran target PERSIS (cover-fit) -- semua frame GIF
     * WAJIB berukuran identik (1 kanvas GIF cuma boleh 1 ukuran, lihat GifEncoder).
     */
    private fun centerCropScale(source: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val result = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val srcRatio = source.width.toFloat() / source.height.toFloat()
        val dstRatio = targetW.toFloat() / targetH.toFloat()

        val srcRect = if (srcRatio > dstRatio) {
            // Sumber lebih "lebar" dari target -> crop kiri-kanan.
            val cropW = (source.height * dstRatio).toInt().coerceIn(1, source.width)
            val left = (source.width - cropW) / 2
            Rect(left, 0, left + cropW, source.height)
        } else {
            // Sumber lebih "tinggi" dari target -> crop atas-bawah.
            val cropH = (source.width / dstRatio).toInt().coerceIn(1, source.height)
            val top = (source.height - cropH) / 2
            Rect(0, top, source.width, top + cropH)
        }
        canvas.drawBitmap(source, srcRect, RectF(0f, 0f, targetW.toFloat(), targetH.toFloat()), paint)
        return result
    }
}
