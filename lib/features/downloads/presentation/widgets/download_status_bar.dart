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

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:jabook/core/torrent/audiobook_torrent_manager.dart';

/// Widget for displaying download status in a status bar.
///
/// This widget shows the progress of an active torrent download,
/// including progress percentage, download speed, and control buttons.
class DownloadStatusBar extends StatelessWidget {
  /// Creates a new DownloadStatusBar instance.
  ///
  /// The [downloadId] parameter is the unique identifier of the download.
  /// The [progress] parameter contains the current download progress.
  /// The [onPause] callback is called when the pause button is pressed.
  /// The [onResume] callback is called when the resume button is pressed.
  /// The [onCancel] callback is called when the cancel button is pressed.
  const DownloadStatusBar({
    super.key,
    required this.downloadId,
    required this.progress,
    required this.onPause,
    required this.onResume,
    required this.onCancel,
  });

  /// Unique identifier of the download.
  final String downloadId;

  /// Current download progress.
  final TorrentProgress progress;

  /// Callback when pause button is pressed.
  final VoidCallback onPause;

  /// Callback when resume button is pressed.
  final VoidCallback onResume;

  /// Callback when cancel button is pressed.
  final VoidCallback onCancel;

  String _formatBytes(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    if (bytes < 1024 * 1024 * 1024) {
      return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
    }
    return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(1)} GB';
  }

  String _formatSpeed(double bytesPerSecond) => '${_formatBytes(bytesPerSecond.toInt())}/s';

  @override
  Widget build(BuildContext context) {
    final isPaused = progress.status == 'paused';
    final isCompleted = progress.status == 'completed';
    final isError = progress.status.startsWith('error');

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surfaceContainerHighest,
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.1),
            blurRadius: 4,
            offset: const Offset(0, -2),
          ),
        ],
      ),
      child: Row(
        children: [
          Icon(
            isCompleted
                ? Icons.check_circle
                : isError
                    ? Icons.error
                    : Icons.download,
            color: isCompleted
                ? Colors.green
                : isError
                    ? Colors.red
                    : Theme.of(context).colorScheme.primary,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  isCompleted
                      ? 'Download completed'
                      : isError
                          ? 'Download error'
                          : 'Downloading...',
                  style: Theme.of(context).textTheme.titleSmall,
                ),
                const SizedBox(height: 4),
                LinearProgressIndicator(
                  value: progress.progress / 100,
                  backgroundColor: Theme.of(context)
                      .colorScheme
                      .surfaceContainerHighest
                      .withValues(alpha: 0.3),
                ),
                const SizedBox(height: 4),
                Row(
                  children: [
                    Text(
                      '${progress.progress.toStringAsFixed(1)}%',
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                    const SizedBox(width: 8),
                    Text(
                      '${_formatBytes(progress.downloadedBytes)} / ${_formatBytes(progress.totalBytes)}',
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                    if (progress.downloadSpeed > 0) ...[
                      const SizedBox(width: 8),
                      Text(
                        _formatSpeed(progress.downloadSpeed),
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                    ],
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          IconButton(
            icon: const Icon(Icons.list),
            onPressed: () {
              context.go('/downloads');
            },
            tooltip: 'View all downloads',
          ),
          if (!isCompleted && !isError)
            IconButton(
              icon: Icon(isPaused ? Icons.play_arrow : Icons.pause),
              onPressed: isPaused ? onResume : onPause,
              tooltip: isPaused ? 'Resume' : 'Pause',
            ),
          IconButton(
            icon: const Icon(Icons.close),
            onPressed: onCancel,
            tooltip: 'Cancel',
          ),
        ],
      ),
    );
  }
}

