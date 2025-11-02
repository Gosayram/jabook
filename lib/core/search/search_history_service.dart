import 'package:jabook/core/logging/structured_logger.dart';
import 'package:sembast/sembast.dart';

/// Service for managing search query history.
///
/// This service provides methods to save, retrieve, and manage
/// search query history for quick access to recent searches.
class SearchHistoryService {
  /// Creates a new instance of SearchHistoryService.
  ///
  /// The [db] parameter is the database instance for storing search history.
  SearchHistoryService(this._db);

  /// Database instance for history storage.
  final Database _db;

  /// Store reference for search history.
  final StoreRef<String, Map<String, dynamic>> _store =
      StoreRef('search_history');

  /// Maximum number of search history entries to keep (50).
  static const int maxHistoryEntries = 50;

  /// Saves a search query to history.
  ///
  /// The [query] parameter is the search query to save.
  /// Automatically removes duplicates and old entries if limit is exceeded.
  Future<void> saveSearchQuery(String query) async {
    if (query.trim().isEmpty) return;

    final trimmedQuery = query.trim();
    final timestamp = DateTime.now().toIso8601String();

    try {
      // Check if query already exists
      final finder = Finder(
        filter: Filter.equals('query', trimmedQuery),
      );
      final existing = await _store.findFirst(_db, finder: finder);

      if (existing != null) {
        // Update existing entry timestamp
        await _store.record(existing.key).update(_db, {
          'query': trimmedQuery,
          'timestamp': timestamp,
          'count': (existing.value['count'] as int? ?? 0) + 1,
        });
      } else {
        // Add new entry
        await _store.record(timestamp).add(_db, {
          'query': trimmedQuery,
          'timestamp': timestamp,
          'count': 1,
        });

        // Clean up old entries if limit exceeded
        await _cleanupOldEntries();
      }

      await StructuredLogger().log(
        level: 'debug',
        subsystem: 'search_history',
        message: 'Saved search query to history',
        extra: {'query': trimmedQuery},
      );
    } on Exception catch (e) {
      await StructuredLogger().log(
        level: 'warning',
        subsystem: 'search_history',
        message: 'Failed to save search query',
        extra: {'query': trimmedQuery},
        cause: e.toString(),
      );
    }
  }

  /// Gets recent search queries from history.
  ///
  /// The [limit] parameter limits the number of results (default: 10).
  ///
  /// Returns a list of search queries, most recent first.
  Future<List<String>> getRecentSearches({int limit = 10}) async {
    try {
      final finder = Finder(
        sortOrders: [SortOrder('timestamp', false)], // Descending order
      );
      final records = await _store.find(_db, finder: finder);

      return records
          .take(limit)
          .map((record) => record.value['query'] as String? ?? '')
          .where((query) => query.isNotEmpty)
          .toList();
    } on Exception catch (e) {
      await StructuredLogger().log(
        level: 'warning',
        subsystem: 'search_history',
        message: 'Failed to get recent searches',
        cause: e.toString(),
      );
      return [];
    }
  }

  /// Gets all search queries from history.
  ///
  /// Returns a list of all search queries, most recent first.
  Future<List<String>> getAllSearches() async {
    try {
      final finder = Finder(
        sortOrders: [SortOrder('timestamp', false)], // Descending order
      );
      final records = await _store.find(_db, finder: finder);

      return records
          .map((record) => record.value['query'] as String? ?? '')
          .where((query) => query.isNotEmpty)
          .toList();
    } on Exception catch (e) {
      await StructuredLogger().log(
        level: 'warning',
        subsystem: 'search_history',
        message: 'Failed to get all searches',
        cause: e.toString(),
      );
      return [];
    }
  }

  /// Removes a specific search query from history.
  ///
  /// The [query] parameter is the query to remove.
  Future<void> removeSearchQuery(String query) async {
    try {
      final finder = Finder(
        filter: Filter.equals('query', query.trim()),
      );
      await _store.delete(_db, finder: finder);

      await StructuredLogger().log(
        level: 'debug',
        subsystem: 'search_history',
        message: 'Removed search query from history',
        extra: {'query': query},
      );
    } on Exception catch (e) {
      await StructuredLogger().log(
        level: 'warning',
        subsystem: 'search_history',
        message: 'Failed to remove search query',
        extra: {'query': query},
        cause: e.toString(),
      );
    }
  }

  /// Clears all search history.
  Future<void> clearHistory() async {
    try {
      await _store.delete(_db);

      await StructuredLogger().log(
        level: 'info',
        subsystem: 'search_history',
        message: 'Cleared all search history',
      );
    } on Exception catch (e) {
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'search_history',
        message: 'Failed to clear search history',
        cause: e.toString(),
      );
      rethrow;
    }
  }

  /// Gets search query statistics.
  ///
  /// Returns a map with total count and most searched queries.
  Future<Map<String, dynamic>> getStatistics() async {
    try {
      final records = await _store.find(_db);
      final total = records.length;

      // Get most searched queries (by count)
      final sortedByCount =
          records.map((r) => MapEntry(r.key, r.value)).toList()
            ..sort((a, b) {
              final countA = a.value['count'] as int? ?? 0;
              final countB = b.value['count'] as int? ?? 0;
              return countB.compareTo(countA);
            });

      final topQueries = sortedByCount
          .take(5)
          .map((e) => {
                'query': e.value['query'] as String? ?? '',
                'count': e.value['count'] as int? ?? 0,
              })
          .toList();

      return {
        'total': total,
        'top_queries': topQueries,
      };
    } on Exception {
      return {
        'total': 0,
        'top_queries': <Map<String, dynamic>>[],
      };
    }
  }

  /// Cleans up old entries if history exceeds max limit.
  Future<void> _cleanupOldEntries() async {
    try {
      final finder = Finder(
        sortOrders: [SortOrder('timestamp', false)], // Descending order
      );
      final records = await _store.find(_db, finder: finder);

      if (records.length > maxHistoryEntries) {
        // Remove oldest entries
        final toRemove = records.skip(maxHistoryEntries);
        for (final record in toRemove) {
          await _store.record(record.key).delete(_db);
        }

        await StructuredLogger().log(
          level: 'debug',
          subsystem: 'search_history',
          message: 'Cleaned up old search history entries',
          extra: {'removed': records.length - maxHistoryEntries},
        );
      }
    } on Exception {
      // Ignore cleanup errors
    }
  }
}
