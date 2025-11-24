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

import 'package:device_info_plus/device_info_plus.dart';
import 'package:jabook/core/background/background_compatibility_checker.dart';
import 'package:jabook/core/background/workmanager_diagnostics_service.dart';
import 'package:jabook/core/logging/environment_logger.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/core/torrent/audiobook_torrent_manager.dart';
import 'package:jabook/core/utils/device_info_utils.dart';
import 'package:jabook/data/db/app_database.dart';
import 'package:workmanager/workmanager.dart';

/// Background service for managing torrent downloads.
///
/// This service ensures that downloads continue even when the app
/// is in the background or closed. It uses WorkManager for periodic
/// checks and state updates.
class DownloadBackgroundService {
  /// Factory constructor to get the singleton instance.
  factory DownloadBackgroundService() => _instance;

  DownloadBackgroundService._internal();

  /// Singleton instance.
  static final DownloadBackgroundService _instance =
      DownloadBackgroundService._internal();

  final EnvironmentLogger _logger = EnvironmentLogger();
  bool _isInitialized = false;

  /// Unique task name for download monitoring.
  static const String downloadTaskName = 'downloadMonitoringTask';

  /// Initializes the background service.
  ///
  /// This should be called once during app startup.
  Future<void> initialize() async {
    if (_isInitialized) {
      _logger.w('DownloadBackgroundService already initialized');
      return;
    }

    try {
      await Workmanager().initialize(
        callbackDispatcher,
      );
      _isInitialized = true;
      _logger.i('DownloadBackgroundService initialized successfully');
    } on Exception catch (e) {
      _logger.e('Failed to initialize DownloadBackgroundService: $e');
      // Continue without background service - downloads will still work
      // when app is in foreground
    }
  }

  /// Registers a periodic task to monitor downloads.
  ///
  /// This task runs periodically to ensure downloads continue
  /// and their state is properly saved.
  ///
  /// For Chinese manufacturers (Xiaomi, Huawei, Oppo, etc.), uses more
  /// aggressive settings to ensure WorkManager works despite aggressive
  /// battery optimization.
  Future<void> registerDownloadMonitoring() async {
    if (!_isInitialized) {
      await initialize();
    }

    try {
      // Check if device is from a Chinese manufacturer
      final deviceInfo = DeviceInfoUtils.instance;
      final isChineseDevice = await deviceInfo.isChineseManufacturer();
      final customRom = await deviceInfo.getCustomRom();

      _logger.i(
        'Registering download monitoring task (Chinese device: $isChineseDevice, ROM: $customRom)',
      );

      if (isChineseDevice) {
        // For Chinese devices, use more aggressive settings
        await _registerForChineseDevices();
      } else {
        // Standard registration for other devices
        await _registerStandard();
      }
    } on Exception catch (e) {
      _logger.e('Failed to register download monitoring task: $e');
      // Fallback to standard registration
      try {
        await _registerStandard();
      } on Exception catch (e2) {
        _logger.e('Failed to register standard download monitoring task: $e2');
      }
    }
  }

  /// Registers download monitoring with standard settings.
  Future<void> _registerStandard() async {
    try {
      // Register periodic task that runs every 5 minutes
      // This ensures downloads are monitored even when app is closed
      await Workmanager().registerPeriodicTask(
        downloadTaskName,
        downloadTaskName,
        frequency: const Duration(minutes: 5),
        constraints: Constraints(
          networkType: NetworkType.connected,
        ),
      );
      _logger.i('Download monitoring task registered (standard settings)');
    } on Exception catch (e) {
      _logger.e('Failed to register standard download monitoring task: $e');
      rethrow;
    }
  }

  /// Registers download monitoring with aggressive settings for Chinese devices.
  ///
  /// Chinese manufacturers (Xiaomi, Huawei, Oppo, etc.) have aggressive battery
  /// optimization that can kill WorkManager tasks. This method uses more aggressive
  /// settings to ensure tasks run reliably.
  Future<void> _registerForChineseDevices() async {
    try {
      // For Chinese devices, try periodic task first with more aggressive settings
      // Cancel existing task first to ensure fresh registration (helps with aggressive OEM killers)
      try {
        // Cancel existing task to force re-registration
        try {
          await Workmanager().cancelByUniqueName(downloadTaskName);
        } on Exception {
          // Ignore if task doesn't exist
        }

        await Workmanager().registerPeriodicTask(
          downloadTaskName,
          downloadTaskName,
          frequency: const Duration(minutes: 5),
          constraints: Constraints(
            networkType: NetworkType.connected,
            // Don't require battery to not be low - Chinese devices kill tasks when battery is low
            requiresBatteryNotLow: false,
            // Don't require device to be idle - run even when device is in use
            requiresDeviceIdle: false,
            // Don't require storage to not be low
            requiresStorageNotLow: false,
          ),
        );
        _logger.i(
          'Download monitoring task registered for Chinese device (periodic with aggressive settings)',
        );
        return;
      } on Exception catch (e) {
        _logger.w(
          'Failed to register periodic task for Chinese device, trying one-time task: $e',
        );
        // Fallback to one-time task with retry
      }

      // Fallback: Use one-time task with retry logic
      // This is more reliable on problematic devices
      // Use REPLACE policy to ensure task is re-registered
      await Workmanager().registerOneOffTask(
        downloadTaskName,
        downloadTaskName,
        constraints: Constraints(
          networkType: NetworkType.connected,
          requiresBatteryNotLow: false,
          requiresDeviceIdle: false,
          requiresStorageNotLow: false,
        ),
        // Use exponential backoff for retries
        backoffPolicy: BackoffPolicy.exponential,
        backoffPolicyDelay: const Duration(minutes: 5),
        existingWorkPolicy: ExistingWorkPolicy.replace,
      );
      _logger.i(
        'Download monitoring task registered for Chinese device (one-time with retry)',
      );

      // Schedule next one-time task after this one completes
      // This creates a chain of one-time tasks (more reliable than periodic on some devices)
      _scheduleNextOneTimeTask();
    } on Exception catch (e) {
      _logger.e(
          'Failed to register download monitoring task for Chinese device: $e');
      rethrow;
    }
  }

