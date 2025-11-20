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
import 'package:flutter/material.dart';
import 'package:jabook/core/auth/credential_manager.dart';
import 'package:jabook/core/auth/direct_auth_service.dart';
import 'package:jabook/core/auth/simple_cookie_manager.dart';
import 'package:jabook/core/endpoints/endpoint_manager.dart';
import 'package:jabook/core/endpoints/url_constants.dart';
import 'package:jabook/core/errors/failures.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/session/session_manager.dart';
import 'package:jabook/data/db/app_database.dart';

/// Handles authentication with RuTracker forum.
///
/// This class provides methods for login, logout, and checking authentication
/// status using direct HTTP authentication (no WebView).
class RuTrackerAuth {
  /// Private constructor for singleton pattern.
  RuTrackerAuth._(this._context);

  /// Factory constructor to create a new instance of RuTrackerAuth.
  ///
  /// The [context] parameter is required for showing dialogs and navigation.
  factory RuTrackerAuth(BuildContext context) => RuTrackerAuth._(context);

  /// Simple cookie manager for storing and managing cookies.
  final SimpleCookieManager _cookieManager = SimpleCookieManager();

  /// Build context for UI operations.
  /// Currently unused but kept for potential future UI operations.
  // ignore: unused_field
  final BuildContext _context;

  /// Credential manager for secure storage.
  final CredentialManager _credentialManager = CredentialManager();

  /// Session manager for centralized session management.
  SessionManager? _sessionManager;

  /// Controller for auth status changes
  final StreamController<bool> _authStatusController =
      StreamController<bool>.broadcast();

  /// Tracks if login is currently in progress to prevent concurrent login attempts.
  bool _isLoggingIn = false;

  /// Gets or creates the session manager instance.
  SessionManager get sessionManager {
    if (_sessionManager == null) {
      try {
        // Create SessionManager with this instance
        // SessionManager is a singleton, so this is safe
        _sessionManager = SessionManager(rutrackerAuth: this);
        // Verify that the instance was created successfully
        if (_sessionManager == null) {
          throw StateError('SessionManager creation returned null');
        }
      } catch (e) {
        // If SessionManager creation fails, log and rethrow
        throw StateError('Failed to create SessionManager: $e');
      }
    }
    final manager = _sessionManager;
    if (manager == null) {
      throw StateError('SessionManager is null after creation');
    }
    return manager;
  }

