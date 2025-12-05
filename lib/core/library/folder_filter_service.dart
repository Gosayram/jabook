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

import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:path/path.dart' as path;
import 'package:shared_preferences/shared_preferences.dart';

/// Filter mode for folder filtering.
enum FilterMode {
  /// Blacklist mode - exclude folders matching patterns.
  blacklist,

  /// Whitelist mode - include only folders matching patterns.
  whitelist,
}

/// Service for managing folder filters during library scanning.
///
/// This service provides functionality to exclude or include
/// specific folders based on patterns (regex or simple patterns).
class FolderFilterService {
  /// Creates a new FolderFilterService instance.
  FolderFilterService();

  final StructuredLogger _logger = StructuredLogger();
  static const String _filterModeKey = 'folder_filter_mode';
  static const String _filterPatternsKey = 'folder_filter_patterns';
  static const FilterMode _defaultFilterMode = FilterMode.blacklist;

  /// Gets the current filter mode.
  Future<FilterMode> getFilterMode() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final modeString = prefs.getString(_filterModeKey);
      if (modeString == null) {
        return _defaultFilterMode;
      }
      return FilterMode.values.firstWhere(
        (mode) => mode.name == modeString,
        orElse: () => _defaultFilterMode,
      );
    } on Exception {
      return _defaultFilterMode;
    }
  }

  /// Sets the filter mode.
  Future<void> setFilterMode(FilterMode mode) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_filterModeKey, mode.name);
      await _logger.log(
        level: 'info',
        subsystem: 'folder_filter',
        message: 'Filter mode set',
        extra: {'mode': mode.name},
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'folder_filter',
        message: 'Failed to set filter mode',
        extra: {'error': e.toString()},
      );
    }
  }

  /// Gets all filter patterns.
  Future<List<String>> getFilterPatterns() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final patternsJson = prefs.getStringList(_filterPatternsKey) ?? [];
      return patternsJson;
    } on Exception {
      return [];
    }
  }

  /// Adds a filter pattern.
  ///
  /// The [pattern] parameter is the pattern to add (can be regex or simple pattern).
  Future<bool> addFilterPattern(String pattern) async {
    try {
      final patterns = await getFilterPatterns();
      if (patterns.contains(pattern)) {
        return false;
      }

      patterns.add(pattern);
      await _saveFilterPatterns(patterns);

      await _logger.log(
        level: 'info',
        subsystem: 'folder_filter',
        message: 'Filter pattern added',
        extra: {'pattern': pattern},
      );

      return true;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'folder_filter',
        message: 'Failed to add filter pattern',
        extra: {'error': e.toString()},
      );
      return false;
    }
  }

  /// Removes a filter pattern.
  Future<bool> removeFilterPattern(String pattern) async {
    try {
      final patterns = await getFilterPatterns();
      if (!patterns.contains(pattern)) {
        return false;
      }

      patterns.remove(pattern);
      await _saveFilterPatterns(patterns);

      await _logger.log(
        level: 'info',
        subsystem: 'folder_filter',
        message: 'Filter pattern removed',
        extra: {'pattern': pattern},
      );

      return true;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'folder_filter',
        message: 'Failed to remove filter pattern',
        extra: {'error': e.toString()},
      );
      return false;
    }
  }

  /// Clears all filter patterns.
  Future<void> clearFilterPatterns() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.remove(_filterPatternsKey);
      await _logger.log(
        level: 'info',
        subsystem: 'folder_filter',
        message: 'Filter patterns cleared',
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'folder_filter',
        message: 'Failed to clear filter patterns',
        extra: {'error': e.toString()},
      );
    }
  }

  /// Checks if a folder path should be excluded from scanning.
  ///
  /// The [folderPath] parameter is the path to check.
  ///
  /// Returns true if the folder should be excluded, false otherwise.
  Future<bool> shouldExcludeFolder(String folderPath) async {
    try {
      final patterns = await getFilterPatterns();
      if (patterns.isEmpty) {
        return false; // No filters, don't exclude
      }

      final mode = await getFilterMode();
      final folderName = path.basename(folderPath);
      final normalizedPath = folderPath.replaceAll('\\', '/');

      // Check each pattern
      var matchesAny = false;
      for (final pattern in patterns) {
        try {
          // Try regex first
          final regex = RegExp(pattern, caseSensitive: false);
          if (regex.hasMatch(folderPath) ||
              regex.hasMatch(normalizedPath) ||
              regex.hasMatch(folderName)) {
            matchesAny = true;
            break;
          }
        } on Exception {
          // If regex fails, try simple string matching
          if (folderPath.contains(pattern) ||
              normalizedPath.contains(pattern) ||
              folderName.contains(pattern)) {
            matchesAny = true;
            break;
          }
        }
      }

      // In blacklist mode, exclude if matches
      // In whitelist mode, exclude if doesn't match
      if (mode == FilterMode.blacklist) {
        return matchesAny;
      } else {
        return !matchesAny;
      }
    } on Exception catch (e) {
      await _logger.log(
        level: 'warning',
        subsystem: 'folder_filter',
        message: 'Error checking folder filter',
        extra: {
          'folderPath': folderPath,
          'error': e.toString(),
        },
      );
      return false; // Don't exclude on error
    }
  }

  /// Filters a list of folder paths based on current filter settings.
  ///
  /// The [folderPaths] parameter is the list of paths to filter.
  ///
  /// Returns a list of paths that should be included in scanning.
  Future<List<String>> filterFolders(List<String> folderPaths) async {
    try {
      final filtered = <String>[];
      for (final folderPath in folderPaths) {
        final shouldExclude = await shouldExcludeFolder(folderPath);
        if (!shouldExclude) {
          filtered.add(folderPath);
        } else {
          await _logger.log(
            level: 'debug',
            subsystem: 'folder_filter',
            message: 'Folder excluded from scanning',
            extra: {'folderPath': folderPath},
          );
        }
      }
      return filtered;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'folder_filter',
        message: 'Failed to filter folders',
        extra: {'error': e.toString()},
      );
      return folderPaths; // Return all on error
    }
  }

  /// Checks if a directory should be scanned during recursive scanning.
  ///
  /// The [dir] parameter is the directory to check.
  ///
  /// Returns true if the directory should be scanned, false otherwise.
  Future<bool> shouldScanDirectory(Directory dir) async {
    try {
      return !await shouldExcludeFolder(dir.path);
    } on Exception {
      return true; // Scan on error
    }
  }

  /// Saves filter patterns to SharedPreferences.
  Future<void> _saveFilterPatterns(List<String> patterns) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setStringList(_filterPatternsKey, patterns);
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'folder_filter',
        message: 'Failed to save filter patterns',
        extra: {'error': e.toString()},
      );
    }
  }
}
