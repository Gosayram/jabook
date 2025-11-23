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

import 'dart:async';
import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:jabook/core/favorites/favorites_service.dart';
import 'package:jabook/core/library/audiobook_file_manager.dart';
import 'package:jabook/core/library/audiobook_library_scanner.dart';
import 'package:jabook/core/library/folder_filter_service.dart';
import 'package:jabook/core/library/local_audiobook.dart';
import 'package:jabook/core/library/smart_scanner_service.dart';
import 'package:jabook/core/player/native_audio_player.dart';
import 'package:jabook/core/utils/content_uri_service.dart';
import 'package:jabook/core/utils/file_picker_utils.dart' as file_picker_utils;
import 'package:jabook/core/utils/responsive_utils.dart';
import 'package:jabook/core/utils/storage_path_utils.dart';
import 'package:jabook/data/db/app_database.dart';
import 'package:jabook/features/library/presentation/widgets/delete_confirmation_dialog.dart';
import 'package:jabook/features/library/providers/library_provider.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Options for sorting audiobook groups in the library.
enum SortOption {
  /// Sort by name in ascending order (A-Z).
  nameAsc,

  /// Sort by name in descending order (Z-A).
  nameDesc,

  /// Sort by size in ascending order (smallest first).
  sizeAsc,

  /// Sort by size in descending order (largest first).
  sizeDesc,

  /// Sort by date in ascending order (oldest first).
  dateAsc,

  /// Sort by date in descending order (newest first).
  dateDesc,

  /// Sort by number of files in ascending order (fewest first).
  filesAsc,

  /// Sort by number of files in descending order (most first).
  filesDesc,
}

/// Options for grouping audiobook groups in the library.
enum GroupOption {
  /// No grouping - show all items in a flat list.
  none,

  /// Group items by the first letter of their name.
  firstLetter,
}

/// Main screen for displaying the user's audiobook library.
///
/// This screen shows the user's collection of downloaded and favorited
/// audiobooks, with options to search, filter, and add new books.
class LibraryScreen extends ConsumerStatefulWidget {
  /// Creates a new LibraryScreen instance.
  ///
  /// The [key] parameter is optional and can be used to identify
  /// this widget in the widget tree.
  const LibraryScreen({super.key});

  @override
  ConsumerState<LibraryScreen> createState() => _LibraryScreenState();
}

class _LibraryScreenState extends ConsumerState<LibraryScreen> {
  int _favoritesCount = 0;
  FavoritesService? _favoritesService;
  DateTime? _lastBackPressTime;

  @override
  void initState() {
    super.initState();
    _initializeService();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    // Reload favorites count when screen becomes visible
    _loadFavoritesCount();
  }

  Future<void> _initializeService() async {
    try {
      // Use singleton AppDatabase instance - it should already be initialized
      // by app.dart, but handle the case where it's not ready yet
      final appDatabase = AppDatabase();

      // Wait a bit for database to be initialized by app.dart
      // This is a workaround for race condition on new Android
      await Future.delayed(const Duration(milliseconds: 100));

      // Check if database is already initialized
      if (!appDatabase.isInitialized) {
        // Database not ready yet - initialize it
        try {
          await appDatabase.initialize();
        } on Exception {
          // If initialization fails, just skip favorites service
          // It's optional and shouldn't block app startup
          return;
        }
      }

      // Now safely access database
      try {
        final db = appDatabase.database;
        _favoritesService = FavoritesService(db);
        await _loadFavoritesCount();
      } on Exception {
        // If database access fails, just skip favorites service
        // It's optional and shouldn't block app startup
      }
    } on Exception {
      // Ignore all errors - favorites service is optional
      // Don't block app startup if favorites can't be loaded
    }
  }

  SortOption _currentSort = SortOption.nameAsc;
  GroupOption _currentGroup = GroupOption.none;

