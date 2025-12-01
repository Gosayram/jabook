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

import 'dart:async';
import 'dart:convert';
import 'dart:isolate';

import 'package:b_encode_decode/b_encode_decode.dart';
import 'package:crypto/crypto.dart';
import 'package:dtorrent_parser/dtorrent_parser.dart';
import 'package:flutter/foundation.dart';
import 'package:jabook/core/cache/rutracker_cache_service.dart';
import 'package:jabook/core/parse/rutracker_parser.dart';

/// Reusable isolate manager for torrent parsing.
///
/// This manager maintains a single isolate that is reused for all parsing tasks,
/// avoiding the overhead of creating a new isolate for each parse operation.
class _TorrentParserIsolateManager {
  /// Factory constructor for singleton instance.
  factory _TorrentParserIsolateManager() {
    _instance ??= _TorrentParserIsolateManager._();
    return _instance!;
  }
  _TorrentParserIsolateManager._();
  static _TorrentParserIsolateManager? _instance;

  Isolate? _isolate;
  SendPort? _sendPort;
  ReceivePort? _receivePort;
  bool _initialized = false;
  final _pendingTasks = <Completer<List<Chapter>>>[];
  bool _processing = false;

  /// Initialize the isolate if not already initialized.
  Future<void> _ensureInitialized() async {
    if (_initialized) return;

    _receivePort = ReceivePort();
    _isolate = await Isolate.spawn(
      _torrentParserIsolateEntry,
      _receivePort!.sendPort,
      debugName: 'TorrentParserIsolate',
    );

    _sendPort = await _receivePort!.first as SendPort;

    // Listen for responses and process queue
    _receivePort!.listen((response) {
      if (_pendingTasks.isEmpty) return;

      final completer = _pendingTasks.removeAt(0);
      _processing = false;

      if (response is List<Chapter>) {
        completer.complete(response);
      } else if (response is Exception) {
        completer.completeError(response);
      } else {
        completer.completeError(Exception('Unexpected response type'));
      }

      // Process next task in queue
      _processNext();
    });

    _initialized = true;
  }

  /// Process next task in queue.
  void _processNext() {
    if (_pendingTasks.isEmpty || _processing || !_initialized) {
      return;
    }

    _processing = true;
    // Task data is already sent, isolate will process and send response
    // The response listener will handle the result and call _processNext() again
  }

  /// Parse torrent bytes in the reusable isolate.
  ///
  /// Tasks are queued and processed sequentially to ensure responses
  /// match requests correctly.
  Future<List<Chapter>> parse(List<int> torrentBytes) async {
    await _ensureInitialized();

    final completer = Completer<List<Chapter>>();
    _pendingTasks.add(completer);

    // Send task to isolate
    _sendPort!.send(torrentBytes);

    // Start processing if not already processing
    if (!_processing) {
      _processNext();
    }

    return completer.future.timeout(
      const Duration(seconds: 30),
      onTimeout: () {
        _pendingTasks.remove(completer);
        _processing = false;
        _processNext();
        throw TimeoutException('Torrent parsing timeout');
      },
    );
  }

  /// Dispose the isolate (for testing or cleanup).
  void dispose() {
    _isolate?.kill(priority: Isolate.immediate);
    _isolate = null;
    _sendPort = null;
    _receivePort?.close();
    _receivePort = null;
    _initialized = false;
    _pendingTasks.clear();
    _processing = false;
  }
}

/// Isolate entry point for torrent parsing.
void _torrentParserIsolateEntry(SendPort sendPort) async {
  final receivePort = ReceivePort();
  sendPort.send(receivePort.sendPort);

  receivePort.listen((message) {
    if (message is List<int>) {
      try {
        final chapters = TorrentParserService._parseTorrentInIsolate(message);
        sendPort.send(chapters);
      } on Exception catch (e) {
        sendPort.send(Exception(e.toString()));
      } on Object catch (e, _) {
        sendPort.send(Exception(e.toString()));
      }
    }
  });
}

