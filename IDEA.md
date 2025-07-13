# JaBook - Modern Audiobook Player for Android

## Project Overview

JaBook is a modern Android application for audiobook listening, designed as a successor to the discontinued "аудиокниги-торрент" application. Built with Kotlin 2.2.x and targeting Android devices, it provides seamless integration with RuTracker.net for discovering and downloading audiobooks via torrent protocol.

## Table of Contents

1. [Project Overview](#project-overview)
2. [Core Features](#core-features)
3. [Technical Architecture](#technical-architecture)
4. [User Interface Design](#user-interface-design)
5. [RuTracker Integration](#rutracker-integration)
6. [Torrent Module](#torrent-module)
7. [File Management](#file-management)
8. [Debug & Logging](#debug--logging)
9. [Development Roadmap](#development-roadmap)

## Core Features

### Primary Features
- **Modern audiobook player** with intuitive controls
- **RuTracker.net integration** for audiobook discovery
- **Torrent-based downloading** with proper file extraction
- **Smart library management** with metadata support
- **Offline-first architecture** for uninterrupted listening
- **Beautiful Material Design 3** user interface

### Secondary Features
- **Bookmarks and progress tracking**
- **Sleep timer and playback speed control**
- **Background playback** with notification controls
- **Search and filtering** within library
- **Categories and genre organization**
- **Download queue management**

## Technical Architecture

### Technology Stack
- **Language**: Kotlin 2.2.x
- **Target SDK**: Android 14 (API 34)
- **Minimum SDK**: Android 6.0 (API 23) - covers ~98% of devices
- **Compile SDK**: Android 14 (API 34)
- **Architecture**: MVVM with Clean Architecture
- **DI**: Hilt/Dagger (with compatibility layer for older versions)
- **Database**: Room with SQLite
- **Networking**: Retrofit2 + OkHttp3
- **UI**: Jetpack Compose with View fallbacks for older devices
- **Coroutines**: kotlinx.coroutines for async operations
- **Services**: Basic GMS/HMS compatibility for device support

### Core Modules
```
app/
├── core/
│   ├── network/          # RuTracker API client
│   ├── database/         # Room database entities
│   ├── torrent/          # Torrent download engine
│   ├── storage/          # File management
│   └── compat/           # Compatibility layer for different Android versions
├── features/
│   ├── library/          # Book library & organization
│   ├── player/           # Audio player functionality
│   ├── discovery/        # RuTracker browsing
│   └── downloads/        # Download management
└── shared/
    ├── ui/               # Common UI components
    ├── utils/            # Utilities & extensions
    └── debug/            # Debug tools & logging
```



### Gradle Configuration
```kotlin
android {
    compileSdk 34
    
    defaultConfig {
        minSdk 23                    // Android 6.0 - wide device coverage
        targetSdk 34                 // Latest stable Android
        versionCode 1
        versionName "0.1.0"
        
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }
    
    buildFeatures {
        compose = true
        viewBinding = true           // For legacy View fallbacks
        buildConfig = true
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }
}

dependencies {
    // Core Android dependencies
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui:$compose_version")
    implementation("androidx.compose.material3:material3:1.1.2")
    
    // Navigation & Architecture
    implementation("androidx.navigation:navigation-compose:2.7.6")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    implementation("com.google.dagger:hilt-android:2.48")
    
    // Database & Storage
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    
    // Media & Audio
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    
    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Image loading
    implementation("io.coil-kt:coil-compose:2.5.0")
}
```

## User Interface Design

### Main Screens (Based on Reference App)

#### 1. Library Screen
- **Grid/List view** of audiobooks with covers
- **Categories**: Фантастика, Детективы, Классика, etc.
- **Search bar** for quick filtering
- **Sort options**: by title, author, date added, progress

#### 2. Player Screen
- **Large cover art** display
- **Playback controls**: play/pause, next/previous chapter
- **Progress bar** with time indicators
- **Chapter navigation**
- **Speed control** (0.5x to 3.0x)
- **Sleep timer** functionality

#### 3. Discovery Screen (RuTracker Integration)
- **Category browser** mirroring RuTracker structure
- **Search functionality**
- **Book details** with description, size, quality info
- **Download button** with progress indicator

#### 4. Downloads Screen
- **Active downloads** with progress bars
- **Download queue** management
- **Completed downloads** status
- **Storage usage** information

## RuTracker Integration

### Target Categories Analysis
Based on RuTracker structure (https://rutracker.net/forum/index.php?c=33):

#### Primary Categories:
- **Художественная литература** (Fiction)
  - Фантастика, фэнтези, мистика, ужасы, фанфики
  - Детективы, триллеры, боевики
  - Исторические романы
  - Классическая литература
  - Современная литература

- **Познавательная литература** (Educational)
  - Научно-популярная литература
  - История, биографии
  - Психология, философия

- **Детская литература** (Children's Books)
  - Сказки и детские книги
  - Подростковая литература

### Parser Implementation Strategy

```kotlin
data class AudiobookInfo(
    val id: String,
    val title: String,
    val author: String,
    val narrator: String?,
    val description: String,
    val category: String,
    val size: Long,
    val duration: String?,
    val quality: String,
    val torrentUrl: String,
    val coverUrl: String?,
    val seeders: Int,
    val leechers: Int
)

interface RuTrackerParser {
    suspend fun getCategories(): List<Category>
    suspend fun getAudiobooks(categoryId: String, page: Int): List<AudiobookInfo>
    suspend fun searchAudiobooks(query: String): List<AudiobookInfo>
    suspend fun getAudiobookDetails(id: String): AudiobookInfo
}
```

## Torrent Module

### Core Functionality
- **LibTorrent integration** for torrent downloading
- **Smart file detection** (identify audio files from archives)
- **Automatic extraction** of compressed audiobooks
- **Download progress tracking**
- **Bandwidth limiting** and scheduling

### Implementation Approach
```kotlin
interface TorrentManager {
    suspend fun addTorrent(magnetUri: String): TorrentHandle
    suspend fun pauseTorrent(torrentId: String)
    suspend fun resumeTorrent(torrentId: String)
    suspend fun removeTorrent(torrentId: String, deleteFiles: Boolean)
    fun getTorrentProgress(torrentId: String): Flow<DownloadProgress>
}

data class DownloadProgress(
    val torrentId: String,
    val progress: Float,
    val downloadSpeed: Long,
    val uploadSpeed: Long,
    val eta: Long,
    val status: TorrentStatus
)
```

## File Management

### Storage Organization
```
/Android/data/com.jabook.app/files/
├── audiobooks/
│   ├── [AuthorName]/
│   │   └── [BookTitle]/
│   │       ├── metadata.json
│   │       ├── cover.jpg
│   │       └── audio/
│   │           ├── chapter_01.mp3
│   │           ├── chapter_02.mp3
│   │           └── ...
├── temp/                 # Temporary download files
├── cache/               # Cached covers and metadata
└── logs/                # Debug logs
```

### Metadata Management
- **Automatic metadata extraction** from audio files
- **Cover art downloading** and caching
- **Progress persistence** across app restarts
- **Bookmark synchronization**

## Debug & Logging

### Debug Features
- **Comprehensive logging** with different log levels
- **Network request/response logging**
- **Torrent download debugging**
- **Performance metrics** tracking
- **Local crash logging** without external services

### Debug Tools
```kotlin
object DebugLogger {
    fun logNetworkRequest(request: String, response: String)
    fun logTorrentEvent(event: TorrentEvent)
    fun logPlaybackEvent(event: PlaybackEvent)
    fun logError(error: Throwable, context: String)
}

// Debug panel for development
interface DebugPanel {
    fun showNetworkLogs()
    fun showTorrentStats()
    fun clearCache()
    fun exportLogs()
}
```

## Development Roadmap

### Phase 1: Foundation
- [ ] Project setup with Kotlin 2.2.x and multi-API support (23-34)
- [ ] Basic MVVM architecture implementation
- [ ] Room database setup for audiobook metadata
- [ ] Basic UI components with Jetpack Compose + View fallbacks
- [ ] Compatibility layer setup for older Android versions

### Phase 2: Core Features
- [ ] Audio player implementation with ExoPlayer
- [ ] File storage and organization system
- [ ] Basic library management (add/remove/organize)
- [ ] Simple torrent integration

### Phase 3: RuTracker Integration
- [ ] RuTracker parser implementation
- [ ] Category browsing and search
- [ ] Audiobook discovery interface
- [ ] Download queue management

### Phase 4: Advanced Features
- [ ] Advanced player features (speed control, sleep timer)
- [ ] Bookmark and progress tracking
- [ ] Background playback with notifications
- [ ] Performance optimization

### Phase 5: Polish & Release
- [ ] UI/UX improvements and animations
- [ ] Comprehensive testing on multiple Android versions (API 23-34)
- [ ] Device compatibility testing (phones, tablets, different screen sizes)
- [ ] Cross-device testing (Samsung, Xiaomi, OnePlus, Huawei, etc.)
- [ ] Debug tools refinement
- [ ] Documentation and release preparation
- [ ] APK preparation for sideloading distribution

## Technical Considerations

### Android Version Compatibility
- **API 23-34 Support** - Wide device coverage from Android 6.0 to Android 14
- **Adaptive UI** - Different layouts for various screen sizes and densities
- **Feature detection** - Graceful degradation for missing APIs
- **Backward compatibility** - Polyfills and compatibility libraries where needed
- **Permission handling** - Runtime permissions (API 23+) with fallbacks
- **Background restrictions** - Doze mode and App Standby handling
- **Notification channels** - Android 8.0+ with fallback for older versions

### Device Compatibility
- **Universal Android support** - works on all Android devices (Samsung, Xiaomi, OnePlus, Huawei, etc.)
- **No external services dependency** - fully offline application
- **Local storage only** - all data stored on device
- **No analytics or tracking** - privacy-focused approach

### Version-Specific Features
#### Android 6.0+ (API 23)
- **Runtime Permissions** for storage and network access
- **Doze Mode optimizations** for background downloading
- **App Data backup** for user preferences

#### Android 8.0+ (API 26)
- **Notification channels** for organized notifications
- **Background service limits** - use foreground services for downloads
- **Adaptive icons** support

#### Android 10+ (API 29)
- **Scoped storage** compatibility
- **Dark theme** system integration
- **Gesture navigation** support

#### Android 12+ (API 31)
- **Material You** dynamic theming
- **Notification trampolines** restrictions
- **Approximate location** permissions

#### Android 13+ (API 33)
- **Notification runtime permission**
- **Themed app icons**
- **Per-app language preferences**



### Security & Legal
- **No direct torrent hosting** - only indexing from public trackers
- **User responsibility** for content legality
- **Optional VPN integration** suggestions
- **Clear disclaimers** about content usage
- **Privacy-first approach** - no data collection or external analytics

### Performance Optimization
- **Lazy loading** for large audiobook lists
- **Image caching** with Glide/Coil
- **Background processing** for file operations
- **Memory management** for audio playback
- **API-level optimizations** - use newer APIs when available

### Accessibility
- **Screen reader support** (TalkBack compatibility)
- **Large text options** for visually impaired users
- **High contrast themes** for better visibility
- **Voice control integration** with Android accessibility services

## Device Profiles & UI Adaptation

### Target Device Profiles

The application UI is optimized for a wide range of Android devices, with special attention to popular models and modern flagships. Below are reference device profiles for layout and density testing:

#### Samsung Galaxy S23 FE (Exynos)
- **Display:** 6.4" Dynamic AMOLED 2X, FHD+ (1080x2340), 401 ppi, 19.5:9 aspect ratio, 120Hz adaptive
- **Dimensions:** 158.0 x 76.5 x 8.2 mm
- **Density bucket:** xxhdpi (~401 ppi)
- **OS:** Android 13/14, One UI 5.1+
- **Notes:** Rounded corners, punch-hole camera, high brightness, always-on display

#### Other Common Profiles
- **Google Pixel 7:** 6.3" FHD+ (1080x2400), 416 ppi, 20:9, Android 13/14, xxhdpi
- **Xiaomi Redmi Note 12:** 6.67" FHD+ (1080x2400), 395 ppi, 20:9, Android 13/14, xxhdpi
- **OnePlus 11:** 6.7" QHD+ (1440x3216), 525 ppi, 20:9, Android 13/14, xxxhdpi
- **Samsung Galaxy Tab S8:** 11" WQXGA (2560x1600), 274 ppi, 16:10, Android 13/14, xhdpi

### UI/UX Adaptation Guidelines
- **Spacing:** Use minimum 16dp horizontal/vertical padding for all screen edges. Increase to 24dp+ on tablets.
- **Buttons:** Height ≥ 48dp, width ≥ 88dp, with 12-16dp spacing between buttons.
- **Typography:** Use scalable sp units. Main titles: 22-28sp, subtitles: 16-20sp, body: 14-16sp.
- **Touch targets:** Minimum 48x48dp for all interactive elements.
- **Empty states:** Centered, with clear iconography and concise text. Add extra vertical space for visual comfort.
- **Navigation bar:** Height 56dp, icons 24-28dp, labels 12-14sp.
- **Adaptive layouts:** Use ConstraintLayout or Compose Box/Column/Row with Modifier.padding(WindowInsets) for cutouts and rounded corners.
- **Dark/Light theme:** All screens must support both themes with proper contrast and color roles.
- **Density buckets:** Test on at least mdpi, xhdpi, xxhdpi, xxxhdpi.

### Device Testing Recommendations
- Use Android Studio emulators for the above profiles.
- Validate on at least one Samsung flagship (S23 FE or newer), one Pixel, and one Xiaomi/OnePlus device.
- Check for cutout/punch-hole overlap, navigation bar overlap, and correct scaling of icons/text.

## Theme Switcher (Light/Dark Mode)

### Requirements
- The app must provide a visible toggle for switching between light and dark themes.
- Recommended placement: top-right corner of the main screens or in the app settings.
- The toggle should use a recognizable icon (e.g., sun/moon or system theme icon).
- User choice must be persisted (e.g., SharedPreferences or DataStore).
- Theme change must apply instantly without app restart.
- Default: follow system theme unless user overrides.

### Implementation Notes
- Use MaterialTheme (Compose) or AppCompatDelegate (View) for theme switching.
- All custom colors must be defined in colors.xml and support both light/dark palettes.
- Test for color contrast and accessibility in both modes.
- Provide KDoc and usage example for the theme switcher component.

---

**Target Platforms**: Android 6.0+ (API 23-34, covers ~98% of devices)  
**Target Devices**: All Android devices (Samsung, Xiaomi, OnePlus, Huawei, Honor, etc.)  
**Distribution**: Direct APK (sideloading)  
**License**: Apache 2.0 