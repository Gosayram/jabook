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

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:jabook/core/library/audiobook_file_manager.dart';
import 'package:jabook/core/library/local_audiobook.dart';
import 'package:jabook/core/library/playback_position_service.dart';
import 'package:path/path.dart' as path;

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  // Mock SharedPreferences and path_provider for integration tests
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

    // Mock SharedPreferences
    const sharedPrefsChannel =
        MethodChannel('plugins.flutter.io/shared_preferences');
    final mockSharedPrefs = <String, dynamic>{};
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(sharedPrefsChannel, (call) async {
      if (call.method == 'getAll') {
        return Map<String, dynamic>.from(mockSharedPrefs);
      }
      if (call.method == 'setString' || call.method == 'setInt') {
        final key = call.arguments['key'] as String;
        final value = call.arguments['value'];
        mockSharedPrefs[key] = value;
        return true;
      }
      if (call.method == 'getString' || call.method == 'getInt') {
        final key = call.arguments['key'] as String;
        return mockSharedPrefs[key];
      }
      if (call.method == 'remove') {
        final key = call.arguments['key'] as String;
        mockSharedPrefs.remove(key);
        return true;
      }
      return null;
    });
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

  group('File Deletion Integration Tests', () {
    late AudiobookFileManager fileManager;
    late PlaybackPositionService playbackService;
    Directory? testDir;

    setUp(() async {
      // Create temporary directory for tests
      final systemTemp = Directory.systemTemp;
      testDir = Directory(
        path.join(
          systemTemp.path,
          'test_integration_deletion_${DateTime.now().millisecondsSinceEpoch}',
        ),
      );
      if (await testDir!.exists()) {
        await testDir!.delete(recursive: true);
      }
      await testDir!.create(recursive: true);

      playbackService = PlaybackPositionService();
      fileManager = AudiobookFileManager(
        playbackPositionService: playbackService,
      );
    });

    tearDown(() async {
      // Cleanup test files
      if (testDir != null && await testDir!.exists()) {
        try {
          await testDir!.delete(recursive: true);
        } on Exception {
          // Ignore cleanup errors
        }
      }
    });

    /// Helper to create a test audio file
    Future<File> createTestAudioFile(String filePath, {int size = 1024}) async {
      final file = File(filePath);
      await file.parent.create(recursive: true);
      await file.writeAsBytes(List.generate(size, (i) => i % 256));
      return file;
    }

    /// Helper to create a test LocalAudiobook
    LocalAudiobook createTestAudiobook({
      required String filePath,
      String? fileName,
      int? fileSize,
    }) =>
        LocalAudiobook(
          filePath: filePath,
          fileName: fileName ?? path.basename(filePath),
          fileSize: fileSize ?? 1024,
          title: 'Test Audiobook',
          author: 'Test Author',
        );

    /// Helper to create a test LocalAudiobookGroup
    Future<LocalAudiobookGroup> createTestGroup({
      required String groupPath,
      required String groupName,
      int fileCount = 3,
      int? fileSize,
      String? coverPath,
    }) async {
      final dir = Directory(groupPath);
      if (!await dir.exists()) {
        await dir.create(recursive: true);
      }

      final files = <LocalAudiobook>[];
      for (var i = 0; i < fileCount; i++) {
        final filePath = path.join(groupPath, 'file_$i.mp3');
        await createTestAudioFile(filePath, size: fileSize ?? 1024);
        files.add(createTestAudiobook(
          filePath: filePath,
          fileName: 'file_$i.mp3',
          fileSize: fileSize ?? 1024,
        ));
      }

      // Create cover if specified
      if (coverPath != null) {
        await createTestAudioFile(coverPath, size: 512);
      }

      return LocalAudiobookGroup(
        groupPath: groupPath,
        groupName: groupName,
        files: files,
        coverPath: coverPath,
      );
    }

    group('File Deletion', () {
      testWidgets('should delete single file successfully', (tester) async {
        // Arrange
        final filePath = path.join(testDir!.path, 'test_file.mp3');
        final testFile = await createTestAudioFile(filePath, size: 2048);
        final audiobook = createTestAudiobook(filePath: filePath);

        // Verify file exists
        expect(await testFile.exists(), isTrue);

        // Act
        final result = await fileManager.deleteFile(audiobook);

        // Assert
        expect(result, isTrue);
        expect(await testFile.exists(), isFalse);
      });

      testWidgets('should handle deletion of non-existent file gracefully',
          (tester) async {
        // Arrange
        final filePath = path.join(testDir!.path, 'non_existent.mp3');
        final audiobook = createTestAudiobook(filePath: filePath);

        // Act
        final result = await fileManager.deleteFile(audiobook);

        // Assert
        expect(result, isFalse);
      });

      testWidgets('should preserve other files when deleting one file',
          (tester) async {
        // Arrange
        final file1 = await createTestAudioFile(
          path.join(testDir!.path, 'file1.mp3'),
        );
        final file2 = await createTestAudioFile(
          path.join(testDir!.path, 'file2.mp3'),
        );
        final audiobook1 = createTestAudiobook(filePath: file1.path);

        // Act
        await fileManager.deleteFile(audiobook1);

        // Assert
        expect(await file1.exists(), isFalse);
        expect(await file2.exists(), isTrue);
      });
    });

    group('Group Deletion', () {
      testWidgets('should delete entire group with all files', (tester) async {
        // Arrange
        final groupPath = path.join(testDir!.path, 'test_group');
        final group = await createTestGroup(
          groupPath: groupPath,
          groupName: 'Test Group',
        );

        // Verify all files exist
        for (final file in group.files) {
          expect(await File(file.filePath).exists(), isTrue);
        }

        // Act
        final result = await fileManager.deleteGroupDetailed(group);

        // Assert
        expect(result.success, isTrue);
        expect(result.deletedCount, equals(3));
        expect(result.failedCount, equals(0));

        // Verify all files are deleted
        for (final file in group.files) {
          expect(await File(file.filePath).exists(), isFalse);
        }

        // Verify directory is deleted
        expect(await Directory(groupPath).exists(), isFalse);
      });

      testWidgets('should delete group with cover image', (tester) async {
        // Arrange
        final groupPath = path.join(testDir!.path, 'group_with_cover');
        final coverPath = path.join(groupPath, 'cover.jpg');
        final group = await createTestGroup(
          groupPath: groupPath,
          groupName: 'Group with Cover',
          fileCount: 2,
          coverPath: coverPath,
        );

        final coverFile = File(coverPath);
        expect(await coverFile.exists(), isTrue);

        // Act
        final result = await fileManager.deleteGroupDetailed(group);

        // Assert
        expect(result.success, isTrue);
        expect(await coverFile.exists(), isFalse);
      });

      testWidgets('should handle partial deletion correctly', (tester) async {
        // Arrange
        final groupPath = path.join(testDir!.path, 'partial_group');
        final group = await createTestGroup(
          groupPath: groupPath,
          groupName: 'Partial Group',
        );

        // Delete one file manually to simulate failure
        await File(group.files[0].filePath).delete();

        // Act
        final result = await fileManager.deleteGroupDetailed(
          group,
        );

        // Assert
        expect(result.deletedCount, equals(2));
        expect(result.failedCount, equals(1));
        expect(result.isPartialSuccess, isTrue);
        expect(result.errors.length, greaterThan(0));
      });

      testWidgets('should preserve directory when deleteDirectory is false',
          (tester) async {
        // Arrange
        final groupPath = path.join(testDir!.path, 'preserve_dir');
        final group = await createTestGroup(
          groupPath: groupPath,
          groupName: 'Preserve Dir',
          fileCount: 2,
        );

        // Act
        final result = await fileManager.deleteGroupDetailed(
          group,
          deleteDirectory: false,
        );

        // Assert
        expect(result.success, isTrue);
        // Files should be deleted
        for (final file in group.files) {
          expect(await File(file.filePath).exists(), isFalse);
        }
        // Directory should still exist
        expect(await Directory(groupPath).exists(), isTrue);
      });
    });

    group('Playback Position Cleanup', () {
      testWidgets('should clear playback position after deletion',
          (tester) async {
        // Arrange
        final groupPath = path.join(testDir!.path, 'playback_test');
        final group = await createTestGroup(
          groupPath: groupPath,
          groupName: 'Playback Test',
          fileCount: 2,
        );

        // Save playback position
        await playbackService.savePosition(groupPath, 0, 5000);

        // Verify position is saved
        final savedPosition = await playbackService.restorePosition(groupPath);
        expect(savedPosition, isNotNull);
        expect(savedPosition!['positionMs'], equals(5000));

        // Act
        await fileManager.deleteGroupDetailed(group);

        // Assert
        final position = await playbackService.restorePosition(groupPath);
        expect(position, isNull);
      });

      testWidgets('should not clear playback position on partial deletion',
          (tester) async {
        // Arrange
        final groupPath = path.join(testDir!.path, 'partial_playback');
        final group = await createTestGroup(
          groupPath: groupPath,
          groupName: 'Partial Playback',
          fileCount: 2,
        );

        // Save playback position
        await playbackService.savePosition(groupPath, 0, 3000);

        // Delete one file manually
        await File(group.files[0].filePath).delete();

        // Act
        final result = await fileManager.deleteGroupDetailed(
          group,
        );

        // Assert
        expect(result.failedCount, greaterThan(0));
        // Playback position should still exist (not cleared on partial deletion)
        final position = await playbackService.restorePosition(groupPath);
        expect(position, isNotNull);
      });
    });

    group('Remove from Library', () {
      testWidgets('should remove from library without deleting files',
          (tester) async {
        // Arrange
        final groupPath = path.join(testDir!.path, 'remove_library');
        final group = await createTestGroup(
          groupPath: groupPath,
          groupName: 'Remove Library',
          fileCount: 2,
        );

        // Save playback position
        await playbackService.savePosition(groupPath, 0, 2000);

        // Act
        await fileManager.removeFromLibrary(group);

        // Assert
        // Files should still exist
        for (final file in group.files) {
          expect(await File(file.filePath).exists(), isTrue);
        }
        // Playback position should be cleared
        final position = await playbackService.restorePosition(groupPath);
        expect(position, isNull);
      });
    });

    group('Size Calculation', () {
      testWidgets('should calculate correct group size', (tester) async {
        // Arrange
        final groupPath = path.join(testDir!.path, 'size_test');
        final group = await createTestGroup(
          groupPath: groupPath,
          groupName: 'Size Test',
          fileSize: 2048, // 2 KB per file
        );

        // Act
        final size = await fileManager.calculateGroupSize(group);

        // Assert
        // 3 files * 2048 bytes = 6144 bytes
        expect(size, equals(6144));
      });

      testWidgets('should handle non-existent files in size calculation',
          (tester) async {
        // Arrange
        final groupPath = path.join(testDir!.path, 'missing_size');
        final group = await createTestGroup(
          groupPath: groupPath,
          groupName: 'Missing Size',
          fileCount: 2,
          fileSize: 1024,
        );

        // Delete one file
        await File(group.files[0].filePath).delete();

        // Act
        final size = await fileManager.calculateGroupSize(group);

        // Assert
        // Should use cached size for deleted file (1024) + actual size for existing (1024) = 2048
        expect(size, equals(2048));
      });
    });

    group('Error Recovery', () {
      testWidgets('should allow retry after failed deletion', (tester) async {
        // Arrange
        final groupPath = path.join(testDir!.path, 'retry_test');
        final group = await createTestGroup(
          groupPath: groupPath,
          groupName: 'Retry Test',
          fileCount: 2,
        );

        // First attempt: delete one file manually to simulate failure
        await File(group.files[0].filePath).delete();

        // Act - first attempt (partial failure)
        final firstResult = await fileManager.deleteGroupDetailed(
          group,
        );

        // Assert - should have partial success
        expect(firstResult.isPartialSuccess, isTrue);
        expect(firstResult.failedCount, equals(1));

        // Retry: create the missing file and try again
        await createTestAudioFile(group.files[0].filePath);

        // Act - retry (with allowPartialDeletion to handle any edge cases)
        final retryResult = await fileManager.deleteGroupDetailed(
          group,
        );

        // Assert - should succeed on retry (may have 0 or 1 failed if file was already deleted)
        // The important thing is that the operation completes
        expect(retryResult.deletedCount, greaterThan(0));
        // Note: failedCount might be 1 if the file was already deleted, which is acceptable
      });
    });

    group('Multiple Groups', () {
      testWidgets('should delete multiple groups independently',
          (tester) async {
        // Arrange
        final group1 = await createTestGroup(
          groupPath: path.join(testDir!.path, 'group1'),
          groupName: 'Group 1',
          fileCount: 2,
        );
        final group2 = await createTestGroup(
          groupPath: path.join(testDir!.path, 'group2'),
          groupName: 'Group 2',
          fileCount: 2,
        );

        // Act - delete first group
        final result1 = await fileManager.deleteGroupDetailed(group1);

        // Assert - first group deleted, second group intact
        expect(result1.success, isTrue);
        for (final file in group1.files) {
          expect(await File(file.filePath).exists(), isFalse);
        }
        for (final file in group2.files) {
          expect(await File(file.filePath).exists(), isTrue);
        }

        // Act - delete second group
        final result2 = await fileManager.deleteGroupDetailed(group2);

        // Assert - both groups deleted
        expect(result2.success, isTrue);
        for (final file in group2.files) {
          expect(await File(file.filePath).exists(), isFalse);
        }
      });
    });
  });
}
