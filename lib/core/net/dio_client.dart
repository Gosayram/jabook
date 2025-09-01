import 'package:dio/dio.dart';
import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:webview_cookie_manager/webview_cookie_manager.dart';

class DioClient {
  static Dio? _instance;
  static CookieJar? _cookieJar;
  static WebViewCookieManager? _cookieManager;

  static Dio get instance {
    _instance ??= Dio();
    _cookieJar ??= CookieJar();
    _cookieManager ??= WebViewCookieManager();
    
    _instance!.options = BaseOptions(
      connectTimeout: const Duration(seconds: 30),
      receiveTimeout: const Duration(seconds: 30),
      sendTimeout: const Duration(seconds: 30),
      headers: {
        'User-Agent': _getUserAgent(),
      },
    );
    
    _instance!.interceptors.add(CookieManager(_cookieJar!));
    
    return _instance!;
  }

  static String _getUserAgent() {
    // TODO: Extract from WebView on first init
    return 'Mozilla/5.0 (Linux; Android 10; SM-G973F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36';
  }

  static Future<void> syncCookiesFromWebView() async {
    if (_cookieManager == null) return;
    
    final cookies = await _cookieManager!.getCookies('https://rutracker.org');
    if (_cookieJar != null) {
      _cookieJar!.saveFromResponse(
        Uri.parse('https://rutracker.org'),
        cookies,
      );
    }
  }

  static Future<void> clearCookies() async {
    if (_cookieJar != null) {
      await _cookieJar!.deleteAll();
    }
    if (_cookieManager != null) {
      await _cookieManager!.clearCookies();
    }
  }
}