  void _showFilterDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (dialogContext) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: Text(
            AppLocalizations.of(context)?.filterLibraryTooltip ??
                'Filter & Sort Library',
          ),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Sort options
                Text(
                  'Sort by:',
                  style: Theme.of(context).textTheme.titleSmall,
                ),
                const SizedBox(height: 8),
                ...SortOption.values.map((option) => ListTile(
                      leading: Icon(
                        _currentSort == option
                            ? Icons.radio_button_checked
                            : Icons.radio_button_unchecked,
                        color: _currentSort == option
                            ? Theme.of(context).primaryColor
                            : null,
                      ),
                      title: Text(_getSortOptionLabel(option, context)),
                      onTap: () {
                        setDialogState(() {
                          _currentSort = option;
                        });
                      },
                    )),
                const Divider(),
                // Group options
                Text(
                  'Group by:',
                  style: Theme.of(context).textTheme.titleSmall,
                ),
                const SizedBox(height: 8),
                ...GroupOption.values.map((option) => ListTile(
                      leading: Icon(
                        _currentGroup == option
                            ? Icons.radio_button_checked
                            : Icons.radio_button_unchecked,
                        color: _currentGroup == option
                            ? Theme.of(context).primaryColor
                            : null,
                      ),
                      title: Text(_getGroupOptionLabel(option, context)),
                      onTap: () {
                        setDialogState(() {
                          _currentGroup = option;
                        });
                      },
                    )),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () {
                setDialogState(() {
                  _currentSort = SortOption.nameAsc;
                  _currentGroup = GroupOption.none;
                });
              },
              child: const Text('Reset'),
            ),
            TextButton(
              onPressed: () => Navigator.of(dialogContext).pop(),
              child: Text(AppLocalizations.of(context)?.close ?? 'Close'),
            ),
            TextButton(
              onPressed: () {
                Navigator.of(dialogContext).pop();
                // Notify library content to update
                setState(() {});
              },
              child: const Text('Apply'),
            ),
          ],
        ),
      ),
    );
  }

  String _getSortOptionLabel(SortOption option, BuildContext context) {
    switch (option) {
      case SortOption.nameAsc:
        return 'Name (A-Z)';
      case SortOption.nameDesc:
        return 'Name (Z-A)';
      case SortOption.sizeAsc:
        return 'Size (Smallest)';
      case SortOption.sizeDesc:
        return 'Size (Largest)';
      case SortOption.dateAsc:
        return 'Date (Oldest)';
      case SortOption.dateDesc:
        return 'Date (Newest)';
      case SortOption.filesAsc:
        return 'Files (Fewest)';
      case SortOption.filesDesc:
        return 'Files (Most)';
    }
  }

  String _getGroupOptionLabel(GroupOption option, BuildContext context) {
    switch (option) {
      case GroupOption.none:
        return 'None';
      case GroupOption.firstLetter:
        return 'First Letter';
    }
  }

  Future<void> _loadFavoritesCount() async {
    if (_favoritesService == null) return;
    try {
      final count = await _favoritesService!.getFavoritesCount();
      if (mounted) {
        setState(() {
          _favoritesCount = count;
        });
      }
    } on Exception {
      // Ignore errors
    }
  }

  @override
  Widget build(BuildContext context) => PopScope(
        canPop: false,
        onPopInvokedWithResult: (didPop, result) async {
          if (didPop) return;

          final now = DateTime.now();
          final shouldExit = _lastBackPressTime == null ||
              now.difference(_lastBackPressTime!) > const Duration(seconds: 2);

          if (shouldExit) {
            _lastBackPressTime = now;
            if (mounted) {
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text(
                    AppLocalizations.of(context)?.pressBackAgainToExit ??
                        'Press back again to exit',
                  ),
                  duration: const Duration(seconds: 2),
                ),
              );
            }
          } else {
            // Exit app
            exit(0);
          }
        },
        child: Scaffold(
          appBar: AppBar(
            title:
                Text(AppLocalizations.of(context)?.libraryTitle ?? 'Library'),
            actions: [
              Stack(
                clipBehavior: Clip.none,
                children: [
                  IconButton(
                    icon: const Icon(Icons.favorite),
                    onPressed: () {
                      context.go('/favorites');
                      // Count will be reloaded when screen becomes visible again
                    },
                    tooltip: AppLocalizations.of(context)?.favoritesTooltip ??
                        'Favorites',
                  ),
                  if (_favoritesCount > 0)
                    Positioned(
                      right: 6,
                      top: 6,
                      child: Container(
                        padding: const EdgeInsets.all(4),
                        decoration: const BoxDecoration(
                          color: Colors.red,
                          shape: BoxShape.circle,
                        ),
                        constraints: const BoxConstraints(
                          minWidth: 16,
                          minHeight: 16,
                        ),
                        child: Text(
                          _favoritesCount > 99 ? '99+' : '$_favoritesCount',
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 10,
                            fontWeight: FontWeight.bold,
                          ),
                          textAlign: TextAlign.center,
                        ),
                      ),
                    ),
                ],
              ),
              IconButton(
                icon: const Icon(Icons.search),
                onPressed: () {
                  // Navigate to search screen
                  context.go('/search');
                },
                tooltip:
                    AppLocalizations.of(context)?.searchAudiobooks ?? 'Search',
              ),
              IconButton(
                icon: const Icon(Icons.filter_list),
                onPressed: () => _showFilterDialog(context),
                tooltip: AppLocalizations.of(context)?.filterLibraryTooltip ??
                    'Filter library',
              ),
            ],
          ),
          body: _LibraryContent(
            sortOption: _currentSort,
            groupOption: _currentGroup,
          ),
          floatingActionButton: ResponsiveUtils.isDesktop(context)
              ? null // Hide FAB on desktop, use app bar actions instead
              : FloatingActionButton(
                  onPressed: () {
                    // Navigate to search screen for now - FAB functionality
                    context.go('/search');
                  },
                  tooltip: AppLocalizations.of(context)?.addAudiobookTooltip ??
                      'Add audiobook',
                  child: const Icon(Icons.add),
                ),
        ),
      );
}

/// Private widget for displaying the main library content.
///
/// This widget contains the actual content of the library screen,
/// including the list of audiobooks and any filtering/sorting options.
class _LibraryContent extends ConsumerStatefulWidget {
  /// Creates a new _LibraryContent instance.
  const _LibraryContent({
    required this.sortOption,
    required this.groupOption,
  });

  final SortOption sortOption;
  final GroupOption groupOption;

  @override
  ConsumerState<_LibraryContent> createState() => _LibraryContentState();
}

class _LibraryContentState extends ConsumerState<_LibraryContent> {
  List<LocalAudiobookGroup> _displayedGroups = [];
  final FolderFilterService _folderFilterService = FolderFilterService();
  late final AudiobookLibraryScanner _scanner = AudiobookLibraryScanner(
    folderFilterService: _folderFilterService,
    contentUriService: Platform.isAndroid ? ContentUriService() : null,
  );
  final AudiobookFileManager _fileManager = AudiobookFileManager();
  bool _hasInitialLoad = false;

