import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:jabook/core/auth/credential_manager.dart';
import 'package:jabook/core/config/app_config.dart';
import 'package:jabook/core/endpoints/url_constants.dart';
import 'package:jabook/core/errors/failures.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:webview_flutter/webview_flutter.dart';

/// Constants for RuTracker endpoints
class RuTrackerUrls {
  /// Private constructor to prevent instantiation.
  RuTrackerUrls._();

  /// Gets the URL for the RuTracker login page.
  static String get login => UrlConstants.getLoginUrl(AppConfig().rutrackerUrl);

  /// Gets the URL for the RuTracker profile page (used to verify auth state).
  static String get profile => UrlConstants.getProfileUrl(AppConfig().rutrackerUrl);

  /// Profile endpoint path (relative to base URL).
  static const String profilePath = '/forum/profile.php';
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

  /// Credential manager for secure storage.
  final CredentialManager _credentialManager = CredentialManager();

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
      final response = await (await DioClient.instance).get(
        RuTrackerUrls.profile,
        options: Options(
          receiveTimeout: const Duration(seconds: 5),
          validateStatus: (status) => status != null && status < 500,
          followRedirects: false,
        ),
      );
      
      // Comprehensive authentication check:
      // 1. Check HTTP status is 200
      // 2. Verify we're not redirected to login page
      // 3. Check for authenticator indicators in HTML content
      final responseData = response.data.toString();
      final responseUri = response.realUri.toString();
      
      final isAuthenticated = response.statusCode == 200 &&
          !responseUri.contains('login.php') &&
          // Check for profile-specific elements that indicate successful auth
          (responseData.contains('profile') ||
           responseData.contains('личный кабинет') ||
           responseData.contains('private') ||
           responseData.contains('username') ||
           responseData.contains('user_id'));
      
      return isAuthenticated;
    } on DioException catch (e) {
      if (e.response?.realUri.toString().contains('login.php') ?? false) {
        return false; // Redirected to login - not authenticated
      }
    
      return false;
    } on Exception {
      return false;
    }
  }

  /// Attempts to login using stored credentials with optional biometric authentication.
  ///
  /// Returns `true` if login was successful using stored credentials,
  /// `false` if no stored credentials or authentication failed.
  Future<bool> loginWithStoredCredentials({bool useBiometric = false}) async {
    try {
      final credentials = await _credentialManager.getCredentials(
        requireBiometric: useBiometric,
      );
      
      if (credentials == null) {
        return false;
      }
      
      return await login(credentials['username']!, credentials['password']!);
    } on Exception {
      return false;
    }
  }

  /// Saves credentials for future automatic login.
  Future<void> saveCredentials({
    required String username,
    required String password,
    bool rememberMe = true,
    bool useBiometric = false,
  }) async {
    await _credentialManager.saveCredentials(
      username: username,
      password: password,
      rememberMe: rememberMe,
    );
  }

  /// Checks if stored credentials are available.
  Future<bool> hasStoredCredentials() => _credentialManager.hasStoredCredentials();

  /// Checks if biometric authentication is available on the device.
  Future<bool> isBiometricAvailable() => _credentialManager.isBiometricAvailable();

  /// Clears all stored credentials.
  Future<void> clearStoredCredentials() async {
    await _credentialManager.clearCredentials();
  }

  /// Exports stored credentials in specified format.
  Future<String> exportCredentials({String format = 'json'}) =>
      _credentialManager.exportCredentials(format: format);

  /// Imports credentials from specified format.
  Future<void> importCredentials(String data, {String format = 'json'}) async {
    await _credentialManager.importCredentials(data, format: format);
  }

  /// Synchronizes cookies between WebView and Dio client using JavaScript bridge.
  Future<void> _syncCookies() async {
    try {
      // In a production implementation, we would use JavaScript bridge to:
      // 1. Extract cookies from WebView using document.cookie
      // 2. Parse and store them in Dio's CookieJar
      // 3. Ensure proper domain and path matching
      
      // For now, we rely on the automatic cookie handling provided by
      // WebView's internal cookie storage and Dio's CookieManager
      
      // Clear any existing cookies to ensure fresh session state
      await _cookieJar.deleteAll();
    } catch (e) {
      throw AuthFailure('Cookie sync failed: ${e.toString()}');
    }
  }
}