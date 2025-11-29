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

import 'package:jabook/core/data/player/datasources/player_local_datasource.dart';
import 'package:jabook/core/domain/player/entities/player_state.dart';
import 'package:jabook/core/domain/player/repositories/player_repository.dart';

/// Implementation of PlayerRepository using data sources.
class PlayerRepositoryImpl implements PlayerRepository {
  /// Creates a new PlayerRepositoryImpl instance.
  PlayerRepositoryImpl(this._localDataSource);

  final PlayerLocalDataSource _localDataSource;

  @override
  Future<void> initialize() => _localDataSource.initialize();

  @override
  Future<void> setPlaylist(
    List<String> filePaths, {
    Map<String, String>? metadata,
    String? groupPath,
  }) =>
      _localDataSource.setPlaylist(
        filePaths,
        metadata: metadata,
        groupPath: groupPath,
      );

  @override
  Future<void> play() => _localDataSource.play();

  @override
  Future<void> pause() => _localDataSource.pause();

  @override
  Future<void> seekTo(int position) => _localDataSource.seekTo(position);

  @override
  Future<void> seekToNext() => _localDataSource.seekToNext();

  @override
  Future<void> seekToPrevious() => _localDataSource.seekToPrevious();

  @override
  Future<void> setPlaybackSpeed(double speed) =>
      _localDataSource.setPlaybackSpeed(speed);

  @override
  Future<PlayerState> getState() => _localDataSource.getState();

  @override
  Stream<PlayerState> get stateStream => _localDataSource.stateStream;

  @override
  Future<void> dispose() => _localDataSource.dispose();
}
