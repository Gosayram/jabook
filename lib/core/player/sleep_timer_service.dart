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
import 'package:jabook/core/player/native_audio_player.dart';

/// Sleep timer service for automatically pausing playback.
///
/// This service manages sleep timer functionality, allowing users to set
/// a timer that will pause playback after a specified duration or at
/// the end of the current chapter.
///
/// Uses native timer implementation (inspired by EasyBook) for reliable
/// operation even when app is in background.
class SleepTimerService {
  /// Creates a new SleepTimerService instance.
  ///
  /// [nativePlayer] is the NativeAudioPlayer instance to use for timer operations.
  SleepTimerService(this._nativePlayer);

  /// Logger for structured logging.
  final StructuredLogger _logger = StructuredLogger();

  /// Native audio player for timer management.
  final NativeAudioPlayer _nativePlayer;

  /// Timer for periodic updates of remaining time.
  Timer? _updateTimer;

  /// Callback to be called when timer expires.
  VoidCallback? _onTimerExpired;

  /// Broadcast receiver subscription for timer expired events.
  StreamSubscription<dynamic>? _timerEventSubscription;

  /// Whether timer is active.
  ///
  /// Checks native timer state for accurate status.
  bool get isActive {
    // If no callback is set, timer is definitely not active
    if (_onTimerExpired == null) {
      return false;
    }
    // If selected duration is null, timer was cancelled
    if (_selectedDuration == null) {
      return false;
    }
    // For "end of chapter" mode, check if callback exists
    if (_selectedDuration == const Duration(seconds: -1)) {
      // Timer is active if callback exists
      return true;
    }
    // For fixed duration, check remaining seconds
    return _remainingSeconds != null && _remainingSeconds! > 0;
  }

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
  ///
  /// Uses native timer implementation for reliable operation in background.
  Future<void> startTimer(Duration duration, VoidCallback onExpired) async {
    // Validate duration - must be positive
    if (duration.inSeconds <= 0) {
      await _logger.log(
        level: 'warning',
        subsystem: 'player',
        message: 'Invalid sleep timer duration, cancelling',
        extra: {'duration_seconds': duration.inSeconds},
      );
      await _cancelTimer();
      return;
    }

    await _logger.log(
      level: 'info',
      subsystem: 'player',
      message: 'Starting sleep timer',
      extra: {'duration_seconds': duration.inSeconds},
    );

    await _cancelTimer();
    _onTimerExpired = onExpired;
    _remainingSeconds = duration.inSeconds;
    _selectedDuration = duration;

    // Set timer in native service (inspired by EasyBook)
    final minutes = duration.inMinutes;
    if (minutes > 0) {
      try {
        await _nativePlayer.setSleepTimerMinutes(minutes);
        await _logger.log(
          level: 'info',
          subsystem: 'player',
          message: 'Native sleep timer set',
          extra: {'minutes': minutes},
        );
      } on Exception catch (e) {
        await _logger.log(
          level: 'error',
          subsystem: 'player',
          message: 'Failed to set native sleep timer',
          cause: e.toString(),
        );
        // Fallback: continue with local timer if native fails
      }
    }

    // Start periodic updates to sync remaining time from native timer
    _startTimerUpdates();
  }

  /// Starts a sleep timer that expires at the end of current chapter.
  ///
  /// [onExpired] is the callback to be called when timer expires.
  ///
  /// Uses native timer implementation for reliable operation in background.
  /// Native service will trigger callback when track ends automatically.
  Future<void> startTimerAtEndOfChapter(VoidCallback onExpired) async {
    await _logger.log(
      level: 'info',
      subsystem: 'player',
      message: 'Starting sleep timer at end of chapter',
    );

    await _cancelTimer();
    _onTimerExpired = onExpired;
    _remainingSeconds = null; // Unknown duration
    _selectedDuration =
        const Duration(seconds: -1); // Special value for "at end of chapter"

    // Set timer in native service (inspired by EasyBook)
    try {
      await _nativePlayer.setSleepTimerEndOfChapter();
      await _logger.log(
        level: 'info',
        subsystem: 'player',
        message: 'Native sleep timer set to end of chapter',
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'player',
        message: 'Failed to set native sleep timer end of chapter',
        cause: e.toString(),
      );
      // Continue anyway - native service will handle it
    }

    // Subscribe to timer expired events from native service
    _subscribeToTimerEvents();
  }

  /// Checks if timer is set to expire at end of chapter.
  bool get isAtEndOfChapter =>
      _onTimerExpired != null && _remainingSeconds == null;

