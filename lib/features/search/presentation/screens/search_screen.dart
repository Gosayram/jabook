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
import 'dart:io' as io;

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:jabook/core/cache/rutracker_cache_service.dart';
import 'package:jabook/core/endpoints/endpoint_manager.dart';
import 'package:jabook/core/endpoints/endpoint_provider.dart';
import 'package:jabook/core/favorites/favorites_service.dart';
import 'package:jabook/core/logging/environment_logger.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/core/metadata/audiobook_metadata_service.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/parse/rutracker_parser.dart';
import 'package:jabook/core/search/search_history_service.dart';
import 'package:jabook/core/services/cookie_service.dart';
import 'package:jabook/core/utils/safe_async.dart';
import 'package:jabook/data/db/app_database.dart';
import 'package:jabook/features/auth/data/providers/auth_provider.dart';
import 'package:jabook/features/search/presentation/widgets/grouped_audiobook_list.dart';
import 'package:jabook/features/settings/presentation/screens/mirror_settings_screen.dart';
import 'package:jabook/features/webview/secure_rutracker_webview.dart';
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
  final RuTrackerCacheService _cacheService = RuTrackerCacheService();
  AudiobookMetadataService? _metadataService;
  SearchHistoryService? _historyService;
  FavoritesService? _favoritesService;

  List<Map<String, dynamic>> _searchResults = [];
  List<String> _searchHistory = [];
  final Set<String> _favoriteIds = <String>{};
  bool _isLoading = false;
  bool _hasSearched = false;
  bool _isFromCache = false;
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
    _initializeCache();
    _initializeMetadataService();
    _loadActiveHost();
    _scrollController.addListener(_onScroll);
    _searchFocusNode.addListener(_onSearchFocusChange);
    _searchController.addListener(_onSearchTextChange);
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
      final appDatabase = AppDatabase();
      await appDatabase.initialize();
      _metadataService = AudiobookMetadataService(appDatabase.database);
      _historyService = SearchHistoryService(appDatabase.database);
      _favoritesService = FavoritesService(appDatabase.database);
      await _loadSearchHistory();
      await _loadFavorites();
    } on Exception {
      // Metadata service optional - continue without it
      _metadataService = null;
      _historyService = null;
      _favoritesService = null;
    }
  }

  Future<void> _loadFavorites() async {
    if (_favoritesService == null) return;
    try {
      final favorites = await _favoritesService!.getAllFavorites();
      if (mounted) {
        setState(() {
          _favoriteIds
            ..clear()
            ..addAll(favorites.map((a) => a.id));
        });
      }
    } on Exception {
      // Ignore errors
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
        logger.w('Current active endpoint $base is unavailable, trying to get available one');
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

  /// Handles login with biometric authentication or WebView fallback.
  Future<void> _handleLogin() async {
    try {
      final repository = ref.read(authRepositoryProvider);

      // Check if stored credentials are available
      final hasStored = await repository.hasStoredCredentials();

      if (hasStored) {
        // Try to login with stored credentials first (without biometric check to avoid permission request)
        // This will only use biometric if credentials require it
        try {
          final success = await repository.loginWithStoredCredentials(
            useBiometric: false, // Don't use biometric to avoid permission request on button click
          );

          if (success) {
            // HTTP login already syncs cookies, just validate
            final isValid = await DioClient.validateCookies();

            if (isValid) {
              if (!mounted) return;
              // ignore: use_build_context_synchronously
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text(AppLocalizations.of(context)
                          ?.authorizationSuccessful ??
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
          // Fall through to WebView login
        }

        // If stored credentials login failed, open WebView directly
        // We don't check biometric availability here to avoid permission request
        // User can use biometric if they want when opening WebView
        await _openWebViewLogin();
      } else {
        // No stored credentials, open WebView directly
        await _openWebViewLogin();
      }
    } on Exception catch (e, stackTrace) {
      // Catch any unexpected errors and always open WebView as fallback
      logger.e('Error in _handleLogin: $e', stackTrace: stackTrace);
      if (!mounted) return;

      // Show error message but still open WebView
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(AppLocalizations.of(context)?.authorizationCheckError(e.toString()) ?? 
                  'Authorization check error: ${e.toString()}'),
          backgroundColor: Colors.orange,
          duration: const Duration(seconds: 3),
        ),
      );

      // Always try to open WebView as fallback
      await _openWebViewLogin();
    }
  }

  /// Opens WebView for manual login.
  Future<void> _openWebViewLogin() async {
    final operationId = 'webview_login_${DateTime.now().millisecondsSinceEpoch}';
    final structuredLogger = StructuredLogger();
    await structuredLogger.log(
      level: 'info',
      subsystem: 'auth',
      message: 'Opening WebView for login',
      operationId: operationId,
      context: 'webview_login',
    );
    
    try {
      if (!mounted) return;
      final navigator = Navigator.of(context);
      await navigator.push<String>(
        MaterialPageRoute(
          builder: (_) => const SecureRutrackerWebView(),
        ),
      );
      if (!mounted) return;

      await structuredLogger.log(
        level: 'info',
        subsystem: 'auth',
        message: 'WebView closed, starting cookie sync',
        operationId: operationId,
        context: 'webview_login',
      );

      // Wait a bit for cookies to be saved in CookieManager
      await Future.delayed(const Duration(milliseconds: 1500));
      await structuredLogger.log(
        level: 'debug',
        subsystem: 'auth',
        message: 'Waited 1500ms for cookies to be saved in CookieManager',
        operationId: operationId,
        context: 'webview_login',
      );

      // NEW APPROACH: Sync cookies from CookieManager (Kotlin) to Dio
      // Try multiple RuTracker domains with retries to ensure we get all cookies
      final rutrackerDomains = ['rutracker.me', 'rutracker.net', 'rutracker.org'];
      var cookiesSynced = false;
      const maxRetries = 5;
      const retryDelay = Duration(milliseconds: 500);
      
      for (final domain in rutrackerDomains) {
        try {
          final url = 'https://$domain';
          
          // Try multiple times with delays
          String? cookieHeader;
          for (var attempt = 0; attempt < maxRetries; attempt++) {
            if (attempt > 0) {
              await Future.delayed(retryDelay);
            }
            
            await structuredLogger.log(
              level: 'debug',
              subsystem: 'auth',
              message: 'Attempting to sync cookies from CookieManager for domain (attempt ${attempt + 1}/$maxRetries)',
              operationId: operationId,
              context: 'webview_login',
              extra: {'domain': domain, 'url': url, 'attempt': attempt + 1},
            );
            
            cookieHeader = await CookieService.getCookiesForUrl(url);
            if (cookieHeader != null && cookieHeader.isNotEmpty) {
              break; // Found cookies, exit retry loop
            }
          }
          
          if (cookieHeader != null && cookieHeader.isNotEmpty) {
            await DioClient.syncCookiesFromCookieService(cookieHeader, url);
            await DioClient.saveCookiesToSecureStorage(cookieHeader, url);
            cookiesSynced = true;
            
            await structuredLogger.log(
              level: 'info',
              subsystem: 'auth',
              message: 'Cookies synced from CookieManager for domain',
              operationId: operationId,
              context: 'webview_login',
              extra: {
                'domain': domain,
                'cookie_header_length': cookieHeader.length,
              },
            );
            break; // Success, no need to try other domains
          }
        } on Exception catch (e) {
          await structuredLogger.log(
            level: 'debug',
            subsystem: 'auth',
            message: 'Failed to sync cookies from CookieManager for domain',
            operationId: operationId,
            context: 'webview_login',
            cause: e.toString(),
            extra: {'domain': domain},
          );
        }
      }
      
      if (!cookiesSynced) {
        await structuredLogger.log(
          level: 'debug',
          subsystem: 'auth',
          message: 'No cookies found in CookieManager after WebView login (cookies may be saved but not yet accessible)',
          operationId: operationId,
          context: 'webview_login',
          extra: {
            'domains_tried': rutrackerDomains,
            'note': 'Cookies may be available later or saved via WebView CookieManager',
          },
        );
      }

      // Check cookies before validation
      final hasCookies = await DioClient.hasValidCookies();
      await structuredLogger.log(
        level: 'debug',
        subsystem: 'auth',
        message: 'Has cookies check result',
        operationId: operationId,
        context: 'webview_login',
        extra: {'has_cookies': hasCookies},
      );

      // Validate authentication
      await structuredLogger.log(
        level: 'debug',
        subsystem: 'auth',
        message: 'Starting cookie validation',
        operationId: operationId,
        context: 'webview_login',
      );
      final isValid = await DioClient.validateCookies();
      await structuredLogger.log(
        level: 'info',
        subsystem: 'auth',
        message: 'Cookie validation result',
        operationId: operationId,
        context: 'webview_login',
        extra: {'is_valid': isValid, 'has_cookies': hasCookies},
      );
      
      if (isValid) {
        if (!mounted) return;
        // ignore: use_build_context_synchronously
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(AppLocalizations.of(context)
                    ?.authorizationSuccessful ??
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
      } else {
        await structuredLogger.log(
          level: 'debug',
          subsystem: 'auth',
          message: 'Cookie validation failed after WebView login (cookies may need time to sync, this is expected)',
          operationId: operationId,
          context: 'webview_login',
          extra: {
            'has_cookies': hasCookies,
            'note': 'Cookies may be available but validation failed due to timing or endpoint issues',
          },
        );
        
        // Try to get more details about why validation failed
        try {
          final db = AppDatabase().database;
          final endpointManager = EndpointManager(db);
          final activeBase = await endpointManager.getActiveEndpoint();
          await DioClient.instance; // Ensure Dio is initialized
          final uri = Uri.parse(activeBase);
          
          // CRITICAL: Check cookies in Dio jar with detailed logging
          await structuredLogger.log(
            level: 'debug',
            subsystem: 'auth',
            message: 'Checking Dio CookieJar for cookies',
            operationId: operationId,
            context: 'webview_login',
            extra: {
              'endpoint': activeBase,
              'uri_host': uri.host,
              'uri_scheme': uri.scheme,
              'uri_path': uri.path,
            },
          );
          
          final cookieJar = await DioClient.getCookieJar();
          if (cookieJar != null) {
            // Try loading cookies for the exact URI
            final dioCookies = await cookieJar.loadForRequest(uri);
            
            // Also try loading for base domain
            final baseUri = Uri.parse('https://${uri.host}');
            final baseCookies = await cookieJar.loadForRequest(baseUri);
            
            await structuredLogger.log(
              level: 'debug',
              subsystem: 'auth',
              message: 'Cookies in Dio jar after failed validation (debug info)',
              operationId: operationId,
              context: 'webview_login',
              extra: {
                'endpoint': activeBase,
                'uri_host': uri.host,
                'uri_full': uri.toString(),
                'base_uri': baseUri.toString(),
                'cookie_count_for_uri': dioCookies.length,
                'cookie_count_for_base': baseCookies.length,
                'cookies_for_uri': dioCookies.map((c) => {
                  'name': c.name,
                  'domain': c.domain ?? '<null>',
                  'path': c.path ?? '/',
                  'has_value': c.value.isNotEmpty,
                  'value_length': c.value.length,
                  'value_preview': c.value.isNotEmpty 
                      ? '${c.value.substring(0, c.value.length > 20 ? 20 : c.value.length)}...' 
                      : '<empty>',
                  'secure': c.secure,
                  'http_only': c.httpOnly,
                  'is_session_cookie': c.name.toLowerCase().contains('session') ||
                      c.name == 'bb_session' ||
                      c.name == 'bb_data',
                }).toList(),
                'cookies_for_base': baseCookies.map((c) => {
                  'name': c.name,
                  'domain': c.domain ?? '<null>',
                  'path': c.path ?? '/',
                  'has_value': c.value.isNotEmpty,
                  'value_length': c.value.length,
                  'value_preview': c.value.isNotEmpty 
                      ? '${c.value.substring(0, c.value.length > 20 ? 20 : c.value.length)}...' 
                      : '<empty>',
                  'secure': c.secure,
                  'http_only': c.httpOnly,
                  'is_session_cookie': c.name.toLowerCase().contains('session') ||
                      c.name == 'bb_session' ||
                      c.name == 'bb_data',
                }).toList(),
              },
            );
            
            // CRITICAL: If no cookies found, log error
            if (dioCookies.isEmpty && baseCookies.isEmpty) {
              await structuredLogger.log(
                level: 'error',
                subsystem: 'auth',
                message: 'CRITICAL: No cookies found in Dio CookieJar after WebView login!',
                operationId: operationId,
                context: 'webview_login',
                extra: {
                  'endpoint': activeBase,
                  'uri_host': uri.host,
                  'note': 'Cookies were not synced from WebView to Dio CookieJar',
                },
              );
            }
          } else {
            await structuredLogger.log(
              level: 'warning',
              subsystem: 'auth',
              message: 'CookieJar is null after failed validation',
              operationId: operationId,
              context: 'webview_login',
            );
          }
        } on Exception catch (e) {
          await structuredLogger.log(
            level: 'warning',
            subsystem: 'auth',
            message: 'Failed to check cookies in Dio jar',
            operationId: operationId,
            context: 'webview_login',
            cause: e.toString(),
          );
        }
        
        if (!mounted) return;
        // ignore: use_build_context_synchronously
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(AppLocalizations.of(context)
                    ?.authorizationFailedMessage ??
                'Authorization failed. Please check your login and password'),
            backgroundColor: Colors.orange,
            duration: const Duration(seconds: 3),
          ),
        );
      }
    } on Exception catch (e, stackTrace) {
      logger.e('Error opening WebView login: $e', stackTrace: stackTrace);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(AppLocalizations.of(context)?.authorizationPageError(e.toString()) ?? 
                  'Authorization page error: ${e.toString()}'),
          backgroundColor: Colors.red,
          duration: const Duration(seconds: 3),
        ),
      );
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
          setState(() {
            _searchResults = localResults.map(_audiobookToMap).toList();
            _isLoading = false;
            _isFromLocalDb = true;
          });
          // Save to search history
          if (_historyService != null && query.isNotEmpty) {
            await _historyService!.saveSearchQuery(query);
            await _loadSearchHistory();
          }

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
      setState(() {
        _searchResults = cachedResults;
        _isLoading = false;
        _isFromCache = true;
      });
      return;
    }

    // Finally, try network search
    await _performNetworkSearch(query);
  }

  /// Performs network search (can be called separately for background updates).
  ///
  /// [updateExisting] - if true, updates existing results instead of replacing them.
  Future<void> _performNetworkSearch(String query,
      {bool updateExisting = false}) async {
    final structuredLogger = StructuredLogger();
    
    await structuredLogger.log(
      level: 'info',
      subsystem: 'search',
      message: 'Starting network search',
      context: 'search_request',
      extra: {
        'query': query,
        'update_existing': updateExisting,
      },
    );
    
    // Ensure cookies are synced before network search
    try {
      await DioClient.syncCookiesFromWebView();
      await structuredLogger.log(
        level: 'debug',
        subsystem: 'search',
        message: 'Cookies synced from WebView',
        context: 'search_request',
      );
    } on Exception catch (e) {
      await structuredLogger.log(
        level: 'warning',
        subsystem: 'search',
        message: 'Failed to sync cookies from WebView, continuing anyway',
        context: 'search_request',
        cause: e.toString(),
      );
    }

    // Check if we have cookies before performing search
    // First check if cookies exist (fast check), then validate if needed
    try {
      final hasCookies = await DioClient.hasValidCookies();
      await structuredLogger.log(
        level: 'debug',
        subsystem: 'search',
        message: 'Cookie validation check',
        context: 'search_request',
        extra: {'has_cookies': hasCookies},
      );
      
      if (!hasCookies) {
        // No cookies at all - definitely need login
        await structuredLogger.log(
          level: 'warning',
          subsystem: 'search',
          message: 'No valid cookies found, requiring login',
          context: 'search_request',
        );
        
        if (mounted) {
          setState(() {
            _isLoading = false;
            _errorKind = 'auth';
            _errorMessage = AppLocalizations.of(context)
                    ?.authorizationFailedMessage ??
                'Please log in first to perform search';
          });
          // Show login prompt
          safeUnawaited(_openWebViewLogin());
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
    final endpointManager = ref.read(endpointManagerProvider);
    final activeEndpoint = await endpointManager.getActiveEndpoint();
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
      final searchUri = Uri.parse('$endpoint/forum/search.php');
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
            c.name == 'bb_data'
          ),
        },
      );
      
      // If no cookies found for active endpoint, try to sync from WebView/CookieService
      if (cookiesForRequest.isEmpty && cookieJar != null) {
        await structuredLogger.log(
          level: 'warning',
          subsystem: 'search',
          message: 'No cookies found for active endpoint, syncing from WebView/CookieService',
          context: 'search_request',
          extra: {'endpoint': endpoint},
        );
        
        // Try CookieService first (Android CookieManager) - most reliable
        try {
          final cookieHeader = await CookieService.getCookiesForUrl(endpoint);
          if (cookieHeader != null && cookieHeader.isNotEmpty) {
            await structuredLogger.log(
              level: 'info',
              subsystem: 'search',
              message: 'Found cookies in CookieService, syncing to Dio',
              context: 'search_request',
              extra: {
                'endpoint': endpoint,
                'cookie_header_length': cookieHeader.length,
              },
            );
            
            await DioClient.syncCookiesFromCookieService(cookieHeader, endpoint);
            
            // Wait a bit and reload cookies
            await Future.delayed(const Duration(milliseconds: 200));
            final cookiesAfterSync = await cookieJar.loadForRequest(searchUri);
            
            await structuredLogger.log(
              level: cookiesAfterSync.isNotEmpty ? 'info' : 'warning',
              subsystem: 'search',
              message: 'Cookies after sync from CookieService',
              context: 'search_request',
              extra: {
                'endpoint': endpoint,
                'cookie_count_after_sync': cookiesAfterSync.length,
                'cookie_names_after_sync': cookiesAfterSync.map((c) => c.name).toList(),
              },
            );
          } else {
            // Fallback to WebView sync
            await DioClient.syncCookiesFromWebView();
            await Future.delayed(const Duration(milliseconds: 200));
            final cookiesAfterSync = await cookieJar.loadForRequest(searchUri);
            
            await structuredLogger.log(
              level: cookiesAfterSync.isNotEmpty ? 'info' : 'warning',
              subsystem: 'search',
              message: 'Cookies after sync from WebView',
              context: 'search_request',
              extra: {
                'endpoint': endpoint,
                'cookie_count_after_sync': cookiesAfterSync.length,
                'cookie_names_after_sync': cookiesAfterSync.map((c) => c.name).toList(),
              },
            );
          }
        } on Exception catch (e) {
          await structuredLogger.log(
            level: 'warning',
            subsystem: 'search',
            message: 'Failed to sync cookies, trying WebView fallback',
            context: 'search_request',
            cause: e.toString(),
            extra: {'endpoint': endpoint},
          );
          
          // Fallback to WebView sync
          await DioClient.syncCookiesFromWebView();
        }
      }
      
      // Make the search request
      final requestStartTime = DateTime.now();
      final requestUri = '$endpoint/forum/search.php';
      final requestQueryParams = {
        'nm': query,
        'o': '1', // Sort by relevance
        'start': _startOffset,
      };
      
      // Get cookies that will actually be sent (after sync)
      final finalCookies = cookieJar != null 
          ? await cookieJar.loadForRequest(searchUri)
          : <io.Cookie>[];
      
      await structuredLogger.log(
        level: 'info',
        subsystem: 'search',
        message: 'Making search request',
        context: 'search_request',
        extra: {
          'endpoint': endpoint,
          'request_uri': requestUri,
          'query_params': requestQueryParams,
          'cookies_count': finalCookies.length,
          'cookies_names': finalCookies.map((c) => c.name).toList(),
          'will_send_cookies': finalCookies.isNotEmpty,
        },
      );
      
      final response = await dio
          .get(
            requestUri,
            queryParameters: requestQueryParams,
            cancelToken: _cancelToken,
          )
          .timeout(const Duration(seconds: 30));
      
      final requestDuration = DateTime.now().difference(requestStartTime).inMilliseconds;
      
      await structuredLogger.log(
        level: response.statusCode == 200 ? 'info' : 'warning',
        subsystem: 'search',
        message: 'Search request completed',
        context: 'search_request',
        durationMs: requestDuration,
        extra: {
          'endpoint': endpoint,
          'status_code': response.statusCode,
          'response_size': response.data?.toString().length ?? 0,
          'cookies_were_sent': finalCookies.isNotEmpty,
          'cookies_sent_count': finalCookies.length,
          'response_headers': {
            'content-type': response.headers.value('content-type'),
            'set-cookie': response.headers.value('set-cookie') != null ? 'present' : 'absent',
          },
        },
      );

      if (response.statusCode == 200) {
        final results = await _parser.parseSearchResults(response.data);

        // Cache the results
        // Convert Audiobook objects to maps for caching
        final resultsMap = results.map(_audiobookToMap).toList();
        await _cacheService.cacheSearchResults(query, resultsMap);

        if (updateExisting && mounted) {
          // Update existing results (for background refresh from local DB)
          setState(() {
            final networkResults = results.map(_audiobookToMap).toList();
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
            // Convert Audiobook objects to maps for UI
            _searchResults = results.map(_audiobookToMap).toList();
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
        }

        // Success - update active host display (don't change endpoint)
        if (mounted) {
          final uri = Uri.tryParse(endpoint);
          if (uri != null && uri.hasScheme && uri.hasAuthority && uri.host.isNotEmpty) {
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
          _errorMessage = AppLocalizations.of(context)
                  ?.requestTimedOutMessage ??
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
      await structuredLogger.log(
        level: 'error',
        subsystem: 'search',
        message: 'Search request failed',
        context: 'search_request',
        cause: e.toString(),
        extra: {
          'endpoint': endpoint,
          'error_type': e.type.toString(),
          'error_message': e.message,
          'status_code': e.response?.statusCode,
          'response_data_preview': e.response?.data?.toString().length != null 
              ? (e.response!.data!.toString().length > 200 
                  ? e.response!.data!.toString().substring(0, 200) 
                  : e.response!.data!.toString())
              : 'null',
          'is_auth_error': e.response?.statusCode == 401 || e.response?.statusCode == 403,
          'request_path': e.requestOptions.path,
          'request_method': e.requestOptions.method,
          'request_headers': e.requestOptions.headers,
        },
      );

      // Handle auth errors
      if (e.response?.statusCode == 401 || e.response?.statusCode == 403 ||
          (e.message?.contains('Authentication required') ?? false)) {
        if (mounted) {
          setState(() {
            _isLoading = false;
            _errorKind = 'auth';
            _errorMessage = AppLocalizations.of(context)
                    ?.authorizationFailedMessage ??
                'Authentication required. Please log in.';
          });
          safeUnawaited(_openWebViewLogin());
        }
        return;
      }

      // Handle connection errors
      if (e.type == DioExceptionType.connectionError ||
          e.type == DioExceptionType.connectionTimeout) {
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
                ? (AppLocalizations.of(context)?.networkConnectionError ??
                    'Could not connect. Check your internet or choose another mirror in Settings → Sources.')
                : (AppLocalizations.of(context)?.connectionFailed ??
                    'Connection failed. Please check your internet connection.');
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
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
          title: Text(AppLocalizations.of(context)?.searchAudiobooks ?? 'Search Audiobooks'),
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
                    child: Text(_activeHost!,
                        style: const TextStyle(fontSize: 12)),
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
                  MaterialPageRoute(
                      builder: (_) => const MirrorSettingsScreen()),
                ).then((_) => _loadActiveHost()));
              },
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
                    labelText: AppLocalizations.of(context)?.searchPlaceholder ?? 'Enter title, author, or keywords',
                    hintText: AppLocalizations.of(context)?.searchPlaceholder ?? 'Enter title, author, or keywords',
                    suffixIcon: _searchController.text.isNotEmpty
                        ? IconButton(
                            icon: const Icon(Icons.clear),
                            onPressed: () {
                              _searchController.clear();
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
                    _debounce = Timer(
                        const Duration(milliseconds: 500), _performSearch);
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
                          AppLocalizations.of(context)
                                  ?.loginRequiredForSearch ??
                              'Login required to search RuTracker',
                          style: TextStyle(color: Colors.orange.shade800),
                        ),
                      ),
                      TextButton(
                        onPressed: () async {
                          await Navigator.push<String>(
                            context,
                            MaterialPageRoute(
                                builder: (_) => const SecureRutrackerWebView()),
                          );
                          if (!mounted) return;
                          
                          // Wait a bit for cookies to be saved in WebView
                          await Future.delayed(const Duration(milliseconds: 500));
                          
                          // Sync cookies from WebView (multiple attempts to ensure sync)
                          for (var i = 0; i < 3; i++) {
                          await DioClient.syncCookiesFromWebView();
                            if (i < 2) {
                              await Future.delayed(const Duration(milliseconds: 200));
                            }
                          }
                          
                          setState(() {
                            _errorKind = null;
                            _errorMessage = null;
                          });
                          await _performSearch();
                        },
                        child: Text(
                            AppLocalizations.of(context)?.login ?? 'Login'),
                      ),
                    ],
                  ),
                ),
              if (_isLoading)
                Expanded(
                  child: Semantics(
                    label: AppLocalizations.of(context)?.loading ?? 'Loading',
                    child: const Center(
                      child: CircularProgressIndicator(),
                    ),
                  ),
                )
              else if (_hasSearched)
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
                              Text(
                                AppLocalizations.of(context)?.resultsFromCache ?? 'Results from cache',
                                style: TextStyle(
                                  color: Colors.blue[700],
                                  fontSize: 12,
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
                            await _performSearch();
                          },
                          child: _buildBodyState(),
                        ),
                      ),
                    ],
                  ),
                )
              else
                Expanded(
                  child: Semantics(
                    label: AppLocalizations.of(context)?.enterSearchTerm ??
                        'Enter a search term to begin',
                    child: Center(
                      child: Text(
                        AppLocalizations.of(context)?.enterSearchTerm ??
                            'Enter a search term to begin',
                        textAlign: TextAlign.center,
                      ),
                    ),
                  ),
                ),
            ],
          ),
        ),
      );

  Widget _buildBodyState() {
    if (_errorKind != null) {
      return _buildErrorState();
    }
    return _buildSearchResults();
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
                style: Theme.of(context).textTheme.labelMedium,
              ),
              if (_selectedCategories.isNotEmpty) ...[
                const SizedBox(width: 8),
                TextButton(
                  onPressed: () {
                    _selectedCategories.clear();
                    setState(() {});
                  },
                  child: Text(AppLocalizations.of(context)?.resetButton ?? 'Reset'),
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
                label: Text(category),
                selected: isSelected,
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
                      child: Text(AppLocalizations.of(context)?.clearButton ??
                          'Clear'),
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
      return Center(
        child: Semantics(
          label: localizations?.noResults ?? 'No results found',
          child: Text(
            localizations?.noResults ?? 'No results found',
            style: Theme.of(context).textTheme.bodyLarge,
            textAlign: TextAlign.center,
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
      favoriteIds: _favoriteIds,
      onFavoriteToggle: _toggleFavorite,
    );
  }

  Future<void> _toggleFavorite(String topicId, bool isFavorite) async {
    if (_favoritesService == null) return;

    try {
      if (isFavorite) {
        // Add to favorites
        final audiobookMap = _searchResults.firstWhere(
          (r) => (r['id'] as String?) == topicId,
        );
        await _favoritesService!.addToFavoritesFromMap(audiobookMap);
      } else {
        // Remove from favorites
        await _favoritesService!.removeFromFavorites(topicId);
      }

      if (mounted) {
        setState(() {
          if (isFavorite) {
            _favoriteIds.add(topicId);
          } else {
            _favoriteIds.remove(topicId);
          }
        });
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
                  : (AppLocalizations.of(context)?.failedToRemoveFromFavorites ??
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
        '$activeEndpoint/forum/search.php',
        queryParameters: {
          'nm': query,
          'o': '1',
          'start': _searchResults.length,
        },
      );
      if (res.statusCode == 200) {
        final more = await _parser.parseSearchResults(res.data);
        if (mounted) {
          setState(() {
            _searchResults.addAll(more.map(_audiobookToMap));
            _hasMore = more.isNotEmpty;
          });
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
        message = localizations?.networkConnectionError ??
            'Could not connect. Check your internet or choose another mirror in Settings → Sources.';
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
}

/// Converts an Audiobook object to a Map for caching.
Map<String, dynamic> _audiobookToMap(Audiobook audiobook) => {
      'id': audiobook.id,
      'title': audiobook.title,
      'author': audiobook.author,
      'category': audiobook.category,
      'size': audiobook.size,
      'seeders': audiobook.seeders,
      'leechers': audiobook.leechers,
      'magnetUrl': audiobook.magnetUrl,
      'coverUrl': audiobook.coverUrl,
      'chapters': audiobook.chapters.map(_chapterToMap).toList(),
      'addedDate': audiobook.addedDate.toIso8601String(),
    };

/// Converts a Chapter object to a Map for caching.
Map<String, dynamic> _chapterToMap(Chapter chapter) => {
      'title': chapter.title,
      'durationMs': chapter.durationMs,
      'fileIndex': chapter.fileIndex,
      'startByte': chapter.startByte,
      'endByte': chapter.endByte,
    };

// Unused legacy dialog removed; login flow handled inline via WebView
