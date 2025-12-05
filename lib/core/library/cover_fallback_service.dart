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

import 'dart:io';

import 'package:dio/dio.dart';
import 'package:html/parser.dart' as html_parser;
import 'package:jabook/core/data/local/database/app_database.dart';
import 'package:jabook/core/data/remote/network/dio_client.dart';
import 'package:jabook/core/data/remote/network/user_agent_manager.dart';
import 'package:jabook/core/data/remote/rutracker/rutracker_parser.dart';
import 'package:jabook/core/infrastructure/endpoints/endpoint_manager.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';

/// Service for fetching cover images from online sources as fallback
/// when local covers are not available.
///
/// This service searches for covers on multiple platforms in priority order:
/// 1. Author.today (highest priority)
/// 2. Audio-kniga.com
/// 3. RuTracker topic page (lowest priority, requires torrentId)
class CoverFallbackService {
  /// Creates a new CoverFallbackService instance.
  const CoverFallbackService();

  /// Searches for cover image online using group name and optional torrentId.
  ///
  /// [groupName] - Name of the audiobook group (e.g., "Атаманов Михаил - Котенок и его человек")
  /// [torrentId] - Optional RuTracker topic ID for extracting cover from topic page
  /// Returns path to cached cover image, or null if not found
  Future<String?> fetchCoverFromOnline(
    String groupName, {
    String? torrentId,
  }) async {
    final logger = StructuredLogger();
    final operationId =
        'cover_fallback_${DateTime.now().millisecondsSinceEpoch}';

    await logger.log(
      level: 'info',
      subsystem: 'cover_fallback',
      message: 'Attempting to fetch cover from online',
      operationId: operationId,
      context: 'fetch_cover',
      extra: {
        'group_name': groupName,
        'has_torrent_id': torrentId != null && torrentId.isNotEmpty,
      },
    );

    // 1. Try author.today first (priority)
    try {
      final authorTodayUrl = await _searchAuthorToday(groupName);
      if (authorTodayUrl != null) {
        await logger.log(
          level: 'info',
          subsystem: 'cover_fallback',
          message: 'Found cover on author.today',
          operationId: operationId,
          context: 'fetch_cover',
          extra: {
            'group_name': groupName,
            'source': 'author.today',
            'cover_url': authorTodayUrl,
          },
        );

        final cachedPath =
            await _downloadAndCacheCover(authorTodayUrl, groupName);
        if (cachedPath != null) {
          await logger.log(
            level: 'info',
            subsystem: 'cover_fallback',
            message: 'Successfully fetched and cached cover from author.today',
            operationId: operationId,
            context: 'fetch_cover',
            extra: {
              'group_name': groupName,
              'source': 'author.today',
              'cached_path': cachedPath,
            },
          );
          return cachedPath;
        }
      }
    } on Exception catch (e) {
      await logger.log(
        level: 'warning',
        subsystem: 'cover_fallback',
        message: 'Failed to fetch cover from author.today',
        operationId: operationId,
        context: 'fetch_cover',
        cause: e.toString(),
        extra: {
          'group_name': groupName,
        },
      );
    }

    // 2. Try audio-kniga.com as fallback
    try {
      final audioKnigaUrl = await _searchAudioKniga(groupName);
      if (audioKnigaUrl != null) {
        await logger.log(
          level: 'info',
          subsystem: 'cover_fallback',
          message: 'Found cover on audio-kniga.com',
          operationId: operationId,
          context: 'fetch_cover',
          extra: {
            'group_name': groupName,
            'source': 'audio-kniga.com',
            'cover_url': audioKnigaUrl,
          },
        );

        final cachedPath =
            await _downloadAndCacheCover(audioKnigaUrl, groupName);
        if (cachedPath != null) {
          await logger.log(
            level: 'info',
            subsystem: 'cover_fallback',
            message:
                'Successfully fetched and cached cover from audio-kniga.com',
            operationId: operationId,
            context: 'fetch_cover',
            extra: {
              'group_name': groupName,
              'source': 'audio-kniga.com',
              'cached_path': cachedPath,
            },
          );
          return cachedPath;
        }
      }
    } on Exception catch (e) {
      await logger.log(
        level: 'warning',
        subsystem: 'cover_fallback',
        message: 'Failed to fetch cover from audio-kniga.com',
        operationId: operationId,
        context: 'fetch_cover',
        cause: e.toString(),
        extra: {
          'group_name': groupName,
        },
      );
    }

    // 3. Try RuTracker topic page (lowest priority, only if torrentId available)
    if (torrentId != null && torrentId.isNotEmpty) {
      try {
        final rutrackerUrl = await _extractCoverFromRuTracker(torrentId);
        if (rutrackerUrl != null) {
          await logger.log(
            level: 'info',
            subsystem: 'cover_fallback',
            message: 'Found cover on RuTracker topic page',
            operationId: operationId,
            context: 'fetch_cover',
            extra: {
              'group_name': groupName,
              'source': 'rutracker',
              'torrent_id': torrentId,
              'cover_url': rutrackerUrl,
            },
          );

          final cachedPath =
              await _downloadAndCacheCover(rutrackerUrl, groupName);
          if (cachedPath != null) {
            await logger.log(
              level: 'info',
              subsystem: 'cover_fallback',
              message:
                  'Successfully fetched and cached cover from RuTracker topic page',
              operationId: operationId,
              context: 'fetch_cover',
              extra: {
                'group_name': groupName,
                'source': 'rutracker',
                'cached_path': cachedPath,
              },
            );
            return cachedPath;
          }
        }
      } on Exception catch (e) {
        await logger.log(
          level: 'warning',
          subsystem: 'cover_fallback',
          message: 'Failed to fetch cover from RuTracker topic page',
          operationId: operationId,
          context: 'fetch_cover',
          cause: e.toString(),
          extra: {
            'group_name': groupName,
            'torrent_id': torrentId,
          },
        );
      }
    }

    await logger.log(
      level: 'info',
      subsystem: 'cover_fallback',
      message: 'Failed to fetch cover from all online sources',
      operationId: operationId,
      context: 'fetch_cover',
      extra: {
        'group_name': groupName,
      },
    );

    return null;
  }

