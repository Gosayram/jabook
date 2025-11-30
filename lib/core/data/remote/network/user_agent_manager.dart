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

import 'dart:io';

import 'package:device_info_plus/device_info_plus.dart';
import 'package:jabook/core/infrastructure/logging/environment_logger.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:path_provider/path_provider.dart';
import 'package:sembast/sembast_io.dart';
import 'package:webview_flutter/webview_flutter.dart';

/// Manages User-Agent synchronization between WebView and HTTP requests.
///
/// This class handles generating a dynamic User-Agent based on the device's
/// Android version (inspired by lissen-android), extracting it from WebView
/// as fallback, storing it in the database, and applying it to Dio requests
/// for consistent browser identification.
///
/// Note: This class is no longer a singleton. Use [userAgentManagerProvider]
/// to get an instance via dependency injection.
class UserAgentManager {
  /// Constructor for UserAgentManager.
  ///
  /// Use [userAgentManagerProvider] to get an instance via dependency injection.
  UserAgentManager();

  /// Key for storing User-Agent in the database.
  static const String _userAgentKey = 'user_agent';

  /// Chrome version to use in User-Agent string.
  /// Updated to match lissen-android for better compatibility.
  static const String _chromeVersion = '130.0.6723.106';

  /// Generates a dynamic User-Agent string based on the device's Android version.
  ///
  /// Inspired by lissen-android approach: uses actual Android version from device
  /// instead of hardcoded values. This ensures the User-Agent is always accurate
  /// and matches the device's actual Android version.
  ///
  /// Format: Mozilla/5.0 (Linux; Android {VERSION}; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/{CHROME_VERSION} Mobile Safari/537.36
  ///
  /// Returns a User-Agent string with dynamic Android version.
  static Future<String> _generateDynamicUserAgent() async {
    try {
      if (!Platform.isAndroid) {
        // Fallback for non-Android platforms
        return 'Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$_chromeVersion Mobile Safari/537.36';
      }

      final deviceInfo = DeviceInfoPlugin();
      final androidInfo = await deviceInfo.androidInfo;
      final androidVersion = androidInfo.version.release;

      // Generate User-Agent with dynamic Android version (inspired by lissen-android)
      return 'Mozilla/5.0 (Linux; Android $androidVersion; K) '
          'AppleWebKit/537.36 (KHTML, like Gecko) '
          'Chrome/$_chromeVersion Mobile Safari/537.36';
    } on Exception catch (e) {
      EnvironmentLogger().e('Failed to generate dynamic user agent: $e');
      // Fallback to default if device info is unavailable
      return 'Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$_chromeVersion Mobile Safari/537.36';
    }
  }

  /// Database instance for storing User-Agent data.
  Database? _db;

  /// Extracts User Agent from WebView through JavaScript.
  ///
  /// This provides a real browser User Agent with unique device characteristics.
  /// This is the preferred method as it gives legitimate User Agent that CloudFlare accepts.
  Future<String?> _extractUserAgentFromWebView() async {
    final operationId = 'extract_ua_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      await logger.log(
        level: 'warning', // Changed to 'warning' for visibility in release mode
        subsystem: 'user_agent',
        message: 'Starting User Agent extraction from WebView',
        operationId: operationId,
        context: 'ua_extraction',
      );

      final controller = WebViewController();
      await controller.setJavaScriptMode(JavaScriptMode.unrestricted);

      String? userAgent;

      await controller.addJavaScriptChannel(
        'UserAgentChannel',
        onMessageReceived: (message) {
          userAgent = message.message;
        },
      );

      await controller.loadHtmlString('''
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
        </head>
        <body>
            <script>
                UserAgentChannel.postMessage(navigator.userAgent);
            </script>
        </body>
        </html>
      ''');

      // Wait for User-Agent extraction with timeout
      await Future.delayed(const Duration(seconds: 2));

      final duration = DateTime.now().difference(startTime).inMilliseconds;

