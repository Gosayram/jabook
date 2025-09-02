import 'package:dio/dio.dart';
import 'package:jabook/core/errors/failures.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:sembast/sembast.dart';

/// Manages RuTracker endpoint configuration and health monitoring.
///
/// This class handles the storage, retrieval, and health checking of
/// RuTracker mirror endpoints, ensuring optimal performance and availability.
class EndpointManager {
  /// Private constructor for singleton pattern.
  EndpointManager._(this._db);

  /// Factory constructor to create a new instance of EndpointManager.
  ///
  /// The [db] parameter is the Sembast database instance for storing endpoint data.
  factory EndpointManager(Database db) => EndpointManager._(db);

  /// Database instance for persistent storage.
  final Database _db;

  /// Store reference for endpoint data storage.
  final StoreRef<String, Map<String, dynamic>> _store = StoreRef.main();

  // Database structure: { url, priority, rtt, last_ok, signature_ok, enabled }
  static const String _storeKey = 'endpoints';

  /// Convenience getter to avoid receiver duplication warnings.
  RecordRef<String, Map<String, dynamic>> get _endpointsRef => _store.record(_storeKey);

  /// Initializes the default RuTracker endpoints if none exist.
  Future<void> initializeDefaultEndpoints() async {
    final defaultEndpoints = [
      {'url': 'https://rutracker.org', 'priority': 1, 'enabled': true},
      {'url': 'https://rutracker.net', 'priority': 2, 'enabled': true},
      {'url': 'https://rutracker.nl', 'priority': 3, 'enabled': true},
      {'url': 'https://rutracker.me', 'priority': 4, 'enabled': true},
    ];

    final record = await _endpointsRef.get(_db);
    if (record == null) {
      await _endpointsRef.put(_db, {'endpoints': defaultEndpoints});
    }
  }

  /// Performs a health check on the specified endpoint.
  Future<void> healthCheck(String endpoint) async {
    try {
      final startTime = DateTime.now();
      final response = await (await DioClient.instance).head(
        endpoint,
        options: Options(
          receiveTimeout: const Duration(seconds: 10),
          validateStatus: (status) => status != null && status < 500,
        ),
      );
      final rtt = DateTime.now().difference(startTime).inMilliseconds;

      final record = await _endpointsRef.get(_db);
      final endpoints =
          List<Map<String, dynamic>>.from((record?['endpoints'] as List?) ?? []);

      final endpointIndex = endpoints.indexWhere((e) => e['url'] == endpoint);
      if (endpointIndex != -1) {
        endpoints[endpointIndex].addAll({
          'rtt': rtt,
          'last_ok': DateTime.now().toIso8601String(),
          'signature_ok': _validateSignature(response.headers),
          'enabled': true,
        });
        await _endpointsRef.put(_db, {'endpoints': endpoints});
      }
    } on Exception {
      // Mark endpoint as unhealthy on error
      final record = await _endpointsRef.get(_db);
      final endpoints =
          List<Map<String, dynamic>>.from((record?['endpoints'] as List?) ?? []);

      final endpointIndex = endpoints.indexWhere((e) => e['url'] == endpoint);
      if (endpointIndex != -1) {
        endpoints[endpointIndex]['enabled'] = false;
        await _endpointsRef.put(_db, {'endpoints': endpoints});
      }
    }
  }

  /// Validates the response signature from RuTracker endpoint.
  ///
  /// Placeholder: returns true by default.
  bool _validateSignature(Headers? headers) => true;

  /// Persists the updated endpoints into the database.
  Future<void> _updateEndpoints(List<Map<String, dynamic>> endpoints) async {
    await _endpointsRef.put(_db, {'endpoints': endpoints});
  }

  /// Gets the currently active (highest priority healthy) endpoint.
  ///
  /// Throws [NetworkFailure] if no healthy endpoints are available.
  Future<String> getActiveEndpoint() async {
    final record = await _endpointsRef.get(_db);
    final endpoints =
        List<Map<String, dynamic>>.from((record?['endpoints'] as List?) ?? []);

    // Filter enabled endpoints and sort by priority
    final enabledEndpoints = endpoints
        .where((e) => e['enabled'] == true)
        .toList()
      ..sort((a, b) => a['priority'].compareTo(b['priority']));

    if (enabledEndpoints.isEmpty) {
      throw const NetworkFailure('No healthy endpoints available');
    }

    return enabledEndpoints.first['url'] as String;
  }

  /// Gets all configured endpoints from the database.
  Future<List<Map<String, dynamic>>> getAllEndpoints() async {
    final record = await _endpointsRef.get(_db);
    return List<Map<String, dynamic>>.from((record?['endpoints'] as List?) ?? []);
  }

  /// Adds a new endpoint to the configuration.
  Future<void> addEndpoint(String url, int priority) async {
    final record = await _endpointsRef.get(_db);
    final endpoints =
        List<Map<String, dynamic>>.from((record?['endpoints'] as List?) ?? []);

    await _updateEndpoints(
      endpoints
        ..add({
          'url': url,
          'priority': priority,
          'rtt': 0,
          'last_ok': null,
          'signature_ok': false,
          'enabled': true,
        }),
    );
  }

  /// Removes an endpoint from the configuration.
  Future<void> removeEndpoint(String url) async {
    final record = await _endpointsRef.get(_db);
    final endpoints =
        List<Map<String, dynamic>>.from((record?['endpoints'] as List?) ?? []);

    await _updateEndpoints(
      endpoints..removeWhere((e) => e['url'] == url),
    );
  }
}