import 'dart:async';

import 'package:jabook/core/logging/environment_logger.dart';

/// Safely executes a future without awaiting it, with error handling.
///
/// This is useful for fire-and-forget operations where you don't want
/// to block the current execution but still need to handle errors.
///
/// Example:
/// ```dart
/// safeUnawaited(
///   someAsyncOperation(),
///   onError: (e, stack) {
///     logger.w('Operation failed: $e');
///   },
/// );
/// ```
///
/// The [future] parameter is the future to execute.
/// The [onError] callback is called if the future completes with an error.
/// If [onError] is not provided, errors are logged using [EnvironmentLogger].
void safeUnawaited<T>(
  Future<T> future, {
  void Function(Object error, StackTrace stackTrace)? onError,
}) {
  future.then(
    (_) {
      // Success - nothing to do
    },
    onError: (error, stackTrace) {
      if (onError != null) {
        onError(error, stackTrace);
      } else {
        // Default error handling - log to environment logger
        logger.w('Unhandled error in fire-and-forget operation: $error',
            stackTrace: stackTrace);
      }
    },
  );
}
