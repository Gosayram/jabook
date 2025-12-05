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
import 'package:jabook/core/infrastructure/config/audio_settings_manager.dart';
import 'package:jabook/core/infrastructure/config/audio_settings_provider.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Shows dialog for selecting playback speed.
Future<void> showPlaybackSpeedDialog(
  BuildContext context,
  AudioSettings audioSettings,
  AudioSettingsNotifier audioNotifier,
  Future<void> Function() onUpdateMediaSession,
) async {
  final speeds = AudioSettingsManager.getAvailablePlaybackSpeeds();
  final selectedSpeed = audioSettings.defaultPlaybackSpeed;

  final result = await showDialog<double>(
    context: context,
    builder: (context) => AlertDialog(
      title: Text(
          AppLocalizations.of(context)?.playbackSpeedTitle ?? 'Playback Speed'),
      content: SizedBox(
        width: double.maxFinite,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: speeds
                .map((speed) => RadioListTile<double>(
                      title:
                          Text(AudioSettingsManager.formatPlaybackSpeed(speed)),
                      value: speed,
                      // ignore: deprecated_member_use
                      groupValue: selectedSpeed,
                      // ignore: deprecated_member_use
                      onChanged: (value) {
                        Navigator.of(context).pop(value);
                      },
                    ))
                .toList(),
          ),
        ),
      ),
    ),
  );

  if (result != null) {
    await audioNotifier.setDefaultPlaybackSpeed(result);
    // Update MediaSessionManager if player is active
    await onUpdateMediaSession();
  }
}

/// Shows dialog for selecting skip duration.
Future<void> showSkipDurationDialog(
  BuildContext context,
  int currentDuration,
  Future<void> Function(int) onDurationSelected,
  String title,
  Future<void> Function() onUpdateMediaSession,
) async {
  final durations = [5, 10, 15, 20, 30, 45, 60];

  final result = await showDialog<int>(
    context: context,
    builder: (context) => AlertDialog(
      title: Text(title),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: durations.map((duration) {
          final localizations = AppLocalizations.of(context);
          return RadioListTile<int>(
            title: Text(
              '$duration ${localizations?.secondsLabel ?? 'seconds'}',
            ),
            value: duration,
            // ignore: deprecated_member_use
            groupValue: currentDuration,
            // ignore: deprecated_member_use
            onChanged: (value) {
              Navigator.of(context).pop(value);
            },
          );
        }).toList(),
      ),
    ),
  );

  if (result != null) {
    await onDurationSelected(result);
    // Update MediaSessionManager if player is active
    await onUpdateMediaSession();
  }
}

/// Shows dialog for selecting inactivity timeout.
Future<void> showInactivityTimeoutDialog(
  BuildContext context,
  int currentTimeout,
  AudioSettingsNotifier audioNotifier,
  Future<void> Function() onUpdateInactivityTimeout,
) async {
  final localizations = AppLocalizations.of(context);
  // Generate list of timeout options: 10, 15, 20, 30, 45, 60, 90, 120, 150, 180 minutes
  final timeouts = [10, 15, 20, 30, 45, 60, 90, 120, 150, 180];

  final result = await showDialog<int>(
    context: context,
    builder: (context) => AlertDialog(
      title:
          Text(localizations?.inactivityTimeoutTitle ?? 'Inactivity Timeout'),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: timeouts
              .map((timeout) => RadioListTile<int>(
                    title: Text(
                      '$timeout ${timeout == 1 ? (localizations?.minute ?? 'minute') : (localizations?.minutes ?? 'minutes')}',
                    ),
                    value: timeout,
                    // ignore: deprecated_member_use
                    groupValue: currentTimeout,
                    // ignore: deprecated_member_use
                    onChanged: (value) {
                      Navigator.of(context).pop(value);
                    },
                  ))
              .toList(),
        ),
      ),
    ),
  );

  if (result != null) {
    await audioNotifier.setInactivityTimeoutMinutes(result);
    // Update inactivity timeout if player is active
    await onUpdateInactivityTimeout();
  }
}

