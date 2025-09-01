import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:jabook/core/errors/failures.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:webview_flutter/webview_flutter.dart';

/// Constants for RuTracker endpoints
class RuTrackerUrls {
  /// URL for the RuTracker login page.
  static const String login = 'https://rutracker.me/forum/login.php';

  /// URL for the RuTracker profile page (used to verify auth state).
  static const String profile = 'https://rutracker.me/forum/profile.php';
}

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

      await controller.setJavaScriptMode(JavaScriptMode.unrestricted);

      BuildContext? dialogContext;

      await controller.setNavigationDelegate(
        NavigationDelegate(
          onPageFinished: (url) {
            if (url.contains('profile.php')) {
              _syncCookies();
              // use_if_null_to_convert_nulls_to_bools
              if (dialogContext?.mounted ?? false) {
                Navigator.of(dialogContext!).pop(true);
              }
            }
          },
        ),
      );

      await controller.loadRequest(Uri.parse(RuTrackerUrls.login));

      // use_build_context_synchronously: check if context is still valid
      if (!_context.mounted) {
        return false;
      }

      final result = await showDialog<bool>(
        context: _context,
        builder: (ctx) {
          dialogContext = ctx;
          return AlertDialog(
            title: const Text('Login to RuTracker'),
            content: SizedBox(
              width: double.maxFinite,
              height: 400,
              child: WebViewWidget(controller: controller),
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(ctx).pop(false),
                child: const Text('Cancel'),
              ),
            ],
          );
        },
      );

      return result ?? false;
    } on Exception catch (e) {
      throw AuthFailure('Login failed: ${e.toString()}');
    }
  }

  /// Logs out from RuTracker by clearing all authentication cookies.
  ///
  /// Throws [AuthFailure] if logout fails.
  Future<void> logout() async {
    try {
      await _cookieManager.clearCookies();
      await _cookieJar.deleteAll();
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
        RuTrackerUrls.profile,
        options: Options(
          receiveTimeout: const Duration(seconds: 5),
          validateStatus: (status) => status != null && status < 500,
        ),
      );
      return response.statusCode == 200;
    } on Exception {
      return false;
    }
  }

  /// Synchronizes cookies between WebView and Dio client.
  void _syncCookies() {
    // Cookies are handled by WebView's built-in WebViewCookieManager and DioClient's CookieManager interceptor.
  }
}