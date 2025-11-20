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

import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Utility class for managing default storage paths for audiobooks.
///
/// This class provides methods to get and set the default download path
/// for audiobooks, with a default value of /storage/emulated/0/JabookAudio.
class StoragePathUtils {
  /// Private constructor for singleton pattern.
  StoragePathUtils._();

  /// Factory constructor to get the singleton instance.
  factory StoragePathUtils() => _instance;

  static final StoragePathUtils _instance = StoragePathUtils._();

  /// Default path for audiobooks storage.
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
        // Validate that the saved path exists
        final dir = Directory(savedPath);
        if (await dir.exists()) {
          return savedPath;
        }
      }

      // Use default path
      final defaultPath = await _getDefaultPath();
      final dir = Directory(defaultPath);
      if (!await dir.exists()) {
        await dir.create(recursive: true);
      }

      // Save default path to preferences if not already set
      if (savedPath == null || savedPath.isEmpty) {
        await prefs.setString(downloadFolderPathKey, defaultPath);
      }

      return defaultPath;
    } on Exception {
      // Fallback to default path if anything fails
      return defaultAudiobookPath;
    }
  }

  /// Gets the default path based on platform.
  ///
  /// For Android, tries to get external storage directory.
  /// Falls back to hardcoded path if needed.
  Future<String> _getDefaultPath() async {
    if (Platform.isAndroid) {
      try {
        // Try to get external storage directory
        final externalDir = await getExternalStorageDirectory();
        if (externalDir != null) {
          // Get parent directory (usually /storage/emulated/0)
          final parent = externalDir.parent;
          final audiobookDir = Directory('${parent.path}/JabookAudio');
          return audiobookDir.path;
        }
      } on Exception {
        // Fallback to hardcoded path
      }

      // Fallback to hardcoded path
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
      final path = await getDefaultAudiobookPath();
      final dir = Directory(path);
      if (!await dir.exists()) {
        await dir.create(recursive: true);
      }
    } on Exception {
      // Ignore errors during initialization
    }
  }
}
