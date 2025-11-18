// Copyright 2025 Jabook Contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import 'package:dio/dio.dart';
import 'package:jabook/core/errors/failures.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/net/user_agent_manager.dart';
import 'package:jabook/core/utils/dns_lookup.dart';
import 'package:jabook/core/utils/safe_async.dart';
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

  /// Default RuTracker endpoints configuration.
  ///
  /// This is the single source of truth for all RuTracker mirrors.
  /// All endpoints are defined here and used throughout the application.
  static List<Map<String, dynamic>> getDefaultEndpoints() => [
        {'url': 'https://rutracker.me', 'priority': 1, 'enabled': true},
        {'url': 'https://rutracker.net', 'priority': 2, 'enabled': true},
        {'url': 'https://rutracker.org', 'priority': 3, 'enabled': true},
      ];

  /// Returns list of default endpoint URLs in priority order.
  ///
  /// This method provides a convenient way to get all endpoint URLs
  /// without needing to parse the full endpoint configuration.
  static List<String> getDefaultEndpointUrls() => getDefaultEndpoints()
      .map((e) => e['url'] as String)
      .toList();

  /// Returns list of RuTracker domain names (without protocol).
  ///
  /// Used for cookie management and domain matching.
  static List<String> getRutrackerDomains() =>
      ['rutracker.me', 'rutracker.net', 'rutracker.org'];

  /// Returns the primary fallback endpoint URL.
  ///
  /// This is used as the ultimate fallback when all other endpoints fail.
  /// Currently returns rutracker.net as it's the most stable mirror.
  static String getPrimaryFallbackEndpoint() => 'https://rutracker.net';

  /// Initializes the EndpointManager with default endpoints and performs health checks.
  ///
  /// Health checks are performed asynchronously in the background to avoid
  /// blocking application startup.
  Future<void> initialize() async {
    await initializeDefaultEndpoints();
    // Perform health checks in background - don't block initialization
    // This allows the app to start even if network is unavailable
    safeUnawaited(
      _performInitialHealthChecks(),
      onError: (e, stack) {
        final logger = StructuredLogger();
        safeUnawaited(
          logger.log(
            level: 'warning',
            subsystem: 'endpoints',
            message: 'Health checks failed during initialization',
            context: 'endpoint_init',
            cause: e.toString(),
          ),
        );
      },
    );
  }

  /// Initializes the default RuTracker endpoints if none exist.
  Future<void> initializeDefaultEndpoints() async {
    final defaultEndpoints = getDefaultEndpoints();

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
    final operationId = 'health_check_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    final record = await _endpointsRef.get(_db);
    final endpoints =
        List<Map<String, dynamic>>.from((record?['endpoints'] as List?) ?? []);

    final endpointIndex = endpoints.indexWhere((e) => e['url'] == endpoint);
    if (endpointIndex == -1) return;

    final endpointData = endpoints[endpointIndex];
    final failureCount = endpointData['failure_count'] as int? ?? 0;
    final healthScore = endpointData['health_score'] as int? ?? 0;
    final enabled = endpointData['enabled'] == true;

    // Log health check start with initial state
    await logger.log(
      level: 'debug',
      subsystem: 'endpoints',
      message: 'Health check started',
      operationId: operationId,
      context: 'health_check',
      stateBefore: {
        'health_score': healthScore,
        'enabled': enabled,
        'failure_count': failureCount,
      },
      extra: {
        'url': endpoint,
        'force': force,
      },
    );

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
              healthScore > 50) {
            await logger.log(
              level: 'debug',
              subsystem: 'endpoints',
              message: 'Skipping health check (recently checked)',
              operationId: operationId,
              context: 'health_check',
              durationMs: DateTime.now().difference(startTime).inMilliseconds,
              extra: {
                'url': endpoint,
                'seconds_ago': timeSinceLastCheck.inSeconds,
              },
            );
            return;
          }
        }
      }
    }

    // Exponential backoff: wait longer after multiple failures
    final backoffMs = 1000 * (1 << (failureCount < 5 ? failureCount : 5));
    await Future.delayed(Duration(milliseconds: backoffMs));

    if (backoffMs > 0) {
      await logger.log(
        level: 'debug',
        subsystem: 'retry',
        message: 'Applied exponential backoff before health check',
        operationId: operationId,
        extra: {
          'url': endpoint,
          'backoff_ms': backoffMs,
          'failure_count': failureCount,
          'backoff_type': 'exponential',
          'backoff_formula': '1000 * (2^min(failure_count, 5))',
          'original_subsystem': 'endpoints',
        },
      );
    }

    // Retry logic for temporary errors
    Exception? lastException;
    var lastAttempt = 0;
    for (var attempt = 0; attempt <= _maxRetries; attempt++) {
      lastAttempt = attempt;
      final attemptStartTime = DateTime.now();

      await logger.log(
        level: 'debug',
        subsystem: 'endpoints',
        message: 'Health check attempt',
        operationId: operationId,
        context: 'health_check',
        extra: {
          'url': endpoint,
          'attempt': attempt + 1,
          'max_retries': _maxRetries,
        },
      );

      try {
        // Ensure HTTP client has the same cookies as WebView (Cloudflare/auth)
        await DioClient.syncCookiesFromWebView();

        // Get real User-Agent for Cloudflare compatibility
        final userAgentManager = UserAgentManager();
        final userAgent = await userAgentManager.getUserAgent();

        // Perform DNS lookup before HTTP request
        final endpointUri = Uri.parse(endpoint);
        final host = endpointUri.host;
        DnsLookupResult? dnsResult;

        try {
          dnsResult = await dnsLookup(
            host,
            operationId: operationId,
          );

          await logger.log(
            level: 'debug',
            subsystem: 'network',
            message: 'DNS lookup completed for health check',
            operationId: operationId,
            context: 'health_check',
            extra: {
              'host': host,
              'dns_success': dnsResult.success,
              'ip_addresses': dnsResult.ipAddresses,
              'resolve_time_ms': dnsResult.resolveTime.inMilliseconds,
              'original_subsystem': 'endpoints',
            },
          );
        } on Exception catch (e) {
          await logger.log(
            level: 'warning',
            subsystem: 'network',
            message: 'DNS lookup failed before health check',
            operationId: operationId,
            context: 'health_check',
            cause: e.toString(),
            extra: {
              'host': host,
              'note': 'Continuing with HTTP request anyway',
              'original_subsystem': 'endpoints',
            },
          );
        }

        final requestStartTime = DateTime.now();
        final dio = await DioClient.instance;
        final cacheBuster = DateTime.now().millisecondsSinceEpoch;
        final requestUrl = '$endpoint/forum/index.php';

        await logger.log(
          level: 'debug',
          subsystem: 'endpoints',
          message: 'Health check HTTP request',
          operationId: operationId,
          context: 'health_check',
          extra: {
            'url': requestUrl,
            'method': 'GET',
            'query_params': {'_': cacheBuster},
            'headers': {
              'User-Agent': userAgent,
              'Accept':
                  'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
              'Accept-Language': 'ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7',
              'Accept-Encoding': 'gzip, deflate, br',
              'Upgrade-Insecure-Requests': '1',
              'Cache-Control': 'no-cache',
              'Referer': '$endpoint/',
            },
            'attempt': attempt + 1,
            if (dnsResult != null)
              'dns_lookup': {
                'success': dnsResult.success,
                'ip_addresses': dnsResult.ipAddresses,
                'resolve_time_ms': dnsResult.resolveTime.inMilliseconds,
              },
          },
        );

        final response = await dio.get(
          requestUrl,
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
        final rtt = DateTime.now().difference(requestStartTime).inMilliseconds;

        // Treat Cloudflare challenge (403 with CF headers) as reachable
        final status = response.statusCode ?? 0;
        final headers = response.headers;
        final serverHeader = headers.value('server') ?? '';
        final cfRayHeader = headers.value('cf-ray') ?? '';

        final isCloudflare =
            (serverHeader.toLowerCase().contains('cloudflare')) ||
                headers.map.keys.any((k) => k.toLowerCase() == 'cf-ray');

        // Detect CF challenge by response body text as well
        final body = response.data is String
            ? (response.data as String).toLowerCase()
            : '';
        final bodySize =
            response.data is String ? (response.data as String).length : 0;

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
        var newHealthScore =
            _calculateHealthScore(rtt, isHealthy ? 200 : status);
        if ((status == 403 || status == 503) && (isCloudflare || looksLikeCf)) {
          // Cloudflare challenge: mirror is working, just protected
          // Give it a reasonable health score (60-80) to indicate it's usable
          newHealthScore = (newHealthScore - 20).clamp(60, 85);
        }

        // Log response details
        await logger.log(
          level: 'debug',
          subsystem: 'endpoints',
          message: 'Health check HTTP response received',
          operationId: operationId,
          context: 'health_check',
          durationMs: rtt,
          extra: {
            'url': requestUrl,
            'status_code': status,
            'response_size_bytes': bodySize,
            'rtt_ms': rtt,
            'headers': {
              'server': serverHeader,
              'cf-ray': cfRayHeader,
            },
            'is_cloudflare': isCloudflare,
            'looks_like_cf': looksLikeCf,
            'attempt': attempt + 1,
            if (dnsResult != null)
              'dns_lookup': {
                'success': dnsResult.success,
                'ip_addresses': dnsResult.ipAddresses,
                'resolve_time_ms': dnsResult.resolveTime.inMilliseconds,
                'ip_count': dnsResult.ipAddresses.length,
              },
          },
        );

        final oldHealthScore = healthScore;
        final oldEnabled = enabled;
        final oldFailureCount = failureCount;

        endpoints[endpointIndex].addAll({
          'rtt': rtt,
          'last_ok': DateTime.now().toIso8601String(),
          'signature_ok': _validateSignature(headers),
          'enabled': true,
          'health_score': newHealthScore,
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

        // Log success with state transition
        final totalDuration =
            DateTime.now().difference(startTime).inMilliseconds;

        // Use state subsystem for state transitions
        await logger.log(
          level: 'info',
          subsystem: 'state',
          message: 'Health check completed - state transition',
          operationId: operationId,
          context: 'health_check',
          durationMs: totalDuration,
          stateBefore: {
            'health_score': oldHealthScore,
            'enabled': oldEnabled,
            'failure_count': oldFailureCount,
          },
          stateAfter: {
            'health_score': newHealthScore,
            'enabled': true,
            'failure_count': 0,
          },
          extra: {
            'url': endpoint,
            'rtt_ms': rtt,
            'status': status,
            'health_score': newHealthScore,
            'attempt': attempt + 1,
            'response_size_bytes': bodySize,
            'headers': {
              'server': serverHeader,
              'cf-ray': cfRayHeader,
            },
            'is_cloudflare_detected': isCloudflare || looksLikeCf,
            'original_subsystem': 'endpoints',
            'state_change_reason': 'health_check_success',
          },
        );
        return; // Success, exit retry loop
      } on DioException catch (e) {
        lastException = e;
        final attemptDuration =
            DateTime.now().difference(attemptStartTime).inMilliseconds;

        // DNS errors are not retryable - mark as unavailable immediately
        final isDnsError = e.type == DioExceptionType.connectionError;
        if (isDnsError) {
          final message = e.message?.toLowerCase() ?? '';
          if (message.contains('host lookup') ||
              message.contains('no address associated with hostname') ||
              message.contains('name or service not known')) {
            await logger.log(
              level: 'error',
              subsystem: 'endpoints',
              message: 'DNS lookup failed for endpoint',
              operationId: operationId,
              context: 'health_check',
              durationMs: attemptDuration,
              extra: {
                'url': endpoint,
                'error': e.message,
                'error_type': e.type.toString(),
                'attempt': attempt + 1,
                'stack_trace': e.stackTrace.toString(),
              },
            );
            // DNS error, not retryable - break immediately
            break;
          }
        }

        // Check if this is a retryable error
        final isRetryable = _isRetryableError(e);

        // Log error details
        final errorType = switch (e.type) {
          DioExceptionType.connectionTimeout => 'Connection timeout',
          DioExceptionType.sendTimeout => 'Send timeout',
          DioExceptionType.receiveTimeout => 'Receive timeout',
          DioExceptionType.badResponse => 'Bad response',
          DioExceptionType.cancel => 'Request cancelled',
          DioExceptionType.connectionError => 'Connection error',
          DioExceptionType.badCertificate => 'Bad certificate',
          DioExceptionType.unknown => 'Unknown error',
        };

        await logger.log(
          level: 'warning',
          subsystem: 'endpoints',
          message: 'Health check attempt failed',
          operationId: operationId,
          context: 'health_check',
          durationMs: attemptDuration,
          extra: {
            'url': endpoint,
            'attempt': attempt + 1,
            'error_type': errorType,
            'error_message': e.message,
            'status_code': e.response?.statusCode,
            'is_retryable': isRetryable,
            'stack_trace': e.stackTrace.toString(),
          },
        );

        if (!isRetryable || attempt >= _maxRetries) {
          // Not retryable or max retries reached, break loop and handle error
          break;
        }

        // Wait before retry with exponential backoff
        final retryDelayMs = _retryDelay.inMilliseconds * (attempt + 1);
        await logger.log(
          level: 'debug',
          subsystem: 'retry',
          message: 'Health check retryable error, retrying with linear backoff',
          operationId: operationId,
          context: 'health_check',
          extra: {
            'url': endpoint,
            'attempt': attempt + 1,
            'max_retries': _maxRetries,
            'error_type': errorType,
            'retry_delay_ms': retryDelayMs,
            'backoff_type': 'linear',
            'backoff_formula': 'retry_delay * (attempt + 1)',
            'base_retry_delay_ms': _retryDelay.inMilliseconds,
            'original_subsystem': 'endpoints',
          },
        );
        await Future.delayed(Duration(milliseconds: retryDelayMs));
        continue;
      }
    }

    // If we reach here, all retries failed or error is not retryable
    final totalDuration = DateTime.now().difference(startTime).inMilliseconds;

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
      final newHealthScore = shouldDisable
          ? 0
          : (endpoints[endpointIndex]['health_score'] as int? ?? 0)
              .clamp(0, 40);
      final newEnabled = !shouldDisable;

      endpoints[endpointIndex].addAll({
        'enabled': newEnabled,
        'health_score': newHealthScore,
        'failure_count': newFailureCount,
        'last_failure': DateTime.now().toIso8601String(),
        'last_error': '$errorType: ${e.message ?? e.toString()}',
        'cooldown_until': shouldDisable ? cooldown?.toIso8601String() : null,
      });
      await _endpointsRef.put(_db, {'endpoints': endpoints});

      // Check if entering cooldown
      final wasInCooldown = endpoints[endpointIndex]['cooldown_until'] != null;
      final enteringCooldown =
          shouldDisable && cooldown != null && !wasInCooldown;

      // Log cooldown entry if applicable
      // enteringCooldown already ensures cooldown != null
      if (enteringCooldown) {
        final cooldownValue = cooldown;
        final cooldownDuration = cooldownValue.difference(DateTime.now());
        await logger.log(
          level: 'warning',
          subsystem: 'state',
          message: 'Endpoint entering cooldown period',
          operationId: operationId,
          context: 'health_check',
          durationMs: totalDuration,
          stateBefore: {
            'health_score': healthScore,
            'enabled': enabled,
            'failure_count': failureCount,
            'cooldown_until': null,
          },
          stateAfter: {
            'health_score': newHealthScore,
            'enabled': newEnabled,
            'failure_count': newFailureCount,
            'cooldown_until': cooldownValue.toIso8601String(),
          },
          extra: {
            'url': endpoint,
            'failure_count': newFailureCount,
            'cooldown_until': cooldownValue.toIso8601String(),
            'cooldown_duration_minutes': cooldownDuration.inMinutes,
            'error_type': errorType,
            'reason': isDnsError ? 'DNS lookup failed' : 'Multiple failures',
            'original_subsystem': 'endpoints',
          },
        );
      }

      // Log failure with detailed context and state transition
      await logger.log(
        level: isDnsError ? 'error' : 'warning',
        subsystem: enteringCooldown ? 'state' : 'endpoints',
        message: isDnsError
            ? 'Health check failed: DNS lookup error'
            : 'Health check failed',
        operationId: operationId,
        context: 'health_check',
        durationMs: totalDuration,
        cause: '$errorType: ${e.message ?? e.toString()}',
        stateBefore: {
          'health_score': healthScore,
          'enabled': enabled,
          'failure_count': failureCount,
        },
        stateAfter: {
          'health_score': newHealthScore,
          'enabled': newEnabled,
          'failure_count': newFailureCount,
        },
        extra: {
          'url': endpoint,
          'failure_count': newFailureCount,
          'cooldown_until': cooldown?.toIso8601String(),
          'entering_cooldown': enteringCooldown,
          'error_type': errorType,
          'response_status': e.response?.statusCode,
          'retries_attempted': lastAttempt + 1,
          'is_dns_error': isDnsError,
          'disabled': shouldDisable,
          'stack_trace': e.stackTrace.toString(),
        },
      );

      // Notify if this was the active mirror and it's now disabled
      if (shouldDisable) {
        final record = await _endpointsRef.get(_db);
        final activeUrl = record?[_activeUrlKey] as String?;
        if (activeUrl == endpoint) {
          await logger.log(
            level: 'error',
            subsystem: 'endpoints',
            message: 'Active mirror disabled due to failures',
            operationId: operationId,
            context: 'health_check',
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
      final newHealthScore = newFailureCount >= 2
          ? 0
          : (endpoints[endpointIndex]['health_score'] as int? ?? 0)
              .clamp(0, 40);
      final newEnabled = newFailureCount < 2;

      endpoints[endpointIndex].addAll({
        'enabled': newEnabled,
        'health_score': newHealthScore,
        'failure_count': newFailureCount,
        'last_failure': DateTime.now().toIso8601String(),
        'last_error': e.toString(),
        'cooldown_until':
            newFailureCount >= 2 ? cooldown?.toIso8601String() : null,
      });
      await _endpointsRef.put(_db, {'endpoints': endpoints});

      // Check if entering cooldown
      final wasInCooldown = endpoints[endpointIndex]['cooldown_until'] != null;
      final enteringCooldown =
          newFailureCount >= 2 && cooldown != null && !wasInCooldown;

      // Log cooldown entry if applicable
      // enteringCooldown already ensures cooldown != null
      if (enteringCooldown) {
        final cooldownValue = cooldown;
        final cooldownDuration = cooldownValue.difference(DateTime.now());
        await logger.log(
          level: 'warning',
          subsystem: 'state',
          message: 'Endpoint entering cooldown period',
          operationId: operationId,
          context: 'health_check',
          durationMs: totalDuration,
          stateBefore: {
            'health_score': healthScore,
            'enabled': enabled,
            'failure_count': failureCount,
            'cooldown_until': null,
          },
          stateAfter: {
            'health_score': newHealthScore,
            'enabled': newEnabled,
            'failure_count': newFailureCount,
            'cooldown_until': cooldownValue.toIso8601String(),
          },
          extra: {
            'url': endpoint,
            'failure_count': newFailureCount,
            'cooldown_until': cooldownValue.toIso8601String(),
            'cooldown_duration_minutes': cooldownDuration.inMinutes,
            'reason': 'Multiple failures',
            'original_subsystem': 'endpoints',
          },
        );
      }

      // Log failure with state transition
      await logger.log(
        level: 'warning',
        subsystem: enteringCooldown ? 'state' : 'endpoints',
        message: 'Health check failed',
        operationId: operationId,
        context: 'health_check',
        durationMs: totalDuration,
        cause: e.toString(),
        stateBefore: {
          'health_score': healthScore,
          'enabled': enabled,
          'failure_count': failureCount,
        },
        stateAfter: {
          'health_score': newHealthScore,
          'enabled': newEnabled,
          'failure_count': newFailureCount,
        },
        extra: {
          'url': endpoint,
          'failure_count': newFailureCount,
          'cooldown_until': cooldown?.toIso8601String(),
          'entering_cooldown': enteringCooldown,
          'stack_trace':
              (e is Error) ? (e as Error).stackTrace.toString() : null,
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
    final operationId = 'quick_check_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    // Check cache first
    final cached = _quickCheckCache[endpoint];
    if (cached != null) {
      final timestamp = cached['timestamp'] as DateTime;
      final result = cached['result'] as bool;
      final age = DateTime.now().difference(timestamp);

      if (age < _quickCheckCacheTTL) {
        await logger.log(
          level: 'debug',
          subsystem: 'cache',
          message: 'Using cached quick availability check result',
          operationId: operationId,
          context: 'quick_availability_check',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
          extra: {
            'url': endpoint,
            'result': result,
            'age_seconds': age.inSeconds,
            'cache_used': true,
            'cache_ttl_seconds': _quickCheckCacheTTL.inSeconds,
            'original_subsystem': 'endpoints',
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

      // Perform DNS lookup before HTTP request
      final endpointUri = Uri.parse(endpoint);
      final host = endpointUri.host;
      DnsLookupResult? dnsResult;

      try {
        dnsResult = await dnsLookup(
          host,
          operationId: operationId,
        );

        await logger.log(
          level: 'debug',
          subsystem: 'network',
          message: 'DNS lookup completed for quick availability check',
          operationId: operationId,
          context: 'quick_availability_check',
          extra: {
            'host': host,
            'dns_success': dnsResult.success,
            'ip_addresses': dnsResult.ipAddresses,
            'resolve_time_ms': dnsResult.resolveTime.inMilliseconds,
            'original_subsystem': 'endpoints',
          },
        );
      } on Exception catch (e) {
        await logger.log(
          level: 'warning',
          subsystem: 'network',
          message: 'DNS lookup failed before quick availability check',
          operationId: operationId,
          context: 'quick_availability_check',
          cause: e.toString(),
          extra: {
            'host': host,
            'note': 'Continuing with HTTP request anyway',
            'original_subsystem': 'endpoints',
          },
        );
      }

      final requestStartTime = DateTime.now();
      final dioInstance = Dio(BaseOptions(
        connectTimeout: const Duration(seconds: 5),
        receiveTimeout: const Duration(seconds: 5),
      ));

      final requestUrl = '$endpoint/forum/index.php';

      await logger.log(
        level: 'debug',
        subsystem: 'endpoints',
        message: 'Quick availability check started',
        operationId: operationId,
        context: 'quick_availability_check',
        extra: {
          'url': requestUrl,
          'method': 'GET',
          'cache_used': false,
          if (dnsResult != null)
            'dns_lookup': {
              'success': dnsResult.success,
              'ip_addresses': dnsResult.ipAddresses,
              'resolve_time_ms': dnsResult.resolveTime.inMilliseconds,
            },
        },
      );

      final response = await dioInstance
          .get(
            requestUrl,
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

      final requestDuration =
          DateTime.now().difference(requestStartTime).inMilliseconds;

      // Check for CloudFlare challenge - endpoint is reachable but protected
      final status = response.statusCode ?? 0;
      final headers = response.headers;
      final serverHeader = headers.value('server') ?? '';
      final cfRayHeader = headers.value('cf-ray') ?? '';

      final isCloudflare =
          (serverHeader.toLowerCase().contains('cloudflare')) ||
              headers.map.keys.any((k) => k.toLowerCase() == 'cf-ray');

      final body = response.data is String
          ? (response.data as String).toLowerCase()
          : '';
      final bodySize =
          response.data is String ? (response.data as String).length : 0;

      final looksLikeCf = body.contains('checking your browser') ||
          body.contains('please enable javascript') ||
          body.contains('cloudflare') ||
          body.contains('just a moment');

      // Endpoint is available if:
      // 1. Status 200-399 (success/redirect)
      // 2. Status 403/503 with CloudFlare headers/body (reachable but protected)
      final isAvailable = (status >= 200 && status < 400) ||
          ((status == 403 || status == 503) && (isCloudflare || looksLikeCf));

      final totalDuration = DateTime.now().difference(startTime).inMilliseconds;

      if (isAvailable && (isCloudflare || looksLikeCf)) {
        await logger.log(
          level: 'info',
          subsystem: 'endpoints',
          message: 'Endpoint available but protected by CloudFlare',
          operationId: operationId,
          context: 'quick_availability_check',
          durationMs: totalDuration,
          extra: {
            'url': endpoint,
            'status_code': status,
            'is_cloudflare': isCloudflare,
            'looks_like_cf': looksLikeCf,
            'response_size_bytes': bodySize,
            'rtt_ms': requestDuration,
            'is_cloudflare_detected': true,
            if (dnsResult != null)
              'dns_lookup': {
                'success': dnsResult.success,
                'ip_addresses': dnsResult.ipAddresses,
                'resolve_time_ms': dnsResult.resolveTime.inMilliseconds,
                'ip_count': dnsResult.ipAddresses.length,
              },
            'headers': {
              'server': serverHeader,
              'cf-ray': cfRayHeader,
              'content-type': headers.value('content-type') ?? '',
              'content-length': headers.value('content-length') ?? '',
            },
            'all_headers': headers.map,
            'response_url': response.realUri.toString(),
            'is_redirect': response.requestOptions.uri.toString() !=
                response.realUri.toString(),
            if (response.realUri.toString() !=
                response.requestOptions.uri.toString())
              'redirect_location': response.realUri.toString(),
          },
        );
      } else {
        // Check for CloudFlare detection
        final isCloudflare = serverHeader
                .toLowerCase()
                .contains('cloudflare') ||
            cfRayHeader.isNotEmpty ||
            (response.data != null &&
                response.data.toString().contains('cf-browser-verification'));

        await logger.log(
          level: 'info',
          subsystem: 'endpoints',
          message: 'Quick availability check completed',
          operationId: operationId,
          context: 'quick_availability_check',
          durationMs: totalDuration,
          extra: {
            'url': endpoint,
            'status_code': status,
            'is_available': isAvailable,
            'response_size_bytes': bodySize,
            'rtt_ms': requestDuration,
            'is_cloudflare_detected': isCloudflare,
            if (dnsResult != null)
              'dns_lookup': {
                'success': dnsResult.success,
                'ip_addresses': dnsResult.ipAddresses,
                'resolve_time_ms': dnsResult.resolveTime.inMilliseconds,
                'ip_count': dnsResult.ipAddresses.length,
              },
            'headers': {
              'server': serverHeader,
              'cf-ray': cfRayHeader,
              'content-type': headers.value('content-type') ?? '',
              'content-length': headers.value('content-length') ?? '',
            },
            'all_headers': headers.map,
            'response_url': response.realUri.toString(),
            'is_redirect': response.requestOptions.uri.toString() !=
                response.realUri.toString(),
            if (response.realUri.toString() !=
                response.requestOptions.uri.toString())
              'redirect_location': response.realUri.toString(),
          },
        );
      }

      // Cache the result
      _quickCheckCache[endpoint] = {
        'result': isAvailable,
        'timestamp': DateTime.now(),
      };

      return isAvailable;
    } on DioException catch (e) {
      final totalDuration = DateTime.now().difference(startTime).inMilliseconds;

      // DNS errors indicate endpoint is unavailable
      final isDnsError = e.type == DioExceptionType.connectionError;
      if (isDnsError) {
        final message = e.message?.toLowerCase() ?? '';
        if (message.contains('host lookup') ||
            message.contains('no address associated with hostname') ||
            message.contains('name or service not known')) {
          await logger.log(
            level: 'warning',
            subsystem: 'endpoints',
            message: 'DNS lookup failed for endpoint availability check',
            operationId: operationId,
            context: 'quick_availability_check',
            durationMs: totalDuration,
            extra: {
              'url': endpoint,
              'error': e.message,
              'error_type': e.type.toString(),
              'is_dns_error': true,
              'stack_trace': e.stackTrace.toString(),
            },
          );
          // Cache negative result
          _quickCheckCache[endpoint] = {
            'result': false,
            'timestamp': DateTime.now(),
          };
          return false; // DNS error, endpoint unavailable
        }
      }

      // Log other errors
      final errorType = switch (e.type) {
        DioExceptionType.connectionTimeout => 'Connection timeout',
        DioExceptionType.receiveTimeout => 'Receive timeout',
        DioExceptionType.connectionError => 'Connection error',
        _ => 'Unknown error',
      };

      await logger.log(
        level: 'warning',
        subsystem: 'endpoints',
        message: 'Quick availability check failed',
        operationId: operationId,
        context: 'quick_availability_check',
        durationMs: totalDuration,
        extra: {
          'url': endpoint,
          'error': e.message,
          'error_type': errorType,
          'status_code': e.response?.statusCode,
          'stack_trace': e.stackTrace.toString(),
        },
      );

      // Other errors (timeouts, connection errors) might be temporary
      // Cache negative result with shorter TTL (10 seconds for failures)
      _quickCheckCache[endpoint] = {
        'result': false,
        'timestamp': DateTime.now(),
      };
      return false;
    } on Exception catch (e) {
      final totalDuration = DateTime.now().difference(startTime).inMilliseconds;

      await logger.log(
        level: 'warning',
        subsystem: 'endpoints',
        message: 'Quick availability check exception',
        operationId: operationId,
        context: 'quick_availability_check',
        durationMs: totalDuration,
        cause: e.toString(),
        extra: {
          'url': endpoint,
          'stack_trace':
              (e is Error) ? (e as Error).stackTrace.toString() : null,
        },
      );

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
    final operationId =
        'endpoint_selection_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    final record = await _endpointsRef.get(_db);
    final endpoints =
        List<Map<String, dynamic>>.from((record?['endpoints'] as List?) ?? []);

    // Log all available endpoints before selection
    final availableEndpoints = endpoints
        .map((e) => {
              'url': e['url'],
              'health_score': e['health_score'] ?? 0,
              'rtt': e['rtt'] ?? 9999,
              'priority': e['priority'] ?? 999,
              'enabled': e['enabled'] ?? false,
            })
        .toList();

    await logger.log(
      level: 'debug',
      subsystem: 'endpoints',
      message: 'Endpoint selection started',
      operationId: operationId,
      context: 'endpoint_selection',
      extra: {
        'available_endpoints': availableEndpoints,
        'total_count': endpoints.length,
      },
    );

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
            final duration =
                DateTime.now().difference(startTime).inMilliseconds;
            await logger.log(
              level: 'info',
              subsystem: 'endpoints',
              message: 'Using sticky active endpoint',
              operationId: operationId,
              context: 'endpoint_selection',
              durationMs: duration,
              extra: {
                'url': activeFromStore,
                'selection_reason': 'sticky_endpoint_available',
                'health_score': e['health_score'] ?? 0,
                'rtt': e['rtt'] ?? 9999,
                'priority': e['priority'] ?? 999,
              },
            );
            return activeFromStore;
          } else {
            // Sticky endpoint unavailable, log and continue to selection
            await logger.log(
              level: 'warning',
              subsystem: 'endpoints',
              message: 'Sticky endpoint unavailable, selecting new one',
              operationId: operationId,
              context: 'endpoint_selection',
              extra: {
                'url': activeFromStore,
                'reason': 'quick_availability_check_failed',
              },
            );
          }
        } else {
          await logger.log(
            level: 'debug',
            subsystem: 'endpoints',
            message: 'Sticky endpoint not usable',
            operationId: operationId,
            context: 'endpoint_selection',
            extra: {
              'url': activeFromStore,
              'enabled': enabled,
              'in_cooldown': inCooldown,
              'reason': enabled ? 'in_cooldown' : 'disabled',
            },
          );
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
          final oldState = {
            'enabled': e['enabled'],
            'health_score': e['health_score'] ?? 0,
            'cooldown_until': e['cooldown_until'],
          };
          e['enabled'] = true;
          e['health_score'] = (e['health_score'] as int? ?? 0).clamp(0, 40);
          e['cooldown_until'] = null;
          final cooldownDuration = DateTime.now().difference(cooldownUntil);
          await logger.log(
            level: 'info',
            subsystem: 'state',
            message: 'Cooldown expired, re-enabling mirror',
            operationId: operationId,
            context: 'endpoint_selection',
            stateBefore: oldState,
            stateAfter: {
              'enabled': true,
              'health_score': e['health_score'],
              'cooldown_until': null,
            },
            extra: {
              'url': e['url'],
              'cooldown_duration_minutes': cooldownDuration.inMinutes,
              'cooldown_started_at': cooldownUntilStr,
              'original_subsystem': 'endpoints',
            },
          );
        }
      }
    }

    // 2) Filter enabled endpoints with sufficient health. If health unknown, we will treat later as fallback
    final healthyEndpoints = endpoints
        .where((e) =>
            e['enabled'] == true && (e['health_score'] as int? ?? 0) > 50)
        .toList();

    await logger.log(
      level: 'debug',
      subsystem: 'endpoints',
      message: 'Filtered healthy endpoints',
      operationId: operationId,
      context: 'endpoint_selection',
      extra: {
        'healthy_count': healthyEndpoints.length,
        'healthy_endpoints': healthyEndpoints
            .map((e) => {
                  'url': e['url'],
                  'health_score': e['health_score'] ?? 0,
                  'rtt': e['rtt'] ?? 9999,
                  'priority': e['priority'] ?? 999,
                })
            .toList(),
      },
    );

    if (healthyEndpoints.isEmpty) {
      // Fallback to any enabled endpoint if no healthy ones
      // include unknown-health endpoints too (enabled ones)
      final fallbackEndpoints =
          endpoints.where((e) => e['enabled'] == true).toList();

      await logger.log(
        level: 'warning',
        subsystem: 'endpoints',
        message: 'No healthy endpoints found, using fallback logic',
        operationId: operationId,
        context: 'endpoint_selection',
        extra: {
          'fallback_count': fallbackEndpoints.length,
          'fallback_endpoints': fallbackEndpoints
              .map((e) => {
                    'url': e['url'],
                    'health_score': e['health_score'] ?? 0,
                    'priority': e['priority'] ?? 999,
                  })
              .toList(),
        },
      );

      if (fallbackEndpoints.isEmpty) {
        // Ultimate fallback: use primary fallback endpoint
        final hardcodedFallback = getPrimaryFallbackEndpoint();
        final duration = DateTime.now().difference(startTime).inMilliseconds;
        await logger.log(
          level: 'warning',
          subsystem: 'endpoints',
          message: 'No endpoints available, using hardcoded fallback',
          operationId: operationId,
          context: 'endpoint_selection',
          durationMs: duration,
          extra: {
            'url': hardcodedFallback,
            'selection_reason': 'hardcoded_fallback',
          },
        );
        return hardcodedFallback;
      }

      // Sort fallback by priority
      fallbackEndpoints.sort((a, b) => a['priority'].compareTo(b['priority']));
      final chosen = fallbackEndpoints.first['url'] as String;
      final chosenData = fallbackEndpoints.first;

      // Persist sticky active
      await _endpointsRef.put(_db, {
        'endpoints': endpoints,
        _activeUrlKey: chosen,
      });

      final duration = DateTime.now().difference(startTime).inMilliseconds;

      // Build detailed fallback selection criteria
      final fallbackCriteria = {
        'primary_sort': 'priority',
        'healthy_candidates': 0,
        'fallback_candidates': fallbackEndpoints.length,
        'selection_method': 'fallback_by_priority',
        'fallback_reason': 'no_healthy_endpoints',
      };

      // Add comparison with other fallback candidates if available
      Map<String, dynamic>? fallbackComparison;
      if (fallbackEndpoints.length > 1) {
        final sortedByPriority = [...fallbackEndpoints]
          ..sort((a, b) => a['priority'].compareTo(b['priority']));
        final runnerUpFallback =
            sortedByPriority.length > 1 ? sortedByPriority[1] : null;

        if (runnerUpFallback != null) {
          fallbackComparison = {
            'runner_up_url': runnerUpFallback['url'],
            'runner_up_priority': runnerUpFallback['priority'] ?? 999,
            'chosen_priority': chosenData['priority'] ?? 999,
            'priority_diff': (chosenData['priority'] ?? 999) -
                (runnerUpFallback['priority'] ?? 999),
          };
        }
      }

      await logger.log(
        level: 'warning',
        subsystem: 'endpoints',
        message: 'No healthy endpoints, using fallback',
        operationId: operationId,
        context: 'endpoint_selection',
        durationMs: duration,
        extra: {
          'url': chosen,
          'selection_reason': 'fallback_by_priority',
          'health_score': chosenData['health_score'] ?? 0,
          'priority': chosenData['priority'] ?? 999,
          'selection_criteria': fallbackCriteria,
          if (fallbackComparison != null)
            'comparison_with_runner_up': fallbackComparison,
        },
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
    final chosenData = healthyEndpoints.first;
    final runnerUp = healthyEndpoints.length > 1 ? healthyEndpoints[1] : null;

    // Persist sticky active
    await _endpointsRef.put(_db, {
      'endpoints': endpoints,
      _activeUrlKey: chosen,
    });

    final duration = DateTime.now().difference(startTime).inMilliseconds;
    await logger.log(
      level: 'info',
      subsystem: 'endpoints',
      message: 'Active endpoint selected',
      operationId: operationId,
      context: 'endpoint_selection',
      durationMs: duration,
      extra: {
        'url': chosen,
        'selection_reason': 'best_health_score',
        'health_score': chosenData['health_score'] ?? 0,
        'rtt': chosenData['rtt'] ?? 9999,
        'priority': chosenData['priority'] ?? 999,
        'selection_criteria': {
          'primary': 'health_score',
          'secondary': 'rtt',
          'tertiary': 'priority',
        },
        'selected_over': runnerUp != null
            ? {
                'url': runnerUp['url'],
                'health_score': runnerUp['health_score'] ?? 0,
                'rtt': runnerUp['rtt'] ?? 9999,
                'priority': runnerUp['priority'] ?? 999,
              }
            : null,
        'candidates_count': healthyEndpoints.length,
      },
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
    final operationId =
        'endpoint_switch_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    await logger.log(
      level: 'info',
      subsystem: 'state',
      message: 'Attempting to switch endpoint',
      operationId: operationId,
      context: 'endpoint_switch',
      extra: {
        'current_endpoint': currentEndpoint,
        'reason': 'current_endpoint_failing',
      },
    );

    final record = await _endpointsRef.get(_db);
    final endpoints =
        List<Map<String, dynamic>>.from((record?['endpoints'] as List?) ?? []);

    // Get current endpoint state before modification
    final currentIndex =
        endpoints.indexWhere((e) => e['url'] == currentEndpoint);
    Map<String, dynamic>? currentEndpointState;
    if (currentIndex != -1) {
      currentEndpointState = Map<String, dynamic>.from(endpoints[currentIndex]);
    }

    // Disable current endpoint temporarily
    if (currentIndex != -1) {
      final oldState = {
        'enabled': endpoints[currentIndex]['enabled'],
        'failure_count': endpoints[currentIndex]['failure_count'] ?? 0,
      };
      endpoints[currentIndex]['enabled'] = false;
      endpoints[currentIndex]['failure_count'] =
          (endpoints[currentIndex]['failure_count'] as int? ?? 0) + 1;
      await _updateEndpoints(endpoints);

      await logger.log(
        level: 'debug',
        subsystem: 'state',
        message: 'Disabled current endpoint before switch',
        operationId: operationId,
        context: 'endpoint_switch',
        stateBefore: oldState,
        stateAfter: {
          'enabled': false,
          'failure_count': endpoints[currentIndex]['failure_count'],
        },
        extra: {
          'url': currentEndpoint,
          'original_subsystem': 'endpoints',
        },
      );
    }

    // Try to get a new active endpoint
    try {
      final selectionStartTime = DateTime.now();
      final newEndpoint = await getActiveEndpoint();
      final selectionDuration =
          DateTime.now().difference(selectionStartTime).inMilliseconds;

      if (newEndpoint != currentEndpoint) {
        // Clear cache for both endpoints since state changed
        clearQuickCheckCache(currentEndpoint);
        clearQuickCheckCache(newEndpoint);

        // Sync cookies to new endpoint
        final syncStartTime = DateTime.now();
        try {
          await DioClient.syncCookiesOnEndpointSwitch(
            currentEndpoint,
            newEndpoint,
          );
          final syncDuration =
              DateTime.now().difference(syncStartTime).inMilliseconds;

          await logger.log(
            level: 'debug',
            subsystem: 'cookies',
            message: 'Cookies synced on endpoint switch',
            operationId: operationId,
            context: 'endpoint_switch',
            durationMs: syncDuration,
            extra: {
              'old_endpoint': currentEndpoint,
              'new_endpoint': newEndpoint,
              'original_subsystem': 'endpoints',
            },
          );
        } on Exception catch (e) {
          final syncDuration =
              DateTime.now().difference(syncStartTime).inMilliseconds;
          await logger.log(
            level: 'warning',
            subsystem: 'cookies',
            message: 'Failed to sync cookies on endpoint switch',
            operationId: operationId,
            context: 'endpoint_switch',
            durationMs: syncDuration,
            cause: e.toString(),
            extra: {
              'old_endpoint': currentEndpoint,
              'new_endpoint': newEndpoint,
              'original_subsystem': 'endpoints',
            },
          );
        }

        final totalDuration =
            DateTime.now().difference(startTime).inMilliseconds;

        // Get new endpoint state for logging
        final newIndex = endpoints.indexWhere((e) => e['url'] == newEndpoint);
        final newEndpointState = newIndex != -1
            ? {
                'health_score': endpoints[newIndex]['health_score'] ?? 0,
                'enabled': endpoints[newIndex]['enabled'] ?? true,
                'priority': endpoints[newIndex]['priority'] ?? 999,
                'rtt': endpoints[newIndex]['rtt'] ?? 9999,
              }
            : null;

        await logger.log(
          level: 'info',
          subsystem: 'state',
          message: 'Successfully switched endpoint',
          operationId: operationId,
          context: 'endpoint_switch',
          durationMs: totalDuration,
          stateBefore: currentEndpointState != null
              ? {
                  'enabled': currentEndpointState['enabled'],
                  'health_score': currentEndpointState['health_score'] ?? 0,
                  'failure_count': currentEndpointState['failure_count'] ?? 0,
                }
              : null,
          stateAfter: newEndpointState != null
              ? {
                  'enabled': newEndpointState['enabled'],
                  'health_score': newEndpointState['health_score'],
                }
              : null,
          extra: {
            'old_endpoint': currentEndpoint,
            'new_endpoint': newEndpoint,
            'selection_duration_ms': selectionDuration,
            if (newEndpointState != null)
              'new_endpoint_state': newEndpointState,
            'switch_reason': 'automatic_failure_recovery',
            'original_subsystem': 'endpoints',
          },
        );
        return true;
      } else {
        // Same endpoint selected
        final totalDuration =
            DateTime.now().difference(startTime).inMilliseconds;
        await logger.log(
          level: 'warning',
          subsystem: 'state',
          message: 'Endpoint switch attempted but same endpoint selected',
          operationId: operationId,
          context: 'endpoint_switch',
          durationMs: totalDuration,
          extra: {
            'current_endpoint': currentEndpoint,
            'selected_endpoint': newEndpoint,
            'selection_duration_ms': selectionDuration,
            'switch_reason': 'no_alternative_available',
            'original_subsystem': 'endpoints',
          },
        );
        return false;
      }
    } on Exception catch (e) {
      final totalDuration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'state',
        message: 'Failed to switch endpoint - exception during selection',
        operationId: operationId,
        context: 'endpoint_switch',
        durationMs: totalDuration,
        cause: e.toString(),
        extra: {
          'current_endpoint': currentEndpoint,
          'switch_reason': 'selection_exception',
          'stack_trace':
              (e is Error) ? (e as Error).stackTrace.toString() : null,
          'original_subsystem': 'endpoints',
        },
      );
      return false;
    }
  }
}
