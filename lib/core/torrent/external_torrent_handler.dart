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

import 'package:android_intent_plus/android_intent.dart';
import 'package:jabook/core/infrastructure/logging/environment_logger.dart';
import 'package:jabook/core/utils/storage_path_utils.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:url_launcher/url_launcher.dart';

/// Service for handling torrent downloads through external torrent clients.
///
/// This service allows users to open magnet links or torrent files
/// in external torrent clients installed on the device.
class ExternalTorrentHandler {
  /// Private constructor for singleton pattern.
  ExternalTorrentHandler._();

  /// Factory constructor to get the singleton instance.
  factory ExternalTorrentHandler() => _instance;

  static final ExternalTorrentHandler _instance = ExternalTorrentHandler._();

  /// Opens a magnet link in an external torrent client.
  ///
  /// The [magnetUrl] parameter is the magnet link to open.
  /// The [savePath] parameter is an optional download path to pass to the client.
  /// Returns a map with 'success' (bool) and 'pathPassed' (bool) indicating
  /// whether the link was opened and if the save path was passed.
  Future<Map<String, dynamic>> openMagnetLink(
    String magnetUrl, {
    String? savePath,
  }) async {
    try {
      final uri = Uri.parse(magnetUrl);
      if (!uri.scheme.startsWith('magnet')) {
        EnvironmentLogger().e('Invalid magnet URL: $magnetUrl');
        return {'success': false, 'pathPassed': false};
      }

      if (Platform.isAndroid) {
        // Use android_intent_plus for Android to pass save path
        try {
          // Get preferred client if set
          final preferredClient = await getPreferredClient();

          // Get save path from parameter, preferences, or default
          final downloadPath = savePath ??
              await getPreferredDownloadPath() ??
              await StoragePathUtils().getDefaultAudiobookPath();

          // Build extras map if save path is provided
          final Map<String, dynamic>? extras;
          var pathCanBePassed = false;
          if (downloadPath.isNotEmpty) {
            if (supportsSavePath(preferredClient)) {
              extras = <String, dynamic>{};
              if (preferredClient == 'org.proninyaroslav.libretorrent') {
                // LibreTorrent specific extra
                extras['org.proninyaroslav.libretorrent.intent.extra.SAVE_PATH'] =
                    downloadPath;
                pathCanBePassed = true;
              } else if (preferredClient == 'com.flud.android') {
                // Flud specific extra
                extras['com.flud.android.intent.extra.SAVE_PATH'] =
                    downloadPath;
                pathCanBePassed = true;
              }
              // Add more clients as needed
            } else {
              // Client doesn't support save path, but we'll still try to open
              extras = null;
              if (preferredClient != null) {
                EnvironmentLogger().w(
                  'Client ${getClientName(preferredClient)} does not support '
                  'passing save path via Intent',
                );
              }
            }
          } else {
            extras = null;
          }

          final intent = AndroidIntent(
            action: 'android.intent.action.VIEW',
            data: magnetUrl,
            package: preferredClient, // Optional: specific client
            arguments: extras, // Pass extras through arguments
          );

          await intent.launch();
          EnvironmentLogger()
              .i('Opened magnet link in external client: $magnetUrl');
          if (pathCanBePassed) {
            EnvironmentLogger().i('Passed save path: $downloadPath');
          } else if (downloadPath.isNotEmpty) {
            EnvironmentLogger().w(
              'Save path could not be passed to client ${getClientName(preferredClient)}',
            );
          }
          return {
            'success': true,
            'pathPassed': pathCanBePassed,
            'clientName': getClientName(preferredClient),
          };
        } on Exception catch (e) {
          EnvironmentLogger()
              .w('Failed to use Android Intent, falling back to launchUrl: $e');
          // Fallback to launchUrl
        }
      }

      // Fallback for other platforms or if Intent fails
      final canLaunch = await canLaunchUrl(uri);
      if (!canLaunch) {
        EnvironmentLogger().w('Cannot launch magnet URL: $magnetUrl');
        return {'success': false, 'pathPassed': false};
      }

      await launchUrl(
        uri,
        mode: LaunchMode.externalApplication,
      );

      EnvironmentLogger()
          .i('Opened magnet link in external client: $magnetUrl');
      // On fallback, we can't pass save path
      return {'success': true, 'pathPassed': false};
    } on Exception catch (e) {
      EnvironmentLogger().e('Failed to open magnet link: $e');
      return {'success': false, 'pathPassed': false};
    }
  }

