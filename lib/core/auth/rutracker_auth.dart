import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:webview_flutter/webview_flutter.dart';

import '../errors/failures.dart';
import '../net/dio_client.dart';

class RuTrackerAuth {
  final WebViewCookieManager _cookieManager = WebViewCookieManager();
  final CookieJar _cookieJar = CookieJar();
  final BuildContext _context;

  RuTrackerAuth._(this._context);

  factory RuTrackerAuth(BuildContext context) => RuTrackerAuth._(context);

  Future<bool> login(String username, String password) async {
    try {
      // Create a WebView for login
      final controller = WebViewController()
        ..setJavaScriptMode(JavaScriptMode.unrestricted)
        ..setNavigationDelegate(
          NavigationDelegate(
            onPageFinished: (url) {
              // Check if login was successful by looking for user-specific content
              if (url.contains('profile.php')) {
                _syncCookies();
                Navigator.of(_context).pop(true);
              }
            },
          ),
        )
        ..loadRequest(Uri.parse('https://rutracker.me/forum/login.php'));

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
      _cookieManager.clearCookies();
      
      // Clear CookieJar
      await _cookieJar.deleteAll();
      
      // Reset authentication state
      // TODO: Add any additional logout logic
    } on Exception {
      throw AuthFailure('Logout failed');
    }
  }

  Future<bool> get isLoggedIn async {
    try {
      // Try to access a protected page to check if we're authenticated
      final response = await DioClient.instance.get(
        'https://rutracker.org/forum/profile.php',
        options: Options(
          receiveTimeout: const Duration(seconds: 5),
          validateStatus: (status) => status! < 500,
        ),
      );
      
      // If we get a 200 response, we're likely authenticated
      return response.statusCode == 200;
    } on Exception {
      return false;
    }
  }

  void _syncCookies() {
    // This will be called when login is successful
    // The cookies are already managed by WebViewCookieManager and DioClient
    // handles the sync through its CookieManager interceptor
  }
}