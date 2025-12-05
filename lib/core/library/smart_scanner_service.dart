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

import 'dart:convert';
import 'dart:io';

import 'package:jabook/core/domain/library/entities/local_audiobook_group.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:jabook/core/library/audiobook_library_scanner.dart';
import 'package:jabook/core/library/file_checksum_service.dart';
import 'package:jabook/core/library/folder_filter_service.dart';
import 'package:jabook/core/utils/storage_path_utils.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Represents a folder's last scan state.
class FolderScanState {
  /// Creates FolderScanState from JSON.
  factory FolderScanState.fromJson(Map<String, dynamic> json) =>
      FolderScanState(
        folderPath: json['folderPath'] as String,
        lastScanTime: DateTime.parse(json['lastScanTime'] as String),
        lastModifiedTime: DateTime.parse(json['lastModifiedTime'] as String),
        fileCount: json['fileCount'] as int? ?? 0,
        totalSize: json['totalSize'] as int? ?? 0,
      );

  /// Creates a new FolderScanState instance.
  FolderScanState({
    required this.folderPath,
    required this.lastScanTime,
    required this.lastModifiedTime,
    this.fileCount = 0,
    this.totalSize = 0,
  });

  /// Path of the folder.
  final String folderPath;

  /// Last time the folder was scanned.
  final DateTime lastScanTime;

  /// Last modification time of the folder.
  final DateTime lastModifiedTime;

  /// Number of files in the folder.
  final int fileCount;

  /// Total size of files in bytes.
  final int totalSize;

  /// Converts FolderScanState to JSON.
  Map<String, dynamic> toJson() => {
        'folderPath': folderPath,
        'lastScanTime': lastScanTime.toIso8601String(),
        'lastModifiedTime': lastModifiedTime.toIso8601String(),
        'fileCount': fileCount,
        'totalSize': totalSize,
      };
}

/// Service for smart/incremental scanning of audiobook library.
///
/// This service tracks folder modification times and only scans
/// folders that have been modified since the last scan.
/// Uses file checksums to detect file-level changes for more efficient scanning.
class SmartScannerService {
  /// Creates a new SmartScannerService instance.
  SmartScannerService({
    AudiobookLibraryScanner? scanner,
    StoragePathUtils? storageUtils,
    FolderFilterService? folderFilterService,
    FileChecksumService? checksumService,
  })  : _scanner = scanner ??
            AudiobookLibraryScanner(
              folderFilterService: folderFilterService,
            ),
        _storageUtils = storageUtils ?? StoragePathUtils(),
        _checksumService = checksumService;

  final AudiobookLibraryScanner _scanner;
  final StoragePathUtils _storageUtils;
  final FileChecksumService? _checksumService;
  final StructuredLogger _logger = StructuredLogger();
  static const String _scanStatesKey = 'folder_scan_states';

