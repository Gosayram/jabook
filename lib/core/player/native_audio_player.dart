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

import 'package:flutter/services.dart';
import 'package:jabook/core/errors/failures.dart';
import 'package:jabook/core/logging/structured_logger.dart';

/// Native audio player state.
class AudioPlayerState {
  /// Creates a new [AudioPlayerState] instance.
  const AudioPlayerState({
    required this.isPlaying,
    required this.currentPosition,
    required this.duration,
    required this.currentIndex,
    required this.playbackSpeed,
    required this.playbackState,
  });

  /// Creates [AudioPlayerState] from a map.
  ///
  /// Used to deserialize state from native platform channel.
  factory AudioPlayerState.fromMap(Map<dynamic, dynamic> map) =>
      AudioPlayerState(
        isPlaying: map['isPlaying'] as bool? ?? false,
        currentPosition: (map['currentPosition'] as num?)?.toInt() ?? 0,
        duration: (map['duration'] as num?)?.toInt() ?? 0,
        currentIndex: (map['currentIndex'] as num?)?.toInt() ?? 0,
        playbackSpeed: (map['playbackSpeed'] as num?)?.toDouble() ?? 1.0,
        playbackState: (map['playbackState'] as num?)?.toInt() ?? 0,
      );

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
}

/// Native audio player using Media3 ExoPlayer.
///
/// This class provides a Dart interface to the native Android audio player
/// implemented in Kotlin using Media3 ExoPlayer.
///
/// It communicates with the native layer through MethodChannel and provides
/// a stream of player state updates for reactive UI updates.
class NativeAudioPlayer {
  /// MethodChannel for communication with native code.
  static const MethodChannel _channel = MethodChannel(
    'com.jabook.app.jabook/audio_player',
  );

  /// Logger for structured logging.
  final StructuredLogger _logger = StructuredLogger();

  /// Stream controller for player state updates.
  final StreamController<AudioPlayerState> _stateController =
      StreamController<AudioPlayerState>.broadcast();

  /// Stream of player state updates.
  Stream<AudioPlayerState> get stateStream => _stateController.stream;

  /// Timer for periodic state updates.
  Timer? _stateUpdateTimer;

  /// Whether player is initialized.
  bool _isInitialized = false;

