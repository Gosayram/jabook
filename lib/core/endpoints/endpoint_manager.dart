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

  /// Performs a health check on the specified endpoint with exponential backoff.
  Future<void> healthCheck(String endpoint) async {
    final record = await _endpointsRef.get(_db);
    final endpoints =
        List<Map<String, dynamic>>.from((record?['endpoints'] as List?) ?? []);

    final endpointIndex = endpoints.indexWhere((e) => e['url'] == endpoint);
    if (endpointIndex == -1) return;

    final endpointData = endpoints[endpointIndex];
    final failureCount = endpointData['failure_count'] as int? ?? 0;
    
    // Exponential backoff: wait longer after multiple failures
    final backoffDelay = Duration(milliseconds: 1000 * (1 << (failureCount < 5 ? failureCount : 5)));
    await Future.delayed(backoffDelay);

    try {
      final startTime = DateTime.now();
      final response = await (await DioClient.instance).head(
        endpoint,
        options: Options(
          receiveTimeout: const Duration(seconds: 5), // Shorter timeout for health checks
          validateStatus: (status) => status != null && status < 500,
        ),
      );
      final rtt = DateTime.now().difference(startTime).inMilliseconds;

      // Calculate health score (0-100)
      final healthScore = _calculateHealthScore(rtt, response.statusCode ?? 200);

      endpoints[endpointIndex].addAll({
        'rtt': rtt,
        'last_ok': DateTime.now().toIso8601String(),
        'signature_ok': _validateSignature(response.headers),
        'enabled': true,
        'health_score': healthScore,
        'failure_count': 0, // Reset failure count on success
        'last_failure': null,
      });
      await _endpointsRef.put(_db, {'endpoints': endpoints});
    } on Exception catch (e) {
      // Mark endpoint as unhealthy and increment failure count
      endpoints[endpointIndex].addAll({
        'enabled': false,
        'health_score': 0,
        'failure_count': failureCount + 1,
        'last_failure': DateTime.now().toIso8601String(),
        'last_error': e.toString(),
      });
      await _endpointsRef.put(_db, {'endpoints': endpoints});
    }
  }

  /// Calculates health score based on RTT and status code
  int _calculateHealthScore(int rtt, int statusCode) {
    // Base score from status code (200 OK = 100, 4xx = 50, 5xx = 0)
    final statusScore = switch (statusCode) {
      >= 200 && < 300 => 100,
      >= 300 && < 400 => 80, // Redirects
      >= 400 && < 500 => 50, // Client errors
      _ => 0, // Server errors or other
    };

    // Adjust score based on RTT (lower is better)
    final rttPenalty = switch (rtt) {
      < 100 => 0,    // Excellent: no penalty
      < 500 => -10,  // Good: small penalty
      < 1000 => -30, // Average: moderate penalty
      < 2000 => -50, // Poor: significant penalty
      _ => -70,      // Very poor: major penalty
    };

    return (statusScore + rttPenalty).clamp(0, 100);
  }

  /// Validates the response signature from RuTracker endpoint.
  ///
  /// Placeholder: returns true by default.
  bool _validateSignature(Headers? headers) => true;

  /// Persists the updated endpoints into the database.
  Future<void> _updateEndpoints(List<Map<String, dynamic>> endpoints) async {
    await _endpointsRef.put(_db, {'endpoints': endpoints});
  }

  /// Gets the best available endpoint using health-based selection.
  ///
  /// Selects endpoints based on health score, RTT, and priority.
  /// Throws [NetworkFailure] if no healthy endpoints are available.
  Future<String> getActiveEndpoint() async {
    final record = await _endpointsRef.get(_db);
    final endpoints =
        List<Map<String, dynamic>>.from((record?['endpoints'] as List?) ?? []);

    // Filter enabled endpoints with health score > 50
    final healthyEndpoints = endpoints
        .where((e) => e['enabled'] == true && (e['health_score'] as int? ?? 0) > 50)
        .toList();

    if (healthyEndpoints.isEmpty) {
      // Fallback to any enabled endpoint if no healthy ones
      final fallbackEndpoints = endpoints.where((e) => e['enabled'] == true).toList();
      if (fallbackEndpoints.isEmpty) {
        throw const NetworkFailure('No healthy endpoints available');
      }
      
      // Sort fallback by priority
      fallbackEndpoints.sort((a, b) => a['priority'].compareTo(b['priority']));
      return fallbackEndpoints.first['url'] as String;
    }

    // Sort by health score (descending), then RTT (ascending), then priority (ascending)
    healthyEndpoints.sort((a, b) {
      final scoreA = a['health_score'] as int? ?? 0;
      final scoreB = b['health_score'] as int? ?? 0;
      if (scoreA != scoreB) return scoreB.compareTo(scoreA);
      
      final rttA = a['rtt'] as int? ?? 9999;
      final rttB = b['rtt'] as int? ?? 9999;
      if (rttA != rttB) return rttA.compareTo(rttB);
      
      return a['priority'].compareTo(b['priority']);
    });

    return healthyEndpoints.first['url'] as String;
  }

  /// Gets all endpoints with their current health status
  Future<List<Map<String, dynamic>>> getAllEndpointsWithHealth() async {
    final record = await _endpointsRef.get(_db);
    final endpoints =
        List<Map<String, dynamic>>.from((record?['endpoints'] as List?) ?? []);

    // Add calculated health status for each endpoint
    return endpoints.map((endpoint) {
      final healthScore = endpoint['health_score'] as int? ?? 0;
      final enabled = endpoint['enabled'] == true;
      
      String healthStatus;
      if (!enabled) {
        healthStatus = 'Disabled';
      } else if (healthScore >= 80) {
        healthStatus = 'Excellent';
      } else if (healthScore >= 60) {
        healthStatus = 'Good';
      } else if (healthScore >= 40) {
        healthStatus = 'Fair';
      } else {
        healthStatus = 'Poor';
      }

      return {
        ...endpoint,
        'health_status': healthStatus,
      };
    }).toList();
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
          'health_score': 0,
          'failure_count': 0,
          'last_failure': null,
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