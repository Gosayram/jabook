import 'dart:async';
import 'dart:convert';

import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart';
import 'package:jabook/core/endpoints/endpoint_manager.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/core/net/user_agent_manager.dart';
import 'package:jabook/data/db/app_database.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// HTTP client for making requests to RuTracker APIs.
///
/// This class provides a singleton Dio instance configured for
/// making HTTP requests to RuTracker with proper timeouts,
/// user agent, and cookie management.
class DioClient {
  /// Private constructor to prevent direct instantiation.
  const DioClient._();

  static CookieJar? _cookieJar;

  /// Gets the singleton Dio instance configured for RuTracker requests.
  ///
  /// This instance is configured with appropriate timeouts, user agent,
  /// and cookie management for RuTracker API calls.
  ///
  /// Returns a configured Dio instance ready for use.
  static Future<Dio> get instance async {
    final dio = Dio();
    final userAgentManager = UserAgentManager();

    // Apply User-Agent from manager
    await userAgentManager.applyUserAgentToDio(dio);

    // Resolve active RuTracker endpoint dynamically
    final db = AppDatabase().database;
    final endpointManager = EndpointManager(db);
    final activeBase = await endpointManager.getActiveEndpoint();

    dio.options = BaseOptions(
      baseUrl: activeBase,
      connectTimeout: const Duration(seconds: 30),
      receiveTimeout: const Duration(seconds: 30),
      sendTimeout: const Duration(seconds: 30),
      headers: {
        'User-Agent': await userAgentManager.getUserAgent(),
        'Accept':
            'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        'Accept-Language': 'ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7',
        'Accept-Encoding': 'gzip, deflate, br',
        'Connection': 'keep-alive',
        'Referer': '$activeBase/',
      },
    );

    // Add structured logging interceptor
    dio.interceptors.add(InterceptorsWrapper(
      onRequest: (request, handler) async {
        final logger = StructuredLogger();
        await logger.log(
          level: 'debug',
          subsystem: 'network',
          message: 'HTTP Request: ${request.method} ${request.uri}',
          extra: {
            'method': request.method,
            'url': request.uri.toString(),
            'headers': request.headers,
          },
        );
        return handler.next(request);
      },
      onResponse: (response, handler) async {
        final logger = StructuredLogger();
        await logger.log(
          level: 'info',
          subsystem: 'network',
          message: 'HTTP Response: ${response.statusCode} ${response.realUri}',
          extra: {
            'statusCode': response.statusCode,
            'url': response.realUri.toString(),
            'headers': response.headers.map,
          },
        );
        return handler.next(response);
      },
      onError: (error, handler) async {
        final logger = StructuredLogger();
        await logger.log(
          level: 'error',
          subsystem: 'network',
          message: 'HTTP Error: ${error.type} ${error.message}',
          cause: error.toString(),
          extra: {
            'type': error.type.toString(),
            'message': error.message,
            'url': error.requestOptions.uri.toString(),
            'statusCode': error.response?.statusCode,
          },
        );
        return handler.next(error);
      },
    ));

    // Add authentication redirect handler and resilient retry policy for idempotent requests
    dio.interceptors.add(InterceptorsWrapper(
      onResponse: (response, handler) {
        // Check if we got redirected to login page instead of the requested resource
        if (response.realUri.toString().contains('login.php') &&
            response.requestOptions.uri.toString().contains('rutracker')) {
          // This is an authentication redirect - reject with specific error
          return handler.reject(DioException(
            requestOptions: response.requestOptions,
            error: 'Authentication required',
            response: response,
            type: DioExceptionType.badResponse,
          ));
        }
        // Check for 401/403 status codes indicating authentication failure
        if (response.statusCode == 401 || response.statusCode == 403) {
          return handler.reject(DioException(
            requestOptions: response.requestOptions,
            error: 'Authentication required',
            response: response,
            type: DioExceptionType.badResponse,
          ));
        }
        return handler.next(response);
      },
      onError: (error, handler) async {
        // Handle authentication errors (401/403)
        final status = error.response?.statusCode ?? 0;
        if (status == 401 ||
            status == 403 ||
            (error.message?.contains('Authentication required') ?? false)) {
          // Try to sync cookies one more time before rejecting
          try {
            await syncCookiesFromWebView();
          } on Exception {
            // Continue to reject even if sync fails
          }
          // Reject with authentication error
          return handler.reject(DioException(
            requestOptions: error.requestOptions,
            error: 'Authentication required',
            response: error.response,
            type: DioExceptionType.badResponse,
          ));
        }

        // Retry logic for idempotent requests (GET, HEAD, OPTIONS) on temporary issues
        final method = error.requestOptions.method.toUpperCase();
        final isIdempotent =
            method == 'GET' || method == 'HEAD' || method == 'OPTIONS';
        final isTemporary = error.type == DioExceptionType.connectionTimeout ||
            error.type == DioExceptionType.receiveTimeout ||
            error.type == DioExceptionType.sendTimeout ||
            status == 429 ||
            (status >= 500 && status < 600);

        if (isIdempotent && isTemporary) {
          // Up to 3 retries with exponential backoff + jitter
          final retryCount =
              (error.requestOptions.extra['retryCount'] as int?) ?? 0;
          if (retryCount < 3) {
            int baseDelayMs;
            if (status == 429) {
              // Honor Retry-After if provided
              final retryAfter = error.response?.headers.value('retry-after');
              final retryAfterSeconds = int.tryParse(retryAfter ?? '') ?? 0;
              baseDelayMs = (retryAfterSeconds > 0
                  ? retryAfterSeconds * 1000
                  : (500 * (1 << retryCount)));
            } else {
              baseDelayMs = 500 * (1 << retryCount);
            }
            // jitter 0..250ms
            final jitterMs = DateTime.now().microsecondsSinceEpoch % 250;
            final delayMs = baseDelayMs + jitterMs;
            await StructuredLogger().log(
              level: 'warning',
              subsystem: 'network',
              message: 'Retrying request',
              extra: {
                'url': error.requestOptions.uri.toString(),
                'status': status,
                'retry': retryCount + 1,
                'delay_ms': delayMs,
              },
            );
            await Future.delayed(Duration(milliseconds: delayMs));

            final newOptions = error.requestOptions.copyWith(
              extra: {
                ...error.requestOptions.extra,
                'retryCount': retryCount + 1
              },
            );

            try {
              final retried = await dio.fetch(newOptions);
              return handler.resolve(retried);
            } on Exception {
              // Fall-through to next
            }
          }
        }

        return handler.next(error);
      },
    ));

    _cookieJar ??= CookieJar();
    dio.interceptors.add(CookieManager(_cookieJar!));

    return dio;
  }

