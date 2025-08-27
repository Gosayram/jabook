# JaBook Audiobook App - Project Overview

## Project Summary
JaBook is an offline-first audiobook streaming application that allows users to play audiobooks from RuTracker torrents without requiring external backend services. The app works without authorization (local library, playback, history, exports) but offers optional login for online search functionality. **Branded** with JaBook logo and custom palette (violet, beige, orange).

## Build Toolchain
- **Gradle**: 8.14.3 (wrapper)
- **AGP**: 8.12.1 (Android Gradle Plugin)
- **Kotlin**: 2.2.10 (uses `compilerOptions {}` instead of `kotlinOptions {}`)
- **JDK**: 17 (toolchain)

## Key Features
- **Offline-first architecture** - all logic runs on-device
- **No external backend required** - complete functionality without server dependencies
- **Optional RuTracker login** - only needed for online search (offline features work without auth)
- **Mirror/endpoint manager** - handles RuTracker domain changes and regional blocks
- **Unified User-Agent** - consistent browser fingerprint between WebView and OkHttp
- **Robust debug logging** - in-app viewer + file rotation + one-tap sharing
- **Background playback** - Media3 ExoPlayer with lock screen and headset controls
- **Cross-platform compatibility** - Android 7.0 (API 24) through 15 (API 35)
- **GitHub distribution** - per-ABI APKs for armeabi-v7a, arm64-v8a, x86, x86_64

## Brand Palette
- **Primary background**: Violet `#3E2A53`
- **Primary text/icons**: Beige `#E6D9C6`
- **Accent**: Orange `#E59C28`
- **Success**: Green `#6BBF59`
- **Error**: Red `#D75C58`
- **Warning**: Amber `#F2B950`
- **Secondary text/details**: Light beige `#CBBFAE`

## Logo & Icons
- **App icon**: JaBook logo (book + headphones)
- **Adaptive icons**: Violet background, orange book + beige headphones
- **Splash screen**: Violet background, centered logo, "JaBook" in beige, fade-in animation

## UI Themes

