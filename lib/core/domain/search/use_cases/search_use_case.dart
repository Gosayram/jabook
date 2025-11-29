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

import 'package:jabook/core/domain/search/repositories/search_repository.dart';

/// Use case for performing a search.
class SearchUseCase {
  /// Creates a new SearchUseCase instance.
  SearchUseCase(this._repository);

  final SearchRepository _repository;

  /// Executes the search use case.
  ///
  /// The [query] parameter is the search query text.
  /// The [offset] parameter is the pagination offset (default: 0).
  ///
  /// Returns a list of search results as maps.
  ///
  /// Throws [Exception] if search fails.
  Future<List<Map<String, dynamic>>> call(
    String query, {
    int offset = 0,
  }) async {
    final results = await _repository.search(query, offset: offset);
    // Save query to history
    if (query.trim().isNotEmpty) {
      await _repository.saveSearchQuery(query.trim());
    }
    return results;
  }
}
