import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart' show debugPrint; // debugPrint
import 'package:jabook/core/errors/failures.dart';
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';

/// A structured logger that writes logs to files in NDJSON format.
///
/// This logger provides structured logging with rotation, filtering,
/// and export capabilities. Logs are written to the application's
/// documents directory in NDJSON format for easy parsing and analysis.
class StructuredLogger {
  /// Creates a new StructuredLogger instance.
  ///
  /// The [logDirectory] parameter specifies the directory where logs will be stored.
  /// If not provided, defaults to 'logs'.
  /// The [logFileName] parameter specifies the name of the log file.
  /// If not provided, defaults to 'jabook.ndjson'.
  StructuredLogger({
    String? logDirectory,
    String? logFileName,
  })  : _logDirectory = logDirectory ?? 'logs',
        _logFileName = logFileName ?? 'jabook.ndjson';

  /// Maximum size of a log file before rotation (10MB).
  static const int _maxLogSize = 10 * 1024 * 1024; // 10MB

  /// Maximum number of log files to keep.
  static const int _maxLogFiles = 5;

  /// Maximum age of log files in days.
  static const int _maxLogAgeDays = 7;

  /// Minimum time between identical log messages (1 minute).
  static const Duration _deduplicationWindow = Duration(minutes: 1);

  /// Current log file being written to.
  File? _logFile;

  /// Directory where log files are stored.
  final String _logDirectory;

  /// Name of the log file.
  final String _logFileName;

  /// Cache for deduplication: key is message signature, value is last log time and count
  static final Map<String, ({DateTime lastTime, int count})>
      _deduplicationCache = {};

  /// Last cleanup time for deduplication cache
  static DateTime _lastCacheCleanup = DateTime.now();

  /// Cleanup interval for deduplication cache (5 minutes)
  static const Duration _cacheCleanupInterval = Duration(minutes: 5);

  /// Initializes the logger by creating the log directory and file.
  Future<void> initialize() async {
    try {
      final appDocDir = await getApplicationDocumentsDirectory();
      final logDir = Directory('${appDocDir.path}/$_logDirectory');

      if (!await logDir.exists()) {
        await logDir.create(recursive: true);
      }

      _logFile = File('${logDir.path}/$_logFileName');

      // Check if log rotation is needed
      await _checkLogRotation();
    } on Exception {
      throw const LoggingFailure('Failed to initialize logger');
    }
  }

  /// Logs a message with the specified level and subsystem.
  ///
  /// [operation_id] - Unique identifier for grouping related log entries
  /// [duration_ms] - Duration of the operation in milliseconds
  /// [context] - Additional context about what the application is doing
  /// [state_before] - State before the operation (for state transitions)
  /// [state_after] - State after the operation (for state transitions)
  Future<void> log({
    required String level,
    required String subsystem,
    required String message,
    String? cause,
    Map<String, dynamic>? extra,
    String? operationId,
    int? durationMs,
    String? context,
    Map<String, dynamic>? stateBefore,
    Map<String, dynamic>? stateAfter,
  }) async {
    if (_logFile == null) {
      await initialize();
    }

    try {
      // Check deduplication for error/warning messages
      final shouldDeduplicate = level == 'error' || level == 'warning';
      if (shouldDeduplicate) {
        final signature = _createLogSignature(
          level: level,
          subsystem: subsystem,
          message: message,
          cause: cause,
          extra: extra,
        );

        final now = DateTime.now();
        final cached = _deduplicationCache[signature];

        // Cleanup cache periodically
        if (now.difference(_lastCacheCleanup) > _cacheCleanupInterval) {
          _cleanupDeduplicationCache();
          _lastCacheCleanup = now;
        }

        if (cached != null) {
          final timeSinceLastLog = now.difference(cached.lastTime);

          // If logged recently, increment count but don't log
          if (timeSinceLastLog < _deduplicationWindow) {
            _deduplicationCache[signature] = (
              lastTime: cached.lastTime,
              count: cached.count + 1,
            );
            // Suppress duplicate log
            return;
          }

          // If logged before but outside window, log with count
          if (cached.count > 0) {
            final logEntry = {
              'ts': now.toIso8601String(),
              'level': level,
              'subsystem': subsystem,
              'msg': _scrubSensitiveData(message),
              if (cause != null) 'cause': _scrubSensitiveData(cause),
              if (operationId != null) 'operation_id': operationId,
              if (durationMs != null) 'duration_ms': durationMs,
              if (context != null) 'context': context,
              if (stateBefore != null) 'state_before': _scrubMap(stateBefore),
              if (stateAfter != null) 'state_after': _scrubMap(stateAfter),
              if (extra != null)
                'extra': {
                  ..._scrubMap(extra),
                  '_deduplication_count': cached.count,
                  '_deduplication_note':
                      'This message was suppressed ${cached.count} time(s) in the last minute',
                },
            };

            final logLine = jsonEncode(logEntry);
            await _logFile!.writeAsString(
              '$logLine\n',
              mode: FileMode.append,
            );

            // Reset count after logging
            _deduplicationCache[signature] = (lastTime: now, count: 0);
            await _checkLogRotation();
            return;
          }
        }

        // First time or outside deduplication window - log normally
        _deduplicationCache[signature] = (lastTime: now, count: 0);
      }

      // Normal logging
      final logEntry = {
        'ts': DateTime.now().toIso8601String(),
        'level': level,
        'subsystem': subsystem,
        'msg': _scrubSensitiveData(message),
        if (cause != null) 'cause': _scrubSensitiveData(cause),
        if (operationId != null) 'operation_id': operationId,
        if (durationMs != null) 'duration_ms': durationMs,
        if (context != null) 'context': context,
        if (stateBefore != null) 'state_before': _scrubMap(stateBefore),
        if (stateAfter != null) 'state_after': _scrubMap(stateAfter),
        if (extra != null) 'extra': _scrubMap(extra),
      };

      final logLine = jsonEncode(logEntry);

      await _logFile!.writeAsString(
        '$logLine\n',
        mode: FileMode.append,
      );

      // Check if log rotation is needed after writing
      await _checkLogRotation();
    } on Exception {
      throw const LoggingFailure('Failed to write log');
    }
  }

