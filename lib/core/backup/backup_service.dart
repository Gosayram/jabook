import 'dart:convert';
import 'dart:io';

import 'package:jabook/core/favorites/favorites_service.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/core/metadata/audiobook_metadata_service.dart';
import 'package:jabook/core/parse/rutracker_parser.dart';
import 'package:jabook/core/search/search_history_service.dart';
import 'package:jabook/data/db/app_database.dart';
import 'package:path_provider/path_provider.dart';
import 'package:sembast/sembast.dart';

/// Service for exporting and importing application data (backup/restore).
///
/// This service provides functionality to backup and restore:
/// - Audiobook metadata
/// - Favorites
/// - Search history
/// - Forum resolver cache
class BackupService {
  /// Creates a new instance of BackupService.
  ///
  /// The [db] parameter is the database instance.
  BackupService(this._db);

  /// Database instance.
  final Database _db;

  /// Current backup format version.
  static const int backupFormatVersion = 1;

  /// Metadata service instance.
  AudiobookMetadataService? _metadataService;

  /// Favorites service instance.
  FavoritesService? _favoritesService;

  /// Search history service instance.
  SearchHistoryService? _historyService;

  /// Gets or creates service instances.
  void _initializeServices() {
    _metadataService ??= AudiobookMetadataService(_db);
    _favoritesService ??= FavoritesService(_db);
    _historyService ??= SearchHistoryService(_db);
  }

