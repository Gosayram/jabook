import 'package:path_provider/path_provider.dart';
import 'package:sembast/sembast.dart';
import 'package:sembast/sembast_io.dart';

/// Main database class for the JaBook application.
///
/// This class provides access to all database stores used throughout the app,
/// including user preferences, authentication data, and cached content.
class AppDatabase {
  /// Factory constructor to create a singleton instance.
  factory AppDatabase() => _instance ??= AppDatabase._();

  /// Private constructor to prevent direct instantiation.
  AppDatabase._();

  /// Singleton instance of the database.
  static AppDatabase? _instance;

  /// Database instance for all operations.
  late Database _db;

  /// Gets the database instance for direct operations.
  Database get database => _db;

  /// Initializes the database and creates all necessary stores.
  ///
  /// This method should be called once when the app starts.
  Future<void> initialize() async {
    final appDocumentDir = await getApplicationDocumentsDirectory();
    final dbPath = '${appDocumentDir.path}/jabook.db';

    _db = await databaseFactoryIo.openDatabase(dbPath);
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

  /// Closes the database connection.
  Future<void> close() async {
    await _db.close();
  }
}
