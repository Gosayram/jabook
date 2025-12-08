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
import 'package:flutter/services.dart';
import 'package:jabook/core/player/audio_player_bridge.dart';
import 'package:jabook/core/player/player_state_persistence_service.dart';
import 'package:jabook/core/player/player_state_stream.dart';
import 'package:riverpod/legacy.dart';

/// Simplified player state model for bridge-based architecture.
///
/// This model represents the state from the native layer via EventChannel.
class SimplePlayerState {
  /// Creates a new SimplePlayerState instance.
  const SimplePlayerState({
    required this.isPlaying,
    required this.currentPosition,
    required this.duration,
    required this.currentTrackIndex,
    required this.playbackSpeed,
    this.bufferedPosition = 0,
    this.error,
    this.currentTitle,
    this.currentArtist,
    this.currentCoverPath,
    this.playbackState = 0, // 0 = idle, 1 = buffering, 2 = ready, 3 = ended
  });

  /// Creates SimplePlayerState from a map (from EventChannel).
  factory SimplePlayerState.fromMap(Map<String, dynamic> map) =>
      SimplePlayerState(
        isPlaying: map['isPlaying'] as bool? ?? false,
        currentPosition: (map['currentPosition'] as num?)?.toInt() ?? 0,
        duration: (map['duration'] as num?)?.toInt() ?? 0,
        currentTrackIndex: (map['currentTrackIndex'] as num?)?.toInt() ?? 0,
        playbackSpeed: (map['playbackSpeed'] as num?)?.toDouble() ?? 1.0,
        bufferedPosition: (map['bufferedPosition'] as num?)?.toInt() ?? 0,
        error: map['error'] as String?,
        currentTitle: map['currentTitle'] as String?,
        currentArtist: map['currentArtist'] as String?,
        currentCoverPath: map['currentCoverPath'] as String?,
        playbackState: (map['playbackState'] as num?)?.toInt() ?? 0,
      );

  /// Whether audio is currently playing.
  final bool isPlaying;

  /// Current playback position in milliseconds.
  final int currentPosition;

  /// Total duration in milliseconds.
  final int duration;

  /// Current track index in playlist.
  final int currentTrackIndex;

  /// Playback speed (1.0 = normal speed).
  final double playbackSpeed;

  /// Buffered position in milliseconds.
  final int bufferedPosition;

  /// Error message if any.
  final String? error;

  /// Current track title.
  final String? currentTitle;

  /// Current track artist.
  final String? currentArtist;

  /// Current cover image path.
  final String? currentCoverPath;

  /// Playback state: 0 = idle, 1 = buffering, 2 = ready, 3 = ended.
  final int playbackState;

  /// Chapter number (1-based) for display.
  /// Calculated from currentTrackIndex (0-based) + 1.
  int get chapterNumberValue => currentTrackIndex + 1;

  /// Creates a copy with updated fields.
  SimplePlayerState copyWith({
    bool? isPlaying,
    int? currentPosition,
    int? duration,
    int? currentTrackIndex,
    double? playbackSpeed,
    int? bufferedPosition,
    String? error,
    String? currentTitle,
    String? currentArtist,
    String? currentCoverPath,
    int? playbackState,
  }) =>
      SimplePlayerState(
        isPlaying: isPlaying ?? this.isPlaying,
        currentPosition: currentPosition ?? this.currentPosition,
        duration: duration ?? this.duration,
        currentTrackIndex: currentTrackIndex ?? this.currentTrackIndex,
        playbackSpeed: playbackSpeed ?? this.playbackSpeed,
        bufferedPosition: bufferedPosition ?? this.bufferedPosition,
        error: error,
        currentTitle: currentTitle ?? this.currentTitle,
        currentArtist: currentArtist ?? this.currentArtist,
        currentCoverPath: currentCoverPath ?? this.currentCoverPath,
        playbackState: playbackState ?? this.playbackState,
      );
}

/// Simplified player provider using bridge architecture.
///
/// This provider uses AudioPlayerBridge for method calls and
/// PlayerStateStream for reactive state updates.
/// All business logic, including state persistence, is in the Kotlin layer.
class SimplePlayerNotifier extends StateNotifier<SimplePlayerState> {
  /// Creates a new SimplePlayerNotifier instance.
  SimplePlayerNotifier({
    required this.bridge,
    required this.stateStream,
  }) : super(const SimplePlayerState(
          isPlaying: false,
          currentPosition: 0,
          duration: 0,
          currentTrackIndex: 0,
          playbackSpeed: 1.0,
        )) {
    _subscribeToStateStream();
  }

  /// Bridge for calling native methods.
  final AudioPlayerBridge bridge;

  /// Stream for receiving state updates from native layer.
  final PlayerStateStream stateStream;

