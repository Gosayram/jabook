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
import 'package:jabook/core/favorites/favorites_provider.dart';
import 'package:jabook/core/parse/rutracker_parser.dart';
import 'package:jabook/features/search/presentation/widgets/audiobook_card.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Screen for displaying user's favorite audiobooks.
///
/// This screen shows all audiobooks that the user has marked as favorites,
/// with options to view details and remove from favorites.
class FavoritesScreen extends ConsumerStatefulWidget {
  /// Creates a new FavoritesScreen instance.
  const FavoritesScreen({super.key});

  @override
  ConsumerState<FavoritesScreen> createState() => _FavoritesScreenState();
}

class _FavoritesScreenState extends ConsumerState<FavoritesScreen> {
  Future<void> _toggleFavorite(String topicId, bool isFavorite) async {
    final notifier = ref.read(favoriteIdsProvider.notifier);
    final favoritesAsync = ref.read(favoritesListProvider);

    // Get audiobook data from current favorites list (for re-adding if needed)
    Map<String, dynamic>? audiobookMap;
    if (favoritesAsync.hasValue) {
      try {
        final audiobook = favoritesAsync.value!.firstWhere(
          (a) => a.id == topicId,
        );
        audiobookMap = _audiobookToMap(audiobook);
      } on Exception {
        // Audiobook not found in favorites - this is OK if we're removing
        audiobookMap = null;
      }
    }

    try {
      final wasAdded = await notifier.toggleFavorite(
        topicId,
        audiobookMap: audiobookMap,
      );

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              wasAdded
                  ? (AppLocalizations.of(context)?.addedToFavorites ??
                      'Added to favorites')
                  : (AppLocalizations.of(context)?.removedFromFavorites ??
                      'Removed from favorites'),
            ),
            duration: const Duration(seconds: 2),
          ),
        );
      }
    } on Exception {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              isFavorite
                  ? (AppLocalizations.of(context)
                          ?.failedToRemoveFromFavorites ??
                      'Failed to remove from favorites')
                  : (AppLocalizations.of(context)?.failedToAddToFavorites ??
                      'Failed to add to favorites'),
            ),
          ),
        );
      }
    }
  }

  Map<String, dynamic> _audiobookToMap(Audiobook audiobook) => {
        'id': audiobook.id,
        'title': audiobook.title,
        'author': audiobook.author,
        'category': audiobook.category,
        'size': audiobook.size,
        'seeders': audiobook.seeders,
        'leechers': audiobook.leechers,
        'magnetUrl': audiobook.magnetUrl,
        'coverUrl': audiobook.coverUrl,
        'performer': audiobook.performer,
        'genres': audiobook.genres,
        'addedDate': audiobook.addedDate.toIso8601String(),
        'chapters': audiobook.chapters
            .map((c) => {
                  'title': c.title,
                  'durationMs': c.durationMs,
                  'fileIndex': c.fileIndex,
                  'startByte': c.startByte,
                  'endByte': c.endByte,
                })
            .toList(),
        'duration': audiobook.duration,
        'bitrate': audiobook.bitrate,
        'audioCodec': audiobook.audioCodec,
      };

  @override
  Widget build(BuildContext context) {
    final favoritesAsync = ref.watch(favoritesListProvider);
    final favoriteIds = ref.watch(favoriteIdsProvider);

    return PopScope(
      onPopInvokedWithResult: (didPop, result) {
        if (didPop) return;
        // Allow navigation back using GoRouter
        if (context.canPop()) {
          context.pop();
        } else {
          context.go('/');
        }
      },
      child: Scaffold(
        appBar: AppBar(
          title:
              Text(AppLocalizations.of(context)?.favoritesTitle ?? 'Favorites'),
          actions: [
            if (favoritesAsync.hasValue && favoritesAsync.value!.isNotEmpty)
              IconButton(
                icon: const Icon(Icons.refresh),
                tooltip:
                    AppLocalizations.of(context)?.refreshTooltip ?? 'Refresh',
                onPressed: () {
                  ref.read(favoriteIdsProvider.notifier).refresh();
                  ref.invalidate(favoritesListProvider);
                },
              ),
          ],
        ),
        body: favoritesAsync.when(
          data: (favorites) {
            if (favorites.isEmpty) {
              return _buildEmptyState(context);
            }
            return _buildFavoritesList(context, favorites, favoriteIds);
          },
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (error, stack) => Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(
                  AppLocalizations.of(context)?.error ?? 'Error',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(height: 8),
                Text(
                  error.toString(),
                  style: Theme.of(context).textTheme.bodySmall,
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 16),
                ElevatedButton(
                  onPressed: () {
                    ref.invalidate(favoritesListProvider);
                    ref.read(favoriteIdsProvider.notifier).refresh();
                  },
                  child: Text(AppLocalizations.of(context)?.retry ?? 'Retry'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  /// Builds empty state widget when no favorites are available.
  Widget _buildEmptyState(BuildContext context) => Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              Icons.favorite_border,
              size: 64,
              color: Colors.grey.shade400,
            ),
            const SizedBox(height: 16),
            Text(
              AppLocalizations.of(context)?.noFavoritesMessage ??
                  'No favorite audiobooks',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 8),
            Text(
              AppLocalizations.of(context)?.addFavoritesHint ??
                  'Add audiobooks to favorites from search results',
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: Colors.grey.shade600,
                  ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 24),
            ElevatedButton.icon(
              onPressed: () => context.go('/search'),
              icon: const Icon(Icons.search),
              label: Text(AppLocalizations.of(context)?.goToSearchButton ??
                  'Go to Search'),
            ),
          ],
        ),
      );

  /// Builds favorites list widget.
  Widget _buildFavoritesList(
    BuildContext context,
    List<Audiobook> favorites,
    Set<String> favoriteIds,
  ) =>
      RefreshIndicator(
        onRefresh: () async {
          await ref.read(favoriteIdsProvider.notifier).refresh();
          ref.invalidate(favoritesListProvider);
        },
        child: ListView.builder(
          padding: const EdgeInsets.symmetric(vertical: 8),
          itemCount: favorites.length,
          itemBuilder: (context, index) {
            final audiobook = favorites[index];
            final topicId = audiobook.id;
            final isFavorite = favoriteIds.contains(topicId);

            return AudiobookCard(
              audiobook: _audiobookToMap(audiobook),
              onTap: () {
                context.push('/topic/$topicId');
              },
              isFavorite: isFavorite,
              onFavoriteToggle: (newState) {
                _toggleFavorite(topicId, newState);
              },
            );
          },
        ),
      );
}