  /// Creates a signature for log deduplication.
  ///
  /// Uses level, subsystem, message, and simplified extra data to identify
  /// duplicate log entries.
  String _createLogSignature({
    required String level,
    required String subsystem,
    required String message,
    String? cause,
    Map<String, dynamic>? extra,
  }) {
    // Create a simplified signature from key fields
    final keyFields = <String, dynamic>{
      'level': level,
      'subsystem': subsystem,
      'message': message,
      if (cause != null) 'cause': cause,
    };

    // Add simplified extra fields for common error patterns
    if (extra != null) {
      // Include error_type, url, endpoint, is_dns_error for deduplication
      if (extra.containsKey('error_type')) {
        keyFields['error_type'] = extra['error_type'];
      }
      if (extra.containsKey('url')) {
        keyFields['url'] = extra['url'];
      }
      if (extra.containsKey('endpoint')) {
        keyFields['endpoint'] = extra['endpoint'];
      }
      if (extra.containsKey('is_dns_error')) {
        keyFields['is_dns_error'] = extra['is_dns_error'];
      }
    }

    return jsonEncode(keyFields);
  }

  /// Cleans up old entries from deduplication cache.
  ///
  /// Removes entries older than the deduplication window.
  static void _cleanupDeduplicationCache() {
    final now = DateTime.now();
    final keysToRemove = <String>[];

    _deduplicationCache.forEach((key, value) {
      if (now.difference(value.lastTime) > _deduplicationWindow) {
        keysToRemove.add(key);
      }
    });

    for (final key in keysToRemove) {
      _deduplicationCache.remove(key);
    }
  }

  // Redacts sensitive tokens, cookies, emails, magnets, and long IDs from strings
  String _scrubSensitiveData(String input) {
    var out = input;
    // Cookies
    out = out.replaceAll(
        RegExp(r'(cookie\s*:\s*)([^;\n]+)', caseSensitive: false),
        r'$1<redacted>');
    // Authorization headers / tokens
    out = out.replaceAll(
        RegExp(r'(authorization|token|bearer)\s*[:=]\s*([^\s;]+)',
            caseSensitive: false),
        r'$1=<redacted>');
    // Magnet links
    out =
        out.replaceAll(RegExp(r'magnet:\?xt=urn:[^\s]+'), 'magnet:<redacted>');
    // Email addresses
    out = out.replaceAll(RegExp(r'[\w\.-]+@[\w\.-]+'), '<redacted-email>');
    // Long hex/base64-like IDs
    out = out.replaceAll(
        RegExp(r'\b[a-f0-9]{24,}\b', caseSensitive: false), '<redacted-id>');
    out = out.replaceAll(RegExp(r'\b[\w+/=]{32,}\b'), '<redacted-id>');
    return out;
  }

  Map<String, dynamic> _scrubMap(Map<String, dynamic> input) {
    final result = <String, dynamic>{};
    input.forEach((key, value) {
      final lowerKey = key.toLowerCase();
      final isSensitiveKey = lowerKey.contains('cookie') ||
          lowerKey.contains('authorization') ||
          lowerKey.contains('token') ||
          lowerKey.contains('password') ||
          lowerKey.contains('set-cookie');

      if (isSensitiveKey) {
        result[key] = '<redacted>';
      } else if (value is String) {
        result[key] = _scrubSensitiveData(value);
      } else if (value is Map<String, dynamic>) {
        result[key] = _scrubMap(value);
      } else if (value is Map) {
        result[key] = _scrubMap(value.map((k, v) => MapEntry(k.toString(), v)));
      } else if (value is List) {
        result[key] = value
            .map((e) => e is String
                ? _scrubSensitiveData(e)
                : e is Map
                    ? _scrubMap(e.map((k, v) => MapEntry(k.toString(), v)))
                    : e)
            .toList();
      } else {
        result[key] = value;
      }
    });
    return result;
  }

