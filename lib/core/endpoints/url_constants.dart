import 'package:jabook/core/constants/category_constants.dart';

/// URL constants for RuTracker endpoints
class RuTrackerUrls {
  /// Private constructor to prevent instantiation
  RuTrackerUrls._();

  /// Base URL for RuTracker forum (primary mirror)
  static const String base = 'https://rutracker.me/forum';
  
  /// Main index page URL
  static const String index = '$base/index.php';
  
  /// Tracker page URL
  static const String tracker = '$base/tracker.php';
  
  /// Search page URL
  static const String search = '$base/search.php';
  
  /// Forum view page URL
  static const String viewForum = '$base/viewforum.php';
  
  /// Topic view page URL
  static const String viewTopic = '$base/viewtopic.php';
  
  /// Login page URL
  static const String login = '$base/login.php';

  /// Profile page URL
  static const String profile = '$base/profile.php';

  /// Download page URL
  static const String download = '$base/dl.php';
  
  /// Audiobooks category URL
  static const String audiobooksCategory = '$index?c=${CategoryConstants.audiobooksCategoryId}';

  /// Radiospektakli category URL
  static const String radiospektakli = '$viewForum?f=574';

  /// Biographies category URL
  static const String biographies = '$viewForum?f=1036';

  /// History category URL
  static const String history = '$viewForum?f=400';
  
  /// Atom feed URL for RuTracker
  static const String atomFeed = 'https://feed.rutracker.cc/atom/f';
  
  /// OpenSearch URL for RuTracker
  static const String opensearch = 'https://static.rutracker.cc/opensearch.xml';
  
  /// Builds category URL with forum ID
  static String categoryUrl(String forumId) => '$viewForum?f=$forumId';
  
  /// Builds topic URL with topic ID
  static String topicUrl(String topicId) => '$viewTopic?t=$topicId';
  
  /// Builds download URL with topic ID
  static String downloadUrl(String topicId) => '$download?t=$topicId';
  
  /// Builds search URL with query and parameters
  static String searchUrl(String query, {String? forumId, int start = 0}) {
    final params = {
      'nm': query,
      if (forumId != null) 'f': forumId,
      if (start > 0) 'start': start.toString(),
    };
    
    final queryString = params.entries
        .map((e) => '${e.key}=${Uri.encodeComponent(e.value)}')
        .join('&');
    
    return '$search?$queryString';
  }
  
  /// Builds paginated forum URL
  static String paginatedForumUrl(String forumId, int page, {int perPage = 50}) {
    final start = (page - 1) * perPage;
    return '$viewForum?f=$forumId&start=$start';
  }
}