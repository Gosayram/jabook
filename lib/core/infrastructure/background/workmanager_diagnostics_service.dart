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
import 'dart:convert';

import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Information about a WorkManager task execution.
class TaskExecutionInfo {
  /// Creates from JSON.
  factory TaskExecutionInfo.fromJson(Map<String, dynamic> json) =>
      TaskExecutionInfo(
        taskName: json['taskName'] as String,
        status: json['status'] as String,
        startTime: DateTime.parse(json['startTime'] as String),
        endTime: json['endTime'] != null
            ? DateTime.parse(json['endTime'] as String)
            : null,
        duration: json['duration'] != null
            ? Duration(milliseconds: json['duration'] as int)
            : null,
        expectedDelay: json['expectedDelay'] != null
            ? Duration(milliseconds: json['expectedDelay'] as int)
            : null,
        actualDelay: json['actualDelay'] != null
            ? Duration(milliseconds: json['actualDelay'] as int)
            : null,
        errorReason: json['errorReason'] as String?,
        errorMessage: json['errorMessage'] as String?,
        activeDownloadsCount: json['activeDownloadsCount'] as int?,
      );

  /// Creates a new TaskExecutionInfo.
  const TaskExecutionInfo({
    required this.taskName,
    required this.status,
    required this.startTime,
    required this.endTime,
    this.duration,
    this.expectedDelay,
    this.actualDelay,
    this.errorReason,
    this.errorMessage,
    this.activeDownloadsCount,
  });

  /// Task name/identifier.
  final String taskName;

  /// Execution status ('success', 'failed', 'cancelled').
  final String status;

  /// Task start time.
  final DateTime startTime;

  /// Task end time (or null if still running).
  final DateTime? endTime;

  /// Actual execution duration.
  final Duration? duration;

  /// Expected delay before task execution.
  final Duration? expectedDelay;

  /// Actual delay before task execution.
  final Duration? actualDelay;

  /// Error reason category (if failed).
  final String? errorReason;

  /// Error message (if failed).
  final String? errorMessage;

  /// Number of active downloads found during execution.
  final int? activeDownloadsCount;

  /// Converts to JSON for storage.
  Map<String, dynamic> toJson() => {
        'taskName': taskName,
        'status': status,
        'startTime': startTime.toIso8601String(),
        'endTime': endTime?.toIso8601String(),
        'duration': duration?.inMilliseconds,
        'expectedDelay': expectedDelay?.inMilliseconds,
        'actualDelay': actualDelay?.inMilliseconds,
        'errorReason': errorReason,
        'errorMessage': errorMessage,
        'activeDownloadsCount': activeDownloadsCount,
      };
}

/// Service for tracking and retrieving WorkManager task execution history.
///
/// This service stores execution information in SharedPreferences and provides
/// methods to retrieve the history for diagnostic purposes.
class WorkManagerDiagnosticsService {
  WorkManagerDiagnosticsService._();

  /// Factory constructor to get the singleton instance.
  factory WorkManagerDiagnosticsService() => _instance;

  /// Singleton instance
  static final WorkManagerDiagnosticsService _instance =
      WorkManagerDiagnosticsService._();

  /// Logger instance
  final StructuredLogger _logger = StructuredLogger();

  /// SharedPreferences key for storing execution history
  static const String _historyKey = 'workmanager_execution_history';

  /// Maximum number of execution records to keep
  static const int _maxHistorySize = 50;

  /// Records a task execution.
  ///
  /// This should be called after a WorkManager task completes (successfully
  /// or with an error).
  Future<void> recordExecution(TaskExecutionInfo info) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final historyJson = prefs.getStringList(_historyKey) ?? [];

      // Add new execution
      final newEntry = info.toJson();
      historyJson.insert(0, jsonEncode(newEntry));

      // Keep only the most recent executions
      if (historyJson.length > _maxHistorySize) {
        historyJson.removeRange(_maxHistorySize, historyJson.length);
      }

