import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_en.dart';
import 'app_localizations_ru.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'l10n/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale) : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations? of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations);
  }

  static const LocalizationsDelegate<AppLocalizations> delegate = _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates = <LocalizationsDelegate<dynamic>>[
    delegate,
    GlobalMaterialLocalizations.delegate,
    GlobalCupertinoLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate,
  ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[
    Locale('en'),
    Locale('ru')
  ];

  /// No description provided for @appTitle.
  ///
  /// In en, this message translates to:
  /// **'JaBook'**
  String get appTitle;

  /// Title for search screen
  ///
  /// In en, this message translates to:
  /// **'Search Audiobooks'**
  String get searchAudiobooks;

  /// Placeholder text for search field
  ///
  /// In en, this message translates to:
  /// **'Enter title, author, or keywords'**
  String get searchPlaceholder;

  /// Title for library screen
  ///
  /// In en, this message translates to:
  /// **'Library'**
  String get libraryTitle;

  /// Title for player screen
  ///
  /// In en, this message translates to:
  /// **'Player'**
  String get playerTitle;

  /// Title for settings screen
  ///
  /// In en, this message translates to:
  /// **'Settings'**
  String get settingsTitle;

  /// Title for debug screen
  ///
  /// In en, this message translates to:
  /// **'Debug'**
  String get debugTitle;

  /// Title for mirrors screen
  ///
  /// In en, this message translates to:
  /// **'Mirrors'**
  String get mirrorsTitle;

  /// Title for topic screen
  ///
  /// In en, this message translates to:
  /// **'Topic'**
  String get topicTitle;

  /// Message when no search results
  ///
  /// In en, this message translates to:
  /// **'No results found'**
  String get noResults;

  /// Loading indicator text
  ///
  /// In en, this message translates to:
  /// **'Loading...'**
  String get loading;

  /// Error message title
  ///
  /// In en, this message translates to:
  /// **'Error'**
  String get error;

  /// Retry button text
  ///
  /// In en, this message translates to:
  /// **'Retry'**
  String get retry;

  /// Language setting label
  ///
  /// In en, this message translates to:
  /// **'Language'**
  String get language;

  /// English language option
  ///
  /// In en, this message translates to:
  /// **'English'**
  String get english;

  /// Russian language option
  ///
  /// In en, this message translates to:
  /// **'Russian'**
  String get russian;

  /// Use system language setting
  ///
  /// In en, this message translates to:
  /// **'System Default'**
  String get systemDefault;

  /// Description for language settings section
  ///
  /// In en, this message translates to:
  /// **'Choose your preferred language for the app interface'**
  String get languageDescription;

  /// Error message when search fails
  ///
  /// In en, this message translates to:
  /// **'Failed to search'**
  String get failedToSearch;

  /// Indicator that results are from cache
  ///
  /// In en, this message translates to:
  /// **'Results from cache'**
  String get resultsFromCache;

  /// Prompt to start searching
  ///
  /// In en, this message translates to:
  /// **'Enter a search term to begin'**
  String get enterSearchTerm;

  /// Label for author field
  ///
  /// In en, this message translates to:
  /// **'Author: '**
  String get authorLabel;

  /// Label for size field
  ///
  /// In en, this message translates to:
  /// **'Size: '**
  String get sizeLabel;

  /// Label for seeders count
  ///
  /// In en, this message translates to:
  /// **' seeders'**
  String get seedersLabel;

  /// Label for leechers count
  ///
  /// In en, this message translates to:
  /// **' leechers'**
  String get leechersLabel;

  /// Fallback for missing title
  ///
  /// In en, this message translates to:
  /// **'Unknown Title'**
  String get unknownTitle;

  /// Fallback for missing author
  ///
  /// In en, this message translates to:
  /// **'Unknown Author'**
  String get unknownAuthor;

  /// Fallback for missing size
  ///
  /// In en, this message translates to:
  /// **'Unknown Size'**
  String get unknownSize;

  /// Message for upcoming add book feature
  ///
  /// In en, this message translates to:
  /// **'Add book functionality coming soon!'**
  String get addBookComingSoon;

  /// Placeholder text for library screen
  ///
  /// In en, this message translates to:
  /// **'Library content will be displayed here'**
  String get libraryContentPlaceholder;

  /// Title for authentication prompt dialog
  ///
  /// In en, this message translates to:
  /// **'Authentication Required'**
  String get authenticationRequired;

  /// Message explaining that login is required for search
  ///
  /// In en, this message translates to:
  /// **'Please login to RuTracker to access search functionality.'**
  String get loginRequiredForSearch;

  /// Cancel button text
  ///
  /// In en, this message translates to:
  /// **'Cancel'**
  String get cancel;

  /// Login button text
  ///
  /// In en, this message translates to:
  /// **'Login'**
  String get login;

  /// Title for theme settings section
  ///
  /// In en, this message translates to:
  /// **'Theme'**
  String get themeTitle;

  /// Description for theme settings section
  ///
  /// In en, this message translates to:
  /// **'Customize the appearance of the app'**
  String get themeDescription;

  /// Dark mode toggle label
  ///
  /// In en, this message translates to:
  /// **'Dark Mode'**
  String get darkMode;

  /// High contrast mode toggle label
  ///
  /// In en, this message translates to:
  /// **'High Contrast'**
  String get highContrast;

  /// Title for audio settings section
  ///
  /// In en, this message translates to:
  /// **'Audio'**
  String get audioTitle;

  /// Description for audio settings section
  ///
  /// In en, this message translates to:
  /// **'Configure audio playback settings'**
  String get audioDescription;

  /// Playback speed setting label
  ///
  /// In en, this message translates to:
  /// **'Playback Speed'**
  String get playbackSpeed;

  /// Skip duration setting label
  ///
  /// In en, this message translates to:
  /// **'Skip Duration'**
  String get skipDuration;

  /// Title for downloads settings section
  ///
  /// In en, this message translates to:
  /// **'Downloads'**
  String get downloadsTitle;

  /// Description for downloads settings section
  ///
  /// In en, this message translates to:
  /// **'Manage download preferences and storage'**
  String get downloadsDescription;

  /// Download location setting label
  ///
  /// In en, this message translates to:
  /// **'Download Location'**
  String get downloadLocation;

  /// Wi-Fi only downloads toggle label
  ///
  /// In en, this message translates to:
  /// **'Wi-Fi Only Downloads'**
  String get wifiOnlyDownloads;

  /// Title for debug screen
  ///
  /// In en, this message translates to:
  /// **'Debug Tools'**
  String get debugTools;

  /// Logs tab label
  ///
  /// In en, this message translates to:
  /// **'Logs'**
  String get logsTab;

  /// Mirrors tab label
  ///
  /// In en, this message translates to:
  /// **'Mirrors'**
  String get mirrorsTab;

  /// Downloads tab label
  ///
  /// In en, this message translates to:
  /// **'Downloads'**
  String get downloadsTab;

  /// Cache tab label
  ///
  /// In en, this message translates to:
  /// **'Cache'**
  String get cacheTab;

  /// Test all mirrors button text
  ///
  /// In en, this message translates to:
  /// **'Test All Mirrors'**
  String get testAllMirrors;

  /// Status label text
  ///
  /// In en, this message translates to:
  /// **'Status: '**
  String get statusLabel;

  /// Active status text
  ///
  /// In en, this message translates to:
  /// **'Active'**
  String get activeStatus;

  /// Disabled status text
  ///
  /// In en, this message translates to:
  /// **'Disabled'**
  String get disabledStatus;

  /// Last OK label text
  ///
  /// In en, this message translates to:
  /// **'Last OK: '**
  String get lastOkLabel;

  /// RTT label text
  ///
  /// In en, this message translates to:
  /// **'RTT: '**
  String get rttLabel;

  /// Milliseconds abbreviation
  ///
  /// In en, this message translates to:
  /// **'ms'**
  String get milliseconds;

  /// Cache statistics title
  ///
  /// In en, this message translates to:
  /// **'Cache Statistics'**
  String get cacheStatistics;

  /// Total entries label
  ///
  /// In en, this message translates to:
  /// **'Total entries: '**
  String get totalEntries;

  /// Search cache label
  ///
  /// In en, this message translates to:
  /// **'Search cache: '**
  String get searchCache;

  /// Topic cache label
  ///
  /// In en, this message translates to:
  /// **'Topic cache: '**
  String get topicCache;

  /// Memory usage label
  ///
  /// In en, this message translates to:
  /// **'Memory usage: '**
  String get memoryUsage;

  /// Clear all cache button text
  ///
  /// In en, this message translates to:
  /// **'Clear All Cache'**
  String get clearAllCache;

  /// Cache cleared success message
  ///
  /// In en, this message translates to:
  /// **'Cache cleared successfully'**
  String get cacheClearedSuccessfully;

  /// Mirror health check completion message
  ///
  /// In en, this message translates to:
  /// **'Mirror health check completed'**
  String get mirrorHealthCheckCompleted;

  /// Export functionality coming soon message
  ///
  /// In en, this message translates to:
  /// **'Export functionality coming soon'**
  String get exportFunctionalityComingSoon;

  /// Logs exported success message
  ///
  /// In en, this message translates to:
  /// **'Logs exported successfully'**
  String get logsExportedSuccessfully;

  /// Logs export failure message
  ///
  /// In en, this message translates to:
  /// **'Failed to export logs'**
  String get failedToExportLogs;

  /// Network timeout error message
  ///
  /// In en, this message translates to:
  /// **'Request timed out. Please check your connection.'**
  String get requestTimedOut;

  /// Generic network error message
  ///
  /// In en, this message translates to:
  /// **'Network error: {errorMessage}'**
  String networkError(Object errorMessage);

  /// Download feature coming soon message
  ///
  /// In en, this message translates to:
  /// **'Download functionality coming soon!'**
  String get downloadFunctionalityComingSoon;

  /// Magnet link copied success message
  ///
  /// In en, this message translates to:
  /// **'Magnet link copied to clipboard'**
  String get magnetLinkCopied;

  /// Cache data loaded message
  ///
  /// In en, this message translates to:
  /// **'Data loaded from cache'**
  String get dataLoadedFromCache;

  /// Topic loading failure message
  ///
  /// In en, this message translates to:
  /// **'Failed to load topic'**
  String get failedToLoadTopic;

  /// Topic loading error message
  ///
  /// In en, this message translates to:
  /// **'Error loading topic: {error}'**
  String errorLoadingTopic(Object error);

  /// Fallback for missing chapter title
  ///
  /// In en, this message translates to:
  /// **'Unknown Chapter'**
  String get unknownChapter;

  /// Label for chapters section
  ///
  /// In en, this message translates to:
  /// **'Chapters'**
  String get chaptersLabel;

  /// Label for magnet link
  ///
  /// In en, this message translates to:
  /// **'Magnet Link'**
  String get magnetLinkLabel;

  /// Download button label
  ///
  /// In en, this message translates to:
  /// **'Download'**
  String get downloadLabel;

  /// Play button label
  ///
  /// In en, this message translates to:
  /// **'Play'**
  String get playLabel;

  /// Pause button label
  ///
  /// In en, this message translates to:
  /// **'Pause'**
  String get pauseLabel;

  /// Stop button label
  ///
  /// In en, this message translates to:
  /// **'Stop'**
  String get stopLabel;

  /// Audiobook loading failure message
  ///
  /// In en, this message translates to:
  /// **'Failed to load audiobook'**
  String get failedToLoadAudio;

  /// Sample audiobook title
  ///
  /// In en, this message translates to:
  /// **'Sample Audiobook'**
  String get sampleAudiobook;

  /// Sample author name
  ///
  /// In en, this message translates to:
  /// **'Sample Author'**
  String get sampleAuthor;

  /// Chapter 1 title
  ///
  /// In en, this message translates to:
  /// **'Chapter 1'**
  String get chapter1;

  /// Chapter 2 title
  ///
  /// In en, this message translates to:
  /// **'Chapter 2'**
  String get chapter2;

  /// Title for mirrors screen
  ///
  /// In en, this message translates to:
  /// **'Mirrors Screen'**
  String get mirrorsScreenTitle;

  /// Unknown download status
  ///
  /// In en, this message translates to:
  /// **'unknown'**
  String get downloadStatusUnknown;

  /// Download progress label
  ///
  /// In en, this message translates to:
  /// **'Progress: {progress}%'**
  String downloadProgressLabel(Object progress);

  /// Status label without colon
  ///
  /// In en, this message translates to:
  /// **'Status'**
  String get statusLabelNoColon;

  /// Copy to clipboard success message
  ///
  /// In en, this message translates to:
  /// **'{label} copied to clipboard'**
  String copyToClipboardLabel(Object label);

  /// Short navigation label for search
  ///
  /// In en, this message translates to:
  /// **'Search'**
  String get navSearch;

  /// Short navigation label for library
  ///
  /// In en, this message translates to:
  /// **'Library'**
  String get navLibrary;

  /// Short navigation label for settings
  ///
  /// In en, this message translates to:
  /// **'Settings'**
  String get navSettings;

  /// Short navigation label for debug
  ///
  /// In en, this message translates to:
  /// **'Debug'**
  String get navDebug;

  /// Active mirror status text
  ///
  /// In en, this message translates to:
  /// **'Active'**
  String get mirrorStatusActive;

  /// Inactive mirror status text
  ///
  /// In en, this message translates to:
  /// **'Inactive'**
  String get mirrorStatusInactive;

  /// Disabled mirror status text
  ///
  /// In en, this message translates to:
  /// **'Disabled'**
  String get mirrorStatusDisabled;

  /// Individual mirror test button text
  ///
  /// In en, this message translates to:
  /// **'Test this mirror'**
  String get mirrorTestIndividual;

  /// Label for mirror domain
  ///
  /// In en, this message translates to:
  /// **'Domain'**
  String get mirrorDomainLabel;

  /// Label for response time
  ///
  /// In en, this message translates to:
  /// **'Response time'**
  String get mirrorResponseTime;

  /// Label for last check time
  ///
  /// In en, this message translates to:
  /// **'Last checked'**
  String get mirrorLastCheck;
}

class _AppLocalizationsDelegate extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) => <String>['en', 'ru'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {


  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'en': return AppLocalizationsEn();
    case 'ru': return AppLocalizationsRu();
  }

  throw FlutterError(
    'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.'
  );
}
