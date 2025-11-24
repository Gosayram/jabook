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
import 'package:jabook/core/permissions/manufacturer_permissions_service.dart';
import 'package:jabook/core/utils/device_info_utils.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Dialog for configuring manufacturer-specific settings on Android devices.
///
/// This dialog shows instructions and buttons to open manufacturer-specific
/// settings for autostart, battery optimization, and background restrictions.
/// It adapts to the device's manufacturer and custom ROM.
class ManufacturerSettingsDialog extends StatefulWidget {
  /// Creates a new ManufacturerSettingsDialog.
  const ManufacturerSettingsDialog({super.key});

  /// Shows the manufacturer settings dialog.
  ///
  /// Returns `true` if user completed the setup, `false` if cancelled.
  static Future<bool> show(BuildContext context) async {
    final result = await showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (context) => const ManufacturerSettingsDialog(),
    );
    return result ?? false;
  }

  @override
  State<ManufacturerSettingsDialog> createState() =>
      _ManufacturerSettingsDialogState();
}

class _ManufacturerSettingsDialogState
    extends State<ManufacturerSettingsDialog> {
  final ManufacturerPermissionsService _manufacturerService =
      ManufacturerPermissionsService();
  final DeviceInfoUtils _deviceInfo = DeviceInfoUtils.instance;

  Map<String, String> _instructions = {};
  String? _customRom;
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadInstructions();
  }

  Future<void> _loadInstructions() async {
    setState(() {
      _isLoading = true;
    });

    try {
      final instructions =
          await _manufacturerService.getManufacturerSpecificInstructions();
      final customRom = await _deviceInfo.getCustomRom();

      if (mounted) {
        setState(() {
          _instructions = instructions;
          _customRom = customRom;
          _isLoading = false;
        });
      }
    } on Exception {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
    }
  }

  Future<void> _openAutostartSettings() async {
    await _manufacturerService.openAutostartSettings();
  }

  Future<void> _openBatteryOptimizationSettings() async {
    await _manufacturerService.openBatteryOptimizationSettings();
  }

  Future<void> _openBackgroundRestrictionsSettings() async {
    await _manufacturerService.openBackgroundRestrictionsSettings();
  }

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context);
    return AlertDialog(
      title: Text(
        _instructions['title'] ??
            (localizations?.manufacturerSettingsTitle ??
                'Settings for Stable Operation'),
      ),
      content: SingleChildScrollView(
        child: _isLoading
            ? const Center(
                child: Padding(
                  padding: EdgeInsets.all(16.0),
                  child: CircularProgressIndicator(),
                ),
              )
            : Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    _instructions['message'] ??
                        (localizations?.manufacturerSettingsDialogDescription ??
                            'To ensure stable operation, you need to configure the following settings:'),
                    style: Theme.of(context).textTheme.bodyMedium,
                  ),
                  const SizedBox(height: 16),
                  _buildStep(
                    context,
                    icon: Icons.power_settings_new,
                    text: _instructions['step1'] ??
                        (localizations?.enableAutostartStep ??
                            '1. Enable application autostart'),
                    onTap: _openAutostartSettings,
                  ),
                  const SizedBox(height: 12),
                  _buildStep(
                    context,
                    icon: Icons.battery_charging_full,
                    text: _instructions['step2'] ??
                        (localizations?.disableBatteryOptimizationStep ??
                            '2. Disable battery optimization for the application'),
                    onTap: _openBatteryOptimizationSettings,
                  ),
                  const SizedBox(height: 12),
                  _buildStep(
                    context,
                    icon: Icons.settings_backup_restore,
                    text: _instructions['step3'] ??
                        (localizations?.allowBackgroundActivityStep ??
                            '3. Allow background activity'),
                    onTap: _openBackgroundRestrictionsSettings,
                  ),
                  if (_customRom != null) ...[
                    const SizedBox(height: 16),
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: Theme.of(context)
                            .colorScheme
                            .surfaceContainerHighest,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Row(
                        children: [
                          Icon(
                            Icons.info_outline,
                            size: 20,
                            color:
                                Theme.of(context).colorScheme.onSurfaceVariant,
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              localizations?.detectedRom(_customRom!) ??
                                  'Detected ROM: $_customRom',
                              style: Theme.of(context)
                                  .textTheme
                                  .bodySmall
                                  ?.copyWith(
                                    color: Theme.of(context)
                                        .colorScheme
                                        .onSurfaceVariant,
                                  ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ],
              ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(false),
          child: Text(localizations?.skip ?? 'Skip'),
        ),
        ElevatedButton(
          onPressed: () => Navigator.of(context).pop(true),
          child: Text(localizations?.gotIt ?? 'Got It'),
        ),
      ],
    );
  }

  Widget _buildStep(
    BuildContext context, {
    required IconData icon,
    required String text,
    required VoidCallback onTap,
  }) =>
      InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(8),
        child: Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            border: Border.all(
              color:
                  Theme.of(context).colorScheme.outline.withValues(alpha: 0.3),
            ),
            borderRadius: BorderRadius.circular(8),
          ),
          child: Row(
            children: [
              Icon(
                icon,
                size: 24,
                color: Theme.of(context).colorScheme.primary,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  text,
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
              ),
              Icon(
                Icons.arrow_forward_ios,
                size: 16,
                color: Theme.of(context)
                    .colorScheme
                    .onSurface
                    .withValues(alpha: 0.5),
              ),
            ],
          ),
        ),
      );
}
