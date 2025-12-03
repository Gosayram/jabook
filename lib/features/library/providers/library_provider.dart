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

import 'package:jabook/core/domain/library/entities/local_audiobook_group.dart';
import 'package:riverpod/legacy.dart';

/// Provider for storing library audiobook groups.
///
/// This provider persists the scanned library state across tab switches
/// and app lifecycle events.
final libraryGroupsProvider =
    StateNotifierProvider<LibraryGroupsNotifier, List<LocalAudiobookGroup>>(
  (ref) => LibraryGroupsNotifier(),
);

/// Notifier for managing library groups state.
class LibraryGroupsNotifier extends StateNotifier<List<LocalAudiobookGroup>> {
  /// Creates a new LibraryGroupsNotifier instance.
  LibraryGroupsNotifier() : super([]);

  /// Updates the library groups.
  // ignore: use_setters_to_change_properties
  void updateGroups(List<LocalAudiobookGroup> groups) {
    state = groups;
  }

  /// Adds new groups to the existing list.
  void addGroups(List<LocalAudiobookGroup> groups) {
    final existingPaths = state.map((g) => g.groupPath).toSet();
    final newGroups =
        groups.where((g) => !existingPaths.contains(g.groupPath)).toList();
    if (newGroups.isNotEmpty) {
      state = [...state, ...newGroups];
    }
  }

  /// Clears all groups.
  void clear() {
    state = [];
  }

  /// Removes a specific group.
  void removeGroup(LocalAudiobookGroup group) {
    state = state.where((g) => g.groupPath != group.groupPath).toList();
  }
}

/// Provider for scanning state.
final isScanningProvider = StateProvider<bool>((ref) => false);
