package com.example.roamingphotobooth.settings

import android.content.Context

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
        return SessionSettings(
            countdownSeconds = rawCountdown.coerceIn(
                SessionSettings.COUNTDOWN_RANGE.first,
                SessionSettings.COUNTDOWN_RANGE.last
            ),
            mirrorCamera = prefs.getBoolean(KEY_MIRROR_CAMERA, default.mirrorCamera),
            autoCountdownNextSlots = prefs.getBoolean(KEY_AUTO_COUNTDOWN, default.autoCountdownNextSlots)
        )
    }

    fun save(settings: SessionSettings) {
        prefs.edit()
            .putInt(KEY_COUNTDOWN_SECONDS, settings.countdownSeconds)
            .putBoolean(KEY_MIRROR_CAMERA, settings.mirrorCamera)
            .putBoolean(KEY_AUTO_COUNTDOWN, settings.autoCountdownNextSlots)
            .apply()
    }

    companion object {
        private const val KEY_COUNTDOWN_SECONDS = "countdown_seconds"
        private const val KEY_MIRROR_CAMERA = "mirror_camera"
        private const val KEY_AUTO_COUNTDOWN = "auto_countdown_next_slots"
    }
}
