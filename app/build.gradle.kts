

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)

    id("com.google.dagger.hilt.android")
    id("org.jmailen.kotlinter") version "5.1.0"
    id("com.google.devtools.ksp")
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.navigation.safeargs.kotlin)
}

kotlinter {
    reporters = arrayOf("checkstyle", "plain")
    ignoreFormatFailures = false
    ignoreLintFailures = false
}

tasks.lintKotlinMain {
    dependsOn(tasks.formatKotlinMain)
}

android {
    namespace = "com.jabook.app"
    compileSdk = 35

    defaultConfig {
        manifestPlaceholders["appLabel"] = "JaBook"
        applicationId = "com.jabook.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // BuildConfig fields
        buildConfigField("String", "RU_TRACKER_BASE_URL", "\"https://rutracker.me\"")
        buildConfigField("String", "RU_TRACKER_BACKUP_URL", "\"https://rutracker.org\"")
        buildConfigField("String", "RU_TRACKER_LEGACY_URL", "\"https://rutracker.net\"")
        buildConfigField("long", "CACHE_EXPIRATION_TIME", "86400000L") // 24 hours
        buildConfigField("int", "MAX_CACHE_SIZE", "104857600") // 100 MB
        buildConfigField("int", "MAX_RETRY_ATTEMPTS", "3")
        buildConfigField("long", "RETRY_DELAY_BASE", "1000L") // 1 second
        buildConfigField("float", "RETRY_DELAY_MULTIPLIER", "2.0f")
        buildConfigField("long", "RATE_LIMIT_INTERVAL", "1000L") // 1 second
        buildConfigField("int", "RATE_LIMIT_REQUESTS", "5")
        buildConfigField("long", "CIRCUIT_BREAKER_TIMEOUT", "30000L") // 30 seconds
        buildConfigField("int", "CIRCUIT_BREAKER_FAILURE_THRESHOLD", "5")
        buildConfigField("long", "CIRCUIT_BREAKER_RETRY_DELAY", "60000L") // 1 minute

        // Room schema location
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
            arg("room.expandProjection", "true")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // BuildConfig fields for release
            buildConfigField("boolean", "DEBUG", "false")
            buildConfigField("boolean", "ENABLE_LOGGING", "false")

            // Res values for release
            resValue("string", "app_name", "JaBook")

            // Signing config for release
            signingConfig = signingConfigs.getByName("debug")
        }

        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"

            // BuildConfig fields for debug
            buildConfigField("boolean", "DEBUG", "true")
            buildConfigField("boolean", "ENABLE_LOGGING", "true")

            // Res values for debug
            resValue("string", "app_name", "JaBook Debug")

            // Enable debug features
            isDebuggable = true
        }

        // Custom build type for staging
        create("staging") {
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"

            // BuildConfig fields for staging
            buildConfigField("boolean", "DEBUG", "true")
            buildConfigField("boolean", "ENABLE_LOGGING", "true")

            // Res values for staging
            resValue("string", "app_name", "JaBook Staging")

            // Disable minify for staging
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                "-Xskip-prerelease-check",
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
                "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
                "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
                "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
                "-opt-in=coil.annotation.ExperimentalCoilApi",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-opt-in=kotlinx.coroutines.FlowPreview"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = true
        dataBinding = true
        resValues = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }


    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    sourceSets {
        // Add shared test resources
        getByName("test") {
            resources.srcDir("src/sharedTest/resources")
        }

        getByName("androidTest") {
            resources.srcDir("src/sharedTest/resources")
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.jsoup)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.material)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.coil3.core)
    implementation(libs.coil3.gif)
    implementation(libs.accompanist.systemuicontroller)
    implementation(libs.accompanist.permissions)
    implementation(libs.accompanist.placeholder)
    implementation(libs.androidx.constraintlayout)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)

    // Circuit Breaker (Resilience4j)
    implementation(libs.resilience4j.circuitbreaker)
    implementation(libs.resilience4j.retry)

    // Apache Commons
    implementation(libs.commons.lang3)


    // Media3 ExoPlayer dependencies
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.exoplayer.workmanager)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
