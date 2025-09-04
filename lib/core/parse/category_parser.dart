import 'dart:convert';

import 'package:html/dom.dart';
import 'package:html/parser.dart' as parser;
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


      // Find the specific audiobooks category (c=33)
      final audiobooksCategory = document.querySelector('#c-33');
      if (audiobooksCategory != null) {
        // Extract forums from the audiobooks category table
        final forumTable = audiobooksCategory.querySelector('table.forums');
        if (forumTable != null) {
          final rows = forumTable.querySelectorAll('tr');
          for (final row in rows) {
            final forumLink = row.querySelector('h4.forumlink a');
            if (forumLink != null) {
              final forumName = forumLink.text.trim();
              final forumUrl = forumLink.attributes['href'] ?? '';
              final forumId = _extractForumId(forumUrl);

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
      }

      return categories;
    } on Exception {
      throw const ParsingFailure('Failed to parse categories');
    }
  }

  /// Parses subcategories from a category row.
  Future<List<AudiobookCategory>> _parseSubcategories(Element row) async {
    final subcategories = <AudiobookCategory>[];
    
    // Look for subcategory links (usually in nested tables or lists)
    final subcategoryLinks = row.querySelectorAll('a.subforum');
    for (final link in subcategoryLinks) {
      final name = link.text.trim();
      final url = link.attributes['href'] ?? '';
      final id = _extractCategoryId(url);

      if (id.isNotEmpty && !_shouldIgnoreCategory(name)) {
        subcategories.add(AudiobookCategory(
          id: id,
          name: name,
          url: url,
        ));
      }
    }

    return subcategories;
  }

  /// Extracts category ID from URL.
  String _extractCategoryId(String url) {
    final regex = RegExp(r'c=(\d+)');
    final match = regex.firstMatch(url);
    return match?.group(1) ?? '';
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

      // Find topic rows in the forum table
      final topicRows = document.querySelectorAll('tr:has(.torTopic)');
      
      for (final row in topicRows) {
        final topicLink = row.querySelector('a.torTopic');
        final authorLink = row.querySelector('a[href*="profile.php"]');
        final sizeElement = row.querySelector('td:has(.small)');
        final seedersElement = row.querySelector('td.seedmed, td.seedmed b');
        final leechersElement = row.querySelector('td.leechmed, td.leechmed b');

        if (topicLink != null) {
          final topic = {
            'title': topicLink.text.trim(),
            'url': topicLink.attributes['href'] ?? '',
            'author': authorLink?.text.trim() ?? 'Unknown',
            'size': sizeElement?.text.trim() ?? '',
            'seeders': int.tryParse(seedersElement?.text.trim() ?? '0') ?? 0,
            'leechers': int.tryParse(leechersElement?.text.trim() ?? '0') ?? 0,
            'id': _extractTopicId(topicLink.attributes['href'] ?? ''),
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