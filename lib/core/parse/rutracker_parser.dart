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

import 'package:html/dom.dart';
import 'package:html/parser.dart' as parser;
import 'package:jabook/core/errors/failures.dart';
import 'package:windows1251/windows1251.dart';

/// Represents an audiobook with metadata from RuTracker.
///
/// This class contains all the information needed to display and
/// manage an audiobook, including basic metadata, torrent information,
/// and chapter details.
class Audiobook {
  /// Creates a new Audiobook instance.
  ///
  /// All parameters are required to ensure complete audiobook information.
  Audiobook({
    required this.id,
    required this.title,
    required this.author,
    required this.category,
    required this.size,
    required this.seeders,
    required this.leechers,
    required this.magnetUrl,
    this.coverUrl,
    required this.chapters,
    required this.addedDate,
  });

  /// Unique identifier for the audiobook.
  final String id;

  /// Title of the audiobook.
  final String title;

  /// Author or narrator of the audiobook.
  final String author;

  /// Category the audiobook belongs to.
  final String category;

  /// File size of the audiobook.
  final String size;

  /// Number of seeders for the torrent.
  final int seeders;

  /// Number of leechers for the torrent.
  final int leechers;

  /// Magnet URL for downloading the audiobook.
  final String magnetUrl;

  /// URL of the cover image, if available.
  final String? coverUrl;

  /// List of chapters in the audiobook.
  final List<Chapter> chapters;

  /// Date when the audiobook was added to RuTracker.
  final DateTime addedDate;
}

/// Represents a chapter within an audiobook.
///
/// This class contains information about a specific chapter,
/// including its title, duration, and byte range for streaming.
class Chapter {
  /// Creates a new Chapter instance.
  ///
  /// All parameters are required to define a complete chapter.
  Chapter({
    required this.title,
    required this.durationMs,
    required this.fileIndex,
    required this.startByte,
    required this.endByte,
  });

  /// Title of the chapter.
  final String title;

  /// Duration of the chapter in milliseconds.
  final int durationMs;

  /// Index of the file containing this chapter.
  final int fileIndex;

  /// Starting byte position of the chapter in the file.
  final int startByte;

  /// Ending byte position of the chapter in the file.
  final int endByte;
}

/// Parser for extracting audiobook information from RuTracker HTML.
///
/// This class provides methods to parse search results and topic details
/// from RuTracker forum pages, handling both UTF-8 and Windows-1251 encoding.
class RuTrackerParser {
  // CSS selectors centralized for easier maintenance
  static const String _rowSelector = 'tr.hl-tr';
  static const String _titleSelector = 'a.torTopic, a.torTopic.tt-text, a[href*="viewtopic.php?t="]';
  static const String _authorSelector =
      'a.pmed, .topicAuthor a, a[href*="profile.php"]';
  static const String _sizeSelector = 'span.small, a.f-dl.dl-stub';
  static const String _seedersSelector = 'span.seedmed, span.seedmed b';
  static const String _leechersSelector = 'span.leechmed, span.leechmed b';
  static const String _downloadHrefSelector = 'a[href^="dl.php?t="]';
  static const String _magnetLinkSelector = 'a.magnet-link, a[href^="magnet:"]';
  static const String _postBodySelector = '.post_body, .post-body';
  static const String _maintitleSelector = 'h1.maintitle a, h1.maintitle';
  static const String _torStatsSelector = '#t-tor-stats';
  static const String _torSizeSelector = '#tor-size-humn, span#tor-size-humn';

