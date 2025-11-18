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

import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:jabook/core/endpoints/endpoint_manager.dart';
import 'package:jabook/data/db/app_database.dart';
import 'package:jabook/l10n/app_localizations.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:webview_flutter/webview_flutter.dart';

/// A WebView-based login screen for RuTracker.
///
/// This screen provides a WebView for users to log in to RuTracker and
/// automatically extracts cookies for use with the HTTP client.
class RutrackerLoginScreen extends StatefulWidget {
  /// Creates a new RutrackerLoginScreen instance.
  const RutrackerLoginScreen({super.key});

  @override
  State<RutrackerLoginScreen> createState() => _RutrackerLoginScreenState();
}

/// State class for RutrackerLoginScreen widget.
class _RutrackerLoginScreenState extends State<RutrackerLoginScreen> {
  late final WebViewController _controller;
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
    // Use platform default User-Agent for legitimacy (no override)
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
          if (url.contains(Uri.parse(activeBase).host) &&
              !url.contains('login')) {
            _showLoginSuccessHint();
          }
          _detectCloudflareAndHint();
        },
        onWebResourceError: (error) {
          // Handle specific error types
          final errorMessage = error.description;
          final errorCode = error.errorCode;

          // Handle ORB (Origin Resource Blocking) errors
          if (errorMessage.contains('ERR_BLOCKED_BY_ORB') ||
              errorMessage.contains('ERR_BLOCKED_BY_CLIENT') ||
              errorMessage.contains('ERR_BLOCKED_BY_RESPONSE') ||
              errorCode == -6) {
            // These are often non-critical resource loading errors
            debugPrint(
                'WebView resource blocked (non-critical): $errorMessage');
            return;
          }

          // Handle CORS errors
          if (errorMessage.contains('CORS') ||
              errorMessage.contains('Cross-Origin') ||
              errorMessage.contains('Access-Control-Allow-Origin')) {
            debugPrint('CORS error (non-critical): $errorMessage');
            return;
          }

          // Handle mixed content errors
          if (errorMessage.contains('ERR_INSECURE_RESPONSE') ||
              errorMessage.contains('Mixed Content')) {
            debugPrint('Mixed content error (non-critical): $errorMessage');
            return;
          }

          // Handle network errors
          if (errorMessage.contains('ERR_INTERNET_DISCONNECTED') ||
              errorMessage.contains('ERR_NETWORK_CHANGED') ||
              errorCode == -2) {
            setState(() {
              _hasError = true;
              _errorMessage =
                  'Проблема с сетью. Проверьте подключение к интернету.';
            });
            return;
          }

          // Handle other errors
          setState(() {
            _hasError = true;
            _errorMessage = 'Ошибка загрузки: $errorMessage (код: $errorCode)';
          });
          debugPrint('WebView error: $errorMessage, code: $errorCode');
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
      final activeHost = Uri.parse(
              await EndpointManager(AppDatabase().database).getActiveEndpoint())
          .host;

      // Extract cookies using JavaScript - this is the primary method
      try {
        // First, try to get all cookies from document.cookie
        final result =
            await _controller.runJavaScriptReturningResult('document.cookie');
        if (result is String && result.isNotEmpty) {
          cookieStr = result;
          debugPrint('JS cookies from document.cookie: $cookieStr');
        }
      } on Object catch (e) {
        debugPrint('JS cookie extraction failed: $e');
      }

      // If no cookies found, try to get cookies for the specific domain
      if (cookieStr.isEmpty) {
        try {
          const jsCode = '''
            (function() {
              var cookies = document.cookie;
              var domain = window.location.hostname;
              console.log('Current domain: ' + domain);
              console.log('All cookies: ' + cookies);
              
              // Try to get cookies for the current domain
              var cookieArray = cookies.split(';');
              var domainCookies = [];
              
              for (var i = 0; i < cookieArray.length; i++) {
                var cookie = cookieArray[i].trim();
                if (cookie) {
                  domainCookies.push(cookie);
                }
              }
              
              return domainCookies.join('; ');
            })();
          ''';

          final result = await _controller.runJavaScriptReturningResult(jsCode);
          if (result is String && result.isNotEmpty) {
            cookieStr = result;
            debugPrint('JS cookies for domain: $cookieStr');
          }
        } on Object catch (e) {
          debugPrint('Domain-specific cookie extraction failed: $e');
        }
      }

      // If still no cookies, try to get them from localStorage/sessionStorage
      if (cookieStr.isEmpty) {
        try {
          const jsCode = '''
            (function() {
              var storage = {};
              try {
                for (var i = 0; i < localStorage.length; i++) {
                  var key = localStorage.key(i);
                  storage[key] = localStorage.getItem(key);
                }
              } catch (e) {
                console.log('localStorage access failed: ' + e);
              }
              
              try {
                for (var i = 0; i < sessionStorage.length; i++) {
                  var key = sessionStorage.key(i);
                  storage['session_' + key] = sessionStorage.getItem(key);
                }
              } catch (e) {
                console.log('sessionStorage access failed: ' + e);
              }
              
              return JSON.stringify(storage);
            })();
          ''';

          final result = await _controller.runJavaScriptReturningResult(jsCode);
          if (result is String && result.isNotEmpty) {
            debugPrint('Storage data: $result');
            // Convert storage data to cookie-like format
            final storageData = jsonDecode(result) as Map<String, dynamic>;
            final storageCookies = <String>[];
            storageData.forEach((key, value) {
              if (value is String && value.isNotEmpty) {
                storageCookies.add('$key=$value');
              }
            });
            if (storageCookies.isNotEmpty) {
              cookieStr = storageCookies.join('; ');
              debugPrint('Storage cookies: $cookieStr');
            }
          }
        } on Object catch (e) {
          debugPrint('Storage extraction failed: $e');
        }
      }

      // Normalize possible wrapping quotes/newlines from JS bridge
      cookieStr = cookieStr.trim();
      if (cookieStr.length >= 2 &&
          cookieStr.startsWith('"') &&
          cookieStr.endsWith('"')) {
        cookieStr = cookieStr.substring(1, cookieStr.length - 1);
      }
      cookieStr = cookieStr.replaceAll('\n', '').replaceAll('\r', '');

      // Persist cookies to SharedPreferences in JSON list format for Dio sync
      try {
        final prefs = await SharedPreferences.getInstance();
        final jsonList = _serializeCookiesForDomain(cookieStr, activeHost);
        if (jsonList.isNotEmpty) {
          await prefs.setString('rutracker_cookies_v1', jsonEncode(jsonList));
          debugPrint('Saved ${jsonList.length} cookies to SharedPreferences');
          // Also set current mirror as active
          final db = AppDatabase().database;
          await EndpointManager(db).setActiveEndpoint('https://$activeHost');
        } else {
          debugPrint('No cookies to save');
        }
      } on Object catch (e) {
        debugPrint('Cookie persistence failed: $e');
      }

      if (!mounted) return;
      // Return raw string for compatibility
      Navigator.of(context).pop<String>(cookieStr);
    } on Exception catch (e) {
      debugPrint('Cookie extraction failed: $e');
      setState(() {
        _hasError = true;
        _errorMessage = 'Failed to extract cookies: $e';
      });
    }
  }

  // Serializes "name=value; name2=value2" into list of maps with minimal fields
  List<Map<String, String>> _serializeCookiesForDomain(
      String cookieString, String host) {
    final cookies = <Map<String, String>>[];
    if (cookieString.isEmpty) return cookies;
    final validName = RegExp(r"^[!#\$%&'*+.^_`|~0-9A-Za-z-]+$");
    final parts = cookieString.split(';');
    for (final part in parts) {
      final kv = part.trim().split('=');
      if (kv.length < 2) continue;
      var name = kv.first.trim();
      var value = kv.sublist(1).join('=').trim();

      // Strip surrounding quotes if present
      if (name.length >= 2 && name.startsWith('"') && name.endsWith('"')) {
        name = name.substring(1, name.length - 1);
      }
      if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) {
        value = value.substring(1, value.length - 1);
      }

      if (name.isEmpty) continue;
      if (!validName.hasMatch(name)) continue; // skip invalid cookie names
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
      final text = (result is String)
          ? result.toLowerCase()
          : result.toString().toLowerCase();
      if (text.contains('checking your browser') ||
          text.contains('please enable javascript') ||
          text.contains('attention required') ||
          text.contains('cf-chl-bypass')) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
                'Сайт проверяет ваш браузер (Cloudflare). Подождите 5–10 секунд.'),
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
        const SnackBar(
            content: Text('Для загрузки файл будет открыт в браузере')),
      );
      await _launchExternal(uri);
    }
  }

  Widget _buildWebViewContent() => WebViewWidget(controller: _controller);

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
          title:
              Text(AppLocalizations.of(context)?.webViewTitle ?? 'RuTracker'),
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
                        Text(AppLocalizations.of(context)?.loading ??
                            'Loading...'),
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
                        onPressed: _initializeWebView,
                        child: Text(
                            AppLocalizations.of(context)?.retryButtonText ??
                                'Retry'),
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
