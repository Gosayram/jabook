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

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:jabook/core/cache/cache_cleanup_service.dart';
import 'package:jabook/core/library/audiobook_file_manager.dart';
import 'package:jabook/core/library/audiobook_library_scanner.dart';
import 'package:jabook/core/library/local_audiobook.dart';
import 'package:jabook/core/library/storage_statistics_service.dart';
import 'package:jabook/core/library/trash_service.dart';
import 'package:jabook/core/utils/storage_path_utils.dart';
import 'package:jabook/features/library/presentation/widgets/delete_confirmation_dialog.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Screen for managing storage, including library size, cache, and file deletion.
class StorageManagementScreen extends StatefulWidget {
  /// Creates a new StorageManagementScreen instance.
  const StorageManagementScreen({super.key});

  @override
  State<StorageManagementScreen> createState() =>
      _StorageManagementScreenState();
}

class _StorageManagementScreenState extends State<StorageManagementScreen> {
  final AudiobookLibraryScanner _scanner = AudiobookLibraryScanner();
  final AudiobookFileManager _fileManager = AudiobookFileManager();
  final CacheCleanupService _cacheService = CacheCleanupService();
  final StoragePathUtils _storageUtils = StoragePathUtils();
  final TrashService _trashService = TrashService();
  final StorageStatisticsService _statisticsService =
      StorageStatisticsService();

  List<LocalAudiobookGroup> _audiobookGroups = [];
  Map<String, int> _folderSizes = {};
  int _totalLibrarySize = 0;
  int _totalCacheSize = 0;
  int _trashSize = 0;
  int _trashItemsCount = 0;
  StorageBreakdown? _storageBreakdown;
  bool _isLoading = true;
  final Set<String> _selectedGroups = {};

  @override
  void initState() {
    super.initState();
    _loadStorageInfo();
  }

  Future<void> _loadStorageInfo() async {
    setState(() => _isLoading = true);

    try {
      // Load audiobook groups
      final groups = await _scanner.scanAllLibraryFolders();
      _audiobookGroups = groups;

      // Calculate sizes
      _totalLibrarySize = 0;
      for (final group in groups) {
        final size = await _fileManager.calculateGroupSize(group);
        _totalLibrarySize += size;
      }

      // Calculate folder sizes
      final libraryFolders = await _storageUtils.getLibraryFolders();
      _folderSizes = {};
      for (final folder in libraryFolders) {
        _folderSizes[folder] = await _calculateFolderSize(folder);
      }

      // Get cache size
      _totalCacheSize = await _cacheService.getTotalCacheSize();

      // Get trash info
      _trashSize = await _trashService.getTrashSize();
      _trashItemsCount = (await _trashService.getTrashItems()).length;

      // Get storage breakdown
      _storageBreakdown = await _statisticsService.getStorageBreakdown();
    } on Exception {
      // Handle error
    } finally {
      if (mounted) {
        setState(() => _isLoading = false);
      }
    }
  }

  Future<int> _calculateFolderSize(String folderPath) async {
    try {
      final dir = Directory(folderPath);
      if (!await dir.exists()) return 0;

      var totalSize = 0;
      await for (final entity in dir.list(recursive: true)) {
        if (entity is File) {
          try {
            final stat = await entity.stat();
            totalSize = (totalSize + stat.size).toInt();
          } on Exception {
            // Continue with other files
          }
        }
      }
      return totalSize;
    } on Exception {
      return 0;
    }
  }