  /// Attempts to log in to RuTracker via direct HTTP authentication.
  ///
  /// This method uses DirectAuthService to authenticate directly without WebView,
  /// following the same approach as the Python parser.
  ///
  /// Returns `true` if login was successful, `false` otherwise.
  Future<bool> loginViaHttp(String username, String password) async {
    final operationId = 'http_login_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    // Validate input
    if (username.trim().isEmpty || password.trim().isEmpty) {
      await logger.log(
        level: 'warning',
        subsystem: 'auth',
        message: 'Login attempt with empty credentials',
        operationId: operationId,
        context: 'http_login',
        durationMs: DateTime.now().difference(startTime).inMilliseconds,
      );
      return false;
    }

    // Prevent concurrent login attempts
    if (_isLoggingIn) {
      await logger.log(
        level: 'warning',
        subsystem: 'auth',
        message: 'Login already in progress, ignoring duplicate request',
        operationId: operationId,
        context: 'http_login',
        durationMs: DateTime.now().difference(startTime).inMilliseconds,
      );
      return false;
    }

    _isLoggingIn = true;
    try {
      // Get active endpoint (mirror)
      final db = AppDatabase().database;
      final endpointManager = EndpointManager(db);
      final activeBase = await endpointManager.getActiveEndpoint();
      final dio = await DioClient.instance;

      await logger.log(
        level: 'info',
        subsystem: 'auth',
        message: 'Direct HTTP login started',
        operationId: operationId,
        context: 'http_login',
        extra: {
          'base_url': activeBase,
          'username_length': username.length,
        },
      );

      // Use DirectAuthService for authentication
      final authService = DirectAuthService(dio);
      final authResult = await authService.authenticate(
        username,
        password,
        activeBase,
      );

      if (authResult.success && authResult.cookieString != null) {
        // Save cookie using SimpleCookieManager
        final cookieJar = await _getCookieJar(dio);
        await _cookieManager.saveCookie(
          authResult.cookieString!,
          activeBase,
          cookieJar,
        );

        // Also save via SessionManager for compatibility
        try {
          final uri = Uri.parse(activeBase);
          final cookies = await cookieJar.loadForRequest(uri);
          if (cookies.isNotEmpty) {
            await sessionManager.saveSessionCookies(cookies, activeBase);
            await sessionManager.startSessionMonitoring();
          }
        } on Exception catch (e) {
          await logger.log(
            level: 'warning',
            subsystem: 'auth',
            message: 'Failed to save cookies via SessionManager',
            operationId: operationId,
            context: 'http_login',
            cause: e.toString(),
          );
        }

        // Validate authentication
        final isValid = await _validateAuthentication(dio, activeBase, operationId);

        final totalDuration = DateTime.now().difference(startTime).inMilliseconds;
        if (isValid) {
          _authStatusController.add(true);
          await logger.log(
            level: 'info',
            subsystem: 'auth',
            message: 'Direct HTTP login successful and validated',
            operationId: operationId,
            context: 'http_login',
            durationMs: totalDuration,
          );
        } else {
          await logger.log(
            level: 'warning',
            subsystem: 'auth',
            message: 'Login appeared successful but validation failed',
            operationId: operationId,
            context: 'http_login',
            durationMs: totalDuration,
          );
        }

        return isValid;
      } else {
        // Handle authentication errors
        final errorMessage = authResult.errorMessage ?? 'Unknown error';
        final totalDuration = DateTime.now().difference(startTime).inMilliseconds;

        await logger.log(
          level: 'warning',
          subsystem: 'auth',
          message: 'Direct HTTP login failed',
          operationId: operationId,
          context: 'http_login',
          durationMs: totalDuration,
          extra: {
            'error_message': errorMessage,
          },
        );

        // Map error messages to AuthFailure
        if (errorMessage.contains('wrong username/password')) {
          throw const AuthFailure.invalidCredentials();
        } else if (errorMessage.contains('captcha')) {
          throw const AuthFailure('Site requires captcha verification');
        } else {
          throw AuthFailure('Authentication failed: $errorMessage');
        }
      }
    } on AuthFailure {
      rethrow;
    } on Exception catch (e) {
      final totalDuration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'auth',
        message: 'Direct HTTP login failed with exception',
        operationId: operationId,
        context: 'http_login',
        durationMs: totalDuration,
        cause: e.toString(),
      );
      throw AuthFailure('Login failed: ${e.toString()}');
    } finally {
      _isLoggingIn = false;
    }
  }

  /// Validates authentication by testing search and download functionality.
  ///
  /// Returns true if authentication appears to be working, false otherwise.
  /// Includes timeout protection (10 seconds total).
  Future<bool> _validateAuthentication(
    Dio dio,
    String baseUrl, [
    String? operationId,
  ]) async {
    final validationOperationId = operationId != null
        ? '${operationId}_validation'
        : 'auth_validation_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final validationStartTime = DateTime.now();

    try {
      await logger.log(
        level: 'debug',
        subsystem: 'auth',
        message: 'Authentication validation started',
        operationId: validationOperationId,
        context: 'auth_validation',
        extra: {
          'base_url': baseUrl,
        },
      );

      // Test 1: Search with cookies (with timeout)
      final searchTestStartTime = DateTime.now();
      await logger.log(
        level: 'debug',
        subsystem: 'auth',
        message: 'Validation test 1: Search',
        operationId: validationOperationId,
        context: 'auth_validation',
        extra: {
          'test': 'search',
          'url': '$baseUrl/forum/search.php',
          'query_params': {'nm': 'test', 'f': '33', 'o': '1', 'start': '0'},
        },
      );

      final searchResponse = await dio
          .get(
            '$baseUrl/forum/search.php',
            queryParameters: {'nm': 'test', 'f': '33', 'o': '1', 'start': '0'},
            options: Options(
              validateStatus: (status) => status != null && status < 500,
              receiveTimeout: const Duration(seconds: 5),
              sendTimeout: const Duration(seconds: 5),
            ),
          )
          .timeout(const Duration(seconds: 10));

      final searchTestDuration =
          DateTime.now().difference(searchTestStartTime).inMilliseconds;
      final searchBody = searchResponse.data?.toString().toLowerCase() ?? '';
      final searchBodySize = searchBody.length;
      final hasSearchResults = searchBody.contains('hl-tr') ||
          searchBody.contains('tortopic') ||
          searchBody.contains('viewtopic.php?t=');

      await logger.log(
        level: searchResponse.statusCode == 200 ? 'debug' : 'warning',
        subsystem: 'auth',
        message: 'Validation test 1: Search completed',
        operationId: validationOperationId,
        context: 'auth_validation',
        durationMs: searchTestDuration,
        extra: {
          'test': 'search',
          'status_code': searchResponse.statusCode,
          'response_size_bytes': searchBodySize,
          'has_search_results': hasSearchResults,
          'indicator_matches': {
            'hl-tr': searchBody.contains('hl-tr'),
            'tortopic': searchBody.contains('tortopic'),
            'viewtopic.php?t=': searchBody.contains('viewtopic.php?t='),
          },
        },
      );

      if (!hasSearchResults) {
        final totalDuration =
            DateTime.now().difference(validationStartTime).inMilliseconds;
        await logger.log(
          level: 'warning',
          subsystem: 'auth',
          message: 'Search validation failed - no results',
          operationId: validationOperationId,
          context: 'auth_validation',
          durationMs: totalDuration,
          extra: {
            'test': 'search',
            'status_code': searchResponse.statusCode,
            'response_size_bytes': searchBodySize,
          },
        );
        return false;
      }

      // Test 2: Try to download torrent (extract topic ID from search)
      final topicIdMatch =
          RegExp(r'viewtopic\.php\?t=(\d+)').firstMatch(searchBody);

      if (topicIdMatch != null) {
        final topicId = topicIdMatch.group(1);

        final downloadTestStartTime = DateTime.now();
        await logger.log(
          level: 'debug',
          subsystem: 'auth',
          message: 'Validation test 2: Download',
          operationId: validationOperationId,
          context: 'auth_validation',
          extra: {
            'test': 'download',
            'url': '$baseUrl/forum/dl.php',
            'topic_id': topicId,
            'query_params': {'t': topicId},
          },
        );

        final downloadResponse = await dio
            .get(
              '$baseUrl/forum/dl.php',
              queryParameters: {'t': topicId},
              options: Options(
                validateStatus: (status) => status != null && status < 500,
                receiveTimeout: const Duration(seconds: 5),
                sendTimeout: const Duration(seconds: 5),
              ),
            )
            .timeout(const Duration(seconds: 10));

        final downloadTestDuration =
            DateTime.now().difference(downloadTestStartTime).inMilliseconds;
        final downloadBodySize = downloadResponse.data is String
            ? (downloadResponse.data as String).length
            : (downloadResponse.data is List<int>
                ? (downloadResponse.data as List<int>).length
                : 0);

        // Check content type
        final contentType =
            downloadResponse.headers.value('content-type')?.toLowerCase() ?? '';

        await logger.log(
          level: 'debug',
          subsystem: 'auth',
          message: 'Validation test 2: Download completed',
          operationId: validationOperationId,
          context: 'auth_validation',
          durationMs: downloadTestDuration,
          extra: {
            'test': 'download',
            'status_code': downloadResponse.statusCode,
            'content_type': contentType,
            'response_size_bytes': downloadBodySize,
            'topic_id': topicId,
          },
        );

        // Torrent file or binary data = success
        if (contentType.contains('application/x-bittorrent') ||
            contentType.contains('application/octet-stream')) {
          final totalDuration =
              DateTime.now().difference(validationStartTime).inMilliseconds;
          await logger.log(
            level: 'info',
            subsystem: 'auth',
            message: 'Download validation succeeded - received torrent file',
            operationId: validationOperationId,
            context: 'auth_validation',
            durationMs: totalDuration,
            extra: {
              'test': 'download',
              'content_type': contentType,
              'tests': {
                'search': {
                  'duration_ms': searchTestDuration,
                  'success': true,
                },
                'download': {
                  'duration_ms': downloadTestDuration,
                  'success': true,
                },
              },
            },
          );
          return true;
        }

        final downloadBody =
            downloadResponse.data?.toString().toLowerCase() ?? '';

        // "attachment data not found" = auth works, just no file
        if (downloadBody.contains('attachment data not found') ||
            downloadBody.contains('attachment.*not.*found')) {
          final totalDuration =
              DateTime.now().difference(validationStartTime).inMilliseconds;
          await logger.log(
            level: 'info',
            subsystem: 'auth',
            message: 'Download validation - attachment not found (auth OK)',
            operationId: validationOperationId,
            context: 'auth_validation',
            durationMs: totalDuration,
            extra: {
              'test': 'download',
              'tests': {
                'search': {
                  'duration_ms': searchTestDuration,
                  'success': true,
                },
                'download': {
                  'duration_ms': downloadTestDuration,
                  'success': true,
                  'note': 'attachment_not_found_but_auth_ok',
                },
              },
            },
          );
          return hasSearchResults;
        }

        // Magnet link without torrent = not authenticated
        if (downloadBody.contains('magnet:') &&
            !downloadBody.contains('dl.php')) {
          final totalDuration =
              DateTime.now().difference(validationStartTime).inMilliseconds;
          await logger.log(
            level: 'warning',
            subsystem: 'auth',
            message:
                'Download validation failed - only magnet link (not authenticated)',
            operationId: validationOperationId,
            context: 'auth_validation',
            durationMs: totalDuration,
            extra: {
              'test': 'download',
              'tests': {
                'search': {
                  'duration_ms': searchTestDuration,
                  'success': true,
                },
                'download': {
                  'duration_ms': downloadTestDuration,
                  'success': false,
                  'reason': 'only_magnet_link',
                },
              },
            },
          );
          return false;
        }

        // Redirect to login = not authenticated
        if (downloadBody.contains('login.php') ||
            downloadBody.contains('авторизация')) {
          final totalDuration =
              DateTime.now().difference(validationStartTime).inMilliseconds;
          await logger.log(
            level: 'warning',
            subsystem: 'auth',
            message: 'Download validation failed - redirected to login',
            operationId: validationOperationId,
            context: 'auth_validation',
            durationMs: totalDuration,
            extra: {
              'test': 'download',
              'tests': {
                'search': {
                  'duration_ms': searchTestDuration,
                  'success': true,
                },
                'download': {
                  'duration_ms': downloadTestDuration,
                  'success': false,
                  'reason': 'redirected_to_login',
                },
              },
            },
          );
          return false;
        }
      }

      // If search works, consider authentication valid
      final totalDuration =
          DateTime.now().difference(validationStartTime).inMilliseconds;
      await logger.log(
        level: 'info',
        subsystem: 'auth',
        message: 'Authentication validation succeeded (search works)',
        operationId: validationOperationId,
        context: 'auth_validation',
        durationMs: totalDuration,
        extra: {
          'tests': {
            'search': {
              'duration_ms': searchTestDuration,
              'success': true,
            },
            'download': {
              'performed': topicIdMatch != null,
            },
          },
        },
      );
      return true;
    } on Exception catch (e) {
      final totalDuration =
          DateTime.now().difference(validationStartTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'auth',
        message: 'Authentication validation failed with exception',
        operationId: validationOperationId,
        context: 'auth_validation',
        durationMs: totalDuration,
        cause: e.toString(),
        extra: {
          'stack_trace':
              (e is Error) ? (e as Error).stackTrace.toString() : null,
        },
      );
      return false;
    }
  }

  /// Attempts to log in to RuTracker with the provided credentials.
  ///
  /// This method uses direct HTTP authentication (no WebView).
  /// It's an alias for [loginViaHttp] for backward compatibility.
  ///
  /// Returns `true` if login was successful, `false` otherwise.
  Future<bool> login(String username, String password) =>
      loginViaHttp(username, password);

  /// Logs out from RuTracker by clearing all authentication cookies.
  ///
  /// Throws [AuthFailure] if logout fails.
  Future<void> logout() async {
    try {
      // Clear session via SessionManager
      await sessionManager.clearSession();

      // Clear cookies from SimpleCookieManager
      final db = AppDatabase().database;
      final endpointManager = EndpointManager(db);
      final activeBase = await endpointManager.getActiveEndpoint();
      final dio = await DioClient.instance;
      final cookieJar = await _getCookieJar(dio);
      
      await _cookieManager.clearCookie(activeBase, cookieJar);
      _authStatusController.add(false);
    } on Exception {
      throw const AuthFailure('Logout failed');
    }
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

  /// Checks if the user is currently authenticated with RuTracker.
  ///
  /// Returns `true` if authenticated, `false` otherwise.
  Future<bool> get isLoggedIn async {
    try {
      final response = await (await DioClient.instance).get(
        RuTrackerUrls.profile,
        options: Options(
          receiveTimeout: const Duration(seconds: 5),
          validateStatus: (status) => status != null && status < 500,
          followRedirects: false,
        ),
      );

      // Comprehensive authentication check:
      // 1. Check HTTP status is 200
      // 2. Verify we're not redirected to login page
      // 3. Check for authenticator indicators in HTML content
      final responseData = response.data.toString();
      final responseUri = response.realUri.toString();

      final isAuthenticated = response.statusCode == 200 &&
          !responseUri.contains('login.php') &&
          // Check for profile-specific elements that indicate successful auth
          (responseData.contains('profile') ||
              responseData.contains('личный кабинет') ||
              responseData.contains('private') ||
              responseData.contains('username') ||
              responseData.contains('user_id'));

      return isAuthenticated;
    } on DioException catch (e) {
      if (e.response?.realUri.toString().contains('login.php') ?? false) {
        return false; // Redirected to login - not authenticated
      }

      return false;
    } on Exception {
      return false;
    }
  }

  /// Stream of authentication status changes
  Stream<bool> get authStatusChanges => _authStatusController.stream;

  /// Disposes the auth status controller.
  ///
  /// This method should be called when the auth instance is no longer needed
  /// to prevent memory leaks.
  void dispose() {
    _authStatusController.close();
  }

  /// Attempts to login using stored credentials with optional biometric authentication.
  ///
  /// This method tries HTTP-based login first (faster, more reliable), and falls back
  /// to WebView login if HTTP login fails or is unavailable.
  ///
  /// Returns `true` if login was successful using stored credentials,
  /// `false` if no stored credentials or authentication failed.
  Future<bool> loginWithStoredCredentials({bool useBiometric = false}) async {
    // Prevent concurrent login attempts
    if (_isLoggingIn) {
      await StructuredLogger().log(
        level: 'warning',
        subsystem: 'auth',
        message: 'Login already in progress, ignoring stored credentials login',
      );
      return false;
    }

    try {
      final credentials = await _credentialManager.getCredentials(
        requireBiometric: useBiometric,
      );

      if (credentials == null) {
        return false;
      }

      final username = credentials['username'] ?? '';
      final password = credentials['password'] ?? '';

      // Validate credentials
      if (username.trim().isEmpty || password.trim().isEmpty) {
        await StructuredLogger().log(
          level: 'warning',
          subsystem: 'auth',
          message: 'Stored credentials are empty',
        );
        return false;
      }

      // Use direct HTTP login (no WebView fallback)
      try {
        final httpSuccess = await loginViaHttp(username, password);
        if (httpSuccess) {
          await StructuredLogger().log(
            level: 'info',
            subsystem: 'auth',
            message: 'Login with stored credentials successful via HTTP',
          );
          return true;
        }
        return false;
      } on AuthFailure catch (e) {
        await StructuredLogger().log(
          level: 'warning',
          subsystem: 'auth',
          message: 'HTTP login failed with AuthFailure',
          cause: e.message,
        );
        return false;
      } on Exception catch (e) {
        await StructuredLogger().log(
          level: 'warning',
          subsystem: 'auth',
          message: 'HTTP login failed with exception',
          cause: e.toString(),
        );
        return false;
      }
    } on Exception {
      return false;
    } finally {
      _isLoggingIn = false;
    }
  }

  /// Saves credentials for future automatic login.
  Future<void> saveCredentials({
    required String username,
    required String password,
    bool rememberMe = true,
    bool useBiometric = false,
  }) async {
    await _credentialManager.saveCredentials(
      username: username,
      password: password,
      rememberMe: rememberMe,
    );
  }

  /// Checks if stored credentials are available.
  Future<bool> hasStoredCredentials() =>
      _credentialManager.hasStoredCredentials();

  /// Checks if biometric authentication is available on the device.
  Future<bool> isBiometricAvailable() =>
      _credentialManager.isBiometricAvailable();

  /// Clears all stored credentials.
  Future<void> clearStoredCredentials() async {
    await _credentialManager.clearCredentials();
  }

  /// Exports stored credentials in specified format.
  Future<String> exportCredentials({String format = 'json'}) =>
      _credentialManager.exportCredentials(format: format);

  /// Imports credentials from specified format.
  Future<void> importCredentials(String data, {String format = 'json'}) async {
    await _credentialManager.importCredentials(data, format: format);
  }

}
