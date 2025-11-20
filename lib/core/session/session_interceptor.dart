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

import 'package:dio/dio.dart';
import 'package:jabook/core/endpoints/endpoint_manager.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/session/session_manager.dart';
import 'package:jabook/data/db/app_database.dart';

/// Dio interceptor that automatically validates and refreshes session cookies.
///
/// This interceptor checks session validity before requests and automatically
/// refreshes the session if it has expired.
///
/// To avoid excessive network requests, validation is performed with caching
/// and only when necessary (not on every request).
class SessionInterceptor extends Interceptor {
  /// Creates a new SessionInterceptor instance.
  ///
  /// The [sessionManager] parameter is required for session management.
  SessionInterceptor(this.sessionManager);

  /// Session manager instance for validating and refreshing sessions.
  final SessionManager sessionManager;

  /// Last time session was checked in this interceptor.
  static DateTime? _lastCheck;

  /// Minimum interval between session checks (5 minutes).
  static const Duration _checkInterval = Duration(minutes: 5);

  @override
  Future<void> onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    final operationId =
        'session_interceptor_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();

    try {
      // Check if this is a request that requires authentication
      // Skip validation for certain paths (e.g., login, public pages)
      // Also skip if explicitly requested (e.g., for cookie validation)
      final path = options.path.toLowerCase();
      final skipValidation = path.contains('login') ||
          path.contains('register') ||
          path.contains('public') ||
          (options.extra['skip_session_check'] == true);

      if (!skipValidation) {
        // Check if we have cookies - if yes, allow request to proceed
        // This is more legitimate than blocking requests based on cached validation
        final cookieJar = await DioClient.getCookieJar();
        if (cookieJar != null) {
          final db = AppDatabase().database;
          final endpointManager = EndpointManager(db);
          final activeBase = await endpointManager.getActiveEndpoint();
          final uri = Uri.parse(activeBase);
          final cookies = await cookieJar.loadForRequest(uri);
          
          // If we have cookies, allow request to proceed
          // Server will validate cookies and return 401 if invalid
          // This is the legitimate way - don't block requests preemptively
          if (cookies.isNotEmpty) {
            // Check session validity only periodically to avoid excessive requests
            final shouldCheck = _lastCheck == null ||
                DateTime.now().difference(_lastCheck!) > _checkInterval;

            if (shouldCheck) {
              // Check session validity in background (non-blocking)
              // This updates cache but doesn't block the request
              unawaited(sessionManager.isSessionValid().then((isValid) {
                if (!isValid) {
                  // Try to refresh in background, but don't block
                  unawaited(sessionManager.refreshSessionIfNeeded());
                }
              }).catchError((e) {
                // Ignore errors in background check
              }));
              _lastCheck = DateTime.now();
            }
            
            // Always allow request to proceed if we have cookies
            // This is legitimate behavior - let server validate
            handler.next(options);
            return;
          }
        }
        
        // No cookies - check if we can refresh session
        // But don't block - let request proceed and server will handle it
        final shouldCheck = _lastCheck == null ||
            DateTime.now().difference(_lastCheck!) > _checkInterval;

        if (shouldCheck) {
          // Try to refresh session in background
          unawaited(sessionManager.refreshSessionIfNeeded().catchError(
            (e) => false, // Ignore errors - return false to indicate refresh failed
          ));
          _lastCheck = DateTime.now();
        }
      }
    } on Exception catch (e) {
      await logger.log(
        level: 'error',
        subsystem: 'session_interceptor',
        message: 'Error in session interceptor onRequest',
        operationId: operationId,
        context: 'session_interceptor',
        cause: e.toString(),
        extra: {
          'method': options.method,
          'path': options.path,
        },
      );
      // Don't block the request on interceptor errors
    }

    handler.next(options);
  }

  @override
  Future<void> onError(
    DioException err,
    ErrorInterceptorHandler handler,
  ) async {
    final operationId =
        'session_interceptor_error_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();

    // If we got 401/403, try to refresh session and retry
    if (err.response?.statusCode == 401 || err.response?.statusCode == 403) {
      try {
        await logger.log(
          level: 'warning',
          subsystem: 'session_interceptor',
          message: 'Received 401/403, attempting to refresh session',
          operationId: operationId,
          context: 'session_interceptor',
          extra: {
            'status_code': err.response?.statusCode,
            'path': err.requestOptions.path,
          },
        );

        final refreshed = await sessionManager.refreshSessionIfNeeded();
        if (refreshed) {
          // Retry the request with refreshed session
          try {
            final dio = err.requestOptions.extra['dio'] as Dio?;
            if (dio != null) {
              final opts = err.requestOptions;
              final response = await dio.request(
                opts.path,
                options: Options(
                  method: opts.method,
                  headers: opts.headers,
                ),
                data: opts.data,
                queryParameters: opts.queryParameters,
              );

              await logger.log(
                level: 'info',
                subsystem: 'session_interceptor',
                message: 'Request retried successfully after session refresh',
                operationId: operationId,
                context: 'session_interceptor',
                extra: {
                  'path': opts.path,
                  'status_code': response.statusCode,
                },
              );

              handler.resolve(response);
              return;
            }
          } on Exception catch (retryError) {
            await logger.log(
              level: 'error',
              subsystem: 'session_interceptor',
              message: 'Failed to retry request after session refresh',
              operationId: operationId,
              context: 'session_interceptor',
              cause: retryError.toString(),
            );
          }
        } else {
          await logger.log(
            level: 'warning',
            subsystem: 'session_interceptor',
            message: 'Session refresh failed, cannot retry request',
            operationId: operationId,
            context: 'session_interceptor',
            extra: {
              'path': err.requestOptions.path,
            },
          );
        }
      } on Exception catch (e) {
        await logger.log(
          level: 'error',
          subsystem: 'session_interceptor',
          message: 'Error handling 401/403 in session interceptor',
          operationId: operationId,
          context: 'session_interceptor',
          cause: e.toString(),
        );
      }
    }

    handler.next(err);
  }
}

