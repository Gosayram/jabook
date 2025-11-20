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

import 'dart:async';
import 'dart:io' as io;

import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:dio_brotli_transformer/dio_brotli_transformer.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart' as dio_cookie;
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:jabook/core/auth/simple_cookie_manager.dart';
import 'package:jabook/core/endpoints/endpoint_manager.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/core/net/dio_cookie_manager.dart';
import 'package:jabook/core/net/dio_interceptors.dart';
import 'package:jabook/core/net/user_agent_manager.dart';
import 'package:jabook/core/services/cookie_service.dart';
import 'package:jabook/core/session/session_interceptor.dart';
import 'package:jabook/core/session/session_manager.dart';
import 'package:jabook/data/db/app_database.dart';

/// HTTP client for making requests to RuTracker APIs.
///
/// This class provides a singleton Dio instance configured for
/// making HTTP requests to RuTracker with proper timeouts,
/// user agent, and cookie management.
class DioClient {
  /// Private constructor to prevent direct instantiation.
  const DioClient._();

  static Dio? _instance;
  static CookieJar? _cookieJar;
  static SessionManager? _sessionManager;
  static DateTime? _appStartTime;
  static bool _firstRequestTracked = false;
  static SimpleCookieManager? _simpleCookieManager;

  /// Gets the singleton Dio instance configured for RuTracker requests.
  ///
  /// This instance is configured with appropriate timeouts, user agent,
  /// and cookie management for RuTracker API calls.
  ///
  /// Returns a configured Dio instance ready for use.
  static Future<Dio> get instance async {
    // Return cached instance if already initialized
    if (_instance != null) {
      return _instance!;
    }

    final dio = Dio();
    final userAgentManager = UserAgentManager();

    // Apply User-Agent from manager
    await userAgentManager.applyUserAgentToDio(dio);

    // Resolve active RuTracker endpoint dynamically
    final db = AppDatabase().database;
    final endpointManager = EndpointManager(db);
    final activeBase = await endpointManager.getActiveEndpoint();

    // Get User-Agent from WebView to ensure consistency (important for Cloudflare)
    final userAgent = await userAgentManager.getUserAgent();

    // Configure Dio options and Brotli transformer
    dio
      ..options = BaseOptions(
        baseUrl: activeBase,
        connectTimeout: const Duration(seconds: 30),
        receiveTimeout: const Duration(seconds: 30),
        sendTimeout: const Duration(seconds: 30),
        headers: {
          'User-Agent': userAgent, // Same as WebView - critical for Cloudflare
          'Accept':
              'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
          'Accept-Language': 'ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7',
          'Accept-Encoding': 'gzip, deflate, br',
          'Connection': 'keep-alive',
          'Referer': '$activeBase/',
          // Don't set Cookie header manually - let CookieJar handle it
          // This ensures cookies are sent automatically with requests
        },
      )
      // Configure Brotli transformer for automatic decompression
      // This ensures Brotli-compressed responses (content-encoding: br) are automatically decompressed
      // before being passed to interceptors and response handlers
      ..transformer = DioBrotliTransformer();
    await StructuredLogger().log(
      level: 'info',
      subsystem: 'network',
      message: 'Brotli transformer configured for automatic decompression',
      context: 'dio_init',
    );

    // Interceptors are added in a specific order (executed in reverse order):
    // 1. LoggingInterceptor (first - logs all requests/responses)
    // 2. AuthAndRetryInterceptor (second - handles auth errors and retries)
    // 3. CookieManager (third - manages cookies)
    // 4. SessionInterceptor (fourth - validates session)

    // Add structured logging interceptor
    dio.interceptors.add(DioInterceptors.createLoggingInterceptor(
      appStartTime: _appStartTime,
      firstRequestTracked: _firstRequestTracked,
    ));

    // Add authentication redirect handler and resilient retry policy for idempotent requests
    dio.interceptors.add(DioInterceptors.createAuthAndRetryInterceptor(dio));

    // Initialize cookie jar
    _cookieJar ??= CookieJar();
    dio.interceptors.add(dio_cookie.CookieManager(_cookieJar!));

    // Initialize SimpleCookieManager and load saved cookies
    _simpleCookieManager ??= SimpleCookieManager();
    try {
      await _simpleCookieManager!.loadCookieToJar(activeBase, _cookieJar!);
      await StructuredLogger().log(
        level: 'info',
        subsystem: 'cookies',
        message: 'Cookies loaded from SimpleCookieManager',
        context: 'cookie_init',
      );
    } on Exception catch (e) {
      await StructuredLogger().log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'No saved cookies found or failed to load',
        context: 'cookie_init',
        cause: e.toString(),
      );
    }

