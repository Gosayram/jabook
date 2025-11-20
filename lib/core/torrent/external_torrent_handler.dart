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

import 'package:jabook/core/logging/environment_logger.dart';
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
  /// Returns true if the link was opened successfully, false otherwise.
  Future<bool> openMagnetLink(String magnetUrl) async {
    try {
      final uri = Uri.parse(magnetUrl);
      if (!uri.scheme.startsWith('magnet')) {
        EnvironmentLogger().e('Invalid magnet URL: $magnetUrl');
        return false;
      }

      final canLaunch = await canLaunchUrl(uri);
      if (!canLaunch) {
        EnvironmentLogger().w('Cannot launch magnet URL: $magnetUrl');
        return false;
      }

      await launchUrl(
        uri,
        mode: LaunchMode.externalApplication,
      );

      EnvironmentLogger()
          .i('Opened magnet link in external client: $magnetUrl');
      return true;
    } on Exception catch (e) {
      EnvironmentLogger().e('Failed to open magnet link: $e');
      return false;
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
}
