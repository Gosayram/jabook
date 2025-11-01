import 'package:dio/dio.dart';
import 'package:jabook/core/errors/failures.dart';
import 'package:jabook/core/logging/structured_logger.dart';
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
  static const String _activeUrlKey = 'active_url';

  /// Convenience getter to avoid receiver duplication warnings.
  RecordRef<String, Map<String, dynamic>> get _endpointsRef =>
      _store.record(_storeKey);

  /// Initializes the EndpointManager with default endpoints and performs health checks.
  Future<void> initialize() async {
    await initializeDefaultEndpoints();
    await _performInitialHealthChecks();
  }

  /// Initializes the default RuTracker endpoints if none exist.
  Future<void> initializeDefaultEndpoints() async {
    final defaultEndpoints = [
      {'url': 'https://rutracker.net', 'priority': 1, 'enabled': true},
      {'url': 'https://rutracker.me', 'priority': 2, 'enabled': true},
      {'url': 'https://rutracker.org', 'priority': 3, 'enabled': true},
    ];

    final record = await _endpointsRef.get(_db);
    if (record == null) {
      await _endpointsRef.put(_db, {
        'endpoints': defaultEndpoints,
        _activeUrlKey: defaultEndpoints.first['url'],
      });
    }
  }

  /// Performs initial health checks on all endpoints
  Future<void> _performInitialHealthChecks() async {
    final record = await _endpointsRef.get(_db);
    final endpoints =
        List<Map<String, dynamic>>.from((record?['endpoints'] as List?) ?? []);

    for (final endpoint in endpoints) {
      final url = endpoint['url'] as String;
      if (endpoint['enabled'] == true) {
        await healthCheck(url);
      }
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
    final backoffDelay = Duration(
        milliseconds: 1000 * (1 << (failureCount < 5 ? failureCount : 5)));
    await Future.delayed(backoffDelay);

    try {
      // Ensure HTTP client has the same cookies as WebView (Cloudflare/auth)
      await DioClient.syncCookiesFromWebView();
      final startTime = DateTime.now();
      final dio = await DioClient.instance;
      final cacheBuster = DateTime.now().millisecondsSinceEpoch;
      final response = await dio.get(
        '$endpoint/forum/index.php',
        options: Options(
          followRedirects: true,
          maxRedirects: 5,
          receiveTimeout: const Duration(seconds: 15),
          sendTimeout: const Duration(seconds: 15),
          validateStatus: (status) => true,
          headers: {
            'Accept':
                'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
            'Accept-Language': 'ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7',
            'Accept-Encoding': 'gzip, deflate, br',
            'Upgrade-Insecure-Requests': '1',
            'Cache-Control': 'no-cache',
          },
        ),
        queryParameters: {'_': cacheBuster},
      );
      final rtt = DateTime.now().difference(startTime).inMilliseconds;

      // Treat Cloudflare challenge (403 with CF headers) as reachable
      final status = response.statusCode ?? 0;
      final headers = response.headers;
      final isCloudflare =
          (headers.value('server')?.toLowerCase().contains('cloudflare') ??
                  false) ||
              headers.map.keys.any((k) => k.toLowerCase() == 'cf-ray');
      // Detect CF challenge by response body text as well
      final body = response.data is String
          ? (response.data as String).toLowerCase()
          : '';
      final looksLikeCf = body.contains('checking your browser') ||
          body.contains('please enable javascript') ||
          body.contains('attention required') ||
          body.contains('cf-chl') ||
          body.contains('cloudflare');

      // Consider healthy: 2xx-3xx, or CF challenge (403/503 + CF headers/body)
      final isHealthy = (status >= 200 && status < 400) ||
          ((status == 403 || status == 503) && (isCloudflare || looksLikeCf));

      // Calculate health score (0-100)
      var healthScore = _calculateHealthScore(rtt, isHealthy ? 200 : status);
      if (status == 403 && isCloudflare) {
        // Penalize a bit but keep usable
        healthScore = (healthScore - 10).clamp(40, 100);
      }

      endpoints[endpointIndex].addAll({
        'rtt': rtt,
        'last_ok': DateTime.now().toIso8601String(),
        'signature_ok': _validateSignature(headers),
        'enabled': true,
        'health_score': healthScore,
        'failure_count': 0,
        'last_failure': null,
        'cooldown_until': null,
      });
      await _endpointsRef.put(_db, {'endpoints': endpoints});
      // Log success
      await StructuredLogger().log(
        level: 'info',
        subsystem: 'endpoints',
        message: 'HealthCheck success for $endpoint',
        extra: {
          'rtt_ms': rtt,
          'status': response.statusCode,
          'health_score': healthScore,
        },
      );
    } on Exception catch (e) {
      // Не отключаем зеркало мгновенно: требуем как минимум 2 подряд ошибки
      final newFailureCount = failureCount + 1;
      final cooldown = _calculateCooldown(newFailureCount);
      endpoints[endpointIndex].addAll({
        'enabled': newFailureCount >= 2 ? false : true,
        'health_score': newFailureCount >= 2
            ? 0
            : (endpoints[endpointIndex]['health_score'] as int? ?? 0)
                .clamp(0, 40),
        'failure_count': newFailureCount,
        'last_failure': DateTime.now().toIso8601String(),
        'last_error': e.toString(),
        'cooldown_until':
            newFailureCount >= 2 ? cooldown?.toIso8601String() : null,
      });
      await _endpointsRef.put(_db, {'endpoints': endpoints});
      // Log failure
      await StructuredLogger().log(
        level: 'warning',
        subsystem: 'endpoints',
        message: 'HealthCheck failed for $endpoint',
        cause: e.toString(),
        extra: {
          'failure_count': newFailureCount,
          'cooldown_until': cooldown?.toIso8601String(),
        },
      );
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
      < 100 => 0, // Excellent: no penalty
      < 500 => -10, // Good: small penalty
      < 1000 => -30, // Average: moderate penalty
      < 2000 => -50, // Poor: significant penalty
      _ => -70, // Very poor: major penalty
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

    // 1) If we already have a chosen active mirror, keep using it while it's not in cooldown/disabled
    final activeFromStore = (record?[_activeUrlKey] as String?);
    if (activeFromStore != null) {
      final idx = endpoints.indexWhere((e) => e['url'] == activeFromStore);
      if (idx != -1) {
        final e = endpoints[idx];
        final enabled = e['enabled'] == true;
        final cooldownUntilStr = e['cooldown_until'] as String?;
        final inCooldown = () {
          if (cooldownUntilStr == null) return false;
          final dt = DateTime.tryParse(cooldownUntilStr);
          if (dt == null) return false;
          return DateTime.now().isBefore(dt);
        }();
        if (enabled && !inCooldown) {
          await StructuredLogger().log(
            level: 'info',
            subsystem: 'endpoints',
            message: 'Using sticky active endpoint',
            extra: {'url': activeFromStore},
          );
          return activeFromStore;
        }
      }
    }

    // Refresh availability based on cooldowns
    for (final e in endpoints) {
      final cooldownUntilStr = e['cooldown_until'] as String?;
      if (cooldownUntilStr != null) {
        final cooldownUntil = DateTime.tryParse(cooldownUntilStr);
        if (cooldownUntil != null && DateTime.now().isAfter(cooldownUntil)) {
          // Cooldown expired: re-enable on probation with low health score
          e['enabled'] = true;
          e['health_score'] = (e['health_score'] as int? ?? 0).clamp(0, 40);
          e['cooldown_until'] = null;
          await StructuredLogger().log(
            level: 'info',
            subsystem: 'endpoints',
            message: 'Cooldown expired, re-enabling mirror',
            extra: {'url': e['url']},
          );
        }
      }
    }

    // 2) Filter enabled endpoints with sufficient health. If health unknown, we will treat later as fallback
    final healthyEndpoints = endpoints
        .where((e) =>
            e['enabled'] == true && (e['health_score'] as int? ?? 0) > 50)
        .toList();

    if (healthyEndpoints.isEmpty) {
      // Fallback to any enabled endpoint if no healthy ones
      // include unknown-health endpoints too (enabled ones)
      final fallbackEndpoints =
          endpoints.where((e) => e['enabled'] == true).toList();
      if (fallbackEndpoints.isEmpty) {
        throw const NetworkFailure('No healthy endpoints available');
      }

      // Sort fallback by priority
      fallbackEndpoints.sort((a, b) => a['priority'].compareTo(b['priority']));
      final chosen = fallbackEndpoints.first['url'] as String;
      // Persist sticky active
      await _endpointsRef.put(_db, {
        'endpoints': endpoints,
        _activeUrlKey: chosen,
      });
      await StructuredLogger().log(
        level: 'warning',
        subsystem: 'endpoints',
        message: 'No healthy endpoints, using fallback',
        extra: {'url': chosen},
      );
      return chosen;
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

    final chosen = healthyEndpoints.first['url'] as String;
    // Persist sticky active
    await _endpointsRef.put(_db, {
      'endpoints': endpoints,
      _activeUrlKey: chosen,
    });
    await StructuredLogger().log(
      level: 'info',
      subsystem: 'endpoints',
      message: 'Active endpoint selected',
      extra: {'url': chosen},
    );
    return chosen;
  }

  /// Calculates cooldown duration based on failure count using exponential backoff
  Duration? _failureCooldown(int failures) {
    if (failures <= 1) return null;
    final capped = failures > 6 ? 6 : failures; // cap growth
    return Duration(minutes: 1 * (1 << (capped - 1)));
  }

  DateTime? _calculateCooldown(int failures) {
    final dur = _failureCooldown(failures);
    if (dur == null) return null;
    return DateTime.now().add(dur);
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
    return List<Map<String, dynamic>>.from(
        (record?['endpoints'] as List?) ?? []);
  }

  /// Force-set active endpoint (sticky) if it exists and is enabled.
  Future<void> setActiveEndpoint(String url) async {
    final record = await _endpointsRef.get(_db);
    final endpoints =
        List<Map<String, dynamic>>.from((record?['endpoints'] as List?) ?? []);
    final idx = endpoints.indexWhere((e) => e['url'] == url);
    if (idx == -1) return;
    if (endpoints[idx]['enabled'] != true) return;
    await _endpointsRef.put(_db, {
      'endpoints': endpoints,
      _activeUrlKey: url,
    });
    await StructuredLogger().log(
      level: 'info',
      subsystem: 'endpoints',
      message: 'Active endpoint manually set',
      extra: {'url': url},
    );
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

  /// Updates an endpoint's enabled status.
  Future<void> updateEndpointStatus(String url, bool enabled) async {
    final record = await _endpointsRef.get(_db);
    final endpoints =
        List<Map<String, dynamic>>.from((record?['endpoints'] as List?) ?? []);

    final endpointIndex = endpoints.indexWhere((e) => e['url'] == url);
    if (endpointIndex == -1) return;

    endpoints[endpointIndex]['enabled'] = enabled;
    await _updateEndpoints(endpoints);
  }

  /// Builds a full URL using the active endpoint and the given path.
  Future<String> buildUrl(String path) async {
    final baseUrl = await getActiveEndpoint();
    if (path.startsWith('/')) {
      path = path.substring(1);
    }
    return '$baseUrl/$path';
  }
}
