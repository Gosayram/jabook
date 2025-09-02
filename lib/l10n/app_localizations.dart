import 'dart:async';
import 'dart:convert';

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
        'assets/lib/l10n/app_${locale.languageCode}.arb',
      );
      
      final jsonMap = json.decode(jsonString) as Map<String, dynamic>;
      
      _localizedStrings = jsonMap.map((key, value) {
        if (key.startsWith('@')) return MapEntry(key, value.toString());
        return MapEntry(key, value.toString());
      });
      
      return true;
    } on Exception catch (_) {
      return false;
    }
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

  /// Returns the localized message for upcoming add book feature.
  String get addBookComingSoon => translate('addBookComingSoon');

  /// Returns the localized placeholder text for library screen.
  String get libraryContentPlaceholder => translate('libraryContentPlaceholder');

  /// Returns the localized authentication required title.
  String get authenticationRequired => translate('authenticationRequired');

  /// Returns the localized login required message for search.
  String get loginRequiredForSearch => translate('loginRequiredForSearch');

  /// Returns the localized cancel button text.
  String get cancel => translate('cancel');

  /// Returns the localized login button text.
  String get login => translate('login');

  /// Returns the localized language description.
  String get languageDescription => translate('languageDescription');

  /// Returns the localized theme section title.
  String get themeTitle => translate('themeTitle');

  /// Returns the localized theme section description.
  String get themeDescription => translate('themeDescription');

  /// Returns the localized dark mode setting label.
  String get darkMode => translate('darkMode');

  /// Returns the localized high contrast setting label.
  String get highContrast => translate('highContrast');

  /// Returns the localized audio section title.
  String get audioTitle => translate('audioTitle');

  /// Returns the localized audio section description.
  String get audioDescription => translate('audioDescription');

  /// Returns the localized playback speed setting label.
  String get playbackSpeed => translate('playbackSpeed');

  /// Returns the localized skip duration setting label.
  String get skipDuration => translate('skipDuration');

  /// Returns the localized downloads section title.
  String get downloadsTitle => translate('downloadsTitle');

  /// Returns the localized downloads section description.
  String get downloadsDescription => translate('downloadsDescription');

  /// Returns the localized download location setting label.
  String get downloadLocation => translate('downloadLocation');

  /// Returns the localized Wi-Fi only downloads setting label.
  String get wifiOnlyDownloads => translate('wifiOnlyDownloads');

  /// Returns the localized debug tools title.
  String get debugTools => translate('debugTools');

  /// Returns the localized logs tab label.
  String get logsTab => translate('logsTab');

  /// Returns the localized mirrors tab label.
  String get mirrorsTab => translate('mirrorsTab');

  /// Returns the localized downloads tab label.
  String get downloadsTab => translate('downloadsTab');

  /// Returns the localized cache tab label.
  String get cacheTab => translate('cacheTab');

  /// Returns the localized test all mirrors button text.
  String get testAllMirrors => translate('testAllMirrors');

  /// Returns the localized status label.
  String get statusLabel => translate('statusLabel');

  /// Returns the localized active status text.
  String get activeStatus => translate('activeStatus');

  /// Returns the localized disabled status text.
  String get disabledStatus => translate('disabledStatus');

  /// Returns the localized last OK label.
  String get lastOkLabel => translate('lastOkLabel');

  /// Returns the localized RTT label.
  String get rttLabel => translate('rttLabel');

  /// Returns the localized milliseconds abbreviation.
  String get milliseconds => translate('milliseconds');

  /// Returns the localized cache statistics title.
  String get cacheStatistics => translate('cacheStatistics');

  /// Returns the localized total entries label.
  String get totalEntries => translate('totalEntries');

  /// Returns the localized search cache label.
  String get searchCache => translate('searchCache');

  /// Returns the localized topic cache label.
  String get topicCache => translate('topicCache');

  /// Returns the localized memory usage label.
  String get memoryUsage => translate('memoryUsage');

  /// Returns the localized clear all cache button text.
  String get clearAllCache => translate('clearAllCache');

  /// Returns the localized cache cleared success message.
  String get cacheClearedSuccessfully => translate('cacheClearedSuccessfully');

  /// Returns the localized mirror health check completion message.
  String get mirrorHealthCheckCompleted => translate('mirrorHealthCheckCompleted');

  /// Returns the localized export functionality coming soon message.
  String get exportFunctionalityComingSoon => translate('exportFunctionalityComingSoon');
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