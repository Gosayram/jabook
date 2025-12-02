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

/// Status of the smart search cache.
///
/// Provides information about cache state, size, and sync progress.
class CacheStatus {
  /// Creates a new CacheStatus instance.
  CacheStatus({
    required this.totalCachedBooks,
    required this.isStale,
    this.lastSyncTime,
    this.cacheSizeBytes,
    this.syncInProgress = false,
    this.syncProgress,
  });

  /// Total number of cached audiobooks.
  final int totalCachedBooks;

  /// Whether the cache is stale (needs update).
  final bool isStale;

  /// Time of last successful sync.
  final DateTime? lastSyncTime;

  /// Size of cache in bytes (if available).
  final int? cacheSizeBytes;

  /// Whether sync is currently in progress.
  final bool syncInProgress;

  /// Current sync progress (if sync is in progress).
  final SyncProgress? syncProgress;

  /// Whether cache is empty.
  bool get isEmpty => totalCachedBooks == 0;

  /// Whether cache is valid (not stale and not empty).
  bool get isValid => !isEmpty && !isStale;

  @override
  String toString() => 'CacheStatus(totalCachedBooks: $totalCachedBooks, '
      'isStale: $isStale, lastSyncTime: $lastSyncTime, '
      'cacheSizeBytes: $cacheSizeBytes, syncInProgress: $syncInProgress)';
}

/// Progress information for cache synchronization.
class SyncProgress {
  /// Creates a new SyncProgress instance.
  SyncProgress({
    required this.totalForums,
    required this.completedForums,
    required this.totalTopics,
    required this.completedTopics,
    this.currentForum,
    this.currentTopic,
    this.estimatedCompletionTime,
  }) : progressPercent = totalTopics > 0
            ? (completedTopics / totalTopics * 100).clamp(0.0, 100.0)
            : 0.0;

  /// Total number of forums to sync.
  final int totalForums;

  /// Number of completed forums.
  final int completedForums;

  /// Total number of topics to sync.
  final int totalTopics;

  /// Number of completed topics.
  final int completedTopics;

  /// Name of currently processing forum.
  final String? currentForum;

  /// ID or title of currently processing topic.
  final String? currentTopic;

  /// Estimated time of completion.
  final DateTime? estimatedCompletionTime;

  /// Progress percentage (0.0 to 100.0).
  final double progressPercent;

  /// Whether sync is complete.
  bool get isComplete => completedTopics >= totalTopics;

  @override
  String toString() => 'SyncProgress(totalForums: $totalForums, '
      'completedForums: $completedForums, '
      'totalTopics: $totalTopics, '
      'completedTopics: $completedTopics, '
      'progressPercent: ${progressPercent.toStringAsFixed(1)}%, '
      'currentForum: $currentForum, currentTopic: $currentTopic)';
}
