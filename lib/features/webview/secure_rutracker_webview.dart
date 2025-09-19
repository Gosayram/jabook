import 'package:flutter/material.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
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

  @override
  void initState() {
    super.initState();
    _restoreCookies();
  }

  Future<void> _restoreCookies() async {
    final prefs = await SharedPreferences.getInstance();
    final cookieJson = prefs.getString('rutracker_cookies_v1');
    if (cookieJson != null) {
      // Simple deserialization — example; handle securely in production
      // flutter_inappwebview has setCookie method
      // Here we assume cookieJson is serialized list of maps
      // Deserialization implementation omitted for brevity
    }
  }

  Future<void> _saveCookies() async {
    // Serialize cookies to JSON and save
    // Serialization implementation omitted for brevity
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
      title: const Text('RuTracker'),
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
    body: InAppWebView(
      initialUrlRequest: URLRequest(url: WebUri(initialUrl)),
      initialSettings: InAppWebViewSettings(
        useShouldOverrideUrlLoading: true,
      ),
      onWebViewCreated: (controller) => _webViewController = controller,
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
        // Show UI with retry option
      },
    ),
  );

  void _showCloudflareHint() {
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('Сайт проверяет ваш браузер — пожалуйста, дождитесь завершения проверки в этой странице.'),
      ),
    );
  }

  Future<void> _handleTorrentLink(Uri uri) async {
    if (await launchUrl(uri)) {
    } else {
      // Handle saving the torrent file (requires write permissions)
    }
  }
}