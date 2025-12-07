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

import 'package:cached_network_image/cached_network_image.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/core/di/providers/database_providers.dart';
import 'package:jabook/core/favorites/favorites_provider.dart';
import 'package:jabook/core/infrastructure/endpoints/endpoint_manager.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/parse/rutracker_parser.dart';
import 'package:jabook/core/player/player_state_provider.dart';
import 'package:jabook/core/stream/local_stream_server.dart';
import 'package:jabook/core/utils/app_title_utils.dart';
import 'package:jabook/core/utils/responsive_utils.dart';
import 'package:jabook/features/player/presentation/widgets/chapters_bottom_sheet.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Main audiobook player screen.
///
/// This screen provides the user interface for playing audiobooks,
/// including playback controls, progress tracking, and chapter navigation.
class PlayerScreen extends ConsumerStatefulWidget {
  /// Creates a new PlayerScreen instance.
  ///
  /// The [bookId] parameter is required to identify which audiobook
  /// should be displayed and played.
  const PlayerScreen({super.key, required this.bookId});

  /// The unique identifier of the audiobook to play.
  final String bookId;

  @override
  ConsumerState<PlayerScreen> createState() => _PlayerScreenState();
}

class _PlayerScreenState extends ConsumerState<PlayerScreen> {
  final LocalStreamServer _streamServer = LocalStreamServer();

  Audiobook? _audiobook;
  bool _isInitialized = false;
  bool _hasError = false;
  String? _errorMessage;
  // Local state for slider during dragging
  double? _sliderValue;
  bool _isDragging = false;

  @override
  void initState() {
    super.initState();
    // Start stream server first (non-blocking)
    _startStreamServer();
    // Initialize player asynchronously
    _initializePlayer();
  }

  @override
  void dispose() {
    _streamServer.stop();
    super.dispose();
  }

  Future<void> _initializePlayer() async {
    try {
      final playerNotifier = ref.read(playerStateProvider.notifier);
      final currentState = ref.read(playerStateProvider);

      // Check if player is already initialized and playing the same book
      // If yes, skip reinitialization to avoid interrupting playback
      if (currentState.currentGroupPath == widget.bookId &&
          currentState.playbackState != 0) {
        // Player is already playing this book - load audiobook data for UI
        // Wait for it to load so UI can display chapters
        await _loadAudiobookFromRutracker();
        // Mark as initialized after audiobook is loaded
        setState(() {
          _isInitialized = true;
          _hasError = false;
          _errorMessage = null;
        });
        return;
      }

      // Mark as loading immediately to show UI
      setState(() {
        _isInitialized = false;
        _hasError = false;
        _errorMessage = null;
      });

      // Initialize player service (only if not already initialized)
      if (currentState.playbackState == 0) {
        await playerNotifier.initialize();
      }

      // Load audiobook from RuTracker and restore position in parallel
      final results = await Future.wait([
        _loadAudiobookFromRutracker(),
        playerNotifier.restorePosition(widget.bookId),
      ]);
      final savedPosition = results[1] as Map<String, int>?;

      // Ensure audiobook was loaded
      if (_audiobook == null) {
        throw Exception('Failed to load audiobook data');
      }

      // Validate saved position if available
      // Note: restorePosition already validates, but we do additional checks here
      if (savedPosition != null) {
        final trackIndex = savedPosition['trackIndex'];
        final positionMs = savedPosition['positionMs'];
        if (trackIndex == null ||
            positionMs == null ||
            trackIndex < 0 ||
            trackIndex >= (_audiobook?.chapters.length ?? 0) ||
            positionMs < 0) {
          // Invalid saved position - will start from beginning
          // restorePosition should have cleared it, but we handle it here too
        }
      }

      // Ensure stream server is running before loading audio
      if (!_streamServer.isRunning) {
        await _streamServer.start();
      }

      // Load audio sources
      await _loadAudioSource();

      // Update metadata immediately (non-blocking for UI)
      _updateMetadata();

      // Mark as initialized to show UI
      setState(() {
        _isInitialized = true;
        _hasError = false;
        _errorMessage = null;
      });

      // Restore position and start playback in background (non-blocking)
      if (savedPosition != null && mounted) {
        unawaited(_restorePositionAndPlay(savedPosition));
      } else {
        // If no saved position, start playback from beginning
        unawaited(
          Future.delayed(const Duration(milliseconds: 300), () {
            if (mounted) {
              playerNotifier.play();
            }
          }),
        );
      }
    } on Exception catch (e) {
      setState(() {
        _isInitialized = true;
        _hasError = true;
        _errorMessage = e.toString();
      });
    }
  }

