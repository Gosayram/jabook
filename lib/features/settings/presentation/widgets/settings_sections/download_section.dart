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
import 'package:jabook/l10n/app_localizations.dart';

/// Download settings section widget.
class DownloadSection extends StatelessWidget {
  /// Creates a new DownloadSection instance.
  const DownloadSection({
    super.key,
    required this.downloadFolderPath,
    required this.wifiOnlyDownloads,
    required this.onSelectDownloadFolder,
    required this.onShowFolderSelectionInstructions,
    required this.onWifiOnlyChanged,
  });

  /// Current download folder path.
  final String? downloadFolderPath;

  /// Whether Wi-Fi only downloads is enabled.
  final bool wifiOnlyDownloads;

  /// Callback when download folder selection is requested.
  final VoidCallback onSelectDownloadFolder;

  /// Callback when folder selection instructions should be shown.
  final VoidCallback onShowFolderSelectionInstructions;

  /// Callback when Wi-Fi only setting is changed.
  final void Function(bool value) onWifiOnlyChanged;

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          localizations?.downloadsTitle ?? 'Downloads',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 8),
        Text(
          localizations?.downloadsDescription ??
              'Manage download preferences and storage',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 16),
        Semantics(
          label: 'Download location',
          child: ListTile(
            leading: const Icon(Icons.folder),
            title: Text(localizations?.downloadLocationTitle ??
                localizations?.downloadLocation ??
                'Download Location'),
            subtitle: downloadFolderPath != null
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
                              downloadFolderPath!,
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
                    localizations?.defaultDownloadFolder ?? 'Default folder',
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          color: Theme.of(context)
                              .colorScheme
                              .onSurface
                              .withValues(alpha: 0.6),
                        ),
                  ),
            trailing: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (Platform.isAndroid)
                  Semantics(
                    button: true,
                    label: 'Show folder selection instructions',
                    child: IconButton(
                      icon: const Icon(Icons.help_outline),
                      onPressed: onShowFolderSelectionInstructions,
                      tooltip: 'Show instructions',
                    ),
                  ),
                Semantics(
                  button: true,
                  label: 'Change download folder',
                  child: IconButton(
                    icon: const Icon(Icons.edit),
                    onPressed: onSelectDownloadFolder,
                    tooltip: 'Change folder',
                  ),
                ),
              ],
            ),
          ),
        ),
        ListTile(
          leading: const Icon(Icons.wifi),
          title: Text(localizations?.wifiOnlyDownloadsTitle ??
              localizations?.wifiOnlyDownloads ??
              'Wi-Fi Only Downloads'),
          subtitle: Text(
            wifiOnlyDownloads
                ? 'Downloads will only start when connected to Wi-Fi'
                : 'Downloads can start on any network connection',
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: Theme.of(context)
                      .colorScheme
                      .onSurface
                      .withValues(alpha: 0.6),
                ),
          ),
          trailing: Semantics(
            label: 'Wi-Fi only downloads toggle',
            child: Switch(
              value: wifiOnlyDownloads,
              onChanged: onWifiOnlyChanged,
            ),
          ),
        ),
      ],
    );
  }
}
