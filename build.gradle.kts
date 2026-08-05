// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Dipakai oleh module :es-ptp-camera-patched (vendor lokal es-ptp-camera).
    // Versi disamakan dengan agp yang dipakai :app (lihat libs.versions.toml).
    id("com.android.library") version "9.2.1" apply false
}