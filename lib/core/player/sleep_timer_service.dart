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
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';

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

  /// Selected duration for timer (for display purposes).
  Duration? _selectedDuration;

  /// Gets selected duration for timer.
  Duration? get selectedDuration => _selectedDuration;

  /// Starts a sleep timer with specified duration.
  ///
  /// [duration] is the duration after which playback should pause.
  /// [onExpired] is the callback to be called when timer expires.
  Future<void> startTimer(Duration duration, VoidCallback onExpired) async {
    // Validate duration - must be positive
    if (duration.inSeconds <= 0) {
      await _logger.log(
        level: 'warning',
        subsystem: 'player',
        message: 'Invalid sleep timer duration, cancelling',
        extra: {'duration_seconds': duration.inSeconds},
      );
      _cancelTimer();
      return;
    }

    await _logger.log(
      level: 'info',
      subsystem: 'player',
      message: 'Starting sleep timer',
      extra: {'duration_seconds': duration.inSeconds},
    );

    _cancelTimer();
    _onTimerExpired = onExpired;
    _remainingSeconds = duration.inSeconds;
    _selectedDuration = duration;

    _timer = Timer.periodic(
      const Duration(seconds: 1),
      (timer) {
        if (_remainingSeconds == null) {
          timer.cancel();
          return;
        }
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
    _selectedDuration =
        const Duration(seconds: -1); // Special value for "at end of chapter"

    // This timer will be cancelled when chapter ends
    // The actual expiration is handled externally
  }

  /// Checks if timer is set to expire at end of chapter.
  bool get isAtEndOfChapter =>
      _onTimerExpired != null && _remainingSeconds == null;

  /// Triggers the timer callback if it's set to expire at end of chapter.
  /// Should be called when a track/chapter ends.
  Future<void> triggerAtEndOfChapter() async {
    debugPrint(
        '🟡 [SLEEP_TIMER] triggerAtEndOfChapter called: isAtEndOfChapter=$isAtEndOfChapter, callback=${_onTimerExpired != null}');
    if (isAtEndOfChapter && _onTimerExpired != null) {
      await _logger.log(
        level: 'info',
        subsystem: 'player',
        message: 'Sleep timer triggered at end of chapter',
      );
      debugPrint('🟢 [SLEEP_TIMER] Calling sleep timer callback');
      final callback = _onTimerExpired;
      _cancelTimer(); // Cancel before calling callback to prevent double trigger
      callback?.call();
    } else {
      debugPrint(
          '🔴 [SLEEP_TIMER] Timer not triggered: isAtEndOfChapter=$isAtEndOfChapter, callback=${_onTimerExpired != null}');
    }
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
    // Keep _selectedDuration for display purposes even after cancellation
  }

  /// Disposes resources.
  void dispose() {
    _cancelTimer();
  }
}
