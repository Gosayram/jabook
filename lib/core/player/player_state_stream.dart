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

import 'dart:async';

import 'package:flutter/services.dart';

/// Stream of playback state updates from native layer.
///
/// Subscribes to EventChannel for reactive state updates.
class PlayerStateStream {
  /// Creates a new PlayerStateStream instance.
  ///
  /// Uses v2 channel name for parallel operation with old API during migration.
  /// Can be switched to 'com.jabook.app.jabook/audio_player_events' after full migration.
  PlayerStateStream({bool useV2Channel = true})
      : _channel = EventChannel(
          useV2Channel
              ? 'com.jabook.app.jabook/audio_player_events_v2'
              : 'com.jabook.app.jabook/audio_player_events',
        );

  final EventChannel _channel;
  StreamSubscription<dynamic>? _subscription;

  /// Starts listening to playback state updates.
  ///
  /// Returns a stream of playback state maps.
  Stream<Map<String, dynamic>> listen() =>
      _channel.receiveBroadcastStream().map((dynamic event) {
        if (event is Map) {
          return Map<String, dynamic>.from(event);
        }
        return <String, dynamic>{};
      });

  /// Cancels the subscription.
  void cancel() {
    _subscription?.cancel();
    _subscription = null;
  }
}
