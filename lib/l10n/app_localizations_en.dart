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
  String get languageDescription => 'Choose your preferred language for the app interface';

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
  String get themeTitle => 'Theme';

  @override
  String get themeDescription => 'Customize the appearance of the app';

  @override
  String get darkMode => 'Dark Mode';

  @override
  String get highContrast => 'High Contrast';

  @override
  String get audioTitle => 'Audio';

  @override
  String get audioDescription => 'Configure audio playback settings';

  @override
  String get playbackSpeed => 'Playback Speed';

  @override
  String get skipDuration => 'Skip Duration';

  @override
  String get downloadsTitle => 'Downloads';

  @override
  String get downloadsDescription => 'Manage download preferences and storage';

  @override
  String get downloadLocation => 'Download Location';

  @override
  String get wifiOnlyDownloads => 'Wi-Fi Only Downloads';

  @override
  String get debugTools => 'Debug Tools';

  @override
  String get logsTab => 'Logs';

  @override
  String get mirrorsTab => 'Mirrors';

  @override
  String get downloadsTab => 'Downloads';

  @override
  String get cacheTab => 'Cache';

  @override
  String get testAllMirrors => 'Test All Mirrors';

  @override
  String get statusLabel => 'Status: ';

  @override
  String get activeStatus => 'Active';

  @override
  String get disabledStatus => 'Disabled';

  @override
  String get lastOkLabel => 'Last OK: ';

  @override
  String get rttLabel => 'RTT: ';

  @override
  String get milliseconds => 'ms';

  @override
  String get cacheStatistics => 'Cache Statistics';

  @override
  String get totalEntries => 'Total entries: ';

  @override
  String get searchCache => 'Search cache: ';

  @override
  String get topicCache => 'Topic cache: ';

  @override
  String get memoryUsage => 'Memory usage: ';

  @override
  String get clearAllCache => 'Clear All Cache';

  @override
  String get cacheClearedSuccessfully => 'Cache cleared successfully';

  @override
  String get mirrorHealthCheckCompleted => 'Mirror health check completed';

  @override
  String get exportFunctionalityComingSoon => 'Export functionality coming soon';

  @override
  String get logsExportedSuccessfully => 'Logs exported successfully';

  @override
  String get failedToExportLogs => 'Failed to export logs';

  @override
  String get requestTimedOut => 'Request timed out. Please check your connection.';

  @override
  String networkError(Object errorMessage) {
    return 'Network error: $errorMessage';
  }

  @override
  String get downloadFunctionalityComingSoon => 'Download functionality coming soon!';

  @override
  String get magnetLinkCopied => 'Magnet link copied to clipboard';

  @override
  String get dataLoadedFromCache => 'Data loaded from cache';

  @override
  String get failedToLoadTopic => 'Failed to load topic';

  @override
  String errorLoadingTopic(Object error) {
    return 'Error loading topic: $error';
  }

  @override
  String get unknownChapter => 'Unknown Chapter';

  @override
  String get chaptersLabel => 'Chapters';

  @override
  String get magnetLinkLabel => 'Magnet Link';

  @override
  String get downloadLabel => 'Download';

  @override
  String get playLabel => 'Play';

  @override
  String get pauseLabel => 'Pause';

  @override
  String get stopLabel => 'Stop';

  @override
  String get failedToLoadAudio => 'Failed to load audiobook';

  @override
  String get sampleAudiobook => 'Sample Audiobook';

  @override
  String get sampleAuthor => 'Sample Author';

  @override
  String get chapter1 => 'Chapter 1';

  @override
  String get chapter2 => 'Chapter 2';

  @override
  String get mirrorsScreenTitle => 'Mirrors Screen';

  @override
  String get downloadStatusUnknown => 'unknown';

  @override
  String downloadProgressLabel(Object progress) {
    return 'Progress: $progress%';
  }

  @override
  String get statusLabelNoColon => 'Status';

  @override
  String copyToClipboardLabel(Object label) {
    return '$label copied to clipboard';
  }

  @override
  String get navSearch => 'Search';

  @override
  String get navLibrary => 'Library';

  @override
  String get navSettings => 'Settings';

  @override
  String get navDebug => 'Debug';

  @override
  String get mirrorStatusActive => 'Active';

  @override
  String get mirrorStatusInactive => 'Inactive';

  @override
  String get mirrorStatusDisabled => 'Disabled';

  @override
  String get mirrorTestIndividual => 'Test this mirror';

  @override
  String get mirrorDomainLabel => 'Domain';

  @override
  String get mirrorResponseTime => 'Response time';

  @override
  String get mirrorLastCheck => 'Last checked';
}
