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

// Aggregates checks from the app module (lintKotlin/formatKotlin)
tasks.register("check-all") {
    description = "Run all checks including linting and code formatting"
    group = "verification"
    dependsOn(":app:lintKotlin", ":app:formatKotlin")
}
