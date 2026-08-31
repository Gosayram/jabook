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
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.process.ExecOperations
import java.io.File
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration
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
    id("dev.detekt")
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

// Load runtime configuration from the gitignored .env at the repo root.
// Keys override defaults and must never be committed. RuTracker mirror URLs are
// exposed to the app as BuildConfig fields so no connection URLs are hardcoded
// in source. Config-cache friendly: read via providers.fileContents.
val envText: String =
    try {
        providers
            .fileContents(rootProject.layout.projectDirectory.file("../.env"))
            .asText
            .get()
    } catch (_: Exception) {
        ""
    }

val envConfig: Map<String, String> =
    envText
        .lines()
        .mapNotNull { rawLine ->
            val line = rawLine.trim()
            if (line.isBlank() || line.startsWith("#")) return@mapNotNull null
            val eq = line.indexOf('=')
            if (eq <= 0) return@mapNotNull null
            val key = line.substring(0, eq).trim()
            var value = line.substring(eq + 1).trim()
            if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length - 1)
            }
            key to value
        }.toMap()

// RuTracker mirrors and base URL MUST be supplied via ../.env — real connection
// URLs are never hardcoded in source (including comments and fallbacks).
//   RUTRACKER_DEFAULT_MIRRORS=comma,separated,mirror,domains
//   RUTRACKER_BASE_URL=https://<mirror>/forum/
//   RUTRACKER_COVER_CDN=https://static.<cdn-host>/   (cover image CDN)
val rutrackerDefaultMirrors: String =
    envConfig["RUTRACKER_DEFAULT_MIRRORS"]?.takeIf { it.isNotBlank() }
        ?: throw GradleException(
            "Missing required RuTracker config in ../.env — set RUTRACKER_DEFAULT_MIRRORS (see .env-example).",
        )
val rutrackerBaseUrl: String =
    envConfig["RUTRACKER_BASE_URL"]?.takeIf { it.isNotBlank() }
        ?: throw GradleException(
            "Missing required RuTracker config in ../.env — set RUTRACKER_BASE_URL (see .env-example).",
        )
