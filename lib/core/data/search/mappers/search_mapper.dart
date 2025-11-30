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

/// Mapper for converting search history strings to domain entities.
class SearchMapper {
  // Private constructor to prevent instantiation
  SearchMapper._();

  /// Converts a list of query strings to domain SearchQuery entities.
  ///
  /// Note: SearchHistoryService only returns strings, so we create
  /// SearchQuery entities with current timestamp and default count.
  /// In a full implementation, this would use the actual metadata from the service.
  static List<SearchQuery> toDomainList(List<String> queries) => queries
      .map((query) => SearchQuery(
            query: query,
            timestamp: DateTime.now(),
          ))
      .toList();
}
