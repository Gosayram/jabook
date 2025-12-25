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

- Add `@Keep` annotation to navigation route data classes
- Add `AuthInterceptor` for automatic re-authentication, integrate it into `NetworkModule`, and remove `PlayerScreen`'s `TopAppBar`
- Add `autoPlay` parameter to `loadAndPlayAudio` to control immediate playback after loading
- Add back navigation icon to the player screen with localized content description
- Add book properties dialog, improve file system scanning with robust book identification and encoding detection, and refactor library display views
- Add BookDetailPane composable to display detailed book information and integrate it into the library screen
- Add chapter search functionality to the player, improve playback speed display formatting, and adjust player padding
- Add chapter selection UI with chapter utilities and localization strings
- Add check for configured scan paths before initiating library scan and localize scan messages
- Add conditional bottom padding to the SnackbarHost
- Add custom icons and localized strings for media session controls and notifications, and synchronize player settings
- Add customizable font selection to app settings, allowing users to choose between default and system fonts
- Add debug screen for viewing and sharing logs and integrate it into navigation
- Add defensive encoding handler and decoding result for robust remote data decoding
- Add download history screen with search and sort functionality, and enable drag-and-drop reordering for the download queue
- Add dynamic color support to `JabookTheme` for Android 12+ devices
- Add fade-in and fade-out navigation transitions to PlayerScreen
- Add favorites feature with new data model, DAO, repository, ViewModel, and database migration
- Add Favorites screen with navigation and audiobook management capabilities
- Add grouped and grid library view modes with user preference persistence
- Add loading and error states to BookCard cover image
- Add login concurrency protection and multi-tier authentication validation
- Add Makefile targets for string migration and improve script to reuse existing strings and enhance translation robustness
- Add online audiobook search via Rutracker
- Add per-book customizable rewind and fast-forward durations via a new player settings sheet
- Add permission management and refactor Rutracker authentication to improve WebView cookie synchronization
- Add player performance logger and integrate it into AudioPlayerService initialization
- Add ProGuard rules to keep navigation and backup classes from obfuscation
- Add pull-to-refresh to LibraryScreen to trigger library scan with runtime storage permission checks
- Add RuTracker debug tab displaying authentication status, validation results, and mirror connectivity
- Add RuTracker diagnostics tab displaying authentication status, validation results, and mirror connectivity
- Add script to automate Kotlin Compose hardcoded string migration to resources
- Add support for custom audiobook scan paths with dedicated settings UI and database persistence
- Add swipe gestures and notification controls to mini player
- Apply zero window insets to TopAppBar in multiple feature screens, refine search bar logic, and update debug/topic screen titles
- Auto-initialize player with book data upon UI state success and update the library list view icon to be auto-mirrored
- Chapter search to filter by title/number instead of auto-jumping to chapter numbers
- Enhance backup/restore to include books, favorites, search history, scan paths, and extended settings
- Enhance chapter sorting logic with numerical and special chapter handling, and improve title extraction by falling back to filename
- Enhance Media3 integration with rich metadata, completion status, and improved Android Auto support
- Enhance Rutracker authentication with detailed logging, robust error handling, and new debug info
- Enhance settings screen with improved cache clearing messages, customizable slider value formatting, and direct links to GitHub resources
- Enhance string migration script with improved filtering, statistics, and add string resource for unknown cache size
- Externalize UI strings to resources for internationalization in player, settings, and library features
- Extract audiobook covers during library scan and skip scan if no scan paths are configured
- Extract embedded book covers during library scan and unify cover image loading across UI components
- Feat: Implement seek forward/backward controls in the player and refactor playback speed persistence to user preferences
- Feat: remove close button from BookPropertiesDialog
- Format chapter titles using `ChapterUtils` to include index and localized prefix
- Implement adaptive navigation using window size classes to display NavigationRail or BottomBar
- Implement app data backup and restore functionality for settings and book metadata
- Implement cache management in settings, allowing users to view and clear app cache
- Implement chapter search/jump in player, display library screen title, and remove redundant TopAppBar window insets
- Implement cleanup for non-existent scan paths and deleted books from the database
- Implement custom audio notification manager with media session integration and enable shared element transitions
- Implement download filtering, priority management, and queue reordering with a new database table
- Implement download history tracking and add new download speed and concurrency settings
- Implement hybrid local book scanning with direct file system access for custom paths and introduce shared audio file info
- Implement local audiobook scanning, sleep timer, and playback speed controls, and display download messages
- Implement main navigation and integrate core screens
- Implement Material 3 Adaptive UI for PlayerScreen and SearchScreen
- Implement MediaInfo parsing and models, and display details on the topic screen
- Implement multi-stage cookie persistence using new DAO, entity, and manager, integrated into the authentication flow and database
- Implement native streaming torrent downloads
- Implement pull-to-refresh for library scanning, enhance library display with empty state, and add chapter reordering functionality
- Implement real-time library scan progress with UI integration and metadata caching
- Implement user preference to toggle chapter title normalization in player and settings
- Implement user-configurable download location and Wi-Fi only download settings, and add library scanning functionality
- Improve authentication with strict validation and WebView cookie synchronization, and update Android runtime permission requests
- Initialize player only with non-empty chapters and eagerly start player state flow to avoid race conditions
- Integrate Glide for optimized notification artwork loading, replacing DataSourceBitmapLoader
- Internationalize favorites screen dialog and menu texts by adding new string resources
- Internationalize various UI and worker strings and refactor library scan worker for improved performance and batch processing
- Introduce Jetpack Compose UI with new data layer for books and refactor audio player components: phase 1 and 2
- Introduce library sorting by activity, title, author, and date added, and track book completion and last played times
- Introduce mini player and compact book list view, and enhance debug screen with auto-refresh and error handling
- Introduce pre-commit hook and script for automated cleaning of duplicate Android string resources
- Introduce unified book card, actions provider, and display modes for consistent book presentation and selection across Library, Search, and Favorites
- Localize search screen by extracting strings to resources and adding an ARB to XML conversion script
- Migrate media notifications from custom provider to `androidx.media3.ui.PlayerNotificationManager` for improved background service reliability
- Migrate to Gradle 9 for comprehensive building experience
- Mirror management with dynamic base URL, health checks, and persistent settings
- Persist book activity timestamps in player state and integrate into backup/restore functionality
- Prevent audio playlist reloads and configure Media3 notification channels
- Proactively start audio player service for warmup, enhance its startup logging and error handling, and increment the build version
- Replace hardcoded UI strings with string resources for localization across various screens and components
- Reposition and restyle LibraryScreen snackbar to be adaptive and bottom-aligned, and update PlayerScreen coroutine launches to use CoroutineStart.UNDISPATCHED
- Request Android 13+ notification permission, export audio service, and refine Media3 notification and media session integration
- Update FileProvider paths to include logs, downloads, and audiobooks, and align AndroidManifest authority and resource

