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

import 'package:flutter/services.dart';
import 'package:jabook/core/background/background_compatibility_checker.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/core/utils/device_info_utils.dart';

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

  /// Gets manufacturer-specific instructions for the user.
  ///
  /// Returns a map with instructions tailored to the device's manufacturer and ROM.
  Future<Map<String, String>> getManufacturerSpecificInstructions() async {
    try {
      if (!Platform.isAndroid) {
        return {
          'title': 'Settings Not Available',
          'message':
              'Manufacturer-specific settings are only available on Android devices.',
        };
      }

      final customRom = await _deviceInfo.getCustomRom();
      final manufacturer = await _deviceInfo.getManufacturer() ?? 'unknown';

      // Default instructions
      var instructions = <String, String>{
        'title': 'Настройки для стабильной работы',
        'message':
            'Для стабильной работы приложения необходимо настроить следующие параметры:',
        'step1': '1. Включите автозапуск приложения',
        'step2': '2. Отключите оптимизацию батареи для приложения',
        'step3': '3. Разрешите фоновую активность',
      };

      // Manufacturer-specific instructions
      if (customRom == 'MIUI') {
        instructions = {
          'title': 'Настройки MIUI для стабильной работы',
          'message':
              'На устройствах Xiaomi/Redmi/Poco необходимо настроить следующие параметры:',
          'step1':
              '1. Автозапуск: Настройки → Приложения → Управление разрешениями → Автозапуск → Включите для JaBook',
          'step2':
              '2. Оптимизация батареи: Настройки → Батарея → Оптимизация батареи → Выберите JaBook → Не оптимизировать',
          'step3':
              '3. Фоновая активность: Настройки → Приложения → JaBook → Батарея → Фоновая активность → Разрешить',
        };
      } else if (customRom == 'EMUI') {
        instructions = {
          'title': 'Настройки EMUI для стабильной работы',
          'message':
              'На устройствах Huawei/Honor необходимо настроить следующие параметры:',
          'step1':
              '1. Защита приложений: Настройки → Приложения → Защита приложений → JaBook → Включите автозапуск',
          'step2':
              '2. Управление батареей: Настройки → Батарея → Управление батареей → JaBook → Не оптимизировать',
          'step3':
              '3. Фоновая активность: Настройки → Приложения → JaBook → Батарея → Разрешить фоновую активность',
        };
      } else if (customRom == 'ColorOS' || customRom == 'RealmeUI') {
        instructions = {
          'title': 'Настройки ColorOS/RealmeUI для стабильной работы',
          'message':
              'На устройствах Oppo/Realme необходимо настроить следующие параметры:',
          'step1':
              '1. Автозапуск: Настройки → Приложения → Автозапуск → Включите для JaBook',
          'step2':
              '2. Оптимизация батареи: Настройки → Батарея → Оптимизация батареи → JaBook → Не оптимизировать',
          'step3':
              '3. Фоновая активность: Настройки → Приложения → JaBook → Батарея → Разрешить фоновую активность',
        };
      } else if (customRom == 'OxygenOS') {
        instructions = {
          'title': 'Настройки OxygenOS для стабильной работы',
          'message':
              'На устройствах OnePlus необходимо настроить следующие параметры:',
          'step1':
              '1. Автозапуск: Настройки → Приложения → Автозапуск → Включите для JaBook',
          'step2':
              '2. Оптимизация батареи: Настройки → Батарея → Оптимизация батареи → JaBook → Не оптимизировать',
          'step3':
              '3. Фоновая активность: Настройки → Приложения → JaBook → Батарея → Разрешить фоновую активность',
        };
      } else if (customRom == 'FuntouchOS') {
        instructions = {
          'title': 'Настройки FuntouchOS/OriginOS для стабильной работы',
          'message':
              'На устройствах Vivo необходимо настроить следующие параметры:',
          'step1':
              '1. Автозапуск: Настройки → Приложения → Автозапуск → Включите для JaBook',
          'step2':
              '2. Оптимизация батареи: Настройки → Батарея → Оптимизация батареи → JaBook → Не оптимизировать',
          'step3':
              '3. Фоновая активность: Настройки → Приложения → JaBook → Батарея → Разрешить фоновую активность',
        };
      } else if (customRom == 'Flyme') {
        instructions = {
          'title': 'Настройки Flyme для стабильной работы',
          'message':
              'На устройствах Meizu необходимо настроить следующие параметры:',
          'step1':
              '1. Автозапуск: Настройки → Приложения → Автозапуск → Включите для JaBook',
          'step2':
              '2. Оптимизация батареи: Настройки → Батарея → Оптимизация батареи → JaBook → Не оптимизировать',
          'step3':
              '3. Фоновая активность: Настройки → Приложения → JaBook → Батарея → Разрешить фоновую активность',
        };
      } else if (customRom == 'One UI') {
        instructions = {
          'title': 'Настройки One UI для стабильной работы',
          'message':
              'На устройствах Samsung рекомендуется настроить следующие параметры:',
          'step1':
              '1. Оптимизация батареи: Настройки → Приложения → JaBook → Батарея → Не оптимизировать',
          'step2':
              '2. Фоновая активность: Настройки → Приложения → JaBook → Батарея → Фоновая активность → Разрешить',
          'step3':
              '3. Автозапуск: Обычно не требуется на Samsung, но можно проверить в настройках приложения',
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
      // Return default instructions on error
      return {
        'title': 'Настройки для стабильной работы',
        'message':
            'Для стабильной работы приложения необходимо настроить следующие параметры:',
        'step1': '1. Включите автозапуск приложения',
        'step2': '2. Отключите оптимизацию батареи для приложения',
        'step3': '3. Разрешите фоновую активность',
      };
    }
  }
}
