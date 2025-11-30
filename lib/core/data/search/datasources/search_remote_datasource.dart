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

import 'package:jabook/core/data/local/cache/rutracker_cache_service.dart';
import 'package:jabook/core/metadata/audiobook_metadata_service.dart';

/// Remote data source for search operations.
///
/// This class provides methods for searching audiobooks on RuTracker
/// and accessing cached search results.
abstract class SearchRemoteDataSource {
  /// Performs a network search.
  ///
  /// The [query] parameter is the search query text.
  /// The [offset] parameter is the pagination offset.
  ///
  /// Returns a list of search results as maps.
  ///
  /// Throws [Exception] if search fails.
  Future<List<Map<String, dynamic>>> searchNetwork(
    String query, {
    int offset = 0,
  });

  /// Gets cached search results.
  ///
  /// The [query] parameter is the search query text.
  ///
  /// Returns cached results if available, null otherwise.
  Future<List<Map<String, dynamic>>?> getCachedResults(String query);

  /// Gets search results from local database.
  ///
  /// The [query] parameter is the search query text.
  ///
  /// Returns local results if available, empty list otherwise.
  Future<List<Map<String, dynamic>>> searchLocal(String query);
}

/// Implementation of SearchRemoteDataSource using services.
class SearchRemoteDataSourceImpl implements SearchRemoteDataSource {
  /// Creates a new SearchRemoteDataSourceImpl instance.
  SearchRemoteDataSourceImpl(
    this._cacheService,
    this._metadataService,
  );

  final RuTrackerCacheService _cacheService;
  final AudiobookMetadataService? _metadataService;

  @override
  Future<List<Map<String, dynamic>>> searchNetwork(
    String query, {
    int offset = 0,
  }) async {
    // Note: This is a simplified implementation
    // Full implementation would use DioClient and EndpointManager
    // For now, this is a placeholder that shows the interface
    throw UnimplementedError(
      'Network search should be implemented in the full data source',
    );
  }

  @override
  Future<List<Map<String, dynamic>>?> getCachedResults(String query) =>
      _cacheService.getCachedSearchResults(query);

  @override
  Future<List<Map<String, dynamic>>> searchLocal(String query) async {
    if (_metadataService == null) return [];
    try {
      final results = await _metadataService.searchLocally(query);
      // Convert to map format (simplified - would need proper mapping)
      return results
          .map((audiobook) => {
                'id': audiobook.id,
                'title': audiobook.title,
                'author': audiobook.author,
                // Add other fields as needed
              })
          .toList();
    } on Exception {
      return [];
    }
  }
}
