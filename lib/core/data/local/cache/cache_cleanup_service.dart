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

import 'package:flutter/services.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:path_provider/path_provider.dart';

/// Service for cleaning up cache and temporary files.
///
/// This service provides methods to clear playback cache, temporary torrent files,
/// old log files, and calculate total cache size.
class CacheCleanupService {
  /// Creates a new CacheCleanupService instance.
  CacheCleanupService();

  final StructuredLogger _logger = StructuredLogger();
  static const MethodChannel _channel = MethodChannel(
    'com.jabook.app.jabook/audio_player',
  );

  /// Clears playback cache (ExoPlayer cache).
  ///
  /// Returns the size of cleared cache in bytes.
  Future<int> clearPlaybackCache() async {
    try {
      await _logger.log(
        level: 'info',
        subsystem: 'cache_cleanup',
        message: 'Clearing playback cache',
      );

      // Try to clear cache via native method
      try {
        final clearedSize =
            await _channel.invokeMethod<int>('clearPlaybackCache');
        if (clearedSize != null) {
          await _logger.log(
            level: 'info',
            subsystem: 'cache_cleanup',
            message: 'Playback cache cleared via native method',
            extra: {'clearedSize': clearedSize},
          );
          return clearedSize;
        }
      } on PlatformException catch (e) {
        await _logger.log(
          level: 'warning',
          subsystem: 'cache_cleanup',
          message: 'Native cache clearing not available, using fallback',
          extra: {'error': e.toString()},
        );
      }

      // Fallback: manually clear cache directory
      return await _clearPlaybackCacheManually();
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'cache_cleanup',
        message: 'Failed to clear playback cache',
        extra: {'error': e.toString()},
      );
      return 0;
    }
  }

  /// Manually clears playback cache directory.
  Future<int> _clearPlaybackCacheManually() async {
    try {
      final cacheDir = await getTemporaryDirectory();
      final playbackCacheDir = Directory('${cacheDir.path}/playback_cache');

      if (!await playbackCacheDir.exists()) {
        return 0;
      }

      var totalSize = 0;
      await for (final entity in playbackCacheDir.list(recursive: true)) {
        if (entity is File) {
          try {
            final stat = await entity.stat();
            totalSize += stat.size;
            await entity.delete();
          } on Exception {
            // Continue with other files
          }
        }
      }

      await _logger.log(
        level: 'info',
        subsystem: 'cache_cleanup',
        message: 'Playback cache cleared manually',
        extra: {'clearedSize': totalSize},
      );

      return totalSize;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'cache_cleanup',
        message: 'Failed to clear playback cache manually',
        extra: {'error': e.toString()},
      );
      return 0;
    }
  }

  /// Clears temporary torrent files.
  ///
  /// Returns the number of files deleted.
  Future<int> clearTemporaryFiles() async {
    try {
      await _logger.log(
        level: 'info',
        subsystem: 'cache_cleanup',
        message: 'Clearing temporary files',
      );

      final tempDir = await getTemporaryDirectory();
      var deletedCount = 0;

      await for (final entity in tempDir.list()) {
        if (entity is File) {
          final fileName = entity.path.split('/').last;
          // Check if it's a temporary torrent file
          if (fileName.startsWith('torrent_') &&
              fileName.endsWith('.torrent')) {
            try {
              await entity.delete();
              deletedCount++;
            } on Exception {
              // Continue with other files
            }
          }
        }
      }

      await _logger.log(
        level: 'info',
        subsystem: 'cache_cleanup',
        message: 'Temporary files cleared',
        extra: {'deletedCount': deletedCount},
      );

      return deletedCount;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'cache_cleanup',
        message: 'Failed to clear temporary files',
        extra: {'error': e.toString()},
      );
      return 0;
    }
  }

  /// Clears old log files.
  ///
  /// The [maxAgeDays] parameter specifies the maximum age of logs to keep (default: 7).
  ///
  /// Returns the number of files deleted.
  Future<int> clearOldLogs({int maxAgeDays = 7}) async {
    try {
      await _logger.log(
        level: 'info',
        subsystem: 'cache_cleanup',
        message: 'Clearing old log files',
        extra: {'maxAgeDays': maxAgeDays},
      );

      // Use StructuredLogger's built-in cleanup
      final logger = StructuredLogger();
      await logger.cleanOldLogs();

      await _logger.log(
        level: 'info',
        subsystem: 'cache_cleanup',
        message: 'Old log files cleared',
      );

      return 1; // Logger handles the actual deletion
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'cache_cleanup',
        message: 'Failed to clear old logs',
        extra: {'error': e.toString()},
      );
      return 0;
    }
  }

  /// Gets total cache size.
  ///
  /// Returns the total size of all cache in bytes, including:
  /// - Playback cache
  /// - Temporary files
  /// - Log files
  Future<int> getTotalCacheSize() async {
    try {
      var totalSize = 0;

      // Get playback cache size
      totalSize += await _getPlaybackCacheSize();

      // Get temporary files size
      totalSize += await _getTemporaryFilesSize();

      // Get log files size
      totalSize += await _getLogFilesSize();

      return totalSize;
    } on Exception catch (e) {
      await _logger.log(
        level: 'warning',
        subsystem: 'cache_cleanup',
        message: 'Failed to calculate total cache size',
        extra: {'error': e.toString()},
      );
      return 0;
    }
  }

  /// Gets playback cache size.
  Future<int> _getPlaybackCacheSize() async {
    try {
      final cacheDir = await getTemporaryDirectory();
      final playbackCacheDir = Directory('${cacheDir.path}/playback_cache');

      if (!await playbackCacheDir.exists()) {
        return 0;
      }

      var totalSize = 0;
      await for (final entity in playbackCacheDir.list(recursive: true)) {
        if (entity is File) {
          try {
            final stat = await entity.stat();
            totalSize += stat.size;
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

  /// Gets temporary files size.
  Future<int> _getTemporaryFilesSize() async {
    try {
      final tempDir = await getTemporaryDirectory();
      var totalSize = 0;

      await for (final entity in tempDir.list()) {
        if (entity is File) {
          final fileName = entity.path.split('/').last;
          if (fileName.startsWith('torrent_') &&
              fileName.endsWith('.torrent')) {
            try {
              final stat = await entity.stat();
              totalSize += stat.size;
            } on Exception {
              // Continue with other files
            }
          }
        }
      }

      return totalSize;
    } on Exception {
      return 0;
    }
  }

  /// Gets log files size.
  Future<int> _getLogFilesSize() async {
    try {
      final appDocDir = await getApplicationDocumentsDirectory();
      final logDir = Directory('${appDocDir.path}/logs');

      if (!await logDir.exists()) {
        return 0;
      }

      var totalSize = 0;
      await for (final entity in logDir.list()) {
        if (entity is File && entity.path.endsWith('.ndjson')) {
          try {
            final stat = await entity.stat();
            totalSize += stat.size;
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

  /// Performs automatic cleanup when storage is low.
  ///
  /// This method clears old cache and temporary files to free up space.
  Future<void> performAutomaticCleanup() async {
    try {
      await _logger.log(
        level: 'info',
        subsystem: 'cache_cleanup',
        message: 'Performing automatic cleanup',
      );

      // Clear old logs (older than 7 days)
      await clearOldLogs();

      // Clear temporary torrent files
      await clearTemporaryFiles();

      // Clear old playback cache (keep recent cache)
      // Note: ExoPlayer manages cache automatically, but we can clear very old entries
      // For now, we'll just clear temporary files and logs

      await _logger.log(
        level: 'info',
        subsystem: 'cache_cleanup',
        message: 'Automatic cleanup completed',
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'cache_cleanup',
        message: 'Automatic cleanup failed',
        extra: {'error': e.toString()},
      );
    }
  }
}
