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
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';

/// Service for managing download foreground service.
///
/// This service provides a Dart interface to the native Android foreground service
/// that keeps downloads running in the background.
class DownloadForegroundService {
  /// MethodChannel for communication with native code.
  static const MethodChannel _channel = MethodChannel(
    'com.jabook.app.jabook/download_service',
  );

  /// Logger for structured logging.
  final StructuredLogger _logger = StructuredLogger();

  /// Starts the download foreground service.
  ///
  /// This should be called when downloads start to ensure they continue
  /// even when the app is in the background.
  ///
  /// Throws [Exception] if the service fails to start, allowing the caller
  /// to handle the error appropriately (e.g., not start the torrent task).
  Future<void> startService() async {
    try {
      await _logger.log(
        level: 'info',
        subsystem: 'download',
        message: 'Starting download foreground service',
      );

      // Add timeout to ensure we don't wait forever
      // Android requires startForeground() within 5 seconds
      await _channel.invokeMethod('startService').timeout(
        const Duration(seconds: 8),
        onTimeout: () {
          throw TimeoutException(
            'Foreground service start timeout after 8 seconds. '
            'Android requires startForeground() within 5 seconds.',
          );
        },
      );

      await _logger.log(
        level: 'info',
        subsystem: 'download',
        message: 'Download foreground service started successfully',
      );
    } on PlatformException catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'download',
        message: 'Platform error starting download service',
        cause: e.toString(),
        extra: {
          'code': e.code,
          'message': e.message,
        },
      );
      // CRITICAL: Re-throw to allow caller to handle
      // Previous implementation swallowed exceptions, which could cause downloads to hang
      rethrow;
    } on TimeoutException catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'download',
        message: 'Timeout starting download service',
        cause: e.toString(),
      );
      // CRITICAL: Re-throw to allow caller to handle
      rethrow;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'download',
        message: 'Failed to start download foreground service',
        cause: e.toString(),
      );
      // CRITICAL: Re-throw to allow caller to handle
      rethrow;
    }
  }

  /// Stops the download foreground service.
  ///
  /// This should be called when all downloads are completed or cancelled.
  Future<void> stopService() async {
    try {
      await _logger.log(
        level: 'info',
        subsystem: 'download',
        message: 'Stopping download foreground service',
      );

      await _channel.invokeMethod('stopService');

      await _logger.log(
        level: 'info',
        subsystem: 'download',
        message: 'Download foreground service stopped successfully',
      );
    } on PlatformException catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'download',
        message: 'Platform error stopping download service',
        cause: e.toString(),
        extra: {
          'code': e.code,
          'message': e.message,
        },
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'download',
        message: 'Failed to stop download foreground service',
        cause: e.toString(),
      );
    }
  }

  /// Updates download progress in the foreground service notification.
  ///
  /// [title] is the download title.
  /// [progress] is the download progress (0-100).
  /// [speed] is the download speed formatted string (e.g., "1.5 MB/s").
  Future<void> updateProgress({
    required String title,
    required double progress,
    String speed = '',
  }) async {
    try {
      await _channel.invokeMethod('updateProgress', {
        'title': title,
        'progress': progress,
        'speed': speed,
      });
    } on PlatformException catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'download',
        message: 'Platform error updating download progress',
        cause: e.toString(),
        extra: {
          'code': e.code,
          'message': e.message,
        },
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'download',
        message: 'Failed to update download progress',
        cause: e.toString(),
      );
    }
  }
}
