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
import 'package:jabook/core/di/providers/simple_player_providers.dart';
import 'package:jabook/core/domain/library/entities/local_audiobook_group.dart';
import 'package:jabook/l10n/app_localizations.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Bottom sheet widget for displaying and navigating tracks (chapters) for local audiobooks.
///
/// Provides optimized navigation for audiobooks with many tracks through:
/// - Search by track number or title
/// - Range selection (chunks of 10 tracks)
/// - Filter modes (All, Current)
/// - Fast scrolling with track indicator
class TracksBottomSheet extends ConsumerStatefulWidget {
  /// Creates a new TracksBottomSheet instance.
  const TracksBottomSheet({
    super.key,
    required this.group,
    required this.onTrackSelected,
    required this.scrollController,
  });

  /// The audiobook group containing tracks to display.
  final LocalAudiobookGroup group;

  /// Callback when a track is selected.
  final void Function(int index) onTrackSelected;

  /// Scroll controller for the bottom sheet.
  final DraggableScrollableController scrollController;

  @override
  ConsumerState<TracksBottomSheet> createState() => _TracksBottomSheetState();
}

class _TracksBottomSheetState extends ConsumerState<TracksBottomSheet> {
  final TextEditingController _searchController = TextEditingController();
  final ScrollController _listScrollController = ScrollController();
  int? _selectedRangeIndex;
  String? _searchQuery;
  List<int> _filteredIndices = [];
  String _filterMode = 'all'; // 'all', 'current'
  List<({int start, int end, int index})>? _cachedRanges;
  double? _savedScrollPosition;

