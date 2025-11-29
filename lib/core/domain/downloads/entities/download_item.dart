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

/// Represents a download item in the downloads list.
///
/// This entity contains all information needed to display a download
/// in the UI, including its status, progress, and metadata.
class DownloadItem {
  /// Creates a new DownloadItem instance.
  DownloadItem({
    required this.id,
    required this.name,
    required this.status,
    required this.progress,
    required this.downloadSpeed,
    required this.uploadSpeed,
    required this.downloadedBytes,
    required this.totalBytes,
    required this.seeders,
    required this.leechers,
    required this.isActive,
    this.title,
    this.savePath,
    this.startedAt,
    this.pausedAt,
  });

  /// Unique identifier for the download.
  final String id;

  /// Display name of the download.
  final String name;

  /// Optional title from metadata.
  final String? title;

  /// Current status of the download (e.g., 'downloading', 'completed', 'paused', 'restored').
  final String status;

  /// Download progress as a percentage (0.0 to 100.0).
  final double progress;

  /// Current download speed in bytes per second.
  final double downloadSpeed;

  /// Current upload speed in bytes per second.
  final double uploadSpeed;

  /// Number of bytes downloaded so far.
  final int downloadedBytes;

  /// Total size of the download in bytes.
  final int totalBytes;

  /// Number of seeders connected to the torrent.
  final int seeders;

  /// Number of leechers connected to the torrent.
  final int leechers;

  /// Whether the download is currently active.
  final bool isActive;

  /// Path where the download is being saved.
  final String? savePath;

  /// Timestamp when the download was started.
  final DateTime? startedAt;

  /// Timestamp when the download was paused.
  final DateTime? pausedAt;

  /// Checks if the download is completed.
  bool get isCompleted => status == 'completed' || progress >= 100.0;

  /// Checks if the download is paused.
  bool get isPaused => status == 'paused';

  /// Checks if the download is restored (from database but not active).
  bool get isRestored => status == 'restored';

  /// Checks if the download has an error.
  bool get hasError => status.startsWith('error');

  /// Creates a copy with updated fields.
  DownloadItem copyWith({
    String? id,
    String? name,
    String? title,
    String? status,
    double? progress,
    double? downloadSpeed,
    double? uploadSpeed,
    int? downloadedBytes,
    int? totalBytes,
    int? seeders,
    int? leechers,
    bool? isActive,
    String? savePath,
    DateTime? startedAt,
    DateTime? pausedAt,
  }) =>
      DownloadItem(
        id: id ?? this.id,
        name: name ?? this.name,
        title: title ?? this.title,
        status: status ?? this.status,
        progress: progress ?? this.progress,
        downloadSpeed: downloadSpeed ?? this.downloadSpeed,
        uploadSpeed: uploadSpeed ?? this.uploadSpeed,
        downloadedBytes: downloadedBytes ?? this.downloadedBytes,
        totalBytes: totalBytes ?? this.totalBytes,
        seeders: seeders ?? this.seeders,
        leechers: leechers ?? this.leechers,
        isActive: isActive ?? this.isActive,
        savePath: savePath ?? this.savePath,
        startedAt: startedAt ?? this.startedAt,
        pausedAt: pausedAt ?? this.pausedAt,
      );
}
