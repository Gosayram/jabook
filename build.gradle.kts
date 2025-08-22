// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.detekt) apply false
}

// Registers a clean task for the whole project
tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

// Aggregates checks from the app module (detekt/lint/tests/coverage)
tasks.register("check-all") {
    description = "Run all checks including linting, static analysis, and tests"
    group = "verification"
    dependsOn(":app:check-all", ":app:lint")
}

// Builds a debug APK and copies it into bin/ with timestamped name
tasks.register("buildApk") {
    dependsOn(":app:assembleDebug")
    doLast {
        val apkFile = file("app/build/outputs/apk/debug/app-debug.apk")
        val binDir = file("bin")
        val timestamp = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val outputFile = file("bin/JaBook_${timestamp}.apk")

        if (apkFile.exists()) {
            copy {
                from(apkFile)
                into(binDir)
                rename { "JaBook_${timestamp}.apk" }
            }
            println("✅ APK successfully built: ${outputFile.absolutePath}")
        } else {
            throw GradleException("APK file not found: ${apkFile.absolutePath}")
        }
    }
}

// Builds a release APK and copies it into bin/ with timestamped name
tasks.register("buildReleaseApk") {
    dependsOn(":app:assembleRelease")
    doLast {
        val apkFile = file("app/build/outputs/apk/release/app-release.apk")
        val binDir = file("bin")
        val timestamp = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val outputFile = file("bin/JaBook_Release_${timestamp}.apk")

        if (apkFile.exists()) {
            copy {
                from(apkFile)
                into(binDir)
                rename { "JaBook_Release_${timestamp}.apk" }
            }
            println("✅ Release APK successfully built: ${outputFile.absolutePath}")
        } else {
            throw GradleException("Release APK file not found: ${apkFile.absolutePath}")
        }
    }
}

// Cleans the bin/ folder with generated APKs
tasks.register("cleanBin") {
    doLast {
        val binDir = file("bin")
        if (binDir.exists()) {
            binDir.deleteRecursively()
            println("🗑️ bin/ folder cleaned")
        }
    }
}