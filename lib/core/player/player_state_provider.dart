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

import 'package:flutter/foundation.dart';
import 'package:jabook/core/di/providers/player_providers.dart';
import 'package:jabook/core/domain/library/entities/local_audiobook_group.dart';
import 'package:jabook/core/infrastructure/errors/failures.dart';
import 'package:jabook/core/player/media3_player_service.dart';
import 'package:jabook/core/player/native_audio_player.dart';
import 'package:jabook/core/player/player_state_persistence_service.dart';
import 'package:jabook/core/utils/safe_async.dart';
import 'package:riverpod/legacy.dart';

/// Player state model.
class PlayerStateModel {
  /// Creates PlayerStateModel from AudioPlayerState.
  factory PlayerStateModel.fromAudioPlayerState(AudioPlayerState state) =>
      PlayerStateModel(
        isPlaying: state.isPlaying,
        currentPosition: state.currentPosition,
        duration: state.duration,
        currentIndex: state.currentIndex,
        playbackSpeed: state.playbackSpeed,
        playbackState: state.playbackState,
        chapterNumber: state.chapterNumber,
      );

  /// Creates a new PlayerStateModel instance.
  const PlayerStateModel({
    required this.isPlaying,
    required this.currentPosition,
    required this.duration,
    required this.currentIndex,
    required this.playbackSpeed,
    required this.playbackState,
    this.error,
    this.currentTitle,
    this.currentArtist,
    this.currentCoverPath,
    this.currentGroupPath,
    this.chapterNumber,
  });

  /// Whether audio is currently playing.
  final bool isPlaying;

  /// Current playback position in milliseconds.
  final int currentPosition;

  /// Total duration in milliseconds.
  final int duration;

  /// Current track index in playlist.
  final int currentIndex;

  /// Playback speed (1.0 = normal speed).
  final double playbackSpeed;

  /// Playback state (0 = idle, 1 = buffering, 2 = ready, 3 = ended).
  final int playbackState;

  /// Error message if any.
  final String? error;

  /// Current track title.
  final String? currentTitle;

  /// Current track artist.
  final String? currentArtist;

  /// Current cover image path.
  final String? currentCoverPath;

  /// Current group path (for navigation).
  final String? currentGroupPath;

  /// Chapter number (1-based) from native player.
  /// This is the single source of truth calculated in Kotlin.
  /// If null, fallback to currentIndex + 1.
  final int? chapterNumber;

  /// Gets the current chapter number (1-based).
  /// Always uses value from native player (Kotlin) - single source of truth.
  /// Kotlin always provides chapterNumber, so this should never be null.
  int get chapterNumberValue => chapterNumber ?? 1;

  /// Creates a copy with updated fields.
  PlayerStateModel copyWith({
    bool? isPlaying,
    int? currentPosition,
    int? duration,
    int? currentIndex,
    double? playbackSpeed,
    int? playbackState,
    String? error,
    String? currentTitle,
    String? currentArtist,
    String? currentCoverPath,
    String? currentGroupPath,
    int? chapterNumber,
  }) =>
      PlayerStateModel(
        isPlaying: isPlaying ?? this.isPlaying,
        currentPosition: currentPosition ?? this.currentPosition,
        duration: duration ?? this.duration,
        currentIndex: currentIndex ?? this.currentIndex,
        playbackSpeed: playbackSpeed ?? this.playbackSpeed,
        playbackState: playbackState ?? this.playbackState,
        error: error ?? this.error,
        currentTitle: currentTitle ?? this.currentTitle,
        currentArtist: currentArtist ?? this.currentArtist,
        currentCoverPath: currentCoverPath ?? this.currentCoverPath,
        currentGroupPath: currentGroupPath ?? this.currentGroupPath,
        chapterNumber: chapterNumber ?? this.chapterNumber,
      );
}

// NOTE: media3PlayerServiceProvider is defined in lib/core/di/providers/player_providers.dart
// Import it from there instead of defining it here to avoid duplication.

