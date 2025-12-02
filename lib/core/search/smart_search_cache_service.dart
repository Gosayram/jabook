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
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:jabook/core/search/cache_migration_service.dart';
import 'package:jabook/core/search/full_sync_service.dart';
import 'package:jabook/core/search/models/cache_settings.dart';
import 'package:jabook/core/search/models/cache_status.dart';
import 'package:jabook/core/search/smart_search_index.dart';
import 'package:jabook/core/utils/safe_async.dart';
import 'package:sembast/sembast.dart';

/// Service for managing smart search cache.
///
/// This service provides functionality to:
/// - Initialize and manage cache settings
/// - Start full synchronization of all RuTracker topics
/// - Search through cached metadata
/// - Schedule automatic cache updates
class SmartSearchCacheService {
  /// Creates a new SmartSearchCacheService instance.
  SmartSearchCacheService(
    this._appDatabase,
    this._endpointManager,
  );

  /// Database instance for cache operations.
  final AppDatabase _appDatabase;

  /// Endpoint manager for building URLs.
  final EndpointManager _endpointManager;

  /// Store reference for cache settings.
  static const String _settingsKey = 'settings';

  /// Logger for cache operations.
  final StructuredLogger _logger = StructuredLogger();

  /// Whether the service has been initialized.
  bool _isInitialized = false;

  /// Full sync service instance.
  FullSyncService? _fullSyncService;

  /// Search index instance.
  SmartSearchIndex? _searchIndex;

  /// Migration service instance.
  CacheMigrationService? _migrationService;

  /// Gets or creates FullSyncService instance.
  FullSyncService get _syncService {
    _fullSyncService ??= FullSyncService(_appDatabase, _endpointManager);
    return _fullSyncService!;
  }

  /// Gets or creates SmartSearchIndex instance.
  SmartSearchIndex get _index {
    _searchIndex ??= SmartSearchIndex(_appDatabase);
    return _searchIndex!;
  }

  /// Gets or creates CacheMigrationService instance.
  CacheMigrationService get _migration {
    _migrationService ??= CacheMigrationService(_appDatabase);
    return _migrationService!;
  }

