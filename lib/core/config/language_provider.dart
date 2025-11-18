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

/// Provider for tracking language changes and notifying the app to rebuild.
final languageProvider = StateNotifierProvider<LanguageNotifier, String>(
  (ref) => LanguageNotifier(),
);

/// Notifier for language changes that reads from SharedPreferences.
class LanguageNotifier extends StateNotifier<String> {
  /// Creates a new LanguageNotifier instance.
  LanguageNotifier() : super('system') {
    _loadLanguagePreference();
  }

  /// Key for storing language preference in SharedPreferences.
  static const String _languageKey = 'app_language';

  /// Loads the language preference from SharedPreferences.
  Future<void> _loadLanguagePreference() async {
    final prefs = await SharedPreferences.getInstance();
    final languageCode = prefs.getString(_languageKey) ?? 'system';
    state = languageCode;
  }

  /// Changes the language and updates SharedPreferences.
  Future<void> changeLanguage(String languageCode) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_languageKey, languageCode);
    state = languageCode;
  }

  /// Resets language preference to system default.
  Future<void> resetToSystemDefault() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_languageKey);
    state = 'system';
  }
}
