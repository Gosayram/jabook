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
  /// This method opens a WebView dialog for the user to complete the
  /// login process manually. It monitors the navigation to detect
  /// successful login and syncs cookies accordingly.
  ///
  /// The [username] and [password] parameters are not directly used
  /// as the login is performed through the WebView interface.
  ///
  /// Returns `true` if login was successful, `false` otherwise.
  /// Attempts to log in to RuTracker with the provided credentials.
  ///
  /// This method opens a WebView dialog for the user to complete the
  /// login process manually. It monitors the navigation to detect
  /// successful login and syncs cookies accordingly.
  ///
  /// The [username] and [password] parameters are not directly used
  /// as the login is performed through the WebView interface.
  ///
  /// Returns `true` if login was successful, `false` otherwise.
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
    } on Exception catch (e) {
      throw AuthFailure('Login failed: ${e.toString()}');
    }
  }

  /// Logs out from RuTracker by clearing all authentication cookies.
  ///
  /// This method clears cookies from both WebView and Dio client,
  /// effectively ending the current session.
  ///
  /// Throws [AuthFailure] if logout fails.
  /// Logs out from RuTracker by clearing all authentication cookies.
  ///
  /// This method clears cookies from both WebView and Dio client,
  /// effectively ending the current session.
  ///
  /// Throws [AuthFailure] if logout fails.
  Future<void> logout() async {
    try {
      // Clear WebView cookies
      _cookieManager.clearCookies();
      
      // Clear CookieJar
      await _cookieJar.deleteAll();
      
      // Reset authentication state
      // TODO: Add any additional logout logic
    } on Exception catch (e) {
      throw const AuthFailure('Logout failed');
    }
  }

  /// Checks if the user is currently authenticated with RuTracker.
  ///
  /// This method attempts to access a protected page to verify
  /// authentication status. Returns `true` if access is granted,
  /// `false` otherwise.
  ///
  /// Returns `true` if authenticated, `false` if not or if an error occurs.
  /// Checks if the user is currently authenticated with RuTracker.
  ///
  /// This method attempts to access a protected page to verify
  /// authentication status. Returns `true` if access is granted,
  /// `false` otherwise.
  ///
  /// Returns `true` if authenticated, `false` if not or if an error occurs.
  Future<bool> get isLoggedIn async {
    try {
      // Try to access a protected page to check if we're authenticated
      final response = await DioClient.instance.get(
        'https://rutracker.me/forum/profile.php',
        options: Options(
          receiveTimeout: const Duration(seconds: 5),
          validateStatus: (status) => status! < 500,
        ),
      );
      
      // If we get a 200 response, we're likely authenticated
      return response.statusCode == 200;
    } on Exception catch (e) {
      return false;
    }
  }

  /// Synchronizes cookies between WebView and Dio client.
  ///
  /// This method is called when login is successful to ensure
  /// that authentication cookies are properly shared between
  /// WebView and Dio client for subsequent API calls.
  ///
  /// Note: Cookie synchronization is handled automatically by
  /// the WebViewCookieManager and DioClient's CookieManager interceptor.
  void _syncCookies() {
    // This will be called when login is successful
    // The cookies are already managed by WebViewCookieManager and DioClient
    // handles the sync through its CookieManager interceptor
  }
}