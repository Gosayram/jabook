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

import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:jabook/core/di/providers/player_providers.dart';
import 'package:jabook/core/player/native_audio_player.dart';
import 'package:jabook/core/player/player_state_provider.dart';

/// Mini player widget displayed at the bottom of the screen.
///
/// This widget shows the current track information, playback progress,
/// and basic controls. Tapping on it opens the full player screen.
class MiniPlayerWidget extends ConsumerStatefulWidget {
  /// Creates a new MiniPlayerWidget instance.
  const MiniPlayerWidget({super.key});

  @override
  ConsumerState<MiniPlayerWidget> createState() => _MiniPlayerWidgetState();
}

class _MiniPlayerWidgetState extends ConsumerState<MiniPlayerWidget> {
  String? _embeddedArtworkPath;
  String?
      _groupArtworkPath; // First found artwork path for the group (global setting)

  @override
  void initState() {
    super.initState();
    // Check for embedded artwork after first frame
    WidgetsBinding.instance.addPostFrameCallback((_) {
      // Load first available artwork from group (global setting)
      _loadGroupArtwork();
      // Check immediately and also with delay for async updates
      _checkEmbeddedArtwork();
      Future.delayed(const Duration(milliseconds: 500), _checkEmbeddedArtwork);
    });
  }

  @override
  void didUpdateWidget(MiniPlayerWidget oldWidget) {
    super.didUpdateWidget(oldWidget);
    // Re-check embedded artwork when widget updates (e.g., track changes)
    _checkEmbeddedArtwork();
  }

  /// Loads the first available artwork from group files (global setting).
  /// This ensures we always use the first found artwork, even if the first file doesn't have one.
  Future<void> _loadGroupArtwork() async {
    try {
      final currentGroup = ref.read(currentAudiobookGroupProvider);
      if (currentGroup == null) return;

      // Try group coverPath first
      if (currentGroup.coverPath != null) {
        final coverFile = File(currentGroup.coverPath!);
        if (coverFile.existsSync()) {
          if (mounted) {
            setState(() {
              _groupArtworkPath = currentGroup.coverPath;
            });
          }
          return;
        }
      }

      // Try to extract artwork from files in order until found
      final nativePlayer = NativeAudioPlayer();
      String? artworkPath;

      for (final file in currentGroup.files) {
        try {
          artworkPath = await nativePlayer.extractArtworkFromFile(
            file.filePath,
          );

          if (artworkPath != null) {
            final artworkFile = File(artworkPath);
            if (artworkFile.existsSync()) {
              // Found artwork, stop searching and use this as group artwork
              if (mounted) {
                setState(() {
                  _groupArtworkPath = artworkPath;
                });
              }
              return;
            } else {
              artworkPath = null;
            }
          }
        } on Exception catch (e) {
          // Continue to next file if this one fails
          debugPrint('Failed to extract artwork from ${file.filePath}: $e');
          artworkPath = null;
        }
      }
    } on Exception {
      // Silently fail - embedded artwork is optional
    }
  }

