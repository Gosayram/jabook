import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:jabook/core/cache/rutracker_cache_service.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/parse/rutracker_parser.dart';

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
  
  List<Audiobook> _searchResults = [];
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
        'https://rutracker.me/forum/tracker.php',
        queryParameters: {
          'nm': query,
          'o=1': '1', // Sort by relevance
        },
      );

      if (response.statusCode == 200) {
        final results = await _parser.parseSearchResults(response.data);
        
        // Cache the results
        await _cacheService.cacheSearchResults(query, results);
        
        setState(() {
          _searchResults = results;
          _isLoading = false;
          _isFromCache = false;
        });
      } else {
        setState(() {
          _isLoading = false;
        });
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Failed to search')),
          );
        }
      }
    } on Exception catch (e) {
      setState(() {
        _isLoading = false;
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error: $e')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('Search Audiobooks'),
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
              labelText: 'Search audiobooks...',
              hintText: 'Enter title, author, or keywords',
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
                          'Results from cache',
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
          const Expanded(
            child: Center(
              child: Text('Enter a search term to begin'),
            ),
          ),
      ],
    ),
  );

  Widget _buildSearchResults() {
    if (_searchResults.isEmpty) {
      return const Center(
        child: Text('No results found'),
      );
    }

    return ListView.builder(
      itemCount: _searchResults.length,
      itemBuilder: (context, index) {
        final audiobook = _searchResults[index];
        return Card(
          margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          child: ListTile(
            leading: const Icon(Icons.audiotrack),
            title: Text(
              audiobook.title,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
            subtitle: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Author: ${audiobook.author}'),
                Text('Size: ${audiobook.size}'),
                Row(
                  children: [
                    Icon(
                      Icons.people,
                      size: 16,
                      color: audiobook.seeders > 0 ? Colors.green : Colors.grey,
                    ),
                    const SizedBox(width: 4),
                    Text('${audiobook.seeders} seeders'),
                    const SizedBox(width: 16),
                    Icon(
                      Icons.person_off,
                      size: 16,
                      color: audiobook.leechers > 0 ? Colors.orange : Colors.grey,
                    ),
                    const SizedBox(width: 4),
                    Text('${audiobook.leechers} leechers'),
                  ],
                ),
              ],
            ),
            trailing: const Icon(Icons.arrow_forward_ios),
            onTap: () {
              // Navigate to topic details
              Navigator.pushNamed(
                context,
                '/topic/${audiobook.id}',
              );
            },
          ),
        );
      },
    );
  }
}