  @override
  void initState() {
    super.initState();
    _searchController.addListener(_onSearchChanged);
    _loadSavedScrollPosition();
    _calculateRanges();
    _filterTracks();
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
      final saved = prefs.getDouble('tracks_scroll_${widget.group.groupPath}');
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
      await prefs.setDouble('tracks_scroll_${widget.group.groupPath}',
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
        // If no saved position, scroll to current track
        _scrollToCurrentTrack();
      }
    });
  }

  /// Scrolls to the current track.
  void _scrollToCurrentTrack() {
    if (!_listScrollController.hasClients) return;

    final currentIndex = ref.read(simplePlayerProvider).currentTrackIndex;
    if (currentIndex >= 0 && currentIndex < widget.group.files.length) {
      // Reset filters to show all tracks
      setState(() {
        _filterMode = 'all';
        _selectedRangeIndex = null;
        _searchQuery = null;
        _searchController.clear();
      });
      _filterTracks();

      // Wait for list to rebuild, then scroll
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (_listScrollController.hasClients) {
          final filteredIndex = _filteredIndices.indexOf(currentIndex);

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

  /// Calculates track ranges (chunks of 10).
  void _calculateRanges() {
    final files = widget.group.files;
    const chunkSize = 10;
    final ranges = <({int start, int end, int index})>[];

    for (var i = 0; i < files.length; i += chunkSize) {
      final end = (i + chunkSize - 1).clamp(0, files.length - 1);
      ranges.add((start: i, end: end, index: ranges.length));
    }

    _cachedRanges = ranges;
  }

  /// Handles search input changes.
  void _onSearchChanged() {
    final query = _searchController.text;
    setState(() {
      _searchQuery = query.isEmpty ? null : query;
    });

    // If number entered, reset filters and scroll to track
    final number = int.tryParse(query);
    if (number != null && number > 0 && number <= widget.group.files.length) {
      // Reset filters to show all tracks when searching by number
      setState(() {
        _filterMode = 'all';
        _selectedRangeIndex = null;
      });
      _filterTracks();
      // Wait for list to rebuild, then scroll
      WidgetsBinding.instance.addPostFrameCallback((_) {
        _scrollToTrack(number - 1); // 0-based index
      });
    } else {
      _filterTracks(); // Filter by text
    }
  }

  /// Scrolls to a specific track by index.
  void _scrollToTrack(int index) {
    if (!_listScrollController.hasClients) return;
    if (index < 0 || index >= widget.group.files.length) return;

    final filteredIndex = _filteredIndices.indexOf(index);

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
      // Track not in filtered list - ensure it's visible by resetting filters
      setState(() {
        _filterMode = 'all';
        _selectedRangeIndex = null;
        _searchQuery = null;
        _searchController.clear();
      });
      _filterTracks();
      // Retry scrolling after rebuild
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (_listScrollController.hasClients) {
          final newFilteredIndex = _filteredIndices.indexOf(index);
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
    _filterTracks();
  }

  /// Filters tracks based on selected range, search query, and filter mode.
  void _filterTracks() {
    final files = widget.group.files;
    var indices = List.generate(files.length, (i) => i);

    // Filter by mode
    if (_filterMode == 'current') {
      final currentIndex = ref.read(simplePlayerProvider).currentTrackIndex;
      if (currentIndex >= 0 && currentIndex < files.length) {
        indices = [currentIndex];
      } else {
        indices = [];
      }
    }

    // Filter by range
    if (_selectedRangeIndex != null && _cachedRanges != null) {
      final ranges = _cachedRanges!;
      if (_selectedRangeIndex! < ranges.length) {
        final range = ranges[_selectedRangeIndex!];
        indices =
            indices.where((i) => i >= range.start && i <= range.end).toList();
      }
    }

    // Filter by search query
    if (_searchQuery != null && _searchQuery!.isNotEmpty) {
      final query = _searchQuery!.toLowerCase();
      indices = indices.where((i) {
        final file = files[i];
        final displayName = widget.group.hasMultiFolderStructure
            ? file.getDisplayNameWithPart(widget.group.groupPath)
            : file.displayName;
        return displayName.toLowerCase().contains(query) ||
            (i + 1).toString().contains(query);
      }).toList();
    }

    setState(() {
      _filteredIndices = indices;
    });
  }

  /// Gets display name for a track.
  String _getTrackDisplayName(int index) {
    final file = widget.group.files[index];
    return widget.group.hasMultiFolderStructure
        ? file.getDisplayNameWithPart(widget.group.groupPath)
        : file.displayName;
  }

  /// Formats duration in milliseconds to readable string.
  String _formatDuration(int? durationMs) {
    if (durationMs == null) return '--:--';
    final duration = Duration(milliseconds: durationMs);
    final hours = duration.inHours;
    final minutes = duration.inMinutes.remainder(60);
    final seconds = duration.inSeconds.remainder(60);

    if (hours > 0) {
      return '${hours.toString().padLeft(2, '0')}:'
          '${minutes.toString().padLeft(2, '0')}:'
          '${seconds.toString().padLeft(2, '0')}';
    }
    return '${minutes.toString().padLeft(2, '0')}:'
        '${seconds.toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context);
    final playerState = ref.watch(simplePlayerProvider);
    final currentIndex = playerState.currentTrackIndex;

    return DecoratedBox(
      decoration: BoxDecoration(
        color: Theme.of(context).scaffoldBackgroundColor,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
      ),
      child: DraggableScrollableSheet(
        initialChildSize: 0.9,
        minChildSize: 0.5,
        maxChildSize: 0.95,
        controller: widget.scrollController,
        builder: (sheetContext, scrollController) {
          // Save scroll position when scrolling
          scrollController.addListener(_saveScrollPosition);

          return Column(
            children: [
              // Header
              Padding(
                padding: const EdgeInsets.all(16.0),
                child: Row(
                  children: [
                    Icon(
                      Icons.menu_book,
                      color: Theme.of(context).primaryColor,
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        localizations?.chaptersLabel ?? 'Tracks',
                        style: Theme.of(context).textTheme.titleLarge?.copyWith(
                              fontWeight: FontWeight.bold,
                            ),
                      ),
                    ),
                    Text(
                      '${widget.group.files.length}',
                      style: Theme.of(context).textTheme.titleMedium?.copyWith(
                            color: Theme.of(context).primaryColor,
                            fontWeight: FontWeight.bold,
                          ),
                    ),
                  ],
                ),
              ),
              const Divider(height: 1),
              // Search bar
              Padding(
                padding: const EdgeInsets.all(16.0),
                child: TextField(
                  controller: _searchController,
                  style: TextStyle(
                    color: Theme.of(context).colorScheme.onSurface,
                  ),
                  decoration: InputDecoration(
                    hintText: localizations?.searchChaptersHint ??
                        'Search by number or title...',
                    hintStyle: TextStyle(
                      color: Theme.of(context)
                          .colorScheme
                          .onSurface
                          .withValues(alpha: 0.5),
                    ),
                    prefixIcon: Icon(
                      Icons.search,
                      color: Theme.of(context)
                          .colorScheme
                          .onSurface
                          .withValues(alpha: 0.7),
                    ),
                    suffixIcon: _searchQuery != null
                        ? IconButton(
                            icon: Icon(
                              Icons.clear,
                              color: Theme.of(context)
                                  .colorScheme
                                  .onSurface
                                  .withValues(alpha: 0.7),
                            ),
                            onPressed: _searchController.clear,
                          )
                        : null,
                    filled: true,
                    fillColor: Theme.of(context).colorScheme.surface,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: BorderSide(
                        color: Theme.of(context)
                            .colorScheme
                            .outline
                            .withValues(alpha: 0.3),
                      ),
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: BorderSide(
                        color: Theme.of(context)
                            .colorScheme
                            .outline
                            .withValues(alpha: 0.3),
                      ),
                    ),
                    focusedBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: BorderSide(
                        color: Theme.of(context).primaryColor,
                        width: 2,
                      ),
                    ),
                  ),
                ),
              ),
              // Range chips (if more than 10 tracks)
              if (widget.group.files.length > 10 && _cachedRanges != null)
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16.0),
                  child: SingleChildScrollView(
                    scrollDirection: Axis.horizontal,
                    child: Row(
                      children: [
                        FilterChip(
                          label: const Text('All'),
                          selected: _selectedRangeIndex == null,
                          onSelected: (_) {
                            setState(() {
                              _selectedRangeIndex = null;
                            });
                            _filterTracks();
                          },
                        ),
                        const SizedBox(width: 8),
                        ..._cachedRanges!.map((range) {
                          final isSelected = _selectedRangeIndex == range.index;
                          return Padding(
                            padding: const EdgeInsets.only(right: 8.0),
                            child: FilterChip(
                              label:
                                  Text('${range.start + 1}-${range.end + 1}'),
                              selected: isSelected,
                              onSelected: (_) {
                                _onRangeSelected(range.index);
                              },
                            ),
                          );
                        }),
                      ],
                    ),
                  ),
                ),
              // Filter mode chips
              Padding(
                padding:
                    const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
                child: Row(
                  children: [
                    FilterChip(
                      label: const Text('All'),
                      selected: _filterMode == 'all',
                      onSelected: (_) {
                        setState(() {
                          _filterMode = 'all';
                        });
                        _filterTracks();
                      },
                    ),
                    const SizedBox(width: 8),
                    FilterChip(
                      label: const Text('Current'),
                      selected: _filterMode == 'current',
                      onSelected: (_) {
                        setState(() {
                          _filterMode = 'current';
                        });
                        _filterTracks();
                      },
                    ),
                  ],
                ),
              ),
              // Track list
              Expanded(
                child: _filteredIndices.isEmpty
                    ? Center(
                        child: Text(
                          localizations?.noChaptersFound ?? 'No tracks found',
                          style: Theme.of(context).textTheme.bodyMedium,
                        ),
                      )
                    : ListView.builder(
                        controller: _listScrollController,
                        itemCount: _filteredIndices.length,
                        itemBuilder: (context, index) {
                          final trackIndex = _filteredIndices[index];
                          final file = widget.group.files[trackIndex];
                          final isCurrent = trackIndex == currentIndex;
                          final displayName = _getTrackDisplayName(trackIndex);

                          return ListTile(
                            leading: CircleAvatar(
                              backgroundColor: isCurrent
                                  ? Theme.of(context).primaryColor
                                  : Colors.grey[300],
                              child: Text(
                                '${trackIndex + 1}',
                                style: TextStyle(
                                  color: isCurrent
                                      ? Theme.of(context).colorScheme.onPrimary
                                      : Colors.grey[700],
                                  fontWeight: isCurrent
                                      ? FontWeight.bold
                                      : FontWeight.normal,
                                ),
                              ),
                            ),
                            title: Text(
                              displayName,
                              style: TextStyle(
                                fontWeight: isCurrent
                                    ? FontWeight.bold
                                    : FontWeight.normal,
                                color: isCurrent
                                    ? Theme.of(context).primaryColor
                                    : null,
                              ),
                            ),
                            subtitle: file.duration != null
                                ? Text(_formatDuration(file.duration))
                                : null,
                            trailing: isCurrent
                                ? Icon(
                                    Icons.play_circle_filled,
                                    color: Theme.of(context).primaryColor,
                                  )
                                : null,
                            onTap: () {
                              widget.onTrackSelected(trackIndex);
                              // Close bottom sheet using the sheet context
                              Navigator.of(sheetContext).pop();
                            },
                          );
                        },
                      ),
              ),
            ],
          );
        },
      ),
    );
  }
}
