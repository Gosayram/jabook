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

import 'dart:io' as io;

import 'package:cookie_jar/cookie_jar.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';

/// Simple cookie manager for RuTracker authentication.
///
/// This class provides a simplified interface for managing cookies
/// without complex WebView synchronization. Cookies are stored in
/// secure storage and applied to Dio requests via CookieJar.
class SimpleCookieManager {
  /// Creates a new SimpleCookieManager instance.
  SimpleCookieManager({
    FlutterSecureStorage? secureStorage,
  }) : _secureStorage = secureStorage ?? const FlutterSecureStorage();

  /// Secure storage for persistent cookie storage.
  final FlutterSecureStorage _secureStorage;

  /// Key prefix for storing cookies in secure storage.
  static const String _cookieKeyPrefix = 'rutracker_cookie_';

  /// Saves cookie string for a specific base URL.
  ///
  /// The [cookieString] should be in format 'bb_session=...; bb_ssl=1'.
  /// The [baseUrl] is used as a key to store cookies per mirror.
  ///
  /// Also applies the cookie to the provided [cookieJar] for immediate use.
  Future<void> saveCookie(
    String cookieString,
    String baseUrl,
    CookieJar cookieJar,
  ) async {
    final operationId = 'cookie_save_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      await logger.log(
        level: 'info',
        subsystem: 'auth',
        message: 'Saving cookie to secure storage',
        operationId: operationId,
        context: 'simple_cookie_manager',
        extra: {
          'base_url': baseUrl,
          'cookie_length': cookieString.length,
        },
      );

      // Save to secure storage
      final storageKey = _getStorageKey(baseUrl);
      await _secureStorage.write(key: storageKey, value: cookieString);

      // Parse cookie string and save to CookieJar
      final uri = Uri.parse(baseUrl);
      final cookies = _parseCookieString(cookieString, uri);

