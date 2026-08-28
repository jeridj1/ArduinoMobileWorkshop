plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.arduinomobileworkshop.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.arduinomobileworkshop.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    val releaseKeystoreFile = System.getenv("AMW_KEYSTORE_FILE")
    signingConfigs {
        if (releaseKeystoreFile != null) {
            create("release") {
                storeFile = file(releaseKeystoreFile)
                storePassword = System.getenv("AMW_KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("AMW_KEY_ALIAS") ?: ""
                keyPassword = System.getenv("AMW_KEY_PASSWORD") ?: ""
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            // Sign the release APK with the auto-generated debug keystore by
            // default so CI produces an installable artifact. Set the
            // AMW_KEYSTORE_FILE / AMW_KEYSTORE_PASSWORD / AMW_KEY_ALIAS /
            // AMW_KEY_PASSWORD env vars to sign with a production key.
            signingConfig = if (releaseKeystoreFile != null)
                signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug { packaging.jniLibs.keepDebugSymbols.add("**/libarduino-cli.so") }
    }
    packaging.jniLibs.useLegacyPackaging = true
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { viewBinding = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.gridlayout:gridlayout:1.0.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation(project(":toolchain"))
    implementation(project(":workspace"))
    implementation(project(":usb"))
    implementation(project(":rp2040"))
    testImplementation("junit:junit:4.13.2")
}
