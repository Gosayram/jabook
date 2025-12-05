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

import 'package:dio/dio.dart';
import 'package:jabook/core/constants/category_constants.dart';
import 'package:jabook/core/data/local/database/app_database.dart';
import 'package:jabook/core/data/remote/rutracker/category_parser.dart';
import 'package:jabook/core/data/remote/rutracker/rutracker_parser.dart';
import 'package:jabook/core/infrastructure/endpoints/endpoint_manager.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/search/models/cache_settings.dart';
import 'package:jabook/core/search/models/cache_status.dart';
import 'package:sembast/sembast.dart';

/// Service for full synchronization of all RuTracker audiobook topics.
///
/// This service synchronizes all forums and topics, extracting metadata
/// including cover URLs and storing them in the cache database.
class FullSyncService {
  /// Creates a new FullSyncService instance.
  FullSyncService(
    this._appDatabase,
    this._endpointManager,
  );

  /// Database instance for cache operations.
  final AppDatabase _appDatabase;

  /// Endpoint manager for building URLs.
  final EndpointManager _endpointManager;

  /// Logger for sync operations.
  final StructuredLogger _logger = StructuredLogger();

  /// Category parser for extracting forums and topics.
  final CategoryParser _categoryParser = CategoryParser();

  /// Topic parser for extracting detailed metadata.
  final RuTrackerParser _topicParser = RuTrackerParser();

  /// Stream controller for sync progress.
  StreamController<SyncProgress>? _progressController;

  /// Whether sync is currently in progress.
  bool _isSyncing = false;

  /// Current sync progress.
  SyncProgress? _currentProgress;

  /// Gets stream of sync progress updates.
  Stream<SyncProgress> watchProgress() {
    _progressController ??= StreamController<SyncProgress>.broadcast();
    return _progressController!.stream;
  }

