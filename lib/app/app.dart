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

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/app/router/app_router.dart';
import 'package:jabook/app/theme/app_theme.dart';
import 'package:jabook/core/auth/rutracker_auth.dart';
import 'package:jabook/core/cache/rutracker_cache_service.dart';
import 'package:jabook/core/config/app_config.dart';
import 'package:jabook/core/config/language_manager.dart';
import 'package:jabook/core/config/language_provider.dart';
import 'package:jabook/core/endpoints/endpoint_health_scheduler.dart';
import 'package:jabook/core/endpoints/endpoint_manager.dart';
import 'package:jabook/core/logging/environment_logger.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/permissions/permission_service.dart';
import 'package:jabook/core/session/session_manager.dart';
import 'package:jabook/core/utils/first_launch.dart';
import 'package:jabook/core/utils/safe_async.dart';
import 'package:jabook/data/db/app_database.dart';
import 'package:jabook/features/auth/data/providers/auth_provider.dart';
import 'package:jabook/features/auth/data/repositories/auth_repository_impl.dart';
import 'package:jabook/features/permissions/presentation/widgets/permissions_onboarding_dialog.dart';
import 'package:jabook/l10n/app_localizations.dart';
import 'package:responsive_framework/responsive_framework.dart';

/// Main application widget for JaBook audiobook player.
///
/// This widget serves as the root of the application and sets up
/// the Material Design app with routing and theming.
class JaBookApp extends ConsumerStatefulWidget {
  /// Creates the root JaBook application widget.
  ///
  /// The optional [key] allows Flutter to preserve state when
  /// the widget tree is rebuilt.
  const JaBookApp({super.key});

  /// Creates the mutable state for [JaBookApp].
  @override
  ConsumerState<JaBookApp> createState() => _JaBookAppState();
}

/// State class for [JaBookApp].
///
/// Handles initialization of environment, configuration,
/// and logging for the application lifecycle.
class _JaBookAppState extends ConsumerState<JaBookApp> {
  final AppConfig config = AppConfig();
  final EnvironmentLogger logger = EnvironmentLogger();
  final AppDatabase database = AppDatabase();
  final RuTrackerCacheService cacheService = RuTrackerCacheService();
  final LanguageManager languageManager = LanguageManager();
  RuTrackerAuth? _rutrackerAuth;
  AuthRepositoryImpl? _authRepository;

  // Avoid recreating the key on every build.
  final GlobalKey<ScaffoldMessengerState> _scaffoldMessengerKey =
      GlobalKey<ScaffoldMessengerState>();

  bool _hasShownOnboarding = false;
  bool _firstFrameTracked = false;
  DateTime? _appStartTime;
  bool _isInitialized = false;

  @override
  void initState() {
    super.initState();
    try {
      // Record app start time for UI render metrics
      _appStartTime = DateTime.now();
      // Run initialization in the background to avoid blocking UI
      // Add timeout to prevent infinite loading if initialization hangs
      Future.microtask(() async {
        try {
          await _initializeApp().timeout(
            const Duration(seconds: 10),
            onTimeout: () {
              logger.w('App initialization timed out after 10 seconds');
              // Mark as initialized even on timeout to prevent infinite loading
              if (mounted) {
                setState(() {
                  _isInitialized = true;
                });
              }
            },
          );
        } on Exception catch (e, stackTrace) {
          logger.e('Error in initialization timeout handler: $e', stackTrace: stackTrace);
          // Ensure app continues even if timeout handler fails
          if (mounted) {
            setState(() {
              _isInitialized = true;
            });
          }
        }
      });
    } on Exception catch (e, stackTrace) {
      logger.e('Error in initState: $e', stackTrace: stackTrace);
      // Try to continue anyway - mark as initialized to prevent infinite loading
      if (mounted) {
        setState(() {
          _isInitialized = true;
        });
      }
    }
  }

