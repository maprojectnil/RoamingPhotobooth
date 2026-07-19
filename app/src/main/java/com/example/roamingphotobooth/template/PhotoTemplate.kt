package com.example.roamingphotobooth.template

import kotlinx.serialization.Serializable

/**
 * Merepresentasikan 1 slot foto di dalam template — posisi, ukuran, rotasi.
 * Koordinat dalam bentuk RASIO (0.0 - 1.0) relatif terhadap ukuran bingkai,
 * BUKAN pixel absolut — supaya template tetap valid walau bingkai di-render
 * di resolusi/ukuran layar berbeda.
 */
@Serializable
data class PhotoSlot(
    val id: String,           // unik per slot, misal "slot_1"
    val order: Int,           // urutan pengambilan foto (1, 2, 3, ...)
    val xRatio: Float,        // posisi X kiri-atas, 0.0 = paling kiri, 1.0 = paling kanan
    val yRatio: Float,        // posisi Y kiri-atas, 0.0 = paling atas, 1.0 = paling bawah
    val widthRatio: Float,    // lebar slot, relatif terhadap lebar bingkai
    val heightRatio: Float,   // tinggi slot, relatif terhadap tinggi bingkai
    val rotationDegrees: Float = 0f
)

/**
 * 1 template lengkap: bingkai PNG + semua slot foto di dalamnya.
 */
@Serializable
data class PhotoTemplate(
    val id: String,                // unik, misal UUID atau timestamp
    val name: String,               // nama template, misal "Wedding Frame 3 Slot"
    val framePngPath: String,       // path absolut file PNG bingkai di storage
    val frameWidthPx: Int,          // ukuran asli bingkai PNG (buat referensi rasio)
    val frameHeightPx: Int,
    val slots: List<PhotoSlot>,
    val createdAt: Long = System.currentTimeMillis()
) {
    val slotCount: Int get() = slots.size
}