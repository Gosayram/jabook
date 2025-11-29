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

import 'package:jabook/core/data/search/datasources/search_local_datasource.dart';
import 'package:jabook/core/data/search/datasources/search_remote_datasource.dart';
import 'package:jabook/core/domain/search/entities/search_query.dart';
import 'package:jabook/core/domain/search/repositories/search_repository.dart';

/// Implementation of SearchRepository using data sources.
class SearchRepositoryImpl implements SearchRepository {
  /// Creates a new SearchRepositoryImpl instance.
  SearchRepositoryImpl(
    this._remoteDataSource,
    this._localDataSource,
  );

  final SearchRemoteDataSource _remoteDataSource;
  final SearchLocalDataSource _localDataSource;

  @override
  Future<List<Map<String, dynamic>>> search(
    String query, {
    int offset = 0,
  }) async {
    // Try local database first (offline mode)
    final localResults = await _remoteDataSource.searchLocal(query);
    if (localResults.isNotEmpty) {
      return localResults;
    }

    // Try cache
    final cachedResults = await _remoteDataSource.getCachedResults(query);
    if (cachedResults != null) {
      return cachedResults;
    }

    // Finally, try network search
    return _remoteDataSource.searchNetwork(query, offset: offset);
  }

  @override
  Future<void> saveSearchQuery(String query) =>
      _localDataSource.saveSearchQuery(query);

  @override
  Future<List<SearchQuery>> getRecentSearches({int limit = 10}) =>
      _localDataSource.getRecentSearches(limit: limit);

  @override
  Future<List<SearchQuery>> getAllSearches() =>
      _localDataSource.getAllSearches();

  @override
  Future<void> removeSearchQuery(String query) =>
      _localDataSource.removeSearchQuery(query);

  @override
  Future<void> clearHistory() => _localDataSource.clearHistory();
}
