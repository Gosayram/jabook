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
import 'package:jabook/core/domain/library/entities/local_audiobook_group.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Result of delete confirmation dialog.
enum DeleteAction {
  /// Delete files from disk (physical deletion).
  deleteFiles,

  /// Remove from library only (logical deletion).
  removeFromLibrary,

  /// Cancel deletion.
  cancel,
}

/// Dialog for confirming deletion of audiobook files.
///
/// This dialog shows information about what will be deleted and allows
/// the user to choose between physical deletion and logical removal.
class DeleteConfirmationDialog extends StatelessWidget {
  /// Creates a new DeleteConfirmationDialog instance.
  ///
  /// The [group] parameter is the audiobook group to delete.
  /// The [totalSize] parameter is the total size of files in bytes (optional).
  const DeleteConfirmationDialog({
    super.key,
    required this.group,
    this.totalSize,
  });

  /// The audiobook group to delete.
  final LocalAudiobookGroup group;

  /// Total size of files in bytes (optional).
  final int? totalSize;

  /// Shows the delete confirmation dialog.
  ///
  /// Returns [DeleteAction] indicating user's choice.
  static Future<DeleteAction?> show(
    BuildContext context,
    LocalAudiobookGroup group, {
    int? totalSize,
  }) async =>
      showDialog<DeleteAction>(
        context: context,
        builder: (context) => DeleteConfirmationDialog(
          group: group,
          totalSize: totalSize,
        ),
      );

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context);
    final fileCount = group.files.length;
    final sizeText =
        totalSize != null ? _formatSize(totalSize!) : group.formattedTotalSize;

    return AlertDialog(
      title: Row(
        children: [
          Icon(
            Icons.warning,
            color: Theme.of(context).colorScheme.error,
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              localizations?.deleteConfirmationTitle ?? 'Delete Audiobook?',
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
              localizations?.deleteConfirmationMessage ??
                  'Are you sure you want to delete this audiobook?',
              style: Theme.of(context).textTheme.bodyLarge,
            ),
            const SizedBox(height: 16),
            // Group information
            _buildInfoRow(
              context,
              Icons.folder,
              localizations?.audiobookGroupName ?? 'Group',
              group.groupName,
            ),
            const SizedBox(height: 8),
            _buildInfoRow(
              context,
              Icons.audiotrack,
              localizations?.fileCount ?? 'Files',
              '$fileCount ${fileCount == 1 ? 'file' : 'files'}',
            ),
            const SizedBox(height: 8),
            _buildInfoRow(
              context,
              Icons.storage,
              localizations?.totalSize ?? 'Total Size',
              sizeText,
            ),
            const SizedBox(height: 16),
            // Warning
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Theme.of(context).colorScheme.errorContainer,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Row(
                children: [
                  Icon(
                    Icons.info_outline,
                    color: Theme.of(context).colorScheme.onErrorContainer,
                    size: 20,
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      localizations?.deleteWarningMessage ??
                          'Files will be permanently deleted and cannot be recovered.',
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                            color:
                                Theme.of(context).colorScheme.onErrorContainer,
                          ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(DeleteAction.cancel),
          child: Text(localizations?.cancel ?? 'Cancel'),
        ),
        TextButton(
          onPressed: () =>
              Navigator.of(context).pop(DeleteAction.removeFromLibrary),
          child: Text(
            localizations?.removeFromLibraryButton ?? 'Remove from Library',
          ),
        ),
        ElevatedButton(
          style: ElevatedButton.styleFrom(
            backgroundColor: Theme.of(context).colorScheme.error,
            foregroundColor: Theme.of(context).colorScheme.onError,
          ),
          onPressed: () => Navigator.of(context).pop(DeleteAction.deleteFiles),
          child: Text(
            localizations?.deleteFilesButton ?? 'Delete Files',
          ),
        ),
      ],
    );
  }

  Widget _buildInfoRow(
    BuildContext context,
    IconData icon,
    String label,
    String value,
  ) =>
      Row(
        children: [
          Icon(icon, size: 20, color: Theme.of(context).colorScheme.onSurface),
          const SizedBox(width: 8),
          Text(
            '$label: ',
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  fontWeight: FontWeight.bold,
                ),
          ),
          Expanded(
            child: Text(
              value,
              style: Theme.of(context).textTheme.bodyMedium,
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      );

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
}
