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
import 'dart:io';
import 'dart:typed_data';

import 'package:b_encode_decode/b_encode_decode.dart';
// ignore: unused_import
import 'package:dtorrent_common/dtorrent_common.dart'; // PeerSource is used in addPeer call
import 'package:dtorrent_parser/dtorrent_parser.dart';
import 'package:dtorrent_task_v2/dtorrent_task_v2.dart';
import 'package:jabook/core/errors/failures.dart';
import 'package:jabook/core/logging/environment_logger.dart';
import 'package:jabook/core/notifications/download_notification_service.dart';
import 'package:jabook/core/utils/safe_async.dart';
import 'package:jabook/data/db/app_database.dart';
import 'package:path_provider/path_provider.dart';
import 'package:sembast/sembast.dart';

/// Represents the progress of a torrent download.
///
/// This class contains all the information needed to display
/// and monitor the progress of a torrent download, including
/// transfer speeds, completion percentage, and peer information.
class TorrentProgress {
  /// Creates a new TorrentProgress instance.
  ///
  /// All parameters are required to provide complete download progress information.
  TorrentProgress({
    required this.progress,
    required this.downloadSpeed,
    required this.uploadSpeed,
    required this.downloadedBytes,
    required this.totalBytes,
    required this.seeders,
    required this.leechers,
    required this.status,
  });

  /// Download progress as a percentage (0.0 to 100.0).
  final double progress;

  /// Current download speed in bytes per second.
  final double downloadSpeed;

  /// Current upload speed in bytes per second.
  final double uploadSpeed;

  /// Number of bytes downloaded so far.
  final int downloadedBytes;

  /// Total size of the torrent in bytes.
  final int totalBytes;

  /// Number of seeders connected to the torrent.
  final int seeders;

  /// Number of leechers connected to the torrent.
  final int leechers;

  /// Current status of the download (e.g., 'downloading', 'completed', 'paused').
  final String status;
}

/// Manages torrent downloads for audiobooks.
///
/// This class provides functionality to start, pause, resume, and monitor
/// torrent downloads for audiobook files. It uses a singleton pattern
/// to ensure consistent download management across the application.
class AudiobookTorrentManager {
  /// Private constructor for singleton pattern.
  AudiobookTorrentManager._();

  /// Factory constructor to get the singleton instance.
  factory AudiobookTorrentManager() => AudiobookTorrentManager._();

  /// Map of download progress controllers for active downloads.
  final Map<String, StreamController<TorrentProgress>> _progressControllers =
      {};

  /// Map of active torrent tasks.
  final Map<String, TorrentTask> _activeTasks = {};

  /// Map of download metadata.
  final Map<String, Map<String, dynamic>> _downloadMetadata = {};

  /// Map of metadata downloaders for active downloads.
  final Map<String, MetadataDownloader> _metadataDownloaders = {};

  /// Database instance for persisting download state.
  Database? _db;

  /// Notification service for download progress updates.
  final DownloadNotificationService _notificationService =
      DownloadNotificationService();

  /// Initializes the torrent manager with database connection.
  ///
  /// This method should be called once when the app starts to enable
  /// persistence of download state across app restarts.
  Future<void> initialize(Database? db) async {
    _db = db;
    if (_db != null) {
      await _restoreDownloads();
    }
    // Initialize notification service
    await _notificationService.initialize();
  }

