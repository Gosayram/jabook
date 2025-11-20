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

import 'package:jabook/core/endpoints/endpoint_manager.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:sembast/sembast.dart';

/// Scheduler for managing automatic endpoint health checks.
///
/// This class handles periodic health checking of RuTracker endpoints
/// in the background and automatically switches to the best available mirror
/// when the current one becomes unhealthy.
class EndpointHealthScheduler {
  /// Creates a new instance of EndpointHealthScheduler.
  ///
  /// The [db] parameter is the database instance.
  EndpointHealthScheduler(this._db);

  /// Database instance.
  final Database _db;

  /// Store reference for scheduler state.
  final StoreRef<String, Map<String, dynamic>> _stateStore = StoreRef.main();

  /// Key for storing scheduler state.
  static const String _stateKey = 'endpoint_health_sync_state';

  /// Health check interval in hours (default: 12 hours - increased to reduce Cloudflare rate limiting).
  static const int healthCheckIntervalHours = 12;

  /// Checks if automatic health check should run.
  ///
  /// Returns true if health check should run, false otherwise.
  Future<bool> shouldRunHealthCheck() async {
    final state = await _getState();
    final now = DateTime.now();

    final lastCheck = state['last_health_check'] as String?;
    if (lastCheck == null) {
      // Never checked, run immediately
      return true;
    }

    final lastCheckDate = DateTime.parse(lastCheck);
    final hoursSinceCheck = now.difference(lastCheckDate).inHours;

    // Run if it's been more than the interval since last check
    return hoursSinceCheck >= healthCheckIntervalHours;
  }

  /// Runs health check for all enabled endpoints.
  ///
  /// This method checks the health of all enabled endpoints and
  /// automatically switches to the best available mirror if needed.
  Future<void> runHealthCheck() async {
    try {
      await StructuredLogger().log(
        level: 'info',
        subsystem: 'endpoint_health',
        message: 'Starting automatic endpoint health check',
      );

      final endpointManager = EndpointManager(_db);
      final endpoints = await endpointManager.getAllEndpoints();

      // Get current active endpoint
      String? currentActive;
      try {
        currentActive = await endpointManager.getActiveEndpoint();
      } on Exception {
        currentActive = null;
      }

      // Check all enabled endpoints
      var checkedCount = 0;
      var healthyCount = 0;
      for (final endpoint in endpoints) {
        final url = endpoint['url'] as String?;
        final enabled = endpoint['enabled'] as bool? ?? false;

        if (url == null || !enabled) continue;

        try {
          await endpointManager.healthCheck(url);
          checkedCount++;

          // Check if this endpoint is now healthy
          final updatedEndpoints = await endpointManager.getAllEndpoints();
          final updated = updatedEndpoints.firstWhere((e) => e['url'] == url,
              orElse: () => endpoint);
          final healthScore = updated['health_score'] as int? ?? 0;
          if (healthScore >= 60) {
            healthyCount++;
          }

          // Increased delay between checks to avoid Cloudflare rate limiting
          await Future.delayed(const Duration(seconds: 5));
        } on Exception catch (e) {
          await StructuredLogger().log(
            level: 'warning',
            subsystem: 'endpoint_health',
            message: 'Failed to check endpoint health',
            cause: e.toString(),
            extra: {'url': url},
          );
        }
      }

      // Check if current active endpoint is still healthy
      if (currentActive != null) {
        try {
          final updatedEndpoints = await endpointManager.getAllEndpoints();
          final currentEndpoint = updatedEndpoints
              .firstWhere((e) => e['url'] == currentActive, orElse: () => {});

          final enabled = currentEndpoint['enabled'] as bool? ?? false;
          final healthScore = currentEndpoint['health_score'] as int? ?? 0;
          final isHealthy = enabled && healthScore >= 60;

          if (!isHealthy) {
            // Current endpoint is unhealthy, switch to best available
            await StructuredLogger().log(
              level: 'info',
              subsystem: 'endpoint_health',
              message:
                  'Current active endpoint is unhealthy, switching to best available',
              extra: {'current': currentActive, 'health_score': healthScore},
            );

            try {
              final newActive = await endpointManager.getActiveEndpoint();
              await StructuredLogger().log(
                level: 'info',
                subsystem: 'endpoint_health',
                message: 'Switched to new active endpoint',
                extra: {'old': currentActive, 'new': newActive},
              );
            } on Exception catch (e) {
              await StructuredLogger().log(
                level: 'error',
                subsystem: 'endpoint_health',
                message: 'Failed to switch to new active endpoint',
                cause: e.toString(),
              );
            }
          }
        } on Exception catch (e) {
          await StructuredLogger().log(
            level: 'warning',
            subsystem: 'endpoint_health',
            message: 'Failed to verify current active endpoint',
            cause: e.toString(),
          );
        }
      }

      // Update last check time
      await _updateState({
        'last_health_check': DateTime.now().toIso8601String(),
      });

      await StructuredLogger().log(
        level: 'info',
        subsystem: 'endpoint_health',
        message: 'Completed automatic endpoint health check',
        extra: {
          'checked': checkedCount,
          'healthy': healthyCount,
        },
      );
    } on Exception catch (e) {
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'endpoint_health',
        message: 'Failed to run automatic health check',
        cause: e.toString(),
      );
    }
  }

  /// Runs automatic health check if conditions are met.
  ///
  /// This method checks if health check should run and executes it if needed.
  Future<void> runAutomaticHealthCheckIfNeeded() async {
    if (!await shouldRunHealthCheck()) {
      await StructuredLogger().log(
        level: 'debug',
        subsystem: 'endpoint_health',
        message: 'Skipping automatic health check (not needed)',
      );
      return;
    }

    await runHealthCheck();
  }

  /// Gets the current scheduler state.
  ///
  /// Returns a map with scheduler state information.
  Future<Map<String, dynamic>> _getState() async {
    final record = await _stateStore.record(_stateKey).get(_db);
    return record ?? <String, dynamic>{};
  }

  /// Updates the scheduler state.
  ///
  /// The [updates] parameter contains state fields to update.
  Future<void> _updateState(Map<String, dynamic> updates) async {
    final current = await _getState();
    final updated = {...current, ...updates};
    await _stateStore.record(_stateKey).put(_db, updated);
  }
}
