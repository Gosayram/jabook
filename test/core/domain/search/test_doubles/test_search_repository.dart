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
import 'package:jabook/core/domain/search/repositories/search_repository.dart';

/// Test implementation of SearchRepository.
///
/// This implementation provides a simple in-memory storage for testing
/// search operations without external dependencies. It follows the
/// Test Doubles pattern from Now In Android.
///
/// **Test Hooks**:
/// - `setSearchResults()` - Set search results for a query
/// - `addSearchHistory()` - Add items to search history
/// - `setShouldFail()` - Simulate failures
class TestSearchRepository implements SearchRepository {
  /// Creates a new TestSearchRepository instance.
  TestSearchRepository();

  final Map<String, List<Map<String, dynamic>>> _searchResults = {};
  final List<SearchQuery> _searchHistory = [];
  bool _shouldFail = false;

  @override
  Future<List<Map<String, dynamic>>> search(
    String query, {
    int offset = 0,
  }) async {
    if (_shouldFail) {
      throw Exception('Search failed');
    }
    final results = _searchResults[query] ?? [];
    // Simple pagination simulation
    if (offset >= results.length) {
      return [];
    }
    return results.skip(offset).toList();
  }

  @override
  Future<void> saveSearchQuery(String query) async {
    if (_shouldFail) {
      throw Exception('Save search query failed');
    }
    final existingIndex = _searchHistory.indexWhere((q) => q.query == query);
    if (existingIndex >= 0) {
      // Update existing query
      final existing = _searchHistory[existingIndex];
      _searchHistory[existingIndex] = existing.copyWith(
        timestamp: DateTime.now(),
        count: existing.count + 1,
      );
    } else {
      // Add new query
      _searchHistory.add(SearchQuery(
        query: query,
        timestamp: DateTime.now(),
      ));
    }
    // Sort by timestamp descending
    _searchHistory.sort((a, b) => b.timestamp.compareTo(a.timestamp));
  }

  @override
  Future<List<SearchQuery>> getRecentSearches({int limit = 10}) async {
    if (_shouldFail) {
      throw Exception('Get recent searches failed');
    }
    return _searchHistory.take(limit).toList();
  }

  @override
  Future<List<SearchQuery>> getAllSearches() async {
    if (_shouldFail) {
      throw Exception('Get all searches failed');
    }
    return List.from(_searchHistory);
  }

  @override
  Future<void> removeSearchQuery(String query) async {
    if (_shouldFail) {
      throw Exception('Remove search query failed');
    }
    _searchHistory.removeWhere((q) => q.query == query);
  }

  @override
  Future<void> clearHistory() async {
    if (_shouldFail) {
      throw Exception('Clear history failed');
    }
    _searchHistory.clear();
  }

  // Test hooks
  /// Sets search results for a query.
  void setSearchResults(String query, List<Map<String, dynamic>> results) {
    _searchResults[query] = results;
  }

  /// Adds a search query to history.
  void addSearchHistory(SearchQuery query) {
    _searchHistory
      ..add(query)
      ..sort((a, b) => b.timestamp.compareTo(a.timestamp));
  }

  /// Sets whether operations should fail.
  set shouldFail(bool value) => _shouldFail = value;

  /// Clears all test data.
  void clear() {
    _searchResults.clear();
    _searchHistory.clear();
    _shouldFail = false;
  }

  @override
  Stream<List<Map<String, dynamic>>> watchCachedResults(String query) async* {
    final results = _searchResults[query] ?? [];
    yield results;
  }
}
