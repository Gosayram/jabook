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
import 'package:go_router/go_router.dart';
import 'package:jabook/core/infrastructure/config/app_config.dart';
import 'package:jabook/core/infrastructure/logging/environment_logger.dart';
import 'package:jabook/core/infrastructure/permissions/permission_service.dart';
import 'package:jabook/core/torrent/audiobook_torrent_manager.dart';
import 'package:jabook/core/utils/safe_async.dart';

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
  GoRouter? _router;

  /// Sets the GoRouter instance for navigation from notifications.
  ///
  /// This should be called once when the app starts, after the router is created.
  void setRouter(GoRouter router) {
    _router = router;
    EnvironmentLogger().d('DownloadNotificationService: Router set');
  }

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

      // Request notification permission (non-blocking)
      try {
        final permissionService = PermissionService();
        final hasPermission =
            await permissionService.hasNotificationPermission();
        if (!hasPermission) {
          EnvironmentLogger().d(
            'DownloadNotificationService: Notification permission not granted, requesting...',
          );
          // Request permission asynchronously (non-blocking)
          safeUnawaited(
              permissionService.requestNotificationPermission().then((granted) {
            if (granted) {
              EnvironmentLogger().i(
                'DownloadNotificationService: Notification permission granted',
              );
            } else {
              EnvironmentLogger().w(
                'DownloadNotificationService: Notification permission denied by user',
              );
            }
          }));
        } else {
          EnvironmentLogger().d(
            'DownloadNotificationService: Notification permission already granted',
          );
        }
      } on Exception catch (e) {
        EnvironmentLogger().w(
          'DownloadNotificationService: Error requesting notification permission: $e',
        );
        // Continue - permission might not be available on older Android versions
      }

      // Verify initialization was successful
      if (_notifications != null) {
        _initialized = true;
        EnvironmentLogger()
            .i('DownloadNotificationService initialized successfully');
      } else {
        EnvironmentLogger().e(
            'DownloadNotificationService: Initialization failed - notifications plugin is null');
        _initialized = false;
      }
    } on Exception catch (e) {
      EnvironmentLogger()
          .e('Failed to initialize DownloadNotificationService: $e');
      _initialized = false;
      _notifications = null;
      // Don't set _initialized to true on error - allow retry
    }
  }

  /// Creates a notification channel for downloads on Android.
  Future<void> _createDownloadChannel() async {
    if (!Platform.isAndroid || _notifications == null) {
      EnvironmentLogger().d(
        'DownloadNotificationService: Skipping channel creation - not Android or notifications not initialized',
      );
      return;
    }

    try {
      EnvironmentLogger().d(
        'DownloadNotificationService: Creating notification channel "downloads"',
      );
      final config = AppConfig();
      final channelName =
          config.isProd ? 'Downloads' : 'Downloads (${config.flavor})';
      final androidChannel = AndroidNotificationChannel(
        'downloads',
        channelName,
        description: 'Notifications for active torrent downloads',
        importance: Importance.low,
        showBadge: false,
      );

      final androidImplementation = _notifications!
          .resolvePlatformSpecificImplementation<
              AndroidFlutterLocalNotificationsPlugin>();

      if (androidImplementation != null) {
        await androidImplementation.createNotificationChannel(androidChannel);
        EnvironmentLogger().d(
          'DownloadNotificationService: Created notification channel "downloads"',
        );
      } else {
        EnvironmentLogger().w(
          'DownloadNotificationService: Android implementation not available',
        );
      }
    } on Exception catch (e) {
      EnvironmentLogger().e(
        'DownloadNotificationService: Failed to create notification channel',
        error: e,
      );
    }
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
      // Try to initialize if not already initialized
      try {
        await initialize();
      } on Exception catch (e) {
        EnvironmentLogger().e(
          'DownloadNotificationService: Failed to initialize during showDownloadProgress: $e',
        );
      }
      // Check again after initialization attempt
      if (!_initialized || _notifications == null) {
        EnvironmentLogger().w(
          'DownloadNotificationService: Cannot show notification - service not initialized',
        );
        return;
      }
    }

    if (!Platform.isAndroid) return;

    // Check notification permission before showing
    try {
      final permissionService = PermissionService();
      final hasPermission = await permissionService.hasNotificationPermission();
      if (!hasPermission) {
        EnvironmentLogger().w(
          'DownloadNotificationService: Notification permission not granted, skipping notification',
        );
        return;
      }
    } on Exception catch (e) {
      EnvironmentLogger().e(
        'DownloadNotificationService: Error checking notification permission',
        error: e,
      );
      // Continue anyway - permission check might fail on older Android versions
    }

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

      final config = AppConfig();
      final channelName =
          config.isProd ? 'Downloads' : 'Downloads (${config.flavor})';
      final androidDetails = AndroidNotificationDetails(
        'downloads',
        channelName,
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
    // Store the notification response to handle navigation when app opens
    // The navigation will be handled in _handleNotificationResponse when app is in foreground
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
    // Try multiple times if router is not set yet (app might be starting)
    void attemptNavigation({int retryCount = 0}) {
      if (_router == null) {
        if (retryCount < 5) {
          EnvironmentLogger().d(
            'DownloadNotificationService: Router not set yet, retrying in 200ms (attempt ${retryCount + 1}/5)',
          );
          // Try to navigate after a short delay to allow router to be set
          Future.delayed(const Duration(milliseconds: 200), () {
            attemptNavigation(retryCount: retryCount + 1);
          });
        } else {
          EnvironmentLogger().w(
            'DownloadNotificationService: Router not set after 5 attempts, cannot navigate',
          );
        }
        return;
      }

      try {
        // Use GoRouter for navigation with downloadId if provided
        final route = downloadId != null
            ? '/downloads?downloadId=$downloadId'
            : '/downloads';
        _router!.go(route);
        EnvironmentLogger().d(
          'DownloadNotificationService: Successfully navigated to downloads screen from notification${downloadId != null ? ' (downloadId: $downloadId)' : ''}',
        );
      } on Exception catch (e) {
        EnvironmentLogger().e(
          'DownloadNotificationService: Failed to navigate to downloads',
          error: e,
        );
        // Try to navigate to home as fallback
        try {
          _router!.go('/');
          EnvironmentLogger().d(
            'DownloadNotificationService: Navigated to home as fallback',
          );
        } on Exception catch (e2) {
          EnvironmentLogger().e(
            'DownloadNotificationService: Failed to navigate to home as fallback',
            error: e2,
          );
        }
      }
    }

    attemptNavigation();
  }

  /// Resumes a download.
  void _resumeDownload(String downloadId) {
    try {
      // Note: Notification service runs outside Riverpod context, so we create
      // the instance directly. This is acceptable for notification services.
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