  /// Searches on author.today and extracts cover URL.
  ///
  /// Uses direct search on author.today to find book page, then extracts cover.
  /// Also tries to extract cover directly from search results if available.
  Future<String?> _searchAuthorToday(String query) async {
    try {
      // Step 1: Search directly on author.today
      final searchUrl =
          'https://author.today/search?q=${Uri.encodeComponent(query)}';
      final searchHtml = await _fetchHtml(searchUrl);
      if (searchHtml == null) return null;

      // Step 2: Try to extract cover directly from search results (faster)
      final coverFromSearch = _extractCoverUrlFromAuthorTodaySearch(searchHtml);
      if (coverFromSearch != null) {
        return coverFromSearch;
      }

      // Step 3: Extract book URL from search results
      final bookUrl = _extractBookUrlFromAuthorTodaySearch(searchHtml);
      if (bookUrl == null) return null;

      // Step 4: Navigate to book page and extract cover
      final bookPageHtml = await _fetchHtml(bookUrl);
      if (bookPageHtml == null) return null;

      return _extractCoverUrlFromBookPage(bookPageHtml, 'author.today');
    } on Exception {
      return null;
    }
  }

  /// Extracts cover URL directly from author.today search results.
  ///
  /// Looks for cover images in bookcard elements on search page.
  /// Only extracts from actual book cards in search results.
  String? _extractCoverUrlFromAuthorTodaySearch(String html) {
    try {
      final document = html_parser.parse(html);

      // Look for bookcard elements in search results (only in book-row/book-shelf sections)
      final bookcards = document.querySelectorAll(
          '.book-row .bookcard, .book-shelf .bookcard, .bookcard');
      for (final bookcard in bookcards) {
        // Verify this is a real book card by checking for a valid book link
        final hasValidLink = bookcard
            .querySelectorAll('a[href*="/work/"], a[href*="/audiobook/"]')
            .any((link) {
          final href = link.attributes['href'];
          if (href == null) return false;
          final cleanHref = href.split('?')[0].split('#')[0];
          return (RegExp(r'^/work/\d+$').hasMatch(cleanHref) ||
                  RegExp(r'^/audiobook/\d+$').hasMatch(cleanHref)) &&
              !cleanHref.contains('/discounts') &&
              !cleanHref.contains('/genre/');
        });

        if (!hasValidLink) continue;

        // Look for cover images within bookcard
        final coverImages = bookcard.querySelectorAll(
            '.cover-image img, .ebook-cover-image img, .audiobook-cover-image img');
        for (final img in coverImages) {
          // Check data-src first (lazy loading), then src
          final dataSrc = img.attributes['data-src'];
          final srcAttr = img.attributes['src'];
          final src = dataSrc ?? srcAttr;
          if (src != null &&
              src.isNotEmpty &&
              !src.contains('data:image') &&
              !src.contains('1x1') &&
              !src.contains('placeholder')) {
            final url = _makeAbsoluteUrl(src, 'https://author.today');
            if (!url.contains('data:image') &&
                !url.contains('1x1') &&
                !url.contains('placeholder')) {
              return url;
            }
          }
        }
      }
      return null;
    } on Exception {
      return null;
    }
  }

