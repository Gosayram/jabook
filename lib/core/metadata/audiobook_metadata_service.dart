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
    final operationId =
        'metadata_collect_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    await logger.log(
      level: 'info',
      subsystem: 'metadata',
      message: 'Metadata collection started',
      operationId: operationId,
      context: 'metadata_collection',
      extra: {
        'forum_title': forumTitle,
        'force': force,
      },
    );

    // Resolve forum URL dynamically
    final forumResolveStartTime = DateTime.now();
    final forumUrl = await _resolver.resolveForumUrl(
      categoryTitle: 'Аудиокниги',
      forumTitle: forumTitle,
    );
    final forumResolveDuration =
        DateTime.now().difference(forumResolveStartTime).inMilliseconds;

    if (forumUrl == null) {
      await logger.log(
        level: 'error',
        subsystem: 'metadata',
        message: 'Failed to resolve forum URL',
        operationId: operationId,
        context: 'metadata_collection',
        durationMs: forumResolveDuration,
        extra: {'forum_title': forumTitle},
      );
      return 0;
    }

    await logger.log(
      level: 'debug',
      subsystem: 'metadata',
      message: 'Forum URL resolved',
      operationId: operationId,
      context: 'metadata_collection',
      durationMs: forumResolveDuration,
      extra: {
        'forum_title': forumTitle,
        'forum_url': forumUrl.toString(),
      },
    );

    // Extract forum ID from URL
    final forumIdMatch = RegExp(r'f=(\d+)').firstMatch(forumUrl.toString());
    final forumId = forumIdMatch?.group(1) ?? '';

    if (forumId.isEmpty) {
      await logger.log(
        level: 'error',
        subsystem: 'metadata',
        message: 'Failed to extract forum ID from URL',
        operationId: operationId,
        context: 'metadata_collection',
        extra: {'forum_url': forumUrl.toString()},
      );
      return 0;
    }

    // Check if update is needed
    final needsUpdateStartTime = DateTime.now();
    final needsUpdateResult = !force && await needsUpdate(forumId);
    final needsUpdateDuration =
        DateTime.now().difference(needsUpdateStartTime).inMilliseconds;

    if (needsUpdateResult) {
      await logger.log(
        level: 'info',
        subsystem: 'metadata',
        message: 'Skipping update for forum (recently updated)',
        operationId: operationId,
        context: 'metadata_collection',
        durationMs: DateTime.now().difference(startTime).inMilliseconds,
        extra: {
          'forum_title': forumTitle,
          'forum_id': forumId,
          'check_duration_ms': needsUpdateDuration,
        },
      );
      return 0;
    }

    await logger.log(
      level: 'info',
      subsystem: 'metadata',
      message: 'Starting metadata collection for forum',
      operationId: operationId,
      context: 'metadata_collection',
      extra: {
        'forum_title': forumTitle,
        'forum_id': forumId,
        'forum_url': forumUrl.toString(),
        'force': force,
      },
    );

    final dio = await DioClient.instance;
    final lastSynced = DateTime.now();

    var totalCollected = 0;
    var start = 0;
    var hasMore = true;
    var currentForumUrl = forumUrl;
    var currentForumId = forumId;
    var pageNumber = 0;

    try {
      while (hasMore) {
        pageNumber++;
        final pageStartTime = DateTime.now();

        // Add delay to avoid rate limiting
        if (start > 0) {
          await Future.delayed(const Duration(milliseconds: requestDelayMs));
        }

        await logger.log(
          level: 'debug',
          subsystem: 'metadata',
          message: 'Fetching forum page',
          operationId: operationId,
          context: 'metadata_collection',
          extra: {
            'forum_title': forumTitle,
            'forum_id': currentForumId,
            'page_number': pageNumber,
            'start': start,
            'url': currentForumUrl.toString(),
            'query_params': {'start': start},
          },
        );

        final response = await dio.get(
          currentForumUrl.toString(),
          queryParameters: {
            'start': start,
          },
        );

        final pageRequestDuration =
            DateTime.now().difference(pageStartTime).inMilliseconds;
        final responseSize = response.data?.toString().length ?? 0;

        await logger.log(
          level: 'debug',
          subsystem: 'metadata',
          message: 'Forum page response received',
          operationId: operationId,
          context: 'metadata_collection',
          durationMs: pageRequestDuration,
          extra: {
            'forum_title': forumTitle,
            'page_number': pageNumber,
            'start': start,
            'status_code': response.statusCode,
            'response_size_bytes': responseSize,
          },
        );

        if (response.statusCode == 404) {
          // Forum might have moved, invalidate cache and re-resolve
          await logger.log(
            level: 'warning',
            subsystem: 'metadata',
            message: 'Forum returned 404, invalidating cache and re-resolving',
            operationId: operationId,
            context: 'metadata_collection',
            extra: {
              'forum_title': forumTitle,
              'page_number': pageNumber,
              'url': currentForumUrl.toString(),
            },
          );
          await _resolver.invalidateCache(forumTitle);
          // Try to re-resolve
          final reResolveStartTime = DateTime.now();
          final newForumUrl = await _resolver.resolveForumUrl(
            categoryTitle: 'Аудиокниги',
            forumTitle: forumTitle,
          );
          final reResolveDuration =
              DateTime.now().difference(reResolveStartTime).inMilliseconds;

          if (newForumUrl == null) {
            await logger.log(
              level: 'error',
              subsystem: 'metadata',
              message: 'Failed to re-resolve forum after 404',
              operationId: operationId,
              context: 'metadata_collection',
              durationMs: reResolveDuration,
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
            await logger.log(
              level: 'info',
              subsystem: 'metadata',
              message: 'Re-resolved forum after 404',
              operationId: operationId,
              context: 'metadata_collection',
              durationMs: reResolveDuration,
              extra: {
                'forum_title': forumTitle,
                'old_forum_id': oldForumId,
                'new_forum_id': newForumId,
                'old_url': forumUrl.toString(),
                'new_url': newForumUrl.toString(),
              },
            );
            // Retry the same request with new URL
            continue;
          } else {
            break;
          }
        }

        if (response.statusCode != 200) {
          await logger.log(
            level: 'warning',
            subsystem: 'metadata',
            message: 'Failed to fetch forum page',
            operationId: operationId,
            context: 'metadata_collection',
            durationMs: pageRequestDuration,
            extra: {
              'status': response.statusCode,
              'forum': forumTitle,
              'page_number': pageNumber,
              'url': currentForumUrl.toString(),
            },
          );
          break;
        }

        // Parse search results
        final parseStartTime = DateTime.now();
        final audiobooks = await _parser.parseSearchResults(response.data);
        final parseDuration =
            DateTime.now().difference(parseStartTime).inMilliseconds;

        await logger.log(
          level: 'debug',
          subsystem: 'metadata',
          message: 'Parsed forum page',
          operationId: operationId,
          context: 'metadata_collection',
          durationMs: parseDuration,
          extra: {
            'forum_title': forumTitle,
            'page_number': pageNumber,
            'topics_found': audiobooks.length,
            'response_size_bytes': responseSize,
          },
        );

        if (audiobooks.isEmpty) {
          await logger.log(
            level: 'debug',
            subsystem: 'metadata',
            message: 'No more topics found, ending collection',
            operationId: operationId,
            context: 'metadata_collection',
            extra: {
              'forum_title': forumTitle,
              'page_number': pageNumber,
              'total_collected': totalCollected,
            },
          );
          hasMore = false;
          break;
        }

        // Save batch
        final saveBatchStartTime = DateTime.now();
        await _saveBatch(audiobooks, currentForumId, forumTitle, lastSynced);
        final saveBatchDuration =
            DateTime.now().difference(saveBatchStartTime).inMilliseconds;
        totalCollected += audiobooks.length;

        await logger.log(
          level: 'info',
          subsystem: 'metadata',
          message: 'Saved batch of audiobooks',
          operationId: operationId,
          context: 'metadata_collection',
          durationMs: saveBatchDuration,
          extra: {
            'forum_title': forumTitle,
            'forum_id': currentForumId,
            'page_number': pageNumber,
            'batch_count': audiobooks.length,
            'total_collected': totalCollected,
            'start': start,
            'timings': {
              'page_request_ms': pageRequestDuration,
              'parse_ms': parseDuration,
              'save_ms': saveBatchDuration,
              'total_page_ms':
                  DateTime.now().difference(pageStartTime).inMilliseconds,
            },
          },
        );

        // Check if we should continue (if we got less than expected, likely no more)
        if (audiobooks.length < batchSize) {
          await logger.log(
            level: 'debug',
            subsystem: 'metadata',
            message: 'Batch size smaller than expected, ending collection',
            operationId: operationId,
            context: 'metadata_collection',
            extra: {
              'forum_title': forumTitle,
              'batch_count': audiobooks.length,
              'expected_batch_size': batchSize,
            },
          );
          hasMore = false;
        } else {
          start += audiobooks.length;
        }
      }

      final totalDuration = DateTime.now().difference(startTime).inMilliseconds;

      await logger.log(
        level: 'info',
        subsystem: 'metadata',
        message: 'Completed metadata collection for forum',
        operationId: operationId,
        context: 'metadata_collection',
        durationMs: totalDuration,
        extra: {
          'forum_title': forumTitle,
          'forum_id': currentForumId,
          'total_collected': totalCollected,
          'pages_processed': pageNumber,
          'timings': {
            'forum_resolve_ms': forumResolveDuration,
            'total_duration_ms': totalDuration,
          },
        },
      );

      return totalCollected;
    } on Exception catch (e) {
      final totalDuration = DateTime.now().difference(startTime).inMilliseconds;

      await logger.log(
        level: 'error',
        subsystem: 'metadata',
        message: 'Failed to collect metadata for forum',
        operationId: operationId,
        context: 'metadata_collection',
        durationMs: totalDuration,
        cause: e.toString(),
        extra: {
          'forum_title': forumTitle,
          'forum_id': currentForumId,
          'total_collected': totalCollected,
          'pages_processed': pageNumber,
          'stack_trace':
              (e is Error) ? (e as Error).stackTrace.toString() : null,
        },
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
    final operationId =
        'metadata_collect_all_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();
    final results = <String, int>{};

    await logger.log(
      level: 'info',
      subsystem: 'metadata',
      message: 'Starting full metadata collection',
      operationId: operationId,
      context: 'metadata_collection_all',
      extra: {
        'force': force,
        'categories': forumLabels.length,
        'forum_labels': forumLabels,
      },
    );

    // Ensure all forums are resolved first
    final resolveStartTime = DateTime.now();
    await ensureForumsResolved();
    final resolveDuration =
        DateTime.now().difference(resolveStartTime).inMilliseconds;

    await logger.log(
      level: 'debug',
      subsystem: 'metadata',
      message: 'All forums resolved',
      operationId: operationId,
      context: 'metadata_collection_all',
      durationMs: resolveDuration,
      extra: {
        'forums_count': forumLabels.length,
      },
    );

    for (var i = 0; i < forumLabels.length; i++) {
      final forumTitle = forumLabels[i];
      final categoryStartTime = DateTime.now();

      try {
        await logger.log(
          level: 'debug',
          subsystem: 'metadata',
          message: 'Starting collection for category',
          operationId: operationId,
          context: 'metadata_collection_all',
          extra: {
            'forum_title': forumTitle,
            'category_index': i + 1,
            'total_categories': forumLabels.length,
          },
        );

        final count =
            await collectMetadataForCategory(forumTitle, force: force);
        results[forumTitle] = count;

        final categoryDuration =
            DateTime.now().difference(categoryStartTime).inMilliseconds;

        await logger.log(
          level: 'info',
          subsystem: 'metadata',
          message: 'Completed collection for category',
          operationId: operationId,
          context: 'metadata_collection_all',
          durationMs: categoryDuration,
          extra: {
            'forum_title': forumTitle,
            'category_index': i + 1,
            'total_categories': forumLabels.length,
            'collected_count': count,
          },
        );

        // Add delay between categories
        if (i < forumLabels.length - 1) {
          await Future.delayed(const Duration(milliseconds: requestDelayMs));
        }
      } on Exception catch (e) {
        final categoryDuration =
            DateTime.now().difference(categoryStartTime).inMilliseconds;

        await logger.log(
          level: 'error',
          subsystem: 'metadata',
          message: 'Failed to collect metadata for category',
          operationId: operationId,
          context: 'metadata_collection_all',
          durationMs: categoryDuration,
          cause: e.toString(),
          extra: {
            'forum_title': forumTitle,
            'category_index': i + 1,
            'total_categories': forumLabels.length,
            'stack_trace':
                (e is Error) ? (e as Error).stackTrace.toString() : null,
          },
        );
        results[forumTitle] = 0;
      }
    }

    final totalDuration = DateTime.now().difference(startTime).inMilliseconds;
    final totalCollected = results.values.fold(0, (sum, count) => sum + count);

    await logger.log(
      level: 'info',
      subsystem: 'metadata',
      message: 'Completed full metadata collection',
      operationId: operationId,
      context: 'metadata_collection_all',
      durationMs: totalDuration,
      extra: {
        'results': results,
        'total_collected': totalCollected,
        'categories_processed': forumLabels.length,
        'timings': {
          'forum_resolution_ms': resolveDuration,
          'total_duration_ms': totalDuration,
        },
      },
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
    var processed = 0;

    for (final record in records) {
      // Limit processing for performance
      if (processed >= maxProcess && results.length >= limit) {
        break;
      }
      processed++;

      // Use pre-computed lowercase fields if available for performance
      final title = record.value['title_lower'] as String? ??
          (record.value['title'] as String? ?? '').toLowerCase();
      final author = record.value['author_lower'] as String? ??
          (record.value['author'] as String? ?? '').toLowerCase();

      var score = 0;
      var matches = false;

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
