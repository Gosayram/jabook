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

  /// Initializes the database and creates all necessary stores.
  ///
  /// This method should be called once when the app starts.
  Future<void> initialize() async {
    if (_isInitialized && _db != null) {
      return; // Already initialized
    }
    final appDocumentDir = await getApplicationDocumentsDirectory();
    final dbPath = '${appDocumentDir.path}/jabook.db';

    _db = await databaseFactoryIo.openDatabase(dbPath);
    _isInitialized = true;
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

  /// Closes the database connection.
  Future<void> close() async {
    if (_isInitialized && _db != null) {
      await _db!.close();
      _db = null;
      _isInitialized = false;
    }
  }
}
