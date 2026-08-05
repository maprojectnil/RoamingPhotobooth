plugins {
    id("com.android.library")
}

// Module ini adalah vendor lokal dari library es-ptp-camera
// (https://github.com/ReemMousaES/es-ptp-camera, v1.0.3) dengan 1 patch:
// menambahkan `options.inMutable = true` di jalur decode live view Canon EOS
// & Nikon supaya mekanisme reuse bitmap (double-buffering) yang library ini
// sendiri coba lakukan benar-benar berfungsi (sebelumnya selalu gagal reuse
// karena bitmap hasil decode secara default immutable -> log warning "Unable to reuse an
// immutable bitmap..." + alokasi baru tiap frame walau niatnya reuse).
//
// Sengaja divendor sebagai module lokal (bukan lewat JitPack) supaya build
// TIDAK bergantung pada repo GitHub eksternal -- penting untuk app kiosk yang
// build-nya harus bisa diulang kapan saja tanpa tergantung availability repo
// orang lain. Lihat komentar "PATCH" di source untuk lokasi persis perubahan.
android {
    namespace = "com.extremesolution.esptpcamera"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Hanya ContextCompat yang dipakai (PtpUsbService.java) — pakai artifact
    // Java "core" biasa, bukan "core-ktx" (module ini tidak ada kode Kotlin).
    implementation("androidx.core:core:1.16.0")
}
