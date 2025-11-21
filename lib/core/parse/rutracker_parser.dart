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
import 'package:jabook/core/logging/environment_logger.dart';
import 'package:jabook/core/logging/structured_logger.dart';
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
    this.performer,
    this.genres = const [],
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

  /// Performer or narrator of the audiobook.
  final String? performer;

  /// List of genres for the audiobook.
  final List<String> genres;

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
  static const String _titleSelector =
      'a.torTopic, a.torTopic.tt-text, a[href*="viewtopic.php?t="]';
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
  /// The [htmlData] parameter contains the HTML content of the search results page.
  /// The [contentType] parameter is the Content-Type header value for encoding detection.
  ///
  /// Returns a list of [Audiobook] objects found in the search results.
  ///
  /// Throws [ParsingFailure] if the HTML cannot be parsed.
  Future<List<Audiobook>> parseSearchResults(
    dynamic htmlData, {
    String? contentType,
    String? baseUrl,
  }) async {
    final logger = StructuredLogger();
    try {
      String decodedHtml;

      // Log initial data type and size for diagnostics
      final dataType = htmlData.runtimeType.toString();
      final dataSize = htmlData is List<int>
          ? htmlData.length
          : (htmlData is String ? htmlData.length : 0);

      await logger.log(
        level: 'debug',
        subsystem: 'parser',
        message: 'Starting HTML decoding',
        context: 'parse_search_results',
        extra: {
          'data_type': dataType,
          'data_size_bytes': dataSize,
          'content_type': contentType ?? 'not_provided',
        },
      );

      // Determine encoding from Content-Type header if available
      String? detectedEncoding;
      if (contentType != null) {
        final charsetMatch = RegExp(r'charset=([^;\s]+)', caseSensitive: false)
            .firstMatch(contentType);
        if (charsetMatch != null) {
          detectedEncoding = charsetMatch.group(1)?.toLowerCase();
        }
      }

      if (htmlData is String) {
        // String data - check if it's already correctly decoded
        // If it contains typical Windows-1251 characters incorrectly decoded as UTF-8,
        // we might need to re-encode, but this is complex, so we'll use as-is for now
        decodedHtml = htmlData;

        await logger.log(
          level: 'debug',
          subsystem: 'parser',
          message: 'HTML data is already a String, using as-is',
          context: 'parse_search_results',
          extra: {
            'string_length': decodedHtml.length,
            'preview': decodedHtml.length > 200
                ? decodedHtml.substring(0, 200)
                : decodedHtml,
          },
        );

        // Check for signs of incorrect encoding (mojibake)
        // If string contains replacement characters or unusual sequences, it might be wrong
        if (decodedHtml.contains('\uFFFD') ||
            (decodedHtml.contains('Р') && decodedHtml.contains('С'))) {
          // Possible encoding issue, but we can't fix it from String
          await logger.log(
            level: 'warning',
            subsystem: 'parser',
            message: 'Possible encoding issue detected in String data',
            context: 'parse_search_results',
            extra: {
              'has_replacement_char': decodedHtml.contains('\uFFFD'),
            },
          );
        }
      } else if (htmlData is List<int>) {
        // Binary data (bytes) - decode based on detected encoding or try both
        // Note: Brotli decompression is handled automatically by DioBrotliTransformer in DioClient
        // These bytes are already decompressed and ready for encoding conversion
        // They need to be decoded from Windows-1251 (RuTracker default)
        final bytes = htmlData;

        // Validate bytes before decoding
        if (bytes.isEmpty) {
          throw const ParsingFailure(
            'Received empty bytes. This may indicate a network error.',
          );
        }

        await logger.log(
          level: 'debug',
          subsystem: 'parser',
          message:
              'Decoding bytes (Brotli already decompressed by Dio transformer)',
          context: 'parse_search_results',
          extra: {
            'bytes_length': bytes.length,
            'detected_encoding': detectedEncoding ?? 'auto-detect',
            'first_bytes_preview': bytes.length > 50
                ? bytes.sublist(0, 50).toString()
                : bytes.toString(),
          },
        );

        if (detectedEncoding != null) {
          // Use detected encoding from Content-Type header
          if (detectedEncoding.contains('windows-1251') ||
              detectedEncoding.contains('cp1251') ||
              detectedEncoding.contains('1251')) {
            try {
              decodedHtml = windows1251.decode(bytes);
              await logger.log(
                level: 'debug',
                subsystem: 'parser',
                message: 'Successfully decoded with Windows-1251',
                context: 'parse_search_results',
                extra: {
                  'decoded_length': decodedHtml.length,
                  'preview': decodedHtml.length > 200
                      ? decodedHtml.substring(0, 200)
                      : decodedHtml,
                },
              );
            } on Exception catch (e) {
              await logger.log(
                level: 'warning',
                subsystem: 'parser',
                message: 'Windows-1251 decoding failed, trying UTF-8',
                context: 'parse_search_results',
                cause: e.toString(),
              );
              // Fallback to UTF-8 if Windows-1251 fails
              try {
                decodedHtml = utf8.decode(bytes);
              } on FormatException catch (e2) {
                await logger.log(
                  level: 'error',
                  subsystem: 'parser',
                  message: 'Both Windows-1251 and UTF-8 decoding failed',
                  context: 'parse_search_results',
                  cause: e2.toString(),
                );
                throw ParsingFailure(
                  'Failed to decode bytes: Windows-1251 failed (${e.toString()}), UTF-8 failed (${e2.toString()})',
                  e2,
                );
              }
            }
          } else if (detectedEncoding.contains('utf-8') ||
              detectedEncoding.contains('utf8')) {
            try {
              decodedHtml = utf8.decode(bytes);
              await logger.log(
                level: 'debug',
                subsystem: 'parser',
                message: 'Successfully decoded with UTF-8',
                context: 'parse_search_results',
                extra: {
                  'decoded_length': decodedHtml.length,
                },
              );
            } on FormatException catch (e) {
              await logger.log(
                level: 'warning',
                subsystem: 'parser',
                message: 'UTF-8 decoding failed, trying Windows-1251',
                context: 'parse_search_results',
                cause: e.toString(),
              );
              // Fallback to Windows-1251 if UTF-8 fails (RuTracker sometimes lies)
              try {
                decodedHtml = windows1251.decode(bytes);
              } on Exception catch (e2) {
                await logger.log(
                  level: 'error',
                  subsystem: 'parser',
                  message: 'Both UTF-8 and Windows-1251 decoding failed',
                  context: 'parse_search_results',
                  cause: e2.toString(),
                );
                throw ParsingFailure(
                  'Failed to decode bytes: UTF-8 failed (${e.toString()}), Windows-1251 failed (${e2.toString()})',
                  e2,
                );
              }
            }
          } else {
            // Unknown encoding, try both
            try {
              decodedHtml = utf8.decode(bytes);
            } on FormatException {
              try {
                decodedHtml = windows1251.decode(bytes);
              } on Exception catch (e) {
                throw ParsingFailure(
                  'Failed to decode bytes with unknown encoding: ${e.toString()}',
                  e,
                );
              }
            }
          }
        } else {
          // No encoding specified - try Windows-1251 first (RuTracker default)
          // then fallback to UTF-8
          try {
            decodedHtml = windows1251.decode(bytes);
            await logger.log(
              level: 'debug',
              subsystem: 'parser',
              message: 'Successfully decoded with Windows-1251 (default)',
              context: 'parse_search_results',
              extra: {
                'decoded_length': decodedHtml.length,
              },
            );
          } on Exception catch (e) {
            await logger.log(
              level: 'warning',
              subsystem: 'parser',
              message: 'Windows-1251 decoding failed, trying UTF-8',
              context: 'parse_search_results',
              cause: e.toString(),
            );
            try {
              decodedHtml = utf8.decode(bytes);
            } on FormatException catch (e2) {
              await logger.log(
                level: 'warning',
                subsystem: 'parser',
                message:
                    'UTF-8 decoding also failed, trying Latin-1 as last resort',
                context: 'parse_search_results',
                cause: e2.toString(),
              );
              // Last resort: try to decode as Latin-1 (never fails)
              decodedHtml = latin1.decode(bytes);
            }
          }
        }
      } else {
        // Fallback: try to convert to string
        await logger.log(
          level: 'warning',
          subsystem: 'parser',
          message: 'Unexpected data type, converting to string',
          context: 'parse_search_results',
          extra: {
            'data_type': dataType,
          },
        );
        decodedHtml = htmlData.toString();
      }

      // Validate decoded HTML before parsing
      if (decodedHtml.isEmpty) {
        throw const ParsingFailure(
          'Decoded HTML is empty. This may indicate a network error or encoding issue.',
        );
      }

      // Check for valid HTML structure
      final hasHtmlStructure = decodedHtml.contains('<html') ||
          decodedHtml.contains('<HTML') ||
          decodedHtml.contains('<body') ||
          decodedHtml.contains('<BODY');

      if (!hasHtmlStructure) {
        await logger.log(
          level: 'error',
          subsystem: 'parser',
          message: 'Decoded text does not appear to be valid HTML',
          context: 'parse_search_results',
          extra: {
            'decoded_length': decodedHtml.length,
            'preview': decodedHtml.length > 500
                ? decodedHtml.substring(0, 500)
                : decodedHtml,
            'has_html_tag': decodedHtml.contains('<html'),
            'has_body_tag': decodedHtml.contains('<body'),
          },
        );
        throw ParsingFailure(
          'Response does not appear to be valid HTML. This may indicate a network error or encoding issue. '
          'Decoded text length: ${decodedHtml.length} bytes.',
        );
      }

      // Check for encoding issues (mojibake) - signs of incorrect decoding
      // If decoded HTML contains replacement characters or unusual sequences, it might be wrong
      final hasEncodingIssues = decodedHtml.contains('\uFFFD') ||
          (decodedHtml.contains('Р') &&
              decodedHtml.contains('С') &&
              decodedHtml.length < 1000);

      if (hasEncodingIssues) {
        await logger.log(
          level: 'warning',
          subsystem: 'parser',
          message: 'Possible encoding issues detected in decoded HTML',
          context: 'parse_search_results',
          extra: {
            'has_replacement_char': decodedHtml.contains('\uFFFD'),
            'has_suspicious_chars':
                decodedHtml.contains('Р') && decodedHtml.contains('С'),
          },
        );
      }

      // Try to parse the document
      Document? document;
      try {
        document = parser.parse(decodedHtml);

        // Validate that parsing produced a valid document
        // Note: parser.parse() never returns null, but we check body for validity
        if (document.body == null) {
          throw const ParsingFailure(
            'HTML parser returned document without body. This may indicate invalid HTML structure.',
          );
        }

        await logger.log(
          level: 'debug',
          subsystem: 'parser',
          message: 'Successfully parsed HTML document',
          context: 'parse_search_results',
          extra: {
            'has_body': document.body != null,
            'has_head': document.head != null,
          },
        );
      } on Exception catch (e) {
        await logger.log(
          level: 'error',
          subsystem: 'parser',
          message: 'Failed to parse HTML document',
          context: 'parse_search_results',
          cause: e.toString(),
          extra: {
            'has_encoding_issues': hasEncodingIssues,
            'html_data_type': htmlData.runtimeType.toString(),
            'decoded_length': decodedHtml.length,
            'html_preview': decodedHtml.length > 1000
                ? decodedHtml.substring(0, 1000)
                : decodedHtml,
          },
        );

        // If parsing fails and we have encoding issues, try to re-encode
        if (hasEncodingIssues && htmlData is List<int>) {
          final bytes = htmlData;
          try {
            await logger.log(
              level: 'info',
              subsystem: 'parser',
              message: 'Attempting recovery with alternative encoding',
              context: 'parse_search_results',
              extra: {
                'original_encoding': detectedEncoding ?? 'auto-detect',
                'will_try_alternative': true,
              },
            );

            // Try alternative encoding
            final alternativeDecoded =
                (detectedEncoding?.contains('utf') ?? false)
                    ? windows1251.decode(bytes)
                    : utf8.decode(bytes);

            // Validate alternative decoded HTML
            if (alternativeDecoded.isEmpty ||
                (!alternativeDecoded.contains('<html') &&
                    !alternativeDecoded.contains('<HTML') &&
                    !alternativeDecoded.contains('<body') &&
                    !alternativeDecoded.contains('<BODY'))) {
              throw ParsingFailure(
                'Alternative encoding also produced invalid HTML. '
                'Original encoding: ${detectedEncoding ?? 'auto-detect'}.',
                e,
              );
            }

            document = parser.parse(alternativeDecoded);
            decodedHtml = alternativeDecoded; // Update for later use

            await logger.log(
              level: 'info',
              subsystem: 'parser',
              message: 'Recovery with alternative encoding succeeded',
              context: 'parse_search_results',
              extra: {
                'alternative_encoding':
                    (detectedEncoding?.contains('utf') ?? false)
                        ? 'windows-1251'
                        : 'utf-8',
              },
            );
          } on Exception catch (recoveryError) {
            await logger.log(
              level: 'error',
              subsystem: 'parser',
              message: 'Recovery with alternative encoding also failed',
              context: 'parse_search_results',
              cause: recoveryError.toString(),
            );
            // If re-encoding also fails, throw detailed error
            throw ParsingFailure(
              'Failed to parse HTML: encoding issue detected. Tried ${detectedEncoding ?? 'auto-detect'}, but parsing still failed. '
              'Original error: ${e.toString()}, Recovery error: ${recoveryError.toString()}',
              e,
            );
          }
        } else {
          throw ParsingFailure(
            'Failed to parse HTML document structure. This may indicate a change in page structure or encoding issues. '
            'Error: ${e.toString()}',
            e,
          );
        }
      }

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
        final hasSearchForm =
            document.querySelector('form[action*="tracker"]') != null ||
                document.querySelector('form[action*="search"]') != null ||
                document.querySelector('input[name="nm"]') != null ||
                document.querySelector('form#quick-search-guest') != null ||
                document.querySelector('form#quick-search') != null;

        // Check for search page elements (even if results are empty)
        final hasSearchPageElements =
            document.querySelector('div.tCenter') != null ||
                document.querySelector('table.forumline') != null ||
                document.querySelector('div.nav') != null;

        // Check if this is the main index page (not search results)
        final isIndexPage =
            document.querySelector('div#forums_list_wrap') != null ||
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
        // Provide more detailed error message
        final pageLength = decodedHtml.length;
        final hasHtmlStructure =
            decodedHtml.contains('<html') || decodedHtml.contains('<body');
        final hasRuTrackerElements = decodedHtml.contains('rutracker') ||
            decodedHtml.contains('RuTracker') ||
            decodedHtml.contains('форум');

        String errorMessage;
        if (!hasHtmlStructure) {
          errorMessage =
              'Response does not appear to be valid HTML. This may indicate a network error or encoding issue.';
        } else if (!hasRuTrackerElements) {
          errorMessage =
              'Response does not appear to be from RuTracker. Page structure may have changed or wrong endpoint was used.';
        } else {
          errorMessage =
              'Page structure may have changed. Unable to find search results or search form. Response size: $pageLength bytes.';
        }

        throw ParsingFailure(errorMessage);
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
        final magnetElement = row.querySelector(_downloadHrefSelector);

        // Extract seeders with multiple fallback selectors
        var seeders = 0;
        var seedersElement = row.querySelector(_seedersSelector);
        if (seedersElement != null) {
          final seedersText = seedersElement.text.trim();
          seeders = int.tryParse(seedersText) ?? 0;
          EnvironmentLogger().d(
            'Extracted seeders from _seedersSelector: $seeders (text: "$seedersText")',
          );
        } else {
          // Try alternative selectors
          seedersElement = row.querySelector('span.seed, span.seed b');
          if (seedersElement != null) {
            final seedersText = seedersElement.text.trim();
            seeders = int.tryParse(seedersText) ?? 0;
            EnvironmentLogger().d(
              'Extracted seeders from alternative selector: $seeders (text: "$seedersText")',
            );
          } else {
            // Try to extract from row text using regex
            final seedersMatch =
                RegExp(r'[↑↑]\s*(\d+)|[Сс]иды[:\s]*(\d+)').firstMatch(row.text);
            if (seedersMatch != null) {
              seeders = int.tryParse(
                      seedersMatch.group(1) ?? seedersMatch.group(2) ?? '0') ??
                  0;
              EnvironmentLogger().d(
                'Extracted seeders from regex: $seeders',
              );
            } else {
              EnvironmentLogger().d('No seeders found for topic $topicId');
            }
          }
        }

        // Extract leechers with multiple fallback selectors
        var leechers = 0;
        var leechersElement = row.querySelector(_leechersSelector);
        if (leechersElement != null) {
          final leechersText = leechersElement.text.trim();
          leechers = int.tryParse(leechersText) ?? 0;
          EnvironmentLogger().d(
            'Extracted leechers from _leechersSelector: $leechers (text: "$leechersText")',
          );
        } else {
          // Try alternative selectors
          leechersElement = row.querySelector('span.leech, span.leech b');
          if (leechersElement != null) {
            final leechersText = leechersElement.text.trim();
            leechers = int.tryParse(leechersText) ?? 0;
            EnvironmentLogger().d(
              'Extracted leechers from alternative selector: $leechers (text: "$leechersText")',
            );
          } else {
            // Try to extract from row text using regex
            final leechersMatch =
                RegExp(r'[↓↓]\s*(\d+)|[Лл]ичи[:\s]*(\d+)').firstMatch(row.text);
            if (leechersMatch != null) {
              leechers = int.tryParse(leechersMatch.group(1) ??
                      leechersMatch.group(2) ??
                      '0') ??
                  0;
              EnvironmentLogger().d(
                'Extracted leechers from regex: $leechers',
              );
            } else {
              EnvironmentLogger().d('No leechers found for topic $topicId');
            }
          }
        }

        // Log final values for debugging
        EnvironmentLogger().d(
          'Topic $topicId: seeders=$seeders, leechers=$leechers',
        );

        // Verify values are not swapped (seeders should typically be >= leechers for active torrents)
        // This is a sanity check - if leechers > seeders by a large margin, might be swapped
        if (leechers > seeders && seeders > 0 && leechers > 10) {
          EnvironmentLogger().w(
            'Possible seeders/leechers swap detected for topic $topicId: '
            'seeders=$seeders, leechers=$leechers (leechers > seeders)',
          );
        }

        // Verify values are not swapped (seeders should typically be >= leechers for active torrents)
        // This is a sanity check - if leechers > seeders by a large margin, might be swapped
        if (leechers > seeders && seeders > 0 && leechers > 10) {
          EnvironmentLogger().w(
            'Possible seeders/leechers swap detected for topic $topicId: '
            'seeders=$seeders, leechers=$leechers (leechers > seeders)',
          );
        }

        // Extract size from multiple possible locations
        var sizeText = '0 MB';
        if (sizeElement != null) {
          sizeText = sizeElement.text.trim();
        } else {
          // Try to extract from row text using regex
          final sizeMatch =
              RegExp(r'([\d.,]+\s*[KMGT]?B)', caseSensitive: false)
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

        final coverUrl = _extractCoverUrl(row, baseUrl: baseUrl);
        // Log cover URL extraction for debugging
        if (coverUrl != null) {
          EnvironmentLogger().d(
            'Topic $topicId: extracted cover URL: $coverUrl',
          );
        } else {
          EnvironmentLogger().d(
            'Topic $topicId: no cover URL found',
          );
        }
        final audiobook = Audiobook(
          id: topicId,
          title: titleElement.text.trim(),
          author: authorElement?.text.trim() ?? 'Unknown',
          category: _extractCategoryFromTitle(titleElement.text),
          size: sizeText,
          seeders: seeders,
          leechers: leechers,
          magnetUrl: magnetUrl,
          coverUrl: coverUrl,
          chapters: [],
          addedDate: _extractDateFromRow(row),
        );
        results.add(audiobook);
      }

      // Log statistics about cover URLs
      final envLogger = EnvironmentLogger();
      var coverUrlCount = 0;
      var emptyCoverUrlCount = 0;

      for (final audiobook in results) {
        if (audiobook.coverUrl != null && audiobook.coverUrl!.isNotEmpty) {
          coverUrlCount++;
        } else {
          emptyCoverUrlCount++;
        }
      }

      envLogger
        ..i('Parsed ${results.length} search results')
        ..i('Cover URLs found: $coverUrlCount, empty: $emptyCoverUrlCount');

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
  /// The [htmlData] parameter contains the HTML content of the topic page.
  /// The [contentType] parameter is the Content-Type header value for encoding detection.
  ///
  /// Returns an [Audiobook] object with detailed information, or `null`
  /// if the page cannot be parsed or doesn't contain valid audiobook data.
  ///
  /// Throws [ParsingFailure] if the HTML cannot be parsed.
  Future<Audiobook?> parseTopicDetails(
    dynamic htmlData, {
    String? contentType,
  }) async {
    final logger = StructuredLogger();
    try {
      String decodedHtml;

      // Log initial data type and size for diagnostics
      final dataType = htmlData.runtimeType.toString();
      final dataSize = htmlData is List<int>
          ? htmlData.length
          : (htmlData is String ? htmlData.length : 0);

      await logger.log(
        level: 'debug',
        subsystem: 'parser',
        message: 'Starting HTML decoding for topic details',
        context: 'parse_topic_details',
        extra: {
          'data_type': dataType,
          'data_size_bytes': dataSize,
          'content_type': contentType ?? 'not_provided',
        },
      );

      // Determine encoding from Content-Type header if available
      String? detectedEncoding;
      if (contentType != null) {
        final charsetMatch = RegExp(r'charset=([^;\s]+)', caseSensitive: false)
            .firstMatch(contentType);
        if (charsetMatch != null) {
          detectedEncoding = charsetMatch.group(1)?.toLowerCase();
        }
      }

      if (htmlData is String) {
        // String data - use as-is (may have encoding issues, but we can't fix from String)
        decodedHtml = htmlData;

        await logger.log(
          level: 'debug',
          subsystem: 'parser',
          message: 'HTML data is already a String, using as-is',
          context: 'parse_topic_details',
          extra: {
            'string_length': decodedHtml.length,
          },
        );
      } else if (htmlData is List<int>) {
        // Binary data (bytes) - decode based on detected encoding or try both
        // Note: Brotli decompression is handled automatically by DioBrotliTransformer in DioClient
        // These bytes are already decompressed and ready for encoding conversion
        final bytes = htmlData;

        // Validate bytes before decoding
        if (bytes.isEmpty) {
          throw const ParsingFailure(
            'Received empty bytes. This may indicate a network error.',
          );
        }

        await logger.log(
          level: 'debug',
          subsystem: 'parser',
          message:
              'Decoding bytes (Brotli already decompressed by Dio transformer)',
          context: 'parse_topic_details',
          extra: {
            'bytes_length': bytes.length,
            'detected_encoding': detectedEncoding ?? 'auto-detect',
          },
        );

        if (detectedEncoding != null) {
          // Use detected encoding from Content-Type header
          if (detectedEncoding.contains('windows-1251') ||
              detectedEncoding.contains('cp1251') ||
              detectedEncoding.contains('1251')) {
            try {
              decodedHtml = windows1251.decode(bytes);
              await logger.log(
                level: 'debug',
                subsystem: 'parser',
                message: 'Successfully decoded with Windows-1251',
                context: 'parse_topic_details',
              );
            } on Exception catch (e) {
              await logger.log(
                level: 'warning',
                subsystem: 'parser',
                message: 'Windows-1251 decoding failed, trying UTF-8',
                context: 'parse_topic_details',
                cause: e.toString(),
              );
              // Fallback to UTF-8 if Windows-1251 fails
              try {
                decodedHtml = utf8.decode(bytes);
              } on FormatException catch (e2) {
                await logger.log(
                  level: 'error',
                  subsystem: 'parser',
                  message: 'Both Windows-1251 and UTF-8 decoding failed',
                  context: 'parse_topic_details',
                  cause: e2.toString(),
                );
                throw ParsingFailure(
                  'Failed to decode bytes: Windows-1251 failed (${e.toString()}), UTF-8 failed (${e2.toString()})',
                  e2,
                );
              }
            }
          } else if (detectedEncoding.contains('utf-8') ||
              detectedEncoding.contains('utf8')) {
            try {
              decodedHtml = utf8.decode(bytes);
              await logger.log(
                level: 'debug',
                subsystem: 'parser',
                message: 'Successfully decoded with UTF-8',
                context: 'parse_topic_details',
              );
            } on FormatException catch (e) {
              await logger.log(
                level: 'warning',
                subsystem: 'parser',
                message: 'UTF-8 decoding failed, trying Windows-1251',
                context: 'parse_topic_details',
                cause: e.toString(),
              );
              // Fallback to Windows-1251 if UTF-8 fails (RuTracker sometimes lies)
              try {
                decodedHtml = windows1251.decode(bytes);
              } on Exception catch (e2) {
                await logger.log(
                  level: 'error',
                  subsystem: 'parser',
                  message: 'Both UTF-8 and Windows-1251 decoding failed',
                  context: 'parse_topic_details',
                  cause: e2.toString(),
                );
                throw ParsingFailure(
                  'Failed to decode bytes: UTF-8 failed (${e.toString()}), Windows-1251 failed (${e2.toString()})',
                  e2,
                );
              }
            }
          } else {
            // Unknown encoding, try both
            try {
              decodedHtml = utf8.decode(bytes);
            } on FormatException {
              try {
                decodedHtml = windows1251.decode(bytes);
              } on Exception catch (e) {
                throw ParsingFailure(
                  'Failed to decode bytes with unknown encoding: ${e.toString()}',
                  e,
                );
              }
            }
          }
        } else {
          // No encoding specified - try Windows-1251 first (RuTracker default)
          // then fallback to UTF-8
          try {
            decodedHtml = windows1251.decode(bytes);
            await logger.log(
              level: 'debug',
              subsystem: 'parser',
              message: 'Successfully decoded with Windows-1251 (default)',
              context: 'parse_topic_details',
            );
          } on Exception catch (e) {
            await logger.log(
              level: 'warning',
              subsystem: 'parser',
              message: 'Windows-1251 decoding failed, trying UTF-8',
              context: 'parse_topic_details',
              cause: e.toString(),
            );
            try {
              decodedHtml = utf8.decode(bytes);
            } on FormatException catch (e2) {
              await logger.log(
                level: 'warning',
                subsystem: 'parser',
                message:
                    'UTF-8 decoding also failed, trying Latin-1 as last resort',
                context: 'parse_topic_details',
                cause: e2.toString(),
              );
              // Last resort: try to decode as Latin-1 (never fails)
              decodedHtml = latin1.decode(bytes);
            }
          }
        }
      } else {
        // Fallback: try to convert to string
        await logger.log(
          level: 'warning',
          subsystem: 'parser',
          message: 'Unexpected data type, converting to string',
          context: 'parse_topic_details',
          extra: {
            'data_type': dataType,
          },
        );
        decodedHtml = htmlData.toString();
      }

      // Validate decoded HTML before parsing
      if (decodedHtml.isEmpty) {
        throw const ParsingFailure(
          'Decoded HTML is empty. This may indicate a network error or encoding issue.',
        );
      }

      // Check for valid HTML structure
      final hasHtmlStructure = decodedHtml.contains('<html') ||
          decodedHtml.contains('<HTML') ||
          decodedHtml.contains('<body') ||
          decodedHtml.contains('<BODY');

      if (!hasHtmlStructure) {
        await logger.log(
          level: 'error',
          subsystem: 'parser',
          message: 'Decoded text does not appear to be valid HTML',
          context: 'parse_topic_details',
          extra: {
            'decoded_length': decodedHtml.length,
            'has_html_tag': decodedHtml.contains('<html'),
            'has_body_tag': decodedHtml.contains('<body'),
          },
        );
        throw ParsingFailure(
          'Response does not appear to be valid HTML. This may indicate a network error or encoding issue. '
          'Decoded text length: ${decodedHtml.length} bytes.',
        );
      }

      Document? document;
      try {
        document = parser.parse(decodedHtml);

        // Validate that parsing produced a valid document
        if (document.body == null) {
          throw const ParsingFailure(
            'HTML parser returned document without body. This may indicate invalid HTML structure.',
          );
        }

        await logger.log(
          level: 'debug',
          subsystem: 'parser',
          message: 'Successfully parsed HTML document',
          context: 'parse_topic_details',
          extra: {
            'has_body': document.body != null,
            'has_head': document.head != null,
          },
        );
      } on Exception catch (e) {
        await logger.log(
          level: 'error',
          subsystem: 'parser',
          message: 'Failed to parse HTML document',
          context: 'parse_topic_details',
          cause: e.toString(),
          extra: {
            'html_data_type': htmlData.runtimeType.toString(),
            'decoded_length': decodedHtml.length,
          },
        );
        throw ParsingFailure(
          'Failed to parse HTML document structure. This may indicate a change in page structure or encoding issues. '
          'Error: ${e.toString()}',
          e,
        );
      }

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

      // Extract performer from structured metadata
      String? performerName;
      final performerKeys = [
        'Исполнитель',
        'Чтец',
        'Читает',
        'Исполнитель:',
        'Читает:'
      ];
      for (final key in performerKeys) {
        final performerText = metadata[key];
        if (performerText != null && performerText.isNotEmpty) {
          performerName = performerText.trim();
          break;
        }
      }

      // Fallback: search in post body text using regex patterns
      if (performerName == null || performerName.isEmpty) {
        final performerPatterns = [
          RegExp(r'Читает[:\s]+([^\n<]+)', caseSensitive: false),
          RegExp(r'Исполнитель[:\s]+([^\n<]+)', caseSensitive: false),
          RegExp(r'Чтец[:\s]+([^\n<]+)', caseSensitive: false),
        ];
        for (final pattern in performerPatterns) {
          final match = pattern.firstMatch(postBody.text);
          if (match != null) {
            performerName = match.group(1)?.trim();
            if (performerName != null && performerName.isNotEmpty) {
              break;
            }
          }
        }
      }

      // Extract genres from structured metadata
      var genres = <String>[];
      final genreKeys = ['Жанр', 'Жанры', 'Genre', 'Genres', 'Жанр:', 'Жанры:'];
      for (final key in genreKeys) {
        final genreText = metadata[key];
        if (genreText != null && genreText.isNotEmpty) {
          // Split by comma or semicolon
          genres = genreText
              .split(RegExp(r'[,;]'))
              .map((g) => g.trim())
              .where((g) => g.isNotEmpty)
              .toList();
          if (genres.isNotEmpty) {
            break;
          }
        }
      }

      // Fallback: search in post body text using regex patterns
      if (genres.isEmpty) {
        final genrePatterns = [
          RegExp(r'Жанр[ы]?[:\s]+([^\n<]+)', caseSensitive: false),
          RegExp(r'Genre[s]?[:\s]+([^\n<]+)', caseSensitive: false),
        ];
        for (final pattern in genrePatterns) {
          final match = pattern.firstMatch(postBody.text);
          if (match != null) {
            final genreText = match.group(1)?.trim();
            if (genreText != null && genreText.isNotEmpty) {
              genres = genreText
                  .split(RegExp(r'[,;]'))
                  .map((g) => g.trim())
                  .where((g) => g.isNotEmpty)
                  .toList();
              if (genres.isNotEmpty) {
                break;
              }
            }
          }
        }
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
            final dateMatch =
                RegExp(r'(\d{2})-(\w{3})-(\d{2})(?:\s+(\d{2}):(\d{2}))?')
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
        final sizeMatch = RegExp(r'Размер[:\s]*([\d.,]+\s*[KMGT]?B)')
            .firstMatch(postBody.text);
        sizeText = sizeMatch?.group(1)?.trim();
      }

      // Extract seeders and leechers from tor-stats table (reuse cached selector)
      if (torStats != null) {
        // Extract seeders and leechers with improved selectors
        final seedersElement =
            torStats.querySelector('span.seed b, span.seedmed b');
        final leechersElement =
            torStats.querySelector('span.leech b, span.leechmed b');
        seeders = int.tryParse(seedersElement?.text.trim() ?? '0');
        leechers = int.tryParse(leechersElement?.text.trim() ?? '0');
      }
      // Fallback to post body text
      if (seeders == null) {
        final seedersMatch =
            RegExp(r'Сиды[:\s]*(\d+)').firstMatch(postBody.text);
        seeders = int.tryParse(seedersMatch?.group(1) ?? '0') ?? 0;
      }
      if (leechers == null) {
        final leechersMatch =
            RegExp(r'Личи[:\s]*(\d+)').firstMatch(postBody.text);
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
      final coverUrl =
          _extractCoverUrlImproved(postBody, document.documentElement!);

      final chapters = <Chapter>[];
      // Try to parse chapters from description with improved flexible patterns
      final chapterText = postBody.text;

      // Pattern 1: "1. Название (1:23:45)" or "01. Название (1:23:45)"
      final pattern1 =
          RegExp(r'(\d+)[.:]\s*([^\n(]+?)\s*\((\d+:\d+(?::\d+)?)\)');
      for (final match in pattern1.allMatches(chapterText)) {
        final title = match.group(2)?.trim() ?? '';
        final duration = match.group(3)?.trim() ?? '0:00';
        chapters.add(_createChapterFromDuration(title, duration));
      }

      // Pattern 2: "01 - Название [1:23:45]"
      if (chapters.isEmpty) {
        final pattern2 =
            RegExp(r'(\d+)\s*[-–]\s*([^\n[\]]+?)\s*\[(\d+:\d+(?::\d+)?)\]');
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
        final pattern4 =
            RegExp(r'(\d+[.:]\s*[^\n]+?)\s*\(?(\d+:\d+(?::\d+)?)\)?');
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
        final titleLink =
            titleElement.querySelector('a[href*="viewtopic.php?t="]');
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
        topicId =
            _extractTopicIdFromUrl(document.documentElement?.outerHtml ?? '');
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
        performer: performerName,
        genres: genres,
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

  /// Parses topic statistics (seeders and leechers) from topic page HTML.
  ///
  /// This is a lightweight method that only extracts statistics without
  /// parsing the full topic details. Used to update statistics for search
  /// results that don't have them.
  ///
  /// The [htmlData] parameter contains the HTML content of the topic page.
  ///
  /// Returns a map with 'seeders' and 'leechers' keys, or null if parsing fails.
  Future<Map<String, int>?> parseTopicStatistics(dynamic htmlData) async {
    try {
      String decodedHtml;
      try {
        decodedHtml = utf8.decode(htmlData.codeUnits);
      } on FormatException {
        decodedHtml = windows1251.decode(htmlData.codeUnits);
      }

      final document = parser.parse(decodedHtml);
      final torStats = document.querySelector(_torStatsSelector);

      int? seeders;
      int? leechers;

      // Extract seeders and leechers from tor-stats table
      if (torStats != null) {
        final seedersElement =
            torStats.querySelector('span.seed b, span.seedmed b');
        final leechersElement =
            torStats.querySelector('span.leech b, span.leechmed b');
        seeders = int.tryParse(seedersElement?.text.trim() ?? '0');
        leechers = int.tryParse(leechersElement?.text.trim() ?? '0');
      }

      // Fallback to post body text
      final postBody = document.querySelector(_postBodySelector);
      if (postBody != null) {
        if (seeders == null) {
          final seedersMatch =
              RegExp(r'Сиды[:\s]*(\d+)').firstMatch(postBody.text);
          seeders = int.tryParse(seedersMatch?.group(1) ?? '0') ?? 0;
        }
        if (leechers == null) {
          final leechersMatch =
              RegExp(r'Личи[:\s]*(\d+)').firstMatch(postBody.text);
          leechers = int.tryParse(leechersMatch?.group(1) ?? '0') ?? 0;
        }
      }

      // Return null if we couldn't extract statistics
      if (seeders == null || leechers == null) {
        return null;
      }

      return {
        'seeders': seeders,
        'leechers': leechers,
      };
    } on Exception {
      return null;
    }
  }
}

// Helper methods for parsing
String _extractInfoHashFromUrl(String? url) {
  if (url == null) return '';
  // Try to extract info hash from magnet link
  if (url.startsWith('magnet:')) {
    final hashMatch =
        RegExp(r'btih:([A-F0-9]+)', caseSensitive: false).firstMatch(url);
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
      final categoryMatch =
          RegExp(r'Категория[:\s]*([^\n<]+)').firstMatch(parentText);
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

/// Extracts cover URL from search result row.
///
/// Tries multiple selectors in priority order to find cover image.
/// Returns normalized absolute URL or null if not found.
String? _extractCoverUrl(Element row, {String? baseUrl}) {
  final logger = EnvironmentLogger()
    ..d('Extracting cover URL from search result row');

  // Priority 1: var.postImg with title attribute (contains full URL)
  final postImgVar =
      row.querySelector('var.postImg[title], var.postImgAligned[title]');
  if (postImgVar != null) {
    final title = postImgVar.attributes['title'];
    if (title != null && title.isNotEmpty) {
      final normalizedUrl = _normalizeCoverUrl(title, baseUrl: baseUrl);
      if (normalizedUrl != null) {
        logger.d('Cover URL extracted (Priority 1): $normalizedUrl');
        return normalizedUrl;
      }
    }
  }

  // Priority 2: var.postImg with title containing fastpic or rutracker
  final postImgFastpic = row.querySelector(
      'var.postImg[title*="fastpic"], var.postImg[title*="rutracker"], '
      'var.postImgAligned[title*="fastpic"], var.postImgAligned[title*="rutracker"]');
  if (postImgFastpic != null) {
    final title = postImgFastpic.attributes['title'];
    if (title != null && title.isNotEmpty) {
      final normalizedUrl = _normalizeCoverUrl(title);
      if (normalizedUrl != null) {
        logger.d('Cover URL extracted (Priority 2): $normalizedUrl');
        return normalizedUrl;
      }
    }
  }

  // Priority 3: img with src containing static.rutracker or fastpic
  final imgElement = row.querySelector(
      'img[src*="static.rutracker"], img[src*="fastpic"], img.postimg');
  if (imgElement != null) {
    final src = imgElement.attributes['src'];
    if (src != null && src.isNotEmpty) {
      final normalizedUrl = _normalizeCoverUrl(src, baseUrl: baseUrl);
      if (normalizedUrl != null) {
        logger.d('Cover URL extracted (Priority 3): $normalizedUrl');
        return normalizedUrl;
      }
    }
  }

  // Priority 4: img with data-src (lazy loading)
  final imgLazy = row.querySelector('img[data-src]');
  if (imgLazy != null) {
    final dataSrc = imgLazy.attributes['data-src'];
    if (dataSrc != null && dataSrc.isNotEmpty) {
      final normalizedUrl = _normalizeCoverUrl(dataSrc, baseUrl: baseUrl);
      if (normalizedUrl != null) {
        logger.d('Cover URL extracted (Priority 4): $normalizedUrl');
        return normalizedUrl;
      }
    }
  }

  // Priority 5: img with srcset
  final imgSrcset = row.querySelector('img[srcset]');
  if (imgSrcset != null) {
    final srcset = imgSrcset.attributes['srcset'];
    if (srcset != null && srcset.isNotEmpty) {
      // Extract first URL from srcset
      final firstUrl = srcset.split(',').first.trim().split(' ').first;
      if (firstUrl.isNotEmpty) {
        final normalizedUrl = _normalizeCoverUrl(firstUrl, baseUrl: baseUrl);
        if (normalizedUrl != null) {
          logger.d('Cover URL extracted (Priority 5): $normalizedUrl');
          return normalizedUrl;
        }
      }
    }
  }

  logger.d('Cover URL not found in search result row');
  return null;
}

/// Normalizes cover URL to absolute URL.
///
/// Converts relative URLs to absolute URLs using provided baseUrl or rutracker.org as fallback.
String? _normalizeCoverUrl(String? url, {String? baseUrl}) {
  if (url == null || url.isEmpty) {
    EnvironmentLogger().d('_normalizeCoverUrl: URL is null or empty');
    return null;
  }

  final logger = EnvironmentLogger()
    ..d('_normalizeCoverUrl: Normalizing URL: $url (baseUrl: $baseUrl)');

  // If URL already absolute, return as is
  if (url.startsWith('http://') || url.startsWith('https://')) {
    logger.d('_normalizeCoverUrl: URL is already absolute: $url');
    return url;
  }

  // Determine base URL to use
  String effectiveBaseUrl;
  if (baseUrl != null && baseUrl.isNotEmpty) {
    // Remove trailing slash from baseUrl
    effectiveBaseUrl = baseUrl.endsWith('/')
        ? baseUrl.substring(0, baseUrl.length - 1)
        : baseUrl;
    // Ensure baseUrl has protocol
    if (!effectiveBaseUrl.startsWith('http://') &&
        !effectiveBaseUrl.startsWith('https://')) {
      effectiveBaseUrl = 'https://$effectiveBaseUrl';
    }
  } else {
    // Fallback to rutracker.org
    effectiveBaseUrl = 'https://rutracker.org';
  }

  // If URL relative (starts with /), convert to absolute
  if (url.startsWith('/')) {
    final normalized = '$effectiveBaseUrl$url';
    logger.d('_normalizeCoverUrl: Normalized relative URL: $normalized');
    return normalized;
  }

  // If URL starts with //, add https:
  if (url.startsWith('//')) {
    final normalized = 'https:$url';
    logger
        .d('_normalizeCoverUrl: Normalized protocol-relative URL: $normalized');
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
        final year =
            yearTwoDigits >= 50 ? 1900 + yearTwoDigits : 2000 + yearTwoDigits;
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
        final year =
            yearTwoDigits >= 50 ? 1900 + yearTwoDigits : 2000 + yearTwoDigits;
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
      final year =
          yearTwoDigits >= 50 ? 1900 + yearTwoDigits : 2000 + yearTwoDigits;
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
  final attachDateMatch =
      RegExp(r'(\d{2})-(\w{3})-(\d{2})(?:\s+(\d{2}):(\d{2}))?')
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
    final year =
        yearTwoDigits >= 50 ? 1900 + yearTwoDigits : 2000 + yearTwoDigits;

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
        (int.parse(durationParts[0]) * 60 + int.parse(durationParts[1])) * 1000;
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
    final match1 =
        RegExp('${RegExp.escape(key)}[:\\s]+([^\\n<]+)').firstMatch(parentText);
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
    final match2 =
        RegExp('${RegExp.escape(key)}[:\\s]*([^<\\n]+)').firstMatch(parentHtml);
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
  final postImgVar =
      postBody.querySelector('var.postImg[title], var.postImgAligned[title]');
  if (postImgVar != null) {
    final title = postImgVar.attributes['title'];
    if (title != null && title.isNotEmpty) {
      return title;
    }
  }

  // Priority 2: var.postImg with title containing fastpic or rutracker
  final postImgFastpic = postBody.querySelector(
      'var.postImg[title*="fastpic"], var.postImg[title*="rutracker"], '
      'var.postImgAligned[title*="fastpic"], var.postImgAligned[title*="rutracker"]');
  if (postImgFastpic != null) {
    final title = postImgFastpic.attributes['title'];
    if (title != null && title.isNotEmpty) {
      return title;
    }
  }

  // Priority 3: img with src containing static.rutracker or fastpic
  final imgElement = document.querySelector(
      'img[src*="static.rutracker"], img[src*="fastpic"], img.postimg');
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
  final absoluteMatch =
      RegExp(r'(\d{2})-(\w{3})-(\d{2})\s+(\d{2}):(\d{2})').firstMatch(dateText);
  if (absoluteMatch != null) {
    try {
      final day = int.parse(absoluteMatch.group(1)!);
      final month = _monthToNumber(absoluteMatch.group(2)!);
      final yearTwoDigits = int.parse(absoluteMatch.group(3)!);
      // If year >= 50, assume 1900s (1950-1999), otherwise 2000s (2000-2049)
      final year =
          yearTwoDigits >= 50 ? 1900 + yearTwoDigits : 2000 + yearTwoDigits;
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
        final year =
            yearTwoDigits >= 50 ? 1900 + yearTwoDigits : 2000 + yearTwoDigits;
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
