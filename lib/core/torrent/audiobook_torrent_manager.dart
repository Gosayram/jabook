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
import 'package:dio/dio.dart';
// ignore: unused_import
import 'package:dtorrent_common/dtorrent_common.dart'; // PeerSource is used in addPeer call
import 'package:dtorrent_parser/dtorrent_parser.dart';
import 'package:dtorrent_task_v2/dtorrent_task_v2.dart';
import 'package:jabook/core/data/local/database/app_database.dart';
import 'package:jabook/core/download/download_foreground_service.dart';
import 'package:jabook/core/infrastructure/background/download_background_service.dart';
import 'package:jabook/core/infrastructure/errors/failures.dart';
import 'package:jabook/core/infrastructure/logging/environment_logger.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:jabook/core/infrastructure/notifications/download_notification_service.dart';
import 'package:jabook/core/infrastructure/permissions/permission_service.dart';
import 'package:jabook/core/infrastructure/task_manager/task_manager.dart';
import 'package:jabook/core/library/audiobook_library_scanner.dart';
import 'package:jabook/core/library/folder_filter_service.dart';
import 'package:jabook/core/library/smart_scanner_service.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/utils/content_uri_service.dart';
import 'package:jabook/core/utils/network_utils.dart';
import 'package:jabook/core/utils/safe_async.dart';
import 'package:jabook/core/utils/storage_path_utils.dart';
import 'package:path/path.dart' as path;
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
/// torrent downloads for audiobook files.
///
/// Note: This class is no longer a singleton. Use [audiobookTorrentManagerProvider]
/// to get an instance via dependency injection.
class AudiobookTorrentManager {
  /// Creates a new AudiobookTorrentManager instance.
  ///
  /// Use [audiobookTorrentManagerProvider] to get an instance via dependency injection.
  AudiobookTorrentManager();

  /// Map of download progress controllers for active downloads.
  final Map<String, StreamController<TorrentProgress>> _progressControllers =
      {};

  /// Map of active torrent tasks.
  final Map<String, TorrentTask> _activeTasks = {};

  /// Map of download metadata.
  final Map<String, Map<String, dynamic>> _downloadMetadata = {};

  /// Map of metadata downloaders for active downloads.
  final Map<String, MetadataDownloader> _metadataDownloaders = {};

  /// Map of progress timers for active downloads.
  final Map<String, Timer> _progressTimers = {};

  /// Map of retry counts for network errors per download.
  final Map<String, int> _networkRetryCounts = {};

  /// Map of last error time per download for network error detection.
  final Map<String, DateTime> _lastNetworkErrorTime = {};

  /// Map of last successful download time per download.
  final Map<String, DateTime> _lastSuccessfulDownloadTime = {};

  /// Maximum number of retry attempts for network errors.
  static const int _maxNetworkRetries = 3;

  /// Minimum time without download before considering it a network error (seconds).
  static const int _networkErrorDetectionTimeSeconds = 120;

  /// Database instance for persisting download state.
  Database? _db;

  /// Notification service for download progress updates.
  final DownloadNotificationService _notificationService =
      DownloadNotificationService();

  /// Foreground service for keeping downloads alive in background.
  final DownloadForegroundService _foregroundService =
      DownloadForegroundService();

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

