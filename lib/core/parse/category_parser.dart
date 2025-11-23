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
import 'package:jabook/core/constants/category_constants.dart';
import 'package:jabook/core/errors/failures.dart';
import 'package:jabook/core/logging/environment_logger.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:windows1251/windows1251.dart';

/// Represents a category of audiobooks on RuTracker.
class AudiobookCategory {
  /// Creates a new AudiobookCategory instance.
  AudiobookCategory({
    required this.id,
    required this.name,
    required this.url,
    this.subcategories = const [],
  });

  /// Unique identifier for the category.
  final String id;

  /// Name of the category.
  final String name;

  /// URL to the category page.
  final String url;

  /// List of subcategories within this category.
  final List<AudiobookCategory> subcategories;
}

/// Parser for extracting audiobook categories from RuTracker forum structure.
///
/// This class provides methods to parse the category structure from RuTracker
/// forum pages, specifically focusing on the audiobooks section (c=33).
class CategoryParser {
  // Centralized selectors
  static const String _audiobooksRootSelectorPrefix = '#c-';
  static const String _forumRowSelector = 'tr[id^="f-"]';
  static const String _forumLinkSelector = 'h4.forumlink a';
  static const String _subforumsSelector = '.subforums';
  static const String _topicRowSelector = 'tr.hl-tr';
  static const String _topicAuthorSelector =
      '.topicAuthor, .topicAuthor a, a.pmed';
  // Use all possible seeders classes: seed and seedmed (both with and without b tag)
  static const String _seedersSelector =
      'span.seed, span.seed b, span.seedmed, span.seedmed b';
  // Use all possible leechers classes: leech and leechmed (both with and without b tag)
  static const String _leechersSelector =
      'span.leech, span.leech b, span.leechmed, span.leechmed b';
  static const String _downloadsSelector = 'p.med[title*="Торрент скачан"] b';
  static const String _sizeSelector = 'span.small, a.f-dl.dl-stub';

  /// Parses the main audiobooks categories page from RuTracker.
  ///
  /// This method extracts categories and subcategories from the forum structure,
  /// ignoring unwanted sections like news, announcements, and general information.
  ///
  /// The [html] parameter contains the HTML content of the forum page.
  ///
  /// Returns a list of [AudiobookCategory] objects representing the valid categories.
  ///
  /// Throws [ParsingFailure] if the HTML cannot be parsed.
  Future<List<AudiobookCategory>> parseCategories(String html) async {
    try {
      // Try UTF-8 first, fallback to cp1251
      String decodedHtml;
      try {
        decodedHtml = utf8.decode(html.codeUnits);
      } on FormatException {
        decodedHtml = windows1251.decode(html.codeUnits);
      }

      final document = parser.parse(decodedHtml);
      final categories = <AudiobookCategory>[];

      // Find audiobooks category (c=33) in the main index page structure
      final audiobooksCategory = document.querySelector(
          '$_audiobooksRootSelectorPrefix${CategoryConstants.audiobooksCategoryId}');
      if (audiobooksCategory != null) {
        // Extract forums from the category table
        final forumRows =
            audiobooksCategory.querySelectorAll(_forumRowSelector);

        for (final row in forumRows) {
          final forumLink = row.querySelector(_forumLinkSelector);
          if (forumLink != null) {
            final forumName = forumLink.text.trim();
            final forumUrl = forumLink.attributes['href'] ?? '';
            final forumId = row.id.replaceFirst('f-', '');

            // Skip unwanted forums (news, announcements, discussions)
            if (forumId.isNotEmpty && !_shouldIgnoreForum(forumName)) {
              categories.add(AudiobookCategory(
                id: forumId,
                name: forumName,
                url: forumUrl,
                subcategories: await _parseSubcategories(row),
              ));
            }
          }
        }
      }

      return categories;
    } on Exception {
      throw const ParsingFailure('Failed to parse categories');
    }
  }

  /// Parses subcategories from a category row.
  Future<List<AudiobookCategory>> _parseSubcategories(Element row) async {
    final subcategories = <AudiobookCategory>[];

    // Look for subcategory links in the subforums section
    final subforumsElement = row.querySelector(_subforumsSelector);
    if (subforumsElement != null) {
      final subforumLinks = subforumsElement.querySelectorAll('a');

      for (final link in subforumLinks) {
        final name = link.text.trim();
        final url = link.attributes['href'] ?? '';
        final id = _extractForumId(url);

        if (id.isNotEmpty && !_shouldIgnoreCategory(name)) {
          subcategories.add(AudiobookCategory(
            id: id,
            name: name,
            url: url,
          ));
        }
      }
    }

    return subcategories;
  }

