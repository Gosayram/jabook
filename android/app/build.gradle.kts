import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import javax.inject.Inject

abstract class GenerateProtoLiteTask : DefaultTask() {
    @get:Input
    abstract val windowsHost: org.gradle.api.provider.Property<Boolean>

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val protoSourceDir: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val protoFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val protocBinaryFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:OutputFile
    abstract val protocOutputFile: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun generate() {
        val protocBinary = protocBinaryFiles.singleFile
        val protocFile = protocOutputFile.get().asFile
        protocFile.parentFile.mkdirs()
        Files.copy(protocBinary.toPath(), protocFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        if (!windowsHost.get()) {
            protocFile.setExecutable(true)
        }

        val output = outputDir.get().asFile
        output.mkdirs()

        val protoRoot = protoSourceDir.get().asFile
        val protoPaths =
            protoFiles
                .files
                .map(File::getAbsolutePath)
                .sorted()

        if (protoPaths.isEmpty()) {
            logger.lifecycle("No proto files found in ${protoRoot.absolutePath}, skipping protobuf generation")
            return
        }

        val args =
            mutableListOf(
                "--java_out=lite:${output.absolutePath}",
                "-I${protoRoot.absolutePath}",
            ).apply {
                addAll(protoPaths)
            }

        execOperations.exec {
            executable = protocFile.absolutePath
            args(args)
        }
    }
}

plugins {
    id("com.android.application")
    // REMOVED: Flutter Gradle Plugin - no longer needed
    // id("dev.flutter.flutter-gradle-plugin")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    // REMOVED: kotlin-kapt - migrated to KSP for Kotlin 2.0+ compatibility
    // id("kotlin-kapt")
    id("org.jlleitschuh.gradle.ktlint")
    // Kotlinx serialization for type-safe navigation
    id("org.jetbrains.kotlin.plugin.serialization")
    // Compose Compiler (required for Kotlin 2.0+)
    id("org.jetbrains.kotlin.plugin.compose")
    // JaCoCo for test coverage
    id("jacoco")
}

val googleServicesCandidates =
    listOf(
        "google-services.json",
        "src/debug/google-services.json",
        "src/beta/google-services.json",
        "src/prod/google-services.json",
        "src/beta/debug/google-services.json",
        "src/debug/beta/google-services.json",
        "src/prod/debug/google-services.json",
        "src/debug/prod/google-services.json",
        "src/betaDebug/google-services.json",
        "src/prodDebug/google-services.json",
    )

val hasGoogleServicesJson = googleServicesCandidates.any { relativePath -> file(relativePath).exists() }

if (hasGoogleServicesJson) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
} else {
    logger.lifecycle(
        "google-services.json was not found in app module. " +
            "Skipping com.google.gms.google-services plugin for this build.",
    )
}

val osName = System.getProperty("os.name").lowercase()
val osArch = System.getProperty("os.arch").lowercase()
val isWindowsHost = osName.contains("windows")

fun detectProtocClassifier(
    os: String,
    arch: String,
): String =
    when {
        os.contains("mac") && (arch == "aarch64" || arch == "arm64") -> "osx-aarch_64"
        os.contains("mac") -> "osx-x86_64"
        os.contains("linux") && (arch == "aarch64" || arch == "arm64") -> "linux-aarch_64"
        os.contains("linux") -> "linux-x86_64"
        os.contains("windows") && (arch == "aarch64" || arch == "arm64") -> "windows-aarch_64"
        os.contains("windows") -> "windows-x86_64"
        else -> throw GradleException("Unsupported OS/arch for protoc: os=$os arch=$arch")
    }

val protocClassifier = detectProtocClassifier(osName, osArch)
val protoSourceDir = layout.projectDirectory.dir("src/main/proto")
val generatedProtoDir = layout.buildDirectory.dir("generated/source/proto/main/java")
val roomSchemaDir = layout.buildDirectory.dir("generated/room-schemas")
val protoInputFiles =
    fileTree(protoSourceDir) {
        include("**/*.proto")
    }

