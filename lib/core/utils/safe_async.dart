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
