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
}
