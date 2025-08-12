// Top-level imports for property loading and Jacoco task type
import java.util.Properties
import java.io.FileInputStream
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    // Android & Kotlin (use AGP version from root; do not specify here to avoid conflicts)
    id("com.android.application")
    id("org.jetbrains.kotlin.android") version "2.2.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0"
    id("org.jetbrains.kotlin.plugin.parcelize") version "2.2.0"

    // KSP (aligned with Kotlin 2.2.0)
    id("com.google.devtools.ksp") version "2.2.0-2.0.2"

    // Static analysis
    id("io.gitlab.arturbosch.detekt") version "1.23.8"

    // Hilt Gradle plugin
    id("com.google.dagger.hilt.android") version "2.57"

    // Code coverage
    jacoco

    // Kotlinx Serialization compiler plugin
    kotlin("plugin.serialization") version "2.2.0"
}

// Load keystore properties (if present)
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(FileInputStream(keystorePropertiesFile))
    }
}

// Android configuration
android {
    namespace = "com.jabook.app"

    // Use SDK levels supported by used libraries
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jabook.app"
        minSdk = 23
        targetSdk = 35

        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        buildConfigField("String", "VERSION_NAME", "\"${versionName}\"")
        buildConfigField("boolean", "DEBUG_MODE", "true")
    }

    // Signing (uses values from keystore.properties if available)
    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties["keyAlias"]?.toString()
                keyPassword = keystoreProperties["keyPassword"]?.toString()
                storeFile = keystoreProperties["storeFile"]?.toString()?.let { file(it) }
                storePassword = keystoreProperties["storePassword"]?.toString()
            }
        }
    }

    // Build types
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    // Enable Compose, ViewBinding and BuildConfig
    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }

    // Java / Kotlin toolchains
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }

    // Lint configuration
    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = false
    }

    // Packaging options
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs { }
    }

    // Unit test options
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

// Repositories are intentionally NOT declared here.
// Settings enforce FAIL_ON_PROJECT_REPOS; repositories must be in settings.gradle(.kts).

dependencies {
    // Kotlin standard library
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")

    // Android core & UI
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")

    // Activity
    implementation("androidx.activity:activity-compose:1.10.1")

    // Compose (stable BOM)
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material:material")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.7")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // DI
    implementation("com.google.dagger:hilt-android:2.57")
    implementation("javax.inject:javax.inject:1")
    ksp("com.google.dagger:hilt-compiler:2.57")

    // Room
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    implementation("androidx.room:room-common:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    // Media3
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-exoplayer-dash:1.8.0")
    implementation("androidx.media3:media3-ui:1.8.0")
    implementation("androidx.media3:media3-session:1.8.0")

    // Legacy media compat
    implementation("androidx.media:media:1.7.0")

    // Networking (Retrofit2 + OkHttp4)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // JSON (Gson)
    implementation("com.google.code.gson:gson:2.13.1")

    // HTML parsing
    implementation("org.jsoup:jsoup:1.21.1")

    // Kotlinx Serialization JSON (with kotlin.time.Instant serializers)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // Image loading (Coil v3 for Compose)
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")

    // Audio metadata
    implementation("net.jthink:jaudiotagger:3.0.1")

    // Compression
    implementation("org.apache.commons:commons-compress:1.28.0")

    // Detekt formatting
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.2")

    // Tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("com.google.truth:truth:1.4.4")
    testImplementation("io.mockk:mockk:1.13.13")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Aggregated quality task
tasks.register("check-all") {
    dependsOn("detekt", "testDebugUnitTest", "assembleDebug", "jacocoTestReport", "lint")
}

// JaCoCo configuration for unit test coverage report
tasks.register("jacocoTestReport", JacocoReport::class) {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    // Main Kotlin sources
    val mainSrc = "${project.projectDir}/src/main/kotlin"
    sourceDirectories.from(mainSrc)

    // Include both Java and Kotlin compiled classes (AGP 8+)
    classDirectories.from(
        fileTree("${project.layout.buildDirectory.get()}/intermediates/javac/debug/compileDebugJavaWithJavac/classes"),
        fileTree("${project.layout.buildDirectory.get()}/tmp/kotlin-classes/debug")
    )

    // Execution data (*.exec / *.ec)
    executionData.from(
        fileTree(project.layout.buildDirectory.get()).include("**/*.exec", "**/*.ec")
    )
}

// KSP arguments (Room schemas)
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")
}

// Detekt configuration
detekt {
    toolVersion = "1.23.8"
    config.setFrom("$rootDir/detekt.yml")
    buildUponDefaultConfig = true
    allRules = false
    baseline = file("$rootDir/detekt-baseline.xml")
    autoCorrect = true
}

// Detekt reports & JVM target
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        html.required.set(true)
        xml.required.set(true)
        sarif.required.set(true)
        md.required.set(true)
    }
}