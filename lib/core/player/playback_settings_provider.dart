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

import 'package:riverpod/legacy.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Repeat mode for playback.
enum RepeatMode {
  /// No repeat - play once and stop.
  none,

  /// Repeat current track.
  track,

  /// Repeat entire playlist/queue.
  playlist,
}

/// Provider for playback settings.
final playbackSettingsProvider =
    StateNotifierProvider<PlaybackSettingsNotifier, PlaybackSettings>(
  (ref) => PlaybackSettingsNotifier(),
);

/// Playback settings model.
class PlaybackSettings {
  /// Creates a new PlaybackSettings instance.
  const PlaybackSettings({
    this.repeatMode = RepeatMode.none,
  });

  /// Repeat mode for playback.
  final RepeatMode repeatMode;

  /// Creates a copy with updated fields.
  PlaybackSettings copyWith({
    RepeatMode? repeatMode,
  }) =>
      PlaybackSettings(
        repeatMode: repeatMode ?? this.repeatMode,
      );
}

/// Notifier for playback settings management.
class PlaybackSettingsNotifier extends StateNotifier<PlaybackSettings> {
  /// Creates a new PlaybackSettingsNotifier instance.
  PlaybackSettingsNotifier() : super(const PlaybackSettings()) {
    _loadSettings();
  }

  /// Loads settings from SharedPreferences.
  Future<void> _loadSettings() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final repeatModeIndex = prefs.getInt('repeat_mode') ?? 0;
      state = state.copyWith(
        repeatMode: RepeatMode.values[repeatModeIndex.clamp(
          0,
          RepeatMode.values.length - 1,
        )],
      );
    } on Object {
      // Use default settings on error
    }
  }

  /// Sets repeat mode.
  Future<void> setRepeatMode(RepeatMode mode) async {
    state = state.copyWith(repeatMode: mode);
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setInt('repeat_mode', mode.index);
    } on Object {
      // Ignore save errors
    }
  }

  /// Cycles through repeat modes: none -> track -> playlist -> none.
  Future<void> cycleRepeatMode() async {
    final nextMode = switch (state.repeatMode) {
      RepeatMode.none => RepeatMode.track,
      RepeatMode.track => RepeatMode.playlist,
      RepeatMode.playlist => RepeatMode.none,
    };
    await setRepeatMode(nextMode);
  }
}