  /// Starts a sequential torrent download for an audiobook.
  ///
  /// This method initiates a torrent download using the provided magnet URL
  /// and saves it to the specified path. It creates a progress stream
  /// to monitor download progress.
  ///
  /// The [magnetUrl] parameter is the magnet link for the torrent.
  /// The [savePath] parameter is the directory where the downloaded files will be saved.
  /// The [title] parameter is an optional title for the download (e.g., audiobook name).
  ///
  /// Returns the download ID for tracking progress.
  ///
  /// Throws [TorrentFailure] if the download cannot be started.
  Future<String> downloadSequential(String magnetUrl, String savePath,
      {String? title}) async {
    // Check initialization
    final logger = EnvironmentLogger();
    if (_db == null) {
      logger.w('AudiobookTorrentManager not initialized with database');
      // Continue without persistence, but log warning
    }

    String? downloadId;
    try {
      // Generate a unique download ID
      downloadId = DateTime.now().millisecondsSinceEpoch.toString();

      // Create progress controller for this download
      final progressController = StreamController<TorrentProgress>.broadcast();
      _progressControllers[downloadId] = progressController;

      // Parse magnet link
      final magnet = MagnetParser.parse(magnetUrl);
      if (magnet == null) {
        throw const TorrentFailure('Invalid magnet URL: failed to parse');
      }

      // Create download directory if it doesn't exist
      final downloadDir = Directory(savePath);
      if (!await downloadDir.exists()) {
        await downloadDir.create(recursive: true);
      }

      // Check metadata cache first
      var metadataBytes =
          await MetadataDownloader.loadFromCache(magnet.infoHashString);

      // Store metadata downloader and peers for later transfer
      MetadataDownloader? metadataDownloader;
      List<Peer>? metadataPeers;

      // If not in cache, download metadata
      if (metadataBytes == null) {
        // Create metadata downloader
        metadataDownloader = MetadataDownloader.fromMagnet(magnetUrl);
        _metadataDownloaders[downloadId] = metadataDownloader;
        final metadataListener = metadataDownloader.createListener();

        // Wait for metadata download with timeout
        final metadataCompleter = Completer<Uint8List>();
        metadataListener
          ..on<MetaDataDownloadProgress>((event) {
            // Emit metadata download progress
            final progress = TorrentProgress(
              progress: event.progress * 100,
              downloadSpeed: 0.0,
              uploadSpeed: 0.0,
              downloadedBytes: 0,
              totalBytes: 0,
              seeders: 0,
              leechers: 0,
              status: 'downloading_metadata',
            );
            progressController.add(progress);
          })
          ..on<MetaDataDownloadComplete>((event) {
            if (!metadataCompleter.isCompleted) {
              metadataCompleter.complete(Uint8List.fromList(event.data));
            }
          });

        // Start metadata download
        safeUnawaited(metadataDownloader.startDownload());

        // Wait for metadata with timeout (180 seconds)
        try {
          metadataBytes = await metadataCompleter.future.timeout(
            const Duration(seconds: 180),
            onTimeout: () {
              throw const TorrentFailure(
                  'Metadata download timeout after 180 seconds');
            },
          );
          // Save peers before cleanup
          metadataPeers = metadataDownloader.activePeers.toList();
        } finally {
          // Clean up metadata downloader
          _metadataDownloaders.remove(downloadId);
          await metadataDownloader.stop();
        }
      }

      // Parse torrent from metadata
      final msg = decode(metadataBytes);
      final torrentMap = <String, dynamic>{'info': msg};
      final torrentModel = parseTorrentFileContent(torrentMap);

      if (torrentModel == null) {
        throw const TorrentFailure('Failed to parse torrent from metadata');
      }

      // Create torrent task with web seeds and acceptable sources from magnet link
      final task = TorrentTask.newTask(
        torrentModel,
        savePath,
        true, // sequential download for audiobooks
        magnet.webSeeds.isNotEmpty ? magnet.webSeeds : null,
        magnet.acceptableSources.isNotEmpty ? magnet.acceptableSources : null,
      );
      _activeTasks[downloadId] = task;

      // Log task creation
      final logger = EnvironmentLogger()
        ..i('Created TorrentTask for download $downloadId')
        ..i('Torrent name: ${torrentModel.name}')
        ..i('Torrent size: ${torrentModel.length} bytes')
        ..i('Save path: $savePath');

      // Apply selected files from magnet link (BEP 0053) if specified
      if (magnet.selectedFileIndices != null &&
          magnet.selectedFileIndices!.isNotEmpty) {
        task.applySelectedFiles(magnet.selectedFileIndices!);
      }

      // Store metadata
      final metadata = {
        'magnetUrl': magnetUrl,
        'savePath': savePath,
        'infoHash': magnet.infoHashString,
        'startedAt': DateTime.now().toIso8601String(),
        if (title != null && title.isNotEmpty) 'title': title,
      };
      _downloadMetadata[downloadId] = metadata;

      // Persist to database
      await _saveDownloadMetadata(downloadId, metadata);

      // Set up event listeners
      // downloadId is guaranteed to be non-null at this point
      final finalDownloadId = downloadId;
      task.events.on<TaskStarted>((event) {
        logger
          ..i('Task started for download $finalDownloadId')
          ..i('Connecting to peers...');
        _updateProgress(finalDownloadId, task, progressController);
      });

      task.events.on<AllComplete>((event) {
        logger.i('Download completed for download $finalDownloadId');
        final progress = TorrentProgress(
          progress: 100.0,
          downloadSpeed: 0.0,
          uploadSpeed: 0.0,
          downloadedBytes: task.downloaded ?? 0,
          totalBytes: task.metaInfo.length,
          seeders: task.seederNumber,
          leechers: task.allPeersNumber - task.seederNumber,
          status: 'completed',
        );
        progressController
          ..add(progress)
          ..close();
        _progressControllers.remove(downloadId);
        _activeTasks.remove(downloadId);
        _downloadMetadata.remove(downloadId);

        // Remove from database when completed
        safeUnawaited(_removeDownloadMetadata(finalDownloadId));
      });

      // Error handling is done in _updateProgress and try-catch blocks

      // Start the download
      await task.start();

      // Transfer peers from metadata downloader if available
      if (metadataPeers != null && metadataPeers.isNotEmpty) {
        for (final peer in metadataPeers) {
          try {
            task.addPeer(peer.address, PeerSource.manual, type: peer.type);
          } on Exception {
            // Skip peers that can't be transferred
          }
        }
      }

      // Add trackers from magnet link to TorrentTask
      if (magnet.trackers.isNotEmpty) {
        final infoHashBuffer = Uint8List.fromList(
          List.generate(magnet.infoHashString.length ~/ 2, (i) {
            final s = magnet.infoHashString.substring(i * 2, i * 2 + 2);
            return int.parse(s, radix: 16);
          }),
        );
        for (var trackerUrl in magnet.trackers) {
          try {
            task.startAnnounceUrl(trackerUrl, infoHashBuffer);
          } on Exception {
            // Skip trackers that can't be added
          }
        }
      }

      // Add DHT nodes from torrent model
      torrentModel.nodes.forEach(task.addDHTNode);

      return downloadId;
    } on Exception catch (e) {
      // Clean up on error
      if (downloadId != null) {
        final controller = _progressControllers[downloadId];
        if (controller != null) {
          safeUnawaited(controller.close());
        }
        _progressControllers.remove(downloadId);
        final task = _activeTasks[downloadId];
        if (task != null) {
          safeUnawaited(task.dispose());
        }
        _activeTasks.remove(downloadId);
        final metadata = _metadataDownloaders[downloadId];
        if (metadata != null) {
          safeUnawaited(metadata.stop());
        }
        _metadataDownloaders.remove(downloadId);
        _downloadMetadata.remove(downloadId);
      }
      throw TorrentFailure('Failed to start download: ${e.toString()}');
    }
  }

