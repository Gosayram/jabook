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

import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:jabook/core/auth/captcha_detector.dart';
import 'package:jabook/core/auth/cp1251_encoder.dart';
import 'package:jabook/core/infrastructure/errors/failures.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:jabook/core/net/user_agent_manager.dart';
import 'package:windows1251/windows1251.dart';

/// Result of authentication attempt.
class AuthResult {
  /// Creates a new AuthResult instance.
  AuthResult({
    required this.success,
    this.cookieString,
    this.errorMessage,
    this.captchaType,
    this.rutrackerCaptchaData,
    this.captchaUrl,
  });

  /// Whether authentication was successful.
  final bool success;

  /// Cookie string in format 'bb_session=...; bb_ssl=1' if successful.
  final String? cookieString;

  /// Error message if authentication failed.
  final String? errorMessage;

  /// Type of captcha required (if any).
  final CaptchaType? captchaType;

  /// RuTracker captcha data (if captchaType is rutracker).
  final RutrackerCaptchaData? rutrackerCaptchaData;

  /// URL for CloudFlare challenge (if captchaType is cloudflare).
  final String? captchaUrl;
}

/// Service for direct HTTP authentication with RuTracker.
///
/// This service implements authentication similar to the Python parser,
/// sending POST request with CP1251-encoded credentials directly to
/// the login endpoint without using WebView.
class DirectAuthService {
  /// Creates a new DirectAuthService instance.
  DirectAuthService(this._dio);

  /// Dio instance for making HTTP requests.
  final Dio _dio;

