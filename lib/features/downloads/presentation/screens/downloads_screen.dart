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
import 'package:jabook/core/torrent/audiobook_torrent_manager.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Screen for displaying all active downloads.
///
/// This screen shows a list of all active torrent downloads
/// with their progress, speed, and controls.
class DownloadsScreen extends StatefulWidget {
  /// Creates a new DownloadsScreen instance.
  const DownloadsScreen({super.key});

  @override
  State<DownloadsScreen> createState() => _DownloadsScreenState();
}

class _DownloadsScreenState extends State<DownloadsScreen> {
  final AudiobookTorrentManager _torrentManager = AudiobookTorrentManager();
  Timer? _refreshTimer;
  List<Map<String, dynamic>> _downloads = [];
  final Map<String, TorrentProgress> _progressMap = {};

  @override
  void initState() {
    super.initState();
    _loadDownloads();
    // Refresh every 2 seconds
    _refreshTimer = Timer.periodic(const Duration(seconds: 2), (_) {
      _loadDownloads();
    });
  }

  @override
  void dispose() {
    _refreshTimer?.cancel();
    super.dispose();
  }

  Future<void> _loadDownloads() async {
    try {
      final downloads = await _torrentManager.getActiveDownloads();
      
      // Update progress for each download using StreamBuilder approach
      // We'll use a different strategy - update progress in build method
      if (mounted) {
        setState(() {
          _downloads = downloads;
        });
      }
    } on Exception {
      // Ignore errors
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

  String _formatSpeed(double bytesPerSecond) => '${_formatBytes(bytesPerSecond.toInt())}/s';

  @override
  Widget build(BuildContext context) => Scaffold(
      appBar: AppBar(
        title: Text(
          AppLocalizations.of(context)?.downloadsTitle ?? 'Downloads',
        ),
      ),
      body: _downloads.isEmpty
          ? Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(
                    Icons.download_done,
                    size: 64,
                    color: Theme.of(context).colorScheme.onSurface.withValues(alpha: 0.3),
                  ),
                  const SizedBox(height: 16),
                  Text(
                    AppLocalizations.of(context)?.noActiveDownloads ?? 'No active downloads',
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
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
                itemCount: _downloads.length,
                itemBuilder: (context, index) {
                  final download = _downloads[index];
                  final downloadId = download['id'] as String? ?? '';
                  final title = download['name'] as String? ?? 'Unknown';
                  
                  // Get current progress from stream
                  Stream<TorrentProgress>? progressStream;
                  try {
                    progressStream = _torrentManager.getProgressStream(downloadId);
                  } on Exception {
                    // Download might be completed or removed
                    progressStream = null;
                  }
                  
                  if (progressStream == null) {
                    // Fallback to static data from download map
                    final staticProgress = download['progress'] as double? ?? 0.0;
                    final isCompleted = staticProgress >= 100.0;
                    return Card(
                      margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                      child: ListTile(
                        leading: Icon(
                          isCompleted ? Icons.check_circle : Icons.download,
                          color: isCompleted
                              ? Colors.green
                              : Theme.of(context).colorScheme.primary,
                        ),
                        title: Text(title, maxLines: 1, overflow: TextOverflow.ellipsis),
                        subtitle: Text('${staticProgress.toStringAsFixed(1)}%'),
                      ),
                    );
                  }
                  
                  return StreamBuilder<TorrentProgress>(
                    stream: progressStream,
                    builder: (context, snapshot) {
                      final progress = snapshot.data ?? _progressMap[downloadId];
                      if (progress != null && !_progressMap.containsKey(downloadId)) {
                        // Cache progress
                        WidgetsBinding.instance.addPostFrameCallback((_) {
                          if (mounted) {
                            setState(() {
                              _progressMap[downloadId] = progress;
                            });
                          }
                        });
                      }
                      final isPaused = progress?.status == 'paused';
                      final isCompleted = progress?.status == 'completed';
                      final isError = progress?.status.startsWith('error') ?? false;

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
                                : Theme.of(context).colorScheme.primary,
                      ),
                      title: Text(
                        title,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      subtitle: progress != null
                          ? Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
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
                            )
                          : Text(
                              AppLocalizations.of(context)?.loading ?? 'Loading...',
                            ),
                      trailing: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          if (!isCompleted && !isError)
                            IconButton(
                              icon: Icon(isPaused ? Icons.play_arrow : Icons.pause),
                              onPressed: () async {
                                final messenger = ScaffoldMessenger.of(context);
                                try {
                                  if (isPaused) {
                                    await _torrentManager.resumeDownload(downloadId);
                                  } else {
                                    await _torrentManager.pauseDownload(downloadId);
                                  }
                                  await _loadDownloads();
                                } on Exception catch (e) {
                                  if (!mounted) return;
                                  messenger.showSnackBar(
                                    SnackBar(
                                      content: Text('Error: ${e.toString()}'),
                                    ),
                                  );
                                }
                              },
                              tooltip: isPaused ? 'Resume' : 'Pause',
                            ),
                          IconButton(
                            icon: const Icon(Icons.close),
                            onPressed: () async {
                              final messenger = ScaffoldMessenger.of(context);
                              try {
                                await _torrentManager.removeDownload(downloadId);
                                await _loadDownloads();
                              } on Exception catch (e) {
                                if (!mounted) return;
                                messenger.showSnackBar(
                                  SnackBar(
                                    content: Text('Error: ${e.toString()}'),
                                  ),
                                );
                              }
                            },
                            tooltip: 'Cancel',
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
    );
}

