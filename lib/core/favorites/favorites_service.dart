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

import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:jabook/core/parse/rutracker_parser.dart';
import 'package:sembast/sembast.dart';

/// Service for managing user's favorite audiobooks.
///
/// This service provides methods to add, remove, and retrieve
/// favorite audiobooks stored locally in the database.
class FavoritesService {
  /// Creates a new instance of FavoritesService.
  ///
  /// The [db] parameter is the database instance for storing favorites.
  FavoritesService(this._db);

  /// Database instance for favorites storage.
  final Database _db;

  /// Store reference for favorites.
  final StoreRef<String, Map<String, dynamic>> _store = StoreRef('favorites');

  /// Adds an audiobook to favorites.
  ///
  /// The [audiobook] parameter is the audiobook to add.
  /// Automatically handles duplicates (updates existing entry).
  Future<void> addToFavorites(Audiobook audiobook) async {
    try {
      final map = _audiobookToMap(audiobook);
      await _store.record(audiobook.id).put(_db, map);

      await StructuredLogger().log(
        level: 'info',
        subsystem: 'favorites',
        message: 'Added audiobook to favorites',
        extra: {'topic_id': audiobook.id, 'title': audiobook.title},
      );
    } on Exception catch (e) {
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'favorites',
        message: 'Failed to add audiobook to favorites',
        extra: {'topic_id': audiobook.id},
        cause: e.toString(),
      );
      rethrow;
    }
  }

  /// Adds an audiobook to favorites from Map format.
  ///
  /// The [audiobookMap] parameter contains audiobook data as Map.
  /// Automatically handles duplicates (updates existing entry).
  Future<void> addToFavoritesFromMap(Map<String, dynamic> audiobookMap) async {
    try {
      final topicId = audiobookMap['id'] as String? ?? '';
      if (topicId.isEmpty) return;

      // Convert Map format to storage format
      final map = {
        'topic_id': topicId,
        'title': audiobookMap['title'] as String? ?? '',
        'author': audiobookMap['author'] as String? ?? '',
        'category': audiobookMap['category'] as String? ?? '',
        'size': audiobookMap['size'] as String? ?? '',
        'seeders': audiobookMap['seeders'] as int? ?? 0,
        'leechers': audiobookMap['leechers'] as int? ?? 0,
        'magnet_url': audiobookMap['magnetUrl'] as String? ?? '',
        'cover_url': audiobookMap['coverUrl'] as String?,
        'added_date': audiobookMap['addedDate'] as String? ??
            DateTime.now().toIso8601String(),
        'added_to_favorites': DateTime.now().toIso8601String(),
        'chapters': audiobookMap['chapters'] as List<dynamic>? ?? [],
      };

      await _store.record(topicId).put(_db, map);

      await StructuredLogger().log(
        level: 'info',
        subsystem: 'favorites',
        message: 'Added audiobook to favorites',
        extra: {'topic_id': topicId},
      );
    } on Exception catch (e) {
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'favorites',
        message: 'Failed to add audiobook to favorites',
        cause: e.toString(),
      );
      rethrow;
    }
  }

  /// Removes an audiobook from favorites.
  ///
  /// The [topicId] parameter is the topic ID of the audiobook to remove.
  Future<void> removeFromFavorites(String topicId) async {
    try {
      await _store.record(topicId).delete(_db);

      await StructuredLogger().log(
        level: 'info',
        subsystem: 'favorites',
        message: 'Removed audiobook from favorites',
        extra: {'topic_id': topicId},
      );
    } on Exception catch (e) {
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'favorites',
        message: 'Failed to remove audiobook from favorites',
        extra: {'topic_id': topicId},
        cause: e.toString(),
      );
      rethrow;
    }
  }

  /// Checks if an audiobook is in favorites.
  ///
  /// The [topicId] parameter is the topic ID to check.
  ///
  /// Returns true if the audiobook is in favorites, false otherwise.
  Future<bool> isFavorite(String topicId) async {
    try {
      final record = await _store.record(topicId).get(_db);
      return record != null;
    } on Exception {
      return false;
    }
  }

  /// Gets all favorite audiobooks.
  ///
  /// Returns a list of all favorite audiobooks, sorted by date added (newest first).
  Future<List<Audiobook>> getAllFavorites() async {
    try {
      final finder = Finder(
        sortOrders: [
          SortOrder('added_to_favorites', false)
        ], // Descending order
      );
      final records = await _store.find(_db, finder: finder);

      return records.map((record) => _mapToAudiobook(record.value)).toList();
    } on Exception catch (e) {
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'favorites',
        message: 'Failed to get all favorites',
        cause: e.toString(),
      );
      return [];
    }
  }

  /// Gets favorites count.
  ///
  /// Returns the total number of favorite audiobooks.
  Future<int> getFavoritesCount() async {
    try {
      final records = await _store.find(_db);
      return records.length;
    } on Exception {
      return 0;
    }
  }

  /// Removes multiple favorites by topic IDs.
  ///
  /// The [topicIds] parameter is a list of topic IDs to remove.
  Future<void> removeMultipleFromFavorites(List<String> topicIds) async {
    try {
      for (final topicId in topicIds) {
        await _store.record(topicId).delete(_db);
      }

      await StructuredLogger().log(
        level: 'info',
        subsystem: 'favorites',
        message: 'Removed multiple audiobooks from favorites',
        extra: {'count': topicIds.length},
      );
    } on Exception catch (e) {
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'favorites',
        message: 'Failed to remove multiple audiobooks from favorites',
        extra: {'count': topicIds.length},
        cause: e.toString(),
      );
      rethrow;
    }
  }

  /// Clears all favorites.
  ///
  /// Removes all favorite audiobooks from the database.
  Future<void> clearAllFavorites() async {
    try {
      await _store.delete(_db);

      await StructuredLogger().log(
        level: 'info',
        subsystem: 'favorites',
        message: 'Cleared all favorites',
      );
    } on Exception catch (e) {
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'favorites',
        message: 'Failed to clear all favorites',
        cause: e.toString(),
      );
      rethrow;
    }
  }

  /// Converts an Audiobook to a Map for storage.
  Map<String, dynamic> _audiobookToMap(Audiobook audiobook) => {
        'topic_id': audiobook.id,
        'title': audiobook.title,
        'author': audiobook.author,
        'category': audiobook.category,
        'size': audiobook.size,
        'seeders': audiobook.seeders,
        'leechers': audiobook.leechers,
        'magnet_url': audiobook.magnetUrl,
        'cover_url': audiobook.coverUrl,
        'performer': audiobook.performer,
        'genres': audiobook.genres,
        'added_date': audiobook.addedDate.toIso8601String(),
        'added_to_favorites': DateTime.now().toIso8601String(),
        'chapters': audiobook.chapters
            .map((c) => {
                  'title': c.title,
                  'duration_ms': c.durationMs,
                  'file_index': c.fileIndex,
                  'start_byte': c.startByte,
                  'end_byte': c.endByte,
                })
            .toList(),
        'duration': audiobook.duration,
        'bitrate': audiobook.bitrate,
        'audio_codec': audiobook.audioCodec,
      };

  /// Converts a stored Map back to an Audiobook entity.
  Audiobook _mapToAudiobook(Map<String, dynamic> map) {
    final chapters = (map['chapters'] as List<dynamic>?)
            ?.map((c) => Chapter(
                  title: c['title'] as String? ?? '',
                  durationMs: c['duration_ms'] as int? ?? 0,
                  fileIndex: c['file_index'] as int? ?? 0,
                  startByte: c['start_byte'] as int? ?? 0,
                  endByte: c['end_byte'] as int? ?? 0,
                ))
            .toList() ??
        <Chapter>[];

    return Audiobook(
      id: map['topic_id'] as String? ?? '',
      title: map['title'] as String? ?? '',
      author: map['author'] as String? ?? '',
      category: map['category'] as String? ?? '',
      size: map['size'] as String? ?? '',
      seeders: map['seeders'] as int? ?? 0,
      leechers: map['leechers'] as int? ?? 0,
      magnetUrl: map['magnet_url'] as String? ?? '',
      coverUrl: map['cover_url'] as String?,
      performer: map['performer'] as String?,
      genres: (map['genres'] as List<dynamic>?)
              ?.map((g) => g.toString())
              .where((g) => g.isNotEmpty)
              .toList() ??
          const [],
      chapters: chapters,
      addedDate: map['added_date'] != null
          ? DateTime.parse(map['added_date'] as String)
          : DateTime.now(),
      duration: map['duration'] as String?,
      bitrate: map['bitrate'] as String?,
      audioCodec: map['audio_codec'] as String?,
    );
  }
}
