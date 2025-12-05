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

import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:jabook/core/infrastructure/task_manager/task_manager.dart';

/// Task execution statistics.
class TaskStatistics {
  /// Creates new task statistics.
  TaskStatistics({
    required this.activeTasks,
    required this.maxConcurrent,
    required this.queuedHeavy,
    required this.queuedMedium,
    required this.queuedLight,
    required this.paused,
    required this.totalExecuted,
    required this.totalFailed,
    required this.averageDurationMs,
    required this.totalRejected,
    required this.rejectedHeavy,
    required this.rejectedMedium,
    required this.rejectedLight,
  });

  /// Number of currently active tasks.
  final int activeTasks;

  /// Maximum concurrent tasks.
  final int maxConcurrent;

  /// Number of queued HEAVY tasks.
  final int queuedHeavy;

  /// Number of queued MEDIUM tasks.
  final int queuedMedium;

  /// Number of queued LIGHT tasks.
  final int queuedLight;

  /// Whether tasks are paused.
  final bool paused;

  /// Total number of executed tasks.
  final int totalExecuted;

  /// Total number of failed tasks.
  final int totalFailed;

  /// Average task duration in milliseconds.
  final int averageDurationMs;

  /// Total number of rejected tasks (queue overflow).
  final int totalRejected;

  /// Number of rejected HEAVY tasks.
  final int rejectedHeavy;

  /// Number of rejected MEDIUM tasks.
  final int rejectedMedium;

  /// Number of rejected LIGHT tasks.
  final int rejectedLight;

  /// Get total queued tasks.
  int get totalQueued => queuedHeavy + queuedMedium + queuedLight;

  /// Get success rate (0.0 to 1.0).
  double get successRate {
    if (totalExecuted == 0) return 1.0;
    return (totalExecuted - totalFailed) / totalExecuted;
  }

  /// Get rejection rate (0.0 to 1.0).
  double get rejectionRate {
    final total = totalExecuted + totalRejected;
    if (total == 0) return 0.0;
    return totalRejected / total;
  }
}

/// Monitor for task manager operations.
///
/// Provides statistics, logging, and health monitoring for tasks.
class TaskMonitor {
  /// Private constructor for singleton pattern.
  TaskMonitor._();

  /// Singleton instance.
  static final TaskMonitor instance = TaskMonitor._();

  /// Logger for monitoring operations.
  final StructuredLogger _logger = StructuredLogger();

  /// Total executed tasks counter.
  int _totalExecuted = 0;

  /// Total failed tasks counter.
  int _totalFailed = 0;

  /// Total duration of all tasks (milliseconds).
  int _totalDurationMs = 0;

  /// Total rejected tasks counter (queue overflow).
  int _totalRejected = 0;

  /// Rejected tasks by priority.
  final Map<TaskPriority, int> _rejectedByPriority = {
    TaskPriority.light: 0,
    TaskPriority.medium: 0,
    TaskPriority.heavy: 0,
  };

  /// Periodic statistics reporting timer.
  Timer? _reportTimer;

  /// Start periodic statistics reporting.
  ///
  /// The [interval] specifies how often to report statistics (default: 60 seconds).
  void startReporting({Duration interval = const Duration(seconds: 60)}) {
    _reportTimer?.cancel();
    _reportTimer = Timer.periodic(interval, (_) {
      _reportStatistics();
    });
  }

  /// Stop periodic statistics reporting.
  void stopReporting() {
    _reportTimer?.cancel();
    _reportTimer = null;
  }

  /// Record task execution.
  ///
  /// The [duration] is the task execution duration in milliseconds.
  /// The [success] indicates whether the task succeeded.
  void recordTaskExecution({
    required int durationMs,
    required bool success,
  }) {
    _totalExecuted++;
    _totalDurationMs += durationMs;

    if (!success) {
      _totalFailed++;
    }
  }

  /// Record task rejection due to queue overflow.
  ///
  /// The [priority] is the priority of the rejected task.
  void recordTaskRejection({required TaskPriority priority}) {
    _totalRejected++;
    _rejectedByPriority[priority] = (_rejectedByPriority[priority] ?? 0) + 1;
  }

  /// Get current statistics.
  Future<TaskStatistics> getStatistics() async {
    final managerStats = TaskManager.instance.getStatistics();

    return TaskStatistics(
      activeTasks: managerStats['active_tasks'] as int,
      maxConcurrent: managerStats['max_concurrent'] as int,
      queuedHeavy: managerStats['queued_heavy'] as int,
      queuedMedium: managerStats['queued_medium'] as int,
      queuedLight: managerStats['queued_light'] as int,
      paused: managerStats['paused'] as bool,
      totalExecuted: _totalExecuted,
      totalFailed: _totalFailed,
      averageDurationMs:
          _totalExecuted > 0 ? (_totalDurationMs / _totalExecuted).round() : 0,
      totalRejected: _totalRejected,
      rejectedHeavy: _rejectedByPriority[TaskPriority.heavy] ?? 0,
      rejectedMedium: _rejectedByPriority[TaskPriority.medium] ?? 0,
      rejectedLight: _rejectedByPriority[TaskPriority.light] ?? 0,
    );
  }

  /// Report current statistics to logs.
  Future<void> _reportStatistics() async {
    final stats = await getStatistics();

    await _logger.log(
      level: 'info',
      subsystem: 'task_monitor',
      message: 'Task statistics',
      extra: {
        'active_tasks': stats.activeTasks,
        'max_concurrent': stats.maxConcurrent,
        'queued_heavy': stats.queuedHeavy,
        'queued_medium': stats.queuedMedium,
        'queued_light': stats.queuedLight,
        'total_queued': stats.totalQueued,
        'paused': stats.paused,
        'total_executed': stats.totalExecuted,
        'total_failed': stats.totalFailed,
        'total_rejected': stats.totalRejected,
        'rejected_heavy': stats.rejectedHeavy,
        'rejected_medium': stats.rejectedMedium,
        'rejected_light': stats.rejectedLight,
        'success_rate': stats.successRate,
        'rejection_rate': stats.rejectionRate,
        'avg_duration_ms': stats.averageDurationMs,
      },
    );
  }

  /// Check task manager health.
  ///
  /// Returns true if the task manager is healthy (not overloaded).
  Future<bool> checkHealth() async {
    final stats = await getStatistics();

    // Health checks:
    // 1. Not too many queued tasks (warning if > 100)
    // 2. Success rate is reasonable (> 0.9)
    // 3. Rejection rate is acceptable (< 0.1)
    // 4. Not paused indefinitely

    final isHealthy = stats.totalQueued < 100 &&
        stats.successRate > 0.9 &&
        stats.rejectionRate < 0.1 &&
        (!stats.paused || stats.activeTasks > 0);

    if (!isHealthy) {
      await _logger.log(
        level: 'warning',
        subsystem: 'task_monitor',
        message: 'Task manager health check failed',
        extra: {
          'queued_tasks': stats.totalQueued,
          'success_rate': stats.successRate,
          'rejection_rate': stats.rejectionRate,
          'total_rejected': stats.totalRejected,
          'paused': stats.paused,
        },
      );
    }

    return isHealthy;
  }

  /// Reset statistics counters.
  void resetStatistics() {
    _totalExecuted = 0;
    _totalFailed = 0;
    _totalDurationMs = 0;
    _totalRejected = 0;
    _rejectedByPriority.clear();
    _rejectedByPriority[TaskPriority.light] = 0;
    _rejectedByPriority[TaskPriority.medium] = 0;
    _rejectedByPriority[TaskPriority.heavy] = 0;
  }
}
