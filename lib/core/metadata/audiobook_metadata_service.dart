import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/parse/rutracker_parser.dart';
import 'package:jabook/core/rutracker/forum_resolver.dart';
import 'package:sembast/sembast.dart';

/// Service for managing audiobook metadata collection and storage.
///
/// This service provides methods to collect metadata from RuTracker forums,
/// store it locally in the database, and search through cached metadata.
class AudiobookMetadataService {
  /// Creates a new instance of AudiobookMetadataService.
  ///
  /// The [db] parameter is the database instance for storing metadata.
  AudiobookMetadataService(this._db);

  /// Database instance for metadata storage.
  final Database _db;

  /// Store reference for audiobook metadata.
  final StoreRef<String, Map<String, dynamic>> _store =
      StoreRef('audiobook_metadata');

  /// Parser for extracting metadata from RuTracker HTML.
  final RuTrackerParser _parser = RuTrackerParser();

  /// Forum resolver for dynamic category resolution.
  ForumResolver? _forumResolver;

  /// Gets or creates ForumResolver instance.
  ForumResolver get _resolver {
    _forumResolver ??= ForumResolver(_db);
    return _forumResolver!;
  }

  /// Text labels for forums to collect metadata from.
  /// These are stable textual names that will be resolved dynamically.
  static const List<String> forumLabels = [
    'Радиоспектакли, история, мемуары',
    'Фантастика, фэнтези, мистика, ужасы, фанфики',
    'Художественная литература',
    'Религии',
    'Прочая литература',
  ];

  /// Batch size for processing metadata (50-100 records).
  static const int batchSize = 50;

  /// Delay between requests to avoid rate limiting (milliseconds).
  static const int requestDelayMs = 2000;

  /// Converts an Audiobook entity to a Map for storage.
  Map<String, dynamic> _audiobookToMap(Audiobook audiobook, String forumId,
      String forumName, DateTime lastSynced) {
    // Pre-compute lowercase versions for faster searching
    final titleLower = audiobook.title.toLowerCase();
    final authorLower = audiobook.author.toLowerCase();

    return {
      'topic_id': audiobook.id,
      'title': audiobook.title,
      'title_lower': titleLower, // Indexed field for search
      'author': audiobook.author,
      'author_lower': authorLower, // Indexed field for search
      'category': audiobook.category,
      'forum_id': forumId,
      'forum_name': forumName,
      'size': audiobook.size,
      'seeders': audiobook.seeders,
      'leechers': audiobook.leechers,
      'magnet_url': audiobook.magnetUrl,
      'cover_url': audiobook.coverUrl,
      'added_date': audiobook.addedDate.toIso8601String(),
      'last_updated': DateTime.now().toIso8601String(),
      'last_synced': lastSynced.toIso8601String(),
      'chapters': audiobook.chapters
          .map((c) => {
                'title': c.title,
                'duration_ms': c.durationMs,
                'file_index': c.fileIndex,
                'start_byte': c.startByte,
                'end_byte': c.endByte,
              })
          .toList(),
    };
  }

  /// Converts a stored Map back to an Audiobook entity.
  Audiobook _mapToAudiobook(Map<String, dynamic> map) {
    final chapters = (map['chapters'] as List<dynamic>?)
            ?.map((c) => Chapter(
                  title: c['title'] as String? ?? '',
                  durationMs: c['duration_ms'] as int? ?? 0,
                  fileIndex: c['file_index'] as int? ?? 0,
                  startByte: c['start_byte'] as int? ?? 0,
                  endByte: c['end_byte'] as int? ?? 0,
                ))
            .toList() ??
        <Chapter>[];

    return Audiobook(
      id: map['topic_id'] as String? ?? '',
      title: map['title'] as String? ?? '',
      author: map['author'] as String? ?? '',
      category: map['category'] as String? ?? '',
      size: map['size'] as String? ?? '',
      seeders: map['seeders'] as int? ?? 0,
      leechers: map['leechers'] as int? ?? 0,
      magnetUrl: map['magnet_url'] as String? ?? '',
      coverUrl: map['cover_url'] as String?,
      chapters: chapters,
      addedDate: map['added_date'] != null
          ? DateTime.parse(map['added_date'] as String)
          : DateTime.now(),
    );
  }

  /// Saves or updates metadata for a list of audiobooks.
  ///
  /// The [audiobooks] parameter is the list of audiobooks to save.
  /// The [forumId] parameter is the forum ID where these audiobooks are from.
  /// The [forumName] parameter is the name of the forum category.
  /// The [lastSynced] parameter is the timestamp when metadata was collected.
  Future<void> _saveBatch(List<Audiobook> audiobooks, String forumId,
      String forumName, DateTime lastSynced) async {
    for (final audiobook in audiobooks) {
      final map = _audiobookToMap(audiobook, forumId, forumName, lastSynced);
      await _store.record(audiobook.id).put(_db, map);
    }
  }

