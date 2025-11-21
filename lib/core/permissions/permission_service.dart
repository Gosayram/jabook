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
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:permission_handler/permission_handler.dart' as ph
    show openAppSettings;

/// Service for managing app permissions.
///
/// This service handles requesting and checking various permissions
/// required for the app to function properly.
class PermissionService {
  /// Factory constructor to get the singleton instance.
  factory PermissionService() => _instance;

  /// Private constructor for singleton pattern.
  PermissionService._();

  /// Method channel for native permission operations
  static const MethodChannel _permissionChannel =
      MethodChannel('permission_channel');

  /// Singleton instance of the PermissionService.
  static final PermissionService _instance = PermissionService._();

  /// Logger instance for structured logging.
  final StructuredLogger _logger = StructuredLogger();

  /// Completer for synchronizing permission requests.
  /// Prevents multiple simultaneous permission requests.
  Completer<Map<String, bool>>? _permissionRequestCompleter;

  /// Checks if storage permission is granted.
  ///
  /// This method is primarily used for READING files from shared storage.
  /// For WRITING files, use [canWriteToStorage()] instead, which correctly
  /// handles app-specific directories that don't require permissions on Android 11+.
  ///
  /// Returns `true` if storage permission is granted, `false` otherwise.
  Future<bool> hasStoragePermission() async {
    try {
      if (!Platform.isAndroid) {
        // For non-Android platforms, return true (permissions handled differently)
        return true;
      }

      // Get Android version
      final androidInfo = await DeviceInfoPlugin().androidInfo;
      final sdkInt = androidInfo.version.sdkInt;

      // Android 13+ (API 33+): Use audio permission for audio files
      if (sdkInt >= 33) {
        final audioStatus = await Permission.audio.status;
        if (audioStatus.isGranted) return true;
        // Fallback to photos permission for broader access
        final photosStatus = await Permission.photos.status;
        return photosStatus.isGranted;
      }

      // Android 12 (API 32): Use storage permission (still required)
      if (sdkInt == 32) {
        final storageStatus = await Permission.storage.status;
        return storageStatus.isGranted;
      }

      // Android 11 and below (API 30-31): Use storage permission
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
  /// This method is primarily used for READING files from shared storage.
  /// For WRITING files, use [canWriteToStorage()] instead, which correctly
  /// handles app-specific directories that don't require permissions on Android 11+.
  ///
  /// Returns `true` if permission was granted, `false` otherwise.
  Future<bool> requestStoragePermission() async {
    try {
      if (!Platform.isAndroid) {
        // For non-Android platforms, return true (permissions handled differently)
        return true;
      }

      await _logger.log(
        level: 'info',
        subsystem: 'permissions',
        message: 'Requesting storage permission',
      );

      // Get Android version
      final androidInfo = await DeviceInfoPlugin().androidInfo;
      final sdkInt = androidInfo.version.sdkInt;

      // Android 13+ (API 33+): For default path /storage/emulated/0/JabookAudio,
      // we still need MANAGE_EXTERNAL_STORAGE, not just audio permission.
      // Audio permission is only for app-specific directories.
      // So we check MANAGE_EXTERNAL_STORAGE first, then fallback to audio permission.
      if (sdkInt >= 33) {
        await _logger.log(
          level: 'info',
          subsystem: 'permissions',
          message:
              'Android 13+ detected, checking MANAGE_EXTERNAL_STORAGE for default path',
          extra: {'sdk_int': sdkInt},
        );
        // Check if MANAGE_EXTERNAL_STORAGE is already granted
        final hasManageStorage = await _hasManageExternalStoragePermission();
        await _logger.log(
          level: 'info',
          subsystem: 'permissions',
          message:
              'MANAGE_EXTERNAL_STORAGE permission check result (Android 13+)',
          extra: {'has_permission': hasManageStorage},
        );
        if (hasManageStorage) {
          await _logger.log(
            level: 'info',
            subsystem: 'permissions',
            message:
                'MANAGE_EXTERNAL_STORAGE permission already granted (Android 13+)',
          );
          return true;
        }

        // Open system settings for MANAGE_EXTERNAL_STORAGE
        await _logger.log(
          level: 'info',
          subsystem: 'permissions',
          message:
              'Opening system settings for MANAGE_EXTERNAL_STORAGE (Android 13+)',
        );
        try {
          await _permissionChannel
              .invokeMethod('openManageExternalStorageSettings');
          await _logger.log(
            level: 'info',
            subsystem: 'permissions',
            message:
                'System settings opened for MANAGE_EXTERNAL_STORAGE. User needs to grant permission manually.',
          );
          // Wait a bit for user to potentially grant permission
          await Future.delayed(const Duration(seconds: 1));
          // Check again if user granted permission
          final hasPermission = await _hasManageExternalStoragePermission();
          if (hasPermission) {
            await _logger.log(
              level: 'info',
              subsystem: 'permissions',
              message:
                  'MANAGE_EXTERNAL_STORAGE permission granted after opening settings (Android 13+)',
            );
            return true;
          }
          // Return false - user needs to grant permission in settings
          return false;
        } on PlatformException catch (e) {
          await _logger.log(
            level: 'error',
            subsystem: 'permissions',
            message:
                'Failed to open system settings for MANAGE_EXTERNAL_STORAGE (Android 13+)',
            cause: e.toString(),
          );
          // Fallback to audio permission (for app-specific directories only)
          final audioStatus = await Permission.audio.request();
          if (audioStatus.isGranted) {
            await _logger.log(
              level: 'warning',
              subsystem: 'permissions',
              message:
                  'Audio permission granted (Android 13+), but MANAGE_EXTERNAL_STORAGE is still required for default path',
            );
          }
          return false; // Return false because MANAGE_EXTERNAL_STORAGE is required
        }
      }

      // Android 12 (API 32): Request storage permission (still required)
      if (sdkInt == 32) {
        final storageStatus = await Permission.storage.request();
        await _logger.log(
          level: 'info',
          subsystem: 'permissions',
          message:
              'Storage permission result (Android 12): ${storageStatus.name}',
        );
        return storageStatus.isGranted;
      }

      // Android 11+ (API 30+): Request MANAGE_EXTERNAL_STORAGE first, then storage as fallback
      if (sdkInt >= 30) {
        await _logger.log(
          level: 'info',
          subsystem: 'permissions',
          message:
              'Android 11+ detected, checking MANAGE_EXTERNAL_STORAGE permission',
          extra: {'sdk_int': sdkInt},
        );
        // Check if MANAGE_EXTERNAL_STORAGE is already granted
        final hasManageStorage = await _hasManageExternalStoragePermission();
        await _logger.log(
          level: 'info',
          subsystem: 'permissions',
          message: 'MANAGE_EXTERNAL_STORAGE permission check result',
          extra: {'has_permission': hasManageStorage},
        );
        if (hasManageStorage) {
          await _logger.log(
            level: 'info',
            subsystem: 'permissions',
            message:
                'MANAGE_EXTERNAL_STORAGE permission already granted (Android 11+)',
          );
          return true;
        }

        // Open system settings for MANAGE_EXTERNAL_STORAGE
        // This is required because permission_handler doesn't properly handle this permission
        await _logger.log(
          level: 'info',
          subsystem: 'permissions',
          message:
              'Opening system settings for MANAGE_EXTERNAL_STORAGE (Android 11+)',
        );
        try {
          await _permissionChannel
              .invokeMethod('openManageExternalStorageSettings');
          await _logger.log(
            level: 'info',
            subsystem: 'permissions',
            message:
                'System settings opened for MANAGE_EXTERNAL_STORAGE. User needs to grant permission manually.',
          );
          // Wait a bit for user to potentially grant permission
          await Future.delayed(const Duration(seconds: 1));
          // Check again if user granted permission
          final hasPermission = await _hasManageExternalStoragePermission();
          if (hasPermission) {
            await _logger.log(
              level: 'info',
              subsystem: 'permissions',
              message:
                  'MANAGE_EXTERNAL_STORAGE permission granted after opening settings',
            );
            return true;
          }
          // Return false - user needs to grant permission in settings
          // The app will check again when trying to write
          return false;
        } on PlatformException catch (e) {
          await _logger.log(
            level: 'error',
            subsystem: 'permissions',
            message:
                'Failed to open system settings for MANAGE_EXTERNAL_STORAGE',
            cause: e.toString(),
          );
          // Fallback to storage permission
          final storageStatus = await Permission.storage.request();
          await _logger.log(
            level: 'info',
            subsystem: 'permissions',
            message:
                'Storage permission result (fallback): ${storageStatus.name}',
          );
          return storageStatus.isGranted;
        }
      }

      // Android 10 and below: Request storage permission
      final storageStatus = await Permission.storage.request();
      await _logger.log(
        level: 'info',
        subsystem: 'permissions',
        message: 'Storage permission result: ${storageStatus.name}',
      );

      return storageStatus.isGranted;
    } on Exception catch (e) {
      final errorStr = e.toString();

      // Handle "permission request already running" error
      if (errorStr.contains('already running') ||
          errorStr.contains('wait for it to finish')) {
        await _logger.log(
          level: 'warning',
          subsystem: 'permissions',
          message: 'Permission request already in progress, waiting...',
          cause: e.toString(),
        );
        // Wait a bit and retry
        await Future.delayed(const Duration(milliseconds: 500));
        return requestStoragePermission();
      }

      // Check if error is about missing Activity
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

      // Check current status first
      final currentStatus = await Permission.notification.status;

      // If permanently denied, open settings
      if (currentStatus.isPermanentlyDenied) {
        await _logger.log(
          level: 'info',
          subsystem: 'permissions',
          message:
              'Notification permission permanently denied, opening settings',
        );
        await openAppSettings();
        // Wait a bit for user to potentially grant permission
        await Future.delayed(const Duration(seconds: 1));
        // Check again if user granted permission
        final newStatus = await Permission.notification.status;
        return newStatus.isGranted;
      }

      final status = await Permission.notification.request();

      await _logger.log(
        level: 'info',
        subsystem: 'permissions',
        message: 'Notification permission result: ${status.name}',
      );

      // If denied and can be requested again, it's not permanently denied yet
      // If permanently denied after request, open settings
      if (status.isPermanentlyDenied) {
        await _logger.log(
          level: 'info',
          subsystem: 'permissions',
          message:
              'Notification permission permanently denied after request, opening settings',
        );
        await openAppSettings();
        // Wait a bit for user to potentially grant permission
        await Future.delayed(const Duration(seconds: 1));
        // Check again if user granted permission
        final newStatus = await Permission.notification.status;
        return newStatus.isGranted;
      }

      return status.isGranted;
    } on Exception catch (e) {
      final errorStr = e.toString();

      // Handle "permission request already running" error
      if (errorStr.contains('already running') ||
          errorStr.contains('wait for it to finish')) {
        await _logger.log(
          level: 'warning',
          subsystem: 'permissions',
          message: 'Permission request already in progress, waiting...',
          cause: e.toString(),
        );
        // Wait a bit and retry
        await Future.delayed(const Duration(milliseconds: 500));
        return requestNotificationPermission();
      }

      // Check if error is about missing Activity
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

  /// Gets detailed permission status for all permissions.
  ///
  /// Returns a map with permission names as keys and their status information.
  Future<Map<String, Map<String, dynamic>>>
      getDetailedPermissionStatus() async {
    final status = <String, Map<String, dynamic>>{};

    try {
      // Storage permission status
      if (Platform.isAndroid) {
        final androidInfo = await DeviceInfoPlugin().androidInfo;
        final sdkInt = androidInfo.version.sdkInt;

        if (sdkInt >= 33) {
          final audioStatus = await Permission.audio.status;
          final photosStatus = await Permission.photos.status;
          status['storage'] = {
            'granted': audioStatus.isGranted || photosStatus.isGranted,
            'audio': {
              'granted': audioStatus.isGranted,
              'denied': audioStatus.isDenied,
              'permanentlyDenied': audioStatus.isPermanentlyDenied,
            },
            'photos': {
              'granted': photosStatus.isGranted,
              'denied': photosStatus.isDenied,
              'permanentlyDenied': photosStatus.isPermanentlyDenied,
            },
          };
        } else {
          final storageStatus = await Permission.storage.status;
          status['storage'] = {
            'granted': storageStatus.isGranted,
            'denied': storageStatus.isDenied,
            'permanentlyDenied': storageStatus.isPermanentlyDenied,
          };
        }
      } else {
        status['storage'] = {'granted': true};
      }

      // Notification permission status
      final notificationStatus = await Permission.notification.status;
      status['notification'] = {
        'granted': notificationStatus.isGranted,
        'denied': notificationStatus.isDenied,
        'permanentlyDenied': notificationStatus.isPermanentlyDenied,
      };
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'permissions',
        message: 'Error getting detailed permission status',
        cause: e.toString(),
      );
    }

    return status;
  }

  /// Checks if a permission is permanently denied.
  ///
  /// Returns `true` if the permission is permanently denied, `false` otherwise.
  Future<bool> isPermissionPermanentlyDenied(Permission permission) async {
    try {
      final status = await permission.status;
      return status.isPermanentlyDenied;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'permissions',
        message: 'Error checking if permission is permanently denied',
        cause: e.toString(),
      );
      return false;
    }
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
  ///
  /// Uses permission_handler's openAppSettings to open system app settings.
  Future<void> openAppSettings() async {
    try {
      // Use permission_handler's openAppSettings function
      await ph.openAppSettings();
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
      // Fallback: try native method channel
      try {
        await _permissionChannel
            .invokeMethod('openManageExternalStorageSettings');
      } on Exception {
        // Ignore fallback errors
      }
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
  ///
  /// This method is synchronized to prevent multiple simultaneous permission requests.
  Future<Map<String, bool>> requestEssentialPermissions() async {
    // If a request is already in progress, wait for it
    if (_permissionRequestCompleter != null) {
      await _logger.log(
        level: 'debug',
        subsystem: 'permissions',
        message:
            'Permission request already in progress, waiting for completion',
      );
      return _permissionRequestCompleter!.future;
    }

    _permissionRequestCompleter = Completer<Map<String, bool>>();
    try {
      final results = await _doRequestEssentialPermissions();
      _permissionRequestCompleter!.complete(results);
      return results;
    } catch (e) {
      _permissionRequestCompleter!.completeError(e);
      rethrow;
    } finally {
      _permissionRequestCompleter = null;
    }
  }

  /// Internal method that performs the actual permission requests.
  Future<Map<String, bool>> _doRequestEssentialPermissions() async {
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

  /// Checks if we can write to storage.
  ///
  /// For app-specific directory on Android 11+ (API 30+), no permission is needed.
  /// For default path /storage/emulated/0/JabookAudio on Android 11+, requires
  /// MANAGE_EXTERNAL_STORAGE permission.
  /// For older Android versions or user-selected directories, checks storage permission.
  ///
  /// Returns `true` if we can write to storage, `false` otherwise.
  Future<bool> canWriteToStorage() async {
    try {
      if (!Platform.isAndroid) {
        // For non-Android platforms, return true (permissions handled differently)
        return true;
      }

      // Get Android version
      final androidInfo = await DeviceInfoPlugin().androidInfo;
      final sdkInt = androidInfo.version.sdkInt;

      // Android 11+ (API 30+): Check MANAGE_EXTERNAL_STORAGE for default path
      if (sdkInt >= 30) {
        // Check MANAGE_EXTERNAL_STORAGE permission using native method
        // (required for /storage/emulated/0/JabookAudio)
        final hasManageStorage = await _hasManageExternalStoragePermission();
        if (hasManageStorage) {
          await _logger.log(
            level: 'debug',
            subsystem: 'permissions',
            message: 'MANAGE_EXTERNAL_STORAGE permission granted',
          );
          return true;
        }
        // Also check storage permission as fallback
        final storageStatus = await Permission.storage.status;
        if (storageStatus.isGranted) {
          await _logger.log(
            level: 'debug',
            subsystem: 'permissions',
            message: 'Storage permission granted (fallback)',
          );
          return true;
        }
        await _logger.log(
          level: 'warning',
          subsystem: 'permissions',
          message: 'No storage write permission granted (Android 11+)',
        );
        return false;
      }

      // Android 10 and below: Check storage permission
      final storageStatus = await Permission.storage.status;
      return storageStatus.isGranted;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'permissions',
        message: 'Error checking write permission',
        cause: e.toString(),
      );
      return false;
    }
  }

  /// Checks if MANAGE_EXTERNAL_STORAGE permission is granted using native method.
  ///
  /// This is more reliable than permission_handler for Android 11+.
  Future<bool> _hasManageExternalStoragePermission() async {
    if (!Platform.isAndroid) return true;

    try {
      final androidInfo = await DeviceInfoPlugin().androidInfo;
      if (androidInfo.version.sdkInt < 30) {
        // Android 10 and below don't need this permission
        return true;
      }

      // Use native method to check permission
      final result = await _permissionChannel.invokeMethod<bool>(
        'hasManageExternalStoragePermission',
      );
      return result ?? false;
    } on PlatformException catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'permissions',
        message: 'Error checking MANAGE_EXTERNAL_STORAGE permission',
        cause: e.toString(),
      );
      // Fallback to permission_handler
      try {
        final status = await Permission.manageExternalStorage.status;
        return status.isGranted;
      } on Exception {
        return false;
      }
    }
  }

  /// Checks if a path is an app-specific directory.
  ///
  /// App-specific directories work WITHOUT permissions on Android 11+ (API 30+).
  /// The default path /storage/emulated/0/JabookAudio is NOT app-specific and
  /// requires MANAGE_EXTERNAL_STORAGE permission on Android 11+.
  /// Returns `true` if the path is an app-specific directory, `false` otherwise.
  static bool isAppSpecificDirectory(String path) {
    if (!Platform.isAndroid) return false;
    // App-specific directory patterns:
    // - /storage/emulated/0/Android/data/package/files
    // - /storage/emulated/0/Android/media/package
    // Default path /storage/emulated/0/JabookAudio is NOT app-specific
    return path.contains('/Android/data/') || path.contains('/Android/media/');
  }
}
