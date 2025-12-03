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
import 'package:jabook/core/library/folder_structure_analyzer.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Shows dialog with instructions for selecting a folder.
Future<void> showFolderSelectionInstructions(
  BuildContext context,
  VoidCallback onSelectFolder,
) async {
  if (!context.mounted) return;

  final localizations = AppLocalizations.of(context);
  final dialogTitle =
      localizations?.selectFolderDialogTitle ?? 'Select Download Folder';
  final dialogMessage = localizations?.selectFolderDialogMessage ??
      'To select a download folder:\n\n'
          '1. Navigate to the desired folder in the file manager\n'
          '2. Tap "Use this folder" button in the top right corner\n\n'
          'The selected folder will be used to save downloaded audiobooks.';
  final cancelText = localizations?.cancel ?? 'Cancel';

  if (!context.mounted) return;
  await showDialog<void>(
    context: context,
    builder: (dialogContext) => AlertDialog(
      title: Row(
        children: [
          Icon(
            Icons.info_outline,
            color: Theme.of(dialogContext).colorScheme.primary,
          ),
          const SizedBox(width: 8),
          Expanded(child: Text(dialogTitle)),
        ],
      ),
      content: SingleChildScrollView(
        child: Text(
          dialogMessage,
          style: Theme.of(dialogContext).textTheme.bodyMedium,
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(dialogContext).pop(),
          child: Text(cancelText),
        ),
        ElevatedButton.icon(
          onPressed: () {
            Navigator.of(dialogContext).pop();
            onSelectFolder();
          },
          icon: const Icon(Icons.folder_open),
          label: const Text('Select Folder'),
        ),
      ],
    ),
  );
}

/// Shows dialog with SAF folder picker hint for Android 11+.
Future<bool> showSafFolderPickerHint(
  BuildContext context,
) async {
  final localizations = AppLocalizations.of(context);
  final dialogTitle = localizations?.safFolderPickerHintTitle ??
      'Important: Check the checkbox';
  final dialogMessage = localizations?.safFolderPickerHintMessage ??
      'When selecting a folder, please make sure to check the \'Allow access to this folder\' checkbox in the file picker dialog. Without this checkbox, the app cannot access the selected folder.';

  final shouldProceed = await showDialog<bool>(
    context: context,
    builder: (dialogContext) => AlertDialog(
      title: Row(
        children: [
          Icon(
            Icons.info_outline,
            color: Theme.of(dialogContext).colorScheme.primary,
          ),
          const SizedBox(width: 8),
          Expanded(child: Text(dialogTitle)),
        ],
      ),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              dialogMessage,
              style: Theme.of(dialogContext).textTheme.bodyMedium,
            ),
            const SizedBox(height: 16),
            Text(
              localizations?.safAndroidDataObbWarning ??
                  'Note: Access to Android/data and Android/obb folders is blocked on Android 11+ devices with security updates from March 2024. Please select a different folder.',
              style: Theme.of(dialogContext).textTheme.bodySmall?.copyWith(
                    fontStyle: FontStyle.italic,
                    color: Theme.of(dialogContext).colorScheme.error,
                  ),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(dialogContext).pop(false),
          child: Text(localizations?.cancel ?? 'Cancel'),
        ),
        ElevatedButton.icon(
          onPressed: () => Navigator.of(dialogContext).pop(true),
          icon: const Icon(Icons.folder_open),
          label: const Text('Continue'),
        ),
      ],
    ),
  );

  return shouldProceed ?? false;
}

/// Shows dialog for confirming library folder migration.
Future<bool> showMigrateLibraryFolderDialog(
  BuildContext context,
) async {
  final localizations = AppLocalizations.of(context);
  final result = await showDialog<bool>(
    context: context,
    builder: (dialogContext) => AlertDialog(
      title: Text(
        localizations?.migrateLibraryFolderTitle ?? 'Migrate Files?',
      ),
      content: Text(
        localizations?.migrateLibraryFolderMessage ??
            'Do you want to move your existing audiobooks from the old folder to the new folder?',
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(dialogContext).pop(false),
          child: Text(localizations?.cancel ?? 'Cancel'),
        ),
        TextButton(
          onPressed: () => Navigator.of(dialogContext).pop(false),
          child: Text(localizations?.no ?? 'No'),
        ),
        ElevatedButton(
          onPressed: () => Navigator.of(dialogContext).pop(true),
          child: Text(localizations?.yes ?? 'Yes'),
        ),
      ],
    ),
  );
  return result ?? false;
}

