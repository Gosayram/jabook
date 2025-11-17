import 'dart:convert';

import 'package:cookie_jar/cookie_jar.dart';
import 'package:jabook/core/endpoints/endpoint_manager.dart';
import 'package:jabook/core/errors/failures.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/data/db/app_database.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Handles synchronization of cookies between WebView and Dio client.
///
/// This service ensures that cookies obtained through WebView login
/// are available for HTTP requests via Dio, and vice versa.
class CookieSyncService {
  /// Creates a new CookieSyncService instance.
  const CookieSyncService();

  /// Synchronizes cookies from WebView storage to Dio cookie jar.
  ///
  /// This method reads cookies from SharedPreferences (where WebView stores them)
  /// and saves them to the Dio cookie jar for use in HTTP requests.
  ///
  /// Throws [AuthFailure] if synchronization fails.
  Future<void> syncFromWebViewToDio(CookieJar cookieJar) async {
    final operationId =
        'sync_webview_to_dio_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      await logger.log(
        level: 'debug',
        subsystem: 'cookie_sync',
        message: 'Syncing cookies from WebView to Dio',
        operationId: operationId,
        context: 'cookie_sync',
      );

      final prefs = await SharedPreferences.getInstance();
      final cookieJson = prefs.getString('rutracker_cookies_v1');
      if (cookieJson == null) {
        await logger.log(
          level: 'debug',
          subsystem: 'cookie_sync',
          message: 'No cookies found in WebView storage',
          operationId: operationId,
          context: 'cookie_sync',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
        );
        return;
      }

      final db = AppDatabase().database;
      final endpointManager = EndpointManager(db);
      final activeBase = await endpointManager.getActiveEndpoint();
      final uri = Uri.parse(activeBase);

      // Parse cookies list from JSON
      List<dynamic> list;
      try {
        list = jsonDecode(cookieJson) as List<dynamic>;
      } on Exception catch (e) {
        await logger.log(
          level: 'error',
          subsystem: 'cookie_sync',
          message: 'Failed to parse cookie JSON',
          operationId: operationId,
          context: 'cookie_sync',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
          cause: e.toString(),
        );
        throw AuthFailure(
          'Failed to parse cookies from WebView: ${e.toString()}',
          e,
        );
      }

      if (list.isEmpty) {
        await logger.log(
          level: 'debug',
          subsystem: 'cookie_sync',
          message: 'Empty cookie list from WebView',
          operationId: operationId,
          context: 'cookie_sync',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
        );
        return;
      }

      // Convert to Cookie objects and save to Dio cookie jar
      final cookies = <Cookie>[];
      for (final item in list) {
        final map = item as Map<String, dynamic>;
        final name = map['name'] as String? ?? '';
        final value = map['value'] as String? ?? '';
        final domain = map['domain'] as String? ?? uri.host;
        final path = map['path'] as String? ?? '/';

        if (name.isNotEmpty && value.isNotEmpty) {
          final cookie = Cookie(name, value)
            ..domain = domain
            ..path = path;
          cookies.add(cookie);
        }
      }

      // Save cookies to Dio cookie jar
      await cookieJar.saveFromResponse(uri, cookies);

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'info',
        subsystem: 'cookie_sync',
        message: 'Cookies synced from WebView to Dio',
        operationId: operationId,
        context: 'cookie_sync',
        durationMs: duration,
        extra: {
          'cookie_count': cookies.length,
          'endpoint': activeBase,
        },
      );
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'cookie_sync',
        message: 'Failed to sync cookies from WebView to Dio',
        operationId: operationId,
        context: 'cookie_sync',
        durationMs: duration,
        cause: e.toString(),
      );
      throw AuthFailure(
        'Failed to sync cookies from WebView: ${e.toString()}',
        e,
      );
    }
  }

  /// Synchronizes cookies from Dio cookie jar to WebView storage.
  ///
  /// This method reads cookies from the Dio cookie jar and saves them
  /// to SharedPreferences in the format expected by WebView.
  ///
  /// Throws [AuthFailure] if synchronization fails.
  Future<void> syncFromDioToWebView(CookieJar cookieJar) async {
    final operationId =
        'sync_dio_to_webview_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      await logger.log(
        level: 'debug',
        subsystem: 'cookie_sync',
        message: 'Syncing cookies from Dio to WebView',
        operationId: operationId,
        context: 'cookie_sync',
      );

      final db = AppDatabase().database;
      final endpointManager = EndpointManager(db);
      final activeBase = await endpointManager.getActiveEndpoint();
      final uri = Uri.parse(activeBase);

      // Load cookies from Dio cookie jar
      final cookies = await cookieJar.loadForRequest(uri);

      if (cookies.isEmpty) {
        await logger.log(
          level: 'debug',
          subsystem: 'cookie_sync',
          message: 'No cookies in Dio cookie jar to sync',
          operationId: operationId,
          context: 'cookie_sync',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
        );
        return;
      }

      // Convert Dio Cookie objects to JSON format compatible with WebView storage
      final cookieList = <Map<String, String>>[];
      for (final cookie in cookies) {
        cookieList.add({
          'name': cookie.name,
          'value': cookie.value,
          'domain': cookie.domain ?? uri.host,
          'path': cookie.path ?? '/',
        });
      }

      // Save to SharedPreferences in the same format as WebView
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString('rutracker_cookies_v1', jsonEncode(cookieList));

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'info',
        subsystem: 'cookie_sync',
        message: 'Cookies synced from Dio to WebView',
        operationId: operationId,
        context: 'cookie_sync',
        durationMs: duration,
        extra: {
          'count': cookieList.length,
          'endpoint': activeBase,
        },
      );
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'cookie_sync',
        message: 'Failed to sync cookies from Dio to WebView',
        operationId: operationId,
        context: 'cookie_sync',
        durationMs: duration,
        cause: e.toString(),
      );
      throw AuthFailure(
        'Failed to sync cookies to WebView: ${e.toString()}',
        e,
      );
    }
  }

  /// Performs bidirectional synchronization between WebView and Dio.
  ///
  /// This method ensures cookies are synchronized in both directions,
  /// merging cookies from both sources.
  ///
  /// Throws [AuthFailure] if synchronization fails.
  Future<void> syncBothWays(CookieJar cookieJar) async {
    final operationId =
        'sync_both_ways_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      await logger.log(
        level: 'debug',
        subsystem: 'cookie_sync',
        message: 'Performing bidirectional cookie sync',
        operationId: operationId,
        context: 'cookie_sync',
      );

      // Sync from WebView to Dio first
      await syncFromWebViewToDio(cookieJar);

      // Then sync from Dio to WebView to ensure consistency
      await syncFromDioToWebView(cookieJar);

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'info',
        subsystem: 'cookie_sync',
        message: 'Bidirectional cookie sync completed',
        operationId: operationId,
        context: 'cookie_sync',
        durationMs: duration,
      );
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'cookie_sync',
        message: 'Bidirectional cookie sync failed',
        operationId: operationId,
        context: 'cookie_sync',
        durationMs: duration,
        cause: e.toString(),
      );
      throw AuthFailure(
        'Failed to sync cookies both ways: ${e.toString()}',
        e,
      );
    }
  }
}

