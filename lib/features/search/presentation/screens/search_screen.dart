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

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  @override
  void initState() {
    super.initState();
    _initializeCache();
  }

  Future<void> _initializeCache() async {
    // Cache service initialization would typically happen at app startup
    // For now, we'll handle it here
  }

  Future<void> _performSearch() async {
    if (_searchController.text.trim().isEmpty) return;

    setState(() {
      _isLoading = true;
      _hasSearched = true;
      _isFromCache = false;
      _errorKind = null;
      _errorMessage = null;
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
        },
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
          _errorMessage = AppLocalizations.of(context)?.requestTimedOut ?? 'Timeout';
        });
      }
    } on DioException catch (e) {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
        
        // Handle authentication errors specifically
        if (e.message?.contains('Authentication required') ?? false) {
          setState(() {
            _errorKind = 'auth';
            _errorMessage = 'Authentication required';
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
            Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const MirrorSettingsScreen()),
            );
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
      itemCount: _searchResults.length,
      itemBuilder: (context, index) {
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

  Widget _buildErrorState() {
    final loc = AppLocalizations.of(context)!;
    String title;
    var actions = <Widget>[];
    var message = _errorMessage ?? '';

    switch (_errorKind) {
      case 'auth':
        title = loc.authenticationRequired;
        actions = [
          ElevatedButton(
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
            child: Text(loc.login),
          ),
        ];
        message = loc.loginRequiredForSearch;
        break;
      case 'timeout':
        title = loc.timeoutError;
        actions = [
          ElevatedButton(
            onPressed: _performSearch,
            child: Text(loc.retry),
          ),
          TextButton(
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const MirrorSettingsScreen()),
            ),
            child: Text(loc.changeMirror),
          ),
        ];
        break;
      case 'network':
      default:
        title = loc.networkErrorUser;
        actions = [
          ElevatedButton(
            onPressed: _performSearch,
            child: Text(loc.retry),
          ),
          TextButton(
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const MirrorSettingsScreen()),
            ),
            child: Text(loc.changeMirror),
          ),
        ];
        break;
    }

    return Center(
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.error_outline, size: 48, color: Colors.red.shade400),
            const SizedBox(height: 12),
            Text(title, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 8),
            if (message.isNotEmpty)
              Text(
                message,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodySmall,
              ),
            const SizedBox(height: 16),
            Wrap(spacing: 12, runSpacing: 8, alignment: WrapAlignment.center, children: actions),
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