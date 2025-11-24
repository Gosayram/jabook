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

import 'package:device_info_plus/device_info_plus.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/core/utils/device_info_utils.dart';

/// Report about background compatibility status.
class CompatibilityReport {
  /// Creates a new CompatibilityReport.
  const CompatibilityReport({
    required this.isCompatible,
    required this.issues,
    required this.recommendations,
    this.standbyBucket,
    this.manufacturer,
    this.customRom,
    this.androidVersion,
  });

  /// Whether the device is generally compatible with background tasks.
  final bool isCompatible;

  /// List of detected issues.
  final List<String> issues;

  /// List of recommendations for the user.
  final List<String> recommendations;

  /// App Standby Bucket value (10, 20, 30, 40, or 50).
  final int? standbyBucket;

  /// Device manufacturer.
  final String? manufacturer;

  /// Custom ROM name.
  final String? customRom;

  /// Android version (SDK int).
  final int? androidVersion;
}

/// Event about background compatibility problems.
class CompatibilityEvent {
  /// Creates a new CompatibilityEvent.
  const CompatibilityEvent({
    required this.type,
    required this.message,
    this.severity = 'warning',
    this.timestamp,
  });

  /// Event type (e.g., 'workmanager_delayed', 'fgs_killed').
  final String type;

  /// Event message.
  final String message;

  /// Event severity ('info', 'warning', 'error').
  final String severity;

  /// Event timestamp.
  final DateTime? timestamp;
}

/// Service for checking background task compatibility on Android devices.
///
/// This service performs self-diagnostics to detect potential issues with
/// background tasks (WorkManager, Foreground Services) and provides
/// recommendations to users.
class BackgroundCompatibilityChecker {
  BackgroundCompatibilityChecker._();

  /// Factory constructor to get the singleton instance.
  factory BackgroundCompatibilityChecker() => _instance;

  /// Singleton instance
  static final BackgroundCompatibilityChecker _instance =
      BackgroundCompatibilityChecker._();

  /// Logger instance
  final StructuredLogger _logger = StructuredLogger();

  /// Device info utils instance
  final DeviceInfoUtils _deviceInfo = DeviceInfoUtils.instance;

  /// Stream controller for compatibility events
  final _eventController = StreamController<CompatibilityEvent>.broadcast();

  /// Stream of compatibility events.
  Stream<CompatibilityEvent> get watchProblems => _eventController.stream;

  /// Performs a comprehensive compatibility check.
  ///
  /// Returns a report with detected issues and recommendations.
  /// [localizations] is optional - if not provided, English strings will be used.
  Future<CompatibilityReport> checkNow([dynamic localizations]) async {
    try {
      if (!Platform.isAndroid) {
        return const CompatibilityReport(
          isCompatible: true,
          issues: [],
          recommendations: [],
        );
      }

      final issues = <String>[];
      final recommendations = <String>[];

      // Get device information
      final manufacturer = await _deviceInfo.getManufacturer();
      final customRom = await _deviceInfo.getCustomRom();
      final deviceInfoPlugin = DeviceInfoPlugin();
      final androidInfo = await deviceInfoPlugin.androidInfo;
      final androidVersion = androidInfo.version.sdkInt;
      final standbyBucket = await _deviceInfo.getAppStandbyBucket();
      final isChinese = await _deviceInfo.isChineseManufacturer();
      final needsGuidance = await _deviceInfo.needsAggressiveGuidance();

      // Check App Standby Bucket
      if (standbyBucket != null) {
        if (standbyBucket >= 40) {
          // RARE or NEVER bucket - app is being restricted
          final bucketName = _getBucketName(standbyBucket, localizations);
          issues.add(
            localizations?.appStandbyBucketRestricted(bucketName) ??
                'Application is in restricted background activity mode (Standby Bucket: $bucketName)',
          );
          recommendations.add(
            localizations?.useAppMoreFrequently ??
                'Use the application more frequently so the system moves it to active mode',
          );
        }
      }

      // Check for Chinese manufacturers
      if (isChinese && needsGuidance) {
        issues.add(
          localizations?.aggressiveBatteryOptimization(
                manufacturer ?? 'unknown',
                customRom ?? 'unknown',
              ) ??
              'Device from manufacturer with aggressive battery optimization detected ($manufacturer, $customRom)',
        );
        recommendations
          ..add(
            localizations?.configureAutostartAndBattery ??
                'It is recommended to configure autostart and disable battery optimization for the application',
          )
          ..add(
            localizations?.openManufacturerSettings ??
                'Open manufacturer settings through the application menu',
          );
      }

      // Check Android version for FGS restrictions
      if (androidVersion >= 34) {
        // Android 14+
        recommendations.add(
          localizations?.android14ForegroundServices ??
              'On Android 14+, make sure Foreground Services start correctly',
        );
      }

      // Determine overall compatibility
      final isCompatible = issues.isEmpty;

      await _logger.log(
        level: isCompatible ? 'info' : 'warning',
        subsystem: 'compatibility',
        message: 'Background compatibility check completed',
        extra: {
          'is_compatible': isCompatible,
          'issues_count': issues.length,
          'manufacturer': manufacturer,
          'custom_rom': customRom,
          'android_version': androidVersion,
          'standby_bucket': standbyBucket,
        },
      );

      return CompatibilityReport(
        isCompatible: isCompatible,
        issues: issues,
        recommendations: recommendations,
        standbyBucket: standbyBucket,
        manufacturer: manufacturer,
        customRom: customRom,
        androidVersion: androidVersion,
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'compatibility',
        message: 'Error performing compatibility check',
        cause: e.toString(),
      );
      return const CompatibilityReport(
        isCompatible: true,
        issues: [],
        recommendations: [],
      );
    }
  }

