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

/// Represents the progress of a torrent download.
///
/// This is a domain entity that contains all the information needed to display
/// and monitor the progress of a torrent download, including transfer speeds,
/// completion percentage, and peer information.
class TorrentProgress {
  /// Creates a new TorrentProgress instance.
  ///
  /// All parameters are required to provide complete download progress information.
  TorrentProgress({
    required this.progress,
    required this.downloadSpeed,
    required this.uploadSpeed,
    required this.downloadedBytes,
    required this.totalBytes,
    required this.seeders,
    required this.leechers,
    required this.status,
  });

  /// Download progress as a percentage (0.0 to 100.0).
  final double progress;

  /// Current download speed in bytes per second.
  final double downloadSpeed;

  /// Current upload speed in bytes per second.
  final double uploadSpeed;

  /// Number of bytes downloaded so far.
  final int downloadedBytes;

  /// Total size of the torrent in bytes.
  final int totalBytes;

  /// Number of seeders connected to the torrent.
  final int seeders;

  /// Number of leechers connected to the torrent.
  final int leechers;

  /// Current status of the download (e.g., 'downloading', 'completed', 'paused').
  final String status;

  /// Gets the remaining bytes to download.
  int get remainingBytes => totalBytes - downloadedBytes;

  /// Gets the progress as a decimal (0.0 to 1.0).
  double get progressDecimal => progress / 100.0;

  /// Checks if the download is completed.
  bool get isCompleted => status == 'completed' || progress >= 100.0;

  /// Checks if the download is paused.
  bool get isPaused => status == 'paused';

  /// Checks if the download is active.
  bool get isActive => status == 'downloading';

  /// Gets formatted download speed (e.g., "1.5 MB/s").
  String get formattedDownloadSpeed {
    if (downloadSpeed < 1024) {
      return '${downloadSpeed.toStringAsFixed(0)} B/s';
    } else if (downloadSpeed < 1024 * 1024) {
      return '${(downloadSpeed / 1024).toStringAsFixed(1)} KB/s';
    } else {
      return '${(downloadSpeed / (1024 * 1024)).toStringAsFixed(1)} MB/s';
    }
  }

  /// Gets formatted total size (e.g., "1.5 GB").
  String get formattedTotalSize {
    if (totalBytes < 1024) {
      return '$totalBytes B';
    } else if (totalBytes < 1024 * 1024) {
      return '${(totalBytes / 1024).toStringAsFixed(1)} KB';
    } else if (totalBytes < 1024 * 1024 * 1024) {
      return '${(totalBytes / (1024 * 1024)).toStringAsFixed(1)} MB';
    } else {
      return '${(totalBytes / (1024 * 1024 * 1024)).toStringAsFixed(1)} GB';
    }
  }

  /// Creates a copy with updated fields.
  TorrentProgress copyWith({
    double? progress,
    double? downloadSpeed,
    double? uploadSpeed,
    int? downloadedBytes,
    int? totalBytes,
    int? seeders,
    int? leechers,
    String? status,
  }) =>
      TorrentProgress(
        progress: progress ?? this.progress,
        downloadSpeed: downloadSpeed ?? this.downloadSpeed,
        uploadSpeed: uploadSpeed ?? this.uploadSpeed,
        downloadedBytes: downloadedBytes ?? this.downloadedBytes,
        totalBytes: totalBytes ?? this.totalBytes,
        seeders: seeders ?? this.seeders,
        leechers: leechers ?? this.leechers,
        status: status ?? this.status,
      );
}