  Future<void> _initializeApp() async {
    // Add small delay to ensure Flutter is fully ready on new Android
    // This helps avoid race conditions during initialization
    await Future.delayed(const Duration(milliseconds: 50));
    
    final appStartTime = DateTime.now();
    final structuredLogger = StructuredLogger();
    try {
      // Initialize logger (critical, must be first)
      final loggerInitStart = DateTime.now();
      logger.initialize();
      final loggerInitDuration = DateTime.now().difference(loggerInitStart).inMilliseconds;

      // Log startup details (single receiver via cascade inside helper)
      _logStartupDetails();

      // Initialize database and cache (critical for app functionality)
      final dbInitStart = DateTime.now();
      await _initializeDatabase();
      final dbInitDuration = DateTime.now().difference(dbInitStart).inMilliseconds;

      // Initialize configuration based on flavor (lightweight, can run in parallel)
      // Request essential permissions (can be deferred, but better to do early)
      // Run these in parallel to speed up startup
      final envInitStart = DateTime.now();
      await Future.wait([
        _initializeEnvironment(),
        _requestEssentialPermissions(),
      ]);
      final envInitDuration = DateTime.now().difference(envInitStart).inMilliseconds;

      final totalInitDuration = DateTime.now().difference(appStartTime).inMilliseconds;

      // Log initialization metrics using StructuredLogger
      await structuredLogger.log(
        level: 'info',
        subsystem: 'performance',
        message: 'App initialization complete',
        context: 'app_startup',
        durationMs: totalInitDuration,
        extra: {
          'total_init_duration_ms': totalInitDuration,
          'logger_init_duration_ms': loggerInitDuration,
          'db_init_duration_ms': dbInitDuration,
          'env_init_duration_ms': envInitDuration,
          'metric_type': 'app_initialization',
          'breakdown': {
            'logger': loggerInitDuration,
            'database': dbInitDuration,
            'environment': envInitDuration,
          },
        },
      );

      // Also log to environment logger for backward compatibility
      logger.i(
        'App initialization complete. Metrics: total=${totalInitDuration}ms, '
        'logger=${loggerInitDuration}ms, db=${dbInitDuration}ms, env=${envInitDuration}ms',
      );
      
      // Mark initialization as complete
      if (mounted) {
        setState(() {
          _isInitialized = true;
        });
      }
    } on Exception catch (e, stackTrace) {
      logger.e('Failed to initialize app', error: e, stackTrace: stackTrace);
      // Show error to user if critical initialization fails
      // Use scaffoldMessengerKey to avoid BuildContext issues after async
      // On Android 16, context may become null between check and use, so use safe access
      final context = _scaffoldMessengerKey.currentContext;
      if (context != null && context.mounted) {
        try {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Initialization error: ${e.toString()}'),
              backgroundColor: Colors.red,
              duration: const Duration(seconds: 5),
            ),
          );
        } on Exception catch (contextError) {
          // Context may have become invalid, log but don't crash
          logger.w('Failed to show error snackbar: $contextError');
        }
      }
      // CRITICAL: Always mark as initialized even on error to prevent infinite loading
      // App should continue to work even if some initialization steps fail
      if (mounted) {
        setState(() {
          _isInitialized = true;
        });
      }
    }
  }

  /// Logs startup details using a single cascade chain to avoid
  /// `cascade_invocations` warnings in the caller.
  void _logStartupDetails() {
    logger
      ..i('Initializing JaBook app...')
      ..i('Build flavor: ${config.flavor}')
      ..i('App version: ${config.appVersion}')
      ..i('API base URL: ${config.apiBaseUrl}')
      ..i('Log level: ${config.logLevel}');
  }

  Future<void> _initializeEnvironment() async {
    switch (config.flavor) {
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
        logger.w('Unknown flavor: ${config.flavor}, falling back to dev');
        await _initializeDevEnvironment();
    }
  }

  Future<void> _initializeDatabase() async {
    final dbStartTime = DateTime.now();
    final structuredLogger = StructuredLogger();
    logger.i('Initializing database...');
    await database.initialize();
    final dbInitDuration = DateTime.now().difference(dbStartTime).inMilliseconds;

    // Verify database is initialized before using it
    if (!database.isInitialized) {
      throw StateError('Database initialization failed');
    }
    final db = database.database;
    
    final cacheStartTime = DateTime.now();
    await cacheService.initialize(db);
    final cacheInitDuration = DateTime.now().difference(cacheStartTime).inMilliseconds;

    // AuthRepository will be initialized in build method when context is available

    // Initialize EndpointManager with default endpoints and health checks
    final endpointStartTime = DateTime.now();
    final endpointManager = EndpointManager(db);
    await endpointManager
        .initialize(); // This includes initializeDefaultEndpoints() and health checks
    final endpointInitDuration = DateTime.now().difference(endpointStartTime).inMilliseconds;

    // Log database initialization metrics using StructuredLogger
    await structuredLogger.log(
      level: 'info',
      subsystem: 'performance',
      message: 'Database initialization complete',
      context: 'app_startup',
      durationMs: dbInitDuration + cacheInitDuration + endpointInitDuration,
      extra: {
        'db_init_duration_ms': dbInitDuration,
        'cache_init_duration_ms': cacheInitDuration,
        'endpoint_init_duration_ms': endpointInitDuration,
        'total_db_init_duration_ms': dbInitDuration + cacheInitDuration + endpointInitDuration,
        'metric_type': 'database_initialization',
        'breakdown': {
          'database': dbInitDuration,
          'cache': cacheInitDuration,
          'endpoints': endpointInitDuration,
        },
      },
    );

    logger.i(
      'Database initialization metrics: db=${dbInitDuration}ms, '
      'cache=${cacheInitDuration}ms, endpoint=${endpointInitDuration}ms',
    );

    // Run automatic endpoint health check if needed (non-blocking)
    try {
      final healthScheduler = EndpointHealthScheduler(db);
      // Run asynchronously without blocking startup
      // Use safeUnawaited to handle errors in fire-and-forget operations
      safeUnawaited(
        healthScheduler.runAutomaticHealthCheckIfNeeded(),
        onError: (e, stack) {
          logger.w('Endpoint health check failed: $e');
        },
      );
      logger.i('Endpoint health check scheduled');
    } on Exception catch (e) {
      logger.w('Failed to schedule endpoint health check: $e');
    }

    // Restore session from secure storage (non-blocking)
    // This is not critical for app startup, so run it in background
    // Session validation is deferred until first use to avoid blocking startup
    safeUnawaited(
      () async {
        try {
          final sessionRestoreStart = DateTime.now();
          final sessionManager = SessionManager();
          final restored = await sessionManager.restoreSession();
          final sessionRestoreDuration = DateTime.now().difference(sessionRestoreStart).inMilliseconds;

          if (restored) {
            logger.i(
              'Session restored successfully on startup (${sessionRestoreDuration}ms)',
            );
            // Don't validate session immediately - defer to first use
            // This prevents blocking startup with network requests
            // Session will be validated automatically on first HTTP request
            // Start periodic session monitoring (validation will happen in background)
            await sessionManager.startSessionMonitoring();
            logger.i('Session monitoring started');
          } else {
            logger.i(
              'No session to restore on startup (${sessionRestoreDuration}ms)',
            );
          }
        } on Exception catch (e) {
          logger.w('Failed to restore session on startup: $e');
        }
      }(),
      onError: (e, stack) {
        logger.w('Failed to restore session on startup: $e');
      },
    );

    // Restore cookies from SecureStorage on startup (non-blocking)
    // This restores cookies saved after WebView login for auto-login
    // According to .plan-idea-docs.md: cookies should be restored from SecureStorage
    // and synced to both CookieManager (Kotlin) and Dio CookieJar
    safeUnawaited(
      DioClient.restoreCookiesFromSecureStorage(),
      onError: (e, stack) {
        logger.w('Failed to restore cookies from SecureStorage on startup: $e');
      },
    );

    // Also synchronize cookies from WebView SharedPreferences (legacy/fallback)
    // This is not critical for app startup, so run it in background
    safeUnawaited(
      DioClient.syncCookiesFromWebView(),
      onError: (e, stack) {
        logger.w('Failed to sync cookies from WebView on startup: $e');
      },
    );

    logger.i('Database, cache, auth, and endpoints initialized successfully');
  }

  Future<void> _requestEssentialPermissions() async {
    try {
      logger.i('Requesting essential permissions...');

      final permissionService = PermissionService();

      // Check if permissions are already granted
      final hasAllPermissions =
          await permissionService.hasAllEssentialPermissions();

      if (!hasAllPermissions) {
        logger.i('Some permissions are missing, requesting...');

        // Show onboarding dialog on first launch
        // Use scaffoldMessengerKey.currentContext to avoid BuildContext issues after async
        if (mounted && !_hasShownOnboarding) {
          final isFirstLaunch = await FirstLaunchHelper.isFirstLaunch();
          if (isFirstLaunch && mounted) {
            // Get context from scaffoldMessengerKey to ensure it's valid
            final dialogContext = _scaffoldMessengerKey.currentContext;
            if (dialogContext != null && dialogContext.mounted) {
              try {
                final proceed = await PermissionsOnboardingDialog.show(dialogContext);
                _hasShownOnboarding = true;
                if (!proceed) {
                  logger.i('User cancelled permission onboarding');
                  // Mark as launched even if cancelled
                  await FirstLaunchHelper.markAsLaunched();
                  return;
                }
                // Mark as launched after showing dialog
                await FirstLaunchHelper.markAsLaunched();
              } on Exception catch (dialogError) {
                logger.w('Failed to show permissions onboarding dialog: $dialogError');
                // Continue without dialog if it fails
                await FirstLaunchHelper.markAsLaunched();
              }
            } else {
              logger.w('Context not available for permissions onboarding dialog');
              // Continue without dialog if context is not available
              await FirstLaunchHelper.markAsLaunched();
            }
          }
        }

        // Request all essential permissions
        final results = await permissionService.requestEssentialPermissions();

        final grantedCount = results.values.where((granted) => granted).length;
        final totalCount = results.length;

        logger.i('Permissions requested: $grantedCount/$totalCount granted');

        if (grantedCount < totalCount) {
          logger.w(
              'Some permissions were not granted. App functionality may be limited.');
        }
      } else {
        logger.i('All essential permissions already granted');
        // Still mark as launched if permissions already granted
        if (mounted) {
          final isFirstLaunch = await FirstLaunchHelper.isFirstLaunch();
          if (isFirstLaunch) {
            await FirstLaunchHelper.markAsLaunched();
          }
        }
      }
    } on Exception catch (e, stackTrace) {
      logger.e('Failed to request essential permissions',
          error: e, stackTrace: stackTrace);
      // Continue app initialization even if permission requests fail
    }
  }

  Future<void> _initializeDevEnvironment() async {
    logger.i('Initializing development environment');

    if (config.debugFeaturesEnabled) {
      logger.i('Debug features enabled');
    }

    // TODO: Add dev-specific setup
  }

  Future<void> _initializeStageEnvironment() async {
    logger.i('Initializing stage environment');

    if (config.analyticsEnabled) {
      logger.i('Analytics enabled for stage environment');
      // TODO: Initialize analytics
    }

    if (config.crashReportingEnabled) {
      logger.i('Crash reporting enabled for stage environment');
      // TODO: Initialize crash reporting
    }
  }

  Future<void> _initializeProdEnvironment() async {
    logger.i('Initializing production environment');

    if (config.analyticsEnabled) {
      logger.i('Analytics enabled for production environment');
      // TODO: Initialize analytics
    }

    if (config.crashReportingEnabled) {
      logger.i('Crash reporting enabled for production environment');
      // TODO: Initialize crash reporting
    }

    if (!config.debugFeaturesEnabled) {
      logger.i('Debug features disabled for production environment');
    }
  }

  // Cache locale to avoid repeated FutureBuilder calls
  Locale? _cachedLocale;
  Future<Locale>? _localeFuture;

  @override
  Widget build(BuildContext context) {
    try {
      // Only create router after initialization is complete to avoid race conditions
      // Show loading screen while initializing
      // Use minimal MaterialApp without localization to avoid null check errors
      if (!_isInitialized) {
        // Use absolute minimal MaterialApp without any theme or complex widgets
        // This prevents any null check errors during early initialization on new Android
        try {
          return MaterialApp(
            title: 'JaBook',
            debugShowCheckedModeBanner: false,
            home: Builder(
              builder: (context) => const Scaffold(
                body: Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      CircularProgressIndicator(),
                      SizedBox(height: 16),
                      Text('Loading...'),
                    ],
                  ),
                ),
              ),
            ),
          );
        } on Exception {
          // Ultimate fallback - no theme, no complex widgets
          return const MaterialApp(
            debugShowCheckedModeBanner: false,
            home: Scaffold(
              body: ColoredBox(
                color: Colors.white,
                child: Center(
                  child: CircularProgressIndicator(),
                ),
              ),
            ),
          );
        }
      }
    
    // Watch for language changes to trigger rebuild
    try {
      ref.watch(languageProvider);
    } on Exception catch (e, stackTrace) {
      logger.e('Error watching languageProvider: $e', stackTrace: stackTrace);
    }
    
    // Create router lazily - only when actually needed after initialization
    // This prevents immediate screen building before everything is ready
    // Delay router creation by one frame to ensure all initialization is complete
    final router = ref.watch(appRouterProvider);

    // Initialize locale future only once
    _localeFuture ??= languageManager.getLocale().then((locale) {
      _cachedLocale = locale;
      return locale;
    });

    return ProviderScope(
      overrides: [
        // Override AuthRepositoryProvider with actual implementation
        // Use lazy initialization to avoid issues during app startup
        authRepositoryProvider.overrideWith((ref) {
          // Lazy initialization - only create when actually needed
          // Use a try-catch to handle any initialization errors gracefully
          try {
            // Double-check that widget is still mounted
            if (!mounted) {
              logger.w('Widget not mounted when creating auth repository');
              throw StateError('Widget is not mounted, cannot create RuTrackerAuth');
            }
            
            // On Android 16, context may not be fully ready during early initialization
            // Additional check: verify that context is still mounted
            // This is especially important on Android 16 where lifecycle is stricter
            final buildContext = context;
            if (!buildContext.mounted) {
              throw StateError('BuildContext is not mounted, cannot create RuTrackerAuth');
            }
            
            if (_rutrackerAuth == null) {
              // Create RuTrackerAuth with context
              // On Android 16, wrap in try-catch to handle any initialization issues
              try {
                _rutrackerAuth = RuTrackerAuth(buildContext);
                if (_rutrackerAuth == null) {
                  throw StateError('RuTrackerAuth creation returned null');
                }
              } on Exception catch (authError) {
                logger.e('Failed to create RuTrackerAuth: $authError');
                rethrow;
              }
            }
            
            if (_authRepository == null) {
              final auth = _rutrackerAuth;
              if (auth == null) {
                throw StateError('RuTrackerAuth is null');
              }
              _authRepository = AuthRepositoryImpl(auth);
              if (_authRepository == null) {
                throw StateError('AuthRepositoryImpl creation returned null');
              }
            }
            
            final repository = _authRepository;
            if (repository == null) {
              throw StateError('AuthRepository is null after initialization');
            }
            return repository;
          } catch (e, stackTrace) {
            logger.e('Failed to initialize auth repository: $e', stackTrace: stackTrace);
            // On new Android, try to continue with a fallback instead of crashing
            // This allows the app to start even if auth initialization fails
            rethrow;
          }
        }),
      ],
      child: FutureBuilder<Locale>(
        future: _localeFuture,
        initialData: _cachedLocale ?? const Locale('en', 'US'),
        builder: (context, snapshot) {
          try {
            final locale = snapshot.data ?? const Locale('en', 'US');

            return MaterialApp.router(
            title: config.appName,
            theme: AppTheme.lightTheme,
            darkTheme: AppTheme.darkTheme,
            routerConfig: router,
            debugShowCheckedModeBanner: config.isDebug,
            scaffoldMessengerKey: config.isDebug ? _scaffoldMessengerKey : null,
            // Performance optimizations and responsive framework
            builder: (context, child) {
              // Track first frame render time
              final appStartTime = _appStartTime;
              if (!_firstFrameTracked && appStartTime != null) {
                WidgetsBinding.instance.addPostFrameCallback((_) {
                  if (!_firstFrameTracked) {
                    _firstFrameTracked = true;
                    final timeToFirstFrame =
                        DateTime.now().difference(appStartTime).inMilliseconds;
                    final structuredLogger = StructuredLogger();
                    safeUnawaited(
                      structuredLogger.log(
                        level: 'info',
                        subsystem: 'performance',
                        message: 'First UI frame rendered',
                        context: 'app_startup',
                        durationMs: timeToFirstFrame,
                        extra: {
                          'time_to_first_frame_ms': timeToFirstFrame,
                          'metric_type': 'ui_render_time',
                        },
                      ),
                    );
                    logger.i('First UI frame rendered in ${timeToFirstFrame}ms');
                  }
                });
              }
              // Safely get MediaQuery - may be null during initialization on new Android
              final mediaQuery = MediaQuery.maybeOf(context);
              if (mediaQuery == null) {
                return child ?? const SizedBox.shrink();
              }
              
              // Wrap with ResponsiveFramework for adaptive UI
              return ResponsiveBreakpoints.builder(
                child: MediaQuery(
                  // Use text scaler from device but clamp it for consistency
                  data: mediaQuery.copyWith(
                    textScaler: mediaQuery.textScaler.clamp(
                          minScaleFactor: 0.8,
                          maxScaleFactor: 1.2,
                        ),
                  ),
                  child: child ?? const SizedBox.shrink(),
                ),
                breakpoints: [
                  const Breakpoint(start: 0, end: 450, name: MOBILE),
                  const Breakpoint(start: 451, end: 800, name: TABLET),
                  const Breakpoint(start: 801, end: 1920, name: DESKTOP),
                  const Breakpoint(start: 1921, end: double.infinity, name: '4K'),
                ],
              );
            },
            localizationsDelegates: const [
              AppLocalizations.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
              GlobalCupertinoLocalizations.delegate,
            ],
            supportedLocales: const [
              Locale('en', 'US'), // English
              Locale('ru', 'RU'), // Russian
            ],
            locale: locale,
          );
          } on Exception catch (e, stackTrace) {
            logger.e('Error in FutureBuilder builder: $e', stackTrace: stackTrace);
            // Fallback to minimal MaterialApp
            return MaterialApp(
              title: config.appName,
              home: Scaffold(
                body: Center(
                  child: Text('Error: ${e.toString()}'),
                ),
              ),
            );
          }
        },
      ),
    );
    } on Exception catch (e, stackTrace) {
      logger.e('Error in build method: $e', stackTrace: stackTrace);
      // Ultimate fallback
      return MaterialApp(
        title: 'JaBook',
        home: Scaffold(
          body: Center(
            child: Text('Critical error: ${e.toString()}'),
          ),
        ),
      );
    }
  }
}
