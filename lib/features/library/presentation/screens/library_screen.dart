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

import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:jabook/core/favorites/favorites_service.dart';
import 'package:jabook/data/db/app_database.dart';
import 'package:jabook/l10n/app_localizations.dart';
import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';

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
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
          title: Text(AppLocalizations.of(context)?.libraryTitle ?? 'Library'),
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
              tooltip: AppLocalizations.of(context)?.searchAudiobooks ?? 'Search',
            ),
            IconButton(
              icon: const Icon(Icons.filter_list),
              onPressed: () {
                // Show filter options - navigate to settings for now
                context.go('/settings');
              },
              tooltip: AppLocalizations.of(context)?.filterLibraryTooltip ??
                  'Filter library',
            ),
          ],
        ),
        body: const _LibraryContent(),
        floatingActionButton: FloatingActionButton(
          onPressed: () {
            // Navigate to search screen for now - FAB functionality
            context.go('/search');
          },
          tooltip: AppLocalizations.of(context)?.addAudiobookTooltip ??
              'Add audiobook',
          child: const Icon(Icons.add),
        ),
      );
}

/// Private widget for displaying the main library content.
///
/// This widget contains the actual content of the library screen,
/// including the list of audiobooks and any filtering/sorting options.
class _LibraryContent extends ConsumerWidget {
  /// Creates a new _LibraryContent instance.
  const _LibraryContent();

  @override
  Widget build(BuildContext context, WidgetRef ref) => Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Semantics(
              label: 'Empty library',
              child: const Icon(Icons.library_books_outlined,
                  size: 64, color: Colors.grey),
            ),
            const SizedBox(height: 16),
            Text(
              AppLocalizations.of(context)?.libraryEmptyMessage ??
                  'Your library is empty',
              style: Theme.of(context).textTheme.titleMedium,
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 8),
            Text(
              AppLocalizations.of(context)?.addAudiobooksHint ??
                  'Add audiobooks to your library to start listening',
              style: Theme.of(context)
                  .textTheme
                  .bodyMedium
                  ?.copyWith(color: Colors.grey),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 24),
            Column(
              mainAxisSize: MainAxisSize.min,
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
                const SizedBox(height: 12),
                Semantics(
                  button: true,
                  label: 'Import audiobooks from files',
                  child: _buildActionButton(
                    context,
                    icon: Icons.folder_open,
                    label: AppLocalizations.of(context)
                            ?.importFromFilesButton ??
                        'Import from Files',
                    onPressed: () => _showImportDialog(context),
                  ),
                ),
                const SizedBox(height: 12),
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
      );

  Widget _buildActionButton(
    BuildContext context, {
    required IconData icon,
    required String label,
    required VoidCallback onPressed,
  }) =>
      SizedBox(
        width: 200,
        child: OutlinedButton.icon(
          icon: Icon(icon, size: 20),
          label: Text(label),
          onPressed: onPressed,
          style: OutlinedButton.styleFrom(
            padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 16),
          ),
        ),
      );

  // Removed unused method

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

  Future<void> _importAudiobookFiles(BuildContext context) async {
    Navigator.pop(context);

    try {
      final result = await FilePicker.platform.pickFiles(
        type: FileType.audio,
        allowMultiple: true,
        allowedExtensions: ['mp3', 'm4a', 'm4b', 'aac', 'flac', 'wav'],
      );

      if (result != null && result.files.isNotEmpty) {
        final files = result.files;
        final importedCount = await _copyAudioFilesToLibrary(files);

        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
                content: Text(AppLocalizations.of(context)?.importedSuccess(importedCount) ?? 
                        'Imported $importedCount audiobook(s)'),
            ),
          );
        }
      } else {
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
                content: Text(
                    AppLocalizations.of(context)?.noFilesSelectedMessage ??
                        'No files selected')),
          );
        }
      }
    } on Exception catch (e) {
      // Handle "already_active" error gracefully
      if (e.toString().contains('already_active')) {
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
                content: Text(
                    'File picker is already open. Please close it first.'),
            ),
          );
        }
        return;
      }
      
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
              content: Text(AppLocalizations.of(context)?.importFailedMessage(e.toString()) ?? 
                      'Import failed: ${e.toString()}'),
          ),
        );
      }
    }
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

  Future<void> _scanFolderForAudiobooks(BuildContext context) async {
    Navigator.pop(context);

    try {
      final directory = await FilePicker.platform.getDirectoryPath();

      if (directory != null) {
        final dir = Directory(directory);
        // Remove the torrent manager reference

        // Scan for audio files
        final audioFiles = await dir
            .list()
            .where((entity) => entity is File)
            .map((entity) => entity as File)
            .where((file) => _isAudioFile(file.path))
            .toList();

        if (audioFiles.isNotEmpty) {
          final importedCount = await _copyAudioFilesToLibrary(audioFiles
              .map((file) => PlatformFile(
                    name: path.basename(file.path),
                    path: file.path,
                    size: file.lengthSync(),
                  ))
              .toList());

          if (context.mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                  content: Text(AppLocalizations.of(context)?.scanSuccessMessage(importedCount) ?? 
                          'Scanned and imported $importedCount audiobook(s)'),
              ),
            );
          }
        } else {
          if (context.mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                  content: Text(
                      AppLocalizations.of(context)?.noAudiobooksFoundMessage ??
                          'No audiobook files found in selected folder')),
            );
          }
        }
      } else {
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
                content: Text(
                    AppLocalizations.of(context)?.noFolderSelectedMessage ??
                        'No folder selected')),
          );
        }
      }
    } on Exception catch (e) {
      // Handle "already_active" error gracefully
      if (e.toString().contains('already_active')) {
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
                content: Text(
                    'File picker is already open. Please close it first.'),
            ),
          );
        }
        return;
      }
      
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
              content: Text(AppLocalizations.of(context)?.scanFailedMessage(e.toString()) ?? 
                      'Scan failed: ${e.toString()}'),
          ),
        );
      }
    }
  }

  bool _isAudioFile(String path) {
    final extensions = ['.mp3', '.m4a', '.m4b', '.aac', '.flac', '.wav'];
    return extensions.any((ext) => path.toLowerCase().endsWith(ext));
  }

  Future<int> _copyAudioFilesToLibrary(List<PlatformFile> files) async {
    var importedCount = 0;
    final libraryDir = await getLibraryDirectory();
    final audiobooksDir = Directory('${libraryDir.path}/audiobooks');

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
}
