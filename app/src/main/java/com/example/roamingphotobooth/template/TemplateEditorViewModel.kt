package com.example.roamingphotobooth.template

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

/**
 * Hasil pemanggilan [TemplateEditorViewModel.autoDetectSlots] — dipakai layar editor
 * buat nampilin pesan yang sesuai (Toast) tanpa ViewModel perlu tahu soal Context/UI.
 */
sealed class AutoDetectResult {
    /** Berhasil, [slotCount] bolongan terdeteksi & jadi slot baru. */
    data class Success(val slotCount: Int) : AutoDetectResult()

    /** Belum ada bingkai PNG yang dipilih sama sekali. */
    object NoFrame : AutoDetectResult()

    /** Ada bingkai, tapi tidak ada bolongan transparan yang cukup besar ditemukan. */
    object NoHolesFound : AutoDetectResult()
}

class TemplateEditorViewModel : ViewModel() {

    var templateName = mutableStateOf("")
    var framePath = mutableStateOf<String?>(null)
    var frameBitmap = mutableStateOf<Bitmap?>(null)
    var frameWidthPx = mutableStateOf(0)
    var frameHeightPx = mutableStateOf(0)

    val slots = mutableStateListOf<PhotoSlot>()

    // Toggle Smart Snap (cuma Quarters, cuma sumbu X) — dipakai SlotEditorBox saat
    // drag/resize, diatur lewat switch di panel kontrol (ControlPanel).
    var snapSettings = mutableStateOf(SmartSnapSettings())

    fun setSnapEnabled(enabled: Boolean) {
        snapSettings.value = snapSettings.value.copy(enabled = enabled)
    }

    fun setFrame(path: String, bitmap: Bitmap, width: Int, height: Int) {
        framePath.value = path
        frameBitmap.value = bitmap
        frameWidthPx.value = width
        frameHeightPx.value = height
    }

    fun setSlotCount(count: Int) {
        val current = slots.size
        if (count > current) {
            // Order harus unik & lanjut dari yang terbesar sekarang — jangan pakai index
            // polos, karena slot hasil duplicateSlot() bisa bikin order yang sudah ada terpakai.
            var nextOrder = (slots.maxOfOrNull { it.order } ?: 0) + 1
            for (i in current until count) {
                slots.add(
                    PhotoSlot(
                        id = "slot_${System.currentTimeMillis()}_${i + 1}",
                        order = nextOrder,
                        // Ditumpuk sedikit offset (mengulang tiap 4 slot) biar kelihatan beda
                        xRatio = 0.08f + (i % 4) * 0.06f,
                        yRatio = 0.08f + (i % 4) * 0.06f,
                        widthRatio = 0.3f,
                        heightRatio = 0.3f
                    )
                )
                nextOrder++
            }
        } else if (count < current) {
            while (slots.size > count) {
                slots.removeAt(slots.size - 1)
            }
        }
    }

    /**
     * Duplikat slot di [index]: dipakai supaya 1 foto yang sama nanti dipasang di
     * beberapa slot sekaligus (misal foto strip yang diulang di 2 posisi bingkai).
     * Slot baru dibuat dengan `order` yang SAMA persis dengan slot sumber (lihat catatan
     * di PhotoSlot) tapi posisinya digeser sedikit supaya kelihatan & bisa diatur ulang
     * independen di editor.
     */
    fun duplicateSlot(index: Int) {
        if (index !in slots.indices) return
        val source = slots[index]
        val newX = (source.xRatio + 0.04f).coerceIn(0f, 1f - source.widthRatio)
        val newY = (source.yRatio + 0.04f).coerceIn(0f, 1f - source.heightRatio)
        slots.add(
            source.copy(
                id = "slot_${System.currentTimeMillis()}_${slots.size + 1}",
                xRatio = newX,
                yRatio = newY
            )
        )
    }

    fun removeSlotAt(index: Int) {
        if (index in slots.indices) {
            slots.removeAt(index)
        }
    }

    fun updateSlot(index: Int, updated: PhotoSlot) {
        if (index in slots.indices) {
            slots[index] = updated
        }
    }