  /// Starts a sequential torrent download from a torrent file.
  ///
  /// This method initiates a torrent download using the provided torrent file path
  /// and saves it to the specified path. It creates a progress stream
  /// to monitor download progress.
  ///
  /// The [torrentFilePath] parameter is the path to the .torrent file.
  /// The [savePath] parameter is the directory where the downloaded files will be saved.
  /// The [title] parameter is an optional title for the download (e.g., audiobook name).
  ///
  /// Returns the download ID for tracking progress.
  ///
  /// Throws [TorrentFailure] if the download cannot be started.
  Future<String> downloadFromTorrentFile(
      String torrentFilePath, String savePath,
      {String? title}) async {
    final torrentFile = File(torrentFilePath);
    if (!await torrentFile.exists()) {
      throw const TorrentFailure('Torrent file not found');
    }

    final torrentBytes = await torrentFile.readAsBytes();
    return downloadFromTorrentBytes(torrentBytes, savePath, title: title);
  }

  /// Starts a sequential torrent download from torrent file bytes.
  ///
  /// This method initiates a torrent download using the provided torrent file bytes
  /// and saves it to the specified path. It creates a progress stream
  /// to monitor download progress.
  ///
  /// The [torrentBytes] parameter is the content of the .torrent file.
  /// The [savePath] parameter is the directory where the downloaded files will be saved.
  /// The [title] parameter is an optional title for the download (e.g., audiobook name).
  ///
  /// Returns the download ID for tracking progress.
  ///
  /// Throws [TorrentFailure] if the download cannot be started.
  Future<String> downloadFromTorrentBytes(
      List<int> torrentBytes, String savePath,
      {String? title}) async {
    // Check initialization
    final logger = EnvironmentLogger();
    if (_db == null) {
      logger.w('AudiobookTorrentManager not initialized with database');
      // Continue without persistence, but log warning
    }

    String? downloadId;
    try {
      // Generate a unique download ID
      downloadId = DateTime.now().millisecondsSinceEpoch.toString();

      // Create progress controller for this download
      final progressController = StreamController<TorrentProgress>.broadcast();
      _progressControllers[downloadId] = progressController;

      // Parse torrent file
      final torrentMap = decode(Uint8List.fromList(torrentBytes));
      final torrentModel = parseTorrentFileContent(torrentMap);

      if (torrentModel == null) {
        throw const TorrentFailure('Failed to parse torrent file');
      }

      // Create download directory if it doesn't exist
      final downloadDir = Directory(savePath);
      if (!await downloadDir.exists()) {
        await downloadDir.create(recursive: true);
      }

      // Create torrent task with sequential download
      final task = TorrentTask.newTask(
        torrentModel,
        savePath,
        true, // sequential download for audiobooks
      );
      _activeTasks[downloadId] = task;

      // Log task creation
      final logger = EnvironmentLogger()
        ..i('Created TorrentTask for download $downloadId')
        ..i('Torrent name: ${torrentModel.name}')
        ..i('Torrent size: ${torrentModel.length} bytes')
        ..i('Save path: $savePath');

      // Store metadata
      final metadata = <String, dynamic>{
        'savePath': savePath,
        'startedAt': DateTime.now().toIso8601String(),
      };
      if (title != null && title.isNotEmpty) {
        metadata['title'] = title;
      }
      _downloadMetadata[downloadId] = metadata;

      // Persist to database
      await _saveDownloadMetadata(downloadId, metadata);

      // Set up event listeners
      final finalDownloadId = downloadId;
      task.events.on<TaskStarted>((event) {
        logger.i('Task started for download $finalDownloadId');
        _updateProgress(finalDownloadId, task, progressController);
      });

      task.events.on<AllComplete>((event) {
        logger.i('Download completed for download $finalDownloadId');
        final progress = TorrentProgress(
          progress: 100.0,
          downloadSpeed: 0.0,
          uploadSpeed: 0.0,
          downloadedBytes: task.downloaded ?? 0,
          totalBytes: task.metaInfo.length,
          seeders: task.seederNumber,
          leechers: task.allPeersNumber - task.seederNumber,
          status: 'completed',
        );
        progressController
          ..add(progress)
          ..close();
        _progressControllers.remove(downloadId);
        _activeTasks.remove(downloadId);
        _downloadMetadata.remove(downloadId);

        // Remove from database when completed
        safeUnawaited(_removeDownloadMetadata(finalDownloadId));
      });

      // Error handling is done in _updateProgress and try-catch blocks

      // Start the download
      await task.start();

      return downloadId;
    } on Exception catch (e) {
      // Clean up on error
      if (downloadId != null) {
        final controller = _progressControllers[downloadId];
        if (controller != null) {
          safeUnawaited(controller.close());
        }
        _progressControllers.remove(downloadId);
        final task = _activeTasks[downloadId];
        if (task != null) {
          safeUnawaited(task.dispose());
        }
        _activeTasks.remove(downloadId);
        _downloadMetadata.remove(downloadId);
      }
      throw TorrentFailure('Failed to start download: ${e.toString()}');
    }
  }

