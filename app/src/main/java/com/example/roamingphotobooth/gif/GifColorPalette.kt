package com.example.roamingphotobooth.gif

import android.graphics.Bitmap

/**
 * Palet warna TETAP (256 entri, dipakai sebagai Global Color Table GIF): color-cube
 * 6x6x6 (216 warna) + 40 gradasi abu-abu tambahan untuk area netral/kulit supaya
 * tidak terlalu banding. Dipilih karena SEDERHANA & CEPAT -- pemetaan tiap piksel
 * ke index palet dihitung langsung lewat rumus (O(1) per piksel), tanpa perlu
 * analisis warna per-gambar seperti algoritma quantization adaptif (mis. NeuQuant).
 * Cukup untuk preview GIF kecil (lihat PhotoGifBuilder), bukan untuk kualitas cetak.
 *
 * Palet yang SAMA dipakai untuk SEMUA frame dalam 1 GIF (lihat GifEncoder) supaya
 * warnanya konsisten antar frame -- tidak "berkedip" gara-gara beda palet tiap frame.
 */
internal object GifColorPalette {

    private val LEVELS = intArrayOf(0, 51, 102, 153, 204, 255) // 6 level per kanal
    private const val CUBE_SIZE = 216 // 6*6*6
    private const val GRAY_COUNT = 256 - CUBE_SIZE // 40 slot sisa untuk gradasi abu-abu

    // Pola dithering Bayer 4x4 (ordered dithering) -- dipakai supaya gradasi warna
    // (mis. kulit wajah) tidak terlalu "pecah" jadi blok warna kasar akibat cuma
    // 6 level per kanal.
    private val BAYER_4X4 = intArrayOf(
        0, 8, 2, 10,
        12, 4, 14, 6,
        3, 11, 1, 9,
        15, 7, 13, 5
    )

    /** Tabel warna global GIF: 256 entri x 3 byte (R, G, B). */
    fun globalColorTable(): ByteArray {
        val bytes = ByteArray(256 * 3)
        var i = 0
        for (r in LEVELS) for (g in LEVELS) for (b in LEVELS) {
            bytes[i * 3] = r.toByte()
            bytes[i * 3 + 1] = g.toByte()
            bytes[i * 3 + 2] = b.toByte()
            i++
        }
        // Sisa slot (216..255): gradasi abu-abu merata 0..255.
        while (i < 256) {
            val grayIdx = i - CUBE_SIZE
            val v = if (GRAY_COUNT <= 1) 0 else grayIdx * 255 / (GRAY_COUNT - 1)
            bytes[i * 3] = v.toByte()
            bytes[i * 3 + 1] = v.toByte()
            bytes[i * 3 + 2] = v.toByte()
            i++
        }
        return bytes
    }

    /**
     * Petakan seluruh piksel [bitmap] ke index warna terdekat di palet ini (dengan
     * dithering ringan), hasilnya array index (1 byte/piksel) siap di-LZW-encode.
     */
    fun mapToIndices(bitmap: Bitmap): ByteArray {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val out = ByteArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val p = pixels[y * w + x]
                // Bias dithering kecil (+-~12) berdasar posisi piksel, supaya gradasi
                // halus tidak selalu dibulatkan ke arah yang sama.
                val bias = (BAYER_4X4[(y and 3) * 4 + (x and 3)] - 8)
                val r = (((p shr 16) and 0xFF) + bias).coerceIn(0, 255)
                val g = (((p shr 8) and 0xFF) + bias).coerceIn(0, 255)
                val b = ((p and 0xFF) + bias).coerceIn(0, 255)
                out[y * w + x] = nearestIndex(r, g, b).toByte()
            }
        }
        return out
    }

    /** Cari index palet terdekat untuk 1 piksel -- O(1), tanpa linear search. */
    private fun nearestIndex(r: Int, g: Int, b: Int): Int {
        // Piksel yang hampir netral (perbedaan antar kanal kecil) dipetakan ke ramp
        // abu-abu tambahan supaya wajah/highlight tidak terlalu banding dibanding
        // kalau dipaksa masuk color-cube 6 level.
        val maxC = maxOf(r, g, b)
        val minC = minOf(r, g, b)
        if (maxC - minC <= 10) {
            val avg = (r + g + b) / 3
            val grayIdx = if (GRAY_COUNT <= 1) 0 else avg * (GRAY_COUNT - 1) / 255
            return CUBE_SIZE + grayIdx
        }
        val ri = quantizeLevel(r)
        val gi = quantizeLevel(g)
        val bi = quantizeLevel(b)
        return ri * 36 + gi * 6 + bi
    }

    /** Bulatkan 1 kanal warna (0..255) ke index level terdekat (0..5) di [LEVELS]. */
    private fun quantizeLevel(c: Int): Int = ((c * 5 + 127) / 255).coerceIn(0, 5)
}
