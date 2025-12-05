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
import 'package:jabook/features/settings/presentation/screens/background_compatibility_screen.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Background compatibility section widget.
class BackgroundCompatibilitySection extends StatelessWidget {
  /// Creates a new BackgroundCompatibilitySection instance.
  const BackgroundCompatibilitySection({super.key});

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          localizations?.backgroundWorkTitle ?? 'Background Work',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 8),
        Text(
          localizations?.backgroundCompatibilityBannerMessage ??
              'To ensure stable background operation, you may need to configure device settings.',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 16),
        Semantics(
          button: true,
          label: 'Open background compatibility settings',
          child: ListTile(
            leading: const Icon(Icons.phone_android),
            title: Text(localizations?.compatibilityDiagnosticsTitle ??
                'Compatibility & Diagnostics'),
            subtitle: Text(
              localizations?.compatibilityDiagnosticsSubtitle ??
                  'Compatibility check and manufacturer settings configuration',
            ),
            trailing: const Icon(Icons.arrow_forward_ios),
            onTap: () {
              Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (context) => const BackgroundCompatibilityScreen(),
                ),
              );
            },
          ),
        ),
      ],
    );
  }
}
