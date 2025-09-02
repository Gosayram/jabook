/// Constants for RuTracker URL paths and endpoints.
///
/// This class provides centralized URL constants to avoid hardcoded values
/// throughout the codebase and ensure consistency.
class UrlConstants {
  /// Private constructor to prevent instantiation.
  UrlConstants._();

  /// Base path for forum endpoints.
  static const String forumBasePath = '/forum';

  /// Search endpoint path.
  static const String searchPath = '$forumBasePath/tracker.php';

  /// Topic view endpoint path.
  static const String topicViewPath = '$forumBasePath/viewtopic.php';

  /// Login endpoint path.
  static const String loginPath = '$forumBasePath/login.php';

  /// Logout endpoint path.
  static const String logoutPath = '$forumBasePath/logout.php';

  /// Profile endpoint path.
  static const String profilePath = '$forumBasePath/profile.php';

  /// Download endpoint path.
  static const String downloadPath = '$forumBasePath/dl.php';

  /// Gets the full search URL by combining base URL with search path.
  static String getSearchUrl(String baseUrl) => '$baseUrl$searchPath';

  /// Gets the full topic view URL by combining base URL with topic path and ID.
  static String getTopicViewUrl(String baseUrl, String topicId) => 
      '$baseUrl$topicViewPath?t=$topicId';

  /// Gets the full download URL by combining base URL with download path and ID.
  static String getDownloadUrl(String baseUrl, String torrentId) => 
      '$baseUrl$downloadPath?id=$torrentId';

  /// Gets the full login URL by combining base URL with login path.
  static String getLoginUrl(String baseUrl) => '$baseUrl$loginPath';

  /// Gets the full logout URL by combining base URL with logout path.
  static String getLogoutUrl(String baseUrl) => '$baseUrl$logoutPath';

  /// Gets the full profile URL by combining base URL with profile path.
  static String getProfileUrl(String baseUrl) => '$baseUrl$profilePath';
}