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

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/core/di/providers/database_providers.dart';
import 'package:jabook/core/di/providers/simple_player_providers.dart';
import 'package:jabook/core/favorites/favorites_provider.dart';
import 'package:jabook/core/infrastructure/endpoints/endpoint_manager.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:jabook/core/library/library_file_finder.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/parse/rutracker_parser.dart';
import 'package:jabook/core/stream/local_stream_server.dart';
import 'package:jabook/core/utils/app_title_utils.dart';
import 'package:jabook/features/player/presentation/widgets/chapter_indicator.dart';
import 'package:jabook/features/player/presentation/widgets/chapters_bottom_sheet.dart';
import 'package:jabook/features/player/presentation/widgets/nearby_chapters_carousel.dart';
import 'package:jabook/features/player/presentation/widgets/player_controls.dart';
import 'package:jabook/features/player/presentation/widgets/player_info.dart';
import 'package:jabook/features/player/presentation/widgets/player_progress.dart';
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
  // Sorted chapters by fileIndex - used for playlist creation and navigation
  // This ensures playlist index corresponds to fileIndex
  List<Chapter>? _sortedChapters;
  bool _isInitialized = false;
  bool _hasError = false;
  String? _errorMessage;
  // Local state for slider during dragging
  double? _sliderValue;
  final bool _isDragging = false;

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
      // Use new simplePlayerProvider for all operations
      final simplePlayerNotifier = ref.read(simplePlayerProvider.notifier);
      // Also get new provider for basic state
      final currentState = ref.read(simplePlayerProvider);

      // ALWAYS recreate playlist to ensure mapping is correct
      // (removed check for existing player state)

      // Mark as loading immediately to show UI
      setState(() {
        _isInitialized = false;
        _hasError = false;
        _errorMessage = null;
      });

      // Initialize player service (only if not already initialized)
      // Note: initialize() is not yet available in simplePlayerProvider,
      // Use new simplePlayerProvider for initialization
      if (currentState.playbackState == 0) {
        await simplePlayerNotifier.initialize();
      }

      // Load audiobook from RuTracker and restore position in parallel
      // Use new simplePlayerProvider for restorePosition
      final results = await Future.wait([
        _loadAudiobookFromRutracker(),
        simplePlayerNotifier.restorePosition(widget.bookId),
      ]);
      final savedPosition = results[1] as Map<String, int>?;

      // Ensure audiobook was loaded
      if (_audiobook == null) {
        throw Exception('Failed to load audiobook data');
      }

      // Validate saved position if available
      // Note: restorePosition already validates, but we do additional checks here
      // CRITICAL: After sorting chapters by fileIndex, we need to map saved trackIndex
      // to the new sorted position. The saved trackIndex was the playlist index,
      // which may have been based on unsorted chapters. We need to find the chapter
      // that was at that position and use its fileIndex to find the new position.
      int? initialTrackIndex;
      int? initialPositionMs;
      if (savedPosition != null && _audiobook != null) {
        final savedTrackIndex = savedPosition['trackIndex'];
        final positionMs = savedPosition['positionMs'];

        if (savedTrackIndex != null &&
            positionMs != null &&
            savedTrackIndex >= 0 &&
            savedTrackIndex < _audiobook!.chapters.length &&
            positionMs >= 0) {
          // Ensure sorted chapters are available
          if (_sortedChapters == null || _sortedChapters!.isEmpty) {
            _sortedChapters = List<Chapter>.from(_audiobook!.chapters)
              ..sort((a, b) => a.fileIndex.compareTo(b.fileIndex));
          }

          // Find the chapter that was at the saved track index
          // The saved trackIndex was the position in the old playlist
          // We need to find which chapter's fileIndex corresponds to that position
          if (savedTrackIndex < _sortedChapters!.length) {
            // Find chapter by matching fileIndex from old position
            // The old playlist may have been unsorted, so we need to find
            // the chapter that was at savedTrackIndex and map it to new sorted position
            final oldChapter = _audiobook!.chapters[savedTrackIndex];
            // Find this chapter's position in sorted list
            final newIndex = _sortedChapters!.indexWhere(
              (ch) => ch.fileIndex == oldChapter.fileIndex,
            );

            if (newIndex >= 0 && newIndex < _sortedChapters!.length) {
              initialTrackIndex = newIndex;
              initialPositionMs = positionMs;
            } else {
              // Fallback: use savedTrackIndex if chapter not found
              // This handles edge cases where chapter structure changed
              initialTrackIndex = savedTrackIndex < _sortedChapters!.length
                  ? savedTrackIndex
                  : null;
              initialPositionMs = positionMs;
            }
          }
        }
      }

      // Ensure stream server is running before loading audio
      if (!_streamServer.isRunning) {
        await _streamServer.start();
      }

      // Load audio sources with initial position
      await _loadAudioSource(
        initialTrackIndex: initialTrackIndex,
        initialPosition: initialPositionMs,
      );

      // Update metadata immediately (non-blocking for UI)
      _updateMetadata();

      // Mark as initialized to show UI
      setState(() {
        _isInitialized = true;
        _hasError = false;
        _errorMessage = null;
      });

      // Start playback in background (non-blocking)
      // Note: Position is already restored via initialTrackIndex/initialPosition in setPlaylist
      // Use new simplePlayerProvider for play()
      if (savedPosition != null && mounted) {
        unawaited(
          Future.delayed(const Duration(milliseconds: 300), () {
            if (mounted) {
              simplePlayerNotifier.play();
            }
          }),
        );
      } else {
        // If no saved position, start playback from beginning
        unawaited(
          Future.delayed(const Duration(milliseconds: 300), () {
            if (mounted) {
              simplePlayerNotifier.play();
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
          // Sort chapters by fileIndex to ensure playlist order matches file order
          final sortedChapters = List<Chapter>.from(parsed.chapters)
            ..sort((a, b) => a.fileIndex.compareTo(b.fileIndex));

          setState(() {
            _audiobook = parsed;
            _sortedChapters = sortedChapters;
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

  Future<void> _loadAudioSource({
    int? initialTrackIndex,
    int? initialPosition,
  }) async {
    if (_audiobook == null || _audiobook!.chapters.isEmpty) {
      // Fallback to demo audio if no chapters available
      // Use new simplePlayerProvider for setPlaylist
      final simplePlayerNotifier = ref.read(simplePlayerProvider.notifier);
      await simplePlayerNotifier.setPlaylist(
        filePaths: [
          'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3'
        ],
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

      // CRITICAL FIX: Use file order from disk, match chapters by position
      // The problem: chapters from description have fileIndex as sequential number,
      // but the actual files in directory are sorted by path. The fileIndex in chapters
      // may not correspond to the actual file order after sorting.
      //
      // Solution: Get all files sorted by path, then match chapters to files by position.
      // This ensures that playlist order matches the actual file order on disk.
      // We assume that chapters are already in the correct order matching file order.
      final fileFinder = LibraryFileFinder();

      // Get all files from directory, sorted by path
      final allFiles = await fileFinder.getAllFilesByBookId(widget.bookId);

      if (allFiles.isEmpty) {
        await StructuredLogger().log(
          level: 'warning',
          subsystem: 'player',
          message: 'No files found for audiobook',
          extra: {'bookId': widget.bookId},
        );
        if (mounted) {
          setState(() {
            _hasError = true;
            _errorMessage =
                'Files not found. Please download the audiobook first.';
          });
        }
        return;
      }

      // Sort chapters by fileIndex to maintain original order
      // This order should match the order files appear in the directory
      if (_sortedChapters == null || _sortedChapters!.isEmpty) {
        _sortedChapters = List<Chapter>.from(_audiobook!.chapters)
          ..sort((a, b) => a.fileIndex.compareTo(b.fileIndex));
      }

      // Use file order from disk (sorted by path)
      // Match chapters to files by position: chapter[0] -> file[0], chapter[1] -> file[1], etc.
      final filePaths = <String>[];
      final matchedChapters = <Chapter>[];

      // Match chapters to files by position
      // Take minimum of chapters and files to avoid index out of bounds
      final maxCount = _sortedChapters!.length < allFiles.length
          ? _sortedChapters!.length
          : allFiles.length;

      for (var i = 0; i < maxCount; i++) {
        filePaths.add(allFiles[i]);
        matchedChapters.add(_sortedChapters![i]);
      }

      // Update _sortedChapters to match the order of files
      _sortedChapters = matchedChapters;

      // Log detailed mapping for debugging
      unawaited(StructuredLogger().log(
        level: 'info',
        subsystem: 'player',
        message: 'Created playlist by matching chapters to files by position',
        extra: {
          'bookId': widget.bookId,
          'totalChapters': _audiobook!.chapters.length,
          'totalFiles': allFiles.length,
          'matchedCount': matchedChapters.length,
          'playlistSize': filePaths.length,
          'fileOrder': allFiles.take(10).map((p) => p.split('/').last).toList(),
          'mapping': matchedChapters.asMap().entries.take(10).map((e) {
            final filePath = e.key < filePaths.length ? filePaths[e.key] : null;
            return {
              'playlistIndex': e.key,
              'chapterTitle': e.value.title,
              'chapterFileIndex': e.value.fileIndex,
              'fileName': filePath?.split('/').last,
            };
          }).toList(),
        },
      ));

      // Log detailed mapping for debugging
      unawaited(StructuredLogger().log(
        level: 'info',
        subsystem: 'player',
        message: 'Created playlist by matching chapters to files by filename',
        extra: {
          'bookId': widget.bookId,
          'totalChapters': _audiobook!.chapters.length,
          'matchedChapters': _sortedChapters!.length,
          'totalFiles': allFiles.length,
          'playlistSize': filePaths.length,
          'mapping': _sortedChapters!.asMap().entries.map((e) {
            final filePath = e.key < filePaths.length ? filePaths[e.key] : null;
            return {
              'playlistIndex': e.key,
              'chapterTitle': e.value.title,
              'chapterFileIndex': e.value.fileIndex,
              'filePath': filePath,
              'fileName': filePath?.split('/').last,
            };
          }).toList(),
          'allFilesOrder':
              allFiles.take(10).map((p) => p.split('/').last).toList(),
        },
      ));

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

      // Set playlist with all chapters using real file paths
      // Use initialTrackIndex and initialPosition to immediately load the saved track
      // Use new simplePlayerProvider for setPlaylist
      final simplePlayerNotifier = ref.read(simplePlayerProvider.notifier);
      await simplePlayerNotifier.setPlaylist(
        filePaths: filePaths,
        metadata: metadata,
        groupPath: widget.bookId,
        initialTrackIndex: initialTrackIndex,
        initialPosition: initialPosition,
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
    final playerNotifier = ref.read(simplePlayerProvider.notifier);
    final state = ref.read(simplePlayerProvider);
    if (state.isPlaying) {
      playerNotifier.pause();
    } else {
      playerNotifier.play();
    }
  }

  void _seekToChapter(Chapter chapter) {
    if (_audiobook == null) return;

    // Use new simplePlayerProvider for seekToTrack
    final simplePlayerNotifier = ref.read(simplePlayerProvider.notifier);

    // CRITICAL: Playlist is created by matching chapters to files by filename
    // _sortedChapters contains chapters in the same order as files in the playlist
    if (_audiobook!.chapters.length > 1) {
      // Ensure sorted chapters are available (should be set during _loadAudioSource)
      if (_sortedChapters == null || _sortedChapters!.isEmpty) {
        // Fallback: if _sortedChapters not set, sort by fileIndex
        _sortedChapters = List<Chapter>.from(_audiobook!.chapters)
          ..sort((a, b) => a.fileIndex.compareTo(b.fileIndex));
      }

      // Find the chapter's position in the sorted list
      // Since _sortedChapters matches the playlist order, the index here
      // directly corresponds to the playlist index
      var playlistIndex = _sortedChapters!.indexWhere(
        (ch) => ch.title == chapter.title && ch.fileIndex == chapter.fileIndex,
      );

      // Fallback: if exact match not found, try by title only
      if (playlistIndex < 0) {
        playlistIndex = _sortedChapters!.indexWhere(
          (ch) => ch.title == chapter.title,
        );
      }

      // Fallback: if still not found, try by fileIndex (for backward compatibility)
      if (playlistIndex < 0) {
        playlistIndex = _sortedChapters!.indexWhere(
          (ch) => ch.fileIndex == chapter.fileIndex,
        );
      }

      // Log for debugging
      unawaited(StructuredLogger().log(
        level: 'info',
        subsystem: 'player',
        message: 'Seeking to chapter',
        extra: {
          'chapterTitle': chapter.title,
          'chapterFileIndex': chapter.fileIndex,
          'playlistIndex': playlistIndex,
          'sortedChaptersCount': _sortedChapters!.length,
          'originalChaptersCount': _audiobook!.chapters.length,
        },
      ));

      if (playlistIndex < 0 || playlistIndex >= _sortedChapters!.length) {
        // Chapter not found in sorted list, abort
        unawaited(StructuredLogger().log(
          level: 'warning',
          subsystem: 'player',
          message: 'Chapter not found in sorted list',
          extra: {
            'chapterTitle': chapter.title,
            'chapterFileIndex': chapter.fileIndex,
            'sortedChaptersCount': _sortedChapters!.length,
          },
        ));
        return;
      }

      // Perform the seek using sorted index (which equals playlist index)
      simplePlayerNotifier
        ..seekToTrack(playlistIndex)
        ..seek(0);
    } else {
      // For single file: seek to chapter start position
      final target = Duration(
          milliseconds: chapter.startByte > 0 ? 0 : (chapter.durationMs ~/ 2));
      simplePlayerNotifier.seek(target.inMilliseconds);
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

    simplePlayerNotifier.updateMetadata({
      'title': '${_audiobook!.title} — ${chapter.title}',
      'artist': _audiobook!.author,
      'album': _audiobook!.category,
      if (art != null) 'artworkUri': art.toString(),
    });
  }

  void _prevChapter() {
    final state = ref.read(simplePlayerProvider);
    if (state.currentTrackIndex > 0) {
      ref.read(simplePlayerProvider.notifier).previous();
    }
  }

  void _nextChapter() {
    final state = ref.read(simplePlayerProvider);
    if (_sortedChapters != null &&
        state.currentTrackIndex < _sortedChapters!.length - 1) {
      ref.read(simplePlayerProvider.notifier).next();
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
    // Use new simplePlayerProvider for UI state
    final playerState = ref.watch(simplePlayerProvider);
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
        'currentIndex': playerState.currentTrackIndex,
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
              if (_audiobook != null)
                PlayerInfo(
                  title: _audiobook!.title,
                  author: _audiobook!.author,
                  coverUrl: (_audiobook as dynamic).coverUrl as String?,
                  category: _audiobook!.category,
                  size: _audiobook!.size,
                ),

              // Progress bar
              PlayerProgress(
                currentPosition: playerState.currentPosition,
                duration: playerState.duration,
                onSeek: (position) {
                  ref
                      .read(simplePlayerProvider.notifier)
                      .seekDuration(Duration(milliseconds: position));
                },
                isDragging: _isDragging,
                sliderValue: _sliderValue,
              ),

              // Current chapter indicator
              if (_audiobook != null &&
                  _sortedChapters != null &&
                  _sortedChapters!.isNotEmpty &&
                  playerState.currentTrackIndex >= 0 &&
                  playerState.currentTrackIndex < _sortedChapters!.length)
                ChapterIndicator(
                  currentChapterIndex: playerState.currentTrackIndex,
                  totalChapters: _sortedChapters!.length,
                  currentChapterTitle:
                      _sortedChapters![playerState.currentTrackIndex].title,
                  onTap: _showChaptersBottomSheet,
                ),

              // Player controls
              PlayerControls(
                isPlaying: playerState.isPlaying,
                onPlayPause: _playPause,
                onPrevious:
                    playerState.currentTrackIndex > 0 ? _prevChapter : null,
                onNext: _nextChapter,
                onSpeedChanged: (speed) {
                  ref.read(simplePlayerProvider.notifier).setSpeed(speed);
                },
                currentSpeed: playerState.playbackSpeed,
                canGoPrevious: playerState.currentTrackIndex > 0,
                canGoNext: _sortedChapters != null &&
                    playerState.currentTrackIndex < _sortedChapters!.length - 1,
                previousTooltip: _sortedChapters != null &&
                        playerState.currentTrackIndex > 0 &&
                        playerState.currentTrackIndex - 1 >= 0 &&
                        playerState.currentTrackIndex - 1 <
                            _sortedChapters!.length
                    ? _sortedChapters![playerState.currentTrackIndex - 1].title
                    : null,
                nextTooltip: _sortedChapters != null &&
                        playerState.currentTrackIndex >= 0 &&
                        playerState.currentTrackIndex + 1 >= 0 &&
                        playerState.currentTrackIndex + 1 <
                            _sortedChapters!.length
                    ? _sortedChapters![playerState.currentTrackIndex + 1].title
                    : null,
              ),

              // Nearby chapters carousel
              if (_sortedChapters != null &&
                  _sortedChapters!.length > 1 &&
                  playerState.currentTrackIndex >= 0 &&
                  playerState.currentTrackIndex < _sortedChapters!.length)
                NearbyChaptersCarousel(
                  chapters: _sortedChapters!,
                  currentIndex: playerState.currentTrackIndex,
                  onChapterSelected: _seekToChapter,
                ),
            ],
          ),
        ),
      ),
    );
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

    ref.read(simplePlayerProvider.notifier).updateMetadata(metadata);
  }
}
