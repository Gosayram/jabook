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
import 'dart:io' as io;

import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart' as dio_cookie;
import 'package:flutter_cookie_bridge/flutter_cookie_bridge.dart';
import 'package:flutter_cookie_bridge/session_manager.dart' as bridge_session;
import 'package:jabook/core/endpoints/endpoint_manager.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/core/net/user_agent_manager.dart';
import 'package:jabook/core/session/session_interceptor.dart';
import 'package:jabook/core/session/session_manager.dart';
import 'package:jabook/core/utils/safe_async.dart';
import 'package:jabook/data/db/app_database.dart';
import 'package:shared_preferences/shared_preferences.dart';

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
    dio.interceptors.add(InterceptorsWrapper(
      onRequest: (request, handler) async {
        final operationId =
            'http_request_${DateTime.now().millisecondsSinceEpoch}_${request.hashCode}';
        final startTime = DateTime.now();

        // Track first network request time (for startup metrics)
        if (!_firstRequestTracked) {
          _firstRequestTracked = true;
          final timeToFirstRequest = _appStartTime != null
              ? DateTime.now().difference(_appStartTime!).inMilliseconds
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
    ));

    // Add authentication redirect handler and resilient retry policy for idempotent requests
    dio.interceptors.add(InterceptorsWrapper(
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
        // NEW: Auto-switch endpoint on connection errors
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
          try {
            await syncCookiesFromWebView();
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
    ));

    // Initialize FlutterCookieBridge for automatic cookie synchronization
    // According to .report-issue-docs.md: this library handles cookie sync
    // between WebView and Dio automatically, ensuring legitimate Cloudflare connections
    _cookieBridge ??= FlutterCookieBridge();
    _bridgeSessionManager ??= bridge_session.SessionManager();
    
    // Initialize cookie jar
    _cookieJar ??= CookieJar();
    dio.interceptors.add(dio_cookie.CookieManager(_cookieJar!));
    
    // Add FlutterCookieBridge interceptor for automatic cookie sync
    // This ensures cookies from SessionManager are added to requests
    // and cookies from responses are saved to SessionManager
    dio.interceptors.add(InterceptorsWrapper(
      onRequest: (request, handler) async {
        try {
          // Get cookies from FlutterCookieBridge SessionManager
          final sessionCookies = await _bridgeSessionManager!.getSessionCookies();
          if (sessionCookies.isNotEmpty) {
            // Add cookies to request header (like NetworkManager does)
            final cookieHeader = sessionCookies.join('; ');
            request.headers['Cookie'] = cookieHeader;
            
            await StructuredLogger().log(
              level: 'debug',
              subsystem: 'cookies',
              message: 'Added cookies from FlutterCookieBridge to request',
              context: 'cookie_bridge_request',
              extra: {
                'uri': request.uri.toString(),
                'cookie_count': sessionCookies.length,
                'cookie_names': sessionCookies.map((c) => c.split('=').first).toList(),
              },
            );
          }
        } on Exception catch (e) {
          await StructuredLogger().log(
            level: 'debug',
            subsystem: 'cookies',
            message: 'Failed to get cookies from FlutterCookieBridge',
            context: 'cookie_bridge_request',
            cause: e.toString(),
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
              await _bridgeSessionManager!.saveSessionCookies(filteredCookies);
              
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
    final operationId =
        'cookie_sync_webview_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      await logger.log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'Cookie sync from WebView started',
        operationId: operationId,
        context: 'cookie_sync',
      );

      final prefs = await SharedPreferences.getInstance();
      final cookieJson = prefs.getString('rutracker_cookies_v1');
      if (cookieJson == null) {
        await logger.log(
          level: 'debug',
          subsystem: 'cookies',
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

      // Parse cookies list from JSON (as saved by secure webview)
      List<dynamic> list;
      try {
        list = jsonDecode(cookieJson) as List<dynamic>;
      } on Exception catch (e) {
        await logger.log(
          level: 'error',
          subsystem: 'cookies',
          message: 'Failed to parse cookie JSON',
          operationId: operationId,
          context: 'cookie_sync',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
          cause: e.toString(),
        );
        return;
      }

      await logger.log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'Parsed cookies from JSON',
        operationId: operationId,
        context: 'cookie_sync',
        extra: {
          'total_cookies': list.length,
          'active_endpoint': activeBase,
        },
      );

      if (list.isEmpty) {
        await logger.log(
          level: 'debug',
          subsystem: 'cookies',
          message: 'Cookie list is empty',
          operationId: operationId,
          context: 'cookie_sync',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
        );
        return;
      }

      _cookieJar ??= CookieJar();
      final cookies = <Cookie>[];
      final skippedCookies = <Map<String, dynamic>>[];
      final validName = RegExp(r"^[!#\$%&'*+.^_`|~0-9A-Za-z-]+$");
      var skippedCount = 0;

      for (final c in list) {
        try {
          final m = Map<String, dynamic>.from(c as Map);
          var name = m['name']?.toString();
          var value = m['value']?.toString();
          final originalName = name;

          if (name == null || value == null) {
            skippedCount++;
            skippedCookies.add({
              'name': originalName ?? '<null>',
              'reason': 'name_or_value_null',
            });
            continue;
          }

          // Trim and strip surrounding quotes
          name = name.trim();
          value = value.trim();
          if (name.isEmpty || value.isEmpty) {
            skippedCount++;
            skippedCookies.add({
              'name': originalName ?? '<unknown>',
              'reason': 'name_or_value_empty_after_trim',
            });
            continue;
          }

          if (name.length >= 2 && name.startsWith('"') && name.endsWith('"')) {
            name = name.substring(1, name.length - 1);
          }
          if (value.length >= 2 &&
              value.startsWith('"') &&
              value.endsWith('"')) {
            value = value.substring(1, value.length - 1);
          }

          // Validate cookie name format
          if (!validName.hasMatch(name)) {
            skippedCount++;
            skippedCookies.add({
              'name': name,
              'reason': 'invalid_name_format',
            });
            continue;
          }

          // Extract domain, path, and other properties
          final domain = (m['domain']?.toString() ?? uri.host).trim();
          final path = (m['path']?.toString() ?? '/').trim();

          // Validate domain matches the endpoint
          if (!domain.contains(uri.host) && !domain.startsWith('.')) {
            // Allow subdomains and exact matches
            if (domain != uri.host && !domain.endsWith(uri.host)) {
              skippedCount++;
              skippedCookies.add({
                'name': name,
                'domain': domain,
                'reason': 'domain_mismatch',
                'expected_host': uri.host,
              });
              continue;
            }
          }

          final cookie = Cookie(name, value)
            ..domain = domain
            ..path = path
            ..secure = true;

          cookies.add(cookie);
        } on Exception catch (e) {
          // Log individual cookie parsing errors but continue
          skippedCount++;
          skippedCookies.add({
            'reason': 'parse_exception',
            'error': e.toString(),
          });
        }
      }

      // Log parsed cookies (without values for security)
      if (cookies.isNotEmpty) {
        await logger.log(
          level: 'debug',
          subsystem: 'cookies',
          message: 'Parsed cookies from WebView',
          operationId: operationId,
          context: 'cookie_sync',
          extra: {
            'cookies': cookies
                .map((c) => {
                      'name': c.name,
                      'domain': c.domain ?? '<null>',
                      'path': c.path ?? '/',
                    })
                .toList(),
          },
        );
      }

      if (skippedCookies.isNotEmpty) {
        await logger.log(
          level: 'debug',
          subsystem: 'cookies',
          message: 'Skipped cookies during parsing',
          operationId: operationId,
          context: 'cookie_sync',
          extra: {
            'skipped_cookies': skippedCookies,
          },
        );
      }

      if (cookies.isEmpty) {
        await logger.log(
          level: 'warning',
          subsystem: 'cookies',
          message: 'No valid cookies to sync after parsing',
          operationId: operationId,
          context: 'cookie_sync',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
          extra: {
            'total': list.length,
            'skipped': skippedCount,
          },
        );
        return;
      }

      // Save cookies for all rutracker domains (net, me, org) to ensure
      // cookies work when switching between mirrors
      final rutrackerDomains = EndpointManager.getRutrackerDomains();
      final savedDomains = <String>[];
      final domainCookieCounts = <String, int>{};

      for (final domain in rutrackerDomains) {
        try {
          final domainUri = Uri.parse('https://$domain');
          // Filter cookies that belong to this domain or are domain-wide
          final domainCookies = cookies.where((cookie) {
            final cookieDomain = cookie.domain?.toLowerCase() ?? '';
            if (cookieDomain.isEmpty) return false;
            return cookieDomain == domain ||
                cookieDomain == '.$domain' ||
                cookieDomain.contains(domain) ||
                (cookieDomain.startsWith('.') &&
                    cookieDomain.substring(1) == domain);
          }).toList();

          if (domainCookies.isNotEmpty) {
            await _cookieJar!.saveFromResponse(domainUri, domainCookies);
            savedDomains.add(domain);
            domainCookieCounts[domain] = domainCookies.length;

            await logger.log(
              level: 'debug',
              subsystem: 'cookies',
              message: 'Saved cookies for domain',
              operationId: operationId,
              context: 'cookie_sync',
              extra: {
                'domain': domain,
                'cookie_count': domainCookies.length,
                'cookies': domainCookies
                    .map((c) => {
                          'name': c.name,
                          'domain': c.domain ?? '<null>',
                          'path': c.path ?? '/',
                        })
                    .toList(),
              },
            );
          }
        } on Exception catch (e) {
          await logger.log(
            level: 'debug',
            subsystem: 'cookies',
            message: 'Failed to save cookies for domain',
            operationId: operationId,
            context: 'cookie_sync',
            cause: e.toString(),
            extra: {'domain': domain},
          );
        }
      }

      // Also save to active endpoint URI
      if (!savedDomains.contains(uri.host)) {
        await _cookieJar!.saveFromResponse(uri, cookies);
        savedDomains.add(uri.host);
        domainCookieCounts[uri.host] = cookies.length;
      }

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'info',
        subsystem: 'cookies',
        message: 'Synced cookies from WebView to Dio',
        operationId: operationId,
        context: 'cookie_sync',
        durationMs: duration,
        extra: {
          'count': cookies.length,
          'total': list.length,
          'skipped': skippedCount,
          'domains': savedDomains,
          'domain_cookie_counts': domainCookieCounts,
        },
      );
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'warning',
        subsystem: 'cookies',
        message: 'Failed to sync cookies from WebView',
        operationId: operationId,
        context: 'cookie_sync',
        durationMs: duration,
        cause: e.toString(),
        extra: {
          'stack_trace':
              (e is Error) ? (e as Error).stackTrace.toString() : null,
        },
      );
    }
  }

  /// Saves cookies directly to Dio CookieJar.
  ///
  /// This is a helper method for direct cookie synchronization from WebView.
  /// It bypasses SharedPreferences and saves cookies directly to the cookie jar.
  static Future<void> saveCookiesDirectly(Uri uri, List<io.Cookie> cookies) async {
    final logger = StructuredLogger();
    final operationId = 'save_cookies_direct_${DateTime.now().millisecondsSinceEpoch}';
    
    await logger.log(
      level: 'debug',
      subsystem: 'cookies',
      message: 'Saving cookies directly to Dio CookieJar',
      operationId: operationId,
      context: 'cookie_save_direct',
      extra: {
        'uri': uri.toString(),
        'cookie_count': cookies.length,
        'cookies': cookies.map((c) => {
          'name': c.name,
          'domain': c.domain ?? '<null>',
          'path': c.path ?? '/',
          'has_value': c.value.isNotEmpty,
          'value_length': c.value.length,
          'secure': c.secure,
          'http_only': c.httpOnly,
        }).toList(),
      },
    );
    
      _cookieJar ??= CookieJar();
      
      // Verify cookies before saving
      await logger.log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'About to save cookies to Dio CookieJar',
        operationId: operationId,
        context: 'cookie_save_direct',
        extra: {
          'uri': uri.toString(),
          'cookie_count': cookies.length,
          'cookie_names': cookies.map((c) => c.name).toList(),
          'cookie_domains': cookies.map((c) => c.domain ?? '<null>').toList(),
        },
      );
      
      // CRITICAL: Try to save cookies using saveFromResponse
      // If that doesn't work, we'll try an alternative approach
      try {
        await _cookieJar!.saveFromResponse(uri, cookies);
      } on Exception catch (e) {
        await logger.log(
          level: 'warning',
          subsystem: 'cookies',
          message: 'saveFromResponse failed, trying alternative method',
          operationId: operationId,
          context: 'cookie_save_direct',
          cause: e.toString(),
        );
        
        // Alternative: Try saving cookies one by one with explicit domain/path
        // This ensures cookies are saved even if domain matching is strict
        for (final cookie in cookies) {
          try {
            // Create a URI that matches the cookie's domain
            final cookieUri = cookie.domain != null && cookie.domain!.isNotEmpty
                ? Uri.parse('https://${cookie.domain!.replaceFirst('.', '')}')
                : uri;
            
            // Save cookie with explicit domain/path
            final cookieToSave = io.Cookie(cookie.name, cookie.value)
              ..domain = cookie.domain ?? uri.host
              ..path = cookie.path ?? '/'
              ..secure = cookie.secure
              ..httpOnly = cookie.httpOnly;
            
            await _cookieJar!.saveFromResponse(cookieUri, [cookieToSave]);
          } on Exception catch (cookieError) {
            await logger.log(
              level: 'debug',
              subsystem: 'cookies',
              message: 'Failed to save individual cookie',
              operationId: operationId,
              context: 'cookie_save_direct',
              cause: cookieError.toString(),
              extra: {
                'cookie_name': cookie.name,
                'cookie_domain': cookie.domain,
              },
            );
          }
        }
      }
      
      // CRITICAL: Verify cookies were saved by loading them back
      // Use a small delay to ensure cookies are persisted
      await Future.delayed(const Duration(milliseconds: 100));
      
      // Try loading from multiple URI variations to ensure we find saved cookies
      final savedCookies = await _cookieJar!.loadForRequest(uri);
      final baseUri = Uri.parse('https://${uri.host}');
      final baseCookies = await _cookieJar!.loadForRequest(baseUri);
      
      // Combine cookies from both URIs (avoid duplicates)
      final allSavedCookies = <io.Cookie>[];
      for (final cookie in savedCookies) {
        if (!allSavedCookies.any((c) => c.name == cookie.name)) {
          allSavedCookies.add(cookie);
        }
      }
      for (final cookie in baseCookies) {
        if (!allSavedCookies.any((c) => c.name == cookie.name)) {
          allSavedCookies.add(cookie);
        }
      }
      
      await logger.log(
        level: 'info',
        subsystem: 'cookies',
        message: 'Cookies saved directly to Dio CookieJar',
        operationId: operationId,
        context: 'cookie_save_direct',
        extra: {
          'uri': uri.toString(),
          'uri_host': uri.host,
          'cookie_count': cookies.length,
          'saved_cookie_count': allSavedCookies.length,
          'saved_cookie_names': allSavedCookies.map((c) => c.name).toList(),
          'saved_cookies': allSavedCookies.map((c) => {
            'name': c.name,
            'domain': c.domain ?? '<null>',
            'path': c.path ?? '/',
            'has_value': c.value.isNotEmpty,
            'value_length': c.value.length,
            'value_preview': c.value.isNotEmpty 
                ? '${c.value.substring(0, c.value.length > 20 ? 20 : c.value.length)}...' 
                : '<empty>',
          }).toList(),
          'verification': allSavedCookies.length == cookies.length 
              ? 'success' 
              : 'mismatch',
          'uri_cookie_count': savedCookies.length,
          'base_cookie_count': baseCookies.length,
          'input_cookie_names': cookies.map((c) => c.name).toList(),
        },
      );
      
      // CRITICAL: If cookies were not saved, log error
      if (allSavedCookies.isEmpty && cookies.isNotEmpty) {
        await logger.log(
          level: 'error',
          subsystem: 'cookies',
          message: 'CRITICAL: Cookies were NOT saved to Dio CookieJar!',
          operationId: operationId,
          context: 'cookie_save_direct',
          extra: {
            'uri': uri.toString(),
            'uri_host': uri.host,
            'input_cookie_count': cookies.length,
            'saved_cookie_count': allSavedCookies.length,
            'input_cookie_names': cookies.map((c) => c.name).toList(),
            'input_cookie_domains': cookies.map((c) => c.domain ?? '<null>').toList(),
            'note': 'Cookies may not match domain or path requirements',
          },
        );
      }
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
    final operationId =
        'cookie_sync_dio_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      await logger.log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'Cookie sync to WebView started',
        operationId: operationId,
        context: 'cookie_sync',
      );

      _cookieJar ??= CookieJar();
      final db = AppDatabase().database;
      final endpointManager = EndpointManager(db);
      final activeBase = await endpointManager.getActiveEndpoint();
      final uri = Uri.parse(activeBase);

      // Load cookies for the active endpoint
      final cookies = await _cookieJar!.loadForRequest(uri);

      await logger.log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'Loaded cookies from Dio cookie jar',
        operationId: operationId,
        context: 'cookie_sync',
        extra: {
          'cookie_count': cookies.length,
          'endpoint': activeBase,
          'cookies': cookies
              .map((c) => {
                    'name': c.name,
                    'domain': c.domain ?? '<null>',
                    'path': c.path ?? '/',
                  })
              .toList(),
        },
      );

      if (cookies.isEmpty) {
        await logger.log(
          level: 'debug',
          subsystem: 'cookies',
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
        subsystem: 'cookies',
        message: 'Synced cookies from Dio to WebView storage',
        operationId: operationId,
        context: 'cookie_sync',
        durationMs: duration,
        extra: {
          'count': cookieList.length,
          'endpoint': activeBase,
          'cookies_synced': cookies
              .map((c) => {
                    'name': c.name,
                    'domain': c.domain ?? '<null>',
                    'path': c.path ?? '/',
                  })
              .toList(),
        },
      );
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'warning',
        subsystem: 'cookies',
        message: 'Failed to sync cookies to WebView',
        operationId: operationId,
        context: 'cookie_sync',
        durationMs: duration,
        cause: e.toString(),
        extra: {
          'stack_trace':
              (e is Error) ? (e as Error).stackTrace.toString() : null,
        },
      );
    }
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
    final operationId =
        'cookie_sync_endpoint_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      await logger.log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'Cookie sync on endpoint switch started',
        operationId: operationId,
        context: 'cookie_sync_endpoint_switch',
        extra: {
          'old_endpoint': oldEndpoint,
          'new_endpoint': newEndpoint,
        },
      );

      _cookieJar ??= CookieJar();
      final oldUri = Uri.parse(oldEndpoint);
      final newUri = Uri.parse(newEndpoint);

      // If endpoints are in the same domain family (rutracker.net/me/org),
      // cookies should be compatible
      final oldHost = oldUri.host;
      final newHost = newUri.host;
      final isSameFamily = oldHost.contains('rutracker') &&
          newHost.contains('rutracker') &&
          (oldHost.endsWith('.net') ||
              oldHost.endsWith('.me') ||
              oldHost.endsWith('.org')) &&
          (newHost.endsWith('.net') ||
              newHost.endsWith('.me') ||
              newHost.endsWith('.org'));

      if (!isSameFamily) {
        await logger.log(
          level: 'debug',
          subsystem: 'cookies',
          message:
              'Endpoints are not in same domain family, skipping cookie sync',
          operationId: operationId,
          context: 'cookie_sync_endpoint_switch',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
          extra: {
            'old_host': oldHost,
            'new_host': newHost,
            'is_same_family': false,
          },
        );
        return;
      }

      // Try to load cookies from old endpoint
      final oldCookies = await _cookieJar!.loadForRequest(oldUri);

      await logger.log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'Loaded cookies from old endpoint',
        operationId: operationId,
        context: 'cookie_sync_endpoint_switch',
        extra: {
          'old_endpoint': oldEndpoint,
          'cookie_count': oldCookies.length,
          'cookies': oldCookies
              .map((c) => {
                    'name': c.name,
                    'domain': c.domain ?? '<null>',
                    'path': c.path ?? '/',
                  })
              .toList(),
        },
      );

      if (oldCookies.isEmpty) {
        await logger.log(
          level: 'debug',
          subsystem: 'cookies',
          message: 'No cookies found for old endpoint',
          operationId: operationId,
          context: 'cookie_sync_endpoint_switch',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
          extra: {'old_endpoint': oldEndpoint},
        );
        return;
      }

      // Check if new endpoint already has cookies
      final newCookies = await _cookieJar!.loadForRequest(newUri);

      await logger.log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'Checked cookies for new endpoint',
        operationId: operationId,
        context: 'cookie_sync_endpoint_switch',
        extra: {
          'new_endpoint': newEndpoint,
          'cookie_count': newCookies.length,
          'has_existing_cookies': newCookies.isNotEmpty,
        },
      );

      if (newCookies.isNotEmpty) {
        await logger.log(
          level: 'debug',
          subsystem: 'cookies',
          message: 'New endpoint already has cookies, skipping sync',
          operationId: operationId,
          context: 'cookie_sync_endpoint_switch',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
          extra: {
            'new_endpoint': newEndpoint,
            'existing_cookie_count': newCookies.length,
          },
        );
        return;
      }

      // Copy compatible cookies to new endpoint
      // RuTracker cookies are typically domain-wide, so they should work
      final compatibleCookies = oldCookies.where((cookie) {
        // Copy cookies that are domain-wide or for rutracker domains
        final cookieDomain = cookie.domain?.toLowerCase() ?? '';
        return cookieDomain.isEmpty ||
            cookieDomain.contains('rutracker') ||
            cookieDomain.startsWith('.');
      }).toList();

      final incompatibleCookies = oldCookies.length - compatibleCookies.length;

      await logger.log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'Filtered compatible cookies for endpoint switch',
        operationId: operationId,
        context: 'cookie_sync_endpoint_switch',
        extra: {
          'total_old_cookies': oldCookies.length,
          'compatible_cookies': compatibleCookies.length,
          'incompatible_cookies': incompatibleCookies,
          'compatible_cookies_list': compatibleCookies
              .map((c) => {
                    'name': c.name,
                    'domain': c.domain ?? '<null>',
                    'path': c.path ?? '/',
                  })
              .toList(),
        },
      );

      if (compatibleCookies.isNotEmpty) {
        // Save cookies for all rutracker domains to ensure compatibility
        final rutrackerDomains = EndpointManager.getRutrackerDomains();

        final savedDomains = <String>[];
        for (final domain in rutrackerDomains) {
          final domainUri = Uri.parse('https://$domain');
          await _cookieJar!.saveFromResponse(domainUri, compatibleCookies);
          savedDomains.add(domain);

          await logger.log(
            level: 'debug',
            subsystem: 'cookies',
            message: 'Saved cookies for domain during endpoint switch',
            operationId: operationId,
            context: 'cookie_sync_endpoint_switch',
            extra: {
              'domain': domain,
              'cookie_count': compatibleCookies.length,
            },
          );
        }

        final duration = DateTime.now().difference(startTime).inMilliseconds;
        await logger.log(
          level: 'info',
          subsystem: 'cookies',
          message: 'Synced cookies on endpoint switch',
          operationId: operationId,
          context: 'cookie_sync_endpoint_switch',
          durationMs: duration,
          extra: {
            'old_endpoint': oldEndpoint,
            'new_endpoint': newEndpoint,
            'cookies_synced': compatibleCookies.length,
            'domains_saved_to': savedDomains,
            'cookies': compatibleCookies
                .map((c) => {
                      'name': c.name,
                      'domain': c.domain ?? '<null>',
                      'path': c.path ?? '/',
                    })
                .toList(),
          },
        );
      } else {
        final duration = DateTime.now().difference(startTime).inMilliseconds;
        await logger.log(
          level: 'warning',
          subsystem: 'cookies',
          message: 'No compatible cookies to sync on endpoint switch',
          operationId: operationId,
          context: 'cookie_sync_endpoint_switch',
          durationMs: duration,
          extra: {
            'old_endpoint': oldEndpoint,
            'new_endpoint': newEndpoint,
            'total_old_cookies': oldCookies.length,
          },
        );
      }
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'warning',
        subsystem: 'cookies',
        message: 'Failed to sync cookies on endpoint switch',
        operationId: operationId,
        context: 'cookie_sync_endpoint_switch',
        durationMs: duration,
        cause: e.toString(),
        extra: {
          'old_endpoint': oldEndpoint,
          'new_endpoint': newEndpoint,
          'stack_trace':
              (e is Error) ? (e as Error).stackTrace.toString() : null,
        },
      );
    }
  }

  /// Clears all stored cookies from the cookie jar.
  ///
  /// This method removes all cookies, effectively logging out the user
  /// from any authenticated sessions.
  static Future<void> clearCookies() async {
    await _cookieJar?.deleteAll();

    // Also clear from SharedPreferences
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('rutracker_cookies_v1');

    await StructuredLogger().log(
      level: 'info',
      subsystem: 'auth',
      message: 'Cleared all cookies',
    );
  }

  /// Checks if valid cookies are available for authentication.
  ///
  /// Returns true if cookies exist and appear to be valid, false otherwise.
  static Future<bool> hasValidCookies() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final cookieJson = prefs.getString('rutracker_cookies_v1');

      if (cookieJson == null || cookieJson.isEmpty) {
        return false;
      }

      // Check if cookies are in valid JSON format
      try {
        final list = jsonDecode(cookieJson) as List<dynamic>;
        if (list.isEmpty) {
          return false;
        }

        // Check if we have at least one cookie with a value
        var hasValidCookie = false;
        for (final c in list) {
          final cookie = c as Map<String, dynamic>;
          final name = cookie['name'] as String?;
          final value = cookie['value'] as String?;

          if (name != null &&
              name.isNotEmpty &&
              value != null &&
              value.isNotEmpty) {
            hasValidCookie = true;
            break;
          }
        }

        return hasValidCookie;
      } on FormatException {
        return false;
      }
    } on Exception {
      return false;
    }
  }

  /// Validates cookies by making a test request to RuTracker.
  ///
  /// Returns true if cookies are valid and authentication is active, false otherwise.
  static Future<bool> validateCookies() async {
    final operationId =
        'cookie_validate_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      await logger.log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'Cookie validation started',
        operationId: operationId,
        context: 'cookie_validation',
      );

      final hasCookiesCheckStartTime = DateTime.now();
      final hasCookies = await hasValidCookies();
      final hasCookiesCheckDuration =
          DateTime.now().difference(hasCookiesCheckStartTime).inMilliseconds;

      if (!hasCookies) {
        await logger.log(
          level: 'debug',
          subsystem: 'cookies',
          message: 'No cookies available for validation',
          operationId: operationId,
          context: 'cookie_validation',
          durationMs: hasCookiesCheckDuration,
        );
        return false;
      }

      // Only sync from WebView if we don't have cookies in Dio jar
      // This prevents unnecessary syncs when cookies are already synced
      try {
        _cookieJar ??= CookieJar();
        final db = AppDatabase().database;
        final endpointManager = EndpointManager(db);
        final activeBase = await endpointManager.getActiveEndpoint();
        final uri = Uri.parse(activeBase);

        await logger.log(
          level: 'debug',
          subsystem: 'cookies',
          message: 'Loading cookies from Dio cookie jar for validation',
          operationId: operationId,
          context: 'cookie_validation',
          extra: {
            'endpoint': activeBase,
            'uri_host': uri.host,
          },
        );

        final loadCookiesStartTime = DateTime.now();
        final dioCookies = await _cookieJar!.loadForRequest(uri);
        final loadCookiesDuration =
            DateTime.now().difference(loadCookiesStartTime).inMilliseconds;

        await logger.log(
          level: 'debug',
          subsystem: 'cookies',
          message: 'Loaded cookies from Dio cookie jar',
          operationId: operationId,
          context: 'cookie_validation',
          durationMs: loadCookiesDuration,
          extra: {
            'cookie_count': dioCookies.length,
            'cookies': dioCookies
                .map((c) => {
                      'name': c.name,
                      'domain': c.domain ?? '<null>',
                      'path': c.path ?? '/',
                      'has_value': c.value.isNotEmpty,
                      'value_length': c.value.length,
                      'value_preview': c.value.isNotEmpty 
                          ? '${c.value.substring(0, c.value.length > 20 ? 20 : c.value.length)}...' 
                          : '<empty>',
                      'is_session_cookie': c.name.toLowerCase().contains('session') ||
                          c.name == 'bb_session' ||
                          c.name == 'bb_data',
                    })
                .toList(),
            'endpoint': activeBase,
          },
        );

        if (dioCookies.isEmpty) {
          // No cookies in Dio jar, sync from WebView
          await logger.log(
            level: 'warning',
            subsystem: 'cookies',
            message: 'No cookies in Dio jar, syncing from WebView',
            operationId: operationId,
            context: 'cookie_validation',
            extra: {
              'endpoint': activeBase,
              'uri_host': uri.host,
            },
          );
          await syncCookiesFromWebView();
          
          // Check again after sync
          final dioCookiesAfterSync = await _cookieJar!.loadForRequest(uri);
          await logger.log(
            level: 'info',
            subsystem: 'cookies',
            message: 'Cookies in Dio jar after sync from WebView',
            operationId: operationId,
            context: 'cookie_validation',
            extra: {
              'cookie_count': dioCookiesAfterSync.length,
              'cookies': dioCookiesAfterSync.map((c) => {
                'name': c.name,
                'domain': c.domain ?? '<null>',
                'path': c.path ?? '/',
                'has_value': c.value.isNotEmpty,
                'value_length': c.value.length,
                'value_preview': c.value.isNotEmpty 
                    ? '${c.value.substring(0, c.value.length > 20 ? 20 : c.value.length)}...' 
                    : '<empty>',
                'is_session_cookie': c.name.toLowerCase().contains('session') ||
                    c.name == 'bb_session' ||
                    c.name == 'bb_data',
              }).toList(),
            },
          );
          
          if (dioCookiesAfterSync.isEmpty) {
            await logger.log(
              level: 'error',
              subsystem: 'cookies',
              message: 'No cookies in Dio jar after sync from WebView - validation will fail',
              operationId: operationId,
              context: 'cookie_validation',
            );
            return false;
          }
        }
      } on Exception catch (e) {
        await logger.log(
          level: 'warning',
          subsystem: 'cookies',
          message: 'Exception checking Dio cookies, syncing from WebView',
          operationId: operationId,
          context: 'cookie_validation',
          cause: e.toString(),
        );
        // If check fails, sync anyway for safety
        await syncCookiesFromWebView();
      }

      // Make a lightweight test request to check authentication
      final dio = await instance;
      final db = AppDatabase().database;
      final endpointManager = EndpointManager(db);
      final activeBase = await endpointManager.getActiveEndpoint();
      final testUrl = '$activeBase/index.php';

      await logger.log(
        level: 'debug',
        subsystem: 'cookies',
        message: 'Making test request to validate cookies',
        operationId: operationId,
        context: 'cookie_validation',
        extra: {
          'test_url': testUrl,
          'endpoint': activeBase,
        },
      );

      try {
        // Try to access a protected page (user profile or settings would require auth)
        // Using index.php as a lightweight test
        // Use normal request flow - SessionInterceptor now allows requests with cookies
        final testRequestStartTime = DateTime.now();
        final response = await dio
            .get(
              testUrl,
              options: Options(
                validateStatus: (status) => status != null && status < 500,
                followRedirects: false,
              ),
            )
            .timeout(const Duration(seconds: 10));

        final testRequestDuration =
            DateTime.now().difference(testRequestStartTime).inMilliseconds;

        // If we get redirected to login, cookies are invalid
        final location = response.headers.value('location');
        final isRedirectedToLogin = location != null &&
            (location.contains('login.php') || location.contains('login'));

        if (isRedirectedToLogin) {
          final totalDuration =
              DateTime.now().difference(startTime).inMilliseconds;
          await logger.log(
            level: 'info',
            subsystem: 'cookies',
            message: 'Cookie validation failed - redirected to login',
            operationId: operationId,
            context: 'cookie_validation',
            durationMs: totalDuration,
            extra: {
              'test_url': testUrl,
              'status_code': response.statusCode,
              'redirect_location': location,
              'test_request_duration_ms': testRequestDuration,
            },
          );
          return false;
        }

        // If we get a successful response or a non-login redirect, cookies are likely valid
        final isValid = response.statusCode != null &&
            response.statusCode! >= 200 &&
            response.statusCode! < 400;

        final totalDuration =
            DateTime.now().difference(startTime).inMilliseconds;
        await logger.log(
          level: isValid ? 'info' : 'warning',
          subsystem: 'cookies',
          message: 'Cookie validation ${isValid ? "succeeded" : "failed"}',
          operationId: operationId,
          context: 'cookie_validation',
          durationMs: totalDuration,
          extra: {
            'test_url': testUrl,
            'status_code': response.statusCode,
            'is_valid': isValid,
            'redirect_location': location,
            'test_request_duration_ms': testRequestDuration,
            'response_headers': {
              'server': response.headers.value('server') ?? '',
              'cf-ray': response.headers.value('cf-ray') ?? '',
            },
          },
        );

        return isValid;
      } on TimeoutException {
        final totalDuration =
            DateTime.now().difference(startTime).inMilliseconds;
        await logger.log(
          level: 'warning',
          subsystem: 'cookies',
          message: 'Cookie validation timed out',
          operationId: operationId,
          context: 'cookie_validation',
          durationMs: totalDuration,
          extra: {
            'test_url': testUrl,
            'timeout_seconds': 10,
          },
        );
        // Assume valid if request times out (network issue, not auth issue)
        return true;
      } on DioException catch (e) {
        final totalDuration =
            DateTime.now().difference(startTime).inMilliseconds;
        final errorType = switch (e.type) {
          DioExceptionType.connectionTimeout => 'Connection timeout',
          DioExceptionType.sendTimeout => 'Send timeout',
          DioExceptionType.receiveTimeout => 'Receive timeout',
          DioExceptionType.badResponse => 'Bad response',
          DioExceptionType.cancel => 'Request cancelled',
          DioExceptionType.connectionError => 'Connection error',
          DioExceptionType.badCertificate => 'Bad certificate',
          DioExceptionType.unknown => 'Unknown error',
        };

        if (e.response?.statusCode == 401 ||
            e.response?.statusCode == 403 ||
            (e.message?.contains('Authentication required') ?? false)) {
          await logger.log(
            level: 'info',
            subsystem: 'cookies',
            message: 'Cookie validation failed - authentication required',
            operationId: operationId,
            context: 'cookie_validation',
            durationMs: totalDuration,
            cause: e.toString(),
            extra: {
              'test_url': testUrl,
              'status_code': e.response?.statusCode,
              'error_type': errorType,
            },
          );
          return false;
        }
        // For other errors, assume cookies might still be valid
        await logger.log(
          level: 'warning',
          subsystem: 'cookies',
          message: 'Cookie validation error (assuming valid)',
          operationId: operationId,
          context: 'cookie_validation',
          durationMs: totalDuration,
          cause: e.toString(),
          extra: {
            'test_url': testUrl,
            'error_type': errorType,
            'status_code': e.response?.statusCode,
            'stack_trace': e.stackTrace.toString(),
          },
        );
        return true;
      }
    } on Exception catch (e) {
      final totalDuration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'cookies',
        message: 'Failed to validate cookies',
        operationId: operationId,
        context: 'cookie_validation',
        durationMs: totalDuration,
        cause: e.toString(),
        extra: {
          'stack_trace':
              (e is Error) ? (e as Error).stackTrace.toString() : null,
        },
      );
      return false;
    }
  }
}
