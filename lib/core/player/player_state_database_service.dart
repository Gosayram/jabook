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
import 'package:jabook/core/player/player_state_persistence_service.dart';
import 'package:sembast/sembast.dart';

/// Service for persisting player state to database.
///
/// This service provides more reliable storage than SharedPreferences
/// by using the database. It also includes validation and integrity checks.
class PlayerStateDatabaseService {
  /// Creates a new PlayerStateDatabaseService instance.
  PlayerStateDatabaseService({
    AppDatabase? appDatabase,
  }) : _appDatabase = appDatabase;

  final AppDatabase? _appDatabase;
  final StructuredLogger _logger = StructuredLogger();

  /// Gets the player state store from the database.
  Future<StoreRef<String, Map<String, dynamic>>> _getStore() async {
    if (_appDatabase == null) {
      throw StateError('AppDatabase not provided');
    }
    await _appDatabase.ensureInitialized();
    return StoreRef<String, Map<String, dynamic>>('player_state');
  }

  /// Validates player state before saving.
  ///
  /// Returns true if state is valid, false otherwise.
  bool _validateState(SavedPlayerState state) {
    // Check that currentIndex is within bounds
    if (state.currentIndex < 0 ||
        (state.filePaths.isNotEmpty &&
            state.currentIndex >= state.filePaths.length)) {
      return false;
    }

    // Check that position is non-negative
    if (state.currentPosition < 0) {
      return false;
    }

    // Check that playback speed is reasonable (0.25x to 4.0x)
    if (state.playbackSpeed < 0.25 || state.playbackSpeed > 4.0) {
      return false;
    }

    // Check that repeat mode is valid (0, 1, or 2)
    if (state.repeatMode < 0 || state.repeatMode > 2) {
      return false;
    }

    return true;
  }

  /// Validates that files in the state still exist.
  ///
  /// Returns true if all files exist, false otherwise.
  Future<bool> _validateFilesExist(SavedPlayerState state) async {
    if (state.filePaths.isEmpty) {
      return false;
    }

    // Check that current file exists
    if (state.currentIndex >= 0 &&
        state.currentIndex < state.filePaths.length) {
      final currentFilePath = state.filePaths[state.currentIndex];
      final file = File(currentFilePath);
      if (!await file.exists()) {
        return false;
      }
    }

    // Optionally check all files (can be expensive for large playlists)
    // For now, we only check the current file
    return true;
  }

  /// Saves player state to database.
  ///
  /// The [state] parameter is the player state to save.
  /// Returns true if saved successfully, false if validation failed.
  Future<bool> saveState(SavedPlayerState state) async {
    try {
      // Validate state before saving
      if (!_validateState(state)) {
        await _logger.log(
          level: 'warning',
          subsystem: 'player_state_db',
          message: 'Invalid player state, skipping save',
          extra: {
            'group_path': state.groupPath,
            'current_index': state.currentIndex,
            'file_paths_count': state.filePaths.length,
            'current_position': state.currentPosition,
          },
        );
        return false;
      }

      final store = await _getStore();
      if (_appDatabase == null) {
        throw StateError('AppDatabase not provided');
      }
      final db = await _appDatabase.ensureInitialized();

      final json = state.toJson();
      json['saved_at'] = DateTime.now().toIso8601String();

      await store.record(state.groupPath).put(db, json);

      await _logger.log(
        level: 'debug',
        subsystem: 'player_state_db',
        message: 'Saved player state to database',
        extra: {
          'group_path': state.groupPath,
          'current_index': state.currentIndex,
          'current_position': state.currentPosition,
        },
      );

      return true;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'player_state_db',
        message: 'Failed to save player state',
        extra: {
          'group_path': state.groupPath,
          'error': e.toString(),
        },
      );
      return false;
    }
  }

  /// Restores player state from database.
  ///
  /// The [groupPath] parameter is the group path to restore state for.
  /// Returns [SavedPlayerState] if found and valid, null otherwise.
  Future<SavedPlayerState?> restoreState(String groupPath) async {
    try {
      final store = await _getStore();
      if (_appDatabase == null) {
        throw StateError('AppDatabase not provided');
      }
      final db = await _appDatabase.ensureInitialized();
      final record = await store.record(groupPath).get(db);

      if (record == null) {
        return null;
      }

      final state = SavedPlayerState.fromJson(record);

      // Validate restored state
      if (!_validateState(state)) {
        await _logger.log(
          level: 'warning',
          subsystem: 'player_state_db',
          message: 'Invalid restored state, clearing',
          extra: {
            'group_path': groupPath,
          },
        );
        await clearState(groupPath);
        return null;
      }

      // Validate that files still exist
      final filesExist = await _validateFilesExist(state);
      if (!filesExist) {
        await _logger.log(
          level: 'warning',
          subsystem: 'player_state_db',
          message: 'Files no longer exist, clearing state',
          extra: {
            'group_path': groupPath,
          },
        );
        await clearState(groupPath);
        return null;
      }

      await _logger.log(
        level: 'debug',
        subsystem: 'player_state_db',
        message: 'Restored player state from database',
        extra: {
          'group_path': groupPath,
          'current_index': state.currentIndex,
          'current_position': state.currentPosition,
        },
      );

      return state;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'player_state_db',
        message: 'Failed to restore player state',
        extra: {
          'group_path': groupPath,
          'error': e.toString(),
        },
      );
      return null;
    }
  }

  /// Clears player state from database.
  ///
  /// The [groupPath] parameter is the group path to clear state for.
  Future<void> clearState(String groupPath) async {
    try {
      final store = await _getStore();
      if (_appDatabase == null) {
        throw StateError('AppDatabase not provided');
      }
      final db = await _appDatabase.ensureInitialized();
      await store.record(groupPath).delete(db);

      await _logger.log(
        level: 'debug',
        subsystem: 'player_state_db',
        message: 'Cleared player state from database',
        extra: {
          'group_path': groupPath,
        },
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'player_state_db',
        message: 'Failed to clear player state',
        extra: {
          'group_path': groupPath,
          'error': e.toString(),
        },
      );
    }
  }

  /// Checks if there is a saved player state.
  ///
  /// The [groupPath] parameter is the group path to check.
  Future<bool> hasSavedState(String groupPath) async {
    try {
      final store = await _getStore();
      if (_appDatabase == null) {
        throw StateError('AppDatabase not provided');
      }
      final db = await _appDatabase.ensureInitialized();
      final record = await store.record(groupPath).get(db);
      return record != null;
    } on Exception {
      return false;
    }
  }
}
