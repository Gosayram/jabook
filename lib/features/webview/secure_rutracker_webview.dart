import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:jabook/core/endpoints/endpoint_manager.dart';
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
    final endpoint = await EndpointManager(db).getActiveEndpoint();
    initialUrl = endpoint;
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
                  final targetUrl =
                      url ?? (initialUrl != null ? WebUri(initialUrl!) : null);

                  if (targetUrl != null) {
                    final uri = Uri.parse(targetUrl.toString());
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
                      const SnackBar(
                        content: Text('URL недоступен'),
                        duration: Duration(seconds: 2),
                      ),
                    );
                  }
                } on Exception catch (e) {
                  if (!mounted) return;
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
                          },
                          onProgressChanged: (controller, p) =>
                              setState(() => progress = p / 100.0),
                          onReceivedError: (controller, request, error) {
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
                            setState(() {
                              _hasError = true;
                              _errorMessage = 'Ошибка загрузки: $desc';
                            });
                          },
                          onReceivedHttpError:
                              (controller, request, errorResponse) {
                            // Show HTTP error only for main frame
                            if (!(request.isForMainFrame ?? true)) return;
                            setState(() {
                              _hasError = true;
                              _errorMessage =
                                  'HTTP ошибка: ${errorResponse.statusCode}';
                            });
                          },
                          onLoadStop: (controller, url) async {
                            final html = await controller.getHtml();
                            if (html != null && _looksLikeCloudflare(html)) {
                              _showCloudflareHint();
                              // Show a more prominent Cloudflare indicator
                              _showCloudflareOverlay();
                            } else {
                              await _saveCookies();
                              // Automatically sync cookies to DioClient after saving
                              try {
                                await DioClient.syncCookiesFromWebView();
                              } on Exception catch (e) {
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
                                  }
                                }
                              } on Exception {
                                // ignore
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
                                  final targetUrl = url ??
                                      (initialUrl != null
                                          ? WebUri(initialUrl!)
                                          : null);
                                  if (targetUrl != null) {
                                    final uri = Uri.parse(targetUrl.toString());
                                    if (await canLaunchUrl(uri)) {
                                      await launchUrl(uri,
                                          mode: LaunchMode.externalApplication);
                                    }
                                  }
                                } on Exception catch (e) {
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
                        onPressed: () {
                          setState(() {
                            _hasError = false;
                            _errorMessage = null;
                          });
                          _webViewController.reload();
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
