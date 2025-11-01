import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:jabook/core/cache/rutracker_cache_service.dart';
import 'package:jabook/core/endpoints/endpoint_provider.dart';
import 'package:jabook/core/favorites/favorites_service.dart';
import 'package:jabook/core/metadata/audiobook_metadata_service.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/parse/rutracker_parser.dart';
import 'package:jabook/core/search/search_history_service.dart';
import 'package:jabook/data/db/app_database.dart';
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
      final host = Uri.parse(base).host;
      if (mounted) {
        setState(() => _activeHost = host);
      }
    } on Object {
      // ignore
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
          unawaited(_performNetworkSearch(query, updateExisting: true));
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
    // Ensure cookies are synced and validated before network search
    await DioClient.syncCookiesFromWebView();

    // Fetch from network using EndpointManager
    try {
      final endpointManager = ref.read(endpointManagerProvider);
      final activeEndpoint = await endpointManager.getActiveEndpoint();
      final dio = await DioClient.instance;
      final response = await dio
          .get(
            '$activeEndpoint/forum/search.php',
            queryParameters: {
              'nm': query,
              'o': '1', // Sort by relevance
              'start': _startOffset,
            },
            cancelToken: _cancelToken,
          )
          .timeout(const Duration(seconds: 30));

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
      } else {
        setState(() {
          _isLoading = false;
          _errorKind = 'network';
          _errorMessage = 'HTTP ${response.statusCode}';
        });
      }
    } on TimeoutException {
      if (mounted) {
        setState(() {
          _isLoading = false;
          _errorKind = 'timeout';
          _errorMessage = 'Request timed out';
        });
      }
    } on DioException catch (e) {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });

        if (CancelToken.isCancel(e)) {
          // Silently ignore cancelled request
          return;
        }
        // Handle authentication errors specifically
        if (e.message?.contains('Authentication required') ?? false) {
          setState(() {
            _errorKind = 'auth';
            _errorMessage = 'Authentication required';
          });
        } else if (e.type == DioExceptionType.connectionTimeout ||
            e.type == DioExceptionType.receiveTimeout ||
            e.type == DioExceptionType.sendTimeout) {
          setState(() {
            _errorKind = 'timeout';
            _errorMessage = 'Request timed out';
          });
        } else if (e.type == DioExceptionType.connectionError) {
          setState(() {
            _errorKind = 'mirror';
            _errorMessage = 'Cannot connect to mirror';
          });
        } else {
          setState(() {
            _errorKind = 'network';
            _errorMessage = e.message ?? 'Unknown error';
          });
        }
      }
    } on Exception catch (e) {
      if (mounted) {
        setState(() {
          _isLoading = false;
          _errorKind = 'network';
          _errorMessage = e.toString();
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
          title: Text(AppLocalizations.of(context)!.searchAudiobooks),
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
              tooltip: 'RuTracker Login',
              icon: const Icon(Icons.vpn_key),
              onPressed: () async {
                await Navigator.push<String>(
                  context,
                  MaterialPageRoute(
                      builder: (_) => const SecureRutrackerWebView()),
                );
                if (!mounted) return;

                // Sync cookies from WebView
                await DioClient.syncCookiesFromWebView();

                // Validate authentication
                final isValid = await DioClient.validateCookies();
                if (isValid) {
                  if (!mounted) return;
                  // ignore: use_build_context_synchronously
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                      content: Text('Авторизация успешна'),
                      backgroundColor: Colors.green,
                      duration: Duration(seconds: 2),
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
                  if (!mounted) return;
                  // ignore: use_build_context_synchronously
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                      content: Text(
                          'Авторизация не удалась. Проверьте логин и пароль'),
                      backgroundColor: Colors.orange,
                      duration: Duration(seconds: 3),
                    ),
                  );
                }
              },
            ),
            IconButton(
              tooltip: 'Mirrors',
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
                    labelText: AppLocalizations.of(context)!.searchPlaceholder,
                    hintText: AppLocalizations.of(context)!.searchPlaceholder,
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
                          await DioClient.syncCookiesFromWebView();
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
                const Expanded(
                  child: Center(
                    child: CircularProgressIndicator(),
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
                                AppLocalizations.of(context)!.resultsFromCache,
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
                                  'Результаты из локальной базы данных',
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
                  child: Center(
                    child: Text(AppLocalizations.of(context)!.enterSearchTerm),
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
                'Фильтры:',
                style: Theme.of(context).textTheme.labelMedium,
              ),
              if (_selectedCategories.isNotEmpty) ...[
                const SizedBox(width: 8),
                TextButton(
                  onPressed: () {
                    _selectedCategories.clear();
                    setState(() {});
                  },
                  child: const Text('Сбросить'),
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
      final category = result['category'] as String? ?? 'Другое';
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
                    'История поиска',
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
                      child: const Text('Очистить'),
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

    if (filteredResults.isEmpty && _searchResults.isNotEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(
              Icons.filter_alt_off,
              size: 48,
              color: Colors.grey,
            ),
            const SizedBox(height: 16),
            Text(
              'Нет результатов для выбранных фильтров',
              style: Theme.of(context).textTheme.bodyLarge,
            ),
          ],
        ),
      );
    }

    if (filteredResults.isEmpty) {
      return Center(
        child: Text(AppLocalizations.of(context)!.noResults),
      );
    }

    return GroupedAudiobookList(
      audiobooks: filteredResults,
      onAudiobookTap: (id) {
        Navigator.pushNamed(context, '/topic/$id');
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
                  ? 'Не удалось добавить в избранное'
                  : 'Не удалось удалить из избранного',
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

    switch (_errorKind) {
      case 'auth':
        title = 'Authentication Required';
        iconData = Icons.lock_outline;
        iconColor = Colors.orange.shade600;
        actions = [
          ElevatedButton.icon(
            onPressed: () async {
              await Navigator.push<String>(
                context,
                MaterialPageRoute(
                    builder: (_) => const SecureRutrackerWebView()),
              );
              if (!mounted) return;
              await DioClient.syncCookiesFromWebView();
              setState(() {
                _errorKind = null;
                _errorMessage = null;
              });
              await _performSearch();
            },
            icon: const Icon(Icons.vpn_key),
            label: const Text('Login to RuTracker'),
          ),
        ];
        message =
            'Для поиска аудиокниг требуется авторизация на RuTracker. Нажмите кнопку "Авторизоваться" чтобы открыть безопасную форму входа.';
        break;
      case 'timeout':
        title = 'Request Timed Out';
        iconData = Icons.timer_off;
        iconColor = Colors.amber.shade600;
        actions = [
          ElevatedButton.icon(
            onPressed: _performSearch,
            icon: const Icon(Icons.refresh),
            label: const Text('Retry Search'),
          ),
          OutlinedButton.icon(
            onPressed: () => unawaited(Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const MirrorSettingsScreen()),
            ).then((_) => _loadActiveHost())),
            icon: const Icon(Icons.dns),
            label: const Text('Change Mirror'),
          ),
        ];
        message =
            'Запрос занял слишком много времени. Возможно, зеркало перегружено или у вас медленное подключение. Попробуйте выбрать другое зеркало или повторить попытку позже.';
        break;
      case 'mirror':
        title = 'Mirror Unavailable';
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
            label: const Text('Auto-Fix Mirrors'),
          ),
          OutlinedButton.icon(
            onPressed: () => unawaited(Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const MirrorSettingsScreen()),
            ).then((_) => _loadActiveHost())),
            icon: const Icon(Icons.settings),
            label: const Text('Manage Mirrors'),
          ),
        ];
        message =
            'The current RuTracker mirror is not responding. You can try to automatically find a working mirror or manually manage them.';
        break;
      case 'network':
      default:
        title = 'Network Error';
        iconData = Icons.wifi_off;
        iconColor = Colors.red.shade400;
        actions = [
          ElevatedButton.icon(
            onPressed: _performSearch,
            icon: const Icon(Icons.refresh),
            label: const Text('Retry'),
          ),
          OutlinedButton.icon(
            onPressed: () => unawaited(Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const MirrorSettingsScreen()),
            ).then((_) => _loadActiveHost())),
            icon: const Icon(Icons.dns),
            label: const Text('Change Mirror'),
          ),
        ];
        message =
            'Unable to connect to RuTracker. Please check your internet connection and try again.';
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