  /// Parses search results from RuTracker search page HTML.
  ///
  /// This method takes HTML content from a search results page and extracts
  /// audiobook information including titles, authors, magnet links, etc.
  /// It automatically handles character encoding (UTF-8 or Windows-1251).
  ///
  /// The [html] parameter contains the HTML content of the search results page.
  ///
  /// Returns a list of [Audiobook] objects found in the search results.
  ///
  /// Throws [ParsingFailure] if the HTML cannot be parsed.
  Future<List<Audiobook>> parseSearchResults(dynamic htmlData) async {
    try {
      // Handle both String (from ResponseType.plain) and List<int> (for backward compatibility)
      String decodedHtml;
      if (htmlData is String) {
        // Dio with ResponseType.plain already decoded the response
        // Use the string directly
        decodedHtml = htmlData;
      } else if (htmlData is List<int>) {
        // Handle binary data (bytes) - decode from UTF-8 or Windows-1251
        try {
          decodedHtml = utf8.decode(htmlData);
        } on FormatException {
          decodedHtml = windows1251.decode(htmlData);
        }
      } else {
        // Fallback: try to convert to string
        decodedHtml = htmlData.toString();
      }

      final document = parser.parse(decodedHtml);
      final results = <Audiobook>[];

      // Check if page requires authentication or has errors
      // Note: RuTracker shows login form for guests, but search can still work (with limitations)
      // So we only check for actual access denied messages, not just presence of login form
      final pageText = (document.text ?? '').toLowerCase();
      final hasAccessDenied = pageText.contains('доступ запрещен') ||
          pageText.contains('access denied') ||
          pageText.contains('недостаточно прав') ||
          pageText.contains('требуется авторизация') ||
          pageText.contains('авторизуйтесь');
      
      // If page has access denied message, it's an auth issue
      if (hasAccessDenied) {
        throw const ParsingFailure(
          'Page appears to require authentication. Please log in first.',
        );
      }
      
      // If no guest search form and no results, might be wrong page type
      // But don't throw error yet - let's check if there are actual results

      // Parse actual RuTracker topic rows structure
      final topicRows = document.querySelectorAll(_rowSelector);
      
      // If no rows found, check if page structure is unexpected
      if (topicRows.isEmpty) {
        // Improved check for search form (more reliable)
        final hasSearchForm = document.querySelector('form[action*="tracker"]') != null ||
            document.querySelector('form[action*="search"]') != null ||
            document.querySelector('input[name="nm"]') != null ||
            document.querySelector('form#quick-search-guest') != null ||
            document.querySelector('form#quick-search') != null;
        
        // Check for search page elements (even if results are empty)
        final hasSearchPageElements = document.querySelector('div.tCenter') != null ||
            document.querySelector('table.forumline') != null ||
            document.querySelector('div.nav') != null;
        
        // Check if this is the main index page (not search results)
        final isIndexPage = document.querySelector('div#forums_list_wrap') != null ||
            document.querySelector('div#latest_news') != null;
        
        // Improved check for access denied messages (more specific)
        final hasAccessDenied = pageText.contains('доступ запрещен') ||
            pageText.contains('access denied') ||
            pageText.contains('недостаточно прав') ||
            pageText.contains('требуется авторизация') ||
            pageText.contains('авторизуйтесь');
        
        // If page has access denied message, it's an auth issue
        if (hasAccessDenied) {
          throw const ParsingFailure(
            'Page appears to require authentication. Please log in first.',
          );
        }
        
        // If there's a search form OR search page elements, it's likely empty results (not an error)
        // If it's index page, it's also valid (just no search was performed)
        if (hasSearchForm || hasSearchPageElements || isIndexPage) {
          // Empty results are valid - return empty list
          return results;
        }
        
        // If no search form, no search page elements, and not index page - possibly error
        throw const ParsingFailure(
          'Page structure may have changed. Unable to parse search results.',
        );
      }

      for (final row in topicRows) {
        // Skip ad/outer rows
        if (row.classes.any((c) => c.contains('banner') || c.contains('ads'))) {
          continue;
        }
        
        final titleElement = row.querySelector(_titleSelector);
        if (titleElement == null) {
          continue;
        }
        
        // Extract topic ID with priority: data-topic_id attribute (most reliable)
        var topicId = row.attributes['data-topic_id'] ?? '';
        if (topicId.isEmpty) {
          // Try to extract from title link href
          final titleHref = titleElement.attributes['href'];
          if (titleHref != null) {
            topicId = _extractTopicIdFromUrl(titleHref);
          }
        }
        if (topicId.isEmpty) {
          topicId = _extractTopicIdFromAny(row);
        }
        
        // If still no topic ID, try to extract from download link
        if (topicId.isEmpty) {
          final magnetElement = row.querySelector(_downloadHrefSelector);
          if (magnetElement != null) {
            final href = magnetElement.attributes['href'];
            if (href != null) {
              topicId = _extractInfoHashFromUrl(href);
            }
          }
        }
        
        // Use title as fallback ID if no topic ID found (shouldn't happen, but be safe)
        if (topicId.isEmpty) {
          topicId = titleElement.text.trim().hashCode.toString();
        }
        
        final authorElement = row.querySelector(_authorSelector);
        final sizeElement = row.querySelector(_sizeSelector);
        final seedersElement = row.querySelector(_seedersSelector);
        final leechersElement = row.querySelector(_leechersSelector);
        final magnetElement = row.querySelector(_downloadHrefSelector);

        // Extract size from multiple possible locations
        var sizeText = '0 MB';
        if (sizeElement != null) {
          sizeText = sizeElement.text.trim();
        } else {
          // Try to extract from row text using regex
          final sizeMatch = RegExp(r'([\d.,]+\s*[KMGT]?B)', caseSensitive: false)
              .firstMatch(row.text);
          if (sizeMatch != null) {
            sizeText = sizeMatch.group(1)?.trim() ?? '0 MB';
          }
        }

        // Extract magnet URL from download link
        var magnetUrl = '';
        if (magnetElement != null) {
          final href = magnetElement.attributes['href'];
          if (href != null) {
            magnetUrl = 'magnet:?xt=urn:btih:${_extractInfoHashFromUrl(href)}';
          }
        }

        final audiobook = Audiobook(
          id: topicId,
          title: titleElement.text.trim(),
          author: authorElement?.text.trim() ?? 'Unknown',
          category: _extractCategoryFromTitle(titleElement.text),
          size: sizeText,
          seeders: int.tryParse(seedersElement?.text.trim() ?? '0') ?? 0,
          leechers: int.tryParse(leechersElement?.text.trim() ?? '0') ?? 0,
          magnetUrl: magnetUrl,
          coverUrl: _extractCoverUrl(row),
          chapters: [],
          addedDate: _extractDateFromRow(row),
        );
        results.add(audiobook);
      }

      return results;
    } on ParsingFailure {
      // Re-throw parsing failures as-is
      rethrow;
    } on FormatException catch (e) {
      throw ParsingFailure(
        'Failed to decode HTML content. Encoding issue: ${e.message}',
        e,
      );
    } on Exception catch (e) {
      throw ParsingFailure(
        'Failed to parse search results: ${e.toString()}',
        e,
      );
    }
  }

