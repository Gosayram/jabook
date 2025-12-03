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

import 'package:jabook/core/domain/library/entities/local_audiobook.dart';
import 'package:jabook/core/domain/library/entities/local_audiobook_group.dart';
import 'package:jabook/core/library/folder_structure_analyzer.dart';
import 'package:jabook/core/utils/content_uri_service.dart';
import 'package:path/path.dart' as path;

/// Strategy for grouping files in external folders.
///
/// This class provides different grouping strategies based on folder structure type.
class ExternalFolderGroupingStrategy {
  // Private constructor to prevent instantiation
  ExternalFolderGroupingStrategy._();

  /// Groups files based on folder type.
  ///
  /// The [files] parameter is the list of files to group.
  /// The [basePath] parameter is the base path of the folder.
  /// The [folderType] parameter is the detected folder structure type.
  /// The [contentUriService] parameter is optional and used for Content URI paths.
  ///
  /// Returns a map of group paths to LocalAudiobookGroup instances.
  static Future<Map<String, LocalAudiobookGroup>> groupFiles(
    List<File> files,
    String basePath,
    ExternalFolderType folderType, {
    ContentUriService? contentUriService,
  }) async {
    switch (folderType) {
      case ExternalFolderType.singleFolder:
        return _groupAsSingleFolder(files, basePath);

      case ExternalFolderType.rootWithSubfolders:
        return _groupBySubfolder(files, basePath);

      case ExternalFolderType.authorBookStructure:
        return _groupByAuthorBook(files, basePath);

      case ExternalFolderType.seriesBookStructure:
        return _groupBySeriesBook(files, basePath);

      case ExternalFolderType.arbitrary:
        return _groupArbitrary(files, basePath);
    }
  }

  /// Groups all files in a single folder as one book.
  static Future<Map<String, LocalAudiobookGroup>> _groupAsSingleFolder(
    List<File> files,
    String basePath,
  ) async {
    final groupName = path.basename(basePath);
    final audiobooks = <LocalAudiobook>[];

    for (final file in files) {
      try {
        final stat = await file.stat();
        if (stat.type == FileSystemEntityType.file) {
          final fileName = path.basename(file.path);
          audiobooks.add(
            LocalAudiobook(
              filePath: file.path,
              fileName: fileName,
              fileSize: stat.size,
              scannedAt: DateTime.now(),
            ),
          );
        }
      } on Exception {
        // Skip files that can't be accessed
        continue;
      }
    }

    // Sort files by path for consistency
    audiobooks.sort((a, b) => a.filePath.compareTo(b.filePath));

    final group = LocalAudiobookGroup(
      groupName: groupName,
      groupPath: basePath,
      files: audiobooks,
      scannedAt: DateTime.now(),
      isExternalFolder: true,
      externalFolderType: ExternalFolderType.singleFolder,
    );

    return {basePath: group};
  }

  /// Groups files by subfolder (each subfolder = one book).
  static Future<Map<String, LocalAudiobookGroup>> _groupBySubfolder(
    List<File> files,
    String basePath,
  ) async {
    final groups = <String, LocalAudiobookGroup>{};

    for (final file in files) {
      try {
        final stat = await file.stat();
        if (stat.type != FileSystemEntityType.file) continue;

        final fileDir = path.dirname(file.path);
        final relativePath = path.relative(fileDir, from: basePath);
        final parts = path.split(relativePath);

        // First subfolder = group name
        final groupFolder =
            parts.isNotEmpty ? parts.first : path.basename(fileDir);
        final groupPath = path.join(basePath, groupFolder);

        if (!groups.containsKey(groupPath)) {
          groups[groupPath] = LocalAudiobookGroup(
            groupName: groupFolder,
            groupPath: groupPath,
            files: [],
            scannedAt: DateTime.now(),
            isExternalFolder: true,
            externalFolderType: ExternalFolderType.rootWithSubfolders,
          );
        }

        final fileName = path.basename(file.path);
        groups[groupPath]!.files.add(
              LocalAudiobook(
                filePath: file.path,
                fileName: fileName,
                fileSize: stat.size,
                scannedAt: DateTime.now(),
              ),
            );
      } on Exception {
        // Skip files that can't be accessed
        continue;
      }
    }

    // Sort files in each group
    for (final group in groups.values) {
      group.files.sort((a, b) => a.filePath.compareTo(b.filePath));
    }

    return groups;
  }