/// Service for parsing torrent files to extract chapter information.
///
/// This service extracts audio file names from torrent files
/// and creates Chapter objects based on file names.
/// It also caches parsed chapters to avoid re-parsing the same torrent.
class TorrentParserService {
  /// Creates a new TorrentParserService instance.
  ///
  /// The [cacheService] parameter is optional - if not provided, a new instance will be created.
  /// For dependency injection, prefer passing an initialized instance from a provider.
  TorrentParserService({RuTrackerCacheService? cacheService})
      : _cacheService = cacheService ?? RuTrackerCacheService();

  /// Cache service for storing parsed chapters.
  final RuTrackerCacheService _cacheService;

  /// Audio file extensions to consider as chapters.
  static const List<String> audioExtensions = [
    '.mp3',
    '.flac',
    '.m4b',
    '.m4a',
    '.ogg',
    '.wav',
    '.aac',
    '.opus',
  ];

  /// Threshold for using isolate (1 MB).
  /// Files larger than this will be parsed in an isolate to avoid blocking UI.
  static const int isolateThresholdBytes = 1024 * 1024;

  /// Extracts chapters from a torrent file.
  ///
  /// The [torrentBytes] parameter contains the content of the .torrent file.
  /// The [forceRefresh] parameter, if true, bypasses cache and forces re-parsing.
  /// Returns a list of [Chapter] objects based on audio file names in the torrent.
  ///
  /// For large files (>1MB), parsing is performed in an isolate to avoid blocking the UI thread.
  ///
  /// Chapters are sorted by their original order in the torrent file (fileIndex),
  /// which preserves the order specified in info.files according to BEP 0003.
  ///
  /// Note: This method only extracts chapter information (title, fileIndex, byte ranges).
  /// Duration is not calculated to avoid unnecessary resource usage.
  ///
  /// Throws [Exception] if the torrent cannot be parsed.
  Future<List<Chapter>> extractChaptersFromTorrent(List<int> torrentBytes,
      {bool forceRefresh = false}) async {
    try {
      // Calculate infoHash for caching
      final torrentMap = decode(Uint8List.fromList(torrentBytes));
      final infoHash = _calculateInfoHash(torrentMap);

      List<Chapter> sortedChapters;

      // For large files, use isolate to avoid blocking UI
      if (torrentBytes.length > isolateThresholdBytes) {
        final chapters =
            await _extractChaptersInIsolate(torrentBytes, forceRefresh);
        sortedChapters = _sortChapters(chapters);
      } else {
        // For smaller files, parse directly
        final chapters =
            await _extractChaptersDirectly(torrentBytes, forceRefresh);
        sortedChapters = _sortChapters(chapters);
      }

      // Cache sorted chapters
      if (infoHash.isNotEmpty && sortedChapters.isNotEmpty) {
        final chaptersMap = sortedChapters
            .map((c) => {
                  'title': c.title,
                  'duration_ms': c.durationMs,
                  'file_index': c.fileIndex,
                  'start_byte': c.startByte,
                  'end_byte': c.endByte,
                })
            .toList();
        await _cacheService.cacheTorrentChapters(infoHash, chaptersMap);
      }

      return sortedChapters;
    } on Exception {
      return [];
    }
  }

