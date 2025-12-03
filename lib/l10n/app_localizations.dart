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

  /// Message when no results match selected category filters
  ///
  /// In en, this message translates to:
  /// **'No results match the selected filters'**
  String get noResultsForFilters;

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

  /// Indicator that results are from local database
  ///
  /// In en, this message translates to:
  /// **'Results from local database'**
  String get resultsFromLocalDb;

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

  /// Button label for reset action
  ///
  /// In en, this message translates to:
  /// **'Reset'**
  String get reset;

  /// Menu item to reset book settings to global defaults
  ///
  /// In en, this message translates to:
  /// **'Reset to global settings'**
  String get resetToGlobalSettings;

  /// Button label to reset all individual book settings
  ///
  /// In en, this message translates to:
  /// **'Reset all book settings'**
  String get resetAllBookSettings;

  /// Description for reset all book settings action
  ///
  /// In en, this message translates to:
  /// **'Remove individual settings for all books'**
  String get resetAllBookSettingsDescription;

  /// Confirmation message for resetting all book settings
  ///
  /// In en, this message translates to:
  /// **'This will remove individual audio settings for all books. All books will use global settings. This action cannot be undone.'**
  String get resetAllBookSettingsConfirmation;

  /// Message shown when book settings are reset
  ///
  /// In en, this message translates to:
  /// **'Settings reset to global defaults'**
  String get settingsResetToGlobal;

  /// Message shown when all book settings are reset
  ///
  /// In en, this message translates to:
  /// **'All book settings have been reset to global defaults'**
  String get allBookSettingsReset;

  /// Error message when resetting settings fails
  ///
  /// In en, this message translates to:
  /// **'Error resetting settings'**
  String get errorResettingSettings;

  /// Login button text
  ///
  /// In en, this message translates to:
  /// **'Login'**
  String get login;

  /// User-friendly network connection error message
  ///
  /// In en, this message translates to:
  /// **'Could not connect. Check your internet or choose another mirror in Settings → Sources.'**
  String get networkConnectionError;

  /// Generic connection failure message
  ///
  /// In en, this message translates to:
  /// **'Connection failed. Please check your internet connection or try a different mirror.'**
  String get connectionFailed;

  /// Button text for choosing mirror
  ///
  /// In en, this message translates to:
  /// **'Choose Mirror'**
  String get chooseMirror;

  /// User-friendly network error title
  ///
  /// In en, this message translates to:
  /// **'Network connection issue'**
  String get networkErrorUser;

  /// DNS resolution error message
  ///
  /// In en, this message translates to:
  /// **'Could not resolve domain. This may be due to network restrictions or an inactive mirror.'**
  String get dnsError;

  /// Timeout error message
  ///
  /// In en, this message translates to:
  /// **'Request took too long. Please check your connection and try again.'**
  String get timeoutError;

  /// Server error message
  ///
  /// In en, this message translates to:
  /// **'Server is temporarily unavailable. Please try again later or choose another mirror.'**
  String get serverError;

  /// Title for recent searches section
  ///
  /// In en, this message translates to:
  /// **'Recent Searches'**
  String get recentSearches;

  /// Title for search examples section
  ///
  /// In en, this message translates to:
  /// **'Try these examples'**
  String get searchExamples;

  /// Title for quick actions section
  ///
  /// In en, this message translates to:
  /// **'Quick Actions'**
  String get quickActions;

  /// Label for scan local files action
  ///
  /// In en, this message translates to:
  /// **'Scan Local Files'**
  String get scanLocalFiles;

  /// Message for upcoming features
  ///
  /// In en, this message translates to:
  /// **'Feature coming soon'**
  String get featureComingSoon;

  /// Label for change mirror action
  ///
  /// In en, this message translates to:
  /// **'Change Mirror'**
  String get changeMirror;

  /// Label for check connection action
  ///
  /// In en, this message translates to:
  /// **'Check Connection'**
  String get checkConnection;

  /// Title for permissions required dialog
  ///
  /// In en, this message translates to:
  /// **'Permissions Required'**
  String get permissionsRequired;

  /// Explanation for why permissions are needed
  ///
  /// In en, this message translates to:
  /// **'This app needs storage permission to download and save audiobook files. Please grant the required permissions to continue.'**
  String get permissionExplanation;

  /// Button text to grant permissions
  ///
  /// In en, this message translates to:
  /// **'Grant Permissions'**
  String get grantPermissions;

  /// Title for permission denied dialog
  ///
  /// In en, this message translates to:
  /// **'Permission Denied'**
  String get permissionDeniedTitle;

  /// Message for permission denied dialog
  ///
  /// In en, this message translates to:
  /// **'Storage permission is required to download files. Please enable it in app settings.'**
  String get permissionDeniedMessage;

  /// Button text for permission denied action
  ///
  /// In en, this message translates to:
  /// **'Open Settings'**
  String get permissionDeniedButton;

  /// Navigation label for library
  ///
  /// In en, this message translates to:
  /// **'Library'**
  String get navLibrary;

  /// Navigation label for authentication
  ///
  /// In en, this message translates to:
  /// **'Connect'**
  String get navAuth;

  /// Navigation label for search
  ///
  /// In en, this message translates to:
  /// **'Search'**
  String get navSearch;

  /// Navigation label for settings
  ///
  /// In en, this message translates to:
  /// **'Settings'**
  String get navSettings;

  /// Navigation label for debug
  ///
  /// In en, this message translates to:
  /// **'Debug'**
  String get navDebug;

  /// Title for authentication screen
  ///
  /// In en, this message translates to:
  /// **'Login'**
  String get authTitle;

  /// Label for username field
  ///
  /// In en, this message translates to:
  /// **'Username'**
  String get usernameLabel;

  /// Label for password field
  ///
  /// In en, this message translates to:
  /// **'Password'**
  String get passwordLabel;

  /// Label for remember me checkbox
  ///
  /// In en, this message translates to:
  /// **'Remember me'**
  String get rememberMeLabel;

  /// Login button text
  ///
  /// In en, this message translates to:
  /// **'Login'**
  String get loginButton;

  /// Test connection button text
  ///
  /// In en, this message translates to:
  /// **'Test Connection'**
  String get testConnectionButton;

  /// Logout button text
  ///
  /// In en, this message translates to:
  /// **'Logout'**
  String get logoutButton;

  /// Message for successful cache clearing
  ///
  /// In en, this message translates to:
  /// **'Cache cleared successfully'**
  String get cacheClearedSuccessfully;

  /// Message for unknown download status
  ///
  /// In en, this message translates to:
  /// **'Download status unknown'**
  String get downloadStatusUnknown;

  /// Message for failed log export
  ///
  /// In en, this message translates to:
  /// **'Failed to export logs'**
  String get failedToExportLogs;

  /// Message for successful log export
  ///
  /// In en, this message translates to:
  /// **'Logs exported successfully'**
  String get logsExportedSuccessfully;

  /// Message for completed mirror health check
  ///
  /// In en, this message translates to:
  /// **'Mirror health check completed'**
  String get mirrorHealthCheckCompleted;

  /// Test all mirrors button text
  ///
  /// In en, this message translates to:
  /// **'Test All Mirrors'**
  String get testAllMirrors;

  /// Label for status
  ///
  /// In en, this message translates to:
  /// **'Status:'**
  String get statusLabel;

  /// Label for last OK time
  ///
  /// In en, this message translates to:
  /// **'Last OK:'**
  String get lastOkLabel;

  /// Label for round trip time
  ///
  /// In en, this message translates to:
  /// **'RTT:'**
  String get rttLabel;

  /// Abbreviation for milliseconds
  ///
  /// In en, this message translates to:
  /// **'ms'**
  String get milliseconds;

  /// Label for download
  ///
  /// In en, this message translates to:
  /// **'Download'**
  String get downloadLabel;

  /// Status label without colon
  ///
  /// In en, this message translates to:
  /// **'Status'**
  String get statusLabelNoColon;

  /// Label for download progress
  ///
  /// In en, this message translates to:
  /// **'Progress'**
  String get downloadProgressLabel;

  /// Title for cache statistics
  ///
  /// In en, this message translates to:
  /// **'Cache Statistics'**
  String get cacheStatistics;

  /// Label for total entries
  ///
  /// In en, this message translates to:
  /// **'Total entries'**
  String get totalEntries;

  /// Label for search cache
  ///
  /// In en, this message translates to:
  /// **'Search cache'**
  String get searchCache;

  /// Label for topic cache
  ///
  /// In en, this message translates to:
  /// **'Topic cache'**
  String get topicCache;

  /// Label for memory usage
  ///
  /// In en, this message translates to:
  /// **'Memory usage'**
  String get memoryUsage;

  /// Clear all cache button text
  ///
  /// In en, this message translates to:
  /// **'Clear All Cache'**
  String get clearAllCache;

  /// Title for mirrors screen
  ///
  /// In en, this message translates to:
  /// **'Mirrors'**
  String get mirrorsScreenTitle;

  /// Message for failed audio loading
  ///
  /// In en, this message translates to:
  /// **'Failed to load audio'**
  String get failedToLoadAudio;

  /// Label for chapters
  ///
  /// In en, this message translates to:
  /// **'Chapters'**
  String get chaptersLabel;

  /// Message for upcoming download feature
  ///
  /// In en, this message translates to:
  /// **'Download functionality coming soon'**
  String get downloadFunctionalityComingSoon;

  /// Message for request timeout
  ///
  /// In en, this message translates to:
  /// **'Request timed out'**
  String get requestTimedOut;

  /// Message for network error
  ///
  /// In en, this message translates to:
  /// **'Network error'**
  String get networkError;

  /// Status for disabled mirror
  ///
  /// In en, this message translates to:
  /// **'Disabled'**
  String get mirrorStatusDisabled;

  /// Label for mirror response time
  ///
  /// In en, this message translates to:
  /// **'Response time'**
  String get mirrorResponseTime;

  /// Label for last check time
  ///
  /// In en, this message translates to:
  /// **'Last check'**
  String get mirrorLastCheck;

  /// Text for testing individual mirror
  ///
  /// In en, this message translates to:
  /// **'Test individual mirror'**
  String get mirrorTestIndividual;

  /// Status for active mirror
  ///
  /// In en, this message translates to:
  /// **'Active'**
  String get activeStatus;

  /// Status for disabled mirror
  ///
  /// In en, this message translates to:
  /// **'Disabled'**
  String get disabledStatus;

  /// Description for language setting
  ///
  /// In en, this message translates to:
  /// **'Select language'**
  String get languageDescription;

  /// Title for theme setting
  ///
  /// In en, this message translates to:
  /// **'Theme'**
  String get themeTitle;

  /// Description for theme setting
  ///
  /// In en, this message translates to:
  /// **'Select app theme'**
  String get themeDescription;

  /// Label for dark mode
  ///
  /// In en, this message translates to:
  /// **'Dark mode'**
  String get darkMode;

  /// Label for high contrast
  ///
  /// In en, this message translates to:
  /// **'High contrast'**
  String get highContrast;

  /// Title for audio settings
  ///
  /// In en, this message translates to:
  /// **'Audio'**
  String get audioTitle;

  /// Description for audio settings
  ///
  /// In en, this message translates to:
  /// **'Audio settings'**
  String get audioDescription;

  /// Title for audio enhancement settings
  ///
  /// In en, this message translates to:
  /// **'Audio Enhancement'**
  String get audioEnhancementTitle;

  /// Description for audio enhancement settings
  ///
  /// In en, this message translates to:
  /// **'Improve audio quality and volume consistency'**
  String get audioEnhancementDescription;

  /// Title for normalize volume setting
  ///
  /// In en, this message translates to:
  /// **'Normalize Volume'**
  String get normalizeVolumeTitle;

  /// Description for normalize volume setting
  ///
  /// In en, this message translates to:
  /// **'Maintain consistent volume across different audiobooks'**
  String get normalizeVolumeDescription;

  /// Title for volume boost setting
  ///
  /// In en, this message translates to:
  /// **'Volume Boost'**
  String get volumeBoostTitle;

  /// Title for DRC level setting
  ///
  /// In en, this message translates to:
  /// **'Dynamic Range Compression'**
  String get drcLevelTitle;

  /// Title for speech enhancer setting
  ///
  /// In en, this message translates to:
  /// **'Speech Enhancer'**
  String get speechEnhancerTitle;

  /// Description for speech enhancer setting
  ///
  /// In en, this message translates to:
  /// **'Improve speech clarity and reduce sibilance'**
  String get speechEnhancerDescription;

  /// Title for auto volume leveling setting
  ///
  /// In en, this message translates to:
  /// **'Auto Volume Leveling'**
  String get autoVolumeLevelingTitle;

  /// Description for auto volume leveling setting
  ///
  /// In en, this message translates to:
  /// **'Automatically adjust volume to maintain consistent level'**
  String get autoVolumeLevelingDescription;

  /// Label for playback speed
  ///
  /// In en, this message translates to:
  /// **'Playback speed'**
  String get playbackSpeed;

  /// Label for skip duration
  ///
  /// In en, this message translates to:
  /// **'Skip duration'**
  String get skipDuration;

  /// Title for downloads settings
  ///
  /// In en, this message translates to:
  /// **'Downloads'**
  String get downloadsTitle;

  /// Description for downloads settings
  ///
  /// In en, this message translates to:
  /// **'Download settings'**
  String get downloadsDescription;

  /// Label for download location
  ///
  /// In en, this message translates to:
  /// **'Download location'**
  String get downloadLocation;

  /// Label for WiFi only downloads
  ///
  /// In en, this message translates to:
  /// **'WiFi only downloads'**
  String get wifiOnlyDownloads;

  /// Message for data loaded from cache
  ///
  /// In en, this message translates to:
  /// **'Data loaded from cache'**
  String get dataLoadedFromCache;

  /// Message for failed topic loading
  ///
  /// In en, this message translates to:
  /// **'Failed to load topic'**
  String get failedToLoadTopic;

  /// Label for magnet link
  ///
  /// In en, this message translates to:
  /// **'Magnet link'**
  String get magnetLinkLabel;

  /// Fallback for missing chapter
  ///
  /// In en, this message translates to:
  /// **'Unknown chapter'**
  String get unknownChapter;

  /// Message for magnet link copied
  ///
  /// In en, this message translates to:
  /// **'Magnet link copied'**
  String get magnetLinkCopied;

  /// Label for copy to clipboard
  ///
  /// In en, this message translates to:
  /// **'Copy to clipboard'**
  String get copyToClipboardLabel;

  /// Title for debug tools screen
  ///
  /// In en, this message translates to:
  /// **'Debug Tools'**
  String get debugToolsTitle;

  /// Text for logs tab
  ///
  /// In en, this message translates to:
  /// **'Logs'**
  String get logsTab;

  /// Text for mirrors tab
  ///
  /// In en, this message translates to:
  /// **'Mirrors'**
  String get mirrorsTab;

  /// Text for downloads tab
  ///
  /// In en, this message translates to:
  /// **'Downloads'**
  String get downloadsTab;

  /// Text for cache tab
  ///
  /// In en, this message translates to:
  /// **'Cache'**
  String get cacheTab;

  /// Text for test all mirrors button
  ///
  /// In en, this message translates to:
  /// **'Test All Mirrors'**
  String get testAllMirrorsButton;

  /// Label for displaying status
  ///
  /// In en, this message translates to:
  /// **'Status: '**
  String get statusLabelText;

  /// Label for last OK time
  ///
  /// In en, this message translates to:
  /// **'Last OK: '**
  String get lastOkLabelText;

  /// Label for round trip time
  ///
  /// In en, this message translates to:
  /// **'RTT: '**
  String get rttLabelText;

  /// Abbreviation for milliseconds
  ///
  /// In en, this message translates to:
  /// **'ms'**
  String get millisecondsText;

  /// Label for download
  ///
  /// In en, this message translates to:
  /// **'Download'**
  String get downloadLabelText;

  /// Status label without colon
  ///
  /// In en, this message translates to:
  /// **'Status'**
  String get statusLabelNoColonText;

  /// Label for download progress
  ///
  /// In en, this message translates to:
  /// **'Progress'**
  String get downloadProgressLabelText;

  /// Title for cache statistics section
  ///
  /// In en, this message translates to:
  /// **'Cache Statistics'**
  String get cacheStatisticsTitle;

  /// Label for total entries
  ///
  /// In en, this message translates to:
  /// **'Total entries: '**
  String get totalEntriesText;

  /// Label for search cache size
  ///
  /// In en, this message translates to:
  /// **'Search cache: '**
  String get searchCacheText;

  /// Label for topic cache size
  ///
  /// In en, this message translates to:
  /// **'Topic cache: '**
  String get topicCacheText;

  /// Label for memory usage
  ///
  /// In en, this message translates to:
  /// **'Memory usage: '**
  String get memoryUsageText;

  /// Text for clear cache button
  ///
  /// In en, this message translates to:
  /// **'Clear All Cache'**
  String get clearAllCacheButton;

  /// Message for empty cache
  ///
  /// In en, this message translates to:
  /// **'Cache is empty'**
  String get cacheIsEmptyMessage;

  /// Text for refresh debug data button
  ///
  /// In en, this message translates to:
  /// **'Refresh Debug Data'**
  String get refreshDebugDataButton;

  /// Text for export logs button
  ///
  /// In en, this message translates to:
  /// **'Export Logs'**
  String get exportLogsButton;

  /// Text for delete download button
  ///
  /// In en, this message translates to:
  /// **'Delete Download'**
  String get deleteDownloadButton;

  /// Text for done button
  ///
  /// In en, this message translates to:
  /// **'Done'**
  String get doneButtonText;

  /// Title for webview screen
  ///
  /// In en, this message translates to:
  /// **'RuTracker'**
  String get webViewTitle;

  /// Instruction banner text for WebView login screen
  ///
  /// In en, this message translates to:
  /// **'Please log in to RuTracker. After successful login, tap Done to extract cookies for the client.'**
  String get webViewLoginInstruction;

  /// Message shown during Cloudflare check
  ///
  /// In en, this message translates to:
  /// **'This site uses Cloudflare security checks. Please wait for the check to complete and interact with the page that opens if needed.'**
  String get cloudflareMessage;

  /// Text for retry button
  ///
  /// In en, this message translates to:
  /// **'Retry'**
  String get retryButtonText;

  /// Text for go home button
  ///
  /// In en, this message translates to:
  /// **'Go to Home'**
  String get goHomeButtonText;

  /// Message shown during browser check
  ///
  /// In en, this message translates to:
  /// **'The site is checking your browser - please wait for the check to complete on this page.'**
  String get browserCheckMessage;

  /// Title for download torrent dialog
  ///
  /// In en, this message translates to:
  /// **'Download Torrent'**
  String get downloadTorrentTitle;

  /// Content for download torrent dialog
  ///
  /// In en, this message translates to:
  /// **'Select action:'**
  String get selectActionText;

  /// Text for open button
  ///
  /// In en, this message translates to:
  /// **'Open'**
  String get openButtonText;

  /// Text for download button
  ///
  /// In en, this message translates to:
  /// **'Download'**
  String get downloadButtonText;

  /// Message for opening link in browser
  ///
  /// In en, this message translates to:
  /// **'To download the file, please open the link in your browser'**
  String get downloadInBrowserMessage;

  /// Title for import audiobooks dialog
  ///
  /// In en, this message translates to:
  /// **'Import Audiobooks'**
  String get importAudiobooksTitle;

  /// Content for import audiobooks dialog
  ///
  /// In en, this message translates to:
  /// **'Select audiobook files from your device to add to your library'**
  String get selectFilesMessage;

  /// Text for import button
  ///
  /// In en, this message translates to:
  /// **'Import'**
  String get importButtonText;

  /// Message for successful import
  ///
  /// In en, this message translates to:
  /// **'Imported \$count audiobook(s)'**
  String importedSuccess(int count);

  /// Message for no files selected
  ///
  /// In en, this message translates to:
  /// **'No files selected'**
  String get noFilesSelectedMessage;

  /// Message for import failure
  ///
  /// In en, this message translates to:
  /// **'Failed to import: \$error'**
  String importFailedMessage(String error);

  /// Title for scan folder dialog
  ///
  /// In en, this message translates to:
  /// **'Scan Folder'**
  String get scanFolderTitle;

  /// Content for scan folder dialog
  ///
  /// In en, this message translates to:
  /// **'Scan a folder on your device for audiobook files'**
  String get scanFolderMessage;

  /// Text for scan button
  ///
  /// In en, this message translates to:
  /// **'Scan'**
  String get scanButtonText;

  /// Message for successful scan
  ///
  /// In en, this message translates to:
  /// **'Found and imported \$count audiobook(s)'**
  String scanSuccessMessage(int count);

  /// Message for no audiobooks found
  ///
  /// In en, this message translates to:
  /// **'No audiobook files found in selected folder'**
  String get noAudiobooksFoundMessage;

  /// Message for no folder selected
  ///
  /// In en, this message translates to:
  /// **'No folder selected'**
  String get noFolderSelectedMessage;

  /// Message for scan failure
  ///
  /// In en, this message translates to:
  /// **'Failed to scan folder: \$error'**
  String scanFailedMessage(String error);

  /// Title for authentication screen
  ///
  /// In en, this message translates to:
  /// **'RuTracker Connection'**
  String get authScreenTitle;

  /// Label for username field
  ///
  /// In en, this message translates to:
  /// **'Username'**
  String get usernameLabelText;

  /// Label for password field
  ///
  /// In en, this message translates to:
  /// **'Password'**
  String get passwordLabelText;

  /// Label for remember me checkbox
  ///
  /// In en, this message translates to:
  /// **'Remember me'**
  String get rememberMeLabelText;

  /// Text for login button
  ///
  /// In en, this message translates to:
  /// **'Login'**
  String get loginButtonText;

  /// Text for test connection button
  ///
  /// In en, this message translates to:
  /// **'Test Connection'**
  String get testConnectionButtonText;

  /// Text for logout button
  ///
  /// In en, this message translates to:
  /// **'Logout'**
  String get logoutButtonText;

  /// Message during login
  ///
  /// In en, this message translates to:
  /// **'Logging in...'**
  String get loggingInText;

  /// Message for successful login
  ///
  /// In en, this message translates to:
  /// **'Login successful!'**
  String get loginSuccessMessage;

  /// Message for failed login
  ///
  /// In en, this message translates to:
  /// **'Login failed. Please check credentials'**
  String get loginFailedMessage;

  /// Message for login error
  ///
  /// In en, this message translates to:
  /// **'Login error: \$error'**
  String loginErrorMessage(String error);

  /// Message for successful connection test
  ///
  /// In en, this message translates to:
  /// **'Connection successful! Using: \$endpoint'**
  String connectionSuccessMessage(String endpoint);

  /// Message for failed connection test
  ///
  /// In en, this message translates to:
  /// **'Connection test failed: \$error'**
  String connectionFailedMessage(String error);

  /// Message during logout
  ///
  /// In en, this message translates to:
  /// **'Logging out...'**
  String get loggingOutText;

  /// Message for successful logout
  ///
  /// In en, this message translates to:
  /// **'Logout completed'**
  String get logoutSuccessMessage;

  /// Message for logout error
  ///
  /// In en, this message translates to:
  /// **'Logout error: \$error'**
  String logoutErrorMessage(String error);

  /// Subtitle for mirror configuration
  ///
  /// In en, this message translates to:
  /// **'Configure and test RuTracker mirrors'**
  String get configureMirrorsSubtitle;

  /// Title for playback speed setting
  ///
  /// In en, this message translates to:
  /// **'Playback Speed'**
  String get playbackSpeedTitle;

  /// Title for skip duration setting
  ///
  /// In en, this message translates to:
  /// **'Skip Duration'**
  String get skipDurationTitle;

  /// Title for download location setting
  ///
  /// In en, this message translates to:
  /// **'Download Location'**
  String get downloadLocationTitle;

  /// Title for dark mode setting
  ///
  /// In en, this message translates to:
  /// **'Dark Mode'**
  String get darkModeTitle;

  /// Title for high contrast setting
  ///
  /// In en, this message translates to:
  /// **'High Contrast'**
  String get highContrastTitle;

  /// Title for WiFi only downloads setting
  ///
  /// In en, this message translates to:
  /// **'WiFi Only Downloads'**
  String get wifiOnlyDownloadsTitle;

  /// Message for failed mirror loading
  ///
  /// In en, this message translates to:
  /// **'Failed to load mirrors: \$error'**
  String get failedToLoadMirrorsMessage;

  /// Message for successful mirror test
  ///
  /// In en, this message translates to:
  /// **'Mirror \$url tested successfully'**
  String get mirrorTestSuccessMessage;

  /// Message for failed mirror test
  ///
  /// In en, this message translates to:
  /// **'Failed to test mirror \$url: \$error'**
  String get mirrorTestFailedMessage;

  /// Mirror status text
  ///
  /// In en, this message translates to:
  /// **'Mirror \$status'**
  String get mirrorStatusText;

  /// Message for failed mirror update
  ///
  /// In en, this message translates to:
  /// **'Failed to update mirror: \$error'**
  String get failedToUpdateMirrorMessage;

  /// Title for add custom mirror dialog
  ///
  /// In en, this message translates to:
  /// **'Add Custom Mirror'**
  String get addCustomMirrorTitle;

  /// Label for mirror URL field
  ///
  /// In en, this message translates to:
  /// **'Mirror URL'**
  String get mirrorUrlLabelText;

  /// Hint for mirror URL field
  ///
  /// In en, this message translates to:
  /// **'https://rutracker.example.com'**
  String get mirrorUrlHintText;

  /// Label for priority field
  ///
  /// In en, this message translates to:
  /// **'Priority (1-10)'**
  String get priorityLabelText;

  /// Hint for priority field
  ///
  /// In en, this message translates to:
  /// **'5'**
  String get priorityHintText;

  /// Text for add mirror button
  ///
  /// In en, this message translates to:
  /// **'Add'**
  String get addMirrorButtonText;

  /// Message for successful mirror addition
  ///
  /// In en, this message translates to:
  /// **'Mirror \$url added'**
  String get mirrorAddedMessage;

  /// Message for failed mirror addition
  ///
  /// In en, this message translates to:
  /// **'Failed to add mirror: \$error'**
  String get failedToAddMirrorMessage;

  /// Title for mirror settings screen
  ///
  /// In en, this message translates to:
  /// **'Mirror Settings'**
  String get mirrorSettingsTitle;

  /// Description for mirror settings screen
  ///
  /// In en, this message translates to:
  /// **'Configure RuTracker mirrors for optimal search performance. Enabled mirrors will be used automatically.'**
  String get mirrorSettingsDescription;

  /// Text for add custom mirror button
  ///
  /// In en, this message translates to:
  /// **'Add Custom Mirror'**
  String get addCustomMirrorButtonText;

  /// Text for showing mirror priority
  ///
  /// In en, this message translates to:
  /// **'Priority: \$priority'**
  String priorityText(int priority);

  /// Text for showing mirror response time
  ///
  /// In en, this message translates to:
  /// **'Response time: \$rtt ms'**
  String get responseTimeText;

  /// Text for showing mirror last check time
  ///
  /// In en, this message translates to:
  /// **'Last checked: \$date'**
  String lastCheckedText(String date);

  /// Text for test mirror button
  ///
  /// In en, this message translates to:
  /// **'Test this mirror'**
  String get testMirrorButtonText;

  /// Status for active mirror
  ///
  /// In en, this message translates to:
  /// **'Active'**
  String get activeStatusText;

  /// Status for disabled mirror
  ///
  /// In en, this message translates to:
  /// **'Disabled'**
  String get disabledStatusText;

  /// Text for never
  ///
  /// In en, this message translates to:
  /// **'Never'**
  String get neverDateText;

  /// Text for invalid date
  ///
  /// In en, this message translates to:
  /// **'Invalid date'**
  String get invalidDateText;

  /// Title for player screen
  ///
  /// In en, this message translates to:
  /// **'Player'**
  String get playerScreenTitle;

  /// Message for failed audiobook loading
  ///
  /// In en, this message translates to:
  /// **'Failed to load audiobook'**
  String get failedToLoadAudioMessage;

  /// Text for showing author
  ///
  /// In en, this message translates to:
  /// **'by: \$author'**
  String get byAuthorText;

  /// Label for chapters section
  ///
  /// In en, this message translates to:
  /// **'Chapters'**
  String get chaptersLabelText;

  /// Message for upcoming download feature
  ///
  /// In en, this message translates to:
  /// **'Download functionality coming soon!'**
  String get downloadFunctionalityComingSoonMessage;

  /// Sample audiobook title
  ///
  /// In en, this message translates to:
  /// **'Sample Audiobook'**
  String get sampleTitleText;

  /// Sample audiobook author
  ///
  /// In en, this message translates to:
  /// **'Sample Author'**
  String get sampleAuthorText;

  /// Sample audiobook category
  ///
  /// In en, this message translates to:
  /// **'Fiction'**
  String get sampleCategoryText;

  /// Sample audiobook size
  ///
  /// In en, this message translates to:
  /// **'150 MB'**
  String get sampleSizeText;

  /// Sample chapter 1 title
  ///
  /// In en, this message translates to:
  /// **'Chapter 1'**
  String get sampleChapter1Text;

  /// Sample chapter 2 title
  ///
  /// In en, this message translates to:
  /// **'Chapter 2'**
  String get sampleChapter2Text;

  /// Title for topic screen
  ///
  /// In en, this message translates to:
  /// **'Topic'**
  String get topicScreenTitle;

  /// Message for request timeout
  ///
  /// In en, this message translates to:
  /// **'Request timed out. Check your connection.'**
  String get requestTimedOutMessage;

  /// Message for network error
  ///
  /// In en, this message translates to:
  /// **'Network error: \$error'**
  String networkErrorMessage(String error);

  /// Message for topic loading error
  ///
  /// In en, this message translates to:
  /// **'Error loading topic: \$error'**
  String errorLoadingTopicMessage(String error);

  /// Message for failed topic loading
  ///
  /// In en, this message translates to:
  /// **'Failed to load topic'**
  String get failedToLoadTopicMessage;

  /// Message for data loaded from cache
  ///
  /// In en, this message translates to:
  /// **'Data loaded from cache'**
  String get dataLoadedFromCacheMessage;

  /// Fallback for missing chapter
  ///
  /// In en, this message translates to:
  /// **'Unknown chapter'**
  String get unknownChapterText;

  /// Label for magnet link
  ///
  /// In en, this message translates to:
  /// **'Magnet link'**
  String get magnetLinkLabelText;

  /// Message for magnet link copied
  ///
  /// In en, this message translates to:
  /// **'Magnet link copied to clipboard'**
  String get magnetLinkCopiedMessage;

  /// Message for copy to clipboard
  ///
  /// In en, this message translates to:
  /// **'\$label copied to clipboard'**
  String copyToClipboardMessage(String label);

  /// Navigation text for library
  ///
  /// In en, this message translates to:
  /// **'Library'**
  String get navLibraryText;

  /// Navigation text for authentication
  ///
  /// In en, this message translates to:
  /// **'Connect'**
  String get navAuthText;

  /// Navigation text for search
  ///
  /// In en, this message translates to:
  /// **'Search'**
  String get navSearchText;

  /// Navigation text for settings
  ///
  /// In en, this message translates to:
  /// **'Settings'**
  String get navSettingsText;

  /// Navigation text for debug
  ///
  /// In en, this message translates to:
  /// **'Debug'**
  String get navDebugText;

  /// Title for login screen
  ///
  /// In en, this message translates to:
  /// **'Login'**
  String get authDialogTitle;

  /// Help text for authentication
  ///
  /// In en, this message translates to:
  /// **'Login to RuTracker to access audiobook search and downloads. Your credentials are stored securely.'**
  String get authHelpText;

  /// Message for successful cache clearing
  ///
  /// In en, this message translates to:
  /// **'Cache cleared successfully'**
  String get cacheClearedSuccessfullyMessage;

  /// Message for unknown download status
  ///
  /// In en, this message translates to:
  /// **'Download status unknown'**
  String get downloadStatusUnknownMessage;

  /// Message for failed log export
  ///
  /// In en, this message translates to:
  /// **'Failed to export logs'**
  String get failedToExportLogsMessage;

  /// Message for successful log export
  ///
  /// In en, this message translates to:
  /// **'Logs exported successfully'**
  String get logsExportedSuccessfullyMessage;

  /// Message for completed mirror health check
  ///
  /// In en, this message translates to:
  /// **'Mirror health check completed: \$tested/\$total mirrors'**
  String mirrorHealthCheckCompletedMessage(int tested, int total);

  /// Message when no active mirrors available for testing
  ///
  /// In en, this message translates to:
  /// **'No active mirrors to test'**
  String get noActiveMirrorsMessage;

  /// Message when active mirror is set
  ///
  /// In en, this message translates to:
  /// **'Active mirror set: \$url'**
  String activeMirrorSetMessage(String url);

  /// Message when failed to set active mirror
  ///
  /// In en, this message translates to:
  /// **'Failed to set active mirror: \$error'**
  String failedToSetActiveMirrorMessage(String error);

  /// Text showing active mirror
  ///
  /// In en, this message translates to:
  /// **'Active mirror: \$url'**
  String activeMirrorText(String url);

  /// Message when failed to set best mirror
  ///
  /// In en, this message translates to:
  /// **'Failed to set best mirror: \$error'**
  String failedToSetBestMirrorMessage(String error);

  /// Message when active mirror is disabled and switched
  ///
  /// In en, this message translates to:
  /// **'Active mirror disabled. Switched to: \$url'**
  String activeMirrorDisabledMessage(String url);

  /// Warning message when failed to select new active mirror
  ///
  /// In en, this message translates to:
  /// **'Warning: failed to select new active mirror: \$error'**
  String warningFailedToSelectNewActiveMirrorMessage(String error);

  /// Message when URL is copied to clipboard
  ///
  /// In en, this message translates to:
  /// **'URL copied to clipboard'**
  String get urlCopiedToClipboardMessage;

  /// Title for edit priority dialog
  ///
  /// In en, this message translates to:
  /// **'Edit Priority'**
  String get editPriorityTitle;

  /// Helper text for priority input
  ///
  /// In en, this message translates to:
  /// **'Lower number = higher priority'**
  String get priorityHelperText;

  /// Save button text
  ///
  /// In en, this message translates to:
  /// **'Save'**
  String get saveButtonText;

  /// Message when priority is updated
  ///
  /// In en, this message translates to:
  /// **'Priority updated: \$priority'**
  String priorityUpdatedMessage(int priority);

  /// Message when failed to update priority
  ///
  /// In en, this message translates to:
  /// **'Failed to update priority: \$error'**
  String failedToUpdatePriorityMessage(String error);

  /// Title for delete mirror dialog
  ///
  /// In en, this message translates to:
  /// **'Delete Mirror?'**
  String get deleteMirrorTitle;

  /// Warning message for default mirror deletion
  ///
  /// In en, this message translates to:
  /// **'Warning: this is a default mirror. It will be removed from the list, but can be added again.'**
  String get defaultMirrorWarningMessage;

  /// Confirmation message for mirror deletion
  ///
  /// In en, this message translates to:
  /// **'Are you sure you want to delete this mirror?'**
  String get confirmDeleteMirrorMessage;

  /// Delete button text
  ///
  /// In en, this message translates to:
  /// **'Delete'**
  String get deleteButtonText;

  /// Message when mirror is deleted
  ///
  /// In en, this message translates to:
  /// **'Mirror deleted: \$url'**
  String mirrorDeletedMessage(String url);

  /// Message when failed to delete mirror
  ///
  /// In en, this message translates to:
  /// **'Failed to delete mirror: \$error'**
  String failedToDeleteMirrorMessage(String error);

  /// Message when URL format is invalid
  ///
  /// In en, this message translates to:
  /// **'URL must start with http:// or https://'**
  String get urlMustStartWithHttpMessage;

  /// Message for invalid URL format
  ///
  /// In en, this message translates to:
  /// **'Invalid URL format'**
  String get invalidUrlFormatMessage;

  /// Message when mirror already exists
  ///
  /// In en, this message translates to:
  /// **'This mirror already exists in the list'**
  String get mirrorAlreadyExistsMessage;

  /// Filter text for showing only active mirrors
  ///
  /// In en, this message translates to:
  /// **'Only Active'**
  String get onlyActiveFilterText;

  /// Filter text for showing only healthy mirrors
  ///
  /// In en, this message translates to:
  /// **'Only Healthy'**
  String get onlyHealthyFilterText;

  /// Sort option text for priority
  ///
  /// In en, this message translates to:
  /// **'By Priority'**
  String get sortByPriorityText;

  /// Sort option text for health
  ///
  /// In en, this message translates to:
  /// **'By Health'**
  String get sortByHealthText;

  /// Sort option text for speed
  ///
  /// In en, this message translates to:
  /// **'By Speed'**
  String get sortBySpeedText;

  /// Message when no mirrors match filters
  ///
  /// In en, this message translates to:
  /// **'No mirrors match the filters'**
  String get noMirrorsMatchFiltersMessage;

  /// Button text for setting best mirror
  ///
  /// In en, this message translates to:
  /// **'Set Best Mirror'**
  String get setBestMirrorButtonText;

  /// Text showing health score
  ///
  /// In en, this message translates to:
  /// **'Health: \$score%'**
  String healthScoreText(int score);

  /// Tooltip for set as active button
  ///
  /// In en, this message translates to:
  /// **'Set as active'**
  String get setAsActiveTooltip;

  /// Tooltip for copy URL button
  ///
  /// In en, this message translates to:
  /// **'Copy URL'**
  String get copyUrlTooltip;

  /// Tooltip for delete mirror button
  ///
  /// In en, this message translates to:
  /// **'Delete mirror'**
  String get deleteMirrorTooltip;

  /// Label text for active status
  ///
  /// In en, this message translates to:
  /// **'Active'**
  String get activeLabelText;

  /// Message for failed cache clearing
  ///
  /// In en, this message translates to:
  /// **'Failed to clear cache: \$error'**
  String failedToClearCacheMessage(String error);

  /// Message for failed mirror testing
  ///
  /// In en, this message translates to:
  /// **'Failed to test mirrors: \$error'**
  String failedToTestMirrorsMessage(String error);

  /// Status for degraded mirror
  ///
  /// In en, this message translates to:
  /// **'Degraded'**
  String get mirrorStatusDegraded;

  /// Status for unhealthy mirror
  ///
  /// In en, this message translates to:
  /// **'Unhealthy'**
  String get mirrorStatusUnhealthy;

  /// Message prompting for credentials
  ///
  /// In en, this message translates to:
  /// **'Please enter username and password'**
  String get pleaseEnterCredentials;

  /// Message during connection testing
  ///
  /// In en, this message translates to:
  /// **'Testing connection...'**
  String get testingConnectionText;

  /// Title for favorites screen
  ///
  /// In en, this message translates to:
  /// **'Favorites'**
  String get favoritesTitle;

  /// Tooltip for refresh button
  ///
  /// In en, this message translates to:
  /// **'Refresh'**
  String get refreshTooltip;

  /// Message when no favorites
  ///
  /// In en, this message translates to:
  /// **'No favorite audiobooks'**
  String get noFavoritesMessage;

  /// Hint for empty favorites
  ///
  /// In en, this message translates to:
  /// **'Add audiobooks to favorites from search results'**
  String get addFavoritesHint;

  /// Button to navigate to search
  ///
  /// In en, this message translates to:
  /// **'Go to Search'**
  String get goToSearchButton;

  /// Message when added to favorites
  ///
  /// In en, this message translates to:
  /// **'Added to favorites'**
  String get addedToFavorites;

  /// Message when removed from favorites
  ///
  /// In en, this message translates to:
  /// **'Removed from favorites'**
  String get removedFromFavorites;

  /// Error message for failed removal
  ///
  /// In en, this message translates to:
  /// **'Failed to remove from favorites'**
  String get failedToRemoveFromFavorites;

  /// Tooltip for favorites button
  ///
  /// In en, this message translates to:
  /// **'Favorites'**
  String get favoritesTooltip;

  /// Tooltip for filter button
  ///
  /// In en, this message translates to:
  /// **'Filter library'**
  String get filterLibraryTooltip;

  /// Tooltip for add audiobook button
  ///
  /// In en, this message translates to:
  /// **'Add audiobook'**
  String get addAudiobookTooltip;

  /// Message when library is empty
  ///
  /// In en, this message translates to:
  /// **'Your library is empty'**
  String get libraryEmptyMessage;

  /// Hint for empty library
  ///
  /// In en, this message translates to:
  /// **'Add audiobooks to your library to start listening'**
  String get addAudiobooksHint;

  /// Button label for searching
  ///
  /// In en, this message translates to:
  /// **'Search for audiobooks'**
  String get searchForAudiobooksButton;

  /// Button label for importing files
  ///
  /// In en, this message translates to:
  /// **'Import from Files'**
  String get importFromFilesButton;

  /// Message during biometric authentication
  ///
  /// In en, this message translates to:
  /// **'Please wait, biometric authentication in progress...'**
  String get biometricAuthInProgress;

  /// Message for successful authorization
  ///
  /// In en, this message translates to:
  /// **'Authorization successful'**
  String get authorizationSuccessful;

  /// Title for authorization dialog
  ///
  /// In en, this message translates to:
  /// **'Authorization'**
  String get authorizationTitle;

  /// Message when biometric auth is unavailable
  ///
  /// In en, this message translates to:
  /// **'Biometric authentication is unavailable or failed. Open WebView to login?'**
  String get biometricUnavailableMessage;

  /// Button to open WebView login
  ///
  /// In en, this message translates to:
  /// **'Open WebView'**
  String get openWebViewButton;

  /// Error message for authorization check
  ///
  /// In en, this message translates to:
  /// **'Error checking authorization: \$error'**
  String authorizationCheckError(String error);

  /// Message for failed authorization
  ///
  /// In en, this message translates to:
  /// **'Authorization failed. Please check your login and password'**
  String get authorizationFailedMessage;

  /// Error message for authorization page
  ///
  /// In en, this message translates to:
  /// **'Error opening authorization page: \$error'**
  String authorizationPageError(String error);

  /// Label for filters section
  ///
  /// In en, this message translates to:
  /// **'Filters:'**
  String get filtersLabel;

  /// Button to reset filters
  ///
  /// In en, this message translates to:
  /// **'Reset'**
  String get resetButton;

  /// Fallback category name
  ///
  /// In en, this message translates to:
  /// **'Other'**
  String get otherCategory;

  /// Title for search history section
  ///
  /// In en, this message translates to:
  /// **'Search History'**
  String get searchHistoryTitle;

  /// Button to clear history
  ///
  /// In en, this message translates to:
  /// **'Clear'**
  String get clearButton;

  /// Error message for failed favorite addition
  ///
  /// In en, this message translates to:
  /// **'Failed to add to favorites'**
  String get failedToAddToFavorites;

  /// Button to load more results
  ///
  /// In en, this message translates to:
  /// **'Load more'**
  String get loadMoreButton;

  /// Button to allow permission
  ///
  /// In en, this message translates to:
  /// **'Allow'**
  String get allowButton;

  /// Menu item to copy magnet link
  ///
  /// In en, this message translates to:
  /// **'Copy Magnet Link'**
  String get copyMagnetLink;

  /// Menu item to download torrent
  ///
  /// In en, this message translates to:
  /// **'Download Torrent'**
  String get downloadTorrentMenu;

  /// Subtitle for torrent download
  ///
  /// In en, this message translates to:
  /// **'Open torrent file in external app'**
  String get openTorrentInExternalApp;

  /// Error message for failed torrent opening
  ///
  /// In en, this message translates to:
  /// **'Failed to open torrent: \$error'**
  String failedToOpenTorrent(String error);

  /// Error message for unavailable URL
  ///
  /// In en, this message translates to:
  /// **'URL unavailable'**
  String get urlUnavailable;

  /// Error message for invalid URL
  ///
  /// In en, this message translates to:
  /// **'Invalid URL format: \$url'**
  String invalidUrlFormat(String url);

  /// Generic error message
  ///
  /// In en, this message translates to:
  /// **'Error: \$error'**
  String genericError(String error);

  /// Message for retrying connection
  ///
  /// In en, this message translates to:
  /// **'Retrying connection (\$current/\$max)...'**
  String retryConnectionMessage(int current, int max);

  /// Error message for load failure
  ///
  /// In en, this message translates to:
  /// **'Load error: \$desc'**
  String loadError(String desc);

  /// Error message for page load failure
  ///
  /// In en, this message translates to:
  /// **'An error occurred while loading the page'**
  String get pageLoadError;

  /// Message during security verification
  ///
  /// In en, this message translates to:
  /// **'Security verification in progress - please wait...'**
  String get securityVerificationInProgress;

  /// Button to open in browser
  ///
  /// In en, this message translates to:
  /// **'Open in Browser'**
  String get openInBrowserButton;

  /// Message for file download in browser
  ///
  /// In en, this message translates to:
  /// **'The file will be opened in browser for download'**
  String get fileWillOpenInBrowser;

  /// Button to reset filters
  ///
  /// In en, this message translates to:
  /// **'Reset Filters'**
  String get resetFiltersButton;

  /// Message when language is changed
  ///
  /// In en, this message translates to:
  /// **'Language changed to {languageName}'**
  String languageChangedMessage(String languageName);

  /// Title for follow system theme setting
  ///
  /// In en, this message translates to:
  /// **'Follow System Theme'**
  String get followSystemTheme;

  /// Description for RuTracker session section
  ///
  /// In en, this message translates to:
  /// **'RuTracker session management (cookie)'**
  String get rutrackerSessionDescription;

  /// Button text for WebView login
  ///
  /// In en, this message translates to:
  /// **'Login to RuTracker via WebView'**
  String get loginViaWebViewButton;

  /// Subtitle for WebView login button
  ///
  /// In en, this message translates to:
  /// **'Pass Cloudflare/captcha and save cookie for client'**
  String get loginViaWebViewSubtitle;

  /// Message when cookies are saved
  ///
  /// In en, this message translates to:
  /// **'Cookies saved for HTTP client'**
  String get cookiesSavedForHttpClient;

  /// Button text to clear session
  ///
  /// In en, this message translates to:
  /// **'Clear RuTracker session (cookie)'**
  String get clearSessionButton;

  /// Subtitle for clear session button
  ///
  /// In en, this message translates to:
  /// **'Delete saved cookies and logout from account'**
  String get clearSessionSubtitle;

  /// Message when session is cleared
  ///
  /// In en, this message translates to:
  /// **'RuTracker session cleared'**
  String get sessionClearedMessage;

  /// Title for metadata section
  ///
  /// In en, this message translates to:
  /// **'Audiobook Metadata'**
  String get metadataSectionTitle;

  /// Description for metadata section
  ///
  /// In en, this message translates to:
  /// **'Manage local audiobook metadata database'**
  String get metadataSectionDescription;

  /// Label for total records
  ///
  /// In en, this message translates to:
  /// **'Total records'**
  String get totalRecordsLabel;

  /// Label for last update
  ///
  /// In en, this message translates to:
  /// **'Last update'**
  String get lastUpdateLabel;

  /// Text shown during update
  ///
  /// In en, this message translates to:
  /// **'Updating...'**
  String get updatingText;

  /// Button text to update metadata
  ///
  /// In en, this message translates to:
  /// **'Update Metadata'**
  String get updateMetadataButton;

  /// Message when metadata update starts
  ///
  /// In en, this message translates to:
  /// **'Metadata update started...'**
  String get metadataUpdateStartedMessage;

  /// Message when metadata update completes
  ///
  /// In en, this message translates to:
  /// **'Update completed: collected \$total records'**
  String metadataUpdateCompletedMessage(int total);

  /// Error message for metadata update
  ///
  /// In en, this message translates to:
  /// **'Update error: \$error'**
  String metadataUpdateError(String error);

  /// Text for never date
  ///
  /// In en, this message translates to:
  /// **'Never'**
  String get neverDate;

  /// Text for days ago
  ///
  /// In en, this message translates to:
  /// **'\$days days ago'**
  String daysAgo(int days);

  /// Text for hours ago
  ///
  /// In en, this message translates to:
  /// **'\$hours hours ago'**
  String hoursAgo(int hours);

  /// Text for minutes ago
  ///
  /// In en, this message translates to:
  /// **'\$minutes minutes ago'**
  String minutesAgo(int minutes);

  /// Text for just now
  ///
  /// In en, this message translates to:
  /// **'Just now'**
  String get justNow;

  /// Text for unknown date
  ///
  /// In en, this message translates to:
  /// **'Unknown'**
  String get unknownDate;

  /// Default playback speed
  ///
  /// In en, this message translates to:
  /// **'1.0x'**
  String get playbackSpeedDefault;

  /// Default skip duration
  ///
  /// In en, this message translates to:
  /// **'15 seconds'**
  String get skipDurationDefault;

  /// Button text to clear expired cache
  ///
  /// In en, this message translates to:
  /// **'Clear Expired Cache'**
  String get clearExpiredCacheButton;

  /// Title for permissions section
  ///
  /// In en, this message translates to:
  /// **'App Permissions'**
  String get appPermissionsTitle;

  /// Name for storage permission
  ///
  /// In en, this message translates to:
  /// **'Storage'**
  String get storagePermissionName;

  /// Description for storage permission
  ///
  /// In en, this message translates to:
  /// **'Save audiobook files and cache data'**
  String get storagePermissionDescription;

  /// Name for notifications permission
  ///
  /// In en, this message translates to:
  /// **'Notifications'**
  String get notificationsPermissionName;

  /// Description for notifications permission
  ///
  /// In en, this message translates to:
  /// **'Show playback controls and updates'**
  String get notificationsPermissionDescription;

  /// Button text to grant all permissions
  ///
  /// In en, this message translates to:
  /// **'Grant All Permissions'**
  String get grantAllPermissionsButton;

  /// Message when all permissions are granted
  ///
  /// In en, this message translates to:
  /// **'All permissions granted'**
  String get allPermissionsGranted;

  /// Message when file access is available
  ///
  /// In en, this message translates to:
  /// **'File access available'**
  String get fileAccessAvailable;

  /// Message when file access is unavailable
  ///
  /// In en, this message translates to:
  /// **'File access unavailable'**
  String get fileAccessUnavailable;

  /// Title for storage permission guidance dialog
  ///
  /// In en, this message translates to:
  /// **'File Access Permission'**
  String get storagePermissionGuidanceTitle;

  /// Message for storage permission guidance dialog
  ///
  /// In en, this message translates to:
  /// **'To access audio files, you need to grant file access permission.'**
  String get storagePermissionGuidanceMessage;

  /// Step 1 for storage permission guidance
  ///
  /// In en, this message translates to:
  /// **'1. Open JaBook app settings'**
  String get storagePermissionGuidanceStep1;

  /// Step 2 for storage permission guidance
  ///
  /// In en, this message translates to:
  /// **'2. Go to Permissions section'**
  String get storagePermissionGuidanceStep2;

  /// Step 3 for storage permission guidance
  ///
  /// In en, this message translates to:
  /// **'3. Enable \"Files and media\" or \"Storage\" permission'**
  String get storagePermissionGuidanceStep3;

  /// Title for MIUI storage permission guidance dialog
  ///
  /// In en, this message translates to:
  /// **'File Access Permission (MIUI)'**
  String get storagePermissionGuidanceMiuiTitle;

  /// Message for MIUI storage permission guidance dialog
  ///
  /// In en, this message translates to:
  /// **'On Xiaomi/Redmi/Poco (MIUI) devices, you need to grant file access permission:'**
  String get storagePermissionGuidanceMiuiMessage;

  /// Step 1 for MIUI storage permission guidance
  ///
  /// In en, this message translates to:
  /// **'1. Open Settings → Apps → JaBook → Permissions'**
  String get storagePermissionGuidanceMiuiStep1;

  /// Step 2 for MIUI storage permission guidance
  ///
  /// In en, this message translates to:
  /// **'2. Enable \"Files and media\" or \"Storage\" permission'**
  String get storagePermissionGuidanceMiuiStep2;

  /// Step 3 for MIUI storage permission guidance
  ///
  /// In en, this message translates to:
  /// **'3. If access is limited, enable \"Manage all files\" in MIUI Security settings'**
  String get storagePermissionGuidanceMiuiStep3;

  /// Note for MIUI storage permission guidance
  ///
  /// In en, this message translates to:
  /// **'Note: On some MIUI versions, you may need to additionally enable \"Manage all files\" in Settings → Security → Permission management.'**
  String get storagePermissionGuidanceMiuiNote;

  /// Title for ColorOS/RealmeUI storage permission guidance dialog
  ///
  /// In en, this message translates to:
  /// **'File Access Permission (ColorOS/RealmeUI)'**
  String get storagePermissionGuidanceColorosTitle;

  /// Message for ColorOS/RealmeUI storage permission guidance dialog
  ///
  /// In en, this message translates to:
  /// **'On Oppo/Realme (ColorOS/RealmeUI) devices, you need to grant file access permission:'**
  String get storagePermissionGuidanceColorosMessage;

  /// Step 1 for ColorOS/RealmeUI storage permission guidance
  ///
  /// In en, this message translates to:
  /// **'1. Open Settings → Apps → JaBook → Permissions'**
  String get storagePermissionGuidanceColorosStep1;

  /// Step 2 for ColorOS/RealmeUI storage permission guidance
  ///
  /// In en, this message translates to:
  /// **'2. Enable \"Files and media\" permission'**
  String get storagePermissionGuidanceColorosStep2;

  /// Step 3 for ColorOS/RealmeUI storage permission guidance
  ///
  /// In en, this message translates to:
  /// **'3. If access is limited, check \"Files and media\" settings in permissions section'**
  String get storagePermissionGuidanceColorosStep3;

  /// Note for ColorOS/RealmeUI storage permission guidance
  ///
  /// In en, this message translates to:
  /// **'Note: On some ColorOS versions, you may need to additionally allow file access in security settings.'**
  String get storagePermissionGuidanceColorosNote;

  /// Button text to open settings
  ///
  /// In en, this message translates to:
  /// **'Open Settings'**
  String get openSettings;

  /// Title for SAF fallback dialog
  ///
  /// In en, this message translates to:
  /// **'Alternative File Access Method'**
  String get safFallbackTitle;

  /// Message for SAF fallback dialog
  ///
  /// In en, this message translates to:
  /// **'File access permissions are not working properly on your device. You can use the Storage Access Framework (SAF) to select folders manually. This method works without requiring special permissions.'**
  String get safFallbackMessage;

  /// Message when user didn't check the permission checkbox in SAF folder picker
  ///
  /// In en, this message translates to:
  /// **'Please check the \'Allow access to this folder\' checkbox in the file picker dialog and try again. Without this checkbox, the app cannot access the selected folder.'**
  String get safPermissionCheckboxMessage;

  /// Message when folder access check fails after selection
  ///
  /// In en, this message translates to:
  /// **'No access to selected folder. Please check the \'Allow access to this folder\' checkbox in the file picker and try again.'**
  String get safNoAccessMessage;

  /// Title for hint dialog before folder picker
  ///
  /// In en, this message translates to:
  /// **'Important: Check the checkbox'**
  String get safFolderPickerHintTitle;

  /// Message for hint dialog before folder picker
  ///
  /// In en, this message translates to:
  /// **'When selecting a folder, please make sure to check the \'Allow access to this folder\' checkbox in the file picker dialog. Without this checkbox, the app cannot access the selected folder.'**
  String get safFolderPickerHintMessage;

  /// Warning about blocked Android/data and Android/obb folders
  ///
  /// In en, this message translates to:
  /// **'Note: Access to Android/data and Android/obb folders is blocked on Android 11+ devices with security updates from March 2024. Please select a different folder.'**
  String get safAndroidDataObbWarning;

  /// Benefits of using SAF fallback
  ///
  /// In en, this message translates to:
  /// **'Benefits of using SAF:\n• Works on all Android devices\n• No special permissions needed\n• You choose which folders to access'**
  String get safFallbackBenefits;

  /// Button to use SAF method
  ///
  /// In en, this message translates to:
  /// **'Use Folder Selection'**
  String get useSafMethod;

  /// Button to try permissions again
  ///
  /// In en, this message translates to:
  /// **'Try Permissions Again'**
  String get tryPermissionsAgain;

  /// Message when notifications are available
  ///
  /// In en, this message translates to:
  /// **'Notifications available'**
  String get notificationsAvailable;

  /// Message when notifications are unavailable
  ///
  /// In en, this message translates to:
  /// **'Notifications unavailable'**
  String get notificationsUnavailable;

  /// Status message for capabilities
  ///
  /// In en, this message translates to:
  /// **'Capabilities: \$grantedCount/\$total'**
  String capabilitiesStatus(int grantedCount, int total);

  /// Title for backup and restore section
  ///
  /// In en, this message translates to:
  /// **'Backup & Restore'**
  String get backupRestoreTitle;

  /// Description for backup and restore section
  ///
  /// In en, this message translates to:
  /// **'Export and import your data (favorites, history, metadata)'**
  String get backupRestoreDescription;

  /// Button text to export data
  ///
  /// In en, this message translates to:
  /// **'Export Data'**
  String get exportDataButton;

  /// Subtitle for export data button
  ///
  /// In en, this message translates to:
  /// **'Save all your data to a backup file'**
  String get exportDataSubtitle;

  /// Button text to import data
  ///
  /// In en, this message translates to:
  /// **'Import Data'**
  String get importDataButton;

  /// Subtitle for import data button
  ///
  /// In en, this message translates to:
  /// **'Restore data from a backup file'**
  String get importDataSubtitle;

  /// Message when exporting data
  ///
  /// In en, this message translates to:
  /// **'Exporting data...'**
  String get exportingDataMessage;

  /// Message when data export succeeds
  ///
  /// In en, this message translates to:
  /// **'Data exported successfully'**
  String get dataExportedSuccessfullyMessage;

  /// Error message for failed export
  ///
  /// In en, this message translates to:
  /// **'Failed to export: \$error'**
  String failedToExportMessage(String error);

  /// Title for import backup dialog
  ///
  /// In en, this message translates to:
  /// **'Import Backup'**
  String get importBackupTitle;

  /// Confirmation message for import backup
  ///
  /// In en, this message translates to:
  /// **'This will import data from the backup file. Existing data may be merged or replaced. Continue?'**
  String get importBackupConfirmationMessage;

  /// Button text to import
  ///
  /// In en, this message translates to:
  /// **'Import'**
  String get importButton;

  /// Message when importing data
  ///
  /// In en, this message translates to:
  /// **'Importing data...'**
  String get importingDataMessage;

  /// Error message for failed import
  ///
  /// In en, this message translates to:
  /// **'Failed to import: \$error'**
  String failedToImportMessage(String error);

  /// Tooltip for RuTracker login button
  ///
  /// In en, this message translates to:
  /// **'RuTracker Login'**
  String get rutrackerLoginTooltip;

  /// Tooltip for mirrors button
  ///
  /// In en, this message translates to:
  /// **'Mirrors'**
  String get mirrorsTooltip;

  /// Label showing current active mirror
  ///
  /// In en, this message translates to:
  /// **'Current mirror: {host}'**
  String currentMirrorLabel(String host);

  /// Error message when all mirrors fail
  ///
  /// In en, this message translates to:
  /// **'All mirrors failed'**
  String get allMirrorsFailedMessage;

  /// Message for unknown error
  ///
  /// In en, this message translates to:
  /// **'Unknown error'**
  String get unknownError;

  /// Error message when cannot connect to RuTracker mirrors
  ///
  /// In en, this message translates to:
  /// **'Could not connect to RuTracker mirrors. Check your internet connection or try selecting another mirror in settings'**
  String get mirrorConnectionError;

  /// Short error message when cannot connect to RuTracker mirrors
  ///
  /// In en, this message translates to:
  /// **'Could not connect to RuTracker mirrors'**
  String get mirrorConnectionFailed;

  /// Text for refresh button
  ///
  /// In en, this message translates to:
  /// **'Refresh'**
  String get refresh;

  /// Menu item for refreshing current search
  ///
  /// In en, this message translates to:
  /// **'Refresh current search'**
  String get refreshCurrentSearch;

  /// Menu item for clearing search cache
  ///
  /// In en, this message translates to:
  /// **'Clear search cache'**
  String get clearSearchCache;

  /// Message for successful cache clearing
  ///
  /// In en, this message translates to:
  /// **'Cache cleared'**
  String get cacheCleared;

  /// Message for successful clearing of all cache
  ///
  /// In en, this message translates to:
  /// **'All cache cleared'**
  String get allCacheCleared;

  /// Menu item for downloading via magnet link
  ///
  /// In en, this message translates to:
  /// **'Download via Magnet'**
  String get downloadViaMagnet;

  /// Message for download start
  ///
  /// In en, this message translates to:
  /// **'Download started'**
  String get downloadStarted;

  /// No description provided for @refreshChaptersFromTorrent.
  ///
  /// In en, this message translates to:
  /// **'Refresh chapters from torrent'**
  String get refreshChaptersFromTorrent;

  /// No description provided for @chaptersRefreshed.
  ///
  /// In en, this message translates to:
  /// **'Chapters refreshed from torrent'**
  String get chaptersRefreshed;

  /// No description provided for @failedToRefreshChapters.
  ///
  /// In en, this message translates to:
  /// **'Failed to refresh chapters'**
  String get failedToRefreshChapters;

  /// No description provided for @noChaptersFound.
  ///
  /// In en, this message translates to:
  /// **'No chapters found in torrent'**
  String get noChaptersFound;

  /// Error message for failed download start
  ///
  /// In en, this message translates to:
  /// **'Failed to start download'**
  String get failedToStartDownload;

  /// Message when there are no active downloads
  ///
  /// In en, this message translates to:
  /// **'No active downloads'**
  String get noActiveDownloads;

  /// Text before cache expiration time
  ///
  /// In en, this message translates to:
  /// **'Expires in'**
  String get cacheExpires;

  /// Text when cache has expired
  ///
  /// In en, this message translates to:
  /// **'Expired'**
  String get cacheExpired;

  /// Text when cache expires soon
  ///
  /// In en, this message translates to:
  /// **'Expires soon'**
  String get cacheExpiresSoon;

  /// Abbreviation for days
  ///
  /// In en, this message translates to:
  /// **'days'**
  String get days;

  /// Abbreviation for hours
  ///
  /// In en, this message translates to:
  /// **'hours'**
  String get hours;

  /// Abbreviation for minutes
  ///
  /// In en, this message translates to:
  /// **'minutes'**
  String get minutes;

  /// Button to expand text
  ///
  /// In en, this message translates to:
  /// **'Show more'**
  String get showMore;

  /// Button to collapse text
  ///
  /// In en, this message translates to:
  /// **'Show less'**
  String get showLess;

  /// Title for folder selection dialog
  ///
  /// In en, this message translates to:
  /// **'Select Download Folder'**
  String get selectFolderDialogTitle;

  /// Instructions for selecting folder on Android 13+
  ///
  /// In en, this message translates to:
  /// **'Please select the folder where downloaded audiobooks will be saved. In the file manager, navigate to the desired folder and tap \"Use this folder\" to confirm.'**
  String get selectFolderDialogMessage;

  /// Message when folder is selected successfully
  ///
  /// In en, this message translates to:
  /// **'Download folder selected successfully'**
  String get folderSelectedSuccessMessage;

  /// Message when folder selection is cancelled
  ///
  /// In en, this message translates to:
  /// **'Folder selection cancelled'**
  String get folderSelectionCancelledMessage;

  /// Current download folder path
  ///
  /// In en, this message translates to:
  /// **'Current folder: {path}'**
  String currentDownloadFolder(String path);

  /// No description provided for @defaultDownloadFolder.
  ///
  /// In en, this message translates to:
  /// **'Default folder'**
  String get defaultDownloadFolder;

  /// Message shown when user presses back button, asking to press again to exit
  ///
  /// In en, this message translates to:
  /// **'Press back again to exit'**
  String get pressBackAgainToExit;

  /// Message in filter dialog indicating filters are coming soon
  ///
  /// In en, this message translates to:
  /// **'Filter options will be available soon. You will be able to filter by:'**
  String get filterOptionsComingSoon;

  /// Filter option by category
  ///
  /// In en, this message translates to:
  /// **'Category'**
  String get filterByCategory;

  /// Filter option by author
  ///
  /// In en, this message translates to:
  /// **'Author'**
  String get filterByAuthor;

  /// Filter option by date added
  ///
  /// In en, this message translates to:
  /// **'Date Added'**
  String get filterByDate;

  /// Close button text
  ///
  /// In en, this message translates to:
  /// **'Close'**
  String get close;

  /// Button to open torrent in external client
  ///
  /// In en, this message translates to:
  /// **'Open in External Client'**
  String get openInExternalClient;

  /// Description for downloading torrent in app
  ///
  /// In en, this message translates to:
  /// **'Download using built-in torrent client'**
  String get downloadTorrentInApp;

  /// Message when torrent is opened in external client
  ///
  /// In en, this message translates to:
  /// **'Opened in external torrent client'**
  String get openedInExternalClient;

  /// Message when failed to open in external client
  ///
  /// In en, this message translates to:
  /// **'Failed to open in external client'**
  String get failedToOpenExternalClient;

  /// Message when magnet link is not available
  ///
  /// In en, this message translates to:
  /// **'No magnet link available'**
  String get noMagnetLinkAvailable;

  /// Title for recommended audiobooks section
  ///
  /// In en, this message translates to:
  /// **'Recommended'**
  String get recommended;

  /// Message when no results found for specific query
  ///
  /// In en, this message translates to:
  /// **'Nothing found for query: \"{query}\"'**
  String noResultsForQuery(String query);

  /// Suggestion to try different search keywords
  ///
  /// In en, this message translates to:
  /// **'Try changing keywords'**
  String get tryDifferentKeywords;

  /// Button to clear search field
  ///
  /// In en, this message translates to:
  /// **'Clear search'**
  String get clearSearch;

  /// Title for library folders section
  ///
  /// In en, this message translates to:
  /// **'Library Folders'**
  String get libraryFolderTitle;

  /// Description for library folders section
  ///
  /// In en, this message translates to:
  /// **'Select folders where your audiobooks are stored'**
  String get libraryFolderDescription;

  /// Default library folder text
  ///
  /// In en, this message translates to:
  /// **'Default folder'**
  String get defaultLibraryFolder;

  /// Title for add library folder button
  ///
  /// In en, this message translates to:
  /// **'Add Library Folder'**
  String get addLibraryFolderTitle;

  /// Subtitle for add library folder button
  ///
  /// In en, this message translates to:
  /// **'Add an additional folder to scan for audiobooks'**
  String get addLibraryFolderSubtitle;

  /// Title for all library folders expansion tile
  ///
  /// In en, this message translates to:
  /// **'All Library Folders'**
  String get allLibraryFoldersTitle;

  /// Text indicating primary library folder
  ///
  /// In en, this message translates to:
  /// **'Primary folder'**
  String get primaryLibraryFolder;

  /// Title for select library folder dialog
  ///
  /// In en, this message translates to:
  /// **'Select Library Folder'**
  String get selectLibraryFolderDialogTitle;

  /// Message for select library folder dialog
  ///
  /// In en, this message translates to:
  /// **'To select a library folder:\n\n1. Navigate to the desired folder in the file manager\n2. Tap \"Use this folder\" button in the top right corner\n\nThe selected folder will be used to scan for audiobooks.'**
  String get selectLibraryFolderDialogMessage;

  /// Title for migrate library folder dialog
  ///
  /// In en, this message translates to:
  /// **'Migrate Files?'**
  String get migrateLibraryFolderTitle;

  /// Message for migrate library folder dialog
  ///
  /// In en, this message translates to:
  /// **'Do you want to move your existing audiobooks from the old folder to the new folder?'**
  String get migrateLibraryFolderMessage;

  /// Yes button text
  ///
  /// In en, this message translates to:
  /// **'Yes'**
  String get yes;

  /// No button text
  ///
  /// In en, this message translates to:
  /// **'No'**
  String get no;

  /// Success message when library folder is selected
  ///
  /// In en, this message translates to:
  /// **'Library folder selected successfully'**
  String get libraryFolderSelectedSuccessMessage;

  /// Message when library folder already exists
  ///
  /// In en, this message translates to:
  /// **'This folder is already in the library folders list'**
  String get libraryFolderAlreadyExistsMessage;

  /// Success message when library folder is added
  ///
  /// In en, this message translates to:
  /// **'Library folder added successfully'**
  String get libraryFolderAddedSuccessMessage;

  /// Title for remove library folder dialog
  ///
  /// In en, this message translates to:
  /// **'Remove Folder?'**
  String get removeLibraryFolderTitle;

  /// Message for remove library folder dialog
  ///
  /// In en, this message translates to:
  /// **'Are you sure you want to remove this folder from the library? This will not delete the files, only stop scanning this folder.'**
  String get removeLibraryFolderMessage;

  /// Remove button text
  ///
  /// In en, this message translates to:
  /// **'Remove'**
  String get remove;

  /// Success message when library folder is removed
  ///
  /// In en, this message translates to:
  /// **'Library folder removed successfully'**
  String get libraryFolderRemovedSuccessMessage;

  /// Error message when library folder removal fails
  ///
  /// In en, this message translates to:
  /// **'Failed to remove library folder'**
  String get libraryFolderRemoveFailedMessage;

  /// Message shown during library folder migration
  ///
  /// In en, this message translates to:
  /// **'Migrating files...'**
  String get migratingLibraryFolderMessage;

  /// Success message when migration is completed
  ///
  /// In en, this message translates to:
  /// **'Migration completed successfully'**
  String get migrationCompletedSuccessMessage;

  /// Error message when migration fails
  ///
  /// In en, this message translates to:
  /// **'Migration failed'**
  String get migrationFailedMessage;

  /// Title for delete confirmation dialog
  ///
  /// In en, this message translates to:
  /// **'Delete Audiobook?'**
  String get deleteConfirmationTitle;

  /// Message for delete confirmation dialog
  ///
  /// In en, this message translates to:
  /// **'Are you sure you want to delete this audiobook?'**
  String get deleteConfirmationMessage;

  /// Warning message about permanent deletion
  ///
  /// In en, this message translates to:
  /// **'Files will be permanently deleted and cannot be recovered.'**
  String get deleteWarningMessage;

  /// Button to delete files physically
  ///
  /// In en, this message translates to:
  /// **'Delete Files'**
  String get deleteFilesButton;

  /// Button to remove from library (logical deletion)
  ///
  /// In en, this message translates to:
  /// **'Remove from Library'**
  String get removeFromLibraryButton;

  /// Title for remove from library dialog
  ///
  /// In en, this message translates to:
  /// **'Remove from Library?'**
  String get removeFromLibraryTitle;

  /// Message for remove from library dialog
  ///
  /// In en, this message translates to:
  /// **'This will remove the audiobook from your library but will not delete the files. You can add it back by rescanning.'**
  String get removeFromLibraryMessage;

  /// Message shown while removing from library
  ///
  /// In en, this message translates to:
  /// **'Removing...'**
  String get removingFromLibraryMessage;

  /// Success message when removed from library
  ///
  /// In en, this message translates to:
  /// **'Removed from library successfully'**
  String get removedFromLibrarySuccessMessage;

  /// Error message when removal from library fails
  ///
  /// In en, this message translates to:
  /// **'Failed to remove from library'**
  String get removeFromLibraryFailedMessage;

  /// Message shown while deleting files
  ///
  /// In en, this message translates to:
  /// **'Deleting...'**
  String get deletingMessage;

  /// Success message when files are deleted
  ///
  /// In en, this message translates to:
  /// **'Files deleted successfully'**
  String get deletedSuccessMessage;

  /// Error message when deletion fails
  ///
  /// In en, this message translates to:
  /// **'Failed to delete files'**
  String get deleteFailedMessage;

  /// Error message when trying to delete a file that is playing
  ///
  /// In en, this message translates to:
  /// **'Cannot delete: File is currently being played'**
  String get fileInUseMessage;

  /// Button to show information about audiobook
  ///
  /// In en, this message translates to:
  /// **'Show Info'**
  String get showInfoButton;

  /// Label for file path
  ///
  /// In en, this message translates to:
  /// **'Path'**
  String get path;

  /// Label for file count
  ///
  /// In en, this message translates to:
  /// **'Files'**
  String get fileCount;

  /// Label for total size
  ///
  /// In en, this message translates to:
  /// **'Total Size'**
  String get totalSize;

  /// Label for audiobook group name
  ///
  /// In en, this message translates to:
  /// **'Group'**
  String get audiobookGroupName;

  /// Title for storage management screen
  ///
  /// In en, this message translates to:
  /// **'Storage Management'**
  String get storageManagementTitle;

  /// Button to delete selected items
  ///
  /// In en, this message translates to:
  /// **'Delete Selected'**
  String get deleteSelectedButton;

  /// Title for storage summary section
  ///
  /// In en, this message translates to:
  /// **'Storage Summary'**
  String get storageSummaryTitle;

  /// Label for total library size
  ///
  /// In en, this message translates to:
  /// **'Total Library Size'**
  String get totalLibrarySize;

  /// Label for audiobook groups count
  ///
  /// In en, this message translates to:
  /// **'Audiobook Groups'**
  String get audiobookGroupsCount;

  /// Label for cache size
  ///
  /// In en, this message translates to:
  /// **'Cache Size'**
  String get cacheSize;

  /// Title for library folders section
  ///
  /// In en, this message translates to:
  /// **'Library Folders'**
  String get libraryFoldersTitle;

  /// Title for cache section
  ///
  /// In en, this message translates to:
  /// **'Cache'**
  String get cacheSectionTitle;

  /// Label for total cache size
  ///
  /// In en, this message translates to:
  /// **'Total Cache'**
  String get totalCacheSize;

  /// Button to clear playback cache
  ///
  /// In en, this message translates to:
  /// **'Clear Playback Cache'**
  String get clearPlaybackCacheButton;

  /// Title for audiobook groups section
  ///
  /// In en, this message translates to:
  /// **'Audiobook Groups'**
  String get audiobookGroupsTitle;

  /// Button to deselect all items
  ///
  /// In en, this message translates to:
  /// **'Deselect All'**
  String get deselectAllButton;

  /// Button to select all items
  ///
  /// In en, this message translates to:
  /// **'Select All'**
  String get selectAllButton;

  /// Message when no audiobooks are found
  ///
  /// In en, this message translates to:
  /// **'No audiobooks found'**
  String get noAudiobooksMessage;

  /// Title for delete selected dialog
  ///
  /// In en, this message translates to:
  /// **'Delete Selected?'**
  String get deleteSelectedTitle;

  /// Message for delete selected dialog
  ///
  /// In en, this message translates to:
  /// **'Are you sure you want to delete {count} selected audiobook(s)?'**
  String deleteSelectedMessage(int count);

  /// Result message after deleting selected items
  ///
  /// In en, this message translates to:
  /// **'Deleted: {success}, Failed: {failed}'**
  String deleteSelectedResultMessage(int success, int failed);

  /// Title for clear playback cache dialog
  ///
  /// In en, this message translates to:
  /// **'Clear Playback Cache?'**
  String get clearPlaybackCacheTitle;

  /// Message for clear playback cache dialog
  ///
  /// In en, this message translates to:
  /// **'This will clear the playback cache. Playback may be slower until cache is rebuilt.'**
  String get clearPlaybackCacheMessage;

  /// Message shown while clearing cache
  ///
  /// In en, this message translates to:
  /// **'Clearing cache...'**
  String get clearingCacheMessage;

  /// Success message when cache is cleared
  ///
  /// In en, this message translates to:
  /// **'Cache cleared successfully'**
  String get cacheClearedSuccessMessage;

  /// Title for clear all cache dialog
  ///
  /// In en, this message translates to:
  /// **'Clear All Cache?'**
  String get clearAllCacheTitle;

  /// Message for clear all cache dialog
  ///
  /// In en, this message translates to:
  /// **'This will clear all cache including playback cache, temporary files, and old logs.'**
  String get clearAllCacheMessage;

  /// Description for storage management section
  ///
  /// In en, this message translates to:
  /// **'Manage library size, cache, and files'**
  String get storageManagementDescription;

  /// Button to open storage management screen
  ///
  /// In en, this message translates to:
  /// **'Open Storage Management'**
  String get openStorageManagementButton;

  /// Message when some files were deleted successfully
  ///
  /// In en, this message translates to:
  /// **'Partially deleted: {deleted}/{total} files'**
  String partialDeletionSuccessMessage(int deleted, int total);

  /// Button to show details
  ///
  /// In en, this message translates to:
  /// **'Details'**
  String get showDetailsButton;

  /// Error message when permission is denied for file deletion
  ///
  /// In en, this message translates to:
  /// **'Permission denied: Cannot delete file'**
  String get permissionDeniedDeletionMessage;

  /// Title for deletion details dialog
  ///
  /// In en, this message translates to:
  /// **'Deletion Details'**
  String get deletionDetailsTitle;

  /// Summary message for deletion details
  ///
  /// In en, this message translates to:
  /// **'Deleted: {deleted}/{total} files'**
  String deletionSummaryMessage(int deleted, int total);

  /// Title for errors section
  ///
  /// In en, this message translates to:
  /// **'Errors:'**
  String get errorsTitle;

  /// Button to retry an operation
  ///
  /// In en, this message translates to:
  /// **'Retry'**
  String get retryButton;

  /// Title for background compatibility screen
  ///
  /// In en, this message translates to:
  /// **'Background Work'**
  String get backgroundWorkTitle;

  /// Tooltip for refresh button
  ///
  /// In en, this message translates to:
  /// **'Refresh diagnostics'**
  String get refreshDiagnostics;

  /// Section title for device information
  ///
  /// In en, this message translates to:
  /// **'Device Information'**
  String get deviceInformation;

  /// Label for device manufacturer
  ///
  /// In en, this message translates to:
  /// **'Manufacturer'**
  String get manufacturer;

  /// Label for custom ROM
  ///
  /// In en, this message translates to:
  /// **'Custom ROM'**
  String get customRom;

  /// Label for Android version
  ///
  /// In en, this message translates to:
  /// **'Android Version'**
  String get androidVersion;

  /// Label for App Standby Bucket
  ///
  /// In en, this message translates to:
  /// **'Background Activity Mode'**
  String get backgroundActivityMode;

  /// Status when compatibility is OK
  ///
  /// In en, this message translates to:
  /// **'Compatibility: OK'**
  String get compatibilityOk;

  /// Status when issues are detected
  ///
  /// In en, this message translates to:
  /// **'Issues Detected'**
  String get issuesDetected;

  /// Title for detected issues section
  ///
  /// In en, this message translates to:
  /// **'Detected Issues:'**
  String get detectedIssues;

  /// Title for recommendations section
  ///
  /// In en, this message translates to:
  /// **'Recommendations'**
  String get recommendations;

  /// Title for manufacturer settings section
  ///
  /// In en, this message translates to:
  /// **'Manufacturer Settings'**
  String get manufacturerSettings;

  /// Description for manufacturer settings section
  ///
  /// In en, this message translates to:
  /// **'To ensure stable background operation, you need to configure manufacturer-specific device settings.'**
  String get manufacturerSettingsDescription;

  /// Title for autostart settings
  ///
  /// In en, this message translates to:
  /// **'Autostart Application'**
  String get autostartApp;

  /// Subtitle for autostart settings
  ///
  /// In en, this message translates to:
  /// **'Enable autostart for stable operation'**
  String get enableAutostart;

  /// Title for battery optimization settings
  ///
  /// In en, this message translates to:
  /// **'Battery Optimization'**
  String get batteryOptimization;

  /// Subtitle for battery optimization settings
  ///
  /// In en, this message translates to:
  /// **'Disable battery optimization for the application'**
  String get disableBatteryOptimization;

  /// Title for background activity settings
  ///
  /// In en, this message translates to:
  /// **'Background Activity'**
  String get backgroundActivity;

  /// Subtitle for background activity settings
  ///
  /// In en, this message translates to:
  /// **'Allow background activity'**
  String get allowBackgroundActivity;

  /// Button to show instructions
  ///
  /// In en, this message translates to:
  /// **'Show Instructions'**
  String get showInstructions;

  /// Title for WorkManager diagnostics section
  ///
  /// In en, this message translates to:
  /// **'WorkManager Diagnostics'**
  String get workManagerDiagnostics;

  /// Title for last executions list
  ///
  /// In en, this message translates to:
  /// **'Last Executions:'**
  String get lastExecutions;

  /// Message when execution history is empty
  ///
  /// In en, this message translates to:
  /// **'Execution history is empty'**
  String get executionHistoryEmpty;

  /// Label for total executions
  ///
  /// In en, this message translates to:
  /// **'Total'**
  String get total;

  /// Label for successful executions
  ///
  /// In en, this message translates to:
  /// **'Successful'**
  String get successful;

  /// Label for failed executions
  ///
  /// In en, this message translates to:
  /// **'Errors'**
  String get errors;

  /// Label for average delay
  ///
  /// In en, this message translates to:
  /// **'Avg. Delay'**
  String get avgDelay;

  /// Abbreviation for minutes
  ///
  /// In en, this message translates to:
  /// **'min'**
  String get minutesAbbr;

  /// Delay information
  ///
  /// In en, this message translates to:
  /// **'Delay: {minutes} min'**
  String delay(int minutes);

  /// Error information
  ///
  /// In en, this message translates to:
  /// **'Error: {reason}'**
  String errorLabel(String reason);

  /// Issue message for restricted standby bucket
  ///
  /// In en, this message translates to:
  /// **'Application is in restricted background activity mode (Standby Bucket: {bucket})'**
  String appStandbyBucketRestricted(String bucket);

  /// Recommendation to use app more frequently
  ///
  /// In en, this message translates to:
  /// **'Use the application more frequently so the system moves it to active mode'**
  String get useAppMoreFrequently;

  /// Issue message for aggressive battery optimization
  ///
  /// In en, this message translates to:
  /// **'Device from manufacturer with aggressive battery optimization detected ({manufacturer}, {rom})'**
  String aggressiveBatteryOptimization(String manufacturer, String rom);

  /// Recommendation to configure autostart and battery
  ///
  /// In en, this message translates to:
  /// **'It is recommended to configure autostart and disable battery optimization for the application'**
  String get configureAutostartAndBattery;

  /// Recommendation to open manufacturer settings
  ///
  /// In en, this message translates to:
  /// **'Open manufacturer settings through the application menu'**
  String get openManufacturerSettings;

  /// Recommendation for Android 14+
  ///
  /// In en, this message translates to:
  /// **'On Android 14+, make sure Foreground Services start correctly'**
  String get android14ForegroundServices;

  /// Event message when compatibility issues are detected
  ///
  /// In en, this message translates to:
  /// **'Background work issues detected'**
  String get compatibilityIssuesDetected;

  /// Event message when WorkManager task is delayed
  ///
  /// In en, this message translates to:
  /// **'WorkManager task \"{taskName}\" delayed by {hours} hours (expected: {minutes} minutes)'**
  String workManagerTaskDelayed(String taskName, int hours, int minutes);

  /// Event message when foreground service is killed
  ///
  /// In en, this message translates to:
  /// **'Foreground Service \"{serviceName}\" was unexpectedly terminated by the system'**
  String foregroundServiceKilled(String serviceName);

  /// App Standby Bucket status: Active
  ///
  /// In en, this message translates to:
  /// **'Actively Used'**
  String get standbyBucketActive;

  /// App Standby Bucket status: Working Set
  ///
  /// In en, this message translates to:
  /// **'Frequently Used'**
  String get standbyBucketWorkingSet;

  /// App Standby Bucket status: Frequent
  ///
  /// In en, this message translates to:
  /// **'Regularly Used'**
  String get standbyBucketFrequent;

  /// App Standby Bucket status: Rare
  ///
  /// In en, this message translates to:
  /// **'Rarely Used'**
  String get standbyBucketRare;

  /// App Standby Bucket status: Never
  ///
  /// In en, this message translates to:
  /// **'Never Used (Restricted)'**
  String get standbyBucketNever;

  /// App Standby Bucket status: Unknown
  ///
  /// In en, this message translates to:
  /// **'Unknown ({bucket})'**
  String standbyBucketUnknown(int bucket);

  /// App Standby Bucket description: Active
  ///
  /// In en, this message translates to:
  /// **'Actively Used'**
  String get standbyBucketActiveUsed;

  /// App Standby Bucket description: Frequently
  ///
  /// In en, this message translates to:
  /// **'Frequently Used'**
  String get standbyBucketFrequentlyUsed;

  /// App Standby Bucket description: Regularly
  ///
  /// In en, this message translates to:
  /// **'Regularly Used'**
  String get standbyBucketRegularlyUsed;

  /// App Standby Bucket description: Rarely
  ///
  /// In en, this message translates to:
  /// **'Rarely Used'**
  String get standbyBucketRarelyUsed;

  /// App Standby Bucket description: Never
  ///
  /// In en, this message translates to:
  /// **'Never Used (Restricted)'**
  String get standbyBucketNeverUsed;

  /// Title for manufacturer settings dialog
  ///
  /// In en, this message translates to:
  /// **'Settings for Stable Operation'**
  String get manufacturerSettingsTitle;

  /// Description for manufacturer settings dialog
  ///
  /// In en, this message translates to:
  /// **'To ensure stable operation, you need to configure the following settings:'**
  String get manufacturerSettingsDialogDescription;

  /// Step 1: Enable autostart
  ///
  /// In en, this message translates to:
  /// **'1. Enable application autostart'**
  String get enableAutostartStep;

  /// Step 2: Disable battery optimization
  ///
  /// In en, this message translates to:
  /// **'2. Disable battery optimization for the application'**
  String get disableBatteryOptimizationStep;

  /// Step 3: Allow background activity
  ///
  /// In en, this message translates to:
  /// **'3. Allow background activity'**
  String get allowBackgroundActivityStep;

  /// Message showing detected ROM
  ///
  /// In en, this message translates to:
  /// **'Detected ROM: {rom}'**
  String detectedRom(String rom);

  /// Button to skip
  ///
  /// In en, this message translates to:
  /// **'Skip'**
  String get skip;

  /// Button to acknowledge
  ///
  /// In en, this message translates to:
  /// **'Got It'**
  String get gotIt;

  /// Title for background compatibility banner
  ///
  /// In en, this message translates to:
  /// **'Background Operation Settings'**
  String get backgroundCompatibilityBannerTitle;

  /// Message for background compatibility banner
  ///
  /// In en, this message translates to:
  /// **'To ensure stable background operation, you may need to configure device settings.'**
  String get backgroundCompatibilityBannerMessage;

  /// Button to dismiss banner or dialog
  ///
  /// In en, this message translates to:
  /// **'Dismiss'**
  String get dismiss;

  /// Message for successful login
  ///
  /// In en, this message translates to:
  /// **'Login successful!'**
  String get loginSuccessfulMessage;

  /// Button to open settings
  ///
  /// In en, this message translates to:
  /// **'Open Settings'**
  String get openSettingsButton;

  /// Title for compatibility and diagnostics section
  ///
  /// In en, this message translates to:
  /// **'Compatibility & Diagnostics'**
  String get compatibilityDiagnosticsTitle;

  /// Subtitle for compatibility and diagnostics section
  ///
  /// In en, this message translates to:
  /// **'Compatibility check and manufacturer settings configuration'**
  String get compatibilityDiagnosticsSubtitle;

  /// Button to select folder
  ///
  /// In en, this message translates to:
  /// **'Select Folder'**
  String get selectFolderButton;

  /// Button to continue
  ///
  /// In en, this message translates to:
  /// **'Continue'**
  String get continueButton;

  /// Error message when adding folder fails
  ///
  /// In en, this message translates to:
  /// **'Error adding folder: \$error'**
  String errorAddingFolder(String error);

  /// Error message when audiobook group is missing
  ///
  /// In en, this message translates to:
  /// **'No audiobook group provided'**
  String get noAudiobookGroupProvided;

  /// Notification message when sleep timer pauses playback
  ///
  /// In en, this message translates to:
  /// **'Sleep timer: Playback paused'**
  String get sleepTimerPaused;

  /// Error message when speed change fails
  ///
  /// In en, this message translates to:
  /// **'Failed to change speed: \$error'**
  String failedToChangeSpeed(String error);

  /// Button to cancel timer
  ///
  /// In en, this message translates to:
  /// **'Cancel timer'**
  String get cancelTimerButton;

  /// Label for end of chapter
  ///
  /// In en, this message translates to:
  /// **'End of chapter'**
  String get endOfChapterLabel;

  /// Label for at end of chapter
  ///
  /// In en, this message translates to:
  /// **'At end of chapter'**
  String get atEndOfChapterLabel;

  /// Tooltip for sleep timer with duration
  ///
  /// In en, this message translates to:
  /// **'Sleep timer: {duration}'**
  String sleepTimerTooltip(String duration);

  /// Tooltip for setting sleep timer
  ///
  /// In en, this message translates to:
  /// **'Set sleep timer'**
  String get setSleepTimerTooltip;

  /// Sleep timer duration in minutes
  ///
  /// In en, this message translates to:
  /// **'{minutes} min.'**
  String sleepTimerMinutes(int minutes);

  /// Sleep timer duration - 1 hour
  ///
  /// In en, this message translates to:
  /// **'1 hour'**
  String get sleepTimerHour;

  /// Message that app will exit due to sleep timer
  ///
  /// In en, this message translates to:
  /// **'Sleep timer: App will exit'**
  String get sleepTimerAppWillExit;

  /// Label for items in trash
  ///
  /// In en, this message translates to:
  /// **'Items in Trash'**
  String get itemsInTrashLabel;

  /// Label for trash size
  ///
  /// In en, this message translates to:
  /// **'Trash Size'**
  String get trashSizeLabel;

  /// Button to manage trash
  ///
  /// In en, this message translates to:
  /// **'Manage Trash'**
  String get manageTrashButton;

  /// Singular form of file
  ///
  /// In en, this message translates to:
  /// **'file'**
  String get fileLabel;

  /// Plural form of file
  ///
  /// In en, this message translates to:
  /// **'files'**
  String get filesLabel;

  /// Error message when loading trash fails
  ///
  /// In en, this message translates to:
  /// **'Failed to load trash: \$error'**
  String failedToLoadTrash(String error);

  /// Message when item is restored
  ///
  /// In en, this message translates to:
  /// **'Item restored successfully'**
  String get itemRestoredSuccessfully;

  /// Error message when restore fails
  ///
  /// In en, this message translates to:
  /// **'Failed to restore item'**
  String get failedToRestoreItem;

  /// Error message when restoring item fails
  ///
  /// In en, this message translates to:
  /// **'Error restoring item: \$error'**
  String errorRestoringItem(String error);

  /// Title for permanently delete dialog
  ///
  /// In en, this message translates to:
  /// **'Permanently Delete'**
  String get permanentlyDeleteTitle;

  /// Confirmation message for permanent deletion
  ///
  /// In en, this message translates to:
  /// **'Are you sure you want to permanently delete \"{name}\"? This action cannot be undone.'**
  String permanentlyDeleteMessage(String name);

  /// Message when item is permanently deleted
  ///
  /// In en, this message translates to:
  /// **'Item permanently deleted'**
  String get itemPermanentlyDeleted;

  /// Error message when deletion fails
  ///
  /// In en, this message translates to:
  /// **'Failed to delete item'**
  String get failedToDeleteItem;

  /// Error message when deleting item fails
  ///
  /// In en, this message translates to:
  /// **'Error deleting item: \$error'**
  String errorDeletingItem(String error);

  /// Title for clear all trash dialog
  ///
  /// In en, this message translates to:
  /// **'Clear All Trash'**
  String get clearAllTrashTitle;

  /// Confirmation message for clearing all trash
  ///
  /// In en, this message translates to:
  /// **'Are you sure you want to permanently delete all items in trash? This action cannot be undone.'**
  String get clearAllTrashMessage;

  /// Error message when file picker is already open
  ///
  /// In en, this message translates to:
  /// **'File picker is already open. Please close it first.'**
  String get filePickerAlreadyOpen;

  /// Button to apply changes
  ///
  /// In en, this message translates to:
  /// **'Apply'**
  String get applyButton;

  /// Sort option for last message
  ///
  /// In en, this message translates to:
  /// **'Last message'**
  String get lastMessageSort;

  /// Sort option for topic name
  ///
  /// In en, this message translates to:
  /// **'Topic name'**
  String get topicNameSort;

  /// Sort option for posting time
  ///
  /// In en, this message translates to:
  /// **'Posting time'**
  String get postingTimeSort;

  /// Error message when operation times out
  ///
  /// In en, this message translates to:
  /// **'Operation timed out. Please try again.'**
  String get operationTimedOut;

  /// Error message when permission is denied for downloads
  ///
  /// In en, this message translates to:
  /// **'Permission denied. Please check app permissions in settings.'**
  String get permissionDeniedDownloads;

  /// Error message when download is not found
  ///
  /// In en, this message translates to:
  /// **'Download not found. It may have been removed.'**
  String get downloadNotFound;

  /// Generic error message
  ///
  /// In en, this message translates to:
  /// **'An error occurred. Please try again.'**
  String get anErrorOccurred;

  /// Error message during initialization
  ///
  /// In en, this message translates to:
  /// **'Initialization error: \$error'**
  String initializationError(String error);

  /// Critical error message
  ///
  /// In en, this message translates to:
  /// **'Critical error: \$error'**
  String criticalError(String error);

  /// Error message when checking capabilities fails
  ///
  /// In en, this message translates to:
  /// **'Error checking capabilities: \$error'**
  String capabilityCheckError(String error);

  /// Message when files are selected
  ///
  /// In en, this message translates to:
  /// **'Files selected: \$count'**
  String filesSelected(int count);

  /// Error message when file selection fails
  ///
  /// In en, this message translates to:
  /// **'Error selecting files: \$error'**
  String fileSelectionError(String error);

  /// Message when images are selected
  ///
  /// In en, this message translates to:
  /// **'Images selected: \$count'**
  String imagesSelected(int count);

  /// Error message when image selection fails
  ///
  /// In en, this message translates to:
  /// **'Error selecting images: \$error'**
  String imageSelectionError(String error);

  /// Title for test notification
  ///
  /// In en, this message translates to:
  /// **'Test notification'**
  String get testNotificationTitle;

  /// Body text for test notification
  ///
  /// In en, this message translates to:
  /// **'This is a test notification from JaBook'**
  String get testNotificationBody;

  /// Message when notification is sent
  ///
  /// In en, this message translates to:
  /// **'Notification sent'**
  String get notificationSent;

  /// Error message when notification fails
  ///
  /// In en, this message translates to:
  /// **'Failed to send notification (channel not implemented)'**
  String get failedToSendNotification;

  /// Message showing Bluetooth availability
  ///
  /// In en, this message translates to:
  /// **'Bluetooth available: \$available'**
  String bluetoothAvailable(String available);

  /// Message showing paired devices count
  ///
  /// In en, this message translates to:
  /// **'Paired devices: \$count'**
  String pairedDevicesCount(int count);

  /// Error message when Bluetooth check fails
  ///
  /// In en, this message translates to:
  /// **'Error checking Bluetooth: \$error'**
  String bluetoothCheckError(String error);

  /// Title for system capabilities section
  ///
  /// In en, this message translates to:
  /// **'System Capabilities'**
  String get systemCapabilitiesTitle;

  /// Name for file access capability
  ///
  /// In en, this message translates to:
  /// **'File Access'**
  String get fileAccessCapability;

  /// Name for image access capability
  ///
  /// In en, this message translates to:
  /// **'Image Access'**
  String get imageAccessCapability;

  /// Name for camera capability
  ///
  /// In en, this message translates to:
  /// **'Camera'**
  String get cameraCapability;

  /// Message when photo is taken
  ///
  /// In en, this message translates to:
  /// **'Photo taken: \$path'**
  String photoTaken(String path);

  /// Message when photo is not taken
  ///
  /// In en, this message translates to:
  /// **'Photo not taken'**
  String get photoNotTaken;

  /// Error message when camera fails
  ///
  /// In en, this message translates to:
  /// **'Camera error: \$error'**
  String cameraError(String error);

  /// Name for notifications capability
  ///
  /// In en, this message translates to:
  /// **'Notifications'**
  String get notificationsCapability;

  /// Button to explain capabilities
  ///
  /// In en, this message translates to:
  /// **'Explain Capabilities'**
  String get capabilityExplanationButton;

  /// Button to test capability
  ///
  /// In en, this message translates to:
  /// **'Test'**
  String get testButton;

  /// Title for permissions onboarding dialog
  ///
  /// In en, this message translates to:
  /// **'Permissions for JaBook'**
  String get permissionsForJaBookTitle;

  /// Title for file access permission
  ///
  /// In en, this message translates to:
  /// **'File Access'**
  String get fileAccessPermissionTitle;

  /// Description for file access permission
  ///
  /// In en, this message translates to:
  /// **'Needed to save and play audiobooks. On Android 11+, you\'ll need to enable \"All files access\" in system settings.'**
  String get fileAccessPermissionDescription;

  /// Title for notifications permission
  ///
  /// In en, this message translates to:
  /// **'Notifications'**
  String get notificationsPermissionTitle;

  /// Title for battery optimization permission
  ///
  /// In en, this message translates to:
  /// **'Battery Optimization'**
  String get batteryOptimizationPermissionTitle;

  /// Description for battery optimization permission
  ///
  /// In en, this message translates to:
  /// **'So the app can work in background for playback.'**
  String get batteryOptimizationPermissionDescription;

  /// Message explaining why permissions are needed
  ///
  /// In en, this message translates to:
  /// **'These permissions will help provide a better app experience.'**
  String get permissionsHelpMessage;

  /// Label for URL display
  ///
  /// In en, this message translates to:
  /// **'URL: \$url'**
  String urlLabel(String url);

  /// Title for RuTracker section
  ///
  /// In en, this message translates to:
  /// **'RuTracker'**
  String get rutrackerTitle;

  /// Button text for WebView login
  ///
  /// In en, this message translates to:
  /// **'Login to RuTracker via WebView'**
  String get webViewLoginButton;

  /// Subtitle for WebView login button
  ///
  /// In en, this message translates to:
  /// **'Pass Cloudflare/captcha and save cookies for client'**
  String get webViewLoginSubtitle;

  /// Success message when cookies are saved
  ///
  /// In en, this message translates to:
  /// **'Cookies saved for HTTP client'**
  String get cookieSavedMessage;

  /// Error message when search results parsing fails due to encoding issue
  ///
  /// In en, this message translates to:
  /// **'Failed to parse search results due to encoding issue. This may be a temporary server problem. Please try again. If the problem persists, try changing the mirror in Settings → Sources.'**
  String get failedToParseSearchResultsEncoding;

  /// Error message when search results parsing fails due to page structure change
  ///
  /// In en, this message translates to:
  /// **'Failed to parse search results. The page structure may have changed. Please try again. If the problem persists, try changing the mirror in Settings → Sources.'**
  String get failedToParseSearchResultsStructure;

  /// Error message when search fails
  ///
  /// In en, this message translates to:
  /// **'Search failed: {errorType}'**
  String searchFailedMessage(String errorType);

  /// Sort option label for topic title
  ///
  /// In en, this message translates to:
  /// **'Topic title'**
  String get topicTitleSort;

  /// Sort option label for post time
  ///
  /// In en, this message translates to:
  /// **'Post time'**
  String get postTimeSort;

  /// Success message when local stream server starts
  ///
  /// In en, this message translates to:
  /// **'Local stream server started on http://{host}:{port}'**
  String localStreamServerStarted(String host, int port);

  /// Error message when stream server fails to start
  ///
  /// In en, this message translates to:
  /// **'Failed to start stream server: {error}'**
  String failedToStartStreamServer(String error);

  /// Success message when local stream server stops
  ///
  /// In en, this message translates to:
  /// **'Local stream server stopped'**
  String get localStreamServerStopped;

  /// Error message when stream server fails to stop
  ///
  /// In en, this message translates to:
  /// **'Failed to stop stream server: {error}'**
  String failedToStopStreamServer(String error);

  /// Error message when book ID parameter is missing
  ///
  /// In en, this message translates to:
  /// **'Missing book ID parameter'**
  String get missingBookIdParameter;

  /// Error message when file index parameter is invalid
  ///
  /// In en, this message translates to:
  /// **'Invalid file index parameter'**
  String get invalidFileIndexParameter;

  /// Error message when file is not found
  ///
  /// In en, this message translates to:
  /// **'File not found'**
  String get fileNotFound;

  /// Error message for streaming errors
  ///
  /// In en, this message translates to:
  /// **'Streaming error'**
  String get streamingError;

  /// Error message when range header is invalid
  ///
  /// In en, this message translates to:
  /// **'Invalid range header'**
  String get invalidRangeHeader;

  /// Error message when requested range cannot be satisfied
  ///
  /// In en, this message translates to:
  /// **'Requested range not satisfiable'**
  String get requestedRangeNotSatisfiable;

  /// Error message for range request errors
  ///
  /// In en, this message translates to:
  /// **'Range request error'**
  String get rangeRequestError;

  /// Error message for static file errors
  ///
  /// In en, this message translates to:
  /// **'Static file error'**
  String get staticFileError;

  /// Error message when audio service fails to start
  ///
  /// In en, this message translates to:
  /// **'Failed to start audio service: {error}'**
  String failedToStartAudioService(String error);

  /// Error message when media playback fails
  ///
  /// In en, this message translates to:
  /// **'Failed to play media: {error}'**
  String failedToPlayMedia(String error);

  /// Error message when media pause fails
  ///
  /// In en, this message translates to:
  /// **'Failed to pause media: {error}'**
  String failedToPauseMedia(String error);

  /// Error message when media stop fails
  ///
  /// In en, this message translates to:
  /// **'Failed to stop media: {error}'**
  String failedToStopMedia(String error);

  /// Error message when seek operation fails
  ///
  /// In en, this message translates to:
  /// **'Failed to seek: {error}'**
  String failedToSeek(String error);

  /// Error message when setting playback speed fails
  ///
  /// In en, this message translates to:
  /// **'Failed to set speed: {error}'**
  String failedToSetSpeed(String error);

  /// Error message when logger initialization fails
  ///
  /// In en, this message translates to:
  /// **'Failed to initialize logger'**
  String get failedToInitializeLogger;

  /// Error message when log writing fails
  ///
  /// In en, this message translates to:
  /// **'Failed to write log'**
  String get failedToWriteLog;

  /// Error message when log rotation fails
  ///
  /// In en, this message translates to:
  /// **'Log rotation failed'**
  String get logRotationFailed;

  /// Error message when log rotation fails
  ///
  /// In en, this message translates to:
  /// **'Failed to rotate logs'**
  String get failedToRotateLogs;

  /// Error message when cleaning old logs fails
  ///
  /// In en, this message translates to:
  /// **'Failed to clean old logs'**
  String get failedToCleanOldLogs;

  /// Error message when sharing logs fails
  ///
  /// In en, this message translates to:
  /// **'Error sharing logs: {error}'**
  String errorSharingLogs(String error);

  /// Error message when sharing logs fails
  ///
  /// In en, this message translates to:
  /// **'Failed to share logs'**
  String get failedToShareLogs;

  /// Error message when reading logs fails
  ///
  /// In en, this message translates to:
  /// **'Failed to read logs'**
  String get failedToReadLogs;

  /// Error message when CacheManager is not initialized
  ///
  /// In en, this message translates to:
  /// **'CacheManager not initialized'**
  String get cacheManagerNotInitialized;

  /// Error message when parsing search results fails
  ///
  /// In en, this message translates to:
  /// **'Failed to parse search results: {error}'**
  String failedToParseSearchResults(String error);

  /// Error message when parsing topic details fails
  ///
  /// In en, this message translates to:
  /// **'Failed to parse topic details: {error}'**
  String failedToParseTopicDetails(String error);

  /// Error message when parsing categories fails
  ///
  /// In en, this message translates to:
  /// **'Failed to parse categories'**
  String get failedToParseCategories;

  /// Error message when parsing category topics fails
  ///
  /// In en, this message translates to:
  /// **'Failed to parse category topics'**
  String get failedToParseCategoryTopics;

  /// Category name for radio plays
  ///
  /// In en, this message translates to:
  /// **'Radio Play'**
  String get radioPlayCategory;

  /// Category name for audiobooks
  ///
  /// In en, this message translates to:
  /// **'Audiobook'**
  String get audiobookCategory;

  /// Category name for biographies
  ///
  /// In en, this message translates to:
  /// **'Biography'**
  String get biographyCategory;

  /// Category name for memoirs
  ///
  /// In en, this message translates to:
  /// **'Memoirs'**
  String get memoirsCategory;

  /// Category name for history
  ///
  /// In en, this message translates to:
  /// **'History'**
  String get historyCategory;

  /// Label for added date
  ///
  /// In en, this message translates to:
  /// **'Added'**
  String get addedLabel;

  /// Error message when magnet URL is missing info hash
  ///
  /// In en, this message translates to:
  /// **'Invalid magnet URL: missing info hash'**
  String get invalidMagnetUrlMissingHash;

  /// Error message when info hash has invalid length
  ///
  /// In en, this message translates to:
  /// **'Invalid info hash length'**
  String get invalidInfoHashLength;

  /// Error message when download fails to start with error details
  ///
  /// In en, this message translates to:
  /// **'Failed to start download: {error}'**
  String failedToStartDownloadWithError(String error);

  /// Error message when torrent download is not found
  ///
  /// In en, this message translates to:
  /// **'Download not found'**
  String get downloadNotFoundTorrent;

  /// Error message when download pause fails
  ///
  /// In en, this message translates to:
  /// **'Failed to pause download: {error}'**
  String failedToPauseDownload(String error);

  /// Error message when download resume fails
  ///
  /// In en, this message translates to:
  /// **'Failed to resume download: {error}'**
  String failedToResumeDownload(String error);

  /// Error message when download removal fails
  ///
  /// In en, this message translates to:
  /// **'Failed to remove download: {error}'**
  String failedToRemoveDownload(String error);

  /// Error message when getting active downloads fails
  ///
  /// In en, this message translates to:
  /// **'Failed to get active downloads: {error}'**
  String failedToGetActiveDownloads(String error);

  /// Error message when torrent manager shutdown fails
  ///
  /// In en, this message translates to:
  /// **'Failed to shutdown torrent manager: {error}'**
  String failedToShutdownTorrentManager(String error);

  /// Error message when no healthy endpoints are available
  ///
  /// In en, this message translates to:
  /// **'No healthy endpoints available'**
  String get noHealthyEndpointsAvailable;

  /// Error message when AuthRepositoryProvider is not properly configured
  ///
  /// In en, this message translates to:
  /// **'AuthRepositoryProvider must be overridden with proper context'**
  String get authRepositoryProviderMustBeOverridden;

  /// Deprecated message for using EndpointManager.getActiveEndpoint()
  ///
  /// In en, this message translates to:
  /// **'Use EndpointManager.getActiveEndpoint() for dynamic mirror selection'**
  String get useEndpointManagerGetActiveEndpoint;

  /// Error message when CacheManager is not initialized in config
  ///
  /// In en, this message translates to:
  /// **'CacheManager not initialized'**
  String get cacheManagerNotInitializedConfig;

  /// Error message when search fails with specific message
  ///
  /// In en, this message translates to:
  /// **'Search failed: {message}'**
  String searchFailedWithMessage(String message);

  /// Error message when audiobook search fails
  ///
  /// In en, this message translates to:
  /// **'Failed to search audiobooks'**
  String get failedToSearchAudiobooks;

  /// Error message when fetching categories fails
  ///
  /// In en, this message translates to:
  /// **'Failed to fetch categories: {message}'**
  String failedToFetchCategories(String message);

  /// Error message when getting categories fails
  ///
  /// In en, this message translates to:
  /// **'Failed to get categories'**
  String get failedToGetCategories;

  /// Error message when getting category audiobooks fails with message
  ///
  /// In en, this message translates to:
  /// **'Failed to get category audiobooks: {message}'**
  String failedToGetCategoryAudiobooksWithMessage(String message);

  /// Error message when getting category audiobooks fails
  ///
  /// In en, this message translates to:
  /// **'Failed to get category audiobooks'**
  String get failedToGetCategoryAudiobooks;

  /// Error message when fetching audiobook details fails
  ///
  /// In en, this message translates to:
  /// **'Failed to fetch audiobook details: {message}'**
  String failedToFetchAudiobookDetails(String message);

  /// Error message when getting audiobook details fails
  ///
  /// In en, this message translates to:
  /// **'Failed to get audiobook details'**
  String get failedToGetAudiobookDetails;

  /// Error message when fetching new releases fails
  ///
  /// In en, this message translates to:
  /// **'Failed to fetch new releases: {message}'**
  String failedToFetchNewReleases(String message);

  /// Error message when saving credentials fails
  ///
  /// In en, this message translates to:
  /// **'Failed to save credentials: {error}'**
  String failedToSaveCredentials(String error);

  /// Error message when retrieving credentials fails
  ///
  /// In en, this message translates to:
  /// **'Failed to retrieve credentials: {error}'**
  String failedToRetrieveCredentials(String error);

  /// Error message when clearing credentials fails
  ///
  /// In en, this message translates to:
  /// **'Failed to clear credentials: {error}'**
  String failedToClearCredentials(String error);

  /// Error message when there are no credentials to export
  ///
  /// In en, this message translates to:
  /// **'No credentials to export'**
  String get noCredentialsToExport;

  /// Error message when export format is unsupported
  ///
  /// In en, this message translates to:
  /// **'Unsupported export format: {format}'**
  String unsupportedExportFormat(String format);

  /// Error message when CSV format is invalid
  ///
  /// In en, this message translates to:
  /// **'Invalid CSV format'**
  String get invalidCsvFormat;

  /// Error message when CSV data is invalid
  ///
  /// In en, this message translates to:
  /// **'Invalid CSV data'**
  String get invalidCsvData;

  /// Error message when JSON format is invalid
  ///
  /// In en, this message translates to:
  /// **'Invalid JSON format'**
  String get invalidJsonFormat;

  /// Error message when import format is unsupported
  ///
  /// In en, this message translates to:
  /// **'Unsupported import format: {format}'**
  String unsupportedImportFormat(String format);

  /// Error message when importing credentials fails
  ///
  /// In en, this message translates to:
  /// **'Failed to import credentials: {error}'**
  String failedToImportCredentials(String error);

  /// Generic error message with error details
  ///
  /// In en, this message translates to:
  /// **'Error: {error}'**
  String errorWithDetails(String error);

  /// Singular form of 'file'
  ///
  /// In en, this message translates to:
  /// **'file'**
  String get fileSingular;

  /// Plural form of 'file'
  ///
  /// In en, this message translates to:
  /// **'files'**
  String get filePlural;

  /// Confirmation message for deleting selected audiobooks
  ///
  /// In en, this message translates to:
  /// **'Are you sure you want to delete {count} selected audiobook(s)?'**
  String deleteSelectedAudiobooksConfirmation(int count);

  /// Description of what clearing playback cache does
  ///
  /// In en, this message translates to:
  /// **'This will clear the playback cache. Playback may be slower until cache is rebuilt.'**
  String get clearPlaybackCacheDescription;

  /// Description of what clearing all cache does
  ///
  /// In en, this message translates to:
  /// **'This will clear all cache including playback cache, temporary files, and old logs.'**
  String get clearAllCacheDescription;

  /// Title for session expired error dialog
  ///
  /// In en, this message translates to:
  /// **'Session Expired'**
  String get sessionExpiredTitle;

  /// Message for session expired error
  ///
  /// In en, this message translates to:
  /// **'Your session has expired. Please log in again.'**
  String get sessionExpiredMessage;

  /// Title for invalid credentials error dialog
  ///
  /// In en, this message translates to:
  /// **'Authorization Error'**
  String get invalidCredentialsTitle;

  /// Message for invalid credentials error
  ///
  /// In en, this message translates to:
  /// **'Invalid username or password. Please check your credentials.'**
  String get invalidCredentialsMessage;

  /// Title for login required error dialog
  ///
  /// In en, this message translates to:
  /// **'Authentication Required'**
  String get loginRequiredTitle;

  /// Message for login required error
  ///
  /// In en, this message translates to:
  /// **'You need to log in to perform this action.'**
  String get loginRequiredMessage;

  /// Title for general authorization error dialog
  ///
  /// In en, this message translates to:
  /// **'Authorization Error'**
  String get authorizationErrorTitle;

  /// Message for access denied error
  ///
  /// In en, this message translates to:
  /// **'Access denied. Please check your credentials or log in again.'**
  String get accessDeniedMessage;

  /// Title for network error dialog
  ///
  /// In en, this message translates to:
  /// **'Network Error'**
  String get networkErrorTitle;

  /// Message for network request failed error
  ///
  /// In en, this message translates to:
  /// **'Failed to complete the request. Please check your internet connection.'**
  String get networkRequestFailedMessage;

  /// Generic error message
  ///
  /// In en, this message translates to:
  /// **'An error occurred while performing the operation.'**
  String get errorOccurredMessage;

  /// Snackbar message for session expired
  ///
  /// In en, this message translates to:
  /// **'Session expired. Please log in again.'**
  String get sessionExpiredSnackBar;

  /// Snackbar message for invalid credentials
  ///
  /// In en, this message translates to:
  /// **'Invalid username or password.'**
  String get invalidCredentialsSnackBar;

  /// Snackbar message for authorization error
  ///
  /// In en, this message translates to:
  /// **'Authorization error. Please check your credentials.'**
  String get authorizationErrorSnackBar;

  /// Snackbar message for network error
  ///
  /// In en, this message translates to:
  /// **'Network error. Please check your connection.'**
  String get networkErrorSnackBar;

  /// Message when captcha verification is required
  ///
  /// In en, this message translates to:
  /// **'Captcha verification required. Please try again later.'**
  String get captchaVerificationRequired;

  /// Network error message asking user to check connection
  ///
  /// In en, this message translates to:
  /// **'Network error. Please check your connection and try again.'**
  String get networkErrorCheckConnection;

  /// Message when authentication fails
  ///
  /// In en, this message translates to:
  /// **'Authentication failed. Please check your credentials and try again.'**
  String get authenticationFailedMessage;

  /// Login failed message with error details
  ///
  /// In en, this message translates to:
  /// **'Login failed: {error}'**
  String loginFailedWithError(String error);

  /// Error message when no accessible audio files are found
  ///
  /// In en, this message translates to:
  /// **'No accessible audio files found'**
  String get noAccessibleAudioFiles;

  /// About screen title
  ///
  /// In en, this message translates to:
  /// **'About'**
  String get aboutTitle;

  /// App slogan
  ///
  /// In en, this message translates to:
  /// **'Modern audiobook player for torrents'**
  String get aboutAppSlogan;

  /// Label for app version
  ///
  /// In en, this message translates to:
  /// **'App Version'**
  String get appVersion;

  /// Label for build number
  ///
  /// In en, this message translates to:
  /// **'Build'**
  String get buildNumber;

  /// Label for package ID
  ///
  /// In en, this message translates to:
  /// **'Package ID'**
  String get packageId;

  /// License section title
  ///
  /// In en, this message translates to:
  /// **'License'**
  String get licenseTitle;

  /// License description
  ///
  /// In en, this message translates to:
  /// **'JaBook is distributed under the Apache 2.0 license. Tap to view the full license text and list of third-party libraries.'**
  String get licenseDescription;

  /// Menu item for main license
  ///
  /// In en, this message translates to:
  /// **'Main License'**
  String get mainLicense;

  /// Menu item for third-party libraries
  ///
  /// In en, this message translates to:
  /// **'Third-Party Libraries'**
  String get thirdPartyLicenses;

  /// Menu item for Telegram channel
  ///
  /// In en, this message translates to:
  /// **'Telegram Channel'**
  String get telegramChannel;

  /// Subtitle for Telegram channel
  ///
  /// In en, this message translates to:
  /// **'Open channel in Telegram'**
  String get openTelegramChannel;

  /// Menu item for contacting developer
  ///
  /// In en, this message translates to:
  /// **'Contact Developer'**
  String get contactDeveloper;

  /// Subtitle for email client
  ///
  /// In en, this message translates to:
  /// **'Open email client'**
  String get openEmailClient;

  /// Menu item for supporting project
  ///
  /// In en, this message translates to:
  /// **'Support Project'**
  String get supportProject;

  /// Subtitle for supporting project
  ///
  /// In en, this message translates to:
  /// **'Donate or support development'**
  String get supportProjectDescription;

  /// Menu item for GitHub repository
  ///
  /// In en, this message translates to:
  /// **'GitHub'**
  String get githubRepository;

  /// Subtitle for GitHub repository
  ///
  /// In en, this message translates to:
  /// **'Source code and changelog'**
  String get githubRepositoryDescription;

  /// Menu item for changelog
  ///
  /// In en, this message translates to:
  /// **'Changelog'**
  String get changelog;

  /// Subtitle for changelog
  ///
  /// In en, this message translates to:
  /// **'Version history and updates'**
  String get changelogDescription;

  /// Menu item for GitHub issues
  ///
  /// In en, this message translates to:
  /// **'Issues'**
  String get issues;

  /// Subtitle for issues
  ///
  /// In en, this message translates to:
  /// **'Report bugs and request features'**
  String get issuesDescription;

  /// About developer section title
  ///
  /// In en, this message translates to:
  /// **'About Developer'**
  String get aboutDeveloper;

  /// Text about developer
  ///
  /// In en, this message translates to:
  /// **'JaBook is developed by Jabook Contributors team. This is an open source project created for convenient listening to audiobooks from open sources and torrents. No ads, no registration — just pure listening.'**
  String get aboutDeveloperText;

  /// Button for copying information
  ///
  /// In en, this message translates to:
  /// **'Copy'**
  String get copyInfo;

  /// Message after copying
  ///
  /// In en, this message translates to:
  /// **'Info copied'**
  String get infoCopied;

  /// Error message when opening link fails
  ///
  /// In en, this message translates to:
  /// **'Failed to open link'**
  String get failedToOpenLink;

  /// Error message when opening email client fails
  ///
  /// In en, this message translates to:
  /// **'Failed to open email client'**
  String get failedToOpenEmail;

  /// Subtitle for viewing license in app
  ///
  /// In en, this message translates to:
  /// **'View in app'**
  String get viewInApp;

  /// Subtitle for viewing licenses
  ///
  /// In en, this message translates to:
  /// **'View licenses'**
  String get viewLicenses;

  /// Menu item for viewing license on GitHub
  ///
  /// In en, this message translates to:
  /// **'License on GitHub'**
  String get licenseOnGitHub;

  /// Subtitle for viewing license file
  ///
  /// In en, this message translates to:
  /// **'View LICENSE file'**
  String get viewLicenseFile;

  /// Subtitle for 4PDA forum
  ///
  /// In en, this message translates to:
  /// **'App discussion, questions and reviews'**
  String get appDiscussionQuestionsReviews;

  /// Developer team name
  ///
  /// In en, this message translates to:
  /// **'Jabook Contributors'**
  String get jabookContributors;

  /// GitHub service name
  ///
  /// In en, this message translates to:
  /// **'GitHub'**
  String get github;

  /// Accessibility label for version section
  ///
  /// In en, this message translates to:
  /// **'Version information. Long press to copy.'**
  String get versionInformationLongPress;

  /// Default value for unknown data
  ///
  /// In en, this message translates to:
  /// **'Unknown'**
  String get unknown;

  /// Email subject for feedback
  ///
  /// In en, this message translates to:
  /// **'JaBook - Feedback'**
  String get emailFeedbackSubject;

  /// App label in email
  ///
  /// In en, this message translates to:
  /// **'App: JaBook'**
  String get emailFeedbackApp;

  /// Version label in email
  ///
  /// In en, this message translates to:
  /// **'Version:'**
  String get emailFeedbackVersion;

  /// Device label in email
  ///
  /// In en, this message translates to:
  /// **'Device:'**
  String get emailFeedbackDevice;

  /// Android version label in email
  ///
  /// In en, this message translates to:
  /// **'Android:'**
  String get emailFeedbackAndroid;

  /// Description label in email
  ///
  /// In en, this message translates to:
  /// **'Description of issue / suggestion:'**
  String get emailFeedbackDescription;

  /// Description of About section in settings
  ///
  /// In en, this message translates to:
  /// **'App information and links'**
  String get aboutSectionDescription;

  /// Subtitle for About section in settings
  ///
  /// In en, this message translates to:
  /// **'Version, license, and developer information'**
  String get aboutSectionSubtitle;

  /// 4PDA forum name
  ///
  /// In en, this message translates to:
  /// **'4PDA'**
  String get forum4Pda;

  /// Title for rewind duration setting
  ///
  /// In en, this message translates to:
  /// **'Rewind Duration'**
  String get rewindDurationTitle;

  /// Title for forward duration setting
  ///
  /// In en, this message translates to:
  /// **'Forward Duration'**
  String get forwardDurationTitle;

  /// Title for inactivity timeout setting
  ///
  /// In en, this message translates to:
  /// **'Inactivity Timeout'**
  String get inactivityTimeoutTitle;

  /// Label for inactivity timeout setting
  ///
  /// In en, this message translates to:
  /// **'Set inactivity timeout'**
  String get inactivityTimeoutLabel;

  /// Singular form of minute
  ///
  /// In en, this message translates to:
  /// **'minute'**
  String get minute;

  /// Label for rewind action
  ///
  /// In en, this message translates to:
  /// **'Rewind'**
  String get rewind;

  /// Label for forward action
  ///
  /// In en, this message translates to:
  /// **'Forward'**
  String get forward;

  /// Label for current playback position
  ///
  /// In en, this message translates to:
  /// **'Current'**
  String get currentPosition;

  /// Label for new playback position after skip
  ///
  /// In en, this message translates to:
  /// **'New'**
  String get newPosition;

  /// Label for seconds unit
  ///
  /// In en, this message translates to:
  /// **'seconds'**
  String get secondsLabel;

  /// Accessibility label for language settings section
  ///
  /// In en, this message translates to:
  /// **'Language settings'**
  String get languageSettingsLabel;

  /// Accessibility label for mirror settings section
  ///
  /// In en, this message translates to:
  /// **'Mirror and source settings'**
  String get mirrorSourceSettingsLabel;

  /// Accessibility label for RuTracker session section
  ///
  /// In en, this message translates to:
  /// **'RuTracker session management'**
  String get rutrackerSessionLabel;

  /// Accessibility label for metadata section
  ///
  /// In en, this message translates to:
  /// **'Metadata management'**
  String get metadataManagementLabel;

  /// Accessibility label for theme settings section
  ///
  /// In en, this message translates to:
  /// **'Theme settings'**
  String get themeSettingsLabel;

  /// Accessibility label for audio settings section
  ///
  /// In en, this message translates to:
  /// **'Audio playback settings'**
  String get audioPlaybackSettingsLabel;

  /// Accessibility label for download settings section
  ///
  /// In en, this message translates to:
  /// **'Download settings'**
  String get downloadSettingsLabel;

  /// Accessibility label for library folder settings section
  ///
  /// In en, this message translates to:
  /// **'Library folder settings'**
  String get libraryFolderSettingsLabel;

  /// Accessibility label for storage management section
  ///
  /// In en, this message translates to:
  /// **'Storage management'**
  String get storageManagementLabel;

  /// Accessibility label for cache settings section
  ///
  /// In en, this message translates to:
  /// **'Cache settings'**
  String get cacheSettingsLabel;

  /// Accessibility label for app permissions section
  ///
  /// In en, this message translates to:
  /// **'App permissions'**
  String get appPermissionsLabel;

  /// Accessibility label for about app section
  ///
  /// In en, this message translates to:
  /// **'About app'**
  String get aboutAppLabel;

  /// Accessibility label for background compatibility section
  ///
  /// In en, this message translates to:
  /// **'Background task compatibility'**
  String get backgroundTaskCompatibilityLabel;

  /// Accessibility label for backup and restore section
  ///
  /// In en, this message translates to:
  /// **'Backup and restore'**
  String get backupRestoreLabel;

  /// Button to enter selection mode for favorites
  ///
  /// In en, this message translates to:
  /// **'Select'**
  String get selectFavorites;

  /// Button to delete selected favorites
  ///
  /// In en, this message translates to:
  /// **'Delete Selected'**
  String get clearSelectedFavorites;

  /// Button to clear all favorites
  ///
  /// In en, this message translates to:
  /// **'Clear All'**
  String get clearAllFavorites;

  /// Title for clear all favorites confirmation dialog
  ///
  /// In en, this message translates to:
  /// **'Clear All Favorites?'**
  String get clearAllFavoritesTitle;

  /// Message for clear all favorites confirmation dialog
  ///
  /// In en, this message translates to:
  /// **'This will remove all favorite audiobooks. This action cannot be undone.'**
  String get clearAllFavoritesMessage;

  /// Message when favorites are cleared
  ///
  /// In en, this message translates to:
  /// **'Favorites cleared'**
  String get favoritesCleared;

  /// Message when favorites are deleted
  ///
  /// In en, this message translates to:
  /// **'{count} favorite(s) deleted'**
  String favoritesDeleted(int count);

  /// Message when trying to delete but no favorites are selected
  ///
  /// In en, this message translates to:
  /// **'No favorites selected'**
  String get noFavoritesSelected;

  /// Label for selected items count
  ///
  /// In en, this message translates to:
  /// **'selected'**
  String get selected;

  /// Label for sort options section in library filter dialog
  ///
  /// In en, this message translates to:
  /// **'Sort by:'**
  String get sortByLabel;

  /// Label for group options section in library filter dialog
  ///
  /// In en, this message translates to:
  /// **'Group by:'**
  String get groupByLabel;

  /// Sort option: name ascending
  ///
  /// In en, this message translates to:
  /// **'Name (A-Z)'**
  String get sortByNameAsc;

  /// Sort option: name descending
  ///
  /// In en, this message translates to:
  /// **'Name (Z-A)'**
  String get sortByNameDesc;

  /// Sort option: size ascending
  ///
  /// In en, this message translates to:
  /// **'Size (Smallest)'**
  String get sortBySizeAsc;

  /// Sort option: size descending
  ///
  /// In en, this message translates to:
  /// **'Size (Largest)'**
  String get sortBySizeDesc;

  /// Sort option: date ascending
  ///
  /// In en, this message translates to:
  /// **'Date (Oldest)'**
  String get sortByDateAsc;

  /// Sort option: date descending
  ///
  /// In en, this message translates to:
  /// **'Date (Newest)'**
  String get sortByDateDesc;

  /// Sort option: files ascending
  ///
  /// In en, this message translates to:
  /// **'Files (Fewest)'**
  String get sortByFilesAsc;

  /// Sort option: files descending
  ///
  /// In en, this message translates to:
  /// **'Files (Most)'**
  String get sortByFilesDesc;

  /// Group option: no grouping
  ///
  /// In en, this message translates to:
  /// **'None'**
  String get groupByNone;

  /// Group option: group by first letter
  ///
  /// In en, this message translates to:
  /// **'First Letter'**
  String get groupByFirstLetter;

  /// Message shown while scanning library for audiobooks
  ///
  /// In en, this message translates to:
  /// **'Scanning library...'**
  String get scanningLibrary;

  /// Title when manufacturer settings are not available
  ///
  /// In en, this message translates to:
  /// **'Settings Not Available'**
  String get manufacturerSettingsNotAvailable;

  /// Message when manufacturer settings are not available
  ///
  /// In en, this message translates to:
  /// **'Manufacturer-specific settings are only available on Android devices.'**
  String get manufacturerSettingsNotAvailableMessage;

  /// Default title for manufacturer settings instructions
  ///
  /// In en, this message translates to:
  /// **'Settings for Stable Operation'**
  String get manufacturerSettingsDefaultTitle;

  /// Default message for manufacturer settings instructions
  ///
  /// In en, this message translates to:
  /// **'To ensure stable operation, you need to configure the following settings:'**
  String get manufacturerSettingsDefaultMessage;

  /// Default step 1 for manufacturer settings
  ///
  /// In en, this message translates to:
  /// **'1. Enable application autostart'**
  String get manufacturerSettingsDefaultStep1;

  /// Default step 2 for manufacturer settings
  ///
  /// In en, this message translates to:
  /// **'2. Disable battery optimization for the application'**
  String get manufacturerSettingsDefaultStep2;

  /// Default step 3 for manufacturer settings
  ///
  /// In en, this message translates to:
  /// **'3. Allow background activity'**
  String get manufacturerSettingsDefaultStep3;

  /// Title for MIUI manufacturer settings
  ///
  /// In en, this message translates to:
  /// **'MIUI Settings for Stable Operation'**
  String get manufacturerSettingsMiuiTitle;

  /// Message for MIUI manufacturer settings
  ///
  /// In en, this message translates to:
  /// **'On Xiaomi/Redmi/Poco devices, you need to configure the following settings:'**
  String get manufacturerSettingsMiuiMessage;

  /// Step 1 for MIUI settings
  ///
  /// In en, this message translates to:
  /// **'1. Autostart: Settings → Apps → Permission management → Autostart → Enable for {appName}'**
  String manufacturerSettingsMiuiStep1(String appName);

  /// Step 2 for MIUI settings
  ///
  /// In en, this message translates to:
  /// **'2. Battery optimization: Settings → Battery → Battery optimization → Select {appName} → Don\'t optimize'**
  String manufacturerSettingsMiuiStep2(String appName);

  /// Step 3 for MIUI settings
  ///
  /// In en, this message translates to:
  /// **'3. Background activity: Settings → Apps → {appName} → Battery → Background activity → Allow'**
  String manufacturerSettingsMiuiStep3(String appName);

  /// Title for EMUI manufacturer settings
  ///
  /// In en, this message translates to:
  /// **'EMUI Settings for Stable Operation'**
  String get manufacturerSettingsEmuiTitle;

  /// Message for EMUI manufacturer settings
  ///
  /// In en, this message translates to:
  /// **'On Huawei/Honor devices, you need to configure the following settings:'**
  String get manufacturerSettingsEmuiMessage;

  /// Step 1 for EMUI settings
  ///
  /// In en, this message translates to:
  /// **'1. App protection: Settings → Apps → App protection → {appName} → Enable autostart'**
  String manufacturerSettingsEmuiStep1(String appName);

  /// Step 2 for EMUI settings
  ///
  /// In en, this message translates to:
  /// **'2. Battery management: Settings → Battery → Battery management → {appName} → Don\'t optimize'**
  String manufacturerSettingsEmuiStep2(String appName);

  /// Step 3 for EMUI settings
  ///
  /// In en, this message translates to:
  /// **'3. Background activity: Settings → Apps → {appName} → Battery → Allow background activity'**
  String manufacturerSettingsEmuiStep3(String appName);

  /// Title for ColorOS/RealmeUI manufacturer settings
  ///
  /// In en, this message translates to:
  /// **'ColorOS/RealmeUI Settings for Stable Operation'**
  String get manufacturerSettingsColorosTitle;

  /// Message for ColorOS/RealmeUI manufacturer settings
  ///
  /// In en, this message translates to:
  /// **'On Oppo/Realme devices, you need to configure the following settings:'**
  String get manufacturerSettingsColorosMessage;

  /// Step 1 for ColorOS/RealmeUI settings
  ///
  /// In en, this message translates to:
  /// **'1. Autostart: Settings → Apps → Autostart → Enable for {appName}'**
  String manufacturerSettingsColorosStep1(String appName);

  /// Step 2 for ColorOS/RealmeUI settings
  ///
  /// In en, this message translates to:
  /// **'2. Battery optimization: Settings → Battery → Battery optimization → {appName} → Don\'t optimize'**
  String manufacturerSettingsColorosStep2(String appName);

  /// Step 3 for ColorOS/RealmeUI settings
  ///
  /// In en, this message translates to:
  /// **'3. Background activity: Settings → Apps → {appName} → Battery → Allow background activity'**
  String manufacturerSettingsColorosStep3(String appName);

  /// Title for OxygenOS manufacturer settings
  ///
  /// In en, this message translates to:
  /// **'OxygenOS Settings for Stable Operation'**
  String get manufacturerSettingsOxygenosTitle;

  /// Message for OxygenOS manufacturer settings
  ///
  /// In en, this message translates to:
  /// **'On OnePlus devices, you need to configure the following settings:'**
  String get manufacturerSettingsOxygenosMessage;

  /// Step 1 for OxygenOS settings
  ///
  /// In en, this message translates to:
  /// **'1. Autostart: Settings → Apps → Autostart → Enable for {appName}'**
  String manufacturerSettingsOxygenosStep1(String appName);

  /// Step 2 for OxygenOS settings
  ///
  /// In en, this message translates to:
  /// **'2. Battery optimization: Settings → Battery → Battery optimization → {appName} → Don\'t optimize'**
  String manufacturerSettingsOxygenosStep2(String appName);

  /// Step 3 for OxygenOS settings
  ///
  /// In en, this message translates to:
  /// **'3. Background activity: Settings → Apps → {appName} → Battery → Allow background activity'**
  String manufacturerSettingsOxygenosStep3(String appName);

  /// Title for FuntouchOS/OriginOS manufacturer settings
  ///
  /// In en, this message translates to:
  /// **'FuntouchOS/OriginOS Settings for Stable Operation'**
  String get manufacturerSettingsFuntouchosTitle;

  /// Message for FuntouchOS/OriginOS manufacturer settings
  ///
  /// In en, this message translates to:
  /// **'On Vivo devices, you need to configure the following settings:'**
  String get manufacturerSettingsFuntouchosMessage;

  /// Step 1 for FuntouchOS/OriginOS settings
  ///
  /// In en, this message translates to:
  /// **'1. Autostart: Settings → Apps → Autostart → Enable for {appName}'**
  String manufacturerSettingsFuntouchosStep1(String appName);

  /// Step 2 for FuntouchOS/OriginOS settings
  ///
  /// In en, this message translates to:
  /// **'2. Battery optimization: Settings → Battery → Battery optimization → {appName} → Don\'t optimize'**
  String manufacturerSettingsFuntouchosStep2(String appName);

  /// Step 3 for FuntouchOS/OriginOS settings
  ///
  /// In en, this message translates to:
  /// **'3. Background activity: Settings → Apps → {appName} → Battery → Allow background activity'**
  String manufacturerSettingsFuntouchosStep3(String appName);

  /// Title for Flyme manufacturer settings
  ///
  /// In en, this message translates to:
  /// **'Flyme Settings for Stable Operation'**
  String get manufacturerSettingsFlymeTitle;

  /// Message for Flyme manufacturer settings
  ///
  /// In en, this message translates to:
  /// **'On Meizu devices, you need to configure the following settings:'**
  String get manufacturerSettingsFlymeMessage;

  /// Step 1 for Flyme settings
  ///
  /// In en, this message translates to:
  /// **'1. Autostart: Settings → Apps → Autostart → Enable for {appName}'**
  String manufacturerSettingsFlymeStep1(String appName);

  /// Step 2 for Flyme settings
  ///
  /// In en, this message translates to:
  /// **'2. Battery optimization: Settings → Battery → Battery optimization → {appName} → Don\'t optimize'**
  String manufacturerSettingsFlymeStep2(String appName);

  /// Step 3 for Flyme settings
  ///
  /// In en, this message translates to:
  /// **'3. Background activity: Settings → Apps → {appName} → Battery → Allow background activity'**
  String manufacturerSettingsFlymeStep3(String appName);

  /// Title for One UI manufacturer settings
  ///
  /// In en, this message translates to:
  /// **'One UI Settings for Stable Operation'**
  String get manufacturerSettingsOneuiTitle;

  /// Message for One UI manufacturer settings
  ///
  /// In en, this message translates to:
  /// **'On Samsung devices, it is recommended to configure the following settings:'**
  String get manufacturerSettingsOneuiMessage;

  /// Step 1 for One UI settings
  ///
  /// In en, this message translates to:
  /// **'1. Battery optimization: Settings → Apps → {appName} → Battery → Don\'t optimize'**
  String manufacturerSettingsOneuiStep1(String appName);

  /// Step 2 for One UI settings
  ///
  /// In en, this message translates to:
  /// **'2. Background activity: Settings → Apps → {appName} → Battery → Background activity → Allow'**
  String manufacturerSettingsOneuiStep2(String appName);

  /// Step 3 for One UI settings
  ///
  /// In en, this message translates to:
  /// **'3. Autostart: Usually not required on Samsung, but you can check in app settings'**
  String get manufacturerSettingsOneuiStep3;

  /// Track position indicator
  ///
  /// In en, this message translates to:
  /// **'Track {currentTrack} of {totalTracks}'**
  String trackOfTotal(int currentTrack, int totalTracks);

  /// Tracks section title with plural
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =0{No tracks} =1{Track} other{Tracks}}'**
  String tracksTitle(int count);

  /// No repeat mode
  ///
  /// In en, this message translates to:
  /// **'No repeat'**
  String get noRepeat;

  /// Repeat current track mode
  ///
  /// In en, this message translates to:
  /// **'Repeat track'**
  String get repeatTrack;

  /// Repeat entire playlist mode
  ///
  /// In en, this message translates to:
  /// **'Repeat playlist'**
  String get repeatPlaylist;

  /// Sleep timer label
  ///
  /// In en, this message translates to:
  /// **'Timer'**
  String get timerLabel;

  /// Playback speed label
  ///
  /// In en, this message translates to:
  /// **'Speed'**
  String get speedLabel;

  /// Rewind button label
  ///
  /// In en, this message translates to:
  /// **'Rewind'**
  String get rewindButton;

  /// Forward button label
  ///
  /// In en, this message translates to:
  /// **'Forward'**
  String get forwardButton;

  /// Play button label
  ///
  /// In en, this message translates to:
  /// **'Play'**
  String get playButton;

  /// Pause button label
  ///
  /// In en, this message translates to:
  /// **'Pause'**
  String get pauseButton;

  /// Next track button label
  ///
  /// In en, this message translates to:
  /// **'Next'**
  String get nextButton;

  /// Previous track button label
  ///
  /// In en, this message translates to:
  /// **'Previous'**
  String get previousButton;
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
