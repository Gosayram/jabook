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
import 'dart:isolate';

import 'package:crypto/crypto.dart';
import 'package:dtorrent_parser/dtorrent_parser.dart';
import 'package:dtorrent_task_v2/dtorrent_task_v2.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';

/// Messages sent to the torrent isolate
abstract class TorrentIsolateMessage {}

/// Command to create and start a torrent task
class CreateTorrentTaskMessage implements TorrentIsolateMessage {
  /// Creates a new CreateTorrentTaskMessage instance.
  CreateTorrentTaskMessage(
    this.torrentModel,
    this.savePath,
    this.sequential, {
    this.webSeeds,
    this.acceptableSources,
    this.selectedFileIndices,
  });

  /// The torrent model to create task from.
  final Torrent torrentModel;

  /// The path where to save downloaded files.
  final String savePath;

  /// Whether to download files sequentially.
  final bool sequential;

  /// Optional web seeds URLs.
  final List<Uri>? webSeeds;

  /// Optional acceptable sources URLs.
  final List<Uri>? acceptableSources;

  /// Optional selected file indices to download.
  final List<int>? selectedFileIndices;
}

/// Command to pause a torrent task
class PauseTorrentTaskMessage implements TorrentIsolateMessage {
  /// Creates a new PauseTorrentTaskMessage instance.
  PauseTorrentTaskMessage(this.taskId);

  /// The task ID to pause.
  final String taskId;
}

/// Command to resume a torrent task
class ResumeTorrentTaskMessage implements TorrentIsolateMessage {
  /// Creates a new ResumeTorrentTaskMessage instance.
  ResumeTorrentTaskMessage(this.taskId);

  /// The task ID to resume.
  final String taskId;
}

/// Command to stop a torrent task
class StopTorrentTaskMessage implements TorrentIsolateMessage {
  /// Creates a new StopTorrentTaskMessage instance.
  StopTorrentTaskMessage(this.taskId);

  /// The task ID to stop.
  final String taskId;
}

/// Command to get progress
class GetProgressMessage implements TorrentIsolateMessage {
  /// Creates a new GetProgressMessage instance.
  GetProgressMessage(this.taskId);

  /// The task ID to get progress for.
  final String taskId;
}

/// Response from isolate
abstract class TorrentIsolateResponse {}

/// Progress response
class ProgressResponse implements TorrentIsolateResponse {
  /// Creates a new ProgressResponse instance.
  ProgressResponse({
    required this.taskId,
    required this.progress,
    required this.downloadSpeed,
    required this.uploadSpeed,
    required this.downloadedBytes,
    required this.totalBytes,
    required this.seeders,
    required this.leechers,
    required this.status,
  });

  /// The task ID.
  final String taskId;

  /// Download progress (0.0 to 1.0).
  final double progress;

  /// Current download speed in bytes per second.
  final double downloadSpeed;

  /// Current upload speed in bytes per second.
  final double uploadSpeed;

  /// Number of bytes downloaded.
  final int downloadedBytes;

  /// Total number of bytes to download.
  final int totalBytes;

  /// Number of seeders.
  final int seeders;

  /// Number of leechers.
  final int leechers;

  /// Task status string.
  final String status;
}

/// Task created response
class TaskCreatedResponse implements TorrentIsolateResponse {
  /// Creates a new TaskCreatedResponse instance.
  TaskCreatedResponse(this.taskId);

  /// The created task ID.
  final String taskId;
}

/// Error response
class ErrorResponse implements TorrentIsolateResponse {
  /// Creates a new ErrorResponse instance.
  ErrorResponse(this.taskId, this.error);

  /// The task ID that caused the error.
  final String taskId;

  /// The error message.
  final String error;
}

