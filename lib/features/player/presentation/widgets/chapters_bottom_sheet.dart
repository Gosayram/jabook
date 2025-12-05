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
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/core/data/remote/rutracker/rutracker_parser.dart';
import 'package:jabook/core/player/player_state_provider.dart';
import 'package:jabook/l10n/app_localizations.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Bottom sheet widget for displaying and navigating chapters.
///
/// Provides optimized navigation for audiobooks with many chapters through:
/// - Search by chapter number or title
/// - Range selection (chunks of 10 chapters)
/// - Filter modes (All, Current)
/// - Fast scrolling with chapter indicator
class ChaptersBottomSheet extends ConsumerStatefulWidget {
  /// Creates a new ChaptersBottomSheet instance.
  const ChaptersBottomSheet({
    super.key,
    required this.audiobook,
    required this.onChapterSelected,
    required this.scrollController,
  });

  /// The audiobook containing chapters to display.
  final Audiobook audiobook;

  /// Callback when a chapter is selected.
  final void Function(Chapter) onChapterSelected;

  /// Scroll controller for the bottom sheet.
  final ScrollController scrollController;

  @override
  ConsumerState<ChaptersBottomSheet> createState() =>
      _ChaptersBottomSheetState();
}

class _ChaptersBottomSheetState extends ConsumerState<ChaptersBottomSheet> {
  final TextEditingController _searchController = TextEditingController();
  final ScrollController _listScrollController = ScrollController();
  int? _selectedRangeIndex;
  String? _searchQuery;
  List<Chapter> _filteredChapters = [];
  String _filterMode = 'all'; // 'all', 'current'
  List<({int start, int end, int index})>? _cachedRanges;
  double? _savedScrollPosition;

  @override
  void initState() {
    super.initState();
    _searchController.addListener(_onSearchChanged);
    _loadSavedScrollPosition();
    _calculateRanges();
    _filterChapters();
    _restoreScrollPosition();
  }

  @override
  void dispose() {
    _searchController
      ..removeListener(_onSearchChanged)
      ..dispose();
    _listScrollController.dispose();
    super.dispose();
  }

