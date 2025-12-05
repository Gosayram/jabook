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
import 'package:jabook/l10n/app_localizations.dart';

/// Library folder section widget.
class LibraryFolderSection extends StatelessWidget {
  /// Creates a new LibraryFolderSection instance.
  const LibraryFolderSection({
    super.key,
    required this.libraryFolderPath,
    required this.libraryFolders,
    required this.onSelectLibraryFolder,
    required this.onAddLibraryFolder,
    required this.onRemoveLibraryFolder,
    required this.onRestoreFolderPermission,
    required this.buildLibraryFolderItem,
  });

  /// Current library folder path.
  final String? libraryFolderPath;

  /// List of all library folders.
  final List<String> libraryFolders;

  /// Callback when library folder selection is requested.
  final VoidCallback onSelectLibraryFolder;

  /// Callback when add library folder is requested.
  final VoidCallback onAddLibraryFolder;

  /// Callback when remove library folder is requested.
  final void Function(String folder) onRemoveLibraryFolder;

  /// Callback when restore folder permission is requested.
  final void Function(String folder) onRestoreFolderPermission;

  /// Builder for library folder item.
  final Widget Function(
    BuildContext context,
    String folder,
    bool isPrimary,
  ) buildLibraryFolderItem;

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          localizations?.libraryFolderTitle ?? 'Library Folders',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 8),
        Text(
          localizations?.libraryFolderDescription ??
              'Select folders where your audiobooks are stored',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 16),
        // Current library folder
        Semantics(
          label: 'Library folder location',
          child: ListTile(
            leading: const Icon(Icons.folder),
            title: Text(localizations?.libraryFolderTitle ?? 'Library Folder'),
            subtitle: libraryFolderPath != null
                ? Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const SizedBox(height: 4),
                      Row(
                        children: [
                          Icon(
                            Icons.folder_outlined,
                            size: 16,
                            color: Theme.of(context)
                                .colorScheme
                                .onSurface
                                .withValues(alpha: 0.6),
                          ),
                          const SizedBox(width: 4),
                          Expanded(
                            child: Text(
                              libraryFolderPath!,
                              style: Theme.of(context)
                                  .textTheme
                                  .bodySmall
                                  ?.copyWith(
                                    color: Theme.of(context)
                                        .colorScheme
                                        .onSurface
                                        .withValues(alpha: 0.6),
                                  ),
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ],
                      ),
                    ],
                  )
                : Text(
                    localizations?.defaultLibraryFolder ?? 'Default folder',
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          color: Theme.of(context)
                              .colorScheme
                              .onSurface
                              .withValues(alpha: 0.6),
                        ),
                  ),
            trailing: Semantics(
              button: true,
              label: 'Change library folder',
              child: IconButton(
                icon: const Icon(Icons.edit),
                onPressed: onSelectLibraryFolder,
                tooltip: 'Change folder',
              ),
            ),
          ),
        ),
        // Add library folder button
        ListTile(
          leading: const Icon(Icons.add),
          title: Text(
              localizations?.addLibraryFolderTitle ?? 'Add Library Folder'),
          subtitle: Text(
            localizations?.addLibraryFolderSubtitle ??
                'Add an additional folder to scan for audiobooks',
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: Theme.of(context)
                      .colorScheme
                      .onSurface
                      .withValues(alpha: 0.6),
                ),
          ),
          trailing: const Icon(Icons.arrow_forward_ios),
          onTap: onAddLibraryFolder,
        ),
        // List of all library folders
        if (libraryFolders.length > 1)
          ExpansionTile(
            leading: const Icon(Icons.folder),
            title: Text(
              localizations?.allLibraryFoldersTitle ??
                  'All Library Folders (${libraryFolders.length})',
            ),
            children: libraryFolders.map((folder) {
              final isPrimary = folder == libraryFolderPath;
              return buildLibraryFolderItem(context, folder, isPrimary);
            }).toList(),
          ),
      ],
    );
  }
}
