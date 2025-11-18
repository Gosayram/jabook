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

  @override
  void initState() {
    super.initState();
    // Record app start time for UI render metrics
    _appStartTime = DateTime.now();
    // Run initialization in the background to avoid blocking UI
    Future.microtask(_initializeApp);
  }

  Future<void> _initializeApp() async {
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
    } on Exception catch (e, stackTrace) {
      logger.e('Failed to initialize app', error: e, stackTrace: stackTrace);
      // Show error to user if critical initialization fails
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Ошибка инициализации приложения: ${e.toString()}'),
            backgroundColor: Colors.red,
            duration: const Duration(seconds: 5),
          ),
        );
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

    final cacheStartTime = DateTime.now();
    await cacheService.initialize(database.database);
    final cacheInitDuration = DateTime.now().difference(cacheStartTime).inMilliseconds;

    // AuthRepository will be initialized in build method when context is available

    // Initialize EndpointManager with default endpoints and health checks
    final endpointStartTime = DateTime.now();
    final endpointManager = EndpointManager(database.database);
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
      final healthScheduler = EndpointHealthScheduler(database.database);
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

    // Synchronize cookies from WebView to DioClient on startup (non-blocking)
    // This is not critical for app startup, so run it in background
    safeUnawaited(
      DioClient.syncCookiesFromWebView(),
      onError: (e, stack) {
        logger.w('Failed to sync cookies on startup: $e');
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
        if (mounted && !_hasShownOnboarding) {
          final isFirstLaunch = await FirstLaunchHelper.isFirstLaunch();
          if (isFirstLaunch && mounted) {
            final proceed = await PermissionsOnboardingDialog.show(context);
            _hasShownOnboarding = true;
            if (!proceed) {
              logger.i('User cancelled permission onboarding');
              // Mark as launched even if cancelled
              await FirstLaunchHelper.markAsLaunched();
              return;
            }
            // Mark as launched after showing dialog
            await FirstLaunchHelper.markAsLaunched();
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
    final router = ref.watch(appRouterProvider);
    // Watch for language changes to trigger rebuild
    ref.watch(languageProvider);

    // Initialize locale future only once
    _localeFuture ??= languageManager.getLocale().then((locale) {
      _cachedLocale = locale;
      return locale;
    });

    return ProviderScope(
      overrides: [
        // Override AuthRepositoryProvider with actual implementation
        authRepositoryProvider.overrideWith((ref) {
          _rutrackerAuth ??= RuTrackerAuth(context);
          _authRepository ??= AuthRepositoryImpl(_rutrackerAuth!);
          return _authRepository!;
        }),
      ],
      child: FutureBuilder<Locale>(
        future: _localeFuture,
        initialData: _cachedLocale ?? const Locale('en', 'US'),
        builder: (context, snapshot) {
          final locale = snapshot.data ?? const Locale('en', 'US');

          return MaterialApp.router(
            title: config.appName,
            theme: AppTheme.lightTheme,
            darkTheme: AppTheme.darkTheme,
            routerConfig: router,
            debugShowCheckedModeBanner: config.isDebug,
            scaffoldMessengerKey: config.isDebug ? _scaffoldMessengerKey : null,
            // Performance optimizations
            builder: (context, child) {
              // Track first frame render time
              if (!_firstFrameTracked && _appStartTime != null) {
                WidgetsBinding.instance.addPostFrameCallback((_) {
                  if (!_firstFrameTracked) {
                    _firstFrameTracked = true;
                    final timeToFirstFrame =
                        DateTime.now().difference(_appStartTime!).inMilliseconds;
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
              return MediaQuery(
                // Use text scaler from device but clamp it for consistency
                data: MediaQuery.of(context).copyWith(
                  textScaler: MediaQuery.of(context).textScaler.clamp(
                        minScaleFactor: 0.8,
                        maxScaleFactor: 1.2,
                      ),
                ),
                child: child!,
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
        },
      ),
    );
  }
}
