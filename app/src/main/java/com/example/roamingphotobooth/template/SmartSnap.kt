package com.example.roamingphotobooth.template

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * Jenis-jenis snap yang didukung Smart Snap. Tiap jenis punya toggle sendiri di
 * panel kontrol editor (lihat [SmartSnapSettings] & bagian "Smart Snap" di
 * ControlPanel pada TemplateEditorScreen).
 */
enum class SnapType {
    CENTER,   // tengah bingkai (garis 50%)
    THIRDS,   // garis rule-of-thirds (33% & 67%)
    QUARTERS, // garis seperempat (25%, 50%, 75%)
    EDGES     // tepi bingkai (0% & 100%)
}

/** 1 garis target snap: posisinya (rasio 0.0-1.0 relatif bingkai) + kategorinya. */
data class SnapTarget(val ratio: Float, val type: SnapType)

/**
 * Toggle per-jenis snap. Semua default aktif; user bisa matikan satu-satu lewat
 * switch di panel kontrol.
 */
data class SmartSnapSettings(
    val centerEnabled: Boolean = true,
    val thirdsEnabled: Boolean = true,
    val quartersEnabled: Boolean = true,
    val edgesEnabled: Boolean = true
) {
    val anyEnabled: Boolean get() = centerEnabled || thirdsEnabled || quartersEnabled || edgesEnabled
}

/**
 * Guide line yang lagi aktif ditampilkan di atas workspace saat drag/resize lagi
 * nge-snap ke suatu garis. `vertical` = garis tegak (posisi di sumbu X) yang lagi
 * kena snap, `horizontal` = garis mendatar (posisi di sumbu Y). Sumbu X & Y dipetakan
 * terpisah karena lebar & tinggi kontainer bisa beda (rasio yang sama di X belum
 * tentu align dengan rasio yang sama di Y secara visual, makanya masing-masing
 * dihitung & ditampilkan independen).
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
 * SlotEditorBox buat cari garis target (Center/Thirds/Quarters/Edges) terdekat dalam
 * jarak [THRESHOLD_DP], lalu ngembaliin posisi yang udah "ditarik" pas ke garis itu.
 *
 * Threshold-nya RASIONAL: didefinisikan dalam dp lalu dikonversi ke rasio berdasarkan
 * ukuran kontainer AKTUAL saat itu (bukan angka rasio tetap yang di-hardcode), supaya
 * "jarak kepekaan" snap kerasa konsisten secara visual di berbagai ukuran bingkai /
 * layar — bukannya makin gampang nyantol di bingkai kecil & makin susah di bingkai besar.
 */
object SmartSnap {

    /** Jarak toleransi snap, dalam dp — dikonversi ke px oleh caller lewat LocalDensity. */
    const val THRESHOLD_DP = 10f

    /** Bangun daftar garis target sesuai toggle yang aktif di [settings]. */
    fun buildTargets(settings: SmartSnapSettings): List<SnapTarget> {
        if (!settings.anyEnabled) return emptyList()
        // LinkedHashMap: kalau ada rasio yang sama persis dari 2 kategori (mis. quarter
        // 0.5 vs center 0.5), entri yang lebih dulu dimasukkan yang menang -> Center
        // diprioritaskan di atas Quarters karena lebih "penting" secara visual.
        val targets = LinkedHashMap<Float, SnapType>()
        if (settings.centerEnabled) targets.putIfAbsent(0.5f, SnapType.CENTER)
        if (settings.edgesEnabled) {
            targets.putIfAbsent(0f, SnapType.EDGES)
            targets.putIfAbsent(1f, SnapType.EDGES)
        }
        if (settings.thirdsEnabled) {
            targets.putIfAbsent(1f / 3f, SnapType.THIRDS)
            targets.putIfAbsent(2f / 3f, SnapType.THIRDS)
        }
        if (settings.quartersEnabled) {
            targets.putIfAbsent(0.25f, SnapType.QUARTERS)
            targets.putIfAbsent(0.5f, SnapType.QUARTERS)
            targets.putIfAbsent(0.75f, SnapType.QUARTERS)
        }
        return targets.map { (ratio, type) -> SnapTarget(ratio, type) }
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

    /** Warna garis panduan per jenis snap, biar user gampang bedain jenisnya sekilas. */
    fun colorFor(type: SnapType): Color = when (type) {
        SnapType.CENTER -> Color(0xFFFF4081)
        SnapType.THIRDS -> Color(0xFF40C4FF)
        SnapType.QUARTERS -> Color(0xFFFFEB3B)
        SnapType.EDGES -> Color(0xFF69F0AE)
    }

    /** Label singkat per jenis snap, dipakai buat switch toggle di panel kontrol. */
    fun labelFor(type: SnapType): String = when (type) {
        SnapType.CENTER -> "Center"
        SnapType.THIRDS -> "Thirds"
        SnapType.QUARTERS -> "Quarters"
        SnapType.EDGES -> "Edges"
    }
}