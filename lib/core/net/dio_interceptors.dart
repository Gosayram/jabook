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
import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter_cookie_bridge/session_manager.dart' as bridge_session;
import 'package:jabook/core/endpoints/endpoint_manager.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/core/net/dio_cookie_manager.dart';
import 'package:jabook/core/utils/safe_async.dart';
import 'package:jabook/data/db/app_database.dart';

/// Factory class for creating Dio interceptors.
class DioInterceptors {
  /// Private constructor to prevent instantiation.
  const DioInterceptors._();

  /// Creates a structured logging interceptor.
  ///
  /// This interceptor logs all HTTP requests and responses with detailed
  /// information including timing, headers, and error details.
  ///
  /// The [appStartTime] parameter is used to track time to first network request.
  /// The [firstRequestTracked] parameter tracks if the first request has been logged.
  static Interceptor createLoggingInterceptor({
    DateTime? appStartTime,
    bool firstRequestTracked = false,
  }) {
    var firstRequestTrackedLocal = firstRequestTracked;
    
    return InterceptorsWrapper(
      onRequest: (request, handler) async {
        final operationId =
            'http_request_${DateTime.now().millisecondsSinceEpoch}_${request.hashCode}';
        final startTime = DateTime.now();

        // Track first network request time (for startup metrics)
        if (!firstRequestTrackedLocal) {
          firstRequestTrackedLocal = true;
          final timeToFirstRequest = appStartTime != null
              ? DateTime.now().difference(appStartTime).inMilliseconds
              : null;
          if (timeToFirstRequest != null) {
            final logger = StructuredLogger();
            safeUnawaited(
              logger.log(
                level: 'info',
                subsystem: 'performance',
                message: 'First network request initiated',
                context: 'app_startup',
                durationMs: timeToFirstRequest,
                extra: {
                  'time_to_first_request_ms': timeToFirstRequest,
                  'metric_type': 'first_network_request',
                  'url': request.uri.toString(),
                  'method': request.method,
                },
              ),
            );
          }
        }

        // Store operation ID and start time in request extra for later use
        request.extra['operation_id'] = operationId;
        request.extra['start_time'] = startTime;

        final logger = StructuredLogger();

        // Calculate body size for POST/PUT/PATCH requests
        int? bodySize;
        if (request.data != null) {
          if (request.data is String) {
            bodySize = (request.data as String).length;
          } else if (request.data is List<int>) {
            bodySize = (request.data as List<int>).length;
          } else if (request.data is Map || request.data is List) {
            // For complex objects, estimate size (approximate)
            try {
              final jsonString = jsonEncode(request.data);
              bodySize = jsonString.length;
            } on Exception {
              bodySize = null;
            }
          }
        }

        await logger.log(
          level: 'debug',
          subsystem: 'network',
          message: 'HTTP request started',
          operationId: operationId,
          context: 'http_request',
          extra: {
            'method': request.method,
            'url': request.uri.toString(),
            'base_url': request.baseUrl,
            'path': request.path,
            'query_params': request.queryParameters,
            'headers': request.headers,
            if (bodySize != null) 'request_body_size_bytes': bodySize,
            'content_type': request.contentType?.toString(),
          },
        );
        return handler.next(request);
      },
      onResponse: (response, handler) async {
        final logger = StructuredLogger();
        final operationId =
            response.requestOptions.extra['operation_id'] as String?;
        final startTime =
            response.requestOptions.extra['start_time'] as DateTime?;

        final requestUrl = response.requestOptions.uri.toString();
        final responseUrl = response.realUri.toString();
        final isRedirect = requestUrl != responseUrl;

        // Calculate response size
        int? responseSize;
        if (response.data != null) {
          if (response.data is String) {
            responseSize = (response.data as String).length;
          } else if (response.data is List<int>) {
            responseSize = (response.data as List<int>).length;
          }
        }

        // Extract important headers
        final headers = response.headers;

        // Build redirect chain info if redirect occurred
        Map<String, dynamic>? redirectChain;
        if (isRedirect) {
          redirectChain = {
            'redirect_count':
                1, // Dio handles redirects internally, we see final result
            'original_url': requestUrl,
            'final_url': responseUrl,
            'redirect_detected': true,
            'status_code': response.statusCode,
            'location_header': headers.value('location') ?? '',
          };
        }
        final serverHeader = headers.value('server') ?? '';
        final cfRayHeader = headers.value('cf-ray') ?? '';
        final locationHeader = headers.value('location') ?? '';
        final contentTypeHeader = headers.value('content-type') ?? '';

        final duration = startTime != null
            ? DateTime.now().difference(startTime).inMilliseconds
            : null;

        // Log performance metrics for slow requests
        final isSlowRequest = duration != null && duration > 2000;
        final isVerySlowRequest = duration != null && duration > 5000;

        await logger.log(
          level: 'info',
          subsystem: isSlowRequest ? 'performance' : 'network',
          message: isSlowRequest
              ? (isVerySlowRequest
                  ? 'HTTP response received (very slow)'
                  : 'HTTP response received (slow)')
              : 'HTTP response received',
          operationId: operationId,
          context: 'http_request',
          durationMs: duration,
          extra: {
            'method': response.requestOptions.method,
            'status_code': response.statusCode,
            'request_url': requestUrl,
            'response_url': responseUrl,
            'is_redirect': isRedirect,
            if (locationHeader.isNotEmpty) 'location': locationHeader,
            if (redirectChain != null) 'redirect_chain': redirectChain,
            if (responseSize != null) 'response_size_bytes': responseSize,
            if (isSlowRequest) 'performance_warning': true,
            if (isSlowRequest)
              'performance_category': isVerySlowRequest ? 'very_slow' : 'slow',
            if (isSlowRequest) 'original_subsystem': 'network',
            'headers': {
              'server': serverHeader,
              'cf-ray': cfRayHeader,
              'content-type': contentTypeHeader,
              'content-length': headers.value('content-length') ?? '',
            },
            'all_headers': headers.map,
          },
        );
        return handler.next(response);
      },
      onError: (error, handler) async {
        final logger = StructuredLogger();
        final operationId =
            error.requestOptions.extra['operation_id'] as String?;
        final startTime = error.requestOptions.extra['start_time'] as DateTime?;

        final duration = startTime != null
            ? DateTime.now().difference(startTime).inMilliseconds
            : null;

        // Determine error type with details
        final errorType = switch (error.type) {
          DioExceptionType.connectionTimeout => 'Connection timeout',
          DioExceptionType.sendTimeout => 'Send timeout',
          DioExceptionType.receiveTimeout => 'Receive timeout',
          DioExceptionType.badResponse => 'Bad response',
          DioExceptionType.cancel => 'Request cancelled',
          DioExceptionType.connectionError => 'Connection error',
          DioExceptionType.badCertificate => 'Bad certificate',
          DioExceptionType.unknown => 'Unknown error',
        };

        // Check if it's a DNS error
        final isDnsError = error.type == DioExceptionType.connectionError &&
            ((error.message?.toLowerCase().contains('host lookup') ?? false) ||
                (error.message
                        ?.toLowerCase()
                        .contains('no address associated with hostname') ??
                    false) ||
                (error.message
                        ?.toLowerCase()
                        .contains('name or service not known') ??
                    false));

        // Get retry count if available
        final retryCount = error.requestOptions.extra['retryCount'] as int?;
        final endpointRetry =
            error.requestOptions.extra['endpoint_retry'] as int?;

        await logger.log(
          level: 'error',
          subsystem: 'network',
          message: 'HTTP error occurred',
          operationId: operationId,
          context: 'http_request',
          durationMs: duration,
          cause: error.toString(),
          extra: {
            'method': error.requestOptions.method,
            'url': error.requestOptions.uri.toString(),
            'error_type': errorType,
            'dio_error_type': error.type.toString(),
            'error_message': error.message,
            'is_dns_error': isDnsError,
            'status_code': error.response?.statusCode,
            if (retryCount != null) 'retry_count': retryCount,
            if (endpointRetry != null) 'endpoint_retry_count': endpointRetry,
            'stack_trace': error.stackTrace.toString(),
            'response_data': error.response?.data?.toString() ?? '',
            'response_headers': error.response?.headers.map,
          },
        );
        return handler.next(error);
      },
    );
  }