  void _updateProgress(String downloadId, TorrentTask task,
      StreamController<TorrentProgress> progressController) {
    // Track last log time to avoid spamming logs
    DateTime? lastLogTime;

    // Update progress periodically
    Timer.periodic(const Duration(seconds: 1), (timer) async {
      if (!_activeTasks.containsKey(downloadId)) {
        timer.cancel();
        return;
      }

      try {
        // Check if task is stopped (paused)
        final isPaused = _downloadMetadata[downloadId]?['pausedAt'] != null;

        String status;
        if (task.progress >= 1.0) {
          status = 'completed';
        } else if (isPaused) {
          status = 'paused';
        } else {
          status = 'downloading';
        }

        final progress = TorrentProgress(
          progress: task.progress * 100,
          downloadSpeed: isPaused ? 0.0 : task.currentDownloadSpeed,
          uploadSpeed: isPaused ? 0.0 : task.uploadSpeed,
          downloadedBytes: task.downloaded ?? 0,
          totalBytes: task.metaInfo.length,
          seeders: task.seederNumber,
          leechers: task.allPeersNumber - task.seederNumber,
          status: status,
        );

        progressController.add(progress);

        // Update notification
        final metadata = _downloadMetadata[downloadId];
        final title = metadata?['title'] as String? ?? task.metaInfo.name;
        safeUnawaited(_notificationService.showDownloadProgress(
          downloadId,
          title,
          progress.progress,
          progress.downloadSpeed,
          status,
        ));

        // Log progress every 10 seconds
        final now = DateTime.now();
        if (lastLogTime == null ||
            now.difference(lastLogTime!) >= const Duration(seconds: 10)) {
          EnvironmentLogger()
            ..i('Download progress: $downloadId')
            ..i('  Progress: ${progress.progress.toStringAsFixed(1)}%')
            ..i('  Downloaded: ${_formatBytesForLog(progress.downloadedBytes)} / ${_formatBytesForLog(progress.totalBytes)}')
            ..i('  Speed: ${_formatBytesForLog(progress.downloadSpeed.toInt())}/s')
            ..i('  Seeders: ${progress.seeders}, Leechers: ${progress.leechers}')
            ..i('  Status: $status');
          lastLogTime = now;
        }

        if (task.progress >= 1.0) {
          timer.cancel();
          await progressController.close();
          // Show completion notification before removing metadata
          final metadata = _downloadMetadata[downloadId];
          _progressControllers.remove(downloadId);
          _activeTasks.remove(downloadId);
          _downloadMetadata.remove(downloadId);
          // Show completion notification and cancel after delay
          final title = metadata?['title'] as String? ?? task.metaInfo.name;
          safeUnawaited(_notificationService.showDownloadProgress(
            downloadId,
            title,
            100.0,
            0.0,
            'completed',
          ));
          // Cancel notification after 3 seconds
          Future.delayed(const Duration(seconds: 3), () {
            safeUnawaited(
                _notificationService.cancelDownloadNotification(downloadId));
          });
        }
      } on Exception catch (e) {
        // Task might be disposed or in error state
        try {
          final errorProgress = TorrentProgress(
            progress: task.progress * 100,
            downloadSpeed: 0.0,
            uploadSpeed: 0.0,
            downloadedBytes: task.downloaded ?? 0,
            totalBytes: task.metaInfo.length,
            seeders: 0,
            leechers: 0,
            status: 'error: ${e.toString()}',
          );
          progressController.add(errorProgress);
        } on Exception {
          // If we can't even create error progress, task is completely dead
        }
        timer.cancel();
      }
    });
  }

