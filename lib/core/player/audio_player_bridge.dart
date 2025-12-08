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

import 'package:flutter/services.dart';

/// Thin wrapper over MethodChannel for audio player operations.
///
/// This class provides a minimal interface for calling native methods.
/// All business logic is in the Kotlin layer.
class AudioPlayerBridge {
  /// Creates a new AudioPlayerBridge instance.
  ///
  /// Uses v2 channel name for parallel operation with old API during migration.
  /// Can be switched to 'com.jabook.app.jabook/audio_player' after full migration.
  AudioPlayerBridge({bool useV2Channel = true})
      : _channel = MethodChannel(
          useV2Channel
              ? 'com.jabook.app.jabook/audio_player_v2'
              : 'com.jabook.app.jabook/audio_player',
        );

  final MethodChannel _channel;

  /// Loads a playlist for a book.
  ///
  /// Returns a map with playlist data or throws an error.
  Future<Map<String, dynamic>> loadPlaylist(String bookId) async {
    try {
      final result = await _channel.invokeMethod<Map<Object?, Object?>>(
        'loadPlaylist',
        {'bookId': bookId},
      );
      if (result == null) {
        throw PlatformException(
          code: 'NULL_RESULT',
          message: 'loadPlaylist returned null',
        );
      }
      return Map<String, dynamic>.from(result);
    } on PlatformException catch (e) {
      throw PlatformException(
        code: e.code,
        message: e.message,
        details: e.details,
      );
    }
  }

  /// Saves the current playback position.
  Future<bool> savePosition({
    required String bookId,
    required int trackIndex,
    required int position,
  }) async {
    try {
      final result = await _channel.invokeMethod<bool>(
        'savePosition',
        {
          'bookId': bookId,
          'trackIndex': trackIndex,
          'position': position,
        },
      );
      return result ?? false;
    } on PlatformException catch (e) {
      throw PlatformException(
        code: e.code,
        message: e.message,
        details: e.details,
      );
    }
  }

  /// Restores the playback position for a book.
  ///
  /// Returns a map with restored state or null if no saved position exists.
  Future<Map<String, dynamic>?> restorePlayback(String bookId) async {
    try {
      final result = await _channel.invokeMethod<Map<Object?, Object?>>(
        'restorePlayback',
        {'bookId': bookId},
      );
      if (result == null) {
        return null;
      }
      return Map<String, dynamic>.from(result);
    } on PlatformException catch (e) {
      throw PlatformException(
        code: e.code,
        message: e.message,
        details: e.details,
      );
    }
  }

  /// Synchronizes chapters metadata.
  Future<bool> syncChapters({
    required String bookId,
    required String bookTitle,
    required List<Map<String, dynamic>> chapters,
    required List<String> filePaths,
  }) async {
    try {
      final result = await _channel.invokeMethod<bool>(
        'syncChapters',
        {
          'bookId': bookId,
          'bookTitle': bookTitle,
          'chapters': chapters,
          'filePaths': filePaths,
        },
      );
      return result ?? false;
    } on PlatformException catch (e) {
      throw PlatformException(
        code: e.code,
        message: e.message,
        details: e.details,
      );
    }
  }

  /// Plays or resumes playback.
  Future<void> play() async {
    try {
      await _channel.invokeMethod('play');
    } on PlatformException catch (e) {
      throw PlatformException(
        code: e.code,
        message: e.message,
        details: e.details,
      );
    }
  }

  /// Pauses playback.
  Future<void> pause() async {
    try {
      await _channel.invokeMethod('pause');
    } on PlatformException catch (e) {
      throw PlatformException(
        code: e.code,
        message: e.message,
        details: e.details,
      );
    }
  }

  /// Seeks to a specific position.
  ///
  /// [positionMs] is the position in milliseconds.
  Future<void> seek(int positionMs) async {
    try {
      await _channel.invokeMethod('seek', {'position': positionMs});
    } on PlatformException catch (e) {
      throw PlatformException(
        code: e.code,
        message: e.message,
        details: e.details,
      );
    }
  }

  /// Sets playback speed.
  ///
  /// [speed] is the playback speed (0.5 to 2.0).
  Future<void> setSpeed(double speed) async {
    try {
      await _channel.invokeMethod('setSpeed', {'speed': speed});
    } on PlatformException catch (e) {
      throw PlatformException(
        code: e.code,
        message: e.message,
        details: e.details,
      );
    }
  }

  /// Skips to next track.
  Future<void> next() async {
    try {
      await _channel.invokeMethod('next');
    } on PlatformException catch (e) {
      throw PlatformException(
        code: e.code,
        message: e.message,
        details: e.details,
      );
    }
  }

