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

/// Represents the state of the audio player.
///
/// This is a domain entity that contains information about the current
/// playback state, position, and metadata.
class PlayerState {
  /// Creates a new PlayerState instance.
  const PlayerState({
    required this.isPlaying,
    required this.currentPosition,
    required this.duration,
    required this.currentIndex,
    required this.playbackSpeed,
    required this.playbackState,
    this.error,
    this.currentTitle,
    this.currentArtist,
    this.currentCoverPath,
    this.currentGroupPath,
  });

  /// Whether audio is currently playing.
  final bool isPlaying;

  /// Current playback position in milliseconds.
  final int currentPosition;

  /// Total duration in milliseconds.
  final int duration;

  /// Current track index in playlist.
  final int currentIndex;

  /// Playback speed (1.0 = normal speed).
  final double playbackSpeed;

  /// Playback state (0 = idle, 1 = buffering, 2 = ready, 3 = ended).
  final int playbackState;

  /// Error message if any.
  final String? error;

  /// Current track title.
  final String? currentTitle;

  /// Current track artist.
  final String? currentArtist;

  /// Current cover image path.
  final String? currentCoverPath;

  /// Current group path (for navigation).
  final String? currentGroupPath;

  /// Gets the playback progress as a percentage (0.0 to 1.0).
  double get progress {
    if (duration == 0) return 0.0;
    return currentPosition / duration;
  }

  /// Gets the remaining time in milliseconds.
  int get remainingTime => duration - currentPosition;

  /// Checks if playback is at the end.
  bool get isAtEnd => currentPosition >= duration && duration > 0;

  /// Checks if playback is ready.
  bool get isReady => playbackState == 2;

  /// Checks if playback is buffering.
  bool get isBuffering => playbackState == 1;

  /// Creates a copy with updated fields.
  PlayerState copyWith({
    bool? isPlaying,
    int? currentPosition,
    int? duration,
    int? currentIndex,
    double? playbackSpeed,
    int? playbackState,
    String? error,
    String? currentTitle,
    String? currentArtist,
    String? currentCoverPath,
    String? currentGroupPath,
  }) =>
      PlayerState(
        isPlaying: isPlaying ?? this.isPlaying,
        currentPosition: currentPosition ?? this.currentPosition,
        duration: duration ?? this.duration,
        currentIndex: currentIndex ?? this.currentIndex,
        playbackSpeed: playbackSpeed ?? this.playbackSpeed,
        playbackState: playbackState ?? this.playbackState,
        error: error ?? this.error,
        currentTitle: currentTitle ?? this.currentTitle,
        currentArtist: currentArtist ?? this.currentArtist,
        currentCoverPath: currentCoverPath ?? this.currentCoverPath,
        currentGroupPath: currentGroupPath ?? this.currentGroupPath,
      );
}
