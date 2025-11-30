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
import 'package:jabook/core/domain/torrent/use_cases/get_active_tasks_use_case.dart';

/// Mock implementation of TorrentRepository for testing.
class MockTorrentRepository implements TorrentRepository {
  final List<TorrentTask> _tasks = [];
  bool _shouldFail = false;

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
  Future<void> cancelDownload(String taskId,
      {bool deleteFiles = false}) async {}

  @override
  Stream<TorrentProgress> getProgressStream(String taskId) =>
      const Stream.empty();

  @override
  Future<List<TorrentTask>> getActiveTasks() async {
    if (_shouldFail) {
      throw Exception('Failed to get active tasks');
    }
    return _tasks;
  }

  @override
  Future<TorrentTask?> getTask(String taskId) async => null;

  // Test helpers
  set tasks(List<TorrentTask> value) {
    _tasks
      ..clear()
      ..addAll(value);
  }

  set shouldFail(bool value) => _shouldFail = value;
}

void main() {
  group('GetActiveTasksUseCase', () {
    late MockTorrentRepository mockRepository;
    late GetActiveTasksUseCase useCase;

    setUp(() {
      mockRepository = MockTorrentRepository();
      useCase = GetActiveTasksUseCase(mockRepository);
    });

    test('should return list of active tasks', () async {
      // Arrange
      final tasks = [
        TorrentTask(
          taskId: 'task-1',
          torrentId: 'torrent-1',
          status: TorrentTaskStatus.downloading,
          savePath: '/path/to/save',
        ),
        TorrentTask(
          taskId: 'task-2',
          torrentId: 'torrent-2',
          status: TorrentTaskStatus.paused,
          savePath: '/path/to/save2',
        ),
      ];
      mockRepository.tasks = tasks;

      // Act
      final result = await useCase();

      // Assert
      expect(result, equals(tasks));
      expect(result.length, equals(2));
    });

    test('should return empty list when no active tasks', () async {
      // Arrange
      mockRepository.tasks = [];

      // Act
      final result = await useCase();

      // Assert
      expect(result, isEmpty);
    });

    test('should throw exception when repository fails', () async {
      // Arrange
      mockRepository.shouldFail = true;

      // Act & Assert
      expect(
        () => useCase(),
        throwsException,
      );
    });
  });
}