      await prefs.setStringList(_historyKey, historyJson);

      await _logger.log(
        level: 'debug',
        subsystem: 'workmanager_diagnostics',
        message: 'Recorded task execution',
        extra: {
          'task_name': info.taskName,
          'status': info.status,
        },
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'workmanager_diagnostics',
        message: 'Failed to record task execution',
        cause: e.toString(),
      );
    }
  }

  /// Gets the execution history.
  ///
  /// Returns a list of task executions, most recent first.
  /// The [limit] parameter limits the number of results (default: 20).
  Future<List<TaskExecutionInfo>> getHistory({int limit = 20}) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final historyJson = prefs.getStringList(_historyKey) ?? [];

      final history = <TaskExecutionInfo>[];
      for (final entryJson in historyJson.take(limit)) {
        try {
          final entry = jsonDecode(entryJson) as Map<String, dynamic>;
          history.add(TaskExecutionInfo.fromJson(entry));
        } on Exception {
          // Skip invalid entries
          continue;
        }
      }

      return history;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'workmanager_diagnostics',
        message: 'Failed to get execution history',
        cause: e.toString(),
      );
      return [];
    }
  }

  /// Gets the last execution for a specific task.
  ///
  /// Returns the most recent execution info for the given task name,
  /// or null if no execution found.
  Future<TaskExecutionInfo?> getLastExecution(String taskName) async {
    try {
      final history = await getHistory(limit: 100);
      for (final execution in history) {
        if (execution.taskName == taskName) {
          return execution;
        }
      }
      return null;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'workmanager_diagnostics',
        message: 'Failed to get last execution',
        cause: e.toString(),
      );
      return null;
    }
  }

  /// Clears the execution history.
  Future<void> clearHistory() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.remove(_historyKey);
      await _logger.log(
        level: 'info',
        subsystem: 'workmanager_diagnostics',
        message: 'Cleared execution history',
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'workmanager_diagnostics',
        message: 'Failed to clear execution history',
        cause: e.toString(),
      );
    }
  }

  /// Gets statistics about task executions.
  ///
  /// Returns a map with statistics:
  /// - totalExecutions: total number of executions
  /// - successfulExecutions: number of successful executions
  /// - failedExecutions: number of failed executions
  /// - averageDelay: average delay before execution (in minutes)
  /// - lastExecutionTime: timestamp of the last execution
  Future<Map<String, dynamic>> getStatistics() async {
    try {
      final history = await getHistory(limit: 100);

      if (history.isEmpty) {
        return {
          'totalExecutions': 0,
          'successfulExecutions': 0,
          'failedExecutions': 0,
          'averageDelayMinutes': 0.0,
          'lastExecutionTime': null,
        };
      }

      var successful = 0;
      var failed = 0;
      var totalDelayMinutes = 0.0;
      var delayCount = 0;

      for (final execution in history) {
        if (execution.status == 'success') {
          successful++;
        } else if (execution.status == 'failed') {
          failed++;
        }

        if (execution.actualDelay != null) {
          totalDelayMinutes += execution.actualDelay!.inMinutes;
          delayCount++;
        }
      }

      final averageDelay =
          delayCount > 0 ? totalDelayMinutes / delayCount : 0.0;
      final lastExecution = history.isNotEmpty ? history.first.startTime : null;

      return {
        'totalExecutions': history.length,
        'successfulExecutions': successful,
        'failedExecutions': failed,
        'averageDelayMinutes': averageDelay,
        'lastExecutionTime': lastExecution?.toIso8601String(),
      };
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'workmanager_diagnostics',
        message: 'Failed to get statistics',
        cause: e.toString(),
      );
      return {
        'totalExecutions': 0,
        'successfulExecutions': 0,
        'failedExecutions': 0,
        'averageDelayMinutes': 0.0,
        'lastExecutionTime': null,
      };
    }
  }
}
