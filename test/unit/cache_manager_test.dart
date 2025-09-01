import 'package:flutter_test/flutter_test.dart';
import 'package:sembast/sembast.dart';
import 'package:sembast/sembast_memory.dart';

import 'package:jabook/core/cache/cache_manager.dart';

void main() {
  group('CacheManager Tests', () {
    late CacheManager cacheManager;
    late Database db;

    setUp(() async {
      cacheManager = CacheManager();
      
      // Create in-memory database for testing
      db = await databaseFactoryMemory.openDatabase('test_cache.db');
      
      await cacheManager.initialize(db);
    });

    tearDown(() async {
      await cacheManager.clearAll();
      await db.close();
    });

    test('should store and retrieve value with TTL', () async {
      const key = 'test_key';
      const value = 'test_value';

      await cacheManager.storeWithTTL(key, value, 60);
      final result = await cacheManager.getIfNotExpired(key);

      expect(result, equals(value));
    });

    test('should return null for non-existent key', () async {
      final result = await cacheManager.getIfNotExpired('non_existent_key');
      expect(result, isNull);
    });

    test('should store and retrieve complex data', () async {
      const key = 'test_map_key';
      final value = {'name': 'test', 'value': 42, 'list': [1, 2, 3]};

      await cacheManager.storeWithTTL(key, value, 60);
      final result = await cacheManager.getIfNotExpired(key);

      expect(result, equals(value));
    });

    test('should remove value by key', () async {
      const key = 'test_remove_key';
      const value = 'test_value';

      await cacheManager.storeWithTTL(key, value, 60);
      await cacheManager.remove(key);
      final result = await cacheManager.getIfNotExpired(key);

      expect(result, isNull);
    });

    test('should clear all cache', () async {
      const key1 = 'key1';
      const key2 = 'key2';
      const value = 'test_value';

      await cacheManager.storeWithTTL(key1, value, 60);
      await cacheManager.storeWithTTL(key2, value, 60);
      await cacheManager.clearAll();

      final result1 = await cacheManager.getIfNotExpired(key1);
      final result2 = await cacheManager.getIfNotExpired(key2);

      expect(result1, isNull);
      expect(result2, isNull);
    });

    test('should check if key exists', () async {
      const key = 'test_exists_key';
      const value = 'test_value';

      await cacheManager.storeWithTTL(key, value, 60);
      final exists = await cacheManager.exists(key);

      expect(exists, isTrue);
    });

    test('should return false for non-existent key', () async {
      final exists = await cacheManager.exists('non_existent_key');
      expect(exists, isFalse);
    });

    test('should handle TTL expiration', () async {
      const key = 'test_ttl_key';
      const value = 'test_value';

      await cacheManager.storeWithTTL(key, value, 1); // 1 second TTL
      
      // Wait for TTL to expire
      await Future.delayed(const Duration(seconds: 2));
      
      final result = await cacheManager.getIfNotExpired(key);
      expect(result, isNull);
    });

    test('should not expire before TTL', () async {
      const key = 'test_ttl_key';
      const value = 'test_value';

      await cacheManager.storeWithTTL(key, value, 5); // 5 seconds TTL
      
      // Wait less than TTL
      await Future.delayed(const Duration(seconds: 2));
      
      final result = await cacheManager.getIfNotExpired(key);
      expect(result, equals(value));
    });

    test('should get expiration time', () async {
      const key = 'test_expiration_key';
      const value = 'test_value';
      const ttlSeconds = 60;

      await cacheManager.storeWithTTL(key, value, ttlSeconds);
      final expirationTime = await cacheManager.getExpirationTime(key);

      expect(expirationTime, isNotNull);
      expect(expirationTime!.isAfter(DateTime.now()), isTrue);
    });

    test('should clear expired entries', () async {
      const key1 = 'expired_key';
      const key2 = 'valid_key';
      const value = 'test_value';

      // Store one expired and one valid entry
      await cacheManager.storeWithTTL(key1, value, 1); // Expires quickly
      await cacheManager.storeWithTTL(key2, value, 60); // Valid for 60 seconds
      
      // Wait for first key to expire
      await Future.delayed(const Duration(seconds: 2));
      
      await cacheManager.clearExpired();

      final result1 = await cacheManager.getIfNotExpired(key1);
      final result2 = await cacheManager.getIfNotExpired(key2);

      expect(result1, isNull);
      expect(result2, equals(value));
    });
  });
}