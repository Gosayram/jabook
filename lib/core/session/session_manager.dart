import 'dart:async';

import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart';
import 'package:jabook/core/auth/credential_manager.dart';
import 'package:jabook/core/auth/rutracker_auth.dart';
import 'package:jabook/core/endpoints/endpoint_manager.dart';
import 'package:jabook/core/errors/failures.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/session/cookie_sync_service.dart';
import 'package:jabook/core/session/session_storage.dart';
import 'package:jabook/core/session/session_validator.dart';
import 'package:jabook/data/db/app_database.dart';

/// Centralized manager for session and cookie management.
///
/// This class provides a unified interface for managing user sessions,
/// including saving, restoring, validating, and synchronizing cookies
/// between WebView and Dio client.
class SessionManager {
  /// Creates a new SessionManager instance.
  ///
  /// The [rutrackerAuth] parameter is optional and will be created if not provided.
  SessionManager({
    RuTrackerAuth? rutrackerAuth,
  })  : _rutrackerAuth = rutrackerAuth,
        _sessionStorage = const SessionStorage(),
        _sessionValidator = const SessionValidator(),
        _cookieSyncService = const CookieSyncService(),
        _credentialManager = CredentialManager();

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

  /// Timer for periodic session monitoring.
  Timer? _sessionCheckTimer;

  /// Checks if the current session is valid.
  ///
  /// Returns true if session cookies are valid and authentication is active,
  /// false otherwise.
  ///
  /// Throws [AuthFailure] if validation fails due to network errors.
  Future<bool> isSessionValid() async {
    final operationId =
        'check_session_valid_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

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

      final db = AppDatabase().database;
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

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: isValid ? 'info' : 'warning',
        subsystem: 'session_manager',
        message: 'Session validity check ${isValid ? "passed" : "failed"}',
        operationId: operationId,
        context: 'session_validation',
        durationMs: duration,
        extra: {'is_valid': isValid},
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
    final operationId =
        'save_session_${DateTime.now().millisecondsSinceEpoch}';
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

      final duration = DateTime.now().difference(startTime).inMilliseconds;
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
          'https://rutracker.net';

      // Restore to Dio cookie jar
      final dio = await DioClient.instance;
      final cookieJar = await _getCookieJar(dio);
      final uri = Uri.parse(endpoint);
      await cookieJar.saveFromResponse(uri, cookies);

      // Sync to WebView storage
      await _cookieSyncService.syncFromDioToWebView(cookieJar);

      final duration = DateTime.now().difference(startTime).inMilliseconds;
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
        await logger.log(
          level: 'warning',
          subsystem: 'session_manager',
          message: 'No stored credentials available for session refresh',
          operationId: operationId,
          context: 'session_refresh',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
        );
        return false;
      }

      // Re-authenticate using stored credentials
      if (_rutrackerAuth == null) {
        await logger.log(
          level: 'warning',
          subsystem: 'session_manager',
          message: 'RuTrackerAuth not available for session refresh',
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

      final success = await _rutrackerAuth.loginViaHttp(username, password);

      final duration = DateTime.now().difference(startTime).inMilliseconds;
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
  /// and WebView storage.
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

      // Clear Dio cookie jar
      final dio = await DioClient.instance;
      final cookieJar = await _getCookieJar(dio);
      await cookieJar.deleteAll();

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

  /// Starts periodic monitoring of session validity.
  ///
  /// The [interval] parameter specifies how often to check session validity.
  /// Defaults to 5 minutes.
  void startSessionMonitoring({Duration interval = const Duration(minutes: 5)}) {
    _sessionCheckTimer?.cancel();
    _sessionCheckTimer = Timer.periodic(interval, (_) async {
      try {
        final isValid = await isSessionValid();
        if (!isValid) {
          await refreshSessionIfNeeded();
        }
      } on Exception {
        // Ignore errors in background monitoring
      }
    });
  }

  /// Stops periodic session monitoring.
  void stopSessionMonitoring() {
    _sessionCheckTimer?.cancel();
    _sessionCheckTimer = null;
  }

  /// Gets the cookie jar from Dio instance.
  Future<CookieJar> _getCookieJar(Dio dio) async {
    // Find CookieManager interceptor
    final cookieInterceptors = dio.interceptors
        .whereType<CookieManager>()
        .toList();
    if (cookieInterceptors.isNotEmpty) {
      return cookieInterceptors.first.cookieJar;
    }
    // If no cookie manager found, create a new one
    final cookieJar = CookieJar();
    dio.interceptors.add(CookieManager(cookieJar));
    return cookieJar;
  }

  /// Disposes resources held by this manager.
  void dispose() {
    stopSessionMonitoring();
  }
}

