import 'dart:async';

import 'package:sembast/sembast.dart';

/// Manages caching with TTL (Time To Live) for various types of data.
///
/// This class provides methods to store and retrieve cached data with
/// automatic expiration based on TTL values.
class CacheManager {
  /// Private constructor for singleton pattern.
  CacheManager._();

  /// Factory constructor to get the singleton instance.
  factory CacheManager() => _instance;

  /// Singleton instance of the CacheManager.
  static final CacheManager _instance = CacheManager._();

  /// Database instance for cache operations.
  Database? _db;

  /// Initializes the cache manager with database connection.
  Future<void> initialize(Database db) async {
    _db = db;
  }

  /// Stores data in cache with TTL expiration.
  ///
  /// The [key] parameter is the unique identifier for the cached data.
  /// The [data] parameter is the data to be cached (must be JSON-serializable).
  /// The [ttlSeconds] parameter is the time-to-live in seconds.
  Future<void> storeWithTTL(String key, dynamic data, int ttlSeconds) async {
    if (_db == null) {
      throw StateError('CacheManager not initialized');
    }

    final store = StoreRef<String, Map<String, dynamic>>('cache');
    final expirationTime = DateTime.now().add(Duration(seconds: ttlSeconds));

    await store.record(key).put(_db!, {
      'data': data,
      'expires_at': expirationTime.toIso8601String(),
    });
  }

  /// Retrieves cached data if it hasn't expired.
  ///
  /// The [key] parameter is the unique identifier for the cached data.
  /// Returns the cached data or `null` if expired or not found.
  Future<dynamic> getIfNotExpired(String key) async {
    if (_db == null) {
      throw StateError('CacheManager not initialized');
    }

    final store = StoreRef<String, Map<String, dynamic>>('cache');
    final record = await store.record(key).get(_db!);

    if (record == null) {
      return null;
    }

    final expiresAt = DateTime.parse(record['expires_at'] as String);
    if (DateTime.now().isAfter(expiresAt)) {
      // Data expired, remove it from cache
      await store.record(key).delete(_db!);
      return null;
    }

    return record['data'];
  }

  /// Clears expired cache entries.
  Future<void> clearExpired() async {
    if (_db == null) {
      throw StateError('CacheManager not initialized');
    }

    final store = StoreRef<String, Map<String, dynamic>>('cache');
    final records = await store.find(_db!);

    for (final record in records) {
      final expiresAt = DateTime.parse(record.value['expires_at'] as String);
      if (DateTime.now().isAfter(expiresAt)) {
        await store.record(record.key).delete(_db!);
      }
    }
  }

  /// Clears all cache entries.
  Future<void> clearAll() async {
    if (_db == null) {
      throw StateError('CacheManager not initialized');
    }

    final store = StoreRef<String, Map<String, dynamic>>('cache');
    await store.delete(_db!);
  }

  /// Removes a specific cache entry.
  Future<void> remove(String key) async {
    if (_db == null) {
      throw StateError('CacheManager not initialized');
    }

    final store = StoreRef<String, Map<String, dynamic>>('cache');
    await store.record(key).delete(_db!);
  }

  /// Gets the expiration time for a cached item.
  Future<DateTime?> getExpirationTime(String key) async {
    if (_db == null) {
      throw StateError('CacheManager not initialized');
    }

    final store = StoreRef<String, Map<String, dynamic>>('cache');
    final record = await store.record(key).get(_db!);

    if (record == null) {
      return null;
    }

    return DateTime.parse(record['expires_at'] as String);
  }

  /// Checks if a cached item exists and is not expired.
  Future<bool> exists(String key) async {
    if (_db == null) {
      throw StateError('CacheManager not initialized');
    }

    final store = StoreRef<String, Map<String, dynamic>>('cache');
    final record = await store.record(key).get(_db!);

    if (record == null) {
      return false;
    }

    final expiresAt = DateTime.parse(record['expires_at'] as String);
    return !DateTime.now().isAfter(expiresAt);
  }
}