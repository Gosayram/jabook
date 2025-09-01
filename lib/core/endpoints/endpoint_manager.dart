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

  /// Initializes the default RuTracker endpoints if none exist.
  ///
  /// This method sets up the default list of RuTracker mirror endpoints
  /// with predefined priorities and enables them for use.
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

  /// Performs a health check on the specified endpoint.
  ///
  /// This method sends a HEAD request to the endpoint to check its availability,
  /// measures response time, and updates the endpoint's health status in the database.
  ///
  /// The [endpoint] parameter is the URL of the endpoint to check.
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
    } on Exception {
      // Mark endpoint as unhealthy on error
      final record = await _store.record(_storeKey).get(_db);
      final endpoints = List<Map<String, dynamic>>.from((record?['endpoints'] as List?) ?? []);

      final endpointIndex = endpoints.indexWhere((e) => e['url'] == endpoint);
      if (endpointIndex != -1) {
        endpoints[endpointIndex]['enabled'] = false;
        await _store.record(_storeKey).put(_db, {'endpoints': endpoints});
      }
    }
  }

  /// Validates the response signature from RuTracker endpoint.
  ///
  /// This method should be implemented to verify the authenticity of
  /// responses from RuTracker endpoints to prevent man-in-the-middle attacks.
  ///
  /// The [headers] parameter contains the response headers to validate.
  ///
  /// Returns `true` if the signature is valid, `false` otherwise.
  /// Currently returns `true` as a placeholder.
  /// Validates the response signature from RuTracker endpoint.
  ///
  /// This method should be implemented to verify the authenticity of
  /// responses from RuTracker endpoints to prevent man-in-the-middle attacks.
  ///
  /// The [headers] parameter contains the response headers to validate.
  ///
  /// Returns `true` if the signature is valid, `false` otherwise.
  /// Currently returns `true` as a placeholder.
  bool _validateSignature(Headers? headers) => true;

  /// Updates the endpoints list in the database.
  ///
  /// This is a helper method to persist the updated endpoints list
  /// to the database.
  ///
  /// The [endpoints] parameter is the list of endpoint configurations to store.
  Future<void> _updateEndpoints(List<Map<String, dynamic>> endpoints) async {
    await _store.record(_storeKey).put(_db, {'endpoints': endpoints});
  }

  /// Gets the currently active (highest priority healthy) endpoint.
  ///
  /// This method retrieves all enabled endpoints, sorts them by priority,
  /// and returns the URL of the highest priority healthy endpoint.
  ///
  /// Returns the URL of the active endpoint.
  ///
  /// Throws [NetworkFailure] if no healthy endpoints are available.
  /// Gets the currently active (highest priority healthy) endpoint.
  ///
  /// This method retrieves all enabled endpoints, sorts them by priority,
  /// and returns the URL of the highest priority healthy endpoint.
  ///
  /// Returns the URL of the active endpoint.
  ///
  /// Throws [NetworkFailure] if no healthy endpoints are available.
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

  /// Gets all configured endpoints from the database.
  ///
  /// This method retrieves all endpoint configurations regardless of
  /// their health status or priority.
  ///
  /// Returns a list of all endpoint configurations.
  Future<List<Map<String, dynamic>>> getAllEndpoints() async {
    final record = await _store.record(_storeKey).get(_db);
    return List<Map<String, dynamic>>.from((record?['endpoints'] as List?) ?? []);
  }

  /// Adds a new endpoint to the configuration.
  ///
  /// This method adds a new RuTracker endpoint with the specified URL
  /// and priority, initializing its health status as unknown.
  ///
  /// The [url] parameter is the URL of the new endpoint.
  /// The [priority] parameter determines the endpoint's priority (lower is better).
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

  /// Removes an endpoint from the configuration.
  ///
  /// This method permanently removes the specified endpoint from the
  /// database and its associated health data.
  ///
  /// The [url] parameter is the URL of the endpoint to remove.
  Future<void> removeEndpoint(String url) async {
    final record = await _store.record(_storeKey).get(_db);
    final endpoints = List<Map<String, dynamic>>.from((record?['endpoints'] as List?) ?? []);

    endpoints.removeWhere((e) => e['url'] == url);
    await _updateEndpoints(endpoints);
  }
}