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

import 'package:flutter/material.dart';
import 'package:flutter_cookie_bridge/session_manager.dart' as bridge_session;
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:jabook/core/endpoints/endpoint_manager.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/net/user_agent_manager.dart';
import 'package:jabook/core/services/cookie_service.dart';
import 'package:jabook/core/utils/safe_async.dart';
import 'package:jabook/data/db/app_database.dart';
import 'package:jabook/features/webview/webview_cloudflare_handler.dart';
import 'package:jabook/features/webview/webview_cookie_manager.dart';
import 'package:jabook/features/webview/webview_login_detector.dart';
import 'package:jabook/features/webview/webview_navigation_handler.dart';
import 'package:jabook/features/webview/webview_state_manager.dart';
import 'package:jabook/l10n/app_localizations.dart';
import 'package:url_launcher/url_launcher.dart';

/// A secure WebView widget for RuTracker login with modular architecture.
///
/// This widget provides a WebView interface for users to log in to RuTracker
/// and automatically extracts cookies for use with the HTTP client.
/// It uses modular handlers for cookies, navigation, login detection,
/// Cloudflare challenges, and state management.
class SecureRutrackerWebView extends StatefulWidget {
  /// Creates a new SecureRutrackerWebView instance.
  const SecureRutrackerWebView({super.key});

  @override
  State<SecureRutrackerWebView> createState() => _SecureRutrackerWebViewState();
}

/// State class for SecureRutrackerWebView widget.
class _SecureRutrackerWebViewState extends State<SecureRutrackerWebView> {
  InAppWebViewController? _webViewController;
  InAppWebViewSettings? _settings;

  bool _isLoading = true;
  bool _hasError = false;
  String? _errorMessage;
  bool _cloudflareDetected = false;
  bool _loginDetected = false;

  String? _initialUrl;
  bridge_session.SessionManager? _sessionManager;
  WebViewCookieManager? _cookieManager;
  WebViewStateManager? _stateManager;
  WebViewCloudflareHandler? _cloudflareHandler;

  final GlobalKey _webViewKey = GlobalKey();

  @override
  void initState() {
    super.initState();
    _initializeWebView();
  }

