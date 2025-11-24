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

import 'package:jabook/core/errors/failures.dart';
import 'package:jabook/core/library/playback_position_service.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/core/player/native_audio_player.dart';
import 'package:jabook/core/player/player_state_persistence_service.dart';

/// Service for managing Media3 audio player.
///
/// This service provides a unified interface for managing audio playback
/// using Media3 ExoPlayer through NativeAudioPlayer. It handles state management,
/// error handling, retry logic, and synchronization with position saving.
class Media3PlayerService {
  /// Native audio player instance.
  final NativeAudioPlayer _player = NativeAudioPlayer();

  /// Playback position service for saving positions.
  final PlaybackPositionService _positionService = PlaybackPositionService();

  /// Player state persistence service for saving full state.
  final PlayerStatePersistenceService _statePersistenceService =
      PlayerStatePersistenceService();

  /// Logger for structured logging.
  final StructuredLogger _logger = StructuredLogger();

  /// Stream of player state updates.
  Stream<AudioPlayerState> get stateStream => _player.stateStream;

  /// Current group path for position saving.
  String? _currentGroupPath;

  /// Timer for periodic position saving.
  Timer? _positionSaveTimer;

  /// Whether the service is initialized.
  bool _isInitialized = false;

  /// Initializes the player service.
  ///
  /// Throws [AudioFailure] if initialization fails.
  /// Includes timeout to prevent blocking initialization.
  Future<void> initialize() async {
    if (_isInitialized) return;

    try {
      await _logger.log(
        level: 'info',
        subsystem: 'audio',
        message: 'Initializing Media3PlayerService',
      );

      final initStart = DateTime.now();

      // Add timeout to prevent blocking initialization (10 seconds)
      await _player.initialize().timeout(
        const Duration(seconds: 10),
        onTimeout: () {
          throw TimeoutException(
            'Player initialization timed out after 10 seconds',
            const Duration(seconds: 10),
          );
        },
      );

      final initDuration = DateTime.now().difference(initStart).inMilliseconds;
      _isInitialized = true;

      await _logger.log(
        level: 'info',
        subsystem: 'audio',
        message: 'Media3PlayerService initialized successfully',
        durationMs: initDuration,
        extra: {'init_duration_ms': initDuration},
      );
    } on TimeoutException catch (e) {
      await _logger.log(
        level: 'warning',
        subsystem: 'audio',
        message:
            'Player initialization timed out - will initialize on first use',
        cause: e.toString(),
        durationMs: 10000,
      );
      // Don't set _isInitialized = true, so it will retry on first use
      throw AudioFailure('Player initialization timed out: ${e.message}');
    } on AudioFailure {
      // Re-throw AudioFailure as-is (already logged in native_audio_player)
      rethrow;
    } on Exception catch (e) {
      await _logger.log(
        level: 'warning',
        subsystem: 'audio',
        message:
            'Failed to initialize Media3PlayerService - will initialize on first use',
        cause: e.toString(),
      );
      // Don't set _isInitialized = true, so it will retry on first use
      throw AudioFailure('Failed to initialize: ${e.toString()}');
    }
  }

