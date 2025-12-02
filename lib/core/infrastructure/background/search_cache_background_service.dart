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

import 'package:jabook/core/data/local/database/app_database.dart';
import 'package:jabook/core/infrastructure/endpoints/endpoint_manager.dart';
import 'package:jabook/core/infrastructure/logging/environment_logger.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:jabook/core/search/full_sync_service.dart';
import 'package:jabook/core/search/models/cache_settings.dart';
import 'package:jabook/core/utils/device_info_utils.dart';
import 'package:sembast/sembast.dart';
import 'package:workmanager/workmanager.dart';

/// Background service for managing search cache updates.
///
/// This service ensures that search cache is updated periodically in the background
/// according to user settings. It uses WorkManager for periodic tasks.
class SearchCacheBackgroundService {
  /// Factory constructor to get the singleton instance.
  factory SearchCacheBackgroundService() => _instance;

  SearchCacheBackgroundService._internal();

  /// Singleton instance.
  static final SearchCacheBackgroundService _instance =
      SearchCacheBackgroundService._internal();

  final EnvironmentLogger _logger = EnvironmentLogger();
  bool _isInitialized = false;

  /// Unique task name for cache update.
  static const String cacheUpdateTaskName = 'searchCacheUpdateTask';

  /// Initializes the background service.
  ///
  /// This should be called once during app startup.
  /// Note: WorkManager should be initialized by DownloadBackgroundService first.
  Future<void> initialize() async {
    if (_isInitialized) {
      _logger.w('SearchCacheBackgroundService already initialized');
      return;
    }

    // WorkManager is already initialized by DownloadBackgroundService
    // We just mark this service as initialized
    _isInitialized = true;
    _logger.i('SearchCacheBackgroundService initialized successfully');
  }

  /// Schedules automatic cache update based on settings.
  ///
  /// This method reads cache settings and schedules the next update.
  /// If auto-update is disabled, cancels any scheduled tasks.
  Future<void> scheduleAutoUpdate() async {
    if (!_isInitialized) {
      await initialize();
    }

    try {
      final appDatabase = AppDatabase.getInstance();
      await appDatabase.ensureInitialized();
      final db = appDatabase.database;
      final settingsStore = appDatabase.searchCacheSettingsStore;
      final settingsMap = await settingsStore.record('settings').get(db);

      if (settingsMap == null) {
        _logger.d('No cache settings found, skipping auto-update scheduling');
        return;
      }

      final settings = CacheSettings.fromMap(settingsMap);

      if (!settings.autoUpdateEnabled) {
        // Cancel scheduled task if auto-update is disabled
        await Workmanager().cancelByUniqueName(cacheUpdateTaskName);
        _logger.i('Auto-update disabled, cancelled scheduled task');
        return;
      }

      // Calculate next update time
      final now = DateTime.now();
      DateTime nextUpdate;
      if (settings.nextUpdateTime != null &&
          settings.nextUpdateTime!.isAfter(now)) {
        nextUpdate = settings.nextUpdateTime!;
      } else {
        nextUpdate = now.add(settings.autoUpdateInterval);
      }

      // Schedule one-time task for next update
      final delay = nextUpdate.difference(now);
      if (delay.isNegative) {
        // Update is overdue, schedule immediately
        await _registerUpdateTask();
      } else {
        // Schedule for calculated time
        await _registerUpdateTask(delay: delay);
      }

      // Update settings with next update time
      final updatedSettings = settings.copyWith(nextUpdateTime: nextUpdate);
      await settingsStore.record('settings').put(db, updatedSettings.toMap());

      _logger.i(
        'Auto-update scheduled for ${nextUpdate.toIso8601String()} '
        '(in ${delay.inHours} hours)',
      );
    } on Exception catch (e) {
      _logger.e('Failed to schedule auto-update: $e');
    }
  }

