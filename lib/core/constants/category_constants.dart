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

/// Constants for RuTracker category IDs and names
class CategoryConstants {
  /// Private constructor to prevent instantiation
  CategoryConstants._();

  /// Audiobooks main category ID
  static const String audiobooksCategoryId = '33';

  /// Popular categories for featured content
  static const List<String> popularCategoryIds = ['574', '1036', '400'];

  /// Category ID to name mapping
  static const Map<String, String> categoryNameMap = {
    '574': 'Радиоспектакли',
    '1036': 'Биографии и мемуары',
    '400': 'История и философия',
    '395': 'Новости и информация',
    '2322': 'Общение и обсуждения',
  };

  /// Default category name fallback
  static const String defaultCategoryName = 'Аудиокниги';

  /// Search sort parameter for newest results
  static const String searchSortNewest = 'tm=-1';

  /// Number of search results per page
  static const int searchResultsPerPage = 50;

  /// Magnet URL template with topic ID placeholder
  static const String magnetUrlTemplate =
      'magnet:?xt=urn:btih:\$topicId&dn=RuTracker+Audiobook&tr=udp://tracker.opentrackr.org:1337/announce';

  /// Sorting options
  static const List<Map<String, String>> defaultSortingOptions = [
    {'value': '0', 'label': 'Посл. сообщение'},
    {'value': '1', 'label': 'Название темы'},
    {'value': '2', 'label': 'Время размещения'},
  ];
}
