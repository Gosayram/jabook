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

import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:jabook/core/infrastructure/background/background_compatibility_checker.dart';
import 'package:jabook/core/infrastructure/config/app_config.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:jabook/core/utils/device_info_utils.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Service for managing manufacturer-specific settings on Android devices.
///
/// This service provides methods to open manufacturer-specific settings
/// for autostart, battery optimization, and background restrictions.
/// It works with [DeviceInfoUtils] to detect the device manufacturer
/// and uses native Android code to open the appropriate settings screens.
class ManufacturerPermissionsService {
  ManufacturerPermissionsService._();

  /// Factory constructor to get the singleton instance.
  factory ManufacturerPermissionsService() => _instance;

  /// Singleton instance
  static final ManufacturerPermissionsService _instance =
      ManufacturerPermissionsService._();

  /// Method channel for manufacturer settings operations
  static const MethodChannel _channel =
      MethodChannel('manufacturer_settings_channel');

  /// Logger instance
  final StructuredLogger _logger = StructuredLogger();

  /// Device info utils instance
  final DeviceInfoUtils _deviceInfo = DeviceInfoUtils.instance;

  /// Opens autostart settings for the app.
  ///
  /// This will open manufacturer-specific autostart settings if available,
  /// or fall back to standard Android app settings.
  ///
  /// Returns `true` if settings were opened successfully, `false` otherwise.
  Future<bool> openAutostartSettings() async {
    // Log analytics event
    try {
      final deviceInfo = DeviceInfoUtils.instance;
      final manufacturer = await deviceInfo.getManufacturer();
      final customRom = await deviceInfo.getCustomRom();
      final romVersion = await deviceInfo.getRomVersion();
      final checker = BackgroundCompatibilityChecker();
      await checker.logUserOpenedOemGuidance(
        manufacturer: manufacturer ?? 'unknown',
        customRom: customRom,
        romVersion: romVersion,
      );
    } on Exception {
      // Ignore analytics errors
    }
    try {
      if (!Platform.isAndroid) {
        await _logger.log(
          level: 'warning',
          subsystem: 'manufacturer_permissions',
          message: 'Autostart settings only available on Android',
        );
        return false;
      }

      await _logger.log(
        level: 'info',
        subsystem: 'manufacturer_permissions',
        message: 'Opening autostart settings',
      );

      final result = await _channel.invokeMethod<bool>('openAutostartSettings');
      final success = result ?? false;

      await _logger.log(
        level: success ? 'info' : 'warning',
        subsystem: 'manufacturer_permissions',
        message: 'Autostart settings opened',
        extra: {'success': success},
      );

      return success;
    } on PlatformException catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'manufacturer_permissions',
        message: 'Error opening autostart settings',
        cause: e.toString(),
      );
      return false;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'manufacturer_permissions',
        message: 'Unexpected error opening autostart settings',
        cause: e.toString(),
      );
      return false;
    }
  }

  /// Opens battery optimization settings for the app.
  ///
  /// This will open manufacturer-specific battery optimization settings if available,
  /// or fall back to standard Android battery optimization settings.
  ///
  /// Returns `true` if settings were opened successfully, `false` otherwise.
  Future<bool> openBatteryOptimizationSettings() async {
    // Log analytics event
    try {
      final deviceInfo = DeviceInfoUtils.instance;
      final manufacturer = await deviceInfo.getManufacturer();
      final customRom = await deviceInfo.getCustomRom();
      final romVersion = await deviceInfo.getRomVersion();
      final checker = BackgroundCompatibilityChecker();
      await checker.logUserOpenedOemGuidance(
        manufacturer: manufacturer ?? 'unknown',
        customRom: customRom,
        romVersion: romVersion,
      );
    } on Exception {
      // Ignore analytics errors
    }
    try {
      if (!Platform.isAndroid) {
        await _logger.log(
          level: 'warning',
          subsystem: 'manufacturer_permissions',
          message: 'Battery optimization settings only available on Android',
        );
        return false;
      }

      await _logger.log(
        level: 'info',
        subsystem: 'manufacturer_permissions',
        message: 'Opening battery optimization settings',
      );

      final result = await _channel.invokeMethod<bool>(
        'openBatteryOptimizationSettings',
      );
      final success = result ?? false;

      await _logger.log(
        level: success ? 'info' : 'warning',
        subsystem: 'manufacturer_permissions',
        message: 'Battery optimization settings opened',
        extra: {'success': success},
      );

      return success;
    } on PlatformException catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'manufacturer_permissions',
        message: 'Error opening battery optimization settings',
        cause: e.toString(),
      );
      return false;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'manufacturer_permissions',
        message: 'Unexpected error opening battery optimization settings',
        cause: e.toString(),
      );
      return false;
    }
  }

  /// Opens background restrictions settings for the app.
  ///
  /// This will open manufacturer-specific background restrictions settings if available,
  /// or fall back to standard Android settings.
  ///
  /// Returns `true` if settings were opened successfully, `false` otherwise.
  Future<bool> openBackgroundRestrictionsSettings() async {
    // Log analytics event
    try {
      final deviceInfo = DeviceInfoUtils.instance;
      final manufacturer = await deviceInfo.getManufacturer();
      final customRom = await deviceInfo.getCustomRom();
      final romVersion = await deviceInfo.getRomVersion();
      final checker = BackgroundCompatibilityChecker();
      await checker.logUserOpenedOemGuidance(
        manufacturer: manufacturer ?? 'unknown',
        customRom: customRom,
        romVersion: romVersion,
      );
    } on Exception {
      // Ignore analytics errors
    }
    try {
      if (!Platform.isAndroid) {
        await _logger.log(
          level: 'warning',
          subsystem: 'manufacturer_permissions',
          message: 'Background restrictions settings only available on Android',
        );
        return false;
      }

      await _logger.log(
        level: 'info',
        subsystem: 'manufacturer_permissions',
        message: 'Opening background restrictions settings',
      );

      final result = await _channel.invokeMethod<bool>(
        'openBackgroundRestrictionsSettings',
      );
      final success = result ?? false;

      await _logger.log(
        level: success ? 'info' : 'warning',
        subsystem: 'manufacturer_permissions',
        message: 'Background restrictions settings opened',
        extra: {'success': success},
      );

      return success;
    } on PlatformException catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'manufacturer_permissions',
        message: 'Error opening background restrictions settings',
        cause: e.toString(),
      );
      return false;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'manufacturer_permissions',
        message: 'Unexpected error opening background restrictions settings',
        cause: e.toString(),
      );
      return false;
    }
  }

  /// Checks if autostart is enabled for the app (if possible to determine).
  ///
  /// Note: Most manufacturers don't provide a public API to check autostart status.
  /// This method may return `false` even if autostart is enabled.
  ///
  /// Returns `true` if autostart is enabled, `false` if disabled or cannot be determined.
  Future<bool> checkAutostartEnabled() async {
    try {
      if (!Platform.isAndroid) {
        return false;
      }

      final result = await _channel.invokeMethod<bool>('checkAutostartEnabled');
      return result ?? false;
    } on PlatformException catch (e) {
      await _logger.log(
        level: 'debug',
        subsystem: 'manufacturer_permissions',
        message: 'Cannot check autostart status (expected on most devices)',
        cause: e.toString(),
      );
      return false;
    } on Exception catch (e) {
      await _logger.log(
        level: 'debug',
        subsystem: 'manufacturer_permissions',
        message: 'Error checking autostart status',
        cause: e.toString(),
      );
      return false;
    }
  }

  /// Determines if manufacturer-specific settings should be shown to the user.
  ///
  /// Returns `true` if the device has a custom ROM that requires special settings,
  /// or if the device is from a manufacturer known to have aggressive battery optimization.
  Future<bool> shouldShowManufacturerSettings() async {
    try {
      if (!Platform.isAndroid) {
        return false;
      }

      final needsSettings = await _deviceInfo.needsManufacturerSettings();
      final isChinese = await _deviceInfo.isChineseManufacturer();

      await _logger.log(
        level: 'debug',
        subsystem: 'manufacturer_permissions',
        message: 'Checking if manufacturer settings should be shown',
        extra: {
          'needs_settings': needsSettings,
          'is_chinese_manufacturer': isChinese,
        },
      );

      // Show settings if device needs them or is from a Chinese manufacturer
      return needsSettings || isChinese;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'manufacturer_permissions',
        message: 'Error checking if manufacturer settings should be shown',
        cause: e.toString(),
      );
      // Default to showing settings if we can't determine
      return true;
    }
  }

  /// Checks if storage permissions need special handling for this manufacturer.
  ///
  /// Returns `true` if the device manufacturer requires special storage permission handling
  /// (e.g., MIUI, ColorOS), `false` otherwise.
  Future<bool> needsStoragePermissionGuidance() async {
    try {
      if (!Platform.isAndroid) {
        return false;
      }

      final customRom = await _deviceInfo.getCustomRom();
      final manufacturer = await _deviceInfo.getManufacturer() ?? '';

      // MIUI and ColorOS require special storage permission handling
      final needsGuidance = customRom == 'MIUI' ||
          customRom == 'ColorOS' ||
          customRom == 'RealmeUI' ||
          manufacturer.toLowerCase().contains('xiaomi') ||
          manufacturer.toLowerCase().contains('oppo') ||
          manufacturer.toLowerCase().contains('realme');

      await _logger.log(
        level: 'debug',
        subsystem: 'manufacturer_permissions',
        message: 'Checking if storage permission guidance is needed',
        extra: {
          'needs_guidance': needsGuidance,
          'custom_rom': customRom,
          'manufacturer': manufacturer,
        },
      );

      return needsGuidance;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'manufacturer_permissions',
        message: 'Error checking if storage permission guidance is needed',
        cause: e.toString(),
      );
      return false;
    }
  }

  /// Opens storage permission settings for the app.
  ///
  /// This will open manufacturer-specific storage permission settings if available,
  /// or fall back to standard Android app settings.
  ///
  /// Returns `true` if settings were opened successfully, `false` otherwise.
  Future<bool> openStoragePermissionSettings() async {
    try {
      if (!Platform.isAndroid) {
        await _logger.log(
          level: 'warning',
          subsystem: 'manufacturer_permissions',
          message: 'Storage permission settings only available on Android',
        );
        return false;
      }

      await _logger.log(
        level: 'info',
        subsystem: 'manufacturer_permissions',
        message: 'Opening storage permission settings',
      );

      // Try to open manufacturer-specific storage settings
      // For now, fall back to standard app settings
      // This can be enhanced with manufacturer-specific Intents if needed
      final result = await _channel.invokeMethod<bool>(
        'openStoragePermissionSettings',
      );
      final success = result ?? false;

      if (!success) {
        // Fallback to standard app settings
        await _logger.log(
          level: 'info',
          subsystem: 'manufacturer_permissions',
          message: 'Falling back to standard app settings',
        );
        // Use permission_handler's openAppSettings as fallback
        // This will be handled by PermissionService
        return false;
      }

      await _logger.log(
        level: success ? 'info' : 'warning',
        subsystem: 'manufacturer_permissions',
        message: 'Storage permission settings opened',
        extra: {'success': success},
      );

      return success;
    } on PlatformException catch (e) {
      await _logger.log(
        level: 'debug',
        subsystem: 'manufacturer_permissions',
        message:
            'Manufacturer-specific storage settings not available, using fallback',
        cause: e.toString(),
      );
      return false;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'manufacturer_permissions',
        message: 'Unexpected error opening storage permission settings',
        cause: e.toString(),
      );
      return false;
    }
  }

  /// Gets manufacturer-specific storage permission instructions for the user.
  ///
  /// Returns a map with instructions tailored to the device's manufacturer and ROM
  /// for granting storage permissions.
  ///
  /// [context] is used to get localized strings. If null, English defaults are used.
  Future<Map<String, String>> getStoragePermissionInstructions(
    BuildContext? context,
  ) async {
    // Get localizations before async operations to avoid BuildContext issues
    final localizations = context != null && context.mounted
        ? AppLocalizations.of(context)
        : null;

    try {
      if (!Platform.isAndroid) {
        return {
          'title': localizations?.storagePermissionGuidanceTitle ??
              'Storage Permission Not Available',
          'message': localizations?.storagePermissionGuidanceMessage ??
              'Storage permissions are only relevant on Android devices.',
        };
      }

      final customRom = await _deviceInfo.getCustomRom();
      final manufacturer = await _deviceInfo.getManufacturer() ?? 'unknown';
      final romVersion = await _deviceInfo.getRomVersion();

      // Default instructions
      var instructions = <String, String>{
        'title': localizations?.storagePermissionGuidanceTitle ??
            'File Access Permission',
        'message': localizations?.storagePermissionGuidanceMessage ??
            'To access audio files, you need to grant file access permission.',
        'step1': localizations?.storagePermissionGuidanceStep1 ??
            '1. Open ${AppConfig().displayAppName} app settings',
        'step2': localizations?.storagePermissionGuidanceStep2 ??
            '2. Go to Permissions section',
        'step3': localizations?.storagePermissionGuidanceStep3 ??
            '3. Enable "Files and media" or "Storage" permission',
      };

      // Manufacturer-specific instructions
      if (customRom == 'MIUI') {
        instructions = {
          'title': localizations?.storagePermissionGuidanceMiuiTitle ??
              'File Access Permission (MIUI)',
          'message': localizations?.storagePermissionGuidanceMiuiMessage ??
              'On Xiaomi/Redmi/Poco (MIUI) devices, you need to grant file access permission:',
          'step1': localizations?.storagePermissionGuidanceMiuiStep1 ??
              '1. Open Settings → Apps → ${AppConfig().displayAppName} → Permissions',
          'step2': localizations?.storagePermissionGuidanceMiuiStep2 ??
              '2. Enable "Files and media" or "Storage" permission',
          'step3': localizations?.storagePermissionGuidanceMiuiStep3 ??
              '3. If access is limited, enable "Manage all files" in MIUI Security settings',
          'note': localizations?.storagePermissionGuidanceMiuiNote ??
              'Note: On some MIUI versions, you may need to additionally enable "Manage all files" in Settings → Security → Permission management.',
        };
      } else if (customRom == 'ColorOS' || customRom == 'RealmeUI') {
        instructions = {
          'title': localizations?.storagePermissionGuidanceColorosTitle ??
              'File Access Permission (ColorOS/RealmeUI)',
          'message': localizations?.storagePermissionGuidanceColorosMessage ??
              'On Oppo/Realme (ColorOS/RealmeUI) devices, you need to grant file access permission:',
          'step1': localizations?.storagePermissionGuidanceColorosStep1 ??
              '1. Open Settings → Apps → ${AppConfig().displayAppName} → Permissions',
          'step2': localizations?.storagePermissionGuidanceColorosStep2 ??
              '2. Enable "Files and media" permission',
          'step3': localizations?.storagePermissionGuidanceColorosStep3 ??
              '3. If access is limited, check "Files and media" settings in permissions section',
          'note': localizations?.storagePermissionGuidanceColorosNote ??
              'Note: On some ColorOS versions, you may need to additionally allow file access in security settings.',
        };
      }

      await _logger.log(
        level: 'debug',
        subsystem: 'manufacturer_permissions',
        message: 'Storage permission instructions retrieved',
        extra: {
          'custom_rom': customRom,
          'manufacturer': manufacturer,
          'rom_version': romVersion,
        },
      );

      return instructions;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'manufacturer_permissions',
        message: 'Error getting storage permission instructions',
        cause: e.toString(),
      );
      // Return default instructions on error (using localizations obtained before async)
      return {
        'title': localizations?.storagePermissionGuidanceTitle ??
            'File Access Permission',
        'message': localizations?.storagePermissionGuidanceMessage ??
            'To access audio files, you need to grant file access permission.',
        'step1': localizations?.storagePermissionGuidanceStep1 ??
            '1. Open ${AppConfig().displayAppName} app settings',
        'step2': localizations?.storagePermissionGuidanceStep2 ??
            '2. Go to Permissions section',
        'step3': localizations?.storagePermissionGuidanceStep3 ??
            '3. Enable "Files and media" or "Storage" permission',
      };
    }
  }

  /// Gets manufacturer-specific instructions for the user.
  ///
  /// Returns a map with instructions tailored to the device's manufacturer and ROM.
  ///
  /// [context] is used to get localized strings. If null, English defaults are used.
  Future<Map<String, String>> getManufacturerSpecificInstructions(
    BuildContext? context,
  ) async {
    // Get localizations before async operations to avoid BuildContext issues
    final localizations = context != null && context.mounted
        ? AppLocalizations.of(context)
        : null;
    final appName = AppConfig().displayAppName;

    try {
      if (!Platform.isAndroid) {
        return {
          'title': localizations?.manufacturerSettingsNotAvailable ??
              'Settings Not Available',
          'message': localizations?.manufacturerSettingsNotAvailableMessage ??
              'Manufacturer-specific settings are only available on Android devices.',
        };
      }

      final customRom = await _deviceInfo.getCustomRom();
      final manufacturer = await _deviceInfo.getManufacturer() ?? 'unknown';

      // Default instructions
      var instructions = <String, String>{
        'title': localizations?.manufacturerSettingsDefaultTitle ??
            'Settings for Stable Operation',
        'message': localizations?.manufacturerSettingsDefaultMessage ??
            'To ensure stable operation, you need to configure the following settings:',
        'step1': localizations?.manufacturerSettingsDefaultStep1 ??
            '1. Enable application autostart',
        'step2': localizations?.manufacturerSettingsDefaultStep2 ??
            '2. Disable battery optimization for the application',
        'step3': localizations?.manufacturerSettingsDefaultStep3 ??
            '3. Allow background activity',
      };

      // Manufacturer-specific instructions
      if (customRom == 'MIUI') {
        instructions = {
          'title': localizations?.manufacturerSettingsMiuiTitle ??
              'MIUI Settings for Stable Operation',
          'message': localizations?.manufacturerSettingsMiuiMessage ??
              'On Xiaomi/Redmi/Poco devices, you need to configure the following settings:',
          'step1': localizations?.manufacturerSettingsMiuiStep1(appName) ??
              '1. Autostart: Settings → Apps → Permission management → Autostart → Enable for $appName',
          'step2': localizations?.manufacturerSettingsMiuiStep2(appName) ??
              '2. Battery optimization: Settings → Battery → Battery optimization → Select $appName → Don\'t optimize',
          'step3': localizations?.manufacturerSettingsMiuiStep3(appName) ??
              '3. Background activity: Settings → Apps → $appName → Battery → Background activity → Allow',
        };
      } else if (customRom == 'EMUI') {
        instructions = {
          'title': localizations?.manufacturerSettingsEmuiTitle ??
              'EMUI Settings for Stable Operation',
          'message': localizations?.manufacturerSettingsEmuiMessage ??
              'On Huawei/Honor devices, you need to configure the following settings:',
          'step1': localizations?.manufacturerSettingsEmuiStep1(appName) ??
              '1. App protection: Settings → Apps → App protection → $appName → Enable autostart',
          'step2': localizations?.manufacturerSettingsEmuiStep2(appName) ??
              '2. Battery management: Settings → Battery → Battery management → $appName → Don\'t optimize',
          'step3': localizations?.manufacturerSettingsEmuiStep3(appName) ??
              '3. Background activity: Settings → Apps → $appName → Battery → Allow background activity',
        };
      } else if (customRom == 'ColorOS' || customRom == 'RealmeUI') {
        instructions = {
          'title': localizations?.manufacturerSettingsColorosTitle ??
              'ColorOS/RealmeUI Settings for Stable Operation',
          'message': localizations?.manufacturerSettingsColorosMessage ??
              'On Oppo/Realme devices, you need to configure the following settings:',
          'step1': localizations?.manufacturerSettingsColorosStep1(appName) ??
              '1. Autostart: Settings → Apps → Autostart → Enable for $appName',
          'step2': localizations?.manufacturerSettingsColorosStep2(appName) ??
              '2. Battery optimization: Settings → Battery → Battery optimization → $appName → Don\'t optimize',
          'step3': localizations?.manufacturerSettingsColorosStep3(appName) ??
              '3. Background activity: Settings → Apps → $appName → Battery → Allow background activity',
        };
      } else if (customRom == 'OxygenOS') {
        instructions = {
          'title': localizations?.manufacturerSettingsOxygenosTitle ??
              'OxygenOS Settings for Stable Operation',
          'message': localizations?.manufacturerSettingsOxygenosMessage ??
              'On OnePlus devices, you need to configure the following settings:',
          'step1': localizations?.manufacturerSettingsOxygenosStep1(appName) ??
              '1. Autostart: Settings → Apps → Autostart → Enable for $appName',
          'step2': localizations?.manufacturerSettingsOxygenosStep2(appName) ??
              '2. Battery optimization: Settings → Battery → Battery optimization → $appName → Don\'t optimize',
          'step3': localizations?.manufacturerSettingsOxygenosStep3(appName) ??
              '3. Background activity: Settings → Apps → $appName → Battery → Allow background activity',
        };
      } else if (customRom == 'FuntouchOS') {
        instructions = {
          'title': localizations?.manufacturerSettingsFuntouchosTitle ??
              'FuntouchOS/OriginOS Settings for Stable Operation',
          'message': localizations?.manufacturerSettingsFuntouchosMessage ??
              'On Vivo devices, you need to configure the following settings:',
          'step1': localizations
                  ?.manufacturerSettingsFuntouchosStep1(appName) ??
              '1. Autostart: Settings → Apps → Autostart → Enable for $appName',
          'step2': localizations
                  ?.manufacturerSettingsFuntouchosStep2(appName) ??
              '2. Battery optimization: Settings → Battery → Battery optimization → $appName → Don\'t optimize',
          'step3': localizations
                  ?.manufacturerSettingsFuntouchosStep3(appName) ??
              '3. Background activity: Settings → Apps → $appName → Battery → Allow background activity',
        };
      } else if (customRom == 'Flyme') {
        instructions = {
          'title': localizations?.manufacturerSettingsFlymeTitle ??
              'Flyme Settings for Stable Operation',
          'message': localizations?.manufacturerSettingsFlymeMessage ??
              'On Meizu devices, you need to configure the following settings:',
          'step1': localizations?.manufacturerSettingsFlymeStep1(appName) ??
              '1. Autostart: Settings → Apps → Autostart → Enable for $appName',
          'step2': localizations?.manufacturerSettingsFlymeStep2(appName) ??
              '2. Battery optimization: Settings → Battery → Battery optimization → $appName → Don\'t optimize',
          'step3': localizations?.manufacturerSettingsFlymeStep3(appName) ??
              '3. Background activity: Settings → Apps → $appName → Battery → Allow background activity',
        };
      } else if (customRom == 'One UI') {
        instructions = {
          'title': localizations?.manufacturerSettingsOneuiTitle ??
              'One UI Settings for Stable Operation',
          'message': localizations?.manufacturerSettingsOneuiMessage ??
              'On Samsung devices, it is recommended to configure the following settings:',
          'step1': localizations?.manufacturerSettingsOneuiStep1(appName) ??
              '1. Battery optimization: Settings → Apps → $appName → Battery → Don\'t optimize',
          'step2': localizations?.manufacturerSettingsOneuiStep2(appName) ??
              '2. Background activity: Settings → Apps → $appName → Battery → Background activity → Allow',
          'step3': localizations?.manufacturerSettingsOneuiStep3 ??
              '3. Autostart: Usually not required on Samsung, but you can check in app settings',
        };
      }

      await _logger.log(
        level: 'debug',
        subsystem: 'manufacturer_permissions',
        message: 'Manufacturer-specific instructions retrieved',
        extra: {
          'custom_rom': customRom,
          'manufacturer': manufacturer,
        },
      );

      return instructions;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'manufacturer_permissions',
        message: 'Error getting manufacturer-specific instructions',
        cause: e.toString(),
      );
      // Return default instructions on error (using localizations obtained before async)
      return {
        'title': localizations?.manufacturerSettingsDefaultTitle ??
            'Settings for Stable Operation',
        'message': localizations?.manufacturerSettingsDefaultMessage ??
            'To ensure stable operation, you need to configure the following settings:',
        'step1': localizations?.manufacturerSettingsDefaultStep1 ??
            '1. Enable application autostart',
        'step2': localizations?.manufacturerSettingsDefaultStep2 ??
            '2. Disable battery optimization for the application',
        'step3': localizations?.manufacturerSettingsDefaultStep3 ??
            '3. Allow background activity',
      };
    }
  }
}
