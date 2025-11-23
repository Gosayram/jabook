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
import 'dart:io';

import 'package:jabook/core/library/folder_filter_service.dart';
import 'package:jabook/core/library/local_audiobook.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/core/utils/content_uri_service.dart';
import 'package:jabook/core/utils/storage_path_utils.dart';
import 'package:path/path.dart' as path;

/// Service for scanning the file system for audiobook files.
///
/// This service provides methods to scan directories recursively
/// for audio files and create LocalAudiobook instances.
class AudiobookLibraryScanner {
  /// Creates a new AudiobookLibraryScanner instance.
  AudiobookLibraryScanner({
    FolderFilterService? folderFilterService,
    ContentUriService? contentUriService,
  })  : _folderFilterService = folderFilterService,
        _contentUriService = contentUriService ??
            (Platform.isAndroid ? ContentUriService() : null);

  final StructuredLogger _logger = StructuredLogger();
  final FolderFilterService? _folderFilterService;
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

  /// Supported cover image file extensions.
  static const List<String> _coverExtensions = [
    '.jpg',
    '.jpeg',
    '.png',
    '.gif',
    '.webp',
  ];

  /// Common cover file names.
  static const List<String> _coverFileNames = [
    'cover',
    'folder',
    'album',
    'artwork',
    'art',
  ];

