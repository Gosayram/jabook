import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart';

class DioClient {
  const DioClient._();

  static Dio get instance {
    final dio = Dio();
    final cookieJar = CookieJar();
    
    dio.options = BaseOptions(
      connectTimeout: const Duration(seconds: 30),
      receiveTimeout: const Duration(seconds: 30),
      sendTimeout: const Duration(seconds: 30),
      headers: {
        'User-Agent': _getUserAgent(),
      },
    );
    
    dio.interceptors.add(CookieManager(cookieJar));
    
    return dio;
  }

  static String _getUserAgent() {
    // TODO: Extract from WebView on first init
    return 'Mozilla/5.0 (Linux; Android 10; SM-G973F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36';
  }

  static Future<void> syncCookiesFromWebView() async {
    // TODO: Implement cookie sync from WebView when WebView is available
    // This requires WebView instance to be initialized first
    throw UnimplementedError('Cookie sync from WebView not implemented yet');
  }

  static Future<void> clearCookies() async {
    final cookieJar = CookieJar();
    await cookieJar.deleteAll();
  }
}