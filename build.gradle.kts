// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.9.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0" apply false
    id("com.google.devtools.ksp") version "2.2.0-2.0.2" apply false
    id("com.google.dagger.hilt.android") version "2.53.1" apply false
    id("org.jetbrains.kotlin.plugin.parcelize") version "2.2.0" apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.4" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

tasks.register("check-all") {
    description = "Run all checks including linting, static analysis, and tests"
    group = "verification"
    dependsOn(":app:check-all", ":app:lint")
}

tasks.register("buildApk") {
    dependsOn(":app:assembleDebug")
    doLast {
        val apkFile = file("app/build/outputs/apk/debug/app-debug.apk")
        val binDir = file("bin")
        val timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
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

tasks.register("buildReleaseApk") {
    dependsOn(":app:assembleRelease")
    doLast {
        val apkFile = file("app/build/outputs/apk/release/app-release.apk")
        val binDir = file("bin")
        val timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
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

tasks.register("cleanBin") {
    doLast {
        val binDir = file("bin")
        if (binDir.exists()) {
            binDir.deleteRecursively()
            println("🗑️ bin/ folder cleaned")
        }
    }
} 