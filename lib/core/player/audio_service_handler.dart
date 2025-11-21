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

import 'package:audio_service/audio_service.dart';
import 'package:audio_session/audio_session.dart';
import 'package:flutter/services.dart';
import 'package:jabook/core/errors/failures.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:just_audio/just_audio.dart';
import 'package:rxdart/rxdart.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Handles audio service operations and playback state management.
///
/// This class manages the audio service lifecycle, handles media playback,
/// and maintains the playback state for background audio playback.
class AudioServiceHandler {
  /// Internal audio player instance for media playback.
  final AudioPlayer _audioPlayer = AudioPlayer();
  double _preDuckingVolume = 1.0;
  bool _isDucked = false;
  AudioPlayerHandler? _playerHandler;
  String? _currentMediaId;

  /// Stream controller for playback state updates.
  final BehaviorSubject<PlaybackState> _playbackState = BehaviorSubject();

  /// Gets the stream of playback state updates.
  ///
  /// UI layers can listen to this stream to react to changes in playback state.
  Stream<PlaybackState> get playbackState => _playbackState.stream;

  /// Initializes and starts the audio service.
  ///
  /// Sets up the audio service, initializes the player, and registers listeners.
  ///
  /// Throws [AudioFailure] if the service cannot be started.
  Future<void> startService() async {
    final logger = StructuredLogger();
    final operationId =
        'start_service_${DateTime.now().millisecondsSinceEpoch}';

    try {
      await logger.log(
        level: 'info',
        subsystem: 'audio',
        message: 'Starting audio service',
        operationId: operationId,
      );

      // Initialize audio service with basic configuration
      // AudioService.init() will handle the case if service is already running
      await logger.log(
        level: 'debug',
        subsystem: 'audio',
        message: 'Initializing AudioService',
        operationId: operationId,
      );

      final handler = await AudioService.init(
        builder: () => AudioPlayerHandler(_audioPlayer),
        config: const AudioServiceConfig(
          androidNotificationChannelId: 'com.jabook.app',
          androidNotificationChannelName: 'JaBook Audio',
          androidNotificationChannelDescription: 'JaBook Audiobook Player',
          androidNotificationOngoing: true,
        ),
      );

      _playerHandler = handler;

      await logger.log(
        level: 'info',
        subsystem: 'audio',
        message: 'AudioService initialized successfully',
        operationId: operationId,
      );

      // Set up audio player event listener
      _setupAudioPlayer();

      await logger.log(
        level: 'debug',
        subsystem: 'audio',
        message: 'Setting up audio focus',
        operationId: operationId,
      );

      // Handle audio focus policies
      await _setupAudioFocus();

      await logger.log(
        level: 'info',
        subsystem: 'audio',
        message: 'Audio service started successfully',
        operationId: operationId,
      );
    } on PlatformException catch (e) {
      await logger.log(
        level: 'error',
        subsystem: 'audio',
        message: 'Platform error during audio service initialization',
        operationId: operationId,
        cause: e.toString(),
        extra: {
          'code': e.code,
          'message': e.message,
          'details': e.details?.toString(),
        },
      );
      throw AudioFailure(
        'Failed to start audio service: ${e.message ?? e.code}',
      );
    } on Exception catch (e) {
      await logger.log(
        level: 'error',
        subsystem: 'audio',
        message: 'Failed to start audio service',
        operationId: operationId,
        cause: e.toString(),
        extra: {'error_type': e.runtimeType.toString()},
      );
      throw AudioFailure('Failed to start audio service: ${e.toString()}');
    }
  }

  /// Registers a listener for player events and emits [PlaybackState] updates.
  void _setupAudioPlayer() {
    _audioPlayer.playbackEventStream.listen((event) {
      final state = PlaybackState(
        processingState: _getProcessingState(),
        updatePosition: _audioPlayer.position,
        bufferedPosition: _audioPlayer.bufferedPosition,
        speed: _audioPlayer.speed,
        playing: _audioPlayer.playing,
      );
      _playbackState.add(state);
      // Also update system playback state for notifications/lockscreen
      try {
        // Map just_audio ProcessingState to audio_service AudioProcessingState
        final systemState = AudioProcessingState.values.firstWhere(
          (s) => s == state.processingState,
          orElse: () => AudioProcessingState.idle,
        );
        final controls = <MediaControl>[
          MediaControl.rewind,
          if (_audioPlayer.playing) MediaControl.pause else MediaControl.play,
          MediaControl.fastForward,
          MediaControl.stop,
        ];
        final systemPlayback = PlaybackState(
          controls: controls,
          systemActions: const {
            MediaAction.seek,
            MediaAction.seekForward,
            MediaAction.seekBackward
          },
          androidCompactActionIndices: const [0, 1, 2],
          processingState: systemState,
          playing: _audioPlayer.playing,
          updatePosition: _audioPlayer.position,
          bufferedPosition: _audioPlayer.bufferedPosition,
          speed: _audioPlayer.speed,
        );
        // This add works inside handler; from here we can downcast
        _playerHandler?.playbackState.add(systemPlayback);
        // Persist playback position periodically
        _persistPosition();
      } on Object {
        // ignore errors in system state update
      }
    });
  }

