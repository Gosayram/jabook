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
import 'package:jabook/core/infrastructure/task_manager/battery_monitor.dart';
import 'package:jabook/core/infrastructure/task_manager/task_monitor.dart';

/// Task priority levels.
enum TaskPriority {
  /// Light tasks: caching, UI updates (LIFO queue)
  light,

  /// Medium tasks: metadata downloads, MediaItem creation (LIFO queue)
  medium,

  /// Heavy tasks: torrent parsing, library scanning (FIFO queue)
  heavy,
}

/// Task manager for centralized control of concurrent tasks.
///
/// This manager provides:
/// - Priority-based task queues (HEAVY: FIFO, MEDIUM/LIGHT: LIFO)
/// - Limited parallelism (2-4 isolates based on CPU cores)
/// - Task monitoring and logging
/// - Energy efficiency (pause/resume non-critical tasks)
class TaskManager {
  /// Private constructor for singleton pattern.
  TaskManager._();

  /// Singleton instance.
  static final TaskManager instance = TaskManager._();

  /// Logger for task operations.
  final StructuredLogger _logger = StructuredLogger();

  /// Task monitor for statistics.
  final TaskMonitor _monitor = TaskMonitor.instance;

  /// Battery monitor for energy efficiency.
  final BatteryMonitor _batteryMonitor = BatteryMonitor.instance;

  /// Number of CPU cores (defaults to 2 if detection fails).
  static int _cpuCores = 2;

  /// Retry configuration for failed tasks.
  static const int _maxRetries = 3;
  static const Duration _retryDelay = Duration(seconds: 2);

  /// Failed tasks waiting for retry.
  final Map<_TaskItem, int> _failedTasks = {};

  /// Maximum number of concurrent isolates (2-4 based on CPU).
  static int get _maxConcurrentIsolates {
    if (_cpuCores <= 2) return 2;
    if (_cpuCores <= 4) return 3;
    return 4;
  }

  /// Initialize CPU core detection and battery monitoring.
  static Future<void> initialize() async {
    try {
      // Try to detect CPU cores (platform-specific)
      // For now, default to 2, can be enhanced with platform channels
      _cpuCores = 2;
    } on Exception {
      // Default to 2 cores if detection fails
      _cpuCores = 2;
    } on Object {
      // Default to 2 cores if detection fails
      _cpuCores = 2;
    }

    // Initialize battery monitoring
    await instance._batteryMonitor.initialize();
  }

  /// Maximum queue size for each priority.
  /// Tasks exceeding this limit will be rejected.
  static const Map<TaskPriority, int> _maxQueueSize = {
    TaskPriority.light: 50, // Light tasks: smaller queue
    TaskPriority.medium: 100, // Medium tasks: medium queue
    TaskPriority.heavy:
        200, // Heavy tasks: larger queue (FIFO, so more can wait)
  };

  /// Queues for different priorities.
  final Map<TaskPriority, List<_TaskItem>> _queues = {
    TaskPriority.light: [],
    TaskPriority.medium: [],
    TaskPriority.heavy: [],
  };

  /// Currently executing tasks.
  final Set<_TaskItem> _activeTasks = {};

  /// Semaphore for limiting concurrent isolates.
  final List<Completer<void>> _isolateSemaphore = [];

  /// Counter for rejected tasks (queue overflow).
  int _rejectedTasks = 0;

  /// Pause state for non-critical tasks.
  bool _paused = false;

  /// Pause completer (null if not paused).
  Completer<void>? _pauseCompleter;

  /// Submit a single task with priority.
  ///
  /// The [priority] determines which queue the task goes to.
  /// The [task] is the function to execute.
  ///
  /// Returns the result of the task execution.
  /// Throws [TaskRejectedException] if queue is full.
  Future<T> submit<T>({
    required TaskPriority priority,
    required Future<T> Function() task,
  }) async {
    final completer = Completer<T>();
    final taskItem = _TaskItem<T>(
      priority: priority,
      task: task,
      completer: completer,
      submittedAt: DateTime.now(),
    );

    // Check queue size limit
    final queue = _queues[priority]!;
    final maxSize = _maxQueueSize[priority] ?? 100;

    if (queue.length >= maxSize) {
      // Queue is full, reject the task
      _rejectedTasks++;
      final error = TaskRejectedException(
        'Task queue for priority ${priority.name} is full (max: $maxSize)',
        priority: priority,
        queueSize: queue.length,
      );

      await _logger.log(
        level: 'warning',
        subsystem: 'task_manager',
        message: 'Task rejected due to queue overflow',
        extra: {
          'priority': priority.name,
          'queue_size': queue.length,
          'max_size': maxSize,
          'total_rejected': _rejectedTasks,
        },
      );

      // Record rejection in monitor
      _monitor.recordTaskRejection(priority: priority);

      completer.completeError(error);
      return completer.future;
    }

    queue.add(taskItem);
    _processNext();

    return completer.future;
  }

