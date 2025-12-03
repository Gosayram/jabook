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
import 'package:jabook/core/infrastructure/config/audio_settings_manager.dart';
import 'package:jabook/core/infrastructure/config/audio_settings_provider.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Audio settings section widget.
class AudioSection extends ConsumerWidget {
  /// Creates a new AudioSection instance.
  const AudioSection({
    super.key,
    required this.onShowPlaybackSpeedDialog,
    required this.onShowSkipDurationDialog,
    required this.onShowInactivityTimeoutDialog,
    required this.onShowResetAllBookSettingsDialog,
  });

  /// Callback when playback speed dialog should be shown.
  final void Function(BuildContext context) onShowPlaybackSpeedDialog;

  /// Callback when skip duration dialog should be shown.
  final void Function(
    BuildContext context,
    int currentDuration,
    String title,
  ) onShowSkipDurationDialog;

  /// Callback when inactivity timeout dialog should be shown.
  final void Function(BuildContext context) onShowInactivityTimeoutDialog;

  /// Callback when reset all book settings dialog should be shown.
  final void Function(BuildContext context) onShowResetAllBookSettingsDialog;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final localizations = AppLocalizations.of(context);
    final audioSettings = ref.watch(audioSettingsProvider);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          localizations?.audioTitle ?? 'Audio',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 8),
        Text(
          localizations?.audioDescription ??
              'Configure audio playback settings',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 16),
        // Playback speed selection
        Semantics(
          button: true,
          label: 'Set playback speed',
          child: ListTile(
            leading: const Icon(Icons.speed),
            title: Text(localizations?.playbackSpeedTitle ??
                localizations?.playbackSpeed ??
                'Playback Speed'),
            subtitle: Text(AudioSettingsManager.formatPlaybackSpeed(
                audioSettings.defaultPlaybackSpeed)),
            onTap: () => onShowPlaybackSpeedDialog(context),
          ),
        ),
        // Rewind duration selection
        Semantics(
          button: true,
          label: 'Set rewind duration',
          child: ListTile(
            leading: const Icon(Icons.replay_10),
            title:
                Text(localizations?.rewindDurationTitle ?? 'Rewind Duration'),
            subtitle: Text(
              '${audioSettings.defaultRewindDuration} ${localizations?.secondsLabel ?? 'seconds'}',
            ),
            onTap: () => onShowSkipDurationDialog(
              context,
              audioSettings.defaultRewindDuration,
              localizations?.rewindDurationTitle ?? 'Rewind Duration',
            ),
          ),
        ),
        // Forward duration selection
        Semantics(
          button: true,
          label: 'Set forward duration',
          child: ListTile(
            leading: const Icon(Icons.forward_30),
            title:
                Text(localizations?.forwardDurationTitle ?? 'Forward Duration'),
            subtitle: Text(
              '${audioSettings.defaultForwardDuration} ${localizations?.secondsLabel ?? 'seconds'}',
            ),
            onTap: () => onShowSkipDurationDialog(
              context,
              audioSettings.defaultForwardDuration,
              localizations?.forwardDurationTitle ?? 'Forward Duration',
            ),
          ),
        ),
        // Inactivity timeout selection
        Semantics(
          button: true,
          label:
              localizations?.inactivityTimeoutLabel ?? 'Set inactivity timeout',
          child: ListTile(
            leading: const Icon(Icons.timer_outlined),
            title: Text(
                localizations?.inactivityTimeoutTitle ?? 'Inactivity Timeout'),
            subtitle: Text(
              '${audioSettings.inactivityTimeoutMinutes} ${audioSettings.inactivityTimeoutMinutes == 1 ? (localizations?.minute ?? 'minute') : (localizations?.minutes ?? 'minutes')}',
            ),
            onTap: () => onShowInactivityTimeoutDialog(context),
          ),
        ),
        const Divider(),
        // Reset all book settings button
        ListTile(
          leading: const Icon(Icons.restore, color: Colors.red),
          title: Text(
            localizations?.resetAllBookSettings ?? 'Reset all book settings',
            style: const TextStyle(color: Colors.red),
          ),
          subtitle: Text(
            localizations?.resetAllBookSettingsDescription ??
                'Remove individual settings for all books',
          ),
          onTap: () => onShowResetAllBookSettingsDialog(context),
        ),
      ],
    );
  }
}
