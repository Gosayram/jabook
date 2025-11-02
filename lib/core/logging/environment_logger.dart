import 'dart:developer' as developer;
import 'package:flutter/foundation.dart';

/// Environment-aware logger for the application.
///
/// This logger provides different log levels based on the build flavor
/// and supports both debug and release logging.
class EnvironmentLogger {
  /// Private constructor for singleton pattern.
  EnvironmentLogger._();

  /// Factory constructor to get the instance.
  factory EnvironmentLogger() => _instance;

  static final EnvironmentLogger _instance = EnvironmentLogger._();

  /// Logs a verbose message.
  void v(String message, {dynamic error, StackTrace? stackTrace}) {
    _log('VERBOSE', message, error: error, stackTrace: stackTrace);
  }

  /// Logs a debug message.
  void d(String message, {dynamic error, StackTrace? stackTrace}) {
    _log('DEBUG', message, error: error, stackTrace: stackTrace);
  }

  /// Logs an info message.
  void i(String message, {dynamic error, StackTrace? stackTrace}) {
    _log('INFO', message, error: error, stackTrace: stackTrace);
  }

  /// Logs a warning message.
  void w(String message, {dynamic error, StackTrace? stackTrace}) {
    _log('WARNING', message, error: error, stackTrace: stackTrace);
  }

  /// Logs an error message.
  void e(String message, {dynamic error, StackTrace? stackTrace}) {
    _log('ERROR', message, error: error, stackTrace: stackTrace);
  }

  /// Logs a fatal error message.
  void wtf(String message, {dynamic error, StackTrace? stackTrace}) {
    _log('WTF', message, error: error, stackTrace: stackTrace);
  }

  /// Internal logging method that respects environment settings.
  void _log(
    String level,
    String message, {
    dynamic error,
    StackTrace? stackTrace,
  }) {
    // Get log level from environment
    final logLevel = _getLogLevel();

    // Check if this message should be logged based on level
    if (!_shouldLog(level, logLevel)) {
      return;
    }

    // Format the log message
    final timestamp = DateTime.now().toIso8601String();
    final formattedMessage = '[$timestamp] [$level] $message';

    // Log based on platform
    if (kDebugMode) {
      // Debug mode - use developer.log for better IDE integration
      developer.log(
        message,
        time: DateTime.now(),
        error: error,
        stackTrace: stackTrace,
        name: 'JaBook',
      );
    } else {
      // Release mode - use developer.log for consistency
      developer.log(formattedMessage, name: 'JaBook');
      if (error != null) {
        developer.log('Error: $error', name: 'JaBook');
      }
      if (stackTrace != null) {
        developer.log('Stack trace: $stackTrace', name: 'JaBook');
      }
    }

    // In production, send errors to crash reporting service
    if (!_isDebugMode() && (level == 'ERROR' || level == 'WTF')) {
      _sendToCrashReporting(level, message, error, stackTrace);
    }
  }

  /// Determines if a log level should be logged based on environment settings.
  bool _shouldLog(String messageLevel, String environmentLevel) {
    const levels = ['VERBOSE', 'DEBUG', 'INFO', 'WARNING', 'ERROR', 'WTF'];

    final messageIndex = levels.indexOf(messageLevel);
    final environmentIndex = levels.indexOf(environmentLevel);

    return messageIndex >= environmentIndex;
  }

  /// Gets the current log level from environment.
  String _getLogLevel() {
    // In debug mode, always show all logs
    if (kDebugMode) {
      return 'VERBOSE';
    }

    // Get from environment configuration
    const logLevel = String.fromEnvironment('LOG_LEVEL', defaultValue: 'INFO');
    return logLevel;
  }

  /// Checks if we're in debug mode.
  bool _isDebugMode() => kDebugMode;

  /// Sends error information to crash reporting service.
  void _sendToCrashReporting(
    String level,
    String message,
    dynamic error,
    StackTrace? stackTrace,
  ) {
    // TODO: Implement actual crash reporting integration
    // This could integrate with Firebase Crashlytics, Sentry, or other services

    // For now, just log to console in release builds
    developer.log('[CRASH_REPORTING] Level: $level, Message: $message',
        name: 'JaBook');
    if (error != null) {
      developer.log('[CRASH_REPORTING] Error: $error', name: 'JaBook');
    }
    if (stackTrace != null) {
      developer.log('[CRASH_REPORTING] Stack trace: $stackTrace',
          name: 'JaBook');
    }
  }

  /// Initializes the logger for the current environment.
  void initialize() {
    if (kDebugMode) {
      // In debug mode, enable verbose logging
      developer.log('Logger initialized in debug mode', name: 'JaBook');
    } else {
      // In release mode, log initialization at info level
      developer.log('Logger initialized in release mode', name: 'JaBook');
    }
  }

  /// Clears all cached logs (if any).
  void clearLogs() =>
      // TODO: Implement log clearing if using persistent logging
      {};

  /// Gets the current log level as a user-friendly string.
  String getLogLevelName() {
    final level = _getLogLevel();
    switch (level) {
      case 'VERBOSE':
        return 'Verbose';
      case 'DEBUG':
        return 'Debug';
      case 'INFO':
        return 'Info';
      case 'WARNING':
        return 'Warning';
      case 'ERROR':
        return 'Error';
      case 'WTF':
        return 'WTF';
      default:
        return 'Unknown';
    }
  }
}

/// Global logger instance for easy access.
final logger = EnvironmentLogger();
