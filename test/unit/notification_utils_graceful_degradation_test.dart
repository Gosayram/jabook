import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:jabook/core/utils/notification_utils.dart';

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

  group('NotificationUtils - Graceful Degradation', () {
    test('showSimpleNotification handles MissingPluginException gracefully',
        () async {
      // Mock the channel to throw MissingPluginException
      const channel = MethodChannel('jabook.notifications');

      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
        throw MissingPluginException(
          'No implementation found for method ${call.method}',
        );
      });

      // Should return false, not throw
      final result = await showSimpleNotification(
        title: 'Test',
        body: 'Test body',
      );

      expect(result, isFalse);

      // Cleanup
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null);
    });

    test('showSimpleNotification handles PlatformException gracefully',
        () async {
      const channel = MethodChannel('jabook.notifications');

      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
        throw PlatformException(
          code: 'ERROR',
          message: 'Test error',
        );
      });

      final result = await showSimpleNotification(
        title: 'Test',
        body: 'Test body',
      );

      expect(result, isFalse);

      // Cleanup
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null);
    });

    test('showMediaNotification handles MissingPluginException gracefully',
        () async {
      const channel = MethodChannel('jabook.notifications');

      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
        throw MissingPluginException(
          'No implementation found for method ${call.method}',
        );
      });

      final result = await showMediaNotification(
        title: 'Test',
        artist: 'Artist',
        album: 'Album',
      );

      expect(result, isFalse);

      // Cleanup
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null);
    });

    test('cancelNotification handles MissingPluginException gracefully',
        () async {
      const channel = MethodChannel('jabook.notifications');

      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
        throw MissingPluginException(
          'No implementation found for method ${call.method}',
        );
      });

      final result = await cancelNotification(1);

      expect(result, isFalse);

      // Cleanup
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null);
    });

    test('cancelAllNotifications handles MissingPluginException gracefully',
        () async {
      const channel = MethodChannel('jabook.notifications');

      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
        throw MissingPluginException(
          'No implementation found for method ${call.method}',
        );
      });

      final result = await cancelAllNotifications();

      expect(result, isFalse);

      // Cleanup
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null);
    });
  });
}
