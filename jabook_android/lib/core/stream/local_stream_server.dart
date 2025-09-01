import 'dart:async';
import 'dart:io';

import 'package:jabook/core/errors/failures.dart';
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
      _isRunning = true;

      // ignore: avoid_print
      print('Local stream server started on http://$_host:$_port');
    } on Exception catch (e) {
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
    } on Exception catch (e) {
      throw StreamFailure('Failed to stop stream server: ${e.toString()}');
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
      return _handleStreamRequest(request);
    }

    // Handle other requests with static file serving
    return _handleStaticRequest(request);
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

      // TODO: Implement actual streaming logic
      // This is a placeholder implementation
      final filePath = _getFilePath(bookId, fileIndex);
      
      if (!await File(filePath).exists()) {
        return Response.notFound('File not found');
      }

      // Handle Range requests for partial content
      final rangeHeader = request.headers[HttpHeaders.rangeHeader];
      if (rangeHeader != null) {
        return await _handleRangeRequest(filePath, rangeHeader);
      }

      // Serve full file
      final file = File(filePath);
      final fileBytes = await file.readAsBytes();
      
      return Response.ok(
        fileBytes,
        headers: {
          HttpHeaders.contentTypeHeader: 'audio/mpeg',
          HttpHeaders.contentLengthHeader: fileBytes.length.toString(),
        },
      );
    } on Exception {
      return Response.internalServerError(body: 'Streaming error');
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
  Future<Response> _handleRangeRequest(String filePath, String rangeHeader) async {
    try {
      final file = File(filePath);
      final fileBytes = await file.readAsBytes();
      final fileSize = fileBytes.length;

      // Parse Range header (e.g., "bytes=0-1023")
      final rangeMatch = RegExp(r'bytes=(\d+)?-(\d+)?').firstMatch(rangeHeader);
      if (rangeMatch == null) {
        return Response.badRequest(body: 'Invalid range header');
      }

      final start = int.tryParse(rangeMatch.group(1) ?? '0') ?? 0;
      final end = int.tryParse(rangeMatch.group(2) ?? (fileSize - 1).toString()) ?? fileSize - 1;

      if (start >= fileSize || end >= fileSize || start > end) {
        return Response(416, body: 'Requested range not satisfiable');
      }

      final contentLength = end - start + 1;
      final rangeBytes = fileBytes.sublist(start, end + 1);

      return Response(
        206, // Partial Content
        body: rangeBytes,
        headers: {
          HttpHeaders.contentTypeHeader: 'audio/mpeg',
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
  /// This method should be implemented to resolve the actual file path
  /// based on the book ID and file index. Currently returns a placeholder.
  ///
  /// The [bookId] parameter is the unique identifier for the audiobook.
  /// The [fileIndex] parameter is the index of the file within the audiobook.
  ///
  /// Returns the file path as a string.
  String _getFilePath(String bookId, int fileIndex) =>
    // TODO: Implement actual file path resolution based on book ID and file index
    // This is a placeholder implementation
    '/path/to/downloads/$bookId/file_$fileIndex.mp3';

  /// Gets the streaming URL for the specified audiobook file.
  ///
  /// This method constructs the full URL for streaming a specific file
  /// from the local server.
  ///
  /// The [bookId] parameter is the unique identifier for the audiobook.
  /// The [fileIndex] parameter is the index of the file within the audiobook.
  ///
  /// Returns the streaming URL as a string.
  String getStreamUrl(String bookId, int fileIndex) => 'http://$_host:$_port/stream?id=$bookId&file=$fileIndex';
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