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
- Add online audiobook search via Rutracker
- Add swipe gestures and notification controls to mini player
- Implement main navigation and integrate core screens
- Implement native streaming torrent downloads
- Improve authentication with strict validation and WebView cookie synchronization, and update Android runtime permission requests
- Introduce Jetpack Compose UI with new data layer for books and refactor audio player components: phase 1 and 2
- Migrate to Gradle 9 for comprehensive building experience
- Mirror management with dynamic base URL, health checks, and persistent settings

### Changed
- Add `run-beta` and `run-beta-debug` Makefile targets and update `install-beta` to use `adb`
- Add deprecation suppression for hiltViewModel in feature screens
- Add logic to request necessary permissions on app launch based on Android version
- Add topic details screen with its viewmodel and navigation route
- Added ignore packages for copyright validation
- Bump Android SDK versions, enable ABI splits with universal APK generation, remove desugaring, and adapt Makefile for new APK output structure
- Bump Makefile
- Bump packages
- Bump pub build
- Clean up unused imports and apply minor formatting to settings and topic screens
- Enhance foreground service initialization for Android 14+ and standardize notification ID
- Enhance screen reader experience by adding semantic descriptions and roles to various UI components
- Exclude test_results folder for copyright heads
- Extract MainActivity logic to handlers and fix missing methods
- Implement custom rewind/forward media session commands, force Android compile SDK to 34, and update flutter_media_metadata to a path dependency
- Implement environment-specific beta and production color themes, replacing the default Material 3 color schemes
- Implement jumpToTrack functionality, increase playback position saving frequency, and add extensive logging for audio bridge events
- Improve code formatting and organize imports
- Migrate player to new bridge API with Kotlin state persistence
- Migrate to DataStore + Tink encryption for credentials
- Migrate to Java 21 and replace kapt with KSP for Room
- Polish navigation UI and resolve deprecations
- Refactor audio player service architecture
- Remove unused flutter collab code from Kotlin
- Removed all flutter code from project; prepate to migrate native Kotlin code
- Reorganize makefile automation and fix hack scripts
- Streamline Gradle configuration, enable build caching, remove integration test plugin workaround, disable default WorkManager initialization, and generalize DataMigrationManager's DataStore usage
- Update ProGuard rules by removing Flutter configurations and adding rules for Compose, Hilt, Room, DataStore, Media3, and WorkManager

### Fixed
- Fix chapter mismatch, player freeze, and local playback
- Fix compilation errors and improve playback position saving
- Fix playback position restoration and prevent playlist loading conflicts
- Flutter media start
- HttpCache validation
- Move Lyricist's `ProvideStrings` to `JabookApp` and update `LocalStrings` imports
- Player validation
- Remove old BridgeModule between Kotlin and Flutter
- Removed unused flutter params
- Removed unused flutter tests, implementations, libs
- Resolve audio player bridging and playlist sorting issues
- Resolve compilation warnings and deprecated API usage
- Resolve Kotlin 2.2.0 compilation errors and update dependencies
- Resolve kotlinx-serialization version conflict in kapt and suppress manifest warnings
- Room version and build namespace for validation
- Update beta APK installation to use arm64-v8a specific build

### Security
- FileProvider for secure file sharing, new permissions, and manifest compatibility adjustments

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

