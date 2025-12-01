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

import 'dart:async';

import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart';
import 'package:jabook/core/auth/credential_manager.dart';
import 'package:jabook/core/auth/rutracker_auth.dart';
import 'package:jabook/core/cache/rutracker_cache_service.dart';
import 'package:jabook/core/data/local/database/app_database.dart';
import 'package:jabook/core/infrastructure/endpoints/endpoint_manager.dart';
import 'package:jabook/core/infrastructure/errors/failures.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/session/cookie_sync_service.dart';
import 'package:jabook/core/session/session_storage.dart';
import 'package:jabook/core/session/session_validator.dart';
import 'package:jabook/core/utils/notification_utils.dart'
    as notification_utils;

/// Centralized manager for session and cookie management.
///
/// This class provides a unified interface for managing user sessions,
/// including saving, restoring, validating, and synchronizing cookies
/// between WebView and Dio client.
///
/// This class can be instantiated directly or through a provider.
/// For dependency injection, use [sessionManagerProvider] from
/// [core/di/providers/auth_infrastructure_providers.dart].
class SessionManager {
  /// Creates a new SessionManager instance.
  ///
  /// The [rutrackerAuth] parameter is optional but recommended for full functionality.
  /// The [appDatabase] parameter is optional - if not provided, will use AppDatabase() directly.
  /// For dependency injection, prefer using [sessionManagerProvider].
  SessionManager({
    RuTrackerAuth? rutrackerAuth,
    CredentialManager? credentialManager,
    AppDatabase? appDatabase,
  })  : _rutrackerAuth = rutrackerAuth,
        _sessionStorage = const SessionStorage(),
        _sessionValidator = SessionValidator(appDatabase: appDatabase),
        _cookieSyncService = const CookieSyncService(),
        _credentialManager = credentialManager ?? CredentialManager(),
        _appDatabase = appDatabase;

  /// RuTracker authentication instance.
  final RuTrackerAuth? _rutrackerAuth;

  /// Session storage for persistent cookie storage.
  final SessionStorage _sessionStorage;

  /// Session validator for checking cookie validity.
  final SessionValidator _sessionValidator;

  /// Cookie sync service for synchronizing between WebView and Dio.
  final CookieSyncService _cookieSyncService;

  /// Credential manager for accessing stored credentials.
  final CredentialManager _credentialManager;

  /// AppDatabase instance for database operations.
  final AppDatabase? _appDatabase;

  /// Timer for periodic session monitoring.
  Timer? _sessionCheckTimer;

  /// Performance metrics for session operations.
  final Map<String, List<int>> _operationMetrics = {};

  /// Cache for session validation results to avoid excessive network requests.
  DateTime? _lastValidationTime;
  bool? _cachedValidationResult;

  /// TTL for validation cache (5 minutes).
  static const Duration _validationCacheTTL = Duration(minutes: 5);

  /// Gets performance metrics for session operations.
  ///
  /// Returns a map with average, min, max, and count for each operation type.
  Map<String, Map<String, dynamic>> getPerformanceMetrics() {
    final metrics = <String, Map<String, dynamic>>{};
    for (final entry in _operationMetrics.entries) {
      final durations = entry.value;
      if (durations.isEmpty) continue;

      durations.sort();
      final sum = durations.fold<int>(0, (a, b) => a + b);
      final avg = sum / durations.length;

      metrics[entry.key] = {
        'count': durations.length,
        'avgMs': avg.round(),
        'minMs': durations.first,
        'maxMs': durations.last,
        'p50Ms': durations[durations.length ~/ 2],
        'p95Ms': durations[(durations.length * 0.95).round()],
        'p99Ms': durations[(durations.length * 0.99).round()],
      };
    }
    return metrics;
  }

