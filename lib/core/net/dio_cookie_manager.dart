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
import 'package:flutter_cookie_bridge/session_manager.dart' as bridge_session;
import 'package:jabook/core/endpoints/endpoint_manager.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/data/db/app_database.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Manages cookie synchronization between WebView, Dio CookieJar, and SessionManager.
class DioCookieManager {
  /// Private constructor to prevent instantiation.
  const DioCookieManager._();

  /// Synchronizes cookies from WebView to the Dio client.
  ///
  /// This method should be called to ensure that authentication cookies
  /// obtained through WebView login are available for HTTP requests.
  ///
  /// It validates cookies before saving and handles various cookie formats.
  ///
  /// The [cookieJar] parameter is the CookieJar instance to save cookies to.
  static Future<void> syncCookiesFromWebView([CookieJar? cookieJar]) async {
    final operationId =
        'cookie_sync_webview_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      await logger.log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'Cookie sync from WebView started',
        operationId: operationId,
        context: 'cookie_sync',
      );

      final prefs = await SharedPreferences.getInstance();
      final cookieJson = prefs.getString('rutracker_cookies_v1');
      if (cookieJson == null) {
        await logger.log(
          level: 'debug',
          subsystem: 'cookies',
          message: 'No cookies found in WebView storage',
          operationId: operationId,
          context: 'cookie_sync',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
        );
        return;
      }

      final db = AppDatabase().database;
      final endpointManager = EndpointManager(db);
      final activeBase = await endpointManager.getActiveEndpoint();
      final uri = Uri.parse(activeBase);

      // Parse cookies list from JSON (as saved by secure webview)
      List<dynamic> list;
      try {
        list = jsonDecode(cookieJson) as List<dynamic>;
      } on Exception catch (e) {
        await logger.log(
          level: 'error',
          subsystem: 'cookies',
          message: 'Failed to parse cookie JSON',
          operationId: operationId,
          context: 'cookie_sync',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
          cause: e.toString(),
        );
        return;
      }

