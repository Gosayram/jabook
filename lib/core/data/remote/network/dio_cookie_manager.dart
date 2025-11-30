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
import 'dart:convert';
import 'dart:io' as io;

import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:jabook/core/auth/cookie_database_service.dart';
import 'package:jabook/core/auth/simple_cookie_manager.dart';
import 'package:jabook/core/data/remote/network/dio_client.dart';
import 'package:jabook/core/infrastructure/endpoints/endpoint_manager.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:jabook/core/services/cookie_service.dart';
import 'package:jabook/data/db/app_database.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Manages cookie synchronization between WebView, Dio CookieJar, and SessionManager.
class DioCookieManager {
  /// Private constructor to prevent instantiation.
  const DioCookieManager._();

  /// Synchronizes cookies from WebView to the Dio client.
  ///
  /// DEPRECATED: This method is no longer needed as we use direct HTTP authentication.
  /// Kept as no-op for backward compatibility.
  ///
  /// @deprecated Use SimpleCookieManager instead
  static Future<void> syncCookiesFromWebView([CookieJar? cookieJar]) async {
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
  ///
  /// The [cookieJar] parameter is the CookieJar instance to save cookies to.
  /// The [uri] parameter is the URI to save cookies for.
  /// The [cookies] parameter is the list of cookies to save.
  static Future<void> saveCookiesDirectly(
    Uri uri,
    List<io.Cookie> cookies, [
    CookieJar? cookieJar,
  ]) async {
    final logger = StructuredLogger();
    final operationId =
        'save_cookies_direct_${DateTime.now().millisecondsSinceEpoch}';

    await logger.log(
      level: 'debug',
      subsystem: 'cookies',
      message: 'Saving cookies directly to Dio CookieJar',
      operationId: operationId,
      context: 'cookie_save_direct',
      extra: {
        'uri': uri.toString(),
        'cookie_count': cookies.length,
        'cookies': cookies
            .map((c) => {
                  'name': c.name,
                  'domain': c.domain ?? '<null>',
                  'path': c.path ?? '/',
                  'has_value': c.value.isNotEmpty,
                  'value_length': c.value.length,
                  'secure': c.secure,
                  'http_only': c.httpOnly,
                })
            .toList(),
      },
    );

    final jar = cookieJar ?? CookieJar();

    // Verify cookies before saving
    await logger.log(
      level: 'debug',
      subsystem: 'cookies',
      message: 'About to save cookies to Dio CookieJar',
      operationId: operationId,
      context: 'cookie_save_direct',
      extra: {
        'uri': uri.toString(),
        'cookie_count': cookies.length,
        'cookie_names': cookies.map((c) => c.name).toList(),
        'cookie_domains': cookies.map((c) => c.domain ?? '<null>').toList(),
      },
    );

    // IMPORTANT: CookieJar is primarily for cookies from HTTP responses
    // For WebView cookies, SessionManager is the primary storage
    // We try to save to CookieJar for backward compatibility, but it's not critical

    // CRITICAL: CookieJar behavior:
    // - If cookie has domain, it's saved in domainCookies (index 0) and can be used for subdomains
    // - If cookie has NO domain, it's saved in hostCookies (index 1) and used only for exact host match
    // For rutracker cookies, we want them to work for the exact host, so we should NOT set domain
    // unless the original cookie had a domain attribute
    final uriHost = uri.host.toLowerCase();
    final cookiesToSave = cookies.map((cookie) {
      final cookieToSave = io.Cookie(cookie.name, cookie.value)
        ..path = cookie.path ?? '/' // Always use '/' for rutracker cookies
        ..secure = cookie.secure
        ..httpOnly = cookie.httpOnly;

      // Only set domain if the original cookie had one
      // This ensures cookies are saved in the correct CookieJar storage
      final cookieDomain = cookie.domain?.toLowerCase().trim() ?? '';
      if (cookieDomain.isNotEmpty) {
        // Normalize domain: remove leading dot if present (CookieJar does this automatically, but we do it for consistency)
        final normalizedDomain = cookieDomain.startsWith('.')
            ? cookieDomain.substring(1)
            : cookieDomain;

        // Only set domain if it matches the URI host or is a valid domain
        // This ensures cookies are saved in domainCookies for subdomain sharing
        if (normalizedDomain == uriHost ||
            uriHost.endsWith('.$normalizedDomain') ||
            normalizedDomain.contains('rutracker')) {
          cookieToSave.domain = normalizedDomain;
        } else {
          // Domain doesn't match, don't set it - will be saved in hostCookies
          // CookieJar will use uri.host automatically
        }
      }
      // If cookie had no domain, don't set it - CookieJar will use uri.host
      // This saves cookie in hostCookies for exact host matching

      return cookieToSave;
    }).toList();

    // Try simple batch save first (most reliable method)
    try {
      await logger.log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'Attempting batch save to CookieJar',
        operationId: operationId,
        context: 'cookie_save_direct',
        extra: {
          'uri': uri.toString(),
          'uri_host': uri.host,
          'cookie_count': cookiesToSave.length,
          'cookie_names': cookiesToSave.map((c) => c.name).toList(),
        },
      );

      // Use the exact URI provided - CookieJar will handle domain matching
      // Cookies without domain will use URI host automatically
      await jar.saveFromResponse(uri, cookiesToSave);

      await logger.log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'Batch save to CookieJar completed',
        operationId: operationId,
        context: 'cookie_save_direct',
      );
    } on Exception catch (batchError) {
      await logger.log(
        level: 'warning',
        subsystem: 'cookies',
        message: 'Batch save to CookieJar failed, trying individual saves',
        operationId: operationId,
        context: 'cookie_save_direct',
        cause: batchError.toString(),
      );

      // Try individual saves as fallback (only if batch failed)
      var savedCount = 0;
      for (final cookie in cookiesToSave) {
        try {
          await jar.saveFromResponse(uri, [cookie]);
          savedCount++;
        } on Exception catch (e) {
          await logger.log(
            level: 'debug',
            subsystem: 'cookies',
            message: 'Failed to save individual cookie',
            operationId: operationId,
            context: 'cookie_save_direct',
            cause: e.toString(),
            extra: {'cookie_name': cookie.name},
          );
        }
      }

      if (savedCount > 0) {
        await logger.log(
          level: 'info',
          subsystem: 'cookies',
          message: 'Some cookies saved individually to CookieJar',
          operationId: operationId,
          context: 'cookie_save_direct',
          extra: {
            'saved_count': savedCount,
            'total_count': cookiesToSave.length,
          },
        );
      } else {
        await logger.log(
          level: 'error',
          subsystem: 'cookies',
          message: 'CRITICAL: Failed to save any cookies to CookieJar',
          operationId: operationId,
          context: 'cookie_save_direct',
          extra: {
            'total_count': cookiesToSave.length,
            'note':
                'Cookies are stored in SessionManager, which is the primary storage for WebView cookies',
          },
        );
      }
    }