  /// Authenticates with RuTracker using direct HTTP POST request.
  ///
  /// The [username] and [password] are encoded to CP1251 before sending.
  /// The [baseUrl] is the RuTracker mirror URL to authenticate against.
  ///
  /// Returns [AuthResult] with success status and cookie string if successful.
  ///
  /// Throws [AuthFailure] if authentication fails with specific error.
  Future<AuthResult> authenticate(
    String username,
    String password,
    String baseUrl,
  ) async {
    final operationId = 'direct_auth_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      // CRITICAL: Get User Agent from WebView
      final userAgentManager = UserAgentManager();
      final userAgent = await userAgentManager.getUserAgent();

      await logger.log(
        level: 'info',
        subsystem: 'auth',
        message: 'Direct authentication started',
        operationId: operationId,
        context: 'direct_auth',
        extra: {
          'base_url': baseUrl,
          'username_length': username.length,
          'password_length': password.length,
          'user_agent': userAgent,
          'user_agent_length': userAgent.length,
        },
      );

      await logger.log(
        level: 'warning', // Changed to 'warning' for visibility in release mode
        subsystem: 'auth',
        message: 'User Agent obtained for authentication request',
        operationId: operationId,
        context: 'direct_auth',
        extra: {
          'user_agent': userAgent,
          'user_agent_length': userAgent.length,
        },
      );

      // Step 1: Encode credentials to CP1251
      await logger.log(
        level: 'info',
        subsystem: 'auth',
        message: 'Starting credential encoding to CP1251',
        operationId: operationId,
        context: 'direct_auth',
      );

      final encodeStartTime = DateTime.now();
      final usernameBytes = Cp1251Encoder.encodeToCp1251(username);
      final passwordBytes = Cp1251Encoder.encodeToCp1251(password);
      final loginButtonBytes = Cp1251Encoder.loginButtonBytes;
      final encodeDuration =
          DateTime.now().difference(encodeStartTime).inMilliseconds;

      await logger.log(
        level: 'info',
        subsystem: 'auth',
        message: 'Credential encoding completed',
        operationId: operationId,
        context: 'direct_auth',
        durationMs: encodeDuration,
        extra: {
          'username_bytes_length': usernameBytes.length,
          'password_bytes_length': passwordBytes.length,
          'login_button_bytes_length': loginButtonBytes.length,
        },
      );

      await logger.log(
        level: 'debug',
        subsystem: 'auth',
        message: 'Credentials encoded to CP1251',
        operationId: operationId,
        context: 'direct_auth',
        durationMs: encodeDuration,
        extra: {
          'username_bytes_length': usernameBytes.length,
          'password_bytes_length': passwordBytes.length,
        },
      );

      // Step 2: Build form data
      // In Python: post_params = {
      //   'login_username': params['username'].encode('cp1251'),
      //   'login_password': params['password'].encode('cp1251'),
      //   'login': b'\xe2\xf5\xee\xe4'
      // }
      // requests.post automatically handles URL encoding of CP1251 bytes
      // We need to send this as application/x-www-form-urlencoded with CP1251 encoding
      // Dio's FormData will URL-encode, but we need to ensure CP1251 bytes are used
      // So we'll build the form data manually with percent-encoded CP1251 bytes
      final formDataStartTime = DateTime.now();
      final formData = _buildFormData(
        usernameBytes: usernameBytes,
        passwordBytes: passwordBytes,
        loginButtonBytes: loginButtonBytes,
      );
      final formDataDuration =
          DateTime.now().difference(formDataStartTime).inMilliseconds;

      await logger.log(
        level: 'debug',
        subsystem: 'auth',
        message: 'Form data built',
        operationId: operationId,
        context: 'direct_auth',
        durationMs: formDataDuration,
        extra: {
          'form_data_length': formData.length,
        },
      );

      // Step 3: Send POST request
      final requestStartTime = DateTime.now();
      final loginUrl = '$baseUrl/forum/login.php';

      await logger.log(
        level: 'debug',
        subsystem: 'auth',
        message: 'Sending authentication request with User Agent',
        operationId: operationId,
        context: 'direct_auth',
        extra: {
          'url': loginUrl,
          'method': 'POST',
          'user_agent': userAgent,
        },
      );

      final response = await _dio.post(
        loginUrl,
        data: formData,
        options: Options(
          validateStatus: (status) => status != null && status < 600,
          followRedirects: false,
          headers: {
            'Content-Type':
                'application/x-www-form-urlencoded; charset=windows-1251',
            'Accept':
                'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
            'Accept-Language': 'ru,en-US;q=0.8,en;q=0.6',
            'Accept-Encoding': 'gzip,deflate',
            'Referer': '$baseUrl/forum/index.php',
            'Connection': 'keep-alive',
            'User-Agent': userAgent, // CRITICAL: Use User Agent from WebView
          },
        ),
      );

      final requestDuration =
          DateTime.now().difference(requestStartTime).inMilliseconds;

      // Log response details including redirect info
      // In Python parser: 302 with cookies = success (allow_redirects=False)
      final isRedirect =
          response.statusCode == 302 || response.statusCode == 301;
      final locationHeader = response.headers.value('location') ?? '';
      final setCookieHeaders = response.headers['set-cookie'];

      // Log all response headers for debugging
      final allHeaders = <String, dynamic>{};
      response.headers.forEach((key, values) {
        allHeaders[key] = values.length == 1 ? values.first : values;
      });

      await logger.log(
        level: 'info', // Changed to 'info' to ensure it's logged
        subsystem: 'auth',
        message: 'Authentication request completed with User Agent',
        operationId: operationId,
        context: 'direct_auth',
        durationMs: requestDuration,
        extra: {
          'status_code': response.statusCode,
          'is_redirect': isRedirect,
          'location_header': locationHeader,
          'response_size': response.data?.toString().length ?? 0,
          'has_cookies':
              setCookieHeaders != null && setCookieHeaders.isNotEmpty,
          'set_cookie_headers_count': setCookieHeaders?.length ?? 0,
          'all_response_headers': allHeaders,
          // Don't log cookie headers - they are sensitive
          'user_agent': userAgent,
          'user_agent_applied':
              true, // Confirmation that User Agent was applied
        },
      );

      // Step 4: Extract and validate cookies
      final cookieExtractStartTime = DateTime.now();

      // Log cookie extraction for debugging (always log, even if no cookies)
      await logger.log(
        level: 'info', // Changed to 'info' to ensure it's logged
        subsystem: 'auth',
        message: 'Extracting cookies from response',
        operationId: operationId,
        context: 'direct_auth',
        extra: {
          'set_cookie_headers_count': setCookieHeaders?.length ?? 0,
          // Don't log cookie headers - they are sensitive
        },
      );

      final cookieString = _extractCookie(response);
      final cookieExtractDuration =
          DateTime.now().difference(cookieExtractStartTime).inMilliseconds;

      // Log cookie extraction result
      await logger.log(
        level: 'info',
        subsystem: 'auth',
        message: 'Cookie extraction completed',
        operationId: operationId,
        context: 'direct_auth',
        durationMs: cookieExtractDuration,
        extra: {
          'cookie_found': cookieString != null,
          'cookie_length': cookieString?.length ?? 0,
          'cookie_count':
              cookieString != null ? cookieString.split(';').length : 0,
          // Don't log cookie values - they are sensitive
        },
      );

      if (cookieString != null) {
        // Success: cookies found (even with 302 status)
        // In Python parser: 302 with bb_session cookie = success
        await logger.log(
          level: 'info',
          subsystem: 'auth',
          message: 'Authentication successful',
          operationId: operationId,
          context: 'direct_auth',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
          extra: {
            'cookie_length': cookieString.length,
            'status_code': response.statusCode,
            'is_302_redirect': response.statusCode == 302,
            'location_header': locationHeader,
            'steps': {
              'encode_ms': encodeDuration,
              'form_data_ms': formDataDuration,
              'request_ms': requestDuration,
              'cookie_extract_ms': cookieExtractDuration,
            },
          },
        );

        return AuthResult(
          success: true,
          cookieString: cookieString,
        );
      }

      // Step 5: Check for errors in response
      final errorCheckStartTime = DateTime.now();
      final errorMessage = _checkForErrors(response);
      final errorCheckDuration =
          DateTime.now().difference(errorCheckStartTime).inMilliseconds;

      // Step 6: Extract captcha data if captcha is required
      Map<String, dynamic>? captchaData;
      if (errorMessage == 'site want captcha') {
        final captchaExtractStartTime = DateTime.now();
        captchaData = await _extractCaptchaData(response, baseUrl);
        final captchaExtractDuration =
            DateTime.now().difference(captchaExtractStartTime).inMilliseconds;

        await logger.log(
          level: 'info',
          subsystem: 'auth',
          message: 'Captcha data extracted',
          operationId: operationId,
          context: 'direct_auth',
          durationMs: captchaExtractDuration,
          extra: {
            'has_captcha_data': captchaData != null,
            'captcha_type': captchaData?['type']?.toString(),
          },
        );
      }

      final totalDuration = DateTime.now().difference(startTime).inMilliseconds;

      await logger.log(
        level: 'warning',
        subsystem: 'auth',
        message: 'Authentication failed',
        operationId: operationId,
        context: 'direct_auth',
        durationMs: totalDuration,
        extra: {
          'error_message': errorMessage,
          'status_code': response.statusCode,
          'has_captcha_data': captchaData != null,
          'steps': {
            'encode_ms': encodeDuration,
            'form_data_ms': formDataDuration,
            'request_ms': requestDuration,
            'cookie_extract_ms': cookieExtractDuration,
            'error_check_ms': errorCheckDuration,
          },
        },
      );

      return AuthResult(
        success: false,
        errorMessage: errorMessage,
        captchaType: captchaData?['type'] as CaptchaType?,
        rutrackerCaptchaData:
            captchaData?['rutrackerData'] as RutrackerCaptchaData?,
        captchaUrl: captchaData?['url'] as String?,
      );
    } on DioException catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'auth',
        message: 'Network error during authentication',
        operationId: operationId,
        context: 'direct_auth',
        durationMs: duration,
        cause: e.toString(),
        extra: {
          'error_type': e.type.toString(),
          'status_code': e.response?.statusCode,
        },
      );

      if (e.type == DioExceptionType.connectionTimeout ||
          e.type == DioExceptionType.receiveTimeout) {
        throw const AuthFailure(
            'Request timeout. Please check your connection.');
      }

      throw AuthFailure('Network error: ${e.message}');
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'auth',
        message: 'Unexpected error during authentication',
        operationId: operationId,
        context: 'direct_auth',
        durationMs: duration,
        cause: e.toString(),
      );

      throw AuthFailure('Authentication failed: ${e.toString()}');
    }
  }

  /// Authenticates with RuTracker using direct HTTP POST request with captcha code.
  ///
  /// Similar to [authenticate], but includes captcha code in the request.
  /// The [captchaCode] is the code entered by the user.
  /// The [rutrackerCaptchaData] contains captcha session data from previous attempt.
  Future<AuthResult> authenticateWithCaptcha(
    String username,
    String password,
    String baseUrl,
    String captchaCode,
    RutrackerCaptchaData rutrackerCaptchaData,
  ) async {
    final operationId =
        'direct_auth_captcha_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      // CRITICAL: Get User Agent from WebView
      final userAgentManager = UserAgentManager();
      final userAgent = await userAgentManager.getUserAgent();

      await logger.log(
        level: 'info',
        subsystem: 'auth',
        message: 'Direct authentication with captcha started',
        operationId: operationId,
        context: 'direct_auth_captcha',
        extra: {
          'base_url': baseUrl,
          'username_length': username.length,
          'password_length': password.length,
          'captcha_code_length': captchaCode.length,
          'user_agent': userAgent,
        },
      );

      // Step 1: Encode credentials to CP1251
      final usernameBytes = Cp1251Encoder.encodeToCp1251(username);
      final passwordBytes = Cp1251Encoder.encodeToCp1251(password);
      final loginButtonBytes = Cp1251Encoder.loginButtonBytes;
      final captchaCodeBytes = Cp1251Encoder.encodeToCp1251(captchaCode);

      // Step 2: Build form data with captcha
      final formData = _buildFormDataWithCaptcha(
        usernameBytes: usernameBytes,
        passwordBytes: passwordBytes,
        loginButtonBytes: loginButtonBytes,
        captchaCodeBytes: captchaCodeBytes,
        capSid: rutrackerCaptchaData.capSid,
        capCodeFieldName: rutrackerCaptchaData.capCodeFieldName,
      );

      await logger.log(
        level: 'debug',
        subsystem: 'auth',
        message: 'Form data with captcha built',
        operationId: operationId,
        context: 'direct_auth_captcha',
        extra: {
          'form_data_length': formData.length,
          'cap_sid': rutrackerCaptchaData.capSid,
          'cap_code_field': rutrackerCaptchaData.capCodeFieldName,
        },
      );

      // Step 3: Send POST request
      final loginUrl = '$baseUrl/forum/login.php';

      final response = await _dio.post(
        loginUrl,
        data: formData,
        options: Options(
          validateStatus: (status) => status != null && status < 600,
          followRedirects: false,
          headers: {
            'Content-Type':
                'application/x-www-form-urlencoded; charset=windows-1251',
            'Accept':
                'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
            'Accept-Language': 'ru,en-US;q=0.8,en;q=0.6',
            'Accept-Encoding': 'gzip,deflate',
            'Referer': '$baseUrl/forum/index.php',
            'Connection': 'keep-alive',
            'User-Agent': userAgent,
          },
        ),
      );

      // Step 4: Extract and validate cookies
      final cookieString = _extractCookie(response);

      if (cookieString != null) {
        // Success: cookies found
        await logger.log(
          level: 'info',
          subsystem: 'auth',
          message: 'Authentication with captcha successful',
          operationId: operationId,
          context: 'direct_auth_captcha',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
          extra: {
            'cookie_length': cookieString.length,
            'status_code': response.statusCode,
          },
        );

        return AuthResult(
          success: true,
          cookieString: cookieString,
        );
      }

      // Step 5: Check for errors
      final errorMessage = _checkForErrors(response);

      // If captcha is still required, extract new captcha data
      Map<String, dynamic>? captchaData;
      if (errorMessage == 'site want captcha') {
        captchaData = await _extractCaptchaData(response, baseUrl);
      }

      final totalDuration = DateTime.now().difference(startTime).inMilliseconds;

      await logger.log(
        level: 'warning',
        subsystem: 'auth',
        message: 'Authentication with captcha failed',
        operationId: operationId,
        context: 'direct_auth_captcha',
        durationMs: totalDuration,
        extra: {
          'error_message': errorMessage,
          'status_code': response.statusCode,
          'has_new_captcha_data': captchaData != null,
        },
      );

      return AuthResult(
        success: false,
        errorMessage: errorMessage,
        captchaType: captchaData?['type'] as CaptchaType?,
        rutrackerCaptchaData:
            captchaData?['rutrackerData'] as RutrackerCaptchaData?,
        captchaUrl: captchaData?['url'] as String?,
      );
    } on DioException catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'auth',
        message: 'Network error during authentication with captcha',
        operationId: operationId,
        context: 'direct_auth_captcha',
        durationMs: duration,
        cause: e.toString(),
      );

      if (e.type == DioExceptionType.connectionTimeout ||
          e.type == DioExceptionType.receiveTimeout) {
        throw const AuthFailure(
            'Request timeout. Please check your connection.');
      }

      throw AuthFailure('Network error: ${e.message}');
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'auth',
        message: 'Unexpected error during authentication with captcha',
        operationId: operationId,
        context: 'direct_auth_captcha',
        durationMs: duration,
        cause: e.toString(),
      );

      throw AuthFailure('Authentication failed: ${e.toString()}');
    }
  }

  /// Builds form data string with CP1251-encoded values.
  ///
  /// The form data is URL-encoded but uses CP1251 bytes for values.
  /// Format: login_username=...&login_password=...&login=...
  String _buildFormData({
    required List<int> usernameBytes,
    required List<int> passwordBytes,
    required List<int> loginButtonBytes,
  }) {
    // URL encode the field names and CP1251-encoded values
    // We need to percent-encode the CP1251 bytes
    final usernameEncoded = _percentEncode(usernameBytes);
    final passwordEncoded = _percentEncode(passwordBytes);
    final loginEncoded = _percentEncode(loginButtonBytes);

    return 'login_username=$usernameEncoded'
        '&login_password=$passwordEncoded'
        '&login=$loginEncoded';
  }

  /// Builds form data string with CP1251-encoded values including captcha.
  ///
  /// Format: login_username=...&login_password=...&login=...&cap_sid=...&cap_code_*=...
  String _buildFormDataWithCaptcha({
    required List<int> usernameBytes,
    required List<int> passwordBytes,
    required List<int> loginButtonBytes,
    required List<int> captchaCodeBytes,
    required String capSid,
    required String capCodeFieldName,
  }) {
    final usernameEncoded = _percentEncode(usernameBytes);
    final passwordEncoded = _percentEncode(passwordBytes);
    final loginEncoded = _percentEncode(loginButtonBytes);
    final captchaCodeEncoded = _percentEncode(captchaCodeBytes);

    // URL encode cap_sid and cap_code field name
    final capSidEncoded = Uri.encodeComponent(capSid);
    final capCodeFieldEncoded = Uri.encodeComponent(capCodeFieldName);

    return 'login_username=$usernameEncoded'
        '&login_password=$passwordEncoded'
        '&login=$loginEncoded'
        '&cap_sid=$capSidEncoded'
        '&$capCodeFieldEncoded=$captchaCodeEncoded';
  }

  /// Percent-encodes a list of bytes.
  ///
  /// Converts bytes to percent-encoded string (e.g., %E2%F5%EE%E4).
  String _percentEncode(List<int> bytes) => bytes
      .map((byte) => '%${byte.toRadixString(16).toUpperCase().padLeft(2, '0')}')
      .join();

  /// Extracts cookie string from response.
  ///
  /// Looks for 'bb_session' cookie in Set-Cookie headers.
  /// Returns cookie string in format 'bb_session=...; bb_ssl=1' or null if not found.
  String? _extractCookie(Response response) {
    // Check Set-Cookie header
    // Dio stores Set-Cookie headers in response.headers['set-cookie'] as List<String>
    // Note: Dio headers are case-insensitive, but we check both lowercase and title case
    final setCookieHeaders =
        response.headers['set-cookie'] ?? response.headers['Set-Cookie'];

    if (setCookieHeaders != null && setCookieHeaders.isNotEmpty) {
      for (final cookieHeader in setCookieHeaders) {
        // Cookie header format: "bb_session=value; path=/; domain=..."
        // Extract the actual cookie value (before first semicolon)
        if (cookieHeader.contains('bb_session=')) {
          // Extract bb_session value
          // Pattern: bb_session=value (value can contain special chars, so we take until ; or end)
          final match = RegExp(r'bb_session=([^;]+)').firstMatch(cookieHeader);
          if (match != null) {
            final sessionValue = match.group(1)?.trim();
            if (sessionValue != null && sessionValue.isNotEmpty) {
              // Format: 'bb_session=...; bb_ssl=1' (as in Python parser)
              return 'bb_session=$sessionValue; bb_ssl=1';
            }
          }
        }
      }
    }

    // Also check response.headers.value('set-cookie') as fallback (case-insensitive)
    final setCookieValue = response.headers.value('set-cookie') ??
        response.headers.value('Set-Cookie');
    if (setCookieValue != null && setCookieValue.contains('bb_session=')) {
      final match = RegExp(r'bb_session=([^;]+)').firstMatch(setCookieValue);
      if (match != null) {
        final sessionValue = match.group(1)?.trim();
        if (sessionValue != null && sessionValue.isNotEmpty) {
          return 'bb_session=$sessionValue; bb_ssl=1';
        }
      }
    }

    // Try to get cookies from CookieManager if available
    // This is a fallback in case cookies are already processed by CookieManager
    try {
      final cookieManager = response.requestOptions.headers['Cookie'];
      if (cookieManager != null &&
          cookieManager.toString().contains('bb_session=')) {
        final match =
            RegExp(r'bb_session=([^;]+)').firstMatch(cookieManager.toString());
        if (match != null) {
          final sessionValue = match.group(1)?.trim();
          if (sessionValue != null && sessionValue.isNotEmpty) {
            return 'bb_session=$sessionValue; bb_ssl=1';
          }
        }
      }
    } on Exception {
      // Ignore errors in fallback
    }

    return null;
  }

  /// Checks response for authentication errors.
  ///
  /// Returns error message if error detected, null otherwise.
  ///
  /// In Python parser, errors are checked by searching for CP1251-encoded error strings
  /// in the response content. We decode the response and check for error patterns.
  String? _checkForErrors(Response response) {
    // Check if we have cookies - if yes, no error (successful auth)
    final hasCookies = _extractCookie(response) != null;

    // IMPORTANT: Based on HAR file analysis, RuTracker returns:
    // - Status 200 with no cookies = authentication failed (error page with HTML)
    // - Status 302 with cookies = authentication successful
    // - Status 302 without cookies = authentication failed (redirect to login)
    // If status 200 and no cookies - this is an error page (not a redirect)
    // RuTracker shows error page with status 200 when auth fails (including captcha)
    // We need to check the response body for specific error messages below

    // If 302 redirect but no cookies, check for error messages
    // In Python parser: 302 without cookies = authentication failed
    if (response.statusCode == 302 && !hasCookies) {
      // Check location header - if redirects to login, it's an error
      final location = response.headers.value('location') ?? '';
      if (location.contains('login.php')) {
        // Redirected back to login = authentication failed
        return 'Authentication failed - redirected to login';
      }
    }

    final responseBody = response.data;
    if (responseBody == null) {
      return 'No response body';
    }

    // Convert response to string for error checking
    // RuTracker responses are typically in CP1251 encoding
    String bodyText;
    if (responseBody is String) {
      // Already a string, use as-is
      bodyText = responseBody;
    } else if (responseBody is List<int>) {
      // Binary data - try to decode as CP1251 (RuTracker's encoding)
      // In Python: errors are checked in r.content (bytes)
      // We decode to string to check for error messages
      try {
        // Try CP1251 first (RuTracker's default encoding)
        bodyText = windows1251.decode(responseBody);
      } on Exception {
        // Fallback to UTF-8 if CP1251 fails
        try {
          bodyText = utf8.decode(responseBody);
        } on FormatException {
          // If both fail, try to use as-is (might work for ASCII)
          bodyText = String.fromCharCodes(responseBody);
        }
      }
    } else {
      bodyText = responseBody.toString();
    }

    // Check for error messages in response
    // In Python parser:
    // - 'неверный пароль'.encode('cp1251') in r.content → wrong username/password
    // - 'введите код подтверждения'.encode('cp1251') in r.content → site want captcha
    // We decode the response and check for these strings

    final lowerBody = bodyText.toLowerCase();

    // Check for "wrong password" error FIRST (has priority over captcha)
    // Use more specific patterns to avoid false positives
    // Pattern: "неверный пароль" or variations
    final hasWrongPassword = lowerBody.contains('неверный пароль') ||
        lowerBody.contains('неправильный пароль') ||
        lowerBody.contains('wrong password') ||
        lowerBody.contains('invalid password') ||
        lowerBody.contains('неверное имя пользователя') ||
        lowerBody.contains('неверное имя');

    // Check for captcha requirement
    // Use more specific patterns to avoid false positives with wrong password messages
    // Pattern: "введите код подтверждения" or variations
    final hasCaptcha = (lowerBody.contains('введите код подтверждения') ||
            lowerBody.contains('код подтверждения') ||
            (lowerBody.contains('код') &&
                lowerBody.contains('подтверждени'))) &&
        !hasWrongPassword; // Don't treat as captcha if there's a wrong password error

    // Priority: wrong password > captcha
    if (hasWrongPassword) {
      return 'wrong username/password';
    }

    if (hasCaptcha ||
        (lowerBody.contains('captcha') && !hasWrongPassword) ||
        (lowerBody.contains('капча') && !hasWrongPassword)) {
      return 'site want captcha';
    }

    // If no cookie and no specific error, return generic message
    // This matches Python parser behavior: 'no cookies returned'
    return 'no cookies returned';
  }

  /// Extracts captcha information from response if captcha is required.
  ///
  /// Returns captcha type and data if found, null otherwise.
  Future<Map<String, dynamic>?> _extractCaptchaData(
    Response response,
    String baseUrl,
  ) async {
    final errorMessage = _checkForErrors(response);
    if (errorMessage != 'site want captcha') {
      return null;
    }

    try {
      // Decode response body
      String htmlBody;
      final responseBody = response.data;
      if (responseBody is String) {
        htmlBody = responseBody;
      } else if (responseBody is List<int>) {
        try {
          htmlBody = windows1251.decode(responseBody);
        } on Exception {
          try {
            htmlBody = utf8.decode(responseBody);
          } on FormatException {
            htmlBody = String.fromCharCodes(responseBody);
          }
        }
      } else {
        htmlBody = responseBody.toString();
      }

      // Detect captcha type
      final captchaType = await CaptchaDetector.detectType(htmlBody);

      if (captchaType == CaptchaType.rutracker) {
        // Extract RuTracker captcha data
        final captchaData = await CaptchaDetector.extractRutrackerCaptcha(
          responseBody,
          baseUrl,
        );

        if (captchaData != null) {
          return {
            'type': captchaType,
            'rutrackerData': captchaData,
          };
        }
      } else if (captchaType == CaptchaType.cloudflare) {
        // For CloudFlare, return the login URL
        return {
          'type': captchaType,
          'url': '$baseUrl/forum/login.php',
        };
      }
    } on Exception catch (e) {
      await StructuredLogger().log(
        level: 'warning',
        subsystem: 'auth',
        message: 'Failed to extract captcha data',
        context: 'direct_auth',
        cause: e.toString(),
      );
    }

    return null;
  }
}
