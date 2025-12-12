import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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
    // Protobuf for Proto DataStore
    id("com.google.protobuf") version "0.9.5"
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
            // TODO: Add your own signing config for the release build.
            // Signing with the debug keys for now, so `flutter run --release` works.
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
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // AppCompat for AppCompatActivity and AlertDialog
    implementation("androidx.appcompat:appcompat:1.7.1")

    // Splash Screen API
    implementation("androidx.core:core-splashscreen:1.2.0")

    // Dagger Hilt - Dependency Injection (using KSP instead of KAPT for Kotlin 2.0+)
    implementation("com.google.dagger:hilt-android:2.57.2")
    ksp("com.google.dagger:hilt-android-compiler:2.57.2")

    // Hilt WorkManager integration (using KSP)
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // Media3 - Native audio player (using 1.8.0 version, same as lissen-android)
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-ui:1.8.0")
    implementation("androidx.media3:media3-session:1.8.0")
    implementation("androidx.media3:media3-common:1.8.0")
    // Media3 database provider for cache
    implementation("androidx.media3:media3-database:1.8.0")
    // Media3 datasource for network streaming (OkHttp support)
    implementation("androidx.media3:media3-datasource-okhttp:1.8.0")

    // Android 14+ specific dependencies
    // Add support for Android 14+ foreground service types
    implementation("androidx.work:work-runtime:2.11.0")

    // Add coroutines support for proper async handling
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.10.2")

    // Kotlinx serialization (required by Room 2.8.4+)
    // Room uses setClassDiscriminatorMode which requires kotlinx.serialization 1.6.0+
    val kotlinxSerializationVersion = "1.9.0"
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:$kotlinxSerializationVersion")

    // Room database for local storage
    val roomVersion = "2.8.4"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    // Use KSP instead of kapt for Room (recommended by Google)
    ksp("androidx.room:room-compiler:$roomVersion")

    // DataStore for preferences
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    // Proto DataStore for typed preferences
    implementation("androidx.datastore:datastore:1.2.0")
    implementation("com.google.protobuf:protobuf-javalite:4.33.2")

    // Security & Encryption - Modern approach with Tink (replaces deprecated EncryptedSharedPreferences)
    implementation("com.google.crypto.tink:tink-android:1.20.0")
    // Note: Media3 1.8.0 is the current stable version with full Android 14+ support
    // Previous alpha/beta versions (1.3.0, 1.4.0) had compatibility issues
    // Version 1.8.0 includes all Android 14+ fixes and is production-ready

    // Media library for MediaStyle notification (required for MediaStyle class)
    // MediaStyle is part of androidx.media, not androidx.core
    implementation("androidx.media:media:1.7.1")

    // OkHttp for network requests in MediaDataSourceFactory
    implementation("com.squareup.okhttp3:okhttp:5.3.2")

    // Retrofit for REST API calls
    val retrofitVersion = "3.0.0"
    implementation("com.squareup.retrofit2:retrofit:$retrofitVersion")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:$retrofitVersion")

    // OkHttp logging interceptor for debugging
    implementation("com.squareup.okhttp3:logging-interceptor:5.3.2")

    // Jsoup for HTML parsing (Rutracker scraping)
    implementation("org.jsoup:jsoup:1.21.2")

    // Retrofit scalar converter for HTML responses
    implementation("com.squareup.retrofit2:converter-scalars:$retrofitVersion")

    // libtorrent4j for torrent downloads
    implementation("org.libtorrent4j:libtorrent4j:2.1.0-38")
    implementation("org.libtorrent4j:libtorrent4j-android-arm64:2.1.0-38")
    implementation("org.libtorrent4j:libtorrent4j-android-arm:2.1.0-38")
    implementation("org.libtorrent4j:libtorrent4j-android-x86:2.1.0-38")

    // Jetpack Compose - Modern UI toolkit
    implementation(platform("androidx.compose:compose-bom:2025.12.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Compose Navigation
    implementation("androidx.navigation:navigation-compose:2.9.6")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")
    // Navigation with kotlinx.serialization for type-safe routing
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // Lifecycle & ViewModel for Compose
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    // Activity Compose
    implementation("androidx.activity:activity-compose:1.12.1")

    // Coil3 for async image loading in Compose
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")

    // Lyricist - Type-safe i18n for Compose
    implementation("cafe.adriel.lyricist:lyricist:1.8.0")
    ksp("cafe.adriel.lyricist:lyricist-processor:1.8.0")

    // Note: Google Play Core is NOT needed as a dependency
    // Flutter references these classes but they're not actually used
    // ProGuard rules in proguard-rules.pro handle R8 warnings with -dontwarn
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
// Protobuf configuration for Proto DataStore
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.33.2"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}
