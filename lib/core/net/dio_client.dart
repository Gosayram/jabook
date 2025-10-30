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
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
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
          ));
        }
        return handler.next(response);
      },
      onError: (error, handler) async {
        // Retry logic for idempotent requests (GET, HEAD, OPTIONS) on temporary issues
        final method = error.requestOptions.method.toUpperCase();
        final isIdempotent = method == 'GET' || method == 'HEAD' || method == 'OPTIONS';
        final status = error.response?.statusCode ?? 0;
        final isTemporary = error.type == DioExceptionType.connectionTimeout ||
            error.type == DioExceptionType.receiveTimeout ||
            error.type == DioExceptionType.sendTimeout ||
            status == 429 ||
            (status >= 500 && status < 600);

        if (isIdempotent && isTemporary) {
          // Up to 3 retries with exponential backoff + jitter
          final retryCount = (error.requestOptions.extra['retryCount'] as int?) ?? 0;
          if (retryCount < 3) {
            int baseDelayMs;
            if (status == 429) {
              // Honor Retry-After if provided
              final retryAfter = error.response?.headers.value('retry-after');
              final retryAfterSeconds = int.tryParse(retryAfter ?? '') ?? 0;
              baseDelayMs = (retryAfterSeconds > 0 ? retryAfterSeconds * 1000 : (500 * (1 << retryCount)));
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
              extra: {...error.requestOptions.extra, 'retryCount': retryCount + 1},
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
  static Future<void> syncCookiesFromWebView() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final cookieJson = prefs.getString('rutracker_cookies_v1');
      if (cookieJson == null) return;

      final db = AppDatabase().database;
      final endpointManager = EndpointManager(db);
      final activeBase = await endpointManager.getActiveEndpoint();
      final uri = Uri.parse(activeBase);

      // Parse cookies list from JSON (as saved by secure webview)
      final list = jsonDecode(cookieJson) as List<dynamic>;

      _cookieJar ??= CookieJar();
      final cookies = <Cookie>[];
      for (final c in list) {
        final m = Map<String, dynamic>.from(c as Map);
        final name = m['name']?.toString();
        final value = m['value']?.toString();
        if (name == null || value == null) continue;
        final cookie = Cookie(name, value)
          ..domain = m['domain']?.toString() ?? uri.host
          ..path = m['path']?.toString() ?? '/'
          ..secure = true;
        cookies.add(cookie);
      }
      await _cookieJar!.saveFromResponse(uri, cookies);

      await StructuredLogger().log(
        level: 'info',
        subsystem: 'auth',
        message: 'Synced cookies from WebView to Dio',
        extra: {'count': cookies.length},
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
  }
}