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

/// Manages language settings and preferences for the application.
///
/// Note: This class is no longer a singleton. Use [languageManagerProvider]
/// to get an instance via dependency injection.
class LanguageManager {
  /// Constructor for LanguageManager.
  ///
  /// Use [languageManagerProvider] to get an instance via dependency injection.
  LanguageManager();

  /// Key for storing language preference in SharedPreferences.
  static const String _languageKey = 'app_language';

  /// Gets the current language code.
  Future<String> getCurrentLanguage() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_languageKey) ?? 'system';
  }

  /// Sets the language preference.
  Future<void> setLanguage(String languageCode) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_languageKey, languageCode);
  }

  /// Gets the actual locale to use based on user preference.
  Future<Locale> getLocale() async {
    final languageCode = await getCurrentLanguage();

    if (languageCode == 'system') {
      // Use system default
      final systemLocale = WidgetsBinding.instance.platformDispatcher.locale;
      return _getSupportedLocale(systemLocale);
    }

    return _getSupportedLocale(Locale(languageCode));
  }

  /// Gets the supported locale that matches the requested locale.
  Locale _getSupportedLocale(Locale requestedLocale) {
    const supportedLocales = [
      Locale('en'), // English
      Locale('ru'), // Russian
    ];

    // First try exact match
    for (final locale in supportedLocales) {
      if (locale.languageCode == requestedLocale.languageCode) {
        return locale;
      }
    }

    // Fallback to English
    return const Locale('en');
  }

  /// Gets the display name for a language code.
  String getLanguageName(String languageCode) => switch (languageCode) {
        'en' => 'English',
        'ru' => 'Русский',
        'system' => 'System Default',
        _ => 'English',
      };

  /// Gets the flag emoji for a language code.
  String getLanguageFlag(String languageCode) => switch (languageCode) {
        'en' => '🇺🇸',
        'ru' => '🇷🇺',
        'system' => '🌐',
        _ => '🇺🇸',
      };

  /// Gets all available language options.
  List<Map<String, String>> getAvailableLanguages() => [
        {
          'code': 'system',
          'name': 'System Default',
          'flag': '🌐',
        },
        {
          'code': 'en',
          'name': 'English',
          'flag': '🇺🇸',
        },
        {
          'code': 'ru',
          'name': 'Русский',
          'flag': '🇷🇺',
        },
      ];

  /// Checks if the app should use RTL layout for the current language.
  Future<bool> isRTL() async {
    final languageCode = await getCurrentLanguage();

    if (languageCode == 'system') {
      final systemLocale = WidgetsBinding.instance.platformDispatcher.locale;
      return systemLocale.languageCode == 'ar' ||
          systemLocale.languageCode == 'he' ||
          systemLocale.languageCode == 'fa';
    }

    return languageCode == 'ar' || languageCode == 'he' || languageCode == 'fa';
  }

  /// Resets language preference to system default.
  Future<void> resetToSystemDefault() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_languageKey);
  }
}
