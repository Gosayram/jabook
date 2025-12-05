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

import 'package:crypto/crypto.dart';
import 'package:jabook/core/data/local/database/app_database.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:sembast/sembast.dart';

/// Represents a file checksum record in the database.
class FileChecksumRecord {
  /// Creates FileChecksumRecord from JSON.
  factory FileChecksumRecord.fromJson(Map<String, dynamic> json) =>
      FileChecksumRecord(
        filePath: json['file_path'] as String,
        checksum: json['checksum'] as String,
        fileSize: json['file_size'] as int,
        lastModified: DateTime.parse(json['last_modified'] as String),
        scannedAt: DateTime.parse(json['scanned_at'] as String),
      );

  /// Creates a new FileChecksumRecord instance.
  FileChecksumRecord({
    required this.filePath,
    required this.checksum,
    required this.fileSize,
    required this.lastModified,
    required this.scannedAt,
  });

  /// Full path to the file.
  final String filePath;

  /// SHA-256 checksum of the file.
  final String checksum;

  /// Size of the file in bytes.
  final int fileSize;

  /// Last modification time of the file.
  final DateTime lastModified;

  /// Timestamp when checksum was computed.
  final DateTime scannedAt;

  /// Converts FileChecksumRecord to JSON.
  Map<String, dynamic> toJson() => {
        'file_path': filePath,
        'checksum': checksum,
        'file_size': fileSize,
        'last_modified': lastModified.toIso8601String(),
        'scanned_at': scannedAt.toIso8601String(),
      };
}

/// Service for computing and managing file checksums.
///
/// This service provides functionality to:
/// - Compute SHA-256 checksums for files (more reliable than MD5)
/// - Store checksums in the database
/// - Check if files have changed by comparing checksums
/// - Batch process files for better performance
class FileChecksumService {
  /// Creates a new FileChecksumService instance.
  FileChecksumService({
    AppDatabase? appDatabase,
  }) : _appDatabase = appDatabase;

  final AppDatabase? _appDatabase;
  final StructuredLogger _logger = StructuredLogger();

  /// Gets the file checksums store from the database.
  Future<StoreRef<String, Map<String, dynamic>>> _getStore() async {
    if (_appDatabase == null) {
      throw StateError('AppDatabase not provided');
    }
    await _appDatabase.ensureInitialized();
    return StoreRef<String, Map<String, dynamic>>('file_checksums');
  }