  /// Gets the user agent string for HTTP requests.
  ///
  /// This method returns a user agent string that mimics a mobile browser
  /// to ensure compatibility with RuTracker's anti-bot measures.
  ///
  /// Returns a user agent string for HTTP requests.
  static Future<String> getUserAgent() async {
    final userAgentManager = UserAgentManager();
    return userAgentManager.getUserAgent();
  }

  /// Synchronizes cookies from WebView to the Dio client.
  ///
  /// This method should be called to ensure that authentication cookies
  /// obtained through WebView login are available for HTTP requests.
  ///
  /// It validates cookies before saving and handles various cookie formats.
  static Future<void> syncCookiesFromWebView() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final cookieJson = prefs.getString('rutracker_cookies_v1');
      if (cookieJson == null) {
        await StructuredLogger().log(
          level: 'debug',
          subsystem: 'auth',
          message: 'No cookies found in WebView storage',
        );
        return;
      }

      final db = AppDatabase().database;
      final endpointManager = EndpointManager(db);
      final activeBase = await endpointManager.getActiveEndpoint();
      final uri = Uri.parse(activeBase);

      // Parse cookies list from JSON (as saved by secure webview)
      List<dynamic> list;
      try {
        list = jsonDecode(cookieJson) as List<dynamic>;
      } on Exception catch (e) {
        await StructuredLogger().log(
          level: 'error',
          subsystem: 'auth',
          message: 'Failed to parse cookie JSON',
          cause: e.toString(),
        );
        return;
      }

      if (list.isEmpty) {
        await StructuredLogger().log(
          level: 'debug',
          subsystem: 'auth',
          message: 'Cookie list is empty',
        );
        return;
      }

