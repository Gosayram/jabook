import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:jabook/core/utils/first_launch.dart';

void main() {
  // Initialize Flutter binding for tests
  TestWidgetsFlutterBinding.ensureInitialized();

  group('FirstLaunchHelper', () {
    setUp(() {
      // Mock SharedPreferences channel
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(
        const MethodChannel('plugins.flutter.io/shared_preferences'),
        (call) async {
          if (call.method == 'getAll') {
            return <String, dynamic>{};
          }
          return null;
        },
      );
    });

    tearDown(() {
      // Cleanup
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(
        const MethodChannel('plugins.flutter.io/shared_preferences'),
        null,
      );
    });

    test('isFirstLaunch returns true by default', () async {
      // Mock returns empty, so should default to true
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(
        const MethodChannel('plugins.flutter.io/shared_preferences'),
        (call) async {
          if (call.method == 'getAll') {
            return <String, dynamic>{}; // No stored value = first launch
          }
          return null;
        },
      );

      final isFirst = await FirstLaunchHelper.isFirstLaunch();
      expect(isFirst, isTrue);
    });

    test('isFirstLaunch returns false after markAsLaunched', () async {
      final storedValues = <String, dynamic>{};
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(
        const MethodChannel('plugins.flutter.io/shared_preferences'),
        (call) async {
          if (call.method == 'setBool') {
            final args = call.arguments as Map?;
            if (args != null) {
              storedValues[args['key'] as String] = args['value'] as bool;
            }
            return true;
          }
          if (call.method == 'getAll') {
            return Map<String, dynamic>.from(storedValues);
          }
          return null;
        },
      );

      await FirstLaunchHelper.markAsLaunched();
      final isFirst = await FirstLaunchHelper.isFirstLaunch();
      expect(isFirst, isFalse);
    });

    test('markAsLaunched persists the state', () async {
      final storedValues = <String, dynamic>{};
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(
        const MethodChannel('plugins.flutter.io/shared_preferences'),
        (call) async {
          if (call.method == 'setBool') {
            final key = call.arguments as Map?;
            if (key != null) {
              storedValues[key['key'] as String] = key['value'] as bool;
            }
            return true;
          }
          if (call.method == 'getAll') {
            return Map<String, dynamic>.from(storedValues);
          }
          return null;
        },
      );

      await FirstLaunchHelper.markAsLaunched();
      final isFirst = await FirstLaunchHelper.isFirstLaunch();
      expect(isFirst, isFalse);
    });

    test('resetFirstLaunch restores first launch state', () async {
      final storedValues = <String, dynamic>{'is_first_launch': false};
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(
        const MethodChannel('plugins.flutter.io/shared_preferences'),
        (call) async {
          if (call.method == 'remove') {
            storedValues.remove('is_first_launch');
            return true;
          }
          if (call.method == 'getAll') {
            return Map<String, dynamic>.from(storedValues);
          }
          return null;
        },
      );

      await FirstLaunchHelper.resetFirstLaunch();
      final isFirst = await FirstLaunchHelper.isFirstLaunch();
      expect(isFirst, isTrue);
    });

    test('markAsLaunched can be called multiple times safely', () async {
      final storedValues = <String, dynamic>{};
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(
        const MethodChannel('plugins.flutter.io/shared_preferences'),
        (call) async {
          if (call.method == 'setBool') {
            final args = call.arguments as Map?;
            if (args != null) {
              storedValues[args['key'] as String] = args['value'] as bool;
            }
            return true;
          }
          if (call.method == 'getAll') {
            return Map<String, dynamic>.from(storedValues);
          }
          return null;
        },
      );

      await FirstLaunchHelper.markAsLaunched();
      await FirstLaunchHelper.markAsLaunched();
      await FirstLaunchHelper.markAsLaunched();

      final isFirst = await FirstLaunchHelper.isFirstLaunch();
      expect(isFirst, isFalse);
    });

    test('handles errors gracefully', () async {
      // Mock to throw exception
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(
        const MethodChannel('plugins.flutter.io/shared_preferences'),
        (call) async {
          throw Exception('Test error');
        },
      );

      // Should not throw, but return default value
      final isFirst = await FirstLaunchHelper.isFirstLaunch();
      expect(isFirst, isTrue); // Should default to true on error

      // These should complete without throwing
      await expectLater(FirstLaunchHelper.markAsLaunched(), completes);
      await expectLater(FirstLaunchHelper.resetFirstLaunch(), completes);
    });
  });
}
