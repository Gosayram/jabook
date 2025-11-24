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
import 'package:flutter/services.dart';
import 'package:jabook/core/config/app_config.dart';
import 'package:jabook/core/constants/about_constants.dart';
import 'package:jabook/l10n/app_localizations.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:url_launcher/url_launcher.dart';

/// Screen displaying information about the application.
///
/// This screen shows app version, build number, package ID, license information,
/// and provides quick access to GitHub repository, Telegram channel, and other resources.
class AboutScreen extends StatefulWidget {
  /// Creates a new AboutScreen instance.
  const AboutScreen({super.key});

  @override
  State<AboutScreen> createState() => _AboutScreenState();
}

class _AboutScreenState extends State<AboutScreen> {
  PackageInfo? _packageInfo;
  String? _deviceModel;
  String? _androidVersion;
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadPackageInfo();
    _loadDeviceInfo();
  }

  Future<void> _loadPackageInfo() async {
    try {
      final packageInfo = await PackageInfo.fromPlatform();
      if (mounted) {
        setState(() {
          _packageInfo = packageInfo;
          _isLoading = false;
        });
      }
    } on Exception catch (e) {
      // Log error but don't crash the app
      debugPrint('Failed to load package info: $e');
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
    }
  }

  Future<void> _loadDeviceInfo() async {
    if (!Platform.isAndroid) {
      return;
    }

    try {
      final deviceInfoPlugin = DeviceInfoPlugin();
      final androidInfo = await deviceInfoPlugin.androidInfo;
      if (mounted) {
        setState(() {
          _deviceModel = androidInfo.model;
          _androidVersion = 'Android ${androidInfo.version.release}';
        });
      }
    } on Exception catch (e) {
      // Log error but don't crash the app
      debugPrint('Failed to load device info: $e');
      // Keep default values (null)
    }
  }

  Future<void> _copyInfoToClipboard() async {
    if (_packageInfo == null) return;

    final config = AppConfig();
    final appName = config.appName;
    final version = _packageInfo!.version;
    final buildNumber = _packageInfo!.buildNumber;
    final packageName = _packageInfo!.packageName;

    final localizations = AppLocalizations.of(context);
    final deviceInfo = _deviceModel ?? (localizations?.unknown ?? 'Unknown');
    final androidVersion =
        _androidVersion ?? (localizations?.unknown ?? 'Unknown');

    final deviceLabel = localizations?.emailFeedbackDevice ?? 'Device:';
    final infoString =
        '$appName v$version ($buildNumber), $packageName, $androidVersion, $deviceLabel $deviceInfo';

    await Clipboard.setData(ClipboardData(text: infoString));

    if (mounted) {
      final localizations = AppLocalizations.of(context);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(localizations?.infoCopied ?? 'Info copied'),
          duration: const Duration(seconds: 2),
        ),
      );
    }
  }

  Future<void> _openUrl(String url) async {
    try {
      final uri = Uri.parse(url);
      if (await canLaunchUrl(uri)) {
        await launchUrl(
          uri,
          mode: LaunchMode.externalApplication,
        );
      } else {
        if (mounted) {
          final localizations = AppLocalizations.of(context);
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                localizations?.failedToOpenLink ?? 'Failed to open link',
              ),
              duration: const Duration(seconds: 2),
            ),
          );
        }
      }
    } on Exception {
      if (mounted) {
        final localizations = AppLocalizations.of(context);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              localizations?.failedToOpenLink ?? 'Failed to open link',
            ),
            duration: const Duration(seconds: 2),
          ),
        );
      }
    }
  }

  Future<void> _openEmail() async {
    if (AboutConstants.developerEmail == null) return;

    try {
      final packageInfo = _packageInfo;
      if (packageInfo == null) return;

      final localizations = AppLocalizations.of(context);
      final deviceInfo = _deviceModel ?? (localizations?.unknown ?? 'Unknown');
      final androidVersion =
          _androidVersion ?? (localizations?.unknown ?? 'Unknown');
      final subject =
          localizations?.emailFeedbackSubject ?? 'JaBook - Feedback';
      final appLabel = localizations?.emailFeedbackApp ?? 'App: JaBook';
      final versionLabel = localizations?.emailFeedbackVersion ?? 'Version:';
      final deviceLabel = localizations?.emailFeedbackDevice ?? 'Device:';
      final androidLabel = localizations?.emailFeedbackAndroid ?? 'Android:';
      final descriptionLabel = localizations?.emailFeedbackDescription ??
          'Description of issue / suggestion:';

      final body = '''
$appLabel
$versionLabel ${packageInfo.version} (${packageInfo.buildNumber})
$deviceLabel $deviceInfo
$androidLabel $androidVersion

$descriptionLabel


''';

      final uri = Uri.parse(
        'mailto:${AboutConstants.developerEmail}?subject=${Uri.encodeComponent(subject)}&body=${Uri.encodeComponent(body)}',
      );

      if (await canLaunchUrl(uri)) {
        await launchUrl(uri);
      } else {
        if (mounted) {
          final localizations = AppLocalizations.of(context);
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                localizations?.failedToOpenEmail ??
                    'Failed to open email client',
              ),
              duration: const Duration(seconds: 2),
            ),
          );
        }
      }
    } on Exception {
      if (mounted) {
        final localizations = AppLocalizations.of(context);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              localizations?.failedToOpenEmail ?? 'Failed to open email client',
            ),
            duration: const Duration(seconds: 2),
          ),
        );
      }
    }
  }

  Future<void> _showLicensePage() async {
    // Use Flutter's built-in license page which shows both main license
    // and third-party libraries
    if (mounted) {
      showLicensePage(
        context: context,
        applicationName: AppConfig().appName,
        applicationVersion: _packageInfo?.version ??
            (AppLocalizations.of(context)?.unknown ?? 'Unknown'),
        applicationLegalese: 'Copyright 2025 Jabook Contributors',
      );
    }
  }

  Future<void> _openLicenseOnGitHub() async {
    await _openUrl(AboutConstants.licenseUrl);
  }

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final config = AppConfig();

    return Scaffold(
      appBar: AppBar(
        title: Text(localizations?.aboutTitle ?? 'About'),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : LayoutBuilder(
              builder: (context, constraints) => SingleChildScrollView(
                padding: EdgeInsets.only(
                  left: constraints.maxWidth > 720 ? 0 : 16,
                  right: constraints.maxWidth > 720 ? 0 : 16,
                  bottom: 24,
                ),
                child: ConstrainedBox(
                  constraints: const BoxConstraints(
                    maxWidth: 720,
                  ),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    child: Column(
                      children: [
                        const SizedBox(height: 24),

                        // App header
                        _buildAppHeader(context, config, localizations),

                        const SizedBox(height: 32),

                        // Version and build info
                        _buildVersionSection(
                          context,
                          localizations,
                          theme,
                        ),

                        const SizedBox(height: 24),

                        // License section
                        _buildLicenseSection(
                          context,
                          localizations,
                          theme,
                        ),

                        const SizedBox(height: 24),

                        // Contacts and feedback
                        _buildContactsSection(
                          context,
                          localizations,
                          theme,
                        ),

                        const SizedBox(height: 24),

                        // Resources links
                        _buildResourcesSection(
                          context,
                          localizations,
                          theme,
                        ),

                        const SizedBox(height: 24),

                        // About developer
                        _buildDeveloperSection(
                          context,
                          localizations,
                          theme,
                        ),

                        const SizedBox(height: 24),
                      ],
                    ),
                  ),
                ),
              ),
            ),
    );
  }

  Widget _buildAppHeader(
    BuildContext context,
    AppConfig config,
    AppLocalizations? localizations,
  ) {
    final screenWidth = MediaQuery.of(context).size.width;
    final iconSize = screenWidth > 600 ? 96.0 : 64.0;

    return Column(
      children: [
        ClipRRect(
          borderRadius: BorderRadius.circular(16),
          child: Image.asset(
            'assets/icons/app_icon.png',
            width: iconSize,
            height: iconSize,
            errorBuilder: (context, error, stackTrace) => Container(
              width: iconSize,
              height: iconSize,
              decoration: BoxDecoration(
                color: Theme.of(context).colorScheme.primaryContainer,
                borderRadius: BorderRadius.circular(16),
              ),
              child: Icon(
                Icons.library_books,
                size: iconSize * 0.6,
                color: Theme.of(context).colorScheme.onPrimaryContainer,
              ),
            ),
          ),
        ),
        const SizedBox(height: 16),
        Text(
          config.appName,
          style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                fontWeight: FontWeight.bold,
              ),
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 8),
        Text(
          localizations?.aboutAppSlogan ??
              'Modern audiobook player for torrents',
          style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              ),
          textAlign: TextAlign.center,
        ),
      ],
    );
  }

  Widget _buildVersionSection(
    BuildContext context,
    AppLocalizations? localizations,
    ThemeData theme,
  ) {
    if (_packageInfo == null) {
      return const SizedBox.shrink();
    }

    return Semantics(
      label: localizations?.versionInformationLongPress ??
          'Version information. Long press to copy.',
      child: GestureDetector(
        onLongPress: _copyInfoToClipboard,
        child: Card(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Padding(
                padding: const EdgeInsets.all(16),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      localizations?.appVersion ?? 'App Version',
                      style: theme.textTheme.titleMedium,
                    ),
                    Text(
                      _packageInfo!.version,
                      style: theme.textTheme.bodyLarge,
                    ),
                  ],
                ),
              ),
              const Divider(height: 1),
              Padding(
                padding: const EdgeInsets.all(16),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      localizations?.buildNumber ?? 'Build',
                      style: theme.textTheme.titleMedium,
                    ),
                    Text(
                      _packageInfo!.buildNumber,
                      style: theme.textTheme.bodyLarge,
                    ),
                  ],
                ),
              ),
              const Divider(height: 1),
              Padding(
                padding: const EdgeInsets.all(16),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      localizations?.packageId ?? 'Package ID',
                      style: theme.textTheme.titleMedium,
                    ),
                    Flexible(
                      child: Text(
                        _packageInfo!.packageName,
                        style: theme.textTheme.bodyLarge,
                        textAlign: TextAlign.end,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                ),
              ),
              const Divider(height: 1),
              Padding(
                padding: const EdgeInsets.all(16),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      localizations?.copyInfo ?? 'Copy',
                      style: theme.textTheme.titleMedium,
                    ),
                    Semantics(
                      label: localizations?.copyInfo ?? 'Copy information',
                      button: true,
                      child: IconButton(
                        icon: const Icon(Icons.copy),
                        onPressed: _copyInfoToClipboard,
                        tooltip: localizations?.copyInfo ?? 'Copy',
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildLicenseSection(
    BuildContext context,
    AppLocalizations? localizations,
    ThemeData theme,
  ) =>
      Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            localizations?.licenseTitle ?? 'License',
            style: theme.textTheme.titleLarge,
          ),
          const SizedBox(height: 8),
          Text(
            localizations?.licenseDescription ??
                'JaBook is distributed under the Apache 2.0 license.',
            style: theme.textTheme.bodyMedium,
          ),
          const SizedBox(height: 16),
          Semantics(
            label: localizations?.thirdPartyLicenses ?? 'Third-Party Libraries',
            button: true,
            child: ListTile(
              leading: const Icon(Icons.library_books),
              title: Text(
                localizations?.thirdPartyLicenses ?? 'Third-Party Libraries',
              ),
              subtitle: Text(
                localizations?.viewLicenses ?? 'View licenses',
              ),
              trailing: const Icon(Icons.arrow_forward_ios),
              onTap: _showLicensePage,
            ),
          ),
          Semantics(
            label: localizations?.licenseOnGitHub ?? 'License on GitHub',
            button: true,
            child: ListTile(
              leading: const Icon(Icons.open_in_new),
              title: Text(
                localizations?.licenseOnGitHub ?? 'License on GitHub',
              ),
              subtitle: Text(
                localizations?.viewLicenseFile ?? 'View LICENSE file',
              ),
              trailing: const Icon(Icons.arrow_forward_ios),
              onTap: _openLicenseOnGitHub,
            ),
          ),
        ],
      );

  Widget _buildContactsSection(
    BuildContext context,
    AppLocalizations? localizations,
    ThemeData theme,
  ) =>
      Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Semantics(
            label: localizations?.telegramChannel ?? 'Telegram Channel',
            button: true,
            child: ListTile(
              leading: const Icon(Icons.chat),
              title: Text(localizations?.telegramChannel ?? 'Telegram Channel'),
              subtitle: Text(
                localizations?.openTelegramChannel ??
                    'Open channel in Telegram',
              ),
              trailing: const Icon(Icons.arrow_forward_ios),
              onTap: () => _openUrl(AboutConstants.telegramUrl),
            ),
          ),
          if (AboutConstants.developerEmail != null)
            Semantics(
              label: localizations?.contactDeveloper ?? 'Contact Developer',
              button: true,
              child: ListTile(
                leading: const Icon(Icons.email_outlined),
                title: Text(
                  localizations?.contactDeveloper ?? 'Contact Developer',
                ),
                subtitle: Text(
                  localizations?.openEmailClient ?? 'Open email client',
                ),
                trailing: const Icon(Icons.arrow_forward_ios),
                onTap: _openEmail,
              ),
            ),
          if (AboutConstants.supportUrl != null)
            Semantics(
              label: localizations?.supportProject ?? 'Support Project',
              button: true,
              child: ListTile(
                leading: const Icon(Icons.favorite_outline),
                title: Text(localizations?.supportProject ?? 'Support Project'),
                subtitle: Text(
                  localizations?.supportProjectDescription ??
                      'Donate or support development',
                ),
                trailing: const Icon(Icons.arrow_forward_ios),
                onTap: () => _openUrl(AboutConstants.supportUrl!),
              ),
            ),
        ],
      );

  Widget _buildResourcesSection(
    BuildContext context,
    AppLocalizations? localizations,
    ThemeData theme,
  ) =>
      Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Semantics(
            label: localizations?.githubRepository ?? 'GitHub',
            button: true,
            child: ListTile(
              leading: const Icon(Icons.code),
              title: Text(localizations?.githubRepository ?? 'GitHub'),
              subtitle: Text(
                localizations?.githubRepositoryDescription ??
                    'Source code and changelog',
              ),
              trailing: const Icon(Icons.arrow_forward_ios),
              onTap: () => _openUrl(AboutConstants.githubUrl),
            ),
          ),
          Semantics(
            label: localizations?.changelog ?? 'Changelog',
            button: true,
            child: ListTile(
              leading: const Icon(Icons.history),
              title: Text(localizations?.changelog ?? 'Changelog'),
              subtitle: Text(
                localizations?.changelogDescription ??
                    'Version history and updates',
              ),
              trailing: const Icon(Icons.arrow_forward_ios),
              onTap: () => _openUrl(AboutConstants.changelogUrl),
            ),
          ),
          Semantics(
            label: localizations?.issues ?? 'Issues',
            button: true,
            child: ListTile(
              leading: const Icon(Icons.bug_report_outlined),
              title: Text(localizations?.issues ?? 'Issues'),
              subtitle: Text(
                localizations?.issuesDescription ??
                    'Report bugs and request features',
              ),
              trailing: const Icon(Icons.arrow_forward_ios),
              onTap: () => _openUrl(AboutConstants.issuesUrl),
            ),
          ),
          if (AboutConstants.forum4PdaUrl != null)
            Semantics(
              label: '4PDA',
              button: true,
              child: ListTile(
                leading: const Icon(Icons.forum_outlined),
                title: Text(
                  localizations?.forum4Pda ?? '4PDA',
                ),
                subtitle: Text(
                  localizations?.appDiscussionQuestionsReviews ??
                      'App discussion, questions and reviews',
                ),
                trailing: const Icon(Icons.arrow_forward_ios),
                onTap: () => _openUrl(AboutConstants.forum4PdaUrl!),
              ),
            ),
        ],
      );

  Widget _buildDeveloperSection(
    BuildContext context,
    AppLocalizations? localizations,
    ThemeData theme,
  ) =>
      Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            localizations?.aboutDeveloper ?? 'About Developer',
            style: theme.textTheme.titleLarge,
          ),
          const SizedBox(height: 8),
          Text(
            localizations?.aboutDeveloperText ??
                'JaBook is developed by Jabook Contributors team.',
            style: theme.textTheme.bodyMedium,
            textAlign: TextAlign.center,
            softWrap: true,
          ),
          const SizedBox(height: 16),
          Semantics(
            label: localizations?.jabookContributors ?? 'Jabook Contributors',
            button: true,
            child: ListTile(
              leading: const Icon(Icons.people_outline),
              title: Text(
                localizations?.jabookContributors ?? 'Jabook Contributors',
              ),
              subtitle: Text(
                localizations?.github ?? 'GitHub',
              ),
              trailing: const Icon(Icons.arrow_forward_ios),
              onTap: () => _openUrl(AboutConstants.githubContributorsUrl),
            ),
          ),
        ],
      );
}
