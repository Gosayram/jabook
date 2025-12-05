// ignore_for_file: avoid_print

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
import 'package:flutter_test/flutter_test.dart';
import 'package:html/parser.dart' as html_parser;
import 'package:jabook/core/data/remote/network/dio_client.dart';
import 'package:jabook/core/data/remote/network/user_agent_manager.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:path/path.dart' as path;

/// Test service for testing cover fallback logic with real books.
///
/// This service allows testing the full flow:
/// 1. Search for a book on author.today or audio-kniga.com
/// 2. Find the book link in search results
/// 3. Navigate to the book page
/// 4. Extract cover image URL from the book page
/// 5. Download and save the cover image
class CoverFallbackTestService {
  /// Tests the full flow for author.today.
  ///
  /// [bookQuery] - Search query (e.g., "Атаманов Михаил - Котенок и его человек")
  Future<TestResult> testAuthorToday(String bookQuery) async {
    final logger = StructuredLogger();
    final result = TestResult(source: 'author.today', query: bookQuery);

    try {
      try {
        await logger.log(
          level: 'info',
          subsystem: 'cover_fallback_test',
          message: 'Testing author.today cover fallback',
          extra: {'query': bookQuery},
        );
      } on Exception {
        // Ignore logging errors in tests
      }

      // Step 1: Search directly on author.today
      print('🔍 Step 1: Searching author.today for "$bookQuery"...');
      final searchUrl =
          'https://author.today/search?q=${Uri.encodeComponent(bookQuery)}';
      final searchHtml = await _fetchHtml(searchUrl);
      if (searchHtml == null) {
        result.error = 'Failed to fetch search results';
        return result;
      }
      result.searchHtmlLength = searchHtml.length;
      print('✅ Search results fetched (${searchHtml.length} chars)');

      // Step 2: Extract book URL from search results
      print('🔍 Step 2: Extracting book URL from search results...');
      final bookUrl = _extractBookUrlFromAuthorTodaySearch(searchHtml);
      if (bookUrl == null) {
        // Debug: check what we found
        final document = html_parser.parse(searchHtml);
        final bookcards = document.querySelectorAll('.bookcard');
        print('   📊 Found ${bookcards.length} bookcard elements');
        if (bookcards.isNotEmpty) {
          final firstCard = bookcards.first;
          final allLinks = firstCard
              .querySelectorAll('a[href*="/work/"], a[href*="/audiobook/"]');
          print('   📊 First bookcard has ${allLinks.length} links');
          for (var i = 0; i < allLinks.length && i < 3; i++) {
            final href = allLinks[i].attributes['href'];
            print('   📊 Link ${i + 1}: $href');
          }
        }
        result.error = 'Book link not found in search results';
        return result;
      }
      result.bookUrl = bookUrl;
      print('✅ Found book URL: $bookUrl');

      // Step 3: Navigate to book page
      print('🔍 Step 3: Fetching book page...');
      final bookPageHtml = await _fetchHtml(bookUrl);
      if (bookPageHtml == null) {
        result.error = 'Failed to fetch book page';
        return result;
      }
      result.bookPageHtmlLength = bookPageHtml.length;
      print('✅ Book page fetched (${bookPageHtml.length} chars)');

      // Step 4: Extract cover URL from book page
      print('🔍 Step 4: Extracting cover URL from book page...');
      final coverUrl =
          _extractCoverUrlFromBookPage(bookPageHtml, 'author.today');
      if (coverUrl == null) {
        result.error = 'Cover URL not found on book page';
        return result;
      }
      result.coverUrl = coverUrl;
      print('✅ Found cover URL: $coverUrl');

      // Step 5: Download cover image
      print('🔍 Step 5: Downloading cover image...');
      final coverPath = await _downloadCover(coverUrl, bookQuery);
      if (coverPath == null) {
        result.error = 'Failed to download cover image';
        return result;
      }
      result
        ..coverPath = coverPath
        ..success = true;
      print('✅ Cover downloaded to: $coverPath');

      // Step 6: Verify file exists and get size
      final coverFile = File(coverPath);
      if (await coverFile.exists()) {
        final stat = await coverFile.stat();
        result.coverFileSize = stat.size;
        print('✅ Cover file verified: ${stat.size} bytes');
      }

      try {
        await logger.log(
          level: 'info',
          subsystem: 'cover_fallback_test',
          message: 'Author.today test completed successfully',
          extra: {
            'query': bookQuery,
            'book_url': bookUrl,
            'cover_url': coverUrl,
            'cover_path': coverPath,
            'file_size': result.coverFileSize,
          },
        );
      } on Exception {
        // Ignore logging errors in tests
      }

      return result;
    } on Exception catch (e) {
      result.error = e.toString();
      try {
        await logger.log(
          level: 'error',
          subsystem: 'cover_fallback_test',
          message: 'Author.today test failed',
          cause: e.toString(),
          extra: {'query': bookQuery},
        );
      } on Exception {
        // Ignore logging errors in tests
      }
      return result;
    }
  }

