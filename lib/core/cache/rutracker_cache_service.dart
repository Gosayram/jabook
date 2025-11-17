import 'package:jabook/core/cache/cache_manager.dart';
import 'package:jabook/core/session/session_storage.dart';
import 'package:sembast/sembast.dart';

/// Service for caching RuTracker search results and topic details.
///
/// This service provides TTL-based caching for search results (1 hour)
/// and topic details (24 hours) to reduce network requests.
class RuTrackerCacheService {
  /// Private constructor for singleton pattern.
  RuTrackerCacheService._();

  /// Factory constructor to get the singleton instance.
  factory RuTrackerCacheService() => _instance;

  /// Singleton instance of the RuTrackerCacheService.
  static final RuTrackerCacheService _instance = RuTrackerCacheService._();

  /// Cache manager instance.
  final CacheManager _cacheManager = CacheManager();

  /// Session storage for getting session ID.
  final SessionStorage _sessionStorage = const SessionStorage();

  /// TTL for search results in seconds (1 hour).
  static const int searchResultsTTL = 3600;

  /// TTL for topic details in seconds (24 hours).
  static const int topicDetailsTTL = 86400;

  /// Initializes the cache service with database connection.
  Future<void> initialize(Database db) async {
    await _cacheManager.initialize(db);
  }

  /// Caches search results with TTL and session binding.
  ///
  /// The [query] parameter is the search query.
  /// The [results] parameter is the list of audiobook maps to cache.
  /// Results are bound to the current session ID to ensure cache
  /// is cleared when session changes.
  Future<void> cacheSearchResults(
      String query, List<Map<String, dynamic>> results) async {
    final sessionId = await _sessionStorage.getSessionId();
    final key = _getSearchResultsKey(query, sessionId);
    await _cacheManager.storeWithTTL(key, results, searchResultsTTL);
  }

  /// Retrieves cached search results if available and not expired.
  ///
  /// The [query] parameter is the search query.
  /// Returns the cached results or `null` if expired or not found.
  /// Only returns results for the current session.
  Future<List<Map<String, dynamic>>?> getCachedSearchResults(
      String query) async {
    final sessionId = await _sessionStorage.getSessionId();
    final key = _getSearchResultsKey(query, sessionId);
    final cachedData = await _cacheManager.getIfNotExpired(key);

    if (cachedData is List) {
      return cachedData.cast<Map<String, dynamic>>();
    }

    return null;
  }

  /// Caches topic details with TTL.
  ///
  /// The [topicId] parameter is the topic identifier.
  /// The [audiobook] parameter is the audiobook details map to cache.
  Future<void> cacheTopicDetails(
      String topicId, Map<String, dynamic> audiobook) async {
    final key = _getTopicDetailsKey(topicId);
    await _cacheManager.storeWithTTL(key, audiobook, topicDetailsTTL);
  }

  /// Retrieves cached topic details if available and not expired.
  ///
  /// The [topicId] parameter is the topic identifier.
  /// Returns the cached audiobook map or `null` if expired or not found.
  Future<Map<String, dynamic>?> getCachedTopicDetails(String topicId) async {
    final key = _getTopicDetailsKey(topicId);
    final cachedData = await _cacheManager.getIfNotExpired(key);

    if (cachedData is Map<String, dynamic>) {
      return cachedData;
    }

    return null;
  }

  /// Clears all cached search results.
  Future<void> clearSearchResultsCache() async {
    // This would need a more sophisticated approach to clear only search results
    // For now, we'll clear the entire cache
    await _cacheManager.clearAll();
  }

  /// Clears cached topic details for a specific topic.
  Future<void> clearTopicDetailsCache(String topicId) async {
    final key = _getTopicDetailsKey(topicId);
    await _cacheManager.remove(key);
  }

  /// Clears all cached topic details.
  Future<void> clearAllTopicDetailsCache() async {
    await _cacheManager.clearByPrefix('topic:');
  }

  /// Checks if search results are cached for a query.
  Future<bool> hasCachedSearchResults(String query) async {
    final sessionId = await _sessionStorage.getSessionId();
    final key = _getSearchResultsKey(query, sessionId);
    return _cacheManager.exists(key);
  }

  /// Checks if topic details are cached for a topic.
  Future<bool> hasCachedTopicDetails(String topicId) {
    final key = _getTopicDetailsKey(topicId);
    return _cacheManager.exists(key);
  }

  /// Gets the expiration time for cached search results.
  Future<DateTime?> getSearchResultsExpiration(String query) async {
    final sessionId = await _sessionStorage.getSessionId();
    final key = _getSearchResultsKey(query, sessionId);
    return _cacheManager.getExpirationTime(key);
  }

  /// Gets the expiration time for cached topic details.
  Future<DateTime?> getTopicDetailsExpiration(String topicId) {
    final key = _getTopicDetailsKey(topicId);
    return _cacheManager.getExpirationTime(key);
  }

  /// Generates cache key for search results with session binding.
  ///
  /// The [query] parameter is the search query.
  /// The [sessionId] parameter is the current session ID (optional).
  /// If sessionId is null, uses 'no_session' as fallback.
  String _getSearchResultsKey(String query, String? sessionId) {
    final session = sessionId ?? 'no_session';
    return 'search:$session:${query.toLowerCase().trim()}';
  }

  /// Clears all cached search results for the current session.
  ///
  /// This is useful when session changes or user logs out.
  Future<void> clearCurrentSessionCache() async {
    final sessionId = await _sessionStorage.getSessionId();
    if (sessionId != null) {
      await _cacheManager.clearByPrefix('search:$sessionId:');
      await _cacheManager.clearByPrefix('topic:$sessionId:');
    }
  }

  /// Generates cache key for topic details.
  String _getTopicDetailsKey(String topicId) => 'topic:$topicId';

  /// Clears all expired cache entries.
  Future<void> clearExpired() async {
    await _cacheManager.clearExpired();
  }

  /// Gets cache statistics including total entries and memory usage.
  Future<Map<String, dynamic>> getStatistics() => _cacheManager.getStatistics();
}
