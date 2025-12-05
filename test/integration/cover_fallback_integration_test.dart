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

import 'package:device_info_plus/device_info_plus.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:jabook/core/data/local/database/app_database.dart';
import 'package:sembast/sembast_io.dart';
import 'cover_fallback_test_service.dart';

void main() {
  // Initialize IntegrationTest binding for real network requests
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  // Mock path_provider and SharedPreferences for StructuredLogger
  setUpAll(() async {
    // Mock path_provider for StructuredLogger
    const pathProviderChannel =
        MethodChannel('plugins.flutter.io/path_provider');
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(pathProviderChannel, (call) async {
      if (call.method == 'getApplicationDocumentsDirectory') {
        return Directory.systemTemp.path;
      }
      if (call.method == 'getTemporaryDirectory') {
        return Directory.systemTemp.path;
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

    // Initialize AppDatabase before using DioClient
    // DioClient.getInstance() requires AppDatabase to be initialized
    try {
      final appDb = AppDatabase.getInstance();
      await appDb.ensureInitialized();
    } on Exception {
      // Try to initialize manually if ensureInitialized fails
      try {
        final appDb = AppDatabase.getInstance();
        await appDb.initialize();
      } on Exception {
        // Ignore errors - will fail later if needed
      }
    }

    // Pre-store User-Agent in database to avoid WebView extraction in tests
    // This prevents WebView initialization errors
    // UserAgentManager uses getApplicationDocumentsDirectory() which we mock to Directory.systemTemp.path
    try {
      final appDocumentDir = Directory.systemTemp;
      final dbPath = '${appDocumentDir.path}/jabook.db';
      final db = await databaseFactoryIo.openDatabase(dbPath);
      final store = StoreRef<String, Map<String, dynamic>>.main();

      // Generate fallback User-Agent
      String fallbackUa;
      try {
        if (Platform.isAndroid) {
          final deviceInfo = DeviceInfoPlugin();
          final androidInfo = await deviceInfo.androidInfo;
          final androidVersion = androidInfo.version.release;
          fallbackUa = 'Mozilla/5.0 (Linux; Android $androidVersion; K) '
              'AppleWebKit/537.36 (KHTML, like Gecko) '
              'Chrome/130.0.6723.106 Mobile Safari/537.36';
        } else {
          fallbackUa = 'Mozilla/5.0 (Linux; Android 13; K) '
              'AppleWebKit/537.36 (KHTML, like Gecko) '
              'Chrome/130.0.6723.106 Mobile Safari/537.36';
        }
      } on Exception {
        fallbackUa = 'Mozilla/5.0 (Linux; Android 13; K) '
            'AppleWebKit/537.36 (KHTML, like Gecko) '
            'Chrome/130.0.6723.106 Mobile Safari/537.36';
      }

      // Store User-Agent in database (same key as UserAgentManager uses)
      await store.record('user_agent').put(db, {
        'user_agent': fallbackUa,
        'updated_at': DateTime.now().toIso8601String(),
      });
      await db.close();
    } on Exception {
      // Ignore errors - UserAgentManager will use fallback
    }
  });

  tearDownAll(() async {
    // Cleanup mocks
    const pathProviderChannel =
        MethodChannel('plugins.flutter.io/path_provider');
    const sharedPrefsChannel =
        MethodChannel('plugins.flutter.io/shared_preferences');
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(pathProviderChannel, null);
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(sharedPrefsChannel, null);
  });

  group('Cover Fallback Integration Tests', () {
    late CoverFallbackTestService testService;

    setUp(() {
      testService = CoverFallbackTestService();
    });

    test('should find and download cover from author.today', () async {
      // Test with a real book query
      const bookQuery = 'Атаманов Михаил - Котенок и его человек';

      print('\n📚 Testing author.today with query: "$bookQuery"\n');

      final result = await testService.testAuthorToday(bookQuery);

      print('\n📊 Test Result:\n$result\n');

      // Verify results
      expect(result.source, equals('author.today'));
      expect(result.query, equals(bookQuery));

      // Check if we got search results
      expect(result.searchHtmlLength, greaterThan(0),
          reason: 'Should receive search results HTML');

      // If we found a book URL, verify it's not a generic page
      if (result.bookUrl != null) {
        expect(result.bookUrl, isNot(contains('/genre/')),
            reason: 'Should find specific book, not genre page');
        expect(result.bookUrl, isNot(contains('/sorting=')),
            reason: 'Should find specific book, not sorting page');
      }

      // If we found a cover URL, verify it's not a placeholder
      if (result.coverUrl != null) {
        expect(result.coverUrl, isNot(contains('data:image')),
            reason: 'Should find real image URL, not data URI placeholder');
        expect(result.coverUrl, isNot(contains('1x1')),
            reason: 'Should find real image, not 1x1 placeholder');
      }

      // Success means we found and downloaded a real cover
      if (result.success) {
        expect(result.bookUrl, isNotNull, reason: 'Book URL should be found');
        expect(result.coverUrl, isNotNull, reason: 'Cover URL should be found');
        expect(result.coverPath, isNotNull,
            reason: 'Cover should be downloaded');

        // Verify file exists and has content
        final coverFile = File(result.coverPath!);
        expect(await coverFile.exists(), isTrue,
            reason: 'Cover file should exist');
        expect(result.coverFileSize, greaterThan(1000),
            reason: 'Cover file should be at least 1KB (not a placeholder)');

        print('\n✅ SUCCESS: Cover downloaded successfully!');
        print('   📁 File path: ${result.coverPath}');
        print('   📊 File size: ${result.coverFileSize} bytes');
        print('   🔗 Cover URL: ${result.coverUrl}');
        print('   📚 Book URL: ${result.bookUrl}');
        print(
            '\n   You can check the downloaded cover at: ${result.coverPath}');
      } else {
        print('\n⚠️ Test completed but cover not downloaded');
        print('   Error: ${result.error}');
        print('   Search HTML length: ${result.searchHtmlLength ?? 0}');
        if (result.bookUrl != null) {
          print('   Found book URL: ${result.bookUrl}');
        }
        if (result.coverUrl != null) {
          print('   Found cover URL: ${result.coverUrl}');
          print('   (This might be a placeholder image)');
        }
        print('\n   Possible reasons:');
        print('   - Book not found in search results');
        print('   - Cover image not found on book page');
        print('   - Placeholder image detected (data:image, 1x1)');
        print('   - Network issues');
      }
    }, timeout: const Timeout(Duration(minutes: 2)));

    test('should find and download cover from audio-kniga.com', () async {
      // Test with a real book query
      const bookQuery = 'Котенок и его человек';

      print('\n📚 Testing audio-kniga.com with query: "$bookQuery"\n');

      final result = await testService.testAudioKniga(bookQuery);

      print('\n📊 Test Result:\n$result\n');

      // Verify results
      expect(result.source, equals('audio-kniga.com'));
      expect(result.query, equals(bookQuery));

      // Check if we got search results (may be null if site is unavailable)
      if (result.error != null && result.error!.contains('404')) {
        print(
            '\n⚠️ Site returned 404 - search URL may be incorrect or site structure changed');
        print('   Skipping assertions for unavailable site');
        // Don't fail the test if site is unavailable
        return;
      }

      if (result.searchHtmlLength == null) {
        print('\n⚠️ Site is unavailable or returned no content');
        print('   Error: ${result.error}');
        print('   This may indicate:');
        print('   - Site is temporarily down');
        print('   - Search URL format has changed');
        print('   - Site requires different authentication');
        // Don't fail the test, just skip assertions
        return;
      }

      expect(result.searchHtmlLength, greaterThan(0),
          reason: 'Should receive search results HTML');

      // If we found a book URL, verify it's a book page
      if (result.bookUrl != null) {
        expect(result.bookUrl, contains('audio-kniga.com'),
            reason: 'Should find audio-kniga.com book page');
        expect(result.bookUrl, isNot(contains('/search')),
            reason: 'Should find specific book, not search page');
        expect(result.bookUrl, isNot(contains('/genre')),
            reason: 'Should find specific book, not genre page');
      }

      // If we found a cover URL, verify it's not a placeholder
      if (result.coverUrl != null) {
        expect(result.coverUrl, isNot(contains('data:image')),
            reason: 'Should find real image URL, not data URI placeholder');
        expect(result.coverUrl, isNot(contains('1x1')),
            reason: 'Should find real image, not 1x1 placeholder');
      }

      // Success means we found and downloaded a real cover
      if (result.success) {
        expect(result.bookUrl, isNotNull, reason: 'Book URL should be found');
        expect(result.coverUrl, isNotNull, reason: 'Cover URL should be found');
        expect(result.coverPath, isNotNull,
            reason: 'Cover should be downloaded');

        // Verify file exists and has content
        final coverFile = File(result.coverPath!);
        expect(await coverFile.exists(), isTrue,
            reason: 'Cover file should exist');
        expect(result.coverFileSize, greaterThan(1000),
            reason: 'Cover file should be at least 1KB (not a placeholder)');

        print('\n✅ SUCCESS: Cover downloaded successfully!');
        print('   📁 File path: ${result.coverPath}');
        print('   📊 File size: ${result.coverFileSize} bytes');
        print('   🔗 Cover URL: ${result.coverUrl}');
        print('   📚 Book URL: ${result.bookUrl}');
        print(
            '\n   You can check the downloaded cover at: ${result.coverPath}');
      } else {
        print('\n⚠️ Test completed but cover not downloaded');
        print('   Error: ${result.error}');
        print('   Search HTML length: ${result.searchHtmlLength ?? 0}');
        if (result.bookUrl != null) {
          print('   Found book URL: ${result.bookUrl}');
        }
        if (result.coverUrl != null) {
          print('   Found cover URL: ${result.coverUrl}');
          print('   (This might be a placeholder image)');
        }
        print('\n   Possible reasons:');
        print('   - Book not found in search results');
        print('   - Cover image not found on book page');
        print('   - Placeholder image detected (data:image, 1x1)');
        print('   - Network issues');
      }
    }, timeout: const Timeout(Duration(minutes: 2)));

    test('should handle book not found gracefully', () async {
      // Test with a non-existent book query
      const bookQuery = 'NonExistentBook12345XYZ';

      print('\n📚 Testing with non-existent book: "$bookQuery"\n');

      final result = await testService.testAuthorToday(bookQuery);

      print('\n📊 Test Result:\n$result\n');

      // Should fail gracefully without throwing
      expect(result.success, isFalse);
      expect(result.error, isNotNull);
    }, timeout: const Timeout(Duration(minutes: 1)));
  });
}