val protocBinary by configurations.creating

val generateProtoLite by tasks.registering(GenerateProtoLiteTask::class) {
    windowsHost.set(isWindowsHost)
    protoSourceDir.set(layout.projectDirectory.dir("src/main/proto"))
    protoFiles.from(protoInputFiles)
    protocBinaryFiles.from(protocBinary)
    outputDir.set(generatedProtoDir)
    protocOutputFile.set(layout.buildDirectory.file("tools/protoc/${if (isWindowsHost) "protoc.exe" else "protoc"}"))
}

android {
    namespace = "com.jabook.app.jabook"
    compileSdk = 36 // Android 16 (required by androidx.activity:1.12.1)
    // ndkVersion no longer needed without Flutter

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)

        compilerOptions {
            // Kotlin compilation optimization
            freeCompilerArgs.addAll(
                "-opt-in=kotlin.RequiresOptIn",
            )
            // Explicit API mode - requires explicit visibility modifiers and return types for public API
            explicitApi()
        }
    }

    // Build process optimization to reduce CPU load
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            // Reduce Kotlin compilation threads
            freeCompilerArgs.addAll(
                "-opt-in=kotlin.RequiresOptIn",
            )
        }
    }

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("key.properties")
            if (keystorePropertiesFile.exists()) {
                val keystoreProperties = Properties()
                keystoreProperties.load(keystorePropertiesFile.inputStream())

                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    // R8 configuration for code optimization
    buildTypes {
        release {
            // Sign with the release keys
            signingConfig = signingConfigs.getByName("release")

            // Enable code shrinking, obfuscation, and optimization for release builds
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            // Disable some optimizations for debug build speed
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    // Enable Jetpack Compose
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    // Compose Compiler configuration moved to plugin (line 16)
    // No need for composeOptions with Kotlin 2.0+ and org.jetbrains.kotlin.plugin.compose

    defaultConfig {
        applicationId = "com.jabook.app.jabook"
        minSdk = 30 // Android 11
        targetSdk = 36 // Android 16

        // Read version from .release-version file (format: version+build, e.g. "1.2.7+127")
        val versionFile = rootProject.file("../.release-version")
        val fullVersion =
            if (versionFile.exists()) {
                versionFile.readText().trim()
            } else {
                "0.0.1+1"
            }

        // Parse version and build number
        val parts = fullVersion.split("+")
        versionName = parts[0] // e.g. "1.2.7"
        versionCode =
            if (parts.size > 1) {
                parts[1].toIntOrNull() ?: 1 // e.g. 127
            } else {
                // Fallback: generate from version (1.2.7 -> 127)
                parts[0].replace(".", "").toIntOrNull() ?: 1
            }

        // Android 14+ specific configurations
        // Ensure proper foreground service type for media playback
        manifestPlaceholders["foregroundServiceType"] = "mediaPlayback"

        // Enable explicit intent handling for Android 14+
        manifestPlaceholders["enableExplicitIntentHandling"] = "true"
        buildConfigField("boolean", "HAS_GOOGLE_SERVICES", hasGoogleServicesJson.toString())
    }

    flavorDimensions += "default"
    productFlavors {
        create("dev") {
            dimension = "default"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "JaBook Dev")
        }
        create("stage") {
            dimension = "default"
            applicationIdSuffix = ".stage"
            versionNameSuffix = "-stage"
            resValue("string", "app_name", "JaBook Stage")
        }
        create("beta") {
            dimension = "default"
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            resValue("string", "app_name", "Jabook Beta")
        }
        create("prod") {
            dimension = "default"
            resValue("string", "app_name", "JaBook")
        }
    }

    sourceSets
        .getByName("main")
        .java
        .directories
        .add(generatedProtoDir.get().asFile.absolutePath)

    // Generate separate APKs per architecture + universal APK
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true // Also build a universal APK
        }
    }
}