  /// Creates an authentication and retry interceptor.
  ///
  /// This interceptor handles:
  /// - Authentication redirects (login page detection)
  /// - Endpoint switching on connection errors
  /// - Retry logic for idempotent requests
  ///
  /// The [dio] parameter is the Dio instance to use for retries.
  static Interceptor createAuthAndRetryInterceptor(Dio dio) =>
      InterceptorsWrapper(
      onResponse: (response, handler) {
        // Check if we got redirected to login page instead of the requested resource
        if (response.realUri.toString().contains('login.php') &&
            response.requestOptions.uri.toString().contains('rutracker')) {
          // This is an authentication redirect - reject with specific error
          return handler.reject(DioException(
            requestOptions: response.requestOptions,
            error: 'Authentication required',
            response: response,
            type: DioExceptionType.badResponse,
          ));
        }
        // Check for 401/403 status codes indicating authentication failure
        if (response.statusCode == 401 || response.statusCode == 403) {
          return handler.reject(DioException(
            requestOptions: response.requestOptions,
            error: 'Authentication required',
            response: response,
            type: DioExceptionType.badResponse,
          ));
        }
        return handler.next(response);
      },
      onError: (error, handler) async {
        // Auto-switch endpoint on connection errors
        final isConnectionError =
            error.type == DioExceptionType.connectionError ||
                error.type == DioExceptionType.connectionTimeout ||
                error.type == DioExceptionType.receiveTimeout;

        if (isConnectionError) {
          final message = error.message?.toLowerCase() ?? '';
          final isDnsError = error.type == DioExceptionType.connectionError &&
              (message.contains('host lookup') ||
                  message.contains('no address associated with hostname') ||
                  message.contains('name or service not known'));

          // Try to switch to another endpoint for DNS/timeout errors
          if (isDnsError || error.type == DioExceptionType.connectionTimeout) {
            final operationId =
                error.requestOptions.extra['operation_id'] as String?;
            final logger = StructuredLogger();
            final switchStartTime = DateTime.now();

            // Determine error type for logging
            final switchErrorType = switch (error.type) {
              DioExceptionType.connectionTimeout => 'Connection timeout',
              DioExceptionType.sendTimeout => 'Send timeout',
              DioExceptionType.receiveTimeout => 'Receive timeout',
              DioExceptionType.badResponse => 'Bad response',
              DioExceptionType.cancel => 'Request cancelled',
              DioExceptionType.connectionError => 'Connection error',
              DioExceptionType.badCertificate => 'Bad certificate',
              DioExceptionType.unknown => 'Unknown error',
            };

            await logger.log(
              level: 'info',
              subsystem: 'state',
              message: 'Attempting endpoint switch due to connection error',
              operationId: operationId,
              context: 'endpoint_switch_retry',
              extra: {
                'error_type': switchErrorType,
                'is_dns_error': isDnsError,
                'current_endpoint': error.requestOptions.baseUrl,
                'url': error.requestOptions.uri.toString(),
                'switch_reason':
                    isDnsError ? 'DNS_lookup_failed' : 'Connection_timeout',
                'original_subsystem': 'network',
              },
            );

            try {
              final db = AppDatabase().database;
              final endpointManager = EndpointManager(db);
              final currentEndpoint = error.requestOptions.baseUrl;

              // Try to switch to another endpoint
              final switchAttemptStartTime = DateTime.now();
              final switched = await endpointManager.trySwitchEndpoint(
                currentEndpoint,
              );
              final switchAttemptDuration = DateTime.now()
                  .difference(switchAttemptStartTime)
                  .inMilliseconds;

              if (switched) {
                final newEndpoint = await endpointManager.getActiveEndpoint();

                // Update Dio baseUrl
                dio.options.baseUrl = newEndpoint;
                dio.options.headers['Referer'] = '$newEndpoint/';

                await logger.log(
                  level: 'info',
                  subsystem: 'state',
                  message: 'Endpoint switched, updating Dio client',
                  operationId: operationId,
                  context: 'endpoint_switch_retry',
                  durationMs: switchAttemptDuration,
                  extra: {
                    'old_endpoint': currentEndpoint,
                    'new_endpoint': newEndpoint,
                    'dio_base_url_updated': true,
                    'original_subsystem': 'network',
                  },
                );

                // Retry request on new endpoint (only once to avoid loops)
                final retryCount =
                    (error.requestOptions.extra['endpoint_retry'] as int?) ?? 0;
                if (retryCount < 1) {
                  final retryStartTime = DateTime.now();
                  final newOptions = error.requestOptions.copyWith(
                    baseUrl: newEndpoint,
                    extra: {
                      ...error.requestOptions.extra,
                      'endpoint_retry': retryCount + 1,
                    },
                  );

                  try {
                    final retried = await dio.fetch(newOptions);
                    final retryDuration = DateTime.now()
                        .difference(retryStartTime)
                        .inMilliseconds;
                    final totalDuration = DateTime.now()
                        .difference(switchStartTime)
                        .inMilliseconds;

                    await logger.log(
                      level: 'info',
                      subsystem: 'retry',
                      message:
                          'Successfully switched endpoint and retried request',
                      operationId: operationId,
                      context: 'endpoint_switch_retry',
                      durationMs: totalDuration,
                      extra: {
                        'old_endpoint': currentEndpoint,
                        'new_endpoint': newEndpoint,
                        'url': error.requestOptions.uri.toString(),
                        'status_code': retried.statusCode,
                        'retry_attempt': retryCount + 1,
                        'switch_duration_ms': switchAttemptDuration,
                        'retry_duration_ms': retryDuration,
                        'original_subsystem': 'network',
                      },
                    );
                    return handler.resolve(retried);
                  } on Exception catch (e) {
                    final retryDuration = DateTime.now()
                        .difference(retryStartTime)
                        .inMilliseconds;
                    final totalDuration = DateTime.now()
                        .difference(switchStartTime)
                        .inMilliseconds;

                    await logger.log(
                      level: 'warning',
                      subsystem: 'retry',
                      message: 'Failed to retry on new endpoint',
                      operationId: operationId,
                      context: 'endpoint_switch_retry',
                      durationMs: totalDuration,
                      cause: e.toString(),
                      extra: {
                        'old_endpoint': currentEndpoint,
                        'new_endpoint': newEndpoint,
                        'url': error.requestOptions.uri.toString(),
                        'retry_attempt': retryCount + 1,
                        'switch_duration_ms': switchAttemptDuration,
                        'retry_duration_ms': retryDuration,
                        'stack_trace': (e is Error)
                            ? (e as Error).stackTrace.toString()
                            : null,
                        'original_subsystem': 'network',
                      },
                    );
                  }
                } else {
                  await logger.log(
                    level: 'warning',
                    subsystem: 'retry',
                    message:
                        'Endpoint switch successful but retry limit reached',
                    operationId: operationId,
                    context: 'endpoint_switch_retry',
                    extra: {
                      'old_endpoint': currentEndpoint,
                      'new_endpoint': newEndpoint,
                      'retry_count': retryCount,
                      'original_subsystem': 'network',
                    },
                  );
                }
              } else {
                final totalDuration =
                    DateTime.now().difference(switchStartTime).inMilliseconds;
                await logger.log(
                  level: 'warning',
                  subsystem: 'state',
                  message:
                      'Endpoint switch attempt failed - no alternative available',
                  operationId: operationId,
                  context: 'endpoint_switch_retry',
                  durationMs: totalDuration,
                  extra: {
                    'current_endpoint': currentEndpoint,
                    'switch_duration_ms': switchAttemptDuration,
                    'switch_reason': 'no_alternative_endpoint',
                    'original_subsystem': 'network',
                  },
                );
              }
            } on Exception catch (e) {
              final totalDuration =
                  DateTime.now().difference(switchStartTime).inMilliseconds;
              await logger.log(
                level: 'error',
                subsystem: 'state',
                message: 'Failed to switch endpoint - exception',
                operationId: operationId,
                context: 'endpoint_switch_retry',
                durationMs: totalDuration,
                cause: e.toString(),
                extra: {
                  'current_endpoint': error.requestOptions.baseUrl,
                  'switch_reason': 'exception_during_switch',
                  'stack_trace':
                      (e is Error) ? (e as Error).stackTrace.toString() : null,
                  'original_subsystem': 'network',
                },
              );
            }
          }
        }

        // Handle authentication errors (401/403)
        final status = error.response?.statusCode ?? 0;
        if (status == 401 ||
            status == 403 ||
            (error.message?.contains('Authentication required') ?? false)) {
          // Try to sync cookies one more time before rejecting
          // Note: CookieJar will be obtained from DioClient if needed
          try {
            await DioCookieManager.syncCookiesFromWebView();
          } on Exception {
            // Continue to reject even if sync fails
          }
          // Reject with authentication error
          return handler.reject(DioException(
            requestOptions: error.requestOptions,
            error: 'Authentication required',
            response: error.response,
            type: DioExceptionType.badResponse,
          ));
        }

        // Retry logic for idempotent requests (GET, HEAD, OPTIONS) on temporary issues
        final method = error.requestOptions.method.toUpperCase();
        final isIdempotent =
            method == 'GET' || method == 'HEAD' || method == 'OPTIONS';
        final isTemporary = error.type == DioExceptionType.connectionTimeout ||
            error.type == DioExceptionType.receiveTimeout ||
            error.type == DioExceptionType.sendTimeout ||
            status == 429 ||
            (status >= 500 && status < 600);

        if (isIdempotent && isTemporary) {
          // Up to 3 retries with exponential backoff + jitter
          final retryCount =
              (error.requestOptions.extra['retryCount'] as int?) ?? 0;
          final operationId =
              error.requestOptions.extra['operation_id'] as String?;

          if (retryCount < 3) {
            int baseDelayMs;
            int? retryAfterSeconds;
            if (status == 429) {
              // Honor Retry-After if provided
              final retryAfter = error.response?.headers.value('retry-after');
              retryAfterSeconds = int.tryParse(retryAfter ?? '') ?? 0;
              baseDelayMs = (retryAfterSeconds > 0
                  ? retryAfterSeconds * 1000
                  : (500 * (1 << retryCount)));
            } else {
              baseDelayMs = 500 * (1 << retryCount);
            }
            // jitter 0..250ms
            final jitterMs = DateTime.now().microsecondsSinceEpoch % 250;
            final delayMs = baseDelayMs + jitterMs;

            await StructuredLogger().log(
              level: 'warning',
              subsystem: 'retry',
              message: 'Retrying HTTP request with exponential backoff',
              operationId: operationId,
              context: 'http_retry',
              extra: {
                'url': error.requestOptions.uri.toString(),
                'method': error.requestOptions.method,
                'status': status,
                'retry_attempt': retryCount + 1,
                'max_retries': 3,
                'delay_breakdown': {
                  'base_delay_ms': baseDelayMs,
                  'jitter_ms': jitterMs,
                  'total_delay_ms': delayMs,
                },
                'error_type': error.type.toString(),
                'is_exponential_backoff': true,
                'backoff_type': 'exponential_with_jitter',
                'exponential_factor': retryCount + 1,
                'base_delay_formula': '500 * (2^retry_count)',
                'backoff_formula': 'base_delay + jitter (0-250ms)',
                if (retryAfterSeconds != null && retryAfterSeconds > 0)
                  'retry_after_header_seconds': retryAfterSeconds,
                'original_subsystem': 'network',
              },
            );

            await Future.delayed(Duration(milliseconds: delayMs));

            final newOptions = error.requestOptions.copyWith(
              extra: {
                ...error.requestOptions.extra,
                'retryCount': retryCount + 1
              },
            );

            try {
              final retried = await dio.fetch(newOptions);

              // Log successful retry
              await StructuredLogger().log(
                level: 'info',
                subsystem: 'retry',
                message: 'HTTP request retry succeeded',
                operationId: operationId,
                context: 'http_retry',
                extra: {
                  'url': error.requestOptions.uri.toString(),
                  'retry_attempt': retryCount + 1,
                  'status_code': retried.statusCode,
                  'original_subsystem': 'network',
                },
              );

              return handler.resolve(retried);
            } on Exception catch (e) {
              // Log failed retry
              await StructuredLogger().log(
                level: 'warning',
                subsystem: 'retry',
                message: 'HTTP request retry failed',
                operationId: operationId,
                context: 'http_retry',
                cause: e.toString(),
                extra: {
                  'url': error.requestOptions.uri.toString(),
                  'retry_attempt': retryCount + 1,
                  'original_subsystem': 'network',
                },
              );
              // Fall-through to next
            }
          } else {
            // Max retries reached
            await StructuredLogger().log(
              level: 'error',
              subsystem: 'retry',
              message: 'HTTP request max retries reached',
              operationId: operationId,
              context: 'http_retry',
              extra: {
                'url': error.requestOptions.uri.toString(),
                'max_retries': 3,
                'final_status': status,
                'original_subsystem': 'network',
              },
            );
          }
        }

        return handler.next(error);
      },
    );

