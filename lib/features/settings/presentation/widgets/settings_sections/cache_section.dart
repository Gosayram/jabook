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
import 'package:go_router/go_router.dart';
import 'package:jabook/core/di/providers/cache_providers.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Cache settings section widget.
class CacheSection extends ConsumerStatefulWidget {
  /// Creates a new CacheSection instance.
  const CacheSection({super.key});

  @override
  ConsumerState<CacheSection> createState() => _CacheSectionState();
}

class _CacheSectionState extends ConsumerState<CacheSection> {
  Future<Map<String, dynamic>> _loadCacheStats() async {
    final cache = ref.read(rutrackerCacheServiceProvider);
    return cache.getStatistics();
  }

  Future<void> _clearExpiredCache() async {
    final cache = ref.read(rutrackerCacheServiceProvider);
    await cache.clearExpired();
  }

  Future<void> _clearAllCache() async {
    final cache = ref.read(rutrackerCacheServiceProvider);
    await cache.clearSearchResultsCache();
    await cache.clearAllTopicDetailsCache();
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
  Widget build(BuildContext context) {
    final loc = AppLocalizations.of(context);
    return FutureBuilder<Map<String, dynamic>>(
      future: _loadCacheStats(),
      builder: (context, snapshot) {
        final stats = snapshot.data;
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              loc?.cacheStatistics ?? 'Cache Statistics',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 8),
            if (stats == null)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 8.0),
                child: LinearProgressIndicator(),
              )
            else
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _buildStatRow('Total entries', '${stats['total_entries']}'),
                  _buildStatRow(
                      'Search cache', '${stats['search_cache_size']} entries'),
                  _buildStatRow(
                      'Topic cache', '${stats['topic_cache_size']} entries'),
                  _buildStatRow('Memory usage', '${stats['memory_usage']}'),
                ],
              ),
            const SizedBox(height: 12),
            Wrap(spacing: 12, runSpacing: 8, children: [
              ElevatedButton.icon(
                onPressed: () {
                  context.push('/settings/search-cache');
                },
                icon: const Icon(Icons.settings),
                label: const Text('Smart Search Cache Settings'),
              ),
              ElevatedButton.icon(
                onPressed: () async {
                  final messenger = ScaffoldMessenger.of(context);
                  await _clearExpiredCache();
                  if (mounted) setState(() {});
                  if (mounted) {
                    messenger.showSnackBar(
                      SnackBar(
                          content: Text(loc?.cacheClearedSuccessfullyMessage ??
                              'Cache cleared successfully')),
                    );
                  }
                },
                icon: const Icon(Icons.auto_delete),
                label: Text(
                    AppLocalizations.of(context)?.clearExpiredCacheButton ??
                        'Clear Expired Cache'),
              ),
              OutlinedButton.icon(
                onPressed: () async {
                  final messenger = ScaffoldMessenger.of(context);
                  await _clearAllCache();
                  if (mounted) setState(() {});
                  if (mounted) {
                    messenger.showSnackBar(
                      SnackBar(
                          content: Text(loc?.cacheClearedSuccessfullyMessage ??
                              'Cache cleared successfully')),
                    );
                  }
                },
                icon: const Icon(Icons.delete_forever),
                label: Text(loc?.clearAllCacheButton ?? 'Clear All Cache'),
              ),
            ]),
          ],
        );
      },
    );
  }
}
