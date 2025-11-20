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

import 'package:jabook/core/endpoints/endpoint_manager.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/data/db/app_database.dart';

/// Handles URL resolution and navigation for WebView.
class WebViewNavigationHandler {
  /// Private constructor to prevent instantiation.
  WebViewNavigationHandler._();

  /// Resolves the initial URL for WebView with fallback support.
  static Future<String> resolveInitialUrl() async {
    final db = AppDatabase().database;
    final endpointManager = EndpointManager(db);

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
          }
        } else {
          // Switch failed, try fallbacks
          endpoint = await tryFallbackEndpoints(fallbackEndpoints);
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

        return endpoint;
      } on Exception catch (e) {
        // Even validation failed, try fallbacks
        await StructuredLogger().log(
          level: 'warning',
          subsystem: 'webview',
          message: 'Endpoint validation failed, trying fallbacks',
          extra: {'failed_endpoint': endpoint, 'error': e.toString()},
        );
        return await tryFallbackEndpoints(fallbackEndpoints);
      }
    } on Exception catch (e) {
      // If all fails, use hardcoded fallback
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'webview',
        message: 'All endpoint resolution failed, using hardcoded fallback',
        cause: e.toString(),
      );
      return EndpointManager.getPrimaryFallbackEndpoint();
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
        final db = AppDatabase().database;
        final endpointManager = EndpointManager(db);
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

    // If all fallbacks failed, return the first one anyway
    await StructuredLogger().log(
      level: 'warning',
      subsystem: 'webview',
      message: 'All fallback endpoints failed, using first fallback',
      extra: {'endpoint': endpoints.first},
    );
    return endpoints.first;
  }
}