  /// Submit multiple tasks with priority and concurrency limit.
  ///
  /// The [priority] determines which queue the tasks go to.
  /// The [maxConcurrent] limits how many tasks run simultaneously.
  /// The [tasks] are the functions to execute.
  ///
  /// Returns a list of results in the same order as tasks.
  Future<List<T>> submitAll<T>({
    required TaskPriority priority,
    required int maxConcurrent,
    required List<Future<T> Function()> tasks,
  }) async {
    if (tasks.isEmpty) return [];

    final results = <T>[];
    for (var i = 0; i < tasks.length; i += maxConcurrent) {
      final batch = tasks.skip(i).take(maxConcurrent).toList();
      final batchResults = await Future.wait(
        batch.map((task) => submit<T>(priority: priority, task: task)),
      );
      results.addAll(batchResults);
    }

    return results;
  }

  /// Process next task from queues.
  ///
  /// Tasks are processed in priority order: HEAVY -> MEDIUM -> LIGHT.
  /// Within each priority, queue order depends on priority type:
  /// - HEAVY: FIFO (first in, first out)
  /// - MEDIUM/LIGHT: LIFO (last in, first out)
  void _processNext() {
    if (_paused) return;
    if (_activeTasks.length >= _maxConcurrentIsolates) return;

    // Find next task in priority order
    _TaskItem? nextTask;
    TaskPriority? nextPriority;

    // Check HEAVY queue (FIFO)
    if (_queues[TaskPriority.heavy]!.isNotEmpty) {
      nextTask = _queues[TaskPriority.heavy]!.removeAt(0);
      nextPriority = TaskPriority.heavy;
    }
    // Check MEDIUM queue (LIFO)
    else if (_queues[TaskPriority.medium]!.isNotEmpty) {
      nextTask = _queues[TaskPriority.medium]!.removeLast();
      nextPriority = TaskPriority.medium;
    }
    // Check LIGHT queue (LIFO)
    else if (_queues[TaskPriority.light]!.isNotEmpty) {
      nextTask = _queues[TaskPriority.light]!.removeLast();
      nextPriority = TaskPriority.light;
    }

    if (nextTask == null || nextPriority == null) return;

    // Acquire isolate slot
    _acquireIsolateSlot().then((_) {
      _executeTask(nextTask!);
    });
  }

  /// Acquire a slot in the isolate semaphore.
  Future<void> _acquireIsolateSlot() async {
    if (_isolateSemaphore.length < _maxConcurrentIsolates) {
      return;
    }

    final completer = Completer<void>();
    _isolateSemaphore.add(completer);
    await completer.future;
  }

  /// Release a slot in the isolate semaphore.
  void _releaseIsolateSlot() {
    if (_isolateSemaphore.isNotEmpty) {
      final completer = _isolateSemaphore.removeAt(0);
      if (!completer.isCompleted) {
        completer.complete();
      }
    }
  }

