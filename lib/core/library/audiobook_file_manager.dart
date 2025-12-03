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
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:jabook/core/library/playback_position_service.dart';
import 'package:jabook/core/library/trash_service.dart';
import 'package:jabook/core/player/media3_player_service.dart';
import 'package:jabook/core/player/native_audio_player.dart';
import 'package:path/path.dart' as path;

/// Exception thrown when trying to delete a file that is currently being played.
class FileInUseException implements Exception {
  /// Creates a new FileInUseException instance.
  FileInUseException(this.message);

  /// Error message.
  final String message;

  @override
  String toString() => 'FileInUseException: $message';
}

/// Exception thrown when there are insufficient permissions to delete a file.
class PermissionDeniedException implements Exception {
  /// Creates a new PermissionDeniedException instance.
  PermissionDeniedException(this.message, {this.filePath});

  /// Error message.
  final String message;

  /// Path to the file that couldn't be deleted.
  final String? filePath;

  @override
  String toString() => 'PermissionDeniedException: $message';
}

/// Exception thrown when a file operation fails due to insufficient storage space.
class InsufficientStorageException implements Exception {
  /// Creates a new InsufficientStorageException instance.
  InsufficientStorageException(this.message,
      {this.requiredBytes, this.availableBytes});

  /// Error message.
  final String message;

  /// Required bytes for the operation.
  final int? requiredBytes;

  /// Available bytes on the storage.
  final int? availableBytes;

  @override
  String toString() => 'InsufficientStorageException: $message';
}

/// Result of a deletion operation with details about what was deleted.
class DeletionResult {
  /// Creates a new DeletionResult instance.
  DeletionResult({
    required this.success,
    this.deletedCount = 0,
    this.failedCount = 0,
    this.totalCount = 0,
    this.errors = const [],
  });

  /// Whether the deletion was successful.
  final bool success;

  /// Number of files successfully deleted.
  final int deletedCount;

  /// Number of files that failed to delete.
  final int failedCount;

  /// Total number of files attempted to delete.
  final int totalCount;

  /// List of error messages for failed deletions.
  final List<String> errors;

  /// Whether the deletion was partially successful.
  bool get isPartialSuccess => deletedCount > 0 && failedCount > 0;
}

/// Service for managing audiobook files, including deletion and cleanup.
///
/// This service provides methods to delete audiobook files and groups,
/// clear associated data (playback positions, covers), and check if files
/// are currently in use.
class AudiobookFileManager {
  /// Creates a new AudiobookFileManager instance.
  ///
  /// The [playbackPositionService] parameter is optional - if not provided,
  /// a new instance will be created.
  /// The [trashService] parameter is optional - if not provided, files will
  /// be deleted permanently.
  /// The [media3PlayerService] parameter is optional but recommended - if not provided,
  /// a new NativeAudioPlayer instance will be created for file playing checks.
  /// For dependency injection, prefer passing an instance from [media3PlayerServiceProvider].
  AudiobookFileManager({
    PlaybackPositionService? playbackPositionService,
    TrashService? trashService,
    Media3PlayerService? media3PlayerService,
  })  : _playbackPositionService =
            playbackPositionService ?? PlaybackPositionService(),
        _trashService = trashService,
        _media3PlayerService = media3PlayerService;

  final PlaybackPositionService _playbackPositionService;
  final StructuredLogger _logger = StructuredLogger();
  final Media3PlayerService? _media3PlayerService;
  final TrashService? _trashService;