### Added
- ADD - Add CI workflow for GitHub Actions
- Add CI workflow for GitHub Actions
- Add comprehensive file management and torrent system
- Add downloads management and enhanced metadata support (#15)
- Add RuTracker availability checker, improve logging and settings UI: - Add RuTrackerAvailabilityChecker: periodic background check (every 5 min) and manual check from settings. - Integrate checker with MainActivity lifecycle and RuTrackerSettingsScreen (manual button). - Refactor RuTrackerSettingsScreen and ViewModel: all UI strings moved to resources, improved localization. - Add/replace strings in strings.xml for all new UI and error messages. - Improve debug log export, SAF folder selection, and error handling in settings. - Update DI modules, repository, and core network logic for new checker and guest mode. - Minor: update DebugLogger, file_paths.xml, and related UI/UX polish
- FEATURE - Add RuTracker and torrent system architecture
- Implement Clean Architecture foundation with modular structure
- Implement phase 2 core player functionality
- Implement Phase 4 advanced features: background playback + performance optimization
- Implement Phase 5 UI/UX improvements with comprehensive animation system
- Improve cache and cookie handling; (#13)
- Initial commit
- Initialize Android Kotlin project with Jetpack Compose

### Changed
- 1.1.2; (#12)
- Add comprehensive logging and error handling for downloads synchronization (#17)
- Added signed release
- Bump actions/checkout from 5.0.0 to 5.0.1 (#14)
- Bump docs
- Bump docs; (#2)
- Bump flutter sdk
- Bump kotlin version
- Bump localizations
- Bump package version
- Bunch of changes without comments
- Detekt: massive refactor, split complex classes, fix trailing spaces, reduce cyclomatic complexity: - Split PlayerManagerImpl into smaller classes (AudioFocusManager, SleepTimerManager, MediaItemManager, PlaybackStateManager) - Refactor DebugLogger and TorrentEventFormatter for better maintainability - Remove unused properties and imports\n- Fix all trailing spaces and final newlines - Reduce cyclomatic complexity in UI and domain logic - All detekt errors and warnings are now non-blocking (only complexity and function count remain as warnings)
- Fix base errors for impl, imports and unused methods
- Fix builds
- Fix compilation errors and detekt warnings, improve code structure: - Fix Hilt dependency injection conflicts: remove duplicate bindings from NetworkModule,   move RuTracker dependencies to RuTrackerModule with proper @Binds annotations - Resolve MediaType deprecation: replace MediaType.get() with toMediaType() extension - Fix compilation errors in RuTrackerApiService: add missing imports and @Inject constructor - Refactor complex UI components to reduce function count and improve maintainability:   * Split RuTrackerSettingsScreen into ModeToggleCard, LoginCard, StatusMessageCard   * Break down AudiobookSearchResultCard into AudiobookCover, AudiobookInfo,     ActionButtons, MetadataRow, AdditionalInfo components   * Extract SleepTimerDelegate from PlayerManagerImpl to reduce function count - Fix import conflicts and trailing spaces: resolve weight() modifier issues,   remove unused imports, fix import ordering - Improve code organization: use data classes for component parameters,   add proper trailing commas, fix modifier usage - All detekt warnings resolved: no more compilation errors, clean build passes
- Gitignore for compiler
- Lots of updates and fixes
- Refactoring/perf (#20)
- Remove deprecated Android API usage, unify compatibility for minSdk 23+; cleanup suppressions: - Remove all usages of deprecated Android platform APIs (audio focus, getColor, getParcelableExtra, network info); - Unify compatibility logic for Android 6.0+ (minSdk 23): explicit version checks, no suppressions; - Refactor AudioFocusManager, PlayerService, Extensions, ViewFallbacks for clarity and maintainability
- Remove unused private property in DebugLogger.kt
- Removed and fixed unused params; bump logical structure
- Removed unused files
- Rewrite DebugLogger.kt to fix line length and unused property issues
- Upd build packages
- Upd build params
- Update semver
- Upgrade code quality tools and resolve formatting conflicts
- V1.1.1; (#1)

### Fixed
- Add automatic download resumption and improve settings UX (#19)
- Adding super.onStartCommand(intent, flags, startId) at the beginning of the onStartCommand method
- Api version for deprecated libs
- Base fixes
- BUGFIX - Fix RuTracker integration and compilation issues
- Change duration time via external libs
- CI settings
- Compiler version
- Download mechanism; states and Download status; fix old errors with playerListener
- Exclude additional files
- Fix app version and sdk; fix theme names
- Fix base params and editor configs
- Fix CI via ignore wrapper jar
- Fix deprecated libs
- Fix formats
- Fix imports and minor bugs
- Fix imports and syntax errors
- Fix imports, metrics, unsafe returns
- Fix incorrect alias
- Fix incorrect arrays
- Fix incorrect data
- Fix incorrect implementation; added desugaring libs for old android versions
- Fix incorrect imports
- Fix incorrect libs
- Fix incorrect syntax
- Fix java path
- Fix limits
- Fix package versions
- Fix pop_up (#16)
- Fix/notify (#18)
- Fixed build errors: -  Hilt binding issues - Fixed proper dependency injection for File and CacheConfig; - ExperimentalCoilApi dependency - Corrected import path for Coil 3; - Kotlin language version conflicts - Resolved API version compatibility issue
- Fixed formats
- Fixes format issues
- Format issues
- Formats
- Import primary source for exepctions; removed duplicated improts; move SimpleDataFormat to ParseDateText
- Imports and some fixes
- Improve RuTracker connectivity and authentication: (#5)
- Include wrapper file
- Incorrect imports
- Java version compatibility
- Libs version and configs
- Linter checks and syntax errors
- Linter mechanism
- Linters and imports; remove unused params; validation errors
- Lots of fixes and exceptions
- Lots of fixes for packages
- Minor changes: - Updated `fallbackToDestructiveMigration()` to `fallbackToDestructiveMigration(true)` in JaBookDatabase.kt - Fixed annotation target in RuTrackerPreferences.kt using `@param:ApplicationContext` - Replaced deprecated `LocalClipboardManager` with `LocalClipboard` in AudiobookSearchResultCard.kt - Removed deprecated `catch` operator on SharedFlow in DownloadsViewModel.kt
- Multiple code quality and compatibility fixes across the project Material Design Migration: - updated DownloadsScreen.kt to use material3 library instead of material imports; - replaced android:tint with app:tint in jabook_widget_layout.xml for proper namespace usage; - added kotlin-kapt plugin to build.gradle.kts to support data binding functionality; - added format placeholders (%s) to string resources that were used with String.format(); - enhanced RuTrackerPreferences.kt encryption by adding explicit mode and padding to Cipher.getInstance(); - removed unnecessary SDK_INT checks in multiple files since minSdk is 24
- Package compatibility fixes
- Package version
- Packages fixes and imports
- Removed jaudiotagger
- Removed unused error: Exception? = null; error = e
- Resolve all unused resource warnings and improve code quality across the project: - Created JaBookAppWidgetProvider.kt with proper AppWidgetProvider implementation; - Added widget receiver registration in AndroidManifest.xml; - Fixed unused appwidget_background.xml and appwidget_preview.xml drawable resources; - Removed unnecessary resValue calls for app_name in build.gradle.kts; - Fixed duplicate string resource definition conflicts
- Resolve duplicate strings, add pending/idle statuses, bind TorrentRepository; expose downloadStates in TorrentManager and make UI when-expressions exhaustive; remove resumeTorrent overload, fix Hilt MissingBinding for downloads module
- Resolve linters issue
- Resolve multiple build warnings and compatibility issues: - Fix PlayerService onStartCommand by adding super.onStartCommand call - Remove non-existent activity references from AndroidManifest.xml (PlayerActivity, RuTrackerSettingsScreen, etc.) - Update library versions in gradle/libs.versions.toml to latest stable versions - Fix screen orientation for Chrome OS compatibility (unspecified instead of portrait) - Add @UnstableApi annotation to PlayerService for Media3 experimental APIs - Update ThemeToggleButton to use LocalWindowInfo instead of LocalConfiguration - Fix Compose Modifier parameter positioning in multiple components
- Resolve package compatability
- Resolve some issues: - Fixed SearchBar function signature by removing the incorrect inputField parameter and using the proper overload - Fixed @Composable invocation issues by removing nested SearchBarDefaults.InputField calls - Fixed Icons.Default.Close reference; - Fixed Icons.AutoMirrored.Default.ArrowBack reference to use Icons.AutoMirrored.filled.ArrowBack; - Fixed all Icons.AutoMirrored.Default.LibraryBooks references to use Icons.AutoMirrored.filled.LibraryBooks; - Fixed Icons.AutoMirrored.Default.Sort reference to use Icons.AutoMirrored.filled.Sort; - Fixed statusBarColor reference by using colorScheme.toArgb() instead of the incorrect property access
- Resolve some issues: - fixed unchecked cast warning on line 59 by replacing flows[6] as List<Bookmark> with flows[6] as? List<Bookmark> ?: emptyList(); - fixed deprecated Icons.Filled.ArrowBack by importing autoMirrored.filled.ArrowBack and updating the reference to Icons.AutoMirrored.Default.ArrowBack; - fixed 2 deprecated LinearProgressIndicator warnings by changing progress = audiobook.progressPercentage to progress = { audiobook.progressPercentage } on lines 109 and 181; - fixed 4 deprecated Icons.Filled.LibraryBooks warnings by importing autoMirrored.filled.LibraryBooks and updating all references to Icons.AutoMirrored.Default.LibraryBooks; - fixed deprecated Icons.Filled.Sort by importing autoMirrored.filled.Sort and updating the reference to Icons.AutoMirrored.Default.Sort; - fixed deprecated statusBarColor by replacing window.statusBarColor = colorScheme.primary.toArgb() with window.statusBarColor = WindowCompat.getInsetsController(window, view).statusBarColor
- Resolve unused permissions and fixes launchers (logos)
- Restrict generic, fix data type
- Sdk 24 compatability params
- Some bug fixes
- Some fixes
- Some fixes: - RuTrackerParserEnhanced.kt: The nullable String issues on lines 442-443 have been resolved - the code now uses safe calls (?.) with null fallbacks (?:); - JaBookDatabase.kt: Fixed the deprecated Room API warning by updating fallbackToDestructiveMigration() to fallbackToDestructiveMigration(true) to use the non-deprecated overloaded version; - Coil warning: This is a build configuration issue about a missing dependency for coil.annotation.ExperimentalCoilApi that would need to be addressed in the build.gradle file
- Specify Java home directory
- Syntax errors and imports

### Security
- Fix secure imports and logical starts with cursors
- Security params


[Unreleased]: https://github.com/Gosayram/jabook/compare/1.2.6...HEAD
[1.2.6]: https://github.com/Gosayram/jabook/compare/1.2.5...1.2.6
[1.2.5]: https://github.com/Gosayram/jabook/compare/1.2.4...1.2.5
[1.2.4]: https://github.com/Gosayram/jabook/compare/1.2.0...1.2.4
[1.2.0]: https://github.com/Gosayram/jabook/compare/1.1.4+9...1.2.0
[1.1.4+9]: https://github.com/Gosayram/jabook/compare/d267d15fb5c474143256670074da461cd315af74...1.1.4+9