  /// Schedules the next one-time task after a delay.
  ///
  /// This creates a chain of one-time tasks which is more reliable than
  /// periodic tasks on some Chinese devices.
  void _scheduleNextOneTimeTask() {
    // The next task will be scheduled in the callback dispatcher
    // after the current task completes
    _logger
        .d('Next one-time task will be scheduled after current task completes');
  }

  /// Cancels the download monitoring task.
  Future<void> cancelDownloadMonitoring() async {
    try {
      await Workmanager().cancelByUniqueName(downloadTaskName);
      _logger.i('Download monitoring task cancelled');
    } on Exception catch (e) {
      _logger.e('Failed to cancel download monitoring task: $e');
    }
  }
}

/// Top-level function for WorkManager callback.
///
/// This function is called by WorkManager when the background task runs.
/// It must be a top-level function (not a class method).
@pragma('vm:entry-point')
void callbackDispatcher() {
  Workmanager().executeTask((task, inputData) async {
    final taskStartTime = DateTime.now();
    final logger = EnvironmentLogger()..i('Background task executed: $task');

    try {
      // Log task execution start time for diagnostics
      // Expected delay: 5 minutes for periodic tasks, or backoff delay for one-time tasks
      const expectedDelayMinutes = 5;
      logger.d(
        'WorkManager task started at ${taskStartTime.toIso8601String()}, '
        'expected delay: ~$expectedDelayMinutes minutes',
      );

      // Check if device is from a Chinese manufacturer for logging
      final deviceInfo = DeviceInfoUtils.instance;
      final isChineseDevice = await deviceInfo.isChineseManufacturer();
      final customRom = await deviceInfo.getCustomRom();

      if (isChineseDevice) {
        logger.i(
          'Background task running on Chinese device (ROM: $customRom)',
        );
      }

      // Initialize database for background task
      final appDatabase = AppDatabase();
      try {
        await appDatabase.initialize();
        if (!appDatabase.isInitialized) {
          logger.w('Database initialization failed in background task');
          return Future.value(false);
        }
        logger.d('Database initialized successfully in background task');
      } on Exception catch (e) {
        logger.e('Failed to initialize database in background task: $e');
        // Continue without database - downloads may still work but state won't be saved
      }

      // Get torrent manager instance and initialize with database
      final torrentManager = AudiobookTorrentManager();
      if (appDatabase.isInitialized) {
        try {
          await torrentManager.initialize(appDatabase.database);
          logger
              .d('TorrentManager initialized with database in background task');
        } on Exception catch (e) {
          logger.w('Failed to initialize TorrentManager with database: $e');
          // Continue without database initialization
        }
      }

      // Check if there are active downloads
      final activeDownloadsFuture = torrentManager.getActiveDownloads();
      final activeDownloads = await activeDownloadsFuture;

      if (activeDownloads.isEmpty) {
        logger.i('No active downloads, skipping background task');
        // For Chinese devices using one-time tasks, don't reschedule if no downloads
        return Future.value(true);
      }

      logger.i('Found ${activeDownloads.length} active downloads');

      // The downloads should continue automatically as they're managed
      // by TorrentTask which runs independently. This task just ensures
      // the app process stays alive and state is saved.

      // Save state for all active downloads
      for (final download in activeDownloads) {
        try {
          final downloadId = download['id'] as String?;
          if (downloadId != null) {
            // State is already saved via _saveDownloadMetadata during progress updates
            // This is just a safety check
            logger.d('Checking download state for: $downloadId');
          } else {
            logger.w('Download entry missing id field: $download');
          }
        } on Exception catch (e) {
          logger.w('Error checking download state: $e');
        }
      }

      // For Chinese devices using one-time tasks, reschedule the next task
      if (isChineseDevice) {
        try {
          // Reschedule one-time task for next run
          // Use REPLACE policy to ensure task is re-registered
          await Workmanager().registerOneOffTask(
            DownloadBackgroundService.downloadTaskName,
            DownloadBackgroundService.downloadTaskName,
            constraints: Constraints(
              networkType: NetworkType.connected,
              requiresBatteryNotLow: false,
              requiresDeviceIdle: false,
              requiresStorageNotLow: false,
            ),
            backoffPolicy: BackoffPolicy.exponential,
            backoffPolicyDelay: const Duration(minutes: 5),
            existingWorkPolicy: ExistingWorkPolicy.replace,
          );
          logger.d('Rescheduled one-time task for Chinese device');
        } on Exception catch (e) {
          logger.w('Failed to reschedule one-time task: $e');
          // Continue - task might still be scheduled by periodic registration
        }
      }

      final taskEndTime = DateTime.now();
      final actualDuration = taskEndTime.difference(taskStartTime);
      final taskDurationMinutes = actualDuration.inMinutes;

      logger.i(
        'Background task completed successfully in $taskDurationMinutes minutes',
      );

      // Record execution in diagnostics service
      try {
        final diagnosticsService = WorkManagerDiagnosticsService();
        await diagnosticsService.recordExecution(
          TaskExecutionInfo(
            taskName: task,
            status: 'success',
            startTime: taskStartTime,
            endTime: taskEndTime,
            duration: actualDuration,
            expectedDelay: const Duration(minutes: 5),
            activeDownloadsCount: activeDownloads.length,
          ),
        );
      } on Exception {
        // Ignore errors in diagnostics service
      }

      // Log timing diagnostics for WorkManager delays
      // Compare actual execution time with expected interval
      const expectedIntervalMinutes = 5;
      if (taskDurationMinutes > expectedIntervalMinutes * 2) {
        // Significant delay detected - log for compatibility checker
        try {
          final compatibilityChecker = BackgroundCompatibilityChecker();
          await compatibilityChecker.logWorkManagerDelayed(
            taskName: task,
            expectedDelay: const Duration(minutes: expectedIntervalMinutes),
            actualDelay: actualDuration,
          );
        } on Exception {
          // Ignore errors in compatibility checker
        }
      }

      return Future.value(true);
    } on Exception catch (e) {
      final taskEndTime = DateTime.now();
      final actualDuration = taskEndTime.difference(taskStartTime);

      // Determine error reason/category
      var errorReason = 'unknown';
      if (e.toString().contains('timeout') ||
          e.toString().contains('Timeout')) {
        errorReason = 'timeout';
      } else if (e.toString().contains('network') ||
          e.toString().contains('Network')) {
        errorReason = 'network';
      } else if (e.toString().contains('permission') ||
          e.toString().contains('Permission')) {
        errorReason = 'permission';
      } else if (e.toString().contains('database') ||
          e.toString().contains('Database')) {
        errorReason = 'database';
      } else if (e.toString().contains('initialization') ||
          e.toString().contains('Initialization')) {
        errorReason = 'initialization';
      }

      logger.e(
        'Error in background task after ${actualDuration.inMinutes} minutes '
        '(reason: $errorReason): $e',
      );

      // Log device info for diagnostics with error reason
      try {
        final deviceInfo = DeviceInfoUtils.instance;
        final manufacturer = await deviceInfo.getManufacturer();
        final customRom = await deviceInfo.getCustomRom();
        final romVersion = await deviceInfo.getRomVersion();
        final deviceInfoPlugin = DeviceInfoPlugin();
        final androidInfo = await deviceInfoPlugin.androidInfo;

        logger.e(
          'WorkManager task failed (Manufacturer: $manufacturer, ROM: $customRom, '
          'ROM Version: $romVersion, Android SDK: ${androidInfo.version.sdkInt}, '
          'Duration: ${actualDuration.inMinutes} minutes, Reason: $errorReason): $e',
        );

        // Record execution in diagnostics service
        try {
          final diagnosticsService = WorkManagerDiagnosticsService();
          await diagnosticsService.recordExecution(
            TaskExecutionInfo(
              taskName: task,
              status: 'failed',
              startTime: taskStartTime,
              endTime: taskEndTime,
              duration: actualDuration,
              expectedDelay: const Duration(minutes: 5),
              errorReason: errorReason,
              errorMessage: e.toString(),
            ),
          );
        } on Exception {
          // Ignore errors in diagnostics service
        }

        // Log to compatibility checker for analytics
        try {
          final structuredLogger = StructuredLogger();
          await structuredLogger.log(
            level: 'error',
            subsystem: 'compatibility',
            message: 'WorkManager task failed',
            extra: {
              'task_name': task,
              'error_reason': errorReason,
              'error_message': e.toString(),
              'manufacturer': manufacturer,
              'custom_rom': customRom,
              'rom_version': romVersion,
              'android_sdk': androidInfo.version.sdkInt,
              'android_version': androidInfo.version.release,
              'duration_minutes': actualDuration.inMinutes,
              'event_type': 'workmanager_failed',
            },
          );
        } on Exception {
          // Ignore errors in compatibility checker
        }
      } on Exception {
        // Ignore errors in logging device info
      }

      // Return false to retry the task (with exponential backoff)
      return Future.value(false);
    }
  });
}