  /// Tests the full flow for audio-kniga.com.
  Future<TestResult> testAudioKniga(String bookQuery) async {
    final logger = StructuredLogger();
    final result = TestResult(source: 'audio-kniga.com', query: bookQuery);

    try {
      try {
        await logger.log(
          level: 'info',
          subsystem: 'cover_fallback_test',
          message: 'Testing audio-kniga.com cover fallback',
          extra: {'query': bookQuery},
        );
      } on Exception {
        // Ignore logging errors in tests
      }

      // Step 1: Search directly on audio-kniga.com using POST (as required by the site)
      print('🔍 Step 1: Searching audio-kniga.com for "$bookQuery"...');
      final searchHtml = await _fetchHtmlPost(
        'https://audio-kniga.com/index.php',
        {
          'do': 'search',
          'subaction': 'search',
          'story': bookQuery,
        },
      );
      if (searchHtml == null) {
        result.error = 'Failed to fetch search results';
        return result;
      }
      result.searchHtmlLength = searchHtml.length;
      print('✅ Search results fetched (${searchHtml.length} chars)');

      // Step 2: Extract book URL from search results
      print('🔍 Step 2: Extracting book URL from search results...');
      final bookUrl =
          _extractBookUrlFromAudioKnigaSearch(searchHtml, query: bookQuery);
      if (bookUrl == null) {
        // Debug: check what we found
        final document = html_parser.parse(searchHtml);

        // Check for book containers
        final bookContainers = document.querySelectorAll(
            '.rel-movie, .short-item, .side-movie, .short2-item, article, .book, .book-card');
        print('   📊 Found ${bookContainers.length} book container elements');

        // Show first few book containers and their links
        var count = 0;
        for (final container in bookContainers) {
          if (count++ >= 5) break;
          final link = container.querySelector('a[href]');
          if (link != null) {
            final href = link.attributes['href'];
            final text = link.text.trim();
            print(
                '   📊 Container $count: href="$href" text="${text.substring(0, text.length > 50 ? 50 : text.length)}..."');
          }
        }

        result.error = 'Book link not found in search results';
        return result;
      }
      result.bookUrl = bookUrl;
      print('✅ Found book URL: $bookUrl');

      // Step 3: Navigate to book page
      print('🔍 Step 3: Fetching book page...');
      final bookPageHtml = await _fetchHtml(bookUrl);
      if (bookPageHtml == null) {
        result.error = 'Failed to fetch book page';
        return result;
      }
      result.bookPageHtmlLength = bookPageHtml.length;
      print('✅ Book page fetched (${bookPageHtml.length} chars)');

      // Step 4: Extract cover URL from book page
      print('🔍 Step 4: Extracting cover URL from book page...');
      final coverUrl =
          _extractCoverUrlFromBookPage(bookPageHtml, 'audio-kniga.com');
      if (coverUrl == null) {
        result.error = 'Cover URL not found on book page';
        return result;
      }
      result.coverUrl = coverUrl;
      print('✅ Found cover URL: $coverUrl');

      // Step 5: Download cover image
      print('🔍 Step 5: Downloading cover image...');
      final coverPath = await _downloadCover(coverUrl, bookQuery);
      if (coverPath == null) {
        result.error = 'Failed to download cover image';
        return result;
      }
      result
        ..coverPath = coverPath
        ..success = true;
      print('✅ Cover downloaded to: $coverPath');

      // Step 6: Verify file exists and get size
      final coverFile = File(coverPath);
      if (await coverFile.exists()) {
        final stat = await coverFile.stat();
        result.coverFileSize = stat.size;
        print('✅ Cover file verified: ${stat.size} bytes');
      }

      try {
        await logger.log(
          level: 'info',
          subsystem: 'cover_fallback_test',
          message: 'Audio-kniga.com test completed successfully',
          extra: {
            'query': bookQuery,
            'book_url': bookUrl,
            'cover_url': coverUrl,
            'cover_path': coverPath,
            'file_size': result.coverFileSize,
          },
        );
      } on Exception {
        // Ignore logging errors in tests
      }

      return result;
    } on Exception catch (e) {
      result.error = e.toString();
      try {
        await logger.log(
          level: 'error',
          subsystem: 'cover_fallback_test',
          message: 'Audio-kniga.com test failed',
          cause: e.toString(),
          extra: {'query': bookQuery},
        );
      } on Exception {
        // Ignore logging errors in tests
      }
      return result;
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

  /// Extracts book URL from audio-kniga.com search results.
  ///
  /// Looks for book links in search results by finding links in common book containers.
  String? _extractBookUrlFromAudioKnigaSearch(String html, {String? query}) {
    try {
      final document = html_parser.parse(html);

      print('   🔍 HTML contains "movie-item": ${html.contains("movie-item")}');
      print('   🔍 HTML length: ${html.length}');
      if (html.length > 500) {
        print(
            '   🔍 HTML start: ${html.substring(0, 500).replaceAll("\n", " ")}');
      }

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

              // This link looks good, return it
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

  /// Fetches HTML content from a URL.
  Future<String?> _fetchHtml(String url) async {
    try {
      final dio = await DioClient.instance;
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

  /// Downloads cover image and saves to test directory.
  Future<String?> _downloadCover(String imageUrl, String bookQuery) async {
    try {
      final dio = await DioClient.instance;
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

        // Get test directory - use /tmp for easy access
        final testDir = Directory(path.join(
          '/tmp',
          'cover_fallback_test',
        ));
        if (!await testDir.exists()) {
          await testDir.create(recursive: true);
        }

        // Generate filename with source prefix for easy identification
        final sourcePrefix = imageUrl.contains('author.today')
            ? 'author_today'
            : (imageUrl.contains('audio-kniga.com')
                ? 'audio_kniga'
                : 'unknown');
        final hash = bookQuery.hashCode.abs();
        final extension = path.extension(imageUrl).split('?').first;
        final filename =
            '${sourcePrefix}_$hash${extension.isNotEmpty ? extension : '.jpg'}';
        final filePath = path.join(testDir.path, filename);

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

/// Result of a cover fallback test.
class TestResult {
  TestResult({
    required this.source,
    required this.query,
  });

  final String source;
  final String query;
  bool success = false;
  String? error;
  int? searchHtmlLength;
  String? bookUrl;
  int? bookPageHtmlLength;
  String? coverUrl;
  String? coverPath;
  int? coverFileSize;

  @override
  String toString() {
    final buffer = StringBuffer()
      ..writeln('Test Result:')
      ..writeln('  Source: $source')
      ..writeln('  Query: $query')
      ..writeln('  Success: $success');
    if (error != null) {
      buffer.writeln('  Error: $error');
    }
    if (searchHtmlLength != null) {
      buffer.writeln('  Search HTML length: $searchHtmlLength');
    }
    if (bookUrl != null) {
      buffer.writeln('  Book URL: $bookUrl');
    }
    if (bookPageHtmlLength != null) {
      buffer.writeln('  Book page HTML length: $bookPageHtmlLength');
    }
    if (coverUrl != null) {
      buffer.writeln('  Cover URL: $coverUrl');
    }
    if (coverPath != null) {
      buffer.writeln('  Cover path: $coverPath');
    }
    if (coverFileSize != null) {
      buffer.writeln('  Cover file size: $coverFileSize bytes');
    }
    return buffer.toString();
  }
}
