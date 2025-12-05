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
import 'package:jabook/features/settings/presentation/screens/mirror_settings_screen.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Mirror settings section widget.
class MirrorSection extends StatelessWidget {
  /// Creates a new MirrorSection instance.
  const MirrorSection({super.key});

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          localizations?.mirrorsScreenTitle ?? 'Mirrors & Sources',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 8),
        Text(
          'Configure RuTracker mirrors for optimal search performance',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 16),
        Semantics(
          button: true,
          label: 'Manage RuTracker mirrors',
          child: ListTile(
            leading: const Icon(Icons.dns),
            title: Text(localizations?.mirrorsScreenTitle ?? 'Manage Mirrors'),
            subtitle: Text(localizations?.configureMirrorsSubtitle ??
                'Configure and test RuTracker mirrors'),
            trailing: const Icon(Icons.arrow_forward_ios),
            onTap: () {
              Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (context) => const MirrorSettingsScreen(),
                ),
              );
            },
          ),
        ),
      ],
    );
  }
}
