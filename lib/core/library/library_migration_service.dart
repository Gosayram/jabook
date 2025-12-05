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

import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:jabook/core/library/audiobook_library_scanner.dart';
import 'package:path/path.dart' as path;

/// Result of a library migration operation.
class MigrationResult {
  /// Creates a new MigrationResult instance.
  MigrationResult({
    required this.success,
    this.filesMoved = 0,
    this.filesCopied = 0,
    this.oldPath,
    this.newPath,
    this.error,
  });

  /// Whether the migration was successful.
  final bool success;

  /// Number of files moved.
  final int filesMoved;

  /// Number of files copied.
  final int filesCopied;

  /// Old path that was migrated from.
  final String? oldPath;

  /// New path that was migrated to.
  final String? newPath;

  /// Error message if migration failed.
  final String? error;
}

/// Estimate of migration operation.
class MigrationEstimate {
  /// Creates a new MigrationEstimate instance.
  MigrationEstimate({
    required this.totalFiles,
    required this.totalSize,
    this.estimatedTimeSeconds,
  });

  /// Total number of files to migrate.
  final int totalFiles;

  /// Total size of files in bytes.
  final int totalSize;

  /// Estimated time in seconds (optional).
  final int? estimatedTimeSeconds;
}

/// Service for migrating library files between directories.
///
/// This service provides methods to migrate audiobook files from one
/// directory to another, with support for moving or copying files.
class LibraryMigrationService {
  /// Creates a new LibraryMigrationService instance.
  LibraryMigrationService();

  final StructuredLogger _logger = StructuredLogger();
  final AudiobookLibraryScanner _scanner = AudiobookLibraryScanner();