val rutrackerCoverCdn: String =
    envConfig["RUTRACKER_COVER_CDN"]?.takeIf { it.isNotBlank() }
        ?: throw GradleException(
            "Missing required RuTracker config in ../.env — set RUTRACKER_COVER_CDN (see .env-example).",
        )

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
val roomSchemaDir = layout.projectDirectory.dir("schemas")
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
    compileSdk = 37 // Android 16+ (required by material3-adaptive 1.3.0-alpha10)
    // ndkVersion no longer needed without Flutter

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }

    kotlin {
        jvmToolchain(25)

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
            val keystoreProperties = Properties()
            if (keystorePropertiesFile.exists()) {
                keystoreProperties.load(keystorePropertiesFile.inputStream())
            }

            val envStoreFile = System.getenv("JABOOK_KEYSTORE_FILE")
            val envStorePassword = System.getenv("JABOOK_KEYSTORE_PASSWORD")
            val envKeyAlias = System.getenv("JABOOK_KEY_ALIAS")
            val envKeyPassword = System.getenv("JABOOK_KEY_PASSWORD")

            val resolvedStoreFile = keystoreProperties.getProperty("storeFile") ?: envStoreFile
            val resolvedStorePassword = keystoreProperties.getProperty("storePassword") ?: envStorePassword
            val resolvedKeyAlias = keystoreProperties.getProperty("keyAlias") ?: envKeyAlias
            val resolvedKeyPassword = keystoreProperties.getProperty("keyPassword") ?: envKeyPassword
            val signingProps =
                mapOf(
                    "storeFile" to resolvedStoreFile,
                    "storePassword" to resolvedStorePassword,
                    "keyAlias" to resolvedKeyAlias,
                    "keyPassword" to resolvedKeyPassword,
                )
            val hasAnySigningConfig = signingProps.values.any { !it.isNullOrBlank() }
            val missingSigningProps =
                signingProps
                    .filterValues { it.isNullOrBlank() }
                    .keys

            if (
                !resolvedStoreFile.isNullOrBlank() &&
                !resolvedStorePassword.isNullOrBlank() &&
                !resolvedKeyAlias.isNullOrBlank() &&
                !resolvedKeyPassword.isNullOrBlank()
            ) {
                storeFile = file(resolvedStoreFile)
                storePassword = resolvedStorePassword
                keyAlias = resolvedKeyAlias
                keyPassword = resolvedKeyPassword
            } else if (hasAnySigningConfig) {
                logger.warn(
                    "Release signing config is incomplete; missing: ${missingSigningProps.joinToString(", ")}",
                )
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            enableAndroidTestCoverage = true
            enableUnitTestCoverage = true
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
        // ponytail: providers.fileContents is a proper config-cache input (tracks file content)
        val fullVersion: String =
            try {
                providers
                    .fileContents(rootProject.layout.projectDirectory.file("../.release-version"))
                    .asText
                    .get()
                    .trim()
            } catch (_: Exception) {
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
        buildConfigField("String", "RUTRACKER_DEFAULT_MIRRORS", "\"$rutrackerDefaultMirrors\"")
        buildConfigField("String", "RUTRACKER_BASE_URL", "\"$rutrackerBaseUrl\"")
        buildConfigField("String", "RUTRACKER_COVER_CDN", "\"$rutrackerCoverCdn\"")
    }

    // Ponytail: strip unused locale resources for smaller APK.
    // resourceConfigurations draws deprecated warning; localeFilters is the AGP 9+ replacement.
    androidResources {
        localeFilters += setOf("en", "ru")
    }

    lint {
        checkDependencies = true
        abortOnError = true
        warningsAsErrors = true
        baseline = file("lint-baseline.xml")
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
            // x86: libtorrent4j ships a native x86 lib (old emulators, some Chromebooks);
            // without this entry that .so is packaged but never delivered per-ABI.
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true // Also build a universal APK
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // ponytail: deliberate — many unit tests construct Android framework objects
            // (ActivityManager.MemoryInfo, Media3 EventTime, SystemClock) without Robolectric.
            // Removing this silently breaks ~10 test classes; migrate them to
            // @RunWith(RobolectricTestRunner::class) first, then drop this flag.
            isReturnDefaultValues = true
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

// REMOVED: Flutter configuration block - no longer needed
// REMOVED: fixIntegrationTestPlugin task - GeneratedPluginRegistrant.java no longer exists

// REMOVED: afterEvaluate block for fixIntegrationTestPlugin - task no longer needed

// Only enable R8 minification for prod and beta release builds
androidComponents {
    beforeVariants { variant ->
        val flavor = variant.productFlavors.firstOrNull()?.second
        if ((flavor == "prod" || flavor == "beta") && variant.buildType == "release") {
            variant.isMinifyEnabled = true
        }
    }
}

// Configure KSP for Room and Hilt
ksp {
    arg("room.schemaLocation", roomSchemaDir.asFile.absolutePath)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(generateProtoLite)
}

tasks.withType<org.gradle.api.tasks.compile.JavaCompile>().configureEach {
    dependsOn(generateProtoLite)
}

tasks.withType<Test>().configureEach {
    // Hard stop for hung test task in local and CI runs. 20 min: one hung test
    // previously killed the whole CI run at 12 min with 105+ test files.
    timeout.set(Duration.ofMinutes(20))
    // ponytail: failFast via gradle property — fast local feedback, full CI report.
    // CI: -Ptest.failFast=false ; local default: true (fail on first error).
    failFast =
        providers
            .gradleProperty("test.failFast")
            .map { it.toBoolean() }
            .orElse(true)
            .get()
    // Robolectric's native runtime mounts a shared ZIP filesystem; parallel forks
    // can race there. maxParallelForks=2 verified stable (2026-08-30 experiment);
    // revert to 1 if flaky non-reproducible failures reappear.
    maxParallelForks = 2
    forkEvery = 120
    // ponytail: Robolectric loads many classes; keep the single test JVM well provisioned.
    minHeapSize = "512m"
    maxHeapSize = "2048m"
    jvmArgs(
        "-XX:+UseParallelGC",
        "-XX:MaxMetaspaceSize=512m",
        // Robolectric/JNA use JNI. Declare it explicitly on JDK 25 instead of
        // relying on the deprecated permissive native-access mode.
        "--enable-native-access=ALL-UNNAMED",
        // Robolectric appends to the bootstrap classpath, where CDS cannot be used.
        "-Xshare:off",
    )
    val logStartedTests =
        providers
            .gradleProperty("test.logStarted")
            .map(String::toBoolean)
            .orElse(false)
            .get()
    testLogging {
        events("failed", "skipped")
        if (logStartedTests) events("started")
    }
    systemProperty("kotlinx.coroutines.test.default_timeout", "30s")

    // ponytail: test.fast=true excludes @Category(SlowTest) classes (Robolectric).
    // `make test-fast` → ~1min (pure JVM); `make test` → ~5min (all).
    val fastOnly =
        providers
            .gradleProperty("test.fast")
            .orElse("false")
            .get()
            .toBoolean()
    if (fastOnly) {
        useJUnit {
            excludeCategories("com.jabook.app.jabook.test.SlowTest")
        }
    }

    // Emit thread diagnostics when a test likely failed due to timeout/hang.
    // Note: tests run in forked JVMs (forkEvery=120, maxParallelForks>0), so this
    // dump captures the Gradle daemon's threads, not the test process. Useful as a
    // coarse signal but not a substitute for jstack on the forked PID.
    val enableThreadDumpOnTimeout =
        providers
            .gradleProperty("test.threadDumpOnTimeout")
            .map { it.equals("true", ignoreCase = true) }
            .orElse(false)
            .get()

    addTestListener(
        object : TestListener {
            override fun beforeSuite(suite: TestDescriptor) = Unit

            override fun afterSuite(
                suite: TestDescriptor,
                result: TestResult,
            ) = Unit

            override fun beforeTest(testDescriptor: TestDescriptor) = Unit

            override fun afterTest(
                testDescriptor: TestDescriptor,
                result: TestResult,
            ) {
                if (result.resultType != TestResult.ResultType.FAILURE) return
                val failureSummary =
                    result
                        .exceptions
                        .joinToString(separator = "\n") { throwable ->
                            buildString {
                                append(throwable::class.java.name)
                                append(": ")
                                append(throwable.message.orEmpty())
                            }
                        }
                val looksLikeTimeout =
                    failureSummary.contains("TestTimedOutException") ||
                        failureSummary.contains("TimeoutException") ||
                        failureSummary.contains("timed out", ignoreCase = true)
                if (!looksLikeTimeout || !enableThreadDumpOnTimeout) return

                logger.error(
                    "⏱️ Timeout-like failure in ${testDescriptor.className}.${testDescriptor.name}. " +
                        "Thread dump is enabled via -Ptest.threadDumpOnTimeout=true.",
                )
                val threadDump =
                    ManagementFactory
                        .getThreadMXBean()
                        .dumpAllThreads(true, true)
                        .joinToString(separator = "\n\n") { threadInfo -> threadInfo.toString() }
                logger.error(threadDump)
            }
        },
    )
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

    // Media3 - Native audio player (stable 1.11.0; version catalog is the source of truth)
    implementation(libs.bundles.media3)
    implementation(libs.media3.ui)
    implementation(libs.media3.ui.compose)
    implementation(libs.media3.cast)
    implementation(libs.androidx.mediarouter)
    implementation(libs.play.services.cast.framework)

    // Audio metadata parsing using KTagLib (TagLib Kotlin bindings)
    implementation(libs.ktaglib)

    // Android 14+ specific dependencies
    // Add support for Android 14+ foreground service types
    implementation(libs.androidx.work.runtime)

    // Add coroutines support for proper async handling
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)
    debugImplementation(libs.kotlinx.coroutines.debug)
    implementation(libs.kotlinx.collections.immutable)

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

    // Media library for MediaStyle notification (required for MediaStyle class)
    // MediaStyle is part of androidx.media, not androidx.core
    implementation(libs.androidx.media)

    // Network libraries
    implementation(libs.bundles.network)
    // Jsoup for HTML parsing (Rutracker scraping)
    implementation(libs.jsoup)

    // libtorrent4j for torrent downloads
    implementation(libs.bundles.libtorrent4j)

    // Jetpack Compose - Modern UI toolkit
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    // Google Fonts support for Compose
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.compose.material3.window.size)
    // ponytail: graphics-shapes for M3 Expressive RoundedPolygon/Morph (overrides BOM via explicit version)
    implementation(libs.androidx.graphics.shapes)
    debugImplementation(libs.bundles.compose.debug)

    // Palette for extracting colors from images (Dynamic Theme)
    implementation(libs.androidx.palette)
    // Material Color Utilities — real HCT/CAM16/TonalPalette/Score (replaces HSL stubs)
    implementation(libs.material.color.utilities)

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

    // Lyricist dependency removed

    // Note: Google Play Core is NOT needed as a dependency
    // Flutter references these classes but they're not actually used
    // ProGuard rules in proguard-rules.pro handle R8 warnings with -dontwarn

    // LeakCanary - Memory leak detection for debug builds (BP-6.4)
    debugImplementation(libs.leakcanary.android)

    // Testing dependencies
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.bundles.test)
    testImplementation(libs.bundles.compose.test)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.jimfs)
    testImplementation(libs.kotest.property)

    // Android Instrumentation tests
    androidTestUtil(libs.androidx.test.services)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.media3.test.utils)

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

