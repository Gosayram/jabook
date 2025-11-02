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
