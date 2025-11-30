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

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/core/di/providers/utils_providers.dart';
import 'package:jabook/core/utils/first_launch.dart';
import 'package:jabook/features/permissions/presentation/widgets/manufacturer_settings_dialog.dart';
import 'package:jabook/l10n/app_localizations.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Banner widget for showing background compatibility guidance on first launch.
///
/// This banner appears non-intrusively at the top of the screen on problematic
/// devices (Chinese manufacturers) to guide users to configure manufacturer-specific
/// settings for stable background operation.
class BackgroundCompatibilityBanner extends ConsumerStatefulWidget {
  /// Creates a new BackgroundCompatibilityBanner.
  const BackgroundCompatibilityBanner({super.key});

  @override
  ConsumerState<BackgroundCompatibilityBanner> createState() =>
      _BackgroundCompatibilityBannerState();
}

class _BackgroundCompatibilityBannerState
    extends ConsumerState<BackgroundCompatibilityBanner> {
  bool _shouldShow = false;
  bool _isDismissed = false;
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _checkIfShouldShow();
  }

  /// Checks if the banner should be shown.
  ///
  /// Banner is shown only if:
  /// - Platform is Android
  /// - This is the first launch
  /// - Device is from a problematic manufacturer (Chinese OEMs)
  /// - Banner hasn't been dismissed before
  Future<void> _checkIfShouldShow() async {
    if (!Platform.isAndroid) {
      setState(() {
        _isLoading = false;
        _shouldShow = false;
      });
      return;
    }

    try {
      // Check if this is first launch
      final isFirstLaunch = await FirstLaunchHelper.isFirstLaunch();
      if (!isFirstLaunch) {
        setState(() {
          _isLoading = false;
          _shouldShow = false;
        });
        return;
      }

      // Check if banner was already dismissed
      final prefs = await SharedPreferences.getInstance();
      final bannerDismissed =
          prefs.getBool('background_compatibility_banner_dismissed') ?? false;
      if (bannerDismissed) {
        setState(() {
          _isLoading = false;
          _shouldShow = false;
          _isDismissed = true;
        });
        return;
      }

      // Check if device needs aggressive guidance
      final deviceInfo = ref.read(deviceInfoUtilsProvider);
      final needsGuidance = await deviceInfo.needsAggressiveGuidance();

      if (mounted) {
        setState(() {
          _isLoading = false;
          _shouldShow = needsGuidance;
        });
      }
    } on Exception {
      // Silently fail - don't show banner if check fails
      if (mounted) {
        setState(() {
          _isLoading = false;
          _shouldShow = false;
        });
      }
    }
  }

  /// Dismisses the banner and saves the state.
  Future<void> _dismissBanner() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setBool('background_compatibility_banner_dismissed', true);
      if (mounted) {
        setState(() {
          _isDismissed = true;
          _shouldShow = false;
        });
      }
    } on Exception {
      // Silently fail
    }
  }

  /// Opens the manufacturer settings dialog.
  Future<void> _openSettingsDialog() async {
    if (!mounted) return;

    try {
      await showDialog(
        context: context,
        builder: (context) => const ManufacturerSettingsDialog(),
      );
    } on Exception {
      // Silently fail
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading || !_shouldShow || _isDismissed) {
      return const SizedBox.shrink();
    }

    final localizations = AppLocalizations.of(context);

    return Container(
      margin: const EdgeInsets.all(8),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(
          color: Theme.of(context).colorScheme.outline.withValues(alpha: 0.2),
        ),
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: _openSettingsDialog,
          borderRadius: BorderRadius.circular(8),
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Row(
              children: [
                Icon(
                  Icons.info_outline,
                  color: Theme.of(context).colorScheme.primary,
                  size: 20,
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        localizations?.backgroundCompatibilityBannerTitle ??
                            'Background Operation Settings',
                        style: Theme.of(context).textTheme.titleSmall?.copyWith(
                              fontWeight: FontWeight.bold,
                            ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        localizations?.backgroundCompatibilityBannerMessage ??
                            'To ensure stable background operation, you may need to configure device settings.',
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                IconButton(
                  icon: const Icon(Icons.close, size: 18),
                  onPressed: _dismissBanner,
                  tooltip: localizations?.dismiss ?? 'Dismiss',
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(
                    minWidth: 32,
                    minHeight: 32,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
