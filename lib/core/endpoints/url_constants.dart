import 'package:jabook/core/constants/category_constants.dart';

/// URL constants for RuTracker endpoints
class RuTrackerUrls {
  /// Private constructor to prevent instantiation
  RuTrackerUrls._();

  /// Base URL for RuTracker forum (primary mirror)
  /// Note: dynamic mirror selection is preferred via EndpointManager.
  /// Available mirrors: rutracker.net, rutracker.me, rutracker.org
  static const String base = 'https://rutracker.net';
  
  /// Main index page URL
  static const String index = '$base/forum/index.php';
  
  /// Tracker page URL
  static const String tracker = '$base/forum/tracker.php';
  
  /// Search page URL
  static const String search = '$base/forum/search.php';
  
  /// Forum view page URL
  static const String viewForum = '$base/forum/viewforum.php';
  
  /// Topic view page URL
  static const String viewTopic = '$base/forum/viewtopic.php';
  
  /// Login page URL
  static const String login = '$base/forum/login.php';

  /// Profile page URL
  static const String profile = '$base/forum/profile.php';

  /// Download page URL
  static const String download = '$base/forum/dl.php';
  
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