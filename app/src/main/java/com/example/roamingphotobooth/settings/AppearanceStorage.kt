package com.example.roamingphotobooth.settings

import android.content.Context

/**
 * Baca/simpan [AppearanceSettings] ke SharedPreferences. Cuma menyimpan
 * path/flag/warna/teks (data ringan) — file gambar/video sendiri sudah
 * di-copy & dikelola terpisah lewat [MediaFileManager].
 */
class AppearanceStorage(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("appearance_settings", Context.MODE_PRIVATE)

    fun load(): AppearanceSettings {
        val default = AppearanceSettings()
        return AppearanceSettings(
            homeBackground = BackgroundSetting(
                path = prefs.getString(KEY_HOME_BG_PATH, null),
                isVideo = prefs.getBoolean(KEY_HOME_BG_IS_VIDEO, false)
            ),
            modeSelectBackground = BackgroundSetting(
                path = prefs.getString(KEY_MODE_BG_PATH, null),
                isVideo = prefs.getBoolean(KEY_MODE_BG_IS_VIDEO, false)
            ),
            buttonColorArgb = prefs.getInt(KEY_BUTTON_COLOR, default.buttonColorArgb),
            accentColorArgb = prefs.getInt(KEY_ACCENT_COLOR, default.accentColorArgb),
            startButtonText = prefs.getString(KEY_START_TEXT, default.startButtonText) ?: default.startButtonText,
            mobileButtonText = prefs.getString(KEY_MOBILE_TEXT, default.mobileButtonText) ?: default.mobileButtonText,
            standButtonText = prefs.getString(KEY_STAND_TEXT, default.standButtonText) ?: default.standButtonText
        )
    }

    fun save(settings: AppearanceSettings) {
        prefs.edit()
            .putString(KEY_HOME_BG_PATH, settings.homeBackground.path)
            .putBoolean(KEY_HOME_BG_IS_VIDEO, settings.homeBackground.isVideo)
            .putString(KEY_MODE_BG_PATH, settings.modeSelectBackground.path)
            .putBoolean(KEY_MODE_BG_IS_VIDEO, settings.modeSelectBackground.isVideo)
            .putInt(KEY_BUTTON_COLOR, settings.buttonColorArgb)
            .putInt(KEY_ACCENT_COLOR, settings.accentColorArgb)
            .putString(KEY_START_TEXT, settings.startButtonText)
            .putString(KEY_MOBILE_TEXT, settings.mobileButtonText)
            .putString(KEY_STAND_TEXT, settings.standButtonText)
            .apply()
    }

    companion object {
        private const val KEY_HOME_BG_PATH = "home_bg_path"
        private const val KEY_HOME_BG_IS_VIDEO = "home_bg_is_video"
        private const val KEY_MODE_BG_PATH = "mode_bg_path"
        private const val KEY_MODE_BG_IS_VIDEO = "mode_bg_is_video"
        private const val KEY_BUTTON_COLOR = "button_color"
        private const val KEY_ACCENT_COLOR = "accent_color"
        private const val KEY_START_TEXT = "start_button_text"
        private const val KEY_MOBILE_TEXT = "mobile_button_text"
        private const val KEY_STAND_TEXT = "stand_button_text"
    }
}