  /// Validates that migration is possible.
  ///
  /// Checks if both old and new paths exist and are accessible.
  Future<bool> canMigrate(String oldPath, String newPath) async {
    try {
      final oldDir = Directory(oldPath);
      final newDir = Directory(newPath);

      if (!await oldDir.exists()) {
        await _logger.log(
          level: 'warning',
          subsystem: 'library_migration',
          message: 'Old path does not exist',
          extra: {'oldPath': oldPath},
        );
        return false;
      }

      // Create new directory if it doesn't exist
      if (!await newDir.exists()) {
        try {
          await newDir.create(recursive: true);
          await _logger.log(
            level: 'info',
            subsystem: 'library_migration',
            message: 'Created new directory for migration',
            extra: {'newPath': newPath},
          );
        } on Exception catch (e) {
          await _logger.log(
            level: 'error',
            subsystem: 'library_migration',
            message: 'Failed to create new directory',
            extra: {
              'newPath': newPath,
              'error': e.toString(),
            },
          );
          return false;
        }
      }

      // Check write access to new directory
      try {
        final testFile = File('${newDir.path}/.test_write');
        await testFile.writeAsString('test');
        await testFile.delete();
      } on Exception {
        await _logger.log(
          level: 'error',
          subsystem: 'library_migration',
          message: 'No write access to new directory',
          extra: {'newPath': newPath},
        );
        return false;
      }

      return true;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'library_migration',
        message: 'Failed to validate migration',
        extra: {
          'oldPath': oldPath,
          'newPath': newPath,
          'error': e.toString(),
        },
      );
      return false;
    }
  }

  /// Gets estimated migration time and size.
  ///
  /// Scans the old path to determine how many files and how much data
  /// needs to be migrated.
  Future<MigrationEstimate> estimateMigration(
    String oldPath,
    String newPath,
  ) async {
    try {
      final groups = await _scanner.scanDirectoryGrouped(
        oldPath,
        recursive: true,
      );

      var totalFiles = 0;
      var totalSize = 0;

      for (final group in groups) {
        totalFiles += group.files.length;
        totalSize += group.totalSize;
      }

      // Rough estimate: 1 second per 10MB
      final estimatedTimeSeconds = (totalSize / (10 * 1024 * 1024)).ceil();

      return MigrationEstimate(
        totalFiles: totalFiles,
        totalSize: totalSize,
        estimatedTimeSeconds: estimatedTimeSeconds,
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'library_migration',
        message: 'Failed to estimate migration',
        extra: {
          'oldPath': oldPath,
          'newPath': newPath,
          'error': e.toString(),
        },
      );
      return MigrationEstimate(totalFiles: 0, totalSize: 0);
    }
  }

  /// Migrates library from old path to new path.
  ///
  /// The [oldPath] parameter is the source directory.
  /// The [newPath] parameter is the destination directory.
  /// The [moveFiles] parameter determines whether to move (true) or copy (false) files.
  /// The [onProgress] callback is called with current and total file counts.
  ///
  /// Returns a [MigrationResult] with the outcome of the migration.
  Future<MigrationResult> migrateLibrary({
    required String oldPath,
    required String newPath,
    bool moveFiles = true,
    Function(int current, int total)? onProgress,
  }) async {
    try {
      // Validate migration
      final canMigrate = await this.canMigrate(oldPath, newPath);
      if (!canMigrate) {
        return MigrationResult(
          success: false,
          oldPath: oldPath,
          newPath: newPath,
          error: 'Migration validation failed',
        );
      }

      await _logger.log(
        level: 'info',
        subsystem: 'library_migration',
        message: 'Starting library migration',
        extra: {
          'oldPath': oldPath,
          'newPath': newPath,
          'moveFiles': moveFiles,
        },
      );

      // Scan old directory
      final groups = await _scanner.scanDirectoryGrouped(
        oldPath,
        recursive: true,
      );

      var filesMoved = 0;
      var filesCopied = 0;
      var totalFiles = 0;

      // Count total files
      for (final group in groups) {
        totalFiles += group.files.length;
      }

      // Migrate files
      for (final group in groups) {
        for (final file in group.files) {
          try {
            final oldFile = File(file.filePath);
            if (!await oldFile.exists()) {
              await _logger.log(
                level: 'warning',
                subsystem: 'library_migration',
                message: 'File does not exist, skipping',
                extra: {'filePath': file.filePath},
              );
              continue;
            }

            // Calculate relative path from old directory
            final relativePath = path.relative(file.filePath, from: oldPath);
            final newFilePath = path.join(newPath, relativePath);
            final newFile = File(newFilePath);

            // Create parent directory if needed
            await newFile.parent.create(recursive: true);

            if (moveFiles) {
              // Move file
              await oldFile.rename(newFile.path);
              filesMoved++;
            } else {
              // Copy file
              await oldFile.copy(newFile.path);
              filesCopied++;
            }

            // Report progress
            final current = filesMoved + filesCopied;
            onProgress?.call(current, totalFiles);

            await _logger.log(
              level: 'debug',
              subsystem: 'library_migration',
              message: 'Migrated file',
              extra: {
                'oldPath': file.filePath,
                'newPath': newFilePath,
                'moved': moveFiles,
              },
            );
          } on Exception catch (e) {
            await _logger.log(
              level: 'error',
              subsystem: 'library_migration',
              message: 'Failed to migrate file',
              extra: {
                'filePath': file.filePath,
                'error': e.toString(),
              },
            );
            // Continue with other files
          }
        }

        // Migrate cover image if exists
        if (group.coverPath != null) {
          try {
            final oldCover = File(group.coverPath!);
            if (await oldCover.exists()) {
              final relativeCoverPath =
                  path.relative(group.coverPath!, from: oldPath);
              final newCoverPath = path.join(newPath, relativeCoverPath);
              final newCover = File(newCoverPath);

              await newCover.parent.create(recursive: true);

              if (moveFiles) {
                await oldCover.rename(newCover.path);
              } else {
                await oldCover.copy(newCover.path);
              }
            }
          } on Exception catch (e) {
            await _logger.log(
              level: 'warning',
              subsystem: 'library_migration',
              message: 'Failed to migrate cover image',
              extra: {
                'coverPath': group.coverPath,
                'error': e.toString(),
              },
            );
          }
        }
      }

      await _logger.log(
        level: 'info',
        subsystem: 'library_migration',
        message: 'Library migration completed',
        extra: {
          'oldPath': oldPath,
          'newPath': newPath,
          'filesMoved': filesMoved,
          'filesCopied': filesCopied,
        },
      );

      return MigrationResult(
        success: true,
        filesMoved: filesMoved,
        filesCopied: filesCopied,
        oldPath: oldPath,
        newPath: newPath,
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'library_migration',
        message: 'Library migration failed',
        extra: {
          'oldPath': oldPath,
          'newPath': newPath,
          'error': e.toString(),
        },
      );
      return MigrationResult(
        success: false,
        oldPath: oldPath,
        newPath: newPath,
        error: e.toString(),
      );
    }
  }
}
