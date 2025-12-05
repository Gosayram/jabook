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
import 'package:jabook/core/utils/accessibility_utils.dart';
import 'package:jabook/core/utils/color_scheme_helper.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Smart key button widget with context menu for authentication actions.
///
/// This widget provides a key icon button that shows a context menu
/// with authentication-related actions when long-pressed or clicked.
class SmartKeyButton extends ConsumerWidget {
  /// Creates a new SmartKeyButton instance.
  const SmartKeyButton({
    required this.onLogin,
    this.onLogout,
    this.onTestConnection,
    this.showContextMenu = true,
    super.key,
  });

  /// Callback when login is requested.
  final VoidCallback onLogin;

  /// Optional callback when logout is requested.
  final VoidCallback? onLogout;

  /// Optional callback when test connection is requested.
  final VoidCallback? onTestConnection;

  /// Whether to show context menu on long press.
  final bool showContextMenu;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final localizations = AppLocalizations.of(context);
    final isLoggedIn = ref.watch(isLoggedInProvider).value ?? false;
    final appConfig = ref.watch(appConfigProvider);
    final isBeta = appConfig.isBeta;
    final primaryColor = ColorSchemeHelper.getPrimaryColor(isBeta);
    final minSize = AccessibilityUtils.getMinTouchTargetSize(context);

    if (!showContextMenu || (!isLoggedIn && onLogout == null)) {
      // Simple button without context menu
      return Semantics(
        button: true,
        label: localizations?.rutrackerLoginTooltip ?? 'RuTracker Login',
        child: IconButton(
          tooltip: localizations?.rutrackerLoginTooltip ?? 'RuTracker Login',
          icon: Icon(
            Icons.vpn_key,
            color: isLoggedIn ? primaryColor : null,
          ),
          onPressed: onLogin,
          constraints: BoxConstraints(
            minWidth: minSize,
            minHeight: minSize,
          ),
        ),
      );
    }

    // Button with context menu
    return Semantics(
      button: true,
      label: localizations?.rutrackerLoginTooltip ?? 'RuTracker Login',
      child: PopupMenuButton<String>(
        tooltip: localizations?.rutrackerLoginTooltip ?? 'RuTracker Login',
        icon: Icon(
          Icons.vpn_key,
          color: isLoggedIn ? primaryColor : null,
        ),
        constraints: BoxConstraints(
          minWidth: minSize,
          minHeight: minSize,
        ),
        onSelected: (value) {
          switch (value) {
            case 'login':
              onLogin();
              break;
            case 'logout':
              onLogout?.call();
              break;
            case 'test':
              onTestConnection?.call();
              break;
            case 'auth_screen':
              context.push('/auth');
              break;
          }
        },
        itemBuilder: (context) {
          final items = <PopupMenuEntry<String>>[];

          if (!isLoggedIn) {
            items
              ..add(
                PopupMenuItem<String>(
                  value: 'login',
                  child: Row(
                    children: [
                      const Icon(Icons.login, size: 20),
                      const SizedBox(width: 12),
                      Text(
                        localizations?.loginButton ?? 'Login',
                      ),
                    ],
                  ),
                ),
              )
              ..add(
                PopupMenuItem<String>(
                  value: 'auth_screen',
                  child: Row(
                    children: [
                      const Icon(Icons.vpn_key, size: 20),
                      const SizedBox(width: 12),
                      Text(
                        localizations?.authTitle ?? 'Login',
                      ),
                    ],
                  ),
                ),
              );
          } else {
            if (onTestConnection != null) {
              items.add(
                PopupMenuItem<String>(
                  value: 'test',
                  child: Row(
                    children: [
                      const Icon(Icons.network_check, size: 20),
                      const SizedBox(width: 12),
                      Text(
                        localizations?.testConnectionButton ??
                            'Test Connection',
                      ),
                    ],
                  ),
                ),
              );
            }
            if (onLogout != null) {
              items.add(
                PopupMenuItem<String>(
                  value: 'logout',
                  child: Row(
                    children: [
                      const Icon(Icons.logout, size: 20, color: Colors.red),
                      const SizedBox(width: 12),
                      Text(
                        localizations?.logoutButton ?? 'Logout',
                        style: const TextStyle(color: Colors.red),
                      ),
                    ],
                  ),
                ),
              );
            }
          }

          return items;
        },
        child: IconButton(
          tooltip: localizations?.rutrackerLoginTooltip ?? 'RuTracker Login',
          icon: Icon(
            Icons.vpn_key,
            color: isLoggedIn ? primaryColor : null,
          ),
          onPressed: () {
            // On tap, show context menu or perform login
            if (isLoggedIn) {
              // Show menu on tap if logged in
              // PopupMenuButton will handle this
            } else {
              // Direct login if not logged in
              onLogin();
            }
          },
          constraints: BoxConstraints(
            minWidth: minSize,
            minHeight: minSize,
          ),
        ),
      ),
    );
  }
}
