import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:jabook/core/rutracker/go_client.dart';
import 'package:jabook/l10n/app_localizations.dart';
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
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..enableZoom(false)
      ..setBackgroundColor(const Color(0xFFFFFFFF))
      ..setNavigationDelegate(
        NavigationDelegate(
          onProgress: (int progress) {
            // You can show progress if needed
          },
          onPageStarted: (String url) {
            setState(() {
              _isLoading = true;
              _hasError = false;
            });
          },
          onPageFinished: (String url) {
            setState(() {
              _isLoading = false;
            });
            
            // Check if we're on a successful login page
            if (url.contains('rutracker') && !url.contains('login')) {
              _showLoginSuccessHint();
            }
          },
          onWebResourceError: (error) {
            setState(() {
              _hasError = true;
              _errorMessage = 'Ошибка загрузки: ${error.description}';
            });
          },
        ),
      )
      ..loadRequest(Uri.parse('https://rutracker.me/'));
  }

  Future<void> _saveCookies() async {
    try {
      // Get cookies from Android CookieManager
      final cookieStr = await _cookieChannel.invokeMethod<String>(
        'getCookiesForDomain',
        {'domain': 'rutracker.me'},
      );
      
      if (!mounted) return;
      
      // Return cookies to the calling screen
      Navigator.of(context).pop<String>(cookieStr ?? '');
    } catch (e) {
      setState(() {
        _hasError = true;
        _errorMessage = 'Failed to extract cookies: $e';
      });
    }
  }

  void _showLoginSuccessHint() {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          'Login successful! You can now use the Go client.',
        ),
        backgroundColor: Colors.green,
        duration: const Duration(seconds: 3),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('RuTracker Login'),
        automaticallyImplyLeading: false,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _initializeWebView,
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
                    Icon(
                      Icons.info_outline,
                      color: Colors.blue.shade700,
                    ),
                    const SizedBox(width: 8.0),
                    Expanded(
                      child: Text(
                        AppLocalizations.of(context)?.authHelpText ??
                        'Please log in to RuTracker. After successful login, click "Done" to extract cookies for the Go client.',
                        style: TextStyle(
                          color: Colors.blue.shade800,
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
                child: const Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      CircularProgressIndicator(),
                      SizedBox(height: 16),
                      Text('Loading RuTracker...'),
                    ],
                  ),
                ),
              ),
            ),
          // Error overlay
          if (_hasError)
            Positioned.fill(
              child: Container(
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
                        _errorMessage ?? 'An error occurred',
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
                      child: const Text('Retry'),
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
        label: const Text('Done'),
        backgroundColor: Colors.green,
      ),
    );
  }

  Widget _buildWebViewContent() {
    return WebViewWidget(controller: _controller);
  }
}

/// Extension to provide easy integration with Go client
extension RutrackerClientIntegration on RutrackerClient {
  /// Initializes the client with session from login screen
  static Future<RutrackerClient> fromLoginScreen({
    required BuildContext context,
    List<String>? mirrors,
    String userAgent = 'Mozilla/5.0 (Android) Jabook/1.0',
  }) async {
    // Show login screen and get cookies
    final cookies = await Navigator.of(context).push<String>(
      MaterialPageRoute(
        builder: (context) => const RutrackerLoginScreen(),
      ),
    );
    
    if (cookies == null || cookies.isEmpty) {
      throw Exception('No cookies received from login screen');
    }
    
    final client = RutrackerClient(
      mirrors: mirrors,
      userAgent: userAgent,
    );
    
    await client.initialize();
    await client.setSessionCookies(cookies);
    
    return client;
  }
}