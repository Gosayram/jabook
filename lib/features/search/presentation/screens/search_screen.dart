import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:jabook/core/cache/rutracker_cache_service.dart';
import 'package:jabook/core/endpoints/endpoint_provider.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/parse/rutracker_parser.dart';
import 'package:jabook/features/settings/presentation/screens/mirror_settings_screen.dart';
import 'package:jabook/features/webview/rutracker_login_screen.dart';
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
  
  List<Map<String, dynamic>> _searchResults = [];
  bool _isLoading = false;
  bool _hasSearched = false;
  bool _isFromCache = false;
  String? _errorKind; // 'network' | 'auth' | 'mirror' | 'timeout' | null
  String? _errorMessage;
  String? _activeHost;
  CancelToken? _cancelToken;
  Timer? _debounce;
  int _startOffset = 0;
  bool _isLoadingMore = false;
  bool _hasMore = true;
  final ScrollController _scrollController = ScrollController();

  @override
  void dispose() {
    _searchController.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  @override
  void initState() {
    super.initState();
    _initializeCache();
    _loadActiveHost();
    _scrollController.addListener(_onScroll);
  }

  Future<void> _initializeCache() async {
    // Cache service initialization would typically happen at app startup
    // For now, we'll handle it here
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
      _errorKind = null;
      _errorMessage = null;
      _startOffset = 0;
      _hasMore = true;
      _searchResults = [];
    });

    final query = _searchController.text.trim();

    // First try to get from cache
    final cachedResults = await _cacheService.getCachedSearchResults(query);
    if (cachedResults != null) {
      setState(() {
        _searchResults = cachedResults;
        _isLoading = false;
        _isFromCache = true;
      });
      return;
    }

    // If not in cache, fetch from network using EndpointManager
    try {
      final endpointManager = ref.read(endpointManagerProvider);
      final activeEndpoint = await endpointManager.getActiveEndpoint();
      final dio = await DioClient.instance;
      final response = await dio.get(
        '$activeEndpoint/forum/search.php',
        queryParameters: {
          'nm': query,
          'o': '1', // Sort by relevance
          'start': _startOffset,
        },
        cancelToken: _cancelToken,
      ).timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        final results = await _parser.parseSearchResults(response.data);
        
        // Cache the results
        // Convert Audiobook objects to maps for caching
        final resultsMap = results.map(_audiobookToMap).toList();
        await _cacheService.cacheSearchResults(query, resultsMap);
        
        setState(() {
          // Convert Audiobook objects to maps for UI
          _searchResults = results.map(_audiobookToMap).toList();
          _isLoading = false;
          _isFromCache = false;
          _errorKind = null;
          _errorMessage = null;
          _hasMore = results.isNotEmpty;
        });
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
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: Colors.grey.shade200,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Text(_activeHost!, style: const TextStyle(fontSize: 12)),
              ),
            ),
          ),
        IconButton(
          tooltip: 'RuTracker Login',
          icon: const Icon(Icons.vpn_key),
          onPressed: () async {
            await Navigator.push<String>(
              context,
              MaterialPageRoute(builder: (_) => const RutrackerLoginScreen()),
            );
            if (!mounted) return;
            await DioClient.syncCookiesFromWebView();
            // If была auth-ошибка — попробуем повторить
            if (_errorKind == 'auth') {
              setState(() {
                _errorKind = null;
                _errorMessage = null;
              });
              await _performSearch();
            }
          },
        ),
        IconButton(
          tooltip: 'Mirrors',
          icon: const Icon(Icons.dns),
          onPressed: () {
            unawaited(Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const MirrorSettingsScreen()),
            ).then((_) => _loadActiveHost()));
          },
        ),
        IconButton(
          icon: const Icon(Icons.search),
          onPressed: _performSearch,
        ),
      ],
    ),
    body: Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(16.0),
          child: TextField(
            controller: _searchController,
            decoration: InputDecoration(
              labelText: AppLocalizations.of(context)!.searchPlaceholder,
              hintText: AppLocalizations.of(context)!.searchPlaceholder,
              suffixIcon: IconButton(
                icon: const Icon(Icons.clear),
                onPressed: () {
                  _searchController.clear();
                  setState(() {
                    _searchResults = [];
                    _hasSearched = false;
                  });
                },
              ),
              border: const OutlineInputBorder(),
            ),
            onChanged: (value) {
              _debounce?.cancel();
              _debounce = Timer(const Duration(milliseconds: 500), _performSearch);
            },
            onSubmitted: (_) => _performSearch(),
          ),
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
                    AppLocalizations.of(context)?.loginRequiredForSearch ?? 'Login required to search RuTracker',
                    style: TextStyle(color: Colors.orange.shade800),
                  ),
                ),
                TextButton(
                  onPressed: () async {
                    await Navigator.push<String>(
                      context,
                      MaterialPageRoute(builder: (_) => const RutrackerLoginScreen()),
                    );
                    if (!mounted) return;
                    await DioClient.syncCookiesFromWebView();
                    setState(() {
                      _errorKind = null;
                      _errorMessage = null;
                    });
                    await _performSearch();
                  },
                  child: Text(AppLocalizations.of(context)?.login ?? 'Login'),
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
                        Icon(Icons.cached, size: 16, color: Colors.blue[700]),
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
  );

  Widget _buildBodyState() {
    if (_errorKind != null) {
      return _buildErrorState();
    }
    return _buildSearchResults();
  }

  Widget _buildSearchResults() {
    if (_searchResults.isEmpty) {
      return Center(
        child: Text(AppLocalizations.of(context)!.noResults),
      );
    }

    return ListView.builder(
      controller: _scrollController,
      itemCount: _searchResults.length + (_hasMore ? 1 : 0),
      itemBuilder: (context, index) {
        if (index >= _searchResults.length) {
          // Load more indicator / button
          return Padding(
            padding: const EdgeInsets.symmetric(vertical: 16),
            child: Center(
              child: _isLoadingMore
                  ? const SizedBox(width: 24, height: 24, child: CircularProgressIndicator())
                  : OutlinedButton(
                      onPressed: _loadMore,
                      child: const Text('Load more'),
                    ),
            ),
          );
        }
        final audiobook = _searchResults[index];
        final title = audiobook['title'] as String? ?? AppLocalizations.of(context)!.unknownTitle;
        final author = audiobook['author'] as String? ?? AppLocalizations.of(context)!.unknownAuthor;
        final size = audiobook['size'] as String? ?? AppLocalizations.of(context)!.unknownSize;
        final seeders = audiobook['seeders'] as int? ?? 0;
        final leechers = audiobook['leechers'] as int? ?? 0;
        final id = audiobook['id'] as String? ?? '';
        return Card(
          margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          child: ListTile(
            leading: const Icon(Icons.audiotrack),
            title: Text(
              title,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
            subtitle: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('${AppLocalizations.of(context)!.authorLabel}$author'),
                Text('${AppLocalizations.of(context)!.sizeLabel}$size'),
                Row(
                  children: [
                    Icon(
                      Icons.people,
                      size: 16,
                      color: seeders > 0 ? Colors.green : Colors.grey,
                    ),
                    const SizedBox(width: 4),
                    Text('$seeders${AppLocalizations.of(context)!.seedersLabel}'),
                    const SizedBox(width: 16),
                    Icon(
                      Icons.person_off,
                      size: 16,
                      color: leechers > 0 ? Colors.orange : Colors.grey,
                    ),
                    const SizedBox(width: 4),
                    Text('$leechers${AppLocalizations.of(context)!.leechersLabel}'),
                  ],
                ),
              ],
            ),
            trailing: const Icon(Icons.arrow_forward_ios),
            onTap: () {
              // Navigate to topic details
              Navigator.pushNamed(
                context,
                '/topic/$id',
              );
            },
          ),
        );
      },
    );
  }

  void _onScroll() {
    if (!_hasMore || _isLoadingMore) return;
    if (_scrollController.position.pixels >= _scrollController.position.maxScrollExtent - 200) {
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
                MaterialPageRoute(builder: (_) => const RutrackerLoginScreen()),
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
        message = 'You need to log in to RuTracker to search for audiobooks. This will open a secure web view where you can complete authentication.';
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
        message = 'The search request took too long to complete. This might be due to a slow mirror or network issues.';
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
                    MaterialPageRoute(builder: (_) => const MirrorSettingsScreen()),
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
        message = 'The current RuTracker mirror is not responding. You can try to automatically find a working mirror or manually manage them.';
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
        message = 'Unable to connect to RuTracker. Please check your internet connection and try again.';
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
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
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