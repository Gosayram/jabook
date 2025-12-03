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
import 'package:jabook/core/infrastructure/config/language_manager.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Language settings section widget.
class LanguageSection extends ConsumerWidget {
  /// Creates a new LanguageSection instance.
  const LanguageSection({
    super.key,
    required this.selectedLanguage,
    required this.onLanguageChanged,
  });

  /// Currently selected language code.
  final String selectedLanguage;

  /// Callback when language is changed.
  final Future<void> Function(String languageCode) onLanguageChanged;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final localizations = AppLocalizations.of(context);
    final languageManager = LanguageManager();
    final languages = languageManager.getAvailableLanguages();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          localizations?.language ?? 'Language',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 8),
        Text(
          localizations?.languageDescription ??
              'Choose your preferred language for the app interface',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 16),
        ...languages.map((language) => _buildLanguageTile(
              context,
              language,
              selectedLanguage,
              onLanguageChanged,
            )),
      ],
    );
  }

  Widget _buildLanguageTile(
    BuildContext context,
    Map<String, String> language,
    String selectedLanguage,
    Future<void> Function(String) onLanguageChanged,
  ) {
    final localizations = AppLocalizations.of(context);
    final languageName = language['code'] == 'system'
        ? (localizations?.systemDefault ?? 'System Default')
        : language['name']!;

    return ListTile(
      leading: Text(
        language['flag']!,
        style: const TextStyle(fontSize: 24),
      ),
      title: Text(languageName),
      trailing: selectedLanguage == language['code']
          ? const Icon(Icons.check, color: Colors.blue)
          : null,
      onTap: () => onLanguageChanged(language['code']!),
    );
  }
}
