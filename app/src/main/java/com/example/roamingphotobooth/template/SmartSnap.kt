package com.example.roamingphotobooth.template

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * Jenis snap yang didukung Smart Snap. Disederhanakan: cuma garis seperempat
 * (Quarters) yang tersisa — Center/Thirds/Edges sudah dihapus supaya perilaku
 * snap lebih simpel & gak terlalu banyak "nyantol" ke mana-mana.
 */
enum class SnapType {
    QUARTERS // garis seperempat (25%, 50%, 75%)
}

/** 1 garis target snap: posisinya (rasio 0.0-1.0 relatif bingkai) + kategorinya. */
data class SnapTarget(val ratio: Float, val type: SnapType)

/**
 * Toggle Smart Snap. Cuma 1 switch sekarang (dulu per-jenis) karena cuma
 * Quarters yang tersisa.
 */
data class SmartSnapSettings(
    val enabled: Boolean = true
)

/**
 * Guide line yang lagi aktif ditampilkan di atas workspace saat drag/resize lagi
 * nge-snap ke suatu garis. Snap sekarang cuma dipakai di sumbu X (garis VERTIKAL,
 * buat nge-snap posisi horizontal slot) — garis horizontal (sumbu Y) sengaja
 * dikosongkan terus supaya geser/resize ke atas-bawah tetap leluasa tanpa nyantol.
 */
data class SnapGuides(
    val vertical: Map<Float, SnapType> = emptyMap(),
    val horizontal: Map<Float, SnapType> = emptyMap()
) {
    val isEmpty: Boolean get() = vertical.isEmpty() && horizontal.isEmpty()

    companion object {
        val NONE = SnapGuides()
    }
}

/**
 * Mesin snap murni (stateless) buat fitur Smart Snap. Dipanggil tiap event drag dari
 * SlotEditorBox buat cari garis Quarters terdekat dalam jarak [THRESHOLD_DP], lalu
 * ngembaliin posisi yang udah "ditarik" pas ke garis itu. Snap ini HANYA dipakai di
 * sumbu X oleh SlotEditorBox (lihat catatan di [SnapGuides]).
 *
 * Threshold-nya RASIONAL: didefinisikan dalam dp lalu dikonversi ke rasio berdasarkan
 * ukuran kontainer AKTUAL saat itu (bukan angka rasio tetap yang di-hardcode), supaya
 * "jarak kepekaan" snap kerasa konsisten secara visual di berbagai ukuran bingkai /
 * layar — bukannya makin gampang nyantol di bingkai kecil & makin susah di bingkai besar.
 */
object SmartSnap {

    /**
     * Jarak toleransi snap, dalam dp — dikonversi ke px oleh caller lewat LocalDensity.
     * Diturunin jadi 2dp: sebelumnya kerasa terlalu gampang "nyantol" pas drag,
     * jadi threshold-nya dipersempit secara signifikan biar drag tetap leluasa
     * & snap cuma kena pas beneran deket ke garis quarter.
     */
    const val THRESHOLD_DP = 2f

    /** Bangun daftar garis target Quarters kalau [settings] aktif. */
    fun buildTargets(settings: SmartSnapSettings): List<SnapTarget> {
        if (!settings.enabled) return emptyList()
        return listOf(
            SnapTarget(0.25f, SnapType.QUARTERS),
            SnapTarget(0.5f, SnapType.QUARTERS),
            SnapTarget(0.75f, SnapType.QUARTERS)
        )
    }

    /**
     * Snap posisi 1 sumbu — dipakai saat GESER (move) slot. Nyoba nge-snap sisi awal
     * (kiri/atas), titik tengah, dan sisi akhir (kanan/bawah) slot ke tiap garis
     * target; yang selisihnya paling kecil & masih di dalam threshold yang menang.
     *
     * @param startRatio posisi kiri/atas slot sekarang (rasio 0..1)
     * @param sizeRatio lebar/tinggi slot (rasio 0..1, tidak berubah saat geser)
     * @return posisi awal baru (sudah di-snap kalau ada yang kena) + garis yang kena, kalau ada
     */
    fun snapPosition(
        startRatio: Float,
        sizeRatio: Float,
        containerSizePx: Float,
        thresholdPx: Float,
        targets: List<SnapTarget>
    ): Pair<Float, SnapTarget?> {
        if (targets.isEmpty() || containerSizePx <= 0f) return startRatio to null
        val thresholdRatio = thresholdPx / containerSizePx
        val centerRatio = startRatio + sizeRatio / 2f
        val endRatio = startRatio + sizeRatio

        var bestDelta = thresholdRatio
        var bestStart = startRatio
        var bestTarget: SnapTarget? = null

        for (target in targets) {
            val dStart = abs(startRatio - target.ratio)
            if (dStart < bestDelta) {
                bestDelta = dStart; bestStart = target.ratio; bestTarget = target
            }
            val dCenter = abs(centerRatio - target.ratio)
            if (dCenter < bestDelta) {
                bestDelta = dCenter; bestStart = target.ratio - sizeRatio / 2f; bestTarget = target
            }
            val dEnd = abs(endRatio - target.ratio)
            if (dEnd < bestDelta) {
                bestDelta = dEnd; bestStart = target.ratio - sizeRatio; bestTarget = target
            }
        }
        return bestStart to bestTarget
    }

    /**
     * Snap setengah-ukuran 1 sumbu — dipakai saat RESIZE dengan metode "Scale from
     * Center" (lihat SlotEditorBox). Karena titik pusat slot TETAP diam selama
     * resize, yang di-snap adalah sisi tepi (kiri/kanan atau atas/bawah) ke garis
     * target; setengah-ukuran baru dihitung dari jarak pusat ke garis tersebut.
     */
    fun snapHalfExtent(
        centerRatio: Float,
        halfSizeRatio: Float,
        containerSizePx: Float,
        thresholdPx: Float,
        targets: List<SnapTarget>
    ): Pair<Float, SnapTarget?> {
        if (targets.isEmpty() || containerSizePx <= 0f) return halfSizeRatio to null
        val thresholdRatio = thresholdPx / containerSizePx
        val nearEdge = centerRatio - halfSizeRatio
        val farEdge = centerRatio + halfSizeRatio

        var bestDelta = thresholdRatio
        var bestHalf = halfSizeRatio
        var bestTarget: SnapTarget? = null

        for (target in targets) {
            val dNear = abs(nearEdge - target.ratio)
            if (dNear < bestDelta) {
                bestDelta = dNear; bestHalf = abs(centerRatio - target.ratio); bestTarget = target
            }
            val dFar = abs(farEdge - target.ratio)
            if (dFar < bestDelta) {
                bestDelta = dFar; bestHalf = abs(target.ratio - centerRatio); bestTarget = target
            }
        }
        return bestHalf to bestTarget
    }

    /** Warna garis panduan Quarters. */
    fun colorFor(type: SnapType): Color = when (type) {
        SnapType.QUARTERS -> Color(0xFFFFEB3B)
    }

    /** Label singkat, dipakai buat switch toggle di panel kontrol. */
    fun labelFor(type: SnapType): String = when (type) {
        SnapType.QUARTERS -> "Quarters"
    }
}
