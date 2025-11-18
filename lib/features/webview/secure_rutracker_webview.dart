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

import 'package:flutter/material.dart';
import 'package:flutter_cookie_bridge/session_manager.dart' as bridge_session;
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/net/user_agent_manager.dart';
import 'package:jabook/core/services/cookie_service.dart';
import 'package:jabook/features/webview/permission_wrapper.dart';
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
  State<SecureRutrackerWebView> createState() =>
      _SecureRutrackerWebViewState();
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
        _showLoginSuccessHint();
        
        // NEW APPROACH: Get cookies from CookieManager (Kotlin) and sync to Dio
        await _syncCookiesAfterLogin(url?.toString());
      }
      
      // Save WebView state
      await _stateManager?.saveWebViewHistory();
      
      // Save cookies periodically
      await _cookieManager?.saveCookies(
        callerContext: 'page_load',
        initialUrl: url?.toString(),
      );
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
    
    // Handle non-critical errors
    if (errorMessage.contains('ERR_BLOCKED_BY_ORB') ||
        errorMessage.contains('ERR_BLOCKED_BY_CLIENT') ||
        errorMessage.contains('ERR_BLOCKED_BY_RESPONSE') ||
        errorMessage.contains('CORS') ||
        errorMessage.contains('Cross-Origin') ||
        errorMessage.contains('ERR_INSECURE_RESPONSE') ||
        errorMessage.contains('Mixed Content')) {
      return;
    }
    
    // Handle network errors
    if (errorMessage.contains('ERR_INTERNET_DISCONNECTED') ||
        errorMessage.contains('ERR_NETWORK_CHANGED')) {
      if (mounted) {
        setState(() {
          _hasError = true;
          _errorMessage = AppLocalizations.of(context)
                  ?.networkError ??
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
      // Save cookies one final time
      await _cookieManager?.saveCookies(
        callerContext: 'user_close',
        initialUrl: await _webViewController!.getUrl().then((u) => u?.toString()),
      );
      
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
          AppLocalizations.of(context)?.selectActionText ??
              'Select action:',
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
  Future<void> _syncCookiesAfterLogin(String? currentUrl) async {
    final operationId = 'cookie_sync_after_login_${DateTime.now().millisecondsSinceEpoch}';
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

      // Get base URL for RuTracker (use current URL or default)
      var baseUrl = currentUrl;
      if (baseUrl == null || !baseUrl.contains('rutracker')) {
        // Try to get from WebView
        try {
          final webViewUrl = await _webViewController?.getUrl();
          if (webViewUrl != null && webViewUrl.toString().contains('rutracker')) {
            baseUrl = webViewUrl.toString();
          }
        } on Exception {
          // Ignore
        }
        
        // Fallback to default RuTracker URL
        if (baseUrl == null || !baseUrl.contains('rutracker')) {
          baseUrl = 'https://rutracker.org';
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

      // Get cookies from CookieManager (Kotlin) with retries
      // Cookies may not be immediately available after login, so retry with delays
      String? cookieHeader;
      const maxRetries = 5;
      const retryDelay = Duration(milliseconds: 500);
      
      for (var attempt = 0; attempt < maxRetries; attempt++) {
        if (attempt > 0) {
          await Future.delayed(retryDelay);
        }
        
        // Try with full URL first, then base URL
        cookieHeader = await CookieService.getCookiesForUrl(baseUrl) ??
            await CookieService.getCookiesForUrl(rutrackerBaseUrl);
        
        if (cookieHeader != null && cookieHeader.isNotEmpty) {
          await StructuredLogger().log(
            level: 'info',
            subsystem: 'cookies',
            message: 'Got cookies from CookieManager after retry',
            operationId: operationId,
            context: 'webview_login_sync',
            extra: {
              'attempt': attempt + 1,
              'url_used': baseUrl,
              'cookie_header_length': cookieHeader.length,
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
          },
        );
      }

      if (cookieHeader == null || cookieHeader.isEmpty) {
        // Try all RuTracker domains as fallback
        final rutrackerDomains = ['rutracker.me', 'rutracker.net', 'rutracker.org'];
        for (final domain in rutrackerDomains) {
          if (domain == uri.host) continue; // Already tried
          
          final domainUrl = 'https://$domain';
          cookieHeader = await CookieService.getCookiesForUrl(domainUrl);
          
          if (cookieHeader != null && cookieHeader.isNotEmpty) {
            await StructuredLogger().log(
              level: 'info',
              subsystem: 'cookies',
              message: 'Got cookies from CookieManager for fallback domain',
              operationId: operationId,
              context: 'webview_login_sync',
              extra: {
                'domain': domain,
                'cookie_header_length': cookieHeader.length,
              },
            );
            break;
          }
        }
      }

      if (cookieHeader == null || cookieHeader.isEmpty) {
        await StructuredLogger().log(
          level: 'debug',
          subsystem: 'cookies',
          message: 'No cookies found in CookieManager after login (cookies may be saved but not yet accessible via CookieManager.getCookie)',
          operationId: operationId,
          context: 'webview_login_sync',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
          extra: {
            'rutracker_url': rutrackerBaseUrl,
            'base_url': baseUrl,
            'note': 'Cookies may be available later or saved via WebView CookieManager',
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

      // Sync cookies to Dio using DioClient
      await DioClient.syncCookiesFromCookieService(cookieHeader, rutrackerBaseUrl);

      // Save cookies to SecureStorage for auto-login on app restart
      await DioClient.saveCookiesToSecureStorage(cookieHeader, rutrackerBaseUrl);

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await StructuredLogger().log(
        level: 'info',
        subsystem: 'cookies',
        message: 'Cookies synced to Dio after login',
        operationId: operationId,
        context: 'webview_login_sync',
        durationMs: duration,
        extra: {
          'rutracker_url': rutrackerBaseUrl,
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
  Widget build(BuildContext context) => PermissionWrapper(
      onPermissionsGranted: () {
        // Permissions granted, WebView can be used
      },
      child: Scaffold(
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
                          shouldOverrideUrlLoading: (controller, navigationAction) async {
                            final policy = await _onNavigationRequest(navigationAction);
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
      ),
    );
}

