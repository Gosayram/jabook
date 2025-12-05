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

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/core/cache/rutracker_cache_service.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:jabook/core/metadata/audiobook_metadata_service.dart';
import 'package:jabook/core/parse/rutracker_parser.dart';
import 'package:jabook/core/search/smart_search_cache_service.dart';
import 'package:jabook/features/search/presentation/screens/search_screen_utils.dart';
import 'package:jabook/features/search/presentation/services/search_network_service.dart';

/// Result of a search operation.
class SearchResult {
  /// Creates a new SearchResult.
  const SearchResult({
    required this.results,
    required this.isFromCache,
    required this.isFromLocalDb,
    this.shouldSkipNetworkSearch = false,
  });

  /// Search results.
  final List<Map<String, dynamic>> results;

  /// Whether results are from cache.
  final bool isFromCache;

  /// Whether results are from local database.
  final bool isFromLocalDb;

  /// Whether network search should be skipped (e.g., if smart cache is valid).
  final bool shouldSkipNetworkSearch;
}

/// Helper class for search operations in search screen.
class SearchScreenSearchHandlers {
  // Private constructor to prevent instantiation
  SearchScreenSearchHandlers._();

  /// Performs a search operation.
  ///
  /// Checks smart cache, local DB, and regular cache first for quick results,
  /// then performs network search if needed.
  static Future<SearchResult?> performQuickSearch({
    required String query,
    required SmartSearchCacheService? smartCacheService,
    required AudiobookMetadataService? metadataService,
    required RuTrackerCacheService cacheService,
  }) async {
    final structuredLogger = StructuredLogger();

    await structuredLogger.log(
      level: 'info',
      subsystem: 'search',
      message: 'performQuickSearch called',
      context: 'search_request',
      extra: {
        'query': query,
        'query_is_empty': query.isEmpty,
      },
    );

    if (query.isEmpty) {
      await structuredLogger.log(
        level: 'debug',
        subsystem: 'search',
        message: 'performQuickSearch returning early - empty query',
        context: 'search_request',
      );
      return null;
    }

    // Check smart cache first (if available and valid)
    Future<List<Map<String, dynamic>>?>? smartCacheResultsFuture;
    if (smartCacheService != null) {
      smartCacheResultsFuture = (() async {
        try {
          final status = await smartCacheService.getCacheStatus();
          if (status.isValid) {
            await structuredLogger.log(
              level: 'info',
              subsystem: 'search',
              message: 'Smart cache is valid, using smart search',
              context: 'search_request',
              extra: {
                'query': query,
                'cached_books': status.totalCachedBooks,
              },
            );
            final smartResults = await smartCacheService.search(query);
            if (smartResults.isNotEmpty) {
              // Convert cache format to display format
              return smartResults.map(cacheMetadataToMap).toList();
            }
          } else {
            await structuredLogger.log(
              level: 'debug',
              subsystem: 'search',
              message: 'Smart cache is not valid, skipping',
              context: 'search_request',
              extra: {
                'is_empty': status.isEmpty,
                'is_stale': status.isStale,
              },
            );
          }
        } on Exception catch (e) {
          await structuredLogger.log(
            level: 'debug',
            subsystem: 'search',
            message: 'Smart cache search failed',
            context: 'search_request',
            cause: e.toString(),
          );
        }
        return null;
      })();
    }

    // Check local DB and cache in parallel for quick display, but don't block network search
    // Network search is the primary source of truth
    Future<List<Map<String, dynamic>>?>? localResultsFuture;
    Future<List<Map<String, dynamic>>?>? cachedResultsFuture;

    // Start local DB check in background (non-blocking)
    if (metadataService != null) {
      localResultsFuture = (() async {
        try {
          await structuredLogger.log(
            level: 'debug',
            subsystem: 'search',
            message: 'Checking local database in background',
            context: 'search_request',
            extra: {'query': query},
          );
          final localResults = await metadataService.searchLocally(query);
          if (localResults.isNotEmpty) {
            await structuredLogger.log(
              level: 'info',
              subsystem: 'search',
              message: 'Found local results in background',
              context: 'search_request',
              extra: {
                'query': query,
                'local_results_count': localResults.length,
              },
            );
            return localResults.map(audiobookToMap).toList();
          }
        } on Exception catch (e) {
          await structuredLogger.log(
            level: 'debug',
            subsystem: 'search',
            message: 'Local search check failed',
            context: 'search_request',
            cause: e.toString(),
          );
        }
        return null;
      })();
    }

    // Start cache check in background (non-blocking)
    cachedResultsFuture = (() async {
      try {
        await structuredLogger.log(
          level: 'debug',
          subsystem: 'search',
          message: 'Checking cache in background',
          context: 'search_request',
          extra: {'query': query},
        );
        final cachedResults =
            await cacheService.getCachedSearchResults(query).timeout(
                  const Duration(seconds: 1),
                  onTimeout: () => null,
                );
        if (cachedResults != null && cachedResults.isNotEmpty) {
          await structuredLogger.log(
            level: 'info',
            subsystem: 'search',
            message: 'Found cached results in background',
            context: 'search_request',
            extra: {
              'query': query,
              'cached_results_count': cachedResults.length,
            },
          );
          return cachedResults;
        }
      } on Exception catch (e) {
        await structuredLogger.log(
          level: 'debug',
          subsystem: 'search',
          message: 'Cache check failed',
          context: 'search_request',
          cause: e.toString(),
        );
      }
      return null;
    })();

    // Perform network search immediately (primary source)
    await structuredLogger.log(
      level: 'info',
      subsystem: 'search',
      message: 'Starting network search immediately',
      context: 'search_request',
      extra: {'query': query},
    );

    // Wait for quick results from smart cache/cache/local DB (with short timeout)
    // If they arrive quickly, show them immediately, then update with network results
    try {
      final futures = <Future<List<Map<String, dynamic>>?>>[
        if (smartCacheResultsFuture != null) smartCacheResultsFuture,
        cachedResultsFuture,
        if (localResultsFuture != null) localResultsFuture,
        Future.delayed(const Duration(milliseconds: 100))
            .then((_) => null as List<Map<String, dynamic>>?),
      ];

      final quickResults =
          await Future.any(futures).timeout(const Duration(milliseconds: 200));

      if (quickResults != null && quickResults.isNotEmpty) {
        // Check if results came from smart cache
        var isFromSmartCache = false;
        if (smartCacheResultsFuture != null) {
          try {
            final smartResults = await smartCacheResultsFuture.timeout(
              const Duration(milliseconds: 50),
            );
            if (smartResults != null &&
                smartResults.length == quickResults.length) {
              // Simple check: if lengths match and first result IDs match, likely same source
              if (smartResults.isNotEmpty && quickResults.isNotEmpty) {
                final smartFirstId = smartResults.first['id'] as String?;
                final quickFirstId = quickResults.first['id'] as String?;
                if (smartFirstId != null &&
                    quickFirstId != null &&
                    smartFirstId == quickFirstId) {
                  isFromSmartCache = true;
                }
              }
            }
          } on Exception {
            // Ignore timeout/errors
          }
        }

        await structuredLogger.log(
          level: 'info',
          subsystem: 'search',
          message: isFromSmartCache
              ? 'Showing smart cache results (skipping network search)'
              : 'Showing quick results while network search continues',
          context: 'search_request',
          extra: {
            'query': query,
            'quick_results_count': quickResults.length,
            'is_from_smart_cache': isFromSmartCache,
          },
        );

        // If results are from smart cache and cache is valid, skip network search
        var shouldSkipNetworkSearch = false;
        if (isFromSmartCache && smartCacheService != null) {
          final status = await smartCacheService.getCacheStatus();
          if (status.isValid) {
            await structuredLogger.log(
              level: 'info',
              subsystem: 'search',
              message: 'Smart cache is valid, skipping network search',
              context: 'search_request',
            );
            shouldSkipNetworkSearch = true;
          }
        }

        return SearchResult(
          results: quickResults,
          isFromCache: isFromSmartCache,
          isFromLocalDb: !isFromSmartCache,
          shouldSkipNetworkSearch: shouldSkipNetworkSearch,
        );
      }
    } on Exception catch (_) {
      // Ignore timeout or errors - just continue with network search
    }

    return null; // No quick results, need network search
  }

  /// Performs network search using SearchNetworkService.
  static Future<NetworkSearchResult> performNetworkSearch({
    required WidgetRef ref,
    required String query,
    required int startOffset,
    required CancelToken cancelToken,
    required RuTrackerParser parser,
  }) async {
    final service = SearchNetworkService(ref);
    return service.performSearch(
      query: query,
      startOffset: startOffset,
      cancelToken: cancelToken,
      parser: parser,
    );
  }

  /// Forces a refresh of the current search by clearing cache.
  ///
  /// This method clears the cache for the current query.
  static Future<void> clearCacheForQuery({
    required String query,
    required RuTrackerCacheService cacheService,
  }) async {
    await cacheService.clearSearchResultsCacheForQuery(query);
  }
}
