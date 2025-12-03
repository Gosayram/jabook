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

/// Backup & Restore section widget.
class BackupSection extends StatelessWidget {
  /// Creates a new BackupSection instance.
  const BackupSection({
    super.key,
    required this.onExportData,
    required this.onImportData,
  });

  /// Callback when export data is requested.
  final VoidCallback onExportData;

  /// Callback when import data is requested.
  final VoidCallback onImportData;

  @override
  Widget build(BuildContext context) => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            AppLocalizations.of(context)?.backupRestoreTitle ??
                'Backup & Restore',
            style: Theme.of(context).textTheme.titleLarge,
          ),
          const SizedBox(height: 8),
          Text(
            AppLocalizations.of(context)?.backupRestoreDescription ??
                'Export and import your data (favorites, history, metadata)',
            style: Theme.of(context).textTheme.bodySmall,
          ),
          const SizedBox(height: 16),
          ListTile(
            leading: const Icon(Icons.file_download),
            title: Text(AppLocalizations.of(context)?.exportDataButton ??
                'Export Data'),
            subtitle: Text(AppLocalizations.of(context)?.exportDataSubtitle ??
                'Save all your data to a backup file'),
            trailing: const Icon(Icons.arrow_forward_ios),
            onTap: onExportData,
          ),
          ListTile(
            leading: const Icon(Icons.file_upload),
            title: Text(AppLocalizations.of(context)?.importDataButton ??
                'Import Data'),
            subtitle: Text(AppLocalizations.of(context)?.importDataSubtitle ??
                'Restore data from a backup file'),
            trailing: const Icon(Icons.arrow_forward_ios),
            onTap: onImportData,
          ),
        ],
      );
}