  @override
  void initState() {
    super.initState();
    _hasInitialLoad = false;
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    // Always scan on first load to ensure library is up-to-date
    // This ensures that deleted files are removed and new files are added
    if (!_hasInitialLoad) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        // Always perform incremental scan on first load to sync with filesystem
        // This ensures that deleted books are removed and new books are added
        _loadLocalAudiobooks();
        _hasInitialLoad = true;
      });
    } else {
      // When returning to library tab, ensure UI is updated with current provider state
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) {
          _applyFilters();
        }
      });
    }
  }

  Future<void> _loadLocalAudiobooks({bool forceFullScan = false}) async {
    final isScanning = ref.read(isScanningProvider);
    if (isScanning) return;

    // Check if we have groups in provider - if not, force full scan
    // This ensures that books are always found on first load or after app restart
    final existingGroups = ref.read(libraryGroupsProvider);
    final shouldForceFullScan = forceFullScan || existingGroups.isEmpty;

    if (shouldForceFullScan && !forceFullScan) {
      debugPrint(
        'Library provider is empty, forcing full scan to find all books',
      );
    }

    // Update scanning state via provider
    ref.read(isScanningProvider.notifier).state = true;

    try {
      // Run scanning in isolate to prevent cancellation when app is minimized
      final groups =
          await _scanInBackground(forceFullScan: shouldForceFullScan);

      if (mounted) {
        // Update state via provider to persist across tab switches
        ref.read(libraryGroupsProvider.notifier).updateGroups(groups);
        ref.read(isScanningProvider.notifier).state = false;
        _applyFilters();
      }
    } on Exception catch (e) {
      if (mounted) {
        ref.read(isScanningProvider.notifier).state = false;
        debugPrint('Failed to load local audiobooks: $e');
        // Fallback to regular scanner on error
        try {
          final groups = await _scanner.scanAllLibraryFolders();
          if (mounted) {
            ref.read(libraryGroupsProvider.notifier).updateGroups(groups);
            ref.read(isScanningProvider.notifier).state = false;
            _applyFilters();
          }
        } on Exception {
          if (mounted) {
            ref.read(isScanningProvider.notifier).state = false;
          }
        }
      }
    }
  }

  /// Scans library in background isolate to prevent cancellation.
  Future<List<LocalAudiobookGroup>> _scanInBackground({
    required bool forceFullScan,
  }) async {
    // Run scanning directly but with periodic UI updates
    // Note: Using compute is problematic with file I/O, so we'll use
    // a different approach - periodic setState updates to keep UI responsive
    final folderFilterService = FolderFilterService();
    final scanner = AudiobookLibraryScanner(
      folderFilterService: folderFilterService,
      contentUriService: Platform.isAndroid ? ContentUriService() : null,
    );
    final smartScanner = SmartScannerService(
      scanner: scanner,
      folderFilterService: folderFilterService,
    );

    // Get existing groups to preserve unchanged folders
    final existingGroups = ref.read(libraryGroupsProvider);

    // Use smart scanner for incremental updates, or full scan if forced
    if (forceFullScan) {
      return smartScanner.forceFullScan();
    } else {
      // Pass existing groups to preserve unchanged folders
      return smartScanner.scanIncremental(
        existingGroups: existingGroups.isNotEmpty ? existingGroups : null,
      );
    }
  }

  Future<void> _refreshLibrary({bool forceFullScan = false}) async {
    // Always perform full scan on pull-to-refresh to ensure all files are found
    await _loadLocalAudiobooks(forceFullScan: true);
  }

  void _applyFilters() {
    // Get groups from provider (persisted state)
    final audiobookGroups = ref.read(libraryGroupsProvider);
    final filtered = List<LocalAudiobookGroup>.from(audiobookGroups);

    // Apply sorting
    switch (widget.sortOption) {
      case SortOption.nameAsc:
        filtered.sort((a, b) => a.groupName.compareTo(b.groupName));
        break;
      case SortOption.nameDesc:
        filtered.sort((a, b) => b.groupName.compareTo(a.groupName));
        break;
      case SortOption.sizeAsc:
        filtered.sort((a, b) => a.totalSize.compareTo(b.totalSize));
        break;
      case SortOption.sizeDesc:
        filtered.sort((a, b) => b.totalSize.compareTo(a.totalSize));
        break;
      case SortOption.dateAsc:
        filtered.sort((a, b) {
          final aDate = a.scannedAt ?? DateTime(1970);
          final bDate = b.scannedAt ?? DateTime(1970);
          return aDate.compareTo(bDate);
        });
        break;
      case SortOption.dateDesc:
        filtered.sort((a, b) {
          final aDate = a.scannedAt ?? DateTime(1970);
          final bDate = b.scannedAt ?? DateTime(1970);
          return bDate.compareTo(aDate);
        });
        break;
      case SortOption.filesAsc:
        filtered.sort((a, b) => a.fileCount.compareTo(b.fileCount));
        break;
      case SortOption.filesDesc:
        filtered.sort((a, b) => b.fileCount.compareTo(a.fileCount));
        break;
    }

    // Apply grouping
    if (widget.groupOption == GroupOption.firstLetter) {
      // Grouping will be handled in the UI
    }

    setState(() {
      _displayedGroups = filtered;
    });
  }

  @override
  Widget build(BuildContext context) {
    final padding = ResponsiveUtils.getResponsivePadding(context);
    final iconSize = ResponsiveUtils.getIconSize(context, baseSize: 64);
    final spacing = ResponsiveUtils.getSpacing(context, baseSpacing: 16);

    // Watch providers for reactive updates
    final audiobookGroups = ref.watch(libraryGroupsProvider);
    final isScanning = ref.watch(isScanningProvider);

    // Update displayed groups when provider changes
    if (audiobookGroups.isNotEmpty && _displayedGroups != audiobookGroups) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) {
          _applyFilters();
        }
      });
    }

    // Show loading indicator while scanning
    if (isScanning && audiobookGroups.isEmpty && !_hasInitialLoad) {
      return ResponsiveUtils.responsiveContainer(
        context,
        Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              // Smooth animated progress indicator
              SizedBox(
                width: iconSize,
                height: iconSize,
                child: CircularProgressIndicator(
                  strokeWidth: 4,
                  valueColor: AlwaysStoppedAnimation<Color>(
                    Theme.of(context).colorScheme.primary,
                  ),
                ),
              ),
              SizedBox(height: spacing),
              // Animated text with fade
              TweenAnimationBuilder<double>(
                tween: Tween(begin: 0.0, end: 1.0),
                duration: const Duration(milliseconds: 500),
                builder: (context, value, child) => Opacity(
                  opacity: value,
                  child: Text(
                    'Scanning library...',
                    style: Theme.of(context).textTheme.bodyMedium,
                  ),
                ),
              ),
            ],
          ),
        ),
        padding: padding,
      );
    }

    // Show list of audiobook groups if found
    if (_displayedGroups.isNotEmpty || audiobookGroups.isNotEmpty) {
      final groupsToShow =
          _displayedGroups.isNotEmpty ? _displayedGroups : audiobookGroups;

      if (widget.groupOption == GroupOption.firstLetter) {
        // Group by first letter
        final grouped = <String, List<LocalAudiobookGroup>>{};
        for (final group in groupsToShow) {
          final firstLetter = group.groupName.isNotEmpty
              ? group.groupName[0].toUpperCase()
              : '#';
          if (!grouped.containsKey(firstLetter)) {
            grouped[firstLetter] = [];
          }
          grouped[firstLetter]!.add(group);
        }

        final sortedKeys = grouped.keys.toList()..sort();

        return ResponsiveUtils.responsiveContainer(
          context,
          RefreshIndicator(
            onRefresh: _refreshLibrary,
            child: ListView.builder(
              itemCount: sortedKeys.length,
              itemBuilder: (context, index) {
                final letter = sortedKeys[index];
                final groups = grouped[letter]!;
                return Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Padding(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 16,
                        vertical: 8,
                      ),
                      child: Text(
                        letter,
                        style:
                            Theme.of(context).textTheme.titleMedium?.copyWith(
                                  fontWeight: FontWeight.bold,
                                ),
                      ),
                    ),
                    ...groups.map(
                        (group) => _buildAudiobookGroupTile(context, group)),
                  ],
                );
              },
            ),
          ),
          padding: padding,
        );
      } else {
        return ResponsiveUtils.responsiveContainer(
          context,
          RefreshIndicator(
            onRefresh: _refreshLibrary,
            child: ListView.builder(
              itemCount: groupsToShow.length,
              itemBuilder: (context, index) {
                final group = groupsToShow[index];
                return _buildAudiobookGroupTile(context, group);
              },
            ),
          ),
          padding: padding,
        );
      }
    }

    // Show empty state with action buttons
    return ResponsiveUtils.responsiveContainer(
      context,
      Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Semantics(
              label: 'Empty library',
              child: Icon(
                Icons.library_books_outlined,
                size: iconSize,
                color: Colors.grey,
              ),
            ),
            SizedBox(height: spacing),
            Text(
              AppLocalizations.of(context)?.libraryEmptyMessage ??
                  'Your library is empty',
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontSize:
                        (Theme.of(context).textTheme.titleMedium?.fontSize ??
                                16) *
                            ResponsiveUtils.getFontSizeMultiplier(context),
                  ),
              textAlign: TextAlign.center,
            ),
            SizedBox(height: spacing / 2),
            Padding(
              padding: EdgeInsets.symmetric(
                horizontal: ResponsiveUtils.getHorizontalPadding(context),
              ),
              child: Text(
                AppLocalizations.of(context)?.addAudiobooksHint ??
                    'Add audiobooks to your library to start listening',
                style: Theme.of(context)
                    .textTheme
                    .bodyMedium
                    ?.copyWith(color: Colors.grey),
                textAlign: TextAlign.center,
              ),
            ),
            SizedBox(height: spacing * 1.5),
            Wrap(
              spacing: spacing,
              runSpacing: spacing,
              alignment: WrapAlignment.center,
              children: [
                Semantics(
                  button: true,
                  label: 'Search for audiobooks',
                  child: _buildActionButton(
                    context,
                    icon: Icons.search,
                    label: AppLocalizations.of(context)
                            ?.searchForAudiobooksButton ??
                        'Search for audiobooks',
                    onPressed: () => context.go('/search'),
                  ),
                ),
                Semantics(
                  button: true,
                  label: 'Import audiobooks from files',
                  child: _buildActionButton(
                    context,
                    icon: Icons.folder_open,
                    label:
                        AppLocalizations.of(context)?.importFromFilesButton ??
                            'Import from Files',
                    onPressed: () => _showImportDialog(context),
                  ),
                ),
                Semantics(
                  button: true,
                  label: 'Scan folder for audiobooks',
                  child: _buildActionButton(
                    context,
                    icon: Icons.folder,
                    label: AppLocalizations.of(context)?.scanFolderTitle ??
                        'Scan Folder',
                    onPressed: () => _showScanDialog(context),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
      padding: padding,
    );
  }

  void _showImportDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(AppLocalizations.of(context)?.importAudiobooksTitle ??
            'Import Audiobooks'),
        content: Text(AppLocalizations.of(context)?.selectFilesMessage ??
            'Select audiobook files from your device to add to your library'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text(AppLocalizations.of(context)?.cancel ?? 'Cancel'),
          ),
          ElevatedButton(
            onPressed: () => _importAudiobookFiles(context),
            child: Text(
                AppLocalizations.of(context)?.importButtonText ?? 'Import'),
          ),
        ],
      ),
    );
  }

  void _showScanDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(
            AppLocalizations.of(context)?.scanFolderTitle ?? 'Scan Folder'),
        content: Text(AppLocalizations.of(context)?.scanFolderMessage ??
            'Scan a folder on your device for audiobook files'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text(AppLocalizations.of(context)?.cancel ?? 'Cancel'),
          ),
          ElevatedButton(
            onPressed: () => _scanFolderForAudiobooks(context),
            child: Text(AppLocalizations.of(context)?.scanButtonText ?? 'Scan'),
          ),
        ],
      ),
    );
  }

  Widget _buildActionButton(
    BuildContext context, {
    required IconData icon,
    required String label,
    required VoidCallback onPressed,
  }) {
    final isDesktop = ResponsiveUtils.isDesktop(context);
    final isTablet = ResponsiveUtils.isTablet(context);
    final buttonWidth = isDesktop ? 220.0 : (isTablet ? 200.0 : 180.0);
    final iconSize = ResponsiveUtils.getIconSize(context, baseSize: 20);
    final padding = ResponsiveUtils.getSpacing(context, baseSpacing: 12);

    return SizedBox(
      width: buttonWidth,
      child: OutlinedButton.icon(
        icon: Icon(icon, size: iconSize),
        label: Text(
          label,
          style: TextStyle(
            fontSize: (Theme.of(context).textTheme.bodyMedium?.fontSize ?? 14) *
                ResponsiveUtils.getFontSizeMultiplier(context),
          ),
        ),
        onPressed: onPressed,
        style: OutlinedButton.styleFrom(
          padding: EdgeInsets.symmetric(
            vertical: padding,
            horizontal: padding * 1.33,
          ),
        ),
      ),
    );
  }

  Future<void> _importAudiobookFiles(BuildContext context) async {
    Navigator.pop(context);
    // Save context-dependent objects before async operations
    final messenger = ScaffoldMessenger.of(context);
    final localizations = AppLocalizations.of(context);

    try {
      final result = await FilePicker.platform.pickFiles(
        type: FileType.audio,
        allowMultiple: true,
        allowedExtensions: ['mp3', 'm4a', 'm4b', 'aac', 'flac', 'wav'],
      );

      if (result != null && result.files.isNotEmpty) {
        final files = result.files;
        final importedCount = await _copyAudioFilesToLibrary(files);

        if (!mounted) return;
        // Reload library after import to show new groups
        await _loadLocalAudiobooks();
        if (!mounted) return;
        messenger.showSnackBar(
          SnackBar(
            content: Text(localizations?.importedSuccess(importedCount) ??
                'Imported $importedCount audiobook(s)'),
          ),
        );
      } else {
        if (!mounted) return;
        messenger.showSnackBar(
          SnackBar(
            content: Text(
                localizations?.noFilesSelectedMessage ?? 'No files selected'),
          ),
        );
      }
    } on Exception catch (e) {
      // Handle "already_active" error gracefully
      if (e.toString().contains('already_active')) {
        if (!mounted) return;
        messenger.showSnackBar(
          const SnackBar(
            content:
                Text('File picker is already open. Please close it first.'),
          ),
        );
        return;
      }

      if (!mounted) return;
      messenger.showSnackBar(
        SnackBar(
          content: Text(
            'Import failed: ${e.toString()}',
          ),
        ),
      );
    }
  }

  Future<void> _scanFolderForAudiobooks(BuildContext context) async {
    Navigator.pop(context);
    // Save context-dependent objects before async operations
    final messenger = ScaffoldMessenger.of(context);
    final localizations = AppLocalizations.of(context);

    try {
      // Use the utility function which has better error handling
      final directory = await file_picker_utils.pickDirectory();

      if (directory != null) {
        ref.read(isScanningProvider.notifier).state = true;
        try {
          // Scan recursively for audio files
          final audiobooks = await _scanner.scanDirectory(
            directory,
            recursive: true,
          );

          if (!mounted) return;
          // For now, reload all groups after scan
          // TODO: Implement smarter merging logic
          await _loadLocalAudiobooks();
          if (!mounted) return;
          ref.read(isScanningProvider.notifier).state = false;

          if (!mounted) return;
          if (audiobooks.isNotEmpty) {
            messenger.showSnackBar(
              SnackBar(
                content: Text(
                    localizations?.scanSuccessMessage(audiobooks.length) ??
                        'Scanned and found ${audiobooks.length} audiobook(s)'),
              ),
            );
          } else {
            messenger.showSnackBar(
              SnackBar(
                content: Text(localizations?.noAudiobooksFoundMessage ??
                    'No audiobook files found in selected folder'),
              ),
            );
          }
        } on Exception catch (e) {
          if (!mounted) return;
          ref.read(isScanningProvider.notifier).state = false;
          messenger.showSnackBar(
            SnackBar(
              content: Text(
                'Scan failed: ${e.toString()}',
              ),
            ),
          );
        }
        return;
      }

      // Directory is null - user may have cancelled or there was an issue
      if (!mounted) return;
      messenger.showSnackBar(
        SnackBar(
          content: Text(localizations?.noFolderSelectedMessage ??
              'No folder selected. Please try again or check app permissions.'),
          duration: const Duration(seconds: 3),
        ),
      );
    } on Exception catch (e) {
      // Handle "already_active" error gracefully
      if (e.toString().contains('already_active')) {
        if (!mounted) return;
        messenger.showSnackBar(
          const SnackBar(
            content:
                Text('File picker is already open. Please close it first.'),
          ),
        );
        return;
      }

      if (!mounted) return;
      messenger.showSnackBar(
        SnackBar(
          content: Text(
            'Scan failed: ${e.toString()}',
          ),
        ),
      );
    }
  }

  bool _isAudioFile(String path) {
    final extensions = ['.mp3', '.m4a', '.m4b', '.aac', '.flac', '.wav'];
    return extensions.any((ext) => path.toLowerCase().endsWith(ext));
  }

  Future<int> _copyAudioFilesToLibrary(List<PlatformFile> files) async {
    var importedCount = 0;
    // Use default audiobook path from StoragePathUtils
    final storageUtils = StoragePathUtils();
    final libraryDir = await storageUtils.getDefaultAudiobookPath();
    final audiobooksDir = Directory(libraryDir);

    if (!await audiobooksDir.exists()) {
      await audiobooksDir.create(recursive: true);
    }

    for (final file in files) {
      if (file.path != null && _isAudioFile(file.path!)) {
        final sourceFile = File(file.path!);
        final destFile = File('${audiobooksDir.path}/${file.name}');

        try {
          await sourceFile.copy(destFile.path);
          importedCount++;
        } on Exception catch (e) {
          // Log error but continue with other files
          debugPrint('Failed to copy file ${file.name}: $e');
        }
      }
    }

    return importedCount;
  }

  Widget _buildAudiobookGroupTile(
    BuildContext context,
    LocalAudiobookGroup group,
  ) =>
      // Build cover widget with caching optimization
      // Try embedded artwork from metadata if no coverPath
      _AudiobookGroupTile(
        group: group,
        onPlay: () {
          // Navigate to local player screen
          context.push('/local-player', extra: group);
        },
        onTap: () {
          // Navigate to local player screen
          context.push('/local-player', extra: group);
        },
        onMenuAction: _handleMenuAction,
      );
}