/// Provider for current audiobook group being played.
final currentAudiobookGroupProvider =
    StateProvider<LocalAudiobookGroup?>((ref) => null);

/// Provider for PlayerStateNotifier.
final playerStateProvider =
    StateNotifierProvider<PlayerStateNotifier, PlayerStateModel>((ref) {
  final service = ref.watch(media3PlayerServiceProvider);
  return PlayerStateNotifier(service);
});

/// Notifier for player state management.
///
/// This notifier manages player state, handles state updates from the player stream,
/// and provides methods for controlling playback.
class PlayerStateNotifier extends StateNotifier<PlayerStateModel> {
  /// Creates a new PlayerStateNotifier instance.
  PlayerStateNotifier(this._service)
      : super(const PlayerStateModel(
          isPlaying: false,
          currentPosition: 0,
          duration: 0,
          currentIndex: 0,
          playbackSpeed: 1.0,
          playbackState: 0,
        )) {
    _subscribeToStateStream();
  }

  /// Media3PlayerService instance.
  final Media3PlayerService _service;

  /// Stream subscription for player state updates.
  StreamSubscription<AudioPlayerState>? _stateSubscription;

  /// Subscribes to player state stream.
  void _subscribeToStateStream() {
    _stateSubscription?.cancel();
    try {
      _stateSubscription = _service.stateStream.listen(
        (audioState) {
          // Preserve metadata when updating from stream
          final newState = PlayerStateModel.fromAudioPlayerState(audioState);

          // Check if track index changed - if so, update cover path from current media item
          final trackChanged = state.currentIndex != newState.currentIndex;
          final playbackStateChanged =
              state.playbackState != newState.playbackState;
          final updatedCoverPath =
              newState.currentCoverPath ?? state.currentCoverPath;

          // Update state immediately
          state = newState.copyWith(
            currentTitle: newState.currentTitle ?? state.currentTitle,
            currentArtist: newState.currentArtist ?? state.currentArtist,
            currentCoverPath: updatedCoverPath,
            currentGroupPath:
                newState.currentGroupPath ?? state.currentGroupPath,
          );

          // If track changed or playback state changed to ready (initial load), update cover path
          if (trackChanged ||
              (playbackStateChanged && newState.playbackState == 2)) {
            // playbackState 2 = ready, means player is ready and we should have artwork
            safeUnawaited(_updateCoverPathFromMediaItem());
          }
        },
        onError: (error) {
          state = state.copyWith(
            error: error is AudioFailure ? error.message : error.toString(),
          );
        },
        cancelOnError: false, // Don't cancel subscription on error
      );
    } on Object catch (e) {
      // If subscription fails, log but don't block - state will be updated
      // when player is initialized and stream becomes available
      debugPrint('Failed to subscribe to player state stream: $e');
    }
  }

  /// Updates cover path from current media item info.
  Future<void> _updateCoverPathFromMediaItem() async {
    try {
      final mediaInfo = await _service.getCurrentMediaItemInfo();
      final artworkPath = mediaInfo['artworkPath'] as String?;
      if (artworkPath != null && artworkPath.isNotEmpty) {
        state = state.copyWith(currentCoverPath: artworkPath);
      }
    } on Exception {
      // Silently fail - keep existing cover path
    }
  }

  /// Initializes the player service.
  ///
  /// Throws [AudioFailure] if initialization fails.
  Future<void> initialize() async {
    try {
      await _service.initialize();
    } on AudioFailure catch (e) {
      state = state.copyWith(error: e.message);
      rethrow;
    } on Object catch (e) {
      state = state.copyWith(error: e.toString());
      throw AudioFailure('Failed to initialize: ${e.toString()}');
    }
  }

