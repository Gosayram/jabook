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
import 'package:jabook/core/domain/torrent/entities/torrent_progress.dart';
import 'package:jabook/core/domain/torrent/entities/torrent_task.dart';
import 'package:jabook/core/domain/torrent/repositories/torrent_repository.dart';
import 'package:jabook/core/domain/torrent/use_cases/cancel_torrent_use_case.dart';

/// Mock implementation of TorrentRepository for testing.
class MockTorrentRepository implements TorrentRepository {
  final Set<String> _cancelledTasks = {};
  bool _shouldFail = false;
  String? _lastCancelledTaskId;
  bool _lastDeleteFiles = false;

  @override
  Future<void> initialize() async {}

  @override
  Future<String> startDownload(
    dynamic torrentBytes,
    String savePath, {
    String? magnetUrl,
    Map<String, dynamic>? metadata,
    bool sequential = false,
    List<int>? selectedFileIndices,
  }) async =>
      'task-id';

  @override
  Future<void> pauseDownload(String taskId) async {}

  @override
  Future<void> resumeDownload(String taskId) async {}

  @override
  Future<void> cancelDownload(String taskId, {bool deleteFiles = false}) async {
    if (_shouldFail) {
      throw Exception('Cancel failed');
    }
    _lastCancelledTaskId = taskId;
    _lastDeleteFiles = deleteFiles;
    _cancelledTasks.add(taskId);
  }

  @override
  Stream<TorrentProgress> getProgressStream(String taskId) =>
      const Stream.empty();

  @override
  Future<List<TorrentTask>> getActiveTasks() async => [];

  @override
  Future<TorrentTask?> getTask(String taskId) async => null;

  // Test helpers
  set shouldFail(bool value) => _shouldFail = value;
  String? get lastCancelledTaskId => _lastCancelledTaskId;
  bool get lastDeleteFiles => _lastDeleteFiles;
  bool isCancelled(String taskId) => _cancelledTasks.contains(taskId);
}

void main() {
  group('CancelTorrentUseCase', () {
    late MockTorrentRepository mockRepository;
    late CancelTorrentUseCase useCase;

    setUp(() {
      mockRepository = MockTorrentRepository();
      useCase = CancelTorrentUseCase(mockRepository);
    });

    test('should call repository cancelDownload with correct taskId', () async {
      // Arrange
      const taskId = 'test-task-id';

      // Act
      await useCase(taskId);

      // Assert
      expect(mockRepository.lastCancelledTaskId, equals(taskId));
      expect(mockRepository.lastDeleteFiles, isFalse);
      expect(mockRepository.isCancelled(taskId), isTrue);
    });

    test('should call repository cancelDownload with deleteFiles=true',
        () async {
      // Arrange
      const taskId = 'test-task-id';
      const deleteFiles = true;

      // Act
      await useCase(taskId, deleteFiles: deleteFiles);

      // Assert
      expect(mockRepository.lastCancelledTaskId, equals(taskId));
      expect(mockRepository.lastDeleteFiles, isTrue);
    });

    test('should throw exception when repository cancel fails', () async {
      // Arrange
      mockRepository.shouldFail = true;
      const taskId = 'test-task-id';

      // Act & Assert
      expect(
        () => useCase(taskId),
        throwsException,
      );
    });
  });
}
