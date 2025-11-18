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

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/app/app.dart';
import 'package:jabook/core/logging/environment_logger.dart';
import 'package:jabook/core/net/dio_client.dart';

void main() {
  // Initialize Flutter bindings first - required before accessing PaintingBinding
  WidgetsFlutterBinding.ensureInitialized();
  
  // Wrap everything in try-catch to prevent crashes during initialization
  try {
    // Record app start time for performance metrics
    final appStartTime = DateTime.now();
    DioClient.appStartTime = appStartTime;
    
    // Enable performance optimizations before runApp
    _enablePerformanceOptimizations();
  } on Exception catch (e) {
    // If initialization fails, try to continue anyway
    // This is especially important on new Android versions
    logger.w('Error during main initialization: $e');
  }
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

    // Log detailed error information for null check operator errors
    if (error.toString().contains('Null check operator used on a null value')) {
      logger.e(
        'Null check operator error: $error',
        stackTrace: stack,
        error: error,
      );
      // Try to continue execution instead of crashing
      return true;
    }

    logger.e('Unhandled Dart error: $error', stackTrace: stack);
    return true;
  };

  runZonedGuarded(() {
    // Add small delay before runApp to ensure Flutter engine is fully ready
    // This helps on new Android versions where initialization timing is critical
    Future.delayed(const Duration(milliseconds: 100), () {
      // Run app with performance optimizations
      runApp(
        const ProviderScope(
          child: JaBookApp(),
        ),
      );
    });
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

    // Log detailed error information for null check operator errors
    if (error.toString().contains('Null check operator used on a null value')) {
      logger.e(
        'Null check operator error in zone: $error',
        stackTrace: stackTrace,
        error: error,
      );
      // Try to continue execution instead of crashing
      return;
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
  // Ensure bindings are initialized (idempotent, safe to call multiple times)
  // After ensureInitialized(), PaintingBinding.instance is guaranteed to be non-null
  WidgetsFlutterBinding.ensureInitialized();
  
  // Get PaintingBinding instance - safe after ensureInitialized()
  final paintingBinding = PaintingBinding.instance;

  // Enable Impeller rendering engine (better performance on Android)
  // This is set via AndroidManifest meta-data when android folder is generated
  // But we can also optimize Dart-side rendering

  // Optimize image cache for better memory management
  if (!kDebugMode) {
    // In release mode, use more aggressive caching
    paintingBinding.imageCache.maximumSize = 100;
    paintingBinding.imageCache.maximumSizeBytes = 50 << 20; // 50 MB
  } else {
    // In debug mode, use smaller cache
    paintingBinding.imageCache.maximumSize = 50;
    paintingBinding.imageCache.maximumSizeBytes = 25 << 20; // 25 MB
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
