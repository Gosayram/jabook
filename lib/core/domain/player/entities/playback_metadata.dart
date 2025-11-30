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

/// Represents metadata for audio playback.
///
/// This entity contains information about the current track being played,
/// such as title, artist, album, and cover artwork.
class PlaybackMetadata {
  /// Creates a new PlaybackMetadata instance.
  const PlaybackMetadata({
    this.title,
    this.artist,
    this.album,
    this.artworkUri,
    this.coverPath,
  });

  /// Track title.
  final String? title;

  /// Track artist.
  final String? artist;

  /// Album name.
  final String? album;

  /// Artwork URI (for network images).
  final String? artworkUri;

  /// Cover image path (for local images).
  final String? coverPath;

  /// Gets the display title (title or "Unknown").
  String get displayTitle => title ?? 'Unknown';

  /// Gets the display artist (artist or "Unknown Artist").
  String get displayArtist => artist ?? 'Unknown Artist';

  /// Creates a copy with updated fields.
  PlaybackMetadata copyWith({
    String? title,
    String? artist,
    String? album,
    String? artworkUri,
    String? coverPath,
  }) =>
      PlaybackMetadata(
        title: title ?? this.title,
        artist: artist ?? this.artist,
        album: album ?? this.album,
        artworkUri: artworkUri ?? this.artworkUri,
        coverPath: coverPath ?? this.coverPath,
      );
}