/// Widget for displaying an audiobook group tile with embedded artwork support.
class _AudiobookGroupTile extends ConsumerStatefulWidget {
  const _AudiobookGroupTile({
    required this.group,
    required this.onPlay,
    required this.onTap,
    this.onMenuAction,
  });

  final LocalAudiobookGroup group;
  final VoidCallback onPlay;
  final VoidCallback onTap;
  final void Function(BuildContext, LocalAudiobookGroup, String)? onMenuAction;

  @override
  ConsumerState<_AudiobookGroupTile> createState() =>
      _AudiobookGroupTileState();
}

class _AudiobookGroupTileState extends ConsumerState<_AudiobookGroupTile> {
  String? _embeddedArtworkPath;
  bool _isLoadingArtwork = false;

  @override
  void initState() {
    super.initState();
    // Try to extract embedded artwork if no coverPath
    if (widget.group.coverPath == null && widget.group.files.isNotEmpty) {
      _loadEmbeddedArtwork();
    }
  }

  Future<void> _loadEmbeddedArtwork() async {
    if (_isLoadingArtwork) return;
    _isLoadingArtwork = true;

    try {
      // Try to extract artwork from first file in group
      final firstFile = widget.group.files.first;
      final nativePlayer = NativeAudioPlayer();
      final artworkPath = await nativePlayer.extractArtworkFromFile(
        firstFile.filePath,
      );

      if (artworkPath != null && mounted) {
        final artworkFile = File(artworkPath);
        if (artworkFile.existsSync()) {
          setState(() {
            _embeddedArtworkPath = artworkPath;
          });
        }
      }
    } on Exception catch (e) {
      // Silently fail - embedded artwork is optional
      debugPrint('Failed to load embedded artwork: $e');
    } finally {
      if (mounted) {
        setState(() {
          _isLoadingArtwork = false;
        });
      }
    }
  }

