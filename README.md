# JaBook

[![Flutter](https://img.shields.io/badge/Flutter-3.35.3-blue.svg)](https://flutter.dev/)
[![Dart](https://img.shields.io/badge/Dart-3.2.0+-blue.svg)](https://dart.dev/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)

Modern audiobook player for Android devices with RuTracker.net integration. Built with Flutter 3.35.3 and Dart 3.2.0+. Features offline-first architecture with torrent-based downloading, advanced player controls, and comprehensive library management.

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
  - [Build Commands](#build-commands)
  - [Testing \& Quality](#testing--quality)
  - [Architecture](#architecture)
    - [Technology Stack](#technology-stack)
    - [Module Structure](#module-structure)
    - [Supported Platforms](#supported-platforms)
  - [Contributing](#contributing)
    - [Code Style](#code-style)
  - [License](#license)
  - [Changelog](#changelog)

## Features

- **Modern Material Design 3** with dynamic theming (light/dark/auto)
- **RuTracker.net integration** for audiobook discovery and downloading with dynamic mirror management
- **Torrent-based downloading** with smart file extraction and progress tracking using dtorrent library
- **Advanced audio player** with chapter navigation, playback speed control, sleep timer, and background playback
- **Offline-first architecture** for uninterrupted listening experience
- **Smart library management** with automatic metadata extraction and organization
- **Search functionality** with history and caching for fast results
- **Favorites system** for quick access to preferred audiobooks
- **Multi-language support** (English and Russian)
- **Privacy-focused** - no analytics or external tracking
- **Comprehensive download management** with pause/resume capabilities
- **Backup and restore** functionality for user data
- **Endpoint health monitoring** with automatic failover to working mirrors

## Getting Started

### Prerequisites

- Flutter SDK 3.35.3 or higher
- Dart SDK 3.2.0 or higher
- Android SDK (API 24+) for Android development
- Xcode (for iOS development, optional)

### Quick Install

```bash
# Clone the repository
git clone https://github.com/Gosayram/jabook.git
cd jabook

# Install dependencies
flutter pub get

# Run the application
flutter run

# Or build for Android
flutter build apk --release
```

## Usage

### Basic Operations

```bash
# Install dependencies
make install
# or
flutter pub get

# Run the app
flutter run

# Build Android APK (dev)
make build-android-dev

# Build Android APK (production)
make build-android-prod

# Run tests
make test
# or
flutter test

# Format code
make fmt
# or
dart format .

# Analyze code
make analyze
# or
flutter analyze
```

### Main Features

1. **Library Management**: Organize and browse your audiobook collection with smart filtering
2. **Search**: Search and download audiobooks from RuTracker.net with real-time updates and caching
3. **Player**: Advanced audio playback with chapter navigation, sleep timer, speed control, and background playback
4. **Favorites**: Save and quickly access your favorite audiobooks
5. **Downloads**: Monitor and manage torrent downloads with pause/resume functionality
6. **Settings**: Configure mirrors, language, permissions, and app preferences
7. **Mirrors**: Manage RuTracker mirrors with automatic health checking and failover

## Build Commands

The project includes a Makefile for convenient build operations:

| Command                    | Description                       |
| -------------------------- | --------------------------------- |
| `make install`             | Install Flutter dependencies      |
| `make clean`               | Clean build artifacts             |
| `make build-android-dev`   | Build Android dev variant         |
| `make build-android-stage` | Build Android stage variant       |
| `make build-android-prod`  | Build Android production variant  |
| `make test`                | Run all tests                     |
| `make test-unit`           | Run unit tests only               |
| `make analyze`             | Run Flutter analysis              |
| `make fmt`                 | Format code                       |
| `make l10n`                | Generate localization files       |
| `make release-android`     | Build all signed Android variants |

For iOS builds, see `make help` for available iOS commands.

## Testing & Quality

Run comprehensive tests:

```bash
# Run all tests
make test
# or
flutter test

# Run unit tests only
make test-unit

# Run widget tests
make test-widget

# Run integration tests
make test-integration

# Analyze code
make analyze

# Format code
make fmt
```

Quality tools configured:
- **Flutter Lints** for Dart code analysis
- **Flutter Test** for unit and widget testing
- **Integration Test** for end-to-end testing
- **Code formatting** with `dart format`
- **Localization** with `flutter gen-l10n`

Unit tests are available for:
- Cache management
- Endpoint management
- RuTracker parsing
- Category parsing
- CloudFlare utilities
- User agent management
- Safe async operations
- Notification utilities
- Bluetooth utilities
- First launch helpers

## Architecture

### Technology Stack

- **Framework**: Flutter 3.35.3
- **Language**: Dart 3.2.0+
- **State Management**: Flutter Riverpod
- **Routing**: GoRouter
- **Database**: Sembast (NoSQL document store)
- **Audio Playback**: Just Audio with Audio Service for background playback
- **Networking**: Dio with cookie management
- **Torrent**: dtorrent library suite
- **WebView**: flutter_inappwebview for RuTracker authentication
- **Localization**: Flutter Localizations (ARB files)
- **Image Loading**: cached_network_image
- **Storage**: flutter_secure_storage for sensitive data

### Module Structure

```
lib/
├── app/                      # Application entry point and configuration
│   ├── app.dart              # Main app widget
│   ├── router/               # Navigation configuration
│   └── theme/                # Theme configuration
├── core/                     # Core functionality
│   ├── auth/                 # RuTracker authentication
│   ├── backup/               # Backup and restore service
│   ├── cache/                # Caching system
│   ├── config/               # App configuration and language management
│   ├── constants/            # Category constants
│   ├── endpoints/            # Endpoint/mirror management
│   ├── errors/               # Error handling
│   ├── favorites/            # Favorites service
│   ├── logging/              # Structured logging
│   ├── metadata/             # Audiobook metadata management
│   ├── net/                  # Network utilities (Dio, CloudFlare, User-Agent)
│   ├── parse/                # HTML parsing for RuTracker
│   ├── permissions/          # Permission management
│   ├── player/               # Audio player service handler
│   ├── rutracker/            # RuTracker-specific utilities
│   ├── search/                # Search history service
│   ├── stream/                # Local streaming server
│   ├── torrent/               # Torrent download management
│   └── utils/                 # Utility functions
├── data/                      # Data layer
│   └── db/                    # Database configuration
├── features/                   # Feature modules
│   ├── auth/                  # Authentication UI
│   ├── debug/                 # Debug tools
│   ├── library/               # Library and favorites screens
│   ├── mirrors/               # Mirror management
│   ├── permissions/           # Permission UI
│   ├── player/                # Player screen
│   ├── search/                 # Search screen
│   ├── settings/               # Settings screens
│   ├── topic/                  # Topic/details screen
│   └── webview/                # WebView integration
└── l10n/                       # Localization files
```

### Supported Platforms

- **Android**: API 24+ (Android 7.0 Nougat and higher)
- **iOS**: Not currently configured (Android-focused project)
- **Minimum SDK**: Android 7.0 (API 24) for broad device compatibility
- **Target SDK**: Latest Android version

## Contributing

1. Follow the existing code style and architecture patterns
2. Write comprehensive tests for new features
3. Update documentation for API changes
4. Run `make analyze` and `make test` before submitting
5. Follow Flutter/Dart style guidelines

### Code Style

- Use 2 spaces for indentation
- Follow Dart naming conventions
- Write documentation comments for public APIs
- Maximum line length: 140 characters (configurable)
- Use `prefer_single_quotes` for strings
- Follow the analyzer rules defined in `analysis_options.yaml`

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for the latest changes and version history.

---

**Project Status**: In Active Development  
**Current Version**: 1.1.1+43  
**Supported Platforms**: Android 5.0+ (API 21+)  
**Distribution**: Direct APK (sideloading)
