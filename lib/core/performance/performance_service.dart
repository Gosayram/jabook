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
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Class of device performance.
enum PerformanceClass {
  /// Low performance device (< 3GB RAM).
  low,

  /// Standard performance device.
  medium,

  /// High performance device (> 6GB RAM).
  high
}

/// Provider for [PerformanceService].
final performanceServiceProvider =
    Provider<PerformanceService>((ref) => PerformanceService());

/// Provider for the current [PerformanceClass].
final performanceClassProvider = FutureProvider<PerformanceClass>((ref) async {
  final service = ref.watch(performanceServiceProvider);
  return service.getPerformanceClass();
});

/// Service to detect device performance.
class PerformanceService {
  final DeviceInfoPlugin _deviceInfo = DeviceInfoPlugin();

  PerformanceClass? _cachedClass;

  /// Detects the performance class of the device.
  Future<PerformanceClass> getPerformanceClass() async {
    if (_cachedClass != null) return _cachedClass!;

    try {
      if (Platform.isAndroid) {
        final androidInfo = await _deviceInfo.androidInfo;

        // isLowRamDevice is available in AndroidDeviceInfo
        if (androidInfo.isLowRamDevice) {
          _cachedClass = PerformanceClass.low;
          return PerformanceClass.low;
        }

        // Default to medium if not low RAM
        // (totalMemory property might strictly be available only on recent plugin versions or SDKs)
        _cachedClass = PerformanceClass.medium;
        return PerformanceClass.medium;
      } else if (Platform.isIOS) {
        // iOS generally handles animations well, defaulting to medium/high
        _cachedClass = PerformanceClass.medium;
        return PerformanceClass.medium;
      }
    } on Object catch (e) {
      // Fallback to medium if detection fails
      debugPrint('Performance detection failed: $e');
      _cachedClass = PerformanceClass.medium;
    }

    _cachedClass = PerformanceClass.medium;
    return PerformanceClass.medium;
  }
}
