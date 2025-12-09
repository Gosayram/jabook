# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## Types of changes

- `Added` for new features.
- `Changed` for changes in existing functionality.
- `Deprecated` for soon-to-be removed features.
- `Removed` for now removed features.
- `Fixed` for any bug fixes.
- `Security` in case of vulnerabilities.

## [Unreleased]

### Added
- Add swipe gestures and notification controls to mini player

### Changed
- Bump packages
- Bump pub build
- Enhance foreground service initialization for Android 14+ and standardize notification ID
- Exclude test_results folder for copyright heads
- Extract MainActivity logic to handlers and fix missing methods
- Implement custom rewind/forward media session commands, force Android compile SDK to 34, and update flutter_media_metadata to a path dependency
- Implement jumpToTrack functionality, increase playback position saving frequency, and add extensive logging for audio bridge events
- Migrate player to new bridge API with Kotlin state persistence
- Migrate to Java 21 and replace kapt with KSP for Room
- Refactor audio player service architecture

### Fixed
- Fix chapter mismatch, player freeze, and local playback
- Fix compilation errors and improve playback position saving
- Fix playback position restoration and prevent playlist loading conflicts
- Flutter media start
- HttpCache validation
- Player validation
- Resolve audio player bridging and playlist sorting issues
- Resolve compilation warnings and deprecated API usage
- Resolve Kotlin 2.2.0 compilation errors and update dependencies
- Resolve kotlinx-serialization version conflict in kapt and suppress manifest warnings
- Room version and build namespace for validation

## [1.2.6] - 2025-12-06

### Changed
- Add dynamic app name support based on build flavor (#43)
- Bump softprops/action-gh-release from 2.4.2 to 2.5.0 (#44)

### Fixed
- Tag resolution

## [1.2.5] - 2025-11-27

### Changed
- Bump release
- Change fmt to html for each body msg
- Tg notification

## [1.2.4] - 2025-11-27

### Changed
- Bump actions/checkout from 5.0.1 to 6.0.0 (#30)
- Ignore issue docs
- Improve playback speed control and seek feedback (#35)
- Improve storage permissions handling for custom ROMs (#42)

### Fixed
- Fix library books not visible after app update (#37)
- Fix/login auth (#32)

## [1.2.0] - 2025-11-24

### Added
- Add theme and audio settings with per-book customization (#25)
- Feature/about page (#28)
- Refactor favorites to Riverpod and add favorite buttons (#26)

### Changed
- Bump changelog generator
- Optimize Android build configuration and ProGuard rules
- Prod package validation
- Update release 1.2.0

### Fixed
- Fix typo
- Fix/about (#29)
- Fix/distr (#24)
- Prod prefix
- Reduce compilation resources for build params
- System label for localization

## [1.1.4+9] - 2025-11-23