    // CRITICAL: Verify cookies were saved by loading them back
    // Use a delay to ensure cookies are persisted to storage
    await Future.delayed(const Duration(milliseconds: 200));

    // Try loading from multiple URI variations to ensure we find saved cookies
    // Use exact URI first, then base URI
    final savedCookies = await jar.loadForRequest(uri);
    final baseUri = Uri.parse('https://${uri.host}');
    final baseCookies = await jar.loadForRequest(baseUri);

    // Also try with path-only URI
    final pathUri = Uri.parse('https://${uri.host}/');
    final pathCookies = await jar.loadForRequest(pathUri);

    // Combine cookies from all URI variations (avoid duplicates)
    final allSavedCookies = <io.Cookie>[];
    for (final cookie in savedCookies) {
      if (!allSavedCookies.any((c) => c.name == cookie.name)) {
        allSavedCookies.add(cookie);
      }
    }
    for (final cookie in baseCookies) {
      if (!allSavedCookies.any((c) => c.name == cookie.name)) {
        allSavedCookies.add(cookie);
      }
    }
    for (final cookie in pathCookies) {
      if (!allSavedCookies.any((c) => c.name == cookie.name)) {
        allSavedCookies.add(cookie);
      }
    }

    await logger.log(
      level: 'info',
      subsystem: 'cookies',
      message: 'Cookies saved directly to Dio CookieJar',
      operationId: operationId,
      context: 'cookie_save_direct',
      extra: {
        'uri': uri.toString(),
        'uri_host': uri.host,
        'cookie_count': cookies.length,
        'saved_cookie_count': allSavedCookies.length,
        'saved_cookie_names': allSavedCookies.map((c) => c.name).toList(),
        'saved_cookies': allSavedCookies
            .map((c) => {
                  'name': c.name,
                  'domain': c.domain ?? '<null>',
                  'path': c.path ?? '/',
                  'has_value': c.value.isNotEmpty,
                  'value_length': c.value.length,
                  'value_preview': c.value.isNotEmpty
                      ? '${c.value.substring(0, c.value.length > 20 ? 20 : c.value.length)}...'
                      : '<empty>',
                })
            .toList(),
        'verification':
            allSavedCookies.length == cookies.length ? 'success' : 'mismatch',
        'uri_cookie_count': savedCookies.length,
        'base_cookie_count': baseCookies.length,
        'path_cookie_count': pathCookies.length,
        'input_cookie_names': cookies.map((c) => c.name).toList(),
      },
    );

