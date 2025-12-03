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

/// Types of external folder structures.
enum ExternalFolderType {
  /// All files in one folder - one book
  singleFolder,

  /// Each subfolder = one book
  rootWithSubfolders,

  /// Structure: Author/Book/Files
  authorBookStructure,

  /// Structure: Series/Book/Files
  seriesBookStructure,

  /// Arbitrary structure (fallback)
  arbitrary,
}

/// Analyzer for determining the structure of external folders.
///
/// This class analyzes folder structures to determine how files should be grouped
/// for external (non-torrent) folders.
class FolderStructureAnalyzer {
  // Private constructor to prevent instantiation
  FolderStructureAnalyzer._();

  /// Supported audio file extensions.
  static const List<String> _audioExtensions = [
    '.mp3',
    '.m4a',
    '.m4b',
    '.aac',
    '.flac',
    '.wav',
  ];

  /// Analyzes the structure of a directory and determines its type.
  ///
  /// The [directoryPath] parameter is the path to analyze.
  /// The [contentUriService] parameter is optional and used for Content URI paths.
  ///
  /// Returns the detected folder type.
  static Future<ExternalFolderType> analyzeStructure(
    String directoryPath, {
    ContentUriService? contentUriService,
  }) async {
    // Check if this is a Content URI
    if (StoragePathUtils.isContentUri(directoryPath)) {
      return _analyzeContentUriStructure(
        directoryPath,
        contentUriService,
      );
    }

    // Regular file path analysis
    final dir = Directory(directoryPath);
    if (!await dir.exists()) {
      return ExternalFolderType.arbitrary;
    }

    final entities = await dir.list().toList();
    final subdirs = entities.whereType<Directory>().toList();
    final files = entities.whereType<File>().toList();

    // Check for audio files in root
    final audioFilesInRoot = files.where((f) => _isAudioFile(f.path)).length;

    // If there are audio files in root and subdirectories - mixed structure
    if (audioFilesInRoot > 0 && subdirs.isNotEmpty) {
      return ExternalFolderType.arbitrary;
    }

    // If only files in root - single folder
    if (audioFilesInRoot > 0 && subdirs.isEmpty) {
      return ExternalFolderType.singleFolder;
    }

    // If only subdirectories - check structure
    if (subdirs.isNotEmpty && audioFilesInRoot == 0) {
      // Check if subdirectories contain audio files
      var hasAudioInSubdirs = false;
      for (final subdir in subdirs.take(3)) {
        // Check first 3 subdirectories to determine structure
        final subFiles = await subdir.list().toList();
        hasAudioInSubdirs = subFiles.any(
          (e) => e is File && _isAudioFile(e.path),
        );
        if (hasAudioInSubdirs) break;
      }

      if (hasAudioInSubdirs) {
        // Check if there's another level of nesting
        final firstSubdir = subdirs.first;
        final firstSubdirEntities = await firstSubdir.list().toList();
        final firstSubdirSubdirs =
            firstSubdirEntities.whereType<Directory>().toList();

        if (firstSubdirSubdirs.isNotEmpty) {
          // Two levels - possibly Author/Book or Series/Book structure
          // Check if second level contains audio files
          var hasAudioInSecondLevel = false;
          for (final secondLevelDir in firstSubdirSubdirs.take(2)) {
            final secondLevelFiles = await secondLevelDir.list().toList();
            hasAudioInSecondLevel = secondLevelFiles.any(
              (e) => e is File && _isAudioFile(e.path),
            );
            if (hasAudioInSecondLevel) break;
          }

          if (hasAudioInSecondLevel) {
            // This looks like Author/Book or Series/Book structure
            // Default to authorBookStructure (can be refined later)
            return ExternalFolderType.authorBookStructure;
          } else {
            // More than 2 levels - arbitrary
            return ExternalFolderType.arbitrary;
          }
        } else {
          // One level - Root with subfolders
          return ExternalFolderType.rootWithSubfolders;
        }
      }
    }

    return ExternalFolderType.arbitrary;
  }

  /// Analyzes Content URI structure.
  static Future<ExternalFolderType> _analyzeContentUriStructure(
    String uri,
    ContentUriService? contentUriService,
  ) async {
    if (contentUriService == null) {
      return ExternalFolderType.arbitrary;
    }

    try {
      // Check access first
      final hasAccess = await contentUriService.checkUriAccess(uri);
      if (!hasAccess) {
        return ExternalFolderType.arbitrary;
      }

      final entries = await contentUriService.listDirectory(uri);
      final subdirs = entries.where((e) => e.isDirectory).toList();
      final files = entries.where((e) => !e.isDirectory).toList();

      // Check for audio files in root
      final audioFilesInRoot = files.where((f) => _isAudioFile(f.name)).length;

      // If there are audio files in root and subdirectories - mixed structure
      if (audioFilesInRoot > 0 && subdirs.isNotEmpty) {
        return ExternalFolderType.arbitrary;
      }

      // If only files in root - single folder
      if (audioFilesInRoot > 0 && subdirs.isEmpty) {
        return ExternalFolderType.singleFolder;
      }

      // If only subdirectories - check structure
      if (subdirs.isNotEmpty && audioFilesInRoot == 0) {
        // Check if subdirectories contain audio files
        var hasAudioInSubdirs = false;
        for (final subdir in subdirs.take(3)) {
          try {
            final subEntries =
                await contentUriService.listDirectory(subdir.uri);
            hasAudioInSubdirs = subEntries.any(
              (e) => !e.isDirectory && _isAudioFile(e.name),
            );
            if (hasAudioInSubdirs) break;
          } on Exception {
            // Skip if can't access subdirectory
            continue;
          }
        }

        if (hasAudioInSubdirs) {
          // Check if there's another level of nesting
          final firstSubdir = subdirs.first;
          try {
            final firstSubdirEntries =
                await contentUriService.listDirectory(firstSubdir.uri);
            final firstSubdirSubdirs =
                firstSubdirEntries.where((e) => e.isDirectory).toList();

            if (firstSubdirSubdirs.isNotEmpty) {
              // Two levels - possibly Author/Book structure
              var hasAudioInSecondLevel = false;
              for (final secondLevelDir in firstSubdirSubdirs.take(2)) {
                try {
                  final secondLevelEntries =
                      await contentUriService.listDirectory(secondLevelDir.uri);
                  hasAudioInSecondLevel = secondLevelEntries.any(
                    (e) => !e.isDirectory && _isAudioFile(e.name),
                  );
                  if (hasAudioInSecondLevel) break;
                } on Exception {
                  continue;
                }
              }

              if (hasAudioInSecondLevel) {
                return ExternalFolderType.authorBookStructure;
              } else {
                return ExternalFolderType.arbitrary;
              }
            } else {
              // One level - Root with subfolders
              return ExternalFolderType.rootWithSubfolders;
            }
          } on Exception {
            // If can't access, assume root with subfolders
            return ExternalFolderType.rootWithSubfolders;
          }
        }
      }

      return ExternalFolderType.arbitrary;
    } on Exception {
      return ExternalFolderType.arbitrary;
    }
  }

  /// Checks if a file path is an audio file.
  static bool _isAudioFile(String filePath) {
    final extension = path.extension(filePath).toLowerCase();
    return _audioExtensions.contains(extension);
  }
}