  /// Initializes the native audio player.
  ///
  /// Throws [AudioFailure] if initialization fails.
  Future<void> initialize() async {
    if (_isInitialized) return;

    try {
      await _logger.log(
        level: 'info',
        subsystem: 'audio',
        message: 'Initializing native audio player',
      );

      final result = await _channel.invokeMethod<bool>('initialize');
      if (result != true) {
        throw const AudioFailure('Failed to initialize native audio player');
      }

      _isInitialized = true;
      _startStateUpdates();

      await _logger.log(
        level: 'info',
        subsystem: 'audio',
        message: 'Native audio player initialized successfully',
      );
    } on PlatformException catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'audio',
        message: 'Platform error during initialization',
        cause: e.toString(),
        extra: {
          'code': e.code,
          'message': e.message,
        },
      );
      throw AudioFailure('Failed to initialize: ${e.message ?? e.code}');
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'audio',
        message: 'Failed to initialize native audio player',
        cause: e.toString(),
      );
      throw AudioFailure('Failed to initialize: ${e.toString()}');
    }
  }

  /// Sets playlist from file paths.
  ///
  /// [filePaths] is a list of absolute file paths to audio files.
  /// [metadata] is optional metadata (title, artist, album).
  ///
  /// Throws [AudioFailure] if setting playlist fails.
  Future<void> setPlaylist(
    List<String> filePaths, {
    Map<String, String>? metadata,
  }) async {
    if (!_isInitialized) {
      await initialize();
    }

    try {
      await _logger.log(
        level: 'info',
        subsystem: 'audio',
        message: 'Setting playlist',
        extra: {'file_count': filePaths.length},
      );

      await _channel.invokeMethod('setPlaylist', {
        'filePaths': filePaths,
        'metadata': metadata,
      });

      await _logger.log(
        level: 'info',
        subsystem: 'audio',
        message: 'Playlist set successfully',
      );
    } on PlatformException catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'audio',
        message: 'Platform error setting playlist',
        cause: e.toString(),
      );
      throw AudioFailure('Failed to set playlist: ${e.message ?? e.code}');
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
      await _channel.invokeMethod('play');
    } on PlatformException catch (e) {
      throw AudioFailure('Failed to play: ${e.message ?? e.code}');
    } on Exception catch (e) {
      throw AudioFailure('Failed to play: ${e.toString()}');
    }
  }

  /// Pauses playback.
  ///
  /// Throws [AudioFailure] if pausing fails.
  Future<void> pause() async {
    try {
      await _channel.invokeMethod('pause');
    } on PlatformException catch (e) {
      throw AudioFailure('Failed to pause: ${e.message ?? e.code}');
    } on Exception catch (e) {
      throw AudioFailure('Failed to pause: ${e.toString()}');
    }
  }

  /// Stops playback.
  ///
  /// Throws [AudioFailure] if stopping fails.
  Future<void> stop() async {
    try {
      await _channel.invokeMethod('stop');
    } on PlatformException catch (e) {
      throw AudioFailure('Failed to stop: ${e.message ?? e.code}');
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
      // Validate position: must be non-negative
      if (position.isNegative) {
        throw const AudioFailure('Position cannot be negative');
      }

      // Convert to int64 explicitly for proper type handling across platform channel
      final positionMs = position.inMilliseconds;
      if (positionMs < 0) {
        throw const AudioFailure('Position in milliseconds cannot be negative');
      }

      await _channel.invokeMethod('seek', {
        'positionMs': positionMs,
      });
    } on PlatformException catch (e) {
      throw AudioFailure('Failed to seek: ${e.message ?? e.code}');
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
      await _channel.invokeMethod('setSpeed', {'speed': speed});
    } on PlatformException catch (e) {
      throw AudioFailure('Failed to set speed: ${e.message ?? e.code}');
    } on Exception catch (e) {
      throw AudioFailure('Failed to set speed: ${e.toString()}');
    }
  }

  /// Gets current playback position.
  ///
  /// Returns current position in milliseconds.
  Future<int> getPosition() async {
    try {
      final position = await _channel.invokeMethod<int>('getPosition');
      return position ?? 0;
    } on PlatformException catch (e) {
      throw AudioFailure('Failed to get position: ${e.message ?? e.code}');
    } on Exception catch (e) {
      throw AudioFailure('Failed to get position: ${e.toString()}');
    }
  }

  /// Gets total duration.
  ///
  /// Returns duration in milliseconds.
  Future<int> getDuration() async {
    try {
      final duration = await _channel.invokeMethod<int>('getDuration');
      return duration ?? 0;
    } on PlatformException catch (e) {
      throw AudioFailure('Failed to get duration: ${e.message ?? e.code}');
    } on Exception catch (e) {
      throw AudioFailure('Failed to get duration: ${e.toString()}');
    }
  }

  /// Gets current player state.
  ///
  /// Returns [AudioPlayerState] with current player information.
  Future<AudioPlayerState> getState() async {
    try {
      final stateMap = await _channel.invokeMethod<Map<dynamic, dynamic>>(
        'getState',
      );
      if (stateMap == null) {
        return const AudioPlayerState(
          isPlaying: false,
          currentPosition: 0,
          duration: 0,
          currentIndex: 0,
          playbackSpeed: 1.0,
          playbackState: 0,
        );
      }
      return AudioPlayerState.fromMap(stateMap);
    } on PlatformException catch (e) {
      throw AudioFailure('Failed to get state: ${e.message ?? e.code}');
    } on Exception catch (e) {
      throw AudioFailure('Failed to get state: ${e.toString()}');
    }
  }

  /// Skips to next track.
  Future<void> next() async {
    try {
      await _channel.invokeMethod('next');
    } on PlatformException catch (e) {
      throw AudioFailure('Failed to skip next: ${e.message ?? e.code}');
    } on Exception catch (e) {
      throw AudioFailure('Failed to skip next: ${e.toString()}');
    }
  }

  /// Skips to previous track.
  Future<void> previous() async {
    try {
      await _channel.invokeMethod('previous');
    } on PlatformException catch (e) {
      throw AudioFailure('Failed to skip previous: ${e.message ?? e.code}');
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
      await _channel.invokeMethod('seekToTrack', {'index': index});
    } on PlatformException catch (e) {
      throw AudioFailure('Failed to seek to track: ${e.message ?? e.code}');
    } on Exception catch (e) {
      throw AudioFailure('Failed to seek to track: ${e.toString()}');
    }
  }

  /// Updates metadata for current track.
  ///
  /// [metadata] is a map with title, artist, album, etc.
  ///
  /// Throws [AudioFailure] if update fails.
  Future<void> updateMetadata(Map<String, String> metadata) async {
    try {
      await _channel.invokeMethod('updateMetadata', {'metadata': metadata});
    } on PlatformException catch (e) {
      throw AudioFailure('Failed to update metadata: ${e.message ?? e.code}');
    } on Exception catch (e) {
      throw AudioFailure('Failed to update metadata: ${e.toString()}');
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
      // Validate track index
      if (trackIndex < 0) {
        throw const AudioFailure('Track index cannot be negative');
      }

      // Validate position: must be non-negative
      if (position.isNegative) {
        throw const AudioFailure('Position cannot be negative');
      }

      // Convert to int64 explicitly for proper type handling across platform channel
      final positionMs = position.inMilliseconds;
      if (positionMs < 0) {
        throw const AudioFailure('Position in milliseconds cannot be negative');
      }

      await _channel.invokeMethod('seekToTrackAndPosition', {
        'trackIndex': trackIndex,
        'positionMs': positionMs,
      });
    } on PlatformException catch (e) {
      throw AudioFailure(
        'Failed to seek to track and position: ${e.message ?? e.code}',
      );
    } on Exception catch (e) {
      throw AudioFailure(
          'Failed to seek to track and position: ${e.toString()}');
    }
  }

  /// Starts periodic state updates.
  void _startStateUpdates() {
    _stateUpdateTimer?.cancel();
    _stateUpdateTimer = Timer.periodic(
      const Duration(milliseconds: 500),
      (_) async {
        try {
          final state = await getState();
          _stateController.add(state);
        } on Exception {
          // Ignore errors in state updates
        }
      },
    );
  }

  /// Disposes resources and stops the native player.
  Future<void> dispose() async {
    _stateUpdateTimer?.cancel();
    _stateUpdateTimer = null;

    try {
      await _channel.invokeMethod('dispose');
      _isInitialized = false;
    } on Exception {
      // Ignore errors during dispose
    }

    await _stateController.close();
  }
}
