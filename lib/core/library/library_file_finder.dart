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

import 'dart:io';

import 'package:jabook/core/utils/content_uri_service.dart';
import 'package:jabook/core/utils/storage_path_utils.dart';
import 'package:path/path.dart' as path;

/// Service for finding files in library folders.
///
/// This class provides methods to find audiobook files in library folders,
/// supporting both torrent folders and external folders.
class LibraryFileFinder {
  /// Creates a new LibraryFileFinder instance.
  LibraryFileFinder({
    StoragePathUtils? storageUtils,
    ContentUriService? contentUriService,
  })  : _storageUtils = storageUtils ?? StoragePathUtils(),
        _contentUriService = contentUriService;

  final StoragePathUtils _storageUtils;
  final ContentUriService? _contentUriService;

  /// Supported audio file extensions.
  static const List<String> _audioExtensions = [
    '.mp3',
    '.m4a',
    '.m4b',
    '.aac',
    '.flac',
    '.wav',
  ];

  /// Gets all audio files for a bookId, sorted by path.
  ///
  /// The [bookId] parameter is the book ID (numeric folder name).
  ///
  /// Returns a list of file paths sorted by path, or empty list if not found.
  Future<List<String>> getAllFilesByBookId(String bookId) async {
    // First, search in download directory
    final downloadDir = await _storageUtils.getDefaultAudiobookPath();
    final possiblePaths = [
      path.join(downloadDir, bookId),
      downloadDir,
    ];

    for (final basePath in possiblePaths) {
      final files = await _getAllFilesInDirectory(basePath);
      if (files.isNotEmpty) return files;
    }

    // If not found, search in library folders
    final libraryFolders = await _storageUtils.getLibraryFolders();
    for (final libraryFolder in libraryFolders) {
      final files = await _getAllFilesInLibraryFolder(libraryFolder, bookId);
      if (files.isNotEmpty) return files;
    }

    return [];
  }

  /// Gets all audio files in a directory, sorted by path.
  Future<List<String>> _getAllFilesInDirectory(String directoryPath) async {
    if (StoragePathUtils.isContentUri(directoryPath)) {
      return _getAllFilesInContentUri(directoryPath);
    }

    final dir = Directory(directoryPath);
    if (!await dir.exists()) return [];

    final audioFiles = <File>[];
    await for (final entity in dir.list(recursive: true)) {
      if (entity is File && _isAudioFile(entity.path)) {
        audioFiles.add(entity);
      }
    }

    audioFiles.sort((a, b) => _comparePaths(a.path, b.path));
    return audioFiles.map((f) => f.path).toList();
  }

  /// Gets all audio files in a library folder by bookId.
  Future<List<String>> _getAllFilesInLibraryFolder(
    String libraryFolder,
    String bookId,
  ) async {
    final dir = Directory(libraryFolder);
    if (!await dir.exists()) return [];

    // Search for directory containing bookId
    await for (final entity in dir.list(recursive: true)) {
      if (entity is Directory) {
        if (entity.path.contains(bookId)) {
          return _getAllFilesInDirectory(entity.path);
        }
      }
    }

    return [];
  }

  /// Gets all audio files in a Content URI, sorted by URI.
  Future<List<String>> _getAllFilesInContentUri(String uri) async {
    if (_contentUriService == null) return [];

    try {
      final hasAccess = await _contentUriService.checkUriAccess(uri);
      if (!hasAccess) return [];

      final entries = await _contentUriService.listDirectory(uri);
      final audioFiles = <String>[];

      await _collectAudioFilesFromContentUri(entries, audioFiles);
      audioFiles.sort(_comparePaths);

      return audioFiles;
    } on Exception {
      return [];
    }
  }

  /// Finds a file by bookId (for torrent books).
  ///
  /// The [bookId] parameter is the book ID (numeric folder name).
  /// The [fileIndex] parameter is the index of the file in the book.
  ///
  /// Returns the file path if found, null otherwise.
  Future<String?> findFileByBookId(String bookId, int fileIndex) async {
    // First, search in download directory (as currently)
    final downloadDir = await _storageUtils.getDefaultAudiobookPath();
    final possiblePaths = [
      path.join(downloadDir, bookId),
      downloadDir,
    ];

    for (final basePath in possiblePaths) {
      final file = await _findFileInDirectory(basePath, fileIndex);
      if (file != null) return file;
    }

    // If not found, search in library folders
    final libraryFolders = await _storageUtils.getLibraryFolders();
    for (final libraryFolder in libraryFolders) {
      final file = await _findFileInLibraryFolder(
        libraryFolder,
        bookId,
        fileIndex,
      );
      if (file != null) return file;
    }

    return null;
  }

