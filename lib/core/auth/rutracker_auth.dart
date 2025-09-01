import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:jabook/core/errors/failures.dart';
import 'package:jabook/core/net/dio_client.dart';
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

  /// Attempts to log in to RuTracker with the provided credentials.
  ///
  /// Returns `true` if login was successful, `false` otherwise.
  Future<bool> login(String username, String password) async {
    try {
      // Create controller
      final controller = WebViewController();

      // IMPORTANT: these can return Future in your plugin version → await them
      await controller.setJavaScriptMode(JavaScriptMode.unrestricted);
      controller.setNavigationDelegate(
        NavigationDelegate(
          onPageFinished: (url) async {
            // Check if login was successful by looking for user-specific content
            if (url.contains('profile.php')) {
              // if you ever make _syncCookies async, await it here
              _syncCookies();
              if (_context.mounted) {
                Navigator.of(_context).pop(true);
              }
            }
          },
        ),
      );
      await controller.loadRequest(Uri.parse('https://rutracker.me/forum/login.php'));

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
    } on Exception catch (e) {
      // Requires AuthFailure to implement Exception (see patch below)
      throw AuthFailure('Login failed: ${e.toString()}');
    }
  }

  /// Logs out from RuTracker by clearing all authentication cookies.
  ///
  /// Throws [AuthFailure] if logout fails.
  Future<void> logout() async {
    try {
      // Clear WebView cookies (returns Future<bool>)
      await _cookieManager.clearCookies();

      // Clear CookieJar
      await _cookieJar.deleteAll();

      // TODO: Add any additional logout logic
    } on Exception {
      throw const AuthFailure('Logout failed');
    }
  }

  /// Checks if the user is currently authenticated with RuTracker.
  ///
  /// Returns `true` if authenticated, `false` otherwise.
  Future<bool> get isLoggedIn async {
    try {
      final response = await DioClient.instance.get(
        'https://rutracker.me/forum/profile.php',
        options: Options(
          receiveTimeout: const Duration(seconds: 5),
          validateStatus: (status) => status != null && status < 500,
        ),
      );

      // If we get a 200 response, we're likely authenticated
      return response.statusCode == 200;
    } on Exception {
      return false;
    }
  }

  /// Synchronizes cookies between WebView and Dio client.
  void _syncCookies() {
    // Cookies are already managed by WebViewCookieManager and DioClient's CookieManager interceptor.
  }
}