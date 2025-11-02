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
  String get importedSuccess;

  /// Message for no files selected
  ///
  /// In en, this message translates to:
  /// **'No files selected'**
  String get noFilesSelectedMessage;

  /// Message for import failure
  ///
  /// In en, this message translates to:
  /// **'Failed to import: \$error'**
  String get importFailedMessage;

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
  String get scanSuccessMessage;

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
  String get scanFailedMessage;

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
  String get logoutErrorMessage;

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
  String get priorityText;

  /// Text for showing mirror response time
  ///
  /// In en, this message translates to:
  /// **'Response time: \$rtt ms'**
  String get responseTimeText;

  /// Text for showing mirror last check time
  ///
  /// In en, this message translates to:
  /// **'Last checked: \$date'**
  String get lastCheckedText;

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
  String get networkErrorMessage;

  /// Message for topic loading error
  ///
  /// In en, this message translates to:
  /// **'Error loading topic: \$error'**
  String get errorLoadingTopicMessage;

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
  String get copyToClipboardMessage;

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
  /// **'Enter your credentials'**
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
  /// **'Mirror health check completed'**
  String get mirrorHealthCheckCompletedMessage;
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
