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
import 'dart:io' as io;

import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart' as dio_cookie;
import 'package:flutter_cookie_bridge/flutter_cookie_bridge.dart';
import 'package:flutter_cookie_bridge/session_manager.dart' as bridge_session;
import 'package:jabook/core/endpoints/endpoint_manager.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/core/net/dio_cookie_manager.dart';
import 'package:jabook/core/net/dio_interceptors.dart';
import 'package:jabook/core/net/user_agent_manager.dart';
import 'package:jabook/core/session/session_interceptor.dart';
import 'package:jabook/core/session/session_manager.dart';
import 'package:jabook/data/db/app_database.dart';

/// HTTP client for making requests to RuTracker APIs.
///
/// This class provides a singleton Dio instance configured for
/// making HTTP requests to RuTracker with proper timeouts,
/// user agent, and cookie management.
class DioClient {
  /// Private constructor to prevent direct instantiation.
  const DioClient._();

  static Dio? _instance;
  static CookieJar? _cookieJar;
  static SessionManager? _sessionManager;
  static DateTime? _appStartTime;
  static bool _firstRequestTracked = false;
  static FlutterCookieBridge? _cookieBridge;
  static bridge_session.SessionManager? _bridgeSessionManager;

  /// Gets the singleton Dio instance configured for RuTracker requests.
  ///
  /// This instance is configured with appropriate timeouts, user agent,
  /// and cookie management for RuTracker API calls.
  ///
  /// Returns a configured Dio instance ready for use.
  static Future<Dio> get instance async {
    // Return cached instance if already initialized
    if (_instance != null) {
      return _instance!;
    }

    final dio = Dio();
    final userAgentManager = UserAgentManager();

    // Apply User-Agent from manager
    await userAgentManager.applyUserAgentToDio(dio);

    // Resolve active RuTracker endpoint dynamically
    final db = AppDatabase().database;
    final endpointManager = EndpointManager(db);
    final activeBase = await endpointManager.getActiveEndpoint();

    // Get User-Agent from WebView to ensure consistency (important for Cloudflare)
    final userAgent = await userAgentManager.getUserAgent();
    
    dio.options = BaseOptions(
      baseUrl: activeBase,
      connectTimeout: const Duration(seconds: 30),
      receiveTimeout: const Duration(seconds: 30),
      sendTimeout: const Duration(seconds: 30),
      headers: {
        'User-Agent': userAgent, // Same as WebView - critical for Cloudflare
        'Accept':
            'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        'Accept-Language': 'ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7',
        'Accept-Encoding': 'gzip, deflate, br',
        'Connection': 'keep-alive',
        'Referer': '$activeBase/',
        // Don't set Cookie header manually - let CookieJar handle it
        // This ensures cookies are sent automatically with requests
      },
    );

    // Add structured logging interceptor
    dio.interceptors.add(DioInterceptors.createLoggingInterceptor(
      appStartTime: _appStartTime,
      firstRequestTracked: _firstRequestTracked,
    ));

    // Add authentication redirect handler and resilient retry policy for idempotent requests
    dio.interceptors.add(DioInterceptors.createAuthAndRetryInterceptor(dio));

    // Initialize FlutterCookieBridge for automatic cookie synchronization
    // According to .report-issue-docs.md: this library handles cookie sync
    // between WebView and Dio automatically, ensuring legitimate Cloudflare connections
    _cookieBridge ??= FlutterCookieBridge();
    _bridgeSessionManager ??= bridge_session.SessionManager();
    
    // Initialize cookie jar
    _cookieJar ??= CookieJar();
    dio.interceptors.add(dio_cookie.CookieManager(_cookieJar!));
    
    // Add FlutterCookieBridge interceptor for automatic cookie sync
    // IMPORTANT: This interceptor must be AFTER CookieManager so that:
    // 1. CookieManager adds cookies from CookieJar to requests
    // 2. This interceptor merges cookies from SessionManager with existing Cookie header
    // 3. CookieManager saves cookies from responses to CookieJar
    // 4. This interceptor also saves cookies from responses to SessionManager
    dio.interceptors.add(DioInterceptors.createCookieBridgeInterceptor(
      _bridgeSessionManager!,
    ));
    
    await StructuredLogger().log(
      level: 'info',
      subsystem: 'cookies',
      message: 'FlutterCookieBridge initialized for automatic cookie sync',
      context: 'cookie_bridge_init',
    );

    // Add SessionInterceptor for automatic session validation and refresh
    // Use singleton SessionManager instance
    _sessionManager ??= SessionManager();
    dio.interceptors.add(SessionInterceptor(_sessionManager!));

    // Cache and return the instance
    _instance = dio;
    return _instance!;
  }

