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
import 'package:jabook/core/auth/form_parser.dart';
import 'package:jabook/core/endpoints/endpoint_manager.dart';
import 'package:jabook/core/endpoints/url_constants.dart';
import 'package:jabook/core/errors/failures.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/session/session_manager.dart';
import 'package:jabook/data/db/app_database.dart';
import 'package:webview_flutter/webview_flutter.dart';

/// Handles authentication with RuTracker forum.
///
/// This class provides methods for login, logout, and checking authentication
/// status using WebView for the login process and Dio for API calls.
class RuTrackerAuth {
  /// Private constructor for singleton pattern.
  RuTrackerAuth._(this._context);

  /// Factory constructor to create a new instance of RuTrackerAuth.
  ///
  /// The [context] parameter is required for showing dialogs and navigation.
  factory RuTrackerAuth(BuildContext context) => RuTrackerAuth._(context);

  /// Manages cookies for WebView authentication.
  final WebViewCookieManager _cookieManager = WebViewCookieManager();

  /// Stores cookies for Dio client authentication.
  final CookieJar _cookieJar = CookieJar();

  /// Build context for UI operations.
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

  /// Attempts to log in to RuTracker via HTTP (using form parser).
  ///
  /// This method extracts the login form, fills in credentials, and submits it
  /// using proper encoding and headers as specified in the working script.
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
      final db = AppDatabase().database;
      final endpointManager = EndpointManager(db);
      final activeBase = await endpointManager.getActiveEndpoint();
      final dio = await DioClient.instance;

      await logger.log(
        level: 'info',
        subsystem: 'auth',
        message: 'HTTP login started',
        operationId: operationId,
        context: 'http_login',
        extra: {
          'base_url': activeBase,
          'username': username,
        },
      );

      // 1. Get login page
      final loginPageStartTime = DateTime.now();
      await logger.log(
        level: 'debug',
        subsystem: 'auth',
        message: 'Fetching login page',
        operationId: operationId,
        context: 'http_login',
        extra: {
          'url': '$activeBase/forum/login.php',
          'method': 'GET',
        },
      );

      final loginPageResponse = await dio.get(
        '$activeBase/forum/login.php',
        options: Options(
          validateStatus: (status) => status != null && status < 500,
          headers: {
            'Accept':
                'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
          },
        ),
      );

      final loginPageDuration =
          DateTime.now().difference(loginPageStartTime).inMilliseconds;
      final htmlSize = loginPageResponse.data?.toString().length ?? 0;

      await logger.log(
        level: 'debug',
        subsystem: 'auth',
        message: 'Login page fetched',
        operationId: operationId,
        context: 'http_login',
        durationMs: loginPageDuration,
        extra: {
          'status_code': loginPageResponse.statusCode,
          'html_size': htmlSize,
          'headers': {
            'content-type':
                loginPageResponse.headers.value('content-type') ?? '',
            'server': loginPageResponse.headers.value('server') ?? '',
          },
        },
      );

      if (loginPageResponse.statusCode != 200) {
        await logger.log(
          level: 'error',
          subsystem: 'auth',
          message: 'Failed to fetch login page',
          operationId: operationId,
          context: 'http_login',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
          extra: {
            'status': loginPageResponse.statusCode,
            'url': '$activeBase/forum/login.php',
          },
        );
        return false;
      }

      // 2. Extract form
      final formExtractStartTime = DateTime.now();
      final html = loginPageResponse.data.toString();

      await logger.log(
        level: 'debug',
        subsystem: 'auth',
        message: 'Extracting login form',
        operationId: operationId,
        context: 'http_login',
        extra: {
          'html_size': htmlSize,
        },
      );

      final form = await FormParser.extractLoginForm(html, activeBase);
      final formExtractDuration =
          DateTime.now().difference(formExtractStartTime).inMilliseconds;