  /// Sets playlist and starts position saving.
  ///
  /// [filePaths] is a list of absolute file paths or HTTP(S) URLs to audio files.
  /// Supports both local files and network streaming.
  /// [metadata] is optional metadata (title, artist, album, artworkUri).
  /// [groupPath] is the unique path for saving playback positions.
  ///
  /// Throws [AudioFailure] if setting playlist fails.
  Future<void> setPlaylist(
    List<String> filePaths, {
    Map<String, String>? metadata,
    String? groupPath,
  }) async {
    if (!_isInitialized) {
      await initialize();
    }

    try {
      await _logger.log(
        level: 'info',
        subsystem: 'audio',
        message: 'Setting playlist',
        extra: {
          'file_count': filePaths.length,
          'group_path': groupPath,
        },
      );

      await _player.setPlaylist(filePaths, metadata: metadata);
      _currentGroupPath = groupPath;

      // Start periodic position saving if group path is provided
      if (groupPath != null) {
        _startPositionSaving();
        // Save full player state
        await _saveFullState(filePaths, metadata, groupPath);
      }

      await _logger.log(
        level: 'info',
        subsystem: 'audio',
        message: 'Playlist set successfully',
      );
    } on AudioFailure {
      rethrow;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'audio',
        message: 'Failed to set playlist',
        cause: e.toString(),
      );
      throw AudioFailure('Failed to set playlist: ${e.toString()}');
    }
  }

  /// Starts or resumes playback.
  ///
  /// Throws [AudioFailure] if playback fails.
  Future<void> play() async {
    try {
      await _player.play();
      // Update saved state
      await _updateFullState();
    } on AudioFailure {
      rethrow;
    } on Exception catch (e) {
      throw AudioFailure('Failed to play: ${e.toString()}');
    }
  }

  /// Pauses playback.
  ///
  /// Throws [AudioFailure] if pausing fails.
  Future<void> pause() async {
    try {
      await _player.pause();
      // Save position immediately on pause
      await _saveCurrentPosition();
    } on AudioFailure {
      rethrow;
    } on Exception catch (e) {
      throw AudioFailure('Failed to pause: ${e.toString()}');
    }
  }

  /// Stops playback.
  ///
  /// Throws [AudioFailure] if stopping fails.
  Future<void> stop() async {
    try {
      await _player.stop();
      await _saveCurrentPosition();
      // Clear saved state on stop
      await clearSavedState();
    } on AudioFailure {
      rethrow;
    } on Exception catch (e) {
      throw AudioFailure('Failed to stop: ${e.toString()}');
    }
  }

  /// Seeks to specific position.
  ///
  /// [position] is the position as Duration.
  ///
  /// Throws [AudioFailure] if seeking fails.
  Future<void> seek(Duration position) async {
    try {
      await _player.seek(position);
      // Save position immediately after seek
      await _saveCurrentPosition();
    } on AudioFailure {
      rethrow;
    } on Exception catch (e) {
      throw AudioFailure('Failed to seek: ${e.toString()}');
    }
  }

  /// Sets playback speed.
  ///
  /// [speed] is the playback speed (0.5 to 2.0).
  ///
  /// Throws [AudioFailure] if setting speed fails.
  Future<void> setSpeed(double speed) async {
    try {
      await _player.setSpeed(speed);
      // Update saved state
      await _updateFullState();
    } on AudioFailure {
      rethrow;
    } on Exception catch (e) {
      throw AudioFailure('Failed to set speed: ${e.toString()}');
    }
  }

  /// Updates skip durations for MediaSessionManager.
  ///
  /// [rewindSeconds] is the duration in seconds for rewind action.
  /// [forwardSeconds] is the duration in seconds for forward action.
  ///
  /// Throws [AudioFailure] if updating fails.
  Future<void> updateSkipDurations(
      int rewindSeconds, int forwardSeconds) async {
    try {
      await _player.updateSkipDurations(rewindSeconds, forwardSeconds);
    } on AudioFailure {
      rethrow;
    } on Exception catch (e) {
      throw AudioFailure('Failed to update skip durations: ${e.toString()}');
    }
  }

  /// Skips to next track.
  ///
  /// Throws [AudioFailure] if skipping fails.
  Future<void> next() async {
    try {
      await _player.next();
      // Save position immediately after track change
      await _saveCurrentPosition();
    } on AudioFailure {
      rethrow;
    } on Exception catch (e) {
      throw AudioFailure('Failed to skip next: ${e.toString()}');
    }
  }

  /// Skips to previous track.
  ///
  /// Throws [AudioFailure] if skipping fails.
  Future<void> previous() async {
    try {
      await _player.previous();
      // Save position immediately after track change
      await _saveCurrentPosition();
    } on AudioFailure {
      rethrow;
    } on Exception catch (e) {
      throw AudioFailure('Failed to skip previous: ${e.toString()}');
    }
  }

  /// Seeks to specific track by index.
  ///
  /// [index] is the track index in the playlist (0-based).
  ///
  /// Throws [AudioFailure] if seeking fails.
  Future<void> seekToTrack(int index) async {
    try {
      await _player.seekToTrack(index);
      // Save position immediately after track change
      await _saveCurrentPosition();
    } on AudioFailure {
      rethrow;
    } on Exception catch (e) {
      throw AudioFailure('Failed to seek to track: ${e.toString()}');
    }
  }

  /// Seeks to specific track and position.
  ///
  /// [trackIndex] is the track index in the playlist (0-based).
  /// [position] is the position within the track.
  ///
  /// Throws [AudioFailure] if seeking fails.
  Future<void> seekToTrackAndPosition(int trackIndex, Duration position) async {
    try {
      await _player.seekToTrackAndPosition(trackIndex, position);
      // Save position immediately after seek
      await _saveCurrentPosition();
    } on AudioFailure {
      rethrow;
    } on Exception catch (e) {
      throw AudioFailure(
          'Failed to seek to track and position: ${e.toString()}');
    }
  }

  /// Gets current player state.
  ///
  /// Returns [AudioPlayerState] with current player information.
  Future<AudioPlayerState> getState() async {
    try {
      return await _player.getState();
    } on AudioFailure {
      rethrow;
    } on Exception catch (e) {
      throw AudioFailure('Failed to get state: ${e.toString()}');
    }
  }

  /// Updates metadata for current track.
  ///
  /// [metadata] is a map with title, artist, album, etc.
  ///
  /// Throws [AudioFailure] if update fails.
  Future<void> updateMetadata(Map<String, String> metadata) async {
    try {
      await _player.updateMetadata(metadata);
    } on AudioFailure {
      rethrow;
    } on Exception catch (e) {
      throw AudioFailure('Failed to update metadata: ${e.toString()}');
    }
  }

  /// Restores saved playback position for a group.
  ///
  /// [groupPath] is the unique path identifying the group.
  ///
  /// Returns a map with 'trackIndex' and 'positionMs', or null if no saved position exists.
  Future<Map<String, int>?> restorePosition(String groupPath) async =>
      _positionService.restorePosition(groupPath);

  /// Starts periodic position saving with adaptive intervals.
  ///
  /// Inspired by lissen-android: saves more frequently near start/end of track
  /// (every 5 seconds) and less frequently in the middle (every 30 seconds).
  /// This balances between position accuracy and battery/performance.
  void _startPositionSaving() {
    _positionSaveTimer?.cancel();

    // Use adaptive interval based on position in track
    _positionSaveTimer = Timer.periodic(
      const Duration(seconds: 5), // Check every 5 seconds
      (_) => _saveCurrentPositionAdaptive(),
    );
  }

  /// Saves position with adaptive interval logic.
  ///
  /// Saves more frequently (every 5s) when near start/end of track,
  /// less frequently (every 30s) in the middle.
  Future<void> _saveCurrentPositionAdaptive() async {
    if (_currentGroupPath == null) return;

    try {
      final state = await _player.getState();
      final position = state.currentPosition;
      final duration = state.duration;

      if (duration <= 0) {
        // Duration unknown, save anyway
        await _saveCurrentPosition();
        return;
      }

      // Calculate position ratio (0.0 to 1.0)
      final positionRatio = position / duration;

      // Define "near start" and "near end" thresholds
      // Save frequently if within first 10% or last 10% of track
      const nearStartThreshold = 0.1; // 10% from start
      const nearEndThreshold = 0.9; // 10% from end

      final isNearStart = positionRatio < nearStartThreshold;
      final isNearEnd = positionRatio > nearEndThreshold;

      // Track last save time to implement adaptive intervals
      final now = DateTime.now();
      final lastSaveTime = _lastPositionSaveTime;
      _lastPositionSaveTime = now;

      if (lastSaveTime == null) {
        // First save, always save
        await _saveCurrentPosition();
        return;
      }

      final timeSinceLastSave = now.difference(lastSaveTime);

      // Save if:
      // - Near start/end and 5+ seconds passed (frequent saves)
      // - In middle and 30+ seconds passed (less frequent saves)
      final shouldSave = (isNearStart || isNearEnd)
          ? timeSinceLastSave.inSeconds >= 5
          : timeSinceLastSave.inSeconds >= 30;

      if (shouldSave) {
        await _saveCurrentPosition();
      }
    } on Exception {
      // Ignore errors - position saving is not critical
    }
  }

  /// Timestamp of last position save for adaptive interval calculation.
  DateTime? _lastPositionSaveTime;

  /// Saves current playback position.
  Future<void> _saveCurrentPosition() async {
    if (_currentGroupPath == null) return;

    try {
      final state = await _player.getState();
      await _positionService.savePosition(
        _currentGroupPath!,
        state.currentIndex,
        state.currentPosition,
      );
      // Also update full state
      await _updateFullState();
    } on Exception {
      // Ignore errors - position saving is not critical
    }
  }

  /// Saves current playback position (public method for lifecycle events).
  ///
  /// This method can be called from app lifecycle handlers to ensure
  /// position is saved when app is paused or closed.
  Future<void> saveCurrentPosition() async {
    await _saveCurrentPosition();
  }

  /// Saves full player state including playlist, position, speed, etc.
  Future<void> _saveFullState(
    List<String> filePaths,
    Map<String, String>? metadata,
    String groupPath,
  ) async {
    try {
      final state = await _player.getState();
      final savedState = SavedPlayerState(
        groupPath: groupPath,
        filePaths: filePaths,
        metadata: metadata,
        currentIndex: state.currentIndex,
        currentPosition: state.currentPosition,
        playbackSpeed: state.playbackSpeed,
        isPlaying: state.isPlaying,
      );
      await _statePersistenceService.saveState(savedState);
    } on Exception {
      // Ignore errors
    }
  }

  /// Updates full player state with current values.
  Future<void> _updateFullState() async {
    if (_currentGroupPath == null) return;

    try {
      final savedState = await _statePersistenceService.restoreState();
      if (savedState == null || savedState.groupPath != _currentGroupPath) {
        return;
      }

      final state = await _player.getState();
      final updatedState = SavedPlayerState(
        groupPath: savedState.groupPath,
        filePaths: savedState.filePaths,
        metadata: savedState.metadata,
        currentIndex: state.currentIndex,
        currentPosition: state.currentPosition,
        playbackSpeed: state.playbackSpeed,
        isPlaying: state.isPlaying,
        repeatMode: savedState.repeatMode,
        sleepTimerRemainingSeconds: savedState.sleepTimerRemainingSeconds,
      );
      await _statePersistenceService.saveState(updatedState);
    } on Exception {
      // Ignore errors
    }
  }

  /// Restores full player state.
  ///
  /// Returns [SavedPlayerState] if available, null otherwise.
  Future<SavedPlayerState?> restoreFullState() async =>
      _statePersistenceService.restoreState();

  /// Clears saved player state.
  Future<void> clearSavedState() async => _statePersistenceService.clearState();

  /// Updates saved state with repeat mode and sleep timer.
  ///
  /// [repeatMode] is the repeat mode (0 = none, 1 = track, 2 = playlist).
  /// [sleepTimerRemainingSeconds] is the remaining seconds for sleep timer, if active.
  Future<void> updateSavedStateSettings({
    int? repeatMode,
    int? sleepTimerRemainingSeconds,
  }) async {
    if (_currentGroupPath == null) return;

    try {
      final savedState = await _statePersistenceService.restoreState();
      if (savedState == null || savedState.groupPath != _currentGroupPath) {
        return;
      }

      final state = await _player.getState();
      final updatedState = SavedPlayerState(
        groupPath: savedState.groupPath,
        filePaths: savedState.filePaths,
        metadata: savedState.metadata,
        currentIndex: state.currentIndex,
        currentPosition: state.currentPosition,
        playbackSpeed: state.playbackSpeed,
        isPlaying: state.isPlaying,
        repeatMode: repeatMode ?? savedState.repeatMode,
        sleepTimerRemainingSeconds:
            sleepTimerRemainingSeconds ?? savedState.sleepTimerRemainingSeconds,
      );
      await _statePersistenceService.saveState(updatedState);
    } on Exception {
      // Ignore errors
    }
  }

  /// Disposes resources and stops the player.
  Future<void> dispose() async {
    _positionSaveTimer?.cancel();
    _positionSaveTimer = null;

    // Save final position before disposing
    await _saveCurrentPosition();

    await _player.dispose();
    _isInitialized = false;
    _currentGroupPath = null;
  }
}