  /// Extracts book URL from author.today search results.
  ///
  /// Looks for bookcard elements in search results and extracts work/audiobook links.
  /// Only extracts links from actual book cards, not navigation or other elements.
  String? _extractBookUrlFromAuthorTodaySearch(String html) {
    try {
      final document = html_parser.parse(html);

      // Look for bookcard elements in search results
      // Try multiple selectors to handle different HTML structures
      var bookcards = document.querySelectorAll('.bookcard');
      if (bookcards.isEmpty) {
        // Try alternative selectors
        bookcards = document.querySelectorAll('[class*="bookcard"]');
      }
      if (bookcards.isEmpty) {
        // Try finding in book-row or book-shelf
        final bookRows = document.querySelectorAll('.book-row, .book-shelf');
        for (final row in bookRows) {
          bookcards = row.querySelectorAll('.bookcard, [class*="bookcard"]');
          if (bookcards.isNotEmpty) break;
        }
      }

      for (final bookcard in bookcards) {
        // Look for links to /work/ or /audiobook/ within bookcard
        // Prefer links in bookcard-footer (title links) or book-cover-content
        final titleLinks = bookcard.querySelectorAll(
            '.bookcard-footer a[href*="/work/"], .bookcard-footer a[href*="/audiobook/"]');
        final coverLinks = bookcard.querySelectorAll(
            'a.book-cover-content[href*="/work/"], a.book-cover-content[href*="/audiobook/"]');

        // Combine all links
        final allLinks = <dynamic>[...titleLinks, ...coverLinks];

        // If no specific links found, try all links in bookcard
        if (allLinks.isEmpty) {
          allLinks.addAll(bookcard
              .querySelectorAll('a[href*="/work/"], a[href*="/audiobook/"]'));
        }

        for (final link in allLinks) {
          final href = link.attributes['href'];
          if (href == null) continue;

          // Clean href - remove query parameters for matching
          final cleanHref = href.split('?')[0].split('#')[0];

          // Must be a numeric work/audiobook ID (e.g., /work/337777, /audiobook/342514)
          // Strictly exclude all non-book pages
          if ((cleanHref.contains('/work/') ||
                  cleanHref.contains('/audiobook/')) &&
              // Exclude all navigation and category pages
              !cleanHref.contains('/genre/') &&
              !cleanHref.contains('/sorting') &&
              !cleanHref.contains('/all') &&
              !cleanHref.contains('/discounts') &&
              !cleanHref.contains('/author/') &&
              !cleanHref.contains('/search') &&
              !cleanHref.contains('/recommended') &&
              !cleanHref.contains('/recent') &&
              // Must match exact pattern: /work/123456 or /audiobook/123456
              (RegExp(r'^/work/\d+$').hasMatch(cleanHref) ||
                  RegExp(r'^/audiobook/\d+$').hasMatch(cleanHref))) {
            // Return absolute URL
            if (cleanHref.startsWith('http')) {
              return cleanHref;
            } else if (cleanHref.startsWith('/')) {
              return 'https://author.today$cleanHref';
            }
          }
        }
      }

      // Fallback: if no bookcards found, try direct link search (less reliable)
      if (bookcards.isEmpty) {
        final allLinks = document
            .querySelectorAll('a[href*="/work/"], a[href*="/audiobook/"]');
        for (final link in allLinks) {
          final href = link.attributes['href'];
          if (href == null) continue;

          final cleanHref = href.split('?')[0].split('#')[0];

          // Must match exact pattern and exclude navigation
          if ((RegExp(r'^/work/\d+$').hasMatch(cleanHref) ||
                  RegExp(r'^/audiobook/\d+$').hasMatch(cleanHref)) &&
              !cleanHref.contains('/genre/') &&
              !cleanHref.contains('/discounts') &&
              !cleanHref.contains('/author/') &&
              !cleanHref.contains('/search')) {
            if (cleanHref.startsWith('/')) {
              return 'https://author.today$cleanHref';
            } else if (cleanHref.startsWith('http')) {
              return cleanHref;
            }
          }
        }
      }

      return null;
    } on Exception {
      return null;
    }
  }

