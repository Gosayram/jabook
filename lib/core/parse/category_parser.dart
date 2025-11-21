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
  static const String _topicTitleSelector = 'a.torTopic.tt-text, a.torTopic';
  static const String _topicAuthorSelector =
      '.topicAuthor, .topicAuthor a, a.pmed';
  static const String _topicSizeSelector = 'a.f-dl.dl-stub, span.small';
  static const String _seedersSelector = 'span.seedmed b, span.seedmed';
  static const String _leechersSelector = 'span.leechmed b, span.leechmed';
  static const String _downloadsSelector = 'p.med[title*="Торрент скачан"] b';

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
    try {
      String decodedHtml;
      try {
        decodedHtml = utf8.decode(html.codeUnits);
      } on FormatException {
        decodedHtml = windows1251.decode(html.codeUnits);
      }

      final document = parser.parse(decodedHtml);
      final topics = <Map<String, dynamic>>[];

      // Find topic rows in the forum table using actual RuTracker structure
      final topicRows = document.querySelectorAll(_topicRowSelector);

      for (final row in topicRows) {
        // Skip ad rows
        if (row.classes.any((c) => c.contains('banner') || c.contains('ads'))) {
          continue;
        }

        // Skip announcements and sticky topics
        // Check icon to determine if it's an announcement or sticky topic
        final iconElement = row.querySelector('img.topic_icon');
        final iconSrc = iconElement?.attributes['src'] ?? '';
        if (iconSrc.contains('folder_announce') ||
            iconSrc.contains('folder_sticky')) {
          continue; // Skip announcements and sticky topics
        }

        final topicLink = row.querySelector(_topicTitleSelector);
        final authorLink = row.querySelector(_topicAuthorSelector);
        final sizeElement = row.querySelector(_topicSizeSelector);
        final seedersElement = row.querySelector(_seedersSelector);
        final leechersElement = row.querySelector(_leechersSelector);
        final downloadsElement = row.querySelector(_downloadsSelector);

        // Only include topics that have torrent data (size, seeders/leechers)
        // This filters out non-torrent topics
        if (topicLink != null &&
            (sizeElement != null ||
                seedersElement != null ||
                leechersElement != null)) {
          final topicId = row.attributes['data-topic_id'] ??
              _extractTopicId(topicLink.attributes['href'] ?? '');

          final topic = {
            'title': topicLink.text.trim(),
            'url': topicLink.attributes['href'] ?? '',
            'author': authorLink?.text.trim() ?? 'Unknown',
            'size': sizeElement?.text.trim() ?? '',
            'seeders': int.tryParse(seedersElement?.text.trim() ?? '0') ?? 0,
            'leechers': int.tryParse(leechersElement?.text.trim() ?? '0') ?? 0,
            'downloads':
                int.tryParse(downloadsElement?.text.trim() ?? '0') ?? 0,
            'id': topicId,
            'added_date': _extractDateFromTopicRow(row),
          };
          topics.add(topic);
        }
      }

      return topics;
    } on Exception {
      throw const ParsingFailure('Failed to parse category topics');
    }
  }

  /// Extracts topic ID from URL.
  String _extractTopicId(String url) {
    final regex = RegExp(r't=(\d+)');
    final match = regex.firstMatch(url);
    return match?.group(1) ?? '';
  }
}

// Helper method to extract date from topic row
DateTime _extractDateFromTopicRow(Element row) {
  final dateElement = row.querySelector('.small');
  if (dateElement != null) {
    try {
      final dateText = dateElement.text.trim();
      final dateMatch = RegExp(r'(\d{2}-\w{3}-\d{2})').firstMatch(dateText);
      if (dateMatch != null) {
        return DateTime.parse('20${dateMatch.group(1)!.split('-')[2]}-'
            '${_monthToNumber(dateMatch.group(1)!.split('-')[1])}-'
            '${dateMatch.group(1)!.split('-')[0]}');
      }
    } on Exception {
      // Fallback to current date
    }
  }
  return DateTime.now();
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
