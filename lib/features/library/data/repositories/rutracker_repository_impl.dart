import 'package:dio/dio.dart';
import 'package:jabook/core/constants/category_constants.dart';
import 'package:jabook/core/endpoints/url_constants.dart';
import 'package:jabook/core/errors/failures.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/net/rutracker_service.dart';
import 'package:jabook/core/parse/category_parser.dart' as category_parser;
import 'package:jabook/core/parse/rutracker_parser.dart' as rutracker_parser;
import 'package:jabook/features/library/domain/entities/audiobook.dart';
import 'package:jabook/features/library/domain/entities/audiobook_category.dart';
import 'package:jabook/features/library/domain/repositories/rutracker_repository.dart';

/// Implementation of RuTrackerRepository with improved parsing
/// based on actual RuTracker HTML structure.
class RuTrackerRepositoryImpl implements RuTrackerRepository {
  /// Creates a new RuTrackerRepositoryImpl instance.
  RuTrackerRepositoryImpl({
    required rutracker_parser.RuTrackerParser parser,
    required category_parser.CategoryParser categoryParser,
    required RuTrackerService rutrackerService,
  })  : _parser = parser,
        _categoryParser = categoryParser,
        _rutrackerService = rutrackerService;

  final rutracker_parser.RuTrackerParser _parser;
  final category_parser.CategoryParser _categoryParser;
  final RuTrackerService _rutrackerService;

  @override
  Future<List<Audiobook>> searchAudiobooks(String query, {int page = 1}) async {
    try {
      final dio = await DioClient.instance;
      
      // Build search URL with proper RuTracker parameters
      final searchUrl = '${RuTrackerUrls.search}?nm=$query&f=${CategoryConstants.audiobooksCategoryId}&start=${(page - 1) * CategoryConstants.searchResultsPerPage}';
      
      final response = await dio.get(
        searchUrl,
        options: Options(
          responseType: ResponseType.plain,
          headers: {
            'Accept': 'text/html,application/xhtml+xml,application/xml',
            'Accept-Charset': 'windows-1251,utf-8',
          },
        ),
      );

      final results = await _parser.parseSearchResults(response.data.toString());
      return results.map((audiobook) => Audiobook(
        id: audiobook.id,
        title: audiobook.title,
        author: audiobook.author,
        category: audiobook.category,
        size: audiobook.size,
        seeders: audiobook.seeders,
        leechers: audiobook.leechers,
        magnetUrl: audiobook.magnetUrl,
        coverUrl: audiobook.coverUrl,
        chapters: audiobook.chapters.map((chapter) => Chapter(
          title: chapter.title,
          durationMs: chapter.durationMs,
          fileIndex: chapter.fileIndex,
          startByte: chapter.startByte,
          endByte: chapter.endByte,
        )).toList(),
        addedDate: audiobook.addedDate,
      )).toList();
    } on DioException catch (e) {
      throw NetworkFailure('Search failed: ${e.message}');
    } on Exception {
      throw const NetworkFailure('Failed to search audiobooks');
    }
  }

  @override
  Future<List<AudiobookCategory>> getCategories() async {
    try {
      final dio = await DioClient.instance;
      
      final response = await dio.get(
        RuTrackerUrls.index,
        options: Options(
          responseType: ResponseType.plain,
          headers: {
            'Accept': 'text/html,application/xhtml+xml,application/xml',
            'Accept-Charset': 'windows-1251,utf-8',
          },
        ),
      );

      final categories = await category_parser.CategoryParser().parseCategories(response.data.toString());
      return categories.map((category) => AudiobookCategory(
        id: category.id,
        name: category.name,
        url: category.url,
        subcategories: category.subcategories.map((subcategory) => AudiobookCategory(
          id: subcategory.id,
          name: subcategory.name,
          url: subcategory.url,
        )).toList(),
      )).toList();
    } on DioException catch (e) {
      throw NetworkFailure('Failed to fetch categories: ${e.message}');
    } on Exception {
      throw const NetworkFailure('Failed to get categories');
    }
  }

  @override
  Future<List<Audiobook>> getCategoryAudiobooks(String categoryId, {int page = 1}) async {
    try {
      final html = await _rutrackerService.fetchPage(
        url: '${RuTrackerUrls.viewForum}?f=$categoryId',
        page: page,
      );

      // Parse topics from category page
      final topics = await _categoryParser.parseCategoryTopics(html);
      
      // Convert topics to audiobooks
      return topics.map((topic) => Audiobook(
        id: topic['id']?.toString() ?? '',
        title: topic['title']?.toString() ?? '',
        author: topic['author']?.toString() ?? 'Unknown',
        category: _extractCategoryFromForumId(categoryId),
        size: topic['size']?.toString() ?? '0 MB',
        seeders: topic['seeders'] as int? ?? 0,
        leechers: topic['leechers'] as int? ?? 0,
        magnetUrl: _buildMagnetUrl(topic['id']?.toString() ?? ''),
        chapters: [],
        addedDate: topic['added_date'] as DateTime? ?? DateTime.now(),
      )).toList();
    } on Exception {
      throw const NetworkFailure('Failed to get category audiobooks');
    }
  }

