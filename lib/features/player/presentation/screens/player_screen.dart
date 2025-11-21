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
import 'package:jabook/core/endpoints/endpoint_manager.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/parse/rutracker_parser.dart';
import 'package:jabook/core/player/audio_service_handler.dart';
import 'package:jabook/core/stream/local_stream_server.dart';
import 'package:jabook/data/db/app_database.dart';
import 'package:jabook/l10n/app_localizations.dart';
import 'package:just_audio/just_audio.dart';
import 'package:rxdart/rxdart.dart';
import 'package:shared_preferences/shared_preferences.dart';

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
  final AudioPlayer _audioPlayer = AudioPlayer();
  final LocalStreamServer _streamServer = LocalStreamServer();
  final AudioServiceHandler _audioService = AudioServiceHandler();

  Audiobook? _audiobook;
  Duration _currentPosition = Duration.zero;
  Duration _totalDuration = Duration.zero;
  bool _isPlaying = false;
  bool _isLoading = true;
  bool _hasError = false;
  StreamSubscription? _positionSubscription;
  StreamSubscription? _playerStateSubscription;
  int _currentChapterIndex = 0;
  // Local state for slider during dragging
  double? _sliderValue;
  bool _isDragging = false;
  Timer? _positionSaveTimer;

  @override
  void initState() {
    super.initState();
    _initializePlayer();
    _startStreamServer();
  }

  @override
  void dispose() {
    _positionSubscription?.cancel();
    _playerStateSubscription?.cancel();
    _positionSaveTimer?.cancel();
    _saveCurrentPosition();
    _audioPlayer.dispose();
    _streamServer.stop();
    super.dispose();
  }

  Future<void> _initializePlayer() async {
    try {
      await _audioService.startService();
      await _loadAudiobookFromRutracker();

      // Set up audio player listeners
      // Throttle position updates to avoid excessive rebuilds (update every 200ms)
      _positionSubscription = _audioPlayer.positionStream
          .throttleTime(const Duration(milliseconds: 200))
          .listen((position) {
        if (mounted) {
          setState(() {
            _currentPosition = position;
          });
          // Save position periodically
          _saveCurrentPosition();
        }
      });

      _playerStateSubscription = _audioPlayer.playerStateStream.listen((state) {
        if (!mounted) return;
        setState(() {
          _isPlaying = state.playing;
          _isLoading = state.processingState == ProcessingState.loading;
        });

        // Update current chapter index for playlist
        if (state.processingState == ProcessingState.ready &&
            _audiobook != null &&
            _audiobook!.chapters.length > 1) {
          final currentIndex = _audioPlayer.currentIndex ?? 0;
          if (currentIndex != _currentChapterIndex && mounted) {
            setState(() {
              _currentChapterIndex = currentIndex;
            });
          }
        }
      });

      // Set total duration
      _audioPlayer.durationStream.listen((duration) async {
        if (!mounted) return;
        final wasZero = _totalDuration == Duration.zero;
        setState(() {
          _totalDuration = duration ?? Duration.zero;
        });
        // Restore position when duration becomes available for the first time
        if (wasZero && duration != null && duration > Duration.zero) {
          await _restorePosition();
        }
        // Update system metadata with actual duration when known
        if (_audiobook != null && duration != null) {
          Uri? art;
          try {
            // ignore: deprecated_member_use_from_same_package
            final coverUrl = (_audiobook as dynamic).coverUrl as String?;
            if (coverUrl != null && coverUrl.isNotEmpty) {
              art = Uri.parse(coverUrl);
            }
          } on Exception catch (_) {}
          await _audioService.setNowPlayingMetadata(
            id: _audiobook!.id,
            title: _audiobook!.title,
            artist: _audiobook!.author,
            artUri: art,
            album: _audiobook!.category,
            duration: duration,
          );
        }
      });

      // Load real audio source via local stream server
      await _loadAudioSource();

      // Position will be restored automatically when duration becomes available
      // (see durationStream listener above)

      setState(() {
        _isLoading = false;
        _hasError = false;
      });

      // Update now-playing metadata for system notification/lockscreen
      if (_audiobook != null) {
        Uri? art;
        // In real flow, use parsed coverUrl from RuTracker topic if available
        try {
          // ignore: deprecated_member_use_from_same_package
          final coverUrl = (_audiobook as dynamic).coverUrl as String?;
          if (coverUrl != null && coverUrl.isNotEmpty) {
            art = Uri.parse(coverUrl);
          }
        } on Exception catch (_) {}
        await _audioService.setNowPlayingMetadata(
          id: _audiobook!.id,
          title: _audiobook!.title,
          artist: _audiobook!.author,
          artUri: art,
          album: _audiobook!.category,
        );
      }
    } on Exception catch (_) {
      setState(() {
        _isLoading = false;
        _hasError = true;
      });
    }
  }

  Future<void> _loadAudiobookFromRutracker() async {
    try {
      final db = AppDatabase().database;
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
          setState(() => _audiobook = parsed);
        }
      }
    } on Exception {
      // ignore and keep error handling by caller
    }
  }

  Future<void> _loadAudioSource() async {
    if (_audiobook == null || _audiobook!.chapters.isEmpty) {
      // Fallback to demo audio if no chapters available
      await _audioPlayer.setUrl(
          'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3');
      return;
    }

    try {
      // Start local stream server
      await _streamServer.start();

      // For now, use first chapter as single file
      // TODO: Implement playlist for multi-file audiobooks
      final firstChapter = _audiobook!.chapters.first;
      final streamUrl =
          _streamServer.getStreamUrl(widget.bookId, firstChapter.fileIndex);

      await _audioPlayer.setUrl(streamUrl);

      // Set up playlist if multiple chapters exist
      if (_audiobook!.chapters.length > 1) {
        await _setupPlaylist();
      }
    } on Exception catch (_) {
      // Fallback to demo audio on error
      await _audioPlayer.setUrl(
          'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3');
    }
  }

  Future<void> _setupPlaylist() async {
    if (_audiobook == null || _audiobook!.chapters.length <= 1) return;

    try {
      // Create playlist from chapters
      final audioSources = _audiobook!.chapters
          .map((chapter) => AudioSource.uri(
                Uri.parse(_streamServer.getStreamUrl(
                    widget.bookId, chapter.fileIndex)),
              ))
          .toList();

      await _audioPlayer.setAudioSources(audioSources);
    } on Exception catch (_) {
      // Keep single file if playlist setup fails
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
    if (_isPlaying) {
      _audioPlayer.pause();
    } else {
      _audioPlayer.play();
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
    final position = Duration(
      milliseconds: (value * _totalDuration.inMilliseconds).round(),
    );
    _audioPlayer.seek(position);

    // Reset local state
    setState(() {
      _isDragging = false;
      _sliderValue = null;
    });
  }

  void _seekToChapter(Chapter chapter) {
    if (_audiobook == null) return;

    // For playlist: seek to chapter index
    if (_audiobook!.chapters.length > 1) {
      final chapterIndex = _audiobook!.chapters.indexOf(chapter);
      if (chapterIndex >= 0) {
        _audioPlayer.seek(Duration.zero, index: chapterIndex);
        _currentChapterIndex = chapterIndex;
      }
    } else {
      // For single file: seek to chapter start position
      final target = Duration(
          milliseconds: chapter.startByte > 0 ? 0 : (chapter.durationMs ~/ 2));
      _audioPlayer.seek(target);
    }

    // Update now-playing metadata with chapter info
    Uri? art;
    try {
      // ignore: deprecated_member_use_from_same_package
      final coverUrl = (_audiobook as dynamic).coverUrl as String?;
      if (coverUrl != null && coverUrl.isNotEmpty) {
        art = Uri.parse(coverUrl);
      }
    } on Exception catch (_) {}

    _audioService.setNowPlayingMetadata(
      id: _audiobook!.id,
      title: '${_audiobook!.title} — ${chapter.title}',
      artist: _audiobook!.author,
      artUri: art,
      album: _audiobook!.category,
      duration: Duration(milliseconds: chapter.durationMs),
    );
  }

  void _prevChapter() {
    if (_audiobook == null || _audiobook!.chapters.isEmpty) return;
    if (_currentChapterIndex > 0) {
      _currentChapterIndex--;
      _seekToChapter(_audiobook!.chapters[_currentChapterIndex]);
    }
  }

  void _nextChapter() {
    if (_audiobook == null || _audiobook!.chapters.isEmpty) return;
    if (_currentChapterIndex < _audiobook!.chapters.length - 1) {
      _currentChapterIndex++;
      _seekToChapter(_audiobook!.chapters[_currentChapterIndex]);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
          title: Text(
              '${AppLocalizations.of(context)?.playerTitle ?? 'Player'}: ${widget.bookId}'),
          actions: [
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
            Text(AppLocalizations.of(context)?.failedToLoadAudio ??
                'Failed to load audiobook'),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: _initializePlayer,
              child: Text(AppLocalizations.of(context)?.retry ?? 'Retry'),
            ),
          ],
        ),
      );
    }

    return Column(
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
                  final coverUrl = (_audiobook as dynamic).coverUrl as String?;
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
                                child:
                                    CircularProgressIndicator(strokeWidth: 2),
                              ),
                            ),
                          ),
                          errorWidget: (context, url, error) => Container(
                            width: 96,
                            height: 96,
                            color: Theme.of(context).colorScheme.errorContainer,
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
                  Text(_formatDuration(_currentPosition)),
                  Text(_formatDuration(_totalDuration)),
                ],
              ),
              const SizedBox(height: 8),
              Slider(
                value: _isDragging && _sliderValue != null
                    ? _sliderValue!.clamp(0.0, 1.0)
                    : (_totalDuration.inMilliseconds > 0
                        ? _currentPosition.inMilliseconds /
                            _totalDuration.inMilliseconds
                        : 0.0),
                onChanged:
                    _totalDuration.inMilliseconds > 0 ? _onSliderChanged : null,
                onChangeStart:
                    _totalDuration.inMilliseconds > 0 ? _onSliderStart : null,
                onChangeEnd:
                    _totalDuration.inMilliseconds > 0 ? _onSliderEnd : null,
              ),
            ],
          ),
        ),

        // Player controls
        Padding(
          padding: const EdgeInsets.all(16.0),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              IconButton(
                icon: const Icon(Icons.skip_previous),
                onPressed: _prevChapter,
                iconSize: 48,
              ),
              const SizedBox(width: 32),
              IconButton(
                icon: Icon(_isPlaying ? Icons.pause : Icons.play_arrow),
                onPressed: _playPause,
                iconSize: 64,
              ),
              const SizedBox(width: 32),
              IconButton(
                icon: const Icon(Icons.skip_next),
                onPressed: _nextChapter,
                iconSize: 48,
              ),
            ],
          ),
        ),

        // Chapters
        if (_audiobook!.chapters.isNotEmpty)
          Expanded(
            child: Padding(
              padding: const EdgeInsets.all(16.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    AppLocalizations.of(context)?.chaptersLabel ?? 'Chapters',
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                  const SizedBox(height: 8),
                  Expanded(
                    child: ListView.builder(
                      itemCount: _audiobook!.chapters.length,
                      itemBuilder: (context, index) {
                        final chapter = _audiobook!.chapters[index];
                        // Use RepaintBoundary to isolate repaints for each chapter item
                        return RepaintBoundary(
                          child: ListTile(
                            leading: const Icon(Icons.book),
                            title: Text(chapter.title),
                            subtitle: Text(_formatDuration(
                                Duration(milliseconds: chapter.durationMs))),
                            onTap: () => _seekToChapter(chapter),
                          ),
                        );
                      },
                    ),
                  ),
                ],
              ),
            ),
          ),
      ],
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

  /// Saves current playback position to SharedPreferences.
  Future<void> _saveCurrentPosition() async {
    if (_audiobook == null || _currentPosition == Duration.zero) return;
    try {
      final prefs = await SharedPreferences.getInstance();
      final positionKey = 'pos_${widget.bookId}';
      final chapterKey = 'chapter_${widget.bookId}';
      await prefs.setInt(positionKey, _currentPosition.inMilliseconds);
      await prefs.setInt(chapterKey, _currentChapterIndex);
    } on Exception {
      // Ignore errors - position saving is not critical
    }
  }

  /// Restores saved playback position from SharedPreferences and starts playback.
  Future<void> _restorePosition() async {
    if (_audiobook == null) return;
    try {
      final prefs = await SharedPreferences.getInstance();
      final positionKey = 'pos_${widget.bookId}';
      final chapterKey = 'chapter_${widget.bookId}';
      final savedPositionMs = prefs.getInt(positionKey) ?? 0;
      final savedChapterIndex = prefs.getInt(chapterKey) ?? 0;

      if (savedPositionMs > 0 && savedChapterIndex >= 0) {
        // Wait for player to be ready
        var attempts = 0;
        while (attempts < 20 && mounted) {
          if (_totalDuration.inMilliseconds > 0 &&
              _audioPlayer.processingState == ProcessingState.ready) {
            break;
          }
          await Future.delayed(const Duration(milliseconds: 100));
          attempts++;
        }

        if (mounted && _totalDuration.inMilliseconds > 0) {
          // Restore chapter if multiple chapters exist
          if (_audiobook!.chapters.length > 1 &&
              savedChapterIndex < _audiobook!.chapters.length) {
            _currentChapterIndex = savedChapterIndex;
            await _audioPlayer.seek(Duration.zero, index: savedChapterIndex);
          }

          // Restore position within current chapter
          final savedPosition = Duration(milliseconds: savedPositionMs);
          if (savedPosition < _totalDuration) {
            await _audioPlayer.seek(savedPosition);
            // Automatically start playback from restored position
            await _audioPlayer.play();
          }
        }
      }
    } on Exception {
      // Ignore errors - position restoration is not critical
    }
  }
}