      await logger.log(
        level: 'debug',
        subsystem: 'auth',
        message: 'Login form extracted',
        operationId: operationId,
        context: 'http_login',
        durationMs: formExtractDuration,
        extra: {
          'action_url': form.absoluteActionUrl,
          'username_field': form.usernameFieldName ?? 'login_username',
          'password_field': form.passwordFieldName ?? 'login_password',
          'hidden_fields_count': form.hiddenFields.length,
          'hidden_fields': form.hiddenFields
              .map((f) => {
                    'name': f.name,
                    'has_value': f.value != null && f.value!.isNotEmpty,
                  })
              .toList(),
        },
      );

      // 3. Build POST data with URL encoding
      final postData = <String, String>{};

      // Add all hidden fields (CSRF tokens, formhash, etc.)
      for (final field in form.hiddenFields) {
        postData[field.name] = Uri.encodeComponent(field.value ?? '');
      }

      // Add username and password
      final usernameField = form.usernameFieldName ?? 'login_username';
      final passwordField = form.passwordFieldName ?? 'login_password';

      postData[usernameField] = Uri.encodeComponent(username);
      postData[passwordField] = Uri.encodeComponent(password);

      final postDataSize = postData.entries
          .map((e) => e.key.length + e.value.length)
          .fold(0, (a, b) => a + b);

      await logger.log(
        level: 'debug',
        subsystem: 'auth',
        message: 'Submitting login form',
        operationId: operationId,
        context: 'http_login',
        extra: {
          'action': form.absoluteActionUrl,
          'username_field': usernameField,
          'password_field': passwordField,
          'hidden_fields_count': form.hiddenFields.length,
          'post_data_size_bytes': postDataSize,
        },
      );