  /// Extracts chapters directly in the current isolate.
  Future<List<Chapter>> _extractChaptersDirectly(
      List<int> torrentBytes, bool forceRefresh) async {
    try {
      // Parse torrent file
      final torrentMap = decode(Uint8List.fromList(torrentBytes));
      final torrentModel = parseTorrentFileContent(torrentMap);

      if (torrentModel == null) {
        return [];
      }

      // Calculate infoHash from torrent info dictionary
      final infoHash = _calculateInfoHash(torrentMap);

      // Clear cache if force refresh is requested
      if (forceRefresh && infoHash.isNotEmpty) {
        await _cacheService.clearTorrentChaptersCache(infoHash);
      }

      // Check cache first (unless force refresh)
      if (!forceRefresh && infoHash.isNotEmpty) {
        final cachedChapters =
            await _cacheService.getCachedTorrentChapters(infoHash);
        if (cachedChapters != null && cachedChapters.isNotEmpty) {
          // Convert cached maps to Chapter objects
          return cachedChapters
              .map((c) => Chapter(
                    title: c['title'] as String? ?? '',
                    durationMs: c['duration_ms'] as int? ?? 0,
                    fileIndex: c['file_index'] as int? ?? 0,
                    startByte: c['start_byte'] as int? ?? 0,
                    endByte: c['end_byte'] as int? ?? 0,
                  ))
              .toList();
        }
      }

      final chapters = <Chapter>[];

      // Extract files from torrent
      // According to BEP 0003, files in info.files are already in correct order
      if (torrentModel.files.isNotEmpty) {
        // Multi-file torrent
        // Extract files directly from info dictionary to preserve order
        final info = torrentMap['info'] as Map<String, dynamic>?;
        if (info != null && info.containsKey('files')) {
          final files = info['files'] as List;
          var fileIndex = 0;
          for (var i = 0; i < files.length; i++) {
            final fileInfo = files[i] as Map;
            final length = fileInfo['length'] as int? ?? 0;

            // Extract path - can be List of bytes or List of strings
            final pathData = fileInfo['path'];
            String fileName;
            if (pathData is List) {
              // Path is a list - decode each element from bytes if needed
              final pathParts = <String>[];
              for (final part in pathData) {
                if (part is List<int>) {
                  // Decode bytes to UTF-8 string
                  pathParts.add(utf8.decode(part));
                } else if (part is String) {
                  pathParts.add(part);
                } else {
                  pathParts.add(part.toString());
                }
              }
              fileName = pathParts.join('/');
            } else {
              fileName = pathData?.toString() ?? '';
            }

            if (fileName.isNotEmpty && _isAudioFile(fileName)) {
              final chapterTitle = _extractChapterTitle(fileName);
              // Store original filename in title temporarily for sorting
              // Format: "originalFileName|cleanedTitle"
              chapters.add(Chapter(
                title: '$fileName|$chapterTitle',
                durationMs: 0, // Duration not calculated - informational only
                fileIndex: fileIndex,
                startByte: 0,
                endByte: length, // Use length from torrent info
              ));
              fileIndex++;
            }
          }
        } else {
          // Fallback to torrentModel.files if info.files is not available
          var fileIndex = 0;
          for (final file in torrentModel.files) {
            // Handle path as either List<String> or String
            final fileName = file.path is List
                ? (file.path as List).join('/')
                : file.path.toString();
            if (_isAudioFile(fileName)) {
              final chapterTitle = _extractChapterTitle(fileName);
              final fileSize = file.length;
              chapters.add(Chapter(
                title: '$fileName|$chapterTitle',
                durationMs: 0,
                fileIndex: fileIndex,
                startByte: 0,
                endByte: fileSize,
              ));
              fileIndex++;
            }
          }
        }
      } else {
        // Single-file torrent
        final fileName = torrentModel.name;
        if (fileName.isNotEmpty && _isAudioFile(fileName)) {
          final chapterTitle = _extractChapterTitle(fileName);
          final fileSize = torrentModel.length;
          // Store original filename in title temporarily for sorting
          chapters.add(Chapter(
            title: '$fileName|$chapterTitle',
            durationMs: 0, // Duration not calculated - informational only
            fileIndex: 0,
            startByte: 0,
            endByte: fileSize,
          ));
        }
      }

      // Don't cache here - caching will be done after sorting in extractChaptersFromTorrent
      return chapters;
    } on Exception {
      return [];
    }
  }