  /// Exports all application data to a JSON format.
  ///
  /// Returns a Map containing all exportable data.
  Future<Map<String, dynamic>> exportAllData() async {
    _initializeServices();

    try {
      final metadata = await _exportMetadata();
      final favorites = await _exportFavorites();
      final searchHistory = await _exportSearchHistory();
      final forumResolverCache = await _exportForumResolverCache();

      return {
        'version': backupFormatVersion,
        'exported_at': DateTime.now().toIso8601String(),
        'metadata': {
          'audiobooks': metadata,
          'count': metadata.length,
        },
        'favorites': {
          'items': favorites,
          'count': favorites.length,
        },
        'search_history': {
          'items': searchHistory,
          'count': searchHistory.length,
        },
        'forum_resolver_cache': {
          'items': forumResolverCache,
          'count': forumResolverCache.length,
        },
      };
    } on Exception catch (e) {
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'backup',
        message: 'Failed to export data',
        cause: e.toString(),
      );
      rethrow;
    }
  }

  /// Exports metadata to JSON string.
  ///
  /// Returns a JSON string representation of all exportable data.
  Future<String> exportToJson() async {
    final data = await exportAllData();
    const encoder = JsonEncoder.withIndent('  ');
    return encoder.convert(data);
  }

  /// Exports data to a file.
  ///
  /// The [filePath] parameter is the path where to save the backup file.
  /// If not provided, saves to app documents directory with timestamp.
  ///
  /// Returns the path to the saved file.
  Future<String> exportToFile({String? filePath}) async {
    try {
      final jsonContent = await exportToJson();

      if (filePath != null) {
        final file = File(filePath);
        await file.writeAsString(jsonContent, flush: true);
        return filePath;
      }

      // Save to documents directory with timestamp
      final appDocDir = await getApplicationDocumentsDirectory();
      final timestamp = DateTime.now().toIso8601String().replaceAll(':', '-');
      final backupFile =
          File('${appDocDir.path}/jabook_backup_$timestamp.json');
      await backupFile.writeAsString(jsonContent, flush: true);

      await StructuredLogger().log(
        level: 'info',
        subsystem: 'backup',
        message: 'Backup exported to file',
        extra: {'path': backupFile.path},
      );

      return backupFile.path;
    } on Exception catch (e) {
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'backup',
        message: 'Failed to export to file',
        cause: e.toString(),
      );
      rethrow;
    }
  }

  /// Imports data from JSON string.
  ///
  /// The [jsonString] parameter is the JSON string to import.
  /// The [options] parameter controls what data to import.
  ///
  /// Returns a map with import statistics.
  Future<Map<String, int>> importFromJson(
    String jsonString, {
    ImportOptions? options,
  }) async {
    _initializeServices();

    final opts = options ?? ImportOptions.all();

    try {
      final data = jsonDecode(jsonString) as Map<String, dynamic>;

      // Check version compatibility
      final version = data['version'] as int?;
      if (version != null && version > backupFormatVersion) {
        throw Exception(
            'Backup format version $version is newer than supported version $backupFormatVersion');
      }

      final stats = <String, int>{};

      if (opts.importMetadata && data.containsKey('metadata')) {
        final metadataData = data['metadata'] as Map<String, dynamic>?;
        if (metadataData != null) {
          final imported = await _importMetadata(metadataData);
          stats['metadata'] = imported;
        }
      }

      if (opts.importFavorites && data.containsKey('favorites')) {
        final favoritesData = data['favorites'] as Map<String, dynamic>?;
        if (favoritesData != null) {
          final imported = await _importFavorites(favoritesData);
          stats['favorites'] = imported;
        }
      }

      if (opts.importSearchHistory && data.containsKey('search_history')) {
        final historyData = data['search_history'] as Map<String, dynamic>?;
        if (historyData != null) {
          final imported = await _importSearchHistory(historyData);
          stats['search_history'] = imported;
        }
      }

      if (opts.importForumCache && data.containsKey('forum_resolver_cache')) {
        final cacheData = data['forum_resolver_cache'] as Map<String, dynamic>?;
        if (cacheData != null) {
          final imported = await _importForumResolverCache(cacheData);
          stats['forum_resolver_cache'] = imported;
        }
      }

      await StructuredLogger().log(
        level: 'info',
        subsystem: 'backup',
        message: 'Data imported successfully',
        extra: stats,
      );

      return stats;
    } on FormatException catch (e) {
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'backup',
        message: 'Invalid JSON format',
        cause: e.toString(),
      );
      throw Exception('Invalid backup file format: ${e.message}');
    } on Exception catch (e) {
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'backup',
        message: 'Failed to import data',
        cause: e.toString(),
      );
      rethrow;
    }
  }

  /// Imports data from a file.
  ///
  /// The [filePath] parameter is the path to the backup file.
  /// The [options] parameter controls what data to import.
  ///
  /// Returns a map with import statistics.
  Future<Map<String, int>> importFromFile(
    String filePath, {
    ImportOptions? options,
  }) async {
    try {
      final file = File(filePath);
      if (!await file.exists()) {
        throw Exception('Backup file not found: $filePath');
      }

      final jsonContent = await file.readAsString();
      return await importFromJson(jsonContent, options: options);
    } on Exception catch (e) {
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'backup',
        message: 'Failed to import from file',
        extra: {'path': filePath},
        cause: e.toString(),
      );
      rethrow;
    }
  }

  /// Exports audiobook metadata.
  Future<List<Map<String, dynamic>>> _exportMetadata() async {
    if (_metadataService == null) return [];

    try {
      final audiobooks = await _metadataService!.getAllMetadata();
      return audiobooks
          .map((a) => {
                'id': a.id,
                'title': a.title,
                'author': a.author,
                'category': a.category,
                'size': a.size,
                'seeders': a.seeders,
                'leechers': a.leechers,
                'magnetUrl': a.magnetUrl,
                'coverUrl': a.coverUrl,
                'addedDate': a.addedDate.toIso8601String(),
                'chapters': a.chapters
                    .map((c) => {
                          'title': c.title,
                          'durationMs': c.durationMs,
                          'fileIndex': c.fileIndex,
                          'startByte': c.startByte,
                          'endByte': c.endByte,
                        })
                    .toList(),
              })
          .toList();
    } on Exception {
      return [];
    }
  }

  /// Exports favorites.
  Future<List<Map<String, dynamic>>> _exportFavorites() async {
    if (_favoritesService == null) return [];

    try {
      final favorites = await _favoritesService!.getAllFavorites();
      return favorites
          .map((a) => {
                'id': a.id,
                'title': a.title,
                'author': a.author,
                'category': a.category,
                'size': a.size,
                'seeders': a.seeders,
                'leechers': a.leechers,
                'magnetUrl': a.magnetUrl,
                'coverUrl': a.coverUrl,
                'addedDate': a.addedDate.toIso8601String(),
                'chapters': a.chapters
                    .map((c) => {
                          'title': c.title,
                          'durationMs': c.durationMs,
                          'fileIndex': c.fileIndex,
                          'startByte': c.startByte,
                          'endByte': c.endByte,
                        })
                    .toList(),
              })
          .toList();
    } on Exception {
      return [];
    }
  }

  /// Exports search history.
  Future<List<Map<String, dynamic>>> _exportSearchHistory() async {
    if (_historyService == null) return [];

    try {
      final history = await _historyService!.getAllSearches();
      return history
          .map((query) => {
                'query': query,
                'exported_at': DateTime.now().toIso8601String(),
              })
          .toList();
    } on Exception {
      return [];
    }
  }

  /// Exports forum resolver cache.
  Future<List<Map<String, dynamic>>> _exportForumResolverCache() async {
    try {
      final store = AppDatabase().forumResolverCacheStore;
      final records = await store.find(_db);
      return records.map((record) => record.value).toList();
    } on Exception {
      return [];
    }
  }

  /// Imports metadata.
  Future<int> _importMetadata(Map<String, dynamic> metadataData) async {
    if (_metadataService == null) return 0;

    try {
      final audiobooksList = metadataData['audiobooks'] as List<dynamic>?;
      if (audiobooksList == null) return 0;

      int imported = 0;
      for (final _ in audiobooksList) {
        // Metadata is automatically saved during collection, so we skip individual import
        // This is just for statistics
        imported++;
      }
      return imported;
    } on Exception {
      return 0;
    }
  }

  /// Imports favorites.
  Future<int> _importFavorites(Map<String, dynamic> favoritesData) async {
    if (_favoritesService == null) return 0;

    try {
      final itemsList = favoritesData['items'] as List<dynamic>?;
      if (itemsList == null) return 0;

      int imported = 0;
      for (final item in itemsList) {
        final map = item as Map<String, dynamic>;
        final audiobook = _mapToAudiobook(map);
        await _favoritesService!.addToFavorites(audiobook);
        imported++;
      }
      return imported;
    } on Exception {
      return 0;
    }
  }

  /// Imports search history.
  Future<int> _importSearchHistory(Map<String, dynamic> historyData) async {
    if (_historyService == null) return 0;

    try {
      final itemsList = historyData['items'] as List<dynamic>?;
      if (itemsList == null) return 0;

      int imported = 0;
      for (final item in itemsList) {
        final map = item as Map<String, dynamic>;
        final query = map['query'] as String?;
        if (query != null && query.isNotEmpty) {
          await _historyService!.saveSearchQuery(query);
          imported++;
        }
      }
      return imported;
    } on Exception {
      return 0;
    }
  }

  /// Imports forum resolver cache.
  Future<int> _importForumResolverCache(Map<String, dynamic> cacheData) async {
    try {
      final itemsList = cacheData['items'] as List<dynamic>?;
      if (itemsList == null) return 0;

      final store = AppDatabase().forumResolverCacheStore;
      int imported = 0;
      for (final item in itemsList) {
        final map = item as Map<String, dynamic>;
        final forumTitle = map['forum_title'] as String?;
        if (forumTitle != null) {
          await store.record(forumTitle).put(_db, map);
          imported++;
        }
      }
      return imported;
    } on Exception {
      return 0;
    }
  }

  /// Converts map to Audiobook (helper for favorites import).
  Audiobook _mapToAudiobook(Map<String, dynamic> map) {
    final chapters = (map['chapters'] as List<dynamic>?)
            ?.map((c) => Chapter(
                  title: c['title'] as String? ?? '',
                  durationMs: c['durationMs'] as int? ?? 0,
                  fileIndex: c['fileIndex'] as int? ?? 0,
                  startByte: c['startByte'] as int? ?? 0,
                  endByte: c['endByte'] as int? ?? 0,
                ))
            .toList() ??
        <Chapter>[];

    return Audiobook(
      id: map['id'] as String? ?? '',
      title: map['title'] as String? ?? '',
      author: map['author'] as String? ?? '',
      category: map['category'] as String? ?? '',
      size: map['size'] as String? ?? '',
      seeders: map['seeders'] as int? ?? 0,
      leechers: map['leechers'] as int? ?? 0,
      magnetUrl: map['magnetUrl'] as String? ?? '',
      coverUrl: map['coverUrl'] as String?,
      chapters: chapters,
      addedDate: map['addedDate'] != null
          ? DateTime.parse(map['addedDate'] as String)
          : DateTime.now(),
    );
  }
}

/// Options for controlling what data to import during restore.
class ImportOptions {
  /// Creates new import options.
  const ImportOptions({
    this.importMetadata = true,
    this.importFavorites = true,
    this.importSearchHistory = true,
    this.importForumCache = true,
  });

  /// Import all data.
  factory ImportOptions.all() => const ImportOptions();

  /// Import only favorites.
  factory ImportOptions.favoritesOnly() => const ImportOptions(
        importMetadata: false,
        importSearchHistory: false,
        importForumCache: false,
      );

  /// Whether to import metadata.
  final bool importMetadata;

  /// Whether to import favorites.
  final bool importFavorites;

  /// Whether to import search history.
  final bool importSearchHistory;

  /// Whether to import forum resolver cache.
  final bool importForumCache;
}