      // 4. Submit POST request with proper headers
      final loginSubmitStartTime = DateTime.now();
      final loginResponse = await dio.post(
        form.absoluteActionUrl,
        data: postData,
        options: Options(
          validateStatus: (status) => status != null && status < 600,
          followRedirects: false,
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
            'Accept-Charset': 'windows-1251,utf-8',
            'Referer': '$activeBase/forum/login.php',
            'Origin': activeBase,
            'Accept':
                'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
          },
        ),
      );

      final loginSubmitDuration =
          DateTime.now().difference(loginSubmitStartTime).inMilliseconds;
      final responseBody = loginResponse.data?.toString() ?? '';
      final responseBodySize = responseBody.length;
      final locationHeader = loginResponse.headers.value('location') ?? '';

      await logger.log(
        level: 'debug',
        subsystem: 'auth',
        message: 'Login form submitted',
        operationId: operationId,
        context: 'http_login',
        durationMs: loginSubmitDuration,
        extra: {
          'status_code': loginResponse.statusCode,
          'response_size_bytes': responseBodySize,
          'location': locationHeader.isNotEmpty ? locationHeader : null,
          'headers': {
            'content-type': loginResponse.headers.value('content-type') ?? '',
            'server': loginResponse.headers.value('server') ?? '',
            'cf-ray': loginResponse.headers.value('cf-ray') ?? '',
          },
        },
      );

      // 5. Check if login was successful
      final validationStartTime = DateTime.now();
      final isSuccessful = _isLoginSuccessful(loginResponse);
      final validationDuration =
          DateTime.now().difference(validationStartTime).inMilliseconds;

      await logger.log(
        level: 'debug',
        subsystem: 'auth',
        message: 'Login success check completed',
        operationId: operationId,
        context: 'http_login',
        durationMs: validationDuration,
        extra: {
          'is_successful': isSuccessful,
          'status_code': loginResponse.statusCode,
          'has_location': locationHeader.isNotEmpty,
          'location': locationHeader.isNotEmpty ? locationHeader : null,
        },
      );

      if (isSuccessful) {
        final totalDuration =
            DateTime.now().difference(startTime).inMilliseconds;
        await logger.log(
          level: 'info',
          subsystem: 'auth',
          message: 'Login successful via HTTP',
          operationId: operationId,
          context: 'http_login',
          durationMs: totalDuration,
          extra: {
            'status_code': loginResponse.statusCode,
            'location': locationHeader.isNotEmpty ? locationHeader : null,
            'steps': {
              'fetch_page_ms': loginPageDuration,
              'extract_form_ms': formExtractDuration,
              'submit_form_ms': loginSubmitDuration,
              'validate_ms': validationDuration,
            },
          },
        );
        _authStatusController.add(true);

        // Get cookies from Dio cookie jar and save via SessionManager
        try {
          final cookieJar = await _getCookieJar(dio);
          final uri = Uri.parse(activeBase);
          final cookies = await cookieJar.loadForRequest(uri);

          if (cookies.isNotEmpty) {
            await logger.log(
              level: 'debug',
              subsystem: 'auth',
              message: 'Saving session cookies via SessionManager',
              operationId: operationId,
              context: 'http_login',
              extra: {
                'cookie_count': cookies.length,
              },
            );

            await sessionManager.saveSessionCookies(cookies, activeBase);

            // Start session monitoring after successful login
            await sessionManager.startSessionMonitoring();

            await logger.log(
              level: 'info',
              subsystem: 'auth',
              message: 'Session cookies saved successfully, monitoring started',
              operationId: operationId,
              context: 'http_login',
            );
          } else {
            await logger.log(
              level: 'warning',
              subsystem: 'auth',
              message: 'No cookies found after successful login',
              operationId: operationId,
              context: 'http_login',
            );
          }
        } on Exception catch (e) {
          await logger.log(
            level: 'warning',
            subsystem: 'auth',
            message: 'Failed to save session cookies via SessionManager',
            operationId: operationId,
            context: 'http_login',
            cause: e.toString(),
            extra: {
              'stack_trace':
                  (e is Error) ? (e as Error).stackTrace.toString() : null,
            },
          );
        }

        // Sync cookies from Dio to WebView storage so they're available everywhere
        try {
          final syncStartTime = DateTime.now();
          await logger.log(
            level: 'debug',
            subsystem: 'auth',
            message: 'Syncing cookies to WebView after HTTP login',
            operationId: operationId,
            context: 'http_login',
          );

          await DioClient.syncCookiesToWebView();

          final syncDuration =
              DateTime.now().difference(syncStartTime).inMilliseconds;
          await logger.log(
            level: 'info',
            subsystem: 'auth',
            message: 'Cookies synced to WebView after HTTP login',
            operationId: operationId,
            context: 'http_login',
            durationMs: syncDuration,
          );
        } on Exception catch (e) {
          await logger.log(
            level: 'warning',
            subsystem: 'auth',
            message: 'Failed to sync cookies to WebView after HTTP login',
            operationId: operationId,
            context: 'http_login',
            cause: e.toString(),
            extra: {
              'stack_trace':
                  (e is Error) ? (e as Error).stackTrace.toString() : null,
            },
          );
        }

        // Validate authentication (cookies are automatically managed by Dio)
        final isValid =
            await _validateAuthentication(dio, activeBase, operationId);

        if (!isValid) {
          await logger.log(
            level: 'warning',
            subsystem: 'auth',
            message: 'Login appeared successful but validation failed',
            operationId: operationId,
            context: 'http_login',
            durationMs: DateTime.now().difference(startTime).inMilliseconds,
          );
        } else {
          final totalDuration =
              DateTime.now().difference(startTime).inMilliseconds;
          await logger.log(
            level: 'info',
            subsystem: 'auth',
            message: 'HTTP login completed and validated',
            operationId: operationId,
            context: 'http_login',
            durationMs: totalDuration,
          );
        }

        return isValid;
      } else {
        // Analyze why login failed for better error reporting
        final status = loginResponse.statusCode ?? 0;
        final body = responseBody.toLowerCase();
        final hasLoginForm = body.contains('login') && body.contains('form');
        final hasErrorMessage = body.contains('неверн') ||
            body.contains('error') ||
            body.contains('неверный') ||
            body.contains('неправильн');

        final totalDuration =
            DateTime.now().difference(startTime).inMilliseconds;
        await logger.log(
          level: 'warning',
          subsystem: 'auth',
          message: 'Login failed via HTTP',
          operationId: operationId,
          context: 'http_login',
          durationMs: totalDuration,
          extra: {
            'status': status,
            'has_login_form': hasLoginForm,
            'has_error_message': hasErrorMessage,
            'redirect_location':
                locationHeader.isNotEmpty ? locationHeader : null,
            'response_body_size': responseBodySize,
            'steps': {
              'fetch_page_ms': loginPageDuration,
              'extract_form_ms': formExtractDuration,
              'submit_form_ms': loginSubmitDuration,
              'validate_ms': validationDuration,
            },
          },
        );
        return false;
      }
    } on DioException catch (e, stackTrace) {
      final totalDuration = DateTime.now().difference(startTime).inMilliseconds;

      // More detailed error logging for network errors
      var errorType = 'Unknown';
      if (e.type == DioExceptionType.connectionTimeout) {
        errorType = 'Connection timeout';
      } else if (e.type == DioExceptionType.receiveTimeout) {
        errorType = 'Receive timeout';
      } else if (e.type == DioExceptionType.connectionError) {
        errorType = 'Connection error';
        final message = e.message?.toLowerCase() ?? '';
        if (message.contains('host lookup') ||
            message.contains('no address associated with hostname')) {
          errorType = 'DNS lookup failed';
        }
      } else if (e.type == DioExceptionType.badResponse) {
        errorType = 'Bad response';
      }

      await logger.log(
        level: 'error',
        subsystem: 'auth',
        message: 'HTTP login failed with DioException',
        operationId: operationId,
        context: 'http_login',
        durationMs: totalDuration,
        cause: e.toString(),
        extra: {
          'error_type': errorType,
          'dio_error_type': e.type.toString(),
          'status_code': e.response?.statusCode,
          'base_url': e.requestOptions.baseUrl,
          'url': e.requestOptions.uri.toString(),
          'stack_trace': stackTrace.toString(),
        },
      );
      return false;
    } on Exception catch (e, stackTrace) {
      final totalDuration = DateTime.now().difference(startTime).inMilliseconds;

      await logger.log(
        level: 'error',
        subsystem: 'auth',
        message: 'HTTP login failed with exception',
        operationId: operationId,
        context: 'http_login',
        durationMs: totalDuration,
        cause: e.toString(),
        extra: {
          'stack_trace': (e is Error)
              ? (e as Error).stackTrace.toString()
              : stackTrace.toString(),
        },
      );
      return false;
    } finally {
      _isLoggingIn = false;
    }
  }

  /// Checks if login response indicates successful authentication.
  ///
  /// Uses multiple indicators: HTTP status codes, redirects, and content analysis.
  bool _isLoginSuccessful(Response response) {
    // 1. Check redirect status
    if (response.statusCode == 302 || response.statusCode == 301) {
      final location = response.headers.value('location')?.toLowerCase() ?? '';
      if (location.isNotEmpty) {
        // Redirect to profile/index = success
        if (location.contains('profile') || location.contains('index.php')) {
          if (!location.contains('login')) {
            return true; // Redirect to profile/index without login = success
          }
        }
      } else {
        // Has redirect status but no Location header = likely success
        return true;
      }
    }

    // 2. Check response content
    final body = response.data?.toString().toLowerCase() ?? '';

    // Positive indicators
    final hasLogout = body.contains('выход') || body.contains('logout');
    final hasProfile = body.contains('profile') ||
        body.contains('личный кабинет') ||
        body.contains('личная информация');
    final hasUsername = body.contains('username') ||
        body.contains('имя пользователя') ||
        body.contains('user_id');

    // Negative indicators
    final hasLoginForm = body.contains('login.php') ||
        body.contains('авторизация') ||
        body.contains('войти') ||
        (body.contains('input') && body.contains('password'));

    // Combination: positive indicators exist AND login form absent
    if (!hasLoginForm && (hasLogout || hasProfile || hasUsername)) {
      return true;
    }

    // Strong positive indicators override form presence
    if (hasLogout || hasProfile) {
      return true;
    }

    // If status is 200 but no clear indicators, assume failure
    return false;
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
  /// This method uses WebView for login. For HTTP-based login, use [loginViaHttp].
  ///
  /// Returns `true` if login was successful, `false` otherwise.
  Future<bool> login(String username, String password) async {
    // Validate input
    if (username.trim().isEmpty || password.trim().isEmpty) {
      throw const AuthFailure.invalidCredentials();
    }

    // Prevent concurrent login attempts
    if (_isLoggingIn) {
      throw const AuthFailure('Login already in progress');
    }

    _isLoggingIn = true;
    try {
      // Create controller
      final controller = WebViewController();

      await controller.setJavaScriptMode(JavaScriptMode.unrestricted);

      BuildContext? dialogContext;

      await controller.setNavigationDelegate(
        NavigationDelegate(
          onPageFinished: (url) {
            if (url.contains('profile.php')) {
              _syncCookies();
              // On Android 16, context may become null between check and use
              // Store context in local variable to avoid race condition
              final context = dialogContext;
              if (context != null && context.mounted) {
                try {
                  Navigator.of(context).pop(true);
                } on Exception {
                  // Context may have become invalid, ignore
                }
              }
            }
          },
        ),
      );

      await controller.loadRequest(Uri.parse(RuTrackerUrls.login));

      // use_build_context_synchronously: check if context is still valid
      if (!_context.mounted) {
        return false;
      }

      final result = await showDialog<bool>(
        context: _context,
        builder: (ctx) {
          dialogContext = ctx;
          return AlertDialog(
            title: const Text('Login to RuTracker'),
            content: SizedBox(
              width: double.maxFinite,
              height: 400,
              child: WebViewWidget(controller: controller),
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(ctx).pop(false),
                child: const Text('Cancel'),
              ),
            ],
          );
        },
      );

      final success = result ?? false;
      if (success) {
        _authStatusController.add(true);
      }
      return success;
    } on Exception catch (e) {
      throw AuthFailure('Login failed: ${e.toString()}');
    } finally {
      _isLoggingIn = false;
    }
  }

  /// Logs out from RuTracker by clearing all authentication cookies.
  ///
  /// Throws [AuthFailure] if logout fails.
  Future<void> logout() async {
    try {
      // Clear session via SessionManager
      await sessionManager.clearSession();

      // Also clear WebView and local cookies
      await _cookieManager.clearCookies();
      await _cookieJar.deleteAll();
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

      // Try HTTP login first (faster and more reliable)
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
      } on Exception catch (e) {
        await StructuredLogger().log(
          level: 'warning',
          subsystem: 'auth',
          message: 'HTTP login failed, falling back to WebView',
          cause: e.toString(),
        );
      }

      // Fallback to WebView login if HTTP login failed
      return await login(username, password);
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

  /// Synchronizes cookies between WebView and Dio client.
  Future<void> _syncCookies() async {
    try {
      // In webview_flutter 4.13.0, cookies are automatically shared between
      // WebView and the app's cookie store. We just need to ensure Dio uses
      // the same cookie jar and clear any stale cookies.

      // Clear existing cookies to ensure fresh session state
      await _cookieJar.deleteAll();

      // Also clear cookies from DioClient's global cookie jar
      final dio = await DioClient.instance;
      final cookieInterceptors = dio.interceptors.whereType<CookieManager>();

      for (final interceptor in cookieInterceptors) {
        await interceptor.cookieJar.deleteAll();
      }

      // The actual cookie synchronization happens automatically through
      // the platform's cookie store shared between WebView and HTTP client
    } catch (e) {
      throw AuthFailure('Cookie sync failed: ${e.toString()}');
    }
  }
}
