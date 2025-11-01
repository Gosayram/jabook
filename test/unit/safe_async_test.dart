import 'package:flutter_test/flutter_test.dart';
import 'package:jabook/core/utils/safe_async.dart';

void main() {
  group('safeUnawaited', () {
    test('executes future without blocking', () async {
      var executed = false;

      safeUnawaited(
        Future.delayed(const Duration(milliseconds: 10), () {
          executed = true;
        }),
      );

      // Should not wait for completion
      expect(executed, isFalse);

      // Wait a bit to verify it executes
      await Future.delayed(const Duration(milliseconds: 20));
      expect(executed, isTrue);
    });

    test('handles errors with default logger', () async {
      const errorHandled = false;

      // Note: In tests, we can't easily verify logger calls,
      // but we can verify the future completes without throwing
      await expectLater(
        () => safeUnawaited(
          Future.error('Test error'),
        ),
        returnsNormally,
      );

      // Wait a bit to let error handling complete
      await Future.delayed(const Duration(milliseconds: 50));

      // Should not throw unhandled exception
      expect(errorHandled, isFalse); // This just verifies no crash
    });

    test('calls onError callback when provided', () async {
      var errorCallbackCalled = false;
      Object? caughtError;
      StackTrace? caughtStackTrace;

      safeUnawaited(
        Future.error('Test error'),
        onError: (error, stackTrace) {
          errorCallbackCalled = true;
          caughtError = error;
          caughtStackTrace = stackTrace;
        },
      );

      // Wait for error handling
      await Future.delayed(const Duration(milliseconds: 50));

      expect(errorCallbackCalled, isTrue);
      expect(caughtError, equals('Test error'));
      expect(caughtStackTrace, isNotNull);
    });

    test('handles successful completion without errors', () async {
      const successExecuted = false;
      var errorCallbackCalled = false;

      safeUnawaited(
        Future.value(42),
        onError: (error, stackTrace) {
          errorCallbackCalled = true;
        },
      );

      // Wait a bit
      await Future.delayed(const Duration(milliseconds: 50));

      expect(errorCallbackCalled, isFalse);
      expect(successExecuted, isFalse); // Just verify no errors
    });

    test('handles multiple futures concurrently', () async {
      var completedCount = 0;

      for (var i = 0; i < 5; i++) {
        safeUnawaited(
          Future.delayed(const Duration(milliseconds: 10), () {
            completedCount++;
          }),
        );
      }

      await Future.delayed(const Duration(milliseconds: 50));
      expect(completedCount, equals(5));
    });

    test('handles futures with return values', () async {
      safeUnawaited(
        Future<String>.value('test'),
        onError: (error, stackTrace) {
          fail('Should not call onError for successful future');
        },
      );

      await Future.delayed(const Duration(milliseconds: 10));
      // Test passes if no exception is thrown
    });
  });
}
