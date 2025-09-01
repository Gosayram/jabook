// Root Gradle build script used only for utility tasks in a Flutter project.
// The tasks are written to be compatible with Gradle Configuration Cache.

import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Delete

val verificationGroup = "verification"
val buildGroup = "build"
val flutterGroup = "flutter"

// --- Clean tasks ---

tasks.register<Delete>("cleanAll") {
    description = "Clean root and Flutter build artifacts"
    group = buildGroup
    delete(
        rootProject.layout.buildDirectory,
        "build",
        ".dart_tool",
        "jabook_android",
    )
}

tasks.register<Delete>("cleanFlutter") {
    description = "Clean Flutter-specific build artifacts"
    group = buildGroup
    delete(
        "build",
        ".dart_tool",
        "android/app/build",
        "android/build",
    )
}

// Alias so `./gradlew clean` behaves as usual and is not ambiguous
tasks.register("clean") {
    description = "Alias for cleanAll"
    group = buildGroup
    dependsOn("cleanAll")
}

// --- Flutter tasks (Exec) ---

tasks.register<Exec>("flutterAnalyze") {
    description = "Run `flutter analyze` on the project"
    group = flutterGroup
    // Do not use doLast/exec inside actions; set commandLine at configuration time
    commandLine("flutter", "analyze")
    // If you need environment overrides, configure them via environment["KEY"] = "value"
}

tasks.register<Exec>("flutterBuildApk") {
    description = "Build a debug APK via Flutter"
    group = buildGroup
    commandLine("flutter", "build", "apk", "--debug")
}

tasks.register<Exec>("flutterBuildAppbundle") {
    description = "Build a release AAB via Flutter"
    group = buildGroup
    commandLine("flutter", "build", "appbundle", "--release")
}

// --- Android tasks (Exec inside android/ dir) ---

fun gradlewName(): String =
    if (System.getProperty("os.name").lowercase().contains("win")) "gradlew.bat" else "./gradlew"

tasks.register<Exec>("androidAssembleDebug") {
    description = "Assemble Android debug via android/gradle"
    group = buildGroup
    workingDir = file("android")
    commandLine(gradlewName(), "assembleDebug")
}

tasks.register<Exec>("androidAssembleRelease") {
    description = "Assemble Android release via android/gradle"
    group = buildGroup
    workingDir = file("android")
    commandLine(gradlewName(), "assembleRelease")
}

tasks.register<Exec>("androidLint") {
    description = "Run Android lint via android/gradle"
    group = verificationGroup
    workingDir = file("android")
    commandLine(gradlewName(), "lint")
}

tasks.register<Exec>("androidTest") {
    description = "Run Android unit tests via android/gradle"
    group = verificationGroup
    workingDir = file("android")
    commandLine(gradlewName(), "testDebugUnitTest")
}

// --- Composite checks ---

tasks.register("devCheck") {
    description = "Quick development checks (Flutter analyze + Android lint)"
    group = verificationGroup
    dependsOn("flutterAnalyze", "androidLint")
}