import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/core/config/language_manager.dart';
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

  Future<void> _changeLanguage(String languageCode) async {
    await _languageManager.setLanguage(languageCode);
    setState(() {
      _selectedLanguage = languageCode;
    });
    
    // Show confirmation message
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          'Language changed to ${_languageManager.getLanguageName(languageCode)}',
        ),
        duration: const Duration(seconds: 2),
      ),
    );
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
          _buildLanguageSection(context),
          
          const SizedBox(height: 24),
          
          // Theme Settings Section
          _buildThemeSection(context),
          
          const SizedBox(height: 24),
          
          // Audio Settings Section
          _buildAudioSection(context),
          
          const SizedBox(height: 24),
          
          // Download Settings Section
          _buildDownloadSection(context),
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
          'Choose your preferred language for the app interface',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 16),
        ...languages.map((language) => _buildLanguageTile(language)),
      ],
    );
  }

  Widget _buildLanguageTile(Map<String, String> language) {
    return ListTile(
      leading: Text(
        language['flag']!,
        style: const TextStyle(fontSize: 24),
      ),
      title: Text(language['name']!),
      trailing: _selectedLanguage == language['code']
          ? const Icon(Icons.check, color: Colors.blue)
          : null,
      onTap: () => _changeLanguage(language['code']!),
    );
  }

  Widget _buildThemeSection(BuildContext context) {
    final localizations = AppLocalizations.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Theme',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 8),
        Text(
          'Customize the appearance of the app',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 16),
        ListTile(
          leading: const Icon(Icons.color_lens),
          title: const Text('Dark Mode'),
          trailing: Switch(
            value: false,
            onChanged: (value) {
              // TODO: Implement theme switching
            },
          ),
        ),
        ListTile(
          leading: const Icon(Icons.contrast),
          title: const Text('High Contrast'),
          trailing: Switch(
            value: false,
            onChanged: (value) {
              // TODO: Implement high contrast mode
            },
          ),
        ),
      ],
    );
  }

  Widget _buildAudioSection(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Audio',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 8),
        Text(
          'Configure audio playback settings',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 16),
        ListTile(
          leading: const Icon(Icons.speed),
          title: const Text('Playback Speed'),
          subtitle: const Text('1.0x'),
          onTap: () {
            // TODO: Implement playback speed selection
          },
        ),
        ListTile(
          leading: const Icon(Icons.skip_next),
          title: const Text('Skip Duration'),
          subtitle: const Text('15 seconds'),
          onTap: () {
            // TODO: Implement skip duration selection
          },
        ),
      ],
    );
  }

  Widget _buildDownloadSection(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Downloads',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 8),
        Text(
          'Manage download preferences and storage',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 16),
        ListTile(
          leading: const Icon(Icons.storage),
          title: const Text('Download Location'),
          subtitle: const Text('/storage/emulated/0/Download'),
          onTap: () {
            // TODO: Implement download location selection
          },
        ),
        ListTile(
          leading: const Icon(Icons.wifi),
          title: const Text('Wi-Fi Only Downloads'),
          trailing: Switch(
            value: true,
            onChanged: (value) {
              // TODO: Implement Wi-Fi only setting
            },
          ),
        ),
      ],
    );
  }
}