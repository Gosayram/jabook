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
include(
    ":app",
    ":core-net",
    ":core-endpoints",
    ":core-auth",
    ":core-parse",
    ":core-torrent",
    ":core-stream",
    ":core-player",
    ":core-logging"
)