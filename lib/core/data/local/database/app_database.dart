// Copyright 2025 Jabook Contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import 'dart:async';

import 'package:jabook/core/infrastructure/config/app_config.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:path_provider/path_provider.dart';
import 'package:sembast/sembast_io.dart';

/// Main database class for the JaBook application.
///
/// This class provides access to all database stores used throughout the app,
/// including user preferences, authentication data, and cached content.
///
/// Note: This class is no longer a singleton. Use [appDatabaseProvider]
/// from `core/di/providers/database_providers.dart` to get an instance.
///
/// For migration purposes, a cached instance is maintained for backward compatibility.
/// New code should use [appDatabaseProvider] instead of calling [getInstance()] directly.
class AppDatabase {
  /// Creates a new AppDatabase instance.
  ///
  /// Use [appDatabaseProvider] to get an instance instead of calling
  /// this constructor directly.
  AppDatabase();

  /// Cached instance for backward compatibility during migration.
  /// This will be removed once all code uses providers.
  static AppDatabase? _cachedInstance;

  /// Gets a cached instance of AppDatabase for backward compatibility.
  ///
  /// **DEPRECATED**: Use [appDatabaseProvider] from `core/di/providers/database_providers.dart` instead.
  ///
  /// This method maintains a cached instance to avoid creating multiple instances
  /// during the migration period. Once all code uses providers, this will be removed.
  // ignore: prefer_constructors_over_static_methods
  static AppDatabase getInstance() => _cachedInstance ??= AppDatabase();

  /// Database instance for all operations.
  Database? _db;
  bool _isInitialized = false;

  /// Flag to track if initialization is in progress.
  /// Used to prevent concurrent initialization attempts.
  bool _isInitializing = false;

  /// Completer for initialization future.
  /// Used to wait for ongoing initialization to complete.
  Completer<void>? _initializationCompleter;

  /// Gets the database instance for direct operations.
  /// Throws StateError if database is not initialized.
  Database get database {
    if (!_isInitialized || _db == null) {
      throw StateError('Database is not initialized. Call initialize() first.');
    }
    return _db!;
  }

  /// Checks if database is initialized.
  bool get isInitialized => _isInitialized && _db != null;

  /// Ensures database is initialized and returns the database instance.
  ///
  /// This method waits for initialization if it's in progress or initializes
  /// the database if it's not initialized yet. This is the safe way to
  /// access the database from code that might run before app initialization.
  ///
  /// Throws StateError if initialization fails after maximum retries.
  Future<Database> ensureInitialized() async {
    if (_isInitialized && _db != null) {
      return _db!;
    }

    // Wait for initialization if in progress
    if (_isInitializing && _initializationCompleter != null) {
      await _initializationCompleter!.future;
      if (_isInitialized && _db != null) {
        return _db!;
      }
    }

    // Initialize if not already initialized
    await initialize();
    if (!_isInitialized || _db == null) {
      throw StateError('Database initialization failed');
    }
    return _db!;
  }

  /// Initializes the database and creates all necessary stores.
  ///
  /// This method should be called once when the app starts.
  /// It's thread-safe and can be called multiple times safely.
  Future<void> initialize() async {
    // Fast path: already initialized
    if (_isInitialized && _db != null) {
      return;
    }

    // If initialization is in progress, wait for it to complete
    if (_isInitializing && _initializationCompleter != null) {
      return _initializationCompleter!.future;
    }

    // Start initialization
    _isInitializing = true;
    _initializationCompleter = Completer<void>();

    try {
      // Double-check after acquiring lock
      if (_isInitialized && _db != null) {
        _isInitializing = false;
        _initializationCompleter!.complete();
        _initializationCompleter = null;
        return;
      }

      final appDocumentDir = await getApplicationDocumentsDirectory();

      // Use different database file names based on flavor
      // For prod: jabook.db
      // For other flavors: jabook-{flavor}.db (e.g., jabook-beta.db, jabook-dev.db)
      final config = AppConfig();
      final dbFileName =
          config.isProd ? 'jabook.db' : 'jabook-${config.flavor}.db';
      final dbPath = '${appDocumentDir.path}/$dbFileName';

      _db = await databaseFactoryIo.openDatabase(dbPath);
      _isInitialized = true;

      await StructuredLogger().log(
        level: 'info',
        subsystem: 'database',
        message: 'Database initialized',
        context: 'app_database_init',
        extra: {'path': dbPath},
      );

      _initializationCompleter!.complete();
    } catch (e) {
      _isInitializing = false;
      _initializationCompleter!.completeError(e);
      _initializationCompleter = null;
      rethrow;
    } finally {
      _isInitializing = false;
      _initializationCompleter = null;
    }
  }

  /// Gets the user preferences store.
  StoreRef<String, Map<String, dynamic>> get userPreferencesStore =>
      StoreRef.main();

  /// Gets the authentication store.
  StoreRef<String, Map<String, dynamic>> get authStore => StoreRef('auth');

  /// Gets the cache store.
  StoreRef<String, Map<String, dynamic>> get cacheStore => StoreRef('cache');

