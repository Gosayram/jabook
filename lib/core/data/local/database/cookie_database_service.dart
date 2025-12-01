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

import 'package:jabook/core/data/local/database/app_database.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:sembast/sembast.dart';

/// Service for managing cookies in the database.
///
/// This service provides a simple and reliable way to store and retrieve
/// authentication cookies, eliminating the need to sync between multiple
/// storage systems (CookieService, CookieJar, SecureStorage).
class CookieDatabaseService {
  /// Creates a new CookieDatabaseService instance.
  CookieDatabaseService(this._appDb);

  final AppDatabase _appDb;

  /// Saves cookies for a specific endpoint to the database.
  ///
  /// The [endpoint] parameter is the base URL (e.g., "https://rutracker.org").
  /// The [cookieHeader] parameter is the full cookie header string.
  ///
  /// Returns true if cookies were saved successfully, false otherwise.
  Future<bool> saveCookies(String endpoint, String cookieHeader) async {
    final operationId =
        'cookie_db_save_${DateTime.now().millisecondsSinceEpoch}';
    final startTime = DateTime.now();
    final logger = StructuredLogger();

    try {
      await logger.log(
        level: 'info',
        subsystem: 'cookies',
        message: 'Saving cookies to database',
        operationId: operationId,
        context: 'cookie_database_save',
        extra: {
          'endpoint': endpoint,
          'cookie_header_length': cookieHeader.length,
          'cookie_count': cookieHeader.split(';').length,
          // Don't log cookie values - they are sensitive
        },
      );

      if (cookieHeader.isEmpty) {
        await logger.log(
          level: 'warning',
          subsystem: 'cookies',
          message: 'Empty cookie header, not saving to database',
          operationId: operationId,
          context: 'cookie_database_save',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
        );
        return false;
      }

      // Ensure database is initialized before using it
      if (!_appDb.isInitialized) {
        await logger.log(
          level: 'warning',
          subsystem: 'cookies',
          message: 'Database not initialized, waiting for initialization',
          operationId: operationId,
          context: 'cookie_database_save',
        );

        // Wait for database initialization
        var retries = 0;
        const maxRetries = 50;
        const retryDelay = Duration(milliseconds: 100);

        while (!_appDb.isInitialized && retries < maxRetries) {
          await Future.delayed(retryDelay);
          retries++;
        }

        if (!_appDb.isInitialized) {
          await logger.log(
            level: 'error',
            subsystem: 'cookies',
            message: 'Database not initialized after waiting',
            operationId: operationId,
            context: 'cookie_database_save',
            durationMs: DateTime.now().difference(startTime).inMilliseconds,
          );
          return false;
        }
      }

      final store = _appDb.cookiesStore;
      final db = _appDb.database;
      final cookieData = {
        'cookie_header': cookieHeader,
        'saved_at': DateTime.now().toIso8601String(),
        'endpoint': endpoint,
      };

      // Check for required session cookies
      final hasBbSession = cookieHeader.contains('bb_session=');
      final hasBbData = cookieHeader.contains('bb_data=');

      await store.record(endpoint).put(db, cookieData);

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'info',
        subsystem: 'cookies',
        message: 'Cookies saved to database successfully',
        operationId: operationId,
        context: 'cookie_database_save',
        durationMs: duration,
        extra: {
          'endpoint': endpoint,
          'cookie_header_length': cookieHeader.length,
          'has_bb_session': hasBbSession,
          'has_bb_data': hasBbData,
          'has_required_cookies': hasBbSession || hasBbData,
        },
      );

      return true;
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'cookies',
        message: 'Failed to save cookies to database',
        operationId: operationId,
        context: 'cookie_database_save',
        durationMs: duration,
        cause: e.toString(),
        extra: {
          'endpoint': endpoint,
        },
      );
      return false;
    }
  }

  /// Gets cookies for a specific endpoint from the database.
  ///
  /// The [endpoint] parameter is the base URL (e.g., "https://rutracker.org").
  ///
  /// Returns the cookie header string if found, null otherwise.
  Future<String?> getCookies(String endpoint) async {
    final operationId =
        'cookie_db_get_${DateTime.now().millisecondsSinceEpoch}';
    final startTime = DateTime.now();
    final logger = StructuredLogger();

    try {
      await logger.log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'Getting cookies from database',
        operationId: operationId,
        context: 'cookie_database_get',
        extra: {
          'endpoint': endpoint,
        },
      );

      // Ensure database is initialized before using it
      if (!_appDb.isInitialized) {
        await logger.log(
          level: 'warning',
          subsystem: 'cookies',
          message: 'Database not initialized, waiting for initialization',
          operationId: operationId,
          context: 'cookie_database_get',
        );

        // Wait for database initialization
        var retries = 0;
        const maxRetries = 50;
        const retryDelay = Duration(milliseconds: 100);

        while (!_appDb.isInitialized && retries < maxRetries) {
          await Future.delayed(retryDelay);
          retries++;
        }

        if (!_appDb.isInitialized) {
          await logger.log(
            level: 'error',
            subsystem: 'cookies',
            message: 'Database not initialized after waiting',
            operationId: operationId,
            context: 'cookie_database_get',
            durationMs: DateTime.now().difference(startTime).inMilliseconds,
          );
          return null;
        }
      }

      final store = _appDb.cookiesStore;
      final db = _appDb.database;
      final cookieData = await store.record(endpoint).get(db);

      if (cookieData == null) {
        await logger.log(
          level: 'debug',
          subsystem: 'cookies',
          message: 'No cookies found in database for endpoint',
          operationId: operationId,
          context: 'cookie_database_get',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
          extra: {
            'endpoint': endpoint,
          },
        );
        return null;
      }

      final cookieHeader = cookieData['cookie_header'] as String?;
      if (cookieHeader == null || cookieHeader.isEmpty) {
        await logger.log(
          level: 'warning',
          subsystem: 'cookies',
          message: 'Empty cookie header in database',
          operationId: operationId,
          context: 'cookie_database_get',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
          extra: {
            'endpoint': endpoint,
          },
        );
        return null;
      }

      final savedAt = cookieData['saved_at'] as String?;
      final hasBbSession = cookieHeader.contains('bb_session=');
      final hasBbData = cookieHeader.contains('bb_data=');

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'info',
        subsystem: 'cookies',
        message: 'Cookies retrieved from database',
        operationId: operationId,
        context: 'cookie_database_get',
        durationMs: duration,
        extra: {
          'endpoint': endpoint,
          'cookie_header_length': cookieHeader.length,
          'saved_at': savedAt,
          'has_bb_session': hasBbSession,
          'has_bb_data': hasBbData,
          'has_required_cookies': hasBbSession || hasBbData,
        },
      );

      return cookieHeader;
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'cookies',
        message: 'Failed to get cookies from database',
        operationId: operationId,
        context: 'cookie_database_get',
        durationMs: duration,
        cause: e.toString(),
        extra: {
          'endpoint': endpoint,
        },
      );
      return null;
    }
  }

  /// Gets cookies for any RuTracker endpoint.
  ///
  /// This method tries to find cookies for the given endpoint, and if not found,
  /// tries other RuTracker domains.
  ///
  /// Returns the cookie header string if found, null otherwise.
  Future<String?> getCookiesForAnyEndpoint(String endpoint) async {
    final operationId =
        'cookie_db_get_any_${DateTime.now().millisecondsSinceEpoch}';
    final startTime = DateTime.now();
    final logger = StructuredLogger();

    try {
      await logger.log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'Getting cookies from database for any endpoint',
        operationId: operationId,
        context: 'cookie_database_get_any',
        extra: {
          'endpoint': endpoint,
        },
      );

      // Try the exact endpoint first
      var cookies = await getCookies(endpoint);
      if (cookies != null && cookies.isNotEmpty) {
        return cookies;
      }

      // Try other RuTracker domains
      final rutrackerDomains = [
        'https://rutracker.org',
        'https://rutracker.me',
        'https://rutracker.net',
      ];

      for (final domain in rutrackerDomains) {
        if (domain == endpoint) continue;

        cookies = await getCookies(domain);
        if (cookies != null && cookies.isNotEmpty) {
          final duration = DateTime.now().difference(startTime).inMilliseconds;
          await logger.log(
            level: 'info',
            subsystem: 'cookies',
            message: 'Found cookies for different endpoint',
            operationId: operationId,
            context: 'cookie_database_get_any',
            durationMs: duration,
            extra: {
              'requested_endpoint': endpoint,
              'found_endpoint': domain,
            },
          );
          return cookies;
        }
      }

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'No cookies found in database for any endpoint',
        operationId: operationId,
        context: 'cookie_database_get_any',
        durationMs: duration,
        extra: {
          'endpoint': endpoint,
          'checked_domains': rutrackerDomains,
        },
      );

      return null;
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'cookies',
        message: 'Failed to get cookies from database for any endpoint',
        operationId: operationId,
        context: 'cookie_database_get_any',
        durationMs: duration,
        cause: e.toString(),
        extra: {
          'endpoint': endpoint,
        },
      );
      return null;
    }
  }

  /// Clears all cookies from the database.
  ///
  /// Returns true if cookies were cleared successfully, false otherwise.
  Future<bool> clearCookies() async {
    final operationId =
        'cookie_db_clear_${DateTime.now().millisecondsSinceEpoch}';
    final startTime = DateTime.now();
    final logger = StructuredLogger();

    try {
      await logger.log(
        level: 'info',
        subsystem: 'cookies',
        message: 'Clearing all cookies from database',
        operationId: operationId,
        context: 'cookie_database_clear',
      );

      // Ensure database is initialized before using it
      if (!_appDb.isInitialized) {
        await logger.log(
          level: 'warning',
          subsystem: 'cookies',
          message: 'Database not initialized, waiting for initialization',
          operationId: operationId,
          context: 'cookie_database_clear',
        );

        // Wait for database initialization
        var retries = 0;
        const maxRetries = 50;
        const retryDelay = Duration(milliseconds: 100);

        while (!_appDb.isInitialized && retries < maxRetries) {
          await Future.delayed(retryDelay);
          retries++;
        }

        if (!_appDb.isInitialized) {
          await logger.log(
            level: 'error',
            subsystem: 'cookies',
            message: 'Database not initialized after waiting',
            operationId: operationId,
            context: 'cookie_database_clear',
            durationMs: DateTime.now().difference(startTime).inMilliseconds,
          );
          return false;
        }
      }

      final store = _appDb.cookiesStore;
      final db = _appDb.database;
      await store.delete(db);

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'info',
        subsystem: 'cookies',
        message: 'All cookies cleared from database',
        operationId: operationId,
        context: 'cookie_database_clear',
        durationMs: duration,
      );

      return true;
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'cookies',
        message: 'Failed to clear cookies from database',
        operationId: operationId,
        context: 'cookie_database_clear',
        durationMs: duration,
        cause: e.toString(),
      );
      return false;
    }
  }

  /// Checks if cookies exist in the database for the given endpoint.
  ///
  /// Returns true if cookies exist, false otherwise.
  Future<bool> hasCookies(String endpoint) async {
    final cookies = await getCookies(endpoint);
    return cookies != null && cookies.isNotEmpty;
  }
}
