import 'dart:convert';

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
      } on FormatException catch (e) {
        decodedHtml = windows1251.decode(html.codeUnits);
      }

      final document = parser.parse(decodedHtml);
      final results = <Audiobook>[];

      // TODO: Implement actual parsing logic based on RuTracker HTML structure
      // This is a placeholder implementation
      final rows = document.querySelectorAll('tr');
      
      for (final row in rows) {
        // Extract audiobook information from table rows
        // This needs to be adapted to actual RuTracker HTML structure
        final titleElement = row.querySelector('a.title');
        final authorElement = row.querySelector('span.author');
        final sizeElement = row.querySelector('span.size');
        final seedersElement = row.querySelector('span.seeders');
        final leechersElement = row.querySelector('span.leechers');
        final magnetElement = row.querySelector('a[href^="magnet:"]');

        if (titleElement != null && magnetElement != null) {
          final audiobook = Audiobook(
            id: row.attributes['id'] ?? '',
            title: titleElement.text.trim(),
            author: authorElement?.text.trim() ?? 'Unknown',
            category: 'Unknown',
            size: sizeElement?.text.trim() ?? '0 MB',
            seeders: int.tryParse(seedersElement?.text.trim() ?? '0') ?? 0,
            leechers: int.tryParse(leechersElement?.text.trim() ?? '0') ?? 0,
            magnetUrl: magnetElement.attributes['href'] ?? '',
            chapters: [],
            addedDate: DateTime.now(),
          );
          results.add(audiobook);
        }
      }

      return results;
    } on Exception {
      throw ParsingFailure('Failed to parse search results');
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
      } on FormatException catch (e) {
        decodedHtml = windows1251.decode(html.codeUnits);
      }

      final document = parser.parse(decodedHtml);

      // TODO: Implement actual parsing logic based on RuTracker topic HTML structure
      // This is a placeholder implementation
      final titleElement = document.querySelector('h1.title');
      final authorElement = document.querySelector('span.author');
      final coverElement = document.querySelector('img.cover');
      final chaptersElement = document.querySelector('div.chapters');

      if (titleElement == null) {
        return null;
      }

      final chapters = <Chapter>[];
      if (chaptersElement != null) {
        // Parse chapters from the chapters section
        // This needs to be adapted to actual RuTracker HTML structure
        final chapterRows = chaptersElement.querySelectorAll('tr');
        for (final row in chapterRows) {
          final title = row.querySelector('td.title')?.text.trim() ?? '';
          final duration = row.querySelector('td.duration')?.text.trim() ?? '0:00';
          final fileIndex = int.tryParse(row.querySelector('td.file')?.text.trim() ?? '0') ?? 0;
          
          // Parse duration (e.g., "1:23:45" to milliseconds)
          final durationParts = duration.split(':');
          var durationMs = 0;
          if (durationParts.length == 3) {
            durationMs = (int.parse(durationParts[0]) * 3600 +
                         int.parse(durationParts[1]) * 60 +
                         int.parse(durationParts[2])) * 1000;
          }

          chapters.add(Chapter(
            title: title,
            durationMs: durationMs,
            fileIndex: fileIndex,
            startByte: 0,
            endByte: 0,
          ));
        }
      }

      return Audiobook(
        id: document.querySelector('meta[name="topic-id"]')?.attributes['content'] ?? '',
        title: titleElement.text.trim(),
        author: authorElement?.text.trim() ?? 'Unknown',
        category: 'Unknown',
        size: '0 MB',
        seeders: 0,
        leechers: 0,
        magnetUrl: '',
        coverUrl: coverElement?.attributes['src'],
        chapters: chapters,
        addedDate: DateTime.now(),
      );
    } on Exception {
      throw ParsingFailure('Failed to parse topic details');
    }
  }
}

/// A failure that occurs during parsing operations.
///
/// This exception is thrown when parsing operations fail,
/// such as when parsing HTML content from RuTracker pages.
class ParsingFailure extends Failure {
  /// Creates a new ParsingFailure instance.
  ///
  /// The [message] parameter describes the parsing-related failure.
  /// The optional [exception] parameter contains the original exception
  /// that caused this failure, if any.
  const ParsingFailure(super.message, [super.exception]);
}