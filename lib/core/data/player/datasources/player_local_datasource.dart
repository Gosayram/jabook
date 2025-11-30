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

import 'package:jabook/core/data/player/mappers/player_mapper.dart';
import 'package:jabook/core/domain/player/entities/player_state.dart';
import 'package:jabook/core/player/media3_player_service.dart';

/// Local data source for player operations.
///
/// This class wraps Media3PlayerService to provide a clean interface
/// for player operations that interact with the native audio player.
abstract class PlayerLocalDataSource {
  /// Initializes the player service.
  Future<void> initialize();

  /// Sets playlist and starts position saving.
  Future<void> setPlaylist(
    List<String> filePaths, {
    Map<String, String>? metadata,
    String? groupPath,
  });

  /// Starts or resumes playback.
  Future<void> play();

  /// Pauses playback.
  Future<void> pause();

  /// Seeks to a specific position in the current track.
  Future<void> seekTo(int position);

  /// Seeks to the next track in the playlist.
  Future<void> seekToNext();

  /// Seeks to the previous track in the playlist.
  Future<void> seekToPrevious();

  /// Sets the playback speed.
  Future<void> setPlaybackSpeed(double speed);

  /// Gets the current player state.
  Future<PlayerState> getState();

  /// Gets a stream of player state updates.
  Stream<PlayerState> get stateStream;

  /// Disposes the player service and releases resources.
  Future<void> dispose();
}

/// Implementation of PlayerLocalDataSource using Media3PlayerService.
class PlayerLocalDataSourceImpl implements PlayerLocalDataSource {
  /// Creates a new PlayerLocalDataSourceImpl instance.
  PlayerLocalDataSourceImpl(this._service);

  final Media3PlayerService _service;

  @override
  Future<void> initialize() => _service.initialize();

  @override
  Future<void> setPlaylist(
    List<String> filePaths, {
    Map<String, String>? metadata,
    String? groupPath,
  }) =>
      _service.setPlaylist(
        filePaths,
        metadata: metadata,
        groupPath: groupPath,
      );

  @override
  Future<void> play() => _service.play();

  @override
  Future<void> pause() => _service.pause();

  @override
  Future<void> seekTo(int position) =>
      _service.seek(Duration(milliseconds: position));

  @override
  Future<void> seekToNext() => _service.next();

  @override
  Future<void> seekToPrevious() => _service.previous();

  @override
  Future<void> setPlaybackSpeed(double speed) => _service.setSpeed(speed);

  @override
  Future<PlayerState> getState() async {
    final audioState = await _service.getState();
    return PlayerMapper.toDomainFromAudioState(audioState);
  }

  @override
  Stream<PlayerState> get stateStream =>
      _service.stateStream.map(PlayerMapper.toDomainFromAudioState);

  @override
  Future<void> dispose() => _service.dispose();
}
