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

import 'package:jabook/core/data/search/mappers/search_mapper.dart';
import 'package:jabook/core/domain/search/entities/search_query.dart';
import 'package:jabook/core/search/search_history_service.dart';

/// Local data source for search history operations.
///
/// This class wraps SearchHistoryService to provide a clean interface
/// for search history operations that interact with local storage.
abstract class SearchLocalDataSource {
  /// Saves a search query to history.
  Future<void> saveSearchQuery(String query);

  /// Gets recent search queries from history.
  Future<List<SearchQuery>> getRecentSearches({int limit = 10});

  /// Gets all search queries from history.
  Future<List<SearchQuery>> getAllSearches();

  /// Removes a specific search query from history.
  Future<void> removeSearchQuery(String query);

  /// Clears all search history.
  Future<void> clearHistory();
}

/// Implementation of SearchLocalDataSource using SearchHistoryService.
class SearchLocalDataSourceImpl implements SearchLocalDataSource {
  /// Creates a new SearchLocalDataSourceImpl instance.
  SearchLocalDataSourceImpl(this._service);

  final SearchHistoryService _service;

  @override
  Future<void> saveSearchQuery(String query) => _service.saveSearchQuery(query);

  @override
  Future<List<SearchQuery>> getRecentSearches({int limit = 10}) async {
    final queries = await _service.getRecentSearches(limit: limit);
    return SearchMapper.toDomainList(queries);
  }

  @override
  Future<List<SearchQuery>> getAllSearches() async {
    final queries = await _service.getAllSearches();
    return SearchMapper.toDomainList(queries);
  }

  @override
  Future<void> removeSearchQuery(String query) =>
      _service.removeSearchQuery(query);

  @override
  Future<void> clearHistory() => _service.clearHistory();
}
