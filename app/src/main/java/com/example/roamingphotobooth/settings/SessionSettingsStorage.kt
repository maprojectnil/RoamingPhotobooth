package com.example.roamingphotobooth.settings

import android.content.Context
import com.example.roamingphotobooth.nav.BoothMode

/**
 * Baca/simpan [SessionSettings] ke SharedPreferences. Pola sama persis dengan
 * [AppearanceStorage] (SharedPreferences terpisah, key per-field) supaya
 * konsisten dengan storage settings yang sudah ada.
 */
class SessionSettingsStorage(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("session_settings", Context.MODE_PRIVATE)

    fun load(): SessionSettings {
        val default = SessionSettings()
        val rawCountdown = prefs.getInt(KEY_COUNTDOWN_SECONDS, default.countdownSeconds)
        // <-- BARU: boothMode disimpan sebagai nama enum (String). Kalau value-nya
        // rusak/tidak dikenal (mis. dari versi lama sebelum fitur ini ada), jatuh
        // balik ke default (BoothMode.STAND) lewat runCatching supaya tidak crash.
        val rawBoothMode = prefs.getString(KEY_BOOTH_MODE, default.boothMode.name)
        val boothMode = rawBoothMode
            ?.let { name -> runCatching { BoothMode.valueOf(name) }.getOrNull() }
            ?: default.boothMode
        return SessionSettings(
            boothMode = boothMode,
            countdownSeconds = rawCountdown.coerceIn(
                SessionSettings.COUNTDOWN_RANGE.first,
                SessionSettings.COUNTDOWN_RANGE.last
            ),
            mirrorCamera = prefs.getBoolean(KEY_MIRROR_CAMERA, default.mirrorCamera),
            autoCountdownNextSlots = prefs.getBoolean(KEY_AUTO_COUNTDOWN, default.autoCountdownNextSlots),
            driveFolderId = prefs.getString(KEY_DRIVE_FOLDER_ID, default.driveFolderId) ?: default.driveFolderId
        )
    }

    fun save(settings: SessionSettings) {
        prefs.edit()
            .putString(KEY_BOOTH_MODE, settings.boothMode.name)
            .putInt(KEY_COUNTDOWN_SECONDS, settings.countdownSeconds)
            .putBoolean(KEY_MIRROR_CAMERA, settings.mirrorCamera)
            .putBoolean(KEY_AUTO_COUNTDOWN, settings.autoCountdownNextSlots)
            // trim() supaya spasi nyasar (awal/akhir, umum kalau copy-paste ID folder
            // dari URL Drive) tidak bikin ID folder ketolak/salah kirim ke API.
            .putString(KEY_DRIVE_FOLDER_ID, settings.driveFolderId.trim())
            .apply()
    }

    companion object {
        private const val KEY_BOOTH_MODE = "booth_mode"
        private const val KEY_COUNTDOWN_SECONDS = "countdown_seconds"
        private const val KEY_MIRROR_CAMERA = "mirror_camera"
        private const val KEY_AUTO_COUNTDOWN = "auto_countdown_next_slots"
        private const val KEY_DRIVE_FOLDER_ID = "drive_folder_id"
    }
}