    /**
     * REORDER: tukar `order` (nomor urut pengambilan foto) antara slot [indexA] dan
     * [indexB] — posisi & ukuran (bounding box) KEDUA slot SAMA SEKALI TIDAK berubah,
     * cuma nomor urut fotonya yang saling bertukar tempat.
     *
     * Contoh: urutan fisik slot di bingkai tetap 1→2→3→4→5→6, tapi kalau slot ke-3 &
     * ke-4 di-`swapSlotOrder`, urutan PENGAMBILAN FOTO jadi 1→2→4→3→5→6 — slot yang
     * tadinya nomor 3 sekarang minta foto ke-4, dan sebaliknya, tanpa slot-nya sendiri
     * pindah posisi di bingkai.
     */
    fun swapSlotOrder(indexA: Int, indexB: Int) {
        if (indexA !in slots.indices || indexB !in slots.indices || indexA == indexB) return
        val a = slots[indexA]
        val b = slots[indexB]
        slots[indexA] = a.copy(order = b.order)
        slots[indexB] = b.copy(order = a.order)
    }

    /**
     * DUPLICATE (tanpa bikin slot baru): jadikan slot di [index] "berbagi" foto dengan
     * slot [sourceIndex] — `order` slot [index] ditimpa dengan `order` milik
     * [sourceIndex], sehingga otomatis keisi foto yang SAMA saat sesi pemotretan
     * (lihat catatan di [PhotoSlot.order]). Posisi & ukuran slot [index] TIDAK
     * berubah — beda dengan [duplicateSlot] yang bikin kotak BARU, fungsi ini cuma
     * mengubah PEMETAAN foto pada slot yang SUDAH ADA.
     *
     * Contoh: "Slot 6 → Duplikat dari Slot 3" -> slot 6 tetap di posisi/bentuknya
     * semula, tapi sekarang otomatis terisi foto yang sama dengan slot 3.
     */
    fun setSlotSource(index: Int, sourceIndex: Int) {
        if (index !in slots.indices || sourceIndex !in slots.indices || index == sourceIndex) return
        val sourceOrder = slots[sourceIndex].order
        slots[index] = slots[index].copy(order = sourceOrder)
    }

    /**
     * Lepaskan slot di [index] dari status "berbagi foto" (kebalikan dari
     * [setSlotSource]) — dikasih `order` UNIK baru (lanjut dari order terbesar yang
     * ada sekarang), supaya slot ini kembali minta fotonya sendiri saat sesi
     * pemotretan. Posisi & ukuran slot TIDAK berubah.
     */
    fun makeSlotUnique(index: Int) {
        if (index !in slots.indices) return
        val nextOrder = (slots.maxOfOrNull { it.order } ?: 0) + 1
        slots[index] = slots[index].copy(order = nextOrder)
    }

    /**
     * Fitur "Deteksi Otomatis Slot": scan bingkai PNG yang sedang aktif buat cari
     * bolongan (area transparan) lewat [FrameSlotDetector], lalu MENIMPA seluruh
     * daftar `slots` sekarang dengan slot baru — 1 slot per bolongan yang ditemukan,
     * `order` diisi berurutan sesuai reading order (baris atas→bawah, kiri→kanan)
     * sebagai tebakan awal.
     *
     * Ini aksi DESTRUKTIF terhadap slot manual yang sudah ada — pemanggil (layar
     * editor) bertanggung jawab minta konfirmasi user dulu kalau `slots` sebelumnya
     * tidak kosong. Hasil deteksi (posisi, ukuran, & terutama urutan) cuma tebakan
     * awal — user tetap bebas geser/resize/ubah urutan/duplikat/hapus tiap slot
     * hasilnya persis seperti slot yang dibuat manual, karena bentuknya memang
     * [PhotoSlot] biasa, tidak ditandai spesial.
     */
    fun autoDetectSlots(): AutoDetectResult {
        val bitmap = frameBitmap.value ?: return AutoDetectResult.NoFrame
        val holes = FrameSlotDetector.detectHoles(bitmap)
        if (holes.isEmpty()) return AutoDetectResult.NoHolesFound

        slots.clear()
        holes.forEachIndexed { index, hole ->
            slots.add(
                PhotoSlot(
                    id = "slot_${System.currentTimeMillis()}_auto_${index + 1}",
                    order = index + 1,
                    xRatio = hole.xRatio,
                    yRatio = hole.yRatio,
                    widthRatio = hole.widthRatio,
                    heightRatio = hole.heightRatio
                )
            )
        }
        return AutoDetectResult.Success(holes.size)
    }

    fun buildTemplate(): PhotoTemplate? {
        val path = framePath.value ?: return null
        if (slots.isEmpty()) return null

        return PhotoTemplate(
            id = "template_${System.currentTimeMillis()}",
            name = templateName.value.ifBlank { "Template Tanpa Nama" },
            framePngPath = path,
            frameWidthPx = frameWidthPx.value,
            frameHeightPx = frameHeightPx.value,
            slots = slots.toList()
        )
    }

    fun reset() {
        templateName.value = ""
        framePath.value = null
        frameBitmap.value = null
        slots.clear()
    }
}