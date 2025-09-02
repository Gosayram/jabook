import 'dart:async';

import 'package:cached_network_image/cached_network_image.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:jabook/core/cache/rutracker_cache_service.dart';
import 'package:jabook/core/config/app_config.dart';
import 'package:jabook/core/endpoints/url_constants.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/parse/rutracker_parser.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Screen for displaying a specific RuTracker topic.
///
/// This screen shows the details of a specific forum topic,
/// including posts, attachments, and download links.
class TopicScreen extends ConsumerStatefulWidget {

  /// Creates a new TopicScreen instance.
  ///
  /// The [topicId] parameter is required to identify which topic
  /// should be displayed.
  const TopicScreen({super.key, required this.topicId});
  
  /// The unique identifier of the topic to display.
  final String topicId;

  @override
  ConsumerState<TopicScreen> createState() => _TopicScreenState();
}

class _TopicScreenState extends ConsumerState<TopicScreen> {
  final RuTrackerParser _parser = RuTrackerParser();
  final RuTrackerCacheService _cacheService = RuTrackerCacheService();
  
  Map<String, dynamic>? _audiobook;
  bool _isLoading = true;
  bool _hasError = false;
  bool _isFromCache = false;

  @override
  void initState() {
    super.initState();
    _initializeCache();
    _loadTopicDetails();
  }

  Future<void> _initializeCache() async {
    // Cache service initialization would typically happen at app startup
  }

