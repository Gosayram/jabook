// Root Gradle build script used only for utility tasks in a Flutter project.

import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

val verificationGroup = "verification"
val buildGroup = "build"
val flutterGroup = "flutter"

// --- Clean tasks ---

tasks.register("cleanAll", Delete::class) {
    description = "Clean root and Flutter build artifacts"
    group = buildGroup
    delete(rootProject.layout.buildDirectory)
    delete("build", ".dart_tool", "jabook_android")
}

tasks.register("cleanFlutter", Delete::class) {
    description = "Clean Flutter-specific build artifacts"
    group = buildGroup
    delete("build", ".dart_tool", "android/app/build", "android/build")
}

// Alias so `./gradlew clean` works and is not ambiguous
tasks.register("clean") {
    description = "Alias for cleanAll"
    group = buildGroup
    dependsOn("cleanAll")
}

// --- Flutter tasks ---

tasks.register("flutterAnalyze") {
    description = "Run `flutter analyze` on the project"
    group = flutterGroup
    doLast { exec { commandLine("flutter", "analyze") } }
}

tasks.register("flutterBuildApk") {
    description = "Build a debug APK via Flutter"
    group = buildGroup
    doLast { exec { commandLine("flutter", "build", "apk", "--debug") } }
}

tasks.register("flutterBuildAppbundle") {
    description = "Build a release AAB via Flutter"
    group = buildGroup
    doLast { exec { commandLine("flutter", "build", "appbundle", "--release") } }
}

// --- Android tasks (invoke Gradle inside android/) ---

fun androidGradle(vararg args: String) = exec {
    workingDir = file("android")
    // On Windows use "gradlew.bat"
    commandLine("./gradlew", *args)
}

tasks.register("androidAssembleDebug") {
    description = "Assemble Android debug via android/gradle"
    group = buildGroup
    doLast { androidGradle("assembleDebug") }
}

tasks.register("androidAssembleRelease") {
    description = "Assemble Android release via android/gradle"
    group = buildGroup
    doLast { androidGradle("assembleRelease") }
}

tasks.register("androidLint") {
    description = "Run Android lint via android/gradle"
    group = verificationGroup
    doLast { androidGradle("lint") }
}

tasks.register("androidTest") {
    description = "Run Android unit tests via android/gradle"
    group = verificationGroup
    doLast { androidGradle("testDebugUnitTest") }
}

tasks.register("devCheck") {
    description = "Quick development checks (Flutter analyze + Android lint)"
    group = verificationGroup
    dependsOn("flutterAnalyze", "androidLint")
}