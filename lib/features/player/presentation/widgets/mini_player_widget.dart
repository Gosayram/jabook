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
import 'package:jabook/core/player/player_state_provider.dart';

/// Mini player widget displayed at the bottom of the screen.
///
/// This widget shows the current track information, playback progress,
/// and basic controls. Tapping on it opens the full player screen.
class MiniPlayerWidget extends ConsumerWidget {
  /// Creates a new MiniPlayerWidget instance.
  const MiniPlayerWidget({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final playerState = ref.watch(playerStateProvider);

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
      child: Container(
        height: 64,
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
            // Cover image
            _buildCoverImage(context, playerState.currentCoverPath),
            const SizedBox(width: 12),
            // Track info and progress
            Expanded(
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
            const SizedBox(width: 8),
            // Play/Pause button
            IconButton(
              icon: Icon(
                playerState.isPlaying ? Icons.pause : Icons.play_arrow,
              ),
              onPressed: () => _togglePlayPause(ref),
              tooltip: playerState.isPlaying ? 'Pause' : 'Play',
            ),
            const SizedBox(width: 8),
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
          width: 64,
          height: 64,
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

  void _openFullPlayer(
      BuildContext context, WidgetRef ref, PlayerStateModel state) {
    final currentGroup = ref.read(currentAudiobookGroupProvider);
    if (currentGroup != null) {
      context.push('/local-player', extra: currentGroup);
    }
  }
}
