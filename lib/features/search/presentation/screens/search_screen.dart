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
import 'dart:convert';
import 'dart:io' as io;

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:jabook/core/auth/cookie_database_service.dart';
import 'package:jabook/core/auth/simple_cookie_manager.dart';
import 'package:jabook/core/cache/rutracker_cache_service.dart';
import 'package:jabook/core/constants/category_constants.dart';
import 'package:jabook/core/di/providers/auth_providers.dart';
import 'package:jabook/core/di/providers/database_providers.dart'
    as db_providers;
import 'package:jabook/core/domain/auth/entities/auth_status.dart';
import 'package:jabook/core/favorites/favorites_provider.dart';
import 'package:jabook/core/infrastructure/endpoints/endpoint_provider.dart';
import 'package:jabook/core/infrastructure/errors/failures.dart';
import 'package:jabook/core/infrastructure/logging/environment_logger.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:jabook/core/metadata/audiobook_metadata_service.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/parse/category_parser.dart' as category_parser;
import 'package:jabook/core/parse/rutracker_parser.dart';
import 'package:jabook/core/search/search_history_service.dart';
import 'package:jabook/core/services/cookie_service.dart';
import 'package:jabook/core/utils/app_title_utils.dart';
import 'package:jabook/core/utils/responsive_utils.dart';
import 'package:jabook/core/utils/safe_async.dart';
import 'package:jabook/features/search/presentation/widgets/audiobook_card_skeleton.dart';
import 'package:jabook/features/search/presentation/widgets/grouped_audiobook_list.dart';
import 'package:jabook/features/search/presentation/widgets/recommended_audiobooks_widget.dart';
import 'package:jabook/features/settings/presentation/screens/mirror_settings_screen.dart';
import 'package:jabook/l10n/app_localizations.dart';
import 'package:windows1251/windows1251.dart';

/// Screen for searching audiobooks on RuTracker.
///
/// This screen provides a search interface for users to find
/// audiobooks by title, author, or other criteria.
class SearchScreen extends ConsumerStatefulWidget {
  /// Creates a new SearchScreen instance.
  ///
  /// The [key] parameter is optional and can be used to identify
  /// this widget in the widget tree.
  const SearchScreen({super.key});

  @override
  ConsumerState<SearchScreen> createState() => _SearchScreenState();
}

class _SearchScreenState extends ConsumerState<SearchScreen> {
  final TextEditingController _searchController = TextEditingController();
  final RuTrackerParser _parser = RuTrackerParser();
  final RuTrackerCacheService _cacheService = RuTrackerCacheService();
  AudiobookMetadataService? _metadataService;
  SearchHistoryService? _historyService;
  List<Map<String, dynamic>> _searchResults = [];
  List<String> _searchHistory = [];
  bool _isLoading = false;
  bool _hasSearched = false;
  bool _isFromCache = false;
  DateTime? _cacheExpirationTime;
  bool _isFromLocalDb = false;
  bool _showHistory = false;
  String? _errorKind; // 'network' | 'auth' | 'mirror' | 'timeout' | null
  String? _errorMessage;
  String? _activeHost;
  CancelToken? _cancelToken;
  Timer? _debounce;
  int _startOffset = 0;
  bool _isLoadingMore = false;
  bool _hasMore = true;
  final ScrollController _scrollController = ScrollController();
  final Set<String> _selectedCategories = <String>{};
  final FocusNode _searchFocusNode = FocusNode();
  // Cache for recommended audiobooks to avoid reloading on every build
  Future<List<RecommendedAudiobook>>? _recommendedAudiobooksFuture;

  @override
  void dispose() {
    _debounce?.cancel();
    _cancelToken?.cancel();
    _searchController.dispose();
    _scrollController.dispose();
    _searchFocusNode.dispose();
    super.dispose();
  }

  @override
  void initState() {
    super.initState();
    EnvironmentLogger().d('SearchScreen.initState called');
    _initializeCache();
    _initializeMetadataService();
    _loadActiveHost();
    _scrollController.addListener(_onScroll);
    _searchFocusNode.addListener(_onSearchFocusChange);
    _searchController.addListener(_onSearchTextChange);

    // Test: Try to load recommendations immediately in initState
    Future.microtask(() async {
      EnvironmentLogger().d('Testing recommendation loading in initState');
      try {
        final authStatus = ref.read(authStatusProvider);
        final isAuthenticated = authStatus.value?.isAuthenticated ?? false;
        EnvironmentLogger().d(
          'initState: isAuthenticated=$isAuthenticated, authStatus=${authStatus.value}',
        );

        if (isAuthenticated) {
          EnvironmentLogger()
              .d('User is authenticated, testing recommendation loading');
          // Test the recommendation loading mechanism directly
          final testResult = await _getCategoryRecommendations();
          EnvironmentLogger().i(
            'Test recommendation loading completed: ${testResult.length} recommendations',
          );
        } else {
          EnvironmentLogger()
              .w('User not authenticated in initState, skipping test');
        }
      } on Exception catch (e, stackTrace) {
        EnvironmentLogger().e(
          'Error testing recommendation loading in initState: $e',
          error: e,
          stackTrace: stackTrace,
        );
      }
    });

    // Listen to auth status changes and reload recommendations when user logs in
    Future.microtask(() {
      final structuredLogger = StructuredLogger();
      safeUnawaited(structuredLogger.log(
        level: 'debug',
        subsystem: 'recommendations',
        message: 'Setting up auth status listener in initState',
        context: 'auth_listener_setup',
      ));
      ref.listen<AsyncValue<AuthStatus>>(
        authStatusProvider,
        (previous, next) {
          safeUnawaited(structuredLogger.log(
            level: 'info',
            subsystem: 'recommendations',
            message: 'Auth status changed',
            context: 'auth_status_change',
            extra: {
              'previous_value': previous?.value?.toString() ?? 'null',
              'next_value': next.value?.toString() ?? 'null',
              'next_has_value': next.hasValue,
              'next_is_loading': next.isLoading,
              'next_has_error': next.hasError,
            },
          ));
          next.whenData((status) {
            // If user just authenticated, reload recommendations
            final wasAuthenticated = previous?.value?.isAuthenticated ?? false;
            safeUnawaited(structuredLogger.log(
              level: 'info',
              subsystem: 'recommendations',
              message: 'Processing auth status change',
              context: 'auth_status_change',
              extra: {
                'was_authenticated': wasAuthenticated,
                'is_authenticated': status.isAuthenticated,
                'status_changed': status.isAuthenticated != wasAuthenticated,
              },
            ));
            if (status.isAuthenticated && !wasAuthenticated) {
              safeUnawaited(structuredLogger.log(
                level: 'info',
                subsystem: 'recommendations',
                message: 'User authenticated, reloading recommendations',
                context: 'auth_status_change',
              ));
              if (mounted) {
                setState(() {
                  _recommendedAudiobooksFuture = _getRecommendedAudiobooks();
                });
              } else {
                safeUnawaited(structuredLogger.log(
                  level: 'warning',
                  subsystem: 'recommendations',
                  message: 'Widget not mounted, cannot reload recommendations',
                  context: 'auth_status_change',
                ));
              }
            }
          });
          // Also handle loading and error states
          if (next.isLoading) {
            safeUnawaited(structuredLogger.log(
              level: 'debug',
              subsystem: 'recommendations',
              message: 'Auth status is loading',
              context: 'auth_status_change',
            ));
          }
          if (next.hasError) {
            safeUnawaited(structuredLogger.log(
              level: 'warning',
              subsystem: 'recommendations',
              message: 'Auth status has error',
              context: 'auth_status_change',
              extra: {
                'error': next.error.toString(),
              },
            ));
          }
        },
      );
    });
  }

  void _onSearchFocusChange() {
    setState(() {
      _showHistory = _searchFocusNode.hasFocus &&
          _searchController.text.isEmpty &&
          _searchHistory.isNotEmpty;
    });
  }

  void _onSearchTextChange() {
    setState(() {
      _showHistory = _searchFocusNode.hasFocus &&
          _searchController.text.isEmpty &&
          _searchHistory.isNotEmpty;
    });
  }

  Future<void> _initializeCache() async {
    // Cache service initialization would typically happen at app startup
    // For now, we'll handle it here
  }

  Future<void> _initializeMetadataService() async {
    try {
      final appDatabase = ref.read(db_providers.appDatabaseProvider);
      await appDatabase.initialize();
      _metadataService = AudiobookMetadataService(appDatabase.database);
      _historyService = SearchHistoryService(appDatabase.database);
      await _loadSearchHistory();
    } on Exception {
      // Metadata service optional - continue without it
      _metadataService = null;
      _historyService = null;
    }
  }

  Future<void> _loadSearchHistory() async {
    if (_historyService == null) return;
    try {
      final history = await _historyService!.getRecentSearches();
      if (mounted) {
        setState(() {
          _searchHistory = history;
        });
      }
    } on Exception {
      // Ignore errors
    }
  }

  Future<void> _loadActiveHost() async {
    try {
      final endpointManager = ref.read(endpointManagerProvider);
      final base = await endpointManager.getActiveEndpoint();

      // Verify endpoint is available before displaying
      final isAvailable = await endpointManager.quickAvailabilityCheck(base);
      if (!isAvailable) {
        // If current endpoint is unavailable, try to get a better one
        logger.w(
            'Current active endpoint $base is unavailable, trying to get available one');
        // getActiveEndpoint should already handle this, but we verify anyway
      }

      final uri = Uri.tryParse(base);
      if (uri != null && uri.hasScheme && uri.hasAuthority) {
        final host = uri.host;
        if (mounted && host.isNotEmpty) {
          setState(() => _activeHost = host);
        }
      } else {
        // Fallback: try to extract host from base string
        try {
          final host = Uri.parse(base).host;
          if (mounted && host.isNotEmpty) {
            setState(() => _activeHost = host);
          }
        } on Exception {
          // If parsing fails, set to null
          if (mounted) {
            setState(() => _activeHost = null);
          }
        }
      }
    } on Exception catch (e) {
      logger.w('Failed to load active host: $e');
      if (mounted) {
        setState(() => _activeHost = null);
      }
    }
  }

  /// Handles login with stored credentials or opens auth screen.
  Future<void> _handleLogin() async {
    try {
      final repository = ref.read(authRepositoryProvider);

      // Check if stored credentials are available
      final hasStored = await repository.hasStoredCredentials();

      if (hasStored) {
        // Try to login with stored credentials first
        try {
          final success = await repository.loginWithStoredCredentials();

          if (success) {
            // Validate cookies
            final isValid = await DioClient.validateCookies();

            if (isValid) {
              if (!mounted) return;
              // ignore: use_build_context_synchronously
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text(
                      AppLocalizations.of(context)?.authorizationSuccessful ??
                          'Authorization successful'),
                  backgroundColor: Colors.green,
                  duration: const Duration(seconds: 2),
                ),
              );

              // Clear auth errors if any
              if (_errorKind == 'auth') {
                setState(() {
                  _errorKind = null;
                  _errorMessage = null;
                });
                await _performSearch();
              }
              return;
            }
          }
        } on Exception catch (e) {
          logger.w('Login with stored credentials failed: $e');
          // Fall through to auth screen
        }
      }

      // Open auth screen for manual login
      if (!mounted) return;
      final result = await context.push('/auth');