  /// Parses detailed information from a RuTracker topic page.
  ///
  /// This method takes HTML content from a topic page and extracts
  /// comprehensive audiobook information including chapters, cover art,
  /// and detailed metadata. It automatically handles character encoding.
  ///
  /// The [html] parameter contains the HTML content of the topic page.
  ///
  /// Returns an [Audiobook] object with detailed information, or `null`
  /// if the page cannot be parsed or doesn't contain valid audiobook data.
  ///
  /// Throws [ParsingFailure] if the HTML cannot be parsed.
  Future<Audiobook?> parseTopicDetails(dynamic htmlData) async {
    try {
      // Handle both String (from ResponseType.plain) and List<int> (for backward compatibility)
      String decodedHtml;
      if (htmlData is String) {
        // Dio with ResponseType.plain already decoded the response
        // Use the string directly
        decodedHtml = htmlData;
      } else if (htmlData is List<int>) {
        // Handle binary data (bytes) - decode from UTF-8 or Windows-1251
        try {
          decodedHtml = utf8.decode(htmlData);
        } on FormatException {
          decodedHtml = windows1251.decode(htmlData);
        }
      } else {
        // Fallback: try to convert to string
        decodedHtml = htmlData.toString();
      }

      final document = parser.parse(decodedHtml);

      // Parse actual RuTracker topic page structure
      final titleElement = document.querySelector(_maintitleSelector);
      final postBody = document.querySelector(_postBodySelector);

      if (titleElement == null || postBody == null) {
        return null;
      }

      // Extract all structured metadata from span.post-b
      final metadata = _extractAllMetadata(postBody);
      
      // Extract author from structured metadata with priority:
      // 1. "Автор" field (if present)
      // 2. "Фамилия автора" + "Имя автора" (if both present)
      // 3. Profile link (fallback)
      String? authorName;
      final authorText = metadata['Автор'];
      if (authorText != null && authorText.isNotEmpty) {
        authorName = authorText.trim();
      } else {
        // Try combining surname and name
        final surnameText = metadata['Фамилия автора'];
        final nameText = metadata['Имя автора'];
        if (surnameText != null && nameText != null) {
          authorName = '$surnameText $nameText'.trim();
        }
      }
      
      // Fallback to profile link if structured metadata not found
      if (authorName == null || authorName.isEmpty) {
        final authorElement =
            postBody.querySelector('a[href*="profile.php"], .topicAuthor a');
        authorName = authorElement?.text.trim();
      }
      
      // Extract statistics from attach table first (most reliable source)
      // Cache frequently used selectors to avoid multiple DOM queries
      final attachTable = document.querySelector('table.attach');
      final torStats = document.querySelector(_torStatsSelector);
      String? sizeText;
      int? seeders, leechers;
      DateTime? registeredDate;
      
      if (attachTable != null) {
        // Extract size from span#tor-size-humn (most accurate)
        final sizeSpan = attachTable.querySelector('span#tor-size-humn');
        if (sizeSpan != null) {
          sizeText = sizeSpan.text.trim();
        }
        
        // Extract registered date from attach table (format: "ДД-МММ-ГГ ЧЧ:ММ")
        // Iterate through table rows to find the one containing "Зарегистрирован"
        final rows = attachTable.querySelectorAll('tr');
        for (final row in rows) {
          final rowText = row.text;
          if (rowText.contains('Зарегистрирован')) {
            // Look for date pattern "ДД-МММ-ГГ ЧЧ:ММ" or "ДД-МММ-ГГ"
            final dateMatch = RegExp(r'(\d{2})-(\w{3})-(\d{2})(?:\s+(\d{2}):(\d{2}))?')
                .firstMatch(rowText);
            if (dateMatch != null) {
              registeredDate = _parseAttachTableDate(
                dateMatch.group(1)!,
                dateMatch.group(2)!,
                dateMatch.group(3)!,
                dateMatch.group(4),
                dateMatch.group(5),
              );
              break;
            }
          }
        }
      }
      
      // Extract size from multiple possible locations (fallback)
      if (sizeText == null || sizeText.isEmpty) {
        // Try from tor-size-humn span (if not in attach table)
        final sizeSpan = document.querySelector(_torSizeSelector);
        if (sizeSpan != null) {
          sizeText = sizeSpan.text.trim();
        }
      }
      // Try from tor-stats table (reuse cached selector)
      if ((sizeText == null || sizeText.isEmpty) && torStats != null) {
        final sizeMatch = RegExp(r'Размер[:\s]*<b>([\d.,]+\s*[KMGT]?B)</b>')
            .firstMatch(torStats.innerHtml);
        sizeText = sizeMatch?.group(1)?.trim();
      }
      // Fallback to post body text
      if (sizeText == null || sizeText.isEmpty) {
        final sizeMatch =
            RegExp(r'Размер[:\s]*([\d.,]+\s*[KMGT]?B)').firstMatch(postBody.text);
        sizeText = sizeMatch?.group(1)?.trim();
      }
      
      // Extract seeders and leechers from tor-stats table (reuse cached selector)
      if (torStats != null) {
        // Extract seeders and leechers with improved selectors
        final seedersElement = torStats.querySelector('span.seed b, span.seedmed b');
        final leechersElement = torStats.querySelector('span.leech b, span.leechmed b');
        seeders = int.tryParse(seedersElement?.text.trim() ?? '0');
        leechers = int.tryParse(leechersElement?.text.trim() ?? '0');
      }
      // Fallback to post body text
      if (seeders == null) {
        final seedersMatch = RegExp(r'Сиды[:\s]*(\d+)').firstMatch(postBody.text);
        seeders = int.tryParse(seedersMatch?.group(1) ?? '0') ?? 0;
      }
      if (leechers == null) {
        final leechersMatch = RegExp(r'Личи[:\s]*(\d+)').firstMatch(postBody.text);
        leechers = int.tryParse(leechersMatch?.group(1) ?? '0') ?? 0;
      }

      // Extract magnet link - try magnet-link class first, then dl.php
      String? magnetUrl;
      final magnetElement = document.querySelector(_magnetLinkSelector);
      if (magnetElement != null && magnetElement.attributes['href'] != null) {
        final href = magnetElement.attributes['href']!;
        if (href.startsWith('magnet:')) {
          magnetUrl = href;
        } else {
          // Extract info hash from data-topic_id or href
          final topicId = magnetElement.attributes['data-topic_id'] ??
              _extractInfoHashFromUrl(href);
          if (topicId.isNotEmpty) {
            magnetUrl = 'magnet:?xt=urn:btih:$topicId';
          }
        }
      }
      // Fallback to dl.php link
      if (magnetUrl == null || magnetUrl.isEmpty) {
        final dlElement = document.querySelector(_downloadHrefSelector);
        if (dlElement != null) {
          final topicId = _extractInfoHashFromUrl(dlElement.attributes['href']);
          if (topicId.isNotEmpty) {
            magnetUrl = 'magnet:?xt=urn:btih:$topicId';
          }
        }
      }
      
      // Extract cover image with improved logic
      final coverUrl = _extractCoverUrlImproved(postBody, document.documentElement!);

      final chapters = <Chapter>[];
      // Try to parse chapters from description with improved flexible patterns
      final chapterText = postBody.text;
      
      // Pattern 1: "1. Название (1:23:45)" or "01. Название (1:23:45)"
      final pattern1 = RegExp(r'(\d+)[.:]\s*([^\n(]+?)\s*\((\d+:\d+(?::\d+)?)\)');
      for (final match in pattern1.allMatches(chapterText)) {
        final title = match.group(2)?.trim() ?? '';
        final duration = match.group(3)?.trim() ?? '0:00';
        chapters.add(_createChapterFromDuration(title, duration));
      }
      
      // Pattern 2: "01 - Название [1:23:45]"
      if (chapters.isEmpty) {
        final pattern2 = RegExp(r'(\d+)\s*[-–]\s*([^\n[\]]+?)\s*\[(\d+:\d+(?::\d+)?)\]');
        for (final match in pattern2.allMatches(chapterText)) {
          final title = match.group(2)?.trim() ?? '';
          final duration = match.group(3)?.trim() ?? '0:00';
          chapters.add(_createChapterFromDuration(title, duration));
        }
      }
      
      // Pattern 3: "Глава 1: Название (01:23:45)" or "Часть 1: Название (01:23:45)"
      if (chapters.isEmpty) {
        final pattern3 = RegExp(
          r'(?:Глава|Часть|Книга|Часть)\s*\d+[.:]\s*([^\n(]+?)\s*\((\d+:\d+(?::\d+)?)\)',
          caseSensitive: false,
        );
        for (final match in pattern3.allMatches(chapterText)) {
          final title = match.group(1)?.trim() ?? '';
          final duration = match.group(2)?.trim() ?? '0:00';
          chapters.add(_createChapterFromDuration(title, duration));
        }
      }
      
      // Pattern 4: Fallback to original pattern
      if (chapters.isEmpty) {
        final pattern4 = RegExp(r'(\d+[.:]\s*[^\n]+?)\s*\(?(\d+:\d+(?::\d+)?)\)?');
        for (final match in pattern4.allMatches(chapterText)) {
          final title = match.group(1)?.trim() ?? '';
          final duration = match.group(2)?.trim() ?? '0:00';
          chapters.add(_createChapterFromDuration(title, duration));
        }
      }

      // Extract topic ID from multiple possible sources (improved)
      var topicId = '';
      // Try from post body data attribute (most reliable)
      final dataAttr = postBody.attributes['data-ext_link_data'];
      if (dataAttr != null) {
        final topicMatch = RegExp(r'"t":(\d+)').firstMatch(dataAttr);
        topicId = topicMatch?.group(1) ?? '';
      }
      // Try from URL in title link
      if (topicId.isEmpty) {
        final titleLink = titleElement.querySelector('a[href*="viewtopic.php?t="]');
        if (titleLink != null) {
          topicId = _extractTopicIdFromUrl(titleLink.attributes['href'] ?? '');
        }
      }
      // Try from magnet link data-topic_id attribute
      if (topicId.isEmpty) {
        final magnetElement = document.querySelector(_magnetLinkSelector);
        if (magnetElement != null) {
          final dataTopicId = magnetElement.attributes['data-topic_id'];
          if (dataTopicId != null && dataTopicId.isNotEmpty) {
            topicId = dataTopicId;
          }
        }
      }
      // Fallback to extracting from document
      if (topicId.isEmpty) {
        topicId = _extractTopicIdFromUrl(document.documentElement?.outerHtml ?? '');
      }

      // Extract category from breadcrumb navigation (preferred) or post body metadata or title
      var category = _extractCategoryFromBreadcrumb(document.documentElement!);
      if (category.isEmpty) {
        category = _extractCategoryFromPostBody(postBody);
      }
      if (category.isEmpty) {
        category = _extractCategoryFromTitle(titleElement.text);
      }

      return Audiobook(
        id: topicId,
        title: titleElement.text.trim(),
        author: authorName ?? 'Unknown',
        category: category,
        size: sizeText ?? '0 MB',
        seeders: seeders,
        leechers: leechers,
        magnetUrl: magnetUrl ?? '',
        coverUrl: coverUrl,
        chapters: chapters,
        addedDate: registeredDate ?? _extractDateFromPost(postBody),
      );
    } on ParsingFailure {
      // Re-throw parsing failures as-is
      rethrow;
    } on FormatException catch (e) {
      throw ParsingFailure(
        'Failed to decode HTML content. Encoding issue: ${e.message}',
        e,
      );
    } on Exception catch (e) {
      throw ParsingFailure(
        'Failed to parse topic details: ${e.toString()}',
        e,
      );
    }
  }
}

