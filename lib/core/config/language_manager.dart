import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Manages language settings and preferences for the application.
class LanguageManager {
  /// Private constructor for singleton pattern.
  LanguageManager._();

  /// Factory constructor to get the instance.
  factory LanguageManager() => _instance;

  static final LanguageManager _instance = LanguageManager._();

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