  Future<void> _checkEmbeddedArtwork() async {
    // Use group artwork (first found) as primary source (global setting)
    // Only check current track artwork if group artwork is not available
    if (_groupArtworkPath != null) {
      final artworkFile = File(_groupArtworkPath!);
      if (artworkFile.existsSync()) {
        if (mounted) {
          setState(() {
            _embeddedArtworkPath = _groupArtworkPath;
          });
        }
        return;
      }
    }

    try {
      // Fallback 1: Get currentCoverPath from playerState (updated by player_state_provider)
      final playerState = ref.read(playerStateProvider);
      if (playerState.currentCoverPath != null) {
        final coverFile = File(playerState.currentCoverPath!);
        if (coverFile.existsSync()) {
          if (mounted) {
            setState(() {
              _embeddedArtworkPath = playerState.currentCoverPath;
            });
          }
          return;
        }
      }

      // Fallback 2: Get current media item info which includes artworkPath
      // Use Media3PlayerService through provider to ensure singleton instance
      final playerService = ref.read(media3PlayerServiceProvider);
      final mediaInfo = await playerService.getCurrentMediaItemInfo();
      final artworkPath = mediaInfo['artworkPath'] as String?;
      if (artworkPath != null && artworkPath.isNotEmpty) {
        final artworkFile = File(artworkPath);
        if (artworkFile.existsSync()) {
          if (mounted) {
            setState(() {
              _embeddedArtworkPath = artworkPath;
            });
          }
        }
      } else {
        // Clear embedded artwork if not available
        if (mounted) {
          setState(() {
            _embeddedArtworkPath = null;
          });
        }
      }
    } on Exception {
      // Silently fail - embedded artwork is optional
      if (mounted) {
        setState(() {
          _embeddedArtworkPath = null;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final playerState = ref.watch(playerStateProvider);

    // Listen to track changes and group changes
    ref
      ..listen(playerStateProvider, (previous, next) {
        // When playback starts (transitions from idle to ready/playing), load artwork
        if (previous?.playbackState == 0 && next.playbackState != 0) {
          // Player just started - load group artwork and check embedded artwork
          _loadGroupArtwork();
          Future.delayed(
              const Duration(milliseconds: 100), _checkEmbeddedArtwork);
        }
        // Check if track index changed (new track)
        if (previous?.currentIndex != next.currentIndex) {
          _checkEmbeddedArtwork();
        }
        // Update if cover path changed (but prioritize group artwork)
        if (previous?.currentCoverPath != next.currentCoverPath) {
          if (next.currentCoverPath != null) {
            // If we don't have group artwork, use currentCoverPath
            if (_groupArtworkPath == null) {
              setState(() {
                _embeddedArtworkPath = next.currentCoverPath;
              });
            }
          } else {
            // If cover path was cleared, try to reload
            _checkEmbeddedArtwork();
          }
        }
      })
      ..listen(currentAudiobookGroupProvider, (previous, next) {
        if (previous?.groupPath != next?.groupPath) {
          // Group changed, reload group artwork
          setState(() {
            _groupArtworkPath = null;
            _embeddedArtworkPath = null;
          });
          // Load group artwork after state is reset
          _loadGroupArtwork();
        }
      });

    // Show mini player only when there's an active track (not idle)
    if (playerState.playbackState == 0) {
      // 0 = idle
      return const SizedBox.shrink();
    }

    return GestureDetector(
      onTap: () => _openFullPlayer(context, ref, playerState),
      onVerticalDragEnd: (details) {
        // Swipe up to expand to full player
        if (details.primaryVelocity != null &&
            details.primaryVelocity! < -500) {
          _openFullPlayer(context, ref, playerState);
        }
      },
      onHorizontalDragEnd: (details) {
        // Swipe left for next track, swipe right for previous track
        if (details.primaryVelocity != null) {
          if (details.primaryVelocity! < -500) {
            // Swipe left - next track
            _skipNext(ref);
          } else if (details.primaryVelocity! > 500) {
            // Swipe right - previous track
            _skipPrevious(ref);
          }
        }
      },
      child: Container(
        height: 52, // Reduced from 64 (20% decrease)
        decoration: BoxDecoration(
          color: Theme.of(context).cardColor,
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.1),
              blurRadius: 4,
              offset: const Offset(0, -2),
            ),
          ],
        ),
        child: Row(
          children: [
            // Cover image - prioritize group artwork, then embedded artwork, then currentCoverPath
            _buildCoverImage(
              context,
              _groupArtworkPath ??
                  _embeddedArtworkPath ??
                  playerState.currentCoverPath,
            ),
            const SizedBox(width: 12),
            // Track info and progress
            Flexible(
              flex: 2,
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Title
                  Text(
                    playerState.currentTitle ?? 'Unknown',
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                          fontWeight: FontWeight.w500,
                        ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: 2),
                  // Artist
                  if (playerState.currentArtist != null)
                    Text(
                      playerState.currentArtist!,
                      style: Theme.of(context).textTheme.bodySmall,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  const SizedBox(height: 4),
                  // Progress bar
                  _buildProgressBar(context, playerState),
                ],
              ),
            ),
            // Player controls - centered and expanding to fill available space
            Flexible(
              flex: 3,
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  // Previous track button
                  IconButton(
                    icon: const Icon(Icons.skip_previous),
                    onPressed: () => _skipPrevious(ref),
                    tooltip: 'Previous track',
                  ),
                  // Play/Pause button
                  IconButton(
                    icon: Icon(
                      playerState.isPlaying ? Icons.pause : Icons.play_arrow,
                    ),
                    onPressed: () => _togglePlayPause(ref),
                    tooltip: playerState.isPlaying ? 'Pause' : 'Play',
                  ),
                  // Next track button
                  IconButton(
                    icon: const Icon(Icons.skip_next),
                    onPressed: () => _skipNext(ref),
                    tooltip: 'Next track',
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildCoverImage(BuildContext context, String? coverPath) {
    if (coverPath != null) {
      final coverFile = File(coverPath);
      if (coverFile.existsSync()) {
        return Container(
          width: 52,
          height: 52,
          decoration: BoxDecoration(
            color: Colors.grey[300],
          ),
          child: Image.file(
            coverFile,
            width: 64,
            height: 64,
            fit: BoxFit.cover,
            cacheWidth: 128, // 2x for retina displays
            errorBuilder: (_, __, ___) => _buildDefaultCover(),
          ),
        );
      }
    }
    return _buildDefaultCover();
  }

  Widget _buildDefaultCover() => Container(
        width: 64,
        height: 64,
        decoration: BoxDecoration(
          color: Colors.grey[300],
        ),
        child: const Icon(Icons.audiotrack, size: 32, color: Colors.grey),
      );

  Widget _buildProgressBar(BuildContext context, PlayerStateModel state) {
    final progress =
        state.duration > 0 ? state.currentPosition / state.duration : 0.0;

    return ClipRRect(
      borderRadius: BorderRadius.circular(2),
      child: LinearProgressIndicator(
        value: progress.clamp(0.0, 1.0),
        minHeight: 2,
        backgroundColor: Colors.grey[300],
        valueColor: AlwaysStoppedAnimation<Color>(
          Theme.of(context).primaryColor,
        ),
      ),
    );
  }

  void _togglePlayPause(WidgetRef ref) {
    final playerNotifier = ref.read(playerStateProvider.notifier);
    final state = ref.read(playerStateProvider);
    if (state.isPlaying) {
      playerNotifier.pause();
    } else {
      playerNotifier.play();
    }
  }

  void _skipPrevious(WidgetRef ref) {
    ref.read(playerStateProvider.notifier).previous();
  }

  void _skipNext(WidgetRef ref) {
    ref.read(playerStateProvider.notifier).next();
  }

  void _openFullPlayer(
      BuildContext context, WidgetRef ref, PlayerStateModel state) {
    final currentGroup = ref.read(currentAudiobookGroupProvider);
    if (currentGroup != null) {
      context.push('/local-player', extra: currentGroup);
    }
  }
}