  /// Sets the application start time for performance metrics tracking.
  ///
  /// This should be called early in the application lifecycle (e.g., in main())
  /// to enable tracking of time to first network request.
  static set appStartTime(DateTime startTime) {
    _appStartTime = startTime;
  }

  /// Resets the singleton instance (useful for testing).
  ///
  /// This method clears the cached Dio instance and session manager,
  /// allowing a fresh instance to be created on the next call to [instance].
  static void reset() {
    _instance = null;
    _sessionManager = null;
    _firstRequestTracked = false;
    _appStartTime = null;
    // Keep _cookieJar and _cookieBridge to preserve cookies across resets
  }
  
  /// Gets the FlutterCookieBridge instance for WebView integration.
  ///
  /// This method returns the singleton FlutterCookieBridge instance
  /// that handles automatic cookie synchronization between WebView and Dio.
  ///
  /// Returns the FlutterCookieBridge instance.
  static Future<FlutterCookieBridge> get cookieBridge async {
    await instance; // Ensure Dio is initialized
    _cookieBridge ??= FlutterCookieBridge();
    return _cookieBridge!;
  }

  /// Gets the user agent string for HTTP requests.
  ///
  /// This method returns a user agent string that mimics a mobile browser
  /// to ensure compatibility with RuTracker's anti-bot measures.
  ///
  /// Returns a user agent string for HTTP requests.
  static Future<String> getUserAgent() async {
    final userAgentManager = UserAgentManager();
    return userAgentManager.getUserAgent();
  }

  /// Synchronizes cookies from WebView to the Dio client.
  ///
  /// This method should be called to ensure that authentication cookies
  /// obtained through WebView login are available for HTTP requests.
  ///
  /// It validates cookies before saving and handles various cookie formats.
  static Future<void> syncCookiesFromWebView() async {
    await DioCookieManager.syncCookiesFromWebView(_cookieJar);
  }

  /// Saves cookies directly to Dio CookieJar.
  ///
  /// This is a helper method for direct cookie synchronization from WebView.
  /// It bypasses SharedPreferences and saves cookies directly to the cookie jar.
  static Future<void> saveCookiesDirectly(Uri uri, List<io.Cookie> cookies) async {
    await DioCookieManager.saveCookiesDirectly(uri, cookies, _cookieJar);
  }

  /// Gets the CookieJar instance for debugging purposes.
  ///
  /// This method ensures the cookie jar is initialized and returns it.
  static Future<CookieJar?> getCookieJar() async {
    await instance; // Ensure Dio is initialized
    _cookieJar ??= CookieJar();
    return _cookieJar;
  }

  /// Synchronizes cookies from Dio cookie jar to WebView storage.
  ///
  /// This method should be called after HTTP-based login to ensure cookies
  /// are available for WebView as well.
  static Future<void> syncCookiesToWebView() async {
    await DioCookieManager.syncCookiesToWebView(_cookieJar);
  }

  /// Syncs cookies to a new endpoint when switching mirrors.
  ///
  /// This method ensures that cookies from the old endpoint are available
  /// for the new endpoint if they are compatible (same domain family).
  ///
  /// The [oldEndpoint] parameter is the URL of the endpoint being switched from.
  /// The [newEndpoint] parameter is the URL of the endpoint being switched to.
  static Future<void> syncCookiesOnEndpointSwitch(
    String oldEndpoint,
    String newEndpoint,
  ) async {
    await DioCookieManager.syncCookiesOnEndpointSwitch(
      oldEndpoint,
      newEndpoint,
      _cookieJar,
    );
  }

  /// Clears all stored cookies from the cookie jar.
  ///
  /// This method removes all cookies, effectively logging out the user
  /// from any authenticated sessions.
  static Future<void> clearCookies() async {
    await DioCookieManager.clearCookies(_cookieJar);
  }

  /// Checks if valid cookies are available for authentication.
  ///
  /// Returns true if cookies exist and appear to be valid, false otherwise.
  static Future<bool> hasValidCookies() async =>
      DioCookieManager.hasValidCookies();

  /// Validates cookies by making a test request to RuTracker.
  ///
  /// Returns true if cookies are valid and authentication is active, false otherwise.
  static Future<bool> validateCookies() async {
      final dio = await instance;
    return DioCookieManager.validateCookies(
      dio,
      _cookieJar,
      _bridgeSessionManager,
    );
  }
}
