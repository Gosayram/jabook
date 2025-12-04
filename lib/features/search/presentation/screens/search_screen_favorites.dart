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
import 'package:jabook/core/favorites/favorites_provider.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Helper class for managing favorites in search screen.
class SearchScreenFavoritesHelper {
  // Private constructor to prevent instantiation
  SearchScreenFavoritesHelper._();

  /// Toggles favorite status for an audiobook.
  ///
  /// Takes callbacks to show snackbar messages.
  static Future<void> toggleFavorite({
    required WidgetRef ref,
    required String topicId,
    required bool isFavorite,
    required List<Map<String, dynamic>> searchResults,
    required bool mounted,
    required BuildContext context,
  }) async {
    final notifier = ref.read(favoriteIdsProvider.notifier);

    // Get audiobook data from search results
    Map<String, dynamic>? audiobookMap;
    try {
      audiobookMap = searchResults.firstWhere(
        (r) => (r['id'] as String?) == topicId,
      );
    } on Exception {
      // Audiobook not found in search results
      if (mounted) {
        // ignore: use_build_context_synchronously
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              AppLocalizations.of(context)?.failedToAddToFavorites ??
                  'Failed to add to favorites',
            ),
          ),
        );
      }
      return;
    }

    try {
      final wasAdded = await notifier.toggleFavorite(
        topicId,
        audiobookMap: audiobookMap,
      );

      if (mounted) {
        // ignore: use_build_context_synchronously
        final localizations = AppLocalizations.of(context);
        // ignore: use_build_context_synchronously
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              wasAdded
                  ? (localizations?.addedToFavorites ?? 'Added to favorites')
                  : (localizations?.removedFromFavorites ??
                      'Removed from favorites'),
            ),
            duration: const Duration(seconds: 2),
          ),
        );
      }
    } on Exception {
      // Show error message
      if (mounted) {
        // ignore: use_build_context_synchronously
        final localizations = AppLocalizations.of(context);
        // ignore: use_build_context_synchronously
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              isFavorite
                  ? (localizations?.failedToAddToFavorites ??
                      'Failed to add to favorites')
                  : (localizations?.failedToRemoveFromFavorites ??
                      'Failed to remove from favorites'),
            ),
          ),
        );
      }
    }
  }
}
