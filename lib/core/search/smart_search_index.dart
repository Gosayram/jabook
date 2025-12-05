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

import 'package:jabook/core/data/local/database/app_database.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:sembast/sembast.dart';

/// Service for smart search indexing and searching.
///
/// This service provides fast search capabilities over cached audiobook metadata
/// using indexed fields and ranking algorithms.
class SmartSearchIndex {
  /// Creates a new SmartSearchIndex instance.
  SmartSearchIndex(this._appDatabase);

  /// Database instance for search operations.
  final AppDatabase _appDatabase;

  /// Logger for search operations.
  final StructuredLogger _logger = StructuredLogger();

  /// Searches for audiobooks in the cache.
  ///
  /// The [query] parameter is the search query string.
  /// The [limit] parameter limits the number of results (default: 100).
  /// The [categoryFilter] parameter optionally filters by category.
  ///
  /// Returns a list of audiobook metadata maps, sorted by relevance.
  Future<List<Map<String, dynamic>>> search(
    String query, {
    int limit = 100,
    String? categoryFilter,
  }) async {
    if (query.isEmpty) {
      return [];
    }

    try {
      await _logger.log(
        level: 'info',
        subsystem: 'search_index',
        message: 'Starting smart search',
        extra: {
          'query': query,
          'limit': limit,
          'category_filter': categoryFilter,
        },
      );

      final db = await _appDatabase.ensureInitialized();
      final store = _appDatabase.audiobookMetadataStore;

      // Normalize query
      final normalizedQuery = _normalizeQuery(query);
      final queryWords =
          normalizedQuery.split(' ').where((w) => w.isNotEmpty).toList();

      if (queryWords.isEmpty) {
        return [];
      }

      // Build filter to exclude stale entries
      Filter? filter;
      if (categoryFilter != null) {
        filter = Filter.and([
          Filter.equals('is_stale', false),
          Filter.equals('category', categoryFilter),
        ]);
      } else {
        filter = Filter.equals('is_stale', false);
      }

      // Find records with filter (more efficient than loading all)
      final finder = Finder(filter: filter);
      final records = await store.find(db, finder: finder);
      final candidates = <Map<String, dynamic>>[];

      for (final record in records) {
        final metadata = record.value;

        // Calculate relevance score
        final score =
            _calculateRelevanceScore(metadata, queryWords, normalizedQuery);
        if (score > 0) {
          candidates.add({
            ...metadata,
            '_relevance_score': score,
          });
        }
      }

      // Sort by relevance (descending) and limit results
      candidates.sort((a, b) {
        final scoreA = a['_relevance_score'] as double;
        final scoreB = b['_relevance_score'] as double;
        return scoreB.compareTo(scoreA);
      });

      final results = candidates.take(limit).toList();

      // Remove internal relevance score before returning
      for (final result in results) {
        result.remove('_relevance_score');
      }

      await _logger.log(
        level: 'info',
        subsystem: 'search_index',
        message: 'Smart search completed',
        extra: {
          'query': query,
          'results_count': results.length,
        },
      );

      return results;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'search_index',
        message: 'Failed to perform smart search',
        cause: e.toString(),
        extra: {'query': query},
      );
      return [];
    }
  }

  /// Normalizes search query.
  ///
  /// Converts to lowercase, removes extra spaces, and trims.
  String _normalizeQuery(String query) =>
      query.toLowerCase().trim().replaceAll(RegExp(r'\s+'), ' ');

  /// Calculates relevance score for a metadata record.
  ///
  /// Higher score means better match. Scoring priorities:
  /// - Exact match in title: 100 points
  /// - Title starts with query: 80 points
  /// - Title contains query: 60 points
  /// - Exact match in author: 50 points
  /// - Author contains query: 30 points
  /// - Match in search_text: 20 points
  /// - Match in keywords: 10 points
  /// - Match in series: 5 points
  double _calculateRelevanceScore(
    Map<String, dynamic> metadata,
    List<String> queryWords,
    String normalizedQuery,
  ) {
    var score = 0.0;

    final titleLower = (metadata['title_lower'] as String? ?? '').toLowerCase();
    final authorLower =
        (metadata['author_lower'] as String? ?? '').toLowerCase();
    final searchTextLower =
        (metadata['search_text_lower'] as String? ?? '').toLowerCase();
    final keywords = (metadata['keywords'] as List<dynamic>? ?? [])
        .map((k) => k.toString().toLowerCase())
        .toList();
    final series = (metadata['series'] as String? ?? '').toLowerCase();
    final performer = (metadata['performer'] as String? ?? '').toLowerCase();

    // Check each query word
    for (final word in queryWords) {
      if (word.isEmpty) continue;

      // Exact match in title (highest priority)
      if (titleLower == word) {
        score += 100.0;
      } else if (titleLower.startsWith(word)) {
        // Title starts with query word
        score += 80.0;
      } else if (titleLower.contains(word)) {
        // Title contains query word
        score += 60.0;
      }

      // Exact match in author
      if (authorLower == word) {
        score += 50.0;
      } else if (authorLower.contains(word)) {
        // Author contains query word
        score += 30.0;
      }

      // Match in performer
      if (performer.contains(word)) {
        score += 25.0;
      }

      // Match in search_text (full-text search)
      if (searchTextLower.contains(word)) {
        score += 20.0;
      }

      // Match in keywords
      for (final keyword in keywords) {
        if (keyword.contains(word)) {
          score += 10.0;
          break; // Count each keyword match only once per word
        }
      }

      // Match in series
      if (series.contains(word)) {
        score += 5.0;
      }
    }

    // Bonus for exact phrase match in title
    if (titleLower.contains(normalizedQuery)) {
      score += 50.0;
    }

    // Bonus for exact phrase match in author
    if (authorLower.contains(normalizedQuery)) {
      score += 30.0;
    }

    return score;
  }

  /// Indexes an audiobook for search.
  ///
  /// This method extracts keywords and updates search_text fields.
  /// In the current implementation, indexing happens automatically
  /// when metadata is saved (search_text is pre-computed).
  ///
  /// This method can be used to rebuild index or update existing entries.
  Future<void> indexAudiobook(Map<String, dynamic> audiobook) async {
    try {
      final db = await _appDatabase.ensureInitialized();
      final store = _appDatabase.audiobookMetadataStore;

      final topicId = audiobook['topic_id'] as String? ?? '';
      if (topicId.isEmpty) {
        return;
      }

      // Extract keywords from description or other fields if needed
      final keywords = _extractKeywords(audiobook);

      // Build search text
      final searchParts = <String>[
        audiobook['title'] as String? ?? '',
        audiobook['author'] as String? ?? '',
        if (audiobook['performer'] != null) audiobook['performer'] as String,
        ...(audiobook['genres'] as List<dynamic>? ?? [])
            .map((g) => g.toString()),
        ...keywords,
      ];
      final searchText = searchParts.join(' ');

      // Update metadata with indexed fields
      final updatedMetadata = Map<String, dynamic>.from(audiobook);
      updatedMetadata['keywords'] = keywords;
      updatedMetadata['search_text'] = searchText;
      updatedMetadata['search_text_lower'] = searchText.toLowerCase();

      await store.record(topicId).put(db, updatedMetadata);

      await _logger.log(
        level: 'debug',
        subsystem: 'search_index',
        message: 'Audiobook indexed',
        extra: {'topic_id': topicId},
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'warning',
        subsystem: 'search_index',
        message: 'Failed to index audiobook',
        cause: e.toString(),
      );
    }
  }

  /// Extracts keywords from audiobook metadata.
  ///
  /// Currently extracts from title, author, and genres.
  /// Can be extended to extract from description if available.
  List<String> _extractKeywords(Map<String, dynamic> audiobook) {
    final keywords = <String>[];

    // Extract meaningful words from title (skip common words)
    final title = audiobook['title'] as String? ?? '';
    final titleWords = title
        .toLowerCase()
        .split(RegExp(r'[\s\-_.,;:!?()\[\]{}]+'))
        .where((w) => w.length > 2)
        .where((w) => !_isCommonWord(w))
        .toList();
    keywords.addAll(titleWords);

    // Extract from author
    final author = audiobook['author'] as String? ?? '';
    final authorWords = author
        .toLowerCase()
        .split(RegExp(r'[\s\-_.,;:!?()\[\]{}]+'))
        .where((w) => w.length > 2)
        .where((w) => !_isCommonWord(w))
        .toList();
    keywords.addAll(authorWords);

    // Genres are already keywords
    final genres = (audiobook['genres'] as List<dynamic>? ?? [])
        .map((g) => g.toString().toLowerCase())
        .toList();
    keywords.addAll(genres);

    // Remove duplicates and return
    return keywords.toSet().toList();
  }

  /// Checks if a word is a common word that should be excluded from keywords.
  bool _isCommonWord(String word) {
    const commonWords = {
      'the',
      'a',
      'an',
      'and',
      'or',
      'but',
      'in',
      'on',
      'at',
      'to',
      'for',
      'of',
      'with',
      'by',
      'from',
      'as',
      'is',
      'was',
      'are',
      'were',
      'be',
      'been',
      'being',
      'have',
      'has',
      'had',
      'do',
      'does',
      'did',
      'will',
      'would',
      'could',
      'should',
      'may',
      'might',
      'must',
      'can',
      'this',
      'that',
      'these',
      'those',
      'i',
      'you',
      'he',
      'she',
      'it',
      'we',
      'they',
      'книга',
      'книги',
      'аудио',
      'аудиокнига',
      'аудиокниги',
      'автор',
      'читает',
      'читатель',
      'часть',
      'части',
      'том',
      'тома',
    };
    return commonWords.contains(word.toLowerCase());
  }

  /// Rebuilds the search index for all cached audiobooks.
  ///
  /// This method re-indexes all entries in the cache, useful after
  /// schema changes or to update search_text fields.
  Future<void> rebuildIndex() async {
    try {
      await _logger.log(
        level: 'info',
        subsystem: 'search_index',
        message: 'Starting index rebuild',
      );

      final db = await _appDatabase.ensureInitialized();
      final store = _appDatabase.audiobookMetadataStore;
      final records = await store.find(db);

      var indexed = 0;
      for (final record in records) {
        await indexAudiobook(record.value);
        indexed++;
      }

      await _logger.log(
        level: 'info',
        subsystem: 'search_index',
        message: 'Index rebuild completed',
        extra: {'indexed_count': indexed},
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'search_index',
        message: 'Failed to rebuild index',
        cause: e.toString(),
      );
      rethrow;
    }
  }

  /// Clears the search index.
  ///
  /// Note: This doesn't delete the metadata, only clears indexed fields.
  /// To fully clear, use SmartSearchCacheService.clearCache().
  Future<void> clearIndex() async {
    try {
      await _logger.log(
        level: 'info',
        subsystem: 'search_index',
        message: 'Clearing search index',
      );

      final db = await _appDatabase.ensureInitialized();
      final store = _appDatabase.audiobookMetadataStore;
      final records = await store.find(db);

      for (final record in records) {
        final metadata = Map<String, dynamic>.from(record.value)
          ..remove('keywords')
          ..remove('search_text')
          ..remove('search_text_lower');
        await store.record(record.key).put(db, metadata);
      }

      await _logger.log(
        level: 'info',
        subsystem: 'search_index',
        message: 'Search index cleared',
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'search_index',
        message: 'Failed to clear index',
        cause: e.toString(),
      );
      rethrow;
    }
  }
}