  /// Searches on audio-kniga.com and extracts cover URL.
  ///
  /// Uses POST search on audio-kniga.com to find book,
  /// then extracts cover from search results or book page.
  Future<String?> _searchAudioKniga(String query) async {
    try {
      // Step 1: Search directly on audio-kniga.com using POST (as required by the site)
      final searchHtml = await _fetchHtmlPost(
        'https://audio-kniga.com/index.php',
        {
          'do': 'search',
          'subaction': 'search',
          'story': query,
        },
      );
      if (searchHtml == null) return null;

      // Step 3: Fallback - extract book URL and navigate to book page
      final bookUrl =
          _extractBookUrlFromAudioKnigaSearch(searchHtml, query: query);
      if (bookUrl == null) return null;

      // Step 4: Navigate to book page and extract cover
      final bookPageHtml = await _fetchHtml(bookUrl);
      if (bookPageHtml == null) return null;

      return _extractCoverUrlFromBookPage(bookPageHtml, 'audio-kniga.com');
    } on Exception {
      return null;
    }
  }

  /// Extracts book URL from audio-kniga.com search results.
  ///
  /// Looks for book links in search results by finding links in common book containers.
  String? _extractBookUrlFromAudioKnigaSearch(String html, {String? query}) {
    try {
      final document = html_parser.parse(html);

      // Extract meaningful words from query for matching (if provided)
      var queryWords = <String>[];
      if (query != null) {
        queryWords = query
            .toLowerCase()
            .split(RegExp(r'[\s\-–—]+'))
            .where((word) => word.length > 2) // Only words longer than 2 chars
            .where((word) => ![
                  'и',
                  'или',
                  'для',
                  'про',
                  'из',
                  'на',
                  'по',
                  'от',
                  'до'
                ].contains(word))
            .toList();
      }

      // Priority 1: Look for links in common book container elements
      // Based on real HTML structure: .rel-movie, .short-item, .side-movie, .short2-item
      final bookContainers = document.querySelectorAll(
          '.movie-item, .rel-movie, .short-item, .side-movie, .short2-item, article, .book, .book-card, [class*="movie"], [class*="item"]');

      for (final container in bookContainers) {
        final link = container.querySelector('a[href]');
        if (link != null) {
          final href = link.attributes['href'];
          if (href != null) {
            final cleanHref = href.split('?')[0].split('#')[0];
            // Exclude navigation links
            if (!cleanHref.contains('/search') &&
                !cleanHref.contains('/genre') &&
                !cleanHref.contains('/blog') &&
                !cleanHref.contains('/top') &&
                !cleanHref.startsWith('#') &&
                cleanHref.isNotEmpty) {
              // If query is provided, check if link text matches query words
              if (queryWords.isNotEmpty) {
                final linkText = link.text.toLowerCase();
                var matches = 0;
                for (final word in queryWords) {
                  if (linkText.contains(word)) {
                    matches++;
                  }
                }

                // Need at least some matches to consider this link
                if (matches == 0 && queryWords.length > 1) {
                  continue; // Skip this link, doesn't match query
                }
              }

              if (cleanHref.startsWith('/')) {
                return 'https://audio-kniga.com$cleanHref';
              } else if (cleanHref.startsWith('http')) {
                if (cleanHref.contains('audio-kniga.com')) {
                  return cleanHref;
                }
              } else {
                // Relative URL
                return 'https://audio-kniga.com/$cleanHref';
              }
            }
          }
        }
      }

      // Priority 2: Look for all links and filter by matching query words
      if (query != null) {
        final allLinks = document.querySelectorAll('a[href]');

        for (final link in allLinks) {
          final href = link.attributes['href'];
          if (href == null) continue;

          final cleanHref = href.split('?')[0].split('#')[0];

          // Skip navigation and non-book links
          if (cleanHref.contains('/search') ||
              cleanHref.contains('/genre') ||
              cleanHref.contains('/blog') ||
              cleanHref.contains('/top') ||
              cleanHref.startsWith('#') ||
              cleanHref.isEmpty) {
            continue;
          }

          // Check if link text or nearby text matches query
          final linkText = link.text.toLowerCase();
          if (queryWords.isNotEmpty) {
            var matches = 0;
            for (final word in queryWords) {
              if (linkText.contains(word)) {
                matches++;
              }
            }

            // Need at least some matches
            if (matches == 0 && queryWords.length > 1) {
              continue;
            }
          }

          // Return first valid book link
          if (cleanHref.startsWith('/')) {
            return 'https://audio-kniga.com$cleanHref';
          } else if (cleanHref.startsWith('http')) {
            if (cleanHref.contains('audio-kniga.com')) {
              return cleanHref;
            }
          } else {
            return 'https://audio-kniga.com/$cleanHref';
          }
        }
      }

      return null;
    } on Exception {
      return null;
    }
  }

