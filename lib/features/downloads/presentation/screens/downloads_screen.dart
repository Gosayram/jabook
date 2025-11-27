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
import 'package:go_router/go_router.dart';
import 'package:jabook/core/config/app_config.dart';
import 'package:jabook/core/logging/environment_logger.dart';
import 'package:jabook/core/torrent/audiobook_torrent_manager.dart';
import 'package:jabook/core/utils/app_title_utils.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Screen for displaying all active downloads.
///
/// This screen shows a list of all active torrent downloads
/// with their progress, speed, and controls.
class DownloadsScreen extends StatefulWidget {
  /// Creates a new DownloadsScreen instance.
  ///
  /// If [highlightDownloadId] is provided, the screen will scroll to
  /// and highlight that download when the list is loaded.
  const DownloadsScreen({
    super.key,
    this.highlightDownloadId,
  });

  /// Optional download ID to highlight when the screen loads.
  final String? highlightDownloadId;

  @override
  State<DownloadsScreen> createState() => _DownloadsScreenState();
}

class _DownloadsScreenState extends State<DownloadsScreen> {
  final AudiobookTorrentManager _torrentManager = AudiobookTorrentManager();
  Timer? _refreshTimer;
  List<Map<String, dynamic>> _downloads = [];
  final Map<String, TorrentProgress> _progressMap = {};
  final ScrollController _scrollController = ScrollController();
  bool _hasScrolledToHighlight = false;

  @override
  void initState() {
    super.initState();
    // Load downloads immediately with small delay to ensure downloads are initialized
    WidgetsBinding.instance.addPostFrameCallback((_) {
      // Small delay to ensure download is added to manager before loading
      Future.delayed(const Duration(milliseconds: 100), _loadDownloads);
    });
    // Refresh every 2 seconds
    _refreshTimer = Timer.periodic(const Duration(seconds: 2), (_) {
      _loadDownloads();
    });
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    // Reload downloads when screen becomes visible
    // Small delay to ensure downloads are initialized
    Future.delayed(const Duration(milliseconds: 100), _loadDownloads);
  }

  @override
  void dispose() {
    _refreshTimer?.cancel();
    _scrollController.dispose();
    super.dispose();
  }

  Future<void> _loadDownloads() async {
    try {
      EnvironmentLogger().i('DownloadsScreen: Starting to load downloads');
      final downloads = await _torrentManager.getActiveDownloads();

      EnvironmentLogger().d(
        'DownloadsScreen: Loaded ${downloads.length} downloads (active: ${downloads.where((d) => d['isActive'] as bool? ?? false).length}, restored: ${downloads.where((d) => d['status'] == 'restored').length})',
      );

      // Update progress for each download using StreamBuilder approach
      // We'll use a different strategy - update progress in build method
      if (mounted) {
        setState(() {
          _downloads = downloads;
        });

        // Scroll to highlighted download if needed
        if (widget.highlightDownloadId != null &&
            !_hasScrolledToHighlight &&
            downloads.isNotEmpty) {
          _scrollToDownload(widget.highlightDownloadId!);
        }
      } else {
        EnvironmentLogger()
            .w('DownloadsScreen: Widget not mounted, skipping state update');
      }
    } on Exception catch (e) {
      // Log error for debugging
      EnvironmentLogger()
          .e('DownloadsScreen: Failed to load downloads', error: e);
      if (mounted) {
        setState(() {
          _downloads = [];
        });
      }
    }
  }

  /// Gets a user-friendly error message from an exception.
  String _getUserFriendlyErrorMessage(Exception e) {
    final errorStr = e.toString().toLowerCase();
    if (errorStr.contains('timeout') || errorStr.contains('timed out')) {
      return AppLocalizations.of(context)?.operationTimedOut ??
          'Operation timed out. Please try again.';
    } else if (errorStr.contains('network') ||
        errorStr.contains('connection') ||
        errorStr.contains('socket')) {
      return AppLocalizations.of(context)?.networkErrorMessage('') ??
          'Network error. Please check your connection.';
    } else if (errorStr.contains('permission') || errorStr.contains('access')) {
      return AppLocalizations.of(context)?.permissionDeniedDownloads ??
          'Permission denied. Please check app permissions in settings.';
    } else if (errorStr.contains('not found') || errorStr.contains('missing')) {
      return AppLocalizations.of(context)?.downloadNotFound ??
          'Download not found. It may have been removed.';
    } else {
      return AppLocalizations.of(context)?.anErrorOccurred ??
          'An error occurred. Please try again.';
    }
  }

