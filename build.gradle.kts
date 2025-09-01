// Root Gradle build script used only for utility tasks in a Flutter project.
// Tasks are configuration-cache friendly.

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Copy

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

// --- Bootstrap: generate android/ via `flutter create jabook_android ...` and move it ---

// 1) Run flutter create jabook_android --org com.jabook.app --platforms=android -a kotlin
val flutterCreateAndroid by tasks.registering(Exec::class) {
    description = "Create temporary Flutter project in jabook_android with Android platform"
    group = flutterGroup
    // Only if android/ is missing
    onlyIf {
        val androidDir = file("android")
        !androidDir.exists()
    }
    commandLine(
        "flutter", "create",
        "jabook_android",
        "--org", "com.jabook.app",
        "--platforms=android",
        "-a", "kotlin"
    )
}

// 2) Copy jabook_android/android -> android
val moveCreatedAndroid by tasks.registering(Copy::class) {
    description = "Move generated Android folder into project root"
    group = flutterGroup
    dependsOn(flutterCreateAndroid)
    onlyIf {
        val src = file("jabook_android/android")
        val dst = file("android")
        src.exists() && !dst.exists()
    }
    from("jabook_android/android")
    into("android")
}

// 3) Cleanup jabook_android temp dir
val cleanupTempAndroid by tasks.registering(Delete::class) {
    description = "Cleanup temporary jabook_android"
    group = flutterGroup
    dependsOn(moveCreatedAndroid)
    onlyIf { file("jabook_android").exists() }
    delete("jabook_android")
}

// 4) Aggregate bootstrap task
val ensureAndroid by tasks.registering(DefaultTask::class) {
    description = "Ensure android/ exists by creating it via flutter and moving it from jabook_android"
    group = flutterGroup
    dependsOn(flutterCreateAndroid, moveCreatedAndroid, cleanupTempAndroid)
}

// --- Flutter tasks (Exec) ---

tasks.register<Exec>("flutterAnalyze") {
    description = "Run `flutter analyze` on the project"
    group = flutterGroup
    commandLine("flutter", "analyze")
}

tasks.register<Exec>("flutterBuildApk") {
    description = "Build a debug APK via Flutter"
    group = buildGroup
    dependsOn(ensureAndroid)
    commandLine("flutter", "build", "apk", "--debug")
}

tasks.register<Exec>("flutterBuildAppbundle") {
    description = "Build a release AAB via Flutter"
    group = buildGroup
    dependsOn(ensureAndroid)
    commandLine("flutter", "build", "appbundle", "--release")
}

/**
 * Build a **signed** release APK via Flutter.
 * Signing is configured in android/app/build.gradle.kts and keystore.properties.
 */
tasks.register<Exec>("flutterBuildApkReleaseSigned") {
    description = "Build a signed release APK via Flutter"
    group = buildGroup
    dependsOn(ensureAndroid)

    val ksProps = file("keystore.properties")
    val ksFile = file("keystore/jabook-release.jks")
    inputs.files(ksProps, ksFile)
    onlyIf {
        if (!ksProps.exists()) {
            throw GradleException("Missing keystore.properties at project root.")
        }
        if (!ksFile.exists()) {
            throw GradleException("Missing keystore/jabook-release.jks.")
        }
        true
    }

    commandLine("flutter", "build", "apk", "--release")
}

// --- Android tasks (Exec inside android/ dir) ---

fun gradlewName(): String =
    if (System.getProperty("os.name").lowercase().contains("win")) "gradlew.bat" else "./gradlew"

tasks.register<Exec>("androidAssembleDebug") {
    description = "Assemble Android debug via android/gradle"
    group = buildGroup
    dependsOn(ensureAndroid)
    workingDir = file("android")
    commandLine(gradlewName(), "assembleDebug")
}

tasks.register<Exec>("androidAssembleRelease") {
    description = "Assemble Android release via android/gradle (signed if configured)"
    group = buildGroup
    dependsOn(ensureAndroid)
    workingDir = file("android")
    commandLine(gradlewName(), "assembleRelease")
}

tasks.register<Exec>("androidTest") {
    description = "Run Android unit tests via android/gradle"
    group = verificationGroup
    dependsOn(ensureAndroid)
    workingDir = file("android")
    commandLine(gradlewName(), "testDebugUnitTest")
}

// --- Composite checks ---

tasks.register("devCheck") {
    description = "Quick development checks (Flutter analyze)"
    group = verificationGroup
    dependsOn("flutterAnalyze")
}