  Future<void> _loadTopicDetails() async {
    // First try to get from cache
    final cachedAudiobook = await _cacheService.getCachedTopicDetails(widget.topicId);
    if (cachedAudiobook != null) {
      setState(() {
        _audiobook = cachedAudiobook;
        _isLoading = false;
        _hasError = false;
        _isFromCache = true;
      });
      return;
    }

    // If not in cache, fetch from network
    try {
      final dio = await DioClient.instance;
      final response = await dio.get(
        UrlConstants.getTopicViewUrl(AppConfig().rutrackerUrl, widget.topicId),
      ).timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        final audiobook = await _parser.parseTopicDetails(response.data);
        
        if (audiobook != null) {
          // Cache the topic details
          // Convert Audiobook object to map for caching
          final audiobookMap = _audiobookToMap(audiobook);
          await _cacheService.cacheTopicDetails(widget.topicId, audiobookMap);
        }
        
        if (mounted) {
          setState(() {
            // Convert Audiobook object to map for UI
            if (audiobook != null) {
              _audiobook = _audiobookToMap(audiobook);
            } else {
              _audiobook = null;
            }
            _isLoading = false;
            _hasError = false;
            _isFromCache = false;
          });
        }
      } else {
        if (mounted) {
          setState(() {
            _isLoading = false;
            _hasError = true;
          });
        }
      }
    } on TimeoutException {
      if (mounted) {
        setState(() {
          _isLoading = false;
          _hasError = true;
        });
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Request timed out. Please check your connection.')),
        );
      }
    } on DioException catch (e) {
      if (mounted) {
        setState(() {
          _isLoading = false;
          _hasError = true;
        });
        
        // Handle authentication errors specifically
        if (e.message?.contains('Authentication required') ?? false) {
          _showAuthenticationPrompt(context);
        } else {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Network error: ${e.message}')),
          );
        }
      }
    } on Exception catch (e) {
      if (mounted) {
        setState(() {
          _isLoading = false;
          _hasError = true;
        });
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error loading topic: $e')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: Text('Topic: ${widget.topicId}'),
      actions: [
        if (_isFromCache)
          IconButton(
            icon: const Icon(Icons.cached),
            tooltip: 'Loaded from cache',
            onPressed: () {
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('Data loaded from cache')),
              );
            },
          ),
        if (_audiobook != null && (_audiobook!['magnetUrl'] as String).isNotEmpty)
          IconButton(
            icon: const Icon(Icons.download),
            onPressed: _downloadAudiobook,
          ),
      ],
    ),
    body: _buildBody(),
  );

  Widget _buildBody() {
    if (_isLoading) {
      return const Center(
        child: CircularProgressIndicator(),
      );
    }

    if (_hasError || _audiobook == null) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Text('Failed to load topic'),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: _loadTopicDetails,
              child: const Text('Retry'),
            ),
          ],
        ),
      );
    }

    return CustomScrollView(
      slivers: [
        SliverToBoxAdapter(
          child: _buildHeader(),
        ),
        if ((_audiobook!['chapters'] as List).isNotEmpty)
          SliverToBoxAdapter(
            child: _buildChaptersSection(),
          ),
      ],
    );
  }

  Widget _buildHeader() {
    final audiobook = _audiobook!;
    final title = audiobook['title'] as String? ?? 'Unknown Title';
    final author = audiobook['author'] as String? ?? 'Unknown Author';
    final category = audiobook['category'] as String? ?? 'Unknown Category';
    final size = audiobook['size'] as String? ?? 'Unknown Size';
    final seeders = audiobook['seeders'] as int? ?? 0;
    final leechers = audiobook['leechers'] as int? ?? 0;
    final coverUrl = audiobook['coverUrl'] as String?;
    final magnetUrl = audiobook['magnetUrl'] as String? ?? '';
    // Chapters variable is not used, removed to fix lint warning
    
    return Padding(
      padding: const EdgeInsets.all(16.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: Theme.of(context).textTheme.headlineSmall,
            maxLines: 3,
            overflow: TextOverflow.ellipsis,
          ),
          const SizedBox(height: 8),
          Text(
            'by $author',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              Chip(
                label: Text(category),
                backgroundColor: Colors.blue.shade100,
              ),
              const SizedBox(width: 8),
              Chip(
                label: Text(size),
                backgroundColor: Colors.green.shade100,
              ),
              const SizedBox(width: 8),
              Chip(
                label: Text('$seeders seeders'),
                backgroundColor: Colors.green.shade100,
              ),
              const SizedBox(width: 8),
              Chip(
                label: Text('$leechers leechers'),
                backgroundColor: Colors.orange.shade100,
              ),
            ],
          ),
          const SizedBox(height: 16),
          if (coverUrl != null)
            CachedNetworkImage(
              imageUrl: coverUrl,
              height: 200,
              fit: BoxFit.cover,
              placeholder: (context, url) => const Center(
                child: CircularProgressIndicator(),
              ),
              errorWidget: (context, url, error) => const Icon(Icons.error),
            ),
          const SizedBox(height: 16),
          if (magnetUrl.isNotEmpty)
            Card(
              child: ListTile(
                leading: const Icon(Icons.link),
                title: const Text('Magnet Link'),
                subtitle: Text(
                  magnetUrl,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
                trailing: const Icon(Icons.copy),
                onTap: () {
                  _copyToClipboard(magnetUrl, 'Magnet link');
                },
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildChaptersSection() {
    final audiobook = _audiobook!;
    final chapters = audiobook['chapters'] as List<dynamic>? ?? [];
    
    return Padding(
      padding: const EdgeInsets.all(16.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Chapters (${chapters.length})',
            style: Theme.of(context).textTheme.titleLarge,
          ),
          const SizedBox(height: 8),
          ListView.builder(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            itemCount: chapters.length,
            itemBuilder: (context, index) {
              final chapter = chapters[index] as Map<String, dynamic>;
              final chapterTitle = chapter['title'] as String? ?? 'Unknown Chapter';
              final durationMs = chapter['durationMs'] as int? ?? 0;
              return Card(
                margin: const EdgeInsets.only(bottom: 8),
                child: ListTile(
                  leading: const Icon(Icons.play_circle_outline),
                  title: Text(chapterTitle),
                  subtitle: Text(_formatDuration(durationMs)),
                  trailing: const Icon(Icons.more_vert),
                  onTap: () {
                    // TODO: Implement chapter navigation
                  },
                ),
              );
            },
          ),
        ],
      ),
    );
  }

  String _formatDuration(int milliseconds) {
    final duration = Duration(milliseconds: milliseconds);
    final hours = duration.inHours;
    final minutes = duration.inMinutes.remainder(60);
    final seconds = duration.inSeconds.remainder(60);
    
    if (hours > 0) {
      return '$hours:${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}';
    } else {
      return '$minutes:${seconds.toString().padLeft(2, '0')}';
    }
  }

  void _downloadAudiobook() {
    if (_audiobook != null && (_audiobook!['magnetUrl'] as String).isNotEmpty) {
      _copyToClipboard(_audiobook!['magnetUrl'] as String, 'Magnet link');
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Magnet link copied to clipboard')),
      );
    }
  }

  void _copyToClipboard(String text, String label) {
    // TODO: Implement actual clipboard copy
    // For now, just show a message
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('$label copied to clipboard')),
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

/// Shows authentication prompt when login is required.
void _showAuthenticationPrompt(BuildContext context) {
  showDialog(
    context: context,
    builder: (ctx) => AlertDialog(
      title: Text(AppLocalizations.of(context)!.authenticationRequired),
      content: Text(AppLocalizations.of(context)!.loginRequiredForSearch),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(ctx),
          child: Text(AppLocalizations.of(context)!.cancel),
        ),
        TextButton(
          onPressed: () {
            Navigator.pop(ctx);
            // Navigate to login screen
            Navigator.pushNamed(context, '/login');
          },
          child: Text(AppLocalizations.of(context)!.login),
        ),
      ],
    ),
  );
}