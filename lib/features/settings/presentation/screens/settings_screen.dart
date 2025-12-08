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
import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:jabook/core/backup/backup_service.dart';
import 'package:jabook/core/di/providers/database_providers.dart';
import 'package:jabook/core/di/providers/player_providers.dart';
import 'package:jabook/core/di/providers/simple_player_providers.dart';
import 'package:jabook/core/di/providers/utils_providers.dart';
import 'package:jabook/core/infrastructure/config/app_config.dart';
import 'package:jabook/core/infrastructure/config/audio_settings_provider.dart';
import 'package:jabook/core/infrastructure/config/book_audio_settings_service.dart';
import 'package:jabook/core/infrastructure/config/language_manager.dart';
import 'package:jabook/core/infrastructure/config/language_provider.dart';
import 'package:jabook/core/infrastructure/config/notification_settings_provider.dart';
import 'package:jabook/core/infrastructure/permissions/permission_service.dart';
import 'package:jabook/core/player/native_audio_player.dart';
import 'package:jabook/core/player/player_state_provider.dart'
    show currentAudiobookGroupProvider;
import 'package:jabook/core/utils/app_title_utils.dart';
import 'package:jabook/features/settings/presentation/widgets/dialogs/audio_dialogs.dart';
import 'package:jabook/features/settings/presentation/widgets/dialogs/folder_dialogs.dart';
import 'package:jabook/features/settings/presentation/widgets/dialogs/permission_dialogs.dart';
import 'package:jabook/features/settings/presentation/widgets/folder_handlers.dart';
import 'package:jabook/features/settings/presentation/widgets/settings_sections/about_section.dart';
import 'package:jabook/features/settings/presentation/widgets/settings_sections/accessibility_section.dart';
import 'package:jabook/features/settings/presentation/widgets/settings_sections/audio_enhancement_section.dart';
import 'package:jabook/features/settings/presentation/widgets/settings_sections/audio_section.dart';
import 'package:jabook/features/settings/presentation/widgets/settings_sections/background_compatibility_section.dart';
import 'package:jabook/features/settings/presentation/widgets/settings_sections/backup_section.dart';
import 'package:jabook/features/settings/presentation/widgets/settings_sections/cache_section.dart';
import 'package:jabook/features/settings/presentation/widgets/settings_sections/download_section.dart';
import 'package:jabook/features/settings/presentation/widgets/settings_sections/language_section.dart';
import 'package:jabook/features/settings/presentation/widgets/settings_sections/library_folder_section.dart';
import 'package:jabook/features/settings/presentation/widgets/settings_sections/metadata_section.dart';
import 'package:jabook/features/settings/presentation/widgets/settings_sections/mirror_section.dart';
import 'package:jabook/features/settings/presentation/widgets/settings_sections/notification_section.dart';
import 'package:jabook/features/settings/presentation/widgets/settings_sections/permissions_section.dart';
import 'package:jabook/features/settings/presentation/widgets/settings_sections/rutracker_session_section.dart';
import 'package:jabook/features/settings/presentation/widgets/settings_sections/theme_section.dart';
import 'package:jabook/l10n/app_localizations.dart';
import 'package:share_plus/share_plus.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Screen for application settings and preferences.
///
/// This screen allows users to configure various application settings
/// including language preferences, audio quality, download preferences, and theme options.
class SettingsScreen extends ConsumerStatefulWidget {
  /// Creates a new SettingsScreen instance.
  ///
  /// The [key] parameter is optional and can be used to identify
  /// this widget in the widget tree.
  const SettingsScreen({super.key});