  /// Checks if the torrent manager is initialized with database.
  ///
  /// Returns true if database is initialized, false otherwise.
  bool get isInitialized => _db != null;

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
      {String? title, String? coverUrl}) async {
    // Check initialization
    final logger = EnvironmentLogger();
    if (!isInitialized) {
      logger
        ..w('AudiobookTorrentManager not initialized with database')
        ..d('Attempting to continue download without database persistence');
      // Continue without persistence, but log warning
      // Note: Downloads can still work, but state won't be persisted
      // However, we should still try to initialize if possible
    }
    logger
      ..i('=== downloadSequential CALLED ===')
      ..i('magnetUrl: ${magnetUrl.substring(0, magnetUrl.length > 50 ? 50 : magnetUrl.length)}...')
      ..i('savePath: $savePath')
      ..i('title: $title')
      ..i(
        'Starting download - save path: $savePath, isAppSpecific: ${PermissionService.isAppSpecificDirectory(savePath)}',
      );
    await StructuredLogger().log(
      level: 'info',
      subsystem: 'torrent',
      message: 'Starting download',
      extra: {
        'isAppSpecific': PermissionService.isAppSpecificDirectory(savePath),
        'title': title,
      }, // Don't include savePath and magnetUrl in extra to avoid redaction
    );

    // Check Wi-Fi only setting
    final networkUtils = NetworkUtils();
    final canDownload = await networkUtils.canDownload();
    if (!canDownload) {
      final connectionType = await networkUtils.getConnectionTypeDescription();
      throw TorrentFailure(
        'Download blocked: Wi-Fi only mode is enabled, but current connection is $connectionType',
      );
    }

    String? downloadId;
    try {
      // Generate a unique download ID
      downloadId = DateTime.now().millisecondsSinceEpoch.toString();

      // Create progress controller for this download
      final progressController = StreamController<TorrentProgress>.broadcast();
      _progressControllers[downloadId] = progressController;

      // Emit initial progress immediately so UI can show the download right away
      // This must be done before any long-running operations
      final initialProgress = TorrentProgress(
        progress: 0.0,
        downloadSpeed: 0.0,
        uploadSpeed: 0.0,
        downloadedBytes: 0,
        totalBytes: 0, // Will be updated after metadata download
        seeders: 0,
        leechers: 0,
        status: 'downloading_metadata',
      );
      if (!progressController.isClosed) {
        progressController.add(initialProgress);
      }

      // Parse magnet link
      final magnet = MagnetParser.parse(magnetUrl);
      if (magnet == null) {
        throw const TorrentFailure('Invalid magnet URL: failed to parse');
      }

      // Early permission check before creating directory
      final isAppSpecific = PermissionService.isAppSpecificDirectory(savePath);
      final isContentUri = StoragePathUtils.isContentUri(savePath);

      if (!isAppSpecific && !isContentUri) {
        // For user-selected directory (not SAF), check actual write capability
        logger.i(
            'Checking write capability for non-app-specific directory: $savePath');
        await StructuredLogger().log(
          level: 'info',
          subsystem: 'torrent',
          message: 'Checking write capability for non-app-specific directory',
        );

        final permissionService = PermissionService();
        // Check actual write capability, not just permissions
        final canWrite = await permissionService.canWriteToPath(savePath);
        if (!canWrite) {
          logger.w('Cannot write to path, checking permissions for: $savePath');
          await StructuredLogger().log(
            level: 'warning',
            subsystem: 'torrent',
            message: 'Cannot write to path, checking permissions',
          );

          // If write test failed, check if we have permissions at all
          final hasPermission = await permissionService.canWriteToStorage();
          if (!hasPermission) {
            // Request permission
            final granted = await permissionService.requestStoragePermission();
            if (!granted) {
              // Check again after opening settings (user might have granted permission)
              final canWriteAfterRequest =
                  await permissionService.canWriteToPath(savePath);
              if (!canWriteAfterRequest) {
                logger.e(
                    'Cannot write to selected directory - permission denied: $savePath');
                await StructuredLogger().log(
                  level: 'error',
                  subsystem: 'torrent',
                  message:
                      'Cannot write to selected directory - permission denied',
                );
                throw const TorrentFailure(
                  'Permission denied: Cannot write to download directory. '
                  'Please grant "Allow access to manage all files" permission in system settings '
                  '(Settings > Apps > Jabook > Permissions > Files and media > Allow access to manage all files).',
                );
              }
            } else {
              // Permission granted, test write again
              final canWriteAfterGrant =
                  await permissionService.canWriteToPath(savePath);
              if (!canWriteAfterGrant) {
                logger.e(
                    'Permission granted but still cannot write to directory: $savePath');
                await StructuredLogger().log(
                  level: 'error',
                  subsystem: 'torrent',
                  message:
                      'Permission granted but still cannot write to directory',
                );
                throw const TorrentFailure(
                  'Permission granted but cannot write to directory. '
                  'This may be a system-level issue. Please try restarting the app or selecting a different folder.',
                );
              }
            }
          } else {
            // Has permission but cannot write - this is unusual
            logger.e('Has permission but cannot write to directory: $savePath');
            await StructuredLogger().log(
              level: 'error',
              subsystem: 'torrent',
              message: 'Has permission but cannot write to directory',
            );
            throw const TorrentFailure(
              'Cannot write to directory despite having permissions. '
              'This may be a system-level issue. Please try restarting the app or selecting a different folder.',
            );
          }
        } else {
          logger.i('Write capability verified for directory: $savePath');
        }
      } else {
        if (isAppSpecific) {
          logger.i(
              'Using app-specific directory, no permission check needed: $savePath');
          await StructuredLogger().log(
            level: 'info',
            subsystem: 'torrent',
            message: 'Using app-specific directory, no permission check needed',
          );
        } else if (isContentUri) {
          logger.i(
              'Using content URI (SAF), no permission check needed: $savePath');
          await StructuredLogger().log(
            level: 'info',
            subsystem: 'torrent',
            message: 'Using content URI (SAF), no permission check needed',
          );
        }
      }

      // Create download directory if it doesn't exist
      final downloadDir = Directory(savePath);
      if (!await downloadDir.exists()) {
        try {
          logger.d('Creating download directory: $savePath');
          await downloadDir.create(recursive: true);
          logger.d('Successfully created download directory: $savePath');
        } on Exception catch (e) {
          logger.e(
            'Failed to create download directory: $savePath',
            error: e,
          );
          // Check if error is related to permissions
          final errorStr = e.toString().toLowerCase();
          if (errorStr.contains('permission') ||
              errorStr.contains('access') ||
              errorStr.contains('denied')) {
            if (isAppSpecific) {
              throw TorrentFailure(
                'Cannot create download directory: ${e.toString()}. '
                'This may be a system-level issue. Please try restarting the app.',
              );
            } else {
              throw const TorrentFailure(
                'Permission denied: Cannot create download directory. '
                'Please grant storage permission in app settings or select a different folder.',
              );
            }
          }
          throw TorrentFailure(
            'Failed to create download directory: ${e.toString()}',
          );
        }
      } else {
        logger.d('Download directory already exists: $savePath');
        // Verify we can write to the directory
        try {
          final testFile = File('${downloadDir.path}/.test_write');
          await testFile.writeAsString('test');
          await testFile.delete();
        } on Exception catch (e) {
          logger.e(
            'Cannot write to download directory: $savePath',
            error: e,
          );
          final errorStr = e.toString().toLowerCase();
          if (errorStr.contains('permission') ||
              errorStr.contains('access') ||
              errorStr.contains('denied')) {
            if (isAppSpecific) {
              throw TorrentFailure(
                'Cannot write to download directory: ${e.toString()}. '
                'This may be a system-level issue. Please try restarting the app.',
              );
            } else {
              throw const TorrentFailure(
                'Permission denied: Cannot write to download directory. '
                'Please grant MANAGE_EXTERNAL_STORAGE permission in app settings '
                '(Android 11+) or storage permission (Android 10 and below).',
              );
            }
          }
          throw TorrentFailure(
            'Cannot write to download directory: ${e.toString()}',
          );
        }
      }

      // Check metadata cache first
      var metadataBytes =
          await MetadataDownloader.loadFromCache(magnet.infoHashString);

      // Store peers for later transfer
      List<Peer>? metadataPeers;

      // If not in cache, download metadata
      if (metadataBytes == null) {
        // Use TaskManager with MEDIUM priority to limit concurrent metadata downloads
        // This provides better control, monitoring, and priority management
        // Store magnetUrl in local variable to avoid null-safety issues in closure
        // Create a new String from the parameter to ensure type safety in closure
        final magnetUrlValue = magnetUrl;
        // Store downloadId in local variable to avoid null-safety issues in closure
        // downloadId is guaranteed to be non-null at this point (assigned on line 219)
        final downloadIdValue = downloadId;
        // Create a function that uses the local variable
        Future<({Uint8List bytes, List<Peer> peers})> downloadMetadata() async {
          // Create metadata downloader
          // magnetUrlValue is a new String created from non-nullable parameter, so it's safe to use
          final downloader = MetadataDownloader.fromMagnet(magnetUrlValue);
          _metadataDownloaders[downloadIdValue] = downloader;
          final metadataListener = downloader.createListener();

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
              if (!progressController.isClosed) {
                progressController.add(progress);
              }
            })
            ..on<MetaDataDownloadComplete>((event) {
              if (!metadataCompleter.isCompleted) {
                metadataCompleter.complete(Uint8List.fromList(event.data));
              }
            });

          // Start metadata download
          safeUnawaited(downloader.startDownload());

          try {
            // Wait for metadata with timeout (180 seconds)
            final result = await metadataCompleter.future.timeout(
              const Duration(seconds: 180),
              onTimeout: () {
                throw const TorrentFailure(
                    'Metadata download timeout after 180 seconds');
              },
            );

            // Save peers before cleanup
            final peers = downloader.activePeers.toList();

            return (bytes: result, peers: peers);
          } finally {
            // Clean up metadata downloader
            _metadataDownloaders.remove(downloadIdValue);
            await downloader.stop();
          }
        }

        final downloadResult = await TaskManager.instance.submit<
            ({
              Uint8List bytes,
              List<Peer> peers,
            })>(
          priority: TaskPriority.medium,
          task: downloadMetadata,
        );

        metadataBytes = downloadResult.bytes;
        metadataPeers = downloadResult.peers;
      }

      // Parse torrent from metadata
      final msg = decode(metadataBytes);
      final torrentMap = <String, dynamic>{'info': msg};
      final torrentModel = parseTorrentFileContent(torrentMap);

      if (torrentModel == null) {
        throw const TorrentFailure('Failed to parse torrent from metadata');
      }

      // Store metadata IMMEDIATELY so it appears in downloads list right away
      // This must be done BEFORE creating the task to ensure it's available when getActiveDownloads() is called
      final metadata = {
        'magnetUrl': magnetUrl,
        'savePath': savePath,
        'infoHash': magnet.infoHashString,
        'startedAt': DateTime.now().toIso8601String(),
        'status': 'downloading_metadata', // Initial status
        'progress': 0.0,
        'downloadedBytes': 0,
        'totalBytes': 0,
        if (title != null && title.isNotEmpty) 'title': title,
        if (coverUrl != null && coverUrl.isNotEmpty) 'coverUrl': coverUrl,
      };
      _downloadMetadata[downloadId] = metadata;
      logger.i(
        'downloadSequential: Saved metadata to _downloadMetadata for downloadId: $downloadId, metadata keys: ${metadata.keys.toList()}',
      );

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
      EnvironmentLogger()
        ..i('Created TorrentTask for download $downloadId')
        ..i('Torrent name: ${torrentModel.name}')
        ..i('Torrent size: ${torrentModel.length} bytes')
        ..i('Save path: $savePath');

      // Apply selected files from magnet link (BEP 0053) if specified
      if (magnet.selectedFileIndices != null &&
          magnet.selectedFileIndices!.isNotEmpty) {
        task.applySelectedFiles(magnet.selectedFileIndices!);
      }

      // Persist to database BEFORE adding to active tasks
      // This ensures metadata is saved even if something goes wrong
      await _saveDownloadMetadata(downloadId, metadata);
      logger.d(
        'Saved download metadata to database for downloadId: $downloadId',
      );

      // Show initial notification
      final displayTitle = title ?? 'Download';
      safeUnawaited(_notificationService.showDownloadProgress(
        downloadId,
        displayTitle,
        0.0,
        0.0,
        'downloading_metadata',
      ));

      // Register background service for monitoring downloads
      safeUnawaited(_registerBackgroundService());

      // Start foreground service to keep downloads alive in background
      safeUnawaited(_foregroundService.startService());

      // Set up event listeners
      // downloadId is guaranteed to be non-null at this point
      final finalDownloadId = downloadId;
      task.events.on<TaskStarted>((event) {
        logger
          ..i('Task started for download $finalDownloadId')
          ..i('Connecting to peers...');

        safeUnawaited(StructuredLogger().log(
          level: 'info',
          subsystem: 'torrent',
          message: 'Torrent task started',
          extra: {
            'downloadId': finalDownloadId,
            'torrentName': task.metaInfo.name,
            'torrentSize': task.metaInfo.length,
          },
        ));

        // Log the actual save path that TorrentTask is using
        final metadata = _downloadMetadata[finalDownloadId];
        if (metadata != null) {
          final savePath = metadata['savePath'] as String?;
          if (savePath != null) {
            // Use EnvironmentLogger for paths (not StructuredLogger) to avoid redaction
            logger
              ..i('Torrent task save path: $savePath')
              ..i('Torrent task save path (absolute): ${Directory(savePath).absolute.path}');

            // Verify the directory exists and is accessible
            safeUnawaited(_verifySaveDirectory(finalDownloadId, savePath));
          }
        }

        _updateProgress(finalDownloadId, task, progressController);
      });

      task.events.on<AllComplete>((event) {
        logger.i('Download completed for download $finalDownloadId');
        safeUnawaited(StructuredLogger().log(
          level: 'info',
          subsystem: 'torrent',
          message: 'Download completed',
          extra: {
            'downloadId': finalDownloadId,
            'downloadedBytes': task.downloaded ?? 0,
            'totalBytes': task.metaInfo.length,
          },
        ));
        // Cancel progress timer
        final timer = _progressTimers[finalDownloadId];
        if (timer != null) {
          timer.cancel();
          _progressTimers.remove(finalDownloadId);
        }
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
        if (!progressController.isClosed) {
          progressController
            ..add(progress)
            ..close();
        }
        _progressControllers.remove(downloadId);
        final taskToDispose = _activeTasks.remove(downloadId);
        if (taskToDispose != null) {
          safeUnawaited(taskToDispose.dispose());
        }

        // Update notification with completed status
        final completedNotificationMetadata2 =
            _downloadMetadata[finalDownloadId];
        final displayTitle2 =
            completedNotificationMetadata2?['title'] as String? ??
                task.metaInfo.name;
        safeUnawaited(_notificationService.showDownloadProgress(
          finalDownloadId,
          displayTitle2,
          100.0,
          0.0,
          'completed',
        ));

        // Update notification with completed status
        final completedNotificationMetadata =
            _downloadMetadata[finalDownloadId];
        final displayTitle =
            completedNotificationMetadata?['title'] as String? ??
                task.metaInfo.name;
        safeUnawaited(_notificationService.showDownloadProgress(
          finalDownloadId,
          displayTitle,
          100.0,
          0.0,
          'completed',
        ));

        // Check if all downloads are completed and stop foreground service
        safeUnawaited(_checkAndStopForegroundService());

        // Update metadata with completed status instead of removing
        final completedMetadata = _downloadMetadata[finalDownloadId];
        if (completedMetadata != null) {
          metadata['status'] = 'completed';
          metadata['progress'] = 100.0;
          metadata['completedAt'] = DateTime.now().toIso8601String();

          // Verify that files were actually saved
          final savePath = metadata['savePath'] as String?;
          if (savePath != null) {
            // TorrentTask may save files in a subdirectory with torrent name
            // Check multiple possible locations
            final torrentName = task.metaInfo.name;
            final possiblePaths = [
              savePath, // Original path
              '$savePath/$torrentName', // Subdirectory with torrent name
              Directory(savePath).parent.path, // Parent directory
            ];

            logger.i('Checking for downloaded files in multiple locations:');
            for (final checkPath in possiblePaths) {
              logger.i('  - $checkPath');
            }

            // Check all possible paths
            safeUnawaited(_verifyDownloadedFilesInMultiplePaths(
              finalDownloadId,
              possiblePaths,
            ));

            // Download cover image if available
            final coverUrl = metadata['coverUrl'] as String?;
            if (coverUrl != null && coverUrl.isNotEmpty) {
              // Try all possible paths for cover download
              for (final checkPath in possiblePaths) {
                if (checkPath.isNotEmpty) {
                  safeUnawaited(
                      _downloadCoverImage(coverUrl, checkPath, torrentName));
                  break; // Only download once
                }
              }
            }
          }

          // Trigger library scan after download completes
          safeUnawaited(_triggerLibraryScan(metadata['savePath'] as String?));

          // Save updated metadata to database
          safeUnawaited(_saveDownloadMetadata(finalDownloadId, metadata));
          logger.d(
            'Updated download metadata to completed status for downloadId: $finalDownloadId',
          );
          // Remove from memory after a delay to allow UI to update
          Future.delayed(const Duration(seconds: 30), () {
            _downloadMetadata.remove(finalDownloadId);
            // Remove from database after delay
            safeUnawaited(_removeDownloadMetadata(finalDownloadId));
            logger.d(
              'Removed completed download metadata after delay for downloadId: $finalDownloadId',
            );
          });
        } else {
          // If metadata is missing, just remove from database
          safeUnawaited(_removeDownloadMetadata(finalDownloadId));
        }
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
      // Log error with full context
      logger.e('Failed to start download: ${e.toString()}', error: e);
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'torrent',
        message: 'Failed to start download',
        extra: {
          'downloadId': downloadId,
          'error': e.toString(),
          'errorType': e.runtimeType.toString(),
        },
      );

      // Clean up on error
      if (downloadId != null) {
        try {
          // Cancel progress timer if exists
          final timer = _progressTimers[downloadId];
          if (timer != null) {
            timer.cancel();
            _progressTimers.remove(downloadId);
          }
          // Close progress controller safely
          final controller = _progressControllers[downloadId];
          if (controller != null && !controller.isClosed) {
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

          // Update metadata with error status before removing
          final errorMetadata = _downloadMetadata[downloadId];
          if (errorMetadata != null) {
            errorMetadata['status'] = 'error: ${e.toString()}';
            errorMetadata['error'] = e.toString();
            errorMetadata['failedAt'] = DateTime.now().toIso8601String();
            safeUnawaited(_saveDownloadMetadata(downloadId, errorMetadata));

            // Show error notification
            final displayTitle =
                errorMetadata['title'] as String? ?? 'Download';
            safeUnawaited(_notificationService.showDownloadProgress(
              downloadId,
              displayTitle,
              0.0,
              0.0,
              'error: ${e.toString()}',
            ));
          }
          _downloadMetadata.remove(downloadId);
        } on Exception catch (cleanupError) {
          // Log cleanup error but don't throw - original error is more important
          EnvironmentLogger().w('Error during cleanup: $cleanupError');
        }
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
      {String? title, String? coverUrl}) async {
    final torrentFile = File(torrentFilePath);
    if (!await torrentFile.exists()) {
      throw const TorrentFailure('Torrent file not found');
    }

    final torrentBytes = await torrentFile.readAsBytes();
    return downloadFromTorrentBytes(torrentBytes, savePath,
        title: title, coverUrl: coverUrl);
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
      {String? title, String? coverUrl}) async {
    // Check initialization
    final logger = EnvironmentLogger();
    if (!isInitialized) {
      logger.w('AudiobookTorrentManager not initialized with database');
      // Continue without persistence, but log warning
      // Note: Downloads can still work, but state won't be persisted
    }

    // Check Wi-Fi only setting
    final networkUtils = NetworkUtils();
    final canDownload = await networkUtils.canDownload();
    if (!canDownload) {
      final connectionType = await networkUtils.getConnectionTypeDescription();
      throw TorrentFailure(
        'Download blocked: Wi-Fi only mode is enabled, but current connection is $connectionType',
      );
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

      // Emit initial progress immediately so UI can show the download right away
      // This must be done before any long-running operations
      final initialProgress = TorrentProgress(
        progress: 0.0,
        downloadSpeed: 0.0,
        uploadSpeed: 0.0,
        downloadedBytes: 0,
        totalBytes: torrentModel.length,
        seeders: 0,
        leechers: 0,
        status: 'downloading',
      );
      if (!progressController.isClosed) {
        progressController.add(initialProgress);
      }

      // Check if path is app-specific directory (no permission needed on Android 11+)
      final isAppSpecific = PermissionService.isAppSpecificDirectory(savePath);
      final isContentUri = StoragePathUtils.isContentUri(savePath);

      if (isAppSpecific) {
        logger.d(
          'Using app-specific directory: $savePath (no permission needed on Android 11+)',
        );
      } else if (isContentUri) {
        logger.d(
          'Using content URI (SAF): $savePath (no permission check needed)',
        );
      } else {
        // For user-selected directory (not SAF), check actual write capability
        final permissionService = PermissionService();
        final canWrite = await permissionService.canWriteToPath(savePath);
        if (!canWrite) {
          logger.w(
            'Cannot write to path, checking permissions before creating directory',
          );

          // If write test failed, check if we have permissions at all
          final hasPermission = await permissionService.canWriteToStorage();
          if (!hasPermission) {
            final granted = await permissionService.requestStoragePermission();
            if (!granted) {
              // Check again after opening settings (user might have granted permission)
              final canWriteAfterRequest =
                  await permissionService.canWriteToPath(savePath);
              if (!canWriteAfterRequest) {
                logger.e(
                  'Cannot write to storage - permission denied after request',
                );
                throw const TorrentFailure(
                  'Permission denied: Cannot write to download directory. '
                  'Please grant "Allow access to manage all files" permission in system settings '
                  '(Settings > Apps > Jabook > Permissions > Files and media > Allow access to manage all files).',
                );
              }
            } else {
              // Permission granted, test write again
              final canWriteAfterGrant =
                  await permissionService.canWriteToPath(savePath);
              if (!canWriteAfterGrant) {
                logger.e(
                  'Permission granted but still cannot write to directory',
                );
                throw const TorrentFailure(
                  'Permission granted but cannot write to directory. '
                  'This may be a system-level issue. Please try restarting the app or selecting a different folder.',
                );
              }
            }
          } else {
            // Has permission but cannot write - this is unusual
            logger.e(
              'Has permission but cannot write to directory',
            );
            throw const TorrentFailure(
              'Cannot write to directory despite having permissions. '
              'This may be a system-level issue. Please try restarting the app or selecting a different folder.',
            );
          }
        } else {
          logger.d('Write capability verified for directory: $savePath');
        }
      }

      // Create download directory if it doesn't exist
      logger.i(
          'Creating download directory: $savePath (downloadId: $downloadId)');
      await StructuredLogger().log(
        level: 'info',
        subsystem: 'torrent',
        message: 'Creating download directory',
        extra: {
          'downloadId': downloadId
        }, // Don't include path in extra to avoid redaction
      );

      // Check if path is content URI (SAF)
      final isContentUriPath = StoragePathUtils.isContentUri(savePath);
      if (isContentUriPath) {
        // For content URIs, check access via ContentResolver
        // The directory should already exist (user selected it via SAF)
        // We just need to verify access
        try {
          final contentUriService = ContentUriService();
          final hasAccess = await contentUriService.checkUriAccess(savePath);
          if (!hasAccess) {
            logger.e('No access to content URI: $savePath');
            await StructuredLogger().log(
              level: 'error',
              subsystem: 'torrent',
              message: 'No access to content URI',
              extra: {
                'downloadId': downloadId,
                'error': 'No access to content URI',
              },
            );
            throw const TorrentFailure(
              'No access to selected folder. Please grant permission in the file picker.',
            );
          }
          logger.d('Content URI access verified: $savePath');
        } on TorrentFailure {
          rethrow;
        } on Exception catch (e) {
          logger.e(
            'Error checking content URI access: $savePath',
            error: e,
          );
          await StructuredLogger().log(
            level: 'error',
            subsystem: 'torrent',
            message: 'Error checking content URI access',
            extra: {
              'downloadId': downloadId,
              'error': e.toString(),
            },
          );
          throw TorrentFailure(
            'Failed to verify access to selected folder: ${e.toString()}',
          );
        }
      } else {
        // For file paths, use Directory API
        final downloadDir = Directory(savePath);
        if (!await downloadDir.exists()) {
          try {
            logger.d('Creating download directory: $savePath');
            await downloadDir.create(recursive: true);
            logger
              ..d('Successfully created download directory: $savePath')
              ..i('Successfully created download directory: $savePath (downloadId: $downloadId)');
            await StructuredLogger().log(
              level: 'info',
              subsystem: 'torrent',
              message: 'Successfully created download directory',
              extra: {
                'downloadId': downloadId
              }, // Don't include path in extra to avoid redaction
            );
          } on Exception catch (e) {
            logger.e(
              'Failed to create download directory: $savePath',
              error: e,
            );
            await StructuredLogger().log(
              level: 'error',
              subsystem: 'torrent',
              message: 'Failed to create download directory',
              extra: {
                'path': savePath,
                'downloadId': downloadId,
                'error': e.toString(),
              },
            );
            // Check if error is related to permissions
            final errorStr = e.toString().toLowerCase();
            if (errorStr.contains('permission') ||
                errorStr.contains('access') ||
                errorStr.contains('denied')) {
              if (isAppSpecific) {
                throw TorrentFailure(
                  'Cannot create download directory: ${e.toString()}. '
                  'This may be a system-level issue. Please try restarting the app.',
                );
              } else {
                throw const TorrentFailure(
                  'Permission denied: Cannot create download directory. '
                  'Please grant storage permission in app settings or select a different folder.',
                );
              }
            }
            throw TorrentFailure(
              'Failed to create download directory: ${e.toString()}',
            );
          }
        } else {
          logger
            ..d('Download directory already exists: $savePath')
            ..i('Download directory already exists: $savePath (downloadId: $downloadId)');
          await StructuredLogger().log(
            level: 'info',
            subsystem: 'torrent',
            message: 'Download directory already exists',
            extra: {
              'downloadId': downloadId
            }, // Don't include path in extra to avoid redaction
          );
          // Verify we can write to the directory (only for file paths, not content URIs)
          if (!isContentUriPath) {
            try {
              final testFile = File('$savePath/.test_write');
              await testFile.writeAsString('test');
              await testFile.delete();
            } on Exception catch (e) {
              logger
                ..e(
                  'Cannot write to download directory: $savePath',
                  error: e,
                )
                ..e('Cannot write to download directory: $savePath (downloadId: $downloadId)',
                    error: e);
              await StructuredLogger().log(
                level: 'error',
                subsystem: 'torrent',
                message: 'Cannot write to download directory',
                extra: {
                  'downloadId': downloadId,
                  'error': e.toString(),
                }, // Don't include path in extra to avoid redaction
              );
              final errorStr = e.toString().toLowerCase();
              if (errorStr.contains('permission') ||
                  errorStr.contains('access') ||
                  errorStr.contains('denied')) {
                if (isAppSpecific) {
                  throw TorrentFailure(
                    'Cannot write to download directory: ${e.toString()}. '
                    'This may be a system-level issue. Please try restarting the app.',
                  );
                } else {
                  throw const TorrentFailure(
                    'Permission denied: Cannot write to download directory. '
                    'Please grant MANAGE_EXTERNAL_STORAGE permission in app settings '
                    '(Android 11+) or storage permission (Android 10 and below).',
                  );
                }
              }
              throw TorrentFailure(
                'Cannot write to download directory: ${e.toString()}',
              );
            }
          }
        }
      }

      // Store metadata IMMEDIATELY so it appears in downloads list right away
      // This must be done BEFORE creating the task to ensure it's available when getActiveDownloads() is called
      final metadata = <String, dynamic>{
        'savePath': savePath,
        'startedAt': DateTime.now().toIso8601String(),
        'torrentBytes': torrentBytes, // Store torrent bytes for restoration
        'status': 'downloading', // Initial status
        'progress': 0.0,
        'downloadedBytes': 0,
        'totalBytes': torrentModel.length,
      };
      if (title != null && title.isNotEmpty) {
        metadata['title'] = title;
      }
      if (coverUrl != null && coverUrl.isNotEmpty) {
        metadata['coverUrl'] = coverUrl;
      }
      _downloadMetadata[downloadId] = metadata;
      logger.i(
        'downloadFromTorrentBytes: Saved metadata to _downloadMetadata for downloadId: $downloadId, metadata keys: ${metadata.keys.toList()}',
      );

      // Create torrent task with sequential download
      final task = TorrentTask.newTask(
        torrentModel,
        savePath,
        true, // sequential download for audiobooks
      );
      _activeTasks[downloadId] = task;

      // Log task creation
      EnvironmentLogger()
        ..i('Created TorrentTask for download $downloadId')
        ..i('Torrent name: ${torrentModel.name}')
        ..i('Torrent size: ${torrentModel.length} bytes')
        ..i('Save path: $savePath');

      logger.i('Created TorrentTask - save path: $savePath');
      await StructuredLogger().log(
        level: 'info',
        subsystem: 'torrent',
        message: 'Created TorrentTask',
        extra: {
          'downloadId': downloadId,
          'torrentName': torrentModel.name,
          'torrentSize': torrentModel.length,
        }, // Don't include savePath in extra to avoid redaction
      );

      // Persist to database BEFORE adding to active tasks
      // This ensures metadata is saved even if something goes wrong
      await _saveDownloadMetadata(downloadId, metadata);
      logger.d(
        'Saved download metadata to database for downloadId: $downloadId',
      );

      // Show initial notification
      final displayTitle = title ?? torrentModel.name;
      safeUnawaited(_notificationService.showDownloadProgress(
        downloadId,
        displayTitle,
        0.0,
        0.0,
        'downloading',
      ));
      await StructuredLogger().log(
        level: 'info',
        subsystem: 'torrent',
        message: 'Saved download metadata to database',
        extra: {'downloadId': downloadId},
      );

      // Register background service for monitoring downloads
      safeUnawaited(_registerBackgroundService());

      // Start foreground service to keep downloads alive in background
      safeUnawaited(_foregroundService.startService());

      // Set up event listeners
      final finalDownloadId = downloadId;
      task.events.on<TaskStarted>((event) {
        logger.i('Task started for download $finalDownloadId');
        safeUnawaited(StructuredLogger().log(
          level: 'info',
          subsystem: 'torrent',
          message: 'Torrent task started',
          extra: {'downloadId': finalDownloadId},
        ));
        _updateProgress(finalDownloadId, task, progressController);
      });

      task.events.on<AllComplete>((event) {
        logger.i('Download completed for download $finalDownloadId');
        safeUnawaited(StructuredLogger().log(
          level: 'info',
          subsystem: 'torrent',
          message: 'Download completed',
          extra: {'downloadId': finalDownloadId},
        ));
        // Cancel progress timer
        final timer = _progressTimers[finalDownloadId];
        if (timer != null) {
          timer.cancel();
          _progressTimers.remove(finalDownloadId);
        }
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
        if (!progressController.isClosed) {
          progressController
            ..add(progress)
            ..close();
        }
        _progressControllers.remove(downloadId);
        final taskToDispose = _activeTasks.remove(downloadId);
        if (taskToDispose != null) {
          safeUnawaited(taskToDispose.dispose());
        }

        // Update notification with completed status
        final completedNotificationMetadata3 =
            _downloadMetadata[finalDownloadId];
        final displayTitle3 =
            completedNotificationMetadata3?['title'] as String? ??
                task.metaInfo.name;
        safeUnawaited(_notificationService.showDownloadProgress(
          finalDownloadId,
          displayTitle3,
          100.0,
          0.0,
          'completed',
        ));

        // Update metadata with completed status instead of removing
        final metadata = _downloadMetadata[finalDownloadId];
        if (metadata != null) {
          metadata['status'] = 'completed';
          metadata['progress'] = 100.0;
          metadata['completedAt'] = DateTime.now().toIso8601String();

          // Verify that files were actually saved
          final savePath = metadata['savePath'] as String?;
          if (savePath != null) {
            // TorrentTask may save files in a subdirectory with torrent name
            // Check multiple possible locations
            final torrentName = task.metaInfo.name;
            final possiblePaths = [
              savePath, // Original path
              '$savePath/$torrentName', // Subdirectory with torrent name
              Directory(savePath).parent.path, // Parent directory
            ];

            logger.i('Checking for downloaded files in multiple locations:');
            for (final checkPath in possiblePaths) {
              logger.i('  - $checkPath');
            }

            // Check all possible paths
            safeUnawaited(_verifyDownloadedFilesInMultiplePaths(
              finalDownloadId,
              possiblePaths,
            ));

            // Download cover image if available
            final coverUrl = metadata['coverUrl'] as String?;
            if (coverUrl != null && coverUrl.isNotEmpty) {
              // Try all possible paths for cover download
              for (final checkPath in possiblePaths) {
                if (checkPath.isNotEmpty) {
                  safeUnawaited(
                      _downloadCoverImage(coverUrl, checkPath, torrentName));
                  break; // Only download once
                }
              }
            }
          }

          // Trigger library scan after download completes
          safeUnawaited(_triggerLibraryScan(metadata['savePath'] as String?));

          // Save updated metadata to database
          safeUnawaited(_saveDownloadMetadata(finalDownloadId, metadata));
          logger.d(
            'Updated download metadata to completed status for downloadId: $finalDownloadId',
          );
          // Remove from memory after a delay to allow UI to update
          Future.delayed(const Duration(seconds: 30), () {
            _downloadMetadata.remove(finalDownloadId);
            // Remove from database after delay
            safeUnawaited(_removeDownloadMetadata(finalDownloadId));
            logger.d(
              'Removed completed download metadata after delay for downloadId: $finalDownloadId',
            );
          });
        } else {
          // If metadata is missing, just remove from database
          safeUnawaited(_removeDownloadMetadata(finalDownloadId));
        }
      });

      // Error handling is done in _updateProgress and try-catch blocks

      // Start the download
      logger.i('Starting torrent download - save path: $savePath');
      await StructuredLogger().log(
        level: 'info',
        subsystem: 'torrent',
        message: 'Starting torrent download',
        extra: {
          'downloadId': downloadId,
          'torrentName': torrentModel.name,
          'torrentSize': torrentModel.length,
        }, // Don't include savePath in extra to avoid redaction
      );

      await task.start();

      return downloadId;
    } on Exception catch (e) {
      // Clean up on error
      if (downloadId != null) {
        try {
          // Cancel progress timer if exists
          final timer = _progressTimers[downloadId];
          if (timer != null) {
            timer.cancel();
            _progressTimers.remove(downloadId);
          }
          // Close progress controller safely
          final controller = _progressControllers[downloadId];
          if (controller != null && !controller.isClosed) {
            safeUnawaited(controller.close());
          }
          _progressControllers.remove(downloadId);
          final task = _activeTasks[downloadId];
          if (task != null) {
            safeUnawaited(task.dispose());
          }
          _activeTasks.remove(downloadId);
          _downloadMetadata.remove(downloadId);
        } on Exception catch (cleanupError) {
          // Log cleanup error but don't throw - original error is more important
          EnvironmentLogger().w('Error during cleanup: $cleanupError');
        }
      }
      throw TorrentFailure('Failed to start download: ${e.toString()}');
    }
  }

  /// Cleans up retry tracking data for a download.
  void _cleanupRetryData(String downloadId) {
    _networkRetryCounts.remove(downloadId);
    _lastNetworkErrorTime.remove(downloadId);
    _lastSuccessfulDownloadTime.remove(downloadId);
  }

  /// Checks if an exception represents a network error.
  ///
  /// Returns true if the error is likely a network-related issue
  /// that could be retried.
  bool _isNetworkError(Exception e) {
    final errorString = e.toString().toLowerCase();
    return errorString.contains('connection') ||
        errorString.contains('network') ||
        errorString.contains('timeout') ||
        errorString.contains('socket') ||
        errorString.contains('host') ||
        errorString.contains('dns') ||
        errorString.contains('unreachable');
  }

  /// Attempts to retry a download after a network error.
  ///
  /// This method will retry the download up to [_maxNetworkRetries] times
  /// with exponential backoff between attempts.
  Future<void> _retryDownloadOnNetworkError(String downloadId) async {
    final retryCount = _networkRetryCounts[downloadId] ?? 0;
    if (retryCount >= _maxNetworkRetries) {
      EnvironmentLogger().w(
        'Download $downloadId: Max retry attempts ($_maxNetworkRetries) reached, giving up',
      );
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'torrent',
        message: 'Max network retry attempts reached',
        extra: {
          'downloadId': downloadId,
          'maxRetries': _maxNetworkRetries,
        },
      );
      return;
    }

    final metadata = _downloadMetadata[downloadId];
    if (metadata == null) {
      EnvironmentLogger().w(
        'Download $downloadId: Cannot retry - metadata not found',
      );
      return;
    }

    // Check if download is paused - don't retry paused downloads
    if (metadata['pausedAt'] != null) {
      EnvironmentLogger().d(
        'Download $downloadId: Skipping retry - download is paused',
      );
      return;
    }

    // Calculate exponential backoff delay
    final baseDelaySeconds = 5 * (1 << retryCount); // 5, 10, 20 seconds
    final jitterMs = DateTime.now().microsecondsSinceEpoch % 2000;
    final delayMs = (baseDelaySeconds * 1000) + jitterMs;

    EnvironmentLogger().i(
      'Download $downloadId: Retrying after network error (attempt ${retryCount + 1}/$_maxNetworkRetries, delay: ${(delayMs / 1000).toStringAsFixed(1)}s)',
    );

    await StructuredLogger().log(
      level: 'warning',
      subsystem: 'torrent',
      message: 'Retrying download after network error',
      extra: {
        'downloadId': downloadId,
        'retryAttempt': retryCount + 1,
        'maxRetries': _maxNetworkRetries,
        'delayMs': delayMs,
      },
    );

    // Wait before retry
    await Future.delayed(Duration(milliseconds: delayMs));

    // Increment retry count
    _networkRetryCounts[downloadId] = retryCount + 1;

    try {
      // Try to resume the download
      final task = _activeTasks[downloadId];
      if (task != null) {
        // Task exists, try to restart it
        try {
          await task.stop();
          await Future.delayed(const Duration(milliseconds: 500));
          await task.start();
          EnvironmentLogger().i(
            'Download $downloadId: Successfully restarted after network error',
          );
          // Reset retry count on success
          _networkRetryCounts.remove(downloadId);
          _lastNetworkErrorTime.remove(downloadId);
          _lastSuccessfulDownloadTime[downloadId] = DateTime.now();
        } on Exception catch (e) {
          EnvironmentLogger().w(
            'Download $downloadId: Failed to restart task: $e',
          );
          // Will retry again on next error if under limit
        }
      } else {
        // Task doesn't exist, try to restore from metadata
        try {
          await resumeRestoredDownload(downloadId);
          EnvironmentLogger().i(
            'Download $downloadId: Successfully restored after network error',
          );
          // Reset retry count on success
          _networkRetryCounts.remove(downloadId);
          _lastNetworkErrorTime.remove(downloadId);
          _lastSuccessfulDownloadTime[downloadId] = DateTime.now();
        } on Exception catch (e) {
          EnvironmentLogger().w(
            'Download $downloadId: Failed to restore download: $e',
          );
          // Will retry again on next error if under limit
        }
      }
    } on Exception catch (e) {
      EnvironmentLogger().e(
        'Download $downloadId: Error during retry: $e',
      );
      // Will retry again on next error if under limit
    }
  }

  void _updateProgress(String downloadId, TorrentTask task,
      StreamController<TorrentProgress> progressController) {
    // Track last log time to avoid spamming logs
    DateTime? lastLogTime;
    // Track last save time to avoid spamming database
    DateTime? lastSaveTime;
    // Track progress for hang detection (99% hang)
    double? lastProgress;
    DateTime? lastProgressChangeTime;
    DateTime? hangDetectionStartTime;

    // Update progress periodically
    final timer = Timer.periodic(const Duration(seconds: 1), (timer) async {
      if (!_activeTasks.containsKey(downloadId)) {
        timer.cancel();
        _progressTimers.remove(downloadId);
        return;
      }

      try {
        // Check if task is stopped (paused)
        final isPaused = _downloadMetadata[downloadId]?['pausedAt'] != null;

        // Check for hang at 99% (progress >= 99.0% and not changing for 20 seconds)
        // Also check if download speed is 0 and progress is high
        final currentProgress = task.progress * 100;
        // Convert KB/s to B/s for comparison
        final currentSpeed = task.currentDownloadSpeed * 1024;
        final now = DateTime.now();

        // Check if download is actually completed (task.progress >= 1.0)
        if (task.progress >= 1.0) {
          logger.i(
            'Download $downloadId completed (task.progress >= 1.0)',
          );
          timer.cancel();
          _progressTimers.remove(downloadId);
          if (!progressController.isClosed) {
            final completedProgress = TorrentProgress(
              progress: 100.0,
              downloadSpeed: 0.0,
              uploadSpeed: 0.0,
              downloadedBytes: task.downloaded ?? task.metaInfo.length,
              totalBytes: task.metaInfo.length,
              seeders: task.seederNumber,
              leechers: task.allPeersNumber - task.seederNumber,
              status: 'completed',
            );
            progressController.add(completedProgress);
            safeUnawaited(progressController.close());
          }
          _progressControllers.remove(downloadId);
          final taskToDispose = _activeTasks.remove(downloadId);
          if (taskToDispose != null) {
            safeUnawaited(taskToDispose.dispose());
          }

          // Clean up retry tracking data
          _cleanupRetryData(downloadId);

          // Check if all downloads are completed and stop foreground service
          safeUnawaited(_checkAndStopForegroundService());

          // Update metadata
          final metadata = _downloadMetadata[downloadId];
          if (metadata != null) {
            metadata['status'] = 'completed';
            metadata['progress'] = 100.0;
            metadata['completedAt'] = now.toIso8601String();
            safeUnawaited(_saveDownloadMetadata(downloadId, metadata));
            // Remove after delay
            Future.delayed(const Duration(seconds: 30), () {
              _downloadMetadata.remove(downloadId);
              safeUnawaited(_removeDownloadMetadata(downloadId));
            });
          }
          return;
        }

        // Check for hang at 99%+ (progress >= 99.0% and not changing for 20 seconds)
        // Also consider it hung if speed is 0 and progress is >= 99%
        final isHighProgress =
            currentProgress >= 99.0 && currentProgress < 100.0;
        final isNoSpeed = currentSpeed <= 0.0;
        final isStuck = isHighProgress &&
            (isNoSpeed ||
                (lastProgress != null &&
                    (currentProgress - lastProgress!).abs() < 0.1));

        if (isStuck) {
          // Progress is stuck at 99%+ with no speed or no change
          if (lastProgressChangeTime == null) {
            lastProgressChangeTime = now;
            hangDetectionStartTime = now;
            logger.d(
              'Download $downloadId detected as stuck at ${currentProgress.toStringAsFixed(1)}% (speed: ${currentSpeed.toStringAsFixed(1)} B/s)',
            );
          } else if (now.difference(lastProgressChangeTime!) >=
              const Duration(seconds: 20)) {
            // Progress stuck at 99%+ for 20 seconds - consider it completed
            logger.w(
              'Download $downloadId stuck at ${currentProgress.toStringAsFixed(1)}% for 20+ seconds (speed: ${currentSpeed.toStringAsFixed(1)} B/s), marking as completed',
            );
            // Force completion
            timer.cancel();
            _progressTimers.remove(downloadId);
            if (!progressController.isClosed) {
              final completedProgress = TorrentProgress(
                progress: 100.0,
                downloadSpeed: 0.0,
                uploadSpeed: 0.0,
                downloadedBytes: task.downloaded ?? task.metaInfo.length,
                totalBytes: task.metaInfo.length,
                seeders: task.seederNumber,
                leechers: task.allPeersNumber - task.seederNumber,
                status: 'completed',
              );
              progressController.add(completedProgress);
              safeUnawaited(progressController.close());
            }
            _progressControllers.remove(downloadId);
            _activeTasks.remove(downloadId);

            // Clean up retry tracking data
            _cleanupRetryData(downloadId);

            // Check if all downloads are completed and stop foreground service
            safeUnawaited(_checkAndStopForegroundService());

            // Update metadata
            final metadata = _downloadMetadata[downloadId];
            if (metadata != null) {
              metadata['status'] = 'completed';
              metadata['progress'] = 100.0;
              metadata['completedAt'] = now.toIso8601String();
              safeUnawaited(_saveDownloadMetadata(downloadId, metadata));
              // Remove after delay
              Future.delayed(const Duration(seconds: 30), () {
                _downloadMetadata.remove(downloadId);
                safeUnawaited(_removeDownloadMetadata(downloadId));
              });
            }
            return;
          }
        } else {
          // Progress changed or speed is non-zero, reset hang detection
          if (lastProgressChangeTime != null) {
            logger.d(
              'Download $downloadId progress changed or speed resumed, resetting hang detection',
            );
          }
          lastProgressChangeTime = null;
          hangDetectionStartTime = null;
        }
        lastProgress = currentProgress;

        // Check for timeout: if progress >= 99% and stuck for 3 minutes, force completion
        if (hangDetectionStartTime != null &&
            now.difference(hangDetectionStartTime!) >=
                const Duration(minutes: 3)) {
          logger.w(
            'Download $downloadId stuck at 99%+ for 3 minutes, forcing completion',
          );
          timer.cancel();
          _progressTimers.remove(downloadId);
          if (!progressController.isClosed) {
            final completedProgress = TorrentProgress(
              progress: 100.0,
              downloadSpeed: 0.0,
              uploadSpeed: 0.0,
              downloadedBytes: task.downloaded ?? task.metaInfo.length,
              totalBytes: task.metaInfo.length,
              seeders: task.seederNumber,
              leechers: task.allPeersNumber - task.seederNumber,
              status: 'completed',
            );
            progressController.add(completedProgress);
            safeUnawaited(progressController.close());
          }
          _progressControllers.remove(downloadId);
          final taskToDispose = _activeTasks.remove(downloadId);
          if (taskToDispose != null) {
            safeUnawaited(taskToDispose.dispose());
          }

          // Clean up retry tracking data
          _cleanupRetryData(downloadId);

          // Check if all downloads are completed and stop foreground service
          safeUnawaited(_checkAndStopForegroundService());

          // Update metadata
          final metadata = _downloadMetadata[downloadId];
          if (metadata != null) {
            metadata['status'] = 'completed';
            metadata['progress'] = 100.0;
            metadata['completedAt'] = now.toIso8601String();
            safeUnawaited(_saveDownloadMetadata(downloadId, metadata));
            // Remove after delay
            Future.delayed(const Duration(seconds: 30), () {
              _downloadMetadata.remove(downloadId);
              safeUnawaited(_removeDownloadMetadata(downloadId));
            });
          }
          return;
        }

        String status;
        if (task.progress >= 1.0) {
          status = 'completed';
        } else if (isPaused) {
          status = 'paused';
        } else {
          status = 'downloading';
        }

        // Log raw speed value for debugging (first time only)
        if (lastLogTime == null) {
          logger.d(
            'Raw download speed from TorrentTask: ${task.currentDownloadSpeed} (type: ${task.currentDownloadSpeed.runtimeType})',
          );
        }

        // TorrentTask returns speed in KB/s, not B/s
        // Convert to bytes per second for consistent formatting
        // All speeds from TorrentTask are in KB/s, so always multiply by 1024
        final rawSpeed = task.currentDownloadSpeed;
        final downloadSpeed = isPaused ? 0.0 : rawSpeed * 1024;

        // Get metadata early for network error detection
        final metadata = _downloadMetadata[downloadId];

        // Track successful downloads for network error detection
        if (downloadSpeed > 0 && !isPaused) {
          _lastSuccessfulDownloadTime[downloadId] = now;
          // Reset retry count on successful download
          if (_networkRetryCounts.containsKey(downloadId)) {
            _networkRetryCounts.remove(downloadId);
            _lastNetworkErrorTime.remove(downloadId);
          }
        }

        // Detect network errors by long periods without download
        // (no speed, no peers, not paused, not completed)
        if (!isPaused &&
            downloadSpeed <= 0 &&
            task.seederNumber == 0 &&
            task.allPeersNumber == 0 &&
            currentProgress < 99.0) {
          final lastSuccess = _lastSuccessfulDownloadTime[downloadId];
          if (lastSuccess != null) {
            final timeSinceLastSuccess = now.difference(lastSuccess).inSeconds;
            if (timeSinceLastSuccess >= _networkErrorDetectionTimeSeconds) {
              // Potential network error detected
              final lastErrorTime = _lastNetworkErrorTime[downloadId];
              if (lastErrorTime == null ||
                  now.difference(lastErrorTime).inSeconds >=
                      _networkErrorDetectionTimeSeconds) {
                EnvironmentLogger().w(
                  'Download $downloadId: Potential network error detected (no download for ${timeSinceLastSuccess}s, no peers)',
                );
                _lastNetworkErrorTime[downloadId] = now;
                // Trigger retry asynchronously
                safeUnawaited(_retryDownloadOnNetworkError(downloadId));
              }
            }
          } else {
            // No successful download yet, but no peers - might be network issue
            final downloadStartTime = metadata?['startedAt'] as String?;
            if (downloadStartTime != null) {
              try {
                final startTime = DateTime.parse(downloadStartTime);
                final timeSinceStart = now.difference(startTime).inSeconds;
                if (timeSinceStart >= _networkErrorDetectionTimeSeconds) {
                  final lastErrorTime = _lastNetworkErrorTime[downloadId];
                  if (lastErrorTime == null ||
                      now.difference(lastErrorTime).inSeconds >=
                          _networkErrorDetectionTimeSeconds) {
                    EnvironmentLogger().w(
                      'Download $downloadId: Potential network error detected (no download for ${timeSinceStart}s since start, no peers)',
                    );
                    _lastNetworkErrorTime[downloadId] = now;
                    // Trigger retry asynchronously
                    safeUnawaited(_retryDownloadOnNetworkError(downloadId));
                  }
                }
              } on Exception {
                // Ignore parse errors
              }
            }
          }
        }

        final progress = TorrentProgress(
          progress: task.progress * 100,
          downloadSpeed: downloadSpeed,
          uploadSpeed: isPaused ? 0.0 : task.uploadSpeed,
          downloadedBytes: task.downloaded ?? 0,
          totalBytes: task.metaInfo.length,
          seeders: task.seederNumber,
          leechers: task.allPeersNumber - task.seederNumber,
          status: status,
        );

        // Check if controller is closed before adding
        if (!progressController.isClosed) {
          progressController.add(progress);
        }

        // Update notification - always sync with current status
        // Use only DownloadNotificationService for individual download notifications
        // DownloadForegroundService is only used to keep service alive, not for notifications
        final title = metadata?['title'] as String? ?? task.metaInfo.name;
        safeUnawaited(_notificationService.showDownloadProgress(
          downloadId,
          title,
          progress.progress,
          progress.downloadSpeed,
          status,
        ));

        // Log status changes for debugging
        if (lastLogTime == null ||
            now.difference(lastLogTime!) >= const Duration(seconds: 10)) {
          await StructuredLogger().log(
            level: 'debug',
            subsystem: 'torrent',
            message: 'Download progress update',
            extra: {
              'downloadId': downloadId,
              'progress': progress.progress,
              'status': status,
              'speed': progress.downloadSpeed,
              'seeders': progress.seeders,
              'leechers': progress.leechers,
            },
          );
        }

        // Note: Removed foreground service notification update to avoid duplicate notifications
        // DownloadForegroundService is only used to keep the service alive in background
        // Individual download notifications are handled by DownloadNotificationService

        // Save progress and status to metadata every 5 seconds
        if (lastSaveTime == null ||
            now.difference(lastSaveTime!) >= const Duration(seconds: 5)) {
          if (metadata != null) {
            metadata['progress'] = progress.progress;
            metadata['status'] = status;
            metadata['downloadedBytes'] = progress.downloadedBytes;
            metadata['totalBytes'] = progress.totalBytes;
            metadata['lastUpdated'] = now.toIso8601String();
            safeUnawaited(_saveDownloadMetadata(downloadId, metadata));
          }
          lastSaveTime = now;
        }

        // Log progress every 10 seconds
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

        // Note: task.progress >= 1.0 check is now handled earlier in the hang detection logic
      } on Exception catch (e) {
        // Task might be disposed or in error state
        final isNetworkErr = _isNetworkError(e);
        final retryCount = _networkRetryCounts[downloadId] ?? 0;

        EnvironmentLogger().w(
          'Download $downloadId: Error in progress update: $e (isNetworkError: $isNetworkErr, retryCount: $retryCount)',
        );

        await StructuredLogger().log(
          level: 'warning',
          subsystem: 'torrent',
          message: 'Error in download progress update',
          extra: {
            'downloadId': downloadId,
            'error': e.toString(),
            'isNetworkError': isNetworkErr,
            'retryCount': retryCount,
          },
        );

        try {
          final errorStatus = isNetworkErr && retryCount < _maxNetworkRetries
              ? 'retrying: ${e.toString()}'
              : 'error: ${e.toString()}';
          final errorProgress = TorrentProgress(
            progress: task.progress * 100,
            downloadSpeed: 0.0,
            uploadSpeed: 0.0,
            downloadedBytes: task.downloaded ?? 0,
            totalBytes: task.metaInfo.length,
            seeders: 0,
            leechers: 0,
            status: errorStatus,
          );
          if (!progressController.isClosed) {
            progressController.add(errorProgress);
          }

          // Update notification with error status
          final metadata = _downloadMetadata[downloadId];
          final displayTitle =
              metadata?['title'] as String? ?? task.metaInfo.name;
          safeUnawaited(_notificationService.showDownloadProgress(
            downloadId,
            displayTitle,
            task.progress * 100,
            0.0,
            errorStatus,
          ));

          // Update metadata with error status
          if (metadata != null) {
            metadata['status'] = errorStatus;
            metadata['error'] = e.toString();
            metadata['lastUpdated'] = DateTime.now().toIso8601String();
            safeUnawaited(_saveDownloadMetadata(downloadId, metadata));
          }

          await StructuredLogger().log(
            level: 'error',
            subsystem: 'torrent',
            message: 'Download error in progress update',
            extra: {
              'downloadId': downloadId,
              'error': e.toString(),
              'isNetworkError': isNetworkErr,
              'retryCount': retryCount,
              'progress': task.progress * 100,
            },
          );
        } on Exception {
          // If we can't even create error progress, task is completely dead
        }

        // If it's a network error and we haven't exceeded retry limit, try to retry
        if (isNetworkErr && retryCount < _maxNetworkRetries) {
          _lastNetworkErrorTime[downloadId] = DateTime.now();
          // Don't cancel timer immediately - let retry attempt happen
          // Retry will be handled asynchronously
          safeUnawaited(_retryDownloadOnNetworkError(downloadId));
        } else {
          // Not a network error or max retries reached - cancel timer
          timer.cancel();
          _progressTimers.remove(downloadId);

          // Final error notification if max retries reached
          if (isNetworkErr && retryCount >= _maxNetworkRetries) {
            final metadata = _downloadMetadata[downloadId];
            final displayTitle =
                metadata?['title'] as String? ?? task.metaInfo.name;
            safeUnawaited(_notificationService.showDownloadProgress(
              downloadId,
              displayTitle,
              task.progress * 100,
              0.0,
              'error: Max retries reached',
            ));
          }
        }
      }
    });
    // Store timer in Map for proper cleanup
    _progressTimers[downloadId] = timer;
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

      // Timer will continue running but will show paused status
      // We don't cancel it here to allow progress updates even when paused
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

      // Update notification with paused status
      final metadata = _downloadMetadata[downloadId];
      final displayTitle = metadata?['title'] as String? ?? task.metaInfo.name;
      safeUnawaited(_notificationService.showDownloadProgress(
        downloadId,
        displayTitle,
        task.progress * 100,
        0.0,
        'paused',
      ));

      // Log pause action
      EnvironmentLogger().i('Download $downloadId paused');
      await StructuredLogger().log(
        level: 'info',
        subsystem: 'torrent',
        message: 'Download paused',
        extra: {
          'downloadId': downloadId,
          'progress': task.progress * 100,
        },
      );
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
      // Convert KB/s to B/s for TorrentProgress
      final downloadSpeedBps = task.currentDownloadSpeed * 1024;
      final controller = _progressControllers[downloadId];
      if (controller != null && !controller.isClosed) {
        final progress = TorrentProgress(
          progress: task.progress * 100,
          downloadSpeed: downloadSpeedBps,
          uploadSpeed: task.uploadSpeed,
          downloadedBytes: task.downloaded ?? 0,
          totalBytes: task.metaInfo.length,
          seeders: task.seederNumber,
          leechers: task.allPeersNumber - task.seederNumber,
          status: 'downloading',
        );
        controller.add(progress);
      }

      // Update notification with downloading status
      final resumeMetadata = _downloadMetadata[downloadId];
      final displayTitle =
          resumeMetadata?['title'] as String? ?? task.metaInfo.name;
      safeUnawaited(_notificationService.showDownloadProgress(
        downloadId,
        displayTitle,
        task.progress * 100,
        downloadSpeedBps,
        'downloading',
      ));

      // Log resume action
      EnvironmentLogger().i('Download $downloadId resumed');
      await StructuredLogger().log(
        level: 'info',
        subsystem: 'torrent',
        message: 'Download resumed',
        extra: {
          'downloadId': downloadId,
          'progress': task.progress * 100,
        },
      );
    } on Exception catch (e) {
      throw TorrentFailure('Failed to resume download: ${e.toString()}');
    }
  }

  /// Resumes a restored download by restarting it.
  ///
  /// The [downloadId] parameter is the unique identifier of the download.
  /// The download must be in restored state (not active).
  ///
  /// Throws [TorrentFailure] if the download cannot be resumed.
  Future<void> resumeRestoredDownload(String downloadId) async {
    final metadata = _downloadMetadata[downloadId];
    if (metadata == null) {
      throw const TorrentFailure('Download not found');
    }

    if (_activeTasks.containsKey(downloadId)) {
      throw const TorrentFailure('Download is already active');
    }

    final savePath = metadata['savePath'] as String?;
    if (savePath == null) {
      throw const TorrentFailure('Save path not found');
    }

    // Check if we have magnet URL or torrent file
    final magnetUrl = metadata['magnetUrl'] as String?;
    final torrentBytes = metadata['torrentBytes'] as Uint8List?;

    if (magnetUrl != null && magnetUrl.isNotEmpty) {
      // Resume from magnet URL
      await downloadSequential(
        magnetUrl,
        savePath,
        title: metadata['title'] as String?,
      );
    } else if (torrentBytes != null) {
      // Resume from torrent file
      await downloadFromTorrentBytes(
        torrentBytes,
        savePath,
        title: metadata['title'] as String?,
      );
    } else {
      throw const TorrentFailure(
        'Cannot resume: no magnet URL or torrent file found',
      );
    }
  }

  /// Restarts a download that has stopped or failed.
  ///
  /// This method stops the current download task (if active) and restarts it.
  /// For active downloads, it stops and starts the task again.
  /// For inactive downloads, it uses resumeRestoredDownload.
  ///
  /// The [downloadId] parameter is the unique identifier of the download to restart.
  ///
  /// Throws [TorrentFailure] if the download cannot be restarted.
  Future<void> restartDownload(String downloadId) async {
    final logger = EnvironmentLogger();
    try {
      final task = _activeTasks[downloadId];
      if (task != null) {
        // Active download: stop and start again
        try {
          await task.stop();
          await Future.delayed(const Duration(milliseconds: 500));
          await task.start();

          // Update progress status to downloading
          final downloadSpeedBps = task.currentDownloadSpeed * 1024;
          final controller = _progressControllers[downloadId];
          if (controller != null && !controller.isClosed) {
            final progress = TorrentProgress(
              progress: task.progress * 100,
              downloadSpeed: downloadSpeedBps,
              uploadSpeed: task.uploadSpeed,
              downloadedBytes: task.downloaded ?? 0,
              totalBytes: task.metaInfo.length,
              seeders: task.seederNumber,
              leechers: task.allPeersNumber - task.seederNumber,
              status: 'downloading',
            );
            controller.add(progress);
          }

          // Update notification
          final metadata = _downloadMetadata[downloadId];
          final displayTitle =
              metadata?['title'] as String? ?? task.metaInfo.name;
          safeUnawaited(_notificationService.showDownloadProgress(
            downloadId,
            displayTitle,
            task.progress * 100,
            downloadSpeedBps,
            'downloading',
          ));

          logger.i('Download $downloadId restarted (active task)');
          await StructuredLogger().log(
            level: 'info',
            subsystem: 'torrent',
            message: 'Download restarted (active task)',
            extra: {'downloadId': downloadId},
          );
        } on Exception catch (e) {
          logger.w('Error restarting active task: $e');
          throw TorrentFailure(
              'Failed to restart active download: ${e.toString()}');
        }
      } else {
        // Inactive download: use resumeRestoredDownload
        await resumeRestoredDownload(downloadId);
        logger.i('Download $downloadId restarted (restored)');
        await StructuredLogger().log(
          level: 'info',
          subsystem: 'torrent',
          message: 'Download restarted (restored)',
          extra: {'downloadId': downloadId},
        );
      }
    } on Exception catch (e) {
      if (e is TorrentFailure) {
        rethrow;
      }
      throw TorrentFailure('Failed to restart download: ${e.toString()}');
    }
  }

  /// Removes a download and starts it again from the beginning.
  ///
  /// This method removes the current download (including metadata) and starts
  /// a new download with the same parameters. A new download ID will be generated.
  ///
  /// The [downloadId] parameter is the unique identifier of the download to redownload.
  ///
  /// Returns the new download ID.
  ///
  /// Throws [TorrentFailure] if the download cannot be redownloaded.
  Future<String> redownload(String downloadId) async {
    final logger = EnvironmentLogger();
    try {
      final metadata = _downloadMetadata[downloadId];
      if (metadata == null) {
        throw const TorrentFailure('Download not found');
      }

      // Get save path and source before removing
      final savePath = metadata['savePath'] as String?;
      if (savePath == null) {
        throw const TorrentFailure('Save path not found');
      }

      final magnetUrl = metadata['magnetUrl'] as String?;
      final torrentBytes = metadata['torrentBytes'] as List<int>?;
      final title = metadata['title'] as String?;
      final coverUrl = metadata['coverUrl'] as String?;

      // Remove the old download
      await removeDownload(downloadId);

      // Start new download
      String newDownloadId;
      if (magnetUrl != null && magnetUrl.isNotEmpty) {
        newDownloadId = await downloadSequential(
          magnetUrl,
          savePath,
          title: title,
          coverUrl: coverUrl,
        );
      } else if (torrentBytes != null) {
        newDownloadId = await downloadFromTorrentBytes(
          torrentBytes,
          savePath,
          title: title,
          coverUrl: coverUrl,
        );
      } else {
        throw const TorrentFailure(
          'Cannot redownload: no magnet URL or torrent file found',
        );
      }

      logger.i('Download $downloadId redownloaded as $newDownloadId');
      await StructuredLogger().log(
        level: 'info',
        subsystem: 'torrent',
        message: 'Download redownloaded',
        extra: {
          'oldDownloadId': downloadId,
          'newDownloadId': newDownloadId,
        },
      );

      return newDownloadId;
    } on Exception catch (e) {
      throw TorrentFailure('Failed to redownload: ${e.toString()}');
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
    StreamController<TorrentProgress>? controller;
    try {
      // Cancel progress timer first
      final timer = _progressTimers[downloadId];
      if (timer != null) {
        timer.cancel();
        _progressTimers.remove(downloadId);
      }

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

      // Store controller reference for finally block
      controller = _progressControllers[downloadId];
      _progressControllers.remove(downloadId);
      _downloadMetadata.remove(downloadId);

      // Clean up retry tracking data
      _cleanupRetryData(downloadId);
    } on Exception catch (e) {
      throw TorrentFailure('Failed to remove download: ${e.toString()}');
    } finally {
      // Always close controller in finally block to prevent leaks
      if (controller != null && !controller.isClosed) {
        try {
          await controller.close();
        } on Exception catch (e) {
          EnvironmentLogger()
              .w('Error closing controller in removeDownload: $e');
        }
      }
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
  /// including their IDs, progress, and status. It also includes restored
  /// downloads that were recovered from the database but not yet restarted.
  ///
  /// Returns a list of maps containing download information.
  ///
  /// Throws [TorrentFailure] if the active downloads cannot be retrieved.
  Future<List<Map<String, dynamic>>> getActiveDownloads() async {
    try {
      final downloads = <Map<String, dynamic>>[];

      logger
        ..i(
          'getActiveDownloads: Starting - active tasks: ${_activeTasks.length}, metadata entries: ${_downloadMetadata.length}',
        )
        ..i(
          'getActiveDownloads: Active task IDs: ${_activeTasks.keys.toList()}',
        )
        ..i(
          'getActiveDownloads: Metadata IDs: ${_downloadMetadata.keys.toList()}',
        );

      // Add active tasks
      for (final entry in _activeTasks.entries) {
        final downloadId = entry.key;
        final task = entry.value;
        final metadata = _downloadMetadata[downloadId] ?? {};

        // Use title from metadata if available, otherwise use torrent name
        final displayName = metadata['title'] as String? ?? task.metaInfo.name;

        logger.d(
          'getActiveDownloads: Adding active task - id: $downloadId, name: $displayName, progress: ${(task.progress * 100).toStringAsFixed(1)}%',
        );

        // Convert KB/s to B/s for consistency
        final downloadSpeedBps = task.currentDownloadSpeed * 1024;
        downloads.add({
          'id': downloadId,
          'name': displayName,
          'title': metadata['title'],
          'progress': task.progress * 100,
          'downloadSpeed': downloadSpeedBps,
          'uploadSpeed': task.uploadSpeed,
          'downloadedBytes': task.downloaded ?? 0,
          'totalBytes': task.metaInfo.length,
          'seeders': task.seederNumber,
          'leechers': task.allPeersNumber - task.seederNumber,
          'status': task.progress >= 1.0 ? 'completed' : 'downloading',
          'startedAt': metadata['startedAt'],
          'pausedAt': metadata['pausedAt'],
          'savePath': metadata['savePath'],
          'isActive': true, // Mark as active
        });
      }

      // Add restored downloads (from database but not active)
      var restoredCount = 0;
      var removedCount = 0;
      for (final entry in _downloadMetadata.entries) {
        final downloadId = entry.key;
        // Skip if already in active tasks
        if (_activeTasks.containsKey(downloadId)) continue;

        final metadata = entry.value;
        final savePath = metadata['savePath'] as String?;
        if (savePath == null) {
          logger.d(
            'getActiveDownloads: Skipping metadata entry - id: $downloadId, reason: no savePath',
          );
          continue;
        }

        // Check if download directory still exists
        final saveDir = Directory(savePath);
        if (!await saveDir.exists()) {
          logger.w(
            'getActiveDownloads: Removing metadata - id: $downloadId, reason: directory does not exist: $savePath',
          );
          await _removeDownloadMetadata(downloadId);
          removedCount++;
          continue;
        }

        logger.d(
          'getActiveDownloads: Adding restored download - id: $downloadId, name: ${metadata['title'] as String? ?? 'Unknown'}',
        );
        restoredCount++;

        // Restore progress from metadata if available
        final restoredProgress =
            (metadata['progress'] as num?)?.toDouble() ?? 0.0;
        final restoredStatus = metadata['status'] as String? ?? 'restored';
        final restoredDownloadedBytes =
            (metadata['downloadedBytes'] as num?)?.toInt() ?? 0;
        final restoredTotalBytes =
            (metadata['totalBytes'] as num?)?.toInt() ?? 0;

        downloads.add({
          'id': downloadId,
          'name': metadata['title'] as String? ?? 'Unknown',
          'title': metadata['title'],
          'progress': restoredProgress,
          'downloadSpeed': 0.0,
          'uploadSpeed': 0.0,
          'downloadedBytes': restoredDownloadedBytes,
          'totalBytes': restoredTotalBytes,
          'seeders': 0,
          'leechers': 0,
          'status': restoredStatus,
          'startedAt': metadata['startedAt'],
          'pausedAt': metadata['pausedAt'],
          'savePath': savePath,
          'isActive': false, // Mark as not active
        });
      }

      logger.d(
        'getActiveDownloads: Completed - total: ${downloads.length} (active: ${_activeTasks.length}, restored: $restoredCount, removed: $removedCount)',
      );

      return downloads;
    } on Exception catch (e) {
      logger.e('getActiveDownloads: Failed to get active downloads', error: e);
      throw TorrentFailure('Failed to get active downloads: ${e.toString()}');
    }
  }

  /// Shuts down the torrent manager and cleans up resources.
  ///
  /// This method closes all active download streams and clears the
  /// progress controllers. It should be called when the application
  /// is closing or when torrent functionality is no longer needed.
  ///
  /// Note: Download metadata is preserved in the database to allow
  /// restoration of downloads after app restart.
  ///
  /// Throws [TorrentFailure] if shutdown fails.
  Future<void> shutdown() async {
    try {
      // Cancel all progress timers
      for (final timer in _progressTimers.values) {
        timer.cancel();
      }
      _progressTimers.clear();

      // Stop all metadata downloaders with limited parallelism (max 4)
      // This prevents overwhelming the system during shutdown
      const maxConcurrentShutdown = 4;
      final metadataList = _metadataDownloaders.values.toList();
      for (var i = 0; i < metadataList.length; i += maxConcurrentShutdown) {
        final batch = metadataList.skip(i).take(maxConcurrentShutdown).toList();
        await Future.wait(batch.map((metadata) async {
          try {
            await metadata.stop();
          } on Exception {
            // Ignore errors during shutdown
          }
        }));
      }
      _metadataDownloaders.clear();

      // Close all progress controllers safely
      await Future.wait(_progressControllers.values.map((controller) async {
        try {
          if (!controller.isClosed) {
            await controller.close();
          }
        } on Exception {
          // Ignore errors during shutdown
        }
      }));
      _progressControllers.clear();

      // Dispose all active torrent tasks
      await Future.wait(_activeTasks.values.map((task) => task.dispose()));
      _activeTasks.clear();

      // Don't clear _downloadMetadata - it's needed for restoration
      // Metadata is already persisted in database via _saveDownloadMetadata
    } on Exception catch (e) {
      throw TorrentFailure(
          'Failed to shutdown torrent manager: ${e.toString()}');
    }
  }

  /// Saves download metadata to database.
  Future<void> _saveDownloadMetadata(
      String downloadId, Map<String, dynamic> metadata) async {
    if (_db == null) {
      logger.d(
          '_saveDownloadMetadata: Database not initialized, skipping save for downloadId: $downloadId');
      return;
    }

    try {
      final store = AppDatabase.getInstance().downloadsStore;
      await store.record(downloadId).put(_db!, metadata);
      logger.d(
          '_saveDownloadMetadata: Successfully saved metadata for downloadId: $downloadId');
    } on Exception catch (e) {
      // Log error but don't throw - persistence is optional
      logger.w(
          '_saveDownloadMetadata: Failed to save metadata for downloadId: $downloadId',
          error: e);
    }
  }

  /// Removes download metadata from database.
  Future<void> _removeDownloadMetadata(String downloadId) async {
    if (_db == null) return;

    try {
      final store = AppDatabase.getInstance().downloadsStore;
      await store.record(downloadId).delete(_db!);
    } on Exception {
      // Ignore errors - persistence is optional
    }
  }

  /// Restores downloads from database on app startup.
  Future<void> _restoreDownloads() async {
    if (_db == null) return;

    try {
      final store = AppDatabase.getInstance().downloadsStore;
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
  /// should be saved. Uses the default audiobook path from StoragePathUtils.
  /// If the directory doesn't exist, it will be created.
  ///
  /// Returns the path to the download directory as a string.
  static Future<String> getDownloadDirectory() async {
    final logger = EnvironmentLogger();
    // Use default audiobook path from StoragePathUtils
    final storageUtils = StoragePathUtils();
    final audiobookPath = await storageUtils.getDefaultAudiobookPath();
    final downloadDir = Directory(audiobookPath);
    if (!await downloadDir.exists()) {
      try {
        logger.d('Creating default download directory: $audiobookPath');
        await downloadDir.create(recursive: true);
        logger.d(
            'Successfully created default download directory: $audiobookPath');
      } on Exception catch (e) {
        logger.e(
          'Failed to create default download directory: $audiobookPath',
          error: e,
        );
        // Don't throw here - let StoragePathUtils handle the error
        // This is a fallback method, so we should be lenient
      }
    } else {
      logger.d('Default download directory already exists: $audiobookPath');
    }
    return downloadDir.path;
  }

  /// Formats bytes for logging purposes.
  /// Checks if all downloads are completed and stops foreground service if needed.
  Future<void> _checkAndStopForegroundService() async {
    try {
      // Check if there are any active downloads
      final hasActiveDownloads = _activeTasks.isNotEmpty;

      if (!hasActiveDownloads) {
        // All downloads completed, stop foreground service
        await _foregroundService.stopService();
        EnvironmentLogger()
            .i('All downloads completed, stopped foreground service');
        await StructuredLogger().log(
          level: 'info',
          subsystem: 'torrent',
          message: 'All downloads completed, stopped foreground service',
        );
      }
    } on Exception catch (e) {
      EnvironmentLogger().w('Failed to check/stop foreground service: $e');
      await StructuredLogger().log(
        level: 'warning',
        subsystem: 'torrent',
        message: 'Failed to check/stop foreground service',
        extra: {'error': e.toString()},
      );
    }
  }

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

  /// Registers background service for monitoring downloads.
  ///
  /// This ensures downloads continue even when app is in background.
  Future<void> _registerBackgroundService() async {
    try {
      final backgroundService = DownloadBackgroundService();
      await backgroundService.registerDownloadMonitoring();
    } on Exception catch (e) {
      EnvironmentLogger().w('Failed to register background service: $e');
      // Continue without background service - downloads will still work
    }
  }

  /// Verifies that the save directory exists and is accessible before download starts.
  ///
  /// This method checks that the directory exists and can be listed.
  Future<void> _verifySaveDirectory(String downloadId, String savePath) async {
    final logger = EnvironmentLogger();
    try {
      final saveDir = Directory(savePath);
      final exists = await saveDir.exists();

      await StructuredLogger().log(
        level: exists ? 'info' : 'warning',
        subsystem: 'torrent',
        message: 'Save directory verification',
        extra: {
          'downloadId': downloadId,
          'savePath': savePath,
          'exists': exists,
        },
      );

      if (exists) {
        try {
          await saveDir.list().first;
          logger.d('Save directory is accessible: $savePath');
        } on Exception catch (e) {
          logger.w('Save directory exists but may not be accessible: $savePath',
              error: e);
        }
      } else {
        logger.w('Save directory does not exist yet: $savePath');
      }
    } on Exception catch (e) {
      logger.e('Error verifying save directory: $savePath', error: e);
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'torrent',
        message: 'Error verifying save directory',
        extra: {
          'downloadId': downloadId,
          'savePath': savePath,
          'error': e.toString(),
        },
      );
    }
  }

  /// Verifies that downloaded files exist after download completion.
  ///
  /// Verifies downloaded files in multiple possible paths.
  ///
  /// TorrentTask may save files in different locations than expected,
  /// so we check multiple possible paths.
  Future<void> _verifyDownloadedFilesInMultiplePaths(
    String downloadId,
    List<String> possiblePaths,
  ) async {
    final logger = EnvironmentLogger();
    var foundFiles = false;

    for (final checkPath in possiblePaths) {
      if (checkPath.isEmpty) continue;

      try {
        final checkDir = Directory(checkPath);
        if (!await checkDir.exists()) {
          logger.d('Path does not exist: $checkPath');
          continue;
        }

        final entities = await checkDir.list().toList();
        final files = entities.whereType<File>().toList();

        // Filter out .bt.state files - we're looking for actual content files
        final contentFiles = files
            .where((f) =>
                !f.path.endsWith('.bt.state') && !f.path.endsWith('.torrent'))
            .toList();

        if (contentFiles.isNotEmpty) {
          foundFiles = true;
          logger.i('Found ${contentFiles.length} content files in: $checkPath');

          var totalSize = 0;
          for (final file in contentFiles.take(10)) {
            try {
              final size = await file.length();
              totalSize += size;
              logger
                  .i('  File: ${file.path}, size: ${_formatBytesForLog(size)}');
            } on Exception catch (e) {
              logger.w('Cannot get size for file: ${file.path}', error: e);
            }
          }

          logger
              .i('Total content files size: ${_formatBytesForLog(totalSize)}');

          await StructuredLogger().log(
            level: 'info',
            subsystem: 'torrent',
            message: 'Download files found in alternative path',
            extra: {
              'downloadId': downloadId,
              'foundPath': checkPath,
              'fileCount': contentFiles.length,
              'totalSize': totalSize,
            },
          );
          break; // Found files, no need to check other paths
        } else {
          logger.d(
              'No content files found in: $checkPath (found ${files.length} total files)');
        }
      } on Exception catch (e) {
        logger.d('Cannot check path: $checkPath', error: e);
      }
    }

    if (!foundFiles) {
      logger.w('No content files found in any of the checked paths');
      await StructuredLogger().log(
        level: 'warning',
        subsystem: 'torrent',
        message: 'No content files found in any checked path',
        extra: {
          'downloadId': downloadId,
          'checkedPaths': possiblePaths,
        },
      );
    }
  }

  /// Triggers library scan after download completion.
  ///
  /// Performs a full scan of all library folders to ensure all downloaded files
  /// are found, including files in subdirectories.
  Future<void> _triggerLibraryScan(String? savePath) async {
    final logger = EnvironmentLogger();
    try {
      // Use SmartScannerService for full scan to ensure all files are found
      // This is necessary because torrent files may be saved in subdirectories
      final folderFilterService = FolderFilterService();
      final scanner = AudiobookLibraryScanner(
        folderFilterService: folderFilterService,
        contentUriService: Platform.isAndroid ? ContentUriService() : null,
      );
      final smartScanner = SmartScannerService(
        scanner: scanner,
        folderFilterService: folderFilterService,
      );

      logger.i(
        'Triggering full library scan after download completion (savePath: $savePath)',
      );

      // Perform full scan to ensure all downloaded files are found
      // This will scan all library folders recursively
      await smartScanner.forceFullScan();

      await StructuredLogger().log(
        level: 'info',
        subsystem: 'torrent',
        message: 'Full library scan completed after download',
        extra: {'savePath': savePath},
      );

      logger.i('Full library scan completed successfully');
    } on Exception catch (e) {
      logger.w('Failed to trigger library scan: $e');
      await StructuredLogger().log(
        level: 'warning',
        subsystem: 'torrent',
        message: 'Failed to trigger library scan',
        extra: {
          'savePath': savePath,
          'error': e.toString(),
        },
      );
    }
  }

  /// Downloads cover image from URL and saves it to the download directory.
  ///
  /// Tries common cover file names (cover.jpg, folder.jpg, etc.)
  Future<void> _downloadCoverImage(
    String coverUrl,
    String directoryPath,
    String? torrentName,
  ) async {
    final logger = EnvironmentLogger();
    try {
      // Determine file extension from URL
      final uri = Uri.tryParse(coverUrl);
      if (uri == null) {
        logger.w('Invalid cover URL: $coverUrl');
        return;
      }

      final urlPath = uri.path.toLowerCase();
      var extension = '.jpg'; // Default to jpg
      if (urlPath.endsWith('.png')) {
        extension = '.png';
      } else if (urlPath.endsWith('.gif')) {
        extension = '.gif';
      } else if (urlPath.endsWith('.webp')) {
        extension = '.webp';
      }

      // Try common cover file names
      final coverFileNames = ['cover', 'folder', 'album', 'artwork', 'art'];
      final directory = Directory(directoryPath);

      if (!await directory.exists()) {
        logger.w('Directory does not exist for cover download: $directoryPath');
        return;
      }

      // Download to first available cover name
      for (final coverName in coverFileNames) {
        final coverPath = path.join(directoryPath, '$coverName$extension');
        final coverFile = File(coverPath);

        // Skip if file already exists
        if (await coverFile.exists()) {
          logger.d('Cover already exists: $coverPath');
          continue;
        }

        try {
          // Download cover image
          final dio = await DioClient.instance;
          final response = await dio.get<Uint8List>(
            coverUrl,
            options: Options(responseType: ResponseType.bytes),
          );

          if (response.data != null && response.data!.isNotEmpty) {
            await coverFile.writeAsBytes(response.data!);
            logger.i('Downloaded cover image to: $coverPath');
            await StructuredLogger().log(
              level: 'info',
              subsystem: 'torrent',
              message: 'Downloaded cover image',
              extra: {
                'coverPath': coverPath,
                'coverUrl': coverUrl,
              },
            );
            return; // Success, exit
          }
        } on Exception catch (e) {
          logger.w('Failed to download cover to $coverPath: $e');
          // Try next name
          continue;
        }
      }

      logger.w('Failed to download cover image: all attempts failed');
    } on Exception catch (e) {
      logger.w('Error downloading cover image: $e');
      await StructuredLogger().log(
        level: 'warning',
        subsystem: 'torrent',
        message: 'Failed to download cover image',
        extra: {
          'coverUrl': coverUrl,
          'directoryPath': directoryPath,
          'error': e.toString(),
        },
      );
    }
  }
}
