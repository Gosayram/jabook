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

import 'package:jabook/core/domain/player/entities/player_state.dart';

/// Repository interface for player operations.
///
/// This repository provides methods for controlling audio playback,
/// managing playlists, and accessing player state.
abstract class PlayerRepository {
  /// Initializes the player service.
  ///
  /// Throws [Exception] if initialization fails.
  Future<void> initialize();

  /// Sets playlist and starts position saving.
  ///
  /// [filePaths] is a list of absolute file paths or HTTP(S) URLs to audio files.
  /// Supports both local files and network streaming.
  /// [metadata] is optional metadata (title, artist, album, artworkUri).
  /// [groupPath] is the unique path for saving playback positions.
  ///
  /// Throws [Exception] if setting playlist fails.
  Future<void> setPlaylist(
    List<String> filePaths, {
    Map<String, String>? metadata,
    String? groupPath,
  });

  /// Starts or resumes playback.
  ///
  /// Throws [Exception] if playback fails.
  Future<void> play();

  /// Pauses playback.
  ///
  /// Throws [Exception] if pausing fails.
  Future<void> pause();

  /// Seeks to a specific position in the current track.
  ///
  /// [position] is the position in milliseconds.
  ///
  /// Throws [Exception] if seeking fails.
  Future<void> seekTo(int position);

  /// Seeks to the next track in the playlist.
  ///
  /// Throws [Exception] if seeking fails.
  Future<void> seekToNext();

  /// Seeks to the previous track in the playlist.
  ///
  /// Throws [Exception] if seeking fails.
  Future<void> seekToPrevious();

  /// Sets the playback speed.
  ///
  /// [speed] is the playback speed (1.0 = normal speed).
  ///
  /// Throws [Exception] if setting speed fails.
  Future<void> setPlaybackSpeed(double speed);

  /// Gets the current player state.
  ///
  /// Returns the current PlayerState.
  Future<PlayerState> getState();

  /// Gets a stream of player state updates.
  ///
  /// Returns a stream that emits PlayerState whenever the state changes.
  Stream<PlayerState> get stateStream;

  /// Disposes the player service and releases resources.
  Future<void> dispose();
}