// Helper methods for parsing
String _extractInfoHashFromUrl(String? url) {
  if (url == null) return '';
  // Try to extract info hash from magnet link
  if (url.startsWith('magnet:')) {
    final hashMatch = RegExp(r'btih:([A-F0-9]+)', caseSensitive: false)
        .firstMatch(url);
    return hashMatch?.group(1) ?? '';
  }
  // Extract topic ID from dl.php?t=XXX
  final match = RegExp(r't=(\d+)').firstMatch(url);
  return match?.group(1) ?? '';
}

/// Extracts category from post body metadata.
String _extractCategoryFromPostBody(Element postBody) {
  // Try to find structured metadata with span.post-b containing "Категория"
  final allPostB = postBody.querySelectorAll('span.post-b');
  for (final element in allPostB) {
    final text = element.text.toLowerCase();
    if (text.contains('категория')) {
      // Get the parent element text to find the value after "Категория:"
      final parentText = element.parent?.text ?? '';
      final categoryMatch = RegExp(r'Категория[:\s]*([^\n<]+)')
          .firstMatch(parentText);
      if (categoryMatch != null) {
        final category = categoryMatch.group(1)?.trim() ?? '';
        if (category.isNotEmpty) {
          return category;
        }
      }
    }
  }
  return '';
}