### SPA (Svelte + Vite) Theme
- **Background**: Violet (#3E2A53)
- **Text**: Beige (#E6D9C6)
- **Primary buttons**: Orange bg, Beige text
- **Secondary buttons**: Transparent bg, Beige text + border
- **Icons**: SVG monochrome, Beige → Orange on hover
- **Links/active states**: Orange

### Player UI
- **Background**: Violet
- **Progress**: Orange
- **Title**: Beige
- **Secondary info**: Light beige (#CBBFAE)
- **Controls**: Beige → Orange on press
- **Foreground notification**: Dark violet background, beige icons/text

### Debug UI (Logs)
- **Background**: Violet
- **Font**: Monospace
- **Level colors**: INFO — Beige; WARN — Amber; ERROR — Red; DEBUG — #B3A89A
- **Format**: NDJSON with UTC timestamps
- **Fields**: `ts`, `level`, `subsystem`, `msg`, `thread`, optional `cause`

## Technical Architecture

### Frontend
- **SPA Framework**: Svelte + Vite (compiled, tiny runtime)
- **Container**: Android WebView with WebViewAssetLoader
- **Local API**: REST server on `127.0.0.1:17171` for audio streaming and API endpoints

### Core Modules
```
/app/                    # Main application module
├── MainActivity.kt      # WebView setup, AssetLoader, UA bootstrap
├── PlayerService.kt     # Foreground service + MediaSession
├── DebugShareProvider.kt # FileProvider helper for logs
├── di/, ui/             # DI singletons, native UI screens if needed
├── assets/              # Svelte build (index.html, JS, CSS)
├── res/xml/file_paths.xml # FileProvider paths
├── res/xml/network_security_config.xml # allow 127.0.0.1 only
├── AndroidManifest.xml
├── proguard-rules.pro
└── build.gradle.kts

/core-net/              # HTTP client and networking
├── Http.kt             # OkHttp client, UA Interceptor, CookieJar

/core-endpoints/        # Mirror management
├── EndpointResolver.kt # mirrors, health-check, failover

/core-auth/             # Authentication
├── AuthService.kt      # WebView login (primary), OkHttp fallback

/core-parse/            # HTML parsing
├── Parser.kt           # jsoup mappers (search, topic details), charset detect

/core-torrent/          # Torrent handling
├── TorrentService.kt   # libtorrent4j wrapper (sequential, status)

/core-stream/           # Local HTTP server
├── LocalHttp.kt        # NanoHTTPD: /api/*, /stream/* (Range/206)

/core-player/           # Audio playback
├── Player.kt           # ExoPlayer + MediaSession

/core-logging/          # Debug logging
├── AppLog.kt           # NDJSON logs, rotation, sinks, share

/spa (source)           # Svelte source
├── src/, vite.config.ts, package.json
└── build → copied into /app/assets/

/packaging              # Release artifacts
├── icons/              # Logo SVG/PNG
└── release-notes.md
```

### Technology Stack
- **Frontend**: Svelte + Vite
- **HTTP Client**: OkHttp 5.1.0 + CookieJar
- **HTML Parsing**: jsoup 1.21.2
- **Torrents**: libtorrent4j 2.1.0-37 (sequential download)
- **Player**: AndroidX Media3 1.7.1 (ExoPlayer + MediaSession)
- **WebView**: androidx.webkit 1.13.0
- **Local Server**: NanoHTTPD 2.3.1
- **SDK**: minSdk=24, compileSdk=35, targetSdk=35
- **ABI**: armeabi-v7a, arm64-v8a, x86, x86_64 (with universal APK option)

## Key Technical Implementations

### 1. Unified User-Agent System
- **Source of truth**: `WebSettings.getDefaultUserAgent(context)`
- **Fallback**: `webView.settings.userAgentString` / `System.getProperty("http.agent")`
- **Persistence**: SharedPreferences-based `UserAgentRepository`
- **Synchronization**: WebView init updates repository, OkHttp reads from it
- **Refresh**: On app start to catch Chromium/WebView updates

### 2. Mirror/Endpoint Manager
- **Features**: Default + user-defined mirrors, health-check, failover
- **Validation**: Status codes, Content-Type, HTML signatures, RTT measurement
- **Auto failover**: On error, one retry
- **REST API**: `/api/endpoints`, `/api/endpoints/active`, `/api/endpoints/rehash`
- **UI**: Status list (✅/⚠️/⛔), add/remove/reset mirrors

### 3. Authorization System
- **Primary**: WebView login → cookies in app sandbox
- **Fallback**: OkHttp login; on captcha → prompt WebView login
- **Logout**: Clears CookieManager + CookieJar
- **Search restriction**: Online search disabled until logged in

### 4. Local HTTP API
```bash
POST /api/login                            # Authentication
GET  /api/me                              # User status
GET  /api/search?q=...                     # Search (requires login)
GET  /api/topic/{id}                       # Topic details (requires login)
POST /api/torrents                         # Add magnet/torrent
GET  /api/torrents/{id}                    # Progress, stats
DELETE /api/torrents/{id}                  # Remove torrent
GET  /stream/{id}                         # Audio stream (Range/206)
```

### 5. Debug Logging System
- **Format**: NDJSON with UTC timestamps
- **Fields**: `ts`, `level`, `subsystem`, `msg`, `thread`, optional `cause`
- **Levels**: TRACE, DEBUG, INFO, WARN, ERROR (DEBUG+TRACE gated by BuildConfig.DEBUG)
- **Colors**: INFO — Beige, WARN — Amber, ERROR — Red, DEBUG — #B3A89A
- **Sinks**: Logcat, rolling files, in-app Debug tab
- **Sharing**: Zip logs and share via Android Sharesheet

### 6. Build & Release System
- **Distribution**: GitHub Releases only (no Google Play)
- **APK Types**: Per-ABI APKs (armeabi-v7a, arm64-v8a, x86, x86_64) + Universal APK
- **Signing**: Managed via `keystore.properties` (excluded from VCS)
- **Build Tools**: Gradle + Makefile (no external shell scripts)
- **Commands**: `make debug`, `make apk`, `make splits`, `make aab`
- **GitHub Flow**: Tag `vX.Y.Z`, run `make splits` + `make apk`, attach artifacts

## RuTracker Parsing - Robust Implementation

### URLs (example for https://rutracker.me)
- **Search**: `/forum/tracker.php?nm=<query>&start=<offset>` (offset = (page-1)*50)
- **Topic**: `/forum/viewtopic.php?t=<id>`

### Selectors (multi-path)
- **Topics**: `a[href*="viewtopic.php?t="]` (fallback on `a.tLink`)
- **Torrent link**: `a[href*="download.php?id="]`
- **Magnet**: `a[href^="magnet:?"]`
- **Seeds/Leeches**: By classes (`seedmed`/`leechmed`) OR column headers OR cell order
- **Size**: Extract text, normalize (KiB/MiB/GiB) → bytes

### Character Encoding
- Try `Content-Type`/`<meta charset>` → jsoup
- If missing/broken → try UTF-8, then windows-1251 (manual decoding), then Jsoup.parse

### Anti-Bot/Cloudflare
- **Primary path**: WebView (passes challenge; cookies → CookieManager)
- **OkHttp**: Uses same UA and cookies → passes without challenge
- **On 403/503/login-redirect**: One failover and retry

### Resilience Features
- Rate-limiting (search/topic), exponential backoff
- Caching (disk, TTL: search 1h, topic 24h)
- Unit tests with HTML fixture snapshots

## Application Screens

### SPA (Svelte) Interface
1. **Home/Library**: Local items, history, continue listening, storage stats
2. **Search**: Disabled until logged in, then query with paging
3. **Topic Details**: Files list, sizes, magnet button for "Listen/Download"
4. **Player mini-bar**: Play/pause/seek controls
5. **Mirrors**: Status list, add/remove, health management
6. **Debug**: Live log tail, filters, share functionality
7. **Settings**: Theme, logs verbosity, UA view, data export

## Development Milestones

### M1 - Skeleton Runnable
- WebView + AssetLoader loads Svelte index
- UA repository + OkHttp interceptor
- Local REST server with basic endpoints
- Mirrors UI in SPA
- Branded theme implementation

### M2 - Auth + Search + Topic
- WebView login flow
- Real `/api/me` endpoint
- Search and topic details with auth checks
- Failover mechanisms
- Caching and rate-limiting

### M3 - Torrent + Stream + Player + Debug
- Torrent addition and sequential download
- Audio streaming with Range support
- ExoPlayer foreground service
- Complete logging system
- Data export functionality

### M4 - Release
- Adaptive icons + splash screen
- Per-ABI APKs
- Signing and GitHub distribution

## Acceptance Criteria
- ✅ Theme matches logo palette (violet, beige, orange)
- ✅ Adaptive icons & splash screen with JaBook logo
- ✅ UA synchronized across WebView & OkHttp
- ✅ Mirrors add/validate/switch with failover
- ✅ Auth optional; search locked until logged in
- ✅ Parser robust (multi-selectors, charset detect, cache, backoff)
- ✅ Torrent streaming works; ExoPlayer background playback
- ✅ Debug logging: NDJSON, rotation, Share
- ✅ Multi-ABI signed APKs (armeabi-v7a, arm64-v8a, x86, x86_64)
- ✅ GitHub Releases with release notes & artifacts

## Project Structure
```
/jabook
├── /app                    # Main application
├── /core-*                 # Core modules (net, endpoints, auth, etc.)
├── /spa                    # Svelte source
├── /packaging              # Release assets (icons, release notes)
├── gradle/libs.versions.toml
├── build.gradle.kts        # Root build configuration
├── settings.gradle.kts
├── Makefile                # Build targets
├── gradlew                 # Gradle wrapper (Unix)
├── gradlew.bat             # Gradle wrapper (Windows)
├── gradle/wrapper/         # Gradle wrapper files
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties
└── keystore.properties.example
```

This project represents a sophisticated branded offline-first audiobook application with modern Android development practices, modular architecture, comprehensive feature set for RuTracker-based content consumption, and GitHub-based distribution with multi-ABI APK support.