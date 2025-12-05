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

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:jabook/core/cache/rutracker_cache_service.dart';
import 'package:jabook/core/di/providers/auth_providers.dart';
import 'package:jabook/core/di/providers/cache_providers.dart';
import 'package:jabook/core/di/providers/search_providers.dart';
import 'package:jabook/core/domain/auth/entities/auth_status.dart';
import 'package:jabook/core/infrastructure/endpoints/endpoint_provider.dart';
import 'package:jabook/core/infrastructure/logging/environment_logger.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:jabook/core/metadata/audiobook_metadata_service.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/parse/rutracker_parser.dart';
import 'package:jabook/core/search/search_history_service.dart';
import 'package:jabook/core/search/smart_search_cache_service.dart';
import 'package:jabook/core/utils/app_title_utils.dart';
import 'package:jabook/core/utils/safe_async.dart';
import 'package:jabook/features/search/presentation/screens/search_screen_favorites.dart';
import 'package:jabook/features/search/presentation/screens/search_screen_initialization_helpers.dart';
import 'package:jabook/features/search/presentation/screens/search_screen_login_handler.dart';
import 'package:jabook/features/search/presentation/screens/search_screen_pagination.dart';
import 'package:jabook/features/search/presentation/screens/search_screen_search_handlers.dart';
import 'package:jabook/features/search/presentation/screens/search_screen_ui_builders.dart';
import 'package:jabook/features/search/presentation/services/search_recommendations_service.dart';
import 'package:jabook/features/search/presentation/widgets/audiobook_card_skeleton.dart';
import 'package:jabook/features/search/presentation/widgets/recommended_audiobooks_widget.dart';
import 'package:jabook/features/search/presentation/widgets/search_error_state.dart';
import 'package:jabook/features/settings/presentation/screens/mirror_settings_screen.dart';
import 'package:jabook/l10n/app_localizations.dart';

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
  late final RuTrackerCacheService _cacheService;
  SmartSearchCacheService? _smartCacheService;
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
  // Track last search query to prevent duplicate searches
  String? _lastSearchQuery;

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
    // Get cache service from provider (already initialized in app.dart)
    _cacheService = ref.read(rutrackerCacheServiceProvider);
    _smartCacheService = ref.read(smartSearchCacheServiceProvider);
    _initializeCache();
    _initializeMetadataService();
    _loadActiveHost();
    _scrollController.addListener(() {
      SearchScreenPaginationHelper.onScroll(
        scrollController: _scrollController,
        hasMore: _hasMore,
        isLoadingMore: _isLoadingMore,
        loadMore: _loadMore,
      );
    });
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
          final service = SearchRecommendationsService(ref);
          final testResult = await service.getRecommendedAudiobooks();
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
                  final service = SearchRecommendationsService(ref);
                  _recommendedAudiobooksFuture =
                      service.getRecommendedAudiobooks();
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
    // Cache service is initialized in app.dart via provider
    // No additional initialization needed here
  }

  Future<void> _initializeMetadataService() async {
    final result =
        await SearchScreenInitializationHelper.initializeMetadataService(ref);
    _metadataService = result.metadataService;
    _historyService = result.historyService;
    await _loadSearchHistory();
  }

  Future<void> _loadSearchHistory() async {
    final history = await SearchScreenInitializationHelper.loadSearchHistory(
        _historyService);
    if (mounted) {
      setState(() {
        _searchHistory = history;
      });
    }
  }

  Future<void> _loadActiveHost() async {
    final host = await SearchScreenInitializationHelper.loadActiveHost(ref);
    if (mounted) {
      setState(() => _activeHost = host);
    }
  }

  /// Handles login with stored credentials or opens auth screen.
  Future<void> _handleLogin() async {
    final result = await SearchScreenLoginHandler.handleLogin(
      ref: ref,
      context: context,
      mounted: mounted,
      errorKind: _errorKind,
      setState: setState,
    );

    if (result.shouldClearAuthError) {
      setState(() {
        _errorKind = null;
        _errorMessage = null;
      });
    }

    if (result.shouldPerformSearch) {
      await _performSearch();
    }
  }

  Future<void> _performSearch() async {
    final query = _searchController.text.trim();

    if (query.isEmpty) return;

    // Prevent duplicate searches for the same query
    if (_isLoading && query == _lastSearchQuery) {
      return;
    }
    _lastSearchQuery = query;

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

    // Try quick search first (smart cache, local DB, cache)
    final quickResult = await SearchScreenSearchHandlers.performQuickSearch(
      query: query,
      smartCacheService: _smartCacheService,
      metadataService: _metadataService,
      cacheService: _cacheService,
    );

    if (quickResult != null && mounted) {
      setState(() {
        _searchResults = quickResult.results;
        _isFromCache = quickResult.isFromCache;
        _isFromLocalDb = quickResult.isFromLocalDb;
        if (quickResult.isFromCache) {
          _isLoading = false;
        } else {
          _isLoading = true; // Keep loading indicator for network update
        }
      });

      // If smart cache is valid, skip network search
      if (quickResult.shouldSkipNetworkSearch) {
        return;
      }
    }

    // Perform network search (this will update results when complete)
    await _performNetworkSearchWithService(query);
  }

  /// Loads more search results.
  Future<void> _loadMore() async {
    if (_isLoadingMore || !_hasMore) return;

    setState(() {
      _isLoadingMore = true;
    });

    try {
      final result = await SearchScreenPaginationHelper.loadMore(
        ref: ref,
        query: _searchController.text.trim(),
        currentResultsLength: _searchResults.length,
        parser: _parser,
      );

      if (mounted) {
        setState(() {
          _searchResults = [..._searchResults, ...result.newResults];
          _hasMore = result.hasMore;
          _isLoadingMore = false;
        });
      }
    } on Exception {
      if (mounted) {
        setState(() {
          _isLoadingMore = false;
        });
      }
    }
  }

  /// Forces a refresh of the current search by clearing cache and performing network search.
  ///
  /// This method clears the cache for the current query and performs a fresh network search,
  /// bypassing cache and local database.
  Future<void> _forceRefreshSearch() async {
    final query = _searchController.text.trim();
    if (query.isEmpty) return;

    // Clear cache for this query
    await SearchScreenSearchHandlers.clearCacheForQuery(
      query: query,
      cacheService: _cacheService,
    );

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
    await _performNetworkSearchWithService(query);
  }

  /// Performs network search using SearchNetworkService.
  Future<void> _performNetworkSearchWithService(String query,
      {bool updateExisting = false}) async {
    final structuredLogger = StructuredLogger();

    await structuredLogger.log(
      level: 'info',
      subsystem: 'search',
      message: 'Starting network search with service',
      context: 'search_request',
      extra: {
        'query': query,
        'update_existing': updateExisting,
        'start_offset': _startOffset,
      },
    );

    // Cancel any in-flight search
    _cancelToken?.cancel('superseded');
    _cancelToken = CancelToken();

    try {
      final result = await SearchScreenSearchHandlers.performNetworkSearch(
        ref: ref,
        query: query,
        startOffset: _startOffset,
        cancelToken: _cancelToken!,
        parser: _parser,
      );

      if (!mounted) return;

      // Handle errors
      if (result.errorKind != null) {
        setState(() {
          _isLoading = false;
          _lastSearchQuery = null; // Reset to allow re-searching same query
          _errorKind = result.errorKind;
          _errorMessage = result.errorMessage;
        });

        if (result.errorKind == 'auth') {
          safeUnawaited(_handleLogin());
        }
        return;
      }

      // Cache the results
      try {
        await _cacheService.cacheSearchResults(query, result.results).timeout(
          const Duration(seconds: 2),
          onTimeout: () {
            safeUnawaited(structuredLogger.log(
              level: 'warning',
              subsystem: 'search',
              message: 'Cache operation timed out, continuing anyway',
              context: 'search_request',
              extra: {'query': query},
            ));
          },
        );
      } on Exception catch (e) {
        await structuredLogger.log(
          level: 'warning',
          subsystem: 'search',
          message: 'Failed to cache search results, continuing anyway',
          context: 'search_request',
          cause: e.toString(),
          extra: {'query': query},
        );
      }

      // Update state
      if (updateExisting) {
        setState(() {
          _lastSearchQuery = null; // Reset to allow re-searching same query
          final existingIds =
              _searchResults.map((r) => r['id'] as String?).toSet();
          final newResults = result.results
              .where((r) => !existingIds.contains(r['id'] as String?))
              .toList();
          _searchResults = [..._searchResults, ...newResults];
          _errorKind = null;
          _errorMessage = null;
          _hasMore = result.results.isNotEmpty;
        });
      } else {
        setState(() {
          _isLoading = false;
          _lastSearchQuery = null; // Reset to allow re-searching same query
          _searchResults = result.results;
          _errorKind = null;
          _errorMessage = null;
          _hasMore = result.results.isNotEmpty;
          _showHistory = false;
          if (result.activeHost != null) {
            _activeHost = result.activeHost;
          }
        });

        // Save to search history
        if (_historyService != null && query.isNotEmpty) {
          await _historyService!.saveSearchQuery(query);
          await _loadSearchHistory();
        }

        // Update statistics for items that don't have them
        safeUnawaited(_updateMissingStatistics(result.results));
      }
    } on Exception catch (e) {
      await structuredLogger.log(
        level: 'error',
        subsystem: 'search',
        message: 'Unexpected error during search',
        context: 'search_request',
        cause: e.toString(),
      );

      if (mounted) {
        setState(() {
          _isLoading = false;
          _lastSearchQuery = null; // Reset to allow re-searching same query
          _errorKind = 'network';
          _errorMessage = e.toString();
        });
      }
    }
  }

  // Old _performNetworkSearch method removed - now using _performNetworkSearchWithService
  // which uses SearchNetworkService

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
            // Guest mode warning banner
            if (ref.watch(accessProvider).isGuest)
              Container(
                width: double.infinity,
                margin: const EdgeInsets.fromLTRB(16, 8, 16, 8),
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: Theme.of(context)
                      .colorScheme
                      .errorContainer
                      .withValues(alpha: 0.3),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(
                    color: Theme.of(context)
                        .colorScheme
                        .error
                        .withValues(alpha: 0.3),
                  ),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Icon(
                          Icons.info_outline,
                          color: Theme.of(context).colorScheme.onErrorContainer,
                          size: 20,
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Text(
                            AppLocalizations.of(context)
                                    ?.searchGuestModeWarning ??
                                'Search is limited in guest mode. Some features may not work. Sign in for full access.',
                            style:
                                Theme.of(context).textTheme.bodySmall?.copyWith(
                                      color: Theme.of(context)
                                          .colorScheme
                                          .onErrorContainer,
                                    ),
                            maxLines: 3,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    SizedBox(
                      width: double.infinity,
                      child: ElevatedButton.icon(
                        onPressed: () {
                          context.push('/auth');
                        },
                        icon: const Icon(Icons.login, size: 18),
                        label: Text(
                          AppLocalizations.of(context)?.signInToUnlock ??
                              'Sign in to unlock',
                        ),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Theme.of(context).colorScheme.error,
                          foregroundColor: Colors.white,
                          padding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 12,
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
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
                  // Debounce search with 500ms delay to reduce unnecessary searches
                  _debounce =
                      Timer(const Duration(milliseconds: 500), _performSearch);
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
              SearchScreenUIBuilders.buildSearchHistory(
                context: context,
                searchHistory: _searchHistory,
                onQuerySelected: (query) {
                  _searchController.text = query;
                  _performSearch();
                },
                onQueryRemoved: (query) async {
                  if (_historyService != null) {
                    await _historyService!.removeSearchQuery(query);
                    await _loadSearchHistory();
                  }
                },
                onClear: () async {
                  if (_historyService != null) {
                    await _historyService!.clearHistory();
                    await _loadSearchHistory();
                  }
                },
              ),
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
                        await _handleLogin();
                      },
                      child: Text(
                        AppLocalizations.of(context)?.login ?? 'Login',
                      ),
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
                        margin: const EdgeInsets.symmetric(
                            horizontal: 16.0, vertical: 8.0),
                        padding: const EdgeInsets.all(8.0),
                        decoration: BoxDecoration(
                          color: Colors.blue.shade50,
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: Row(
                          children: [
                            Icon(Icons.cached, color: Colors.blue.shade700),
                            const SizedBox(width: 8),
                            Expanded(
                              child: Text(
                                AppLocalizations.of(context)
                                        ?.resultsFromCache ??
                                    'Results from cache',
                                style: TextStyle(color: Colors.blue.shade800),
                              ),
                            ),
                            if (_cacheExpirationTime != null)
                              Text(
                                _formatCacheExpiration(_cacheExpirationTime!),
                                style: TextStyle(
                                  fontSize: 12,
                                  color: Colors.blue.shade600,
                                ),
                              ),
                          ],
                        ),
                      ),
                    if (_isFromLocalDb)
                      Container(
                        margin: const EdgeInsets.symmetric(
                            horizontal: 16.0, vertical: 8.0),
                        padding: const EdgeInsets.all(8.0),
                        decoration: BoxDecoration(
                          color: Colors.green.shade50,
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: Row(
                          children: [
                            Icon(Icons.storage, color: Colors.green.shade700),
                            const SizedBox(width: 8),
                            Text(
                              AppLocalizations.of(context)
                                      ?.resultsFromLocalDb ??
                                  'Results from local database',
                              style: TextStyle(color: Colors.green.shade800),
                            ),
                          ],
                        ),
                      ),
                    if (_errorKind != null)
                      SearchErrorState(
                        errorKind: _errorKind!,
                        errorMessage: _errorMessage,
                        onRetry: _performSearch,
                        onLogin: _handleLogin,
                      )
                    else
                      Expanded(
                        child: SearchScreenUIBuilders.buildBodyState(
                          context: context,
                          errorKind: _errorKind,
                          errorMessage: _errorMessage,
                          onRetry: _performSearch,
                          onLogin: _handleLogin,
                          searchResults:
                              SearchScreenUIBuilders.buildSearchResults(
                            context: context,
                            ref: ref,
                            searchResults: _searchResults,
                            selectedCategories: _selectedCategories,
                            searchText: _searchController.text,
                            hasMore: _hasMore,
                            isLoadingMore: _isLoadingMore,
                            loadMore:
                                _hasMore && !_isLoadingMore ? _loadMore : null,
                            onFavoriteToggle: (topicId, isFavorite) {
                              SearchScreenFavoritesHelper.toggleFavorite(
                                ref: ref,
                                topicId: topicId,
                                isFavorite: isFavorite,
                                searchResults: _searchResults,
                                mounted: mounted,
                                context: context,
                              );
                            },
                            onClearSearch: () {
                              _searchController.clear();
                              setState(() {
                                _searchResults = [];
                                _hasSearched = false;
                              });
                            },
                          ),
                        ),
                      ),
                  ],
                ),
              )
            else
              Expanded(
                child: SearchScreenUIBuilders.buildEmptyState(
                  context: context,
                  searchText: _searchController.text,
                  recommendedAudiobooksFuture: _recommendedAudiobooksFuture,
                  onRefresh: () {
                    final service = SearchRecommendationsService(ref);
                    _recommendedAudiobooksFuture =
                        service.getRecommendedAudiobooks();
                    setState(() {});
                  },
                ),
              ),
          ],
        ),
      ),
    );
  }

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

  // _audiobookToMap method moved to search_screen_utils.dart

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

// _filterAudiobookResults method moved to search_screen_filters.dart

// _chapterToMap method moved to search_screen_utils.dart
