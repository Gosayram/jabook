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
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';

/// Battery monitor for energy efficiency.
///
/// Monitors battery level and provides information for task throttling.
class BatteryMonitor {
  /// Private constructor for singleton pattern.
  BatteryMonitor._();

  /// Singleton instance.
  static final BatteryMonitor instance = BatteryMonitor._();

  /// Battery level threshold for slowing down tasks (15%).
  ///
  /// When battery level drops below this threshold, tasks will be slowed down.
  static const int lowBatteryThreshold = 15;

  /// Battery level threshold for critical slowdown (5%).
  ///
  /// When battery level drops below this threshold, tasks will be significantly slowed down.
  static const int criticalBatteryThreshold = 5;

  /// Logger for battery monitoring.
  final StructuredLogger _logger = StructuredLogger();

  /// Platform channel for battery information.
  static const MethodChannel _channel =
      MethodChannel('com.jabook.app.jabook/battery');

  /// Current battery level (0-100), null if unknown.
  int? _batteryLevel;

  /// Battery level stream controller.
  final StreamController<int> _batteryLevelController =
      StreamController<int>.broadcast();

  /// Stream of battery level updates.
  Stream<int> get batteryLevelStream => _batteryLevelController.stream;

  /// Periodic timer for battery level updates.
  Timer? _updateTimer;

  /// Initialize battery monitoring.
  Future<void> initialize() async {
    if (!Platform.isAndroid && !Platform.isIOS) {
      // Battery monitoring only available on mobile platforms
      await _logger.log(
        level: 'info',
        subsystem: 'battery_monitor',
        message: 'Battery monitoring not available on this platform',
      );
      return;
    }

    // Get initial battery level
    await _updateBatteryLevel();

    // Start periodic updates (every 30 seconds)
    _updateTimer = Timer.periodic(const Duration(seconds: 30), (_) {
      _updateBatteryLevel();
    });

    await _logger.log(
      level: 'info',
      subsystem: 'battery_monitor',
      message: 'Battery monitoring initialized',
    );
  }

  /// Update battery level from platform.
  Future<void> _updateBatteryLevel() async {
    try {
      final level = await _channel.invokeMethod<int>('getBatteryLevel');
      if (level != null && level >= 0 && level <= 100) {
        final previousLevel = _batteryLevel;
        _batteryLevel = level;

        if (previousLevel != level) {
          _batteryLevelController.add(level);

          await _logger.log(
            level: 'debug',
            subsystem: 'battery_monitor',
            message: 'Battery level updated',
            extra: {'level': level},
          );
        }
      }
    } on PlatformException catch (e) {
      await _logger.log(
        level: 'warning',
        subsystem: 'battery_monitor',
        message: 'Failed to get battery level',
        extra: {'error': e.toString()},
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'warning',
        subsystem: 'battery_monitor',
        message: 'Error updating battery level',
        extra: {'error': e.toString()},
      );
    }
  }

  /// Get current battery level.
  ///
  /// Returns battery level (0-100) or null if unknown.
  int? getBatteryLevel() => _batteryLevel;

  /// Check if battery is low (below threshold).
  bool isLowBattery() {
    final level = _batteryLevel;
    return level != null && level <= lowBatteryThreshold;
  }

  /// Check if battery is critical (below critical threshold).
  bool isCriticalBattery() {
    final level = _batteryLevel;
    return level != null && level <= criticalBatteryThreshold;
  }

  /// Get slowdown multiplier based on battery level.
  ///
  /// Returns:
  /// - 1.0 (normal speed) if battery > 15%
  /// - 0.5 (half speed) if battery <= 15%
  /// - 0.25 (quarter speed) if battery <= 5%
  double getSlowdownMultiplier() {
    if (isCriticalBattery()) {
      return 0.25; // Quarter speed for critical battery
    } else if (isLowBattery()) {
      return 0.5; // Half speed for low battery
    }
    return 1.0; // Normal speed
  }

  /// Dispose battery monitor.
  void dispose() {
    _updateTimer?.cancel();
    _updateTimer = null;
    _batteryLevelController.close();
  }
}