### Changed

- Add `report` target to analyze startup logs and generate a debug report
- Add `run-beta` and `run-beta-debug` Makefile targets and update `install-beta` to use `adb`
- Add Apache 2.0 license header to all files
- Add comprehensive architecture documentation in Quarto format with 45+ Mermaid diagrams
- Add deprecation suppression for hiltViewModel in feature screens
- Add logic to request necessary permissions on app launch based on Android version
- Add topic details screen with its viewmodel and navigation route
- Added ignore packages for copyright validation
- Added ktlint-strace for make hook
- Added unique pid for each player session and fixing startForground issue
- Adjust Makefile build output paths by removing 'android/' prefix and trailing slashes
- Adjust PlayerScreen content padding and vertical item spacing
- Adjust SettingsScreen layout by adding top bar window insets, removing a horizontal divider, and reducing a spacer's height
- Apply system bar padding to player screen content and set fixed height and zero elevation for the navigation bar
- Audio player notification and session initialization to use `DefaultMediaNotificationProvider` with a small icon and set custom commands
- Auto markdown formatter
- Autoincrement for beta-releases as a patch
- Broaden string migration's file and technical string exclusion rules and streamline string replacement
- Bump Android SDK versions, enable ABI splits with universal APK generation, remove desugaring, and adapt Makefile for new APK output structure
- Bump Makefile
- Bump packages
- Bump patch version
- Bump patch version to 1.2.7+11
- Bump patch version to 1.2.7+12
- Bump patch version to 1.2.7+13
- Bump patch version to 1.2.7+14
- Bump patch version to 1.2.7+15
- Bump patch version to 1.2.7+16
- Bump patch version to 1.2.7+17
- Bump patch version to 1.2.7+18
- Bump patch version to 1.2.7+19
- Bump patch version to 1.2.7+20
- Bump patch version to 1.2.7+21
- Bump pub build
- Centralize date and time formatting with a new DateTimeFormatter utility and apply it to backup and settings
- Centralize playback speed constants and update player and settings UI to utilize them
- Clean up each cache before compilation testing
- Clean up unused imports and apply minor formatting to settings and topic screens
- Consolidate and simplify R8 rules for Kotlinx Serialization, Hilt, and other libraries
- Disable PlayerNotificationManager in audio service
- Enhance back gesture handling to always intercept and provide screen exit fallback
- Enhance foreground service initialization for Android 14+ and standardize notification ID
- Enhance ProGuard rules for Kotlinx Serialization, Hilt, Room, and Retrofit, and remove `@SerialName` annotations from navigation routes
- Enhance screen reader experience by adding semantic descriptions and roles to various UI components
- Enhance string migration script safety and remove problematic string resources
- Exclude test_results folder for copyright heads
- Externalize favorite button content descriptions using string resources
- Externalize hardcoded strings in various Compose screens and add new string resources
- Externalize library folder management and other UI strings for localization in settings screens
- Extract MainActivity logic to handlers and fix missing methods
- Fmt
- Gitignore
- Guaranteed forward to library window from anything via navigation panel button
- Ignore backup file
- Ignore xml backup
- Implement batch processing for library scanning with incremental progress updates and improved error handling
- Implement custom rewind/forward media session commands, force Android compile SDK to 34, and update flutter_media_metadata to a path dependency
- Implement environment-specific beta and production color themes, replacing the default Material 3 color schemes
- Implement jumpToTrack functionality, increase playback position saving frequency, and add extensive logging for audio bridge events
- Improve auto-increment experience
- Improve changelog generation mechanism
- Improve code formatting and organize imports
- Improve encoding detection and mojibake fixing by removing verbose logs, adding control character penalties, and adjusting confidence thresholds to prevent false positives
- Improve playback speed display formatting, refactor bottom navigation bar to use app state, and adjust UI paddings
- Improve string resource handling by consolidating access, fixing formatting, and removing obsolete keys
- Improve translate quality and checking dry-run mechanism
- Localize default string resources to English and update `TestComposeScreen` usage
- Make Text() string extraction regex more flexible to include additional arguments
- Migrate player to new bridge API with Kotlin state persistence
- Migrate string management from custom `Strings.kt` to standard `R.string` resources
- Migrate to DataStore + Tink encryption for credentials
- Migrate to Java 21 and replace kapt with KSP for Room
- Optimize library scan performance and cleanup obsolete TODOs
- Optimize ProGuard rules for Kotlinx Serialization, Hilt, Room, and DataStore Proto, remove Gson, and use `@SerialName` in navigation routes
- Polish navigation UI and resolve deprecations
- Preserve XML comments and formatting when adding new strings to `strings.xml`
- Re-change clean params for linting
- Refactor audio player service architecture
- Refine hardcoded string detection in `migrate_strings.py` to exclude non-UI files and technical strings
- Remove devDebugKotlin compilation from Makefile's compile target
- Remove explicit navigation transitions for Player screen and add background color to PlayerScreen
- Remove extraneous blank line in player route deep link configuration
- Remove format strings for numbers and dates from `strings.xml` and update migration script to identify them as technical
- Remove Lyricist i18n dependencies
- Remove redundant changelog generation confirmation message
- Remove unused backup build file
- Remove unused calls for defalt params into compile
- Remove unused flutter collab code from Kotlin
- Removed all flutter code from project; prepate to migrate native Kotlin code
- Rename string resource keys to English for improved clarity and maintainability
- Reorganize makefile automation and fix hack scripts
- Replace byte-count based Cyrillic encoding detection with confidence-based string decoding
- Replace hardcoded "Logged in as" string with a string resource
- Return  media player service initializer
- Script for Android startup log analysis and enhance Kotlin serialization ProGuard rules for R8 Full Mode
- Service warmup and using
- Simplify BookCard image loading state handling and improve accessibility
- Simplify ProGuard rules by removing redundant library-specific configurations and refining Kotlinx Serialization rules for `@SerialName`
- Streamline audio player service initialization and notification provider setup, and remove reflection fallback for media style token
- Streamline Gradle configuration, enable build caching, remove integration test plugin workaround, disable default WorkManager initialization, and generalize DataMigrationManager's DataStore usage
- Unification into one abstraction for easily linting
- Update chapter name formatting to accept a localized prefix and introduce new string resources for UI elements
- Update CI workflows to improve Gradle wrapper generation and permissions; added checks for existing files and enhanced error handling
- Update copyright script to target Kotlin files instead of Dart and remove Flutter-specific exclusions
- Update Hilt `@ApplicationContext` parameter annotation syntax and add `viewModel` import
- Update Hilt ViewModel imports and add trailing comma to enum definition + enhance downloads mechanism
- Update Kotlinx Serialization ProGuard rules for enhanced R8 compatibility, adding specific keeps for core classes, `@SerialName` fields, and navigation routes
- Update ProGuard rules by removing Flutter configurations and adding rules for Compose, Hilt, Room, DataStore, Media3, and WorkManager
- Use Provider for AuthRepository injection in AuthInterceptor and condense its KDoc