  String _formatSize(int bytes) {
    if (bytes < 1024) {
      return '$bytes B';
    } else if (bytes < 1024 * 1024) {
      return '${(bytes / 1024).toStringAsFixed(1)} KB';
    } else if (bytes < 1024 * 1024 * 1024) {
      return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
    } else {
      return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(1)} GB';
    }
  }

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context);

    return Scaffold(
      appBar: AppBar(
        title: Text(
          localizations?.storageManagementTitle ?? 'Storage Management',
        ),
        actions: [
          if (_selectedGroups.isNotEmpty)
            IconButton(
              icon: const Icon(Icons.delete),
              tooltip: localizations?.deleteSelectedButton ?? 'Delete Selected',
              onPressed: () => _deleteSelectedGroups(context),
            ),
        ],
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: _loadStorageInfo,
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  // Summary section
                  _buildSummarySection(context),
                  const SizedBox(height: 24),
                  // Storage breakdown chart
                  _buildStorageBreakdownSection(context),
                  const SizedBox(height: 24),
                  // Library folders section
                  _buildLibraryFoldersSection(context),
                  const SizedBox(height: 24),
                  // Cache section
                  _buildCacheSection(context),
                  const SizedBox(height: 24),
                  // Trash section
                  _buildTrashSection(context),
                  const SizedBox(height: 24),
                  // Audiobook groups section
                  _buildAudiobookGroupsSection(context),
                ],
              ),
            ),
    );
  }

  Widget _buildSummarySection(BuildContext context) {
    final localizations = AppLocalizations.of(context);

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              localizations?.storageSummaryTitle ?? 'Storage Summary',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 16),
            _buildStatRow(
              context,
              Icons.library_books,
              localizations?.totalLibrarySize ?? 'Total Library Size',
              _formatSize(_totalLibrarySize),
            ),
            const SizedBox(height: 8),
            _buildStatRow(
              context,
              Icons.audiotrack,
              localizations?.audiobookGroupsCount ?? 'Audiobook Groups',
              '${_audiobookGroups.length}',
            ),
            const SizedBox(height: 8),
            _buildStatRow(
              context,
              Icons.storage,
              localizations?.cacheSize ?? 'Cache Size',
              _formatSize(_totalCacheSize),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildStorageBreakdownSection(BuildContext context) {
    if (_storageBreakdown == null || _storageBreakdown!.totalSize == 0) {
      return const SizedBox.shrink();
    }

    final breakdown = _storageBreakdown!;

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Storage Breakdown',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 16),
            // Visual breakdown with progress bars
            _buildBreakdownBar(
              context,
              'Library',
              breakdown.librarySize,
              breakdown.libraryPercentage,
              Colors.blue,
              Icons.library_books,
            ),
            const SizedBox(height: 12),
            _buildBreakdownBar(
              context,
              'Cache',
              breakdown.cacheSize,
              breakdown.cachePercentage,
              Colors.orange,
              Icons.storage,
            ),
            const SizedBox(height: 12),
            _buildBreakdownBar(
              context,
              'Trash',
              breakdown.trashSize,
              breakdown.trashPercentage,
              Colors.red,
              Icons.delete_outline,
            ),
            if (breakdown.otherSize > 0) ...[
              const SizedBox(height: 12),
              _buildBreakdownBar(
                context,
                'Other',
                breakdown.otherSize,
                breakdown.otherPercentage,
                Colors.grey,
                Icons.folder,
              ),
            ],
            const SizedBox(height: 16),
            Divider(color: Colors.grey[300]),
            const SizedBox(height: 8),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  'Total',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                Text(
                  _formatSize(breakdown.totalSize),
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.bold,
                      ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildBreakdownBar(
    BuildContext context,
    String label,
    int size,
    double percentage,
    Color color,
    IconData icon,
  ) =>
      Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  Icon(icon, size: 18, color: color),
                  const SizedBox(width: 8),
                  Text(
                    label,
                    style: Theme.of(context).textTheme.bodyMedium,
                  ),
                ],
              ),
              Text(
                '${_formatSize(size)} (${percentage.toStringAsFixed(1)}%)',
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
              ),
            ],
          ),
          const SizedBox(height: 4),
          ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: LinearProgressIndicator(
              value: percentage / 100,
              backgroundColor: color.withValues(alpha: 0.1),
              valueColor: AlwaysStoppedAnimation<Color>(color),
              minHeight: 8,
            ),
          ),
        ],
      );

  Widget _buildStatRow(
    BuildContext context,
    IconData icon,
    String label,
    String value,
  ) =>
      Row(
        children: [
          Icon(icon, size: 20, color: Theme.of(context).colorScheme.primary),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              label,
              style: Theme.of(context).textTheme.bodyMedium,
            ),
          ),
          Text(
            value,
            style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                  fontWeight: FontWeight.bold,
                ),
          ),
        ],
      );

  Widget _buildLibraryFoldersSection(BuildContext context) {
    final localizations = AppLocalizations.of(context);

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              localizations?.libraryFoldersTitle ?? 'Library Folders',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 12),
            ..._folderSizes.entries.map((entry) => ListTile(
                  leading: const Icon(Icons.folder),
                  title: Text(
                    entry.key,
                    style: Theme.of(context).textTheme.bodyMedium,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  trailing: Text(
                    _formatSize(entry.value),
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                  ),
                )),
          ],
        ),
      ),
    );
  }

  Widget _buildCacheSection(BuildContext context) {
    final localizations = AppLocalizations.of(context);

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              localizations?.cacheSectionTitle ?? 'Cache',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 12),
            _buildStatRow(
              context,
              Icons.storage,
              localizations?.totalCacheSize ?? 'Total Cache',
              _formatSize(_totalCacheSize),
            ),
            const SizedBox(height: 16),
            Wrap(
              spacing: 12,
              runSpacing: 8,
              children: [
                ElevatedButton.icon(
                  onPressed: () => _clearPlaybackCache(context),
                  icon: const Icon(Icons.delete_outline),
                  label: Text(
                    localizations?.clearPlaybackCacheButton ??
                        'Clear Playback Cache',
                  ),
                ),
                OutlinedButton.icon(
                  onPressed: () => _clearAllCache(context),
                  icon: const Icon(Icons.delete_forever),
                  label: Text(
                    localizations?.clearAllCacheButton ?? 'Clear All Cache',
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTrashSection(BuildContext context) => Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'Trash',
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                  if (_trashItemsCount > 0)
                    TextButton.icon(
                      onPressed: () => context.push('/trash'),
                      icon: const Icon(Icons.open_in_new, size: 18),
                      label: const Text('Open'),
                    ),
                ],
              ),
              const SizedBox(height: 12),
              _buildStatRow(
                context,
                Icons.delete_outline,
                'Items in Trash',
                '$_trashItemsCount',
              ),
              const SizedBox(height: 8),
              _buildStatRow(
                context,
                Icons.storage,
                'Trash Size',
                _formatSize(_trashSize),
              ),
              const SizedBox(height: 16),
              Wrap(
                spacing: 12,
                runSpacing: 8,
                children: [
                  if (_trashItemsCount > 0)
                    ElevatedButton.icon(
                      onPressed: () => context.push('/trash'),
                      icon: const Icon(Icons.restore),
                      label: const Text('Manage Trash'),
                    ),
                ],
              ),
            ],
          ),
        ),
      );

  Widget _buildAudiobookGroupsSection(BuildContext context) {
    final localizations = AppLocalizations.of(context);

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  localizations?.audiobookGroupsTitle ?? 'Audiobook Groups',
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                if (_audiobookGroups.isNotEmpty)
                  TextButton.icon(
                    onPressed: () {
                      setState(() {
                        if (_selectedGroups.length == _audiobookGroups.length) {
                          _selectedGroups.clear();
                        } else {
                          _selectedGroups.addAll(
                            _audiobookGroups.map((g) => g.groupPath),
                          );
                        }
                      });
                    },
                    icon: Icon(
                      _selectedGroups.length == _audiobookGroups.length
                          ? Icons.deselect
                          : Icons.select_all,
                    ),
                    label: Text(
                      _selectedGroups.length == _audiobookGroups.length
                          ? localizations?.deselectAllButton ?? 'Deselect All'
                          : localizations?.selectAllButton ?? 'Select All',
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 12),
            if (_audiobookGroups.isEmpty)
              Center(
                child: Padding(
                  padding: const EdgeInsets.all(32),
                  child: Text(
                    localizations?.noAudiobooksMessage ?? 'No audiobooks found',
                    style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                          color: Theme.of(context)
                              .colorScheme
                              .onSurface
                              .withValues(alpha: 0.6),
                        ),
                  ),
                ),
              )
            else
              ..._audiobookGroups
                  .map((group) => _buildGroupTile(context, group)),
          ],
        ),
      ),
    );
  }

  Widget _buildGroupTile(BuildContext context, LocalAudiobookGroup group) {
    final isSelected = _selectedGroups.contains(group.groupPath);
    final size = group.formattedTotalSize;

    return CheckboxListTile(
      value: isSelected,
      onChanged: (value) {
        setState(() {
          if (value ?? false) {
            _selectedGroups.add(group.groupPath);
          } else {
            _selectedGroups.remove(group.groupPath);
          }
        });
      },
      title: Text(group.groupName),
      subtitle: Text(
        '${group.fileCount} ${group.fileCount == 1 ? 'file' : 'files'} • $size',
      ),
      secondary: PopupMenuButton<String>(
        icon: const Icon(Icons.more_vert),
        onSelected: (value) => _handleGroupAction(context, group, value),
        itemBuilder: (context) => [
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
                  AppLocalizations.of(context)?.deleteFilesButton ?? 'Delete',
                  style: TextStyle(
                    color: Theme.of(context).colorScheme.error,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _handleGroupAction(
    BuildContext context,
    LocalAudiobookGroup group,
    String action,
  ) async {
    if (action == 'delete') {
      await _deleteGroup(context, group);
    }
  }

  Future<void> _deleteGroup(
    BuildContext context,
    LocalAudiobookGroup group,
  ) async {
    final localizations = AppLocalizations.of(context);
    final messenger = ScaffoldMessenger.of(context);

    final totalSize = await _fileManager.calculateGroupSize(group);
    if (!mounted) return;
    final dialogContext = context;
    final action = await DeleteConfirmationDialog.show(
      // ignore: use_build_context_synchronously
      dialogContext,
      group,
      totalSize: totalSize,
    );

    if (!mounted) return;
    if (action == null || action == DeleteAction.cancel) return;

    if (!mounted) return;
    messenger.showSnackBar(
      SnackBar(
        content: Text(localizations?.deletingMessage ?? 'Deleting...'),
        duration: const Duration(seconds: 2),
      ),
    );

    try {
      if (action == DeleteAction.deleteFiles) {
        final success = await _fileManager.deleteGroup(group);
        if (!mounted) return;

        if (success) {
          await _loadStorageInfo();
          messenger.showSnackBar(
            SnackBar(
              content: Text(
                localizations?.deletedSuccessMessage ??
                    'Files deleted successfully',
              ),
              backgroundColor: Colors.green,
            ),
          );
        } else {
          messenger.showSnackBar(
            SnackBar(
              content: Text(
                localizations?.deleteFailedMessage ?? 'Failed to delete files',
              ),
              backgroundColor: Colors.red,
            ),
          );
        }
      } else if (action == DeleteAction.removeFromLibrary) {
        await _fileManager.removeFromLibrary(group);
        await _loadStorageInfo();

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

  Future<void> _deleteSelectedGroups(BuildContext context) async {
    final localizations = AppLocalizations.of(context);
    final messenger = ScaffoldMessenger.of(context);

    if (_selectedGroups.isEmpty) return;

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(
          localizations?.deleteSelectedTitle ?? 'Delete Selected?',
        ),
        content: Text(
          localizations?.deleteSelectedMessage(_selectedGroups.length) ??
              'Are you sure you want to delete ${_selectedGroups.length} selected audiobook(s)?',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: Text(localizations?.cancel ?? 'Cancel'),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: Theme.of(context).colorScheme.error,
              foregroundColor: Theme.of(context).colorScheme.onError,
            ),
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: Text(localizations?.deleteFilesButton ?? 'Delete'),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    if (!mounted) return;
    messenger.showSnackBar(
      SnackBar(
        content: Text(localizations?.deletingMessage ?? 'Deleting...'),
        duration: const Duration(seconds: 2),
      ),
    );

    var successCount = 0;
    var failCount = 0;

    for (final groupPath in _selectedGroups.toList()) {
      final group = _audiobookGroups.firstWhere(
        (g) => g.groupPath == groupPath,
        orElse: () => _audiobookGroups.first,
      );

      try {
        final success = await _fileManager.deleteGroup(group);
        if (success) {
          successCount++;
        } else {
          failCount++;
        }
      } on Exception {
        failCount++;
      }
    }

    await _loadStorageInfo();

    if (!mounted) return;
    messenger.showSnackBar(
      SnackBar(
        content: Text(
          localizations?.deleteSelectedResultMessage(successCount, failCount) ??
              'Deleted: $successCount, Failed: $failCount',
        ),
        backgroundColor: failCount == 0 ? Colors.green : Colors.orange,
      ),
    );
  }

  Future<void> _clearPlaybackCache(BuildContext context) async {
    final localizations = AppLocalizations.of(context);
    final messenger = ScaffoldMessenger.of(context);

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(
          localizations?.clearPlaybackCacheTitle ?? 'Clear Playback Cache?',
        ),
        content: Text(
          localizations?.clearPlaybackCacheMessage ??
              'This will clear the playback cache. Playback may be slower until cache is rebuilt.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: Text(localizations?.cancel ?? 'Cancel'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: Text(localizations?.clearButton ?? 'Clear'),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    if (!mounted) return;
    messenger.showSnackBar(
      SnackBar(
        content:
            Text(localizations?.clearingCacheMessage ?? 'Clearing cache...'),
      ),
    );

    final clearedSize = await _cacheService.clearPlaybackCache();
    await _loadStorageInfo();

    if (!mounted) return;
    messenger.showSnackBar(
      SnackBar(
        content: Text(
          localizations?.cacheClearedSuccessMessage ??
              'Cache cleared: ${_formatSize(clearedSize)}',
        ),
        backgroundColor: Colors.green,
      ),
    );
  }

  Future<void> _clearAllCache(BuildContext context) async {
    final localizations = AppLocalizations.of(context);
    final messenger = ScaffoldMessenger.of(context);

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(
          localizations?.clearAllCacheTitle ?? 'Clear All Cache?',
        ),
        content: Text(
          localizations?.clearAllCacheMessage ??
              'This will clear all cache including playback cache, temporary files, and old logs.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: Text(localizations?.cancel ?? 'Cancel'),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: Theme.of(context).colorScheme.error,
              foregroundColor: Theme.of(context).colorScheme.onError,
            ),
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: Text(localizations?.clearAllCacheButton ?? 'Clear All'),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    if (!mounted) return;
    messenger.showSnackBar(
      SnackBar(
        content:
            Text(localizations?.clearingCacheMessage ?? 'Clearing cache...'),
      ),
    );

    await _cacheService.clearPlaybackCache();
    await _cacheService.clearTemporaryFiles();
    await _cacheService.clearOldLogs();
    await _loadStorageInfo();

    if (!mounted) return;
    messenger.showSnackBar(
      SnackBar(
        content: Text(
          localizations?.cacheClearedSuccessMessage ??
              'All cache cleared successfully',
        ),
        backgroundColor: Colors.green,
      ),
    );
  }
}
