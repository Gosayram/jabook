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

import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Recommended audiobooks data model.
class RecommendedAudiobook {
  /// Creates a new RecommendedAudiobook instance.
  const RecommendedAudiobook({
    required this.id,
    required this.title,
    required this.author,
    this.coverUrl,
    this.size,
    this.genre,
  });

  /// Topic ID for navigation.
  final String id;

  /// Book title.
  final String title;

  /// Author name.
  final String author;

  /// Cover image URL.
  final String? coverUrl;

  /// File size.
  final String? size;

  /// Genre.
  final String? genre;
}

/// Widget for displaying recommended audiobooks.
///
/// Shows a grid of recommended audiobooks when search field is empty.
class RecommendedAudiobooksWidget extends StatelessWidget {
  /// Creates a new RecommendedAudiobooksWidget instance.
  const RecommendedAudiobooksWidget({
    super.key,
    this.audiobooks = const [],
  });

  /// List of recommended audiobooks to display.
  final List<RecommendedAudiobook> audiobooks;

  @override
  Widget build(BuildContext context) => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            child: Text(
              AppLocalizations.of(context)?.recommended ?? 'Recommended',
              style: Theme.of(context).textTheme.titleLarge?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
            ),
          ),
          if (audiobooks.isEmpty)
            SizedBox(
              height: 200,
              child: Center(
                child: Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Text(
                    AppLocalizations.of(context)?.loading ?? 'Loading...',
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                          color: Theme.of(context)
                              .colorScheme
                              .onSurface
                              .withValues(alpha: 0.6),
                        ),
                  ),
                ),
              ),
            )
          else
            SizedBox(
              height: 200,
              child: ListView.builder(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.symmetric(horizontal: 12),
                itemCount: audiobooks.length,
                itemBuilder: (context, index) {
                  final audiobook = audiobooks[index];
                  return _RecommendedAudiobookCard(
                    audiobook: audiobook,
                    onTap: () => context.push('/topic/${audiobook.id}'),
                  );
                },
              ),
            ),
        ],
      );
}

/// Card widget for a single recommended audiobook.
class _RecommendedAudiobookCard extends StatelessWidget {
  /// Creates a new _RecommendedAudiobookCard instance.
  const _RecommendedAudiobookCard({
    required this.audiobook,
    required this.onTap,
  });

  /// Recommended audiobook data.
  final RecommendedAudiobook audiobook;

  /// Callback when card is tapped.
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => GestureDetector(
        onTap: onTap,
        child: Container(
          width: 140,
          margin: const EdgeInsets.symmetric(horizontal: 4),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Cover image with size badge overlay
              Stack(
                children: [
                  _buildCover(context),
                  // Size badge overlay
                  if (audiobook.size != null)
                    Positioned(
                      bottom: 8,
                      right: 8,
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 6,
                          vertical: 2,
                        ),
                        decoration: BoxDecoration(
                          color: Colors.black.withValues(alpha: 0.7),
                          borderRadius: BorderRadius.circular(4),
                        ),
                        child: Text(
                          audiobook.size!,
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 10,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ),
                    ),
                ],
              ),
              const SizedBox(height: 8),
              // Title
              Text(
                audiobook.title,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
              const SizedBox(height: 4),
              // Author
              Text(
                audiobook.author,
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: Theme.of(context)
                          .colorScheme
                          .onSurface
                          .withValues(alpha: 0.7),
                    ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ],
          ),
        ),
      );

  Widget _buildCover(BuildContext context) => AspectRatio(
        aspectRatio: 2 / 3, // Standard book cover ratio
        child: ClipRRect(
          borderRadius: BorderRadius.circular(12),
          child: _buildCoverImage(context),
        ),
      );

  Widget _buildCoverImage(BuildContext context) {
    if (audiobook.coverUrl != null && audiobook.coverUrl!.isNotEmpty) {
      final uri = Uri.tryParse(audiobook.coverUrl!);
      if (uri != null && uri.hasScheme && uri.hasAuthority) {
        return CachedNetworkImage(
          imageUrl: audiobook.coverUrl!,
          fit: BoxFit.cover,
          // Optimize memory cache for list view (2x for retina displays)
          memCacheWidth: 280, // 140 * 2
          memCacheHeight: 420, // 210 * 2
          // Limit disk cache size to prevent excessive storage usage
          maxWidthDiskCache: 280,
          maxHeightDiskCache: 420,
          placeholder: (context, url) => _buildPlaceholder(context),
          errorWidget: (context, url, error) => _buildPlaceholder(context),
          fadeInDuration: const Duration(milliseconds: 200),
          fadeOutDuration: const Duration(milliseconds: 100),
        );
      }
    }
    return _buildPlaceholder(context);
  }

  Widget _buildPlaceholder(BuildContext context) => DecoratedBox(
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.primaryContainer,
        ),
        child: Center(
          child: Image.asset(
            'assets/icons/app_icon.png',
            fit: BoxFit.contain,
            errorBuilder: (context, error, stackTrace) => Icon(
              Icons.audiotrack,
              color: Theme.of(context).colorScheme.onPrimaryContainer,
              size: 48,
            ),
          ),
        ),
      );
}