String _extractCategoryFromTitle(String title) {
  if (title.toLowerCase().contains('радиоспектакль')) return 'Радиоспектакль';
  if (title.toLowerCase().contains('аудиокнига')) return 'Аудиокнига';
  if (title.toLowerCase().contains('биография')) return 'Биография';
  if (title.toLowerCase().contains('мемуары')) return 'Мемуары';
  if (title.toLowerCase().contains('история')) return 'История';
  return 'Другое';
}

String? _extractCoverUrl(Element row) {
  final imgElement = row.querySelector('img[src*="static.rutracker"]');
  return imgElement?.attributes['src'];
}

DateTime _extractDateFromRow(Element row) {
  // Try multiple selectors for date element
  final dateElement = row.querySelector('.small') ??
      row.querySelector('td.small') ??
      row.querySelector('span.small');
  
  if (dateElement != null) {
    try {
      final dateText = dateElement.text.trim();
      
      // Pattern 1: "ДД-МММ-ГГ" format (e.g., "08-Июн-07")
      var dateMatch = RegExp(r'(\d{2})-(\w{3})-(\d{2})').firstMatch(dateText);
      if (dateMatch != null) {
        final day = int.parse(dateMatch.group(1)!);
        final month = _monthToNumber(dateMatch.group(2)!);
        final yearTwoDigits = int.parse(dateMatch.group(3)!);
        // If year >= 50, assume 1900s (1950-1999), otherwise 2000s (2000-2049)
        final year = yearTwoDigits >= 50 
            ? 1900 + yearTwoDigits 
            : 2000 + yearTwoDigits;
        return DateTime(year, month, day);
      }
      
      // Pattern 2: "ДД.ММ.ГГГГ" format (e.g., "08.06.2007")
      dateMatch = RegExp(r'(\d{2})\.(\d{2})\.(\d{4})').firstMatch(dateText);
      if (dateMatch != null) {
        final day = int.parse(dateMatch.group(1)!);
        final month = int.parse(dateMatch.group(2)!);
        final year = int.parse(dateMatch.group(3)!);
        return DateTime(year, month, day);
      }
      
      // Pattern 3: "ДД.ММ.ГГ" format (e.g., "08.06.07")
      dateMatch = RegExp(r'(\d{2})\.(\d{2})\.(\d{2})').firstMatch(dateText);
      if (dateMatch != null) {
        final day = int.parse(dateMatch.group(1)!);
        final month = int.parse(dateMatch.group(2)!);
        final yearTwoDigits = int.parse(dateMatch.group(3)!);
        final year = yearTwoDigits >= 50 
            ? 1900 + yearTwoDigits 
            : 2000 + yearTwoDigits;
        return DateTime(year, month, day);
      }
    } on Exception {
      // Fallback to current date
    }
  }
  
  // Try to extract from row text as fallback
  final rowText = row.text;
  final dateMatch = RegExp(r'(\d{2})-(\w{3})-(\d{2})').firstMatch(rowText);
  if (dateMatch != null) {
    try {
      final day = int.parse(dateMatch.group(1)!);
      final month = _monthToNumber(dateMatch.group(2)!);
      final yearTwoDigits = int.parse(dateMatch.group(3)!);
      final year = yearTwoDigits >= 50 
          ? 1900 + yearTwoDigits 
          : 2000 + yearTwoDigits;
      return DateTime(year, month, day);
    } on Exception {
      // Fallback
    }
  }
  
  return DateTime.now();
}

