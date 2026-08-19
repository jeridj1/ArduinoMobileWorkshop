pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            allowInsecureProtocol = true
        }
    }
}

rootProject.name = "ArduinoMobileWorkshop"

include(":app")
include(":toolchain")
include(":workspace")
include(":usb")
include(":rp2040")
