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

import 'package:jabook/core/player/native_audio_player.dart';
import 'package:riverpod/legacy.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Type of notification to display.
enum NotificationType {
  /// Full notification with all controls (Previous, Rewind, Play/Pause, Forward, Next, Stop).
  full,

  /// Minimal notification with only Play/Pause button (for foreground service).
  minimal,
}

/// Model for notification settings.
class NotificationSettings {
  /// Creates a new NotificationSettings instance.
  const NotificationSettings({
    this.notificationType = NotificationType.full,
  });

  /// Type of notification to display.
  final NotificationType notificationType;

  /// Creates a copy with updated fields.
  NotificationSettings copyWith({
    NotificationType? notificationType,
  }) =>
      NotificationSettings(
        notificationType: notificationType ?? this.notificationType,
      );
}

/// Provider for notification settings.
final notificationSettingsProvider =
    StateNotifierProvider<NotificationSettingsNotifier, NotificationSettings>(
  (ref) => NotificationSettingsNotifier(),
);

/// Notifier for notification settings management.
class NotificationSettingsNotifier extends StateNotifier<NotificationSettings> {
  /// Creates a new NotificationSettingsNotifier instance.
  NotificationSettingsNotifier() : super(const NotificationSettings()) {
    _loadSettings();
  }

  /// Key for storing notification type in SharedPreferences.
  static const String _notificationTypeKey = 'notification_type';

  /// Loads settings from SharedPreferences.
  Future<void> _loadSettings() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final typeIndex = prefs.getInt(_notificationTypeKey) ?? 0;
      final type = NotificationType.values[typeIndex.clamp(
        0,
        NotificationType.values.length - 1,
      )];
      state = NotificationSettings(notificationType: type);

      // Sync with native player
      final isMinimal = type == NotificationType.minimal;
      await NativeAudioPlayer().setNotificationType(isMinimal);
    } on Object {
      // Use default settings on error
    }
  }

  /// Sets the notification type.
  Future<void> setNotificationType(NotificationType type) async {
    state = state.copyWith(notificationType: type);
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setInt(_notificationTypeKey, type.index);

      // Update native player configuration
      final isMinimal = type == NotificationType.minimal;
      await NativeAudioPlayer().setNotificationType(isMinimal);
    } on Object {
      // Ignore save errors
    }
  }
}