DateTime _extractDateFromPost(Element postBody) {
  // Try to extract from "Добавлено: ДД.ММ.ГГГГ" format
  final dateMatch =
      RegExp(r'Добавлено[:\s]*(\d{2}\.\d{2}\.\d{4})').firstMatch(postBody.text);
  if (dateMatch != null) {
    try {
      final parts = dateMatch.group(1)!.split('.');
      return DateTime.parse('${parts[2]}-${parts[1]}-${parts[0]}');
    } on Exception {
      // Fallback
    }
  }
  
  // Try to extract from attach table format in post body text
  final attachDateMatch = RegExp(r'(\d{2})-(\w{3})-(\d{2})(?:\s+(\d{2}):(\d{2}))?')
      .firstMatch(postBody.text);
  if (attachDateMatch != null) {
    final parsedDate = _parseAttachTableDate(
      attachDateMatch.group(1)!,
      attachDateMatch.group(2)!,
      attachDateMatch.group(3)!,
      attachDateMatch.group(4),
      attachDateMatch.group(5),
    );
    if (parsedDate != null) {
      return parsedDate;
    }
  }
  
  return DateTime.now();
}

/// Parses date from attach table format "ДД-МММ-ГГ ЧЧ:ММ" or "ДД-МММ-ГГ".
///
/// Handles year conversion: if year >= 50 → 1900s (1950-1999), else → 2000s (2000-2049).
DateTime? _parseAttachTableDate(
  String dayStr,
  String monthStr,
  String yearStr,
  String? hourStr,
  String? minuteStr,
) {
  try {
    final day = int.parse(dayStr);
    final month = _monthToNumber(monthStr);
    final yearTwoDigits = int.parse(yearStr);
    // If year >= 50, assume 1900s (1950-1999), otherwise 2000s (2000-2049)
    final year = yearTwoDigits >= 50 
        ? 1900 + yearTwoDigits 
        : 2000 + yearTwoDigits;
    
    if (hourStr != null && minuteStr != null) {
      final hour = int.parse(hourStr);
      final minute = int.parse(minuteStr);
      return DateTime(year, month, day, hour, minute);
    } else {
      return DateTime(year, month, day);
    }
  } on Exception {
    return null;
  }
}

