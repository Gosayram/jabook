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
import 'package:go_router/go_router.dart';
import 'package:jabook/core/di/providers/auth_providers.dart';
import 'package:jabook/core/di/providers/config_providers.dart';
import 'package:jabook/core/utils/safe_async.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Screen displayed when a user tries to access a restricted feature.
///
/// This screen is shown when a user in guest mode tries to access
/// a feature that requires authentication.
class RestrictedFeatureScreen extends ConsumerWidget {
  /// Creates a new RestrictedFeatureScreen instance.
  const RestrictedFeatureScreen({
    required this.feature,
    super.key,
  });

  /// Name of the restricted feature.
  final String feature;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final localizations = AppLocalizations.of(context);
    final appConfig = ref.watch(appConfigProvider);
    final isBeta = appConfig.isBeta;
    final primaryColor =
        isBeta ? const Color(0xFF263B52) : const Color(0xFF6B46C1);
    final accessNotifier = ref.read(accessProvider.notifier);

    return Scaffold(
      appBar: AppBar(
        title: Text(
          localizations?.restrictedFeature ?? 'Restricted Feature',
        ),
        backgroundColor: Colors.transparent,
        elevation: 0,
      ),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                Icons.lock,
                color: primaryColor,
                size: 80,
              ),
              const SizedBox(height: 24),
              Text(
                localizations?.featureRestricted ??
                    'This feature is restricted in demo mode',
                style: theme.textTheme.headlineMedium?.copyWith(
                  color: primaryColor,
                  fontWeight: FontWeight.bold,
                ),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 16),
              Text(
                localizations?.featureRestrictedDescription ??
                    'To access this feature, please sign in to your account',
                style: theme.textTheme.bodyLarge,
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 32),
              ElevatedButton.icon(
                onPressed: () async {
                  final granted = await accessNotifier.requestAccess(
                    context,
                    feature,
                  );
                  if (granted && context.mounted) {
                    // Navigate to auth screen if user chose to sign in
                    safeUnawaited(context.push('/auth'));
                  }
                },
                icon: const Icon(Icons.login),
                label: Text(
                  localizations?.signInToUnlock ?? 'Sign in to unlock',
                ),
                style: ElevatedButton.styleFrom(
                  backgroundColor: primaryColor,
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(
                    horizontal: 32,
                    vertical: 16,
                  ),
                ),
              ),
              const SizedBox(height: 16),
              TextButton(
                onPressed: () => context.pop(),
                child: Text(
                  localizations?.continueAsGuest ?? 'Continue as Guest',
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
