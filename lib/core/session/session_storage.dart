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
import 'dart:typed_data';

import 'package:cookie_jar/cookie_jar.dart';
import 'package:crypto/crypto.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:jabook/core/errors/failures.dart';
import 'package:jabook/core/logging/structured_logger.dart';

/// Handles secure storage and retrieval of session cookies and metadata.
///
/// This class manages persistent storage of session data using
/// FlutterSecureStorage for sensitive cookie information.
class SessionStorage {
  /// Creates a new SessionStorage instance.
  const SessionStorage();

  /// Secure storage instance for session data.
  static const FlutterSecureStorage _storage = FlutterSecureStorage();

  /// Key for storing session cookies in secure storage.
  static const String _cookiesKey = 'session_cookies_v2';

  /// Key for storing session metadata in secure storage.
  static const String _metadataKey = 'session_metadata_v2';

  /// Saves session cookies to secure storage.
  ///
  /// The [cookies] parameter is a list of cookies to save.
  /// The [endpoint] parameter is the active endpoint URL.
  ///
  /// Throws [AuthFailure] if saving fails.
  Future<void> saveCookies(
    List<Cookie> cookies,
    String endpoint,
  ) async {
    final operationId =
        'save_cookies_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      await logger.log(
        level: 'debug',
        subsystem: 'session_storage',
        message: 'Saving session cookies',
        operationId: operationId,
        context: 'session_storage',
        extra: {
          'cookie_count': cookies.length,
          'endpoint': endpoint,
        },
      );

      // Convert cookies to JSON format
      final cookieList = cookies
          .map(
            (cookie) => {
              'name': cookie.name,
              'value': cookie.value,
              'domain': cookie.domain ?? '',
              'path': cookie.path ?? '/',
              'expires': cookie.expires?.toIso8601String(),
              'secure': cookie.secure,
              'httpOnly': cookie.httpOnly,
            },
          )
          .toList();

      final cookiesJson = jsonEncode(cookieList);
      await _storage.write(key: _cookiesKey, value: cookiesJson);

      // Generate session ID based on cookies and timestamp
      final sessionId = _generateSessionId(cookies, endpoint);

      // Save metadata
      final metadata = {
        'endpoint': endpoint,
        'created_at': DateTime.now().toIso8601String(),
        'cookie_count': cookies.length,
        'session_id': sessionId,
      };
      await _storage.write(
        key: _metadataKey,
        value: jsonEncode(metadata),
      );

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'info',
        subsystem: 'session_storage',
        message: 'Session cookies saved successfully',
        operationId: operationId,
        context: 'session_storage',
        durationMs: duration,
        extra: {
          'cookie_count': cookies.length,
          'endpoint': endpoint,
        },
      );
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'session_storage',
        message: 'Failed to save session cookies',
        operationId: operationId,
        context: 'session_storage',
        durationMs: duration,
        cause: e.toString(),
      );
      throw AuthFailure(
        'Failed to save session cookies: ${e.toString()}',
        e,
      );
    }
  }

  /// Retrieves session cookies from secure storage.
  ///
  /// Returns a list of cookies if available, null otherwise.
  ///
  /// Throws [AuthFailure] if retrieval fails.
  Future<List<Cookie>?> loadCookies() async {
    final operationId =
        'load_cookies_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      final cookiesJson = await _storage.read(key: _cookiesKey);
      if (cookiesJson == null) {
        await logger.log(
          level: 'debug',
          subsystem: 'session_storage',
          message: 'No session cookies found in storage',
          operationId: operationId,
          context: 'session_storage',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
        );
        return null;
      }

      final cookieList = jsonDecode(cookiesJson) as List<dynamic>;
      final cookies = cookieList.map((cookieMap) {
        final map = cookieMap as Map<String, dynamic>;
        return Cookie(
          map['name'] as String,
          map['value'] as String,
        )
          ..domain = map['domain'] as String? ?? ''
          ..path = map['path'] as String? ?? '/'
          ..expires = map['expires'] != null
              ? DateTime.parse(map['expires'] as String)
              : null
          ..secure = map['secure'] as bool? ?? false
          ..httpOnly = map['httpOnly'] as bool? ?? false;
      }).toList();

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'info',
        subsystem: 'session_storage',
        message: 'Session cookies loaded successfully',
        operationId: operationId,
        context: 'session_storage',
        durationMs: duration,
        extra: {
          'cookie_count': cookies.length,
        },
      );

      return cookies;
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'session_storage',
        message: 'Failed to load session cookies',
        operationId: operationId,
        context: 'session_storage',
        durationMs: duration,
        cause: e.toString(),
      );
      throw AuthFailure(
        'Failed to load session cookies: ${e.toString()}',
        e,
      );
    }
  }

  /// Retrieves session metadata from secure storage.
  ///
  /// Returns a map with metadata if available, null otherwise.
  Future<Map<String, dynamic>?> loadMetadata() async {
    try {
      final metadataJson = await _storage.read(key: _metadataKey);
      if (metadataJson == null) {
        return null;
      }

      return jsonDecode(metadataJson) as Map<String, dynamic>;
    } on Exception {
      return null;
    }
  }

  /// Clears all session data from secure storage.
  ///
  /// Throws [AuthFailure] if clearing fails.
  Future<void> clear() async {
    final operationId =
        'clear_session_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      await _storage.delete(key: _cookiesKey);
      await _storage.delete(key: _metadataKey);

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'info',
        subsystem: 'session_storage',
        message: 'Session data cleared successfully',
        operationId: operationId,
        context: 'session_storage',
        durationMs: duration,
      );
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'session_storage',
        message: 'Failed to clear session data',
        operationId: operationId,
        context: 'session_storage',
        durationMs: duration,
        cause: e.toString(),
      );
      throw AuthFailure(
        'Failed to clear session data: ${e.toString()}',
        e,
      );
    }
  }

  /// Checks if session data exists in storage.
  // ignore: prefer_expression_function_bodies
  Future<bool> hasSession() async {
    try {
      final cookiesJson = await _storage.read(key: _cookiesKey);
      return cookiesJson != null && cookiesJson.isNotEmpty;
    } on Exception {
      return false;
    }
  }

  /// Generates a unique session ID based on cookies and endpoint.
  ///
  /// This ID is used to identify the session and can be used
  /// to bind cache entries to specific sessions.
  String _generateSessionId(List<Cookie> cookies, String endpoint) {
    // Create a hash from important cookie values and endpoint
    final cookieData = cookies
        .where((c) => c.name == 'bb_session' || c.name == 'bb_data')
        .map((c) => '${c.name}=${c.value}')
        .join('|');
    final data = '$endpoint|$cookieData';
    final bytes = Uint8List.fromList(data.codeUnits);
    final digest = sha256.convert(bytes);
    return digest.toString().substring(0, 16); // Use first 16 chars as session ID
  }

  /// Gets the current session ID from metadata.
  ///
  /// Returns the session ID if available, null otherwise.
  Future<String?> getSessionId() async {
    try {
      final metadata = await loadMetadata();
      return metadata?['session_id'] as String?;
    } on Exception {
      return null;
    }
  }
}

