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

/// Represents a search result source.
enum SearchResultSource {
  /// Results from network search.
  network,

  /// Results from cache.
  cache,

  /// Results from local database.
  localDatabase,
}

/// Represents search results metadata.
///
/// This entity contains information about the search results,
/// including the source and expiration time for cached results.
class SearchResultMetadata {
  /// Creates a new SearchResultMetadata instance.
  SearchResultMetadata({
    required this.source,
    this.cacheExpirationTime,
    this.totalCount,
    this.hasMore,
  });

  /// Source of the search results.
  final SearchResultSource source;

  /// Cache expiration time (if results are from cache).
  final DateTime? cacheExpirationTime;

  /// Total number of results available.
  final int? totalCount;

  /// Whether there are more results available.
  final bool? hasMore;

  /// Checks if results are from cache and still valid.
  bool get isCacheValid {
    if (source != SearchResultSource.cache) return false;
    if (cacheExpirationTime == null) return false;
    return DateTime.now().isBefore(cacheExpirationTime!);
  }

  /// Creates a copy with updated fields.
  SearchResultMetadata copyWith({
    SearchResultSource? source,
    DateTime? cacheExpirationTime,
    int? totalCount,
    bool? hasMore,
  }) =>
      SearchResultMetadata(
        source: source ?? this.source,
        cacheExpirationTime: cacheExpirationTime ?? this.cacheExpirationTime,
        totalCount: totalCount ?? this.totalCount,
        hasMore: hasMore ?? this.hasMore,
      );
}