/// Shows dialog for selecting folder type (optional override).
///
/// Returns the selected folder type, or null if cancelled.
/// If [detectedType] is provided, it will be pre-selected.
Future<ExternalFolderType?> showFolderTypeSelectionDialog(
  BuildContext context, {
  ExternalFolderType? detectedType,
}) async {
  if (!context.mounted) return null;

  final localizations = AppLocalizations.of(context);

  final result = await showDialog<ExternalFolderType>(
    context: context,
    builder: (dialogContext) => StatefulBuilder(
      builder: (context, setState) {
        var selectedType = detectedType;

        return AlertDialog(
          title: Row(
            children: [
              Icon(
                Icons.folder_special,
                color: Theme.of(dialogContext).colorScheme.primary,
              ),
              const SizedBox(width: 8),
              const Expanded(
                child: Text(
                  'Select Folder Structure Type',
                ),
              ),
            ],
          ),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Choose how files in this folder should be grouped:',
                  style: Theme.of(dialogContext).textTheme.bodyMedium,
                ),
                const SizedBox(height: 16),
                ListTile(
                  leading: Icon(
                    selectedType == ExternalFolderType.singleFolder
                        ? Icons.radio_button_checked
                        : Icons.radio_button_unchecked,
                    color: selectedType == ExternalFolderType.singleFolder
                        ? Theme.of(dialogContext).colorScheme.primary
                        : null,
                  ),
                  title: const Text('Single Folder'),
                  subtitle: const Text(
                    'All files in one folder = one book',
                    style: TextStyle(fontSize: 12),
                  ),
                  onTap: () {
                    setState(() {
                      selectedType = ExternalFolderType.singleFolder;
                    });
                  },
                ),
                ListTile(
                  leading: Icon(
                    selectedType == ExternalFolderType.rootWithSubfolders
                        ? Icons.radio_button_checked
                        : Icons.radio_button_unchecked,
                    color: selectedType == ExternalFolderType.rootWithSubfolders
                        ? Theme.of(dialogContext).colorScheme.primary
                        : null,
                  ),
                  title: const Text('Root with Subfolders'),
                  subtitle: const Text(
                    'Each subfolder = one book',
                    style: TextStyle(fontSize: 12),
                  ),
                  onTap: () {
                    setState(() {
                      selectedType = ExternalFolderType.rootWithSubfolders;
                    });
                  },
                ),
                ListTile(
                  leading: Icon(
                    selectedType == ExternalFolderType.authorBookStructure
                        ? Icons.radio_button_checked
                        : Icons.radio_button_unchecked,
                    color:
                        selectedType == ExternalFolderType.authorBookStructure
                            ? Theme.of(dialogContext).colorScheme.primary
                            : null,
                  ),
                  title: const Text('Author/Book Structure'),
                  subtitle: const Text(
                    'Structure: Author/Book/Files',
                    style: TextStyle(fontSize: 12),
                  ),
                  onTap: () {
                    setState(() {
                      selectedType = ExternalFolderType.authorBookStructure;
                    });
                  },
                ),
                ListTile(
                  leading: Icon(
                    selectedType == ExternalFolderType.seriesBookStructure
                        ? Icons.radio_button_checked
                        : Icons.radio_button_unchecked,
                    color:
                        selectedType == ExternalFolderType.seriesBookStructure
                            ? Theme.of(dialogContext).colorScheme.primary
                            : null,
                  ),
                  title: const Text('Series/Book Structure'),
                  subtitle: const Text(
                    'Structure: Series/Book/Files',
                    style: TextStyle(fontSize: 12),
                  ),
                  onTap: () {
                    setState(() {
                      selectedType = ExternalFolderType.seriesBookStructure;
                    });
                  },
                ),
                ListTile(
                  leading: Icon(
                    selectedType == ExternalFolderType.arbitrary
                        ? Icons.radio_button_checked
                        : Icons.radio_button_unchecked,
                    color: selectedType == ExternalFolderType.arbitrary
                        ? Theme.of(dialogContext).colorScheme.primary
                        : null,
                  ),
                  title: const Text('Arbitrary Structure'),
                  subtitle: const Text(
                    'Fallback for complex structures',
                    style: TextStyle(fontSize: 12),
                  ),
                  onTap: () {
                    setState(() {
                      selectedType = ExternalFolderType.arbitrary;
                    });
                  },
                ),
                if (detectedType != null) ...[
                  const SizedBox(height: 8),
                  Text(
                    'Detected type: ${_getFolderTypeName(detectedType)}',
                    style: Theme.of(dialogContext)
                        .textTheme
                        .bodySmall
                        ?.copyWith(
                          fontStyle: FontStyle.italic,
                          color: Theme.of(dialogContext).colorScheme.primary,
                        ),
                  ),
                ],
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(dialogContext).pop(),
              child: Text(localizations?.cancel ?? 'Cancel'),
            ),
            if (selectedType != null)
              ElevatedButton(
                onPressed: () => Navigator.of(dialogContext).pop(selectedType),
                child: const Text('Apply'),
              ),
          ],
        );
      },
    ),
  );

  return result;
}

/// Gets a human-readable name for folder type.
String _getFolderTypeName(ExternalFolderType type) {
  switch (type) {
    case ExternalFolderType.singleFolder:
      return 'Single Folder';
    case ExternalFolderType.rootWithSubfolders:
      return 'Root with Subfolders';
    case ExternalFolderType.authorBookStructure:
      return 'Author/Book Structure';
    case ExternalFolderType.seriesBookStructure:
      return 'Series/Book Structure';
    case ExternalFolderType.arbitrary:
      return 'Arbitrary Structure';
  }
}