  /// Pauses an active torrent download.
  ///
  /// This method pauses the specified download, allowing it to be resumed later.
  /// The download progress is preserved.
  ///
  /// The [downloadId] parameter is the unique identifier of the download to pause.
  ///
  /// Throws [TorrentFailure] if the download cannot be paused.
  Future<void> pauseDownload(String downloadId) async {
    try {
      final task = _activeTasks[downloadId];
      if (task == null) {
        throw const TorrentFailure('Download not found');
      }

      await task.stop();
      _downloadMetadata[downloadId]?['pausedAt'] =
          DateTime.now().toIso8601String();

      // Update database
      await _saveDownloadMetadata(downloadId, _downloadMetadata[downloadId]!);

      // Update progress status to paused
      final controller = _progressControllers[downloadId];
      if (controller != null && !controller.isClosed) {
        final progress = TorrentProgress(
          progress: task.progress * 100,
          downloadSpeed: 0.0,
          uploadSpeed: 0.0,
          downloadedBytes: task.downloaded ?? 0,
          totalBytes: task.metaInfo.length,
          seeders: task.seederNumber,
          leechers: task.allPeersNumber - task.seederNumber,
          status: 'paused',
        );
        controller.add(progress);
      }
    } on Exception catch (e) {
      throw TorrentFailure('Failed to pause download: ${e.toString()}');
    }
  }

