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

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/core/player/audio_player_bridge.dart';
import 'package:jabook/core/player/player_state_stream.dart';
import 'package:jabook/core/player/simple_player_provider.dart';
import 'package:riverpod/legacy.dart' as legacy;

/// Provider for AudioPlayerBridge instance.
///
/// This is a singleton provider that creates a single instance of AudioPlayerBridge.
final audioPlayerBridgeProvider = Provider<AudioPlayerBridge>((ref) =>
    // Use v2 channel for parallel operation during migration
    AudioPlayerBridge());

/// Provider for PlayerStateStream instance.
///
/// This is a singleton provider that creates a single instance of PlayerStateStream.
final playerStateStreamProvider = Provider<PlayerStateStream>((ref) =>
    // Use v2 channel for parallel operation during migration
    PlayerStateStream());

/// Provider for SimplePlayerNotifier instance.
///
/// This provider creates a SimplePlayerNotifier using the bridge architecture.
/// It uses AudioPlayerBridge for method calls and PlayerStateStream for reactive updates.
/// All business logic, including state persistence, is in the Kotlin layer.
final simplePlayerProvider =
    legacy.StateNotifierProvider<SimplePlayerNotifier, SimplePlayerState>(
        (ref) {
  final bridge = ref.watch(audioPlayerBridgeProvider);
  final stateStream = ref.watch(playerStateStreamProvider);
  return SimplePlayerNotifier(
    bridge: bridge,
    stateStream: stateStream,
  );
});
