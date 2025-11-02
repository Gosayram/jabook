# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Multi-language support (English and Russian) with Flutter localization
- Dynamic RuTracker mirror management with automatic health checking and failover
- Endpoint health scheduler for proactive mirror availability monitoring
- Comprehensive search functionality with history and caching
- Favorites system for quick access to preferred audiobooks
- Advanced audio player with chapter navigation, playback speed control, and sleep timer
- Background playback service using Audio Service with MediaSession integration
- Local streaming server for audio file serving
- Torrent download management with pause/resume capabilities using dtorrent library
- Backup and restore service for user data preservation
- Audiobook metadata synchronization and management
- Search history service with automatic cleanup
- Forum resolver for category mapping and URL resolution
- CloudFlare bypass utilities for improved connectivity
- User agent management with automatic rotation
- Permission management system with onboarding dialog
- First launch detection and initialization flow
- Graceful degradation for platform-specific features (Bluetooth, notifications)
- Secure WebView integration for RuTracker authentication
- Cookie synchronization between WebView and HTTP client
- Environment logger with structured logging
- Safe async utilities with error handling
- Comprehensive error handling and failure types

### Changed
- Migrated project structure to Clean Architecture with feature modules
- Updated state management to Flutter Riverpod
- Implemented GoRouter for declarative navigation
- Refactored authentication flow to use WebView-based login
- Improved caching system with TTL support and automatic expiration
- Enhanced parsing utilities with better error handling
- Updated database layer to use Sembast for document storage
- Modernized UI with Material Design 3 components
- Improved code organization with clear separation of concerns

### Fixed
- Resolved cookie management issues between WebView and HTTP requests
- Fixed authentication state persistence
- Corrected endpoint selection logic with fallback support
- Addressed memory leaks in audio player
- Fixed background playback service lifecycle management
- Resolved permission request flow on first launch
- Corrected cache expiration handling
- Fixed search result caching and retrieval

### Performance
- Optimized database queries with indexed stores
- Improved cache hit rates with smart TTL management
- Reduced memory footprint in audio player
- Enhanced network request efficiency with connection pooling
- Optimized image loading with caching and compression

## [1.1.1] - 2025-01-XX

### Added
- Initial Flutter project setup with Dart 3.2.0+
- Material Design 3 theme system with light/dark mode support
- Basic navigation structure (Library, Search, Settings)
- RuTracker integration foundation
- Audio player basic functionality
- Database layer with Sembast
- Core utilities and helper classes
- Basic error handling infrastructure

### Changed
- Project structure established with core/features separation

### Fixed
- Initial release stabilization

---

**Note**: This project is currently in active development. Version 1.1.1 represents a functional release with core features implemented.

[Unreleased]: https://github.com/Gosayram/jabook/compare/v1.1.1...HEAD
[1.1.1]: https://github.com/Gosayram/jabook/releases/tag/v1.1.1