  /// Extracts cover URL from RuTracker topic page.
  Future<String?> _extractCoverFromRuTracker(String topicId) async {
    try {
      // Get active RuTracker endpoint
      final appDb = AppDatabase.getInstance();
      final db = await appDb.ensureInitialized();
      final endpointManager = EndpointManager(db, appDb);
      final baseUrl = await endpointManager.getActiveEndpoint();

      // Build topic URL
      final topicUrl = '$baseUrl/forum/viewtopic.php?t=$topicId';

      // Fetch HTML using existing Dio client (with authentication)
      final dio = await DioClient.instance;
      final response = await dio.get(
        topicUrl,
        options: Options(
          responseType: ResponseType.bytes,
          headers: {
            'Accept': 'text/html,application/xhtml+xml,application/xml',
            'Accept-Charset': 'windows-1251,utf-8',
          },
        ),
      );

      if (response.statusCode != 200) return null;

      // Use existing RuTrackerParser to extract cover
      // This reuses the existing _extractCoverUrlImproved logic
      final parser = RuTrackerParser();
      final audiobook = await parser.parseTopicDetails(
        response.data,
        contentType: response.headers.value('content-type'),
        baseUrl: baseUrl,
      );

      // Extract coverUrl from parsed audiobook
      return audiobook?.coverUrl;
    } on Exception {
      return null;
    }
  }

  /// Fetches HTML content from a URL.
  Future<String?> _fetchHtml(String url) async {
    try {
      final dio = await DioClient.instance;
      // Use the same User-Agent as RuTracker authentication to avoid blocks
      final userAgentManager = UserAgentManager();
      final userAgent = await userAgentManager.getUserAgent();

      final response = await dio.get(
        url,
        options: Options(
          responseType: ResponseType.plain,
          headers: {
            'User-Agent': userAgent,
            'Accept':
                'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
            'Accept-Language': 'ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7',
          },
          receiveTimeout: const Duration(seconds: 10),
        ),
      );

      // Accept both 200 (OK) and 202 (Accepted) status codes
      if ((response.statusCode == 200 || response.statusCode == 202) &&
          response.data is String) {
        return response.data as String;
      }
      return null;
    } on Exception {
      return null;
    }
  }