  /// Stream subscription for state updates.
  StreamSubscription<Map<String, dynamic>>? _stateSubscription;

  /// Subscribes to state stream from native layer.
  void _subscribeToStateStream() {
    _stateSubscription?.cancel();
    try {
      _stateSubscription = stateStream.listen().listen(
        (event) {
          final newState = SimplePlayerState.fromMap(event);
          // Clear error if player is ready or playing (means it recovered from error)
          // Only clear if new state doesn't have error (native layer cleared it)
          final shouldClearError = (newState.playbackState == 2 || // 2 = ready
                  newState.isPlaying) &&
              newState.error == null &&
              state.error != null;
          state = shouldClearError ? newState.copyWith() : newState;
        },
        onError: (error) {
          state = state.copyWith(
            error: error is PlatformException
                ? error.message ?? error.code
                : error.toString(),
          );
        },
        cancelOnError: false,
      );
    } on Object catch (e) {
      debugPrint('Failed to subscribe to player state stream: $e');
    }
  }

  /// Plays or resumes playback.
  ///
  /// This is a thin wrapper that calls the native layer.
  /// All business logic is in Kotlin.
  Future<void> play() async {
    try {
      await bridge.play();
    } on PlatformException catch (e) {
      state = state.copyWith(error: e.message ?? e.code);
      rethrow;
    } on Object catch (e) {
      state = state.copyWith(error: e.toString());
      rethrow;
    }
  }

  /// Pauses playback.
  ///
  /// This is a thin wrapper that calls the native layer.
  Future<void> pause() async {
    try {
      await bridge.pause();
    } on PlatformException catch (e) {
      state = state.copyWith(error: e.message ?? e.code);
      rethrow;
    } on Object catch (e) {
      state = state.copyWith(error: e.toString());
      rethrow;
    }
  }

  /// Seeks to a specific position.
  ///
  /// [positionMs] is the position in milliseconds.
  Future<void> seek(int positionMs) async {
    try {
      await bridge.seek(positionMs);
    } on PlatformException catch (e) {
      state = state.copyWith(error: e.message ?? e.code);
      rethrow;
    } on Object catch (e) {
      state = state.copyWith(error: e.toString());
      rethrow;
    }
  }

  /// Seeks to a specific position using Duration.
  ///
  /// [position] is the position as Duration.
  /// This is a convenience method that converts Duration to milliseconds.
  Future<void> seekDuration(Duration position) async {
    await seek(position.inMilliseconds);
  }

  /// Sets playback speed.
  ///
  /// [speed] is the playback speed (0.5 to 2.0).
  Future<void> setSpeed(double speed) async {
    try {
      await bridge.setSpeed(speed);
    } on PlatformException catch (e) {
      state = state.copyWith(error: e.message ?? e.code);
      rethrow;
    } on Object catch (e) {
      state = state.copyWith(error: e.toString());
      rethrow;
    }
  }

  /// Skips to next track.
  Future<void> next() async {
    try {
      await bridge.next();
    } on PlatformException catch (e) {
      state = state.copyWith(error: e.message ?? e.code);
      rethrow;
    } on Object catch (e) {
      state = state.copyWith(error: e.toString());
      rethrow;
    }
  }

  /// Skips to previous track.
  Future<void> previous() async {
    try {
      await bridge.previous();
    } on PlatformException catch (e) {
      state = state.copyWith(error: e.message ?? e.code);
      rethrow;
    } on Object catch (e) {
      state = state.copyWith(error: e.toString());
      rethrow;
    }
  }

  /// Stops playback.
  ///
  /// This is a thin wrapper that calls the native layer.
  Future<void> stop() async {
    try {
      await bridge.stop();
      // Update state immediately to hide mini player
      state = state.copyWith(
        isPlaying: false,
        playbackState: 0, // IDLE state
      );
    } on PlatformException catch (e) {
      state = state.copyWith(error: e.message ?? e.code);
      rethrow;
    } on Object catch (e) {
      state = state.copyWith(error: e.toString());
      rethrow;
    }
  }

  /// Initializes the audio player service.
  ///
  /// This is a thin wrapper that calls the native layer.
  Future<void> initialize() async {
    try {
      await bridge.initialize();
    } on PlatformException catch (e) {
      state = state.copyWith(error: e.message ?? e.code);
      rethrow;
    } on Object catch (e) {
      state = state.copyWith(error: e.toString());
      rethrow;
    }
  }