  /// Extracts forum ID from URL.
  String _extractForumId(String url) {
    final regex = RegExp(r'f=(\d+)');
    final match = regex.firstMatch(url);
    return match?.group(1) ?? '';
  }

  /// Determines if a category should be ignored based on its name.
  bool _shouldIgnoreCategory(String categoryName) {
    final lowerName = categoryName.toLowerCase();

    return lowerName.contains('новости') ||
        lowerName.contains('объявления') ||
        lowerName.contains('полезная информация') ||
        lowerName.contains('обсуждение') ||
        lowerName.contains('технический') ||
        lowerName.contains('флудильня') ||
        lowerName.contains('оффтоп') ||
        lowerName.contains('помощь') ||
        lowerName.contains('правила');
  }

  /// Determines if a forum should be ignored based on its name.
  bool _shouldIgnoreForum(String forumName) {
    final lowerName = forumName.toLowerCase();

    return lowerName.contains('новости') ||
        lowerName.contains('объявления') ||
        lowerName.contains('полезная информация') ||
        lowerName.contains('обсуждение') ||
        lowerName.contains('общение') ||
        lowerName.contains('предложения') ||
        lowerName.contains('поиск') ||
        lowerName.contains('авторы') ||
        lowerName.contains('исполнители');
  }

  /// Parses audiobook topics from a category page.
  ///
  /// This method extracts audiobook topics from a specific category page,
  /// including their titles, authors, and other metadata.
  Future<List<Map<String, dynamic>>> parseCategoryTopics(String html) async {
    final structuredLogger = StructuredLogger();
    try {
      await structuredLogger.log(
        level: 'debug',
        subsystem: 'category_parser',
        message: 'Starting to parse category topics',
        context: 'parse_category_topics',
        extra: {
          'html_length': html.length,
        },
      );

      String decodedHtml;
      var encoding = 'utf8';
      try {
        decodedHtml = utf8.decode(html.codeUnits);
      } on FormatException {
        decodedHtml = windows1251.decode(html.codeUnits);
        encoding = 'windows1251';
      }

      await structuredLogger.log(
        level: 'debug',
        subsystem: 'category_parser',
        message: 'Decoded HTML',
        context: 'parse_category_topics',
        extra: {
          'encoding': encoding,
          'decoded_length': decodedHtml.length,
        },
      );

      // Check if this is a login page (redirect happened)
      if (decodedHtml.contains('login.php') ||
          decodedHtml.contains('Вход в систему') ||
          decodedHtml.contains('Имя пользователя') ||
          decodedHtml.contains('Пароль') ||
          decodedHtml.contains('form_token')) {
        await structuredLogger.log(
          level: 'warning',
          subsystem: 'category_parser',
          message: 'Detected login page instead of category content',
          context: 'parse_category_topics',
        );
        // This is a login page, return empty list
        return [];
      }

      final document = parser.parse(decodedHtml);
      final topics = <Map<String, dynamic>>[];

      // Find topic rows in the forum table using actual RuTracker structure
      final topicRows = document.querySelectorAll(_topicRowSelector);

      await structuredLogger.log(
        level: 'info',
        subsystem: 'category_parser',
        message: 'Found topic rows',
        context: 'parse_category_topics',
        extra: {
          'topic_rows_count': topicRows.length,
          'selector': _topicRowSelector,
          'html_contains_hl_tr': decodedHtml.contains('hl-tr'),
          'html_contains_vf_table': decodedHtml.contains('vf-table'),
        },
      );

      // If no topic rows found, might be login page or empty category
      if (topicRows.isEmpty) {
        // Try alternative selectors
        final altRows1 = document.querySelectorAll('tr[class*="hl-tr"]');
        final altRows2 = document.querySelectorAll('tr[id^="tr-"]');
        final altRows3 = document.querySelectorAll('table.vf-table tr');

        await structuredLogger.log(
          level: 'warning',
          subsystem: 'category_parser',
          message: 'No topic rows found in category page',
          context: 'parse_category_topics',
          extra: {
            'html_preview': decodedHtml.substring(0, 1000),
            'html_length': decodedHtml.length,
            'alt_selector_hl_tr_count': altRows1.length,
            'alt_selector_tr_id_count': altRows2.length,
            'alt_selector_vf_table_tr_count': altRows3.length,
            'html_contains_hl_tr': decodedHtml.contains('hl-tr'),
            'html_contains_vf_table': decodedHtml.contains('vf-table'),
            'html_contains_forumline': decodedHtml.contains('forumline'),
          },
        );
        return [];
      }

      for (final row in topicRows) {
        // Skip ad rows
        if (row.classes.any((c) => c.contains('banner') || c.contains('ads'))) {
          continue;
        }

        // Skip announcements and sticky topics
        // Check icon to determine if it's an announcement or sticky topic
        final iconElement = row.querySelector('img.topic_icon');
        final iconSrc = iconElement?.attributes['src'] ?? '';
        // Only skip if it's an announcement (folder_announce)
        // Keep sticky topics (folder_sticky) as they are often new releases
        if (iconSrc.contains('folder_announce')) {
          EnvironmentLogger().d('Skipping announcement topic');
          continue; // Skip announcements
        }
        // Note: We keep sticky topics as they are often new releases

        // Try to find topic link - try multiple selectors
        // HTML structure: <a class="torTopic bold tt-text" ...>
        Element? topicLink;
        topicLink = row.querySelector('a.torTopic.tt-text') ??
            row.querySelector('a.torTopic.bold') ??
            row.querySelector('a.torTopic');

        final authorLink = row.querySelector(_topicAuthorSelector);
        final downloadsElement = row.querySelector(_downloadsSelector);

        // First, try to find the tor column (vf-col-tor) which contains size, seeders/leechers
        final torColumn = row.querySelector('td.vf-col-tor');

        // Size element for fallback (if not found in tor column)
        final sizeElement = row.querySelector(_sizeSelector);

        // Extract seeders with comprehensive selector (includes seed, seedmed, with and without b tag)
        // First, try to find within the tor column (most reliable)
        var seeders = 0;
        Element? seedersElement;

        if (torColumn != null) {
          // Look for seeders within the tor column first (most reliable)
          seedersElement = torColumn.querySelector(_seedersSelector);
        }

        // Fallback to searching in entire row if not found in tor column
        seedersElement ??= row.querySelector(_seedersSelector);

        if (seedersElement != null) {
          // Try to extract number from <b> tag first (most reliable)
          final bTag = seedersElement.querySelector('b');
          if (bTag != null) {
            final seedersText = bTag.text.trim();
            seeders = int.tryParse(seedersText) ?? 0;
          } else {
            // Fallback: extract from span text, but remove "Сиды: " prefix if present
            var seedersText = seedersElement.text.trim();
            seedersText =
                seedersText.replaceFirst(RegExp(r'^[Сс]иды[:\s]*'), '');
            seeders = int.tryParse(seedersText) ?? 0;
          }
        } else {
          // Fallback: try to extract from row text using regex
          final seedersMatch =
              RegExp(r'[↑↑]\s*(\d+)|[Сс]иды[:\s]*(\d+)').firstMatch(row.text);
          if (seedersMatch != null) {
            seeders = int.tryParse(
                    seedersMatch.group(1) ?? seedersMatch.group(2) ?? '0') ??
                0;
          }
        }

        // Extract leechers with comprehensive selector (includes leech, leechmed, with and without b tag)
        // First, try to find within the tor column (most reliable)
        var leechers = 0;
        Element? leechersElement;

        if (torColumn != null) {
          // Look for leechers within the tor column first (most reliable)
          leechersElement = torColumn.querySelector(_leechersSelector);
        }

        // Fallback to searching in entire row if not found in tor column
        leechersElement ??= row.querySelector(_leechersSelector);

        if (leechersElement != null) {
          // Try to extract number from <b> tag first (most reliable)
          final bTag = leechersElement.querySelector('b');
          if (bTag != null) {
            final leechersText = bTag.text.trim();
            leechers = int.tryParse(leechersText) ?? 0;
          } else {
            // Fallback: extract from span text, but remove "Личи: " prefix if present
            var leechersText = leechersElement.text.trim();
            leechersText =
                leechersText.replaceFirst(RegExp(r'^[Лл]ичи[:\s]*'), '');
            leechers = int.tryParse(leechersText) ?? 0;
          }
        } else {
          // Fallback: try to extract from row text using regex
          final leechersMatch =
              RegExp(r'[↓↓]\s*(\d+)|[Лл]ичи[:\s]*(\d+)').firstMatch(row.text);
          if (leechersMatch != null) {
            leechers = int.tryParse(
                    leechersMatch.group(1) ?? leechersMatch.group(2) ?? '0') ??
                0;
          }
        }

        // Verify values are not swapped (seeders should typically be >= leechers for active torrents)
        // If leechers > seeders by a large margin, they might be swapped
        // Auto-fix: swap values if leechers significantly exceeds seeders
        if (leechers > seeders && seeders > 0 && leechers > seeders * 2) {
          // Swap values
          final temp = seeders;
          seeders = leechers;
          leechers = temp;
        }

        // Extract size from multiple possible locations
        // First, try to find size in tor column (vf-col-tor) which contains size info
        var sizeText = '';
        if (torColumn != null) {
          // Look for size within the tor column first (most reliable)
          final torSizeElement = torColumn.querySelector(_sizeSelector);
          if (torSizeElement != null) {
            sizeText = torSizeElement.text.trim();
          }
        }

        // Fallback to original sizeElement if not found in tor column
        if (sizeText.isEmpty && sizeElement != null) {
          sizeText = sizeElement.text.trim();
        } else if (sizeText.isEmpty) {
          // Try to extract from row text using regex
          final sizeMatch =
              RegExp(r'([\d.,]+\s*[KMGT]?B)', caseSensitive: false)
                  .firstMatch(row.text);
          if (sizeMatch != null) {
            sizeText = sizeMatch.group(1)?.trim() ?? '';
          }
        }

        // Debug logging
        if (topicLink == null) {
          EnvironmentLogger().d(
            'No topic link found in row. Row HTML preview: ${row.outerHtml.substring(0, 200)}...',
          );
        }

        // Only include topics that have torrent data (size, seeders/leechers)
        // This filters out non-torrent topics
        if (topicLink != null &&
            (sizeText.isNotEmpty || seeders > 0 || leechers > 0)) {
          final topicId = row.attributes['data-topic_id'] ??
              _extractTopicId(topicLink.attributes['href'] ?? '');

          // Skip if topic ID is empty
          if (topicId.isEmpty) {
            EnvironmentLogger().w(
              'Skipping topic with empty ID. Title: ${topicLink.text.trim()}',
            );
            continue;
          }

          // Log extracted values for debugging
          EnvironmentLogger().d(
            'Topic $topicId: size=$sizeText, seeders=$seeders, leechers=$leechers',
          );

          // Extract cover URL - try tor column first, then entire row
          String? coverUrl;
          if (torColumn != null) {
            coverUrl = _extractCoverUrl(torColumn);
            if (coverUrl != null) {
              EnvironmentLogger().d(
                'Topic $topicId: extracted cover URL from tor column: $coverUrl',
              );
            }
          }

          // Fallback to searching in entire row
          if (coverUrl == null || coverUrl.isEmpty) {
            coverUrl = _extractCoverUrl(row);
            if (coverUrl != null) {
              EnvironmentLogger().d(
                'Topic $topicId: extracted cover URL from row: $coverUrl',
              );
            } else {
              EnvironmentLogger().d(
                'Topic $topicId: no cover URL found',
              );
            }
          }

          final lastPostDate = _extractDateFromTopicRow(row);
          final topic = {
            'title': topicLink.text.trim(),
            'url': topicLink.attributes['href'] ?? '',
            'author': authorLink?.text.trim() ?? 'Unknown',
            'size': sizeText,
            'seeders': seeders,
            'leechers': leechers,
            'downloads':
                int.tryParse(downloadsElement?.text.trim() ?? '0') ?? 0,
            'id': topicId,
            'added_date': lastPostDate,
            'last_post_date': lastPostDate,
            'coverUrl': coverUrl,
          };
          topics.add(topic);
        } else {
          // Log why topic was skipped
          if (topicLink == null) {
            await structuredLogger.log(
              level: 'debug',
              subsystem: 'category_parser',
              message: 'Skipping row: no topic link found',
              context: 'parse_category_topics',
              extra: {
                'row_html_preview': row.outerHtml.substring(0, 300),
              },
            );
          } else if (sizeElement == null &&
              seedersElement == null &&
              leechersElement == null) {
            await structuredLogger.log(
              level: 'debug',
              subsystem: 'category_parser',
              message: 'Skipping topic: no torrent data',
              context: 'parse_category_topics',
              extra: {
                'topic_title': topicLink.text.trim(),
                'row_html_preview': row.outerHtml.substring(0, 300),
              },
            );
          }
        }
      }

      // Sort topics by last post date (newest first)
      // Topics without valid dates will be placed at the end
      topics.sort((a, b) {
        final dateA = a['last_post_date'] as DateTime?;
        final dateB = b['last_post_date'] as DateTime?;
        if (dateA == null && dateB == null) return 0;
        if (dateA == null) return 1; // Put null dates at the end
        if (dateB == null) return -1; // Put null dates at the end
        return dateB.compareTo(dateA); // Descending order (newest first)
      });

      await structuredLogger.log(
        level: 'info',
        subsystem: 'category_parser',
        message: 'Parsed and sorted valid topics from category page',
        context: 'parse_category_topics',
        extra: {
          'valid_topics_count': topics.length,
          'total_rows': topicRows.length,
        },
      );
      return topics;
    } on Exception catch (e, stackTrace) {
      await structuredLogger.log(
        level: 'error',
        subsystem: 'category_parser',
        message: 'Failed to parse category topics',
        context: 'parse_category_topics',
        extra: {
          'error': e.toString(),
          'stack_trace': stackTrace.toString(),
        },
      );
      throw const ParsingFailure('Failed to parse category topics');
    }
  }

