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
  Future<List<Audiobook>> parseSearchResults(String html) async {
    try {
      // Try UTF-8 first, fallback to cp1251
      String decodedHtml;
      try {
        decodedHtml = utf8.decode(html.codeUnits);
      } on FormatException {
        decodedHtml = windows1251.decode(html.codeUnits);
      }

      final document = parser.parse(decodedHtml);
      final results = <Audiobook>[];

      // Parse actual RuTracker topic rows structure
      final topicRows = document.querySelectorAll('tr.hl-tr');
      
      for (final row in topicRows) {
        final topicId = row.attributes['data-topic_id'];
        final titleElement = row.querySelector('a.torTopic'); // Right selector
        final authorElement = row.querySelector('a.pmed'); // Right selector
        final sizeElement = row.querySelector('span.small'); // Right selector
        final seedersElement = row.querySelector('span.seedmed'); // Right selector
        final leechersElement = row.querySelector('span.leechmed'); // Right selector
        final magnetElement = row.querySelector('a[href^="dl.php?t="]');

        if (titleElement != null && topicId != null) {
          // Extract size from download link text (e.g., "40 MB")
          final sizeText = sizeElement?.text.trim() ?? '0 MB';
          
          // Extract magnet URL from download link
          final magnetUrl = magnetElement != null
              ? 'magnet:?xt=urn:btih:${_extractInfoHashFromUrl(magnetElement.attributes['href'])}'
              : '';

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
      }

      return results;
    } on Exception {
      throw const ParsingFailure('Failed to parse search results');
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
  Future<Audiobook?> parseTopicDetails(String html) async {
    try {
      // Try UTF-8 first, fallback to cp1251
      String decodedHtml;
      try {
        decodedHtml = utf8.decode(html.codeUnits);
      } on FormatException {
        decodedHtml = windows1251.decode(html.codeUnits);
      }

      final document = parser.parse(decodedHtml);

      // Parse actual RuTracker topic page structure
      final titleElement = document.querySelector('h1.maintitle');
      final postBody = document.querySelector('.post-body');
      
      if (titleElement == null || postBody == null) {
        return null;
      }

      // Extract metadata from post content
      final authorElement = postBody.querySelector('a[href*="profile.php"]');
      final sizeMatch = RegExp(r'Размер[:\s]*([\d.,]+\s*[KMGT]?B)').firstMatch(postBody.text);
      final seedersMatch = RegExp(r'Сиды[:\s]*(\d+)').firstMatch(postBody.text);
      final leechersMatch = RegExp(r'Личи[:\s]*(\d+)').firstMatch(postBody.text);
      
      // Extract magnet link from download buttons
      final magnetElement = document.querySelector('a[href^="dl.php?t="]');
      final coverElement = document.querySelector('img.postimg');

      final chapters = <Chapter>[];
      // Try to parse chapters from description (common pattern)
      final chapterMatches = RegExp(r'(\d+[.:]\s*[^\n]+?)\s*\(?(\d+:\d+(?::\d+)?)\)?').allMatches(postBody.text);
      for (final match in chapterMatches) {
        final title = match.group(1)?.trim() ?? '';
        final duration = match.group(2)?.trim() ?? '0:00';
        
        final durationParts = duration.split(':');
        var durationMs = 0;
        if (durationParts.length == 2) {
          durationMs = (int.parse(durationParts[0]) * 60 + int.parse(durationParts[1])) * 1000;
        } else if (durationParts.length == 3) {
          durationMs = (int.parse(durationParts[0]) * 3600 +
                  int.parse(durationParts[1]) * 60 +
                  int.parse(durationParts[2])) *
              1000;
        }

        chapters.add(Chapter(
          title: title,
          durationMs: durationMs,
          fileIndex: 0,
          startByte: 0,
          endByte: 0,
        ));
      }

      return Audiobook(
        id: _extractTopicIdFromUrl(document.documentElement?.outerHtml ?? ''),
        title: titleElement.text.trim(),
        author: authorElement?.text.trim() ?? 'Unknown',
        category: _extractCategoryFromTitle(titleElement.text),
        size: sizeMatch?.group(1)?.trim() ?? '0 MB',
        seeders: int.tryParse(seedersMatch?.group(1) ?? '0') ?? 0,
        leechers: int.tryParse(leechersMatch?.group(1) ?? '0') ?? 0,
        magnetUrl: magnetElement != null
            ? 'magnet:?xt=urn:btih:${_extractInfoHashFromUrl(magnetElement.attributes['href'])}'
            : '',
        coverUrl: coverElement?.attributes['src'],
        chapters: chapters,
        addedDate: _extractDateFromPost(postBody),
      );
    } on Exception {
      throw const ParsingFailure('Failed to parse topic details');
    }
  }
}

// Helper methods for parsing
String _extractInfoHashFromUrl(String? url) {
  if (url == null) return '';
  final match = RegExp(r't=(\d+)').firstMatch(url);
  return match?.group(1) ?? '';
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

DateTime _extractDateFromPost(Element postBody) {
  final dateMatch = RegExp(r'Добавлено[:\s]*(\d{2}\.\d{2}\.\d{4})').firstMatch(postBody.text);
  if (dateMatch != null) {
    try {
      final parts = dateMatch.group(1)!.split('.');
      return DateTime.parse('${parts[2]}-${parts[1]}-${parts[0]}');
    } on Exception {
      // Fallback
    }
  }
  return DateTime.now();
}

String _extractTopicIdFromUrl(String url) {
  final match = RegExp(r't=(\d+)').firstMatch(url);
  return match?.group(1) ?? '';
}

int _monthToNumber(String month) {
  const months = {
    'янв': 1, 'фев': 2, 'мар': 3, 'апр': 4, 'май': 5, 'июн': 6,
    'июл': 7, 'авг': 8, 'сен': 9, 'окт': 10, 'ноя': 11, 'дек': 12
  };
  return months[month.toLowerCase()] ?? 1;
}