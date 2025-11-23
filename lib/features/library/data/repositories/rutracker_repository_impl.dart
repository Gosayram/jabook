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

import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:html/parser.dart' as parser;
import 'package:jabook/core/constants/category_constants.dart';
import 'package:jabook/core/endpoints/endpoint_manager.dart';
import 'package:jabook/core/errors/failures.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/parse/category_parser.dart' as category_parser;
import 'package:jabook/core/parse/rutracker_parser.dart' as rutracker_parser;
import 'package:jabook/features/library/domain/entities/audiobook.dart';
import 'package:jabook/features/library/domain/entities/audiobook_category.dart';
import 'package:jabook/features/library/domain/repositories/rutracker_repository.dart';
import 'package:windows1251/windows1251.dart';

/// Implementation of the RuTracker repository for accessing audiobook data.
///
/// This class provides concrete implementations of the RuTrackerRepository interface,
/// handling data fetching, parsing, and caching operations for audiobooks and categories.
class RuTrackerRepositoryImpl implements RuTrackerRepository {
  /// Creates a new RuTrackerRepositoryImpl instance.
  RuTrackerRepositoryImpl({
    required EndpointManager endpointManager,
    required rutracker_parser.RuTrackerParser parser,
    required category_parser.CategoryParser categoryParser,
  })  : _endpointManager = endpointManager,
        _parser = parser,
        _categoryParser = categoryParser;

  final EndpointManager _endpointManager;
  final rutracker_parser.RuTrackerParser _parser;
  final category_parser.CategoryParser _categoryParser;

  @override
  Future<List<Audiobook>> searchAudiobooks(String query, {int page = 1}) async {
    try {
      final dio = await DioClient.instance;

      // Build search URL with proper RuTracker parameters
      // CRITICAL: Use tracker.php (not search.php) for searching torrents/audiobooks
      // tracker.php is the default search method on RuTracker for "раздачи" (torrents)
      // Try to use c=33 to filter by audiobooks category
      final searchPath =
          '/forum/tracker.php?nm=$query&c=${CategoryConstants.audiobooksCategoryId}&start=${(page - 1) * CategoryConstants.searchResultsPerPage}';
      final searchUrl = await _endpointManager.buildUrl(searchPath);

      final response = await dio.get(
        searchUrl,
        options: Options(
          responseType: ResponseType.plain,
          headers: {
            'Accept': 'text/html,application/xhtml+xml,application/xml',
            'Accept-Charset': 'windows-1251,utf-8',
          },
        ),
      );

      final results = await _parser.parseSearchResults(response.data);
      return results
          .map((audiobook) => Audiobook(
                id: audiobook.id,
                title: audiobook.title,
                author: audiobook.author,
                category: audiobook.category,
                size: audiobook.size,
                seeders: audiobook.seeders,
                leechers: audiobook.leechers,
                magnetUrl: audiobook.magnetUrl,
                coverUrl: audiobook.coverUrl,
                chapters: audiobook.chapters
                    .map((chapter) => Chapter(
                          title: chapter.title,
                          durationMs: chapter.durationMs,
                          fileIndex: chapter.fileIndex,
                          startByte: chapter.startByte,
                          endByte: chapter.endByte,
                        ))
                    .toList(),
                addedDate: audiobook.addedDate,
                duration: audiobook.duration,
                bitrate: audiobook.bitrate,
                audioCodec: audiobook.audioCodec,
              ))
          .toList();
    } on DioException catch (e) {
      throw NetworkFailure('Search failed: ${e.message}');
    } on Exception {
      throw const NetworkFailure('Failed to search audiobooks');
    }
  }

  @override
  Future<List<AudiobookCategory>> getCategories() async {
    try {
      final dio = await DioClient.instance;

      // CRITICAL: Load index.php?c=33 to get ONLY audiobooks category structure
      // This is the static page that contains all forums and subforums for audiobooks
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

      final categories = await category_parser.CategoryParser()
          .parseCategories(response.data.toString());
      return categories
          .map((category) => AudiobookCategory(
                id: category.id,
                name: category.name,
                url: category.url,
                subcategories: category.subcategories
                    .map((subcategory) => AudiobookCategory(
                          id: subcategory.id,
                          name: subcategory.name,
                          url: subcategory.url,
                        ))
                    .toList(),
              ))
          .toList();
    } on DioException catch (e) {
      throw NetworkFailure('Failed to fetch categories: ${e.message}');
    } on Exception {
      throw const NetworkFailure('Failed to get categories');
    }
  }

