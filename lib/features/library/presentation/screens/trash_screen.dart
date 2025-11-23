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
import 'package:intl/intl.dart';
import 'package:jabook/core/library/trash_service.dart';
import 'package:share_plus/share_plus.dart';

/// Screen for managing trash (recovery) of deleted files.
///
/// This screen allows users to:
/// - View deleted files and groups
/// - Restore files from trash
/// - Permanently delete items
/// - Clear all trash
/// - View trash statistics
class TrashScreen extends StatefulWidget {
  /// Creates a new TrashScreen instance.
  const TrashScreen({super.key});

  @override
  State<TrashScreen> createState() => _TrashScreenState();
}

class _TrashScreenState extends State<TrashScreen> {
  final TrashService _trashService = TrashService();
  List<TrashItem> _trashItems = [];
  bool _isLoading = true;
  int _totalSize = 0;
  bool _trashEnabled = true;

  @override
  void initState() {
    super.initState();
    _loadTrashItems();
  }

  Future<void> _loadTrashItems() async {
    setState(() => _isLoading = true);
    try {
      final items = await _trashService.getTrashItems();
      final size = await _trashService.getTrashSize();
      final enabled = await _trashService.isTrashEnabled();
      if (mounted) {
        setState(() {
          _trashItems = items;
          _totalSize = size;
          _trashEnabled = enabled;
          _isLoading = false;
        });
      }
    } on Exception catch (e) {
      if (mounted) {
        setState(() => _isLoading = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Failed to load trash: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  Future<void> _restoreItem(TrashItem item) async {
    try {
      final success = item.groupPath != null
          ? await _trashService.restoreGroupFromTrash(item)
          : await _trashService.restoreFromTrash(item);

      if (mounted) {
        if (success) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Item restored successfully'),
              backgroundColor: Colors.green,
            ),
          );
          await _loadTrashItems();
        } else {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Failed to restore item'),
              backgroundColor: Colors.red,
            ),
          );
        }
      }
    } on Exception catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Error restoring item: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  Future<void> _permanentlyDeleteItem(TrashItem item) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Permanently Delete'),
        content: Text(
          'Are you sure you want to permanently delete "${item.groupName}"? This action cannot be undone.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.of(context).pop(true),
            style: TextButton.styleFrom(
              foregroundColor: Colors.red,
            ),
            child: const Text('Delete'),
          ),
        ],
      ),
    );

    if (confirmed ?? false) {
      try {
        final success = await _trashService.permanentlyDelete(item);
        if (mounted) {
          if (success) {
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(
                content: Text('Item permanently deleted'),
                backgroundColor: Colors.green,
              ),
            );
            await _loadTrashItems();
          } else {
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(
                content: Text('Failed to delete item'),
                backgroundColor: Colors.red,
              ),
            );
          }
        }
      } on Exception catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Error deleting item: $e'),
              backgroundColor: Colors.red,
            ),
          );
        }
      }
    }
  }

  Future<void> _clearAll() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Clear All Trash'),
        content: const Text(
          'Are you sure you want to permanently delete all items in trash? This action cannot be undone.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.of(context).pop(true),
            style: TextButton.styleFrom(
              foregroundColor: Colors.red,
            ),
            child: const Text('Clear All'),
          ),
        ],
      ),
    );

    if (confirmed ?? false) {
      try {
        final count = await _trashService.clearAll();
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('$count items permanently deleted'),
              backgroundColor: Colors.green,
            ),
          );
          await _loadTrashItems();
        }
      } on Exception catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Error clearing trash: $e'),
              backgroundColor: Colors.red,
            ),
          );
        }
      }
    }
  }

  Future<void> _clearOldItems() async {
    try {
      final count = await _trashService.clearOldItems();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('$count old items cleared'),
            backgroundColor: Colors.green,
          ),
        );
        await _loadTrashItems();
      }
    } on Exception catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Error clearing old items: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  Future<void> _exportTrash(String format) async {
    try {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Exporting trash items...'),
          duration: Duration(seconds: 1),
        ),
      );

      // Export to file
      final filePath = await _trashService.exportToFile(format: format);
      final file = File(filePath);

      if (await file.exists()) {
        // Share the file
        final params = ShareParams(
          files: [XFile(filePath)],
          subject: 'JaBook Trash Export',
          text: 'JaBook deleted files export ($format format)',
        );
        await SharePlus.instance.share(params);

        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Trash exported successfully as $format'),
              backgroundColor: Colors.green,
            ),
          );
        }
      }
    } on Exception catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Failed to export: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  String _formatSize(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    if (bytes < 1024 * 1024 * 1024) {
      return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
    }
    return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(1)} GB';
  }

  String _formatDate(DateTime date) =>
      DateFormat('MMM d, yyyy HH:mm').format(date);

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
          title: const Text('Trash'),
          actions: [
            if (_trashItems.isNotEmpty)
              PopupMenuButton<String>(
                onSelected: (value) {
                  switch (value) {
                    case 'export_csv':
                      _exportTrash('csv');
                      break;
                    case 'export_json':
                      _exportTrash('json');
                      break;
                    case 'clear_old':
                      _clearOldItems();
                      break;
                    case 'clear_all':
                      _clearAll();
                      break;
                  }
                },
                itemBuilder: (context) => [
                  const PopupMenuItem(
                    value: 'export_csv',
                    child: Row(
                      children: [
                        Icon(Icons.file_download, size: 20),
                        SizedBox(width: 8),
                        Text('Export as CSV'),
                      ],
                    ),
                  ),
                  const PopupMenuItem(
                    value: 'export_json',
                    child: Row(
                      children: [
                        Icon(Icons.file_download, size: 20),
                        SizedBox(width: 8),
                        Text('Export as JSON'),
                      ],
                    ),
                  ),
                  const PopupMenuDivider(),
                  const PopupMenuItem(
                    value: 'clear_old',
                    child: Row(
                      children: [
                        Icon(Icons.delete_sweep, size: 20),
                        SizedBox(width: 8),
                        Text('Clear Old Items'),
                      ],
                    ),
                  ),
                  const PopupMenuItem(
                    value: 'clear_all',
                    child: Row(
                      children: [
                        Icon(Icons.delete_forever, size: 20),
                        SizedBox(width: 8),
                        Text(
                          'Clear All',
                          style: TextStyle(color: Colors.red),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
          ],
        ),
        body: _isLoading
            ? const Center(child: CircularProgressIndicator())
            : _trashItems.isEmpty
                ? Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(
                          Icons.delete_outline,
                          size: 64,
                          color: Colors.grey[400],
                        ),
                        const SizedBox(height: 16),
                        Text(
                          'Trash is empty',
                          style:
                              Theme.of(context).textTheme.titleLarge?.copyWith(
                                    color: Colors.grey[600],
                                  ),
                        ),
                      ],
                    ),
                  )
                : RefreshIndicator(
                    onRefresh: _loadTrashItems,
                    child: Column(
                      children: [
                        // Summary section
                        Container(
                          padding: const EdgeInsets.all(16),
                          color: Theme.of(context)
                              .colorScheme
                              .surfaceContainerHighest,
                          child: Row(
                            children: [
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(
                                      '${_trashItems.length} items',
                                      style: Theme.of(context)
                                          .textTheme
                                          .titleMedium,
                                    ),
                                    const SizedBox(height: 4),
                                    Text(
                                      'Total size: ${_formatSize(_totalSize)}',
                                      style:
                                          Theme.of(context).textTheme.bodySmall,
                                    ),
                                  ],
                                ),
                              ),
                              if (!_trashEnabled)
                                Chip(
                                  label: const Text(
                                    'Disabled',
                                    style: TextStyle(fontSize: 12),
                                  ),
                                  backgroundColor: Colors.orange[100],
                                ),
                            ],
                          ),
                        ),
                        // Items list
                        Expanded(
                          child: ListView.builder(
                            itemCount: _trashItems.length,
                            itemBuilder: (context, index) {
                              final item = _trashItems[index];
                              return _buildTrashItemTile(item);
                            },
                          ),
                        ),
                      ],
                    ),
                  ),
      );

  Widget _buildTrashItemTile(TrashItem item) {
    final ageInDays = item.ageInDays;
    final isOld = ageInDays >= 30;

    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: isOld ? Colors.orange[100] : Colors.grey[200],
          child: Icon(
            item.groupPath != null ? Icons.folder : Icons.audiotrack,
            color: isOld ? Colors.orange[800] : Colors.grey[700],
          ),
        ),
        title: Text(
          item.groupName,
          style: TextStyle(
            fontWeight: FontWeight.bold,
            color: isOld ? Colors.orange[800] : null,
          ),
        ),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const SizedBox(height: 4),
            Text(
              '${item.fileCount} ${item.fileCount == 1 ? 'file' : 'files'} • ${_formatSize(item.totalSize)}',
            ),
            const SizedBox(height: 4),
            Text(
              'Deleted: ${_formatDate(item.deletedAt)}',
              style: Theme.of(context).textTheme.bodySmall,
            ),
            if (ageInDays > 0)
              Text(
                'Age: $ageInDays ${ageInDays == 1 ? 'day' : 'days'}',
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: isOld ? Colors.orange[800] : Colors.grey[600],
                    ),
              ),
          ],
        ),
        trailing: PopupMenuButton<String>(
          onSelected: (value) {
            switch (value) {
              case 'restore':
                _restoreItem(item);
                break;
              case 'delete':
                _permanentlyDeleteItem(item);
                break;
            }
          },
          itemBuilder: (context) => [
            const PopupMenuItem(
              value: 'restore',
              child: Row(
                children: [
                  Icon(Icons.restore, size: 20),
                  SizedBox(width: 8),
                  Text('Restore'),
                ],
              ),
            ),
            const PopupMenuItem(
              value: 'delete',
              child: Row(
                children: [
                  Icon(Icons.delete_forever, size: 20, color: Colors.red),
                  SizedBox(width: 8),
                  Text(
                    'Permanently Delete',
                    style: TextStyle(color: Colors.red),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