  /// Extracts topic ID from URL.
  String _extractTopicId(String url) {
    final regex = RegExp(r't=(\d+)');
    final match = regex.firstMatch(url);
    return match?.group(1) ?? '';
  }

  /// Extracts cover URL from search result row.
  ///
  /// Tries multiple selectors in priority order to find cover image.
  /// Returns normalized absolute URL or null if not found.
  String? _extractCoverUrl(Element row) {
    final logger = EnvironmentLogger()
      ..d('Extracting cover URL from category topic row');

    // Priority 1: var.postImg.postImgAligned.img-right with title attribute (main cover image)
    // This is the most reliable selector for cover images in RuTracker
    final postImgCover =
        row.querySelector('var.postImg.postImgAligned.img-right[title], '
            'var.postImg.postImgAligned[title].img-right');
    if (postImgCover != null) {
      final title = postImgCover.attributes['title'];
      if (title != null && title.isNotEmpty) {
        final normalizedUrl = _normalizeCoverUrl(title);
        if (normalizedUrl != null) {
          logger.d(
              'Cover URL extracted (Priority 1 - postImg.postImgAligned.img-right): $normalizedUrl');
          return normalizedUrl;
        }
      }
    }

    // Priority 2: var.postImg with title attribute (contains full URL)
    final postImgVar =
        row.querySelector('var.postImg[title], var.postImgAligned[title]');
    if (postImgVar != null) {
      final title = postImgVar.attributes['title'];
      if (title != null && title.isNotEmpty) {
        final normalizedUrl = _normalizeCoverUrl(title);
        if (normalizedUrl != null) {
          logger.d('Cover URL extracted (Priority 2): $normalizedUrl');
          return normalizedUrl;
        }
      }
    }

    // Priority 3: var.postImg with title containing fastpic or rutracker
    final postImgFastpic = row.querySelector(
        'var.postImg[title*="fastpic"], var.postImg[title*="rutracker"], '
        'var.postImgAligned[title*="fastpic"], var.postImgAligned[title*="rutracker"]');
    if (postImgFastpic != null) {
      final title = postImgFastpic.attributes['title'];
      if (title != null && title.isNotEmpty) {
        final normalizedUrl = _normalizeCoverUrl(title);
        if (normalizedUrl != null) {
          logger.d('Cover URL extracted (Priority 3): $normalizedUrl');
          return normalizedUrl;
        }
      }
    }

    // Priority 4: img with src containing static.rutracker or fastpic
    final imgElement = row.querySelector(
        'img[src*="static.rutracker"], img[src*="fastpic"], img.postimg');
    if (imgElement != null) {
      final src = imgElement.attributes['src'];
      if (src != null && src.isNotEmpty) {
        final normalizedUrl = _normalizeCoverUrl(src);
        if (normalizedUrl != null) {
          logger.d('Cover URL extracted (Priority 4): $normalizedUrl');
          return normalizedUrl;
        }
      }
    }

    // Priority 5: img with data-src (lazy loading)
    final imgLazy = row.querySelector('img[data-src]');
    if (imgLazy != null) {
      final dataSrc = imgLazy.attributes['data-src'];
      if (dataSrc != null && dataSrc.isNotEmpty) {
        final normalizedUrl = _normalizeCoverUrl(dataSrc);
        if (normalizedUrl != null) {
          logger.d('Cover URL extracted (Priority 5): $normalizedUrl');
          return normalizedUrl;
        }
      }
    }

    // Priority 6: img with srcset
    final imgSrcset = row.querySelector('img[srcset]');
    if (imgSrcset != null) {
      final srcset = imgSrcset.attributes['srcset'];
      if (srcset != null && srcset.isNotEmpty) {
        // Extract first URL from srcset
        final firstUrl = srcset.split(',').first.trim().split(' ').first;
        if (firstUrl.isNotEmpty) {
          final normalizedUrl = _normalizeCoverUrl(firstUrl);
          if (normalizedUrl != null) {
            logger.d('Cover URL extracted (Priority 6): $normalizedUrl');
            return normalizedUrl;
          }
        }
      }
    }

    logger.d('Cover URL not found in category topic row');
    return null;
  }

