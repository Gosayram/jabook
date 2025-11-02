import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:jabook/core/endpoints/endpoint_manager.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/core/net/dio_client.dart';
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

  // State restoration
  final String _webViewStateKey = 'rutracker_webview_state';

  // Error handling
  bool _hasError = false;
  String? _errorMessage;
  int _retryCount = 0;
  static const int _maxRetries = 2;

  // Cloudflare detection
  bool _isCloudflareDetected = false;

  @override
  void initState() {
    super.initState();
    _resolveInitialUrl().then((_) {
      _restoreCookies();
      setState(() {});
    });
    _restoreWebViewHistory();
  }

  @override
  void dispose() {
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
        } else {
          // Switch failed, use fallback
          endpoint = 'https://rutracker.net';
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
      } on Exception {
        // Even fallback failed, but use it anyway (WebView will show error)
        await StructuredLogger().log(
          level: 'warning',
          subsystem: 'webview',
          message: 'Endpoint validation failed, using anyway',
          extra: {'endpoint': endpoint},
        );
        initialUrl = endpoint;
      }
    } on Exception {
      // If all fails, use hardcoded fallback
      initialUrl = 'https://rutracker.net';
    }
  }

  Future<void> _restoreCookies() async {
    final prefs = await SharedPreferences.getInstance();
    final cookieJson = prefs.getString('rutracker_cookies_v1');
    if (cookieJson != null) {
      try {
        final cookies = jsonDecode(cookieJson) as List<dynamic>;
        for (final cookie in cookies) {
          await CookieManager.instance().setCookie(
            url: WebUri(initialUrl!),
            name: cookie['name'],
            value: cookie['value'],
            domain: cookie['domain'],
            path: cookie['path'],
            // Note: flutter_inappwebview doesn't support setting expires directly
            // Cookies will expire based on their natural expiration
          );
        }
      } on Exception {
        // If cookie restoration fails, clear old cookies and start fresh
        await CookieManager.instance().deleteCookies(
          url: WebUri(initialUrl!),
          domain: Uri.parse(initialUrl!).host,
        );
      }
    }
  }

  Future<void> _saveCookies() async {
    try {
      if (initialUrl == null) return;
      final cookies =
          await CookieManager.instance().getCookies(url: WebUri(initialUrl!));
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
    } on Exception catch (e) {
      // If cookie saving fails, log but don't crash
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
                try {
                  final url = await _webViewController.getUrl();
                  String? urlString;

                  if (url != null) {
                    urlString = url.toString();
                  } else if (initialUrl != null) {
                    urlString = initialUrl;
                  }

                  if (urlString != null && urlString.isNotEmpty) {
                    // Validate and parse URL
                    final uri = Uri.tryParse(urlString);
                    if (uri != null && uri.hasScheme && uri.hasAuthority) {
                      if (await canLaunchUrl(uri)) {
                        await launchUrl(uri,
                            mode: LaunchMode.externalApplication);
                      } else {
                        if (!mounted) return;
                        // ignore: use_build_context_synchronously
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(
                            content: Text('Не удалось открыть в браузере'),
                            duration: Duration(seconds: 2),
                          ),
                        );
                      }
                    } else {
                      if (!mounted) return;
                      // ignore: use_build_context_synchronously
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text('Неверный формат URL: $urlString'),
                          duration: const Duration(seconds: 3),
                        ),
                      );
                      await StructuredLogger().log(
                        level: 'warning',
                        subsystem: 'webview',
                        message: 'Invalid URL format for browser',
                        extra: {'url': urlString},
                      );
                    }
                  } else {
                    if (!mounted) return;
                    // ignore: use_build_context_synchronously
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Text('URL недоступен'),
                        duration: Duration(seconds: 2),
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
                  // ignore: use_build_context_synchronously
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text('Ошибка: $e'),
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
                            'Повторная попытка подключения ($_retryCount/$_maxRetries)...',
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
                              'Security Check in Progress',
                              style: TextStyle(
                                color: Colors.blue.shade800,
                                fontSize: 14.0,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              'RuTracker uses Cloudflare protection. Please wait for the verification to complete (5-10 seconds).',
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
                  child: initialUrl == null
                      ? const Center(child: CircularProgressIndicator())
                      : InAppWebView(
                          initialUrlRequest:
                              URLRequest(url: WebUri(initialUrl!)),
                          initialSettings: InAppWebViewSettings(
                            useShouldOverrideUrlLoading: true,
                            sharedCookiesEnabled: true,
                            allowsInlineMediaPlayback: true,
                            mediaPlaybackRequiresUserGesture: false,
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
                              extra: {'initial_url': initialUrl},
                            );
                          },
                          onProgressChanged: (controller, p) {
                            setState(() => progress = p / 100.0);
                          },
                          onLoadStart: (controller, url) async {
                            // Log when page starts loading
                            await StructuredLogger().log(
                              level: 'info',
                              subsystem: 'webview',
                              message: 'WebView page load started',
                              extra: {'url': url?.toString()},
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

                            // Log error
                            await StructuredLogger().log(
                              level: 'error',
                              subsystem: 'webview',
                              message: 'WebView received error',
                              cause: desc,
                              extra: {
                                'url': request.url.toString(),
                                'error_type': error.type.toString(),
                                'is_main_frame': request.isForMainFrame ?? true,
                              },
                            );

                            // Try to switch endpoint on network errors (DNS, connection timeout)
                            // Check error description for network-related errors
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

                            if (isNetworkError) {
                              final db = AppDatabase().database;
                              final endpointManager = EndpointManager(db);
                              try {
                                final currentUrl = request.url.toString();
                                if (currentUrl.isNotEmpty) {
                                  final switched = await endpointManager
                                      .trySwitchEndpoint(currentUrl);
                                  if (switched) {
                                    final newEndpoint = await endpointManager
                                        .getActiveEndpoint();
                                    await StructuredLogger().log(
                                      level: 'info',
                                      subsystem: 'webview',
                                      message:
                                          'Switched endpoint due to WebView error',
                                      extra: {
                                        'old_url': currentUrl,
                                        'new_endpoint': newEndpoint,
                                      },
                                    );
                                    await controller.loadUrl(
                                      urlRequest:
                                          URLRequest(url: WebUri(newEndpoint)),
                                    );
                                    return; // Don't show error if retrying
                                  }
                                }
                              } on Exception {
                                // Continue to show error
                              }
                            }

                            // Auto-retry on network errors (up to max retries)
                            if (isNetworkError && _retryCount < _maxRetries) {
                              _retryCount++;
                              await StructuredLogger().log(
                                level: 'info',
                                subsystem: 'webview',
                                message:
                                    'Auto-retrying WebView load after error',
                                extra: {
                                  'retry_count': _retryCount,
                                  'max_retries': _maxRetries,
                                  'error': desc,
                                },
                              );

                              // Wait a bit before retry
                              await Future.delayed(
                                  Duration(seconds: 2 * _retryCount));

                              // Try to reload or switch endpoint
                              try {
                                final db = AppDatabase().database;
                                final endpointManager = EndpointManager(db);
                                final currentUrl = request.url.toString();

                                if (currentUrl.isNotEmpty) {
                                  final switched = await endpointManager
                                      .trySwitchEndpoint(currentUrl);
                                  if (switched) {
                                    final newEndpoint = await endpointManager
                                        .getActiveEndpoint();
                                    await controller.loadUrl(
                                      urlRequest:
                                          URLRequest(url: WebUri(newEndpoint)),
                                    );
                                    return; // Don't show error if retrying
                                  }
                                }
                              } on Exception {
                                // Continue to show error
                              }

                              // Fallback to reload
                              await controller.reload();
                              return; // Don't show error during retry
                            }

                            // Reset retry count on successful load
                            _retryCount = 0;

                            if (mounted) {
                              setState(() {
                                _hasError = true;
                                _errorMessage = 'Ошибка загрузки: $desc';
                              });
                            }
                          },
                          onReceivedHttpError:
                              (controller, request, errorResponse) async {
                            // Show HTTP error only for main frame
                            if (!(request.isForMainFrame ?? true)) return;

                            // Log HTTP error
                            final statusCode = errorResponse.statusCode ?? 0;
                            await StructuredLogger().log(
                              level: statusCode >= 500 ? 'error' : 'warning',
                              subsystem: 'webview',
                              message: 'WebView received HTTP error',
                              extra: {
                                'url': request.url.toString(),
                                'status_code': statusCode,
                                'status_message': errorResponse.reasonPhrase,
                              },
                            );

                            // For 5xx errors, try endpoint switch
                            if (statusCode >= 500 && initialUrl != null) {
                              final db = AppDatabase().database;
                              final endpointManager = EndpointManager(db);
                              try {
                                final currentUrl = request.url.toString();
                                if (currentUrl.isNotEmpty) {
                                  final switched = await endpointManager
                                      .trySwitchEndpoint(currentUrl);
                                  if (switched) {
                                    final newEndpoint = await endpointManager
                                        .getActiveEndpoint();
                                    await controller.loadUrl(
                                      urlRequest:
                                          URLRequest(url: WebUri(newEndpoint)),
                                    );
                                    return; // Don't show error if retrying
                                  }
                                }
                              } on Exception {
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
                                _errorMessage = 'HTTP ошибка: $statusCode';
                              });
                            }
                          },
                          onLoadStop: (controller, url) async {
                            // Reset retry count and clear error state on successful load
                            if (mounted) {
                              setState(() {
                                _retryCount = 0;
                                _hasError = false;
                                _errorMessage = null;
                              });
                            }

                            // Log successful page load (already logged in onLoadStop above, but this is the existing handler)
                            await StructuredLogger().log(
                              level: 'info',
                              subsystem: 'webview',
                              message: 'WebView page load completed',
                              extra: {'url': url?.toString()},
                            );

                            final html = await controller.getHtml();
                            if (html != null && _looksLikeCloudflare(html)) {
                              await StructuredLogger().log(
                                level: 'info',
                                subsystem: 'webview',
                                message:
                                    'CloudFlare challenge detected on page',
                              );
                              _showCloudflareHint();
                              // Show a more prominent Cloudflare indicator
                              _showCloudflareOverlay();
                            } else {
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
                                await StructuredLogger().log(
                                  level: 'info',
                                  subsystem: 'webview',
                                  message: 'WebView login appears successful',
                                  extra: {
                                    'url': urlString,
                                    'has_profile_content': html != null &&
                                        html.toLowerCase().contains('profile'),
                                  },
                                );
                              }

                              // Automatically sync cookies to DioClient after saving
                              try {
                                await DioClient.syncCookiesFromWebView();
                                await StructuredLogger().log(
                                  level: 'info',
                                  subsystem: 'webview',
                                  message:
                                      'Cookies synced from WebView to DioClient',
                                );
                              } on Exception catch (e) {
                                await StructuredLogger().log(
                                  level: 'warning',
                                  subsystem: 'webview',
                                  message:
                                      'Failed to sync cookies to DioClient',
                                  cause: e.toString(),
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
                              'Security Verification',
                              style: TextStyle(
                                fontSize: 20,
                                fontWeight: FontWeight.bold,
                                color: Colors.blue.shade800,
                              ),
                            ),
                            const SizedBox(height: 8),
                            Text(
                              'RuTracker is verifying your browser.\nPlease wait 5-10 seconds for the check to complete.',
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
                                  final url = await _webViewController.getUrl();
                                  String? urlString;

                                  if (url != null) {
                                    urlString = url.toString();
                                  } else if (initialUrl != null) {
                                    urlString = initialUrl;
                                  }

                                  if (urlString != null &&
                                      urlString.isNotEmpty) {
                                    final uri = Uri.tryParse(urlString);
                                    if (uri != null &&
                                        uri.hasScheme &&
                                        uri.hasAuthority) {
                                      if (await canLaunchUrl(uri)) {
                                        await launchUrl(uri,
                                            mode:
                                                LaunchMode.externalApplication);
                                      }
                                    } else {
                                      await StructuredLogger().log(
                                        level: 'warning',
                                        subsystem: 'webview',
                                        message:
                                            'Invalid URL format for browser',
                                        extra: {'url': urlString},
                                      );
                                    }
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
                              label: const Text('Open in Browser'),
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
                              'Произошла ошибка при загрузке страницы',
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
                        child: const Text('Повторить попытку'),
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
                        child: const Text('Перейти на главную'),
                      ),
                    ],
                  ),
                ),
              ),
          ],
        ),
      );

  void _showCloudflareHint() {
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('Security verification in progress - please wait...'),
        duration: Duration(seconds: 3),
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
        title: const Text('Скачать торрент'),
        content: const Text('Выберите действие:'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, 'open'),
            child: const Text('Открыть'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, 'download'),
            child: const Text('Скачать'),
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
          const SnackBar(
            content: Text(
                'To download the file, please open the link in your browser'),
          ),
        );
      }
      await launchUrl(uri);
    }
  }
}
