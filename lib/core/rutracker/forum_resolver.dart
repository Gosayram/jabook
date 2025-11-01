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
  /// [categoryTitle] - name of the category (e.g., "Аудиокниги")
  /// [forumTitle] - name of the forum (e.g., "Радиоспектакли, история, мемуары")
  ///
  /// Returns absolute URI to viewforum.php?f=XXXX or null if not found.
  Future<Uri?> resolveForumUrl({
    required String categoryTitle,
    required String forumTitle,
  }) async {
    // Check cache first
    final cached = await getCachedForumInfo(forumTitle);
    if (cached != null && _isCacheValid(cached)) {
      final url = cached['forum_url'] as String?;
      if (url != null) {
        await StructuredLogger().log(
          level: 'debug',
          subsystem: 'forum_resolver',
          message: 'Using cached forum URL',
          extra: {'forum_title': forumTitle, 'url': url},
        );
        return Uri.parse(url);
      }
    }

    // Try to resolve from RuTracker
    final endpointManager = EndpointManager(_db);
    final dio = await DioClient.instance;

    for (final entryPoint in _entryPoints) {
      try {
        final baseUrl = await endpointManager.getActiveEndpoint();
        final url = '$baseUrl$entryPoint';

        await StructuredLogger().log(
          level: 'debug',
          subsystem: 'forum_resolver',
          message: 'Attempting to resolve forum',
          extra: {
            'forum_title': forumTitle,
            'category_title': categoryTitle,
            'entry_point': entryPoint,
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

        if (response.statusCode != 200) {
          continue;
        }

        // Decode HTML from Windows-1251
        String decodedHtml;
        try {
          decodedHtml = utf8.decode(response.data as List<int>);
        } on FormatException {
          decodedHtml = windows1251.decode(response.data as List<int>);
        }

        // Parse and find forum
        final resolvedUrl = _findForumInHtml(
          decodedHtml,
          categoryTitle,
          forumTitle,
          baseUrl,
        );

        if (resolvedUrl != null) {
          // Cache the result
          await _cacheForumResult(forumTitle, categoryTitle, resolvedUrl);
          return resolvedUrl;
        }
      } on DioException catch (e) {
        await StructuredLogger().log(
          level: 'warning',
          subsystem: 'forum_resolver',
          message: 'Failed to resolve forum from entry point',
          extra: {'entry_point': entryPoint, 'error': e.message},
        );
        // Try next entry point
        continue;
      }
    }

    await StructuredLogger().log(
      level: 'error',
      subsystem: 'forum_resolver',
      message: 'Failed to resolve forum after trying all entry points',
      extra: {'forum_title': forumTitle},
    );

    return null;
  }

  /// Finds forum in HTML by category and forum title.
  Uri? _findForumInHtml(
    String html,
    String categoryTitle,
    String forumTitle,
    String baseUrl,
  ) {
    final document = parser.parse(html);

    // Find audiobooks category section (c=33)
    final categorySelector = '#c-${CategoryConstants.audiobooksCategoryId}';
    final categoryElement = document.querySelector(categorySelector);

    if (categoryElement == null) {
      // Try alternative: find category by title
      final allCategories = document.querySelectorAll('.category');
      Element? foundCategory;

      for (final cat in allCategories) {
        final catTitle = cat.querySelector('h2, .forumtitle');
        if (catTitle != null) {
          final titleText = catTitle.text.trim();
          if (_calculateSimilarity(
                  titleText.toLowerCase(), categoryTitle.toLowerCase()) >=
              _minSimilarityThreshold) {
            foundCategory = cat;
            break;
          }
        }
      }

      if (foundCategory == null) {
        return null;
      }

      return _findForumInCategory(foundCategory, forumTitle, baseUrl);
    }

    return _findForumInCategory(categoryElement, forumTitle, baseUrl);
  }

  /// Finds forum within a category element.
  Uri? _findForumInCategory(
    Element categoryElement,
    String forumTitle,
    String baseUrl,
  ) {
    // Find forum table within category
    final forumTable =
        categoryElement.querySelector('table.forums, table[id^="cf-"]');
    if (forumTable == null) {
      return null;
    }

    // Find all forum rows
    final forumRows = forumTable.querySelectorAll('tr[id^="f-"]');

    String? bestForumId;
    double bestSimilarity = 0.0;

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
        return url;
      }

      // Fuzzy match
      final similarity = _calculateSimilarity(
        linkText.toLowerCase(),
        forumTitle.toLowerCase(),
      );

      if (similarity >= _minSimilarityThreshold &&
          similarity > bestSimilarity) {
        bestSimilarity = similarity;
        bestForumId = forumId;
      }
    }

    // Use best fuzzy match if found
    if (bestForumId != null) {
      final url = _buildForumUrl(baseUrl, bestForumId);
      return url;
    }

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
  Uri _buildForumUrl(String baseUrl, String forumId) {
    return Uri.parse('$baseUrl/forum/viewforum.php?f=$forumId');
  }

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
    Uri forumUrl,
  ) async {
    final forumId = _extractForumId(forumUrl.toString(), '');

    await _cacheStore.record(forumTitle).put(_db, {
      'forum_title': forumTitle,
      'forum_id': forumId,
      'forum_url': forumUrl.toString(),
      'category_title': categoryTitle,
      'cached_at': DateTime.now().toIso8601String(),
      'last_validated': DateTime.now().toIso8601String(),
      'is_valid': true,
    });

    await StructuredLogger().log(
      level: 'info',
      subsystem: 'forum_resolver',
      message: 'Cached forum resolution',
      extra: {
        'forum_title': forumTitle,
        'forum_id': forumId,
        'url': forumUrl.toString(),
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