  /// Scans all library folders incrementally (only changed folders).
  ///
  /// Returns a list of LocalAudiobookGroup instances.
  ///
  /// If [forceScan] is true, all folders will be scanned regardless of modification time.
  /// If [existingGroups] is provided, groups from unchanged folders will be preserved.
  Future<List<LocalAudiobookGroup>> scanIncremental({
    bool forceScan = false,
    List<LocalAudiobookGroup>? existingGroups,
  }) async {
    try {
      final folders = await _storageUtils.getLibraryFolders();
      final scanStates = await _getScanStates();
      final allGroups = <LocalAudiobookGroup>[];
      final updatedStates = <String, FolderScanState>{};
      final scannedFolderPaths = <String>{};

      // Check if scan states are compatible with current library folders
      // This handles cases where library paths changed after app update
      final hasIncompatibleStates = await _checkScanStatesCompatibility(
        folders,
        scanStates,
      );

      // If scan states are incompatible (paths changed after update),
      // clear all states and force full scan
      if (hasIncompatibleStates) {
        await _logger.log(
          level: 'warning',
          subsystem: 'smart_scanner',
          message:
              'Scan states incompatible with current library folders, clearing and forcing full scan',
          extra: {
            'currentFolders': folders,
            'existingStatesCount': scanStates.length,
          },
        );
        await clearScanStates();
        // Force full scan after clearing incompatible states
        return forceFullScan();
      }

      // If no scan states exist, force scan all folders (first run)
      final isFirstRun = scanStates.isEmpty;

      // If existingGroups is null or empty, we need to scan all folders
      // because we can't preserve groups from unchanged folders without existingGroups
      // This handles the case after app update when provider is empty but scan states exist
      final needsFullScan = existingGroups == null || existingGroups.isEmpty;
      final shouldForceScan = forceScan || isFirstRun || needsFullScan;

      if (needsFullScan && !forceScan && !isFirstRun) {
        await _logger.log(
          level: 'info',
          subsystem: 'smart_scanner',
          message:
              'Existing groups empty but scan states exist, forcing full scan to find all books',
          extra: {
            'scanStatesCount': scanStates.length,
            'foldersCount': folders.length,
          },
        );
      }

      for (final folder in folders) {
        try {
          final dir = Directory(folder);
          if (!await dir.exists()) {
            continue;
          }

          // Get folder modification time
          final stat = await dir.stat();
          final lastModified = stat.modified;

          // Check if folder needs scanning
          final existingState = scanStates[folder];
          final needsScan = shouldForceScan ||
              existingState == null ||
              lastModified.isAfter(existingState.lastModifiedTime);

          if (needsScan) {
            await _logger.log(
              level: 'info',
              subsystem: 'smart_scanner',
              message: 'Scanning changed folder',
              extra: {
                'folder': folder,
                'lastModified': lastModified.toIso8601String(),
                'lastScanTime': existingState?.lastScanTime.toIso8601String(),
              },
            );

            // Scan the folder
            final groups = await _scanner.scanDirectoryGrouped(
              folder,
              recursive: true,
            );

            // Calculate folder stats and save checksums for scanned files
            var fileCount = 0;
            var totalSize = 0;
            for (final group in groups) {
              fileCount += group.files.length;
              for (final file in group.files) {
                totalSize += file.fileSize;
                // Save checksum for the file if checksum service is available
                if (_checksumService != null) {
                  try {
                    final checksum = await _checksumService
                        .computeAndSaveChecksum(file.filePath);
                    await _logger.log(
                      level: 'debug',
                      subsystem: 'smart_scanner',
                      message: 'Saved checksum for file',
                      extra: {
                        'file_path': file.filePath,
                        'checksum': checksum,
                      },
                    );
                  } on Exception catch (e) {
                    // Log but don't fail the scan if checksum computation fails
                    await _logger.log(
                      level: 'warning',
                      subsystem: 'smart_scanner',
                      message: 'Failed to save checksum for file',
                      extra: {
                        'file_path': file.filePath,
                        'error': e.toString(),
                      },
                    );
                  }
                }
              }
              // Track which folders were scanned
              scannedFolderPaths.add(folder);
            }

            // Update scan state
            updatedStates[folder] = FolderScanState(
              folderPath: folder,
              lastScanTime: DateTime.now(),
              lastModifiedTime: lastModified,
              fileCount: fileCount,
              totalSize: totalSize,
            );

            allGroups.addAll(groups);
          } else {
            // Use cached groups from previous scan or existing groups
            await _logger.log(
              level: 'debug',
              subsystem: 'smart_scanner',
              message: 'Skipping unchanged folder',
              extra: {
                'folder': folder,
                'lastModified': lastModified.toIso8601String(),
              },
            );

            // If existing groups provided, preserve groups from this unchanged folder
            // Note: If existingGroups was null/empty, shouldForceScan would be true
            // and we wouldn't reach this branch, so existingGroups is guaranteed non-null here
            final groupsFromFolder = existingGroups
                .where((group) => group.groupPath.startsWith(folder))
                .toList();
            if (groupsFromFolder.isNotEmpty) {
              allGroups.addAll(groupsFromFolder);
            }
          }
        } on Exception catch (e) {
          await _logger.log(
            level: 'error',
            subsystem: 'smart_scanner',
            message: 'Failed to scan folder',
            extra: {
              'folder': folder,
              'error': e.toString(),
            },
          );
        }
      }

      // Save updated scan states and clean up old states for removed folders
      await _saveScanStates(updatedStates, currentFolders: folders);

      await _logger.log(
        level: 'info',
        subsystem: 'smart_scanner',
        message: 'Incremental scan completed',
        extra: {
          'foldersScanned': updatedStates.length,
          'foldersSkipped': folders.length - updatedStates.length,
          'totalGroups': allGroups.length,
          'preservedFromExisting': existingGroups != null
              ? allGroups.length - updatedStates.length
              : 0,
        },
      );

      return allGroups;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'smart_scanner',
        message: 'Failed to perform incremental scan',
        extra: {'error': e.toString()},
      );
      // Fallback to full scan
      return _scanner.scanAllLibraryFolders();
    }
  }

  /// Scans only specific folders that have changed.
  ///
  /// The [folderPaths] parameter specifies which folders to check.
  ///
  /// Returns a list of LocalAudiobookGroup instances from changed folders.
  Future<List<LocalAudiobookGroup>> scanChangedFolders(
    List<String> folderPaths,
  ) async {
    try {
      final scanStates = await _getScanStates();
      final allGroups = <LocalAudiobookGroup>[];
      final updatedStates = <String, FolderScanState>{};

      for (final folder in folderPaths) {
        try {
          final dir = Directory(folder);
          if (!await dir.exists()) {
            continue;
          }

          final stat = await dir.stat();
          final lastModified = stat.modified;

          final existingState = scanStates[folder];
          final needsScan = existingState == null ||
              lastModified.isAfter(existingState.lastModifiedTime);

          if (needsScan) {
            final groups = await _scanner.scanDirectoryGrouped(
              folder,
              recursive: true,
            );

            var fileCount = 0;
            var totalSize = 0;
            for (final group in groups) {
              fileCount += group.files.length;
              for (final file in group.files) {
                totalSize += file.fileSize;
              }
            }

            updatedStates[folder] = FolderScanState(
              folderPath: folder,
              lastScanTime: DateTime.now(),
              lastModifiedTime: lastModified,
              fileCount: fileCount,
              totalSize: totalSize,
            );

            allGroups.addAll(groups);
          }
        } on Exception catch (e) {
          await _logger.log(
            level: 'error',
            subsystem: 'smart_scanner',
            message: 'Failed to scan folder',
            extra: {
              'folder': folder,
              'error': e.toString(),
            },
          );
        }
      }

      // Get current folders for cleanup
      final folders = await _storageUtils.getLibraryFolders();
      await _saveScanStates(updatedStates, currentFolders: folders);

      return allGroups;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'smart_scanner',
        message: 'Failed to scan changed folders',
        extra: {'error': e.toString()},
      );
      return [];
    }
  }

  /// Forces a full scan and updates scan states.
  ///
  /// Returns a list of LocalAudiobookGroup instances.
  Future<List<LocalAudiobookGroup>> forceFullScan() async {
    try {
      final folders = await _storageUtils.getLibraryFolders();
      final allGroups = await _scanner.scanMultipleDirectories(folders);
      final updatedStates = <String, FolderScanState>{};

      for (final folder in folders) {
        try {
          final dir = Directory(folder);
          if (await dir.exists()) {
            final stat = await dir.stat();
            final groups =
                allGroups.where((g) => g.groupPath.startsWith(folder));

            var fileCount = 0;
            var totalSize = 0;
            for (final group in groups) {
              fileCount += group.files.length;
              for (final file in group.files) {
                totalSize += file.fileSize;
              }
            }

            updatedStates[folder] = FolderScanState(
              folderPath: folder,
              lastScanTime: DateTime.now(),
              lastModifiedTime: stat.modified,
              fileCount: fileCount,
              totalSize: totalSize,
            );
          }
        } on Exception {
          // Continue with other folders
        }
      }

      await _saveScanStates(updatedStates, currentFolders: folders);

      await _logger.log(
        level: 'info',
        subsystem: 'smart_scanner',
        message: 'Full scan completed',
        extra: {
          'foldersScanned': folders.length,
          'totalGroups': allGroups.length,
        },
      );

      return allGroups;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'smart_scanner',
        message: 'Failed to perform full scan',
        extra: {'error': e.toString()},
      );
      return [];
    }
  }

  /// Gets scan statistics.
  ///
  /// Returns a map with scan statistics.
  Future<Map<String, dynamic>> getScanStatistics() async {
    try {
      final scanStates = await _getScanStates();
      final folders = await _storageUtils.getLibraryFolders();

      var totalFolders = 0;
      var foldersNeedingScan = 0;
      var totalFiles = 0;
      var totalSize = 0;
      DateTime? oldestScan;
      DateTime? newestScan;

      for (final folder in folders) {
        totalFolders++;
        final state = scanStates[folder];

        if (state == null) {
          foldersNeedingScan++;
        } else {
          totalFiles += state.fileCount;
          totalSize += state.totalSize;

          if (oldestScan == null || state.lastScanTime.isBefore(oldestScan)) {
            oldestScan = state.lastScanTime;
          }
          if (newestScan == null || state.lastScanTime.isAfter(newestScan)) {
            newestScan = state.lastScanTime;
          }

          // Check if folder needs rescan
          try {
            final dir = Directory(folder);
            if (await dir.exists()) {
              final stat = await dir.stat();
              if (stat.modified.isAfter(state.lastModifiedTime)) {
                foldersNeedingScan++;
              }
            }
          } on Exception {
            // Ignore errors
          }
        }
      }

      return {
        'totalFolders': totalFolders,
        'foldersScanned': totalFolders - foldersNeedingScan,
        'foldersNeedingScan': foldersNeedingScan,
        'totalFiles': totalFiles,
        'totalSize': totalSize,
        'oldestScan': oldestScan?.toIso8601String(),
        'newestScan': newestScan?.toIso8601String(),
      };
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'smart_scanner',
        message: 'Failed to get scan statistics',
        extra: {'error': e.toString()},
      );
      return {};
    }
  }

  /// Checks if scan states are compatible with current library folders.
  ///
  /// Returns true if states are incompatible (e.g., paths changed after app update).
  /// This happens when:
  /// - There are scan states but none match current library folders
  /// - Some current folders don't have scan states but others do (mixed state)
  Future<bool> _checkScanStatesCompatibility(
    List<String> currentFolders,
    Map<String, FolderScanState> scanStates,
  ) async {
    // If no scan states exist, they are compatible (first run)
    if (scanStates.isEmpty) {
      return false;
    }

    // If no current folders, states are incompatible
    if (currentFolders.isEmpty) {
      return true;
    }

    // Check if any current folder has a scan state
    final currentFoldersSet = currentFolders.toSet();
    final statesFoldersSet = scanStates.keys.toSet();

    // Check if there's any overlap between current folders and state folders
    final hasOverlap =
        currentFoldersSet.intersection(statesFoldersSet).isNotEmpty;

    // If there's no overlap at all, states are incompatible
    // This means library paths changed completely after app update
    if (!hasOverlap) {
      await _logger.log(
        level: 'warning',
        subsystem: 'smart_scanner',
        message: 'No overlap between current folders and scan states',
        extra: {
          'currentFolders': currentFolders,
          'stateFolders': statesFoldersSet.toList(),
        },
      );
      return true;
    }

    // Check if all current folders have states
    // If some folders don't have states, it might indicate paths changed
    final foldersWithoutStates = currentFoldersSet.difference(statesFoldersSet);
    if (foldersWithoutStates.isNotEmpty) {
      // Log warning but don't treat as incompatible
      // New folders might have been added by user
      await _logger.log(
        level: 'info',
        subsystem: 'smart_scanner',
        message:
            'Some current folders do not have scan states (may be newly added)',
        extra: {
          'foldersWithoutStates': foldersWithoutStates.toList(),
        },
      );
    }

    // Check if there are old states for folders that no longer exist
    final oldStatesForRemovedFolders =
        statesFoldersSet.difference(currentFoldersSet);
    if (oldStatesForRemovedFolders.isNotEmpty) {
      await _logger.log(
        level: 'info',
        subsystem: 'smart_scanner',
        message: 'Found scan states for folders that no longer exist',
        extra: {
          'removedFolders': oldStatesForRemovedFolders.toList(),
        },
      );
      // Don't treat as incompatible - just clean up old states later
    }

    return false;
  }

  /// Clears scan states for all folders.
  Future<void> clearScanStates() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.remove(_scanStatesKey);
      await _logger.log(
        level: 'info',
        subsystem: 'smart_scanner',
        message: 'Scan states cleared',
      );
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'smart_scanner',
        message: 'Failed to clear scan states',
        extra: {'error': e.toString()},
      );
    }
  }

  /// Gets all scan states.
  Future<Map<String, FolderScanState>> _getScanStates() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final statesJson = prefs.getStringList(_scanStatesKey) ?? [];

      final states = <String, FolderScanState>{};
      for (final stateJson in statesJson) {
        try {
          // Parse JSON from string
          final stateMap = jsonDecode(stateJson) as Map<String, dynamic>;
          final state = FolderScanState.fromJson(stateMap);
          states[state.folderPath] = state;
        } on Exception {
          // Skip invalid states
          continue;
        }
      }

      return states;
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'smart_scanner',
        message: 'Failed to get scan states',
        extra: {'error': e.toString()},
      );
      return {};
    }
  }

  /// Saves scan states.
  ///
  /// The [states] parameter contains the states to save.
  /// The [currentFolders] parameter is optional and if provided, will be used
  /// to clean up old states for folders that no longer exist.
  Future<void> _saveScanStates(
    Map<String, FolderScanState> states, {
    List<String>? currentFolders,
  }) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final existingStates = await _getScanStates();

      // If currentFolders is provided, remove states for folders that no longer exist
      if (currentFolders != null && currentFolders.isNotEmpty) {
        final currentFoldersSet = currentFolders.toSet();
        final statesToRemove = <String>[];

        for (final statePath in existingStates.keys) {
          // Check if this state path is still in current folders
          // Also check if any current folder starts with this state path (for nested paths)
          final isStillValid = currentFoldersSet.contains(statePath) ||
              currentFoldersSet.any((folder) => folder.startsWith(statePath)) ||
              existingStates.keys.any((existingPath) =>
                  statePath.startsWith(existingPath) &&
                  currentFoldersSet.contains(existingPath));

          if (!isStillValid) {
            statesToRemove.add(statePath);
          }
        }

        if (statesToRemove.isNotEmpty) {
          await _logger.log(
            level: 'info',
            subsystem: 'smart_scanner',
            message: 'Removing scan states for folders that no longer exist',
            extra: {
              'removedStates': statesToRemove,
            },
          );

          statesToRemove.forEach(existingStates.remove);
        }
      }

      // Merge with existing states (after cleanup)
      existingStates.addAll(states);

      // Convert to JSON strings for storage
      final statesJson = existingStates.values
          .map((state) => jsonEncode(state.toJson()))
          .toList();

      await prefs.setStringList(_scanStatesKey, statesJson);
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'smart_scanner',
        message: 'Failed to save scan states',
        extra: {'error': e.toString()},
      );
    }
  }
}
