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

import 'package:flutter/material.dart';
import 'package:jabook/core/data/remote/rutracker/rutracker_parser.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Converts Chapter entity to Map.
Map<String, dynamic> chapterToMap(Chapter chapter) => {
      'title': chapter.title,
      'durationMs': chapter.durationMs,
      'fileIndex': chapter.fileIndex,
      'startByte': chapter.startByte,
      'endByte': chapter.endByte,
    };

/// Formats cache expiration time for display.
String formatCacheExpiration(
  BuildContext context,
  DateTime expirationTime,
) {
  final now = DateTime.now();
  final difference = expirationTime.difference(now);

  if (difference.isNegative) {
    return AppLocalizations.of(context)?.cacheExpired ?? 'Expired';
  }

  if (difference.inDays > 0) {
    return '${difference.inDays} ${AppLocalizations.of(context)?.days ?? 'days'}';
  } else if (difference.inHours > 0) {
    return '${difference.inHours} ${AppLocalizations.of(context)?.hours ?? 'hours'}';
  } else if (difference.inMinutes > 0) {
    return '${difference.inMinutes} ${AppLocalizations.of(context)?.minutes ?? 'minutes'}';
  } else {
    return AppLocalizations.of(context)?.cacheExpiresSoon ?? 'Expires soon';
  }
}

/// Converts Audiobook entity to Map for display.
Map<String, dynamic> audiobookToMap(Audiobook audiobook) => {
      'id': audiobook.id,
      'title': audiobook.title,
      'author': audiobook.author,
      'category': audiobook.category,
      'size': audiobook.size,
      'seeders': audiobook.seeders,
      'leechers': audiobook.leechers,
      'magnetUrl': audiobook.magnetUrl,
      'coverUrl': audiobook.coverUrl,
      'performer': audiobook.performer,
      'genres': audiobook.genres,
      'chapters': audiobook.chapters.map(chapterToMap).toList(),
      'addedDate': audiobook.addedDate.toIso8601String(),
      'duration': audiobook.duration,
      'bitrate': audiobook.bitrate,
      'audioCodec': audiobook.audioCodec,
    };

/// Converts cached metadata from smart cache to display format.
///
/// Maps cache format (topic_id, cover_url) to display format (id, coverUrl).
Map<String, dynamic> cacheMetadataToMap(
  Map<String, dynamic> metadata,
) =>
    {
      'id': metadata['topic_id'] as String? ?? '',
      'title': metadata['title'] as String? ?? '',
      'author': metadata['author'] as String? ?? '',
      'category': metadata['category'] as String? ?? '',
      'size': metadata['size'] as String? ?? '0 MB',
      'seeders': metadata['seeders'] as int? ?? 0,
      'leechers': metadata['leechers'] as int? ?? 0,
      'magnetUrl': metadata['magnet_url'] as String? ?? '',
      'coverUrl': metadata['cover_url'] as String?,
      'performer': metadata['performer'] as String?,
      'genres': (metadata['genres'] as List<dynamic>?)
              ?.map((g) => g.toString())
              .toList() ??
          [],
      'chapters': (metadata['chapters'] as List<dynamic>?)
              ?.map((c) => {
                    'title': c['title'] as String? ?? '',
                    'durationMs': c['duration_ms'] as int? ?? 0,
                    'fileIndex': c['file_index'] as int? ?? 0,
                    'startByte': c['start_byte'] as int? ?? 0,
                    'endByte': c['end_byte'] as int? ?? 0,
                  })
              .toList() ??
          [],
      'addedDate': metadata['added_date'] != null
          ? DateTime.parse(metadata['added_date'] as String)
          : DateTime.now(),
      'duration': metadata['duration'] as String?,
      'bitrate': metadata['bitrate'] as String?,
      'audioCodec': metadata['audio_codec'] as String?,
    };