  /// Shows guidance UI if problems are detected.
  ///
  /// This method checks for compatibility issues and shows appropriate
  /// guidance to the user if needed.
  /// [localizations] is optional - if not provided, English strings will be used.
  Future<void> maybeShowGuidanceUI([dynamic localizations]) async {
    try {
      final report = await checkNow(localizations);

      if (!report.isCompatible && report.issues.isNotEmpty) {
        // Emit event for UI to handle
        _eventController.add(
          CompatibilityEvent(
            type: 'compatibility_issues_detected',
            message: localizations?.compatibilityIssuesDetected ??
                'Background work issues detected',
            timestamp: DateTime.now(),
          ),
        );

        await _logger.log(
          level: 'info',
          subsystem: 'compatibility',
          message: 'Compatibility issues detected, guidance should be shown',
          extra: {
            'issues': report.issues,
            'recommendations': report.recommendations,
          },
        );
      }
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'compatibility',
        message: 'Error checking if guidance should be shown',
        cause: e.toString(),
      );
    }
  }

  /// Logs a WorkManager delay event.
  ///
  /// This should be called when a WorkManager task is delayed beyond
  /// expected time.
  /// [localizations] is optional - if not provided, English strings will be used.
  Future<void> logWorkManagerDelayed({
    required String taskName,
    required Duration expectedDelay,
    required Duration actualDelay,
    dynamic localizations,
  }) async {
    final delayHours = actualDelay.inHours;
    if (delayHours > 1) {
      // Significant delay detected
      _eventController.add(
        CompatibilityEvent(
          type: 'workmanager_delayed',
          message: localizations?.workManagerTaskDelayed(
                taskName,
                delayHours,
                expectedDelay.inMinutes,
              ) ??
              'WorkManager task "$taskName" delayed by $delayHours hours (expected: ${expectedDelay.inMinutes} minutes)',
          timestamp: DateTime.now(),
        ),
      );

      // Get device info for analytics
      final manufacturer = await _deviceInfo.getManufacturer();
      final customRom = await _deviceInfo.getCustomRom();
      final romVersion = await _deviceInfo.getRomVersion();
      final deviceInfoPlugin = DeviceInfoPlugin();
      final androidInfo = await deviceInfoPlugin.androidInfo;

      await _logger.log(
        level: 'warning',
        subsystem: 'compatibility',
        message: 'WorkManager task significantly delayed',
        extra: {
          'task_name': taskName,
          'expected_delay_minutes': expectedDelay.inMinutes,
          'actual_delay_hours': delayHours,
          'manufacturer': manufacturer,
          'custom_rom': customRom,
          'rom_version': romVersion,
          'android_sdk': androidInfo.version.sdkInt,
          'android_version': androidInfo.version.release,
          'event_type': 'workmanager_delayed',
        },
      );
    }
  }

  /// Logs a Foreground Service killed event.
  ///
  /// This should be called when a Foreground Service is unexpectedly killed.
  /// [localizations] is optional - if not provided, English strings will be used.
  Future<void> logForegroundServiceKilled({
    required String serviceName,
    String? reason,
    dynamic localizations,
  }) async {
    _eventController.add(
      CompatibilityEvent(
        type: 'fgs_killed',
        message: localizations?.foregroundServiceKilled(serviceName) ??
            'Foreground Service "$serviceName" was unexpectedly terminated by the system',
        severity: 'error',
        timestamp: DateTime.now(),
      ),
    );

    // Get comprehensive device info for analytics
    final manufacturer = await _deviceInfo.getManufacturer();
    final customRom = await _deviceInfo.getCustomRom();
    final romVersion = await _deviceInfo.getRomVersion();
    final deviceInfoPlugin = DeviceInfoPlugin();
    final androidInfo = await deviceInfoPlugin.androidInfo;

    await _logger.log(
      level: 'error',
      subsystem: 'compatibility',
      message: 'Foreground Service killed unexpectedly',
      extra: {
        'service_name': serviceName,
        'reason': reason,
        'manufacturer': manufacturer,
        'custom_rom': customRom,
        'rom_version': romVersion,
        'android_sdk': androidInfo.version.sdkInt,
        'android_version': androidInfo.version.release,
        'event_type': 'fgs_killed',
      },
    );
  }

  /// Logs when user opens OEM guidance screen.
  ///
  /// This is useful for analytics to understand which users need help
  /// and which manufacturers are most problematic.
  Future<void> logUserOpenedOemGuidance({
    required String manufacturer,
    String? customRom,
    String? romVersion,
  }) async {
    await _logger.log(
      level: 'info',
      subsystem: 'compatibility',
      message: 'User opened OEM guidance screen',
      extra: {
        'manufacturer': manufacturer,
        'custom_rom': customRom,
        'rom_version': romVersion,
        'event_type': 'user_opened_oem_guidance',
      },
    );
  }

  /// Gets human-readable name for App Standby Bucket.
  String _getBucketName(int bucket, [dynamic localizations]) {
    switch (bucket) {
      case 10:
        return localizations?.standbyBucketActive ?? 'Actively Used';
      case 20:
        return localizations?.standbyBucketWorkingSet ?? 'Frequently Used';
      case 30:
        return localizations?.standbyBucketFrequent ?? 'Regularly Used';
      case 40:
        return localizations?.standbyBucketRare ?? 'Rarely Used';
      case 50:
        return localizations?.standbyBucketNever ?? 'Never Used (Restricted)';
      default:
        return localizations?.standbyBucketUnknown(bucket) ??
            'Unknown ($bucket)';
    }
  }

  /// Disposes the checker and closes streams.
  void dispose() {
    _eventController.close();
  }
}