/// Shows dialog for selecting volume boost level.
Future<void> showVolumeBoostDialog(
  BuildContext context,
  AudioSettings audioSettings,
  AudioSettingsNotifier audioNotifier,
  String Function(String) getVolumeBoostLabel,
  Future<void> Function(AudioSettings) onApplyAudioProcessingSettings,
) async {
  final levels = ['Off', 'Boost50', 'Boost100', 'Boost200', 'Auto'];
  final selectedLevel = audioSettings.volumeBoostLevel;

  final result = await showDialog<String>(
    context: context,
    builder: (context) => AlertDialog(
      title: Text(
        AppLocalizations.of(context)?.volumeBoostTitle ?? 'Volume Boost',
      ),
      content: SizedBox(
        width: double.maxFinite,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: levels
                .map((level) => RadioListTile<String>(
                      title: Text(getVolumeBoostLabel(level)),
                      value: level,
                      // ignore: deprecated_member_use
                      groupValue: selectedLevel,
                      // ignore: deprecated_member_use
                      onChanged: (value) {
                        Navigator.of(context).pop(value);
                      },
                    ))
                .toList(),
          ),
        ),
      ),
    ),
  );

  if (result != null) {
    await audioNotifier.setVolumeBoostLevel(result);
    // Apply settings to active player
    await onApplyAudioProcessingSettings(
      audioSettings.copyWith(volumeBoostLevel: result),
    );
  }
}

/// Shows dialog for selecting DRC level.
Future<void> showDRCLevelDialog(
  BuildContext context,
  AudioSettings audioSettings,
  AudioSettingsNotifier audioNotifier,
  String Function(String) getDRCLevelLabel,
  Future<void> Function(AudioSettings) onApplyAudioProcessingSettings,
) async {
  final levels = ['Off', 'Gentle', 'Medium', 'Strong'];
  final selectedLevel = audioSettings.drcLevel;

  final result = await showDialog<String>(
    context: context,
    builder: (context) => AlertDialog(
      title: Text(
        AppLocalizations.of(context)?.drcLevelTitle ??
            'Dynamic Range Compression',
      ),
      content: SizedBox(
        width: double.maxFinite,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: levels
                .map((level) => RadioListTile<String>(
                      title: Text(getDRCLevelLabel(level)),
                      value: level,
                      // ignore: deprecated_member_use
                      groupValue: selectedLevel,
                      // ignore: deprecated_member_use
                      onChanged: (value) {
                        Navigator.of(context).pop(value);
                      },
                    ))
                .toList(),
          ),
        ),
      ),
    ),
  );

  if (result != null) {
    await audioNotifier.setDRCLevel(result);
    // Apply settings to active player
    await onApplyAudioProcessingSettings(
      audioSettings.copyWith(drcLevel: result),
    );
  }
}

/// Shows confirmation dialog for resetting all book settings.
Future<bool> showResetAllBookSettingsDialog(BuildContext context) async {
  final localizations = AppLocalizations.of(context);
  final confirmed = await showDialog<bool>(
    context: context,
    builder: (context) => AlertDialog(
      title: Text(
        localizations?.resetAllBookSettings ?? 'Reset all book settings',
      ),
      content: Text(
        localizations?.resetAllBookSettingsConfirmation ??
            'This will remove individual audio settings for all books. '
                'All books will use global settings. This action cannot be undone.',
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(false),
          child: Text(localizations?.cancel ?? 'Cancel'),
        ),
        TextButton(
          onPressed: () => Navigator.of(context).pop(true),
          style: TextButton.styleFrom(foregroundColor: Colors.red),
          child: Text(localizations?.resetButton ?? 'Reset'),
        ),
      ],
    ),
  );
  return confirmed ?? false;
}
