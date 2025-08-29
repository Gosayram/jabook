// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.kotlin.allopen) apply false
    alias(libs.plugins.spotless) apply false
}

// Apply common configurations to all modules
allprojects {
    // Common configurations can be added here if needed
}

// Configure build optimizations
tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

// Configure Spotless for code formatting
subprojects {
    plugins.withId("com.diffplug.spotless") {
        configure<com.diffplug.gradle.spotless.SpotlessExtension> {

            // ---------- Kotlin source files ----------
            kotlin {
                target("src/**/*.kt")
                targetExclude("**/build/**", "**/generated/**")

                ktlint(libs.versions.spotlessKtlint.get())

                licenseHeaderFile(
                    rootProject.file("spotless/copyright.kt"),
                    "(package|@file|import)"
                )

                trimTrailingWhitespace()
                endWithNewline()
            }

            // ---------- Kotlin Gradle scripts (*.gradle.kts) ----------
            kotlinGradle {
                target("**/*.gradle.kts")

                ktlint(libs.versions.spotlessKtlint.get())

                licenseHeaderFile(
                    rootProject.file("spotless/copyright.kt"),
                    "(plugins|import)"
                )

                trimTrailingWhitespace()
                endWithNewline()
            }

            java {
                target("src/**/*.java")

                googleJavaFormat(libs.versions.spotlessGoogleJavaFormat.get()).reflowLongStrings()

                licenseHeaderFile(rootProject.file("spotless/copyright.kt"))

                trimTrailingWhitespace()
                endWithNewline()
            }
        }
    }
}

// Create check-all task that runs all formatting and quality checks
tasks.register("check-all") {
    group = "verification"
    description = "Run all code quality checks and formatting"
    
    dependsOn(
        ":app:check",
        ":core-auth:check",
        ":core-endpoints:check",
        ":core-logging:check",
        ":core-net:check",
        ":core-parse:check",
        ":core-player:check",
        ":core-stream:check",
        ":core-torrent:check",
        "spotlessCheck"
    )
}