import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:jabook/core/cache/rutracker_cache_service.dart';
import 'package:jabook/core/config/app_config.dart';
import 'package:jabook/core/endpoints/url_constants.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/parse/rutracker_parser.dart';
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

    // If not in cache, fetch from network
    try {
      final dio = await DioClient.instance;
      final response = await dio.get(
        UrlConstants.getSearchUrl(AppConfig().rutrackerUrl),
        queryParameters: {
          'nm': query,
          'o=1': '1', // Sort by relevance
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
        });
      } else {
        setState(() {
          _isLoading = false;
        });
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(AppLocalizations.of(context)!.failedToSearch)),
          );
        }
      }
    } on TimeoutException {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Request timed out. Please check your connection.')),
        );
      }
    } on DioException catch (e) {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Network error: ${e.message}')),
        );
      }
    } on Exception catch (e) {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error: $e')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: Text(AppLocalizations.of(context)!.searchAudiobooks),
      actions: [
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
                  child: _buildSearchResults(),
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