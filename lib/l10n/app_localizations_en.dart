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
  String get noResultsForFilters => 'No results match the selected filters';

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
  String get resultsFromLocalDb => 'Results from local database';

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
  String get reset => 'Reset';

  @override
  String get resetToGlobalSettings => 'Reset to global settings';

  @override
  String get resetAllBookSettings => 'Reset all book settings';

  @override
  String get resetAllBookSettingsDescription => 'Remove individual settings for all books';

  @override
  String get resetAllBookSettingsConfirmation => 'This will remove individual audio settings for all books. All books will use global settings. This action cannot be undone.';

  @override
  String get settingsResetToGlobal => 'Settings reset to global defaults';

  @override
  String get allBookSettingsReset => 'All book settings have been reset to global defaults';

  @override
  String get errorResettingSettings => 'Error resetting settings';

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
  String get navLibrary => 'Library';

  @override
  String get navAuth => 'Connect';

  @override
  String get navSearch => 'Search';

  @override
  String get navSettings => 'Settings';

  @override
  String get navDebug => 'Debug';

  @override
  String get authTitle => 'Login';

  @override
  String get usernameLabel => 'Username';

  @override
  String get passwordLabel => 'Password';

  @override
  String get rememberMeLabel => 'Remember me';

  @override
  String get loginButton => 'Login';

  @override
  String get testConnectionButton => 'Test Connection';

  @override
  String get logoutButton => 'Logout';

  @override
  String get cacheClearedSuccessfully => 'Cache cleared successfully';

  @override
  String get downloadStatusUnknown => 'Download status unknown';

  @override
  String get failedToExportLogs => 'Failed to export logs';

  @override
  String get logsExportedSuccessfully => 'Logs exported successfully';

  @override
  String get mirrorHealthCheckCompleted => 'Mirror health check completed';

  @override
  String get testAllMirrors => 'Test All Mirrors';

  @override
  String get statusLabel => 'Status:';

  @override
  String get lastOkLabel => 'Last OK:';

  @override
  String get rttLabel => 'RTT:';

  @override
  String get milliseconds => 'ms';

  @override
  String get downloadLabel => 'Download';

  @override
  String get statusLabelNoColon => 'Status';

  @override
  String get downloadProgressLabel => 'Progress';

  @override
  String get cacheStatistics => 'Cache Statistics';

  @override
  String get totalEntries => 'Total entries';

  @override
  String get searchCache => 'Search cache';

  @override
  String get topicCache => 'Topic cache';

  @override
  String get memoryUsage => 'Memory usage';

  @override
  String get clearAllCache => 'Clear All Cache';

  @override
  String get mirrorsScreenTitle => 'Mirrors';

  @override
  String get failedToLoadAudio => 'Failed to load audio';

  @override
  String get chaptersLabel => 'Chapters';

  @override
  String get downloadFunctionalityComingSoon => 'Download functionality coming soon';

  @override
  String get requestTimedOut => 'Request timed out';

  @override
  String get networkError => 'Network error';

  @override
  String get mirrorStatusDisabled => 'Disabled';

  @override
  String get mirrorResponseTime => 'Response time';

  @override
  String get mirrorLastCheck => 'Last check';

  @override
  String get mirrorTestIndividual => 'Test individual mirror';

  @override
  String get activeStatus => 'Active';

  @override
  String get disabledStatus => 'Disabled';

  @override
  String get languageDescription => 'Select language';

  @override
  String get themeTitle => 'Theme';

  @override
  String get themeDescription => 'Select app theme';

  @override
  String get darkMode => 'Dark mode';

  @override
  String get highContrast => 'High contrast';

  @override
  String get audioTitle => 'Audio';

  @override
  String get audioDescription => 'Audio settings';

  @override
  String get playbackSpeed => 'Playback speed';

  @override
  String get skipDuration => 'Skip duration';

  @override
  String get downloadsTitle => 'Downloads';

  @override
  String get downloadsDescription => 'Download settings';

  @override
  String get downloadLocation => 'Download location';

  @override
  String get wifiOnlyDownloads => 'WiFi only downloads';

  @override
  String get dataLoadedFromCache => 'Data loaded from cache';

  @override
  String get failedToLoadTopic => 'Failed to load topic';

  @override
  String get magnetLinkLabel => 'Magnet link';

  @override
  String get unknownChapter => 'Unknown chapter';

  @override
  String get magnetLinkCopied => 'Magnet link copied';

  @override
  String get copyToClipboardLabel => 'Copy to clipboard';

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
  String get testAllMirrorsButton => 'Test All Mirrors';

  @override
  String get statusLabelText => 'Status: ';

  @override
  String get lastOkLabelText => 'Last OK: ';

  @override
  String get rttLabelText => 'RTT: ';

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
  String get totalEntriesText => 'Total entries: ';

  @override
  String get searchCacheText => 'Search cache: ';

  @override
  String get topicCacheText => 'Topic cache: ';

  @override
  String get memoryUsageText => 'Memory usage: ';

  @override
  String get clearAllCacheButton => 'Clear All Cache';

  @override
  String get cacheIsEmptyMessage => 'Cache is empty';

  @override
  String get refreshDebugDataButton => 'Refresh Debug Data';

  @override
  String get exportLogsButton => 'Export Logs';

  @override
  String get deleteDownloadButton => 'Delete Download';

  @override
  String get doneButtonText => 'Done';

  @override
  String get webViewTitle => 'RuTracker';

  @override
  String get webViewLoginInstruction => 'Please log in to RuTracker. After successful login, tap Done to extract cookies for the client.';

  @override
  String get cloudflareMessage => 'This site uses Cloudflare security checks. Please wait for the check to complete and interact with the page that opens if needed.';

  @override
  String get retryButtonText => 'Retry';

  @override
  String get goHomeButtonText => 'Go to Home';

  @override
  String get browserCheckMessage => 'The site is checking your browser - please wait for the check to complete on this page.';

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
  String importedSuccess(int count) {
    return 'Imported \$count audiobook(s)';
  }

  @override
  String get noFilesSelectedMessage => 'No files selected';

  @override
  String importFailedMessage(String error) {
    return 'Failed to import: \$error';
  }

  @override
  String get scanFolderTitle => 'Scan Folder';

  @override
  String get scanFolderMessage => 'Scan a folder on your device for audiobook files';

  @override
  String get scanButtonText => 'Scan';

  @override
  String scanSuccessMessage(int count) {
    return 'Found and imported \$count audiobook(s)';
  }

  @override
  String get noAudiobooksFoundMessage => 'No audiobook files found in selected folder';

  @override
  String get noFolderSelectedMessage => 'No folder selected';

  @override
  String scanFailedMessage(String error) {
    return 'Failed to scan folder: \$error';
  }

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
  String get loggingInText => 'Logging in...';

  @override
  String get loginSuccessMessage => 'Login successful!';

  @override
  String get loginFailedMessage => 'Login failed. Please check credentials';

  @override
  String loginErrorMessage(String error) {
    return 'Login error: \$error';
  }

  @override
  String connectionSuccessMessage(String endpoint) {
    return 'Connection successful! Using: \$endpoint';
  }

  @override
  String connectionFailedMessage(String error) {
    return 'Connection test failed: \$error';
  }

  @override
  String get loggingOutText => 'Logging out...';

  @override
  String get logoutSuccessMessage => 'Logout completed';

  @override
  String logoutErrorMessage(String error) {
    return 'Logout error: \$error';
  }

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
  String get wifiOnlyDownloadsTitle => 'WiFi Only Downloads';

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
  String priorityText(int priority) {
    return 'Priority: \$priority';
  }

  @override
  String get responseTimeText => 'Response time: \$rtt ms';

  @override
  String lastCheckedText(String date) {
    return 'Last checked: \$date';
  }

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
  String get failedToLoadAudioMessage => 'Failed to load audiobook';

  @override
  String get byAuthorText => 'by: \$author';

  @override
  String get chaptersLabelText => 'Chapters';

  @override
  String get downloadFunctionalityComingSoonMessage => 'Download functionality coming soon!';

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
  String get requestTimedOutMessage => 'Request timed out. Check your connection.';

  @override
  String networkErrorMessage(String error) {
    return 'Network error: \$error';
  }

  @override
  String errorLoadingTopicMessage(String error) {
    return 'Error loading topic: \$error';
  }

  @override
  String get failedToLoadTopicMessage => 'Failed to load topic';

  @override
  String get dataLoadedFromCacheMessage => 'Data loaded from cache';

  @override
  String get unknownChapterText => 'Unknown chapter';

  @override
  String get magnetLinkLabelText => 'Magnet link';

  @override
  String get magnetLinkCopiedMessage => 'Magnet link copied to clipboard';

  @override
  String copyToClipboardMessage(String label) {
    return '\$label copied to clipboard';
  }

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
  String get authHelpText => 'Login to RuTracker to access audiobook search and downloads. Your credentials are stored securely.';

  @override
  String get cacheClearedSuccessfullyMessage => 'Cache cleared successfully';

  @override
  String get downloadStatusUnknownMessage => 'Download status unknown';

  @override
  String get failedToExportLogsMessage => 'Failed to export logs';

  @override
  String get logsExportedSuccessfullyMessage => 'Logs exported successfully';

  @override
  String mirrorHealthCheckCompletedMessage(int tested, int total) {
    return 'Mirror health check completed: \$tested/\$total mirrors';
  }

  @override
  String get noActiveMirrorsMessage => 'No active mirrors to test';

  @override
  String activeMirrorSetMessage(String url) {
    return 'Active mirror set: \$url';
  }

  @override
  String failedToSetActiveMirrorMessage(String error) {
    return 'Failed to set active mirror: \$error';
  }

  @override
  String activeMirrorText(String url) {
    return 'Active mirror: \$url';
  }

  @override
  String failedToSetBestMirrorMessage(String error) {
    return 'Failed to set best mirror: \$error';
  }

  @override
  String activeMirrorDisabledMessage(String url) {
    return 'Active mirror disabled. Switched to: \$url';
  }

  @override
  String warningFailedToSelectNewActiveMirrorMessage(String error) {
    return 'Warning: failed to select new active mirror: \$error';
  }

  @override
  String get urlCopiedToClipboardMessage => 'URL copied to clipboard';

  @override
  String get editPriorityTitle => 'Edit Priority';

  @override
  String get priorityHelperText => 'Lower number = higher priority';

  @override
  String get saveButtonText => 'Save';

  @override
  String priorityUpdatedMessage(int priority) {
    return 'Priority updated: \$priority';
  }

  @override
  String failedToUpdatePriorityMessage(String error) {
    return 'Failed to update priority: \$error';
  }

  @override
  String get deleteMirrorTitle => 'Delete Mirror?';

  @override
  String get defaultMirrorWarningMessage => 'Warning: this is a default mirror. It will be removed from the list, but can be added again.';

  @override
  String get confirmDeleteMirrorMessage => 'Are you sure you want to delete this mirror?';

  @override
  String get deleteButtonText => 'Delete';

  @override
  String mirrorDeletedMessage(String url) {
    return 'Mirror deleted: \$url';
  }

  @override
  String failedToDeleteMirrorMessage(String error) {
    return 'Failed to delete mirror: \$error';
  }

  @override
  String get urlMustStartWithHttpMessage => 'URL must start with http:// or https://';

  @override
  String get invalidUrlFormatMessage => 'Invalid URL format';

  @override
  String get mirrorAlreadyExistsMessage => 'This mirror already exists in the list';

  @override
  String get onlyActiveFilterText => 'Only Active';

  @override
  String get onlyHealthyFilterText => 'Only Healthy';

  @override
  String get sortByPriorityText => 'By Priority';

  @override
  String get sortByHealthText => 'By Health';

  @override
  String get sortBySpeedText => 'By Speed';

  @override
  String get noMirrorsMatchFiltersMessage => 'No mirrors match the filters';

  @override
  String get setBestMirrorButtonText => 'Set Best Mirror';

  @override
  String healthScoreText(int score) {
    return 'Health: \$score%';
  }

  @override
  String get setAsActiveTooltip => 'Set as active';

  @override
  String get copyUrlTooltip => 'Copy URL';

  @override
  String get deleteMirrorTooltip => 'Delete mirror';

  @override
  String get activeLabelText => 'Active';

  @override
  String failedToClearCacheMessage(String error) {
    return 'Failed to clear cache: \$error';
  }

  @override
  String failedToTestMirrorsMessage(String error) {
    return 'Failed to test mirrors: \$error';
  }

  @override
  String get mirrorStatusDegraded => 'Degraded';

  @override
  String get mirrorStatusUnhealthy => 'Unhealthy';

  @override
  String get pleaseEnterCredentials => 'Please enter username and password';

  @override
  String get testingConnectionText => 'Testing connection...';

  @override
  String get favoritesTitle => 'Favorites';

  @override
  String get refreshTooltip => 'Refresh';

  @override
  String get noFavoritesMessage => 'No favorite audiobooks';

  @override
  String get addFavoritesHint => 'Add audiobooks to favorites from search results';

  @override
  String get goToSearchButton => 'Go to Search';

  @override
  String get addedToFavorites => 'Added to favorites';

  @override
  String get removedFromFavorites => 'Removed from favorites';

  @override
  String get failedToRemoveFromFavorites => 'Failed to remove from favorites';

  @override
  String get favoritesTooltip => 'Favorites';

  @override
  String get filterLibraryTooltip => 'Filter library';

  @override
  String get addAudiobookTooltip => 'Add audiobook';

  @override
  String get libraryEmptyMessage => 'Your library is empty';

  @override
  String get addAudiobooksHint => 'Add audiobooks to your library to start listening';

  @override
  String get searchForAudiobooksButton => 'Search for audiobooks';

  @override
  String get importFromFilesButton => 'Import from Files';

  @override
  String get biometricAuthInProgress => 'Please wait, biometric authentication in progress...';

  @override
  String get authorizationSuccessful => 'Authorization successful';

  @override
  String get authorizationTitle => 'Authorization';

  @override
  String get biometricUnavailableMessage => 'Biometric authentication is unavailable or failed. Open WebView to login?';

  @override
  String get openWebViewButton => 'Open WebView';

  @override
  String authorizationCheckError(String error) {
    return 'Error checking authorization: \$error';
  }

  @override
  String get authorizationFailedMessage => 'Authorization failed. Please check your login and password';

  @override
  String authorizationPageError(String error) {
    return 'Error opening authorization page: \$error';
  }

  @override
  String get filtersLabel => 'Filters:';

  @override
  String get resetButton => 'Reset';

  @override
  String get otherCategory => 'Other';

  @override
  String get searchHistoryTitle => 'Search History';

  @override
  String get clearButton => 'Clear';

  @override
  String get failedToAddToFavorites => 'Failed to add to favorites';

  @override
  String get loadMoreButton => 'Load more';

  @override
  String get allowButton => 'Allow';

  @override
  String get copyMagnetLink => 'Copy Magnet Link';

  @override
  String get downloadTorrentMenu => 'Download Torrent';

  @override
  String get openTorrentInExternalApp => 'Open torrent file in external app';

  @override
  String failedToOpenTorrent(String error) {
    return 'Failed to open torrent: \$error';
  }

  @override
  String get urlUnavailable => 'URL unavailable';

  @override
  String invalidUrlFormat(String url) {
    return 'Invalid URL format: \$url';
  }

  @override
  String genericError(String error) {
    return 'Error: \$error';
  }

  @override
  String retryConnectionMessage(int current, int max) {
    return 'Retrying connection (\$current/\$max)...';
  }

  @override
  String loadError(String desc) {
    return 'Load error: \$desc';
  }

  @override
  String get pageLoadError => 'An error occurred while loading the page';

  @override
  String get securityVerificationInProgress => 'Security verification in progress - please wait...';

  @override
  String get openInBrowserButton => 'Open in Browser';

  @override
  String get fileWillOpenInBrowser => 'The file will be opened in browser for download';

  @override
  String get resetFiltersButton => 'Reset Filters';

  @override
  String languageChangedMessage(String languageName) {
    return 'Language changed to $languageName';
  }

  @override
  String get followSystemTheme => 'Follow System Theme';

  @override
  String get rutrackerSessionDescription => 'RuTracker session management (cookie)';

  @override
  String get loginViaWebViewButton => 'Login to RuTracker via WebView';

  @override
  String get loginViaWebViewSubtitle => 'Pass Cloudflare/captcha and save cookie for client';

  @override
  String get cookiesSavedForHttpClient => 'Cookies saved for HTTP client';

  @override
  String get clearSessionButton => 'Clear RuTracker session (cookie)';

  @override
  String get clearSessionSubtitle => 'Delete saved cookies and logout from account';

  @override
  String get sessionClearedMessage => 'RuTracker session cleared';

  @override
  String get metadataSectionTitle => 'Audiobook Metadata';

  @override
  String get metadataSectionDescription => 'Manage local audiobook metadata database';

  @override
  String get totalRecordsLabel => 'Total records';

  @override
  String get lastUpdateLabel => 'Last update';

  @override
  String get updatingText => 'Updating...';

  @override
  String get updateMetadataButton => 'Update Metadata';

  @override
  String get metadataUpdateStartedMessage => 'Metadata update started...';

  @override
  String metadataUpdateCompletedMessage(int total) {
    return 'Update completed: collected \$total records';
  }

  @override
  String metadataUpdateError(String error) {
    return 'Update error: \$error';
  }

  @override
  String get neverDate => 'Never';

  @override
  String daysAgo(int days) {
    return '\$days days ago';
  }

  @override
  String hoursAgo(int hours) {
    return '\$hours hours ago';
  }

  @override
  String minutesAgo(int minutes) {
    return '\$minutes minutes ago';
  }

  @override
  String get justNow => 'Just now';

  @override
  String get unknownDate => 'Unknown';

  @override
  String get playbackSpeedDefault => '1.0x';

  @override
  String get skipDurationDefault => '15 seconds';

  @override
  String get clearExpiredCacheButton => 'Clear Expired Cache';

  @override
  String get appPermissionsTitle => 'App Permissions';

  @override
  String get storagePermissionName => 'Storage';

  @override
  String get storagePermissionDescription => 'Save audiobook files and cache data';

  @override
  String get notificationsPermissionName => 'Notifications';

  @override
  String get notificationsPermissionDescription => 'Show playback controls and updates';

  @override
  String get grantAllPermissionsButton => 'Grant All Permissions';

  @override
  String get allPermissionsGranted => 'All permissions granted';

  @override
  String get fileAccessAvailable => 'File access available';

  @override
  String get fileAccessUnavailable => 'File access unavailable';

  @override
  String get storagePermissionGuidanceTitle => 'File Access Permission';

  @override
  String get storagePermissionGuidanceMessage => 'To access audio files, you need to grant file access permission.';

  @override
  String get storagePermissionGuidanceStep1 => '1. Open JaBook app settings';

  @override
  String get storagePermissionGuidanceStep2 => '2. Go to Permissions section';

  @override
  String get storagePermissionGuidanceStep3 => '3. Enable \"Files and media\" or \"Storage\" permission';

  @override
  String get storagePermissionGuidanceMiuiTitle => 'File Access Permission (MIUI)';

  @override
  String get storagePermissionGuidanceMiuiMessage => 'On Xiaomi/Redmi/Poco (MIUI) devices, you need to grant file access permission:';

  @override
  String get storagePermissionGuidanceMiuiStep1 => '1. Open Settings → Apps → JaBook → Permissions';

  @override
  String get storagePermissionGuidanceMiuiStep2 => '2. Enable \"Files and media\" or \"Storage\" permission';

  @override
  String get storagePermissionGuidanceMiuiStep3 => '3. If access is limited, enable \"Manage all files\" in MIUI Security settings';

  @override
  String get storagePermissionGuidanceMiuiNote => 'Note: On some MIUI versions, you may need to additionally enable \"Manage all files\" in Settings → Security → Permission management.';

  @override
  String get storagePermissionGuidanceColorosTitle => 'File Access Permission (ColorOS/RealmeUI)';

  @override
  String get storagePermissionGuidanceColorosMessage => 'On Oppo/Realme (ColorOS/RealmeUI) devices, you need to grant file access permission:';

  @override
  String get storagePermissionGuidanceColorosStep1 => '1. Open Settings → Apps → JaBook → Permissions';

  @override
  String get storagePermissionGuidanceColorosStep2 => '2. Enable \"Files and media\" permission';

  @override
  String get storagePermissionGuidanceColorosStep3 => '3. If access is limited, check \"Files and media\" settings in permissions section';

  @override
  String get storagePermissionGuidanceColorosNote => 'Note: On some ColorOS versions, you may need to additionally allow file access in security settings.';

  @override
  String get openSettings => 'Open Settings';

  @override
  String get safFallbackTitle => 'Alternative File Access Method';

  @override
  String get safFallbackMessage => 'File access permissions are not working properly on your device. You can use the Storage Access Framework (SAF) to select folders manually. This method works without requiring special permissions.';

  @override
  String get safPermissionCheckboxMessage => 'Please check the \'Allow access to this folder\' checkbox in the file picker dialog and try again. Without this checkbox, the app cannot access the selected folder.';

  @override
  String get safNoAccessMessage => 'No access to selected folder. Please check the \'Allow access to this folder\' checkbox in the file picker and try again.';

  @override
  String get safFolderPickerHintTitle => 'Important: Check the checkbox';

  @override
  String get safFolderPickerHintMessage => 'When selecting a folder, please make sure to check the \'Allow access to this folder\' checkbox in the file picker dialog. Without this checkbox, the app cannot access the selected folder.';

  @override
  String get safAndroidDataObbWarning => 'Note: Access to Android/data and Android/obb folders is blocked on Android 11+ devices with security updates from March 2024. Please select a different folder.';

  @override
  String get safFallbackBenefits => 'Benefits of using SAF:\n• Works on all Android devices\n• No special permissions needed\n• You choose which folders to access';

  @override
  String get useSafMethod => 'Use Folder Selection';

  @override
  String get tryPermissionsAgain => 'Try Permissions Again';

  @override
  String get notificationsAvailable => 'Notifications available';

  @override
  String get notificationsUnavailable => 'Notifications unavailable';

  @override
  String capabilitiesStatus(int grantedCount, int total) {
    return 'Capabilities: \$grantedCount/\$total';
  }

  @override
  String get backupRestoreTitle => 'Backup & Restore';

  @override
  String get backupRestoreDescription => 'Export and import your data (favorites, history, metadata)';

  @override
  String get exportDataButton => 'Export Data';

  @override
  String get exportDataSubtitle => 'Save all your data to a backup file';

  @override
  String get importDataButton => 'Import Data';

  @override
  String get importDataSubtitle => 'Restore data from a backup file';

  @override
  String get exportingDataMessage => 'Exporting data...';

  @override
  String get dataExportedSuccessfullyMessage => 'Data exported successfully';

  @override
  String failedToExportMessage(String error) {
    return 'Failed to export: \$error';
  }

  @override
  String get importBackupTitle => 'Import Backup';

  @override
  String get importBackupConfirmationMessage => 'This will import data from the backup file. Existing data may be merged or replaced. Continue?';

  @override
  String get importButton => 'Import';

  @override
  String get importingDataMessage => 'Importing data...';

  @override
  String failedToImportMessage(String error) {
    return 'Failed to import: \$error';
  }

  @override
  String get rutrackerLoginTooltip => 'RuTracker Login';

  @override
  String get mirrorsTooltip => 'Mirrors';

  @override
  String currentMirrorLabel(String host) {
    return 'Current mirror: $host';
  }

  @override
  String get allMirrorsFailedMessage => 'All mirrors failed';

  @override
  String get unknownError => 'Unknown error';

  @override
  String get mirrorConnectionError => 'Could not connect to RuTracker mirrors. Check your internet connection or try selecting another mirror in settings';

  @override
  String get mirrorConnectionFailed => 'Could not connect to RuTracker mirrors';

  @override
  String get refresh => 'Refresh';

  @override
  String get refreshCurrentSearch => 'Refresh current search';

  @override
  String get clearSearchCache => 'Clear search cache';

  @override
  String get cacheCleared => 'Cache cleared';

  @override
  String get allCacheCleared => 'All cache cleared';

  @override
  String get downloadViaMagnet => 'Download via Magnet';

  @override
  String get downloadStarted => 'Download started';

  @override
  String get refreshChaptersFromTorrent => 'Refresh chapters from torrent';

  @override
  String get chaptersRefreshed => 'Chapters refreshed from torrent';

  @override
  String get failedToRefreshChapters => 'Failed to refresh chapters';

  @override
  String get noChaptersFound => 'No chapters found in torrent';

  @override
  String get failedToStartDownload => 'Failed to start download';

  @override
  String get noActiveDownloads => 'No active downloads';

  @override
  String get cacheExpires => 'Expires in';

  @override
  String get cacheExpired => 'Expired';

  @override
  String get cacheExpiresSoon => 'Expires soon';

  @override
  String get days => 'days';

  @override
  String get hours => 'hours';

  @override
  String get minutes => 'minutes';

  @override
  String get showMore => 'Show more';

  @override
  String get showLess => 'Show less';

  @override
  String get selectFolderDialogTitle => 'Select Download Folder';

  @override
  String get selectFolderDialogMessage => 'Please select the folder where downloaded audiobooks will be saved. In the file manager, navigate to the desired folder and tap \"Use this folder\" to confirm.';

  @override
  String get folderSelectedSuccessMessage => 'Download folder selected successfully';

  @override
  String get folderSelectionCancelledMessage => 'Folder selection cancelled';

  @override
  String currentDownloadFolder(String path) {
    return 'Current folder: $path';
  }

  @override
  String get defaultDownloadFolder => 'Default folder';

  @override
  String get pressBackAgainToExit => 'Press back again to exit';

  @override
  String get filterOptionsComingSoon => 'Filter options will be available soon. You will be able to filter by:';

  @override
  String get filterByCategory => 'Category';

  @override
  String get filterByAuthor => 'Author';

  @override
  String get filterByDate => 'Date Added';

  @override
  String get close => 'Close';

  @override
  String get openInExternalClient => 'Open in External Client';

  @override
  String get downloadTorrentInApp => 'Download using built-in torrent client';

  @override
  String get openedInExternalClient => 'Opened in external torrent client';

  @override
  String get failedToOpenExternalClient => 'Failed to open in external client';

  @override
  String get noMagnetLinkAvailable => 'No magnet link available';

  @override
  String get recommended => 'Recommended';

  @override
  String noResultsForQuery(String query) {
    return 'Nothing found for query: \"$query\"';
  }

  @override
  String get tryDifferentKeywords => 'Try changing keywords';

  @override
  String get clearSearch => 'Clear search';

  @override
  String get libraryFolderTitle => 'Library Folders';

  @override
  String get libraryFolderDescription => 'Select folders where your audiobooks are stored';

  @override
  String get defaultLibraryFolder => 'Default folder';

  @override
  String get addLibraryFolderTitle => 'Add Library Folder';

  @override
  String get addLibraryFolderSubtitle => 'Add an additional folder to scan for audiobooks';

  @override
  String get allLibraryFoldersTitle => 'All Library Folders';

  @override
  String get primaryLibraryFolder => 'Primary folder';

  @override
  String get selectLibraryFolderDialogTitle => 'Select Library Folder';

  @override
  String get selectLibraryFolderDialogMessage => 'To select a library folder:\n\n1. Navigate to the desired folder in the file manager\n2. Tap \"Use this folder\" button in the top right corner\n\nThe selected folder will be used to scan for audiobooks.';

  @override
  String get migrateLibraryFolderTitle => 'Migrate Files?';

  @override
  String get migrateLibraryFolderMessage => 'Do you want to move your existing audiobooks from the old folder to the new folder?';

  @override
  String get yes => 'Yes';

  @override
  String get no => 'No';

  @override
  String get libraryFolderSelectedSuccessMessage => 'Library folder selected successfully';

  @override
  String get libraryFolderAlreadyExistsMessage => 'This folder is already in the library folders list';

  @override
  String get libraryFolderAddedSuccessMessage => 'Library folder added successfully';

  @override
  String get removeLibraryFolderTitle => 'Remove Folder?';

  @override
  String get removeLibraryFolderMessage => 'Are you sure you want to remove this folder from the library? This will not delete the files, only stop scanning this folder.';

  @override
  String get remove => 'Remove';

  @override
  String get libraryFolderRemovedSuccessMessage => 'Library folder removed successfully';

  @override
  String get libraryFolderRemoveFailedMessage => 'Failed to remove library folder';

  @override
  String get migratingLibraryFolderMessage => 'Migrating files...';

  @override
  String get migrationCompletedSuccessMessage => 'Migration completed successfully';

  @override
  String get migrationFailedMessage => 'Migration failed';

  @override
  String get deleteConfirmationTitle => 'Delete Audiobook?';

  @override
  String get deleteConfirmationMessage => 'Are you sure you want to delete this audiobook?';

  @override
  String get deleteWarningMessage => 'Files will be permanently deleted and cannot be recovered.';

  @override
  String get deleteFilesButton => 'Delete Files';

  @override
  String get removeFromLibraryButton => 'Remove from Library';

  @override
  String get removeFromLibraryTitle => 'Remove from Library?';

  @override
  String get removeFromLibraryMessage => 'This will remove the audiobook from your library but will not delete the files. You can add it back by rescanning.';

  @override
  String get removingFromLibraryMessage => 'Removing...';

  @override
  String get removedFromLibrarySuccessMessage => 'Removed from library successfully';

  @override
  String get removeFromLibraryFailedMessage => 'Failed to remove from library';

  @override
  String get deletingMessage => 'Deleting...';

  @override
  String get deletedSuccessMessage => 'Files deleted successfully';

  @override
  String get deleteFailedMessage => 'Failed to delete files';

  @override
  String get fileInUseMessage => 'Cannot delete: File is currently being played';

  @override
  String get showInfoButton => 'Show Info';

  @override
  String get path => 'Path';

  @override
  String get fileCount => 'Files';

  @override
  String get totalSize => 'Total Size';

  @override
  String get audiobookGroupName => 'Group';

  @override
  String get storageManagementTitle => 'Storage Management';

  @override
  String get deleteSelectedButton => 'Delete Selected';

  @override
  String get storageSummaryTitle => 'Storage Summary';

  @override
  String get totalLibrarySize => 'Total Library Size';

  @override
  String get audiobookGroupsCount => 'Audiobook Groups';

  @override
  String get cacheSize => 'Cache Size';

  @override
  String get libraryFoldersTitle => 'Library Folders';

  @override
  String get cacheSectionTitle => 'Cache';

  @override
  String get totalCacheSize => 'Total Cache';

  @override
  String get clearPlaybackCacheButton => 'Clear Playback Cache';

  @override
  String get audiobookGroupsTitle => 'Audiobook Groups';

  @override
  String get deselectAllButton => 'Deselect All';

  @override
  String get selectAllButton => 'Select All';

  @override
  String get noAudiobooksMessage => 'No audiobooks found';

  @override
  String get deleteSelectedTitle => 'Delete Selected?';

  @override
  String deleteSelectedMessage(int count) {
    return 'Are you sure you want to delete $count selected audiobook(s)?';
  }

  @override
  String deleteSelectedResultMessage(int success, int failed) {
    return 'Deleted: $success, Failed: $failed';
  }

  @override
  String get clearPlaybackCacheTitle => 'Clear Playback Cache?';

  @override
  String get clearPlaybackCacheMessage => 'This will clear the playback cache. Playback may be slower until cache is rebuilt.';

  @override
  String get clearingCacheMessage => 'Clearing cache...';

  @override
  String get cacheClearedSuccessMessage => 'Cache cleared successfully';

  @override
  String get clearAllCacheTitle => 'Clear All Cache?';

  @override
  String get clearAllCacheMessage => 'This will clear all cache including playback cache, temporary files, and old logs.';

  @override
  String get storageManagementDescription => 'Manage library size, cache, and files';

  @override
  String get openStorageManagementButton => 'Open Storage Management';

  @override
  String partialDeletionSuccessMessage(int deleted, int total) {
    return 'Partially deleted: $deleted/$total files';
  }

  @override
  String get showDetailsButton => 'Details';

  @override
  String get permissionDeniedDeletionMessage => 'Permission denied: Cannot delete file';

  @override
  String get deletionDetailsTitle => 'Deletion Details';

  @override
  String deletionSummaryMessage(int deleted, int total) {
    return 'Deleted: $deleted/$total files';
  }

  @override
  String get errorsTitle => 'Errors:';

  @override
  String get retryButton => 'Retry';

  @override
  String get backgroundWorkTitle => 'Background Work';

  @override
  String get refreshDiagnostics => 'Refresh diagnostics';

  @override
  String get deviceInformation => 'Device Information';

  @override
  String get manufacturer => 'Manufacturer';

  @override
  String get customRom => 'Custom ROM';

  @override
  String get androidVersion => 'Android Version';

  @override
  String get backgroundActivityMode => 'Background Activity Mode';

  @override
  String get compatibilityOk => 'Compatibility: OK';

  @override
  String get issuesDetected => 'Issues Detected';

  @override
  String get detectedIssues => 'Detected Issues:';

  @override
  String get recommendations => 'Recommendations';

  @override
  String get manufacturerSettings => 'Manufacturer Settings';

  @override
  String get manufacturerSettingsDescription => 'To ensure stable background operation, you need to configure manufacturer-specific device settings.';

  @override
  String get autostartApp => 'Autostart Application';

  @override
  String get enableAutostart => 'Enable autostart for stable operation';

  @override
  String get batteryOptimization => 'Battery Optimization';

  @override
  String get disableBatteryOptimization => 'Disable battery optimization for the application';

  @override
  String get backgroundActivity => 'Background Activity';

  @override
  String get allowBackgroundActivity => 'Allow background activity';

  @override
  String get showInstructions => 'Show Instructions';

  @override
  String get workManagerDiagnostics => 'WorkManager Diagnostics';

  @override
  String get lastExecutions => 'Last Executions:';

  @override
  String get executionHistoryEmpty => 'Execution history is empty';

  @override
  String get total => 'Total';

  @override
  String get successful => 'Successful';

  @override
  String get errors => 'Errors';

  @override
  String get avgDelay => 'Avg. Delay';

  @override
  String get minutesAbbr => 'min';

  @override
  String delay(int minutes) {
    return 'Delay: $minutes min';
  }

  @override
  String errorLabel(String reason) {
    return 'Error: $reason';
  }

  @override
  String appStandbyBucketRestricted(String bucket) {
    return 'Application is in restricted background activity mode (Standby Bucket: $bucket)';
  }

  @override
  String get useAppMoreFrequently => 'Use the application more frequently so the system moves it to active mode';

  @override
  String aggressiveBatteryOptimization(String manufacturer, String rom) {
    return 'Device from manufacturer with aggressive battery optimization detected ($manufacturer, $rom)';
  }

  @override
  String get configureAutostartAndBattery => 'It is recommended to configure autostart and disable battery optimization for the application';

  @override
  String get openManufacturerSettings => 'Open manufacturer settings through the application menu';

  @override
  String get android14ForegroundServices => 'On Android 14+, make sure Foreground Services start correctly';

  @override
  String get compatibilityIssuesDetected => 'Background work issues detected';

  @override
  String workManagerTaskDelayed(String taskName, int hours, int minutes) {
    return 'WorkManager task \"$taskName\" delayed by $hours hours (expected: $minutes minutes)';
  }

  @override
  String foregroundServiceKilled(String serviceName) {
    return 'Foreground Service \"$serviceName\" was unexpectedly terminated by the system';
  }

  @override
  String get standbyBucketActive => 'Actively Used';

  @override
  String get standbyBucketWorkingSet => 'Frequently Used';

  @override
  String get standbyBucketFrequent => 'Regularly Used';

  @override
  String get standbyBucketRare => 'Rarely Used';

  @override
  String get standbyBucketNever => 'Never Used (Restricted)';

  @override
  String standbyBucketUnknown(int bucket) {
    return 'Unknown ($bucket)';
  }

  @override
  String get standbyBucketActiveUsed => 'Actively Used';

  @override
  String get standbyBucketFrequentlyUsed => 'Frequently Used';

  @override
  String get standbyBucketRegularlyUsed => 'Regularly Used';

  @override
  String get standbyBucketRarelyUsed => 'Rarely Used';

  @override
  String get standbyBucketNeverUsed => 'Never Used (Restricted)';

  @override
  String get manufacturerSettingsTitle => 'Settings for Stable Operation';

  @override
  String get manufacturerSettingsDialogDescription => 'To ensure stable operation, you need to configure the following settings:';

  @override
  String get enableAutostartStep => '1. Enable application autostart';

  @override
  String get disableBatteryOptimizationStep => '2. Disable battery optimization for the application';

  @override
  String get allowBackgroundActivityStep => '3. Allow background activity';

  @override
  String detectedRom(String rom) {
    return 'Detected ROM: $rom';
  }

  @override
  String get skip => 'Skip';

  @override
  String get gotIt => 'Got It';

  @override
  String get backgroundCompatibilityBannerTitle => 'Background Operation Settings';

  @override
  String get backgroundCompatibilityBannerMessage => 'To ensure stable background operation, you may need to configure device settings.';

  @override
  String get dismiss => 'Dismiss';

  @override
  String get loginSuccessfulMessage => 'Login successful!';

  @override
  String get openSettingsButton => 'Open Settings';

  @override
  String get compatibilityDiagnosticsTitle => 'Compatibility & Diagnostics';

  @override
  String get compatibilityDiagnosticsSubtitle => 'Compatibility check and manufacturer settings configuration';

  @override
  String get selectFolderButton => 'Select Folder';

  @override
  String get continueButton => 'Continue';

  @override
  String errorAddingFolder(String error) {
    return 'Error adding folder: \$error';
  }

  @override
  String get noAudiobookGroupProvided => 'No audiobook group provided';

  @override
  String get sleepTimerPaused => 'Sleep timer: Playback paused';

  @override
  String failedToChangeSpeed(String error) {
    return 'Failed to change speed: \$error';
  }

  @override
  String get cancelTimerButton => 'Cancel timer';

  @override
  String get endOfChapterLabel => 'End of chapter';

  @override
  String get atEndOfChapterLabel => 'At end of chapter';

  @override
  String get itemsInTrashLabel => 'Items in Trash';

  @override
  String get trashSizeLabel => 'Trash Size';

  @override
  String get manageTrashButton => 'Manage Trash';

  @override
  String get fileLabel => 'file';

  @override
  String get filesLabel => 'files';

  @override
  String failedToLoadTrash(String error) {
    return 'Failed to load trash: \$error';
  }

  @override
  String get itemRestoredSuccessfully => 'Item restored successfully';

  @override
  String get failedToRestoreItem => 'Failed to restore item';

  @override
  String errorRestoringItem(String error) {
    return 'Error restoring item: \$error';
  }

  @override
  String get permanentlyDeleteTitle => 'Permanently Delete';

  @override
  String permanentlyDeleteMessage(String name) {
    return 'Are you sure you want to permanently delete \"$name\"? This action cannot be undone.';
  }

  @override
  String get itemPermanentlyDeleted => 'Item permanently deleted';

  @override
  String get failedToDeleteItem => 'Failed to delete item';

  @override
  String errorDeletingItem(String error) {
    return 'Error deleting item: \$error';
  }

  @override
  String get clearAllTrashTitle => 'Clear All Trash';

  @override
  String get clearAllTrashMessage => 'Are you sure you want to permanently delete all items in trash? This action cannot be undone.';

  @override
  String get filePickerAlreadyOpen => 'File picker is already open. Please close it first.';

  @override
  String get applyButton => 'Apply';

  @override
  String get lastMessageSort => 'Last message';

  @override
  String get topicNameSort => 'Topic name';

  @override
  String get postingTimeSort => 'Posting time';

  @override
  String get operationTimedOut => 'Operation timed out. Please try again.';

  @override
  String get permissionDeniedDownloads => 'Permission denied. Please check app permissions in settings.';

  @override
  String get downloadNotFound => 'Download not found. It may have been removed.';

  @override
  String get anErrorOccurred => 'An error occurred. Please try again.';

  @override
  String initializationError(String error) {
    return 'Initialization error: \$error';
  }

  @override
  String criticalError(String error) {
    return 'Critical error: \$error';
  }

  @override
  String capabilityCheckError(String error) {
    return 'Error checking capabilities: \$error';
  }

  @override
  String filesSelected(int count) {
    return 'Files selected: \$count';
  }

  @override
  String fileSelectionError(String error) {
    return 'Error selecting files: \$error';
  }

  @override
  String imagesSelected(int count) {
    return 'Images selected: \$count';
  }

  @override
  String imageSelectionError(String error) {
    return 'Error selecting images: \$error';
  }

  @override
  String get testNotificationTitle => 'Test notification';

  @override
  String get testNotificationBody => 'This is a test notification from JaBook';

  @override
  String get notificationSent => 'Notification sent';

  @override
  String get failedToSendNotification => 'Failed to send notification (channel not implemented)';

  @override
  String bluetoothAvailable(String available) {
    return 'Bluetooth available: \$available';
  }

  @override
  String pairedDevicesCount(int count) {
    return 'Paired devices: \$count';
  }

  @override
  String bluetoothCheckError(String error) {
    return 'Error checking Bluetooth: \$error';
  }

  @override
  String get systemCapabilitiesTitle => 'System Capabilities';

  @override
  String get fileAccessCapability => 'File Access';

  @override
  String get imageAccessCapability => 'Image Access';

  @override
  String get cameraCapability => 'Camera';

  @override
  String photoTaken(String path) {
    return 'Photo taken: \$path';
  }

  @override
  String get photoNotTaken => 'Photo not taken';

  @override
  String cameraError(String error) {
    return 'Camera error: \$error';
  }

  @override
  String get notificationsCapability => 'Notifications';

  @override
  String get capabilityExplanationButton => 'Explain Capabilities';

  @override
  String get testButton => 'Test';

  @override
  String get permissionsForJaBookTitle => 'Permissions for JaBook';

  @override
  String get fileAccessPermissionTitle => 'File Access';

  @override
  String get fileAccessPermissionDescription => 'Needed to save and play audiobooks.';

  @override
  String get notificationsPermissionTitle => 'Notifications';

  @override
  String get batteryOptimizationPermissionTitle => 'Battery Optimization';

  @override
  String get batteryOptimizationPermissionDescription => 'So the app can work in background for playback.';

  @override
  String get permissionsHelpMessage => 'These permissions will help provide a better app experience.';

  @override
  String urlLabel(String url) {
    return 'URL: \$url';
  }

  @override
  String get rutrackerTitle => 'RuTracker';

  @override
  String get webViewLoginButton => 'Login to RuTracker via WebView';

  @override
  String get webViewLoginSubtitle => 'Pass Cloudflare/captcha and save cookies for client';

  @override
  String get cookieSavedMessage => 'Cookies saved for HTTP client';

  @override
  String get failedToParseSearchResultsEncoding => 'Failed to parse search results due to encoding issue. This may be a temporary server problem. Please try again. If the problem persists, try changing the mirror in Settings → Sources.';

  @override
  String get failedToParseSearchResultsStructure => 'Failed to parse search results. The page structure may have changed. Please try again. If the problem persists, try changing the mirror in Settings → Sources.';

  @override
  String searchFailedMessage(String errorType) {
    return 'Search failed: $errorType';
  }

  @override
  String get topicTitleSort => 'Topic title';

  @override
  String get postTimeSort => 'Post time';

  @override
  String localStreamServerStarted(String host, int port) {
    return 'Local stream server started on http://$host:$port';
  }

  @override
  String failedToStartStreamServer(String error) {
    return 'Failed to start stream server: $error';
  }

  @override
  String get localStreamServerStopped => 'Local stream server stopped';

  @override
  String failedToStopStreamServer(String error) {
    return 'Failed to stop stream server: $error';
  }

  @override
  String get missingBookIdParameter => 'Missing book ID parameter';

  @override
  String get invalidFileIndexParameter => 'Invalid file index parameter';

  @override
  String get fileNotFound => 'File not found';

  @override
  String get streamingError => 'Streaming error';

  @override
  String get invalidRangeHeader => 'Invalid range header';

  @override
  String get requestedRangeNotSatisfiable => 'Requested range not satisfiable';

  @override
  String get rangeRequestError => 'Range request error';

  @override
  String get staticFileError => 'Static file error';

  @override
  String failedToStartAudioService(String error) {
    return 'Failed to start audio service: $error';
  }

  @override
  String failedToPlayMedia(String error) {
    return 'Failed to play media: $error';
  }

  @override
  String failedToPauseMedia(String error) {
    return 'Failed to pause media: $error';
  }

  @override
  String failedToStopMedia(String error) {
    return 'Failed to stop media: $error';
  }

  @override
  String failedToSeek(String error) {
    return 'Failed to seek: $error';
  }

  @override
  String failedToSetSpeed(String error) {
    return 'Failed to set speed: $error';
  }

  @override
  String get failedToInitializeLogger => 'Failed to initialize logger';

  @override
  String get failedToWriteLog => 'Failed to write log';

  @override
  String get logRotationFailed => 'Log rotation failed';

  @override
  String get failedToRotateLogs => 'Failed to rotate logs';

  @override
  String get failedToCleanOldLogs => 'Failed to clean old logs';

  @override
  String errorSharingLogs(String error) {
    return 'Error sharing logs: $error';
  }

  @override
  String get failedToShareLogs => 'Failed to share logs';

  @override
  String get failedToReadLogs => 'Failed to read logs';

  @override
  String get cacheManagerNotInitialized => 'CacheManager not initialized';

  @override
  String failedToParseSearchResults(String error) {
    return 'Failed to parse search results: $error';
  }

  @override
  String failedToParseTopicDetails(String error) {
    return 'Failed to parse topic details: $error';
  }

  @override
  String get failedToParseCategories => 'Failed to parse categories';

  @override
  String get failedToParseCategoryTopics => 'Failed to parse category topics';

  @override
  String get radioPlayCategory => 'Radio Play';

  @override
  String get audiobookCategory => 'Audiobook';

  @override
  String get biographyCategory => 'Biography';

  @override
  String get memoirsCategory => 'Memoirs';

  @override
  String get historyCategory => 'History';

  @override
  String get addedLabel => 'Added';

  @override
  String get invalidMagnetUrlMissingHash => 'Invalid magnet URL: missing info hash';

  @override
  String get invalidInfoHashLength => 'Invalid info hash length';

  @override
  String failedToStartDownloadWithError(String error) {
    return 'Failed to start download: $error';
  }

  @override
  String get downloadNotFoundTorrent => 'Download not found';

  @override
  String failedToPauseDownload(String error) {
    return 'Failed to pause download: $error';
  }

  @override
  String failedToResumeDownload(String error) {
    return 'Failed to resume download: $error';
  }

  @override
  String failedToRemoveDownload(String error) {
    return 'Failed to remove download: $error';
  }

  @override
  String failedToGetActiveDownloads(String error) {
    return 'Failed to get active downloads: $error';
  }

  @override
  String failedToShutdownTorrentManager(String error) {
    return 'Failed to shutdown torrent manager: $error';
  }

  @override
  String get noHealthyEndpointsAvailable => 'No healthy endpoints available';

  @override
  String get authRepositoryProviderMustBeOverridden => 'AuthRepositoryProvider must be overridden with proper context';

  @override
  String get useEndpointManagerGetActiveEndpoint => 'Use EndpointManager.getActiveEndpoint() for dynamic mirror selection';

  @override
  String get cacheManagerNotInitializedConfig => 'CacheManager not initialized';

  @override
  String searchFailedWithMessage(String message) {
    return 'Search failed: $message';
  }

  @override
  String get failedToSearchAudiobooks => 'Failed to search audiobooks';

  @override
  String failedToFetchCategories(String message) {
    return 'Failed to fetch categories: $message';
  }

  @override
  String get failedToGetCategories => 'Failed to get categories';

  @override
  String failedToGetCategoryAudiobooksWithMessage(String message) {
    return 'Failed to get category audiobooks: $message';
  }

  @override
  String get failedToGetCategoryAudiobooks => 'Failed to get category audiobooks';

  @override
  String failedToFetchAudiobookDetails(String message) {
    return 'Failed to fetch audiobook details: $message';
  }

  @override
  String get failedToGetAudiobookDetails => 'Failed to get audiobook details';

  @override
  String failedToFetchNewReleases(String message) {
    return 'Failed to fetch new releases: $message';
  }

  @override
  String failedToSaveCredentials(String error) {
    return 'Failed to save credentials: $error';
  }

  @override
  String failedToRetrieveCredentials(String error) {
    return 'Failed to retrieve credentials: $error';
  }

  @override
  String failedToClearCredentials(String error) {
    return 'Failed to clear credentials: $error';
  }

  @override
  String get noCredentialsToExport => 'No credentials to export';

  @override
  String unsupportedExportFormat(String format) {
    return 'Unsupported export format: $format';
  }

  @override
  String get invalidCsvFormat => 'Invalid CSV format';

  @override
  String get invalidCsvData => 'Invalid CSV data';

  @override
  String get invalidJsonFormat => 'Invalid JSON format';

  @override
  String unsupportedImportFormat(String format) {
    return 'Unsupported import format: $format';
  }

  @override
  String failedToImportCredentials(String error) {
    return 'Failed to import credentials: $error';
  }

  @override
  String errorWithDetails(String error) {
    return 'Error: $error';
  }

  @override
  String get fileSingular => 'file';

  @override
  String get filePlural => 'files';

  @override
  String deleteSelectedAudiobooksConfirmation(int count) {
    return 'Are you sure you want to delete $count selected audiobook(s)?';
  }

  @override
  String get clearPlaybackCacheDescription => 'This will clear the playback cache. Playback may be slower until cache is rebuilt.';

  @override
  String get clearAllCacheDescription => 'This will clear all cache including playback cache, temporary files, and old logs.';

  @override
  String get sessionExpiredTitle => 'Session Expired';

  @override
  String get sessionExpiredMessage => 'Your session has expired. Please log in again.';

  @override
  String get invalidCredentialsTitle => 'Authorization Error';

  @override
  String get invalidCredentialsMessage => 'Invalid username or password. Please check your credentials.';

  @override
  String get loginRequiredTitle => 'Authentication Required';

  @override
  String get loginRequiredMessage => 'You need to log in to perform this action.';

  @override
  String get authorizationErrorTitle => 'Authorization Error';

  @override
  String get accessDeniedMessage => 'Access denied. Please check your credentials or log in again.';

  @override
  String get networkErrorTitle => 'Network Error';

  @override
  String get networkRequestFailedMessage => 'Failed to complete the request. Please check your internet connection.';

  @override
  String get errorOccurredMessage => 'An error occurred while performing the operation.';

  @override
  String get sessionExpiredSnackBar => 'Session expired. Please log in again.';

  @override
  String get invalidCredentialsSnackBar => 'Invalid username or password.';

  @override
  String get authorizationErrorSnackBar => 'Authorization error. Please check your credentials.';

  @override
  String get networkErrorSnackBar => 'Network error. Please check your connection.';

  @override
  String get captchaVerificationRequired => 'Captcha verification required. Please try again later.';

  @override
  String get networkErrorCheckConnection => 'Network error. Please check your connection and try again.';

  @override
  String get authenticationFailedMessage => 'Authentication failed. Please check your credentials and try again.';

  @override
  String loginFailedWithError(String error) {
    return 'Login failed: $error';
  }

  @override
  String get noAccessibleAudioFiles => 'No accessible audio files found';

  @override
  String get aboutTitle => 'About';

  @override
  String get aboutAppSlogan => 'Modern audiobook player for torrents';

  @override
  String get appVersion => 'App Version';

  @override
  String get buildNumber => 'Build';

  @override
  String get packageId => 'Package ID';

  @override
  String get licenseTitle => 'License';

  @override
  String get licenseDescription => 'JaBook is distributed under the Apache 2.0 license. Tap to view the full license text and list of third-party libraries.';

  @override
  String get mainLicense => 'Main License';

  @override
  String get thirdPartyLicenses => 'Third-Party Libraries';

  @override
  String get telegramChannel => 'Telegram Channel';

  @override
  String get openTelegramChannel => 'Open channel in Telegram';

  @override
  String get contactDeveloper => 'Contact Developer';

  @override
  String get openEmailClient => 'Open email client';

  @override
  String get supportProject => 'Support Project';

  @override
  String get supportProjectDescription => 'Donate or support development';

  @override
  String get githubRepository => 'GitHub';

  @override
  String get githubRepositoryDescription => 'Source code and changelog';

  @override
  String get changelog => 'Changelog';

  @override
  String get changelogDescription => 'Version history and updates';

  @override
  String get issues => 'Issues';

  @override
  String get issuesDescription => 'Report bugs and request features';

  @override
  String get aboutDeveloper => 'About Developer';

  @override
  String get aboutDeveloperText => 'JaBook is developed by Jabook Contributors team. This is an open source project created for convenient listening to audiobooks from open sources and torrents. No ads, no registration — just pure listening.';

  @override
  String get copyInfo => 'Copy';

  @override
  String get infoCopied => 'Info copied';

  @override
  String get failedToOpenLink => 'Failed to open link';

  @override
  String get failedToOpenEmail => 'Failed to open email client';

  @override
  String get viewInApp => 'View in app';

  @override
  String get viewLicenses => 'View licenses';

  @override
  String get licenseOnGitHub => 'License on GitHub';

  @override
  String get viewLicenseFile => 'View LICENSE file';

  @override
  String get appDiscussionQuestionsReviews => 'App discussion, questions and reviews';

  @override
  String get jabookContributors => 'Jabook Contributors';

  @override
  String get github => 'GitHub';

  @override
  String get versionInformationLongPress => 'Version information. Long press to copy.';

  @override
  String get unknown => 'Unknown';

  @override
  String get emailFeedbackSubject => 'JaBook - Feedback';

  @override
  String get emailFeedbackApp => 'App: JaBook';

  @override
  String get emailFeedbackVersion => 'Version:';

  @override
  String get emailFeedbackDevice => 'Device:';

  @override
  String get emailFeedbackAndroid => 'Android:';

  @override
  String get emailFeedbackDescription => 'Description of issue / suggestion:';

  @override
  String get aboutSectionDescription => 'App information and links';

  @override
  String get aboutSectionSubtitle => 'Version, license, and developer information';

  @override
  String get forum4Pda => '4PDA';

  @override
  String get rewindDurationTitle => 'Rewind Duration';

  @override
  String get forwardDurationTitle => 'Forward Duration';

  @override
  String get inactivityTimeoutTitle => 'Inactivity Timeout';

  @override
  String get inactivityTimeoutLabel => 'Set inactivity timeout';

  @override
  String get minute => 'minute';

  @override
  String get rewind => 'Rewind';

  @override
  String get forward => 'Forward';

  @override
  String get currentPosition => 'Current';

  @override
  String get newPosition => 'New';

  @override
  String get secondsLabel => 'seconds';

  @override
  String get languageSettingsLabel => 'Language settings';

  @override
  String get mirrorSourceSettingsLabel => 'Mirror and source settings';

  @override
  String get rutrackerSessionLabel => 'RuTracker session management';

  @override
  String get metadataManagementLabel => 'Metadata management';

  @override
  String get themeSettingsLabel => 'Theme settings';

  @override
  String get audioPlaybackSettingsLabel => 'Audio playback settings';

  @override
  String get downloadSettingsLabel => 'Download settings';

  @override
  String get libraryFolderSettingsLabel => 'Library folder settings';

  @override
  String get storageManagementLabel => 'Storage management';

  @override
  String get cacheSettingsLabel => 'Cache settings';

  @override
  String get appPermissionsLabel => 'App permissions';

  @override
  String get aboutAppLabel => 'About app';

  @override
  String get backgroundTaskCompatibilityLabel => 'Background task compatibility';

  @override
  String get backupRestoreLabel => 'Backup and restore';

  @override
  String get selectFavorites => 'Select';

  @override
  String get clearSelectedFavorites => 'Delete Selected';

  @override
  String get clearAllFavorites => 'Clear All';

  @override
  String get clearAllFavoritesTitle => 'Clear All Favorites?';

  @override
  String get clearAllFavoritesMessage => 'This will remove all favorite audiobooks. This action cannot be undone.';

  @override
  String get favoritesCleared => 'Favorites cleared';

  @override
  String favoritesDeleted(int count) {
    return '$count favorite(s) deleted';
  }

  @override
  String get noFavoritesSelected => 'No favorites selected';

  @override
  String get selected => 'selected';

  @override
  String get sortByLabel => 'Sort by:';

  @override
  String get groupByLabel => 'Group by:';

  @override
  String get sortByNameAsc => 'Name (A-Z)';

  @override
  String get sortByNameDesc => 'Name (Z-A)';

  @override
  String get sortBySizeAsc => 'Size (Smallest)';

  @override
  String get sortBySizeDesc => 'Size (Largest)';

  @override
  String get sortByDateAsc => 'Date (Oldest)';

  @override
  String get sortByDateDesc => 'Date (Newest)';

  @override
  String get sortByFilesAsc => 'Files (Fewest)';

  @override
  String get sortByFilesDesc => 'Files (Most)';

  @override
  String get groupByNone => 'None';

  @override
  String get groupByFirstLetter => 'First Letter';

  @override
  String get scanningLibrary => 'Scanning library...';

  @override
  String get manufacturerSettingsNotAvailable => 'Settings Not Available';

  @override
  String get manufacturerSettingsNotAvailableMessage => 'Manufacturer-specific settings are only available on Android devices.';

  @override
  String get manufacturerSettingsDefaultTitle => 'Settings for Stable Operation';

  @override
  String get manufacturerSettingsDefaultMessage => 'To ensure stable operation, you need to configure the following settings:';

  @override
  String get manufacturerSettingsDefaultStep1 => '1. Enable application autostart';

  @override
  String get manufacturerSettingsDefaultStep2 => '2. Disable battery optimization for the application';

  @override
  String get manufacturerSettingsDefaultStep3 => '3. Allow background activity';

  @override
  String get manufacturerSettingsMiuiTitle => 'MIUI Settings for Stable Operation';

  @override
  String get manufacturerSettingsMiuiMessage => 'On Xiaomi/Redmi/Poco devices, you need to configure the following settings:';

  @override
  String manufacturerSettingsMiuiStep1(String appName) {
    return '1. Autostart: Settings → Apps → Permission management → Autostart → Enable for $appName';
  }

  @override
  String manufacturerSettingsMiuiStep2(String appName) {
    return '2. Battery optimization: Settings → Battery → Battery optimization → Select $appName → Don\'t optimize';
  }

  @override
  String manufacturerSettingsMiuiStep3(String appName) {
    return '3. Background activity: Settings → Apps → $appName → Battery → Background activity → Allow';
  }

  @override
  String get manufacturerSettingsEmuiTitle => 'EMUI Settings for Stable Operation';

  @override
  String get manufacturerSettingsEmuiMessage => 'On Huawei/Honor devices, you need to configure the following settings:';

  @override
  String manufacturerSettingsEmuiStep1(String appName) {
    return '1. App protection: Settings → Apps → App protection → $appName → Enable autostart';
  }

  @override
  String manufacturerSettingsEmuiStep2(String appName) {
    return '2. Battery management: Settings → Battery → Battery management → $appName → Don\'t optimize';
  }

  @override
  String manufacturerSettingsEmuiStep3(String appName) {
    return '3. Background activity: Settings → Apps → $appName → Battery → Allow background activity';
  }

  @override
  String get manufacturerSettingsColorosTitle => 'ColorOS/RealmeUI Settings for Stable Operation';

  @override
  String get manufacturerSettingsColorosMessage => 'On Oppo/Realme devices, you need to configure the following settings:';

  @override
  String manufacturerSettingsColorosStep1(String appName) {
    return '1. Autostart: Settings → Apps → Autostart → Enable for $appName';
  }

  @override
  String manufacturerSettingsColorosStep2(String appName) {
    return '2. Battery optimization: Settings → Battery → Battery optimization → $appName → Don\'t optimize';
  }

  @override
  String manufacturerSettingsColorosStep3(String appName) {
    return '3. Background activity: Settings → Apps → $appName → Battery → Allow background activity';
  }

  @override
  String get manufacturerSettingsOxygenosTitle => 'OxygenOS Settings for Stable Operation';

  @override
  String get manufacturerSettingsOxygenosMessage => 'On OnePlus devices, you need to configure the following settings:';

  @override
  String manufacturerSettingsOxygenosStep1(String appName) {
    return '1. Autostart: Settings → Apps → Autostart → Enable for $appName';
  }

  @override
  String manufacturerSettingsOxygenosStep2(String appName) {
    return '2. Battery optimization: Settings → Battery → Battery optimization → $appName → Don\'t optimize';
  }

  @override
  String manufacturerSettingsOxygenosStep3(String appName) {
    return '3. Background activity: Settings → Apps → $appName → Battery → Allow background activity';
  }

  @override
  String get manufacturerSettingsFuntouchosTitle => 'FuntouchOS/OriginOS Settings for Stable Operation';

  @override
  String get manufacturerSettingsFuntouchosMessage => 'On Vivo devices, you need to configure the following settings:';

  @override
  String manufacturerSettingsFuntouchosStep1(String appName) {
    return '1. Autostart: Settings → Apps → Autostart → Enable for $appName';
  }

  @override
  String manufacturerSettingsFuntouchosStep2(String appName) {
    return '2. Battery optimization: Settings → Battery → Battery optimization → $appName → Don\'t optimize';
  }

  @override
  String manufacturerSettingsFuntouchosStep3(String appName) {
    return '3. Background activity: Settings → Apps → $appName → Battery → Allow background activity';
  }

  @override
  String get manufacturerSettingsFlymeTitle => 'Flyme Settings for Stable Operation';

  @override
  String get manufacturerSettingsFlymeMessage => 'On Meizu devices, you need to configure the following settings:';

  @override
  String manufacturerSettingsFlymeStep1(String appName) {
    return '1. Autostart: Settings → Apps → Autostart → Enable for $appName';
  }

  @override
  String manufacturerSettingsFlymeStep2(String appName) {
    return '2. Battery optimization: Settings → Battery → Battery optimization → $appName → Don\'t optimize';
  }

  @override
  String manufacturerSettingsFlymeStep3(String appName) {
    return '3. Background activity: Settings → Apps → $appName → Battery → Allow background activity';
  }

  @override
  String get manufacturerSettingsOneuiTitle => 'One UI Settings for Stable Operation';

  @override
  String get manufacturerSettingsOneuiMessage => 'On Samsung devices, it is recommended to configure the following settings:';

  @override
  String manufacturerSettingsOneuiStep1(String appName) {
    return '1. Battery optimization: Settings → Apps → $appName → Battery → Don\'t optimize';
  }

  @override
  String manufacturerSettingsOneuiStep2(String appName) {
    return '2. Background activity: Settings → Apps → $appName → Battery → Background activity → Allow';
  }

  @override
  String get manufacturerSettingsOneuiStep3 => '3. Autostart: Usually not required on Samsung, but you can check in app settings';
}
