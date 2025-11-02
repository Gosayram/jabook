import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:jabook/core/logging/structured_logger.dart';

/// Utility functions for handling notifications without requiring POST_NOTIFICATIONS permission.
///
/// Uses system APIs and platform channels to work with notifications
/// without needing explicit permission declarations.
const MethodChannel _notificationChannel =
    MethodChannel('jabook.notifications');

/// Logger instance for structured logging.
final StructuredLogger _logger = StructuredLogger();

/// Shows a simple notification without requiring POST_NOTIFICATIONS permission.
///
/// This uses the system's built-in notification capabilities
/// that don't require explicit permission declarations.
/// Returns [true] if notification was shown successfully, [false] otherwise.
Future<bool> showSimpleNotification({
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
    return true;
  } on MissingPluginException catch (e) {
    // Platform channel not implemented - graceful degradation
    await _logger.log(
      level: 'warning',
      subsystem: 'notifications',
      message: 'Notification channel not available',
      extra: {'error': e.toString()},
    );
    if (kDebugMode) {
      print('Notification channel not implemented: $e');
    }
    return false;
  } on PlatformException catch (e) {
    // Other platform errors - log but don't crash
    await _logger.log(
      level: 'warning',
      subsystem: 'notifications',
      message: 'Failed to show notification',
      extra: {'error': e.toString(), 'code': e.code},
    );
    if (kDebugMode) {
      print('Failed to show notification: $e');
    }
    return false;
  } on Exception catch (e) {
    // Catch any other exceptions
    await _logger.log(
      level: 'warning',
      subsystem: 'notifications',
      message: 'Unexpected error showing notification',
      extra: {'error': e.toString()},
    );
    if (kDebugMode) {
      print('Unexpected error showing notification: $e');
    }
    return false;
  }
}

/// Shows a media notification for audio playback.
///
/// This is handled by the audio service and doesn't require
/// POST_NOTIFICATIONS permission as it's part of media playback.
/// Returns [true] if notification was shown successfully, [false] otherwise.
Future<bool> showMediaNotification({
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
    return true;
  } on MissingPluginException catch (e) {
    // Platform channel not implemented - graceful degradation
    await _logger.log(
      level: 'warning',
      subsystem: 'notifications',
      message: 'Media notification channel not available',
      extra: {'error': e.toString()},
    );
    if (kDebugMode) {
      print('Media notification channel not implemented: $e');
    }
    return false;
  } on PlatformException catch (e) {
    // Other platform errors - log but don't crash
    await _logger.log(
      level: 'warning',
      subsystem: 'notifications',
      message: 'Failed to show media notification',
      extra: {'error': e.toString(), 'code': e.code},
    );
    if (kDebugMode) {
      print('Failed to show media notification: $e');
    }
    return false;
  } on Exception catch (e) {
    // Catch any other exceptions
    await _logger.log(
      level: 'warning',
      subsystem: 'notifications',
      message: 'Unexpected error showing media notification',
      extra: {'error': e.toString()},
    );
    if (kDebugMode) {
      print('Unexpected error showing media notification: $e');
    }
    return false;
  }
}

/// Cancels a notification by ID.
/// Returns [true] if cancellation was successful, [false] otherwise.
Future<bool> cancelNotification(int id) async {
  try {
    await _notificationChannel.invokeMethod('cancelNotification', {'id': id});
    return true;
  } on MissingPluginException {
    // Platform channel not implemented - not critical, just log
    await _logger.log(
      level: 'debug',
      subsystem: 'notifications',
      message: 'Notification channel not available for cancellation',
    );
    return false;
  } on PlatformException catch (e) {
    await _logger.log(
      level: 'debug',
      subsystem: 'notifications',
      message: 'Failed to cancel notification',
      extra: {'error': e.toString(), 'code': e.code, 'id': id},
    );
    if (kDebugMode) {
      print('Failed to cancel notification: $e');
    }
    return false;
  } on Exception catch (e) {
    await _logger.log(
      level: 'debug',
      subsystem: 'notifications',
      message: 'Unexpected error canceling notification',
      extra: {'error': e.toString(), 'id': id},
    );
    return false;
  }
}

/// Cancels all notifications.
/// Returns [true] if cancellation was successful, [false] otherwise.
Future<bool> cancelAllNotifications() async {
  try {
    await _notificationChannel.invokeMethod('cancelAllNotifications');
    return true;
  } on MissingPluginException {
    // Platform channel not implemented - not critical, just log
    await _logger.log(
      level: 'debug',
      subsystem: 'notifications',
      message: 'Notification channel not available for cancellation',
    );
    return false;
  } on PlatformException catch (e) {
    await _logger.log(
      level: 'debug',
      subsystem: 'notifications',
      message: 'Failed to cancel all notifications',
      extra: {'error': e.toString(), 'code': e.code},
    );
    if (kDebugMode) {
      print('Failed to cancel all notifications: $e');
    }
    return false;
  } on Exception catch (e) {
    await _logger.log(
      level: 'debug',
      subsystem: 'notifications',
      message: 'Unexpected error canceling all notifications',
      extra: {'error': e.toString()},
    );
    return false;
  }
}
