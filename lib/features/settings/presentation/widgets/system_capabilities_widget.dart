import 'package:flutter/material.dart';

import 'package:jabook/core/permissions/permission_service_v2.dart';
import 'package:jabook/core/utils/bluetooth_utils.dart' as bluetooth_utils;
import 'package:jabook/core/utils/file_picker_utils.dart' as file_picker_utils;
import 'package:jabook/core/utils/notification_utils.dart' as notification_utils;

/// Widget that displays system capabilities and allows testing them.
///
/// This widget shows what system APIs are available and allows users
/// to test file picking, notifications, and other capabilities.
class SystemCapabilitiesWidget extends StatefulWidget {
  /// Creates a new SystemCapabilitiesWidget instance.
  const SystemCapabilitiesWidget({super.key});

  @override
  State<SystemCapabilitiesWidget> createState() => _SystemCapabilitiesWidgetState();
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
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Ошибка проверки возможностей: $e'),
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
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Выбрано файлов: ${files.length}'),
            backgroundColor: Colors.green,
          ),
        );
      }
    } on Exception catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Ошибка выбора файлов: $e'),
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
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Выбрано изображений: ${images.length}'),
            backgroundColor: Colors.green,
          ),
        );
      }
    } on Exception catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Ошибка выбора изображений: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  Future<void> _testNotification() async {
    try {
      await notification_utils.showSimpleNotification(
        title: 'Тест уведомления',
        body: 'Это тестовое уведомление от JaBook',
      );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Уведомление отправлено'),
            backgroundColor: Colors.green,
          ),
        );
      }
    } on Exception catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Ошибка отправки уведомления: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  Future<void> _testBluetooth() async {
    try {
      final isAvailable = await bluetooth_utils.isBluetoothAvailable();
      final pairedDevices = await bluetooth_utils.getPairedDevices();
      
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              'Bluetooth доступен: $isAvailable\n'
              'Сопряженных устройств: ${pairedDevices.length}',
            ),
            backgroundColor: isAvailable ? Colors.green : Colors.orange,
          ),
        );
      }
    } on Exception catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Ошибка проверки Bluetooth: $e'),
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
                const Text(
                  'Системные возможности',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
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
                    'Доступ к файлам',
                    _capabilities['files'] ?? false,
                    Icons.folder,
                    _testFilePicking,
                  ),
                  _buildCapabilityRow(
                    'Доступ к изображениям',
                    _capabilities['media_files'] ?? false,
                    Icons.image,
                    _testImagePicking,
                  ),
                  _buildCapabilityRow(
                    'Камера',
                    _capabilities['camera'] ?? false,
                    Icons.camera_alt,
                    () async {
                      final messenger = ScaffoldMessenger.of(context);
                      try {
                        final photo = await file_picker_utils.takePhoto();
                        if (!mounted) return;
                        messenger.showSnackBar(
                          SnackBar(
                            content: Text(photo != null ? 'Фото сделано: $photo' : 'Фото не сделано'),
                            backgroundColor: photo != null ? Colors.green : Colors.orange,
                          ),
                        );
                      } on Exception catch (e) {
                        if (!mounted) return;
                        messenger.showSnackBar(
                          SnackBar(
                            content: Text('Ошибка камеры: $e'),
                            backgroundColor: Colors.red,
                          ),
                        );
                      }
                    },
                  ),
                  _buildCapabilityRow(
                    'Уведомления',
                    _capabilities['notifications'] ?? false,
                    Icons.notifications,
                    _testNotification,
                  ),
                  _buildCapabilityRow(
                    'Bluetooth',
                    _capabilities['bluetooth'] ?? false,
                    Icons.bluetooth,
                    _testBluetooth,
                  ),
                ],
              ),
            const SizedBox(height: 16),
            TextButton.icon(
              onPressed: () => _permissionService.showPermissionExplanationDialog(context),
              icon: const Icon(Icons.info_outline),
              label: const Text('Объяснение возможностей'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildCapabilityRow(
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
            child: const Text('Тест'),
          ),
        ],
      ),
    );
