import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:jabook/app/router/app_router.dart';
import 'package:jabook/app/theme/app_theme.dart';
import 'package:jabook/core/config/app_config.dart';
import 'package:jabook/core/logging/environment_logger.dart';

/// Main application widget for JaBook audiobook player.
///
/// This widget serves as the root of the application and sets up
/// the Material Design app with routing and theming.
class JaBookApp extends ConsumerStatefulWidget {
  /// Creates a new instance of JaBookApp.
  ///
  /// The [key] parameter is used to control how one widget replaces another
  /// in the tree.
  const JaBookApp({super.key});

  @override
  ConsumerState<JaBookApp> createState() => _JaBookAppState();
}

class _JaBookAppState extends ConsumerState<JaBookApp> {
  final AppConfig _config = AppConfig();
  final EnvironmentLogger _logger = EnvironmentLogger();

  @override
  void initState() {
    super.initState();
    _initializeApp();
  }

  Future<void> _initializeApp() async {
    try {
      // Initialize logger
      _logger.initialize();
      
      // Log app initialization
      _logger.i('Initializing JaBook app...');
      _logger.i('Build flavor: ${_config.flavor}');
      _logger.i('App version: ${_config.appVersion}');
      _logger.i('API base URL: ${_config.apiBaseUrl}');
      _logger.i('Log level: ${_config.logLevel}');
      
      // Initialize configuration based on flavor
      await _initializeEnvironment();
      
      _logger.i('App initialization complete');
    } catch (e, stackTrace) {
      _logger.e('Failed to initialize app', error: e, stackTrace: stackTrace);
      // In a real app, you might want to show an error screen here
    }
  }

  Future<void> _initializeEnvironment() async {
    switch (_config.flavor) {
      case 'dev':
        await _initializeDevEnvironment();
        break;
      case 'stage':
        await _initializeStageEnvironment();
        break;
      case 'prod':
        await _initializeProdEnvironment();
        break;
      default:
        _logger.w('Unknown flavor: ${_config.flavor}, falling back to dev');
        await _initializeDevEnvironment();
    }
  }

  Future<void> _initializeDevEnvironment() async {
    _logger.i('Initializing development environment');
    
    // Enable debug features
    if (_config.debugFeaturesEnabled) {
      _logger.i('Debug features enabled');
    }
    
    // Set up development-specific configurations
    // TODO: Add dev-specific setup
  }

  Future<void> _initializeStageEnvironment() async {
    _logger.i('Initializing stage environment');
    
    // Enable analytics in stage
    if (_config.analyticsEnabled) {
      _logger.i('Analytics enabled for stage environment');
      // TODO: Initialize analytics
    }
    
    // Enable crash reporting in stage
    if (_config.crashReportingEnabled) {
      _logger.i('Crash reporting enabled for stage environment');
      // TODO: Initialize crash reporting
    }
  }

  Future<void> _initializeProdEnvironment() async {
    _logger.i('Initializing production environment');
    
    // Enable analytics in production
    if (_config.analyticsEnabled) {
      _logger.i('Analytics enabled for production environment');
      // TODO: Initialize analytics
    }
    
    // Enable crash reporting in production
    if (_config.crashReportingEnabled) {
      _logger.i('Crash reporting enabled for production environment');
      // TODO: Initialize crash reporting
    }
    
    // Disable debug features in production
    if (!_config.debugFeaturesEnabled) {
      _logger.i('Debug features disabled for production environment');
    }
  }

  @override
  Widget build(BuildContext context) {
    final router = ref.watch(appRouterProvider);
    
    return MaterialApp.router(
      title: _config.appName,
      theme: AppTheme.lightTheme,
      darkTheme: AppTheme.darkTheme,
      themeMode: ThemeMode.system,
      routerConfig: router,
      debugShowCheckedModeBanner: _config.isDebug,
      // Show scaffold in debug mode for better debugging
      scaffoldMessengerKey: _config.isDebug ? GlobalKey<ScaffoldMessengerState>() : null,
    );
  }
}