  /// Resumes a paused torrent download.
  ///
  /// This method resumes a previously paused download, continuing from where
  /// it left off.
  ///
  /// The [downloadId] parameter is the unique identifier of the download to resume.
  ///
  /// Throws [TorrentFailure] if the download cannot be resumed.
  Future<void> resumeDownload(String downloadId) async {
    try {
      final task = _activeTasks[downloadId];
      final metadata = _downloadMetadata[downloadId];
      if (task == null || metadata == null) {
        throw const TorrentFailure('Download not found');
      }

      // Remove pausedAt flag
      metadata.remove('pausedAt');

      // Update database
      await _saveDownloadMetadata(downloadId, metadata);

      // Resume the task
      await task.start();

      // Update progress status to downloading
      final controller = _progressControllers[downloadId];
      if (controller != null && !controller.isClosed) {
        final progress = TorrentProgress(
          progress: task.progress * 100,
          downloadSpeed: task.currentDownloadSpeed,
          uploadSpeed: task.uploadSpeed,
          downloadedBytes: task.downloaded ?? 0,
          totalBytes: task.metaInfo.length,
          seeders: task.seederNumber,
          leechers: task.allPeersNumber - task.seederNumber,
          status: 'downloading',
        );
        controller.add(progress);
      }
    } on Exception catch (e) {
      throw TorrentFailure('Failed to resume download: ${e.toString()}');
    }
  }

  /// Removes a torrent download from the manager.
  ///
  /// This method stops the download and removes it from the active downloads list.
  /// The downloaded files are not deleted from disk.
  ///
  /// The [downloadId] parameter is the unique identifier of the download to remove.
  ///
  /// Throws [TorrentFailure] if the download cannot be removed.
  Future<void> removeDownload(String downloadId) async {
    try {
      final task = _activeTasks[downloadId];
      if (task != null) {
        await task.dispose();
        _activeTasks.remove(downloadId);
      }

      // Cancel notification
      await _notificationService.cancelDownloadNotification(downloadId);

      final metadata = _metadataDownloaders[downloadId];
      if (metadata != null) {
        await metadata.stop();
        _metadataDownloaders.remove(downloadId);
      }

      await _progressControllers[downloadId]?.close();
      _progressControllers.remove(downloadId);
      _downloadMetadata.remove(downloadId);
    } on Exception catch (e) {
      throw TorrentFailure('Failed to remove download: ${e.toString()}');
    }
  }

  /// Gets the progress stream for a specific download.
  ///
  /// This method returns a stream that emits progress updates for the specified
  /// download. The stream can be listened to for real-time progress updates.
  ///
  /// The [downloadId] parameter is the unique identifier of the download.
  ///
  /// Returns a [Stream] of [TorrentProgress] updates.
  ///
  /// Throws [TorrentFailure] if the download is not found.
  Stream<TorrentProgress> getProgressStream(String downloadId) {
    final controller = _progressControllers[downloadId];
    if (controller == null) {
      throw const TorrentFailure('Download not found');
    }
    return controller.stream;
  }

  /// Gets a list of all active downloads.
  ///
  /// This method returns information about all currently active downloads,
  /// including their IDs, progress, and status.
  ///
  /// Returns a list of maps containing download information.
  ///
  /// Throws [TorrentFailure] if the active downloads cannot be retrieved.
  Future<List<Map<String, dynamic>>> getActiveDownloads() async {
    try {
      final downloads = <Map<String, dynamic>>[];

      for (final entry in _activeTasks.entries) {
        final downloadId = entry.key;
        final task = entry.value;
        final metadata = _downloadMetadata[downloadId] ?? {};

        // Use title from metadata if available, otherwise use torrent name
        final displayName = metadata['title'] as String? ?? task.metaInfo.name;

        downloads.add({
          'id': downloadId,
          'name': displayName,
          'title': metadata['title'],
          'progress': task.progress * 100,
          'downloadSpeed': task.currentDownloadSpeed,
          'uploadSpeed': task.uploadSpeed,
          'downloadedBytes': task.downloaded ?? 0,
          'totalBytes': task.metaInfo.length,
          'seeders': task.seederNumber,
          'leechers': task.allPeersNumber - task.seederNumber,
          'status': task.progress >= 1.0 ? 'completed' : 'downloading',
          'startedAt': metadata['startedAt'],
          'pausedAt': metadata['pausedAt'],
          'savePath': metadata['savePath'],
        });
      }

      return downloads;
    } on Exception catch (e) {
      throw TorrentFailure('Failed to get active downloads: ${e.toString()}');
    }
  }

