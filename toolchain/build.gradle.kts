plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.arduinomobileworkshop.toolchain"
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
    // HTTP client used to fetch the real Arduino package / library indexes and
    // to download target board package profiles into sandboxed app storage.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
