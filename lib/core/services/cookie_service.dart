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

import 'package:flutter/services.dart';
import 'package:jabook/core/logging/structured_logger.dart';

/// Service for managing cookies via Android CookieManager.
///
/// This service provides a bridge between Flutter and Android's native
/// CookieManager, allowing Flutter to read and manage cookies that are
/// set by WebView. This is the single source of truth for cookies on Android.
class CookieService {
  /// Private constructor to prevent instantiation.
  const CookieService._();

  static const MethodChannel _channel = MethodChannel('cookie_channel');

  /// Gets cookies for a specific URL from Android CookieManager.
  ///
  /// Returns the Cookie header string that can be used directly in HTTP requests.
  /// Returns null if no cookies are available or if an error occurs.
  ///
  /// The [url] parameter is the URL to get cookies for (e.g., "https://rutracker.org").
  static Future<String?> getCookiesForUrl(String url) async {
    final operationId = 'cookie_get_${DateTime.now().millisecondsSinceEpoch}';
    final startTime = DateTime.now();

    try {
      await StructuredLogger().log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'Getting cookies from CookieManager',
        operationId: operationId,
        context: 'cookie_service',
        extra: {'url': url},
      );

      final result = await _channel.invokeMethod<String>('getCookiesForUrl', {
        'url': url,
      });

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await StructuredLogger().log(
        level: result != null && result.isNotEmpty ? 'info' : 'debug',
        subsystem: 'cookies',
        message: result != null && result.isNotEmpty
            ? 'Got cookies from CookieManager'
            : 'No cookies found in CookieManager',
        operationId: operationId,
        context: 'cookie_service',
        durationMs: duration,
        extra: {
          'url': url,
          'cookie_header_length': result?.length ?? 0,
          'has_cookies': result != null && result.isNotEmpty,
        },
      );

      return result;
    } on PlatformException catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'cookies',
        message: 'Failed to get cookies from CookieManager',
        operationId: operationId,
        context: 'cookie_service',
        durationMs: duration,
        cause: e.toString(),
        extra: {
          'url': url,
          'error_code': e.code,
          'error_message': e.message,
        },
      );
      return null;
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'cookies',
        message: 'Exception getting cookies from CookieManager',
        operationId: operationId,
        context: 'cookie_service',
        durationMs: duration,
        cause: e.toString(),
        extra: {'url': url},
      );
      return null;
    }
  }

  /// Sets a cookie for a specific URL in Android CookieManager.
  ///
  /// The [url] parameter is the URL to set the cookie for.
  /// The [cookie] parameter is the cookie string in the format "name=value; path=/; domain=example.com".
  ///
  /// Returns true if the cookie was set successfully, false otherwise.
  static Future<bool> setCookie(String url, String cookie) async {
    final operationId = 'cookie_set_${DateTime.now().millisecondsSinceEpoch}';
    final startTime = DateTime.now();

    try {
      await StructuredLogger().log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'Setting cookie in CookieManager',
        operationId: operationId,
        context: 'cookie_service',
        extra: {
          'url': url,
          'cookie_preview': cookie.length > 100 ? '${cookie.substring(0, 100)}...' : cookie,
        },
      );

      final result = await _channel.invokeMethod<bool>('setCookie', {
        'url': url,
        'cookie': cookie,
      });

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await StructuredLogger().log(
        level: (result ?? false) ? 'info' : 'warning',
        subsystem: 'cookies',
        message: (result ?? false)
            ? 'Cookie set in CookieManager'
            : 'Failed to set cookie in CookieManager',
        operationId: operationId,
        context: 'cookie_service',
        durationMs: duration,
        extra: {
          'url': url,
          'success': result ?? false,
        },
      );

      return result ?? false;
    } on PlatformException catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'cookies',
        message: 'Failed to set cookie in CookieManager',
        operationId: operationId,
        context: 'cookie_service',
        durationMs: duration,
        cause: e.toString(),
        extra: {
          'url': url,
          'error_code': e.code,
          'error_message': e.message,
        },
      );
      return false;
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'cookies',
        message: 'Exception setting cookie in CookieManager',
        operationId: operationId,
        context: 'cookie_service',
        durationMs: duration,
        cause: e.toString(),
        extra: {'url': url},
      );
      return false;
    }
  }

  /// Clears all cookies from Android CookieManager.
  ///
  /// Returns true if cookies were cleared successfully, false otherwise.
  static Future<bool> clearAllCookies() async {
    final operationId = 'cookie_clear_${DateTime.now().millisecondsSinceEpoch}';
    final startTime = DateTime.now();

    try {
      await StructuredLogger().log(
        level: 'info',
        subsystem: 'cookies',
        message: 'Clearing all cookies from CookieManager',
        operationId: operationId,
        context: 'cookie_service',
      );

      final result = await _channel.invokeMethod<bool>('clearAllCookies');

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await StructuredLogger().log(
        level: (result ?? false) ? 'info' : 'warning',
        subsystem: 'cookies',
        message: (result ?? false)
            ? 'All cookies cleared from CookieManager'
            : 'Failed to clear cookies from CookieManager',
        operationId: operationId,
        context: 'cookie_service',
        durationMs: duration,
        extra: {'success': result ?? false},
      );

      return result ?? false;
    } on PlatformException catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'cookies',
        message: 'Failed to clear cookies from CookieManager',
        operationId: operationId,
        context: 'cookie_service',
        durationMs: duration,
        cause: e.toString(),
        extra: {
          'error_code': e.code,
          'error_message': e.message,
        },
      );
      return false;
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'cookies',
        message: 'Exception clearing cookies from CookieManager',
        operationId: operationId,
        context: 'cookie_service',
        durationMs: duration,
        cause: e.toString(),
      );
      return false;
    }
  }
}

