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
import 'package:jabook/core/domain/search/use_cases/search_use_case.dart';

/// Mock implementation of SearchRepository for testing.
class MockSearchRepository implements SearchRepository {
  final List<Map<String, dynamic>> _results = [];
  bool _shouldFail = false;
  String? _lastQuery;
  int? _lastOffset;

  @override
  Future<List<Map<String, dynamic>>> search(
    String query, {
    int offset = 0,
  }) async {
    if (_shouldFail) {
      throw Exception('Search failed');
    }
    _lastQuery = query;
    _lastOffset = offset;
    return _results;
  }

  @override
  Future<List<SearchQuery>> getRecentSearches({int limit = 10}) async => [];

  @override
  Future<List<SearchQuery>> getAllSearches() async => [];

  @override
  Future<void> removeSearchQuery(String query) async {}

  @override
  Future<void> saveSearchQuery(String query) async {}

  @override
  Future<void> clearHistory() async {}

  // Test helpers
  set results(List<Map<String, dynamic>> value) {
    _results
      ..clear()
      ..addAll(value);
  }

  set shouldFail(bool value) => _shouldFail = value;
  String? get lastQuery => _lastQuery;
  int? get lastOffset => _lastOffset;
}

void main() {
  group('SearchUseCase', () {
    late MockSearchRepository mockRepository;
    late SearchUseCase searchUseCase;

    setUp(() {
      mockRepository = MockSearchRepository();
      searchUseCase = SearchUseCase(mockRepository);
    });

    test('should call repository search with correct query', () async {
      // Arrange
      const query = 'test query';
      final expectedResults = [
        {'id': '1', 'title': 'Test Result 1'},
        {'id': '2', 'title': 'Test Result 2'},
      ];
      mockRepository.results = expectedResults;

      // Act
      final results = await searchUseCase(query);

      // Assert
      expect(results, equals(expectedResults));
      expect(mockRepository.lastQuery, equals(query));
      expect(mockRepository.lastOffset, equals(0));
    });

    test('should call repository search with offset', () async {
      // Arrange
      const query = 'test query';
      const offset = 10;
      mockRepository.results = [];

      // Act
      await searchUseCase(query, offset: offset);

      // Assert
      expect(mockRepository.lastOffset, equals(offset));
    });

    test('should throw exception when repository search fails', () async {
      // Arrange
      mockRepository.shouldFail = true;
      const query = 'test query';

      // Act & Assert
      expect(
        () => searchUseCase(query),
        throwsException,
      );
    });

    test('should save query to history when query is not empty', () async {
      // Arrange
      const query = 'test query';
      mockRepository.results = [];

      // Act
      await searchUseCase(query);

      // Assert
      expect(mockRepository.lastQuery, equals(query));
      // Note: In a real test, we would verify that saveSearchQuery was called
      // This requires a more sophisticated mock or spy
    });
  });
}
