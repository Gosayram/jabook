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
import 'dart:io';

import 'package:cached_network_image/cached_network_image.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:jabook/core/cache/rutracker_cache_service.dart';
import 'package:jabook/core/endpoints/endpoint_provider.dart';
import 'package:jabook/core/errors/failures.dart';
import 'package:jabook/core/logging/environment_logger.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/parse/rutracker_parser.dart';
import 'package:jabook/core/session/auth_error_handler.dart';
import 'package:jabook/core/torrent/audiobook_torrent_manager.dart';
import 'package:jabook/core/torrent/audiobook_torrent_manager_provider.dart';
import 'package:jabook/core/torrent/torrent_parser_service.dart';
import 'package:jabook/features/downloads/presentation/widgets/download_status_bar.dart';
import 'package:jabook/l10n/app_localizations.dart';
import 'package:path_provider/path_provider.dart';

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
  String? _activeDownloadId;
  StreamSubscription<TorrentProgress>? _downloadProgressSubscription;
  TorrentProgress? _currentProgress;

  @override
  void initState() {
    super.initState();
    _initializeCache();
    _loadTopicDetails();
  }

  @override
  void dispose() {
    _downloadProgressSubscription?.cancel();
    super.dispose();
  }

  Future<void> _initializeCache() async {
    // Cache service initialization would typically happen at app startup
  }

  Future<void> _loadTopicDetails() async {
    // First try to get from cache
    final cachedAudiobook =
        await _cacheService.getCachedTopicDetails(widget.topicId);
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
      final response = await dio
          .get(
            '$activeEndpoint/forum/viewtopic.php?t=${widget.topicId}',
            options: Options(
              // Get raw bytes (Brotli decompression handled automatically by DioBrotliTransformer)
              // Bytes are ready for Windows-1251 decoding
              responseType: ResponseType.bytes,
            ),
          )
          .timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        // Pass response data and headers to parser for proper encoding detection
        // Note: Brotli decompression is handled automatically by DioBrotliTransformer
        var audiobook = await _parser.parseTopicDetails(
          response.data,
          contentType: response.headers.value('content-type'),
        );

        // If chapters are empty, try to extract from torrent file
        if (audiobook != null && audiobook.chapters.isEmpty) {
          try {
            final chaptersFromTorrent = await _extractChaptersFromTorrent(
              widget.topicId,
              activeEndpoint,
            );
            if (chaptersFromTorrent.isNotEmpty) {
              // Create new Audiobook with chapters from torrent
              audiobook = Audiobook(
                id: audiobook.id,
                title: audiobook.title,
                author: audiobook.author,
                category: audiobook.category,
                size: audiobook.size,
                seeders: audiobook.seeders,
                leechers: audiobook.leechers,
                magnetUrl: audiobook.magnetUrl,
                coverUrl: audiobook.coverUrl,
                performer: audiobook.performer,
                genres: audiobook.genres,
                chapters: chaptersFromTorrent,
                addedDate: audiobook.addedDate,
              );
            }
          } on AuthFailure catch (e) {
            // Handle authentication errors - show user-friendly message
            if (mounted) {
              AuthErrorHandler.showAuthErrorSnackBar(context, e);
            }
            // Continue with HTML-parsed chapters (empty list)
          } on Exception {
            // Ignore other errors when extracting from torrent
            // Fallback to HTML-parsed chapters (empty list)
          }
        }

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
          SnackBar(
              content: Text(
                  AppLocalizations.of(context)?.requestTimedOutMessage ??
                      'Request timed out. Please check your connection.')),
        );
      }
    } on DioException catch (e) {
      if (mounted) {
        setState(() {
          _isLoading = false;
          _hasError = true;
        });

        // Handle authentication errors using AuthErrorHandler
        if (e.error is AuthFailure) {
          final authError = e.error as AuthFailure;
          AuthErrorHandler.showAuthErrorSnackBar(context, authError);
          _showAuthenticationPrompt(context);
        } else if (e.message?.contains('Authentication required') ??
            false ||
                e.response?.statusCode == 401 ||
                e.response?.statusCode == 403) {
          AuthErrorHandler.showAuthErrorSnackBar(context, e);
          _showAuthenticationPrompt(context);
        } else {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
                content: Text(AppLocalizations.of(context)!
                    .networkErrorMessage(e.message ?? 'Unknown error'))),
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
          SnackBar(
              content: Text(AppLocalizations.of(context)!
                  .errorLoadingTopicMessage(e.toString()))),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
          title: Text(
              '${AppLocalizations.of(context)?.topicTitle ?? 'Topic'}: ${widget.topicId}'),
          actions: [
            if (_isFromCache)
              IconButton(
                icon: const Icon(Icons.cached),
                tooltip:
                    AppLocalizations.of(context)?.dataLoadedFromCacheMessage ??
                        'Loaded from cache',
                onPressed: () {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                        content: Text(AppLocalizations.of(context)
                                ?.dataLoadedFromCacheMessage ??
                            'Data loaded from cache')),
                  );
                },
              ),
            if (_audiobook != null)
              PopupMenuButton<String>(
                icon: const Icon(Icons.more_vert),
                onSelected: (value) async {
                  if (value == 'refresh_chapters') {
                    await _refreshChaptersFromTorrent();
                  } else if (value == 'magnet' ||
                      value == 'torrent' ||
                      value == 'download_magnet') {
                    _handleDownloadAction(value);
                  }
                },
                itemBuilder: (context) => [
                  PopupMenuItem(
                    value: 'refresh_chapters',
                    child: ListTile(
                      leading: const Icon(Icons.refresh),
                      title: Text(AppLocalizations.of(context)
                              ?.refreshChaptersFromTorrent ??
                          'Refresh chapters from torrent'),
                      contentPadding: EdgeInsets.zero,
                    ),
                  ),
                  if ((_audiobook!['magnetUrl'] as String).isNotEmpty) ...[
                    const PopupMenuDivider(),
                    PopupMenuItem(
                      value: 'magnet',
                      child: ListTile(
                        leading: const Icon(Icons.link),
                        title: Text(
                            AppLocalizations.of(context)?.copyMagnetLink ??
                                'Copy Magnet Link'),
                        contentPadding: EdgeInsets.zero,
                      ),
                    ),
                    PopupMenuItem(
                      value: 'torrent',
                      child: ListTile(
                        leading: const Icon(Icons.file_download),
                        title: Text(
                            AppLocalizations.of(context)?.downloadTorrentMenu ??
                                'Download Torrent'),
                        contentPadding: EdgeInsets.zero,
                      ),
                    ),
                    PopupMenuItem(
                      value: 'download_magnet',
                      child: ListTile(
                        leading: const Icon(Icons.download),
                        title: Text(
                            AppLocalizations.of(context)?.downloadViaMagnet ??
                                'Download via Magnet'),
                        contentPadding: EdgeInsets.zero,
                      ),
                    ),
                  ],
                ],
              ),
          ],
        ),
        body: Stack(
          children: [
            _buildBody(),
            if (_activeDownloadId != null && _currentProgress != null)
              Positioned(
                bottom: 0,
                left: 0,
                right: 0,
                child: DownloadStatusBar(
                  downloadId: _activeDownloadId!,
                  progress: _currentProgress!,
                  onPause: () async {
                    final torrentManager =
                        await ref.read(audiobookTorrentManagerProvider.future);
                    await torrentManager.pauseDownload(_activeDownloadId!);
                    if (mounted) {
                      setState(() {
                        _currentProgress = TorrentProgress(
                          progress: _currentProgress!.progress,
                          downloadSpeed: 0.0,
                          uploadSpeed: 0.0,
                          downloadedBytes: _currentProgress!.downloadedBytes,
                          totalBytes: _currentProgress!.totalBytes,
                          seeders: _currentProgress!.seeders,
                          leechers: _currentProgress!.leechers,
                          status: 'paused',
                        );
                      });
                    }
                  },
                  onResume: () async {
                    final torrentManager =
                        await ref.read(audiobookTorrentManagerProvider.future);
                    await torrentManager.resumeDownload(_activeDownloadId!);
                  },
                  onCancel: () async {
                    final torrentManager =
                        await ref.read(audiobookTorrentManagerProvider.future);
                    await torrentManager.removeDownload(_activeDownloadId!);
                    if (mounted) {
                      setState(() {
                        _activeDownloadId = null;
                        _currentProgress = null;
                      });
                    }
                  },
                ),
              ),
          ],
        ),
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
            Text(AppLocalizations.of(context)?.failedToLoadTopicMessage ??
                'Failed to load topic'),
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
    final performer = audiobook['performer'] as String?;
    final category = audiobook['category'] as String? ?? 'Unknown Category';
    final size = audiobook['size'] as String? ?? 'Unknown Size';
    final seeders = audiobook['seeders'] as int? ?? 0;
    final leechers = audiobook['leechers'] as int? ?? 0;
    final coverUrl = audiobook['coverUrl'] as String?;
    final magnetUrl = audiobook['magnetUrl'] as String? ?? '';
    final genres = (audiobook['genres'] as List<dynamic>?)
            ?.map((g) => g.toString())
            .where((g) => g.isNotEmpty)
            .toList() ??
        <String>[];
    // Chapters variable is not used, removed to fix lint warning

    // Determine font size based on screen size
    final screenWidth = MediaQuery.of(context).size.width;
    final isSmallScreen = screenWidth < 360;
    final titleStyle = isSmallScreen
        ? Theme.of(context).textTheme.titleLarge?.copyWith(fontSize: 20)
        : Theme.of(context).textTheme.headlineSmall;
    final authorStyle = isSmallScreen
        ? Theme.of(context).textTheme.titleSmall
        : Theme.of(context).textTheme.titleMedium;

    return Padding(
      padding: const EdgeInsets.all(16.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _ExpandableTitle(
            title: title,
            style: titleStyle,
          ),
          const SizedBox(height: 12),
          SelectableText(
            'by $author',
            style: authorStyle,
          ),
          if (performer != null && performer.isNotEmpty) ...[
            const SizedBox(height: 8),
            SelectableText(
              'Performed by $performer',
              style: Theme.of(context).textTheme.bodyMedium,
            ),
          ],
          const SizedBox(height: 16),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              _buildChip(
                category,
                Colors.blue.shade100,
              ),
              _buildChip(
                size,
                Colors.green.shade100,
              ),
              _buildChip(
                '$seeders${AppLocalizations.of(context)?.seedersLabel ?? ' seeders'}',
                Colors.green.shade100,
                labelColor: Colors.green.shade900,
              ),
              _buildChip(
                '$leechers${AppLocalizations.of(context)?.leechersLabel ?? ' leechers'}',
                Colors.red.shade100,
                labelColor: Colors.red.shade900,
              ),
            ],
          ),
          if (genres.isNotEmpty) ...[
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 4,
              children: genres
                  .map((genre) => Chip(
                        label: Text(genre),
                        backgroundColor: Colors.purple.shade100,
                        labelStyle: TextStyle(color: Colors.purple.shade900),
                      ))
                  .toList(),
            ),
          ],
          const SizedBox(height: 16),
          if (coverUrl != null)
            RepaintBoundary(
              child: CachedNetworkImage(
                imageUrl: coverUrl,
                height: 200,
                fit: BoxFit.cover,
                placeholder: (context, url) => Container(
                  height: 200,
                  color: Theme.of(context).colorScheme.surfaceContainerHighest,
                  child: const Center(
                    child: CircularProgressIndicator(),
                  ),
                ),
                errorWidget: (context, url, e) => Container(
                  height: 200,
                  color: Theme.of(context).colorScheme.errorContainer,
                  child: Icon(
                    Icons.error_outline,
                    color: Theme.of(context).colorScheme.onErrorContainer,
                  ),
                ),
              ),
            ),
          const SizedBox(height: 16),
          if (magnetUrl.isNotEmpty)
            Card(
              child: Column(
                children: [
                  ListTile(
                    leading: const Icon(Icons.link),
                    title: Text(
                        AppLocalizations.of(context)?.magnetLinkLabelText ??
                            'Magnet Link'),
                    subtitle: Text(
                      magnetUrl,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                    trailing: const Icon(Icons.copy),
                    onTap: _copyMagnetLink,
                  ),
                  const Divider(height: 1),
                  ListTile(
                    leading: const Icon(Icons.file_download),
                    title: Text(
                        AppLocalizations.of(context)?.downloadTorrentMenu ??
                            'Download Torrent'),
                    subtitle: Text(AppLocalizations.of(context)
                            ?.openTorrentInExternalApp ??
                        'Open torrent file in external app'),
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
              final chapterTitle = chapter['title'] as String? ??
                  AppLocalizations.of(context)?.unknownChapterText ??
                  'Unknown Chapter';
              final durationMs = chapter['durationMs'] as int? ?? 0;
              // Use RepaintBoundary to isolate repaints for each chapter item
              return RepaintBoundary(
                child: Card(
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
      case 'download_magnet':
        _startMagnetDownload();
        break;
    }
  }

  void _copyMagnetLink() {
    if (_audiobook != null && (_audiobook!['magnetUrl'] as String).isNotEmpty) {
      final magnetLinkLabel =
          AppLocalizations.of(context)?.magnetLinkLabelText ?? 'Magnet link';
      _copyToClipboard(_audiobook!['magnetUrl'] as String, magnetLinkLabel);
    }
  }

  Future<void> _downloadTorrent() async {
    if (_audiobook == null) return;

    try {
      final endpointManager = ref.read(endpointManagerProvider);
      final activeEndpoint = await endpointManager.getActiveEndpoint();
      final torrentUrl = '$activeEndpoint/forum/dl.php?t=${widget.topicId}';

      // Get temporary directory for torrent file
      final tempDir = await getTemporaryDirectory();
      final torrentFile =
          File('${tempDir.path}/torrent_${widget.topicId}.torrent');

      // Download torrent file
      final dio = await DioClient.instance;
      try {
        await dio.download(torrentUrl, torrentFile.path);
      } on DioException catch (e) {
        // Check if error is authentication-related
        if (e.response?.statusCode == 401 ||
            e.response?.statusCode == 403 ||
            (e.message?.toLowerCase().contains('authentication') ?? false) ||
            (e.response?.realUri.toString().contains('login.php') ?? false)) {
          // Delete temporary file if it exists
          try {
            if (await torrentFile.exists()) {
              await torrentFile.delete();
            }
          } on Exception {
            // Ignore deletion errors
          }
          if (mounted) {
            AuthErrorHandler.showAuthErrorSnackBar(
              context,
              const AuthFailure.loginRequired(),
            );
          }
          return;
        }
        // Re-throw other DioExceptions
        rethrow;
      }

      // Check if downloaded file is actually a torrent file
      // Sometimes RuTracker returns HTML login page instead of torrent file
      final torrentBytes = await torrentFile.readAsBytes();

      // Check if file is too small (likely not a torrent) or starts with HTML
      if (torrentBytes.length < 100) {
        // Too small to be a valid torrent file
        try {
          await torrentFile.delete();
        } on Exception {
          // Ignore deletion errors
        }
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                AppLocalizations.of(context)?.failedToStartDownload ??
                    'Failed to start download: Invalid torrent file',
              ),
            ),
          );
        }
        return;
      }

      // Check if file starts with HTML (likely login page)
      final fileStart = String.fromCharCodes(
        torrentBytes.take(100),
      ).toLowerCase();
      if (fileStart.contains('<!doctype') ||
          fileStart.contains('<html') ||
          fileStart.contains('login.php') ||
          fileStart.contains('авторизация')) {
        // This is HTML, not a torrent file - likely requires authentication
        try {
          await torrentFile.delete();
        } on Exception {
          // Ignore deletion errors
        }
        if (mounted) {
          AuthErrorHandler.showAuthErrorSnackBar(
            context,
            const AuthFailure.loginRequired(),
          );
        }
        return;
      }

      // Start download through AudiobookTorrentManager
      final downloadDir = await AudiobookTorrentManager.getDownloadDirectory();

      // Get audiobook title for better display in downloads list
      final audiobookTitle = _audiobook?['title'] as String? ?? '';

      // Logging start of download
      final logger = EnvironmentLogger()
        ..i('Starting torrent download for topic ${widget.topicId}')
        ..i('Torrent URL: $torrentUrl')
        ..i('Starting download from torrent file: ${torrentFile.path}');

      final torrentManager =
          await ref.read(audiobookTorrentManagerProvider.future);
      final downloadId = await torrentManager.downloadFromTorrentFile(
        torrentFile.path,
        '$downloadDir/${widget.topicId}',
        title: audiobookTitle.isNotEmpty ? audiobookTitle : null,
      );

      logger.i('Download started successfully with ID: $downloadId');

      // Set up progress tracking
      _activeDownloadId = downloadId;
      await _downloadProgressSubscription?.cancel();

      // Set initial progress state
      if (mounted) {
        setState(() {
          _currentProgress = TorrentProgress(
            progress: 0.0,
            downloadSpeed: 0.0,
            uploadSpeed: 0.0,
            downloadedBytes: 0,
            totalBytes: 0,
            seeders: 0,
            leechers: 0,
            status: 'downloading_metadata',
          );
        });
      }

      // Subscribe to progress with error handling
      try {
        _downloadProgressSubscription =
            torrentManager.getProgressStream(downloadId).listen(
          (progress) {
            if (mounted) {
              setState(() {
                _currentProgress = progress;
              });
            }
          },
          onError: (error) {
            logger.e('Error in download progress stream: $error');
            if (mounted) {
              setState(() {
                _currentProgress = TorrentProgress(
                  progress: _currentProgress?.progress ?? 0.0,
                  downloadSpeed: 0.0,
                  uploadSpeed: 0.0,
                  downloadedBytes: _currentProgress?.downloadedBytes ?? 0,
                  totalBytes: _currentProgress?.totalBytes ?? 0,
                  seeders: 0,
                  leechers: 0,
                  status: 'error: $error',
                );
              });
            }
          },
        );
      } on Exception catch (e) {
        logger.e('Failed to subscribe to download progress: $e');
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                '${AppLocalizations.of(context)?.failedToStartDownload ?? 'Failed to start download'}: Failed to track download progress: ${e.toString()}',
              ),
            ),
          );
        }
      }

      // Delete temporary torrent file after starting download
      try {
        await torrentFile.delete();
      } on Exception {
        // Ignore deletion errors
      }

      // Show notification
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              AppLocalizations.of(context)?.downloadStarted ??
                  'Download started',
            ),
          ),
        );
      }
    } on Exception catch (e) {
      EnvironmentLogger().e('Failed to start download: $e');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              '${AppLocalizations.of(context)?.failedToStartDownload ?? 'Failed to start download'}: ${e.toString()}',
            ),
          ),
        );
      }
    }
  }

  Future<void> _startMagnetDownload() async {
    if (_audiobook == null) return;
    final magnetUrl = _audiobook!['magnetUrl'] as String? ?? '';
    if (magnetUrl.isEmpty) return;

    try {
      final downloadDir = await AudiobookTorrentManager.getDownloadDirectory();

      // Get audiobook title for better display in downloads list
      final audiobookTitle = _audiobook?['title'] as String? ?? '';

      // Logging start of download
      final logger = EnvironmentLogger()
        ..i('Starting magnet download for topic ${widget.topicId}')
        ..i('Magnet URL: $magnetUrl');

      final torrentManager =
          await ref.read(audiobookTorrentManagerProvider.future);
      final downloadId = await torrentManager.downloadSequential(
        magnetUrl,
        '$downloadDir/${widget.topicId}',
        title: audiobookTitle.isNotEmpty ? audiobookTitle : null,
      );

      logger.i('Download started successfully with ID: $downloadId');

      // Set up progress tracking
      _activeDownloadId = downloadId;
      await _downloadProgressSubscription?.cancel();

      // Set initial progress state
      if (mounted) {
        setState(() {
          _currentProgress = TorrentProgress(
            progress: 0.0,
            downloadSpeed: 0.0,
            uploadSpeed: 0.0,
            downloadedBytes: 0,
            totalBytes: 0,
            seeders: 0,
            leechers: 0,
            status: 'downloading_metadata',
          );
        });
      }

      // Subscribe to progress with error handling
      try {
        _downloadProgressSubscription =
            torrentManager.getProgressStream(downloadId).listen(
          (progress) {
            if (mounted) {
              setState(() {
                _currentProgress = progress;
              });
            }
          },
          onError: (error) {
            logger.e('Error in download progress stream: $error');
            if (mounted) {
              setState(() {
                _currentProgress = TorrentProgress(
                  progress: _currentProgress?.progress ?? 0.0,
                  downloadSpeed: 0.0,
                  uploadSpeed: 0.0,
                  downloadedBytes: _currentProgress?.downloadedBytes ?? 0,
                  totalBytes: _currentProgress?.totalBytes ?? 0,
                  seeders: 0,
                  leechers: 0,
                  status: 'error: $error',
                );
              });
            }
          },
        );
      } on Exception catch (e) {
        logger.e('Failed to subscribe to download progress: $e');
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                '${AppLocalizations.of(context)?.failedToStartDownload ?? 'Failed to start download'}: Failed to track download progress: ${e.toString()}',
              ),
            ),
          );
        }
      }

      // Show notification
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              AppLocalizations.of(context)?.downloadStarted ??
                  'Download started',
            ),
          ),
        );
      }
    } on Exception catch (e) {
      EnvironmentLogger().e('Failed to start magnet download: $e');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              '${AppLocalizations.of(context)?.failedToStartDownload ?? 'Failed to start download'}: ${e.toString()}',
            ),
          ),
        );
      }
    }
  }

  /// Refreshes chapters from torrent file, bypassing cache.
  ///
  /// This method forces re-parsing of the torrent file to get updated chapter information.
  Future<void> _refreshChaptersFromTorrent() async {
    if (_audiobook == null) return;

    try {
      setState(() {
        _isLoading = true;
      });

      final endpointManager = ref.read(endpointManagerProvider);
      final activeEndpoint = await endpointManager.getActiveEndpoint();

      // Extract chapters from torrent with force refresh
      final chaptersFromTorrent = await _extractChaptersFromTorrent(
        widget.topicId,
        activeEndpoint,
        forceRefresh: true,
      );

      if (chaptersFromTorrent.isNotEmpty && mounted) {
        // Update audiobook with new chapters
        final updatedAudiobook = Map<String, dynamic>.from(_audiobook!);
        updatedAudiobook['chapters'] = chaptersFromTorrent
            .map((c) => {
                  'title': c.title,
                  'duration_ms': c.durationMs,
                  'file_index': c.fileIndex,
                  'start_byte': c.startByte,
                  'end_byte': c.endByte,
                })
            .toList();

        // Update cache with new chapters
        await _cacheService.cacheTopicDetails(widget.topicId, updatedAudiobook);

        setState(() {
          _audiobook = updatedAudiobook;
          _isLoading = false;
        });

        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                AppLocalizations.of(context)?.chaptersRefreshed ??
                    'Chapters refreshed from torrent',
              ),
            ),
          );
        }
      } else if (mounted) {
        setState(() {
          _isLoading = false;
        });
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              AppLocalizations.of(context)?.noChaptersFound ??
                  'No chapters found in torrent',
            ),
          ),
        );
      }
    } on AuthFailure catch (e) {
      // Handle authentication errors
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
        AuthErrorHandler.showAuthErrorSnackBar(context, e);
      }
    } on Exception catch (e) {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              '${AppLocalizations.of(context)?.failedToRefreshChapters ?? 'Failed to refresh chapters'}: ${e.toString()}',
            ),
          ),
        );
      }
    }
  }

  /// Extracts chapters from torrent file as fallback when HTML parsing fails.
  ///
  /// Downloads the torrent file and extracts audio file names as chapters.
  /// The [forceRefresh] parameter, if true, bypasses cache and forces re-parsing.
  ///
  /// Throws [AuthFailure] if authentication is required to download the torrent file.
  Future<List<Chapter>> _extractChaptersFromTorrent(
      String topicId, String endpoint,
      {bool forceRefresh = false}) async {
    try {
      // Download torrent file
      final tempDir = await getTemporaryDirectory();
      final torrentFile = File('${tempDir.path}/torrent_$topicId.torrent');

      final dio = await DioClient.instance;
      try {
        await dio.download(
          '$endpoint/forum/dl.php?t=$topicId',
          torrentFile.path,
        );
      } on DioException catch (e) {
        // Check if error is authentication-related
        if (e.response?.statusCode == 401 ||
            e.response?.statusCode == 403 ||
            (e.message?.toLowerCase().contains('authentication') ?? false) ||
            (e.response?.realUri.toString().contains('login.php') ?? false)) {
          // Delete temporary file if it exists
          try {
            if (await torrentFile.exists()) {
              await torrentFile.delete();
            }
          } on Exception {
            // Ignore deletion errors
          }
          throw const AuthFailure.loginRequired();
        }
        // Re-throw other DioExceptions
        rethrow;
      }

      // Check if downloaded file is actually a torrent file
      // Sometimes RuTracker returns HTML login page instead of torrent file
      final torrentBytes = await torrentFile.readAsBytes();

      // Check if file is too small (likely not a torrent) or starts with HTML
      if (torrentBytes.length < 100) {
        // Too small to be a valid torrent file
        try {
          await torrentFile.delete();
        } on Exception {
          // Ignore deletion errors
        }
        throw const AuthFailure(
            'Downloaded file is too small, may require authentication');
      }

      // Check if file starts with HTML (likely login page)
      final fileStart = String.fromCharCodes(
        torrentBytes.take(100),
      ).toLowerCase();
      if (fileStart.contains('<!doctype') ||
          fileStart.contains('<html') ||
          fileStart.contains('login.php') ||
          fileStart.contains('авторизация')) {
        // This is HTML, not a torrent file - likely requires authentication
        try {
          await torrentFile.delete();
        } on Exception {
          // Ignore deletion errors
        }
        throw const AuthFailure.loginRequired();
      }

      // Parse chapters from torrent
      final parserService = TorrentParserService();
      final chapters = await parserService.extractChaptersFromTorrent(
        torrentBytes,
        forceRefresh: forceRefresh,
      );

      // Delete temporary torrent file
      try {
        await torrentFile.delete();
      } on Exception {
        // Ignore deletion errors
      }

      return chapters;
    } on AuthFailure {
      // Re-throw authentication failures
      rethrow;
    } on Exception {
      // Return empty list for other errors (non-critical)
      return [];
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
      SnackBar(
          content: Text(
              AppLocalizations.of(context)!.copyToClipboardMessage(label))),
    );
  }

  /// Builds a chip with constrained width and tooltip.
  Widget _buildChip(String label, Color backgroundColor, {Color? labelColor}) {
    final maxWidth = MediaQuery.of(context).size.width * 0.4;

    return Tooltip(
      message: label,
      child: ConstrainedBox(
        constraints: BoxConstraints(maxWidth: maxWidth),
        child: Chip(
          label: Text(
            label,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: labelColor != null ? TextStyle(color: labelColor) : null,
          ),
          backgroundColor: backgroundColor,
        ),
      ),
    );
  }
}