  /// Gets list of forum IDs that contain audiobooks.
  ///
  /// Returns list of forum IDs (as integers) from all audiobook categories.
  Future<List<int>> getAudiobookForumIds() async {
    try {
      await _logger.log(
        level: 'info',
        subsystem: 'full_sync',
        message: 'Getting list of audiobook forum IDs',
      );

      final dio = await DioClient.instance;
      const indexPath =
          '/forum/index.php?c=${CategoryConstants.audiobooksCategoryId}';
      final indexUrl = await _endpointManager.buildUrl(indexPath);

      final response = await dio.get(
        indexUrl,
        options: Options(
          responseType: ResponseType.plain,
          headers: {
            'Accept': 'text/html,application/xhtml+xml,application/xml',
            'Accept-Charset': 'windows-1251,utf-8',
          },
        ),
      );

      final categories = await _categoryParser.parseCategories(
        response.data.toString(),
      );

      // Extract all forum IDs (including subforums)
      final forumIds = <int>[];
      for (final category in categories) {
        forumIds.add(int.parse(category.id));
        for (final subcategory in category.subcategories) {
          forumIds.add(int.parse(subcategory.id));
        }
      }

      await _logger.log(
        level: 'info',
        subsystem: 'full_sync',
        message: 'Found audiobook forums',
        extra: {'forum_count': forumIds.length},
      );

      return forumIds;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'full_sync',
        message: 'Failed to get audiobook forum IDs',
        cause: e.toString(),
      );
      rethrow;
    }
  }

  /// Synchronizes all forums with audiobooks.
  ///
  /// This method gets all forum IDs and synchronizes each forum sequentially.
  Future<void> syncAllForums() async {
    if (_isSyncing) {
      await _logger.log(
        level: 'warning',
        subsystem: 'full_sync',
        message: 'Sync already in progress, skipping',
      );
      return;
    }

    _isSyncing = true;
    _progressController ??= StreamController<SyncProgress>.broadcast();

    try {
      await _logger.log(
        level: 'info',
        subsystem: 'full_sync',
        message: 'Starting full sync of all forums',
      );

      final forumIds = await getAudiobookForumIds();
      final totalForums = forumIds.length;
      var completedForums = 0;
      var totalTopics = 0;
      const completedTopics = 0;

      // Initialize progress
      _currentProgress = SyncProgress(
        totalForums: totalForums,
        completedForums: 0,
        totalTopics: 0,
        completedTopics: 0,
      );
      _progressController?.add(_currentProgress!);

      // Sync each forum
      for (final forumId in forumIds) {
        try {
          // Get forum name (we'll extract it during sync)
          final forumName = 'Forum $forumId';

          // Update progress
          _currentProgress = SyncProgress(
            totalForums: totalForums,
            completedForums: completedForums,
            totalTopics: totalTopics,
            completedTopics: completedTopics,
            currentForum: forumName,
          );
          _progressController?.add(_currentProgress!);

          // Sync forum topics
          final topicsCount = await syncForumTopics(forumId, forumName);

          // Update counters
          totalTopics += topicsCount;
          completedForums++;

          _currentProgress = SyncProgress(
            totalForums: totalForums,
            completedForums: completedForums,
            totalTopics: totalTopics,
            completedTopics: completedTopics,
            currentForum: forumName,
          );
          _progressController?.add(_currentProgress!);

          // Small delay to avoid rate limiting
          await Future.delayed(const Duration(milliseconds: 500));
        } on Exception catch (e) {
          await _logger.log(
            level: 'warning',
            subsystem: 'full_sync',
            message: 'Failed to sync forum',
            cause: e.toString(),
            extra: {'forum_id': forumId},
          );
          // Continue with next forum
          completedForums++;
        }
      }

      await _logger.log(
        level: 'info',
        subsystem: 'full_sync',
        message: 'Full sync completed',
        extra: {
          'total_forums': totalForums,
          'total_topics': totalTopics,
        },
      );
    } finally {
      _isSyncing = false;
      _currentProgress = null;
    }
  }

  /// Synchronizes topics from a specific forum.
  ///
  /// Returns the number of topics processed.
  Future<int> syncForumTopics(int forumId, String forumName) async {
    try {
      await _logger.log(
        level: 'info',
        subsystem: 'full_sync',
        message: 'Syncing forum topics',
        extra: {
          'forum_id': forumId,
          'forum_name': forumName,
        },
      );

      final dio = await DioClient.instance;
      final forumPath = '/forum/viewforum.php?f=$forumId';

      // Get base URL for cover URL normalization
      final baseUrl = await _endpointManager.getActiveEndpoint();

      var page = 1;
      var totalTopics = 0;
      const topicsPerPage = 50; // Typical RuTracker page size
      const batchSize = 10; // Save in batches of 10

      // Batch buffer for efficient database writes
      final batchBuffer = <Map<String, dynamic>>[];

      // Sync all pages
      while (true) {
        final pagePath = '$forumPath&start=${(page - 1) * topicsPerPage}';
        final pageUrl = await _endpointManager.buildUrl(pagePath);

        final response = await dio.get(
          pageUrl,
          options: Options(
            responseType: ResponseType.plain,
            headers: {
              'Accept': 'text/html,application/xhtml+xml,application/xml',
              'Accept-Charset': 'windows-1251,utf-8',
            },
          ),
        );

        final topics = await _categoryParser.parseCategoryTopics(
          response.data.toString(),
        );

        if (topics.isEmpty) {
          // No more topics
          break;
        }

        // Sync each topic
        for (final topic in topics) {
          try {
            final topicId = topic['id']?.toString() ?? '';
            if (topicId.isEmpty) continue;

            // Update progress
            _currentProgress = SyncProgress(
              totalForums: _currentProgress?.totalForums ?? 0,
              completedForums: _currentProgress?.completedForums ?? 0,
              totalTopics: _currentProgress?.totalTopics ?? 0,
              completedTopics: totalTopics,
              currentForum: forumName,
              currentTopic: topic['title']?.toString() ?? topicId,
            );
            _progressController?.add(_currentProgress!);

            // Sync topic and add to batch buffer
            final metadataMap = await _syncTopicToMap(
              topicId,
              baseUrl,
              forumId,
              forumName,
            );

            if (metadataMap != null) {
              batchBuffer.add({
                'topic_id': topicId,
                'metadata': metadataMap,
              });

              // Save batch when buffer is full
              if (batchBuffer.length >= batchSize) {
                await _saveBatch(batchBuffer);
                batchBuffer.clear();
              }
            }

            totalTopics++;

            // Small delay to avoid rate limiting
            await Future.delayed(const Duration(milliseconds: 1000));
          } on Exception catch (e) {
            await _logger.log(
              level: 'warning',
              subsystem: 'full_sync',
              message: 'Failed to sync topic',
              cause: e.toString(),
              extra: {
                'forum_id': forumId,
                'topic_id': topic['id'],
              },
            );
            // Continue with next topic
          }
        }

        // Check if there are more pages
        if (topics.length < topicsPerPage) {
          break;
        }

        page++;
      }

      // Save remaining items in buffer
      if (batchBuffer.isNotEmpty) {
        await _saveBatch(batchBuffer);
        batchBuffer.clear();
      }

      await _logger.log(
        level: 'info',
        subsystem: 'full_sync',
        message: 'Forum sync completed',
        extra: {
          'forum_id': forumId,
          'forum_name': forumName,
          'topics_count': totalTopics,
        },
      );

      return totalTopics;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'full_sync',
        message: 'Failed to sync forum topics',
        cause: e.toString(),
        extra: {
          'forum_id': forumId,
          'forum_name': forumName,
        },
      );
      rethrow;
    }
  }

  /// Synchronizes a specific topic and returns metadata map (without saving).
  ///
  /// This method extracts detailed metadata including cover URL and returns it
  /// as a map. Use this for batch operations.
  Future<Map<String, dynamic>?> _syncTopicToMap(
    String topicId,
    String baseUrl,
    int forumId,
    String forumName,
  ) async {
    try {
      final dio = await DioClient.instance;
      final topicPath = '/forum/viewtopic.php?t=$topicId';
      final topicUrl = await _endpointManager.buildUrl(topicPath);

      final response = await dio.get(
        topicUrl,
        options: Options(
          responseType: ResponseType.plain,
          headers: {
            'Accept': 'text/html,application/xhtml+xml,application/xml',
            'Accept-Charset': 'windows-1251,utf-8',
          },
        ),
      );

      // Parse topic details
      final audiobook = await _topicParser.parseTopicDetails(
        response.data,
        contentType: response.headers.value('content-type'),
        baseUrl: baseUrl,
      );

      if (audiobook == null) {
        await _logger.log(
          level: 'warning',
          subsystem: 'full_sync',
          message: 'Failed to parse topic',
          extra: {'topic_id': topicId},
        );
        return null;
      }

      // CRITICAL: Normalize cover URL to absolute URL
      String? normalizedCoverUrl;
      if (audiobook.coverUrl != null && audiobook.coverUrl!.isNotEmpty) {
        // Use the normalization function from parser
        normalizedCoverUrl = await _normalizeCoverUrl(
          audiobook.coverUrl!,
          baseUrl: baseUrl,
        );
      }

      // Convert to map for storage
      final metadataMap = _audiobookToMap(
        audiobook,
        forumId.toString(),
        forumName,
        normalizedCoverUrl,
      );

      await _logger.log(
        level: 'debug',
        subsystem: 'full_sync',
        message: 'Topic synced',
        extra: {
          'topic_id': topicId,
          'has_cover': normalizedCoverUrl != null,
        },
      );

      return metadataMap;
    } on Exception catch (e) {
      await _logger.log(
        level: 'warning',
        subsystem: 'full_sync',
        message: 'Failed to sync topic',
        cause: e.toString(),
        extra: {'topic_id': topicId},
      );
      return null;
    }
  }

  /// Synchronizes a specific topic and saves metadata to cache.
  ///
  /// This method extracts detailed metadata including cover URL and saves it
  /// to the database with normalized absolute cover URL.
  /// For batch operations, use _syncTopicToMap instead.
  Future<void> syncTopic(
    String topicId,
    String baseUrl,
    int forumId,
    String forumName,
  ) async {
    final metadataMap = await _syncTopicToMap(
      topicId,
      baseUrl,
      forumId,
      forumName,
    );

    if (metadataMap != null) {
      // Save to database
      final db = await _appDatabase.ensureInitialized();
      final store = _appDatabase.audiobookMetadataStore;
      await store.record(topicId).put(db, metadataMap);
    }
  }

  /// Saves a batch of metadata to database efficiently.
  ///
  /// The [batch] parameter is a list of maps, each containing 'topic_id' and 'metadata'.
  Future<void> _saveBatch(List<Map<String, dynamic>> batch) async {
    if (batch.isEmpty) return;

    try {
      final db = await _appDatabase.ensureInitialized();
      final store = _appDatabase.audiobookMetadataStore;

      // Update lastUpdateTime in cache settings to keep cache fresh
      // This is done periodically (every 10 batches) to avoid excessive writes
      final shouldUpdateCacheTime = batch.length >= 10 ||
          (DateTime.now().millisecondsSinceEpoch % 10 == 0);

      if (shouldUpdateCacheTime) {
        try {
          final settingsStore = _appDatabase.searchCacheSettingsStore;
          final settingsMap = await settingsStore.record('settings').get(db);
          if (settingsMap != null) {
            final settings = CacheSettings.fromMap(settingsMap);
            // Only update if last update was more than 1 hour ago
            final shouldUpdate = settings.lastUpdateTime == null ||
                DateTime.now().difference(settings.lastUpdateTime!) >
                    const Duration(hours: 1);
            if (shouldUpdate) {
              final updatedSettings = settings.copyWith(
                lastUpdateTime: DateTime.now(),
              );
              await settingsStore
                  .record('settings')
                  .put(db, updatedSettings.toMap());
            }
          }
        } on Exception {
          // Ignore errors - cache time update is not critical
        }
      }

      // Use transaction for atomic batch write
      await db.transaction((transaction) async {
        for (final item in batch) {
          final topicId = item['topic_id'] as String;
          final metadata = item['metadata'] as Map<String, dynamic>;
          await store.record(topicId).put(transaction, metadata);
        }
      });

      await _logger.log(
        level: 'debug',
        subsystem: 'full_sync',
        message: 'Batch saved',
        extra: {'batch_size': batch.length},
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'full_sync',
        message: 'Failed to save batch',
        cause: e.toString(),
        extra: {'batch_size': batch.length},
      );
      rethrow;
    }
  }

  /// Normalizes cover URL to absolute URL.
  ///
  /// Converts relative URLs to absolute URLs using provided baseUrl.
  Future<String?> _normalizeCoverUrl(String url, {String? baseUrl}) async {
    if (url.isEmpty) return null;

    // If already absolute, return as is
    if (url.startsWith('http://') || url.startsWith('https://')) {
      return url;
    }

    // Determine base URL
    String effectiveBaseUrl;
    if (baseUrl != null && baseUrl.isNotEmpty) {
      effectiveBaseUrl = baseUrl.endsWith('/')
          ? baseUrl.substring(0, baseUrl.length - 1)
          : baseUrl;
      if (!effectiveBaseUrl.startsWith('http://') &&
          !effectiveBaseUrl.startsWith('https://')) {
        effectiveBaseUrl = 'https://$effectiveBaseUrl';
      }
    } else {
      effectiveBaseUrl = 'https://rutracker.org';
    }

    // Normalize relative URLs
    if (url.startsWith('/')) {
      return '$effectiveBaseUrl$url';
    }

    if (url.startsWith('//')) {
      return 'https:$url';
    }

    if (!url.contains('://')) {
      final cleanUrl = url.startsWith('/') ? url.substring(1) : url;
      return '$effectiveBaseUrl/$cleanUrl';
    }

    // Return original if can't normalize
    return url;
  }

  /// Converts Audiobook to Map for storage with extended fields.
  Map<String, dynamic> _audiobookToMap(
    dynamic audiobook,
    String forumId,
    String forumName,
    String? normalizedCoverUrl,
  ) {
    final title = audiobook.title ?? '';
    final author = audiobook.author ?? '';
    final titleLower = title.toLowerCase();
    final authorLower = author.toLowerCase();

    // Build search text
    final searchParts = <String>[
      title,
      author,
      if (audiobook.performer != null) audiobook.performer!,
      ...(audiobook.genres ?? []),
    ];
    final searchText = searchParts.join(' ');
    final searchTextLower = searchText.toLowerCase();

    // Extract parts from chapters
    final parts = <String>[];
    if (audiobook.chapters != null) {
      for (final chapter in audiobook.chapters!) {
        // Chapter is a Chapter object, not a Map
        final chapterTitle = chapter.title;
        if (chapterTitle != null && chapterTitle.isNotEmpty) {
          parts.add(chapterTitle);
        }
      }
    }

    // Extract series from relatedAudiobooks if available
    String? series;
    int? seriesOrder;
    if (audiobook.relatedAudiobooks != null &&
        audiobook.relatedAudiobooks!.isNotEmpty) {
      // Try to extract series name from first related audiobook title
      // (assuming they share a common prefix)
      final firstRelated = audiobook.relatedAudiobooks!.first;
      if (firstRelated.title != null) {
        // Simple heuristic: if titles share common prefix, it's a series
        final commonPrefix = _findCommonPrefix(title, firstRelated.title!);
        if (commonPrefix.length > 10) {
          // Only consider it a series if prefix is substantial
          series = commonPrefix.trim();
        }
      }
    }

    return {
      'topic_id': audiobook.id ?? '',
      'title': title,
      'title_lower': titleLower,
      'author': author,
      'author_lower': authorLower,
      'category': audiobook.category ?? '',
      'forum_id': int.tryParse(forumId) ?? 0,
      'forum_name': forumName,
      'size': audiobook.size ?? '0 MB',
      'seeders': audiobook.seeders ?? 0,
      'leechers': audiobook.leechers ?? 0,
      'magnet_url': audiobook.magnetUrl ?? '',
      'cover_url': normalizedCoverUrl, // CRITICAL: Use normalized absolute URL
      'performer': audiobook.performer,
      'genres': audiobook.genres ?? [],
      'added_date': (audiobook.addedDate ?? DateTime.now()).toIso8601String(),
      'last_updated': DateTime.now().toIso8601String(),
      'last_synced': DateTime.now().toIso8601String(),
      'chapters': (audiobook.chapters ?? [])
          .map((c) => {
                'title': c.title ?? '',
                'duration_ms': c.durationMs,
                'file_index': c.fileIndex,
                'start_byte': c.startByte,
                'end_byte': c.endByte,
              })
          .toList(),
      'duration': null,
      'bitrate': null,
      'audio_codec': null,
      // Extended fields for smart search
      'series': series,
      'series_order': seriesOrder,
      'parts': parts,
      'keywords': [], // Will be extracted from description if needed
      'search_text': searchText,
      'search_text_lower': searchTextLower,
      // Cache metadata
      'cached_at': DateTime.now().toIso8601String(),
      'cache_version': 1,
      'is_stale': false,
    };
  }

  /// Finds common prefix between two strings.
  String _findCommonPrefix(String a, String b) {
    final minLength = a.length < b.length ? a.length : b.length;
    var i = 0;
    while (i < minLength && a[i] == b[i]) {
      i++;
    }
    return a.substring(0, i);
  }

  /// Disposes resources.
  void dispose() {
    _progressController?.close();
    _progressController = null;
  }
}
