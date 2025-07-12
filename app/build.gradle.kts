plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    // id("org.jlleitschuh.gradle.ktlint") // Disabled to avoid conflicts with ktfmt
    id("io.gitlab.arturbosch.detekt")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("com.ncorti.ktfmt.gradle") version "0.23.0"
    jacoco
}

android {
    namespace = "com.jabook.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jabook.app"
        minSdk = 23 // Android 6.0 - wide device coverage
        targetSdk = 34 // Latest stable Android
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        buildConfigField("String", "VERSION_NAME", "\"${versionName}\"")
        buildConfigField("boolean", "DEBUG_MODE", "true")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    buildFeatures {
        compose = true
        viewBinding = true // For legacy View fallbacks
        buildConfig = true
    }

    composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin { jvmToolchain(17) }

    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }

    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = false
    }
}

dependencies {
    // Kotlin standard library
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
    // Core Android dependencies
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation & Architecture
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Dependency Injection
    implementation("com.google.dagger:hilt-android:2.56.2")
    implementation("javax.inject:javax.inject:1")
    ksp("com.google.dagger:hilt-compiler:2.56.2")

    // Database & Storage
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.room:room-common:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Media & Audio
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-session:1.3.1")

    // Media compatibility for notifications
    implementation("androidx.media:media:1.6.0")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // JSON processing
    implementation("com.google.code.gson:gson:2.11.0")

    // File processing and compression
    implementation("org.apache.commons:commons-compress:1.27.1")

    // Torrent (placeholder for now)
    // implementation("org.libtorrent4j:libtorrent4j:2.0.9-1")

    // Code quality plugins
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")

    // Testing dependencies
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
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

tasks.register("check-all") { dependsOn("ktfmtCheck", "detekt", "testDebugUnitTest", "assembleDebug", "jacocoTestReport") }

// Configure test task for Android
android.testOptions {
    unitTests.isReturnDefaultValues = true
    unitTests.isIncludeAndroidResources = true
}

// Configure JaCoCo for test coverage
tasks.register("jacocoTestReport", JacocoReport::class) {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val mainSrc = "${project.projectDir}/src/main/kotlin"

    sourceDirectories.setFrom(files(mainSrc))
    classDirectories.setFrom(
        files("${project.layout.buildDirectory.asFile.get()}/intermediates/javac/debug/compileDebugJavaWithJavac/classes")
    )
    executionData.setFrom(fileTree(project.layout.buildDirectory.asFile.get()).include("**/*.exec", "**/*.ec"))
}

// KSP configuration for better performance
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")
}

// Ktlint configuration (disabled to avoid conflicts with ktfmt)
// ktlint {
//     android.set(true)
//     ignoreFailures.set(false)
//     reporters {
//         reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
//         reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
//     }
// }

// Detekt configuration
detekt {
    toolVersion = "1.23.8"
    config.setFrom("$rootDir/detekt.yml")
    buildUponDefaultConfig = true
    allRules = false // Don't activate all rules (many are unstable)
    baseline = file("$rootDir/detekt-baseline.xml")
}

// Configure detekt reports
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        html.required.set(true) // HTML report for browser viewing
        xml.required.set(true) // XML report for CI integration
        sarif.required.set(true) // SARIF format for GitHub integration
        md.required.set(true) // Markdown format for documentation
    }
}

tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach { jvmTarget = "17" }

// Ktfmt configuration
ktfmt {
    kotlinLangStyle() // Use 4 spaces indentation as per .editorconfig
    maxWidth.set(140)
    removeUnusedImports.set(true)
    manageTrailingCommas.set(true) // Automatically add/remove trailing commas
}