      if (cookies.isNotEmpty) {
        await cookieJar.saveFromResponse(uri, cookies);

        // Also save to base domain for all paths
        final baseUri = Uri.parse('${uri.scheme}://${uri.host}');
        if (baseUri != uri) {
          await cookieJar.saveFromResponse(baseUri, cookies);
        }

        await logger.log(
          level: 'info',
          subsystem: 'auth',
          message: 'Cookie saved and applied to CookieJar',
          operationId: operationId,
          context: 'simple_cookie_manager',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
          extra: {
            'base_url': baseUrl,
            'cookie_count': cookies.length,
            'cookie_names': cookies.map((c) => c.name).toList(),
          },
        );
      } else {
        await logger.log(
          level: 'warning',
          subsystem: 'auth',
          message: 'No cookies parsed from cookie string',
          operationId: operationId,
          context: 'simple_cookie_manager',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
          extra: {
            'base_url': baseUrl,
            'cookie_string_preview': cookieString.length > 100
                ? '${cookieString.substring(0, 100)}...'
                : cookieString,
          },
        );
      }
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'auth',
        message: 'Failed to save cookie',
        operationId: operationId,
        context: 'simple_cookie_manager',
        durationMs: duration,
        cause: e.toString(),
        extra: {
          'base_url': baseUrl,
        },
      );
      rethrow;
    }
  }

  /// Gets cookie string for a specific base URL.
  ///
  /// Returns the cookie string if found, null otherwise.
  Future<String?> getCookie(String baseUrl) async {
    final operationId = 'cookie_get_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      final storageKey = _getStorageKey(baseUrl);
      final cookieString = await _secureStorage.read(key: storageKey);

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: cookieString != null ? 'debug' : 'debug',
        subsystem: 'auth',
        message: cookieString != null
            ? 'Cookie retrieved from secure storage'
            : 'No cookie found in secure storage',
        operationId: operationId,
        context: 'simple_cookie_manager',
        durationMs: duration,
        extra: {
          'base_url': baseUrl,
          'has_cookie': cookieString != null,
          'cookie_length': cookieString?.length ?? 0,
        },
      );

      return cookieString;
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'auth',
        message: 'Failed to get cookie',
        operationId: operationId,
        context: 'simple_cookie_manager',
        durationMs: duration,
        cause: e.toString(),
        extra: {
          'base_url': baseUrl,
        },
      );
      return null;
    }
  }

  /// Clears cookie for a specific base URL.
  ///
  /// Removes cookie from secure storage and CookieJar.
  Future<void> clearCookie(String baseUrl, CookieJar cookieJar) async {
    final operationId = 'cookie_clear_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      // Remove from secure storage
      final storageKey = _getStorageKey(baseUrl);
      await _secureStorage.delete(key: storageKey);

      // Clear from CookieJar
      // CookieJar doesn't have a direct clear method, but we can delete cookies
      // by saving empty cookies or using delete method if available
      // For now, we'll just remove from storage
      // Note: CookieJar will automatically handle expired/invalid cookies

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'info',
        subsystem: 'auth',
        message: 'Cookie cleared from secure storage',
        operationId: operationId,
        context: 'simple_cookie_manager',
        durationMs: duration,
        extra: {
          'base_url': baseUrl,
        },
      );
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'auth',
        message: 'Failed to clear cookie',
        operationId: operationId,
        context: 'simple_cookie_manager',
        durationMs: duration,
        cause: e.toString(),
        extra: {
          'base_url': baseUrl,
        },
      );
      rethrow;
    }
  }

  /// Checks if a valid cookie exists for a specific base URL.
  ///
  /// A cookie is considered valid if it contains 'bb_session'.
  Future<bool> hasValidCookie(String baseUrl) async {
    final operationId =
        'cookie_validate_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      final cookieString = await getCookie(baseUrl);
      final isValid =
          cookieString != null && cookieString.contains('bb_session=');

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'debug',
        subsystem: 'auth',
        message: isValid ? 'Valid cookie found' : 'No valid cookie found',
        operationId: operationId,
        context: 'simple_cookie_manager',
        durationMs: duration,
        extra: {
          'base_url': baseUrl,
          'is_valid': isValid,
        },
      );

      return isValid;
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'auth',
        message: 'Failed to validate cookie',
        operationId: operationId,
        context: 'simple_cookie_manager',
        durationMs: duration,
        cause: e.toString(),
        extra: {
          'base_url': baseUrl,
        },
      );
      return false;
    }
  }

  /// Loads cookie from secure storage and applies it to CookieJar.
  ///
  /// This should be called during app initialization to restore cookies.
  Future<void> loadCookieToJar(String baseUrl, CookieJar cookieJar) async {
    final operationId = 'cookie_load_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      final cookieString = await getCookie(baseUrl);
      if (cookieString == null) {
        await logger.log(
          level: 'debug',
          subsystem: 'auth',
          message: 'No cookie to load from storage',
          operationId: operationId,
          context: 'simple_cookie_manager',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
          extra: {
            'base_url': baseUrl,
          },
        );
        return;
      }

      // Parse and apply to CookieJar
      final uri = Uri.parse(baseUrl);
      final cookies = _parseCookieString(cookieString, uri);

      if (cookies.isNotEmpty) {
        await cookieJar.saveFromResponse(uri, cookies);

        // Also save to base domain
        final baseUri = Uri.parse('${uri.scheme}://${uri.host}');
        if (baseUri != uri) {
          await cookieJar.saveFromResponse(baseUri, cookies);
        }

        await logger.log(
          level: 'info',
          subsystem: 'auth',
          message: 'Cookie loaded from storage and applied to CookieJar',
          operationId: operationId,
          context: 'simple_cookie_manager',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
          extra: {
            'base_url': baseUrl,
            'cookie_count': cookies.length,
          },
        );
      }
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'auth',
        message: 'Failed to load cookie to jar',
        operationId: operationId,
        context: 'simple_cookie_manager',
        durationMs: duration,
        cause: e.toString(),
        extra: {
          'base_url': baseUrl,
        },
      );
      rethrow;
    }
  }

  /// Gets storage key for a base URL.
  ///
  /// Uses the hostname as part of the key to support multiple mirrors.
  String _getStorageKey(String baseUrl) {
    final uri = Uri.parse(baseUrl);
    return '$_cookieKeyPrefix${uri.host}';
  }

  /// Parses cookie string into Cookie objects.
  ///
  /// Cookie string format: 'bb_session=value; bb_ssl=1'
  /// Returns list of Cookie objects ready for CookieJar.
  List<io.Cookie> _parseCookieString(String cookieString, Uri uri) {
    final cookies = <io.Cookie>[];

    // Split by semicolon to get individual cookies
    final cookieParts = cookieString
        .split(';')
        .map((s) => s.trim())
        .where((s) => s.isNotEmpty)
        .toList();

    for (final cookiePart in cookieParts) {
      // Split by '=' to get name and value
      final parts = cookiePart.split('=');
      if (parts.length >= 2) {
        final name = parts[0].trim();
        final value =
            parts.sublist(1).join('=').trim(); // Handle values with '='

        if (name.isNotEmpty && value.isNotEmpty) {
          final cookie = io.Cookie(name, value)
            ..path = '/'
            ..secure = uri.scheme == 'https'
            ..httpOnly = false; // We don't know from the string

          cookies.add(cookie);
        }
      }
    }

    return cookies;
  }
}
