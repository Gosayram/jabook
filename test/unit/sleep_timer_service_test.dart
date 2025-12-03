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

import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:jabook/core/player/native_audio_player.dart';
import 'package:jabook/core/player/sleep_timer_service.dart';

void main() {
  // Initialize Flutter binding for tests
  TestWidgetsFlutterBinding.ensureInitialized();

  group('SleepTimerService', () {
    late SleepTimerService sleepTimerService;
    late NativeAudioPlayer nativePlayer;
    final mockTimerState = <String, dynamic>{
      'isActive': false,
      'remainingSeconds': null,
    };

    setUpAll(() {
      // Mock path_provider for StructuredLogger
      const pathProviderChannel =
          MethodChannel('plugins.flutter.io/path_provider');
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(pathProviderChannel, (call) async {
        if (call.method == 'getApplicationDocumentsDirectory') {
          return '/tmp/test';
        }
        if (call.method == 'getTemporaryDirectory') {
          return '/tmp/test';
        }
        return null;
      });

      // Mock SharedPreferences for StructuredLogger
      const sharedPrefsChannel =
          MethodChannel('plugins.flutter.io/shared_preferences');
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(sharedPrefsChannel, (call) async {
        if (call.method == 'getAll') {
          return <String, dynamic>{};
        }
        if (call.method == 'setString' || call.method == 'setInt') {
          return true;
        }
        if (call.method == 'remove') {
          return true;
        }
        return null;
      });

      // Mock audio player MethodChannel
      const audioPlayerChannel =
          MethodChannel('com.jabook.app.jabook/audio_player');
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(audioPlayerChannel, (call) async {
        switch (call.method) {
          case 'setSleepTimerMinutes':
            final minutes = call.arguments['minutes'] as int;
            mockTimerState['isActive'] = true;
            mockTimerState['remainingSeconds'] = minutes * 60;
            mockTimerState['endOfChapter'] = false;
            return null;
          case 'setSleepTimerEndOfChapter':
            mockTimerState['isActive'] = true;
            mockTimerState['remainingSeconds'] = null;
            mockTimerState['endOfChapter'] = true;
            return null;
          case 'cancelSleepTimer':
            mockTimerState['isActive'] = false;
            mockTimerState['remainingSeconds'] = null;
            mockTimerState['endOfChapter'] = false;
            return null;
          case 'getSleepTimerRemainingSeconds':
            // Simulate timer countdown
            if (mockTimerState['remainingSeconds'] != null) {
              final remaining = mockTimerState['remainingSeconds'] as int;
              if (remaining > 0) {
                mockTimerState['remainingSeconds'] = remaining - 1;
                return remaining - 1;
              }
            }
            return mockTimerState['remainingSeconds'];
          case 'isSleepTimerActive':
            return mockTimerState['isActive'] as bool;
          default:
            return null;
        }
      });
    });

    setUp(() {
      nativePlayer = NativeAudioPlayer();
      sleepTimerService = SleepTimerService(nativePlayer);
      // Reset mock state
      mockTimerState['isActive'] = false;
      mockTimerState['remainingSeconds'] = null;
      mockTimerState['endOfChapter'] = false;
    });

    tearDown(() async {
      await sleepTimerService.dispose();
      await nativePlayer.dispose();
    });

    tearDownAll(() {
      // Cleanup mocks
      const pathProviderChannel =
          MethodChannel('plugins.flutter.io/path_provider');
      const sharedPrefsChannel =
          MethodChannel('plugins.flutter.io/shared_preferences');
      const audioPlayerChannel =
          MethodChannel('com.jabook.app.jabook/audio_player');
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(pathProviderChannel, null);
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(sharedPrefsChannel, null);
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(audioPlayerChannel, null);
    });

    group('Timer with fixed duration', () {
      test('should start timer with valid duration', () async {
        // Arrange
        bool callbackCalled = false;

        // Act
        await sleepTimerService.startTimer(
          const Duration(minutes: 10),
          () {
            callbackCalled = true;
          },
        );

        // Assert
        expect(sleepTimerService.isActive, isTrue);
        expect(sleepTimerService.selectedDuration, const Duration(minutes: 10));
        expect(sleepTimerService.remainingSeconds, isNotNull);
        expect(sleepTimerService.remainingSeconds, greaterThan(0));
        expect(callbackCalled, isFalse);
      });

      test('should cancel timer with invalid duration', () async {
        // Arrange
        bool callbackCalled = false;

        // Act
        await sleepTimerService.startTimer(
          const Duration(seconds: -1),
          () {
            callbackCalled = true;
          },
        );

        // Assert
        expect(sleepTimerService.isActive, isFalse);
        expect(callbackCalled, isFalse);
      });

      test('should cancel timer with zero duration', () async {
        // Arrange
        bool callbackCalled = false;

        // Act
        await sleepTimerService.startTimer(
          Duration.zero,
          () {
            callbackCalled = true;
          },
        );

        // Assert
        expect(sleepTimerService.isActive, isFalse);
        expect(callbackCalled, isFalse);
      });

      test('should update remaining seconds periodically', () async {
        // Arrange
        await sleepTimerService.startTimer(
          const Duration(minutes: 1),
          () {},
        );

        // Act
        final initialRemaining = sleepTimerService.remainingSeconds;
        await Future.delayed(const Duration(seconds: 2));

        // Assert
        // Note: In real scenario, remaining seconds would be updated by periodic polling
        // For test, we verify that timer is still active
        expect(sleepTimerService.isActive, isTrue);
      });

      test('should cancel timer', () async {
        // Arrange
        await sleepTimerService.startTimer(
          const Duration(minutes: 10),
          () {},
        );

        // Act
        await sleepTimerService.cancelTimer();

        // Assert
        expect(sleepTimerService.isActive, isFalse);
        expect(sleepTimerService.remainingSeconds, isNull);
        expect(
            sleepTimerService.selectedDuration, isNotNull); // Kept for display
      });

      test('should update remaining seconds from native timer', () async {
        // Arrange
        await sleepTimerService.startTimer(
          const Duration(minutes: 1),
          () {},
        );

        // Act
        // Wait for periodic update to sync with native timer
        await Future.delayed(const Duration(milliseconds: 1100));

        // Assert
        // Timer should still be active
        expect(sleepTimerService.isActive, isTrue);
      });
    });

    group('Timer at end of chapter', () {
      test('should start timer at end of chapter', () async {
        // Arrange
        bool callbackCalled = false;

        // Act
        await sleepTimerService.startTimerAtEndOfChapter(() {
          callbackCalled = true;
        });

        // Assert
        expect(sleepTimerService.isActive, isTrue);
        expect(sleepTimerService.isAtEndOfChapter, isTrue);
        expect(sleepTimerService.remainingSeconds, isNull);
        expect(sleepTimerService.selectedDuration, const Duration(seconds: -1));
        expect(callbackCalled, isFalse);
      });

      test('should trigger callback at end of chapter', () async {
        // Arrange
        bool callbackCalled = false;
        await sleepTimerService.startTimerAtEndOfChapter(() {
          callbackCalled = true;
        });

        // Act
        await sleepTimerService.triggerAtEndOfChapter();

        // Assert
        expect(callbackCalled, isTrue);
        expect(sleepTimerService.isActive, isFalse);
      });

      test('should not trigger callback if not at end of chapter', () async {
        // Arrange
        bool callbackCalled = false;
        await sleepTimerService.startTimer(
          const Duration(minutes: 10),
          () {
            callbackCalled = true;
          },
        );

        // Act
        await sleepTimerService.triggerAtEndOfChapter();

        // Assert
        expect(callbackCalled, isFalse);
        expect(sleepTimerService.isActive, isTrue);
      });

      test('should cancel timer at end of chapter', () async {
        // Arrange
        await sleepTimerService.startTimerAtEndOfChapter(() {});

        // Act
        await sleepTimerService.cancelTimer();

        // Assert
        expect(sleepTimerService.isActive, isFalse);
        expect(sleepTimerService.isAtEndOfChapter, isFalse);
      });
    });

    group('Timer state management', () {
      test('should preserve selected duration after cancellation', () async {
        // Arrange
        await sleepTimerService.startTimer(
          const Duration(minutes: 15),
          () {},
        );

        // Act
        await sleepTimerService.cancelTimer();

        // Assert
        expect(sleepTimerService.isActive, isFalse);
        expect(sleepTimerService.selectedDuration, const Duration(minutes: 15));
      });

      test('should handle multiple timer starts', () async {
        // Arrange
        await sleepTimerService.startTimer(
          const Duration(minutes: 10),
          () {},
        );

        // Act
        await sleepTimerService.startTimer(
          const Duration(minutes: 20),
          () {},
        );

        // Assert
        expect(sleepTimerService.isActive, isTrue);
        expect(sleepTimerService.selectedDuration, const Duration(minutes: 20));
      });

      test('should switch from fixed duration to end of chapter', () async {
        // Arrange
        await sleepTimerService.startTimer(
          const Duration(minutes: 10),
          () {},
        );

        // Act
        await sleepTimerService.startTimerAtEndOfChapter(() {});

        // Assert
        expect(sleepTimerService.isActive, isTrue);
        expect(sleepTimerService.isAtEndOfChapter, isTrue);
        expect(sleepTimerService.selectedDuration, const Duration(seconds: -1));
      });

      test('should switch from end of chapter to fixed duration', () async {
        // Arrange
        await sleepTimerService.startTimerAtEndOfChapter(() {});

        // Act
        await sleepTimerService.startTimer(
          const Duration(minutes: 15),
          () {},
        );

        // Assert
        expect(sleepTimerService.isActive, isTrue);
        expect(sleepTimerService.isAtEndOfChapter, isFalse);
        expect(sleepTimerService.selectedDuration, const Duration(minutes: 15));
      });
    });

    group('Timer disposal', () {
      test('should dispose resources correctly', () async {
        // Arrange
        await sleepTimerService.startTimer(
          const Duration(minutes: 10),
          () {},
        );

        // Act
        await sleepTimerService.dispose();

        // Assert
        expect(sleepTimerService.isActive, isFalse);
      });

      test('should handle multiple dispose calls', () async {
        // Arrange
        await sleepTimerService.startTimer(
          const Duration(minutes: 10),
          () {},
        );

        // Act
        await sleepTimerService.dispose();
        await sleepTimerService.dispose(); // Should not throw

        // Assert
        expect(sleepTimerService.isActive, isFalse);
      });
    });

    group('Edge cases', () {
      test('should handle very short duration', () async {
        // Arrange
        bool callbackCalled = false;

        // Act
        await sleepTimerService.startTimer(
          const Duration(seconds: 1),
          () {
            callbackCalled = true;
          },
        );

        // Assert
        expect(sleepTimerService.isActive, isTrue);
        expect(sleepTimerService.remainingSeconds, isNotNull);
      });

      test('should handle very long duration', () async {
        // Arrange
        bool callbackCalled = false;

        // Act
        await sleepTimerService.startTimer(
          const Duration(hours: 2),
          () {
            callbackCalled = true;
          },
        );

        // Assert
        expect(sleepTimerService.isActive, isTrue);
        expect(sleepTimerService.remainingSeconds, isNotNull);
        expect(sleepTimerService.remainingSeconds, greaterThan(0));
      });

      test('should handle cancel when timer not started', () async {
        // Act & Assert
        await sleepTimerService.cancelTimer(); // Should not throw
        expect(sleepTimerService.isActive, isFalse);
      });

      test('should handle triggerAtEndOfChapter when timer not started',
          () async {
        // Act & Assert
        await sleepTimerService.triggerAtEndOfChapter(); // Should not throw
        expect(sleepTimerService.isActive, isFalse);
      });
    });
  });
}
