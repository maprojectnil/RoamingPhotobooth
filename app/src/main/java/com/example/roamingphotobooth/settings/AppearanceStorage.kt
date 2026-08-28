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
            buttonColorArgb = prefs.getInt(KEY_BUTTON_COLOR, default.buttonColorArgb),
            accentColorArgb = prefs.getInt(KEY_ACCENT_COLOR, default.accentColorArgb),
            startButtonText = prefs.getString(KEY_START_TEXT, default.startButtonText) ?: default.startButtonText,
            startButtonIconColorArgb = prefs.getInt(KEY_START_ICON_COLOR, default.startButtonIconColorArgb),
            startButtonSizeDp = prefs.getFloat(KEY_START_BUTTON_SIZE, default.startButtonSizeDp),
            startButtonOffsetXDp = prefs.getFloat(KEY_START_BUTTON_OFFSET_X, default.startButtonOffsetXDp),
            startButtonOffsetYDp = prefs.getFloat(KEY_START_BUTTON_OFFSET_Y, default.startButtonOffsetYDp),
            useLiveViewAsHomeBackground = prefs.getBoolean(
                KEY_USE_LIVE_VIEW_HOME_BG,
                default.useLiveViewAsHomeBackground
            ),
            homeOverlayImagePath = prefs.getString(KEY_HOME_OVERLAY_PATH, null),
            showStatusText = prefs.getBoolean(KEY_SHOW_STATUS_TEXT, default.showStatusText)
        )
    }

    fun save(settings: AppearanceSettings) {
        prefs.edit()
            .putString(KEY_HOME_BG_PATH, settings.homeBackground.path)
            .putBoolean(KEY_HOME_BG_IS_VIDEO, settings.homeBackground.isVideo)
            .putInt(KEY_BUTTON_COLOR, settings.buttonColorArgb)
            .putInt(KEY_ACCENT_COLOR, settings.accentColorArgb)
            .putString(KEY_START_TEXT, settings.startButtonText)
            .putInt(KEY_START_ICON_COLOR, settings.startButtonIconColorArgb)
            .putFloat(KEY_START_BUTTON_SIZE, settings.startButtonSizeDp)
            .putFloat(KEY_START_BUTTON_OFFSET_X, settings.startButtonOffsetXDp)
            .putFloat(KEY_START_BUTTON_OFFSET_Y, settings.startButtonOffsetYDp)
            .putBoolean(KEY_USE_LIVE_VIEW_HOME_BG, settings.useLiveViewAsHomeBackground)
            .putString(KEY_HOME_OVERLAY_PATH, settings.homeOverlayImagePath)
            .putBoolean(KEY_SHOW_STATUS_TEXT, settings.showStatusText)
            .apply()
    }

    companion object {
        private const val KEY_HOME_BG_PATH = "home_bg_path"
        private const val KEY_HOME_BG_IS_VIDEO = "home_bg_is_video"
        private const val KEY_BUTTON_COLOR = "button_color"
        private const val KEY_ACCENT_COLOR = "accent_color"
        private const val KEY_START_TEXT = "start_button_text"
        private const val KEY_START_ICON_COLOR = "start_button_icon_color"
        private const val KEY_START_BUTTON_SIZE = "start_button_size_dp"
        private const val KEY_START_BUTTON_OFFSET_X = "start_button_offset_x_dp"
        private const val KEY_START_BUTTON_OFFSET_Y = "start_button_offset_y_dp"
        private const val KEY_USE_LIVE_VIEW_HOME_BG = "use_live_view_home_bg"
        private const val KEY_HOME_OVERLAY_PATH = "home_overlay_path"
        private const val KEY_SHOW_STATUS_TEXT = "show_status_text"
    }
}
