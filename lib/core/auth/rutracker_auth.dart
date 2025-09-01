import 'package:flutter/material.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:webview_cookie_manager/webview_cookie_manager.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart';
import 'package:cookie_jar/cookie_jar.dart';
import '../net/dio_client.dart';
import '../errors/failures.dart';

class RuTrackerAuth {
  final WebViewCookieManager _cookieManager = WebViewCookieManager();
  final CookieJar _cookieJar = CookieJar();
  final BuildContext _context;

  RuTrackerAuth(this._context);

  Future<bool> login(String username, String password) async {
    try {
      // Create a WebView for login
      final controller = WebViewController()
        ..setJavaScriptMode(JavaScriptMode.unrestricted)
        ..setNavigationDelegate(
          NavigationDelegate(
            onPageFinished: (String url) {
              // Check if login was successful by looking for user-specific content
              if (url.contains('profile.php')) {
                _syncCookies();
                Navigator.of(_context).pop(true);
              }
            },
          ),
        )
        ..loadRequest(Uri.parse('https://rutracker.org/forum/login.php'));

      // Show login dialog
      final result = await showDialog<bool>(
        context: _context,
        builder: (context) => AlertDialog(
          title: const Text('Login to RuTracker'),
          content: SizedBox(
            width: double.maxFinite,
            height: 400,
            child: WebViewWidget(controller: controller),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(false),
              child: const Text('Cancel'),
            ),
          ],
        ),
      );

      return result ?? false;
    } catch (e) {
      throw AuthFailure('Login failed: ${e.toString()}');
    }
  }

  Future<void> logout() async {
    try {
      // Clear WebView cookies
      await _cookieManager.clearCookies();
      
      // Clear CookieJar
      await _cookieJar.deleteAll();
      
      // Reset authentication state
      // TODO: Add any additional logout logic
    } catch (e) {
      throw AuthFailure('Logout failed: ${e.toString()}');
    }
  }

  Future<bool> get isLoggedIn async {
    try {
      final cookies = await _cookieManager.getCookies('https://rutracker.org');
      return cookies.any((cookie) => 
        cookie.name == 'bb_data' || cookie.name == 'bb_session');
    } catch (e) {
      return false;
    }
  }

  void _syncCookies() {
    // This will be called when login is successful
    // The cookies are already managed by WebViewCookieManager and DioClient
    // handles the sync through its CookieManager interceptor
  }
}