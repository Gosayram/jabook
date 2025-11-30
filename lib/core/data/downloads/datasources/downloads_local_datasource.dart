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

import 'package:jabook/core/torrent/audiobook_torrent_manager.dart' as old;

/// Data source interface for downloads operations.
///
/// This interface provides methods for interacting with the local
/// torrent manager to get and manage downloads.
abstract class DownloadsLocalDataSource {
  /// Gets all active and restored downloads.
  ///
  /// Returns a list of maps containing download information.
  ///
  /// Throws [Exception] if getting downloads fails.
  Future<List<Map<String, dynamic>>> getDownloads();

  /// Pauses a download.
  ///
  /// The [downloadId] parameter is the unique identifier for the download.
  ///
  /// Throws [Exception] if pausing fails.
  Future<void> pauseDownload(String downloadId);

  /// Resumes a paused download.
  ///
  /// The [downloadId] parameter is the unique identifier for the download.
  ///
  /// Throws [Exception] if resuming fails.
  Future<void> resumeDownload(String downloadId);

  /// Resumes a restored download.
  ///
  /// The [downloadId] parameter is the unique identifier for the restored download.
  ///
  /// Throws [Exception] if resuming fails.
  Future<void> resumeRestoredDownload(String downloadId);

  /// Restarts a download.
  ///
  /// The [downloadId] parameter is the unique identifier for the download.
  ///
  /// Throws [Exception] if restarting fails.
  Future<void> restartDownload(String downloadId);

  /// Redownloads a torrent.
  ///
  /// The [downloadId] parameter is the unique identifier for the download.
  ///
  /// Returns the new download ID.
  ///
  /// Throws [Exception] if redownloading fails.
  Future<String> redownload(String downloadId);

  /// Removes a download.
  ///
  /// The [downloadId] parameter is the unique identifier for the download.
  ///
  /// Throws [Exception] if removing fails.
  Future<void> removeDownload(String downloadId);

  /// Gets the progress stream for a download.
  ///
  /// The [downloadId] parameter is the unique identifier for the download.
  ///
  /// Returns a stream that emits TorrentProgress whenever the progress updates.
  Stream<old.TorrentProgress> getProgressStream(String downloadId);
}

/// Implementation of DownloadsLocalDataSource using AudiobookTorrentManager.
class DownloadsLocalDataSourceImpl implements DownloadsLocalDataSource {
  /// Creates a new DownloadsLocalDataSourceImpl instance.
  DownloadsLocalDataSourceImpl(this._manager);

  final old.AudiobookTorrentManager _manager;

  @override
  Future<List<Map<String, dynamic>>> getDownloads() =>
      _manager.getActiveDownloads();

  @override
  Future<void> pauseDownload(String downloadId) =>
      _manager.pauseDownload(downloadId);

  @override
  Future<void> resumeDownload(String downloadId) =>
      _manager.resumeDownload(downloadId);

  @override
  Future<void> resumeRestoredDownload(String downloadId) =>
      _manager.resumeRestoredDownload(downloadId);

  @override
  Future<void> restartDownload(String downloadId) =>
      _manager.restartDownload(downloadId);

  @override
  Future<String> redownload(String downloadId) =>
      _manager.redownload(downloadId);

  @override
  Future<void> removeDownload(String downloadId) =>
      _manager.removeDownload(downloadId);

  @override
  Stream<old.TorrentProgress> getProgressStream(String downloadId) =>
      _manager.getProgressStream(downloadId);
}
