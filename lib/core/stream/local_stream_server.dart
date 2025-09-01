import 'dart:async';
import 'dart:io';
import 'package:shelf/shelf.dart';
import 'package:shelf/shelf_io.dart';
import 'package:shelf_static/shelf_static.dart';
import '../errors/failures.dart';

class LocalStreamServer {
  HttpServer? _server;
  final String _host = '127.0.0.1';
  final int _port = 17171;
  bool _isRunning = false;

  bool get isRunning => _isRunning;

  Future<void> start() async {
    if (_isRunning) return;

    try {
      // Create a shelf handler
      final handler = Pipeline()
          .addMiddleware(logRequests())
          .addHandler(_createHandler());

      // Start the server
      _server = await serve(handler, _host, _port);
      _isRunning = true;

      print('Local stream server started on http://$_host:$_port');
    } catch (e) {
      throw StreamFailure('Failed to start stream server: ${e.toString()}');
    }
  }

  Future<void> stop() async {
    if (!_isRunning || _server == null) return;

    try {
      await _server!.close();
      _server = null;
      _isRunning = false;
      print('Local stream server stopped');
    } catch (e) {
      throw StreamFailure('Failed to stop stream server: ${e.toString()}');
    }
  }

  Handler _createHandler() {
    return (Request request) async {
      final uri = request.url;
      
      // Handle streaming requests
      if (uri.pathSegments.isNotEmpty && uri.pathSegments.first == 'stream') {
        return _handleStreamRequest(request);
      }

      // Handle other requests with static file serving
      return _handleStaticRequest(request);
    };
  }

  Future<Response> _handleStreamRequest(Request request) async {
    try {
      final queryParams = request.url.queryParameters;
      final bookId = queryParams['id'];
      final fileIndex = int.tryParse(queryParams['file'] ?? '0');

      if (bookId == null) {
        return Response.badRequest(body: 'Missing book ID parameter');
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
    } catch (e) {
      return Response.internalServerError(body: 'Streaming error: ${e.toString()}');
    }
  }

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
        return Response.requestedRangeNotSatisfiable();
      }

      final contentLength = end - start + 1;
      final rangeBytes = fileBytes.sublist(start, end + 1);

      return Response.partialContent(
        rangeBytes,
        headers: {
          HttpHeaders.contentTypeHeader: 'audio/mpeg',
          HttpHeaders.contentLengthHeader: contentLength.toString(),
          HttpHeaders.acceptRangesHeader: 'bytes',
          HttpHeaders.contentRangeHeader: 'bytes $start-$end/$fileSize',
        },
      );
    } catch (e) {
      return Response.internalServerError(body: 'Range request error: ${e.toString()}');
    }
  }

  Future<Response> _handleStaticRequest(Request request) async {
    try {
      // Use shelf_static to serve static files
      final staticHandler = createStaticHandler('assets/web');
      
      // For API endpoints, return appropriate responses
      if (request.url.path.startsWith('api/')) {
        return Response.json({'message': 'API endpoint not implemented yet'});
      }

      // For other static requests, use the static handler
      return await staticHandler(request);
    } catch (e) {
      return Response.internalServerError(body: 'Static file error: ${e.toString()}');
    }
  }

  String _getFilePath(String bookId, int fileIndex) {
    // TODO: Implement actual file path resolution based on book ID and file index
    // This is a placeholder implementation
    return '/path/to/downloads/$bookId/file_$fileIndex.mp3';
  }

  String getStreamUrl(String bookId, int fileIndex) {
    return 'http://$_host:$_port/stream?id=$bookId&file=$fileIndex';
  }
}

class StreamFailure extends Failure {
  const StreamFailure(super.message, [super.exception]);
}