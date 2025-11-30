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

import 'dart:io' as io;

import 'package:dio/dio.dart';
import 'package:jabook/core/data/local/database/app_database.dart';
import 'package:jabook/core/infrastructure/endpoints/endpoint_manager.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:jabook/core/net/dio_client.dart';

/// Handles URL resolution and navigation for WebView.
class WebViewNavigationHandler {
  /// Private constructor to prevent instantiation.
  WebViewNavigationHandler._();

  /// Resolves the initial URL for WebView with fallback support.
  static Future<String> resolveInitialUrl() async {
    final appDb = AppDatabase.getInstance();
    final db = appDb.database;
    final endpointManager = EndpointManager(db, appDb);

    // List of endpoints to try in order of preference
    final fallbackEndpoints = EndpointManager.getDefaultEndpointUrls();

    try {
      // Get active endpoint with health check
      var endpoint = await endpointManager.getActiveEndpoint();

      // Pre-check endpoint availability before using it
      final isAvailable =
          await endpointManager.quickAvailabilityCheck(endpoint);

      if (!isAvailable) {
        // Current endpoint not available, try to switch
        await StructuredLogger().log(
          level: 'warning',
          subsystem: 'webview',
          message: 'Active endpoint not available, switching',
          extra: {'failed_endpoint': endpoint},
        );

        // Try to switch endpoint
        final switched = await endpointManager.trySwitchEndpoint(endpoint);
        if (switched) {
          endpoint = await endpointManager.getActiveEndpoint();
          // Re-check availability of new endpoint
          final newIsAvailable =
              await endpointManager.quickAvailabilityCheck(endpoint);
          if (!newIsAvailable) {
            // New endpoint also not available, try fallbacks
            endpoint = await tryFallbackEndpoints(fallbackEndpoints);
            // Validate fallback endpoint
            final dio = await DioClient.instance;
            try {
              await dio
                  .get(
                    '$endpoint/forum/index.php',
                    options: Options(
                      receiveTimeout: const Duration(seconds: 5),
                      validateStatus: (status) =>
                          status != null && status < 500,
                    ),
                  )
                  .timeout(const Duration(seconds: 5));
            } on Exception {
              // Fallback also failed - will be caught by outer try-catch
              throw Exception(
                'All RuTracker endpoints are unavailable. Please check your internet connection and try again later.',
              );
            }
          }
        } else {
          // Switch failed, try fallbacks
          endpoint = await tryFallbackEndpoints(fallbackEndpoints);
          // Validate fallback endpoint
          final dio = await DioClient.instance;
          try {
            await dio
                .get(
                  '$endpoint/forum/index.php',
                  options: Options(
                    receiveTimeout: const Duration(seconds: 5),
                    validateStatus: (status) => status != null && status < 500,
                  ),
                )
                .timeout(const Duration(seconds: 5));
          } on Exception {
            // Fallback also failed - will be caught by outer try-catch
            throw Exception(
              'All RuTracker endpoints are unavailable. Please check your internet connection and try again later.',
            );
          }
        }
      } else {
        // Endpoint is available, but do a quick DNS check to ensure it resolves
        try {
          final uri = Uri.parse(endpoint);
          await io.InternetAddress.lookup(uri.host)
              .timeout(const Duration(seconds: 3));
        } on Exception catch (e) {
          // DNS lookup failed, try fallbacks
          await StructuredLogger().log(
            level: 'warning',
            subsystem: 'webview',
            message: 'DNS lookup failed for endpoint, trying fallbacks',
            extra: {'failed_endpoint': endpoint, 'error': e.toString()},
          );
          endpoint = await tryFallbackEndpoints(fallbackEndpoints);
          // Validate fallback endpoint
          final dio = await DioClient.instance;
          try {
            await dio
                .get(
                  '$endpoint/forum/index.php',
                  options: Options(
                    receiveTimeout: const Duration(seconds: 5),
                    validateStatus: (status) => status != null && status < 500,
                  ),
                )
                .timeout(const Duration(seconds: 5));
          } on Exception {
            // Fallback also failed - will be caught by outer try-catch
            throw Exception(
              'All RuTracker endpoints are unavailable. Please check your internet connection and try again later.',
            );
          }
        }
      }

      // Final validation: ensure endpoint is accessible
      try {
        final dio = await DioClient.instance;
        await dio
            .get(
              '$endpoint/forum/index.php',
              options: Options(
                receiveTimeout: const Duration(seconds: 5),
                validateStatus: (status) => status != null && status < 500,
              ),
            )
            .timeout(const Duration(seconds: 5));

        await StructuredLogger().log(
          level: 'info',
          subsystem: 'webview',
          message: 'Endpoint validated successfully',
          extra: {'endpoint': endpoint},
        );
        return endpoint;
      } on Exception catch (e) {
        // Even validation failed, try fallbacks
        await StructuredLogger().log(
          level: 'warning',
          subsystem: 'webview',
          message: 'Endpoint validation failed, trying fallbacks',
          extra: {'failed_endpoint': endpoint, 'error': e.toString()},
        );
        final fallbackEndpoint = await tryFallbackEndpoints(fallbackEndpoints);
        // Validate the fallback endpoint before returning
        try {
          final dio = await DioClient.instance;
          await dio
              .get(
                '$fallbackEndpoint/forum/index.php',
                options: Options(
                  receiveTimeout: const Duration(seconds: 5),
                  validateStatus: (status) => status != null && status < 500,
                ),
              )
              .timeout(const Duration(seconds: 5));
          return fallbackEndpoint;
        } on Exception catch (validationError) {
          // Fallback endpoint also failed validation - this means all endpoints are unavailable
          await StructuredLogger().log(
            level: 'error',
            subsystem: 'webview',
            message:
                'Fallback endpoint validation failed - all endpoints unavailable',
            extra: {
              'fallback_endpoint': fallbackEndpoint,
              'error': validationError.toString(),
            },
          );
          throw Exception(
            'All RuTracker endpoints are unavailable. Please check your internet connection and try again later.',
          );
        }
      }
    } on Exception catch (e) {
      // If all fails, try one more time with hardcoded fallback
      try {
        final hardcodedFallback = EndpointManager.getPrimaryFallbackEndpoint();
        final appDb = AppDatabase.getInstance();
        final db = appDb.database;
        final endpointManager = EndpointManager(db, appDb);
        final isAvailable =
            await endpointManager.quickAvailabilityCheck(hardcodedFallback);

        if (isAvailable) {
          await StructuredLogger().log(
            level: 'warning',
            subsystem: 'webview',
            message: 'Using hardcoded fallback endpoint',
            extra: {'endpoint': hardcodedFallback},
          );
          return hardcodedFallback;
        }
      } on Exception {
        // Hardcoded fallback also failed
      }

      // If even hardcoded fallback failed, throw exception
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'webview',
        message: 'All endpoint resolution failed including hardcoded fallback',
        cause: e.toString(),
      );
      throw Exception(
        'All RuTracker endpoints are unavailable. Please check your internet connection and try again later.',
      );
    }
  }

  /// Tries fallback endpoints in order until one works.
  static Future<String> tryFallbackEndpoints(List<String> endpoints) async {
    for (final fallback in endpoints) {
      try {
        // Quick DNS check
        final uri = Uri.parse(fallback);
        await io.InternetAddress.lookup(uri.host)
            .timeout(const Duration(seconds: 2));

        // Quick availability check
        final appDb = AppDatabase.getInstance();
        final db = appDb.database;
        final endpointManager = EndpointManager(db, appDb);
        final isAvailable =
            await endpointManager.quickAvailabilityCheck(fallback);

        if (isAvailable) {
          await StructuredLogger().log(
            level: 'info',
            subsystem: 'webview',
            message: 'Using fallback endpoint',
            extra: {'endpoint': fallback},
          );
          return fallback;
        }
      } on Exception {
        // Try next fallback
        continue;
      }
    }

    // If all fallbacks failed, throw exception instead of returning unavailable endpoint
    await StructuredLogger().log(
      level: 'error',
      subsystem: 'webview',
      message: 'All fallback endpoints failed - no endpoints available',
      extra: {
        'tried_endpoints': endpoints,
        'count': endpoints.length,
      },
    );
    throw Exception(
      'All RuTracker endpoints are unavailable. Please check your internet connection and try again later.',
    );
  }
}
