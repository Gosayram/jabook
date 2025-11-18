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

/// Utility class for managing first launch detection.
///
/// This class uses SharedPreferences to track whether the app
/// has been launched before. Useful for showing onboarding
/// dialogs, tutorials, or initial setup screens.
class FirstLaunchHelper {
  /// Private constructor to prevent instantiation.
  FirstLaunchHelper._();

  /// Key for storing first launch status in SharedPreferences.
  static const String _firstLaunchKey = 'is_first_launch';

  /// Checks if this is the first launch of the app.
  ///
  /// Returns [true] if this is the first launch, [false] otherwise.
  /// Defaults to [true] if the key doesn't exist (assumes first launch).
  static Future<bool> isFirstLaunch() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      return prefs.getBool(_firstLaunchKey) ?? true;
    } on Exception catch (_) {
      // If we can't read preferences, assume first launch to be safe
      return true;
    }
  }

  /// Marks the app as having been launched (not first launch anymore).
  ///
  /// This should be called after showing onboarding dialogs or
  /// completing initial setup.
  static Future<void> markAsLaunched() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setBool(_firstLaunchKey, false);
    } on Exception catch (_) {
      // Silently fail - not critical
    }
  }

  /// Resets the first launch status (for testing purposes).
  ///
  /// This can be used to test onboarding flows again.
  static Future<void> resetFirstLaunch() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.remove(_firstLaunchKey);
    } on Exception catch (_) {
      // Silently fail - not critical
    }
  }
}
