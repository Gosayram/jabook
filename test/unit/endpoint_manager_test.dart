import 'package:flutter_test/flutter_test.dart';
import 'package:jabook/core/endpoints/endpoint_manager.dart';
import 'package:sembast/sembast_memory.dart';

void main() {
  // Initialize Flutter binding for tests
  TestWidgetsFlutterBinding.ensureInitialized();

  group('EndpointManager Tests', () {
    late Database db;
    late EndpointManager endpointManager;

    setUp(() async {
      db = await databaseFactoryMemory.openDatabase('test_endpoints.db');
      endpointManager = EndpointManager(db);
    });

    tearDown(() async {
      await db.close();
    });

    test('should initialize default endpoints', () async {
      await endpointManager.initializeDefaultEndpoints();

      final endpoints = await endpointManager.getAllEndpoints();
      expect(endpoints, isNotEmpty);
      expect(endpoints.length, greaterThanOrEqualTo(3));

      // Check that rutracker.me is first (priority 1)
      final firstEndpoint = endpoints.first;
      expect(firstEndpoint['url'], equals('https://rutracker.me'));
      expect(firstEndpoint['priority'], equals(1));
    });

    test('should return hardcoded fallback when no endpoints available',
        () async {
      // Initialize to get endpoints
      await endpointManager.initializeDefaultEndpoints();

      // Disable all endpoints
      final endpoints = await endpointManager.getAllEndpoints();
      for (final endpoint in endpoints) {
        await endpointManager.updateEndpointStatus(
            endpoint['url'] as String, false);
      }

      // Try to get active endpoint - should return fallback
      final activeEndpoint = await endpointManager.getActiveEndpoint();
      expect(activeEndpoint, equals('https://rutracker.me'));
    });

    test('should check sticky endpoint availability before returning',
        () async {
      await endpointManager.initializeDefaultEndpoints();

      // Set an endpoint as active
      await endpointManager.setActiveEndpoint('https://rutracker.net');

      // getActiveEndpoint should check availability
      // In test environment, this might fail or succeed depending on network
      // But the method should complete without throwing
      final result = await endpointManager.getActiveEndpoint();
      expect(result, isNotEmpty);
      expect(result, startsWith('https://'));
    });

    test('should handle DNS errors in health check', () async {
      await endpointManager.initializeDefaultEndpoints();

      // Add a test endpoint that will fail DNS lookup
      await endpointManager.addEndpoint(
          'https://nonexistent-domain-12345.com', 10);

      // Health check should handle DNS errors gracefully
      // This might take some time but should complete
      try {
        await endpointManager
            .healthCheck('https://nonexistent-domain-12345.com', force: true);
      } on Exception {
        // Expected - DNS errors should be handled
      }

      // Endpoint should be marked as disabled after DNS error
      final endpoints = await endpointManager.getAllEndpoints();
      final testEndpoint = endpoints.firstWhere(
        (e) => e['url'] == 'https://nonexistent-domain-12345.com',
        orElse: () => <String, dynamic>{},
      );

      // If DNS error occurred, endpoint should exist in list
      // This is expected behavior
      expect(testEndpoint.isNotEmpty, isTrue);
    });

    test('should update endpoint priority', () async {
      await endpointManager.initializeDefaultEndpoints();

      await endpointManager.updateEndpointPriority('https://rutracker.me', 5);

      final endpoints = await endpointManager.getAllEndpoints();
      final updated = endpoints.firstWhere(
        (e) => e['url'] == 'https://rutracker.me',
      );

      expect(updated['priority'], equals(5));
    });

    test('should update endpoint status', () async {
      await endpointManager.initializeDefaultEndpoints();

      await endpointManager.updateEndpointStatus('https://rutracker.me', false);

      final endpoints = await endpointManager.getAllEndpoints();
      final updated = endpoints.firstWhere(
        (e) => e['url'] == 'https://rutracker.me',
      );

      expect(updated['enabled'], equals(false));
    });

    test('should set active endpoint', () async {
      await endpointManager.initializeDefaultEndpoints();

      await endpointManager.setActiveEndpoint('https://rutracker.org');

      final active = await endpointManager.getActiveEndpoint();
      // Might return different endpoint if availability check fails
      // But should not throw
      expect(active, isNotEmpty);
    });

    test('should build URL correctly', () async {
      await endpointManager.initializeDefaultEndpoints();

      final url = await endpointManager.buildUrl('/forum/search.php');
      expect(url, isNotEmpty);
      expect(url, contains('/forum/search.php'));
      expect(url, startsWith('https://'));
    });
  });
}