// REMOVED: Flutter configuration block - no longer needed
// REMOVED: fixIntegrationTestPlugin task - GeneratedPluginRegistrant.java no longer exists

// REMOVED: afterEvaluate block for fixIntegrationTestPlugin - task no longer needed

// Configure KSP for Room and Hilt
ksp {
    arg("room.schemaLocation", roomSchemaDir.get().asFile.absolutePath)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(generateProtoLite)
}

tasks.withType<org.gradle.api.tasks.compile.JavaCompile>().configureEach {
    dependsOn(generateProtoLite)
}

tasks
    .matching { task ->
        task.name.startsWith("ksp") && task.name.endsWith("Kotlin")
    }.configureEach {
        dependsOn(generateProtoLite)
    }

dependencies {
    // Protoc binary for Proto DataStore code generation (replaces protobuf-gradle-plugin).
    protocBinary("com.google.protobuf:protoc:4.34.1:$protocClassifier@exe")

    // AppCompat for AppCompatActivity and AlertDialog
    implementation(libs.androidx.appcompat)

    // Splash Screen API
    implementation(libs.androidx.core.splashscreen)

    // Dagger Hilt - Dependency Injection (using KSP instead of KAPT for Kotlin 2.0+)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    // Hilt WorkManager integration (using KSP)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Media3 - Native audio player (using 1.8.0 version, same as lissen-android)
    implementation(libs.bundles.media3)

    // Audio metadata parsing using KTagLib (TagLib Kotlin bindings)
    implementation(libs.ktaglib)

    // Android 14+ specific dependencies
    // Add support for Android 14+ foreground service types
    implementation(libs.androidx.work.runtime)

    // Add coroutines support for proper async handling
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)

    // Kotlinx serialization (required by Room 2.8.4+)
    // Room uses setClassDiscriminatorMode which requires kotlinx.serialization 1.6.0+
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.core)

    // Room database for local storage
    implementation(libs.bundles.room)
    // Use KSP instead of kapt for Room (recommended by Google)
    ksp(libs.androidx.room.compiler)

    // DataStore for preferences
    implementation(libs.bundles.datastore)
    // Proto DataStore for typed preferences
    implementation(libs.protobuf.javalite)

    // Security & Encryption - Modern approach with Tink (replaces deprecated EncryptedSharedPreferences)
    implementation(libs.tink.android)
    // Note: Media3 1.8.0 is the current stable version with full Android 14+ support
    // Previous alpha/beta versions (1.3.0, 1.4.0) had compatibility issues
    // Version 1.8.0 includes all Android 14+ fixes and is production-ready

    // Media library for MediaStyle notification (required for MediaStyle class)
    // MediaStyle is part of androidx.media, not androidx.core
    implementation(libs.androidx.media)

    // Network libraries
    implementation(libs.bundles.network)
    // Jsoup for HTML parsing (Rutracker scraping)
    implementation(libs.jsoup)
    // Jsoup optional regex backend required for R8 minify (Re2jRegex)
    implementation(libs.re2j)

    // libtorrent4j for torrent downloads
    implementation(libs.bundles.libtorrent4j)

    // Jetpack Compose - Modern UI toolkit
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    // Google Fonts support for Compose
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.compose.material3.window.size)
    debugImplementation(libs.bundles.compose.debug)

    // Palette for extracting colors from images (Dynamic Theme)
    implementation(libs.androidx.palette)

    // Material 3 Adaptive - Official adaptive UI components
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)

    // NavigationSuiteScaffold for adaptive navigation
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)

    // Compose Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    // Lifecycle & ViewModel for Compose
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Activity Compose
    implementation(libs.androidx.activity.compose)

    // Premium UI Dependencies
    // Haze for Glassmorphism (blur effects)
    implementation(libs.haze)
    // HypnoticCanvas for Procedural Animated Backgrounds (Shaders)
    implementation(libs.hypnoticcanvas)
    implementation(libs.hypnoticcanvas.shaders)
    // Leanback for Android TV support
    implementation(libs.leanback)

    // Coil3 for async image loading in Compose
    implementation(libs.coil3.compose)
    // Coil3 network support with OkHttp (uses existing OkHttpClient)
    implementation(libs.coil3.network.okhttp)

    // Glide for optimized artwork loading in notifications
    // Used specifically for Media3 BitmapLoader and notification cover art
    implementation(libs.glide)
    ksp(libs.glide.ksp)

    // Lyricist dependency removed

    // Note: Google Play Core is NOT needed as a dependency
    // Flutter references these classes but they're not actually used
    // ProGuard rules in proguard-rules.pro handle R8 warnings with -dontwarn

    // Testing dependencies
    testImplementation(libs.bundles.test)
    testImplementation(libs.jimfs)

    // Firebase - Import the Firebase BoM to manage library versions
    implementation(platform(libs.firebase.bom))

    // Firebase Analytics (required for other Firebase services)
    implementation(libs.firebase.analytics)
    // Firebase Crashlytics runtime SDK
    implementation(libs.firebase.crashlytics)

    // Add other Firebase dependencies as needed
    // https://firebase.google.com/docs/android/setup#available-libraries
}