// Detekt configuration for static analysis (complements ktlint)
// Config file: default-detekt-config.yml in project root
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(
        rootProject.layout.projectDirectory
            .file("../default-detekt-config.yml")
            .asFile,
    )
    baseline =
        rootProject.layout.projectDirectory
            .file("../detekt-baseline.xml")
            .asFile

    source.setFrom(
        files(
            "src/main/kotlin",
        ),
    )
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
    // Kotlin classes live in tmp/kotlin-classes; generated Java (Hilt, etc.) in intermediates/javac
    classDirectories.setFrom(
        files(
            fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/betaDebug")) {
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
            fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/betaDebug")) {
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

tasks.register("generateBaselineProfile") {
    group = "verification"
    description = "Validates and materializes app baseline profile artifact from src/main/baseline-prof.txt"

    val sourceFile = layout.projectDirectory.file("src/main/baseline-prof.txt")
    val outputFile = layout.buildDirectory.file("generated/baseline-prof/baseline-prof.txt")
    outputs.file(outputFile)

    doLast {
        val input = sourceFile.asFile
        if (!input.exists()) {
            throw GradleException(
                "Missing baseline profile file at ${input.absolutePath}. " +
                    "Create it before running generateBaselineProfile.",
            )
        }
        val lines =
            input
                .readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
        if (lines.isEmpty()) {
            throw GradleException("Baseline profile is empty: ${input.absolutePath}")
        }

        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        input.copyTo(target = output, overwrite = true)
        logger.lifecycle("Baseline profile materialized: ${output.absolutePath} (${lines.size} rules)")
    }
}