  /// Deletes a single audio file from disk.
  ///
  /// The [audiobook] parameter is the audio file to delete.
  ///
  /// Returns true if the file was deleted successfully, false otherwise.
  ///
  /// Throws [FileInUseException] if the file is currently being played.
  /// Throws [PermissionDeniedException] if there are insufficient permissions.
  Future<bool> deleteFile(LocalAudiobook audiobook) async {
    try {
      // Check if file is currently playing
      if (await _isFilePlaying(audiobook.filePath)) {
        throw FileInUseException(
          'File is currently being played: ${audiobook.fileName}',
        );
      }

      final file = File(audiobook.filePath);
      if (!await file.exists()) {
        await _logger.log(
          level: 'warning',
          subsystem: 'file_manager',
          message: 'File does not exist, skipping deletion',
          extra: {'filePath': audiobook.filePath},
        );
        return false;
      }

      // Check if file is readable/writable (permission check)
      try {
        final stat = await file.stat();
        if (stat.type == FileSystemEntityType.notFound) {
          return false;
        }
      } on FileSystemException catch (e) {
        if (e.osError?.errorCode == 13 || // Permission denied
            e.message.contains('Permission denied')) {
          throw PermissionDeniedException(
            'Permission denied: ${audiobook.fileName}',
            filePath: audiobook.filePath,
          );
        }
        rethrow;
      }

      // Try to move to trash first if trash service is available
      if (_trashService != null) {
        final trashItem = await _trashService.moveToTrash(
          audiobook.filePath,
          audiobook.title ?? audiobook.fileName,
        );
        if (trashItem != null) {
          // Successfully moved to trash
          await _logger.log(
            level: 'info',
            subsystem: 'file_manager',
            message: 'File moved to trash',
            extra: {
              'filePath': audiobook.filePath,
              'fileName': audiobook.fileName,
            },
          );
          return true;
        }
        // If trash failed, fall through to permanent deletion
      }

      // Permanent deletion (or if trash is disabled/failed)
      await file.delete();

      await _logger.log(
        level: 'info',
        subsystem: 'file_manager',
        message: 'File deleted successfully',
        extra: {
          'filePath': audiobook.filePath,
          'fileName': audiobook.fileName,
        },
      );

      return true;
    } on FileInUseException {
      rethrow;
    } on PermissionDeniedException {
      rethrow;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'file_manager',
        message: 'Failed to delete file',
        extra: {
          'filePath': audiobook.filePath,
          'error': e.toString(),
          'errorType': e.runtimeType.toString(),
        },
      );
      return false;
    }
  }

  /// Deletes all files in a group and optionally the directory.
  ///
  /// The [group] parameter is the audiobook group to delete.
  /// The [deleteDirectory] parameter determines whether to delete the directory
  /// after removing all files (default: true).
  /// The [allowPartialDeletion] parameter allows partial deletion if some files fail (default: true).
  ///
  /// Returns [DeletionResult] with details about the deletion operation.
  ///
  /// Throws [FileInUseException] if any file in the group is currently playing.
  Future<DeletionResult> deleteGroupDetailed(
    LocalAudiobookGroup group, {
    bool deleteDirectory = true,
    bool allowPartialDeletion = true,
  }) async {
    try {
      // Check if any file in the group is currently playing
      for (final file in group.files) {
        if (await _isFilePlaying(file.filePath)) {
          throw FileInUseException(
            'Group is currently being played: ${group.groupName}',
          );
        }
      }

      // Try to move entire group to trash first if trash service is available
      if (_trashService != null) {
        final trashItem = await _trashService.moveGroupToTrash(group);
        if (trashItem != null) {
          // Successfully moved to trash
          await _logger.log(
            level: 'info',
            subsystem: 'file_manager',
            message: 'Group moved to trash',
            extra: {
              'groupPath': group.groupPath,
              'groupName': group.groupName,
            },
          );
          // Clear playback position data
          await clearPlaybackData(group.groupPath);
          return DeletionResult(
            success: true,
            deletedCount: group.files.length,
            totalCount: group.files.length,
          );
        }
        // If trash failed, fall through to individual file deletion
      }

      var deletedCount = 0;
      var failedCount = 0;
      final errors = <String>[];

      // Delete all files in the group
      for (final file in group.files) {
        try {
          final success = await deleteFile(file);
          if (success) {
            deletedCount++;
          } else {
            failedCount++;
            errors.add('Failed to delete: ${file.fileName}');
          }
        } on FileInUseException catch (e) {
          // If file is in use and partial deletion is not allowed, throw immediately
          if (!allowPartialDeletion) {
            rethrow;
          }
          failedCount++;
          errors.add('File in use: ${file.fileName} - ${e.message}');
        } on PermissionDeniedException catch (e) {
          // If permission denied and partial deletion is not allowed, throw immediately
          if (!allowPartialDeletion) {
            rethrow;
          }
          failedCount++;
          errors.add('Permission denied: ${file.fileName} - ${e.message}');
        } on Exception catch (e) {
          failedCount++;
          errors.add('Error deleting ${file.fileName}: ${e.toString()}');
        }
      }

      // Delete cover image if exists
      if (group.coverPath != null) {
        try {
          final coverFile = File(group.coverPath!);
          if (await coverFile.exists()) {
            await coverFile.delete();
            await _logger.log(
              level: 'info',
              subsystem: 'file_manager',
              message: 'Cover image deleted',
              extra: {'coverPath': group.coverPath},
            );
          }
        } on Exception catch (e) {
          await _logger.log(
            level: 'warning',
            subsystem: 'file_manager',
            message: 'Failed to delete cover image',
            extra: {
              'coverPath': group.coverPath,
              'error': e.toString(),
            },
          );
        }
      }

      // Delete directory if required
      // After deleting all audio files, delete the group directory recursively
      // even if it's not empty (there may be service files like .m3u, .bt.state, etc.)
      if (deleteDirectory) {
        try {
          final dir = Directory(group.groupPath);
          if (await dir.exists()) {
            // Delete directory recursively - all audio files are already deleted,
            // remaining files are service files that should be removed too
            await dir.delete(recursive: true);
            await _logger.log(
              level: 'info',
              subsystem: 'file_manager',
              message: 'Directory deleted recursively',
              extra: {'groupPath': group.groupPath},
            );

            // If group has torrentId, also delete parent torrent folder if no other groups
            if (group.torrentId != null) {
              await _deleteTorrentIdFolderIfEmpty(group);
            }
          } else {
            // Directory doesn't exist, but still try to delete torrentId folder if applicable
            if (group.torrentId != null) {
              await _deleteTorrentIdFolderIfEmpty(group);
            }
          }
        } on Exception catch (e) {
          await _logger.log(
            level: 'warning',
            subsystem: 'file_manager',
            message: 'Failed to delete directory',
            extra: {
              'groupPath': group.groupPath,
              'error': e.toString(),
            },
          );
        }
      }

      // Clear playback position data only if all files were deleted
      if (failedCount == 0) {
        await clearPlaybackData(group.groupPath);
      }

      final success =
          failedCount == 0 || (allowPartialDeletion && deletedCount > 0);

      await _logger.log(
        level: success ? 'info' : 'warning',
        subsystem: 'file_manager',
        message: 'Group deletion completed',
        extra: {
          'groupPath': group.groupPath,
          'groupName': group.groupName,
          'deletedCount': deletedCount,
          'failedCount': failedCount,
          'totalFiles': group.files.length,
          'allowPartialDeletion': allowPartialDeletion,
        },
      );

      return DeletionResult(
        success: success,
        deletedCount: deletedCount,
        failedCount: failedCount,
        totalCount: group.files.length,
        errors: errors,
      );
    } on FileInUseException {
      rethrow;
    } on PermissionDeniedException {
      rethrow;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'file_manager',
        message: 'Failed to delete group',
        extra: {
          'groupPath': group.groupPath,
          'error': e.toString(),
          'errorType': e.runtimeType.toString(),
        },
      );
      return DeletionResult(
        success: false,
        totalCount: group.files.length,
        errors: [e.toString()],
      );
    }
  }

  /// Deletes all files in a group and optionally the directory.
  ///
  /// The [group] parameter is the audiobook group to delete.
  /// The [deleteDirectory] parameter determines whether to delete the directory
  /// after removing all files (default: true).
  ///
  /// Returns true if all files were deleted successfully, false otherwise.
  ///
  /// Throws [FileInUseException] if any file in the group is currently playing.
  ///
  /// This is a convenience method that uses [deleteGroupDetailed] internally.
  Future<bool> deleteGroup(
    LocalAudiobookGroup group, {
    bool deleteDirectory = true,
  }) async {
    final result = await deleteGroupDetailed(
      group,
      deleteDirectory: deleteDirectory,
      allowPartialDeletion:
          false, // For backward compatibility, require all files to be deleted
    );
    return result.success;
  }

  /// Removes audiobook from library without deleting files.
  ///
  /// This method only clears playback position data, but does not delete
  /// the actual files from disk. The files will remain but won't appear
  /// in the library after rescanning.
  ///
  /// The [group] parameter is the audiobook group to remove.
  Future<void> removeFromLibrary(LocalAudiobookGroup group) async {
    try {
      // Only clear playback data, don't delete files
      await clearPlaybackData(group.groupPath);

      await _logger.log(
        level: 'info',
        subsystem: 'file_manager',
        message: 'Audiobook removed from library',
        extra: {
          'groupPath': group.groupPath,
          'groupName': group.groupName,
        },
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'file_manager',
        message: 'Failed to remove from library',
        extra: {
          'groupPath': group.groupPath,
          'error': e.toString(),
        },
      );
    }
  }

  /// Clears playback position data for a group.
  ///
  /// The [groupPath] parameter is the unique path identifying the group.
  Future<void> clearPlaybackData(String groupPath) async {
    try {
      await _playbackPositionService.clearPosition(groupPath);
      await _playbackPositionService.clearAllTrackPositions(groupPath);

      await _logger.log(
        level: 'info',
        subsystem: 'file_manager',
        message: 'Playback data cleared',
        extra: {'groupPath': groupPath},
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'warning',
        subsystem: 'file_manager',
        message: 'Failed to clear playback data',
        extra: {
          'groupPath': groupPath,
          'error': e.toString(),
        },
      );
    }
  }

  /// Calculates total size of files in a group.
  ///
  /// The [group] parameter is the audiobook group.
  ///
  /// Returns the total size in bytes.
  Future<int> calculateGroupSize(LocalAudiobookGroup group) async {
    var totalSize = 0;

    for (final file in group.files) {
      try {
        final fileEntity = File(file.filePath);
        if (await fileEntity.exists()) {
          final stat = await fileEntity.stat();
          totalSize += stat.size;
        } else {
          // Use cached size if file doesn't exist
          totalSize += file.fileSize;
        }
      } on Exception {
        // Use cached size if stat fails
        totalSize += file.fileSize;
      }
    }

    // Add cover image size if exists
    if (group.coverPath != null) {
      try {
        final coverFile = File(group.coverPath!);
        if (await coverFile.exists()) {
          final stat = await coverFile.stat();
          totalSize += stat.size;
        }
      } on Exception {
        // Ignore cover size calculation errors
      }
    }

    return totalSize;
  }

  /// Checks if a file is currently being played.
  ///
  /// The [filePath] parameter is the path to the file to check.
  ///
  /// Returns true if the file is currently playing, false otherwise.
  Future<bool> _isFilePlaying(String filePath) async {
    try {
      // Use Media3PlayerService if provided (preferred), otherwise fallback to NativeAudioPlayer
      final playerService = _media3PlayerService;
      if (playerService != null) {
        return await playerService.isFilePlaying(filePath);
      }

      // Fallback: create a new NativeAudioPlayer instance for checking
      // This is less ideal as it doesn't use the singleton player instance
      final audioPlayer = NativeAudioPlayer();
      final playerState = await audioPlayer.getState();
      final isPlaying = playerState.isPlaying;
      if (!isPlaying) {
        return false;
      }

      final currentMediaItem = await audioPlayer.getCurrentMediaItemInfo();
      if (currentMediaItem.isEmpty) {
        return false;
      }

      final currentUri = currentMediaItem['uri'] as String?;
      if (currentUri == null) {
        return false;
      }

      // Normalize paths for comparison
      final normalizedFilePath = path.normalize(filePath);
      // Remove file:// prefix if present
      var normalizedCurrentUri = currentUri;
      if (normalizedCurrentUri.startsWith('file://')) {
        normalizedCurrentUri = normalizedCurrentUri.substring(7);
      }
      normalizedCurrentUri = path.normalize(normalizedCurrentUri);

      // Check if the current playing file matches the file path
      return normalizedFilePath == normalizedCurrentUri ||
          currentUri.contains(filePath);
    } on Exception catch (e) {
      await _logger.log(
        level: 'warning',
        subsystem: 'file_manager',
        message: 'Failed to check if file is playing',
        extra: {
          'filePath': filePath,
          'error': e.toString(),
        },
      );
      // If we can't check, assume file is not playing to allow deletion
      return false;
    }
  }

  /// Deletes the parent torrent ID folder if it's empty after group deletion.
  ///
  /// This method finds the parent folder with torrentId and deletes it
  /// recursively if it's empty or contains only service files.
  ///
  /// The [group] parameter is the audiobook group that was deleted.
  Future<void> _deleteTorrentIdFolderIfEmpty(
    LocalAudiobookGroup group,
  ) async {
    if (group.torrentId == null) {
      return;
    }

    try {
      // Find parent directory containing torrentId
      // groupPath format: basePath/{torrentId}/groupName or basePath/{torrentId}
      final normalizedGroupPath = path.normalize(group.groupPath);
      final pathParts = path.split(normalizedGroupPath);

      // Find index of torrentId in path
      int? torrentIdIndex;
      for (var i = 0; i < pathParts.length; i++) {
        if (pathParts[i] == group.torrentId) {
          torrentIdIndex = i;
          break;
        }
      }

      if (torrentIdIndex == null) {
        // TorrentId not found in path - skip deletion
        await _logger.log(
          level: 'warning',
          subsystem: 'file_manager',
          message: 'TorrentId not found in group path',
          extra: {
            'groupPath': group.groupPath,
            'torrentId': group.torrentId,
          },
        );
        return;
      }

      // Build path to torrentId folder
      final torrentIdPathParts = pathParts.sublist(0, torrentIdIndex + 1);
      final torrentIdDir = Directory(path.joinAll(torrentIdPathParts));

      // Check if groupPath is the same as torrentIdDir path
      // If so, the torrentId folder was already deleted when we deleted groupPath
      final normalizedTorrentIdPath = path.normalize(torrentIdDir.path);
      if (normalizedGroupPath == normalizedTorrentIdPath) {
        // GroupPath is the torrentId folder itself - already deleted
        await _logger.log(
          level: 'info',
          subsystem: 'file_manager',
          message: 'TorrentId folder was already deleted with groupPath',
          extra: {
            'torrentIdPath': torrentIdDir.path,
            'torrentId': group.torrentId,
            'groupPath': group.groupPath,
          },
        );
        return;
      }

      if (!await torrentIdDir.exists()) {
        // Directory doesn't exist - nothing to delete
        await _logger.log(
          level: 'info',
          subsystem: 'file_manager',
          message: 'TorrentId folder does not exist',
          extra: {
            'torrentIdPath': torrentIdDir.path,
            'torrentId': group.torrentId,
            'groupPath': group.groupPath,
          },
        );
        return;
      }

      // Check if there are other directories (other groups) in torrentId folder
      // If no other directories exist, we can safely delete the entire torrentId folder
      final hasOtherGroups = await _hasOtherGroupsInTorrentIdFolder(
        torrentIdDir,
        normalizedGroupPath,
      );
      if (!hasOtherGroups) {
        // No other groups found - delete the entire torrentId folder recursively
        await torrentIdDir.delete(recursive: true);
        await _logger.log(
          level: 'info',
          subsystem: 'file_manager',
          message: 'TorrentId folder deleted recursively',
          extra: {
            'torrentIdPath': torrentIdDir.path,
            'torrentId': group.torrentId,
            'groupPath': group.groupPath,
          },
        );
      } else {
        await _logger.log(
          level: 'info',
          subsystem: 'file_manager',
          message: 'TorrentId folder contains other groups, skipping deletion',
          extra: {
            'torrentIdPath': torrentIdDir.path,
            'torrentId': group.torrentId,
            'groupPath': group.groupPath,
          },
        );
      }
    } on Exception catch (e) {
      await _logger.log(
        level: 'warning',
        subsystem: 'file_manager',
        message: 'Failed to delete torrentId folder',
        extra: {
          'groupPath': group.groupPath,
          'torrentId': group.torrentId,
          'error': e.toString(),
        },
      );
    }
  }

  /// Checks if torrentId folder contains other groups (directories) besides the deleted one.
  ///
  /// The [torrentIdDir] parameter is the torrentId directory to check.
  /// The [deletedGroupPath] parameter is the path of the group that was deleted.
  ///
  /// Returns true if there are other groups (directories) in the torrentId folder,
  /// false if the folder only contains files or is empty.
  Future<bool> _hasOtherGroupsInTorrentIdFolder(
    Directory torrentIdDir,
    String deletedGroupPath,
  ) async {
    try {
      final entries = await torrentIdDir.list().toList();
      if (entries.isEmpty) {
        return false; // No other groups
      }

      // Check if there are any directories (other groups) besides the deleted one
      for (final entry in entries) {
        if (entry is Directory) {
          final entryPath = path.normalize(entry.path);
          // If this directory is not the deleted group path, it's another group
          if (entryPath != deletedGroupPath) {
            await _logger.log(
              level: 'info',
              subsystem: 'file_manager',
              message: 'Found other group in torrentId folder',
              extra: {
                'torrentIdPath': torrentIdDir.path,
                'otherGroupPath': entryPath,
                'deletedGroupPath': deletedGroupPath,
              },
            );
            return true; // Found another group
          }
        }
      }

      // No other directories found - only files remain, safe to delete
      return false;
    } on Exception catch (e) {
      await _logger.log(
        level: 'warning',
        subsystem: 'file_manager',
        message: 'Failed to check for other groups in torrentId folder',
        extra: {
          'dirPath': torrentIdDir.path,
          'error': e.toString(),
        },
      );
      // If we can't check, assume there are other groups to be safe
      return true;
    }
  }
}