  /// Finds a file by group path (for external folders).
  ///
  /// The [groupPath] parameter is the path to the group folder.
  /// The [fileIndex] parameter is the index of the file in the group.
  ///
  /// Returns the file path if found, null otherwise.
  Future<String?> findFileByGroupPath(
    String groupPath,
    int fileIndex,
  ) async {
    // Check if this is a Content URI
    if (StoragePathUtils.isContentUri(groupPath)) {
      return _findFileInContentUri(groupPath, fileIndex);
    }

    // Check if path exists
    final dir = Directory(groupPath);
    if (!await dir.exists()) return null;

    // Get all audio files in the group
    final audioFiles = <File>[];
    await for (final entity in dir.list(recursive: true)) {
      if (entity is File && _isAudioFile(entity.path)) {
        audioFiles.add(entity);
      }
    }

    // Sort by path for consistency
    audioFiles.sort((a, b) => _comparePaths(a.path, b.path));

    // Return file by index
    if (fileIndex >= 0 && fileIndex < audioFiles.length) {
      return audioFiles[fileIndex].path;
    }

    return null;
  }

  /// Finds a file in a library folder by bookId.
  Future<String?> _findFileInLibraryFolder(
    String libraryFolder,
    String bookId,
    int fileIndex,
  ) async {
    // Search for a folder that might correspond to bookId
    // This could be a folder name containing bookId, or a path
    if (StoragePathUtils.isContentUri(libraryFolder)) {
      // For Content URI, use ContentUriService to search recursively
      if (_contentUriService == null) return null;

      try {
        // Check access first
        final hasAccess =
            await _contentUriService.checkUriAccess(libraryFolder);
        if (!hasAccess) return null;

        // Recursively search for a folder with bookId in name or path
        final foundFile = await _findFileInContentUriRecursive(
          libraryFolder,
          bookId,
          fileIndex,
        );
        return foundFile;
      } on Exception {
        return null;
      }
    }

    final dir = Directory(libraryFolder);
    if (!await dir.exists()) return null;

    // Recursively search for a folder with bookId in name
    await for (final entity in dir.list(recursive: true)) {
      if (entity is Directory && entity.path.contains(bookId)) {
        // Found potential group folder
        final file = await _findFileInDirectory(entity.path, fileIndex);
        if (file != null) return file;
      }
    }

    return null;
  }

  /// Recursively searches for a file in Content URI by bookId.
  Future<String?> _findFileInContentUriRecursive(
    String baseUri,
    String bookId,
    int fileIndex,
  ) async {
    if (_contentUriService == null) return null;

    try {
      final entries = await _contentUriService.listDirectory(baseUri);

      for (final entry in entries) {
        if (entry.isDirectory) {
          // Check if directory name or path contains bookId
          if (entry.name.contains(bookId) || entry.uri.contains(bookId)) {
            // Found potential group folder - search for file by index
            final file = await _findFileInContentUri(entry.uri, fileIndex);
            if (file != null) return file;
          }

          // Recursively search in subdirectories
          final foundFile = await _findFileInContentUriRecursive(
            entry.uri,
            bookId,
            fileIndex,
          );
          if (foundFile != null) return foundFile;
        }
      }
    } on Exception {
      // Error accessing directory - continue searching
    }

    return null;
  }

  /// Finds a file in a directory by index.
  Future<String?> _findFileInDirectory(
    String directoryPath,
    int fileIndex,
  ) async {
    if (StoragePathUtils.isContentUri(directoryPath)) {
      return _findFileInContentUri(directoryPath, fileIndex);
    }

    final dir = Directory(directoryPath);
    if (!await dir.exists()) return null;

    final audioFiles = <File>[];
    await for (final entity in dir.list(recursive: true)) {
      if (entity is File && _isAudioFile(entity.path)) {
        audioFiles.add(entity);
      }
    }

    audioFiles.sort((a, b) => _comparePaths(a.path, b.path));

    if (fileIndex >= 0 && fileIndex < audioFiles.length) {
      return audioFiles[fileIndex].path;
    }

    return null;
  }

