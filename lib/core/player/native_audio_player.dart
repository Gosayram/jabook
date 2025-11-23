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

  /// Maximum number of retry attempts for initialization.
  static const int _maxInitRetries = 3;

  /// Base delay for exponential backoff retry (in milliseconds).
  static const int _baseRetryDelayMs = 500;

  /// Checks if error is retryable (SERVICE_UNAVAILABLE or similar).
  bool _isRetryableError(PlatformException e) => e.code == 'SERVICE_UNAVAILABLE' ||
        (e.message?.contains('not ready') ?? false) ||
        (e.message?.contains('not initialized') ?? false);

  /// Executes a method with retry logic for SERVICE_UNAVAILABLE errors.
  ///
  /// Uses exponential backoff: 500ms, 1000ms, 2000ms.
  Future<T> _invokeWithRetry<T>(
    String method, {
    dynamic arguments,
    int maxRetries = _maxInitRetries,
  }) async {
    var attempt = 0;
    Exception? lastException;

    while (attempt < maxRetries) {
      try {
        T? result;
        if (arguments != null) {
          result = await _channel.invokeMethod<T>(method, arguments);
        } else {
          result = await _channel.invokeMethod<T>(method);
        }

        // For void methods, result can be null - that's OK
        // For non-void methods, check if result is not null
        if (result != null) {
          return result;
        }

        // If we expect a non-null result but got null, check if it's a void method
        // For void methods (like setPlaylist, play), null is acceptable
        // For bool methods (like initialize), null means failure
        if (T == bool) {
          // For bool methods, null means false
          return false as T;
        }

        // For void methods, return null as T (which will be cast to void)
        return result as T;
      } on PlatformException catch (e) {
        lastException = e;

        // Check if error is retryable
        if (!_isRetryableError(e)) {
          // Not retryable, throw immediately
          rethrow;
        }

        attempt++;
        if (attempt >= maxRetries) {
          // Max retries reached
          await _logger.log(
            level: 'warning',
            subsystem: 'audio',
            message: 'Max retries reached for $method',
            cause: e.toString(),
            extra: {
              'code': e.code,
              'message': e.message,
              'attempts': attempt,
            },
          );
          rethrow;
        }

        // Calculate exponential backoff delay
        final delayMs = _baseRetryDelayMs * (1 << (attempt - 1));
        await _logger.log(
          level: 'info',
          subsystem: 'audio',
          message: 'Retrying $method after SERVICE_UNAVAILABLE error',
          extra: {
            'code': e.code,
            'message': e.message,
            'attempt': attempt,
            'max_retries': maxRetries,
            'delay_ms': delayMs,
          },
        );

        // Wait before retry
        await Future.delayed(Duration(milliseconds: delayMs));
      } on Exception catch (e) {
        // Non-PlatformException errors are not retryable
        lastException = e;
        rethrow;
      }
    }

    // Should not reach here, but handle just in case
    throw AudioFailure(
      'Failed after $maxRetries attempts: ${lastException?.toString() ?? 'Unknown error'}',
    );
  }

  /// Initializes the native audio player with retry logic.
  ///
  /// Uses exponential backoff retry for SERVICE_UNAVAILABLE errors.
  /// Throws [AudioFailure] if initialization fails after all retries.
  Future<void> initialize() async {
    if (_isInitialized) return;

    try {
      await _logger.log(
        level: 'info',
        subsystem: 'audio',
        message: 'Initializing native audio player',
      );

      // Use retry mechanism for initialization
      final result = await _invokeWithRetry<bool>(
        'initialize',
      ).timeout(
        const Duration(seconds: 15),
        onTimeout: () {
          throw TimeoutException(
            'Player initialization timed out after 15 seconds',
            const Duration(seconds: 15),
          );
        },
      );

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
    } on TimeoutException catch (e) {
      await _logger.log(
        level: 'warning',
        subsystem: 'audio',
        message:
            'Player initialization timed out - will initialize on first use',
        cause: e.toString(),
      );
      throw AudioFailure('Initialization timed out: ${e.message}');
    } on PlatformException catch (e) {
      // Log as warning instead of error - player will initialize on first use
      await _logger.log(
        level: 'warning',
        subsystem: 'audio',
        message:
            'Platform error during initialization - will initialize on first use',
        cause: e.toString(),
        extra: {
          'code': e.code,
          'message': e.message,
        },
      );
      // Don't set _isInitialized = true, so it will retry on first use
      throw AudioFailure('Failed to initialize: ${e.message ?? e.code}');
    } on Exception catch (e) {
      // Log as warning instead of error - player will initialize on first use
      await _logger.log(
        level: 'warning',
        subsystem: 'audio',
        message:
            'Failed to initialize native audio player - will initialize on first use',
        cause: e.toString(),
      );
      // Don't set _isInitialized = true, so it will retry on first use
      throw AudioFailure('Failed to initialize: ${e.toString()}');
    }
  }

  /// Sets playlist from file paths or URLs.
  ///
  /// [filePaths] is a list of absolute file paths or HTTP(S) URLs to audio files.
  /// Supports both local files and network streaming.
  /// [metadata] is optional metadata (title, artist, album, artworkUri).
  ///
  /// Throws [AudioFailure] if setting playlist fails.
  /// Uses retry logic for SERVICE_UNAVAILABLE errors.
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

      // Use retry mechanism for setPlaylist
      await _invokeWithRetry<void>(
        'setPlaylist',
        arguments: {
          'filePaths': filePaths,
          'metadata': metadata,
        },
        maxRetries: 2, // Fewer retries for setPlaylist
      ).timeout(
        const Duration(seconds: 30),
        onTimeout: () {
          throw TimeoutException(
            'setPlaylist timed out after 30 seconds',
            const Duration(seconds: 30),
          );
        },
      );

      // Get player state after setting playlist to log details
      try {
        final stateMap =
            await _channel.invokeMethod<Map<dynamic, dynamic>>('getState');
        if (stateMap != null) {
          await _logger.log(
            level: 'info',
            subsystem: 'audio',
            message: 'Playlist set successfully',
            extra: {
              'playbackState': stateMap['playbackState'],
              'playWhenReady': stateMap['playWhenReady'],
              'isPlaying': stateMap['isPlaying'],
              'mediaItemCount': stateMap['mediaItemCount'],
              'currentIndex': stateMap['currentIndex'],
            },
          );
        } else {
          await _logger.log(
            level: 'info',
            subsystem: 'audio',
            message: 'Playlist set successfully',
          );
        }
      } on Exception {
        // If getState fails, just log success without details
        await _logger.log(
          level: 'info',
          subsystem: 'audio',
          message: 'Playlist set successfully',
        );
      }
    } on TimeoutException catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'audio',
        message: 'setPlaylist timed out',
        cause: e.toString(),
      );
      throw AudioFailure('setPlaylist timed out: ${e.message}');
    } on PlatformException catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'audio',
        message: 'Platform error setting playlist',
        cause: e.toString(),
        extra: {
          'code': e.code,
          'message': e.message,
        },
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
      await _logger.log(
        level: 'info',
        subsystem: 'audio',
        message: 'Invoking play() method on native side',
      );
      await _channel.invokeMethod('play');
      await _logger.log(
        level: 'info',
        subsystem: 'audio',
        message: 'play() method invocation completed',
      );
    } on PlatformException catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'audio',
        message: 'Platform error calling play()',
        cause: e.toString(),
        extra: {'code': e.code, 'message': e.message},
      );
      throw AudioFailure('Failed to play: ${e.message ?? e.code}');
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'audio',
        message: 'Exception calling play()',
        cause: e.toString(),
      );
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
