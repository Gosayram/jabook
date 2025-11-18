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

  /// Text showing priority
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
  /// **'Language changed to \$languageName'**
  String languageChangedMessage(String languageName);

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
  /// **'Current mirror: \$host'**
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