  /// Loads saved scroll position from SharedPreferences.
  Future<void> _loadSavedScrollPosition() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final saved = prefs.getDouble('chapters_scroll_${widget.audiobook.id}');
      if (saved != null) {
        setState(() {
          _savedScrollPosition = saved;
        });
      }
    } on Exception {
      // Ignore errors
    }
  }

  /// Saves scroll position to SharedPreferences.
  Future<void> _saveScrollPosition() async {
    if (!_listScrollController.hasClients) return;
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setDouble('chapters_scroll_${widget.audiobook.id}',
          _listScrollController.offset);
    } on Exception {
      // Ignore errors
    }
  }

  /// Restores scroll position after widget is built.
  void _restoreScrollPosition() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_savedScrollPosition != null && _listScrollController.hasClients) {
        _listScrollController.jumpTo(_savedScrollPosition!);
      } else {
        // If no saved position, scroll to current chapter
        _scrollToCurrentChapter();
      }
    });
  }

  /// Scrolls to the current chapter.
  void _scrollToCurrentChapter() {
    if (!_listScrollController.hasClients) return;

    final currentIndex = ref.read(playerStateProvider).currentIndex;
    if (currentIndex >= 0 && currentIndex < widget.audiobook.chapters.length) {
      // Reset filters to show all chapters
      setState(() {
        _filterMode = 'all';
        _selectedRangeIndex = null;
        _searchQuery = null;
        _searchController.clear();
      });
      _filterChapters();

      // Wait for list to rebuild, then scroll
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (_listScrollController.hasClients) {
          final chapter = widget.audiobook.chapters[currentIndex];
          final filteredIndex = _filteredChapters.indexOf(chapter);

          if (filteredIndex >= 0) {
            const itemHeight = 72.0;
            final targetOffset = filteredIndex * itemHeight;

            _listScrollController.animateTo(
              targetOffset.clamp(
                  0.0, _listScrollController.position.maxScrollExtent),
              duration: const Duration(milliseconds: 300),
              curve: Curves.easeInOut,
            );
          }
        }
      });
    }
  }

  /// Calculates chapter ranges (chunks of 10).
  void _calculateRanges() {
    final chapters = widget.audiobook.chapters;
    const chunkSize = 10;
    final ranges = <({int start, int end, int index})>[];

    for (var i = 0; i < chapters.length; i += chunkSize) {
      final end = (i + chunkSize - 1).clamp(0, chapters.length - 1);
      ranges.add((start: i, end: end, index: ranges.length));
    }

    _cachedRanges = ranges;
  }

  /// Gets the range index containing the current chapter.
  int? _getCurrentRangeIndex() {
    final currentIndex = ref.read(playerStateProvider).currentIndex;
    final ranges = _cachedRanges;
    if (ranges == null) return null;

    for (final range in ranges) {
      if (currentIndex >= range.start && currentIndex <= range.end) {
        return range.index;
      }
    }
    return null;
  }

  /// Handles search input changes.
  void _onSearchChanged() {
    final query = _searchController.text;
    setState(() {
      _searchQuery = query.isEmpty ? null : query;
    });

    // If number entered, reset filters and scroll to chapter
    final number = int.tryParse(query);
    if (number != null &&
        number > 0 &&
        number <= widget.audiobook.chapters.length) {
      // Reset filters to show all chapters when searching by number
      setState(() {
        _filterMode = 'all';
        _selectedRangeIndex = null;
      });
      _filterChapters();
      // Wait for list to rebuild, then scroll
      WidgetsBinding.instance.addPostFrameCallback((_) {
        _scrollToChapter(number - 1); // 0-based index
      });
    } else {
      _filterChapters(); // Filter by text
    }
  }

  /// Scrolls to a specific chapter by index.
  void _scrollToChapter(int index) {
    if (!_listScrollController.hasClients) return;
    if (index < 0 || index >= widget.audiobook.chapters.length) return;

    // Find the chapter in filtered list
    final chapter = widget.audiobook.chapters[index];
    final filteredIndex = _filteredChapters.indexOf(chapter);

    if (filteredIndex >= 0) {
      // Estimate item height (ListTile with padding)
      const itemHeight = 72.0;
      final targetOffset = filteredIndex * itemHeight;

      _listScrollController.animateTo(
        targetOffset.clamp(0.0, _listScrollController.position.maxScrollExtent),
        duration: const Duration(milliseconds: 300),
        curve: Curves.easeInOut,
      );
    } else {
      // Chapter not in filtered list - ensure it's visible by resetting filters
      setState(() {
        _filterMode = 'all';
        _selectedRangeIndex = null;
        _searchQuery = null;
        _searchController.clear();
      });
      _filterChapters();
      // Retry scrolling after rebuild
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (_listScrollController.hasClients) {
          final newFilteredIndex = _filteredChapters.indexOf(chapter);
          if (newFilteredIndex >= 0) {
            const itemHeight = 72.0;
            final targetOffset = newFilteredIndex * itemHeight;
            _listScrollController.animateTo(
              targetOffset.clamp(
                  0.0, _listScrollController.position.maxScrollExtent),
              duration: const Duration(milliseconds: 300),
              curve: Curves.easeInOut,
            );
          }
        }
      });
    }
  }

  /// Handles range selection.
  void _onRangeSelected(int rangeIndex) {
    setState(() {
      _selectedRangeIndex = rangeIndex;
    });
    _filterChapters();
  }

  /// Filters chapters based on selected range, search query, and filter mode.
  void _filterChapters() {
    var chapters = widget.audiobook.chapters;

    // Filter by mode
    if (_filterMode == 'current') {
      final currentIndex = ref.read(playerStateProvider).currentIndex;
      if (currentIndex >= 0 && currentIndex < chapters.length) {
        chapters = [chapters[currentIndex]];
      } else {
        chapters = [];
      }
    }

    // Filter by range
    if (_selectedRangeIndex != null && _cachedRanges != null) {
      final ranges = _cachedRanges!;
      if (_selectedRangeIndex! < ranges.length) {
        final range = ranges[_selectedRangeIndex!];
        chapters = chapters.sublist(
          range.start.clamp(0, chapters.length - 1),
          (range.end + 1).clamp(0, chapters.length),
        );
      }
    }

    // Filter by search query
    if (_searchQuery != null && _searchQuery!.isNotEmpty) {
      final query = _searchQuery!.toLowerCase();
      chapters = chapters
          .where((chapter) => chapter.title.toLowerCase().contains(query))
          .toList();
    }

    setState(() {
      _filteredChapters = chapters;
    });
  }

  /// Formats duration as MM:SS.
  String _formatDuration(Duration duration) {
    final minutes = duration.inMinutes.remainder(60);
    final seconds = duration.inSeconds.remainder(60);
    return '$minutes:${seconds.toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) {
    final playerState = ref.watch(playerStateProvider);
    final currentIndex = playerState.currentIndex;
    final ranges = _cachedRanges ?? [];
    final currentRangeIndex = _getCurrentRangeIndex();

    return DecoratedBox(
      decoration: BoxDecoration(
        color: Theme.of(context).scaffoldBackgroundColor,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
      ),
      child: Column(
        children: [
          // Handle bar
          Container(
            margin: const EdgeInsets.only(top: 12, bottom: 8),
            width: 40,
            height: 4,
            decoration: BoxDecoration(
              color: Theme.of(context).dividerColor,
              borderRadius: BorderRadius.circular(2),
            ),
          ),

          // Header
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: Row(
              children: [
                Text(
                  AppLocalizations.of(context)?.chaptersLabel ?? 'Chapters',
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                const Spacer(),
                IconButton(
                  icon: const Icon(Icons.close),
                  onPressed: () => Navigator.of(context).pop(),
                ),
              ],
            ),
          ),

          // Search field
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: TextField(
              controller: _searchController,
              decoration: InputDecoration(
                hintText: 'Search by number or title...',
                prefixIcon: const Icon(Icons.search),
                suffixIcon: _searchController.text.isNotEmpty
                    ? IconButton(
                        icon: const Icon(Icons.clear),
                        onPressed: _searchController.clear,
                      )
                    : null,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
              keyboardType: TextInputType.text,
              textInputAction: TextInputAction.search,
            ),
          ),

          // Filter chips
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: Row(
              children: [
                _buildFilterChip(
                  context,
                  label: 'All',
                  isSelected: _filterMode == 'all',
                  onSelected: (selected) {
                    if (selected) {
                      setState(() {
                        _filterMode = 'all';
                        _selectedRangeIndex = null;
                      });
                      _filterChapters();
                    }
                  },
                ),
                const SizedBox(width: 8),
                _buildFilterChip(
                  context,
                  label: 'Current',
                  isSelected: _filterMode == 'current',
                  onSelected: (selected) {
                    if (selected) {
                      setState(() {
                        _filterMode = 'current';
                        _selectedRangeIndex = null;
                      });
                      _filterChapters();
                    }
                  },
                ),
              ],
            ),
          ),

          // Range chips (only show if filter mode is 'all' and there are many chapters)
          if (_filterMode == 'all' && ranges.length > 1)
            SizedBox(
              height: 48,
              child: ListView.builder(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.symmetric(horizontal: 16),
                itemCount: ranges.length,
                itemBuilder: (context, index) {
                  final range = ranges[index];
                  final isSelected = _selectedRangeIndex == index;
                  final isCurrentRange = currentRangeIndex == index;

                  return Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: FilterChip(
                      label: Text('${range.start + 1}-${range.end + 1}'),
                      selected: isSelected || isCurrentRange,
                      onSelected: (selected) {
                        if (selected) {
                          _onRangeSelected(index);
                        } else if (isSelected) {
                          setState(() {
                            _selectedRangeIndex = null;
                          });
                          _filterChapters();
                        }
                      },
                      selectedColor: isCurrentRange
                          ? Theme.of(context)
                              .primaryColor
                              .withValues(alpha: 0.3)
                          : null,
                    ),
                  );
                },
              ),
            ),

          // Chapters list
          Expanded(
            child: _filteredChapters.isEmpty
                ? Center(
                    child: Text(
                      'No chapters found',
                      style: Theme.of(context).textTheme.bodyMedium,
                    ),
                  )
                : NotificationListener<ScrollNotification>(
                    onNotification: (notification) {
                      if (notification is ScrollUpdateNotification) {
                        _saveScrollPosition();
                      }
                      return false;
                    },
                    child: ListView.builder(
                      controller: _listScrollController,
                      padding: const EdgeInsets.symmetric(horizontal: 16),
                      itemCount: _filteredChapters.length,
                      itemBuilder: (context, index) {
                        final chapter = _filteredChapters[index];
                        final chapterIndex =
                            widget.audiobook.chapters.indexOf(chapter);
                        final isCurrent = chapterIndex == currentIndex;

                        return RepaintBoundary(
                          child: ListTile(
                            leading: Icon(
                              isCurrent ? Icons.play_circle_filled : Icons.book,
                              color: isCurrent
                                  ? Theme.of(context).primaryColor
                                  : null,
                            ),
                            title: Text(
                              chapter.title,
                              style: TextStyle(
                                fontWeight: isCurrent
                                    ? FontWeight.bold
                                    : FontWeight.normal,
                                color: isCurrent
                                    ? Theme.of(context).primaryColor
                                    : null,
                              ),
                            ),
                            subtitle: Text(
                              _formatDuration(
                                  Duration(milliseconds: chapter.durationMs)),
                            ),
                            trailing: isCurrent
                                ? Icon(
                                    Icons.volume_up,
                                    color: Theme.of(context).primaryColor,
                                    size: 20,
                                  )
                                : null,
                            onTap: () {
                              widget.onChapterSelected(chapter);
                              Navigator.of(context).pop();
                            },
                          ),
                        );
                      },
                    ),
                  ),
          ),
        ],
      ),
    );
  }

  /// Builds a filter chip widget.
  Widget _buildFilterChip(
    BuildContext context, {
    required String label,
    required bool isSelected,
    required void Function(bool) onSelected,
  }) =>
      FilterChip(
        label: Text(label),
        selected: isSelected,
        onSelected: onSelected,
      );
}
