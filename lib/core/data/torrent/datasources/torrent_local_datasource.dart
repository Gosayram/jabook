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

import 'package:jabook/core/data/torrent/mappers/torrent_mapper.dart';
import 'package:jabook/core/domain/torrent/entities/torrent_progress.dart';
import 'package:jabook/core/domain/torrent/entities/torrent_task.dart';
import 'package:jabook/core/torrent/audiobook_torrent_manager.dart'
    as old_torrent;

/// Local data source for torrent operations.
///
/// This class wraps AudiobookTorrentManager to provide a clean interface
/// for torrent operations that interact with the local torrent manager.
abstract class TorrentLocalDataSource {
  /// Initializes the torrent manager with database connection.
  Future<void> initialize();

  /// Starts a new torrent download using magnet URL or torrent bytes.
  ///
  /// Note: The current implementation uses magnet URLs. If torrentBytes is provided,
  /// it should be converted to a magnet URL first.
  Future<String> startDownload(
    String savePath, {
    Uint8List? torrentBytes,
    String? magnetUrl,
    Map<String, dynamic>? metadata,
    bool sequential = false,
    List<int>? selectedFileIndices,
  });

  /// Pauses a torrent download.
  Future<void> pauseDownload(String downloadId);

  /// Resumes a paused torrent download.
  Future<void> resumeDownload(String downloadId);

  /// Cancels and removes a torrent download.
  Future<void> cancelDownload(String downloadId);

  /// Gets the progress stream for a torrent download.
  Stream<TorrentProgress> getProgressStream(String downloadId);

  /// Gets all active torrent tasks.
  Future<List<TorrentTask>> getActiveTasks();
}

/// Implementation of TorrentLocalDataSource using AudiobookTorrentManager.
class TorrentLocalDataSourceImpl implements TorrentLocalDataSource {
  /// Creates a new TorrentLocalDataSourceImpl instance.
  TorrentLocalDataSourceImpl(this._manager);

  final old_torrent.AudiobookTorrentManager _manager;

  @override
  Future<void> initialize() async {
    // Note: Database should be passed from outside
    // For now, we'll initialize without database
    // This should be handled by the provider
  }

  @override
  Future<String> startDownload(
    String savePath, {
    Uint8List? torrentBytes,
    String? magnetUrl,
    Map<String, dynamic>? metadata,
    bool sequential = false,
    List<int>? selectedFileIndices,
  }) {
    // Current implementation only supports magnet URLs
    if (magnetUrl == null) {
      throw Exception(
          'Magnet URL is required. Torrent bytes conversion not yet implemented.');
    }

    final title = metadata?['title'] as String?;
    final coverUrl = metadata?['coverUrl'] as String?;

    return _manager.downloadSequential(
      magnetUrl,
      savePath,
      title: title,
      coverUrl: coverUrl,
    );
  }

  @override
  Future<void> pauseDownload(String downloadId) =>
      _manager.pauseDownload(downloadId);

  @override
  Future<void> resumeDownload(String downloadId) =>
      _manager.resumeDownload(downloadId);

  @override
  Future<void> cancelDownload(String downloadId) =>
      _manager.removeDownload(downloadId);

  @override
  Stream<TorrentProgress> getProgressStream(String downloadId) =>
      _manager.getProgressStream(downloadId).map(TorrentMapper.toDomain);

  @override
  Future<List<TorrentTask>> getActiveTasks() async =>
      // Note: AudiobookTorrentManager doesn't expose active tasks directly
      // This would need to be implemented based on internal state
      // For now, return empty list
      [];
}