### Fixed

- Add empty MIGRATION_7_8 to resolve database migration issues
- Add ProGuard rules for Navigation Compose SavedStateHandle serialization and remove explicit kotlinx-serialization-json dependency
- Copyright validation folders
- Correct Media3 notification initialization and reduce default inactivity timeout
- Disable animation for fixing forward to library (back params) from player
- Disable KTagLib on Android 16+ due to FDSAN incompatibility and simplify ParcelFileDescriptor handling in metadata parsing
- Display book cover images in search results
- Duplicated and numeric strings with similar params for each project
- Ensure audio player notification remains persistent by always calling startForeground instead of conditionally stopping it
- Extend KTagLib disablement to Android 11+ (API 30) due to FDSAN incompatibility
- Fix chapter mismatch, player freeze, and local playback
- Fix compilation errors and improve playback position saving
- Fix logical relation with called alias param for each task in makefile
- Fix playback position restoration and prevent playlist loading conflicts
- Flutter media start
- HttpCache validation
- Launch method for double notification due revalidation ddos actions
- Migrate copyright management scripts from Dart to Kotlin files
- Move Lyricist's `ProvideStrings` to `JabookApp` and update `LocalStrings` imports
- Player validation
- Prevent duplicate player notification manager initialization and decrease notification update debounce delay
- Prevent KTagLib FDSAN errors by using separate file descriptors for metadata/artwork and enhance debug logs with comprehensive device info
- Refine metadata encoding correction to prevent UTF-16 corruption by selectively applying `fixGarbledText` based on mojibake detection
- Refine PlayerScreen back handler to only intercept when the chapters pane is open
- Remove `TopAppBar` from player screen and simplify search placeholder text
- Remove cover art processing from LibraryScanWorker, allowing UI to load covers directly from book folders
- Remove old BridgeModule between Kotlin and Flutter
- Remove redundant null check for sort order assignment
- Remove unnecessary Flutter plugin loader from settings.gradle.kts; streamline project configuration
- Removed unused flutter params
- Removed unused flutter tests, implementations, libs
- Resolve audio player bridging and playlist sorting issues
- Resolve compilation warnings and deprecated API usage
- Resolve Kotlin 2.2.0 compilation errors and update dependencies
- Resolve kotlinx-serialization version conflict in kapt and suppress manifest warnings
- Resolve player back navigation blank screen issue
- Room version and build namespace for validation
- Rutracker search parsing by adding cover URL extraction and enhancing selector robustness for existing fields
- Skip test files in the string migration script
- Temporarily disable mini-player functionality and its PlayerViewModel instantiation, adding TODOs for future state management
- Update beta APK installation to use arm64-v8a specific build
- Use default view list icon instead of auto-mirrored in library screen

