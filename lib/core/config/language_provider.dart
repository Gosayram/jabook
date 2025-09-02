import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Provider for tracking language changes and notifying the app to rebuild.
final languageProvider = StateNotifierProvider<LanguageNotifier, String>(
  (ref) => LanguageNotifier(),
);

/// Notifier for language changes that reads from SharedPreferences.
/// Notifier for language changes that reads from SharedPreferences.
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