/// Creates a Chapter from title and duration string.
Chapter _createChapterFromDuration(String title, String duration) {
  final durationParts = duration.split(':');
  var durationMs = 0;
  if (durationParts.length == 2) {
    durationMs =
        (int.parse(durationParts[0]) * 60 + int.parse(durationParts[1])) *
            1000;
  } else if (durationParts.length == 3) {
    durationMs = (int.parse(durationParts[0]) * 3600 +
            int.parse(durationParts[1]) * 60 +
            int.parse(durationParts[2])) *
        1000;
  }

  return Chapter(
    title: title,
    durationMs: durationMs,
    fileIndex: 0,
    startByte: 0,
    endByte: 0,
  );
}

String _extractTopicIdFromUrl(String url) {
  final match = RegExp(r't=(\d+)').firstMatch(url);
  return match?.group(1) ?? '';
}

String _extractTopicIdFromAny(Element row) {
  // Try hrefs within row
  final link = row.querySelector('a[href*="t="]');
  if (link != null) {
    final href = link.attributes['href'] ?? '';
    final m = RegExp(r't=(\d+)').firstMatch(href);
    if (m != null) return m.group(1) ?? '';
  }
  return '';
}

int _monthToNumber(String month) {
  const months = {
    'янв': 1,
    'фев': 2,
    'мар': 3,
    'апр': 4,
    'май': 5,
    'июн': 6,
    'июл': 7,
    'авг': 8,
    'сен': 9,
    'окт': 10,
    'ноя': 11,
    'дек': 12
  };
  return months[month.toLowerCase()] ?? 1;
}

/// Extracts all structured metadata from span.post-b elements.
///
/// This method iterates through all span.post-b elements in the post body
/// and extracts key-value pairs following the pattern "Ключ: Значение".
/// It handles various formats including:
/// - "Ключ: Значение"
/// - "Ключ Значение"
/// - Multi-line values
Map<String, String> _extractAllMetadata(Element postBody) {
  final metadata = <String, String>{};
  final allPostB = postBody.querySelectorAll('span.post-b');
  
  for (final element in allPostB) {
    final key = element.text.trim();
    if (key.isEmpty) continue;
    
    // Get parent element to find the value after the key
    final parent = element.parent;
    if (parent == null) continue;
    
    // Try to find value in the same line or next line
    // Look in parent's text first
    final parentText = parent.text;
    
    // Pattern 1: "Ключ: Значение" or "Ключ Значение" on the same line
    final match1 = RegExp('${RegExp.escape(key)}[:\\s]+([^\\n<]+)')
        .firstMatch(parentText);
    if (match1 != null) {
      final value = match1.group(1)?.trim() ?? '';
      if (value.isNotEmpty && !value.contains(key)) {
        metadata[key] = value;
        continue;
      }
    }
    
    // Pattern 2: Look for value in the next sibling element
    final nextSibling = element.nextElementSibling;
    if (nextSibling != null) {
      final siblingText = nextSibling.text.trim();
      if (siblingText.isNotEmpty && !siblingText.contains(key)) {
        metadata[key] = siblingText;
        continue;
      }
    }
    
    // Pattern 3: Look for value after the key in parent's innerHTML
    final parentHtml = parent.innerHtml;
    final match2 = RegExp('${RegExp.escape(key)}[:\\s]*([^<\\n]+)')
        .firstMatch(parentHtml);
    if (match2 != null) {
      final value = match2.group(1)?.trim() ?? '';
      // Remove HTML tags from value
      final cleanValue = value.replaceAll(RegExp(r'<[^>]+>'), '').trim();
      if (cleanValue.isNotEmpty && !cleanValue.contains(key)) {
        metadata[key] = cleanValue;
      }
    }
  }
  
  return metadata;
}