  /// Groups files by Author/Book structure.
  static Future<Map<String, LocalAudiobookGroup>> _groupByAuthorBook(
    List<File> files,
    String basePath,
  ) async {
    final groups = <String, LocalAudiobookGroup>{};

    for (final file in files) {
      try {
        final stat = await file.stat();
        if (stat.type != FileSystemEntityType.file) continue;

        final fileDir = path.dirname(file.path);
        final relativePath = path.relative(fileDir, from: basePath);
        final parts = path.split(relativePath);

        // Structure: [author, book, ...]
        if (parts.length >= 2) {
          final author = parts[0];
          final book = parts[1];
          final groupName = '$author - $book';
          final groupPath = path.join(basePath, author, book);

          if (!groups.containsKey(groupPath)) {
            groups[groupPath] = LocalAudiobookGroup(
              groupName: groupName,
              groupPath: groupPath,
              files: [],
              scannedAt: DateTime.now(),
              isExternalFolder: true,
              externalFolderType: ExternalFolderType.authorBookStructure,
            );
          }

          final fileName = path.basename(file.path);
          groups[groupPath]!.files.add(
                LocalAudiobook(
                  filePath: file.path,
                  fileName: fileName,
                  fileSize: stat.size,
                  scannedAt: DateTime.now(),
                ),
              );
        }
      } on Exception {
        // Skip files that can't be accessed
        continue;
      }
    }

    // Sort files in each group
    for (final group in groups.values) {
      group.files.sort((a, b) => a.filePath.compareTo(b.filePath));
    }

    return groups;
  }

  /// Groups files by Series/Book structure.
  static Future<Map<String, LocalAudiobookGroup>> _groupBySeriesBook(
    List<File> files,
    String basePath,
  ) =>
      _groupByAuthorBook(files, basePath);

  /// Groups Content URI entries based on folder type.
  ///
  /// The [entries] parameter is the list of Content URI entries to group.
  /// The [baseUri] parameter is the base Content URI of the folder.
  /// The [folderType] parameter is the detected folder structure type.
  /// The [contentUriService] parameter is required for Content URI paths.
  ///
  /// Returns a map of group URIs to LocalAudiobookGroup instances.
  static Future<Map<String, LocalAudiobookGroup>> groupContentUriEntries(
    List<ContentUriEntry> entries,
    String baseUri,
    ExternalFolderType folderType,
    ContentUriService contentUriService,
  ) async {
    switch (folderType) {
      case ExternalFolderType.singleFolder:
        return _groupContentUriAsSingleFolder(entries, baseUri);

      case ExternalFolderType.rootWithSubfolders:
        return _groupContentUriBySubfolder(entries, baseUri, contentUriService);

      case ExternalFolderType.authorBookStructure:
        return _groupContentUriByAuthorBook(
            entries, baseUri, contentUriService);

      case ExternalFolderType.seriesBookStructure:
        return _groupContentUriBySeriesBook(
            entries, baseUri, contentUriService);

      case ExternalFolderType.arbitrary:
        return _groupContentUriArbitrary(entries, baseUri, contentUriService);
    }
  }

