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

import 'package:jabook/core/utils/content_uri_service.dart';
import 'package:jabook/core/utils/storage_path_utils.dart';

/// Manager for library folder permissions.
///
/// This class provides methods to validate, check, and restore permissions
/// for library folders, especially for Content URI paths.
class LibraryFolderPermissionManager {
  /// Creates a new LibraryFolderPermissionManager instance.
  LibraryFolderPermissionManager({
    StoragePathUtils? storageUtils,
    ContentUriService? contentUriService,
  })  : _storageUtils = storageUtils ?? StoragePathUtils(),
        _contentUriService = contentUriService ?? ContentUriService();

  final StoragePathUtils _storageUtils;
  final ContentUriService _contentUriService;

  /// Validates access to all library folders.
  ///
  /// Returns a map of folder paths to boolean values indicating access status.
  Future<Map<String, bool>> validateAllFolderPermissions() async {
    final folders = await _storageUtils.getLibraryFolders();
    final results = <String, bool>{};

    for (final folder in folders) {
      if (StoragePathUtils.isContentUri(folder)) {
        // Check access via ContentUriService
        try {
          final hasAccess = await _contentUriService.checkUriAccess(folder);
          results[folder] = hasAccess;
        } on Exception {
          results[folder] = false;
        }
      } else {
        // For regular paths, check existence
        final dir = Directory(folder);
        results[folder] = await dir.exists();
      }
    }

    return results;
  }

  /// Gets a list of folders with lost permissions.
  ///
  /// Returns a list of folder paths that don't have access.
  Future<List<String>> getFoldersWithLostPermissions() async {
    final validation = await validateAllFolderPermissions();
    return validation.entries.where((e) => !e.value).map((e) => e.key).toList();
  }

  /// Checks if a specific folder has access.
  ///
  /// The [folderPath] parameter is the path to check.
  ///
  /// Returns true if the folder has access, false otherwise.
  Future<bool> checkFolderPermission(String folderPath) async {
    if (StoragePathUtils.isContentUri(folderPath)) {
      try {
        return await _contentUriService.checkUriAccess(folderPath);
      } on Exception {
        return false;
      }
    } else {
      final dir = Directory(folderPath);
      return dir.exists();
    }
  }

  /// Validates permissions for a single folder.
  ///
  /// The [folderPath] parameter is the path to validate.
  ///
  /// Returns true if the folder has valid permissions, false otherwise.
  Future<bool> validateFolderPermission(String folderPath) async =>
      checkFolderPermission(folderPath);
}
