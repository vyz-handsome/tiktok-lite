plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Nomor build GitHub Actions dipakai sebagai versionCode. Tiap build otomatis lebih tinggi
// dari sebelumnya, jadi Android menerimanya sebagai "update" yang menimpa app lama.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.example.tiktoklike"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.tiktoklike"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.1.$buildNumber"
    }

    // Keystore tetap (di-commit di app/debug.keystore) -> tanda tangan APK selalu sama.
    // JANGAN hapus/ganti file ini, kalau tidak update tidak bisa lagi menimpa app lama.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
