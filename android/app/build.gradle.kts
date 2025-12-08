import java.util.Properties

plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
    id("com.google.dagger.hilt.android")
    id("kotlin-kapt")
    id("org.jlleitschuh.gradle.ktlint")
}

android {
    namespace = "com.jabook.app.jabook"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Enable core library desugaring for flutter_local_notifications
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        jvmToolchain(17)

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

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "com.jabook.app.jabook"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName

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
}

flutter {
    source = "../.."
}

// Task to fix integration_test plugin registration in GeneratedPluginRegistrant.java
// This is needed because integration_test is a dev dependency but Flutter still generates
// its registration, causing compilation errors in release builds
tasks.register("fixIntegrationTestPlugin") {
    group = "flutter"
    description = "Fix integration_test plugin registration to use reflection"

    doLast {
        val generatedFile = file("src/main/java/io/flutter/plugins/GeneratedPluginRegistrant.java")
        if (!generatedFile.exists()) {
            logger.warn("GeneratedPluginRegistrant.java not found, skipping fix")
            return@doLast
        }

        var content = generatedFile.readText()

        // Check if already fixed
        if (content.contains("Class.forName(\"dev.flutter.plugins.integration_test")) {
            logger.info("integration_test plugin already fixed, skipping")
            return@doLast
        }

        // Check if integration_test registration exists
        if (!content.contains("integration_test.IntegrationTestPlugin")) {
            logger.info("integration_test plugin not found, skipping fix")
            return@doLast
        }

        // Replace direct instantiation with reflection-based approach
        // Match the try-catch block for integration_test plugin registration
        val oldPattern = """try \{
      flutterEngine\.getPlugins\(\)\.add\(new dev\.flutter\.plugins\.integration_test\.IntegrationTestPlugin\(\)\);
    \} catch \(Exception e\) \{
      Log\.e\(TAG, "Error registering plugin integration_test, dev\.flutter\.plugins\.integration_test\.IntegrationTestPlugin", e\);
    \}"""

        val newCode = """// integration_test is a dev dependency - use reflection to avoid compilation errors in release
    try {
      Class<?> integrationTestClass = Class.forName("dev.flutter.plugins.integration_test.IntegrationTestPlugin");
      Object plugin = integrationTestClass.getDeclaredConstructor().newInstance();
      flutterEngine.getPlugins().add((io.flutter.embedding.engine.plugins.FlutterPlugin) plugin);
    } catch (ClassNotFoundException e) {
      // Silently ignore - integration_test is not available in release builds
    } catch (Exception e) {
      Log.e(TAG, "Error registering plugin integration_test, dev.flutter.plugins.integration_test.IntegrationTestPlugin", e);
    }"""

        content = content.replace(Regex(oldPattern, RegexOption.MULTILINE), newCode)
        generatedFile.writeText(content)
        logger.info("Fixed integration_test plugin registration in GeneratedPluginRegistrant.java")
    }
}

// Automatically run fix task before Java compilation for all release build types
afterEvaluate {
    tasks
        .matching { it.name.startsWith("compile") && it.name.contains("Release") && it.name.contains("Java") }
        .configureEach {
            dependsOn("fixIntegrationTestPlugin")
        }

    // Also run for all build variants
    tasks
        .matching { it.name.contains("compileReleaseJavaWithJavac") }
        .configureEach {
            dependsOn("fixIntegrationTestPlugin")
        }
}

// Configure kapt for Dagger Hilt and Room
// Note: Some kapt options may show warnings if not used by processors.
// This is normal and doesn't affect functionality.
kapt {
    correctErrorTypes = true
    useBuildCache = true
    // Room schema export location
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
    // These options are set automatically by Hilt plugin
    // Warnings about unrecognized options can be safely ignored
}

dependencies {
    // Desugaring for flutter_local_notifications
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    // AppCompat for AppCompatActivity and AlertDialog
    implementation("androidx.appcompat:appcompat:1.7.1")

    // Dagger Hilt - Dependency Injection (version 2.57.2, same as lissen-android)
    implementation("com.google.dagger:hilt-android:2.57.2")
    kapt("com.google.dagger:hilt-android-compiler:2.57.2")

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
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // Room database for local storage
    val roomVersion = "2.8.4"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion") {
        // Exclude any old kotlinx-serialization versions that Room might bring transitively
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-serialization-json")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-serialization-core")
    }
    // Add kotlinx-serialization to kapt compile classpath for Room compiler
    // This must be added after Room compiler to ensure correct version is used
    kapt("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // DataStore for preferences
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    // Note: Media3 1.8.0 is the current stable version with full Android 14+ support
    // Previous alpha/beta versions (1.3.0, 1.4.0) had compatibility issues
    // Version 1.8.0 includes all Android 14+ fixes and is production-ready

    // Media library for MediaStyle notification (required for MediaStyle class)
    // MediaStyle is part of androidx.media, not androidx.core
    implementation("androidx.media:media:1.7.1")

    // OkHttp for network requests in MediaDataSourceFactory
    implementation("com.squareup.okhttp3:okhttp:5.3.2")

    // Note: Google Play Core is NOT needed as a dependency
    // Flutter references these classes but they're not actually used
    // ProGuard rules in proguard-rules.pro handle R8 warnings with -dontwarn
}

// Force kotlinx-serialization version for all configurations (including kapt)
// This is required because Room 2.8.4+ uses setClassDiscriminatorMode which requires kotlinx.serialization 1.6.0+
val kotlinxSerializationVersion = "1.9.0"
configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
        force("org.jetbrains.kotlinx:kotlinx-serialization-core:$kotlinxSerializationVersion")
        // Also force transitive dependencies
        eachDependency {
            if (requested.group == "org.jetbrains.kotlinx" && requested.name.startsWith("kotlinx-serialization")) {
                useVersion(kotlinxSerializationVersion)
            }
        }
    }
}

// Explicitly configure kapt to use the correct kotlinx-serialization version
// This is needed because kapt uses a separate classpath that may not respect resolutionStrategy
afterEvaluate {
    // Force version for all kapt-related configurations
    configurations.matching { it.name.startsWith("kapt") || it.name.contains("kapt") }.configureEach {
        resolutionStrategy {
            force("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
            force("org.jetbrains.kotlinx:kotlinx-serialization-core:$kotlinxSerializationVersion")
            eachDependency {
                if (requested.group == "org.jetbrains.kotlinx" && requested.name.startsWith("kotlinx-serialization")) {
                    useVersion(kotlinxSerializationVersion)
                }
            }
        }
    }

    // Also explicitly add kotlinx-serialization to kapt classpath as a dependency
    // This ensures it's available even if resolutionStrategy doesn't work
    try {
        configurations.findByName("kapt")?.dependencies?.add(
            project.dependencies.create("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion"),
        )
    } catch (e: Exception) {
        // Ignore if configuration doesn't exist or is already configured
    }
}

// ktlint configuration
// Plugin version 14.0.1 will use its default ktlint version
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
