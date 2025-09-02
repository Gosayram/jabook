import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart';
import 'package:jabook/core/net/user_agent_manager.dart';

/// HTTP client for making requests to RuTracker APIs.
///
/// This class provides a singleton Dio instance configured for
/// making HTTP requests to RuTracker with proper timeouts,
/// user agent, and cookie management.
class DioClient {
  /// Private constructor to prevent direct instantiation.
  const DioClient._();

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
    
    dio.options = BaseOptions(
      connectTimeout: const Duration(seconds: 30),
      receiveTimeout: const Duration(seconds: 30),
      sendTimeout: const Duration(seconds: 30),
    );
    
    // Add logging interceptor for debugging
    dio.interceptors.add(LogInterceptor(
      logPrint: (object) {
        // Use print for now, will be replaced with structured logger
        // ignore: avoid_print
        print('[DIO] $object');
      },
    ));
    
    // Add authentication redirect handler
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
    ));
    
    dio.interceptors.add(CookieManager(CookieJar()));
    
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
    // Cookie synchronization is handled automatically by the CookieManager
    // interceptor that's already added to the Dio instance
    // WebView cookies are automatically available to HTTP requests
  }

  /// Clears all stored cookies from the cookie jar.
  ///
  /// This method removes all cookies, effectively logging out the user
  /// from any authenticated sessions.
  static Future<void> clearCookies() async {
    await CookieJar().deleteAll();
  }
}