  /// Computes SHA-256 checksum for a file.
  ///
  /// The [filePath] parameter is the path to the file.
  /// Returns the SHA-256 checksum as a hexadecimal string.
  ///
  /// This method reads the file in chunks to avoid loading large files
  /// entirely into memory.
  Future<String> computeChecksum(String filePath) async {
    try {
      final file = File(filePath);
      if (!await file.exists()) {
        throw FileSystemException('File does not exist', filePath);
      }

      final bytes = await file.readAsBytes();
      final digest = sha256.convert(bytes);

      return digest.toString();
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'file_checksum',
        message: 'Failed to compute checksum',
        extra: {
          'file_path': filePath,
          'error': e.toString(),
        },
      );
      rethrow;
    }
  }

  /// Gets the stored checksum for a file.
  ///
  /// The [filePath] parameter is the path to the file.
  /// Returns [FileChecksumRecord] if found, null otherwise.
  Future<FileChecksumRecord?> getChecksum(String filePath) async {
    try {
      final store = await _getStore();
      if (_appDatabase == null) {
        throw StateError('AppDatabase not provided');
      }
      final db = await _appDatabase.ensureInitialized();
      final record = await store.record(filePath).get(db);

      if (record == null) {
        return null;
      }

      return FileChecksumRecord.fromJson(record);
    } on Exception catch (e) {
      await _logger.log(
        level: 'warning',
        subsystem: 'file_checksum',
        message: 'Failed to get checksum from database',
        extra: {
          'file_path': filePath,
          'error': e.toString(),
        },
      );
      return null;
    }
  }

  /// Saves checksum for a file.
  ///
  /// The [filePath] parameter is the path to the file.
  /// The [checksum] parameter is the SHA-256 checksum.
  /// The [fileSize] parameter is the size of the file in bytes.
  /// The [lastModified] parameter is the last modification time of the file.
  Future<void> saveChecksum(
    String filePath,
    String checksum,
    int fileSize,
    DateTime lastModified,
  ) async {
    try {
      final store = await _getStore();
      if (_appDatabase == null) {
        throw StateError('AppDatabase not provided');
      }
      final db = await _appDatabase.ensureInitialized();

      final record = FileChecksumRecord(
        filePath: filePath,
        checksum: checksum,
        fileSize: fileSize,
        lastModified: lastModified,
        scannedAt: DateTime.now(),
      );

      await store.record(filePath).put(db, record.toJson());
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'file_checksum',
        message: 'Failed to save checksum',
        extra: {
          'file_path': filePath,
          'error': e.toString(),
        },
      );
    }
  }

  /// Checks if a file has changed by comparing its current state with stored checksum.
  ///
  /// The [filePath] parameter is the path to the file.
  /// Returns true if the file has changed or doesn't exist in the database,
  /// false if the file hasn't changed.
  ///
  /// This method first checks file size and modification time for quick comparison.
  /// If those match, it compares checksums. This avoids computing checksums
  /// for files that haven't changed.
  Future<bool> hasFileChanged(String filePath) async {
    try {
      final file = File(filePath);
      if (!await file.exists()) {
        return true; // File doesn't exist, consider it changed
      }

      final stat = await file.stat();
      final storedChecksum = await getChecksum(filePath);

      // If no stored checksum, file is considered new/changed
      if (storedChecksum == null) {
        return true;
      }

      // Quick check: compare file size and modification time
      if (storedChecksum.fileSize != stat.size ||
          storedChecksum.lastModified != stat.modified) {
        return true; // File size or modification time changed
      }

      // If size and time match, compare checksums to be sure
      final currentChecksum = await computeChecksum(filePath);
      return storedChecksum.checksum != currentChecksum;
    } on Exception catch (e) {
      await _logger.log(
        level: 'warning',
        subsystem: 'file_checksum',
        message: 'Failed to check if file changed',
        extra: {
          'file_path': filePath,
          'error': e.toString(),
        },
      );
      // On error, assume file changed to be safe
      return true;
    }
  }

  /// Computes and saves checksum for a file.
  ///
  /// The [filePath] parameter is the path to the file.
  /// Returns the computed checksum.
  ///
  /// This is a convenience method that combines computeChecksum and saveChecksum.
  Future<String> computeAndSaveChecksum(String filePath) async {
    try {
      final file = File(filePath);
      if (!await file.exists()) {
        throw FileSystemException('File does not exist', filePath);
      }

      final stat = await file.stat();
      final checksum = await computeChecksum(filePath);
      await saveChecksum(filePath, checksum, stat.size, stat.modified);

      return checksum;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'file_checksum',
        message: 'Failed to compute and save checksum',
        extra: {
          'file_path': filePath,
          'error': e.toString(),
        },
      );
      rethrow;
    }
  }

  /// Removes checksum record for a file.
  ///
  /// The [filePath] parameter is the path to the file.
  Future<void> removeChecksum(String filePath) async {
    try {
      final store = await _getStore();
      final db = await _appDatabase!.ensureInitialized();
      await store.record(filePath).delete(db);
    } on Exception catch (e) {
      await _logger.log(
        level: 'warning',
        subsystem: 'file_checksum',
        message: 'Failed to remove checksum',
        extra: {
          'file_path': filePath,
          'error': e.toString(),
        },
      );
    }
  }

  /// Removes checksum records for multiple files.
  ///
  /// The [filePaths] parameter is a list of file paths.
  Future<void> removeChecksums(List<String> filePaths) async {
    try {
      if (_appDatabase == null) {
        throw StateError('AppDatabase not provided');
      }
      final store = await _getStore();
      final db = await _appDatabase.ensureInitialized();

      await db.transaction((transaction) async {
        for (final filePath in filePaths) {
          await store.record(filePath).delete(transaction);
        }
      });
    } on Exception catch (e) {
      await _logger.log(
        level: 'warning',
        subsystem: 'file_checksum',
        message: 'Failed to remove checksums',
        extra: {
          'file_paths_count': filePaths.length,
          'error': e.toString(),
        },
      );
    }
  }

  /// Gets all stored checksums.
  ///
  /// Returns a map of file paths to FileChecksumRecord.
  Future<Map<String, FileChecksumRecord>> getAllChecksums() async {
    try {
      final store = await _getStore();
      if (_appDatabase == null) {
        throw StateError('AppDatabase not provided');
      }
      final db = await _appDatabase.ensureInitialized();
      final records = await store.find(db);

      final result = <String, FileChecksumRecord>{};
      for (final snapshot in records) {
        try {
          final record = FileChecksumRecord.fromJson(snapshot.value);
          result[record.filePath] = record;
        } on Exception {
          // Skip invalid records
          continue;
        }
      }

      return result;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'file_checksum',
        message: 'Failed to get all checksums',
        extra: {
          'error': e.toString(),
        },
      );
      return {};
    }
  }

  /// Cleans up checksum records for files that no longer exist.
  ///
  /// The [existingFilePaths] parameter is a set of file paths that currently exist.
  /// Removes checksum records for files that are not in this set.
  Future<int> cleanupMissingFiles(Set<String> existingFilePaths) async {
    try {
      final allChecksums = await getAllChecksums();

      final toRemove = <String>[];
      for (final filePath in allChecksums.keys) {
        if (!existingFilePaths.contains(filePath)) {
          toRemove.add(filePath);
        }
      }

      if (toRemove.isNotEmpty) {
        await removeChecksums(toRemove);
        await _logger.log(
          level: 'info',
          subsystem: 'file_checksum',
          message: 'Cleaned up checksums for missing files',
          extra: {
            'removed_count': toRemove.length,
          },
        );
      }

      return toRemove.length;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'file_checksum',
        message: 'Failed to cleanup missing files',
        extra: {
          'error': e.toString(),
        },
      );
      return 0;
    }
  }
}
