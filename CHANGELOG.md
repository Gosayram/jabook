# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Theme system with light/dark/auto modes and persistent user preferences
- Comprehensive UI/UX improvements with modern spacing and adaptive design
- Advanced player features: sleep timer, playback speed control, chapter navigation
- Background playback service with MediaSession integration
- Performance optimization and memory management improvements
- Torrent download system with pause/resume capabilities
- RuTracker integration for audiobook discovery and downloading
- File management system with automatic metadata extraction
- Clean Architecture foundation with modular structure
- Comprehensive error handling and debug logging

### Changed
- Refactored deprecated Android API usage for better compatibility
- Improved UI spacing and modernized interface design
- Enhanced player architecture with separated concerns
- Updated code quality tools and resolved formatting conflicts
- Unified compatibility logic for Android 6.0+ (minSdk 23)

### Fixed
- Resolved duplicate strings and missing bindings
- Fixed Hilt dependency injection issues
- Corrected UI state management and navigation
- Addressed deprecated API warnings and compatibility issues

### Performance
- Optimized player performance and memory usage
- Improved background service efficiency
- Enhanced UI responsiveness and animations

## [v0.1.0] - 2025-01-XX

### Added
- Initial project setup with Kotlin 2.2.x and Gradle 8.14+
- MVVM architecture setup with Hilt dependency injection
- Jetpack Compose UI framework with Material Design 3
- Android compatibility layer for API 23-34 support
- Basic navigation structure (Library, Discovery, Player, Downloads)
- UI theme system with light/dark mode support
- String resources and Android manifest configuration
- Code quality tools (Ktlint, Detekt, JaCoCo)

---

**Note**: This project is currently in active development. Version 0.1.0 represents the foundational setup phase.

[Unreleased]: https://github.com/Gosayram/jabook/compare/v0.1.0...HEAD
[v0.1.0]: https://github.com/Gosayram/jabook/releases/tag/v0.1.0 