  /// Extracts chapters in an isolate for large files.
  Future<List<Chapter>> _extractChaptersInIsolate(
      List<int> torrentBytes, bool forceRefresh) async {
    try {
      // Calculate infoHash for caching (before isolate)
      final torrentMap = decode(Uint8List.fromList(torrentBytes));
      final infoHash = _calculateInfoHash(torrentMap);

      // Handle cache before parsing
      if (forceRefresh && infoHash.isNotEmpty) {
        await _cacheService.clearTorrentChaptersCache(infoHash);
      }

      if (!forceRefresh && infoHash.isNotEmpty) {
        final cachedChapters =
            await _cacheService.getCachedTorrentChapters(infoHash);
        if (cachedChapters != null && cachedChapters.isNotEmpty) {
          return cachedChapters
              .map((c) => Chapter(
                    title: c['title'] as String? ?? '',
                    durationMs: c['duration_ms'] as int? ?? 0,
                    fileIndex: c['file_index'] as int? ?? 0,
                    startByte: c['start_byte'] as int? ?? 0,
                    endByte: c['end_byte'] as int? ?? 0,
                  ))
              .toList();
        }
      }

      // Use reusable isolate instead of compute() to avoid creating new isolate each time
      // This is more efficient for multiple parsing requests
      final result = await _TorrentParserIsolateManager().parse(torrentBytes);

      // Sort chapters after returning from isolate
      // Don't cache here - caching will be done after sorting in extractChaptersFromTorrent
      final sortedChapters = _sortChapters(result);

      return sortedChapters;
    } on Exception {
      return [];
    }
  }

  /// Static function for isolate execution.
  /// This function must be top-level or static to be used with compute.
  static List<Chapter> _parseTorrentInIsolate(List<int> torrentBytes) {
    try {
      // Parse torrent file
      final torrentMap = decode(Uint8List.fromList(torrentBytes));
      final torrentModel = parseTorrentFileContent(torrentMap);

      if (torrentModel == null) {
        return [];
      }

      final chapters = <Chapter>[];

      // Extract files from torrent
      // According to BEP 0003, files in info.files are already in correct order
      if (torrentModel.files.isNotEmpty) {
        // Multi-file torrent
        // Extract files directly from info dictionary to preserve order
        final info = torrentMap['info'] as Map<String, dynamic>?;
        if (info != null && info.containsKey('files')) {
          final files = info['files'] as List;
          var fileIndex = 0;
          for (var i = 0; i < files.length; i++) {
            final fileInfo = files[i] as Map;
            final length = fileInfo['length'] as int? ?? 0;

            // Extract path - can be List of bytes or List of strings
            final pathData = fileInfo['path'];
            String fileName;
            if (pathData is List) {
              // Path is a list - decode each element from bytes if needed
              final pathParts = <String>[];
              for (final part in pathData) {
                if (part is List<int>) {
                  // Decode bytes to UTF-8 string
                  pathParts.add(utf8.decode(part));
                } else if (part is String) {
                  pathParts.add(part);
                } else {
                  pathParts.add(part.toString());
                }
              }
              fileName = pathParts.join('/');
            } else {
              fileName = pathData?.toString() ?? '';
            }

            if (fileName.isNotEmpty && _isAudioFileStatic(fileName)) {
              final chapterTitle = _extractChapterTitleStatic(fileName);
              // Store original filename in title temporarily for sorting
              // Format: "originalFileName|cleanedTitle"
              chapters.add(Chapter(
                title: '$fileName|$chapterTitle',
                durationMs: 0, // Duration not calculated - informational only
                fileIndex: fileIndex,
                startByte: 0,
                endByte: length, // Use length from torrent info
              ));
              fileIndex++;
            }
          }
        } else {
          // Fallback to torrentModel.files if info.files is not available
          var fileIndex = 0;
          for (final file in torrentModel.files) {
            // Handle path as either List<String> or String
            final fileName = file.path is List
                ? (file.path as List).join('/')
                : file.path.toString();
            if (_isAudioFileStatic(fileName)) {
              final chapterTitle = _extractChapterTitleStatic(fileName);
              chapters.add(Chapter(
                title: '$fileName|$chapterTitle',
                durationMs: 0,
                fileIndex: fileIndex,
                startByte: 0,
                endByte: file.length,
              ));
              fileIndex++;
            }
          }
        }
      } else {
        // Single-file torrent
        final fileName = torrentModel.name;
        if (fileName.isNotEmpty && _isAudioFileStatic(fileName)) {
          final chapterTitle = _extractChapterTitleStatic(fileName);
          // Store original filename in title temporarily for sorting
          chapters.add(Chapter(
            title: '$fileName|$chapterTitle',
            durationMs: 0, // Duration not calculated - informational only
            fileIndex: 0,
            startByte: 0,
            endByte: torrentModel.length,
          ));
        }
      }

      return chapters;
    } on Exception {
      return [];
    }
  }

