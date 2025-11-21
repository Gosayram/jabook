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

import 'package:flutter/material.dart';
import 'package:jabook/core/logging/structured_logger.dart';

/// Sleep timer service for automatically pausing playback.
///
/// This service manages sleep timer functionality, allowing users to set
/// a timer that will pause playback after a specified duration or at
/// the end of the current chapter.
class SleepTimerService {
  /// Logger for structured logging.
  final StructuredLogger _logger = StructuredLogger();

  /// Timer instance.
  Timer? _timer;

  /// Callback to be called when timer expires.
  VoidCallback? _onTimerExpired;

  /// Whether timer is active.
  bool get isActive => _timer != null && _timer!.isActive;

  /// Remaining time in seconds.
  int? _remainingSeconds;

  /// Gets remaining time in seconds.
  int? get remainingSeconds => _remainingSeconds;

  /// Starts a sleep timer with specified duration.
  ///
  /// [duration] is the duration after which playback should pause.
  /// [onExpired] is the callback to be called when timer expires.
  Future<void> startTimer(Duration duration, VoidCallback onExpired) async {
    await _logger.log(
      level: 'info',
      subsystem: 'player',
      message: 'Starting sleep timer',
      extra: {'duration_seconds': duration.inSeconds},
    );

    _cancelTimer();
    _onTimerExpired = onExpired;
    _remainingSeconds = duration.inSeconds;

    _timer = Timer.periodic(
      const Duration(seconds: 1),
      (timer) {
        _remainingSeconds = _remainingSeconds! - 1;
        if (_remainingSeconds! <= 0) {
          _cancelTimer();
          _onTimerExpired?.call();
          _logger.log(
            level: 'info',
            subsystem: 'player',
            message: 'Sleep timer expired',
          );
        }
      },
    );
  }

  /// Starts a sleep timer that expires at the end of current chapter.
  ///
  /// [onExpired] is the callback to be called when timer expires.
  /// Note: This requires external monitoring of chapter completion.
  Future<void> startTimerAtEndOfChapter(VoidCallback onExpired) async {
    await _logger.log(
      level: 'info',
      subsystem: 'player',
      message: 'Starting sleep timer at end of chapter',
    );

    _cancelTimer();
    _onTimerExpired = onExpired;
    _remainingSeconds = null; // Unknown duration

    // This timer will be cancelled when chapter ends
    // The actual expiration is handled externally
  }

  /// Cancels the active sleep timer.
  Future<void> cancelTimer() async {
    await _logger.log(
      level: 'info',
      subsystem: 'player',
      message: 'Cancelling sleep timer',
    );
    _cancelTimer();
  }

  /// Internal method to cancel timer.
  void _cancelTimer() {
    _timer?.cancel();
    _timer = null;
    _remainingSeconds = null;
    _onTimerExpired = null;
  }

  /// Disposes resources.
  void dispose() {
    _cancelTimer();
  }
}
