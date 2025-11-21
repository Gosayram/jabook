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

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/core/errors/failures.dart';
import 'package:jabook/core/library/local_audiobook.dart';
import 'package:jabook/core/library/playback_position_service.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/core/permissions/permission_service.dart';
import 'package:jabook/core/player/native_audio_player.dart';

/// Player screen for local audiobook files.
///
/// This screen provides the user interface for playing local audiobook files,
/// including playback controls, progress tracking, and automatic track switching.
class LocalPlayerScreen extends ConsumerStatefulWidget {
  /// Creates a new LocalPlayerScreen instance.
  ///
  /// The [group] parameter is required to identify which audiobook group
  /// should be displayed and played.
  const LocalPlayerScreen({super.key, required this.group});

  /// The audiobook group to play.
  final LocalAudiobookGroup group;

  @override
  ConsumerState<LocalPlayerScreen> createState() => _LocalPlayerScreenState();
}

class _LocalPlayerScreenState extends ConsumerState<LocalPlayerScreen> {
  final NativeAudioPlayer _nativePlayer = NativeAudioPlayer();
  final PlaybackPositionService _positionService = PlaybackPositionService();
  final PermissionService _permissionService = PermissionService();
  final StructuredLogger _logger = StructuredLogger();
  Duration _currentPosition = Duration.zero;
  Duration _totalDuration = Duration.zero;
  bool _isPlaying = false;
  bool _isLoading = true;
  bool _hasError = false;
  String? _errorMessage;
  StreamSubscription<AudioPlayerState>? _stateSubscription;
  int _currentTrackIndex = 0;
  Timer? _positionSaveTimer;
  double _playbackSpeed = 1.0;

  @override
  void initState() {
    super.initState();
    _initializePlayer();
  }

  @override
  void dispose() {
    _stateSubscription?.cancel();
    _positionSaveTimer?.cancel();
    // Save final position before disposing
    _saveCurrentPosition();
    _nativePlayer.dispose();
    super.dispose();
  }