      await logger.log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'Parsed cookies from JSON',
        operationId: operationId,
        context: 'cookie_sync',
        extra: {
          'total_cookies': list.length,
          'active_endpoint': activeBase,
        },
      );

      if (list.isEmpty) {
        await logger.log(
          level: 'debug',
          subsystem: 'cookies',
          message: 'Cookie list is empty',
          operationId: operationId,
          context: 'cookie_sync',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
        );
        return;
      }

      final jar = cookieJar ?? CookieJar();
      final cookies = <Cookie>[];
      final skippedCookies = <Map<String, dynamic>>[];
      final validName = RegExp(r"^[!#\$%&'*+.^_`|~0-9A-Za-z-]+$");
      var skippedCount = 0;

      for (final c in list) {
        try {
          final m = Map<String, dynamic>.from(c as Map);
          var name = m['name']?.toString();
          var value = m['value']?.toString();
          final originalName = name;

          if (name == null || value == null) {
            skippedCount++;
            skippedCookies.add({
              'name': originalName ?? '<null>',
              'reason': 'name_or_value_null',
            });
            continue;
          }

          // Trim and strip surrounding quotes
          name = name.trim();
          value = value.trim();
          if (name.isEmpty || value.isEmpty) {
            skippedCount++;
            skippedCookies.add({
              'name': originalName ?? '<unknown>',
              'reason': 'name_or_value_empty_after_trim',
            });
            continue;
          }

          if (name.length >= 2 && name.startsWith('"') && name.endsWith('"')) {
            name = name.substring(1, name.length - 1);
          }
          if (value.length >= 2 &&
              value.startsWith('"') &&
              value.endsWith('"')) {
            value = value.substring(1, value.length - 1);
          }

          // Validate cookie name format
          if (!validName.hasMatch(name)) {
            skippedCount++;
            skippedCookies.add({
              'name': name,
              'reason': 'invalid_name_format',
            });
            continue;
          }

          // Extract domain, path, and other properties
          final domain = (m['domain']?.toString() ?? uri.host).trim();
          final path = (m['path']?.toString() ?? '/').trim();

          // Validate domain matches the endpoint
          if (!domain.contains(uri.host) && !domain.startsWith('.')) {
            // Allow subdomains and exact matches
            if (domain != uri.host && !domain.endsWith(uri.host)) {
              skippedCount++;
              skippedCookies.add({
                'name': name,
                'domain': domain,
                'reason': 'domain_mismatch',
                'expected_host': uri.host,
              });
              continue;
            }
          }

          final cookie = Cookie(name, value)
            ..domain = domain
            ..path = path
            ..secure = true;

          cookies.add(cookie);
        } on Exception catch (e) {
          // Log individual cookie parsing errors but continue
          skippedCount++;
          skippedCookies.add({
            'reason': 'parse_exception',
            'error': e.toString(),
          });
        }
      }

      // Log parsed cookies (without values for security)
      if (cookies.isNotEmpty) {
        await logger.log(
          level: 'debug',
          subsystem: 'cookies',
          message: 'Parsed cookies from WebView',
          operationId: operationId,
          context: 'cookie_sync',
          extra: {
            'cookies': cookies
                .map((c) => {
                      'name': c.name,
                      'domain': c.domain ?? '<null>',
                      'path': c.path ?? '/',
                    })
                .toList(),
          },
        );
      }

      if (skippedCookies.isNotEmpty) {
        await logger.log(
          level: 'debug',
          subsystem: 'cookies',
          message: 'Skipped cookies during parsing',
          operationId: operationId,
          context: 'cookie_sync',
          extra: {
            'skipped_cookies': skippedCookies,
          },
        );
      }

      if (cookies.isEmpty) {
        await logger.log(
          level: 'warning',
          subsystem: 'cookies',
          message: 'No valid cookies to sync after parsing',
          operationId: operationId,
          context: 'cookie_sync',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
          extra: {
            'total': list.length,
            'skipped': skippedCount,
          },
        );
        return;
      }

      // Save cookies for all rutracker domains (net, me, org) to ensure
      // cookies work when switching between mirrors
      final rutrackerDomains = EndpointManager.getRutrackerDomains();
      final savedDomains = <String>[];
      final domainCookieCounts = <String, int>{};

      for (final domain in rutrackerDomains) {
        try {
          final domainUri = Uri.parse('https://$domain');
          // Filter cookies that belong to this domain or are domain-wide
          final domainCookies = cookies.where((cookie) {
            final cookieDomain = cookie.domain?.toLowerCase() ?? '';
            if (cookieDomain.isEmpty) return false;
            return cookieDomain == domain ||
                cookieDomain == '.$domain' ||
                cookieDomain.contains(domain) ||
                (cookieDomain.startsWith('.') &&
                    cookieDomain.substring(1) == domain);
          }).toList();

          if (domainCookies.isNotEmpty) {
            await jar.saveFromResponse(domainUri, domainCookies);
            savedDomains.add(domain);
            domainCookieCounts[domain] = domainCookies.length;

            await logger.log(
              level: 'debug',
              subsystem: 'cookies',
              message: 'Saved cookies for domain',
              operationId: operationId,
              context: 'cookie_sync',
              extra: {
                'domain': domain,
                'cookie_count': domainCookies.length,
                'cookies': domainCookies
                    .map((c) => {
                          'name': c.name,
                          'domain': c.domain ?? '<null>',
                          'path': c.path ?? '/',
                        })
                    .toList(),
              },
            );
          }
        } on Exception catch (e) {
          await logger.log(
            level: 'debug',
            subsystem: 'cookies',
            message: 'Failed to save cookies for domain',
            operationId: operationId,
            context: 'cookie_sync',
            cause: e.toString(),
            extra: {'domain': domain},
          );
        }
      }

      // Also save to active endpoint URI
      if (!savedDomains.contains(uri.host)) {
        await jar.saveFromResponse(uri, cookies);
        savedDomains.add(uri.host);
        domainCookieCounts[uri.host] = cookies.length;
      }

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'info',
        subsystem: 'cookies',
        message: 'Synced cookies from WebView to Dio',
        operationId: operationId,
        context: 'cookie_sync',
        durationMs: duration,
        extra: {
          'count': cookies.length,
          'total': list.length,
          'skipped': skippedCount,
          'domains': savedDomains,
          'domain_cookie_counts': domainCookieCounts,
        },
      );
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'warning',
        subsystem: 'cookies',
        message: 'Failed to sync cookies from WebView',
        operationId: operationId,
        context: 'cookie_sync',
        durationMs: duration,
        cause: e.toString(),
        extra: {
          'stack_trace':
              (e is Error) ? (e as Error).stackTrace.toString() : null,
        },
      );
    }
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
    final operationId = 'save_cookies_direct_${DateTime.now().millisecondsSinceEpoch}';
    
    await logger.log(
      level: 'debug',
      subsystem: 'cookies',
      message: 'Saving cookies directly to Dio CookieJar',
      operationId: operationId,
      context: 'cookie_save_direct',
      extra: {
        'uri': uri.toString(),
        'cookie_count': cookies.length,
        'cookies': cookies.map((c) => {
          'name': c.name,
          'domain': c.domain ?? '<null>',
          'path': c.path ?? '/',
          'has_value': c.value.isNotEmpty,
          'value_length': c.value.length,
          'secure': c.secure,
          'http_only': c.httpOnly,
        }).toList(),
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
    
    // CRITICAL: Create cookies without domain to let CookieJar use URI host automatically
    // This is more reliable than trying to normalize domains manually
    final cookiesToSave = cookies.map((cookie) {
      final cookieToSave = io.Cookie(cookie.name, cookie.value)
        ..path = cookie.path ?? '/'
        ..secure = cookie.secure
        ..httpOnly = cookie.httpOnly;
      // Don't set domain - let CookieJar use URI host automatically
      // This ensures cookies are saved correctly regardless of domain format
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
            'note': 'Cookies are stored in SessionManager, which is the primary storage for WebView cookies',
          },
        );
      }
    }
    
    // CRITICAL: Verify cookies were saved by loading them back
    // Use a small delay to ensure cookies are persisted
    await Future.delayed(const Duration(milliseconds: 100));
    
    // Try loading from multiple URI variations to ensure we find saved cookies
    final savedCookies = await jar.loadForRequest(uri);
    final baseUri = Uri.parse('https://${uri.host}');
    final baseCookies = await jar.loadForRequest(baseUri);
    
    // Combine cookies from both URIs (avoid duplicates)
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
        'saved_cookies': allSavedCookies.map((c) => {
          'name': c.name,
          'domain': c.domain ?? '<null>',
          'path': c.path ?? '/',
          'has_value': c.value.isNotEmpty,
          'value_length': c.value.length,
          'value_preview': c.value.isNotEmpty 
              ? '${c.value.substring(0, c.value.length > 20 ? 20 : c.value.length)}...' 
              : '<empty>',
        }).toList(),
        'verification': allSavedCookies.length == cookies.length 
            ? 'success' 
            : 'mismatch',
        'uri_cookie_count': savedCookies.length,
        'base_cookie_count': baseCookies.length,
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
          'note': 'Cookies are stored in SessionManager, which is the primary storage. CookieJar is primarily for HTTP response cookies.',
        },
      );
    }
  }

  /// Synchronizes cookies from Dio cookie jar to WebView storage.
  ///
  /// This method should be called after HTTP-based login to ensure cookies
  /// are available for WebView as well.
  ///
  /// The [cookieJar] parameter is the CookieJar instance to load cookies from.
  static Future<void> syncCookiesToWebView([CookieJar? cookieJar]) async {
    final operationId =
        'cookie_sync_dio_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      await logger.log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'Cookie sync to WebView started',
        operationId: operationId,
        context: 'cookie_sync',
      );

      final jar = cookieJar ?? CookieJar();
      final db = AppDatabase().database;
      final endpointManager = EndpointManager(db);
      final activeBase = await endpointManager.getActiveEndpoint();
      final uri = Uri.parse(activeBase);

      // Load cookies for the active endpoint
      final cookies = await jar.loadForRequest(uri);

      await logger.log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'Loaded cookies from Dio cookie jar',
        operationId: operationId,
        context: 'cookie_sync',
        extra: {
          'cookie_count': cookies.length,
          'endpoint': activeBase,
          'cookies': cookies
              .map((c) => {
                    'name': c.name,
                    'domain': c.domain ?? '<null>',
                    'path': c.path ?? '/',
                  })
              .toList(),
        },
      );

      if (cookies.isEmpty) {
        await logger.log(
          level: 'debug',
          subsystem: 'cookies',
          message: 'No cookies in Dio cookie jar to sync',
          operationId: operationId,
          context: 'cookie_sync',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
        );
        return;
      }

      // Convert Dio Cookie objects to JSON format compatible with WebView storage
      final cookieList = <Map<String, String>>[];
      for (final cookie in cookies) {
        cookieList.add({
          'name': cookie.name,
          'value': cookie.value,
          'domain': cookie.domain ?? uri.host,
          'path': cookie.path ?? '/',
        });
      }

      // Save to SharedPreferences in the same format as WebView
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString('rutracker_cookies_v1', jsonEncode(cookieList));

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'info',
        subsystem: 'cookies',
        message: 'Synced cookies from Dio to WebView storage',
        operationId: operationId,
        context: 'cookie_sync',
        durationMs: duration,
        extra: {
          'count': cookieList.length,
          'endpoint': activeBase,
          'cookies_synced': cookies
              .map((c) => {
                    'name': c.name,
                    'domain': c.domain ?? '<null>',
                    'path': c.path ?? '/',
                  })
              .toList(),
        },
      );
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'warning',
        subsystem: 'cookies',
        message: 'Failed to sync cookies to WebView',
        operationId: operationId,
        context: 'cookie_sync',
        durationMs: duration,
        cause: e.toString(),
        extra: {
          'stack_trace':
              (e is Error) ? (e as Error).stackTrace.toString() : null,
        },
      );
    }
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
    try {
      final prefs = await SharedPreferences.getInstance();
      final cookieJson = prefs.getString('rutracker_cookies_v1');

      if (cookieJson == null || cookieJson.isEmpty) {
        return false;
      }

      // Check if cookies are in valid JSON format
      try {
        final list = jsonDecode(cookieJson) as List<dynamic>;
        if (list.isEmpty) {
          return false;
        }

        // Check if we have at least one cookie with a value
        var hasValidCookie = false;
        for (final c in list) {
          final cookie = c as Map<String, dynamic>;
          final name = cookie['name'] as String?;
          final value = cookie['value'] as String?;

          if (name != null &&
              name.isNotEmpty &&
              value != null &&
              value.isNotEmpty) {
            hasValidCookie = true;
            break;
          }
        }

        return hasValidCookie;
      } on FormatException {
        return false;
      }
    } on Exception {
      return false;
    }
  }

  /// Validates cookies by making a test request to RuTracker.
  ///
  /// Returns true if cookies are valid and authentication is active, false otherwise.
  ///
  /// The [cookieJar] parameter is the CookieJar instance to use.
  /// The [dio] parameter is the Dio instance to use for the test request.
  /// The [bridgeSessionManager] parameter is the SessionManager instance to use.
  static Future<bool> validateCookies(
    Dio dio, [
    CookieJar? cookieJar,
    bridge_session.SessionManager? bridgeSessionManager,
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
        final db = AppDatabase().database;
        final endpointManager = EndpointManager(db);
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
                      'is_session_cookie': c.name.toLowerCase().contains('session') ||
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
            level: 'warning',
            subsystem: 'cookies',
            message: 'No cookies in Dio jar, syncing from WebView',
            operationId: operationId,
            context: 'cookie_validation',
            extra: {
              'endpoint': activeBase,
              'uri_host': uri.host,
            },
          );
          await syncCookiesFromWebView(jar);
          
          // Check again after sync
          final dioCookiesAfterSync = await jar.loadForRequest(uri);
          await logger.log(
            level: 'info',
            subsystem: 'cookies',
            message: 'Cookies in Dio jar after sync from WebView',
            operationId: operationId,
            context: 'cookie_validation',
            extra: {
              'cookie_count': dioCookiesAfterSync.length,
              'cookies': dioCookiesAfterSync.map((c) => {
                'name': c.name,
                'domain': c.domain ?? '<null>',
                'path': c.path ?? '/',
                'has_value': c.value.isNotEmpty,
                'value_length': c.value.length,
                'value_preview': c.value.isNotEmpty 
                    ? '${c.value.substring(0, c.value.length > 20 ? 20 : c.value.length)}...' 
                    : '<empty>',
                'is_session_cookie': c.name.toLowerCase().contains('session') ||
                    c.name == 'bb_session' ||
                    c.name == 'bb_data',
              }).toList(),
            },
          );
          
          if (dioCookiesAfterSync.isEmpty) {
            await logger.log(
              level: 'error',
              subsystem: 'cookies',
              message: 'No cookies in Dio jar after sync from WebView - validation will fail',
              operationId: operationId,
              context: 'cookie_validation',
              extra: {
                'endpoint': activeBase,
                'uri_host': uri.host,
                'note': 'Cookies may be in SessionManager but not in CookieJar. Trying to sync from SessionManager.',
              },
            );
            
            // CRITICAL: Try to sync from SessionManager as last resort
            try {
              if (bridgeSessionManager != null) {
                final sessionCookies = await bridgeSessionManager.getSessionCookies();
                if (sessionCookies.isNotEmpty) {
                  await logger.log(
                    level: 'info',
                    subsystem: 'cookies',
                    message: 'Found cookies in SessionManager, syncing to CookieJar',
                    operationId: operationId,
                    context: 'cookie_validation',
                    extra: {
                      'session_cookie_count': sessionCookies.length,
                    },
                  );
                  
                  // Parse session cookies and save to CookieJar
                  final parsedCookies = <io.Cookie>[];
                  for (final cookieString in sessionCookies) {
                    if (cookieString.contains('=')) {
                      final parts = cookieString.split('=');
                      if (parts.length >= 2) {
                        final name = parts[0].trim();
                        final value = parts.sublist(1).join('=').trim();
                        if (name.isNotEmpty && value.isNotEmpty) {
                          final cookie = io.Cookie(name, value)
                            ..path = '/'
                            ..secure = true;
                          parsedCookies.add(cookie);
                        }
                      }
                    }
                  }
                  
                  if (parsedCookies.isNotEmpty) {
                    await jar.saveFromResponse(uri, parsedCookies);
                    
                    // Check again after SessionManager sync
                    final dioCookiesAfterSessionSync = await jar.loadForRequest(uri);
                    if (dioCookiesAfterSessionSync.isNotEmpty) {
                      await logger.log(
                        level: 'info',
                        subsystem: 'cookies',
                        message: 'Cookies synced from SessionManager to CookieJar successfully',
                        operationId: operationId,
                        context: 'cookie_validation',
                        extra: {
                          'cookie_count': dioCookiesAfterSessionSync.length,
                        },
                      );
                    } else {
                      await logger.log(
                        level: 'error',
                        subsystem: 'cookies',
                        message: 'Cookies still not found in CookieJar after SessionManager sync',
                        operationId: operationId,
                        context: 'cookie_validation',
                      );
                      return false;
                    }
                  } else {
                    return false;
                  }
                } else {
                  return false;
                }
              } else {
                return false;
              }
            } on Exception catch (e) {
              await logger.log(
                level: 'error',
                subsystem: 'cookies',
                message: 'Failed to sync from SessionManager',
                operationId: operationId,
                context: 'cookie_validation',
                cause: e.toString(),
              );
              return false;
            }
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
      final db = AppDatabase().database;
      final endpointManager = EndpointManager(db);
      final activeBase = await endpointManager.getActiveEndpoint();
      final testUrl = '$activeBase/index.php';

      await logger.log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'Making test request to validate cookies',
        operationId: operationId,
        context: 'cookie_validation',
        extra: {
          'test_url': testUrl,
          'endpoint': activeBase,
        },
      );

      try {
        // Try to access a protected page (user profile or settings would require auth)
        // Using index.php as a lightweight test
        // Use normal request flow - SessionInterceptor now allows requests with cookies
        final testRequestStartTime = DateTime.now();
        final response = await dio
            .get(
              testUrl,
              options: Options(
                validateStatus: (status) => status != null && status < 500,
                followRedirects: false,
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

