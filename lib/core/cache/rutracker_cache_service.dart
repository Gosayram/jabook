import 'package:jabook/core/cache/cache_manager.dart';
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

  /// TTL for search results in seconds (1 hour).
  static const int searchResultsTTL = 3600;

  /// TTL for topic details in seconds (24 hours).
  static const int topicDetailsTTL = 86400;

  /// Initializes the cache service with database connection.
  Future<void> initialize(Database db) async {
    await _cacheManager.initialize(db);
  }

  /// Caches search results with TTL.
  ///
  /// The [query] parameter is the search query.
  /// The [results] parameter is the list of audiobook maps to cache.
  Future<void> cacheSearchResults(
      String query, List<Map<String, dynamic>> results) async {
    final key = _getSearchResultsKey(query);
    await _cacheManager.storeWithTTL(key, results, searchResultsTTL);
  }

  /// Retrieves cached search results if available and not expired.
  ///
  /// The [query] parameter is the search query.
  /// Returns the cached results or `null` if expired or not found.
  Future<List<Map<String, dynamic>>?> getCachedSearchResults(
      String query) async {
    final key = _getSearchResultsKey(query);
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
  Future<bool> hasCachedSearchResults(String query) {
    final key = _getSearchResultsKey(query);
    return _cacheManager.exists(key);
  }

  /// Checks if topic details are cached for a topic.
  Future<bool> hasCachedTopicDetails(String topicId) {
    final key = _getTopicDetailsKey(topicId);
    return _cacheManager.exists(key);
  }

  /// Gets the expiration time for cached search results.
  Future<DateTime?> getSearchResultsExpiration(String query) {
    final key = _getSearchResultsKey(query);
    return _cacheManager.getExpirationTime(key);
  }

  /// Gets the expiration time for cached topic details.
  Future<DateTime?> getTopicDetailsExpiration(String topicId) {
    final key = _getTopicDetailsKey(topicId);
    return _cacheManager.getExpirationTime(key);
  }

  /// Generates cache key for search results.
  String _getSearchResultsKey(String query) =>
      'search:${query.toLowerCase().trim()}';

  /// Generates cache key for topic details.
  String _getTopicDetailsKey(String topicId) => 'topic:$topicId';

  /// Clears all expired cache entries.
  Future<void> clearExpired() async {
    await _cacheManager.clearExpired();
  }

  /// Gets cache statistics including total entries and memory usage.
  Future<Map<String, dynamic>> getStatistics() => _cacheManager.getStatistics();
}