  /// Restores position and starts playback asynchronously (non-blocking).
  Future<void> _restorePositionAndPlay(Map<String, int> savedPosition) async {
    if (!mounted) return;

    final playerNotifier = ref.read(playerStateProvider.notifier);
    final trackIndex = savedPosition['trackIndex']!;
    final positionMs = savedPosition['positionMs']!;

    if (trackIndex >= 0 &&
        trackIndex < (_audiobook?.chapters.length ?? 0) &&
        positionMs > 0) {
      // Wait for player to be ready (with timeout)
      var attempts = 0;
      while (attempts < 30 && mounted) {
        final state = ref.read(playerStateProvider);
        if (state.playbackState == 2) {
          // 2 = ready
          break;
        }
        await Future.delayed(const Duration(milliseconds: 100));
        attempts++;
      }

      if (mounted) {
        try {
          await playerNotifier.seekToTrackAndPosition(
            trackIndex,
            Duration(milliseconds: positionMs),
          );

          // Start playback automatically
          final currentState = ref.read(playerStateProvider);
          if (!currentState.isPlaying) {
            await playerNotifier.play();
          }
        } on Exception {
          // If restore fails, start from beginning
          if (mounted) {
            unawaited(playerNotifier.play());
          }
        }
      }
    } else {
      // Invalid saved position, start from beginning
      if (mounted) {
        unawaited(playerNotifier.play());
      }
    }
  }

  Future<void> _loadAudiobookFromRutracker() async {
    try {
      final appDatabase = ref.read(appDatabaseProvider);
      final db = await appDatabase.ensureInitialized();
      final endpointManager = EndpointManager(db);
      final base = await endpointManager.getActiveEndpoint();
      final dio = await DioClient.instance;
      final response = await dio.get('$base/forum/viewtopic.php',
          queryParameters: {'t': widget.bookId},
          options: Options(
            responseType:
                ResponseType.plain, // Ensure gzip is automatically decompressed
          ));
      if (response.statusCode == 200) {
        final parsed = await RuTrackerParser().parseTopicDetails(
          response.data,
          baseUrl: base,
        );
        if (parsed != null) {
          setState(() {
            _audiobook = parsed;
          });
          // Log audiobook loaded
          unawaited(StructuredLogger().log(
            level: 'info',
            subsystem: 'player',
            message: 'Audiobook loaded successfully',
            extra: {
              'bookId': widget.bookId,
              'title': parsed.title,
              'chaptersCount': parsed.chapters.length,
              'hasChapters': parsed.chapters.isNotEmpty,
              'firstChapterTitle': parsed.chapters.isNotEmpty
                  ? parsed.chapters.first.title
                  : null,
            },
          ));
        } else {
          unawaited(StructuredLogger().log(
            level: 'warning',
            subsystem: 'player',
            message: 'Failed to parse audiobook',
            extra: {
              'bookId': widget.bookId,
              'responseStatusCode': response.statusCode,
            },
          ));
        }
      }
    } on Exception {
      // ignore and keep error handling by caller
    }
  }