  /// Opens a torrent file URL in an external torrent client.
  ///
  /// The [torrentUrl] parameter is the URL of the .torrent file.
  /// Returns true if the file was opened successfully, false otherwise.
  Future<bool> openTorrentFile(String torrentUrl) async {
    try {
      final uri = Uri.parse(torrentUrl);
      if (!torrentUrl.toLowerCase().endsWith('.torrent')) {
        EnvironmentLogger().e('Invalid torrent file URL: $torrentUrl');
        return false;
      }

      final canLaunch = await canLaunchUrl(uri);
      if (!canLaunch) {
        EnvironmentLogger().w('Cannot launch torrent file URL: $torrentUrl');
        return false;
      }

      await launchUrl(
        uri,
        mode: LaunchMode.externalApplication,
      );

      EnvironmentLogger()
          .i('Opened torrent file in external client: $torrentUrl');
      return true;
    } on Exception catch (e) {
      EnvironmentLogger().e('Failed to open torrent file: $e');
      return false;
    }
  }

  /// Gets the preferred download path for external torrent clients.
  ///
  /// Returns the path saved in SharedPreferences, or null if not set.
  Future<String?> getPreferredDownloadPath() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      return prefs.getString('external_torrent_download_path');
    } on Exception catch (e) {
      EnvironmentLogger().e('Failed to get preferred download path: $e');
      return null;
    }
  }

  /// Sets the preferred download path for external torrent clients.
  ///
  /// The [path] parameter is the download path to save.
  Future<void> setPreferredDownloadPath(String path) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString('external_torrent_download_path', path);
      EnvironmentLogger().i('Set preferred download path: $path');
    } on Exception catch (e) {
      EnvironmentLogger().e('Failed to set preferred download path: $e');
    }
  }

  /// Gets the preferred torrent client package name.
  ///
  /// Returns the package name saved in SharedPreferences, or null if not set.
  Future<String?> getPreferredClient() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      return prefs.getString('preferred_torrent_client');
    } on Exception catch (e) {
      EnvironmentLogger().e('Failed to get preferred client: $e');
      return null;
    }
  }

  /// Sets the preferred torrent client package name.
  ///
  /// The [packageName] parameter is the package name of the preferred client.
  Future<void> setPreferredClient(String packageName) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString('preferred_torrent_client', packageName);
      EnvironmentLogger().i('Set preferred torrent client: $packageName');
    } on Exception catch (e) {
      EnvironmentLogger().e('Failed to set preferred client: $e');
    }
  }

  /// Checks if the specified client supports passing save path via Intent.
  ///
  /// The [packageName] parameter is the package name of the client.
  /// Returns true if the client is known to support save path, false otherwise.
  bool supportsSavePath(String? packageName) {
    if (packageName == null) return false;

    // List of known clients that support save path via Intent
    const supportedClients = [
      'org.proninyaroslav.libretorrent', // LibreTorrent
      'com.flud.android', // Flud
      // Add more clients as needed
    ];

    return supportedClients.contains(packageName);
  }

  /// Gets a user-friendly name for a torrent client package.
  ///
  /// The [packageName] parameter is the package name of the client.
  /// Returns a user-friendly name or the package name if unknown.
  String getClientName(String? packageName) {
    if (packageName == null) return 'Unknown';

    const clientNames = {
      'org.proninyaroslav.libretorrent': 'LibreTorrent',
      'com.flud.android': 'Flud',
      'com.utorrent.client': 'uTorrent',
      'com.transmissionbt.android': 'Transmission',
      // Add more clients as needed
    };

    return clientNames[packageName] ?? packageName;
  }
}
