// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    id("com.android.application") version "8.6.0" apply false
    id("com.android.library") version "8.6.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.0" apply false
}

allprojects {
    // Repositories are declared in settings.gradle.kts.
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