  Future<void> _loadAudioSource() async {
    if (_audiobook == null || _audiobook!.chapters.isEmpty) {
      // Fallback to demo audio if no chapters available
      final playerNotifier = ref.read(playerStateProvider.notifier);
      await playerNotifier.setPlaylist(
        ['https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3'],
        metadata: {
          'title': 'Demo Audio',
          'artist': 'SoundHelix',
        },
        groupPath: widget.bookId,
      );
      return;
    }

    try {
      // Check if files are downloaded locally before using LocalStreamServer
      final hasFiles = await _streamServer.hasFiles(widget.bookId);
      if (!hasFiles) {
        await StructuredLogger().log(
          level: 'warning',
          subsystem: 'player',
          message: 'Files not found locally for RuTracker audiobook',
          extra: {
            'bookId': widget.bookId,
            'title': _audiobook!.title,
          },
        );

        // Show error message to user
        if (mounted) {
          setState(() {
            _hasError = true;
            _errorMessage =
                'Files are not downloaded. Please download the audiobook first to play it.';
          });
        }
        return;
      }

      // Ensure stream server is running
      if (!_streamServer.isRunning) {
        await _streamServer.start();
      }

      // Create URLs for all chapters
      final streamUrls = _audiobook!.chapters
          .map((chapter) =>
              _streamServer.getStreamUrl(widget.bookId, chapter.fileIndex))
          .toList();

      // Prepare metadata
      Uri? art;
      try {
        // ignore: deprecated_member_use_from_same_package
        final coverUrl = (_audiobook as dynamic).coverUrl as String?;
        if (coverUrl != null && coverUrl.isNotEmpty) {
          art = Uri.parse(coverUrl);
        }
      } on Exception catch (_) {}

      final metadata = <String, String>{
        'title': _audiobook!.title,
        'artist': _audiobook!.author,
        'album': _audiobook!.category,
        if (art != null) 'artworkUri': art.toString(),
      };

      // Set playlist with all chapters
      final playerNotifier = ref.read(playerStateProvider.notifier);
      await playerNotifier.setPlaylist(
        streamUrls,
        metadata: metadata,
        groupPath: widget.bookId,
      );
    } on StreamFailure catch (e) {
      // Log the specific stream failure
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'player',
        message: 'Failed to load audio sources from local stream server',
        cause: e.toString(),
        extra: {'bookId': widget.bookId},
      );

      // Show error message to user
      if (mounted) {
        setState(() {
          _hasError = true;
          _errorMessage =
              'Failed to load audio files. Please check if files are downloaded.';
        });
      }
    } on Exception catch (e) {
      // Log error for debugging
      await StructuredLogger().log(
        level: 'error',
        subsystem: 'player',
        message: 'Failed to load audio source',
        cause: e.toString(),
        extra: {'bookId': widget.bookId},
      );

      // Show error message to user
      if (mounted) {
        setState(() {
          _hasError = true;
          _errorMessage = 'Failed to load audio. Please try again.';
        });
      }
    }
  }

  Future<void> _startStreamServer() async {
    try {
      await _streamServer.start();
    } on Exception catch (_) {
      // Handle server start error
    }
  }

  void _playPause() {
    final playerNotifier = ref.read(playerStateProvider.notifier);
    final state = ref.read(playerStateProvider);
    if (state.isPlaying) {
      playerNotifier.pause();
    } else {
      playerNotifier.play();
    }
  }

  /// Called when user starts dragging the slider.
  void _onSliderStart(double value) {
    setState(() {
      _isDragging = true;
      _sliderValue = value;
    });
  }

  /// Called while user is dragging the slider.
  void _onSliderChanged(double value) {
    setState(() {
      _sliderValue = value.clamp(0.0, 1.0);
    });
  }

  /// Called when user finishes dragging the slider.
  void _onSliderEnd(double value) {
    final state = ref.read(playerStateProvider);
    final positionMs =
        (value * state.duration).round().clamp(0, state.duration);
    final position = Duration(milliseconds: positionMs);

    ref.read(playerStateProvider.notifier).seek(position);

    // Reset local state
    setState(() {
      _isDragging = false;
      _sliderValue = null;
    });
  }

  void _seekToChapter(Chapter chapter) {
    if (_audiobook == null) return;

    final playerNotifier = ref.read(playerStateProvider.notifier);

    // For playlist: seek to chapter index
    if (_audiobook!.chapters.length > 1) {
      final chapterIndex = _audiobook!.chapters.indexOf(chapter);
      if (chapterIndex >= 0) {
        playerNotifier
          ..seekToTrack(chapterIndex)
          ..seek(Duration.zero);
      }
    } else {
      // For single file: seek to chapter start position
      final target = Duration(
          milliseconds: chapter.startByte > 0 ? 0 : (chapter.durationMs ~/ 2));
      playerNotifier.seek(target);
    }

    // Update metadata with chapter info
    Uri? art;
    try {
      // ignore: deprecated_member_use_from_same_package
      final coverUrl = (_audiobook as dynamic).coverUrl as String?;
      if (coverUrl != null && coverUrl.isNotEmpty) {
        art = Uri.parse(coverUrl);
      }
    } on Exception catch (_) {}

    playerNotifier.updateMetadata({
      'title': '${_audiobook!.title} — ${chapter.title}',
      'artist': _audiobook!.author,
      'album': _audiobook!.category,
      if (art != null) 'artworkUri': art.toString(),
    });
  }

  void _prevChapter() {
    final state = ref.read(playerStateProvider);
    if (state.currentIndex > 0) {
      ref.read(playerStateProvider.notifier).previous();
    }
  }

  void _nextChapter() {
    final state = ref.read(playerStateProvider);
    if (state.currentIndex < (_audiobook?.chapters.length ?? 0) - 1) {
      ref.read(playerStateProvider.notifier).next();
    }
  }

  /// Shows chapters bottom sheet for navigation.
  void _showChaptersBottomSheet() {
    if (_audiobook == null || _audiobook!.chapters.isEmpty) {
      StructuredLogger().log(
        level: 'warning',
        subsystem: 'player',
        message:
            'Cannot show chapters bottom sheet: audiobook is null or has no chapters',
        extra: {
          'audiobookNull': _audiobook == null,
          'chaptersEmpty': _audiobook?.chapters.isEmpty ?? true,
        },
      );
      return;
    }

    StructuredLogger().log(
      level: 'info',
      subsystem: 'player',
      message: 'Showing chapters bottom sheet',
      extra: {
        'chaptersCount': _audiobook!.chapters.length,
      },
    );

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => DraggableScrollableSheet(
        initialChildSize: 0.9,
        minChildSize: 0.5,
        maxChildSize: 0.95,
        builder: (context, scrollController) => ChaptersBottomSheet(
          audiobook: _audiobook!,
          onChapterSelected: _seekToChapter,
          scrollController: scrollController,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
          automaticallyImplyLeading: false,
          title: Text(
              '${(AppLocalizations.of(context)?.playerTitle ?? 'Player').withFlavorSuffix()}: ${widget.bookId}'),
          actions: [
            _buildFavoriteButton(),
            IconButton(
              icon: const Icon(Icons.download),
              onPressed: _downloadAudiobook,
            ),
          ],
        ),
        body: _buildBody(),
      );

  /// Builds favorite button for AppBar.
  Widget _buildFavoriteButton() {
    final favoriteIds = ref.watch(favoriteIdsProvider);
    final isFavorite = favoriteIds.contains(widget.bookId);

    return IconButton(
      icon: Icon(
        isFavorite ? Icons.favorite : Icons.favorite_border,
        color: isFavorite ? Colors.red : null,
      ),
      tooltip: isFavorite
          ? (AppLocalizations.of(context)?.favoritesTooltip ?? 'Favorites')
          : (AppLocalizations.of(context)?.favoritesTooltip ?? 'Favorites'),
      onPressed: _toggleFavorite,
    );
  }

  /// Toggles favorite status for current audiobook.
  Future<void> _toggleFavorite() async {
    if (_audiobook == null) return;

    final notifier = ref.read(favoriteIdsProvider.notifier);
    final favoriteIds = ref.read(favoriteIdsProvider);
    final isCurrentlyFavorite = favoriteIds.contains(widget.bookId);

    // Convert Audiobook to Map format
    final audiobookMap = {
      'id': _audiobook!.id,
      'title': _audiobook!.title,
      'author': _audiobook!.author,
      'category': _audiobook!.category,
      'size': _audiobook!.size,
      'seeders': _audiobook!.seeders,
      'leechers': _audiobook!.leechers,
      'magnetUrl': _audiobook!.magnetUrl,
      'coverUrl': _audiobook!.coverUrl,
      'performer': _audiobook!.performer,
      'genres': _audiobook!.genres,
      'addedDate': _audiobook!.addedDate.toIso8601String(),
      'chapters': _audiobook!.chapters
          .map((c) => {
                'title': c.title,
                'durationMs': c.durationMs,
                'fileIndex': c.fileIndex,
                'startByte': c.startByte,
                'endByte': c.endByte,
              })
          .toList(),
      'duration': _audiobook!.duration,
      'bitrate': _audiobook!.bitrate,
      'audioCodec': _audiobook!.audioCodec,
    };

    try {
      final wasAdded = await notifier.toggleFavorite(
        widget.bookId,
        audiobookMap: audiobookMap,
      );

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              wasAdded
                  ? (AppLocalizations.of(context)?.addedToFavorites ??
                      'Added to favorites')
                  : (AppLocalizations.of(context)?.removedFromFavorites ??
                      'Removed from favorites'),
            ),
            duration: const Duration(seconds: 2),
          ),
        );
      }
    } on Exception {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              isCurrentlyFavorite
                  ? (AppLocalizations.of(context)
                          ?.failedToRemoveFromFavorites ??
                      'Failed to remove from favorites')
                  : (AppLocalizations.of(context)?.failedToAddToFavorites ??
                      'Failed to add to favorites'),
            ),
          ),
        );
      }
    }
  }

  Widget _buildBody() {
    final playerState = ref.watch(playerStateProvider);
    final isLoading =
        !_isInitialized || playerState.playbackState == 1; // 1 = buffering
    final hasError = !isLoading && (_hasError || playerState.error != null);
    final errorMessage = _errorMessage ?? playerState.error;

    if (isLoading) {
      return const Center(
        child: CircularProgressIndicator(),
      );
    }

    if (hasError || _audiobook == null) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text(AppLocalizations.of(context)?.failedToLoadAudio ??
                'Failed to load audiobook'),
            if (errorMessage != null) ...[
              const SizedBox(height: 8),
              Text(
                errorMessage,
                style: Theme.of(context).textTheme.bodyMedium,
                textAlign: TextAlign.center,
              ),
            ],
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: _initializePlayer,
              child: Text(AppLocalizations.of(context)?.retry ?? 'Retry'),
            ),
          ],
        ),
      );
    }

    // Get bottom padding for system navigation bar
    final mediaQuery = MediaQuery.of(context);
    final bottomPadding = mediaQuery.padding.bottom;

    // Debug logging
    unawaited(StructuredLogger().log(
      level: 'info',
      subsystem: 'player',
      message: 'Building player body',
      extra: {
        'audiobookNull': _audiobook == null,
        'chaptersCount': _audiobook?.chapters.length ?? 0,
        'chaptersEmpty': _audiobook?.chapters.isEmpty ?? true,
        'currentIndex': playerState.currentIndex,
        'bottomPadding': bottomPadding,
        'isInitialized': _isInitialized,
        'hasError': _hasError,
      },
    ));

    return SafeArea(
      child: SingleChildScrollView(
        child: Padding(
          padding: EdgeInsets.only(
            bottom: bottomPadding > 0 ? bottomPadding + 24 : 32,
          ),
          child: Column(
            children: [
              // Audiobook info
              Padding(
                padding: const EdgeInsets.all(16.0),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Cover image if available
                    Builder(builder: (context) {
                      try {
                        // ignore: deprecated_member_use_from_same_package
                        final coverUrl =
                            (_audiobook as dynamic).coverUrl as String?;
                        if (coverUrl != null && coverUrl.isNotEmpty) {
                          return RepaintBoundary(
                            child: ClipRRect(
                              borderRadius: BorderRadius.circular(8),
                              child: CachedNetworkImage(
                                imageUrl: coverUrl,
                                width: 96,
                                height: 96,
                                fit: BoxFit.cover,
                                placeholder: (context, url) => Container(
                                  width: 96,
                                  height: 96,
                                  color: Theme.of(context)
                                      .colorScheme
                                      .surfaceContainerHighest,
                                  child: const Center(
                                    child: SizedBox(
                                      width: 24,
                                      height: 24,
                                      child: CircularProgressIndicator(
                                          strokeWidth: 2),
                                    ),
                                  ),
                                ),
                                errorWidget: (context, url, error) => Container(
                                  width: 96,
                                  height: 96,
                                  color: Theme.of(context)
                                      .colorScheme
                                      .errorContainer,
                                  child: Icon(
                                    Icons.error_outline,
                                    color: Theme.of(context)
                                        .colorScheme
                                        .onErrorContainer,
                                  ),
                                ),
                              ),
                            ),
                          );
                        }
                      } on Exception catch (_) {}
                      return const SizedBox(width: 0, height: 0);
                    }),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            _audiobook!.title,
                            style: Theme.of(context).textTheme.headlineSmall,
                          ),
                          const SizedBox(height: 8),
                          Text(
                            'by ${_audiobook!.author}',
                            style: Theme.of(context).textTheme.titleMedium,
                          ),
                          const SizedBox(height: 16),
                          Row(
                            children: [
                              Chip(
                                label: Text(_audiobook!.category),
                                backgroundColor: Colors.blue.shade100,
                              ),
                              const SizedBox(width: 8),
                              Chip(
                                label: Text(_audiobook!.size),
                                backgroundColor: Colors.green.shade100,
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),

              // Progress bar
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16.0),
                child: Column(
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(_formatDuration(Duration(
                            milliseconds: playerState.currentPosition))),
                        Text(_formatDuration(
                            Duration(milliseconds: playerState.duration))),
                      ],
                    ),
                    const SizedBox(height: 8),
                    Slider(
                      value: _isDragging && _sliderValue != null
                          ? _sliderValue!.clamp(0.0, 1.0)
                          : (playerState.duration > 0
                              ? playerState.currentPosition /
                                  playerState.duration
                              : 0.0),
                      onChanged:
                          playerState.duration > 0 ? _onSliderChanged : null,
                      onChangeStart:
                          playerState.duration > 0 ? _onSliderStart : null,
                      onChangeEnd:
                          playerState.duration > 0 ? _onSliderEnd : null,
                    ),
                  ],
                ),
              ),

              // Current chapter indicator - shows current chapter and allows quick navigation
              if (_audiobook != null &&
                  _audiobook!.chapters.isNotEmpty &&
                  playerState.currentIndex >= 0 &&
                  playerState.currentIndex < _audiobook!.chapters.length)
                Padding(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 16.0, vertical: 8.0),
                  child: Card(
                    elevation: 2,
                    child: InkWell(
                      onTap: _showChaptersBottomSheet,
                      borderRadius: BorderRadius.circular(12),
                      child: Padding(
                        padding: const EdgeInsets.all(12.0),
                        child: Row(
                          children: [
                            Icon(
                              Icons.play_circle_filled,
                              color: Theme.of(context).primaryColor,
                              size: 24,
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    '${AppLocalizations.of(context)?.chaptersLabel ?? 'Chapter'} ${playerState.chapterNumberValue} / ${_audiobook!.chapters.length}',
                                    style:
                                        Theme.of(context).textTheme.bodySmall,
                                  ),
                                  const SizedBox(height: 4),
                                  Text(
                                    _audiobook!
                                        .chapters[playerState.currentIndex]
                                        .title,
                                    style: Theme.of(context)
                                        .textTheme
                                        .titleMedium
                                        ?.copyWith(
                                          fontWeight: FontWeight.bold,
                                          color: Theme.of(context).primaryColor,
                                        ),
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                ],
                              ),
                            ),
                            Icon(
                              Icons.chevron_right,
                              color: Theme.of(context).primaryColor,
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),

              // Player controls
              Padding(
                padding: EdgeInsets.only(
                  left: ResponsiveUtils.getCompactPadding(context).left,
                  right: ResponsiveUtils.getCompactPadding(context).right,
                  top: ResponsiveUtils.getCompactPadding(context).top,
                  bottom: ResponsiveUtils.getCompactPadding(context).bottom +
                      16.0, // Base padding
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    IconButton(
                      icon: const Icon(Icons.skip_previous),
                      onPressed:
                          playerState.currentIndex > 0 ? _prevChapter : null,
                      tooltip: _audiobook != null &&
                              playerState.currentIndex > 0 &&
                              playerState.currentIndex <=
                                  _audiobook!.chapters.length
                          ? _audiobook!
                              .chapters[playerState.currentIndex - 1].title
                          : 'Previous chapter',
                      iconSize: ResponsiveUtils.getIconSize(
                        context,
                        baseSize: ResponsiveUtils.isVerySmallScreen(context)
                            ? 40
                            : 48,
                      ),
                      constraints: BoxConstraints(
                        minWidth:
                            ResponsiveUtils.getMinTouchTarget(context) * 1.1,
                        minHeight:
                            ResponsiveUtils.getMinTouchTarget(context) * 1.1,
                      ),
                    ),
                    SizedBox(
                      width: ResponsiveUtils.getSpacing(
                        context,
                        baseSpacing: 32,
                      ),
                    ),
                    IconButton(
                      icon: Icon(playerState.isPlaying
                          ? Icons.pause
                          : Icons.play_arrow),
                      onPressed: _playPause,
                      iconSize: ResponsiveUtils.getIconSize(
                        context,
                        baseSize: ResponsiveUtils.isVerySmallScreen(context)
                            ? 56
                            : 64,
                      ),
                      constraints: BoxConstraints(
                        minWidth:
                            ResponsiveUtils.getMinTouchTarget(context) * 1.4,
                        minHeight:
                            ResponsiveUtils.getMinTouchTarget(context) * 1.4,
                      ),
                    ),
                    SizedBox(
                      width: ResponsiveUtils.getSpacing(
                        context,
                        baseSpacing: 32,
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.skip_next),
                      onPressed: _nextChapter,
                      tooltip: _audiobook != null &&
                              playerState.currentIndex >= 0 &&
                              playerState.currentIndex <
                                  _audiobook!.chapters.length - 1
                          ? _audiobook!
                              .chapters[playerState.currentIndex + 1].title
                          : 'Next chapter',
                      iconSize: ResponsiveUtils.getIconSize(
                        context,
                        baseSize: ResponsiveUtils.isVerySmallScreen(context)
                            ? 40
                            : 48,
                      ),
                      constraints: BoxConstraints(
                        minWidth:
                            ResponsiveUtils.getMinTouchTarget(context) * 1.1,
                        minHeight:
                            ResponsiveUtils.getMinTouchTarget(context) * 1.1,
                      ),
                    ),
                  ],
                ),
              ),

              // Nearby chapters carousel - shows current + 2 prev + 2 next for quick navigation
              if (_audiobook != null &&
                  _audiobook!.chapters.length > 1 &&
                  playerState.currentIndex >= 0 &&
                  playerState.currentIndex < _audiobook!.chapters.length)
                Padding(
                  padding: const EdgeInsets.only(top: 8.0, bottom: 8.0),
                  child: SizedBox(
                    height: 90,
                    child: ListView.builder(
                      scrollDirection: Axis.horizontal,
                      padding: const EdgeInsets.symmetric(horizontal: 16.0),
                      itemCount: _audiobook!.chapters.length,
                      itemBuilder: (context, index) {
                        final chapter = _audiobook!.chapters[index];
                        final isCurrent = index == playerState.currentIndex;
                        final distance =
                            (index - playerState.currentIndex).abs();

                        // Show only current + 2 prev + 2 next (total 5 chapters)
                        if (distance > 2) return const SizedBox.shrink();

                        return Padding(
                          padding: const EdgeInsets.only(right: 8.0),
                          child: GestureDetector(
                            onTap: () => _seekToChapter(chapter),
                            child: Container(
                              width: 130,
                              decoration: BoxDecoration(
                                color: isCurrent
                                    ? Theme.of(context)
                                        .primaryColor
                                        .withValues(alpha: 0.1)
                                    : Theme.of(context).cardColor,
                                borderRadius: BorderRadius.circular(12),
                                border: isCurrent
                                    ? Border.all(
                                        color: Theme.of(context).primaryColor,
                                        width: 2,
                                      )
                                    : Border.all(
                                        color: Theme.of(context).dividerColor,
                                      ),
                              ),
                              padding: const EdgeInsets.all(10.0),
                              child: Column(
                                mainAxisAlignment: MainAxisAlignment.center,
                                children: [
                                  Row(
                                    mainAxisAlignment: MainAxisAlignment.center,
                                    children: [
                                      if (isCurrent)
                                        Icon(
                                          Icons.play_circle_filled,
                                          size: 16,
                                          color: Theme.of(context).primaryColor,
                                        ),
                                      if (isCurrent) const SizedBox(width: 4),
                                      Text(
                                        '${index + 1}',
                                        style: Theme.of(context)
                                            .textTheme
                                            .titleSmall
                                            ?.copyWith(
                                              fontWeight: isCurrent
                                                  ? FontWeight.bold
                                                  : FontWeight.normal,
                                              color: isCurrent
                                                  ? Theme.of(context)
                                                      .primaryColor
                                                  : null,
                                            ),
                                      ),
                                    ],
                                  ),
                                  const SizedBox(height: 6),
                                  Expanded(
                                    child: Text(chapter.title,
                                        style: Theme.of(context)
                                            .textTheme
                                            .bodySmall,
                                        maxLines: 2,
                                        overflow: TextOverflow.ellipsis,
                                        textAlign: TextAlign.center),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        );
                      },
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  String _formatDuration(Duration duration) {
    final minutes = duration.inMinutes.remainder(60);
    final seconds = duration.inSeconds.remainder(60);
    return '$minutes:${seconds.toString().padLeft(2, '0')}';
  }

  void _downloadAudiobook() {
    // TODO: Implement download functionality
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
          content: Text(
              AppLocalizations.of(context)?.downloadFunctionalityComingSoon ??
                  'Download functionality coming soon!')),
    );
  }

  /// Updates metadata for current track.
  void _updateMetadata() {
    if (_audiobook == null) return;

    Uri? art;
    try {
      // ignore: deprecated_member_use_from_same_package
      final coverUrl = (_audiobook as dynamic).coverUrl as String?;
      if (coverUrl != null && coverUrl.isNotEmpty) {
        art = Uri.parse(coverUrl);
      }
    } on Exception catch (_) {}

    final metadata = <String, String>{
      'title': _audiobook!.title,
      'artist': _audiobook!.author,
      'album': _audiobook!.category,
      if (art != null) 'artworkUri': art.toString(),
    };

    ref.read(playerStateProvider.notifier).updateMetadata(metadata);
  }
}