  /// Scrolls to the download with the given ID.
  void _scrollToDownload(String downloadId) {
    if (_hasScrolledToHighlight) return;

    final index = _downloads.indexWhere(
      (download) => download['id'] == downloadId,
    );

    if (index != -1 && _scrollController.hasClients) {
      _hasScrolledToHighlight = true;
      // Wait for the list to be built, then scroll
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (_scrollController.hasClients) {
          const itemHeight = 120.0; // Approximate height of each download item
          final scrollPosition = index * itemHeight;
          _scrollController.animateTo(
            scrollPosition.clamp(
                0.0, _scrollController.position.maxScrollExtent),
            duration: const Duration(milliseconds: 300),
            curve: Curves.easeInOut,
          );
          EnvironmentLogger().d(
            'Scrolled to download $downloadId at index $index',
          );
        }
      });
    }
  }

  String _formatBytes(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    if (bytes < 1024 * 1024 * 1024) {
      return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
    }
    return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(1)} GB';
  }

  String _formatSpeed(double bytesPerSecond) {
    if (bytesPerSecond < 1024) return '${bytesPerSecond.toInt()} B/s';
    if (bytesPerSecond < 1024 * 1024) {
      return '${(bytesPerSecond / 1024).toStringAsFixed(1)} KB/s';
    }
    return '${(bytesPerSecond / (1024 * 1024)).toStringAsFixed(1)} MB/s';
  }

  String _formatTimeRemaining(TorrentProgress progress) {
    if (progress.downloadSpeed <= 0 || progress.progress >= 100) {
      return '';
    }
    final remainingBytes = progress.totalBytes - progress.downloadedBytes;
    final secondsRemaining = (remainingBytes / progress.downloadSpeed).ceil();

    if (secondsRemaining < 60) {
      return '${secondsRemaining}s';
    } else if (secondsRemaining < 3600) {
      final minutes = secondsRemaining ~/ 60;
      final seconds = secondsRemaining % 60;
      return '${minutes}m ${seconds}s';
    } else {
      final hours = secondsRemaining ~/ 3600;
      final minutes = (secondsRemaining % 3600) ~/ 60;
      return '${hours}h ${minutes}m';
    }
  }

  String _formatPath(String? path) {
    if (path == null || path.isEmpty) return '';
    // Show only last part of path for brevity
    final parts = path.split('/');
    if (parts.length > 3) {
      return '.../${parts.sublist(parts.length - 2).join('/')}';
    }
    return path;
  }

  @override
  Widget build(BuildContext context) => PopScope(
        canPop: false,
        onPopInvokedWithResult: (didPop, result) {
          if (didPop) {
            EnvironmentLogger()
                .d('DownloadsScreen: Pop already handled by system');
            return;
          }
          // Downloads is now a root tab, so back should navigate to Library tab
          EnvironmentLogger().d(
            'DownloadsScreen: onPopInvokedWithResult - navigating to Library tab',
          );
          try {
            context.go('/');
            EnvironmentLogger()
                .d('DownloadsScreen: Successfully navigated to Library tab');
          } on Exception catch (e) {
            EnvironmentLogger().w(
              'DownloadsScreen: Failed to navigate to Library',
              error: e,
            );
          }
        },
        child: Scaffold(
          appBar: AppBar(
            title: Text(
              (AppLocalizations.of(context)?.downloadsTitle ?? 'Downloads')
                  .withFlavorSuffix(),
            ),
            actions: [
              if (AppConfig().debugFeaturesEnabled)
                IconButton(
                  icon: const Icon(Icons.bug_report),
                  onPressed: () => context.go('/debug'),
                  tooltip: AppLocalizations.of(context)?.debugTitle ?? 'Debug',
                ),
            ],
          ),
          body: Column(
            children: [
              // Warning banner about downloads stopping when app closes
              if (_downloads.isNotEmpty &&
                  _downloads.any((d) => d['isActive'] as bool? ?? false))
                Container(
                  width: double.infinity,
                  margin: const EdgeInsets.all(8),
                  padding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 12,
                  ),
                  decoration: BoxDecoration(
                    color: Theme.of(context)
                        .colorScheme
                        .surfaceContainerHighest
                        .withValues(alpha: 0.7),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(
                      color: Theme.of(context)
                          .colorScheme
                          .primary
                          .withValues(alpha: 0.3),
                    ),
                  ),
                  child: Row(
                    children: [
                      Icon(
                        Icons.info_outline,
                        color: Theme.of(context).colorScheme.primary,
                        size: 20,
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Text(
                          'Downloads will pause when the app is closed. '
                          'You can resume them later from this screen.',
                          style:
                              Theme.of(context).textTheme.bodySmall?.copyWith(
                                    color: Theme.of(context)
                                        .colorScheme
                                        .onSurface
                                        .withValues(alpha: 0.8),
                                  ),
                        ),
                      ),
                    ],
                  ),
                ),
              // Downloads list or empty state
              Expanded(
                child: _downloads.isEmpty
                    ? Center(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(
                              Icons.download_done,
                              size: 64,
                              color: Theme.of(context)
                                  .colorScheme
                                  .onSurface
                                  .withValues(alpha: 0.3),
                            ),
                            const SizedBox(height: 16),
                            Text(
                              AppLocalizations.of(context)?.noActiveDownloads ??
                                  'No active downloads',
                              style: Theme.of(context)
                                  .textTheme
                                  .titleMedium
                                  ?.copyWith(
                                    color: Theme.of(context)
                                        .colorScheme
                                        .onSurface
                                        .withValues(alpha: 0.6),
                                  ),
                            ),
                          ],
                        ),
                      )
                    : RefreshIndicator(
                        onRefresh: _loadDownloads,
                        child: ListView.builder(
                          controller: _scrollController,
                          itemCount: _downloads.length,
                          itemBuilder: (context, index) {
                            final download = _downloads[index];
                            final downloadId = download['id'] as String? ?? '';
                            final title =
                                download['name'] as String? ?? 'Unknown';
                            final savePath = download['savePath'] as String?;
                            final status = download['status'] as String? ?? '';
                            final isActive =
                                download['isActive'] as bool? ?? true;
                            final isRestored = status == 'restored';

                            // Get current progress from stream (only for active downloads)
                            Stream<TorrentProgress>? progressStream;
                            if (isActive) {
                              try {
                                progressStream = _torrentManager
                                    .getProgressStream(downloadId);
                                EnvironmentLogger().d(
                                  'DownloadsScreen: Successfully obtained progress stream for download $downloadId',
                                );
                              } on Exception catch (e) {
                                // Download might be completed or removed
                                EnvironmentLogger().w(
                                  'DownloadsScreen: Failed to get progress stream for download $downloadId',
                                  error: e,
                                );
                                progressStream = null;
                              }
                            }

                            // Handle restored downloads (no progress stream)
                            if (isRestored && progressStream == null) {
                              return Card(
                                margin: const EdgeInsets.symmetric(
                                    horizontal: 8, vertical: 4),
                                child: ListTile(
                                  leading: Icon(
                                    Icons.download_outlined,
                                    color: Theme.of(context)
                                        .colorScheme
                                        .onSurface
                                        .withValues(alpha: 0.6),
                                  ),
                                  title: Text(title,
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis),
                                  subtitle: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        'Download restored - tap to resume',
                                        style: Theme.of(context)
                                            .textTheme
                                            .bodySmall
                                            ?.copyWith(
                                              color: Theme.of(context)
                                                  .colorScheme
                                                  .onSurface
                                                  .withValues(alpha: 0.6),
                                            ),
                                      ),
                                      if (savePath != null &&
                                          savePath.isNotEmpty) ...[
                                        const SizedBox(height: 4),
                                        Row(
                                          children: [
                                            Icon(
                                              Icons.folder,
                                              size: 14,
                                              color: Theme.of(context)
                                                  .colorScheme
                                                  .onSurface
                                                  .withValues(alpha: 0.6),
                                            ),
                                            const SizedBox(width: 4),
                                            Expanded(
                                              child: Text(
                                                _formatPath(savePath),
                                                style: Theme.of(context)
                                                    .textTheme
                                                    .bodySmall
                                                    ?.copyWith(
                                                      color: Theme.of(context)
                                                          .colorScheme
                                                          .onSurface
                                                          .withValues(
                                                              alpha: 0.6),
                                                    ),
                                                maxLines: 1,
                                                overflow: TextOverflow.ellipsis,
                                              ),
                                            ),
                                          ],
                                        ),
                                      ],
                                    ],
                                  ),
                                  trailing: PopupMenuButton<String>(
                                    onSelected: (value) async {
                                      final messenger =
                                          ScaffoldMessenger.of(context);
                                      try {
                                        if (value == 'resume') {
                                          await _torrentManager
                                              .resumeRestoredDownload(
                                                  downloadId);
                                        } else if (value == 'restart') {
                                          await _torrentManager
                                              .restartDownload(downloadId);
                                        } else if (value == 'redownload') {
                                          await _torrentManager
                                              .redownload(downloadId);
                                        } else if (value == 'remove') {
                                          await _torrentManager
                                              .removeDownload(downloadId);
                                        }
                                        await _loadDownloads();
                                      } on Exception catch (e) {
                                        if (!mounted) return;
                                        final errorMsg =
                                            _getUserFriendlyErrorMessage(e);
                                        messenger.showSnackBar(
                                          SnackBar(
                                            content: Text(errorMsg),
                                            backgroundColor: Colors.orange,
                                            duration:
                                                const Duration(seconds: 3),
                                          ),
                                        );
                                      }
                                    },
                                    itemBuilder: (context) => [
                                      const PopupMenuItem(
                                        value: 'resume',
                                        child: Row(
                                          children: [
                                            Icon(Icons.play_arrow),
                                            SizedBox(width: 8),
                                            Text('Resume'),
                                          ],
                                        ),
                                      ),
                                      const PopupMenuItem(
                                        value: 'restart',
                                        child: Row(
                                          children: [
                                            Icon(Icons.refresh),
                                            SizedBox(width: 8),
                                            Text('Restart'),
                                          ],
                                        ),
                                      ),
                                      const PopupMenuItem(
                                        value: 'redownload',
                                        child: Row(
                                          children: [
                                            Icon(Icons.download),
                                            SizedBox(width: 8),
                                            Text('Redownload'),
                                          ],
                                        ),
                                      ),
                                      const PopupMenuItem(
                                        value: 'remove',
                                        child: Row(
                                          children: [
                                            Icon(Icons.delete),
                                            SizedBox(width: 8),
                                            Text('Remove'),
                                          ],
                                        ),
                                      ),
                                    ],
                                    child: const Icon(Icons.more_vert),
                                  ),
                                ),
                              );
                            }

                            // Handle downloads without progress stream (completed or error)
                            if (progressStream == null && !isRestored) {
                              // Fallback to static data from download map
                              final staticProgress =
                                  download['progress'] as double? ?? 0.0;
                              final isCompleted = staticProgress >= 100.0;
                              final isError = status.contains('error');
                              return Card(
                                margin: const EdgeInsets.symmetric(
                                    horizontal: 8, vertical: 4),
                                child: ListTile(
                                  leading: Icon(
                                    isCompleted
                                        ? Icons.check_circle
                                        : isError
                                            ? Icons.error
                                            : Icons.download,
                                    color: isCompleted
                                        ? Colors.green
                                        : isError
                                            ? Colors.red
                                            : Theme.of(context)
                                                .colorScheme
                                                .primary,
                                  ),
                                  title: Text(title,
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis),
                                  subtitle: Text(
                                      '${staticProgress.toStringAsFixed(1)}%'),
                                  trailing: PopupMenuButton<String>(
                                    onSelected: (value) async {
                                      final messenger =
                                          ScaffoldMessenger.of(context);
                                      try {
                                        if (value == 'restart') {
                                          await _torrentManager
                                              .restartDownload(downloadId);
                                        } else if (value == 'redownload') {
                                          await _torrentManager
                                              .redownload(downloadId);
                                        } else if (value == 'remove') {
                                          await _torrentManager
                                              .removeDownload(downloadId);
                                        }
                                        await _loadDownloads();
                                      } on Exception catch (e) {
                                        if (!mounted) return;
                                        final errorMsg =
                                            _getUserFriendlyErrorMessage(e);
                                        messenger.showSnackBar(
                                          SnackBar(
                                            content: Text(errorMsg),
                                            backgroundColor: Colors.orange,
                                            duration:
                                                const Duration(seconds: 3),
                                          ),
                                        );
                                      }
                                    },
                                    itemBuilder: (context) => [
                                      if (isError || isCompleted)
                                        const PopupMenuItem(
                                          value: 'restart',
                                          child: Row(
                                            children: [
                                              Icon(Icons.refresh),
                                              SizedBox(width: 8),
                                              Text('Restart'),
                                            ],
                                          ),
                                        ),
                                      const PopupMenuItem(
                                        value: 'redownload',
                                        child: Row(
                                          children: [
                                            Icon(Icons.download),
                                            SizedBox(width: 8),
                                            Text('Redownload'),
                                          ],
                                        ),
                                      ),
                                      const PopupMenuItem(
                                        value: 'remove',
                                        child: Row(
                                          children: [
                                            Icon(Icons.delete),
                                            SizedBox(width: 8),
                                            Text('Remove'),
                                          ],
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              );
                            }

                            return StreamBuilder<TorrentProgress>(
                              stream: progressStream,
                              builder: (context, snapshot) {
                                final progress =
                                    snapshot.data ?? _progressMap[downloadId];
                                if (progress != null &&
                                    !_progressMap.containsKey(downloadId)) {
                                  // Cache progress
                                  WidgetsBinding.instance
                                      .addPostFrameCallback((_) {
                                    if (mounted) {
                                      setState(() {
                                        _progressMap[downloadId] = progress;
                                      });
                                    }
                                  });
                                }
                                final isPaused = progress?.status == 'paused';
                                final isCompleted =
                                    progress?.status == 'completed';
                                final isError =
                                    progress?.status.startsWith('error') ??
                                        false;

                                return Card(
                                  margin: const EdgeInsets.symmetric(
                                    horizontal: 8,
                                    vertical: 4,
                                  ),
                                  child: ListTile(
                                    leading: Icon(
                                      isCompleted
                                          ? Icons.check_circle
                                          : isError
                                              ? Icons.error
                                              : Icons.download,
                                      color: isCompleted
                                          ? Colors.green
                                          : isError
                                              ? Colors.red
                                              : Theme.of(context)
                                                  .colorScheme
                                                  .primary,
                                    ),
                                    title: Text(
                                      title,
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                    subtitle: progress != null
                                        ? Column(
                                            crossAxisAlignment:
                                                CrossAxisAlignment.start,
                                            children: [
                                              const SizedBox(height: 4),
                                              LinearProgressIndicator(
                                                value: progress.progress / 100,
                                                backgroundColor:
                                                    Theme.of(context)
                                                        .colorScheme
                                                        .surfaceContainerHighest
                                                        .withValues(alpha: 0.3),
                                              ),
                                              const SizedBox(height: 4),
                                              Row(
                                                children: [
                                                  Text(
                                                    '${progress.progress.toStringAsFixed(1)}%',
                                                    style: Theme.of(context)
                                                        .textTheme
                                                        .bodySmall,
                                                  ),
                                                  const SizedBox(width: 8),
                                                  Text(
                                                    '${_formatBytes(progress.downloadedBytes)} / ${_formatBytes(progress.totalBytes)}',
                                                    style: Theme.of(context)
                                                        .textTheme
                                                        .bodySmall,
                                                  ),
                                                ],
                                              ),
                                              const SizedBox(height: 2),
                                              Wrap(
                                                spacing: 8,
                                                runSpacing: 4,
                                                children: [
                                                  if (progress.downloadSpeed >
                                                      0) ...[
                                                    Text(
                                                      _formatSpeed(progress
                                                          .downloadSpeed),
                                                      style: Theme.of(context)
                                                          .textTheme
                                                          .bodySmall
                                                          ?.copyWith(
                                                            color: Theme.of(
                                                                    context)
                                                                .colorScheme
                                                                .primary,
                                                          ),
                                                    ),
                                                    if (progress.progress <
                                                        100) ...[
                                                      Text(
                                                        '•',
                                                        style: Theme.of(context)
                                                            .textTheme
                                                            .bodySmall,
                                                      ),
                                                      Text(
                                                        _formatTimeRemaining(
                                                            progress),
                                                        style: Theme.of(context)
                                                            .textTheme
                                                            .bodySmall,
                                                      ),
                                                    ],
                                                  ],
                                                ],
                                              ),
                                              if (savePath != null &&
                                                  savePath.isNotEmpty) ...[
                                                const SizedBox(height: 4),
                                                Row(
                                                  children: [
                                                    Icon(
                                                      Icons.folder,
                                                      size: 14,
                                                      color: Theme.of(context)
                                                          .colorScheme
                                                          .onSurface
                                                          .withValues(
                                                              alpha: 0.6),
                                                    ),
                                                    const SizedBox(width: 4),
                                                    Expanded(
                                                      child: Text(
                                                        _formatPath(savePath),
                                                        style: Theme.of(context)
                                                            .textTheme
                                                            .bodySmall
                                                            ?.copyWith(
                                                              color: Theme.of(
                                                                      context)
                                                                  .colorScheme
                                                                  .onSurface
                                                                  .withValues(
                                                                      alpha:
                                                                          0.6),
                                                            ),
                                                        maxLines: 1,
                                                        overflow: TextOverflow
                                                            .ellipsis,
                                                      ),
                                                    ),
                                                  ],
                                                ),
                                              ],
                                            ],
                                          )
                                        : Text(
                                            AppLocalizations.of(context)
                                                    ?.loading ??
                                                'Loading...',
                                          ),
                                    trailing: Row(
                                      mainAxisSize: MainAxisSize.min,
                                      children: [
                                        if (!isCompleted && !isError)
                                          IconButton(
                                            icon: Icon(isPaused
                                                ? Icons.play_arrow
                                                : Icons.pause),
                                            onPressed: () async {
                                              final messenger =
                                                  ScaffoldMessenger.of(context);
                                              try {
                                                if (isPaused) {
                                                  await _torrentManager
                                                      .resumeDownload(
                                                          downloadId);
                                                } else {
                                                  await _torrentManager
                                                      .pauseDownload(
                                                          downloadId);
                                                }
                                                await _loadDownloads();
                                              } on Exception catch (e) {
                                                if (!mounted) return;
                                                final errorMsg =
                                                    _getUserFriendlyErrorMessage(
                                                        e);
                                                messenger.showSnackBar(
                                                  SnackBar(
                                                    content: Text(errorMsg),
                                                    backgroundColor:
                                                        Colors.orange,
                                                    duration: const Duration(
                                                        seconds: 3),
                                                  ),
                                                );
                                              }
                                            },
                                            tooltip:
                                                isPaused ? 'Resume' : 'Pause',
                                          ),
                                        PopupMenuButton<String>(
                                          onSelected: (value) async {
                                            final messenger =
                                                ScaffoldMessenger.of(context);
                                            try {
                                              if (value == 'restart') {
                                                await _torrentManager
                                                    .restartDownload(
                                                        downloadId);
                                              } else if (value ==
                                                  'redownload') {
                                                await _torrentManager
                                                    .redownload(downloadId);
                                              } else if (value == 'remove') {
                                                await _torrentManager
                                                    .removeDownload(downloadId);
                                              }
                                              await _loadDownloads();
                                            } on Exception catch (e) {
                                              if (!mounted) return;
                                              final errorMsg =
                                                  _getUserFriendlyErrorMessage(
                                                      e);
                                              messenger.showSnackBar(
                                                SnackBar(
                                                  content: Text(errorMsg),
                                                  backgroundColor:
                                                      Colors.orange,
                                                  duration: const Duration(
                                                      seconds: 3),
                                                ),
                                              );
                                            }
                                          },
                                          itemBuilder: (context) => [
                                            if (isError)
                                              const PopupMenuItem(
                                                value: 'restart',
                                                child: Row(
                                                  children: [
                                                    Icon(Icons.refresh),
                                                    SizedBox(width: 8),
                                                    Text('Restart'),
                                                  ],
                                                ),
                                              ),
                                            const PopupMenuItem(
                                              value: 'redownload',
                                              child: Row(
                                                children: [
                                                  Icon(Icons.download),
                                                  SizedBox(width: 8),
                                                  Text('Redownload'),
                                                ],
                                              ),
                                            ),
                                            const PopupMenuItem(
                                              value: 'remove',
                                              child: Row(
                                                children: [
                                                  Icon(Icons.delete),
                                                  SizedBox(width: 8),
                                                  Text('Remove'),
                                                ],
                                              ),
                                            ),
                                          ],
                                          child: const Icon(Icons.more_vert),
                                        ),
                                      ],
                                    ),
                                  ),
                                );
                              },
                            );
                          },
                        ),
                      ),
              ),
            ],
          ),
        ),
      );
}
