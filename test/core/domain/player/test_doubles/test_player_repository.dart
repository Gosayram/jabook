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

import 'package:jabook/core/domain/player/entities/player_state.dart';
import 'package:jabook/core/domain/player/repositories/player_repository.dart';

/// Test implementation of PlayerRepository for testing.
///
/// This class provides a controllable implementation that can be used
/// in tests to simulate different scenarios.
class TestPlayerRepository implements PlayerRepository {
  bool _isInitialized = false;
  bool _shouldFailInitialize = false;
  bool _shouldFailPlay = false;
  bool _shouldFailPause = false;
  bool _shouldFailSeek = false;
  bool _shouldFailSetPlaylist = false;
  bool _shouldFailSeekToNext = false;
  bool _shouldFailSeekToPrevious = false;
  bool _shouldFailSetPlaybackSpeed = false;
  bool _shouldFailGetState = false;
  bool _shouldFailDispose = false;

  PlayerState _currentState = const PlayerState(
    isPlaying: false,
    currentPosition: 0,
    duration: 0,
    currentIndex: 0,
    playbackSpeed: 1.0,
    playbackState: 0,
  );

  final StreamController<PlayerState> _stateController =
      StreamController<PlayerState>.broadcast();

  List<String>? _lastPlaylist;
  Map<String, String>? _lastMetadata;
  String? _lastGroupPath;
  int? _lastSeekPosition;
  double? _lastPlaybackSpeed;

  @override
  Future<void> initialize() async {
    if (_shouldFailInitialize) {
      throw Exception('Failed to initialize player');
    }
    _isInitialized = true;
  }

  @override
  Future<void> setPlaylist(
    List<String> filePaths, {
    Map<String, String>? metadata,
    String? groupPath,
  }) async {
    if (_shouldFailSetPlaylist) {
      throw Exception('Failed to set playlist');
    }
    _lastPlaylist = filePaths;
    _lastMetadata = metadata;
    _lastGroupPath = groupPath;
    _currentState = _currentState.copyWith(
      currentIndex: 0,
      duration: 0,
    );
    _stateController.add(_currentState);
  }

  @override
  Future<void> play() async {
    if (_shouldFailPlay) {
      throw Exception('Failed to play');
    }
    _currentState = _currentState.copyWith(isPlaying: true);
    _stateController.add(_currentState);
  }

  @override
  Future<void> pause() async {
    if (_shouldFailPause) {
      throw Exception('Failed to pause');
    }
    _currentState = _currentState.copyWith(isPlaying: false);
    _stateController.add(_currentState);
  }

  @override
  Future<void> seekTo(int position) async {
    if (_shouldFailSeek) {
      throw Exception('Failed to seek');
    }
    _lastSeekPosition = position;
    _currentState = _currentState.copyWith(currentPosition: position);
    _stateController.add(_currentState);
  }

  @override
  Future<void> seekToNext() async {
    if (_shouldFailSeekToNext) {
      throw Exception('Failed to seek to next');
    }
    _currentState = _currentState.copyWith(
      currentIndex: _currentState.currentIndex + 1,
      currentPosition: 0,
    );
    _stateController.add(_currentState);
  }

  @override
  Future<void> seekToPrevious() async {
    if (_shouldFailSeekToPrevious) {
      throw Exception('Failed to seek to previous');
    }
    _currentState = _currentState.copyWith(
      currentIndex:
          (_currentState.currentIndex - 1).clamp(0, double.infinity).toInt(),
      currentPosition: 0,
    );
    _stateController.add(_currentState);
  }

  @override
  Future<void> setPlaybackSpeed(double speed) async {
    if (_shouldFailSetPlaybackSpeed) {
      throw Exception('Failed to set playback speed');
    }
    _lastPlaybackSpeed = speed;
    _currentState = _currentState.copyWith(playbackSpeed: speed);
    _stateController.add(_currentState);
  }

  @override
  Future<PlayerState> getState() async {
    if (_shouldFailGetState) {
      throw Exception('Failed to get state');
    }
    return _currentState;
  }

  @override
  Stream<PlayerState> get stateStream => _stateController.stream;

  @override
  Future<void> dispose() async {
    if (_shouldFailDispose) {
      throw Exception('Failed to dispose');
    }
    await _stateController.close();
    _isInitialized = false;
  }

  // Test helpers
  set shouldFailInitialize(bool value) => _shouldFailInitialize = value;
  set shouldFailPlay(bool value) => _shouldFailPlay = value;
  set shouldFailPause(bool value) => _shouldFailPause = value;
  set shouldFailSeek(bool value) => _shouldFailSeek = value;
  set shouldFailSetPlaylist(bool value) => _shouldFailSetPlaylist = value;
  set shouldFailSeekToNext(bool value) => _shouldFailSeekToNext = value;
  set shouldFailSeekToPrevious(bool value) => _shouldFailSeekToPrevious = value;
  set shouldFailSetPlaybackSpeed(bool value) =>
      _shouldFailSetPlaybackSpeed = value;
  set shouldFailGetState(bool value) => _shouldFailGetState = value;
  set shouldFailDispose(bool value) => _shouldFailDispose = value;

  void setState(PlayerState state) {
    _currentState = state;
    _stateController.add(_currentState);
  }

  List<String>? get lastPlaylist => _lastPlaylist;
  Map<String, String>? get lastMetadata => _lastMetadata;
  String? get lastGroupPath => _lastGroupPath;
  int? get lastSeekPosition => _lastSeekPosition;
  double? get lastPlaybackSpeed => _lastPlaybackSpeed;
  bool get isInitialized => _isInitialized;
}
