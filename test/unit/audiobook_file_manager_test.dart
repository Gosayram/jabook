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
import 'package:jabook/core/library/audiobook_file_manager.dart';
import 'package:jabook/core/library/local_audiobook.dart';
import 'package:jabook/core/library/playback_position_service.dart';
import 'package:path/path.dart' as path;

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  // Mock SharedPreferences and path_provider for tests
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

  group('AudiobookFileManager Tests', () {
    late AudiobookFileManager fileManager;
    Directory? testDir;
    late PlaybackPositionService playbackService;
    final mockSharedPrefs = <String, dynamic>{};

    setUp(() async {
      // Create temporary directory for tests using system temp
      final systemTemp = Directory.systemTemp;
      testDir = Directory(
        path.join(systemTemp.path,
            'test_audiobook_manager_${DateTime.now().millisecondsSinceEpoch}'),
      );
      if (await testDir!.exists()) {
        await testDir!.delete(recursive: true);
      }
      await testDir!.create(recursive: true);

      // Setup mock SharedPreferences with in-memory storage
      const channel = MethodChannel('plugins.flutter.io/shared_preferences');
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
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

      // Clear mock storage before each test
      mockSharedPrefs.clear();

      // Create file manager with real playback service
      playbackService = PlaybackPositionService();
      fileManager = AudiobookFileManager(
        playbackPositionService: playbackService,
      );
    });

    tearDown(() async {
      // Cleanup test files
      if (testDir != null && await testDir!.exists()) {
        await testDir!.delete(recursive: true);
      }
    });

    /// Helper to create a test audio file
    Future<File> createTestAudioFile(String filePath) async {
      final file = File(filePath);
      // Ensure parent directory exists
      await file.parent.create(recursive: true);
      await file.writeAsBytes(List.generate(1024, (i) => i % 256));
      return file;
    }

    /// Helper to create a test LocalAudiobook
    LocalAudiobook createTestAudiobook({
      required String filePath,
      String? fileName,
      int? fileSize,
    }) {
      final actualFileName = fileName ?? path.basename(filePath);
      final actualFileSize = fileSize ?? 1024;
      return LocalAudiobook(
        filePath: filePath,
        fileName: actualFileName,
        fileSize: actualFileSize,
        title: 'Test Audiobook',
        author: 'Test Author',
      );
    }

    /// Helper to create a test LocalAudiobookGroup
    Future<LocalAudiobookGroup> createTestGroup({
      required String groupPath,
      int fileCount = 3,
      String? coverPath,
    }) async {
      final dir = Directory(groupPath);
      if (!await dir.exists()) {
        await dir.create(recursive: true);
      }

      final files = <LocalAudiobook>[];
      for (var i = 0; i < fileCount; i++) {
        final filePath = path.join(groupPath, 'file_$i.mp3');
        await createTestAudioFile(filePath);
        files.add(createTestAudiobook(
          filePath: filePath,
          fileName: 'file_$i.mp3',
        ));
      }

      // Create cover if specified
      if (coverPath != null) {
        await createTestAudioFile(coverPath);
      }

      return LocalAudiobookGroup(
        groupPath: groupPath,
        groupName: 'Test Group',
        files: files,
        coverPath: coverPath,
      );
    }

    group('deleteFile', () {
      test('should delete file successfully', () async {
        // Arrange
        final filePath = path.join(testDir!.path, 'test.mp3');
        await createTestAudioFile(filePath);
        final audiobook = createTestAudiobook(filePath: filePath);

        // Act
        final result = await fileManager.deleteFile(audiobook);

        // Assert
        expect(result, isTrue);
        expect(await File(filePath).exists(), isFalse);
      });

      test('should return false for non-existent file', () async {
        // Arrange
        final filePath = path.join(testDir!.path, 'non_existent.mp3');
        final audiobook = createTestAudiobook(filePath: filePath);

        // Act
        final result = await fileManager.deleteFile(audiobook);

        // Assert
        expect(result, isFalse);
      });

      // Note: Test for FileInUseException requires mocking NativeAudioPlayer
      // This would require more complex setup with dependency injection
      // Skipping for now as it's not critical for basic functionality

      test('should handle file deletion errors gracefully', () async {
        // Arrange
        final filePath = path.join(testDir!.path, 'error.mp3');
        await createTestAudioFile(filePath);
        final audiobook = createTestAudiobook(filePath: filePath);

        // Delete file before attempting to delete again
        await File(filePath).delete();

        // Act
        final result = await fileManager.deleteFile(audiobook);

        // Assert
        expect(result, isFalse);
      });
    });

    group('deleteGroupDetailed', () {
      test('should delete entire group successfully', () async {
        // Arrange
        final groupPath = path.join(testDir!.path, 'test_group');
        final group = await createTestGroup(groupPath: groupPath);

        // Act
        final result = await fileManager.deleteGroupDetailed(group);

        // Assert
        expect(result.success, isTrue);
        expect(result.deletedCount, equals(3));
        expect(result.failedCount, equals(0));
        expect(result.totalCount, equals(3));
        expect(await Directory(groupPath).exists(), isFalse);
      });

      test('should delete group with cover image', () async {
        // Arrange
        final groupPath = path.join(testDir!.path, 'group_with_cover');
        final coverPath = path.join(groupPath, 'cover.jpg');
        final group = await createTestGroup(
          groupPath: groupPath,
          fileCount: 2,
          coverPath: coverPath,
        );

        // Act
        final result = await fileManager.deleteGroupDetailed(group);

        // Assert
        expect(result.success, isTrue);
        expect(result.deletedCount, equals(2));
        expect(await File(coverPath).exists(), isFalse);
      });

      test(
          'should delete group without directory when deleteDirectory is false',
          () async {
        // Arrange
        final groupPath = path.join(testDir!.path, 'group_no_dir');
        final group = await createTestGroup(groupPath: groupPath, fileCount: 2);

        // Act
        final result = await fileManager.deleteGroupDetailed(
          group,
          deleteDirectory: false,
        );

        // Assert
        expect(result.success, isTrue);
        expect(result.deletedCount, equals(2));
        // Directory should still exist (but files should be deleted)
        expect(await Directory(groupPath).exists(), isTrue);
      });

      test('should handle partial deletion when allowPartialDeletion is true',
          () async {
        // Arrange
        final groupPath = path.join(testDir!.path, 'partial_group');
        final group = await createTestGroup(groupPath: groupPath);

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
      });

      test('should not clear playback data on partial deletion', () async {
        // Arrange
        final groupPath = path.join(testDir!.path, 'partial_playback');
        final group = await createTestGroup(groupPath: groupPath, fileCount: 2);

        // Save playback position
        await playbackService.savePosition(groupPath, 0, 1000);

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

    group('deleteGroup', () {
      test('should delete group and return true on success', () async {
        // Arrange
        final groupPath = path.join(testDir!.path, 'simple_group');
        final group = await createTestGroup(groupPath: groupPath, fileCount: 2);

        // Act
        final result = await fileManager.deleteGroup(group);

        // Assert
        expect(result, isTrue);
        expect(await Directory(groupPath).exists(), isFalse);
      });

      test('should return false when deletion fails', () async {
        // Arrange
        final groupPath = path.join(testDir!.path, 'fail_group');
        final group = await createTestGroup(groupPath: groupPath, fileCount: 1);

        // Delete file before attempting deletion
        await File(group.files[0].filePath).delete();

        // Act
        final result = await fileManager.deleteGroup(group);

        // Assert
        expect(result, isFalse);
      });
    });

    group('calculateGroupSize', () {
      test('should calculate correct group size', () async {
        // Arrange
        final groupPath = path.join(testDir!.path, 'size_group');
        final group = await createTestGroup(groupPath: groupPath);

        // Act
        final size = await fileManager.calculateGroupSize(group);

        // Assert
        // Each file is 1024 bytes, so total should be 3 * 1024 = 3072
        expect(size, equals(3072));
      });

      test('should return 0 for empty group', () async {
        // Arrange
        final group = LocalAudiobookGroup(
          groupPath: path.join(testDir!.path, 'empty_group'),
          groupName: 'Empty Group',
          files: [],
        );

        // Act
        final size = await fileManager.calculateGroupSize(group);

        // Assert
        expect(size, equals(0));
      });

      test('should handle non-existent files in calculation', () async {
        // Arrange
        final groupPath = path.join(testDir!.path, 'missing_files');
        final group = await createTestGroup(groupPath: groupPath, fileCount: 2);

        // Delete one file
        await File(group.files[0].filePath).delete();

        // Act
        final size = await fileManager.calculateGroupSize(group);

        // Assert
        // Should use cached size for deleted file (1024) + actual size for existing file (1024) = 2048
        // The method uses cached fileSize when file doesn't exist
        expect(size, equals(2048));
      });
    });

    group('removeFromLibrary', () {
      test('should clear playback data without deleting files', () async {
        // Arrange
        final groupPath = path.join(testDir!.path, 'remove_library');
        final group = await createTestGroup(groupPath: groupPath, fileCount: 2);

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

    group('clearPlaybackData', () {
      test('should clear playback position for group', () async {
        // Arrange
        final groupPath = path.join(testDir!.path, 'clear_playback');
        await playbackService.savePosition(groupPath, 0, 3000);

        // Act
        await fileManager.clearPlaybackData(groupPath);

        // Assert
        final position = await playbackService.restorePosition(groupPath);
        expect(position, isNull);
      });

      test('should handle clearing non-existent playback data', () async {
        // Arrange
        final groupPath = path.join(testDir!.path, 'non_existent_playback');

        // Act & Assert - should not throw
        await fileManager.clearPlaybackData(groupPath);
      });
    });
  });
}
