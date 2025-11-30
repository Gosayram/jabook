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

import 'package:jabook/core/infrastructure/logging/environment_logger.dart';
import 'package:jabook/core/infrastructure/permissions/permission_service.dart';
import 'package:jabook/core/utils/content_uri_service.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Service for managing storage paths for audiobooks and app data.
///
/// This service provides methods to get and set storage paths,
/// handling platform-specific requirements and permissions.
class PathStorageService {
  /// Creates a new PathStorageService instance.
  PathStorageService();

  /// Default path for audiobooks storage.
  /// This is the user-accessible directory that requires MANAGE_EXTERNAL_STORAGE
  /// permission on Android 11+ (API 30+).
  static const String defaultAudiobookPath = '/storage/emulated/0/JabookAudio';

  /// Key for storing the download folder path in SharedPreferences.
  static const String downloadFolderPathKey = 'download_folder_path';

  /// Key for storing the library folder path in SharedPreferences.
  static const String libraryFolderPathKey = 'library_folder_path';

  /// Key for storing multiple library folders in SharedPreferences.
  static const String libraryFoldersKey = 'library_folders';

  /// Gets the default audiobook storage path.
  ///
  /// Returns the path from SharedPreferences if set, otherwise returns
  /// the default path. Creates the directory if it doesn't exist.
  ///
  /// Returns the path as a String.
  Future<String> getDefaultAudiobookPath() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final savedPath = prefs.getString(downloadFolderPathKey);

      if (savedPath != null && savedPath.isNotEmpty) {
        // Check if saved path is app-specific directory (old path that user can't access)
        if (PermissionService.isAppSpecificDirectory(savedPath)) {
          EnvironmentLogger().w(
            'PathStorageService: Saved path is app-specific directory (user cannot access): $savedPath. Clearing and using default path.',
          );
          await prefs.remove(downloadFolderPathKey);
        } else if (isContentUri(savedPath)) {
          // For content URIs, check access via ContentResolver
          try {
            final contentUriService = ContentUriService();
            final hasAccess = await contentUriService.checkUriAccess(savedPath);
            if (hasAccess) {
              EnvironmentLogger().d(
                'PathStorageService: Using saved content URI: $savedPath',
              );
              return savedPath;
            } else {
              EnvironmentLogger().w(
                'PathStorageService: No access to saved content URI: $savedPath, falling back to default',
              );
              await prefs.remove(downloadFolderPathKey);
            }
          } on Exception catch (e) {
            EnvironmentLogger().w(
              'PathStorageService: Error checking content URI access: $savedPath',
              error: e,
            );
            return savedPath;
          }
        } else {
          // Validate that the saved path exists
          final dir = Directory(savedPath);
          if (await dir.exists()) {
            return savedPath;
          } else {
            EnvironmentLogger().w(
              'PathStorageService: Saved path does not exist: $savedPath, falling back to default',
            );
            await prefs.remove(downloadFolderPathKey);
          }
        }
      }

      // Use default path
      final defaultPath = await _getDefaultPath();
      final dir = Directory(defaultPath);
      if (!await dir.exists()) {
        await dir.create(recursive: true);
      }
      return defaultPath;
    } on Exception catch (e) {
      EnvironmentLogger().e(
        'PathStorageService: Error getting default audiobook path',
        error: e,
      );
      // Fallback to default path
      return defaultAudiobookPath;
    }
  }

  /// Gets the default path based on platform.
  ///
  /// Falls back to app documents directory if needed.
  Future<String> _getDefaultPath() async {
    if (Platform.isAndroid) {
      // Use user-accessible directory: /storage/emulated/0/JabookAudio
      // This requires MANAGE_EXTERNAL_STORAGE permission on Android 11+
      EnvironmentLogger().d(
        'PathStorageService: Using user-accessible default path: $defaultAudiobookPath',
      );
      return defaultAudiobookPath;
    }

    // For other platforms, use application documents directory
    final appDocDir = await getApplicationDocumentsDirectory();
    return '${appDocDir.path}/JabookAudio';
  }

  /// Sets the download folder path.
  ///
  /// The [path] parameter is the path to save.
  Future<void> setDownloadFolderPath(String path) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(downloadFolderPathKey, path);
    } on Exception {
      // Ignore errors
    }
  }

  /// Gets the download folder path from SharedPreferences.
  ///
  /// Returns the saved path or null if not set.
  Future<String?> getDownloadFolderPath() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      return prefs.getString(downloadFolderPathKey);
    } on Exception {
      return null;
    }
  }

  /// Checks if a path is a content URI.
  ///
  /// The [path] parameter is the path to check.
  ///
  /// Returns true if the path is a content URI, false otherwise.
  bool isContentUri(String path) => path.startsWith('content://');
}
