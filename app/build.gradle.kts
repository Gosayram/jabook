
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.gms.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.navigation.safeargs.kotlin)
}

android {
    namespace = "com.jabook.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jabook.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

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
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf(
                    "room.schemaLocation" to "$projectDir/schemas",
                    "room.incremental" to "true",
                    "room.expandProjection" to "true"
                )
            }
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
            buildConfigField("boolean", "ENABLE_CRASHLYTICS", "true")
            buildConfigField("boolean", "ENABLE_ANALYTICS", "true")
            
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
            buildConfigField("boolean", "ENABLE_CRASHLYTICS", "false")
            buildConfigField("boolean", "ENABLE_ANALYTICS", "false")
            
            // Res values for debug
            resValue("string", "app_name", "JaBook Debug")
            
            // Enable debug features
            debuggable = true
        }
        
        // Custom build type for staging
        create("staging") {
            initWith(getByName("release"))
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            
            // BuildConfig fields for staging
            buildConfigField("boolean", "DEBUG", "true")
            buildConfigField("boolean", "ENABLE_LOGGING", "true")
            buildConfigField("boolean", "ENABLE_CRASHLYTICS", "true")
            buildConfigField("boolean", "ENABLE_ANALYTICS", "true")
            
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
    
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = listOf(
            "-Xskip-prerelease-check",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=coil.annotation.ExperimentalCoilApi",
            "-opt-in=androidx.paging.ExperimentalPagingApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview"
        )
    }
    
    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = true
        dataBinding = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE-notice.md"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/DEPENDENCIES.txt"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/versions/9/previous-versions.bin"
            excludes += "META-INF/versions/9/previous-versions.bin.meta_inf"
            excludes += "META-INF/versions/9/previous-versions.bin.sig"
            excludes += "META-INF/versions/9/previous-versions.bin.meta_inf.sig"
            excludes += "kotlin/**"
            excludes += "**/*.kotlin_metadata"
            excludes += "**/*.kotlin_builtins"
            excludes += "**/*.kotlin_module"
            excludes += "**/kotlin/**"
            excludes += "**/*.kotlin_metadata"
            excludes += "**/*.kotlin_builtins"
            excludes += "**/*.kotlin_module"
            pickFirsts += "META-INF/io.netty.versions.properties"
            pickFirsts += "META-INF/DEPENDENCIES"
            pickFirsts += "META-INF/LICENSE"
            pickFirsts += "META-INF/LICENSE.txt"
            pickFirsts += "META-INF/NOTICE"
            pickFirsts += "META-INF/NOTICE.txt"
        }
    }
    
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
    
    sourceSets {
        // Add the schema location for Room
        getByName("main") {
            java.srcDir("build/generated/source/kapt/main")
        }
        
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
        disable += "InvalidPackage"
        disable += "MissingTranslation"
        disable += "ExtraTranslation"
        disable += "VectorPath"
        disable += "VectorRaster"
        disable += "OldTargetApi"
        disable += "GradleDependency"
        disable += "IconMissingDensityFolder"
        disable += "IconDensities"
        disable += "IconDuplicates"
        disable += "IconDuplicatesConfig"
        disable += "IconLocation"
        disable += "IconXmlFormat"
        disable += "UnusedResources"
        disable += "UnusedIds"
        disable += "Typos"
        disable += "RtlHardcoded"
        disable += "RtlCompat"
        disable += "RtlEnabled"
        disable += "RtlSymmetry"
        disable += "SetTextI18n"
        disable += "HardcodedText"
        disable += "ImpliedQuantity"
        disable += "MissingQuantity"
        disable += "PluralsCandidate"
        disable += "StringFormatCount"
        disable += "StringFormatInvalid"
        disable += "StringFormatMatches"
        disable += "TooManyViews"
        disable += "TooDeepLayout"
        disable += "NestedWeights"
        disable += "NestedScrolling"
        disable += "Overdraw"
        disable += "UnusedMargin"
        disable += "UnusedPadding"
        disable += "UselessParent"
        disable += "UseCompoundDrawables"
        disable += "UselessLeaf"
        disable += "UselessView"
        disable += "MergeRootFrame"
        disable += "DisableBaselineAlignment"
        disable += "AllCaps"
        disable += "SmallSp"
        disable += "DefaultLocale"
        disable += "HardcodedDebugMode"
        disable += "ImpliedLocale"
        disable += "LocaleFolder"
        disable += "MissingTranslation"
        disable += "MissingQuantity"
        disable += "MissingDefaultResource"
        disable += "MissingSuperCall"
        disable += "MissingPermission"
        disable += "MissingPrefix"
        disable += "MissingId"
        disable += "MissingConstraints"
        disable += "MissingConstraintsInConstraintLayout"
        disable += "MissingClass"
        disable += "MissingMethod"
        disable += "MissingField"
        disable += "MissingParameter"
        disable += "MissingTypeParameter"
        disable += "MissingTypeArguments"
        disable += "MissingExpression"
        disable += "MissingProperty"
        disable += "MissingGetter"
        disable += "MissingSetter"
        disable += "MissingConstructor"
        disable += "MissingInitializer"
        disable += "MissingDeclaration"
        disable += "MissingImport"
        disable += "MissingPackage"
        disable += "MissingModule"
        disable += "MissingFile"
        disable += "MissingDirectory"
        disable += "MissingResource"
        disable += "MissingDimension"
        disable += "MissingFlavor"
        disable += "MissingBuildType"
        disable += "MissingProductFlavor"
        disable += "MissingVariant"
        disable += "MissingApplication"
        disable += "MissingActivity"
        disable += "MissingService"
        disable += "MissingReceiver"
        disable += "MissingProvider"
        disable += "MissingPermission"
        disable += "MissingPermissionGroup"
        disable += "MissingPermissionTree"
        disable += "MissingUsesPermission"
        disable += "MissingUsesPermissionSdk23"
        disable += "MissingUsesPermissionSdkL"
        disable += "MissingUsesPermissionSdkM"
        disable += "MissingUsesPermissionSdkN"
        disable += "MissingUsesPermissionSdkO"
        disable += "MissingUsesPermissionSdkP"
        disable += "MissingUsesPermissionSdkQ"
        disable += "MissingUsesPermissionSdkR"
        disable += "MissingUsesPermissionSdkS"
        disable += "MissingUsesPermissionSdkT"
        disable += "MissingUsesPermissionSdkU"
        disable += "MissingUsesPermissionSdkV"
        disable += "MissingUsesPermissionSdkW"
        disable += "MissingUsesPermissionSdkX"
        disable += "MissingUsesPermissionSdkY"
        disable += "MissingUsesPermissionSdkZ"
        disable += "MissingUsesConfiguration"
        disable += "MissingUsesFeature"
        disable += "MissingUsesLibrary"
        disable += "MissingUsesNativeLibrary"
        disable += "MissingUsesOptionalLibrary"
        disable += "MissingUsesCleartextTraffic"
        disable += "MissingUsesNonSdkApi"
        disable += "MissingAllowBackup"
        disable += "MissingFullBackupContent"
        disable += "MissingDataExtractionRules"
        disable += "MissingNetworkSecurityConfig"
        disable += "MissingXmlResource"
        disable += "MissingDrawable"
        disable += "MissingLayout"
        disable += "MissingMenu"
        disable += "MissingAnimation"
        disable += "MissingAnimator"
        disable += "MissingInterpolator"
        disable += "MissingTransition"
        disable += "MissingColor"
        disable += "MissingDimen"
        disable += "MissingString"
        disable += "MissingStyle"
        disable += "MissingTheme"
        disable += "MissingAttr"
        disable += "MissingDeclareStyleable"
        disable += "MissingItem"
        disable += "MissingStyleable"
        disable += "MissingEnum"
        disable += "MissingFlag"
        disable += "MissingInteger"
        disable += "MissingBoolean"
        disable += "MissingFloat"
        disable += "MissingFraction"
        disable += "MissingIdResource"
        disable += "MissingNameResource"
        disable += "MissingTypeResource"
        disable += "MissingLayoutResource"
        disable += "MissingMenuResource"
        disable += "MissingDrawableResource"
        disable += "MissingStringResource"
        disable += "MissingColorResource"
        disable += "MissingDimenResource"
        disable += "MissingStyleResource"
        disable += "MissingThemeResource"
        disable += "MissingAnimResource"
        disable += "MissingAnimatorResource"
        disable += "MissingInterpolatorResource"
        disable += "MissingTransitionResource"
        disable += "MissingXmlResource"
        disable += "MissingRawResource"
        disable += "MissingAsset"
        disable += "MissingFont"
        disable += "MissingFontResource"
        disable += "MissingNavigation"
        disable += "MissingGraph"
        disable += "MissingDestination"
        disable += "MissingAction"
        disable += "MissingArgument"
        disable += "MissingDeepLink"
        disable += "MissingInclude"
        disable += "MissingNestedGraph"
        disable += "MissingActivityGraph"
        disable += "MissingFragmentGraph"
        disable += "MissingDialogGraph"
        disable += "MissingBottomSheetGraph"
        disable += "MissingNavigationGraph"
        disable += "MissingNavGraph"
        disable += "MissingNavHost"
        disable += "MissingNavController"
        disable += "MissingNavOptions"
        disable += "MissingNavDeepLink"
        disable += "MissingNavArgument"
        disable += "MissingNavType"
        disable += "MissingNavInflater"
        disable += "MissingNavDestination"
        disable += "MissingNavAction"
        disable += "MissingNavInclude"
        disable += "MissingNavNestedGraph"
        disable += "MissingNavActivityGraph"
        disable += "MissingNavFragmentGraph"
        disable += "MissingNavDialogGraph"
        disable += "MissingNavBottomSheetGraph"
        disable += "MissingNavNavigationGraph"
        disable += "MissingNavNavGraph"
        disable += "MissingNavNavHost"
        disable += "MissingNavNavController"
        disable += "MissingNavNavOptions"
        disable += "MissingNavNavDeepLink"
        disable += "MissingNavNavArgument"
        disable += "MissingNavNavType"
        disable += "MissingNavNavInflater"
        disable += "MissingNavNavDestination"
        disable += "MissingNavNavAction"
        disable += "MissingNavNavInclude"
        disable += "MissingNavNavNestedGraph"
        disable += "MissingNavNavActivityGraph"
        disable += "MissingNavNavFragmentGraph"
        disable += "MissingNavNavDialogGraph"
        disable += "MissingNavNavBottomSheetGraph"
        disable += "MissingNavNavNavigationGraph"
        disable += "MissingNavNavNavGraph"
        disable += "MissingNavNavNavHost"
        disable += "MissingNavNavNavController"
        disable += "MissingNavNavNavOptions"
        disable += "MissingNavNavNavDeepLink"
        disable += "MissingNavNavNavArgument"
        disable += "MissingNavNavNavType"
        disable += "MissingNavNavNavInflater"
        disable += "MissingNavNavNavDestination"
        disable += "MissingNavNavNavAction"
        disable += "MissingNavNavNavInclude"
        disable += "MissingNavNavNavNestedGraph"
        disable += "MissingNavNavNavActivityGraph"
        disable += "MissingNavNavNavFragmentGraph"
        disable += "MissingNavNavNavDialogGraph"
        disable += "MissingNavNavNavBottomSheetGraph"
        disable += "MissingNavNavNavNavigationGraph"
        disable += "MissingNavNavNavNavGraph"
        disable += "MissingNavNavNavNavHost"
        disable += "MissingNavNavNavNavController"
        disable += "MissingNavNavNavNavOptions"
        disable += "MissingNavNavNavNavDeepLink"
        disable += "MissingNavNavNavNavArgument"
        disable += "MissingNavNavNavNavType"
        disable += "MissingNavNavNavNavInflater"
        disable += "MissingNavNavNavNavDestination"
        disable += "MissingNavNavNavNavAction"
        disable += "MissingNavNavNavNavInclude"
        disable += "MissingNavNavNavNavNestedGraph"
        disable += "MissingNavNavNavNavActivityGraph"
        disable += "MissingNavNavNavNavFragmentGraph"
        disable += "MissingNavNavNavNavDialogGraph"
        disable += "MissingNavNavNavNavBottomSheetGraph"
        disable += "MissingNavNavNavNavNavigationGraph"
        disable += "MissingNavNavNavNavNavGraph"
        disable += "MissingNavNavNavNavNavHost"
        disable += "MissingNavNavNavNavNavController"
        disable += "MissingNavNavNavNavNavOptions"
        disable += "MissingNavNavNavNavNavDeepLink"
        disable += "MissingNavNavNavNavNavArgument"
        disable += "MissingNavNavNavNavNavType"
        disable += "MissingNavNavNavNavNavInflater"
        disable += "MissingNavNavNavNavNavDestination"
        disable += "MissingNavNavNavNavNavAction"
        disable += "MissingNavNavNavNavNavInclude"
        disable += "MissingNavNavNavNavNavNestedGraph"
        disable += "MissingNavNavNavNavNavActivityGraph"
        disable += "MissingNavNavNavNavNavFragmentGraph"
        disable += "MissingNavNavNavNavNavDialogGraph"
        disable += "MissingNavNavNavNavNavBottomSheetGraph"
        disable += "MissingNavNavNavNavNavNavigationGraph"
        disable += "MissingNavNavNavNavNavNavGraph"
        disable += "MissingNavNavNavNavNavNavHost"
        disable += "MissingNavNavNavNavNavNavController"
        disable += "MissingNavNavNavNavNavNavOptions"
        disable += "MissingNavNavNavNavNavNavDeepLink"
        disable += "MissingNavNavNavNavNavNavArgument"
        disable += "MissingNavNavNavNavNavNavType"
        disable += "MissingNavNavNavNavNavNavInflater"
        disable += "MissingNavNavNavNavNavNavDestination"
        disable += "MissingNavNavNavNavNavNavAction"
        disable += "MissingNavNavNavNavNavNavInclude"
        disable += "MissingNavNavNavNavNavNavNestedGraph"
        disable += "MissingNavNavNavNavNavNavActivityGraph"
        disable += "MissingNavNavNavNavNavNavFragmentGraph"
        disable += "MissingNavNavNavNavNavNavDialogGraph"
        disable += "MissingNavNavNavNavNavNavBottomSheetGraph"
        disable += "MissingNavNavNavNavNavNavNavigationGraph"
        disable += "MissingNavNavNavNavNavNavNavGraph"
        disable += "MissingNavNavNavNavNavNavNavHost"
        disable += "MissingNavNavNavNavNavNavNavController"
        disable += "MissingNavNavNavNavNavNavNavOptions"
        disable += "MissingNavNavNavNavNavNavNavDeepLink"
        disable += "MissingNavNavNavNavNavNavNavArgument"
        disable += "MissingNavNavNavNavNavNavNavType"
        disable += "MissingNavNavNavNavNavNavNavInflater"
        disable += "MissingNavNavNavNavNavNavNavDestination"
        disable += "MissingNavNavNavNavNavNavNavAction"
        disable += "MissingNavNavNavNavNavNavNavInclude"
        disable += "MissingNavNavNavNavNavNavNavNestedGraph"
        disable += "MissingNavNavNavNavNavNavNavActivityGraph"
        disable += "MissingNavNavNavNavNavNavNavFragmentGraph"
        disable += "MissingNavNavNavNavNavNavNavDialogGraph"
        disable += "MissingNavNavNavNavNavNavNavBottomSheetGraph"
        disable += "MissingNavNavNavNavNavNavNavNavigationGraph"
        disable += "MissingNavNavNavNavNavNavNavNavGraph"
        disable += "MissingNavNavNavNavNavNavNavNavHost"
        disable += "MissingNavNavNavNavNavNavNavNavController"
        disable += "MissingNavNavNavNavNavNavNavNavOptions"
        disable += "MissingNavNavNavNavNavNavNavNavDeepLink"
        disable += "MissingNavNavNavNavNavNavNavNavArgument"
        disable += "MissingNavNavNavNavNavNavNavNavType"
        disable += "MissingNavNavNavNavNavNavNavNavInflater"
        disable += "MissingNavNavNavNavNavNavNavNavDestination"
        disable += "MissingNavNavNavNavNavNavNavNavAction"
        disable += "MissingNavNavNavNavNavNavNavNavInclude"
        disable += "MissingNavNavNavNavNavNavNavNavNestedGraph"
        disable += "MissingNavNavNavNavNavNavNavNavActivityGraph"
        disable += "MissingNavNavNavNavNavNavNavNavFragmentGraph"
        disable += "MissingNavNavNavNavNavNavNavNavDialogGraph"
        disable += "MissingNavNavNavNavNavNavNavNavBottomSheetGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavigationGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavHost"
        disable += "MissingNavNavNavNavNavNavNavNavNavController"
        disable += "MissingNavNavNavNavNavNavNavNavNavOptions"
        disable += "MissingNavNavNavNavNavNavNavNavNavDeepLink"
        disable += "MissingNavNavNavNavNavNavNavNavNavArgument"
        disable += "MissingNavNavNavNavNavNavNavNavNavType"
        disable += "MissingNavNavNavNavNavNavNavNavNavInflater"
        disable += "MissingNavNavNavNavNavNavNavNavNavDestination"
        disable += "MissingNavNavNavNavNavNavNavNavNavAction"
        disable += "MissingNavNavNavNavNavNavNavNavNavInclude"
        disable += "MissingNavNavNavNavNavNavNavNavNavNestedGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavActivityGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavFragmentGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavDialogGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavBottomSheetGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavigationGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavHost"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavController"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavOptions"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavDeepLink"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavArgument"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavType"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavInflater"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavDestination"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavAction"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavInclude"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNestedGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavActivityGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavFragmentGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavDialogGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavBottomSheetGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavigationGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavHost"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavController"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavOptions"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavDeepLink"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavArgument"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavType"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavInflater"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavDestination"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavAction"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavInclude"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNestedGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavActivityGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavFragmentGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavDialogGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavBottomSheetGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavigationGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavHost"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavController"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavOptions"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDeepLink"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavArgument"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavType"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavInflater"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDestination"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavAction"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavInclude"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNestedGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavActivityGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavFragmentGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDialogGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavBottomSheetGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNavigationGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavHost"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavController"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavOptions"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDeepLink"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavArgument"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavType"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavInflater"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDestination"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavAction"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavInclude"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNestedGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavActivityGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavFragmentGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDialogGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavBottomSheetGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNavigationGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNavGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavHost"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavController"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavOptions"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDeepLink"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavArgument"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNavType"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavInflater"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDestination"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavAction"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavInclude"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNestedGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavActivityGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavFragmentGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDialogGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavBottomSheetGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNavigationGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNavGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavHost"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavController"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavOptions"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDeepLink"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavArgument"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavType"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavInflater"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDestination"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavAction"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavInclude"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNestedGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavActivityGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavFragmentGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDialogGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavBottomSheetGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNavigationGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNavGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavHost"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavController"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavOptions"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDeepLink"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavArgument"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavType"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavInflater"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDestination"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavAction"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavInclude"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNestedGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavActivityGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavFragmentGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDialogGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavBottomSheetGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNavigationGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNavGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavHost"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavController"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavOptions"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDeepLink"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavArgument"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavType"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavInflater"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDestination"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavAction"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavInclude"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNestedGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavActivityGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavFragmentGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDialogGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavBottomSheetGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNavigationGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNavGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavHost"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavController"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavOptions"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDeepLink"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavArgument"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavType"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavInflater"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDestination"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavAction"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavInclude"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNestedGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavActivityGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavFragmentGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDialogGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavBottomSheetGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNavigationGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNavGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavHost"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavController"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavOptions"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDeepLink"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavArgument"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavType"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavInflater"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDestination"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavAction"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavInclude"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNestedGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavActivityGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavFragmentGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDialogGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavBottomSheetGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNavigationGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNavGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavHost"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavController"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavOptions"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDeepLink"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavArgument"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavType"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavInflater"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDestination"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavAction"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavInclude"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNestedGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavActivityGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavFragmentGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDialogGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavBottomSheetGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNavigationGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNavGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavHost"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavController"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavOptions"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDeepLink"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavArgument"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavType"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavInflater"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDestination"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavAction"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavInclude"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNestedGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavActivityGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavFragmentGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDialogGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavBottomSheetGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNavigationGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNavGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavHost"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavController"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavOptions"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDeepLink"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavArgument"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavType"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavInflater"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDestination"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavAction"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavInclude"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNestedGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavActivityGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavFragmentGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDialogGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavBottomSheetGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNavigationGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNavGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavHost"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavController"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavOptions"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDeepLink"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavArgument"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavType"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavInflater"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDestination"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavAction"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavInclude"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNestedGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavActivityGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavFragmentGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDialogGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavBottomSheetGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNavigationGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNavGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavHost"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavController"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavOptions"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDeepLink"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavArgument"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavType"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavInflater"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDestination"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavAction"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavInclude"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNestedGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavActivityGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavFragmentGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDialogGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavBottomSheetGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNavigationGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNavGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavHost"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavController"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavOptions"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDeepLink"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavArgument"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavType"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavInflater"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDestination"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavAction"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavInclude"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNestedGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavActivityGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavFragmentGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDialogGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavBottomSheetGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNavigationGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNavGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavHost"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavController"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavOptions"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDeepLink"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavArgument"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavType"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavInflater"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDestination"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavAction"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavInclude"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNestedGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavActivityGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavFragmentGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavDialogGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavBottomSheetGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNavigationGraph"
        disable += "MissingNavNavNavNavNavNavNavNavNavNavNavNavGraph"
        disable += "MissingNavNavNavNavNavNavNavNav