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
import 'package:jabook/core/infrastructure/endpoints/endpoint_provider.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/parse/rutracker_parser.dart';

/// Updates statistics (seeders/leechers) for search results that don't have them.
///
/// Makes lightweight requests to topic pages to get statistics for items
/// where seeders=0 and leechers=0 (likely missing from search results).
class SearchScreenStatisticsHelper {
  // Private constructor to prevent instantiation
  SearchScreenStatisticsHelper._();

  /// Updates missing statistics for search results.
  ///
  /// Takes a callback to update the search results list in the state.
  static Future<void> updateMissingStatistics({
    required WidgetRef ref,
    required List<Map<String, dynamic>> results,
    required List<Map<String, dynamic>> searchResults,
    required RuTrackerParser parser,
    required bool mounted,
    required void Function(void Function()) setState,
  }) async {
    // Find items that need statistics update
    final itemsNeedingStats = results.where((item) {
      final seeders = item['seeders'] as int? ?? 0;
      final leechers = item['leechers'] as int? ?? 0;
      return seeders == 0 && leechers == 0;
    }).toList();

    if (itemsNeedingStats.isEmpty) {
      return; // No items need statistics update
    }

    // Limit to first 10 items to avoid too many requests
    final itemsToUpdate = itemsNeedingStats.take(10).toList();

    try {
      final endpointManager = ref.read(endpointManagerProvider);
      final baseUrl = await endpointManager.buildUrl('');
      final dio = await DioClient.instance;

      // Update statistics for each item in parallel (with limit)
      final futures = itemsToUpdate.map((item) async {
        final topicId = item['id'] as String? ?? '';
        if (topicId.isEmpty) return;

        try {
          // Make lightweight request to topic page
          final topicUrl = '$baseUrl/forum/viewtopic.php?t=$topicId';
          final response = await dio
              .get(
                topicUrl,
                options: Options(
                  responseType: ResponseType.plain,
                  headers: {
                    'Accept': 'text/html,application/xhtml+xml,application/xml',
                    'Accept-Charset': 'windows-1251,utf-8',
                  },
                ),
              )
              .timeout(const Duration(seconds: 5));

          // Parse statistics from HTML
          final stats = await parser.parseTopicStatistics(response.data);
          if (stats != null && mounted) {
            // Update the item in searchResults
            final index = searchResults.indexWhere(
              (r) => (r['id'] as String? ?? '') == topicId,
            );
            if (index >= 0) {
              setState(() {
                searchResults[index]['seeders'] = stats['seeders'];
                searchResults[index]['leechers'] = stats['leechers'];
              });
            }
          }
        } on Exception {
          // Silently fail for individual items
          return;
        }
      });

      await Future.wait(futures);
    } on Exception {
      // Silently fail if batch update fails
    }
  }
}
