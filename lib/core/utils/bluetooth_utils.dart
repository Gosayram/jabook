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

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';

/// Utility functions for handling Bluetooth operations without requiring explicit permissions.
///
/// Uses system APIs and platform channels to work with Bluetooth
/// without needing BLUETOOTH_SCAN/CONNECT permission declarations.
const MethodChannel _bluetoothChannel = MethodChannel('jabook.bluetooth');

/// Logger instance for structured logging.
final StructuredLogger _logger = StructuredLogger();

/// Checks if Bluetooth is available on the device.
///
/// This doesn't require permissions as it only checks availability.
Future<bool> isBluetoothAvailable() async {
  try {
    final result = await _bluetoothChannel.invokeMethod('isBluetoothAvailable');
    return result as bool? ?? false;
  } on MissingPluginException {
    // Platform channel not implemented - graceful degradation
    await _logger.log(
      level: 'warning',
      subsystem: 'bluetooth',
      message: 'Bluetooth channel not available',
    );
    if (kDebugMode) {
      print('Bluetooth channel not implemented');
    }
    return false;
  } on PlatformException catch (e) {
    await _logger.log(
      level: 'warning',
      subsystem: 'bluetooth',
      message: 'Failed to check Bluetooth availability',
      extra: {'error': e.toString(), 'code': e.code},
    );
    if (kDebugMode) {
      print('Failed to check Bluetooth availability: $e');
    }
    return false;
  } on Exception catch (e) {
    await _logger.log(
      level: 'warning',
      subsystem: 'bluetooth',
      message: 'Unexpected error checking Bluetooth availability',
      extra: {'error': e.toString()},
    );
    return false;
  }
}

/// Gets the list of paired Bluetooth devices.
///
/// This uses system APIs that don't require explicit permissions
/// as it only accesses already paired devices.
Future<List<Map<String, dynamic>>> getPairedDevices() async {
  try {
    final result = await _bluetoothChannel.invokeMethod('getPairedDevices');
    return List<Map<String, dynamic>>.from(result ?? []);
  } on MissingPluginException {
    await _logger.log(
      level: 'warning',
      subsystem: 'bluetooth',
      message: 'Bluetooth channel not available for getting paired devices',
    );
    return [];
  } on PlatformException catch (e) {
    await _logger.log(
      level: 'warning',
      subsystem: 'bluetooth',
      message: 'Failed to get paired devices',
      extra: {'error': e.toString(), 'code': e.code},
    );
    if (kDebugMode) {
      print('Failed to get paired devices: $e');
    }
    return [];
  } on Exception catch (e) {
    await _logger.log(
      level: 'warning',
      subsystem: 'bluetooth',
      message: 'Unexpected error getting paired devices',
      extra: {'error': e.toString()},
    );
    return [];
  }
}

/// Connects to a paired Bluetooth device.
///
/// This uses system APIs for connecting to already paired devices
/// which typically doesn't require additional permissions.
Future<bool> connectToDevice(String deviceAddress) async {
  try {
    final result = await _bluetoothChannel.invokeMethod('connectToDevice', {
      'deviceAddress': deviceAddress,
    });
    return result as bool? ?? false;
  } on MissingPluginException {
    await _logger.log(
      level: 'warning',
      subsystem: 'bluetooth',
      message: 'Bluetooth channel not available for connecting',
    );
    return false;
  } on PlatformException catch (e) {
    await _logger.log(
      level: 'warning',
      subsystem: 'bluetooth',
      message: 'Failed to connect to Bluetooth device',
      extra: {
        'error': e.toString(),
        'code': e.code,
        'deviceAddress': deviceAddress
      },
    );
    if (kDebugMode) {
      print('Failed to connect to device: $e');
    }
    return false;
  } on Exception catch (e) {
    await _logger.log(
      level: 'warning',
      subsystem: 'bluetooth',
      message: 'Unexpected error connecting to Bluetooth device',
      extra: {'error': e.toString(), 'deviceAddress': deviceAddress},
    );
    return false;
  }
}

/// Disconnects from the current Bluetooth device.
Future<bool> disconnect() async {
  try {
    final result = await _bluetoothChannel.invokeMethod('disconnect');
    return result as bool? ?? false;
  } on MissingPluginException {
    await _logger.log(
      level: 'warning',
      subsystem: 'bluetooth',
      message: 'Bluetooth channel not available for disconnecting',
    );
    return false;
  } on PlatformException catch (e) {
    await _logger.log(
      level: 'warning',
      subsystem: 'bluetooth',
      message: 'Failed to disconnect from Bluetooth device',
      extra: {'error': e.toString(), 'code': e.code},
    );
    if (kDebugMode) {
      print('Failed to disconnect: $e');
    }
    return false;
  } on Exception catch (e) {
    await _logger.log(
      level: 'warning',
      subsystem: 'bluetooth',
      message: 'Unexpected error disconnecting from Bluetooth device',
      extra: {'error': e.toString()},
    );
    return false;
  }
}

/// Gets the current Bluetooth connection status.
Future<bool> isConnected() async {
  try {
    final result = await _bluetoothChannel.invokeMethod('isConnected');
    return result as bool? ?? false;
  } on MissingPluginException {
    await _logger.log(
      level: 'debug',
      subsystem: 'bluetooth',
      message: 'Bluetooth channel not available for checking connection status',
    );
    return false;
  } on PlatformException catch (e) {
    await _logger.log(
      level: 'debug',
      subsystem: 'bluetooth',
      message: 'Failed to check Bluetooth connection status',
      extra: {'error': e.toString(), 'code': e.code},
    );
    if (kDebugMode) {
      print('Failed to check connection status: $e');
    }
    return false;
  } on Exception catch (e) {
    await _logger.log(
      level: 'debug',
      subsystem: 'bluetooth',
      message: 'Unexpected error checking Bluetooth connection status',
      extra: {'error': e.toString()},
    );
    return false;
  }
}

/// Sends audio to the connected Bluetooth device.
///
/// This uses the system's audio routing which doesn't require
/// explicit Bluetooth permissions.
Future<bool> routeAudioToBluetooth() async {
  try {
    final result =
        await _bluetoothChannel.invokeMethod('routeAudioToBluetooth');
    return result as bool? ?? false;
  } on MissingPluginException {
    await _logger.log(
      level: 'warning',
      subsystem: 'bluetooth',
      message: 'Bluetooth channel not available for routing audio',
    );
    return false;
  } on PlatformException catch (e) {
    await _logger.log(
      level: 'warning',
      subsystem: 'bluetooth',
      message: 'Failed to route audio to Bluetooth device',
      extra: {'error': e.toString(), 'code': e.code},
    );
    if (kDebugMode) {
      print('Failed to route audio to Bluetooth: $e');
    }
    return false;
  } on Exception catch (e) {
    await _logger.log(
      level: 'warning',
      subsystem: 'bluetooth',
      message: 'Unexpected error routing audio to Bluetooth device',
      extra: {'error': e.toString()},
    );
    return false;
  }
}
