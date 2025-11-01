import 'package:flutter_test/flutter_test.dart';
import 'package:jabook/core/cache/rutracker_cache_service.dart';
import 'package:sembast/sembast_memory.dart';

void main() {
  group('RuTrackerCacheService Tests', () {
    late RuTrackerCacheService cacheService;
    late Database db;

    setUp(() async {
      cacheService = RuTrackerCacheService();

      // Create in-memory database for testing
      db = await databaseFactoryMemory.openDatabase('test_rutracker_cache.db');

      await cacheService.initialize(db);
    });

    tearDown(() async {
      await cacheService.clearExpired();
      await db.close();
    });

    test('should store and retrieve search results as Map', () async {
      const query = 'test query';
      final results = [
        {
          'id': '1',
          'title': 'Test Book 1',
          'author': 'Author 1',
          'category': 'Test',
          'size': '100 MB',
          'seeders': 5,
          'leechers': 2,
          'magnetUrl': 'magnet:test1',
          'chapters': [],
          'addedDate': DateTime.now().toIso8601String(),
        },
        {
          'id': '2',
          'title': 'Test Book 2',
          'author': 'Author 2',
          'category': 'Test',
          'size': '200 MB',
          'seeders': 10,
          'leechers': 3,
          'magnetUrl': 'magnet:test2',
          'chapters': [],
          'addedDate': DateTime.now().toIso8601String(),
        },
      ];

      await cacheService.cacheSearchResults(query, results);
      final cachedResults = await cacheService.getCachedSearchResults(query);

      expect(cachedResults, equals(results));
    });

    test('should return null for non-existent search query', () async {
      final results =
          await cacheService.getCachedSearchResults('non_existent_query');
      expect(results, isNull);
    });

    test('should store and retrieve topic details as Map', () async {
      const topicId = '12345';
      final details = {
        'id': topicId,
        'title': 'Test Topic',
        'author': 'Test Author',
        'category': 'Test',
        'size': '150 MB',
        'seeders': 8,
        'leechers': 4,
        'magnetUrl': 'magnet:test12345',
        'coverUrl': 'https://example.com/cover.jpg',
        'chapters': [],
        'addedDate': DateTime.now().toIso8601String(),
      };

      await cacheService.cacheTopicDetails(topicId, details);
      final cachedDetails = await cacheService.getCachedTopicDetails(topicId);

      expect(cachedDetails, equals(details));
    });

    test('should return null for non-existent topic', () async {
      final details =
          await cacheService.getCachedTopicDetails('non_existent_topic');
      expect(details, isNull);
    });

    test('should clear search results cache', () async {
      const query = 'test clear';
      final results = [
        {
          'id': '1',
          'title': 'Test Book',
          'author': 'Test Author',
          'category': 'Test',
          'size': '100 MB',
          'seeders': 5,
          'leechers': 2,
          'magnetUrl': 'magnet:test',
          'chapters': [],
          'addedDate': DateTime.now().toIso8601String(),
        },
      ];

      await cacheService.cacheSearchResults(query, results);
      await cacheService.clearSearchResultsCache();

      final result = await cacheService.getCachedSearchResults(query);
      expect(result, isNull);
    });

    test('should clear topic details cache', () async {
      const topicId = 'test_clear_topic';
      final details = {
        'id': topicId,
        'title': 'Test Topic',
        'author': 'Test Author',
        'category': 'Test',
        'size': '100 MB',
        'seeders': 5,
        'leechers': 2,
        'magnetUrl': 'magnet:test',
        'chapters': [],
        'addedDate': DateTime.now().toIso8601String(),
      };

      await cacheService.cacheTopicDetails(topicId, details);
      await cacheService.clearTopicDetailsCache(topicId);

      final result = await cacheService.getCachedTopicDetails(topicId);
      expect(result, isNull);
    });

    test('should check if search results exist', () async {
      const query = 'test_exists';
      final results = [
        {
          'id': '1',
          'title': 'Test Book',
          'author': 'Test Author',
          'category': 'Test',
          'size': '100 MB',
          'seeders': 5,
          'leechers': 2,
          'magnetUrl': 'magnet:test',
          'chapters': [],
          'addedDate': DateTime.now().toIso8601String(),
        },
      ];

      await cacheService.cacheSearchResults(query, results);
      final exists = await cacheService.hasCachedSearchResults(query);

      expect(exists, isTrue);
    });

    test('should check if topic details exist', () async {
      const topicId = 'test_exists_topic';
      final details = {
        'id': topicId,
        'title': 'Test Topic',
        'author': 'Test Author',
        'category': 'Test',
        'size': '100 MB',
        'seeders': 5,
        'leechers': 2,
        'magnetUrl': 'magnet:test',
        'chapters': [],
        'addedDate': DateTime.now().toIso8601String(),
      };

      await cacheService.cacheTopicDetails(topicId, details);
      final exists = await cacheService.hasCachedTopicDetails(topicId);

      expect(exists, isTrue);
    });

    test('should handle different search queries independently', () async {
      const query1 = 'query 1';
      const query2 = 'query 2';
      final results1 = [
        {
          'id': '1',
          'title': 'Book 1',
          'author': 'Author 1',
          'category': 'Test',
          'size': '100 MB',
          'seeders': 5,
          'leechers': 2,
          'magnetUrl': 'magnet:test1',
          'chapters': [],
          'addedDate': DateTime.now().toIso8601String(),
        },
      ];
      final results2 = [
        {
          'id': '2',
          'title': 'Book 2',
          'author': 'Author 2',
          'category': 'Test',
          'size': '200 MB',
          'seeders': 10,
          'leechers': 3,
          'magnetUrl': 'magnet:test2',
          'chapters': [],
          'addedDate': DateTime.now().toIso8601String(),
        },
      ];

      await cacheService.cacheSearchResults(query1, results1);
      await cacheService.cacheSearchResults(query2, results2);

      final result1 = await cacheService.getCachedSearchResults(query1);
      final result2 = await cacheService.getCachedSearchResults(query2);

      expect(result1, equals(results1));
      expect(result2, equals(results2));
    });

    test('should handle different topics independently', () async {
      const topicId1 = 'topic1';
      const topicId2 = 'topic2';
      final details1 = {
        'id': topicId1,
        'title': 'Topic 1',
        'author': 'Author 1',
        'category': 'Test',
        'size': '100 MB',
        'seeders': 5,
        'leechers': 2,
        'magnetUrl': 'magnet:test1',
        'chapters': [],
        'addedDate': DateTime.now().toIso8601String(),
      };
      final details2 = {
        'id': topicId2,
        'title': 'Topic 2',
        'author': 'Author 2',
        'category': 'Test',
        'size': '200 MB',
        'seeders': 10,
        'leechers': 3,
        'magnetUrl': 'magnet:test2',
        'chapters': [],
        'addedDate': DateTime.now().toIso8601String(),
      };

      await cacheService.cacheTopicDetails(topicId1, details1);
      await cacheService.cacheTopicDetails(topicId2, details2);

      final result1 = await cacheService.getCachedTopicDetails(topicId1);
      final result2 = await cacheService.getCachedTopicDetails(topicId2);

      expect(result1, equals(details1));
      expect(result2, equals(details2));
    });
  });
}
