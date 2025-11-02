import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:html/dom.dart';
import 'package:html/parser.dart' as parser;
import 'package:jabook/core/constants/category_constants.dart';
import 'package:jabook/core/endpoints/endpoint_manager.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:sembast/sembast.dart';
import 'package:windows1251/windows1251.dart';

/// Dynamic resolver for RuTracker forum categories.
///
/// This class provides methods to resolve forum URLs by textual labels
/// instead of hardcoded forum IDs, making the system resilient to
/// RuTracker structure changes.
class ForumResolver {
  /// Creates a new instance of ForumResolver.
  ///
  /// The [db] parameter is the database instance for caching resolved forums.
  ForumResolver(this._db);

  /// Database instance for caching.
  final Database _db;

  /// Store reference for forum resolver cache.
  final StoreRef<String, Map<String, dynamic>> _cacheStore =
      StoreRef('forum_resolver_cache');

  /// Minimum Jaccard similarity threshold for fuzzy matching (0.7).
  static const double _minSimilarityThreshold = 0.7;

  /// Cache validity period in days (30 days).
  static const int _cacheValidityDays = 30;

  /// Stable entry points for RuTracker index pages.
  static const List<String> _entryPoints = [
    '/forum/index.php?c=${CategoryConstants.audiobooksCategoryId}',
    '/forum/index.php',
    '/forum/index.php?map',
  ];

  /// Resolves forum URL by category and forum title.
  ///
  /// [categoryTitle] - name of the category (e.g., "Audiobooks")
  /// [forumTitle] - name of the forum (e.g., "Radio plays, history, memoirs")
  ///
  /// Returns absolute URI to viewforum.php?f=XXXX or null if not found.
  Future<Uri?> resolveForumUrl({
    required String categoryTitle,
    required String forumTitle,
  }) async {
    final operationId =
        'forum_resolve_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    await logger.log(
      level: 'info',
      subsystem: 'forum_resolver',
      message: 'Forum resolution started',
      operationId: operationId,
      context: 'forum_resolution',
      extra: {
        'category_title': categoryTitle,
        'forum_title': forumTitle,
      },
    );

    // Check cache first
    final cacheCheckStartTime = DateTime.now();
    final cached = await getCachedForumInfo(forumTitle);
    final cacheCheckDuration =
        DateTime.now().difference(cacheCheckStartTime).inMilliseconds;

    if (cached != null && _isCacheValid(cached)) {
      final url = cached['forum_url'] as String?;
      final cachedAtStr = cached['cached_at'] as String?;
      DateTime? cachedAt;
      if (cachedAtStr != null) {
        cachedAt = DateTime.tryParse(cachedAtStr);
      }
      final age =
          cachedAt != null ? DateTime.now().difference(cachedAt).inDays : null;

      if (url != null) {
        await logger.log(
          level: 'info',
          subsystem: 'cache',
          message: 'Using cached forum URL',
          operationId: operationId,
          context: 'forum_resolution',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
          extra: {
            'forum_title': forumTitle,
            'url': url,
            'forum_id': cached['forum_id'],
            'cache_age_days': age,
            'cache_check_duration_ms': cacheCheckDuration,
            'cache_used': true,
            'cache_validity_days': _cacheValidityDays,
            'original_subsystem': 'forum_resolver',
          },
        );
        return Uri.parse(url);
      }
    }

    await logger.log(
      level: 'debug',
      subsystem: 'forum_resolver',
      message: 'Cache check completed',
      operationId: operationId,
      context: 'forum_resolution',
      durationMs: cacheCheckDuration,
      extra: {
        'cache_used': false,
        'cache_found': cached != null,
        'cache_valid': cached != null ? _isCacheValid(cached) : false,
      },
    );

    // Try to resolve from RuTracker
    final endpointManager = EndpointManager(_db);
    final dio = await DioClient.instance;
    final baseUrl = await endpointManager.getActiveEndpoint();