  /// Sets playlist and starts position saving.
  ///
  /// [filePaths] is a list of absolute file paths to audio files.
  /// [metadata] is optional metadata (title, artist, album, coverPath).
  /// [groupPath] is the unique path for saving playback positions.
  /// [initialTrackIndex] is optional track index to load first (for saved position optimization).
  /// If provided, only this track is loaded synchronously, others load asynchronously for fast startup.
  ///
  /// Note: Caller should update currentAudiobookGroupProvider after calling this method
  /// to ensure all UI components (mini player, notification handler) are synchronized.
  ///
  /// Throws [AudioFailure] if setting playlist fails.
  Future<void> setPlaylist(
    List<String> filePaths, {
    Map<String, String>? metadata,
    String? groupPath,
    int? initialTrackIndex,
    int? initialPosition,
  }) async {
    try {
      await _service.setPlaylist(
        filePaths,
        metadata: metadata,
        groupPath: groupPath,
        initialTrackIndex: initialTrackIndex,
        initialPosition: initialPosition,
      );
      // Update state with metadata
      state = state.copyWith(
        currentTitle: metadata?['title'],
        currentArtist: metadata?['artist'],
        currentCoverPath: metadata?['coverPath'],
        currentGroupPath: groupPath,
      );
    } on AudioFailure catch (e) {
      state = state.copyWith(error: e.message);
      rethrow;
    } on Object catch (e) {
      state = state.copyWith(error: e.toString());
      throw AudioFailure('Failed to set playlist: ${e.toString()}');
    }
  }

  /// Starts or resumes playback.
  ///
  /// Throws [AudioFailure] if playback fails.
  Future<void> play() async {
    try {
      await _service.play();
    } on AudioFailure catch (e) {
      state = state.copyWith(error: e.message);
      rethrow;
    } on Object catch (e) {
      state = state.copyWith(error: e.toString());
      throw AudioFailure('Failed to play: ${e.toString()}');
    }
  }

  /// Pauses playback.
  ///
  /// Throws [AudioFailure] if pausing fails.
  Future<void> pause() async {
    try {
      await _service.pause();
    } on AudioFailure catch (e) {
      state = state.copyWith(error: e.message);
      rethrow;
    } on Object catch (e) {
      state = state.copyWith(error: e.toString());
      throw AudioFailure('Failed to pause: ${e.toString()}');
    }
  }

  /// Stops playback.
  ///
  /// Throws [AudioFailure] if stopping fails.
  Future<void> stop() async {
    try {
      await _service.stop();
      // Update state immediately to hide mini player
      // State stream will update playbackState to IDLE (0) shortly
      state = state.copyWith(
        isPlaying: false,
        playbackState: 0, // IDLE state to hide mini player
      );
    } on AudioFailure catch (e) {
      state = state.copyWith(error: e.message);
      rethrow;
    } on Object catch (e) {
      state = state.copyWith(error: e.toString());
      throw AudioFailure('Failed to stop: ${e.toString()}');
    }
  }

  /// Stops the audio service and exits the app.
  ///
  /// This method is used when sleep timer expires to completely stop
  /// the app and free device resources.
  ///
  /// Throws [AudioFailure] if stopping fails.
  Future<void> stopServiceAndExit() async {
    try {
      await _service.stopServiceAndExit();
    } on AudioFailure catch (e) {
      state = state.copyWith(error: e.message);
      rethrow;
    } on Object catch (e) {
      state = state.copyWith(error: e.toString());
      throw AudioFailure('Failed to stop service and exit: ${e.toString()}');
    }
  }

  /// Seeks to specific position.
  ///
  /// [position] is the position as Duration.
  ///
  /// Throws [AudioFailure] if seeking fails.
  Future<void> seek(Duration position) async {
    try {
      await _service.seek(position);

      // Update state synchronously with new position for immediate UI feedback
      final positionMs = position.inMilliseconds.clamp(0, state.duration);
      state = state.copyWith(
        currentPosition: positionMs,
      );
    } on AudioFailure catch (e) {
      state = state.copyWith(error: e.message);
      rethrow;
    } on Object catch (e) {
      state = state.copyWith(error: e.toString());
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
      await _service.setSpeed(speed);
    } on AudioFailure catch (e) {
      state = state.copyWith(error: e.message);
      rethrow;
    } on Object catch (e) {
      state = state.copyWith(error: e.toString());
      throw AudioFailure('Failed to set speed: ${e.toString()}');
    }
  }