/// Torrent isolate entry point
void _torrentIsolateEntry(SendPort sendPort) async {
  final receivePort = ReceivePort();
  sendPort.send(receivePort.sendPort);

  final tasks = <String, TorrentTask>{};
  final logger = StructuredLogger();

  receivePort.listen((message) async {
    try {
      if (message is CreateTorrentTaskMessage) {
        try {
          final task = TorrentTask.newTask(
            message.torrentModel,
            message.savePath,
            message.sequential,
            message.webSeeds,
            message.acceptableSources,
          );

          if (message.selectedFileIndices != null &&
              message.selectedFileIndices!.isNotEmpty) {
            task.applySelectedFiles(message.selectedFileIndices!);
          }

          // Generate task ID from info hash
          final infoHashBytes = message.torrentModel.infoHashBuffer;
          final taskId = sha256.convert(infoHashBytes).toString();
          tasks[taskId] = task;

          await task.start();
          sendPort.send(TaskCreatedResponse(taskId));
        } on Object catch (e) {
          await logger.log(
            level: 'error',
            subsystem: 'torrent',
            message: 'Error creating torrent task in isolate',
            cause: e.toString(),
          );
          // Generate task ID from info hash for error response
          final infoHashBytes = message.torrentModel.infoHashBuffer;
          final taskId = sha256.convert(infoHashBytes).toString();
          sendPort.send(ErrorResponse(taskId, e.toString()));
        }
      } else if (message is PauseTorrentTaskMessage) {
        final task = tasks[message.taskId];
        if (task != null) {
          task.pause();
        }
      } else if (message is ResumeTorrentTaskMessage) {
        final task = tasks[message.taskId];
        if (task != null) {
          task.resume();
        }
      } else if (message is StopTorrentTaskMessage) {
        final task = tasks[message.taskId];
        if (task != null) {
          await task.stop();
          await task.dispose();
          tasks.remove(message.taskId);
        }
      } else if (message is GetProgressMessage) {
        final task = tasks[message.taskId];
        if (task != null) {
          sendPort.send(ProgressResponse(
            taskId: message.taskId,
            progress: task.progress,
            downloadSpeed: task.currentDownloadSpeed,
            uploadSpeed: task.uploadSpeed,
            downloadedBytes: task.downloaded ?? 0,
            totalBytes: task.metaInfo.length,
            seeders: task.seederNumber,
            leechers: task.allPeersNumber - task.seederNumber,
            status: task.state == TaskState.running
                ? 'downloading'
                : task.state == TaskState.paused
                    ? 'paused'
                    : 'stopped',
          ));
        }
      }
    } on Object catch (e) {
      await logger.log(
        level: 'error',
        subsystem: 'torrent',
        message: 'Error in torrent isolate',
        cause: e.toString(),
      );
    }
  });
}

/// Manager for torrent isolate
///
/// NOTE: This implementation is experimental and may not work correctly
/// because TorrentTask uses sockets, file handles, and event emitters
/// that cannot be easily serialized or transferred between isolates.
///
/// The current implementation uses foreground service to keep downloads
/// alive in the background, which is a more reliable approach.
class TorrentIsolateManager {
  Isolate? _isolate;
  SendPort? _sendPort;
  ReceivePort? _receivePort;
  bool _initialized = false;
  final StreamController<ProgressResponse> _progressController =
      StreamController<ProgressResponse>.broadcast();

  /// Initialize the isolate
  Future<void> initialize() async {
    if (_initialized) return;

    _receivePort = ReceivePort();
    _isolate = await Isolate.spawn(
      _torrentIsolateEntry,
      _receivePort!.sendPort,
      debugName: 'TorrentIsolate',
    );

    _sendPort = await _receivePort!.first as SendPort;

    // Listen for responses
    _receivePort!.listen((response) {
      if (response is ProgressResponse) {
        _progressController.add(response);
      }
    });

    _initialized = true;
  }

  /// Create and start a torrent task in isolate
  Future<String> createTask(
    Torrent torrentModel,
    String savePath,
    bool sequential, {
    List<Uri>? webSeeds,
    List<Uri>? acceptableSources,
    List<int>? selectedFileIndices,
  }) async {
    if (!_initialized) await initialize();

    final completer = Completer<String>();
    late StreamSubscription subscription;

    subscription = _receivePort!.listen((response) {
      if (response is TaskCreatedResponse) {
        subscription.cancel();
        completer.complete(response.taskId);
      } else if (response is ErrorResponse) {
        subscription.cancel();
        completer.completeError(Exception(response.error));
      }
    });

    _sendPort!.send(CreateTorrentTaskMessage(
      torrentModel,
      savePath,
      sequential,
      webSeeds: webSeeds,
      acceptableSources: acceptableSources,
      selectedFileIndices: selectedFileIndices,
    ));

    return completer.future.timeout(
      const Duration(seconds: 30),
      onTimeout: () {
        subscription.cancel();
        throw TimeoutException('Task creation timeout');
      },
    );
  }

  /// Get progress stream
  Stream<ProgressResponse> get progressStream => _progressController.stream;

  /// Request progress update
  void requestProgress(String taskId) {
    if (!_initialized) return;
    _sendPort!.send(GetProgressMessage(taskId));
  }

  /// Pause a task
  void pauseTask(String taskId) {
    if (!_initialized) return;
    _sendPort!.send(PauseTorrentTaskMessage(taskId));
  }

  /// Resume a task
  void resumeTask(String taskId) {
    if (!_initialized) return;
    _sendPort!.send(ResumeTorrentTaskMessage(taskId));
  }

  /// Stop a task
  Future<void> stopTask(String taskId) async {
    if (!_initialized) return;
    _sendPort!.send(StopTorrentTaskMessage(taskId));
  }

  /// Dispose the isolate
  Future<void> dispose() async {
    if (!_initialized) return;

    _isolate?.kill(priority: Isolate.immediate);
    _receivePort?.close();
    await _progressController.close();

    _isolate = null;
    _sendPort = null;
    _receivePort = null;
    _initialized = false;
  }
}
