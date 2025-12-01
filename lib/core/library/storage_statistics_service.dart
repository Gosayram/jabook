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

import 'package:jabook/core/cache/cache_cleanup_service.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:jabook/core/library/audiobook_file_manager.dart';
import 'package:jabook/core/library/audiobook_library_scanner.dart';
import 'package:jabook/core/library/trash_service.dart';
import 'package:jabook/core/utils/storage_path_utils.dart';
import 'package:path_provider/path_provider.dart';

/// Represents storage statistics breakdown by category.
class StorageBreakdown {
  /// Creates a new StorageBreakdown instance.
  StorageBreakdown({
    required this.librarySize,
    required this.cacheSize,
    required this.trashSize,
    required this.otherSize,
    this.totalSize = 0,
  }) {
    totalSize = librarySize + cacheSize + trashSize + otherSize;
  }

  /// Size of audiobook library in bytes.
  final int librarySize;

  /// Size of cache in bytes.
  final int cacheSize;

  /// Size of trash in bytes.
  final int trashSize;

  /// Size of other files in bytes.
  final int otherSize;

  /// Total size in bytes.
  late final int totalSize;

  /// Gets the percentage of library size.
  double get libraryPercentage =>
      totalSize > 0 ? (librarySize / totalSize) * 100 : 0;

  /// Gets the percentage of cache size.
  double get cachePercentage =>
      totalSize > 0 ? (cacheSize / totalSize) * 100 : 0;

  /// Gets the percentage of trash size.
  double get trashPercentage =>
      totalSize > 0 ? (trashSize / totalSize) * 100 : 0;

  /// Gets the percentage of other files size.
  double get otherPercentage =>
      totalSize > 0 ? (otherSize / totalSize) * 100 : 0;
}

/// Represents file type breakdown.
class FileTypeBreakdown {
  /// Creates a new FileTypeBreakdown instance.
  FileTypeBreakdown({
    required this.audioFiles,
    required this.coverImages,
    required this.otherFiles,
  });

  /// Size of audio files in bytes.
  final int audioFiles;

  /// Size of cover images in bytes.
  final int coverImages;

  /// Size of other files in bytes.
  final int otherFiles;

  /// Gets total size.
  int get totalSize => audioFiles + coverImages + otherFiles;

  /// Gets the percentage of audio files.
  double get audioPercentage =>
      totalSize > 0 ? (audioFiles / totalSize) * 100 : 0;

  /// Gets the percentage of cover images.
  double get coverPercentage =>
      totalSize > 0 ? (coverImages / totalSize) * 100 : 0;

  /// Gets the percentage of other files.
  double get otherPercentage =>
      totalSize > 0 ? (otherFiles / totalSize) * 100 : 0;
}

/// Represents storage forecast.
class StorageForecast {
  /// Creates a new StorageForecast instance.
  StorageForecast({
    required this.currentSize,
    required this.availableSpace,
    required this.growthRateBytesPerDay,
    this.daysUntilFull,
  }) {
    if (growthRateBytesPerDay > 0 && availableSpace > 0) {
      daysUntilFull = (availableSpace / growthRateBytesPerDay).ceil();
    } else {
      daysUntilFull = null;
    }
  }

  /// Current used size in bytes.
  final int currentSize;

  /// Available space in bytes.
  final int availableSpace;

  /// Growth rate in bytes per day.
  final int growthRateBytesPerDay;

  /// Days until storage is full (null if cannot be calculated).
  int? daysUntilFull;

  /// Gets total storage capacity.
  int get totalCapacity => currentSize + availableSpace;

  /// Gets usage percentage.
  double get usagePercentage =>
      totalCapacity > 0 ? (currentSize / totalCapacity) * 100 : 0;
}

/// Service for collecting and analyzing storage statistics.
class StorageStatisticsService {
  /// Creates a new StorageStatisticsService instance.
  ///
  /// The [scanner] parameter is optional - if not provided, a new instance will be created.
  /// The [fileManager] parameter is optional - if not provided, a new instance will be created.
  /// Note: If creating a new fileManager, it won't have Media3PlayerService, which is less ideal.
  /// For dependency injection, prefer passing an initialized AudiobookFileManager with Media3PlayerService.
  /// The [cacheService] parameter is optional - if not provided, a new instance will be created.
  /// The [trashService] parameter is optional - if not provided, a new instance will be created.
  /// The [storageUtils] parameter is optional - if not provided, a new instance will be created.
  StorageStatisticsService({
    AudiobookLibraryScanner? scanner,
    AudiobookFileManager? fileManager,
    CacheCleanupService? cacheService,
    TrashService? trashService,
    StoragePathUtils? storageUtils,
  })  : _scanner = scanner ?? AudiobookLibraryScanner(),
        _fileManager = fileManager ?? AudiobookFileManager(),
        _cacheService = cacheService ?? CacheCleanupService(),
        _trashService = trashService ?? TrashService(),
        _storageUtils = storageUtils ?? StoragePathUtils();

  final AudiobookLibraryScanner _scanner;
  final AudiobookFileManager _fileManager;
  final CacheCleanupService _cacheService;
  final TrashService _trashService;
  final StoragePathUtils _storageUtils;
  final StructuredLogger _logger = StructuredLogger();

