import 'package:flutter/material.dart';
import 'package:jabook/features/search/presentation/widgets/audiobook_card.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Widget for displaying audiobooks grouped by category.
///
/// This widget groups search results by category and displays them
/// in expandable sections for better organization.
class GroupedAudiobookList extends StatelessWidget {
  /// Creates a new GroupedAudiobookList instance.
  ///
  /// The [audiobooks] parameter is a list of audiobook maps to display.
  /// The [onAudiobookTap] callback is called when an audiobook is tapped.
  /// The [loadMore] callback is called to load more results (optional).
  /// The [hasMore] parameter indicates if more results are available.
  /// The [isLoadingMore] parameter indicates if more results are being loaded.
  /// The [favoriteIds] parameter is a set of favorite audiobook IDs.
  /// The [onFavoriteToggle] callback is called when favorite button is tapped.
  const GroupedAudiobookList({
    super.key,
    required this.audiobooks,
    required this.onAudiobookTap,
    this.loadMore,
    this.hasMore = false,
    this.isLoadingMore = false,
    this.favoriteIds = const <String>{},
    this.onFavoriteToggle,
  });

  /// List of audiobook maps to display.
  final List<Map<String, dynamic>> audiobooks;

  /// Callback when an audiobook is tapped.
  final void Function(String id) onAudiobookTap;

  /// Optional callback to load more results.
  final VoidCallback? loadMore;

  /// Whether more results are available.
  final bool hasMore;

  /// Whether more results are being loaded.
  final bool isLoadingMore;

  /// Set of favorite audiobook IDs.
  final Set<String> favoriteIds;

  /// Callback when favorite button is tapped. Receives topicId and new favorite state.
  final void Function(String topicId, bool isFavorite)? onFavoriteToggle;

  /// Groups audiobooks by category.
  Map<String, List<Map<String, dynamic>>> _groupByCategory() {
    final grouped = <String, List<Map<String, dynamic>>>{};

    for (final audiobook in audiobooks) {
      final category = audiobook['category'] as String? ?? 'Другое';
      if (!grouped.containsKey(category)) {
        grouped[category] = [];
      }
      grouped[category]!.add(audiobook);
    }

    return grouped;
  }

  @override
  Widget build(BuildContext context) {
    final grouped = _groupByCategory();
    final categories = grouped.keys.toList()..sort();

    if (categories.isEmpty) {
      return Center(
        child: Text(AppLocalizations.of(context)!.noResults),
      );
    }

    return ListView.builder(
      itemCount: categories.length + (hasMore ? 1 : 0),
      itemBuilder: (context, index) {
        if (index >= categories.length) {
          // Load more indicator
          return Padding(
            padding: const EdgeInsets.symmetric(vertical: 16),
            child: Center(
              child: isLoadingMore
                  ? const SizedBox(
                      width: 24,
                      height: 24,
                      child: CircularProgressIndicator(),
                    )
                  : loadMore != null
                      ? OutlinedButton(
                          onPressed: loadMore,
                          child: const Text('Load more'),
                        )
                      : const SizedBox.shrink(),
            ),
          );
        }

        final category = categories[index];
        final categoryAudiobooks = grouped[category]!;

        return _CategorySection(
          categoryName: category,
          audiobooks: categoryAudiobooks,
          onAudiobookTap: onAudiobookTap,
          favoriteIds: favoriteIds,
          onFavoriteToggle: onFavoriteToggle,
        );
      },
    );
  }
}

/// Widget for displaying a single category section with audiobooks.
class _CategorySection extends StatefulWidget {
  /// Creates a new _CategorySection instance.
  const _CategorySection({
    required this.categoryName,
    required this.audiobooks,
    required this.onAudiobookTap,
    required this.favoriteIds,
    this.onFavoriteToggle,
  });

  /// Name of the category.
  final String categoryName;

  /// List of audiobooks in this category.
  final List<Map<String, dynamic>> audiobooks;

  /// Callback when an audiobook is tapped.
  final void Function(String id) onAudiobookTap;

  /// Set of favorite audiobook IDs.
  final Set<String> favoriteIds;

  /// Callback when favorite button is tapped. Receives topicId and new favorite state.
  final void Function(String topicId, bool isFavorite)? onFavoriteToggle;

  @override
  State<_CategorySection> createState() => _CategorySectionState();
}

class _CategorySectionState extends State<_CategorySection> {
  bool _isExpanded = true;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Column(
        children: [
          // Category header
          InkWell(
            onTap: () {
              setState(() {
                _isExpanded = !_isExpanded;
              });
            },
            child: Padding(
              padding: const EdgeInsets.all(16.0),
              child: Row(
                children: [
                  Icon(
                    _isExpanded ? Icons.expand_less : Icons.expand_more,
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      widget.categoryName,
                      style: Theme.of(context).textTheme.titleMedium?.copyWith(
                            fontWeight: FontWeight.bold,
                          ),
                    ),
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 8,
                      vertical: 4,
                    ),
                    decoration: BoxDecoration(
                      color: Theme.of(context).colorScheme.primaryContainer,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Text(
                      '${widget.audiobooks.length}',
                      style: TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.bold,
                        color: Theme.of(context).colorScheme.onPrimaryContainer,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
          // Audiobooks list (expandable)
          if (_isExpanded)
            ...widget.audiobooks.map(
              (audiobook) {
                final topicId = audiobook['id'] as String? ?? '';
                final isFavorite = widget.favoriteIds.contains(topicId);
                return AudiobookCard(
                  audiobook: audiobook,
                  onTap: () => widget.onAudiobookTap(topicId),
                  isFavorite: isFavorite,
                  onFavoriteToggle: widget.onFavoriteToggle != null
                      ? (newState) =>
                          widget.onFavoriteToggle!(topicId, newState)
                      : null,
                );
              },
            ),
        ],
      ),
    );
  }
}
