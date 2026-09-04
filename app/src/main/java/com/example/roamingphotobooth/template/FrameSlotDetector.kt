package com.example.roamingphotobooth.template

import android.graphics.Bitmap
import kotlin.math.abs

/**
 * Hasil deteksi otomatis 1 lubang transparan di PNG bingkai — posisi & ukuran dalam
 * RASIO (0.0 - 1.0) relatif terhadap dimensi bingkai, format sama seperti bidang
 * geometri di [PhotoSlot]. Sengaja dipisah dari PhotoSlot karena di titik ini belum
 * ada `id`/`order` — itu baru diisi terakhir oleh pemanggil (lihat
 * [TemplateEditorViewModel.autoDetectSlots]), karena urutan hasil deteksi cuma
 * TEBAKAN AWAL yang masih bisa diubah user.
 */
data class DetectedHole(
    val xRatio: Float,
    val yRatio: Float,
    val widthRatio: Float,
    val heightRatio: Float
)

/**
 * Fitur "Deteksi Otomatis Slot" di editor bingkai: cari area transparan (bolongan)
 * di PNG bingkai lalu ubah tiap bolongan jadi 1 kandidat [DetectedHole].
 *
 * Cara kerja singkat:
 * 1. Downscale bitmap ke grid analisis yang lebih kecil (performa — bingkai asli
 *    bisa beresolusi sangat besar, scan piksel-per-piksel di situ lambat & boros
 *    memory, padahal untuk mendeteksi BENTUK bolongan tidak perlu presisi sub-pixel).
 * 2. Tandai tiap piksel grid "transparan" kalau alpha-nya di bawah [ALPHA_THRESHOLD]
 *    (bukan cuma alpha == 0, supaya tepi bolongan yang di-anti-alias tetap terhitung).
 * 3. Cari komponen piksel transparan yang terhubung (flood fill 4-arah / BFS) —
 *    tiap komponen = 1 kandidat bolongan, dengan bounding box-nya.
 * 4. Buang komponen yang:
 *    - Menyentuh tepi grid (dianggap latar transparan DI LUAR bingkai, bukan slot
 *      foto — slot foto pada frame PNG normal selalu terkurung di dalam bentuk
 *      bingkainya, tidak "bocor" sampai tepi file).
 *    - Terlalu kecil (di bawah [MIN_AREA_RATIO] dari total luas bingkai) — biasanya
 *      noise/anti-aliasing kecil di tepi bingkai, bukan slot foto sungguhan.
 * 5. Urutkan sisa bolongan dalam "reading order" (baris atas→bawah, dalam 1 baris
 *    kiri→kanan) sebagai tebakan awal urutan pengambilan foto.
 * 6. Gelembungkan tiap box beberapa piksel (lihat [EDGE_PADDING_GRID_PX]) supaya
 *    slot yang dihasilkan mentok/sedikit melebihi tepi bolongan — bukan nyisain
 *    celah kecil di tepinya (efek dari piksel anti-alias tepi yang tidak terhitung
 *    "transparan" saat scan alpha).
 *
 * CATATAN: ini heuristik, bukan sihir — pola bingkai yang aneh (mis. bolongan yang
 * sengaja menyentuh tepi PNG, atau efek bayangan semi-transparan di dalam bolongan)
 * bisa saja meleset. Makanya hasilnya SELALU bisa dikoreksi manual oleh user di
 * editor (geser/resize/urutan/duplikat/hapus) — deteksi otomatis cuma titik awal,
 * bukan keputusan final.
 */
object FrameSlotDetector {

    private const val ALPHA_THRESHOLD = 10
    private const val MIN_AREA_RATIO = 0.003f
    private const val MAX_GRID_DIMENSION = 500

    // Bounding box dari flood fill cenderung sedikit LEBIH KECIL dari bolongan yang
    // kelihatan secara visual — soalnya piksel tepi yang semi-transparan (hasil
    // anti-aliasing PNG, alpha di atas ALPHA_THRESHOLD tapi belum solid) tidak
    // dihitung "transparan" sama sekali. Efeknya: slot hasil deteksi jadi ada celah
    // kecil, tidak mentok pas ke tepi bolongan. Makanya tiap sisi box digelembungkan
    // sedikit (dalam satuan piksel GRID analisis, bukan piksel bingkai asli) supaya
    // slot yang dihasilkan sedikit "melebihi" bolongan alih-alih pas-pasan/kurang.
    private const val EDGE_PADDING_GRID_PX = 3

