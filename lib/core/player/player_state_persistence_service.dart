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

import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

/// Model for saved player state.
class SavedPlayerState {
  /// Creates a new SavedPlayerState instance.
  const SavedPlayerState({
    required this.groupPath,
    required this.filePaths,
    this.metadata,
    this.currentIndex = 0,
    this.currentPosition = 0,
    this.playbackSpeed = 1.0,
    this.isPlaying = false,
    this.repeatMode = 0, // RepeatMode.none
    this.sleepTimerRemainingSeconds,
  });

  /// Creates SavedPlayerState from JSON map.
  factory SavedPlayerState.fromJson(Map<String, dynamic> json) =>
      SavedPlayerState(
        groupPath: json['groupPath'] as String,
        filePaths: List<String>.from(json['filePaths'] as List),
        metadata: json['metadata'] != null
            ? Map<String, String>.from(json['metadata'] as Map)
            : null,
        currentIndex: json['currentIndex'] as int? ?? 0,
        currentPosition: json['currentPosition'] as int? ?? 0,
        playbackSpeed: (json['playbackSpeed'] as num?)?.toDouble() ?? 1.0,
        isPlaying: json['isPlaying'] as bool? ?? false,
        repeatMode: json['repeatMode'] as int? ?? 0,
        sleepTimerRemainingSeconds: json['sleepTimerRemainingSeconds'] as int?,
      );

  /// Unique path identifying the audiobook group.
  final String groupPath;

  /// List of file paths in the playlist.
  final List<String> filePaths;

  /// Optional metadata (title, artist, album, coverPath).
  final Map<String, String>? metadata;

  /// Current track index in playlist.
  final int currentIndex;

  /// Current position in milliseconds.
  final int currentPosition;

  /// Playback speed (1.0 = normal).
  final double playbackSpeed;

  /// Whether playback is currently playing.
  final bool isPlaying;

  /// Repeat mode (0 = none, 1 = track, 2 = playlist).
  final int repeatMode;

  /// Remaining seconds for sleep timer, if active.
  final int? sleepTimerRemainingSeconds;

  /// Converts to JSON map.
  Map<String, dynamic> toJson() => {
        'groupPath': groupPath,
        'filePaths': filePaths,
        if (metadata != null) 'metadata': metadata,
        'currentIndex': currentIndex,
        'currentPosition': currentPosition,
        'playbackSpeed': playbackSpeed,
        'isPlaying': isPlaying,
        'repeatMode': repeatMode,
        if (sleepTimerRemainingSeconds != null)
          'sleepTimerRemainingSeconds': sleepTimerRemainingSeconds,
      };
}

/// Service for persisting and restoring full player state.
///
/// This service saves and restores complete player state including
/// playlist, position, speed, repeat mode, and sleep timer.
class PlayerStatePersistenceService {
  /// Key for storing current player state.
  static const String _stateKey = 'player_state';

  /// Saves current player state.
  ///
  /// [state] is the player state to save.
  Future<void> saveState(SavedPlayerState state) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final json = jsonEncode(state.toJson());
      await prefs.setString(_stateKey, json);
    } on Exception {
      // Ignore errors - state saving is not critical
    }
  }

  /// Restores saved player state.
  ///
  /// Returns [SavedPlayerState] if available, null otherwise.
  Future<SavedPlayerState?> restoreState() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final json = prefs.getString(_stateKey);
      if (json == null) return null;

      final map = jsonDecode(json) as Map<String, dynamic>;
      return SavedPlayerState.fromJson(map);
    } on Exception {
      return null;
    }
  }

  /// Clears saved player state.
  Future<void> clearState() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.remove(_stateKey);
    } on Exception {
      // Ignore errors
    }
  }

  /// Checks if there is a saved player state.
  Future<bool> hasSavedState() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      return prefs.containsKey(_stateKey);
    } on Exception {
      return false;
    }
  }
}