### Security

- Add debug tools section and navigation to settings
- Enhance debug log header with detailed device, display, memory, and CPU info, and add a system log section
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
- Add RuTracker availability checker, improve logging and settings UI: - Add RuTrackerAvailabilityChecker: periodic background check (every 5 min) and manual check from settings. - Integrate checker
  with MainActivity lifecycle and RuTrackerSettingsScreen (manual button). - Refactor RuTrackerSettingsScreen and ViewModel: all UI strings moved to resources, improved localization. - Add/replace
  strings in strings.xml for all new UI and error messages. - Improve debug log export, SAF folder selection, and error handling in settings. - Update DI modules, repository, and core network logic
  for new checker and guest mode. - Minor: update DebugLogger, file_paths.xml, and related UI/UX polish
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
- Detekt: massive refactor, split complex classes, fix trailing spaces, reduce cyclomatic complexity: - Split PlayerManagerImpl into smaller classes (AudioFocusManager, SleepTimerManager,
  MediaItemManager, PlaybackStateManager) - Refactor DebugLogger and TorrentEventFormatter for better maintainability - Remove unused properties and imports\n- Fix all trailing spaces and final
  newlines - Reduce cyclomatic complexity in UI and domain logic - All detekt errors and warnings are now non-blocking (only complexity and function count remain as warnings)
