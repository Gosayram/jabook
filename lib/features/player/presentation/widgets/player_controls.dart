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

import 'package:flutter/material.dart';
import 'package:jabook/core/utils/responsive_utils.dart';

/// Widget for player controls (play/pause, prev/next, speed).
///
/// This is a pure UI widget that receives callbacks for actions.
/// All business logic should be handled by the parent widget or provider.
class PlayerControls extends StatelessWidget {
  /// Creates a new PlayerControls instance.
  const PlayerControls({
    super.key,
    required this.isPlaying,
    required this.onPlayPause,
    required this.onPrevious,
    required this.onNext,
    required this.onSpeedChanged,
    required this.currentSpeed,
    this.canGoPrevious = true,
    this.canGoNext = true,
    this.previousTooltip,
    this.nextTooltip,
  });

  /// Whether audio is currently playing.
  final bool isPlaying;

  /// Callback for play/pause action.
  final VoidCallback onPlayPause;

  /// Callback for previous track action.
  final VoidCallback? onPrevious;

  /// Callback for next track action.
  final VoidCallback? onNext;

  /// Callback for speed change action.
  final ValueChanged<double> onSpeedChanged;

  /// Current playback speed.
  final double currentSpeed;

  /// Whether previous button should be enabled.
  final bool canGoPrevious;

  /// Whether next button should be enabled.
  final bool canGoNext;

  /// Tooltip for previous button.
  final String? previousTooltip;

  /// Tooltip for next button.
  final String? nextTooltip;

  @override
  Widget build(BuildContext context) => Padding(
        padding: EdgeInsets.only(
          left: ResponsiveUtils.getCompactPadding(context).left,
          right: ResponsiveUtils.getCompactPadding(context).right,
          top: ResponsiveUtils.getCompactPadding(context).top,
          bottom: ResponsiveUtils.getCompactPadding(context).bottom + 16.0,
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            // Previous button
            IconButton(
              icon: const Icon(Icons.skip_previous),
              onPressed: canGoPrevious ? onPrevious : null,
              tooltip: previousTooltip ?? 'Previous chapter',
              iconSize: ResponsiveUtils.getIconSize(
                context,
                baseSize: ResponsiveUtils.isVerySmallScreen(context) ? 40 : 48,
              ),
              constraints: BoxConstraints(
                minWidth: ResponsiveUtils.getMinTouchTarget(context) * 1.1,
                minHeight: ResponsiveUtils.getMinTouchTarget(context) * 1.1,
              ),
            ),
            SizedBox(
              width: ResponsiveUtils.getSpacing(
                context,
                baseSpacing: 32,
              ),
            ),
            // Play/Pause button
            IconButton(
              icon: Icon(isPlaying ? Icons.pause : Icons.play_arrow),
              onPressed: onPlayPause,
              iconSize: ResponsiveUtils.getIconSize(
                context,
                baseSize: ResponsiveUtils.isVerySmallScreen(context) ? 56 : 64,
              ),
              constraints: BoxConstraints(
                minWidth: ResponsiveUtils.getMinTouchTarget(context) * 1.3,
                minHeight: ResponsiveUtils.getMinTouchTarget(context) * 1.3,
              ),
            ),
            SizedBox(
              width: ResponsiveUtils.getSpacing(
                context,
                baseSpacing: 32,
              ),
            ),
            // Next button
            IconButton(
              icon: const Icon(Icons.skip_next),
              onPressed: canGoNext ? onNext : null,
              tooltip: nextTooltip ?? 'Next chapter',
              iconSize: ResponsiveUtils.getIconSize(
                context,
                baseSize: ResponsiveUtils.isVerySmallScreen(context) ? 40 : 48,
              ),
              constraints: BoxConstraints(
                minWidth: ResponsiveUtils.getMinTouchTarget(context) * 1.1,
                minHeight: ResponsiveUtils.getMinTouchTarget(context) * 1.1,
              ),
            ),
            SizedBox(
              width: ResponsiveUtils.getSpacing(
                context,
                baseSpacing: 16,
              ),
            ),
            // Speed button
            PopupMenuButton<double>(
              icon: Text(
                '${currentSpeed}x',
                style: Theme.of(context).textTheme.titleMedium,
              ),
              onSelected: onSpeedChanged,
              itemBuilder: (context) => [
                const PopupMenuItem(value: 0.5, child: Text('0.5x')),
                const PopupMenuItem(value: 0.75, child: Text('0.75x')),
                const PopupMenuItem(value: 1.0, child: Text('1.0x')),
                const PopupMenuItem(value: 1.25, child: Text('1.25x')),
                const PopupMenuItem(value: 1.5, child: Text('1.5x')),
                const PopupMenuItem(value: 1.75, child: Text('1.75x')),
                const PopupMenuItem(value: 2.0, child: Text('2.0x')),
              ],
            ),
          ],
        ),
      );
}
