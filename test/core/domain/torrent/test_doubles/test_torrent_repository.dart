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
import 'dart:typed_data';

import 'package:jabook/core/domain/torrent/entities/torrent_progress.dart';
import 'package:jabook/core/domain/torrent/entities/torrent_task.dart';
import 'package:jabook/core/domain/torrent/repositories/torrent_repository.dart';

/// Test implementation of TorrentRepository.
///
/// This implementation provides a simple in-memory storage for testing
/// torrent operations without external dependencies. It follows the
/// Test Doubles pattern from Now In Android.
///
/// **Test Hooks**:
/// - `addTask()` - Add a test task
/// - `setProgress()` - Set progress for a task
/// - `setShouldFail()` - Simulate failures
class TestTorrentRepository implements TorrentRepository {
  /// Creates a new TestTorrentRepository instance.
  TestTorrentRepository();

  final Map<String, TorrentTask> _tasks = {};
  final Map<String, TorrentProgress> _progress = {};
  final Map<String, StreamController<TorrentProgress>> _progressControllers =
      {};
  bool _shouldFail = false;
  int _taskIdCounter = 0;

  @override
  Future<void> initialize() async {
    if (_shouldFail) {
      throw Exception('Initialization failed');
    }
  }

  @override
  Future<String> startDownload(
    Uint8List? torrentBytes,
    String savePath, {
    String? magnetUrl,
    Map<String, dynamic>? metadata,
    bool sequential = false,
    List<int>? selectedFileIndices,
  }) async {
    if (_shouldFail) {
      throw Exception('Start download failed');
    }
    final taskId = 'task-${++_taskIdCounter}';
    final task = TorrentTask(
      taskId: taskId,
      torrentId: magnetUrl ?? 'torrent-$taskId',
      status: TorrentTaskStatus.downloading,
      savePath: savePath,
      metadata: metadata,
      createdAt: DateTime.now(),
    );
    _tasks[taskId] = task;

    // Initialize progress
    _progress[taskId] = TorrentProgress(
      progress: 0.0,
      downloadSpeed: 0.0,
      uploadSpeed: 0.0,
      downloadedBytes: 0,
      totalBytes: 1000000, // 1MB default
      seeders: 0,
      leechers: 0,
      status: 'downloading',
    );

    // Create progress stream controller
    final controller = StreamController<TorrentProgress>.broadcast();
    _progressControllers[taskId] = controller;
    controller.add(_progress[taskId]!);

    return taskId;
  }

  @override
  Future<void> pauseDownload(String taskId) async {
    if (_shouldFail) {
      throw Exception('Pause download failed');
    }
    final task = _tasks[taskId];
    if (task != null) {
      _tasks[taskId] = task.copyWith(status: TorrentTaskStatus.paused);
      final progress = _progress[taskId];
      if (progress != null) {
        _progress[taskId] = progress.copyWith(status: 'paused');
        _progressControllers[taskId]?.add(_progress[taskId]!);
      }
    }
  }

  @override
  Future<void> resumeDownload(String taskId) async {
    if (_shouldFail) {
      throw Exception('Resume download failed');
    }
    final task = _tasks[taskId];
    if (task != null) {
      _tasks[taskId] = task.copyWith(status: TorrentTaskStatus.downloading);
      final progress = _progress[taskId];
      if (progress != null) {
        _progress[taskId] = progress.copyWith(status: 'downloading');
        _progressControllers[taskId]?.add(_progress[taskId]!);
      }
    }
  }

  @override
  Future<void> cancelDownload(String taskId, {bool deleteFiles = false}) async {
    if (_shouldFail) {
      throw Exception('Cancel download failed');
    }
    _tasks.remove(taskId);
    _progress.remove(taskId);
    await _progressControllers[taskId]?.close();
    _progressControllers.remove(taskId);
  }

  @override
  Stream<TorrentProgress> getProgressStream(String taskId) {
    final controller = _progressControllers[taskId];
    if (controller == null) {
      return const Stream.empty();
    }
    return controller.stream;
  }

  @override
  Future<List<TorrentTask>> getActiveTasks() async {
    if (_shouldFail) {
      throw Exception('Get active tasks failed');
    }
    return _tasks.values.toList();
  }

  @override
  Future<TorrentTask?> getTask(String taskId) async {
    if (_shouldFail) {
      throw Exception('Get task failed');
    }
    return _tasks[taskId];
  }

  // Test hooks
  /// Adds a test task.
  void addTask(TorrentTask task) {
    _tasks[task.taskId] = task;
  }

  /// Sets progress for a task.
  void setProgress(String taskId, TorrentProgress progress) {
    _progress[taskId] = progress;
    _progressControllers[taskId]?.add(progress);
  }

  /// Sets whether operations should fail.
  set shouldFail(bool value) => _shouldFail = value;

  /// Clears all test data.
  void clear() {
    _tasks.clear();
    _progress.clear();
    for (final controller in _progressControllers.values) {
      controller.close();
    }
    _progressControllers.clear();
    _shouldFail = false;
    _taskIdCounter = 0;
  }

  /// Disposes the repository and releases resources.
  void dispose() {
    clear();
  }
}
