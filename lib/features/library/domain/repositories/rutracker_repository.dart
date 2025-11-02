import 'package:jabook/features/library/domain/entities/audiobook.dart';
import 'package:jabook/features/library/domain/entities/audiobook_category.dart';

/// Repository interface for interacting with RuTracker
abstract class RuTrackerRepository {
  /// Searches for audiobooks by query
  Future<List<Audiobook>> searchAudiobooks(String query, {int page = 1});

  /// Gets all available audiobook categories
  Future<List<AudiobookCategory>> getCategories();

  /// Gets audiobooks from a specific category
  Future<List<Audiobook>> getCategoryAudiobooks(String categoryId,
      {int page = 1});

  /// Gets detailed information about a specific audiobook
  Future<Audiobook?> getAudiobookDetails(String audiobookId);

  /// Gets pagination information for a URL
  Future<Map<String, dynamic>> getPaginationInfo(String url);

  /// Gets available sorting options for a category
  Future<List<Map<String, String>>> getSortingOptions(String categoryId);

  /// Gets featured audiobooks (popular or recommended)
  Future<List<Audiobook>> getFeaturedAudiobooks();

  /// Gets new releases
  Future<List<Audiobook>> getNewReleases();
}
