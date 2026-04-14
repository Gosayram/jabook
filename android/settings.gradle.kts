pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

plugins {
    // REMOVED: Flutter plugin loader - no longer needed
    // id("dev.flutter.flutter-plugin-loader") version "1.0.0"
    id("com.android.application") version "9.1.1" apply false
    id("com.google.devtools.ksp") version "2.3.6" apply false
    id("com.google.dagger.hilt.android") version "2.59.2" apply false
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0" apply false
    id("dev.detekt") version "2.0.0-alpha.2" apply false
    // Kotlinx serialization for type-safe navigation
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.20" apply false
    // Compose Compiler (required for Kotlin 2.0+)
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
    // Google services Gradle plugin for Firebase
    id("com.google.gms.google-services") version "4.4.4" apply false
    // Firebase Crashlytics Gradle plugin
    id("com.google.firebase.crashlytics") version "3.0.7" apply false
}

include(":app")