  /// Shuts down the torrent manager and cleans up resources.
  ///
  /// This method closes all active download streams and clears the
  /// progress controllers. It should be called when the application
  /// is closing or when torrent functionality is no longer needed.
  ///
  /// Throws [TorrentFailure] if shutdown fails.
  Future<void> shutdown() async {
    try {
      // Stop all metadata downloaders
      await Future.wait(_metadataDownloaders.values.map((metadata) async {
        try {
          await metadata.stop();
        } on Exception {
          // Ignore errors during shutdown
        }
      }));
      _metadataDownloaders.clear();

      // Close all progress controllers
      await Future.wait(
          _progressControllers.values.map((controller) => controller.close()));
      _progressControllers.clear();

      // Dispose all active torrent tasks
      await Future.wait(_activeTasks.values.map((task) => task.dispose()));
      _activeTasks.clear();

      _downloadMetadata.clear();
    } on Exception catch (e) {
      throw TorrentFailure(
          'Failed to shutdown torrent manager: ${e.toString()}');
    }
  }

  /// Saves download metadata to database.
  Future<void> _saveDownloadMetadata(
      String downloadId, Map<String, dynamic> metadata) async {
    if (_db == null) return;

    try {
      final store = AppDatabase().downloadsStore;
      await store.record(downloadId).put(_db!, metadata);
    } on Exception {
      // Ignore errors - persistence is optional
    }
  }

  /// Removes download metadata from database.
  Future<void> _removeDownloadMetadata(String downloadId) async {
    if (_db == null) return;

    try {
      final store = AppDatabase().downloadsStore;
      await store.record(downloadId).delete(_db!);
    } on Exception {
      // Ignore errors - persistence is optional
    }
  }

  /// Restores downloads from database on app startup.
  Future<void> _restoreDownloads() async {
    if (_db == null) return;

    try {
      final store = AppDatabase().downloadsStore;
      final records = await store.find(_db!);

      for (final record in records) {
        final downloadId = record.key;
        final metadata = record.value;

        // Check if save path still exists
        final savePath = metadata['savePath'] as String?;
        if (savePath == null) continue;

        final saveDir = Directory(savePath);
        if (!await saveDir.exists()) {
          // Download directory doesn't exist, remove from database
          await _removeDownloadMetadata(downloadId);
          continue;
        }

        // Restore metadata to memory
        _downloadMetadata[downloadId] = metadata;

        // Note: We don't actually restart the download here,
        // just restore the metadata. The user can manually resume if needed.
        // This is because TorrentTask cannot be restored from disk state.
      }
    } on Exception {
      // Ignore errors - restoration is optional
    }
  }

  /// Gets the default download directory for torrent files.
  ///
  /// This method returns the path to the directory where torrent files
  /// should be saved. If the directory doesn't exist, it will be created.
  ///
  /// Returns the path to the download directory as a string.
  static Future<String> getDownloadDirectory() async {
    final appDocDir = await getApplicationDocumentsDirectory();
    final downloadDir = Directory('${appDocDir.path}/downloads');
    if (!await downloadDir.exists()) {
      await downloadDir.create(recursive: true);
    }
    return downloadDir.path;
  }

  /// Formats bytes for logging purposes.
  String _formatBytesForLog(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) {
      return '${(bytes / 1024).toStringAsFixed(2)} KB';
    }
    if (bytes < 1024 * 1024 * 1024) {
      return '${(bytes / (1024 * 1024)).toStringAsFixed(2)} MB';
    }
    return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(2)} GB';
  }
}
