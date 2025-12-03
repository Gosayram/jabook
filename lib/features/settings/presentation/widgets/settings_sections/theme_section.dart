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
import 'package:jabook/core/infrastructure/config/theme_provider.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Theme settings section widget.
class ThemeSection extends ConsumerWidget {
  /// Creates a new ThemeSection instance.
  const ThemeSection({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final localizations = AppLocalizations.of(context);
    final themeSettings = ref.watch(themeProvider);
    final themeNotifier = ref.read(themeProvider.notifier);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          localizations?.themeTitle ?? 'Theme',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 8),
        Text(
          localizations?.themeDescription ??
              'Customize the appearance of the app',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 16),
        // Follow system theme toggle
        ListTile(
          leading: const Icon(Icons.brightness_auto),
          title: Text(
            localizations?.followSystemTheme ?? 'Follow System Theme',
          ),
          trailing: Semantics(
            label: 'Follow system theme toggle',
            child: Switch(
              value: themeSettings.followSystem,
              onChanged: (value) async {
                await themeNotifier.setFollowSystem(value);
              },
            ),
          ),
        ),
        // Theme mode selection (only enabled when not following system)
        if (!themeSettings.followSystem) ...[
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16.0),
            child: SegmentedButton<String>(
              segments: const [
                ButtonSegment<String>(
                  value: 'light',
                  label: Text('Light'),
                  icon: Icon(Icons.light_mode),
                ),
                ButtonSegment<String>(
                  value: 'dark',
                  label: Text('Dark'),
                  icon: Icon(Icons.dark_mode),
                ),
              ],
              selected: {themeSettings.mode},
              onSelectionChanged: (newSelection) {
                if (newSelection.isNotEmpty) {
                  themeNotifier.setThemeMode(newSelection.first);
                }
              },
            ),
          ),
          const SizedBox(height: 8),
        ],
        // High contrast toggle
        ListTile(
          leading: const Icon(Icons.contrast),
          title: Text(localizations?.highContrast ?? 'High Contrast'),
          trailing: Semantics(
            label: 'High contrast mode toggle',
            child: Switch(
              value: themeSettings.highContrastEnabled,
              onChanged: (value) async {
                await themeNotifier.setHighContrastEnabled(value);
              },
            ),
          ),
        ),
      ],
    );
  }
}
