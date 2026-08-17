// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.3.1" apply false
    id("com.android.library") version "8.3.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}

// Common dependencies
val kotlinVersion = "1.9.22"
val androidGradlePluginVersion = "8.3.1"

task clean(type: Delete) {
    delete rootProject.buildDir
}

// Add repositories for all modules
allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url = "https://jitpack.io" }
    }
}