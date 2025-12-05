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

import 'dart:async';
import 'dart:io';

import 'package:device_info_plus/device_info_plus.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/core/di/providers/utils_providers.dart';
import 'package:jabook/core/library/library_folder_permission_manager.dart';
import 'package:jabook/core/library/library_migration_service.dart';
import 'package:jabook/core/utils/content_uri_service.dart';
import 'package:jabook/core/utils/file_picker_utils.dart' as file_picker_utils;
import 'package:jabook/core/utils/storage_path_utils.dart';
import 'package:jabook/features/settings/presentation/widgets/dialogs/folder_dialogs.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Handlers for folder operations in settings screen.
class FolderHandlers {
  FolderHandlers._();

  /// Selects download folder.
  static Future<void> selectDownloadFolder(
    BuildContext context, {
    required bool mounted,
    required WidgetRef ref,
    required void Function(String path) onDownloadFolderChanged,
    required void Function() onStateUpdate,
  }) async {
    if (!mounted) return;

    // Save context and localizations before any async operations
    final savedContext = context;
    final localizations = AppLocalizations.of(savedContext);
    final messenger = ScaffoldMessenger.of(savedContext);

    // Check Android version to show instruction dialog for Android 13+
    if (Platform.isAndroid) {
      final androidInfo = await DeviceInfoPlugin().androidInfo;
      final sdkInt = androidInfo.version.sdkInt;

      // Show hint dialog for Android 11+ (SAF is required)
      if (sdkInt >= 30) {
        if (!mounted) return;
        // Show instruction dialog with checkbox hint
        // Use savedContext which was captured before async operations
        final dialogTitle = localizations?.safFolderPickerHintTitle ??
            'Important: Check the checkbox';
        final dialogMessage = localizations?.safFolderPickerHintMessage ??
            'When selecting a folder, please make sure to check the \'Allow access to this folder\' checkbox in the file picker dialog. Without this checkbox, the app cannot access the selected folder.';
        final cancelText = localizations?.cancel ?? 'Cancel';

        // Check if context is still mounted before using it
        if (!savedContext.mounted) return;
        final shouldProceed = await showDialog<bool>(
          context: savedContext,
          builder: (dialogContext) => AlertDialog(
            title: Row(
              children: [
                Icon(
                  Icons.info_outline,
                  color: Theme.of(dialogContext).colorScheme.primary,
                ),
                const SizedBox(width: 8),
                Expanded(child: Text(dialogTitle)),
              ],
            ),
            content: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    dialogMessage,
                    style: Theme.of(dialogContext).textTheme.bodyMedium,
                  ),
                  if (sdkInt >= 30) ...[
                    const SizedBox(height: 16),
                    Text(
                      localizations?.safAndroidDataObbWarning ??
                          'Note: Access to Android/data and Android/obb folders is blocked on Android 11+ devices with security updates from March 2024. Please select a different folder.',
                      style: Theme.of(dialogContext)
                          .textTheme
                          .bodySmall
                          ?.copyWith(
                            fontStyle: FontStyle.italic,
                            color: Theme.of(dialogContext).colorScheme.error,
                          ),
                    ),
                  ],
                ],
              ),
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(dialogContext).pop(false),
                child: Text(cancelText),
              ),
              ElevatedButton.icon(
                onPressed: () => Navigator.of(dialogContext).pop(true),
                icon: const Icon(Icons.folder_open),
                label: const Text('Continue'),
              ),
            ],
          ),
        );

        if (shouldProceed != true) {
          return; // User cancelled
        }
      }
    }

    // Open folder picker
    try {
      final selectedPath = await file_picker_utils.pickDirectory();

      if (selectedPath != null) {
        // Validate folder accessibility
        // For content:// URIs, use ContentUriService; for file paths, use Directory
        final isContentUri = StoragePathUtils.isContentUri(selectedPath);
        var isAccessible = false;

        if (isContentUri) {
          // Check access via ContentResolver for content URIs
          // Use retry logic as some devices may have delayed permission persistence
          try {
            final contentUriService = ContentUriService();
            var hasAccess =
                await contentUriService.checkUriAccess(selectedPath);

            // Retry after delay if first check fails (some devices have delayed persistence)
            if (!hasAccess) {
              debugPrint('First access check failed, retrying after 500ms...');
              await Future.delayed(const Duration(milliseconds: 500));
              hasAccess = await contentUriService.checkUriAccess(selectedPath);
            }

            // Second retry with longer delay if still no access
            if (!hasAccess) {
              debugPrint('Second access check failed, retrying after 1s...');
              await Future.delayed(const Duration(milliseconds: 1000));
              hasAccess = await contentUriService.checkUriAccess(selectedPath);
            }

            isAccessible = hasAccess;

            if (!isAccessible) {
              if (mounted) {
                messenger.showSnackBar(
                  SnackBar(
                    content: Text(
                      localizations?.safNoAccessMessage ??
                          'No access to selected folder. Please check the \'Allow access to this folder\' checkbox in the file picker and try again.',
                    ),
                    backgroundColor: Colors.orange,
                    duration: const Duration(seconds: 5),
                  ),
                );
              }
              return;
            }
          } on Exception catch (e) {
            // Log error but continue - permission might still work
            debugPrint('Error checking content URI access: $e');
            // Assume accessible if check fails (might be timing issue)
            isAccessible = true;
          }
        } else {
          // Check access via Directory for file paths
          try {
            final dir = Directory(selectedPath);
            isAccessible = await dir.exists();
            if (!isAccessible) {
              if (mounted) {
                messenger.showSnackBar(
                  const SnackBar(
                    content: Text(
                      'Selected folder is not accessible. Please try again.',
                    ),
                    backgroundColor: Colors.orange,
                    duration: Duration(seconds: 3),
                  ),
                );
              }
              return;
            }
          } on Exception {
            // If we can't check, still try to save - might be SAF URI
            isAccessible = true; // Assume accessible if check fails
          }
        }

        // Save selected path using StoragePathUtils
        final storageUtils = ref.read(storagePathUtilsProvider);
        await storageUtils.setDownloadFolderPath(selectedPath);

        if (mounted) {
          onDownloadFolderChanged(selectedPath);
          onStateUpdate();
          messenger.showSnackBar(
            SnackBar(
              content: Row(
                children: [
                  const Icon(Icons.check_circle, color: Colors.white),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(
                          localizations?.folderSelectedSuccessMessage ??
                              'Download folder selected successfully',
                          style: const TextStyle(fontWeight: FontWeight.bold),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          selectedPath,
                          style: const TextStyle(fontSize: 12),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              backgroundColor: Colors.green,
            ),
          );
        }
      } else {
        // User cancelled folder selection
        if (mounted) {
          messenger.showSnackBar(
            SnackBar(
              content: Text(localizations?.folderSelectionCancelledMessage ??
                  'Folder selection cancelled'),
              duration: const Duration(seconds: 1),
            ),
          );
        }
      }
    } on Exception catch (e) {
      if (mounted) {
        messenger.showSnackBar(
          SnackBar(
            content: Row(
              children: [
                const Icon(Icons.error, color: Colors.white),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Error selecting folder: ${e.toString()}',
                  ),
                ),
              ],
            ),
            backgroundColor: Colors.red,
            duration: const Duration(seconds: 3),
          ),
        );
      }
    }
  }

  /// Selects library folder.
  static Future<void> selectLibraryFolder(
    BuildContext context, {
    required bool mounted,
    required WidgetRef ref,
    required String? currentLibraryFolderPath,
    required Future<void> Function() onLoadLibraryFolders,
    required void Function() onStateUpdate,
    required Future<void> Function(BuildContext, String, String)
        onMigrateFolder,
  }) async {
    if (!mounted) return;

    final savedContext = context;
    final localizations = AppLocalizations.of(savedContext);
    final messenger = ScaffoldMessenger.of(savedContext);

    // Show hint dialog for Android 11+ (SAF is required)
    if (Platform.isAndroid) {
      final androidInfo = await DeviceInfoPlugin().androidInfo;
      final sdkInt = androidInfo.version.sdkInt;

      if (sdkInt >= 30) {
        if (!mounted) return;
        if (!savedContext.mounted) return;
        // ignore: use_build_context_synchronously
        // Context is safe here because we check savedContext.mounted before use
        final shouldProceed = await showSafFolderPickerHint(savedContext);

        if (!mounted) return;
        if (!shouldProceed) {
          return; // User cancelled
        }
      }
    }

    // Open folder picker
    try {
      final selectedPath = await file_picker_utils.pickDirectory();

      if (selectedPath != null) {
        // Validate folder accessibility
        // For content:// URIs, use ContentUriService; for file paths, use Directory
        final isContentUri = StoragePathUtils.isContentUri(selectedPath);
        var isAccessible = false;

        if (isContentUri) {
          // Check access via ContentResolver for content URIs
          // Use retry logic as some devices may have delayed permission persistence
          try {
            final contentUriService = ContentUriService();
            var hasAccess =
                await contentUriService.checkUriAccess(selectedPath);

            // Retry after delay if first check fails (some devices have delayed persistence)
            if (!hasAccess) {
              debugPrint('First access check failed, retrying after 500ms...');
              await Future.delayed(const Duration(milliseconds: 500));
              hasAccess = await contentUriService.checkUriAccess(selectedPath);
            }

            // Second retry with longer delay if still no access
            if (!hasAccess) {
              debugPrint('Second access check failed, retrying after 1s...');
              await Future.delayed(const Duration(milliseconds: 1000));
              hasAccess = await contentUriService.checkUriAccess(selectedPath);
            }

            isAccessible = hasAccess;

            if (!isAccessible) {
              if (mounted) {
                messenger.showSnackBar(
                  SnackBar(
                    content: Text(
                      localizations?.safNoAccessMessage ??
                          'No access to selected folder. Please check the \'Allow access to this folder\' checkbox in the file picker and try again.',
                    ),
                    backgroundColor: Colors.orange,
                    duration: const Duration(seconds: 5),
                  ),
                );
              }
              return;
            }
          } on Exception catch (e) {
            // Log error but continue - permission might still work
            debugPrint('Error checking content URI access: $e');
            // Assume accessible if check fails (might be timing issue)
            isAccessible = true;
          }
        } else {
          // Check access via Directory for file paths
          try {
            final dir = Directory(selectedPath);
            isAccessible = await dir.exists();
            if (!isAccessible) {
              if (mounted) {
                messenger.showSnackBar(
                  const SnackBar(
                    content: Text(
                      'Selected folder is not accessible. Please try again.',
                    ),
                    backgroundColor: Colors.orange,
                    duration: Duration(seconds: 3),
                  ),
                );
              }
              return;
            }
          } on Exception {
            // If we can't check, still try to save - might be SAF URI
            isAccessible = true; // Assume accessible if check fails
          }
        }

        // Check if we should migrate files
        final oldPath = currentLibraryFolderPath;
        if (oldPath != null && oldPath != selectedPath) {
          if (!mounted) return;
          if (!savedContext.mounted) return;
          // ignore: use_build_context_synchronously
          // Context is safe here because we check savedContext.mounted before use
          final shouldMigrate =
              await showMigrateLibraryFolderDialog(savedContext);

          if (!mounted) return;
          if (shouldMigrate) {
            // Perform migration
            if (!savedContext.mounted) return;
            // ignore: use_build_context_synchronously
            // Context is safe here because we check savedContext.mounted before use
            await onMigrateFolder(savedContext, oldPath, selectedPath);
          }
        }

        // Save selected path
        final storageUtils = ref.read(storagePathUtilsProvider);
        await storageUtils.setLibraryFolderPath(selectedPath);
        await onLoadLibraryFolders();

        if (mounted) {
          onStateUpdate();
          messenger.showSnackBar(
            SnackBar(
              content: Row(
                children: [
                  const Icon(Icons.check_circle, color: Colors.white),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(
                          localizations?.libraryFolderSelectedSuccessMessage ??
                              'Library folder selected successfully',
                          style: const TextStyle(fontWeight: FontWeight.bold),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          selectedPath,
                          style: const TextStyle(fontSize: 12),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              backgroundColor: Colors.green,
            ),
          );
        }
      } else {
        // User cancelled folder selection
        if (mounted) {
          messenger.showSnackBar(
            SnackBar(
              content: Text(localizations?.folderSelectionCancelledMessage ??
                  'Folder selection cancelled'),
              duration: const Duration(seconds: 1),
            ),
          );
        }
      }
    } on Exception catch (e) {
      if (mounted) {
        messenger.showSnackBar(
          SnackBar(
            content: Row(
              children: [
                const Icon(Icons.error, color: Colors.white),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Error selecting folder: ${e.toString()}',
                  ),
                ),
              ],
            ),
            backgroundColor: Colors.red,
            duration: const Duration(seconds: 3),
          ),
        );
      }
    }
  }

  /// Adds a library folder.
  static Future<void> addLibraryFolder(
    BuildContext context, {
    required bool mounted,
    required WidgetRef ref,
    required List<String> currentLibraryFolders,
    required Future<void> Function() onLoadLibraryFolders,
    required void Function() onStateUpdate,
  }) async {
    if (!mounted) return;

    final savedContext = context;
    final localizations = AppLocalizations.of(savedContext);
    final messenger = ScaffoldMessenger.of(savedContext);

    // Show hint dialog for Android 11+ (SAF is required)
    if (Platform.isAndroid) {
      final androidInfo = await DeviceInfoPlugin().androidInfo;
      final sdkInt = androidInfo.version.sdkInt;

      if (sdkInt >= 30) {
        if (!mounted) return;
        // Check if context is still mounted before using it
        if (!savedContext.mounted) return;
        final shouldProceed = await showSafFolderPickerHint(savedContext);

        if (!mounted) return;
        if (!shouldProceed) {
          return; // User cancelled
        }
      }
    }

    try {
      final selectedPath = await file_picker_utils.pickDirectory();

      if (selectedPath != null) {
        // Validate folder accessibility
        // For content:// URIs, use ContentUriService; for file paths, use Directory
        final isContentUri = StoragePathUtils.isContentUri(selectedPath);
        var isAccessible = false;

        if (isContentUri) {
          // Check access via ContentResolver for content URIs
          // Use retry logic as some devices may have delayed permission persistence
          try {
            final contentUriService = ContentUriService();
            var hasAccess =
                await contentUriService.checkUriAccess(selectedPath);

            // Retry after delay if first check fails (some devices have delayed persistence)
            if (!hasAccess) {
              debugPrint('First access check failed, retrying after 500ms...');
              await Future.delayed(const Duration(milliseconds: 500));
              hasAccess = await contentUriService.checkUriAccess(selectedPath);
            }

            // Second retry with longer delay if still no access
            if (!hasAccess) {
              debugPrint('Second access check failed, retrying after 1s...');
              await Future.delayed(const Duration(milliseconds: 1000));
              hasAccess = await contentUriService.checkUriAccess(selectedPath);
            }

            isAccessible = hasAccess;

            if (!isAccessible) {
              if (mounted) {
                messenger.showSnackBar(
                  SnackBar(
                    content: Text(
                      localizations?.safNoAccessMessage ??
                          'No access to selected folder. Please check the \'Allow access to this folder\' checkbox in the file picker and try again.',
                    ),
                    backgroundColor: Colors.orange,
                    duration: const Duration(seconds: 5),
                  ),
                );
              }
              return;
            }
          } on Exception catch (e) {
            // Log error but continue - permission might still work
            debugPrint('Error checking content URI access: $e');
            // Assume accessible if check fails (might be timing issue)
            isAccessible = true;
          }
        } else {
          // Check access via Directory for file paths
          try {
            final dir = Directory(selectedPath);
            isAccessible = await dir.exists();
            if (!isAccessible) {
              if (mounted) {
                messenger.showSnackBar(
                  const SnackBar(
                    content: Text(
                      'Selected folder is not accessible. Please try again.',
                    ),
                    backgroundColor: Colors.orange,
                    duration: Duration(seconds: 3),
                  ),
                );
              }
              return;
            }
          } on Exception {
            // If we can't check, still try to save - might be SAF URI
            isAccessible = true; // Assume accessible if check fails
          }
        }

        // Check if folder already exists
        if (currentLibraryFolders.contains(selectedPath)) {
          if (mounted) {
            messenger.showSnackBar(
              SnackBar(
                content: Text(
                  localizations?.libraryFolderAlreadyExistsMessage ??
                      'This folder is already in the library folders list',
                ),
                backgroundColor: Colors.orange,
                duration: const Duration(seconds: 2),
              ),
            );
          }
          return;
        }

        // Add folder
        final storageUtils = ref.read(storagePathUtilsProvider);
        final added = await storageUtils.addLibraryFolder(selectedPath);
        await onLoadLibraryFolders();

        if (mounted) {
          onStateUpdate();
          if (added) {
            // For content URIs, verify access one more time after adding
            // This ensures the permission was properly persisted
            if (isContentUri) {
              try {
                final contentUriService = ContentUriService();
                final finalCheck =
                    await contentUriService.checkUriAccess(selectedPath);
                if (!finalCheck) {
                  debugPrint(
                      'Warning: Access check failed after adding folder, but folder was added');
                  // Still show success, but log warning
                  // The scanner will handle access errors during scanning
                }
              } on Exception catch (e) {
                debugPrint('Error in final access check: $e');
                // Continue anyway - folder was added
              }
            }

            messenger.showSnackBar(
              SnackBar(
                content: Text(
                  localizations?.libraryFolderAddedSuccessMessage ??
                      'Library folder added successfully. Please refresh the library to scan for new files.',
                ),
                backgroundColor: Colors.green,
                action: SnackBarAction(
                  label: 'OK',
                  textColor: Colors.white,
                  onPressed: () {},
                ),
              ),
            );
          } else {
            messenger.showSnackBar(
              SnackBar(
                content: Text(
                  localizations?.libraryFolderAlreadyExistsMessage ??
                      'This folder is already in the library folders list',
                ),
                backgroundColor: Colors.orange,
              ),
            );
          }
        }
      }
    } on Exception catch (e) {
      if (mounted) {
        messenger.showSnackBar(
          SnackBar(
            content: Text('Error adding folder: ${e.toString()}'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  /// Removes a library folder.
  static Future<void> removeLibraryFolder(
    BuildContext context,
    String folder, {
    required bool mounted,
    required WidgetRef ref,
    required Future<void> Function() onLoadLibraryFolders,
    required void Function() onStateUpdate,
  }) async {
    if (!mounted) return;

    final localizations = AppLocalizations.of(context);
    final messenger = ScaffoldMessenger.of(context);

    // Confirm removal
    // ignore: use_build_context_synchronously
    // Context is safe here because we check mounted before use
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(
          localizations?.removeLibraryFolderTitle ?? 'Remove Folder?',
        ),
        content: Text(
          localizations?.removeLibraryFolderMessage ??
              'Are you sure you want to remove this folder from the library? '
                  'This will not delete the files, only stop scanning this folder.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: Text(localizations?.cancel ?? 'Cancel'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            style: ElevatedButton.styleFrom(
              backgroundColor: Colors.red,
              foregroundColor: Colors.white,
            ),
            child: Text(localizations?.remove ?? 'Remove'),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    // Remove folder
    final storageUtils = ref.read(storagePathUtilsProvider);
    final removed = await storageUtils.removeLibraryFolder(folder);
    await onLoadLibraryFolders();

    if (mounted) {
      onStateUpdate();
      if (removed) {
        messenger.showSnackBar(
          SnackBar(
            content: Text(
              localizations?.libraryFolderRemovedSuccessMessage ??
                  'Library folder removed successfully',
            ),
            backgroundColor: Colors.green,
          ),
        );
      } else {
        messenger.showSnackBar(
          SnackBar(
            content: Text(
              localizations?.libraryFolderRemoveFailedMessage ??
                  'Failed to remove library folder',
            ),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  /// Builds a library folder item with permission status.
  static Widget buildLibraryFolderItem(
    BuildContext context,
    String folder,
    bool isPrimary,
    AppLocalizations? localizations, {
    required Future<bool> Function(String) checkFolderPermission,
    required void Function(String) onRestorePermission,
    required void Function(String) onRemoveFolder,
  }) =>
      FutureBuilder<bool>(
        future: checkFolderPermission(folder),
        builder: (context, snapshot) {
          final hasPermission = snapshot.data ?? true;
          final isLoading = snapshot.connectionState == ConnectionState.waiting;

          return ListTile(
            leading: Icon(
              isPrimary ? Icons.folder : Icons.folder_outlined,
              color: isPrimary ? Theme.of(context).colorScheme.primary : null,
            ),
            title: Text(
              folder,
              style: TextStyle(
                fontWeight: isPrimary ? FontWeight.bold : FontWeight.normal,
              ),
            ),
            subtitle: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (isPrimary)
                  Text(
                    localizations?.primaryLibraryFolder ?? 'Primary folder',
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                if (!hasPermission && !isLoading)
                  Row(
                    children: [
                      const Icon(
                        Icons.warning,
                        size: 16,
                        color: Colors.orange,
                      ),
                      const SizedBox(width: 4),
                      Text(
                        'Permission lost',
                        style: Theme.of(context).textTheme.bodySmall?.copyWith(
                              color: Colors.orange,
                            ),
                      ),
                    ],
                  ),
              ],
            ),
            trailing: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (!hasPermission && !isLoading)
                  IconButton(
                    icon: const Icon(Icons.refresh),
                    onPressed: () => onRestorePermission(folder),
                    tooltip: 'Restore access',
                  ),
                if (!isPrimary)
                  IconButton(
                    icon: const Icon(Icons.delete),
                    onPressed: () => onRemoveFolder(folder),
                    tooltip: 'Remove folder',
                  ),
              ],
            ),
          );
        },
      );

  /// Checks if a folder has permission.
  static Future<bool> checkFolderPermission(String folder) async {
    try {
      final permissionManager = LibraryFolderPermissionManager();
      return await permissionManager.checkFolderPermission(folder);
    } on Exception {
      return false;
    }
  }

  /// Restores permission for a folder by re-selecting it.
  static Future<void> restoreFolderPermission(
    BuildContext context,
    String oldPath, {
    required bool mounted,
    required WidgetRef ref,
    required Future<void> Function() onLoadLibraryFolders,
    required void Function() onStateUpdate,
  }) async {
    if (!mounted) return;

    final localizations = AppLocalizations.of(context);
    final messenger = ScaffoldMessenger.of(context);

    try {
      // Show hint dialog for Android 11+ (SAF is required)
      if (Platform.isAndroid) {
        final androidInfo = await DeviceInfoPlugin().androidInfo;
        if (androidInfo.version.sdkInt >= 30) {
          // Show hint dialog
          if (!mounted) return;
          if (!context.mounted) return;
          // ignore: use_build_context_synchronously
          // Context is safe here because we check context.mounted before use
          final shouldContinue = await showDialog<bool>(
                context: context,
                builder: (dialogContext) => AlertDialog(
                  title: const Text(
                    'Important: Allow access to this folder',
                  ),
                  content: const Text(
                    'When selecting the folder, please make sure to check the "Allow access to this folder" checkbox at the bottom of the file picker. Without this checkbox, the app will not be able to access the folder.',
                  ),
                  actions: [
                    TextButton(
                      onPressed: () => Navigator.of(dialogContext).pop(false),
                      child: Text(localizations?.cancel ?? 'Cancel'),
                    ),
                    TextButton(
                      onPressed: () => Navigator.of(dialogContext).pop(true),
                      child: const Text('Continue'),
                    ),
                  ],
                ),
              ) ??
              false;

          if (!shouldContinue || !mounted) return;
        }
      }

      // Select folder
      final selectedPath = await file_picker_utils.pickDirectory();
      if (selectedPath == null || !mounted) return;

      // Check if it's a Content URI
      final isContentUri = StoragePathUtils.isContentUri(selectedPath);

      // Verify access
      var isAccessible = false;
      if (isContentUri) {
        try {
          final contentUriService = ContentUriService();
          var hasAccess = await contentUriService.checkUriAccess(selectedPath);

          // Retry logic
          if (!hasAccess) {
            await Future.delayed(const Duration(milliseconds: 500));
            hasAccess = await contentUriService.checkUriAccess(selectedPath);
          }

          if (!hasAccess) {
            await Future.delayed(const Duration(milliseconds: 1000));
            hasAccess = await contentUriService.checkUriAccess(selectedPath);
          }

          isAccessible = hasAccess;
        } on Exception {
          isAccessible = false;
        }
      } else {
        final dir = Directory(selectedPath);
        isAccessible = await dir.exists();
      }

      if (!isAccessible) {
        if (mounted) {
          messenger.showSnackBar(
            SnackBar(
              content: Text(
                localizations?.safNoAccessMessage ??
                    'No access to selected folder. Please check the \'Allow access to this folder\' checkbox in the file picker and try again.',
              ),
              backgroundColor: Colors.orange,
              duration: const Duration(seconds: 5),
            ),
          );
        }
        return;
      }

      // Replace old folder with new one
      final storageUtils = ref.read(storagePathUtilsProvider);
      await storageUtils.removeLibraryFolder(oldPath);
      await storageUtils.addLibraryFolder(selectedPath);
      await onLoadLibraryFolders();

      if (mounted) {
        onStateUpdate();
        messenger.showSnackBar(
          const SnackBar(
            content: Text(
              'Folder access restored successfully',
            ),
            backgroundColor: Colors.green,
            duration: Duration(seconds: 2),
          ),
        );
      }
    } on Exception catch (e) {
      if (mounted) {
        messenger.showSnackBar(
          SnackBar(
            content: Text(
              'Error restoring folder access: ${e.toString()}',
            ),
            backgroundColor: Colors.red,
            duration: const Duration(seconds: 3),
          ),
        );
      }
    }
  }

  /// Migrates library folder from old path to new path.
  static Future<void> migrateLibraryFolder(
    BuildContext context,
    String oldPath,
    String newPath, {
    required bool mounted,
    required void Function() onStateUpdate,
  }) async {
    if (!mounted) return;

    final localizations = AppLocalizations.of(context);

    // Show progress dialog
    if (!mounted) return;
    final dialogContext = context;
    final progressDialog = showDialog(
      context: dialogContext,
      barrierDismissible: false,
      builder: (dialogBuilderContext) => AlertDialog(
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const CircularProgressIndicator(),
            const SizedBox(height: 16),
            Text(
              localizations?.migratingLibraryFolderMessage ??
                  'Migrating files...',
            ),
          ],
        ),
      ),
    );
    unawaited(progressDialog);

    try {
      final migrationService = LibraryMigrationService();
      final result = await migrationService.migrateLibrary(
        oldPath: oldPath,
        newPath: newPath,
      );

      if (!mounted) return;
      final navContext = context;
      // ignore: use_build_context_synchronously
      Navigator.of(navContext).pop(); // Close progress dialog

      if (!mounted) return;
      final messengerContext = context;
      if (result.success) {
        // ignore: use_build_context_synchronously
        ScaffoldMessenger.of(messengerContext).showSnackBar(
          SnackBar(
            content: Text(
              localizations?.migrationCompletedSuccessMessage ??
                  'Migration completed successfully. ${result.filesMoved} files moved.',
            ),
            backgroundColor: Colors.green,
            duration: const Duration(seconds: 3),
          ),
        );
      } else {
        if (!mounted) return;
        final messengerContextForError = context;
        // ignore: use_build_context_synchronously
        ScaffoldMessenger.of(messengerContextForError).showSnackBar(
          SnackBar(
            content: Text(
              localizations?.migrationFailedMessage ??
                  'Migration failed: ${result.error ?? "Unknown error"}',
            ),
            backgroundColor: Colors.red,
            duration: const Duration(seconds: 3),
          ),
        );
      }
    } on Exception catch (e) {
      if (!mounted) return;
      final navContextForError = context;
      // ignore: use_build_context_synchronously
      Navigator.of(navContextForError).pop(); // Close progress dialog
      if (!mounted) return;
      final messengerContextForException = context;
      // ignore: use_build_context_synchronously
      ScaffoldMessenger.of(messengerContextForException).showSnackBar(
        SnackBar(
          content: Text(
            'Migration error: ${e.toString()}',
          ),
          backgroundColor: Colors.red,
        ),
      );
    }
  }
}
