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
            // Tambah slot baru dengan posisi default (ditumpuk sedikit offset biar kelihatan beda)
            for (i in current until count) {
                slots.add(
                    PhotoSlot(
                        id = "slot_${i + 1}",
                        order = i + 1,
                        xRatio = 0.1f + (i * 0.05f),
                        yRatio = 0.1f + (i * 0.05f),
                        widthRatio = 0.3f,
                        heightRatio = 0.3f
                    )
                )
            }
        } else if (count < current) {
            while (slots.size > count) {
                slots.removeAt(slots.size - 1)
            }
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