  /// Fetches HTML content from a URL using POST request.
  Future<String?> _fetchHtmlPost(String url, Map<String, String> data) async {
    try {
      final dio = await DioClient.instance;
      final userAgentManager = UserAgentManager();
      final userAgent = await userAgentManager.getUserAgent();

      final response = await dio.post(
        url,
        data: data,
        options: Options(
          responseType: ResponseType.plain,
          headers: {
            'User-Agent': userAgent,
            'Accept':
                'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
            'Accept-Language': 'ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7',
            'Content-Type': 'application/x-www-form-urlencoded',
            'Referer': 'https://audio-kniga.com/',
          },
          receiveTimeout: const Duration(seconds: 10),
        ),
      );

      // Accept both 200 (OK) and 202 (Accepted) status codes
      if ((response.statusCode == 200 || response.statusCode == 202) &&
          response.data is String) {
        return response.data as String;
      }
      return null;
    } on Exception {
      return null;
    }
  }

  /// Extracts cover URL from book page (not search results).
  String? _extractCoverUrlFromBookPage(String html, String source) {
    try {
      final document = html_parser.parse(html);

      // For author.today - extract from book page
      if (source == 'author.today') {
        // Priority 1: Try specific selector for cover image (img.cover-image)
        final coverImage = document.querySelector('img.cover-image');
        if (coverImage != null) {
          // Check data-src first (lazy loading), then src
          final dataSrc = coverImage.attributes['data-src'];
          final srcAttr = coverImage.attributes['src'];
          final src = dataSrc ?? srcAttr;
          if (src != null && src.isNotEmpty && !src.contains('data:image')) {
            final url = _makeAbsoluteUrl(src, 'https://author.today');
            if (!url.contains('data:image') &&
                !url.contains('1x1') &&
                !url.contains('placeholder')) {
              return url;
            }
          }
        }

        // Priority 2: Look for images in cover-image divs (lazy loading support)
        final coverImageDivs = document.querySelectorAll(
            '.cover-image img, .ebook-cover-image img, .audiobook-cover-image img');
        for (final img in coverImageDivs) {
          // Check data-src first (lazy loading), then src
          final dataSrc = img.attributes['data-src'];
          final srcAttr = img.attributes['src'];
          final src = dataSrc ?? srcAttr;
          if (src != null &&
              src.isNotEmpty &&
              !src.contains('data:image') &&
              !src.contains('1x1') &&
              !src.contains('placeholder')) {
            final url = _makeAbsoluteUrl(src, 'https://author.today');
            if (!url.contains('data:image') &&
                !url.contains('1x1') &&
                !url.contains('placeholder')) {
              return url;
            }
          }
        }

        // Priority 3: Fallback - try all images with cover-related attributes
        final coverImgs = document.querySelectorAll('img');
        for (final img in coverImgs) {
          // Check data-src first (lazy loading), then src
          final dataSrc = img.attributes['data-src'];
          final srcAttr = img.attributes['src'];
          final src = dataSrc ?? srcAttr;
          final className = img.attributes['class'] ?? '';
          if (src != null &&
              src.isNotEmpty &&
              !src.contains('data:image') && // Exclude data URIs (placeholders)
              !src.contains('icon') &&
              !src.contains('logo') &&
              !src.contains('avatar') &&
              (className.contains('cover') ||
                  src.contains('cover') ||
                  src.contains('Cover') ||
                  src.contains('cm.author.today') || // author.today CDN
                  src.contains('.jpg') ||
                  src.contains('.jpeg') ||
                  src.contains('.png') ||
                  src.contains('.webp'))) {
            final url = _makeAbsoluteUrl(src, 'https://author.today');
            // Verify it's a real image URL, not a placeholder
            if (!url.contains('data:image') &&
                !url.contains('1x1') &&
                !url.contains('placeholder')) {
              return url;
            }
          }
        }
      }

      // For audio-kniga.com - extract from book page
      if (source == 'audio-kniga.com') {
        // Priority 1: Look for cover image in .m-img (main image container)
        final mainImg = document.querySelector('.m-img img');
        if (mainImg != null) {
          final src = mainImg.attributes['src'];
          if (src != null &&
              src.isNotEmpty &&
              !src.contains('data:image') &&
              src.contains('/uploads/posts/books/')) {
            final url = _makeAbsoluteUrl(src, 'https://audio-kniga.com');
            if (!url.contains('data:image') &&
                !url.contains('1x1') &&
                !url.contains('placeholder')) {
              return url;
            }
          }
        }

        // Priority 2: Look for images with /uploads/posts/books/ pattern
        final allImages = document.querySelectorAll('img');
        for (final img in allImages) {
          final src = img.attributes['src'];
          if (src != null &&
              src.isNotEmpty &&
              !src.contains('data:image') &&
              !src.contains('icon') &&
              !src.contains('logo') &&
              !src.contains('avatar') &&
              src.contains('/uploads/posts/books/')) {
            final url = _makeAbsoluteUrl(src, 'https://audio-kniga.com');
            if (!url.contains('data:image') &&
                !url.contains('1x1') &&
                !url.contains('placeholder')) {
              return url;
            }
          }
        }

        // Priority 3: Look for images in page-col-left (cover column)
        final coverColumn = document.querySelector('.page-col-left img');
        if (coverColumn != null) {
          final src = coverColumn.attributes['src'];
          if (src != null &&
              src.isNotEmpty &&
              !src.contains('data:image') &&
              !src.contains('18plus.png')) {
            final url = _makeAbsoluteUrl(src, 'https://audio-kniga.com');
            if (!url.contains('data:image') &&
                !url.contains('1x1') &&
                !url.contains('placeholder')) {
              return url;
            }
          }
        }
      }

      return null;
    } on Exception {
      return null;
    }
  }

