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

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/core/di/providers/database_providers.dart';
import 'package:jabook/core/di/providers/search_providers.dart';
import 'package:jabook/core/search/cache_migration_service.dart';
import 'package:jabook/core/search/models/cache_settings.dart';
import 'package:jabook/core/search/models/cache_status.dart';
import 'package:jabook/core/utils/app_title_utils.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Screen for managing smart search cache settings.
///
/// This screen allows users to configure cache TTL, auto-update settings,
/// view cache status, and manage cache synchronization.
class SearchCacheSettingsScreen extends ConsumerStatefulWidget {
  /// Creates a new SearchCacheSettingsScreen instance.
  const SearchCacheSettingsScreen({super.key});

  @override
  ConsumerState<SearchCacheSettingsScreen> createState() =>
      _SearchCacheSettingsScreenState();
}

class _SearchCacheSettingsScreenState
    extends ConsumerState<SearchCacheSettingsScreen> {
  bool _isLoading = false;
  CacheSettings? _settings;
  CacheStatus? _status;
  bool? _migrationComplete;
  bool _migrationInProgress = false;

  @override
  void initState() {
    super.initState();
    _loadSettings();
    _loadStatus();
    _checkMigrationStatus();

    // Periodically check migration status if in progress
    if (_migrationInProgress) {
      _startMigrationStatusPolling();
    }
  }

  void _startMigrationStatusPolling() {
    Future.delayed(const Duration(seconds: 2), () async {
      if (mounted && _migrationInProgress) {
        await _checkMigrationStatus();
        if (mounted && _migrationInProgress) {
          _startMigrationStatusPolling();
        } else if (mounted && (_migrationComplete ?? false)) {
          // Show success notification
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Cache migration completed successfully!'),
              backgroundColor: Colors.green,
              duration: Duration(seconds: 3),
            ),
          );
        }
      }
    });
  }

  Future<void> _loadSettings() async {
    setState(() {
      _isLoading = true;
    });

    try {
      final service = ref.read(smartSearchCacheServiceProvider);
      await service.initialize();
      final settings = await service.getCacheSettings();

      if (mounted) {
        setState(() {
          _settings = settings;
          _isLoading = false;
        });
      }
    } on Exception catch (e) {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              'Failed to load settings: ${e.toString()}',
            ),
          ),
        );
      }
    }
  }

  Future<void> _loadStatus() async {
    try {
      final service = ref.read(smartSearchCacheServiceProvider);
      final status = await service.getCacheStatus();

      if (mounted) {
        setState(() {
          _status = status;
        });
      }
    } on Exception {
      // Ignore errors when loading status
    }
  }

  Future<void> _checkMigrationStatus() async {
    try {
      final appDatabase = ref.read(appDatabaseProvider);
      final migrationService = CacheMigrationService(appDatabase);
      final isComplete = await migrationService.isMigrationComplete();
      final hasOldData = await migrationService.hasOldCacheData();

      if (mounted) {
        final wasInProgress = _migrationInProgress;
        setState(() {
          _migrationComplete = isComplete;
          _migrationInProgress = !isComplete && hasOldData;
        });

        // Reload status after migration completes
        if (wasInProgress && !_migrationInProgress && isComplete) {
          await _loadStatus();
        }
      }
    } on Exception {
      // Ignore errors
    }
  }

  Future<void> _updateSettings(CacheSettings newSettings) async {
    try {
      final service = ref.read(smartSearchCacheServiceProvider);
      await service.updateCacheSettings(newSettings);

      if (mounted) {
        setState(() {
          _settings = newSettings;
        });
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Settings saved'),
            backgroundColor: Colors.green,
          ),
        );
      }
    } on Exception catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Failed to save settings: ${e.toString()}'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  Future<void> _clearCache() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Clear Cache?'),
        content: const Text(
          'Are you sure you want to clear all cached search data? This action cannot be undone.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(AppLocalizations.of(context)?.cancel ?? 'Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            style: TextButton.styleFrom(foregroundColor: Colors.red),
            child: const Text('Clear'),
          ),
        ],
      ),
    );

    if (confirmed ?? false) {
      setState(() {
        _isLoading = true;
      });

      try {
        final service = ref.read(smartSearchCacheServiceProvider);
        await service.clearCache();
        await _loadStatus();

        if (mounted) {
          setState(() {
            _isLoading = false;
          });
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Cache cleared successfully'),
              backgroundColor: Colors.green,
            ),
          );
        }
      } on Exception catch (e) {
        if (mounted) {
          setState(() {
            _isLoading = false;
          });
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Failed to clear cache: ${e.toString()}'),
              backgroundColor: Colors.red,
            ),
          );
        }
      }
    }
  }

  Future<void> _startSync() async {
    setState(() {
      _isLoading = true;
    });

    try {
      final service = ref.read(smartSearchCacheServiceProvider);
      await service.startFullSync();
      await _loadStatus();

      if (mounted) {
        setState(() {
          _isLoading = false;
        });
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Synchronization started'),
            backgroundColor: Colors.green,
          ),
        );
      }
    } on UnimplementedError {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content:
                Text('Synchronization will be available in the next update'),
            backgroundColor: Colors.orange,
          ),
        );
      }
    } on Exception catch (e) {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Failed to start sync: ${e.toString()}'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  String _formatDuration(Duration duration) {
    if (duration.inDays > 0) {
      return '${duration.inDays} ${duration.inDays == 1 ? 'day' : 'days'}';
    } else if (duration.inHours > 0) {
      return '${duration.inHours} ${duration.inHours == 1 ? 'hour' : 'hours'}';
    } else {
      return '${duration.inMinutes} ${duration.inMinutes == 1 ? 'minute' : 'minutes'}';
    }
  }

  String _formatBytes(int? bytes) {
    if (bytes == null) return 'Unknown';
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    if (bytes < 1024 * 1024 * 1024) {
      return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
    }
    return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(1)} GB';
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
          title: Text('Search Cache Settings'.withFlavorSuffix()),
        ),
        body: _isLoading && _settings == null
            ? const Center(child: CircularProgressIndicator())
            : RefreshIndicator(
                onRefresh: () async {
                  await _loadSettings();
                  await _loadStatus();
                  await _checkMigrationStatus();
                },
                child: ListView(
                  padding: const EdgeInsets.all(16.0),
                  children: [
                    // Migration Status Section
                    if (_migrationInProgress || _migrationComplete != null)
                      _buildMigrationSection(context),

                    const SizedBox(height: 24),

                    // Cache Status Section
                    if (_status != null) _buildStatusSection(context, _status!),

                    const SizedBox(height: 24),

                    // Cache Settings Section
                    if (_settings != null)
                      _buildSettingsSection(context, _settings!),

                    const SizedBox(height: 24),

                    // Actions Section
                    _buildActionsSection(context),
                  ],
                ),
              ),
      );

  Widget _buildStatusSection(BuildContext context, CacheStatus status) => Card(
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Cache Status',
                style: Theme.of(context).textTheme.titleLarge,
              ),
              const SizedBox(height: 16),
              _buildStatusRow(
                context,
                'Cached Books',
                '${status.totalCachedBooks}',
              ),
              _buildStatusRow(
                context,
                'Cache Size',
                _formatBytes(status.cacheSizeBytes),
              ),
              if (status.lastSyncTime != null)
                _buildStatusRow(
                  context,
                  'Last Sync',
                  _formatDateTime(status.lastSyncTime!),
                ),
              _buildStatusRow(
                context,
                'Status',
                status.isStale ? 'Stale' : 'Valid',
                statusColor: status.isStale ? Colors.orange : Colors.green,
              ),
              if (status.syncInProgress && status.syncProgress != null) ...[
                const SizedBox(height: 16),
                Text(
                  'Synchronization in progress',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(height: 8),
                LinearProgressIndicator(
                  value: status.syncProgress!.progressPercent / 100,
                ),
                const SizedBox(height: 8),
                Text(
                  '${status.syncProgress!.completedTopics} / ${status.syncProgress!.totalTopics} topics',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ],
            ],
          ),
        ),
      );

  Widget _buildMigrationSection(BuildContext context) => Card(
        color: _migrationInProgress
            ? Colors.orange.withValues(alpha: 0.1)
            : (_migrationComplete ?? false
                ? Colors.green.withValues(alpha: 0.1)
                : null),
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(
                    _migrationInProgress
                        ? Icons.sync
                        : (_migrationComplete ?? false
                            ? Icons.check_circle
                            : Icons.info),
                    color: _migrationInProgress
                        ? Colors.orange
                        : (_migrationComplete ?? false
                            ? Colors.green
                            : Colors.blue),
                  ),
                  const SizedBox(width: 8),
                  Text(
                    'Migration Status',
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                ],
              ),
              const SizedBox(height: 16),
              if (_migrationInProgress) ...[
                const LinearProgressIndicator(),
                const SizedBox(height: 8),
                Text(
                  'Migrating old cache data to new format...',
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
                const SizedBox(height: 4),
                Text(
                  'This may take a few moments. Please wait.',
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: Colors.grey,
                      ),
                ),
              ] else if (_migrationComplete ?? false) ...[
                Row(
                  children: [
                    const Icon(Icons.check_circle,
                        color: Colors.green, size: 20),
                    const SizedBox(width: 8),
                    Text(
                      'Migration completed successfully',
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                            color: Colors.green,
                            fontWeight: FontWeight.bold,
                          ),
                    ),
                  ],
                ),
              ] else if (_migrationComplete == false) ...[
                Text(
                  'No migration needed - cache is already in new format',
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
              ],
            ],
          ),
        ),
      );

  Widget _buildStatusRow(
    BuildContext context,
    String label,
    String value, {
    Color? statusColor,
  }) =>
      Padding(
        padding: const EdgeInsets.symmetric(vertical: 4.0),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              label,
              style: Theme.of(context).textTheme.bodyMedium,
            ),
            Text(
              value,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                    color: statusColor,
                  ),
            ),
          ],
        ),
      );

  Widget _buildSettingsSection(BuildContext context, CacheSettings settings) =>
      Card(
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Cache Settings',
                style: Theme.of(context).textTheme.titleLarge,
              ),
              const SizedBox(height: 16),
              // Cache TTL Slider
              Text(
                'Cache Time to Live: ${_formatDuration(settings.cacheTTL)}',
                style: Theme.of(context).textTheme.bodyMedium,
              ),
              Slider(
                value: settings.cacheTTL.inHours.toDouble().clamp(12, 720),
                min: 12,
                max: 720, // 30 days
                divisions: 59,
                label: _formatDuration(settings.cacheTTL),
                onChanged: (value) {
                  final newTTL = Duration(hours: value.toInt());
                  _updateSettings(settings.copyWith(cacheTTL: newTTL));
                },
              ),
              const SizedBox(height: 16),
              // Auto-update toggle
              SwitchListTile(
                title: const Text('Automatic Updates'),
                subtitle: const Text(
                  'Automatically update cache in the background',
                ),
                value: settings.autoUpdateEnabled,
                onChanged: (value) {
                  _updateSettings(settings.copyWith(autoUpdateEnabled: value));
                },
              ),
              if (settings.autoUpdateEnabled) ...[
                const SizedBox(height: 8),
                Text(
                  'Update Interval: ${_formatDuration(settings.autoUpdateInterval)}',
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
                Slider(
                  value: settings.autoUpdateInterval.inHours
                      .toDouble()
                      .clamp(12, 168),
                  min: 12,
                  max: 168, // 7 days
                  divisions: 13,
                  label: _formatDuration(settings.autoUpdateInterval),
                  onChanged: (value) {
                    final newInterval = Duration(hours: value.toInt());
                    _updateSettings(
                      settings.copyWith(autoUpdateInterval: newInterval),
                    );
                  },
                ),
              ],
            ],
          ),
        ),
      );

  Widget _buildActionsSection(BuildContext context) => Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          ElevatedButton.icon(
            onPressed: _isLoading ? null : _startSync,
            icon: _isLoading
                ? const SizedBox(
                    width: 16,
                    height: 16,
                    child: CircularProgressIndicator(),
                  )
                : const Icon(Icons.sync),
            label: const Text('Update Cache Now'),
          ),
          const SizedBox(height: 8),
          OutlinedButton.icon(
            onPressed: _isLoading ? null : _clearCache,
            icon: const Icon(Icons.delete_outline),
            label: const Text('Clear Cache'),
            style: OutlinedButton.styleFrom(foregroundColor: Colors.red),
          ),
        ],
      );

  String _formatDateTime(DateTime dateTime) =>
      '${dateTime.day}.${dateTime.month}.${dateTime.year} '
      '${dateTime.hour.toString().padLeft(2, '0')}:'
      '${dateTime.minute.toString().padLeft(2, '0')}';
}
