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
  bool _isSelectionMode = false;
  final Set<String> _selectedIds = {};

  void _toggleSelectionMode() {
    setState(() {
      _isSelectionMode = !_isSelectionMode;
      if (!_isSelectionMode) {
        _selectedIds.clear();
      }
    });
  }

  void _toggleSelection(String topicId) {
    setState(() {
      if (_selectedIds.contains(topicId)) {
        _selectedIds.remove(topicId);
      } else {
        _selectedIds.add(topicId);
      }
    });
  }

  Future<void> _clearSelectedFavorites() async {
    if (_selectedIds.isEmpty) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              AppLocalizations.of(context)?.noFavoritesSelected ??
                  'No favorites selected',
            ),
          ),
        );
      }
      return;
    }

    final selectedCount = _selectedIds.length;
    final selectedIdsList = _selectedIds.toList();
    final notifier = ref.read(favoriteIdsProvider.notifier);
    try {
      await notifier.removeMultiple(selectedIdsList);
      ref.invalidate(favoritesListProvider);
      setState(() {
        _selectedIds.clear();
        _isSelectionMode = false;
      });

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              AppLocalizations.of(context)?.favoritesDeleted(selectedCount) ??
                  '$selectedCount favorite(s) deleted',
            ),
            backgroundColor: Colors.green,
          ),
        );
      }
    } on Exception {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              AppLocalizations.of(context)?.error ?? 'Error',
            ),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  Future<void> _clearAllFavorites() async {
    final localizations = AppLocalizations.of(context);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(
          localizations?.clearAllFavoritesTitle ?? 'Clear All Favorites?',
        ),
        content: Text(
          localizations?.clearAllFavoritesMessage ??
              'This will remove all favorite audiobooks. This action cannot be undone.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: Text(localizations?.cancel ?? 'Cancel'),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: Theme.of(context).colorScheme.error,
              foregroundColor: Theme.of(context).colorScheme.onError,
            ),
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: Text(localizations?.clearAllFavorites ?? 'Clear All'),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    final notifier = ref.read(favoriteIdsProvider.notifier);
    try {
      await notifier.clearAll();
      ref.invalidate(favoritesListProvider);
      setState(() {
        _selectedIds.clear();
        _isSelectionMode = false;
      });

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              localizations?.favoritesCleared ?? 'Favorites cleared',
            ),
            backgroundColor: Colors.green,
          ),
        );
      }
    } on Exception {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              localizations?.error ?? 'Error',
            ),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

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
      canPop: false,
      onPopInvokedWithResult: (didPop, result) {
        if (didPop) {
          return;
        }
        // Favorites screen should navigate back to Library tab
        try {
          context.go('/');
        } on Exception {
          // Fallback to pop if go fails
          if (context.canPop()) {
            context.pop();
          }
        }
      },
      child: Scaffold(
        appBar: AppBar(
          title: _isSelectionMode
              ? Text(
                  '${_selectedIds.length} ${AppLocalizations.of(context)?.selected ?? 'selected'}',
                )
              : Text(
                  AppLocalizations.of(context)?.favoritesTitle ?? 'Favorites',
                ),
          leading: _isSelectionMode
              ? IconButton(
                  icon: const Icon(Icons.close),
                  onPressed: _toggleSelectionMode,
                  tooltip: AppLocalizations.of(context)?.cancel ?? 'Cancel',
                )
              : null,
          actions: [
            if (favoritesAsync.hasValue &&
                favoritesAsync.value!.isNotEmpty) ...[
              if (_isSelectionMode) ...[
                if (_selectedIds.isNotEmpty)
                  IconButton(
                    icon: const Icon(Icons.delete),
                    tooltip:
                        AppLocalizations.of(context)?.clearSelectedFavorites ??
                            'Delete Selected',
                    onPressed: _clearSelectedFavorites,
                  ),
              ] else ...[
                IconButton(
                  icon: const Icon(Icons.refresh),
                  tooltip:
                      AppLocalizations.of(context)?.refreshTooltip ?? 'Refresh',
                  onPressed: () {
                    ref.read(favoriteIdsProvider.notifier).refresh();
                    ref.invalidate(favoritesListProvider);
                  },
                ),
                PopupMenuButton<String>(
                  icon: const Icon(Icons.more_vert),
                  onSelected: (value) {
                    if (value == 'select') {
                      _toggleSelectionMode();
                    } else if (value == 'clear_all') {
                      _clearAllFavorites();
                    }
                  },
                  itemBuilder: (context) => [
                    PopupMenuItem(
                      value: 'select',
                      child: Row(
                        children: [
                          const Icon(Icons.checklist),
                          const SizedBox(width: 8),
                          Text(
                            AppLocalizations.of(context)?.selectFavorites ??
                                'Select',
                          ),
                        ],
                      ),
                    ),
                    PopupMenuItem(
                      value: 'clear_all',
                      child: Row(
                        children: [
                          const Icon(Icons.delete_sweep, color: Colors.red),
                          const SizedBox(width: 8),
                          Text(
                            AppLocalizations.of(context)?.clearAllFavorites ??
                                'Clear All',
                            style: const TextStyle(color: Colors.red),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ],
            ],
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

            return ListTile(
              leading: _isSelectionMode
                  ? Checkbox(
                      value: _selectedIds.contains(topicId),
                      onChanged: (_) => _toggleSelection(topicId),
                    )
                  : null,
              title: Text(audiobook.title),
              subtitle:
                  audiobook.author.isNotEmpty ? Text(audiobook.author) : null,
              trailing: _isSelectionMode
                  ? null
                  : IconButton(
                      icon: Icon(
                        isFavorite ? Icons.favorite : Icons.favorite_border,
                        color: isFavorite ? Colors.red : null,
                      ),
                      onPressed: () => _toggleFavorite(topicId, isFavorite),
                    ),
              onTap: _isSelectionMode
                  ? () => _toggleSelection(topicId)
                  : () => context.push('/topic/$topicId'),
              onLongPress: !_isSelectionMode
                  ? () {
                      setState(() {
                        _isSelectionMode = true;
                        _selectedIds.add(topicId);
                      });
                    }
                  : null,
            );
          },
        ),
      );
}
