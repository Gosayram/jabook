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

import 'package:shared_preferences/shared_preferences.dart';

/// Service for managing playback positions for audiobook groups.
///
/// This service persists and restores playback positions for local audiobook groups,
/// allowing users to resume playback from where they left off.
class PlaybackPositionService {
  /// Creates a new PlaybackPositionService instance.
  PlaybackPositionService();

  /// Key prefix for storing group playback positions.
  static const String _groupPositionPrefix = 'group_pos_';

  /// Key prefix for storing current track index.
  static const String _trackIndexPrefix = 'track_idx_';

  /// Key prefix for storing track positions within a group.
  static const String _trackPositionPrefix = 'track_pos_';

  /// Saves the current playback position for a group.
  ///
  /// The [groupPath] parameter is the unique path identifying the group.
  /// The [trackIndex] parameter is the current track index in the group.
  /// The [positionMs] parameter is the position in milliseconds within the current track.
  Future<void> savePosition(
    String groupPath,
    int trackIndex,
    int positionMs,
  ) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final groupKey = '$_groupPositionPrefix${_sanitizeKey(groupPath)}';
      final trackIndexKey = '$_trackIndexPrefix${_sanitizeKey(groupPath)}';
      final trackPositionKey =
          '$_trackPositionPrefix${_sanitizeKey(groupPath)}_$trackIndex';

      await prefs.setInt(groupKey, positionMs);
      await prefs.setInt(trackIndexKey, trackIndex);
      await prefs.setInt(trackPositionKey, positionMs);
    } on Exception {
      // Ignore errors - position saving is not critical
    }
  }

  /// Restores the saved playback position for a group.
  ///
  /// The [groupPath] parameter is the unique path identifying the group.
  ///
  /// Returns a map with 'trackIndex' and 'positionMs', or null if no saved position exists.
  Future<Map<String, int>?> restorePosition(String groupPath) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final trackIndexKey = '$_trackIndexPrefix${_sanitizeKey(groupPath)}';
      final trackPositionKeyPrefix =
          '$_trackPositionPrefix${_sanitizeKey(groupPath)}';

      final trackIndex = prefs.getInt(trackIndexKey);
      if (trackIndex == null) {
        return null;
      }

      final trackPositionKey = '${trackPositionKeyPrefix}_$trackIndex';
      final positionMs = prefs.getInt(trackPositionKey);

      if (positionMs == null) {
        return null;
      }

      return {
        'trackIndex': trackIndex,
        'positionMs': positionMs,
      };
    } on Exception {
      return null;
    }
  }

  /// Clears the saved playback position for a group.
  ///
  /// The [groupPath] parameter is the unique path identifying the group.
  Future<void> clearPosition(String groupPath) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final sanitizedPath = _sanitizeKey(groupPath);

      // Remove all position-related keys for this group
      await prefs.remove('$_groupPositionPrefix$sanitizedPath');
      await prefs.remove('$_trackIndexPrefix$sanitizedPath');

      // Remove all track position keys (we don't know how many there are,
      // so we'll just remove them as they're accessed)
      // For now, we'll keep them and they'll be overwritten
    } on Exception {
      // Ignore errors
    }
  }

  /// Clears all playback positions for multiple groups.
  ///
  /// The [groupPaths] parameter is a list of group paths to clear.
  Future<void> clearPositions(List<String> groupPaths) async {
    for (final groupPath in groupPaths) {
      await clearPosition(groupPath);
    }
  }

  /// Removes all track position keys for a group.
  ///
  /// This method attempts to remove all track position keys by trying
  /// common index values (0-999) since we don't track how many tracks exist.
  ///
  /// The [groupPath] parameter is the unique path identifying the group.
  Future<void> clearAllTrackPositions(String groupPath) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final sanitizedPath = _sanitizeKey(groupPath);
      final trackPositionKeyPrefix = '$_trackPositionPrefix$sanitizedPath';

      // Try to remove track positions for indices 0-999
      // This is a reasonable upper limit for audiobook tracks
      for (var i = 0; i < 1000; i++) {
        final trackPositionKey = '${trackPositionKeyPrefix}_$i';
        await prefs.remove(trackPositionKey);
      }
    } on Exception {
      // Ignore errors
    }
  }

  /// Sanitizes a path to be used as a SharedPreferences key.
  ///
  /// SharedPreferences keys have limitations, so we need to sanitize paths.
  String _sanitizeKey(String key) => key.replaceAll(RegExp(r'[^\w\-.]'), '_');
}