/// Extracts cover image URL with improved logic.
///
/// This method tries multiple sources in priority order:
/// 1. var.postImg with title attribute (contains full URL)
/// 2. var.postImgAligned with title attribute
/// 3. img elements with src containing static.rutracker or fastpic
String? _extractCoverUrlImproved(Element postBody, Element document) {
  // Priority 1: var.postImg with title attribute (contains full URL)
  final postImgVar = postBody.querySelector(
    'var.postImg[title], var.postImgAligned[title]'
  );
  if (postImgVar != null) {
    final title = postImgVar.attributes['title'];
    if (title != null && title.isNotEmpty) {
      return title;
    }
  }
  
  // Priority 2: var.postImg with title containing fastpic or rutracker
  final postImgFastpic = postBody.querySelector(
    'var.postImg[title*="fastpic"], var.postImg[title*="rutracker"], '
    'var.postImgAligned[title*="fastpic"], var.postImgAligned[title*="rutracker"]'
  );
  if (postImgFastpic != null) {
    final title = postImgFastpic.attributes['title'];
    if (title != null && title.isNotEmpty) {
      return title;
    }
  }
  
  // Priority 3: img with src containing static.rutracker or fastpic
  final imgElement = document.querySelector(
    'img[src*="static.rutracker"], img[src*="fastpic"], img.postimg'
  );
  return imgElement?.attributes['src'];
}

/// Extracts category from breadcrumb navigation.
///
/// This method looks for the breadcrumb navigation element and extracts
/// the last link which typically represents the category.
String _extractCategoryFromBreadcrumb(Element document) {
  final breadcrumb = document.querySelector('td.nav.t-breadcrumb-top');
  if (breadcrumb != null) {
    final links = breadcrumb.querySelectorAll('a');
    if (links.length >= 2) {
      // Last link usually represents the category
      return links.last.text.trim();
    }
  }
  return '';
}

/// Parses relative date strings like "4 года", "28 дней", etc.
///
/// This method attempts to parse relative dates and convert them to
/// approximate DateTime values. For absolute dates, it tries to parse
/// formats like "ДД-МММ-ГГ ЧЧ:ММ".
///
/// Note: This function is reserved for future use when the Audiobook model
/// is extended to include registeredDate field.
// ignore: unused_element
DateTime? _parseRelativeDate(String dateText) {
  if (dateText.isEmpty) return null;
  
  // Try to parse absolute date format: "27-Окт-21 11:06" or "27-Окт-99 11:06"
  final absoluteMatch = RegExp(r'(\d{2})-(\w{3})-(\d{2})\s+(\d{2}):(\d{2})')
      .firstMatch(dateText);
  if (absoluteMatch != null) {
    try {
      final day = int.parse(absoluteMatch.group(1)!);
      final month = _monthToNumber(absoluteMatch.group(2)!);
      final yearTwoDigits = int.parse(absoluteMatch.group(3)!);
      // If year >= 50, assume 1900s (1950-1999), otherwise 2000s (2000-2049)
      final year = yearTwoDigits >= 50 
          ? 1900 + yearTwoDigits 
          : 2000 + yearTwoDigits;
      final hour = int.parse(absoluteMatch.group(4)!);
      final minute = int.parse(absoluteMatch.group(5)!);
      return DateTime(year, month, day, hour, minute);
    } on Exception {
      // Fallback to date only
      try {
        final day = int.parse(absoluteMatch.group(1)!);
        final month = _monthToNumber(absoluteMatch.group(2)!);
        final yearTwoDigits = int.parse(absoluteMatch.group(3)!);
        // If year >= 50, assume 1900s (1950-1999), otherwise 2000s (2000-2049)
        final year = yearTwoDigits >= 50 
            ? 1900 + yearTwoDigits 
            : 2000 + yearTwoDigits;
        return DateTime(year, month, day);
      } on Exception {
        return null;
      }
    }
  }
  
  // Try to parse relative dates
  final now = DateTime.now();
  final yearMatch = RegExp(r'(\d+)\s*год').firstMatch(dateText);
  if (yearMatch != null) {
    final years = int.tryParse(yearMatch.group(1) ?? '0') ?? 0;
    return now.subtract(Duration(days: years * 365));
  }
  
  final monthMatch = RegExp(r'(\d+)\s*месяц').firstMatch(dateText);
  if (monthMatch != null) {
    final months = int.tryParse(monthMatch.group(1) ?? '0') ?? 0;
    return now.subtract(Duration(days: months * 30));
  }
  
  final dayMatch = RegExp(r'(\d+)\s*дн').firstMatch(dateText);
  if (dayMatch != null) {
    final days = int.tryParse(dayMatch.group(1) ?? '0') ?? 0;
    return now.subtract(Duration(days: days));
  }
  
  return null;
}
