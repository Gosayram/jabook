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

/// Utility class for managing default storage paths for audiobooks.
///
/// This class provides methods to get and set the default download path
/// for audiobooks. On Android 11+ (API 30+), uses app-specific directory
/// which works WITHOUT permissions.
///
/// Note: This class is no longer a singleton. Use [storagePathUtilsProvider]
/// to get an instance via dependency injection.
class StoragePathUtils {
  /// Constructor for StoragePathUtils.
  ///
  /// Use [storagePathUtilsProvider] to get an instance via dependency injection.
  StoragePathUtils();

  /// Default path for audiobooks storage.
  /// This is the user-accessible directory that requires MANAGE_EXTERNAL_STORAGE
  /// permission on Android 11+ (API 30+).
  /// User explicitly requested this path with all necessary permissions.
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
        // If it is, clear it and use default path instead
        if (PermissionService.isAppSpecificDirectory(savedPath)) {
          EnvironmentLogger().w(
            'StoragePathUtils: Saved path is app-specific directory (user cannot access): $savedPath. Clearing and using default path.',
          );
          // Clear the old app-specific path from preferences
          await prefs.remove(downloadFolderPathKey);
          // Continue to use default path below
        } else if (isContentUri(savedPath)) {
          // For content URIs, check access via ContentResolver
          try {
            final contentUriService = ContentUriService();
            final hasAccess = await contentUriService.checkUriAccess(savedPath);
            if (hasAccess) {
              EnvironmentLogger().d(
                'StoragePathUtils: Using saved content URI: $savedPath',
              );
              return savedPath;
            } else {
              EnvironmentLogger().w(
                'StoragePathUtils: No access to saved content URI: $savedPath, falling back to default',
              );
              await prefs.remove(downloadFolderPathKey);
            }
          } on Exception catch (e) {
            EnvironmentLogger().w(
              'StoragePathUtils: Error checking content URI access: $savedPath',
              error: e,
            );
            // Assume accessible if check fails (might be timing issue)
            return savedPath;
          }
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

  /// Gets the library folder path (separate from download folder).
  ///
  /// Returns the path from SharedPreferences if set, otherwise returns
  /// the default audiobook path. Creates the directory if it doesn't exist.
  ///
  /// Returns the path as a String.
  Future<String> getLibraryFolderPath() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final savedPath = prefs.getString(libraryFolderPathKey);

      if (savedPath != null && savedPath.isNotEmpty) {
        // For content URIs, keep them as-is (don't convert)
        // ContentResolver will handle them directly
        if (isContentUri(savedPath)) {
          EnvironmentLogger().d(
            'StoragePathUtils: Using content URI directly: $savedPath',
          );
          // Return content URI as-is - scanner will use ContentResolver
          return savedPath;
        }

        // Convert content URI to file path if needed (fallback for old saved paths)
        final actualPath = savedPath;

        // Check if saved path is app-specific directory (old path that user can't access)
        if (PermissionService.isAppSpecificDirectory(actualPath)) {
          EnvironmentLogger().w(
            'StoragePathUtils: Saved library path is app-specific directory (user cannot access): $actualPath. Clearing and using default path.',
          );
          await prefs.remove(libraryFolderPathKey);
        } else {
          // Validate that the saved path exists
          final dir = Directory(actualPath);
          if (await dir.exists()) {
            EnvironmentLogger().d(
              'StoragePathUtils: Using saved library path: $actualPath',
            );
            return actualPath;
          } else {
            EnvironmentLogger().w(
              'StoragePathUtils: Saved library path does not exist: $actualPath, falling back to default',
            );
            await prefs.remove(libraryFolderPathKey);
          }
        }
      }

      // Use default path if no library path is set
      final defaultPath = await getDefaultAudiobookPath();
      EnvironmentLogger().d(
        'StoragePathUtils: Using default path for library: $defaultPath',
      );
      return defaultPath;
    } on Exception catch (e) {
      EnvironmentLogger().e(
        'StoragePathUtils: Error getting library path, using fallback',
        error: e,
      );
      return defaultAudiobookPath;
    }
  }

  /// Sets the library folder path.
  ///
  /// The [path] parameter is the path to save.
  Future<void> setLibraryFolderPath(String path) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(libraryFolderPathKey, path);
      EnvironmentLogger().d(
        'StoragePathUtils: Saved library folder path: $path',
      );
    } on Exception catch (e) {
      EnvironmentLogger().e(
        'StoragePathUtils: Failed to save library folder path',
        error: e,
      );
    }
  }

  /// Gets all library folders for scanning.
  ///
  /// Returns a list of folder paths. If no folders are configured,
  /// returns a list with the default library folder path.
  Future<List<String>> getLibraryFolders() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final foldersJson = prefs.getString(libraryFoldersKey);

      if (foldersJson != null && foldersJson.isNotEmpty) {
        try {
          // Parse JSON array of folder paths
          final folders = <String>[];
          final decoded = foldersJson.split(',');
          for (final folder in decoded) {
            final trimmed = folder.trim();
            if (trimmed.isNotEmpty) {
              // Keep content URIs as-is - scanner will use ContentResolver
              if (isContentUri(trimmed)) {
                folders.add(trimmed);
                EnvironmentLogger().d(
                  'StoragePathUtils: Keeping content URI as-is: $trimmed',
                );
              } else {
                folders.add(trimmed);
              }
            }
          }
          if (folders.isNotEmpty) {
            EnvironmentLogger().d(
              'StoragePathUtils: Found ${folders.length} library folders',
            );
            // Update saved folders with converted paths
            await _saveLibraryFolders(folders);
            return folders;
          }
        } on Exception catch (e) {
          EnvironmentLogger().w(
            'StoragePathUtils: Failed to parse library folders, using default',
            error: e,
          );
        }
      }

      // Return default library folder if no folders are configured
      final defaultPath = await getLibraryFolderPath();
      return [defaultPath];
    } on Exception catch (e) {
      EnvironmentLogger().e(
        'StoragePathUtils: Error getting library folders, using default',
        error: e,
      );
      final defaultPath = await getLibraryFolderPath();
      return [defaultPath];
    }
  }

  /// Adds a library folder to the list.
  ///
  /// The [path] parameter is the path to add.
  /// Returns true if the folder was added, false if it already exists.
  Future<bool> addLibraryFolder(String path) async {
    try {
      final folders = await getLibraryFolders();
      if (folders.contains(path)) {
        EnvironmentLogger().d(
          'StoragePathUtils: Library folder already exists: $path',
        );
        return false;
      }

      folders.add(path);
      await _saveLibraryFolders(folders);
      EnvironmentLogger().d(
        'StoragePathUtils: Added library folder: $path',
      );
      return true;
    } on Exception catch (e) {
      EnvironmentLogger().e(
        'StoragePathUtils: Failed to add library folder',
        error: e,
      );
      return false;
    }
  }

  /// Removes a library folder from the list.
  ///
  /// The [path] parameter is the path to remove.
  /// Returns true if the folder was removed, false if it wasn't found.
  Future<bool> removeLibraryFolder(String path) async {
    try {
      final folders = await getLibraryFolders();
      if (!folders.contains(path)) {
        EnvironmentLogger().d(
          'StoragePathUtils: Library folder not found: $path',
        );
        return false;
      }

      folders.remove(path);
      await _saveLibraryFolders(folders);
      EnvironmentLogger().d(
        'StoragePathUtils: Removed library folder: $path',
      );
      return true;
    } on Exception catch (e) {
      EnvironmentLogger().e(
        'StoragePathUtils: Failed to remove library folder',
        error: e,
      );
      return false;
    }
  }

  /// Saves the list of library folders to SharedPreferences.
  Future<void> _saveLibraryFolders(List<String> folders) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      // Store as comma-separated string (simple approach)
      final foldersJson = folders.join(',');
      await prefs.setString(libraryFoldersKey, foldersJson);
    } on Exception catch (e) {
      EnvironmentLogger().e(
        'StoragePathUtils: Failed to save library folders',
        error: e,
      );
    }
  }

  /// Converts a content URI to a file system path.
  ///
  /// On Android, SAF (Storage Access Framework) returns URIs like
  /// `content://com.android.externalstorage.documents/tree/primary%3AJabookAudio`.
  /// This method converts such URIs to actual file paths like `/storage/emulated/0/JabookAudio`.
  ///
  /// The [uriString] parameter is the URI string to convert.
  ///
  /// Returns the file system path, or the original string if conversion is not needed/possible.
  static String? convertUriToPath(String uriString) {
    if (!uriString.startsWith('content://')) {
      // Not a content URI, return as-is
      return uriString;
    }

    try {
      // Handle external storage URIs
      // Format: content://com.android.externalstorage.documents/tree/primary%3APath
      if (uriString.contains('externalstorage.documents')) {
        // Extract the path part after 'tree/'
        final treeIndex = uriString.indexOf('/tree/');
        if (treeIndex != -1) {
          var pathPart = uriString.substring(treeIndex + 6); // Skip '/tree/'

          // Decode URL encoding (%3A -> :)
          pathPart = Uri.decodeComponent(pathPart);

          // Handle 'primary:' prefix -> /storage/emulated/0/
          if (pathPart.startsWith('primary:')) {
            final actualPath = pathPart.substring(8); // Skip 'primary:'
            return '/storage/emulated/0/$actualPath';
          }

          // Handle other storage IDs (e.g., 'XXXX-XXXX:')
          // For now, return null as we can't reliably convert these
          return null;
        }
      }

      // For other content URIs, we can't convert them to file paths
      return null;
    } on Exception {
      // If conversion fails, return null
      return null;
    }
  }

  /// Checks if a path is a content URI.
  ///
  /// The [path] parameter is the path to check.
  ///
  /// Returns true if the path is a content URI, false otherwise.
  static bool isContentUri(String path) => path.startsWith('content://');
}