    // Add SessionInterceptor for automatic session validation and refresh
    // Use singleton SessionManager instance
    _sessionManager ??= SessionManager();
    dio.interceptors.add(SessionInterceptor(_sessionManager!));

    // Cache and return the instance
    _instance = dio;
    return _instance!;
  }

  /// Sets the application start time for performance metrics tracking.
  ///
  /// This should be called early in the application lifecycle (e.g., in main())
  /// to enable tracking of time to first network request.
  static set appStartTime(DateTime startTime) {
    _appStartTime = startTime;
  }

  /// Resets the singleton instance (useful for testing).
  ///
  /// This method clears the cached Dio instance and session manager,
  /// allowing a fresh instance to be created on the next call to [instance].
  static void reset() {
    _instance = null;
    _sessionManager = null;
    _firstRequestTracked = false;
    _appStartTime = null;
    // Keep _cookieJar to preserve cookies across resets
  }

  /// Gets the user agent string for HTTP requests.
  ///
  /// This method returns a user agent string that mimics a mobile browser
  /// to ensure compatibility with RuTracker's anti-bot measures.
  ///
  /// Returns a user agent string for HTTP requests.
  static Future<String> getUserAgent() async {
    final userAgentManager = UserAgentManager();
    return userAgentManager.getUserAgent();
  }

  /// Synchronizes cookies from WebView to the Dio client.
  ///
  /// DEPRECATED: This method is no longer needed as we use direct HTTP authentication.
  /// Kept for backward compatibility but does nothing.
  ///
  /// @deprecated Use SimpleCookieManager instead
  static Future<void> syncCookiesFromWebView() async {
    // No-op: WebView synchronization is no longer needed
    await StructuredLogger().log(
      level: 'debug',
      subsystem: 'cookies',
      message: 'syncCookiesFromWebView called but is deprecated (no-op)',
      context: 'cookie_sync_deprecated',
    );
  }

  /// Saves cookies directly to Dio CookieJar.
  ///
  /// This is a helper method for direct cookie synchronization from WebView.
  /// It bypasses SharedPreferences and saves cookies directly to the cookie jar.
  static Future<void> saveCookiesDirectly(
      Uri uri, List<io.Cookie> cookies) async {
    await DioCookieManager.saveCookiesDirectly(uri, cookies, _cookieJar);
  }

  /// Gets the CookieJar instance for debugging purposes.
  ///
  /// This method ensures the cookie jar is initialized and returns it.
  static Future<CookieJar?> getCookieJar() async {
    await instance; // Ensure Dio is initialized
    _cookieJar ??= CookieJar();
    return _cookieJar;
  }

  /// Synchronizes cookies from Dio cookie jar to WebView storage.
  ///
  /// DEPRECATED: This method is no longer needed as we use direct HTTP authentication.
  /// Kept for backward compatibility but does nothing.
  ///
  /// @deprecated Use SimpleCookieManager instead
  static Future<void> syncCookiesToWebView() async {
    // No-op: WebView synchronization is no longer needed
    await StructuredLogger().log(
      level: 'debug',
      subsystem: 'cookies',
      message: 'syncCookiesToWebView called but is deprecated (no-op)',
      context: 'cookie_sync_deprecated',
    );
  }

  /// Syncs cookies to a new endpoint when switching mirrors.
  ///
  /// This method ensures that cookies from the old endpoint are available
  /// for the new endpoint if they are compatible (same domain family).
  ///
  /// The [oldEndpoint] parameter is the URL of the endpoint being switched from.
  /// The [newEndpoint] parameter is the URL of the endpoint being switched to.
  static Future<void> syncCookiesOnEndpointSwitch(
    String oldEndpoint,
    String newEndpoint,
  ) async {
    await DioCookieManager.syncCookiesOnEndpointSwitch(
      oldEndpoint,
      newEndpoint,
      _cookieJar,
    );
  }

  /// Clears all stored cookies from the cookie jar.
  ///
  /// This method removes all cookies, effectively logging out the user
  /// from any authenticated sessions.
  static Future<void> clearCookies() async {
    await DioCookieManager.clearCookies(_cookieJar);
    // Also clear from SecureStorage
    await clearCookiesFromSecureStorage();
    // Clear from CookieManager (Kotlin) - Android only
    if (!kIsWeb) {
      await CookieService.clearAllCookies();
    }
  }

  /// Checks if valid cookies are available for authentication.
  ///
  /// Returns true if cookies exist and appear to be valid, false otherwise.
  static Future<bool> hasValidCookies() async =>
      DioCookieManager.hasValidCookies();

  /// Validates cookies by making a test request to RuTracker.
  ///
  /// Returns true if cookies are valid and authentication is active, false otherwise.
  static Future<bool> validateCookies() async {
    final dio = await instance;
    return DioCookieManager.validateCookies(
      dio,
      _cookieJar,
    );
  }

  /// Synchronizes cookies from CookieService (Android CookieManager) to Dio CookieJar.
  ///
  /// This is the new approach: get cookies directly from Android CookieManager
  /// (which WebView uses) and sync them to Dio CookieJar.
  ///
  /// The [cookieHeader] parameter is the Cookie header string from CookieManager.
  /// The [url] parameter is the base URL for the cookies (e.g., "https://rutracker.org").
  static Future<void> syncCookiesFromCookieService(
    String cookieHeader,
    String url,
  ) async {
    final operationId =
        'cookie_sync_service_${DateTime.now().millisecondsSinceEpoch}';
    final startTime = DateTime.now();
    final logger = StructuredLogger();

    try {
      await logger.log(
        level: 'info',
        subsystem: 'cookies',
        message: 'Syncing cookies from CookieService to Dio',
        operationId: operationId,
        context: 'cookie_sync_service',
        extra: {
          'url': url,
          'cookie_header_length': cookieHeader.length,
        },
      );

      if (cookieHeader.isEmpty) {
        await logger.log(
          level: 'warning',
          subsystem: 'cookies',
          message: 'Empty cookie header from CookieService',
          operationId: operationId,
          context: 'cookie_sync_service',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
        );
        return;
      }

      final uri = Uri.parse(url);
      final jar = await getCookieJar();
      if (jar == null) {
        await logger.log(
          level: 'error',
          subsystem: 'cookies',
          message: 'CookieJar is not initialized',
          operationId: operationId,
          context: 'cookie_sync_service',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
        );
        return;
      }

      // Parse cookie header string into Cookie objects
      // Format from CookieManager: "name1=value1; name2=value2; name3=value3"
      // Note: CookieManager.getCookie() returns semicolon-separated list
      // CRITICAL: Split by "; " (semicolon + space) to avoid splitting values that contain semicolons
      final cookies = <io.Cookie>[];
      final validName = RegExp(r"^[!#\$%&'*+.^_`|~0-9A-Za-z-]+$");

      // Split by "; " pattern, but handle cases where there's no space after semicolon
      // First try splitting by "; " (most common format)
      final cookieParts = cookieHeader.split(RegExp(r';\s*'));
      var parsedCount = 0;
      var skippedCount = 0;

      for (final part in cookieParts) {
        final trimmed = part.trim();
        if (trimmed.isEmpty) {
          continue;
        }

        // Skip attributes like "path=/", "domain=example.com", "secure", "HttpOnly"
        if (!trimmed.contains('=') ||
            trimmed.startsWith('path=') ||
            trimmed.startsWith('domain=') ||
            trimmed.startsWith('expires=') ||
            trimmed.startsWith('max-age=') ||
            trimmed.toLowerCase() == 'secure' ||
            trimmed.toLowerCase() == 'httponly') {
          continue;
        }

        final equalIndex = trimmed.indexOf('=');
        if (equalIndex <= 0) {
          skippedCount++;
          continue;
        }

        var name = trimmed.substring(0, equalIndex).trim();
        var value = trimmed.substring(equalIndex + 1).trim();

        // Strip surrounding quotes if present
        if (name.length >= 2 && name.startsWith('"') && name.endsWith('"')) {
          name = name.substring(1, name.length - 1);
        }
        if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) {
          value = value.substring(1, value.length - 1);
        }

        // Validate cookie name
        if (name.isEmpty || !validName.hasMatch(name)) {
          skippedCount++;
          await logger.log(
            level: 'debug',
            subsystem: 'cookies',
            message: 'Skipped invalid cookie name',
            operationId: operationId,
            context: 'cookie_sync_service',
            extra: {
              'name': name.isEmpty ? '<empty>' : name,
              'value_length': value.length,
            },
          );
          continue;
        }

        // Create cookie with proper domain
        // CRITICAL: CookieJar behavior:
        // - If cookie has domain, it's saved in domainCookies (index 0) for subdomain sharing
        // - If cookie has NO domain, it's saved in hostCookies (index 1) for exact host match
        // For rutracker cookies from CookieManager, we don't set domain to ensure
        // they are saved in hostCookies for exact host matching
        final cookie = io.Cookie(name, value)
          // Don't set domain - CookieJar will use uri.host automatically
          // This ensures cookie is saved in hostCookies for exact host matching
          ..path = '/' // Always use '/' for rutracker cookies
          ..secure = uri.scheme == 'https'
          ..httpOnly = false; // We don't know if it's HttpOnly from the header

        cookies.add(cookie);
        parsedCount++;
      }

      await logger.log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'Parsed cookies from cookie header',
        operationId: operationId,
        context: 'cookie_sync_service',
        extra: {
          'total_parts': cookieParts.length,
          'parsed_count': parsedCount,
          'skipped_count': skippedCount,
          'cookie_names': cookies.map((c) => c.name).toList(),
        },
      );

      if (cookies.isEmpty) {
        await logger.log(
          level: 'warning',
          subsystem: 'cookies',
          message: 'No valid cookies parsed from cookie header',
          operationId: operationId,
          context: 'cookie_sync_service',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
          extra: {
            'cookie_header_preview': cookieHeader.length > 200
                ? '${cookieHeader.substring(0, 200)}...'
                : cookieHeader,
          },
        );
        return;
      }

      // CRITICAL: Save cookies to CookieJar for the exact URI first
      // Use the exact URI to ensure cookies are saved with correct domain
      await jar.saveFromResponse(uri, cookies);

      // Also save to base URI (without path) to ensure cookies are available for all paths
      final baseUri = Uri.parse('${uri.scheme}://${uri.host}');
      if (baseUri != uri) {
        await jar.saveFromResponse(baseUri, cookies);
      }

      // CRITICAL: Save cookies to CookieService (Android CookieManager) for all RuTracker domains
      // This ensures cookies are available for WebView and other components
      final rutrackerDomains = EndpointManager.getRutrackerDomains();
      var savedToCookieServiceCount = 0;
      for (final domain in rutrackerDomains) {
        try {
          final domainUrl = 'https://$domain';
          for (final cookie in cookies) {
            // Format for Android CookieManager: "name=value; path=/; domain=example.com"
            final cookieString =
                '${cookie.name}=${cookie.value}; path=${cookie.path ?? '/'}; domain=$domain';
            final success =
                await CookieService.setCookie(domainUrl, cookieString);
            if (success) {
              savedToCookieServiceCount++;
            }
          }
        } on Exception catch (e) {
          await logger.log(
            level: 'debug',
            subsystem: 'cookies',
            message: 'Failed to save cookie to CookieService for domain',
            operationId: operationId,
            context: 'cookie_sync_service',
            cause: e.toString(),
            extra: {'domain': domain},
          );
        }
      }

      // Also save to all RuTracker domains in CookieJar for compatibility
      // This ensures cookies work when switching between mirrors
      for (final domain in rutrackerDomains) {
        if (domain != uri.host) {
          try {
            final domainUri = Uri.parse('${uri.scheme}://$domain');
            // Create cookies WITHOUT domain - CookieJar will use domainUri.host automatically
            // This ensures cookies are saved in hostCookies for each domain separately
            final domainCookies = cookies.map((cookie) {
              final domainCookie = io.Cookie(cookie.name, cookie.value)
                // Don't set domain - CookieJar will use domainUri.host automatically
                // This saves cookie in hostCookies for exact host matching per domain
                ..path =
                    cookie.path ?? '/' // Always use '/' for rutracker cookies
                ..secure = cookie.secure
                ..httpOnly = cookie.httpOnly;
              return domainCookie;
            }).toList();
            await jar.saveFromResponse(domainUri, domainCookies);
          } on Exception catch (e) {
            await logger.log(
              level: 'debug',
              subsystem: 'cookies',
              message: 'Failed to save cookies for domain',
              operationId: operationId,
              context: 'cookie_sync_service',
              cause: e.toString(),
              extra: {'domain': domain},
            );
          }
        }
      }

      // Log successful save to CookieService
      if (savedToCookieServiceCount > 0) {
        // CRITICAL: Flush cookies to ensure they are persisted to disk
        await CookieService.flushCookies();

        await logger.log(
          level: 'info',
          subsystem: 'cookies',
          message: 'Cookies saved to CookieService and flushed to disk',
          operationId: operationId,
          context: 'cookie_sync_service',
          extra: {
            'saved_count': savedToCookieServiceCount,
            'total_cookies': cookies.length,
            'domains': rutrackerDomains,
          },
        );
      }

      // CRITICAL: Verify cookies were saved by loading them back
      await Future.delayed(const Duration(milliseconds: 100));
      final savedCookies = await jar.loadForRequest(uri);
      final baseSavedCookies = await jar.loadForRequest(baseUri);

      await logger.log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'Verified cookies in CookieJar after save',
        operationId: operationId,
        context: 'cookie_sync_service',
        extra: {
          'uri_cookie_count': savedCookies.length,
          'base_cookie_count': baseSavedCookies.length,
          'expected_count': cookies.length,
          'saved_cookie_names': savedCookies.map((c) => c.name).toList(),
          'base_cookie_names': baseSavedCookies.map((c) => c.name).toList(),
        },
      );

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'info',
        subsystem: 'cookies',
        message: 'Cookies synced from CookieService to Dio',
        operationId: operationId,
        context: 'cookie_sync_service',
        durationMs: duration,
        extra: {
          'url': url,
          'cookie_count': cookies.length,
          'cookie_names': cookies.map((c) => c.name).toList(),
        },
      );
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'cookies',
        message: 'Failed to sync cookies from CookieService',
        operationId: operationId,
        context: 'cookie_sync_service',
        durationMs: duration,
        cause: e.toString(),
        extra: {'url': url},
      );
    }
  }

  /// Saves cookies to SecureStorage for auto-login on app restart.
  ///
  /// The [cookieHeader] parameter is the Cookie header string from CookieManager.
  /// The [url] parameter is the base URL for the cookies.
  static Future<void> saveCookiesToSecureStorage(
    String cookieHeader,
    String url,
  ) async {
    final operationId =
        'cookie_save_secure_${DateTime.now().millisecondsSinceEpoch}';
    final startTime = DateTime.now();
    final logger = StructuredLogger();
    const storage = FlutterSecureStorage();
    const cookieKey = 'rutracker_cookie_header_v2';

    try {
      await logger.log(
        level: 'info',
        subsystem: 'cookies',
        message: 'Saving cookies to SecureStorage',
        operationId: operationId,
        context: 'cookie_save_secure',
        extra: {
          'url': url,
          'cookie_header_length': cookieHeader.length,
        },
      );

      // Save cookie header and URL
      await storage.write(key: cookieKey, value: cookieHeader);
      await storage.write(key: '${cookieKey}_url', value: url);

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'info',
        subsystem: 'cookies',
        message: 'Cookies saved to SecureStorage',
        operationId: operationId,
        context: 'cookie_save_secure',
        durationMs: duration,
        extra: {
          'url': url,
          'cookie_header_length': cookieHeader.length,
        },
      );
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'cookies',
        message: 'Failed to save cookies to SecureStorage',
        operationId: operationId,
        context: 'cookie_save_secure',
        durationMs: duration,
        cause: e.toString(),
        extra: {'url': url},
      );
    }
  }

  /// Restores cookies from SecureStorage and syncs them to Dio and CookieManager.
  ///
  /// This should be called on app startup to restore authentication state.
  /// Returns true if cookies were restored successfully, false otherwise.
  static Future<bool> restoreCookiesFromSecureStorage() async {
    final operationId =
        'cookie_restore_secure_${DateTime.now().millisecondsSinceEpoch}';
    final startTime = DateTime.now();
    final logger = StructuredLogger();
    const storage = FlutterSecureStorage();
    const cookieKey = 'rutracker_cookie_header_v2';

    try {
      await logger.log(
        level: 'info',
        subsystem: 'cookies',
        message: 'Restoring cookies from SecureStorage',
        operationId: operationId,
        context: 'cookie_restore_secure',
      );

      // Read cookie header and URL from SecureStorage
      final cookieHeader = await storage.read(key: cookieKey);
      final url = await storage.read(key: '${cookieKey}_url');

      if (cookieHeader == null || cookieHeader.isEmpty || url == null) {
        await logger.log(
          level: 'debug',
          subsystem: 'cookies',
          message: 'No cookies found in SecureStorage',
          operationId: operationId,
          context: 'cookie_restore_secure',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
        );
        return false;
      }

      await logger.log(
        level: 'info',
        subsystem: 'cookies',
        message: 'Found cookies in SecureStorage, restoring',
        operationId: operationId,
        context: 'cookie_restore_secure',
        extra: {
          'url': url,
          'cookie_header_length': cookieHeader.length,
        },
      );

      // Sync cookies to CookieManager (Kotlin) first - Android only
      if (!kIsWeb) {
        await CookieService.setCookie(url, cookieHeader);
      }

      // Sync cookies to Dio CookieJar (works on all platforms)
      await syncCookiesFromCookieService(cookieHeader, url);

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'info',
        subsystem: 'cookies',
        message: 'Cookies restored from SecureStorage',
        operationId: operationId,
        context: 'cookie_restore_secure',
        durationMs: duration,
        extra: {
          'url': url,
          'cookie_header_length': cookieHeader.length,
        },
      );

      return true;
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'cookies',
        message: 'Failed to restore cookies from SecureStorage',
        operationId: operationId,
        context: 'cookie_restore_secure',
        durationMs: duration,
        cause: e.toString(),
      );
      return false;
    }
  }

  /// Clears cookies from SecureStorage.
  ///
  /// This should be called on logout to remove stored authentication cookies.
  static Future<void> clearCookiesFromSecureStorage() async {
    final operationId =
        'cookie_clear_secure_${DateTime.now().millisecondsSinceEpoch}';
    final startTime = DateTime.now();
    final logger = StructuredLogger();
    const storage = FlutterSecureStorage();
    const cookieKey = 'rutracker_cookie_header_v2';

    try {
      await logger.log(
        level: 'info',
        subsystem: 'cookies',
        message: 'Clearing cookies from SecureStorage',
        operationId: operationId,
        context: 'cookie_clear_secure',
      );

      await storage.delete(key: cookieKey);
      await storage.delete(key: '${cookieKey}_url');

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'info',
        subsystem: 'cookies',
        message: 'Cookies cleared from SecureStorage',
        operationId: operationId,
        context: 'cookie_clear_secure',
        durationMs: duration,
      );
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'cookies',
        message: 'Failed to clear cookies from SecureStorage',
        operationId: operationId,
        context: 'cookie_clear_secure',
        durationMs: duration,
        cause: e.toString(),
      );
    }
  }
}
