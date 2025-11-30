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

/// Represents a search query.
///
/// This is a domain entity that contains information about a search query,
/// including the query text, timestamp, and usage count.
class SearchQuery {
  /// Creates a new SearchQuery instance.
  SearchQuery({
    required this.query,
    required this.timestamp,
    this.count = 1,
  });

  /// The search query text.
  final String query;

  /// Timestamp when the query was last used.
  final DateTime timestamp;

  /// Number of times this query was used.
  final int count;

  /// Creates a copy with updated fields.
  SearchQuery copyWith({
    String? query,
    DateTime? timestamp,
    int? count,
  }) =>
      SearchQuery(
        query: query ?? this.query,
        timestamp: timestamp ?? this.timestamp,
        count: count ?? this.count,
      );
}