  /// Sets playlist from file paths.
  ///
  /// [filePaths] is a list of absolute file paths or HTTP(S) URLs to audio files.
  /// [metadata] is optional metadata (title, artist, album, artworkUri).
  /// [initialTrackIndex] is optional track index to load first.
  /// [initialPosition] is optional position in milliseconds to seek to after loading initial track.
  /// [groupPath] is optional group path for saving playback position.
  Future<void> setPlaylist({
    required List<String> filePaths,
    Map<String, String>? metadata,
    int? initialTrackIndex,
    int? initialPosition,
    String? groupPath,
  }) async {
    try {
      await bridge.setPlaylist(
        filePaths: filePaths,
        metadata: metadata,
        initialTrackIndex: initialTrackIndex,
        initialPosition: initialPosition,
        groupPath: groupPath,
      );
    } on PlatformException catch (e) {
      state = state.copyWith(error: e.message ?? e.code);
      rethrow;
    } on Object catch (e) {
      state = state.copyWith(error: e.toString());
      rethrow;
    }
  }

  /// Restores playback position for a group path.
  ///
  /// Returns a map with trackIndex and positionMs, or null if no saved position exists.
  Future<Map<String, int>?> restorePosition(
    String groupPath, {
    int? fileCount,
  }) async {
    try {
      return await bridge.restorePosition(groupPath, fileCount: fileCount);
    } on PlatformException catch (e) {
      state = state.copyWith(error: e.message ?? e.code);
      rethrow;
    } on Object catch (e) {
      state = state.copyWith(error: e.toString());
      rethrow;
    }
  }

  /// Seeks to a specific track.
  ///
  /// [trackIndex] is the track index to seek to.
  Future<void> seekToTrack(int trackIndex) async {
    try {
      await bridge.seekToTrack(trackIndex);
    } on PlatformException catch (e) {
      state = state.copyWith(error: e.message ?? e.code);
      rethrow;
    } on Object catch (e) {
      state = state.copyWith(error: e.toString());
      rethrow;
    }
  }

  /// Seeks to a specific track and position.
  ///
  /// [trackIndex] is the track index to seek to.
  /// [position] is the position as Duration.
  Future<void> seekToTrackAndPosition(int trackIndex, Duration position) async {
    try {
      await bridge.seekToTrack(trackIndex);
      await seekDuration(position);
    } on PlatformException catch (e) {
      state = state.copyWith(error: e.message ?? e.code);
      rethrow;
    } on Object catch (e) {
      state = state.copyWith(error: e.toString());
      rethrow;
    }
  }

  /// Updates playback metadata.
  ///
  /// [metadata] is a map with metadata (title, artist, album, artworkUri).
  Future<void> updateMetadata(Map<String, String> metadata) async {
    try {
      await bridge.updateMetadata(metadata);
    } on PlatformException catch (e) {
      state = state.copyWith(error: e.message ?? e.code);
      rethrow;
    } on Object catch (e) {
      state = state.copyWith(error: e.toString());
      rethrow;
    }
  }

  /// Restores full player state.
  ///
  /// Returns SavedPlayerState if available, null otherwise.
  /// Uses the new bridge architecture with Kotlin layer persistence.
  Future<SavedPlayerState?> restoreFullState({String? groupPath}) async {
    try {
      final result = await bridge.restoreFullState(groupPath: groupPath);
      if (result == null) {
        return null;
      }
      return SavedPlayerState.fromJson(result);
    } on PlatformException catch (e) {
      state = state.copyWith(error: e.message ?? e.code);
      rethrow;
    } on Object catch (e) {
      state = state.copyWith(error: e.toString());
      rethrow;
    }
  }

  /// Updates saved state with repeat mode and sleep timer.
  ///
  /// [groupPath] is required.
  /// [repeatMode] is the repeat mode (0 = none, 1 = track, 2 = playlist).
  /// [sleepTimerRemainingSeconds] is the remaining seconds for sleep timer, if active.
  /// Uses the new bridge architecture with Kotlin layer persistence.
  Future<void> updateSavedStateSettings({
    required String groupPath,
    int? repeatMode,
    int? sleepTimerRemainingSeconds,
  }) async {
    try {
      await bridge.updateSavedStateSettings(
        groupPath: groupPath,
        repeatMode: repeatMode,
        sleepTimerRemainingSeconds: sleepTimerRemainingSeconds,
      );
    } on PlatformException catch (e) {
      state = state.copyWith(error: e.message ?? e.code);
      rethrow;
    } on Object catch (e) {
      state = state.copyWith(error: e.toString());
      rethrow;
    }
  }

  /// Stops the audio service and exits the app.
  ///
  /// This is typically used when sleep timer expires.
  /// Uses the new bridge architecture.
  Future<void> stopServiceAndExit() async {
    try {
      await bridge.stopServiceAndExit();
    } on PlatformException catch (e) {
      state = state.copyWith(error: e.message ?? e.code);
      rethrow;
    } on Object catch (e) {
      state = state.copyWith(error: e.toString());
      rethrow;
    }
  }

  @override
  void dispose() {
    _stateSubscription?.cancel();
    super.dispose();
  }
}
