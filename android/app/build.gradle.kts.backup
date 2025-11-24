import java.util.Properties

plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
    id("com.google.dagger.hilt.android")
    id("kotlin-kapt")
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
            // Kotlin compilation optimization to reduce CPU load
            freeCompilerArgs.addAll(
                "-Xallow-result-return-type",
                "-Xopt-in=kotlin.RequiresOptIn"
            )
        }
    }

    // Build process optimization to reduce CPU load
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            // Reduce Kotlin compilation threads
            freeCompilerArgs.addAll(
                "-Xallow-result-return-type",
                "-Xopt-in=kotlin.RequiresOptIn"
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
                "proguard-rules.pro"
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
            versionNameSuffix = "-beta"
            resValue("string", "app_name", "jabook beta")
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

// Configure kapt for Dagger Hilt
// Note: Some kapt options may show warnings if not used by processors.
// This is normal and doesn't affect functionality.
kapt {
    correctErrorTypes = true
    useBuildCache = true
    // These options are set automatically by Hilt plugin
    // Warnings about unrecognized options can be safely ignored
}

dependencies {
    // Desugaring for flutter_local_notifications
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
    
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
    implementation("androidx.work:work-runtime:2.9.0")
    
    // Add coroutines support for proper async handling
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Note: Media3 1.8.0 is the current stable version with full Android 14+ support
    // Previous alpha/beta versions (1.3.0, 1.4.0) had compatibility issues
    // Version 1.8.0 includes all Android 14+ fixes and is production-ready
    
    // Media library for MediaStyle notification (required for MediaStyle class)
    // MediaStyle is part of androidx.media, not androidx.core
    implementation("androidx.media:media:1.7.0")
    
    // OkHttp for network requests in MediaDataSourceFactory
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
}
