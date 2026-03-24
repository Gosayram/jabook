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

- [FEATUREI - Implement torrent file selection and prioritization for downloads
- Add "in library" status indicator to Rutracker search results
- Add `@Keep` annotation to navigation route data classes
- Add `AuthInterceptor` for automatic re-authentication, integrate it into `NetworkModule`, and remove `PlayerScreen`'s `TopAppBar`
- Add `autoPlay` parameter to `loadAndPlayAudio` to control immediate playback after loading
- Add `extractForumNameFromHTML` to parse forum names from Rutracker pages using multiple strategies
- Add `ParsingResult` for graceful error handling and integrate defensive encoding for robust parsing
- Add a collapse button to the TopicScreen for expanded content
- Add AMOLED theme support for enhanced dark mode experience, implement skip to next and previous track functionality in MiniPlayer, and improve drag gesture handling for better user interaction;
  update UI components and localization for new features
- Add animated mini player with improved drag-to-dismiss, visual feedback, and updated UI
- Add audio visualization manager and Compose UI components for waveform, bar, and circular styles
- Add audio visualizer to player screen and request RECORD_AUDIO permission
- Add audiobook category data model and HTML parser for RuTracker
- Add AutoSaveManager for periodic playback state persistence and its corresponding tests
- Add back navigation icon to the player screen with localized content description
- Add backup schema v2.0.0 including app info, statistics, and torrent metadata, and update version compatibility
- Add book play count tracking, incrementing on completion, and dedicated tests
- Add book properties dialog, improve file system scanning with robust book identification and encoding detection, and refactor library display views
- Add BookDetailPane composable to display detailed book information and integrate it into the library screen
- Add buffering state management and display a buffering dialog for torrent streaming
- Add chapter search functionality to the player, improve playback speed display formatting, and adjust player padding
- Add chapter selection UI with chapter utilities and localization strings
- Add check for configured scan paths before initiating library scan and localize scan messages
- Add completion percentage calculation considering all tracks in playlist; implement preloading of next track and memory optimization for large playlists in PlayerConfigurator and PlaylistManager;
  enhance OfflineFirstBooksRepository to utilize improved progress calculation
- Add conditional bottom padding to the SnackbarHost
- Add CoverPreloader utility for efficient preloading of book cover images in LazyColumn and LazyGrid; enhance UnifiedBooksView to utilize preloading for improved user experience during scrolling
- Add custom icons and localized strings for media session controls and notifications, and synchronize player settings
- Add custom media control icons and update NotificationIconProvider to use them
- Add customizable font selection to app settings, allowing users to choose between default and system fonts
- Add debug screen for viewing and sharing logs and integrate it into navigation
- Add dedicated audio settings screen with options for playback, crossfade, skip silence, and volume normalization
- Add defensive encoding handler and decoding result for robust remote data decoding
- Add dependency injection to `CoverUrlExtractor` and `DefensiveFieldExtractor` and introduce `DataMigrationManager` tests
- Add distinct messages for library scan completion when no books are found or no folders are configured
- Add download history screen with search and sort functionality, and enable drag-and-drop reordering for the download queue
- Add dynamic color support to `JabookTheme` for Android 12+ devices
- Add dynamic theming to player UI based on album art color extraction
- Add end-of-chapter sleep timer and refactor sleep timer management to AudioPlayerService
- Add extensive diagnostic logging for Rutracker search parsing and network requests
- Add extraction and display of topic registration date and download count
- Add fade-in and fade-out navigation transitions to PlayerScreen
- Add favorites feature with new data model, DAO, repository, ViewModel, and database migration
- Add Favorites screen with navigation and audiobook management capabilities
- Add file utility for directory size and enhance torrent download deletion with a confirmation dialog and file removal option
- Add functionality to delete all torrents and display their storage usage in settings
- Add generic `allMetadata` field to topic details for dynamic display, replacing explicit duration and bitrate UI
- Add grouped and grid library view modes with user preference persistence
- Add indexing start time and clearing progress tracking, implement notification permission request, and display indexing progress UI
- Add lenient validation and mapping for offline indexed search results, including category fallback for cached entities
- Add loading and error states to BookCard cover image
- Add login concurrency protection and multi-tier authentication validation
- Add login description and 'or' strings, and enhance AuthScreen UI with icons, improved spacing, and animated loading state
- Add make commit target to generate AI commit messages
- Add Makefile targets for string migration and improve script to reuse existing strings and enhance translation robustness
- Add MediaButtonHandler and HeadsetAutoplayHandler for enhanced media control and multi-click support
- Add migration screen navigation route and deep link
- Add MIGRATION_14_15 to set fallback category for cached topics and enhance offline search query filtering and ordering
- Add mini-player state management and Material You theming for notification icons, and clean up RutrackerParserTest
- Add mirror domain logging and stability checks to indexing and search operations
- Add new Beta flavor launcher icon with gradient design; update PlayerScreen and Color.kt to utilize Material 3 color scheme for improved UI consistency and accessibility
- Add new metadata fields, refactor Rutracker description text extraction to use cleaned HTML, and introduce dedicated metadata extraction tests
- Add notification actions for individual torrents and global download controls
- Add online audiobook search via Rutracker
- Add option to display original chapter titles instead of normalized chapter names in player UI
- Add per-book customizable rewind and fast-forward durations via a new player settings sheet
- Add permission management and refactor Rutracker authentication to improve WebView cookie synchronization
- Add player gestures for brightness, volume, and seek with visual indicators
- Add player performance logger and integrate it into AudioPlayerService initialization
- Add premium UI components including animated backgrounds with glassmorphism effects, vinyl mode for audio playback, and synchronized lyrics display; enhance audio visualizer with circular style and
  improve theme management with gradient support
- Add ProGuard rules to keep navigation and backup classes from obfuscation
- Add pull-to-refresh to LibraryScreen to trigger library scan with runtime storage permission checks
- Add quote parsing and refactor comment HTML cleaning into a new helper function, accompanied by a new test
- Add RuTracker debug tab displaying authentication status, validation results, and mirror connectivity
- Add RuTracker diagnostics tab displaying authentication status, validation results, and mirror connectivity
- Add RuTracker repository and search view model for audiobook search and details
- Add RuTracker search screen with corresponding string resources
- Add script to automate Kotlin Compose hardcoded string migration to resources
- Add smart rewind functionality and improve audio player preparation logic
- Add support for custom audiobook scan paths with dedicated settings UI and database persistence
- Add support for external storage paths when resolving content URIs in FileUtils.getPathFromUri
- Add support for initiating torrent downloads via magnet link passed to DownloadsRoute
- Add swipe gestures and notification controls to mini player
- Add tests for media session custom commands for rewind and forward, and apply minor formatting to audio players
- Add topic details refresh functionality with loading indicator and consistent comment sorting
- Add torrent downloads screen, ViewModel, UI components, and associated string resources
- Add torrent file selection dialog and update downloads screen navigation
- Add torrent streaming monitoring with buffering and file readiness checks
- Add unit tests for MediaSessionManager and PlaybackController to ensure proper functionality of audio playback features, including callback handling, playback controls, and state management; remove
  outdated OnboardingViewModelTest for codebase cleanup
- Adjust Rutracker parsing column indices, improve bad request detection, and enhance author extraction from titles
- Apply zero window insets to TopAppBar in multiple feature screens, refine search bar logic, and update debug/topic screen titles
- Auto-initialize player with book data upon UI state success and update the library list view icon to be auto-mirrored
- Chapter search to filter by title/number instead of auto-jumping to chapter numbers
- Display active torrent downloads in the settings screen with status updates and navigation to the downloads route
- Display elapsed time for indexing progress in the UI and foreground service notification, and show clearing index status
- Display snackbar with settings link on notification permission denial in PlayerScreen
- Dynamically update MediaSession custom commands for rewind and forward durations
- Enhance `RutrackerTopicDetails` validation for paged results by introducing `isValidForPagination` and applying it in `RutrackerRepository`
- Enhance audio processing by integrating LoudnessNormalizer and SilenceSkippingAudioProcessor into the processor chain, improving playback quality and control
- Enhance AudioPlayerLibrarySessionCallback with media button event handling for next/previous track; add custom layout buttons for rewind and forward commands; optimize LoadControl settings in
  MediaModule for audiobooks with performance-based buffer adjustments
- Enhance backup/restore to include books, favorites, search history, scan paths, and extended settings
- Enhance chapter sorting logic with numerical and special chapter handling, and improve title extraction by falling back to filename
- Enhance cover art detection logic, defer cover art extraction during file system scanning, and optimize book and chapter insertion with batching and upsert
- Enhance debug screen with mirror health testing and cache management functionality
- Enhance Media3 integration with rich metadata, completion status, and improved Android Auto support
- Enhance onboarding screen visuals with cinematic gradients, text shadows, and redesigned buttons
- Enhance Rutracker authentication with detailed logging, robust error handling, and new debug info
- Enhance Rutracker HTML parsing by adding fallback CSS selectors and a robust row validation strategy
- Enhance Rutracker search with logging, refactor WebView navigation icons, add custom user agent, and implement URL fallback
- Enhance service logging and error handling for foreground indexing operations
- Enhance settings screen with improved cache clearing messages, customizable slider value formatting, and direct links to GitHub resources
- Enhance string migration script with improved filtering, statistics, and add string resource for unknown cache size
- Enhance topic detail display by parsing HTML spoilers and internal links, and enable navigation to linked topics
- Enhance TopicDetails and Comment models to include HTML descriptions for better formatting; update RutrackerParser to extract and clean HTML content, preserving links; modify TopicScreen to display
  HTML content in comments and descriptions, improving user experience with clickable links
- Externalize UI strings to resources for internationalization in player, settings, and library features
- Extract audiobook covers during library scan and skip scan if no scan paths are configured
- Extract embedded book covers during library scan and unify cover image loading across UI components
- Extract forum name from HTML to assign categories to topics and add related import
- Feat: Implement seek forward/backward controls in the player and refactor playback speed persistence to user preferences
- Feat: remove close button from BookPropertiesDialog
- Format chapter titles using `ChapterUtils` to include index and localized prefix
- Implement a persistent mini-player by tracking the last played book and displaying it in the main app UI
- Implement adaptive navigation using window size classes to display NavigationRail or BottomBar
- Implement add torrent dialog with download path selection and URI path resolution
- Implement app data backup and restore functionality for settings and book metadata
- Implement asynchronous notification artwork loading using Coil3 and re-enable the NotificationManager
- Implement automatic audio output switching between speaker and earpiece based on proximity sensor
- Implement avatar preloading for topic comments and add TopicViewModel tests
- Implement background cover loading for search results by adding a CoverLoader, updating RutrackerRepository to fetch and save covers, and modifying OfflineSearchDao to persist cover URLs and
  observe search results
- Implement book metadata and cover image synchronization using torrent topic ID
- Implement cache management in settings, allowing users to view and clear app cache
- Implement chapter repeat functionality in audio player; update PlayerViewModel to manage chapter repeat modes (OFF, ONCE, INFINITE) and integrate repeat logic in AudioPlayerController, enhancing
  user experience during playback; add UI elements in PlayerScreen for chapter repeat controls and update string resources for repeat modes
- Implement chapter search/jump in player, display library screen title, and remove redundant TopAppBar window insets
- Implement cleanup for non-existent scan paths and deleted books from the database
- Implement comment pagination for topic details
- Implement comment pagination in TopicScreen, loading newest comments first and allowing previous pages to be loaded
- Implement comprehensive audio playback, torrent management, and library improvements across the application
- Implement crossfade functionality with a new handler, enhanced player, and service integration for seamless track transitions
- Implement CrossFadePlayer for smooth audio transitions between tracks, including tests for functionality and integration with audio processing settings
- Implement current book ID tracking in AudioPlayerController to prevent data mixing between books; enhance state management on book change to reset playback state and ensure accurate audio playback
  experience
- Implement custom audio notification manager with media session integration and enable shared element transitions
- Implement download filtering, priority management, and queue reordering with a new database table
- Implement download history tracking and add new download speed and concurrency settings
- Implement ForumIndexer service for indexing audiobook forums on RuTracker; includes full and incremental indexing, cover preloading, and version tracking; enhance database schema with last_updated
  and index_version fields for cached topics; add search capabilities in OfflineSearchDao for indexed topics, improving offline search performance
- Implement functionality to move torrent storage to a new path
- Implement fuzzy search for indexed topics using raw SQL queries and add debug commands/logging for indexing
- Implement global crash handling, enhance the audio player with new UI components for lyrics and settings, and update the navigation drawer
- Implement hybrid local book scanning with direct file system access for custom paths and introduce shared audio file info
- Implement indexing functionality in SettingsScreen; add indexing progress tracking, user feedback for indexing status, and a dialog for indexing operations; enhance user experience with dynamic
  updates on index size and indexing status
- Implement IndexingForegroundService for background indexing; enhance IndexingProgressDialog with onHide callback to allow users to continue indexing in the background; update IndexingViewModel to
  manage foreground service initiation; modify SettingsScreen to integrate new dialog functionality
- Implement initial Android TV application with browsing and playback capabilities
- Implement initial onboarding flow with welcome, features, and permissions steps, and persist completion status
- Implement local audiobook scanning, sleep timer, and playback speed controls, and display download messages
- Implement main navigation and integrate core screens
- Implement Material 3 Adaptive UI for PlayerScreen and SearchScreen
- Implement MediaInfo parsing and models, and display details on the topic screen
- Implement migration of legacy Flutter player state from shared preferences to the database
- Implement multi-stage cookie persistence using new DAO, entity, and manager, integrated into the authentication flow and database
- Implement multi-strategy cover URL and topic field extractors for HTML parsing
- Implement native streaming torrent downloads
- Implement network monitoring to conditionally pause and resume torrent downloads based on network type and user preferences
- Implement offline caching and fallback for Rutracker search results with network-first strategy
- Implement offline search for Rutracker topics by caching results locally
- Implement PhoneCallListener to automatically resume audio playback after phone calls; update AudioPlayerService and AudioPlayerServiceInitializer to integrate phone call handling; enhance metadata
  extraction for embedded artwork with validation checks
- Implement playback speed and timer controls in AudioPlayerService; enhance PlayerWidgetProvider to support new widget actions for speed and timer; update widget layout for improved visual
  presentation and functionality; integrate Glide for better image loading in widgets
- Implement PlayerWidgetProvider for audio playback controls; add widget layout and update logic to reflect playback state changes; enhance PlayerListener and ServiceIntentHandler to trigger widget
  updates on playback actions
- Implement power save mode detection to optimize visual effects, enhance audio visualizer with improved data handling, and refactor lyrics management in the player view model; update UI components
  for better readability and performance
- Implement pull-to-refresh for library scanning, enhance library display with empty state, and add chapter reordering functionality
- Implement real-time library scan progress with UI integration and metadata caching
- Implement smart completion detection in PlayerListener to notify users when audio is within 3 minutes of the end; add click debouncer in PlayerScreen to prevent double clicks on playback controls,
  enhancing user experience
- Implement synchronization of local library favorites with FavoriteEntity; add extension function to convert Book to FavoriteEntity; update LibraryViewModel and FavoritesViewModel to manage favorite
  status and combine online and local favorites, improving user experience in managing favorite books
- Implement torrent details screen with file prioritization and per-file download progress
- Implement torrent download management including service, notifications, and core logic
- Implement torrent download persistence by adding a new database table and syncing manager state
- Implement torrent network pause/resume notifications and actual topic cache clearing and size calculation
- Implement track availability checks in PlaybackController for next/previous track navigation; enhance PlayerListener with auto rewind functionality on pause; integrate SuspendableCountDownTimer for
  sleep timer management; add user preferences for auto rewind settings; introduce TrackAvailabilityChecker for improved media item handling
- Implement unit testing and coverage reporting in Makefile; add corresponding GitHub Actions steps for automated testing and coverage verification. Enhance build.gradle.kts with JaCoCo configuration
  for test coverage management
- Implement updating book author and description during sync and remove TODOs
- Implement user preference to toggle chapter title normalization in player and settings
- Implement user-configurable download location and Wi-Fi only download settings, and add library scanning functionality
- Improve authentication with strict validation and WebView cookie synchronization, and update Android runtime permission requests
- Improve downloads storage display in settings with new dedicated strings
- Improve Rutracker parsing by prioritizing structural metadata extraction and enhancing fallback logic for topic details and search results
- Include normalize chapter titles in backup/restore and fetch current mirror from MirrorManager
- Increase forum indexing page limit to support larger forums
- Initialize player only with non-empty chapters and eagerly start player state flow to avoid race conditions
- Integrate Glide for optimized notification artwork loading, replacing DataSourceBitmapLoader
- Integrate new interceptor to add browser-like headers, including Brotli, to network requests
- Integrate PlaybackEnhancerService into AudioPlayerService for enhanced audio playback; initialize and release resources in service lifecycle; add volume boost level mapping and flow handling in
  PlaybackEnhancerService for dynamic audio adjustments
- Integrate torrent download repository to enable browsing and playing downloaded media items with enhanced metadata
- Internationalize favorites screen dialog and menu texts by adding new string resources
- Internationalize various UI and worker strings and refactor library scan worker for improved performance and batch processing
- Introduce AUDIOBOOKS_FORUM_IDS constant in RutrackerApi for audiobooks forum filtering; update RutrackerSearchViewModel to default forumIds parameter to AUDIOBOOKS_FORUM_IDS, enhancing search
  functionality for audiobooks
- Introduce AudioFader and ThrottledSeekHandler components with their respective tests
- Introduce centralized error handling with new extension functions for Throwable; implement RuTrackerError sealed class for specific error types; enhance RutrackerRepository to utilize new error
  handling; add safe parsing extensions for Jsoup Elements to prevent null pointer exceptions; update string resources for error messages in both English and Russian
- Introduce CollectionUtils with mapInstanceOf extension function for enhanced collection manipulation; update CoroutineUtils with SingleItemMutableSharedFlow for managing state with latest value
  retention, improving flow handling in coroutines
- Introduce HTML block parsing and spoiler rendering components, and update link annotation handling
- Introduce in-memory LRU cache for Rutracker search results, integrating it into the repository and cache management
- Introduce Jetpack Compose UI with new data layer for books and refactor audio player components: phase 1 and 2
- Introduce library sorting by activity, title, author, and date added, and track book completion and last played times
- Introduce mini player and compact book list view, and enhance debug screen with auto-refresh and error handling
- Introduce new audio playback features including periodic position saving, pitch correction toggle, and player stats overlay; update UI with glassmorphic card styles and premium background gradients
- Introduce NullableUtils and ResultUtils for enhanced null handling and result management; add utility functions for safer null operations and improved error handling in functional programming,
  enhancing code robustness and readability. Update existing utility functions for consistency
- Introduce ParsingValidators for centralized HTML content validation; implement functions to check for topic existence, regional access, authentication requirements, and content validity before
  parsing; enhance RutrackerParser to utilize these validators for improved error handling during HTML parsing
- Introduce pre-commit hook and script for automated cleaning of duplicate Android string resources
- Introduce RemoteImage and SimpleRemoteImage composables for enhanced remote image loading; implement error handling, loading indicators, and customizable options such as corner radius and color
  placeholders; provide a consistent and user-friendly way to display images in the application
- Introduce Room persistence for torrent downloads with new entity, DAO, and repository
- Introduce scripts for automatically fixing Explicit API mode violations in Kotlin files. The new Kotlin and Python scripts identify and correct visibility modifiers and return types, enhancing code
  compliance and maintainability
- Introduce sorting and filtering functionality to the Rutracker search screen, including UI, ViewModel logic, and data models
- Introduce SquigglySlider component for enhanced audio playback visualization, replacing standard Slider with a dynamic animated design that reflects playback state; update PlayerScreen to utilize
  the new component for improved user interaction
- Introduce unified book card, actions provider, and display modes for consistent book presentation and selection across Library, Search, and Favorites
- Introduce uploader and bitrate fields, enhance topic detail UI with new metadata rows, and update string resources
- Localize search screen by extracting strings to resources and adding an ARB to XML conversion script
- Migrate media notifications from custom provider to `androidx.media3.ui.PlayerNotificationManager` for improved background service reliability
- Migrate onboarding images to WebP, enhance permission screen with back/skip navigation, and adjust initial onboarding state handling
- Migrate to Gradle 9 for comprehensive building experience
- Mirror management with dynamic base URL, health checks, and persistent settings
- Optional avatar URL to Comment model; update RutrackerParser to extract avatar images; modify TopicScreen to display avatars alongside comments, improving visual context and user experience
- Order books by favorite status before last played date
- Parse and include user signatures in Rutracker comment HTML
- Persist book activity timestamps in player state and integrate into backup/restore functionality
- Playback speed control to AudioPlayerService and PlaybackController; enhance PlayerWidgetProvider to support playback speed and repeat mode updates; introduce new widget layouts for improved user
  experience and responsiveness
- Prevent audio playlist reloads and configure Media3 notification channels
- Proactively start audio player service for warmup, enhance its startup logging and error handling, and increment the build version
- Re-enable description cleanup for Rutracker topics and expand recognized metadata labels
- Realized StructuredLogger for consistent logging across the application; implement operation tracking, timing information, and various logging methods to enhance debugging and monitoring capabilities
- Remove download feature and its related components
- Replace hardcoded UI strings with string resources for localization across various screens and components
- Replace old search tests with a new test for `getTopicDetailsPage` using `runBlocking` and `TopicDetails` models
- Reposition and restyle LibraryScreen snackbar to be adaptive and bottom-aligned, and update PlayerScreen coroutine launches to use CoroutineStart.UNDISPATCHED
- Request Android 13+ notification permission, export audio service, and refine Media3 notification and media session integration
- Reset book completion state and last completed track index when play is invoked after book completion
- Rounded corners to cover images in MiniPlayer and TopicScreen; update image loading logic to utilize transformations for improved UI aesthetics, enhancing visual appeal in both components
- RutrackerParser with improved logging for topic ID extraction and title handling
- Support for comments and series information in TopicDetails; update RutrackerParser to extract seeders, leechers, comments, and series from topic pages; enhance TopicScreen UI to display comments
  and series details, improving user engagement and information accessibility
- Trigger immediate one-time sync and enhance SyncWorker with Hilt DI, limited retries, and cache cleanup
- Update FileProvider paths to include logs, downloads, and audiobooks, and align AndroidManifest authority and resource
- Update onboarding welcome and features images
- Use mipmap icon and remove tint on AuthScreen, filter `setRequestedFrameRate` from debug logs, and add detailed logging for Rutracker parsing failures

### Changed

- "System Default" string to "System (default)" for clarity
- Add .gitignore to exclude Quarto build artifacts and temporary files
- Add `report` target to analyze startup logs and generate a debug report
- Add `run-beta` and `run-beta-debug` Makefile targets and update `install-beta` to use `adb`
- Add Apache 2.0 license header to all files
- Add comprehensive architecture documentation in Quarto format with 45+ Mermaid diagrams
- Add deprecation suppression for hiltViewModel in feature screens
- Add deprecation suppression for onCallStateChanged in LegacyPhoneStateListener to maintain compatibility with future Android versions
- Add diagnostic DAO methods and enhance indexed search logging with detailed information and empty result checks
- Add Google Fonts support in build.gradle.kts; refactor font handling in AppFont to include multiple Google Fonts options; update JabookTheme and SettingsScreen to utilize selected font preference
  dynamically; enhance font selection UI for better user experience
- Add logic to request necessary permissions on app launch based on Android version
- Add missing line breaks in logging statements across multiple components including DirectFileSystemScanner, HybridBookScanner, AuthInterceptor, DynamicBaseUrlInterceptor, RetryInterceptor, and
  RutrackerHeadersInterceptor for improved readability and consistency in logging practices
- Add note about `RutrackerParserTest` requiring Android test framework and serving as documentation
- Add OkHttp Brotli support and enhance RutrackerRepository logging for Brotli decompression checks
- Add rutracker infrastructure data flow documentation
- Add RuTracker search screen and its navigation route
- Add sortOrder and viewMode properties to AppSettings; update BackupService to handle restoration of these settings; enhance user preferences management for improved app customization
- Add topic details screen with its viewmodel and navigation route
- Added ignore packages for copyright validation
- Added ktlint-strace for make hook
- Added unique pid for each player session and fixing startForground issue
- Adjust logging levels and messages for debug features and mirror health checks, distinguishing expected failures from errors
- Adjust Makefile build output paths by removing 'android/' prefix and trailing slashes
- Adjust PlayerScreen content padding and vertical item spacing
- Adjust SettingsScreen layout by adding top bar window insets, removing a horizontal divider, and reducing a spacer's height
- Application launcher and foreground icons across various densities
- Apply consistent formatting to onboarding feature files and remove unused import
- Apply system bar padding to player screen content and set fixed height and zero elevation for the navigation bar
- Assign topic categories immutably in parser using map instead of in-place modification
- Audio player notification and session initialization to use `DefaultMediaNotificationProvider` with a small icon and set custom commands
- Auto markdown formatter
- Autoincrement for beta-releases as a patch
- BackupService to determine app flavor from BuildConfig.APPLICATION_ID; implement fallback mechanism using versionName suffix for improved flavor detection; enhance error handling for BuildConfig
  retrieval
- Broaden string migration's file and technical string exclusion rules and streamline string replacement
- Bump Android SDK versions, enable ABI splits with universal APK generation, remove desugaring, and adapt Makefile for new APK output structure
- Bump formatting
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
- Bump patch version to 1.2.7+22
- Bump patch version to 1.2.7+23
- Bump patch version to 1.2.7+24
- Bump patch version to 1.2.7+25
- Bump patch version to 1.2.7+26
- Bump patch version to 1.2.7+27
- Bump patch version to 1.2.7+28
- Bump patch version to 1.2.7+29
- Bump patch version to 1.2.7+30
- Bump patch version to 1.2.7+31
- Bump patch version to 1.2.7+32
- Bump patch version to 1.2.7+33
- Bump patch version to 1.2.7+34
- Bump patch version to 1.2.7+35
- Bump patch version to 1.2.7+36
- Bump patch version to 1.2.7+37
- Bump patch version to 1.2.7+38
- Bump patch version to 1.2.7+39
- Bump patch version to 1.2.7+40
- Bump patch version to 1.2.7+41
- Bump patch version to 1.2.7+42
- Bump patch version to 1.2.7+43
- Bump patch version to 1.2.7+44
- Bump patch version to 1.2.7+45
- Bump patch version to 1.2.7+46
- Bump patch version to 1.2.7+47
- Bump patch version to 1.2.7+48
- Bump patch version to 1.2.7+49
- Bump patch version to 1.2.7+50
- Bump patch version to 1.2.7+51
- Bump patch version to 1.2.7+52
- Bump patch version to 1.2.7+53
- Bump patch version to 1.2.7+54
- Bump patch version to 1.2.7+55
- Bump patch version to 1.2.7+56
- Bump patch version to 1.2.7+57
- Bump patch version to 1.2.7+58
- Bump patch version to 1.2.7+59
- Bump patch version to 1.2.7+60
- Bump patch version to 1.2.7+61
- Bump patch version to 1.2.7+62
- Bump patch version to 1.2.7+63
- Bump patch version to 1.2.7+64
- Bump patch version to 1.2.7+65
- Bump patch version to 1.2.7+66
- Bump patch version to 1.2.7+67
- Bump patch version to 1.2.7+68
- Bump patch version to 1.2.7+69
- Bump patch version to 1.2.7+70
- Bump patch version to 1.2.7+71
- Bump patch version to 1.2.7+72
- Bump patch version to 1.2.7+73
- Bump patch version to 1.2.7+74
- Bump patch version to 1.2.7+75
- Bump patch version to 1.2.7+76
- Bump patch version to 1.2.7+77
- Bump patch version to 1.2.7+78
- Bump patch version to 1.2.7+79
- Bump patch version to 1.2.7+80
- Bump patch version to 1.2.7+81
- Bump patch version to 1.2.7+82
- Bump patch version to 1.2.7+83
- Bump patch version to 1.2.7+84
- Bump patch version to 1.2.7+85
- Bump patch version to 1.2.7+86
- Bump patch version to 1.2.7+87
- Bump patch version to 1.2.7+88
- Bump patch version to 1.2.7+89
- Bump patch version to 1.2.7+90
- Bump patch version to 1.2.7+91
- Bump pub build
- Bump theme localization
- Centralize date and time formatting with a new DateTimeFormatter utility and apply it to backup and settings
- Centralize playback speed constants and update player and settings UI to utilize them
- Change access modifiers for DataManagementActivity and JabookApplication classes and their members to public, enhancing visibility and accessibility across the application
- Change access modifiers for various classes and their members to public, enhancing visibility and accessibility across the application, including JabookApplication, ManufacturerSettingsHelper,
  AudioFader, AudioOutputManager, AudioPlayerLibrarySessionCallback, AudioPlayerNotificationProvider, and AudioPlayerService
- Change access modifiers for various classes and their members to public, including AudioPlayerServiceInitializer, AudioVisualizerManager, AutoSaveManager, ChapterUtils, CompletionStatusHelper,
  CollectionUtils, CoroutineUtils, NullableUtils, and StringUtils, enhancing visibility and accessibility across the application
- Change BrotliInterceptor to a network interceptor for automatic Brotli decompression
- Change logging level from debug to warning in Rutracker parser and repository
- Clarify torrent error tracking limitations and buffering state comments
- Clean up code formatting and remove unnecessary imports in audio modules for improved readability
- Clean up each cache before compilation testing
- Clean up unused imports and apply minor formatting to settings and topic screens
- Clean up whitespace and refactor variable declarations across multiple Kotlin files to enhance code clarity and maintain compliance with Explicit API mode
- Configure release build to use release signing instead of debug keys
- Consolidate and simplify R8 rules for Kotlinx Serialization, Hilt, and other libraries
- CoverUrlExtractor to implement 6 priority-based strategies for extracting cover URLs; update comments for clarity and accuracy; improve URL normalization to ensure consistent loading from CDN;
  refine logging for better debugging of cover URL extraction process
- DebugLogService to export logs and share via Android Share API; refactor DebugScreen to handle Activity context for sharing logs; improve error handling in DebugViewModel for sharing logs
- Delegate field and cover URL extraction to dedicated services using injected extractors
- Directly set next media source for crossfade in `AudioPlayerService` and update `CrossfadeTest` with new testing utilities
- Disable cover preloading during forum indexing to reduce index size and improve performance; update CachedTopicEntity to not store magnetUrl, torrentUrl, and coverUrl, retrieving them on-demand via
  getTopicDetails(); enhance documentation for clarity on indexing optimizations
- Disable PlayerNotificationManager in audio service
- Display debug logs using LazyColumn and remove fetching of all error logs
- Enhance adaptive UI across multiple screens by integrating AdaptiveUtils for effective window size class handling; implement dynamic content padding and item spacing in DownloadHistoryScreen,
  PlayerScreen, RutrackerSearchScreen, SettingsScreen, TopicScreen, TorrentDetailsScreen, and TorrentDownloadsScreen; ensure consistent layout responsiveness for improved user experience on various
  devices
- Enhance adaptive UI across multiple screens; implement dynamic content padding and item spacing based on window size class; integrate AdaptiveUtils for improved layout responsiveness in
  DownloadHistoryScreen, RutrackerSearchScreen, SettingsScreen, TopicScreen, TorrentDetailsScreen, and TorrentDownloadsScreen; ensure consistent spacing and padding for better user experience on
  various screen sizes
- Enhance adaptive UI in SettingsScreen and TopicScreen by integrating dynamic content padding and item spacing; update AdaptiveUtils for effective window size class handling; ensure consistent
  layout responsiveness across various screen sizes
- Enhance alert handling in TorrentSessionManager by adding comprehensive logging for various alert types; improve error handling and debugging capabilities; include additional alert types for better
  monitoring of torrent states and events
- Enhance alert handling in TorrentSessionManager by specifying alert types explicitly to improve efficiency and avoid potential issues; update session initialization to prevent automatic alert
  listener, aligning with libretorrent practices for better stability and debugging
- Enhance audio playback error logging in PlayerListener; improve playlist loading logs in PlaylistManager with detailed track information; implement structured logging for database migrations in
  DatabaseModule; add navigation logging in JabookAppState and JabookNavHost for better traceability of user actions
- Enhance AudioPlayerLibrarySessionCallback to prevent playback resumption for completed books and improve error handling for missing media items. Optimize PlayerScreen and SquigglySlider for
  performance with derived state and value sanitization. Introduce utility functions in ComposeUtils for better UI management
- Enhance AuthInterceptor for improved automatic re-authentication handling and update NetworkModule with logging interceptor notes and timeout adjustments
- Enhance back gesture handling to always intercept and provide screen exit fallback
- Enhance code clarity by updating function parameters and variable types in TorrentDetailsScreen, TorrentDetailsViewModel, and TorrentDownloadsViewModel, ensuring compliance with Explicit API mode
- Enhance cover URL validation in CoverUtils; improve cover image handling by ensuring URLs are not blank and follow valid formats; update UnifiedBookCard and TopicScreen for better text styling and
  spacing; add download functionality in TopicViewModel for torrent management
- Enhance CoverUrlExtractor and RutrackerParser for improved URL normalization; implement CDN support for cover and avatar URLs to ensure consistent image loading; add robust extraction logic for
  cover images from various HTML structures; improve logging for better debugging insights
- Enhance database configuration in MediaModule and DatabaseModule; implement coroutine context for queries, enable foreign key constraints, and add lifecycle callbacks for better performance and
  debugging; update JabookDatabase to export schema for migration validation
- Enhance DebugLogService with app-specific log tags and system log patterns; improve log collection by filtering out system noise and summarizing log statistics; update ForumIndexer and
  RutrackerParser with additional logging for better debugging and tracking of operations
- Enhance error handling and synchronization in RutrackerSearchCache and ForumIndexer; implement network error detection and mirror switching logic in DynamicBaseUrlInterceptor; improve logging for
  better debugging insights; ensure robust handling of exceptions in TorrentSessionManager; update DebugViewModel to handle null cases when loading cache statistics
- Enhance fix-explicit-api.py to add missing public visibility modifiers and return types for const val and var/val properties, improving compliance with Explicit API mode. Updated regex patterns to
  handle various cases for type inference
- Enhance fix-explicit-api.py to improve detection of function bodies by expanding regex patterns and adding checks for lambda expressions, ensuring accurate removal of 'public' from local variable
  declarations, thereby enhancing compliance with Explicit API mode
- Enhance fix-explicit-api.py to improve detection of local variables by adding checks for class declarations, ensuring accurate removal of 'public' keywords from local variable definitions while
  preserving class properties, thereby enhancing compliance with Explicit API mode
- Enhance fix-explicit-api.py to improve handling of multiple statements and string continuations, ensuring accurate splitting and compliance with Explicit API mode
- Enhance fix-explicit-api.py to improve regex patterns for splitting multiple statements and remove 'public' from local variable declarations, ensuring better compliance with Explicit API mode
- Enhance fix-explicit-api.py to improve regex patterns for splitting multiple statements, adding support for additional keywords and method calls, ensuring better compliance with Explicit API mode
- Enhance fix-explicit-api.py to remove 'public' from local variables inside function bodies, split multiple const val declarations into separate lines, and ensure proper visibility modifiers for
  class properties, improving compliance with Explicit API mode
- Enhance fix-explicit-api.py to split multiple statements on the same line into separate lines, improving readability and compliance with Explicit API mode
- Enhance foreground service initialization for Android 14+ and standardize notification ID
- Enhance ForumIndexer and related components to ensure accurate indexing of audiobook forums; implement validation for allowed forums, improve logging for indexing progress and completion, and
  utilize database count as the single source of truth for indexed topics; update IndexingProgressDialog and SettingsScreen to reflect accurate index size from the database
- Enhance ForumIndexer to reduce progress update frequency for smoother indexing experience; improve pagination detection in RutrackerParser for accurate topic retrieval; update IndexingProgress
  calculation for better accuracy; ensure user authentication check in IndexingViewModel before starting indexing process
- Enhance logging in EncodingDetector and MetadataCache by adding missing line breaks for improved readability. Simplify log message formatting in DirectFileSystemScanner to maintain consistent
  logging practices across components
- Enhance logging in RutrackerSimpleDecoder and RutrackerRepository to include detailed decoding process and content encoding checks
- Enhance logging throughout ForumIndexer, DynamicBaseUrlInterceptor, MirrorManager, RutrackerParser, and RutrackerRepository; add detailed timing and memory usage metrics for indexing and network
  requests; improve error handling and reporting for better debugging and performance tracking
- Enhance logging throughout the search and mapping processes to provide better insights into invalid data; improve validation checks in RutrackerMapper and SearchViewModel to filter out invalid
  search results and log relevant warnings; add debugging logs in UnifiedBooksView and SearchScreen to track rendering and data integrity of books; ensure consistent error handling and logging in
  RutrackerSearchViewModel for improved clarity on search operations
- Enhance PlayerScreen by integrating AudioMetadataParser for improved author metadata retrieval; update UI to display author information above the cover; refine button layout and spacing for better
  responsiveness; remove unused timer string resources for cleaner localization
- Enhance PlayerScreen for adaptive UI; implement dynamic sizing for buttons and padding based on window size class; improve layout responsiveness for compact screens; integrate AdaptiveUtils for
  better content spacing and padding management
- Enhance PlayerScreen layout by adjusting control button sizes, spacing, and ergonomics for improved user experience; implement additional spacers for better visual separation of elements
- Enhance PlayerWidgetProvider to utilize MediaController for improved player state retrieval; implement fallback mechanism to AudioPlayerService for widget updates; streamline widget UI updates with
  better error handling and logging
- Enhance playlist loading synchronization and UI state management; implement mutex for concurrent playlist operations and refactor position application logic in AudioPlayerController
- Enhance ProGuard rules for Kotlinx Serialization, Hilt, Room, and Retrofit, and remove `@SerialName` annotations from navigation routes
- Enhance RutrackerParser to improve metadata cleaning by removing cycle/series links, book lists, and advertising text; update TopicScreen layout for better readability by separating
  seeders/leechers and size/duration into distinct rows
- Enhance RutrackerRepository logging to include checks for HTML structure and potential Cyrillic content, and improve HTML preview handling for both UTF-8 and Windows-1251 encodings
- Enhance RutrackerSimpleDecoder with improved validation for decoding results, including checks for HTML structure and valid Cyrillic content
- Enhance screen reader experience by adding semantic descriptions and roles to various UI components
- Enhance string migration script safety and remove problematic string resources
- Enhance TorrentManager and related services with robust error handling for libtorrent4j initialization; add checks for class availability to prevent crashes and ensure graceful degradation of
  torrent functionality; update ProGuard rules to prevent obfuscation of libtorrent4j classes
- Enhance TorrentSessionManager and TorrentDownloadItem for improved error handling and data validation; add robust checks for torrent validity and download progress; update UI components to handle
  edge cases in display logic; improve logging for better debugging insights
- Enhance TorrentSessionManager to improve session management; add checks for session running state before adding torrents and resuming downloads; ensure proper listener addition order to capture
  alerts effectively; improve error logging for better debugging
- Enhance URL normalization in CoverUtils and RutrackerParser to utilize MirrorManager for consistent base URL handling; refactor cover URL extraction logic across multiple components to improve
  reliability and maintainability
- Enhance URL resolution in parsers by implementing baseUri in Jsoup.parse() for absolute URL handling; replace manual URL concatenation with absUrl() for improved reliability; optimize element
  selection with selectFirst() and selectStream() for better performance and efficiency in CategoryParser, CoverUrlExtractor, DefensiveFieldExtractor, and RutrackerParser
- Exclude test_results folder for copyright heads
- Extend AudioPlayerLibrarySessionCallback with custom commands for sleep timer and playlist management; introduce MediaControllerExtensions for streamlined command handling; refactor
  SleepTimerRepositoryImpl to utilize MediaController for state management; enhance AudioPlayerController to fully leverage MediaController for playlist operations, improving overall service
  interaction and responsiveness
- Extend AudioRepositoryModule to provide PlaybackPositionDao for managing playback position data; enhance audio data handling capabilities within the Hilt module
- Externalize favorite button content descriptions using string resources
- Externalize hardcoded strings in various Compose screens and add new string resources
- Externalize library folder management and other UI strings for localization in settings screens
- Extract MainActivity logic to handlers and fix missing methods
- Fmt
- Format kt
- Formats
- ForumIndexer for improved readability and maintainability; streamline cover URL extraction logic; enhance OfflineSearchDao with unused column optimization; update RutrackerParser to support
  encoding-aware parsing of response bodies; modify database schema to include additional metadata fields for indexed topics
- Gitignore
- Guaranteed forward to library window from anything via navigation panel button
- Ignore backup file
- Ignore xml backup
- Implement adaptive layout features across book components using WindowSizeClass; enhance UnifiedBookCard, BookDetailPane, and UnifiedBooksView for improved responsiveness and visual consistency;
  refactor BookDisplayMode to support adaptive grid configurations; update theme with custom shapes for a modern look
- Implement adaptive padding using Material3 WindowSizeClass and add status bar padding to PlayerChapterPane
- Implement batch processing for library scanning with incremental progress updates and improved error handling
- Implement createCoverImageRequest function in CoverUtils for enhanced book cover image loading; update various components to utilize the new function for improved image handling and customization
  options
- Implement custom rewind/forward media session commands, force Android compile SDK to 34, and update flutter_media_metadata to a path dependency
- Implement environment-specific beta and production color themes, replacing the default Material 3 color schemes
- Implement foreground service for audiobook indexing in IndexingViewModel to allow background processing with notification support; update SearchScreen and RutrackerSearchScreen to handle indexing
  status and provide user feedback; enhance SettingsScreen to initiate indexing with context; improve notification visibility in IndexingForegroundService for better user awareness during indexing
  operations
- Implement jumpToTrack functionality, increase playback position saving frequency, and add extensive logging for audio bridge events
- Implement search and sort functionality in FavoritesScreen; add search query and sort order states in FavoritesViewModel; update UI to include search input and sort options, improving user
  experience in managing favorite books
- Implement toast feedback for torrent downloads and deep link navigation to the downloads screen from both torrent start and notifications
- Improve audio playback reliability by ensuring non-dismissible notifications and saving playback position in various scenarios; update PlayerViewModel to restore saved position from database on
  initialization, enhancing user experience during playback interruptions
- Improve authentication logging by adding User-Agent details in RutrackerAuthService; update RutrackerHeadersInterceptor to use device-specific User-Agent for requests, ensuring better compatibility
  and bot detection avoidance
- Improve auto-increment experience
- Improve changelog generation mechanism
- Improve ChapterSelectorSheet with fixed header and search field; add keyboard handling for better user experience; ensure smooth dismissal of the chapter selector; update PlayerScreen to start
  playback immediately upon chapter selection
- Improve code formatting and organize imports
- Improve code readability by reformatting logger calls, function signatures, and simplifying type imports
- Improve code readability in PlaybackEnhancerService and PlayerConfigurator by formatting coroutine calls; remove unused import in TrackAvailabilityChecker
- Improve DebugLogService with safety limits for log collection to prevent infinite loops; add timeout handling for logcat process to avoid hanging; enhance DebugViewModel with error handling for
  logger operations and ensure graceful failure management during log loading and authentication refresh processes
- Improve encoding detection and mojibake fixing by removing verbose logs, adding control character penalties, and adjusting confidence thresholds to prevent false positives
- Improve error handling and logging in PlaylistManager; implement timeout for track switch events and optimize test behavior for media item loading
- Improve error handling in PlayerConfigurator for user preferences retrieval; ensure auto rewind settings default to safe values; enhance PlayerListener to conditionally handle auto rewind based on
  player type
- Improve error handling in RutrackerRepository to prevent user confusion by returning empty lists for bad requests and parsing errors; update logging for better clarity on search failures and index
  issues
- Improve error handling in TorrentManager and TorrentSessionManager; add checks for initialization success and handle native exceptions during torrent operations; ensure only magnet URIs are
  accepted for downloads in TopicViewModel
- Improve index verification logging in ForumIndexer
- Improve playback speed display formatting, refactor bottom navigation bar to use app state, and adjust UI paddings
- Improve RutrackerParser with additional CSS selectors for better row handling; enhance logging for debugging row parsing, including detailed error messages and validation checks; implement a clear
  index feature in IndexingViewModel with user confirmation in SettingsScreen for improved index management
- Improve RutrackerParser with enhanced logging for missing topic IDs and titles; implement fallback logic for torrent URL construction; refactor title extraction to handle various cases and reduce
  code duplication, ensuring more robust parsing and clearer error reporting
- Improve RutrackerRepository to prioritize indexed search for audiobooks; update search logic to handle index existence and size; enhance logging for search results; add getIndexMetadata method in
  IndexingViewModel for index statistics; modify SettingsScreen to display index metadata and improve user feedback on indexing status
- Improve string resource handling by consolidating access, fixing formatting, and removing obsolete keys
- Improve topic validation in ForumIndexer to filter out invalid topics before indexing; enhance logging to provide clearer insights on filtered topics; update RutrackerSimpleDecoder to handle
  decoding errors more gracefully and log only critical issues; refine error logging in RutrackerParser to differentiate between critical and non-critical parsing errors for better debugging
- Improve torrent management by adding validation for magnet URI format, enhancing error handling during torrent addition, and ensuring proper directory creation and write permissions; update
  TopicViewModel to prefer magnet URLs and validate download URL formats, along with improved logging for error scenarios
- Improve translate quality and checking dry-run mechanism
- Improve UI responsiveness in TopicScreen by implementing adaptive layout for description and comments; update JabookApp to conditionally display mini player based on navigation state, enhancing
  user experience during playback
- Improve URL normalization in CoverUtils to handle relative URLs; update TorrentSessionManager to resume torrents after adding; refine description formatting in TopicScreen to preserve line breaks
  while normalizing whitespace
- Increase disk cache size for cover images to 5% for improved UX; add support for hardware bitmaps in image requests for better performance; implement image cache size tracking and clearing in
  CacheManager; update TopicScreen to prioritize cover image loading
- Increase Rutracker logo size on AuthScreen
- Increase timeout for compilation in fix-explicit-api.py and enhance visibility modifiers for properties and functions, ensuring compliance with Explicit API mode. Added return type handling for
  various function types and property getters
- Increase visibility of settings composables and update audio settings string resource keys
- Initialize default values for lastPlaylistLoadTime and currentPosition, and remove unnecessary imports across multiple Kotlin files to enhance code clarity and maintain compliance with Explicit API
  mode
- Integrate Firebase setup in CI workflow, add google-services.json handling, and include Google services Gradle plugin in build configuration for enhanced Firebase support
- Integrate MediaController for AudioPlayerService to enhance Media3 compatibility; implement smart layout updates and player state transfer functionality; refactor AudioPlayerController to utilize
  MediaController for state management and commands, replacing deprecated getInstance() pattern
- Introduce `NONE` log level, explicitly type logger methods, simplify torrent creation date assignment, and remove audiobook-specific forum indexing validation
- Introduce CoroutineUtils for enhanced coroutine management; add new extension functions for creating cancelable scopes and relaunching coroutines, improving lifecycle handling and simplifying
  common operations. Update NetworkModule to use @Binds for NetworkMonitor interface binding, optimizing performance
- Localize default string resources to English and update `TestComposeScreen` usage
- Logging in RutrackerRepository for search operations, including detailed timing for database queries and mapping processes; enhance error handling to provide clearer feedback on search failures and
  index status, ensuring better user insights during search operations
- Logging infrastructure and introduce a structured `Result` type for error handling
- Make Text() string extraction regex more flexible to include additional arguments
- Migrate audio notification management to Media3 MediaSession, removing the custom NotificationManager
- Migrate player to new bridge API with Kotlin state persistence
- Migrate PlayerListener notification updates and position checks to use coroutines for debouncing and improved cancellation
- Migrate string management from custom `Strings.kt` to standard `R.string` resources
- Migrate to DataStore + Tink encryption for credentials
- Migrate to Java 21 and replace kapt with KSP for Room
- Modify AudioPlayerService to stop only when notification is dismissed by the user, enhancing service management; update InactivityTimer to extend default inactivity timeout from 40 to 60 minutes,
  improving user experience during prolonged playback
- Move to test_results
- Optimize ForumIndexer by removing delay between requests for faster indexing; update RutrackerParser to clean up metadata and reverse comment order for better user experience; adjust TopicScreen
  for improved readability on small screens by reducing preview length and max lines in collapsed state
- Optimize ForumIndexer for faster indexing by reducing request delay and increasing concurrent processing; enhance IndexingProgress calculation for improved accuracy; update RutrackerParser to clean
  metadata from HTML and reverse comment order for better user experience; adapt cover image sizes in RutrackerSearchScreen and TopicScreen based on device orientation for improved layout
  responsiveness
- Optimize ForumIndexer for improved performance and indexing accuracy; implement parallel processing for forum indexing, enhance progress reporting with IndexingProgress updates, and introduce
  batching for database writes; update RutrackerParser regex for better description extraction; refine search strategy in RutrackerRepository to prioritize indexed results; enhance
  RutrackerSearchScreen with indexing state management and user feedback for indexing status
- Optimize library scan performance and cleanup obsolete TODOs
- Optimize list rendering in various screens by implementing key-based item tracking in LazyColumn; improve performance and reduce unnecessary recompositions in DebugScreen, FavoritesScreen,
  TopicScreen, FileSelectionDialog, and TorrentDetailsScreen
- Optimize player UI for compact screens
- Optimize ProGuard rules for Kotlinx Serialization, Hilt, Room, and DataStore Proto, remove Gson, and use `@SerialName` in navigation routes
- PlayerScreen layout for compact screens by adjusting item spacing, padding, and typography
- Polish navigation UI and resolve deprecations
- Preserve XML comments and formatting when adding new strings to `strings.xml`
- Re-change clean params for linting
- Refactor adaptive layout handling in book components; replace LocalConfiguration with LocalContext for WindowSizeClass calculations in UnifiedBookCard, BookDetailPane, and UnifiedBooksView; improve
  line height scaling in AdaptiveUtils for better text rendering
- Refactor and enhance code clarity by updating variable types, removing unnecessary imports, and cleaning up whitespace across multiple Kotlin files to maintain compliance with Explicit API mode
- Refactor audio player service architecture
- Refactor AudioPlayerService and NotificationManager to utilize a new utility function for capitalizing the first letter of strings, enhancing code readability and consistency. Introduce StringUtils
  and RetryUtils for additional string manipulation and retry logic with exponential backoff, improving error handling capabilities
- Refactor AuthInterceptor, TorrentDownloadService, and TorrentSessionManager to utilize LoggerFactory for structured logging, replacing direct Android log calls with logger methods. Enhance logging
  clarity and error handling throughout authentication, torrent downloading, and session management functionalities
- Refactor BackupService and SleepTimerRepositoryImpl to utilize LoggerFactory for structured logging, replacing direct Android log calls with logger methods. Enhance error handling and logging
  clarity throughout the backup and sleep timer functionalities
- Refactor CacheManager, LibTorrentDownloader, StubTorrentDownloader, Media3MetadataParser, MetadataCache, RutrackerMapper, and TorrentDownloadRepository to utilize LoggerFactory for structured
  logging, replacing direct Android log calls with logger methods. Enhance logging clarity and error handling throughout caching, downloading, metadata parsing, and torrent management processes
- Refactor ComposeMainActivity, CoverPreloader, FontUtils, UnifiedBookCard, DatabaseModule, JabookAppState, JabookNavHost, and TorrentActionReceiver to utilize LoggerFactory for structured logging,
  replacing direct Android log calls with logger methods. Enhance logging clarity and error handling throughout activity lifecycle management, image preloading, font loading, navigation, and torrent
  actions
- Refactor CookiePersistenceManager and TorrentManager to utilize LoggerFactory for structured logging, replacing direct Android log calls with logger methods. Enhance logging clarity and error
  handling throughout cookie persistence and torrent management functionalities
- Refactor DebugLogService, DebugViewModel, UnifiedBooksView, PlayerScreen, AudioPlayerController, SearchScreen, CoverLoader, AvatarPreloader, and TorrentStreamingMonitor to utilize LoggerFactory for
  structured logging, replacing direct Android log calls with logger methods. Enhance logging clarity and error handling throughout debugging, UI rendering, audio playback, search functionalities,
  and cover loading processes
- Refactor DebugScreen and DebugViewModel to utilize collectAsStateWithLifecycle for better lifecycle management; enhance tab functions to accept tabIndex for improved state handling; update error
  handling in refreshAuthDebugInfo method to provide more informative error states
- Refactor DebugScreen to simplify string resource handling; update DebugViewModel to improve mirror availability checks and add error handling for mirror access, ensuring safer initialization and
  logging during validation processes
- Refactor delay calculation in RetryConfig to use Kotlin's extension function for power, enhancing code clarity and maintainability
- Refactor dependencies in build.gradle.kts to use version variables for libtorrent4j and Coil; enhance JabookApplication to configure Coil ImageLoader with OkHttpClient from Hilt for improved image
  loading performance and caching
- Refactor dependency management in build.gradle.kts to utilize version catalog for improved maintainability and clarity. Introduce a new logging system with structured error handling through
  AppError and Logger interfaces, enhancing logging capabilities across multiple ViewModels and repositories
- Refactor DirectFileSystemScanner and HybridBookScanner to utilize LoggerFactory for structured logging, replacing direct Android log calls with logger methods. Improve logging clarity and error
  handling throughout file scanning processes
- Refactor EncodingDetector, DefensiveEncodingHandler, and RutrackerSimpleDecoder to utilize LoggerFactory for structured logging, replacing direct Android log calls with logger methods. Enhance
  logging clarity and error handling throughout encoding detection and decoding processes
- Refactor FavoritesMigrations, OfflineSearchMigrations, DirectFileSystemScanner, and RutrackerRepository to utilize LoggerFactory for structured logging, replacing direct Android log calls with
  logger methods. Enhance logging clarity and error handling throughout database migrations, file scanning, and remote repository operations
- Refactor FontSelector in SettingsScreen to use ExposedDropdownMenu for improved font selection UI; streamline font option display and enhance user interaction with dropdown functionality
- Refactor function parameters in FavoritesViewModel, LibraryViewModel, and related screens to enhance code clarity and maintain compliance with Explicit API mode
- Refactor image request creation in CoverUtils for better readability; update various components to utilize the streamlined method for cover image requests, enhancing code consistency and
  maintainability
- Refactor logging implementation in IndexingViewModel, RutrackerSearchViewModel, and TorrentDownloadsViewModel to utilize a new LoggerFactory for improved structured logging. Update error handling
  in TopicViewModel to provide more specific error messages, enhancing overall code clarity and maintainability
- Refactor logging statements across multiple components to enhance error handling and readability by standardizing the logging format. This includes updating logger calls to use structured logging
  practices in various services, ensuring consistent error reporting and improved clarity in log messages
- Refactor logging statements in RutrackerRepository, SyncManager, SyncWorker, and various components to improve readability by adding missing line breaks. Update imports in multiple files to utilize
  LoggerFactory for consistent logging practices across the application
- Refactor MediaStoreBookScanner, CategoryParser, and SyncWorker to utilize LoggerFactory for structured logging, replacing direct Android log calls with logger methods. Enhance logging clarity and
  error handling throughout media scanning, category parsing, and synchronization processes
- Refactor multiple components including ComposeMainActivity, BackupService, CacheManager, DebugLogService, LibTorrentDownloader, StubTorrentDownloader, and ForumIndexer to utilize LoggerFactory for
  structured logging. This update enhances logging clarity and error handling across various services and activities, ensuring consistent logging practices throughout the application
- Refactor network interceptors and parsers to utilize LoggerFactory for structured logging, replacing direct Android log calls with logger methods. Enhance logging clarity and error handling
  throughout network request interception and data parsing processes
- Refactor NotificationManager and PlaylistManager to utilize new utility functions for formatting flavor suffixes, enhancing code readability and consistency. Introduce StringUtils methods for
  appending and formatting flavor suffixes, improving title display logic
- Refactor playlist loading strategy to optimize performance for small and large playlists; implement synchronous loading for small playlists and asynchronous loading for larger ones
- Refactor regex declaration in RutrackerParser.kt for improved clarity and compliance with Explicit API mode
- Refactor RutrackerAuthService, ForumIndexer, and MirrorManager to utilize LoggerFactory for structured logging, replacing direct Android log calls with logger methods. Enhance logging clarity and
  error handling across authentication, indexing, and mirror management functionalities
- Refactor RutrackerParser to utilize LoggerFactory for structured logging, replacing direct Android log calls with logger methods. Enhance logging clarity and error handling throughout the parsing
  process for search results, forum pages, and topic details
- Refactor RutrackerRepository to implement an index-only search strategy for audiobooks, ensuring fast offline results without network requests; improve logging for search operations and handle
  empty index scenarios gracefully by returning empty results instead of errors
- Refactor RutrackerRepository to utilize domain models for search results and topic details; improve error handling and validation for topic details retrieval; enhance caching logic to store and
  return domain results; update network module with retry interceptor and improved timeout settings for better performance and reliability
- Refactor variable assignments for improved readability in TopicViewModel, TorrentStreamingMonitor, and PlayerWidgetProvider, ensuring compliance with Explicit API mode
- Refactor variable declarations and remove unnecessary whitespace in RutrackerParser.kt and RutrackerRepository.kt for improved code clarity and compliance with Explicit API mode
- Refactor whitespace and variable declarations in RutrackerRepository.kt to enhance code clarity and maintain compliance with Explicit API mode
- Refine CoverUrlExtractor to implement a 5-level extraction strategy for cover images; remove domain validation for image URLs, allowing any valid extension with HTTP(S) scheme; improve selector
  logic for better URL extraction in RutrackerParser, enhancing overall image handling capabilities
- Refine fix-explicit-api.py to enhance the splitting of multiple statements on the same line by recognizing additional keywords, improving readability and compliance with Explicit API mode
- Refine fix-explicit-api.py to improve handling of multiple const val declarations by splitting them into separate lines, ensuring proper indentation and visibility modifiers, thereby enhancing
  compliance with Explicit API mode
- Refine hardcoded string detection in `migrate_strings.py` to exclude non-UI files and technical strings
- Refine HTML content validation in RutrackerParser to allow empty results for valid search and index pages; improve error handling and logging for better clarity on validation issues
- Refine onboarding screen animations by adjusting parallax, 3D tilt, and text fade/slide effects based on page offset, and remove the unused `isSelected` parameter
- Reformat long JSON string literal for readability in PlayerPersistenceManagerTest
- Remove devDebugKotlin compilation from Makefile's compile target
- Remove direct torrent download UI and logic from topic feature, consolidating download management
- Remove explicit navigation transitions for Player screen and add background color to PlayerScreen
- Remove extraneous blank line in player route deep link configuration
- Remove Flutter-related TODOs and update comments in `AudioPlayerService`
- Remove format strings for numbers and dates from `strings.xml` and update migration script to identify them as technical
- Remove legacy PNG icons and replace them with new WebP versions for improved performance and reduced file size across various drawable and mipmap densities
- Remove Lyricist i18n dependencies
- Remove redundant changelog generation confirmation message
- Remove redundant manual notification update call as MediaSession handles it automatically
- Remove stray 'public' keyword from local variable declaration in BackupService.kt, enhancing code readability and compliance with Explicit API mode
- Remove stray 'public' keyword from local variable declaration in CacheManager.kt, enhancing code readability and compliance with Explicit API mode
- Remove stray 'public' keyword from local variable declaration in CategoryParser.kt, enhancing code readability and compliance with Explicit API mode
- Remove stray 'public' keywords from function declarations and local variable definitions in multiple Kotlin files, enhancing code readability and compliance with Explicit API mode
- Remove stray 'public' keywords from local variable declarations across multiple Kotlin files, enhancing code readability and compliance with Explicit API mode
- Remove stray 'public' keywords from local variable declarations across multiple Kotlin files, improving code readability and compliance with Explicit API mode
- Remove stray 'public' keywords from local variable declarations in DebugLogService.kt, enhancing code readability and compliance with Explicit API mode
- Remove stray 'Unit' return type from function declarations in multiple Kotlin files, enhancing code readability and compliance with Explicit API mode
- Remove trailing whitespace in PlaybackControllerTest.kt
- Remove unnecessary 'Unit' return type from function declarations and clean up whitespace in multiple Kotlin files to enhance code clarity and maintain compliance with Explicit API mode
- Remove unnecessary 'Unit' return type from function declarations and clean up whitespace in SyncManager.kt and SyncWorker.kt to enhance code clarity and maintain compliance with Explicit API mode
- Remove unnecessary imports across multiple Kotlin files to enhance code clarity and maintain compliance with Explicit API mode
- Remove unnecessary parameters from function declarations across multiple Kotlin files to enhance code clarity and maintain compliance with Explicit API mode
- Remove unnecessary return types from function signatures in Kotlin files, improving code clarity and compliance with Kotlin conventions
- Remove unnecessary return types from function signatures in multiple Kotlin files, enhancing code clarity and compliance with Kotlin conventions
- Remove unnecessary whitespace in AuthRepositoryImpl.kt to enhance code clarity and maintain compliance with Explicit API mode
- Remove unused app icons and fonts, and convert rutracker_logo from PNG to WebP
- Remove unused backup build file
- Remove unused calls for defalt params into compile
- Remove unused docs
- Remove unused flutter collab code from Kotlin
- Remove unused imports and reformat test methods
- Removed all flutter code from project; prepate to migrate native Kotlin code
- Rename preferences repository in OnboardingViewModel and update JabookApp to check permissions on resume with a skip option
- Rename string resource keys to English for improved clarity and maintainability
- Reorganize makefile automation and fix hack scripts
- Replace byte-count based Cyrillic encoding detection with confidence-based string decoding
- Replace custom drawable icons with built-in Media3 icons for rewind and forward commands in AudioPlayerLibrarySessionCallback and AudioPlayerService to prevent CustomAction conversion crashes;
  enhance code consistency
- Replace DefensiveEncodingHandler with RutrackerSimpleDecoder for consistent response decoding across components
- Replace hardcoded "Logged in as" string with a string resource
- Replace PlayerPersistenceManager play count tests with PlayerState data model tests
- Replaced playback speed radio button list with a slider and preset chips
- Return  media player service initializer
- Rutracker description parsing to prioritize direct extraction and simplify metadata cleanup regex
- Rutracker indexing schemas
- RutrackerAuthService and AuthRepositoryImpl with timeout handling for authentication validation; improve error logging for timeout and exception scenarios; update IndexingViewModel to utilize
  authStatus flow for more reliable authentication checks; refine PlayerScreen layout with adaptive control button sizes for improved responsiveness
- RutrackerAuthService and RutrackerRepository to implement centralized error handling using RuTrackerError sealed class; improve error logging and messaging for various network and authentication
  errors; ensure consistent error responses across different exception types
- Script for Android startup log analysis and enhance Kotlin serialization ProGuard rules for R8 Full Mode
- Search result parsing to handle various encodings and parsing outcomes
- Service warmup and using
- Simplify BookCard image loading state handling and improve accessibility
- Simplify logging in EncodingDetector by removing unnecessary line breaks in the log message for fixed garbled text, enhancing readability and maintaining consistent logging practices
- Simplify ProGuard rules by removing redundant library-specific configurations and refining Kotlinx Serialization rules for `@SerialName`
- Simplify RutrackerSimpleDecoder by removing redundant validation logic and clarifying decoding strategy comments
- Streamline audio player service initialization and notification provider setup, and remove reflection fallback for media style token
- Streamline cover URL extraction in RutrackerParser by utilizing CoverUrlExtractor for consistency; enhance metadata cleaning by expanding patterns to remove technical details and improve
  description extraction logic; refine author extraction with fallback mechanisms for better accuracy
- Streamline Gradle configuration, enable build caching, remove integration test plugin workaround, disable default WorkManager initialization, and generalize DataMigrationManager's DataStore usage
- Swipe-to-dismiss functionality for mini player in JabookApp; update MiniPlayer component to handle drag gestures and dismiss on threshold, improving user interaction and experience during playback
- TopicScreen and TopicViewModel with new download options for torrents; implement snackbar notifications for download status; add functionality to copy magnet links and download torrent files;
  update UI to include a dropdown menu for download actions
- TopicViewModel tests to mock initial topic details with total pages and remove specific comment order assertions
- Unification into one abstraction for easily linting
- Update access modifiers to public for various functions and properties across multiple Kotlin files to enhance code clarity and maintain compliance with Explicit API mode
- Update AUDIOBOOKS_FORUM_IDS in RutrackerApi to include additional forum and subforum IDs for enhanced audiobook search capabilities; modify error message handling in TopicScreen to display a
  default error message when no specific message is provided, improving user feedback during errors
- Update AudioPlayerService to prioritize MediaLibrarySession for notifications, ensuring system media player integration; modify PlayerScreen to remove redundant repeat mode display; clean up
  strings.xml and localized resources by removing infinite repeat symbol
- Update chapter name formatting to accept a localized prefix and introduce new string resources for UI elements
- Update CI workflows to improve Gradle wrapper generation and permissions; added checks for existing files and enhanced error handling
- Update copyright script to target Kotlin files instead of Dart and remove Flutter-specific exclusions
- Update cover size in PlayerScreen for improved visual consistency; set cover width to 88% for compact screens and 92% for larger screens to enhance layout responsiveness
- Update dependencies in build.gradle.kts and libs.versions.toml to correct the implementation reference for Material3 window size class, ensuring proper integration of the Compose library
- Update fix-explicit-api.py to refine the removal of stray 'public' keywords by enhancing regex patterns to account for both quoted values and variable declarations, improving formatting and
  compliance with Explicit API mode
- Update fix-explicit-api.py to remove stray 'public' keywords at the end of lines, improving formatting and compliance with Explicit API mode
- Update function parameters across multiple Kotlin files to include necessary context and enhance code clarity, ensuring compliance with Explicit API mode
- Update function parameters and access modifiers across multiple files for improved code clarity and compliance with Explicit API mode, including changes in JabookApplication, CrossFadePlayer,
  SettingsViewModel, TopicScreen, TorrentDetailsViewModel, TorrentDownloadsScreen, TorrentDownloadsViewModel, and WebViewScreen
- Update function parameters and access modifiers in SearchViewModel, CoverLoader, and RutrackerSearchViewModel to enhance code clarity and ensure compliance with Explicit API mode
- Update function parameters and variable types across multiple files for improved code clarity and compliance with Explicit API mode, including changes to AudioPlayerService, CrossFadePlayer,
  PlayerPersistenceManager, and BackupService
- Update function parameters and variable types across multiple Kotlin files to enhance code clarity and maintain compliance with Explicit API mode
- Update function parameters and variable types from Int to Long in PlayerViewModel and AudioPlayerController for improved code clarity and compliance with Explicit API mode
- Update function parameters and variable types in SearchViewModel and SearchScreen to enhance code clarity and ensure compliance with Explicit API mode
- Update function parameters and variable types to Long in TorrentNotificationManager and NetworkModule for improved code clarity and compliance with Explicit API mode
- Update function parameters to Long in AudioPlayerController, adjust access modifiers to public in SettingsViewModel, and modify onCheckedChange callbacks in SettingsScreen for improved code clarity
  and compliance with Explicit API mode
- Update function parameters to Long in AudioPlayerController, and adjust access modifiers to public in SearchViewModel and SettingsViewModel for improved code clarity and compliance with Explicit
  API mode
- Update function return types and add explicit return types in multiple Kotlin files to enhance code clarity and maintain compliance with Explicit API mode
- Update Hilt `@ApplicationContext` parameter annotation syntax and add `viewModel` import
- Update Hilt ViewModel imports and add trailing comma to enum definition + enhance downloads mechanism
- Update import statements in TorrentSessionManager to include AlertType for improved clarity and consistency in alert handling; streamline alert management practices in line with recent refactoring
  efforts
- Update Kotlinx Serialization ProGuard rules for enhanced R8 compatibility, adding specific keeps for core classes, `@SerialName` fields, and navigation routes
- Update ParsingValidators and RutrackerParser to improve forum page validation and parsing; add specific error handling for bad requests and implement forum-specific parsing logic to enhance
  robustness and reduce false positives during HTML content validation
- Update ProGuard rules by removing Flutter configurations and adding rules for Compose, Hilt, Room, DataStore, Media3, and WorkManager
- Update Rutracker API integration to use dynamic base URL from MirrorManager; refactor authentication and topic handling to utilize current mirror for URL generation; enhance WebView and TopicScreen
  with improved navigation and fallback mechanisms for login; streamline CategoryParser to resolve links using dynamic base URL
- Update RutrackerHeadersInterceptor to clarify Accept-Encoding handling, enhance logging in RutrackerRepository, and adjust interceptor order in NetworkModule for Brotli support
- Update RutrackerParser to use safer element selection methods for page title and leech text; improve error handling in RutrackerRepository by categorizing HTTP errors into specific RuTrackerError
  types; refactor DebugViewModel to incorporate StructuredLogger for enhanced logging during log loading and authentication refresh operations
- Update RutrackerParser to utilize safer string conversion methods; enhance RutrackerRepository with structured logging for search operations, error handling, and improved network request logging;
  refactor ErrorScreen to support throwable errors for better error messaging and handling
- Update SearchScreen to reflect indexing status with user feedback; implement logic to display appropriate messages based on indexing state; enhance notification channel settings in
  IndexingForegroundService for better visibility while maintaining low disturbance during indexing operations
- Update typography in theme files to use custom Inter fonts; modify JabookTheme to utilize system's default sans-serif font for better user experience; enhance Type.kt with InterFontFamily
  definition for consistent app styling
- Update variable types and access modifiers across multiple files for improved code clarity and compliance with Explicit API mode, including changes in DebugLogService, PlayerScreen,
  SettingsViewModel, TopicViewModel, and various Torrent-related classes
- Update variable types and default values across multiple Kotlin files to enhance code clarity and maintain compliance with Explicit API mode
- Update variable types from Int to Long and adjust default values across multiple Kotlin files to enhance code clarity and maintain compliance with Explicit API mode
- Update variable types from Int to Long for fade durations and other constants across multiple Kotlin files to enhance code clarity and maintain compliance with Explicit API mode
- Update variable types from Int to Long in BookEntity and ChapterEntity, and modify the updateSearchQuery function parameter in DownloadHistoryViewModel for improved code clarity and compliance with
  Explicit API mode
- Update variable types in RutrackerParser.kt for hasTitle and refactor whitespace in SyncWorker.kt and RutrackerSearchViewModel.kt to enhance code clarity and maintain compliance with Explicit API mode
- Update volume icon to `AutoMirrored` and remove redundant bitmap null check
- Use default `SilenceSkippingAudioProcessor` constructor and pass context to `DefaultAudioSink` builder
- Use Provider for AuthRepository injection in AuthInterceptor and condense its KDoc

### Fixed

- Add empty MIGRATION_7_8 to resolve database migration issues
- Add null safety for validation checks and display a placeholder for missing permissions in DebugScreen
- Add ProGuard rules for Navigation Compose SavedStateHandle serialization and remove explicit kotlinx-serialization-json dependency
- Add Rutracker logo and update authentication screen to display it with a smaller size
- Add try-catch blocks to DebugViewModel operations for improved error handling
- Copyright validation folders
- Correct comment loading order for older pages and add test for last page pagination parsing
- Correct Media3 notification initialization and reduce default inactivity timeout
- Corrected English string resources and updated Russian translations
- Correctly resolve static Rutracker CDN URLs for avatar and other content paths
- DebugViewModel to delay initialization and add comprehensive error handling during data loading; implement try-catch blocks to prevent crashes and ensure graceful failure management with
  appropriate logging for initialization issues
- DebugViewModel to delay initialization until viewModelScope is ready; implement Handler for asynchronous data loading with enhanced error handling and logging to prevent crashes during initialization
- DebugViewModel to use Flow's first() for safe mirror retrieval, enhancing error handling for mirror health checks and ensuring proper timeout propagation; improve logging for better clarity during
  failures
- DebugViewModel with timeout handling for authentication validation and mirror health checks; implement parallel processing for mirror checks to improve performance and prevent hanging during
  operations; ensure graceful failure management with appropriate logging for timeout scenarios
- Disable animation for fixing forward to library (back params) from player
- Disable KTagLib on Android 16+ due to FDSAN incompatibility and simplify ParcelFileDescriptor handling in metadata parsing
- Display book cover images in search results
- Duplicated and numeric strings with similar params for each project
- Ensure audio player notification remains persistent by always calling startForeground instead of conditionally stopping it
- Explicit API mode violations in Kotlin files
- Extend KTagLib disablement to Android 11+ (API 30) due to FDSAN incompatibility
- Fix chapter mismatch, player freeze, and local playback
- Fix compilation errors and improve playback position saving
- Fix logical relation with called alias param for each task in makefile
- Fix playback position restoration and prevent playlist loading conflicts
- Fix service initialization leaks and improve stability
- Flutter media start
- HttpCache validation
- Launch method for double notification due revalidation ddos actions
- Map offline search results using `toDomainFromIndex` and update parser and repository tests accordingly
- Migrate copyright management scripts from Dart to Kotlin files
- Move Lyricist's `ProvideStrings` to `JabookApp` and update `LocalStrings` imports
- Player restart logic and slider interaction
- Player validation
- Prevent duplicate player notification manager initialization and decrease notification update debounce delay
- Prevent KTagLib FDSAN errors by using separate file descriptors for metadata/artwork and enhance debug logs with comprehensive device info
- Refactor AudioPlayerService and AudioPlayerLibrarySessionCallback to improve MediaSession handling; ensure immediate foreground service start to prevent ANR issues, and set initial custom layout
  after MediaController initialization to avoid conversion crashes
- Refine metadata encoding correction to prevent UTF-16 corruption by selectively applying `fixGarbledText` based on mojibake detection
- Refine PlayerScreen back handler to only intercept when the chapters pane is open
- Remove `TopAppBar` from player screen and simplify search placeholder text
- Remove cover art processing from LibraryScanWorker, allowing UI to load covers directly from book folders
- Remove old BridgeModule between Kotlin and Flutter
- Remove parent category IDs from AUDIOBOOKS_FORUM_IDS constant
- Remove redundant null check for sort order assignment
- Remove unnecessary Flutter plugin loader from settings.gradle.kts; streamline project configuration
- Removed unused flutter params
- Removed unused flutter tests, implementations, libs
- Rename variable 'flags' to 'pendingIntentFlags' for clarity in PlayerWidgetProvider; ensure consistent use of pending intent flags across widget click handlers
- Resolve audio player bridging and playlist sorting issues
- Resolve compilation warnings and deprecated API usage
- Resolve Kotlin 2.2.0 compilation errors and update dependencies
- Resolve kotlinx-serialization version conflict in kapt and suppress manifest warnings
- Resolve player back navigation blank screen issue
- Room version and build namespace for validation
- Rutracker search parsing by adding cover URL extraction and enhancing selector robustness for existing fields
- Skip test files in the string migration script
- Temporarily disable mini-player functionality and its PlayerViewModel instantiation, adding TODOs for future state management
- Unresolved private params
- Unresolved private params for specific params
- Update beta APK installation to use arm64-v8a specific build
- Update Rutracker API to return ResponseBody and explicitly decode with windows-1251 charset for responses
- Update Rutracker CSS selectors and enhance search result parsing with improved logging and robustness
- Update Rutracker title selector for improved parsing accuracy
- Use default view list icon instead of auto-mirrored in library screen

### Security

- Add debug tools section and navigation to settings
- Add optimization section for Rutracker indexing, detailing current issues, necessary data for search, and recommended improvements to reduce database size and indexing time
- Enhance debug log header with detailed device, display, memory, and CPU info, and add a system log section
- FileProvider for secure file sharing, new permissions, and manifest compatibility adjustments
- Improve description cleaning in RutrackerParser to remove metadata fields and prevent duplication; normalize cover image URLs in TopicScreen for better handling of various URL formats; remove
  unnecessary genres section from TopicScreen
- Improve favorite book management by ensuring current book data is retrieved before updates; update ToggleFavoriteUseCase to handle book updates more reliably and synchronize with FavoriteEntity,
  enhancing user experience in managing favorites
- Improve seek delta formatting to display minutes and padded seconds
- Integrate WithAuthorisedCheckUseCase for authentication checks in IndexingViewModel and TopicViewModel before indexing and downloading; replace AsyncImage with RemoteImage component for improved
  image handling in RutrackerSearchScreen and TopicScreen; enhance error handling for unauthorized access during operations
- Introduce VerifyAuthorisedUseCase and WithAuthorisedCheckUseCase for centralized authentication verification; implement logic to check user authentication based on HTML content and provide a
  composable way to enforce authentication before executing operations
- Refactor AuthRepositoryImpl to utilize LoggerFactory for structured logging, replacing direct Android log calls with logger methods. Update error handling in DeleteBookUseCase,
  ToggleFavoriteUseCase, and UpdatePlaybackProgressUseCase to return AppError for improved error management and clarity
- Remove deprecated toTypedResult() method from Result.kt and update error handling in SearchRutrackerUseCase and SearchViewModel to directly access error messages and causes, improving clarity and
  consistency in error logging

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
