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
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:jabook/core/auth/captcha_detector.dart';
import 'package:jabook/core/auth/cookie_database_service.dart';
import 'package:jabook/core/auth/credential_manager.dart';
import 'package:jabook/core/auth/direct_auth_service.dart';
import 'package:jabook/core/auth/simple_cookie_manager.dart';
import 'package:jabook/core/endpoints/endpoint_manager.dart';
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

        // CRITICAL: Save cookies to database FIRST - this is the primary storage
        try {
          final uri = Uri.parse(activeBase);
          final cookies = await cookieJar.loadForRequest(uri);
          if (cookies.isNotEmpty) {
            final cookieHeader =
                cookies.map((c) => '${c.name}=${c.value}').join('; ');

            // Save to database - most reliable storage
            final cookieDbService = CookieDatabaseService(AppDatabase());
            final savedToDb =
                await cookieDbService.saveCookies(activeBase, cookieHeader);

            await logger.log(
              level: savedToDb ? 'info' : 'warning',
              subsystem: 'auth',
              message: savedToDb
                  ? 'Cookies saved to database after HTTP login'
                  : 'Failed to save cookies to database after HTTP login',
              operationId: operationId,
              context: 'http_login',
              extra: {
                'endpoint': activeBase,
                'cookie_count': cookies.length,
                'cookie_names': cookies.map((c) => c.name).toList(),
                'saved_to_db': savedToDb,
                'has_bb_session': cookieHeader.contains('bb_session='),
                'has_bb_data': cookieHeader.contains('bb_data='),
              },
            );
          }
        } on Exception catch (e) {
          await logger.log(
            level: 'warning',
            subsystem: 'auth',
            message: 'Failed to save cookies to database',
            operationId: operationId,
            context: 'http_login',
            cause: e.toString(),
          );
        }

        // CRITICAL: Also save to CookieService (Android CookieManager)
        // This ensures hasValidCookies() can find cookies
        // Note: CookieService is Android-only, skip on web
        if (!kIsWeb) {
          try {
            final uri = Uri.parse(activeBase);
            final cookies = await cookieJar.loadForRequest(uri);
            if (cookies.isNotEmpty) {
              // Convert cookies to cookie header string for CookieService
              final cookieHeader =
                  cookies.map((c) => '${c.name}=${c.value}').join('; ');

              await logger.log(
                level: 'info',
                subsystem: 'auth',
                message: 'Syncing cookies to CookieService',
                operationId: operationId,
                context: 'http_login',
                extra: {
                  'endpoint': activeBase,
                  'cookie_count': cookies.length,
                },
              );

              // Save to CookieService via DioClient (Android only)
              await DioClient.syncCookiesFromCookieService(
                  cookieHeader, activeBase);

              await logger.log(
                level: 'info',
                subsystem: 'auth',
                message: 'Cookies synced to CookieService',
                operationId: operationId,
                context: 'http_login',
                extra: {
                  'cookie_count': cookies.length,
                  'cookie_names': cookies.map((c) => c.name).toList(),
                },
              );
            }
          } on Exception catch (e) {
            await logger.log(
              level: 'warning',
              subsystem: 'auth',
              message: 'Failed to sync cookies to CookieService',
              operationId: operationId,
              context: 'http_login',
              cause: e.toString(),
            );
          }
        }

        // CRITICAL: Save cookies to database FIRST - this is the primary storage
        try {
          final uri = Uri.parse(activeBase);
          final cookies = await cookieJar.loadForRequest(uri);
          if (cookies.isNotEmpty) {
            final cookieHeader =
                cookies.map((c) => '${c.name}=${c.value}').join('; ');

            // Save to database - most reliable storage
            final cookieDbService = CookieDatabaseService(AppDatabase());
            final savedToDb =
                await cookieDbService.saveCookies(activeBase, cookieHeader);

            await logger.log(
              level: savedToDb ? 'info' : 'warning',
              subsystem: 'auth',
              message: savedToDb
                  ? 'Cookies saved to database after HTTP login'
                  : 'Failed to save cookies to database after HTTP login',
              operationId: operationId,
              context: 'http_login',
              extra: {
                'endpoint': activeBase,
                'cookie_count': cookies.length,
                'saved_to_db': savedToDb,
              },
            );
          }
        } on Exception catch (e) {
          await logger.log(
            level: 'warning',
            subsystem: 'auth',
            message: 'Failed to save cookies to database',
            operationId: operationId,
            context: 'http_login',
            cause: e.toString(),
          );
        }

        // Save to SecureStorage for persistence (works on all platforms including web)
        try {
          final uri = Uri.parse(activeBase);
          final cookies = await cookieJar.loadForRequest(uri);
          if (cookies.isNotEmpty) {
            final cookieHeader =
                cookies.map((c) => '${c.name}=${c.value}').join('; ');
            await DioClient.saveCookiesToSecureStorage(
                cookieHeader, activeBase);

            await logger.log(
              level: 'info',
              subsystem: 'auth',
              message: 'Cookies saved to SecureStorage',
              operationId: operationId,
              context: 'http_login',
              extra: {
                'cookie_count': cookies.length,
                'platform': kIsWeb ? 'web' : 'android',
              },
            );
          }
        } on Exception catch (e) {
          await logger.log(
            level: 'warning',
            subsystem: 'auth',
            message: 'Failed to save cookies to SecureStorage',
            operationId: operationId,
            context: 'http_login',
            cause: e.toString(),
          );
        }

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

        // Validate authentication (optional confirmation)
        // If cookies are received and saved, authentication is considered successful
        // Validation is performed for confirmation and logging purposes
        final isValid =
            await _validateAuthentication(dio, activeBase, operationId);

        final totalDuration =
            DateTime.now().difference(startTime).inMilliseconds;

        // Cookies received = authentication successful
        // Validation is optional confirmation (may fail due to network issues, timeouts, etc.)
        const loginSuccessful =
            true; // Cookies received means authentication succeeded

        if (isValid) {
          _authStatusController.add(true);
          await logger.log(
            level: 'info',
            subsystem: 'auth',
            message: 'Direct HTTP login successful and validated',
            operationId: operationId,
            context: 'http_login',
            durationMs: totalDuration,
            extra: {
              'validation_passed': true,
            },
          );
        } else {
          // Cookies received but validation failed - still consider login successful
          // Validation may fail due to network issues, timeouts, or site changes
          // but cookies being received means authentication was accepted by server
          _authStatusController.add(true);
          await logger.log(
            level: 'info',
            subsystem: 'auth',
            message:
                'Direct HTTP login successful (cookies received, validation failed but ignored)',
            operationId: operationId,
            context: 'http_login',
            durationMs: totalDuration,
            extra: {
              'validation_passed': false,
              'note':
                  'Cookies received means authentication succeeded, validation failure ignored',
            },
          );
        }

        return loginSuccessful;
      } else {
        // Handle authentication errors
        final errorMessage = authResult.errorMessage ?? 'Unknown error';
        final totalDuration =
            DateTime.now().difference(startTime).inMilliseconds;

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
          // Pass captcha data to AuthFailure
          throw AuthFailure.captchaRequired(
            authResult.captchaType,
            authResult.rutrackerCaptchaData,
            authResult.captchaUrl,
          );
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

  /// Validates authentication by testing profile, search and download functionality.
  ///
  /// Returns true if authentication appears to be working, false otherwise.
  /// Includes timeout protection (10 seconds total per test).
  ///
  /// Validation strategy:
  /// 1. Test 0: Check profile page (most reliable)
  /// 2. Test 1: Check search page (verify no redirect to login)
  /// 3. Test 2: Fallback to main forum page if search is inconclusive
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

      // Test 0: Check profile page (most reliable)
      final profileTestStartTime = DateTime.now();
      await logger.log(
        level: 'debug',
        subsystem: 'auth',
        message: 'Validation test 0: Profile',
        operationId: validationOperationId,
        context: 'auth_validation',
        extra: {
          'test': 'profile',
          'url': '$baseUrl/forum/profile.php',
        },
      );

      final profileResponse = await dio
          .get(
            '$baseUrl/forum/profile.php',
            options: Options(
              validateStatus: (status) => status != null && status < 500,
              receiveTimeout: const Duration(seconds: 5),
              followRedirects:
                  false, // Don't follow redirects to see if we get 302 to login
            ),
          )
          .timeout(const Duration(seconds: 10));

      final profileTestDuration =
          DateTime.now().difference(profileTestStartTime).inMilliseconds;
      final profileBody = profileResponse.data?.toString().toLowerCase() ?? '';
      final profileBodySize = profileBody.length;
      final profileUri = profileResponse.realUri.toString().toLowerCase();

      // Check if redirected to login (not authenticated)
      final isProfileRedirectedToLogin = profileUri.contains('login.php') ||
          (profileResponse.statusCode == 302 &&
              (profileResponse.headers
                      .value('location')
                      ?.toLowerCase()
                      .contains('login.php') ??
                  false));

      // Check for profile page elements (indicates authenticated user)
      final hasProfileElements = profileBody.contains('личный кабинет') ||
          profileBody.contains('profile') ||
          profileBody.contains('username') ||
          profileBody.contains('user_id') ||
          profileBody.contains('личные данные');

      final isProfileAccessible = profileResponse.statusCode == 200 &&
          !isProfileRedirectedToLogin &&
          hasProfileElements;

      await logger.log(
        level: isProfileAccessible ? 'info' : 'debug',
        subsystem: 'auth',
        message: 'Validation test 0: Profile completed',
        operationId: validationOperationId,
        context: 'auth_validation',
        durationMs: profileTestDuration,
        extra: {
          'test': 'profile',
          'status_code': profileResponse.statusCode,
          'response_size_bytes': profileBodySize,
          'is_redirected_to_login': isProfileRedirectedToLogin,
          'has_profile_elements': hasProfileElements,
          'is_profile_accessible': isProfileAccessible,
          'real_uri': profileResponse.realUri.toString(),
          'location_header': profileResponse.headers.value('location'),
        },
      );

      if (isProfileAccessible) {
        // Profile accessible = definitely authenticated
        final totalDuration =
            DateTime.now().difference(validationStartTime).inMilliseconds;
        await logger.log(
          level: 'info',
          subsystem: 'auth',
          message: 'Authentication validation succeeded (profile accessible)',
          operationId: validationOperationId,
          context: 'auth_validation',
          durationMs: totalDuration,
          extra: {
            'tests': {
              'profile': {
                'duration_ms': profileTestDuration,
                'success': true,
              },
            },
          },
        );
        return true;
      }

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
              followRedirects: false, // Don't follow redirects
            ),
          )
          .timeout(const Duration(seconds: 10));

      final searchTestDuration =
          DateTime.now().difference(searchTestStartTime).inMilliseconds;
      final searchBody = searchResponse.data?.toString().toLowerCase() ?? '';
      final searchBodySize = searchBody.length;
      final searchUri = searchResponse.realUri.toString().toLowerCase();

      // Check if redirected to login (not authenticated)
      final isSearchRedirectedToLogin = searchUri.contains('login.php') ||
          (searchResponse.statusCode == 302 &&
              (searchResponse.headers
                      .value('location')
                      ?.toLowerCase()
                      .contains('login.php') ??
                  false));

      // Check for authentication required messages
      final requiresAuth = (searchBody.contains('action="https://rutracker') &&
              searchBody.contains('login.php')) ||
          searchBody.contains('profile.php?mode=register') ||
          searchBody.contains('авторизация') ||
          searchBody.contains('войдите в систему') ||
          searchBody.contains('требуется авторизация');

      // Check for search page elements (indicates authenticated user)
      final hasSearchPageElements = searchBody.contains('поиск') ||
          searchBody.contains('search') ||
          searchBody.contains('форум') ||
          searchBody.contains('forum') ||
          searchBody.length >
              1000; // Authenticated search page is usually > 1KB

      final isSearchAccessible = searchResponse.statusCode == 200 &&
          !isSearchRedirectedToLogin &&
          !requiresAuth &&
          hasSearchPageElements;

      await logger.log(
        level: isSearchAccessible ? 'info' : 'warning',
        subsystem: 'auth',
        message: 'Validation test 1: Search completed',
        operationId: validationOperationId,
        context: 'auth_validation',
        durationMs: searchTestDuration,
        extra: {
          'test': 'search',
          'status_code': searchResponse.statusCode,
          'response_size_bytes': searchBodySize,
          'is_redirected_to_login': isSearchRedirectedToLogin,
          'requires_auth': requiresAuth,
          'has_search_page_elements': hasSearchPageElements,
          'is_search_accessible': isSearchAccessible,
          'real_uri': searchResponse.realUri.toString(),
          'location_header': searchResponse.headers.value('location'),
        },
      );

      if (!isSearchAccessible) {
        // Test 2: Check main forum page (fallback)
        final indexTestStartTime = DateTime.now();
        await logger.log(
          level: 'debug',
          subsystem: 'auth',
          message: 'Validation test 2: Main forum page (fallback)',
          operationId: validationOperationId,
          context: 'auth_validation',
          extra: {
            'test': 'index',
            'url': '$baseUrl/forum/index.php',
          },
        );

        final indexResponse = await dio
            .get(
              '$baseUrl/forum/index.php',
              options: Options(
                validateStatus: (status) => status != null && status < 500,
                receiveTimeout: const Duration(seconds: 5),
                followRedirects: false,
              ),
            )
            .timeout(const Duration(seconds: 10));

        final indexTestDuration =
            DateTime.now().difference(indexTestStartTime).inMilliseconds;
        final indexBody = indexResponse.data?.toString().toLowerCase() ?? '';
        final indexBodySize = indexBody.length;
        final indexUri = indexResponse.realUri.toString().toLowerCase();

        final isIndexRedirectedToLogin = indexUri.contains('login.php') ||
            (indexResponse.statusCode == 302 &&
                (indexResponse.headers
                        .value('location')
                        ?.toLowerCase()
                        .contains('login.php') ??
                    false));

        final isIndexAccessible = indexResponse.statusCode == 200 &&
            !isIndexRedirectedToLogin &&
            (indexBody.contains('форум') ||
                indexBody.contains('forum') ||
                indexBody.length > 5000); // Main page is usually large

        await logger.log(
          level: isIndexAccessible ? 'info' : 'warning',
          subsystem: 'auth',
          message: 'Validation test 2: Main forum page completed',
          operationId: validationOperationId,
          context: 'auth_validation',
          durationMs: indexTestDuration,
          extra: {
            'test': 'index',
            'status_code': indexResponse.statusCode,
            'response_size_bytes': indexBodySize,
            'is_redirected_to_login': isIndexRedirectedToLogin,
            'is_index_accessible': isIndexAccessible,
            'real_uri': indexResponse.realUri.toString(),
            'location_header': indexResponse.headers.value('location'),
          },
        );

        if (!isIndexAccessible) {
          final totalDuration =
              DateTime.now().difference(validationStartTime).inMilliseconds;
          await logger.log(
            level: 'warning',
            subsystem: 'auth',
            message: 'Authentication validation failed - all tests failed',
            operationId: validationOperationId,
            context: 'auth_validation',
            durationMs: totalDuration,
            extra: {
              'tests': {
                'profile': {
                  'duration_ms': profileTestDuration,
                  'success': false,
                },
                'search': {
                  'duration_ms': searchTestDuration,
                  'success': false,
                },
                'index': {
                  'duration_ms': indexTestDuration,
                  'success': false,
                },
              },
            },
          );
          return false;
        }

        // Index accessible = authenticated
        final totalDuration =
            DateTime.now().difference(validationStartTime).inMilliseconds;
        await logger.log(
          level: 'info',
          subsystem: 'auth',
          message: 'Authentication validation succeeded (main page accessible)',
          operationId: validationOperationId,
          context: 'auth_validation',
          durationMs: totalDuration,
          extra: {
            'tests': {
              'profile': {
                'duration_ms': profileTestDuration,
                'success': false,
              },
              'search': {
                'duration_ms': searchTestDuration,
                'success': false,
              },
              'index': {
                'duration_ms': indexTestDuration,
                'success': true,
              },
            },
          },
        );
        return true;
      }

      // If search is accessible, consider authentication valid
      // (Search accessible = no redirect to login = authenticated)
      final totalDuration =
          DateTime.now().difference(validationStartTime).inMilliseconds;
      await logger.log(
        level: 'info',
        subsystem: 'auth',
        message: 'Authentication validation succeeded (search accessible)',
        operationId: validationOperationId,
        context: 'auth_validation',
        durationMs: totalDuration,
        extra: {
          'tests': {
            'profile': {
              'duration_ms': profileTestDuration,
              'success': false,
            },
            'search': {
              'duration_ms': searchTestDuration,
              'success': true,
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

  /// Attempts to log in to RuTracker with the provided credentials and captcha code.
  ///
  /// This method uses DirectAuthService to authenticate with captcha code.
  ///
  /// Returns `true` if login was successful, `false` otherwise.
  Future<bool> loginViaHttpWithCaptcha(
    String username,
    String password,
    String captchaCode,
    RutrackerCaptchaData captchaData,
  ) async {
    final operationId =
        'http_login_captcha_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    // Validate input
    if (username.trim().isEmpty ||
        password.trim().isEmpty ||
        captchaCode.trim().isEmpty) {
      await logger.log(
        level: 'warning',
        subsystem: 'auth',
        message: 'Login attempt with empty credentials or captcha code',
        operationId: operationId,
        context: 'http_login_captcha',
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
        context: 'http_login_captcha',
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
        message: 'Direct HTTP login with captcha started',
        operationId: operationId,
        context: 'http_login_captcha',
        extra: {
          'base_url': activeBase,
          'username_length': username.length,
          'captcha_code_length': captchaCode.length,
        },
      );

      // Use DirectAuthService for authentication with captcha
      final authService = DirectAuthService(dio);
      final authResult = await authService.authenticateWithCaptcha(
        username,
        password,
        activeBase,
        captchaCode,
        captchaData,
      );

      if (authResult.success && authResult.cookieString != null) {
        // Save cookie using SimpleCookieManager
        final cookieJar = await _getCookieJar(dio);
        await _cookieManager.saveCookie(
          authResult.cookieString!,
          activeBase,
          cookieJar,
        );

        // CRITICAL: Save cookies to database FIRST - this is the primary storage
        try {
          final uri = Uri.parse(activeBase);
          final cookies = await cookieJar.loadForRequest(uri);
          if (cookies.isNotEmpty) {
            final cookieHeader =
                cookies.map((c) => '${c.name}=${c.value}').join('; ');

            // Save to database - most reliable storage
            final cookieDbService = CookieDatabaseService(AppDatabase());
            final savedToDb =
                await cookieDbService.saveCookies(activeBase, cookieHeader);

            await logger.log(
              level: savedToDb ? 'info' : 'warning',
              subsystem: 'auth',
              message: savedToDb
                  ? 'Cookies saved to database after captcha login'
                  : 'Failed to save cookies to database after captcha login',
              operationId: operationId,
              context: 'http_login_captcha',
              extra: {
                'endpoint': activeBase,
                'cookie_count': cookies.length,
                'cookie_names': cookies.map((c) => c.name).toList(),
                'saved_to_db': savedToDb,
                'has_bb_session': cookieHeader.contains('bb_session='),
                'has_bb_data': cookieHeader.contains('bb_data='),
              },
            );
          }
        } on Exception catch (e) {
          await logger.log(
            level: 'warning',
            subsystem: 'auth',
            message: 'Failed to save cookies to database',
            operationId: operationId,
            context: 'http_login_captcha',
            cause: e.toString(),
          );
        }

        // CRITICAL: Also save to CookieService (Android CookieManager)
        if (!kIsWeb) {
          try {
            final uri = Uri.parse(activeBase);
            final cookies = await cookieJar.loadForRequest(uri);
            if (cookies.isNotEmpty) {
              final cookieHeader =
                  cookies.map((c) => '${c.name}=${c.value}').join('; ');

              await logger.log(
                level: 'info',
                subsystem: 'auth',
                message: 'Syncing cookies to CookieService',
                operationId: operationId,
                context: 'http_login_captcha',
                extra: {
                  'endpoint': activeBase,
                  'cookie_count': cookies.length,
                },
              );

              await DioClient.syncCookiesFromCookieService(
                  cookieHeader, activeBase);

              await logger.log(
                level: 'info',
                subsystem: 'auth',
                message: 'Cookies synced to CookieService',
                operationId: operationId,
                context: 'http_login_captcha',
                extra: {
                  'cookie_count': cookies.length,
                  'cookie_names': cookies.map((c) => c.name).toList(),
                },
              );
            }
          } on Exception catch (e) {
            await logger.log(
              level: 'warning',
              subsystem: 'auth',
              message: 'Failed to sync cookies to CookieService',
              operationId: operationId,
              context: 'http_login_captcha',
              cause: e.toString(),
            );
          }
        }

        // Save to SecureStorage
        try {
          final uri = Uri.parse(activeBase);
          final cookies = await cookieJar.loadForRequest(uri);
          if (cookies.isNotEmpty) {
            final cookieHeader =
                cookies.map((c) => '${c.name}=${c.value}').join('; ');

            await logger.log(
              level: 'info',
              subsystem: 'auth',
              message: 'Saving cookies to SecureStorage',
              operationId: operationId,
              context: 'http_login_captcha',
            );

            await DioClient.saveCookiesToSecureStorage(
                cookieHeader, activeBase);

            await logger.log(
              level: 'info',
              subsystem: 'auth',
              message: 'Cookies saved to SecureStorage',
              operationId: operationId,
              context: 'http_login_captcha',
              extra: {
                'cookie_count': cookies.length,
                'platform': kIsWeb ? 'web' : 'android',
              },
            );
          }
        } on Exception catch (e) {
          await logger.log(
            level: 'warning',
            subsystem: 'auth',
            message: 'Failed to save cookies to SecureStorage',
            operationId: operationId,
            context: 'http_login_captcha',
            cause: e.toString(),
          );
        }

        // Validate authentication
        final isValid =
            await _validateAuthentication(dio, activeBase, operationId);

        final totalDuration =
            DateTime.now().difference(startTime).inMilliseconds;

        const loginSuccessful = true;

        if (isValid) {
          _authStatusController.add(true);
          await logger.log(
            level: 'info',
            subsystem: 'auth',
            message: 'Direct HTTP login with captcha successful and validated',
            operationId: operationId,
            context: 'http_login_captcha',
            durationMs: totalDuration,
            extra: {
              'validation_passed': true,
            },
          );
        } else {
          _authStatusController.add(true);
          await logger.log(
            level: 'info',
            subsystem: 'auth',
            message:
                'Direct HTTP login with captcha successful (cookies received, validation failed but ignored)',
            operationId: operationId,
            context: 'http_login_captcha',
            durationMs: totalDuration,
            extra: {
              'validation_passed': false,
            },
          );
        }

        return loginSuccessful;
      } else {
        // Handle authentication errors
        final errorMessage = authResult.errorMessage ?? 'Unknown error';
        final totalDuration =
            DateTime.now().difference(startTime).inMilliseconds;

        await logger.log(
          level: 'warning',
          subsystem: 'auth',
          message: 'Direct HTTP login with captcha failed',
          operationId: operationId,
          context: 'http_login_captcha',
          durationMs: totalDuration,
          extra: {
            'error_message': errorMessage,
          },
        );

        // Map error messages to AuthFailure
        if (errorMessage.contains('wrong username/password')) {
          throw const AuthFailure.invalidCredentials();
        } else if (errorMessage.contains('captcha')) {
          // Pass captcha data to AuthFailure (new captcha may be required)
          throw AuthFailure.captchaRequired(
            authResult.captchaType,
            authResult.rutrackerCaptchaData,
            authResult.captchaUrl,
          );
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
        message: 'Direct HTTP login with captcha failed with exception',
        operationId: operationId,
        context: 'http_login_captcha',
        durationMs: totalDuration,
        cause: e.toString(),
      );
      throw AuthFailure('Login failed: ${e.toString()}');
    } finally {
      _isLoggingIn = false;
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
    final operationId = 'logout_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      await logger.log(
        level: 'info',
        subsystem: 'auth',
        message: 'Logout started',
        operationId: operationId,
        context: 'logout',
      );

      // CRITICAL: Clear cookies from database FIRST - this is the primary storage
      try {
        final cookieDbService = CookieDatabaseService(AppDatabase());
        final cleared = await cookieDbService.clearCookies();

        await logger.log(
          level: cleared ? 'info' : 'warning',
          subsystem: 'auth',
          message: cleared
              ? 'Cookies cleared from database'
              : 'Failed to clear cookies from database',
          operationId: operationId,
          context: 'logout',
          extra: {
            'cleared_from_db': cleared,
          },
        );
      } on Exception catch (e) {
        await logger.log(
          level: 'warning',
          subsystem: 'auth',
          message: 'Failed to clear cookies from database',
          operationId: operationId,
          context: 'logout',
          cause: e.toString(),
        );
      }

      // Clear session via SessionManager
      await logger.log(
        level: 'info',
        subsystem: 'auth',
        message: 'Clearing session via SessionManager',
        operationId: operationId,
        context: 'logout',
      );

      await sessionManager.clearSession();

      // Clear cookies from SimpleCookieManager
      final db = AppDatabase().database;
      final endpointManager = EndpointManager(db);
      final activeBase = await endpointManager.getActiveEndpoint();
      final dio = await DioClient.instance;
      final cookieJar = await _getCookieJar(dio);

      await logger.log(
        level: 'info',
        subsystem: 'auth',
        message: 'Clearing cookies from CookieJar',
        operationId: operationId,
        context: 'logout',
        extra: {
          'endpoint': activeBase,
        },
      );

      await _cookieManager.clearCookie(activeBase, cookieJar);

      // Also clear from DioClient
      await logger.log(
        level: 'info',
        subsystem: 'auth',
        message: 'Clearing cookies from DioClient',
        operationId: operationId,
        context: 'logout',
      );

      await DioClient.clearCookies();

      _authStatusController.add(false);

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'info',
        subsystem: 'auth',
        message: 'Logout completed successfully',
        operationId: operationId,
        context: 'logout',
        durationMs: duration,
      );
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'auth',
        message: 'Logout failed',
        operationId: operationId,
        context: 'logout',
        durationMs: duration,
        cause: e.toString(),
      );
      throw const AuthFailure('Logout failed');
    }
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

  /// Checks if the user is currently authenticated with RuTracker.
  ///
  /// Returns `true` if authenticated, `false` otherwise.
  Future<bool> get isLoggedIn async {
    final logger = StructuredLogger();
    final operationId = 'is_logged_in_${DateTime.now().millisecondsSinceEpoch}';

    try {
      // Use active endpoint instead of static URL
      final db = AppDatabase().database;
      final endpointManager = EndpointManager(db);
      final activeEndpoint = await endpointManager.getActiveEndpoint();
      final profileUrl = '$activeEndpoint/forum/profile.php';

      await logger.log(
        level: 'debug',
        subsystem: 'auth',
        message: 'Checking login status',
        operationId: operationId,
        context: 'is_logged_in',
        extra: {
          'profile_url': profileUrl,
          'active_endpoint': activeEndpoint,
        },
      );

      final response = await (await DioClient.instance).get(
        profileUrl,
        options: Options(
          receiveTimeout: const Duration(seconds: 5),
          validateStatus: (status) => status != null && status < 500,
          followRedirects: false,
        ),
      );

      final responseUri = response.realUri.toString();
      final locationHeader = response.headers.value('location') ?? '';
      final statusCode = response.statusCode ?? 0;

      await logger.log(
        level: 'debug',
        subsystem: 'auth',
        message: 'Login status check response received',
        operationId: operationId,
        context: 'is_logged_in',
        extra: {
          'status_code': statusCode,
          'response_uri': responseUri,
          'location_header': locationHeader,
        },
      );

      // Check if redirected to login page - this means not authenticated
      if (responseUri.contains('login.php') ||
          locationHeader.contains('login.php')) {
        await logger.log(
          level: 'debug',
          subsystem: 'auth',
          message: 'Redirected to login page - not authenticated',
          operationId: operationId,
          context: 'is_logged_in',
        );
        return false;
      }

      // If status is 200, check for profile-specific elements
      if (statusCode == 200) {
        final responseData = response.data.toString();
        final isAuthenticated = !responseUri.contains('login.php') &&
            // Check for profile-specific elements that indicate successful auth
            (responseData.contains('profile') ||
                responseData.contains('личный кабинет') ||
                responseData.contains('private') ||
                responseData.contains('username') ||
                responseData.contains('user_id'));
        await logger.log(
          level: isAuthenticated ? 'info' : 'debug',
          subsystem: 'auth',
          message: 'Login status check completed (200)',
          operationId: operationId,
          context: 'is_logged_in',
          extra: {
            'is_authenticated': isAuthenticated,
          },
        );
        return isAuthenticated;
      }

      // If status is 302 (redirect) and NOT to login.php, user is authenticated
      // RuTracker redirects authenticated users from profile.php to index.php
      if (statusCode == 302) {
        // If redirect is NOT to login.php, user is authenticated
        final isAuthenticated = !locationHeader.contains('login.php') &&
            !responseUri.contains('login.php');
        await logger.log(
          level: isAuthenticated ? 'info' : 'debug',
          subsystem: 'auth',
          message: 'Login status check completed (302)',
          operationId: operationId,
          context: 'is_logged_in',
          extra: {
            'is_authenticated': isAuthenticated,
            'location_header': locationHeader,
          },
        );
        return isAuthenticated;
      }

      // Other status codes - not authenticated
      await logger.log(
        level: 'debug',
        subsystem: 'auth',
        message: 'Login status check completed (other status)',
        operationId: operationId,
        context: 'is_logged_in',
        extra: {
          'is_authenticated': false,
          'status_code': statusCode,
        },
      );
      return false;
    } on DioException catch (e) {
      final errorUri = e.response?.realUri.toString() ?? '';
      final isLoginRedirect = errorUri.contains('login.php');
      await logger.log(
        level: 'warning',
        subsystem: 'auth',
        message: 'Login status check failed (DioException)',
        operationId: operationId,
        context: 'is_logged_in',
        extra: {
          'error': e.toString(),
          'is_login_redirect': isLoginRedirect,
          'error_uri': errorUri,
        },
      );
      if (isLoginRedirect) {
        return false; // Redirected to login - not authenticated
      }
      return false;
    } on Exception catch (e) {
      await logger.log(
        level: 'warning',
        subsystem: 'auth',
        message: 'Login status check failed (Exception)',
        operationId: operationId,
        context: 'is_logged_in',
        extra: {
          'error': e.toString(),
        },
      );
      return false;
    }
  }

  /// Stream of authentication status changes
  Stream<bool> get authStatusChanges => _authStatusController.stream;

  /// Refreshes the authentication status by checking login state
  /// and updating the status stream if it changed.
  Future<void> refreshAuthStatus() async {
    final logger = StructuredLogger();
    final operationId =
        'refresh_auth_status_${DateTime.now().millisecondsSinceEpoch}';

    await logger.log(
      level: 'debug',
      subsystem: 'auth',
      message: 'Refreshing authentication status',
      operationId: operationId,
      context: 'refresh_auth_status',
    );

    final isAuthenticated = await isLoggedIn;

    await logger.log(
      level: isAuthenticated ? 'info' : 'debug',
      subsystem: 'auth',
      message: 'Authentication status refreshed',
      operationId: operationId,
      context: 'refresh_auth_status',
      extra: {
        'is_authenticated': isAuthenticated,
      },
    );

    // Trigger update in stream to ensure all listeners are notified
    // This is important when status might have changed but stream didn't emit
    _authStatusController.add(isAuthenticated);
  }

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
