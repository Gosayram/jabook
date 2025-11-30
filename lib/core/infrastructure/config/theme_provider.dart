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

import 'package:flutter/material.dart';
import 'package:jabook/core/infrastructure/config/theme_manager.dart';
import 'package:riverpod/legacy.dart';

/// Model for theme settings.
class ThemeSettings {
  /// Creates a new ThemeSettings instance.
  const ThemeSettings({
    this.followSystem = true,
    this.mode = 'light',
    this.highContrastEnabled = false,
  });

  /// Whether to follow system theme.
  final bool followSystem;

  /// Theme mode ('light' or 'dark').
  ///
  /// Only used when followSystem is false.
  final String mode;

  /// Whether high contrast is enabled.
  final bool highContrastEnabled;

  /// Creates a copy with updated fields.
  ThemeSettings copyWith({
    bool? followSystem,
    String? mode,
    bool? highContrastEnabled,
  }) =>
      ThemeSettings(
        followSystem: followSystem ?? this.followSystem,
        mode: mode ?? this.mode,
        highContrastEnabled: highContrastEnabled ?? this.highContrastEnabled,
      );

  /// Gets the ThemeMode to use.
  ThemeMode get themeMode {
    if (followSystem) {
      return ThemeMode.system;
    }
    return mode == 'dark' ? ThemeMode.dark : ThemeMode.light;
  }
}

/// Provider for theme settings.
final themeProvider = StateNotifierProvider<ThemeNotifier, ThemeSettings>(
  (ref) => ThemeNotifier(),
);

/// Notifier for theme changes.
class ThemeNotifier extends StateNotifier<ThemeSettings> {
  /// Creates a new ThemeNotifier instance.
  ThemeNotifier() : super(const ThemeSettings()) {
    _loadThemeSettings();
  }

  final ThemeManager _themeManager = ThemeManager();

  /// Loads theme settings from SharedPreferences.
  Future<void> _loadThemeSettings() async {
    final followSystem = await _themeManager.getFollowSystem();
    final mode = await _themeManager.getThemeMode();
    final highContrastEnabled = await _themeManager.getHighContrastEnabled();
    state = ThemeSettings(
      followSystem: followSystem,
      mode: mode,
      highContrastEnabled: highContrastEnabled,
    );
  }

  /// Sets whether to follow system theme.
  Future<void> setFollowSystem(bool followSystem) async {
    await _themeManager.setFollowSystem(followSystem);
    state = state.copyWith(followSystem: followSystem);
  }

  /// Sets the theme mode ('light' or 'dark').
  ///
  /// Only used when followSystem is false.
  Future<void> setThemeMode(String mode) async {
    if (mode != 'light' && mode != 'dark') {
      throw ArgumentError('Mode must be "light" or "dark"');
    }
    await _themeManager.setThemeMode(mode);
    state = state.copyWith(mode: mode);
  }

  /// Sets whether high contrast is enabled.
  Future<void> setHighContrastEnabled(bool enabled) async {
    await _themeManager.setHighContrastEnabled(enabled);
    state = state.copyWith(highContrastEnabled: enabled);
  }

  /// Resets theme settings to defaults.
  Future<void> resetToDefaults() async {
    await _themeManager.resetToDefaults();
    state = const ThemeSettings();
  }
}