  /// Maps the just_audio processing state to [AudioProcessingState].
  AudioProcessingState _getProcessingState() {
    if (_audioPlayer.playing) {
      return AudioProcessingState.ready;
    } else if (_audioPlayer.processingState == ProcessingState.loading) {
      return AudioProcessingState.loading;
    } else if (_audioPlayer.processingState == ProcessingState.buffering) {
      return AudioProcessingState.buffering;
    } else {
      return AudioProcessingState.idle;
    }
  }

  /// Sets up audio focus management (interruptions, ducking, etc).
  Future<void> _setupAudioFocus() async {
    final session = await AudioSession.instance;
    // Speech profile is a good default for audiobooks: allows ducking, respects interruptions
    await session.configure(const AudioSessionConfiguration.speech());

    // Listen to audio interruptions (phone calls, other apps taking focus, etc.)
    session.interruptionEventStream.listen((event) async {
      final t = event.type;
      final begin = event.begin;

      if (t == AudioInterruptionType.duck) {
        if (begin && !_isDucked) {
          _preDuckingVolume = _audioPlayer.volume;
          _isDucked = true;
          await _audioPlayer
              .setVolume((_preDuckingVolume * 0.2).clamp(0.0, 1.0));
        } else if (!begin && _isDucked) {
          _isDucked = false;
          await _audioPlayer.setVolume(_preDuckingVolume);
        }
        return;
      }

      if (t == AudioInterruptionType.pause) {
        if (begin) {
          if (_audioPlayer.playing) {
            await _audioPlayer.pause();
          }
        } else {
          // Do not auto-resume; leave it to UI/user action
        }
        return;
      }

      // Unknown: safest is to pause
      if (t == AudioInterruptionType.unknown && begin) {
        if (_audioPlayer.playing) {
          await _audioPlayer.pause();
        }
      }
    });

    // Handle becoming noisy (e.g., unplugging headphones)
    session.becomingNoisyEventStream.listen((_) async {
      if (_audioPlayer.playing) {
        await _audioPlayer.pause();
      }
    });
  }

  /// Starts playback for the provided media URL.
  ///
  /// Throws [AudioFailure] if playback cannot be started.
  Future<void> playMedia(String url, {MediaItem? metadata}) async {
    try {
      if (metadata != null) {
        _playerHandler?.setNowPlayingItem(metadata);
        _currentMediaId = metadata.id;
        // Try restore last position
        await _restorePosition();
      }
      await _audioPlayer.setUrl(url);
      await _audioPlayer.play();
    } on Exception {
      throw const AudioFailure('Failed to play media');
    }
  }

  /// Starts playback for a playlist of local file paths.
  ///
  /// The [filePaths] parameter is a list of local file paths to play.
  /// The [metadata] parameter is optional metadata for the first track.
  ///
  /// Throws [AudioFailure] if playback cannot be started.
  Future<void> playLocalPlaylist(
    List<String> filePaths, {
    MediaItem? metadata,
  }) async {
    final logger = StructuredLogger();
    final operationId = 'play_local_${DateTime.now().millisecondsSinceEpoch}';

    try {
      if (filePaths.isEmpty) {
        await logger.log(
          level: 'error',
          subsystem: 'audio',
          message: 'No files to play',
          operationId: operationId,
        );
        throw const AudioFailure('No files to play');
      }

      // Create audio sources with proper error handling
      final audioSources = await _createAudioSources(filePaths, operationId);

      if (audioSources.isEmpty) {
        await logger.log(
          level: 'error',
          subsystem: 'audio',
          message: 'No valid audio sources created',
          operationId: operationId,
          extra: {'file_count': filePaths.length},
        );
        throw const AudioFailure('No valid audio files found');
      }

      await logger.log(
        level: 'info',
        subsystem: 'audio',
        message: 'Setting audio sources',
        operationId: operationId,
        extra: {'sources_count': audioSources.length},
      );

      // Set the playlist
      await _audioPlayer.setAudioSources(audioSources);

      if (metadata != null) {
        _playerHandler?.setNowPlayingItem(metadata);
        _currentMediaId = metadata.id;
        // Try restore last position
        await _restorePosition();
      }

      // Wait for player to be ready
      await _waitForPlayerReady(operationId);

      await logger.log(
        level: 'info',
        subsystem: 'audio',
        message: 'Starting playback',
        operationId: operationId,
      );

      // Start playback
      await _audioPlayer.play();

      await logger.log(
        level: 'info',
        subsystem: 'audio',
        message: 'Playback started successfully',
        operationId: operationId,
      );
    } on PlatformException catch (e) {
      await logger.log(
        level: 'error',
        subsystem: 'audio',
        message: 'Platform error during playback',
        operationId: operationId,
        cause: e.toString(),
        extra: {
          'code': e.code,
          'message': e.message,
          'details': e.details?.toString(),
        },
      );
      throw AudioFailure('Platform error: ${e.message ?? e.code}');
    } on AudioFailure {
      rethrow;
    } on Exception catch (e) {
      await logger.log(
        level: 'error',
        subsystem: 'audio',
        message: 'Failed to play local playlist',
        operationId: operationId,
        cause: e.toString(),
      );
      throw AudioFailure('Failed to play audio: ${e.toString()}');
    }
  }

