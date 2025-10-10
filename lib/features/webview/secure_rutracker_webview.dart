import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
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
  final initialUrl = 'https://rutracker.me'; // or rutracker.me / .org — can be configured
  
  // State restoration
  final String _webViewStateKey = 'rutracker_webview_state';
  
  // Error handling
  bool _hasError = false;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    _restoreCookies();
    _restoreWebViewHistory();
  }

  @override
  void dispose() {
    _saveWebViewHistory();
    super.dispose();
  }

  Future<void> _restoreCookies() async {
    final prefs = await SharedPreferences.getInstance();
    final cookieJson = prefs.getString('rutracker_cookies_v1');
    if (cookieJson != null) {
      try {
        final cookies = jsonDecode(cookieJson) as List<dynamic>;
        for (final cookie in cookies) {
          await CookieManager.instance().setCookie(
            url: WebUri(initialUrl),
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
          url: WebUri(initialUrl),
          domain: 'rutracker.me',
        );
      }
    }
  }

  Future<void> _saveCookies() async {
    try {
      final cookies = await CookieManager.instance().getCookies(url: WebUri(initialUrl));
      final cookieJson = jsonEncode(cookies.map((c) => {
        'name': c.name,
        'value': c.value,
        'domain': c.domain,
        'path': c.path,
        // Note: flutter_inappwebview Cookie doesn't expose expires property
        // We'll rely on the cookie's natural expiration
      }).toList());
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
        await _webViewController.loadUrl(urlRequest: URLRequest(url: WebUri(savedUrl)));
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
           h.contains('cf-chl-bypass');
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: Text(AppLocalizations.of(context)?.webViewTitle ?? 'RuTracker'),
      actions: [
        IconButton(
          icon: const Icon(Icons.open_in_browser),
          onPressed: () async {
            final url = await _webViewController.getUrl();
            if (url != null) await launchUrl(url);
          },
        ),
      ],
      bottom: PreferredSize(
        preferredSize: const Size.fromHeight(3.0),
        child: LinearProgressIndicator(value: progress == 1 ? null : progress),
      ),
    ),
    body: Stack(
      children: [
        Column(
          children: [
            // Cloudflare explanation banner
            Container(
              padding: const EdgeInsets.all(12.0),
              color: Colors.orange.shade100,
              child: Row(
                children: [
                  Icon(
                    Icons.info_outline,
                    color: Colors.orange.shade700,
                  ),
                  const SizedBox(width: 8.0),
                  Expanded(
                    child: Text(
                      AppLocalizations.of(context)?.cloudflareMessage ?? 'Этот сайт использует проверки безопасности Cloudflare. Пожалуйста, дождитесь завершения проверки и взаимодействуйте с открывшейся страницей при необходимости.',
                      style: TextStyle(
                        color: Colors.orange.shade800,
                        fontSize: 14.0,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            // WebView
            Expanded(
              child: InAppWebView(
                initialUrlRequest: URLRequest(url: WebUri(initialUrl)),
                initialSettings: InAppWebViewSettings(
                  useShouldOverrideUrlLoading: true,
                ),
                onWebViewCreated: (controller) {
                  _webViewController = controller;
                  _hasError = false; // Reset error state when WebView is recreated
                },
                onProgressChanged: (controller, p) => setState(() => progress = p / 100.0),
                onLoadStop: (controller, url) async {
                  final html = await controller.getHtml();
                  if (html != null && _looksLikeCloudflare(html)) {
                    _showCloudflareHint();
                  } else {
                    await _saveCookies();
                  }
                },
                shouldOverrideUrlLoading: (controller, nav) async {
                  final uri = nav.request.url;
                  if (uri == null) return NavigationActionPolicy.ALLOW;
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
                onReceivedError: (controller, request, error) {
                  setState(() {
                    _hasError = true;
                    _errorMessage = 'Ошибка загрузки: ${error.description}';
                  });
                },
              ),
            ),
          ],
        ),
        // Error overlay
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
                      _errorMessage ?? 'Произошла ошибка при загрузке страницы',
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
                      _webViewController.loadUrl(urlRequest: URLRequest(url: WebUri(initialUrl)));
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
        content: Text('The site is checking your browser - please wait for the verification to complete on this page.'),
      ),
    );
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
            content: Text('To download the file, please open the link in your browser'),
          ),
        );
      }
      await launchUrl(uri);
    }
  }
}