  /// Skips to previous track.
  Future<void> previous() async {
    try {
      await _channel.invokeMethod('previous');
    } on PlatformException catch (e) {
      throw PlatformException(
        code: e.code,
        message: e.message,
        details: e.details,
      );
    }
  }

  /// Stops playback.
  Future<void> stop() async {
    try {
      await _channel.invokeMethod('stop');
    } on PlatformException catch (e) {
      throw PlatformException(
        code: e.code,
        message: e.message,
        details: e.details,
      );
    }
  }

  /// Sets playlist from file paths.
  ///
  /// [filePaths] is a list of absolute file paths or HTTP(S) URLs to audio files.
  /// [metadata] is optional metadata (title, artist, album, artworkUri).
  /// [initialTrackIndex] is optional track index to load first.
  /// [initialPosition] is optional position in milliseconds to seek to after loading initial track.
  /// [groupPath] is optional group path for saving playback position.
  Future<bool> setPlaylist({
    required List<String> filePaths,
    Map<String, String>? metadata,
    int? initialTrackIndex,
    int? initialPosition,
    String? groupPath,
  }) async {
    try {
      final result = await _channel.invokeMethod<bool>(
        'setPlaylist',
        {
          'filePaths': filePaths,
          'metadata': metadata,
          'initialTrackIndex': initialTrackIndex,
          'initialPosition': initialPosition,
          'groupPath': groupPath,
        },
      );
      return result ?? false;
    } on PlatformException catch (e) {
      throw PlatformException(
        code: e.code,
        message: e.message,
        details: e.details,
      );
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
      final result = await _channel.invokeMethod<Map<Object?, Object?>>(
        'restorePosition',
        {
          'groupPath': groupPath,
          if (fileCount != null) 'fileCount': fileCount,
        },
      );
      if (result == null) {
        return null;
      }
      return {
        'trackIndex': result['trackIndex'] as int,
        'positionMs': result['positionMs'] as int,
      };
    } on PlatformException catch (e) {
      throw PlatformException(
        code: e.code,
        message: e.message,
        details: e.details,
      );
    }
  }

  /// Seeks to a specific track.
  ///
  /// [trackIndex] is the track index to seek to.
  Future<void> seekToTrack(int trackIndex) async {
    try {
      await _channel.invokeMethod('seekToTrack', {'trackIndex': trackIndex});
    } on PlatformException catch (e) {
      throw PlatformException(
        code: e.code,
        message: e.message,
        details: e.details,
      );
    }
  }

  /// Updates playback metadata.
  ///
  /// [metadata] is a map with metadata (title, artist, album, artworkUri).
  Future<void> updateMetadata(Map<String, String> metadata) async {
    try {
      await _channel.invokeMethod('updateMetadata', {'metadata': metadata});
    } on PlatformException catch (e) {
      throw PlatformException(
        code: e.code,
        message: e.message,
        details: e.details,
      );
    }
  }

  /// Initializes the audio player service.
  Future<bool> initialize() async {
    try {
      final result = await _channel.invokeMethod<bool>('initialize');
      return result ?? false;
    } on PlatformException catch (e) {
      throw PlatformException(
        code: e.code,
        message: e.message,
        details: e.details,
      );
    }
  }

  /// Stops the audio service and exits the app.
  ///
  /// This is typically used when sleep timer expires.
  /// Uses the new bridge architecture.
  Future<void> stopServiceAndExit() async {
    try {
      await _channel.invokeMethod('stopServiceAndExit');
    } on PlatformException catch (e) {
      throw PlatformException(
        code: e.code,
        message: e.message,
        details: e.details,
      );
    }
  }

  /// Restores full player state.
  ///
  /// [groupPath] is optional. If provided, restores state for that specific group.
  /// Otherwise, tries to restore any saved state.
  ///
  /// Returns SavedPlayerState as Map if available, null otherwise.
  /// Uses the new bridge architecture with Kotlin layer persistence.
  Future<Map<String, dynamic>?> restoreFullState({String? groupPath}) async {
    try {
      final arguments = groupPath != null ? {'groupPath': groupPath} : null;
      final result = await _channel.invokeMethod<Map<Object?, Object?>>(
        'restoreFullState',
        arguments,
      );
      if (result == null) {
        return null;
      }
      return Map<String, dynamic>.from(result);
    } on PlatformException catch (e) {
      throw PlatformException(
        code: e.code,
        message: e.message,
        details: e.details,
      );
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
      await _channel.invokeMethod(
        'updateSavedStateSettings',
        {
          'groupPath': groupPath,
          if (repeatMode != null) 'repeatMode': repeatMode,
          if (sleepTimerRemainingSeconds != null)
            'sleepTimerRemainingSeconds': sleepTimerRemainingSeconds,
        },
      );
    } on PlatformException catch (e) {
      throw PlatformException(
        code: e.code,
        message: e.message,
        details: e.details,
      );
    }
  }
}
