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

import 'package:jabook/core/infrastructure/config/audio_settings_manager.dart';
import 'package:riverpod/legacy.dart';

/// Model for global audio settings.
class AudioSettings {
  /// Creates a new AudioSettings instance.
  const AudioSettings({
    this.defaultPlaybackSpeed = AudioSettingsManager.defaultPlaybackSpeed,
    this.defaultRewindDuration = AudioSettingsManager.defaultRewindDuration,
    this.defaultForwardDuration = AudioSettingsManager.defaultForwardDuration,
    this.inactivityTimeoutMinutes =
        AudioSettingsManager.defaultInactivityTimeoutMinutes,
  });

  /// Default playback speed.
  final double defaultPlaybackSpeed;

  /// Default rewind duration in seconds.
  final int defaultRewindDuration;

  /// Default forward duration in seconds.
  final int defaultForwardDuration;

  /// Inactivity timeout in minutes (time before player unloads when paused).
  final int inactivityTimeoutMinutes;

  /// Creates a copy with updated fields.
  AudioSettings copyWith({
    double? defaultPlaybackSpeed,
    int? defaultRewindDuration,
    int? defaultForwardDuration,
    int? inactivityTimeoutMinutes,
  }) =>
      AudioSettings(
        defaultPlaybackSpeed: defaultPlaybackSpeed ?? this.defaultPlaybackSpeed,
        defaultRewindDuration:
            defaultRewindDuration ?? this.defaultRewindDuration,
        defaultForwardDuration:
            defaultForwardDuration ?? this.defaultForwardDuration,
        inactivityTimeoutMinutes:
            inactivityTimeoutMinutes ?? this.inactivityTimeoutMinutes,
      );
}

/// Provider for global audio settings.
final audioSettingsProvider =
    StateNotifierProvider<AudioSettingsNotifier, AudioSettings>(
  (ref) => AudioSettingsNotifier(),
);

/// Notifier for global audio settings management.
class AudioSettingsNotifier extends StateNotifier<AudioSettings> {
  /// Creates a new AudioSettingsNotifier instance.
  AudioSettingsNotifier() : super(const AudioSettings()) {
    _loadSettings();
  }

  final AudioSettingsManager _audioSettingsManager = AudioSettingsManager();

  /// Loads settings from SharedPreferences.
  Future<void> _loadSettings() async {
    try {
      final playbackSpeed =
          await _audioSettingsManager.getDefaultPlaybackSpeed();
      final rewindDuration =
          await _audioSettingsManager.getDefaultRewindDuration();
      final forwardDuration =
          await _audioSettingsManager.getDefaultForwardDuration();
      final inactivityTimeout =
          await _audioSettingsManager.getInactivityTimeoutMinutes();
      state = AudioSettings(
        defaultPlaybackSpeed: playbackSpeed,
        defaultRewindDuration: rewindDuration,
        defaultForwardDuration: forwardDuration,
        inactivityTimeoutMinutes: inactivityTimeout,
      );
    } on Object {
      // Use default settings on error
    }
  }

  /// Sets the default playback speed.
  Future<void> setDefaultPlaybackSpeed(double speed) async {
    if (speed < AudioSettingsManager.minPlaybackSpeed ||
        speed > AudioSettingsManager.maxPlaybackSpeed) {
      throw ArgumentError(
          'Speed must be between ${AudioSettingsManager.minPlaybackSpeed} and ${AudioSettingsManager.maxPlaybackSpeed}');
    }
    await _audioSettingsManager.setDefaultPlaybackSpeed(speed);
    state = state.copyWith(defaultPlaybackSpeed: speed);
  }

  /// Sets the default rewind duration in seconds.
  Future<void> setDefaultRewindDuration(int seconds) async {
    if (seconds < 1) {
      throw ArgumentError('Duration must be at least 1 second');
    }
    await _audioSettingsManager.setDefaultRewindDuration(seconds);
    state = state.copyWith(defaultRewindDuration: seconds);
  }

  /// Sets the default forward duration in seconds.
  Future<void> setDefaultForwardDuration(int seconds) async {
    if (seconds < 1) {
      throw ArgumentError('Duration must be at least 1 second');
    }
    await _audioSettingsManager.setDefaultForwardDuration(seconds);
    state = state.copyWith(defaultForwardDuration: seconds);
  }

  /// Sets the inactivity timeout in minutes.
  Future<void> setInactivityTimeoutMinutes(int minutes) async {
    if (minutes < AudioSettingsManager.minInactivityTimeoutMinutes ||
        minutes > AudioSettingsManager.maxInactivityTimeoutMinutes) {
      throw ArgumentError(
          'Timeout must be between ${AudioSettingsManager.minInactivityTimeoutMinutes} and ${AudioSettingsManager.maxInactivityTimeoutMinutes} minutes');
    }
    await _audioSettingsManager.setInactivityTimeoutMinutes(minutes);
    state = state.copyWith(inactivityTimeoutMinutes: minutes);
  }

  /// Resets audio settings to defaults.
  Future<void> resetToDefaults() async {
    await _audioSettingsManager.resetToDefaults();
    state = const AudioSettings();
  }
}
