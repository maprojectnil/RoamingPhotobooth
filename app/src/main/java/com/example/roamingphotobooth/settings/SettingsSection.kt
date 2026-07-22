package com.example.roamingphotobooth.settings

/**
 * Bagian-bagian yang bisa dipilih lewat dropdown menu di kiri layar Settings
 * (lihat SettingsActivity). Urutan di sini = urutan tampil di dropdown.
 */
enum class SettingsSection(val label: String, val assetIconName: String) {
    FRAME_EDITOR("Frame Editor", "frameeditor.svg"),
    FRAME_LIST("Frame List", "framelist.svg"),
    APPEARANCE("Appearance", "appearance.svg"),
    // <-- BARU: cari & pilih Print Server (dulu di FinalResultScreen, sekarang di sini).
    PRINTER("Printer", "printer.svg")
}
