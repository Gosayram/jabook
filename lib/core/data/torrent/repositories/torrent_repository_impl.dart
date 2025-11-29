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

import 'dart:typed_data';

import 'package:jabook/core/data/torrent/datasources/torrent_local_datasource.dart';
import 'package:jabook/core/domain/torrent/entities/torrent_progress.dart';
import 'package:jabook/core/domain/torrent/entities/torrent_task.dart';
import 'package:jabook/core/domain/torrent/repositories/torrent_repository.dart';

/// Implementation of TorrentRepository using data sources.
class TorrentRepositoryImpl implements TorrentRepository {
  /// Creates a new TorrentRepositoryImpl instance.
  TorrentRepositoryImpl(this._localDataSource);

  final TorrentLocalDataSource _localDataSource;

  @override
  Future<void> initialize() => _localDataSource.initialize();

  @override
  Future<String> startDownload(
    Uint8List? torrentBytes,
    String savePath, {
    String? magnetUrl,
    Map<String, dynamic>? metadata,
    bool sequential = false,
    List<int>? selectedFileIndices,
  }) =>
      _localDataSource.startDownload(
        savePath,
        torrentBytes: torrentBytes,
        magnetUrl: magnetUrl,
        metadata: metadata,
        sequential: sequential,
        selectedFileIndices: selectedFileIndices,
      );

  @override
  Future<void> pauseDownload(String taskId) =>
      _localDataSource.pauseDownload(taskId);

  @override
  Future<void> resumeDownload(String taskId) =>
      _localDataSource.resumeDownload(taskId);

  @override
  Future<void> cancelDownload(String taskId, {bool deleteFiles = false}) =>
      _localDataSource.cancelDownload(taskId);

  @override
  Stream<TorrentProgress> getProgressStream(String taskId) =>
      _localDataSource.getProgressStream(taskId);

  @override
  Future<List<TorrentTask>> getActiveTasks() =>
      _localDataSource.getActiveTasks();

  @override
  Future<TorrentTask?> getTask(String taskId) async {
    final tasks = await getActiveTasks();
    try {
      return tasks.firstWhere((task) => task.taskId == taskId);
    } on StateError {
      return null;
    }
  }
}
