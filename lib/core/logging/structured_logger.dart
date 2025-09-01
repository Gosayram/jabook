import 'dart:convert';
import 'dart:io';
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';
import '../errors/failures.dart';

class StructuredLogger {
  static const int _maxLogSize = 10 * 1024 * 1024; // 10MB
  static const int _maxLogFiles = 5;
  static const int _maxLogAgeDays = 7;

  File? _logFile;
  final String _logDirectory;
  final String _logFileName;

  StructuredLogger({
    String? logDirectory,
    String? logFileName,
  }) : _logDirectory = logDirectory ?? 'logs',
       _logFileName = logFileName ?? 'jabook.ndjson';

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
    } catch (e) {
      throw LoggingFailure('Failed to initialize logger: ${e.toString()}');
    }
  }

  Future<void> log({
    required String level,
    required String subsystem,
    required String message,
    String? cause,
    Map<String, dynamic>? extra,
  }) async {
    if (_logFile == null) {
      await initialize();
    }

    try {
      final logEntry = {
        'ts': DateTime.now().toIso8601String(),
        'level': level,
        'subsystem': subsystem,
        'msg': message,
        if (cause != null) 'cause': cause,
        if (extra != null) 'extra': extra,
      };

      final logLine = jsonEncode(logEntry);
      
      await _logFile!.writeAsString(
        '$logLine\n',
        mode: FileMode.append,
      );

      // Check if log rotation is needed after writing
      await _checkLogRotation();
    } catch (e) {
      throw LoggingFailure('Failed to write log: ${e.toString()}');
    }
  }

  Future<void> _checkLogRotation() async {
    if (_logFile == null) return;

    try {
      final fileSize = await _logFile!.length();
      
      if (fileSize > _maxLogSize) {
        await _rotateLogs();
      }
    } catch (e) {
      // Log rotation failure shouldn't prevent normal logging
      print('Log rotation failed: ${e.toString()}');
    }
  }

  Future<void> _rotateLogs() async {
    if (_logFile == null) return;

    try {
      final logDir = _logFile!.parent;
      final fileName = _logFile!.uri.pathSegments.last;
      final baseName = fileName.replaceAll('.ndjson', '');

      // Delete old log files if we have too many
      final allFiles = await logDir.list().where((entity) =>
        entity is File && entity.path.contains(baseName)).cast<File>().toList();
      
      // Sort by last modified date (newest first) - use lastModified if available
      try {
        allFiles.sort((a, b) {
          // Try to use lastModifiedSync first
          final aMod = a.statSync().modified;
          final bMod = b.statSync().modified;
          return bMod.compareTo(aMod);
        });
      } catch (e) {
        // Fallback to simple name-based sorting
        allFiles.sort((a, b) => b.path.compareTo(a.path));
      }

      for (int i = _maxLogFiles; i < allFiles.length; i++) {
        await allFiles[i].delete();
      }

      // Rename current log file with timestamp
      final timestamp = DateTime.now().millisecondsSinceEpoch;
      final backupFile = File('${logDir.path}/$baseName.$timestamp.ndjson');
      await _logFile!.rename(backupFile.path);

      // Create new log file
      _logFile = File('${logDir.path}/$baseName.ndjson');
    } catch (e) {
      throw LoggingFailure('Failed to rotate logs: ${e.toString()}');
    }
  }

  Future<void> cleanOldLogs() async {
    if (_logFile == null) return;

    try {
      final logDir = _logFile!.parent;
      final cutoffDate = DateTime.now().subtract(Duration(days: _maxLogAgeDays));
      
      final logFiles = logDir.listSync()
        ..whereType<File>()
        ..where((file) => file.path.contains(_logFileName));

      for (final file in logFiles) {
        try {
          final modifiedDate = file.statSync().modified;
          if (modifiedDate.isBefore(cutoffDate)) {
            await file.delete();
          }
        } catch (e) {
          // Skip files that can't be accessed
          continue;
        }
      }
    } catch (e) {
      throw LoggingFailure('Failed to clean old logs: ${e.toString()}');
    }
  }

  Future<String> exportLogs() async {
    if (_logFile == null) {
      await initialize();
    }

    try {
      final content = await _logFile!.readAsString();
      return content;
    } catch (e) {
      throw LoggingFailure('Failed to export logs: ${e.toString()}');
    }
  }

  Future<void> shareLogs() async {
    try {
      final logContent = await exportLogs();
      final tempDir = await getTemporaryDirectory();
      final tempFile = File('${tempDir.path}/jabook_logs.txt');
      
      await tempFile.writeAsString(logContent);
      
      await Share.shareXFiles(
        [XFile(tempFile.path, name: 'jabook_logs.txt')],
        subject: 'JaBook Logs',
      );
    } catch (e) {
      throw LoggingFailure('Failed to share logs: ${e.toString()}');
    }
  }

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
      final lines = content.split('\n').where((line) => line.isNotEmpty).toList();
      
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
        } catch (e) {
          // Skip malformed log entries
          continue;
        }
      }
      
      return logs;
    } catch (e) {
      throw LoggingFailure('Failed to read logs: ${e.toString()}');
    }
  }
}

class LoggingFailure extends Failure {
  const LoggingFailure(String message, [Exception? exception])
      : super(message, exception);
}