  /// Makes absolute URL from relative URL.
  String _makeAbsoluteUrl(String url, String baseUrl) {
    if (url.startsWith('http')) {
      return url;
    } else if (url.startsWith('//')) {
      return 'https:$url';
    } else if (url.startsWith('/')) {
      return '$baseUrl$url';
    }
    return '$baseUrl/$url';
  }

  /// Downloads cover image and saves to cache.
  Future<String?> _downloadAndCacheCover(
    String imageUrl,
    String groupName,
  ) async {
    try {
      final dio = await DioClient.instance;
      // Use the same User-Agent as RuTracker authentication to avoid blocks
      final userAgentManager = UserAgentManager();
      final userAgent = await userAgentManager.getUserAgent();

      final response = await dio.get(
        imageUrl,
        options: Options(
          responseType: ResponseType.bytes,
          headers: {
            'User-Agent': userAgent,
            'Accept': 'image/*',
            'Referer': imageUrl,
          },
          receiveTimeout: const Duration(seconds: 15),
        ),
      );

      if (response.statusCode == 200 && response.data is List<int>) {
        final bytes = response.data as List<int>;

        // Get cache directory
        final cacheDir = await getTemporaryDirectory();
        final coversDir =
            Directory(path.join(cacheDir.path, 'covers', 'fallback'));
        if (!await coversDir.exists()) {
          await coversDir.create(recursive: true);
        }

        // Generate filename from groupName hash
        final hash = groupName.hashCode.abs();
        final extension =
            path.extension(imageUrl).split('?').first; // Remove query params
        final filename = '$hash${extension.isNotEmpty ? extension : '.jpg'}';
        final filePath = path.join(coversDir.path, filename);

        // Save to file
        final file = File(filePath);
        await file.writeAsBytes(bytes);

        return filePath;
      }
      return null;
    } on Exception {
      return null;
    }
  }
}
