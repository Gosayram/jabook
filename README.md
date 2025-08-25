# JaBook

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-blue.svg)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-7.0%2B-green.svg)](https://developer.android.com/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)

Modern audiobook player for Android devices with RuTracker.net integration. Built with Kotlin 2.2.x, Jetpack Compose, and Material Design 3. Supports Android 6.0+ (API 23-35) with comprehensive offline-first architecture.

## Table of Contents

- [JaBook](#jabook)
  - [Table of Contents](#table-of-contents)
  - [Features](#features)
  - [Getting Started](#getting-started)
    - [Prerequisites](#prerequisites)
    - [Quick Install](#quick-install)
  - [Usage](#usage)
    - [Basic Operations](#basic-operations)
    - [Main Features](#main-features)
  - [Gradle Tasks](#gradle-tasks)
    - [APK Build Tasks](#apk-build-tasks)
  - [Testing \& Quality](#testing--quality)
  - [Architecture](#architecture)
    - [Technology Stack](#technology-stack)
    - [Module Structure](#module-structure)
    - [Supported Android Versions](#supported-android-versions)
  - [Contributing](#contributing)
    - [Code Style](#code-style)
  - [License](#license)
  - [Changelog](#changelog)

## Features

- **Modern Material Design 3** with dynamic theming (light/dark/auto)
- **RuTracker.net integration** for audiobook discovery and downloading
- **Torrent-based downloading** with smart file extraction and progress tracking
- **ExoPlayer integration** for high-quality audio playback with chapter navigation
- **Offline-first architecture** for uninterrupted listening experience
- **Smart library management** with automatic metadata extraction and organization
- **Universal Android support** - works on all devices (API 24-35)
- **Privacy-focused** - no analytics or external tracking
- **Advanced player features** - sleep timer, playback speed control, bookmarks
- **Comprehensive download management** with pause/resume capabilities

## Getting Started

### Prerequisites

- JDK 17, Kotlin 2.2.x, Gradle 8.14+
- Android SDK with API 24-35
- Android Studio (recommended) or command line tools

### Quick Install

```bash
# Clone the repository
git clone https://github.com/Gosayram/jabook.git
cd jabook

# Build the application
./gradlew build

# Install on connected device
./gradlew installDebug

# Run the application
./gradlew run
```

## Usage

### Basic Operations

```bash
# Launch the application
adb shell am start -n com.jabook.app/.MainActivity

# Install APK directly
adb install app/build/outputs/apk/debug/app-debug.apk

# Build APK with timestamp
./gradlew buildApk
```

### Main Features

1. **Library Management**: Organize and browse your audiobook collection with smart filtering
2. **Discovery**: Search and download audiobooks from RuTracker.net with real-time updates
3. **Player**: Advanced audio playback with chapter navigation, sleep timer, and speed control
4. **Downloads**: Monitor and manage torrent downloads with pause/resume functionality

## Gradle Tasks

| Task                     | Description                                  |
| ------------------------ | -------------------------------------------- |
| `./gradlew build`        | Build the application                        |
| `./gradlew test`         | Run unit tests                               |
| `./gradlew check-all`    | Run all quality checks (lint, detekt, tests) |
| `./gradlew ktlintCheck`  | Check Kotlin code style                      |
| `./gradlew detekt`       | Run static code analysis                     |
| `./gradlew installDebug` | Install debug APK on device                  |
| `./gradlew clean`        | Clean build artifacts                        |

### APK Build Tasks

| Task                        | Description                 |
| --------------------------- | --------------------------- |
| `./gradlew buildApk`        | Build debug APK to `bin/`   |
| `./gradlew buildReleaseApk` | Build release APK to `bin/` |
| `./gradlew cleanBin`        | Clean `bin/` directory      |

APK files are saved to `bin/` directory with timestamps in filenames.

## Testing & Quality

Run comprehensive tests across multiple Android versions:

```bash
# Run all tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Run quality checks
./gradlew check-all

# Generate coverage report
./gradlew jacocoTestReport
```

Quality tools configured:
- **Ktlint** for Kotlin code formatting
- **Detekt** for static code analysis
- **JaCoCo** for test coverage (target: ≥80%)
- **Unit tests** with MockK and Truth assertions

## Architecture

### Technology Stack

- **Language**: Kotlin 2.2.x targeting JVM 17
- **UI**: Jetpack Compose with Material Design 3
- **Architecture**: MVVM with Clean Architecture principles
- **Dependency Injection**: Hilt/Dagger
- **Database**: Room with SQLite
- **Audio**: ExoPlayer (Media3)
- **Networking**: Retrofit2 + OkHttp3
- **Image Loading**: Coil
- **State Management**: Kotlin Flow and StateFlow

### Module Structure

```
app/
├── core/
│   ├── network/          # RuTracker API client
│   ├── database/         # Room database entities
│   ├── torrent/          # Torrent download engine
│   ├── storage/          # File management
│   └── compat/           # Android version compatibility
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

### Supported Android Versions

- **Minimum**: Android 7.0 (API 24) - covers ~98% of devices
- **Target**: Android 15 (API 35)
- **Features**: Adaptive UI, runtime permissions, scoped storage

## Contributing

1. Follow the existing code style and architecture patterns
2. Write comprehensive tests for new features
3. Update documentation for API changes
4. Run `./gradlew check-all` before submitting

### Code Style

- Use 4 spaces for indentation
- Follow Kotlin naming conventions
- Write KDoc comments for public APIs
- Maximum line length: 140 characters

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for the latest changes and version history.

---

**Project Status**: In Development  
**Current Version**: 0.1.0  
**Supported Platforms**: Android 7.0+ (API 24-35)  
**Distribution**: Direct APK (sideloading)
