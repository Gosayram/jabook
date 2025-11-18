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

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_cookie_bridge/session_manager.dart' as bridge_session;
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import 'package:jabook/core/endpoints/endpoint_manager.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/net/user_agent_manager.dart';
import 'package:jabook/data/db/app_database.dart';
import 'package:jabook/l10n/app_localizations.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:url_launcher/url_launcher.dart';

/// A secure WebView widget for accessing RuTracker with Cloudflare protection handling.
///
/// This widget provides a WebView interface for RuTracker with built-in
/// Cloudflare protection detection and handling, cookie management,
/// and external link handling.
class SecureRutrackerWebView extends StatefulWidget {
  /// Creates a new SecureRutrackerWebView instance.
  const SecureRutrackerWebView({super.key});

  @override
  State<SecureRutrackerWebView> createState() => _SecureRutrackerWebViewState();
}

/// State class for SecureRutrackerWebView widget.
///
/// Manages the WebView controller, progress tracking, and Cloudflare detection.
class _SecureRutrackerWebViewState extends State<SecureRutrackerWebView>
    with WidgetsBindingObserver {
  late InAppWebViewController _webViewController;
  double progress = 0.0;
  final storage = const FlutterSecureStorage();
  String? initialUrl; // resolved dynamically from EndpointManager
  String? _userAgent; // User-Agent for legitimate connection

  // State restoration
  final String _webViewStateKey = 'rutracker_webview_state';

  // Error handling
  bool _hasError = false;
  String? _errorMessage;
  int _retryCount = 0;
  static const int _maxRetries = 2;

  // Cloudflare detection
  bool _isCloudflareDetected = false;
  Timer? _cloudflareCheckTimer;
  late DateTime? _cloudflareChallengeStartTime;
  static const Duration _cloudflareWaitDuration = Duration(seconds: 10);

  // Navigation tracking
  String? _currentOperationId;
  DateTime? _currentPageLoadStartTime;

  // Cookie synchronization
  Timer? _cookieSyncTimer;
  // Increased interval to avoid Cloudflare rate limiting
  // Cookies sync every 30 seconds instead of 5 to reduce request frequency
  static const Duration _cookieSyncInterval = Duration(seconds: 30);
  
  // FlutterCookieBridge SessionManager for automatic cookie sync
  bridge_session.SessionManager? _bridgeSessionManager;

  @override
  void initState() {
    super.initState();
    // Add lifecycle observer to track app state changes
    WidgetsBinding.instance.addObserver(this);
    // Initialize FlutterCookieBridge for automatic cookie sync
    _initCookieBridge();
    // Set fallback URL immediately to prevent null issues and ensure WebView can render
    initialUrl = EndpointManager.getPrimaryFallbackEndpoint();
    // Set default User-Agent immediately to prevent UI blocking (WebView won't render without it)
    _userAgent = 'Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.210 Mobile Safari/537.36';
    // Get actual User-Agent asynchronously (will update if successful, but WebView can start with default)
    _getUserAgent();
    // Resolve initial URL with timeout to prevent hanging
    _resolveInitialUrl()
        .timeout(
          const Duration(seconds: 10),
          onTimeout: () {
            // If resolution times out, use fallback
            initialUrl = EndpointManager.getPrimaryFallbackEndpoint();
          },
        )
        .then((_) {
      _restoreCookies();
      if (mounted) {
        setState(() {});
      }
    }).catchError((e) {
      // If URL resolution fails, ensure we have a fallback
      initialUrl ??= EndpointManager.getPrimaryFallbackEndpoint();
      if (mounted) {
        setState(() {});
      }
    });
    _restoreWebViewHistory();
  }
  
  /// Initializes FlutterCookieBridge SessionManager for automatic cookie synchronization.
  Future<void> _initCookieBridge() async {
    try {
      // Initialize SessionManager (singleton, same instance as in DioClient)
      _bridgeSessionManager = bridge_session.SessionManager();
      // Ensure DioClient is initialized (which initializes FlutterCookieBridge)
      await DioClient.instance;
      await StructuredLogger().log(
        level: 'info',
        subsystem: 'webview',
        message: 'FlutterCookieBridge SessionManager initialized in WebView',
        context: 'cookie_bridge_init',
      );
    } on Exception catch (e) {
      await StructuredLogger().log(
        level: 'warning',
        subsystem: 'webview',
        message: 'Failed to initialize FlutterCookieBridge SessionManager',
        context: 'cookie_bridge_init',
        cause: e.toString(),
      );
    }
  }

  /// Gets User-Agent for legitimate connection to pass Cloudflare checks.
  Future<void> _getUserAgent() async {
    try {
      final userAgentManager = UserAgentManager();
      _userAgent = await userAgentManager.getUserAgent();
      if (mounted) {
        setState(() {});
      }
    } on Exception {
      // Use default User-Agent if extraction fails
      // Modern mobile browser User-Agent for Cloudflare compatibility
      _userAgent = 'Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.210 Mobile Safari/537.36';
      if (mounted) {
        setState(() {});
      }
    }
  }

  @override
  void dispose() {
    // Remove lifecycle observer
    WidgetsBinding.instance.removeObserver(this);
    // Cancel Cloudflare check timer
    _cloudflareCheckTimer?.cancel();
    // Cancel cookie sync timer
    _cookieSyncTimer?.cancel();
    // Save cookies one final time before disposing (only if still mounted)
    // Note: If WebView was closed via navigator.pop(), it may already be unmounted
    // In that case, cookies should have been saved before closing
    if (mounted) {
      unawaited(_saveCookies(callerContext: 'dispose'));
    }
    _saveWebViewHistory();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // Sync cookies when app resumes (e.g., returning from browser after Cloudflare challenge)
        if (state == AppLifecycleState.resumed) {
          Future.microtask(() async {
            try {
              // Only sync cookies if WebView is still mounted
              if (!mounted) {
                await StructuredLogger().log(
                  level: 'debug',
                  subsystem: 'cookies',
                  message: 'App resumed but WebView is not mounted, skipping cookie sync',
                  context: 'app_lifecycle',
                );
                return;
              }
              
              await StructuredLogger().log(
                level: 'debug',
                subsystem: 'cookies',
                message: 'App resumed, syncing cookies from WebView',
                context: 'app_lifecycle',
              );
          await _saveCookies(callerContext: 'app_lifecycle_resumed');
        await DioClient.syncCookiesFromWebView();
          await StructuredLogger().log(
            level: 'info',
            subsystem: 'cookies',
            message: 'Cookies synced after app resume',
            context: 'app_lifecycle',
          );
        } on Exception catch (e) {
          await StructuredLogger().log(
            level: 'warning',
            subsystem: 'cookies',
            message: 'Failed to sync cookies after app resume',
            context: 'app_lifecycle',
            cause: e.toString(),
          );
      }
    });
    }
  }

  Future<void> _resolveInitialUrl() async {
    final db = AppDatabase().database;
    final endpointManager = EndpointManager(db);

    // List of endpoints to try in order of preference (matching EndpointManager priorities)
    final fallbackEndpoints = EndpointManager.getDefaultEndpointUrls();

    try {
      // Get active endpoint with health check
      var endpoint = await endpointManager.getActiveEndpoint();

      // Pre-check endpoint availability before using it
      final isAvailable = await endpointManager.quickAvailabilityCheck(
        endpoint,
      );

      if (!isAvailable) {
        // Current endpoint not available, try to switch
        await StructuredLogger().log(
          level: 'warning',
          subsystem: 'webview',
          message: 'Active endpoint not available, switching',
          extra: {'failed_endpoint': endpoint},
        );

        // Try to switch endpoint
        final switched = await endpointManager.trySwitchEndpoint(endpoint);
        if (switched) {
          endpoint = await endpointManager.getActiveEndpoint();
          // Re-check availability of new endpoint
          final newIsAvailable = await endpointManager.quickAvailabilityCheck(
            endpoint,
          );
          if (!newIsAvailable) {
            // New endpoint also not available, try fallbacks
            endpoint = await _tryFallbackEndpoints(fallbackEndpoints);
          }
        } else {
          // Switch failed, try fallbacks
          endpoint = await _tryFallbackEndpoints(fallbackEndpoints);
        }
      } else {
        // Endpoint is available, but do a quick DNS check to ensure it resolves
        try {
          final uri = Uri.parse(endpoint);
          await io.InternetAddress.lookup(uri.host)
              .timeout(const Duration(seconds: 3));
        } on Exception catch (e) {
          // DNS lookup failed, try fallbacks
          await StructuredLogger().log(
            level: 'warning',
            subsystem: 'webview',
            message: 'DNS lookup failed for endpoint, trying fallbacks',
            extra: {'failed_endpoint': endpoint, 'error': e.toString()},
          );
          endpoint = await _tryFallbackEndpoints(fallbackEndpoints);
        }
      }

      // Final validation: ensure endpoint is accessible
      try {
        final dio = await DioClient.instance;
        await dio
            .get(
              '$endpoint/forum/index.php',
              options: Options(
                receiveTimeout: const Duration(seconds: 5),
                validateStatus: (status) => status != null && status < 500,
              ),
            )
            .timeout(const Duration(seconds: 5));

        initialUrl = endpoint;
      } on Exception catch (e) {
        // Even validation failed, try fallbacks
        await StructuredLogger().log(
          level: 'warning',
          subsystem: 'webview',
          message: 'Endpoint validation failed, trying fallbacks',
          extra: {'failed_endpoint': endpoint, 'error': e.toString()},
        );
        endpoint = await _tryFallbackEndpoints(fallbackEndpoints);
        initialUrl = endpoint;
      }
    } on Exception catch (e) {
      // If all fails, use hardcoded fallback
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'webview',
        message: 'All endpoint resolution failed, using hardcoded fallback',
        cause: e.toString(),
      );
      initialUrl = EndpointManager.getPrimaryFallbackEndpoint();
    }
  }

  /// Tries fallback endpoints in order until one works.
  Future<String> _tryFallbackEndpoints(List<String> endpoints) async {
    for (final fallback in endpoints) {
      try {
        // Quick DNS check
        final uri = Uri.parse(fallback);
        await io.InternetAddress.lookup(uri.host)
            .timeout(const Duration(seconds: 2));

        // Quick availability check
        final db = AppDatabase().database;
        final endpointManager = EndpointManager(db);
        final isAvailable = await endpointManager.quickAvailabilityCheck(
          fallback,
        );

        if (isAvailable) {
          await StructuredLogger().log(
            level: 'info',
            subsystem: 'webview',
            message: 'Using fallback endpoint',
            extra: {'endpoint': fallback},
          );
          return fallback;
        }
      } on Exception {
        // Try next fallback
        continue;
      }
    }

    // If all fallbacks failed, return the first one anyway
    // (WebView will show error, but at least it will try to load)
    await StructuredLogger().log(
      level: 'warning',
      subsystem: 'webview',
      message: 'All fallback endpoints failed, using first fallback',
      extra: {'endpoint': endpoints.first},
    );
    return endpoints.first;
  }

  Future<void> _restoreCookies() async {
    final operationId =
        'webview_cookie_restore_${DateTime.now().millisecondsSinceEpoch}';
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

          await logger.log(
            level: 'debug',
            subsystem: 'cookies',
            message: 'Loading cookies from SharedPreferences',
            operationId: operationId,
            context: 'webview_cookie_restore',
            extra: {
              'cookie_count': cookies.length,
              'cookies': cookies
                  .map((c) => {
                        'name': c['name'],
                        'domain': c['domain'] ?? '<null>',
                        'path': c['path'] ?? '/',
                      })
                  .toList(),
            },
          );

          var loadedCount = 0;
          final failedCookies = <Map<String, dynamic>>[];

          // Only restore cookies if initialUrl is set
          if (initialUrl != null) {
            for (final cookie in cookies) {
              try {
                await CookieManager.instance().setCookie(
                  url: WebUri(initialUrl!),
                  name: cookie['name'],
                  value: cookie['value'],
                  domain: cookie['domain'],
                  path: cookie['path'],
                  // Note: flutter_inappwebview doesn't support setting expires directly
                  // Cookies will expire based on their natural expiration
                );
                loadedCount++;
              } on Exception catch (e) {
                failedCookies.add({
                  'name': cookie['name'],
                  'error': e.toString(),
                });
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
              'failed_count': failedCookies.length,
              if (failedCookies.isNotEmpty) 'failed_cookies': failedCookies,
            },
          );
        } on Exception catch (e) {
          // If cookie restoration fails, clear old cookies and start fresh
          if (initialUrl != null) {
            await CookieManager.instance().deleteCookies(
              url: WebUri(initialUrl!),
              domain: Uri.parse(initialUrl!).host,
            );
          }

          await logger.log(
            level: 'warning',
            subsystem: 'cookies',
            message: 'Failed to restore cookies, cleared old cookies',
            operationId: operationId,
            context: 'webview_cookie_restore',
            durationMs: DateTime.now().difference(startTime).inMilliseconds,
            cause: e.toString(),
          );
        }
      } else {
        await logger.log(
          level: 'debug',
          subsystem: 'cookies',
          message: 'No cookies found in SharedPreferences to restore',
          operationId: operationId,
          context: 'webview_cookie_restore',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
        );
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

  Future<void> _saveCookies({String? callerContext}) async {
    // CRITICAL: Check if WebView is still mounted BEFORE attempting to get cookies
    // If WebView is closed, we cannot access it to get cookies
    if (!mounted) {
      // Get stack trace to see where this was called from
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
          'stack_trace_preview': stackLines.length > 5 
              ? stackLines.sublist(0, 5).join('\n') 
              : stackTrace.toString(),
        },
      );
      return;
    }
    
    final operationId =
        'webview_cookie_save_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    // Log that we're starting to save cookies
    await logger.log(
      level: 'info',
      subsystem: 'cookies',
      message: 'Starting cookie save from WebView',
      operationId: operationId,
      context: 'webview_cookie_save',
      extra: {
        'caller_context': callerContext ?? 'not_provided',
        'is_mounted': mounted,
      },
    );

    try {
      // Get current URL from WebView if available, otherwise use initialUrl
      String? currentUrl;
      try {
        final url = await _webViewController.getUrl();
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

      await logger.log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'Cookie save from WebView started',
        operationId: operationId,
        context: 'webview_cookie_save',
        extra: {
          'current_url': currentUrl,
          'initial_url': initialUrl,
          'url_used': urlToUse,
        },
      );

      // PRIMARY METHOD: Get cookies via CookieManager (includes HttpOnly cookies)
      // HttpOnly cookies (like bb_session) are NOT accessible via JavaScript
      // CookieManager can access ALL cookies including HttpOnly ones
      final cookies = <Cookie>[];
      final jsCookieStrings = <String>[];
      
      // PRIMARY METHOD: Get cookies via CookieManager (includes HttpOnly cookies)
      // HttpOnly cookies like bb_session are NOT accessible via JavaScript
      List<Cookie>? cookieManagerCookies;
      Exception? cookieManagerError;
      String? jsCookiesString;
      Exception? lastError;
      
      // Try CookieManager with retries (cookies may not be immediately available)
      for (var cookieManagerAttempt = 0; cookieManagerAttempt < 3; cookieManagerAttempt++) {
        try {
          // Wait before retry (except first attempt)
          if (cookieManagerAttempt > 0) {
            await logger.log(
              level: 'debug',
              subsystem: 'cookies',
              message: 'Retrying CookieManager (attempt ${cookieManagerAttempt + 1}/3)',
              operationId: operationId,
              context: 'webview_cookie_save',
              extra: {
                'attempt': cookieManagerAttempt + 1,
                'delay_ms': 500 * cookieManagerAttempt,
              },
            );
            await Future.delayed(Duration(milliseconds: 500 * cookieManagerAttempt));
            
            if (!mounted) {
              await logger.log(
                level: 'warning',
                subsystem: 'cookies',
                message: 'WebView unmounted during CookieManager retry',
                operationId: operationId,
                context: 'webview_cookie_save',
              );
              break;
            }
          }
          
          await logger.log(
            level: cookieManagerAttempt == 0 ? 'info' : 'debug',
            subsystem: 'cookies',
            message: 'Getting cookies via CookieManager (attempt ${cookieManagerAttempt + 1}/3)',
            operationId: operationId,
            context: 'webview_cookie_save',
            extra: {
              'current_url': urlToUse,
              'attempt': cookieManagerAttempt + 1,
              'note': 'CookieManager can access ALL cookies including HttpOnly ones',
            },
          );
          
          // Get cookies from CookieManager for current URL and all RuTracker domains
          final rutrackerDomains = EndpointManager.getRutrackerDomains();
          final allCookies = <Cookie>[];
          
          // Try current URL first
          try {
            final currentUri = WebUri(urlToUse);
            final currentCookies = await CookieManager.instance().getCookies(url: currentUri);
            allCookies.addAll(currentCookies);
            await logger.log(
              level: 'debug',
              subsystem: 'cookies',
              message: 'Got cookies from CookieManager for current URL',
              operationId: operationId,
              context: 'webview_cookie_save',
              extra: {
                'url': urlToUse,
                'cookie_count': currentCookies.length,
                'cookie_names': currentCookies.map((c) => c.name).toList(),
                'attempt': cookieManagerAttempt + 1,
              },
            );
          } on Exception catch (e) {
            await logger.log(
              level: 'warning',
              subsystem: 'cookies',
              message: 'Failed to get cookies from CookieManager for current URL',
              operationId: operationId,
              context: 'webview_cookie_save',
              cause: e.toString(),
              extra: {
                'attempt': cookieManagerAttempt + 1,
              },
            );
          }
          
          // Try all RuTracker domains to ensure we get all cookies
          for (final domain in rutrackerDomains) {
            try {
              final domainUri = WebUri('https://$domain');
              final domainCookies = await CookieManager.instance().getCookies(url: domainUri);
              
              // Add cookies that we don't already have
              for (final cookie in domainCookies) {
                if (!allCookies.any((c) => c.name == cookie.name && c.domain == cookie.domain)) {
                  allCookies.add(cookie);
                }
              }
              
              if (domainCookies.isNotEmpty) {
                await logger.log(
                  level: 'debug',
                  subsystem: 'cookies',
                  message: 'Got cookies from CookieManager for domain',
                  operationId: operationId,
                  context: 'webview_cookie_save',
                  extra: {
                    'domain': domain,
                    'cookie_count': domainCookies.length,
                    'cookie_names': domainCookies.map((c) => c.name).toList(),
                    'attempt': cookieManagerAttempt + 1,
                  },
                );
              }
            } on Exception catch (e) {
              // Ignore errors for individual domains
              await logger.log(
                level: 'debug',
                subsystem: 'cookies',
                message: 'Failed to get cookies from CookieManager for domain',
                operationId: operationId,
                context: 'webview_cookie_save',
                cause: e.toString(),
                extra: {
                  'domain': domain,
                  'attempt': cookieManagerAttempt + 1,
                },
              );
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
                'attempt': cookieManagerAttempt + 1,
              },
            );
            break; // Success, exit retry loop
          } else {
            await logger.log(
              level: 'warning',
              subsystem: 'cookies',
              message: 'CookieManager returned no cookies (attempt ${cookieManagerAttempt + 1}/3)',
              operationId: operationId,
              context: 'webview_cookie_save',
              extra: {
                'current_url': urlToUse,
                'domains_checked': rutrackerDomains,
                'attempt': cookieManagerAttempt + 1,
                'will_retry': cookieManagerAttempt < 2,
              },
            );
            
            // If this is the last attempt, don't retry
            if (cookieManagerAttempt >= 2) {
              break;
            }
          }
        } on Exception catch (e) {
          cookieManagerError = e;
          await logger.log(
            level: 'error',
            subsystem: 'cookies',
            message: 'Failed to get cookies via CookieManager (attempt ${cookieManagerAttempt + 1}/3)',
            operationId: operationId,
            context: 'webview_cookie_save',
            cause: e.toString(),
            extra: {
              'current_url': urlToUse,
              'attempt': cookieManagerAttempt + 1,
              'will_retry': cookieManagerAttempt < 2,
              'error_type': cookieManagerError.runtimeType.toString(),
            },
          );
          
          // If this is the last attempt, don't retry
          if (cookieManagerAttempt >= 2) {
            break;
          }
        }
      }
      
      // FALLBACK METHOD: Try JavaScript if CookieManager failed or returned no cookies
      // JavaScript can only access non-HttpOnly cookies
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
        
        for (var attempt = 0; attempt < 3; attempt++) {
          try {
            // Check if WebView is still available
            if (!mounted) {
              await logger.log(
                level: 'warning',
                subsystem: 'cookies',
                message: 'WebView widget is not mounted, skipping JavaScript cookie extraction',
                operationId: operationId,
                context: 'webview_cookie_save',
                extra: {
                  'attempt': attempt + 1,
                },
              );
              break;
            }
            
            // Wait before retry (except first attempt)
            if (attempt > 0) {
              await Future.delayed(Duration(milliseconds: 500 * attempt));
              if (!mounted) break;
            }
            
            final jsCookiesResult = await _webViewController.evaluateJavascript(source: 'document.cookie');
          
          await logger.log(
            level: 'debug',
            subsystem: 'cookies',
            message: 'evaluateJavascript completed',
            operationId: operationId,
            context: 'webview_cookie_save',
            extra: {
              'attempt': attempt + 1,
              'result_type': jsCookiesResult.runtimeType.toString(),
              'result_is_null': jsCookiesResult == null,
            },
          );
          
          if (jsCookiesResult != null) {
            jsCookiesString = jsCookiesResult.toString();
            
            await logger.log(
              level: 'debug',
              subsystem: 'cookies',
              message: 'JavaScript cookie string received',
              operationId: operationId,
              context: 'webview_cookie_save',
              extra: {
                'attempt': attempt + 1,
                'cookie_string_length': jsCookiesString.length,
                'cookie_string_preview': jsCookiesString.length > 100 
                    ? '${jsCookiesString.substring(0, 100)}...' 
                    : jsCookiesString,
                'is_empty': jsCookiesString.isEmpty,
              },
            );
            
            if (jsCookiesString.isNotEmpty) {
              // Success! Break out of retry loop
              await logger.log(
                level: 'info',
                subsystem: 'cookies',
                message: 'Successfully got cookies via JavaScript (attempt ${attempt + 1})',
                operationId: operationId,
                context: 'webview_cookie_save',
                extra: {
                  'attempt': attempt + 1,
                  'cookie_string_length': jsCookiesString.length,
                },
              );
              break; // Exit retry loop on success
            } else {
              // Empty string - might need to wait longer
              await logger.log(
                level: 'debug',
                subsystem: 'cookies',
                message: 'JavaScript returned empty cookie string (attempt ${attempt + 1})',
                operationId: operationId,
                context: 'webview_cookie_save',
                extra: {
                  'attempt': attempt + 1,
                  'will_retry': attempt < 2,
                },
              );
              if (attempt < 2) {
                // Will retry
                continue;
              }
            }
          } else {
            // Null result - might need to wait longer
            await logger.log(
              level: 'debug',
              subsystem: 'cookies',
              message: 'JavaScript returned null (attempt ${attempt + 1})',
              operationId: operationId,
              context: 'webview_cookie_save',
              extra: {
                'attempt': attempt + 1,
                'will_retry': attempt < 2,
              },
            );
            if (attempt < 2) {
              // Will retry
              continue;
            }
          }
        } on MissingPluginException catch (e) {
          lastError = e;
          await logger.log(
            level: 'warning',
            subsystem: 'cookies',
            message: 'MissingPluginException on attempt ${attempt + 1}/3 - WebView may not be ready yet',
            operationId: operationId,
            context: 'webview_cookie_save',
            cause: e.toString(),
            extra: {
              'attempt': attempt + 1,
              'will_retry': attempt < 2,
              'error_type': 'MissingPluginException',
              'is_mounted': mounted,
            },
          );
          if (attempt < 2) {
            continue; // Retry
        }
      } on Exception catch (e) {
          lastError = e;
        await logger.log(
            level: 'warning',
          subsystem: 'cookies',
            message: 'Exception getting cookies via JavaScript (attempt ${attempt + 1}/3)',
          operationId: operationId,
          context: 'webview_cookie_save',
            cause: e.toString(),
          extra: {
              'attempt': attempt + 1,
              'will_retry': attempt < 2,
              'error_type': e.runtimeType.toString(),
              'is_mounted': mounted,
            },
          );
          if (attempt < 2) {
            continue; // Retry
          }
        }
        }
      }
      
      // Process cookies: Use CookieManager cookies as PRIMARY, JavaScript as supplement
      if (cookieManagerCookies != null && cookieManagerCookies.isNotEmpty) {
        // Use CookieManager cookies (includes HttpOnly cookies like bb_session)
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
        if (_bridgeSessionManager != null && (jsCookieStrings.isNotEmpty)) {
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
          
          await _bridgeSessionManager!.saveSessionCookies(jsCookieStrings);
          
          await logger.log(
            level: 'info',
            subsystem: 'cookies',
            message: 'CookieManager cookies saved to FlutterCookieBridge SessionManager',
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
              'saved_to_session_manager': true,
            },
          );
        } else if (_bridgeSessionManager == null) {
          await logger.log(
            level: 'error',
            subsystem: 'cookies',
            message: 'CRITICAL: FlutterCookieBridge SessionManager is not initialized!',
            operationId: operationId,
            context: 'webview_cookie_save',
            extra: {
              'caller_context': callerContext ?? 'not_provided',
              'cookie_count_attempted': jsCookieStrings.length,
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
        
        // Parse JavaScript cookies: format is "name=value; name2=value2"
        // Handle both "; " and ";" separators
        final rawCookieStrings = jsCookiesString.split(RegExp(r';\s*'));
        for (final rawCookie in rawCookieStrings) {
          final trimmed = rawCookie.trim();
          if (trimmed.isNotEmpty && trimmed.contains('=')) {
            jsCookieStrings.add(trimmed);
          }
        }
        
        if (jsCookieStrings.isNotEmpty) {
      await logger.log(
            level: 'info',
        subsystem: 'cookies',
            message: 'Parsed JavaScript cookies',
        operationId: operationId,
        context: 'webview_cookie_save',
        extra: {
              'parsed_cookie_count': jsCookieStrings.length,
              'parsed_cookie_names': jsCookieStrings
                  .map((s) {
                    final parts = s.split('=');
                    return parts.isNotEmpty ? parts[0].trim() : '';
                  })
                  .where((name) => name.isNotEmpty)
              .toList(),
        },
      );
          
          // Save JavaScript cookies directly to SessionManager
          // This is the PRIMARY and ONLY method for cookie synchronization
          if (_bridgeSessionManager != null) {
            await logger.log(
              level: 'info',
              subsystem: 'cookies',
              message: 'About to save cookies to FlutterCookieBridge SessionManager',
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
            
            await _bridgeSessionManager!.saveSessionCookies(jsCookieStrings);
            
            await logger.log(
              level: 'info',
              subsystem: 'cookies',
              message: 'JavaScript cookies saved to FlutterCookieBridge SessionManager',
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
                'saved_to_session_manager': true,
              },
            );
          } else {
            await logger.log(
              level: 'error',
              subsystem: 'cookies',
              message: 'CRITICAL: FlutterCookieBridge SessionManager is not initialized!',
              operationId: operationId,
              context: 'webview_cookie_save',
              extra: {
                'caller_context': callerContext ?? 'not_provided',
                'cookie_count_attempted': jsCookieStrings.length,
              },
            );
          }
          
          // Convert to WebView Cookie format for backward compatibility (SharedPreferences)
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
        } else {
          await logger.log(
            level: 'warning',
            subsystem: 'cookies',
            message: 'JavaScript returned cookies string but parsing resulted in empty list',
            operationId: operationId,
            context: 'webview_cookie_save',
            extra: {
              'js_cookies_string': jsCookiesString,
            },
          );
        }
      } else {
        // Failed to get cookies after all retries
        await logger.log(
          level: 'error',
          subsystem: 'cookies',
          message: 'CRITICAL: Failed to get cookies via JavaScript after all retries',
          operationId: operationId,
          context: 'webview_cookie_save',
          cause: lastError?.toString() ?? 'Unknown error - no exception was caught',
          extra: {
            'current_url': urlToUse,
            'note': 'This is the PRIMARY method - cookies will not be available for Dio requests',
            'retries': 3,
            'last_error_type': lastError?.runtimeType.toString() ?? 'null',
            'is_mounted': mounted,
            'final_js_cookies_string': jsCookiesString ?? 'null',
            'final_js_cookies_string_length': jsCookiesString?.length ?? 0,
          },
        );
      }

      final cookieJson = jsonEncode(cookies
          .map((c) => {
                'name': c.name,
                'value': c.value,
                'domain': c.domain,
                'path': c.path,
                // Note: flutter_inappwebview Cookie doesn't expose expires property
                // We'll rely on the cookie's natural expiration
              })
          .toList());

      final prefs = await SharedPreferences.getInstance();
      await prefs.setString('rutracker_cookies_v1', cookieJson);

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
          'saved_cookies': cookies
              .map((c) => {
                    'name': c.name,
                    'domain': c.domain ?? '<null>',
                    'path': c.path ?? '/',
                  })
              .toList(),
        },
      );

      // Note: Cookies are already saved to FlutterCookieBridge SessionManager above
      // (in the JavaScript parsing section, lines 586-604)
      // SessionManager is the PRIMARY storage - it automatically syncs to Dio via interceptor
      
      // Also sync to Dio CookieJar (for backward compatibility, but SessionManager is primary)
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
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      // If cookie saving fails, log but don't crash
      await logger.log(
        level: 'warning',
        subsystem: 'cookies',
        message: 'Failed to save cookies from WebView',
        operationId: operationId,
        context: 'webview_cookie_save',
        durationMs: duration,
        cause: e.toString(),
        extra: {
          'initial_url': initialUrl,
          'stack_trace':
              (e is Error) ? (e as Error).stackTrace.toString() : null,
        },
      );
      debugPrint('Failed to save cookies: $e');
    }
  }

  /// Directly syncs cookies from WebView CookieManager to Dio CookieJar.
  ///
  /// This is the proper way to sync cookies: WebView CookieManager → Dio CookieJar.
  /// SharedPreferences is only used as a backup.
  Future<void> _syncCookiesDirectlyToDio(
      List<Cookie> webViewCookies, String operationId) async {
    try {
      final logger = StructuredLogger();
      final rutrackerDomains = EndpointManager.getRutrackerDomains();

      await logger.log(
        level: 'info',
        subsystem: 'cookies',
        message: 'Starting direct cookie sync to Dio',
        operationId: operationId,
        context: 'cookie_sync_direct',
        extra: {
          'webview_cookie_count': webViewCookies.length,
          'webview_cookies': webViewCookies.map((c) => {
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
      
      // CRITICAL: If no cookies to sync, log error
      if (webViewCookies.isEmpty) {
        await logger.log(
          level: 'error',
          subsystem: 'cookies',
          message: 'CRITICAL: No cookies to sync from WebView to Dio!',
          operationId: operationId,
          context: 'cookie_sync_direct',
          extra: {
            'note': 'This means cookies were not extracted from WebView CookieManager',
          },
        );
        return; // Nothing to sync
      }

      // Convert WebView cookies (flutter_inappwebview Cookie) to Dio Cookie format (dart:io Cookie)
      // Use explicit type to avoid conflict between flutter_inappwebview.Cookie and dart:io.Cookie
      final dioCookies = <io.Cookie>[];
      var conversionErrors = 0;
      for (final webViewCookie in webViewCookies) {
        try {
          // Normalize domain: remove leading dot if present (CookieJar expects host format)
          var normalizedDomain = webViewCookie.domain;
          if (normalizedDomain != null && normalizedDomain.isNotEmpty) {
            if (normalizedDomain.startsWith('.')) {
              normalizedDomain = normalizedDomain.substring(1);
            }
          }
          
          // dart:io.Cookie constructor: Cookie(String name, String value)
          final dioCookie = io.Cookie(webViewCookie.name, webViewCookie.value)
            ..domain = normalizedDomain  // Use normalized domain (no leading dot)
            ..path = webViewCookie.path ?? '/'
            ..secure = webViewCookie.isSecure ?? true
            ..httpOnly = webViewCookie.isHttpOnly ?? false;
          dioCookies.add(dioCookie);
        } on Exception catch (e) {
          conversionErrors++;
          await logger.log(
            level: 'warning',
            subsystem: 'cookies',
            message: 'Failed to convert WebView cookie to Dio format',
            operationId: operationId,
            context: 'cookie_sync_direct',
            cause: e.toString(),
            extra: {
              'cookie_name': webViewCookie.name,
              'cookie_domain': webViewCookie.domain,
              'cookie_value_length': webViewCookie.value.length,
            },
          );
        }
      }

      await logger.log(
        level: 'info',
        subsystem: 'cookies',
        message: 'Cookie conversion completed',
        operationId: operationId,
        context: 'cookie_sync_direct',
        extra: {
          'webview_cookie_count': webViewCookies.length,
          'dio_cookie_count': dioCookies.length,
          'conversion_errors': conversionErrors,
          'dio_cookies': dioCookies.map((c) => {
            'name': c.name,
            'domain': c.domain ?? '<null>',
            'path': c.path ?? '/',
            'has_value': c.value.isNotEmpty,
            'value_length': c.value.length,
            'value_preview': c.value.isNotEmpty 
                ? '${c.value.substring(0, c.value.length > 20 ? 20 : c.value.length)}...' 
                : '<empty>',
          }).toList(),
        },
      );

      if (dioCookies.isEmpty) {
        await logger.log(
          level: 'error',
          subsystem: 'cookies',
          message: 'CRITICAL: No cookies to sync after conversion - all cookies were lost!',
          operationId: operationId,
          context: 'cookie_sync_direct',
          extra: {
            'webview_cookie_count': webViewCookies.length,
            'conversion_errors': conversionErrors,
            'note': 'This indicates a problem with cookie conversion from WebView to Dio format',
          },
        );
        return;
      }

      // Save cookies for RuTracker domains directly to Dio CookieJar
      // Use smart domain matching: cookies should match the domain they came from
      // But also save important cookies (session, cf_clearance) to all domains
      for (final domain in rutrackerDomains) {
        try {
          final domainUri = Uri.parse('https://$domain');
          
          // Smart filtering: match cookies to their domain, but include important ones
          final domainCookies = dioCookies.where((cookie) {
            final cookieDomain = (cookie.domain ?? '').toLowerCase();
            final targetDomain = domain.toLowerCase();
            
            // Important cookies (session, cf_clearance) should be saved for all domains
            final isImportantCookie = cookie.name.toLowerCase().contains('session') ||
                cookie.name == 'bb_session' ||
                cookie.name == 'bb_data' ||
                cookie.name == 'cf_clearance' ||
                cookie.name.startsWith('cf_');
            
            // If cookie has no domain, it's for the current domain
            if (cookieDomain.isEmpty) {
              return true; // Save domainless cookies for this domain
            }
            
            // Exact match
            if (cookieDomain == targetDomain || cookieDomain == '.$targetDomain') {
              return true;
            }
            
            // Subdomain match (e.g., .rutracker.me matches rutracker.me)
            if (cookieDomain.startsWith('.') && cookieDomain.substring(1) == targetDomain) {
              return true;
            }
            
            // Important cookies for all domains
            if (isImportantCookie) {
              return true;
            }
            
            // Domain contains target (e.g., rutracker.me in .rutracker.me)
            if (cookieDomain.contains(targetDomain)) {
              return true;
            }
            
            return false;
          }).toList();

          if (domainCookies.isNotEmpty) {
            await logger.log(
              level: 'debug',
              subsystem: 'cookies',
              message: 'Saving filtered cookies for domain',
              operationId: operationId,
              context: 'cookie_sync_direct',
              extra: {
                'domain': domain,
                'cookie_count': domainCookies.length,
                'total_cookies': dioCookies.length,
                'cookies': domainCookies.map((c) => {
                  'name': c.name,
                  'domain': c.domain ?? '<null>',
                  'path': c.path ?? '/',
                  'has_value': c.value.isNotEmpty,
                }).toList(),
              },
            );
            // Save cookies directly to Dio CookieJar via DioClient
            await DioClient.saveCookiesDirectly(domainUri, domainCookies);
            await logger.log(
              level: 'info',
              subsystem: 'cookies',
              message: 'Successfully saved cookies to Dio CookieJar for domain',
              operationId: operationId,
              context: 'cookie_sync_direct',
              extra: {
                'domain': domain,
                'cookie_count': domainCookies.length,
              },
            );
          } else {
            await logger.log(
              level: 'debug',
              subsystem: 'cookies',
              message: 'No matching cookies to save for domain',
              operationId: operationId,
              context: 'cookie_sync_direct',
              extra: {
                'domain': domain,
                'total_cookies': dioCookies.length,
              },
            );
          }
        } on Exception catch (e) {
          await logger.log(
            level: 'warning',
            subsystem: 'cookies',
            message: 'Failed to save cookies for domain',
            operationId: operationId,
            context: 'cookie_sync_direct',
            cause: e.toString(),
            extra: {'domain': domain},
          );
        }
      }

      await logger.log(
        level: 'info',
        subsystem: 'cookies',
        message: 'Cookies synced directly from WebView to Dio CookieJar',
        operationId: operationId,
        context: 'cookie_sync_direct',
        extra: {
          'total_cookies': dioCookies.length,
          'domains': rutrackerDomains,
        },
      );
    } on Exception catch (e) {
      await StructuredLogger().log(
        level: 'warning',
        subsystem: 'cookies',
        message: 'Failed to sync cookies directly to Dio',
        operationId: operationId,
        context: 'cookie_sync_direct',
        cause: e.toString(),
      );
      rethrow;
    }
  }

  /// Checks if login was successful by verifying session cookies.
  ///
  /// PRIMARY METHOD: Checks cookies via JavaScript (as cookies are set via JavaScript).
  /// Falls back to URL/HTML checks if JavaScript cookies are not available.
  Future<bool> _checkLoginSuccess(InAppWebViewController controller) async {
    // PRIMARY METHOD: Check cookies via JavaScript with retries
    String? jsCookiesString;
    Exception? lastError;
    
    for (var attempt = 0; attempt < 3; attempt++) {
      try {
        // Wait a bit before retry (except first attempt)
        if (attempt > 0) {
          await Future.delayed(Duration(milliseconds: 300 * attempt));
        }
        
        final jsCookiesResult = await controller.evaluateJavascript(source: 'document.cookie');
        if (jsCookiesResult != null) {
          jsCookiesString = jsCookiesResult.toString();
          if (jsCookiesString.isNotEmpty) {
            // Success! Break out of retry loop
            break;
          }
        }
      } on MissingPluginException catch (e) {
        lastError = e;
        if (attempt < 2) {
          continue; // Retry
        }
      } on Exception catch (e) {
        lastError = e;
        if (attempt < 2) {
          continue; // Retry
        }
      }
    }
    
    // Process cookies if we got them
    if (jsCookiesString != null && jsCookiesString.isNotEmpty) {
      // Check if JavaScript cookies contain session cookies
      // Key indicators: bb_session, bb_data (these are set ONLY after successful login)
      // IMPORTANT: We check for specific session cookies, not just "session" in name
      // This prevents false positives from other cookies
      final hasBbSession = jsCookiesString.contains('bb_session=');
      final hasBbData = jsCookiesString.contains('bb_data=');
      final hasSessionInJs = hasBbSession || hasBbData;
      
      if (hasSessionInJs) {
        await StructuredLogger().log(
          level: 'info',
          subsystem: 'webview',
          message: 'Login success detected via session cookies (JavaScript)',
          extra: {
            'has_bb_session': hasBbSession,
            'has_bb_data': hasBbData,
            'js_cookies_preview': jsCookiesString.length > 200 
                ? '${jsCookiesString.substring(0, 200)}...' 
                : jsCookiesString,
            'js_cookies_length': jsCookiesString.length,
          },
        );
        return true;
      } else {
        await StructuredLogger().log(
          level: 'debug',
          subsystem: 'webview',
          message: 'JavaScript cookies found but no session cookies (bb_session/bb_data) detected',
          extra: {
            'js_cookies_preview': jsCookiesString.length > 200 
                ? '${jsCookiesString.substring(0, 200)}...' 
                : jsCookiesString,
            'note': 'User may not be logged in yet, or cookies are still being set',
          },
        );
      }
    } else {
      // Failed to get cookies after all retries
      await StructuredLogger().log(
        level: 'debug',
        subsystem: 'webview',
        message: 'Failed to get cookies via JavaScript after all retries, falling back to URL/HTML check',
        cause: lastError?.toString() ?? 'Unknown error',
        extra: {
          'retries': 3,
        },
      );
    }

    // Final fallback: URL/HTML check ONLY if JavaScript cookies were found but no session cookies
    // IMPORTANT: We don't rely on URL/HTML alone - user might just open index.php without login
    // This is only a fallback when JavaScript is unavailable
    try {
      final currentUrl = await controller.getUrl();
      final urlString = currentUrl?.toString().toLowerCase() ?? '';
      final html = await controller.getHtml();

      // Only check URL/HTML if we have some indication that login might have happened
      // Check for profile.php (strong indicator) or HTML with logout button (user is logged in)
      final urlIndicatesLogin = urlString.contains('profile.php');
      
      // HTML check: look for logout button or profile link (strong indicators)
      final htmlIndicatesLogin = html != null &&
          (html.toLowerCase().contains('выход') ||
              html.toLowerCase().contains('logout') ||
              html.toLowerCase().contains('личный кабинет'));

      // Only return true if we have STRONG indicators (profile.php or logout button)
      // Don't rely on just index.php - user might just open it without login
      final isLoginSuccess = urlIndicatesLogin || htmlIndicatesLogin;

      if (isLoginSuccess) {
        await StructuredLogger().log(
          level: 'debug',
          subsystem: 'webview',
          message: 'Login success detected via URL/HTML check (fallback)',
          extra: {
            'url': urlString,
            'has_html': html != null,
            'url_indicates_login': urlIndicatesLogin,
            'html_indicates_login': htmlIndicatesLogin,
            'note': 'This is a fallback - primary method is JavaScript cookies',
          },
        );
      }
      
      return isLoginSuccess;
    } on Exception {
      return false;
    }
  }

  /// Starts periodic cookie synchronization timer.
  ///
  /// This ensures cookies are regularly synced from WebView to DioClient,
  /// especially important after Cloudflare challenges or when returning from browser.
  void _startCookieSyncTimer() {
    _cookieSyncTimer?.cancel();
    _cookieSyncTimer = Timer.periodic(_cookieSyncInterval, (timer) async {
      if (!mounted) {
        timer.cancel();
        return;
      }
      
      try {
        // Save cookies from WebView (sync happens automatically in _saveCookies)
        await _saveCookies(callerContext: 'periodic_timer');
      } on Exception catch (e) {
        // Log but don't spam - only log occasionally
        if (timer.tick % 12 == 0) { // Log every ~60 seconds (12 * 5s)
          await StructuredLogger().log(
            level: 'debug',
            subsystem: 'cookies',
            message: 'Periodic cookie sync failed',
            cause: e.toString(),
          );
        }
      }
    });
  }

  // WebView state management
  Future<void> _restoreWebViewHistory() async {
    final prefs = await SharedPreferences.getInstance();
    final historyJson = prefs.getString(_webViewStateKey);
    if (historyJson != null) {
      try {
        final history = jsonDecode(historyJson) as Map<String, dynamic>;
        // Restore current URL if available
        if (history['currentUrl'] != null) {
          // Note: We'll let the WebView load the initial URL and navigate to saved URL
          // This is a simplified approach
          WidgetsBinding.instance.addPostFrameCallback((_) {
            _navigateToSavedUrl(history['currentUrl']);
          });
        }
      } on Exception catch (e) {
        debugPrint('Failed to restore WebView history: $e');
      }
    }
  }

  Future<void> _saveWebViewHistory() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final currentUrl = await _webViewController.getUrl();

      final history = {
        'currentUrl': currentUrl?.toString(),
        'timestamp': DateTime.now().toIso8601String(),
      };

      await prefs.setString(_webViewStateKey, jsonEncode(history));
    } on Exception catch (e) {
      debugPrint('Failed to save WebView history: $e');
    }
  }

  Future<void> _navigateToSavedUrl(String? savedUrl) async {
    if (savedUrl != null && savedUrl.isNotEmpty) {
      try {
        await _webViewController.loadUrl(
            urlRequest: URLRequest(url: WebUri(savedUrl)));
      } on Exception catch (e) {
        debugPrint('Failed to navigate to saved URL: $e');
      }
    }
  }

  /// Checks if HTML looks like an active Cloudflare challenge page.
  ///
  /// Returns true only if it's actually a challenge page, not just a page
  /// that mentions Cloudflare (e.g., in headers or meta tags).
  bool _looksLikeCloudflare(String html) {
    final h = html.toLowerCase();
    
    // Check for active challenge indicators (strong signals)
    final hasActiveChallenge = h.contains('checking your browser') ||
        h.contains('please enable javascript') ||
        h.contains('attention required') ||
        h.contains('cf-chl-bypass') ||
        h.contains('just a moment') ||
        h.contains('verifying you are human') ||
        h.contains('security check') ||
        h.contains('cf-browser-verification') ||
        h.contains('cf-challenge-running') ||
        h.contains('ddos-guard');
    
    if (!hasActiveChallenge) {
      return false;
    }
    
    // If we have active challenge indicators, check if page has real content
    // Challenge pages are usually small and don't have forum content
    final hasRealContent = h.contains('forum') ||
        h.contains('трекер') ||
        h.contains('torrent') ||
        h.contains('login') ||
        h.contains('поиск') ||
        h.contains('search') ||
        h.contains('profile') ||
        h.contains('index.php') ||
        h.contains('viewtopic') ||
        h.contains('viewforum');
    
    // If page has real content, it's not a challenge page
    // (even if it mentions Cloudflare in headers)
    if (hasRealContent) {
      return false;
    }
    
    // Check page size - challenge pages are usually small (< 50KB)
    // Real pages with content are usually larger
    if (html.length > 50000) {
      return false;
    }
    
    return true;
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
          title:
              Text(AppLocalizations.of(context)?.webViewTitle ?? 'RuTracker'),
          actions: [
            IconButton(
              icon: const Icon(Icons.open_in_browser),
              onPressed: () async {
                final scaffoldMessenger = ScaffoldMessenger.of(context);
                final localizations = AppLocalizations.of(context);
                try {
                  String? urlString;
                  
                  // Try to get URL from WebView controller
                  try {
                    final url = await _webViewController.getUrl();
                    if (url != null && url.toString().isNotEmpty) {
                      urlString = url.toString();
                    }
                  } on Exception {
                    // If getting URL from controller fails, continue with fallback
                  }

                  // Fallback to initialUrl if WebView URL is not available
                  if (urlString == null || urlString.isEmpty) {
                    urlString = initialUrl;
                  }

                  // Final fallback to default rutracker URL
                  if (urlString == null || urlString.isEmpty) {
                    urlString = EndpointManager.getPrimaryFallbackEndpoint();
                  }

                  // Validate and parse URL
                  final uri = Uri.tryParse(urlString);
                  if (uri != null && uri.hasScheme && uri.hasAuthority) {
                    // Try to launch URL - don't check canLaunchUrl first as it may fail
                    // even when the URL can be launched
                    try {
                      final launched = await launchUrl(
                        uri,
                        mode: LaunchMode.externalApplication,
                      );
                      if (!launched && mounted) {
                        scaffoldMessenger.showSnackBar(
                          SnackBar(
                            content: Text(localizations?.urlUnavailable ??
                                'URL unavailable'),
                            duration: const Duration(seconds: 2),
                          ),
                        );
                      }
                    } on Exception catch (launchError) {
                      if (!mounted) return;
                      await StructuredLogger().log(
                        level: 'error',
                        subsystem: 'webview',
                        message: 'Failed to launch URL in browser',
                        cause: launchError.toString(),
                        extra: {'url': urlString},
                      );
                      scaffoldMessenger.showSnackBar(
                        SnackBar(
                          content: Text(
                            localizations?.urlUnavailable ??
                                'Cannot open URL in browser: ${launchError.toString()}',
                          ),
                          duration: const Duration(seconds: 3),
                        ),
                      );
                    }
                  } else {
                    if (!mounted) return;
                    await StructuredLogger().log(
                      level: 'warning',
                      subsystem: 'webview',
                      message: 'Invalid URL format for browser',
                      extra: {'url': urlString},
                    );
                    scaffoldMessenger.showSnackBar(
                      SnackBar(
                        content: Text(
                          localizations?.invalidUrlFormat(urlString) ??
                              'Invalid URL format: $urlString',
                        ),
                        duration: const Duration(seconds: 3),
                      ),
                    );
                  }
                } on Exception catch (e) {
                  if (!mounted) return;
                  await StructuredLogger().log(
                    level: 'error',
                    subsystem: 'webview',
                    message: 'Failed to open URL in browser',
                    cause: e.toString(),
                  );
                  scaffoldMessenger.showSnackBar(
                    SnackBar(
                      content: Text(
                        localizations?.genericError(e.toString()) ??
                            'Error: ${e.toString()}',
                      ),
                      duration: const Duration(seconds: 3),
                    ),
                  );
                }
              },
            ),
          ],
          bottom: PreferredSize(
            preferredSize: const Size.fromHeight(3.0),
            child:
                LinearProgressIndicator(value: progress == 1 ? null : progress),
          ),
        ),
        body: Stack(
          children: [
            Column(
              children: [
                // Connection status indicator (if retrying)
                if (_retryCount > 0 && _retryCount <= _maxRetries)
                  Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 12.0, vertical: 8.0),
                    color: Colors.orange.shade50,
                    child: Row(
                      children: [
                        Icon(
                          Icons.refresh,
                          color: Colors.orange.shade700,
                          size: 16,
                        ),
                        const SizedBox(width: 8.0),
                        Expanded(
                          child: Text(
                            AppLocalizations.of(context)!
                                    .retryConnectionMessage(_retryCount, _maxRetries),
                            style: TextStyle(
                              color: Colors.orange.shade800,
                              fontSize: 12.0,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                // Cloudflare explanation banner
                Container(
                  padding: const EdgeInsets.all(12.0),
                  color: Colors.blue.shade50,
                  child: Row(
                    children: [
                      Icon(
                        Icons.security,
                        color: Colors.blue.shade700,
                        size: 20,
                      ),
                      const SizedBox(width: 8.0),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              AppLocalizations.of(context)
                                      ?.securityVerificationInProgress ??
                                  'Security verification in progress - please wait...',
                              style: TextStyle(
                                color: Colors.blue.shade800,
                                fontSize: 14.0,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              AppLocalizations.of(context)?.cloudflareMessage ??
                                  'This site uses Cloudflare security checks. Please wait for the check to complete and interact with the page that opens if needed.',
                              style: TextStyle(
                                color: Colors.blue.shade700,
                                fontSize: 12.0,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                // WebView
                Expanded(
                  child: (initialUrl == null || _userAgent == null)
                      ? const Center(child: CircularProgressIndicator())
                      : InAppWebView(
                          initialUrlRequest: URLRequest(
                            url: WebUri(initialUrl!),
                            // Add legitimate headers for Cloudflare compatibility
                            headers: {
                              'User-Agent': _userAgent!,
                              'Accept':
                                  'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8',
                              'Accept-Language': 'ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7',
                              'Accept-Encoding': 'gzip, deflate, br',
                              'Upgrade-Insecure-Requests': '1',
                              'Cache-Control': 'no-cache',
                              'Sec-Fetch-Dest': 'document',
                              'Sec-Fetch-Mode': 'navigate',
                              'Sec-Fetch-Site': 'none',
                              'Sec-Fetch-User': '?1',
                            },
                          ),
                          initialSettings: InAppWebViewSettings(
                            useShouldOverrideUrlLoading: true,
                            sharedCookiesEnabled: true,
                            allowsInlineMediaPlayback: true,
                            mediaPlaybackRequiresUserGesture: false,
                            // Set User-Agent for all requests (required for Cloudflare)
                            userAgent: _userAgent,
                            // JavaScript is enabled by default (required for Cloudflare challenge)
                            // Error handling
                            supportZoom: false,
                            // Memory management
                            minimumLogicalFontSize: 1,
                            // SSL/TLS settings
                            mixedContentMode:
                                MixedContentMode.MIXED_CONTENT_ALWAYS_ALLOW,
                          ),
                          onWebViewCreated: (controller) async {
                            _webViewController = controller;
                            _hasError =
                                false; // Reset error state when WebView is recreated

                            // Log WebView creation
                            await StructuredLogger().log(
                              level: 'info',
                              subsystem: 'webview',
                              message: 'WebView created successfully',
                              extra: {
                                'initial_url': initialUrl,
                                'user_agent': _userAgent?.substring(0, 50) ?? 'null',
                              },
                            );
                            
                            // Load cookies from FlutterCookieBridge SessionManager into WebView
                            // This ensures cookies from previous sessions are available
                            try {
                              if (_bridgeSessionManager != null) {
                                final sessionCookies = await _bridgeSessionManager!.getSessionCookies();
                                if (sessionCookies.isNotEmpty && initialUrl != null) {
                                  // Set cookies in WebView (like FlutterCookieBridge does)
                                  final cookieManager = CookieManager.instance();
                                  final uri = Uri.tryParse(initialUrl!);
                                  if (uri != null) {
                                    for (final cookieString in sessionCookies) {
                                      final parts = cookieString.split('=');
                                      if (parts.length >= 2) {
                                        final name = parts[0].trim();
                                        final value = parts.sublist(1).join('=').trim();
                                        if (name.isNotEmpty && value.isNotEmpty) {
                                          await cookieManager.setCookie(
                                            url: WebUri(initialUrl!),
                                            name: name,
                                            value: value,
                                            domain: uri.host,
                                          );
                                        }
                                      }
                                    }
                                    await StructuredLogger().log(
                                      level: 'info',
                                      subsystem: 'cookies',
                                      message: 'Loaded cookies from FlutterCookieBridge into WebView',
                                      extra: {
                                        'cookie_count': sessionCookies.length,
                                        'url': initialUrl,
                                      },
                                    );
                                  }
                                }
                              }
                            } on Exception catch (e) {
                              await StructuredLogger().log(
                                level: 'debug',
                                subsystem: 'cookies',
                                message: 'Failed to load cookies from FlutterCookieBridge into WebView',
                                cause: e.toString(),
                              );
                            }
                            
                            // Start periodic cookie synchronization
                            _startCookieSyncTimer();
                            
                            // Ensure WebView loads the URL if it wasn't loaded automatically
                            // This is a safety measure in case initialUrlRequest didn't work
                            if (initialUrl != null && initialUrl!.isNotEmpty) {
                              unawaited(Future.microtask(() async {
                                try {
                                  final currentUrl = await controller.getUrl();
                                  if (currentUrl == null || currentUrl.toString().isEmpty) {
                                    await StructuredLogger().log(
                                      level: 'debug',
                                      subsystem: 'webview',
                                      message: 'WebView URL is empty, loading initial URL',
                                      extra: {'initial_url': initialUrl},
                                    );
                                    await controller.loadUrl(
                                      urlRequest: URLRequest(
                                        url: WebUri(initialUrl!),
                                        headers: {
                                          'User-Agent': _userAgent ?? '',
                                          'Accept':
                                              'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8',
                                          'Accept-Language': 'ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7',
                                          'Accept-Encoding': 'gzip, deflate, br',
                                          'Upgrade-Insecure-Requests': '1',
                                          'Cache-Control': 'no-cache',
                                          'Sec-Fetch-Dest': 'document',
                                          'Sec-Fetch-Mode': 'navigate',
                                          'Sec-Fetch-Site': 'none',
                                          'Sec-Fetch-User': '?1',
                                        },
                                      ),
                                    );
                                  }
                                } on Exception catch (e) {
                                  await StructuredLogger().log(
                                    level: 'warning',
                                    subsystem: 'webview',
                                    message: 'Failed to ensure URL load in WebView',
                                    cause: e.toString(),
                                    extra: {'initial_url': initialUrl},
                                  );
                                }
                              }));
                            }
                          },
                          onProgressChanged: (controller, p) {
                            setState(() => progress = p / 100.0);
                          },
                          onLoadStart: (controller, url) async {
                            final operationId =
                                'webview_load_${DateTime.now().millisecondsSinceEpoch}';
                            final startTime = DateTime.now();
                            _currentOperationId = operationId;
                            _currentPageLoadStartTime = startTime;

                            final urlString = url?.toString() ?? '';
                            final logger = StructuredLogger();
                            
                            // NOTE: We don't check for login success in onLoadStart
                            // This is too early - user might just be opening the page without login
                            // Login success is checked in onLoadStop after page fully loads and cookies are set
                            // Checking here causes premature WebView closure when user opens index.php

                            // Log when page starts loading
                            await logger.log(
                              level: 'info',
                              subsystem: 'webview',
                              message: 'WebView page load started',
                              operationId: operationId,
                              context: 'webview_navigation',
                              extra: {
                                'url': urlString,
                                'is_initial_url': urlString == initialUrl,
                                'retry_count': _retryCount,
                              },
                            );
                            setState(() {
                              _hasError = false;
                              _errorMessage = null;
                              // Reset retry count on new page load
                              if (url != null && url.toString() != initialUrl) {
                                _retryCount = 0;
                              }
                            });
                          },
                          onReceivedError: (controller, request, error) async {
                            final desc = error.description.toString();
                            final isOrb = desc.contains('ERR_BLOCKED_BY_ORB') ||
                                desc.contains('ERR_BLOCKED_BY_CLIENT') ||
                                desc.contains('ERR_BLOCKED_BY_RESPONSE');
                            // ORB is not considered critical even if Chromium marked it as main frame
                            if (isOrb) {
                              return;
                            }
                            if (!(request.isForMainFrame ?? true) &&
                                (desc.contains('CORS') ||
                                    desc.contains('Cross-Origin'))) {
                              return;
                            }
                            if (!(request.isForMainFrame ?? true)) return;

                            final operationId = _currentOperationId;
                            final startTime = _currentPageLoadStartTime;
                            final duration = startTime != null
                                ? DateTime.now()
                                    .difference(startTime)
                                    .inMilliseconds
                                : null;

                            final logger = StructuredLogger();

                            // Determine error category
                            final isNetworkError = desc
                                    .contains('host lookup') ||
                                desc.contains(
                                    'no address associated with hostname') ||
                                desc.contains('name or service not known') ||
                                desc.contains('connection timed out') ||
                                desc.contains('ERR_NAME_NOT_RESOLVED') ||
                                desc.contains('ERR_NAME_RESOLUTION_FAILED') ||
                                desc.contains('ERR_CONNECTION_TIMED_OUT') ||
                                desc.contains('ERR_NETWORK_CHANGED') ||
                                desc.contains('ERR_INTERNET_DISCONNECTED');

                            final isDnsError = desc.contains('host lookup') ||
                                desc.contains(
                                    'no address associated with hostname') ||
                                desc.contains('name or service not known') ||
                                desc.contains('ERR_NAME_NOT_RESOLVED') ||
                                desc.contains('ERR_NAME_RESOLUTION_FAILED');

                            // Log error with full details
                            await logger.log(
                              level: 'error',
                              subsystem: 'webview',
                              message: 'WebView received error',
                              operationId: operationId,
                              context: 'webview_navigation',
                              durationMs: duration,
                              cause: desc,
                              extra: {
                                'url': request.url.toString(),
                                'error_type': error.type.toString(),
                                'is_main_frame': request.isForMainFrame ?? true,
                                'is_network_error': isNetworkError,
                                'is_dns_error': isDnsError,
                                'retry_count': _retryCount,
                                'http_method': request.method,
                                'headers': request.headers,
                              },
                            );

                            // Auto-retry on network errors (up to max retries)
                            if (isNetworkError && _retryCount < _maxRetries) {
                              _retryCount++;
                              final retryDelaySeconds = 2 * _retryCount;

                              await logger.log(
                                level: 'info',
                                subsystem: 'webview',
                                message:
                                    'Auto-retrying WebView load after error',
                                operationId: operationId,
                                context: 'webview_retry',
                                durationMs: duration,
                                extra: {
                                  'retry_attempt': _retryCount,
                                  'max_retries': _maxRetries,
                                  'error': desc,
                                  'error_type': error.type.toString(),
                                  'retry_delay_seconds': retryDelaySeconds,
                                  'url': request.url.toString(),
                                },
                              );

                              // Wait a bit before retry
                              await Future.delayed(
                                  Duration(seconds: retryDelaySeconds));

                              // Try to reload or switch endpoint
                              final retrySwitchStartTime = DateTime.now();
                              try {
                                final db = AppDatabase().database;
                                final endpointManager = EndpointManager(db);
                                final currentUrl = request.url.toString();

                                if (currentUrl.isNotEmpty) {
                                  await logger.log(
                                    level: 'debug',
                                    subsystem: 'state',
                                    message:
                                        'Attempting endpoint switch during WebView retry',
                                    operationId: operationId,
                                    context: 'webview_retry',
                                    extra: {
                                      'retry_attempt': _retryCount,
                                      'error_type': error.type.toString(),
                                      'error_description': desc,
                                      'is_dns_error': isDnsError,
                                      'old_url': currentUrl,
                                      'switch_reason': isDnsError
                                          ? 'DNS_lookup_failed'
                                          : 'Network_error',
                                      'original_subsystem': 'webview',
                                    },
                                  );

                                  // For DNS errors, try fallback endpoints directly
                                  String? newEndpoint;
                                  if (isDnsError) {
                                    // DNS error - try fallback endpoints directly
                                    // Try all endpoints in priority order
                                    final fallbackEndpoints =
                                        EndpointManager.getDefaultEndpointUrls();
                                    newEndpoint = await _tryFallbackEndpoints(
                                      fallbackEndpoints,
                                    );
                                    if (newEndpoint != currentUrl) {
                                      // Update initialUrl for future use
                                      initialUrl = newEndpoint;
                                    }
                                  } else {
                                    // For other errors, use EndpointManager
                                    final switched = await endpointManager
                                        .trySwitchEndpoint(currentUrl);
                                    if (switched) {
                                      newEndpoint = await endpointManager
                                          .getActiveEndpoint();
                                    }
                                  }

                                  final switchDuration = DateTime.now()
                                      .difference(retrySwitchStartTime)
                                      .inMilliseconds;

                                  if (newEndpoint != null &&
                                      newEndpoint != currentUrl) {
                                    await logger.log(
                                      level: 'info',
                                      subsystem: 'state',
                                      message:
                                          'Successfully switched endpoint during WebView retry',
                                      operationId: operationId,
                                      context: 'webview_retry',
                                      durationMs: switchDuration,
                                      extra: {
                                        'retry_attempt': _retryCount,
                                        'old_url': currentUrl,
                                        'new_endpoint': newEndpoint,
                                        'switch_reason': isDnsError
                                            ? 'DNS_lookup_failed'
                                            : 'Network_error',
                                        'original_subsystem': 'webview',
                                      },
                                    );

                                    await controller.loadUrl(
                                      urlRequest:
                                          URLRequest(url: WebUri(newEndpoint)),
                                    );
                                    return; // Don't show error if retrying
                                  } else {
                                    await logger.log(
                                      level: 'warning',
                                      subsystem: 'state',
                                      message:
                                          'Endpoint switch failed during WebView retry',
                                      operationId: operationId,
                                      context: 'webview_retry',
                                      durationMs: switchDuration,
                                      extra: {
                                        'retry_attempt': _retryCount,
                                        'old_url': currentUrl,
                                        'switch_reason':
                                            'no_alternative_endpoint',
                                        'original_subsystem': 'webview',
                                      },
                                    );
                                  }
                                }
                              } on Exception catch (e) {
                                final switchDuration = DateTime.now()
                                    .difference(retrySwitchStartTime)
                                    .inMilliseconds;
                                await logger.log(
                                  level: 'warning',
                                  subsystem: 'state',
                                  message:
                                      'Exception during retry endpoint switch',
                                  operationId: operationId,
                                  context: 'webview_retry',
                                  durationMs: switchDuration,
                                  cause: e.toString(),
                                  extra: {
                                    'retry_attempt': _retryCount,
                                    'error_type': error.type.toString(),
                                    'switch_reason': 'exception',
                                    'stack_trace': (e is Error)
                                        ? (e as Error).stackTrace.toString()
                                        : null,
                                    'original_subsystem': 'webview',
                                  },
                                );
                                // Continue to show error
                              }

                              // Fallback to reload
                              await logger.log(
                                level: 'debug',
                                subsystem: 'webview',
                                message: 'Retrying with reload',
                                operationId: operationId,
                                context: 'webview_retry',
                                extra: {
                                  'retry_attempt': _retryCount,
                                  'url': request.url.toString(),
                                },
                              );

                              await controller.reload();
                              return; // Don't show error during retry
                            }

                            // Log when max retries reached
                            if (isNetworkError && _retryCount >= _maxRetries) {
                              await logger.log(
                                level: 'error',
                                subsystem: 'webview',
                                message: 'WebView max retries reached',
                                operationId: operationId,
                                context: 'webview_retry',
                                durationMs: duration,
                                extra: {
                                  'max_retries': _maxRetries,
                                  'final_error': desc,
                                  'url': request.url.toString(),
                                },
                              );
                            }

                            // Reset retry count on successful load
                            _retryCount = 0;

                            if (mounted) {
                              setState(() {
                                _hasError = true;
                                _errorMessage = AppLocalizations.of(context)!
                                        .loadError(desc);
                              });
                            }
                          },
                          onReceivedHttpError:
                              (controller, request, errorResponse) async {
                            // Show HTTP error only for main frame
                            if (!(request.isForMainFrame ?? true)) return;

                            final operationId = _currentOperationId;
                            final startTime = _currentPageLoadStartTime;
                            final duration = startTime != null
                                ? DateTime.now()
                                    .difference(startTime)
                                    .inMilliseconds
                                : null;

                            final logger = StructuredLogger();

                            // Log HTTP error
                            final statusCode = errorResponse.statusCode ?? 0;
                            final isServerError = statusCode >= 500;
                            final isCloudflareError =
                                statusCode == 403 || statusCode == 503;

                            await logger.log(
                              level: isServerError ? 'error' : 'warning',
                              subsystem: 'webview',
                              message: 'WebView received HTTP error',
                              operationId: operationId,
                              context: 'webview_navigation',
                              durationMs: duration,
                              extra: {
                                'url': request.url.toString(),
                                'status_code': statusCode,
                                'status_message': errorResponse.reasonPhrase,
                                'response_headers': errorResponse.headers,
                                'is_server_error': isServerError,
                                'is_cloudflare_error': isCloudflareError,
                                'is_main_frame': request.isForMainFrame ?? true,
                              },
                            );

                            // For 5xx errors, try endpoint switch
                            if (statusCode >= 500 && initialUrl != null) {
                              final db = AppDatabase().database;
                              final endpointManager = EndpointManager(db);
                              try {
                                final currentUrl = request.url.toString();
                                if (currentUrl.isNotEmpty) {
                                  await logger.log(
                                    level: 'debug',
                                    subsystem: 'webview',
                                    message:
                                        'Attempting endpoint switch due to HTTP 5xx error',
                                    operationId: operationId,
                                    context: 'webview_endpoint_switch',
                                    extra: {
                                      'old_url': currentUrl,
                                      'status_code': statusCode,
                                    },
                                  );

                                  final switched = await endpointManager
                                      .trySwitchEndpoint(currentUrl);
                                  if (switched) {
                                    final newEndpoint = await endpointManager
                                        .getActiveEndpoint();

                                    await logger.log(
                                      level: 'info',
                                      subsystem: 'webview',
                                      message:
                                          'Switched endpoint due to HTTP error',
                                      operationId: operationId,
                                      context: 'webview_endpoint_switch',
                                      durationMs: duration,
                                      extra: {
                                        'old_url': currentUrl,
                                        'new_endpoint': newEndpoint,
                                        'status_code': statusCode,
                                      },
                                    );

                                    await controller.loadUrl(
                                      urlRequest:
                                          URLRequest(url: WebUri(newEndpoint)),
                                    );
                                    return; // Don't show error if retrying
                                  }
                                }
                              } on Exception catch (e) {
                                await logger.log(
                                  level: 'warning',
                                  subsystem: 'webview',
                                  message:
                                      'Exception during HTTP error endpoint switch',
                                  operationId: operationId,
                                  context: 'webview_endpoint_switch',
                                  cause: e.toString(),
                                );
                                // Continue to show error
                              }
                            }

                            // For 4xx errors, only set error if not CloudFlare
                            if (statusCode == 403 || statusCode == 503) {
                              // Might be CloudFlare - don't show error immediately
                              return;
                            }

                            if (mounted) {
                              setState(() {
                                _hasError = true;
                                _errorMessage = AppLocalizations.of(context)!
                                        .genericError('HTTP $statusCode');
                              });
                            }
                          },
                          onLoadStop: (controller, url) async {
                            final operationId = _currentOperationId;
                            final startTime = _currentPageLoadStartTime;
                            final duration = startTime != null
                                ? DateTime.now()
                                    .difference(startTime)
                                    .inMilliseconds
                                : null;

                            final urlString = url?.toString() ?? '';
                            final logger = StructuredLogger();
                            
                            // Save Navigator before any async operations to avoid BuildContext issues
                            // We need it in case login is successful
                            final navigator = Navigator.of(context);

                            // Reset retry count and clear error state on successful load
                            if (mounted) {
                              setState(() {
                                _retryCount = 0;
                                _hasError = false;
                                _errorMessage = null;
                              });
                            }

                            // Log successful page load with timing
                            await logger.log(
                              level: 'info',
                              subsystem: 'webview',
                              message: 'WebView page load completed',
                              operationId: operationId,
                              context: 'webview_navigation',
                              durationMs: duration,
                              extra: {
                                'url': urlString,
                                'load_time_ms': duration,
                                'retry_count_used': _retryCount,
                              },
                            );

                            final html = await controller.getHtml();
                            final htmlSize = html?.length ?? 0;

                            // Check if we have session cookies - if yes, user is already authenticated
                            // and we shouldn't show Cloudflare overlay
                            final hasSessionCookies = await _checkLoginSuccess(controller);

                            // Only show Cloudflare overlay if:
                            // 1. HTML looks like active challenge
                            // 2. User doesn't have session cookies (not authenticated yet)
                            // 3. Overlay is not already shown
                            if (html != null && 
                                _looksLikeCloudflare(html) && 
                                !hasSessionCookies &&
                                !_isCloudflareDetected) {
                              await logger.log(
                                level: 'info',
                                subsystem: 'webview',
                                message:
                                    'CloudFlare challenge detected on page',
                                operationId: operationId,
                                context: 'webview_cloudflare',
                                durationMs: duration,
                                extra: {
                                  'url': urlString,
                                  'html_size': htmlSize,
                                  'has_session_cookies': false,
                                },
                              );
                              _showCloudflareHint();
                              // Show a more prominent Cloudflare indicator
                              _showCloudflareOverlay();
                              
                              // Start waiting for Cloudflare challenge to complete
                              // Similar to curl script: wait 2 seconds, then check if challenge passed
                              _startCloudflareWait(controller, urlString);
                            } else {
                              // Hide overlay if it was shown but challenge is gone or user is authenticated
                              if (_isCloudflareDetected && (hasSessionCookies || html == null || !_looksLikeCloudflare(html))) {
                                _hideCloudflareOverlay();
                              }
                              // Cloudflare challenge passed or not present
                              if (_isCloudflareDetected) {
                                // Challenge was detected before, now it's gone - reload page
                                await logger.log(
                                  level: 'info',
                                  subsystem: 'webview',
                                  message: 'CloudFlare challenge completed, reloading page',
                                  operationId: operationId,
                                  context: 'webview_cloudflare',
                                );
                                _hideCloudflareOverlay();
                                // Reload page to get actual content (like curl script does)
                                if (mounted) {
                                  await Future.delayed(const Duration(milliseconds: 500));
                                  await controller.reload();
                                }
                              }
                              // IMPORTANT: Wait before first cookie save to allow cookies to be set
                              // Cookies may be set via JavaScript or HTTP headers after page load
                              // Especially after redirects (like login redirect to index.php)
                              await logger.log(
                                level: 'debug',
                                subsystem: 'cookies',
                                message: 'Waiting before first cookie save in onLoadStop',
                                operationId: operationId,
                                context: 'webview_cookie_save',
                                extra: {
                                  'wait_ms': 2000,
                                  'note': 'Allowing time for cookies to be set after page load',
                                },
                              );
                              await Future.delayed(const Duration(milliseconds: 2000));
                              
                              // Save cookies first (only if WebView is still mounted)
                              if (mounted) {
                                await _saveCookies(callerContext: 'onLoadStop_after_wait');
                              }

                              // Wait a bit more for cookies to be set by JavaScript (especially after redirect)
                              // Cookies may be set via JavaScript after page load, so we need to wait
                              await Future.delayed(const Duration(milliseconds: 1500));
                              
                              // Try to get cookies again after delay (they might be set by JavaScript)
                              // Check mounted again after delay - WebView might have been closed
                              if (mounted) {
                                await _saveCookies(callerContext: 'onLoadStop_after_delay');
                              }

                              // Check if user successfully logged in using improved detection
                              final isLoginSuccess = await _checkLoginSuccess(controller);

                              if (isLoginSuccess) {
                                
                                final currentUrl = await controller.getUrl();
                                final urlStringForLog =
                                    currentUrl?.toString().toLowerCase() ?? '';
                                await logger.log(
                                  level: 'info',
                                  subsystem: 'webview',
                                  message: 'WebView login appears successful',
                                  operationId: operationId,
                                  context: 'webview_login',
                                  durationMs: duration,
                                  extra: {
                                    'url': urlStringForLog,
                                    'html_size': htmlSize,
                                    'detection_method': 'session_cookies_or_url',
                                  },
                                );
                                // Sync cookies after successful login
                                // According to recommendations: extract cookies once, use them in Dio
                                // Don't make multiple sync attempts to avoid Cloudflare rate limiting
                                  await logger.log(
                                    level: 'info',
                                    subsystem: 'cookies',
                                  message: 'Starting cookie sync after successful login',
                                    operationId: operationId,
                                    context: 'webview_login',
                                  );
                                
                                // Extract and save cookies from WebView BEFORE closing
                                // IMPORTANT: Save cookies while WebView is still mounted and accessible
                                  await logger.log(
                                  level: 'info',
                                    subsystem: 'cookies',
                                  message: 'Saving cookies before closing WebView',
                                    operationId: operationId,
                                    context: 'webview_login',
                                  extra: {
                                    'is_mounted': mounted,
                                  },
                                );
                                
                                // Save cookies while WebView is still mounted
                                await _saveCookies(callerContext: 'onLoadStop_login_success');
                                
                                // Verify cookies were saved
                                await logger.log(
                                  level: 'info',
                                  subsystem: 'cookies',
                                  message: 'Cookies saved, verifying before closing WebView',
                                  operationId: operationId,
                                  context: 'webview_login',
                                  extra: {
                                    'is_mounted': mounted,
                                  },
                                );
                                
                                // Small delay to ensure cookies are fully saved to SessionManager
                                await Future.delayed(const Duration(milliseconds: 300));
                                
                                // Verify User-Agent matches WebView (important for Cloudflare)
                                try {
                                  final userAgentManager = UserAgentManager();
                                  final currentUserAgent = await userAgentManager.getUserAgent();
                                await logger.log(
                                  level: 'debug',
                                  subsystem: 'cookies',
                                    message: 'User-Agent verification after login',
                                  operationId: operationId,
                                    context: 'webview_login',
                                  extra: {
                                      'user_agent': currentUserAgent,
                                      'matches_webview': currentUserAgent == _userAgent,
                                  },
                                );
                                } on Exception {
                                  // Ignore User-Agent check errors
                                }

                                await logger.log(
                                  level: 'info',
                                  subsystem: 'cookies',
                                  message: 'Cookies synced after login, ready to close WebView',
                                  operationId: operationId,
                                  context: 'webview_login',
                                  extra: {
                                    'is_mounted': mounted,
                                  },
                                );
                                
                                // Automatically close WebView and return to search screen
                                // User has successfully logged in, cookies are saved
                                await logger.log(
                                  level: 'info',
                                  subsystem: 'webview',
                                  message: 'Automatically closing WebView after successful login',
                                  operationId: operationId,
                                  context: 'webview_login',
                                  extra: {
                                    'is_mounted': mounted,
                                  },
                                );
                                
                                // Close WebView - cookies are already saved
                                if (mounted) {
                                  navigator.pop('login_success');
                                } else {
                                  await logger.log(
                                    level: 'warning',
                                    subsystem: 'webview',
                                    message: 'WebView is not mounted, cannot close',
                                    operationId: operationId,
                                    context: 'webview_login',
                                  );
                                }
                              }

                              // Set active mirror based on current host
                              try {
                                final current = await controller.getUrl();
                                if (current != null) {
                                  final host = current.host;
                                  if (host.isNotEmpty) {
                                    final db = AppDatabase().database;
                                    await EndpointManager(db)
                                        .setActiveEndpoint('https://$host');
                                    await StructuredLogger().log(
                                      level: 'info',
                                      subsystem: 'webview',
                                      message:
                                          'Updated active endpoint from WebView',
                                      extra: {'endpoint': 'https://$host'},
                                    );
                                  }
                                }
                              } on Exception catch (e) {
                                await StructuredLogger().log(
                                  level: 'debug',
                                  subsystem: 'webview',
                                  message:
                                      'Failed to update endpoint from WebView',
                                  cause: e.toString(),
                                );
                              }
                              _hideCloudflareOverlay();
                            }
                          },
                          shouldOverrideUrlLoading: (controller, nav) async {
                            final uri = nav.request.url;
                            if (uri == null) {
                              return NavigationActionPolicy.ALLOW;
                            }
                            final s = uri.toString();

                            // .torrent
                            if (s.endsWith('.torrent')) {
                              await _handleTorrentLink(uri);
                              return NavigationActionPolicy.CANCEL;
                            }
                            // magnet:
                            if (s.startsWith('magnet:')) {
                              await launchUrl(uri);
                              return NavigationActionPolicy.CANCEL;
                            }
                            // External domains: open in browser (optional)
                            if (!uri.host.contains('rutracker')) {
                              await launchUrl(uri);
                              return NavigationActionPolicy.CANCEL;
                            }
                            
                            // If returning to rutracker domain (e.g., from browser),
                            // sync cookies immediately
                            final rutrackerDomains = EndpointManager.getRutrackerDomains();
                            if (rutrackerDomains.contains(uri.host)) {
                              // Sync cookies when navigating to rutracker domain
                              // This helps when returning from browser after Cloudflare challenge
                              // _saveCookies() automatically syncs to DioClient
                              unawaited(Future.microtask(() async {
                                try {
                                  // Only sync if WebView is still mounted
                                  if (mounted) {
                                    await _saveCookies(callerContext: 'shouldOverrideUrlLoading');
                                  }
                                } on Exception {
                                  // Ignore sync errors during navigation
                                }
                              }));
                            }
                            
                            return NavigationActionPolicy.ALLOW;
                          },
                        ),
                ),
              ],
            ),
            // Cloudflare overlay
            if (_isCloudflareDetected)
              Positioned.fill(
                child: ColoredBox(
                  color: Colors.blue.shade50.withValues(alpha: 0.95),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Container(
                        padding: const EdgeInsets.all(24),
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius: BorderRadius.circular(16),
                          boxShadow: [
                            BoxShadow(
                              color: Colors.black.withValues(alpha: 0.1),
                              blurRadius: 10,
                              offset: const Offset(0, 4),
                            ),
                          ],
                        ),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(
                              Icons.security,
                              size: 64,
                              color: Colors.blue.shade600,
                            ),
                            const SizedBox(height: 16),
                            Text(
                              AppLocalizations.of(context)
                                      ?.securityVerificationInProgress ??
                                  'Security verification in progress - please wait...',
                              style: TextStyle(
                                fontSize: 20,
                                fontWeight: FontWeight.bold,
                                color: Colors.blue.shade800,
                              ),
                            ),
                            const SizedBox(height: 8),
                            Text(
                              AppLocalizations.of(context)?.cloudflareMessage ??
                                  'This site uses Cloudflare security checks. Please wait for the check to complete and interact with the page that opens if needed.',
                              textAlign: TextAlign.center,
                              style: TextStyle(
                                fontSize: 14,
                                color: Colors.grey.shade700,
                              ),
                            ),
                            const SizedBox(height: 24),
                            SizedBox(
                              width: 200,
                              child: LinearProgressIndicator(
                                backgroundColor: Colors.blue.shade100,
                                valueColor: AlwaysStoppedAnimation<Color>(
                                    Colors.blue.shade600),
                              ),
                            ),
                            // Note: Opening in external browser doesn't help with cookies
                            // as cookies from browser are not accessible to WebView/app
                            // Cloudflare challenge should be handled within WebView
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            // Error overlay
            if (_hasError)
              Positioned.fill(
                child: ColoredBox(
                  color: Colors.white,
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(
                        Icons.error_outline,
                        size: 64,
                        color: Colors.red.shade400,
                      ),
                      const SizedBox(height: 16),
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 24.0),
                        child: Text(
                          _errorMessage ??
                              (AppLocalizations.of(context)?.pageLoadError ??
                                  'An error occurred while loading the page'),
                          textAlign: TextAlign.center,
                          style: TextStyle(
                            fontSize: 16,
                            color: Colors.grey.shade700,
                          ),
                        ),
                      ),
                      const SizedBox(height: 24),
                      ElevatedButton(
                        onPressed: () async {
                          setState(() {
                            _hasError = false;
                            _errorMessage = null;
                            _retryCount =
                                0; // Reset retry count on manual retry
                          });

                          // Try to reload or switch endpoint if needed
                          try {
                            final db = AppDatabase().database;
                            final endpointManager = EndpointManager(db);
                            final currentUrl =
                                await _webViewController.getUrl();
                            final urlString =
                                currentUrl?.toString() ?? initialUrl;

                            // Try switching endpoint if current one failed
                            if (urlString != null && urlString.isNotEmpty) {
                              final switched = await endpointManager
                                  .trySwitchEndpoint(urlString);
                              if (switched) {
                                final newEndpoint =
                                    await endpointManager.getActiveEndpoint();
                                await _webViewController.loadUrl(
                                  urlRequest:
                                      URLRequest(url: WebUri(newEndpoint)),
                                );
                                await StructuredLogger().log(
                                  level: 'info',
                                  subsystem: 'webview',
                                  message:
                                      'Manually switched endpoint and retrying',
                                  extra: {'new_endpoint': newEndpoint},
                                );
                                return;
                              }
                            }
                          } on Exception {
                            // Continue with reload if switch fails
                          }

                          // Fallback to reload
                          await _webViewController.reload();
                        },
                        child: Text(AppLocalizations.of(context)
                                ?.retryButtonText ??
                            'Retry'),
                      ),
                      const SizedBox(height: 16),
                      TextButton(
                        onPressed: () {
                          setState(() {
                            _hasError = false;
                            _errorMessage = null;
                          });
                          if (initialUrl != null) {
                            _webViewController.loadUrl(
                                urlRequest:
                                    URLRequest(url: WebUri(initialUrl!)));
                          }
                        },
                        child: Text(AppLocalizations.of(context)
                                ?.goHomeButtonText ??
                            'Go to Home'),
                      ),
                    ],
                  ),
                ),
              ),
          ],
        ),
      );

  /// Starts waiting for Cloudflare challenge to complete.
  /// Similar to curl script: wait 2 seconds, then periodically check if challenge passed.
  void _startCloudflareWait(InAppWebViewController controller, String url) {
    // Cancel any existing timer
    _cloudflareCheckTimer?.cancel();
    
    // Record challenge start time
    _cloudflareChallengeStartTime = DateTime.now();
    
    // Wait 2 seconds first (like curl script), then check periodically
    Future.delayed(const Duration(seconds: 2), () {
      if (!mounted) return;
      
      // Increased interval to 10 seconds to avoid Cloudflare rate limiting
      _cloudflareCheckTimer = Timer.periodic(const Duration(seconds: 10), (timer) async {
        if (!mounted) {
          timer.cancel();
          return;
        }
        
        final elapsed = DateTime.now().difference(_cloudflareChallengeStartTime!);
        
        // Check if we've waited too long
        if (elapsed > _cloudflareWaitDuration) {
          timer.cancel();
          if (mounted) {
            // Force reload after timeout (challenge should be complete by now)
            await StructuredLogger().log(
              level: 'info',
              subsystem: 'webview',
              message: 'CloudFlare wait timeout, reloading page',
              context: 'webview_cloudflare',
              extra: {
                'wait_duration_ms': elapsed.inMilliseconds,
                'url': url,
              },
            );
            try {
              await controller.reload();
            } on Exception catch (e) {
              debugPrint('Failed to reload after Cloudflare wait: $e');
            }
          }
          return;
        }
        
        // Check if challenge has passed
        try {
          final html = await controller.getHtml();
          if (html != null && !_looksLikeCloudflare(html)) {
            // Challenge passed! Reload to get actual content
            timer.cancel();
            if (mounted) {
              await StructuredLogger().log(
                level: 'info',
                subsystem: 'webview',
                message: 'CloudFlare challenge completed, reloading page',
                context: 'webview_cloudflare',
                extra: {
                  'wait_duration_ms': elapsed.inMilliseconds,
                  'url': url,
                },
              );
              _hideCloudflareOverlay();
              await Future.delayed(const Duration(milliseconds: 500));
              await controller.reload();
            }
          }
        } on Exception {
          // Ignore errors during check, continue waiting
        }
      });
    });
  }

  void _showCloudflareHint() {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(AppLocalizations.of(context)
                ?.securityVerificationInProgress ??
            'Security verification in progress - please wait...'),
        duration: const Duration(seconds: 3),
      ),
    );
  }

  void _showCloudflareOverlay() {
    setState(() {
      _isCloudflareDetected = true;
    });
  }

  void _hideCloudflareOverlay() {
    setState(() {
      _isCloudflareDetected = false;
    });
  }

  Future<void> _handleTorrentLink(Uri uri) async {
    // Use a context-independent way to show the dialog
    final action = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(AppLocalizations.of(context)?.downloadTorrentTitle ??
            'Download Torrent'),
        content: Text(AppLocalizations.of(context)?.selectActionText ??
            'Select action:'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, 'open'),
            child: Text(AppLocalizations.of(context)?.openButtonText ?? 'Open'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, 'download'),
            child: Text(AppLocalizations.of(context)?.downloadButtonText ??
                'Download'),
          ),
        ],
      ),
    );

    if (action == 'open') {
      await launchUrl(uri);
    } else if (action == 'download') {
      // For download, we'll use the system's download mechanism
      // This typically requires additional permissions and file handling
      // For now, we'll show a message and let the user handle it manually
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(AppLocalizations.of(context)
                    ?.downloadInBrowserMessage ??
                'To download the file, please open the link in your browser'),
          ),
        );
      }
      await launchUrl(uri);
    }
  }
}
