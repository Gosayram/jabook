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

import 'dart:convert';
import 'dart:io';

import 'package:intl/intl.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:jabook/core/library/local_audiobook.dart';
import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Represents an item in the trash.
class TrashItem {
  /// Creates a TrashItem from JSON.
  factory TrashItem.fromJson(Map<String, dynamic> json) => TrashItem(
        originalPath: json['originalPath'] as String,
        trashPath: json['trashPath'] as String,
        deletedAt: DateTime.parse(json['deletedAt'] as String),
        groupName: json['groupName'] as String,
        fileCount: json['fileCount'] as int? ?? 1,
        totalSize: json['totalSize'] as int? ?? 0,
        groupPath: json['groupPath'] as String?,
      );

  /// Creates a new TrashItem instance.
  TrashItem({
    required this.originalPath,
    required this.trashPath,
    required this.deletedAt,
    required this.groupName,
    this.fileCount = 1,
    this.totalSize = 0,
    this.groupPath,
  });

  /// Original path of the deleted file/group.
  final String originalPath;

  /// Path in the trash directory.
  final String trashPath;

  /// Timestamp when the item was deleted.
  final DateTime deletedAt;

  /// Name of the audiobook group.
  final String groupName;

  /// Number of files in the group.
  final int fileCount;

  /// Total size of files in bytes.
  final int totalSize;

  /// Original group path (for groups).
  final String? groupPath;

  /// Gets the age of the item in days.
  int get ageInDays => DateTime.now().difference(deletedAt).inDays;

  /// Converts TrashItem to JSON.
  Map<String, dynamic> toJson() => {
        'originalPath': originalPath,
        'trashPath': trashPath,
        'deletedAt': deletedAt.toIso8601String(),
        'groupName': groupName,
        'fileCount': fileCount,
        'totalSize': totalSize,
        'groupPath': groupPath,
      };
}

/// Service for managing trash (recovery) for deleted files.
///
/// This service provides functionality to move deleted files to a trash
/// directory instead of permanently deleting them, allowing users to
/// recover files later.
class TrashService {
  /// Creates a new TrashService instance.
  TrashService();

  final StructuredLogger _logger = StructuredLogger();
  static const String _trashItemsKey = 'trash_items';
  static const String _trashEnabledKey = 'trash_enabled';
  static const Duration _defaultRetentionPeriod = Duration(days: 30);

