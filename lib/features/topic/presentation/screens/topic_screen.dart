import 'dart:async';

import 'package:cached_network_image/cached_network_image.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/core/cache/rutracker_cache_service.dart';
import 'package:jabook/core/endpoints/endpoint_provider.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/parse/rutracker_parser.dart';
import 'package:jabook/features/webview/secure_rutracker_webview.dart';
import 'package:jabook/l10n/app_localizations.dart';
import 'package:url_launcher/url_launcher.dart';

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
      final endpointManager = ref.read(endpointManagerProvider);
      final activeEndpoint = await endpointManager.getActiveEndpoint();
      final dio = await DioClient.instance;
      final response = await dio.get(
        '$activeEndpoint/forum/viewtopic.php?t=${widget.topicId}',
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
          SnackBar(content: Text(AppLocalizations.of(context)?.requestTimedOutMessage ?? 'Request timed out. Please check your connection.')),
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
            SnackBar(content: Text('Network error: ${e.message ?? "Unknown error"}')),
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
      title: Text('${AppLocalizations.of(context)?.topicTitle ?? 'Topic'}: ${widget.topicId}'),
      actions: [
        if (_isFromCache)
          IconButton(
            icon: const Icon(Icons.cached),
            tooltip: AppLocalizations.of(context)?.dataLoadedFromCacheMessage ?? 'Loaded from cache',
            onPressed: () {
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(content: Text(AppLocalizations.of(context)?.dataLoadedFromCacheMessage ?? 'Data loaded from cache')),
              );
            },
          ),
        if (_audiobook != null && (_audiobook!['magnetUrl'] as String).isNotEmpty)
          PopupMenuButton<String>(
            icon: const Icon(Icons.download),
            onSelected: _handleDownloadAction,
            itemBuilder: (context) => [
              const PopupMenuItem(
                value: 'magnet',
                child: ListTile(
                  leading: Icon(Icons.link),
                  title: Text('Copy Magnet Link'),
                  contentPadding: EdgeInsets.zero,
                ),
              ),
              const PopupMenuItem(
                value: 'torrent',
                child: ListTile(
                  leading: Icon(Icons.file_download),
                  title: Text('Download Torrent'),
                  contentPadding: EdgeInsets.zero,
                ),
              ),
            ],
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
            Text(AppLocalizations.of(context)?.failedToLoadTopicMessage ?? 'Failed to load topic'),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: _loadTopicDetails,
              child: Text(AppLocalizations.of(context)?.retry ?? 'Retry'),
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
                label: Text('$seeders${AppLocalizations.of(context)?.seedersLabel ?? ' seeders'}'),
                backgroundColor: Colors.green.shade100,
              ),
              const SizedBox(width: 8),
              Chip(
                label: Text('$leechers${AppLocalizations.of(context)?.leechersLabel ?? ' leechers'}'),
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
              errorWidget: (context, url, e) => const Icon(Icons.error),
            ),
          const SizedBox(height: 16),
          if (magnetUrl.isNotEmpty)
            Card(
              child: Column(
                children: [
                  ListTile(
                    leading: const Icon(Icons.link),
                    title: Text(AppLocalizations.of(context)?.magnetLinkLabelText ?? 'Magnet Link'),
                    subtitle: Text(
                      magnetUrl,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                    trailing: const Icon(Icons.copy),
                    onTap: () {
                      _copyToClipboard(magnetUrl, AppLocalizations.of(context)?.magnetLinkCopiedMessage ?? 'Magnet link');
                    },
                  ),
                  const Divider(height: 1),
                  ListTile(
                    leading: const Icon(Icons.file_download),
                    title: const Text('Download Torrent'),
                    subtitle: const Text('Open torrent file in external app'),
                    trailing: const Icon(Icons.open_in_new),
                    onTap: _downloadTorrent,
                  ),
                ],
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
            '${AppLocalizations.of(context)?.chaptersLabelText ?? 'Chapters'} (${chapters.length})',
            style: Theme.of(context).textTheme.titleLarge,
          ),
          const SizedBox(height: 8),
          ListView.builder(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            itemCount: chapters.length,
            itemBuilder: (context, index) {
              final chapter = chapters[index] as Map<String, dynamic>;
              final chapterTitle = chapter['title'] as String? ?? AppLocalizations.of(context)?.unknownChapterText ?? 'Unknown Chapter';
              final durationMs = chapter['durationMs'] as int? ?? 0;
              return Card(
                margin: const EdgeInsets.only(bottom: 8),
                child: ListTile(
                  leading: const Icon(Icons.play_circle_outline),
                  title: Text(chapterTitle),
                  subtitle: Text(_formatDuration(durationMs)),
                  trailing: const Icon(Icons.more_vert),
                  onTap: () {
                    _playChapter(chapter);
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

  void _handleDownloadAction(String action) {
    if (_audiobook == null) return;
    
    switch (action) {
      case 'magnet':
        _copyMagnetLink();
        break;
      case 'torrent':
        _downloadTorrent();
        break;
    }
  }

  void _copyMagnetLink() {
    if (_audiobook != null && (_audiobook!['magnetUrl'] as String).isNotEmpty) {
      _copyToClipboard(_audiobook!['magnetUrl'] as String, 'Magnet link');
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(AppLocalizations.of(context)?.magnetLinkCopiedMessage ?? 'Magnet link copied to clipboard')),
      );
    }
  }

  Future<void> _downloadTorrent() async {
    if (_audiobook == null) return;
    
    try {
      final endpointManager = ref.read(endpointManagerProvider);
      final activeEndpoint = await endpointManager.getActiveEndpoint();
      final torrentUrl = '$activeEndpoint/forum/dl.php?t=${widget.topicId}';
      
      final uri = Uri.parse(torrentUrl);
      if (await canLaunchUrl(uri)) {
        await launchUrl(uri, mode: LaunchMode.externalApplication);
      } else {
        throw Exception('Cannot launch torrent URL');
      }
    } on Exception catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to open torrent: $e')),
        );
      }
    }
  }

  void _playChapter(Map<String, dynamic> chapter) {
    // Navigate to player with this audiobook and chapter
    Navigator.pushNamed(
      context,
      '/player/${widget.topicId}',
      arguments: {
        'audiobook': _audiobook,
        'chapterIndex': (_audiobook!['chapters'] as List).indexOf(chapter),
      },
    );
  }

  void _copyToClipboard(String text, String label) {
    Clipboard.setData(ClipboardData(text: text));
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
            Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const SecureRutrackerWebView()),
            ).then((_) async {
              // Sync cookies after login
              await DioClient.syncCookiesFromWebView();
              // Retry loading topic - this will be handled by the parent widget
            });
          },
          child: Text(AppLocalizations.of(context)!.login),
        ),
      ],
    ),
  );
}