    /** 1 bounding box sementara selama flood fill, dalam koordinat grid (bukan rasio). */
    private class GridBox(var minX: Int, var minY: Int, var maxX: Int, var maxY: Int) {
        var area: Int = 0
        var touchesBorder: Boolean = false
    }

    /**
     * Jalankan deteksi pada [source] (bitmap bingkai PNG asli, idealnya masih dengan
     * channel alpha utuh). Return daftar bolongan yang lolos filter, sudah terurut
     * reading order. Return list kosong kalau tidak ada bolongan yang cukup besar
     * ditemukan (mis. bingkai PNG tidak punya area transparan sama sekali).
     */
    fun detectHoles(source: Bitmap): List<DetectedHole> {
        if (source.width <= 0 || source.height <= 0) return emptyList()

        val scale = MAX_GRID_DIMENSION.toFloat() / maxOf(source.width, source.height)
        val downscaled = scale < 1f
        val gridW: Int
        val gridH: Int
        val scaledBitmap: Bitmap
        if (downscaled) {
            gridW = (source.width * scale).toInt().coerceAtLeast(1)
            gridH = (source.height * scale).toInt().coerceAtLeast(1)
            scaledBitmap = Bitmap.createScaledBitmap(source, gridW, gridH, true)
        } else {
            gridW = source.width
            gridH = source.height
            scaledBitmap = source
        }

        // Pastikan config ARGB_8888 supaya getPixels() balikin nilai alpha yang benar
        // (createScaledBitmap biasanya sudah mewarisi config sumber, tapi dijaga tetap
        // aman kalau suatu saat sumbernya bukan ARGB_8888).
        val needsCopy = scaledBitmap.config != Bitmap.Config.ARGB_8888
        val argbBitmap = if (needsCopy) scaledBitmap.copy(Bitmap.Config.ARGB_8888, false) else scaledBitmap

        val pixels = IntArray(gridW * gridH)
        argbBitmap.getPixels(pixels, 0, gridW, 0, 0, gridW, gridH)

        if (downscaled) scaledBitmap.recycle()
        if (needsCopy) argbBitmap.recycle()

        fun alphaAt(x: Int, y: Int): Int = (pixels[y * gridW + x] ushr 24) and 0xFF
        fun isTransparent(x: Int, y: Int): Boolean = alphaAt(x, y) < ALPHA_THRESHOLD

        val visited = BooleanArray(gridW * gridH)
        val minArea = (MIN_AREA_RATIO * gridW * gridH).toInt().coerceAtLeast(4)
        val boxes = mutableListOf<GridBox>()

        // Buffer antrian BFS dipakai ulang (bukan dialokasikan per komponen) —
        // ukurannya cukup buat komponen sebesar apapun (maksimal seluruh grid).
        val queueX = IntArray(gridW * gridH)
        val queueY = IntArray(gridW * gridH)

        for (startY in 0 until gridH) {
            for (startX in 0 until gridW) {
                val startIdx = startY * gridW + startX
                if (visited[startIdx] || !isTransparent(startX, startY)) continue

                var head = 0
                var tail = 0
                queueX[tail] = startX; queueY[tail] = startY; tail++
                visited[startIdx] = true

                val box = GridBox(startX, startY, startX, startY)

                while (head < tail) {
                    val cx = queueX[head]
                    val cy = queueY[head]
                    head++

                    box.area++
                    if (cx < box.minX) box.minX = cx
                    if (cx > box.maxX) box.maxX = cx
                    if (cy < box.minY) box.minY = cy
                    if (cy > box.maxY) box.maxY = cy
                    if (cx == 0 || cy == 0 || cx == gridW - 1 || cy == gridH - 1) {
                        box.touchesBorder = true
                    }

                    // Tetangga 4-arah (atas/bawah/kiri/kanan) — cukup buat komponen
                    // "menyatu" secara visual, tidak perlu 8-arah (diagonal).
                    val nx1 = cx - 1; val ny1 = cy
                    val nx2 = cx + 1; val ny2 = cy
                    val nx3 = cx; val ny3 = cy - 1
                    val nx4 = cx; val ny4 = cy + 1

                    if (nx1 in 0 until gridW && ny1 in 0 until gridH) {
                        val idx = ny1 * gridW + nx1
                        if (!visited[idx] && isTransparent(nx1, ny1)) {
                            visited[idx] = true; queueX[tail] = nx1; queueY[tail] = ny1; tail++
                        }
                    }
                    if (nx2 in 0 until gridW && ny2 in 0 until gridH) {
                        val idx = ny2 * gridW + nx2
                        if (!visited[idx] && isTransparent(nx2, ny2)) {
                            visited[idx] = true; queueX[tail] = nx2; queueY[tail] = ny2; tail++
                        }
                    }
                    if (nx3 in 0 until gridW && ny3 in 0 until gridH) {
                        val idx = ny3 * gridW + nx3
                        if (!visited[idx] && isTransparent(nx3, ny3)) {
                            visited[idx] = true; queueX[tail] = nx3; queueY[tail] = ny3; tail++
                        }
                    }
                    if (nx4 in 0 until gridW && ny4 in 0 until gridH) {
                        val idx = ny4 * gridW + nx4
                        if (!visited[idx] && isTransparent(nx4, ny4)) {
                            visited[idx] = true; queueX[tail] = nx4; queueY[tail] = ny4; tail++
                        }
                    }
                }

                boxes.add(box)
            }
        }

        val holes = boxes
            .filter { !it.touchesBorder && it.area >= minArea }
            .map { b ->
                // Gelembungkan box sedikit ke segala arah (lihat EDGE_PADDING_GRID_PX)
                // supaya slot yang dihasilkan mentok/sedikit melebihi tepi bolongan,
                // bukan malah nyisain celah kecil. Tetap di-clamp ke dalam grid supaya
                // tidak "bocor" keluar bingkai kalau bolongannya dekat tepi.
                val paddedMinX = (b.minX - EDGE_PADDING_GRID_PX).coerceAtLeast(0)
                val paddedMinY = (b.minY - EDGE_PADDING_GRID_PX).coerceAtLeast(0)
                val paddedMaxX = (b.maxX + EDGE_PADDING_GRID_PX).coerceAtMost(gridW - 1)
                val paddedMaxY = (b.maxY + EDGE_PADDING_GRID_PX).coerceAtMost(gridH - 1)
                DetectedHole(
                    xRatio = paddedMinX.toFloat() / gridW,
                    yRatio = paddedMinY.toFloat() / gridH,
                    widthRatio = (paddedMaxX - paddedMinX + 1).toFloat() / gridW,
                    heightRatio = (paddedMaxY - paddedMinY + 1).toFloat() / gridH
                )
            }

        return sortReadingOrder(holes)
    }

