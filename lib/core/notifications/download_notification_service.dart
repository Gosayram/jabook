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

import 'package:flutter/material.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:go_router/go_router.dart';
import 'package:jabook/core/logging/environment_logger.dart';
import 'package:jabook/core/torrent/audiobook_torrent_manager.dart';

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
  final GlobalKey<NavigatorState> _navigatorKey = GlobalKey<NavigatorState>();

  /// Gets the navigator key for navigation from notifications.
  GlobalKey<NavigatorState> get navigatorKey => _navigatorKey;

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
        onDidReceiveBackgroundNotificationResponse:
            _onBackgroundNotificationTapped,
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
              : status == 'restored'
                  ? 'Tap to resume'
                  : '$progressPercent% • $speedText';

      // Add action buttons for restored/paused downloads
      List<AndroidNotificationAction>? actions;
      if (status == 'restored' || status == 'paused') {
        actions = [
          AndroidNotificationAction(
            'resume_$downloadId',
            status == 'restored' ? 'Resume' : 'Resume',
            cancelNotification: false,
          ),
        ];
      }

      final androidDetails = AndroidNotificationDetails(
        'downloads',
        'Downloads',
        channelDescription: 'Notifications for active torrent downloads',
        importance: Importance.low,
        priority: Priority.low,
        showProgress: status != 'restored' && status != 'paused',
        maxProgress: 100,
        progress: progressPercent,
        onlyAlertOnce: true,
        ongoing: status == 'downloading',
        autoCancel: status == 'completed',
        actions: actions,
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
    _handleNotificationResponse(response);
  }

  /// Handles background notification tap events.
  @pragma('vm:entry-point')
  static void _onBackgroundNotificationTapped(NotificationResponse response) {
    // Background handler - navigation will be handled when app is in foreground
    EnvironmentLogger().d('Background notification tapped: ${response.id}');
  }

  /// Handles notification response (tap or action).
  void _handleNotificationResponse(NotificationResponse response) {
    final actionId = response.actionId;
    final notificationId = response.id;

    // Find downloadId by notificationId
    String? downloadId;
    for (final entry in _notificationIds.entries) {
      if (entry.value == notificationId) {
        downloadId = entry.key;
        break;
      }
    }

    if (downloadId == null) {
      EnvironmentLogger()
          .w('Download ID not found for notification: $notificationId');
      // Navigate to downloads screen anyway
      _navigateToDownloads();
      return;
    }

    // Handle action buttons
    if (actionId != null && actionId.startsWith('resume_')) {
      // Resume download
      _resumeDownload(downloadId);
    } else {
      // Tap on notification - navigate to specific download
      _navigateToDownloads(downloadId: downloadId);
    }
  }

  /// Navigates to the downloads screen.
  ///
  /// If [downloadId] is provided, navigates to the specific download.
  void _navigateToDownloads({String? downloadId}) {
    final context = _navigatorKey.currentContext;
    if (context != null) {
      try {
        // Use GoRouter for navigation with downloadId if provided
        final route = downloadId != null
            ? '/downloads?downloadId=$downloadId'
            : '/downloads';
        context.go(route);
        EnvironmentLogger().d(
          'Navigated to downloads screen from notification${downloadId != null ? ' (downloadId: $downloadId)' : ''}',
        );
      } on Exception catch (e) {
        EnvironmentLogger().e('Failed to navigate to downloads: $e');
      }
    } else {
      EnvironmentLogger().w('Navigator context not available, cannot navigate');
    }
  }

  /// Resumes a download.
  void _resumeDownload(String downloadId) {
    try {
      final torrentManager = AudiobookTorrentManager();
      // Check if it's a restored download
      torrentManager.getActiveDownloads().then((downloads) {
        final download = downloads.firstWhere(
          (d) => d['id'] == downloadId,
          orElse: () => <String, dynamic>{},
        );
        if (download.isEmpty) return;

        final status = download['status'] as String?;
        if (status == 'restored') {
          torrentManager.resumeRestoredDownload(downloadId).catchError((e) {
            EnvironmentLogger().e('Failed to resume restored download: $e');
          });
        } else if (status == 'paused') {
          torrentManager.resumeDownload(downloadId).catchError((e) {
            EnvironmentLogger().e('Failed to resume paused download: $e');
          });
        }
      }).catchError((e) {
        EnvironmentLogger().e('Failed to get downloads: $e');
      });
    } on Exception catch (e) {
      EnvironmentLogger().e('Failed to resume download: $e');
    }
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
