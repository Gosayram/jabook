// Top-level build file where you can add configuration options common to all sub-projects/modules.

import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

subprojects {
    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    
    // Configure test logging for better debugging
    tasks.withType<Test>().configureEach {
        testLogging {
            events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
            exceptionFormat = TestExceptionFormat.FULL
            showExceptions = true
            showCauses = true
            showStackTraces = true
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
}

// Task groups for better organization
val verificationGroup = "verification"
val buildGroup = "build"
val flutterGroup = "flutter"

// Registers a clean task for the whole project
tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
    // Also clean Flutter build directories
    delete("build")
    delete(".dart_tool")
    delete("jabook_android")
}

// Enhanced clean task for Flutter-specific cleanup
tasks.register("clean-flutter", Delete::class) {
    description = "Clean Flutter-specific directories and build artifacts"
    group = "build"
    delete("build")
    delete(".dart_tool")
    delete("jabook_android")
    delete("android/app/build")
    delete("android/build")
}

// Aggregates checks from the app module (lintKotlin/formatKotlin)
tasks.register("check-all") {
    description = "Run all checks including linting and code formatting"
    group = verificationGroup
    dependsOn(":app:lintKotlin", ":app:formatKotlin")
}

// Flutter tasks
tasks.register("flutter-analyze") {
    description = "Run Flutter analyze on the project"
    group = flutterGroup
    doLast {
        exec {
            commandLine("flutter", "analyze")
        }
    }
}

tasks.register("flutter-create-android") {
    description = "Create Flutter Android project with Kotlin and clean existing"
    group = buildGroup
    
    doFirst {
        // Clean existing Android directory if it exists
        val androidDir = file("android")
        if (androidDir.exists()) {
            delete(androidDir)
            println("Cleaned existing Android directory")
        }
    }
    
    doLast {
        exec {
            commandLine("flutter", "create", "jabook_android", "--org", "com.jabook.app", "--platforms=android", "-a", "kotlin")
        }
        // Move the created android directory to the project root
        val createdAndroidDir = file("jabook_android/android")
        if (createdAndroidDir.exists()) {
            copy {
                from(createdAndroidDir)
                into(file("android"))
            }
            delete(file("jabook_android"))
            println("Moved Android project to root and cleaned temporary directory")
        }
    }
}

tasks.register("check-flutter") {
    description = "Run all Flutter-related checks"
    group = verificationGroup
    dependsOn("flutter-analyze", "check-all")
}

// Build tasks
tasks.register("build-all") {
    description = "Build all modules and run checks"
    group = buildGroup
    dependsOn(":app:assembleDebug", "check-all")
}

// Development tasks
tasks.register("dev-check") {
    description = "Quick development checks"
    group = verificationGroup
    dependsOn("flutter-analyze", ":app:lintKotlin")
}