  /// Finds a file in a Content URI by index.
  Future<String?> _findFileInContentUri(
    String uri,
    int fileIndex,
  ) async {
    if (_contentUriService == null) return null;

    try {
      // Check access first
      final hasAccess = await _contentUriService.checkUriAccess(uri);
      if (!hasAccess) return null;

      // List directory
      final entries = await _contentUriService.listDirectory(uri);
      final audioFiles = <String>[];

      // Recursively collect audio files
      await _collectAudioFilesFromContentUri(entries, audioFiles);

      // Sort by URI for consistency
      audioFiles.sort(_comparePaths);

      // Return file by index
      if (fileIndex >= 0 && fileIndex < audioFiles.length) {
        return audioFiles[fileIndex];
      }
    } on Exception {
      // Error accessing Content URI
      return null;
    }

    return null;
  }

  /// Recursively collects audio files from Content URI entries.
  Future<void> _collectAudioFilesFromContentUri(
    List<ContentUriEntry> entries,
    List<String> audioFiles,
  ) async {
    if (_contentUriService == null) return;

    for (final entry in entries) {
      if (entry.isDirectory) {
        // Recursively scan subdirectories
        try {
          final subEntries = await _contentUriService.listDirectory(entry.uri);
          await _collectAudioFilesFromContentUri(subEntries, audioFiles);
        } on Exception {
          // Skip if can't access subdirectory
          continue;
        }
      } else if (_isAudioFile(entry.name)) {
        audioFiles.add(entry.uri);
      }
    }
  }

  /// Checks if a file path is an audio file.
  bool _isAudioFile(String filePath) {
    final extension = path.extension(filePath).toLowerCase();
    return _audioExtensions.contains(extension);
  }

  /// Compares two paths using natural sorting on each segment.
  ///
  /// This ensures that:
  /// 1. Directories are sorted numerically: "Vol 1/..." < "Vol 2/..." < "Vol 10/..."
  /// 2. Files are sorted numerically: "1.mp3" < "2.mp3" < "10.mp3"
  int _comparePaths(String pathA, String pathB) {
    // Split paths into segments (directories and filename)
    final partsA = path.split(pathA);
    final partsB = path.split(pathB);

    // Compare common segments
    final len = partsA.length < partsB.length ? partsA.length : partsB.length;
    for (var i = 0; i < len; i++) {
      final segA = partsA[i];
      final segB = partsB[i];

      // Skip identical segments
      if (segA == segB) continue;

      // Natural sort for differing segments
      final cmp = _naturalCompare(segA, segB);
      if (cmp != 0) return cmp;
    }

    // If all common segments are equal, shorter path comes first (parent vs child)
    return partsA.length.compareTo(partsB.length);
  }

  /// Compares two strings using natural sort order (handling embedded numbers).
  int _naturalCompare(String a, String b) {
    final nameA = a.toLowerCase();
    final nameB = b.toLowerCase();

    // Try to extract the *first* number in the string to sort by
    // LIMITATION: This is a simple natural sort. For complex strings like "v1.2.3",
    // it only looks at the first number found ("1").
    final numberA = _extractNumber(nameA);
    final numberB = _extractNumber(nameB);

    if (numberA != null && numberB != null) {
      // If the numbers are different, sort by number
      if (numberA != numberB) {
        return numberA.compareTo(numberB);
      }
      // If numbers are same (e.g. "file 01" vs "file 1"), fall back to string comparison
      // But typically we might want to be smarter. For now standard string compare is fine.
    }

    return nameA.compareTo(nameB);
  }

  /// Extracts the first number found in the string.
  /// Returns null if no number is found.
  int? _extractNumber(String text) {
    final match = RegExp(r'(\d+)').firstMatch(text);
    if (match != null) {
      return int.tryParse(match.group(0)!);
    }
    return null;
  }
}
