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

import 'dart:io';

import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:jabook/core/logging/environment_logger.dart';

/// Service for managing download notifications on Android.
///
/// This service provides notifications for active torrent downloads,
/// showing progress, speed, and status updates.
class DownloadNotificationService {
  /// Private constructor for singleton pattern.
  DownloadNotificationService._();

  /// Factory constructor to get the singleton instance.
  factory DownloadNotificationService() => _instance;

  static final DownloadNotificationService _instance =
      DownloadNotificationService._();

  FlutterLocalNotificationsPlugin? _notifications;
  bool _initialized = false;
  final Map<String, int> _notificationIds = {};
  int _nextNotificationId = 1000;

  /// Initializes the notification service.
  ///
  /// This should be called once when the app starts.
  Future<void> initialize() async {
    if (_initialized) return;

    if (!Platform.isAndroid) {
      // Notifications are Android-only for now
      return;
    }

    try {
      _notifications = FlutterLocalNotificationsPlugin();

      // Initialize Android settings
      const androidSettings =
          AndroidInitializationSettings('@mipmap/ic_launcher');

      const initSettings = InitializationSettings(
        android: androidSettings,
      );

      await _notifications!.initialize(
        initSettings,
        onDidReceiveNotificationResponse: _onNotificationTapped,
      );

      // Create notification channel for downloads
      await _createDownloadChannel();

      _initialized = true;
      EnvironmentLogger().i('DownloadNotificationService initialized');
    } on Exception catch (e) {
      EnvironmentLogger()
          .e('Failed to initialize DownloadNotificationService: $e');
    }
  }

  /// Creates a notification channel for downloads on Android.
  Future<void> _createDownloadChannel() async {
    if (!Platform.isAndroid || _notifications == null) return;

    const androidChannel = AndroidNotificationChannel(
      'downloads',
      'Downloads',
      description: 'Notifications for active torrent downloads',
      importance: Importance.low,
      showBadge: false,
    );

    await _notifications!
        .resolvePlatformSpecificImplementation<
            AndroidFlutterLocalNotificationsPlugin>()
        ?.createNotificationChannel(androidChannel);
  }

  /// Shows or updates a download notification.
  ///
  /// The [downloadId] parameter is the unique identifier for the download.
  /// The [title] parameter is the name of the download.
  /// The [progress] parameter is the download progress (0-100).
  /// The [speed] parameter is the download speed in bytes per second.
  /// The [status] parameter is the current status ('downloading', 'paused', 'completed').
  Future<void> showDownloadProgress(
    String downloadId,
    String title,
    double progress,
    double speed,
    String status,
  ) async {
    if (!_initialized || _notifications == null) {
      await initialize();
      if (!_initialized || _notifications == null) return;
    }

    if (!Platform.isAndroid) return;

    try {
      // Get or create notification ID for this download
      if (!_notificationIds.containsKey(downloadId)) {
        _notificationIds[downloadId] = _nextNotificationId++;
      }
      final notificationId = _notificationIds[downloadId]!;

      // Format speed
      final speedText = _formatSpeed(speed);

      // Build notification details
      final progressPercent = progress.toInt();
      final progressText = status == 'completed'
          ? 'Completed'
          : status == 'paused'
              ? 'Paused'
              : '$progressPercent% • $speedText';

      final androidDetails = AndroidNotificationDetails(
        'downloads',
        'Downloads',
        channelDescription: 'Notifications for active torrent downloads',
        importance: Importance.low,
        priority: Priority.low,
        showProgress: true,
        maxProgress: 100,
        progress: progressPercent,
        onlyAlertOnce: true,
        ongoing: status == 'downloading',
        autoCancel: status == 'completed',
      );

      final notificationDetails = NotificationDetails(android: androidDetails);

      await _notifications!.show(
        notificationId,
        title,
        progressText,
        notificationDetails,
      );
    } on Exception catch (e) {
      EnvironmentLogger().e('Failed to show download notification: $e');
    }
  }

  /// Cancels a download notification.
  ///
  /// The [downloadId] parameter is the unique identifier for the download.
  Future<void> cancelDownloadNotification(String downloadId) async {
    if (!_initialized || _notifications == null) return;
    if (!Platform.isAndroid) return;

    try {
      final notificationId = _notificationIds[downloadId];
      if (notificationId != null) {
        await _notifications!.cancel(notificationId);
        _notificationIds.remove(downloadId);
      }
    } on Exception catch (e) {
      EnvironmentLogger().e('Failed to cancel download notification: $e');
    }
  }

  /// Cancels all download notifications.
  Future<void> cancelAllNotifications() async {
    if (!_initialized || _notifications == null) return;
    if (!Platform.isAndroid) return;

    try {
      await _notifications!.cancelAll();
      _notificationIds.clear();
    } on Exception catch (e) {
      EnvironmentLogger().e('Failed to cancel all notifications: $e');
    }
  }

  /// Handles notification tap events.
  void _onNotificationTapped(NotificationResponse response) {
    // Could navigate to downloads screen here
    EnvironmentLogger().d('Notification tapped: ${response.id}');
  }

  /// Formats download speed for display.
  String _formatSpeed(double bytesPerSecond) {
    if (bytesPerSecond < 1024) return '${bytesPerSecond.toInt()} B/s';
    if (bytesPerSecond < 1024 * 1024) {
      return '${(bytesPerSecond / 1024).toStringAsFixed(1)} KB/s';
    }
    return '${(bytesPerSecond / (1024 * 1024)).toStringAsFixed(1)} MB/s';
  }
}