  /// Static version of _isAudioFile for use in isolate.
  static bool _isAudioFileStatic(String fileName) {
    final lowerFileName = fileName.toLowerCase();
    return audioExtensions.any(lowerFileName.endsWith);
  }

  /// Static version of _extractChapterTitle for use in isolate.
  static String _extractChapterTitleStatic(String fileName) {
    // Remove path and get just the filename
    final parts = fileName.split('/');
    var title = parts.last;

    // Remove file extension
    for (final ext in audioExtensions) {
      if (title.toLowerCase().endsWith(ext)) {
        title = title.substring(0, title.length - ext.length);
        break;
      }
    }

    // Remove common prefixes and patterns
    // Pattern 1: "001 - Название" or "01. Название" or "1- Название"
    title = title.replaceFirst(RegExp(r'^\d+[.\s-]+'), '');

    // Pattern 2: "Глава 1: Название" or "Глава 01. Название"
    title = title.replaceFirst(
        RegExp(r'^Глава\s*\d+[.:\s-]+', caseSensitive: false), '');

    // Pattern 3: "Часть 1: Название" or "Часть 01. Название"
    title = title.replaceFirst(
        RegExp(r'^Часть\s*\d+[.:\s-]+', caseSensitive: false), '');

    // Pattern 4: "Chapter 1: Название" or "Chapter 01. Название"
    title = title.replaceFirst(
        RegExp(r'^Chapter\s*\d+[.:\s-]+', caseSensitive: false), '');

    // Pattern 5: "Книга 1: Название" or "Книга 01. Название"
    title = title.replaceFirst(
        RegExp(r'^Книга\s*\d+[.:\s-]+', caseSensitive: false), '');

    // Clean up title - remove leading/trailing spaces, dashes, underscores
    title = title.trim();
    title = title.replaceFirst(RegExp(r'^[\s\-_]+'), '');
    title = title.replaceFirst(RegExp(r'[\s\-_]+$'), '');

    // If title is empty after cleaning, use filename without extension
    if (title.isEmpty) {
      title = parts.last;
      // Remove extension again
      for (final ext in audioExtensions) {
        if (title.toLowerCase().endsWith(ext)) {
          title = title.substring(0, title.length - ext.length);
          break;
        }
      }
      title = title.trim();
    }

    return title;
  }

  /// Calculates the info hash (SHA1) of the torrent info dictionary.
  ///
  /// The [torrentMap] parameter is the decoded torrent map.
  /// Returns the info hash as a hexadecimal string, or empty string if calculation fails.
  String _calculateInfoHash(Map<String, dynamic> torrentMap) {
    try {
      // Get the 'info' dictionary from torrent
      final info = torrentMap['info'];
      if (info == null) {
        return '';
      }

      // Encode the info dictionary back to bencoded bytes
      final infoBytes = encode(info);

      // Calculate SHA1 hash
      final hash = sha1.convert(infoBytes);

      // Return as uppercase hexadecimal string
      return hash.toString().toUpperCase();
    } on Exception {
      return '';
    }
  }

  /// Checks if a file is an audio file based on its extension.
  bool _isAudioFile(String fileName) {
    final lowerFileName = fileName.toLowerCase();
    return audioExtensions.any(lowerFileName.endsWith);
  }

