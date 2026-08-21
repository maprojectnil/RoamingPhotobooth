package com.example.roamingphotobooth.gallery

import android.content.Context
import android.util.Log
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "GalleryRepository"

/**
 * Simpan/baca riwayat sesi foto SELESAI (buat fitur Galeri) sebagai 1 daftar
 * JSON di SharedPreferences -- pola simpel yang sama seperti storage settings
 * lain di app ini (mis. [com.example.roamingphotobooth.settings.SessionSettingsStorage]),
 * cukup buat ratusan entri teks pendek seperti ini (bukan data besar/biner --
 * bitmap-nya sendiri TIDAK disimpan di sini, cuma referensi mediaUri-nya,
 * lihat [GallerySessionEntry]).
 *
 * Entri baru selalu ditambahkan di URUTAN PALING DEPAN (terbaru dulu, lihat
 * [addEntry]) supaya [getAll] langsung bisa dipakai apa adanya oleh GalleryScreen
 * tanpa perlu sort ulang. Dibatasi [MAX_ENTRIES] entri terbaru supaya
 * SharedPreferences tidak membengkak tanpa batas seiring waktu (venue yang
 * dipakai berbulan-bulan bisa hasilkan ribuan sesi) -- entri paling lama
 * otomatis dibuang begitu batas terlampaui.
 */
class GalleryRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("gallery_history", Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }

    fun addEntry(entry: GallerySessionEntry) {
        val updated = listOf(entry) + getAll()
        save(updated.take(MAX_ENTRIES))
    }

    fun getAll(): List<GallerySessionEntry> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<GallerySessionEntry>>(raw)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal baca riwayat galeri (data korup?): ${e.message}")
            emptyList()
        }
    }

    private fun save(entries: List<GallerySessionEntry>) {
        prefs.edit()
            .putString(KEY_ENTRIES, json.encodeToString(entries))
            .apply()
    }

    companion object {
        private const val KEY_ENTRIES = "entries"
        private const val MAX_ENTRIES = 300
    }
}
