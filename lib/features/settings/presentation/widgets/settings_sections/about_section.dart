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
import 'package:go_router/go_router.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// About section widget.
class AboutSection extends StatelessWidget {
  /// Creates a new AboutSection instance.
  const AboutSection({super.key});

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          localizations?.aboutTitle ?? 'About',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 8),
        Text(
          localizations?.aboutSectionDescription ?? 'App information and links',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 16),
        Semantics(
          button: true,
          label: localizations?.aboutTitle ?? 'About app',
          child: ListTile(
            leading: const Icon(Icons.info_outline),
            title: Text(localizations?.aboutTitle ?? 'About'),
            subtitle: Text(
              localizations?.aboutSectionSubtitle ??
                  'Version, license, and developer information',
            ),
            trailing: const Icon(Icons.arrow_forward_ios),
            onTap: () {
              context.push('/settings/about');
            },
          ),
        ),
      ],
    );
  }
}
