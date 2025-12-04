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
import 'package:jabook/core/di/providers/auth_providers.dart';
import 'package:jabook/core/domain/auth/entities/auth_status.dart';
import 'package:jabook/core/infrastructure/logging/environment_logger.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:jabook/core/utils/safe_async.dart';
import 'package:jabook/features/search/presentation/widgets/recommended_audiobooks_widget.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Widget for displaying empty search state with recommendations.
///
/// Shows recommended audiobooks when search field is empty,
/// or a search prompt when user hasn't searched yet.
class SearchEmptyState extends ConsumerWidget {
  /// Creates a new SearchEmptyState instance.
  const SearchEmptyState({
    required this.searchText,
    required this.recommendedAudiobooksFuture,
    required this.onRefresh,
    super.key,
  });

  /// Current search text.
  final String searchText;

  /// Future for loading recommended audiobooks.
  final Future<List<RecommendedAudiobook>>? recommendedAudiobooksFuture;

  /// Callback when user pulls to refresh.
  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final structuredLogger = StructuredLogger();
    safeUnawaited(structuredLogger.log(
      level: 'debug',
      subsystem: 'recommendations',
      message: 'SearchEmptyState.build called',
      context: 'recommendations_ui',
      extra: {
        'search_text': searchText,
        'search_text_empty': searchText.isEmpty,
      },
    ));

    // Show recommendations if search field is empty
    if (searchText.isEmpty) {
      // Check current auth status
      final authStatus = ref.watch(authStatusProvider);
      final isAuthenticated = authStatus.value?.isAuthenticated ?? false;

      safeUnawaited(structuredLogger.log(
        level: 'debug',
        subsystem: 'recommendations',
        message: 'Checking authentication status in SearchEmptyState',
        context: 'recommendations_ui',
        extra: {
          'is_authenticated': isAuthenticated,
          'future_exists': recommendedAudiobooksFuture != null,
        },
      ));

      // Always recreate future if user is authenticated to ensure fresh data
      // This handles both initial load and auth status changes
      // Also check if auth status is loading (might be updating after login)
      if (isAuthenticated || authStatus.isLoading) {
        // Always recreate future when authenticated or when auth status is loading
        // This ensures that if user just authenticated, we load recommendations
        // even if provider hasn't updated yet
        safeUnawaited(structuredLogger.log(
          level: 'info',
          subsystem: 'recommendations',
          message:
              'User authenticated or auth loading, (re)initializing recommendations future',
          context: 'recommendations_ui',
          extra: {
            'is_authenticated': isAuthenticated,
            'is_loading': authStatus.isLoading,
            'future_exists': recommendedAudiobooksFuture != null,
          },
        ));
      }

      return RefreshIndicator(
        onRefresh: () async {
          // Reload recommendations when user pulls to refresh
          EnvironmentLogger().d('RefreshIndicator: reloading recommendations');
          onRefresh();
          // Wait a bit for the future to complete
          await Future.delayed(const Duration(milliseconds: 100));
        },
        child: FutureBuilder<List<RecommendedAudiobook>>(
          future: recommendedAudiobooksFuture,
          builder: (context, snapshot) {
            if (snapshot.connectionState == ConnectionState.waiting) {
              return const Center(child: CircularProgressIndicator());
            }

            if (snapshot.hasError) {
              safeUnawaited(structuredLogger.log(
                level: 'error',
                subsystem: 'recommendations',
                message: 'Error loading recommendations',
                context: 'recommendations_ui',
                cause: snapshot.error.toString(),
              ));
              return Center(
                child: Text(
                  AppLocalizations.of(context)?.error ??
                      'Error loading recommendations',
                ),
              );
            }

            final recommendations = snapshot.data ?? [];

            if (recommendations.isEmpty) {
              return Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(
                      Icons.search,
                      size: 64,
                      color: Theme.of(context)
                          .colorScheme
                          .onSurface
                          .withValues(alpha: 0.3),
                    ),
                    const SizedBox(height: 16),
                    Text(
                      AppLocalizations.of(context)?.searchAudiobooks ??
                          'Enter a search query to find audiobooks',
                      style: Theme.of(context).textTheme.bodyLarge,
                      textAlign: TextAlign.center,
                    ),
                  ],
                ),
              );
            }

            return RecommendedAudiobooksWidget(
              audiobooks: recommendations,
            );
          },
        ),
      );
    }

    // Show search prompt when search field has text but no results
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.search_off,
            size: 64,
            color:
                Theme.of(context).colorScheme.onSurface.withValues(alpha: 0.3),
          ),
          const SizedBox(height: 16),
          Text(
            AppLocalizations.of(context)?.noResults ??
                'No results found. Try a different search query.',
            style: Theme.of(context).textTheme.bodyLarge,
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }
}