  /// Normalizes cover URL to absolute URL.
  ///
  /// Converts relative URLs to absolute URLs using rutracker.org as base.
  String? _normalizeCoverUrl(String? url) {
    if (url == null || url.isEmpty) {
      EnvironmentLogger().d('_normalizeCoverUrl: URL is null or empty');
      return null;
    }

    final logger = EnvironmentLogger()
      ..d('_normalizeCoverUrl: Normalizing URL: $url');

    // If URL already absolute, return as is
    if (url.startsWith('http://') || url.startsWith('https://')) {
      logger.d('_normalizeCoverUrl: URL is already absolute: $url');
      return url;
    }

    // Use rutracker.org as base URL
    const effectiveBaseUrl = 'https://rutracker.org';

    // If URL relative (starts with /), convert to absolute
    if (url.startsWith('/')) {
      final normalized = '$effectiveBaseUrl$url';
      logger.d('_normalizeCoverUrl: Normalized relative URL: $normalized');
      return normalized;
    }

    // If URL starts with //, add https:
    if (url.startsWith('//')) {
      final normalized = 'https:$url';
      logger.d(
          '_normalizeCoverUrl: Normalized protocol-relative URL: $normalized');
      return normalized;
    }

    // If URL doesn't start with /, possibly already full path
    // Try to add base URL
    if (!url.contains('://')) {
      final normalized = '$effectiveBaseUrl/$url';
      logger.d('_normalizeCoverUrl: Normalized path URL: $normalized');
      return normalized;
    }

    logger.w('_normalizeCoverUrl: Could not normalize URL: $url');
    return url;
  }
}

