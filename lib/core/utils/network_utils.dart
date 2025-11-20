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

import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:jabook/core/logging/environment_logger.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Utility class for network connectivity checks.
class NetworkUtils {
  /// Private constructor for singleton pattern.
  NetworkUtils._();

  /// Factory constructor to get the singleton instance.
  factory NetworkUtils() => _instance;

  static final NetworkUtils _instance = NetworkUtils._();

  final Connectivity _connectivity = Connectivity();

  /// Checks if the device is currently connected to Wi-Fi.
  ///
  /// Returns true if connected to Wi-Fi, false otherwise.
  /// On non-Android platforms, always returns true (no restriction).
  Future<bool> isConnectedToWifi() async {
    if (!Platform.isAndroid) {
      // On non-Android platforms, assume Wi-Fi is available
      return true;
    }

    try {
      final result = await _connectivity.checkConnectivity();
      return result.contains(ConnectivityResult.wifi);
    } on Exception catch (e) {
      EnvironmentLogger().w('Failed to check Wi-Fi connection: $e');
      // On error, assume Wi-Fi is available to avoid blocking downloads
      return true;
    }
  }

  /// Checks if Wi-Fi only downloads setting is enabled.
  ///
  /// Returns true if Wi-Fi only setting is enabled, false otherwise.
  Future<bool> isWifiOnlyEnabled() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      return prefs.getBool('wifi_only_downloads') ?? false;
    } on Exception catch (e) {
      EnvironmentLogger().w('Failed to check Wi-Fi only setting: $e');
      return false;
    }
  }

  /// Checks if download can proceed based on Wi-Fi only setting.
  ///
  /// Returns true if download can proceed, false if blocked by Wi-Fi only setting.
  Future<bool> canDownload() async {
    final wifiOnly = await isWifiOnlyEnabled();
    if (!wifiOnly) {
      return true; // No restriction
    }

    return isConnectedToWifi();
  }

  /// Gets a user-friendly description of the current connection type.
  ///
  /// Returns a string describing the connection type (e.g., "Wi-Fi", "Mobile data").
  Future<String> getConnectionTypeDescription() async {
    if (!Platform.isAndroid) {
      return 'Unknown';
    }

    try {
      final result = await _connectivity.checkConnectivity();
      if (result.contains(ConnectivityResult.wifi)) {
        return 'Wi-Fi';
      } else if (result.contains(ConnectivityResult.mobile)) {
        return 'Mobile data';
      } else if (result.contains(ConnectivityResult.ethernet)) {
        return 'Ethernet';
      } else if (result.contains(ConnectivityResult.none)) {
        return 'No connection';
      } else {
        return 'Unknown';
      }
    } on Exception catch (e) {
      EnvironmentLogger().w('Failed to get connection type: $e');
      return 'Unknown';
    }
  }
}
