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

import 'package:shared_preferences/shared_preferences.dart';

/// Manages global audio settings and preferences for the application.
class AudioSettingsManager {
  /// Private constructor for singleton pattern.
  AudioSettingsManager._();

  /// Factory constructor to get the instance.
  factory AudioSettingsManager() => _instance;

  static final AudioSettingsManager _instance = AudioSettingsManager._();

  /// Key for storing default playback speed in SharedPreferences.
  static const String _defaultPlaybackSpeedKey = 'audio_default_playback_speed';

  /// Key for storing default rewind duration in SharedPreferences.
  static const String _defaultRewindDurationKey =
      'audio_default_rewind_duration';

  /// Key for storing default forward duration in SharedPreferences.
  static const String _defaultForwardDurationKey =
      'audio_default_forward_duration';

  /// Key for storing inactivity timeout in SharedPreferences.
  static const String _inactivityTimeoutMinutesKey =
      'audio_inactivity_timeout_minutes';

  /// Default playback speed.
  static const double defaultPlaybackSpeed = 1.0;

  /// Default rewind duration in seconds.
  static const int defaultRewindDuration = 15;

  /// Default forward duration in seconds.
  static const int defaultForwardDuration = 30;

  /// Default inactivity timeout in minutes (60 minutes = 1 hour).
  static const int defaultInactivityTimeoutMinutes = 60;

  /// Minimum inactivity timeout in minutes.
  static const int minInactivityTimeoutMinutes = 10;

  /// Maximum inactivity timeout in minutes (3 hours).
  static const int maxInactivityTimeoutMinutes = 180;

  /// Playback speed step for fine control.
  static const double playbackSpeedStep = 0.05;

  /// Minimum playback speed.
  static const double minPlaybackSpeed = 0.5;

  /// Maximum playback speed.
  static const double maxPlaybackSpeed = 2.0;

  /// Generates list of available playback speeds with step of 0.05.
  ///
  /// Returns speeds from [minPlaybackSpeed] to [maxPlaybackSpeed] with step of [playbackSpeedStep].
  static List<double> getAvailablePlaybackSpeeds() {
    final speeds = <double>[];
    // Use integer arithmetic to avoid floating point precision issues
    final minSteps = (minPlaybackSpeed / playbackSpeedStep).round();
    final maxSteps = (maxPlaybackSpeed / playbackSpeedStep).round();
    for (var step = minSteps; step <= maxSteps; step++) {
      final speed = step * playbackSpeedStep;
      speeds.add(double.parse(speed.toStringAsFixed(2)));
    }
    return speeds;
  }

  /// Gets the default playback speed.
  Future<double> getDefaultPlaybackSpeed() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getDouble(_defaultPlaybackSpeedKey) ?? defaultPlaybackSpeed;
  }

  /// Sets the default playback speed.
  Future<void> setDefaultPlaybackSpeed(double speed) async {
    if (speed < minPlaybackSpeed || speed > maxPlaybackSpeed) {
      throw ArgumentError(
          'Speed must be between $minPlaybackSpeed and $maxPlaybackSpeed');
    }
    final prefs = await SharedPreferences.getInstance();
    await prefs.setDouble(_defaultPlaybackSpeedKey, speed);
  }

  /// Gets the default rewind duration in seconds.
  Future<int> getDefaultRewindDuration() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getInt(_defaultRewindDurationKey) ?? defaultRewindDuration;
  }

  /// Sets the default rewind duration in seconds.
  Future<void> setDefaultRewindDuration(int seconds) async {
    if (seconds < 1) {
      throw ArgumentError('Duration must be at least 1 second');
    }
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_defaultRewindDurationKey, seconds);
  }

  /// Gets the default forward duration in seconds.
  Future<int> getDefaultForwardDuration() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getInt(_defaultForwardDurationKey) ?? defaultForwardDuration;
  }

  /// Sets the default forward duration in seconds.
  Future<void> setDefaultForwardDuration(int seconds) async {
    if (seconds < 1) {
      throw ArgumentError('Duration must be at least 1 second');
    }
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_defaultForwardDurationKey, seconds);
  }

  /// Gets the inactivity timeout in minutes.
  Future<int> getInactivityTimeoutMinutes() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getInt(_inactivityTimeoutMinutesKey) ??
        defaultInactivityTimeoutMinutes;
  }

  /// Sets the inactivity timeout in minutes.
  Future<void> setInactivityTimeoutMinutes(int minutes) async {
    if (minutes < minInactivityTimeoutMinutes ||
        minutes > maxInactivityTimeoutMinutes) {
      throw ArgumentError(
          'Timeout must be between $minInactivityTimeoutMinutes and $maxInactivityTimeoutMinutes minutes');
    }
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_inactivityTimeoutMinutesKey, minutes);
  }

  /// Resets audio settings to defaults.
  Future<void> resetToDefaults() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setDouble(_defaultPlaybackSpeedKey, defaultPlaybackSpeed);
    await prefs.setInt(_defaultRewindDurationKey, defaultRewindDuration);
    await prefs.setInt(_defaultForwardDurationKey, defaultForwardDuration);
    await prefs.setInt(
        _inactivityTimeoutMinutesKey, defaultInactivityTimeoutMinutes);
  }

  /// Formats playback speed for display in UI.
  ///
  /// Rounds the speed value to 2 decimal places and formats it as "X.XXx".
  /// This ensures consistent display format across all UI components.
  ///
  /// Example:
  /// - 1.149999976158142 → "1.15x"
  /// - 0.999999 → "1.00x"
  /// - 1.250000 → "1.25x"
  static String formatPlaybackSpeed(double speed) =>
      '${speed.toStringAsFixed(2)}x';
}
