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
import 'package:jabook/core/endpoints/endpoint_manager.dart';
import 'package:jabook/core/logging/environment_logger.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/permissions/permission_service_v2.dart';
import 'package:jabook/data/db/app_database.dart';
import 'package:jabook/features/auth/data/providers/auth_provider.dart';
import 'package:jabook/features/auth/data/repositories/auth_repository_impl.dart';
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

  @override
  void initState() {
    super.initState();
    // Run initialization in the background to avoid blocking UI
    Future.microtask(_initializeApp);
  }

  Future<void> _initializeApp() async {
    try {
      // Initialize logger
      logger.initialize();

      // Log startup details (single receiver via cascade inside helper)
      _logStartupDetails();

      // Initialize database and cache
      await _initializeDatabase();

      // Request essential permissions
      await _requestEssentialPermissions();

      // Initialize configuration based on flavor
      await _initializeEnvironment();

      // Single call — no cascade warning
      logger.i('App initialization complete');
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
    logger.i('Initializing database...');
    await database.initialize();
    await cacheService.initialize(database.database);

    // AuthRepository will be initialized in build method when context is available

    // Initialize EndpointManager with default endpoints and health checks
    final endpointManager = EndpointManager(database.database);
    await endpointManager
        .initialize(); // This includes initializeDefaultEndpoints() and health checks

    // Synchronize cookies from WebView to DioClient on startup
    try {
      await DioClient.syncCookiesFromWebView();
      logger.i('Cookies synchronized from WebView');
    } on Exception catch (e) {
      logger.w('Failed to sync cookies on startup: $e');
    }

    logger.i('Database, cache, auth, and endpoints initialized successfully');
  }

  Future<void> _requestEssentialPermissions() async {
    try {
      logger.i('Checking essential capabilities using system APIs...');

      final permissionService = PermissionServiceV2();
      final results = await permissionService.requestEssentialPermissions();

      final grantedCount = results.values.where((granted) => granted).length;
      final totalCount = results.length;

      logger.i('Capabilities checked: $grantedCount/$totalCount available');

      if (grantedCount < totalCount) {
        logger.w(
            'Some capabilities are not available. App functionality may be limited.');
      }
    } on Exception catch (e, stackTrace) {
      logger.e('Failed to check essential capabilities',
          error: e, stackTrace: stackTrace);
      // Continue app initialization even if capability checks fail
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

  @override
  Widget build(BuildContext context) {
    final router = ref.watch(appRouterProvider);
    // Watch for language changes to trigger rebuild
    ref.watch(languageProvider);

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
        future: languageManager.getLocale(),
        builder: (context, snapshot) {
          final locale = snapshot.data ?? const Locale('en', 'US');

          return MaterialApp.router(
            title: config.appName,
            theme: AppTheme.lightTheme,
            darkTheme: AppTheme.darkTheme,
            routerConfig: router,
            debugShowCheckedModeBanner: config.isDebug,
            scaffoldMessengerKey: config.isDebug ? _scaffoldMessengerKey : null,
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