  Widget _buildCoverWidget() {
    const coverSize = 56.0;

    // Try embedded artwork first (if available)
    if (_embeddedArtworkPath != null) {
      final embeddedFile = File(_embeddedArtworkPath!);
      if (embeddedFile.existsSync()) {
        return RepaintBoundary(
          child: Image.file(
            embeddedFile,
            width: coverSize,
            height: coverSize,
            fit: BoxFit.cover,
            cacheWidth: 112, // 2x for retina displays
            errorBuilder: (context, error, stackTrace) =>
                _buildGroupCover(coverSize),
          ),
        );
      }
    }

    // Fallback to group cover path
    if (widget.group.coverPath != null) {
      final coverFile = File(widget.group.coverPath!);
      if (coverFile.existsSync()) {
        return RepaintBoundary(
          child: Image.file(
            coverFile,
            width: coverSize,
            height: coverSize,
            fit: BoxFit.cover,
            cacheWidth: 112, // 2x for retina displays
            errorBuilder: (context, error, stackTrace) =>
                const Icon(Icons.audiotrack, size: 56),
          ),
        );
      }
    }

    // Default icon
    return const Icon(Icons.audiotrack, size: 56);
  }

  Widget _buildGroupCover(double coverSize) {
    if (widget.group.coverPath != null) {
      final coverFile = File(widget.group.coverPath!);
      if (coverFile.existsSync()) {
        return RepaintBoundary(
          child: Image.file(
            coverFile,
            width: coverSize,
            height: coverSize,
            fit: BoxFit.cover,
            cacheWidth: 112,
            errorBuilder: (context, error, stackTrace) =>
                const Icon(Icons.audiotrack, size: 56),
          ),
        );
      }
    }
    return const Icon(Icons.audiotrack, size: 56);
  }