  @override
  Future<List<Audiobook>> getCategoryAudiobooks(String categoryId,
      {int page = 1}) async {
    try {
      final dio = await DioClient.instance;

      final forumPath = '/forum/viewforum.php?f=$categoryId';
      final forumUrl = await _endpointManager.buildUrl(forumPath);

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

      // Parse topics from category page
      final topics =
          await _categoryParser.parseCategoryTopics(response.data.toString());

      // Convert topics to audiobooks
      return topics
          .map((topic) => Audiobook(
                id: topic['id']?.toString() ?? '',
                title: topic['title']?.toString() ?? '',
                author: topic['author']?.toString() ?? 'Unknown',
                category: _extractCategoryFromForumId(categoryId),
                size: topic['size']?.toString() ?? '0 MB',
                seeders: topic['seeders'] as int? ?? 0,
                leechers: topic['leechers'] as int? ?? 0,
                magnetUrl: _buildMagnetUrl(topic['id']?.toString() ?? ''),
                chapters: [],
                addedDate: topic['added_date'] as DateTime? ?? DateTime.now(),
              ))
          .toList();
    } on DioException catch (e) {
      throw NetworkFailure('Failed to get category audiobooks: ${e.message}');
    } on Exception {
      throw const NetworkFailure('Failed to get category audiobooks');
    }
  }

  @override
  Future<Audiobook?> getAudiobookDetails(String audiobookId) async {
    try {
      final dio = await DioClient.instance;

      final topicPath = '/forum/viewtopic.php?t=$audiobookId';
      final topicUrl = await _endpointManager.buildUrl(topicPath);
      final response = await dio.get(
        topicUrl,
        options: Options(
          // Get raw bytes (Brotli decompression handled automatically by DioBrotliTransformer)
          // Bytes are ready for Windows-1251 decoding
          responseType: ResponseType.bytes,
          headers: {
            'Accept': 'text/html,application/xhtml+xml,application/xml',
            'Accept-Charset': 'windows-1251,utf-8',
          },
        ),
      );

      // Pass response data and headers to parser for proper encoding detection
      // Note: Brotli decompression is handled automatically by DioBrotliTransformer
      final baseUrl = await _endpointManager.getActiveEndpoint();
      final details = await _parser.parseTopicDetails(
        response.data,
        contentType: response.headers.value('content-type'),
        baseUrl: baseUrl,
      );
      if (details == null) return null;

      return Audiobook(
        id: details.id,
        title: details.title,
        author: details.author,
        category: details.category,
        size: details.size,
        seeders: details.seeders,
        leechers: details.leechers,
        magnetUrl: details.magnetUrl,
        coverUrl: details.coverUrl,
        chapters: details.chapters
            .map((chapter) => Chapter(
                  title: chapter.title,
                  durationMs: chapter.durationMs,
                  fileIndex: chapter.fileIndex,
                  startByte: chapter.startByte,
                  endByte: chapter.endByte,
                ))
            .toList(),
        addedDate: details.addedDate,
        duration: details.duration,
        bitrate: details.bitrate,
        audioCodec: details.audioCodec,
      );
    } on DioException catch (e) {
      throw NetworkFailure('Failed to fetch audiobook details: ${e.message}');
    } on Exception {
      throw const NetworkFailure('Failed to get audiobook details');
    }
  }

  @override
  Future<Map<String, dynamic>> getPaginationInfo(String url) async {
    try {
      final dio = await DioClient.instance;
      final response = await dio.get(url);
      return _parsePaginationInfo(response.data.toString());
    } on Exception {
      return {
        'currentPage': 1,
        'totalPages': 1,
        'hasNext': false,
        'hasPrevious': false
      };
    }
  }

  @override
  Future<List<Map<String, String>>> getSortingOptions(String categoryId) async {
    try {
      final dio = await DioClient.instance;

      final forumPath = '/forum/viewforum.php?f=$categoryId';
      final forumUrl = await _endpointManager.buildUrl(forumPath);
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

      return _parseSortingOptions(response.data.toString());
    } on Exception {
      return CategoryConstants.defaultSortingOptions;
    }
  }

