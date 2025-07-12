# JaBook

Modern audiobook player for Android devices, designed as a successor to discontinued audiobook applications. Built with Kotlin 2.2.x and featuring seamless RuTracker.net integration for discovering and downloading audiobooks via torrent protocol.

## Table of Contents

- [Features](#features)
- [Getting Started](#getting-started)
- [Usage](#usage)
- [Gradle Tasks](#gradle-tasks)
- [Testing & Quality](#testing--quality)
- [Architecture](#architecture)
- [Contributing](#contributing)
- [License](#license)
- [Changelog](#changelog)

## Features

- **Modern Material Design 3** user interface with dynamic theming
- **RuTracker.net integration** for audiobook discovery and downloading
- **Torrent-based downloading** with smart file extraction
- **ExoPlayer integration** for high-quality audio playback
- **Offline-first architecture** for uninterrupted listening experience
- **Smart library management** with automatic metadata extraction
- **Universal Android support** - works on all devices (API 23-34)
- **Privacy-focused** - no analytics or external tracking

## Getting Started

### Prerequisites

- JDK 17, Kotlin 2.2.x, Gradle 8.14+
- Android SDK with API 23-34
- Android Studio (recommended) or command line tools

### Quick Install

```bash
# Clone the repository
git clone <repository-url>
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

```kotlin
// Launch the application
adb shell am start -n com.jabook.app/.MainActivity

// Install APK directly
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Main Features

1. **Library Management**: Organize and browse your audiobook collection
2. **Discovery**: Search and download audiobooks from RuTracker.net
3. **Player**: Advanced audio playback with chapter navigation
4. **Downloads**: Monitor and manage torrent downloads

## Gradle Tasks

| Task | Description |
|------|-------------|
| `./gradlew build` | Build the application |
| `./gradlew test` | Run unit tests |
| `./gradlew check-all` | Run all quality checks (lint, detekt, tests) |
| `./gradlew ktlintCheck` | Check Kotlin code style |
| `./gradlew detekt` | Run static code analysis |
| `./gradlew installDebug` | Install debug APK on device |
| `./gradlew clean` | Clean build artifacts |

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

- **Minimum**: Android 6.0 (API 23) - covers ~98% of devices
- **Target**: Android 14 (API 34)
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
- Maximum line length: 120 characters

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for the latest changes and version history.

---

**Project Status**: In Development  
**Current Version**: 0.1.0  
**Supported Platforms**: Android 6.0+ (API 23-34)  
**Distribution**: Direct APK (sideloading) 