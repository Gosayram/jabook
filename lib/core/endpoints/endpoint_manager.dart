import 'package:dio/dio.dart';
import 'package:jabook/core/errors/failures.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/net/user_agent_manager.dart';
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
      {'url': 'https://rutracker.me', 'priority': 1, 'enabled': true},
      {'url': 'https://rutracker.net', 'priority': 2, 'enabled': true},
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
  /// Minimum time between health checks for the same endpoint (5 minutes).
  static const Duration _healthCheckCacheTTL = Duration(minutes: 5);

  /// Cache TTL for quick availability checks (30 seconds - much shorter than full health check).
  static const Duration _quickCheckCacheTTL = Duration(seconds: 30);

  /// Maximum retries for temporary errors.
  static const int _maxRetries = 3;

  /// Retry delay for temporary errors.
  static const Duration _retryDelay = Duration(seconds: 2);

  /// In-memory cache for quick availability check results.
  /// Key: endpoint URL, Value: Map with 'result' (bool) and 'timestamp' (DateTime)
  static final Map<String, Map<String, dynamic>> _quickCheckCache = {};

  /// Performs a health check on the specified endpoint.
  ///
  /// The [endpoint] parameter is the URL of the endpoint to check.
  /// The [force] parameter, when true, bypasses cache and forces a fresh check.
  Future<void> healthCheck(String endpoint, {bool force = false}) async {
    final record = await _endpointsRef.get(_db);
    final endpoints =
        List<Map<String, dynamic>>.from((record?['endpoints'] as List?) ?? []);

    final endpointIndex = endpoints.indexWhere((e) => e['url'] == endpoint);
    if (endpointIndex == -1) return;

    final endpointData = endpoints[endpointIndex];
    final failureCount = endpointData['failure_count'] as int? ?? 0;

    // Check cache: skip if recently checked (unless forced or in cooldown)
    if (!force) {
      final lastOk = endpointData['last_ok'] as String?;
      if (lastOk != null) {
        final lastCheck = DateTime.tryParse(lastOk);
        if (lastCheck != null) {
          final timeSinceLastCheck = DateTime.now().difference(lastCheck);
          // Skip if checked recently and no failures
          if (timeSinceLastCheck < _healthCheckCacheTTL &&
              failureCount == 0 &&
              (endpointData['health_score'] as int? ?? 0) > 50) {
            await StructuredLogger().log(
              level: 'debug',
              subsystem: 'endpoints',
              message: 'Skipping health check (recently checked)',
              extra: {
                'url': endpoint,
                'seconds_ago': timeSinceLastCheck.inSeconds
              },
            );
            return;
          }
        }
      }
    }

    // Exponential backoff: wait longer after multiple failures
    await Future.delayed(Duration(
        milliseconds: 1000 * (1 << (failureCount < 5 ? failureCount : 5))));

    // Retry logic for temporary errors
    Exception? lastException;
    var lastAttempt = 0;
    for (var attempt = 0; attempt <= _maxRetries; attempt++) {
      lastAttempt = attempt;
      try {
        // Ensure HTTP client has the same cookies as WebView (Cloudflare/auth)
        await DioClient.syncCookiesFromWebView();

        // Get real User-Agent for Cloudflare compatibility
        final userAgentManager = UserAgentManager();
        final userAgent = await userAgentManager.getUserAgent();

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
              'User-Agent': userAgent,
              'Accept':
                  'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
              'Accept-Language': 'ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7',
              'Accept-Encoding': 'gzip, deflate, br',
              'Upgrade-Insecure-Requests': '1',
              'Cache-Control': 'no-cache',
              'Referer': '$endpoint/',
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
            body.contains('cloudflare') ||
            body.contains('ddos-guard') ||
            body.contains('just a moment') ||
            body.contains('verifying you are human') ||
            body.contains('security check') ||
            body.contains('cf-browser-verification') ||
            body.contains('cf-challenge-running') ||
            body.contains('cf-ray');

        // Consider healthy: 2xx-3xx, or CF challenge (403/503 + CF headers/body)
        // Even if CF challenge, mirror is working, just protected
        final isHealthy = (status >= 200 && status < 400) ||
            ((status == 403 || status == 503) && (isCloudflare || looksLikeCf));

        // Calculate health score (0-100)
        var healthScore = _calculateHealthScore(rtt, isHealthy ? 200 : status);
        if ((status == 403 || status == 503) && (isCloudflare || looksLikeCf)) {
          // Cloudflare challenge: mirror is working, just protected
          // Give it a reasonable health score (60-80) to indicate it's usable
          healthScore = (healthScore - 20).clamp(60, 85);
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

        // Update quick check cache with fresh result
        _quickCheckCache[endpoint] = {
          'result': isHealthy,
          'timestamp': DateTime.now(),
        };

        // Log success
        await StructuredLogger().log(
          level: 'info',
          subsystem: 'endpoints',
          message: 'HealthCheck success for $endpoint',
          extra: {
            'rtt_ms': rtt,
            'status': response.statusCode,
            'health_score': healthScore,
            'attempt': attempt + 1,
          },
        );
        return; // Success, exit retry loop
      } on DioException catch (e) {
        lastException = e;

        // DNS errors are not retryable - mark as unavailable immediately
        if (e.type == DioExceptionType.connectionError) {
          final message = e.message?.toLowerCase() ?? '';
          if (message.contains('host lookup') ||
              message.contains('no address associated with hostname') ||
              message.contains('name or service not known')) {
            await StructuredLogger().log(
              level: 'error',
              subsystem: 'endpoints',
              message: 'DNS lookup failed for endpoint',
              extra: {
                'url': endpoint,
                'error': e.message,
                'attempt': attempt + 1,
              },
            );
            // DNS error, not retryable - break immediately
            break;
          }
        }

        // Check if this is a retryable error
        final isRetryable = _isRetryableError(e);

        if (!isRetryable || attempt >= _maxRetries) {
          // Not retryable or max retries reached, break loop and handle error
          break;
        }

        // Wait before retry
        await StructuredLogger().log(
          level: 'debug',
          subsystem: 'endpoints',
          message: 'HealthCheck retryable error, retrying',
          extra: {
            'url': endpoint,
            'attempt': attempt + 1,
            'max_retries': _maxRetries,
            'error': e.type.toString(),
          },
        );
        await Future.delayed(
            _retryDelay * (attempt + 1)); // Exponential backoff
        continue;
      }
    }

    // If we reach here, all retries failed or error is not retryable
    if (lastException is DioException) {
      final e = lastException;

      // Check if this is a DNS error for special handling
      final message = e.message?.toLowerCase() ?? '';
      final isDnsError = e.type == DioExceptionType.connectionError &&
          (message.contains('host lookup') ||
              message.contains('no address associated with hostname') ||
              message.contains('name or service not known'));

      // Handle Dio-specific errors with more context
      final errorType = isDnsError
          ? 'DNS lookup failed'
          : switch (e.type) {
              DioExceptionType.connectionTimeout => 'Connection timeout',
              DioExceptionType.sendTimeout => 'Send timeout',
              DioExceptionType.receiveTimeout => 'Receive timeout',
              DioExceptionType.badResponse => 'Bad response',
              DioExceptionType.cancel => 'Request cancelled',
              DioExceptionType.connectionError => 'Connection error',
              DioExceptionType.badCertificate => 'Bad certificate',
              DioExceptionType.unknown => 'Unknown error',
            };

      // Handle DioException after retries exhausted or not retryable
      // DNS errors: disable immediately, no need for multiple failures
      // Other errors: require at least 2 consecutive failures
      final newFailureCount = failureCount + 1;
      final cooldown = _calculateCooldown(newFailureCount);

      // DNS errors disable immediately, others require 2 failures
      final shouldDisable = isDnsError || newFailureCount >= 2;

      endpoints[endpointIndex].addAll({
        'enabled': shouldDisable ? false : true,
        'health_score': shouldDisable
            ? 0
            : (endpoints[endpointIndex]['health_score'] as int? ?? 0)
                .clamp(0, 40),
        'failure_count': newFailureCount,
        'last_failure': DateTime.now().toIso8601String(),
        'last_error': '$errorType: ${e.message ?? e.toString()}',
        'cooldown_until': shouldDisable ? cooldown?.toIso8601String() : null,
      });
      await _endpointsRef.put(_db, {'endpoints': endpoints});
      // Log failure with detailed context
      await StructuredLogger().log(
        level: isDnsError ? 'error' : 'warning',
        subsystem: 'endpoints',
        message: isDnsError
            ? 'HealthCheck failed: DNS lookup error'
            : 'HealthCheck failed for $endpoint',
        cause: '$errorType: ${e.message ?? e.toString()}',
        extra: {
          'failure_count': newFailureCount,
          'cooldown_until': cooldown?.toIso8601String(),
          'error_type': errorType,
          'response_status': e.response?.statusCode,
          'retries_attempted': lastAttempt + 1,
          'is_dns_error': isDnsError,
          'disabled': shouldDisable,
        },
      );

      // Notify if this was the active mirror and it's now disabled
      if (shouldDisable) {
        final record = await _endpointsRef.get(_db);
        final activeUrl = record?[_activeUrlKey] as String?;
        if (activeUrl == endpoint) {
          await StructuredLogger().log(
            level: 'error',
            subsystem: 'endpoints',
            message: 'Active mirror disabled due to failures',
            extra: {
              'url': endpoint,
              'failure_count': newFailureCount,
              'reason': isDnsError ? 'DNS lookup failed' : 'Multiple failures',
            },
          );
        }
      }
    } else if (lastException != null) {
      // Handle other exceptions (non-retryable)
      final e = lastException;
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

  /// Checks if an error is retryable (temporary network issues).
  ///
  /// DNS errors are NOT retryable as they indicate endpoint is unavailable.
  bool _isRetryableError(DioException e) {
    // DNS errors are not retryable - endpoint is unavailable
    if (e.type == DioExceptionType.connectionError) {
      final message = e.message?.toLowerCase() ?? '';
      if (message.contains('host lookup') ||
          message.contains('no address associated with hostname') ||
          message.contains('name or service not known')) {
        return false; // DNS error, not retryable
      }
    }
    // Other connection errors, timeouts, and server errors are retryable
    return e.type == DioExceptionType.connectionTimeout ||
        e.type == DioExceptionType.receiveTimeout ||
        e.type == DioExceptionType.sendTimeout ||
        e.type == DioExceptionType.connectionError ||
        (e.type == DioExceptionType.badResponse &&
            e.response?.statusCode != null &&
            (e.response!.statusCode! >= 500)); // Server errors
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

  /// Performs a quick availability check for an endpoint (DNS + basic connectivity + CloudFlare detection).
  ///
  /// Returns true if endpoint is available (can resolve DNS and connect),
  /// false otherwise. CloudFlare challenges are considered as "available".
  ///
  /// Results are cached for 30 seconds to avoid excessive network requests.
  Future<bool> quickAvailabilityCheck(String endpoint) async {
    // Check cache first
    final cached = _quickCheckCache[endpoint];
    if (cached != null) {
      final timestamp = cached['timestamp'] as DateTime;
      final result = cached['result'] as bool;
      final age = DateTime.now().difference(timestamp);

      if (age < _quickCheckCacheTTL) {
        await StructuredLogger().log(
          level: 'debug',
          subsystem: 'endpoints',
          message: 'Using cached quick availability check result',
          extra: {
            'url': endpoint,
            'result': result,
            'age_seconds': age.inSeconds,
          },
        );
        return result;
      } else {
        // Cache expired, remove it
        _quickCheckCache.remove(endpoint);
      }
    }

    try {
      final userAgentManager = UserAgentManager();
      final userAgent = await userAgentManager.getUserAgent();

      final dioInstance = Dio(BaseOptions(
        connectTimeout: const Duration(seconds: 5),
        receiveTimeout: const Duration(seconds: 5),
      ));

      final response = await dioInstance
          .get(
            '$endpoint/forum/index.php',
            options: Options(
              validateStatus: (status) => status != null && status < 500,
              headers: {
                'User-Agent': userAgent,
                'Accept':
                    'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
              },
            ),
          )
          .timeout(const Duration(seconds: 5));

      // Check for CloudFlare challenge - endpoint is reachable but protected
      final status = response.statusCode ?? 0;
      final headers = response.headers;
      final isCloudflare =
          (headers.value('server')?.toLowerCase().contains('cloudflare') ??
                  false) ||
              headers.map.keys.any((k) => k.toLowerCase() == 'cf-ray');

      final body = response.data is String
          ? (response.data as String).toLowerCase()
          : '';
      final looksLikeCf = body.contains('checking your browser') ||
          body.contains('please enable javascript') ||
          body.contains('cloudflare') ||
          body.contains('just a moment');

      // Endpoint is available if:
      // 1. Status 200-399 (success/redirect)
      // 2. Status 403/503 with CloudFlare headers/body (reachable but protected)
      final isAvailable = (status >= 200 && status < 400) ||
          ((status == 403 || status == 503) && (isCloudflare || looksLikeCf));

      if (isAvailable && (isCloudflare || looksLikeCf)) {
        await StructuredLogger().log(
          level: 'info',
          subsystem: 'endpoints',
          message: 'Endpoint available but protected by CloudFlare',
          extra: {'url': endpoint, 'status': status},
        );
      }

      // Cache the result
      _quickCheckCache[endpoint] = {
        'result': isAvailable,
        'timestamp': DateTime.now(),
      };

      return isAvailable;
    } on DioException catch (e) {
      // DNS errors indicate endpoint is unavailable
      if (e.type == DioExceptionType.connectionError) {
        final message = e.message?.toLowerCase() ?? '';
        if (message.contains('host lookup') ||
            message.contains('no address associated with hostname') ||
            message.contains('name or service not known')) {
          await StructuredLogger().log(
            level: 'warning',
            subsystem: 'endpoints',
            message: 'DNS lookup failed for endpoint availability check',
            extra: {'url': endpoint, 'error': e.message},
          );
          return false; // DNS error, endpoint unavailable
        }
      }
      // Other errors (timeouts, connection errors) might be temporary
      // Cache negative result with shorter TTL (10 seconds for failures)
      _quickCheckCache[endpoint] = {
        'result': false,
        'timestamp': DateTime.now(),
      };
      return false;
    } on Exception {
      // Cache negative result for exceptions too
      _quickCheckCache[endpoint] = {
        'result': false,
        'timestamp': DateTime.now(),
      };
      return false;
    }
  }

  /// Clears the quick availability check cache for a specific endpoint.
  ///
  /// Useful when an endpoint state might have changed (e.g., after manual switch).
  void clearQuickCheckCache(String endpoint) {
    _quickCheckCache.remove(endpoint);
  }

  /// Clears all quick availability check cache entries.
  void clearAllQuickCheckCache() {
    _quickCheckCache.clear();
  }

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

    // 1) If we already have a chosen active mirror, check availability before using it
    final activeFromStore = record?[_activeUrlKey] as String?;
    if (activeFromStore != null) {
      final idx = endpoints.indexWhere((e) => e['url'] == activeFromStore);
      if (idx != -1) {
        final e = endpoints[idx];
        final enabled = e['enabled'] == true;
        final cooldownUntilStr = e['cooldown_until'] as String?;
        final inCooldown = cooldownUntilStr != null &&
            (DateTime.tryParse(cooldownUntilStr)?.isAfter(DateTime.now()) ??
                false);
        if (enabled && !inCooldown) {
          // Quick availability check (DNS + basic connectivity)
          final isAvailable = await quickAvailabilityCheck(activeFromStore);
          if (isAvailable) {
            await StructuredLogger().log(
              level: 'info',
              subsystem: 'endpoints',
              message: 'Using sticky active endpoint',
              extra: {'url': activeFromStore},
            );
            return activeFromStore;
          } else {
            // Sticky endpoint unavailable, log and continue to selection
            await StructuredLogger().log(
              level: 'warning',
              subsystem: 'endpoints',
              message: 'Sticky endpoint unavailable, selecting new one',
              extra: {'url': activeFromStore},
            );
          }
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
        // Ultimate fallback: use rutracker.net as guaranteed working mirror
        // Changed from rutracker.me (which is blocked by CloudFlare)
        const hardcodedFallback = 'https://rutracker.net';
        await StructuredLogger().log(
          level: 'warning',
          subsystem: 'endpoints',
          message: 'No endpoints available, using hardcoded fallback',
          extra: {'url': hardcodedFallback},
        );
        return hardcodedFallback;
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
    final oldEndpoint = record?[_activeUrlKey] as String?;
    final endpoints =
        List<Map<String, dynamic>>.from((record?['endpoints'] as List?) ?? []);
    final idx = endpoints.indexWhere((e) => e['url'] == url);
    if (idx == -1) return;
    if (endpoints[idx]['enabled'] != true) return;

    await _endpointsRef.put(_db, {
      'endpoints': endpoints,
      _activeUrlKey: url,
    });

    // Sync cookies if switching to a different endpoint
    if (oldEndpoint != null && oldEndpoint != url) {
      try {
        await DioClient.syncCookiesOnEndpointSwitch(oldEndpoint, url);
      } on Exception catch (e) {
        await StructuredLogger().log(
          level: 'warning',
          subsystem: 'endpoints',
          message: 'Failed to sync cookies when setting active endpoint',
          cause: e.toString(),
        );
      }
    }

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

  /// Updates an endpoint's priority.
  Future<void> updateEndpointPriority(String url, int priority) async {
    final record = await _endpointsRef.get(_db);
    final endpoints =
        List<Map<String, dynamic>>.from((record?['endpoints'] as List?) ?? []);

    final endpointIndex = endpoints.indexWhere((e) => e['url'] == url);
    if (endpointIndex == -1) return;

    endpoints[endpointIndex]['priority'] = priority.clamp(1, 10);
    await _updateEndpoints(endpoints);

    await StructuredLogger().log(
      level: 'info',
      subsystem: 'endpoints',
      message: 'Endpoint priority updated',
      extra: {'url': url, 'priority': priority},
    );
  }

  /// Builds a full URL using the active endpoint and the given path.
  Future<String> buildUrl(String path) async {
    final baseUrl = await getActiveEndpoint();
    if (path.startsWith('/')) {
      path = path.substring(1);
    }
    return '$baseUrl/$path';
  }

  /// Tries to switch to another available endpoint if current one is failing.
  ///
  /// Returns true if switch was successful, false otherwise.
  Future<bool> trySwitchEndpoint(String currentEndpoint) async {
    final record = await _endpointsRef.get(_db);
    final endpoints =
        List<Map<String, dynamic>>.from((record?['endpoints'] as List?) ?? []);

    // Disable current endpoint temporarily
    final currentIndex =
        endpoints.indexWhere((e) => e['url'] == currentEndpoint);
    if (currentIndex != -1) {
      endpoints[currentIndex]['enabled'] = false;
      endpoints[currentIndex]['failure_count'] =
          (endpoints[currentIndex]['failure_count'] as int? ?? 0) + 1;
      await _updateEndpoints(endpoints);
    }

    // Try to get a new active endpoint
    try {
      final newEndpoint = await getActiveEndpoint();

      if (newEndpoint != currentEndpoint) {
        // Clear cache for both endpoints since state changed
        clearQuickCheckCache(currentEndpoint);
        clearQuickCheckCache(newEndpoint);

        // Sync cookies to new endpoint
        try {
          await DioClient.syncCookiesOnEndpointSwitch(
            currentEndpoint,
            newEndpoint,
          );
        } on Exception catch (e) {
          await StructuredLogger().log(
            level: 'warning',
            subsystem: 'endpoints',
            message: 'Failed to sync cookies on endpoint switch',
            cause: e.toString(),
          );
        }

        await StructuredLogger().log(
          level: 'info',
          subsystem: 'endpoints',
          message: 'Automatically switched endpoint',
          extra: {
            'old': currentEndpoint,
            'new': newEndpoint,
          },
        );
        return true;
      }
    } on Exception {
      // Failed to get new endpoint
    }

    return false;
  }
}
