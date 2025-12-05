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

import 'package:jabook/core/di/providers/library_providers.dart';
import 'package:jabook/core/domain/library/entities/local_audiobook_group.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:jabook/core/library/library_groups_storage_service.dart';
import 'package:riverpod/legacy.dart';

/// Provider for storing library audiobook groups.
///
/// This provider persists the scanned library state across tab switches
/// and app lifecycle events. Groups are automatically saved to database
/// and restored on app start.
final libraryGroupsProvider =
    StateNotifierProvider<LibraryGroupsNotifier, List<LocalAudiobookGroup>>(
  (ref) {
    final storageService = ref.watch(libraryGroupsStorageServiceProvider);
    return LibraryGroupsNotifier(storageService: storageService);
  },
);

/// Notifier for managing library groups state.
class LibraryGroupsNotifier extends StateNotifier<List<LocalAudiobookGroup>> {
  /// Creates a new LibraryGroupsNotifier instance.
  LibraryGroupsNotifier({
    LibraryGroupsStorageService? storageService,
  })  : _storageService = storageService,
        super([]) {
    // Load groups from database on initialization
    _loadFromDatabase();
  }

  final LibraryGroupsStorageService? _storageService;
  final StructuredLogger _logger = StructuredLogger();
  bool _isLoading = false;

  /// Loads groups from database.
  Future<void> _loadFromDatabase() async {
    if (_storageService == null || _isLoading) {
      return;
    }

    _isLoading = true;
    try {
      final groups = await _storageService.loadGroups();
      if (groups.isNotEmpty) {
        state = groups;
        await _logger.log(
          level: 'info',
          subsystem: 'library_groups_notifier',
          message: 'Loaded groups from database',
          extra: {
            'groups_count': groups.length,
          },
        );
      }
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'library_groups_notifier',
        message: 'Failed to load groups from database',
        extra: {
          'error': e.toString(),
        },
      );
    } finally {
      _isLoading = false;
    }
  }

  /// Saves groups to database.
  Future<void> _saveToDatabase() async {
    if (_storageService == null || state.isEmpty) {
      return;
    }

    try {
      await _storageService.saveGroups(state);
    } on Exception catch (e) {
      await _logger.log(
        level: 'error',
        subsystem: 'library_groups_notifier',
        message: 'Failed to save groups to database',
        extra: {
          'error': e.toString(),
        },
      );
    }
  }

  /// Updates the library groups.
  ///
  /// Automatically saves groups to database after update.
  // ignore: use_setters_to_change_properties
  void updateGroups(List<LocalAudiobookGroup> groups) {
    state = groups;
    _saveToDatabase();
  }

  /// Adds new groups to the existing list.
  ///
  /// Automatically saves groups to database after update.
  void addGroups(List<LocalAudiobookGroup> groups) {
    final existingPaths = state.map((g) => g.groupPath).toSet();
    final newGroups =
        groups.where((g) => !existingPaths.contains(g.groupPath)).toList();
    if (newGroups.isNotEmpty) {
      state = [...state, ...newGroups];
      _saveToDatabase();
    }
  }

  /// Clears all groups.
  ///
  /// Automatically clears groups from database.
  void clear() {
    state = [];
    if (_storageService != null) {
      _storageService.clearGroups();
    }
  }

  /// Removes a specific group.
  ///
  /// Automatically removes group from database.
  void removeGroup(LocalAudiobookGroup group) {
    state = state.where((g) => g.groupPath != group.groupPath).toList();
    if (_storageService != null) {
      _storageService.removeGroup(group.groupPath);
    }
  }
}

/// Provider for scanning state.
final isScanningProvider = StateProvider<bool>((ref) => false);
