import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// Utility functions for handling Bluetooth operations without requiring explicit permissions.
///
/// Uses system APIs and platform channels to work with Bluetooth
/// without needing BLUETOOTH_SCAN/CONNECT permission declarations.
const MethodChannel _bluetoothChannel = MethodChannel('jabook.bluetooth');

/// Checks if Bluetooth is available on the device.
///
/// This doesn't require permissions as it only checks availability.
Future<bool> isBluetoothAvailable() async {
  try {
    final result = await _bluetoothChannel.invokeMethod('isBluetoothAvailable');
    return result as bool? ?? false;
  } on PlatformException catch (e) {
    if (kDebugMode) {
      print('Failed to check Bluetooth availability: $e');
    }
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
  } on PlatformException catch (e) {
    if (kDebugMode) {
      print('Failed to get paired devices: $e');
    }
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
  } on PlatformException catch (e) {
    if (kDebugMode) {
      print('Failed to connect to device: $e');
    }
    return false;
  }
}

/// Disconnects from the current Bluetooth device.
Future<bool> disconnect() async {
  try {
    final result = await _bluetoothChannel.invokeMethod('disconnect');
    return result as bool? ?? false;
  } on PlatformException catch (e) {
    if (kDebugMode) {
      print('Failed to disconnect: $e');
    }
    return false;
  }
}

/// Gets the current Bluetooth connection status.
Future<bool> isConnected() async {
  try {
    final result = await _bluetoothChannel.invokeMethod('isConnected');
    return result as bool? ?? false;
  } on PlatformException catch (e) {
    if (kDebugMode) {
      print('Failed to check connection status: $e');
    }
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
  } on PlatformException catch (e) {
    if (kDebugMode) {
      print('Failed to route audio to Bluetooth: $e');
    }
    return false;
  }
}
