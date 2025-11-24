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

import 'package:jabook/core/permissions/permission_service_v2.dart';
import 'package:jabook/core/utils/bluetooth_utils.dart' as bluetooth_utils;
import 'package:jabook/core/utils/file_picker_utils.dart' as file_picker_utils;
import 'package:jabook/core/utils/notification_utils.dart'
    as notification_utils;
import 'package:jabook/l10n/app_localizations.dart';

/// Widget that displays system capabilities and allows testing them.
///
/// This widget shows what system APIs are available and allows users
/// to test file picking, notifications, and other capabilities.
class SystemCapabilitiesWidget extends StatefulWidget {
  /// Creates a new SystemCapabilitiesWidget instance.
  const SystemCapabilitiesWidget({super.key});

  @override
  State<SystemCapabilitiesWidget> createState() =>
      _SystemCapabilitiesWidgetState();
}

class _SystemCapabilitiesWidgetState extends State<SystemCapabilitiesWidget> {
  final PermissionServiceV2 _permissionService = PermissionServiceV2();
  Map<String, bool> _capabilities = {};
  bool _isLoading = false;

  @override
  void initState() {
    super.initState();
    _checkCapabilities();
  }

  Future<void> _checkCapabilities() async {
    setState(() {
      _isLoading = true;
    });

    try {
      final capabilities = await _permissionService.getCapabilitySummary();
      setState(() {
        _capabilities = capabilities;
        _isLoading = false;
      });
    } on Exception catch (e) {
      setState(() {
        _isLoading = false;
      });
      if (mounted) {
        final localizations = AppLocalizations.of(context);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(localizations?.capabilityCheckError(e.toString()) ??
                'Error checking capabilities: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  Future<void> _testFilePicking() async {
    try {
      final files = await file_picker_utils.pickAnyFiles();
      if (mounted) {
        final localizations = AppLocalizations.of(context);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(localizations?.filesSelected(files.length) ??
                'Files selected: ${files.length}'),
            backgroundColor: Colors.green,
          ),
        );
      }
    } on Exception catch (e) {
      if (mounted) {
        final localizations = AppLocalizations.of(context);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(localizations?.fileSelectionError(e.toString()) ??
                'Error selecting files: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  Future<void> _testImagePicking() async {
    try {
      final images = await file_picker_utils.pickImageFiles();
      if (mounted) {
        final localizations = AppLocalizations.of(context);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(localizations?.imagesSelected(images.length) ??
                'Images selected: ${images.length}'),
            backgroundColor: Colors.green,
          ),
        );
      }
    } on Exception catch (e) {
      if (mounted) {
        final localizations = AppLocalizations.of(context);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(localizations?.imageSelectionError(e.toString()) ??
                'Error selecting images: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  Future<void> _testNotification() async {
    final localizations = AppLocalizations.of(context);
    final success = await notification_utils.showSimpleNotification(
      title: localizations?.testNotificationTitle ?? 'Test notification',
      body: localizations?.testNotificationBody ??
          'This is a test notification from JaBook',
    );
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(success
              ? (localizations?.notificationSent ?? 'Notification sent')
              : (localizations?.failedToSendNotification ??
                  'Failed to send notification (channel not implemented)')),
          backgroundColor: success ? Colors.green : Colors.orange,
        ),
      );
    }
  }

  Future<void> _testBluetooth() async {
    try {
      final isAvailable = await bluetooth_utils.isBluetoothAvailable();
      final pairedDevices = await bluetooth_utils.getPairedDevices();

      if (mounted) {
        final localizations = AppLocalizations.of(context);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              '${localizations?.bluetoothAvailable(isAvailable.toString()) ?? 'Bluetooth available: $isAvailable'}\n'
              '${localizations?.pairedDevicesCount(pairedDevices.length) ?? 'Paired devices: ${pairedDevices.length}'}',
            ),
            backgroundColor: isAvailable ? Colors.green : Colors.orange,
          ),
        );
      }
    } on Exception catch (e) {
      if (mounted) {
        final localizations = AppLocalizations.of(context);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(localizations?.bluetoothCheckError(e.toString()) ??
                'Error checking Bluetooth: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) => Card(
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  const Icon(Icons.settings_applications),
                  const SizedBox(width: 8),
                  Text(
                    AppLocalizations.of(context)?.systemCapabilitiesTitle ??
                        'System Capabilities',
                    style: const TextStyle(
                        fontSize: 18, fontWeight: FontWeight.bold),
                  ),
                  const Spacer(),
                  IconButton(
                    icon: const Icon(Icons.refresh),
                    onPressed: _isLoading ? null : _checkCapabilities,
                  ),
                ],
              ),
              const SizedBox(height: 16),
              if (_isLoading)
                const Center(child: CircularProgressIndicator())
              else
                Column(
                  children: [
                    _buildCapabilityRow(
                      context,
                      AppLocalizations.of(context)?.fileAccessCapability ??
                          'File Access',
                      _capabilities['files'] ?? false,
                      Icons.folder,
                      _testFilePicking,
                    ),
                    _buildCapabilityRow(
                      context,
                      AppLocalizations.of(context)?.imageAccessCapability ??
                          'Image Access',
                      _capabilities['media_files'] ?? false,
                      Icons.image,
                      _testImagePicking,
                    ),
                    _buildCapabilityRow(
                      context,
                      AppLocalizations.of(context)?.cameraCapability ??
                          'Camera',
                      _capabilities['camera'] ?? false,
                      Icons.camera_alt,
                      () async {
                        final messenger = ScaffoldMessenger.of(context);
                        final localizations = AppLocalizations.of(context);
                        try {
                          final photo = await file_picker_utils.takePhoto();
                          if (!mounted) return;
                          messenger.showSnackBar(
                            SnackBar(
                              content: Text(photo != null
                                  ? (localizations?.photoTaken(photo) ??
                                      'Photo taken: $photo')
                                  : (localizations?.photoNotTaken ??
                                      'Photo not taken')),
                              backgroundColor:
                                  photo != null ? Colors.green : Colors.orange,
                            ),
                          );
                        } on Exception catch (e) {
                          if (!mounted) return;
                          messenger.showSnackBar(
                            SnackBar(
                              content: Text(
                                  localizations?.cameraError(e.toString()) ??
                                      'Camera error: $e'),
                              backgroundColor: Colors.red,
                            ),
                          );
                        }
                      },
                    ),
                    _buildCapabilityRow(
                      context,
                      AppLocalizations.of(context)?.notificationsCapability ??
                          'Notifications',
                      _capabilities['notifications'] ?? false,
                      Icons.notifications,
                      _testNotification,
                    ),
                    _buildCapabilityRow(
                      context,
                      'Bluetooth',
                      _capabilities['bluetooth'] ?? false,
                      Icons.bluetooth,
                      _testBluetooth,
                    ),
                  ],
                ),
              const SizedBox(height: 16),
              TextButton.icon(
                onPressed: () =>
                    _permissionService.showPermissionExplanationDialog(context),
                icon: const Icon(Icons.info_outline),
                label: Text(
                    AppLocalizations.of(context)?.capabilityExplanationButton ??
                        'Explain Capabilities'),
              ),
            ],
          ),
        ),
      );
}

Widget _buildCapabilityRow(
  BuildContext context,
  String title,
  bool isAvailable,
  IconData icon,
  VoidCallback onTest,
) =>
    Padding(
      padding: const EdgeInsets.symmetric(vertical: 4.0),
      child: Row(
        children: [
          Icon(
            icon,
            color: isAvailable ? Colors.green : Colors.grey,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              title,
              style: TextStyle(
                color: isAvailable ? Colors.black87 : Colors.grey,
              ),
            ),
          ),
          Icon(
            isAvailable ? Icons.check_circle : Icons.cancel,
            color: isAvailable ? Colors.green : Colors.red,
            size: 20,
          ),
          const SizedBox(width: 8),
          TextButton(
            onPressed: isAvailable ? onTest : null,
            child: Text(AppLocalizations.of(context)?.testButton ?? 'Test'),
          ),
        ],
      ),
    );