    /**
     * Urutkan bolongan dalam "reading order": kelompokkan ke "baris" dulu berdasar
     * overlap titik-tengah vertikal (2 bolongan dianggap 1 baris kalau jarak
     * titik-tengah Y-nya masih di bawah toleransi ~60% tinggi bolongan), baris
     * diurutkan atas→bawah, lalu di dalam tiap baris diurutkan kiri→kanan.
     *
     * Ini cuma tebakan awal buat `order` — kalau tata letak bingkai tidak berbentuk
     * grid rapi (mis. bolongan miring/bertumpuk), hasilnya mungkin tidak persis
     * sesuai keinginan user, makanya urutan tetap bisa dikoreksi manual di editor.
     */
    private fun sortReadingOrder(holes: List<DetectedHole>): List<DetectedHole> {
        if (holes.size <= 1) return holes

        val sortedByY = holes.sortedBy { it.yRatio }
        val rows = mutableListOf<MutableList<DetectedHole>>()

        for (hole in sortedByY) {
            val holeCenterY = hole.yRatio + hole.heightRatio / 2f
            val row = rows.firstOrNull { row ->
                row.any { existing ->
                    val existingCenterY = existing.yRatio + existing.heightRatio / 2f
                    val tolerance = maxOf(existing.heightRatio, hole.heightRatio) * 0.6f
                    abs(existingCenterY - holeCenterY) < tolerance
                }
            }
            if (row != null) row.add(hole) else rows.add(mutableListOf(hole))
        }

        return rows
            .sortedBy { row -> row.minOf { it.yRatio } }
            .flatMap { row -> row.sortedBy { it.xRatio } }
    }
}
