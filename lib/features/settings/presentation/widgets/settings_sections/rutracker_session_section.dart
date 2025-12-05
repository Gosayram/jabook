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
import 'package:jabook/core/di/providers/auth_infrastructure_providers.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// RuTracker session section widget.
class RutrackerSessionSection extends ConsumerStatefulWidget {
  /// Creates a new RutrackerSessionSection instance.
  const RutrackerSessionSection({super.key});

  @override
  ConsumerState<RutrackerSessionSection> createState() =>
      _RutrackerSessionSectionState();
}

class _RutrackerSessionSectionState
    extends ConsumerState<RutrackerSessionSection> {
  @override
  Widget build(BuildContext context) => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            AppLocalizations.of(context)?.rutrackerTitle ?? 'RuTracker',
            style: Theme.of(context).textTheme.titleLarge,
          ),
          const SizedBox(height: 8),
          Text(
            AppLocalizations.of(context)?.rutrackerSessionDescription ??
                'RuTracker session management (cookie)',
            style: Theme.of(context).textTheme.bodySmall,
          ),
          const SizedBox(height: 16),
          ListTile(
            leading: const Icon(Icons.login),
            title: Text(AppLocalizations.of(context)?.loginButton ??
                'Login to RuTracker'),
            subtitle: Text(
                AppLocalizations.of(context)?.loginRequiredForSearch ??
                    'Enter your credentials to authenticate'),
            onTap: () async {
              final messenger = ScaffoldMessenger.of(context);
              final localizations = AppLocalizations.of(context);
              // Navigate to auth screen
              // ignore: use_build_context_synchronously
              // Context is used immediately, not after async gap
              final result = await context.push('/auth');
              // If login was successful, validate cookies
              if (result == true && mounted) {
                final isValid = await DioClient.validateCookies();
                if (isValid) {
                  messenger.showSnackBar(
                    SnackBar(
                      content: Text(localizations?.authorizationSuccessful ??
                          'Authorization successful'),
                      backgroundColor: Colors.green,
                    ),
                  );
                } else {
                  messenger.showSnackBar(
                    SnackBar(
                      content: Text(localizations?.authorizationFailedMessage ??
                          'Authorization failed'),
                      backgroundColor: Colors.orange,
                    ),
                  );
                }
              }
            },
          ),
          ListTile(
            leading: const Icon(Icons.logout),
            title: Text(AppLocalizations.of(context)?.clearSessionButton ??
                'Clear RuTracker session (cookie)'),
            subtitle: Text(AppLocalizations.of(context)?.clearSessionSubtitle ??
                'Delete saved cookies and logout from account'),
            onTap: () async {
              // Clear session using SessionManager
              final messenger = ScaffoldMessenger.of(context);
              final localizations = AppLocalizations.of(context);
              try {
                final sessionManager = ref.read(sessionManagerProvider);
                await sessionManager.clearSession();
                if (mounted) {
                  messenger.showSnackBar(
                    SnackBar(
                        content: Text(localizations?.sessionClearedMessage ??
                            'RuTracker session cleared')),
                  );
                }
              } on Exception catch (e) {
                if (mounted) {
                  messenger.showSnackBar(
                    SnackBar(
                      content: Text('Error clearing session: ${e.toString()}'),
                    ),
                  );
                }
              }
            },
          ),
        ],
      );
}