  @override
  Widget build(BuildContext context) {
    final coverWidget = _buildCoverWidget();

    return ListTile(
      leading: ClipRRect(
        borderRadius: BorderRadius.circular(4),
        child: coverWidget,
      ),
      title: Text(widget.group.groupName),
      subtitle: Text(
        '${widget.group.fileCount} ${widget.group.fileCount == 1 ? 'file' : 'files'} • ${widget.group.formattedTotalSize}',
      ),
      trailing: ref.watch(isScanningProvider)
          ? const SizedBox(
              width: 20,
              height: 20,
              child: CircularProgressIndicator(strokeWidth: 2),
            )
          : Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                IconButton(
                  icon: const Icon(Icons.play_arrow),
                  onPressed: widget.onPlay,
                  tooltip: 'Play',
                ),
                PopupMenuButton<String>(
                  icon: const Icon(Icons.more_vert),
                  tooltip: 'More options',
                  onSelected: (value) {
                    // Pass context and group to parent for handling
                    widget.onMenuAction?.call(context, widget.group, value);
                  },
                  itemBuilder: (context) => [
                    PopupMenuItem(
                      value: 'info',
                      child: Row(
                        children: [
                          const Icon(Icons.info_outline, size: 20),
                          const SizedBox(width: 8),
                          Text(
                            AppLocalizations.of(context)?.showInfoButton ??
                                'Show Info',
                          ),
                        ],
                      ),
                    ),
                    PopupMenuItem(
                      value: 'remove',
                      child: Row(
                        children: [
                          const Icon(Icons.remove_circle_outline, size: 20),
                          const SizedBox(width: 8),
                          Text(
                            AppLocalizations.of(context)
                                    ?.removeFromLibraryButton ??
                                'Remove from Library',
                          ),
                        ],
                      ),
                    ),
                    PopupMenuItem(
                      value: 'delete',
                      child: Row(
                        children: [
                          Icon(
                            Icons.delete_outline,
                            size: 20,
                            color: Theme.of(context).colorScheme.error,
                          ),
                          const SizedBox(width: 8),
                          Text(
                            AppLocalizations.of(context)?.deleteFilesButton ??
                                'Delete Files',
                            style: TextStyle(
                              color: Theme.of(context).colorScheme.error,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ],
            ),
      onTap: widget.onTap,
    );
  }
}

extension _LibraryContentStateExtension on _LibraryContentState {
  void _handleMenuAction(
    BuildContext context,
    LocalAudiobookGroup group,
    String action,
  ) async {
    switch (action) {
      case 'info':
        _showGroupInfo(context, group);
        break;
      case 'remove':
        await _removeFromLibrary(context, group);
        break;
      case 'delete':
        await _deleteGroup(context, group);
        break;
    }
  }

  void _showGroupInfo(BuildContext context, LocalAudiobookGroup group) {
    showDialog(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(group.groupName),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _buildInfoRow(
                context,
                Icons.folder,
                AppLocalizations.of(context)?.path ?? 'Path',
                group.groupPath,
              ),
              const SizedBox(height: 8),
              _buildInfoRow(
                context,
                Icons.audiotrack,
                AppLocalizations.of(context)?.fileCount ?? 'Files',
                '${group.fileCount}',
              ),
              const SizedBox(height: 8),
              _buildInfoRow(
                context,
                Icons.storage,
                AppLocalizations.of(context)?.totalSize ?? 'Total Size',
                group.formattedTotalSize,
              ),
              if (group.torrentId != null) ...[
                const SizedBox(height: 8),
                _buildInfoRow(
                  context,
                  Icons.link,
                  'Torrent ID',
                  group.torrentId!,
                ),
              ],
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(),
            child: Text(AppLocalizations.of(context)?.close ?? 'Close'),
          ),
        ],
      ),
    );
  }

  Widget _buildInfoRow(
    BuildContext context,
    IconData icon,
    String label,
    String value,
  ) =>
      Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(icon,
                size: 20, color: Theme.of(context).colorScheme.onSurface),
            const SizedBox(width: 8),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    label,
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    value,
                    style: Theme.of(context).textTheme.bodyMedium,
                  ),
                ],
              ),
            ),
          ],
        ),
      );

  Future<void> _removeFromLibrary(
    BuildContext context,
    LocalAudiobookGroup group,
  ) async {
    final localizations = AppLocalizations.of(context);
    final messenger = ScaffoldMessenger.of(context);

    // Show confirmation
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(
          localizations?.removeFromLibraryTitle ?? 'Remove from Library?',
        ),
        content: Text(
          localizations?.removeFromLibraryMessage ??
              'This will remove the audiobook from your library but will not delete the files. You can add it back by rescanning.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: Text(localizations?.cancel ?? 'Cancel'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: Text(
              localizations?.removeFromLibraryButton ?? 'Remove',
            ),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    // Show loading
    if (!mounted) return;
    messenger.showSnackBar(
      SnackBar(
        content: Text(
          localizations?.removingFromLibraryMessage ?? 'Removing...',
        ),
        duration: const Duration(seconds: 1),
      ),
    );

    try {
      await _fileManager.removeFromLibrary(group);
      await _loadLocalAudiobooks();

      if (!mounted) return;
      messenger.showSnackBar(
        SnackBar(
          content: Text(
            localizations?.removedFromLibrarySuccessMessage ??
                'Removed from library successfully',
          ),
          backgroundColor: Colors.green,
        ),
      );
    } on Exception catch (e) {
      if (!mounted) return;
      messenger.showSnackBar(
        SnackBar(
          content: Text(
            localizations?.removeFromLibraryFailedMessage ??
                'Failed to remove from library: ${e.toString()}',
          ),
          backgroundColor: Colors.red,
        ),
      );
    }
  }

  Future<void> _deleteGroup(
    BuildContext context,
    LocalAudiobookGroup group, {
    bool isRetry = false,
  }) async {
    final localizations = AppLocalizations.of(context);
    final messenger = ScaffoldMessenger.of(context);

    // Calculate total size
    final totalSize = await _fileManager.calculateGroupSize(group);

    // Show confirmation dialog (skip on retry)
    DeleteAction? action;
    if (!isRetry) {
      if (!mounted) return;
      final dialogContext = context;
      action = await DeleteConfirmationDialog.show(
        // ignore: use_build_context_synchronously
        dialogContext,
        group,
        totalSize: totalSize,
      );

      if (!mounted || action == null || action == DeleteAction.cancel) {
        return;
      }
    } else {
      // On retry, use the same action as before (delete files)
      action = DeleteAction.deleteFiles;
    }

    // Show loading
    if (!mounted) return;
    messenger.showSnackBar(
      SnackBar(
        content: Text(
          localizations?.deletingMessage ?? 'Deleting...',
        ),
        duration: const Duration(seconds: 2),
      ),
    );

    try {
      if (action == DeleteAction.deleteFiles) {
        // Physical deletion with detailed result
        final result = await _fileManager.deleteGroupDetailed(
          group,
        );
        if (!mounted) return;

        if (result.success) {
          await _loadLocalAudiobooks();
          if (result.isPartialSuccess) {
            messenger.showSnackBar(
              SnackBar(
                content: Text(
                  localizations?.partialDeletionSuccessMessage(
                        result.deletedCount,
                        result.totalCount,
                      ) ??
                      'Partially deleted: ${result.deletedCount}/${result.totalCount} files',
                ),
                backgroundColor: Colors.orange,
                action: SnackBarAction(
                  label: localizations?.showDetailsButton ?? 'Details',
                  onPressed: () {
                    _showDeletionDetails(context, result, group);
                  },
                ),
              ),
            );
          } else {
            messenger.showSnackBar(
              SnackBar(
                content: Text(
                  localizations?.deletedSuccessMessage ??
                      'Files deleted successfully',
                ),
                backgroundColor: Colors.green,
              ),
            );
          }
        } else {
          messenger.showSnackBar(
            SnackBar(
              content: Text(
                localizations?.deleteFailedMessage ?? 'Failed to delete files',
              ),
              backgroundColor: Colors.red,
              duration: const Duration(seconds: 6),
              action: SnackBarAction(
                label: localizations?.retryButton ?? 'Retry',
                onPressed: () {
                  messenger.hideCurrentSnackBar();
                  _deleteGroup(context, group, isRetry: true);
                },
              ),
            ),
          );
        }
      } else if (action == DeleteAction.removeFromLibrary) {
        // Logical deletion
        await _fileManager.removeFromLibrary(group);
        await _loadLocalAudiobooks();

        if (!mounted) return;
        messenger.showSnackBar(
          SnackBar(
            content: Text(
              localizations?.removedFromLibrarySuccessMessage ??
                  'Removed from library successfully',
            ),
            backgroundColor: Colors.green,
          ),
        );
      }
    } on FileInUseException {
      if (!mounted) return;
      messenger.showSnackBar(
        SnackBar(
          content: Text(
            localizations?.fileInUseMessage ??
                'Cannot delete: File is currently being played',
          ),
          backgroundColor: Colors.orange,
          duration: const Duration(seconds: 3),
        ),
      );
    } on PermissionDeniedException {
      if (!mounted) return;
      messenger.showSnackBar(
        SnackBar(
          content: Text(
            localizations?.permissionDeniedMessage ??
                'Permission denied: Cannot delete file',
          ),
          backgroundColor: Colors.red,
        ),
      );
    } on Exception catch (e) {
      if (!mounted) return;
      messenger.showSnackBar(
        SnackBar(
          content: Text(
            localizations?.deleteFailedMessage ??
                'Failed to delete: ${e.toString()}',
          ),
          backgroundColor: Colors.red,
        ),
      );
    }
  }

  void _showDeletionDetails(
    BuildContext context,
    DeletionResult result,
    LocalAudiobookGroup? group,
  ) {
    final localizations = AppLocalizations.of(context);
    showDialog(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(
          localizations?.deletionDetailsTitle ?? 'Deletion Details',
        ),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                localizations?.deletionSummaryMessage(
                      result.deletedCount,
                      result.totalCount,
                    ) ??
                    'Deleted: ${result.deletedCount}/${result.totalCount} files',
                style: Theme.of(context).textTheme.bodyLarge,
              ),
              if (result.errors.isNotEmpty) ...[
                const SizedBox(height: 16),
                Text(
                  localizations?.errorsTitle ?? 'Errors:',
                  style: Theme.of(context).textTheme.titleSmall,
                ),
                const SizedBox(height: 8),
                ...result.errors.map(
                  (error) => Padding(
                    padding: const EdgeInsets.only(bottom: 4),
                    child: Text(
                      '• $error',
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ),
                ),
              ],
            ],
          ),
        ),
        actions: [
          if (group != null && !result.success)
            TextButton(
              onPressed: () {
                Navigator.of(dialogContext).pop();
                _deleteGroup(context, group, isRetry: true);
              },
              child: Text(localizations?.retryButton ?? 'Retry'),
            ),
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(),
            child: Text(localizations?.close ?? 'Close'),
          ),
        ],
      ),
    );
  }
}
