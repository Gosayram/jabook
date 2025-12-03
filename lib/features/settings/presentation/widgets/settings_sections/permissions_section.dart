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
import 'package:jabook/l10n/app_localizations.dart';

/// Permissions section widget.
class PermissionsSection extends StatelessWidget {
  /// Creates a new PermissionsSection instance.
  const PermissionsSection({
    super.key,
    required this.permissionStatusKey,
    required this.permissionStatus,
    required this.onRequestStoragePermission,
    required this.onRequestNotificationPermission,
    required this.onRequestAllPermissions,
  });

  /// Key for FutureBuilder to force rebuild when permissions change.
  final int permissionStatusKey;

  /// Current permission status.
  final Map<String, bool> permissionStatus;

  /// Callback when storage permission is requested.
  final VoidCallback onRequestStoragePermission;

  /// Callback when notification permission is requested.
  final VoidCallback onRequestNotificationPermission;

  /// Callback when all permissions are requested.
  final VoidCallback onRequestAllPermissions;

  Widget _buildPermissionRow({
    required IconData icon,
    required String title,
    required String description,
    required bool isGranted,
    required VoidCallback onTap,
  }) =>
      AnimatedContainer(
        duration: const Duration(milliseconds: 300),
        curve: Curves.easeInOut,
        child: Card(
          child: ListTile(
            leading: AnimatedContainer(
              duration: const Duration(milliseconds: 300),
              curve: Curves.easeInOut,
              child: Icon(
                icon,
                color: isGranted ? Colors.green : Colors.orange,
              ),
            ),
            title: Text(title),
            subtitle: Text(description),
            trailing: AnimatedSwitcher(
              duration: const Duration(milliseconds: 300),
              child: isGranted
                  ? Icon(
                      Icons.check_circle,
                      key: const ValueKey('granted'),
                      color: Colors.green.shade600,
                    )
                  : Icon(
                      Icons.warning,
                      key: const ValueKey('denied'),
                      color: Colors.orange.shade600,
                    ),
            ),
            onTap: onTap,
          ),
        ),
      );

  @override
  Widget build(BuildContext context) {
    final hasStorage = permissionStatus['storage'] ?? false;
    final hasNotification = permissionStatus['notification'] ?? false;
    final allGranted = hasStorage && hasNotification;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          AppLocalizations.of(context)?.appPermissionsTitle ??
              'App Permissions',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 8),
        _buildPermissionRow(
          icon: Icons.folder,
          title:
              AppLocalizations.of(context)?.storagePermissionName ?? 'Storage',
          description:
              AppLocalizations.of(context)?.storagePermissionDescription ??
                  'Save audiobook files and cache data',
          isGranted: hasStorage,
          onTap: onRequestStoragePermission,
        ),
        const SizedBox(height: 8),
        _buildPermissionRow(
          icon: Icons.notifications,
          title: AppLocalizations.of(context)?.notificationsPermissionName ??
              'Notifications',
          description: AppLocalizations.of(context)
                  ?.notificationsPermissionDescription ??
              'Show playback controls and updates',
          isGranted: hasNotification,
          onTap: onRequestNotificationPermission,
        ),
        const SizedBox(height: 16),
        if (!allGranted)
          SizedBox(
            width: double.infinity,
            child: ElevatedButton.icon(
              onPressed: onRequestAllPermissions,
              icon: const Icon(Icons.security),
              label: Text(
                  AppLocalizations.of(context)?.grantAllPermissionsButton ??
                      'Grant All Permissions'),
            ),
          ),
        if (allGranted)
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: Colors.green.shade50,
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: Colors.green.shade200),
            ),
            child: Row(
              children: [
                Icon(Icons.check_circle, color: Colors.green.shade600),
                const SizedBox(width: 8),
                Text(
                  AppLocalizations.of(context)?.allPermissionsGranted ??
                      'All permissions granted',
                  style: TextStyle(
                    color: Colors.green.shade800,
                    fontWeight: FontWeight.w500,
                  ),
                ),
              ],
            ),
          ),
      ],
    );
  }
}
