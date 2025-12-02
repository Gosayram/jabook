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
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:jabook/core/search/smart_search_index.dart';
import 'package:sembast/sembast.dart';

/// Service for migrating data from old cache format to new smart cache format.
///
/// This service detects old cache entries, converts them to the new format,
/// and validates the migrated data.
class CacheMigrationService {
  /// Creates a new CacheMigrationService instance.
  CacheMigrationService(this._appDatabase);

  /// Database instance for migration operations.
  final AppDatabase _appDatabase;

  /// Logger for migration operations.
  final StructuredLogger _logger = StructuredLogger();

  /// Migration version marker key.
  static const String _migrationVersionKey = 'cache_migration_version';
  static const int _currentMigrationVersion = 1;

  /// Checks if old cache data exists and needs migration.
  ///
  /// Returns true if old cache entries are found that need migration.
  Future<bool> hasOldCacheData() async {
    try {
      final db = await _appDatabase.ensureInitialized();
      final store = _appDatabase.audiobookMetadataStore;
      final records = await store.find(db);

      if (records.isEmpty) {
        return false;
      }

      // Check if any records are missing new format fields
      for (final record in records) {
        if (_needsMigration(record.value)) {
          return true;
        }
      }

      return false;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'cache_migration',
        message: 'Failed to check for old cache data',
        cause: e.toString(),
      );
      return false;
    }
  }

  /// Checks if a record needs migration to new format.
  ///
  /// A record needs migration if it's missing new format fields like
  /// `is_stale`, `search_text`, `search_text_lower`, `keywords`.
  bool _needsMigration(Map<String, dynamic> record) {
    // Check for presence of new format fields
    final hasIsStale = record.containsKey('is_stale');
    final hasSearchText = record.containsKey('search_text');
    final hasSearchTextLower = record.containsKey('search_text_lower');
    final hasKeywords = record.containsKey('keywords');

    // If any new field is missing, record needs migration
    return !hasIsStale || !hasSearchText || !hasSearchTextLower || !hasKeywords;
  }

  /// Migrates old cache data to new format.
  ///
  /// This method:
  /// 1. Finds all records that need migration
  /// 2. Converts them to new format
  /// 3. Validates the migrated data
  /// 4. Saves the migrated records
  ///
  /// Returns the number of migrated records.
  Future<int> migrateOldCache() async {
    try {
      await _logger.log(
        level: 'info',
        subsystem: 'cache_migration',
        message: 'Starting cache migration',
      );

      final db = await _appDatabase.ensureInitialized();
      final store = _appDatabase.audiobookMetadataStore;
      final records = await store.find(db);

      if (records.isEmpty) {
        await _logger.log(
          level: 'info',
          subsystem: 'cache_migration',
          message: 'No records to migrate',
        );
        return 0;
      }

      // Find records that need migration
      final recordsToMigrate = <RecordSnapshot<String, Map<String, dynamic>>>[];
      for (final record in records) {
        if (_needsMigration(record.value)) {
          recordsToMigrate.add(record);
        }
      }

      if (recordsToMigrate.isEmpty) {
        await _logger.log(
          level: 'info',
          subsystem: 'cache_migration',
          message: 'No records need migration',
        );
        await markMigrationComplete();
        return 0;
      }

      await _logger.log(
        level: 'info',
        subsystem: 'cache_migration',
        message: 'Found records to migrate',
        extra: {'count': recordsToMigrate.length},
      );

      // Migrate records in batches
      const batchSize = 50;
      var migratedCount = 0;
      var failedCount = 0;

      for (var i = 0; i < recordsToMigrate.length; i += batchSize) {
        final batch = recordsToMigrate.skip(i).take(batchSize).toList();
        final batchResults = await _migrateBatch(batch);

        migratedCount += batchResults['migrated'] as int;
        failedCount += batchResults['failed'] as int;
      }

      // Mark migration as complete
      await markMigrationComplete();

      await _logger.log(
        level: 'info',
        subsystem: 'cache_migration',
        message: 'Cache migration completed',
        extra: {
          'migrated': migratedCount,
          'failed': failedCount,
          'total': recordsToMigrate.length,
        },
      );

      return migratedCount;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'cache_migration',
        message: 'Cache migration failed',
        cause: e.toString(),
      );
      rethrow;
    }
  }

  /// Migrates a batch of records.
  ///
  /// Returns a map with 'migrated' and 'failed' counts.
  Future<Map<String, int>> _migrateBatch(
    List<RecordSnapshot<String, Map<String, dynamic>>> batch,
  ) async {
    final db = await _appDatabase.ensureInitialized();
    final store = _appDatabase.audiobookMetadataStore;
    final searchIndex = SmartSearchIndex(_appDatabase);

    var migratedCount = 0;
    var failedCount = 0;

    // Use transaction for atomic batch migration
    await db.transaction((transaction) async {
      for (final record in batch) {
        try {
          // Convert to new format
          final migratedRecord = _convertToNewFormat(record.value);

          // Validate migrated record
          if (_validateRecord(migratedRecord)) {
            // Save migrated record
            await store.record(record.key).put(transaction, migratedRecord);

            // Index the record for search
            await searchIndex.indexAudiobook(migratedRecord);

            migratedCount++;
          } else {
            await _logger.log(
              level: 'warning',
              subsystem: 'cache_migration',
              message: 'Record validation failed, skipping',
              extra: {'topic_id': record.key},
            );
            failedCount++;
          }
        } on Exception catch (e) {
          await _logger.log(
            level: 'warning',
            subsystem: 'cache_migration',
            message: 'Failed to migrate record',
            cause: e.toString(),
            extra: {'topic_id': record.key},
          );
          failedCount++;
        }
      }
    });

    return {
      'migrated': migratedCount,
      'failed': failedCount,
    };
  }

  /// Converts an old format record to new format.
  ///
  /// Adds missing fields and normalizes existing fields.
  Map<String, dynamic> _convertToNewFormat(Map<String, dynamic> oldRecord) {
    final newRecord = Map<String, dynamic>.from(oldRecord);

    // Add is_stale field (default: false)
    if (!newRecord.containsKey('is_stale')) {
      newRecord['is_stale'] = false;
    }

    // Build search text if missing
    if (!newRecord.containsKey('search_text') ||
        !newRecord.containsKey('search_text_lower')) {
      final searchParts = <String>[
        newRecord['title'] as String? ?? '',
        newRecord['author'] as String? ?? '',
        if (newRecord['performer'] != null) newRecord['performer'] as String,
        ...(newRecord['genres'] as List<dynamic>? ?? [])
            .map((g) => g.toString()),
      ];
      final searchText = searchParts.join(' ');
      newRecord['search_text'] = searchText;
      newRecord['search_text_lower'] = searchText.toLowerCase();
    }

    // Add keywords if missing (will be extracted during indexing)
    if (!newRecord.containsKey('keywords')) {
      newRecord['keywords'] = <String>[];
    }

    // Normalize cover_url if present
    final coverUrl = newRecord['cover_url'] as String?;
    if (coverUrl != null && coverUrl.isNotEmpty) {
      // Ensure it's an absolute URL
      if (!coverUrl.startsWith('http://') && !coverUrl.startsWith('https://')) {
        // Try to normalize (basic attempt)
        if (coverUrl.startsWith('/')) {
          newRecord['cover_url'] = 'https://rutracker.org$coverUrl';
        } else if (coverUrl.startsWith('//')) {
          newRecord['cover_url'] = 'https:$coverUrl';
        }
      }
    }

    // Ensure title_lower and author_lower exist
    if (!newRecord.containsKey('title_lower')) {
      final title = newRecord['title'] as String? ?? '';
      newRecord['title_lower'] = title.toLowerCase();
    }

    if (!newRecord.containsKey('author_lower')) {
      final author = newRecord['author'] as String? ?? '';
      newRecord['author_lower'] = author.toLowerCase();
    }

    // Normalize last_updated field
    if (!newRecord.containsKey('last_updated')) {
      // Try to use last_synced if available
      final lastSynced = newRecord['last_synced'] as String?;
      if (lastSynced != null) {
        newRecord['last_updated'] = lastSynced;
      } else {
        newRecord['last_updated'] = DateTime.now().toIso8601String();
      }
    }

    // Add optional fields with defaults
    if (!newRecord.containsKey('series')) {
      newRecord['series'] = null;
    }
    if (!newRecord.containsKey('series_order')) {
      newRecord['series_order'] = null;
    }
    if (!newRecord.containsKey('parts')) {
      // Extract parts from chapters if available
      final parts = <String>[];
      final chapters = newRecord['chapters'] as List<dynamic>?;
      if (chapters != null) {
        for (final chapter in chapters) {
          if (chapter is Map<String, dynamic>) {
            final title = chapter['title'] as String?;
            if (title != null && title.isNotEmpty) {
              parts.add(title);
            }
          }
        }
      }
      newRecord['parts'] = parts;
    }

    return newRecord;
  }

  /// Validates a migrated record.
  ///
  /// Returns true if the record is valid, false otherwise.
  bool _validateRecord(Map<String, dynamic> record) {
    // Check required fields
    final topicId = record['topic_id'] as String?;
    if (topicId == null || topicId.isEmpty) {
      return false;
    }

    final title = record['title'] as String?;
    if (title == null || title.isEmpty) {
      return false;
    }

    final author = record['author'] as String?;
    if (author == null || author.isEmpty) {
      return false;
    }

    // Check that new format fields are present
    if (record['is_stale'] == null) {
      return false;
    }

    if (record['search_text'] == null || record['search_text_lower'] == null) {
      return false;
    }

    if (record['keywords'] == null) {
      return false;
    }

    // Validate date fields
    try {
      final addedDate = record['added_date'] as String?;
      if (addedDate != null) {
        DateTime.parse(addedDate);
      }

      final lastUpdated = record['last_updated'] as String?;
      if (lastUpdated != null) {
        DateTime.parse(lastUpdated);
      }
    } on Exception {
      return false;
    }

    return true;
  }

  /// Marks migration as complete.
  Future<void> markMigrationComplete() async {
    try {
      final db = await _appDatabase.ensureInitialized();
      final settingsStore = _appDatabase.searchCacheSettingsStore;
      final settingsMap = await settingsStore.record('settings').get(db);

      if (settingsMap != null) {
        final updatedSettings = Map<String, dynamic>.from(settingsMap);
        updatedSettings[_migrationVersionKey] = _currentMigrationVersion;
        await settingsStore.record('settings').put(db, updatedSettings);
      }
    } on Exception catch (e) {
      await _logger.log(
        level: 'warning',
        subsystem: 'cache_migration',
        message: 'Failed to mark migration complete',
        cause: e.toString(),
      );
    }
  }

  /// Checks if migration has been completed.
  Future<bool> isMigrationComplete() async {
    try {
      final db = await _appDatabase.ensureInitialized();
      final settingsStore = _appDatabase.searchCacheSettingsStore;
      final settingsMap = await settingsStore.record('settings').get(db);

      if (settingsMap == null) {
        return false;
      }

      final migrationVersion = settingsMap[_migrationVersionKey] as int?;
      return migrationVersion != null &&
          migrationVersion >= _currentMigrationVersion;
    } on Exception {
      return false;
    }
  }
}
