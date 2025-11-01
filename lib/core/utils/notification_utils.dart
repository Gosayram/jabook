import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// Utility functions for handling notifications without requiring POST_NOTIFICATIONS permission.
///
/// Uses system APIs and platform channels to work with notifications
/// without needing explicit permission declarations.
const MethodChannel _notificationChannel =
    MethodChannel('jabook.notifications');

/// Shows a simple notification without requiring POST_NOTIFICATIONS permission.
///
/// This uses the system's built-in notification capabilities
/// that don't require explicit permission declarations.
Future<void> showSimpleNotification({
  required String title,
  required String body,
  String? channelId,
}) async {
  try {
    await _notificationChannel.invokeMethod('showNotification', {
      'title': title,
      'body': body,
      'channelId': channelId ?? 'jabook_default',
    });
  } on PlatformException catch (e) {
    if (kDebugMode) {
      print('Failed to show notification: $e');
    }
  }
}

/// Shows a media notification for audio playback.
///
/// This is handled by the audio service and doesn't require
/// POST_NOTIFICATIONS permission as it's part of media playback.
Future<void> showMediaNotification({
  required String title,
  required String artist,
  required String album,
  bool isPlaying = false,
}) async {
  try {
    await _notificationChannel.invokeMethod('showMediaNotification', {
      'title': title,
      'artist': artist,
      'album': album,
      'isPlaying': isPlaying,
    });
  } on PlatformException catch (e) {
    if (kDebugMode) {
      print('Failed to show media notification: $e');
    }
  }
}

/// Cancels a notification by ID.
Future<void> cancelNotification(int id) async {
  try {
    await _notificationChannel.invokeMethod('cancelNotification', {'id': id});
  } on PlatformException catch (e) {
    if (kDebugMode) {
      print('Failed to cancel notification: $e');
    }
  }
}

/// Cancels all notifications.
Future<void> cancelAllNotifications() async {
  try {
    await _notificationChannel.invokeMethod('cancelAllNotifications');
  } on PlatformException catch (e) {
    if (kDebugMode) {
      print('Failed to cancel all notifications: $e');
    }
  }
}