  Future<void> _initializePlayer() async {
    try {
      // Initialize native player
      await _nativePlayer.initialize();

      // Set up state stream listener
      _stateSubscription = _nativePlayer.stateStream.listen((state) {
        if (!mounted) return;
        setState(() {
          _isPlaying = state.isPlaying;
          _currentPosition = Duration(milliseconds: state.currentPosition);
          _totalDuration = Duration(milliseconds: state.duration);
          _currentTrackIndex = state.currentIndex;
          _playbackSpeed = state.playbackSpeed;
          _isLoading = state.playbackState == 1; // 1 = buffering
        });
        // Save position periodically
        _schedulePositionSave();
      });

      // Restore saved position before loading
      final savedPosition = await _positionService.restorePosition(
        widget.group.groupPath,
      );

      // Load audio sources
      await _loadAudioSources();

      // Restore position if available
      if (savedPosition != null && mounted) {
        final trackIndex = savedPosition['trackIndex']!;
        final positionMs = savedPosition['positionMs']!;
        if (trackIndex >= 0 &&
            trackIndex < widget.group.files.length &&
            positionMs > 0) {
          // Wait for player to be ready (check state)
          var attempts = 0;
          while (attempts < 20 && mounted) {
            final state = await _nativePlayer.getState();
            if (state.playbackState == 2) {
              // 2 = ready
              break;
            }
            await Future.delayed(const Duration(milliseconds: 100));
            attempts++;
          }

          if (mounted) {
            try {
              // Use optimized method to seek to track and position at once
              await _nativePlayer.seekToTrackAndPosition(
                trackIndex,
                Duration(milliseconds: positionMs),
              );
              setState(() {
                _currentTrackIndex = trackIndex;
              });

              await _logger.log(
                level: 'info',
                subsystem: 'audio',
                message: 'Restored playback position',
                extra: {
                  'track_index': trackIndex,
                  'position_ms': positionMs,
                },
              );
            } on Exception catch (e) {
              await _logger.log(
                level: 'warning',
                subsystem: 'audio',
                message: 'Failed to restore position, continuing anyway',
                cause: e.toString(),
              );
              // Ignore seek errors, continue playback from start
            }
          }
        }
      }

      setState(() {
        _isLoading = false;
        _hasError = false;
        _errorMessage = null;
      });
    } on AudioFailure catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'audio',
        message: 'Failed to initialize player',
        cause: e.toString(),
      );
      setState(() {
        _isLoading = false;
        _hasError = true;
        _errorMessage = e.message;
      });
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'audio',
        message: 'Unexpected error during player initialization',
        cause: e.toString(),
      );
      setState(() {
        _isLoading = false;
        _hasError = true;
        _errorMessage = 'Failed to load audio: ${e.toString()}';
      });
    }
  }

  Future<void> _loadAudioSources() async {
    try {
      // Check permissions first
      final hasPermission = await _permissionService.hasStoragePermission();

      if (!hasPermission) {
        await _logger.log(
          level: 'warning',
          subsystem: 'audio',
          message: 'Storage permission not granted, requesting...',
        );
        // Request permission
        final granted = await _permissionService.requestStoragePermission();
        if (!granted) {
          await _logger.log(
            level: 'error',
            subsystem: 'audio',
            message: 'Storage permission denied',
          );
          throw const AudioFailure(
            'Storage permission is required to play local files. '
            'Please grant permission in app settings.',
          );
        }
      }

      // Verify files exist and are accessible
      final filePaths = widget.group.files.map((f) => f.filePath).toList();
      final accessibleFiles = <String>[];

      for (final filePath in filePaths) {
        final file = File(filePath);
        if (await file.exists()) {
          accessibleFiles.add(filePath);
        } else {
          await _logger.log(
            level: 'warning',
            subsystem: 'audio',
            message: 'File not found, skipping',
            extra: {'path': filePath},
          );
        }
      }

      if (accessibleFiles.isEmpty) {
        await _logger.log(
          level: 'error',
          subsystem: 'audio',
          message: 'No accessible audio files found',
          extra: {'total_files': filePaths.length},
        );
        throw const AudioFailure('No accessible audio files found');
      }

      await _logger.log(
        level: 'info',
        subsystem: 'audio',
        message: 'Loading audio sources',
        extra: {
          'accessible_files': accessibleFiles.length,
          'total_files': filePaths.length,
        },
      );

      // Load audio sources
      final metadata = <String, String>{
        'title': widget.group.groupName,
        if (widget.group.files.firstOrNull?.author != null)
          'artist': widget.group.files.firstOrNull!.author!,
      };

      await _nativePlayer.setPlaylist(
        accessibleFiles,
        metadata: metadata,
      );

      // Update metadata after playlist is set
      _updateMetadata();
    } on AudioFailure {
      rethrow;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'audio',
        message: 'Failed to load audio sources',
        cause: e.toString(),
      );
      throw AudioFailure('Failed to load audio: ${e.toString()}');
    }
  }

  void _playPause() {
    if (_isPlaying) {
      _nativePlayer.pause();
    } else {
      _nativePlayer.play();
    }
  }

  void _seekToPosition(double value) {
    final position = Duration(
      milliseconds: (value * _totalDuration.inMilliseconds).round(),
    );
    _nativePlayer.seek(position);
  }

  void _seekToTrack(int index) {
    if (index >= 0 && index < widget.group.files.length) {
      _nativePlayer
        ..seekToTrack(index)
        ..seek(Duration.zero);
      setState(() {
        _currentTrackIndex = index;
      });
      _saveCurrentPosition();
    }
  }

  /// Saves the current playback position.
  void _saveCurrentPosition() {
    final positionMs = _currentPosition.inMilliseconds;
    if (positionMs > 0) {
      _positionService.savePosition(
        widget.group.groupPath,
        _currentTrackIndex,
        positionMs,
      );
    }
  }

  /// Schedules a position save after a delay.
  ///
  /// This prevents excessive writes by debouncing position saves.
  void _schedulePositionSave() {
    _positionSaveTimer?.cancel();
    _positionSaveTimer =
        Timer(const Duration(seconds: 5), _saveCurrentPosition);
  }

  void _prevTrack() {
    if (_currentTrackIndex > 0) {
      _nativePlayer.previous();
      _saveCurrentPosition();
    }
  }

  void _nextTrack() {
    if (_currentTrackIndex < widget.group.files.length - 1) {
      _nativePlayer.next();
      _saveCurrentPosition();
    }
  }

  /// Updates metadata for current track.
  void _updateMetadata() {
    if (widget.group.files.isEmpty) return;
    final currentFile = widget.group.files[_currentTrackIndex];
    final metadata = <String, String>{
      'title': '${widget.group.groupName} — ${currentFile.displayName}',
      if (currentFile.author != null) 'artist': currentFile.author!,
      if (widget.group.groupName.isNotEmpty) 'album': widget.group.groupName,
    };
    _nativePlayer.updateMetadata(metadata);
  }

  /// Changes playback speed.
  ///
  /// [speed] is the playback speed (0.5 to 2.0).
  Future<void> _setSpeed(double speed) async {
    try {
      await _nativePlayer.setSpeed(speed);
    } on AudioFailure catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'audio',
        message: 'Failed to set playback speed',
        cause: e.toString(),
      );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to change speed: ${e.message}')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
          title: Text(widget.group.groupName),
        ),
        body: _hasError
            ? Center(
                child: Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Icon(Icons.error_outline,
                          size: 64, color: Colors.red),
                      const SizedBox(height: 16),
                      Text(
                        'Failed to load audio',
                        style: Theme.of(context).textTheme.titleMedium,
                        textAlign: TextAlign.center,
                      ),
                      if (_errorMessage != null) ...[
                        const SizedBox(height: 8),
                        Text(
                          _errorMessage!,
                          style: Theme.of(context).textTheme.bodyMedium,
                          textAlign: TextAlign.center,
                        ),
                      ],
                      const SizedBox(height: 24),
                      ElevatedButton(
                        onPressed: () {
                          setState(() {
                            _isLoading = true;
                            _hasError = false;
                            _errorMessage = null;
                          });
                          _initializePlayer();
                        },
                        child: const Text('Retry'),
                      ),
                    ],
                  ),
                ),
              )
            : _isLoading
                ? const Center(child: CircularProgressIndicator())
                : SingleChildScrollView(
                    child: Padding(
                      padding: const EdgeInsets.all(16.0),
                      child: Column(
                        children: [
                          // Cover image
                          _buildCoverImage(),
                          const SizedBox(height: 24),
                          // Track info
                          Text(
                            widget.group.files[_currentTrackIndex].displayName,
                            style: Theme.of(context).textTheme.titleLarge,
                            textAlign: TextAlign.center,
                          ),
                          const SizedBox(height: 8),
                          Text(
                            widget.group.groupName,
                            style: Theme.of(context).textTheme.bodyMedium,
                            textAlign: TextAlign.center,
                          ),
                          const SizedBox(height: 32),
                          // Group progress indicator
                          if (widget.group.files.length > 1)
                            _buildGroupProgressIndicator(),
                          const SizedBox(height: 16),
                          // Progress slider
                          _buildProgressSlider(),
                          const SizedBox(height: 16),
                          // Time indicators
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Text(_formatDuration(_currentPosition)),
                              Text(_formatDuration(_totalDuration)),
                            ],
                          ),
                          const SizedBox(height: 32),
                          // Playback controls
                          _buildPlaybackControls(),
                          const SizedBox(height: 16),
                          // Speed control
                          _buildSpeedControl(),
                          const SizedBox(height: 32),
                          // Track list
                          if (widget.group.files.length > 1) _buildTrackList(),
                        ],
                      ),
                    ),
                  ),
      );

  Widget _buildCoverImage() {
    if (widget.group.coverPath != null) {
      final coverFile = File(widget.group.coverPath!);
      if (coverFile.existsSync()) {
        return RepaintBoundary(
          child: ClipRRect(
            borderRadius: BorderRadius.circular(8),
            child: Image.file(
              coverFile,
              width: 300,
              height: 300,
              fit: BoxFit.cover,
              cacheWidth: 600, // 2x for retina displays
              errorBuilder: (context, error, stackTrace) =>
                  _buildDefaultCover(),
            ),
          ),
        );
      }
    }
    return _buildDefaultCover();
  }

  Widget _buildDefaultCover() => Container(
        width: 300,
        height: 300,
        decoration: BoxDecoration(
          color: Colors.grey[300],
          borderRadius: BorderRadius.circular(8),
        ),
        child: const Icon(Icons.audiotrack, size: 120, color: Colors.grey),
      );

  Widget _buildGroupProgressIndicator() {
    final totalTracks = widget.group.files.length;
    final currentTrack = _currentTrackIndex + 1;
    final progress = totalTracks > 0 ? currentTrack / totalTracks : 0.0;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              'Track $currentTrack of $totalTracks',
              style: Theme.of(context).textTheme.bodySmall,
            ),
            Text(
              '${(progress * 100).toStringAsFixed(0)}%',
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ],
        ),
        const SizedBox(height: 4),
        LinearProgressIndicator(
          value: progress.clamp(0.0, 1.0),
          minHeight: 4,
        ),
      ],
    );
  }

  Widget _buildProgressSlider() {
    final progress = _totalDuration.inMilliseconds > 0
        ? _currentPosition.inMilliseconds / _totalDuration.inMilliseconds
        : 0.0;

    return Slider(
      value: progress.clamp(0.0, 1.0),
      onChanged: _isLoading ? null : _seekToPosition,
    );
  }

  Widget _buildPlaybackControls() => Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          IconButton(
            icon: const Icon(Icons.skip_previous),
            iconSize: 32,
            onPressed: _currentTrackIndex > 0 ? _prevTrack : null,
          ),
          const SizedBox(width: 16),
          IconButton(
            icon: Icon(_isPlaying ? Icons.pause : Icons.play_arrow),
            iconSize: 48,
            onPressed: _isLoading ? null : _playPause,
          ),
          const SizedBox(width: 16),
          IconButton(
            icon: const Icon(Icons.skip_next),
            iconSize: 32,
            onPressed: _currentTrackIndex < widget.group.files.length - 1
                ? _nextTrack
                : null,
          ),
        ],
      );

  Widget _buildTrackList() => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Tracks (${widget.group.files.length})',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 8),
          ListView.builder(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            itemCount: widget.group.files.length,
            itemBuilder: (context, index) {
              final file = widget.group.files[index];
              final isCurrent = index == _currentTrackIndex;
              return ListTile(
                leading: Icon(
                  isCurrent ? Icons.audiotrack : Icons.music_note,
                  color: isCurrent ? Theme.of(context).primaryColor : null,
                ),
                title: Text(
                  file.displayName,
                  style: TextStyle(
                    fontWeight: isCurrent ? FontWeight.bold : FontWeight.normal,
                  ),
                ),
                subtitle: Text(file.formattedSize),
                onTap: () => _seekToTrack(index),
              );
            },
          ),
        ],
      );

  Widget _buildSpeedControl() => Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Text(
          'Speed: ',
          style: Theme.of(context).textTheme.bodyMedium,
        ),
        const SizedBox(width: 8),
        ...([0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0].map((speed) {
          final isSelected = (_playbackSpeed - speed).abs() < 0.01;
          return Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4.0),
            child: ChoiceChip(
              label: Text('${speed}x'),
              selected: isSelected,
              onSelected: (selected) {
                if (selected) {
                  _setSpeed(speed);
                }
              },
            ),
          );
        })),
      ],
    );

  String _formatDuration(Duration duration) {
    final minutes = duration.inMinutes;
    final seconds = duration.inSeconds % 60;
    return '${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}';
  }
}
