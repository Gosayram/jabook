import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/app/app.dart';
import 'package:jabook/core/logging/environment_logger.dart';

void main() {
  // Setup global error handling
  FlutterError.onError = (details) {
    // Handle MissingPluginException gracefully - don't treat as critical error
    if (details.exception is MissingPluginException) {
      logger.w(
        'Missing plugin: ${details.exception}',
        stackTrace: details.stack,
      );
      // Don't show error UI for missing plugins - graceful degradation
      return;
    }

    logger.e('Flutter error: ${details.exception}', stackTrace: details.stack);
    // In debug mode, show error details
    if (kDebugMode) {
      FlutterError.presentError(details);
    }
  };

  // Setup Dart error handling
  PlatformDispatcher.instance.onError = (error, stack) {
    // Handle MissingPluginException gracefully
    if (error is MissingPluginException) {
      logger.w('Missing plugin in platform error: $error', stackTrace: stack);
      return true; // Handled, don't crash
    }

    logger.e('Unhandled Dart error: $error', stackTrace: stack);
    return true;
  };

  runZonedGuarded(() {
    runApp(const ProviderScope(child: JaBookApp()));
  }, (error, stackTrace) {
    // Handle MissingPluginException gracefully
    if (error is MissingPluginException) {
      logger.w('Missing plugin in zone error: $error', stackTrace: stackTrace);
      return; // Handled, don't crash
    }

    logger.e('Unhandled zone error: $error', stackTrace: stackTrace);
    // Try to recover from non-critical errors
    if (error is StateError && error.message.contains('mounted')) {
      logger.w('StateError with mounted check, attempting recovery');
      return;
    }
  });
}