- Fix base errors for impl, imports and unused methods
- Fix builds
- Fix compilation errors and detekt warnings, improve code structure: - Fix Hilt dependency injection conflicts: remove duplicate bindings from NetworkModule,   move RuTracker dependencies to
  RuTrackerModule with proper @Binds annotations - Resolve MediaType deprecation: replace MediaType.get() with toMediaType() extension - Fix compilation errors in RuTrackerApiService: add missing
  imports and @Inject constructor - Refactor complex UI components to reduce function count and improve maintainability:   * Split RuTrackerSettingsScreen into ModeToggleCard, LoginCard,
  StatusMessageCard   * Break down AudiobookSearchResultCard into AudiobookCover, AudiobookInfo,     ActionButtons, MetadataRow, AdditionalInfo components   * Extract SleepTimerDelegate from
  PlayerManagerImpl to reduce function count - Fix import conflicts and trailing spaces: resolve weight() modifier issues,   remove unused imports, fix import ordering - Improve code organization:
  use data classes for component parameters,   add proper trailing commas, fix modifier usage - All detekt warnings resolved: no more compilation errors, clean build passes
- Gitignore for compiler
- Lots of updates and fixes
- Refactoring/perf (#20)
- Remove deprecated Android API usage, unify compatibility for minSdk 23+; cleanup suppressions: - Remove all usages of deprecated Android platform APIs (audio focus, getColor, getParcelableExtra,
  network info); - Unify compatibility logic for Android 6.0+ (minSdk 23): explicit version checks, no suppressions; - Refactor AudioFocusManager, PlayerService, Extensions, ViewFallbacks for clarity
  and maintainability
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
- Fixed build errors: -  Hilt binding issues - Fixed proper dependency injection for File and CacheConfig; - ExperimentalCoilApi dependency - Corrected import path for Coil 3; - Kotlin language
  version conflicts - Resolved API version compatibility issue
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
- Minor changes: - Updated `fallbackToDestructiveMigration()` to `fallbackToDestructiveMigration(true)` in JaBookDatabase.kt - Fixed annotation target in RuTrackerPreferences.kt using
  `@param:ApplicationContext` - Replaced deprecated `LocalClipboardManager` with `LocalClipboard` in AudiobookSearchResultCard.kt - Removed deprecated `catch` operator on SharedFlow in
  DownloadsViewModel.kt
- Multiple code quality and compatibility fixes across the project Material Design Migration: - updated DownloadsScreen.kt to use material3 library instead of material imports; - replaced
  android:tint with app:tint in jabook_widget_layout.xml for proper namespace usage; - added kotlin-kapt plugin to build.gradle.kts to support data binding functionality; - added format placeholders
  (%s) to string resources that were used with String.format(); - enhanced RuTrackerPreferences.kt encryption by adding explicit mode and padding to Cipher.getInstance(); - removed unnecessary
  SDK_INT checks in multiple files since minSdk is 24
- Package compatibility fixes
- Package version
- Packages fixes and imports
- Removed jaudiotagger
- Removed unused error: Exception? = null; error = e
- Resolve all unused resource warnings and improve code quality across the project: - Created JaBookAppWidgetProvider.kt with proper AppWidgetProvider implementation; - Added widget receiver
  registration in AndroidManifest.xml; - Fixed unused appwidget_background.xml and appwidget_preview.xml drawable resources; - Removed unnecessary resValue calls for app_name in build.gradle.kts; -
  Fixed duplicate string resource definition conflicts
- Resolve duplicate strings, add pending/idle statuses, bind TorrentRepository; expose downloadStates in TorrentManager and make UI when-expressions exhaustive; remove resumeTorrent overload, fix
  Hilt MissingBinding for downloads module
- Resolve linters issue
- Resolve multiple build warnings and compatibility issues: - Fix PlayerService onStartCommand by adding super.onStartCommand call - Remove non-existent activity references from AndroidManifest.xml
  (PlayerActivity, RuTrackerSettingsScreen, etc.) - Update library versions in gradle/libs.versions.toml to latest stable versions - Fix screen orientation for Chrome OS compatibility (unspecified
  instead of portrait) - Add @UnstableApi annotation to PlayerService for Media3 experimental APIs - Update ThemeToggleButton to use LocalWindowInfo instead of LocalConfiguration - Fix Compose
  Modifier parameter positioning in multiple components
- Resolve package compatability
- Resolve some issues: - Fixed SearchBar function signature by removing the incorrect inputField parameter and using the proper overload - Fixed @Composable invocation issues by removing nested
  SearchBarDefaults.InputField calls - Fixed Icons.Default.Close reference; - Fixed Icons.AutoMirrored.Default.ArrowBack reference to use Icons.AutoMirrored.filled.ArrowBack; - Fixed all
  Icons.AutoMirrored.Default.LibraryBooks references to use Icons.AutoMirrored.filled.LibraryBooks; - Fixed Icons.AutoMirrored.Default.Sort reference to use Icons.AutoMirrored.filled.Sort; - Fixed
  statusBarColor reference by using colorScheme.toArgb() instead of the incorrect property access
- Resolve some issues: - fixed unchecked cast warning on line 59 by replacing flows[6] as List<Bookmark> with flows[6] as? List<Bookmark> ?: emptyList(); - fixed deprecated Icons.Filled.ArrowBack by
  importing autoMirrored.filled.ArrowBack and updating the reference to Icons.AutoMirrored.Default.ArrowBack; - fixed 2 deprecated LinearProgressIndicator warnings by changing progress =
  audiobook.progressPercentage to progress = { audiobook.progressPercentage } on lines 109 and 181; - fixed 4 deprecated Icons.Filled.LibraryBooks warnings by importing
  autoMirrored.filled.LibraryBooks and updating all references to Icons.AutoMirrored.Default.LibraryBooks; - fixed deprecated Icons.Filled.Sort by importing autoMirrored.filled.Sort and updating the
  reference to Icons.AutoMirrored.Default.Sort; - fixed deprecated statusBarColor by replacing window.statusBarColor = colorScheme.primary.toArgb() with window.statusBarColor =
  WindowCompat.getInsetsController(window, view).statusBarColor
- Resolve unused permissions and fixes launchers (logos)
- Restrict generic, fix data type
- Sdk 24 compatability params
- Some bug fixes
- Some fixes
- Some fixes: - RuTrackerParserEnhanced.kt: The nullable String issues on lines 442-443 have been resolved - the code now uses safe calls (?.) with null fallbacks (?:); - JaBookDatabase.kt: Fixed the
  deprecated Room API warning by updating fallbackToDestructiveMigration() to fallbackToDestructiveMigration(true) to use the non-deprecated overloaded version; - Coil warning: This is a build
  configuration issue about a missing dependency for coil.annotation.ExperimentalCoilApi that would need to be addressed in the build.gradle file
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