  /// Gets storage breakdown by category.
  ///
  /// Uses parallel processing for different categories (library, cache, trash)
  /// to improve performance. This is a low-priority operation that shouldn't
  /// block UI or other critical operations.
  Future<StorageBreakdown> getStorageBreakdown() async {
    try {
      // Get groups first (needed for library size calculation)
      final groups = await _scanner.scanAllLibraryFolders();

      // Process library, cache, and trash sizes in parallel (max 3 concurrent)
      // This significantly improves performance for large storage
      final results = await Future.wait([
        // Calculate library size
        () async {
          var librarySize = 0;
          for (final group in groups) {
            librarySize += await _fileManager.calculateGroupSize(group);
          }
          return librarySize;
        }(),
        // Get cache size
        _cacheService.getTotalCacheSize(),
        // Get trash size
        _trashService.getTrashSize(),
      ]);

      final librarySize = results[0];
      final cacheSize = results[1];
      final trashSize = results[2];

      // Calculate other size (app data, logs, etc.)
      var otherSize = 0;
      try {
        final appDocDir = await getApplicationDocumentsDirectory();
        otherSize = await _calculateDirectorySize(appDocDir.path);
        // Subtract known sizes from app documents
        otherSize -= cacheSize; // Cache is already counted
      } on Exception {
        // Ignore errors calculating other size
      }

      return StorageBreakdown(
        librarySize: librarySize,
        cacheSize: cacheSize,
        trashSize: trashSize,
        otherSize: otherSize > 0 ? otherSize : 0,
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'storage_statistics',
        message: 'Failed to get storage breakdown',
        extra: {'error': e.toString()},
      );
      return StorageBreakdown(
        librarySize: 0,
        cacheSize: 0,
        trashSize: 0,
        otherSize: 0,
      );
    }
  }

  /// Gets file type breakdown for library.
  Future<FileTypeBreakdown> getFileTypeBreakdown() async {
    try {
      var audioFiles = 0;
      var coverImages = 0;
      const otherFiles = 0;

      final groups = await _scanner.scanAllLibraryFolders();
      for (final group in groups) {
        // Count audio files
        for (final file in group.files) {
          try {
            final fileEntity = File(file.filePath);
            if (await fileEntity.exists()) {
              final stat = await fileEntity.stat();
              audioFiles += stat.size;
            } else {
              audioFiles += file.fileSize;
            }
          } on Exception {
            audioFiles += file.fileSize;
          }
        }

        // Count cover images
        if (group.coverPath != null) {
          try {
            final coverFile = File(group.coverPath!);
            if (await coverFile.exists()) {
              final stat = await coverFile.stat();
              coverImages += stat.size;
            }
          } on Exception {
            // Ignore cover errors
          }
        }
      }

      return FileTypeBreakdown(
        audioFiles: audioFiles,
        coverImages: coverImages,
        otherFiles: otherFiles,
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'storage_statistics',
        message: 'Failed to get file type breakdown',
        extra: {'error': e.toString()},
      );
      return FileTypeBreakdown(
        audioFiles: 0,
        coverImages: 0,
        otherFiles: 0,
      );
    }
  }

  /// Gets storage forecast based on historical data.
  ///
  /// The [historicalDays] parameter specifies how many days of history to use.
  Future<StorageForecast> getStorageForecast({int historicalDays = 30}) async {
    try {
      final breakdown = await getStorageBreakdown();
      final currentSize = breakdown.totalSize;

      // Get available space
      var availableSpace = 0;
      try {
        final libraryFolders = await _storageUtils.getLibraryFolders();
        if (libraryFolders.isNotEmpty) {
          final firstFolder = Directory(libraryFolders.first);
          if (await firstFolder.exists()) {
            // Note: This is an approximation, actual available space
            // would require platform-specific APIs
            availableSpace = 1024 * 1024 * 1024; // Default to 1GB estimate
          }
        }
      } on Exception {
        // Use default estimate
        availableSpace = 1024 * 1024 * 1024;
      }

      // Calculate growth rate (simplified - would need historical data)
      // For now, estimate based on average group size
      final groups = await _scanner.scanAllLibraryFolders();
      var averageGroupSize = 0;
      if (groups.isNotEmpty) {
        for (final group in groups) {
          averageGroupSize += await _fileManager.calculateGroupSize(group);
        }
        averageGroupSize = averageGroupSize ~/ groups.length;
      }

      // Estimate growth rate (assume 1 new group per week on average)
      final growthRateBytesPerDay = averageGroupSize ~/ 7;

      return StorageForecast(
        currentSize: currentSize,
        availableSpace: availableSpace,
        growthRateBytesPerDay: growthRateBytesPerDay,
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'storage_statistics',
        message: 'Failed to get storage forecast',
        extra: {'error': e.toString()},
      );
      return StorageForecast(
        currentSize: 0,
        availableSpace: 0,
        growthRateBytesPerDay: 0,
      );
    }
  }

  /// Gets folder size breakdown.
  Future<Map<String, int>> getFolderBreakdown() async {
    try {
      final libraryFolders = await _storageUtils.getLibraryFolders();
      final folderSizes = <String, int>{};

      for (final folder in libraryFolders) {
        folderSizes[folder] = await _calculateDirectorySize(folder);
      }

      return folderSizes;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'storage_statistics',
        message: 'Failed to get folder breakdown',
        extra: {'error': e.toString()},
      );
      return {};
    }
  }

  /// Calculates directory size recursively.
  ///
  /// For large directories, yields periodically to prevent blocking other operations.
  Future<int> _calculateDirectorySize(String path) async {
    try {
      final dir = Directory(path);
      if (!await dir.exists()) return 0;

      var totalSize = 0;
      var fileCount = 0;
      await for (final entity in dir.list(recursive: true)) {
        if (entity is File) {
          try {
            final stat = await entity.stat();
            totalSize = (totalSize + stat.size).toInt();
            fileCount++;

            // Yield every 1000 files for large directories to prevent blocking
            if (fileCount % 1000 == 0) {
              await Future.microtask(() {});
            }
          } on Exception {
            // Continue with other files
          }
        }
      }
      return totalSize;
    } on Exception {
      return 0;
    }
  }
}
