import 'package:dio/dio.dart';
import 'package:sembast/sembast.dart';

import '../errors/failures.dart';
import '../net/dio_client.dart';

class EndpointManager {
  final Database _db;
  final StoreRef<String, Map<String, dynamic>> _store = StoreRef.main();

  EndpointManager._(this._db);

  factory EndpointManager(Database db) => EndpointManager._(db);

  // Database structure: { url, priority, rtt, last_ok, signature_ok, enabled }
  static const String _storeKey = 'endpoints';

  Future<void> initializeDefaultEndpoints() async {
    final defaultEndpoints = [
      {'url': 'https://rutracker.me', 'priority': 1, 'enabled': true},
      {'url': 'https://rutracker.org', 'priority': 2, 'enabled': true},
      {'url': 'https://rutracker.net', 'priority': 3, 'enabled': true},
      {'url': 'https://rutracker.nl', 'priority': 4, 'enabled': true},
    ];

    final record = await _store.record(_storeKey).get(_db);
    if (record == null) {
      await _store.record(_storeKey).put(_db, {'endpoints': defaultEndpoints});
    }
  }

  Future<void> healthCheck(String endpoint) async {
    try {
      final startTime = DateTime.now();
      final response = await DioClient.instance.head(
        endpoint,
        options: Options(
          receiveTimeout: const Duration(seconds: 10),
          validateStatus: (status) => status! < 500,
        ),
      );
      final endTime = DateTime.now();
      final rtt = endTime.difference(startTime).inMilliseconds;

      final record = await _store.record(_storeKey).get(_db);
      final endpoints = List<Map<String, dynamic>>.from((record?['endpoints'] as List?) ?? []);

      final endpointIndex = endpoints.indexWhere((e) => e['url'] == endpoint);
      if (endpointIndex != -1) {
        endpoints[endpointIndex].addAll({
          'rtt': rtt,
          'last_ok': DateTime.now().toIso8601String(),
          'signature_ok': _validateSignature(response.headers),
          'enabled': true,
        });
        await _store.record(_storeKey).put(_db, {'endpoints': endpoints});
      }
    } catch (e) {
      // Mark endpoint as unhealthy
      final record = await _store.record(_storeKey).get(_db);
      final endpoints = List<Map<String, dynamic>>.from((record?['endpoints'] as List?) ?? []);

      final endpointIndex = endpoints.indexWhere((e) => e['url'] == endpoint);
      if (endpointIndex != -1) {
        endpoints[endpointIndex]['enabled'] = false;
        await _store.record(_storeKey).put(_db, {'endpoints': endpoints});
      }
    }
  }

  bool _validateSignature(Headers? headers) {
    // TODO: Implement signature validation based on RuTracker response
    return true;
  }

  Future<void> _updateEndpoints(List<Map<String, dynamic>> endpoints) async {
    await _store.record(_storeKey).put(_db, {'endpoints': endpoints});
  }

  Future<String> getActiveEndpoint() async {
    final record = await _store.record(_storeKey).get(_db);
    final endpoints = List<Map<String, dynamic>>.from((record?['endpoints'] as List?) ?? []);

    // Filter enabled endpoints and sort by priority
    final enabledEndpoints = endpoints
        .where((e) => e['enabled'] == true)
        .toList()
      ..sort((a, b) => a['priority'].compareTo(b['priority']));

    if (enabledEndpoints.isEmpty) {
      throw const NetworkFailure('No healthy endpoints available');
    }

    // Return the highest priority healthy endpoint
    return enabledEndpoints.first['url'];
  }

  Future<List<Map<String, dynamic>>> getAllEndpoints() async {
    final record = await _store.record(_storeKey).get(_db);
    return List<Map<String, dynamic>>.from((record?['endpoints'] as List?) ?? []);
  }

  Future<void> addEndpoint(String url, int priority) async {
    final record = await _store.record(_storeKey).get(_db);
    final endpoints = List<Map<String, dynamic>>.from((record?['endpoints'] as List?) ?? []);

    endpoints.add({
      'url': url,
      'priority': priority,
      'rtt': 0,
      'last_ok': null,
      'signature_ok': false,
      'enabled': true,
    });

    await _updateEndpoints(endpoints);
  }

  Future<void> removeEndpoint(String url) async {
    final record = await _store.record(_storeKey).get(_db);
    final endpoints = List<Map<String, dynamic>>.from((record?['endpoints'] as List?) ?? []);

    endpoints.removeWhere((e) => e['url'] == url);
    await _updateEndpoints(endpoints);
  }
}