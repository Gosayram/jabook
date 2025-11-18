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
import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
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
class _SecureRutrackerWebViewState extends State<SecureRutrackerWebView> {
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

  @override
  void initState() {
    super.initState();
    // Set fallback URL immediately to prevent null issues
    initialUrl = EndpointManager.getPrimaryFallbackEndpoint();
    // Get User-Agent for legitimate connection (required for Cloudflare)
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
    // Cancel Cloudflare check timer
    _cloudflareCheckTimer?.cancel();
    // Save cookies one final time before disposing
    _saveCookies().then((_) async {
      // Sync cookies to DioClient after saving
      try {
        await DioClient.syncCookiesFromWebView();
      } on Exception {
        // Ignore sync errors on dispose
      }
    });
    _saveWebViewHistory();
    super.dispose();
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
          await InternetAddress.lookup(uri.host)
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
        await InternetAddress.lookup(uri.host)
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

  Future<void> _saveCookies() async {
    final operationId =
        'webview_cookie_save_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      if (initialUrl == null) {
        await logger.log(
          level: 'debug',
          subsystem: 'cookies',
          message: 'Cannot save cookies: initialUrl is null',
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
          'initial_url': initialUrl,
        },
      );

      final cookies =
          await CookieManager.instance().getCookies(url: WebUri(initialUrl!));

      await logger.log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'Retrieved cookies from WebView',
        operationId: operationId,
        context: 'webview_cookie_save',
        extra: {
          'cookie_count': cookies.length,
          'cookies': cookies
              .map((c) => {
                    'name': c.name,
                    'domain': c.domain ?? '<null>',
                    'path': c.path ?? '/',
                    'is_secure': c.isSecure ?? false,
                    'is_http_only': c.isHttpOnly ?? false,
                  })
              .toList(),
        },
      );

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

  bool _looksLikeCloudflare(String html) {
    final h = html.toLowerCase();
    return h.contains('checking your browser') ||
        h.contains('please enable javascript') ||
        h.contains('attention required') ||
        h.contains('cf-chl-bypass') ||
        h.contains('cloudflare') ||
        h.contains('ddos-guard') ||
        h.contains('just a moment') ||
        h.contains('verifying you are human') ||
        h.contains('security check') ||
        h.contains('cf-browser-verification') ||
        h.contains('cf-challenge-running');
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
                  child: initialUrl == null || _userAgent == null
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
                          onWebViewCreated: (controller) {
                            _webViewController = controller;
                            _hasError =
                                false; // Reset error state when WebView is recreated

                            // Log WebView creation
                            StructuredLogger().log(
                              level: 'info',
                              subsystem: 'webview',
                              message: 'WebView created successfully',
                              extra: {
                                'initial_url': initialUrl,
                                'user_agent': _userAgent?.substring(0, 50) ?? 'null',
                              },
                            );
                            
                            // Ensure WebView loads the URL if it wasn't loaded automatically
                            // This is a safety measure in case initialUrlRequest didn't work
                            if (initialUrl != null && initialUrl!.isNotEmpty) {
                              Future.microtask(() async {
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
                              });
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

                            if (html != null && _looksLikeCloudflare(html)) {
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
                                },
                              );
                              _showCloudflareHint();
                              // Show a more prominent Cloudflare indicator
                              _showCloudflareOverlay();
                              
                              // Start waiting for Cloudflare challenge to complete
                              // Similar to curl script: wait 2 seconds, then check if challenge passed
                              _startCloudflareWait(controller, urlString);
                            } else {
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
                              await _saveCookies();

                              // Check if user successfully logged in by detecting profile/index pages
                              final currentUrl = await controller.getUrl();
                              final urlString =
                                  currentUrl?.toString().toLowerCase() ?? '';
                              final isLoginSuccess = urlString
                                      .contains('profile.php') ||
                                  (urlString.contains('index.php') &&
                                      !urlString.contains('login')) ||
                                  (html != null &&
                                      (html.toLowerCase().contains('выход') ||
                                          html
                                              .toLowerCase()
                                              .contains('logout') ||
                                          html
                                              .toLowerCase()
                                              .contains('личный кабинет')));

                              if (isLoginSuccess) {
                                await logger.log(
                                  level: 'info',
                                  subsystem: 'webview',
                                  message: 'WebView login appears successful',
                                  operationId: operationId,
                                  context: 'webview_login',
                                  durationMs: duration,
                                  extra: {
                                    'url': urlString,
                                    'has_profile_content': html != null &&
                                        html.toLowerCase().contains('profile'),
                                    'html_size': htmlSize,
                                  },
                                );
                              }

                              // Automatically sync cookies to DioClient after saving
                              try {
                                await logger.log(
                                  level: 'debug',
                                  subsystem: 'cookies',
                                  message:
                                      'Syncing cookies from WebView to DioClient',
                                  operationId: operationId,
                                  context: 'webview_cookie_sync',
                                  extra: {
                                    'url': urlString,
                                  },
                                );

                                await DioClient.syncCookiesFromWebView();

                                await logger.log(
                                  level: 'info',
                                  subsystem: 'cookies',
                                  message:
                                      'Cookies synced from WebView to DioClient',
                                  operationId: operationId,
                                  context: 'webview_cookie_sync',
                                  durationMs: duration,
                                  extra: {
                                    'url': urlString,
                                  },
                                );
                              } on Exception catch (e) {
                                await logger.log(
                                  level: 'warning',
                                  subsystem: 'cookies',
                                  message:
                                      'Failed to sync cookies to DioClient',
                                  operationId: operationId,
                                  context: 'webview_cookie_sync',
                                  durationMs: duration,
                                  cause: e.toString(),
                                  extra: {
                                    'url': urlString,
                                    'stack_trace': (e is Error)
                                        ? (e as Error).stackTrace.toString()
                                        : null,
                                  },
                                );
                                debugPrint(
                                    'Failed to sync cookies to DioClient: $e');
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
                            const SizedBox(height: 16),
                            TextButton.icon(
                              onPressed: () async {
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
                                    urlString =
                                        EndpointManager.getPrimaryFallbackEndpoint();
                                  }

                                  final uri = Uri.tryParse(urlString);
                                  if (uri != null &&
                                      uri.hasScheme &&
                                      uri.hasAuthority) {
                                    try {
                                      await launchUrl(
                                        uri,
                                        mode: LaunchMode.externalApplication,
                                      );
                                    } on Exception catch (launchError) {
                                      await StructuredLogger().log(
                                        level: 'error',
                                        subsystem: 'webview',
                                        message: 'Failed to launch URL in browser',
                                        cause: launchError.toString(),
                                        extra: {'url': urlString},
                                      );
                                      debugPrint('Failed to launch URL: $launchError');
                                    }
                                  } else {
                                    await StructuredLogger().log(
                                      level: 'warning',
                                      subsystem: 'webview',
                                      message: 'Invalid URL format for browser',
                                      extra: {'url': urlString},
                                    );
                                  }
                                } on Exception catch (e) {
                                  await StructuredLogger().log(
                                    level: 'error',
                                    subsystem: 'webview',
                                    message: 'Failed to open in browser',
                                    cause: e.toString(),
                                  );
                                  debugPrint('Failed to open in browser: $e');
                                }
                              },
                              icon: const Icon(Icons.open_in_browser),
                              label: Text(AppLocalizations.of(context)
                                      ?.openInBrowserButton ??
                                  'Open in Browser'),
                            ),
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
      
      // Start periodic checks every 1 second
      _cloudflareCheckTimer = Timer.periodic(const Duration(seconds: 1), (timer) async {
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
