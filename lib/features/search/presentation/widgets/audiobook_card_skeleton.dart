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

/// Skeleton loader widget for audiobook cards.
///
/// Displays a placeholder card with shimmer effect while content is loading.
class AudiobookCardSkeleton extends StatefulWidget {
  /// Creates a new AudiobookCardSkeleton instance.
  const AudiobookCardSkeleton({super.key});

  @override
  State<AudiobookCardSkeleton> createState() => _AudiobookCardSkeletonState();
}

class _AudiobookCardSkeletonState extends State<AudiobookCardSkeleton>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _animation;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1500),
    )..repeat();
    _animation = Tween<double>(begin: -1.0, end: 2.0).animate(
      CurvedAnimation(parent: _controller, curve: Curves.easeInOut),
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Card(
        margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
        elevation: 2,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
        ),
        child: Padding(
          padding: const EdgeInsets.all(12.0),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Cover image placeholder - fixed size
              _ShimmerContainer(
                width: 72,
                height: 72,
                borderRadius: BorderRadius.circular(10),
                animation: _animation,
              ),
              const SizedBox(width: 12),
              // Content placeholders
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Title placeholder (2 lines)
                    _ShimmerContainer(
                      height: 16,
                      width: double.infinity,
                      borderRadius: BorderRadius.circular(4),
                      animation: _animation,
                    ),
                    const SizedBox(height: 8),
                    _ShimmerContainer(
                      height: 16,
                      width: MediaQuery.of(context).size.width * 0.6,
                      borderRadius: BorderRadius.circular(4),
                      animation: _animation,
                    ),
                    const SizedBox(height: 6),
                    // Author placeholder
                    _ShimmerContainer(
                      height: 14,
                      width: MediaQuery.of(context).size.width * 0.4,
                      borderRadius: BorderRadius.circular(4),
                      animation: _animation,
                    ),
                    const SizedBox(height: 8),
                    // Stats placeholder
                    Row(
                      children: [
                        _ShimmerContainer(
                          height: 14,
                          width: 60,
                          borderRadius: BorderRadius.circular(4),
                          animation: _animation,
                        ),
                        const SizedBox(width: 16),
                        _ShimmerContainer(
                          height: 14,
                          width: 40,
                          borderRadius: BorderRadius.circular(4),
                          animation: _animation,
                        ),
                        const SizedBox(width: 12),
                        _ShimmerContainer(
                          height: 14,
                          width: 40,
                          borderRadius: BorderRadius.circular(4),
                          animation: _animation,
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              // Right side placeholder
              Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  _ShimmerContainer(
                    width: 20,
                    height: 20,
                    borderRadius: BorderRadius.circular(4),
                    animation: _animation,
                  ),
                  const SizedBox(height: 4),
                  _ShimmerContainer(
                    width: 16,
                    height: 16,
                    borderRadius: BorderRadius.circular(4),
                    animation: _animation,
                  ),
                ],
              ),
            ],
          ),
        ),
      );
}

/// List of skeleton cards for loading state.
class AudiobookCardSkeletonList extends StatelessWidget {
  /// Creates a new AudiobookCardSkeletonList instance.
  ///
  /// The [count] parameter specifies how many skeleton cards to display.
  const AudiobookCardSkeletonList({
    super.key,
    this.count = 5,
  });

  /// Number of skeleton cards to display.
  final int count;

  @override
  Widget build(BuildContext context) => ListView.builder(
        itemCount: count,
        itemBuilder: (context, index) => const AudiobookCardSkeleton(),
      );
}

/// Shimmer effect container for skeleton loaders.
class _ShimmerContainer extends StatelessWidget {
  /// Creates a new _ShimmerContainer instance.
  const _ShimmerContainer({
    required this.animation,
    this.width,
    this.height,
    required this.borderRadius,
  });

  /// Animation for shimmer effect.
  final Animation<double> animation;

  /// Container width.
  final double? width;

  /// Container height.
  final double? height;

  /// Border radius.
  final BorderRadius borderRadius;

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
        animation: animation,
        builder: (context, child) => Container(
          width: width,
          height: height,
          decoration: BoxDecoration(
            borderRadius: borderRadius,
            gradient: LinearGradient(
              colors: [
                Theme.of(context).colorScheme.surfaceContainerHighest,
                Theme.of(context)
                    .colorScheme
                    .surfaceContainerHighest
                    .withValues(alpha: 0.5),
                Theme.of(context).colorScheme.surfaceContainerHighest,
              ],
              stops: [
                0.0,
                (animation.value.clamp(0.0, 1.0) * 0.5 + 0.25),
                1.0,
              ],
            ),
          ),
        ),
      );
}
