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
///
/// Note: This class is no longer a singleton. Use [audioSettingsManagerProvider]
/// to get an instance via dependency injection.
class AudioSettingsManager {
  /// Constructor for AudioSettingsManager.
  ///
  /// Use [audioSettingsManagerProvider] to get an instance via dependency injection.
  AudioSettingsManager();

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

  /// Key for storing volume boost level in SharedPreferences.
  static const String _volumeBoostLevelKey = 'audio_volume_boost_level';

  /// Key for storing DRC level in SharedPreferences.
  static const String _drcLevelKey = 'audio_drc_level';

  /// Key for storing speech enhancer enabled state in SharedPreferences.
  static const String _speechEnhancerKey = 'audio_speech_enhancer';

  /// Key for storing auto volume leveling enabled state in SharedPreferences.
  static const String _autoVolumeLevelingKey = 'audio_auto_volume_leveling';

  /// Key for storing normalize volume enabled state in SharedPreferences.
  static const String _normalizeVolumeKey = 'audio_normalize_volume';

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

  /// Gets the volume boost level.
  Future<String> getVolumeBoostLevel() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_volumeBoostLevelKey) ?? 'Off';
  }

  /// Sets the volume boost level.
  Future<void> setVolumeBoostLevel(String level) async {
    final validLevels = ['Off', 'Boost50', 'Boost100', 'Boost200', 'Auto'];
    if (!validLevels.contains(level)) {
      throw ArgumentError(
          'Volume boost level must be one of: ${validLevels.join(", ")}');
    }
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_volumeBoostLevelKey, level);
  }

  /// Gets the DRC (Dynamic Range Compression) level.
  Future<String> getDRCLevel() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_drcLevelKey) ?? 'Off';
  }

  /// Sets the DRC (Dynamic Range Compression) level.
  Future<void> setDRCLevel(String level) async {
    final validLevels = ['Off', 'Gentle', 'Medium', 'Strong'];
    if (!validLevels.contains(level)) {
      throw ArgumentError(
          'DRC level must be one of: ${validLevels.join(", ")}');
    }
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_drcLevelKey, level);
  }

  /// Gets whether speech enhancer is enabled.
  Future<bool> getSpeechEnhancer() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_speechEnhancerKey) ?? false;
  }

  /// Sets whether speech enhancer is enabled.
  Future<void> setSpeechEnhancer(bool enabled) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_speechEnhancerKey, enabled);
  }

  /// Gets whether auto volume leveling is enabled.
  Future<bool> getAutoVolumeLeveling() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_autoVolumeLevelingKey) ?? false;
  }

  /// Sets whether auto volume leveling is enabled.
  Future<void> setAutoVolumeLeveling(bool enabled) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_autoVolumeLevelingKey, enabled);
  }

  /// Gets whether volume normalization is enabled.
  Future<bool> getNormalizeVolume() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_normalizeVolumeKey) ?? true; // Default: enabled
  }

  /// Sets whether volume normalization is enabled.
  Future<void> setNormalizeVolume(bool enabled) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_normalizeVolumeKey, enabled);
  }

  /// Resets audio settings to defaults.
  Future<void> resetToDefaults() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setDouble(_defaultPlaybackSpeedKey, defaultPlaybackSpeed);
    await prefs.setInt(_defaultRewindDurationKey, defaultRewindDuration);
    await prefs.setInt(_defaultForwardDurationKey, defaultForwardDuration);
    await prefs.setInt(
        _inactivityTimeoutMinutesKey, defaultInactivityTimeoutMinutes);
    await prefs.setString(_volumeBoostLevelKey, 'Off');
    await prefs.setString(_drcLevelKey, 'Off');
    await prefs.setBool(_speechEnhancerKey, false);
    await prefs.setBool(_autoVolumeLevelingKey, false);
    await prefs.setBool(_normalizeVolumeKey, true);
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
