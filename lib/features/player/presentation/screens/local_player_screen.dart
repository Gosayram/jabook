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
import 'package:go_router/go_router.dart';
import 'package:jabook/core/errors/failures.dart';
import 'package:jabook/core/library/local_audiobook.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/core/permissions/permission_service.dart';
import 'package:jabook/core/player/native_audio_player.dart';
import 'package:jabook/core/player/playback_settings_provider.dart';
import 'package:jabook/core/player/player_state_provider.dart';
import 'package:jabook/core/player/sleep_timer_service.dart';
import 'package:jabook/core/utils/safe_async.dart';
import 'package:jabook/l10n/app_localizations.dart';

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
  final PermissionService _permissionService = PermissionService();
  final StructuredLogger _logger = StructuredLogger();
  final SleepTimerService _sleepTimerService = SleepTimerService();
  Timer? _sleepTimerUpdateTimer;
  bool _isInitialized = false;
  bool _hasError = false;
  String? _errorMessage;
  // Local state for slider during dragging
  double? _sliderValue;
  bool _isDragging = false;
  String? _embeddedArtworkPath; // Path to embedded artwork from metadata

  @override
  void initState() {
    super.initState();
    // Store current group in provider for mini player navigation
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(currentAudiobookGroupProvider.notifier).state = widget.group;
    });
    _initializePlayer();
    // Listen to player state changes to update metadata when track changes
    _setupMetadataListener();
  }

  @override
  void didUpdateWidget(LocalPlayerScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    // If group changed, reinitialize player
    if (oldWidget.group.groupPath != widget.group.groupPath) {
      setState(() {
        _isInitialized = false;
        _hasError = false;
        _errorMessage = null;
      });
      // Stop previous playback before switching
      ref.read(playerStateProvider.notifier).stop();
      // Update current group
      ref.read(currentAudiobookGroupProvider.notifier).state = widget.group;
      // Reinitialize with new group
      _initializePlayer();
    }
  }

  void _setupMetadataListener() {
    // Listen to player state changes and update metadata when track index changes
    ref.listen(playerStateProvider, (previous, next) {
      if (previous?.currentIndex != next.currentIndex) {
        // Track changed, update metadata
        _updateMetadata();
        // Check for embedded artwork from metadata
        _checkEmbeddedArtwork();
      }
    });
    // Also check for embedded artwork on initialization
    WidgetsBinding.instance.addPostFrameCallback((_) {
      Future.delayed(const Duration(milliseconds: 500), _checkEmbeddedArtwork);
    });
  }

  Future<void> _checkEmbeddedArtwork() async {
    try {
      // Get current media item info which includes artworkPath
      final nativePlayer = NativeAudioPlayer();
      final mediaInfo = await nativePlayer.getCurrentMediaItemInfo();
      final artworkPath = mediaInfo['artworkPath'] as String?;
      if (artworkPath != null && artworkPath.isNotEmpty) {
        final artworkFile = File(artworkPath);
        if (artworkFile.existsSync()) {
          setState(() {
            _embeddedArtworkPath = artworkPath;
          });
        }
      }
    } on Exception catch (e) {
      // Silently fail - embedded artwork is optional
      safeUnawaited(_logger.log(
        level: 'debug',
        subsystem: 'player',
        message: 'Failed to check embedded artwork',
        cause: e.toString(),
      ));
    }
  }

  Future<void> _initializePlayer() async {
    try {
      final playerNotifier = ref.read(playerStateProvider.notifier);
      final currentState = ref.read(playerStateProvider);

      // Check if player is already initialized and playing the same group
      // If yes, skip reinitialization to avoid interrupting playback
      if (currentState.currentGroupPath == widget.group.groupPath &&
          currentState.playbackState != 0) {
        // Player is already playing this group - just mark as initialized
        // No need to reload or restore position, player is already at correct position
        await _logger.log(
          level: 'info',
          subsystem: 'audio',
          message:
              'Player already initialized for this group, skipping reinitialization',
          extra: {
            'group_path': widget.group.groupPath,
            'current_position': currentState.currentPosition,
            'current_track': currentState.currentIndex,
          },
        );
        setState(() {
          _isInitialized = true;
          _hasError = false;
          _errorMessage = null;
        });
        return;
      }

      // Player is not initialized or playing different group - full initialization needed
      // Stop any existing playback first to avoid conflicts
      try {
        await playerNotifier.stop();
      } on Exception {
        // Ignore errors when stopping (might not be playing)
      }

      // Initialize player service
      await playerNotifier.initialize();

      // Restore saved position before loading
      final savedPosition = await playerNotifier.restorePosition(
        widget.group.groupPath,
      );

      // Load audio sources
      await _loadAudioSources();

      // Wait for player to be ready before enabling controls
      await _waitForPlayerReady();

      // Restore position if available - automatically continue from saved position
      // Only restore if we actually loaded new sources (not if player was already playing)
      if (savedPosition != null && mounted) {
        final trackIndex = savedPosition['trackIndex']!;
        final positionMs = savedPosition['positionMs']!;
        if (trackIndex >= 0 &&
            trackIndex < widget.group.files.length &&
            positionMs > 0) {
          // Wait for player to be ready (check state)
          var attempts = 0;
          while (attempts < 20 && mounted) {
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
              // Use optimized method to seek to track and position at once
              await playerNotifier.seekToTrackAndPosition(
                trackIndex,
                Duration(milliseconds: positionMs),
              );

              // Check if player is already playing - if not, start playback
              final currentState = ref.read(playerStateProvider);
              if (!currentState.isPlaying) {
                // Automatically start playback from restored position
                await playerNotifier.play();
              }

              await _logger.log(
                level: 'info',
                subsystem: 'audio',
                message: 'Restored playback position',
                extra: {
                  'track_index': trackIndex,
                  'position_ms': positionMs,
                  'was_playing': currentState.isPlaying,
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
        } else {
          // Invalid saved position, start playback from beginning
          if (mounted) {
            await _logger.log(
              level: 'info',
              subsystem: 'audio',
              message:
                  'No valid saved position, starting playback from beginning',
            );
            // Wait a bit for player to be ready
            await Future.delayed(const Duration(milliseconds: 200));
            final currentState = ref.read(playerStateProvider);
            if (!currentState.isPlaying && mounted) {
              await playerNotifier.play();
            }
          }
        }
      } else {
        // No saved position, start playback from beginning
        if (mounted) {
          await _logger.log(
            level: 'info',
            subsystem: 'audio',
            message: 'No saved position, starting playback from beginning',
          );
          // Wait a bit for player to be ready
          await Future.delayed(const Duration(milliseconds: 200));
          final currentState = ref.read(playerStateProvider);
          if (!currentState.isPlaying && mounted) {
            await _logger.log(
              level: 'info',
              subsystem: 'audio',
              message: 'Calling play() to start playback',
              extra: {
                'playbackState': currentState.playbackState,
                'isPlaying': currentState.isPlaying,
              },
            );
            try {
              await playerNotifier.play();
              await _logger.log(
                level: 'info',
                subsystem: 'audio',
                message: 'play() call completed',
              );
            } on Exception catch (e) {
              await _logger.log(
                level: 'error',
                subsystem: 'audio',
                message: 'Failed to call play()',
                cause: e.toString(),
              );
            }
          }
        }
      }

      // Restore repeat mode and sleep timer from saved state
      final savedFullState =
          await ref.read(playerStateProvider.notifier).restoreFullState();
      if (savedFullState != null &&
          savedFullState.groupPath == widget.group.groupPath) {
        // Restore repeat mode
        final repeatMode = RepeatMode.values[
            savedFullState.repeatMode.clamp(0, RepeatMode.values.length - 1)];
        await ref
            .read(playbackSettingsProvider.notifier)
            .setRepeatMode(repeatMode);

        // Restore sleep timer if active
        if (savedFullState.sleepTimerRemainingSeconds != null &&
            savedFullState.sleepTimerRemainingSeconds! > 0) {
          await _sleepTimerService.startTimer(
            Duration(seconds: savedFullState.sleepTimerRemainingSeconds!),
            () {
              if (mounted) {
                ref.read(playerStateProvider.notifier).pause();
                final localizations = AppLocalizations.of(context);
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: Text(localizations?.sleepTimerPaused ??
                        'Sleep timer: Playback paused'),
                  ),
                );
              }
            },
          );
          _startSleepTimerUpdates();
        }
      }

      // Clear any previous errors before marking as initialized
      // This ensures error state is reset on successful initialization
      setState(() {
        _isInitialized = true;
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
        _isInitialized = true;
        _hasError = true;
        final localizations = AppLocalizations.of(context);
        var errorMessage = e.message;

        // Localize common audio error messages
        if (e.message.contains('Failed to start audio service')) {
          final errorMatch = RegExp(r'Failed to start audio service: (.+)')
              .firstMatch(e.message);
          errorMessage = localizations?.failedToStartAudioService(
                  errorMatch?.group(1) ?? e.message) ??
              e.message;
        } else if (e.message.contains('Failed to play media')) {
          final errorMatch =
              RegExp(r'Failed to play media: (.+)').firstMatch(e.message);
          errorMessage = localizations
                  ?.failedToPlayMedia(errorMatch?.group(1) ?? e.message) ??
              e.message;
        } else if (e.message.contains('Failed to pause media')) {
          final errorMatch =
              RegExp(r'Failed to pause media: (.+)').firstMatch(e.message);
          errorMessage = localizations
                  ?.failedToPauseMedia(errorMatch?.group(1) ?? e.message) ??
              e.message;
        } else if (e.message.contains('Failed to stop media')) {
          final errorMatch =
              RegExp(r'Failed to stop media: (.+)').firstMatch(e.message);
          errorMessage = localizations
                  ?.failedToStopMedia(errorMatch?.group(1) ?? e.message) ??
              e.message;
        } else if (e.message.contains('Failed to seek')) {
          final errorMatch =
              RegExp(r'Failed to seek: (.+)').firstMatch(e.message);
          errorMessage =
              localizations?.failedToSeek(errorMatch?.group(1) ?? e.message) ??
                  e.message;
        } else if (e.message.contains('Failed to set speed')) {
          final errorMatch =
              RegExp(r'Failed to set speed: (.+)').firstMatch(e.message);
          errorMessage = localizations
                  ?.failedToSetSpeed(errorMatch?.group(1) ?? e.message) ??
              e.message;
        }

        _errorMessage = errorMessage;
      });
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'audio',
        message: 'Unexpected error during player initialization',
        cause: e.toString(),
      );
      setState(() {
        _isInitialized = true;
        _hasError = true;
        _errorMessage =
            AppLocalizations.of(context)?.failedToLoadAudioMessage ??
                'Failed to load audio: ${e.toString()}';
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
        final errorMessage = mounted
            ? (AppLocalizations.of(context)?.noAccessibleAudioFiles ??
                'No accessible audio files found')
            : 'No accessible audio files found';
        throw AudioFailure(errorMessage);
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
        if (widget.group.coverPath != null)
          'coverPath': widget.group.coverPath!,
      };

      final playerNotifier = ref.read(playerStateProvider.notifier);
      await playerNotifier.setPlaylist(
        accessibleFiles,
        metadata: metadata,
        groupPath: widget.group.groupPath,
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
      final errorMessage = mounted
          ? (AppLocalizations.of(context)?.failedToLoadAudioMessage ??
              'Failed to load audio: ${e.toString()}')
          : 'Failed to load audio: ${e.toString()}';
      throw AudioFailure(errorMessage);
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

  void _seekToTrack(int index) {
    if (index >= 0 && index < widget.group.files.length) {
      ref.read(playerStateProvider.notifier)
        ..seekToTrack(index)
        ..seek(Duration.zero);
    }
  }

  void _prevTrack() {
    final state = ref.read(playerStateProvider);
    if (state.currentIndex > 0) {
      ref.read(playerStateProvider.notifier).previous();
    }
  }

  void _nextTrack() {
    final state = ref.read(playerStateProvider);
    if (state.currentIndex < widget.group.files.length - 1) {
      ref.read(playerStateProvider.notifier).next();
    }
  }

  /// Updates metadata for current track.
  void _updateMetadata() {
    if (widget.group.files.isEmpty) return;
    final state = ref.read(playerStateProvider);
    if (state.currentIndex < 0 ||
        state.currentIndex >= widget.group.files.length) {
      return;
    }
    final currentFile = widget.group.files[state.currentIndex];

    // Build title with fallback to filename if displayName is empty
    final title = currentFile.displayName.isNotEmpty
        ? '${widget.group.groupName} — ${currentFile.displayName}'
        : (widget.group.groupName.isNotEmpty
            ? widget.group.groupName
            : currentFile.fileName);

    final metadata = <String, String>{
      'title': title,
      if (currentFile.author != null && currentFile.author!.isNotEmpty)
        'artist': currentFile.author!,
      if (widget.group.groupName.isNotEmpty) 'album': widget.group.groupName,
      if (widget.group.coverPath != null) 'coverPath': widget.group.coverPath!,
    };
    ref.read(playerStateProvider.notifier).updateMetadata(metadata);
  }

  /// Changes playback speed.
  ///
  /// [speed] is the playback speed (0.5 to 2.0).
  Future<void> _setSpeed(double speed) async {
    try {
      await ref.read(playerStateProvider.notifier).setSpeed(speed);
    } on AudioFailure catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'audio',
        message: 'Failed to set playback speed',
        cause: e.toString(),
      );
      if (mounted) {
        final localizations = AppLocalizations.of(context);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
              content: Text(localizations?.failedToChangeSpeed(e.message) ??
                  'Failed to change speed: ${e.message}')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final playerState = ref.watch(playerStateProvider);
    final isLoading =
        !_isInitialized || playerState.playbackState == 1; // 1 = buffering
    // Only show error if initialization is complete and not loading
    // This prevents showing error messages during initial loading
    final hasError = !isLoading && (_hasError || playerState.error != null);
    final errorMessage = _errorMessage ?? playerState.error;

    return Scaffold(
      appBar: AppBar(
        title: Text(widget.group.groupName),
      ),
      body: GestureDetector(
        onVerticalDragEnd: (details) {
          // Swipe down to close (return to mini player)
          if (details.primaryVelocity != null &&
              details.primaryVelocity! > 500) {
            context.pop();
          }
        },
        onHorizontalDragEnd: (details) {
          // Swipe left for next track, swipe right for previous track
          if (details.primaryVelocity != null) {
            if (details.primaryVelocity! < -500) {
              // Swipe left - next track
              _nextTrack();
            } else if (details.primaryVelocity! > 500) {
              // Swipe right - previous track
              _prevTrack();
            }
          }
        },
        child: hasError
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
                        AppLocalizations.of(context)?.failedToLoadAudio ??
                            'Failed to load audio',
                        style: Theme.of(context).textTheme.titleMedium,
                        textAlign: TextAlign.center,
                      ),
                      if (errorMessage != null) ...[
                        const SizedBox(height: 8),
                        Text(
                          errorMessage,
                          style: Theme.of(context).textTheme.bodyMedium,
                          textAlign: TextAlign.center,
                        ),
                      ],
                      const SizedBox(height: 24),
                      ElevatedButton(
                        onPressed: () {
                          setState(() {
                            _isInitialized = false;
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
            : isLoading
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
                            widget.group.hasMultiFolderStructure
                                ? widget.group.files[playerState.currentIndex]
                                    .getDisplayNameWithPart(
                                        widget.group.groupPath)
                                : widget.group.files[playerState.currentIndex]
                                    .displayName,
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
                            _buildGroupProgressIndicator(playerState),
                          const SizedBox(height: 16),
                          // Progress slider
                          _buildProgressSlider(playerState),
                          const SizedBox(height: 16),
                          // Time indicators
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Text(_formatDuration(Duration(
                                  milliseconds: playerState.currentPosition))),
                              Text(_formatDuration(Duration(
                                  milliseconds: playerState.duration))),
                            ],
                          ),
                          const SizedBox(height: 32),
                          // Playback controls
                          _buildPlaybackControls(playerState),
                          const SizedBox(height: 16),
                          // Speed, repeat and sleep timer controls in one row
                          _buildControlsRow(playerState),
                          const SizedBox(height: 32),
                          // Track list
                          if (widget.group.files.length > 1)
                            _buildTrackList(playerState),
                        ],
                      ),
                    ),
                  ),
      ),
    );
  }

  Widget _buildCoverImage() {
    // Increased size after optimizing controls layout
    const coverSize = 285.0;

    // Try embedded artwork from metadata first (if available)
    if (_embeddedArtworkPath != null) {
      final embeddedFile = File(_embeddedArtworkPath!);
      if (embeddedFile.existsSync()) {
        return RepaintBoundary(
          child: ClipRRect(
            borderRadius: BorderRadius.circular(8),
            child: Image.file(
              embeddedFile,
              width: coverSize,
              height: coverSize,
              fit: BoxFit.cover,
              cacheWidth: (coverSize * 2).round(), // 2x for retina displays
              errorBuilder: (context, error, stackTrace) =>
                  _buildGroupCover(coverSize),
            ),
          ),
        );
      }
    }

    // Fallback to group cover path
    if (widget.group.coverPath != null) {
      final coverFile = File(widget.group.coverPath!);
      if (coverFile.existsSync()) {
        return RepaintBoundary(
          child: ClipRRect(
            borderRadius: BorderRadius.circular(8),
            child: Image.file(
              coverFile,
              width: coverSize,
              height: coverSize,
              fit: BoxFit.cover,
              cacheWidth: (coverSize * 2).round(), // 2x for retina displays
              errorBuilder: (context, error, stackTrace) =>
                  _buildDefaultCover(),
            ),
          ),
        );
      }
    }
    return _buildDefaultCover();
  }

  Widget _buildGroupCover(double coverSize) {
    if (widget.group.coverPath != null) {
      final coverFile = File(widget.group.coverPath!);
      if (coverFile.existsSync()) {
        return RepaintBoundary(
          child: ClipRRect(
            borderRadius: BorderRadius.circular(8),
            child: Image.file(
              coverFile,
              width: coverSize,
              height: coverSize,
              fit: BoxFit.cover,
              cacheWidth: (coverSize * 2).round(),
              errorBuilder: (context, error, stackTrace) =>
                  _buildDefaultCover(),
            ),
          ),
        );
      }
    }
    return _buildDefaultCover();
  }

  Widget _buildDefaultCover() {
    const coverSize = 285.0;
    return Container(
      width: coverSize,
      height: coverSize,
      decoration: BoxDecoration(
        color: Colors.grey[300],
        borderRadius: BorderRadius.circular(8),
      ),
      child: const Icon(Icons.audiotrack, size: 80, color: Colors.grey),
    );
  }

  Widget _buildGroupProgressIndicator(PlayerStateModel state) {
    final totalTracks = widget.group.files.length;
    final currentTrack = state.currentIndex + 1;
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

  Widget _buildProgressSlider(PlayerStateModel state) {
    final hasDuration = state.duration > 0;
    final hasError = state.error != null;

    // Calculate progress - use local slider value if dragging, otherwise use actual position
    final progress = _isDragging && _sliderValue != null
        ? _sliderValue!.clamp(0.0, 1.0)
        : (hasDuration ? state.currentPosition / state.duration : 0.0);

    // Enable seek if we have duration and no errors
    // Allow seeking even during loading for better UX
    final canSeek = hasDuration && !hasError;

    return Slider(
      value: progress.clamp(0.0, 1.0),
      onChanged: canSeek ? _onSliderChanged : null,
      onChangeStart: canSeek ? _onSliderStart : null,
      onChangeEnd: canSeek ? _onSliderEnd : null,
    );
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

    // Perform actual seek
    ref.read(playerStateProvider.notifier).seek(position);

    // Reset local state
    setState(() {
      _isDragging = false;
      _sliderValue = null;
    });
  }

  /// Waits for player to be ready (playbackState == 2).
  ///
  /// This ensures that seek controls are enabled only when player is fully initialized.
  Future<void> _waitForPlayerReady() async {
    var attempts = 0;
    const maxAttempts = 50; // 5 seconds max wait
    while (attempts < maxAttempts && mounted) {
      final state = ref.read(playerStateProvider);
      if (state.playbackState == 2) {
        // Player is ready
        return;
      }
      if (state.error != null) {
        // Error occurred, don't wait further
        return;
      }
      await Future.delayed(const Duration(milliseconds: 100));
      attempts++;
    }
    // If we reach here, player didn't become ready in time
    // This is not necessarily an error - player might still be loading
  }

  Widget _buildPlaybackControls(PlayerStateModel state) {
    final isLoading = state.playbackState == 1; // 1 = buffering

    return Column(
      children: [
        // Main controls row
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            // Previous track button
            IconButton(
              icon: const Icon(Icons.skip_previous),
              iconSize: 32,
              onPressed: state.currentIndex > 0 ? _prevTrack : null,
              tooltip: 'Previous track',
            ),
            const SizedBox(width: 8),
            // Rewind button (-15 seconds)
            IconButton(
              icon: const Icon(Icons.replay_10),
              iconSize: 28,
              onPressed: isLoading ? null : () => _rewind(15),
              tooltip: 'Rewind 15 seconds',
            ),
            const SizedBox(width: 8),
            // Play/Pause button
            IconButton(
              icon: Icon(state.isPlaying ? Icons.pause : Icons.play_arrow),
              iconSize: 48,
              onPressed: isLoading ? null : _playPause,
              tooltip: state.isPlaying ? 'Pause' : 'Play',
            ),
            const SizedBox(width: 8),
            // Forward button (+30 seconds)
            IconButton(
              icon: const Icon(Icons.forward_30),
              iconSize: 28,
              onPressed: isLoading ? null : () => _forward(30),
              tooltip: 'Forward 30 seconds',
            ),
            const SizedBox(width: 8),
            // Next track button
            IconButton(
              icon: const Icon(Icons.skip_next),
              iconSize: 32,
              onPressed: state.currentIndex < widget.group.files.length - 1
                  ? _nextTrack
                  : null,
              tooltip: 'Next track',
            ),
          ],
        ),
      ],
    );
  }

  /// Rewinds playback by specified seconds.
  void _rewind(int seconds) {
    final state = ref.read(playerStateProvider);
    final currentPosition = Duration(milliseconds: state.currentPosition);
    final newPosition = currentPosition - Duration(seconds: seconds);
    ref.read(playerStateProvider.notifier).seek(
          newPosition.isNegative ? Duration.zero : newPosition,
        );
  }

  /// Forwards playback by specified seconds.
  void _forward(int seconds) {
    final state = ref.read(playerStateProvider);
    final currentPosition = Duration(milliseconds: state.currentPosition);
    final duration = Duration(milliseconds: state.duration);
    final newPosition = currentPosition + Duration(seconds: seconds);
    ref.read(playerStateProvider.notifier).seek(
          newPosition > duration ? duration : newPosition,
        );
  }

  Widget _buildTrackList(PlayerStateModel state) {
    final hasMultiFolder = widget.group.hasMultiFolderStructure;
    return Column(
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
            final isCurrent = index == state.currentIndex;
            // Use display name with part prefix if multi-folder structure detected
            final displayName = hasMultiFolder
                ? file.getDisplayNameWithPart(widget.group.groupPath)
                : file.displayName;
            return ListTile(
              leading: Icon(
                isCurrent ? Icons.audiotrack : Icons.music_note,
                color: isCurrent ? Theme.of(context).primaryColor : null,
              ),
              title: Text(
                displayName,
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
  }

  /// Builds a row with speed, repeat and sleep timer controls.
  Widget _buildControlsRow(PlayerStateModel state) => Row(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: [
          // Speed control
          _buildSpeedControl(state),
          // Repeat mode control
          _buildRepeatControl(),
          // Sleep timer control
          _buildSleepTimerControl(),
        ],
      );

  Widget _buildSpeedControl(PlayerStateModel state) => PopupMenuButton<double>(
        tooltip: 'Playback speed',
        itemBuilder: (context) =>
            [0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0].map((speed) {
          final isSelected = (state.playbackSpeed - speed).abs() < 0.01;
          return PopupMenuItem<double>(
            value: speed,
            child: Row(
              children: [
                if (isSelected)
                  const Icon(Icons.check, size: 20)
                else
                  const SizedBox(width: 20),
                const SizedBox(width: 8),
                Text('${speed}x'),
              ],
            ),
          );
        }).toList(),
        onSelected: _setSpeed,
        child: Chip(
          avatar: const Icon(Icons.speed, size: 18),
          label: Text('${state.playbackSpeed}x'),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        ),
      );

  Widget _buildRepeatControl() {
    final settings = ref.watch(playbackSettingsProvider);
    final repeatMode = settings.repeatMode;

    return IconButton(
      icon: Icon(
        repeatMode == RepeatMode.track
            ? Icons.repeat_one
            : repeatMode == RepeatMode.playlist
                ? Icons.repeat
                : Icons.repeat_outlined,
        color: repeatMode != RepeatMode.none
            ? Theme.of(context).primaryColor
            : null,
      ),
      onPressed: () async {
        final settingsNotifier = ref.read(playbackSettingsProvider.notifier);
        await settingsNotifier.cycleRepeatMode();
        // Update saved state with new repeat mode
        final newSettings = ref.read(playbackSettingsProvider);
        await ref.read(playerStateProvider.notifier).updateSavedStateSettings(
              repeatMode: newSettings.repeatMode.index,
            );
      },
      tooltip: _getRepeatModeTooltip(repeatMode),
    );
  }

  String _getRepeatModeTooltip(RepeatMode mode) {
    switch (mode) {
      case RepeatMode.none:
        return 'No repeat';
      case RepeatMode.track:
        return 'Repeat track';
      case RepeatMode.playlist:
        return 'Repeat playlist';
    }
  }

  Widget _buildSleepTimerControl() {
    final isActive = _sleepTimerService.isActive;
    final remainingSeconds = _sleepTimerService.remainingSeconds;

    if (isActive && remainingSeconds != null) {
      return PopupMenuButton<Duration?>(
        tooltip:
            'Sleep timer: ${_formatDuration(Duration(seconds: remainingSeconds))}',
        onSelected: (duration) async {
          if (duration == null) {
            await _sleepTimerService.cancelTimer();
            _stopSleepTimerUpdates();
            await ref
                .read(playerStateProvider.notifier)
                .updateSavedStateSettings();
            if (mounted) {
              setState(() {});
            }
          }
        },
        itemBuilder: (context) {
          final localizations = AppLocalizations.of(context);
          return [
            PopupMenuItem<Duration?>(
              child: Text(localizations?.cancelTimerButton ?? 'Cancel timer'),
            ),
          ];
        },
        child: Chip(
          avatar: const Icon(Icons.timer, size: 18),
          label: Text(_formatDuration(Duration(seconds: remainingSeconds))),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          backgroundColor:
              Theme.of(context).primaryColor.withValues(alpha: 0.1),
        ),
      );
    }

    if (isActive && remainingSeconds == null) {
      return PopupMenuButton<Duration?>(
        tooltip: AppLocalizations.of(context)?.atEndOfChapterLabel ??
            'Sleep timer: At end of chapter',
        onSelected: (duration) async {
          if (duration == null) {
            await _sleepTimerService.cancelTimer();
            _stopSleepTimerUpdates();
            await ref
                .read(playerStateProvider.notifier)
                .updateSavedStateSettings();
            if (mounted) {
              setState(() {});
            }
          }
        },
        itemBuilder: (context) {
          final localizations = AppLocalizations.of(context);
          return [
            PopupMenuItem<Duration?>(
              child: Text(localizations?.cancelTimerButton ?? 'Cancel timer'),
            ),
          ];
        },
        child: Chip(
          avatar: const Icon(Icons.timer, size: 18),
          label: Text(AppLocalizations.of(context)?.endOfChapterLabel ??
              'End of chapter'),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          backgroundColor:
              Theme.of(context).primaryColor.withValues(alpha: 0.1),
        ),
      );
    }

    return PopupMenuButton<Duration?>(
      tooltip: 'Set sleep timer',
      onSelected: (duration) async {
        if (duration == null) {
          await _sleepTimerService.cancelTimer();
        } else if (duration == const Duration(seconds: -1)) {
          // Special value for "at end of chapter"
          await _sleepTimerService.startTimerAtEndOfChapter(() {
            if (mounted) {
              ref.read(playerStateProvider.notifier).pause();
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(
                  content: Text('Sleep timer: Playback paused'),
                ),
              );
            }
          });
        } else {
          await _sleepTimerService.startTimer(duration, () {
            if (mounted) {
              ref.read(playerStateProvider.notifier).pause();
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(
                  content: Text('Sleep timer: Playback paused'),
                ),
              );
            }
          });
        }
        if (mounted) {
          setState(() {});
          _startSleepTimerUpdates();
          // Update saved state with sleep timer
          final remainingSeconds = _sleepTimerService.remainingSeconds;
          await ref.read(playerStateProvider.notifier).updateSavedStateSettings(
                sleepTimerRemainingSeconds: remainingSeconds,
              );
        }
      },
      itemBuilder: (context) => [
        const PopupMenuItem<Duration?>(
          value: Duration(minutes: 10),
          child: Text('10 minutes'),
        ),
        const PopupMenuItem<Duration?>(
          value: Duration(minutes: 15),
          child: Text('15 minutes'),
        ),
        const PopupMenuItem<Duration?>(
          value: Duration(minutes: 30),
          child: Text('30 minutes'),
        ),
        const PopupMenuItem<Duration?>(
          value: Duration(minutes: 45),
          child: Text('45 minutes'),
        ),
        const PopupMenuItem<Duration?>(
          value: Duration(hours: 1),
          child: Text('1 hour'),
        ),
        PopupMenuItem<Duration?>(
          value: const Duration(seconds: -1), // Special value
          child: Text(AppLocalizations.of(context)?.atEndOfChapterLabel ??
              'At end of chapter'),
        ),
      ],
      child: const Chip(
        avatar: Icon(Icons.timer_outlined, size: 18),
        label: Text('Timer'),
        padding: EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      ),
    );
  }

  /// Starts periodic updates for sleep timer display.
  void _startSleepTimerUpdates() {
    _stopSleepTimerUpdates();
    _sleepTimerUpdateTimer = Timer.periodic(
      const Duration(seconds: 1),
      (_) {
        if (mounted && _sleepTimerService.isActive) {
          setState(() {});
        } else {
          _stopSleepTimerUpdates();
        }
      },
    );
  }

  /// Stops sleep timer updates.
  void _stopSleepTimerUpdates() {
    _sleepTimerUpdateTimer?.cancel();
    _sleepTimerUpdateTimer = null;
  }

  @override
  void dispose() {
    _stopSleepTimerUpdates();
    _sleepTimerService.dispose();
    super.dispose();
  }

  String _formatDuration(Duration duration) {
    final minutes = duration.inMinutes;
    final seconds = duration.inSeconds % 60;
    return '${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}';
  }
}
