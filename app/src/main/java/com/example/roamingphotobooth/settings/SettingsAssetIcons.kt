package com.example.roamingphotobooth.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Load logo dropdown Settings dari assets/ (lihat [SettingsSection.assetIconName]).
 * Taruh file PNG-nya di app/src/main/assets/ dengan nama persis:
 *   - ic_frame_editor.png
 *   - ic_frame_list.png
 *   - ic_appearance.png
 * Kalau filenya belum ada / gagal dibaca, return null — pemanggil (SettingsNavDropdown)
 * akan otomatis fallback ke ikon Material bawaan supaya UI tidak pernah crash
 * gara-gara aset belum ditaruh.
 */
object SettingsAssetIcons {

    private val cache = mutableMapOf<String, Bitmap?>()

    fun load(context: Context, section: SettingsSection): Bitmap? {
        return cache.getOrPut(section.assetIconName) {
            try {
                context.assets.open(section.assetIconName).use { input ->
                    BitmapFactory.decodeStream(input)
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
