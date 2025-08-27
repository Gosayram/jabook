pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "jabook"
include(":app")
include(":core-net")
include(":core-endpoints")
include(":core-auth")
include(":core-parse")
include(":core-torrent")
include(":core-stream")
include(":core-player")
include(":core-logging")