  /// Scans the default audiobook directory recursively for audio files.
  ///
  /// Returns a list of LocalAudiobook instances found in the default directory.
  Future<List<LocalAudiobook>> scanDefaultDirectory() async {
    try {
      final storageUtils = StoragePathUtils();
      final defaultPath = await storageUtils.getDefaultAudiobookPath();
      await _logger.log(
        level: 'info',
        subsystem: 'library_scanner',
        message: 'Scanning default directory for audiobooks',
        extra: {'path': defaultPath},
      );
      return scanDirectory(defaultPath, recursive: true);
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'library_scanner',
        message: 'Failed to scan default directory',
        extra: {'error': e.toString()},
      );
      return [];
    }
  }

  /// Scans the default audiobook directory and groups files by folders.
  ///
  /// Returns a list of LocalAudiobookGroup instances found in the default directory.
  Future<List<LocalAudiobookGroup>> scanDefaultDirectoryGrouped() async {
    try {
      final storageUtils = StoragePathUtils();
      final defaultPath = await storageUtils.getDefaultAudiobookPath();
      await _logger.log(
        level: 'info',
        subsystem: 'library_scanner',
        message: 'Scanning default directory for audiobook groups',
        extra: {'path': defaultPath},
      );
      return scanDirectoryGrouped(defaultPath, recursive: true);
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'library_scanner',
        message: 'Failed to scan default directory for groups',
        extra: {'error': e.toString()},
      );
      return [];
    }
  }

  /// Scans multiple directories and combines results.
  ///
  /// The [directoryPaths] parameter is a list of directory paths to scan.
  /// The [recursive] parameter determines whether to scan subdirectories.
  ///
  /// Returns a list of LocalAudiobookGroup instances found in all directories.
  Future<List<LocalAudiobookGroup>> scanMultipleDirectories(
    List<String> directoryPaths, {
    bool recursive = true,
  }) async {
    final allGroups = <LocalAudiobookGroup>[];
    final groupsByPath = <String, LocalAudiobookGroup>{};

    await _logger.log(
      level: 'info',
      subsystem: 'library_scanner',
      message: 'Scanning multiple directories',
      extra: {
        'count': directoryPaths.length,
        'paths': directoryPaths,
        'recursive': recursive,
      },
    );

    for (final directoryPath in directoryPaths) {
      try {
        final groups = await scanDirectoryGrouped(
          directoryPath,
          recursive: recursive,
        );

        // Merge groups by groupPath to avoid duplicates
        for (final group in groups) {
          if (groupsByPath.containsKey(group.groupPath)) {
            // Group already exists, merge files
            final existingGroup = groupsByPath[group.groupPath]!;
            final mergedFiles = <LocalAudiobook>[
              ...existingGroup.files,
              ...group.files,
            ]..sort((a, b) {
                // Sort by full file path to preserve folder structure order
                // This ensures files from different folders (parts of book) are in correct order
                final pathCompare = a.filePath.compareTo(b.filePath);
                if (pathCompare != 0) {
                  return pathCompare;
                }
                // If paths are equal (shouldn't happen), fall back to filename
                return a.fileName.compareTo(b.fileName);
              });

            groupsByPath[group.groupPath] = existingGroup.copyWith(
              files: mergedFiles,
            );
          } else {
            groupsByPath[group.groupPath] = group;
          }
        }

        await _logger.log(
          level: 'info',
          subsystem: 'library_scanner',
          message: 'Scanned directory',
          extra: {
            'path': directoryPath,
            'groupsFound': groups.length,
          },
        );
      } on Exception catch (e) {
        await _logger.log(
          level: 'warning',
          subsystem: 'library_scanner',
          message: 'Failed to scan directory',
          extra: {
            'path': directoryPath,
            'error': e.toString(),
          },
        );
        // Continue scanning other directories
      }
    }

    allGroups.addAll(groupsByPath.values);

    await _logger.log(
      level: 'info',
      subsystem: 'library_scanner',
      message: 'Multiple directory scan completed',
      extra: {
        'directoriesScanned': directoryPaths.length,
        'totalGroupsFound': allGroups.length,
      },
    );

    return allGroups;
  }

  /// Scans all configured library folders.
  ///
  /// Returns a list of LocalAudiobookGroup instances found in all library folders.
  Future<List<LocalAudiobookGroup>> scanAllLibraryFolders() async {
    try {
      final storageUtils = StoragePathUtils();
      var folders = await storageUtils.getLibraryFolders();

      // Apply folder filters if available
      if (_folderFilterService != null) {
        folders = await _folderFilterService.filterFolders(folders);
        await _logger.log(
          level: 'info',
          subsystem: 'library_scanner',
          message: 'Applied folder filters',
          extra: {
            'originalCount': folders.length,
            'filteredCount': folders.length,
          },
        );
      }

      await _logger.log(
        level: 'info',
        subsystem: 'library_scanner',
        message: 'Scanning all library folders',
        extra: {
          'folderCount': folders.length,
          'folders': folders,
        },
      );
      return scanMultipleDirectories(folders);
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'library_scanner',
        message: 'Failed to scan all library folders',
        extra: {'error': e.toString()},
      );
      return [];
    }
  }

  /// Scans a directory for audio files.
  ///
  /// The [directoryPath] parameter is the path to the directory to scan.
  /// The [recursive] parameter determines whether to scan subdirectories.
  ///
  /// Returns a list of LocalAudiobook instances found in the directory.
  Future<List<LocalAudiobook>> scanDirectory(
    String directoryPath, {
    bool recursive = false,
  }) async {
    final audiobooks = <LocalAudiobook>[];
    final scannedAt = DateTime.now();

    try {
      // Check if this is a content URI and use ContentResolver if available
      if (StoragePathUtils.isContentUri(directoryPath) &&
          Platform.isAndroid &&
          _contentUriService != null) {
        // Use ContentResolver for content URIs on Android
        try {
          final hasAccess =
              await _contentUriService.checkUriAccess(directoryPath);
          if (!hasAccess) {
            await _logger.log(
              level: 'error',
              subsystem: 'library_scanner',
              message: 'No access to content URI',
              extra: {'uri': directoryPath},
            );
            return audiobooks;
          }

          // Scan using ContentResolver
          return await _scanDirectoryViaContentResolver(
              directoryPath, recursive);
        } on Exception catch (e) {
          await _logger.log(
            level: 'error',
            subsystem: 'library_scanner',
            message: 'Failed to scan via ContentResolver, trying file path',
            extra: {'uri': directoryPath, 'error': e.toString()},
          );
          // Fall through to try file path conversion
        }
      }

      // Convert content URI to file path if needed (fallback)
      var actualPath = directoryPath;
      if (StoragePathUtils.isContentUri(directoryPath)) {
        final convertedPath = StoragePathUtils.convertUriToPath(directoryPath);
        if (convertedPath != null) {
          actualPath = convertedPath;
          await _logger.log(
            level: 'info',
            subsystem: 'library_scanner',
            message: 'Converted content URI to file path',
            extra: {
              'originalUri': directoryPath,
              'convertedPath': actualPath,
            },
          );
        } else {
          await _logger.log(
            level: 'error',
            subsystem: 'library_scanner',
            message: 'Cannot convert content URI to file path',
            extra: {'uri': directoryPath},
          );
          return audiobooks;
        }
      }

      final dir = Directory(actualPath);
      if (!await dir.exists()) {
        await _logger.log(
          level: 'warning',
          subsystem: 'library_scanner',
          message: 'Directory does not exist',
          extra: {'path': actualPath, 'originalPath': directoryPath},
        );
        return audiobooks;
      }

      await _logger.log(
        level: 'info',
        subsystem: 'library_scanner',
        message: 'Starting directory scan',
        extra: {
          'path': actualPath,
          'originalPath': directoryPath,
          'recursive': recursive,
        },
      );

      final files = await _scanDirectoryRecursive(dir, recursive);
      await _logger.log(
        level: 'info',
        subsystem: 'library_scanner',
        message: 'Found audio files',
        extra: {'count': files.length},
      );

      for (final file in files) {
        try {
          final stat = await file.stat();
          if (stat.type == FileSystemEntityType.file) {
            final fileName = path.basename(file.path);
            final audiobook = LocalAudiobook(
              filePath: file.path,
              fileName: fileName,
              fileSize: stat.size,
              scannedAt: scannedAt,
            );
            audiobooks.add(audiobook);
          }
        } on Exception catch (e) {
          await _logger.log(
            level: 'warning',
            subsystem: 'library_scanner',
            message: 'Failed to process file',
            extra: {
              'path': file.path,
              'error': e.toString(),
            },
          );
        }
      }

      await _logger.log(
        level: 'info',
        subsystem: 'library_scanner',
        message: 'Directory scan completed',
        extra: {
          'path': actualPath,
          'originalPath': directoryPath,
          'audiobooksFound': audiobooks.length,
        },
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'library_scanner',
        message: 'Failed to scan directory',
        extra: {
          'path': directoryPath,
          'error': e.toString(),
        },
      );
    }

    return audiobooks;
  }

  /// Scans a directory using ContentResolver (for content:// URIs on Android).
  ///
  /// The [uri] parameter is the content URI to scan.
  /// The [recursive] parameter determines whether to scan subdirectories.
  ///
  /// Returns a list of LocalAudiobook instances found.
  Future<List<LocalAudiobook>> _scanDirectoryViaContentResolver(
    String uri,
    bool recursive,
  ) async {
    final audiobooks = <LocalAudiobook>[];
    final scannedAt = DateTime.now();

    try {
      if (_contentUriService == null) {
        await _logger.log(
          level: 'error',
          subsystem: 'library_scanner',
          message: 'ContentUriService is not available',
          extra: {'uri': uri},
        );
        return audiobooks;
      }

      await _logger.log(
        level: 'info',
        subsystem: 'library_scanner',
        message: 'Scanning directory via ContentResolver',
        extra: {'uri': uri, 'recursive': recursive},
      );

      final entries = await _contentUriService.listDirectory(uri);

      for (final entry in entries) {
        try {
          if (entry.isDirectory && recursive) {
            // Recursively scan subdirectories
            final subAudiobooks =
                await _scanDirectoryViaContentResolver(entry.uri, recursive);
            audiobooks.addAll(subAudiobooks);
          } else if (!entry.isDirectory && _isAudioFile(entry.name)) {
            // Create LocalAudiobook from entry
            final audiobook = LocalAudiobook(
              filePath: entry.uri, // Use URI as path for content:// files
              fileName: entry.name,
              fileSize: entry.size,
              scannedAt: scannedAt,
            );
            audiobooks.add(audiobook);
          }
        } on Exception catch (e) {
          await _logger.log(
            level: 'warning',
            subsystem: 'library_scanner',
            message: 'Failed to process entry',
            extra: {
              'uri': entry.uri,
              'name': entry.name,
              'error': e.toString(),
            },
          );
        }
      }

      await _logger.log(
        level: 'info',
        subsystem: 'library_scanner',
        message: 'ContentResolver scan completed',
        extra: {
          'uri': uri,
          'audiobooksFound': audiobooks.length,
        },
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'library_scanner',
        message: 'Failed to scan directory via ContentResolver',
        extra: {
          'uri': uri,
          'error': e.toString(),
        },
      );
    }

    return audiobooks;
  }

  /// Recursively scans a directory for audio files.
  ///
  /// The [dir] parameter is the directory to scan.
  /// The [recursive] parameter determines whether to scan subdirectories.
  ///
  /// Returns a list of File instances that are audio files.
  Future<List<File>> _scanDirectoryRecursive(
    Directory dir,
    bool recursive,
  ) async {
    final audioFiles = <File>[];

    try {
      await for (final entity in dir.list()) {
        try {
          if (entity is File) {
            if (_isAudioFile(entity.path)) {
              audioFiles.add(entity);
            }
          } else if (entity is Directory && recursive) {
            // Check if directory should be scanned (folder filter)
            final shouldScan = _folderFilterService == null ||
                await _folderFilterService.shouldScanDirectory(entity);
            if (shouldScan) {
              // Recursively scan subdirectories
              final subFiles = await _scanDirectoryRecursive(entity, recursive);
              audioFiles.addAll(subFiles);
            }
          }
        } on Exception catch (e) {
          // Log but continue scanning
          await _logger.log(
            level: 'warning',
            subsystem: 'library_scanner',
            message: 'Failed to process entity',
            extra: {
              'path': entity.path,
              'error': e.toString(),
            },
          );
        }
      }
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'library_scanner',
        message: 'Failed to list directory',
        extra: {
          'path': dir.path,
          'error': e.toString(),
        },
      );
    }

    return audioFiles;
  }

  /// Scans a directory for audio files and groups them by folders.
  ///
  /// The [directoryPath] parameter is the path to the directory to scan.
  /// The [recursive] parameter determines whether to scan subdirectories.
  ///
  /// Returns a list of LocalAudiobookGroup instances found in the directory.
  Future<List<LocalAudiobookGroup>> scanDirectoryGrouped(
    String directoryPath, {
    bool recursive = false,
  }) async {
    final groups = <String, LocalAudiobookGroup>{};
    final scannedAt = DateTime.now();

    try {
      final dir = Directory(directoryPath);
      if (!await dir.exists()) {
        await _logger.log(
          level: 'warning',
          subsystem: 'library_scanner',
          message: 'Directory does not exist',
          extra: {'path': directoryPath},
        );
        return [];
      }

      await _logger.log(
        level: 'info',
        subsystem: 'library_scanner',
        message: 'Starting grouped directory scan',
        extra: {
          'path': directoryPath,
          'recursive': recursive,
        },
      );

      final files = await _scanDirectoryRecursive(dir, recursive);
      await _logger.log(
        level: 'info',
        subsystem: 'library_scanner',
        message: 'Found audio files',
        extra: {'count': files.length},
      );

      // Group files by their folder structure
      for (final file in files) {
        try {
          final stat = await file.stat();
          if (stat.type == FileSystemEntityType.file) {
            final fileName = path.basename(file.path);
            final audiobook = LocalAudiobook(
              filePath: file.path,
              fileName: fileName,
              fileSize: stat.size,
              scannedAt: scannedAt,
            );

            // Extract group information from file path
            final groupInfo = await _extractGroupInfo(file.path, directoryPath);
            final groupKey = groupInfo['groupPath'] as String;

            if (groups.containsKey(groupKey)) {
              // Add file to existing group
              final existingGroup = groups[groupKey]!;
              final updatedFiles =
                  List<LocalAudiobook>.from(existingGroup.files)
                    ..add(audiobook)
                    ..sort((a, b) {
                      // Sort by full file path to preserve folder structure order
                      // This ensures files from different folders (parts of book) are in correct order
                      final pathCompare = a.filePath.compareTo(b.filePath);
                      if (pathCompare != 0) {
                        return pathCompare;
                      }
                      // If paths are equal (shouldn't happen), fall back to filename
                      return a.fileName.compareTo(b.fileName);
                    });
              groups[groupKey] = existingGroup.copyWith(files: updatedFiles);
            } else {
              // Create new group
              groups[groupKey] = LocalAudiobookGroup(
                groupName: groupInfo['groupName'] as String,
                groupPath: groupKey,
                torrentId: groupInfo['torrentId'] as String?,
                files: [audiobook],
                scannedAt: scannedAt,
              );
            }
          }
        } on Exception catch (e) {
          await _logger.log(
            level: 'warning',
            subsystem: 'library_scanner',
            message: 'Failed to process file',
            extra: {
              'path': file.path,
              'error': e.toString(),
            },
          );
        }
      }

      // Find cover images for each group
      final groupsList = groups.values.toList();
      for (final group in groupsList) {
        final coverPath = await _findCoverImage(group.groupPath);
        if (coverPath != null) {
          groups[group.groupPath] = group.copyWith(coverPath: coverPath);
        }
      }

      await _logger.log(
        level: 'info',
        subsystem: 'library_scanner',
        message: 'Grouped directory scan completed',
        extra: {
          'path': directoryPath,
          'groupsFound': groups.length,
        },
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'library_scanner',
        message: 'Failed to scan directory for groups',
        extra: {
          'path': directoryPath,
          'error': e.toString(),
        },
      );
    }

    return groups.values.toList();
  }

  /// Extracts group information from a file path.
  ///
  /// Returns a map with 'groupName', 'groupPath', and optionally 'torrentId'.
  Future<Map<String, dynamic>> _extractGroupInfo(
    String filePath,
    String basePath,
  ) async {
    try {
      // Normalize paths
      final normalizedFilePath = path.normalize(filePath);
      final normalizedBasePath = path.normalize(basePath);

      // Get relative path from base
      if (!normalizedFilePath.startsWith(normalizedBasePath)) {
        // If file is not under base path, use file's directory as group
        final fileDir = path.dirname(normalizedFilePath);
        final dirName = path.basename(fileDir);
        return {
          'groupName': dirName,
          'groupPath': fileDir,
          'torrentId': null,
        };
      }

      final relativePath =
          path.relative(normalizedFilePath, from: normalizedBasePath);
      final parts = path.split(relativePath);

      // Find numeric folder (torrent ID)
      String? torrentId;
      int? numericFolderIndex;
      for (var i = 0; i < parts.length; i++) {
        final part = parts[i];
        // Check if folder name is numeric (torrent ID)
        if (RegExp(r'^\d+$').hasMatch(part)) {
          torrentId = part;
          numericFolderIndex = i;
          break;
        }
      }

      String groupName;
      String groupPath;

      if (numericFolderIndex != null && numericFolderIndex < parts.length - 1) {
        // There's a folder after numeric folder - use it as group name
        final groupFolderIndex = numericFolderIndex + 1;
        groupName = parts[groupFolderIndex];
        // Build path up to group folder (this includes all nested subfolders)
        final pathParts = parts.sublist(0, groupFolderIndex + 1);
        groupPath = path.joinAll([normalizedBasePath, ...pathParts]);

        // If there are more folders after the group folder, they are considered
        // part of the same group (nested structure)
        // All files in subfolders will be grouped together
      } else if (numericFolderIndex != null) {
        // Numeric folder exists but no folder after it - use numeric folder as group
        groupName = parts[numericFolderIndex];
        final pathParts = parts.sublist(0, numericFolderIndex + 1);
        groupPath = path.joinAll([normalizedBasePath, ...pathParts]);
      } else {
        // No numeric folder found - check if we're in a nested structure
        // Use the deepest non-numeric folder that contains audio files as group
        final fileDir = path.dirname(normalizedFilePath);
        final fileDirParts =
            path.split(path.relative(fileDir, from: normalizedBasePath));

        // Find the first meaningful folder (not empty, not just a number)
        String? meaningfulFolder;
        int? meaningfulIndex;
        for (var i = fileDirParts.length - 1; i >= 0; i--) {
          final part = fileDirParts[i];
          if (part.isNotEmpty && !RegExp(r'^\d+$').hasMatch(part)) {
            meaningfulFolder = part;
            meaningfulIndex = i;
            break;
          }
        }

        if (meaningfulFolder != null && meaningfulIndex != null) {
          // Use the meaningful folder as group
          groupName = meaningfulFolder;
          final pathParts = fileDirParts.sublist(0, meaningfulIndex + 1);
          groupPath = path.joinAll([normalizedBasePath, ...pathParts]);
        } else {
          // Fallback to file's directory
          groupName = path.basename(fileDir);
          groupPath = fileDir;
        }
      }

      return {
        'groupName': groupName,
        'groupPath': path.normalize(groupPath),
        'torrentId': torrentId,
      };
    } on Exception catch (e) {
      // Fallback to file's directory
      final fileDir = path.dirname(filePath);
      final dirName = path.basename(fileDir);
      await _logger.log(
        level: 'warning',
        subsystem: 'library_scanner',
        message: 'Failed to extract group info, using file directory',
        extra: {
          'path': filePath,
          'error': e.toString(),
        },
      );
      return {
        'groupName': dirName,
        'groupPath': fileDir,
        'torrentId': null,
      };
    }
  }

  /// Finds a cover image in the specified directory.
  ///
  /// Returns the path to the cover image if found, null otherwise.
  Future<String?> _findCoverImage(String directoryPath) async {
    try {
      final dir = Directory(directoryPath);
      if (!await dir.exists()) {
        return null;
      }

      // First, try common cover file names
      for (final coverName in _coverFileNames) {
        for (final ext in _coverExtensions) {
          final coverPath = path.join(directoryPath, '$coverName$ext');
          final coverFile = File(coverPath);
          if (await coverFile.exists()) {
            return coverPath;
          }
        }
      }

      // If no common cover found, look for any image file
      // (but only if there's exactly one image file)
      final imageFiles = <File>[];
      await for (final entity in dir.list()) {
        if (entity is File) {
          final ext = path.extension(entity.path).toLowerCase();
          if (_coverExtensions.contains(ext)) {
            imageFiles.add(entity);
          }
        }
      }

      // If there's exactly one image file, use it as cover
      if (imageFiles.length == 1) {
        return imageFiles.first.path;
      }
    } on Exception catch (e) {
      await _logger.log(
        level: 'warning',
        subsystem: 'library_scanner',
        message: 'Failed to find cover image',
        extra: {
          'path': directoryPath,
          'error': e.toString(),
        },
      );
    }

    return null;
  }

  /// Checks if a file path is an audio file.
  ///
  /// The [filePath] parameter is the path to check.
  ///
  /// Returns true if the file has an audio extension.
  bool _isAudioFile(String filePath) {
    final extension = path.extension(filePath).toLowerCase();
    return _audioExtensions.contains(extension);
  }
}
