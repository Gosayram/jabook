import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:jabook/core/endpoints/endpoint_manager.dart';
import 'package:jabook/data/db/app_database.dart';
import 'package:jabook/l10n/app_localizations.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:webview_flutter/webview_flutter.dart';

/// A WebView-based login screen for RuTracker with Go client integration.
///
/// This screen provides a WebView for users to log in to RuTracker and
/// automatically extracts cookies to initialize the Go HTTP client.
class RutrackerLoginScreen extends StatefulWidget {
  /// Creates a new RutrackerLoginScreen instance.
  const RutrackerLoginScreen({super.key});

  @override
  State<RutrackerLoginScreen> createState() => _RutrackerLoginScreenState();
}

/// State class for RutrackerLoginScreen widget.
class _RutrackerLoginScreenState extends State<RutrackerLoginScreen> {
  late final WebViewController _controller;
  static const _cookieChannel = MethodChannel('jabook.cookies');
  bool _isLoading = true;
  bool _hasError = false;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    _initializeWebView();
  }

  Future<void> _initializeWebView() async {
    final db = AppDatabase().database;
    final activeBase = await EndpointManager(db).getActiveEndpoint();

    _controller = WebViewController();
    await _controller.setJavaScriptMode(JavaScriptMode.unrestricted);
    await _controller.enableZoom(false);
    await _controller.setBackgroundColor(const Color(0xFFFFFFFF));
    await _controller.setNavigationDelegate(
      NavigationDelegate(
          onProgress: (progress) {
            // You can show progress if needed
          },
          onPageStarted: (url) {
            setState(() {
              _isLoading = true;
              _hasError = false;
            });
          },
          onPageFinished: (url) {
            setState(() {
              _isLoading = false;
            });
            
            // Check if we're on a successful login page
            if (url.contains(Uri.parse(activeBase).host) && !url.contains('login')) {
              _showLoginSuccessHint();
            }
            _detectCloudflareAndHint();
          },
          onWebResourceError: (error) {
            setState(() {
              _hasError = true;
              _errorMessage = 'Ошибка загрузки: ${error.description}';
            });
          },
          onNavigationRequest: (request) {
            final s = request.url;
            if (s.startsWith('magnet:')) {
              _launchExternal(Uri.parse(s));
              return NavigationDecision.prevent;
            }
            if (s.toLowerCase().endsWith('.torrent')) {
              _handleTorrent(Uri.parse(s));
              return NavigationDecision.prevent;
            }
            // Open external domains in browser
            final host = Uri.parse(s).host;
            if (!host.contains('rutracker')) {
              _launchExternal(Uri.parse(s));
              return NavigationDecision.prevent;
            }
            return NavigationDecision.navigate;
          },
        ),
      );
    await _controller.loadRequest(Uri.parse('$activeBase/'));
  }

  Future<void> _saveCookies() async {
    try {
      var cookieStr = '';
      final activeHost = Uri.parse(await EndpointManager(AppDatabase().database).getActiveEndpoint()).host;

      // Try Android channel first
      try {
        cookieStr = await _cookieChannel.invokeMethod<String>(
              'getCookiesForDomain',
              {'domain': activeHost},
            ) ?? '';
      } on PlatformException {
        // channel not available, fallback below
      }

      // iOS/web fallback: read document.cookie via JS
      if (cookieStr.isEmpty) {
        try {
          final result = await _controller.runJavaScriptReturningResult('document.cookie');
          if (result is String && result.isNotEmpty) {
            cookieStr = result;
          }
        } on Object {
          // ignore
        }
      }

      // Persist cookies to SharedPreferences in JSON list format for Dio sync
      try {
        final prefs = await SharedPreferences.getInstance();
        final jsonList = _serializeCookiesForDomain(cookieStr, activeHost);
        if (jsonList.isNotEmpty) {
          await prefs.setString('rutracker_cookies_v1', jsonEncode(jsonList));
        }
      } on Object {
        // ignore persistence errors
      }

      if (!mounted) return;
      // Return raw string for compatibility
      Navigator.of(context).pop<String>(cookieStr);
    } on Exception catch (e) {
      setState(() {
        _hasError = true;
        _errorMessage = 'Failed to extract cookies: $e';
      });
    }
  }

  // Serializes "name=value; name2=value2" into list of maps with minimal fields
  List<Map<String, String>> _serializeCookiesForDomain(String cookieString, String host) {
    final cookies = <Map<String, String>>[];
    if (cookieString.isEmpty) return cookies;
    final parts = cookieString.split(';');
    for (final part in parts) {
      final kv = part.trim().split('=');
      if (kv.length < 2) continue;
      final name = kv.first.trim();
      final value = kv.sublist(1).join('=').trim();
      if (name.isEmpty) continue;
      cookies.add({
        'name': name,
        'value': value,
        'domain': host,
        'path': '/',
      });
    }
    return cookies;
  }

  void _showLoginSuccessHint() => ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Login successful!'),
          backgroundColor: Colors.green,
          duration: Duration(seconds: 3),
        ),
      );

  Future<void> _detectCloudflareAndHint() async {
    try {
      final result = await _controller.runJavaScriptReturningResult(
        "document.body ? document.body.innerText : ''",
      );
      final text = (result is String) ? result.toLowerCase() : result.toString().toLowerCase();
      if (text.contains('checking your browser') ||
          text.contains('please enable javascript') ||
          text.contains('attention required') ||
          text.contains('cf-chl-bypass')) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Сайт проверяет ваш браузер (Cloudflare). Подождите 5–10 секунд.'),
          ),
        );
      }
    } on Object {
      // no-op
    }
  }

  Future<void> _launchExternal(Uri uri) async {
    await launchUrl(uri, mode: LaunchMode.externalApplication);
  }

  Future<void> _handleTorrent(Uri uri) async {
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

    if (!mounted) return;
    if (action == 'open') {
      await _launchExternal(uri);
    } else if (action == 'download') {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Для загрузки файл будет открыт в браузере')),
      );
      await _launchExternal(uri);
    }
  }

  Widget _buildWebViewContent() => WebViewWidget(controller: _controller);

  @override
  Widget build(BuildContext context) => Scaffold(
      appBar: AppBar(
        title: Text(AppLocalizations.of(context)?.webViewTitle ?? 'RuTracker'),
        automaticallyImplyLeading: false,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _initializeWebView,
          ),
          IconButton(
            icon: const Icon(Icons.open_in_browser),
            onPressed: () async {
              final url = await _controller.currentUrl();
              if (url != null) {
                await _launchExternal(Uri.parse(url));
              }
            },
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
                        AppLocalizations.of(context)?.webViewLoginInstruction ?? 'Please log in to RuTracker. After successful login, click "Done".',
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
                child: _buildWebViewContent(),
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
                      Text(AppLocalizations.of(context)?.loading ?? 'Loading...'),
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
                        _errorMessage ?? (AppLocalizations.of(context)?.networkError ?? 'An error occurred'),
                        textAlign: TextAlign.center,
                        style: TextStyle(
                          fontSize: 16,
                          color: Colors.grey.shade700,
                        ),
                      ),
                    ),
                    const SizedBox(height: 24),
                    ElevatedButton(
                      onPressed: _initializeWebView,
                      child: Text(AppLocalizations.of(context)?.retryButtonText ?? 'Retry'),
                    ),
                  ],
                ),
              ),
            ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _saveCookies,
        icon: const Icon(Icons.check),
        label: Text(AppLocalizations.of(context)?.doneButtonText ?? 'Done'),
        backgroundColor: Colors.green,
      ),
    );

  @override
  void debugFillProperties(DiagnosticPropertiesBuilder properties) {
    super.debugFillProperties(properties);
    properties
      ..add(DiagnosticsProperty<bool>('isLoading', _isLoading))
      ..add(DiagnosticsProperty<bool>('hasError', _hasError))
      ..add(DiagnosticsProperty<String?>('errorMessage', _errorMessage));
  }
}