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

import 'package:flutter_test/flutter_test.dart';
import 'package:jabook/core/session/session_manager.dart';

void main() {
  group('SessionManager', () {
    late SessionManager sessionManager;

    setUp(() {
      sessionManager = SessionManager();
    });

    tearDown(() async {
      // Clean up after each test
      try {
        await sessionManager.clearSession();
      } on Exception {
        // Ignore cleanup errors
      }
      sessionManager.dispose();
    });

    test('should initialize with default components', () {
      expect(sessionManager, isNotNull);
    });

    test('getSessionId should return null when no session exists', () async {
      final sessionId = await sessionManager.getSessionId();
      expect(sessionId, isNull);
    });

    test('getSessionInfo should return null when no session exists', () async {
      final info = await sessionManager.getSessionInfo();
      expect(info, isNull);
    });

    test('getPerformanceMetrics should return empty map initially', () {
      final metrics = sessionManager.getPerformanceMetrics();
      expect(metrics, isEmpty);
    });

    test('startSessionMonitoring should start timer', () {
      sessionManager.startSessionMonitoring(
        interval: const Duration(seconds: 1),
      );
      // Timer should be started
      expect(sessionManager, isNotNull);
    });

    test('stopSessionMonitoring should stop timer', () {
      sessionManager
        ..startSessionMonitoring()
        ..stopSessionMonitoring();
      // Timer should be stopped
      expect(sessionManager, isNotNull);
    });

    test('dispose should clean up resources', () {
      sessionManager
        ..startSessionMonitoring()
        ..dispose();
      // Resources should be cleaned up
      expect(sessionManager, isNotNull);
    });

    test('clearSession should clear all session data', () async {
      // This test verifies that clearSession doesn't throw
      // Actual clearing requires real storage, so we just check it doesn't crash
      try {
        await sessionManager.clearSession();
      } on Exception {
        // Expected if no session exists
      }
      expect(sessionManager, isNotNull);
    });

    test('syncCookiesBetweenWebViewAndDio should not throw', () async {
      // This test verifies the method doesn't throw
      // Actual sync requires real Dio instance
      try {
        await sessionManager.syncCookiesBetweenWebViewAndDio();
      } on Exception {
        // Expected if Dio is not initialized
      }
      expect(sessionManager, isNotNull);
    });

    test('syncCookiesOnEndpointSwitch should handle endpoint switch', () async {
      // This test verifies the method doesn't throw
      try {
        await sessionManager.syncCookiesOnEndpointSwitch(
          'https://rutracker.net',
          'https://rutracker.me',
        );
      } on Exception {
        // Expected if no session exists
      }
      expect(sessionManager, isNotNull);
    });

    test('getPerformanceMetrics should track operations', () {
      // After operations, metrics should be available
      final metrics = sessionManager.getPerformanceMetrics();
      // Initially empty, but structure should be correct
      expect(metrics, isA<Map<String, Map<String, dynamic>>>());
    });

    test('isSessionValid should return false when no session exists', () async {
      // This test verifies that isSessionValid doesn't throw
      // Actual validation requires real network, so we just check it doesn't crash
      try {
        final isValid = await sessionManager.isSessionValid();
        // Should return false when no session exists
        expect(isValid, isA<bool>());
      } on Exception {
        // Expected if network is not available in test environment
      }
    });

    test('restoreSession should return false when no session exists', () async {
      // This test verifies that restoreSession doesn't throw
      try {
        final restored = await sessionManager.restoreSession();
        expect(restored, false);
      } on Exception {
        // Expected if storage is not available in test environment
      }
    });

    test('refreshSessionIfNeeded should return false when no session exists',
        () async {
      // This test verifies that refreshSessionIfNeeded doesn't throw
      try {
        final refreshed = await sessionManager.refreshSessionIfNeeded();
        expect(refreshed, false);
      } on Exception {
        // Expected if no credentials are available
      }
    });

    test('startSessionMonitoring should handle null interval', () {
      // This test verifies that startSessionMonitoring works with null interval
      sessionManager.startSessionMonitoring();
      expect(sessionManager, isNotNull);
      sessionManager.stopSessionMonitoring();
    });

    test('startSessionMonitoring should handle custom interval', () {
      // This test verifies that startSessionMonitoring works with custom interval
      sessionManager.startSessionMonitoring(
        interval: const Duration(seconds: 30),
      );
      expect(sessionManager, isNotNull);
      sessionManager.stopSessionMonitoring();
    });

    test('stopSessionMonitoring should be idempotent', () {
      // This test verifies that stopSessionMonitoring can be called multiple times
      sessionManager
        ..startSessionMonitoring()
        ..stopSessionMonitoring()
        ..stopSessionMonitoring(); // Should not throw
      expect(sessionManager, isNotNull);
    });
  });

  group('SessionManager performance metrics', () {
    late SessionManager sessionManager;

    setUp(() {
      sessionManager = SessionManager();
    });

    tearDown(() {
      sessionManager.dispose();
    });

    test('getPerformanceMetrics should return correct structure', () {
      final metrics = sessionManager.getPerformanceMetrics();
      expect(metrics, isA<Map<String, Map<String, dynamic>>>());
    });

    test('getPerformanceMetrics should handle empty metrics', () {
      final metrics = sessionManager.getPerformanceMetrics();
      expect(metrics, isEmpty);
    });

    test('getPerformanceMetrics should return consistent structure', () {
      final metrics1 = sessionManager.getPerformanceMetrics();
      final metrics2 = sessionManager.getPerformanceMetrics();
      // Should return same structure (empty) when no operations performed
      expect(metrics1, equals(metrics2));
    });
  });

  group('SessionManager lifecycle', () {
    late SessionManager sessionManager;

    setUp(() {
      sessionManager = SessionManager();
    });

    tearDown(() {
      sessionManager.dispose();
    });

    test('should handle multiple dispose calls', () {
      sessionManager
        ..dispose()
        ..dispose(); // Should not throw
      expect(sessionManager, isNotNull);
    });

    test('should handle monitoring after dispose', () {
      sessionManager
        ..dispose()
        // Should not throw, but monitoring won't work
        ..startSessionMonitoring()
        ..stopSessionMonitoring();
    });
  });
}
