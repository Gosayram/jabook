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

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Widget for displaying error states in search screen.
///
/// Shows different error messages and actions based on error type.
class SearchErrorState extends StatelessWidget {
  /// Creates a new SearchErrorState instance.
  const SearchErrorState({
    required this.errorKind,
    required this.errorMessage,
    required this.onRetry,
    required this.onLogin,
    super.key,
  });

  /// Type of error: 'auth', 'timeout', 'mirror', 'network', or null.
  final String? errorKind;

  /// Error message to display.
  final String? errorMessage;

  /// Callback when retry is requested.
  final VoidCallback onRetry;

  /// Callback when login is requested.
  final VoidCallback onLogin;

  @override
  Widget build(BuildContext context) {
    String title;
    var actions = <Widget>[];
    var message = errorMessage ?? '';
    IconData iconData;
    Color iconColor;

    final localizations = AppLocalizations.of(context);

    switch (errorKind) {
      case 'auth':
        title =
            localizations?.authenticationRequired ?? 'Authentication Required';
        iconData = Icons.lock_outline;
        iconColor = Colors.orange.shade600;
        actions = [
          ElevatedButton.icon(
            onPressed: onLogin,
            icon: const Icon(Icons.vpn_key),
            label: Text(localizations?.loginButton ?? 'Login to RuTracker'),
          ),
        ];
        message = localizations?.loginRequiredForSearch ??
            'Please login to RuTracker to access search functionality.';
        break;
      case 'timeout':
        title = localizations?.timeoutError ?? 'Request Timed Out';
        iconData = Icons.timer_off;
        iconColor = Colors.amber.shade600;
        actions = [
          ElevatedButton.icon(
            onPressed: onRetry,
            icon: const Icon(Icons.refresh),
            label: Text(localizations?.retry ?? 'Retry'),
          ),
          OutlinedButton.icon(
            onPressed: () => unawaited(context.push('/mirrors')),
            icon: const Icon(Icons.dns),
            label: Text(localizations?.changeMirror ?? 'Change Mirror'),
          ),
        ];
        message = localizations?.timeoutError ??
            'Request took too long. Please check your connection and try again.';
        break;
      case 'mirror':
        title = localizations?.networkErrorUser ?? 'Mirror Unavailable';
        iconData = Icons.cloud_off;
        iconColor = Colors.red.shade600;
        actions = [
          ElevatedButton.icon(
            onPressed: () async {
              // Navigate to mirror settings
              await context.push('/mirrors');
              // Retry after returning
              onRetry();
            },
            icon: const Icon(Icons.dns),
            label: Text(localizations?.changeMirror ?? 'Change Mirror'),
          ),
          OutlinedButton.icon(
            onPressed: onRetry,
            icon: const Icon(Icons.refresh),
            label: Text(localizations?.retry ?? 'Retry'),
          ),
        ];
        message = localizations?.mirrorConnectionError ??
            'Could not connect to RuTracker mirrors. Check your internet connection or try selecting another mirror in settings.';
        break;
      case 'network':
        title = localizations?.networkErrorUser ?? 'Network Error';
        iconData = Icons.wifi_off;
        iconColor = Colors.red.shade600;
        actions = [
          ElevatedButton.icon(
            onPressed: onRetry,
            icon: const Icon(Icons.refresh),
            label: Text(localizations?.retry ?? 'Retry'),
          ),
        ];
        message = localizations?.networkErrorUser ??
            'Network error occurred. Please check your connection and try again.';
        break;
      default:
        title = localizations?.error ?? 'Error';
        iconData = Icons.error_outline;
        iconColor = Colors.red.shade600;
        actions = [
          ElevatedButton.icon(
            onPressed: onRetry,
            icon: const Icon(Icons.refresh),
            label: Text(localizations?.retry ?? 'Retry'),
          ),
        ];
        message = errorMessage ?? localizations?.error ?? 'An error occurred';
    }

    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              iconData,
              size: 64,
              color: iconColor,
            ),
            const SizedBox(height: 16),
            Text(
              title,
              style: Theme.of(context).textTheme.titleLarge?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 8),
            Text(
              message,
              style: Theme.of(context).textTheme.bodyMedium,
              textAlign: TextAlign.center,
            ),
            if (actions.isNotEmpty) ...[
              const SizedBox(height: 24),
              Wrap(
                spacing: 12,
                runSpacing: 12,
                alignment: WrapAlignment.center,
                children: actions,
              ),
            ],
          ],
        ),
      ),
    );
  }
}
