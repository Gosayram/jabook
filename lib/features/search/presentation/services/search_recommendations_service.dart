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

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/core/constants/category_constants.dart';
import 'package:jabook/core/di/providers/auth_providers.dart';
import 'package:jabook/core/domain/auth/entities/auth_status.dart';
import 'package:jabook/core/infrastructure/endpoints/endpoint_provider.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/parse/category_parser.dart' as category_parser;
import 'package:jabook/features/search/presentation/widgets/recommended_audiobooks_widget.dart';

/// Service for loading recommended audiobooks from categories.
class SearchRecommendationsService {
  /// Creates a new SearchRecommendationsService instance.
  const SearchRecommendationsService(this.ref);

  /// Riverpod reference for accessing providers.
  final WidgetRef ref;

  /// Returns list of recommended audiobooks.
  ///
  /// Gets new books from different categories, sorted by newest first.
  /// Only returns books from actual category pages, no static fallback.
  Future<List<RecommendedAudiobook>> getRecommendedAudiobooks() async {
    final structuredLogger = StructuredLogger();
    await structuredLogger.log(
      level: 'debug',
      subsystem: 'recommendations',
      message: 'getRecommendedAudiobooks called',
      context: 'recommendations_load',
    );
    return getCategoryRecommendations();
  }

  /// Gets recommendations from different categories.
  ///
  /// Fetches new books from popular categories using viewforum.php.
  /// Gets the first few books from each category (they are usually newest).
  Future<List<RecommendedAudiobook>> getCategoryRecommendations() async {
    final structuredLogger = StructuredLogger();
    try {
      await structuredLogger.log(
        level: 'info',
        subsystem: 'recommendations',
        message: 'Starting to load category recommendations',
        context: 'recommendations_load',
      );

      // Check if user is authenticated before making requests
      var authStatus = ref.read(authStatusProvider);
      var isAuthenticated = authStatus.value?.isAuthenticated ?? false;

      // If provider shows not authenticated but is loading, wait a bit for update
      if (!isAuthenticated && authStatus.isLoading) {
        await structuredLogger.log(
          level: 'debug',
          subsystem: 'recommendations',
          message: 'Auth status is loading, waiting for update',
          context: 'recommendations_load',
        );
        await Future.delayed(const Duration(milliseconds: 500));
        authStatus = ref.read(authStatusProvider);
        isAuthenticated = authStatus.value?.isAuthenticated ?? false;
      }

      // If still not authenticated, try to refresh auth status
      if (!isAuthenticated && !authStatus.isLoading) {
        await structuredLogger.log(
          level: 'debug',
          subsystem: 'recommendations',
          message: 'Auth status not authenticated, trying to refresh',
          context: 'recommendations_load',
        );
        try {
          await ref.read(authRepositoryProvider).refreshAuthStatus();
          final loggedIn = await ref.read(authRepositoryProvider).isLoggedIn();
          if (loggedIn) {
            isAuthenticated = true;
            await structuredLogger.log(
              level: 'info',
              subsystem: 'recommendations',
              message:
                  'Auth status refreshed - user is authenticated (checked directly)',
              context: 'recommendations_load',
            );
          } else {
            await Future.delayed(const Duration(milliseconds: 300));
            authStatus = ref.read(authStatusProvider);
            isAuthenticated = authStatus.value?.isAuthenticated ?? false;
          }
        } on Exception catch (e) {
          await structuredLogger.log(
            level: 'warning',
            subsystem: 'recommendations',
            message: 'Failed to refresh auth status',
            context: 'recommendations_load',
            extra: {
              'error': e.toString(),
            },
          );
        }
      }

      await structuredLogger.log(
        level: 'debug',
        subsystem: 'recommendations',
        message: 'Checking authentication status',
        context: 'recommendations_load',
        extra: {
          'is_authenticated': isAuthenticated,
          'auth_status_value': authStatus.value?.toString() ?? 'null',
          'auth_status_loading': authStatus.isLoading,
        },
      );

      if (!isAuthenticated) {
        await structuredLogger.log(
          level: 'warning',
          subsystem: 'recommendations',
          message: 'User not authenticated, skipping category recommendations',
          context: 'recommendations_load',
        );
        return [];
      }

      await structuredLogger.log(
        level: 'info',
        subsystem: 'recommendations',
        message:
            'User is authenticated, proceeding with category recommendations',
        context: 'recommendations_load',
      );

      final endpointManager = ref.read(endpointManagerProvider);
      final baseUrl = await endpointManager.getActiveEndpoint();
      final dio = await DioClient.instance;
      final categoryParser = category_parser.CategoryParser();

      await structuredLogger.log(
        level: 'debug',
        subsystem: 'recommendations',
        message: 'Initialized services for recommendations',
        context: 'recommendations_load',
        extra: {
          'base_url': baseUrl,
        },
      );

      final recommendations = <RecommendedAudiobook>[];
      final seenIds = <String>{};

      // Use popular categories from CategoryConstants
      const categories = CategoryConstants.popularCategoryIds;
      if (categories.isEmpty) {
        await structuredLogger.log(
          level: 'warning',
          subsystem: 'recommendations',
          message: 'No popular categories found in CategoryConstants',
          context: 'recommendations_load',
        );
        return [];
      }

      await structuredLogger.log(
        level: 'info',
        subsystem: 'recommendations',
        message: 'Loading recommendations from categories',
        context: 'recommendations_load',
        extra: {
          'categories_count': categories.length,
          'category_ids': categories,
        },
      );

      // Get 5-10 new books from each category (first books are usually newest)
      final booksPerCategory = (10 / categories.length).ceil().clamp(1, 3);
      await structuredLogger.log(
        level: 'debug',
        subsystem: 'recommendations',
        message: 'Calculated books per category',
        context: 'recommendations_load',
        extra: {
          'books_per_category': booksPerCategory,
        },
      );

      // Fetch from all categories in parallel with timeout
      final futures = categories.map((categoryId) async {
        try {
          final result = await fetchNewBooksFromCategory(
            dio: dio,
            endpoint: baseUrl,
            categoryParser: categoryParser,
            categoryId: categoryId,
            limit: booksPerCategory,
          ).timeout(
            const Duration(seconds: 8),
            onTimeout: () async {
              await structuredLogger.log(
                level: 'warning',
                subsystem: 'recommendations',
                message: 'Timeout loading category',
                context: 'recommendations_load',
                extra: {
                  'category_id': categoryId,
                },
              );
              return <RecommendedAudiobook>[];
            },
          );
          await structuredLogger.log(
            level: 'info',
            subsystem: 'recommendations',
            message: 'Loaded books from category',
            context: 'recommendations_load',
            extra: {
              'category_id': categoryId,
              'books_count': result.length,
            },
          );
          return result;
        } on Exception catch (e) {
          await structuredLogger.log(
            level: 'warning',
            subsystem: 'recommendations',
            message: 'Error loading category',
            context: 'recommendations_load',
            extra: {
              'category_id': categoryId,
              'error': e.toString(),
            },
          );
          return <RecommendedAudiobook>[];
        }
      });

      final results = await Future.wait(futures);

      // Combine results from all categories
      for (final categoryResults in results) {
        for (final result in categoryResults) {
          if (seenIds.contains(result.id)) continue;
          seenIds.add(result.id);
          recommendations.add(result);
          if (recommendations.length >= 10) break;
        }
        if (recommendations.length >= 10) break;
      }

      await structuredLogger.log(
        level: 'info',
        subsystem: 'recommendations',
        message: 'Successfully loaded recommended audiobooks',
        context: 'recommendations_load',
        extra: {
          'total_recommendations': recommendations.length,
          'unique_ids_count': seenIds.length,
        },
      );
      return recommendations;
    } on Exception catch (e, stackTrace) {
      await structuredLogger.log(
        level: 'error',
        subsystem: 'recommendations',
        message: 'Failed to load category recommendations',
        context: 'recommendations_load',
        extra: {
          'error': e.toString(),
          'stack_trace': stackTrace.toString(),
        },
      );
      return [];
    }
  }

