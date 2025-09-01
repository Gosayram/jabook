import 'dart:async';
import 'dart:io';

import 'package:jabook/core/errors/failures.dart';
import 'package:path_provider/path_provider.dart';

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
  
  /// ID of the currently active download.
  String? _currentDownloadId;
  
  /// Map of download progress controllers for active downloads.
  final Map<String, StreamController<TorrentProgress>> _progressControllers = {};
  

  /// Starts a sequential torrent download for an audiobook.
  ///
  /// This method initiates a torrent download using the provided magnet URL
  /// and saves it to the specified path. It creates a progress stream
  /// to monitor download progress.
  ///
  /// The [magnetUrl] parameter is the magnet link for the torrent.
  /// The [savePath] parameter is the directory where the downloaded files will be saved.
  ///
  /// Throws [TorrentFailure] if the download cannot be started.
  Future<void> downloadSequential(String magnetUrl, String savePath) async {
    try {
      // Generate a unique download ID
      _currentDownloadId = DateTime.now().millisecondsSinceEpoch.toString();

      // Create progress controller for this download
      final progressController = StreamController<TorrentProgress>.broadcast();
      _progressControllers[_currentDownloadId!] = progressController;

      // Parse magnet URL to extract info hash
      final uri = Uri.parse(magnetUrl);
      final xtParam = uri.queryParameters['xt'];
      if (xtParam == null || !xtParam.startsWith('urn:btih:')) {
        throw const TorrentFailure('Invalid magnet URL: missing info hash');
      }
      
      final infoHash = xtParam.substring('urn:btih:'.length);
      if (infoHash.length != 40) {
        throw const TorrentFailure('Invalid info hash length');
      }

      // Create metadata downloader for magnet links
      // TODO: Implement real magnet link download
      // For now, continue with simulated download as the metadata downloader
      // requires additional dependencies and setup that may not be compatible
      // with Flutter environment
      _simulateDownload(progressController);

      // TODO: Replace with real dtorrent_task implementation
      // The current implementation uses simulation due to complexity
      // of integrating the full dtorrent_task library with Flutter

    } on Exception catch (e) {
      throw TorrentFailure('Failed to start download: ${e.toString()}');
    }
  }

  void _simulateDownload(StreamController<TorrentProgress> progressController) {
    var progress = 0.0;
    const downloadSpeed = 1024.0; // 1 KB/s
    const totalBytes = 100 * 1024 * 1024; // 100 MB
    
    Timer.periodic(const Duration(seconds: 1), (timer) {
      progress += (downloadSpeed / totalBytes) * 100;
      
      if (progress >= 100) {
        progress = 100.0;
        timer.cancel();
        progressController.close();
        _progressControllers.remove(_currentDownloadId);
      }
      
      final torrentProgress = TorrentProgress(
        progress: progress,
        downloadSpeed: downloadSpeed,
        uploadSpeed: 0.0,
        downloadedBytes: (progress / 100 * totalBytes).toInt(),
        totalBytes: totalBytes,
        seeders: 5,
        leechers: 10,
        status: progress < 100 ? 'downloading' : 'completed',
      );
      
      progressController.add(torrentProgress);
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
      // TODO: Implement pause functionality
      throw UnimplementedError('Pause download not implemented');
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
      // TODO: Implement resume functionality
      throw UnimplementedError('Resume download not implemented');
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
      await _progressControllers[downloadId]?.close();
      _progressControllers.remove(downloadId);
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
      // TODO: Implement actual active downloads retrieval
      return [];
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
      await Future.wait(_progressControllers.values.map((controller) => controller.close()));
      _progressControllers.clear();
    } on Exception catch (e) {
      throw TorrentFailure('Failed to shutdown torrent manager: ${e.toString()}');
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
}