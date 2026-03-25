# JaBook

[![Android](https://img.shields.io/badge/Android-11+-green.svg)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-purple.svg)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Compose-1.7+-blue.svg)](https://developer.android.com/jetpack/compose)
[![Media3](https://img.shields.io/badge/Media3-1.9.3-orange.svg)](https://developer.android.com/media/media3)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)

**JaBook** is a modern Android audiobook player application with RuTracker integration. Built with **Kotlin 2.3.20**, **Jetpack Compose**, and **Media3 (ExoPlayer)** following Android development best practices.

> [!WARNING]
>
> ## ⚠️ Disclaimer
>
> - **The authors are not responsible** for any use of this application.
> - **This application is not affiliated with RuTracker** and has no connection to it.
> - **Use this application at your own risk.**
> - The authors are not responsible for any content downloaded through the application.
> - The application is provided "as is" without any warranties.
> - The user is fully responsible for compliance with copyright laws in their jurisdiction.

---

## Table of Contents

- [JaBook](#jabook)
  - [Table of Contents](#table-of-contents)
  - [Features](#features)
  - [Tech Stack](#tech-stack)
  - [Getting Started](#getting-started)
    - [Prerequisites](#prerequisites)
    - [Installation](#installation)
  - [Usage](#usage)
    - [Build Commands](#build-commands)
    - [Main Features](#main-features)
  - [Architecture](#architecture)
    - [Module Structure](#module-structure)
    - [Key Components](#key-components)
  - [Testing & Quality](#testing--quality)
  - [Contributing](#contributing)
  - [License](#license)
  - [Changelog](#changelog)

---

## Features

- **Jetpack Compose UI** with Material Design 3 and dynamic theme switching (Light/Dark/AMOLED)
- **RuTracker integration** for audiobook discovery and downloading with automatic mirror switching
- **Torrent downloads** via libtorrent4j with sequential playback support
- **Media3 (ExoPlayer) audio player** with:
  - Advanced audio processors (normalization, compression, speech enhancement)
  - Crossfade between tracks
  - Sleep timer (fixed duration + end of track)
  - Background playback with MediaSession
  - Android Auto support
- **Offline-first architecture** with Room database
- **Advanced navigation** with type-safe routes and deep links
- **Hilt Dependency Injection** for clean architecture
- **Proto DataStore** for type-safe settings
- **Multi-language support** (Russian/English)
- **Android TV support** via Leanback
- **Home screen widget** for player control

---

## Tech Stack

| Category          | Technology                   | Version       |
| ----------------- | ---------------------------- | ------------- |
| **Language**      | Kotlin                       | 2.3.20        |
| **UI**            | Jetpack Compose + Material 3 | 1.7+          |
| **DI**            | Hilt                         | 2.59.2        |
| **Database**      | Room                         | 2.8.4         |
| **Preferences**   | Proto DataStore              | 1.2.0         |
| **Audio Player**  | Media3 (ExoPlayer)           | 1.9.3         |
| **Torrent**       | libtorrent4j                 | 2.1.0-39      |
| **Network**       | OkHttp + Retrofit            | 5.3.2 / 3.0.0 |
| **Parsing**       | Jsoup                        | 1.21.2        |
| **Serialization** | kotlinx.serialization        | 1.10.0        |
| **Image Loading** | Coil3 + Glide                | 3.3.0 / 5.0.5 |
| **Navigation**    | Navigation Compose           | 2.9.7         |
| **Encryption**    | Tink                         | 1.20.0        |
| **Analytics**     | Firebase                     | 34.11.0 (BoM) |

**Minimum Android Version:** API 30 (Android 11)  
**Target Android Version:** API 36 (Android 16)

---

## Getting Started

### Prerequisites

- **Android Studio** Hedgehog or newer
- **JDK 21** (Java 21 toolchain)
- **Android SDK** with API 30+
- **Git** for cloning the repository

### Installation

```bash
# Clone the repository
git clone git@github.com:Gosayram/jabook.git
cd jabook

# Open in Android Studio
open android/

# Or build from command line
cd android
./gradlew assembleBetaDebug

# Run on device
./gradlew installBetaDebug
```

---

## Usage

### Build Commands

| Command                          | Description                     |
| -------------------------------- | ------------------------------- |
| `./gradlew assembleDebug`        | Build debug APK for all flavors |
| `./gradlew assembleBetaDebug`    | Build beta debug APK            |
| `./gradlew assembleProdRelease`  | Build production release APK    |
| `./gradlew test`                 | Run unit tests                  |
| `./gradlew connectedAndroidTest` | Run instrumented tests          |
| `./gradlew jacocoTestReport`     | Generate JaCoCo coverage report |
| `./gradlew ktlintCheck`          | Check code with ktlint          |
| `./gradlew ktlintFormat`         | Format code with ktlint         |

**Makefile commands** (from project root):

| Command                    | Description                       |
| -------------------------- | --------------------------------- |
| `make install`             | Install dependencies              |
| `make clean`               | Clean build artifacts             |
| `make build-android-dev`   | Build dev variant                 |
| `make build-android-stage` | Build stage variant               |
| `make build-android-beta`  | Build beta variant                |
| `make build-android-prod`  | Build production variant          |
| `make test`                | Run all tests                     |
| `make analyze`             | Analyze code                      |
| `make fmt`                 | Format code                       |
| `make l10n`                | Generate localization files       |
| `make check-l10n`          | Check localization for duplicates |

### Main Features

1. **Library** — Manage local audiobook library with automatic scanning
2. **Search** — Search for audiobooks on RuTracker with result caching
3. **Player** — Advanced playback with:
   - Chapter/track navigation
   - Speed control (0.5x–3.0x)
   - Sleep timer
   - Crossfade
   - Audio processors
4. **Downloads** — Torrent download manager with pause/resume
5. **Favorites** — Quick access to favorite books
6. **Settings** — App configuration (theme, font, audio, mirrors)
7. **Android Auto** — In-car playback support

---

## Architecture

### Module Structure

```
android/app/src/main/kotlin/com/jabook/app/jabook/
├── audio/                          # Media3 audio player
│   ├── core/                       # Base models and Result types
│   ├── data/                       # Data layer (DAO, Repository, DataStore)
│   ├── domain/                     # Domain layer (UseCases, Mappers)
│   ├── player/                     # ExoPlayer managers
│   ├── processors/                 # AudioProcessors (normalization, compression)
│   └── session/                    # MediaSession integration
│
├── compose/                        # Jetpack Compose UI and features
│   ├── data/                       # Data layer (API, Repository, Parser)
│   ├── designsystem/               # Design System components
│   ├── di/                         # Hilt DI modules
│   ├── domain/                     # Domain layer (Models, UseCases)
│   ├── feature/                    # Feature modules (auth, library, player, search...)
│   ├── navigation/                 # Navigation graph and routes
│   └── infrastructure/             # Infrastructure (notification, sync)
│
├── crash/                          # Crash handling
├── download/                       # Download services (non-torrent)
├── indexing/                       # Indexing services
├── migration/                      # Data migrations
├── torrent/                        # Torrent manager (libtorrent4j)
├── tv/                             # Android TV (Leanback)
├── ui/                             # Theme (Color, Type, Theme)
├── util/                           # Utilities
└── widget/                         # Home screen widget
```

### Key Components

| Component           | File                                                                                                                     | Description                |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------ | -------------------------- |
| **Application**     | [`JabookApplication.kt`](android/app/src/main/kotlin/com/jabook/app/jabook/JabookApplication.kt)                         | Hilt application class     |
| **Main Activity**   | [`ComposeMainActivity.kt`](android/app/src/main/kotlin/com/jabook/app/jabook/compose/ComposeMainActivity.kt)             | Main Compose Activity      |
| **Audio Service**   | [`AudioPlayerService.kt`](android/app/src/main/kotlin/com/jabook/app/jabook/audio/AudioPlayerService.kt)                 | Media3 MediaLibraryService |
| **Torrent Manager** | [`TorrentManager.kt`](android/app/src/main/kotlin/com/jabook/app/jabook/torrent/TorrentManager.kt)                       | libtorrent4j manager       |
| **Auth Service**    | [`RutrackerAuthService.kt`](android/app/src/main/kotlin/com/jabook/app/jabook/compose/data/auth/RutrackerAuthService.kt) | RuTracker authentication   |
| **Database**        | [`JabookDatabase.kt`](android/app/src/main/kotlin/com/jabook/app/jabook/compose/data/local/JabookDatabase.kt)            | Room database (v15)        |
| **Navigation**      | [`JabookNavHost.kt`](android/app/src/main/kotlin/com/jabook/app/jabook/compose/navigation/JabookNavHost.kt)              | Navigation graph           |

---

## Testing & Quality

**Test Coverage:** 85%+ (JaCoCo)

```bash
# Unit tests
./gradlew testBetaDebugUnitTest

# Instrumented tests
./gradlew connectedBetaDebugAndroidTest

# JaCoCo report
./gradlew jacocoTestReport

# Coverage verification
./gradlew jacocoCoverageVerification

# Ktlint check
./gradlew ktlintCheck

# Ktlint format
./gradlew ktlintFormat
```

**Quality tools:**

- **ktlint** 14.2.0 — linter and formatter
- **JaCoCo** 0.8.14 — test coverage
- **Android Lint** — static analysis
- **Detekt** (optional) — Kotlin static analysis

---

## Contributing

1. Follow the existing code style and architectural patterns
2. Write comprehensive tests for new features
3. Update documentation for API changes
4. Run `./gradlew ktlintCheck` and `./gradlew test` before committing
5. Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)

### Code Style

- **Indentation:** 4 spaces (Kotlin standard)
- **Naming:** camelCase for functions/variables, PascalCase for classes
- **Documentation:** KDoc for public API
- **Line length:** 140 characters

---

## License

This project is licensed under the **Apache License 2.0** — see the [LICENSE](LICENSE) file for details.

```
Copyright 2026 JaBook Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for the latest changes and version history.

---

## Repository

- **Remote:** `git@github.com:Gosayram/jabook.git`
- **GitHub:** [https://github.com/Gosayram/jabook](https://github.com/Gosayram/jabook)

---

**Project Status**: In Active Development  
**Current Version**: 1.2.7+96  
**Supported Platforms**: Android 11+ (API 30+)  
**Distribution**: Direct APK (sideloading)
