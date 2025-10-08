import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/core/config/language_manager.dart';
import 'package:jabook/core/config/language_provider.dart';
import 'package:jabook/features/settings/presentation/screens/mirror_settings_screen.dart';
import 'package:jabook/l10n/app_localizations.dart';

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
          localizations?.languageDescriptionText ?? 'Choose your preferred language for the app interface',
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

  Widget _buildThemeSection(BuildContext context) {
    final localizations = AppLocalizations.of(context);
    
    return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            localizations?.themeTitleText ?? 'Theme',
            style: Theme.of(context).textTheme.titleLarge,
          ),
          const SizedBox(height: 8),
          Text(
            localizations?.themeDescriptionText ?? 'Customize the appearance of the app',
            style: Theme.of(context).textTheme.bodySmall,
          ),
          const SizedBox(height: 16),
          ListTile(
            leading: const Icon(Icons.color_lens),
            title: Text(localizations?.darkModeText ?? 'Dark Mode'),
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
            title: Text(localizations?.highContrastText ?? 'High Contrast'),
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
            localizations?.audioTitleText ?? 'Audio',
            style: Theme.of(context).textTheme.titleLarge,
          ),
          const SizedBox(height: 8),
          Text(
            localizations?.audioDescriptionText ?? 'Configure audio playback settings',
            style: Theme.of(context).textTheme.bodySmall,
          ),
          const SizedBox(height: 16),
          Semantics(
            button: true,
            label: 'Set playback speed',
            child: ListTile(
              leading: const Icon(Icons.speed),
              title: Text(localizations?.playbackSpeedText ?? 'Playback Speed'),
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
              title: Text(localizations?.skipDurationText ?? 'Skip Duration'),
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
            localizations?.downloadsTitleText ?? 'Downloads',
            style: Theme.of(context).textTheme.titleLarge,
          ),
          const SizedBox(height: 8),
          Text(
            localizations?.downloadsDescriptionText ?? 'Manage download preferences and storage',
            style: Theme.of(context).textTheme.bodySmall,
          ),
          const SizedBox(height: 16),
          Semantics(
            button: true,
            label: 'Set download location',
            child: ListTile(
              leading: const Icon(Icons.storage),
              title: Text(localizations?.downloadLocationText ?? 'Download Location'),
              subtitle: const Text('/storage/emulated/0/Download'),
              onTap: () {
                // TODO: Implement download location selection
              },
            ),
          ),
          ListTile(
            leading: const Icon(Icons.wifi),
            title: Text(localizations?.wifiOnlyDownloadsText ?? 'Wi-Fi Only Downloads'),
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
}