  @override
  ConsumerState<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends ConsumerState<SettingsScreen> {
  final LanguageManager _languageManager = LanguageManager();
  final PermissionService _permissionService = PermissionService();
  String _selectedLanguage = 'system';
  String? _downloadFolderPath;
  String? _libraryFolderPath;
  List<String> _libraryFolders = [];
  bool _wifiOnlyDownloads = false;
  bool _reduceAnimations = false;
  // Key for FutureBuilder to force rebuild when permissions change
  int _permissionStatusKey = 0;

  @override
  void initState() {
    super.initState();
    _loadLanguagePreference();
    _loadWifiOnlySetting();
    _loadAnimationSetting();
    // Load folder paths after first frame to ensure ref is available
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _loadDownloadFolder();
      _loadLibraryFolders();
      _setupNotificationSettingsListener();
    });
  }

  void _setupNotificationSettingsListener() {
    // Listen to notification settings changes and update native player
    ref.listen(notificationSettingsProvider, (previous, next) {
      if (previous?.notificationType != next.notificationType) {
        _updateNotificationType(next.notificationType);
      }
    });
  }

  Future<void> _updateNotificationType(NotificationType type) async {
    try {
      final nativePlayer = NativeAudioPlayer();
      await nativePlayer.setNotificationType(type == NotificationType.minimal);
    } on Exception catch (e) {
      debugPrint('Failed to update notification type: $e');
    }
  }

  Future<void> _loadLanguagePreference() async {
    final languageCode = await _languageManager.getCurrentLanguage();
    setState(() {
      _selectedLanguage = languageCode;
    });
  }

  Future<void> _loadDownloadFolder() async {
    // Use StoragePathUtils to get default path if not set
    final storageUtils = ref.read(storagePathUtilsProvider);
    final path = await storageUtils.getDefaultAudiobookPath();
    if (mounted) {
      setState(() {
        _downloadFolderPath = path;
      });
    }
  }

  Future<void> _loadLibraryFolders() async {
    final storageUtils = ref.read(storagePathUtilsProvider);
    final path = await storageUtils.getLibraryFolderPath();
    final folders = await storageUtils.getLibraryFolders();
    if (mounted) {
      setState(() {
        _libraryFolderPath = path;
        _libraryFolders = folders;
      });
    }
  }

  Future<void> _loadWifiOnlySetting() async {
    final prefs = await SharedPreferences.getInstance();
    final wifiOnly = prefs.getBool('wifi_only_downloads') ?? false;
    if (mounted) {
      setState(() {
        _wifiOnlyDownloads = wifiOnly;
      });
    }
  }

  Future<void> _saveWifiOnlySetting(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('wifi_only_downloads', value);
    if (mounted) {
      setState(() {
        _wifiOnlyDownloads = value;
      });
    }
  }

  Future<void> _loadAnimationSetting() async {
    final prefs = await SharedPreferences.getInstance();
    final reduceAnimations = prefs.getBool('reduce_animations') ?? false;
    if (mounted) {
      setState(() {
        _reduceAnimations = reduceAnimations;
      });
    }
  }

  Future<void> _saveAnimationSetting(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('reduce_animations', value);
    if (mounted) {
      setState(() {
        _reduceAnimations = value;
      });
    }
  }

  Future<void> _changeLanguage(String languageCode, WidgetRef ref) async {
    final languageNotifier = ref.read(languageProvider.notifier);
    await languageNotifier.changeLanguage(languageCode);
    setState(() {
      _selectedLanguage = languageCode;
    });

    // Show confirmation message
    if (mounted) {
      final loc = AppLocalizations.of(context);
      final languageName = languageCode == 'system'
          ? (loc?.systemDefault ?? 'System Default')
          : _languageManager.getLanguageName(languageCode);
      final message = loc?.languageChangedMessage(languageName) ??
          'Language changed to $languageName';
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(message),
          duration: const Duration(seconds: 2),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context);

    return Scaffold(
      appBar: AppBar(
        title: Text(
            (localizations?.settingsTitle ?? 'Settings').withFlavorSuffix()),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16.0),
        children: [
          // Language Settings Section
          Semantics(
            container: true,
            label: localizations?.languageSettingsLabel ?? 'Language settings',
            child: LanguageSection(
              selectedLanguage: _selectedLanguage,
              onLanguageChanged: (code) => _changeLanguage(code, ref),
            ),
          ),

          const SizedBox(height: 24),

          // Mirror Settings Section
          Semantics(
            container: true,
            label: localizations?.mirrorSourceSettingsLabel ??
                'Mirror and source settings',
            child: const MirrorSection(),
          ),

          const SizedBox(height: 24),

          // RuTracker Session Section
          Semantics(
            container: true,
            label: localizations?.rutrackerSessionLabel ??
                'RuTracker session management',
            child: const RutrackerSessionSection(),
          ),

          const SizedBox(height: 24),

          // Metadata Section
          Semantics(
            container: true,
            label:
                localizations?.metadataManagementLabel ?? 'Metadata management',
            child: const MetadataSection(),
          ),

          const SizedBox(height: 24),

          // Theme Settings Section
          Semantics(
            container: true,
            label: localizations?.themeSettingsLabel ?? 'Theme settings',
            child: const ThemeSection(),
          ),

          const SizedBox(height: 24),

          // Accessibility Settings Section
          Semantics(
            container: true,
            label: 'Accessibility settings',
            child: AccessibilitySection(
              reduceAnimations: _reduceAnimations,
              onReduceAnimationsChanged: _saveAnimationSetting,
            ),
          ),

          const SizedBox(height: 24),

          // Audio Settings Section
          Semantics(
            container: true,
            label: localizations?.audioPlaybackSettingsLabel ??
                'Audio playback settings',
            child: AudioSection(
              onShowPlaybackSpeedDialog: (ctx) => showPlaybackSpeedDialog(
                ctx,
                ref.watch(audioSettingsProvider),
                ref.read(audioSettingsProvider.notifier),
                _updateMediaSessionSkipDurations,
              ),
              onShowSkipDurationDialog: (ctx, duration, title) =>
                  showSkipDurationDialog(
                ctx,
                duration,
                (value) async {
                  final notifier = ref.read(audioSettingsProvider.notifier);
                  if (title.contains('Rewind')) {
                    await notifier.setDefaultRewindDuration(value);
                  } else {
                    await notifier.setDefaultForwardDuration(value);
                  }
                  await _updateMediaSessionSkipDurations();
                },
                title,
                _updateMediaSessionSkipDurations,
              ),
              onShowInactivityTimeoutDialog: (ctx) =>
                  showInactivityTimeoutDialog(
                ctx,
                ref.watch(audioSettingsProvider).inactivityTimeoutMinutes,
                ref.read(audioSettingsProvider.notifier),
                _updateInactivityTimeout,
              ),
              onShowResetAllBookSettingsDialog: (ctx) async {
                final confirmed = await showResetAllBookSettingsDialog(ctx);
                if (confirmed && ctx.mounted) {
                  // ignore: use_build_context_synchronously
                  // Context is safe here because we check ctx.mounted before use
                  await _resetAllBookSettings(ctx);
                }
              },
            ),
          ),

          const SizedBox(height: 24),

          // Audio Enhancement Section
          Semantics(
            container: true,
            label: 'Audio enhancement settings',
            child: AudioEnhancementSection(
              onShowVolumeBoostDialog: (ctx) => showVolumeBoostDialog(
                ctx,
                ref.watch(audioSettingsProvider),
                ref.read(audioSettingsProvider.notifier),
                _getVolumeBoostLabel,
                _applyAudioProcessingSettings,
              ),
              onShowDRCLevelDialog: (ctx) => showDRCLevelDialog(
                ctx,
                ref.watch(audioSettingsProvider),
                ref.read(audioSettingsProvider.notifier),
                _getDRCLevelLabel,
                _applyAudioProcessingSettings,
              ),
              onApplyAudioProcessingSettings: (ctx, settings) =>
                  _applyAudioProcessingSettings(settings),
              getVolumeBoostLabel: _getVolumeBoostLabel,
              getDRCLevelLabel: _getDRCLevelLabel,
            ),
          ),

          const SizedBox(height: 24),

          // Notification Settings Section (Android only)
          if (Platform.isAndroid)
            Semantics(
              container: true,
              label: 'Notification settings',
              child: const NotificationSection(),
            ),

          if (Platform.isAndroid) const SizedBox(height: 24),

          // Download Settings Section
          Semantics(
            container: true,
            label: localizations?.downloadSettingsLabel ?? 'Download settings',
            child: DownloadSection(
              downloadFolderPath: _downloadFolderPath,
              wifiOnlyDownloads: _wifiOnlyDownloads,
              onSelectDownloadFolder: () => FolderHandlers.selectDownloadFolder(
                context,
                mounted: mounted,
                ref: ref,
                onDownloadFolderChanged: (path) {
                  setState(() {
                    _downloadFolderPath = path;
                  });
                },
                onStateUpdate: () {
                  if (mounted) setState(() {});
                },
              ),
              onShowFolderSelectionInstructions: () =>
                  showFolderSelectionInstructions(
                context,
                () => FolderHandlers.selectDownloadFolder(
                  context,
                  mounted: mounted,
                  ref: ref,
                  onDownloadFolderChanged: (path) {
                    setState(() {
                      _downloadFolderPath = path;
                    });
                  },
                  onStateUpdate: () {
                    if (mounted) setState(() {});
                  },
                ),
              ),
              onWifiOnlyChanged: _saveWifiOnlySetting,
            ),
          ),

          const SizedBox(height: 24),

          // Library Folder Settings Section
          Semantics(
            container: true,
            label: localizations?.libraryFolderSettingsLabel ??
                'Library folder settings',
            child: LibraryFolderSection(
              libraryFolderPath: _libraryFolderPath,
              libraryFolders: _libraryFolders,
              onSelectLibraryFolder: () => FolderHandlers.selectLibraryFolder(
                context,
                mounted: mounted,
                ref: ref,
                currentLibraryFolderPath: _libraryFolderPath,
                onLoadLibraryFolders: _loadLibraryFolders,
                onStateUpdate: () {
                  if (mounted) setState(() {});
                },
                onMigrateFolder: (ctx, oldPath, newPath) =>
                    FolderHandlers.migrateLibraryFolder(
                  ctx,
                  oldPath,
                  newPath,
                  mounted: mounted,
                  onStateUpdate: () {
                    if (mounted) setState(() {});
                  },
                ),
              ),
              onAddLibraryFolder: () => FolderHandlers.addLibraryFolder(
                context,
                mounted: mounted,
                ref: ref,
                currentLibraryFolders: _libraryFolders,
                onLoadLibraryFolders: _loadLibraryFolders,
                onStateUpdate: () {
                  if (mounted) setState(() {});
                },
              ),
              onRemoveLibraryFolder: (folder) =>
                  FolderHandlers.removeLibraryFolder(
                context,
                folder,
                mounted: mounted,
                ref: ref,
                onLoadLibraryFolders: _loadLibraryFolders,
                onStateUpdate: () {
                  if (mounted) setState(() {});
                },
              ),
              onRestoreFolderPermission: (folder) =>
                  FolderHandlers.restoreFolderPermission(
                context,
                folder,
                mounted: mounted,
                ref: ref,
                onLoadLibraryFolders: _loadLibraryFolders,
                onStateUpdate: () {
                  if (mounted) setState(() {});
                },
              ),
              buildLibraryFolderItem: (ctx, folder, isPrimary) =>
                  FolderHandlers.buildLibraryFolderItem(
                ctx,
                folder,
                isPrimary,
                AppLocalizations.of(ctx),
                checkFolderPermission: FolderHandlers.checkFolderPermission,
                onRestorePermission: (f) =>
                    FolderHandlers.restoreFolderPermission(
                  ctx,
                  f,
                  mounted: mounted,
                  ref: ref,
                  onLoadLibraryFolders: _loadLibraryFolders,
                  onStateUpdate: () {
                    if (mounted) setState(() {});
                  },
                ),
                onRemoveFolder: (f) => FolderHandlers.removeLibraryFolder(
                  ctx,
                  f,
                  mounted: mounted,
                  ref: ref,
                  onLoadLibraryFolders: _loadLibraryFolders,
                  onStateUpdate: () {
                    if (mounted) setState(() {});
                  },
                ),
              ),
            ),
          ),

          const SizedBox(height: 24),

          // Storage Management Section
          Semantics(
            container: true,
            label:
                localizations?.storageManagementLabel ?? 'Storage management',
            child: ExpansionTile(
              leading: const Icon(Icons.storage),
              title: Text(
                localizations?.storageManagementTitle ?? 'Storage Management',
              ),
              subtitle: Text(
                localizations?.storageManagementDescription ??
                    'Manage library size, cache, and files',
              ),
              children: [
                ListTile(
                  leading: const Icon(Icons.storage),
                  title: Text(
                    localizations?.openStorageManagementButton ??
                        'Open Storage Management',
                  ),
                  onTap: () async {
                    await context.push('/storage-management');
                  },
                ),
              ],
            ),
          ),

          const SizedBox(height: 24),

          // Cache Settings Section
          Semantics(
            container: true,
            label: localizations?.cacheSettingsLabel ?? 'Cache settings',
            child: const CacheSection(),
          ),

          const SizedBox(height: 24),

          // Permissions Section
          Semantics(
            container: true,
            label: localizations?.appPermissionsLabel ?? 'App permissions',
            child: FutureBuilder<Map<String, bool>>(
              key: ValueKey<int>(_permissionStatusKey),
              future: _getPermissionStatus(),
              builder: (context, snapshot) => PermissionsSection(
                permissionStatusKey: _permissionStatusKey,
                permissionStatus: snapshot.data ?? {},
                onRequestStoragePermission: _requestStoragePermission,
                onRequestNotificationPermission: _requestNotificationPermission,
                onRequestAllPermissions: _requestAllPermissions,
              ),
            ),
          ),

          const SizedBox(height: 24),

          // About Section
          Semantics(
            container: true,
            label: localizations?.aboutAppLabel ?? 'About app',
            child: const AboutSection(),
          ),

          const SizedBox(height: 24),

          // Background Compatibility Section (Android only)
          if (Platform.isAndroid)
            Semantics(
              container: true,
              label: localizations?.backgroundTaskCompatibilityLabel ??
                  'Background task compatibility',
              child: const BackgroundCompatibilitySection(),
            ),

          if (Platform.isAndroid) const SizedBox(height: 24),

          // Backup & Restore Section
          Semantics(
            container: true,
            label: localizations?.backupRestoreLabel ?? 'Backup and restore',
            child: BackupSection(
              onExportData: () => _exportData(context),
              onImportData: () => _importData(context),
            ),
          ),
        ],
      ),
    );
  }

  String _getVolumeBoostLabel(String level) {
    switch (level) {
      case 'Off':
        return 'Off';
      case 'Boost50':
        return '+50%';
      case 'Boost100':
        return '+100%';
      case 'Boost200':
        return '+200%';
      case 'Auto':
        return 'Auto';
      default:
        return level;
    }
  }

  String _getDRCLevelLabel(String level) {
    switch (level) {
      case 'Off':
        return 'Off';
      case 'Gentle':
        return 'Gentle';
      case 'Medium':
        return 'Medium';
      case 'Strong':
        return 'Strong';
      default:
        return level;
    }
  }

  Future<void> _applyAudioProcessingSettings(
    AudioSettings settings,
  ) async {
    try {
      // Check if widget is still mounted before accessing providers
      if (!mounted) return;

      // Safely access player state provider - wrap in try-catch to handle
      // cases where provider might not be ready yet
      // Use new simplePlayerProvider for state check
      try {
        final playerState = ref.read(simplePlayerProvider);
        // Only apply if player is active
        if (playerState.playbackState != 0) {
          final playerService = ref.read(media3PlayerServiceProvider);
          await playerService.configureAudioProcessing(
            normalizeVolume: settings.normalizeVolume,
            volumeBoostLevel: settings.volumeBoostLevel,
            drcLevel: settings.drcLevel,
            speechEnhancer: settings.speechEnhancer,
            autoVolumeLeveling: settings.autoVolumeLeveling,
          );
        }
      } on Exception {
        // Provider not ready yet, skip applying settings
        return;
      }
    } on Exception catch (e) {
      // Log error but don't show to user (settings are saved anyway)
      debugPrint('Failed to apply audio processing settings: $e');
    }
  }

  /// Updates skip durations in MediaSessionManager if player is active.
  Future<void> _updateMediaSessionSkipDurations() async {
    try {
      // Check if widget is still mounted before accessing providers
      if (!mounted) return;

      // Safely access player state provider - wrap in try-catch to handle
      // cases where provider might not be ready yet
      // Use new simplePlayerProvider for state check
      try {
        final playerState = ref.read(simplePlayerProvider);
        // Only update if player is initialized and playing
        if (playerState.playbackState != 0) {
          final audioSettings = ref.read(audioSettingsProvider);
          final playerService = ref.read(media3PlayerServiceProvider);
          await playerService.updateSkipDurations(
            audioSettings.defaultRewindDuration,
            audioSettings.defaultForwardDuration,
          );
        }
      } on Exception {
        // Provider not ready yet, skip update
        return;
      }
    } on Exception {
      // Ignore errors - MediaSessionManager update is not critical
    }
  }

  /// Updates inactivity timeout if player is active.
  Future<void> _updateInactivityTimeout() async {
    try {
      // Check if widget is still mounted before accessing providers
      if (!mounted) return;

      // Safely access player state provider - wrap in try-catch to handle
      // cases where provider might not be ready yet
      // Use new simplePlayerProvider for state check
      try {
        final playerState = ref.read(simplePlayerProvider);
        // Only update if player is initialized
        if (playerState.playbackState != 0) {
          final audioSettings = ref.read(audioSettingsProvider);
          final playerService = ref.read(media3PlayerServiceProvider);
          await playerService.setInactivityTimeoutMinutes(
            audioSettings.inactivityTimeoutMinutes,
          );
        }
      } on Exception {
        // Provider not ready yet, skip update
        return;
      }
    } on Exception {
      // Ignore errors - inactivity timeout update is not critical
    }
  }

  /// Resets all individual book settings to global defaults.
  Future<void> _resetAllBookSettings(BuildContext context) async {
    try {
      final bookSettingsService = BookAudioSettingsService();
      await bookSettingsService.clearAllSettings();

      // Update MediaSessionManager if player is active
      await _updateMediaSessionSkipDurations();

      // If player is active, reapply global settings
      // Safely access player state provider - wrap in try-catch to handle
      // cases where provider might not be ready yet
      // Use new simplePlayerProvider for state check
      try {
        final playerState = ref.read(simplePlayerProvider);
        final currentGroup = ref.read(currentAudiobookGroupProvider);
        if (playerState.playbackState != 0 && currentGroup != null) {
          // Player is active, but we can't directly access local_player_screen
          // The settings will be applied on next book load or app restart
        }
      } on Exception {
        // Provider not ready yet, skip check
      }

      if (context.mounted) {
        // ignore: use_build_context_synchronously
        // Context is safe here because we check context.mounted before use
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              AppLocalizations.of(context)?.allBookSettingsReset ??
                  'All book settings have been reset to global defaults',
            ),
            duration: const Duration(seconds: 3),
          ),
        );
      }
    } on Exception catch (e) {
      if (context.mounted) {
        // ignore: use_build_context_synchronously
        // Context is safe here because we check context.mounted before use
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              AppLocalizations.of(context)?.errorResettingSettings ??
                  'Error resetting settings: ${e.toString()}',
            ),
            duration: const Duration(seconds: 3),
          ),
        );
      }
    }
  }

  Future<Map<String, bool>> _getPermissionStatus() async {
    final storage = await _permissionService.hasStoragePermission();
    final notification = await _permissionService.hasNotificationPermission();
    return {
      'storage': storage,
      'notification': notification,
    };
  }

  Future<void> _requestStoragePermission() async {
    if (!mounted) return;

    // Show explanation dialog first
    final shouldRequest = await showPermissionExplanationDialog(
      context,
      title: AppLocalizations.of(context)?.storagePermissionName ??
          'Storage Permission',
      message: AppLocalizations.of(context)?.storagePermissionDescription ??
          '${AppConfig().displayAppName} needs storage permission to save audiobook files and cache data. '
              'This allows you to download and play audiobooks offline.',
    );

    if (!shouldRequest) return;

    // Request permission
    final granted = await _permissionService.requestStoragePermission();

    if (!mounted) return;

    if (granted) {
      setState(() {});
      // ignore: use_build_context_synchronously
      // Context is safe here because we check mounted before use
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            AppLocalizations.of(context)?.fileAccessAvailable ??
                'Storage permission granted',
          ),
          backgroundColor: Colors.green,
        ),
      );
    } else {
      if (!mounted) return;

      // Check if manufacturer-specific guidance is needed
      final needsGuidance =
          await _permissionService.needsStoragePermissionGuidance();

      if (!mounted) return;

      if (needsGuidance) {
        // Check if SAF fallback should be suggested
        final shouldSuggestSaf =
            await _permissionService.shouldSuggestSafFallback();
        if (!mounted) return;

        if (shouldSuggestSaf) {
          // Show SAF fallback dialog
          final useSaf =
              await _permissionService.showSafFallbackDialog(context);
          if (!mounted) return;

          if (useSaf) {
            // User chose to use SAF - this will be handled by the library screen
            // where user can select folders using SAF
            // ignore: use_build_context_synchronously
            // Context is safe here because we check mounted before use
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text(
                  AppLocalizations.of(context)?.safFallbackMessage ??
                      'You can select folders using the folder selection option in Library settings.',
                ),
              ),
            );
            return;
          }
        }

        // Show manufacturer-specific guidance dialog
        final guidanceShown = await _permissionService
            .showStoragePermissionGuidanceDialog(context);
        if (!mounted) return;

        if (!guidanceShown) {
          // Fallback to standard settings dialog
          await showOpenSettingsDialog(
            context,
            title: AppLocalizations.of(context)?.storagePermissionName ??
                'Storage Permission',
            message: AppLocalizations.of(context)?.permissionDeniedMessage ??
                'Storage permission was denied. Please grant it in app settings to use this feature.',
            permissionService: _permissionService,
            onStateUpdate: () {
              if (mounted) setState(() {});
            },
          );
        }
      } else {
        // Show standard dialog to open settings
        await showOpenSettingsDialog(
          context,
          title: AppLocalizations.of(context)?.storagePermissionName ??
              'Storage Permission',
          message: AppLocalizations.of(context)?.permissionDeniedMessage ??
              'Storage permission was denied. Please grant it in app settings to use this feature.',
          permissionService: _permissionService,
          onStateUpdate: () {
            if (mounted) setState(() {});
          },
        );
      }
      // Force FutureBuilder to rebuild even if denied
      if (mounted) {
        setState(() {
          _permissionStatusKey++;
        });
      }
    }
  }

  Future<void> _requestNotificationPermission() async {
    if (!mounted) return;

    // Show explanation dialog first
    final shouldRequest = await showPermissionExplanationDialog(
      context,
      title: AppLocalizations.of(context)?.notificationsPermissionName ??
          'Notification Permission',
      message: AppLocalizations.of(context)
              ?.notificationsPermissionDescription ??
          '${AppConfig().displayAppName} needs notification permission to show playback controls and updates. '
              'This allows you to control playback from the notification panel.',
    );

    if (!shouldRequest) return;

    // Request permission
    final granted = await _permissionService.requestNotificationPermission();

    if (!mounted) return;

    if (granted) {
      // Force FutureBuilder to rebuild by changing key
      setState(() {
        _permissionStatusKey++;
      });
      // ignore: use_build_context_synchronously
      // Context is safe here because we check mounted before use
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            AppLocalizations.of(context)?.notificationsAvailable ??
                'Notification permission granted',
          ),
          backgroundColor: Colors.green,
        ),
      );
    } else {
      // Show dialog to open settings
      await showOpenSettingsDialog(
        context,
        title: AppLocalizations.of(context)?.notificationsPermissionName ??
            'Notification Permission',
        message: AppLocalizations.of(context)?.permissionDeniedMessage ??
            'Notification permission was denied. Please grant it in app settings to use this feature.',
        permissionService: _permissionService,
        onStateUpdate: () {
          if (mounted) setState(() {});
        },
      );
      // Force FutureBuilder to rebuild even if denied
      setState(() {
        _permissionStatusKey++;
      });
    }
  }

  Future<void> _requestAllPermissions() async {
    final results = await _permissionService.requestEssentialPermissions();
    final grantedCount = results.values.where((e) => e).length;
    if (mounted) {
      setState(() {});
      // ignore: use_build_context_synchronously
      // Context is safe here because we check mounted before use
      final loc = AppLocalizations.of(context);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(loc?.capabilitiesStatus(grantedCount, results.length) ??
              'Capabilities: $grantedCount/${results.length}'),
        ),
      );
    }
  }

  Future<void> _exportData(BuildContext context) async {
    if (!mounted) return;
    final localizations = AppLocalizations.of(context);
    final messenger = ScaffoldMessenger.of(context)
      ..showSnackBar(
        SnackBar(
          content:
              Text(localizations?.exportingDataMessage ?? 'Exporting data...'),
          duration: const Duration(seconds: 1),
        ),
      );

    try {
      final appDatabase = ref.read(appDatabaseProvider);
      final db = await appDatabase.ensureInitialized();
      final backupService = BackupService(db);

      // Export to file
      final filePath = await backupService.exportToFile();

      // Share the file
      final file = File(filePath);
      if (await file.exists()) {
        // ignore: deprecated_member_use
        await Share.shareXFiles(
          [XFile(filePath)],
          subject: '${AppConfig().displayAppName} Backup',
          text: '${AppConfig().displayAppName} data backup',
        );

        if (!mounted) return;
        messenger.showSnackBar(
          SnackBar(
            content: Text(localizations?.dataExportedSuccessfullyMessage ??
                'Data exported successfully'),
            backgroundColor: Colors.green,
          ),
        );
      }
    } on Exception catch (e) {
      if (!mounted) return;
      messenger.showSnackBar(
        SnackBar(
          content: Text(localizations?.failedToExportMessage(e.toString()) ??
              'Failed to export: ${e.toString()}'),
          backgroundColor: Colors.red,
        ),
      );
    }
  }

  Future<void> _importData(BuildContext context) async {
    if (!mounted) return;
    final messenger = ScaffoldMessenger.of(context);
    final localizations = AppLocalizations.of(context);
    try {
      final result = await FilePicker.platform.pickFiles(
        type: FileType.custom,
        allowedExtensions: ['json'],
      );

      if (result != null && result.files.single.path != null) {
        final filePath = result.files.single.path!;

        // Show confirmation dialog
        if (!mounted) return;
        // ignore: use_build_context_synchronously
        final confirmed = await showDialog<bool>(
          // ignore: use_build_context_synchronously
          context: context,
          builder: (dialogContext) => AlertDialog(
            title: Text(localizations?.importBackupTitle ?? 'Import Backup'),
            content: Text(localizations?.importBackupConfirmationMessage ??
                'This will import data from the backup file. Existing data may be merged or replaced. Continue?'),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(dialogContext).pop(false),
                child: Text(localizations?.cancel ?? 'Cancel'),
              ),
              ElevatedButton(
                onPressed: () => Navigator.of(dialogContext).pop(true),
                child: Text(localizations?.importButton ?? 'Import'),
              ),
            ],
          ),
        );

        if (confirmed != true) return;

        if (!mounted) return;
        messenger.showSnackBar(
          SnackBar(
            content: Text(
                localizations?.importingDataMessage ?? 'Importing data...'),
            duration: const Duration(seconds: 2),
          ),
        );

        final appDatabase = ref.read(appDatabaseProvider);
        await appDatabase.initialize();
        final backupService = BackupService(appDatabase.database);

        final stats = await backupService.importFromFile(filePath);

        if (!mounted) return;
        final message = StringBuffer('Imported: ');
        stats.forEach((key, value) {
          message.write('$key: $value, ');
        });
        final messageStr = message.toString();
        final cleanMessage = messageStr.endsWith(', ')
            ? messageStr.substring(0, messageStr.length - 2)
            : messageStr;

        messenger.showSnackBar(
          SnackBar(
            content: Text(cleanMessage),
            backgroundColor: Colors.green,
            duration: const Duration(seconds: 3),
          ),
        );
      }
    } on Exception catch (e) {
      if (!mounted) return;
      messenger.showSnackBar(
        SnackBar(
          content: Text(localizations?.failedToImportMessage(e.toString()) ??
              'Failed to import: ${e.toString()}'),
          backgroundColor: Colors.red,
        ),
      );
    }
  }

  // Removed unused methods - functionality moved to FolderHandlers:
  // - _selectLibraryFolder -> FolderHandlers.selectLibraryFolder
  // - _addLibraryFolder -> FolderHandlers.addLibraryFolder
  // - _buildLibraryFolderItem -> FolderHandlers.buildLibraryFolderItem
  // - _removeLibraryFolder -> FolderHandlers.removeLibraryFolder
  // - _checkFolderPermission -> FolderHandlers.checkFolderPermission
  // - _restoreFolderPermission -> FolderHandlers.restoreFolderPermission
  // - _migrateLibraryFolder -> FolderHandlers.migrateLibraryFolder
}
