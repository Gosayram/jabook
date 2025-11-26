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

import 'package:device_info_plus/device_info_plus.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:jabook/core/backup/backup_service.dart';
import 'package:jabook/core/cache/rutracker_cache_service.dart';
import 'package:jabook/core/config/audio_settings_manager.dart';
import 'package:jabook/core/config/audio_settings_provider.dart';
import 'package:jabook/core/config/book_audio_settings_service.dart';
import 'package:jabook/core/config/language_manager.dart';
import 'package:jabook/core/config/language_provider.dart';
import 'package:jabook/core/config/theme_provider.dart';
import 'package:jabook/core/library/library_migration_service.dart';
import 'package:jabook/core/metadata/audiobook_metadata_service.dart';
import 'package:jabook/core/metadata/metadata_sync_scheduler.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/permissions/permission_service.dart';
import 'package:jabook/core/player/player_state_provider.dart';
import 'package:jabook/core/session/session_manager.dart';
import 'package:jabook/core/utils/file_picker_utils.dart' as file_picker_utils;
import 'package:jabook/core/utils/storage_path_utils.dart';
import 'package:jabook/data/db/app_database.dart';
import 'package:jabook/features/settings/presentation/screens/background_compatibility_screen.dart';
import 'package:jabook/features/settings/presentation/screens/mirror_settings_screen.dart';
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
  // Key for FutureBuilder to force rebuild when permissions change
  int _permissionStatusKey = 0;

  @override
  void initState() {
    super.initState();
    _loadLanguagePreference();
    _loadDownloadFolder();
    _loadLibraryFolders();
    _loadWifiOnlySetting();
  }

  Future<void> _loadLanguagePreference() async {
    final languageCode = await _languageManager.getCurrentLanguage();
    setState(() {
      _selectedLanguage = languageCode;
    });
  }

  Future<void> _loadDownloadFolder() async {
    // Use StoragePathUtils to get default path if not set
    final storageUtils = StoragePathUtils();
    final path = await storageUtils.getDefaultAudiobookPath();
    if (mounted) {
      setState(() {
        _downloadFolderPath = path;
      });
    }
  }

  Future<void> _loadLibraryFolders() async {
    final storageUtils = StoragePathUtils();
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
        title: Text(localizations?.settingsTitle ?? 'Settings'),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16.0),
        children: [
          // Language Settings Section
          Semantics(
            container: true,
            label: localizations?.languageSettingsLabel ?? 'Language settings',
            child: _buildLanguageSection(context),
          ),

          const SizedBox(height: 24),

          // Mirror Settings Section
          Semantics(
            container: true,
            label: localizations?.mirrorSourceSettingsLabel ??
                'Mirror and source settings',
            child: _buildMirrorSection(context),
          ),

          const SizedBox(height: 24),

          // RuTracker Session Section
          Semantics(
            container: true,
            label: localizations?.rutrackerSessionLabel ??
                'RuTracker session management',
            child: _buildRutrackerSessionSection(context),
          ),

          const SizedBox(height: 24),

          // Metadata Section
          Semantics(
            container: true,
            label:
                localizations?.metadataManagementLabel ?? 'Metadata management',
            child: _buildMetadataSection(context),
          ),

          const SizedBox(height: 24),

          // Theme Settings Section
          Semantics(
            container: true,
            label: localizations?.themeSettingsLabel ?? 'Theme settings',
            child: _buildThemeSection(context),
          ),

          const SizedBox(height: 24),

          // Audio Settings Section
          Semantics(
            container: true,
            label: localizations?.audioPlaybackSettingsLabel ??
                'Audio playback settings',
            child: _buildAudioSection(context),
          ),

          const SizedBox(height: 24),

          // Download Settings Section
          Semantics(
            container: true,
            label: localizations?.downloadSettingsLabel ?? 'Download settings',
            child: _buildDownloadSection(context),
          ),

          const SizedBox(height: 24),

          // Library Folder Settings Section
          Semantics(
            container: true,
            label: localizations?.libraryFolderSettingsLabel ??
                'Library folder settings',
            child: _buildLibraryFolderSection(context),
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
            child: _buildCacheSection(context),
          ),

          const SizedBox(height: 24),

          // Permissions Section
          Semantics(
            container: true,
            label: localizations?.appPermissionsLabel ?? 'App permissions',
            child: _buildPermissionsSection(context),
          ),

          const SizedBox(height: 24),

          // About Section
          Semantics(
            container: true,
            label: localizations?.aboutAppLabel ?? 'About app',
            child: _buildAboutSection(context),
          ),

          const SizedBox(height: 24),

          // Background Compatibility Section (Android only)
          if (Platform.isAndroid)
            Semantics(
              container: true,
              label: localizations?.backgroundTaskCompatibilityLabel ??
                  'Background task compatibility',
              child: _buildBackgroundCompatibilitySection(context),
            ),

          if (Platform.isAndroid) const SizedBox(height: 24),

          // Backup & Restore Section
          Semantics(
            container: true,
            label: localizations?.backupRestoreLabel ?? 'Backup and restore',
            child: _buildBackupSection(context),
          ),
        ],
      ),
    );
  }

  Widget _buildLanguageSection(BuildContext context) {
    final localizations = AppLocalizations.of(context);
    final languages = _languageManager.getAvailableLanguages();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          localizations?.language ?? 'Language',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 8),
        Text(
          localizations?.languageDescription ??
              'Choose your preferred language for the app interface',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 16),
        ...languages.map(_buildLanguageTile),
      ],
    );
  }

  Widget _buildLanguageTile(Map<String, String> language) {
    final localizations = AppLocalizations.of(context);
    final languageName = language['code'] == 'system'
        ? (localizations?.systemDefault ?? 'System Default')
        : language['name']!;

    return ListTile(
      leading: Text(
        language['flag']!,
        style: const TextStyle(fontSize: 24),
      ),
      title: Text(languageName),
      trailing: _selectedLanguage == language['code']
          ? const Icon(Icons.check, color: Colors.blue)
          : null,
      onTap: () => _changeLanguage(language['code']!, ref),
    );
  }

  Widget _buildMirrorSection(BuildContext context) {
    final localizations = AppLocalizations.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          localizations?.mirrorsScreenTitle ?? 'Mirrors & Sources',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 8),
        Text(
          'Configure RuTracker mirrors for optimal search performance',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 16),
        Semantics(
          button: true,
          label: 'Manage RuTracker mirrors',
          child: ListTile(
            leading: const Icon(Icons.dns),
            title: Text(localizations?.mirrorsScreenTitle ?? 'Manage Mirrors'),
            subtitle: Text(localizations?.configureMirrorsSubtitle ??
                'Configure and test RuTracker mirrors'),
            trailing: const Icon(Icons.arrow_forward_ios),
            onTap: () {
              Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (context) => const MirrorSettingsScreen(),
                ),
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _buildRutrackerSessionSection(BuildContext context) => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            AppLocalizations.of(context)?.rutrackerTitle ?? 'RuTracker',
            style: Theme.of(context).textTheme.titleLarge,
          ),
          const SizedBox(height: 8),
          Text(
            AppLocalizations.of(context)?.rutrackerSessionDescription ??
                'RuTracker session management (cookie)',
            style: Theme.of(context).textTheme.bodySmall,
          ),
          const SizedBox(height: 16),
          ListTile(
            leading: const Icon(Icons.login),
            title: Text(AppLocalizations.of(context)?.loginButton ??
                'Login to RuTracker'),
            subtitle: Text(
                AppLocalizations.of(context)?.loginRequiredForSearch ??
                    'Enter your credentials to authenticate'),
            onTap: () async {
              final messenger = ScaffoldMessenger.of(context);
              final localizations = AppLocalizations.of(context);
              // Navigate to auth screen
              final result = await context.push('/auth');
              // If login was successful, validate cookies
              if (result == true && mounted) {
                final isValid = await DioClient.validateCookies();
                if (isValid) {
                  messenger.showSnackBar(
                    SnackBar(
                      content: Text(localizations?.authorizationSuccessful ??
                          'Authorization successful'),
                      backgroundColor: Colors.green,
                    ),
                  );
                } else {
                  messenger.showSnackBar(
                    SnackBar(
                      content: Text(localizations?.authorizationFailedMessage ??
                          'Authorization failed'),
                      backgroundColor: Colors.orange,
                    ),
                  );
                }
              }
            },
          ),
          ListTile(
            leading: const Icon(Icons.logout),
            title: Text(AppLocalizations.of(context)?.clearSessionButton ??
                'Clear RuTracker session (cookie)'),
            subtitle: Text(AppLocalizations.of(context)?.clearSessionSubtitle ??
                'Delete saved cookies and logout from account'),
            onTap: () async {
              // Clear session using SessionManager
              final messenger = ScaffoldMessenger.of(context);
              final localizations = AppLocalizations.of(context);
              try {
                final sessionManager = SessionManager();
                await sessionManager.clearSession();
                if (mounted) {
                  messenger.showSnackBar(
                    SnackBar(
                        content: Text(localizations?.sessionClearedMessage ??
                            'RuTracker session cleared')),
                  );
                }
              } on Exception catch (e) {
                if (mounted) {
                  messenger.showSnackBar(
                    SnackBar(
                      content: Text('Error clearing session: ${e.toString()}'),
                    ),
                  );
                }
              }
            },
          ),
        ],
      );

  Widget _buildMetadataSection(BuildContext context) =>
      FutureBuilder<Map<String, dynamic>>(
        future: _loadMetadataStats(),
        builder: (context, snapshot) {
          final stats = snapshot.data;
          final isLoading = snapshot.connectionState == ConnectionState.waiting;
          final isUpdating =
              snapshot.hasData && (stats?['is_updating'] as bool? ?? false);

          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                AppLocalizations.of(context)?.metadataSectionTitle ??
                    'Audiobook Metadata',
                style: Theme.of(context).textTheme.titleLarge,
              ),
              const SizedBox(height: 8),
              Text(
                AppLocalizations.of(context)?.metadataSectionDescription ??
                    'Manage local audiobook metadata database',
                style: Theme.of(context).textTheme.bodySmall,
              ),
              const SizedBox(height: 16),
              if (stats == null && isLoading)
                const Padding(
                  padding: EdgeInsets.symmetric(vertical: 8.0),
                  child: LinearProgressIndicator(),
                )
              else if (stats != null)
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _buildStatRow(
                        AppLocalizations.of(context)?.totalRecordsLabel ??
                            'Total records',
                        '${stats['total'] ?? 0}'),
                    if (stats['last_sync'] != null)
                      _buildStatRow(
                        AppLocalizations.of(context)?.lastUpdateLabel ??
                            'Last update',
                        _formatDateTime(stats['last_sync'] as String?),
                      ),
                  ],
                ),
              const SizedBox(height: 12),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton.icon(
                  onPressed: isUpdating
                      ? null
                      : () async {
                          await _updateMetadata(context);
                          if (mounted) setState(() {});
                        },
                  icon: isUpdating
                      ? const SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.sync),
                  label: Text(isUpdating
                      ? (AppLocalizations.of(context)?.updatingText ??
                          'Updating...')
                      : (AppLocalizations.of(context)?.updateMetadataButton ??
                          'Update Metadata')),
                ),
              ),
            ],
          );
        },
      );

  Future<Map<String, dynamic>> _loadMetadataStats() async {
    try {
      final db = AppDatabase().database;
      final metadataService = AudiobookMetadataService(db);
      final scheduler = MetadataSyncScheduler(db, metadataService);

      final syncStats = await scheduler.getSyncStatistics();
      final metadataStats =
          syncStats['metadata'] as Map<String, dynamic>? ?? {};

      return {
        'total': metadataStats['total'] ?? 0,
        'last_sync': syncStats['last_daily_sync'] as String?,
        'last_full_sync': syncStats['last_full_sync'] as String?,
        'is_updating': false,
      };
    } on Exception {
      return {'total': 0, 'is_updating': false};
    }
  }

  Future<void> _updateMetadata(BuildContext context) async {
    final messenger = ScaffoldMessenger.of(context);
    final localizations = AppLocalizations.of(context);

    try {
      // Show updating indicator
      setState(() {});

      final db = AppDatabase().database;
      final metadataService = AudiobookMetadataService(db);
      final scheduler = MetadataSyncScheduler(db, metadataService);

      messenger.showSnackBar(
        SnackBar(
          content: Text(localizations?.metadataUpdateStartedMessage ??
              'Metadata update started...'),
          duration: const Duration(seconds: 2),
        ),
      );

      // Run full sync
      final results = await scheduler.runFullSync();

      final total = results.values.fold<int>(0, (sum, count) => sum + count);

      if (mounted) {
        messenger.showSnackBar(
          SnackBar(
            content: Text(
                localizations?.metadataUpdateCompletedMessage(total) ??
                    'Update completed: collected $total records'),
            duration: const Duration(seconds: 3),
          ),
        );
        setState(() {});
      }
    } on Exception catch (e) {
      if (mounted) {
        messenger.showSnackBar(
          SnackBar(
            content: Text(localizations?.metadataUpdateError(e.toString()) ??
                'Update error: $e'),
            backgroundColor: Colors.red,
            duration: const Duration(seconds: 5),
          ),
        );
        setState(() {});
      }
    }
  }

  String _formatDateTime(String? isoString) {
    final loc = AppLocalizations.of(context);
    if (isoString == null) {
      return loc?.neverDate ?? 'Never';
    }
    try {
      final dateTime = DateTime.parse(isoString);
      final now = DateTime.now();
      final diff = now.difference(dateTime);

      if (diff.inDays > 0) {
        return loc?.daysAgo(diff.inDays) ?? '${diff.inDays} days ago';
      } else if (diff.inHours > 0) {
        return loc?.hoursAgo(diff.inHours) ?? '${diff.inHours} hours ago';
      } else if (diff.inMinutes > 0) {
        return loc?.minutesAgo(diff.inMinutes) ??
            '${diff.inMinutes} minutes ago';
      } else {
        return loc?.justNow ?? 'Just now';
      }
    } on Exception {
      return loc?.unknownDate ?? 'Unknown';
    }
  }

  Widget _buildThemeSection(BuildContext context) {
    final localizations = AppLocalizations.of(context);
    final themeSettings = ref.watch(themeProvider);
    final themeNotifier = ref.read(themeProvider.notifier);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          localizations?.themeTitle ?? 'Theme',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 8),
        Text(
          localizations?.themeDescription ??
              'Customize the appearance of the app',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 16),
        // Follow system theme toggle
        ListTile(
          leading: const Icon(Icons.brightness_auto),
          title: Text(
            localizations?.followSystemTheme ?? 'Follow System Theme',
          ),
          trailing: Semantics(
            label: 'Follow system theme toggle',
            child: Switch(
              value: themeSettings.followSystem,
              onChanged: (value) async {
                await themeNotifier.setFollowSystem(value);
              },
            ),
          ),
        ),
        // Theme mode selection (only enabled when not following system)
        if (!themeSettings.followSystem) ...[
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16.0),
            child: SegmentedButton<String>(
              segments: const [
                ButtonSegment<String>(
                  value: 'light',
                  label: Text('Light'),
                  icon: Icon(Icons.light_mode),
                ),
                ButtonSegment<String>(
                  value: 'dark',
                  label: Text('Dark'),
                  icon: Icon(Icons.dark_mode),
                ),
              ],
              selected: {themeSettings.mode},
              onSelectionChanged: (newSelection) {
                if (newSelection.isNotEmpty) {
                  themeNotifier.setThemeMode(newSelection.first);
                }
              },
            ),
          ),
          const SizedBox(height: 8),
        ],
        // High contrast toggle
        ListTile(
          leading: const Icon(Icons.contrast),
          title: Text(localizations?.highContrast ?? 'High Contrast'),
          trailing: Semantics(
            label: 'High contrast mode toggle',
            child: Switch(
              value: themeSettings.highContrastEnabled,
              onChanged: (value) async {
                await themeNotifier.setHighContrastEnabled(value);
              },
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildAudioSection(BuildContext context) {
    final localizations = AppLocalizations.of(context);
    final audioSettings = ref.watch(audioSettingsProvider);
    final audioNotifier = ref.read(audioSettingsProvider.notifier);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          localizations?.audioTitle ?? 'Audio',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 8),
        Text(
          localizations?.audioDescription ??
              'Configure audio playback settings',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 16),
        // Playback speed selection
        Semantics(
          button: true,
          label: 'Set playback speed',
          child: ListTile(
            leading: const Icon(Icons.speed),
            title: Text(localizations?.playbackSpeedTitle ??
                localizations?.playbackSpeed ??
                'Playback Speed'),
            subtitle: Text('${audioSettings.defaultPlaybackSpeed}x'),
            onTap: () {
              _showPlaybackSpeedDialog(context, audioSettings, audioNotifier);
            },
          ),
        ),
        // Rewind duration selection
        Semantics(
          button: true,
          label: 'Set rewind duration',
          child: ListTile(
            leading: const Icon(Icons.replay_10),
            title:
                Text(localizations?.rewindDurationTitle ?? 'Rewind Duration'),
            subtitle: Text(
              '${audioSettings.defaultRewindDuration} ${localizations?.secondsLabel ?? 'seconds'}',
            ),
            onTap: () {
              _showSkipDurationDialog(
                context,
                audioSettings.defaultRewindDuration,
                audioNotifier.setDefaultRewindDuration,
                localizations?.rewindDurationTitle ?? 'Rewind Duration',
              );
            },
          ),
        ),
        // Forward duration selection
        Semantics(
          button: true,
          label: 'Set forward duration',
          child: ListTile(
            leading: const Icon(Icons.forward_30),
            title:
                Text(localizations?.forwardDurationTitle ?? 'Forward Duration'),
            subtitle: Text(
              '${audioSettings.defaultForwardDuration} ${localizations?.secondsLabel ?? 'seconds'}',
            ),
            onTap: () {
              _showSkipDurationDialog(
                context,
                audioSettings.defaultForwardDuration,
                audioNotifier.setDefaultForwardDuration,
                localizations?.forwardDurationTitle ?? 'Forward Duration',
              );
            },
          ),
        ),
        const Divider(),
        // Reset all book settings button
        ListTile(
          leading: const Icon(Icons.restore, color: Colors.red),
          title: Text(
            localizations?.resetAllBookSettings ?? 'Reset all book settings',
            style: const TextStyle(color: Colors.red),
          ),
          subtitle: Text(
            localizations?.resetAllBookSettingsDescription ??
                'Remove individual settings for all books',
          ),
          onTap: () => _showResetAllBookSettingsDialog(context),
        ),
      ],
    );
  }

  /// Shows dialog for selecting playback speed.
  Future<void> _showPlaybackSpeedDialog(
    BuildContext context,
    AudioSettings audioSettings,
    AudioSettingsNotifier audioNotifier,
  ) async {
    final speeds = AudioSettingsManager.getAvailablePlaybackSpeeds();
    final selectedSpeed = audioSettings.defaultPlaybackSpeed;

    final result = await showDialog<double>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(AppLocalizations.of(context)?.playbackSpeedTitle ??
            'Playback Speed'),
        content: SizedBox(
          width: double.maxFinite,
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: speeds
                  .map((speed) => RadioListTile<double>(
                        title: Text('${speed}x'),
                        value: speed,
                        // ignore: deprecated_member_use
                        groupValue: selectedSpeed,
                        // ignore: deprecated_member_use
                        onChanged: (value) {
                          Navigator.of(context).pop(value);
                        },
                      ))
                  .toList(),
            ),
          ),
        ),
      ),
    );

    if (result != null) {
      await audioNotifier.setDefaultPlaybackSpeed(result);
      // Update MediaSessionManager if player is active
      await _updateMediaSessionSkipDurations();
    }
  }

  /// Shows dialog for selecting skip duration.
  Future<void> _showSkipDurationDialog(
    BuildContext context,
    int currentDuration,
    Future<void> Function(int) onDurationSelected,
    String title,
  ) async {
    final durations = [5, 10, 15, 20, 30, 45, 60];

    final result = await showDialog<int>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: durations.map((duration) {
            final localizations = AppLocalizations.of(context);
            return RadioListTile<int>(
              title: Text(
                '$duration ${localizations?.secondsLabel ?? 'seconds'}',
              ),
              value: duration,
              // ignore: deprecated_member_use
              groupValue: currentDuration,
              // ignore: deprecated_member_use
              onChanged: (value) {
                Navigator.of(context).pop(value);
              },
            );
          }).toList(),
        ),
      ),
    );

    if (result != null) {
      await onDurationSelected(result);
      // Update MediaSessionManager if player is active
      await _updateMediaSessionSkipDurations();
    }
  }

  /// Updates skip durations in MediaSessionManager if player is active.
  Future<void> _updateMediaSessionSkipDurations() async {
    try {
      final playerState = ref.read(playerStateProvider);
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
      // Ignore errors - MediaSessionManager update is not critical
    }
  }

  /// Shows confirmation dialog for resetting all book settings.
  Future<void> _showResetAllBookSettingsDialog(BuildContext context) async {
    final localizations = AppLocalizations.of(context);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(
          localizations?.resetAllBookSettings ?? 'Reset all book settings',
        ),
        content: Text(
          localizations?.resetAllBookSettingsConfirmation ??
              'This will remove individual audio settings for all books. '
                  'All books will use global settings. This action cannot be undone.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: Text(localizations?.cancel ?? 'Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.of(context).pop(true),
            style: TextButton.styleFrom(foregroundColor: Colors.red),
            child: Text(localizations?.reset ?? 'Reset'),
          ),
        ],
      ),
    );

    if (confirmed ?? false) {
      if (context.mounted) {
        await _resetAllBookSettings(context);
      }
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
      final playerState = ref.read(playerStateProvider);
      if (playerState.playbackState != 0 &&
          playerState.currentGroupPath != null) {
        // Player is active, but we can't directly access local_player_screen
        // The settings will be applied on next book load or app restart
      }

      if (context.mounted) {
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

  Widget _buildDownloadSection(BuildContext context) {
    final localizations = AppLocalizations.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          localizations?.downloadsTitle ?? 'Downloads',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 8),
        Text(
          localizations?.downloadsDescription ??
              'Manage download preferences and storage',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 16),
        Semantics(
          label: 'Download location',
          child: ListTile(
            leading: const Icon(Icons.folder),
            title: Text(localizations?.downloadLocationTitle ??
                localizations?.downloadLocation ??
                'Download Location'),
            subtitle: _downloadFolderPath != null
                ? Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const SizedBox(height: 4),
                      Row(
                        children: [
                          Icon(
                            Icons.folder_outlined,
                            size: 16,
                            color: Theme.of(context)
                                .colorScheme
                                .onSurface
                                .withValues(alpha: 0.6),
                          ),
                          const SizedBox(width: 4),
                          Expanded(
                            child: Text(
                              _downloadFolderPath!,
                              style: Theme.of(context)
                                  .textTheme
                                  .bodySmall
                                  ?.copyWith(
                                    color: Theme.of(context)
                                        .colorScheme
                                        .onSurface
                                        .withValues(alpha: 0.6),
                                  ),
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ],
                      ),
                    ],
                  )
                : Text(
                    localizations?.defaultDownloadFolder ?? 'Default folder',
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          color: Theme.of(context)
                              .colorScheme
                              .onSurface
                              .withValues(alpha: 0.6),
                        ),
                  ),
            trailing: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (Platform.isAndroid)
                  Semantics(
                    button: true,
                    label: 'Show folder selection instructions',
                    child: IconButton(
                      icon: const Icon(Icons.help_outline),
                      onPressed: () =>
                          _showFolderSelectionInstructions(context),
                      tooltip: 'Show instructions',
                    ),
                  ),
                Semantics(
                  button: true,
                  label: 'Change download folder',
                  child: IconButton(
                    icon: const Icon(Icons.edit),
                    onPressed: () => _selectDownloadFolder(context),
                    tooltip: 'Change folder',
                  ),
                ),
              ],
            ),
          ),
        ),
        ListTile(
          leading: const Icon(Icons.wifi),
          title: Text(localizations?.wifiOnlyDownloadsTitle ??
              localizations?.wifiOnlyDownloads ??
              'Wi-Fi Only Downloads'),
          subtitle: Text(
            _wifiOnlyDownloads
                ? 'Downloads will only start when connected to Wi-Fi'
                : 'Downloads can start on any network connection',
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: Theme.of(context)
                      .colorScheme
                      .onSurface
                      .withValues(alpha: 0.6),
                ),
          ),
          trailing: Semantics(
            label: 'Wi-Fi only downloads toggle',
            child: Switch(
              value: _wifiOnlyDownloads,
              onChanged: _saveWifiOnlySetting,
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildCacheSection(BuildContext context) {
    final loc = AppLocalizations.of(context);
    return FutureBuilder<Map<String, dynamic>>(
      future: _loadCacheStats(),
      builder: (context, snapshot) {
        final stats = snapshot.data;
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              loc?.cacheStatistics ?? 'Cache Statistics',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 8),
            if (stats == null)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 8.0),
                child: LinearProgressIndicator(),
              )
            else
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _buildStatRow('Total entries', '${stats['total_entries']}'),
                  _buildStatRow(
                      'Search cache', '${stats['search_cache_size']} entries'),
                  _buildStatRow(
                      'Topic cache', '${stats['topic_cache_size']} entries'),
                  _buildStatRow('Memory usage', '${stats['memory_usage']}'),
                ],
              ),
            const SizedBox(height: 12),
            Wrap(spacing: 12, runSpacing: 8, children: [
              ElevatedButton.icon(
                onPressed: () async {
                  final messenger = ScaffoldMessenger.of(context);
                  await _clearExpiredCache();
                  if (mounted) setState(() {});
                  if (mounted) {
                    messenger.showSnackBar(
                      SnackBar(
                          content: Text(loc?.cacheClearedSuccessfullyMessage ??
                              'Cache cleared successfully')),
                    );
                  }
                },
                icon: const Icon(Icons.auto_delete),
                label: Text(
                    AppLocalizations.of(context)?.clearExpiredCacheButton ??
                        'Clear Expired Cache'),
              ),
              OutlinedButton.icon(
                onPressed: () async {
                  final messenger = ScaffoldMessenger.of(context);
                  await _clearAllCache();
                  if (mounted) setState(() {});
                  if (mounted) {
                    messenger.showSnackBar(
                      SnackBar(
                          content: Text(loc?.cacheClearedSuccessfullyMessage ??
                              'Cache cleared successfully')),
                    );
                  }
                },
                icon: const Icon(Icons.delete_forever),
                label: Text(loc?.clearAllCacheButton ?? 'Clear All Cache'),
              ),
            ]),
          ],
        );
      },
    );
  }

  Future<Map<String, dynamic>> _loadCacheStats() async {
    final db = AppDatabase().database;
    final cache = RuTrackerCacheService();
    await cache.initialize(db);
    return cache.getStatistics();
  }

  Future<void> _clearExpiredCache() async {
    final db = AppDatabase().database;
    final cache = RuTrackerCacheService();
    await cache.initialize(db);
    await cache.clearExpired();
  }

  Future<void> _clearAllCache() async {
    final db = AppDatabase().database;
    final cache = RuTrackerCacheService();
    await cache.initialize(db);
    await cache.clearSearchResultsCache();
    await cache.clearAllTopicDetailsCache();
  }

  Widget _buildStatRow(String label, String value) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 2.0),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              label,
              style: const TextStyle(fontWeight: FontWeight.w500),
            ),
            Text(
              value,
              style: TextStyle(
                color: Colors.grey.shade600,
                fontFamily: 'monospace',
              ),
            ),
          ],
        ),
      );

  Widget _buildPermissionsSection(BuildContext context) =>
      FutureBuilder<Map<String, bool>>(
        key: ValueKey<int>(_permissionStatusKey),
        future: _getPermissionStatus(),
        builder: (context, snapshot) {
          final permissions = snapshot.data ?? {};
          final hasStorage = permissions['storage'] ?? false;
          final hasNotification = permissions['notification'] ?? false;
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
                title: AppLocalizations.of(context)?.storagePermissionName ??
                    'Storage',
                description: AppLocalizations.of(context)
                        ?.storagePermissionDescription ??
                    'Save audiobook files and cache data',
                isGranted: hasStorage,
                onTap: _requestStoragePermission,
              ),
              const SizedBox(height: 8),
              _buildPermissionRow(
                icon: Icons.notifications,
                title:
                    AppLocalizations.of(context)?.notificationsPermissionName ??
                        'Notifications',
                description: AppLocalizations.of(context)
                        ?.notificationsPermissionDescription ??
                    'Show playback controls and updates',
                isGranted: hasNotification,
                onTap: _requestNotificationPermission,
              ),
              const SizedBox(height: 16),
              if (!allGranted)
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton.icon(
                    onPressed: _requestAllPermissions,
                    icon: const Icon(Icons.security),
                    label: Text(AppLocalizations.of(context)
                            ?.grantAllPermissionsButton ??
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
        },
      );

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
    final shouldRequest = await _showPermissionExplanationDialog(
      context,
      title: AppLocalizations.of(context)?.storagePermissionName ??
          'Storage Permission',
      message: AppLocalizations.of(context)?.storagePermissionDescription ??
          'JaBook needs storage permission to save audiobook files and cache data. '
              'This allows you to download and play audiobooks offline.',
    );

    if (!shouldRequest) return;

    // Request permission
    final granted = await _permissionService.requestStoragePermission();

    if (!mounted) return;

    if (granted) {
      setState(() {});
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
      // Show dialog to open settings
      await _showOpenSettingsDialog(
        context,
        title: AppLocalizations.of(context)?.storagePermissionName ??
            'Storage Permission',
        message: AppLocalizations.of(context)?.permissionDeniedMessage ??
            'Storage permission was denied. Please grant it in app settings to use this feature.',
      );
      // Force FutureBuilder to rebuild even if denied
      setState(() {
        _permissionStatusKey++;
      });
    }
  }

  Future<void> _requestNotificationPermission() async {
    if (!mounted) return;

    // Show explanation dialog first
    final shouldRequest = await _showPermissionExplanationDialog(
      context,
      title: AppLocalizations.of(context)?.notificationsPermissionName ??
          'Notification Permission',
      message: AppLocalizations.of(context)
              ?.notificationsPermissionDescription ??
          'JaBook needs notification permission to show playback controls and updates. '
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
      await _showOpenSettingsDialog(
        context,
        title: AppLocalizations.of(context)?.notificationsPermissionName ??
            'Notification Permission',
        message: AppLocalizations.of(context)?.permissionDeniedMessage ??
            'Notification permission was denied. Please grant it in app settings to use this feature.',
      );
      // Force FutureBuilder to rebuild even if denied
      setState(() {
        _permissionStatusKey++;
      });
    }
  }

  /// Shows a dialog explaining why a permission is needed.
  ///
  /// Returns `true` if user wants to proceed, `false` otherwise.
  Future<bool> _showPermissionExplanationDialog(
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
  Future<void> _showOpenSettingsDialog(
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
            onPressed: () => Navigator.pop(context),
            child: Text(AppLocalizations.of(context)?.cancel ?? 'Cancel'),
          ),
          ElevatedButton(
            onPressed: () {
              Navigator.pop(context, true);
              _permissionService.openAppSettings();
            },
            child: Text(AppLocalizations.of(context)?.openSettingsButton ??
                'Open Settings'),
          ),
        ],
      ),
    );

    if ((result ?? false) && mounted) {
      // Wait a bit for user to potentially grant permission
      await Future.delayed(const Duration(seconds: 1));
      setState(() {});
    }
  }

  Future<void> _requestAllPermissions() async {
    final results = await _permissionService.requestEssentialPermissions();
    final grantedCount = results.values.where((e) => e).length;
    if (mounted) {
      setState(() {});
      final loc = AppLocalizations.of(context);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(loc?.capabilitiesStatus(grantedCount, results.length) ??
              'Capabilities: $grantedCount/${results.length}'),
        ),
      );
    }
  }

  Widget _buildBackgroundCompatibilitySection(BuildContext context) {
    final localizations = AppLocalizations.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          localizations?.backgroundWorkTitle ?? 'Background Work',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 8),
        Text(
          localizations?.backgroundCompatibilityBannerMessage ??
              'To ensure stable background operation, you may need to configure device settings.',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 16),
        Semantics(
          button: true,
          label: 'Open background compatibility settings',
          child: ListTile(
            leading: const Icon(Icons.phone_android),
            title: Text(localizations?.compatibilityDiagnosticsTitle ??
                'Compatibility & Diagnostics'),
            subtitle: Text(
              localizations?.compatibilityDiagnosticsSubtitle ??
                  'Compatibility check and manufacturer settings configuration',
            ),
            trailing: const Icon(Icons.arrow_forward_ios),
            onTap: () {
              Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (context) => const BackgroundCompatibilityScreen(),
                ),
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _buildAboutSection(BuildContext context) {
    final localizations = AppLocalizations.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          localizations?.aboutTitle ?? 'About',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 8),
        Text(
          localizations?.aboutSectionDescription ?? 'App information and links',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 16),
        Semantics(
          button: true,
          label: localizations?.aboutTitle ?? 'About app',
          child: ListTile(
            leading: const Icon(Icons.info_outline),
            title: Text(localizations?.aboutTitle ?? 'About'),
            subtitle: Text(
              localizations?.aboutSectionSubtitle ??
                  'Version, license, and developer information',
            ),
            trailing: const Icon(Icons.arrow_forward_ios),
            onTap: () {
              context.push('/settings/about');
            },
          ),
        ),
      ],
    );
  }

  Widget _buildBackupSection(BuildContext context) => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            AppLocalizations.of(context)?.backupRestoreTitle ??
                'Backup & Restore',
            style: Theme.of(context).textTheme.titleLarge,
          ),
          const SizedBox(height: 8),
          Text(
            AppLocalizations.of(context)?.backupRestoreDescription ??
                'Export and import your data (favorites, history, metadata)',
            style: Theme.of(context).textTheme.bodySmall,
          ),
          const SizedBox(height: 16),
          ListTile(
            leading: const Icon(Icons.file_download),
            title: Text(AppLocalizations.of(context)?.exportDataButton ??
                'Export Data'),
            subtitle: Text(AppLocalizations.of(context)?.exportDataSubtitle ??
                'Save all your data to a backup file'),
            trailing: const Icon(Icons.arrow_forward_ios),
            onTap: () => _exportData(context),
          ),
          ListTile(
            leading: const Icon(Icons.file_upload),
            title: Text(AppLocalizations.of(context)?.importDataButton ??
                'Import Data'),
            subtitle: Text(AppLocalizations.of(context)?.importDataSubtitle ??
                'Restore data from a backup file'),
            trailing: const Icon(Icons.arrow_forward_ios),
            onTap: () => _importData(context),
          ),
        ],
      );

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
      final appDatabase = AppDatabase();
      await appDatabase.initialize();
      final backupService = BackupService(appDatabase.database);

      // Export to file
      final filePath = await backupService.exportToFile();

      // Share the file
      final file = File(filePath);
      if (await file.exists()) {
        // ignore: deprecated_member_use
        await Share.shareXFiles(
          [XFile(filePath)],
          subject: 'JaBook Backup',
          text: 'JaBook data backup',
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

        final appDatabase = AppDatabase();
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

  /// Shows the folder selection instructions dialog.
  Future<void> _showFolderSelectionInstructions(BuildContext context) async {
    if (!mounted) return;

    final localizations = AppLocalizations.of(context);
    final dialogTitle =
        localizations?.selectFolderDialogTitle ?? 'Select Download Folder';
    final dialogMessage = localizations?.selectFolderDialogMessage ??
        'To select a download folder:\n\n'
            '1. Navigate to the desired folder in the file manager\n'
            '2. Tap "Use this folder" button in the top right corner\n\n'
            'The selected folder will be used to save downloaded audiobooks.';
    final cancelText = localizations?.cancel ?? 'Cancel';

    if (!context.mounted) return;
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Row(
          children: [
            Icon(
              Icons.info_outline,
              color: Theme.of(dialogContext).colorScheme.primary,
            ),
            const SizedBox(width: 8),
            Expanded(child: Text(dialogTitle)),
          ],
        ),
        content: SingleChildScrollView(
          child: Text(
            dialogMessage,
            style: Theme.of(dialogContext).textTheme.bodyMedium,
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(),
            child: Text(cancelText),
          ),
          ElevatedButton.icon(
            onPressed: () {
              Navigator.of(dialogContext).pop();
              _selectDownloadFolder(context);
            },
            icon: const Icon(Icons.folder_open),
            label: const Text('Select Folder'),
          ),
        ],
      ),
    );
  }

  Future<void> _selectDownloadFolder(BuildContext context) async {
    if (!mounted) return;

    // Save context and localizations before any async operations
    final savedContext = context;
    final localizations = AppLocalizations.of(savedContext);
    final messenger = ScaffoldMessenger.of(savedContext);

    // Check Android version to show instruction dialog for Android 13+
    if (Platform.isAndroid) {
      final androidInfo = await DeviceInfoPlugin().androidInfo;
      final sdkInt = androidInfo.version.sdkInt;

      if (sdkInt >= 33) {
        if (!mounted) return;
        // Show instruction dialog for Android 13+
        // Use savedContext which was captured before async operations
        final dialogTitle =
            localizations?.selectFolderDialogTitle ?? 'Select Download Folder';
        final dialogMessage = localizations?.selectFolderDialogMessage ??
            'To select a download folder:\n\n'
                '1. Navigate to the desired folder in the file manager\n'
                '2. Tap "Use this folder" button in the top right corner\n\n'
                'The selected folder will be used to save downloaded audiobooks.';
        final cancelText = localizations?.cancel ?? 'Cancel';

        // Check if context is still mounted before using it
        if (!savedContext.mounted) return;
        final shouldProceed = await showDialog<bool>(
          context: savedContext,
          builder: (dialogContext) => AlertDialog(
            title: Row(
              children: [
                Icon(
                  Icons.info_outline,
                  color: Theme.of(dialogContext).colorScheme.primary,
                ),
                const SizedBox(width: 8),
                Expanded(child: Text(dialogTitle)),
              ],
            ),
            content: SingleChildScrollView(
              child: Text(
                dialogMessage,
                style: Theme.of(dialogContext).textTheme.bodyMedium,
              ),
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(dialogContext).pop(false),
                child: Text(cancelText),
              ),
              ElevatedButton.icon(
                onPressed: () => Navigator.of(dialogContext).pop(true),
                icon: const Icon(Icons.folder_open),
                label: const Text('Continue'),
              ),
            ],
          ),
        );

        if (shouldProceed != true) {
          return; // User cancelled
        }
      }
    }

    // Open folder picker
    try {
      final selectedPath = await file_picker_utils.pickDirectory();

      if (selectedPath != null) {
        // Validate folder accessibility
        try {
          final dir = Directory(selectedPath);
          if (!await dir.exists()) {
            if (mounted) {
              messenger.showSnackBar(
                const SnackBar(
                  content: Text(
                    'Selected folder is not accessible. Please try again.',
                  ),
                  backgroundColor: Colors.orange,
                  duration: Duration(seconds: 3),
                ),
              );
            }
            return;
          }
        } on Exception {
          // If we can't check, still try to save - might be SAF URI
        }

        // Save selected path using StoragePathUtils
        final storageUtils = StoragePathUtils();
        await storageUtils.setDownloadFolderPath(selectedPath);

        if (mounted) {
          setState(() {
            _downloadFolderPath = selectedPath;
          });
          messenger.showSnackBar(
            SnackBar(
              content: Row(
                children: [
                  const Icon(Icons.check_circle, color: Colors.white),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(
                          localizations?.folderSelectedSuccessMessage ??
                              'Download folder selected successfully',
                          style: const TextStyle(fontWeight: FontWeight.bold),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          selectedPath,
                          style: const TextStyle(fontSize: 12),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              backgroundColor: Colors.green,
            ),
          );
        }
      } else {
        // User cancelled folder selection
        if (mounted) {
          messenger.showSnackBar(
            SnackBar(
              content: Text(localizations?.folderSelectionCancelledMessage ??
                  'Folder selection cancelled'),
              duration: const Duration(seconds: 1),
            ),
          );
        }
      }
    } on Exception catch (e) {
      if (mounted) {
        messenger.showSnackBar(
          SnackBar(
            content: Row(
              children: [
                const Icon(Icons.error, color: Colors.white),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Error selecting folder: ${e.toString()}',
                  ),
                ),
              ],
            ),
            backgroundColor: Colors.red,
            duration: const Duration(seconds: 3),
          ),
        );
      }
    }
  }

  Widget _buildLibraryFolderSection(BuildContext context) {
    final localizations = AppLocalizations.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          localizations?.libraryFolderTitle ?? 'Library Folders',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 8),
        Text(
          localizations?.libraryFolderDescription ??
              'Select folders where your audiobooks are stored',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 16),
        // Current library folder
        Semantics(
          label: 'Library folder location',
          child: ListTile(
            leading: const Icon(Icons.folder),
            title: Text(localizations?.libraryFolderTitle ?? 'Library Folder'),
            subtitle: _libraryFolderPath != null
                ? Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const SizedBox(height: 4),
                      Row(
                        children: [
                          Icon(
                            Icons.folder_outlined,
                            size: 16,
                            color: Theme.of(context)
                                .colorScheme
                                .onSurface
                                .withValues(alpha: 0.6),
                          ),
                          const SizedBox(width: 4),
                          Expanded(
                            child: Text(
                              _libraryFolderPath!,
                              style: Theme.of(context)
                                  .textTheme
                                  .bodySmall
                                  ?.copyWith(
                                    color: Theme.of(context)
                                        .colorScheme
                                        .onSurface
                                        .withValues(alpha: 0.6),
                                  ),
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ],
                      ),
                    ],
                  )
                : Text(
                    localizations?.defaultLibraryFolder ?? 'Default folder',
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          color: Theme.of(context)
                              .colorScheme
                              .onSurface
                              .withValues(alpha: 0.6),
                        ),
                  ),
            trailing: Semantics(
              button: true,
              label: 'Change library folder',
              child: IconButton(
                icon: const Icon(Icons.edit),
                onPressed: () => _selectLibraryFolder(context),
                tooltip: 'Change folder',
              ),
            ),
          ),
        ),
        // Add library folder button
        ListTile(
          leading: const Icon(Icons.add),
          title: Text(
              localizations?.addLibraryFolderTitle ?? 'Add Library Folder'),
          subtitle: Text(
            localizations?.addLibraryFolderSubtitle ??
                'Add an additional folder to scan for audiobooks',
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: Theme.of(context)
                      .colorScheme
                      .onSurface
                      .withValues(alpha: 0.6),
                ),
          ),
          trailing: const Icon(Icons.arrow_forward_ios),
          onTap: () => _addLibraryFolder(context),
        ),
        // List of all library folders
        if (_libraryFolders.length > 1)
          ExpansionTile(
            leading: const Icon(Icons.folder),
            title: Text(
              localizations?.allLibraryFoldersTitle ??
                  'All Library Folders (${_libraryFolders.length})',
            ),
            children: _libraryFolders.map((folder) {
              final isPrimary = folder == _libraryFolderPath;
              return ListTile(
                leading: Icon(
                  isPrimary ? Icons.folder : Icons.folder_outlined,
                  color:
                      isPrimary ? Theme.of(context).colorScheme.primary : null,
                ),
                title: Text(
                  folder,
                  style: TextStyle(
                    fontWeight: isPrimary ? FontWeight.bold : FontWeight.normal,
                  ),
                ),
                subtitle: isPrimary
                    ? Text(
                        localizations?.primaryLibraryFolder ?? 'Primary folder',
                        style: Theme.of(context).textTheme.bodySmall,
                      )
                    : null,
                trailing: isPrimary
                    ? null
                    : IconButton(
                        icon: const Icon(Icons.delete),
                        onPressed: () => _removeLibraryFolder(context, folder),
                        tooltip: 'Remove folder',
                      ),
              );
            }).toList(),
          ),
      ],
    );
  }

  Future<void> _selectLibraryFolder(BuildContext context) async {
    if (!mounted) return;

    final savedContext = context;
    final localizations = AppLocalizations.of(savedContext);
    final messenger = ScaffoldMessenger.of(savedContext);

    // Check Android version to show instruction dialog for Android 13+
    if (Platform.isAndroid) {
      final androidInfo = await DeviceInfoPlugin().androidInfo;
      final sdkInt = androidInfo.version.sdkInt;

      if (sdkInt >= 33) {
        if (!mounted) return;
        final dialogContext = context;
        final shouldProceed = await showDialog<bool>(
          // ignore: use_build_context_synchronously
          context: dialogContext,
          builder: (dialogContext) => AlertDialog(
            title: Row(
              children: [
                Icon(
                  Icons.info_outline,
                  color: Theme.of(dialogContext).colorScheme.primary,
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    localizations?.selectLibraryFolderDialogTitle ??
                        'Select Library Folder',
                  ),
                ),
              ],
            ),
            content: SingleChildScrollView(
              child: Text(
                localizations?.selectLibraryFolderDialogMessage ??
                    'To select a library folder:\n\n'
                        '1. Navigate to the desired folder in the file manager\n'
                        '2. Tap "Use this folder" button in the top right corner\n\n'
                        'The selected folder will be used to scan for audiobooks.',
              ),
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(dialogContext).pop(false),
                child: Text(localizations?.cancel ?? 'Cancel'),
              ),
              ElevatedButton.icon(
                onPressed: () => Navigator.of(dialogContext).pop(true),
                icon: const Icon(Icons.folder_open),
                label: const Text('Select Folder'),
              ),
            ],
          ),
        );

        if (!mounted) return;
        if (shouldProceed != true) {
          return; // User cancelled
        }
      }
    }

    // Open folder picker
    try {
      final selectedPath = await file_picker_utils.pickDirectory();

      if (selectedPath != null) {
        // Validate folder accessibility
        try {
          final dir = Directory(selectedPath);
          if (!await dir.exists()) {
            if (mounted) {
              messenger.showSnackBar(
                const SnackBar(
                  content: Text(
                    'Selected folder is not accessible. Please try again.',
                  ),
                  backgroundColor: Colors.orange,
                  duration: Duration(seconds: 3),
                ),
              );
            }
            return;
          }
        } on Exception {
          // If we can't check, still try to save - might be SAF URI
        }

        // Check if we should migrate files
        final oldPath = _libraryFolderPath;
        if (oldPath != null && oldPath != selectedPath) {
          if (!mounted) return;
          final dialogContext = context;
          final shouldMigrate = await showDialog<bool>(
            // ignore: use_build_context_synchronously
            context: dialogContext,
            builder: (dialogContext) => AlertDialog(
              title: Text(
                localizations?.migrateLibraryFolderTitle ?? 'Migrate Files?',
              ),
              content: Text(
                localizations?.migrateLibraryFolderMessage ??
                    'Do you want to move your existing audiobooks from the old folder to the new folder?',
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.of(dialogContext).pop(false),
                  child: Text(localizations?.cancel ?? 'Cancel'),
                ),
                TextButton(
                  onPressed: () => Navigator.of(dialogContext).pop(false),
                  child: Text(localizations?.no ?? 'No'),
                ),
                ElevatedButton(
                  onPressed: () => Navigator.of(dialogContext).pop(true),
                  child: Text(localizations?.yes ?? 'Yes'),
                ),
              ],
            ),
          );

          if (!mounted) return;
          if (shouldMigrate ?? false) {
            // Perform migration
            final migrationContext = context;
            await _migrateLibraryFolder(
                // ignore: use_build_context_synchronously
                migrationContext,
                oldPath,
                selectedPath);
          }
        }

        // Save selected path
        final storageUtils = StoragePathUtils();
        await storageUtils.setLibraryFolderPath(selectedPath);
        await _loadLibraryFolders();

        if (mounted) {
          messenger.showSnackBar(
            SnackBar(
              content: Row(
                children: [
                  const Icon(Icons.check_circle, color: Colors.white),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(
                          localizations?.libraryFolderSelectedSuccessMessage ??
                              'Library folder selected successfully',
                          style: const TextStyle(fontWeight: FontWeight.bold),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          selectedPath,
                          style: const TextStyle(fontSize: 12),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              backgroundColor: Colors.green,
            ),
          );
        }
      } else {
        // User cancelled folder selection
        if (mounted) {
          messenger.showSnackBar(
            SnackBar(
              content: Text(localizations?.folderSelectionCancelledMessage ??
                  'Folder selection cancelled'),
              duration: const Duration(seconds: 1),
            ),
          );
        }
      }
    } on Exception catch (e) {
      if (mounted) {
        messenger.showSnackBar(
          SnackBar(
            content: Row(
              children: [
                const Icon(Icons.error, color: Colors.white),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Error selecting folder: ${e.toString()}',
                  ),
                ),
              ],
            ),
            backgroundColor: Colors.red,
            duration: const Duration(seconds: 3),
          ),
        );
      }
    }
  }

  Future<void> _addLibraryFolder(BuildContext context) async {
    if (!mounted) return;

    final savedContext = context;
    final localizations = AppLocalizations.of(savedContext);
    final messenger = ScaffoldMessenger.of(savedContext);

    try {
      final selectedPath = await file_picker_utils.pickDirectory();

      if (selectedPath != null) {
        // Validate folder accessibility
        try {
          final dir = Directory(selectedPath);
          if (!await dir.exists()) {
            if (mounted) {
              messenger.showSnackBar(
                const SnackBar(
                  content: Text(
                    'Selected folder is not accessible. Please try again.',
                  ),
                  backgroundColor: Colors.orange,
                  duration: Duration(seconds: 3),
                ),
              );
            }
            return;
          }
        } on Exception {
          // If we can't check, still try to save - might be SAF URI
        }

        // Check if folder already exists
        if (_libraryFolders.contains(selectedPath)) {
          if (mounted) {
            messenger.showSnackBar(
              SnackBar(
                content: Text(
                  localizations?.libraryFolderAlreadyExistsMessage ??
                      'This folder is already in the library folders list',
                ),
                backgroundColor: Colors.orange,
                duration: const Duration(seconds: 2),
              ),
            );
          }
          return;
        }

        // Add folder
        final storageUtils = StoragePathUtils();
        final added = await storageUtils.addLibraryFolder(selectedPath);
        await _loadLibraryFolders();

        if (mounted) {
          if (added) {
            messenger.showSnackBar(
              SnackBar(
                content: Text(
                  localizations?.libraryFolderAddedSuccessMessage ??
                      'Library folder added successfully',
                ),
                backgroundColor: Colors.green,
              ),
            );
          } else {
            messenger.showSnackBar(
              SnackBar(
                content: Text(
                  localizations?.libraryFolderAlreadyExistsMessage ??
                      'This folder is already in the library folders list',
                ),
                backgroundColor: Colors.orange,
              ),
            );
          }
        }
      }
    } on Exception catch (e) {
      if (mounted) {
        messenger.showSnackBar(
          SnackBar(
            content: Text('Error adding folder: ${e.toString()}'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  Future<void> _removeLibraryFolder(BuildContext context, String folder) async {
    if (!mounted) return;

    final localizations = AppLocalizations.of(context);
    final messenger = ScaffoldMessenger.of(context);

    // Confirm removal
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(
          localizations?.removeLibraryFolderTitle ?? 'Remove Folder?',
        ),
        content: Text(
          localizations?.removeLibraryFolderMessage ??
              'Are you sure you want to remove this folder from the library? '
                  'This will not delete the files, only stop scanning this folder.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: Text(localizations?.cancel ?? 'Cancel'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            style: ElevatedButton.styleFrom(
              backgroundColor: Colors.red,
              foregroundColor: Colors.white,
            ),
            child: Text(localizations?.remove ?? 'Remove'),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    // Remove folder
    final storageUtils = StoragePathUtils();
    final removed = await storageUtils.removeLibraryFolder(folder);
    await _loadLibraryFolders();

    if (mounted) {
      if (removed) {
        messenger.showSnackBar(
          SnackBar(
            content: Text(
              localizations?.libraryFolderRemovedSuccessMessage ??
                  'Library folder removed successfully',
            ),
            backgroundColor: Colors.green,
          ),
        );
      } else {
        messenger.showSnackBar(
          SnackBar(
            content: Text(
              localizations?.libraryFolderRemoveFailedMessage ??
                  'Failed to remove library folder',
            ),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  Future<void> _migrateLibraryFolder(
    BuildContext context,
    String oldPath,
    String newPath,
  ) async {
    if (!mounted) return;

    final localizations = AppLocalizations.of(context);

    // Show progress dialog
    if (!mounted) return;
    final dialogContext = context;
    final progressDialog = showDialog(
      context: dialogContext,
      barrierDismissible: false,
      builder: (dialogBuilderContext) => AlertDialog(
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const CircularProgressIndicator(),
            const SizedBox(height: 16),
            Text(
              localizations?.migratingLibraryFolderMessage ??
                  'Migrating files...',
            ),
          ],
        ),
      ),
    );
    unawaited(progressDialog);

    try {
      final migrationService = LibraryMigrationService();
      final result = await migrationService.migrateLibrary(
        oldPath: oldPath,
        newPath: newPath,
      );

      if (!mounted) return;
      final navContext = context;
      // ignore: use_build_context_synchronously
      Navigator.of(navContext).pop(); // Close progress dialog

      if (!mounted) return;
      final messengerContext = context;
      if (result.success) {
        // ignore: use_build_context_synchronously
        ScaffoldMessenger.of(messengerContext).showSnackBar(
          SnackBar(
            content: Text(
              localizations?.migrationCompletedSuccessMessage ??
                  'Migration completed successfully. ${result.filesMoved} files moved.',
            ),
            backgroundColor: Colors.green,
            duration: const Duration(seconds: 3),
          ),
        );
      } else {
        if (!mounted) return;
        final messengerContextForError = context;
        // ignore: use_build_context_synchronously
        ScaffoldMessenger.of(messengerContextForError).showSnackBar(
          SnackBar(
            content: Text(
              localizations?.migrationFailedMessage ??
                  'Migration failed: ${result.error ?? "Unknown error"}',
            ),
            backgroundColor: Colors.red,
            duration: const Duration(seconds: 3),
          ),
        );
      }
    } on Exception catch (e) {
      if (!mounted) return;
      final navContextForError = context;
      // ignore: use_build_context_synchronously
      Navigator.of(navContextForError).pop(); // Close progress dialog
      if (!mounted) return;
      final messengerContextForException = context;
      // ignore: use_build_context_synchronously
      ScaffoldMessenger.of(messengerContextForException).showSnackBar(
        SnackBar(
          content: Text(
            'Migration error: ${e.toString()}',
          ),
          backgroundColor: Colors.red,
        ),
      );
    }
  }
}