      // If login was successful, refresh and perform search
      if (result == true && mounted) {
        // Explicitly refresh auth status to update the provider
        await ref.read(authRepositoryProvider).refreshAuthStatus();

        // Validate cookies after returning from auth screen
        final isValid = await DioClient.validateCookies();
        if (isValid) {
          if (_errorKind == 'auth') {
            setState(() {
              _errorKind = null;
              _errorMessage = null;
            });
            await _performSearch();
          }
        }
      }
    } on Exception catch (e, stackTrace) {
      logger.e('Error in _handleLogin: $e', stackTrace: stackTrace);
      if (!mounted) return;

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(AppLocalizations.of(context)
                  ?.authorizationCheckError(e.toString()) ??
              'Authorization check error: ${e.toString()}'),
          backgroundColor: Colors.orange,
          duration: const Duration(seconds: 3),
        ),
      );

      // Open auth screen as fallback
      if (mounted) {
        await context.push('/auth');
      }
    }
  }

  Future<void> _performSearch() async {
    if (_searchController.text.trim().isEmpty) return;

    // Cancel any in-flight search
    _cancelToken?.cancel('superseded');
    _cancelToken = CancelToken();

    setState(() {
      _isLoading = true;
      _hasSearched = true;
      _isFromCache = false;
      _isFromLocalDb = false;
      _errorKind = null;
      _errorMessage = null;
      _startOffset = 0;
      _hasMore = true;
      _searchResults = [];
      _selectedCategories.clear(); // Reset filters on new search
    });

    final query = _searchController.text.trim();

    // First try local database search (offline mode)
    if (_metadataService != null) {
      try {
        final localResults = await _metadataService!.searchLocally(query);
        if (localResults.isNotEmpty) {
          final localResultsMaps = localResults.map(_audiobookToMap).toList();
          setState(() {
            _searchResults = localResultsMaps;
            _isLoading = false;
            _isFromLocalDb = true;
          });
          // Save to search history
          if (_historyService != null && query.isNotEmpty) {
            await _historyService!.saveSearchQuery(query);
            await _loadSearchHistory();
          }

          // Update statistics for items that don't have them
          safeUnawaited(_updateMissingStatistics(localResultsMaps));

          // Still try to get network results in background for updates
          safeUnawaited(
            _performNetworkSearch(query, updateExisting: true),
            onError: (e, stack) {
              logger.w('Background network search failed: $e');
            },
          );
          return;
        }
      } on Exception {
        // Continue to network search if local search fails
      }
    }

    // Second try to get from cache
    final cachedResults = await _cacheService.getCachedSearchResults(query);
    if (cachedResults != null) {
      // Get expiration time
      final expiration = await _cacheService.getSearchResultsExpiration(query);

      setState(() {
        _searchResults = cachedResults;
        _isLoading = false;
        _isFromCache = true;
        _cacheExpirationTime = expiration;
      });
      // Update statistics for items that don't have them
      safeUnawaited(_updateMissingStatistics(cachedResults));
      return;
    }

    // Finally, try network search
    await _performNetworkSearch(query);
  }

  /// Forces a refresh of the current search by clearing cache and performing network search.
  ///
  /// This method clears the cache for the current query and performs a fresh network search,
  /// bypassing cache and local database.
  Future<void> _forceRefreshSearch() async {
    final query = _searchController.text.trim();
    if (query.isEmpty) return;

    // Clear cache for this query
    await _cacheService.clearSearchResultsCacheForQuery(query);

    // Cancel any in-flight search
    _cancelToken?.cancel('superseded');
    _cancelToken = CancelToken();

    setState(() {
      _isFromCache = false;
      _isFromLocalDb = false;
      _isLoading = true;
      _errorKind = null;
      _errorMessage = null;
      _startOffset = 0;
      _hasMore = true;
    });

    // Perform network search directly, bypassing cache
    await _performNetworkSearch(query);
  }

  /// Performs network search (can be called separately for background updates).
  ///
  /// [updateExisting] - if true, updates existing results instead of replacing them.
  Future<void> _performNetworkSearch(String query,
      {bool updateExisting = false}) async {
    final structuredLogger = StructuredLogger();

    // Get active endpoint for logging
    final endpointManager = ref.read(endpointManagerProvider);
    final activeEndpoint = await endpointManager.getActiveEndpoint();

    await structuredLogger.log(
      level: 'info',
      subsystem: 'search',
      message: 'Starting network search',
      context: 'search_request',
      extra: {
        'query': query,
        'update_existing': updateExisting,
        'start_offset': _startOffset,
        'active_endpoint': activeEndpoint,
      },
    );

    // CRITICAL: Comprehensive cookie synchronization before search
    // Sync cookies from all sources (Database, CookieService, SecureStorage, CookieJar) to ensure availability
    final syncStartTime = DateTime.now();
    var syncSuccessCount = 0;
    final syncSourceDetails = <String, dynamic>{};

    // Step 0: Try to get cookies from database FIRST - this is the most reliable source
    try {
      await structuredLogger.log(
        level: 'info',
        subsystem: 'search',
        message: 'Getting cookies from database before search',
        context: 'search_request',
        extra: {
          'active_endpoint': activeEndpoint,
        },
      );

      final cookieDbService =
          CookieDatabaseService(ref.read(db_providers.appDatabaseProvider));
      final cookieHeader =
          await cookieDbService.getCookiesForAnyEndpoint(activeEndpoint);

      if (cookieHeader != null && cookieHeader.isNotEmpty) {
        // Check for required session cookies
        final hasBbSession = cookieHeader.contains('bb_session=');
        final hasBbData = cookieHeader.contains('bb_data=');

        // Sync cookies to Dio CookieJar
        await DioClient.syncCookiesFromCookieService(
            cookieHeader, activeEndpoint);
        syncSuccessCount++;

        await structuredLogger.log(
          level: 'info',
          subsystem: 'search',
          message: 'Cookies synced from database before search',
          context: 'search_request',
          extra: {
            'active_endpoint': activeEndpoint,
            'cookie_header_length': cookieHeader.length,
            'has_bb_session': hasBbSession,
            'has_bb_data': hasBbData,
            'has_required_cookies': hasBbSession || hasBbData,
          },
        );

        syncSourceDetails['Database'] = {
          'success': true,
          'cookie_count': cookieHeader.split(';').length,
          'has_bb_session': hasBbSession,
          'has_bb_data': hasBbData,
        };
      } else {
        syncSourceDetails['Database'] = {
          'success': false,
          'reason': 'no_cookies_found',
        };
      }
    } on Exception catch (e) {
      await structuredLogger.log(
        level: 'warning',
        subsystem: 'search',
        message: 'Failed to sync cookies from database',
        context: 'search_request',
        cause: e.toString(),
      );
      syncSourceDetails['Database'] = {
        'success': false,
        'error': e.toString(),
      };
    }

    // Step 1: Sync from CookieService (Android CookieManager) - fallback source
    try {
      final activeHost = Uri.parse(activeEndpoint).host;
      final url = 'https://$activeHost';

      // Flush cookies to ensure they're available
      await CookieService.flushCookies();

      // Try to get cookies from CookieService
      final cookieHeader = await CookieService.getCookiesForUrl(url);

      if (cookieHeader != null &&
          cookieHeader.isNotEmpty &&
          cookieHeader.trim().contains('=')) {
        // Check for required session cookies
        final hasBbSession = cookieHeader.contains('bb_session=');
        final hasBbData = cookieHeader.contains('bb_data=');

        // Sync cookies to Dio CookieJar
        await DioClient.syncCookiesFromCookieService(cookieHeader, url);
        syncSuccessCount++;

        await structuredLogger.log(
          level: 'info',
          subsystem: 'search',
          message: 'Cookies synced from CookieService before search',
          context: 'search_request',
          extra: {
            'url': url,
            'cookie_header_length': cookieHeader.length,
            'has_bb_session': hasBbSession,
            'has_bb_data': hasBbData,
            'has_required_cookies': hasBbSession || hasBbData,
          },
        );

        syncSourceDetails['CookieService'] = {
          'success': true,
          'cookie_count': cookieHeader.split(';').length,
          'has_bb_session': hasBbSession,
          'has_bb_data': hasBbData,
        };
      } else {
        syncSourceDetails['CookieService'] = {
          'success': false,
          'reason': 'no_cookies_found',
        };
      }
    } on Exception catch (e) {
      await structuredLogger.log(
        level: 'warning',
        subsystem: 'search',
        message: 'Failed to sync cookies from CookieService',
        context: 'search_request',
        cause: e.toString(),
      );
      syncSourceDetails['CookieService'] = {
        'success': false,
        'error': e.toString(),
      };
    }

    // Step 2: Sync from SecureStorage/CookieJar - fallback source
    try {
      final simpleCookieManager = SimpleCookieManager();
      final cookieString = await simpleCookieManager.getCookie(activeEndpoint);

      if (cookieString != null && cookieString.isNotEmpty) {
        // Check for required session cookies
        final hasBbSession = cookieString.contains('bb_session=');
        final hasBbData = cookieString.contains('bb_data=');

        // Load cookies from SecureStorage to CookieJar
        final uri = Uri.parse(activeEndpoint);
        final jar = await DioClient.getCookieJar();
        if (jar != null) {
          await simpleCookieManager.loadCookieToJar(activeEndpoint, jar);

          // Also sync to CookieService if not already synced
          final cookies = await jar.loadForRequest(uri);
          if (cookies.isNotEmpty) {
            final cookieHeader =
                cookies.map((c) => '${c.name}=${c.value}').join('; ');
            await DioClient.syncCookiesFromCookieService(
                cookieHeader, activeEndpoint);
            syncSuccessCount++;

            await structuredLogger.log(
              level: 'info',
              subsystem: 'search',
              message:
                  'Cookies synced from SecureStorage/CookieJar to CookieService',
              context: 'search_request',
              extra: {
                'cookie_count': cookies.length,
                'cookie_names': cookies.map((c) => c.name).toList(),
                'has_bb_session': hasBbSession,
                'has_bb_data': hasBbData,
                'has_required_cookies': hasBbSession || hasBbData,
              },
            );

            syncSourceDetails['SecureStorage'] = {
              'success': true,
              'cookie_count': cookies.length,
              'cookie_names': cookies.map((c) => c.name).toList(),
              'has_bb_session': hasBbSession,
              'has_bb_data': hasBbData,
            };
          }
        }
      } else {
        syncSourceDetails['SecureStorage'] = {
          'success': false,
          'reason': 'no_cookies_found',
        };
      }
    } on Exception catch (e) {
      await structuredLogger.log(
        level: 'debug',
        subsystem: 'search',
        message: 'Failed to sync cookies from SecureStorage',
        context: 'search_request',
        cause: e.toString(),
      );
      syncSourceDetails['SecureStorage'] = {
        'success': false,
        'error': e.toString(),
      };
    }

    // Log comprehensive sync summary
    final syncDuration =
        DateTime.now().difference(syncStartTime).inMilliseconds;
    await structuredLogger.log(
      level: syncSuccessCount > 0 ? 'info' : 'warning',
      subsystem: 'search',
      message: 'Cookie synchronization completed before search',
      context: 'search_request',
      durationMs: syncDuration,
      extra: {
        'sync_success_count': syncSuccessCount,
        'sync_sources': syncSourceDetails,
        'total_sync_duration_ms': syncDuration,
      },
    );

    // Check if we have cookies and required session cookies before performing search
    // Use hasValidCookies which now checks CookieService first
    try {
      final hasCookies = await DioClient.hasValidCookies();

      // Additional check: verify we have required session cookies (bb_session or bb_data)
      var hasRequiredCookies = false;
      var foundCookieNames = <String>[];

      if (hasCookies) {
        // Check CookieJar for required cookies
        final uri = Uri.parse(activeEndpoint);
        final jar = await DioClient.getCookieJar();
        if (jar != null) {
          final cookies = await jar.loadForRequest(uri);
          foundCookieNames = cookies.map((c) => c.name).toList();
          hasRequiredCookies = cookies.any((c) =>
              c.name.toLowerCase() == 'bb_session' ||
              c.name.toLowerCase() == 'bb_data' ||
              c.name.toLowerCase().contains('session'));
        }

        // Also check CookieService if CookieJar doesn't have required cookies
        if (!hasRequiredCookies) {
          try {
            final activeHost = Uri.parse(activeEndpoint).host;
            final url = 'https://$activeHost';
            final cookieHeader = await CookieService.getCookiesForUrl(url);
            if (cookieHeader != null && cookieHeader.isNotEmpty) {
              hasRequiredCookies = cookieHeader.contains('bb_session=') ||
                  cookieHeader.contains('bb_data=');
            }
          } on Exception {
            // Ignore CookieService check errors
          }
        }
      }

      await structuredLogger.log(
        level: 'info',
        subsystem: 'search',
        message: 'Cookie validation check before search',
        context: 'search_request',
        extra: {
          'has_cookies': hasCookies,
          'has_required_cookies': hasRequiredCookies,
          'found_cookie_names': foundCookieNames,
          'checked_sources': ['CookieService', 'CookieJar', 'SecureStorage'],
          'active_endpoint': activeEndpoint,
        },
      );

      if (!hasCookies || !hasRequiredCookies) {
        // No cookies or no required session cookies - need login
        await structuredLogger.log(
          level: 'warning',
          subsystem: 'search',
          message:
              'No valid cookies or required session cookies found, requiring login',
          context: 'search_request',
          extra: {
            'has_cookies': hasCookies,
            'has_required_cookies': hasRequiredCookies,
            'found_cookie_names': foundCookieNames,
          },
        );

        if (mounted) {
          setState(() {
            _isLoading = false;
            _errorKind = 'auth';
            _errorMessage =
                AppLocalizations.of(context)?.authorizationFailedMessage ??
                    'Please log in first to perform search';
          });
          // Show login prompt
          safeUnawaited(_handleLogin());
        }
        return;
      }
    } on Exception catch (e) {
      await structuredLogger.log(
        level: 'error',
        subsystem: 'search',
        message: 'Error checking cookies, continuing with search attempt',
        context: 'search_request',
        cause: e.toString(),
      );
      // Continue with search attempt even if cookie check fails
    }

    // Cookies exist, but they might be invalid - try search anyway
    // If search fails with auth error, we'll handle it in error handling

    // CRITICAL: Use ONLY the active endpoint for search - don't switch mirrors
    final dio = await DioClient.instance;

    await structuredLogger.log(
      level: 'info',
      subsystem: 'search',
      message: 'Starting search with active endpoint',
      context: 'search_request',
      extra: {
        'query': query,
        'active_endpoint': activeEndpoint,
        'start_offset': _startOffset,
      },
    );

    // Use only the active endpoint - don't try other endpoints
    final endpoint = activeEndpoint;

    try {
      // CRITICAL: Before making search request, verify cookies are available for active endpoint
      final searchUri = Uri.parse('$endpoint/forum/tracker.php');
      final cookieJar = await DioClient.getCookieJar();

      // Load cookies that will be sent with the request
      final cookiesForRequest = cookieJar != null
          ? await cookieJar.loadForRequest(searchUri)
          : <io.Cookie>[];

      await structuredLogger.log(
        level: cookiesForRequest.isNotEmpty ? 'info' : 'warning',
        subsystem: 'search',
        message: 'Cookies loaded for search request',
        context: 'search_request',
        extra: {
          'endpoint': endpoint,
          'search_uri': searchUri.toString(),
          'cookie_count': cookiesForRequest.length,
          'cookie_names': cookiesForRequest.map((c) => c.name).toList(),
          'has_session_cookies': cookiesForRequest.any((c) =>
              c.name.toLowerCase().contains('session') ||
              c.name == 'bb_session' ||
              c.name == 'bb_data'),
        },
      );

      // CRITICAL: If no cookies found for active endpoint, sync from CookieService
      // CookieService is the single source of truth for cookies on Android
      if (cookiesForRequest.isEmpty && cookieJar != null) {
        await structuredLogger.log(
          level: 'warning',
          subsystem: 'search',
          message:
              'No cookies found for active endpoint, syncing from CookieService',
          context: 'search_request',
          extra: {'endpoint': endpoint},
        );

        // Try CookieService (Android CookieManager) - most reliable source
        // Use retries with delays to ensure cookies are available
        String? cookieHeader;
        const maxSyncRetries = 6;
        var syncSuccess = false;
        final activeHost = Uri.parse(endpoint).host;
        final url = 'https://$activeHost';

        for (var syncAttempt = 0; syncAttempt < maxSyncRetries; syncAttempt++) {
          if (syncAttempt > 0) {
            await Future.delayed(Duration(milliseconds: 300 * syncAttempt));
            // Flush cookies before each retry
            await CookieService.flushCookies();
          }

          try {
            cookieHeader = await CookieService.getCookiesForUrl(url);
            if (cookieHeader != null &&
                cookieHeader.isNotEmpty &&
                cookieHeader.trim().contains('=')) {
              await structuredLogger.log(
                level: 'info',
                subsystem: 'search',
                message: 'Syncing cookies before search',
                context: 'search_request',
                extra: {
                  'source': 'CookieService',
                  'url': url,
                  'cookie_header_length': cookieHeader.length,
                  'sync_attempt': syncAttempt + 1,
                  'max_retries': maxSyncRetries,
                },
              );

              await DioClient.syncCookiesFromCookieService(cookieHeader, url);

              // Wait a bit and reload cookies to verify
              await Future.delayed(const Duration(milliseconds: 400));
              final cookiesAfterSync =
                  await cookieJar.loadForRequest(searchUri);

              if (cookiesAfterSync.isNotEmpty) {
                syncSuccess = true;
                await structuredLogger.log(
                  level: 'info',
                  subsystem: 'search',
                  message: 'Cookies successfully synced from CookieService',
                  context: 'search_request',
                  extra: {
                    'endpoint': endpoint,
                    'url': url,
                    'cookie_count_after_sync': cookiesAfterSync.length,
                    'cookie_names_after_sync':
                        cookiesAfterSync.map((c) => c.name).toList(),
                    'sync_attempt': syncAttempt + 1,
                  },
                );
                break; // Success, exit retry loop
              } else {
                await structuredLogger.log(
                  level: 'warning',
                  subsystem: 'search',
                  message:
                      'Cookies synced but not found in CookieJar, retrying',
                  context: 'search_request',
                  extra: {
                    'endpoint': endpoint,
                    'url': url,
                    'sync_attempt': syncAttempt + 1,
                  },
                );
              }
            }
          } on Exception catch (e) {
            await structuredLogger.log(
              level: 'debug',
              subsystem: 'search',
              message: 'CookieService sync attempt failed, retrying',
              context: 'search_request',
              cause: e.toString(),
              extra: {
                'endpoint': endpoint,
                'url': url,
                'sync_attempt': syncAttempt + 1,
              },
            );
          }
        }

        // If CookieService sync failed, try WebView sync as fallback
        if (!syncSuccess) {
          await structuredLogger.log(
            level: 'warning',
            subsystem: 'search',
            message: 'CookieService sync failed, trying WebView fallback',
            context: 'search_request',
            extra: {'endpoint': endpoint},
          );

          try {
            await DioClient.syncCookiesFromWebView();
            await Future.delayed(const Duration(milliseconds: 300));
            final cookiesAfterSync = await cookieJar.loadForRequest(searchUri);

            await structuredLogger.log(
              level: cookiesAfterSync.isNotEmpty ? 'info' : 'warning',
              subsystem: 'search',
              message: 'Cookies after sync from WebView',
              context: 'search_request',
              extra: {
                'endpoint': endpoint,
                'cookie_count_after_sync': cookiesAfterSync.length,
                'cookie_names_after_sync':
                    cookiesAfterSync.map((c) => c.name).toList(),
              },
            );
          } on Exception catch (e) {
            await structuredLogger.log(
              level: 'warning',
              subsystem: 'search',
              message: 'WebView sync also failed',
              context: 'search_request',
              cause: e.toString(),
              extra: {'endpoint': endpoint},
            );
          }
        }
      }

      // Make the search request
      // CRITICAL: Use tracker.php (not search.php) for searching torrents/audiobooks
      // tracker.php is the default search method on RuTracker for "раздачи" (torrents)
      // search.php is for searching all topics (including discussions)
      final requestStartTime = DateTime.now();
      final requestUri = '$endpoint/forum/tracker.php';
      final requestQueryParams = {
        'nm': query,
        'c': CategoryConstants
            .audiobooksCategoryId, // Try to filter by category 33 (audiobooks)
        // Note: If c=33 doesn't work, we'll filter results client-side
        'o': '1', // Sort by relevance
        'start': _startOffset,
      };

      // Before making search request, ensure cookies are available
      // Step 6: Check cookies before request and load from SecureStorage if needed
      var finalCookies = cookieJar != null
          ? await cookieJar.loadForRequest(searchUri)
          : <io.Cookie>[];

      if (finalCookies.isEmpty && cookieJar != null) {
        // Try to load from SecureStorage and sync to CookieJar
        final simpleCookieManager = SimpleCookieManager();
        final cookieString = await simpleCookieManager.getCookie(endpoint);

        if (cookieString != null && cookieString.isNotEmpty) {
          // Load cookies from SecureStorage to CookieJar
          await simpleCookieManager.loadCookieToJar(endpoint, cookieJar);

          // Reload cookies after sync
          final cookiesAfterLoad = await cookieJar.loadForRequest(searchUri);
          if (cookiesAfterLoad.isNotEmpty) {
            finalCookies = cookiesAfterLoad;
            await structuredLogger.log(
              level: 'info',
              subsystem: 'search',
              message: 'Loaded cookies from SecureStorage to CookieJar',
              context: 'search_request',
              extra: {
                'cookie_count': cookiesAfterLoad.length,
                'cookie_names': cookiesAfterLoad.map((c) => c.name).toList(),
              },
            );
          }
        }
      }

      await structuredLogger.log(
        level: 'info',
        subsystem: 'search',
        message: 'Making search HTTP request',
        context: 'search_request',
        extra: {
          'endpoint': endpoint,
          'request_uri': requestUri,
          'query_params': requestQueryParams,
          'method': 'GET',
          'cookies_count': finalCookies.length,
          'cookies_names': finalCookies.map((c) => c.name).toList(),
          'will_send_cookies': finalCookies.isNotEmpty,
          'request_start_time': requestStartTime.toIso8601String(),
        },
      );

      final response = await dio
          .get(
            requestUri,
            queryParameters: requestQueryParams,
            options: Options(
              responseType: ResponseType
                  .bytes, // Get raw bytes to decode Windows-1251 correctly
            ),
            cancelToken: _cancelToken,
          )
          .timeout(const Duration(seconds: 30));

      final requestDuration =
          DateTime.now().difference(requestStartTime).inMilliseconds;

      await structuredLogger.log(
        level: response.statusCode == 200 ? 'info' : 'warning',
        subsystem: 'search',
        message: 'Search HTTP response received',
        context: 'search_request',
        durationMs: requestDuration,
        extra: {
          'endpoint': endpoint,
          'status_code': response.statusCode,
          'response_size_bytes': response.data?.toString().length ?? 0,
          'response_url': response.realUri.toString(),
          'is_redirect': response.isRedirect,
          'redirect_location': response.headers.value('location'),
          'cookies_were_sent': finalCookies.isNotEmpty,
          'cookies_sent_count': finalCookies.length,
          'response_headers': {
            'content-type': response.headers.value('content-type'),
            'server': response.headers.value('server'),
            'cf-ray': response.headers.value('cf-ray'),
          },
        },
      );

      if (response.statusCode == 200) {
        // CRITICAL: Validate response data before processing
        // Task 3.2: Check data type, size, and validity at each stage
        final responseDataType = response.data.runtimeType.toString();
        final responseDataSize = response.data is List<int>
            ? (response.data as List<int>).length
            : (response.data is String ? (response.data as String).length : 0);

        await structuredLogger.log(
          level: 'debug',
          subsystem: 'search',
          message: 'Validating response data',
          context: 'search_request',
          extra: {
            'response_data_type': responseDataType,
            'response_data_size': responseDataSize,
            'content_type':
                response.headers.value('content-type') ?? 'not_provided',
            'content_encoding':
                response.headers.value('content-encoding') ?? 'not_provided',
          },
        );

        // Validate that we have data
        if (response.data == null) {
          await structuredLogger.log(
            level: 'error',
            subsystem: 'search',
            message: 'Response data is null',
            context: 'search_request',
          );

          if (mounted) {
            setState(() {
              _isLoading = false;
              _errorKind = 'network';
              _errorMessage =
                  'Received empty response from server. Please try again.';
            });
          }
          return;
        }

        // Validate data size
        if (responseDataSize == 0) {
          await structuredLogger.log(
            level: 'error',
            subsystem: 'search',
            message: 'Response data is empty',
            context: 'search_request',
          );

          if (mounted) {
            setState(() {
              _isLoading = false;
              _errorKind = 'network';
              _errorMessage =
                  'Received empty response from server. Please try again.';
            });
          }
          return;
        }

        // Decode response for login page check (response.data is now List<int>)
        String? decodedTextForCheck;
        final contentType = response.headers.value('content-type') ?? '';

        if (response.data is List<int>) {
          final bytes = response.data as List<int>;

          // Validate bytes before decoding
          // Note: Brotli decompression is handled automatically by DioBrotliTransformer
          if (bytes.isEmpty) {
            await structuredLogger.log(
              level: 'error',
              subsystem: 'search',
              message: 'Bytes array is empty',
              context: 'search_request',
            );

            if (mounted) {
              setState(() {
                _isLoading = false;
                _errorKind = 'network';
                _errorMessage =
                    'Received empty data after decompression. Please try again.';
              });
            }
            return;
          }

          // Validate bytes are valid (not all zeros or corrupted)
          final nonZeroBytes = bytes.where((b) => b != 0).length;
          if (nonZeroBytes < bytes.length * 0.1) {
            // Less than 10% non-zero bytes might indicate corruption
            await structuredLogger.log(
              level: 'warning',
              subsystem: 'search',
              message: 'Response data may be corrupted (too many zero bytes)',
              context: 'search_request',
              extra: {
                'total_bytes': bytes.length,
                'non_zero_bytes': nonZeroBytes,
                'zero_byte_percentage':
                    ((bytes.length - nonZeroBytes) / bytes.length * 100)
                        .toStringAsFixed(2),
              },
            );
          }

          await structuredLogger.log(
            level: 'debug',
            subsystem: 'search',
            message: 'Validating bytes before decoding',
            context: 'search_request',
            extra: {
              'bytes_length': bytes.length,
              'non_zero_bytes': nonZeroBytes,
              'first_bytes_preview': bytes.length > 50
                  ? bytes.sublist(0, 50).toString()
                  : bytes.toString(),
            },
          );
          // Try to decode for login page check
          // Determine encoding from Content-Type header if available
          String? detectedEncoding;
          if (contentType.isNotEmpty) {
            final charsetMatch =
                RegExp(r'charset=([^;\s]+)', caseSensitive: false)
                    .firstMatch(contentType);
            if (charsetMatch != null) {
              detectedEncoding = charsetMatch.group(1)?.toLowerCase();
            }
          }

          try {
            if (detectedEncoding != null &&
                (detectedEncoding.contains('windows-1251') ||
                    detectedEncoding.contains('cp1251') ||
                    detectedEncoding.contains('1251'))) {
              // Use Windows-1251 if specified
              decodedTextForCheck = windows1251.decode(bytes);
            } else {
              // Try Windows-1251 first (RuTracker default), then UTF-8
              try {
                decodedTextForCheck = windows1251.decode(bytes);
              } on Exception {
                decodedTextForCheck = utf8.decode(bytes);
              }
            }
          } on Exception {
            try {
              // Fallback to UTF-8
              decodedTextForCheck = utf8.decode(bytes);
            } on FormatException {
              // If both fail, use toString as last resort
              decodedTextForCheck = String.fromCharCodes(bytes);
            }
          }
        } else {
          decodedTextForCheck = response.data?.toString() ?? '';
        }

        // Check response content for login page indicators before parsing
        final responseText = decodedTextForCheck.toLowerCase();
        final isLoginPage =
            responseText.contains('action="https://rutracker') &&
                responseText.contains('login.php') &&
                responseText.contains('password');

        if (isLoginPage) {
          // This is a login page, not search results
          await structuredLogger.log(
            level: 'warning',
            subsystem: 'search',
            message: 'Received login page instead of search results',
            context: 'search_request',
            extra: {
              'endpoint': endpoint,
              'response_preview': responseText.length > 500
                  ? responseText.substring(0, 500)
                  : responseText,
            },
          );

          if (mounted) {
            setState(() {
              _isLoading = false;
              _errorKind = 'auth';
              _errorMessage =
                  AppLocalizations.of(context)?.authorizationFailedMessage ??
                      'Authentication required. Please log in.';
              safeUnawaited(_handleLogin());
            });
          }
          return;
        }

        var results = <Map<String, dynamic>>[];
        try {
          // Pass response data and headers to parser for proper encoding detection
          final parsedResults = await _parser.parseSearchResults(
            response.data,
            contentType: response.headers.value('content-type'),
            baseUrl: endpoint,
          );

          // CRITICAL: Filter results to only include audiobooks
          // tracker.php returns ALL torrents, so we need to filter by category
          // We can identify audiobooks by checking if they belong to category 33 forums
          // or by checking the title/content for audiobook indicators
          final audiobookResults = _filterAudiobookResults(parsedResults);

          await structuredLogger.log(
            level: 'info',
            subsystem: 'search',
            message: 'Parsing search results',
            context: 'search_request',
            extra: {
              'endpoint': endpoint,
              'response_size_bytes': response.data?.toString().length ?? 0,
              'parsed_results_count': parsedResults.length,
              'filtered_audiobook_count': audiobookResults.length,
            },
          );

          results = audiobookResults.map(_audiobookToMap).toList();
        } on ParsingFailure catch (e) {
          // Enhanced logging and recovery for parsing failures
          String? responseText;
          if (response.data is List<int>) {
            final bytes = response.data as List<int>;
            // Try to decode for analysis
            try {
              responseText = windows1251.decode(bytes);
            } on Exception {
              try {
                responseText = utf8.decode(bytes);
              } on FormatException {
                responseText = String.fromCharCodes(bytes);
              }
            }
          } else {
            responseText = response.data?.toString() ?? '';
          }

          final responseTextLower = responseText.toLowerCase();
          final hasSearchForm =
              responseTextLower.contains('form[action*="tracker"]') ||
                  responseTextLower.contains('input[name="nm"]') ||
                  responseTextLower.contains('form#quick-search');
          final hasSearchPageElements =
              responseTextLower.contains('div.tcenter') ||
                  responseTextLower.contains('table.forumline') ||
                  responseTextLower.contains('div.nav');
          final hasAccessDenied =
              responseTextLower.contains('доступ запрещен') ||
                  responseTextLower.contains('access denied') ||
                  responseTextLower.contains('требуется авторизация');
          final isLoginPage =
              responseTextLower.contains('action="https://rutracker') &&
                  responseTextLower.contains('login.php') &&
                  responseTextLower.contains('password');

          // Check if error is encoding-related
          final isEncodingError = e.message
                  .toLowerCase()
                  .contains('encoding') ||
              e.message.toLowerCase().contains('decode') ||
              responseText.contains(
                  '\uFFFD'); // Replacement character indicates encoding issue

          await structuredLogger.log(
            level: 'error',
            subsystem: 'search',
            message: 'Failed to parse search results',
            context: 'search_request',
            cause: e.message,
            extra: {
              'endpoint': endpoint,
              'status_code': response.statusCode,
              'response_size': responseText.length,
              'response_preview': responseText.length > 1000
                  ? responseText.substring(0, 1000)
                  : responseText,
              'error_type': 'ParsingFailure',
              'is_encoding_error': isEncodingError,
              'page_analysis': {
                'has_search_form': hasSearchForm,
                'has_search_page_elements': hasSearchPageElements,
                'has_access_denied': hasAccessDenied,
                'is_login_page': isLoginPage,
              },
            },
          );

          // Try automatic recovery for encoding errors
          if (isEncodingError &&
              response.data is List<int> &&
              !isLoginPage &&
              !hasAccessDenied) {
            await structuredLogger.log(
              level: 'info',
              subsystem: 'search',
              message: 'Attempting automatic recovery from encoding error',
              context: 'search_request',
              extra: {
                'original_encoding_attempt': contentType,
                'will_try_alternative_encoding': true,
                'response_data_type': response.data.runtimeType.toString(),
                'response_data_size': (response.data as List<int>).length,
              },
            );

            try {
              // CRITICAL: Use original bytes from response.data (not re-encoded)
              // Note: Brotli decompression is handled automatically by DioBrotliTransformer
              // These bytes are already decompressed and ready for encoding conversion
              final bytes = response.data as List<int>;

              // Validate bytes before recovery attempt
              if (bytes.isEmpty) {
                throw Exception('Cannot recover: bytes are empty');
              }

              // Determine which encoding was likely used based on Content-Type
              final detectedEncoding =
                  contentType.toLowerCase().contains('windows-1251') ||
                      contentType.toLowerCase().contains('cp1251') ||
                      contentType.toLowerCase().contains('1251');

              // Try alternative encoding
              // If Content-Type says Windows-1251, try UTF-8, and vice versa
              final alternativeContentType = detectedEncoding
                  ? 'text/html; charset=utf-8'
                  : 'text/html; charset=windows-1251';

              await structuredLogger.log(
                level: 'debug',
                subsystem: 'search',
                message: 'Trying recovery with alternative encoding',
                context: 'search_request',
                extra: {
                  'original_content_type': contentType,
                  'alternative_content_type': alternativeContentType,
                  'bytes_length': bytes.length,
                },
              );

              // Try parsing with alternative encoding
              // Pass original bytes (not re-encoded) to parser
              // Note: Brotli decompression is handled automatically by DioBrotliTransformer
              final recoveredResults = await _parser.parseSearchResults(
                bytes, // Original bytes (Brotli already decompressed by Dio transformer)
                contentType: alternativeContentType,
                baseUrl: endpoint,
              );

              // Validate recovery results
              if (recoveredResults.isEmpty) {
                await structuredLogger.log(
                  level: 'warning',
                  subsystem: 'search',
                  message: 'Recovery succeeded but returned empty results',
                  context: 'search_request',
                  extra: {
                    'alternative_encoding_used':
                        detectedEncoding ? 'utf-8' : 'windows-1251',
                  },
                );
                // Empty results might be valid (no search results), so continue
              }

              final audiobookResults =
                  _filterAudiobookResults(recoveredResults);
              results = audiobookResults.map(_audiobookToMap).toList();

              await structuredLogger.log(
                level: 'info',
                subsystem: 'search',
                message: 'Automatic recovery from encoding error succeeded',
                context: 'search_request',
                extra: {
                  'alternative_encoding_used':
                      detectedEncoding ? 'utf-8' : 'windows-1251',
                  'recovered_results_count': recoveredResults.length,
                  'filtered_audiobook_count': audiobookResults.length,
                },
              );

              // Continue with successful results (skip error handling)
            } on ParsingFailure catch (recoveryError) {
              await structuredLogger.log(
                level: 'warning',
                subsystem: 'search',
                message:
                    'Automatic recovery from encoding error failed with ParsingFailure',
                context: 'search_request',
                cause: recoveryError.message,
                extra: {
                  'error_type': 'ParsingFailure',
                  'recovery_attempted': true,
                },
              );
              // Fall through to error handling
            } on Exception catch (recoveryError) {
              await structuredLogger.log(
                level: 'warning',
                subsystem: 'search',
                message: 'Automatic recovery from encoding error failed',
                context: 'search_request',
                cause: recoveryError.toString(),
                extra: {
                  'error_type': recoveryError.runtimeType.toString(),
                  'recovery_attempted': true,
                },
              );

              // Try Latin-1 as last resort (never fails, but may produce mojibake)
              if (response.data is List<int>) {
                try {
                  await structuredLogger.log(
                    level: 'info',
                    subsystem: 'search',
                    message: 'Trying Latin-1 decoding as last resort',
                    context: 'search_request',
                  );

                  final bytes = response.data as List<int>;
                  final latin1Results = await _parser.parseSearchResults(
                    bytes,
                    contentType: 'text/html; charset=iso-8859-1',
                    baseUrl: endpoint,
                  );

                  final audiobookResults =
                      _filterAudiobookResults(latin1Results);
                  if (audiobookResults.isNotEmpty) {
                    results = audiobookResults.map(_audiobookToMap).toList();

                    await structuredLogger.log(
                      level: 'info',
                      subsystem: 'search',
                      message: 'Latin-1 recovery succeeded',
                      context: 'search_request',
                      extra: {
                        'recovered_results_count': results.length,
                      },
                    );
                    // Continue with successful results
                  } else {
                    await structuredLogger.log(
                      level: 'warning',
                      subsystem: 'search',
                      message: 'Latin-1 recovery returned empty results',
                      context: 'search_request',
                    );
                    // Fall through to error handling
                  }
                } on Exception catch (latin1Error) {
                  await structuredLogger.log(
                    level: 'error',
                    subsystem: 'search',
                    message: 'Latin-1 recovery also failed',
                    context: 'search_request',
                    cause: latin1Error.toString(),
                  );
                  // Fall through to error handling
                }
              } else {
                // Fall through to error handling
              }
            }
          }

          // If recovery didn't work or wasn't attempted, handle the error
          if (results.isEmpty) {
            // Improved check for authentication error (more specific)
            final errorMessageLower = e.message.toLowerCase();
            final isAuthError = errorMessageLower
                    .contains('page appears to require authentication') ||
                errorMessageLower.contains('please log in first') ||
                (errorMessageLower.contains('authentication') &&
                    errorMessageLower.contains('log in')) ||
                // Check response content for login page indicators
                isLoginPage ||
                (hasAccessDenied && !hasSearchForm);

            if (mounted) {
              setState(() {
                _isLoading = false;
                if (isAuthError) {
                  _errorKind = 'auth';
                  _errorMessage = AppLocalizations.of(context)
                          ?.authorizationFailedMessage ??
                      'Authentication required. Please log in.';
                  safeUnawaited(_handleLogin());
                } else if (isEncodingError) {
                  // Task 3.3: Distinguish encoding errors and suggest specific actions
                  _errorKind = 'network';
                  _errorMessage = AppLocalizations.of(context)
                          ?.failedToParseSearchResultsEncoding ??
                      'Failed to parse search results due to encoding issue. '
                          'This may be a temporary server problem. Please try again. '
                          'If the problem persists, try changing the mirror in Settings → Sources.';
                } else {
                  _errorKind = 'network';
                  _errorMessage = AppLocalizations.of(context)
                          ?.failedToParseSearchResultsStructure ??
                      'Failed to parse search results. The page structure may have changed. '
                          'Please try again. If the problem persists, try changing the mirror in Settings → Sources.';
                }
              });
            }
            return;
          }
        }

        // Cache the results
        // Results are already maps
        await _cacheService.cacheSearchResults(query, results);

        if (updateExisting && mounted) {
          // Update existing results (for background refresh from local DB)
          setState(() {
            final networkResults = results;
            // Merge with existing, avoiding duplicates
            final existingIds =
                _searchResults.map((r) => r['id'] as String?).toSet();
            final newResults = networkResults
                .where((r) => !existingIds.contains(r['id'] as String?))
                .toList();
            _searchResults = [..._searchResults, ...newResults];
            _isFromLocalDb = false; // Switch to network results
            _errorKind = null;
            _errorMessage = null;
            _hasMore = results.isNotEmpty;
          });
        } else {
          setState(() {
            // Results are already maps
            _searchResults = results;
            _isLoading = false;
            _isFromCache = false;
            _isFromLocalDb = false;
            _errorKind = null;
            _errorMessage = null;
            _hasMore = results.isNotEmpty;
            _showHistory = false;
          });

          // Save to search history
          if (_historyService != null && query.isNotEmpty) {
            await _historyService!.saveSearchQuery(query);
            await _loadSearchHistory();
          }

          // Update statistics for items that don't have them
          safeUnawaited(_updateMissingStatistics(results));
        }

        // Success - update active host display (don't change endpoint)
        if (mounted) {
          final uri = Uri.tryParse(endpoint);
          if (uri != null &&
              uri.hasScheme &&
              uri.hasAuthority &&
              uri.host.isNotEmpty) {
            setState(() => _activeHost = uri.host);
          } else {
            // Fallback: try to extract host from endpoint string
            try {
              final host = Uri.parse(endpoint).host;
              if (host.isNotEmpty) {
                setState(() => _activeHost = host);
              }
            } on Exception {
              // If parsing fails, try to reload host
              await _loadActiveHost();
            }
          }
        }

        await structuredLogger.log(
          level: 'info',
          subsystem: 'search',
          message: 'Search completed successfully',
          context: 'search_request',
          extra: {
            'endpoint': endpoint,
            'results_count': results.length,
            'has_more': _hasMore,
          },
        );
        return;
      } else {
        // Non-200 status - show error
        await structuredLogger.log(
          level: 'warning',
          subsystem: 'search',
          message: 'Search request returned non-200 status',
          context: 'search_request',
          extra: {
            'endpoint': endpoint,
            'status_code': response.statusCode,
            'response_size': response.data?.toString().length ?? 0,
          },
        );

        if (mounted) {
          setState(() {
            _isLoading = false;
            _errorKind = 'network';
            _errorMessage = 'HTTP ${response.statusCode}';
          });
        }
        return;
      }
    } on TimeoutException catch (e) {
      await structuredLogger.log(
        level: 'warning',
        subsystem: 'search',
        message: 'Search request timed out',
        context: 'search_request',
        cause: e.toString(),
        extra: {
          'endpoint': endpoint,
          'timeout_seconds': 30,
        },
      );

      if (mounted) {
        setState(() {
          _isLoading = false;
          _errorKind = 'timeout';
          _errorMessage =
              AppLocalizations.of(context)?.requestTimedOutMessage ??
                  'Request timed out. Check your connection.';
        });
      }
      return;
    } on DioException catch (e) {
      // Check if request was cancelled
      if (e.type == DioExceptionType.cancel) {
        // Silently ignore cancelled request
        await structuredLogger.log(
          level: 'debug',
          subsystem: 'search',
          message: 'Search request cancelled',
          context: 'search_request',
          extra: {'endpoint': endpoint},
        );
        return;
      }

      // Log detailed error information
      final errorType = switch (e.type) {
        DioExceptionType.connectionTimeout => 'Connection timeout',
        DioExceptionType.sendTimeout => 'Send timeout',
        DioExceptionType.receiveTimeout => 'Receive timeout',
        DioExceptionType.badResponse => 'Bad response',
        DioExceptionType.cancel => 'Request cancelled',
        DioExceptionType.connectionError => 'Connection error',
        DioExceptionType.badCertificate => 'Bad certificate',
        DioExceptionType.unknown => 'Unknown error',
      };

      await structuredLogger.log(
        level: 'error',
        subsystem: 'search',
        message: 'Search request failed with DioException',
        context: 'search_request',
        cause: e.toString(),
        extra: {
          'endpoint': endpoint,
          'error_type': errorType,
          'status_code': e.response?.statusCode,
          'response_data_preview':
              e.response?.data?.toString().length != null &&
                      e.response!.data.toString().length > 500
                  ? e.response!.data.toString().substring(0, 500)
                  : e.response?.data?.toString() ?? '<empty>',
          'request_url': e.requestOptions.uri.toString(),
          'query_params': e.requestOptions.queryParameters,
        },
      );

      // Check if error indicates authentication issue
      final isAuthError = e.response?.statusCode == 401 ||
          e.response?.statusCode == 403 ||
          (e.response?.data?.toString().toLowerCase().contains('login') ??
              false) ||
          (e.response?.data?.toString().toLowerCase().contains('авторизация') ??
              false) ||
          (e.message?.toLowerCase().contains('authentication') ?? false);

      // Check if error is server error (5xx)
      final isServerError = e.response?.statusCode != null &&
          e.response!.statusCode! >= 500 &&
          e.response!.statusCode! < 600;

      if (mounted) {
        setState(() {
          _isLoading = false;
          if (isAuthError) {
            _errorKind = 'auth';
            _errorMessage =
                AppLocalizations.of(context)?.authorizationFailedMessage ??
                    'Authentication required. Please log in.';
            safeUnawaited(_handleLogin());
          } else if (isServerError) {
            _errorKind = 'network';
            _errorMessage = AppLocalizations.of(context)?.serverError ??
                'Server is temporarily unavailable. Please try again later or choose another mirror.';
          } else {
            _errorKind = 'network';
            _errorMessage =
                AppLocalizations.of(context)?.searchFailedMessage(errorType) ??
                    'Search failed: $errorType';
          }
        });
      }

      // Handle connection errors
      if (!isAuthError &&
          (e.type == DioExceptionType.connectionError ||
              e.type == DioExceptionType.connectionTimeout)) {
        final message = e.message?.toLowerCase() ?? '';
        final isDnsError = message.contains('host lookup') ||
            message.contains('no address associated with hostname') ||
            message.contains('name or service not known') ||
            message.contains('failed host lookup') ||
            message.contains('network is unreachable');

        await structuredLogger.log(
          level: 'error',
          subsystem: 'search',
          message: 'Connection error detected',
          context: 'search_request',
          cause: e.toString(),
          extra: {
            'endpoint': endpoint,
            'error_type': e.type.toString(),
            'error_message': e.message,
            'is_dns_error': isDnsError,
            'request_path': e.requestOptions.path,
          },
        );

        if (mounted) {
          setState(() {
            _isLoading = false;
            _errorKind = isDnsError ? 'mirror' : 'network';
            _errorMessage = isDnsError
                ? (AppLocalizations.of(context)?.dnsError ??
                    'Could not resolve domain. This may be due to network restrictions or an inactive mirror.')
                : (AppLocalizations.of(context)?.connectionFailed ??
                    'Connection failed. Please check your internet connection or try a different mirror.');
          });
        }
        return;
      }

      // Handle other DioException types
      await structuredLogger.log(
        level: 'error',
        subsystem: 'search',
        message: 'Other DioException error',
        context: 'search_request',
        cause: e.toString(),
        extra: {
          'endpoint': endpoint,
          'error_type': e.type.toString(),
          'error_message': e.message,
          'status_code': e.response?.statusCode,
          'request_path': e.requestOptions.path,
        },
      );

      if (mounted) {
        setState(() {
          _isLoading = false;
          _errorKind = 'network';
          _errorMessage = e.message ?? 'Network error occurred';
        });
      }
      return;
    } on Exception catch (e) {
      await structuredLogger.log(
        level: 'error',
        subsystem: 'search',
        message: 'Unexpected error during search',
        context: 'search_request',
        cause: e.toString(),
        extra: {
          'endpoint': endpoint,
          'error_type': e.runtimeType.toString(),
        },
      );

      if (mounted) {
        setState(() {
          _isLoading = false;
          _errorKind = 'network';
          _errorMessage = e.toString();
        });
      }
      return;
    }
  }

  @override
  Widget build(BuildContext context) {
    EnvironmentLogger().d(
      'SearchScreen.build called: _hasSearched=$_hasSearched, searchText="${_searchController.text}"',
    );
    return Scaffold(
      appBar: AppBar(
        title: Text((AppLocalizations.of(context)?.searchAudiobooks ??
                'Search Audiobooks')
            .withFlavorSuffix()),
        actions: [
          if (_activeHost != null)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8.0),
              child: Center(
                child: Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: Colors.grey.shade200,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child:
                      Text(_activeHost!, style: const TextStyle(fontSize: 12)),
                ),
              ),
            ),
          IconButton(
            tooltip: AppLocalizations.of(context)?.rutrackerLoginTooltip ??
                'RuTracker Login',
            icon: const Icon(Icons.vpn_key),
            onPressed: _handleLogin,
          ),
          IconButton(
            tooltip: AppLocalizations.of(context)?.mirrorsTooltip ?? 'Mirrors',
            icon: const Icon(Icons.dns),
            onPressed: () {
              unawaited(Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const MirrorSettingsScreen()),
              ).then((_) => _loadActiveHost()));
            },
          ),
          PopupMenuButton<String>(
            onSelected: (value) async {
              final messenger = ScaffoldMessenger.of(context);
              final localizations = AppLocalizations.of(context);
              switch (value) {
                case 'refresh':
                  await _forceRefreshSearch();
                  break;
                case 'clear_search_cache':
                  await _cacheService.clearSearchResultsCache();
                  if (!mounted) break;
                  messenger.showSnackBar(
                    SnackBar(
                      content: Text(
                        localizations?.cacheCleared ?? 'Cache cleared',
                      ),
                    ),
                  );
                  break;
                case 'clear_all_cache':
                  await _cacheService.clearAllTopicDetailsCache();
                  await _cacheService.clearSearchResultsCache();
                  if (!mounted) break;
                  messenger.showSnackBar(
                    SnackBar(
                      content: Text(
                        localizations?.allCacheCleared ?? 'All cache cleared',
                      ),
                    ),
                  );
                  break;
              }
            },
            itemBuilder: (context) => [
              PopupMenuItem(
                value: 'refresh',
                child: ListTile(
                  leading: const Icon(Icons.refresh),
                  title: Text(
                    AppLocalizations.of(context)?.refreshCurrentSearch ??
                        'Refresh current search',
                  ),
                  contentPadding: EdgeInsets.zero,
                ),
              ),
              PopupMenuItem(
                value: 'clear_search_cache',
                child: ListTile(
                  leading: const Icon(Icons.clear_all),
                  title: Text(
                    AppLocalizations.of(context)?.clearSearchCache ??
                        'Clear search cache',
                  ),
                  contentPadding: EdgeInsets.zero,
                ),
              ),
              PopupMenuItem(
                value: 'clear_all_cache',
                child: ListTile(
                  leading: const Icon(Icons.delete_sweep),
                  title: Text(
                    AppLocalizations.of(context)?.clearAllCache ??
                        'Clear all cache',
                  ),
                  contentPadding: EdgeInsets.zero,
                ),
              ),
            ],
          ),
          IconButton(
            icon: const Icon(Icons.search),
            onPressed: _performSearch,
          ),
        ],
      ),
      body: GestureDetector(
        onTap: () {
          _searchFocusNode.unfocus();
          setState(() {
            _showHistory = false;
          });
        },
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.all(16.0),
              child: TextField(
                controller: _searchController,
                focusNode: _searchFocusNode,
                decoration: InputDecoration(
                  labelText: AppLocalizations.of(context)?.searchPlaceholder ??
                      'Enter title, author, or keywords',
                  hintText: AppLocalizations.of(context)?.searchPlaceholder ??
                      'Enter title, author, or keywords',
                  suffixIcon: _searchController.text.isNotEmpty
                      ? IconButton(
                          icon: const Icon(Icons.clear),
                          onPressed: () {
                            _searchController.clear();
                            // Keep focus after clearing
                            _searchFocusNode.requestFocus();
                            setState(() {
                              _searchResults = [];
                              _hasSearched = false;
                              _showHistory = _searchFocusNode.hasFocus &&
                                  _searchHistory.isNotEmpty;
                            });
                          },
                        )
                      : null,
                  border: const OutlineInputBorder(),
                ),
                onChanged: (value) {
                  setState(() {
                    _showHistory = _searchFocusNode.hasFocus &&
                        value.isEmpty &&
                        _searchHistory.isNotEmpty;
                  });
                  _debounce?.cancel();
                  // Debounce search with 300ms delay
                  _debounce =
                      Timer(const Duration(milliseconds: 300), _performSearch);
                },
                onSubmitted: (_) {
                  _performSearch();
                  _searchFocusNode.unfocus();
                },
                onTap: () {
                  setState(() {
                    _showHistory = _searchController.text.isEmpty &&
                        _searchHistory.isNotEmpty;
                  });
                },
              ),
            ),
            if (_showHistory && _searchHistory.isNotEmpty)
              _buildSearchHistory(),
            if (_hasSearched && _errorKind == 'auth')
              Container(
                margin: const EdgeInsets.symmetric(horizontal: 16.0),
                padding: const EdgeInsets.all(12.0),
                decoration: BoxDecoration(
                  color: Colors.orange.shade50,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Row(
                  children: [
                    Icon(Icons.lock_outline, color: Colors.orange.shade700),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        AppLocalizations.of(context)?.loginRequiredForSearch ??
                            'Login required to search RuTracker',
                        style: TextStyle(color: Colors.orange.shade800),
                      ),
                    ),
                    TextButton(
                      onPressed: () async {
                        // Open auth screen for login
                        final result = await context.push('/auth');
                        if (!mounted) return;

                        // If login was successful, validate cookies
                        if (result == true) {
                          final isValid = await DioClient.validateCookies();
                          if (!isValid) {
                            return; // Login failed, don't clear error
                          }
                        } else {
                          return; // User cancelled, don't clear error
                        }

                        setState(() {
                          _errorKind = null;
                          _errorMessage = null;
                        });
                        await _performSearch();
                      },
                      child:
                          Text(AppLocalizations.of(context)?.login ?? 'Login'),
                    ),
                  ],
                ),
              ),
            if (_isLoading)
              Expanded(
                child: Semantics(
                  label: AppLocalizations.of(context)?.loading ?? 'Loading',
                  child: const AudiobookCardSkeletonList(),
                ),
              )
            // Show search results only if user has searched AND search field is not empty
            // Otherwise show empty state with recommendations
            else if (_hasSearched && _searchController.text.isNotEmpty)
              Expanded(
                child: Column(
                  children: [
                    if (_isFromCache)
                      Container(
                        padding: const EdgeInsets.all(8.0),
                        color: Colors.blue[50],
                        child: Row(
                          children: [
                            Icon(Icons.cached,
                                size: 16, color: Colors.blue[700]),
                            const SizedBox(width: 8),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    AppLocalizations.of(context)
                                            ?.resultsFromCache ??
                                        'Results from cache',
                                    style: TextStyle(
                                      color: Colors.blue[700],
                                      fontSize: 12,
                                    ),
                                  ),
                                  if (_cacheExpirationTime != null)
                                    Text(
                                      '${AppLocalizations.of(context)?.cacheExpires ?? 'Expires'}: ${_formatCacheExpiration(_cacheExpirationTime!)}',
                                      style: TextStyle(
                                        fontSize: 10,
                                        color: Colors.blue[600],
                                      ),
                                    ),
                                ],
                              ),
                            ),
                            TextButton.icon(
                              icon: const Icon(Icons.refresh, size: 16),
                              label: Text(
                                  AppLocalizations.of(context)?.refresh ??
                                      'Refresh'),
                              onPressed: _forceRefreshSearch,
                              style: TextButton.styleFrom(
                                padding: const EdgeInsets.symmetric(
                                    horizontal: 8, vertical: 4),
                                minimumSize: Size.zero,
                                tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                              ),
                            ),
                          ],
                        ),
                      ),
                    if (_isFromLocalDb)
                      Container(
                        padding: const EdgeInsets.all(8.0),
                        color: Colors.amber[50],
                        child: Row(
                          children: [
                            Icon(Icons.storage,
                                size: 16, color: Colors.amber[700]),
                            const SizedBox(width: 8),
                            Expanded(
                              child: Text(
                                AppLocalizations.of(context)
                                        ?.resultsFromLocalDb ??
                                    'Results from local database',
                                style: TextStyle(
                                  color: Colors.amber[700],
                                  fontSize: 12,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    if (_hasSearched && _searchResults.isNotEmpty)
                      _buildCategoryFilters(),
                    Expanded(
                      child: RefreshIndicator(
                        onRefresh: () async {
                          await _forceRefreshSearch();
                        },
                        child: _buildBodyState(),
                      ),
                    ),
                  ],
                ),
              )
            else
              // Show empty state with recommendations when search field is empty
              // This happens both when user hasn't searched yet AND when user clears the search field
              Expanded(
                child: RefreshIndicator(
                  onRefresh: () async {
                    // Reload recommendations when user pulls to refresh
                    EnvironmentLogger()
                        .d('RefreshIndicator: reloading recommendations');
                    _recommendedAudiobooksFuture = _getRecommendedAudiobooks();
                    setState(() {});
                    // Wait a bit for the future to complete
                    await Future.delayed(const Duration(milliseconds: 100));
                  },
                  child: _buildEmptyState(context),
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildBodyState() {
    if (_errorKind != null) {
      return _buildErrorState();
    }
    return _buildSearchResults();
  }

  /// Builds empty state with recommendations or search prompt.
  Widget _buildEmptyState(BuildContext context) {
    final structuredLogger = StructuredLogger();
    safeUnawaited(structuredLogger.log(
      level: 'debug',
      subsystem: 'recommendations',
      message: '_buildEmptyState called',
      context: 'recommendations_ui',
      extra: {
        'search_text': _searchController.text,
        'search_text_empty': _searchController.text.isEmpty,
      },
    ));

    // Show recommendations if search field is empty
    if (_searchController.text.isEmpty) {
      // Check current auth status
      final authStatus = ref.watch(authStatusProvider);
      final isAuthenticated = authStatus.value?.isAuthenticated ?? false;

      safeUnawaited(structuredLogger.log(
        level: 'debug',
        subsystem: 'recommendations',
        message: 'Checking authentication status in _buildEmptyState',
        context: 'recommendations_ui',
        extra: {
          'is_authenticated': isAuthenticated,
          'future_exists': _recommendedAudiobooksFuture != null,
        },
      ));

      // Always recreate future if user is authenticated to ensure fresh data
      // This handles both initial load and auth status changes
      // Also check if auth status is loading (might be updating after login)
      if (isAuthenticated || authStatus.isLoading) {
        // Always recreate future when authenticated or when auth status is loading
        // This ensures that if user just authenticated, we load recommendations
        // even if provider hasn't updated yet
        safeUnawaited(structuredLogger.log(
          level: 'info',
          subsystem: 'recommendations',
          message:
              'User authenticated or auth loading, (re)initializing recommendations future',
          context: 'recommendations_ui',
          extra: {
            'is_authenticated': isAuthenticated,
            'is_loading': authStatus.isLoading,
            'future_exists': _recommendedAudiobooksFuture != null,
          },
        ));
        _recommendedAudiobooksFuture = _getRecommendedAudiobooks();
      } else {
        // User not authenticated - create future only if it doesn't exist
        // The future will return empty list after checking auth
        if (_recommendedAudiobooksFuture == null) {
          safeUnawaited(structuredLogger.log(
            level: 'debug',
            subsystem: 'recommendations',
            message:
                'User not authenticated, initializing recommendations future (will return empty)',
            context: 'recommendations_ui',
          ));
          _recommendedAudiobooksFuture = _getRecommendedAudiobooks();
        }
      }

      return FutureBuilder<List<RecommendedAudiobook>>(
        future: _recommendedAudiobooksFuture,
        builder: (context, snapshot) {
          // Show loading state
          if (snapshot.connectionState == ConnectionState.waiting) {
            return Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const CircularProgressIndicator(),
                  const SizedBox(height: 16),
                  Text(
                    AppLocalizations.of(context)?.loading ?? 'Loading...',
                    style: Theme.of(context).textTheme.bodyMedium,
                  ),
                ],
              ),
            );
          }

          // Show error state (but still show search prompt)
          if (snapshot.hasError) {
            // Log error for debugging
            safeUnawaited(structuredLogger.log(
              level: 'error',
              subsystem: 'recommendations',
              message: 'Failed to load recommendations',
              context: 'recommendations_ui',
              extra: {
                'error': snapshot.error.toString(),
                'stack_trace': snapshot.stackTrace?.toString() ?? '',
              },
            ));
          }

          final recommendations = snapshot.data ?? [];

          // Log recommendations count for debugging
          if (recommendations.isNotEmpty) {
            safeUnawaited(structuredLogger.log(
              level: 'info',
              subsystem: 'recommendations',
              message: 'Successfully displaying recommended audiobooks',
              context: 'recommendations_ui',
              extra: {
                'recommendations_count': recommendations.length,
              },
            ));
          } else if (snapshot.connectionState == ConnectionState.done) {
            safeUnawaited(structuredLogger.log(
              level: 'warning',
              subsystem: 'recommendations',
              message: 'No recommended audiobooks loaded (empty result)',
              context: 'recommendations_ui',
              extra: {
                'note': 'This may indicate a parsing or network issue',
              },
            ));
          }

          return SingleChildScrollView(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Show search history prominently if available
                if (_searchHistory.isNotEmpty) ...[
                  Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 12,
                    ),
                    child: Row(
                      children: [
                        Icon(
                          Icons.history,
                          size: 20,
                          color: Theme.of(context)
                              .colorScheme
                              .onSurface
                              .withValues(alpha: 0.7),
                        ),
                        const SizedBox(width: 8),
                        Text(
                          AppLocalizations.of(context)?.recentSearches ??
                              'Recent searches',
                          style:
                              Theme.of(context).textTheme.titleMedium?.copyWith(
                                    fontWeight: FontWeight.w600,
                                  ),
                        ),
                      ],
                    ),
                  ),
                  SizedBox(
                    height:
                        ResponsiveUtils.isVerySmallScreen(context) ? 45 : 50,
                    child: ListView.builder(
                      scrollDirection: Axis.horizontal,
                      padding: EdgeInsets.symmetric(
                        horizontal:
                            ResponsiveUtils.isVerySmallScreen(context) ? 8 : 12,
                      ),
                      itemCount: _searchHistory.length > 10
                          ? 10
                          : _searchHistory.length,
                      itemBuilder: (context, index) {
                        final query = _searchHistory[index];
                        return Padding(
                          padding: EdgeInsets.symmetric(
                            horizontal: ResponsiveUtils.isVerySmallScreen(
                              context,
                            )
                                ? 2
                                : 4,
                          ),
                          child: ActionChip(
                            label: Text(
                              query,
                              style: TextStyle(
                                fontSize: ResponsiveUtils.isVerySmallScreen(
                                  context,
                                )
                                    ? 12
                                    : 14,
                              ),
                            ),
                            onPressed: () {
                              _searchController.text = query;
                              _searchFocusNode.unfocus();
                              _performSearch();
                            },
                            avatar: Icon(
                              Icons.history,
                              size: ResponsiveUtils.isVerySmallScreen(context)
                                  ? 14
                                  : 16,
                            ),
                          ),
                        );
                      },
                    ),
                  ),
                  const SizedBox(height: 8),
                ],
                const SizedBox(height: 8),
                // Always show recommendations widget, even if empty
                // Widget handles empty state internally
                RecommendedAudiobooksWidget(
                  audiobooks: recommendations,
                ),
                const SizedBox(height: 24),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 32),
                  child: Column(
                    children: [
                      Icon(
                        Icons.search,
                        size: 64,
                        color: Theme.of(context)
                            .colorScheme
                            .onSurface
                            .withValues(alpha: 0.3),
                      ),
                      const SizedBox(height: 16),
                      Text(
                        AppLocalizations.of(context)?.enterSearchTerm ??
                            'Enter a search term to begin',
                        style: Theme.of(context).textTheme.bodyLarge,
                        textAlign: TextAlign.center,
                      ),
                    ],
                  ),
                ),
              ],
            ),
          );
        },
      );
    }

    // Show search prompt if search field has text but no results
    return Semantics(
      label: AppLocalizations.of(context)?.enterSearchTerm ??
          'Enter a search term to begin',
      child: Center(
        child: Text(
          AppLocalizations.of(context)?.enterSearchTerm ??
              'Enter a search term to begin',
          textAlign: TextAlign.center,
        ),
      ),
    );
  }

  /// Returns list of recommended audiobooks.
  ///
  /// Gets new books from different categories, sorted by newest first.
  /// Only returns books from actual category pages, no static fallback.
  Future<List<RecommendedAudiobook>> _getRecommendedAudiobooks() async {
    final structuredLogger = StructuredLogger();
    await structuredLogger.log(
      level: 'debug',
      subsystem: 'recommendations',
      message: '_getRecommendedAudiobooks called',
      context: 'recommendations_load',
    );
    return _getCategoryRecommendations();
  }

  /// Gets recommendations from different categories.
  ///
  /// Fetches new books from popular categories using viewforum.php.
  /// Gets the first few books from each category (they are usually newest).
  Future<List<RecommendedAudiobook>> _getCategoryRecommendations() async {
    final structuredLogger = StructuredLogger();
    try {
      await structuredLogger.log(
        level: 'info',
        subsystem: 'recommendations',
        message: 'Starting to load category recommendations',
        context: 'recommendations_load',
      );

      // Check if user is authenticated before making requests
      // Use authStatusProvider which tracks auth status in real-time
      var authStatus = ref.read(authStatusProvider);
      var isAuthenticated = authStatus.value?.isAuthenticated ?? false;

      // If provider shows not authenticated but is loading, wait a bit for update
      // This handles the case when auth just completed but provider hasn't updated yet
      if (!isAuthenticated && authStatus.isLoading) {
        await structuredLogger.log(
          level: 'debug',
          subsystem: 'recommendations',
          message: 'Auth status is loading, waiting for update',
          context: 'recommendations_load',
        );
        // Wait a bit for provider to update (max 1 second)
        await Future.delayed(const Duration(milliseconds: 500));
        authStatus = ref.read(authStatusProvider);
        isAuthenticated = authStatus.value?.isAuthenticated ?? false;
      }

      // If still not authenticated, try to refresh auth status
      // This handles the case when auth completed but provider hasn't updated
      if (!isAuthenticated && !authStatus.isLoading) {
        await structuredLogger.log(
          level: 'debug',
          subsystem: 'recommendations',
          message: 'Auth status not authenticated, trying to refresh',
          context: 'recommendations_load',
        );
        try {
          await ref.read(authRepositoryProvider).refreshAuthStatus();
          // After refresh, check status directly via isLoggedIn() instead of waiting for provider
          // This is more reliable as provider might not update immediately
          final loggedIn = await ref.read(authRepositoryProvider).isLoggedIn();
          if (loggedIn) {
            isAuthenticated = true;
            await structuredLogger.log(
              level: 'info',
              subsystem: 'recommendations',
              message:
                  'Auth status refreshed - user is authenticated (checked directly)',
              context: 'recommendations_load',
            );
          } else {
            // Wait a bit for provider to update after refresh
            await Future.delayed(const Duration(milliseconds: 300));
            authStatus = ref.read(authStatusProvider);
            isAuthenticated = authStatus.value?.isAuthenticated ?? false;
          }
        } on Exception catch (e) {
          await structuredLogger.log(
            level: 'warning',
            subsystem: 'recommendations',
            message: 'Failed to refresh auth status',
            context: 'recommendations_load',
            extra: {
              'error': e.toString(),
            },
          );
        }
      }

      await structuredLogger.log(
        level: 'debug',
        subsystem: 'recommendations',
        message: 'Checking authentication status',
        context: 'recommendations_load',
        extra: {
          'is_authenticated': isAuthenticated,
          'auth_status_value': authStatus.value?.toString() ?? 'null',
          'auth_status_loading': authStatus.isLoading,
        },
      );

      if (!isAuthenticated) {
        await structuredLogger.log(
          level: 'warning',
          subsystem: 'recommendations',
          message: 'User not authenticated, skipping category recommendations',
          context: 'recommendations_load',
        );
        return [];
      }

      await structuredLogger.log(
        level: 'info',
        subsystem: 'recommendations',
        message:
            'User is authenticated, proceeding with category recommendations',
        context: 'recommendations_load',
      );

      final endpointManager = ref.read(endpointManagerProvider);
      final baseUrl = await endpointManager.getActiveEndpoint();
      final dio = await DioClient.instance;
      final categoryParser = category_parser.CategoryParser();

      await structuredLogger.log(
        level: 'debug',
        subsystem: 'recommendations',
        message: 'Initialized services for recommendations',
        context: 'recommendations_load',
        extra: {
          'base_url': baseUrl,
        },
      );

      final recommendations = <RecommendedAudiobook>[];
      final seenIds = <String>{};

      // Use popular categories from CategoryConstants
      const categories = CategoryConstants.popularCategoryIds;
      if (categories.isEmpty) {
        await structuredLogger.log(
          level: 'warning',
          subsystem: 'recommendations',
          message: 'No popular categories found in CategoryConstants',
          context: 'recommendations_load',
        );
        return [];
      }

      await structuredLogger.log(
        level: 'info',
        subsystem: 'recommendations',
        message: 'Loading recommendations from categories',
        context: 'recommendations_load',
        extra: {
          'categories_count': categories.length,
          'category_ids': categories,
        },
      );

      // Get 5-10 new books from each category (first books are usually newest)
      // Distribute evenly: if we have 9 categories, get 1-2 from each to reach ~10 total
      final booksPerCategory = (10 / categories.length).ceil().clamp(1, 3);
      await structuredLogger.log(
        level: 'debug',
        subsystem: 'recommendations',
        message: 'Calculated books per category',
        context: 'recommendations_load',
        extra: {
          'books_per_category': booksPerCategory,
        },
      );

      // Fetch from all categories in parallel with timeout
      final futures = categories.map((categoryId) async {
        try {
          final result = await _fetchNewBooksFromCategory(
            dio: dio,
            endpoint: baseUrl,
            categoryParser: categoryParser,
            categoryId: categoryId,
            limit: booksPerCategory,
          ).timeout(
            const Duration(seconds: 8),
            onTimeout: () async {
              await structuredLogger.log(
                level: 'warning',
                subsystem: 'recommendations',
                message: 'Timeout loading category',
                context: 'recommendations_load',
                extra: {
                  'category_id': categoryId,
                },
              );
              return <RecommendedAudiobook>[];
            },
          );
          await structuredLogger.log(
            level: 'info',
            subsystem: 'recommendations',
            message: 'Loaded books from category',
            context: 'recommendations_load',
            extra: {
              'category_id': categoryId,
              'books_count': result.length,
            },
          );
          return result;
        } on Exception catch (e) {
          await structuredLogger.log(
            level: 'warning',
            subsystem: 'recommendations',
            message: 'Error loading category',
            context: 'recommendations_load',
            extra: {
              'category_id': categoryId,
              'error': e.toString(),
            },
          );
          return <RecommendedAudiobook>[];
        }
      });

      final results = await Future.wait(futures);

      // Combine results from all categories
      for (final categoryResults in results) {
        for (final result in categoryResults) {
          if (seenIds.contains(result.id)) continue;
          seenIds.add(result.id);
          recommendations.add(result);
          if (recommendations.length >= 10) break;
        }
        if (recommendations.length >= 10) break;
      }

      await structuredLogger.log(
        level: 'info',
        subsystem: 'recommendations',
        message: 'Successfully loaded recommended audiobooks',
        context: 'recommendations_load',
        extra: {
          'total_recommendations': recommendations.length,
          'unique_ids_count': seenIds.length,
        },
      );
      return recommendations;
    } on Exception catch (e, stackTrace) {
      await structuredLogger.log(
        level: 'error',
        subsystem: 'recommendations',
        message: 'Failed to load category recommendations',
        context: 'recommendations_load',
        extra: {
          'error': e.toString(),
          'stack_trace': stackTrace.toString(),
        },
      );
      // Return empty list on error - no fallback to static list
      return [];
    }
  }

  /// Fetches new books from a specific category using viewforum.php.
  ///
  /// Gets the first few books from the category page (they are usually newest).
  Future<List<RecommendedAudiobook>> _fetchNewBooksFromCategory({
    required Dio dio,
    required String endpoint,
    required category_parser.CategoryParser categoryParser,
    required String categoryId,
    required int limit,
  }) async {
    final structuredLogger = StructuredLogger();
    try {
      // Use viewforum.php to get books from category
      // First books on the page are usually the newest
      // Build URL properly to avoid double slashes
      final baseUrl = endpoint.endsWith('/')
          ? endpoint.substring(0, endpoint.length - 1)
          : endpoint;
      final forumUrl = '$baseUrl/forum/viewforum.php?f=$categoryId';

      await structuredLogger.log(
        level: 'debug',
        subsystem: 'recommendations',
        message: 'Fetching books from category',
        context: 'category_fetch',
        extra: {
          'category_id': categoryId,
          'forum_url': forumUrl,
          'limit': limit,
        },
      );

      final response = await dio.get(
        forumUrl,
        options: Options(
          responseType: ResponseType.plain,
          headers: {
            'Accept': 'text/html,application/xhtml+xml,application/xml',
            'Accept-Charset': 'windows-1251,utf-8',
          },
        ),
      );

      await structuredLogger.log(
        level: 'info',
        subsystem: 'recommendations',
        message: 'HTTP response received for category',
        context: 'category_fetch',
        extra: {
          'category_id': categoryId,
          'status_code': response.statusCode,
          'response_url': response.realUri.toString(),
          'response_size_bytes': response.data?.toString().length ?? 0,
        },
      );

      // Check if we got redirected to login page
      // Dio follows redirects by default, so check the final URL
      final responseUrl = response.realUri.toString();
      if (responseUrl.contains('login.php')) {
        await structuredLogger.log(
          level: 'warning',
          subsystem: 'recommendations',
          message: 'Category requires authentication (redirected to login)',
          context: 'category_fetch',
          extra: {
            'category_id': categoryId,
            'response_url': responseUrl,
          },
        );
        return [];
      }

      if (response.statusCode != 200) {
        await structuredLogger.log(
          level: 'warning',
          subsystem: 'recommendations',
          message: 'Failed to fetch category',
          context: 'category_fetch',
          extra: {
            'category_id': categoryId,
            'status_code': response.statusCode,
          },
        );
        return [];
      }

      // Check if response contains login page content
      final responseText = response.data.toString();
      if (responseText.contains('login.php') ||
          responseText.contains('Вход в систему') ||
          responseText.contains('Имя пользователя') ||
          responseText.contains('Пароль')) {
        await structuredLogger.log(
          level: 'warning',
          subsystem: 'recommendations',
          message: 'Category returned login page instead of topics',
          context: 'category_fetch',
          extra: {
            'category_id': categoryId,
          },
        );
        return [];
      }

      // Parse topics from category page using CategoryParser
      await structuredLogger.log(
        level: 'debug',
        subsystem: 'recommendations',
        message: 'Parsing topics from category page',
        context: 'category_fetch',
        extra: {
          'category_id': categoryId,
          'response_text_length': responseText.length,
        },
      );

      final topics = await categoryParser.parseCategoryTopics(
        responseText,
      );

      await structuredLogger.log(
        level: 'info',
        subsystem: 'recommendations',
        message: 'Parsed topics from category',
        context: 'category_fetch',
        extra: {
          'category_id': categoryId,
          'topics_count': topics.length,
        },
      );

      // Convert to RecommendedAudiobook
      // Topics are already sorted by last_post_date (newest first) from parser
      // Try to get recent topics (within last 180 days), but if there are not enough,
      // fall back to just taking the newest topics regardless of date
      final now = DateTime.now();
      final oneHundredEightyDaysAgo = now.subtract(const Duration(days: 180));

      await structuredLogger.log(
        level: 'debug',
        subsystem: 'recommendations',
        message: 'Filtering topics by date',
        context: 'category_fetch',
        extra: {
          'category_id': categoryId,
          'topics_before_filter': topics.length,
          'cutoff_date': oneHundredEightyDaysAgo.toIso8601String(),
        },
      );

      // Filter topics by date and collect stats
      var topicsWithoutDate = 0;
      var topicsTooOld = 0;
      final recentTopics = topics.where((topic) {
        // Filter by date - prefer topics with last post within last 180 days
        final lastPostDate = topic['last_post_date'] as DateTime?;
        if (lastPostDate == null) {
          topicsWithoutDate++;
          return false;
        }
        final isRecent = lastPostDate.isAfter(oneHundredEightyDaysAgo);
        if (!isRecent) {
          topicsTooOld++;
        }
        return isRecent;
      }).toList();

      await structuredLogger.log(
        level: 'debug',
        subsystem: 'recommendations',
        message: 'Date filtering results',
        context: 'category_fetch',
        extra: {
          'category_id': categoryId,
          'topics_before_filter': topics.length,
          'recent_topics_count': recentTopics.length,
          'topics_without_date': topicsWithoutDate,
          'topics_too_old': topicsTooOld,
        },
      );

      // Use recent topics if we have enough, otherwise fall back to all topics
      // This ensures we always get recommendations even if fresh releases are rare
      final topicsToUse = recentTopics.length >= limit ? recentTopics : topics;

      await structuredLogger.log(
        level: 'info',
        subsystem: 'recommendations',
        message: 'Selected topics for recommendations',
        context: 'category_fetch',
        extra: {
          'category_id': categoryId,
          'using_recent_only': recentTopics.length >= limit,
          'topics_selected': topicsToUse.length,
        },
      );

      final books = topicsToUse
          .take(limit)
          .map((topic) {
            final topicId = topic['id'] as String? ?? '';
            final title = topic['title'] as String? ?? 'Unknown Title';
            final author = topic['author'] as String? ?? 'Unknown Author';
            final size = topic['size'] as String?;
            final coverUrl = topic['coverUrl'] as String?;
            final categoryName =
                CategoryConstants.categoryNameMap[categoryId] ?? 'Audiobook';

            return RecommendedAudiobook(
              id: topicId,
              title: title,
              author: author,
              size: size,
              coverUrl: coverUrl,
              genre: categoryName,
            );
          })
          .where((book) => book.id.isNotEmpty) // Filter out invalid entries
          .toList();

      await structuredLogger.log(
        level: 'info',
        subsystem: 'recommendations',
        message: 'Converted valid books from category',
        context: 'category_fetch',
        extra: {
          'category_id': categoryId,
          'valid_books_count': books.length,
          'topics_parsed': topics.length,
        },
      );
      return books;
    } on Exception catch (e, stackTrace) {
      await structuredLogger.log(
        level: 'error',
        subsystem: 'recommendations',
        message: 'Exception fetching category',
        context: 'category_fetch',
        extra: {
          'category_id': categoryId,
          'error': e.toString(),
          'stack_trace': stackTrace.toString(),
        },
      );
      return [];
    }
  }

  /// Builds category filter chips.
  Widget _buildCategoryFilters() {
    final categories = _getAvailableCategories();
    if (categories.isEmpty) return const SizedBox.shrink();

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text(
                AppLocalizations.of(context)?.filtersLabel ?? 'Filters:',
                style: Theme.of(context).textTheme.titleSmall?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
              ),
              if (_selectedCategories.isNotEmpty) ...[
                const SizedBox(width: 8),
                OutlinedButton.icon(
                  onPressed: () {
                    setState(_selectedCategories.clear);
                  },
                  icon: const Icon(Icons.clear, size: 16),
                  label: Text(
                      AppLocalizations.of(context)?.resetButton ?? 'Reset'),
                  style: OutlinedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 12,
                      vertical: 8,
                    ),
                    minimumSize: Size.zero,
                    tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                  ),
                ),
              ],
            ],
          ),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: categories.map((category) {
              final isSelected = _selectedCategories.contains(category);
              return FilterChip(
                label: Text(
                  category,
                  style: TextStyle(
                    fontWeight:
                        isSelected ? FontWeight.w600 : FontWeight.normal,
                  ),
                ),
                selected: isSelected,
                selectedColor: Theme.of(context).colorScheme.primaryContainer,
                checkmarkColor:
                    Theme.of(context).colorScheme.onPrimaryContainer,
                onSelected: (selected) {
                  setState(() {
                    if (selected) {
                      _selectedCategories.add(category);
                    } else {
                      _selectedCategories.remove(category);
                    }
                  });
                },
              );
            }).toList(),
          ),
        ],
      ),
    );
  }

  /// Gets unique categories from search results.
  Set<String> _getAvailableCategories() {
    final categories = <String>{};
    for (final result in _searchResults) {
      final category = result['category'] as String?;
      if (category != null && category.isNotEmpty) {
        categories.add(category);
      }
    }
    return categories;
  }

  /// Gets filtered search results based on selected categories.
  List<Map<String, dynamic>> _getFilteredResults() {
    if (_selectedCategories.isEmpty) {
      return _searchResults;
    }
    return _searchResults.where((result) {
      final category = result['category'] as String? ??
          (AppLocalizations.of(context)?.otherCategory ?? 'Other');
      return _selectedCategories.contains(category);
    }).toList();
  }

  /// Builds search history list widget.
  Widget _buildSearchHistory() => Container(
        constraints: const BoxConstraints(maxHeight: 300),
        margin: const EdgeInsets.symmetric(horizontal: 16),
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.surface,
          borderRadius: BorderRadius.circular(8),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.1),
              blurRadius: 4,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Padding(
              padding: const EdgeInsets.all(12.0),
              child: Row(
                children: [
                  Text(
                    AppLocalizations.of(context)?.searchHistoryTitle ??
                        'Search History',
                    style: Theme.of(context).textTheme.titleSmall?.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                  ),
                  const Spacer(),
                  if (_searchHistory.isNotEmpty)
                    TextButton(
                      onPressed: () async {
                        if (_historyService != null) {
                          await _historyService!.clearHistory();
                          await _loadSearchHistory();
                          if (mounted) {
                            setState(() {
                              _showHistory = false;
                            });
                          }
                        }
                      },
                      child: Text(
                          AppLocalizations.of(context)?.clearButton ?? 'Clear'),
                    ),
                ],
              ),
            ),
            Flexible(
              child: ListView.builder(
                shrinkWrap: true,
                itemCount: _searchHistory.length,
                itemBuilder: (context, index) {
                  final query = _searchHistory[index];
                  return ListTile(
                    leading: const Icon(Icons.history, size: 20),
                    title: Text(query),
                    trailing: IconButton(
                      icon: const Icon(Icons.close, size: 18),
                      onPressed: () async {
                        if (_historyService != null) {
                          await _historyService!.removeSearchQuery(query);
                          await _loadSearchHistory();
                          if (mounted) {
                            setState(() {
                              _showHistory = _searchHistory.isNotEmpty &&
                                  _searchController.text.isEmpty;
                            });
                          }
                        }
                      },
                    ),
                    onTap: () {
                      _searchController.text = query;
                      _searchFocusNode.unfocus();
                      setState(() {
                        _showHistory = false;
                      });
                      _performSearch();
                    },
                  );
                },
              ),
            ),
          ],
        ),
      );

  Widget _buildSearchResults() {
    final filteredResults = _getFilteredResults();

    final localizations = AppLocalizations.of(context);

    if (filteredResults.isEmpty && _searchResults.isNotEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Semantics(
              label: 'No results match the selected filters',
              child: const Icon(
                Icons.filter_alt_off,
                size: 48,
                color: Colors.grey,
              ),
            ),
            const SizedBox(height: 16),
            Text(
              localizations?.noResultsForFilters ??
                  'No results match the selected filters',
              style: Theme.of(context).textTheme.bodyLarge,
              textAlign: TextAlign.center,
            ),
          ],
        ),
      );
    }

    if (filteredResults.isEmpty) {
      final query = _searchController.text.trim();
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(32.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                Icons.search_off,
                size: 64,
                color: Theme.of(context)
                    .colorScheme
                    .onSurface
                    .withValues(alpha: 0.4),
              ),
              const SizedBox(height: 16),
              Text(
                localizations?.noResults ?? 'No results found',
                style: Theme.of(context).textTheme.titleLarge,
                textAlign: TextAlign.center,
              ),
              if (query.isNotEmpty) ...[
                const SizedBox(height: 8),
                Text(
                  localizations?.noResultsForQuery(query) ??
                      'Nothing found for query: "$query"',
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: Theme.of(context)
                            .colorScheme
                            .onSurface
                            .withValues(alpha: 0.6),
                      ),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 8),
                Text(
                  localizations?.tryDifferentKeywords ??
                      'Try changing keywords',
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: Theme.of(context)
                            .colorScheme
                            .onSurface
                            .withValues(alpha: 0.5),
                      ),
                  textAlign: TextAlign.center,
                ),
              ],
              const SizedBox(height: 24),
              OutlinedButton.icon(
                onPressed: () {
                  _searchController.clear();
                  setState(() {
                    _searchResults = [];
                    _hasSearched = false;
                  });
                },
                icon: const Icon(Icons.clear),
                label: Text(
                  localizations?.clearSearch ?? 'Clear search',
                ),
              ),
            ],
          ),
        ),
      );
    }

    return GroupedAudiobookList(
      audiobooks: filteredResults,
      onAudiobookTap: (id) {
        context.push('/topic/$id');
      },
      loadMore: _hasMore && !_isLoadingMore ? _loadMore : null,
      hasMore: _hasMore,
      isLoadingMore: _isLoadingMore,
      favoriteIds: ref.watch(favoriteIdsProvider),
      onFavoriteToggle: _toggleFavorite,
    );
  }

  Future<void> _toggleFavorite(String topicId, bool isFavorite) async {
    final notifier = ref.read(favoriteIdsProvider.notifier);

    // Get audiobook data from search results
    Map<String, dynamic>? audiobookMap;
    try {
      audiobookMap = _searchResults.firstWhere(
        (r) => (r['id'] as String?) == topicId,
      );
    } on Exception {
      // Audiobook not found in search results
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              AppLocalizations.of(context)?.failedToAddToFavorites ??
                  'Failed to add to favorites',
            ),
          ),
        );
      }
      return;
    }

    try {
      final wasAdded = await notifier.toggleFavorite(
        topicId,
        audiobookMap: audiobookMap,
      );

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              wasAdded
                  ? (AppLocalizations.of(context)?.addedToFavorites ??
                      'Added to favorites')
                  : (AppLocalizations.of(context)?.removedFromFavorites ??
                      'Removed from favorites'),
            ),
            duration: const Duration(seconds: 2),
          ),
        );
      }
    } on Exception {
      // Show error message
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              isFavorite
                  ? (AppLocalizations.of(context)?.failedToAddToFavorites ??
                      'Failed to add to favorites')
                  : (AppLocalizations.of(context)
                          ?.failedToRemoveFromFavorites ??
                      'Failed to remove from favorites'),
            ),
          ),
        );
      }
    }
  }

  void _onScroll() {
    if (!_hasMore || _isLoadingMore) return;
    if (_scrollController.position.pixels >=
        _scrollController.position.maxScrollExtent - 200) {
      _loadMore();
    }
  }

  Future<void> _loadMore() async {
    if (_isLoadingMore || !_hasMore) return;
    _isLoadingMore = true;
    try {
      final query = _searchController.text.trim();
      if (query.isEmpty) return;
      final endpointManager = ref.read(endpointManagerProvider);
      final activeEndpoint = await endpointManager.getActiveEndpoint();
      final dio = await DioClient.instance;
      final res = await dio.get(
        '$activeEndpoint/forum/tracker.php',
        queryParameters: {
          'nm': query,
          'c': CategoryConstants
              .audiobooksCategoryId, // Try to filter by category 33 (audiobooks)
          'o': '1',
          'start': _searchResults.length,
        },
        options: Options(
          responseType: ResponseType
              .bytes, // Get raw bytes to decode Windows-1251 correctly
        ),
      );
      if (res.statusCode == 200) {
        try {
          // Pass response data and headers to parser for proper encoding detection
          final more = await _parser.parseSearchResults(
            res.data,
            contentType: res.headers.value('content-type'),
            baseUrl: activeEndpoint,
          );

          // CRITICAL: Filter results to only include audiobooks
          final audiobookMore = _filterAudiobookResults(more);

          if (mounted) {
            final moreResults = audiobookMore.map(_audiobookToMap).toList();
            setState(() {
              _searchResults.addAll(moreResults);
              _hasMore = audiobookMore.isNotEmpty;
            });
            // Update statistics for newly added items that don't have them
            safeUnawaited(_updateMissingStatistics(moreResults));
          }
        } on ParsingFailure catch (e) {
          await StructuredLogger().log(
            level: 'error',
            subsystem: 'search',
            message: 'Failed to parse additional search results',
            context: 'search_pagination',
            cause: e.message,
            extra: {
              'start_offset': _startOffset,
              'error_type': 'ParsingFailure',
            },
          );
          // Don't show error to user for pagination failures, just stop loading more
          if (mounted) {
            setState(() {
              _hasMore = false;
            });
          }
        }
      }
    } on Object {
      // ignore pagination errors
    } finally {
      _isLoadingMore = false;
    }
  }

  Widget _buildErrorState() {
    String title;
    var actions = <Widget>[];
    var message = _errorMessage ?? '';
    IconData iconData;
    Color iconColor;

    final localizations = AppLocalizations.of(context);

    switch (_errorKind) {
      case 'auth':
        title =
            localizations?.authenticationRequired ?? 'Authentication Required';
        iconData = Icons.lock_outline;
        iconColor = Colors.orange.shade600;
        actions = [
          ElevatedButton.icon(
            onPressed: _handleLogin,
            icon: const Icon(Icons.vpn_key),
            label: Text(localizations?.loginButton ?? 'Login to RuTracker'),
          ),
        ];
        message = localizations?.loginRequiredForSearch ??
            'Please login to RuTracker to access search functionality.';
        break;
      case 'timeout':
        title = localizations?.timeoutError ?? 'Request Timed Out';
        iconData = Icons.timer_off;
        iconColor = Colors.amber.shade600;
        actions = [
          ElevatedButton.icon(
            onPressed: _performSearch,
            icon: const Icon(Icons.refresh),
            label: Text(localizations?.retry ?? 'Retry'),
          ),
          OutlinedButton.icon(
            onPressed: () => unawaited(Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const MirrorSettingsScreen()),
            ).then((_) => _loadActiveHost())),
            icon: const Icon(Icons.dns),
            label: Text(localizations?.changeMirror ?? 'Change Mirror'),
          ),
        ];
        message = localizations?.timeoutError ??
            'Request took too long. Please check your connection and try again.';
        break;
      case 'mirror':
        title = localizations?.networkErrorUser ?? 'Mirror Unavailable';
        iconData = Icons.cloud_off;
        iconColor = Colors.red.shade600;
        actions = [
          ElevatedButton.icon(
            onPressed: () async {
              // Test all mirrors and select the best one
              final endpointManager = ref.read(endpointManagerProvider);
              try {
                await endpointManager.initialize();
                await _loadActiveHost();
                await _performSearch();
              } on Exception {
                // Show mirror settings if auto-fix fails
                if (mounted) {
                  unawaited(Navigator.push(
                    context,
                    MaterialPageRoute(
                        builder: (_) => const MirrorSettingsScreen()),
                  ).then((_) => _loadActiveHost()));
                }
              }
            },
            icon: const Icon(Icons.auto_fix_high),
            label: Text(localizations?.retry ?? 'Auto-Fix'),
          ),
          OutlinedButton.icon(
            onPressed: () => unawaited(Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const MirrorSettingsScreen()),
            ).then((_) => _loadActiveHost())),
            icon: const Icon(Icons.settings),
            label: Text(localizations?.chooseMirror ?? 'Manage Mirrors'),
          ),
        ];
        message = localizations?.connectionFailed ??
            'Connection failed. Please check your internet connection or try a different mirror.';
        break;
      case 'network':
      default:
        title = localizations?.networkErrorUser ?? 'Network Error';
        iconData = Icons.wifi_off;
        iconColor = Colors.red.shade400;
        actions = [
          ElevatedButton.icon(
            onPressed:
                _performSearch, // Task 3.3: Retry search on encoding/parsing errors
            icon: const Icon(Icons.refresh),
            label: Text(localizations?.retry ?? 'Retry'),
          ),
          OutlinedButton.icon(
            onPressed: () => unawaited(Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const MirrorSettingsScreen()),
            ).then((_) => _loadActiveHost())),
            icon: const Icon(Icons.dns),
            label: Text(localizations?.changeMirror ?? 'Change Mirror'),
          ),
        ];
        // Task 3.3: Use specific error message if available, otherwise use default
        message = (_errorMessage?.isNotEmpty ?? false)
            ? _errorMessage!
            : (localizations?.networkConnectionError ??
                'Could not connect. Check your internet or choose another mirror in Settings → Sources.');
        break;
    }

    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: iconColor.withValues(alpha: 0.1),
                shape: BoxShape.circle,
              ),
              child: Icon(iconData, size: 48, color: iconColor),
            ),
            const SizedBox(height: 24),
            Text(
              title,
              style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 12),
            Text(
              message,
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: Colors.grey.shade600,
                  ),
            ),
            const SizedBox(height: 32),
            Wrap(
              spacing: 12,
              runSpacing: 12,
              alignment: WrapAlignment.center,
              children: actions,
            ),
            if (_activeHost != null) ...[
              const SizedBox(height: 24),
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                decoration: BoxDecoration(
                  color: Colors.grey.shade100,
                  borderRadius: BorderRadius.circular(16),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.dns, size: 16, color: Colors.grey.shade600),
                    const SizedBox(width: 6),
                    Text(
                      AppLocalizations.of(context)
                              ?.currentMirrorLabel(_activeHost!) ??
                          'Current mirror: $_activeHost',
                      style: TextStyle(
                        fontSize: 12,
                        color: Colors.grey.shade600,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  /// Formats cache expiration time for display.
  String _formatCacheExpiration(DateTime expirationTime) {
    final now = DateTime.now();
    final difference = expirationTime.difference(now);

    if (difference.isNegative) {
      return AppLocalizations.of(context)?.cacheExpired ?? 'Expired';
    }

    if (difference.inDays > 0) {
      return '${difference.inDays} ${AppLocalizations.of(context)?.days ?? 'days'}';
    } else if (difference.inHours > 0) {
      return '${difference.inHours} ${AppLocalizations.of(context)?.hours ?? 'hours'}';
    } else if (difference.inMinutes > 0) {
      return '${difference.inMinutes} ${AppLocalizations.of(context)?.minutes ?? 'minutes'}';
    } else {
      return AppLocalizations.of(context)?.cacheExpiresSoon ?? 'Expires soon';
    }
  }

  Map<String, dynamic> _audiobookToMap(Audiobook audiobook) {
    final structuredLogger = StructuredLogger();
    safeUnawaited(structuredLogger.log(
      level: 'debug',
      subsystem: 'search',
      message: 'Converting Audiobook to Map for display',
      context: 'audiobook_to_map',
      extra: {
        'audiobook_id': audiobook.id,
        'title': audiobook.title,
        'has_cover_url':
            audiobook.coverUrl != null && audiobook.coverUrl!.isNotEmpty,
        'cover_url': audiobook.coverUrl ?? 'null',
        'cover_url_length': audiobook.coverUrl?.length ?? 0,
        'size': audiobook.size,
        'seeders': audiobook.seeders,
        'leechers': audiobook.leechers,
      },
    ));

    return {
      'id': audiobook.id,
      'title': audiobook.title,
      'author': audiobook.author,
      'category': audiobook.category,
      'size': audiobook.size,
      'seeders': audiobook.seeders,
      'leechers': audiobook.leechers,
      'magnetUrl': audiobook.magnetUrl,
      'coverUrl': audiobook.coverUrl,
      'performer': audiobook.performer,
      'genres': audiobook.genres,
      'chapters': audiobook.chapters.map(_chapterToMap).toList(),
      'addedDate': audiobook.addedDate.toIso8601String(),
      'duration': audiobook.duration,
      'bitrate': audiobook.bitrate,
      'audioCodec': audiobook.audioCodec,
    };
  }

  /// Updates statistics (seeders/leechers) for search results that don't have them.
  ///
  /// Makes lightweight requests to topic pages to get statistics for items
  /// where seeders=0 and leechers=0 (likely missing from search results).
  Future<void> _updateMissingStatistics(
      List<Map<String, dynamic>> results) async {
    // Find items that need statistics update
    final itemsNeedingStats = results.where((item) {
      final seeders = item['seeders'] as int? ?? 0;
      final leechers = item['leechers'] as int? ?? 0;
      return seeders == 0 && leechers == 0;
    }).toList();

    if (itemsNeedingStats.isEmpty) {
      return; // No items need statistics update
    }

    // Limit to first 10 items to avoid too many requests
    final itemsToUpdate = itemsNeedingStats.take(10).toList();

    try {
      final endpointManager = ref.read(endpointManagerProvider);
      final baseUrl = await endpointManager.buildUrl('');
      final dio = await DioClient.instance;

      // Update statistics for each item in parallel (with limit)
      final futures = itemsToUpdate.map((item) async {
        final topicId = item['id'] as String? ?? '';
        if (topicId.isEmpty) return;

        try {
          // Make lightweight request to topic page
          final topicUrl = '$baseUrl/forum/viewtopic.php?t=$topicId';
          final response = await dio
              .get(
                topicUrl,
                options: Options(
                  responseType: ResponseType.plain,
                  headers: {
                    'Accept': 'text/html,application/xhtml+xml,application/xml',
                    'Accept-Charset': 'windows-1251,utf-8',
                  },
                ),
              )
              .timeout(const Duration(seconds: 5));

          // Parse statistics from HTML
          final stats = await _parser.parseTopicStatistics(response.data);
          if (stats != null && mounted) {
            // Update the item in _searchResults
            final index = _searchResults.indexWhere(
              (r) => (r['id'] as String? ?? '') == topicId,
            );
            if (index >= 0) {
              setState(() {
                _searchResults[index]['seeders'] = stats['seeders'];
                _searchResults[index]['leechers'] = stats['leechers'];
              });
            }
          }
        } on Exception {
          // Silently fail for individual items
          return;
        }
      });

      await Future.wait(futures);
    } on Exception {
      // Silently fail if batch update fails
    }
  }
}

/// Filters search results to only include audiobooks.
///
/// Since tracker.php returns ALL torrents (movies, games, books, audiobooks, etc.),
/// we need to filter results to only show audiobooks from category 33.
///
/// We identify audiobooks by:
/// 1. Checking if the title contains audiobook-related keywords
/// 2. Checking if the category field indicates it's an audiobook
/// 3. Checking if the result has characteristics of audiobooks (size, format, etc.)
List<Audiobook> _filterAudiobookResults(List<Audiobook> allResults) {
  final audiobookKeywords = [
    'аудиокнига',
    'аудио',
    'радиоспектакль',
    'биография',
    'мемуары',
    'чтение',
    'читает',
    'исполнитель',
    'mp3',
    'flac',
    'm4b',
    'ogg',
  ];

  final excludedKeywords = [
    'фильм',
    'сериал',
    'игра',
    'программа',
    'музыка',
    'альбом',
    'трек',
    'песня',
    'видео',
    'dvd',
    'blu-ray',
    'bdrip',
    'dvdrip',
  ];

  return allResults.where((result) {
    final titleLower = result.title.toLowerCase();
    final categoryLower = result.category.toLowerCase();

    // Check if it contains audiobook-related keywords
    final hasAudiobookKeyword = audiobookKeywords.any(
      (keyword) =>
          titleLower.contains(keyword) || categoryLower.contains(keyword),
    );

    // Check if it contains excluded keywords (likely not an audiobook)
    final hasExcludedKeyword = excludedKeywords.any(
      titleLower.contains,
    );

    // Include if it has audiobook keywords and doesn't have excluded keywords
    // OR if the category explicitly indicates it's an audiobook
    return (hasAudiobookKeyword && !hasExcludedKeyword) ||
        categoryLower.contains('аудиокнига') ||
        categoryLower.contains('радиоспектакль') ||
        categoryLower.contains('биография') ||
        categoryLower.contains('мемуары');
  }).toList();
}

/// Converts a Chapter object to a Map for caching.
Map<String, dynamic> _chapterToMap(Chapter chapter) => {
      'title': chapter.title,
      'durationMs': chapter.durationMs,
      'fileIndex': chapter.fileIndex,
      'startByte': chapter.startByte,
      'endByte': chapter.endByte,
    };