  /// Skips to next track.
  ///
  /// Throws [AudioFailure] if skipping fails.
  Future<void> next() async {
    try {
      await _service.next();
    } on AudioFailure catch (e) {
      state = state.copyWith(error: e.message);
      rethrow;
    } on Object catch (e) {
      state = state.copyWith(error: e.toString());
      throw AudioFailure('Failed to skip next: ${e.toString()}');
    }
  }

  /// Skips to previous track.
  ///
  /// Throws [AudioFailure] if skipping fails.
  Future<void> previous() async {
    try {
      await _service.previous();
    } on AudioFailure catch (e) {
      state = state.copyWith(error: e.message);
      rethrow;
    } on Object catch (e) {
      state = state.copyWith(error: e.toString());
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
      await _service.seekToTrack(index);
    } on AudioFailure catch (e) {
      state = state.copyWith(error: e.message);
      rethrow;
    } on Object catch (e) {
      state = state.copyWith(error: e.toString());
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
      await _service.seekToTrackAndPosition(trackIndex, position);

      // Update state synchronously with new track index and position for immediate UI feedback
      final positionMs = position.inMilliseconds.clamp(0, state.duration);
      state = state.copyWith(
        currentIndex: trackIndex,
        currentPosition: positionMs,
      );
    } on AudioFailure catch (e) {
      state = state.copyWith(error: e.message);
      rethrow;
    } on Object catch (e) {
      state = state.copyWith(error: e.toString());
      throw AudioFailure(
          'Failed to seek to track and position: ${e.toString()}');
    }
  }

  /// Updates metadata for current track.
  ///
  /// [metadata] is a map with title, artist, album, coverPath, etc.
  ///
  /// Throws [AudioFailure] if update fails.
  Future<void> updateMetadata(Map<String, String> metadata) async {
    try {
      await _service.updateMetadata(metadata);
      // Update state with metadata
      state = state.copyWith(
        currentTitle: metadata['title'] ?? state.currentTitle,
        currentArtist: metadata['artist'] ?? state.currentArtist,
        currentCoverPath: metadata['coverPath'] ?? state.currentCoverPath,
      );
    } on AudioFailure catch (e) {
      state = state.copyWith(error: e.message);
      rethrow;
    } on Object catch (e) {
      state = state.copyWith(error: e.toString());
      throw AudioFailure('Failed to update metadata: ${e.toString()}');
    }
  }

  /// Restores saved playback position for a group.
  ///
  /// [groupPath] is the unique path identifying the group.
  ///
  /// Returns a map with 'trackIndex' and 'positionMs', or null if no saved position exists.
  Future<Map<String, int>?> restorePosition(String groupPath) async =>
      _service.restorePosition(groupPath);

  /// Restores full player state.
  ///
  /// Returns [SavedPlayerState] if available, null otherwise.
  Future<SavedPlayerState?> restoreFullState() async =>
      _service.restoreFullState();

  /// Updates saved state with repeat mode and sleep timer.
  ///
  /// [repeatMode] is the repeat mode (0 = none, 1 = track, 2 = playlist).
  /// [sleepTimerRemainingSeconds] is the remaining seconds for sleep timer, if active.
  Future<void> updateSavedStateSettings({
    int? repeatMode,
    int? sleepTimerRemainingSeconds,
  }) async =>
      _service.updateSavedStateSettings(
        repeatMode: repeatMode,
        sleepTimerRemainingSeconds: sleepTimerRemainingSeconds,
      );

  @override
  void dispose() {
    _stateSubscription?.cancel();
    super.dispose();
  }
}