  /// Groups all Content URI entries in a single folder as one book.
  static Future<Map<String, LocalAudiobookGroup>>
      _groupContentUriAsSingleFolder(
    List<ContentUriEntry> entries,
    String baseUri,
  ) async {
    final groupName = _extractNameFromUri(baseUri);
    final audiobooks = <LocalAudiobook>[];

    for (final entry in entries) {
      if (!entry.isDirectory && _isAudioFile(entry.name)) {
        audiobooks.add(
          LocalAudiobook(
            filePath: entry.uri,
            fileName: entry.name,
            fileSize: entry.size,
            scannedAt: DateTime.now(),
          ),
        );
      }
    }

    // Sort files by URI for consistency
    audiobooks.sort((a, b) => a.filePath.compareTo(b.filePath));

    final group = LocalAudiobookGroup(
      groupName: groupName,
      groupPath: baseUri,
      files: audiobooks,
      scannedAt: DateTime.now(),
      isExternalFolder: true,
      externalFolderType: ExternalFolderType.singleFolder,
    );

    return {baseUri: group};
  }

  /// Groups Content URI entries by subfolder (each subfolder = one book).
  static Future<Map<String, LocalAudiobookGroup>> _groupContentUriBySubfolder(
    List<ContentUriEntry> entries,
    String baseUri,
    ContentUriService contentUriService,
  ) async {
    final groups = <String, LocalAudiobookGroup>{};
    final audioEntries =
        entries.where((e) => !e.isDirectory && _isAudioFile(e.name)).toList();

    for (final entry in audioEntries) {
      // Extract directory URI from file URI
      final fileUri = entry.uri;
      final dirUri = _getParentUri(fileUri);

      // Get relative path from base
      final relativePath = _getRelativePathFromUri(dirUri, baseUri);
      final parts = _splitUriPath(relativePath);

      // First subfolder = group name
      final groupFolder =
          parts.isNotEmpty ? parts.first : _extractNameFromUri(dirUri);
      final groupUri = _joinUriPath(baseUri, groupFolder);

      if (!groups.containsKey(groupUri)) {
        groups[groupUri] = LocalAudiobookGroup(
          groupName: groupFolder,
          groupPath: groupUri,
          files: [],
          scannedAt: DateTime.now(),
          isExternalFolder: true,
          externalFolderType: ExternalFolderType.rootWithSubfolders,
        );
      }

      groups[groupUri]!.files.add(
            LocalAudiobook(
              filePath: entry.uri,
              fileName: entry.name,
              fileSize: entry.size,
              scannedAt: DateTime.now(),
            ),
          );
    }

    // Sort files in each group
    for (final group in groups.values) {
      group.files.sort((a, b) => a.filePath.compareTo(b.filePath));
    }

    return groups;
  }

  /// Groups Content URI entries by Author/Book structure.
  static Future<Map<String, LocalAudiobookGroup>> _groupContentUriByAuthorBook(
    List<ContentUriEntry> entries,
    String baseUri,
    ContentUriService contentUriService,
  ) async {
    final groups = <String, LocalAudiobookGroup>{};
    final audioEntries =
        entries.where((e) => !e.isDirectory && _isAudioFile(e.name)).toList();

    for (final entry in audioEntries) {
      final fileUri = entry.uri;
      final dirUri = _getParentUri(fileUri);
      final relativePath = _getRelativePathFromUri(dirUri, baseUri);
      final parts = _splitUriPath(relativePath);

      // Structure: [author, book, ...]
      if (parts.length >= 2) {
        final author = parts[0];
        final book = parts[1];
        final groupName = '$author - $book';
        final groupUri = _joinUriPath(baseUri, author, book);

        if (!groups.containsKey(groupUri)) {
          groups[groupUri] = LocalAudiobookGroup(
            groupName: groupName,
            groupPath: groupUri,
            files: [],
            scannedAt: DateTime.now(),
            isExternalFolder: true,
            externalFolderType: ExternalFolderType.authorBookStructure,
          );
        }

        groups[groupUri]!.files.add(
              LocalAudiobook(
                filePath: entry.uri,
                fileName: entry.name,
                fileSize: entry.size,
                scannedAt: DateTime.now(),
              ),
            );
      }
    }

    // Sort files in each group
    for (final group in groups.values) {
      group.files.sort((a, b) => a.filePath.compareTo(b.filePath));
    }

    return groups;
  }

