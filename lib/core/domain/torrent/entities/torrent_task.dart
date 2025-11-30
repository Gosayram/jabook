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

/// Represents a torrent download task.
///
/// This is a domain entity that contains information about a torrent download task,
/// including its ID, status, and metadata.
class TorrentTask {
  /// Creates a new TorrentTask instance.
  TorrentTask({
    required this.taskId,
    required this.torrentId,
    required this.status,
    this.savePath,
    this.metadata,
    this.createdAt,
    this.completedAt,
  });

  /// Unique identifier for the task.
  final String taskId;

  /// Torrent ID (info hash).
  final String torrentId;

  /// Current status of the task.
  final TorrentTaskStatus status;

  /// Path where the torrent is being saved.
  final String? savePath;

  /// Additional metadata for the task.
  final Map<String, dynamic>? metadata;

  /// Timestamp when the task was created.
  final DateTime? createdAt;

  /// Timestamp when the task was completed.
  final DateTime? completedAt;

  /// Checks if the task is active.
  bool get isActive =>
      status == TorrentTaskStatus.downloading ||
      status == TorrentTaskStatus.paused;

  /// Checks if the task is completed.
  bool get isCompleted => status == TorrentTaskStatus.completed;

  /// Checks if the task has failed.
  bool get hasFailed => status == TorrentTaskStatus.failed;

  /// Creates a copy with updated fields.
  TorrentTask copyWith({
    String? taskId,
    String? torrentId,
    TorrentTaskStatus? status,
    String? savePath,
    Map<String, dynamic>? metadata,
    DateTime? createdAt,
    DateTime? completedAt,
  }) =>
      TorrentTask(
        taskId: taskId ?? this.taskId,
        torrentId: torrentId ?? this.torrentId,
        status: status ?? this.status,
        savePath: savePath ?? this.savePath,
        metadata: metadata ?? this.metadata,
        createdAt: createdAt ?? this.createdAt,
        completedAt: completedAt ?? this.completedAt,
      );
}

/// Status of a torrent task.
enum TorrentTaskStatus {
  /// Task is queued and waiting to start.
  queued,

  /// Task is currently downloading.
  downloading,

  /// Task is paused.
  paused,

  /// Task has completed successfully.
  completed,

  /// Task has failed.
  failed,

  /// Task has been cancelled.
  cancelled,
}
