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
import 'package:jabook/core/logging/environment_logger.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/core/net/dio_client.dart';
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

  /// Controller for auth status changes
  final StreamController<bool> _authStatusController =
      StreamController<bool>.broadcast();

  /// Tracks if login is currently in progress to prevent concurrent login attempts.
  bool _isLoggingIn = false;

  /// Attempts to log in to RuTracker via HTTP (using form parser).
  ///
  /// This method extracts the login form, fills in credentials, and submits it
  /// using proper encoding and headers as specified in the working script.
  ///
  /// Returns `true` if login was successful, `false` otherwise.
  Future<bool> loginViaHttp(String username, String password) async {
    // Validate input
    if (username.trim().isEmpty || password.trim().isEmpty) {
      await StructuredLogger().log(
        level: 'warning',
        subsystem: 'auth',
        message: 'Login attempt with empty credentials',
      );
      return false;
    }

    // Prevent concurrent login attempts
    if (_isLoggingIn) {
      await StructuredLogger().log(
        level: 'warning',
        subsystem: 'auth',
        message: 'Login already in progress, ignoring duplicate request',
      );
      return false;
    }

    _isLoggingIn = true;
    try {
      final db = AppDatabase().database;
      final endpointManager = EndpointManager(db);
      final activeBase = await endpointManager.getActiveEndpoint();
      final dio = await DioClient.instance;

      // 1. Get login page
      await StructuredLogger().log(
        level: 'info',
        subsystem: 'auth',
        message: 'Fetching login page',
        extra: {'base_url': activeBase},
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

      if (loginPageResponse.statusCode != 200) {
        await StructuredLogger().log(
          level: 'error',
          subsystem: 'auth',
          message: 'Failed to fetch login page',
          extra: {'status': loginPageResponse.statusCode},
        );
        return false;
      }

      // 2. Extract form
      final html = loginPageResponse.data.toString();
      final form = await FormParser.extractLoginForm(html, activeBase);

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

      await StructuredLogger().log(
        level: 'debug',
        subsystem: 'auth',
        message: 'Submitting login form',
        extra: {
          'action': form.absoluteActionUrl,
          'username_field': usernameField,
          'password_field': passwordField,
        },
      );

      // 4. Submit POST request with proper headers
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

      // 5. Check if login was successful
      final isSuccessful = _isLoginSuccessful(loginResponse);

      if (isSuccessful) {
        await StructuredLogger().log(
          level: 'info',
          subsystem: 'auth',
          message: 'Login successful via HTTP',
        );
        _authStatusController.add(true);

        // Sync cookies from Dio to WebView storage so they're available everywhere
        try {
          await DioClient.syncCookiesToWebView();
        } on Exception catch (e) {
          await StructuredLogger().log(
            level: 'warning',
            subsystem: 'auth',
            message: 'Failed to sync cookies to WebView after HTTP login',
            cause: e.toString(),
          );
        }

        // Validate authentication (cookies are automatically managed by Dio)
        final isValid = await _validateAuthentication(dio, activeBase);

        if (!isValid) {
          await StructuredLogger().log(
            level: 'warning',
            subsystem: 'auth',
            message: 'Login appeared successful but validation failed',
          );
        }

        return isValid;
      } else {
        // Analyze why login failed for better error reporting
        final status = loginResponse.statusCode ?? 0;
        final body = loginResponse.data.toString().toLowerCase();
        final hasLoginForm = body.contains('login') && body.contains('form');
        final hasErrorMessage = body.contains('неверн') ||
            body.contains('error') ||
            body.contains('неверный') ||
            body.contains('неправильн');

        await StructuredLogger().log(
          level: 'warning',
          subsystem: 'auth',
          message: 'Login failed via HTTP',
          extra: {
            'status': status,
            'has_login_form': hasLoginForm,
            'has_error_message': hasErrorMessage,
            'redirect_location': loginResponse.headers.value('location'),
          },
        );
        return false;
      }
    } on DioException catch (e, stackTrace) {
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

      await StructuredLogger().log(
        level: 'error',
        subsystem: 'auth',
        message: 'HTTP login failed with DioException',
        cause: e.toString(),
        extra: {
          'error_type': errorType,
          'status_code': e.response?.statusCode,
          'base_url': e.requestOptions.baseUrl,
        },
      );
      logger.e('HTTP login error: $e', stackTrace: stackTrace);
      return false;
    } on Exception catch (e, stackTrace) {
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'auth',
        message: 'HTTP login failed with exception',
        cause: e.toString(),
      );
      logger.e('HTTP login error: $e', stackTrace: stackTrace);
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
  Future<bool> _validateAuthentication(Dio dio, String baseUrl) async {
    try {
      // Test 1: Search with cookies (with timeout)
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

      final searchBody = searchResponse.data?.toString().toLowerCase() ?? '';
      final hasSearchResults = searchBody.contains('hl-tr') ||
          searchBody.contains('tortopic') ||
          searchBody.contains('viewtopic.php?t=');

      if (!hasSearchResults) {
        await StructuredLogger().log(
          level: 'warning',
          subsystem: 'auth',
          message: 'Search validation failed - no results',
        );
        return false;
      }

      // Test 2: Try to download torrent (extract topic ID from search)
      final topicIdMatch =
          RegExp(r'viewtopic\.php\?t=(\d+)').firstMatch(searchBody);

      if (topicIdMatch != null) {
        final topicId = topicIdMatch.group(1);
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

        // Check content type
        final contentType =
            downloadResponse.headers.value('content-type')?.toLowerCase() ?? '';

        // Torrent file or binary data = success
        if (contentType.contains('application/x-bittorrent') ||
            contentType.contains('application/octet-stream')) {
          await StructuredLogger().log(
            level: 'info',
            subsystem: 'auth',
            message: 'Download validation succeeded - received torrent file',
          );
          return true;
        }

        final downloadBody =
            downloadResponse.data?.toString().toLowerCase() ?? '';

        // "attachment data not found" = auth works, just no file
        if (downloadBody.contains('attachment data not found') ||
            downloadBody.contains('attachment.*not.*found')) {
          await StructuredLogger().log(
            level: 'info',
            subsystem: 'auth',
            message: 'Download validation - attachment not found (auth OK)',
          );
          return hasSearchResults;
        }

        // Magnet link without torrent = not authenticated
        if (downloadBody.contains('magnet:') &&
            !downloadBody.contains('dl.php')) {
          await StructuredLogger().log(
            level: 'warning',
            subsystem: 'auth',
            message:
                'Download validation failed - only magnet link (not authenticated)',
          );
          return false;
        }

        // Redirect to login = not authenticated
        if (downloadBody.contains('login.php') ||
            downloadBody.contains('авторизация')) {
          await StructuredLogger().log(
            level: 'warning',
            subsystem: 'auth',
            message: 'Download validation failed - redirected to login',
          );
          return false;
        }
      }

      // If search works, consider authentication valid
      await StructuredLogger().log(
        level: 'info',
        subsystem: 'auth',
        message: 'Authentication validation succeeded (search works)',
      );
      return true;
    } on Exception catch (e) {
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'auth',
        message: 'Authentication validation failed with exception',
        cause: e.toString(),
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
              // use_if_null_to_convert_nulls_to_bools
              if (dialogContext?.mounted ?? false) {
                Navigator.of(dialogContext!).pop(true);
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
      await _cookieManager.clearCookies();
      await _cookieJar.deleteAll();
      _authStatusController.add(false);
    } on Exception {
      throw const AuthFailure('Logout failed');
    }
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
