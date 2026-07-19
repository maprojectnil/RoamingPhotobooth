package com.example.roamingphotobooth.template

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * Mengelola file PNG bingkai — copy dari Uri picker ke internal storage,
 * supaya app punya salinan sendiri yang tidak bergantung pada file asli user.
 */
class FrameFileManager(private val context: Context) {

    private val framesDir: File
        get() = File(context.filesDir, "frames").apply { if (!exists()) mkdirs() }

    /**
     * Copy file dari Uri (hasil file picker) ke internal storage.
     * Return path absolut file hasil copy, atau null kalau gagal.
     */
    fun importFrameFromUri(uri: Uri): String? {
        return try {
            val fileName = "frame_${UUID.randomUUID()}.png"
            val destFile = File(framesDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            destFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Ambil ukuran asli (width, height) dari file PNG, tanpa load penuh ke memory
     * (pakai inJustDecodeBounds, efisien untuk file besar).
     */
    fun getImageDimensions(path: String): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        return options.outWidth to options.outHeight
    }

    fun loadBitmap(path: String): Bitmap? {
        return try {
            BitmapFactory.decodeFile(path)
        } catch (e: Exception) {
            null
        }
    }

    fun deleteFrameFile(path: String) {
        File(path).delete()
    }
}