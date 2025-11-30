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

import 'package:flutter_test/flutter_test.dart';
import 'package:jabook/core/domain/search/entities/search_query.dart';
import 'package:jabook/core/domain/search/repositories/search_repository.dart';
import 'package:jabook/core/domain/search/use_cases/get_search_history_use_case.dart';

/// Mock implementation of SearchRepository for testing.
class MockSearchRepository implements SearchRepository {
  final List<SearchQuery> _history = [];
  bool _shouldFail = false;
  int? _lastLimit;

  @override
  Future<List<Map<String, dynamic>>> search(
    String query, {
    int offset = 0,
  }) async =>
      [];

  @override
  Future<void> saveSearchQuery(String query) async {}

  @override
  Future<List<SearchQuery>> getRecentSearches({int limit = 10}) async {
    if (_shouldFail) {
      throw Exception('Failed to get search history');
    }
    _lastLimit = limit;
    return _history.take(limit).toList();
  }

  @override
  Future<List<SearchQuery>> getAllSearches() async => _history;

  @override
  Future<void> removeSearchQuery(String query) async {
    _history.removeWhere((q) => q.query == query);
  }

  @override
  Future<void> clearHistory() async {
    _history.clear();
  }

  // Test helpers
  set history(List<SearchQuery> value) {
    _history
      ..clear()
      ..addAll(value);
  }

  set shouldFail(bool value) => _shouldFail = value;
  int? get lastLimit => _lastLimit;
}

void main() {
  group('GetSearchHistoryUseCase', () {
    late MockSearchRepository mockRepository;
    late GetSearchHistoryUseCase useCase;

    setUp(() {
      mockRepository = MockSearchRepository();
      useCase = GetSearchHistoryUseCase(mockRepository);
    });

    test('should return search history with default limit', () async {
      // Arrange
      final history = [
        SearchQuery(
          query: 'query1',
          timestamp: DateTime.now().subtract(const Duration(days: 1)),
        ),
        SearchQuery(
          query: 'query2',
          timestamp: DateTime.now().subtract(const Duration(days: 2)),
        ),
      ];
      mockRepository.history = history;

      // Act
      final result = await useCase();

      // Assert
      expect(result, equals(history));
      expect(mockRepository.lastLimit, equals(10));
    });

    test('should return search history with custom limit', () async {
      // Arrange
      final history = List.generate(
        20,
        (i) => SearchQuery(
          query: 'query$i',
          timestamp: DateTime.now().subtract(Duration(days: i)),
        ),
      );
      mockRepository.history = history;
      const limit = 5;

      // Act
      final result = await useCase(limit: limit);

      // Assert
      expect(result.length, lessThanOrEqualTo(limit));
      expect(mockRepository.lastLimit, equals(limit));
    });

    test('should return empty list when no history', () async {
      // Arrange
      mockRepository.history = [];

      // Act
      final result = await useCase();

      // Assert
      expect(result, isEmpty);
    });

    test('should throw exception when repository fails', () async {
      // Arrange
      mockRepository.shouldFail = true;

      // Act & Assert
      expect(
        () => useCase(),
        throwsException,
      );
    });
  });
}
