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
        
        consumerProguardFiles("consumer-rules.pro")
        
        buildConfigField("String", "RP2040_VID", '"2E8A"')
        buildConfigField("String", "RP2040_PID", '"000A"')
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
    implementation(project(":usb"))
    implementation("com.hoho.android:usb-serial:1.2.0")
}