    final entryPointResults = <Map<String, dynamic>>[];

    for (var i = 0; i < _entryPoints.length; i++) {
      final entryPoint = _entryPoints[i];
      final entryPointStartTime = DateTime.now();

      try {
        final url = '$baseUrl$entryPoint';

        await logger.log(
          level: 'debug',
          subsystem: 'forum_resolver',
          message: 'Attempting to resolve forum from entry point',
          operationId: operationId,
          context: 'forum_resolution',
          extra: {
            'forum_title': forumTitle,
            'category_title': categoryTitle,
            'entry_point': entryPoint,
            'entry_point_index': i + 1,
            'total_entry_points': _entryPoints.length,
            'url': url,
          },
        );

        final response = await dio.get(
          url,
          options: Options(
            responseType: ResponseType.bytes,
            headers: {
              'Accept': 'text/html,application/xhtml+xml,application/xml',
              'Accept-Charset': 'windows-1251,utf-8',
            },
          ),
        );

        final requestDuration =
            DateTime.now().difference(entryPointStartTime).inMilliseconds;
        final responseSize = response.data is List<int>
            ? (response.data as List<int>).length
            : 0;

        await logger.log(
          level: 'debug',
          subsystem: 'forum_resolver',
          message: 'Entry point response received',
          operationId: operationId,
          context: 'forum_resolution',
          durationMs: requestDuration,
          extra: {
            'entry_point': entryPoint,
            'entry_point_index': i + 1,
            'status_code': response.statusCode,
            'response_size_bytes': responseSize,
          },
        );

        if (response.statusCode != 200) {
          entryPointResults.add({
            'entry_point': entryPoint,
            'success': false,
            'reason': 'invalid_status_code',
            'status_code': response.statusCode,
            'duration_ms': requestDuration,
          });
          continue;
        }

        // Decode HTML from Windows-1251
        final decodeStartTime = DateTime.now();
        String decodedHtml;
        String? encoding;
        try {
          decodedHtml = utf8.decode(response.data as List<int>);
          encoding = 'utf8';
        } on FormatException {
          decodedHtml = windows1251.decode(response.data as List<int>);
          encoding = 'windows1251';
        }
        final decodeDuration =
            DateTime.now().difference(decodeStartTime).inMilliseconds;

        await logger.log(
          level: 'debug',
          subsystem: 'forum_resolver',
          message: 'HTML decoded',
          operationId: operationId,
          context: 'forum_resolution',
          durationMs: decodeDuration,
          extra: {
            'entry_point': entryPoint,
            'encoding': encoding,
            'html_size': decodedHtml.length,
          },
        );

        // Parse HTML first to get forum count
        final htmlParseStartTime = DateTime.now();
        final document = parser.parse(decodedHtml);
        final htmlParseDuration =
            DateTime.now().difference(htmlParseStartTime).inMilliseconds;

        // Count forums found in HTML for logging
        final forumTables =
            document.querySelectorAll('table.forums, table[id^="cf-"]');
        final totalForumsInHtml = forumTables
            .expand((table) => table.querySelectorAll('tr[id^="f-"]'))
            .length;

        await logger.log(
          level: 'debug',
          subsystem: 'forum_resolver',
          message: 'HTML parsed - searching for forum',
          operationId: operationId,
          context: 'forum_resolution',
          durationMs: htmlParseDuration,
          extra: {
            'entry_point': entryPoint,
            'html_size': decodedHtml.length,
            'forums_found_in_html': totalForumsInHtml,
            'forum_tables_count': forumTables.length,
          },
        );

        // Parse and find forum
        final parseStartTime = DateTime.now();
        final resolvedUrl = await _findForumInHtml(
          decodedHtml,
          categoryTitle,
          forumTitle,
          baseUrl,
          operationId,
        );
        final parseDuration =
            DateTime.now().difference(parseStartTime).inMilliseconds;

        final entryPointDuration =
            DateTime.now().difference(entryPointStartTime).inMilliseconds;

        if (resolvedUrl != null) {
          await logger.log(
            level: 'info',
            subsystem: 'forum_resolver',
            message: 'Forum resolved successfully from entry point',
            operationId: operationId,
            context: 'forum_resolution',
            durationMs: entryPointDuration,
            extra: {
              'entry_point': entryPoint,
              'entry_point_index': i + 1,
              'resolved_url': resolvedUrl.toString(),
              'forum_title': forumTitle,
              'category_title': categoryTitle,
              'steps': {
                'request_ms': requestDuration,
                'decode_ms': decodeDuration,
                'parse_ms': parseDuration,
              },
            },
          );

          // Cache the result
          await _cacheForumResult(
              forumTitle, categoryTitle, resolvedUrl, operationId);

          final totalDuration =
              DateTime.now().difference(startTime).inMilliseconds;
          await logger.log(
            level: 'info',
            subsystem: 'forum_resolver',
            message: 'Forum resolution completed',
            operationId: operationId,
            context: 'forum_resolution',
            durationMs: totalDuration,
            extra: {
              'forum_title': forumTitle,
              'category_title': categoryTitle,
              'resolved_url': resolvedUrl.toString(),
              'entry_points_tried': i + 1,
              'successful_entry_point': entryPoint,
              'entry_point_results': entryPointResults,
            },
          );

          return resolvedUrl;
        } else {
          entryPointResults.add({
            'entry_point': entryPoint,
            'success': false,
            'reason': 'forum_not_found_in_html',
            'duration_ms': entryPointDuration,
            'steps': {
              'request_ms': requestDuration,
              'decode_ms': decodeDuration,
              'parse_ms': parseDuration,
            },
          });
        }
      } on DioException catch (e) {
        final entryPointDuration =
            DateTime.now().difference(entryPointStartTime).inMilliseconds;
        final errorType = switch (e.type) {
          DioExceptionType.connectionTimeout => 'Connection timeout',
          DioExceptionType.receiveTimeout => 'Receive timeout',
          DioExceptionType.connectionError => 'Connection error',
          DioExceptionType.badResponse => 'Bad response',
          _ => 'Unknown error',
        };

        await logger.log(
          level: 'warning',
          subsystem: 'forum_resolver',
          message: 'Failed to resolve forum from entry point',
          operationId: operationId,
          context: 'forum_resolution',
          durationMs: entryPointDuration,
          cause: e.toString(),
          extra: {
            'entry_point': entryPoint,
            'entry_point_index': i + 1,
            'error_type': errorType,
            'status_code': e.response?.statusCode,
            'stack_trace': e.stackTrace.toString(),
          },
        );

        entryPointResults.add({
          'entry_point': entryPoint,
          'success': false,
          'reason': 'dio_exception',
          'error_type': errorType,
          'duration_ms': entryPointDuration,
        });
        // Try next entry point
        continue;
      } on Exception catch (e) {
        final entryPointDuration =
            DateTime.now().difference(entryPointStartTime).inMilliseconds;

        await logger.log(
          level: 'warning',
          subsystem: 'forum_resolver',
          message: 'Exception during forum resolution from entry point',
          operationId: operationId,
          context: 'forum_resolution',
          durationMs: entryPointDuration,
          cause: e.toString(),
          extra: {
            'entry_point': entryPoint,
            'entry_point_index': i + 1,
            'stack_trace':
                (e is Error) ? (e as Error).stackTrace.toString() : null,
          },
        );

        entryPointResults.add({
          'entry_point': entryPoint,
          'success': false,
          'reason': 'exception',
          'duration_ms': entryPointDuration,
        });
        continue;
      }
    }