  Future<void> _initializeWebView() async {
    try {
      // Initialize cookie bridge
      _sessionManager = await WebViewCookieManager.initCookieBridge();

      // Resolve initial URL
      _initialUrl = await WebViewNavigationHandler.resolveInitialUrl();

      // Get user agent
      final userAgentManager = UserAgentManager();
      final userAgent = await userAgentManager.getUserAgent();

      // Configure WebView settings
      _settings = InAppWebViewSettings(
        userAgent: userAgent,
        mediaPlaybackRequiresUserGesture: false,
      );

      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
    } on Exception catch (e) {
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'webview',
        message: 'Failed to initialize WebView',
        context: 'webview_init',
        cause: e.toString(),
      );

      if (mounted) {
        setState(() {
          _isLoading = false;
          _hasError = true;
          _errorMessage = 'Failed to initialize WebView: $e';
        });
      }
    }
  }

  Future<void> _onWebViewCreated(InAppWebViewController controller) async {
    _webViewController = controller;

    if (_webViewController == null) return;

    // Initialize managers
    _cookieManager = WebViewCookieManager(
      webViewController: _webViewController!,
      sessionManager: _sessionManager,
      isMounted: () => mounted,
    );

    _stateManager = WebViewStateManager(
      webViewController: _webViewController!,
    );

    _cloudflareHandler = WebViewCloudflareHandler(
      context: context,
      isMounted: () => mounted,
      setCloudflareDetected: (detected) {
        if (mounted) {
          setState(() {
            _cloudflareDetected = detected;
          });
        }
      },
    );

    // Setup cookie handlers
    await _cookieManager!.setupJavaScriptCookieHandler();
    await _cookieManager!.injectCookieMonitorScript();

    // Restore cookies
    await _cookieManager!.restoreCookies(_initialUrl);

    // Restore WebView state
    await _stateManager!.restoreWebViewHistory();

    // Load initial URL
    if (_initialUrl != null) {
      await _webViewController!.loadUrl(
        urlRequest: URLRequest(url: WebUri(_initialUrl!)),
      );
    }
  }

  Future<NavigationActionPolicy> _onNavigationRequest(
    NavigationAction navigationAction,
  ) async {
    final url = navigationAction.request.url?.toString() ?? '';

    // Handle magnet links
    if (url.startsWith('magnet:')) {
      await _launchExternal(Uri.parse(url));
      return NavigationActionPolicy.CANCEL;
    }

    // Handle torrent files
    if (url.toLowerCase().endsWith('.torrent')) {
      await _handleTorrent(Uri.parse(url));
      return NavigationActionPolicy.CANCEL;
    }

    // Open external domains in browser
    final uri = Uri.tryParse(url);
    if (uri != null && !uri.host.contains('rutracker')) {
      await _launchExternal(uri);
      return NavigationActionPolicy.CANCEL;
    }

    return NavigationActionPolicy.ALLOW;
  }

  Future<void> _onLoadStart(
    InAppWebViewController controller,
    WebUri? url,
  ) async {
    if (mounted) {
      setState(() {
        _isLoading = true;
        _hasError = false;
      });
    }
  }

  Future<void> _onLoadStop(
    InAppWebViewController controller,
    WebUri? url,
  ) async {
    if (!mounted || _webViewController == null) return;

    try {
      // Check for Cloudflare challenge
      final html = await controller.getHtml();
      if (html != null && WebViewCloudflareHandler.looksLikeCloudflare(html)) {
        _cloudflareHandler?.showCloudflareOverlay();
        _cloudflareHandler?.showCloudflareHint();
        _cloudflareHandler?.startCloudflareWait(
          controller,
          url?.toString() ?? _initialUrl ?? '',
        );
      } else {
        _cloudflareHandler?.hideCloudflareOverlay();
      }

      // Check for login success
      final loginSuccess = await WebViewLoginDetector.checkLoginSuccess(
        controller,
      );

      if (loginSuccess && !_loginDetected) {
        _loginDetected = true;
        // Show success message immediately (don't wait for cookie sync)
        _showLoginSuccessHint();

        // Sync cookies asynchronously (don't block UI)
        // This ensures the success message appears quickly
        safeUnawaited(
          _syncCookiesAfterLoginAsync(url?.toString()),
          onError: (e, stack) {
            StructuredLogger().log(
              level: 'error',
              subsystem: 'cookies',
              message: 'Failed to sync cookies after login (async)',
              cause: e.toString(),
            );
          },
        );
      }

      // Save WebView state
      await _stateManager?.saveWebViewHistory();

      // Save cookies periodically (if not already saved during login)
      if (!loginSuccess) {
        await _cookieManager?.saveCookies(
          callerContext: 'page_load',
          initialUrl: url?.toString(),
        );
      }
    } on Exception catch (e) {
      await StructuredLogger().log(
        level: 'warning',
        subsystem: 'webview',
        message: 'Error in onLoadStop',
        context: 'webview_load_stop',
        cause: e.toString(),
      );
    }

    if (mounted) {
      setState(() {
        _isLoading = false;
      });
    }
  }

  Future<void> _onReceivedError(
    InAppWebViewController controller,
    WebResourceRequest request,
    WebResourceError error,
  ) async {
    final errorMessage = error.description;
    final errorType = error.type;

    // Handle non-critical errors (advertisements, blocked resources, etc.)
    if (errorMessage.contains('ERR_BLOCKED_BY_ORB') ||
        errorMessage.contains('ERR_BLOCKED_BY_CLIENT') ||
        errorMessage.contains('ERR_BLOCKED_BY_RESPONSE') ||
        errorMessage.contains(
            'ERR_NAME_NOT_RESOLVED') || // DNS errors from ads/external links
        errorMessage.contains('CORS') ||
        errorMessage.contains('Cross-Origin') ||
        errorMessage.contains('ERR_INSECURE_RESPONSE') ||
        errorMessage.contains('Mixed Content')) {
      // Log but don't show error to user - these are usually from ads or external resources
      final url = request.url.toString();
      if (!url.contains('rutracker')) {
        // Only log non-rutracker errors (ads, external links)
        await StructuredLogger().log(
          level: 'debug',
          subsystem: 'webview',
          message:
              'Non-critical WebView error (likely from ads/external resources)',
          context: 'webview_error',
          extra: {
            'error_message': errorMessage,
            'error_type': errorType.toString(),
            'url': url,
            'note': 'This error is from external resources and can be ignored',
          },
        );
      }
      return;
    }

    // Handle network errors
    if (errorMessage.contains('ERR_INTERNET_DISCONNECTED') ||
        errorMessage.contains('ERR_NETWORK_CHANGED')) {
      if (mounted) {
        setState(() {
          _hasError = true;
          _errorMessage = AppLocalizations.of(context)?.networkError ??
              'Network error. Please check your internet connection.';
        });
      }
      return;
    }

    // Handle other errors
    if (mounted) {
      setState(() {
        _hasError = true;
        _errorMessage = 'Loading error: $errorMessage';
      });
    }

    await StructuredLogger().log(
      level: 'error',
      subsystem: 'webview',
      message: 'WebView error',
      context: 'webview_error',
      extra: {
        'error_message': errorMessage,
        'error_type': errorType.toString(),
        'url': request.url.toString(),
      },
    );
  }

  Future<void> _onProgressChanged(
    InAppWebViewController controller,
    int progress,
  ) async {
    // Progress tracking if needed
  }

  Future<void> _saveCookiesAndClose() async {
    if (_webViewController == null || !mounted) return;

    try {
      // CRITICAL: If login was successful, force sync cookies before closing
      // This ensures cookies are available even if async sync hasn't completed
      if (_loginDetected) {
        await StructuredLogger().log(
          level: 'info',
          subsystem: 'cookies',
          message: 'Login was successful, forcing cookie sync before close',
          context: 'webview_close',
        );

        // Get current URL for cookie sync
        final currentUrl =
            await _webViewController!.getUrl().then((u) => u?.toString());

        // Save cookies to Android CookieManager first
        await _cookieManager?.saveCookies(
          callerContext: 'user_close_force_sync',
          initialUrl: currentUrl,
        );

        // Wait for CookieManager to process
        await Future.delayed(const Duration(milliseconds: 300));

        // Force sync cookies to Dio (synchronous, blocking)
        try {
          await _syncCookiesAfterLogin(currentUrl);
        } on Exception catch (e) {
          await StructuredLogger().log(
            level: 'warning',
            subsystem: 'cookies',
            message: 'Failed to force sync cookies on close, but continuing',
            context: 'webview_close',
            cause: e.toString(),
          );
        }
      } else {
        // Normal save if login wasn't detected
        await _cookieManager?.saveCookies(
          callerContext: 'user_close',
          initialUrl:
              await _webViewController!.getUrl().then((u) => u?.toString()),
        );
      }

      // Get cookie string for return value
      String? cookieString;
      try {
        final jsCookiesResult = await _webViewController!.evaluateJavascript(
          source: 'document.cookie',
        );
        if (jsCookiesResult != null) {
          cookieString = jsCookiesResult.toString();
        }
      } on Exception {
        // Ignore errors
      }

      if (mounted) {
        Navigator.of(context).pop<String>(cookieString);
      }
    } on Exception catch (e) {
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'webview',
        message: 'Failed to save cookies on close',
        context: 'webview_close',
        cause: e.toString(),
      );

      if (mounted) {
        Navigator.of(context).pop<String>();
      }
    }
  }

  void _showLoginSuccessHint() {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          AppLocalizations.of(context)?.loginSuccessMessage ??
              'Login successful!',
        ),
        backgroundColor: Colors.green,
        duration: const Duration(seconds: 3),
      ),
    );
  }

  Future<void> _launchExternal(Uri uri) async {
    await launchUrl(uri, mode: LaunchMode.externalApplication);
  }

  Future<void> _handleTorrent(Uri uri) async {
    final action = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(
          AppLocalizations.of(context)?.downloadTorrentTitle ??
              'Download Torrent',
        ),
        content: Text(
          AppLocalizations.of(context)?.selectActionText ?? 'Select action:',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, 'open'),
            child: Text(
              AppLocalizations.of(context)?.openButtonText ?? 'Open',
            ),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, 'download'),
            child: Text(
              AppLocalizations.of(context)?.downloadButtonText ?? 'Download',
            ),
          ),
        ],
      ),
    );

    if (!mounted) return;
    if (action == 'open' || action == 'download') {
      await _launchExternal(uri);
    }
  }

  Future<void> _refreshWebView() async {
    if (_webViewController != null) {
      await _webViewController!.reload();
    }
  }

  Future<void> _openInBrowser() async {
    if (_webViewController != null) {
      final url = await _webViewController!.getUrl();
      if (url != null) {
        await _launchExternal(Uri.parse(url.toString()));
      }
    }
  }

  /// Syncs cookies from CookieManager (Kotlin) to Dio after successful login.
  ///
  /// This is the new approach: get cookies directly from Android CookieManager
  /// (which WebView uses) and sync them to Dio CookieJar.
  ///
  /// This is the async version that doesn't block the UI.
  Future<void> _syncCookiesAfterLoginAsync(String? currentUrl) async {
    // First, save cookies to ensure they're synced to Android CookieManager
    if (_cookieManager != null) {
      await _cookieManager!.saveCookies(
        callerContext: 'login_success_async',
        initialUrl: currentUrl,
      );
      // CRITICAL: Increased delay to let CookieManager process cookies
      // Android CookieManager needs time to sync cookies from WebView
      await Future.delayed(const Duration(milliseconds: 500));
    }

    // Then sync to Dio with retries
    await _syncCookiesAfterLogin(currentUrl);
  }

  /// Syncs cookies from CookieManager (Kotlin) to Dio after successful login.
  ///
  /// This is the new approach: get cookies directly from Android CookieManager
  /// (which WebView uses) and sync them to Dio CookieJar.
  Future<void> _syncCookiesAfterLogin(String? currentUrl) async {
    final operationId =
        'cookie_sync_after_login_${DateTime.now().millisecondsSinceEpoch}';
    final startTime = DateTime.now();

    try {
      await StructuredLogger().log(
        level: 'info',
        subsystem: 'cookies',
        message: 'Syncing cookies after login using CookieService',
        operationId: operationId,
        context: 'webview_login_sync',
        extra: {'current_url': currentUrl},
      );

      // CRITICAL: Get the active endpoint first - we need to sync cookies for the active domain
      final db = AppDatabase().database;
      final endpointManager = EndpointManager(db);
      String activeEndpoint;
      try {
        activeEndpoint = await endpointManager.getActiveEndpoint();
      } on Exception {
        // Fallback if endpoint manager fails
        activeEndpoint = 'https://rutracker.me';
      }

      // Get base URL for RuTracker (use current URL from WebView or active endpoint)
      var baseUrl = currentUrl;
      if (baseUrl == null || !baseUrl.contains('rutracker')) {
        // Try to get from WebView
        try {
          final webViewUrl = await _webViewController?.getUrl();
          if (webViewUrl != null &&
              webViewUrl.toString().contains('rutracker')) {
            baseUrl = webViewUrl.toString();
          }
        } on Exception {
          // Ignore
        }

        // Fallback to active endpoint (most important!)
        if (baseUrl == null || !baseUrl.contains('rutracker')) {
          baseUrl = activeEndpoint;
        }
      }

      // Extract base URL (scheme + host)
      final uri = Uri.tryParse(baseUrl);
      if (uri == null) {
        await StructuredLogger().log(
          level: 'error',
          subsystem: 'cookies',
          message: 'Failed to parse URL for cookie sync',
          operationId: operationId,
          context: 'webview_login_sync',
          extra: {'base_url': baseUrl},
        );
        return;
      }

      final rutrackerBaseUrl = '${uri.scheme}://${uri.host}';
      final activeEndpointUri = Uri.parse(activeEndpoint);
      final activeEndpointBaseUrl =
          '${activeEndpointUri.scheme}://${activeEndpointUri.host}';

      await StructuredLogger().log(
        level: 'info',
        subsystem: 'cookies',
        message: 'Syncing cookies for active endpoint',
        operationId: operationId,
        context: 'webview_login_sync',
        extra: {
          'webview_url': baseUrl,
          'active_endpoint': activeEndpoint,
          'active_endpoint_base': activeEndpointBaseUrl,
          'rutracker_base_url': rutrackerBaseUrl,
        },
      );

      // CRITICAL: First, try to get cookies directly from InAppWebView CookieManager
      // This is more reliable than going through Android CookieManager
      if (_webViewController != null) {
        try {
          await StructuredLogger().log(
            level: 'info',
            subsystem: 'cookies',
            message: 'Getting cookies directly from InAppWebView CookieManager',
            operationId: operationId,
            context: 'webview_login_sync',
          );

          // Get cookies directly from InAppWebView CookieManager
          // CRITICAL: Only get cookies for the active endpoint (not all domains)
          final cookieManager = CookieManager.instance();
          final allCookies = <Cookie>[];

          // Try active endpoint FIRST (most important!)
          try {
            final activeEndpointUri = WebUri(activeEndpointBaseUrl);
            final activeCookies =
                await cookieManager.getCookies(url: activeEndpointUri);
            for (final cookie in activeCookies) {
              if (!allCookies.any((c) =>
                  c.name == cookie.name &&
                  (c.domain ?? '') == (cookie.domain ?? ''))) {
                allCookies.add(cookie);
              }
            }

            await StructuredLogger().log(
              level: 'info',
              subsystem: 'cookies',
              message: 'Got cookies from active endpoint',
              operationId: operationId,
              context: 'webview_login_sync',
              extra: {
                'active_endpoint': activeEndpointBaseUrl,
                'cookie_count': activeCookies.length,
              },
            );
          } on Exception catch (e) {
            await StructuredLogger().log(
              level: 'debug',
              subsystem: 'cookies',
              message: 'Failed to get cookies from active endpoint',
              operationId: operationId,
              context: 'webview_login_sync',
              cause: e.toString(),
              extra: {'active_endpoint': activeEndpointBaseUrl},
            );
          }

          // Also try WebView URL if different from active endpoint
          if (rutrackerBaseUrl != activeEndpointBaseUrl) {
            try {
              final webViewUri = WebUri(rutrackerBaseUrl);
              final webViewCookies =
                  await cookieManager.getCookies(url: webViewUri);
              for (final cookie in webViewCookies) {
                if (!allCookies.any((c) =>
                    c.name == cookie.name &&
                    (c.domain ?? '') == (cookie.domain ?? ''))) {
                  allCookies.add(cookie);
                }
              }

              await StructuredLogger().log(
                level: 'info',
                subsystem: 'cookies',
                message: 'Got additional cookies from WebView domain',
                operationId: operationId,
                context: 'webview_login_sync',
                extra: {
                  'webview_domain': rutrackerBaseUrl,
                  'cookie_count': webViewCookies.length,
                },
              );
            } on Exception {
              // Ignore
            }
          }

          if (allCookies.isNotEmpty) {
            await StructuredLogger().log(
              level: 'info',
              subsystem: 'cookies',
              message:
                  'Got cookies from InAppWebView CookieManager, syncing to Dio',
              operationId: operationId,
              context: 'webview_login_sync',
              extra: {
                'cookie_count': allCookies.length,
                'cookie_names': allCookies.map((c) => c.name).toList(),
                'active_endpoint': activeEndpointBaseUrl,
                'webview_domain': rutrackerBaseUrl,
              },
            );

            // Convert InAppWebView cookies to Dio Cookie objects and save directly
            final dioCookies = <io.Cookie>[];
            for (final cookie in allCookies) {
              final dioCookie = io.Cookie(cookie.name, cookie.value)
                ..path = cookie.path ?? '/'
                ..domain = cookie.domain
                ..secure = cookie.isSecure ?? true
                ..httpOnly = cookie.isHttpOnly ?? false;
              dioCookies.add(dioCookie);
            }

            // Save cookies directly to Dio CookieJar
            final jar = await DioClient.getCookieJar();
            if (jar != null && dioCookies.isNotEmpty) {
              // CRITICAL: Save cookies for the active endpoint FIRST (most important!)
              final activeUri = Uri.parse(activeEndpointBaseUrl);
              await jar.saveFromResponse(activeUri, dioCookies);

              await StructuredLogger().log(
                level: 'info',
                subsystem: 'cookies',
                message: 'Cookies saved for active endpoint',
                operationId: operationId,
                context: 'webview_login_sync',
                extra: {
                  'cookie_count': dioCookies.length,
                  'active_endpoint': activeEndpointBaseUrl,
                },
              );

              // Also save to WebView domain (if different from active endpoint)
              if (rutrackerBaseUrl != activeEndpointBaseUrl) {
                final webViewUri = Uri.parse(rutrackerBaseUrl);
                await jar.saveFromResponse(webViewUri, dioCookies);

                await StructuredLogger().log(
                  level: 'info',
                  subsystem: 'cookies',
                  message: 'Cookies also saved for WebView domain',
                  operationId: operationId,
                  context: 'webview_login_sync',
                  extra: {
                    'webview_domain': rutrackerBaseUrl,
                  },
                );
              }

              // Don't save to other domains - only active endpoint is needed

              await StructuredLogger().log(
                level: 'info',
                subsystem: 'cookies',
                message:
                    'Cookies synced directly from InAppWebView to Dio CookieJar',
                operationId: operationId,
                context: 'webview_login_sync',
                extra: {
                  'cookie_count': dioCookies.length,
                  'active_endpoint': activeEndpointBaseUrl,
                  'webview_domain': rutrackerBaseUrl,
                },
              );

              // Also save to SecureStorage for persistence (use active endpoint)
              final cookieHeader =
                  dioCookies.map((c) => '${c.name}=${c.value}').join('; ');
              await DioClient.saveCookiesToSecureStorage(
                  cookieHeader, activeEndpointBaseUrl);

              // Flush cookies to ensure they are saved
              await CookieService.flushCookies();

              return; // Success, exit early
            }
          } else {
            await StructuredLogger().log(
              level: 'warning',
              subsystem: 'cookies',
              message:
                  'No cookies found in InAppWebView CookieManager, trying JavaScript fallback',
              operationId: operationId,
              context: 'webview_login_sync',
            );

            // FALLBACK: Try to get cookies via JavaScript
            if (_webViewController != null) {
              try {
                final jsCookiesResult = await _webViewController!
                    .evaluateJavascript(source: 'document.cookie');
                if (jsCookiesResult != null) {
                  final jsCookiesString = jsCookiesResult.toString();
                  if (jsCookiesString.isNotEmpty) {
                    await StructuredLogger().log(
                      level: 'info',
                      subsystem: 'cookies',
                      message:
                          'Got cookies via JavaScript fallback, parsing and syncing',
                      operationId: operationId,
                      context: 'webview_login_sync',
                      extra: {
                        'cookie_string_length': jsCookiesString.length,
                        'cookie_string_preview': jsCookiesString.length > 200
                            ? '${jsCookiesString.substring(0, 200)}...'
                            : jsCookiesString,
                      },
                    );

                    // Parse JavaScript cookies and sync to Dio
                    final cookieParts = jsCookiesString.split('; ');
                    final dioCookies = <io.Cookie>[];
                    for (final part in cookieParts) {
                      final trimmed = part.trim();
                      if (trimmed.isEmpty) continue;

                      final equalIndex = trimmed.indexOf('=');
                      if (equalIndex <= 0) continue;

                      final name = trimmed.substring(0, equalIndex).trim();
                      final value = trimmed.substring(equalIndex + 1).trim();

                      if (name.isNotEmpty && value.isNotEmpty) {
                        final dioCookie = io.Cookie(name, value)
                          ..path = '/'
                          ..secure = true;
                        dioCookies.add(dioCookie);
                      }
                    }

                    if (dioCookies.isNotEmpty) {
                      final jar = await DioClient.getCookieJar();
                      if (jar != null) {
                        // CRITICAL: Save cookies for the active endpoint FIRST (most important!)
                        final activeUri = Uri.parse(activeEndpointBaseUrl);
                        await jar.saveFromResponse(activeUri, dioCookies);

                        await StructuredLogger().log(
                          level: 'info',
                          subsystem: 'cookies',
                          message:
                              'Cookies saved for active endpoint (JavaScript fallback)',
                          operationId: operationId,
                          context: 'webview_login_sync',
                          extra: {
                            'cookie_count': dioCookies.length,
                            'active_endpoint': activeEndpointBaseUrl,
                          },
                        );

                        // Also save to WebView domain (if different from active endpoint)
                        if (rutrackerBaseUrl != activeEndpointBaseUrl) {
                          final webViewUri = Uri.parse(rutrackerBaseUrl);
                          await jar.saveFromResponse(webViewUri, dioCookies);
                        }

                        // Don't save to other domains - only active endpoint is needed

                        await StructuredLogger().log(
                          level: 'info',
                          subsystem: 'cookies',
                          message:
                              'Cookies synced from JavaScript to Dio CookieJar',
                          operationId: operationId,
                          context: 'webview_login_sync',
                          extra: {
                            'cookie_count': dioCookies.length,
                            'active_endpoint': activeEndpointBaseUrl,
                            'webview_domain': rutrackerBaseUrl,
                          },
                        );

                        // Save to SecureStorage (use active endpoint)
                        final cookieHeader = dioCookies
                            .map((c) => '${c.name}=${c.value}')
                            .join('; ');
                        await DioClient.saveCookiesToSecureStorage(
                            cookieHeader, activeEndpointBaseUrl);

                        // Flush cookies to ensure they are saved
                        await CookieService.flushCookies();

                        return; // Success via JavaScript
                      }
                    }
                  }
                }
              } on Exception catch (e) {
                await StructuredLogger().log(
                  level: 'warning',
                  subsystem: 'cookies',
                  message: 'Failed to get cookies via JavaScript fallback',
                  operationId: operationId,
                  context: 'webview_login_sync',
                  cause: e.toString(),
                );
              }
            }
          }
        } on Exception catch (e) {
          await StructuredLogger().log(
            level: 'warning',
            subsystem: 'cookies',
            message:
                'Failed to get cookies from InAppWebView CookieManager, falling back to Android CookieManager',
            operationId: operationId,
            context: 'webview_login_sync',
            cause: e.toString(),
          );
        }
      }

      // FALLBACK: Try Android CookieManager if InAppWebView method failed
      // CRITICAL: First, ensure cookies are synced from InAppWebView to Android CookieManager
      // This is a fallback in case saveCookies() didn't run or didn't complete
      if (_cookieManager != null && _webViewController != null) {
        await StructuredLogger().log(
          level: 'info',
          subsystem: 'cookies',
          message:
              'Forcing cookie sync from InAppWebView to Android CookieManager before reading',
          operationId: operationId,
          context: 'webview_login_sync',
        );

        // Force save cookies to ensure they're in Android CookieManager
        await _cookieManager!.saveCookies(
          callerContext: 'sync_before_read',
          initialUrl: baseUrl,
        );

        // Wait for Android CookieManager to process
        await Future.delayed(const Duration(milliseconds: 500));
      }

      // Get cookies from CookieManager (Kotlin) with retries
      // Cookies may not be immediately available after login, so retry with delays
      // CRITICAL: Try active endpoint FIRST, then WebView URL
      String? cookieHeader;
      const maxRetries =
          8; // Increased retries for more reliable cookie retrieval
      const initialRetryDelay = Duration(milliseconds: 300);

      for (var attempt = 0; attempt < maxRetries; attempt++) {
        if (attempt > 0) {
          // Exponential backoff: 300ms, 500ms, 700ms, 1000ms, etc.
          final delayMs = initialRetryDelay.inMilliseconds + (attempt * 200);
          await Future.delayed(Duration(milliseconds: delayMs));
        }

        // Try active endpoint FIRST (most important!)
        final cookiesFromActiveEndpoint =
            await CookieService.getCookiesForUrl(activeEndpointBaseUrl);
        // Then try WebView URL
        final cookiesFromFullUrl =
            await CookieService.getCookiesForUrl(baseUrl);
        final cookiesFromBaseUrl =
            await CookieService.getCookiesForUrl(rutrackerBaseUrl);

        cookieHeader = cookiesFromActiveEndpoint ??
            cookiesFromFullUrl ??
            cookiesFromBaseUrl;

        await StructuredLogger().log(
          level: 'debug',
          subsystem: 'cookies',
          message: 'Attempting to get cookies from CookieManager',
          operationId: operationId,
          context: 'webview_login_sync',
          extra: {
            'attempt': attempt + 1,
            'max_retries': maxRetries,
            'active_endpoint': activeEndpointBaseUrl,
            'full_url': baseUrl,
            'base_url': rutrackerBaseUrl,
            'cookies_from_active_endpoint': cookiesFromActiveEndpoint != null &&
                cookiesFromActiveEndpoint.isNotEmpty,
            'cookies_from_full_url':
                cookiesFromFullUrl != null && cookiesFromFullUrl.isNotEmpty,
            'cookies_from_base_url':
                cookiesFromBaseUrl != null && cookiesFromBaseUrl.isNotEmpty,
            'active_endpoint_length': cookiesFromActiveEndpoint?.length ?? 0,
            'full_url_length': cookiesFromFullUrl?.length ?? 0,
            'base_url_length': cookiesFromBaseUrl?.length ?? 0,
          },
        );

        if (cookieHeader != null && cookieHeader.isNotEmpty) {
          await StructuredLogger().log(
            level: 'info',
            subsystem: 'cookies',
            message: 'Got cookies from CookieManager after retry',
            operationId: operationId,
            context: 'webview_login_sync',
            extra: {
              'attempt': attempt + 1,
              'url_used': cookiesFromActiveEndpoint != null
                  ? activeEndpointBaseUrl
                  : (cookiesFromFullUrl != null ? baseUrl : rutrackerBaseUrl),
              'cookie_header_length': cookieHeader.length,
              'cookie_header_preview': cookieHeader.length > 200
                  ? '${cookieHeader.substring(0, 200)}...'
                  : cookieHeader,
            },
          );
          break;
        }

        await StructuredLogger().log(
          level: 'debug',
          subsystem: 'cookies',
          message: 'No cookies found in CookieManager, retrying',
          operationId: operationId,
          context: 'webview_login_sync',
          extra: {
            'attempt': attempt + 1,
            'max_retries': maxRetries,
            'url_tried': baseUrl,
            'base_url_tried': rutrackerBaseUrl,
          },
        );
      }

      // No need to try other domains - only active endpoint is needed

      if (cookieHeader == null || cookieHeader.isEmpty) {
        await StructuredLogger().log(
          level: 'debug',
          subsystem: 'cookies',
          message:
              'No cookies found in CookieManager after login (cookies may be saved but not yet accessible via CookieManager.getCookie)',
          operationId: operationId,
          context: 'webview_login_sync',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
          extra: {
            'rutracker_url': rutrackerBaseUrl,
            'base_url': baseUrl,
            'note':
                'Cookies may be available later or saved via WebView CookieManager',
          },
        );
        return;
      }

      await StructuredLogger().log(
        level: 'info',
        subsystem: 'cookies',
        message: 'Got cookies from CookieManager, syncing to Dio',
        operationId: operationId,
        context: 'webview_login_sync',
        extra: {
          'rutracker_url': rutrackerBaseUrl,
          'cookie_header_length': cookieHeader.length,
          'cookie_header_preview': cookieHeader.length > 200
              ? '${cookieHeader.substring(0, 200)}...'
              : cookieHeader,
        },
      );

      // CRITICAL: Flush cookies to ensure they are saved to disk
      await CookieService.flushCookies();

      // CRITICAL: Sync cookies to Dio using active endpoint (not WebView URL)
      // This ensures cookies are available for the active endpoint used by search
      await DioClient.syncCookiesFromCookieService(
          cookieHeader, activeEndpointBaseUrl);

      // Save cookies to SecureStorage for auto-login on app restart (use active endpoint)
      await DioClient.saveCookiesToSecureStorage(
          cookieHeader, activeEndpointBaseUrl);

      // Flush again after syncing to ensure everything is persisted
      await CookieService.flushCookies();

      // CRITICAL: Verify cookies were saved correctly by loading them back
      await Future.delayed(const Duration(milliseconds: 200));
      final jar = await DioClient.getCookieJar();
      if (jar != null) {
        final activeUri = Uri.parse(activeEndpointBaseUrl);
        final searchUri = Uri.parse('$activeEndpointBaseUrl/forum/tracker.php');
        final savedCookies = await jar.loadForRequest(activeUri);
        final searchCookies = await jar.loadForRequest(searchUri);

        await StructuredLogger().log(
          level: savedCookies.isNotEmpty ? 'info' : 'warning',
          subsystem: 'cookies',
          message: 'Verified cookies in CookieJar after sync',
          operationId: operationId,
          context: 'webview_login_sync',
          extra: {
            'active_endpoint': activeEndpointBaseUrl,
            'saved_cookie_count': savedCookies.length,
            'search_cookie_count': searchCookies.length,
            'saved_cookie_names': savedCookies.map((c) => c.name).toList(),
            'search_cookie_names': searchCookies.map((c) => c.name).toList(),
            'has_session_cookies': savedCookies.any((c) =>
                c.name.toLowerCase().contains('session') ||
                c.name == 'bb_session' ||
                c.name == 'bb_data'),
          },
        );
      }

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await StructuredLogger().log(
        level: 'info',
        subsystem: 'cookies',
        message: 'Cookies synced to Dio after login',
        operationId: operationId,
        context: 'webview_login_sync',
        durationMs: duration,
        extra: {
          'active_endpoint': activeEndpointBaseUrl,
          'webview_domain': rutrackerBaseUrl,
          'cookie_header_length': cookieHeader.length,
        },
      );
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'cookies',
        message: 'Failed to sync cookies after login',
        operationId: operationId,
        context: 'webview_login_sync',
        durationMs: duration,
        cause: e.toString(),
      );
    }
  }

  @override
  void dispose() {
    _cloudflareHandler?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
          title: Text(
            AppLocalizations.of(context)?.webViewTitle ?? 'RuTracker',
          ),
          automaticallyImplyLeading: false,
          actions: [
            IconButton(
              icon: const Icon(Icons.refresh),
              onPressed: _refreshWebView,
            ),
            IconButton(
              icon: const Icon(Icons.open_in_browser),
              onPressed: _openInBrowser,
            ),
          ],
        ),
        body: Stack(
          children: [
            Column(
              children: [
                // Instructions banner
                Container(
                  padding: const EdgeInsets.all(12.0),
                  color: Colors.blue.shade50,
                  child: Row(
                    children: [
                      const Icon(
                        Icons.info_outline,
                        color: Colors.blue,
                      ),
                      const SizedBox(width: 8.0),
                      Expanded(
                        child: Text(
                          AppLocalizations.of(context)
                                  ?.webViewLoginInstruction ??
                              'Please log in to RuTracker. After successful login, click "Done".',
                          style: const TextStyle(
                            color: Colors.blue,
                            fontSize: 14.0,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                // WebView
                Expanded(
                  child: _initialUrl == null
                      ? const Center(child: CircularProgressIndicator())
                      : InAppWebView(
                          key: _webViewKey,
                          initialUrlRequest: URLRequest(
                            url: WebUri(_initialUrl!),
                          ),
                          initialSettings: _settings,
                          onWebViewCreated: _onWebViewCreated,
                          onLoadStart: _onLoadStart,
                          onLoadStop: _onLoadStop,
                          onReceivedError: _onReceivedError,
                          onProgressChanged: _onProgressChanged,
                          shouldOverrideUrlLoading:
                              (controller, navigationAction) async {
                            final policy =
                                await _onNavigationRequest(navigationAction);
                            return policy;
                          },
                        ),
                ),
              ],
            ),
            // Loading overlay
            if (_isLoading)
              Positioned.fill(
                child: ColoredBox(
                  color: Colors.white.withValues(alpha: 0.8),
                  child: Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        const CircularProgressIndicator(),
                        const SizedBox(height: 16),
                        Text(
                          AppLocalizations.of(context)?.loading ?? 'Loading...',
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            // Cloudflare overlay
            if (_cloudflareDetected)
              Positioned.fill(
                child: ColoredBox(
                  color: Colors.white.withValues(alpha: 0.9),
                  child: Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        const CircularProgressIndicator(),
                        const SizedBox(height: 16),
                        Text(
                          AppLocalizations.of(context)
                                  ?.securityVerificationInProgress ??
                              'Security verification in progress - please wait...',
                          textAlign: TextAlign.center,
                          style: const TextStyle(fontSize: 16),
                        ),
                      ],
                    ),
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
                              (AppLocalizations.of(context)?.networkError ??
                                  'An error occurred'),
                          textAlign: TextAlign.center,
                          style: TextStyle(
                            fontSize: 16,
                            color: Colors.grey.shade700,
                          ),
                        ),
                      ),
                      const SizedBox(height: 24),
                      ElevatedButton(
                        onPressed: _refreshWebView,
                        child: Text(
                          AppLocalizations.of(context)?.retryButtonText ??
                              'Retry',
                        ),
                      ),
                    ],
                  ),
                ),
              ),
          ],
        ),
        floatingActionButton: FloatingActionButton.extended(
          onPressed: _saveCookiesAndClose,
          icon: const Icon(Icons.check),
          label: Text(
            AppLocalizations.of(context)?.doneButtonText ?? 'Done',
          ),
          backgroundColor: Colors.green,
        ),
      );
}
