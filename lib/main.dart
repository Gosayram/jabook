import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/app/app.dart';
import 'package:jabook/core/logging/environment_logger.dart';

void main() {
  // Setup global error handling
  FlutterError.onError = (details) {
    logger.e('Flutter error: ${details.exception}', stackTrace: details.stack);
    // In debug mode, show error details
    if (kDebugMode) {
      FlutterError.presentError(details);
    }
  };

  // Setup Dart error handling
  PlatformDispatcher.instance.onError = (error, stack) {
    logger.e('Unhandled Dart error: $error', stackTrace: stack);
    return true;
  };

  runZonedGuarded(() {
    runApp(const ProviderScope(child: JaBookApp()));
  }, (error, stackTrace) {
    logger.e('Unhandled zone error: $error', stackTrace: stackTrace);
    // Try to recover from non-critical errors
    if (error is StateError && error.message.contains('mounted')) {
      logger.w('StateError with mounted check, attempting recovery');
      return;
    }
  });
}
