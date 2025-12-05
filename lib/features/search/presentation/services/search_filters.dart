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

import 'package:jabook/core/data/remote/rutracker/rutracker_parser.dart';

/// Filters search results to only include audiobooks.
///
/// Since tracker.php returns ALL torrents (movies, games, books, audiobooks, etc.),
/// we need to filter results to only show audiobooks from category 33.
///
/// We identify audiobooks by:
/// 1. Checking if the title contains audiobook-related keywords
/// 2. Checking if the category field indicates it's an audiobook
/// 3. Checking if the result has characteristics of audiobooks (size, format, etc.)
List<Audiobook> filterAudiobookResults(List<Audiobook> allResults) {
  final audiobookKeywords = [
    'аудиокнига',
    'аудио',
    'радиоспектакль',
    'биография',
    'мемуары',
    'чтение',
    'читает',
    'исполнитель',
    'mp3',
    'flac',
    'm4b',
    'ogg',
  ];

  final excludedKeywords = [
    'фильм',
    'сериал',
    'игра',
    'программа',
    'музыка',
    'альбом',
    'трек',
    'песня',
    'видео',
    'dvd',
    'blu-ray',
    'bdrip',
    'dvdrip',
  ];

  return allResults.where((result) {
    final titleLower = result.title.toLowerCase();
    final categoryLower = result.category.toLowerCase();

    // Check if it contains audiobook-related keywords
    final hasAudiobookKeyword = audiobookKeywords.any(
      (keyword) =>
          titleLower.contains(keyword) || categoryLower.contains(keyword),
    );

    // Check if it contains excluded keywords (likely not an audiobook)
    final hasExcludedKeyword = excludedKeywords.any(
      titleLower.contains,
    );

    // Include if it has audiobook keywords and doesn't have excluded keywords
    // OR if the category explicitly indicates it's an audiobook
    return (hasAudiobookKeyword && !hasExcludedKeyword) ||
        categoryLower.contains('аудиокнига') ||
        categoryLower.contains('радиоспектакль') ||
        categoryLower.contains('биография') ||
        categoryLower.contains('мемуары');
  }).toList();
}
