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
import 'package:jabook/core/logging/environment_logger.dart';
import 'package:path_provider/path_provider.dart';
import 'package:sembast/sembast_io.dart';

/// Manages User-Agent synchronization between WebView and HTTP requests.
///
/// This class handles generating a dynamic User-Agent based on the device's
/// Android version (inspired by lissen-android), extracting it from WebView
/// as fallback, storing it in the database, and applying it to Dio requests
/// for consistent browser identification.
class UserAgentManager {
  /// Private constructor for singleton pattern.
  UserAgentManager._();

  /// Factory constructor to get the singleton instance.
  factory UserAgentManager() => _instance;

  /// Singleton instance of the UserAgentManager.
  static final UserAgentManager _instance = UserAgentManager._();

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

  /// Gets the current User-Agent string.
  ///
  /// Priority order (inspired by lissen-android approach):
  /// 1. Generate dynamic User-Agent based on device's Android version (preferred)
  /// 2. Try to get stored User-Agent from the database (if not force refresh)
  /// 3. Extract from WebView if available (fallback)
  /// 4. Fall back to generated dynamic User-Agent
  ///
  /// The dynamic approach is preferred because it:
  /// - Always uses the correct Android version for the device
  /// - Doesn't require creating a temporary WebView
  /// - Is faster and more reliable
  /// - Matches the approach used in lissen-android
  Future<String> getUserAgent({bool forceRefresh = false}) async {
    try {
      // First, try to generate dynamic User-Agent (inspired by lissen-android)
      // This is the preferred approach as it's faster and always accurate
      final dynamicUa = await _generateDynamicUserAgent();

      // If force refresh, always use dynamic User-Agent
      if (forceRefresh) {
        await _storeUserAgent(dynamicUa);
        return dynamicUa;
      }

      // Try to get stored User-Agent from database
      await _initializeDatabase();
      final storedUa = await _getStoredUserAgent();
      if (storedUa != null && storedUa.isNotEmpty) {
        // Verify stored User-Agent is still valid (contains Chrome version)
        if (storedUa.contains('Chrome/') && storedUa.contains('Android')) {
          return storedUa;
        }
        // If stored User-Agent is invalid, regenerate and store
        await _storeUserAgent(dynamicUa);
        return dynamicUa;
      }

      // Store and return dynamic User-Agent
      await _storeUserAgent(dynamicUa);
      return dynamicUa;
    } on Exception catch (e) {
      EnvironmentLogger().e('Failed to get user agent: $e');
      // Fall back to generated dynamic User-Agent
      return _generateDynamicUserAgent();
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
