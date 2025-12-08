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
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:jabook/core/di/providers/player_providers.dart';
import 'package:jabook/core/di/providers/simple_player_providers.dart';
import 'package:jabook/core/library/cover_fallback_service.dart';
import 'package:jabook/core/performance/performance_service.dart';
import 'package:jabook/core/player/native_audio_player.dart';
import 'package:jabook/core/player/player_state_provider.dart';
import 'package:jabook/core/player/simple_player_provider.dart';

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

class _MiniPlayerWidgetState extends ConsumerState<MiniPlayerWidget>
    with SingleTickerProviderStateMixin {
  String? _embeddedArtworkPath;
  String?
      _groupArtworkPath; // First found artwork path for the group (global setting)

  // Debounce timer for artwork updates to prevent excessive reloads
  Timer? _artworkUpdateTimer;

  // Visual feedback state for swipe gestures
  double _swipeOpacity = 1.0;
  String? _swipeDirection; // 'up', 'down', 'left', 'right', null
  late AnimationController _swipeAnimationController;
  late Animation<double> _swipeOpacityAnimation;

  @override
  void initState() {
    super.initState();
    // Initialize swipe animation controller
    _swipeAnimationController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 200),
    );
    _swipeOpacityAnimation = Tween<double>(begin: 1.0, end: 0.6).animate(
      CurvedAnimation(
        parent: _swipeAnimationController,
        curve: Curves.easeInOut,
      ),
    );
    _swipeOpacityAnimation.addListener(() {
      if (mounted) {
        setState(() {
          _swipeOpacity = _swipeOpacityAnimation.value;
        });
      }
    });

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
    // Use debounce to prevent excessive updates
    _debouncedCheckEmbeddedArtwork();
  }

  @override
  void dispose() {
    _artworkUpdateTimer?.cancel();
    _swipeAnimationController.dispose();
    super.dispose();
  }

  /// Debounced version of _checkEmbeddedArtwork to prevent excessive updates.
  void _debouncedCheckEmbeddedArtwork() {
    _artworkUpdateTimer?.cancel();
    _artworkUpdateTimer = Timer(const Duration(milliseconds: 200), () {
      if (mounted) {
        _checkEmbeddedArtwork();
      }
    });
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
                // Update global state so other widgets (LocalPlayer) see this cover
                final updatedGroup =
                    currentGroup.copyWith(coverPath: artworkPath);
                ref.read(currentAudiobookGroupProvider.notifier).state =
                    updatedGroup;
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

      // If no artwork found, try online fallback
      if (artworkPath == null && currentGroup.coverPath == null) {
        try {
          const fallbackService = CoverFallbackService();
          final fallbackPath = await fallbackService.fetchCoverFromOnline(
            currentGroup.groupName,
            torrentId: currentGroup.torrentId,
          );
          if (fallbackPath != null && mounted) {
            final fallbackFile = File(fallbackPath);
            if (fallbackFile.existsSync()) {
              setState(() {
                _groupArtworkPath = fallbackPath;
              });
              // Update global state so other widgets (LocalPlayer) see this cover
              final updatedGroup =
                  currentGroup.copyWith(coverPath: fallbackPath);
              ref.read(currentAudiobookGroupProvider.notifier).state =
                  updatedGroup;
            }
          }
        } on Exception {
          // Silently fail - online fallback is optional
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
      // Fallback 1: Get currentCoverPath from playerState (updated by simple_player_provider)
      final playerState = ref.read(simplePlayerProvider);
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
    final playerState = ref.watch(simplePlayerProvider);

    // Listen to track changes and group changes
    ref
      ..listen(simplePlayerProvider, (previous, next) {
        // When playback starts (transitions from idle to ready/playing), load artwork
        if (previous?.playbackState == 0 && next.playbackState != 0) {
          // Player just started - load group artwork and check embedded artwork
          _loadGroupArtwork();
          Future.delayed(
              const Duration(milliseconds: 100), _checkEmbeddedArtwork);
        }
        // Check if track index changed (new track)
        if (previous?.currentTrackIndex != next.currentTrackIndex) {
          _debouncedCheckEmbeddedArtwork();
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
            _debouncedCheckEmbeddedArtwork();
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

    final performanceClassAsync = ref.watch(performanceClassProvider);
    final isLowEnd = performanceClassAsync.value == PerformanceClass.low;

    // AnimatedSize for smooth appearance/disappearance
    // RepaintBoundary isolates the entire player content for better performance
    // Disable animation for low-end devices
    return RepaintBoundary(
      child: isLowEnd
          ? (playerState.playbackState == 0
              ? const SizedBox.shrink()
              : _buildPlayerContent(context, playerState, isLowEnd))
          : AnimatedSize(
              duration: const Duration(milliseconds: 300),
              curve: Curves.easeInOut,
              child: playerState.playbackState == 0
                  ? const SizedBox.shrink()
                  : _buildPlayerContent(context, playerState, isLowEnd),
            ),
    );
  }

  Widget _buildPlayerContent(
      BuildContext context, SimplePlayerState playerState, bool isLowEnd) {
    final coverPath = _groupArtworkPath ??
        _embeddedArtworkPath ??
        playerState.currentCoverPath;

    return GestureDetector(
      onTap: () {
        HapticFeedback.selectionClick();
        _openFullPlayer(context, ref);
      },
      onVerticalDragStart: (details) {
        // Visual feedback: start swipe animation
        _swipeAnimationController.forward();
        HapticFeedback.lightImpact();
      },
      onVerticalDragUpdate: (details) {
        // Visual feedback: show direction indicator
        if (details.primaryDelta != null) {
          if (details.primaryDelta! < 0) {
            // Swiping up
            setState(() {
              _swipeDirection = 'up';
            });
          } else {
            // Swiping down
            setState(() {
              _swipeDirection = 'down';
            });
          }
        }
      },
      onVerticalDragEnd: (details) {
        // Reset visual feedback
        _swipeAnimationController.reverse();
        setState(() {
          _swipeDirection = null;
        });

        if (details.primaryVelocity != null) {
          if (details.primaryVelocity! < -500) {
            // Swipe up to expand to full player
            HapticFeedback.mediumImpact();
            _openFullPlayer(context, ref);
          } else if (details.primaryVelocity! > 500) {
            // Swipe down to stop playback
            HapticFeedback.mediumImpact();
            _stopPlayback(ref);
          }
        }
      },
      onHorizontalDragStart: (details) {
        // Visual feedback: start swipe animation
        _swipeAnimationController.forward();
        HapticFeedback.lightImpact();
      },
      onHorizontalDragUpdate: (details) {
        // Visual feedback: show direction indicator
        if (details.primaryDelta != null) {
          if (details.primaryDelta! < 0) {
            // Swiping left
            setState(() {
              _swipeDirection = 'left';
            });
          } else {
            // Swiping right
            setState(() {
              _swipeDirection = 'right';
            });
          }
        }
      },
      onHorizontalDragEnd: (details) {
        // Reset visual feedback
        _swipeAnimationController.reverse();
        setState(() {
          _swipeDirection = null;
        });

        // Swipe left for next track, swipe right for previous track
        if (details.primaryVelocity != null) {
          if (details.primaryVelocity! < -500) {
            // Swipe left - next track
            HapticFeedback.lightImpact();
            _skipNext(ref);
          } else if (details.primaryVelocity! > 500) {
            // Swipe right - previous track
            HapticFeedback.lightImpact();
            _skipPrevious(ref);
          }
        }
      },
      child: Opacity(
        opacity: _swipeOpacity,
        child: Container(
          height: 52, // Reduced from 64 (20% decrease)
          decoration: BoxDecoration(
            // Subtle gradient for visual enhancement
            gradient: LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [
                Theme.of(context).cardColor,
                Theme.of(context).cardColor.withValues(alpha: 0.95),
              ],
            ),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.1),
                blurRadius: 4,
                offset: const Offset(0, -2),
              ),
            ],
          ),
          // Visual indicator for swipe direction
          child: Stack(
            children: [
              // Main content
              // Main content
              _buildMainContent(context, playerState, coverPath, isLowEnd),
              // Swipe direction indicator overlay
              if (_swipeDirection != null)
                _buildSwipeIndicator(context, _swipeDirection!),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildMainContent(
    BuildContext context,
    SimplePlayerState playerState,
    String? coverPath,
    bool isLowEnd,
  ) =>
      Row(
        children: [
          // Cover image with AnimatedSwitcher for smooth transitions
          // RepaintBoundary isolates cover image repaints for better performance
          RepaintBoundary(
            child: isLowEnd
                ? _buildCoverImage(
                    context,
                    coverPath,
                    key: ValueKey(coverPath),
                  )
                : AnimatedSwitcher(
                    duration: const Duration(milliseconds: 300),
                    transitionBuilder: (child, animation) => FadeTransition(
                      opacity: animation,
                      child: child,
                    ),
                    child: _buildCoverImage(
                      context,
                      coverPath,
                      key: ValueKey(coverPath),
                    ),
                  ),
          ),
          const SizedBox(width: 12),
          // Track info and progress with AnimatedSwitcher for metadata
          Flexible(
            flex: 2,
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Title with fade animation
                // Title with fade animation
                isLowEnd
                    ? Text(
                        playerState.currentTitle ?? 'Unknown',
                        key: ValueKey(playerState.currentTitle),
                        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                              fontWeight: FontWeight.w500,
                            ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      )
                    : AnimatedSwitcher(
                        duration: const Duration(milliseconds: 200),
                        transitionBuilder: (child, animation) => FadeTransition(
                          opacity: animation,
                          child: child,
                        ),
                        child: Text(
                          playerState.currentTitle ?? 'Unknown',
                          key: ValueKey(playerState.currentTitle),
                          style:
                              Theme.of(context).textTheme.bodyMedium?.copyWith(
                                    fontWeight: FontWeight.w500,
                                  ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                const SizedBox(height: 2),
                // Artist with fade animation
                if (playerState.currentArtist != null)
                  // Artist with fade animation
                  if (playerState.currentArtist != null)
                    isLowEnd
                        ? Text(
                            playerState.currentArtist!,
                            key: ValueKey(playerState.currentArtist),
                            style: Theme.of(context).textTheme.bodySmall,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          )
                        : AnimatedSwitcher(
                            duration: const Duration(milliseconds: 200),
                            transitionBuilder: (child, animation) =>
                                FadeTransition(
                              opacity: animation,
                              child: child,
                            ),
                            child: Text(
                              playerState.currentArtist!,
                              key: ValueKey(playerState.currentArtist),
                              style: Theme.of(context).textTheme.bodySmall,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                const SizedBox(height: 4),
                // Progress bar with RepaintBoundary for performance
                RepaintBoundary(
                  child: _buildProgressBar(context, playerState),
                ),
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
                  onPressed: () {
                    HapticFeedback.selectionClick();
                    _skipPrevious(ref);
                  },
                  tooltip: 'Previous track',
                ),
                // Play/Pause button with AnimatedSwitcher
                // Play/Pause button with AnimatedSwitcher
                isLowEnd
                    ? IconButton(
                        key: ValueKey(playerState.isPlaying),
                        icon: Icon(
                          playerState.isPlaying
                              ? Icons.pause
                              : Icons.play_arrow,
                        ),
                        onPressed: () {
                          HapticFeedback.selectionClick();
                          _togglePlayPause(ref);
                        },
                        tooltip: playerState.isPlaying ? 'Pause' : 'Play',
                      )
                    : AnimatedSwitcher(
                        duration: const Duration(milliseconds: 200),
                        transitionBuilder: (child, animation) =>
                            ScaleTransition(
                          scale: animation,
                          child: child,
                        ),
                        child: IconButton(
                          key: ValueKey(playerState.isPlaying),
                          icon: Icon(
                            playerState.isPlaying
                                ? Icons.pause
                                : Icons.play_arrow,
                          ),
                          onPressed: () {
                            HapticFeedback.selectionClick();
                            _togglePlayPause(ref);
                          },
                          tooltip: playerState.isPlaying ? 'Pause' : 'Play',
                        ),
                      ),
                // Next track button
                IconButton(
                  icon: const Icon(Icons.skip_next),
                  onPressed: () {
                    HapticFeedback.selectionClick();
                    _skipNext(ref);
                  },
                  tooltip: 'Next track',
                ),
              ],
            ),
          ),
        ],
      );

  Widget _buildSwipeIndicator(BuildContext context, String direction) {
    IconData icon;
    String label;
    Alignment alignment;

    switch (direction) {
      case 'up':
        icon = Icons.keyboard_arrow_up;
        label = 'Open player';
        alignment = Alignment.topCenter;
        break;
      case 'down':
        icon = Icons.keyboard_arrow_down;
        label = 'Stop';
        alignment = Alignment.bottomCenter;
        break;
      case 'left':
        icon = Icons.keyboard_arrow_left;
        label = 'Next';
        alignment = Alignment.centerLeft;
        break;
      case 'right':
        icon = Icons.keyboard_arrow_right;
        label = 'Previous';
        alignment = Alignment.centerRight;
        break;
      default:
        icon = Icons.arrow_forward;
        label = '';
        alignment = Alignment.center;
    }

    return Positioned.fill(
      child: Align(
        alignment: alignment,
        child: Container(
          margin: const EdgeInsets.all(8),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
          decoration: BoxDecoration(
            color: Theme.of(context).colorScheme.primary.withValues(alpha: 0.9),
            borderRadius: BorderRadius.circular(16),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.2),
                blurRadius: 8,
                offset: const Offset(0, 2),
              ),
            ],
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(icon, color: Colors.white, size: 20),
              if (label.isNotEmpty) ...[
                const SizedBox(width: 4),
                Text(
                  label,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 12,
                    fontWeight: FontWeight.w500,
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildCoverImage(
    BuildContext context,
    String? coverPath, {
    Key? key,
  }) {
    if (coverPath != null) {
      final coverFile = File(coverPath);
      if (coverFile.existsSync()) {
        return Container(
          key: key,
          width: 52,
          height: 52,
          decoration: BoxDecoration(
            color: Colors.grey[300],
            borderRadius:
                BorderRadius.circular(4), // Material 3 rounded corners
          ),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: Image.file(
              coverFile,
              width: 52,
              height: 52,
              fit: BoxFit.cover,
              cacheWidth: 128, // 2x for retina displays
              errorBuilder: (_, __, ___) => _buildDefaultCover(key: key),
            ),
          ),
        );
      }
    }
    return _buildDefaultCover(key: key);
  }

  Widget _buildDefaultCover({Key? key}) => Container(
        key: key,
        width: 52,
        height: 52,
        decoration: const BoxDecoration(
          color: Color(0xFFE0E0E0), // Colors.grey[300] as const
          borderRadius: BorderRadius.all(
              Radius.circular(4)), // Material 3 rounded corners
        ),
        child: const Icon(Icons.audiotrack, size: 32, color: Colors.grey),
      );

  Widget _buildProgressBar(BuildContext context, SimplePlayerState state) {
    final progress =
        state.duration > 0 ? state.currentPosition / state.duration : 0.0;

    return ClipRRect(
      borderRadius: const BorderRadius.all(Radius.circular(2)),
      child: LinearProgressIndicator(
        value: progress.clamp(0.0, 1.0),
        minHeight: 2,
        backgroundColor: const Color(0xFFE0E0E0), // Colors.grey[300] as const
        valueColor: AlwaysStoppedAnimation<Color>(
          Theme.of(context).primaryColor,
        ),
      ),
    );
  }

  void _togglePlayPause(WidgetRef ref) {
    final playerNotifier = ref.read(simplePlayerProvider.notifier);
    final state = ref.read(simplePlayerProvider);
    if (state.isPlaying) {
      playerNotifier.pause();
    } else {
      playerNotifier.play();
    }
  }

  void _skipPrevious(WidgetRef ref) {
    ref.read(simplePlayerProvider.notifier).previous();
  }

  void _skipNext(WidgetRef ref) {
    ref.read(simplePlayerProvider.notifier).next();
  }

  void _stopPlayback(WidgetRef ref) {
    ref.read(simplePlayerProvider.notifier).stop();
  }

  void _openFullPlayer(BuildContext context, WidgetRef ref) {
    final currentGroup = ref.read(currentAudiobookGroupProvider);
    if (currentGroup != null) {
      context.push('/local-player', extra: currentGroup);
    }
  }
}
