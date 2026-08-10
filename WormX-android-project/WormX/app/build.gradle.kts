plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.wormx.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wormx.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-service:2.8.1")

    // Networking — used for resumable (Range-request) downloads
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Background/foreground persistent work
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Vault: encryption + biometric auth (same pattern as the File Safe app)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.biometric:biometric:1.1.0")

    // Ads
    implementation("com.google.android.gms:play-services-ads:23.2.0")

    // Media (in-app playback of downloaded / vault video)
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")

    // MP4 -> MP3 extraction / video re-encoding
    implementation("com.arthenica:ffmpeg-kit-audio:6.0-2")
}
