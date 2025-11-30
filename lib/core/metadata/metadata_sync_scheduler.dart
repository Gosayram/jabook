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

import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:jabook/core/metadata/audiobook_metadata_service.dart';
import 'package:sembast/sembast.dart';

/// Scheduler for managing automatic metadata synchronization.
///
/// This class handles scheduling and execution of metadata collection
/// tasks, including daily automatic updates and incremental sync logic.
class MetadataSyncScheduler {
  /// Creates a new instance of MetadataSyncScheduler.
  ///
  /// The [db] parameter is the database instance.
  /// The [metadataService] parameter is the service for metadata operations.
  MetadataSyncScheduler(this._db, this._metadataService);

  /// Database instance.
  final Database _db;

  /// Metadata service instance.
  final AudiobookMetadataService _metadataService;

  /// Store reference for scheduler state.
  final StoreRef<String, Map<String, dynamic>> _stateStore = StoreRef.main();

  /// Key for storing scheduler state.
  static const String _stateKey = 'metadata_sync_state';

  /// Daily sync time (02:00 AM).
  static const int dailySyncHour = 2;

  /// Weekly full sync day (0 = Sunday, 6 = Saturday).
  static const int weeklyFullSyncDay = 0; // Sunday

  /// Checks if daily automatic sync should run.
  ///
  /// This method checks the current time and last sync state to determine
  /// if an automatic sync should be triggered.
  ///
  /// Returns true if sync should run, false otherwise.
  Future<bool> shouldRunDailySync() async {
    final state = await _getState();
    final now = DateTime.now();

    // Check if we should run full weekly sync (once per week on Sunday)
    if (now.weekday == weeklyFullSyncDay) {
      final lastFullSync = state['last_full_sync'] as String?;
      if (lastFullSync != null) {
        final lastFullSyncDate = DateTime.parse(lastFullSync);
        final daysSinceFullSync = now.difference(lastFullSyncDate).inDays;
        if (daysSinceFullSync < 7) {
          // Full sync already done this week
          return false;
        }
      }
      // Need full sync
      return true;
    }

    // Check if we should run incremental daily sync
    final lastSync = state['last_daily_sync'] as String?;
    if (lastSync != null) {
      final lastSyncDate = DateTime.parse(lastSync);
      final hoursSinceSync = now.difference(lastSyncDate).inHours;

      // Sync if it's been more than 23 hours since last sync
      // and current time is after sync hour
      if (hoursSinceSync >= 23 && now.hour >= dailySyncHour) {
        return true;
      }
    } else {
      // Never synced, run if it's after sync hour
      if (now.hour >= dailySyncHour) {
        return true;
      }
    }

    return false;
  }

  /// Runs incremental metadata sync for all forums.
  ///
  /// This method collects metadata for all forums that need updating
  /// (no data or last update > 24 hours ago).
  ///
  /// Returns a map of forum IDs to the number of audiobooks collected.
  Future<Map<String, int>> runIncrementalSync() async {
    await StructuredLogger().log(
      level: 'info',
      subsystem: 'metadata_sync',
      message: 'Starting incremental metadata sync',
    );

    final results = <String, int>{};

    for (final forumTitle in AudiobookMetadataService.forumLabels) {
      try {
        final needsUpdate =
            await _metadataService.needsUpdateByTitle(forumTitle);
        if (needsUpdate) {
          final count =
              await _metadataService.collectMetadataForCategory(forumTitle);
          results[forumTitle] = count;

          // Add delay between categories
          await Future.delayed(const Duration(milliseconds: 1000));
        } else {
          results[forumTitle] = 0;
        }
      } on Exception catch (e) {
        await StructuredLogger().log(
          level: 'error',
          subsystem: 'metadata_sync',
          message: 'Failed to sync category $forumTitle',
          cause: e.toString(),
        );
        results[forumTitle] = 0;
      }
    }

    // Update last daily sync time
    await _updateState({'last_daily_sync': DateTime.now().toIso8601String()});

    await StructuredLogger().log(
      level: 'info',
      subsystem: 'metadata_sync',
      message: 'Completed incremental metadata sync',
      extra: {'results': results},
    );

    return results;
  }

  /// Runs full metadata sync for all forums.
  ///
  /// This method forces a complete update of metadata for all forums,
  /// regardless of when they were last updated.
  ///
  /// Returns a map of forum IDs to the number of audiobooks collected.
  Future<Map<String, int>> runFullSync() async {
    await StructuredLogger().log(
      level: 'info',
      subsystem: 'metadata_sync',
      message: 'Starting full metadata sync',
    );

    final results = await _metadataService.collectAllMetadata(force: true);

    // Update last full sync time
    await _updateState({
      'last_full_sync': DateTime.now().toIso8601String(),
      'last_daily_sync': DateTime.now().toIso8601String(),
    });

    await StructuredLogger().log(
      level: 'info',
      subsystem: 'metadata_sync',
      message: 'Completed full metadata sync',
      extra: {'results': results},
    );

    return results;
  }

  /// Runs automatic sync if conditions are met.
  ///
  /// This method checks if sync should run and executes it if needed.
  /// It determines whether to run full or incremental sync based on the
  /// day of the week.
  Future<void> runAutomaticSyncIfNeeded() async {
    if (!await shouldRunDailySync()) {
      await StructuredLogger().log(
        level: 'debug',
        subsystem: 'metadata_sync',
        message: 'Skipping automatic sync (not needed)',
      );
      return;
    }

    final now = DateTime.now();
    final isWeeklyFullSyncDay = now.weekday == weeklyFullSyncDay;

    try {
      if (isWeeklyFullSyncDay) {
        await runFullSync();
      } else {
        await runIncrementalSync();
      }
    } on Exception catch (e) {
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'metadata_sync',
        message: 'Failed to run automatic sync',
        cause: e.toString(),
      );
    }
  }

  /// Gets the current scheduler state.
  ///
  /// Returns a map with scheduler state information.
  Future<Map<String, dynamic>> _getState() async {
    final record = await _stateStore.record(_stateKey).get(_db);
    return record ?? <String, dynamic>{};
  }

  /// Updates the scheduler state.
  ///
  /// The [updates] parameter contains state fields to update.
  Future<void> _updateState(Map<String, dynamic> updates) async {
    final currentState = await _getState();
    currentState.addAll(updates);
    await _stateStore.record(_stateKey).put(_db, currentState);
  }

  /// Gets synchronization statistics.
  ///
  /// Returns a map with sync statistics including last sync times.
  Future<Map<String, dynamic>> getSyncStatistics() async {
    final state = await _getState();
    final metadataStats = await _metadataService.getStatistics();

    return {
      'last_daily_sync': state['last_daily_sync'],
      'last_full_sync': state['last_full_sync'],
      'metadata': metadataStats,
    };
  }
}
