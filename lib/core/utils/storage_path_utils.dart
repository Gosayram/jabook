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

import 'package:jabook/core/logging/environment_logger.dart';
import 'package:jabook/core/permissions/permission_service.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Utility class for managing default storage paths for audiobooks.
///
/// This class provides methods to get and set the default download path
/// for audiobooks. On Android 11+ (API 30+), uses app-specific directory
/// which works WITHOUT permissions.
class StoragePathUtils {
  /// Private constructor for singleton pattern.
  StoragePathUtils._();

  /// Factory constructor to get the singleton instance.
  factory StoragePathUtils() => _instance;

  static final StoragePathUtils _instance = StoragePathUtils._();

  /// Default path for audiobooks storage.
  /// This is the user-accessible directory that requires MANAGE_EXTERNAL_STORAGE
  /// permission on Android 11+ (API 30+).
  /// User explicitly requested this path with all necessary permissions.
  static const String defaultAudiobookPath = '/storage/emulated/0/JabookAudio';

  /// Key for storing the download folder path in SharedPreferences.
  static const String downloadFolderPathKey = 'download_folder_path';

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
        // If it is, clear it and use default path instead
        if (PermissionService.isAppSpecificDirectory(savedPath)) {
          EnvironmentLogger().w(
            'StoragePathUtils: Saved path is app-specific directory (user cannot access): $savedPath. Clearing and using default path.',
          );
          // Clear the old app-specific path from preferences
          await prefs.remove(downloadFolderPathKey);
          // Continue to use default path below
        } else {
          // Validate that the saved path exists
          final dir = Directory(savedPath);
          if (await dir.exists()) {
            EnvironmentLogger().d(
              'StoragePathUtils: Using saved path: $savedPath',
            );
            return savedPath;
          } else {
            EnvironmentLogger().w(
              'StoragePathUtils: Saved path does not exist: $savedPath, falling back to default',
            );
            // Clear invalid path from preferences
            await prefs.remove(downloadFolderPathKey);
          }
        }
      }

      // Use default path
      final defaultPath = await _getDefaultPath();
      EnvironmentLogger().d(
        'StoragePathUtils: Using default path: $defaultPath',
      );
      final dir = Directory(defaultPath);
      if (!await dir.exists()) {
        try {
          await dir.create(recursive: true);
          EnvironmentLogger().d(
            'StoragePathUtils: Created directory: $defaultPath',
          );
          // Verify we can write to the directory
          try {
            final testFile = File('${dir.path}/.test_write');
            await testFile.writeAsString('test');
            await testFile.delete();
            EnvironmentLogger().d(
              'StoragePathUtils: Verified write access to directory: $defaultPath',
            );
          } on Exception catch (e) {
            EnvironmentLogger().w(
              'StoragePathUtils: Cannot write to directory: $defaultPath',
              error: e,
            );
            // Don't rethrow - directory exists, but may not have write access
            // This will be caught when trying to download
          }
        } on Exception catch (e) {
          EnvironmentLogger().e(
            'StoragePathUtils: Failed to create directory: $defaultPath',
            error: e,
          );
          // Check if error is related to permissions
          final errorStr = e.toString().toLowerCase();
          if (errorStr.contains('permission') ||
              errorStr.contains('access') ||
              errorStr.contains('denied')) {
            EnvironmentLogger().w(
              'StoragePathUtils: Permission denied when creating directory. '
              'User may need to grant storage permission in app settings.',
            );
          }
          // Re-throw to be handled by outer catch
          rethrow;
        }
      } else {
        // Verify we can write to existing directory
        try {
          final testFile = File('${dir.path}/.test_write');
          await testFile.writeAsString('test');
          await testFile.delete();
          EnvironmentLogger().d(
            'StoragePathUtils: Verified write access to existing directory: $defaultPath',
          );
        } on Exception catch (e) {
          EnvironmentLogger().w(
            'StoragePathUtils: Cannot write to existing directory: $defaultPath',
            error: e,
          );
          // Don't rethrow - directory exists, but may not have write access
          // This will be caught when trying to download
        }
      }

      // Save default path to preferences if not already set
      if (savedPath == null || savedPath.isEmpty) {
        await prefs.setString(downloadFolderPathKey, defaultPath);
        EnvironmentLogger().d(
          'StoragePathUtils: Saved default path to preferences: $defaultPath',
        );
      }

      return defaultPath;
    } on Exception catch (e) {
      EnvironmentLogger().e(
        'StoragePathUtils: Error getting default path, using fallback',
        error: e,
      );
      // Fallback to default path if anything fails
      // This path requires MANAGE_EXTERNAL_STORAGE permission on Android 11+
      EnvironmentLogger().w(
        'StoragePathUtils: Using default path (requires MANAGE_EXTERNAL_STORAGE on Android 11+)',
      );
      return defaultAudiobookPath;
    }
  }

  /// Gets the default path based on platform.
  ///
  /// For Android, uses user-accessible directory: /storage/emulated/0/JabookAudio
  /// This requires MANAGE_EXTERNAL_STORAGE permission on Android 11+ (API 30+).
  /// Falls back to app documents directory if needed.
  Future<String> _getDefaultPath() async {
    if (Platform.isAndroid) {
      // Use user-accessible directory: /storage/emulated/0/JabookAudio
      // This requires MANAGE_EXTERNAL_STORAGE permission on Android 11+
      // User explicitly requested this path with all necessary permissions
      EnvironmentLogger().d(
        'StoragePathUtils: Using user-accessible default path: $defaultAudiobookPath',
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

  /// Initializes the default path on first app launch.
  ///
  /// This should be called once when the app starts to ensure
  /// the default path is set and the directory exists.
  Future<void> initializeDefaultPath() async {
    try {
      EnvironmentLogger().d('StoragePathUtils: Initializing default path');
      final path = await getDefaultAudiobookPath();
      final dir = Directory(path);
      if (!await dir.exists()) {
        try {
          await dir.create(recursive: true);
          EnvironmentLogger().d(
            'StoragePathUtils: Created directory during initialization: $path',
          );
        } on Exception catch (e) {
          EnvironmentLogger().e(
            'StoragePathUtils: Failed to create directory during initialization: $path',
            error: e,
          );
          // Don't rethrow - initialization should be non-blocking
        }
      } else {
        EnvironmentLogger().d(
          'StoragePathUtils: Directory already exists: $path',
        );
      }
    } on Exception catch (e) {
      EnvironmentLogger().e(
        'StoragePathUtils: Error during initialization',
        error: e,
      );
      // Ignore errors during initialization to avoid blocking app startup
    }
  }
}
