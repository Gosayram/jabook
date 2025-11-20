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

import 'package:dio/dio.dart';

import 'package:jabook/core/constants/category_constants.dart';
import 'package:jabook/core/endpoints/endpoint_manager.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/parse/category_parser.dart' as category_parser;
import 'package:jabook/core/parse/rutracker_parser.dart' as rutracker_parser;
import 'package:jabook/features/library/domain/entities/audiobook.dart';
import 'package:jabook/features/library/domain/entities/audiobook_category.dart';

/// Service for caching all audiobooks from category 33 (Аудиокниги).
///
/// This service:
/// 1. Loads the static page index.php?c=33
/// 2. Parses all forums and subforums
/// 3. Loads all audiobooks from each forum
/// 4. Caches them in memory
/// 5. Provides methods to refresh/clear the cache
class AudiobooksCategoryCacheService {
  /// Private constructor for singleton pattern.
  AudiobooksCategoryCacheService._();

  /// Factory constructor to get the singleton instance.
  factory AudiobooksCategoryCacheService() => _instance;

  /// Singleton instance.
  static final AudiobooksCategoryCacheService _instance =
      AudiobooksCategoryCacheService._();

  /// Cached categories with their forums and subforums.
  List<AudiobookCategory>? _cachedCategories;

  /// Cached audiobooks by forum ID.
  final Map<String, List<Audiobook>> _cachedAudiobooksByForum = {};

  /// Timestamp of last cache update.
  DateTime? _lastCacheUpdate;

  /// Whether cache is currently being updated.
  bool _isUpdating = false;

  /// Gets cached categories, or loads them if not cached.
  Future<List<AudiobookCategory>> getCategories({
    required EndpointManager endpointManager,
    bool forceRefresh = false,
  }) async {
    if (_cachedCategories != null && !forceRefresh) {
      return _cachedCategories!;
    }

    final dio = await DioClient.instance;
    final indexUrl = await endpointManager.buildUrl(
      '/forum/index.php?c=${CategoryConstants.audiobooksCategoryId}',
    );

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

    final parser = category_parser.CategoryParser();
    final parsedCategories =
        await parser.parseCategories(response.data.toString());

    // Convert parsed categories to domain entities
    final categories = parsedCategories
        .map((cat) => AudiobookCategory(
              id: cat.id,
              name: cat.name,
              url: cat.url,
              subcategories: cat.subcategories
                  .map((sub) => AudiobookCategory(
                        id: sub.id,
                        name: sub.name,
                        url: sub.url,
                      ))
                  .toList(),
            ))
        .toList();

    _cachedCategories = categories;
    return categories;
  }

  /// Gets cached audiobooks for a specific forum, or loads them if not cached.
  Future<List<Audiobook>> getAudiobooksForForum(
    String forumId, {
    required EndpointManager endpointManager,
    required category_parser.CategoryParser categoryParser,
    required rutracker_parser.RuTrackerParser rutrackerParser,
    bool forceRefresh = false,
  }) async {
    if (_cachedAudiobooksByForum.containsKey(forumId) && !forceRefresh) {
      return _cachedAudiobooksByForum[forumId]!;
    }

    final dio = await DioClient.instance;
    final forumPath = '/forum/viewforum.php?f=$forumId';
    final forumUrl = await endpointManager.buildUrl(forumPath);

    final response = await dio.get(
      forumUrl,
      options: Options(
        responseType: ResponseType.plain,
        headers: {
          'Accept': 'text/html,application/xhtml+xml,application/xml',
          'Accept-Charset': 'windows-1251,utf-8',
        },
      ),
    );

    // Parse topics from forum page
    final topics = await categoryParser.parseCategoryTopics(
      response.data.toString(),
    );

    // Convert topics to audiobooks
    final audiobooks = topics
        .map((topic) => Audiobook(
              id: topic['id']?.toString() ?? '',
              title: topic['title']?.toString() ?? '',
              author: topic['author']?.toString() ?? 'Unknown',
              category: _extractCategoryFromForumId(forumId),
              size: topic['size']?.toString() ?? '0 MB',
              seeders: topic['seeders'] as int? ?? 0,
              leechers: topic['leechers'] as int? ?? 0,
              magnetUrl: _buildMagnetUrl(topic['id']?.toString() ?? ''),
              chapters: [],
              addedDate: topic['added_date'] as DateTime? ?? DateTime.now(),
            ))
        .toList();

    _cachedAudiobooksByForum[forumId] = audiobooks;
    return audiobooks;
  }

  /// Loads and caches all audiobooks from all forums in category 33.
  ///
  /// This method:
  /// 1. Loads index.php?c=33
  /// 2. Parses all forums and subforums
  /// 3. For each forum, loads all audiobooks
  /// 4. Caches everything in memory
  Future<void> loadAllAudiobooks({
    required EndpointManager endpointManager,
    required category_parser.CategoryParser categoryParser,
    required rutracker_parser.RuTrackerParser rutrackerParser,
    Function(int current, int total)? onProgress,
  }) async {
    if (_isUpdating) {
      return; // Already updating
    }

    _isUpdating = true;
    try {
      // Step 1: Load categories
      final categories = await getCategories(
        endpointManager: endpointManager,
        forceRefresh: true,
      );

      // Step 2: Collect all forum IDs (including subforums)
      // IMPORTANT: Only collect subforums (they contain actual audiobooks)
      // Parent forums (like "Новости, объявления") usually don't contain topics
      final allForumIds = <String>[];
      for (final category in categories) {
        // Add parent forum only if it has no subforums (rare case)
        if (category.subcategories.isEmpty) {
          allForumIds.add(category.id);
        }
        // Always add subforums - they contain the actual audiobooks
        for (final subcategory in category.subcategories) {
          allForumIds.add(subcategory.id);
        }
      }

      // Step 3: Load audiobooks for each forum
      var current = 0;
      for (final forumId in allForumIds) {
        current++;
        onProgress?.call(current, allForumIds.length);

        await getAudiobooksForForum(
          forumId,
          endpointManager: endpointManager,
          categoryParser: categoryParser,
          rutrackerParser: rutrackerParser,
          forceRefresh: true,
        );
      }

      _lastCacheUpdate = DateTime.now();
    } finally {
      _isUpdating = false;
    }
  }

  /// Gets all cached audiobooks from all forums.
  List<Audiobook> getAllCachedAudiobooks() {
    final allAudiobooks = <Audiobook>[];
    _cachedAudiobooksByForum.values.forEach(allAudiobooks.addAll);
    return allAudiobooks;
  }

  /// Clears all cached data.
  void clearCache() {
    _cachedCategories = null;
    _cachedAudiobooksByForum.clear();
    _lastCacheUpdate = null;
  }

  /// Gets the timestamp of last cache update.
  DateTime? get lastCacheUpdate => _lastCacheUpdate;

  /// Checks if cache is currently being updated.
  bool get isUpdating => _isUpdating;

  /// Checks if cache has data.
  bool get hasCachedData =>
      _cachedCategories != null || _cachedAudiobooksByForum.isNotEmpty;

  /// Helper method to extract category name from forum ID.
  String _extractCategoryFromForumId(String forumId) =>
      // This is a simplified version - you might want to map forum IDs to names
      CategoryConstants.categoryNameMap[forumId] ??
      CategoryConstants.defaultCategoryName;

  /// Helper method to build magnet URL from topic ID.
  String _buildMagnetUrl(String topicId) => topicId.isEmpty
      ? ''
      : CategoryConstants.magnetUrlTemplate.replaceAll('\$topicId', topicId);
}