  @override
  Future<Audiobook?> getAudiobookDetails(String audiobookId) async {
    try {
      final dio = await DioClient.instance;
      
      final response = await dio.get(
        '${RuTrackerUrls.viewTopic}?t=$audiobookId',
        options: Options(
          responseType: ResponseType.plain,
          headers: {
            'Accept': 'text/html,application/xhtml+xml,application/xml',
            'Accept-Charset': 'windows-1251,utf-8',
          },
        ),
      );

      final details = await _parser.parseTopicDetails(response.data.toString());
      if (details == null) return null;
      
      return Audiobook(
        id: details.id,
        title: details.title,
        author: details.author,
        category: details.category,
        size: details.size,
        seeders: details.seeders,
        leechers: details.leechers,
        magnetUrl: details.magnetUrl,
        coverUrl: details.coverUrl,
        chapters: details.chapters.map((chapter) => Chapter(
          title: chapter.title,
          durationMs: chapter.durationMs,
          fileIndex: chapter.fileIndex,
          startByte: chapter.startByte,
          endByte: chapter.endByte,
        )).toList(),
        addedDate: details.addedDate,
      );
    } on DioException catch (e) {
      throw NetworkFailure('Failed to fetch audiobook details: ${e.message}');
    } on Exception {
      throw const NetworkFailure('Failed to get audiobook details');
    }
  }

  @override
  Future<Map<String, dynamic>> getPaginationInfo(String url) async {
    try {
      final html = await _rutrackerService.fetchPage(url: url);
      return _rutrackerService.parsePaginationInfo(html);
    } on Exception {
      return {'currentPage': 1, 'totalPages': 1, 'hasNext': false, 'hasPrevious': false};
    }
  }

  @override
  Future<List<Map<String, String>>> getSortingOptions(String categoryId) async {
    try {
      final html = await _rutrackerService.fetchPage(
        url: '${RuTrackerUrls.viewForum}?f=$categoryId',
      );
      return _rutrackerService.parseSortingOptions(html);
    } on Exception {
      return CategoryConstants.defaultSortingOptions;
    }
  }

  // Helper methods
  String _extractCategoryFromForumId(String forumId) =>
      CategoryConstants.categoryNameMap[forumId] ?? CategoryConstants.defaultCategoryName;

  String _buildMagnetUrl(String topicId) =>
      CategoryConstants.magnetUrlTemplate.replaceFirst('\$topicId', topicId);

  @override
  Future<List<Audiobook>> getFeaturedAudiobooks() async {
    // Get audiobooks from popular categories
    const popularCategories = CategoryConstants.popularCategoryIds;
    final featuredAudiobooks = <Audiobook>[];
    
    for (final categoryId in popularCategories) {
      try {
        final audiobooks = await getCategoryAudiobooks(categoryId);
        featuredAudiobooks.addAll(audiobooks.take(5)); // Take top 5 from each category
      } on Exception {
        continue;
      }
      
    }
    
    return featuredAudiobooks;
  }

  @override
  Future<List<Audiobook>> getNewReleases() async {
    try {
      // Get latest audiobooks from all categories
      final html = await _rutrackerService.fetchPage(
        url: '${RuTrackerUrls.tracker}?f=${CategoryConstants.audiobooksCategoryId}&${CategoryConstants.searchSortNewest}',
      );
      
      final results = await _parser.parseSearchResults(html);
      return results.map((audiobook) => Audiobook(
        id: audiobook.id,
        title: audiobook.title,
        author: audiobook.author,
        category: audiobook.category,
        size: audiobook.size,
        seeders: audiobook.seeders,
        leechers: audiobook.leechers,
        magnetUrl: audiobook.magnetUrl,
        coverUrl: audiobook.coverUrl,
        chapters: audiobook.chapters.map((chapter) => Chapter(
          title: chapter.title,
          durationMs: chapter.durationMs,
          fileIndex: chapter.fileIndex,
          startByte: chapter.startByte,
          endByte: chapter.endByte,
        )).toList(),
        addedDate: audiobook.addedDate,
      )).toList();
    } on Exception {
      return [];
    }
  }
}