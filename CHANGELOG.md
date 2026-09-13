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

- Add adaptive DRC threshold calibration based on LUFS
- Add adaptive position save policy and crash-safe writer
- Add adaptive silence threshold and sampling policy for chapter detection
- Add album gain fallback for ReplayGain with improved parsing
- Add ANR watchdog and LeakCanary for debug builds
- Add arch docs sync targets to utils makefile
- Add attention detector and LRU cache for chapter metadata
- Add audio chapter model and accessibility labels
- Add audio equalizer manager with audiobook presets; (#96)
- Add audio quality metadata and hold-to-boost controller
- Add auto-bookmark and clustering functionality
- Add automatic chapter detection via silence analysis
- Add Bluetooth disconnect guard and resume flow
- Add bookmark note sheet visibility and overlay support
- Add bookmark notes with voice recording and equalizer with visual curve display
- Add bookmarks table and DAO for timeline bookmarking
- Add BT disconnect guard, underrun monitoring, and output device tracking (BP-13)
- Add chapter marker, hold-to-boost, and LUFS loudness policies
- Add chapter progress list with parallax cover to book detail
- Add coderabbit recommendations extraction script and makefile targets
- Add contextual resume manager with smart rewind and recap
- Add crash-safe download resumption with persisted resume data
- Add crash-safe position writer and chapter detection
- Add daily goal tracker and listening habit analyzer
- Add debug simulators and network revalidate policies
- Add default speech segment analyzer for smart resume
- Add discovery screen, achievements, listening heatmap and speed chart
- Add dynamic track selection parameters based on audio processing settings
- Add expandable book description and image-based search
- Add FADE skip-silence mode with smooth audio transitions
- Add FTS5 offline search and simplify audio warmup
- Add gapless transition and pitch correction policies, support repeat-all in completion coordinator
- Add hierarchical speed memory, circuit breaker, and stability guards
- Add injectable AppDispatchers with Hilt DI and audit make targets
- Add interactive frequency points to equalizer
- Add keyboard shortcuts, color seeders, show download size
- Add library quick filters, book card redesign, and swipe gestures
- Add loudness compensation for consistent volume across books
- Add LUFS analysis cache with migration 3→4 and DI setup
- Add LUFS loudness analysis worker, speed dial policy, and db migration v19
- Add macrobenchmark module and baseline profile generation
- Add MediaSourceValidator and AudioOffloadCompatibilityPolicy
- Add next track artwork preloading to avoid UI flicker
- Add onboarding spotlight overlay, download badge, and refine nav transitions
- Add Picture-in-Picture support for media player
- Add PiP mode playback controls
- Add playback interruptor and sync queue models for offline operations
- Add playback speed formatting with locale and unit tests
- Add player state snapshot policy and restore flow
- Add PlayerBasedVisualizer for audio visualizer
- Add PlayerStateCache for fast in-memory playback state caching
- Add PlayerStateSnapshot and SyncStatus classes with tests
- Add playlist load progress tracking with StateFlow
- Add PlaylistLoadProgress with phase tracking
- Add policy and storage hardening modules with unit tests
- Add proxy-based audio processor chain for hot-swapping at runtime
- Add rating dialog UI and update tests
- Add reactive cover URL updates via SharedFlow events
- Add rebuffer/stall tracking and persist pitch correction
- Add recent playback speeds selection to player sheet
- Add reduced motion support and player accessibility semantics
- Add screen capture protection and fix disk cache
- Add search bar to library screen for filtering books by title and author
- Add series autoplay countdown when book finishes
- Add shouldTerminate helper, improve loop termination
- Add smart chapter auto-scroll policy with snap and animate support
- Add smart chapter navigation with undo snackbar
- Add smart resume recap suggestion on long pause
- Add smart resume rewind mode with aggressiveness control
- Add source-aware inactivity timer reset policy
- Add speed dial, cover background, chapter sheet, timer progress
- Add steering wheel button policy and per-book speed presets
- Add stop at specific time option to sleep timer
- Add stuck playback detection with coroutine recovery
- Add TorrentDownloadItemFormattingTest.kt
- Add waveform preview to seekbar
- Add WCAG contrast-aware text colors on player screen
- Add weekly listening recap card and book actions sheet
- Add year recap sharing, book rating dialog, EQ presets, and UI components
- Enable audio offload mode and suspend visualizer during offload playback
- Implement bookmark functionality in audio player
- Implement context-aware resume with smart rewind and recap
- Implement hold-to-boost speed in audio player
- Implement player facade and equalizer enhancements
- Improve buffer monitoring and crossfade handling
- Improve sleep timer UX with last duration memory, resume hint and stop-at display
- Integrate AudioFader for smooth fade-out before sleep timer expiry
- Library source sync script for project dependencies
- New audio processing classes
- Player key and autoplay tests; update Gradle verification keyring
- Script to sync open tasks between arch docs
- Skip resume rewind after sleep timer stop
- Store magnetUrl and improve diacritic normalization

### Changed

- Add AnimatedContent transitions between player screen states
- Add callback for WorkManager enqueue and refactor tests
- Add connectivity-aware scheduler and dns prefetch guards
- Add dependency verification to fmt-kotlin and lint-kotlin make targets
- Add font scale previews, reduce motion support, and toggle icon states in player
- Add glassmorphism effect and accessibility to player screen
- Add HapticManager, library skeleton and improve UI components
- Add mirror domain validation policy with sanitization and tests; (#92)
- Add player intent guard policy for seek and sleep timer
- Add player reducer predispatch and matrix tests
- Add reducer no-op guard for seek settings update
- Add skeleton loading and play/pause button animations
- Add sleep timer reducer state and idempotent tests
- Add sleep timer state schema to user preferences proto
- Add trust rules for JetBrains IntelliJ coroutines in Gradle verification metadata
- Align copyright headers with project license template
- Arch changement and test management; (#64)
- Bump actions/upload-artifact from 4.6.2 to 7.0.0 (#93)
- Bump actions/upload-artifact from 7.0.0 to 7.0.1 (#99)
- Bump android-actions/setup-android from 3.2.2 to 4.0.0 (#53)
- Bump android-actions/setup-android from 4.0.0 to 4.0.1 (#91)
- Bump androidx.activity:activity-compose in /android (#65)
- Bump androidx.compose:compose-bom in /android
- Bump androidx.compose:compose-bom in /android (#85)
- Bump androidx.compose.material3:material3-adaptive-navigation-suite
- Bump androidx.compose.material3:material3-adaptive-navigation-suite (#77)
- Bump androidx.compose.material3:material3-adaptive-navigation-suite (#95)
- Bump androidx.compose.ui:ui-text-google-fonts in /android
- Bump androidx.compose.ui:ui-text-google-fonts in /android (#58)
- Bump androidx.compose.ui:ui-text-google-fonts in /android (#73)
- Bump androidx.core:core-ktx from 1.13.1 to 1.18.0 in /android (#70)
- Bump androidx.datastore:datastore from 1.2.0 to 1.2.1 in /android (#87)
- Bump androidx.datastore:datastore-preferences in /android (#66)
- Bump androidx.media:media from 1.7.1 to 1.8.0 in /android
- Bump androidx.navigation:navigation-compose in /android
- Bump androidx.navigation:navigation-compose in /android (#68)
- Bump androidx.work:work-runtime from 2.11.0 to 2.11.2 in /android (#83)
- Bump androidxComposeMaterial3Adaptive in /android (#71)
- Bump androidxComposeMaterial3Adaptive in /android (#94)
- Bump androidxComposeMaterial3Adaptive package version
- Bump app.cash.turbine:turbine from 1.2.0 to 1.2.1 in /android (#81)
- Bump audio database version to 4 and add LufsCache migration
- Bump coil3 from 3.3.0 to 3.4.0 in /android (#78)
- Bump com.android.application from 8.13.1 to 9.1.0 in /android (#69)
- Bump com.android.application from 9.1.0 to 9.1.1 in /android (#105)
- Bump com.android.application from 9.1.1 to 9.2.0 in /android
- Bump com.android.application from 9.2.0 to 9.2.1 in /android
- Bump com.google.crypto.tink:tink-android in /android (#79)
- Bump com.google.devtools.ksp from 2.3.3 to 2.3.6 in /android (#62)
- Bump com.google.firebase:firebase-bom from 34.7.0 to 34.11.0 in /android (#63)
- Bump com.google.firebase:firebase-bom in /android
- Bump com.google.firebase:firebase-bom in /android (#97)
- Bump com.google.firebase.crashlytics from 3.0.6 to 3.0.7 in /android (#100)
- Bump com.google.jimfs:jimfs from 1.3.0 to 1.3.1 in /android (#90)
- Bump com.google.protobuf from 0.9.5 to 0.9.6 in /android (#56)
- Bump com.google.protobuf from 0.9.6 to 0.10.0 in /android
- Bump com.google.protobuf:protobuf-javalite in /android (#72)
- Bump deps and enable JaCoCo coverage for debug build
- Bump dev.chrisbanes.haze:haze from 1.7.1 to 1.7.2 in /android (#74)
- Bump dev.detekt from 2.0.0-alpha.2 to 2.0.0-alpha.3 in /android
- Bump dev.detekt:detekt-api in /android
- Bump github/codeql-action from 4.35.1 to 4.35.2
- Bump github/codeql-action from 4.35.2 to 4.35.3
- Bump github/codeql-action from 4.35.3 to 4.35.4
- Bump github/codeql-action from 4.35.4 to 4.36.0
- Bump gradle-wrapper from 9.4.1 to 9.5.0 in /android
- Bump hilt from 2.57.2 to 2.59.2 in /android (#60)
- Bump hiltExt from 1.2.0 to 1.3.0 in /android (#76)
- Bump io.kotest:kotest-property-jvm from 5.9.1 to 6.1.11 in /android (#104)
- Bump kotlin from 2.2.21 to 2.3.20 in /android (#54)
- Bump kotlin from 2.3.20 to 2.3.21 in /android
- Bump kotlinTest from 2.2.21 to 2.3.20 in /android (#57)
- Bump kotlinTest from 2.3.20 to 2.3.21 in /android
- Bump kotlinxCoroutines from 1.10.2 to 1.11.0 in /android
- Bump kotlinxSerialization from 1.10.0 to 1.11.0 in /android (#98)
- Bump kotlinxSerialization from 1.9.0 to 1.10.0 in /android (#59)
- Bump libtorrent4j from 2.1.0-38 to 2.1.0-39 in /android (#61)
- Bump media3 from 1.10.0 to 1.10.1 in /android
- Bump media3 from 1.10.0-rc02 to 1.10.0 in /android (#88)
- Bump media3 from 1.8.0 to 1.9.3 in /android (#55)
- Bump org.jetbrains.kotlinx:kotlinx-coroutines-test in /android
- Bump org.jlleitschuh.gradle.ktlint from 14.0.1 to 14.2.0 in /android (#67)
- Bump org.jsoup:jsoup from 1.21.2 to 1.22.1 in /android (#82)
- Bump org.jsoup:jsoup from 1.22.1 to 1.22.2 in /android
- Bump org.mockito:mockito-core from 5.21.0 to 5.23.0 in /android (#80)
- Bump org.mockito.kotlin:mockito-kotlin from 6.1.0 to 6.3.0 in /android (#84)
- Bump org.robolectric:robolectric from 4.16 to 4.16.1 in /android (#89)
- Bump patch version to 1.2.7+102
- Bump patch version to 1.2.7+103
- Bump patch version to 1.2.7+104
- Bump patch version to 1.2.7+105
- Bump patch version to 1.2.7+106
- Bump patch version to 1.2.7+107
- Bump patch version to 1.2.7+108
- Bump patch version to 1.2.7+109
- Bump patch version to 1.2.7+97; (#75)
- Bump softprops/action-gh-release from 2.5.0 to 2.6.1 (#52)
- Bump softprops/action-gh-release from 2.6.1 to 3.0.0 (#103)
- Clean up AudioPlayerService and related components
- Configure test task timeout, parallelism, and logging
- Consolidate intent routing and optimize recompositions
- Consolidate saved position state into RestoredBootstrapSnapshot
- Extract audio player components into dedicated classes
- Extract AudioPlayerService initialization into focused coordinator classes
- Extract AudioServiceComponentBinder and InactivityPlaybackEventObserver
- Extract book completion and error handling into separate classes
- Extract bookmark handling and time formatting
- Extract chapter repeat path to reducer and add tests
- Extract command pattern for player intent dispatch and add position deduplication
- Extract command router and release handler from AudioPlayerService
- Extract command routing and executor into dedicated file
- Extract dial constants, fix pointerInput, add metadata tests
- Extract facades and handlers to reduce PlayerListener size
- Extract inactivity listener binding and unload orchestration
- Extract inline lambdas into testable functions and replace UI tests with policy tests
- Extract MediaSessionLayoutHelper and PlayerNotificationSetup from AudioPlayerService
- Extract MotionTokens and replace hardcoded animation values
- Extract notification intent factory and delegate service methods
- Extract playback error resolution into PlaybackErrorPolicy
- Extract PlaybackContextHelper from AudioPlayerService
- Extract player state types and add channel command pipeline
- Extract playlist loading logic into dedicated policy classes
- Extract playlist loading logic into policy classes
- Extract playlist management policies to separate files
- Extract playlist policies and add baseline profiles
- Extract playlist policies and add metadata normalization
- Extract playlist seek and metadata policies
- Extract sleep timer and settings handlers
- Extract sleep timer, settings routing, and position publish policy from ViewModel
- Extract speed label formatting and wrap derived state in remember
- Extract SurfaceElevationTokens for consistent Material 3 elevation
- Extract torrent download network policy into dedicated class
- Extract URI resolution into PlaylistUriResolutionPolicy
- Fix hiltViewModel imports and improve indexer robustness
- Fix player speed dial, key events, EQ display, and voice-note error handling
- Fix streak logic and add circular hour distance; update keys
- Format code for consistency
- Improve AMOLED theme with surface containers and fix color priority
- Improve bookmark UX with haptic feedback, undo snackbar, and refactor settings UI to Material3 ListItem
- Improve player animations, haptics, error handling, and add tests
- Inject AppDispatchers into PlaylistManager for testable coroutine dispatchers
- Merge pull request #102 from Gosayram/release/beta_1_2_7_upd_2
- Merge pull request #106 from Gosayram/release/beta_1_2_7_upd_3
- Merge pull request #107 from Gosayram/dependabot/github_actions/github/codeql-action-4.35.2
- Merge pull request #108 from Gosayram/dependabot/github_actions/github/codeql-action-4.35.2
- Merge pull request #109 from Gosayram/dependabot/gradle/android/com.google.protobuf-0.10.0
- Merge pull request #110 from Gosayram/dependabot/gradle/android/org.jsoup-jsoup-1.22.2
- Merge pull request #111 from Gosayram/dependabot/gradle/android/com.android.application-9.2.0
- Merge pull request #114 from Gosayram/dependabot/gradle/android/androidx.compose.material3-material3-adaptive-navigation-suite-1.5.0-alpha18
- Merge pull request #115 from Gosayram/dependabot/gradle/android/org.jsoup-jsoup-1.22.2
- Merge pull request #116 from Gosayram/dependabot/gradle/android/com.android.application-9.2.0
- Merge pull request #117 from Gosayram/dependabot/gradle/android/androidx.compose-compose-bom-2026.04.01
- Merge pull request #118 from Gosayram/dependabot/gradle/android/androidx.navigation-navigation-compose-2.9.8
- Merge pull request #119 from Gosayram/dependabot/gradle/android/androidx.compose.ui-ui-text-google-fonts-1.11.0
- Merge pull request #120 from Gosayram/dependabot/gradle/android/com.google.protobuf-0.10.0
- Merge pull request #122 from Gosayram/dependabot/gradle/android/kotlin-2.3.21
- Merge pull request #123 from Gosayram/dependabot/gradle/android/kotlinTest-2.3.21
- Merge pull request #125 from Gosayram/dependabot/gradle/android/kotlinTest-2.3.21
- Merge pull request #126 from Gosayram/dependabot/github_actions/github/codeql-action-4.35.3
- Merge pull request #127 from Gosayram/feature/redesign
- Merge pull request #128 from Gosayram/dependabot/github_actions/github/codeql-action-4.35.4
- Merge pull request #130 from Gosayram/dependabot/gradle/android/androidx.media-media-1.8.0
- Merge pull request #131 from Gosayram/dependabot/gradle/android/dev.detekt-2.0.0-alpha.3
- Merge pull request #132 from Gosayram/dependabot/gradle/android/androidx.compose-compose-bom-2026.05.00
- Merge pull request #133 from Gosayram/dependabot/gradle/android/gradle-wrapper-9.5.0
- Merge pull request #134 from Gosayram/dependabot/gradle/android/com.google.firebase-firebase-bom-34.13.0
- Merge pull request #135 from Gosayram/dependabot/gradle/android/androidx.compose.ui-ui-text-google-fonts-1.11.1
- Merge pull request #136 from Gosayram/dependabot/gradle/android/dev.detekt-detekt-api-2.0.0-alpha.3
- Merge pull request #137 from Gosayram/dependabot/gradle/android/kotlinxCoroutines-1.11.0
- Merge pull request #138 from Gosayram/dependabot/gradle/android/org.jetbrains.kotlinx-kotlinx-coroutines-test-1.11.0
- Merge pull request #142 from Gosayram/dependabot/gradle/android/kotlinxCoroutines-1.11.0
- Merge pull request #145 from Gosayram/dependabot/gradle/android/dev.detekt-detekt-api-2.0.0-alpha.3
- Merge pull request #146 from Gosayram/dependabot/gradle/android/org.jetbrains.kotlinx-kotlinx-coroutines-test-1.11.0
- Merge pull request #147 from Gosayram/dependabot/gradle/android/dev.detekt-2.0.0-alpha.3
- Merge pull request #148 from Gosayram/dependabot/gradle/android/androidx.media-media-1.8.0
- Merge pull request #149 from Gosayram/dependabot/gradle/android/media3-1.10.1
- Merge pull request #152 from Gosayram/dependabot/github_actions/github/codeql-action-4.36.0
- Merge pull request #153 from Gosayram/feature/audio-test-utils
- Merge pull request #154 from Gosayram/dependabot/gradle/android/androidx.compose-compose-bom-2026.05.01
- Merge pull request #156 from Gosayram/dependabot/gradle/android/androidx.compose.ui-ui-text-google-fonts-1.11.2
- Migrate hiltViewModel import to androidx.hilt.navigation.compose
- Migrate sleep timer state from SharedPreferences to DataStore with fallback
- Move play and seek decisions into player reducer
- Move repeat mode decisions into player reducer
- Move seek reset and defaults to reducer flow
- Optimize dependency verification metadata regeneration
- Optimize gradle config for CI with parallel builds and stricter verification
- Persist player snapshot and reducer audio rules
- Propagate InactivityCommandSource through playback command chain
- Read chapter repeat mode from unified player state
- Refactor audio buffer manager and add tests
- Refactor audio extractor and bump Bluetooth SDK threshold
- Refactor audio player service architecture (#47)
- Refactor EQ preamp to nullable type and fix player UI bugs
- Refactor FTS5 to manual index and add trusted speed memory
- Refactor haptic feedback to HapticManager and add slider value tooltip
- Refactor playlist manager logic to dedicated classes
- Reformat audio test files and remove duplicate code
- Reformat code for better readability and consistency
- Remove InactivityCommandSource coupling and inline service initialization
- Remove indirection in audio player service and simplify notification handling
- Remove legacy chapter repeat toggle path in vm
- Remove test task for check task
- Remove unnecessary @Suppress("DEPRECATION") annotations from screens
- Remove unused import and add missing EOF newlines
- Rename PlayerUiState to sealed PlayerState contract
- Reorder imports in PlayerViewModel
- Replace android.util.Log with centralized LogUtils across codebase
- Replace chapter reorder buttons with drag handle gesture
- Replace circular download progress with badge on book cards
- Replace ValueAnimator with coroutines for crossfade logic
- Restore check-all workflow and clean service delegates
- Restructure test targets to separate dev and CI profiles
- Rewrite service monitor to reactive loop, optimize cover loading
- Route chapter selection through player reducer
- Route player errors through intent dispatcher path
- Run fmt
- Sanitize comment body for coderabbit reports
- Simplify audio APIs, add EQ presets and speed dial management, remove dead tests
- Simplify audio service and update processors to Media3 API
- Simplify FFT window calc and chapter repo retrieval
- Skip no-op audio settings intent in player dispatch
- Update android instrumentation test deps and versions
- Update backpressure handling and reformat license headers
- Update baseline-prof mapping for Compose activities
- Update CI scripts to fallback from rg to grep for portability
- Update compose test imports to v2
- Update Gradle to 9.5.1 and fix track transition test
- Update Java/Kotlin toolchain from 21 to 25 and broaden gradle verification metadata
- Update torrent fgs types and add player one-shot effects
- Upgrade FTS4 to FTS5 for search ranking, add dynamic request timeouts, and improve player lifecycle
- Use dedicated CancellationException catch block

### Fixed

- Add initialization check before resetting visualizer state bridge
- Align wifi-only warning flow with torrent network policy
- Apply loudness gain multiplicatively and extend tests
- Audio listener leaks, CancellationException handling, chapter detection and reorder boundary checks
- Bookmark audio playback race condition and recap hours display
- Checksum for protoc-4.34.1-linux-x86_64.exe
- Correct indexing progress calc, add post-completion index verify, prioritize cover loads
- Correct pitch correction inversion and remove runBlocking calls
- Detect cover url and seeder changes in stale-while-revalidate policy
- Error handling in FTS search flow using coroutine catch
- Fix async timing in PlaylistManager tests with advanceUntilIdle
- Fix audio logging, validation, and caching issues
- Fix buffer coercion, network leak, and echo processor
- Fix CancellationException swallowing and optimize cover loading
- Fix coroutine CancellationException handling in PlaylistManager
- Fix: apply CodeRabbit auto-fixes
- Harden storage writes with atomic lock and path validation
- Import ordering and add opt-in annotation
- Improve accessibility, keyboard controls, and fix speed dial
- Improve error handling and isEnded logic
- Launch CrashActivity on next app start if crash report exists
- Module graph drifts
- Pass handleAudioFocus param to crossfade player factory
- Pitch correction formula, torrent shutdown race, and timeout overflow
- Preserve player transitions and non-dropping event flow
- Prevent duplicate pull-to-refresh triggers while scanning
- Properly rethrow CancellationException and add atomic cover update
- Race condition, edge cases, and privacy in audio service
- Remove signingConfig assignment in debug build
- Replace generic exception catches with specific types in SleepTimerRepository and add tests
- Rethrow CancellationException and add index size retry
- Rethrow CancellationException in all catch blocks to preserve coroutine cancellation semantics
- Rethrow CancellationException in repository and use case catch blocks
- Series autoplay dismiss persistence and code improvements
- Sleep timer progress, autoplay reset, drag state, and background layering
- Stale callbacks in chapter drag and sleep timer progress fraction
- Unused params
- Use Popup for slider tooltip to prevent clipping
- Verification metadata file with unexpected comments

### Security

- Bump aquasecurity/trivy-action from 0.35.0 to 0.36.0
- Merge pull request #112 from Gosayram/dependabot/github_actions/aquasecurity/trivy-action-0.36.0

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
