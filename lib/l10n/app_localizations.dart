import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// Localization class for handling multi-language support in JaBook app.
class AppLocalizations {
  /// Creates a new AppLocalizations instance for the given locale.
  AppLocalizations(this.locale);

  /// The locale for which this localization instance provides translations.
  final Locale locale;

  /// Retrieves the AppLocalizations instance from the build context.
  static AppLocalizations? of(BuildContext context) =>
      Localizations.of<AppLocalizations>(context, AppLocalizations);

  /// The delegate for loading AppLocalizations instances.
  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  Map<String, String>? _localizedStrings;

  /// Loads the localization strings for the current locale.
  Future<bool> load() async {
    try {
      final jsonString = await rootBundle.loadString(
        'packages/jabook/lib/l10n/app_${locale.languageCode}.arb',
      );
      
      final jsonMap = Map<String, dynamic>.from(await _parseArb(jsonString));
      
      _localizedStrings = jsonMap.map((key, value) {
        if (key.startsWith('@')) return MapEntry(key, value.toString());
        return MapEntry(key, value.toString());
      });
      
      return true;
    } on Exception catch (_) {
      return false;
    }
  }

  Future<Map<String, dynamic>> _parseArb(String jsonString) async {
    // Simple ARB parser - in production, use proper JSON parsing
    final result = <String, dynamic>{};
    final lines = jsonString.split('\n');
    
    for (final line in lines) {
      if (line.trim().isEmpty ||
          line.trim().startsWith('//') ||
          line.trim().startsWith('/*')) {
        continue;
      }
      
      if (line.contains(':')) {
        final parts = line.split(':');
        if (parts.length >= 2) {
          final key = parts[0].trim().replaceAll('"', '').replaceAll(',', '');
          final value = parts[1].trim().replaceAll('"', '').replaceAll(',', '');
          
          if (key.isNotEmpty && value.isNotEmpty) {
            result[key] = value;
          }
        }
      }
    }
    
    return result;
  }

  /// Translates the given key to the current locale.
  String translate(String key) => _localizedStrings?[key] ?? key;

  /// Returns the localized app title.
  String get appTitle => translate('appTitle');

  /// Returns the localized search screen title.
  String get searchAudiobooks => translate('searchAudiobooks');

  /// Returns the localized library screen title.
  String get libraryTitle => translate('libraryTitle');

  /// Returns the localized player screen title.
  String get playerTitle => translate('playerTitle');

  /// Returns the localized settings screen title.
  String get settingsTitle => translate('settingsTitle');

  /// Returns the localized debug screen title.
  String get debugTitle => translate('debugTitle');

  /// Returns the localized mirrors screen title.
  String get mirrorsTitle => translate('mirrorsTitle');

  /// Returns the localized topic screen title.
  String get topicTitle => translate('topicTitle');

  /// Returns the localized search placeholder text.
  String get searchPlaceholder => translate('searchPlaceholder');

  /// Returns the localized error message for failed search.
  String get failedToSearch => translate('failedToSearch');

  /// Returns the localized indicator for cached results.
  String get resultsFromCache => translate('resultsFromCache');

  /// Returns the localized prompt to start searching.
  String get enterSearchTerm => translate('enterSearchTerm');

  /// Returns the localized message for no results found.
  String get noResults => translate('noResults');

  /// Returns the localized loading text.
  String get loading => translate('loading');

  /// Returns the localized error message title.
  String get error => translate('error');

  /// Returns the localized retry button text.
  String get retry => translate('retry');

  /// Returns the localized language setting label.
  String get language => translate('language');

  /// Returns the localized English language option.
  String get english => translate('english');

  /// Returns the localized Russian language option.
  String get russian => translate('russian');

  /// Returns the localized system default language option.
  String get systemDefault => translate('systemDefault');

  /// Returns the localized author field label.
  String get authorLabel => translate('authorLabel');

  /// Returns the localized size field label.
  String get sizeLabel => translate('sizeLabel');

  /// Returns the localized seeders count label.
  String get seedersLabel => translate('seedersLabel');

  /// Returns the localized leechers count label.
  String get leechersLabel => translate('leechersLabel');

  /// Returns the localized fallback text for unknown title.
  String get unknownTitle => translate('unknownTitle');

  /// Returns the localized fallback text for unknown author.
  String get unknownAuthor => translate('unknownAuthor');

  /// Returns the localized fallback text for unknown size.
  String get unknownSize => translate('unknownSize');
}

/// Delegate for loading AppLocalizations instances.
class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  /// Creates a new _AppLocalizationsDelegate instance.
  const _AppLocalizationsDelegate();

  @override
  bool isSupported(Locale locale) => ['en', 'ru'].contains(locale.languageCode);

  @override
  Future<AppLocalizations> load(Locale locale) async {
    final localizations = AppLocalizations(locale);
    await localizations.load();
    return localizations;
  }

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}