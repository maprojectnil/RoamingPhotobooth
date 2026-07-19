package com.example.roamingphotobooth.template

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

class TemplateEditorViewModel : ViewModel() {

    var templateName = mutableStateOf("")
    var framePath = mutableStateOf<String?>(null)
    var frameBitmap = mutableStateOf<Bitmap?>(null)
    var frameWidthPx = mutableStateOf(0)
    var frameHeightPx = mutableStateOf(0)

    val slots = mutableStateListOf<PhotoSlot>()

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