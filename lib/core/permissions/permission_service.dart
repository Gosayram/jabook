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
import 'package:jabook/core/animations/dialog_utils.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/core/permissions/manufacturer_permissions_service.dart';
import 'package:jabook/core/utils/device_info_utils.dart';
import 'package:jabook/core/utils/storage_path_utils.dart';
import 'package:jabook/l10n/app_localizations.dart';
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

  /// Manufacturer permissions service instance
  final ManufacturerPermissionsService _manufacturerPermissionsService =
      ManufacturerPermissionsService();

  /// Device info utils instance
  final DeviceInfoUtils _deviceInfo = DeviceInfoUtils.instance;

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

      // Android 14+ (API 34+): Need MANAGE_EXTERNAL_STORAGE for scanning folders
      // This is required for library scanner to work properly
      if (sdkInt >= 34) {
        final hasManageStorage = await _hasManageExternalStoragePermission();
        if (hasManageStorage) return true;
        // Fallback to audio permission (limited access)
        final audioStatus = await Permission.audio.status;
        return audioStatus.isGranted;
      }

      // Android 13 (API 33): Use audio permission for audio files
      if (sdkInt == 33) {
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

      // Android 14+ (API 34+): Request MANAGE_EXTERNAL_STORAGE for scanning folders
      // This is REQUIRED for library scanner to work properly on Android 14+
      // Without it, folder scanning will be very slow or fail
      if (sdkInt >= 34) {
        await _logger.log(
          level: 'info',
          subsystem: 'permissions',
          message:
              'Android 14+ detected, requesting MANAGE_EXTERNAL_STORAGE for folder scanning',
          extra: {'sdk_int': sdkInt},
        );

        // Check if MANAGE_EXTERNAL_STORAGE is already granted
        final hasManageStorage = await _hasManageExternalStoragePermission();
        await _logger.log(
          level: 'info',
          subsystem: 'permissions',
          message:
              'MANAGE_EXTERNAL_STORAGE permission check result (Android 14+)',
          extra: {'has_permission': hasManageStorage},
        );

        if (hasManageStorage) {
          await _logger.log(
            level: 'info',
            subsystem: 'permissions',
            message:
                'MANAGE_EXTERNAL_STORAGE permission already granted (Android 14+)',
          );
          // Verify permission actually works
          final verified = await _verifyStoragePermission();
          if (!verified) {
            await _logger.log(
              level: 'warning',
              subsystem: 'permissions',
              message:
                  'MANAGE_EXTERNAL_STORAGE reported as granted but verification failed (Android 14+)',
            );
          }
          return true;
        }

        // Open system settings for MANAGE_EXTERNAL_STORAGE
        // This is required because permission_handler doesn't properly handle this permission
        await _logger.log(
          level: 'info',
          subsystem: 'permissions',
          message:
              'Opening system settings for MANAGE_EXTERNAL_STORAGE (Android 14+)',
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
                  'MANAGE_EXTERNAL_STORAGE permission granted after opening settings (Android 14+)',
            );
            // Verify permission actually works
            final verified = await _verifyStoragePermission();
            if (verified) {
              return true;
            } else {
              await _logger.log(
                level: 'warning',
                subsystem: 'permissions',
                message:
                    'MANAGE_EXTERNAL_STORAGE granted but verification failed (Android 14+)',
              );
            }
          }
          // Return false - user needs to grant permission in settings
          await _logger.log(
            level: 'warning',
            subsystem: 'permissions',
            message:
                'MANAGE_EXTERNAL_STORAGE not granted. Folder scanning will be slow or fail.',
          );
          return false;
        } on PlatformException catch (e) {
          await _logger.log(
            level: 'error',
            subsystem: 'permissions',
            message:
                'Failed to open system settings for MANAGE_EXTERNAL_STORAGE (Android 14+)',
            cause: e.toString(),
          );
          // Fallback to audio permission (limited access, won't work for folder scanning)
          final audioStatus = await Permission.audio.request();
          if (audioStatus.isGranted) {
            await _logger.log(
              level: 'warning',
              subsystem: 'permissions',
              message:
                  'Audio permission granted (Android 14+), but MANAGE_EXTERNAL_STORAGE is required for folder scanning',
            );
          }
          return false; // Return false because MANAGE_EXTERNAL_STORAGE is required
        }
      }

      // Android 13 (API 33): Request READ_MEDIA_AUDIO for reading audio files
      if (sdkInt == 33) {
        // Log device manufacturer information for troubleshooting
        final manufacturer = await _deviceInfo.getManufacturer();
        final customRom = await _deviceInfo.getCustomRom();
        final romVersion = await _deviceInfo.getRomVersion();
        final needsGuidance = await _manufacturerPermissionsService
            .needsStoragePermissionGuidance();

        await _logger.log(
          level: 'info',
          subsystem: 'permissions',
          message:
              'Android 13 detected, requesting READ_MEDIA_AUDIO permission',
          extra: {
            'sdk_int': sdkInt,
            'manufacturer': manufacturer,
            'custom_rom': customRom,
            'rom_version': romVersion,
            'needs_guidance': needsGuidance,
          },
        );

        // Request READ_MEDIA_AUDIO (this is what we need for reading audio files on Android 13)
        final audioStatus = await Permission.audio.request();
        await _logger.log(
          level: 'info',
          subsystem: 'permissions',
          message:
              'READ_MEDIA_AUDIO permission result (Android 13): ${audioStatus.name}',
          extra: {
            'manufacturer': manufacturer,
            'custom_rom': customRom,
            'status': audioStatus.name,
          },
        );

        if (audioStatus.isGranted) {
          await _logger.log(
            level: 'info',
            subsystem: 'permissions',
            message: 'READ_MEDIA_AUDIO permission granted (Android 13)',
            extra: {
              'manufacturer': manufacturer,
              'custom_rom': customRom,
            },
          );
          // Verify permission actually works, especially for problematic devices
          if (needsGuidance) {
            final verified = await _verifyStoragePermission();
            if (!verified) {
              await _logger.log(
                level: 'warning',
                subsystem: 'permissions',
                message:
                    'Permission reported as granted but verification failed on $customRom device',
                extra: {
                  'manufacturer': manufacturer,
                  'custom_rom': customRom,
                },
              );
              // Still return true, but log the issue for troubleshooting
            }
          }
          return true;
        }

        // If audio permission is permanently denied, try photos permission as fallback
        if (audioStatus.isPermanentlyDenied) {
          await _logger.log(
            level: 'info',
            subsystem: 'permissions',
            message:
                'READ_MEDIA_AUDIO permanently denied, trying photos permission as fallback',
            extra: {
              'manufacturer': manufacturer,
              'custom_rom': customRom,
            },
          );
          final photosStatus = await Permission.photos.request();
          await _logger.log(
            level: 'info',
            subsystem: 'permissions',
            message:
                'Photos permission result (fallback): ${photosStatus.name}',
            extra: {
              'manufacturer': manufacturer,
              'custom_rom': customRom,
            },
          );
          if (photosStatus.isGranted) {
            await _logger.log(
              level: 'info',
              subsystem: 'permissions',
              message: 'Photos permission granted (Android 13, fallback)',
              extra: {
                'manufacturer': manufacturer,
                'custom_rom': customRom,
              },
            );
            // Verify permission actually works
            if (needsGuidance) {
              final verified = await _verifyStoragePermission();
              if (!verified) {
                await _logger.log(
                  level: 'warning',
                  subsystem: 'permissions',
                  message:
                      'Photos permission reported as granted but verification failed on $customRom device',
                  extra: {
                    'manufacturer': manufacturer,
                    'custom_rom': customRom,
                  },
                );
              }
            }
            return true;
          }
        }

        // If both audio and photos permissions are not granted, log manufacturer-specific issue
        await _logger.log(
          level: 'warning',
          subsystem: 'permissions',
          message:
              'Neither READ_MEDIA_AUDIO nor photos permission granted (Android 13)',
          extra: {
            'manufacturer': manufacturer,
            'custom_rom': customRom,
            'rom_version': romVersion,
            'needs_guidance': needsGuidance,
            'audio_status': audioStatus.name,
          },
        );

        // For MIUI and ColorOS, try MANAGE_EXTERNAL_STORAGE as fallback
        if (needsGuidance && (customRom == 'MIUI' || customRom == 'ColorOS')) {
          await _logger.log(
            level: 'info',
            subsystem: 'permissions',
            message:
                'Standard permissions failed on $customRom, trying MANAGE_EXTERNAL_STORAGE',
            extra: {
              'manufacturer': manufacturer,
              'custom_rom': customRom,
            },
          );

          // Check if MANAGE_EXTERNAL_STORAGE is already granted
          final hasManageStorage = await _hasManageExternalStoragePermission();
          if (hasManageStorage) {
            await _logger.log(
              level: 'info',
              subsystem: 'permissions',
              message:
                  'MANAGE_EXTERNAL_STORAGE already granted for $customRom device',
              extra: {
                'manufacturer': manufacturer,
                'custom_rom': customRom,
              },
            );
            // Verify permission actually works
            final verified = await _verifyStoragePermission();
            if (verified) {
              return true;
            }
          }

          // Try to request MANAGE_EXTERNAL_STORAGE for MIUI/ColorOS
          // This is needed because some versions require it even on Android 13
          await _logger.log(
            level: 'info',
            subsystem: 'permissions',
            message:
                'Requesting MANAGE_EXTERNAL_STORAGE for $customRom device (Android 13)',
            extra: {
              'manufacturer': manufacturer,
              'custom_rom': customRom,
            },
          );

          try {
            await _permissionChannel
                .invokeMethod('openManageExternalStorageSettings');
            await _logger.log(
              level: 'info',
              subsystem: 'permissions',
              message:
                  'Opened system settings for MANAGE_EXTERNAL_STORAGE ($customRom, Android 13)',
            );
            // Wait a bit for user to potentially grant permission
            await Future.delayed(const Duration(seconds: 1));
            // Check again if user granted permission
            final hasPermission = await _hasManageExternalStoragePermission();
            if (hasPermission) {
              // Verify permission actually works
              final verified = await _verifyStoragePermission();
              if (verified) {
                await _logger.log(
                  level: 'info',
                  subsystem: 'permissions',
                  message:
                      'MANAGE_EXTERNAL_STORAGE granted and verified for $customRom device',
                  extra: {
                    'manufacturer': manufacturer,
                    'custom_rom': customRom,
                  },
                );
                return true;
              }
            }
          } on PlatformException catch (e) {
            await _logger.log(
              level: 'warning',
              subsystem: 'permissions',
              message:
                  'Failed to open MANAGE_EXTERNAL_STORAGE settings for $customRom',
              cause: e.toString(),
              extra: {
                'manufacturer': manufacturer,
                'custom_rom': customRom,
              },
            );
          }

          // Log that manufacturer-specific guidance may be needed
          await _logger.log(
            level: 'info',
            subsystem: 'permissions',
            message:
                'Storage permission failed on $customRom device, manufacturer-specific guidance may be needed',
            extra: {
              'manufacturer': manufacturer,
              'custom_rom': customRom,
              'rom_version': romVersion,
            },
          );
        }

        return false;
      }

      // Android 12 (API 32): Request storage permission (still required)
      if (sdkInt == 32) {
        // Log device manufacturer information for troubleshooting
        final manufacturer = await _deviceInfo.getManufacturer();
        final customRom = await _deviceInfo.getCustomRom();
        final romVersion = await _deviceInfo.getRomVersion();
        final needsGuidance = await _manufacturerPermissionsService
            .needsStoragePermissionGuidance();

        await _logger.log(
          level: 'info',
          subsystem: 'permissions',
          message: 'Android 12 detected, requesting storage permission',
          extra: {
            'sdk_int': sdkInt,
            'manufacturer': manufacturer,
            'custom_rom': customRom,
            'rom_version': romVersion,
            'needs_guidance': needsGuidance,
          },
        );

        final storageStatus = await Permission.storage.request();
        await _logger.log(
          level: 'info',
          subsystem: 'permissions',
          message:
              'Storage permission result (Android 12): ${storageStatus.name}',
          extra: {
            'manufacturer': manufacturer,
            'custom_rom': customRom,
            'status': storageStatus.name,
          },
        );

        if (storageStatus.isGranted) {
          // Verify permission actually works, especially for problematic devices
          if (needsGuidance) {
            final verified = await _verifyStoragePermission();
            if (!verified) {
              await _logger.log(
                level: 'warning',
                subsystem: 'permissions',
                message:
                    'Storage permission reported as granted but verification failed on $customRom device (Android 12)',
                extra: {
                  'manufacturer': manufacturer,
                  'custom_rom': customRom,
                },
              );
            }
          }
        } else if (needsGuidance) {
          // Log that manufacturer-specific guidance may be needed
          await _logger.log(
            level: 'info',
            subsystem: 'permissions',
            message:
                'Storage permission failed on $customRom device (Android 12), manufacturer-specific guidance may be needed',
            extra: {
              'manufacturer': manufacturer,
              'custom_rom': customRom,
              'rom_version': romVersion,
            },
          );
        }

        return storageStatus.isGranted;
      }

      // Android 11+ (API 30+): Request MANAGE_EXTERNAL_STORAGE first, then storage as fallback
      if (sdkInt >= 30 && sdkInt < 33) {
        // Log device manufacturer information for troubleshooting
        final manufacturer = await _deviceInfo.getManufacturer();
        final customRom = await _deviceInfo.getCustomRom();
        final romVersion = await _deviceInfo.getRomVersion();
        final needsGuidance = await _manufacturerPermissionsService
            .needsStoragePermissionGuidance();

        await _logger.log(
          level: 'info',
          subsystem: 'permissions',
          message:
              'Android 11+ detected, checking MANAGE_EXTERNAL_STORAGE permission',
          extra: {
            'sdk_int': sdkInt,
            'manufacturer': manufacturer,
            'custom_rom': customRom,
            'rom_version': romVersion,
            'needs_guidance': needsGuidance,
          },
        );
        // Check if MANAGE_EXTERNAL_STORAGE is already granted
        final hasManageStorage = await _hasManageExternalStoragePermission();
        await _logger.log(
          level: 'info',
          subsystem: 'permissions',
          message: 'MANAGE_EXTERNAL_STORAGE permission check result',
          extra: {
            'has_permission': hasManageStorage,
            'manufacturer': manufacturer,
            'custom_rom': customRom,
          },
        );
        if (hasManageStorage) {
          await _logger.log(
            level: 'info',
            subsystem: 'permissions',
            message:
                'MANAGE_EXTERNAL_STORAGE permission already granted (Android 11+)',
            extra: {
              'manufacturer': manufacturer,
              'custom_rom': customRom,
            },
          );
          // Verify permission actually works, especially for problematic devices
          if (needsGuidance) {
            final verified = await _verifyStoragePermission();
            if (!verified) {
              await _logger.log(
                level: 'warning',
                subsystem: 'permissions',
                message:
                    'MANAGE_EXTERNAL_STORAGE reported as granted but verification failed on $customRom device (Android 11+)',
                extra: {
                  'manufacturer': manufacturer,
                  'custom_rom': customRom,
                },
              );
            }
          }
          return true;
        }

        // Open system settings for MANAGE_EXTERNAL_STORAGE
        // This is required because permission_handler doesn't properly handle this permission
        await _logger.log(
          level: 'info',
          subsystem: 'permissions',
          message:
              'Opening system settings for MANAGE_EXTERNAL_STORAGE (Android 11+)',
          extra: {
            'manufacturer': manufacturer,
            'custom_rom': customRom,
          },
        );
        try {
          await _permissionChannel
              .invokeMethod('openManageExternalStorageSettings');
          await _logger.log(
            level: 'info',
            subsystem: 'permissions',
            message:
                'System settings opened for MANAGE_EXTERNAL_STORAGE. User needs to grant permission manually.',
            extra: {
              'manufacturer': manufacturer,
              'custom_rom': customRom,
            },
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
                  'MANAGE_EXTERNAL_STORAGE permission granted after opening settings (Android 11+)',
              extra: {
                'manufacturer': manufacturer,
                'custom_rom': customRom,
              },
            );
            // Verify permission actually works
            final verified = await _verifyStoragePermission();
            if (verified) {
              return true;
            } else {
              await _logger.log(
                level: 'warning',
                subsystem: 'permissions',
                message:
                    'MANAGE_EXTERNAL_STORAGE granted but verification failed (Android 11+)',
                extra: {
                  'manufacturer': manufacturer,
                  'custom_rom': customRom,
                },
              );
            }
          } else {
            // For problematic devices, log that guidance may be needed
            if (needsGuidance) {
              await _logger.log(
                level: 'info',
                subsystem: 'permissions',
                message:
                    'MANAGE_EXTERNAL_STORAGE not granted on $customRom device (Android 11+), manufacturer-specific guidance may be needed',
                extra: {
                  'manufacturer': manufacturer,
                  'custom_rom': customRom,
                  'rom_version': romVersion,
                },
              );
            }
          }
          // Return false - user needs to grant permission in settings
          // The app will check again when trying to write
          return false;
        } on PlatformException catch (e) {
          await _logger.log(
            level: 'error',
            subsystem: 'permissions',
            message:
                'Failed to open system settings for MANAGE_EXTERNAL_STORAGE (Android 11+)',
            cause: e.toString(),
            extra: {
              'manufacturer': manufacturer,
              'custom_rom': customRom,
            },
          );
          // Fallback to storage permission
          final storageStatus = await Permission.storage.request();
          await _logger.log(
            level: 'info',
            subsystem: 'permissions',
            message:
                'Storage permission result (fallback, Android 11+): ${storageStatus.name}',
            extra: {
              'manufacturer': manufacturer,
              'custom_rom': customRom,
            },
          );
          if (storageStatus.isGranted && needsGuidance) {
            // Verify permission actually works for problematic devices
            final verified = await _verifyStoragePermission();
            if (!verified) {
              await _logger.log(
                level: 'warning',
                subsystem: 'permissions',
                message:
                    'Storage permission reported as granted but verification failed on $customRom device (Android 11+)',
                extra: {
                  'manufacturer': manufacturer,
                  'custom_rom': customRom,
                },
              );
            }
          }
          return storageStatus.isGranted;
        }
      }

      // Android 10 and below: Request storage permission
      // Log device manufacturer information for troubleshooting
      final manufacturer = await _deviceInfo.getManufacturer();
      final customRom = await _deviceInfo.getCustomRom();
      final romVersion = await _deviceInfo.getRomVersion();
      final needsGuidance = await _manufacturerPermissionsService
          .needsStoragePermissionGuidance();

      await _logger.log(
        level: 'info',
        subsystem: 'permissions',
        message: 'Android 10 and below detected, requesting storage permission',
        extra: {
          'sdk_int': sdkInt,
          'manufacturer': manufacturer,
          'custom_rom': customRom,
          'rom_version': romVersion,
          'needs_guidance': needsGuidance,
        },
      );

      final storageStatus = await Permission.storage.request();
      await _logger.log(
        level: 'info',
        subsystem: 'permissions',
        message:
            'Storage permission result (Android 10 and below): ${storageStatus.name}',
        extra: {
          'manufacturer': manufacturer,
          'custom_rom': customRom,
          'status': storageStatus.name,
        },
      );

      if (storageStatus.isGranted && needsGuidance) {
        // Verify permission actually works for problematic devices
        final verified = await _verifyStoragePermission();
        if (!verified) {
          await _logger.log(
            level: 'warning',
            subsystem: 'permissions',
            message:
                'Storage permission reported as granted but verification failed on $customRom device (Android 10 and below)',
            extra: {
              'manufacturer': manufacturer,
              'custom_rom': customRom,
            },
          );
        }
      } else if (!storageStatus.isGranted && needsGuidance) {
        // Log that manufacturer-specific guidance may be needed
        await _logger.log(
          level: 'info',
          subsystem: 'permissions',
          message:
              'Storage permission failed on $customRom device (Android 10 and below), manufacturer-specific guidance may be needed',
          extra: {
            'manufacturer': manufacturer,
            'custom_rom': customRom,
            'rom_version': romVersion,
          },
        );
      }

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

        if (sdkInt >= 34) {
          // Android 14+: Check MANAGE_EXTERNAL_STORAGE
          final hasManageStorage = await _hasManageExternalStoragePermission();
          final audioStatus = await Permission.audio.status;
          status['storage'] = {
            'granted': hasManageStorage || audioStatus.isGranted,
            'manageExternalStorage': {
              'granted': hasManageStorage,
            },
            'audio': {
              'granted': audioStatus.isGranted,
              'denied': audioStatus.isDenied,
              'permanentlyDenied': audioStatus.isPermanentlyDenied,
            },
          };
        } else if (sdkInt == 33) {
          // Android 13: Check audio/photos permissions
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
    final result = await DialogUtils.showAnimatedDialog<bool>(
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

  /// Checks if storage permission guidance is needed for this device.
  ///
  /// Returns `true` if the device manufacturer requires special storage permission handling
  /// (e.g., MIUI, ColorOS), `false` otherwise.
  Future<bool> needsStoragePermissionGuidance() async =>
      _manufacturerPermissionsService.needsStoragePermissionGuidance();

  /// Checks if SAF (Storage Access Framework) should be suggested as fallback.
  ///
  /// Returns `true` if:
  /// - Permission was denied and device is from problematic manufacturer (MIUI, ColorOS)
  /// - Permission verification failed even though it was reported as granted
  /// - "All files access" option is not available in settings (e.g., Restricted settings on Android 13+)
  /// - User might benefit from using SAF instead of direct file access
  Future<bool> shouldSuggestSafFallback() async {
    try {
      if (!Platform.isAndroid) {
        return false;
      }

      final androidInfo = await DeviceInfoPlugin().androidInfo;
      final sdkInt = androidInfo.version.sdkInt;

      // On Android 11+, check if "All files access" option is available
      if (sdkInt >= 30) {
        final canRequest = await canRequestManageExternalStorage();
        if (!canRequest) {
          await _logger.log(
            level: 'info',
            subsystem: 'permissions',
            message:
                '"All files access" option not available in settings (possibly Restricted settings), suggesting SAF fallback',
            extra: {
              'sdk_int': sdkInt,
            },
          );
          return true;
        }
      }

      final needsGuidance = await _manufacturerPermissionsService
          .needsStoragePermissionGuidance();
      if (!needsGuidance) {
        return false;
      }

      // Check if permission is granted but verification fails
      final hasPermission = await hasStoragePermission();
      if (hasPermission) {
        final verified = await _verifyStoragePermission();
        if (!verified) {
          await _logger.log(
            level: 'info',
            subsystem: 'permissions',
            message:
                'Permission reported as granted but verification failed, suggesting SAF fallback',
          );
          return true;
        }
      }

      // If permission is not granted on problematic device, suggest SAF
      if (!hasPermission) {
        await _logger.log(
          level: 'info',
          subsystem: 'permissions',
          message:
              'Permission not granted on problematic device, suggesting SAF fallback',
        );
        return true;
      }

      return false;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'permissions',
        message: 'Error checking if SAF fallback should be suggested',
        cause: e.toString(),
      );
      return false;
    }
  }

  /// Shows a dialog with manufacturer-specific storage permission instructions.
  ///
  /// This dialog provides device-specific guidance for granting storage permissions
  /// on devices with custom ROMs like MIUI and ColorOS that require special handling.
  ///
  /// Returns `true` if user wants to proceed, `false` otherwise.
  Future<bool> showStoragePermissionGuidanceDialog(
    BuildContext context,
  ) async {
    try {
      if (!Platform.isAndroid) {
        return false;
      }

      final needsGuidance = await _manufacturerPermissionsService
          .needsStoragePermissionGuidance();

      if (!needsGuidance) {
        // No special guidance needed for this device
        return false;
      }

      // Check if context is still valid before async operation
      if (!context.mounted) {
        await _logger.log(
          level: 'warning',
          subsystem: 'permissions',
          message:
              'Context no longer mounted, skipping storage permission guidance dialog',
        );
        return false;
      }

      final instructions = await _manufacturerPermissionsService
          .getStoragePermissionInstructions(context);

      // Check if context is still valid before showing dialog
      if (!context.mounted) {
        await _logger.log(
          level: 'warning',
          subsystem: 'permissions',
          message:
              'Context no longer mounted, skipping storage permission guidance dialog',
        );
        return false;
      }

      final result = await DialogUtils.showAnimatedDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: Text(instructions['title'] ?? 'Storage Permission'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(instructions['message'] ?? ''),
                const SizedBox(height: 16),
                if (instructions['step1'] != null)
                  Text(
                    instructions['step1']!,
                    style: Theme.of(context).textTheme.bodyMedium,
                  ),
                if (instructions['step2'] != null) ...[
                  const SizedBox(height: 8),
                  Text(
                    instructions['step2']!,
                    style: Theme.of(context).textTheme.bodyMedium,
                  ),
                ],
                if (instructions['step3'] != null) ...[
                  const SizedBox(height: 8),
                  Text(
                    instructions['step3']!,
                    style: Theme.of(context).textTheme.bodyMedium,
                  ),
                ],
                if (instructions['note'] != null) ...[
                  const SizedBox(height: 16),
                  Text(
                    instructions['note']!,
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          fontStyle: FontStyle.italic,
                        ),
                  ),
                ],
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: Text(
                AppLocalizations.of(context)?.cancel ?? 'Cancel',
              ),
            ),
            ElevatedButton(
              onPressed: () => Navigator.pop(context, true),
              child: Text(
                AppLocalizations.of(context)?.openSettings ?? 'Open Settings',
              ),
            ),
          ],
        ),
      );

      if (result ?? false) {
        // Open app settings
        await openAppSettings();
        await _logger.log(
          level: 'info',
          subsystem: 'permissions',
          message:
              'User opened settings from storage permission guidance dialog',
        );
      }

      return result ?? false;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'permissions',
        message: 'Error showing storage permission guidance dialog',
        cause: e.toString(),
      );
      return false;
    }
  }

  /// Shows a dialog suggesting SAF (Storage Access Framework) as fallback.
  ///
  /// This dialog is shown when permissions are not working properly on problematic devices.
  /// It offers the user an alternative way to access files using SAF, which doesn't require
  /// special permissions.
  ///
  /// Returns `true` if user wants to use SAF, `false` otherwise.
  Future<bool> showSafFallbackDialog(BuildContext context) async {
    try {
      if (!Platform.isAndroid) {
        return false;
      }

      // Check if context is still valid
      if (!context.mounted) {
        await _logger.log(
          level: 'warning',
          subsystem: 'permissions',
          message: 'Context no longer mounted, skipping SAF fallback dialog',
        );
        return false;
      }

      final localizations = AppLocalizations.of(context);
      final androidInfo = await DeviceInfoPlugin().androidInfo;
      final sdkInt = androidInfo.version.sdkInt;
      final canRequest =
          sdkInt >= 30 ? await canRequestManageExternalStorage() : true;
      final needsGuidance = await _manufacturerPermissionsService
          .needsStoragePermissionGuidance();
      final customRom = await _deviceInfo.getCustomRom();

      // Check if context is still valid after async operations
      if (!context.mounted) {
        await _logger.log(
          level: 'warning',
          subsystem: 'permissions',
          message:
              'Context no longer mounted after async operations, skipping SAF fallback dialog',
        );
        return false;
      }

      // Build message based on the situation
      String message;
      if (!canRequest && sdkInt >= 30) {
        // "All files access" option is not available (e.g., Restricted settings)
        message =
            'The "All files access" option is not available in your device settings. '
            'This may happen if the app was installed outside Google Play Store. '
            'You can use the Storage Access Framework (SAF) to select folders manually. '
            'This method works without requiring special permissions.';
      } else if (needsGuidance) {
        // Problematic device (MIUI, ColorOS, etc.)
        message = localizations?.safFallbackMessage ??
            'File access permissions are not working properly on your $customRom device. '
                'You can use the Storage Access Framework (SAF) to select folders manually. '
                'This method works without requiring special permissions.';
      } else {
        // Generic message
        message = localizations?.safFallbackMessage ??
            'File access permissions are not working properly on your device. '
                'You can use the Storage Access Framework (SAF) to select folders manually. '
                'This method works without requiring special permissions.';
      }

      final result = await DialogUtils.showAnimatedDialog<String>(
        context: context,
        builder: (context) => AlertDialog(
          title: Text(
            localizations?.safFallbackTitle ?? 'Alternative File Access Method',
          ),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(message),
                const SizedBox(height: 16),
                Text(
                  localizations?.safFallbackBenefits ??
                      'Benefits of using SAF:\n• Works on all Android devices\n• No special permissions needed\n• You choose which folders to access',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
                if (!canRequest && sdkInt >= 30) ...[
                  const SizedBox(height: 16),
                  Text(
                    'Note: If you need full file access, you may need to install the app from Google Play Store or grant permission manually in device settings.',
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          fontStyle: FontStyle.italic,
                          color: Theme.of(context).colorScheme.error,
                        ),
                  ),
                ],
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, 'cancel'),
              child: Text(localizations?.cancel ?? 'Cancel'),
            ),
            if (canRequest)
              TextButton(
                onPressed: () => Navigator.pop(context, 'retry'),
                child: Text(
                  localizations?.tryPermissionsAgain ?? 'Try Permissions Again',
                ),
              ),
            ElevatedButton(
              onPressed: () => Navigator.pop(context, 'saf'),
              child: Text(
                localizations?.useSafMethod ?? 'Use Folder Selection',
              ),
            ),
          ],
        ),
      );

      if (result == 'saf') {
        await _logger.log(
          level: 'info',
          subsystem: 'permissions',
          message: 'User chose to use SAF fallback method',
        );
        return true;
      } else if (result == 'retry') {
        await _logger.log(
          level: 'info',
          subsystem: 'permissions',
          message: 'User chose to try permissions again',
        );
        // User wants to try permissions again - return false to allow retry
        return false;
      }

      return false;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'permissions',
        message: 'Error showing SAF fallback dialog',
        cause: e.toString(),
      );
      return false;
    }
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

      // Request storage permission (critical for reading audio files)
      // Skip if Activity is not available
      try {
        await _logger.log(
          level: 'info',
          subsystem: 'permissions',
          message: 'Requesting storage permission (step 1/5)',
        );
        results['storage'] = await requestStoragePermission();
        await _logger.log(
          level: 'info',
          subsystem: 'permissions',
          message: 'Storage permission request completed',
          extra: {'granted': results['storage']},
        );
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
          await _logger.log(
            level: 'error',
            subsystem: 'permissions',
            message: 'Error requesting storage permission',
            cause: e.toString(),
          );
          results['storage'] = false;
        }
      }

      // Request write permission (MANAGE_EXTERNAL_STORAGE for Android 11+)
      // This is required for writing to /storage/emulated/0/JabookAudio
      if (Platform.isAndroid) {
        try {
          final androidInfo = await DeviceInfoPlugin().androidInfo;
          final sdkInt = androidInfo.version.sdkInt;

          if (sdkInt >= 30) {
            await _logger.log(
              level: 'info',
              subsystem: 'permissions',
              message:
                  'Requesting write storage permission (MANAGE_EXTERNAL_STORAGE) (step 2/5)',
            );

            // Check if we already have write permission
            final canWrite = await canWriteToStorage();
            if (!canWrite) {
              // Request MANAGE_EXTERNAL_STORAGE by opening settings
              await _logger.log(
                level: 'info',
                subsystem: 'permissions',
                message:
                    'MANAGE_EXTERNAL_STORAGE not granted, opening settings...',
              );

              // Use the same logic as requestStoragePermission for Android 11+
              try {
                await _permissionChannel
                    .invokeMethod('openManageExternalStorageSettings');
                await _logger.log(
                  level: 'info',
                  subsystem: 'permissions',
                  message: 'Opened settings for MANAGE_EXTERNAL_STORAGE',
                );
                // Wait a bit for user to potentially grant permission
                await Future.delayed(const Duration(seconds: 2));
                // Check again if user granted permission
                final hasPermission =
                    await _hasManageExternalStoragePermission();
                results['write_storage'] = hasPermission;
                await _logger.log(
                  level: 'info',
                  subsystem: 'permissions',
                  message: 'Write storage permission check after request',
                  extra: {'granted': hasPermission},
                );
              } on PlatformException catch (e) {
                await _logger.log(
                  level: 'error',
                  subsystem: 'permissions',
                  message:
                      'Failed to open settings for MANAGE_EXTERNAL_STORAGE',
                  cause: e.toString(),
                );
                results['write_storage'] = false;
              }
            } else {
              results['write_storage'] = true;
              await _logger.log(
                level: 'info',
                subsystem: 'permissions',
                message: 'Write storage permission already granted',
              );
            }
          } else {
            // Android 10 and below: write permission is included in storage permission
            results['write_storage'] = results['storage'] ?? false;
          }
        } on Exception catch (e) {
          await _logger.log(
            level: 'error',
            subsystem: 'permissions',
            message: 'Error checking/requesting write storage permission',
            cause: e.toString(),
          );
          results['write_storage'] = false;
        }
      } else {
        // Non-Android platforms don't need this
        results['write_storage'] = true;
      }

      // Request notification permission (for playback controls)
      try {
        await _logger.log(
          level: 'info',
          subsystem: 'permissions',
          message: 'Requesting notification permission (step 3/5)',
        );
        results['notification'] = await requestNotificationPermission();
        await _logger.log(
          level: 'info',
          subsystem: 'permissions',
          message: 'Notification permission request completed',
          extra: {'granted': results['notification']},
        );
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
          await _logger.log(
            level: 'error',
            subsystem: 'permissions',
            message: 'Error requesting notification permission',
            cause: e.toString(),
          );
          results['notification'] = false;
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

    // On Android 11+, both read and write permissions are needed
    // On older versions, storage permission covers both
    var storageOk = storage;
    if (Platform.isAndroid) {
      final androidInfo = await DeviceInfoPlugin().androidInfo;
      final sdkInt = androidInfo.version.sdkInt;
      if (sdkInt >= 30) {
        // Android 11+: need both read and write permissions
        final canWrite = await canWriteToStorage();
        storageOk = storage && canWrite;
      }
      // Android 10 and below: storage permission covers both read and write
    }

    return storageOk && notification && audio && network;
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

  /// Checks if we can actually write to a specific directory path.
  ///
  /// This method performs a real write test by creating a test file in the directory.
  /// This is more reliable than checking permissions, as it verifies actual write capability.
  ///
  /// [path] is the directory path to test.
  ///
  /// Returns `true` if we can write to the directory, `false` otherwise.
  Future<bool> canWriteToPath(String path) async {
    try {
      // Skip check for content URIs (SAF) - they are handled differently
      if (StoragePathUtils.isContentUri(path)) {
        await _logger.log(
          level: 'debug',
          subsystem: 'permissions',
          message: 'Path is content URI (SAF), skipping write test',
        );
        return true; // Assume accessible if it's a SAF URI
      }

      // Skip check for app-specific directories
      if (isAppSpecificDirectory(path)) {
        await _logger.log(
          level: 'debug',
          subsystem: 'permissions',
          message: 'Path is app-specific, skipping write test',
        );
        return true; // App-specific directories don't need permission check
      }

      final dir = Directory(path);

      // Ensure directory exists
      if (!await dir.exists()) {
        try {
          await dir.create(recursive: true);
        } on Exception catch (e) {
          await _logger.log(
            level: 'warning',
            subsystem: 'permissions',
            message: 'Cannot create directory for write test',
            cause: e.toString(),
          );
          return false;
        }
      }

      // Try to write a test file
      try {
        final testFile = File('${dir.path}/.test_write_permission');
        await testFile.writeAsString('test');
        await testFile.delete();
        await _logger.log(
          level: 'debug',
          subsystem: 'permissions',
          message: 'Write test successful for path',
        );
        return true;
      } on Exception catch (e) {
        await _logger.log(
          level: 'warning',
          subsystem: 'permissions',
          message: 'Write test failed for path',
          cause: e.toString(),
        );
        return false;
      }
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'permissions',
        message: 'Error testing write capability for path',
        cause: e.toString(),
      );
      return false;
    }
  }

  /// Verifies that storage permission actually works by testing file access.
  ///
  /// This method attempts to access a test file to verify that the permission
  /// is not just reported as granted, but actually functional.
  ///
  /// Returns `true` if permission is verified to work, `false` otherwise.
  Future<bool> _verifyStoragePermission() async {
    try {
      if (!Platform.isAndroid) {
        return true;
      }

      // Try to access a common media directory to verify permission
      // Use MediaStore or try to list files in a common location
      final androidInfo = await DeviceInfoPlugin().androidInfo;
      final sdkInt = androidInfo.version.sdkInt;

      if (sdkInt >= 30) {
        // For Android 11+, try to access external storage
        // We'll use a simple test - try to check if we can access Downloads directory
        try {
          const downloadsPath = '/storage/emulated/0/Download';
          final downloadsDir = Directory(downloadsPath);
          // Just check if we can read directory metadata (doesn't require listing files)
          if (await downloadsDir.exists()) {
            await _logger.log(
              level: 'debug',
              subsystem: 'permissions',
              message:
                  'Storage permission verified - can access Downloads directory',
            );
            return true;
          }
        } on Exception {
          // If we can't access Downloads, try Music directory
          try {
            const musicPath = '/storage/emulated/0/Music';
            final musicDir = Directory(musicPath);
            if (await musicDir.exists()) {
              await _logger.log(
                level: 'debug',
                subsystem: 'permissions',
                message:
                    'Storage permission verified - can access Music directory',
              );
              return true;
            }
          } on Exception catch (_) {
            // Continue to fallback
          }
        }

        // For Android 13+, check if we have audio permission and can access MediaStore
        if (sdkInt >= 33) {
          final audioStatus = await Permission.audio.status;
          if (audioStatus.isGranted) {
            // Permission is granted, assume it works
            // Full verification would require MediaStore API which is complex
            await _logger.log(
              level: 'debug',
              subsystem: 'permissions',
              message: 'Storage permission verified - audio permission granted',
            );
            return true;
          }
        }
      }

      // For older Android versions, check storage permission
      final storageStatus = await Permission.storage.status;
      if (storageStatus.isGranted) {
        await _logger.log(
          level: 'debug',
          subsystem: 'permissions',
          message: 'Storage permission verified - storage permission granted',
        );
        return true;
      }

      await _logger.log(
        level: 'warning',
        subsystem: 'permissions',
        message:
            'Storage permission verification failed - no permissions granted',
      );
      return false;
    } on Exception catch (e) {
      await _logger.log(
        level: 'warning',
        subsystem: 'permissions',
        message: 'Error verifying storage permission',
        cause: e.toString(),
      );
      // On error, assume permission might work (don't block user)
      return true;
    }
  }

  /// Checks if the "All files access" option is available in system settings.
  ///
  /// On Android 13+ (API 33+), if the app is installed outside Google Play,
  /// the "All files access" option may not be available due to Restricted settings.
  /// In such cases, SAF should be suggested as the primary method.
  ///
  /// Returns `true` if the option is available, `false` otherwise.
  Future<bool> canRequestManageExternalStorage() async {
    if (!Platform.isAndroid) return true;

    try {
      final androidInfo = await DeviceInfoPlugin().androidInfo;
      if (androidInfo.version.sdkInt < 30) {
        // Android 10 and below don't need this permission
        return true;
      }

      // Use native method to check if the option is available
      final result = await _permissionChannel.invokeMethod<bool>(
        'canRequestManageExternalStorage',
      );
      return result ?? false;
    } on PlatformException catch (e) {
      await _logger.log(
        level: 'warning',
        subsystem: 'permissions',
        message: 'Error checking if can request MANAGE_EXTERNAL_STORAGE',
        cause: e.toString(),
      );
      // If check fails, assume it's not available (safer to suggest SAF)
      return false;
    } on Exception catch (e) {
      await _logger.log(
        level: 'warning',
        subsystem: 'permissions',
        message:
            'Unexpected error checking if can request MANAGE_EXTERNAL_STORAGE',
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

  /// Checks and logs detailed storage permission status at app startup.
  ///
  /// This method provides comprehensive logging of storage permission status
  /// to help diagnose issues on problematic devices (Oppo/Xiaomi).
  Future<void> logStoragePermissionStatusAtStartup() async {
    try {
      if (!Platform.isAndroid) return;

      final androidInfo = await DeviceInfoPlugin().androidInfo;
      final sdkInt = androidInfo.version.sdkInt;
      final manufacturer = await _deviceInfo.getManufacturer();
      final customRom = await _deviceInfo.getCustomRom();
      final romVersion = await _deviceInfo.getRomVersion();

      await _logger.log(
        level: 'info',
        subsystem: 'permissions',
        message: 'Checking storage permission status at startup',
        extra: {
          'sdk_int': sdkInt,
          'manufacturer': manufacturer,
          'custom_rom': customRom,
          'rom_version': romVersion,
        },
      );

      if (sdkInt >= 30) {
        // Android 11+: Check MANAGE_EXTERNAL_STORAGE
        final hasManageStorage = await _hasManageExternalStoragePermission();
        final canRequest = await canRequestManageExternalStorage();
        await _logger.log(
          level: hasManageStorage ? 'info' : 'warning',
          subsystem: 'permissions',
          message: 'MANAGE_EXTERNAL_STORAGE permission status at startup',
          extra: {
            'granted': hasManageStorage,
            'can_request': canRequest,
            'sdk_int': sdkInt,
            'manufacturer': manufacturer,
            'custom_rom': customRom,
          },
        );

        if (!canRequest) {
          await _logger.log(
            level: 'warning',
            subsystem: 'permissions',
            message:
                '"All files access" option not available in settings. SAF fallback should be used.',
            extra: {
              'sdk_int': sdkInt,
              'manufacturer': manufacturer,
              'custom_rom': customRom,
              'rom_version': romVersion,
            },
          );
        }

        if (!hasManageStorage) {
          // Check if we have fallback permissions
          if (sdkInt >= 33) {
            final audioStatus = await Permission.audio.status;
            final photosStatus = await Permission.photos.status;
            await _logger.log(
              level: 'info',
              subsystem: 'permissions',
              message: 'Fallback permissions status (Android 13+)',
              extra: {
                'audio_granted': audioStatus.isGranted,
                'photos_granted': photosStatus.isGranted,
                'manufacturer': manufacturer,
                'custom_rom': customRom,
              },
            );
          } else if (sdkInt >= 30) {
            final storageStatus = await Permission.storage.status;
            await _logger.log(
              level: 'info',
              subsystem: 'permissions',
              message: 'Fallback storage permission status (Android 11-12)',
              extra: {
                'storage_granted': storageStatus.isGranted,
                'manufacturer': manufacturer,
                'custom_rom': customRom,
              },
            );
          }

          // Log warning for problematic devices
          final needsGuidance = await _manufacturerPermissionsService
              .needsStoragePermissionGuidance();
          if (needsGuidance) {
            await _logger.log(
              level: 'warning',
              subsystem: 'permissions',
              message:
                  'MANAGE_EXTERNAL_STORAGE not granted on $customRom device. User may need to grant permission manually or use SAF fallback.',
              extra: {
                'manufacturer': manufacturer,
                'custom_rom': customRom,
                'rom_version': romVersion,
                'sdk_int': sdkInt,
              },
            );
          }
        }
      } else {
        // Android 10 and below: Check storage permission
        final storageStatus = await Permission.storage.status;
        await _logger.log(
          level: storageStatus.isGranted ? 'info' : 'warning',
          subsystem: 'permissions',
          message:
              'Storage permission status at startup (Android 10 and below)',
          extra: {
            'granted': storageStatus.isGranted,
            'sdk_int': sdkInt,
            'manufacturer': manufacturer,
            'custom_rom': customRom,
          },
        );
      }
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'permissions',
        message: 'Error logging storage permission status at startup',
        cause: e.toString(),
      );
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
