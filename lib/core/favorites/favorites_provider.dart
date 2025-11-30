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

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/core/di/providers/database_providers.dart';
import 'package:jabook/core/favorites/favorites_service.dart';
import 'package:jabook/core/parse/rutracker_parser.dart';
import 'package:riverpod/legacy.dart';

/// Provider for FavoritesService instance.
///
/// This provider creates and provides a FavoritesService instance
/// using the app database. The database must be initialized before use.
final favoritesServiceProvider = Provider<FavoritesService?>((ref) {
  try {
    final appDatabase = ref.watch(appDatabaseProvider);
    if (appDatabase.isInitialized) {
      return FavoritesService(appDatabase.database);
    }
    return null;
  } on Exception {
    return null;
  }
}, dependencies: [appDatabaseProvider]);

/// Provider for favorite audiobook IDs set.
///
/// This provider manages the set of favorite audiobook topic IDs
/// and automatically syncs with the database.
final favoriteIdsProvider =
    StateNotifierProvider<FavoriteIdsNotifier, Set<String>>((ref) {
  final service = ref.watch(favoritesServiceProvider);
  return FavoriteIdsNotifier(service);
});

/// Provider for all favorite audiobooks list.
///
/// This provider automatically loads and refreshes the list of favorite audiobooks
/// when the favoriteIdsProvider changes.
final favoritesListProvider = FutureProvider<List<Audiobook>>((ref) async {
  final service = ref.watch(favoritesServiceProvider);
  if (service == null) {
    return [];
  }

  // Watch favoriteIds to trigger refresh when favorites change
  ref.watch(favoriteIdsProvider);

  try {
    return await service.getAllFavorites();
  } on Exception {
    return [];
  }
});

/// Notifier for managing favorite IDs state.
///
/// This notifier maintains a set of favorite audiobook topic IDs
/// and syncs with the database.
class FavoriteIdsNotifier extends StateNotifier<Set<String>> {
  /// Creates a new FavoriteIdsNotifier instance.
  FavoriteIdsNotifier(this._service) : super(<String>{}) {
    _loadFavoriteIds();
  }

  /// FavoritesService instance (may be null if database not initialized).
  final FavoritesService? _service;

  /// Loads favorite IDs from the database.
  Future<void> _loadFavoriteIds() async {
    if (_service == null) return;

    try {
      final favorites = await _service.getAllFavorites();
      state = favorites.map((a) => a.id).toSet();
    } on Exception {
      state = <String>{};
    }
  }

  /// Toggles favorite status for an audiobook.
  ///
  /// The [topicId] parameter is the topic ID of the audiobook.
  /// The [audiobookMap] parameter is optional audiobook data in Map format.
  /// If provided, it will be used when adding to favorites.
  ///
  /// Returns true if added to favorites, false if removed.
  Future<bool> toggleFavorite(
    String topicId, {
    Map<String, dynamic>? audiobookMap,
  }) async {
    if (_service == null) return false;

    final isCurrentlyFavorite = state.contains(topicId);
    final newState = <String>{...state};

    // Optimistic update
    if (isCurrentlyFavorite) {
      newState.remove(topicId);
    } else {
      newState.add(topicId);
    }
    state = newState;

    try {
      if (isCurrentlyFavorite) {
        // Remove from favorites
        await _service.removeFromFavorites(topicId);
        return false;
      } else {
        // Add to favorites
        if (audiobookMap != null) {
          await _service.addToFavoritesFromMap(audiobookMap);
        } else {
          // If no audiobookMap provided, we can't add to favorites
          // Revert optimistic update
          state = <String>{...state}..remove(topicId);
          return false;
        }
        return true;
      }
    } on Exception {
      // Revert optimistic update on error
      state = <String>{...state};
      if (isCurrentlyFavorite) {
        state.add(topicId);
      } else {
        state.remove(topicId);
      }
      rethrow;
    }
  }

  /// Refreshes favorite IDs from the database.
  Future<void> refresh() async {
    await _loadFavoriteIds();
  }

  /// Checks if an audiobook is in favorites.
  ///
  /// The [topicId] parameter is the topic ID to check.
  ///
  /// Returns true if the audiobook is in favorites, false otherwise.
  bool isFavorite(String topicId) => state.contains(topicId);

  /// Removes multiple favorites.
  ///
  /// The [topicIds] parameter is a list of topic IDs to remove.
  Future<void> removeMultiple(List<String> topicIds) async {
    if (_service == null) return;

    final newState = <String>{...state};
    topicIds.forEach(newState.remove);
    state = newState;

    try {
      await _service.removeMultipleFromFavorites(topicIds);
    } on Exception {
      // Revert on error
      await _loadFavoriteIds();
      rethrow;
    }
  }

  /// Clears all favorites.
  Future<void> clearAll() async {
    if (_service == null) return;

    state = <String>{};

    try {
      await _service.clearAllFavorites();
    } on Exception {
      // Revert on error
      await _loadFavoriteIds();
      rethrow;
    }
  }
}