  /// Triggers the timer callback if it's set to expire at end of chapter.
  ///
  /// Should be called when a track/chapter ends.
  /// Note: Native service also handles this automatically, but this method
  /// provides a fallback for cases where native service doesn't trigger.
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
      await _cancelTimer(); // Cancel before calling callback to prevent double trigger
      callback?.call();
    } else {
      debugPrint(
          '🔴 [SLEEP_TIMER] Timer not triggered: isAtEndOfChapter=$isAtEndOfChapter, callback=${_onTimerExpired != null}');
    }
  }

  /// Cancels the active sleep timer.
  Future<void> cancelTimer() async {
    debugPrint('🟡 [SLEEP_TIMER] cancelTimer() called');
    await _logger.log(
      level: 'info',
      subsystem: 'player',
      message: 'Cancelling sleep timer',
    );

    // Cancel native timer first
    try {
      await _nativePlayer.cancelSleepTimer();
      debugPrint('🟢 [SLEEP_TIMER] Native timer cancellation requested');
      await _logger.log(
        level: 'info',
        subsystem: 'player',
        message: 'Native sleep timer cancellation requested',
      );
    } on Exception catch (e) {
      debugPrint('🔴 [SLEEP_TIMER] Failed to cancel native timer: $e');
      await _logger.log(
        level: 'warning',
        subsystem: 'player',
        message: 'Failed to cancel native sleep timer',
        cause: e.toString(),
      );
    }

    // Then cancel local state (native already cancelled above)
    await _cancelTimer();
    debugPrint(
        '🟢 [SLEEP_TIMER] Local state cancelled, isActive should be: $isActive');

    // Verify native timer was cancelled
    try {
      final isNativeActive = await _nativePlayer.isSleepTimerActive();
      if (isNativeActive) {
        await _logger.log(
          level: 'warning',
          subsystem: 'player',
          message: 'Native timer still active after cancellation, retrying',
        );
        // Retry cancellation
        await _nativePlayer.cancelSleepTimer();
        // Wait a bit and check again
        await Future.delayed(const Duration(milliseconds: 100));
        final stillActive = await _nativePlayer.isSleepTimerActive();
        if (stillActive) {
          await _logger.log(
            level: 'error',
            subsystem: 'player',
            message:
                'Native timer still active after retry - possible native service issue',
          );
        }
      } else {
        await _logger.log(
          level: 'info',
          subsystem: 'player',
          message: 'Native timer successfully cancelled',
        );
      }
    } on Exception catch (e) {
      await _logger.log(
        level: 'warning',
        subsystem: 'player',
        message: 'Failed to verify native timer cancellation',
        cause: e.toString(),
      );
    }
  }

  /// Internal method to cancel timer.
  ///
  /// [cancelNative] - whether to cancel native timer (default: false, as it's done in cancelTimer())
  Future<void> _cancelTimer({bool cancelNative = false}) async {
    debugPrint(
        '🟡 [SLEEP_TIMER] _cancelTimer called, cancelNative: $cancelNative');

    _updateTimer?.cancel();
    _updateTimer = null;
    await _timerEventSubscription?.cancel();
    _timerEventSubscription = null;
    _remainingSeconds = null;
    _onTimerExpired = null;
    _selectedDuration = null; // Reset selected duration on cancellation

    debugPrint(
        '🟢 [SLEEP_TIMER] Local state cleared: _onTimerExpired=null, _selectedDuration=null');

    // Cancel native timer only if requested (to avoid double cancellation)
    if (cancelNative) {
      try {
        await _nativePlayer.cancelSleepTimer();
        await _logger.log(
          level: 'info',
          subsystem: 'player',
          message: 'Native sleep timer cancelled',
        );
        debugPrint('🟢 [SLEEP_TIMER] Native timer cancelled');
      } on Exception catch (e) {
        await _logger.log(
          level: 'warning',
          subsystem: 'player',
          message: 'Failed to cancel native sleep timer',
          cause: e.toString(),
        );
        debugPrint('🔴 [SLEEP_TIMER] Failed to cancel native timer: $e');
      }
    }
  }

  /// Starts periodic updates to sync remaining time from native timer.
  void _startTimerUpdates() {
    _updateTimer?.cancel();
    _updateTimer = Timer.periodic(
      const Duration(seconds: 1),
      (_) async {
        try {
          final remaining = await _nativePlayer.getSleepTimerRemainingSeconds();
          if (remaining != null) {
            _remainingSeconds = remaining;
            // Check if timer expired
            if (remaining <= 0) {
              _updateTimer?.cancel();
              _updateTimer = null;
              final callback = _onTimerExpired;
              await _cancelTimer();
              callback?.call();
              await _logger.log(
                level: 'info',
                subsystem: 'player',
                message: 'Sleep timer expired (from native)',
              );
            }
          } else {
            // Timer was cancelled or expired - clear state
            _updateTimer?.cancel();
            _updateTimer = null;
            _remainingSeconds = null;
            // Don't clear callback here - it might be set for "end of chapter" mode
            // But if native timer is not active, we should check and clear if needed
            try {
              final isNativeActive = await _nativePlayer.isSleepTimerActive();
              if (!isNativeActive) {
                // Native timer is not active, clear our state
                _onTimerExpired = null;
                _selectedDuration = null;
              }
            } on Exception {
              // Ignore errors when checking native state
            }
          }
        } on Exception {
          // Ignore errors in updates
        }
      },
    );
  }

  /// Subscribes to timer expired events from native service.
  void _subscribeToTimerEvents() {
    // Note: For now, we rely on periodic polling and native service callbacks
    // Future enhancement: use EventChannel for real-time events
    // The native service will trigger callback automatically when track ends
  }

  /// Disposes resources.
  Future<void> dispose() async {
    await _cancelTimer();
  }
}
