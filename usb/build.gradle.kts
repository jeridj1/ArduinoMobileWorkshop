plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.arduinomobileworkshop.usb"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    // Standard USB-serial drivers for CH340, CP2102, FTDI, CDC/ACM (Arduino), etc.
    // Pulled from JitPack (declared in settings.gradle.kts dependencyResolutionManagement).
    implementation("com.github.mik3y:usb-serial-for-android:3.8.0")
}
