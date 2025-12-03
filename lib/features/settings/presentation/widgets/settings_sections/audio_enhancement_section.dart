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
import 'package:jabook/core/infrastructure/config/audio_settings_provider.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Audio enhancement section widget.
class AudioEnhancementSection extends ConsumerWidget {
  /// Creates a new AudioEnhancementSection instance.
  const AudioEnhancementSection({
    super.key,
    required this.onShowVolumeBoostDialog,
    required this.onShowDRCLevelDialog,
    required this.onApplyAudioProcessingSettings,
    required this.getVolumeBoostLabel,
    required this.getDRCLevelLabel,
  });

  /// Callback when volume boost dialog should be shown.
  final void Function(BuildContext context) onShowVolumeBoostDialog;

  /// Callback when DRC level dialog should be shown.
  final void Function(BuildContext context) onShowDRCLevelDialog;

  /// Callback to apply audio processing settings.
  final Future<void> Function(
    BuildContext context,
    AudioSettings settings,
  ) onApplyAudioProcessingSettings;

  /// Function to get volume boost label.
  final String Function(String level) getVolumeBoostLabel;

  /// Function to get DRC level label.
  final String Function(String level) getDRCLevelLabel;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final localizations = AppLocalizations.of(context);
    final audioSettings = ref.watch(audioSettingsProvider);
    final audioNotifier = ref.read(audioSettingsProvider.notifier);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          '🔊 ${localizations?.audioEnhancementTitle ?? 'Audio Enhancement'}',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 8),
        Text(
          localizations?.audioEnhancementDescription ??
              'Improve audio quality and volume consistency',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 16),
        // Normalize Volume switch
        SwitchListTile(
          secondary: const Icon(Icons.equalizer),
          title: Text(
            localizations?.normalizeVolumeTitle ?? 'Normalize Volume',
          ),
          subtitle: Text(
            localizations?.normalizeVolumeDescription ??
                'Maintain consistent volume across different audiobooks',
          ),
          value: audioSettings.normalizeVolume,
          onChanged: (value) async {
            await audioNotifier.setNormalizeVolume(value);
            // Apply settings to active player
            if (context.mounted) {
              // ignore: use_build_context_synchronously
              // Context is safe here because we check context.mounted before use
              await onApplyAudioProcessingSettings(
                context,
                audioSettings.copyWith(normalizeVolume: value),
              );
            }
          },
        ),
        // Volume Boost selection
        Semantics(
          button: true,
          label: 'Set volume boost level',
          child: ListTile(
            leading: const Icon(Icons.volume_up),
            title: Text(
              localizations?.volumeBoostTitle ?? 'Volume Boost',
            ),
            subtitle: Text(getVolumeBoostLabel(audioSettings.volumeBoostLevel)),
            onTap: () => onShowVolumeBoostDialog(context),
          ),
        ),
        // DRC Level selection
        Semantics(
          button: true,
          label: 'Set DRC level',
          child: ListTile(
            leading: const Icon(Icons.compress),
            title: Text(
              localizations?.drcLevelTitle ?? 'Dynamic Range Compression',
            ),
            subtitle: Text(getDRCLevelLabel(audioSettings.drcLevel)),
            onTap: () => onShowDRCLevelDialog(context),
          ),
        ),
        // Speech Enhancer switch
        SwitchListTile(
          secondary: const Icon(Icons.record_voice_over),
          title: Text(
            localizations?.speechEnhancerTitle ?? 'Speech Enhancer',
          ),
          subtitle: Text(
            localizations?.speechEnhancerDescription ??
                'Improve speech clarity and reduce sibilance',
          ),
          value: audioSettings.speechEnhancer,
          onChanged: (value) async {
            await audioNotifier.setSpeechEnhancer(value);
            // Apply settings to active player
            if (context.mounted) {
              // ignore: use_build_context_synchronously
              // Context is safe here because we check context.mounted before use
              await onApplyAudioProcessingSettings(
                context,
                audioSettings.copyWith(speechEnhancer: value),
              );
            }
          },
        ),
        // Auto Volume Leveling switch
        SwitchListTile(
          secondary: const Icon(Icons.trending_flat),
          title: Text(
            localizations?.autoVolumeLevelingTitle ?? 'Auto Volume Leveling',
          ),
          subtitle: Text(
            localizations?.autoVolumeLevelingDescription ??
                'Automatically adjust volume to maintain consistent level',
          ),
          value: audioSettings.autoVolumeLeveling,
          onChanged: (value) async {
            await audioNotifier.setAutoVolumeLeveling(value);
            // Apply settings to active player
            if (context.mounted) {
              // ignore: use_build_context_synchronously
              // Context is safe here because we check context.mounted before use
              await onApplyAudioProcessingSettings(
                context,
                audioSettings.copyWith(autoVolumeLeveling: value),
              );
            }
          },
        ),
      ],
    );
  }
}