    final totalDuration = DateTime.now().difference(startTime).inMilliseconds;
    await logger.log(
      level: 'error',
      subsystem: 'forum_resolver',
      message: 'Failed to resolve forum after trying all entry points',
      operationId: operationId,
      context: 'forum_resolution',
      durationMs: totalDuration,
      extra: {
        'forum_title': forumTitle,
        'category_title': categoryTitle,
        'entry_points_tried': _entryPoints.length,
        'entry_point_results': entryPointResults,
      },
    );

    return null;
  }

  /// Finds forum in HTML by category and forum title.
  Future<Uri?> _findForumInHtml(
    String html,
    String categoryTitle,
    String forumTitle,
    String baseUrl,
    String operationId,
  ) async {
    final logger = StructuredLogger();
    final parseStartTime = DateTime.now();

    await logger.log(
      level: 'debug',
      subsystem: 'forum_resolver',
      message: 'Parsing HTML to find forum',
      operationId: operationId,
      context: 'forum_resolution',
      extra: {
        'html_size': html.length,
        'category_title': categoryTitle,
        'forum_title': forumTitle,
      },
    );

    final document = parser.parse(html);

    // Find audiobooks category section (c=33)
    // ignore: prefer_const_declarations
    final categorySelector = '#c-${CategoryConstants.audiobooksCategoryId}';
    final categoryElement = document.querySelector(categorySelector);

    if (categoryElement == null) {
      // Try alternative: find category by title
      final allCategories = document.querySelectorAll('.category');

      await logger.log(
        level: 'debug',
        subsystem: 'forum_resolver',
        message: 'Category not found by ID, searching by title',
        operationId: operationId,
        context: 'forum_resolution',
        extra: {
          'categories_found': allCategories.length,
          'category_selector': categorySelector,
        },
      );

      Element? foundCategory;
      final categoryMatches = <Map<String, dynamic>>[];

      for (final cat in allCategories) {
        final catTitle = cat.querySelector('h2, .forumtitle');
        if (catTitle != null) {
          final titleText = catTitle.text.trim();
          final similarity = _calculateSimilarity(
            titleText.toLowerCase(),
            categoryTitle.toLowerCase(),
          );

          categoryMatches.add({
            'title': titleText,
            'similarity': similarity,
          });

          if (similarity >= _minSimilarityThreshold) {
            foundCategory = cat;
            break;
          }
        }
      }

      await logger.log(
        level: 'debug',
        subsystem: 'forum_resolver',
        message: 'Category search by title completed',
        operationId: operationId,
        context: 'forum_resolution',
        extra: {
          'category_found': foundCategory != null,
          'category_matches': categoryMatches,
        },
      );

      if (foundCategory == null) {
        await logger.log(
          level: 'debug',
          subsystem: 'forum_resolver',
          message: 'Category not found in HTML',
          operationId: operationId,
          context: 'forum_resolution',
          durationMs: DateTime.now().difference(parseStartTime).inMilliseconds,
        );
        return null;
      }

      return _findForumInCategory(
        foundCategory,
        forumTitle,
        baseUrl,
        operationId,
      );
    }

    await logger.log(
      level: 'debug',
      subsystem: 'forum_resolver',
      message: 'Category found by ID',
      operationId: operationId,
      context: 'forum_resolution',
      extra: {
        'category_selector': categorySelector,
      },
    );

    return _findForumInCategory(
      categoryElement,
      forumTitle,
      baseUrl,
      operationId,
    );
  }

  /// Finds forum within a category element.
  Future<Uri?> _findForumInCategory(
    Element categoryElement,
    String forumTitle,
    String baseUrl,
    String operationId,
  ) async {
    final logger = StructuredLogger();
    final searchStartTime = DateTime.now();

    await logger.log(
      level: 'debug',
      subsystem: 'forum_resolver',
      message: 'Searching for forum in category',
      operationId: operationId,
      context: 'forum_resolution',
      extra: {
        'forum_title': forumTitle,
      },
    );

    // Find forum table within category
    final forumTable =
        categoryElement.querySelector('table.forums, table[id^="cf-"]');
    if (forumTable == null) {
      await logger.log(
        level: 'debug',
        subsystem: 'forum_resolver',
        message: 'Forum table not found in category',
        operationId: operationId,
        context: 'forum_resolution',
        durationMs: DateTime.now().difference(searchStartTime).inMilliseconds,
      );
      return null;
    }

    // Find all forum rows
    final forumRows = forumTable.querySelectorAll('tr[id^="f-"]');

    await logger.log(
      level: 'debug',
      subsystem: 'forum_resolver',
      message: 'Found forum rows in category',
      operationId: operationId,
      context: 'forum_resolution',
      extra: {
        'forum_rows_count': forumRows.length,
      },
    );

    String? bestForumId;
    var bestSimilarity = 0.0;
    final matches = <Map<String, dynamic>>[];
    bool? exactMatchFound;

    for (final row in forumRows) {
      final forumLink = row.querySelector('h4.forumlink a, .forumlink a');
      if (forumLink == null) continue;

      final linkText = forumLink.text.trim();
      final href = forumLink.attributes['href'] ?? '';

      // Extract forum ID from href or row ID
      final forumId = _extractForumId(href, row.id);
      if (forumId == null) continue;

      // Exact match
      if (linkText == forumTitle) {
        final url = _buildForumUrl(baseUrl, forumId);

        await logger.log(
          level: 'info',
          subsystem: 'forum_resolver',
          message: 'Forum found: exact match',
          operationId: operationId,
          context: 'forum_resolution',
          durationMs: DateTime.now().difference(searchStartTime).inMilliseconds,
          extra: {
            'forum_title': forumTitle,
            'found_forum_title': linkText,
            'forum_id': forumId,
            'url': url.toString(),
            'match_type': 'exact',
            'similarity': 1.0,
            'forums_searched': forumRows.length,
          },
        );

        exactMatchFound = true;
        return url;
      }

      // Fuzzy match
      final similarity = _calculateSimilarity(
        linkText.toLowerCase(),
        forumTitle.toLowerCase(),
      );

      matches.add({
        'forum_title': linkText,
        'forum_id': forumId,
        'similarity': similarity,
        'href': href,
      });

      if (similarity >= _minSimilarityThreshold &&
          similarity > bestSimilarity) {
        bestSimilarity = similarity;
        bestForumId = forumId;
      }
    }

    await logger.log(
      level: 'debug',
      subsystem: 'forum_resolver',
      message: 'Forum search in category completed',
      operationId: operationId,
      context: 'forum_resolution',
      durationMs: DateTime.now().difference(searchStartTime).inMilliseconds,
      extra: {
        'forums_searched': forumRows.length,
        'exact_match_found': exactMatchFound ?? false,
        'best_match_similarity': bestSimilarity,
        'best_match_forum_id': bestForumId,
        'matches_count': matches.length,
        'top_matches': matches
            .where(
                (m) => (m['similarity'] as double) >= _minSimilarityThreshold)
            .toList()
          ..sort((a, b) =>
              (b['similarity'] as double).compareTo(a['similarity'] as double))
          ..take(5)
          ..toList(),
      },
    );

    // Use best fuzzy match if found
    if (bestForumId != null) {
      final bestMatch = matches.firstWhere(
        (m) => m['forum_id'] == bestForumId,
      );

      final url = _buildForumUrl(baseUrl, bestForumId);

      await logger.log(
        level: 'info',
        subsystem: 'forum_resolver',
        message: 'Forum found: fuzzy match',
        operationId: operationId,
        context: 'forum_resolution',
        durationMs: DateTime.now().difference(searchStartTime).inMilliseconds,
        extra: {
          'forum_title': forumTitle,
          'found_forum_title': bestMatch['forum_title'],
          'forum_id': bestForumId,
          'url': url.toString(),
          'match_type': 'fuzzy',
          'similarity': bestSimilarity,
          'similarity_threshold': _minSimilarityThreshold,
          'forums_searched': forumRows.length,
        },
      );

      return url;
    }

    await logger.log(
      level: 'debug',
      subsystem: 'forum_resolver',
      message: 'Forum not found in category',
      operationId: operationId,
      context: 'forum_resolution',
      durationMs: DateTime.now().difference(searchStartTime).inMilliseconds,
      extra: {
        'forums_searched': forumRows.length,
        'matches_found': matches.length,
        'best_similarity': matches.isNotEmpty
            ? matches
                .map((m) => m['similarity'] as double)
                .reduce((a, b) => a > b ? a : b)
            : 0.0,
      },
    );

    return null;
  }

  /// Extracts forum ID from URL or element ID.
  String? _extractForumId(String href, String elementId) {
    // Try to extract from href (viewforum.php?f=2326)
    final hrefMatch = RegExp(r'f=(\d+)').firstMatch(href);
    if (hrefMatch != null) {
      return hrefMatch.group(1);
    }

    // Try to extract from element ID (f-2326)
    final idMatch = RegExp(r'f-(\d+)').firstMatch(elementId);
    if (idMatch != null) {
      return idMatch.group(1);
    }

    return null;
  }

  /// Builds forum URL from base URL and forum ID.
  Uri _buildForumUrl(String baseUrl, String forumId) =>
      Uri.parse('$baseUrl/forum/viewforum.php?f=$forumId');

  /// Verifies that the forum page matches expected title.
  Future<bool> _verifyForumPage(Uri url, String expectedTitle) async {
    try {
      final dio = await DioClient.instance;
      final response = await dio.get(
        url.toString(),
        options: Options(
          responseType: ResponseType.bytes,
          headers: {
            'Accept': 'text/html,application/xhtml+xml,application/xml',
            'Accept-Charset': 'windows-1251,utf-8',
          },
        ),
      );

      if (response.statusCode != 200) {
        return false;
      }

      // Decode HTML
      String decodedHtml;
      try {
        decodedHtml = utf8.decode(response.data as List<int>);
      } on FormatException {
        decodedHtml = windows1251.decode(response.data as List<int>);
      }

      // Check page title
      final document = parser.parse(decodedHtml);
      final titleElement =
          document.querySelector('h1.maintitle, h2, .maintitle');
      if (titleElement != null) {
        final pageTitle = titleElement.text.trim();
        final similarity = _calculateSimilarity(
          pageTitle.toLowerCase(),
          expectedTitle.toLowerCase(),
        );

        // Accept if similarity >= threshold or exact match
        return similarity >= _minSimilarityThreshold ||
            pageTitle == expectedTitle;
      }

      // Fallback: check breadcrumbs or page structure
      return true; // Accept if page loaded successfully
    } on Exception {
      return false;
    }
  }

  /// Calculates Jaccard similarity between two strings.
  ///
  /// Jaccard similarity = intersection / union of character sets
  double _calculateSimilarity(String s1, String s2) {
    if (s1 == s2) return 1.0;
    if (s1.isEmpty || s2.isEmpty) return 0.0;

    // Create character sets (using words for better matching)
    final words1 = s1.split(RegExp(r'\s+')).toSet();
    final words2 = s2.split(RegExp(r'\s+')).toSet();

    final intersection = words1.intersection(words2).length;
    final union = words1.union(words2).length;

    if (union == 0) return 0.0;

    return intersection / union;
  }

  /// Gets cached forum ID by forum title.
  ///
  /// Returns forum ID (e.g., "2326") or null if not found in cache.
  Future<String?> getCachedForumId(String forumTitle) async {
    final cached = await getCachedForumInfo(forumTitle);
    return cached?['forum_id'] as String?;
  }

  /// Gets full cached forum information.
  ///
  /// Returns Map with 'forum_id', 'forum_url', 'forum_name', 'cached_at'
  /// or null if not found in cache or cache is invalid.
  Future<Map<String, dynamic>?> getCachedForumInfo(String forumTitle) async {
    final record = await _cacheStore.record(forumTitle).get(_db);
    if (record == null) return null;

    if (!_isCacheValid(record)) {
      return null;
    }

    return record;
  }

  /// Checks if cache entry is valid.
  bool _isCacheValid(Map<String, dynamic> cached) {
    final cachedAtStr = cached['cached_at'] as String?;
    if (cachedAtStr == null) return false;

    try {
      final cachedAt = DateTime.parse(cachedAtStr);
      final now = DateTime.now();
      final daysSinceCache = now.difference(cachedAt).inDays;

      return daysSinceCache < _cacheValidityDays;
    } on Exception {
      return false;
    }
  }

  /// Caches forum resolution result.
  Future<void> _cacheForumResult(
    String forumTitle,
    String categoryTitle,
    Uri forumUrl, [
    String? operationId,
  ]) async {
    final logger = StructuredLogger();
    final cacheStartTime = DateTime.now();

    final forumId = _extractForumId(forumUrl.toString(), '');

    await logger.log(
      level: 'debug',
      subsystem: 'forum_resolver',
      message: 'Caching forum resolution result',
      operationId: operationId,
      context: 'forum_resolution',
      extra: {
        'forum_title': forumTitle,
        'forum_id': forumId,
        'url': forumUrl.toString(),
        'category_title': categoryTitle,
      },
    );

    await _cacheStore.record(forumTitle).put(_db, {
      'forum_title': forumTitle,
      'forum_id': forumId,
      'forum_url': forumUrl.toString(),
      'category_title': categoryTitle,
      'cached_at': DateTime.now().toIso8601String(),
      'last_validated': DateTime.now().toIso8601String(),
      'is_valid': true,
    });

    final cacheDuration =
        DateTime.now().difference(cacheStartTime).inMilliseconds;
    await logger.log(
      level: 'info',
      subsystem: 'forum_resolver',
      message: 'Cached forum resolution',
      operationId: operationId,
      context: 'forum_resolution',
      durationMs: cacheDuration,
      extra: {
        'forum_title': forumTitle,
        'forum_id': forumId,
        'url': forumUrl.toString(),
        'category_title': categoryTitle,
      },
    );
  }

  /// Invalidates cache for a specific forum (forces re-resolution).
  Future<void> invalidateCache(String forumTitle) async {
    await _cacheStore.record(forumTitle).delete(_db);
    await StructuredLogger().log(
      level: 'info',
      subsystem: 'forum_resolver',
      message: 'Invalidated cache for forum',
      extra: {'forum_title': forumTitle},
    );
  }

  /// Invalidates entire forum cache.
  Future<void> invalidateAllCache() async {
    await _cacheStore.delete(_db);
    await StructuredLogger().log(
      level: 'info',
      subsystem: 'forum_resolver',
      message: 'Invalidated all forum cache',
    );
  }

  /// Validates cached forum by checking if the forum page is accessible.
  ///
  /// Returns true if cached forum ID is valid, false otherwise.
  Future<bool> validateCachedForum(String forumTitle) async {
    final cached = await getCachedForumInfo(forumTitle);
    if (cached == null) return false;

    final urlStr = cached['forum_url'] as String?;
    if (urlStr == null) return false;

    try {
      final url = Uri.parse(urlStr);
      final isValid = await _verifyForumPage(url, forumTitle);

      // Update cache validation status
      if (cached['forum_title'] == forumTitle) {
        await _cacheStore.record(forumTitle).update(_db, {
          'last_validated': DateTime.now().toIso8601String(),
          'is_valid': isValid,
        });
      }

      return isValid;
    } on Exception {
      // Update cache as invalid
      if (cached['forum_title'] == forumTitle) {
        await _cacheStore.record(forumTitle).update(_db, {
          'last_validated': DateTime.now().toIso8601String(),
          'is_valid': false,
        });
      }
      return false;
    }
  }
}