  /// Collects metadata for a specific forum category.
  ///
  /// The [forumTitle] parameter is the forum title (textual label) to collect metadata from.
  /// The [force] parameter, if true, forces a full update even if recent data exists.
  ///
  /// Returns the number of audiobooks collected.
  Future<int> collectMetadataForCategory(String forumTitle,
      {bool force = false}) async {
    // Resolve forum URL dynamically
    final forumUrl = await _resolver.resolveForumUrl(
      categoryTitle: 'Аудиокниги',
      forumTitle: forumTitle,
    );

    if (forumUrl == null) {
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'metadata',
        message: 'Failed to resolve forum URL',
        extra: {'forum_title': forumTitle},
      );
      return 0;
    }

    // Extract forum ID from URL
    final forumIdMatch = RegExp(r'f=(\d+)').firstMatch(forumUrl.toString());
    final forumId = forumIdMatch?.group(1) ?? '';

    if (forumId.isEmpty) {
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'metadata',
        message: 'Failed to extract forum ID from URL',
        extra: {'forum_url': forumUrl.toString()},
      );
      return 0;
    }

    // Check if update is needed
    if (!force && await needsUpdate(forumId)) {
      await StructuredLogger().log(
        level: 'info',
        subsystem: 'metadata',
        message: 'Skipping update for forum $forumTitle (recently updated)',
      );
      return 0;
    }

    await StructuredLogger().log(
      level: 'info',
      subsystem: 'metadata',
      message:
          'Starting metadata collection for forum: $forumTitle (ID: $forumId)',
    );

    final dio = await DioClient.instance;
    final lastSynced = DateTime.now();

    int totalCollected = 0;
    int start = 0;
    bool hasMore = true;
    Uri currentForumUrl = forumUrl;
    String currentForumId = forumId;

    try {
      while (hasMore) {
        // Add delay to avoid rate limiting
        if (start > 0) {
          await Future.delayed(Duration(milliseconds: requestDelayMs));
        }

        final response = await dio.get(
          currentForumUrl.toString(),
          queryParameters: {
            'start': start,
          },
        );

        if (response.statusCode == 404) {
          // Forum might have moved, invalidate cache and re-resolve
          await StructuredLogger().log(
            level: 'warning',
            subsystem: 'metadata',
            message: 'Forum returned 404, invalidating cache and re-resolving',
            extra: {'forum_title': forumTitle},
          );
          await _resolver.invalidateCache(forumTitle);
          // Try to re-resolve
          final newForumUrl = await _resolver.resolveForumUrl(
            categoryTitle: 'Аудиокниги',
            forumTitle: forumTitle,
          );
          if (newForumUrl == null) {
            await StructuredLogger().log(
              level: 'error',
              subsystem: 'metadata',
              message: 'Failed to re-resolve forum after 404',
              extra: {'forum_title': forumTitle},
            );
            break;
          }
          // Update forum URL and extract new forum ID
          currentForumUrl = newForumUrl;
          final newForumIdMatch =
              RegExp(r'f=(\d+)').firstMatch(newForumUrl.toString());
          final newForumId = newForumIdMatch?.group(1) ?? '';
          if (newForumId.isNotEmpty) {
            // Update forumId for saving
            final oldForumId = currentForumId;
            currentForumId = newForumId;
            // Continue with retry of the same start position
            await StructuredLogger().log(
              level: 'info',
              subsystem: 'metadata',
              message: 'Re-resolved forum after 404',
              extra: {
                'forum_title': forumTitle,
                'old_forum_id': oldForumId,
                'new_forum_id': newForumId,
              },
            );
            // Retry the same request with new URL
            continue;
          } else {
            break;
          }
        }

        if (response.statusCode != 200) {
          await StructuredLogger().log(
            level: 'warning',
            subsystem: 'metadata',
            message: 'Failed to fetch forum page',
            extra: {'status': response.statusCode, 'forum': forumTitle},
          );
          break;
        }

        final audiobooks = await _parser.parseSearchResults(response.data);

        if (audiobooks.isEmpty) {
          hasMore = false;
          break;
        }

        // Save batch
        await _saveBatch(audiobooks, currentForumId, forumTitle, lastSynced);
        totalCollected += audiobooks.length;

        await StructuredLogger().log(
          level: 'debug',
          subsystem: 'metadata',
          message: 'Collected batch for forum $forumTitle',
          extra: {
            'count': audiobooks.length,
            'total': totalCollected,
            'start': start,
          },
        );

        // Check if we should continue (if we got less than expected, likely no more)
        if (audiobooks.length < batchSize) {
          hasMore = false;
        } else {
          start += audiobooks.length;
        }
      }

      await StructuredLogger().log(
        level: 'info',
        subsystem: 'metadata',
        message: 'Completed metadata collection for forum: $forumTitle',
        extra: {'total': totalCollected},
      );

      return totalCollected;
    } on Exception catch (e) {
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'metadata',
        message: 'Failed to collect metadata for forum $forumTitle',
        cause: e.toString(),
      );
      rethrow;
    }
  }

  /// Collects metadata for all configured forum categories.
  ///
  /// The [force] parameter, if true, forces a full update for all categories.
  ///
  /// Returns a map of forum titles to the number of audiobooks collected.
  Future<Map<String, int>> collectAllMetadata({bool force = false}) async {
    final results = <String, int>{};

    await StructuredLogger().log(
      level: 'info',
      subsystem: 'metadata',
      message: 'Starting full metadata collection',
      extra: {'force': force, 'categories': forumLabels.length},
    );

    // Ensure all forums are resolved first
    await ensureForumsResolved();

    for (final forumTitle in forumLabels) {
      try {
        final count =
            await collectMetadataForCategory(forumTitle, force: force);
        results[forumTitle] = count;

        // Add delay between categories
        await Future.delayed(Duration(milliseconds: requestDelayMs));
      } on Exception catch (e) {
        await StructuredLogger().log(
          level: 'error',
          subsystem: 'metadata',
          message: 'Failed to collect metadata for category $forumTitle',
          cause: e.toString(),
        );
        results[forumTitle] = 0;
      }
    }

    await StructuredLogger().log(
      level: 'info',
      subsystem: 'metadata',
      message: 'Completed full metadata collection',
      extra: {'results': results},
    );

    return results;
  }

  /// Ensures all forums are resolved and cached.
  ///
  /// This method pre-resolves all forum URLs to speed up subsequent operations.
  Future<void> ensureForumsResolved() async {
    await StructuredLogger().log(
      level: 'info',
      subsystem: 'metadata',
      message: 'Ensuring all forums are resolved',
      extra: {'forums_count': forumLabels.length},
    );

    for (final forumTitle in forumLabels) {
      try {
        // Check cache first
        final cached = await _resolver.getCachedForumInfo(forumTitle);
        if (cached != null) {
          continue; // Already cached
        }

        // Resolve forum
        final url = await _resolver.resolveForumUrl(
          categoryTitle: 'Аудиокниги',
          forumTitle: forumTitle,
        );

        if (url != null) {
          await StructuredLogger().log(
            level: 'debug',
            subsystem: 'metadata',
            message: 'Resolved forum',
            extra: {'forum_title': forumTitle, 'url': url.toString()},
          );
        } else {
          await StructuredLogger().log(
            level: 'warning',
            subsystem: 'metadata',
            message: 'Failed to resolve forum',
            extra: {'forum_title': forumTitle},
          );
        }

        // Add small delay between resolutions
        await Future.delayed(const Duration(milliseconds: 500));
      } on Exception catch (e) {
        await StructuredLogger().log(
          level: 'error',
          subsystem: 'metadata',
          message: 'Error resolving forum',
          extra: {'forum_title': forumTitle},
          cause: e.toString(),
        );
      }
    }

    await StructuredLogger().log(
      level: 'info',
      subsystem: 'metadata',
      message: 'Completed forum resolution',
    );
  }

  /// Checks if metadata for a forum needs updating.
  ///
  /// The [forumId] parameter is the forum ID to check.
  ///
  /// Returns true if metadata needs updating (no data or last update > 24 hours ago).
  Future<bool> needsUpdate(String forumId) async {
    // Check last sync time for any record from this forum
    final finder = Finder(
      filter: Filter.equals('forum_id', forumId),
    );
    final records = await _store.find(_db, finder: finder);

    if (records.isEmpty) {
      return true; // No data, needs update
    }

    // Get the first record to check last sync time
    final firstRecord = records.first;
    final lastSyncedStr = firstRecord.value['last_synced'] as String?;
    if (lastSyncedStr == null) {
      return true; // No sync time, needs update
    }

    final lastSynced = DateTime.parse(lastSyncedStr);
    final now = DateTime.now();
    final hoursSinceUpdate = now.difference(lastSynced).inHours;

    return hoursSinceUpdate >= 24;
  }

  /// Checks if metadata for a forum (by title) needs updating.
  ///
  /// The [forumTitle] parameter is the forum title (textual label) to check.
  ///
  /// Returns true if metadata needs updating (no data or last update > 24 hours ago).
  Future<bool> needsUpdateByTitle(String forumTitle) async {
    // Get cached forum ID
    final cached = await _resolver.getCachedForumInfo(forumTitle);
    final forumId = cached?['forum_id'] as String?;

    if (forumId == null) {
      return true; // Not resolved yet, needs update
    }

    return needsUpdate(forumId);
  }

  /// Gets all metadata from local database.
  ///
  /// Returns a list of all stored audiobooks.
  Future<List<Audiobook>> getAllMetadata() async {
    final records = await _store.find(_db);
    return records.map((record) => _mapToAudiobook(record.value)).toList();
  }

  /// Searches metadata in local database.
  ///
  /// The [query] parameter is the search query (searches in title and author).
  /// The [limit] parameter limits the number of results (default: 100).
  ///
  /// Returns a list of matching audiobooks, ordered by relevance.
  Future<List<Audiobook>> searchLocally(String query, {int limit = 100}) async {
    if (query.trim().isEmpty) {
      return [];
    }

    final lowerQuery = query.toLowerCase().trim();
    final queryWords =
        lowerQuery.split(RegExp(r'\s+')).where((w) => w.length >= 2).toList();

    if (queryWords.isEmpty) {
      return [];
    }

    // Get all records (we'll limit during processing for performance)
    final records = await _store.find(_db);
    final results = <({Audiobook audiobook, int score})>[];
    final maxProcess = limit * 5; // Process more records to find better matches
    int processed = 0;

    for (final record in records) {
      // Limit processing for performance
      if (processed >= maxProcess && results.length >= limit) {
        break;
      }
      processed++;

      // Use pre-computed lowercase fields if available for performance
      final title = (record.value['title_lower'] as String? ??
          (record.value['title'] as String? ?? '').toLowerCase());
      final author = (record.value['author_lower'] as String? ??
          (record.value['author'] as String? ?? '').toLowerCase());

      int score = 0;
      bool matches = false;

      // Score matches based on word positions and exact matches
      for (final word in queryWords) {
        if (title.contains(word)) {
          matches = true;
          // Higher score for title matches
          if (title.startsWith(word)) {
            score += 10; // Exact start match
          } else {
            score += 5; // Contains match
          }
        }
        if (author.contains(word)) {
          matches = true;
          // Lower score for author matches
          if (author.startsWith(word)) {
            score += 3;
          } else {
            score += 1;
          }
        }
      }

      if (matches) {
        results.add((
          audiobook: _mapToAudiobook(record.value),
          score: score,
        ));
      }
    }

    // Sort by score (highest first) and limit results
    results.sort((a, b) => b.score.compareTo(a.score));
    return results.take(limit).map((r) => r.audiobook).toList();
  }

  /// Gets metadata for audiobooks in a specific category.
  ///
  /// The [forumId] parameter is the forum ID to filter by.
  ///
  /// Returns a list of audiobooks from the specified forum.
  Future<List<Audiobook>> getByCategory(String forumId) async {
    final finder = Finder(
      filter: Filter.equals('forum_id', forumId),
    );
    final records = await _store.find(_db, finder: finder);
    return records.map((record) => _mapToAudiobook(record.value)).toList();
  }

  /// Gets statistics about stored metadata.
  ///
  /// Returns a map with statistics including total count, count by category, etc.
  Future<Map<String, dynamic>> getStatistics() async {
    final records = await _store.find(_db);
    final total = records.length;

    final byCategory = <String, int>{};
    final byForum = <String, int>{};

    for (final record in records) {
      final category = record.value['category'] as String? ?? 'Unknown';
      final forumId = record.value['forum_id'] as String? ?? 'Unknown';

      byCategory[category] = (byCategory[category] ?? 0) + 1;
      byForum[forumId] = (byForum[forumId] ?? 0) + 1;
    }

    // Get last sync time
    DateTime? lastSync;
    for (final record in records) {
      final lastSyncedStr = record.value['last_synced'] as String?;
      if (lastSyncedStr != null) {
        final syncTime = DateTime.parse(lastSyncedStr);
        if (lastSync == null || syncTime.isAfter(lastSync)) {
          lastSync = syncTime;
        }
      }
    }

    return {
      'total': total,
      'by_category': byCategory,
      'by_forum': byForum,
      'last_sync': lastSync?.toIso8601String(),
    };
  }
}
