// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    // Keep AGP single-sourced here to avoid classpath conflicts across modules
    id("com.android.application") version "8.9.0" apply false

    // Kotlin 2.2.0 across the project
    id("org.jetbrains.kotlin.android") version "2.2.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0" apply false
    id("org.jetbrains.kotlin.plugin.parcelize") version "2.2.0" apply false

    // KSP aligned with Kotlin 2.2.x
    id("com.google.devtools.ksp") version "2.2.0-2.0.2" apply false  //  [oai_citation:0‡GitHub](https://github.com/google/ksp/releases?utm_source=chatgpt.com)

    // Hilt Gradle plugin (update from 2.53.1 → 2.57)
    id("com.google.dagger.hilt.android") version "2.57" apply false   //  [oai_citation:1‡mvnrepository.com](https://mvnrepository.com/artifact/com.google.dagger/hilt-android-gradle-plugin?utm_source=chatgpt.com)

    // Ktlint Gradle plugin (latest stable)
    id("org.jlleitschuh.gradle.ktlint") version "13.0.0" apply false  //  [oai_citation:2‡plugins.gradle.org](https://plugins.gradle.org/plugin/org.jlleitschuh.gradle.ktlint?utm_source=chatgpt.com) [oai_citation:3‡mvnrepository.com](https://mvnrepository.com/artifact/org.jlleitschuh.gradle.ktlint/org.jlleitschuh.gradle.ktlint.gradle.plugin?utm_source=chatgpt.com)

    // Detekt plugin (update from 1.23.4 → 1.23.8)
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false    //  [oai_citation:4‡detekt.dev](https://detekt.dev/docs/intro?utm_source=chatgpt.com) [oai_citation:5‡GitHub](https://github.com/detekt/detekt/releases?utm_source=chatgpt.com)
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