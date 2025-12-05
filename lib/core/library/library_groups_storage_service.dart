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

import 'package:jabook/core/data/local/database/app_database.dart';
import 'package:jabook/core/domain/library/entities/local_audiobook.dart';
import 'package:jabook/core/domain/library/entities/local_audiobook_group.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:jabook/core/library/folder_structure_analyzer.dart';
import 'package:sembast/sembast.dart';

/// Service for persisting library groups to database.
///
/// This service provides functionality to:
/// - Save library groups to database for fast restoration
/// - Load library groups from database on app start
/// - Clear stored groups when needed
class LibraryGroupsStorageService {
  /// Creates a new LibraryGroupsStorageService instance.
  LibraryGroupsStorageService({
    AppDatabase? appDatabase,
  }) : _appDatabase = appDatabase;

  final AppDatabase? _appDatabase;
  final StructuredLogger _logger = StructuredLogger();

  /// Gets the library groups store from the database.
  Future<StoreRef<String, Map<String, dynamic>>> _getStore() async {
    if (_appDatabase == null) {
      throw StateError('AppDatabase not provided');
    }
    await _appDatabase.ensureInitialized();
    return StoreRef<String, Map<String, dynamic>>('library_groups');
  }

  /// Converts LocalAudiobook to JSON map.
  Map<String, dynamic> _audiobookToJson(LocalAudiobook audiobook) => {
        'file_path': audiobook.filePath,
        'file_name': audiobook.fileName,
        'file_size': audiobook.fileSize,
        'title': audiobook.title,
        'author': audiobook.author,
        'duration': audiobook.duration,
        'cover_path': audiobook.coverPath,
        'scanned_at': audiobook.scannedAt?.toIso8601String(),
      };

  /// Converts JSON map to LocalAudiobook.
  LocalAudiobook _jsonToAudiobook(Map<String, dynamic> json) => LocalAudiobook(
        filePath: json['file_path'] as String,
        fileName: json['file_name'] as String,
        fileSize: json['file_size'] as int,
        title: json['title'] as String?,
        author: json['author'] as String?,
        duration: json['duration'] as int?,
        coverPath: json['cover_path'] as String?,
        scannedAt: json['scanned_at'] != null
            ? DateTime.parse(json['scanned_at'] as String)
            : null,
      );

  /// Converts LocalAudiobookGroup to JSON map.
  Map<String, dynamic> _groupToJson(LocalAudiobookGroup group) => {
        'group_path': group.groupPath,
        'group_name': group.groupName,
        'torrent_id': group.torrentId,
        'cover_path': group.coverPath,
        'scanned_at': group.scannedAt?.toIso8601String(),
        'is_external_folder': group.isExternalFolder,
        'external_folder_type': group.externalFolderType?.toString(),
        'files': group.files.map(_audiobookToJson).toList(),
        'total_size': group.totalSize,
        'file_count': group.fileCount,
      };

  /// Converts JSON map to LocalAudiobookGroup.
  LocalAudiobookGroup _jsonToGroup(Map<String, dynamic> json) {
    final files = (json['files'] as List<dynamic>)
        .map((f) => _jsonToAudiobook(f as Map<String, dynamic>))
        .toList();

    ExternalFolderType? externalFolderType;
    if (json['external_folder_type'] != null) {
      try {
        externalFolderType = ExternalFolderType.values.firstWhere(
          (e) => e.toString() == json['external_folder_type'] as String,
        );
      } on Exception {
        externalFolderType = null;
      }
    }

    return LocalAudiobookGroup(
      groupName: json['group_name'] as String,
      groupPath: json['group_path'] as String,
      torrentId: json['torrent_id'] as String?,
      coverPath: json['cover_path'] as String?,
      scannedAt: json['scanned_at'] != null
          ? DateTime.parse(json['scanned_at'] as String)
          : null,
      isExternalFolder: json['is_external_folder'] as bool? ?? false,
      externalFolderType: externalFolderType,
      files: files,
    );
  }

