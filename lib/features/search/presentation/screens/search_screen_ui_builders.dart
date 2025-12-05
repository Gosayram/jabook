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
import 'package:jabook/features/search/presentation/screens/search_screen_filters.dart';
import 'package:jabook/features/search/presentation/widgets/grouped_audiobook_list.dart';
import 'package:jabook/features/search/presentation/widgets/recommended_audiobooks_widget.dart';
import 'package:jabook/features/search/presentation/widgets/search_category_filters.dart';
import 'package:jabook/features/search/presentation/widgets/search_empty_state.dart';
import 'package:jabook/features/search/presentation/widgets/search_error_state.dart';
import 'package:jabook/features/search/presentation/widgets/search_history_widget.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Helper class for building UI widgets in search screen.
class SearchScreenUIBuilders {
  // Private constructor to prevent instantiation
  SearchScreenUIBuilders._();

  /// Builds the main body state widget.
  ///
  /// Shows error state if there's an error, otherwise shows search results.
  static Widget buildBodyState({
    required BuildContext context,
    required String? errorKind,
    required String? errorMessage,
    required VoidCallback onRetry,
    required VoidCallback onLogin,
    required Widget searchResults,
  }) {
    if (errorKind != null) {
      return SearchErrorState(
        errorKind: errorKind,
        errorMessage: errorMessage,
        onRetry: onRetry,
        onLogin: onLogin,
      );
    }
    return searchResults;
  }

  /// Builds empty state widget.
  ///
  /// Uses SearchEmptyState widget which handles recommendations display.
  static Widget buildEmptyState({
    required BuildContext context,
    required String searchText,
    required Future<List<RecommendedAudiobook>>? recommendedAudiobooksFuture,
    required VoidCallback onRefresh,
  }) =>
      SearchEmptyState(
        searchText: searchText,
        recommendedAudiobooksFuture: recommendedAudiobooksFuture,
        onRefresh: onRefresh,
      );

  /// Builds category filters widget.
  ///
  /// Uses SearchCategoryFilters widget.
  static Widget buildCategoryFilters({
    required BuildContext context,
    required List<Map<String, dynamic>> searchResults,
    required Set<String> selectedCategories,
    required void Function(String category, bool selected) onCategoryToggled,
    required VoidCallback onReset,
  }) {
    final categories = getAvailableCategories(searchResults);
    return SearchCategoryFilters(
      availableCategories: categories,
      selectedCategories: selectedCategories,
      onCategoryToggled: onCategoryToggled,
      onReset: onReset,
    );
  }

  /// Builds search history widget.
  ///
  /// Uses SearchHistoryWidget.
  static Widget buildSearchHistory({
    required BuildContext context,
    required List<String> searchHistory,
    required void Function(String query) onQuerySelected,
    required void Function(String query) onQueryRemoved,
    required VoidCallback onClear,
  }) =>
      SearchHistoryWidget(
        history: searchHistory,
        onQuerySelected: onQuerySelected,
        onQueryRemoved: onQueryRemoved,
        onClear: onClear,
      );

  /// Builds search results widget.
  ///
  /// Shows filtered results or empty state if no results.
  static Widget buildSearchResults({
    required BuildContext context,
    required WidgetRef ref,
    required List<Map<String, dynamic>> searchResults,
    required Set<String> selectedCategories,
    required String searchText,
    required bool hasMore,
    required bool isLoadingMore,
    required Future<void> Function()? loadMore,
    required void Function(String topicId, bool isFavorite) onFavoriteToggle,
    required void Function() onClearSearch,
  }) {
    final filteredResults = getFilteredResults(
      searchResults,
      selectedCategories,
      context,
    );

    final localizations = AppLocalizations.of(context);

    // Show message if filters exclude all results
    if (filteredResults.isEmpty && searchResults.isNotEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Semantics(
              label: 'No results match the selected filters',
              child: const Icon(
                Icons.filter_alt_off,
                size: 48,
                color: Colors.grey,
              ),
            ),
            const SizedBox(height: 16),
            Text(
              localizations?.noResultsForFilters ??
                  'No results match the selected filters',
              style: Theme.of(context).textTheme.bodyLarge,
              textAlign: TextAlign.center,
            ),
          ],
        ),
      );
    }

    // Show empty state if no results at all
    if (filteredResults.isEmpty) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(32.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                Icons.search_off,
                size: 64,
                color: Theme.of(context)
                    .colorScheme
                    .onSurface
                    .withValues(alpha: 0.4),
              ),
              const SizedBox(height: 16),
              Text(
                localizations?.noResults ?? 'No results found',
                style: Theme.of(context).textTheme.titleLarge,
                textAlign: TextAlign.center,
              ),
              if (searchText.isNotEmpty) ...[
                const SizedBox(height: 8),
                Text(
                  localizations?.noResultsForQuery(searchText) ??
                      'Nothing found for query: "$searchText"',
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: Theme.of(context)
                            .colorScheme
                            .onSurface
                            .withValues(alpha: 0.6),
                      ),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 8),
                Text(
                  localizations?.tryDifferentKeywords ??
                      'Try changing keywords',
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: Theme.of(context)
                            .colorScheme
                            .onSurface
                            .withValues(alpha: 0.5),
                      ),
                  textAlign: TextAlign.center,
                ),
              ],
              const SizedBox(height: 24),
              OutlinedButton.icon(
                onPressed: onClearSearch,
                icon: const Icon(Icons.clear),
                label: Text(
                  localizations?.clearSearch ?? 'Clear search',
                ),
              ),
            ],
          ),
        ),
      );
    }

    // Show results list
    return GroupedAudiobookList(
      audiobooks: filteredResults,
      onAudiobookTap: (id) {
        context.push('/topic/$id');
      },
      loadMore: loadMore,
      hasMore: hasMore,
      isLoadingMore: isLoadingMore,
      favoriteIds: ref.watch(favoriteIdsProvider),
      onFavoriteToggle: onFavoriteToggle,
    );
  }
}
