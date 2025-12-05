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
import 'package:jabook/core/infrastructure/permissions/permission_service.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Shows a dialog explaining why a permission is needed.
///
/// Returns `true` if user wants to proceed, `false` otherwise.
Future<bool> showPermissionExplanationDialog(
  BuildContext context, {
  required String title,
  required String message,
}) async {
  final result = await showDialog<bool>(
    context: context,
    builder: (context) => AlertDialog(
      title: Text(title),
      content: Text(message),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context, false),
          child: Text(AppLocalizations.of(context)?.cancel ?? 'Cancel'),
        ),
        ElevatedButton(
          onPressed: () => Navigator.pop(context, true),
          child: Text(AppLocalizations.of(context)?.allowButton ?? 'Allow'),
        ),
      ],
    ),
  );
  return result ?? false;
}

/// Shows a dialog prompting user to open app settings.
Future<void> showOpenSettingsDialog(
  BuildContext context, {
  required String title,
  required String message,
  required PermissionService permissionService,
  required void Function() onStateUpdate,
}) async {
  final result = await showDialog<bool>(
    context: context,
    builder: (context) => AlertDialog(
      title: Text(title),
      content: Text(message),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: Text(AppLocalizations.of(context)?.cancel ?? 'Cancel'),
        ),
        ElevatedButton(
          onPressed: () {
            Navigator.pop(context, true);
            permissionService.openAppSettings();
          },
          child: Text(AppLocalizations.of(context)?.openSettingsButton ??
              'Open Settings'),
        ),
      ],
    ),
  );

  if ((result ?? false) && context.mounted) {
    // Wait a bit for user to potentially grant permission
    await Future.delayed(const Duration(seconds: 1));
    onStateUpdate();
  }
}