  /// Execute a task.
  Future<void> _executeTask<T>(_TaskItem<T> taskItem) async {
    _activeTasks.add(taskItem);
    final startTime = DateTime.now();

    try {
      await _logger.log(
        level: 'debug',
        subsystem: 'task_manager',
        message: 'Task started',
        extra: {
          'priority': taskItem.priority.name,
          'submitted_at': taskItem.submittedAt.toIso8601String(),
        },
      );

      // Apply battery-based slowdown for non-critical tasks
      if (taskItem.priority != TaskPriority.heavy) {
        final slowdown = _batteryMonitor.getSlowdownMultiplier();
        if (slowdown < 1.0) {
          // Add delay based on battery level
          final delayMs = (100 * (1.0 - slowdown)).round();
          await Future.delayed(Duration(milliseconds: delayMs));

          await _logger.log(
            level: 'debug',
            subsystem: 'task_manager',
            message: 'Task slowed down due to low battery',
            extra: {
              'priority': taskItem.priority.name,
              'slowdown_multiplier': slowdown,
              'battery_level': _batteryMonitor.getBatteryLevel(),
            },
          );
        }
      }

      final result = await taskItem.task();
      final duration = DateTime.now().difference(startTime);

      // Record successful task execution
      _monitor.recordTaskExecution(
        durationMs: duration.inMilliseconds,
        success: true,
      );

      await _logger.log(
        level: 'debug',
        subsystem: 'task_manager',
        message: 'Task completed',
        extra: {
          'priority': taskItem.priority.name,
          'duration_ms': duration.inMilliseconds,
        },
      );

      if (!taskItem.completer.isCompleted) {
        taskItem.completer.complete(result);
      }
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime);

      // Record failed task execution
      _monitor.recordTaskExecution(
        durationMs: duration.inMilliseconds,
        success: false,
      );

      // Check if task should be retried
      final retryCount = _failedTasks[taskItem] ?? 0;
      if (retryCount < _maxRetries) {
        // Schedule retry
        _failedTasks[taskItem] = retryCount + 1;

        await _logger.log(
          level: 'warning',
          subsystem: 'task_manager',
          message: 'Task failed, scheduling retry',
          extra: {
            'priority': taskItem.priority.name,
            'retry_count': retryCount + 1,
            'max_retries': _maxRetries,
            'error': e.toString(),
          },
        );

        // Retry after delay
        Future.delayed(_retryDelay, () {
          _failedTasks.remove(taskItem);
          _queues[taskItem.priority]!.add(taskItem);
          _processNext();
        });

        return;
      }

      // Max retries reached, fail the task
      _failedTasks.remove(taskItem);

      await _logger.log(
        level: 'error',
        subsystem: 'task_manager',
        message: 'Task failed after max retries',
        extra: {
          'priority': taskItem.priority.name,
          'duration_ms': duration.inMilliseconds,
          'retry_count': retryCount,
          'error': e.toString(),
        },
      );

      if (!taskItem.completer.isCompleted) {
        taskItem.completer.completeError(e);
      }
    } finally {
      _activeTasks.remove(taskItem);
      _releaseIsolateSlot();
      _processNext();
    }
  }

  /// Pause non-critical tasks (LIGHT and MEDIUM).
  ///
  /// This is useful when the app goes to background or battery is low.
  Future<void> pauseNonCritical() async {
    if (_paused) return;

    _paused = true;
    _pauseCompleter = Completer<void>();

    await _logger.log(
      level: 'info',
      subsystem: 'task_manager',
      message: 'Paused non-critical tasks',
    );
  }

  /// Resume non-critical tasks.
  Future<void> resume() async {
    if (!_paused) return;

    _paused = false;
    if (_pauseCompleter != null && !_pauseCompleter!.isCompleted) {
      _pauseCompleter!.complete();
    }
    _pauseCompleter = null;

    await _logger.log(
      level: 'info',
      subsystem: 'task_manager',
      message: 'Resumed non-critical tasks',
    );

    // Process queued tasks
    while (_activeTasks.length < _maxConcurrentIsolates) {
      _processNext();
      if (_queues[TaskPriority.heavy]!.isEmpty &&
          _queues[TaskPriority.medium]!.isEmpty &&
          _queues[TaskPriority.light]!.isEmpty) {
        break;
      }
    }
  }

  /// Get current task statistics.
  Map<String, dynamic> getStatistics() => {
        'active_tasks': _activeTasks.length,
        'max_concurrent': _maxConcurrentIsolates,
        'queued_heavy': _queues[TaskPriority.heavy]!.length,
        'queued_medium': _queues[TaskPriority.medium]!.length,
        'queued_light': _queues[TaskPriority.light]!.length,
        'max_queue_heavy': _maxQueueSize[TaskPriority.heavy],
        'max_queue_medium': _maxQueueSize[TaskPriority.medium],
        'max_queue_light': _maxQueueSize[TaskPriority.light],
        'rejected_tasks': _rejectedTasks,
        'paused': _paused,
      };
}

/// Exception thrown when a task is rejected due to queue overflow.
class TaskRejectedException implements Exception {
  /// Creates a new TaskRejectedException.
  TaskRejectedException(
    this.message, {
    required this.priority,
    required this.queueSize,
  });

  /// Error message.
  final String message;

  /// Task priority that was rejected.
  final TaskPriority priority;

  /// Current queue size when task was rejected.
  final int queueSize;

  @override
  String toString() => message;
}

/// Internal task item representation.
class _TaskItem<T> {
  /// Creates a new task item.
  _TaskItem({
    required this.priority,
    required this.task,
    required this.completer,
    required this.submittedAt,
  });

  /// Task priority.
  final TaskPriority priority;

  /// Task function.
  final Future<T> Function() task;

  /// Completer for task result.
  final Completer<T> completer;

  /// Time when task was submitted.
  final DateTime submittedAt;
}