  /// Groups Content URI entries by Series/Book structure.
  static Future<Map<String, LocalAudiobookGroup>> _groupContentUriBySeriesBook(
    List<ContentUriEntry> entries,
    String baseUri,
    ContentUriService contentUriService,
  ) =>
      _groupContentUriByAuthorBook(entries, baseUri, contentUriService);

  /// Groups Content URI entries using arbitrary structure (fallback).
  static Future<Map<String, LocalAudiobookGroup>> _groupContentUriArbitrary(
    List<ContentUriEntry> entries,
    String baseUri,
    ContentUriService contentUriService,
  ) async {
    final groups = <String, LocalAudiobookGroup>{};
    final audioEntries =
        entries.where((e) => !e.isDirectory && _isAudioFile(e.name)).toList();

    for (final entry in audioEntries) {
      final fileUri = entry.uri;
      final dirUri = _getParentUri(fileUri);
      final relativePath = _getRelativePathFromUri(dirUri, baseUri);
      final parts = _splitUriPath(relativePath);

      // Use the deepest meaningful folder as group
      String groupName;
      String groupUri;

      if (parts.isNotEmpty) {
        // Find the last non-empty part
        String? lastPart;
        for (var i = parts.length - 1; i >= 0; i--) {
          if (parts[i].isNotEmpty) {
            lastPart = parts[i];
            break;
          }
        }

        groupName = lastPart ?? _extractNameFromUri(dirUri);
        groupUri = dirUri;
      } else {
        // File is directly in base path
        groupName = _extractNameFromUri(baseUri);
        groupUri = baseUri;
      }

      if (!groups.containsKey(groupUri)) {
        groups[groupUri] = LocalAudiobookGroup(
          groupName: groupName,
          groupPath: groupUri,
          files: [],
          scannedAt: DateTime.now(),
          isExternalFolder: true,
          externalFolderType: ExternalFolderType.arbitrary,
        );
      }

      groups[groupUri]!.files.add(
            LocalAudiobook(
              filePath: entry.uri,
              fileName: entry.name,
              fileSize: entry.size,
              scannedAt: DateTime.now(),
            ),
          );
    }

    // Sort files in each group
    for (final group in groups.values) {
      group.files.sort((a, b) => a.filePath.compareTo(b.filePath));
    }

    return groups;
  }

  /// Groups files using arbitrary structure (fallback).
  ///
  /// Uses the existing _extractGroupInfo logic from AudiobookLibraryScanner.
  static Future<Map<String, LocalAudiobookGroup>> _groupArbitrary(
    List<File> files,
    String basePath,
  ) async {
    final groups = <String, LocalAudiobookGroup>{};

    for (final file in files) {
      try {
        final stat = await file.stat();
        if (stat.type != FileSystemEntityType.file) continue;

        // Use simple grouping: group by directory
        final fileDir = path.dirname(file.path);
        final relativePath = path.relative(fileDir, from: basePath);
        final parts = path.split(relativePath);

        // Use the deepest meaningful folder as group
        String groupName;
        String groupPath;

        if (parts.isNotEmpty) {
          // Find the last non-empty part
          String? lastPart;
          for (var i = parts.length - 1; i >= 0; i--) {
            if (parts[i].isNotEmpty) {
              lastPart = parts[i];
              break;
            }
          }

          groupName = lastPart ?? path.basename(fileDir);
          groupPath = fileDir;
        } else {
          // File is directly in base path
          groupName = path.basename(basePath);
          groupPath = basePath;
        }

        if (!groups.containsKey(groupPath)) {
          groups[groupPath] = LocalAudiobookGroup(
            groupName: groupName,
            groupPath: groupPath,
            files: [],
            scannedAt: DateTime.now(),
            isExternalFolder: true,
            externalFolderType: ExternalFolderType.arbitrary,
          );
        }

        final fileName = path.basename(file.path);
        groups[groupPath]!.files.add(
              LocalAudiobook(
                filePath: file.path,
                fileName: fileName,
                fileSize: stat.size,
                scannedAt: DateTime.now(),
              ),
            );
      } on Exception {
        // Skip files that can't be accessed
        continue;
      }
    }

    // Sort files in each group
    for (final group in groups.values) {
      group.files.sort((a, b) => a.filePath.compareTo(b.filePath));
    }

    return groups;
  }