  /// Gets the current audio player instance.
  ///
  /// This is exposed for advanced use cases where direct access to the player is needed.
  AudioPlayer get audioPlayer => _audioPlayer;

  /// Pauses the current playback.
  ///
  /// Throws [AudioFailure] if pausing fails.
  Future<void> pauseMedia() async {
    try {
      await _audioPlayer.pause();
    } on Exception {
      throw const AudioFailure('Failed to pause media');
    }
  }

  /// Stops the current playback.
  ///
  /// Throws [AudioFailure] if stopping fails.
  Future<void> stopMedia() async {
    try {
      await _audioPlayer.stop();
    } on Exception {
      throw const AudioFailure('Failed to stop media');
    }
  }

  /// Seeks to a specific [position] in the current media.
  ///
  /// Throws [AudioFailure] if seeking fails.
  Future<void> seekTo(Duration position) async {
    try {
      await _audioPlayer.seek(position);
    } on Exception {
      throw const AudioFailure('Failed to seek');
    }
  }

  /// Sets the playback [speed].
  ///
  /// Throws [AudioFailure] if changing speed fails.
  Future<void> setSpeed(double speed) async {
    try {
      await _audioPlayer.setSpeed(speed);
    } on Exception {
      throw const AudioFailure('Failed to set speed');
    }
  }

  /// Sets now playing metadata (lockscreen/notification)
  Future<void> setNowPlayingMetadata({
    required String id,
    required String title,
    String? artist,
    Uri? artUri,
    Duration? duration,
    String? album,
  }) async {
    final item = MediaItem(
      id: id,
      title: title,
      artist: artist,
      artUri: artUri,
      duration: duration,
      album: album,
    );
    _playerHandler?.setNowPlayingItem(item);
    _currentMediaId = id;
  }

