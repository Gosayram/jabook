// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.kotlin.allopen) apply false
}

// Apply common configurations to all modules
allprojects {
    repositories {
        google()
        mavenCentral()
        // For libtorrent4j
        maven { url = uri("https://jitpack.io") }
    }
}

// Configure build optimizations
tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}