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

import 'dart:io';

import 'package:device_info_plus/device_info_plus.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:jabook/core/background/background_compatibility_checker.dart';
import 'package:jabook/core/background/workmanager_diagnostics_service.dart';
import 'package:jabook/core/permissions/manufacturer_permissions_service.dart';
import 'package:jabook/core/utils/device_info_utils.dart';
import 'package:jabook/features/permissions/presentation/widgets/manufacturer_settings_dialog.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Screen for background task compatibility diagnostics and settings.
///
/// This screen shows device information, compatibility status, and provides
/// tools to configure manufacturer-specific settings for stable background operation.
class BackgroundCompatibilityScreen extends StatefulWidget {
  /// Creates a new BackgroundCompatibilityScreen.
  const BackgroundCompatibilityScreen({super.key});

  @override
  State<BackgroundCompatibilityScreen> createState() =>
      _BackgroundCompatibilityScreenState();
}

class _BackgroundCompatibilityScreenState
    extends State<BackgroundCompatibilityScreen> {
  final BackgroundCompatibilityChecker _checker =
      BackgroundCompatibilityChecker();
  final ManufacturerPermissionsService _manufacturerService =
      ManufacturerPermissionsService();
  final DeviceInfoUtils _deviceInfo = DeviceInfoUtils.instance;
  final WorkManagerDiagnosticsService _diagnosticsService =
      WorkManagerDiagnosticsService();

  CompatibilityReport? _report;
  bool _isLoading = true;
  String? _manufacturer;
  String? _customRom;
  String? _romVersion;
  String? _androidVersion;
  int? _standbyBucket;
  String? _standbyBucketDescription;
  List<TaskExecutionInfo>? _executionHistory;
  Map<String, dynamic>? _statistics;

  @override
  void initState() {
    super.initState();
    _loadDeviceInfo();
    _performCheck();
  }

  Future<void> _loadDeviceInfo() async {
    if (!Platform.isAndroid) {
      setState(() {
        _isLoading = false;
      });
      return;
    }

    try {
      final manufacturer = await _deviceInfo.getManufacturer();
      final customRom = await _deviceInfo.getCustomRom();
      final romVersion = await _deviceInfo.getRomVersion();
      final deviceInfoPlugin = DeviceInfoPlugin();
      final androidInfo = await deviceInfoPlugin.androidInfo;
      final standbyBucket = await _deviceInfo.getAppStandbyBucket();
      final standbyBucketDesc =
          await _deviceInfo.getAppStandbyBucketDescription();

      if (mounted) {
        setState(() {
          _manufacturer = manufacturer;
          _customRom = customRom;
          _romVersion = romVersion;
          _androidVersion =
              'Android ${androidInfo.version.release} (API ${androidInfo.version.sdkInt})';
          _standbyBucket = standbyBucket;
          _standbyBucketDescription = standbyBucketDesc;
        });
      }
    } on Exception {
      // Ignore errors
    }
  }

  Future<void> _performCheck() async {
    setState(() {
      _isLoading = true;
    });

    try {
      final localizations = AppLocalizations.of(context);
      final report = await _checker.checkNow(localizations);
      final history = await _diagnosticsService.getHistory(limit: 10);
      final stats = await _diagnosticsService.getStatistics();
      final standbyBucketDesc =
          await _deviceInfo.getAppStandbyBucketDescription(localizations);

      if (mounted) {
        setState(() {
          _report = report;
          _executionHistory = history;
          _statistics = stats;
          _standbyBucketDescription = standbyBucketDesc;
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

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
          title: Text(AppLocalizations.of(context)?.backgroundWorkTitle ??
              'Background Work'),
          actions: [
            IconButton(
              icon: const Icon(Icons.refresh),
              onPressed: _performCheck,
              tooltip: AppLocalizations.of(context)?.refreshDiagnostics ??
                  'Refresh diagnostics',
            ),
          ],
        ),
        body: _isLoading
            ? const Center(child: CircularProgressIndicator())
            : SingleChildScrollView(
                padding: const EdgeInsets.all(16.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _buildDeviceInfoSection(),
                    const SizedBox(height: 24),
                    _buildCompatibilityStatusSection(),
                    const SizedBox(height: 24),
                    _buildRecommendationsSection(),
                    const SizedBox(height: 24),
                    _buildWorkManagerDiagnosticsSection(),
                    const SizedBox(height: 24),
                    _buildSettingsSection(),
                  ],
                ),
              ),
      );

  Widget _buildDeviceInfoSection() => Card(
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  const Icon(Icons.phone_android, size: 24),
                  const SizedBox(width: 8),
                  Text(
                    AppLocalizations.of(context)?.deviceInformation ??
                        'Device Information',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                ],
              ),
              const SizedBox(height: 16),
              if (_manufacturer != null)
                _buildInfoRow(
                  AppLocalizations.of(context)?.manufacturer ?? 'Manufacturer',
                  _manufacturer!.toUpperCase(),
                ),
              if (_customRom != null)
                _buildInfoRow(
                  AppLocalizations.of(context)?.customRom ?? 'Custom ROM',
                  _romVersion != null
                      ? '$_customRom ($_romVersion)'
                      : _customRom!,
                ),
              if (_androidVersion != null)
                _buildInfoRow(
                  AppLocalizations.of(context)?.androidVersion ??
                      'Android Version',
                  _androidVersion!,
                ),
              if (_standbyBucketDescription != null)
                _buildInfoRow(
                  AppLocalizations.of(context)?.backgroundActivityMode ??
                      'Background Activity Mode',
                  _standbyBucketDescription!,
                  _standbyBucket != null && _standbyBucket! >= 40
                      ? Colors.orange
                      : Colors.green,
                ),
            ],
          ),
        ),
      );

  Widget _buildInfoRow(String label, String value, [Color? valueColor]) =>
      Padding(
        padding: const EdgeInsets.symmetric(vertical: 4.0),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SizedBox(
              width: 140,
              child: Text(
                label,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
              ),
            ),
            Expanded(
              child: Text(
                value,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: valueColor,
                      fontWeight: valueColor != null
                          ? FontWeight.w500
                          : FontWeight.normal,
                    ),
              ),
            ),
          ],
        ),
      );

  Widget _buildCompatibilityStatusSection() {
    if (_report == null) {
      return const SizedBox.shrink();
    }

    return Card(
      color: _report!.isCompatible
          ? Theme.of(context).colorScheme.surfaceContainerHighest
          : Theme.of(context).colorScheme.errorContainer,
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                  _report!.isCompatible ? Icons.check_circle : Icons.warning,
                  color: _report!.isCompatible ? Colors.green : Colors.orange,
                ),
                const SizedBox(width: 8),
                Text(
                  _report!.isCompatible
                      ? (AppLocalizations.of(context)?.compatibilityOk ??
                          'Compatibility: OK')
                      : (AppLocalizations.of(context)?.issuesDetected ??
                          'Issues Detected'),
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.bold,
                      ),
                ),
              ],
            ),
            if (_report!.issues.isNotEmpty) ...[
              const SizedBox(height: 16),
              Text(
                AppLocalizations.of(context)?.detectedIssues ??
                    'Detected Issues:',
                style: Theme.of(context).textTheme.titleSmall,
              ),
              const SizedBox(height: 8),
              ..._report!.issues.map(
                (issue) => Padding(
                  padding: const EdgeInsets.only(bottom: 8.0),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Icon(Icons.error_outline,
                          size: 20, color: Colors.orange),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          issue,
                          style: Theme.of(context).textTheme.bodyMedium,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildRecommendationsSection() {
    if (_report == null || _report!.recommendations.isEmpty) {
      return const SizedBox.shrink();
    }

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.lightbulb_outline, size: 24),
                const SizedBox(width: 8),
                Text(
                  AppLocalizations.of(context)?.recommendations ??
                      'Recommendations',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
              ],
            ),
            const SizedBox(height: 16),
            ..._report!.recommendations.asMap().entries.map(
                  (entry) => Padding(
                    padding: const EdgeInsets.only(bottom: 12.0),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Container(
                          width: 24,
                          height: 24,
                          decoration: BoxDecoration(
                            color:
                                Theme.of(context).colorScheme.primaryContainer,
                            shape: BoxShape.circle,
                          ),
                          child: Center(
                            child: Text(
                              '${entry.key + 1}',
                              style: TextStyle(
                                color: Theme.of(context)
                                    .colorScheme
                                    .onPrimaryContainer,
                                fontSize: 12,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Text(
                            entry.value,
                            style: Theme.of(context).textTheme.bodyMedium,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
          ],
        ),
      ),
    );
  }

  Widget _buildSettingsSection() {
    if (!Platform.isAndroid) {
      return const SizedBox.shrink();
    }

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.settings, size: 24),
                const SizedBox(width: 8),
                Text(
                  AppLocalizations.of(context)?.manufacturerSettings ??
                      'Manufacturer Settings',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
              ],
            ),
            const SizedBox(height: 16),
            Text(
              AppLocalizations.of(context)?.manufacturerSettingsDescription ??
                  'To ensure stable background operation, you need to configure manufacturer-specific device settings.',
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: Theme.of(context).colorScheme.onSurfaceVariant,
                  ),
            ),
            const SizedBox(height: 16),
            ListTile(
              leading: const Icon(Icons.power_settings_new),
              title: Text(AppLocalizations.of(context)?.autostartApp ??
                  'Autostart Application'),
              subtitle: Text(AppLocalizations.of(context)?.enableAutostart ??
                  'Enable autostart for stable operation'),
              trailing: const Icon(Icons.arrow_forward_ios),
              onTap: _manufacturerService.openAutostartSettings,
            ),
            ListTile(
              leading: const Icon(Icons.battery_charging_full),
              title: Text(AppLocalizations.of(context)?.batteryOptimization ??
                  'Battery Optimization'),
              subtitle: Text(
                  AppLocalizations.of(context)?.disableBatteryOptimization ??
                      'Disable battery optimization for the application'),
              trailing: const Icon(Icons.arrow_forward_ios),
              onTap: _manufacturerService.openBatteryOptimizationSettings,
            ),
            ListTile(
              leading: const Icon(Icons.settings_backup_restore),
              title: Text(AppLocalizations.of(context)?.backgroundActivity ??
                  'Background Activity'),
              subtitle: Text(
                  AppLocalizations.of(context)?.allowBackgroundActivity ??
                      'Allow background activity'),
              trailing: const Icon(Icons.arrow_forward_ios),
              onTap: _manufacturerService.openBackgroundRestrictionsSettings,
            ),
            const SizedBox(height: 8),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton.icon(
                onPressed: () async {
                  final result = await ManufacturerSettingsDialog.show(context);
                  if (result == true && mounted) {
                    // Refresh check after user configured settings
                    await _performCheck();
                  }
                },
                icon: const Icon(Icons.help_outline),
                label: Text(AppLocalizations.of(context)?.showInstructions ??
                    'Show Instructions'),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildWorkManagerDiagnosticsSection() {
    if (!Platform.isAndroid) {
      return const SizedBox.shrink();
    }

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.bug_report, size: 24),
                const SizedBox(width: 8),
                Text(
                  AppLocalizations.of(context)?.workManagerDiagnostics ??
                      'WorkManager Diagnostics',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
              ],
            ),
            const SizedBox(height: 16),
            if (_statistics != null) ...[
              _buildStatisticsRow(),
              const SizedBox(height: 16),
            ],
            if (_executionHistory != null && _executionHistory!.isNotEmpty) ...[
              Text(
                AppLocalizations.of(context)?.lastExecutions ??
                    'Last Executions:',
                style: Theme.of(context).textTheme.titleSmall,
              ),
              const SizedBox(height: 8),
              ..._executionHistory!.take(5).map(_buildExecutionRow),
            ] else if (_executionHistory != null &&
                _executionHistory!.isEmpty) ...[
              Text(
                AppLocalizations.of(context)?.executionHistoryEmpty ??
                    'Execution history is empty',
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildStatisticsRow() {
    final stats = _statistics!;
    final total = stats['totalExecutions'] as int;
    final successful = stats['successfulExecutions'] as int;
    final failed = stats['failedExecutions'] as int;
    final avgDelay = stats['averageDelayMinutes'] as double;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceAround,
          children: [
            _buildStatItem(
              AppLocalizations.of(context)?.total ?? 'Total',
              total.toString(),
            ),
            _buildStatItem(
              AppLocalizations.of(context)?.successful ?? 'Successful',
              successful.toString(),
              Colors.green,
            ),
            _buildStatItem(
              AppLocalizations.of(context)?.errors ?? 'Errors',
              failed.toString(),
              Colors.red,
            ),
            _buildStatItem(
              AppLocalizations.of(context)?.avgDelay ?? 'Avg. Delay',
              '${avgDelay.toStringAsFixed(1)} ${AppLocalizations.of(context)?.minutesAbbr ?? 'min'}',
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildStatItem(String label, String value, [Color? valueColor]) =>
      Column(
        children: [
          Text(
            value,
            style: Theme.of(context).textTheme.titleLarge?.copyWith(
                  color: valueColor,
                  fontWeight: FontWeight.bold,
                ),
          ),
          Text(
            label,
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                ),
          ),
        ],
      );

  Widget _buildExecutionRow(TaskExecutionInfo execution) {
    final statusColor = execution.status == 'success'
        ? Colors.green
        : execution.status == 'failed'
            ? Colors.red
            : Colors.orange;

    final statusIcon = execution.status == 'success'
        ? Icons.check_circle
        : execution.status == 'failed'
            ? Icons.error
            : Icons.cancel;

    final dateFormat = DateFormat('dd.MM.yyyy HH:mm');
    final localizations = AppLocalizations.of(context);
    final delayText = execution.actualDelay != null
        ? (localizations?.delay(execution.actualDelay!.inMinutes) ??
            'Delay: ${execution.actualDelay!.inMinutes} min')
        : '';

    return Padding(
      padding: const EdgeInsets.only(bottom: 8.0),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(statusIcon, size: 20, color: statusColor),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  dateFormat.format(execution.startTime),
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        fontWeight: FontWeight.w500,
                      ),
                ),
                if (delayText.isNotEmpty)
                  Text(
                    delayText,
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          color: Theme.of(context).colorScheme.onSurfaceVariant,
                        ),
                  ),
                if (execution.errorReason != null)
                  Text(
                    localizations?.errorLabel(execution.errorReason!) ??
                        'Error: ${execution.errorReason}',
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          color: Colors.red,
                        ),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
