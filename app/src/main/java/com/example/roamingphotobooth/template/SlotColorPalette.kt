package com.example.roamingphotobooth.template

import android.graphics.Color as AndroidColor

/**
 * Palet warna konsisten untuk badge/kotak nomor urut slot foto — dipakai di
 * thumbnail Template List DAN di preview live booth (Stand & Mobile, saat
 * slot masih kosong belum ada fotonya). Nomor urut yang sama (= slot
 * duplikat, bakal diisi 1 foto yang sama — lihat [PhotoSlot.order]) SELALU
 * dapat warna yang sama di mana pun ditampilkan, karena keduanya pakai
 * fungsi yang sama persis dari sini.
 *
 * Urutan warna dasar sengaja dipilih kontras & gampang dibedakan
 * berdampingan (kuning, biru, oranye, hijau, ungu, pink, teal, coklat).
 * Kalau jumlah nomor unik di 1 template lebih banyak dari itu, warna
 * tambahan digenerate otomatis lewat rotasi hue "golden angle" di ruang
 * HSV — tetap kontras & tidak keliatan berulang walau ada banyak slot.
 */
object SlotColorPalette {

    private val baseColors = intArrayOf(
        0xFFFFC400.toInt(), // kuning
        0xFF3B6FB6.toInt(), // biru
        0xFFE8542A.toInt(), // oranye/merah
        0xFF43A047.toInt(), // hijau
        0xFF8E24AA.toInt(), // ungu
        0xFFD81B60.toInt(), // pink
        0xFF00897B.toInt(), // teal
        0xFF6D4C41.toInt(), // coklat
    )

    /**
     * Bangun map `order -> rank` (0, 1, 2, ...) dari daftar semua `order` di 1
     * template, berdasarkan urutan nomor UNIK yang muncul (sorted ascending).
     * Slot dengan `order` sama (duplikat) otomatis dapat rank — dan lewat itu
     * warna — yang sama. Panggil sekali per template lalu dipakai berulang
     * lewat [colorForOrder], jangan dihitung ulang per-slot.
     */
    fun buildRankMap(orders: List<Int>): Map<Int, Int> =
        orders.distinct().sorted().withIndex().associate { (idx, order) -> order to idx }

    /** Warna ARGB (Int, format sama seperti android.graphics.Color) untuk 1 rank. */
    fun colorForRank(rank: Int): Int {
        if (rank < baseColors.size) return baseColors[rank]
        val hue = (rank * 137.508f) % 360f
        return AndroidColor.HSVToColor(floatArrayOf(hue, 0.62f, 0.88f))
    }

    /** Shortcut: warna langsung dari nomor `order`, pakai [rankMap] yang sudah dibangun. */
    fun colorForOrder(order: Int, rankMap: Map<Int, Int>): Int =
        colorForRank(rankMap[order] ?: 0)

    /** Versi sedikit lebih gelap dari sebuah warna ARGB — dipakai untuk garis tepi/border. */
    fun darken(color: Int, factor: Float = 0.72f): Int {
        val r = (AndroidColor.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (AndroidColor.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (AndroidColor.blue(color) * factor).toInt().coerceIn(0, 255)
        return AndroidColor.rgb(r, g, b)
    }
}
