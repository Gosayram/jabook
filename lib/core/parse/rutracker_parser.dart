import 'dart:convert';

import 'package:html/parser.dart' as parser;
import 'package:jabook/core/errors/failures.dart';
import 'package:windows1251/windows1251.dart';

class Audiobook {

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
  final String id;
  final String title;
  final String author;
  final String category;
  final String size;
  final int seeders;
  final int leechers;
  final String magnetUrl;
  final String? coverUrl;
  final List<Chapter> chapters;
  final DateTime addedDate;
}

class Chapter {

  Chapter({
    required this.title,
    required this.durationMs,
    required this.fileIndex,
    required this.startByte,
    required this.endByte,
  });
  final String title;
  final int durationMs;
  final int fileIndex;
  final int startByte;
  final int endByte;
}

class RuTrackerParser {
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
    } catch (e) {
      throw ParsingFailure('Failed to parse search results: ${e.toString()}');
    }
  }

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
    } catch (e) {
      throw ParsingFailure('Failed to parse topic details: ${e.toString()}');
    }
  }

}