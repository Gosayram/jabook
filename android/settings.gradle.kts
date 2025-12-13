pluginManagement {
    val flutterSdkPath =
        run {
            val properties = java.util.Properties()
            file("local.properties").inputStream().use { properties.load(it) }
            val flutterSdkPath = properties.getProperty("flutter.sdk")
            require(flutterSdkPath != null) { "flutter.sdk not set in local.properties" }
            flutterSdkPath
        }

    includeBuild("$flutterSdkPath/packages/flutter_tools/gradle")

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
    id("dev.flutter.flutter-plugin-loader") version "1.0.0"
    id("com.android.application") version "8.13.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("com.google.devtools.ksp") version "2.3.3" apply false
    id("com.google.dagger.hilt.android") version "2.57.2" apply false
    id("org.jlleitschuh.gradle.ktlint") version "14.0.1" apply false
    // Kotlinx serialization for type-safe navigation
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21" apply false
    // Compose Compiler (required for Kotlin 2.0+)
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
}

include(":app")
