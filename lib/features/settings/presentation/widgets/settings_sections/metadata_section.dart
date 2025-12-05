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
import 'package:jabook/core/metadata/audiobook_metadata_service.dart';
import 'package:jabook/core/metadata/metadata_sync_scheduler.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Metadata section widget.
class MetadataSection extends ConsumerStatefulWidget {
  /// Creates a new MetadataSection instance.
  const MetadataSection({super.key});

  @override
  ConsumerState<MetadataSection> createState() => _MetadataSectionState();
}

class _MetadataSectionState extends ConsumerState<MetadataSection> {
  Future<Map<String, dynamic>> _loadMetadataStats() async {
    try {
      final appDatabase = ref.read(appDatabaseProvider);
      final db = await appDatabase.ensureInitialized();
      final metadataService = AudiobookMetadataService(db);
      final scheduler = MetadataSyncScheduler(db, metadataService);

      final syncStats = await scheduler.getSyncStatistics();
      final metadataStats =
          syncStats['metadata'] as Map<String, dynamic>? ?? {};

      return {
        'total': metadataStats['total'] ?? 0,
        'last_sync': syncStats['last_daily_sync'] as String?,
        'last_full_sync': syncStats['last_full_sync'] as String?,
        'is_updating': false,
      };
    } on Exception {
      return {'total': 0, 'is_updating': false};
    }
  }

  Future<void> _updateMetadata(BuildContext context) async {
    final messenger = ScaffoldMessenger.of(context);
    final localizations = AppLocalizations.of(context);

    try {
      // Show updating indicator
      setState(() {});

      final appDatabase = ref.read(appDatabaseProvider);
      final db = await appDatabase.ensureInitialized();
      final metadataService = AudiobookMetadataService(db);
      final scheduler = MetadataSyncScheduler(db, metadataService);

      messenger.showSnackBar(
        SnackBar(
          content: Text(localizations?.metadataUpdateStartedMessage ??
              'Metadata update started...'),
          duration: const Duration(seconds: 2),
        ),
      );

      // Run full sync
      final results = await scheduler.runFullSync();

      final total = results.values.fold<int>(0, (sum, count) => sum + count);

      if (mounted) {
        messenger.showSnackBar(
          SnackBar(
            content: Text(
                localizations?.metadataUpdateCompletedMessage(total) ??
                    'Update completed: collected $total records'),
            duration: const Duration(seconds: 3),
          ),
        );
        setState(() {});
      }
    } on Exception catch (e) {
      if (mounted) {
        messenger.showSnackBar(
          SnackBar(
            content: Text(localizations?.metadataUpdateError(e.toString()) ??
                'Update error: $e'),
            backgroundColor: Colors.red,
            duration: const Duration(seconds: 5),
          ),
        );
        setState(() {});
      }
    }
  }

  String _formatDateTime(String? isoString) {
    final loc = AppLocalizations.of(context);
    if (isoString == null) {
      return loc?.neverDate ?? 'Never';
    }
    try {
      final dateTime = DateTime.parse(isoString);
      final now = DateTime.now();
      final diff = now.difference(dateTime);

      if (diff.inDays > 0) {
        return loc?.daysAgo(diff.inDays) ?? '${diff.inDays} days ago';
      } else if (diff.inHours > 0) {
        return loc?.hoursAgo(diff.inHours) ?? '${diff.inHours} hours ago';
      } else if (diff.inMinutes > 0) {
        return loc?.minutesAgo(diff.inMinutes) ??
            '${diff.inMinutes} minutes ago';
      } else {
        return loc?.justNow ?? 'Just now';
      }
    } on Exception {
      return loc?.unknownDate ?? 'Unknown';
    }
  }

  Widget _buildStatRow(String label, String value) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 2.0),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              label,
              style: const TextStyle(fontWeight: FontWeight.w500),
            ),
            Text(
              value,
              style: TextStyle(
                color: Colors.grey.shade600,
                fontFamily: 'monospace',
              ),
            ),
          ],
        ),
      );

  @override
  Widget build(BuildContext context) => FutureBuilder<Map<String, dynamic>>(
        future: _loadMetadataStats(),
        builder: (context, snapshot) {
          final stats = snapshot.data;
          final isLoading = snapshot.connectionState == ConnectionState.waiting;
          final isUpdating =
              snapshot.hasData && (stats?['is_updating'] as bool? ?? false);

          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                AppLocalizations.of(context)?.metadataSectionTitle ??
                    'Audiobook Metadata',
                style: Theme.of(context).textTheme.titleLarge,
              ),
              const SizedBox(height: 8),
              Text(
                AppLocalizations.of(context)?.metadataSectionDescription ??
                    'Manage local audiobook metadata database',
                style: Theme.of(context).textTheme.bodySmall,
              ),
              const SizedBox(height: 16),
              if (stats == null && isLoading)
                const Padding(
                  padding: EdgeInsets.symmetric(vertical: 8.0),
                  child: LinearProgressIndicator(),
                )
              else if (stats != null)
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _buildStatRow(
                        AppLocalizations.of(context)?.totalRecordsLabel ??
                            'Total records',
                        '${stats['total'] ?? 0}'),
                    if (stats['last_sync'] != null)
                      _buildStatRow(
                        AppLocalizations.of(context)?.lastUpdateLabel ??
                            'Last update',
                        _formatDateTime(stats['last_sync'] as String?),
                      ),
                  ],
                ),
              const SizedBox(height: 12),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton.icon(
                  onPressed: isUpdating
                      ? null
                      : () async {
                          await _updateMetadata(context);
                          if (mounted) setState(() {});
                        },
                  icon: isUpdating
                      ? const SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.sync),
                  label: Text(isUpdating
                      ? (AppLocalizations.of(context)?.updatingText ??
                          'Updating...')
                      : (AppLocalizations.of(context)?.updateMetadataButton ??
                          'Update Metadata')),
                ),
              ),
            ],
          );
        },
      );
}
