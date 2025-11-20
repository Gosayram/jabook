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

import 'package:flutter/material.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:permission_handler/permission_handler.dart';

/// Service for managing app permissions.
///
/// This service handles requesting and checking various permissions
/// required for the app to function properly.
class PermissionService {
  /// Private constructor for singleton pattern.
  PermissionService._();

  /// Factory constructor to get the singleton instance.
  factory PermissionService() => _instance;

  /// Singleton instance of the PermissionService.
  static final PermissionService _instance = PermissionService._();

  /// Logger instance for structured logging.
  final StructuredLogger _logger = StructuredLogger();

  /// Checks if storage permission is granted.
  ///
  /// Returns `true` if storage permission is granted, `false` otherwise.
  Future<bool> hasStoragePermission() async {
    try {
      // For Android 13+, we need to use photos instead of storage
      final status = await Permission.photos.status;
      if (status.isGranted) return true;

      // For older Android versions
      final storageStatus = await Permission.storage.status;
      return storageStatus.isGranted;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'permissions',
        message: 'Error checking storage permission',
        cause: e.toString(),
      );
      return false;
    }
  }

  /// Requests storage permission.
  ///
  /// Returns `true` if permission was granted, `false` otherwise.
  Future<bool> requestStoragePermission() async {
    try {
      await _logger.log(
        level: 'info',
        subsystem: 'permissions',
        message: 'Requesting storage permission',
      );

      // For Android 13+, request photos permission
      final photosStatus = await Permission.photos.request();
      if (photosStatus.isGranted) {
        await _logger.log(
          level: 'info',
          subsystem: 'permissions',
          message: 'Photos permission granted',
        );
        return true;
      }

      // For older Android versions, request storage permission
      final storageStatus = await Permission.storage.request();

      await _logger.log(
        level: 'info',
        subsystem: 'permissions',
        message: 'Storage permission result: ${storageStatus.name}',
      );

      return storageStatus.isGranted;
    } on Exception catch (e) {
      // Check if error is about missing Activity
      final errorStr = e.toString();
      if (errorStr.contains('Unable to detect current Android Activity') ||
          errorStr.contains('Activity')) {
        // This is expected during early app initialization - log as debug, not warning
        await _logger.log(
          level: 'debug',
          subsystem: 'permissions',
          message:
              'Cannot request storage permission - Activity not available (expected during early initialization)',
          cause: e.toString(),
        );
      } else {
        await _logger.log(
          level: 'error',
          subsystem: 'permissions',
          message: 'Error requesting storage permission',
          cause: e.toString(),
        );
      }
      return false;
    }
  }

  /// Checks if notification permission is granted.
  Future<bool> hasNotificationPermission() async {
    try {
      final status = await Permission.notification.status;
      return status.isGranted;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'permissions',
        message: 'Error checking notification permission',
        cause: e.toString(),
      );
      return false;
    }
  }

  /// Requests notification permission.
  Future<bool> requestNotificationPermission() async {
    try {
      await _logger.log(
        level: 'info',
        subsystem: 'permissions',
        message: 'Requesting notification permission',
      );

      final status = await Permission.notification.request();

      await _logger.log(
        level: 'info',
        subsystem: 'permissions',
        message: 'Notification permission result: ${status.name}',
      );

      return status.isGranted;
    } on Exception catch (e) {
      // Check if error is about missing Activity
      final errorStr = e.toString();
      if (errorStr.contains('Unable to detect current Android Activity') ||
          errorStr.contains('Activity')) {
        // This is expected during early app initialization - log as debug, not warning
        await _logger.log(
          level: 'debug',
          subsystem: 'permissions',
          message:
              'Cannot request notification permission - Activity not available (expected during early initialization)',
          cause: e.toString(),
        );
      } else {
        await _logger.log(
          level: 'error',
          subsystem: 'permissions',
          message: 'Error requesting notification permission',
          cause: e.toString(),
        );
      }
      return false;
    }
  }

  /// Checks if all required permissions are granted.
  ///
  /// Returns `true` if all permissions are granted, `false` otherwise.
  Future<bool> hasAllPermissions() async {
    final hasStorage = await hasStoragePermission();
    final hasNotification = await hasNotificationPermission();

    return hasStorage && hasNotification;
  }

  /// Requests all required permissions.
  ///
  /// Returns `true` if all permissions were granted, `false` otherwise.
  Future<bool> requestAllPermissions() async {
    final storageGranted = await requestStoragePermission();
    final notificationGranted = await requestNotificationPermission();

    return storageGranted && notificationGranted;
  }

  /// Shows a dialog explaining why a permission is needed.
  ///
  /// Returns `true` if user accepts, `false` otherwise.
  Future<bool> showPermissionDialog({
    required BuildContext context,
    required String title,
    required String message,
    required Future<bool> Function() requestPermission,
  }) async {
    final result = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: Text(message),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Allow'),
          ),
        ],
      ),
    );

    if (result ?? false) {
      return requestPermission();
    }

    return false;
  }

  /// Opens app settings so user can manually grant permissions.
  Future<void> openAppSettings() async {
    try {
      await openAppSettings();
      await _logger.log(
        level: 'info',
        subsystem: 'permissions',
        message: 'Opened app settings',
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'permissions',
        message: 'Error opening app settings',
        cause: e.toString(),
      );
    }
  }

  /// Shows a comprehensive permission request dialog.
  Future<bool> showPermissionRequestDialog(BuildContext context) async {
    final result = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Permissions Required'),
        content: const Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('JaBook needs the following permissions to work properly:'),
            SizedBox(height: 16),
            Row(
              children: [
                Icon(Icons.folder, color: Colors.blue),
                SizedBox(width: 8),
                Expanded(
                  child:
                      Text('Storage: To save audiobook files and cache data'),
                ),
              ],
            ),
            SizedBox(height: 8),
            Row(
              children: [
                Icon(Icons.notifications, color: Colors.orange),
                SizedBox(width: 8),
                Expanded(
                  child: Text(
                      'Notifications: To show playback controls and updates'),
                ),
              ],
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Grant Permissions'),
          ),
        ],
      ),
    );
    return result ?? false;
  }

  /// Requests all essential permissions for the app to function.
  ///
  /// This method requests permissions that are critical for app functionality
  /// and should be called during app initialization.
  ///
  /// Note: Only requests permissions that are actually needed.
  /// Does not request camera or file picker permissions as they are not essential.
  Future<Map<String, bool>> requestEssentialPermissions() async {
    final results = <String, bool>{};

    try {
      await _logger.log(
        level: 'info',
        subsystem: 'permissions',
        message: 'Requesting essential permissions',
      );

      // Request storage permission (only if needed - not for login)
      // Skip if Activity is not available
      try {
        results['storage'] = await requestStoragePermission();
      } on Exception catch (e) {
        final errorStr = e.toString();
        if (errorStr.contains('Unable to detect current Android Activity')) {
          await _logger.log(
            level: 'debug',
            subsystem: 'permissions',
            message:
                'Skipping storage permission - Activity not available (expected during early initialization)',
          );
          results['storage'] = false;
        } else {
          rethrow;
        }
      }

      // Request notification permission
      try {
        results['notification'] = await requestNotificationPermission();
      } on Exception catch (e) {
        final errorStr = e.toString();
        if (errorStr.contains('Unable to detect current Android Activity')) {
          await _logger.log(
            level: 'debug',
            subsystem: 'permissions',
            message:
                'Skipping notification permission - Activity not available (expected during early initialization)',
          );
          results['notification'] = false;
        } else {
          rethrow;
        }
      }

      // Request audio permissions for media playback (optional, non-blocking)
      results['audio'] = await _requestAudioPermissions();

      // Request network permissions
      results['network'] = await _requestNetworkPermissions();

      await _logger.log(
        level: 'info',
        subsystem: 'permissions',
        message: 'Essential permissions requested',
        extra: results,
      );

      return results;
    } on Exception catch (e) {
      await _logger.log(
        level: 'warning',
        subsystem: 'permissions',
        message: 'Error requesting essential permissions (continuing anyway)',
        cause: e.toString(),
      );
      return results;
    }
  }

  /// Requests audio-related permissions for media playback.
  Future<bool> _requestAudioPermissions() async {
    try {
      // Request wake lock permission for audio playback
      // Note: This is optional and not critical for app functionality
      final wakeLockStatus =
          await Permission.ignoreBatteryOptimizations.request();

      return wakeLockStatus.isGranted;
    } on Exception catch (e) {
      // Check if error is about missing Activity
      final errorStr = e.toString();
      if (errorStr.contains('Unable to detect current Android Activity') ||
          errorStr.contains('Activity')) {
        // This is expected during early app initialization - log as debug, not warning
        await _logger.log(
          level: 'debug',
          subsystem: 'permissions',
          message:
              'Cannot request audio permissions - Activity not available (expected during early initialization, non-critical)',
          cause: e.toString(),
        );
      } else {
        await _logger.log(
          level: 'debug',
          subsystem: 'permissions',
          message: 'Error requesting audio permissions (non-critical)',
          cause: e.toString(),
        );
      }
      // Return true to not block app functionality - this permission is optional
      return true;
    }
  }

  /// Requests network-related permissions.
  Future<bool> _requestNetworkPermissions() async {
    try {
      // Internet permission is usually granted by default
      return true;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'permissions',
        message: 'Error requesting network permissions',
        cause: e.toString(),
      );
      return false;
    }
  }

  /// Checks if all essential permissions are granted.
  Future<bool> hasAllEssentialPermissions() async {
    final storage = await hasStoragePermission();
    final notification = await hasNotificationPermission();
    final audio = await _hasAudioPermissions();
    final network = _hasNetworkPermissions();

    return storage && notification && audio && network;
  }

  /// Checks if audio permissions are granted.
  Future<bool> _hasAudioPermissions() async {
    try {
      final wakeLockStatus = await Permission.ignoreBatteryOptimizations.status;

      return wakeLockStatus.isGranted;
    } on Exception {
      return false;
    }
  }

  /// Checks if network permissions are granted.
  bool _hasNetworkPermissions() =>
      true; // Internet permission is usually granted by default
}
