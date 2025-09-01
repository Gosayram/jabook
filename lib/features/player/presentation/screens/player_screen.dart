import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/core/parse/rutracker_parser.dart';
import 'package:jabook/core/stream/local_stream_server.dart';
import 'package:just_audio/just_audio.dart';

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
  
  Audiobook? _audiobook;
  Duration _currentPosition = Duration.zero;
  Duration _totalDuration = Duration.zero;
  bool _isPlaying = false;
  bool _isLoading = true;
  bool _hasError = false;
  StreamSubscription? _positionSubscription;
  StreamSubscription? _playerStateSubscription;

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
    _audioPlayer.dispose();
    _streamServer.stop();
    super.dispose();
  }

  Future<void> _initializePlayer() async {
    try {
      // TODO: Load audiobook data based on bookId
      // For now, create a placeholder audiobook
      _audiobook = Audiobook(
        id: widget.bookId,
        title: 'Sample Audiobook',
        author: 'Sample Author',
        category: 'Fiction',
        size: '150 MB',
        seeders: 10,
        leechers: 5,
        magnetUrl: '',
        chapters: [
          Chapter(
            title: 'Chapter 1',
            durationMs: 15 * 60 * 1000, // 15 minutes
            fileIndex: 0,
            startByte: 0,
            endByte: 50000000,
          ),
          Chapter(
            title: 'Chapter 2',
            durationMs: 20 * 60 * 1000, // 20 minutes
            fileIndex: 0,
            startByte: 50000000,
            endByte: 100000000,
          ),
        ],
        addedDate: DateTime.now(),
      );

      // Set up audio player listeners
      _positionSubscription = _audioPlayer.positionStream.listen((position) {
        setState(() {
          _currentPosition = position;
        });
      });

      _playerStateSubscription = _audioPlayer.playerStateStream.listen((state) {
        setState(() {
          _isPlaying = state.playing;
          _isLoading = state.processingState == ProcessingState.loading;
        });
      });

      // Set total duration
      _audioPlayer.durationStream.listen((duration) {
        setState(() {
          _totalDuration = duration ?? Duration.zero;
        });
      });

      // Load a sample audio file (in real app, this would be from the downloaded files)
      await _audioPlayer.setUrl('https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3');
      
      setState(() {
        _isLoading = false;
        _hasError = false;
      });
    } on Exception catch (_) {
      setState(() {
        _isLoading = false;
        _hasError = true;
      });
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

  void _seekToPosition(double value) {
    final position = Duration(
      milliseconds: (value * _totalDuration.inMilliseconds).round(),
    );
    _audioPlayer.seek(position);
  }

  void _seekToChapter(Chapter chapter) {
    _audioPlayer.seek(Duration(milliseconds: chapter.durationMs ~/ 2)); // Seek to middle of chapter
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: Text('Player: ${widget.bookId}'),
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
            const Text('Failed to load audiobook'),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: _initializePlayer,
              child: const Text('Retry'),
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
                value: _totalDuration.inMilliseconds > 0
                    ? _currentPosition.inMilliseconds / _totalDuration.inMilliseconds
                    : 0.0,
                onChanged: _seekToPosition,
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
                onPressed: _audioPlayer.seekToPrevious,
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
                onPressed: _audioPlayer.seekToNext,
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
                    'Chapters',
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                  const SizedBox(height: 8),
                  Expanded(
                    child: ListView.builder(
                      itemCount: _audiobook!.chapters.length,
                      itemBuilder: (context, index) {
                        final chapter = _audiobook!.chapters[index];
                        return ListTile(
                          leading: const Icon(Icons.book),
                          title: Text(chapter.title),
                          subtitle: Text(_formatDuration(Duration(milliseconds: chapter.durationMs))),
                          onTap: () => _seekToChapter(chapter),
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
      const SnackBar(content: Text('Download functionality coming soon!')),
    );
  }
}