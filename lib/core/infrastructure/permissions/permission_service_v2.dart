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

import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:jabook/core/utils/bluetooth_utils.dart' as bluetooth_utils;
import 'package:jabook/core/utils/file_picker_utils.dart' as file_picker_utils;
import 'package:jabook/core/utils/notification_utils.dart'
    as notification_utils;

/// Service for managing app permissions using system APIs.
///
/// This service uses system APIs (Photo Picker, SAF, system camera, etc.)
/// to avoid requiring explicit permissions in AndroidManifest.xml.
class PermissionServiceV2 {
  /// Private constructor for singleton pattern.
  PermissionServiceV2._();

  /// Factory constructor to get the singleton instance.
  factory PermissionServiceV2() => _instance;

  /// Singleton instance of the PermissionServiceV2.
  static final PermissionServiceV2 _instance = PermissionServiceV2._();

  /// Logger instance for structured logging.
  final StructuredLogger _logger = StructuredLogger();

  /// Checks if the app can access media files using system APIs.
  ///
  /// This doesn't require permissions as it uses Photo Picker/SAF.
  Future<bool> canAccessMediaFiles() async {
    try {
      // Test if we can access media files through system APIs
      final files =
          await file_picker_utils.pickImageFiles(allowMultiple: false);
      return files.isNotEmpty;
    } on Exception catch (e) {
      // Handle "already_active" error gracefully - file picker is already open
      if (e.toString().contains('already_active')) {
        await _logger.log(
          level: 'warning',
          subsystem: 'permissions',
          message: 'File picker is already active',
        );
        // Return true as capability is available, just currently in use
        return true;
      }
      await _logger.log(
        level: 'error',
        subsystem: 'permissions',
        message: 'Error checking media file access',
        cause: e.toString(),
      );
      return false;
    }
  }

  /// Checks if the app can access any files using SAF.
  ///
  /// This doesn't require permissions as it uses Storage Access Framework.
  Future<bool> canAccessFiles() async {
    try {
      // Test if we can access files through SAF
      final files = await file_picker_utils.pickAnyFiles(allowMultiple: false);
      return files.isNotEmpty;
    } on Exception catch (e) {
      // Handle "already_active" error gracefully - file picker is already open
      if (e.toString().contains('already_active')) {
        await _logger.log(
          level: 'warning',
          subsystem: 'permissions',
          message: 'File picker is already active',
        );
        // Return true as capability is available, just currently in use
        return true;
      }
      await _logger.log(
        level: 'error',
        subsystem: 'permissions',
        message: 'Error checking file access',
        cause: e.toString(),
      );
      return false;
    }
  }

  /// Checks if the app can take photos using the system camera.
  ///
  /// This doesn't require permissions as it uses the system camera app.
  Future<bool> canTakePhotos() async {
    try {
      // Test if we can take photos through system camera
      final photoPath = await file_picker_utils.takePhoto();
      return photoPath != null;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'permissions',
        message: 'Error checking camera access',
        cause: e.toString(),
      );
      return false;
    }
  }

  /// Checks if Bluetooth is available on the device.
  ///
  /// This doesn't require permissions as it only checks availability.
  Future<bool> isBluetoothAvailable() async {
    try {
      return await bluetooth_utils.isBluetoothAvailable();
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'permissions',
        message: 'Error checking Bluetooth availability',
        cause: e.toString(),
      );
      return false;
    }
  }

  /// Checks if the app can show notifications using system APIs.
  ///
  /// This doesn't require permissions as it uses system notification APIs.
  Future<bool> canShowNotifications() async {
    try {
      // Test if we can show notifications through system APIs
      final success = await notification_utils.showSimpleNotification(
        title: 'Test',
        body: 'Testing notification capability',
      );

      if (!success) {
        // Notification channel not available - log as warning, not error
        await _logger.log(
          level: 'warning',
          subsystem: 'permissions',
          message:
              'Notification capability not available (channel not implemented)',
        );
      }

      return success;
    } on Exception catch (e) {
      // Graceful degradation - log as warning, not error
      await _logger.log(
        level: 'warning',
        subsystem: 'permissions',
        message: 'Error checking notification capability',
        cause: e.toString(),
      );
      return false;
    }
  }

  /// Requests essential permissions using system APIs.
  ///
  /// This method tests various system APIs to ensure they work
  /// without requiring explicit permissions.
  Future<Map<String, bool>> requestEssentialPermissions() async {
    final results = <String, bool>{};

    try {
      await _logger.log(
        level: 'info',
        subsystem: 'permissions',
        message: 'Requesting essential permissions using system APIs',
      );

      // Test media file access
      results['media_files'] = await canAccessMediaFiles();

      // Test general file access
      results['files'] = await canAccessFiles();

      // Test camera access
      results['camera'] = await canTakePhotos();

      // Test Bluetooth availability
      results['bluetooth'] = await isBluetoothAvailable();

      // Test notification capability
      results['notifications'] = await canShowNotifications();

      final grantedCount = results.values.where((granted) => granted).length;
      final totalCount = results.length;

      await _logger.log(
        level: 'info',
        subsystem: 'permissions',
        message:
            'Permission check results: $grantedCount/$totalCount capabilities available',
      );

      return results;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'permissions',
        message: 'Error requesting essential permissions',
        cause: e.toString(),
      );
      return results;
    }
  }

  /// Shows a permission explanation dialog for system APIs.
  ///
  /// This explains to users how the app uses system APIs instead of permissions.
  Future<void> showPermissionExplanationDialog(BuildContext context) async =>
      showDialog<void>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('Системные возможности'),
          content: const Text(
            'Это приложение использует системные API для работы с файлами, '
            'камерой и уведомлениями без запроса дополнительных разрешений.\n\n'
            '• Файлы: через системный проводник\n'
            '• Фото: через системную галерею\n'
            '• Камера: через системное приложение камеры\n'
            '• Уведомления: через системные API\n\n'
            'Все операции выполняются безопасно через стандартные '
            'интерфейсы операционной системы.',
          ),
          actions: <Widget>[
            TextButton(
              child: const Text('Понятно'),
              onPressed: () {
                Navigator.of(context).pop();
              },
            ),
          ],
        ),
      );

  /// Gets a summary of available capabilities.
  ///
  /// Returns a map of capability names and their availability status.
  Future<Map<String, bool>> getCapabilitySummary() =>
      requestEssentialPermissions();
}
