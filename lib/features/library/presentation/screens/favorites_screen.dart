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
import 'package:go_router/go_router.dart';
import 'package:jabook/core/favorites/favorites_service.dart';
import 'package:jabook/core/parse/rutracker_parser.dart';
import 'package:jabook/data/db/app_database.dart';
import 'package:jabook/features/search/presentation/widgets/audiobook_card.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Screen for displaying user's favorite audiobooks.
///
/// This screen shows all audiobooks that the user has marked as favorites,
/// with options to view details and remove from favorites.
class FavoritesScreen extends StatefulWidget {
  /// Creates a new FavoritesScreen instance.
  const FavoritesScreen({super.key});

  @override
  State<FavoritesScreen> createState() => _FavoritesScreenState();
}

class _FavoritesScreenState extends State<FavoritesScreen> {
  FavoritesService? _favoritesService;
  List<Map<String, dynamic>> _favorites = [];
  bool _isLoading = true;
  final Set<String> _favoriteIds = <String>{};

  @override
  void initState() {
    super.initState();
    _initializeService();
  }

  Future<void> _initializeService() async {
    try {
      final appDatabase = AppDatabase();
      await appDatabase.initialize();
      _favoritesService = FavoritesService(appDatabase.database);
      await _loadFavorites();
    } on Exception {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
    }
  }

  Future<void> _loadFavorites() async {
    if (_favoritesService == null) return;

    setState(() {
      _isLoading = true;
    });

    try {
      final favorites = await _favoritesService!.getAllFavorites();
      if (mounted) {
        setState(() {
          _favorites = favorites.map(_audiobookToMap).toList();
          _favoriteIds
            ..clear()
            ..addAll(favorites.map((a) => a.id));
          _isLoading = false;
        });
      }
    } on Exception {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
    }
  }

  Future<void> _toggleFavorite(String topicId, bool isFavorite) async {
    if (_favoritesService == null) return;

    try {
      if (isFavorite) {
        // This shouldn't happen - already in favorites
        return;
      } else {
        // Remove from favorites
        await _favoritesService!.removeFromFavorites(topicId);
      }

      if (mounted) {
        setState(() {
          _favoriteIds.remove(topicId);
          _favorites.removeWhere((a) => (a['id'] as String?) == topicId);
        });

        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(AppLocalizations.of(context)?.removedFromFavorites ??
                'Removed from favorites'),
            duration: const Duration(seconds: 2),
          ),
        );
      }
    } on Exception {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
                AppLocalizations.of(context)?.failedToRemoveFromFavorites ??
                    'Failed to remove from favorites'),
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
      };

  @override
  Widget build(BuildContext context) => PopScope(
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
            title: Text(
                AppLocalizations.of(context)?.favoritesTitle ?? 'Favorites'),
            actions: [
              if (_favorites.isNotEmpty)
                IconButton(
                  icon: const Icon(Icons.refresh),
                  tooltip:
                      AppLocalizations.of(context)?.refreshTooltip ?? 'Refresh',
                  onPressed: _loadFavorites,
                ),
            ],
          ),
          body: _isLoading
              ? const Center(child: CircularProgressIndicator())
              : _favorites.isEmpty
                  ? _buildEmptyState(context)
                  : _buildFavoritesList(context),
        ),
      );

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
  Widget _buildFavoritesList(BuildContext context) => RefreshIndicator(
        onRefresh: _loadFavorites,
        child: ListView.builder(
          padding: const EdgeInsets.symmetric(vertical: 8),
          itemCount: _favorites.length,
          itemBuilder: (context, index) {
            final audiobook = _favorites[index];
            final topicId = audiobook['id'] as String? ?? '';
            final isFavorite = _favoriteIds.contains(topicId);

            return AudiobookCard(
              audiobook: audiobook,
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
