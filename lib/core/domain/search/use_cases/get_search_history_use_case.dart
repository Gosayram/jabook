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

/// Use case for getting search history.
class GetSearchHistoryUseCase {
  /// Creates a new GetSearchHistoryUseCase instance.
  GetSearchHistoryUseCase(this._repository);

  final SearchRepository _repository;

  /// Executes the get search history use case.
  ///
  /// The [limit] parameter limits the number of results (default: 10).
  ///
  /// Returns a list of search queries, most recent first.
  Future<List<SearchQuery>> call({int limit = 10}) =>
      _repository.getRecentSearches(limit: limit);
}
