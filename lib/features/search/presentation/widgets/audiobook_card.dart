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
import 'package:jabook/core/logging/environment_logger.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Reusable card widget for displaying audiobook information.
///
/// This widget provides an improved design with cover image support,
/// better typography, and visual indicators for seeders/leechers.
class AudiobookCard extends StatelessWidget {
  /// Creates a new AudiobookCard instance.
  ///
  /// The [audiobook] parameter contains the audiobook data.
  /// The [onTap] callback is called when the card is tapped.
  /// The [isFavorite] parameter indicates if the audiobook is in favorites.
  /// The [onFavoriteToggle] callback is called when favorite button is tapped.
  const AudiobookCard({
    super.key,
    required this.audiobook,
    required this.onTap,
    this.isFavorite = false,
    this.onFavoriteToggle,
  });

  /// Audiobook data map.
  final Map<String, dynamic> audiobook;

  /// Callback when card is tapped.
  final VoidCallback onTap;

  /// Whether the audiobook is in favorites.
  final bool isFavorite;

  /// Callback when favorite button is tapped. Receives new favorite state.
  final void Function(bool)? onFavoriteToggle;

  @override
  Widget build(BuildContext context) {
    // On Android 16, AppLocalizations may not be initialized yet during early startup
    // Use safe access with fallback values
    final localizations = AppLocalizations.of(context);
    final title = audiobook['title'] as String? ??
        localizations?.unknownTitle ??
        'Unknown Title';
    final author = audiobook['author'] as String? ??
        localizations?.unknownAuthor ??
        'Unknown Author';
    final size = audiobook['size'] as String? ??
        localizations?.unknownSize ??
        'Unknown Size';
    final seeders = audiobook['seeders'] as int? ?? 0;
    final leechers = audiobook['leechers'] as int? ?? 0;
    final category = audiobook['category'] as String? ??
        (AppLocalizations.of(context)?.otherCategory ?? 'Other');
    final coverUrl = audiobook['coverUrl'] as String?;

    // Wrap card in RepaintBoundary to isolate repaints
    return RepaintBoundary(
      child: Card(
        margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
        elevation: 2,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
        ),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(12),
          child: Padding(
            padding: const EdgeInsets.all(14.0),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Cover image or placeholder
                _buildCover(coverUrl, context),
                const SizedBox(width: 12),
                // Content
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      // Title and category badge
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Expanded(
                            child: Text(
                              title,
                              style: Theme.of(context)
                                  .textTheme
                                  .titleMedium
                                  ?.copyWith(
                                    fontWeight: FontWeight.w600,
                                  ),
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                          const SizedBox(width: 8),
                          _CategoryBadge(category: category),
                        ],
                      ),
                      const SizedBox(height: 6),
                      // Author
                      Text(
                        author,
                        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                              color: Theme.of(context)
                                  .colorScheme
                                  .onSurface
                                  .withValues(alpha: 0.7),
                            ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      const SizedBox(height: 8),
                      // Size and stats row
                      Row(
                        children: [
                          Icon(
                            Icons.storage,
                            size: 16,
                            color: Theme.of(context)
                                .colorScheme
                                .onSurface
                                .withValues(alpha: 0.6),
                          ),
                          const SizedBox(width: 6),
                          Text(
                            size,
                            style:
                                Theme.of(context).textTheme.bodySmall?.copyWith(
                                      color: Theme.of(context)
                                          .colorScheme
                                          .onSurface
                                          .withValues(alpha: 0.6),
                                      fontSize: 12,
                                    ),
                          ),
                          const SizedBox(width: 16),
                          _StatIndicator(
                            icon: Icons.arrow_upward,
                            value: seeders,
                            color: Colors.green,
                          ),
                          const SizedBox(width: 12),
                          _StatIndicator(
                            icon: Icons.arrow_downward,
                            value: leechers,
                            color: Colors.red,
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
                // Favorite button and arrow indicator
                Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    if (onFavoriteToggle != null)
                      IconButton(
                        icon: Icon(
                          isFavorite ? Icons.favorite : Icons.favorite_border,
                          size: 20,
                          color: isFavorite
                              ? Colors.red
                              : Theme.of(context)
                                  .colorScheme
                                  .onSurface
                                  .withValues(alpha: 0.5),
                        ),
                        onPressed: () {
                          onFavoriteToggle!(!isFavorite);
                        },
                        padding: EdgeInsets.zero,
                        constraints: const BoxConstraints(),
                      ),
                    if (onFavoriteToggle != null) const SizedBox(height: 4),
                    Icon(
                      Icons.chevron_right,
                      size: 16,
                      color: Theme.of(context)
                          .colorScheme
                          .onSurface
                          .withValues(alpha: 0.3),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  /// Builds cover image widget or placeholder.
  Widget _buildCover(String? coverUrl, BuildContext context) {
    // Validate URL
    if (coverUrl == null || coverUrl.isEmpty) {
      EnvironmentLogger().d('Cover URL is null or empty');
      return _buildPlaceholder(context);
    }

    // Check if URL is valid
    final uri = Uri.tryParse(coverUrl);
    if (uri == null || !uri.hasScheme || !uri.hasAuthority) {
      // URL is invalid, show placeholder
      EnvironmentLogger().w('Invalid cover URL format: $coverUrl');
      return _buildPlaceholder(context);
    }

    // Log successful URL parsing for debugging
    EnvironmentLogger().d('Loading cover image from: $coverUrl');

    return RepaintBoundary(
      child: ClipRRect(
        borderRadius: BorderRadius.circular(10),
        child: CachedNetworkImage(
          imageUrl: coverUrl,
          width: 68,
          height: 68,
          fit: BoxFit.cover,
          // Images from RuTracker are typically up to 500x500, usually smaller
          // Use appropriate cache size for display (68x68 in card, 2x for retina)
          // Limit max dimensions to prevent memory issues with large images
          maxWidthDiskCache: 200,
          maxHeightDiskCache: 200,
          memCacheWidth: 136,
          memCacheHeight: 136,
          // Add headers to ensure proper loading
          httpHeaders: const {
            'Accept': 'image/*',
            'User-Agent': 'Mozilla/5.0',
          },
          // Use fadeInDuration for smoother loading
          fadeInDuration: const Duration(milliseconds: 200),
          fadeOutDuration: const Duration(milliseconds: 100),
          placeholder: (context, url) => Container(
            width: 68,
            height: 68,
            color: Theme.of(context).colorScheme.surfaceContainerHighest,
            child: const Center(
              child: SizedBox(
                width: 20,
                height: 20,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
            ),
          ),
          errorWidget: (context, url, error) {
            // Log error loading image with more details
            EnvironmentLogger().w(
              'Failed to load cover image: $url (error: ${error.runtimeType})',
              error: error,
            );
            return _buildPlaceholder(context);
          },
        ),
      ),
    );
  }

  /// Builds placeholder when no cover is available.
  Widget _buildPlaceholder(BuildContext context) => Container(
        width: 68,
        height: 68,
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.primaryContainer,
          borderRadius: BorderRadius.circular(10),
        ),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(10),
          child: Image.asset(
            'assets/icons/app_icon.png',
            width: 68,
            height: 68,
            fit: BoxFit.cover,
            errorBuilder: (context, error, stackTrace) => Icon(
              Icons.audiotrack,
              color: Theme.of(context).colorScheme.onPrimaryContainer,
              size: 32,
            ),
          ),
        ),
      );
}

/// Widget for displaying category badge.
class _CategoryBadge extends StatelessWidget {
  /// Creates a new _CategoryBadge instance.
  const _CategoryBadge({required this.category});

  /// Category name.
  final String category;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.secondaryContainer,
          borderRadius: BorderRadius.circular(8),
        ),
        child: Text(
          category,
          style: TextStyle(
            fontSize: 10,
            fontWeight: FontWeight.w500,
            color: Theme.of(context).colorScheme.onSecondaryContainer,
          ),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
      );
}

/// Widget for displaying seeders/leechers indicator.
class _StatIndicator extends StatelessWidget {
  /// Creates a new _StatIndicator instance.
  const _StatIndicator({
    required this.icon,
    required this.value,
    required this.color,
  });

  /// Icon to display.
  final IconData icon;

  /// Value to display.
  final int value;

  /// Color for the icon.
  final Color color;

  @override
  Widget build(BuildContext context) {
    final displayText = '$value';
    final textColor = value > 0
        ? color
        : Theme.of(context).colorScheme.onSurface.withValues(alpha: 0.4);

    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 16, color: color),
        const SizedBox(width: 4),
        Text(
          displayText,
          style: TextStyle(
            fontSize: 12,
            fontWeight: FontWeight.w600,
            color: textColor,
          ),
        ),
      ],
    );
  }
}