  // Helper methods for Content URI manipulation

  /// Extracts name from Content URI.
  static String _extractNameFromUri(String uri) {
    try {
      final uriObj = Uri.parse(uri);
      final pathSegments = uriObj.pathSegments;
      if (pathSegments.isNotEmpty) {
        final lastPart = pathSegments.last;
        // Decode URI encoding
        return Uri.decodeComponent(lastPart);
      }
      // Try to extract from full path
      final uriParts = uri.split('/');
      if (uriParts.isNotEmpty) {
        final lastPart = uriParts.last;
        return Uri.decodeComponent(lastPart);
      }
    } on Exception {
      // Fall through to default
    }
    return 'Unknown';
  }

  /// Gets parent URI from a Content URI.
  static String _getParentUri(String uri) {
    try {
      final uriObj = Uri.parse(uri);
      final pathSegments = uriObj.pathSegments;
      if (pathSegments.length > 1) {
        final parentSegments = pathSegments.sublist(0, pathSegments.length - 1);
        return uriObj.replace(pathSegments: parentSegments).toString();
      }
      // If only one segment, parent is the base URI
      // For tree URIs, parent might be the tree root
      if (uri.contains('/tree/')) {
        final treeIndex = uri.indexOf('/tree/');
        if (treeIndex != -1) {
          return uri.substring(0, treeIndex + 6); // Include '/tree/'
        }
      }
    } on Exception {
      // Fall through to return original
    }
    return uri;
  }

  /// Gets relative path from base URI.
  static String _getRelativePathFromUri(String uri, String baseUri) {
    try {
      final uriPath = Uri.parse(uri).path;
      final basePath = Uri.parse(baseUri).path;

      if (uriPath.startsWith(basePath)) {
        final relative = uriPath.substring(basePath.length);
        return relative.startsWith('/') ? relative.substring(1) : relative;
      }
      // If paths don't match, try to extract from URI segments
      final uriSegments = Uri.parse(uri).pathSegments;
      final baseSegments = Uri.parse(baseUri).pathSegments;

      if (uriSegments.length > baseSegments.length) {
        final relativeSegments = uriSegments.sublist(baseSegments.length);
        return relativeSegments.join('/');
      }
    } on Exception {
      // Fall through
    }
    return '';
  }

  /// Splits URI path into parts.
  static List<String> _splitUriPath(String relativePath) {
    if (relativePath.isEmpty) return [];
    return relativePath
        .split('/')
        .where((p) => p.isNotEmpty)
        .map(Uri.decodeComponent)
        .toList();
  }

  /// Joins URI path segments.
  static String _joinUriPath(String baseUri, String segment1,
      [String? segment2]) {
    try {
      final uri = Uri.parse(baseUri);
      final segments = List<String>.from(uri.pathSegments)
        ..add(Uri.encodeComponent(segment1));
      if (segment2 != null) {
        segments.add(Uri.encodeComponent(segment2));
      }
      return uri.replace(pathSegments: segments).toString();
    } on Exception {
      // Fallback: simple string concatenation
      final separator = baseUri.endsWith('/') ? '' : '/';
      if (segment2 != null) {
        return '$baseUri$separator${Uri.encodeComponent(segment1)}/${Uri.encodeComponent(segment2)}';
      }
      return '$baseUri$separator${Uri.encodeComponent(segment1)}';
    }
  }

  /// Checks if file is an audio file by extension.
  static bool _isAudioFile(String fileName) {
    const extensions = [
      '.mp3',
      '.m4a',
      '.m4b',
      '.aac',
      '.flac',
      '.wav',
      '.ogg',
      '.opus'
    ];
    final lowerName = fileName.toLowerCase();
    return extensions.any(lowerName.endsWith);
  }
}
