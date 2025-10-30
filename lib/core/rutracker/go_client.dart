import 'package:flutter/services.dart';
import 'package:jabook/core/endpoints/endpoint_manager.dart';
import 'package:jabook/data/db/app_database.dart';

/// GoClient provides a Dart interface to the Go HTTP client with mirror support
/// for Rutracker integration. This client handles cookie management and
/// automatic mirror rotation.
@pragma('vm:prefer-inline')
class GoClient {
  const GoClient._();
  
  static const _channel = MethodChannel('jabook.golib');

  /// Initializes the Go client with primary host and optional mirrors
  static Future<void> init({
    required String host,
    String userAgent = 'Mozilla/5.0 (Android) Jabook/1.0',
    List<String>? mirrors,
  }) async {
    await _channel.invokeMethod('init', {
      'host': host,
      'ua': userAgent,
      if (mirrors != null && mirrors.isNotEmpty) 'mirrors': mirrors,
    });
  }

  /// Sets cookies from Android CookieManager
  static Future<void> setCookies({
    required String cookies,
    String scheme = 'https',
  }) async {
    await _channel.invokeMethod('setCookies', {
      'cookies': cookies,
      'scheme': scheme,
    });
  }

  /// Performs HTTP GET request with automatic mirror rotation
  static Future<String> getHTML(String path) async {
    final html = await _channel.invokeMethod<String>('getHTML', {'path': path});
    return html ?? '';
  }

  /// Gets the current host being used (useful for debugging)
  static Future<String> getCurrentHost() async {
    final host = await _channel.invokeMethod<String>('getCurrentHost');
    return host ?? '';
  }

  /// Gets the total number of available mirrors
  static Future<int> getMirrorCount() async {
    final count = await _channel.invokeMethod<int>('getMirrorCount');
    return count ?? 0;
  }
}

/// RutrackerClient provides high-level interface for Rutracker operations
class RutrackerClient {
  /// Initializes the client with Rutracker configuration
  RutrackerClient({
    List<String>? mirrors,
    String userAgent = 'Mozilla/5.0 (Android) Jabook/1.0',
  }) : _mirrors = mirrors ?? [],
       _userAgent = userAgent;

  final List<String> _mirrors;
  final String _userAgent;

  /// Initializes the client with Rutracker configuration
  Future<void> initialize() async {
    final db = AppDatabase().database;
    final endpoint = await EndpointManager(db).getActiveEndpoint();
    final host = Uri.parse(endpoint).host;

    await GoClient.init(
      host: host,
      userAgent: _userAgent,
      mirrors: _mirrors.isNotEmpty ? _mirrors : null,
    );
  }

  /// Sets session cookies from WebView login
  Future<void> setSessionCookies(String cookies) async {
    await GoClient.setCookies(cookies: cookies);
  }

  /// Fetches content from Rutracker with automatic mirror rotation
  Future<String> fetch(String path) => GoClient.getHTML(path);

  /// Gets current mirror information for debugging
  Future<Map<String, dynamic>> getMirrorInfo() async {
    final currentHost = await GoClient.getCurrentHost();
    final mirrorCount = await GoClient.getMirrorCount();
    
    return {
      'currentHost': currentHost,
      'mirrorCount': mirrorCount,
      'mirrors': _mirrors,
    };
  }

}