// Helper method to extract date from topic row
// Date is in the "Посл. сообщение" column (vf-col-last-post) in format: yyyy-MM-dd HH:mm
DateTime _extractDateFromTopicRow(Element row) {
  // Find the last post column
  final lastPostColumn = row.querySelector('td.vf-col-last-post');
  if (lastPostColumn != null) {
    // Date is in the first <p> element
    final dateParagraph = lastPostColumn.querySelector('p');
    if (dateParagraph != null) {
      try {
        final dateText = dateParagraph.text.trim();
        // Try to parse format: yyyy-MM-dd HH:mm (e.g., "2025-11-21 20:30")
        final dateMatch = RegExp(r'(\d{4}-\d{2}-\d{2})\s+(\d{2}):(\d{2})')
            .firstMatch(dateText);
        if (dateMatch != null) {
          final datePart = dateMatch.group(1)!;
          final hour = dateMatch.group(2)!;
          final minute = dateMatch.group(3)!;
          return DateTime.parse('$datePart $hour:$minute:00');
        }
        // Fallback: try format without time: yyyy-MM-dd
        final dateOnlyMatch =
            RegExp(r'(\d{4}-\d{2}-\d{2})').firstMatch(dateText);
        if (dateOnlyMatch != null) {
          return DateTime.parse('${dateOnlyMatch.group(1)!} 00:00:00');
        }
      } on Exception {
        // If parsing fails, return very old date so these topics are filtered out
      }
    }
  }
  // If no date found, return very old date (year 2000) so these topics are filtered out
  // This ensures only topics with valid dates are included in recommendations
  return DateTime(2000);
}
