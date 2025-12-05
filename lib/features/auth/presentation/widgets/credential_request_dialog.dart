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
import 'package:jabook/core/di/providers/config_providers.dart';
import 'package:jabook/core/domain/auth/entities/credential_dialog_result.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Dialog for requesting user credentials when accessing restricted features.
///
/// This dialog is shown when a user in guest mode tries to access
/// a feature that requires authentication.
class CredentialRequestDialog extends ConsumerWidget {
  /// Creates a new CredentialRequestDialog instance.
  const CredentialRequestDialog({
    required this.feature,
    super.key,
  });

  /// Name of the feature that requires authentication.
  final String feature;

  /// Shows the credential request dialog.
  static Future<CredentialDialogResult?> show(
    BuildContext context,
    String feature,
  ) async =>
      showDialog<CredentialDialogResult>(
        context: context,
        builder: (context) => CredentialRequestDialog(feature: feature),
      );

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final localizations = AppLocalizations.of(context);
    final appConfig = ref.watch(appConfigProvider);
    final isBeta = appConfig.isBeta;
    final primaryColor =
        isBeta ? const Color(0xFF263B52) : const Color(0xFF6B46C1);

    return AlertDialog(
      title: Text(localizations?.accessRequired ?? 'Access Required'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(
            Icons.lock_outline,
            color: primaryColor,
            size: 48,
          ),
          const SizedBox(height: 16),
          Text(
            localizations?.featureRequiresAuth(feature) ??
                'Feature "$feature" requires authentication',
            style: theme.textTheme.bodyLarge,
          ),
          const SizedBox(height: 8),
          Text(
            localizations?.signInToAccessFeature ??
                'Sign in to access this feature',
            style: theme.textTheme.bodyMedium?.copyWith(
              color: theme.colorScheme.onSurface.withValues(
                alpha: 0.7,
              ),
            ),
          ),
        ],
      ),
      actions: [
        TextButton(
          onPressed: () =>
              Navigator.of(context).pop(CredentialDialogResult.cancel),
          child: Text(localizations?.cancel ?? 'Cancel'),
        ),
        TextButton(
          onPressed: () =>
              Navigator.of(context).pop(CredentialDialogResult.signIn),
          child: Text(localizations?.login ?? 'Login'),
        ),
        ElevatedButton(
          onPressed: () =>
              Navigator.of(context).pop(CredentialDialogResult.upgrade),
          style: ElevatedButton.styleFrom(
            backgroundColor: primaryColor,
            foregroundColor: Colors.white,
          ),
          child: Text(localizations?.upgradeToFull ?? 'Upgrade to Full'),
        ),
      ],
    );
  }
}
