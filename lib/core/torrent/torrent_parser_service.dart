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

import 'package:b_encode_decode/b_encode_decode.dart';
import 'package:crypto/crypto.dart';
import 'package:dtorrent_parser/dtorrent_parser.dart';
import 'package:flutter/foundation.dart';
import 'package:jabook/core/cache/rutracker_cache_service.dart';
import 'package:jabook/core/parse/rutracker_parser.dart';

/// Service for parsing torrent files to extract chapter information.
///
/// This service extracts audio file names from torrent files
/// and creates Chapter objects based on file names.
/// It also caches parsed chapters to avoid re-parsing the same torrent.
class TorrentParserService {
  /// Cache service for storing parsed chapters.
  final RuTrackerCacheService _cacheService = RuTrackerCacheService();
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
  /// Throws [Exception] if the torrent cannot be parsed.
  Future<List<Chapter>> extractChaptersFromTorrent(
      List<int> torrentBytes, {bool forceRefresh = false}) async {
    try {
      // For large files, use isolate to avoid blocking UI
      if (torrentBytes.length > isolateThresholdBytes) {
        return await _extractChaptersInIsolate(torrentBytes, forceRefresh);
      }
      
      // For smaller files, parse directly
      return await _extractChaptersDirectly(torrentBytes, forceRefresh);
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
        final cachedChapters = await _cacheService.getCachedTorrentChapters(infoHash);
        if (cachedChapters != null && cachedChapters.isNotEmpty) {
          // Convert cached maps to Chapter objects
          return cachedChapters.map((c) => Chapter(
            title: c['title'] as String? ?? '',
            durationMs: c['duration_ms'] as int? ?? 0,
            fileIndex: c['file_index'] as int? ?? 0,
            startByte: c['start_byte'] as int? ?? 0,
            endByte: c['end_byte'] as int? ?? 0,
          )).toList();
        }
      }

      final chapters = <Chapter>[];

      // Extract files from torrent
      if (torrentModel.files.isNotEmpty) {
        // Multi-file torrent
        var fileIndex = 0;
        for (final file in torrentModel.files) {
          // Handle path as either List<String> or String
          final fileName = file.path is List
              ? (file.path as List).join('/')
              : file.path.toString();
          if (_isAudioFile(fileName)) {
            final chapterTitle = _extractChapterTitle(fileName);
            chapters.add(Chapter(
              title: chapterTitle,
              durationMs: 0, // Duration not available from torrent file
              fileIndex: fileIndex,
              startByte: 0,
              endByte: file.length,
            ));
            fileIndex++;
          }
        }
      } else {
        // Single-file torrent
        final fileName = torrentModel.name;
        if (fileName.isNotEmpty && _isAudioFile(fileName)) {
          chapters.add(Chapter(
            title: _extractChapterTitle(fileName),
            durationMs: 0,
            fileIndex: 0,
            startByte: 0,
            endByte: torrentModel.length,
          ));
        }
      }

      // Cache the parsed chapters if we have an infoHash
      if (infoHash.isNotEmpty && chapters.isNotEmpty) {
        final chaptersMap = chapters.map((c) => {
          'title': c.title,
          'duration_ms': c.durationMs,
          'file_index': c.fileIndex,
          'start_byte': c.startByte,
          'end_byte': c.endByte,
        }).toList();
        await _cacheService.cacheTorrentChapters(infoHash, chaptersMap);
      }

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
        final cachedChapters = await _cacheService.getCachedTorrentChapters(infoHash);
        if (cachedChapters != null && cachedChapters.isNotEmpty) {
          return cachedChapters.map((c) => Chapter(
            title: c['title'] as String? ?? '',
            durationMs: c['duration_ms'] as int? ?? 0,
            fileIndex: c['file_index'] as int? ?? 0,
            startByte: c['start_byte'] as int? ?? 0,
            endByte: c['end_byte'] as int? ?? 0,
          )).toList();
        }
      }
      
      // Use compute to run parsing in isolate
      final result = await compute(_parseTorrentInIsolate, torrentBytes);
      
      // Cache the parsed chapters
      if (infoHash.isNotEmpty && result.isNotEmpty) {
        final chaptersMap = result.map((c) => {
          'title': c.title,
          'duration_ms': c.durationMs,
          'file_index': c.fileIndex,
          'start_byte': c.startByte,
          'end_byte': c.endByte,
        }).toList();
        await _cacheService.cacheTorrentChapters(infoHash, chaptersMap);
      }
      
      return result;
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
      if (torrentModel.files.isNotEmpty) {
        // Multi-file torrent
        var fileIndex = 0;
        for (final file in torrentModel.files) {
          // Handle path as either List<String> or String
          final fileName = file.path is List
              ? (file.path as List).join('/')
              : file.path.toString();
          if (_isAudioFileStatic(fileName)) {
            final chapterTitle = _extractChapterTitleStatic(fileName);
            chapters.add(Chapter(
              title: chapterTitle,
              durationMs: 0, // Duration not available from torrent file
              fileIndex: fileIndex,
              startByte: 0,
              endByte: file.length,
            ));
            fileIndex++;
          }
        }
      } else {
        // Single-file torrent
        final fileName = torrentModel.name;
        if (fileName.isNotEmpty && _isAudioFileStatic(fileName)) {
          chapters.add(Chapter(
            title: _extractChapterTitleStatic(fileName),
            durationMs: 0,
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

    // Remove common prefixes
    title = title.replaceFirst(RegExp(r'^\d+[.\s-]+'), '');
    title = title.replaceFirst(RegExp(r'^Глава\s*\d+[.\s-]+', caseSensitive: false), '');
    title = title.replaceFirst(RegExp(r'^Часть\s*\d+[.\s-]+', caseSensitive: false), '');
    title = title.replaceFirst(RegExp(r'^Chapter\s*\d+[.\s-]+', caseSensitive: false), '');

    // Clean up title
    title = title.trim();
    if (title.isEmpty) {
      title = fileName.split('/').last;
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

    // Remove common prefixes
    title = title.replaceFirst(RegExp(r'^\d+[.\s-]+'), '');
    title = title.replaceFirst(RegExp(r'^Глава\s*\d+[.\s-]+', caseSensitive: false), '');
    title = title.replaceFirst(RegExp(r'^Часть\s*\d+[.\s-]+', caseSensitive: false), '');
    title = title.replaceFirst(RegExp(r'^Chapter\s*\d+[.\s-]+', caseSensitive: false), '');

    // Clean up title
    title = title.trim();
    if (title.isEmpty) {
      title = fileName.split('/').last;
    }

    return title;
  }
}