  // Helper methods to replace RuTrackerService functionality
  Map<String, dynamic> _parsePaginationInfo(String html) {
    try {
      String decodedHtml;
      try {
        decodedHtml = utf8.decode(html.codeUnits);
      } on FormatException {
        decodedHtml = windows1251.decode(html.codeUnits);
      }

      final document = parser.parse(decodedHtml);
      final paginationInfo = <String, dynamic>{
        'currentPage': 1,
        'totalPages': 1,
        'hasNext': false,
        'hasPrevious': false,
      };

      // Parse pagination links
      final pageLinks = document.querySelectorAll('a.pg');
      if (pageLinks.isNotEmpty) {
        final pageNumbers = pageLinks
            .map((link) => _extractPageNumber(link.attributes['href'] ?? ''))
            .where((page) => page != null)
            .cast<int>()
            .toList();

        if (pageNumbers.isNotEmpty) {
          paginationInfo['totalPages'] =
              pageNumbers.reduce((a, b) => a > b ? a : b);
        }

        // Check for next/previous links
        final nextLink = document.querySelector('a.pg[href*="start="]');
        paginationInfo['hasNext'] = nextLink != null;
      }

      // Parse current page from URL or active page indicator
      final currentPageMatch = RegExp(r'start=(\d+)')
          .firstMatch(document.documentElement?.outerHtml ?? '');
      if (currentPageMatch != null) {
        final start = int.parse(currentPageMatch.group(1)!);
        paginationInfo['currentPage'] =
            (start ~/ 50) + 1; // Default perPage is 50
      }

      return paginationInfo;
    } on Exception {
      return {
        'currentPage': 1,
        'totalPages': 1,
        'hasNext': false,
        'hasPrevious': false
      };
    }
  }

  List<Map<String, String>> _parseSortingOptions(String html) {
    final options = <Map<String, String>>[];
    try {
      String decodedHtml;
      try {
        decodedHtml = utf8.decode(html.codeUnits);
      } on FormatException {
        decodedHtml = windows1251.decode(html.codeUnits);
      }

      final document = parser.parse(decodedHtml);
      final sortSelect = document.querySelector('select#sort');

      if (sortSelect != null) {
        final optionsElements = sortSelect.querySelectorAll('option');
        for (final option in optionsElements) {
          options.add({
            'value': option.attributes['value'] ?? '',
            'label': option.text.trim(),
          });
        }
      }
    } on Exception {
      // Fallback to default options
    }

    if (options.isEmpty) {
      options.addAll([
        {'value': '0', 'label': 'Посл. сообщение'},
        {'value': '1', 'label': 'Название темы'},
        {'value': '2', 'label': 'Время размещения'},
      ]);
    }

    return options;
  }

  int? _extractPageNumber(String url) {
    final match = RegExp(r'start=(\d+)').firstMatch(url);
    if (match != null) {
      final start = int.parse(match.group(1)!);
      return (start ~/ 50) + 1; // Assuming 50 items per page
    }
    return null;
  }

  // Helper methods
  String _extractCategoryFromForumId(String forumId) =>
      CategoryConstants.categoryNameMap[forumId] ??
      CategoryConstants.defaultCategoryName;

  String _buildMagnetUrl(String topicId) =>
      CategoryConstants.magnetUrlTemplate.replaceFirst('\$topicId', topicId);

  @override
  Future<List<Audiobook>> getFeaturedAudiobooks() async {
    // Get audiobooks from popular categories
    const popularCategories = CategoryConstants.popularCategoryIds;
    final featuredAudiobooks = <Audiobook>[];

    for (final categoryId in popularCategories) {
      try {
        final audiobooks = await getCategoryAudiobooks(categoryId);
        featuredAudiobooks
            .addAll(audiobooks.take(5)); // Take top 5 from each category
      } on Exception {
        continue;
      }
    }

    return featuredAudiobooks;
  }

  @override
  Future<List<Audiobook>> getNewReleases() async {
    try {
      final dio = await DioClient.instance;

      // Get latest audiobooks from all categories
      const trackerPath =
          '/forum/tracker.php?f=${CategoryConstants.audiobooksCategoryId}&${CategoryConstants.searchSortNewest}';
      final trackerUrl = await _endpointManager.buildUrl(trackerPath);

      final response = await dio.get(
        trackerUrl,
        options: Options(
          responseType: ResponseType.plain,
          headers: {
            'Accept': 'text/html,application/xhtml+xml,application/xml',
            'Accept-Charset': 'windows-1251,utf-8',
          },
        ),
      );

      final results = await _parser.parseSearchResults(response.data);
      return results
          .map((audiobook) => Audiobook(
                id: audiobook.id,
                title: audiobook.title,
                author: audiobook.author,
                category: audiobook.category,
                size: audiobook.size,
                seeders: audiobook.seeders,
                leechers: audiobook.leechers,
                magnetUrl: audiobook.magnetUrl,
                coverUrl: audiobook.coverUrl,
                chapters: audiobook.chapters
                    .map((chapter) => Chapter(
                          title: chapter.title,
                          durationMs: chapter.durationMs,
                          fileIndex: chapter.fileIndex,
                          startByte: chapter.startByte,
                          endByte: chapter.endByte,
                        ))
                    .toList(),
                addedDate: audiobook.addedDate,
                duration: audiobook.duration,
                bitrate: audiobook.bitrate,
                audioCodec: audiobook.audioCodec,
              ))
          .toList();
    } on DioException catch (e) {
      throw NetworkFailure('Failed to fetch new releases: ${e.message}');
    } on Exception {
      return [];
    }
  }
}