  /// Checks if log rotation is needed and performs it if necessary.
  Future<void> _checkLogRotation() async {
    if (_logFile == null) return;

    try {
      final fileSize = await _logFile!.length();

      if (fileSize > _maxLogSize) {
        await _rotateLogs();
      }
    } on Exception {
      // Log rotation failure shouldn't prevent normal logging
      // ignore: avoid_print
      print('Log rotation failed');
    }
  }

  /// Rotates log files when they exceed the maximum size.
  Future<void> _rotateLogs() async {
    if (_logFile == null) return;

    try {
      final logDir = _logFile!.parent;
      final fileName = _logFile!.uri.pathSegments.last;
      final baseName = fileName.replaceAll('.ndjson', '');

      // Delete old log files if we have too many
      final allFiles = await logDir
          .list()
          .where((entity) => entity is File && entity.path.contains(baseName))
          .cast<File>()
          .toList();

      // Sort by last modified date (newest first)
      try {
        allFiles.sort((a, b) {
          final aMod = a.statSync().modified;
          final bMod = b.statSync().modified;
          return bMod.compareTo(aMod);
        });
      } on Exception {
        // Fallback to simple name-based sorting
        allFiles.sort((a, b) => b.path.compareTo(a.path));
      }

      for (var i = _maxLogFiles; i < allFiles.length; i++) {
        await allFiles[i].delete();
      }

      // Rename current log file with timestamp
      final timestamp = DateTime.now().millisecondsSinceEpoch;
      final backupFile = File('${logDir.path}/$baseName.$timestamp.ndjson');
      await _logFile!.rename(backupFile.path);

      // Create new log file
      _logFile = File('${logDir.path}/$baseName.ndjson');
    } on Exception {
      throw const LoggingFailure('Failed to rotate logs');
    }
  }

  /// Removes log files older than the maximum age.
  Future<void> cleanOldLogs() async {
    if (_logFile == null) return;

    try {
      final logDir = _logFile!.parent;
      final cutoffDate =
          DateTime.now().subtract(const Duration(days: _maxLogAgeDays));

      final entries = logDir.listSync();
      for (final entity in entries) {
        if (entity is! File) continue;
        if (!entity.path.contains(_logFileName)) continue;

        try {
          final modifiedDate = entity.statSync().modified;
          if (modifiedDate.isBefore(cutoffDate)) {
            await entity.delete();
          }
        } on Exception {
          // Skip files that can't be accessed
          continue;
        }
      }
    } on Exception {
      throw const LoggingFailure('Failed to clean old logs');
    }
  }

  /// Exports all log content as a string.
  Future<String> exportLogs() async {
    if (_logFile == null) {
      await initialize();
    }

    try {
      final content = await _logFile!.readAsString();
      return content;
    } on Exception {
      throw const LoggingFailure('Failed to export logs');
    }
  }

  /// Shares the log files using the share plugin.
  Future<void> shareLogs() async {
    try {
      final logContent = await exportLogs();
      final tempDir = await getTemporaryDirectory();
      final tempFile = File('${tempDir.path}/jabook_logs.txt');

      await tempFile.writeAsString(logContent, flush: true);

      // New API: SharePlus.instance.share with ShareParams
      final params = ShareParams(
        files: [XFile(tempFile.path)],
        subject: 'JaBook Logs',
        text: 'Here are the exported JaBook logs.',
        // tip: on iPad you should also pass sharePositionOrigin from a widget context
      );

      await SharePlus.instance.share(params);
    } on Exception catch (e, stack) {
      debugPrint('Error sharing logs: $e\n$stack');
      throw const LoggingFailure('Failed to share logs');
    }
  }

  /// Retrieves log entries with optional filtering.
  Future<List<Map<String, dynamic>>> getLogs({
    String? level,
    String? subsystem,
    DateTime? startDate,
    DateTime? endDate,
    int limit = 100,
  }) async {
    if (_logFile == null) {
      await initialize();
    }

    try {
      final content = await _logFile!.readAsString();
      final lines =
          content.split('\n').where((line) => line.isNotEmpty).toList();

      final logs = <Map<String, dynamic>>[];

      for (final line in lines.reversed) {
        if (logs.length >= limit) break;

        try {
          final logEntry = jsonDecode(line) as Map<String, dynamic>;

          // Apply filters
          if (level != null && logEntry['level'] != level) continue;
          if (subsystem != null && logEntry['subsystem'] != subsystem) continue;
          if (startDate != null) {
            final logDate = DateTime.parse(logEntry['ts'] as String);
            if (logDate.isBefore(startDate)) continue;
          }
          if (endDate != null) {
            final logDate = DateTime.parse(logEntry['ts'] as String);
            if (logDate.isAfter(endDate)) continue;
          }

          logs.add(logEntry);
        } on Exception {
          // Skip malformed log entries
          continue;
        }
      }

      return logs;
    } on Exception {
      throw const LoggingFailure('Failed to read logs');
    }
  }
}

/// A failure that occurs during logging operations.
class LoggingFailure extends Failure implements Exception {
  /// Creates a new LoggingFailure instance.
  ///
  /// [message] describes the error; [exception] can hold an underlying cause.
  const LoggingFailure(super.message, [super.exception]);
}
