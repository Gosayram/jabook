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

import 'package:jabook/core/domain/torrent/entities/torrent_progress.dart';
import 'package:jabook/core/domain/torrent/entities/torrent_task.dart';

/// Repository interface for torrent operations.
///
/// This repository provides methods for managing torrent downloads,
/// monitoring progress, and controlling download tasks.
abstract class TorrentRepository {
  /// Initializes the torrent manager with database connection.
  ///
  /// This method should be called once when the app starts to enable
  /// persistence of download state across app restarts.
  Future<void> initialize();

  /// Starts a new torrent download.
  ///
  /// The [torrentBytes] parameter contains the content of the .torrent file.
  /// The [savePath] parameter is the directory where files will be saved.
  /// The [metadata] parameter contains optional metadata (title, author, etc.).
  ///
  /// Note: Current implementation uses magnet URLs. If torrentBytes is provided,
  /// it should be converted to a magnet URL first.
  ///
  /// Returns the task ID for the new download.
  ///
  /// Throws [Exception] if starting the download fails.
  Future<String> startDownload(
    Uint8List? torrentBytes,
    String savePath, {
    String? magnetUrl,
    Map<String, dynamic>? metadata,
    bool sequential = false,
    List<int>? selectedFileIndices,
  });

  /// Pauses a torrent download.
  ///
  /// The [taskId] parameter is the unique identifier for the task.
  ///
  /// Throws [Exception] if pausing fails.
  Future<void> pauseDownload(String taskId);

  /// Resumes a paused torrent download.
  ///
  /// The [taskId] parameter is the unique identifier for the task.
  ///
  /// Throws [Exception] if resuming fails.
  Future<void> resumeDownload(String taskId);

  /// Cancels and removes a torrent download.
  ///
  /// The [taskId] parameter is the unique identifier for the task.
  /// The [deleteFiles] parameter determines whether to delete downloaded files.
  ///
  /// Throws [Exception] if cancelling fails.
  Future<void> cancelDownload(String taskId, {bool deleteFiles = false});

  /// Gets the progress stream for a torrent download.
  ///
  /// The [taskId] parameter is the unique identifier for the task.
  ///
  /// Returns a stream that emits TorrentProgress whenever the progress updates.
  Stream<TorrentProgress> getProgressStream(String taskId);

  /// Gets all active torrent tasks.
  ///
  /// Returns a list of TorrentTask instances for all active downloads.
  Future<List<TorrentTask>> getActiveTasks();

  /// Gets a specific torrent task by ID.
  ///
  /// The [taskId] parameter is the unique identifier for the task.
  ///
  /// Returns the TorrentTask if found, null otherwise.
  Future<TorrentTask?> getTask(String taskId);
}