  /// Saves library groups to database.
  ///
  /// The [groups] parameter is the list of groups to save.
  Future<void> saveGroups(List<LocalAudiobookGroup> groups) async {
    try {
      final store = await _getStore();
      if (_appDatabase == null) {
        throw StateError('AppDatabase not provided');
      }
      final db = await _appDatabase.ensureInitialized();

      await db.transaction((transaction) async {
        // Clear existing groups first
        await store.delete(transaction);

        // Save all groups
        for (final group in groups) {
          final json = _groupToJson(group);
          await store.record(group.groupPath).put(transaction, json);
        }
      });

      await _logger.log(
        level: 'info',
        subsystem: 'library_groups_storage',
        message: 'Saved library groups to database',
        extra: {
          'groups_count': groups.length,
        },
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'library_groups_storage',
        message: 'Failed to save library groups',
        extra: {
          'error': e.toString(),
        },
      );
      rethrow;
    }
  }

  /// Loads library groups from database.
  ///
  /// Returns a list of LocalAudiobookGroup instances, or empty list if none found.
  Future<List<LocalAudiobookGroup>> loadGroups() async {
    try {
      final store = await _getStore();
      if (_appDatabase == null) {
        throw StateError('AppDatabase not provided');
      }
      final db = await _appDatabase.ensureInitialized();
      final records = await store.find(db);

      final groups = <LocalAudiobookGroup>[];
      for (final snapshot in records) {
        try {
          final group = _jsonToGroup(snapshot.value);
          groups.add(group);
        } on Exception catch (e) {
          await _logger.log(
            level: 'warning',
            subsystem: 'library_groups_storage',
            message: 'Failed to parse group from database',
            extra: {
              'key': snapshot.key,
              'error': e.toString(),
            },
          );
          // Continue with other groups
        }
      }

      await _logger.log(
        level: 'info',
        subsystem: 'library_groups_storage',
        message: 'Loaded library groups from database',
        extra: {
          'groups_count': groups.length,
        },
      );

      return groups;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'library_groups_storage',
        message: 'Failed to load library groups',
        extra: {
          'error': e.toString(),
        },
      );
      return [];
    }
  }

  /// Clears all stored library groups from database.
  Future<void> clearGroups() async {
    try {
      final store = await _getStore();
      if (_appDatabase == null) {
        throw StateError('AppDatabase not provided');
      }
      final db = await _appDatabase.ensureInitialized();
      await store.delete(db);

      await _logger.log(
        level: 'info',
        subsystem: 'library_groups_storage',
        message: 'Cleared library groups from database',
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'library_groups_storage',
        message: 'Failed to clear library groups',
        extra: {
          'error': e.toString(),
        },
      );
    }
  }

  /// Updates a single group in the database.
  ///
  /// The [group] parameter is the group to update.
  Future<void> updateGroup(LocalAudiobookGroup group) async {
    try {
      final store = await _getStore();
      if (_appDatabase == null) {
        throw StateError('AppDatabase not provided');
      }
      final db = await _appDatabase.ensureInitialized();
      final json = _groupToJson(group);
      await store.record(group.groupPath).put(db, json);
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'library_groups_storage',
        message: 'Failed to update group',
        extra: {
          'group_path': group.groupPath,
          'error': e.toString(),
        },
      );
    }
  }

  /// Removes a single group from the database.
  ///
  /// The [groupPath] parameter is the path of the group to remove.
  Future<void> removeGroup(String groupPath) async {
    try {
      final store = await _getStore();
      if (_appDatabase == null) {
        throw StateError('AppDatabase not provided');
      }
      final db = await _appDatabase.ensureInitialized();
      await store.record(groupPath).delete(db);
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'library_groups_storage',
        message: 'Failed to remove group',
        extra: {
          'group_path': groupPath,
          'error': e.toString(),
        },
      );
    }
  }
}
