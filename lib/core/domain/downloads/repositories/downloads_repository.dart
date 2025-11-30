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

import 'package:jabook/core/domain/downloads/entities/download_item.dart';
import 'package:jabook/core/domain/torrent/entities/torrent_progress.dart';

/// Repository interface for managing downloads.
///
/// This repository provides methods for managing download items,
/// including getting the list of downloads, controlling downloads,
/// and monitoring progress.
abstract class DownloadsRepository {
  /// Gets all active and restored downloads.
  ///
  /// Returns a list of all downloads, including active downloads
  /// and restored downloads from the database.
  ///
  /// Throws [Exception] if getting downloads fails.
  Future<List<DownloadItem>> getDownloads();

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
  Stream<TorrentProgress> getProgressStream(String downloadId);
}
