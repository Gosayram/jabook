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

import 'package:jabook/core/domain/torrent/entities/torrent_progress.dart';
import 'package:jabook/core/domain/torrent/entities/torrent_task.dart';
import 'package:jabook/core/torrent/audiobook_torrent_manager.dart'
    as old_torrent;

/// Mapper for converting old torrent entities to new domain entities.
class TorrentMapper {
  // Private constructor to prevent instantiation
  TorrentMapper._();

  /// Converts old TorrentProgress to new domain TorrentProgress.
  static TorrentProgress toDomain(old_torrent.TorrentProgress old) =>
      TorrentProgress(
        progress: old.progress,
        downloadSpeed: old.downloadSpeed,
        uploadSpeed: old.uploadSpeed,
        downloadedBytes: old.downloadedBytes,
        totalBytes: old.totalBytes,
        seeders: old.seeders,
        leechers: old.leechers,
        status: old.status,
      );

  /// Converts status string to TorrentTaskStatus enum.
  static TorrentTaskStatus statusFromString(String status) {
    switch (status.toLowerCase()) {
      case 'downloading':
      case 'downloading_metadata':
        return TorrentTaskStatus.downloading;
      case 'paused':
        return TorrentTaskStatus.paused;
      case 'completed':
        return TorrentTaskStatus.completed;
      case 'failed':
      case 'error':
        return TorrentTaskStatus.failed;
      case 'cancelled':
      case 'stopped':
        return TorrentTaskStatus.cancelled;
      default:
        return TorrentTaskStatus.queued;
    }
  }
}