    // NOTE: If cookies were not saved to CookieJar, it's not critical
    // SessionManager is the primary storage for WebView cookies
    // CookieJar is primarily for cookies from HTTP responses
    if (allSavedCookies.isEmpty && cookies.isNotEmpty) {
      await logger.log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'Cookies not found in CookieJar after save (non-critical)',
        operationId: operationId,
        context: 'cookie_save_direct',
        extra: {
          'uri': uri.toString(),
          'uri_host': uri.host,
          'input_cookie_count': cookies.length,
          'saved_cookie_count': allSavedCookies.length,
          'input_cookie_names': cookies.map((c) => c.name).toList(),
          'note':
              'Cookies are stored in SessionManager, which is the primary storage. CookieJar is primarily for HTTP response cookies.',
        },
      );
    }
  }

  /// Synchronizes cookies from Dio cookie jar to WebView storage.
  ///
  /// DEPRECATED: This method is no longer needed as we use direct HTTP authentication.
  /// Kept as no-op for backward compatibility.
  ///
  /// @deprecated Use SimpleCookieManager instead
  static Future<void> syncCookiesToWebView([CookieJar? cookieJar]) async {
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
  /// The [cookieJar] parameter is the CookieJar instance to use.
  static Future<void> syncCookiesOnEndpointSwitch(
    String oldEndpoint,
    String newEndpoint, [
    CookieJar? cookieJar,
  ]) async {
    final operationId =
        'cookie_sync_endpoint_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      await logger.log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'Cookie sync on endpoint switch started',
        operationId: operationId,
        context: 'cookie_sync_endpoint_switch',
        extra: {
          'old_endpoint': oldEndpoint,
          'new_endpoint': newEndpoint,
        },
      );

      final jar = cookieJar ?? CookieJar();
      final oldUri = Uri.parse(oldEndpoint);
      final newUri = Uri.parse(newEndpoint);

      // If endpoints are in the same domain family (rutracker.net/me/org),
      // cookies should be compatible
      final oldHost = oldUri.host;
      final newHost = newUri.host;
      final isSameFamily = oldHost.contains('rutracker') &&
          newHost.contains('rutracker') &&
          (oldHost.endsWith('.net') ||
              oldHost.endsWith('.me') ||
              oldHost.endsWith('.org')) &&
          (newHost.endsWith('.net') ||
              newHost.endsWith('.me') ||
              newHost.endsWith('.org'));

      if (!isSameFamily) {
        await logger.log(
          level: 'debug',
          subsystem: 'cookies',
          message:
              'Endpoints are not in same domain family, skipping cookie sync',
          operationId: operationId,
          context: 'cookie_sync_endpoint_switch',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
          extra: {
            'old_host': oldHost,
            'new_host': newHost,
            'is_same_family': false,
          },
        );
        return;
      }

      // Try to load cookies from old endpoint
      final oldCookies = await jar.loadForRequest(oldUri);

      await logger.log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'Loaded cookies from old endpoint',
        operationId: operationId,
        context: 'cookie_sync_endpoint_switch',
        extra: {
          'old_endpoint': oldEndpoint,
          'cookie_count': oldCookies.length,
          'cookies': oldCookies
              .map((c) => {
                    'name': c.name,
                    'domain': c.domain ?? '<null>',
                    'path': c.path ?? '/',
                  })
              .toList(),
        },
      );

      if (oldCookies.isEmpty) {
        await logger.log(
          level: 'debug',
          subsystem: 'cookies',
          message: 'No cookies found for old endpoint',
          operationId: operationId,
          context: 'cookie_sync_endpoint_switch',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
          extra: {'old_endpoint': oldEndpoint},
        );
        return;
      }

      // Check if new endpoint already has cookies
      final newCookies = await jar.loadForRequest(newUri);

      await logger.log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'Checked cookies for new endpoint',
        operationId: operationId,
        context: 'cookie_sync_endpoint_switch',
        extra: {
          'new_endpoint': newEndpoint,
          'cookie_count': newCookies.length,
          'has_existing_cookies': newCookies.isNotEmpty,
        },
      );

      if (newCookies.isNotEmpty) {
        await logger.log(
          level: 'debug',
          subsystem: 'cookies',
          message: 'New endpoint already has cookies, skipping sync',
          operationId: operationId,
          context: 'cookie_sync_endpoint_switch',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
          extra: {
            'new_endpoint': newEndpoint,
            'existing_cookie_count': newCookies.length,
          },
        );
        return;
      }

      // Copy compatible cookies to new endpoint
      // RuTracker cookies are typically domain-wide, so they should work
      final compatibleCookies = oldCookies.where((cookie) {
        // Copy cookies that are domain-wide or for rutracker domains
        final cookieDomain = cookie.domain?.toLowerCase() ?? '';
        return cookieDomain.isEmpty ||
            cookieDomain.contains('rutracker') ||
            cookieDomain.startsWith('.');
      }).toList();

      final incompatibleCookies = oldCookies.length - compatibleCookies.length;

      await logger.log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'Filtered compatible cookies for endpoint switch',
        operationId: operationId,
        context: 'cookie_sync_endpoint_switch',
        extra: {
          'total_old_cookies': oldCookies.length,
          'compatible_cookies': compatibleCookies.length,
          'incompatible_cookies': incompatibleCookies,
          'compatible_cookies_list': compatibleCookies
              .map((c) => {
                    'name': c.name,
                    'domain': c.domain ?? '<null>',
                    'path': c.path ?? '/',
                  })
              .toList(),
        },
      );

      if (compatibleCookies.isNotEmpty) {
        // Save cookies for all rutracker domains to ensure compatibility
        final rutrackerDomains = EndpointManager.getRutrackerDomains();

        final savedDomains = <String>[];
        for (final domain in rutrackerDomains) {
          final domainUri = Uri.parse('https://$domain');
          await jar.saveFromResponse(domainUri, compatibleCookies);
          savedDomains.add(domain);

          await logger.log(
            level: 'debug',
            subsystem: 'cookies',
            message: 'Saved cookies for domain during endpoint switch',
            operationId: operationId,
            context: 'cookie_sync_endpoint_switch',
            extra: {
              'domain': domain,
              'cookie_count': compatibleCookies.length,
            },
          );
        }

        final duration = DateTime.now().difference(startTime).inMilliseconds;
        await logger.log(
          level: 'info',
          subsystem: 'cookies',
          message: 'Synced cookies on endpoint switch',
          operationId: operationId,
          context: 'cookie_sync_endpoint_switch',
          durationMs: duration,
          extra: {
            'old_endpoint': oldEndpoint,
            'new_endpoint': newEndpoint,
            'cookies_synced': compatibleCookies.length,
            'domains_saved_to': savedDomains,
            'cookies': compatibleCookies
                .map((c) => {
                      'name': c.name,
                      'domain': c.domain ?? '<null>',
                      'path': c.path ?? '/',
                    })
                .toList(),
          },
        );
      } else {
        final duration = DateTime.now().difference(startTime).inMilliseconds;
        await logger.log(
          level: 'warning',
          subsystem: 'cookies',
          message: 'No compatible cookies to sync on endpoint switch',
          operationId: operationId,
          context: 'cookie_sync_endpoint_switch',
          durationMs: duration,
          extra: {
            'old_endpoint': oldEndpoint,
            'new_endpoint': newEndpoint,
            'total_old_cookies': oldCookies.length,
          },
        );
      }
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'warning',
        subsystem: 'cookies',
        message: 'Failed to sync cookies on endpoint switch',
        operationId: operationId,
        context: 'cookie_sync_endpoint_switch',
        durationMs: duration,
        cause: e.toString(),
        extra: {
          'old_endpoint': oldEndpoint,
          'new_endpoint': newEndpoint,
          'stack_trace':
              (e is Error) ? (e as Error).stackTrace.toString() : null,
        },
      );
    }
  }

  /// Clears all stored cookies from the cookie jar.
  ///
  /// This method removes all cookies, effectively logging out the user
  /// from any authenticated sessions.
  ///
  /// The [cookieJar] parameter is the CookieJar instance to clear.
  static Future<void> clearCookies([CookieJar? cookieJar]) async {
    await cookieJar?.deleteAll();

    // Also clear from SharedPreferences
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('rutracker_cookies_v1');

    await StructuredLogger().log(
      level: 'info',
      subsystem: 'auth',
      message: 'Cleared all cookies',
    );
  }

  /// Checks if valid cookies are available for authentication.
  ///
  /// Returns true if cookies exist and appear to be valid, false otherwise.
  static Future<bool> hasValidCookies() async {
    final operationId =
        'has_valid_cookies_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      // Step 0: Check database FIRST - this is the most reliable source
      try {
        final appDb = AppDatabase.getInstance();
        final db = appDb.database;
        final endpointManager = EndpointManager(db, appDb);
        final activeBase = await endpointManager.getActiveEndpoint();

        final cookieDbService = CookieDatabaseService(appDb);
        final cookieHeader =
            await cookieDbService.getCookiesForAnyEndpoint(activeBase);

        if (cookieHeader != null && cookieHeader.isNotEmpty) {
          final trimmed = cookieHeader.trim();
          if (trimmed.isNotEmpty && trimmed.contains('=')) {
            await logger.log(
              level: 'info',
              subsystem: 'cookies',
              message: 'Found valid cookies in database',
              operationId: operationId,
              context: 'has_valid_cookies',
              durationMs: DateTime.now().difference(startTime).inMilliseconds,
              extra: {
                'active_endpoint': activeBase,
                'cookie_header_length': cookieHeader.length,
                'source': 'database',
                'has_bb_session': cookieHeader.contains('bb_session='),
                'has_bb_data': cookieHeader.contains('bb_data='),
              },
            );
            return true;
          }
        }
      } on Exception catch (e) {
        await logger.log(
          level: 'debug',
          subsystem: 'cookies',
          message: 'Failed to check cookies in database',
          operationId: operationId,
          context: 'has_valid_cookies',
          cause: e.toString(),
        );
      }

      // Step 1: Check CookieService (Android CookieManager) - fallback source
      final rutrackerDomains = EndpointManager.getRutrackerDomains();
      for (final domain in rutrackerDomains) {
        try {
          final url = 'https://$domain';
          final cookieHeader = await CookieService.getCookiesForUrl(url);

          if (cookieHeader != null && cookieHeader.isNotEmpty) {
            // Check if cookie header contains actual cookies (not just whitespace)
            final trimmed = cookieHeader.trim();
            if (trimmed.isNotEmpty && trimmed.contains('=')) {
              await logger.log(
                level: 'info',
                subsystem: 'cookies',
                message: 'Found valid cookies in CookieService',
                operationId: operationId,
                context: 'has_valid_cookies',
                durationMs: DateTime.now().difference(startTime).inMilliseconds,
                extra: {
                  'domain': domain,
                  'cookie_header_length': cookieHeader.length,
                  'source': 'CookieService',
                },
              );
              return true;
            }
          }
        } on Exception {
          // Continue to next domain
          continue;
        }
      }

      // Step 2: Check CookieJar (fallback) - cookies may be here after direct HTTP login
      try {
        final jar = await DioClient.getCookieJar();
        if (jar != null) {
          final appDb = AppDatabase.getInstance();
          final db = appDb.database;
          final endpointManager = EndpointManager(db, appDb);
          final activeBase = await endpointManager.getActiveEndpoint();
          final uri = Uri.parse(activeBase);
          final cookies = await jar.loadForRequest(uri);

          if (cookies.isNotEmpty) {
            // Check if we have session cookies
            final hasSessionCookie = cookies.any((c) =>
                c.name.toLowerCase().contains('session') ||
                c.name == 'bb_session' ||
                c.name == 'bb_data');

            if (hasSessionCookie) {
              await logger.log(
                level: 'info',
                subsystem: 'cookies',
                message: 'Found valid cookies in CookieJar',
                operationId: operationId,
                context: 'has_valid_cookies',
                durationMs: DateTime.now().difference(startTime).inMilliseconds,
                extra: {
                  'cookie_count': cookies.length,
                  'cookie_names': cookies.map((c) => c.name).toList(),
                  'source': 'CookieJar',
                },
              );
              return true;
            }
          }
        }
      } on Exception catch (e) {
        await logger.log(
          level: 'debug',
          subsystem: 'cookies',
          message: 'Error checking CookieJar',
          operationId: operationId,
          context: 'has_valid_cookies',
          cause: e.toString(),
        );
      }

      // Step 3: Check SecureStorage (fallback) - cookies may be here after direct HTTP login
      try {
        final appDb = AppDatabase.getInstance();
        final db = appDb.database;
        final endpointManager = EndpointManager(db, appDb);
        final activeBase = await endpointManager.getActiveEndpoint();
        final simpleCookieManager = SimpleCookieManager();
        final cookieString = await simpleCookieManager.getCookie(activeBase);

        if (cookieString != null &&
            cookieString.isNotEmpty &&
            cookieString.contains('bb_session=')) {
          await logger.log(
            level: 'info',
            subsystem: 'cookies',
            message: 'Found valid cookies in SecureStorage',
            operationId: operationId,
            context: 'has_valid_cookies',
            durationMs: DateTime.now().difference(startTime).inMilliseconds,
            extra: {
              'cookie_length': cookieString.length,
              'source': 'SecureStorage',
            },
          );
          return true;
        }
      } on Exception catch (e) {
        await logger.log(
          level: 'debug',
          subsystem: 'cookies',
          message: 'Error checking SecureStorage',
          operationId: operationId,
          context: 'has_valid_cookies',
          cause: e.toString(),
        );
      }

      // Step 4: Check SharedPreferences (WebView storage) - legacy fallback
      try {
        final prefs = await SharedPreferences.getInstance();
        final cookieJson = prefs.getString('rutracker_cookies_v1');

        if (cookieJson != null && cookieJson.isNotEmpty) {
          // Check if cookies are in valid JSON format
          try {
            final list = jsonDecode(cookieJson) as List<dynamic>;
            if (list.isNotEmpty) {
              // Check if we have at least one cookie with a value
              for (final c in list) {
                final cookie = c as Map<String, dynamic>;
                final name = cookie['name'] as String?;
                final value = cookie['value'] as String?;

                if (name != null &&
                    name.isNotEmpty &&
                    value != null &&
                    value.isNotEmpty) {
                  await logger.log(
                    level: 'info',
                    subsystem: 'cookies',
                    message: 'Found valid cookies in SharedPreferences',
                    operationId: operationId,
                    context: 'has_valid_cookies',
                    durationMs:
                        DateTime.now().difference(startTime).inMilliseconds,
                    extra: {
                      'source': 'SharedPreferences',
                    },
                  );
                  return true;
                }
              }
            }
          } on FormatException {
            // Invalid JSON, continue
          }
        }
      } on Exception {
        // Ignore SharedPreferences errors
      }

      await logger.log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'No valid cookies found in any source',
        operationId: operationId,
        context: 'has_valid_cookies',
        durationMs: DateTime.now().difference(startTime).inMilliseconds,
        extra: {
          'checked_sources': [
            'CookieService',
            'CookieJar',
            'SecureStorage',
            'SharedPreferences'
          ],
        },
      );
      return false;
    } on Exception catch (e) {
      await logger.log(
        level: 'error',
        subsystem: 'cookies',
        message: 'Exception checking for valid cookies',
        operationId: operationId,
        context: 'has_valid_cookies',
        durationMs: DateTime.now().difference(startTime).inMilliseconds,
        cause: e.toString(),
      );
      return false;
    }
  }

  /// Validates cookies by making a test request to RuTracker.
  ///
  /// Returns true if cookies are valid and authentication is active, false otherwise.
  ///
  /// The [cookieJar] parameter is the CookieJar instance to use.
  /// The [dio] parameter is the Dio instance to use for the test request.
  static Future<bool> validateCookies(
    Dio dio, [
    CookieJar? cookieJar,
  ]) async {
    final operationId =
        'cookie_validate_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      await logger.log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'Cookie validation started',
        operationId: operationId,
        context: 'cookie_validation',
      );

      final hasCookiesCheckStartTime = DateTime.now();
      final hasCookies = await hasValidCookies();
      final hasCookiesCheckDuration =
          DateTime.now().difference(hasCookiesCheckStartTime).inMilliseconds;

      if (!hasCookies) {
        await logger.log(
          level: 'debug',
          subsystem: 'cookies',
          message: 'No cookies available for validation',
          operationId: operationId,
          context: 'cookie_validation',
          durationMs: hasCookiesCheckDuration,
        );
        return false;
      }

      // Only sync from WebView if we don't have cookies in Dio jar
      // This prevents unnecessary syncs when cookies are already synced
      try {
        final jar = cookieJar ?? CookieJar();
        final appDb = AppDatabase.getInstance();
        final db = appDb.database;
        final endpointManager = EndpointManager(db, appDb);
        final activeBase = await endpointManager.getActiveEndpoint();
        final uri = Uri.parse(activeBase);

        await logger.log(
          level: 'debug',
          subsystem: 'cookies',
          message: 'Loading cookies from Dio cookie jar for validation',
          operationId: operationId,
          context: 'cookie_validation',
          extra: {
            'endpoint': activeBase,
            'uri_host': uri.host,
          },
        );

        final loadCookiesStartTime = DateTime.now();
        final dioCookies = await jar.loadForRequest(uri);
        final loadCookiesDuration =
            DateTime.now().difference(loadCookiesStartTime).inMilliseconds;

        await logger.log(
          level: 'debug',
          subsystem: 'cookies',
          message: 'Loaded cookies from Dio cookie jar',
          operationId: operationId,
          context: 'cookie_validation',
          durationMs: loadCookiesDuration,
          extra: {
            'cookie_count': dioCookies.length,
            'cookies': dioCookies
                .map((c) => {
                      'name': c.name,
                      'domain': c.domain ?? '<null>',
                      'path': c.path ?? '/',
                      'has_value': c.value.isNotEmpty,
                      'value_length': c.value.length,
                      'value_preview': c.value.isNotEmpty
                          ? '${c.value.substring(0, c.value.length > 20 ? 20 : c.value.length)}...'
                          : '<empty>',
                      'is_session_cookie':
                          c.name.toLowerCase().contains('session') ||
                              c.name == 'bb_session' ||
                              c.name == 'bb_data',
                    })
                .toList(),
            'endpoint': activeBase,
          },
        );

        if (dioCookies.isEmpty) {
          // No cookies in Dio jar, sync from WebView
          await logger.log(
            level: 'debug',
            subsystem: 'cookies',
            message:
                'No cookies in Dio jar, syncing from WebView (expected during first validation)',
            operationId: operationId,
            context: 'cookie_validation',
            extra: {
              'endpoint': activeBase,
              'uri_host': uri.host,
            },
          );
          await syncCookiesFromWebView(jar);

          // Wait a bit for cookies to be persisted
          await Future.delayed(const Duration(milliseconds: 200));

          // Check again after sync - try multiple URI variations
          final dioCookiesAfterSync = await jar.loadForRequest(uri);
          final baseUri = Uri.parse('https://${uri.host}');
          final baseCookiesAfterSync = await jar.loadForRequest(baseUri);
          final pathUri = Uri.parse('https://${uri.host}/');
          final pathCookiesAfterSync = await jar.loadForRequest(pathUri);

          // Combine all found cookies (avoid duplicates)
          final allCookiesAfterSync = <io.Cookie>[];
          for (final cookie in dioCookiesAfterSync) {
            if (!allCookiesAfterSync.any((c) => c.name == cookie.name)) {
              allCookiesAfterSync.add(cookie);
            }
          }
          for (final cookie in baseCookiesAfterSync) {
            if (!allCookiesAfterSync.any((c) => c.name == cookie.name)) {
              allCookiesAfterSync.add(cookie);
            }
          }
          for (final cookie in pathCookiesAfterSync) {
            if (!allCookiesAfterSync.any((c) => c.name == cookie.name)) {
              allCookiesAfterSync.add(cookie);
            }
          }

          await logger.log(
            level: 'info',
            subsystem: 'cookies',
            message: 'Cookies in Dio jar after sync from WebView',
            operationId: operationId,
            context: 'cookie_validation',
            extra: {
              'cookie_count': allCookiesAfterSync.length,
              'uri_cookie_count': dioCookiesAfterSync.length,
              'base_cookie_count': baseCookiesAfterSync.length,
              'path_cookie_count': pathCookiesAfterSync.length,
              'cookies': allCookiesAfterSync
                  .map((c) => {
                        'name': c.name,
                        'domain': c.domain ?? '<null>',
                        'path': c.path ?? '/',
                        'has_value': c.value.isNotEmpty,
                        'value_length': c.value.length,
                        'value_preview': c.value.isNotEmpty
                            ? '${c.value.substring(0, c.value.length > 20 ? 20 : c.value.length)}...'
                            : '<empty>',
                        'is_session_cookie':
                            c.name.toLowerCase().contains('session') ||
                                c.name == 'bb_session' ||
                                c.name == 'bb_data',
                      })
                  .toList(),
            },
          );

          if (allCookiesAfterSync.isEmpty) {
            await logger.log(
              level: 'debug',
              subsystem: 'cookies',
              message:
                  'No cookies in Dio jar after sync from WebView (cookies may be in SessionManager, trying SessionManager sync)',
              operationId: operationId,
              context: 'cookie_validation',
              extra: {
                'endpoint': activeBase,
                'uri_host': uri.host,
                'note':
                    'Cookies may be in SessionManager but not in CookieJar. Trying to sync from SessionManager.',
              },
            );

            // DEPRECATED: SessionManager sync removed - no longer using FlutterCookieBridge
            // Cookies are now managed directly through SimpleCookieManager
          }
        }
      } on Exception catch (e) {
        await logger.log(
          level: 'warning',
          subsystem: 'cookies',
          message: 'Exception checking Dio cookies, syncing from WebView',
          operationId: operationId,
          context: 'cookie_validation',
          cause: e.toString(),
        );
        // If check fails, sync anyway for safety
        final jar = cookieJar ?? CookieJar();
        await syncCookiesFromWebView(jar);
      }

      // Make a lightweight test request to check authentication
      final appDb = AppDatabase.getInstance();
      final db = appDb.database;
      final endpointManager = EndpointManager(db, appDb);
      final activeBase = await endpointManager.getActiveEndpoint();
      // Use root URL instead of index.php - more reliable
      final testUrl = activeBase.endsWith('/') ? activeBase : '$activeBase/';

      // Check what cookies will be sent with the request
      final jar = cookieJar ?? CookieJar();
      final testUri = Uri.parse(testUrl);
      final cookiesForRequest = await jar.loadForRequest(testUri);

      await logger.log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'Making test request to validate cookies',
        operationId: operationId,
        context: 'cookie_validation',
        extra: {
          'test_url': testUrl,
          'endpoint': activeBase,
          'cookies_count': cookiesForRequest.length,
          'cookies_names': cookiesForRequest.map((c) => c.name).toList(),
        },
      );

      try {
        // Try to access root page - should work if cookies are valid
        // Use normal request flow - SessionInterceptor now allows requests with cookies
        final testRequestStartTime = DateTime.now();
        final response = await dio
            .get(
              testUrl,
              options: Options(
                validateStatus: (status) => status != null && status < 500,
                followRedirects:
                    true, // Allow redirects to see if we get redirected to login
                headers: {
                  // Ensure cookies are sent by explicitly checking Cookie header
                  // CookieManager interceptor should add them automatically
                },
              ),
            )
            .timeout(const Duration(seconds: 10));

        final testRequestDuration =
            DateTime.now().difference(testRequestStartTime).inMilliseconds;

        // If we get redirected to login, cookies are invalid
        final location = response.headers.value('location');
        final isRedirectedToLogin = location != null &&
            (location.contains('login.php') || location.contains('login'));

        if (isRedirectedToLogin) {
          final totalDuration =
              DateTime.now().difference(startTime).inMilliseconds;
          await logger.log(
            level: 'info',
            subsystem: 'cookies',
            message: 'Cookie validation failed - redirected to login',
            operationId: operationId,
            context: 'cookie_validation',
            durationMs: totalDuration,
            extra: {
              'test_url': testUrl,
              'status_code': response.statusCode,
              'redirect_location': location,
              'test_request_duration_ms': testRequestDuration,
            },
          );
          return false;
        }

        // If we get a successful response or a non-login redirect, cookies are likely valid
        final isValid = response.statusCode != null &&
            response.statusCode! >= 200 &&
            response.statusCode! < 400;

        final totalDuration =
            DateTime.now().difference(startTime).inMilliseconds;
        await logger.log(
          level: isValid ? 'info' : 'warning',
          subsystem: 'cookies',
          message: 'Cookie validation ${isValid ? "succeeded" : "failed"}',
          operationId: operationId,
          context: 'cookie_validation',
          durationMs: totalDuration,
          extra: {
            'test_url': testUrl,
            'status_code': response.statusCode,
            'is_valid': isValid,
            'redirect_location': location,
            'test_request_duration_ms': testRequestDuration,
            'response_headers': {
              'server': response.headers.value('server') ?? '',
              'cf-ray': response.headers.value('cf-ray') ?? '',
            },
          },
        );

        return isValid;
      } on TimeoutException {
        final totalDuration =
            DateTime.now().difference(startTime).inMilliseconds;
        await logger.log(
          level: 'warning',
          subsystem: 'cookies',
          message: 'Cookie validation timed out',
          operationId: operationId,
          context: 'cookie_validation',
          durationMs: totalDuration,
          extra: {
            'test_url': testUrl,
            'timeout_seconds': 10,
          },
        );
        // Assume valid if request times out (network issue, not auth issue)
        return true;
      } on DioException catch (e) {
        final totalDuration =
            DateTime.now().difference(startTime).inMilliseconds;
        final errorType = switch (e.type) {
          DioExceptionType.connectionTimeout => 'Connection timeout',
          DioExceptionType.sendTimeout => 'Send timeout',
          DioExceptionType.receiveTimeout => 'Receive timeout',
          DioExceptionType.badResponse => 'Bad response',
          DioExceptionType.cancel => 'Request cancelled',
          DioExceptionType.connectionError => 'Connection error',
          DioExceptionType.badCertificate => 'Bad certificate',
          DioExceptionType.unknown => 'Unknown error',
        };

        if (e.response?.statusCode == 401 ||
            e.response?.statusCode == 403 ||
            (e.message?.contains('Authentication required') ?? false)) {
          await logger.log(
            level: 'info',
            subsystem: 'cookies',
            message: 'Cookie validation failed - authentication required',
            operationId: operationId,
            context: 'cookie_validation',
            durationMs: totalDuration,
            cause: e.toString(),
            extra: {
              'test_url': testUrl,
              'status_code': e.response?.statusCode,
              'error_type': errorType,
            },
          );
          return false;
        }
        // For other errors, assume cookies might still be valid
        await logger.log(
          level: 'warning',
          subsystem: 'cookies',
          message: 'Cookie validation error (assuming valid)',
          operationId: operationId,
          context: 'cookie_validation',
          durationMs: totalDuration,
          cause: e.toString(),
          extra: {
            'test_url': testUrl,
            'error_type': errorType,
            'status_code': e.response?.statusCode,
            'stack_trace': e.stackTrace.toString(),
          },
        );
        return true;
      }
    } on Exception catch (e) {
      final totalDuration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'cookies',
        message: 'Failed to validate cookies',
        operationId: operationId,
        context: 'cookie_validation',
        durationMs: totalDuration,
        cause: e.toString(),
        extra: {
          'stack_trace':
              (e is Error) ? (e as Error).stackTrace.toString() : null,
        },
      );
      return false;
    }
  }
}
