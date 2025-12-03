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

import 'package:jabook/core/infrastructure/errors/failures.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:jabook/core/library/library_file_finder.dart';
import 'package:jabook/core/utils/storage_path_utils.dart';
import 'package:mime/mime.dart' as mime;
import 'package:path/path.dart' as path;
import 'package:shelf/shelf.dart';
import 'package:shelf/shelf_io.dart';
import 'package:shelf_static/shelf_static.dart';

/// Local HTTP server for streaming audiobook files.
///
/// This class provides a local web server that can stream audiobook files
/// to media players, supporting range requests for progressive playback.
class LocalStreamServer {
  /// HTTP server instance.
  HttpServer? _server;

  /// Host address for the server (localhost only).
  final String _host = '127.0.0.1';

  /// Port number for the server.
  final int _port = 17171;

  /// Flag indicating whether the server is currently running.
  bool _isRunning = false;

  /// Gets whether the server is currently running.
  bool get isRunning => _isRunning;

  /// Starts the local stream server.
  ///
  /// This method initializes and starts the HTTP server on the configured
  /// host and port. The server will handle streaming requests and static file serving.
  ///
  /// Throws [StreamFailure] if the server cannot be started.
  Future<void> start() async {
    if (_isRunning) return;

    try {
      // Create a shelf handler
      final handler = const Pipeline()
          .addMiddleware(logRequests())
          .addHandler(_createHandler());

      // Start the server
      _server = await serve(handler, _host, _port);
      _server!.autoCompress = true;
      _isRunning = true;

      // ignore: avoid_print
      print('Local stream server started on http://$_host:$_port');
      await StructuredLogger().log(
        level: 'info',
        subsystem: 'stream',
        message: 'Local stream server started',
        extra: {'host': _host, 'port': _port},
      );
    } on Exception catch (e) {
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'stream',
        message: 'Failed to start stream server',
        cause: e.toString(),
      );
      throw StreamFailure('Failed to start stream server: ${e.toString()}');
    }
  }

  /// Stops the local stream server.
  ///
  /// This method gracefully shuts down the HTTP server and cleans up resources.
  ///
  /// Throws [StreamFailure] if the server cannot be stopped.
  Future<void> stop() async {
    if (!_isRunning || _server == null) return;

    try {
      await _server!.close();
      _server = null;
      _isRunning = false;
      // ignore: avoid_print
      print('Local stream server stopped');
      await StructuredLogger().log(
        level: 'info',
        subsystem: 'stream',
        message: 'Local stream server stopped',
      );
    } on Exception catch (e) {
      throw StreamFailure('Failed to stop stream server: ${e.toString()}');
    }
  }

  /// Restarts the server (stop then start).
  Future<void> restart() async {
    try {
      await stop();
    } on Exception {
      // ignore stop errors
    }
    await start();
  }

  /// Ensures the server is running, starts it if needed.
  Future<void> ensureRunning() async {
    if (!_isRunning) {
      await start();
    }
  }

  /// Creates the request handler for the server.
  ///
  /// This method returns a handler function that routes incoming requests
  /// to the appropriate handler based on the URL path.
  ///
  /// Returns a [Handler] function for processing HTTP requests.
  Handler _createHandler() => (request) async {
        final uri = request.url;

        // Handle streaming requests
        if (uri.pathSegments.isNotEmpty && uri.pathSegments.first == 'stream') {
          try {
            return await _handleStreamRequest(request);
          } on Exception catch (e) {
            await StructuredLogger().log(
              level: 'error',
              subsystem: 'stream',
              message: 'Stream request error',
              cause: e.toString(),
              extra: {'path': request.url.toString()},
            );
            return Response.internalServerError(body: 'Streaming error');
          }
        }

        // Handle other requests with static file serving
        try {
          return await _handleStaticRequest(request);
        } on Exception catch (e) {
          await StructuredLogger().log(
            level: 'error',
            subsystem: 'stream',
            message: 'Static request error',
            cause: e.toString(),
            extra: {'path': request.url.toString()},
          );
          return Response.internalServerError(body: 'Static file error');
        }
      };

  /// Handles streaming requests for audiobook files.
  ///
  /// This method processes requests to the `/stream` endpoint, validates
  /// required parameters, and serves the requested audiobook file.
  /// It supports range requests for progressive playback.
  ///
  /// The [request] parameter contains the HTTP request details.
  ///
  /// Returns an appropriate [Response] based on the request outcome.
  Future<Response> _handleStreamRequest(Request request) async {
    try {
      final queryParams = request.url.queryParameters;
      final bookId = queryParams['id'];
      final fileIndex = int.tryParse(queryParams['file'] ?? '0');

      if (bookId == null) {
        return Response.badRequest(body: 'Missing book ID parameter');
      }

      if (fileIndex == null) {
        return Response.badRequest(body: 'Invalid file index parameter');
      }

      // Find the file path (searches in download directories)
      String filePath;
      try {
        filePath = await _getFilePath(bookId, fileIndex);
      } on FileSystemException catch (e) {
        await StructuredLogger().log(
          level: 'warning',
          subsystem: 'stream',
          message: 'File not found for streaming request',
          cause: e.toString(),
          extra: {
            'bookId': bookId,
            'fileIndex': fileIndex,
            'path': request.url.toString(),
          },
        );
        return Response.notFound('File not found: ${e.message}');
      }

      final file = File(filePath);
      if (!await file.exists()) {
        await StructuredLogger().log(
          level: 'warning',
          subsystem: 'stream',
          message: 'File path resolved but file does not exist',
          extra: {
            'bookId': bookId,
            'fileIndex': fileIndex,
            'filePath': filePath,
          },
        );
        return Response.notFound('File not found at resolved path');
      }

      // Resolve content type by extension with sensible default
      final contentType = _resolveContentType(filePath);

      // HEAD requests: return headers only
      if (request.method.toUpperCase() == 'HEAD') {
        final stat = await file.stat();
        return Response(
          200,
          headers: {
            HttpHeaders.contentTypeHeader: contentType,
            HttpHeaders.contentLengthHeader: stat.size.toString(),
            HttpHeaders.acceptRangesHeader: 'bytes',
          },
        );
      }

      // Handle Range requests for partial content
      final rangeHeader = request.headers[HttpHeaders.rangeHeader];
      if (rangeHeader != null) {
        return await _handleRangeRequest(filePath, rangeHeader, contentType);
      }

      // Serve full file
      final stat = await file.stat();
      final stream = file.openRead();
      return Response(
        200,
        body: stream,
        headers: {
          HttpHeaders.contentTypeHeader: contentType,
          HttpHeaders.contentLengthHeader: stat.size.toString(),
          HttpHeaders.acceptRangesHeader: 'bytes',
        },
      );
    } on FileSystemException catch (e) {
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'stream',
        message: 'File system error during streaming',
        cause: e.toString(),
        extra: {'path': request.url.toString()},
      );
      return Response.notFound('File not found: ${e.message}');
    } on Exception catch (e) {
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'stream',
        message: 'Unexpected error during streaming',
        cause: e.toString(),
        extra: {'path': request.url.toString()},
      );
      return Response.internalServerError(
          body: 'Streaming error: ${e.toString()}');
    }
  }

  /// Handles HTTP range requests for partial content.
  ///
  /// This method processes Range headers to serve partial file content,
  /// enabling progressive playback and seeking in large audio files.
  ///
  /// The [filePath] parameter is the path to the requested file.
  /// The [rangeHeader] parameter contains the Range header value.
  ///
  /// Returns a [Response] with the partial content or appropriate error.
  Future<Response> _handleRangeRequest(
      String filePath, String rangeHeader, String contentType) async {
    try {
      final file = File(filePath);
      final stat = await file.stat();
      final fileSize = stat.size;

      // Parse Range header (e.g., "bytes=0-1023")
      final rangeMatch = RegExp(r'bytes=(\d+)?-(\d+)?').firstMatch(rangeHeader);
      if (rangeMatch == null) {
        return Response.badRequest(body: 'Invalid range header');
      }

      final start = int.tryParse(rangeMatch.group(1) ?? '0') ?? 0;
      final end =
          int.tryParse(rangeMatch.group(2) ?? (fileSize - 1).toString()) ??
              fileSize - 1;

      if (start >= fileSize || end >= fileSize || start > end) {
        return Response(
          416,
          body: 'Requested range not satisfiable',
          headers: {
            HttpHeaders.contentRangeHeader: 'bytes */$fileSize',
          },
        );
      }

      final contentLength = end - start + 1;
      final stream = file.openRead(start, end + 1);
      return Response(
        206,
        body: stream,
        headers: {
          HttpHeaders.contentTypeHeader: contentType,
          HttpHeaders.contentLengthHeader: contentLength.toString(),
          HttpHeaders.acceptRangesHeader: 'bytes',
          HttpHeaders.contentRangeHeader: 'bytes $start-$end/$fileSize',
        },
      );
    } on Exception {
      return Response.internalServerError(body: 'Range request error');
    }
  }

  /// Handles static file requests.
  ///
  /// This method serves static files from the assets directory and
  /// provides placeholder responses for API endpoints.
  ///
  /// The [request] parameter contains the HTTP request details.
  ///
  /// Returns an appropriate [Response] for static files or API endpoints.
  Future<Response> _handleStaticRequest(Request request) async {
    try {
      // Use shelf_static to serve static files
      final staticHandler = createStaticHandler('assets/web');

      // For API endpoints, return appropriate responses
      if (request.url.path.startsWith('api/')) {
        return Response(
          200,
          body: '{"message": "API endpoint not implemented yet"}',
          headers: {
            HttpHeaders.contentTypeHeader: 'application/json',
          },
        );
      }

      // For other static requests, use the static handler
      return await staticHandler(request);
    } on Exception {
      return Response.internalServerError(body: 'Static file error');
    }
  }

  /// Gets the file path for the specified audiobook file.
  ///
  /// This method searches for the file using LibraryFileFinder, which searches
  /// in both download directories and library folders.
  ///
  /// The [bookId] parameter can be either:
  /// - topicId (numeric ID) for torrent books
  /// - groupPath for external folder books
  /// The [fileIndex] parameter is the index of the file within the audiobook.
  ///
  /// Returns the file path as a string, or throws if file not found.
  Future<String> _getFilePath(String bookId, int fileIndex) async {
    try {
      final fileFinder = LibraryFileFinder();

      // First, try to find file by bookId (for torrent books)
      // This searches in download dir and library folders
      var filePath = await fileFinder.findFileByBookId(bookId, fileIndex);

      if (filePath != null) {
        await StructuredLogger().log(
          level: 'debug',
          subsystem: 'stream',
          message: 'Found file for streaming by bookId',
          extra: {
            'bookId': bookId,
            'fileIndex': fileIndex,
            'filePath': filePath,
          },
        );
        return filePath;
      }

      // If not found by bookId, try to find by groupPath
      // This handles external folders where bookId might be groupPath
      // Check if bookId looks like a path (contains '/' or is a Content URI)
      final isPath = bookId.contains('/') || bookId.startsWith('content://');
      if (isPath) {
        filePath = await fileFinder.findFileByGroupPath(bookId, fileIndex);
        if (filePath != null) {
          await StructuredLogger().log(
            level: 'debug',
            subsystem: 'stream',
            message: 'Found file for streaming by groupPath',
            extra: {
              'bookId': bookId,
              'fileIndex': fileIndex,
              'filePath': filePath,
            },
          );
          return filePath;
        }
      }

      // File not found - log and throw
      await StructuredLogger().log(
        level: 'warning',
        subsystem: 'stream',
        message: 'File not found for streaming',
        extra: {
          'bookId': bookId,
          'fileIndex': fileIndex,
          'triedBookId': true,
          'triedGroupPath': isPath,
        },
      );
      throw FileSystemException(
          'File not found', 'bookId: $bookId, fileIndex: $fileIndex');
    } on FileSystemException {
      rethrow;
    } on Exception catch (e) {
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'stream',
        message: 'Error finding file path',
        cause: e.toString(),
        extra: {'bookId': bookId, 'fileIndex': fileIndex},
      );
      throw FileSystemException('Error finding file', e.toString());
    }
  }

  /// Gets the streaming URL for the specified audiobook file.
  ///
  /// This method constructs the full URL for streaming a specific file
  /// from the local server.
  ///
  /// The [bookId] parameter is the unique identifier for the audiobook.
  /// The [fileIndex] parameter is the index of the file within the audiobook.
  ///
  /// Returns the streaming URL as a string.
  String getStreamUrl(String bookId, int fileIndex) =>
      'http://$_host:$_port/stream?id=$bookId&file=$fileIndex';

  /// Checks if files for the specified audiobook exist locally.
  ///
  /// This method checks if at least one file for the audiobook is available
  /// in the download directory. This is useful to determine if LocalStreamServer
  /// can be used for streaming.
  ///
  /// The [bookId] parameter is the unique identifier for the audiobook.
  ///
  /// Returns `true` if at least one file exists, `false` otherwise.
  Future<bool> hasFiles(String bookId) async {
    try {
      final storageUtils = StoragePathUtils();
      final downloadDir = await storageUtils.getDefaultAudiobookPath();

      final possibleBasePaths = [
        path.join(downloadDir, bookId),
        downloadDir,
      ];

      const audioExtensions = [
        '.mp3',
        '.m4a',
        '.m4b',
        '.aac',
        '.flac',
        '.wav',
        '.ogg',
        '.oga'
      ];

      for (final basePath in possibleBasePaths) {
        try {
          final baseDir = Directory(basePath);
          if (!await baseDir.exists()) {
            continue;
          }

          // Check if any audio files exist
          await for (final entity in baseDir.list(recursive: true)) {
            if (entity is File) {
              final ext = path.extension(entity.path).toLowerCase();
              if (audioExtensions.contains(ext)) {
                // At least one file exists
                return true;
              }
            }
          }
        } on FileSystemException {
          // Permission denied or other error - continue to next path
          continue;
        }
      }

      return false;
    } on Exception {
      return false;
    }
  }

  String _resolveContentType(String path) {
    final type = mime.lookupMimeType(path);
    if (type != null) return type;
    final lower = path.toLowerCase();
    if (lower.endsWith('.mp3')) return 'audio/mpeg';
    if (lower.endsWith('.m4a') || lower.endsWith('.mp4')) return 'audio/mp4';
    if (lower.endsWith('.ogg') || lower.endsWith('.oga')) return 'audio/ogg';
    if (lower.endsWith('.wav')) return 'audio/wav';
    return 'application/octet-stream';
  }
}

/// Represents a failure related to streaming operations.
///
/// This exception is thrown when errors occur during streaming
/// server operations, such as starting or stopping the server.
class StreamFailure extends Failure {
  /// Creates a new StreamFailure instance.
  ///
  /// The [message] parameter describes the streaming-related failure.
  /// The optional [exception] parameter contains the original exception
  /// that caused this failure, if any.
  const StreamFailure(super.message, [super.exception]);
}