  /// Records a performance metric for an operation.
  void _recordMetric(String operation, int durationMs) {
    _operationMetrics.putIfAbsent(operation, () => []).add(durationMs);
    // Keep only last 100 measurements per operation to avoid memory issues
    final metrics = _operationMetrics[operation]!;
    if (metrics.length > 100) {
      metrics.removeRange(0, metrics.length - 100);
    }
  }

  /// Checks if the current session is valid.
  ///
  /// Returns true if session cookies are valid and authentication is active,
  /// false otherwise.
  ///
  /// The [force] parameter, when true, bypasses cache and forces a fresh validation.
  ///
  /// Throws [AuthFailure] if validation fails due to network errors.
  Future<bool> isSessionValid({bool force = false}) async {
    final operationId =
        'check_session_valid_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    // Check cache first to avoid excessive network requests
    if (!force &&
        _cachedValidationResult != null &&
        _lastValidationTime != null &&
        DateTime.now().difference(_lastValidationTime!) < _validationCacheTTL) {
      await logger.log(
        level: 'debug',
        subsystem: 'session_manager',
        message: 'Using cached session validation result',
        operationId: operationId,
        context: 'session_validation',
        extra: {
          'cached_result': _cachedValidationResult,
          'cache_age_seconds':
              DateTime.now().difference(_lastValidationTime!).inSeconds,
        },
      );
      return _cachedValidationResult!;
    }

    try {
      await logger.log(
        level: 'debug',
        subsystem: 'session_manager',
        message: 'Checking session validity',
        operationId: operationId,
        context: 'session_validation',
      );

      final dio = await DioClient.instance;
      final cookieJar = await _getCookieJar(dio);

      final appDb = _appDatabase ?? AppDatabase.getInstance();
      final db = await appDb.ensureInitialized();
      final endpointManager = EndpointManager(db);
      final activeBase = await endpointManager.getActiveEndpoint();
      final uri = Uri.parse(activeBase);

      // Check if cookies exist
      final hasCookies = await _sessionValidator.hasCookies(cookieJar, uri);
      if (!hasCookies) {
        await logger.log(
          level: 'debug',
          subsystem: 'session_manager',
          message: 'No cookies found in cookie jar',
          operationId: operationId,
          context: 'session_validation',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
        );
        return false;
      }

      // Validate cookies by making a test request
      final isValid = await _sessionValidator.validateCookies(dio, cookieJar);

      // Cache the validation result
      _cachedValidationResult = isValid;
      _lastValidationTime = DateTime.now();

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      _recordMetric('isSessionValid', duration);
      await logger.log(
        level: isValid ? 'info' : 'warning',
        subsystem: 'session_manager',
        message: 'Session validity check ${isValid ? "passed" : "failed"}',
        operationId: operationId,
        context: 'session_validation',
        durationMs: duration,
        extra: {'is_valid': isValid, 'cached': false},
      );

      return isValid;
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'session_manager',
        message: 'Session validity check failed with error',
        operationId: operationId,
        context: 'session_validation',
        durationMs: duration,
        cause: e.toString(),
      );
      rethrow;
    }
  }

  /// Saves session cookies after successful authentication.
  ///
  /// The [cookies] parameter is a list of cookies to save.
  /// The [endpoint] parameter is the active endpoint URL.
  ///
  /// This method saves cookies to both secure storage and Dio cookie jar,
  /// and synchronizes them with WebView storage.
  ///
  /// Throws [AuthFailure] if saving fails.
  Future<void> saveSessionCookies(
    List<Cookie> cookies,
    String endpoint,
  ) async {
    final operationId = 'save_session_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      await logger.log(
        level: 'info',
        subsystem: 'session_manager',
        message: 'Saving session cookies',
        operationId: operationId,
        context: 'session_management',
        extra: {
          'cookie_count': cookies.length,
          'endpoint': endpoint,
        },
      );

      // Save to secure storage
      await _sessionStorage.saveCookies(cookies, endpoint);

      // Save to Dio cookie jar
      final dio = await DioClient.instance;
      final cookieJar = await _getCookieJar(dio);
      final uri = Uri.parse(endpoint);
      await cookieJar.saveFromResponse(uri, cookies);

      // Sync to WebView storage
      await _cookieSyncService.syncFromDioToWebView(cookieJar);

      // Invalidate validation cache when saving new session
      _cachedValidationResult = null;
      _lastValidationTime = null;

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      _recordMetric('saveSessionCookies', duration);
      await logger.log(
        level: 'info',
        subsystem: 'session_manager',
        message: 'Session cookies saved successfully',
        operationId: operationId,
        context: 'session_management',
        durationMs: duration,
        extra: {
          'cookie_count': cookies.length,
          'endpoint': endpoint,
        },
      );
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'session_manager',
        message: 'Failed to save session cookies',
        operationId: operationId,
        context: 'session_management',
        durationMs: duration,
        cause: e.toString(),
      );
      throw AuthFailure(
        'Failed to save session cookies: ${e.toString()}',
        e,
      );
    }
  }

  /// Restores session from secure storage.
  ///
  /// This method loads cookies from secure storage and restores them
  /// to both Dio cookie jar and WebView storage.
  ///
  /// Returns true if session was restored successfully, false otherwise.
  ///
  /// Throws [AuthFailure] if restoration fails.
  Future<bool> restoreSession() async {
    final operationId =
        'restore_session_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      await logger.log(
        level: 'info',
        subsystem: 'session_manager',
        message: 'Restoring session from storage',
        operationId: operationId,
        context: 'session_restore',
      );

      // Check if session exists
      final hasSession = await _sessionStorage.hasSession();
      if (!hasSession) {
        await logger.log(
          level: 'debug',
          subsystem: 'session_manager',
          message: 'No session found in storage',
          operationId: operationId,
          context: 'session_restore',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
        );
        return false;
      }

      // Load cookies from storage
      final cookies = await _sessionStorage.loadCookies();
      if (cookies == null || cookies.isEmpty) {
        await logger.log(
          level: 'debug',
          subsystem: 'session_manager',
          message: 'No cookies found in storage',
          operationId: operationId,
          context: 'session_restore',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
        );
        return false;
      }

      // Load metadata
      final metadata = await _sessionStorage.loadMetadata();
      final endpoint = metadata?['endpoint'] as String? ??
          EndpointManager.getPrimaryFallbackEndpoint();

      // Restore to Dio cookie jar with error handling
      try {
        final dio = await DioClient.instance;
        final cookieJar = await _getCookieJar(dio);
        final uri = Uri.parse(endpoint);
        await cookieJar.saveFromResponse(uri, cookies);
      } on Exception catch (e) {
        await logger.log(
          level: 'warning',
          subsystem: 'session_manager',
          message:
              'Failed to restore cookies to Dio, continuing with WebView sync',
          operationId: operationId,
          context: 'session_restore',
          cause: e.toString(),
        );
        // Continue with WebView sync even if Dio restore fails
      }

      // Sync to WebView storage with error handling
      try {
        final dio = await DioClient.instance;
        final cookieJar = await _getCookieJar(dio);
        await _cookieSyncService.syncFromDioToWebView(cookieJar);
      } on Exception catch (e) {
        await logger.log(
          level: 'warning',
          subsystem: 'session_manager',
          message:
              'Failed to sync cookies to WebView, but session may still be valid',
          operationId: operationId,
          context: 'session_restore',
          cause: e.toString(),
        );
        // Don't fail restoration if WebView sync fails
      }

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      _recordMetric('restoreSession', duration);
      await logger.log(
        level: 'info',
        subsystem: 'session_manager',
        message: 'Session restored successfully',
        operationId: operationId,
        context: 'session_restore',
        durationMs: duration,
        extra: {
          'cookie_count': cookies.length,
          'endpoint': endpoint,
        },
      );

      return true;
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'session_manager',
        message: 'Failed to restore session',
        operationId: operationId,
        context: 'session_restore',
        durationMs: duration,
        cause: e.toString(),
      );
      throw AuthFailure(
        'Failed to restore session: ${e.toString()}',
        e,
      );
    }
  }

  /// Attempts to refresh the session if it has expired.
  ///
  /// This method uses stored credentials to automatically re-authenticate
  /// if the session has expired.
  ///
  /// Returns true if session was refreshed successfully, false otherwise.
  Future<bool> refreshSessionIfNeeded() async {
    final operationId =
        'refresh_session_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      await logger.log(
        level: 'debug',
        subsystem: 'session_manager',
        message: 'Attempting to refresh session',
        operationId: operationId,
        context: 'session_refresh',
      );

      // Check if session is valid
      final isValid = await isSessionValid();
      if (isValid) {
        await logger.log(
          level: 'debug',
          subsystem: 'session_manager',
          message: 'Session is still valid, no refresh needed',
          operationId: operationId,
          context: 'session_refresh',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
        );
        return true;
      }

      // Try to get stored credentials
      final credentials = await _credentialManager.getCredentials();
      if (credentials == null) {
        // This is expected if user is not logged in - log as debug, not warning
        await logger.log(
          level: 'debug',
          subsystem: 'session_manager',
          message:
              'No stored credentials available for session refresh (user not logged in)',
          operationId: operationId,
          context: 'session_refresh',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
        );
        return false;
      }

      // Re-authenticate using stored credentials
      if (_rutrackerAuth == null) {
        // This may be expected if auth is not initialized yet - log as debug
        await logger.log(
          level: 'debug',
          subsystem: 'session_manager',
          message:
              'RuTrackerAuth not available for session refresh (not initialized)',
          operationId: operationId,
          context: 'session_refresh',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
        );
        return false;
      }

      final username = credentials['username'];
      final password = credentials['password'];
      if (username == null || password == null) {
        await logger.log(
          level: 'warning',
          subsystem: 'session_manager',
          message: 'Invalid credentials format',
          operationId: operationId,
          context: 'session_refresh',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
        );
        return false;
      }

      // _rutrackerAuth is guaranteed to be non-null here due to check above
      final auth = _rutrackerAuth;
      final success = await auth.loginViaHttp(username, password);

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      _recordMetric('refreshSessionIfNeeded', duration);
      await logger.log(
        level: success ? 'info' : 'warning',
        subsystem: 'session_manager',
        message: 'Session refresh ${success ? "succeeded" : "failed"}',
        operationId: operationId,
        context: 'session_refresh',
        durationMs: duration,
        extra: {'success': success},
      );

      return success;
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'session_manager',
        message: 'Session refresh failed with error',
        operationId: operationId,
        context: 'session_refresh',
        durationMs: duration,
        cause: e.toString(),
      );
      return false;
    }
  }

  /// Clears all session data.
  ///
  /// This method clears cookies from secure storage, Dio cookie jar,
  /// WebView storage, and session-bound cache.
  ///
  /// Throws [AuthFailure] if clearing fails.
  Future<void> clearSession() async {
    final operationId =
        'clear_session_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      await logger.log(
        level: 'info',
        subsystem: 'session_manager',
        message: 'Clearing session',
        operationId: operationId,
        context: 'session_management',
      );

      // Clear secure storage
      await _sessionStorage.clear();

      // Clear validation cache
      _cachedValidationResult = null;
      _lastValidationTime = null;

      // Clear Dio cookie jar
      final dio = await DioClient.instance;
      final cookieJar = await _getCookieJar(dio);
      await cookieJar.deleteAll();

      // Clear session-bound cache
      try {
        final cacheService = RuTrackerCacheService();
        await cacheService.clearCurrentSessionCache();
      } on Exception {
        // Ignore cache clearing errors
      }

      // Stop session monitoring
      _sessionCheckTimer?.cancel();
      _sessionCheckTimer = null;

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'info',
        subsystem: 'session_manager',
        message: 'Session cleared successfully',
        operationId: operationId,
        context: 'session_management',
        durationMs: duration,
      );
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'session_manager',
        message: 'Failed to clear session',
        operationId: operationId,
        context: 'session_management',
        durationMs: duration,
        cause: e.toString(),
      );
      throw AuthFailure(
        'Failed to clear session: ${e.toString()}',
        e,
      );
    }
  }

  /// Synchronizes cookies between WebView and Dio.
  ///
  /// This method performs bidirectional synchronization to ensure
  /// cookies are consistent across both storage mechanisms.
  ///
  /// Throws [AuthFailure] if synchronization fails.
  Future<void> syncCookiesBetweenWebViewAndDio() async {
    final dio = await DioClient.instance;
    final cookieJar = await _getCookieJar(dio);
    await _cookieSyncService.syncBothWays(cookieJar);
  }

  /// Synchronizes cookies when switching between endpoints.
  ///
  /// This method ensures that compatible cookies from the old endpoint
  /// are available for the new endpoint when switching RuTracker mirrors.
  ///
  /// The [oldEndpoint] parameter is the URL of the endpoint being switched from.
  /// The [newEndpoint] parameter is the URL of the endpoint being switched to.
  ///
  /// Throws [AuthFailure] if synchronization fails.
  Future<void> syncCookiesOnEndpointSwitch(
    String oldEndpoint,
    String newEndpoint,
  ) async {
    final operationId =
        'sync_endpoint_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      await logger.log(
        level: 'info',
        subsystem: 'session_manager',
        message: 'Syncing cookies on endpoint switch',
        operationId: operationId,
        context: 'endpoint_switch',
        extra: {
          'old_endpoint': oldEndpoint,
          'new_endpoint': newEndpoint,
        },
      );

      // Use DioClient's syncCookiesOnEndpointSwitch method
      await DioClient.syncCookiesOnEndpointSwitch(oldEndpoint, newEndpoint);

      // Also sync cookies from secure storage if they exist
      final hasSession = await _sessionStorage.hasSession();
      if (hasSession) {
        final cookies = await _sessionStorage.loadCookies();
        if (cookies != null && cookies.isNotEmpty) {
          // Save cookies for new endpoint
          await saveSessionCookies(cookies, newEndpoint);
        }
      }

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      _recordMetric('syncCookiesOnEndpointSwitch', duration);
      await logger.log(
        level: 'info',
        subsystem: 'session_manager',
        message: 'Cookies synced on endpoint switch',
        operationId: operationId,
        context: 'endpoint_switch',
        durationMs: duration,
        extra: {
          'old_endpoint': oldEndpoint,
          'new_endpoint': newEndpoint,
        },
      );
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'session_manager',
        message: 'Failed to sync cookies on endpoint switch',
        operationId: operationId,
        context: 'endpoint_switch',
        durationMs: duration,
        cause: e.toString(),
        extra: {
          'old_endpoint': oldEndpoint,
          'new_endpoint': newEndpoint,
        },
      );
      throw AuthFailure(
        'Failed to sync cookies on endpoint switch: ${e.toString()}',
        e,
      );
    }
  }

  /// Starts periodic monitoring of session validity.
  ///
  /// The [interval] parameter specifies how often to check session validity.
  /// Defaults to 5 minutes. The interval is automatically adjusted based on
  /// session age to optimize battery usage:
  /// - New sessions (< 1 hour): check every 10 minutes
  /// - Medium sessions (1-20 hours): check every 5 minutes
  /// - Old sessions (> 20 hours): check every 2 minutes (more frequent before expiration)
  ///
  /// This method will automatically check session validity and refresh it
  /// if needed. It also checks for session expiration warnings.
  Future<void> startSessionMonitoring({Duration? interval}) async {
    _sessionCheckTimer?.cancel();

    // Determine optimal interval based on session age
    Duration effectiveInterval;
    if (interval != null) {
      effectiveInterval = interval;
    } else {
      try {
        final metadata = await _sessionStorage.loadMetadata();
        if (metadata != null) {
          final createdAtStr = metadata['created_at'] as String?;
          if (createdAtStr != null) {
            final createdAt = DateTime.parse(createdAtStr);
            final sessionAge = DateTime.now().difference(createdAt);

            // Adjust interval based on session age
            if (sessionAge < const Duration(hours: 1)) {
              // New session - check less frequently
              effectiveInterval = const Duration(minutes: 10);
            } else if (sessionAge < const Duration(hours: 20)) {
              // Medium session - standard interval
              effectiveInterval = const Duration(minutes: 5);
            } else {
              // Old session - check more frequently before expiration
              effectiveInterval = const Duration(minutes: 2);
            }
          } else {
            effectiveInterval = const Duration(minutes: 5);
          }
        } else {
          effectiveInterval = const Duration(minutes: 5);
        }
      } on Exception {
        effectiveInterval = const Duration(minutes: 5);
      }
    }

    _sessionCheckTimer = Timer.periodic(effectiveInterval, (_) async {
      try {
        final isValid = await isSessionValid();
        if (!isValid) {
          final logger = StructuredLogger();
          await logger.log(
            level: 'warning',
            subsystem: 'session_manager',
            message: 'Session invalid during monitoring, attempting refresh',
            context: 'session_monitoring',
            extra: {
              'check_interval_minutes': effectiveInterval.inMinutes,
            },
          );
          await refreshSessionIfNeeded();
        } else {
          // Check if session is about to expire
          await _checkSessionExpirationWarning();
        }
      } on Exception {
        // Ignore errors in background monitoring
      }
    });

    final logger = StructuredLogger();
    await logger.log(
      level: 'info',
      subsystem: 'session_manager',
      message: 'Session monitoring started',
      context: 'session_monitoring',
      extra: {
        'interval_minutes': effectiveInterval.inMinutes,
      },
    );
  }

  /// Checks if session is about to expire and logs a warning.
  ///
  /// This method checks the session metadata to determine if the session
  /// is close to expiration and logs a warning if needed.
  Future<void> _checkSessionExpirationWarning() async {
    try {
      final metadata = await _sessionStorage.loadMetadata();
      if (metadata == null) return;

      final createdAtStr = metadata['created_at'] as String?;
      if (createdAtStr == null) return;

      final createdAt = DateTime.parse(createdAtStr);
      final now = DateTime.now();
      final sessionAge = now.difference(createdAt);

      // RuTracker sessions typically last 24 hours
      // Warn if session is older than 20 hours
      const warningThreshold = Duration(hours: 20);
      const expirationThreshold = Duration(hours: 24);

      if (sessionAge > expirationThreshold) {
        final logger = StructuredLogger();
        await logger.log(
          level: 'warning',
          subsystem: 'session_manager',
          message: 'Session may have expired (older than 24 hours)',
          context: 'session_monitoring',
          extra: {
            'session_age_hours': sessionAge.inHours,
            'created_at': createdAtStr,
          },
        );

        // Show system notification about expired session
        await notification_utils.showSimpleNotification(
          title: 'Сессия истекла',
          body: 'Ваша сессия RuTracker истекла. Пожалуйста, войдите снова.',
          channelId: 'session_expired',
        );
      } else if (sessionAge > warningThreshold) {
        final hoursUntilExpiration = (expirationThreshold - sessionAge).inHours;
        final logger = StructuredLogger();
        await logger.log(
          level: 'info',
          subsystem: 'session_manager',
          message: 'Session expiration warning: session is older than 20 hours',
          context: 'session_monitoring',
          extra: {
            'session_age_hours': sessionAge.inHours,
            'hours_until_expiration': hoursUntilExpiration,
            'created_at': createdAtStr,
          },
        );

        // Show system notification warning about upcoming expiration
        // Show warning if less than 4 hours until expiration
        if (hoursUntilExpiration <= 4) {
          // Show warning if less than 4 hours until expiration
          await notification_utils.showSimpleNotification(
            title: 'Сессия скоро истечет',
            body:
                'Ваша сессия RuTracker истечет через $hoursUntilExpiration ${hoursUntilExpiration == 1 ? 'час' : 'часа'}. Рекомендуется войти снова.',
            channelId: 'session_warning',
          );
        }
      }
    } on Exception {
      // Ignore errors when checking expiration
    }
  }

  /// Stops periodic session monitoring.
  void stopSessionMonitoring() {
    _sessionCheckTimer?.cancel();
    _sessionCheckTimer = null;
  }

  /// Gets the cookie jar from Dio instance.
  Future<CookieJar> _getCookieJar(Dio dio) async {
    // Find CookieManager interceptor
    final cookieInterceptors =
        dio.interceptors.whereType<CookieManager>().toList();
    if (cookieInterceptors.isNotEmpty) {
      return cookieInterceptors.first.cookieJar;
    }
    // If no cookie manager found, create a new one
    final cookieJar = CookieJar();
    dio.interceptors.add(CookieManager(cookieJar));
    return cookieJar;
  }

  /// Gets the current session ID.
  ///
  /// Returns the session ID if available, null otherwise.
  Future<String?> getSessionId() => _sessionStorage.getSessionId();

  /// Gets session information including age and expiration status.
  ///
  /// Returns a map with session information:
  /// - `hasSession`: whether a session exists
  /// - `isValid`: whether the session is currently valid
  /// - `ageHours`: age of the session in hours
  /// - `createdAt`: when the session was created
  /// - `endpoint`: the endpoint URL for this session
  /// - `sessionId`: the unique session identifier
  /// - `expirationWarning`: whether session is close to expiration
  ///
  /// Returns null if no session exists.
  Future<Map<String, dynamic>?> getSessionInfo() async {
    final operationId =
        'get_session_info_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      final hasSession = await _sessionStorage.hasSession();
      if (!hasSession) {
        await logger.log(
          level: 'debug',
          subsystem: 'session_manager',
          message: 'No session found for info request',
          operationId: operationId,
          context: 'session_info',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
        );
        return null;
      }

      final metadata = await _sessionStorage.loadMetadata();
      final isValid = await isSessionValid();

      final createdAtStr = metadata?['created_at'] as String?;
      DateTime? createdAt;
      int? ageHours;
      bool? expirationWarning;

      if (createdAtStr != null) {
        createdAt = DateTime.parse(createdAtStr);
        final now = DateTime.now();
        final sessionAge = now.difference(createdAt);
        ageHours = sessionAge.inHours;

        // Warn if session is older than 20 hours
        const warningThreshold = Duration(hours: 20);
        expirationWarning = sessionAge > warningThreshold;
      }

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      _recordMetric('getSessionInfo', duration);
      await logger.log(
        level: 'info',
        subsystem: 'session_manager',
        message: 'Session info retrieved',
        operationId: operationId,
        context: 'session_info',
        durationMs: duration,
        extra: {
          'has_session': true,
          'is_valid': isValid,
          'age_hours': ageHours,
          'expiration_warning': expirationWarning,
        },
      );

      final sessionId = metadata?['session_id'] as String?;

      return {
        'hasSession': true,
        'isValid': isValid,
        'ageHours': ageHours,
        'createdAt': createdAt?.toIso8601String(),
        'endpoint': metadata?['endpoint'] as String?,
        'sessionId': sessionId,
        'expirationWarning': expirationWarning ?? false,
      };
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'session_manager',
        message: 'Failed to get session info',
        operationId: operationId,
        context: 'session_info',
        durationMs: duration,
        cause: e.toString(),
      );
      return null;
    }
  }

  /// Disposes resources held by this manager.
  void dispose() {
    stopSessionMonitoring();
  }
}