  Future<void> _persistPosition() async {
    if (_currentMediaId == null) return;
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setInt(
          'pos_${_currentMediaId!}', _audioPlayer.position.inMilliseconds);
    } on Object {
      // ignore
    }
  }

  Future<void> _restorePosition() async {
    if (_currentMediaId == null) return;
    try {
      final prefs = await SharedPreferences.getInstance();
      final ms = prefs.getInt('pos_${_currentMediaId!}') ?? 0;
      if (ms > 0) {
        await _audioPlayer.seek(Duration(milliseconds: ms));
      }
    } on Object {
      // ignore
    }
  }

  /// Validates and normalizes a file path.
  ///
  /// Throws [ArgumentError] if the path is invalid.
  String _validateAndNormalizePath(String path) {
    if (path.isEmpty) {
      throw ArgumentError('File path cannot be empty');
    }

    // Normalize path (remove duplicate slashes)
    final normalized = path.replaceAll(RegExp(r'/+'), '/');

    // Check if path is absolute (on Android)
    if (Platform.isAndroid && !normalized.startsWith('/')) {
      throw ArgumentError('File path must be absolute on Android: $path');
    }

    return normalized;
  }

  /// Creates audio sources from file paths with validation and error handling.
  ///
  /// Validates paths, checks file existence and accessibility, and creates
  /// AudioSource instances using Uri.file() for reliable ExoPlayer integration.
  Future<List<AudioSource>> _createAudioSources(
    List<String> filePaths,
    String operationId,
  ) async {
    final audioSources = <AudioSource>[];
    final logger = StructuredLogger();

    for (final filePath in filePaths) {
      try {
        // Validate and normalize path
        final normalizedPath = _validateAndNormalizePath(filePath);

        final file = File(normalizedPath);

        // Check if file exists
        if (!await file.exists()) {
          await logger.log(
            level: 'warning',
            subsystem: 'audio',
            message: 'Audio file does not exist',
            operationId: operationId,
            extra: {'path': normalizedPath},
          );
          continue;
        }

        // Check if path is a file (not directory)
        final stat = await file.stat();
        if (stat.type != FileSystemEntityType.file) {
          await logger.log(
            level: 'warning',
            subsystem: 'audio',
            message: 'Path is not a file',
            operationId: operationId,
            extra: {'path': normalizedPath},
          );
          continue;
        }

        // Check if file size is valid (not empty)
        if (stat.size == 0) {
          await logger.log(
            level: 'warning',
            subsystem: 'audio',
            message: 'Audio file is empty',
            operationId: operationId,
            extra: {'path': normalizedPath},
          );
          continue;
        }

        // Use Uri.file() for reliable ExoPlayer integration
        audioSources.add(AudioSource.uri(Uri.file(normalizedPath)));

        await logger.log(
          level: 'debug',
          subsystem: 'audio',
          message: 'Created audio source from local file',
          operationId: operationId,
          extra: {'path': normalizedPath, 'size': stat.size},
        );
      } on ArgumentError catch (e) {
        await logger.log(
          level: 'error',
          subsystem: 'audio',
          message: 'Invalid file path',
          operationId: operationId,
          cause: e.toString(),
          extra: {'path': filePath},
        );
      } on Exception catch (e) {
        await logger.log(
          level: 'error',
          subsystem: 'audio',
          message: 'Failed to create audio source',
          operationId: operationId,
          cause: e.toString(),
          extra: {'path': filePath},
        );
      }
    }

    return audioSources;
  }

  /// Waits for the audio player to be ready for playback.
  ///
  /// Throws [AudioFailure] if the player doesn't become ready within the timeout.
  Future<void> _waitForPlayerReady(String operationId) async {
    final logger = StructuredLogger();
    var attempts = 0;
    const maxAttempts = 50; // 5 seconds max

    while (_audioPlayer.processingState != ProcessingState.ready &&
        attempts < maxAttempts) {
      await Future.delayed(const Duration(milliseconds: 100));
      attempts++;

      // Check for errors
      if (_audioPlayer.processingState == ProcessingState.idle) {
        await logger.log(
          level: 'warning',
          subsystem: 'audio',
          message: 'Player is idle, waiting for ready state',
          operationId: operationId,
          extra: {'attempt': attempts},
        );
      }
    }

    if (_audioPlayer.processingState != ProcessingState.ready) {
      await logger.log(
        level: 'error',
        subsystem: 'audio',
        message: 'Player failed to become ready',
        operationId: operationId,
        extra: {
          'final_state': _audioPlayer.processingState.toString(),
          'attempts': attempts,
        },
      );
      throw const AudioFailure('Player failed to initialize');
    }

    await logger.log(
      level: 'debug',
      subsystem: 'audio',
      message: 'Player is ready',
      operationId: operationId,
      extra: {'attempts': attempts},
    );
  }

  /// Releases resources held by this handler.
  Future<void> dispose() async {
    await _audioPlayer.dispose();
    await _playbackState.drain();
    await _playbackState.close();
  }
}

/// Bridges audio_service callbacks to the just_audio player.
///
/// Implements [BaseAudioHandler] methods expected by audio_service.
class AudioPlayerHandler extends BaseAudioHandler {
  /// Creates a new [AudioPlayerHandler].
  ///
  /// [audioPlayer] is the just_audio instance used for playback.
  AudioPlayerHandler(this._audioPlayer);

  /// just_audio player instance used for media playback.
  final AudioPlayer _audioPlayer;

  /// Expose methods to update media item and playback state
  void setNowPlayingItem(MediaItem item) {
    mediaItem.add(item);
  }

  @override
  Future<void> play() => _audioPlayer.play();

  @override
  Future<void> pause() => _audioPlayer.pause();

  @override
  Future<void> stop() => _audioPlayer.stop();

  // NOTE: BaseAudioHandler defines `seek(Duration position)`, not `seekTo`.
  @override
  Future<void> seek(Duration position) => _audioPlayer.seek(position);

  @override
  Future<void> fastForward() async {
    final pos = _audioPlayer.position + const Duration(seconds: 30);
    await _audioPlayer.seek(pos);
  }

  @override
  Future<void> rewind() async {
    final newPos = _audioPlayer.position - const Duration(seconds: 15);
    await _audioPlayer.seek(newPos < Duration.zero ? Duration.zero : newPos);
  }
}
