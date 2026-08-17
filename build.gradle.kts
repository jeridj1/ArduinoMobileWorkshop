// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.3.1" apply false
    id("com.android.library") version "8.3.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}

// Common dependencies
val kotlinVersion = "1.9.22"
val androidGradlePluginVersion = "8.3.1"
val coreKtxVersion = "1.12.0"
val appcompatVersion = "1.6.1"
val materialVersion = "1.11.0"
val constraintLayoutVersion = "2.1.4"
val navigationVersion = "2.7.7"
val lifecycleVersion = "2.7.0"
val activityKtxVersion = "1.8.2"
val fragmentKtxVersion = "1.7.0"
val preferenceKtxVersion = "1.2.1"
val coroutinesVersion = "1.7.3"
val usbSerialVersion = "1.2.0"
val filePickerVersion = "1.0.6"

task clean(type: Delete) {
    delete rootProject.buildDir
}