import 'dart:async';
import 'dart:io';
import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/services.dart';
import 'package:jabook/app/app.dart';
import 'package:jabook/core/logging/environment_logger.dart';

void main() {
  // Setup global error handling
  FlutterError.onError = (FlutterErrorDetails details) {
    logger.e('Flutter error: ${details.exception}', stackTrace: details.stack);
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
  });
}