      _cookieJar ??= CookieJar();
      final cookies = <Cookie>[];
      final validName = RegExp(r"^[!#\$%&'*+.^_`|~0-9A-Za-z-]+$");
      var skippedCount = 0;

      for (final c in list) {
        try {
          final m = Map<String, dynamic>.from(c as Map);
          var name = m['name']?.toString();
          var value = m['value']?.toString();

          if (name == null || value == null) {
            skippedCount++;
            continue;
          }

          // Trim and strip surrounding quotes
          name = name.trim();
          value = value.trim();
          if (name.isEmpty || value.isEmpty) {
            skippedCount++;
            continue;
          }

          if (name.length >= 2 && name.startsWith('"') && name.endsWith('"')) {
            name = name.substring(1, name.length - 1);
          }
          if (value.length >= 2 &&
              value.startsWith('"') &&
              value.endsWith('"')) {
            value = value.substring(1, value.length - 1);
          }

          // Validate cookie name format
          if (!validName.hasMatch(name)) {
            skippedCount++;
            continue;
          }

          // Extract domain, path, and other properties
          final domain = (m['domain']?.toString() ?? uri.host).trim();
          final path = (m['path']?.toString() ?? '/').trim();

          // Validate domain matches the endpoint
          if (!domain.contains(uri.host) && !domain.startsWith('.')) {
            // Allow subdomains and exact matches
            if (domain != uri.host && !domain.endsWith(uri.host)) {
              skippedCount++;
              continue;
            }
          }

          final cookie = Cookie(name, value)
            ..domain = domain
            ..path = path
            ..secure = true;

          cookies.add(cookie);
        } on Exception catch (e) {
          // Log individual cookie parsing errors but continue
          await StructuredLogger().log(
            level: 'debug',
            subsystem: 'auth',
            message: 'Failed to parse individual cookie',
            cause: e.toString(),
          );
          skippedCount++;
        }
      }

      if (cookies.isEmpty) {
        await StructuredLogger().log(
          level: 'warning',
          subsystem: 'auth',
          message: 'No valid cookies to sync after parsing',
          extra: {'total': list.length, 'skipped': skippedCount},
        );
        return;
      }

      // Save cookies for all rutracker domains (net, me, org) to ensure
      // cookies work when switching between mirrors
      final rutrackerDomains = [
        'rutracker.net',
        'rutracker.me',
        'rutracker.org'
      ];
      final savedDomains = <String>[];

      for (final domain in rutrackerDomains) {
        try {
          final domainUri = Uri.parse('https://$domain');
          // Filter cookies that belong to this domain or are domain-wide
          final domainCookies = cookies.where((cookie) {
            final cookieDomain = cookie.domain?.toLowerCase() ?? '';
            if (cookieDomain.isEmpty) return false;
            return cookieDomain == domain ||
                cookieDomain == '.$domain' ||
                cookieDomain.contains(domain) ||
                (cookieDomain.startsWith('.') &&
                    cookieDomain.substring(1) == domain);
          }).toList();

          if (domainCookies.isNotEmpty) {
            await _cookieJar!.saveFromResponse(domainUri, domainCookies);
            savedDomains.add(domain);
          }
        } on Exception catch (e) {
          await StructuredLogger().log(
            level: 'debug',
            subsystem: 'auth',
            message: 'Failed to save cookies for domain',
            cause: e.toString(),
            extra: {'domain': domain},
          );
        }
      }

      // Also save to active endpoint URI
      if (!savedDomains.contains(uri.host)) {
        await _cookieJar!.saveFromResponse(uri, cookies);
        savedDomains.add(uri.host);
      }