  /// Gets the trash directory path.
  Future<String> _getTrashDirectory() async {
    try {
      final appDocDir = await getApplicationDocumentsDirectory();
      final trashDir = Directory(path.join(appDocDir.path, 'trash'));
      if (!await trashDir.exists()) {
        await trashDir.create(recursive: true);
      }
      return trashDir.path;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'trash_service',
        message: 'Failed to get trash directory',
        extra: {'error': e.toString()},
      );
      // Fallback to system temp
      final tempDir = await getTemporaryDirectory();
      return path.join(tempDir.path, 'trash');
    }
  }

  /// Checks if trash is enabled.
  Future<bool> isTrashEnabled() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      return prefs.getBool(_trashEnabledKey) ?? true; // Enabled by default
    } on Exception {
      return true;
    }
  }

  /// Enables or disables trash.
  Future<void> setTrashEnabled(bool enabled) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setBool(_trashEnabledKey, enabled);
      await _logger.log(
        level: 'info',
        subsystem: 'trash_service',
        message: 'Trash ${enabled ? 'enabled' : 'disabled'}',
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'trash_service',
        message: 'Failed to set trash enabled',
        extra: {'error': e.toString()},
      );
    }
  }

  /// Moves a file to trash instead of deleting it.
  ///
  /// The [filePath] parameter is the path to the file to move to trash.
  /// The [groupName] parameter is the name of the audiobook group.
  ///
  /// Returns the TrashItem if successful, null otherwise.
  Future<TrashItem?> moveToTrash(
    String filePath,
    String groupName,
  ) async {
    try {
      if (!await isTrashEnabled()) {
        return null;
      }

      final file = File(filePath);
      if (!await file.exists()) {
        return null;
      }

      final trashDir = await _getTrashDirectory();
      final fileName = path.basename(filePath);
      final timestamp = DateTime.now().millisecondsSinceEpoch;
      final trashFileName = '${timestamp}_$fileName';
      final trashPath = path.join(trashDir, trashFileName);

      // Get file size before moving
      final fileSize = await file.length();

      // Move file to trash
      await file.rename(trashPath);

      final trashItem = TrashItem(
        originalPath: filePath,
        trashPath: trashPath,
        deletedAt: DateTime.now(),
        groupName: groupName,
        totalSize: fileSize,
      );

      await _saveTrashItem(trashItem);

      await _logger.log(
        level: 'info',
        subsystem: 'trash_service',
        message: 'File moved to trash',
        extra: {
          'originalPath': filePath,
          'trashPath': trashPath,
        },
      );

      return trashItem;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'trash_service',
        message: 'Failed to move file to trash',
        extra: {
          'filePath': filePath,
          'error': e.toString(),
        },
      );
      return null;
    }
  }

  /// Moves a group to trash instead of deleting it.
  ///
  /// The [group] parameter is the audiobook group to move to trash.
  ///
  /// Returns the TrashItem if successful, null otherwise.
  Future<TrashItem?> moveGroupToTrash(LocalAudiobookGroup group) async {
    try {
      if (!await isTrashEnabled()) {
        return null;
      }

      final groupDir = Directory(group.groupPath);
      if (!await groupDir.exists()) {
        return null;
      }

      final trashDir = await _getTrashDirectory();
      final groupName = path.basename(group.groupPath);
      final timestamp = DateTime.now().millisecondsSinceEpoch;
      final trashGroupName = '${timestamp}_$groupName';
      final trashPath = path.join(trashDir, trashGroupName);

      // Move entire directory to trash
      await groupDir.rename(trashPath);

      final trashItem = TrashItem(
        originalPath: group.groupPath,
        trashPath: trashPath,
        deletedAt: DateTime.now(),
        groupName: group.groupName,
        fileCount: group.files.length,
        totalSize: group.totalSize,
        groupPath: group.groupPath,
      );

      await _saveTrashItem(trashItem);

      await _logger.log(
        level: 'info',
        subsystem: 'trash_service',
        message: 'Group moved to trash',
        extra: {
          'originalPath': group.groupPath,
          'trashPath': trashPath,
        },
      );

      return trashItem;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'trash_service',
        message: 'Failed to move group to trash',
        extra: {
          'groupPath': group.groupPath,
          'error': e.toString(),
        },
      );
      return null;
    }
  }

  /// Restores a file from trash to its original location.
  ///
  /// The [trashItem] parameter is the item to restore.
  ///
  /// Returns true if successful, false otherwise.
  Future<bool> restoreFromTrash(TrashItem trashItem) async {
    try {
      final trashFile = File(trashItem.trashPath);
      if (!await trashFile.exists()) {
        await _logger.log(
          level: 'warning',
          subsystem: 'trash_service',
          message: 'Trash file does not exist',
          extra: {'trashPath': trashItem.trashPath},
        );
        return false;
      }

      // Check if original location exists
      final originalFile = File(trashItem.originalPath);
      if (await originalFile.exists()) {
        await _logger.log(
          level: 'warning',
          subsystem: 'trash_service',
          message: 'Original file already exists',
          extra: {'originalPath': trashItem.originalPath},
        );
        return false;
      }

      // Ensure parent directory exists
      await originalFile.parent.create(recursive: true);

      // Restore file
      await trashFile.rename(trashItem.originalPath);

      // Remove from trash items list
      await _removeTrashItem(trashItem);

      await _logger.log(
        level: 'info',
        subsystem: 'trash_service',
        message: 'File restored from trash',
        extra: {
          'originalPath': trashItem.originalPath,
          'trashPath': trashItem.trashPath,
        },
      );

      return true;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'trash_service',
        message: 'Failed to restore file from trash',
        extra: {
          'trashPath': trashItem.trashPath,
          'error': e.toString(),
        },
      );
      return false;
    }
  }

  /// Restores a group from trash to its original location.
  ///
  /// The [trashItem] parameter is the item to restore.
  ///
  /// Returns true if successful, false otherwise.
  Future<bool> restoreGroupFromTrash(TrashItem trashItem) async {
    try {
      if (trashItem.groupPath == null) {
        return false;
      }

      final trashDir = Directory(trashItem.trashPath);
      if (!await trashDir.exists()) {
        await _logger.log(
          level: 'warning',
          subsystem: 'trash_service',
          message: 'Trash directory does not exist',
          extra: {'trashPath': trashItem.trashPath},
        );
        return false;
      }

      // Check if original location exists
      final originalDir = Directory(trashItem.groupPath!);
      if (await originalDir.exists()) {
        await _logger.log(
          level: 'warning',
          subsystem: 'trash_service',
          message: 'Original directory already exists',
          extra: {'originalPath': trashItem.groupPath},
        );
        return false;
      }

      // Ensure parent directory exists
      await originalDir.parent.create(recursive: true);

      // Restore directory
      await trashDir.rename(trashItem.groupPath!);

      // Remove from trash items list
      await _removeTrashItem(trashItem);

      await _logger.log(
        level: 'info',
        subsystem: 'trash_service',
        message: 'Group restored from trash',
        extra: {
          'originalPath': trashItem.groupPath,
          'trashPath': trashItem.trashPath,
        },
      );

      return true;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'trash_service',
        message: 'Failed to restore group from trash',
        extra: {
          'trashPath': trashItem.trashPath,
          'error': e.toString(),
        },
      );
      return false;
    }
  }

  /// Permanently deletes an item from trash.
  ///
  /// The [trashItem] parameter is the item to permanently delete.
  ///
  /// Returns true if successful, false otherwise.
  Future<bool> permanentlyDelete(TrashItem trashItem) async {
    try {
      final trashFile = File(trashItem.trashPath);
      final trashDir = Directory(trashItem.trashPath);

      if (await trashFile.exists()) {
        await trashFile.delete();
      } else if (await trashDir.exists()) {
        await trashDir.delete(recursive: true);
      } else {
        return false;
      }

      // Remove from trash items list
      await _removeTrashItem(trashItem);

      await _logger.log(
        level: 'info',
        subsystem: 'trash_service',
        message: 'Item permanently deleted from trash',
        extra: {'trashPath': trashItem.trashPath},
      );

      return true;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'trash_service',
        message: 'Failed to permanently delete from trash',
        extra: {
          'trashPath': trashItem.trashPath,
          'error': e.toString(),
        },
      );
      return false;
    }
  }

  /// Gets all items in the trash.
  ///
  /// Returns a list of TrashItem instances.
  Future<List<TrashItem>> getTrashItems() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final itemsJson = prefs.getStringList(_trashItemsKey) ?? [];

      final items = <TrashItem>[];
      for (final itemJson in itemsJson) {
        try {
          // Parse JSON from string
          final itemMap = jsonDecode(itemJson) as Map<String, dynamic>;
          items.add(TrashItem.fromJson(itemMap));
        } on Exception {
          // Skip invalid items
          continue;
        }
      }

      // Filter out items that no longer exist in trash
      final validItems = <TrashItem>[];
      for (final item in items) {
        final trashFile = File(item.trashPath);
        final trashDir = Directory(item.trashPath);
        if (await trashFile.exists() || await trashDir.exists()) {
          validItems.add(item);
        }
      }

      // Update list if items were filtered out
      if (validItems.length != items.length) {
        await _saveTrashItems(validItems);
      }

      return validItems;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'trash_service',
        message: 'Failed to get trash items',
        extra: {'error': e.toString()},
      );
      return [];
    }
  }

  /// Clears old items from trash based on retention period.
  ///
  /// The [retentionPeriod] parameter specifies how long to keep items
  /// (default: 30 days).
  ///
  /// Returns the number of items cleared.
  Future<int> clearOldItems([Duration? retentionPeriod]) async {
    try {
      final period = retentionPeriod ?? _defaultRetentionPeriod;
      final items = await getTrashItems();
      final now = DateTime.now();
      var clearedCount = 0;

      for (final item in items) {
        if (now.difference(item.deletedAt) > period) {
          await permanentlyDelete(item);
          clearedCount++;
        }
      }

      await _logger.log(
        level: 'info',
        subsystem: 'trash_service',
        message: 'Cleared old items from trash',
        extra: {'clearedCount': clearedCount},
      );

      return clearedCount;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'trash_service',
        message: 'Failed to clear old items',
        extra: {'error': e.toString()},
      );
      return 0;
    }
  }

  /// Gets the total size of all items in trash.
  ///
  /// Returns the total size in bytes.
  Future<int> getTrashSize() async {
    try {
      final items = await getTrashItems();
      return items.fold<int>(0, (sum, item) => sum + item.totalSize);
    } on Exception {
      return 0;
    }
  }

  /// Clears all items from trash.
  ///
  /// Returns the number of items cleared.
  Future<int> clearAll() async {
    try {
      final items = await getTrashItems();
      var clearedCount = 0;

      for (final item in items) {
        if (await permanentlyDelete(item)) {
          clearedCount++;
        }
      }

      await _logger.log(
        level: 'info',
        subsystem: 'trash_service',
        message: 'Cleared all items from trash',
        extra: {'clearedCount': clearedCount},
      );

      return clearedCount;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'trash_service',
        message: 'Failed to clear all items',
        extra: {'error': e.toString()},
      );
      return 0;
    }
  }

  /// Saves a trash item to SharedPreferences.
  Future<void> _saveTrashItem(TrashItem item) async {
    try {
      final items = await getTrashItems();
      items.add(item);
      await _saveTrashItems(items);
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'trash_service',
        message: 'Failed to save trash item',
        extra: {'error': e.toString()},
      );
    }
  }

  /// Removes a trash item from SharedPreferences.
  Future<void> _removeTrashItem(TrashItem item) async {
    try {
      final items = await getTrashItems();
      items.removeWhere((i) => i.trashPath == item.trashPath);
      await _saveTrashItems(items);
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'trash_service',
        message: 'Failed to remove trash item',
        extra: {'error': e.toString()},
      );
    }
  }

  /// Saves all trash items to SharedPreferences.
  Future<void> _saveTrashItems(List<TrashItem> items) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      // Convert to JSON strings for storage
      final itemsJson = items.map((item) => jsonEncode(item.toJson())).toList();
      await prefs.setStringList(_trashItemsKey, itemsJson);
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'trash_service',
        message: 'Failed to save trash items',
        extra: {'error': e.toString()},
      );
    }
  }

  /// Exports trash items to CSV format.
  ///
  /// Returns a CSV string with all trash items.
  Future<String> exportToCsv() async {
    try {
      final items = await getTrashItems();
      final dateFormat = DateFormat('yyyy-MM-dd HH:mm:ss');

      // CSV header
      final buffer = StringBuffer()
        ..writeln(
          'Group Name,Original Path,Trash Path,Deleted At,File Count,Total Size (bytes),Age (days)',
        );

      // CSV rows
      for (final item in items) {
        final deletedAt = dateFormat.format(item.deletedAt);
        final ageInDays = item.ageInDays;
        buffer.writeln(
          '"${item.groupName}",'
          '"${item.originalPath}",'
          '"${item.trashPath}",'
          '"$deletedAt",'
          '${item.fileCount},'
          '${item.totalSize},'
          '$ageInDays',
        );
      }

      await _logger.log(
        level: 'info',
        subsystem: 'trash_service',
        message: 'Exported trash items to CSV',
        extra: {'itemCount': items.length},
      );

      return buffer.toString();
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'trash_service',
        message: 'Failed to export to CSV',
        extra: {'error': e.toString()},
      );
      rethrow;
    }
  }

  /// Exports trash items to JSON format.
  ///
  /// Returns a JSON string with all trash items.
  Future<String> exportToJson() async {
    try {
      final items = await getTrashItems();
      final totalSize = await getTrashSize();

      final exportData = {
        'version': 1,
        'exported_at': DateTime.now().toIso8601String(),
        'summary': {
          'total_items': items.length,
          'total_size_bytes': totalSize,
          'total_size_formatted': _formatSize(totalSize),
        },
        'items': items.map((item) => item.toJson()).toList(),
      };

      const encoder = JsonEncoder.withIndent('  ');
      final jsonString = encoder.convert(exportData);

      await _logger.log(
        level: 'info',
        subsystem: 'trash_service',
        message: 'Exported trash items to JSON',
        extra: {'itemCount': items.length},
      );

      return jsonString;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'trash_service',
        message: 'Failed to export to JSON',
        extra: {'error': e.toString()},
      );
      rethrow;
    }
  }

  /// Exports trash items to a file.
  ///
  /// The [format] parameter specifies the export format ('csv' or 'json').
  /// The [filePath] parameter is optional - if not provided, saves to app documents directory.
  ///
  /// Returns the path to the saved file.
  Future<String> exportToFile({
    String format = 'csv',
    String? filePath,
  }) async {
    try {
      String content;
      String extension;

      switch (format.toLowerCase()) {
        case 'csv':
          content = await exportToCsv();
          extension = 'csv';
          break;
        case 'json':
          content = await exportToJson();
          extension = 'json';
          break;
        default:
          throw Exception('Unsupported export format: $format');
      }

      if (filePath != null) {
        final file = File(filePath);
        await file.writeAsString(content, flush: true);
        return filePath;
      }

      // Save to documents directory with timestamp
      final appDocDir = await getApplicationDocumentsDirectory();
      final timestamp =
          DateFormat('yyyy-MM-dd_HH-mm-ss').format(DateTime.now());
      final exportFile = File(
        '${appDocDir.path}/jabook_trash_export_$timestamp.$extension',
      );
      await exportFile.writeAsString(content, flush: true);

      await _logger.log(
        level: 'info',
        subsystem: 'trash_service',
        message: 'Exported trash to file',
        extra: {'path': exportFile.path, 'format': format},
      );

      return exportFile.path;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'trash_service',
        message: 'Failed to export to file',
        extra: {'error': e.toString()},
      );
      rethrow;
    }
  }

  /// Helper method to format size.
  String _formatSize(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    if (bytes < 1024 * 1024 * 1024) {
      return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
    }
    return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(1)} GB';
  }
}
