import 'dart:async';
import 'dart:io';
import 'package:dtorrent_task/dtorrent_task.dart';
import 'package:path_provider/path_provider.dart';
import '../errors/failures.dart';

class TorrentProgress {
  final double progress;
  final double downloadSpeed;
  final double uploadSpeed;
  final int downloadedBytes;
  final int totalBytes;
  final int seeders;
  final int leechers;
  final String status;

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
}

class AudiobookTorrentManager {
  dtorrent_task.TorrentEngine? _torrentEngine;
  String? _currentDownloadId;
  final Map<String, StreamController<TorrentProgress>> _progressControllers = {};

  AudiobookTorrentManager();

  Future<void> downloadSequential(String magnetUrl, String savePath) async {
    try {
      // Initialize torrent engine if not already initialized
      _torrentEngine ??= dtorrent_task.TorrentEngine(
        maxActiveDownloads: 3,
        maxConnectionsPerDownload: 50,
        downloadPath: savePath,
      );

      // Generate a unique download ID
      _currentDownloadId = DateTime.now().millisecondsSinceEpoch.toString();

      // Create progress controller for this download
      final progressController = StreamController<TorrentProgress>.broadcast();
      _progressControllers[_currentDownloadId!] = progressController;

      // Add magnet URL to torrent engine with sequential priority
      await _torrentEngine!.addMagnet(
        magnetUrl,
        downloadId: _currentDownloadId!,
        sequential: true,
        priority: dtorrent_task.Priority.high,
      );

      // Listen to progress updates
      _torrentEngine!.listenToDownload(
        _currentDownloadId!,
        (progress) {
          progressController.add(_convertProgress(progress));
        },
      );

      // Handle download completion
      _torrentEngine!.listenToDownloadState(
        _currentDownloadId!,
        (state) {
          if (state == dtorrent_task.DownloadState.completed) {
            progressController.close();
            _progressControllers.remove(_currentDownloadId);
          }
        },
      );

    } catch (e) {
      throw TorrentFailure('Failed to start download: ${e.toString()}');
    }
  }

  Future<void> pauseDownload(String downloadId) async {
    try {
      await _torrentEngine?.pauseDownload(downloadId);
    } catch (e) {
      throw TorrentFailure('Failed to pause download: ${e.toString()}');
    }
  }

  Future<void> resumeDownload(String downloadId) async {
    try {
      await _torrentEngine?.resumeDownload(downloadId);
    } catch (e) {
      throw TorrentFailure('Failed to resume download: ${e.toString()}');
    }
  }

  Future<void> removeDownload(String downloadId) async {
    try {
      await _torrentEngine?.removeDownload(downloadId);
      _progressControllers[downloadId]?.close();
      _progressControllers.remove(downloadId);
    } catch (e) {
      throw TorrentFailure('Failed to remove download: ${e.toString()}');
    }
  }

  Stream<TorrentProgress> getProgressStream(String downloadId) {
    final controller = _progressControllers[downloadId];
    if (controller == null) {
      throw TorrentFailure('Download not found: $downloadId');
    }
    return controller.stream;
  }

  Future<List<Map<String, dynamic>>> getActiveDownloads() async {
    try {
      final downloads = await _torrentEngine?.getDownloads() ?? [];
      return downloads.map((download) => {
        'id': download.downloadId,
        'magnetUrl': download.magnetUrl,
        'status': download.state.toString(),
        'progress': download.progress,
        'downloadSpeed': download.downloadSpeed,
        'uploadSpeed': download.uploadSpeed,
        'downloadedBytes': download.downloadedBytes,
        'totalBytes': download.totalBytes,
        'seeders': download.seeders,
        'leechers': download.leechers,
      }).toList();
    } catch (e) {
      throw TorrentFailure('Failed to get active downloads: ${e.toString()}');
    }
  }

  Future<void> shutdown() async {
    try {
      await _torrentEngine?.shutdown();
      _progressControllers.forEach((_, controller) => controller.close());
      _progressControllers.clear();
    } catch (e) {
      throw TorrentFailure('Failed to shutdown torrent engine: ${e.toString()}');
    }
  }

  TorrentProgress _convertProgress(dtorrent_task.DownloadProgress progress) {
    return TorrentProgress(
      progress: progress.progress,
      downloadSpeed: progress.downloadSpeed,
      uploadSpeed: progress.uploadSpeed,
      downloadedBytes: progress.downloadedBytes,
      totalBytes: progress.totalBytes,
      seeders: progress.seeders,
      leechers: progress.leechers,
      status: progress.state.toString(),
    );
  }

  static Future<String> getDownloadDirectory() async {
    final appDocDir = await getApplicationDocumentsDirectory();
    final downloadDir = Directory('${appDocDir.path}/downloads');
    if (!await downloadDir.exists()) {
      await downloadDir.create(recursive: true);
    }
    return downloadDir.path;
  }
}