  /// Fetches new books from a specific category using viewforum.php.
  ///
  /// Gets the first few books from the category page (they are usually newest).
  Future<List<RecommendedAudiobook>> fetchNewBooksFromCategory({
    required Dio dio,
    required String endpoint,
    required category_parser.CategoryParser categoryParser,
    required String categoryId,
    required int limit,
  }) async {
    final structuredLogger = StructuredLogger();
    try {
      // Use viewforum.php to get books from category
      final baseUrl = endpoint.endsWith('/')
          ? endpoint.substring(0, endpoint.length - 1)
          : endpoint;
      final forumUrl = '$baseUrl/forum/viewforum.php?f=$categoryId';

      await structuredLogger.log(
        level: 'debug',
        subsystem: 'recommendations',
        message: 'Fetching books from category',
        context: 'category_fetch',
        extra: {
          'category_id': categoryId,
          'forum_url': forumUrl,
          'limit': limit,
        },
      );

      final response = await dio.get(
        forumUrl,
        options: Options(
          responseType: ResponseType.plain,
          headers: {
            'Accept': 'text/html,application/xhtml+xml,application/xml',
            'Accept-Charset': 'windows-1251,utf-8',
          },
        ),
      );

      await structuredLogger.log(
        level: 'info',
        subsystem: 'recommendations',
        message: 'HTTP response received for category',
        context: 'category_fetch',
        extra: {
          'category_id': categoryId,
          'status_code': response.statusCode,
          'response_url': response.realUri.toString(),
          'response_size_bytes': response.data?.toString().length ?? 0,
        },
      );

      // Check if we got redirected to login page
      final responseUrl = response.realUri.toString();
      if (responseUrl.contains('login.php')) {
        await structuredLogger.log(
          level: 'warning',
          subsystem: 'recommendations',
          message: 'Category requires authentication (redirected to login)',
          context: 'category_fetch',
          extra: {
            'category_id': categoryId,
            'response_url': responseUrl,
          },
        );
        return [];
      }

      if (response.statusCode != 200) {
        await structuredLogger.log(
          level: 'warning',
          subsystem: 'recommendations',
          message: 'Failed to fetch category',
          context: 'category_fetch',
          extra: {
            'category_id': categoryId,
            'status_code': response.statusCode,
          },
        );
        return [];
      }

      // Check if response contains login page content
      final responseText = response.data.toString();
      if (responseText.contains('login.php') ||
          responseText.contains('Вход в систему') ||
          responseText.contains('Имя пользователя') ||
          responseText.contains('Пароль')) {
        await structuredLogger.log(
          level: 'warning',
          subsystem: 'recommendations',
          message: 'Category returned login page instead of topics',
          context: 'category_fetch',
          extra: {
            'category_id': categoryId,
          },
        );
        return [];
      }

      // Parse topics from category page using CategoryParser
      final topics = await categoryParser.parseCategoryTopics(responseText);

      await structuredLogger.log(
        level: 'info',
        subsystem: 'recommendations',
        message: 'Parsed topics from category',
        context: 'category_fetch',
        extra: {
          'category_id': categoryId,
          'topics_count': topics.length,
        },
      );

      // Convert to RecommendedAudiobook
      final now = DateTime.now();
      final oneHundredEightyDaysAgo = now.subtract(const Duration(days: 180));

      await structuredLogger.log(
        level: 'debug',
        subsystem: 'recommendations',
        message: 'Filtering topics by date',
        context: 'category_fetch',
        extra: {
          'category_id': categoryId,
          'topics_before_filter': topics.length,
          'cutoff_date': oneHundredEightyDaysAgo.toIso8601String(),
        },
      );

      // Filter topics by date
      var topicsWithoutDate = 0;
      var topicsTooOld = 0;
      final recentTopics = topics.where((topic) {
        final lastPostDate = topic['last_post_date'] as DateTime?;
        if (lastPostDate == null) {
          topicsWithoutDate++;
          return false;
        }
        final isRecent = lastPostDate.isAfter(oneHundredEightyDaysAgo);
        if (!isRecent) {
          topicsTooOld++;
        }
        return isRecent;
      }).toList();

      await structuredLogger.log(
        level: 'debug',
        subsystem: 'recommendations',
        message: 'Date filtering results',
        context: 'category_fetch',
        extra: {
          'category_id': categoryId,
          'topics_before_filter': topics.length,
          'recent_topics_count': recentTopics.length,
          'topics_without_date': topicsWithoutDate,
          'topics_too_old': topicsTooOld,
        },
      );

      // Use recent topics if we have enough, otherwise fall back to all topics
      final topicsToUse = recentTopics.length >= limit ? recentTopics : topics;

      await structuredLogger.log(
        level: 'info',
        subsystem: 'recommendations',
        message: 'Selected topics for recommendations',
        context: 'category_fetch',
        extra: {
          'category_id': categoryId,
          'using_recent_only': recentTopics.length >= limit,
          'topics_selected': topicsToUse.length,
        },
      );

      final books = topicsToUse
          .take(limit)
          .map((topic) {
            final topicId = topic['id'] as String? ?? '';
            final title = topic['title'] as String? ?? 'Unknown Title';
            final author = topic['author'] as String? ?? 'Unknown Author';
            final size = topic['size'] as String?;
            final coverUrl = topic['coverUrl'] as String?;
            final categoryName =
                CategoryConstants.categoryNameMap[categoryId] ?? 'Audiobook';

            return RecommendedAudiobook(
              id: topicId,
              title: title,
              author: author,
              size: size,
              coverUrl: coverUrl,
              genre: categoryName,
            );
          })
          .where((book) => book.id.isNotEmpty)
          .toList();

      await structuredLogger.log(
        level: 'info',
        subsystem: 'recommendations',
        message: 'Converted valid books from category',
        context: 'category_fetch',
        extra: {
          'category_id': categoryId,
          'valid_books_count': books.length,
          'topics_parsed': topics.length,
        },
      );
      return books;
    } on Exception catch (e, stackTrace) {
      await structuredLogger.log(
        level: 'error',
        subsystem: 'recommendations',
        message: 'Exception fetching category',
        context: 'category_fetch',
        extra: {
          'category_id': categoryId,
          'error': e.toString(),
          'stack_trace': stackTrace.toString(),
        },
      );
      return [];
    }
  }
}