  /// Gets the audiobook metadata store.
  ///
  /// This store contains metadata for all audiobooks collected from RuTracker,
  /// including titles, authors, categories, sizes, and other relevant information.
  /// Primary key is topic_id (String).
  StoreRef<String, Map<String, dynamic>> get audiobookMetadataStore =>
      StoreRef('audiobook_metadata');

  /// Gets the forum resolver cache store.
  ///
  /// This store caches forum resolution results from RuTracker,
  /// mapping forum titles (text labels) to forum IDs and URLs.
  /// Primary key is forum_title (String).
  StoreRef<String, Map<String, dynamic>> get forumResolverCacheStore =>
      StoreRef('forum_resolver_cache');

  /// Gets the search history store.
  ///
  /// This store contains search query history for quick access to recent searches.
  /// Primary key is timestamp (String, ISO 8601 format).
  StoreRef<String, Map<String, dynamic>> get searchHistoryStore =>
      StoreRef('search_history');

  /// Gets the favorites store.
  ///
  /// This store contains user's favorite audiobooks.
  /// Primary key is topic_id (String).
  StoreRef<String, Map<String, dynamic>> get favoritesStore =>
      StoreRef('favorites');

  /// Gets the downloads store.
  ///
  /// This store contains metadata for active torrent downloads,
  /// allowing downloads to be restored after app restart.
  /// Primary key is download_id (String).
  StoreRef<String, Map<String, dynamic>> get downloadsStore =>
      StoreRef('downloads');

  /// Gets the cookies store.
  ///
  /// This store contains authentication cookies for RuTracker.
  /// Primary key is endpoint (String, e.g., "https://rutracker.org").
  /// Each entry contains:
  /// - cookie_header: String - Full cookie header string
  /// - saved_at: String - ISO 8601 timestamp when cookies were saved
  /// - expires_at: String? - Optional ISO 8601 timestamp when cookies expire
  StoreRef<String, Map<String, dynamic>> get cookiesStore =>
      StoreRef('cookies');

  /// Gets the search cache settings store.
  ///
  /// This store contains settings for the smart search cache system.
  /// Primary key is 'settings' (String).
  /// Contains:
  /// - cache_ttl_hours: int - Cache time to live in hours (minimum 12)
  /// - auto_update_enabled: bool - Whether automatic updates are enabled
  /// - auto_update_interval_hours: int - Interval between auto updates in hours
  /// - last_full_sync: String? - ISO 8601 timestamp of last full sync
  /// - next_auto_update: String? - ISO 8601 timestamp of next scheduled update
  /// - sync_in_progress: bool - Whether sync is currently in progress
  /// - last_sync_progress: Map or null - Last sync progress information
  StoreRef<String, Map<String, dynamic>> get searchCacheSettingsStore =>
      StoreRef('search_cache_settings');

  /// Gets the file checksums store.
  ///
  /// This store contains SHA-256 checksums for library files to enable
  /// efficient incremental scanning by detecting file changes.
  /// Primary key is file_path (String).
  /// Each entry contains:
  /// - file_path: String - Full path to the file
  /// - checksum: String - SHA-256 checksum of the file
  /// - file_size: int - Size of the file in bytes
  /// - last_modified: String - ISO 8601 timestamp of last modification
  /// - scanned_at: String - ISO 8601 timestamp when checksum was computed
  StoreRef<String, Map<String, dynamic>> get fileChecksumsStore =>
      StoreRef('file_checksums');

  /// Gets the library groups store.
  ///
  /// This store contains cached library groups for fast restoration
  /// after app restart without full rescan.
  /// Primary key is group_path (String).
  /// Each entry contains:
  /// - group_path: String - Path to the audiobook group
  /// - group_name: String - Display name of the group
  /// - files: List<Map> - List of files in JSON format
  /// - total_size: int - Total size of all files in bytes
  /// - file_count: int - Number of files in the group
  /// - scanned_at: String - ISO 8601 timestamp when group was scanned
  StoreRef<String, Map<String, dynamic>> get libraryGroupsStore =>
      StoreRef('library_groups');

  /// Gets the player state store.
  ///
  /// This store contains player state for reliable restoration
  /// after app restart. More reliable than SharedPreferences.
  /// Primary key is group_path (String).
  /// Each entry contains:
  /// - group_path: String - Path to the audiobook group
  /// - file_paths: List of String - List of file paths in playlist
  /// - current_index: int - Current track index
  /// - current_position: int - Current position in milliseconds
  /// - playback_speed: double - Playback speed
  /// - is_playing: bool - Whether playback is active
  /// - repeat_mode: int - Repeat mode (0 = none, 1 = track, 2 = playlist)
  /// - sleep_timer_remaining_seconds: int? - Remaining sleep timer seconds
  /// - metadata: Map of String to String? - Optional metadata
  /// - saved_at: String - ISO 8601 timestamp when state was saved
  StoreRef<String, Map<String, dynamic>> get playerStateStore =>
      StoreRef('player_state');

  /// Closes the database connection.
  Future<void> close() async {
    if (_isInitialized && _db != null) {
      await _db!.close();
      _db = null;
      _isInitialized = false;
      _isInitializing = false;
      _initializationCompleter = null;
    }
  }
}
