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
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/core/utils/safe_async.dart';
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
    final audiobookId = audiobook['id'] as String? ?? 'unknown';
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

    // Log data received by card for debugging
    final structuredLogger = StructuredLogger();
    safeUnawaited(structuredLogger.log(
      level: 'debug',
      subsystem: 'ui',
      message: 'AudiobookCard building with data',
      context: 'audiobook_card_build',
      extra: {
        'audiobook_id': audiobookId,
        'title': title,
        'has_cover_url': coverUrl != null && coverUrl.isNotEmpty,
        'cover_url': coverUrl ?? 'null',
        'cover_url_length': coverUrl?.length ?? 0,
        'size': size,
        'seeders': seeders,
        'leechers': leechers,
        'category': category,
      },
    ));

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
            padding: const EdgeInsets.all(12.0),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Cover image or placeholder - fixed size
                SizedBox(
                  width: 72,
                  height: 72,
                  child: _buildCover(coverUrl, context, audiobookId, title),
                ),
                const SizedBox(width: 12),
                // Content
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
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
                      // Author - only show if not empty and not "Unknown"
                      if (author.isNotEmpty && author != 'Unknown') ...[
                        const SizedBox(height: 4),
                        Text(
                          author,
                          style:
                              Theme.of(context).textTheme.bodyMedium?.copyWith(
                                    color: Theme.of(context)
                                        .colorScheme
                                        .onSurface
                                        .withValues(alpha: 0.7),
                                  ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ],
                      const SizedBox(height: 8),
                      // Size and stats row - only show size if not empty and not "Unknown"
                      Row(
                        children: [
                          if (size.isNotEmpty && size != 'Unknown') ...[
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
                              style: Theme.of(context)
                                  .textTheme
                                  .bodySmall
                                  ?.copyWith(
                                    color: Theme.of(context)
                                        .colorScheme
                                        .onSurface
                                        .withValues(alpha: 0.6),
                                    fontSize: 12,
                                  ),
                            ),
                            const SizedBox(width: 16),
                          ],
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
  Widget _buildCover(
    String? coverUrl,
    BuildContext context,
    String audiobookId,
    String title,
  ) {
    final structuredLogger = StructuredLogger();

    // Validate URL
    if (coverUrl == null || coverUrl.isEmpty) {
      EnvironmentLogger().d('AudiobookCard: Cover URL is null or empty');
      safeUnawaited(structuredLogger.log(
        level: 'debug',
        subsystem: 'ui',
        message: 'Cover URL is null or empty, showing placeholder',
        context: 'audiobook_card_cover',
        extra: {
          'audiobook_id': audiobookId,
          'title': audiobook['title'] as String? ?? 'unknown',
        },
      ));
      return _buildPlaceholder(context);
    }

    // Check if URL is valid
    final uri = Uri.tryParse(coverUrl);
    if (uri == null || !uri.hasScheme || !uri.hasAuthority) {
      // URL is invalid, show placeholder
      EnvironmentLogger().w(
        'AudiobookCard: Invalid cover URL format: $coverUrl',
      );
      safeUnawaited(structuredLogger.log(
        level: 'warning',
        subsystem: 'ui',
        message: 'Invalid cover URL format, showing placeholder',
        context: 'audiobook_card_cover',
        extra: {
          'audiobook_id': audiobookId,
          'cover_url': coverUrl,
          'title': title,
          'uri_parse_result': uri?.toString() ?? 'null',
        },
      ));
      return _buildPlaceholder(context);
    }

    // Log successful URL parsing for debugging
    EnvironmentLogger().d('AudiobookCard: Loading cover image from: $coverUrl');
    safeUnawaited(structuredLogger.log(
      level: 'info',
      subsystem: 'ui',
      message: 'Starting cover image load',
      context: 'audiobook_card_cover',
      extra: {
        'audiobook_id': audiobookId,
        'cover_url': coverUrl,
        'title': title,
        'uri_scheme': uri.scheme,
        'uri_authority': uri.authority,
      },
    ));

    return RepaintBoundary(
      child: ClipRRect(
        borderRadius: BorderRadius.circular(10),
        child: CachedNetworkImage(
          imageUrl: coverUrl,
          width: 72,
          height: 72,
          fit: BoxFit.cover,
          // Optimize for list view: resize images to thumbnail size
          // Original images are ~500x500, we need 72x72 thumbnails
          // Use 2x for retina displays: 144x144
          memCacheWidth: 144,
          memCacheHeight: 144,
          // Limit disk cache to prevent excessive storage usage
          // Store slightly larger than display size for better quality
          maxWidthDiskCache: 200,
          maxHeightDiskCache: 200,
          // Add headers to ensure proper loading with cookies
          httpHeaders: const {
            'Accept': 'image/*',
            'User-Agent':
                'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
            'Referer': 'https://rutracker.org/',
          },
          // Use fadeInDuration for smoother loading
          fadeInDuration: const Duration(milliseconds: 200),
          fadeOutDuration: const Duration(milliseconds: 100),
          // Show loading placeholder while image loads
          placeholder: (context, url) {
            safeUnawaited(structuredLogger.log(
              level: 'debug',
              subsystem: 'ui',
              message: 'Cover image loading (showing placeholder)',
              context: 'audiobook_card_cover',
              extra: {
                'audiobook_id': audiobookId,
                'cover_url': url,
                'title': audiobook['title'] as String? ?? 'unknown',
              },
            ));
            return Container(
              width: 72,
              height: 72,
              decoration: BoxDecoration(
                color: Theme.of(context).colorScheme.surfaceContainerHighest,
                borderRadius: BorderRadius.circular(10),
              ),
              child: const Center(
                child: SizedBox(
                  width: 20,
                  height: 20,
                  child: CircularProgressIndicator(strokeWidth: 2),
                ),
              ),
            );
          },
          errorWidget: (context, url, error) {
            // Log error loading image with more details for debugging
            EnvironmentLogger().w(
              'Failed to load cover image: $url (error: ${error.runtimeType})',
              error: error,
            );
            safeUnawaited(structuredLogger.log(
              level: 'warning',
              subsystem: 'ui',
              message: 'Failed to load cover image',
              context: 'audiobook_card_cover',
              cause: error.toString(),
              extra: {
                'audiobook_id': audiobookId,
                'cover_url': url,
                'error_type': error.runtimeType.toString(),
                'title': title,
              },
            ));
            return _buildPlaceholder(context);
          },
        ),
      ),
    );
  }

  /// Builds placeholder when no cover is available.
  Widget _buildPlaceholder(BuildContext context) => Container(
        width: 72,
        height: 72,
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.primaryContainer,
          borderRadius: BorderRadius.circular(10),
        ),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(10),
          child: Image.asset(
            'assets/icons/app_icon.png',
            width: 72,
            height: 72,
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
