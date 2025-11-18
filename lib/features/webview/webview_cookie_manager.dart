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

import 'package:flutter_cookie_bridge/session_manager.dart' as bridge_session;
import 'package:flutter_inappwebview/flutter_inappwebview.dart';

import 'package:jabook/core/endpoints/endpoint_manager.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Manages cookie synchronization between WebView and Dio HTTP client.
///
/// This class handles:
/// - Initializing FlutterCookieBridge SessionManager
/// - Saving cookies from WebView to SessionManager and Dio CookieJar
/// - Restoring cookies from SessionManager to WebView
/// - Real-time cookie monitoring via JavaScript handlers
class WebViewCookieManager {
  /// Creates a new WebViewCookieManager instance.
  WebViewCookieManager({
    required this.webViewController,
    required this.sessionManager,
    required this.isMounted,
  });

  /// The WebView controller to manage cookies for.
  final InAppWebViewController webViewController;

  /// The session manager for cookie synchronization.
  final bridge_session.SessionManager? sessionManager;

  /// Function to check if the widget is still mounted.
  final bool Function() isMounted;

  /// Initializes FlutterCookieBridge SessionManager for automatic cookie synchronization.
  static Future<bridge_session.SessionManager?> initCookieBridge() async {
    final operationId = 'cookie_bridge_init_${DateTime.now().millisecondsSinceEpoch}';
    final startTime = DateTime.now();
    
    await StructuredLogger().log(
      level: 'info',
      subsystem: 'cookies',
      message: 'Starting FlutterCookieBridge SessionManager initialization',
      operationId: operationId,
      context: 'cookie_bridge_init',
    );
    
    try {
      // Initialize SessionManager (singleton, same instance as in DioClient)
      final sessionManager = bridge_session.SessionManager();
      await StructuredLogger().log(
        level: 'info',
        subsystem: 'cookies',
        message: 'SessionManager instance created',
        operationId: operationId,
        context: 'cookie_bridge_init',
        extra: {},
      );
      
      // Ensure DioClient is initialized (which initializes FlutterCookieBridge)
      await StructuredLogger().log(
        level: 'info',
        subsystem: 'cookies',
        message: 'Initializing DioClient instance',
        operationId: operationId,
        context: 'cookie_bridge_init',
      );
      
      await DioClient.instance;
      
      // Verify SessionManager is still available after DioClient init
      final testCookies = await sessionManager.getSessionCookies();
      await StructuredLogger().log(
        level: 'info',
        subsystem: 'cookies',
        message: 'FlutterCookieBridge SessionManager initialized successfully',
        operationId: operationId,
        context: 'cookie_bridge_init',
        durationMs: DateTime.now().difference(startTime).inMilliseconds,
        extra: {
          'existing_cookies_count': testCookies.length,
          'existing_cookies': testCookies,
        },
      );
      
      return sessionManager;
    } on Exception catch (e) {
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'cookies',
        message: 'Failed to initialize FlutterCookieBridge SessionManager',
        operationId: operationId,
        context: 'cookie_bridge_init',
        durationMs: DateTime.now().difference(startTime).inMilliseconds,
        cause: e.toString(),
        extra: {
          'stack_trace': (e is Error) ? (e as Error).stackTrace.toString() : null,
        },
      );
      return null;
    }
  }

  /// Saves cookies from WebView to SessionManager and Dio CookieJar.
  Future<void> saveCookies({
    String? callerContext,
    String? initialUrl,
  }) async {
    // CRITICAL: Check if WebView is still mounted BEFORE attempting to get cookies
    if (!isMounted()) {
      final stackTrace = StackTrace.current;
      final stackLines = stackTrace.toString().split('\n');
      final callerInfo = stackLines.length > 2 
          ? stackLines[2].trim() 
          : 'unknown';
      
      await StructuredLogger().log(
        level: 'warning',
        subsystem: 'cookies',
        message: 'Cannot save cookies - WebView is not mounted',
        context: 'webview_cookie_save',
        extra: {
          'note': 'WebView may have been closed. Cookies should have been saved before closing.',
          'caller_context': callerContext ?? 'not_provided',
          'caller_stack': callerInfo,
        },
      );
      return;
    }
    
    final operationId = 'webview_cookie_save_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    await logger.log(
      level: 'info',
      subsystem: 'cookies',
      message: 'Starting cookie save from WebView',
      operationId: operationId,
      context: 'webview_cookie_save',
      extra: {
        'caller_context': callerContext ?? 'not_provided',
        'is_mounted': isMounted(),
      },
    );

    try {
      // Get current URL from WebView if available, otherwise use initialUrl
      String? currentUrl;
      try {
        final url = await webViewController.getUrl();
        currentUrl = url?.toString();
      } on Exception {
        // Ignore errors getting current URL
      }

      final urlToUse = currentUrl ?? initialUrl;
      if (urlToUse == null) {
        await logger.log(
          level: 'debug',
          subsystem: 'cookies',
          message: 'Cannot save cookies: no URL available',
          operationId: operationId,
          context: 'webview_cookie_save',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
        );
        return;
      }

      // Get cookies from CookieManager and JavaScript
      final cookies = <Cookie>[];
      final jsCookieStrings = <String>[];
      List<Cookie>? cookieManagerCookies;
      String? jsCookiesString;

      // PRIMARY METHOD: Get cookies via CookieManager (includes HttpOnly cookies)
      try {
        await logger.log(
          level: 'info',
          subsystem: 'cookies',
          message: 'Getting cookies via CookieManager',
          operationId: operationId,
          context: 'webview_cookie_save',
          extra: {
            'current_url': urlToUse,
            'note': 'CookieManager can access ALL cookies including HttpOnly ones',
          },
        );
          
        final rutrackerDomains = EndpointManager.getRutrackerDomains();
        final allCookies = <Cookie>[];
        
        // Get current URL from WebView controller
        String? actualWebViewUrl;
        try {
          final webViewUrl = await webViewController.getUrl();
          actualWebViewUrl = webViewUrl?.toString();
        } on Exception {
          // Use fallback URL
        }
        
        final urlForCookies = actualWebViewUrl ?? urlToUse;
          
        // Get cookies from current URL
        try {
          final currentUri = WebUri(urlForCookies);
          final currentCookies = await CookieManager.instance().getCookies(
            url: currentUri,
            webViewController: webViewController,
          );
          for (final cookie in currentCookies) {
            if (!allCookies.any((c) => c.name == cookie.name && c.domain == cookie.domain)) {
              allCookies.add(cookie);
            }
          }
        } on Exception {
          // Ignore errors
        }
        
        // Get cookies from all RuTracker domains
        for (final domain in rutrackerDomains) {
          try {
            final domainUri = WebUri('https://$domain');
            final domainCookies = await CookieManager.instance().getCookies(
              url: domainUri,
              webViewController: webViewController,
            );
            for (final cookie in domainCookies) {
              if (!allCookies.any((c) => c.name == cookie.name && c.domain == cookie.domain)) {
                allCookies.add(cookie);
              }
            }
          } on Exception {
            // Ignore errors for individual domains
          }
        }
        
        cookieManagerCookies = allCookies;
        
        if (cookieManagerCookies.isNotEmpty) {
          await logger.log(
            level: 'info',
            subsystem: 'cookies',
            message: 'Successfully got cookies via CookieManager',
            operationId: operationId,
            context: 'webview_cookie_save',
            extra: {
              'total_cookie_count': cookieManagerCookies.length,
              'cookie_names': cookieManagerCookies.map((c) => c.name).toList(),
              'has_http_only_cookies': cookieManagerCookies.any((c) => c.isHttpOnly ?? false),
            },
          );
        } else {
          await logger.log(
            level: 'warning',
            subsystem: 'cookies',
            message: 'CookieManager returned no cookies',
            operationId: operationId,
            context: 'webview_cookie_save',
            extra: {
              'current_url': urlToUse,
              'domains_checked': rutrackerDomains,
            },
          );
        }
      } on Exception catch (e) {
        await logger.log(
          level: 'error',
          subsystem: 'cookies',
          message: 'Failed to get cookies via CookieManager',
          operationId: operationId,
          context: 'webview_cookie_save',
          cause: e.toString(),
        );
      }
      
      // FALLBACK METHOD: Try JavaScript if CookieManager failed or returned no cookies
      if (cookieManagerCookies == null || cookieManagerCookies.isEmpty) {
        await logger.log(
          level: 'info',
          subsystem: 'cookies',
          message: 'Trying JavaScript as fallback (CookieManager failed or returned no cookies)',
          operationId: operationId,
          context: 'webview_cookie_save',
          extra: {
            'note': 'JavaScript can only access non-HttpOnly cookies',
          },
        );
        
        try {
          if (!isMounted()) {
            await logger.log(
              level: 'warning',
              subsystem: 'cookies',
              message: 'WebView widget is not mounted, skipping JavaScript cookie extraction',
              operationId: operationId,
              context: 'webview_cookie_save',
            );
          } else {
            final jsCookiesResult = await webViewController.evaluateJavascript(source: 'document.cookie');
            
            await logger.log(
              level: 'debug',
              subsystem: 'cookies',
              message: 'JavaScript cookie evaluation result',
              operationId: operationId,
              context: 'webview_cookie_save',
              extra: {
                'result_type': jsCookiesResult.runtimeType.toString(),
                'result_is_null': jsCookiesResult == null,
                'result_string': jsCookiesResult?.toString() ?? 'null',
              },
            );
            
            if (jsCookiesResult != null) {
              jsCookiesString = jsCookiesResult.toString();
              
              await logger.log(
                level: 'info',
                subsystem: 'cookies',
                message: 'Got cookies string from JavaScript',
                operationId: operationId,
                context: 'webview_cookie_save',
                extra: {
                  'cookie_string_length': jsCookiesString.length,
                  'cookie_string_preview': jsCookiesString.length > 100 
                      ? '${jsCookiesString.substring(0, 100)}...' 
                      : jsCookiesString,
                  'is_empty': jsCookiesString.isEmpty,
                },
              );
            }
          }
        } on Exception catch (e) {
          await logger.log(
            level: 'warning',
            subsystem: 'cookies',
            message: 'Exception getting cookies via JavaScript',
            operationId: operationId,
            context: 'webview_cookie_save',
            cause: e.toString(),
          );
        }
      }
      
      // Process cookies: Use CookieManager cookies as PRIMARY, JavaScript as supplement
      if (cookieManagerCookies != null && cookieManagerCookies.isNotEmpty) {
        cookies.addAll(cookieManagerCookies);
        
        // Convert CookieManager cookies to strings for SessionManager
        for (final cookie in cookieManagerCookies) {
          jsCookieStrings.add('${cookie.name}=${cookie.value}');
        }
        
        await logger.log(
          level: 'info',
          subsystem: 'cookies',
          message: 'Using cookies from CookieManager (PRIMARY METHOD)',
          operationId: operationId,
          context: 'webview_cookie_save',
          extra: {
            'cookie_count': cookieManagerCookies.length,
            'cookie_names': cookieManagerCookies.map((c) => c.name).toList(),
            'has_http_only': cookieManagerCookies.any((c) => c.isHttpOnly ?? false),
          },
        );
        
        // Save CookieManager cookies to SessionManager
        if (sessionManager != null && jsCookieStrings.isNotEmpty) {
          await logger.log(
            level: 'info',
            subsystem: 'cookies',
            message: 'About to save CookieManager cookies to FlutterCookieBridge SessionManager',
            operationId: operationId,
            context: 'webview_cookie_save',
            extra: {
              'cookie_count': jsCookieStrings.length,
              'cookie_names': jsCookieStrings
                  .map((s) {
                    final parts = s.split('=');
                    return parts.isNotEmpty ? parts[0].trim() : '';
                  })
                  .where((name) => name.isNotEmpty)
                  .toList(),
              'caller_context': callerContext ?? 'not_provided',
            },
          );
          
          await sessionManager!.saveSessionCookies(jsCookieStrings);
          
          await logger.log(
            level: 'info',
            subsystem: 'cookies',
            message: 'CookieManager cookies saved to FlutterCookieBridge SessionManager',
            operationId: operationId,
            context: 'webview_cookie_save',
            extra: {
              'cookie_count': jsCookieStrings.length,
              'saved_to_session_manager': true,
            },
          );
        }
      } else if (jsCookiesString != null && jsCookiesString.isNotEmpty) {
        // Fallback to JavaScript cookies (non-HttpOnly only)
        await logger.log(
          level: 'info',
          subsystem: 'cookies',
          message: 'Found cookies via JavaScript',
          operationId: operationId,
          context: 'webview_cookie_save',
          extra: {
            'js_cookies_preview': jsCookiesString.length > 200 
                ? '${jsCookiesString.substring(0, 200)}...' 
                : jsCookiesString,
            'js_cookies_length': jsCookiesString.length,
          },
        );
        
        // Parse JavaScript cookies
        final rawCookieStrings = jsCookiesString.split(RegExp(r';\s*'));
        for (final rawCookie in rawCookieStrings) {
          final trimmed = rawCookie.trim();
          if (trimmed.isNotEmpty && trimmed.contains('=')) {
            jsCookieStrings.add(trimmed);
          }
        }
        
        if (jsCookieStrings.isNotEmpty) {
          // Save JavaScript cookies directly to SessionManager
          if (sessionManager != null) {
            await sessionManager!.saveSessionCookies(jsCookieStrings);
            
            await logger.log(
              level: 'info',
              subsystem: 'cookies',
              message: 'JavaScript cookies saved to FlutterCookieBridge SessionManager',
              operationId: operationId,
              context: 'webview_cookie_save',
              extra: {
                'cookie_count': jsCookieStrings.length,
                'saved_to_session_manager': true,
              },
            );
          }
          
          // Convert to WebView Cookie format for backward compatibility
          try {
            final currentUri = Uri.parse(urlToUse);
            for (final cookieString in jsCookieStrings) {
              final parts = cookieString.split('=');
              if (parts.length >= 2) {
                final name = parts[0].trim();
                final value = parts.sublist(1).join('=').trim();
                if (name.isNotEmpty && value.isNotEmpty) {
                  final cookie = Cookie(
                    name: name,
                    value: value,
                    domain: currentUri.host,
                    path: '/',
                    isSecure: currentUri.scheme == 'https',
                    isHttpOnly: false,
                  );
                  cookies.add(cookie);
                }
              }
            }
          } on Exception catch (convertError) {
            await logger.log(
              level: 'warning',
              subsystem: 'cookies',
              message: 'Failed to convert JavaScript cookies to WebView format',
              operationId: operationId,
              context: 'webview_cookie_save',
              cause: convertError.toString(),
            );
          }
        }
      }

      // Save to SharedPreferences for backward compatibility
      final cookieJson = jsonEncode(cookies
          .map((c) => {
                'name': c.name,
                'value': c.value,
                'domain': c.domain,
                'path': c.path,
              })
          .toList());

      final prefs = await SharedPreferences.getInstance();
      await prefs.setString('rutracker_cookies_v1', cookieJson);

      // CRITICAL: Ensure cookies are saved to SessionManager
      if (jsCookieStrings.isNotEmpty && sessionManager != null) {
        await sessionManager!.saveSessionCookies(jsCookieStrings);
      }
      
      // Sync to Dio CookieJar
      if (cookies.isNotEmpty) {
        try {
          await _syncCookiesDirectlyToDio(cookies, operationId);
          await logger.log(
            level: 'info',
            subsystem: 'cookies',
            message: 'Cookies synced directly to Dio CookieJar',
            operationId: operationId,
            context: 'webview_cookie_save',
          );
        } on Exception catch (syncError) {
          await logger.log(
            level: 'warning',
            subsystem: 'cookies',
            message: 'Failed to sync cookies directly to Dio',
            operationId: operationId,
            context: 'webview_cookie_save',
            cause: syncError.toString(),
          );
        }
      }

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'info',
        subsystem: 'cookies',
        message: 'Cookies saved from WebView',
        operationId: operationId,
        context: 'webview_cookie_save',
        durationMs: duration,
        extra: {
          'cookie_count': cookies.length,
        },
      );
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'warning',
        subsystem: 'cookies',
        message: 'Failed to save cookies from WebView',
        operationId: operationId,
        context: 'webview_cookie_save',
        durationMs: duration,
        cause: e.toString(),
      );
    }
  }

  /// Directly syncs cookies from WebView CookieManager to Dio CookieJar.
  Future<void> _syncCookiesDirectlyToDio(
      List<Cookie> webViewCookies, String operationId) async {
    try {
      final logger = StructuredLogger();
      final rutrackerDomains = EndpointManager.getRutrackerDomains();

      if (webViewCookies.isEmpty) {
        await logger.log(
          level: 'error',
          subsystem: 'cookies',
          message: 'CRITICAL: No cookies to sync from WebView to Dio!',
          operationId: operationId,
          context: 'cookie_sync_direct',
        );
        return;
      }

      // Convert WebView cookies to Dio Cookie format
      // CRITICAL: Don't set domain - let CookieJar use URI host automatically
      // This is more reliable than trying to normalize domains manually
      final dioCookies = <io.Cookie>[];
      for (final webViewCookie in webViewCookies) {
        try {
          final dioCookie = io.Cookie(webViewCookie.name, webViewCookie.value)
            ..path = webViewCookie.path ?? '/'
            ..secure = webViewCookie.isSecure ?? true
            ..httpOnly = webViewCookie.isHttpOnly ?? false;
          // Don't set domain - let CookieJar use URI host automatically
          // This ensures cookies are saved correctly regardless of domain format
          dioCookies.add(dioCookie);
        } on Exception catch (e) {
          await logger.log(
            level: 'warning',
            subsystem: 'cookies',
            message: 'Failed to convert WebView cookie to Dio format',
            operationId: operationId,
            context: 'cookie_sync_direct',
            cause: e.toString(),
            extra: {
              'cookie_name': webViewCookie.name,
            },
          );
        }
      }

      if (dioCookies.isEmpty) {
        await logger.log(
          level: 'error',
          subsystem: 'cookies',
          message: 'CRITICAL: No cookies to sync after conversion!',
          operationId: operationId,
          context: 'cookie_sync_direct',
        );
        return;
      }

      // Save cookies for RuTracker domains directly to Dio CookieJar
      // CRITICAL: Save ALL cookies to ALL domains to ensure they work across mirrors
      for (final domain in rutrackerDomains) {
        try {
          final domainUri = Uri.parse('https://$domain');
          
          // Save ALL cookies to each domain (not filtered) - CookieJar will handle domain matching
          // This ensures cookies work when switching between rutracker mirrors
          if (dioCookies.isNotEmpty) {
            await logger.log(
              level: 'debug',
              subsystem: 'cookies',
              message: 'Saving cookies to domain via DioClient.saveCookiesDirectly',
              operationId: operationId,
              context: 'cookie_sync_direct',
              extra: {
                'domain': domain,
                'cookie_count': dioCookies.length,
                'cookie_names': dioCookies.map((c) => c.name).toList(),
              },
            );
            
            await DioClient.saveCookiesDirectly(domainUri, dioCookies);
            
            // CRITICAL: Wait a bit and verify cookies were saved
            await Future.delayed(const Duration(milliseconds: 200));
            
            // Verify cookies were saved by loading them back
            final cookieJar = await DioClient.getCookieJar();
            final savedCookies = cookieJar != null 
                ? await cookieJar.loadForRequest(domainUri)
                : <io.Cookie>[];
            
            await logger.log(
              level: savedCookies.isNotEmpty ? 'info' : 'warning',
              subsystem: 'cookies',
              message: savedCookies.isNotEmpty 
                  ? 'Cookies verified in Dio CookieJar after save'
                  : 'WARNING: Cookies not found in Dio CookieJar after save',
              operationId: operationId,
              context: 'cookie_sync_direct',
              extra: {
                'domain': domain,
                'saved_count': savedCookies.length,
                'expected_count': dioCookies.length,
                'saved_cookie_names': savedCookies.map((c) => c.name).toList(),
                'expected_cookie_names': dioCookies.map((c) => c.name).toList(),
              },
            );
          }
        } on Exception catch (e) {
          await logger.log(
            level: 'error',
            subsystem: 'cookies',
            message: 'Failed to save cookies for domain',
            operationId: operationId,
            context: 'cookie_sync_direct',
            cause: e.toString(),
            extra: {'domain': domain},
          );
        }
      }
    } on Exception catch (e) {
      await StructuredLogger().log(
        level: 'warning',
        subsystem: 'cookies',
        message: 'Failed to sync cookies directly to Dio',
        operationId: operationId,
        context: 'cookie_sync_direct',
        cause: e.toString(),
      );
    }
  }

  /// Restores cookies from SessionManager to WebView.
  Future<void> restoreCookies(String? initialUrl) async {
    final operationId = 'webview_cookie_restore_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      await logger.log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'Cookie restore to WebView started',
        operationId: operationId,
        context: 'webview_cookie_restore',
        extra: {
          'initial_url': initialUrl,
        },
      );

      final prefs = await SharedPreferences.getInstance();
      final cookieJson = prefs.getString('rutracker_cookies_v1');
      if (cookieJson != null) {
        try {
          final cookies = jsonDecode(cookieJson) as List<dynamic>;

          var loadedCount = 0;
          if (initialUrl != null) {
            for (final cookie in cookies) {
              try {
                await CookieManager.instance().setCookie(
                  url: WebUri(initialUrl),
                  name: cookie['name'],
                  value: cookie['value'],
                  domain: cookie['domain'],
                  path: cookie['path'],
                );
                loadedCount++;
              } on Exception {
                // Ignore individual cookie errors
              }
            }
          }

          final duration = DateTime.now().difference(startTime).inMilliseconds;
          await logger.log(
            level: 'info',
            subsystem: 'cookies',
            message: 'Cookies restored to WebView',
            operationId: operationId,
            context: 'webview_cookie_restore',
            durationMs: duration,
            extra: {
              'total_cookies': cookies.length,
              'loaded_count': loadedCount,
            },
          );
        } on Exception catch (e) {
          await logger.log(
            level: 'warning',
            subsystem: 'cookies',
            message: 'Failed to restore cookies',
            operationId: operationId,
            context: 'webview_cookie_restore',
            durationMs: DateTime.now().difference(startTime).inMilliseconds,
            cause: e.toString(),
          );
        }
      }
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'warning',
        subsystem: 'cookies',
        message: 'Exception during cookie restore',
        operationId: operationId,
        context: 'webview_cookie_restore',
        durationMs: duration,
        cause: e.toString(),
      );
    }
  }

  /// Sets up JavaScript handler for real-time cookie monitoring.
  Future<void> setupJavaScriptCookieHandler() async {
    await StructuredLogger().log(
      level: 'info',
      subsystem: 'cookies',
      message: 'Setting up JavaScript cookie handler for real-time monitoring',
      context: 'webview_js_handler_setup',
    );
    
    try {
      webViewController.addJavaScriptHandler(
        handlerName: 'onCookieChanged',
        callback: (args) async {
          final handlerOperationId = 'js_handler_${DateTime.now().millisecondsSinceEpoch}';
          
          await StructuredLogger().log(
            level: 'info',
            subsystem: 'cookies',
            message: 'JavaScript cookie handler triggered',
            operationId: handlerOperationId,
            context: 'webview_js_handler',
            extra: {
              'args_count': args.length,
              'session_manager_is_null': sessionManager == null,
            },
          );
          
          try {
            if (args.isNotEmpty && args[0] is String) {
              final cookieString = args[0] as String;
              
              if (cookieString.isNotEmpty && sessionManager != null) {
                final cookieStrings = cookieString
                    .split(RegExp(r';\s*'))
                    .where((s) => s.trim().isNotEmpty && s.contains('='))
                    .map((s) => s.trim())
                    .toList();
                
                if (cookieStrings.isNotEmpty) {
                  await sessionManager!.saveSessionCookies(cookieStrings);
                  
                  await StructuredLogger().log(
                    level: 'info',
                    subsystem: 'cookies',
                    message: 'Cookies saved via JavaScript handler',
                    operationId: handlerOperationId,
                    context: 'webview_js_handler',
                    extra: {
                      'cookie_count': cookieStrings.length,
                    },
                  );
                }
              }
            }
          } on Exception catch (e) {
            await StructuredLogger().log(
              level: 'error',
              subsystem: 'cookies',
              message: 'Failed to save cookies from JavaScript handler',
              operationId: handlerOperationId,
              context: 'webview_js_handler',
              cause: e.toString(),
            );
          }
        },
      );
      
      await StructuredLogger().log(
        level: 'info',
        subsystem: 'cookies',
        message: 'JavaScript cookie handler registered successfully',
        context: 'webview_js_handler_setup',
      );
    } on Exception catch (e) {
      await StructuredLogger().log(
        level: 'warning',
        subsystem: 'cookies',
        message: 'Failed to setup JavaScript cookie handler',
        context: 'webview_js_handler_setup',
        cause: e.toString(),
      );
    }
  }

  /// Injects JavaScript code to monitor cookie changes.
  Future<void> injectCookieMonitorScript() async {
    const jsCode = '''
      (function() {
        if (window.__cookieMonitorInstalled) return;
        window.__cookieMonitorInstalled = true;
        
        var lastCookies = document.cookie || '';
        
        function checkCookies() {
          var currentCookies = document.cookie || '';
          if (currentCookies !== lastCookies && currentCookies) {
            lastCookies = currentCookies;
            try {
              window.flutter_inappwebview.callHandler('onCookieChanged', currentCookies);
            } catch(e) {
              console.log('Cookie handler error: ' + e);
            }
          }
        }
        
        setInterval(checkCookies, 500);
        
        if (document.readyState === 'complete') {
          setTimeout(checkCookies, 1000);
        } else {
          document.addEventListener('DOMContentLoaded', function() {
            setTimeout(checkCookies, 1000);
          });
          window.addEventListener('load', function() {
            setTimeout(checkCookies, 1000);
          });
        }
        
        setTimeout(checkCookies, 2000);
      })();
    ''';
    
    unawaited(Future.delayed(const Duration(milliseconds: 2000), () async {
      final injectionOperationId = 'js_injection_${DateTime.now().millisecondsSinceEpoch}';
      final injectionStartTime = DateTime.now();
      
      await StructuredLogger().log(
        level: 'info',
        subsystem: 'cookies',
        message: 'Starting JavaScript cookie monitor injection',
        operationId: injectionOperationId,
        context: 'webview_js_injection',
      );
      
      try {
        final result = await webViewController.evaluateJavascript(source: jsCode);
        
        await StructuredLogger().log(
          level: 'info',
          subsystem: 'cookies',
          message: 'JavaScript cookie monitor injected successfully',
          operationId: injectionOperationId,
          context: 'webview_js_injection',
          durationMs: DateTime.now().difference(injectionStartTime).inMilliseconds,
          extra: {
            'evaluation_result': result?.toString() ?? 'null',
          },
        );
      } on Exception catch (e) {
        await StructuredLogger().log(
          level: 'error',
          subsystem: 'cookies',
          message: 'Failed to inject JavaScript cookie monitor',
          operationId: injectionOperationId,
          context: 'webview_js_injection',
          durationMs: DateTime.now().difference(injectionStartTime).inMilliseconds,
          cause: e.toString(),
        );
      }
    }));
  }
}

