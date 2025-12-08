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
import 'package:jabook/core/infrastructure/config/notification_settings_provider.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Notification settings section widget.
class NotificationSection extends ConsumerWidget {
  /// Creates a new NotificationSection instance.
  const NotificationSection({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final localizations = AppLocalizations.of(context);
    final notificationSettings = ref.watch(notificationSettingsProvider);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Notifications',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 8),
        Text(
          'Configure notification display settings',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 16),
        // Notification type selection
        Semantics(
          button: true,
          label: 'Select notification type',
          child: ListTile(
            leading: const Icon(Icons.notifications),
            title: const Text('Notification Type'),
            subtitle: Text(
              _getNotificationTypeLabel(
                context,
                notificationSettings.notificationType,
                localizations,
              ),
            ),
            onTap: () => _showNotificationTypeDialog(
              context,
              ref,
              notificationSettings.notificationType,
              localizations,
            ),
          ),
        ),
      ],
    );
  }

  String _getNotificationTypeLabel(
    BuildContext context,
    NotificationType type,
    AppLocalizations? localizations,
  ) {
    switch (type) {
      case NotificationType.full:
        return 'Full notification (all controls)';
      case NotificationType.minimal:
        return 'Minimal notification (Play/Pause only)';
    }
  }

  void _showNotificationTypeDialog(
    BuildContext context,
    WidgetRef ref,
    NotificationType currentType,
    AppLocalizations? localizations,
  ) {
    showDialog(
      context: context,
      builder: (dialogContext) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: const Text('Notification Type'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: NotificationType.values
                .map((type) => ListTile(
                      leading: Icon(
                        currentType == type
                            ? Icons.radio_button_checked
                            : Icons.radio_button_unchecked,
                        color: currentType == type
                            ? Theme.of(context).colorScheme.primary
                            : null,
                      ),
                      title: Text(_getNotificationTypeLabel(
                          context, type, localizations)),
                      subtitle: Text(
                        _getNotificationTypeDescription(type, localizations),
                      ),
                      onTap: () {
                        setDialogState(() {
                          ref
                              .read(notificationSettingsProvider.notifier)
                              .setNotificationType(type);
                        });
                        Navigator.of(context).pop();
                      },
                    ))
                .toList(),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('Cancel'),
            ),
          ],
        ),
      ),
    );
  }

  String _getNotificationTypeDescription(
    NotificationType type,
    AppLocalizations? localizations,
  ) {
    switch (type) {
      case NotificationType.full:
        return 'Shows all playback controls: Previous, Rewind, Play/Pause, Forward, Next, Stop';
      case NotificationType.minimal:
        return 'Shows only Play/Pause button. MediaSession integration is preserved for system controls.';
    }
  }
}
