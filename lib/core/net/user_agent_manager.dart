// Copyright 2025 Jabook Contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import 'package:jabook/core/logging/environment_logger.dart';
import 'package:path_provider/path_provider.dart';
import 'package:sembast/sembast_io.dart';
import 'package:webview_flutter/webview_flutter.dart';

/// Manages User-Agent synchronization between WebView and HTTP requests.
///
/// This class handles extracting the User-Agent from WebView, storing it
/// in the database, and applying it to Dio requests for consistent
/// browser identification.
class UserAgentManager {
  /// Private constructor for singleton pattern.
  UserAgentManager._();

  /// Factory constructor to get the singleton instance.
  factory UserAgentManager() => _instance;

  /// Singleton instance of the UserAgentManager.
  static final UserAgentManager _instance = UserAgentManager._();

  /// Key for storing User-Agent in the database.
  static const String _userAgentKey = 'user_agent';

  /// Default User-Agent string to use as fallback.
  /// Uses modern mobile browser User-Agent.
  static String get _defaultUserAgent =>
      'Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.210 Mobile Safari/537.36';

  /// Database instance for storing User-Agent data.
  Database? _db;

  /// Gets the current User-Agent string.
  ///
  /// First tries to get the stored User-Agent from the database,
  /// then extracts it from WebView if not available or if forced refresh.
  /// Falls back to default User-Agent if extraction fails.
  Future<String> getUserAgent({bool forceRefresh = false}) async {
    try {
      // Initialize database if not already done
      await _initializeDatabase();

      // Try to get stored User-Agent first (unless force refresh)
      if (!forceRefresh) {
        final storedUa = await _getStoredUserAgent();
        if (storedUa != null) {
          return storedUa;
        }
      }

      // Extract from WebView if available
      final webViewUa = await _extractUserAgentFromWebView();
      if (webViewUa != null) {
        await _storeUserAgent(webViewUa);
        return webViewUa;
      }

      // Fall back to default
      return _defaultUserAgent;
    } on Exception {
      // If anything goes wrong, return default User-Agent
      return _defaultUserAgent;
    }
  }

  /// Initializes the database.
  Future<void> _initializeDatabase() async {
    if (_db != null) return;

    final appDocumentDir = await getApplicationDocumentsDirectory();
    final dbPath = '${appDocumentDir.path}/jabook.db';

    _db = await databaseFactoryIo.openDatabase(dbPath);
  }

  /// Extracts User-Agent from WebView using JavaScript execution.
  ///
  /// Creates a temporary WebView to extract the actual User-Agent string
  /// from the browser's navigator.userAgent property via JavaScript.
  Future<String?> _extractUserAgentFromWebView() async {
    try {
      final controller = WebViewController();

      await controller.setJavaScriptMode(JavaScriptMode.unrestricted);

      String? userAgent;
      var scriptExecuted = false;

      await controller.setNavigationDelegate(
        NavigationDelegate(
          onPageFinished: (url) async {
            if (!scriptExecuted) {
              try {
                // Execute JavaScript to get the actual User-Agent
                final extractedUa = await controller
                        .runJavaScriptReturningResult('navigator.userAgent')
                    as String?;

                if (extractedUa != null && extractedUa.isNotEmpty) {
                  userAgent = extractedUa;
                } else {
                  // Fallback to default User-Agent
                  userAgent = _defaultUserAgent;
                }
              } on Exception {
                // If JavaScript execution fails, use default User-Agent
                userAgent = _defaultUserAgent;
              }
              scriptExecuted = true;
            }
          },
        ),
      );

      // Load a simple page to ensure JavaScript execution
      await controller.loadHtmlString('''
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
        </head>
        <body>
            <script>
                // Simple page to ensure User-Agent extraction works
            </script>
        </body>
        </html>
        ''');

      // Wait for User-Agent extraction with timeout
      await Future.delayed(const Duration(seconds: 2));

      return userAgent;
    } on Exception {
      // Fallback to default User-Agent
      return _defaultUserAgent;
    }
  }

  /// Stores the User-Agent in the database.
  Future<void> _storeUserAgent(String userAgent) async {
    try {
      if (_db == null) await _initializeDatabase();

      final store = StoreRef<String, Map<String, dynamic>>.main();
      await store.record(_userAgentKey).put(_db!, {
        'user_agent': userAgent,
        'updated_at': DateTime.now().toIso8601String(),
      });
    } on Exception catch (e) {
      // Log error but don't fail the operation
      EnvironmentLogger().e('Failed to store user agent: $e');
    }
  }

  /// Retrieves the stored User-Agent from the database.
  Future<String?> _getStoredUserAgent() async {
    try {
      if (_db == null) await _initializeDatabase();

      final store = StoreRef<String, Map<String, dynamic>>.main();
      final record = await store.record(_userAgentKey).get(_db!);
      return record?['user_agent'] as String?;
    } on Exception {
      return null;
    }
  }

  /// Clears the stored User-Agent from the database.
  Future<void> clearUserAgent() async {
    try {
      if (_db == null) await _initializeDatabase();

      final store = StoreRef<String, Map<String, dynamic>>.main();
      await store.record(_userAgentKey).delete(_db!);
    } on Exception catch (e) {
      // Log error but don't fail the operation
      EnvironmentLogger().e('Failed to clear user agent: $e');
    }
  }

  /// Updates the User-Agent and refreshes it periodically.
  ///
  /// This method should be called on app start to ensure the User-Agent
  /// is up-to-date with the latest browser version.
  Future<void> refreshUserAgent() async {
    await getUserAgent(forceRefresh: true);
  }

  /// Applies the current User-Agent to a Dio instance.
  ///
  /// This method should be called when creating Dio instances to ensure
  /// they use the correct User-Agent.
  Future<void> applyUserAgentToDio(dynamic dio) async {
    try {
      final userAgent = await getUserAgent();
      dio.options.headers['User-Agent'] = userAgent;
    } on Exception {
      // If anything goes wrong, use the default User-Agent
      dio.options.headers['User-Agent'] = _defaultUserAgent;
    }
  }
}
