plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.arduinomobileworkshop.rp2040"
    compileSdk = 34
    
    defaultConfig {
        minSdk = 24
        targetSdk = 34
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        
        // RP2040 specific configurations
        buildConfigField("String", "RP2040_VID", '"2E8A"')
        buildConfigField("String", "RP2040_PID", '"000A"')
        buildConfigField("int", "RP2040_UF2_MAGIC", '"0x0A324655"')
        buildConfigField("boolean", "ENABLE_LOGIC_ANALYZER", "true")
        buildConfigField("boolean", "ENABLE_MULTI_PROGRAMMER", "true")
    }
    
    buildTypes {
        release {
            minifyEnabled(false)
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation(project(":usb"))
    implementation(project(":toolchain"))
    
    // USB serial communication
    implementation("com.hoho.android:usb-serial:1.2.0")
    
    // For UF2 file handling
    implementation("commons-io:commons-io:2.15.1")
    
    // For logic analyzer data processing
    implementation("org.apache.commons:commons-math3:3.6.1")
    
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}