      if (userAgent != null && userAgent!.isNotEmpty) {
        await logger.log(
          level:
              'warning', // Changed to 'warning' for visibility in release mode
          subsystem: 'user_agent',
          message: 'User Agent successfully extracted from WebView',
          operationId: operationId,
          context: 'ua_extraction',
          durationMs: duration,
          extra: {
            'user_agent': userAgent,
            'user_agent_length': userAgent!.length,
            'source': 'webview',
          },
        );
        return userAgent;
      } else {
        await logger.log(
          level: 'warning',
          subsystem: 'user_agent',
          message: 'Failed to extract User Agent from WebView (empty result)',
          operationId: operationId,
          context: 'ua_extraction',
          durationMs: duration,
        );
        return null;
      }
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'user_agent',
        message: 'Failed to extract User Agent from WebView',
        operationId: operationId,
        context: 'ua_extraction',
        durationMs: duration,
        cause: e.toString(),
      );
      return null;
    }
  }

  /// Gets the current User-Agent string with proper priority.
  ///
  /// Priority order (CRITICAL: WebView first to avoid CloudFlare botnet detection):
  /// 1. Use stored User-Agent from database (if not force refresh)
  ///    - Usually User-Agent doesn't change for a device, so we cache it
  /// 2. Extract from WebView (as it was in version 1.1.4+9)
  ///    - This gives real browser User-Agent with unique device characteristics
  ///    - CloudFlare accepts this as legitimate
  /// 3. Fall back to generated dynamic User-Agent
  ///
  /// IMPORTANT: WebView extraction is preferred over dynamic generation because:
  /// - CloudFlare fingerprinting detects identical User-Agents as botnet
  /// - Real User-Agent from WebView has unique device characteristics
  /// - Each device should have unique User-Agent to avoid CloudFlare blocks
  Future<String> getUserAgent({bool forceRefresh = false}) async {
    final operationId = 'get_ua_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      await _initializeDatabase();

      // PRIORITY 1: Use stored User-Agent (if not force refresh)
      // Usually User-Agent doesn't change for a device, so we cache it
      if (!forceRefresh) {
        final storedUa = await _getStoredUserAgent();
        if (storedUa != null && storedUa.isNotEmpty) {
          await logger.log(
            level:
                'warning', // Changed to 'warning' for visibility in release mode
            subsystem: 'user_agent',
            message: 'Using stored User Agent from database',
            operationId: operationId,
            context: 'ua_get',
            durationMs: DateTime.now().difference(startTime).inMilliseconds,
            extra: {
              'user_agent': storedUa,
              'source': 'database',
            },
          );
          return storedUa;
        }
      }

      // PRIORITY 2: Extract from WebView (as it was in version 1.1.4+9)
      // This gives real browser User-Agent with unique device characteristics
      final webViewUa = await _extractUserAgentFromWebView();
      if (webViewUa != null && webViewUa.isNotEmpty) {
        await _storeUserAgent(webViewUa);

        await logger.log(
          level:
              'warning', // Changed to 'warning' for visibility in release mode
          subsystem: 'user_agent',
          message: 'User Agent extracted from WebView and stored',
          operationId: operationId,
          context: 'ua_get',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
          extra: {
            'user_agent': webViewUa,
            'source': 'webview',
            'stored': true,
          },
        );
        return webViewUa;
      }

      // PRIORITY 3: Fallback - use dynamic generation
      final fallbackUa = await _generateDynamicUserAgent();
      await _storeUserAgent(fallbackUa);

      await logger.log(
        level: 'warning',
        subsystem: 'user_agent',
        message:
            'Using fallback generated User Agent (WebView extraction failed)',
        operationId: operationId,
        context: 'ua_get',
        durationMs: DateTime.now().difference(startTime).inMilliseconds,
        extra: {
          'user_agent': fallbackUa,
          'source': 'generated',
        },
      );
      return fallbackUa;
    } on Exception catch (e) {
      final fallbackUa = await _generateDynamicUserAgent();
      await logger.log(
        level: 'error',
        subsystem: 'user_agent',
        message: 'Failed to get User Agent, using fallback',
        operationId: operationId,
        context: 'ua_get',
        durationMs: DateTime.now().difference(startTime).inMilliseconds,
        cause: e.toString(),
        extra: {
          'user_agent': fallbackUa,
          'source': 'fallback',
        },
      );
      return fallbackUa;
    }
  }

  /// Initializes the database.
  Future<void> _initializeDatabase() async {
    if (_db != null) return;

    final appDocumentDir = await getApplicationDocumentsDirectory();
    final dbPath = '${appDocumentDir.path}/jabook.db';

    _db = await databaseFactoryIo.openDatabase(dbPath);
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
      // If anything goes wrong, use the dynamic User-Agent
      final fallbackUa = await _generateDynamicUserAgent();
      dio.options.headers['User-Agent'] = fallbackUa;
    }
  }
}
