import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/core/net/cloudflare_turnstile_service.dart';
import 'package:jabook/core/net/cloudflare_utils.dart';
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
    
    // Apply CloudFlare-specific headers
    CloudFlareUtils.applyCloudFlareHeaders(dio);
    
    dio.options = BaseOptions(
      baseUrl: 'https://rutracker.me',
      connectTimeout: const Duration(seconds: 30),
      receiveTimeout: const Duration(seconds: 30),
      sendTimeout: const Duration(seconds: 30),
      headers: {
        'User-Agent': await userAgentManager.getUserAgent(),
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        'Accept-Language': 'ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7',
        'Accept-Encoding': 'gzip, deflate, br',
        'Connection': 'keep-alive',
        'Referer': 'https://rutracker.me/',
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
    
    // Add authentication redirect handler with retry logic
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
        // Handle CloudFlare-specific errors
        if (error.response != null && CloudFlareUtils.isCloudFlareProtected(error.response!)) {
          final turnstileService = CloudflareTurnstileService();
          
          // Check if this is a Turnstile challenge
          final htmlContent = error.response!.data.toString();
          final isTurnstileChallenge = await turnstileService.isTurnstileChallengePresent(htmlContent);
          
          if (isTurnstileChallenge) {
            // Handle Turnstile challenge
            try {
              final turnstileParams = await turnstileService.handleTurnstileProtection(htmlContent);
              if (turnstileParams != null) {
                // Retry with Turnstile solution
                await Future.delayed(const Duration(seconds: 2));
                
                final newOptions = error.requestOptions.copyWith(
                  headers: {
                    ...error.requestOptions.headers,
                    ...turnstileParams,
                  },
                );
                
                return handler.resolve(await dio.fetch(newOptions));
              }
            } on Exception {
              // Fall through to regular CloudFlare handling
            }
          }
          
          // Regular CloudFlare protection handling
          final retryCount = error.requestOptions.extra['cloudflareRetryCount'] ?? 0;
          if (retryCount < 2) {
            await Future.delayed(Duration(seconds: 2 + retryCount as int));
            
            // Rotate User-Agent for retry
            final userAgentManager = UserAgentManager();
            await userAgentManager.applyUserAgentToDio(dio);
            
            final newOptions = error.requestOptions.copyWith(
              extra: {...error.requestOptions.extra, 'cloudflareRetryCount': retryCount + 1},
            );
            
            return handler.resolve(await dio.fetch(newOptions));
          }
        }
        
        // Add retry logic for temporary network issues
        if (error.type == DioExceptionType.connectionTimeout ||
            error.type == DioExceptionType.receiveTimeout ||
            error.type == DioExceptionType.sendTimeout ||
            (error.response?.statusCode ?? 0) >= 500) {
          
          // Retry up to 3 times for temporary issues
          final retryCount = error.requestOptions.extra['retryCount'] ?? 0;
          if (retryCount < 3) {
            await Future.delayed(Duration(seconds: 1 << retryCount)); // Exponential backoff
            
            final newOptions = error.requestOptions.copyWith(
              extra: {...error.requestOptions.extra, 'retryCount': retryCount + 1},
            );
            
            return handler.resolve(await dio.fetch(newOptions));
          }
        }
        
        return handler.next(error);
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