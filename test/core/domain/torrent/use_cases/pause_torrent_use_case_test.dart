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
import 'package:jabook/core/domain/torrent/use_cases/pause_torrent_use_case.dart';

/// Mock implementation of TorrentRepository for testing.
class MockTorrentRepository implements TorrentRepository {
  final Set<String> _pausedTasks = {};
  bool _shouldFail = false;
  String? _lastPausedTaskId;

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
  Future<void> pauseDownload(String taskId) async {
    if (_shouldFail) {
      throw Exception('Pause failed');
    }
    _lastPausedTaskId = taskId;
    _pausedTasks.add(taskId);
  }

  @override
  Future<void> resumeDownload(String taskId) async {}

  @override
  Future<void> cancelDownload(String taskId,
      {bool deleteFiles = false}) async {}

  @override
  Stream<TorrentProgress> getProgressStream(String taskId) =>
      const Stream.empty();

  @override
  Future<List<TorrentTask>> getActiveTasks() async => [];

  @override
  Future<TorrentTask?> getTask(String taskId) async => null;

  // Test helpers
  set shouldFail(bool value) => _shouldFail = value;
  String? get lastPausedTaskId => _lastPausedTaskId;
  bool isPaused(String taskId) => _pausedTasks.contains(taskId);
}

void main() {
  group('PauseTorrentUseCase', () {
    late MockTorrentRepository mockRepository;
    late PauseTorrentUseCase useCase;

    setUp(() {
      mockRepository = MockTorrentRepository();
      useCase = PauseTorrentUseCase(mockRepository);
    });

    test('should call repository pauseDownload with correct taskId', () async {
      // Arrange
      const taskId = 'test-task-id';

      // Act
      await useCase(taskId);

      // Assert
      expect(mockRepository.lastPausedTaskId, equals(taskId));
      expect(mockRepository.isPaused(taskId), isTrue);
    });

    test('should throw exception when repository pause fails', () async {
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
