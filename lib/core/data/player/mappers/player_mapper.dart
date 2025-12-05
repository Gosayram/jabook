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

import 'package:jabook/core/domain/player/entities/player_state.dart';
import 'package:jabook/core/player/native_audio_player.dart';
import 'package:jabook/core/player/player_state_provider.dart' as old_player;

/// Mapper for converting old player entities to new domain entities.
class PlayerMapper {
  // Private constructor to prevent instantiation
  PlayerMapper._();

  /// Converts AudioPlayerState to domain PlayerState.
  static PlayerState toDomainFromAudioState(AudioPlayerState audioState) =>
      PlayerState(
        isPlaying: audioState.isPlaying,
        currentPosition: audioState.currentPosition,
        duration: audioState.duration,
        currentIndex: audioState.currentIndex,
        playbackSpeed: audioState.playbackSpeed,
        playbackState: audioState.playbackState,
      );

  /// Converts old PlayerStateModel to domain PlayerState.
  static PlayerState toDomain(old_player.PlayerStateModel oldState) =>
      PlayerState(
        isPlaying: oldState.isPlaying,
        currentPosition: oldState.currentPosition,
        duration: oldState.duration,
        currentIndex: oldState.currentIndex,
        playbackSpeed: oldState.playbackSpeed,
        playbackState: oldState.playbackState,
        error: oldState.error,
        currentTitle: oldState.currentTitle,
        currentArtist: oldState.currentArtist,
        currentCoverPath: oldState.currentCoverPath,
        currentGroupPath: oldState.currentGroupPath,
      );
}
