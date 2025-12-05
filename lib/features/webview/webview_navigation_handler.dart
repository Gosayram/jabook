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

import 'package:jabook/core/data/local/database/app_database.dart';
import 'package:jabook/core/infrastructure/endpoints/endpoint_manager.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';

/// Handles URL resolution and navigation for WebView.
class WebViewNavigationHandler {
  /// Private constructor to prevent instantiation.
  WebViewNavigationHandler._();

  /// Resolves the initial URL for WebView with fallback support.
  /// Simplified version that relies on quickAvailabilityCheck which already does DNS and HTTP checks.
  static Future<String> resolveInitialUrl() async {
    final appDb = AppDatabase.getInstance();
    // Ensure database is initialized before using it
    if (!appDb.isInitialized) {
      await appDb.ensureInitialized();
    }
    final db = appDb.database; // Use synchronous access like in old version
    final endpointManager = EndpointManager(db, appDb);

    // List of endpoints to try in order of preference
    final fallbackEndpoints = EndpointManager.getDefaultEndpointUrls();

    try {
      // Get active endpoint with health check
      var endpoint = await endpointManager.getActiveEndpoint();

      // Pre-check endpoint availability - this already does DNS lookup and HTTP check
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
      }

      // quickAvailabilityCheck already validated the endpoint (DNS + HTTP)
      // No need for additional validation
      await StructuredLogger().log(
        level: 'info',
        subsystem: 'webview',
        message: 'Endpoint resolved successfully',
        extra: {'endpoint': endpoint},
      );
      return endpoint;
    } on Exception catch (e) {
      // If all fails, try one more time with hardcoded fallback
      try {
        final hardcodedFallback = EndpointManager.getPrimaryFallbackEndpoint();
        final appDb = AppDatabase.getInstance();
        // Ensure database is initialized before using it
        if (!appDb.isInitialized) {
          await appDb.ensureInitialized();
        }
        final db = appDb.database; // Use synchronous access like in old version
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
  /// Uses quickAvailabilityCheck which already does DNS and HTTP validation.
  static Future<String> tryFallbackEndpoints(List<String> endpoints) async {
    final appDb = AppDatabase.getInstance();
    // Ensure database is initialized before using it
    if (!appDb.isInitialized) {
      await appDb.ensureInitialized();
    }
    final db = appDb.database; // Use synchronous access like in old version
    final endpointManager = EndpointManager(db, appDb);

    for (final fallback in endpoints) {
      try {
        // quickAvailabilityCheck already does DNS lookup and HTTP check
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
