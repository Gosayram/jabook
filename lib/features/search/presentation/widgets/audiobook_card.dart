import 'package:flutter/material.dart';
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
    final title = audiobook['title'] as String? ??
        AppLocalizations.of(context)!.unknownTitle;
    final author = audiobook['author'] as String? ??
        AppLocalizations.of(context)!.unknownAuthor;
    final size = audiobook['size'] as String? ??
        AppLocalizations.of(context)!.unknownSize;
    final seeders = audiobook['seeders'] as int? ?? 0;
    final leechers = audiobook['leechers'] as int? ?? 0;
    final category = audiobook['category'] as String? ?? 'Другое';
    final coverUrl = audiobook['coverUrl'] as String?;

    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
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
                          size: 14,
                          color: Theme.of(context)
                              .colorScheme
                              .onSurface
                              .withValues(alpha: 0.6),
                        ),
                        const SizedBox(width: 4),
                        Text(
                          size,
                          style:
                              Theme.of(context).textTheme.bodySmall?.copyWith(
                                    color: Theme.of(context)
                                        .colorScheme
                                        .onSurface
                                        .withValues(alpha: 0.6),
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
                          color: Colors.orange,
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
    );
  }

  /// Builds cover image widget or placeholder.
  Widget _buildCover(String? coverUrl, BuildContext context) {
    if (coverUrl != null && coverUrl.isNotEmpty) {
      return ClipRRect(
        borderRadius: BorderRadius.circular(8),
        child: Image.network(
          coverUrl,
          width: 60,
          height: 60,
          fit: BoxFit.cover,
          errorBuilder: (context, error, stackTrace) {
            return _buildPlaceholder(context);
          },
        ),
      );
    }
    return _buildPlaceholder(context);
  }

  /// Builds placeholder when no cover is available.
  Widget _buildPlaceholder(BuildContext context) {
    return Container(
      width: 60,
      height: 60,
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.primaryContainer,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Icon(
        Icons.audiotrack,
        color: Theme.of(context).colorScheme.onPrimaryContainer,
        size: 28,
      ),
    );
  }
}

/// Widget for displaying category badge.
class _CategoryBadge extends StatelessWidget {
  /// Creates a new _CategoryBadge instance.
  const _CategoryBadge({required this.category});

  /// Category name.
  final String category;

  @override
  Widget build(BuildContext context) {
    return Container(
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
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 14, color: color),
        const SizedBox(width: 2),
        Text(
          '$value',
          style: TextStyle(
            fontSize: 12,
            fontWeight: FontWeight.w500,
            color: value > 0 ? color : Colors.grey,
          ),
        ),
      ],
    );
  }
}
