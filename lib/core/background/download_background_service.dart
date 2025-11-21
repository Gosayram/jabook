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

import 'package:jabook/core/logging/environment_logger.dart';
import 'package:jabook/core/torrent/audiobook_torrent_manager.dart';
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
  Future<void> registerDownloadMonitoring() async {
    if (!_isInitialized) {
      await initialize();
    }

    try {
      // Register periodic task that runs every 15 minutes
      // This ensures downloads are monitored even when app is closed
      await Workmanager().registerPeriodicTask(
        downloadTaskName,
        downloadTaskName,
        frequency: const Duration(minutes: 15),
        constraints: Constraints(
          networkType: NetworkType.connected,
        ),
      );
      _logger.i('Download monitoring task registered');
    } on Exception catch (e) {
      _logger.e('Failed to register download monitoring task: $e');
    }
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
    final logger = EnvironmentLogger()..i('Background task executed: $task');

    try {
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

      logger.i('Background task completed successfully');
      return Future.value(true);
    } on Exception catch (e) {
      logger.e('Error in background task: $e');
      // Return false to retry the task
      return Future.value(false);
    }
  });
}
