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

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:jabook/core/utils/bluetooth_utils.dart';

void main() {
  // Initialize Flutter binding for tests
  TestWidgetsFlutterBinding.ensureInitialized();

  setUpAll(() async {
    // Mock path_provider to avoid file system operations in tests
    const channel = MethodChannel('plugins.flutter.io/path_provider');
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      if (call.method == 'getApplicationDocumentsDirectory') {
        return '/tmp/test';
      }
      return null;
    });
  });

  group('BluetoothUtils - Graceful Degradation', () {
    test('isBluetoothAvailable handles MissingPluginException gracefully',
        () async {
      const channel = MethodChannel('jabook.bluetooth');

      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
        throw MissingPluginException(
          'No implementation found for method ${call.method}',
        );
      });

      // Should return false, not throw
      final result = await isBluetoothAvailable();

      expect(result, isFalse);

      // Cleanup
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null);
    });

    test('getPairedDevices handles MissingPluginException gracefully',
        () async {
      const channel = MethodChannel('jabook.bluetooth');

      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
        throw MissingPluginException(
          'No implementation found for method ${call.method}',
        );
      });

      final result = await getPairedDevices();

      expect(result, isEmpty);

      // Cleanup
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null);
    });

    test('connectToDevice handles MissingPluginException gracefully', () async {
      const channel = MethodChannel('jabook.bluetooth');

      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
        throw MissingPluginException(
          'No implementation found for method ${call.method}',
        );
      });

      final result = await connectToDevice('test-address');

      expect(result, isFalse);

      // Cleanup
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null);
    });

    test('disconnect handles MissingPluginException gracefully', () async {
      const channel = MethodChannel('jabook.bluetooth');

      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
        throw MissingPluginException(
          'No implementation found for method ${call.method}',
        );
      });

      final result = await disconnect();

      expect(result, isFalse);

      // Cleanup
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null);
    });

    test('isConnected handles MissingPluginException gracefully', () async {
      const channel = MethodChannel('jabook.bluetooth');

      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
        throw MissingPluginException(
          'No implementation found for method ${call.method}',
        );
      });

      final result = await isConnected();

      expect(result, isFalse);

      // Cleanup
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null);
    });

    test('routeAudioToBluetooth handles MissingPluginException gracefully',
        () async {
      const channel = MethodChannel('jabook.bluetooth');

      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
        throw MissingPluginException(
          'No implementation found for method ${call.method}',
        );
      });

      final result = await routeAudioToBluetooth();

      expect(result, isFalse);

      // Cleanup
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null);
    });

    test('handles PlatformException gracefully', () async {
      const channel = MethodChannel('jabook.bluetooth');

      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
        throw PlatformException(
          code: 'ERROR',
          message: 'Bluetooth error',
        );
      });

      final result = await isBluetoothAvailable();

      expect(result, isFalse);

      // Cleanup
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null);
    });
  });
}
