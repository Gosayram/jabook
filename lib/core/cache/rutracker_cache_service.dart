// Copyright 2025 Jabook Contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

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

  /// TTL for torrent chapters cache in seconds (7 days).
  /// Torrent structure doesn't change, so we can cache for longer.
  static const int torrentChaptersTTL = 604800;

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

  /// Clears cached search results for a specific query.
  ///
  /// The [query] parameter is the search query to clear from cache.
  Future<void> clearSearchResultsCacheForQuery(String query) async {
    final sessionId = await _sessionStorage.getSessionId();
    final key = _getSearchResultsKey(query, sessionId);
    await _cacheManager.remove(key);
  }

  /// Clears all cached search results.
  ///
  /// This method clears search results cache for the current session
  /// and also clears cache for all sessions as a fallback.
  Future<void> clearSearchResultsCache() async {
    final sessionId = await _sessionStorage.getSessionId();
    if (sessionId != null) {
      // Clear cache for current session
      await _cacheManager.clearByPrefix('search:$sessionId:');
    }
    // Also clear cache for all sessions (fallback)
    await _cacheManager.clearByPrefix('search:');
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

  /// Caches chapters extracted from a torrent file.
  ///
  /// The [infoHash] parameter is the info hash of the torrent (used as cache key).
  /// The [chapters] parameter is the list of chapters to cache.
  Future<void> cacheTorrentChapters(
      String infoHash, List<Map<String, dynamic>> chapters) async {
    final key = _getTorrentChaptersKey(infoHash);
    await _cacheManager.storeWithTTL(key, chapters, torrentChaptersTTL);
  }

  /// Retrieves cached chapters for a torrent if available and not expired.
  ///
  /// The [infoHash] parameter is the info hash of the torrent.
  /// Returns the cached chapters or `null` if expired or not found.
  Future<List<Map<String, dynamic>>?> getCachedTorrentChapters(
      String infoHash) async {
    final key = _getTorrentChaptersKey(infoHash);
    final cachedData = await _cacheManager.getIfNotExpired(key);

    if (cachedData is List) {
      return cachedData.cast<Map<String, dynamic>>();
    }

    return null;
  }

  /// Generates cache key for torrent chapters.
  String _getTorrentChaptersKey(String infoHash) =>
      'torrent_chapters:$infoHash';

  /// Clears cached chapters for a specific torrent.
  ///
  /// The [infoHash] parameter is the info hash of the torrent to clear from cache.
  Future<void> clearTorrentChaptersCache(String infoHash) async {
    final key = _getTorrentChaptersKey(infoHash);
    await _cacheManager.remove(key);
  }

  /// Clears all cached torrent chapters.
  ///
  /// This removes all cached chapter data from torrent files.
  Future<void> clearAllTorrentChaptersCache() async {
    await _cacheManager.clearByPrefix('torrent_chapters:');
  }

  /// Gets cache statistics including total entries and memory usage.
  Future<Map<String, dynamic>> getStatistics() => _cacheManager.getStatistics();
}
