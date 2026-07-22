plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10"
}

android {
    namespace = "com.example.roamingphotobooth"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.roamingphotobooth"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String", "DRIVE_FOLDER_ID",
            "\"${project.findProperty("DRIVE_FOLDER_ID") ?: ""}\""
        )
        buildConfigField(
            "String", "DRIVE_OAUTH_CLIENT_ID",
            "\"${project.findProperty("DRIVE_OAUTH_CLIENT_ID") ?: ""}\""
        )
        buildConfigField(
            "String", "DRIVE_OAUTH_CLIENT_SECRET",
            "\"${project.findProperty("DRIVE_OAUTH_CLIENT_SECRET") ?: ""}\""
        )
        buildConfigField(
            "String", "DRIVE_OAUTH_REFRESH_TOKEN",
            "\"${project.findProperty("DRIVE_OAUTH_REFRESH_TOKEN") ?: ""}\""
        )
        buildConfigField(
            "String", "FIRESTORE_PROJECT_ID",
            "\"${project.findProperty("FIRESTORE_PROJECT_ID") ?: ""}\""
        )
        buildConfigField(
            "String", "FIREBASE_WEB_API_KEY",
            "\"${project.findProperty("FIREBASE_WEB_API_KEY") ?: ""}\""
        )
        buildConfigField(
            // Base URL landing page di Firebase Hosting, TANPA trailing slash.
            // Contoh: https://roaming-photobooth-xxxxx.web.app
            "String", "LANDING_BASE_URL",
            "\"${project.findProperty("LANDING_BASE_URL") ?: ""}\""
        )
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.benchmark.traceprocessor)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}