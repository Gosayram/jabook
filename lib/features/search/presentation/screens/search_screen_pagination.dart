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
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/core/constants/category_constants.dart';
import 'package:jabook/core/infrastructure/endpoints/endpoint_provider.dart';
import 'package:jabook/core/infrastructure/errors/failures.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/parse/rutracker_parser.dart';
import 'package:jabook/features/search/presentation/screens/search_screen_utils.dart';
import 'package:jabook/features/search/presentation/services/search_filters.dart';

/// Result of loading more search results.
class LoadMoreResult {
  /// Creates a new LoadMoreResult.
  const LoadMoreResult({
    required this.hasMore,
    required this.newResults,
  });

  /// Whether there are more results available.
  final bool hasMore;

  /// New results that were loaded.
  final List<Map<String, dynamic>> newResults;
}

/// Helper class for pagination in search screen.
class SearchScreenPaginationHelper {
  // Private constructor to prevent instantiation
  SearchScreenPaginationHelper._();

  /// Handles scroll event to load more results.
  static void onScroll({
    required ScrollController scrollController,
    required bool hasMore,
    required bool isLoadingMore,
    required VoidCallback loadMore,
  }) {
    if (!hasMore || isLoadingMore) return;
    if (scrollController.position.pixels >=
        scrollController.position.maxScrollExtent - 200) {
      loadMore();
    }
  }

  /// Loads more search results.
  ///
  /// Returns LoadMoreResult with new results and whether there are more available.
  static Future<LoadMoreResult> loadMore({
    required WidgetRef ref,
    required String query,
    required int currentResultsLength,
    required RuTrackerParser parser,
  }) async {
    if (query.isEmpty) {
      return const LoadMoreResult(hasMore: false, newResults: []);
    }
    try {
      final endpointManager = ref.read(endpointManagerProvider);
      final activeEndpoint = await endpointManager.getActiveEndpoint();
      final dio = await DioClient.instance;
      final res = await dio.get(
        '$activeEndpoint/forum/tracker.php',
        queryParameters: {
          'nm': query,
          'c': CategoryConstants
              .audiobooksCategoryId, // Try to filter by category 33 (audiobooks)
          'o': '1',
          'start': currentResultsLength,
        },
        options: Options(
          responseType: ResponseType
              .bytes, // Get raw bytes to decode Windows-1251 correctly
        ),
      );
      if (res.statusCode == 200) {
        try {
          // Pass response data and headers to parser for proper encoding detection
          final more = await parser.parseSearchResults(
            res.data,
            contentType: res.headers.value('content-type'),
            baseUrl: activeEndpoint,
          );

          // CRITICAL: Filter results to only include audiobooks
          final audiobookMore = filterAudiobookResults(more);

          final moreResults = audiobookMore.map(audiobookToMap).toList();
          return LoadMoreResult(
            hasMore: audiobookMore.isNotEmpty,
            newResults: moreResults,
          );
        } on ParsingFailure catch (e) {
          await StructuredLogger().log(
            level: 'error',
            subsystem: 'search',
            message: 'Failed to parse additional search results',
            context: 'search_pagination',
            cause: e.message,
            extra: {
              'start_offset': currentResultsLength,
              'error_type': 'ParsingFailure',
            },
          );
          // Don't show error to user for pagination failures, just stop loading more
          return const LoadMoreResult(hasMore: false, newResults: []);
        }
      }
    } on Object {
      // ignore pagination errors
    }
    return const LoadMoreResult(hasMore: false, newResults: []);
  }
}