      await StructuredLogger().log(
        level: 'info',
        subsystem: 'auth',
        message: 'Synced cookies from WebView to Dio',
        extra: {
          'count': cookies.length,
          'total': list.length,
          'skipped': skippedCount,
          'domains': savedDomains,
        },
      );
    } on Exception catch (e) {
      await StructuredLogger().log(
        level: 'warning',
        subsystem: 'auth',
        message: 'Failed to sync cookies from WebView',
        cause: e.toString(),
      );
    }
  }

  /// Clears all stored cookies from the cookie jar.
  ///
  /// This method removes all cookies, effectively logging out the user
  /// from any authenticated sessions.
  static Future<void> clearCookies() async {
    await _cookieJar?.deleteAll();

    // Also clear from SharedPreferences
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('rutracker_cookies_v1');

    await StructuredLogger().log(
      level: 'info',
      subsystem: 'auth',
      message: 'Cleared all cookies',
    );
  }

  /// Checks if valid cookies are available for authentication.
  ///
  /// Returns true if cookies exist and appear to be valid, false otherwise.
  static Future<bool> hasValidCookies() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final cookieJson = prefs.getString('rutracker_cookies_v1');

      if (cookieJson == null || cookieJson.isEmpty) {
        return false;
      }

      // Check if cookies are in valid JSON format
      try {
        final list = jsonDecode(cookieJson) as List<dynamic>;
        if (list.isEmpty) {
          return false;
        }

        // Check if we have at least one cookie with a value
        var hasValidCookie = false;
        for (final c in list) {
          final cookie = c as Map<String, dynamic>;
          final name = cookie['name'] as String?;
          final value = cookie['value'] as String?;

          if (name != null &&
              name.isNotEmpty &&
              value != null &&
              value.isNotEmpty) {
            hasValidCookie = true;
            break;
          }
        }

        return hasValidCookie;
      } on FormatException {
        return false;
      }
    } on Exception {
      return false;
    }
  }

  /// Validates cookies by making a test request to RuTracker.
  ///
  /// Returns true if cookies are valid and authentication is active, false otherwise.
  static Future<bool> validateCookies() async {
    try {
      if (!await hasValidCookies()) {
        await StructuredLogger().log(
          level: 'debug',
          subsystem: 'auth',
          message: 'No cookies available for validation',
        );
        return false;
      }

      // Ensure cookies are synced
      await syncCookiesFromWebView();

      // Make a lightweight test request to check authentication
      final dio = await instance;
      final db = AppDatabase().database;
      final endpointManager = EndpointManager(db);
      final activeBase = await endpointManager.getActiveEndpoint();

      try {
        // Try to access a protected page (user profile or settings would require auth)
        // Using index.php as a lightweight test
        final response = await dio
            .get(
              '$activeBase/index.php',
              options: Options(
                validateStatus: (status) => status != null && status < 500,
                followRedirects: false,
              ),
            )
            .timeout(const Duration(seconds: 10));

        // If we get redirected to login, cookies are invalid
        final location = response.headers.value('location');
        final isRedirectedToLogin = location != null &&
            (location.contains('login.php') || location.contains('login'));

        if (isRedirectedToLogin) {
          await StructuredLogger().log(
            level: 'info',
            subsystem: 'auth',
            message: 'Cookies validation failed - redirected to login',
          );
          return false;
        }

        // If we get a successful response or a non-login redirect, cookies are likely valid
        final isValid = response.statusCode != null &&
            response.statusCode! >= 200 &&
            response.statusCode! < 400;

        await StructuredLogger().log(
          level: isValid ? 'info' : 'warning',
          subsystem: 'auth',
          message: 'Cookies validation ${isValid ? "succeeded" : "failed"}',
          extra: {'statusCode': response.statusCode},
        );

        return isValid;
      } on TimeoutException {
        await StructuredLogger().log(
          level: 'warning',
          subsystem: 'auth',
          message: 'Cookies validation timed out',
        );
        // Assume valid if request times out (network issue, not auth issue)
        return true;
      } on DioException catch (e) {
        if (e.response?.statusCode == 401 ||
            e.response?.statusCode == 403 ||
            (e.message?.contains('Authentication required') ?? false)) {
          await StructuredLogger().log(
            level: 'info',
            subsystem: 'auth',
            message: 'Cookies validation failed - authentication required',
          );
          return false;
        }
        // For other errors, assume cookies might still be valid
        await StructuredLogger().log(
          level: 'warning',
          subsystem: 'auth',
          message: 'Cookies validation error (assuming valid)',
          cause: e.toString(),
        );
        return true;
      }
    } on Exception catch (e) {
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'auth',
        message: 'Failed to validate cookies',
        cause: e.toString(),
      );
      return false;
    }
  }
}