  /// Registers a one-time task for cache update.
  Future<void> _registerUpdateTask({Duration? delay}) async {
    try {
      final deviceInfo = DeviceInfoUtils();
      final isChineseDevice = await deviceInfo.isChineseManufacturer();

      if (isChineseDevice) {
        // For Chinese devices, use more aggressive settings
        await Workmanager().registerOneOffTask(
          cacheUpdateTaskName,
          cacheUpdateTaskName,
          constraints: Constraints(
            networkType: NetworkType.connected,
            requiresBatteryNotLow: false,
            requiresDeviceIdle: false,
            requiresStorageNotLow: false,
          ),
          backoffPolicy: BackoffPolicy.exponential,
          backoffPolicyDelay: delay ?? const Duration(hours: 12),
          existingWorkPolicy: ExistingWorkPolicy.replace,
        );
      } else {
        // Standard registration
        await Workmanager().registerOneOffTask(
          cacheUpdateTaskName,
          cacheUpdateTaskName,
          constraints: Constraints(
            networkType: NetworkType.connected,
          ),
          initialDelay: delay ?? const Duration(hours: 12),
          existingWorkPolicy: ExistingWorkPolicy.replace,
        );
      }

      _logger.i('Cache update task registered');
    } on Exception catch (e) {
      _logger.e('Failed to register cache update task: $e');
      rethrow;
    }
  }

  /// Cancels scheduled cache update task.
  Future<void> cancelAutoUpdate() async {
    try {
      await Workmanager().cancelByUniqueName(cacheUpdateTaskName);
      _logger.i('Cache update task cancelled');
    } on Exception catch (e) {
      _logger.e('Failed to cancel cache update task: $e');
    }
  }
}

/// Top-level function for WorkManager callback for cache updates.
///
/// This function is called by WorkManager when the background cache update task runs.
/// It must be a top-level function (not a class method).
@pragma('vm:entry-point')
Future<bool> cacheUpdateCallbackDispatcher() async {
  final taskStartTime = DateTime.now();
  final logger = EnvironmentLogger()
    ..i('Cache update background task executed');

  try {
    // Initialize database
    final appDatabase = AppDatabase.getInstance();
    try {
      await appDatabase.initialize();
      if (!appDatabase.isInitialized) {
        logger.w('Database initialization failed in cache update task');
        return false;
      }
    } on Exception catch (e) {
      logger.e('Failed to initialize database in cache update task: $e');
      return false;
    }

    // Get cache settings
    final db = appDatabase.database;
    final settingsStore = appDatabase.searchCacheSettingsStore;
    final settingsMap = await settingsStore.record('settings').get(db);

    if (settingsMap == null) {
      logger.w('No cache settings found, skipping cache update');
      return false;
    }

    final settings = CacheSettings.fromMap(settingsMap);

    if (!settings.autoUpdateEnabled) {
      logger.i('Auto-update disabled in settings, skipping');
      return true;
    }

    // Check if update is needed
    if (settings.lastUpdateTime != null) {
      final age = DateTime.now().difference(settings.lastUpdateTime!);
      if (age < settings.autoUpdateInterval) {
        logger.i(
          'Cache is still fresh (age: ${age.inHours}h, interval: ${settings.autoUpdateInterval.inHours}h), skipping update',
        );
        // Reschedule for next interval
        await SearchCacheBackgroundService().scheduleAutoUpdate();
        return true;
      }
    }

    logger.i('Starting incremental cache update');

    // Perform incremental update
    // For now, we'll do a full sync, but in the future this can be optimized
    // to only update changed topics
    final endpointManager = EndpointManager(db, appDatabase);
    final syncService = FullSyncService(appDatabase, endpointManager);

    // Start sync (this will update progress in settings)
    await syncService.syncAllForums();

    // Update settings
    final updatedSettings = settings.copyWith(
      lastUpdateTime: DateTime.now(),
      nextUpdateTime: DateTime.now().add(settings.autoUpdateInterval),
    );
    await settingsStore.record('settings').put(db, updatedSettings.toMap());

    // Schedule next update
    await SearchCacheBackgroundService().scheduleAutoUpdate();

    final taskEndTime = DateTime.now();
    final duration = taskEndTime.difference(taskStartTime);

    logger.i(
      'Cache update completed successfully in ${duration.inMinutes} minutes',
    );

    await StructuredLogger().log(
      level: 'info',
      subsystem: 'cache_background',
      message: 'Background cache update completed',
      durationMs: duration.inMilliseconds,
    );

    return true;
  } on Exception catch (e, stackTrace) {
    logger.e(
      'Cache update background task failed: $e',
      error: e,
      stackTrace: stackTrace,
    );

    await StructuredLogger().log(
      level: 'error',
      subsystem: 'cache_background',
      message: 'Background cache update failed',
      cause: e.toString(),
    );

    // Reschedule for retry (with backoff)
    try {
      await SearchCacheBackgroundService().scheduleAutoUpdate();
    } on Exception {
      // Ignore reschedule errors
    }

    return false;
  }
}
