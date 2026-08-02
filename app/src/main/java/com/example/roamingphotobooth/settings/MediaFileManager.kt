package com.example.roamingphotobooth.settings

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File
import java.util.UUID

/**
 * Copy gambar/video background yang dipilih user dari galeri ke internal
 * storage app — sama seperti [com.example.roamingphotobooth.template.FrameFileManager]
 * tapi untuk aset Appearance (background Home / Mode Select), yang bisa berupa
 * gambar ATAU video (bukan cuma PNG bingkai).
 */
class MediaFileManager(private val context: Context) {

    private val backgroundsDir: File
        get() = File(context.filesDir, "backgrounds").apply { if (!exists()) mkdirs() }

    /**
     * @return path absolut file hasil copy, atau null kalau gagal.
     */
    fun importFromUri(uri: Uri): String? {
        return try {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri)
            val extension = MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(mimeType)
                ?: uri.lastPathSegment?.substringAfterLast('.', "")
                ?: "dat"

            val fileName = "bg_${UUID.randomUUID()}.$extension"
            val destFile = File(backgroundsDir, fileName)

            resolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null

            destFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun isVideoMime(uri: Uri): Boolean {
        val mimeType = context.contentResolver.getType(uri) ?: return false
        return mimeType.startsWith("video/")
    }

    fun deleteMediaFile(path: String?) {
        if (path.isNullOrBlank()) return
        try {
            File(path).delete()
        } catch (_: Exception) {
        }
    }
}
