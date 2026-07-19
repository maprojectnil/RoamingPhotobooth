package com.example.roamingphotobooth.template

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Menyimpan & membaca daftar PhotoTemplate ke/dari file JSON di internal storage app.
 */
class TemplateStorage(private val context: Context) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val templatesDir: File
        get() = File(context.filesDir, "templates").apply { if (!exists()) mkdirs() }

    private fun templateFile(id: String): File = File(templatesDir, "$id.json")

    fun saveTemplate(template: PhotoTemplate) {
        val file = templateFile(template.id)
        file.writeText(json.encodeToString(template))
    }

    fun loadTemplate(id: String): PhotoTemplate? {
        val file = templateFile(id)
        if (!file.exists()) return null
        return try {
            json.decodeFromString(PhotoTemplate.serializer(), file.readText())
        } catch (e: Exception) {
            null
        }
    }

    fun loadAllTemplates(): List<PhotoTemplate> {
        val dir = templatesDir
        return dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    json.decodeFromString(PhotoTemplate.serializer(), file.readText())
                } catch (e: Exception) {
                    null
                }
            }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
    }

    fun deleteTemplate(id: String) {
        templateFile(id).delete()
    }
}