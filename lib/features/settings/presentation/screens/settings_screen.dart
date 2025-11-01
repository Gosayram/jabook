import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/core/backup/backup_service.dart';
import 'package:jabook/core/cache/rutracker_cache_service.dart';
import 'package:jabook/core/config/language_manager.dart';
import 'package:jabook/core/config/language_provider.dart';
import 'package:jabook/core/metadata/audiobook_metadata_service.dart';
import 'package:jabook/core/metadata/metadata_sync_scheduler.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/permissions/permission_service_v2.dart';
import 'package:jabook/data/db/app_database.dart';
import 'package:jabook/features/settings/presentation/screens/mirror_settings_screen.dart';
import 'package:jabook/features/webview/secure_rutracker_webview.dart';
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
  final PermissionServiceV2 _permissionService = PermissionServiceV2();
  String _selectedLanguage = 'system';

  @override
  void initState() {
    super.initState();
    _loadLanguagePreference();
  }

  Future<void> _loadLanguagePreference() async {
    final languageCode = await _languageManager.getCurrentLanguage();
    setState(() {
      _selectedLanguage = languageCode;
    });
  }

  Future<void> _changeLanguage(String languageCode, WidgetRef ref) async {
    final languageNotifier = ref.read(languageProvider.notifier);
    await languageNotifier.changeLanguage(languageCode);
    setState(() {
      _selectedLanguage = languageCode;
    });

    // Show confirmation message
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'Language changed to ${_languageManager.getLanguageName(languageCode)}',
          ),
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
            label: 'Language settings',
            child: _buildLanguageSection(context),
          ),

          const SizedBox(height: 24),

          // Mirror Settings Section
          Semantics(
            container: true,
            label: 'Mirror and source settings',
            child: _buildMirrorSection(context),
          ),

          const SizedBox(height: 24),

          // RuTracker Session Section
          Semantics(
            container: true,
            label: 'RuTracker session management',
            child: _buildRutrackerSessionSection(context),
          ),

          const SizedBox(height: 24),

          // Metadata Section
          Semantics(
            container: true,
            label: 'Metadata management',
            child: _buildMetadataSection(context),
          ),

          const SizedBox(height: 24),

          // Theme Settings Section
          Semantics(
            container: true,
            label: 'Theme settings',
            child: _buildThemeSection(context),
          ),

          const SizedBox(height: 24),

          // Audio Settings Section
          Semantics(
            container: true,
            label: 'Audio playback settings',
            child: _buildAudioSection(context),
          ),

          const SizedBox(height: 24),

          // Download Settings Section
          Semantics(
            container: true,
            label: 'Download settings',
            child: _buildDownloadSection(context),
          ),

          const SizedBox(height: 24),

          // Cache Settings Section
          Semantics(
            container: true,
            label: 'Cache settings',
            child: _buildCacheSection(context),
          ),

          const SizedBox(height: 24),

          // Permissions Section
          Semantics(
            container: true,
            label: 'App permissions',
            child: _buildPermissionsSection(context),
          ),

          const SizedBox(height: 24),

          // Backup & Restore Section
          Semantics(
            container: true,
            label: 'Backup and restore',
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

  Widget _buildLanguageTile(Map<String, String> language) => ListTile(
        leading: Text(
          language['flag']!,
          style: const TextStyle(fontSize: 24),
        ),
        title: Text(language['name']!),
        trailing: _selectedLanguage == language['code']
            ? const Icon(Icons.check, color: Colors.blue)
            : null,
        onTap: () => _changeLanguage(language['code']!, ref),
      );

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
            subtitle: const Text('Configure and test RuTracker mirrors'),
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
            'RuTracker',
            style: Theme.of(context).textTheme.titleLarge,
          ),
          const SizedBox(height: 8),
          Text(
            'Управление сессией RuTracker (cookie)',
            style: Theme.of(context).textTheme.bodySmall,
          ),
          const SizedBox(height: 16),
          ListTile(
            leading: const Icon(Icons.login),
            title: const Text('Войти в RuTracker через WebView'),
            subtitle: const Text(
                'Пройти Cloudflare/капчу и сохранить cookie для клиента'),
            onTap: () async {
              final messenger = ScaffoldMessenger.of(context);
              final cookieStr = await Navigator.push<String>(
                context,
                MaterialPageRoute(
                    builder: (_) => const SecureRutrackerWebView()),
              );
              if (cookieStr != null && cookieStr.isNotEmpty) {
                final prefs = await SharedPreferences.getInstance();
                // Optionally persist raw cookie string (for Android channel flow)
                await prefs.setString('rutracker_cookie_string', cookieStr);
                // Try sync into Dio cookie jar from stored JSON if present
                await DioClient.syncCookiesFromWebView();
                if (mounted) {
                  messenger.showSnackBar(
                    const SnackBar(
                        content: Text('Cookie сохранены для HTTP-клиента')),
                  );
                }
              }
            },
          ),
          ListTile(
            leading: const Icon(Icons.logout),
            title: const Text('Очистить сессию RuTracker (cookie)'),
            subtitle:
                const Text('Удалить сохранённые cookie и выйти из аккаунта'),
            onTap: () async {
              // Clear cookies in Dio and WebView storage
              final messenger = ScaffoldMessenger.of(context);
              await DioClient.clearCookies();
              final prefs = await SharedPreferences.getInstance();
              await prefs.remove('rutracker_cookies_v1');
              await prefs.remove('rutracker_cookie_string');
              if (mounted) {
                messenger.showSnackBar(
                  const SnackBar(content: Text('Сессия RuTracker очищена')),
                );
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
                'Метаданные аудиокниг',
                style: Theme.of(context).textTheme.titleLarge,
              ),
              const SizedBox(height: 8),
              Text(
                'Управление локальной базой метаданных аудиокниг',
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
                    _buildStatRow('Всего записей', '${stats['total'] ?? 0}'),
                    if (stats['last_sync'] != null)
                      _buildStatRow(
                        'Последнее обновление',
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
                  label: Text(
                      isUpdating ? 'Обновление...' : 'Обновить метаданные'),
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

    try {
      // Show updating indicator
      setState(() {});

      final db = AppDatabase().database;
      final metadataService = AudiobookMetadataService(db);
      final scheduler = MetadataSyncScheduler(db, metadataService);

      messenger.showSnackBar(
        const SnackBar(
          content: Text('Начато обновление метаданных...'),
          duration: Duration(seconds: 2),
        ),
      );

      // Run full sync
      final results = await scheduler.runFullSync();

      final total = results.values.fold<int>(0, (sum, count) => sum + count);

      if (mounted) {
        messenger.showSnackBar(
          SnackBar(
            content: Text('Обновление завершено: собрано $total записей'),
            duration: const Duration(seconds: 3),
          ),
        );
        setState(() {});
      }
    } on Exception catch (e) {
      if (mounted) {
        messenger.showSnackBar(
          SnackBar(
            content: Text('Ошибка обновления: $e'),
            backgroundColor: Colors.red,
            duration: const Duration(seconds: 5),
          ),
        );
        setState(() {});
      }
    }
  }

  String _formatDateTime(String? isoString) {
    if (isoString == null) return 'Никогда';
    try {
      final dateTime = DateTime.parse(isoString);
      final now = DateTime.now();
      final diff = now.difference(dateTime);

      if (diff.inDays > 0) {
        return '${diff.inDays} дн. назад';
      } else if (diff.inHours > 0) {
        return '${diff.inHours} ч. назад';
      } else if (diff.inMinutes > 0) {
        return '${diff.inMinutes} мин. назад';
      } else {
        return 'Только что';
      }
    } on Exception {
      return 'Неизвестно';
    }
  }

  Widget _buildThemeSection(BuildContext context) {
    final localizations = AppLocalizations.of(context);

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
        ListTile(
          leading: const Icon(Icons.color_lens),
          title: Text(localizations?.darkMode ?? 'Dark Mode'),
          trailing: Semantics(
            label: 'Dark mode toggle',
            child: Switch(
              value: false,
              onChanged: (value) {
                // TODO: Implement theme switching
              },
            ),
          ),
        ),
        ListTile(
          leading: const Icon(Icons.contrast),
          title: Text(localizations?.highContrast ?? 'High Contrast'),
          trailing: Semantics(
            label: 'High contrast mode toggle',
            child: Switch(
              value: false,
              onChanged: (value) {
                // TODO: Implement high contrast mode
              },
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildAudioSection(BuildContext context) {
    final localizations = AppLocalizations.of(context);

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
        Semantics(
          button: true,
          label: 'Set playback speed',
          child: ListTile(
            leading: const Icon(Icons.speed),
            title: Text(localizations?.playbackSpeedTitle ??
                localizations?.playbackSpeed ??
                'Playback Speed'),
            subtitle: const Text('1.0x'),
            onTap: () {
              // TODO: Implement playback speed selection
            },
          ),
        ),
        Semantics(
          button: true,
          label: 'Set skip duration',
          child: ListTile(
            leading: const Icon(Icons.skip_next),
            title: Text(localizations?.skipDurationTitle ??
                localizations?.skipDuration ??
                'Skip Duration'),
            subtitle: const Text('15 seconds'),
            onTap: () {
              // TODO: Implement skip duration selection
            },
          ),
        ),
      ],
    );
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
          button: true,
          label: 'Set download location',
          child: ListTile(
            leading: const Icon(Icons.storage),
            title: Text(localizations?.downloadLocationTitle ??
                localizations?.downloadLocation ??
                'Download Location'),
            subtitle: const Text('/storage/emulated/0/Download'),
            onTap: () {
              // TODO: Implement download location selection
            },
          ),
        ),
        ListTile(
          leading: const Icon(Icons.wifi),
          title: Text(localizations?.wifiOnlyDownloadsTitle ??
              localizations?.wifiOnlyDownloads ??
              'Wi-Fi Only Downloads'),
          trailing: Semantics(
            label: 'Wi-Fi only downloads toggle',
            child: Switch(
              value: true,
              onChanged: (value) {
                // TODO: Implement Wi-Fi only setting
              },
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
                label: const Text('Clear Expired Cache'),
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
                'App Permissions',
                style: Theme.of(context).textTheme.titleLarge,
              ),
              const SizedBox(height: 8),
              _buildPermissionRow(
                icon: Icons.folder,
                title: 'Storage',
                description: 'Save audiobook files and cache data',
                isGranted: hasStorage,
                onTap: _requestStoragePermission,
              ),
              const SizedBox(height: 8),
              _buildPermissionRow(
                icon: Icons.notifications,
                title: 'Notifications',
                description: 'Show playback controls and updates',
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
                    label: const Text('Grant All Permissions'),
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
      Card(
        child: ListTile(
          leading: Icon(
            icon,
            color: isGranted ? Colors.green : Colors.orange,
          ),
          title: Text(title),
          subtitle: Text(description),
          trailing: isGranted
              ? Icon(Icons.check_circle, color: Colors.green.shade600)
              : Icon(Icons.warning, color: Colors.orange.shade600),
          onTap: onTap,
        ),
      );

  Future<Map<String, bool>> _getPermissionStatus() async {
    final files = await _permissionService.canAccessFiles();
    final notifications = await _permissionService.canShowNotifications();
    return {
      'storage': files,
      'notification': notifications,
    };
  }

  Future<void> _requestStoragePermission() async {
    final granted = await _permissionService.canAccessFiles();
    if (mounted) {
      setState(() {});
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
              granted ? 'File access available' : 'File access unavailable'),
        ),
      );
    }
  }

  Future<void> _requestNotificationPermission() async {
    final granted = await _permissionService.canShowNotifications();
    if (mounted) {
      setState(() {});
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(granted
              ? 'Notifications available'
              : 'Notifications unavailable'),
        ),
      );
    }
  }

  Future<void> _requestAllPermissions() async {
    final results = await _permissionService.requestEssentialPermissions();
    final grantedCount = results.values.where((e) => e).length;
    if (mounted) {
      setState(() {});
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Capabilities: $grantedCount/${results.length}'),
        ),
      );
    }
  }

  Widget _buildBackupSection(BuildContext context) => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Backup & Restore',
            style: Theme.of(context).textTheme.titleLarge,
          ),
          const SizedBox(height: 8),
          Text(
            'Export and import your data (favorites, history, metadata)',
            style: Theme.of(context).textTheme.bodySmall,
          ),
          const SizedBox(height: 16),
          ListTile(
            leading: const Icon(Icons.file_download),
            title: const Text('Export Data'),
            subtitle: const Text('Save all your data to a backup file'),
            trailing: const Icon(Icons.arrow_forward_ios),
            onTap: () => _exportData(context),
          ),
          ListTile(
            leading: const Icon(Icons.file_upload),
            title: const Text('Import Data'),
            subtitle: const Text('Restore data from a backup file'),
            trailing: const Icon(Icons.arrow_forward_ios),
            onTap: () => _importData(context),
          ),
        ],
      );

  Future<void> _exportData(BuildContext context) async {
    if (!mounted) return;
    final messenger = ScaffoldMessenger.of(context)
      ..showSnackBar(
        const SnackBar(
          content: Text('Exporting data...'),
          duration: Duration(seconds: 1),
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
          const SnackBar(
            content: Text('Data exported successfully'),
            backgroundColor: Colors.green,
          ),
        );
      }
    } on Exception catch (e) {
      if (!mounted) return;
      messenger.showSnackBar(
        SnackBar(
          content: Text('Failed to export: ${e.toString()}'),
          backgroundColor: Colors.red,
        ),
      );
    }
  }

  Future<void> _importData(BuildContext context) async {
    if (!mounted) return;
    final messenger = ScaffoldMessenger.of(context);
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
            title: const Text('Import Backup'),
            content: const Text(
              'This will import data from the backup file. Existing data may be merged or replaced. Continue?',
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(dialogContext).pop(false),
                child: const Text('Cancel'),
              ),
              ElevatedButton(
                onPressed: () => Navigator.of(dialogContext).pop(true),
                child: const Text('Import'),
              ),
            ],
          ),
        );

        if (confirmed != true) return;

        if (!mounted) return;
        messenger.showSnackBar(
          const SnackBar(
            content: Text('Importing data...'),
            duration: Duration(seconds: 2),
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
          content: Text('Failed to import: ${e.toString()}'),
          backgroundColor: Colors.red,
        ),
      );
    }
  }
}
