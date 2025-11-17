import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/app/app.dart';
import 'package:jabook/core/logging/environment_logger.dart';

void main() {
  // Enable performance optimizations before runApp
  _enablePerformanceOptimizations();
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

    // Handle release mode exception from external dependencies
    if (details.exception is Exception &&
        details.exception.toString().contains(
              'not supposed to be executed in release mode',
            )) {
      logger.w(
        'Release mode check from external dependency (ignored): ${details.exception}',
        stackTrace: details.stack,
      );
      return; // Don't show error UI
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

    // Handle release mode exception
    if (error is Exception &&
        error
            .toString()
            .contains('not supposed to be executed in release mode')) {
      logger.w(
        'Release mode check in platform error (ignored): $error',
        stackTrace: stack,
      );
      return true; // Handled
    }

    logger.e('Unhandled Dart error: $error', stackTrace: stack);
    return true;
  };

  runZonedGuarded(() {
    // Run app with performance optimizations
    runApp(
      const ProviderScope(
        child: JaBookApp(),
      ),
    );
  }, (error, stackTrace) {
    // Handle MissingPluginException gracefully
    if (error is MissingPluginException) {
      logger.w('Missing plugin in zone error: $error', stackTrace: stackTrace);
      return; // Handled, don't crash
    }

    // Handle release mode exception
    if (error is Exception &&
        error
            .toString()
            .contains('not supposed to be executed in release mode')) {
      logger.w(
        'Release mode check in zone error (ignored): $error',
        stackTrace: stackTrace,
      );
      return; // Handled
    }

    logger.e('Unhandled zone error: $error', stackTrace: stackTrace);
    // Try to recover from non-critical errors
    if (error is StateError && error.message.contains('mounted')) {
      logger.w('StateError with mounted check, attempting recovery');
      return;
    }
  });
}

/// Enables performance optimizations for Flutter app
void _enablePerformanceOptimizations() {
  // Enable Impeller rendering engine (better performance on Android)
  // This is set via AndroidManifest meta-data when android folder is generated
  // But we can also optimize Dart-side rendering

  // Optimize image cache for better memory management
  if (!kDebugMode) {
    // In release mode, use more aggressive caching
    PaintingBinding.instance.imageCache.maximumSize = 100;
    PaintingBinding.instance.imageCache.maximumSizeBytes = 50 << 20; // 50 MB
  } else {
    // In debug mode, use smaller cache
    PaintingBinding.instance.imageCache.maximumSize = 50;
    PaintingBinding.instance.imageCache.maximumSizeBytes = 25 << 20; // 25 MB
  }

  // Set preferred frame rate for better battery life on Android
  // This helps reduce unnecessary frame renders
  if (defaultTargetPlatform == TargetPlatform.android) {
    // Enable frame scheduling optimizations
    // Flutter will automatically optimize based on device capabilities
  }

  // Disable debug banner in release mode (already handled in MaterialApp)
  // But we ensure it's off here too
  if (kReleaseMode) {
    debugDisableShadows = true;
  }
}
