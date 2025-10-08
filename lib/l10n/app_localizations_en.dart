// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get appTitle => 'JaBook';

  @override
  String get searchAudiobooks => 'Search Audiobooks';

  @override
  String get searchPlaceholder => 'Enter title, author, or keywords';

  @override
  String get libraryTitle => 'Library';

  @override
  String get playerTitle => 'Player';

  @override
  String get settingsTitle => 'Settings';

  @override
  String get debugTitle => 'Debug';

  @override
  String get mirrorsTitle => 'Mirrors';

  @override
  String get topicTitle => 'Topic';

  @override
  String get noResults => 'No results found';

  @override
  String get loading => 'Loading...';

  @override
  String get error => 'Error';

  @override
  String get retry => 'Retry';

  @override
  String get language => 'Language';

  @override
  String get english => 'English';

  @override
  String get russian => 'Russian';

  @override
  String get systemDefault => 'System Default';

  @override
  String get failedToSearch => 'Failed to search';

  @override
  String get resultsFromCache => 'Results from cache';

  @override
  String get enterSearchTerm => 'Enter a search term to begin';

  @override
  String get authorLabel => 'Author: ';

  @override
  String get sizeLabel => 'Size: ';

  @override
  String get seedersLabel => ' seeders';

  @override
  String get leechersLabel => ' leechers';

  @override
  String get unknownTitle => 'Unknown Title';

  @override
  String get unknownAuthor => 'Unknown Author';

  @override
  String get unknownSize => 'Unknown Size';

  @override
  String get addBookComingSoon => 'Add book functionality coming soon!';

  @override
  String get libraryContentPlaceholder => 'Library content will be displayed here';

  @override
  String get authenticationRequired => 'Authentication Required';

  @override
  String get loginRequiredForSearch => 'Please login to RuTracker to access search functionality.';

  @override
  String get cancel => 'Cancel';

  @override
  String get login => 'Login';

  @override
  String get networkConnectionError => 'Could not connect. Check your internet or choose another mirror in Settings → Sources.';

  @override
  String get connectionFailed => 'Connection failed. Please check your internet connection or try a different mirror.';

  @override
  String get chooseMirror => 'Choose Mirror';

  @override
  String get networkErrorUser => 'Network connection issue';

  @override
  String get dnsError => 'Could not resolve domain. This may be due to network restrictions or an inactive mirror.';

  @override
  String get timeoutError => 'Request took too long. Please check your connection and try again.';

  @override
  String get serverError => 'Server is temporarily unavailable. Please try again later or choose another mirror.';

  @override
  String get recentSearches => 'Recent Searches';

  @override
  String get searchExamples => 'Try these examples';

  @override
  String get quickActions => 'Quick Actions';

  @override
  String get scanLocalFiles => 'Scan Local Files';

  @override
  String get featureComingSoon => 'Feature coming soon';

  @override
  String get changeMirror => 'Change Mirror';

  @override
  String get checkConnection => 'Check Connection';

  @override
  String get permissionsRequired => 'Permissions Required';

  @override
  String get permissionExplanation => 'This app needs storage permission to download and save audiobook files. Please grant the required permissions to continue.';

  @override
  String get grantPermissions => 'Grant Permissions';

  @override
  String get permissionDeniedTitle => 'Permission Denied';

  @override
  String get permissionDeniedMessage => 'Storage permission is required to download files. Please enable it in app settings.';

  @override
  String get permissionDeniedButton => 'Open Settings';

  @override
  String get debugToolsTitle => 'Debug Tools';

  @override
  String get logsTab => 'Logs';

  @override
  String get mirrorsTab => 'Mirrors';

  @override
  String get downloadsTab => 'Downloads';

  @override
  String get cacheTab => 'Cache';

  @override
  String get testAllMirrorsButton => 'Test all mirrors';

  @override
  String get statusLabelText => 'Status:';

  @override
  String get lastOkLabelText => 'Last OK:';

  @override
  String get rttLabelText => 'RTT:';

  @override
  String get millisecondsText => 'ms';

  @override
  String get downloadLabelText => 'Download';

  @override
  String get statusLabelNoColonText => 'Status';

  @override
  String get downloadProgressLabelText => 'Progress';

  @override
  String get cacheStatisticsTitle => 'Cache Statistics';

  @override
  String get totalEntriesText => 'Total entries';

  @override
  String get searchCacheText => 'Search cache';

  @override
  String get topicCacheText => 'Topic cache';

  @override
  String get memoryUsageText => 'Memory usage';

  @override
  String get clearAllCacheButton => 'Clear All Cache';

  @override
  String get cacheIsEmptyMessage => 'Cache is empty';

  @override
  String get refreshDebugDataButton => 'Refresh debug data';

  @override
  String get exportLogsButton => 'Export logs';

  @override
  String get deleteDownloadButton => 'Delete download';

  @override
  String get webViewTitle => 'RuTracker';

  @override
  String get cloudflareMessage => 'This site uses Cloudflare security checks. Please wait for the verification to complete and interact with the opened page if necessary.';

  @override
  String get retryButtonText => 'Retry';

  @override
  String get goHomeButtonText => 'Go to Home';

  @override
  String get browserCheckMessage => 'The site is checking your browser - please wait for the verification to complete on this page.';

  @override
  String get downloadTorrentTitle => 'Download Torrent';

  @override
  String get selectActionText => 'Select action:';

  @override
  String get openButtonText => 'Open';

  @override
  String get downloadButtonText => 'Download';

  @override
  String get downloadInBrowserMessage => 'To download the file, please open the link in your browser';

  @override
  String get importAudiobooksTitle => 'Import Audiobooks';

  @override
  String get selectFilesMessage => 'Select audiobook files from your device to add to your library';

  @override
  String get importButtonText => 'Import';

  @override
  String get importedSuccess => 'Imported \$count audiobook(s)';

  @override
  String get noFilesSelectedMessage => 'No files selected';

  @override
  String get importFailedMessage => 'Failed to import: \$error';

  @override
  String get scanFolderTitle => 'Scan Folder';

  @override
  String get scanFolderMessage => 'Scan a folder on your device for audiobook files';

  @override
  String get scanButtonText => 'Scan';

  @override
  String get scanSuccessMessage => 'Found and imported \$count audiobook(s)';

  @override
  String get noAudiobooksFoundMessage => 'No audiobook files found in selected folder';

  @override
  String get noFolderSelectedMessage => 'No folder selected';

  @override
  String get scanFailedMessage => 'Failed to scan folder: \$error';

  @override
  String get authScreenTitle => 'RuTracker Connection';

  @override
  String get usernameLabelText => 'Username';

  @override
  String get passwordLabelText => 'Password';

  @override
  String get rememberMeLabelText => 'Remember me';

  @override
  String get loginButtonText => 'Login';

  @override
  String get testConnectionButtonText => 'Test Connection';

  @override
  String get logoutButtonText => 'Logout';

  @override
  String get authHelpText => 'Enter your credentials';

  @override
  String get enterCredentialsText => 'Please enter username and password';

  @override
  String get loggingInText => 'Logging in...';

  @override
  String get loginSuccessMessage => 'Login successful!';

  @override
  String get loginFailedMessage => 'Login failed. Please check credentials';

  @override
  String get loginErrorMessage => 'Login error: \$error';

  @override
  String get testingConnectionText => 'Testing connection...';

  @override
  String get connectionSuccessMessage => 'Connection successful! Using: \$endpoint';

  @override
  String get connectionFailedMessage => 'Connection test failed: \$error';

  @override
  String get loggingOutText => 'Logging out...';

  @override
  String get logoutSuccessMessage => 'Logged out successfully';

  @override
  String get logoutErrorMessage => 'Logout error: \$error';

  @override
  String get configureMirrorsSubtitle => 'Configure and test RuTracker mirrors';

  @override
  String get playbackSpeedTitle => 'Playback Speed';

  @override
  String get skipDurationTitle => 'Skip Duration';

  @override
  String get downloadLocationTitle => 'Download Location';

  @override
  String get darkModeTitle => 'Dark Mode';

  @override
  String get highContrastTitle => 'High Contrast';

  @override
  String get wifiOnlyDownloadsTitle => 'Wi-Fi Only Downloads';

  @override
  String get failedToLoadMirrorsMessage => 'Failed to load mirrors: \$error';

  @override
  String get mirrorTestSuccessMessage => 'Mirror \$url tested successfully';

  @override
  String get mirrorTestFailedMessage => 'Failed to test mirror \$url: \$error';

  @override
  String get mirrorStatusText => 'Mirror \$status';

  @override
  String get failedToUpdateMirrorMessage => 'Failed to update mirror: \$error';

  @override
  String get addCustomMirrorTitle => 'Add Custom Mirror';

  @override
  String get mirrorUrlLabelText => 'Mirror URL';

  @override
  String get mirrorUrlHintText => 'https://rutracker.example.com';

  @override
  String get priorityLabelText => 'Priority (1-10)';

  @override
  String get priorityHintText => '5';

  @override
  String get addMirrorButtonText => 'Add';

  @override
  String get mirrorAddedMessage => 'Mirror \$url added';

  @override
  String get failedToAddMirrorMessage => 'Failed to add mirror: \$error';

  @override
  String get mirrorSettingsTitle => 'Mirror Settings';

  @override
  String get mirrorSettingsDescription => 'Configure RuTracker mirrors for optimal search performance. Enabled mirrors will be used automatically.';

  @override
  String get addCustomMirrorButtonText => 'Add Custom Mirror';

  @override
  String get priorityText => 'Priority: \$priority';

  @override
  String get responseTimeText => 'Response time: \$rtt ms';

  @override
  String get lastCheckedText => 'Last checked: \$date';

  @override
  String get testMirrorButtonText => 'Test this mirror';

  @override
  String get activeStatusText => 'Active';

  @override
  String get disabledStatusText => 'Disabled';

  @override
  String get neverDateText => 'Never';

  @override
  String get invalidDateText => 'Invalid date';

  @override
  String get playerScreenTitle => 'Player';

  @override
  String get failedToLoadAudioMessage => 'Failed to load audio';

  @override
  String get byAuthorText => 'by \$author';

  @override
  String get chaptersLabelText => 'Chapters';

  @override
  String get downloadFunctionalityComingSoonMessage => 'Download functionality coming soon';

  @override
  String get sampleTitleText => 'Sample Audiobook';

  @override
  String get sampleAuthorText => 'Sample Author';

  @override
  String get sampleCategoryText => 'Fiction';

  @override
  String get sampleSizeText => '150 MB';

  @override
  String get sampleChapter1Text => 'Chapter 1';

  @override
  String get sampleChapter2Text => 'Chapter 2';

  @override
  String get topicScreenTitle => 'Topic';

  @override
  String get requestTimedOutMessage => 'Request timed out';

  @override
  String get networkErrorMessage => 'Network error';

  @override
  String get errorLoadingTopicMessage => 'Error loading topic: \$error';

  @override
  String get failedToLoadTopicMessage => 'Failed to load topic';

  @override
  String get dataLoadedFromCacheMessage => 'Data loaded from cache';

  @override
  String get unknownChapterText => 'Unknown chapter';

  @override
  String get magnetLinkLabelText => 'Magnet link';

  @override
  String get magnetLinkCopiedMessage => 'Magnet link copied';

  @override
  String get copyToClipboardMessage => '\$label copied to clipboard';

  @override
  String get navLibraryText => 'Library';

  @override
  String get navAuthText => 'Connect';

  @override
  String get navSearchText => 'Search';

  @override
  String get navSettingsText => 'Settings';

  @override
  String get navDebugText => 'Debug';

  @override
  String get authDialogTitle => 'Login';

  @override
  String get cacheClearedSuccessfullyMessage => 'Cache cleared successfully';

  @override
  String get downloadStatusUnknownMessage => 'Download status unknown';

  @override
  String get failedToExportLogsMessage => 'Failed to export logs';

  @override
  String get logsExportedSuccessfullyMessage => 'Logs exported successfully';

  @override
  String get mirrorHealthCheckCompletedMessage => 'Mirror health check completed';

  @override
  String get mirrorsScreenTitle => 'Mirrors';

  @override
  String get mirrorStatusDisabledText => 'Disabled';

  @override
  String get mirrorResponseTimeText => 'Response time';

  @override
  String get mirrorLastCheckText => 'Last check';

  @override
  String get mirrorTestIndividualText => 'Test individual mirror';

  @override
  String get languageDescriptionText => 'Select language';

  @override
  String get themeTitleText => 'Theme';

  @override
  String get themeDescriptionText => 'Select app theme';

  @override
  String get darkModeText => 'Dark mode';

  @override
  String get highContrastText => 'High contrast';

  @override
  String get audioTitleText => 'Audio';

  @override
  String get audioDescriptionText => 'Audio settings';

  @override
  String get playbackSpeedText => 'Playback speed';

  @override
  String get skipDurationText => 'Skip duration';

  @override
  String get downloadsTitleText => 'Downloads';

  @override
  String get downloadsDescriptionText => 'Download settings';

  @override
  String get downloadLocationText => 'Download location';

  @override
  String get wifiOnlyDownloadsText => 'Wi-Fi only downloads';

  @override
  String get copyToClipboardLabelText => 'Copy to clipboard';
}