  /// Initializes the cache service.
  ///
  /// Loads settings from database or creates default settings if none exist.
  Future<void> initialize() async {
    if (_isInitialized) {
      return;
    }

    try {
      await _appDatabase.ensureInitialized();
      final db = _appDatabase.database;
      final store = _appDatabase.searchCacheSettingsStore;

      // Check if settings exist
      final existingSettings = await store.record(_settingsKey).get(db);
      if (existingSettings == null) {
        // Create default settings
        final defaultSettings = CacheSettings.standard();
        await store.record(_settingsKey).put(db, defaultSettings.toMap());

        await _logger.log(
          level: 'info',
          subsystem: 'search_cache',
          message: 'Initialized cache service with default settings',
        );
      } else {
        await _logger.log(
          level: 'info',
          subsystem: 'search_cache',
          message: 'Initialized cache service with existing settings',
        );
      }

      _isInitialized = true;

      // Check and run migration if needed
      safeUnawaited(_runMigrationIfNeeded());
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'search_cache',
        message: 'Failed to initialize cache service',
        cause: e.toString(),
      );
      rethrow;
    }
  }

  /// Runs migration if old cache data is detected.
  Future<void> _runMigrationIfNeeded() async {
    try {
      // Check if migration is already complete
      final isComplete = await _migration.isMigrationComplete();
      if (isComplete) {
        await _logger.log(
          level: 'debug',
          subsystem: 'search_cache',
          message: 'Migration already complete, skipping',
        );
        return;
      }

      // Check if old cache data exists
      final hasOldData = await _migration.hasOldCacheData();
      if (!hasOldData) {
        await _logger.log(
          level: 'debug',
          subsystem: 'search_cache',
          message: 'No old cache data found, skipping migration',
        );
        await _migration.markMigrationComplete();
        return;
      }

      // Run migration
      await _logger.log(
        level: 'info',
        subsystem: 'search_cache',
        message: 'Old cache data detected, starting migration',
      );

      final migratedCount = await _migration.migrateOldCache();

      await _logger.log(
        level: 'info',
        subsystem: 'search_cache',
        message: 'Migration completed',
        extra: {'migrated_count': migratedCount},
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'warning',
        subsystem: 'search_cache',
        message: 'Migration failed, will retry on next initialization',
        cause: e.toString(),
      );
      // Don't rethrow - migration failure shouldn't block initialization
    }
  }

  /// Gets current cache settings.
  ///
  /// Returns default settings if none are stored.
  Future<CacheSettings> getCacheSettings() async {
    await _ensureInitialized();

    try {
      final db = _appDatabase.database;
      final store = _appDatabase.searchCacheSettingsStore;
      final settingsMap = await store.record(_settingsKey).get(db);

      if (settingsMap == null) {
        return CacheSettings.standard();
      }

      return CacheSettings.fromMap(settingsMap);
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'search_cache',
        message: 'Failed to get cache settings',
        cause: e.toString(),
      );
      // Return default settings on error
      return CacheSettings.standard();
    }
  }

  /// Updates cache settings.
  Future<void> updateCacheSettings(CacheSettings settings) async {
    await _ensureInitialized();

    try {
      final db = _appDatabase.database;
      final store = _appDatabase.searchCacheSettingsStore;
      await store.record(_settingsKey).put(db, settings.toMap());

      await _logger.log(
        level: 'info',
        subsystem: 'search_cache',
        message: 'Updated cache settings',
        extra: {
          'cache_ttl_hours': settings.cacheTTL.inHours,
          'auto_update_enabled': settings.autoUpdateEnabled,
        },
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'search_cache',
        message: 'Failed to update cache settings',
        cause: e.toString(),
      );
      rethrow;
    }
  }

  /// Gets current cache status.
  Future<CacheStatus> getCacheStatus() async {
    await _ensureInitialized();

    try {
      final db = _appDatabase.database;
      final metadataStore = _appDatabase.audiobookMetadataStore;
      final settingsStore = _appDatabase.searchCacheSettingsStore;

      // Count cached books
      final records = await metadataStore.find(db);
      final totalCachedBooks = records.length;

      // Get settings to check if cache is stale
      final settingsMap = await settingsStore.record(_settingsKey).get(db);
      final settings = settingsMap != null
          ? CacheSettings.fromMap(settingsMap)
          : CacheSettings.standard();

      // Check if cache is stale
      var isStale = false;
      DateTime? lastSyncTime;
      if (settings.lastUpdateTime != null) {
        lastSyncTime = settings.lastUpdateTime;
        final age = DateTime.now().difference(settings.lastUpdateTime!);
        isStale = age > settings.cacheTTL;
      } else {
        // No sync yet - consider stale if empty, valid if has data
        isStale = totalCachedBooks == 0;
      }

      // Check if sync is in progress
      final syncInProgress = settingsMap?['sync_in_progress'] as bool? ?? false;
      SyncProgress? syncProgress;
      if (syncInProgress && settingsMap?['last_sync_progress'] != null) {
        final progressMap =
            settingsMap!['last_sync_progress'] as Map<String, dynamic>;
        syncProgress = SyncProgress(
          totalForums: progressMap['total_forums'] as int? ?? 0,
          completedForums: progressMap['completed_forums'] as int? ?? 0,
          totalTopics: progressMap['total_topics'] as int? ?? 0,
          completedTopics: progressMap['completed_topics'] as int? ?? 0,
          currentForum: progressMap['current_forum'] as String?,
          currentTopic: progressMap['current_topic'] as String?,
          estimatedCompletionTime:
              progressMap['estimated_completion_time'] != null
                  ? DateTime.parse(
                      progressMap['estimated_completion_time'] as String)
                  : null,
        );
      }

      return CacheStatus(
        totalCachedBooks: totalCachedBooks,
        isStale: isStale,
        lastSyncTime: lastSyncTime,
        syncInProgress: syncInProgress,
        syncProgress: syncProgress,
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'search_cache',
        message: 'Failed to get cache status',
        cause: e.toString(),
      );
      // Return empty status on error
      return CacheStatus(
        totalCachedBooks: 0,
        isStale: true,
      );
    }
  }

  /// Starts full synchronization of all RuTracker topics.
  ///
  /// This method starts background synchronization and updates progress
  /// through the progress stream. It also updates cache settings to track
  /// sync status.
  Future<void> startFullSync() async {
    await _ensureInitialized();

    try {
      // Update settings to mark sync as in progress
      final db = _appDatabase.database;
      final settingsStore = _appDatabase.searchCacheSettingsStore;
      final settingsMap = await settingsStore.record(_settingsKey).get(db);
      if (settingsMap != null) {
        final updatedSettings = CacheSettings.fromMap(settingsMap);
        final updatedMap = updatedSettings.toMap();
        updatedMap['sync_in_progress'] = true;
        await settingsStore.record(_settingsKey).put(db, updatedMap);
      }

      // Listen to progress updates
      _syncService.watchProgress().listen((progress) {
        // Update progress in settings
        safeUnawaited(_updateSyncProgress(progress));
      });

      // Start sync in background
      safeUnawaited(_syncService.syncAllForums().then((_) async {
        // Mark sync as complete
        final settingsMap2 = await settingsStore.record(_settingsKey).get(db);
        if (settingsMap2 != null) {
          final settings = CacheSettings.fromMap(settingsMap2);
          final updatedSettings = settings.copyWith(
            lastUpdateTime: DateTime.now(),
            nextUpdateTime: settings.autoUpdateEnabled
                ? DateTime.now().add(settings.autoUpdateInterval)
                : null,
          );
          final updatedMap = updatedSettings.toMap();
          updatedMap['sync_in_progress'] = false;
          updatedMap['last_sync_progress'] = null;
          await settingsStore.record(_settingsKey).put(db, updatedMap);
        }
      }));

      await _logger.log(
        level: 'info',
        subsystem: 'search_cache',
        message: 'Full sync started',
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'search_cache',
        message: 'Failed to start full sync',
        cause: e.toString(),
      );
      rethrow;
    }
  }

  /// Updates sync progress in settings.
  Future<void> _updateSyncProgress(SyncProgress progress) async {
    try {
      final db = _appDatabase.database;
      final settingsStore = _appDatabase.searchCacheSettingsStore;
      final settingsMap = await settingsStore.record(_settingsKey).get(db);
      if (settingsMap != null) {
        final updatedMap = Map<String, dynamic>.from(settingsMap);
        updatedMap['last_sync_progress'] = {
          'total_forums': progress.totalForums,
          'completed_forums': progress.completedForums,
          'total_topics': progress.totalTopics,
          'completed_topics': progress.completedTopics,
          'current_forum': progress.currentForum,
          'current_topic': progress.currentTopic,
          'estimated_completion_time':
              progress.estimatedCompletionTime?.toIso8601String(),
        };
        await settingsStore.record(_settingsKey).put(db, updatedMap);
      }
    } on Exception {
      // Ignore errors when updating progress
    }
  }

  /// Synchronizes a specific forum.
  Future<void> syncForum(int forumId, String forumName) async {
    await _ensureInitialized();

    try {
      await _logger.log(
        level: 'info',
        subsystem: 'search_cache',
        message: 'Forum sync started',
        extra: {
          'forum_id': forumId,
          'forum_name': forumName,
        },
      );

      await _syncService.syncForumTopics(forumId, forumName);

      await _logger.log(
        level: 'info',
        subsystem: 'search_cache',
        message: 'Forum sync completed',
        extra: {
          'forum_id': forumId,
          'forum_name': forumName,
        },
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'search_cache',
        message: 'Failed to sync forum',
        cause: e.toString(),
        extra: {
          'forum_id': forumId,
          'forum_name': forumName,
        },
      );
      rethrow;
    }
  }

  /// Searches through cached metadata using smart search index.
  ///
  /// The [query] parameter is the search query string.
  /// The [limit] parameter limits the number of results (default: 100).
  /// The [categoryFilter] parameter optionally filters by category.
  ///
  /// Returns a list of audiobook metadata maps, sorted by relevance.
  Future<List<Map<String, dynamic>>> search(
    String query, {
    int limit = 100,
    String? categoryFilter,
  }) async {
    await _ensureInitialized();

    try {
      await _logger.log(
        level: 'info',
        subsystem: 'search_cache',
        message: 'Cache search requested',
        extra: {
          'query': query,
          'limit': limit,
          'category_filter': categoryFilter,
        },
      );

      // Check if cache is valid
      final status = await getCacheStatus();
      if (status.isEmpty) {
        await _logger.log(
          level: 'info',
          subsystem: 'search_cache',
          message: 'Cache is empty, returning empty results',
        );
        return [];
      }

      // Perform smart search
      final results = await _index.search(
        query,
        limit: limit,
        categoryFilter: categoryFilter,
      );

      await _logger.log(
        level: 'info',
        subsystem: 'search_cache',
        message: 'Cache search completed',
        extra: {
          'query': query,
          'results_count': results.length,
        },
      );

      return results;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'search_cache',
        message: 'Failed to perform cache search',
        cause: e.toString(),
        extra: {'query': query},
      );
      return [];
    }
  }

  /// Cleans up stale data from cache.
  ///
  /// Removes entries that are marked as stale or haven't been updated
  /// for longer than the cache TTL.
  Future<int> cleanupStaleData() async {
    await _ensureInitialized();

    try {
      final db = _appDatabase.database;
      final store = _appDatabase.audiobookMetadataStore;

      // Get cache settings to determine TTL
      final settingsStore = _appDatabase.searchCacheSettingsStore;
      final settingsMap = await settingsStore.record(_settingsKey).get(db);
      final settings = settingsMap != null
          ? CacheSettings.fromMap(settingsMap)
          : CacheSettings.standard();

      final ttlThreshold = DateTime.now().subtract(settings.cacheTTL);

      // Find stale records
      final finder = Finder(
        filter: Filter.or([
          Filter.equals('is_stale', true),
          Filter.custom((record) {
            final lastUpdated = record['last_updated'] as String?;
            if (lastUpdated == null) return true; // No update time = stale
            try {
              final updateTime = DateTime.parse(lastUpdated);
              return updateTime.isBefore(ttlThreshold);
            } on Exception {
              return true; // Invalid date = stale
            }
          }),
        ]),
      );

      final staleRecords = await store.find(db, finder: finder);
      final staleCount = staleRecords.length;

      if (staleCount > 0) {
        // Delete stale records in batches
        const batchSize = 100;
        for (var i = 0; i < staleRecords.length; i += batchSize) {
          final batch = staleRecords.skip(i).take(batchSize);
          await db.transaction((transaction) async {
            for (final record in batch) {
              await store.record(record.key).delete(transaction);
            }
          });
        }

        await _logger.log(
          level: 'info',
          subsystem: 'search_cache',
          message: 'Stale data cleaned up',
          extra: {'removed_count': staleCount},
        );
      }

      return staleCount;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'search_cache',
        message: 'Failed to cleanup stale data',
        cause: e.toString(),
      );
      return 0;
    }
  }

  /// Clears all cached metadata.
  Future<void> clearCache() async {
    await _ensureInitialized();

    try {
      final db = _appDatabase.database;
      final store = _appDatabase.audiobookMetadataStore;

      // Delete all records
      await store.delete(db);

      // Reset sync status in settings
      final settingsStore = _appDatabase.searchCacheSettingsStore;
      final settingsMap = await settingsStore.record(_settingsKey).get(db);
      if (settingsMap != null) {
        final updatedSettings = CacheSettings.fromMap(settingsMap).copyWith();
        await settingsStore
            .record(_settingsKey)
            .put(db, updatedSettings.toMap());
      }

      await _logger.log(
        level: 'info',
        subsystem: 'search_cache',
        message: 'Cache cleared successfully',
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'search_cache',
        message: 'Failed to clear cache',
        cause: e.toString(),
      );
      rethrow;
    }
  }

  /// Schedules automatic cache update.
  ///
  /// This is a placeholder that will be implemented in Stage 4.
  Future<void> scheduleAutoUpdate() async {
    await _ensureInitialized();

    await _logger.log(
      level: 'info',
      subsystem: 'search_cache',
      message: 'Auto-update scheduling requested (not yet implemented)',
    );

    // TODO: Implement in Stage 4 with WorkManager
  }

  /// Ensures service is initialized.
  Future<void> _ensureInitialized() async {
    if (!_isInitialized) {
      await initialize();
    }
  }
}