  /// Extracts a chapter title from a file name.
  ///
  /// Removes file extension and common prefixes/suffixes.
  String _extractChapterTitle(String fileName) {
    // Remove path and get just the filename
    final parts = fileName.split('/');
    var title = parts.last;

    // Remove file extension
    for (final ext in audioExtensions) {
      if (title.toLowerCase().endsWith(ext)) {
        title = title.substring(0, title.length - ext.length);
        break;
      }
    }

    // Remove common prefixes and patterns
    // Pattern 1: "001 - Название" or "01. Название" or "1- Название"
    title = title.replaceFirst(RegExp(r'^\d+[.\s-]+'), '');

    // Pattern 2: "Глава 1: Название" or "Глава 01. Название"
    title = title.replaceFirst(
        RegExp(r'^Глава\s*\d+[.:\s-]+', caseSensitive: false), '');

    // Pattern 3: "Часть 1: Название" or "Часть 01. Название"
    title = title.replaceFirst(
        RegExp(r'^Часть\s*\d+[.:\s-]+', caseSensitive: false), '');

    // Pattern 4: "Chapter 1: Название" or "Chapter 01. Название"
    title = title.replaceFirst(
        RegExp(r'^Chapter\s*\d+[.:\s-]+', caseSensitive: false), '');

    // Pattern 5: "Книга 1: Название" or "Книга 01. Название"
    title = title.replaceFirst(
        RegExp(r'^Книга\s*\d+[.:\s-]+', caseSensitive: false), '');

    // Clean up title - remove leading/trailing spaces, dashes, underscores
    title = title.trim();
    title = title.replaceFirst(RegExp(r'^[\s\-_]+'), '');
    title = title.replaceFirst(RegExp(r'[\s\-_]+$'), '');

    // If title is empty after cleaning, use filename without extension
    if (title.isEmpty) {
      title = parts.last;
      // Remove extension again
      for (final ext in audioExtensions) {
        if (title.toLowerCase().endsWith(ext)) {
          title = title.substring(0, title.length - ext.length);
          break;
        }
      }
      title = title.trim();
    }

    return title;
  }

  /// Sorts chapters by their original order in torrent (fileIndex).
  ///
  /// Files in torrent info.files are already in correct order according to BEP 0003.
  /// This method preserves that order and extracts cleaned titles.
  /// Also extracts cleaned title from temporary format "originalFileName|cleanedTitle".
  List<Chapter> _sortChapters(List<Chapter> chapters) {
    // Extract cleaned titles and prepare for sorting
    final chaptersWithData =
        <({Chapter chapter, String originalFileName, String cleanedTitle})>[];

    for (var i = 0; i < chapters.length; i++) {
      final chapter = chapters[i];

      // Extract original filename and cleaned title from temporary format
      String originalFileName;
      String cleanedTitle;
      if (chapter.title.contains('|')) {
        final parts = chapter.title.split('|');
        originalFileName = parts[0];
        cleanedTitle = parts.length > 1 ? parts[1] : originalFileName;
      } else {
        // Fallback if format is not as expected
        originalFileName = chapter.title;
        cleanedTitle = chapter.title;
      }

      chaptersWithData.add((
        chapter: chapter,
        originalFileName: originalFileName,
        cleanedTitle: cleanedTitle,
      ));
    }

    // Sort by fileIndex to preserve original torrent order
    // Files in torrent are already in correct order, so we just need to maintain it
    chaptersWithData
        .sort((a, b) => a.chapter.fileIndex.compareTo(b.chapter.fileIndex));

    // Restore cleaned titles (duration remains 0 - not calculated)
    final sortedChapters = chaptersWithData.map((item) {
      final chapter = item.chapter;

      return Chapter(
        title: item.cleanedTitle, // Use cleaned title, not temporary format
        durationMs: chapter.durationMs, // Keep original duration (0)
        fileIndex: chapter.fileIndex,
        startByte: chapter.startByte,
        endByte: chapter.endByte,
      );
    }).toList();

    return sortedChapters;
  }
}