/// Widget for displaying expandable title with show more/less functionality.
class _ExpandableTitle extends StatefulWidget {
  const _ExpandableTitle({
    required this.title,
    this.style,
    // ignore: unused_element_parameter
    this.maxLinesCollapsed = 2,
  });

  final String title;
  final TextStyle? style;
  final int maxLinesCollapsed;

  @override
  State<_ExpandableTitle> createState() => _ExpandableTitleState();
}

class _ExpandableTitleState extends State<_ExpandableTitle> {
  bool _isExpanded = false;

  @override
  Widget build(BuildContext context) {
    final textStyle = widget.style ?? Theme.of(context).textTheme.headlineSmall;
    final shouldShowButton = widget.title.length > 100;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        AnimatedCrossFade(
          firstChild: Text(
            widget.title,
            style: textStyle,
            maxLines: widget.maxLinesCollapsed,
            overflow: TextOverflow.ellipsis,
          ),
          secondChild: SelectableText(
            widget.title,
            style: textStyle,
          ),
          crossFadeState: _isExpanded
              ? CrossFadeState.showSecond
              : CrossFadeState.showFirst,
          duration: const Duration(milliseconds: 200),
        ),
        if (shouldShowButton)
          TextButton.icon(
            onPressed: () {
              setState(() {
                _isExpanded = !_isExpanded;
              });
            },
            icon: Icon(_isExpanded ? Icons.expand_less : Icons.expand_more),
            label: Text(
              _isExpanded
                  ? AppLocalizations.of(context)?.showLess ?? 'Show less'
                  : AppLocalizations.of(context)?.showMore ?? 'Show more',
            ),
            style: TextButton.styleFrom(
              padding: const EdgeInsets.only(top: 4),
              minimumSize: Size.zero,
              tapTargetSize: MaterialTapTargetSize.shrinkWrap,
            ),
          ),
      ],
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
      'performer': audiobook.performer,
      'genres': audiobook.genres,
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
          onPressed: () async {
            Navigator.pop(ctx);
            // Navigate to auth screen
            final result = await context.push('/auth');
            // If login was successful, validate cookies
            if (result == true) {
              final isValid = await DioClient.validateCookies();
              if (isValid && context.mounted) {
                // Retry loading topic - this will be handled by the parent widget
                // The widget will automatically reload when auth status changes
              }
            }
          },
          child: Text(AppLocalizations.of(context)!.login),
        ),
      ],
    ),
  );
}