// ktlint configuration
// Plugin version 14.0.1 will use its default ktlint version
// Rules are configured via .editorconfig file
ktlint {
    debug.set(false)
    verbose.set(true)
    android.set(true)
    outputToConsole.set(true)
    outputColorName.set("RED")
    ignoreFailures.set(false)
    enableExperimentalRules.set(true)

    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
        include("**/kotlin/**")
    }
}
// JaCoCo configuration for test coverage
jacoco {
    toolVersion = "0.8.14"
}

// Task to generate test coverage report
tasks.register<org.gradle.testing.jacoco.tasks.JacocoReport>("jacocoTestReport") {
    group = "verification"
    description = "Generate JaCoCo test coverage report"

    // Run tests first
    dependsOn("testBetaDebugUnitTest", "testProdDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    // Collect execution data from test tasks
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include("jacoco/*.exec")
        },
    )

    // Include source files
    sourceDirectories.setFrom(files("src/main/kotlin"))

    // Include class files (excluding generated and test classes)
    classDirectories.setFrom(
        files(
            fileTree(layout.buildDirectory.dir("intermediates/javac/betaDebug/classes")) {
                exclude(
                    "**/R.class",
                    "**/R\$*.class",
                    "**/BuildConfig.*",
                    "**/Manifest*.*",
                    "**/*Test*.*",
                    "**/*_Factory.*",
                    "**/*_HiltModules.*",
                    "**/Hilt_*.*",
                    "android/**/*.*",
                )
            },
        ),
    )
}

// Task to verify coverage meets minimum threshold (85% as per rules)
tasks.register<org.gradle.testing.jacoco.tasks.JacocoCoverageVerification>("jacocoCoverageVerification") {
    group = "verification"
    description = "Verify test coverage meets minimum threshold of 85%"

    dependsOn("jacocoTestReport")

    violationRules {
        rule {
            limit {
                minimum = "0.85".toBigDecimal()
            }
        }
    }

    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include("jacoco/*.exec")
        },
    )

    sourceDirectories.setFrom(files("src/main/kotlin"))

    classDirectories.setFrom(
        files(
            fileTree(layout.buildDirectory.dir("intermediates/javac/betaDebug/classes")) {
                exclude(
                    "**/R.class",
                    "**/R\$*.class",
                    "**/BuildConfig.*",
                    "**/Manifest*.*",
                    "**/*Test*.*",
                    "**/*_Factory.*",
                    "**/*_HiltModules.*",
                    "**/Hilt_*.*",
                    "android/**/*.*",
                )
            },
        ),
    )
}
