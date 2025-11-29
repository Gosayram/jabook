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

import 'package:jabook/core/domain/search/entities/search_query.dart';

/// Repository interface for search operations.
///
/// This repository provides methods for searching audiobooks,
/// managing search history, and accessing cached results.
abstract class SearchRepository {
  /// Performs a search query.
  ///
  /// The [query] parameter is the search query text.
  /// The [offset] parameter is the pagination offset (default: 0).
  ///
  /// Returns a list of search results as maps (to be converted to domain entities).
  ///
  /// Throws [Exception] if search fails.
  Future<List<Map<String, dynamic>>> search(
    String query, {
    int offset = 0,
  });

  /// Saves a search query to history.
  ///
  /// The [query] parameter is the search query to save.
  Future<void> saveSearchQuery(String query);

  /// Gets recent search queries from history.
  ///
  /// The [limit] parameter limits the number of results (default: 10).
  ///
  /// Returns a list of search queries, most recent first.
  Future<List<SearchQuery>> getRecentSearches({int limit = 10});

  /// Gets all search queries from history.
  ///
  /// Returns a list of all search queries, most recent first.
  Future<List<SearchQuery>> getAllSearches();

  /// Removes a specific search query from history.
  ///
  /// The [query] parameter is the query to remove.
  Future<void> removeSearchQuery(String query);

  /// Clears all search history.
  Future<void> clearHistory();
}
