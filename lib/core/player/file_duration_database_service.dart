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

import 'package:jabook/core/data/local/database/app_database.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:sembast/sembast.dart';

/// Service for persisting audio file durations to database.
///
/// According to best practices:
/// - Primary source: player.duration (from ExoPlayer after STATE_READY)
/// - Fallback: MediaMetadataRetriever (only if player doesn't provide duration)
/// - Cache durations in database to persist across app restarts
class FileDurationDatabaseService {
  /// Creates a new FileDurationDatabaseService instance.
  FileDurationDatabaseService({
    AppDatabase? appDatabase,
  }) : _appDatabase = appDatabase;

  final AppDatabase? _appDatabase;
  final StructuredLogger _logger = StructuredLogger();

  /// Gets the file duration store from the database.
  Future<StoreRef<String, Map<String, dynamic>>> _getStore() async {
    if (_appDatabase == null) {
      throw StateError('AppDatabase not provided');
    }
    await _appDatabase.ensureInitialized();
    return _appDatabase.fileDurationStore;
  }

  /// Saves file duration to database.
  ///
  /// The [filePath] parameter is the absolute path to the audio file.
  /// The [durationMs] parameter is the duration in milliseconds.
  /// The [source] parameter indicates the source: "player" (from ExoPlayer) or "retriever" (from MediaMetadataRetriever).
  /// Returns true if saved successfully, false otherwise.
  Future<bool> saveDuration(
    String filePath,
    int durationMs,
    String source,
  ) async {
    if (durationMs <= 0) {
      return false;
    }

    try {
      final store = await _getStore();
      if (_appDatabase == null) {
        throw StateError('AppDatabase not provided');
      }
      final db = await _appDatabase.ensureInitialized();

      final now = DateTime.now().toIso8601String();
      final record = await store.record(filePath).get(db);

      final json = <String, dynamic>{
        'file_path': filePath,
        'duration_ms': durationMs,
        'source': source,
        if (record == null) 'saved_at': now,
        'updated_at': now,
      };

      await store.record(filePath).put(db, json);

      await _logger.log(
        level: 'debug',
        subsystem: 'file_duration_db',
        message: 'Saved file duration to database',
        extra: {
          'file_path': filePath,
          'duration_ms': durationMs,
          'source': source,
        },
      );

      return true;
    } on Object catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'file_duration_db',
        message: 'Failed to save file duration',
        extra: {
          'file_path': filePath,
          'error': e.toString(),
        },
      );
      return false;
    }
  }

  /// Gets file duration from database.
  ///
  /// The [filePath] parameter is the absolute path to the audio file.
  /// Returns duration in milliseconds if found, null otherwise.
  Future<int?> getDuration(String filePath) async {
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

      // Validate that file still exists before returning cached duration
      final file = File(filePath);
      if (!await file.exists()) {
        // File no longer exists, remove from cache
        await store.record(filePath).delete(db);
        return null;
      }

      final durationMs = record['duration_ms'] as int?;
      if (durationMs == null || durationMs <= 0) {
        return null;
      }

      return durationMs;
    } on Object catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'file_duration_db',
        message: 'Failed to get file duration',
        extra: {
          'file_path': filePath,
          'error': e.toString(),
        },
      );
      return null;
    }
  }

  /// Clears file duration from database.
  ///
  /// The [filePath] parameter is the absolute path to the audio file.
  Future<void> clearDuration(String filePath) async {
    try {
      final store = await _getStore();
      if (_appDatabase == null) {
        throw StateError('AppDatabase not provided');
      }
      final db = await _appDatabase.ensureInitialized();
      await store.record(filePath).delete(db);

      await _logger.log(
        level: 'debug',
        subsystem: 'file_duration_db',
        message: 'Cleared file duration from database',
        extra: {
          'file_path': filePath,
        },
      );
    } on Object catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'file_duration_db',
        message: 'Failed to clear file duration',
        extra: {
          'file_path': filePath,
          'error': e.toString(),
        },
      );
    }
  }

  /// Clears all file durations from database.
  ///
  /// Useful for cleanup or when rebuilding cache.
  Future<void> clearAllDurations() async {
    try {
      final store = await _getStore();
      if (_appDatabase == null) {
        throw StateError('AppDatabase not provided');
      }
      final db = await _appDatabase.ensureInitialized();
      await store.delete(db);

      await _logger.log(
        level: 'info',
        subsystem: 'file_duration_db',
        message: 'Cleared all file durations from database',
      );
    } on Object catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'file_duration_db',
        message: 'Failed to clear all file durations',
        extra: {
          'error': e.toString(),
        },
      );
    }
  }
}
