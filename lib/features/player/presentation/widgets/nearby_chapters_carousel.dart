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
import 'package:jabook/core/data/remote/rutracker/rutracker_parser.dart';

/// Widget for displaying nearby chapters carousel.
///
/// Shows current chapter + 2 previous + 2 next chapters for quick navigation.
/// This is a pure UI widget that receives callbacks for actions.
class NearbyChaptersCarousel extends StatelessWidget {
  /// Creates a new NearbyChaptersCarousel instance.
  const NearbyChaptersCarousel({
    super.key,
    required this.chapters,
    required this.currentIndex,
    required this.onChapterSelected,
  });

  /// List of chapters.
  final List<Chapter> chapters;

  /// Current chapter index (0-based).
  final int currentIndex;

  /// Callback when a chapter is selected.
  final ValueChanged<Chapter> onChapterSelected;

  @override
  Widget build(BuildContext context) {
    if (chapters.isEmpty ||
        currentIndex < 0 ||
        currentIndex >= chapters.length) {
      return const SizedBox.shrink();
    }

    return Padding(
      padding: const EdgeInsets.only(top: 8.0, bottom: 8.0),
      child: SizedBox(
        height: 90,
        child: ListView.builder(
          scrollDirection: Axis.horizontal,
          padding: const EdgeInsets.symmetric(horizontal: 16.0),
          itemCount: chapters.length,
          itemBuilder: (context, index) {
            // DEFENSIVE: Validate index is in bounds
            if (index < 0 || index >= chapters.length) {
              return const SizedBox.shrink();
            }

            final chapter = chapters[index];
            final isCurrent = index == currentIndex;
            final distance = (index - currentIndex).abs();

            // Show only current + 2 prev + 2 next (total 5 chapters)
            if (distance > 2) return const SizedBox.shrink();

            return Padding(
              padding: const EdgeInsets.only(right: 8.0),
              child: GestureDetector(
                onTap: () => onChapterSelected(chapter),
                child: Container(
                  width: 130,
                  decoration: BoxDecoration(
                    color: isCurrent
                        ? Theme.of(context).primaryColor.withValues(alpha: 0.1)
                        : Theme.of(context).cardColor,
                    borderRadius: BorderRadius.circular(12),
                    border: isCurrent
                        ? Border.all(
                            color: Theme.of(context).primaryColor,
                            width: 2,
                          )
                        : Border.all(
                            color: Theme.of(context).dividerColor,
                          ),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.all(8.0),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Text(
                          '${index + 1}',
                          style:
                              Theme.of(context).textTheme.titleSmall?.copyWith(
                                    fontWeight: isCurrent
                                        ? FontWeight.bold
                                        : FontWeight.normal,
                                    color: isCurrent
                                        ? Theme.of(context).primaryColor
                                        : Theme.of(context)
                                            .textTheme
                                            .titleSmall
                                            ?.color,
                                  ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          chapter.title,
                          style: Theme.of(context).textTheme.bodySmall,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          textAlign: TextAlign.center,
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            );
          },
        ),
      ),
    );
  }
}