  /// Creates a FlutterCookieBridge interceptor for automatic cookie synchronization.
  ///
  /// This interceptor merges cookies from SessionManager with existing Cookie header
  /// and saves cookies from responses to SessionManager.
  ///
  /// The [bridgeSessionManager] parameter is the SessionManager instance to use.
  static Interceptor createCookieBridgeInterceptor(
    bridge_session.SessionManager bridgeSessionManager,
  ) =>
      InterceptorsWrapper(
      onRequest: (request, handler) async {
        try {
          // Get cookies from FlutterCookieBridge SessionManager
          final sessionCookies = await bridgeSessionManager.getSessionCookies();
          if (sessionCookies.isNotEmpty) {
            // Merge with existing Cookie header (if any) from CookieManager
            final existingCookieHeader = request.headers['Cookie'];
            final sessionCookieHeader = sessionCookies.join('; ');
            
            if (existingCookieHeader != null && existingCookieHeader.isNotEmpty) {
              // Merge cookies: combine existing and session cookies, avoiding duplicates
              final existingCookies = existingCookieHeader.split('; ').map((c) => c.split('=').first).toSet();
              final newCookies = sessionCookies.where((c) {
                final name = c.split('=').first;
                return !existingCookies.contains(name);
              }).toList();
              
              if (newCookies.isNotEmpty) {
                request.headers['Cookie'] = '$existingCookieHeader; ${newCookies.join('; ')}';
              } else {
                request.headers['Cookie'] = existingCookieHeader;
              }
            } else {
              // No existing cookies, just use session cookies
              request.headers['Cookie'] = sessionCookieHeader;
            }
            
            await StructuredLogger().log(
              level: 'info',
              subsystem: 'cookies',
              message: 'Adding cookies from FlutterCookieBridge SessionManager to request',
              context: 'cookie_bridge_request',
              extra: {
                'uri': request.uri.toString(),
                'session_cookie_count': sessionCookies.length,
                'has_existing_cookies': existingCookieHeader != null && existingCookieHeader.isNotEmpty,
                'cookie_names': sessionCookies.map((c) => c.split('=').first).toList(),
                'final_cookie_header_length': request.headers['Cookie']?.length ?? 0,
              },
            );
          } else {
            // No cookies in SessionManager - log for debugging
            await StructuredLogger().log(
              level: 'debug',
              subsystem: 'cookies',
              message: 'No cookies found in FlutterCookieBridge SessionManager',
              context: 'cookie_bridge_request',
              extra: {
                'uri': request.uri.toString(),
                'note': 'Request will proceed without SessionManager cookies (may use CookieJar cookies)',
              },
            );
          }
        } on Exception catch (e) {
          await StructuredLogger().log(
            level: 'warning',
            subsystem: 'cookies',
            message: 'Failed to get cookies from FlutterCookieBridge SessionManager',
            context: 'cookie_bridge_request',
            cause: e.toString(),
            extra: {
              'uri': request.uri.toString(),
              'note': 'Request will proceed without SessionManager cookies',
            },
          );
        }
        handler.next(request);
      },
      onResponse: (response, handler) async {
        try {
          // Save cookies from response to FlutterCookieBridge SessionManager
          // This is how NetworkManager._storeResponseCookies works
          if (response.headers['set-cookie'] != null) {
            final cookiesList = response.headers['set-cookie']!;
            final filteredCookies = <String>[];
            
            for (final cookie in cookiesList) {
              // Extract actual cookie (before first semicolon)
              final actualCookie = cookie.split(';')[0];
              if (actualCookie.isNotEmpty && !actualCookie.contains('redirect_url=')) {
                filteredCookies.add(actualCookie);
              }
            }
            
            if (filteredCookies.isNotEmpty) {
              await bridgeSessionManager.saveSessionCookies(filteredCookies);
              
              await StructuredLogger().log(
                level: 'debug',
                subsystem: 'cookies',
                message: 'Saved cookies from response to FlutterCookieBridge',
                context: 'cookie_bridge_response',
                extra: {
                  'uri': response.requestOptions.uri.toString(),
                  'cookie_count': filteredCookies.length,
                  'cookie_names': filteredCookies.map((c) => c.split('=').first).toList(),
                },
              );
            }
          }
        } on Exception catch (e) {
          await StructuredLogger().log(
            level: 'debug',
            subsystem: 'cookies',
            message: 'Failed to save cookies to FlutterCookieBridge',
            context: 'cookie_bridge_response',
            cause: e.toString(),
          );
        }
        handler.next(response);
      },
    );
}
