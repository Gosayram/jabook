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

import 'package:device_info_plus/device_info_plus.dart';
import 'package:flutter/services.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';

/// Utility class for detecting Android device manufacturer and custom ROM information.
///
/// This class helps identify the device manufacturer and custom Android ROM
/// (MIUI, EMUI, ColorOS, etc.) to provide manufacturer-specific settings and optimizations.
///
/// Note: This class is no longer a singleton. Use [deviceInfoUtilsProvider]
/// to get an instance via dependency injection.
class DeviceInfoUtils {
  /// Constructor for DeviceInfoUtils.
  ///
  /// Use [deviceInfoUtilsProvider] to get an instance via dependency injection.
  DeviceInfoUtils();

  /// Logger instance
  final StructuredLogger _logger = StructuredLogger();

  /// Method channel for device info operations
  static const MethodChannel _deviceInfoChannel =
      MethodChannel('device_info_channel');

  /// Cached device info to avoid repeated queries
  String? _cachedManufacturer;
  String? _cachedBrand;
  String? _cachedCustomRom;
  String? _cachedRomVersion;
  String? _cachedFirmwareVersion;
  int? _cachedStandbyBucket;
  AndroidDeviceInfo? _cachedAndroidInfo;

  /// Gets the device manufacturer name (e.g., "Xiaomi", "Samsung", "Huawei").
  Future<String?> getManufacturer() async {
    if (_cachedManufacturer != null) return _cachedManufacturer;

    try {
      if (!Platform.isAndroid) {
        _cachedManufacturer = null;
        return null;
      }

      final androidInfo = await _getAndroidInfo();
      _cachedManufacturer = androidInfo.manufacturer.toLowerCase();
      await _logger.log(
        level: 'debug',
        subsystem: 'device_info',
        message: 'Device manufacturer detected',
        extra: {'manufacturer': _cachedManufacturer},
      );
      return _cachedManufacturer;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'device_info',
        message: 'Error getting manufacturer',
        cause: e.toString(),
      );
      return null;
    }
  }

  /// Gets the device brand name (e.g., "Xiaomi", "Samsung", "Huawei").
  Future<String?> getBrand() async {
    if (_cachedBrand != null) return _cachedBrand;

    try {
      if (!Platform.isAndroid) {
        _cachedBrand = null;
        return null;
      }

      final androidInfo = await _getAndroidInfo();
      _cachedBrand = androidInfo.brand.toLowerCase();
      await _logger.log(
        level: 'debug',
        subsystem: 'device_info',
        message: 'Device brand detected',
        extra: {'brand': _cachedBrand},
      );
      return _cachedBrand;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'device_info',
        message: 'Error getting brand',
        cause: e.toString(),
      );
      return null;
    }
  }

  /// Gets the custom ROM name (e.g., "MIUI", "EMUI", "ColorOS", "One UI").
  ///
  /// Returns null if the device uses stock Android or the ROM cannot be determined.
  Future<String?> getCustomRom() async {
    if (_cachedCustomRom != null) return _cachedCustomRom;

    try {
      if (!Platform.isAndroid) {
        _cachedCustomRom = null;
        return null;
      }

      final androidInfo = await _getAndroidInfo();
      final manufacturer = androidInfo.manufacturer.toLowerCase();
      final brand = androidInfo.brand.toLowerCase();

      // Check for MIUI (Xiaomi, Redmi, Poco)
      if (manufacturer.contains('xiaomi') ||
          brand.contains('xiaomi') ||
          brand.contains('redmi') ||
          brand.contains('poco')) {
        _cachedCustomRom = 'MIUI';
        await _logger.log(
          level: 'debug',
          subsystem: 'device_info',
          message: 'Custom ROM detected: MIUI',
        );
        return _cachedCustomRom;
      }

      // Check for EMUI/HarmonyOS (Huawei, Honor)
      if (manufacturer.contains('huawei') ||
          brand.contains('huawei') ||
          brand.contains('honor')) {
        // Try to detect HarmonyOS vs EMUI (simplified check)
        _cachedCustomRom =
            'EMUI'; // Default to EMUI, HarmonyOS detection is complex
        await _logger.log(
          level: 'debug',
          subsystem: 'device_info',
          message: 'Custom ROM detected: EMUI/HarmonyOS',
        );
        return _cachedCustomRom;
      }

      // Check for ColorOS (Oppo)
      if (manufacturer.contains('oppo') || brand.contains('oppo')) {
        _cachedCustomRom = 'ColorOS';
        await _logger.log(
          level: 'debug',
          subsystem: 'device_info',
          message: 'Custom ROM detected: ColorOS',
        );
        return _cachedCustomRom;
      }

      // Check for RealmeUI (Realme - based on ColorOS)
      if (manufacturer.contains('realme') || brand.contains('realme')) {
        _cachedCustomRom = 'RealmeUI';
        await _logger.log(
          level: 'debug',
          subsystem: 'device_info',
          message: 'Custom ROM detected: RealmeUI',
        );
        return _cachedCustomRom;
      }

      // Check for OxygenOS (OnePlus - based on ColorOS in newer versions)
      if (manufacturer.contains('oneplus') || brand.contains('oneplus')) {
        _cachedCustomRom = 'OxygenOS';
        await _logger.log(
          level: 'debug',
          subsystem: 'device_info',
          message: 'Custom ROM detected: OxygenOS',
        );
        return _cachedCustomRom;
      }

      // Check for FuntouchOS/OriginOS (Vivo)
      if (manufacturer.contains('vivo') || brand.contains('vivo')) {
        _cachedCustomRom = 'FuntouchOS';
        await _logger.log(
          level: 'debug',
          subsystem: 'device_info',
          message: 'Custom ROM detected: FuntouchOS/OriginOS',
        );
        return _cachedCustomRom;
      }

      // Check for Flyme (Meizu)
      if (manufacturer.contains('meizu') || brand.contains('meizu')) {
        _cachedCustomRom = 'Flyme';
        await _logger.log(
          level: 'debug',
          subsystem: 'device_info',
          message: 'Custom ROM detected: Flyme',
        );
        return _cachedCustomRom;
      }

      // Check for One UI (Samsung)
      if (manufacturer.contains('samsung') || brand.contains('samsung')) {
        _cachedCustomRom = 'One UI';
        await _logger.log(
          level: 'debug',
          subsystem: 'device_info',
          message: 'Custom ROM detected: One UI',
        );
        return _cachedCustomRom;
      }

      // Stock Android or unknown
      _cachedCustomRom = null;
      await _logger.log(
        level: 'debug',
        subsystem: 'device_info',
        message: 'Stock Android or unknown ROM',
      );
      return null;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'device_info',
        message: 'Error getting custom ROM',
        cause: e.toString(),
      );
      return null;
    }
  }

  /// Checks if the device is from Xiaomi (including Redmi and Poco).
  Future<bool> isXiaomi() async {
    final manufacturer = await getManufacturer();
    final brand = await getBrand();
    return (manufacturer?.contains('xiaomi') ?? false) ||
        (brand?.contains('xiaomi') ?? false) ||
        (brand?.contains('redmi') ?? false) ||
        (brand?.contains('poco') ?? false);
  }

  /// Checks if the device is from Huawei (including Honor).
  Future<bool> isHuawei() async {
    final manufacturer = await getManufacturer();
    final brand = await getBrand();
    return (manufacturer?.contains('huawei') ?? false) ||
        (brand?.contains('huawei') ?? false) ||
        (brand?.contains('honor') ?? false);
  }

  /// Checks if the device is from Oppo.
  Future<bool> isOppo() async {
    final manufacturer = await getManufacturer();
    final brand = await getBrand();
    return (manufacturer?.contains('oppo') ?? false) ||
        (brand?.contains('oppo') ?? false);
  }

  /// Checks if the device is from OnePlus.
  Future<bool> isOnePlus() async {
    final manufacturer = await getManufacturer();
    final brand = await getBrand();
    return (manufacturer?.contains('oneplus') ?? false) ||
        (brand?.contains('oneplus') ?? false);
  }

  /// Checks if the device is from Samsung.
  Future<bool> isSamsung() async {
    final manufacturer = await getManufacturer();
    final brand = await getBrand();
    return (manufacturer?.contains('samsung') ?? false) ||
        (brand?.contains('samsung') ?? false);
  }

  /// Checks if the device is from Realme.
  Future<bool> isRealme() async {
    final manufacturer = await getManufacturer();
    final brand = await getBrand();
    return (manufacturer?.contains('realme') ?? false) ||
        (brand?.contains('realme') ?? false);
  }

  /// Checks if the device is from Vivo.
  Future<bool> isVivo() async {
    final manufacturer = await getManufacturer();
    final brand = await getBrand();
    return (manufacturer?.contains('vivo') ?? false) ||
        (brand?.contains('vivo') ?? false);
  }

  /// Checks if the device is from Meizu.
  Future<bool> isMeizu() async {
    final manufacturer = await getManufacturer();
    final brand = await getBrand();
    return (manufacturer?.contains('meizu') ?? false) ||
        (brand?.contains('meizu') ?? false);
  }

  /// Checks if the device is from a Chinese manufacturer.
  ///
  /// Chinese manufacturers typically have more aggressive battery optimization
  /// and background process restrictions.
  Future<bool> isChineseManufacturer() async =>
      await isXiaomi() ||
      await isHuawei() ||
      await isOppo() ||
      await isOnePlus() ||
      await isRealme() ||
      await isVivo() ||
      await isMeizu();

  /// Checks if the device needs manufacturer-specific settings.
  ///
  /// Returns true if the device has a custom ROM that requires special settings
  /// for autostart, battery optimization, or background restrictions.
  Future<bool> needsManufacturerSettings() async {
    final customRom = await getCustomRom();
    if (customRom == null) {
      // Stock Android - may still need battery optimization settings
      return true;
    }

    // All custom ROMs need manufacturer-specific settings
    return true;
  }

  /// Checks if the device needs aggressive guidance for background tasks.
  ///
  /// Returns true for devices from manufacturers known to have aggressive
  /// battery optimization and background process restrictions (Xiaomi, Huawei,
  /// Oppo, Vivo, Realme, Meizu).
  Future<bool> needsAggressiveGuidance() async => isChineseManufacturer();

  /// Gets the App Standby Bucket (Android 9.0+, API 28+).
  ///
  /// App Standby Bucket indicates how aggressively the system restricts
  /// background activity for the app:
  /// - 10: ACTIVE - App is actively used
  /// - 20: WORKING_SET - App is used frequently
  /// - 30: FREQUENT - App is used regularly
  /// - 40: RARE - App is rarely used
  /// - 50: NEVER - App is never used (restricted)
  ///
  /// Returns the bucket value (10, 20, 30, 40, or 50), or null if unavailable.
  Future<int?> getAppStandbyBucket() async {
    if (_cachedStandbyBucket != null) return _cachedStandbyBucket;

    try {
      if (!Platform.isAndroid) {
        return null;
      }

      final androidInfo = await _getAndroidInfo();
      if (androidInfo.version.sdkInt < 28) {
        // App Standby Bucket requires Android 9.0+ (API 28+)
        await _logger.log(
          level: 'debug',
          subsystem: 'device_info',
          message: 'App Standby Bucket not available on Android < 9.0',
        );
        return null;
      }

      final result = await _deviceInfoChannel.invokeMethod<int>(
        'getAppStandbyBucket',
      );

      _cachedStandbyBucket = result;
      await _logger.log(
        level: 'debug',
        subsystem: 'device_info',
        message: 'App Standby Bucket retrieved',
        extra: {'bucket': _cachedStandbyBucket},
      );
      return _cachedStandbyBucket;
    } on PlatformException catch (e) {
      await _logger.log(
        level: 'debug',
        subsystem: 'device_info',
        message:
            'Cannot get App Standby Bucket (may require special permission)',
        cause: e.toString(),
      );
      return null;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'device_info',
        message: 'Error getting App Standby Bucket',
        cause: e.toString(),
      );
      return null;
    }
  }

  /// Gets a human-readable description of the App Standby Bucket.
  ///
  /// Returns a localized description of the bucket status, or null if unavailable.
  /// [localizations] is optional - if not provided, English strings will be used.
  Future<String?> getAppStandbyBucketDescription(
      [dynamic localizations]) async {
    final bucket = await getAppStandbyBucket();
    if (bucket == null) return null;

    switch (bucket) {
      case 10:
        return localizations?.standbyBucketActiveUsed ?? 'Actively Used';
      case 20:
        return localizations?.standbyBucketFrequentlyUsed ?? 'Frequently Used';
      case 30:
        return localizations?.standbyBucketRegularlyUsed ?? 'Regularly Used';
      case 40:
        return localizations?.standbyBucketRarelyUsed ?? 'Rarely Used';
      case 50:
        return localizations?.standbyBucketNeverUsed ??
            'Never Used (Restricted)';
      default:
        return localizations?.standbyBucketUnknown(bucket) ??
            'Unknown ($bucket)';
    }
  }

  /// Gets the ROM version (e.g., "MIUI 14.0", "EMUI 12.0").
  ///
  /// This method reads system properties to determine the exact version
  /// of the custom ROM. Returns null if the version cannot be determined.
  ///
  /// Returns the ROM version string or null if unavailable.
  Future<String?> getRomVersion() async {
    if (_cachedRomVersion != null) return _cachedRomVersion;

    try {
      if (!Platform.isAndroid) {
        return null;
      }

      final result = await _deviceInfoChannel.invokeMethod<String>(
        'getRomVersion',
      );

      _cachedRomVersion = result;
      await _logger.log(
        level: 'debug',
        subsystem: 'device_info',
        message: 'ROM version retrieved',
        extra: {'rom_version': _cachedRomVersion},
      );
      return _cachedRomVersion;
    } on PlatformException catch (e) {
      await _logger.log(
        level: 'debug',
        subsystem: 'device_info',
        message: 'Cannot get ROM version (may not be available)',
        cause: e.toString(),
      );
      return null;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'device_info',
        message: 'Error getting ROM version',
        cause: e.toString(),
      );
      return null;
    }
  }

  /// Gets the firmware version (build number) of the device.
  ///
  /// For Samsung devices, this typically includes the build number like "S918BXXU3AWGJ".
  /// Returns null if the version cannot be determined.
  ///
  /// Returns the firmware version string or null if unavailable.
  Future<String?> getFirmwareVersion() async {
    if (_cachedFirmwareVersion != null) return _cachedFirmwareVersion;

    try {
      if (!Platform.isAndroid) {
        return null;
      }

      final result = await _deviceInfoChannel.invokeMethod<String>(
        'getFirmwareVersion',
      );

      _cachedFirmwareVersion = result;
      await _logger.log(
        level: 'debug',
        subsystem: 'device_info',
        message: 'Firmware version retrieved',
        extra: {'firmware_version': _cachedFirmwareVersion},
      );
      return _cachedFirmwareVersion;
    } on PlatformException catch (e) {
      await _logger.log(
        level: 'debug',
        subsystem: 'device_info',
        message: 'Cannot get firmware version (may not be available)',
        cause: e.toString(),
      );
      return null;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'device_info',
        message: 'Error getting firmware version',
        cause: e.toString(),
      );
      return null;
    }
  }

  /// Gets cached Android device info or fetches it if not cached.
  Future<AndroidDeviceInfo> _getAndroidInfo() async {
    if (_cachedAndroidInfo != null) return _cachedAndroidInfo!;

    final deviceInfo = DeviceInfoPlugin();
    _cachedAndroidInfo = await deviceInfo.androidInfo;
    return _cachedAndroidInfo!;
  }

  /// Clears cached device information.
  ///
  /// Useful for testing or when device information might have changed.
  void clearCache() {
    _cachedManufacturer = null;
    _cachedBrand = null;
    _cachedCustomRom = null;
    _cachedRomVersion = null;
    _cachedFirmwareVersion = null;
    _cachedStandbyBucket = null;
    _cachedAndroidInfo = null;
  }
}
