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
import 'package:shared_preferences/shared_preferences.dart';

/// Manages theme settings and preferences for the application.
class ThemeManager {
  /// Private constructor for singleton pattern.
  ThemeManager._();

  /// Factory constructor to get the instance.
  factory ThemeManager() => _instance;

  static final ThemeManager _instance = ThemeManager._();

  /// Key for storing theme follow system preference in SharedPreferences.
  static const String _followSystemKey = 'theme_follow_system';

  /// Key for storing theme mode preference in SharedPreferences.
  static const String _modeKey = 'theme_mode';

  /// Key for storing high contrast preference in SharedPreferences.
  static const String _highContrastKey = 'theme_high_contrast_enabled';

  /// Gets whether to follow system theme.
  Future<bool> getFollowSystem() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_followSystemKey) ?? true;
  }

  /// Sets whether to follow system theme.
  Future<void> setFollowSystem(bool followSystem) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_followSystemKey, followSystem);
  }

  /// Gets the theme mode ('light' or 'dark').
  ///
  /// Only used when followSystem is false.
  Future<String> getThemeMode() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_modeKey) ?? 'light';
  }

  /// Sets the theme mode ('light' or 'dark').
  ///
  /// Only used when followSystem is false.
  Future<void> setThemeMode(String mode) async {
    if (mode != 'light' && mode != 'dark') {
      throw ArgumentError('Mode must be "light" or "dark"');
    }
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_modeKey, mode);
  }

  /// Gets whether high contrast is enabled.
  Future<bool> getHighContrastEnabled() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_highContrastKey) ?? false;
  }

  /// Sets whether high contrast is enabled.
  Future<void> setHighContrastEnabled(bool enabled) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_highContrastKey, enabled);
  }

  /// Gets the ThemeMode to use based on current settings.
  Future<ThemeMode> getThemeModeForApp() async {
    final followSystem = await getFollowSystem();
    if (followSystem) {
      return ThemeMode.system;
    }
    final mode = await getThemeMode();
    return mode == 'dark' ? ThemeMode.dark : ThemeMode.light;
  }

  /// Resets theme preferences to defaults.
  Future<void> resetToDefaults() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_followSystemKey, true);
    await prefs.setString(_modeKey, 'light');
    await prefs.setBool(_highContrastKey, false);
  }
}
