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

import 'package:jabook/core/domain/library/entities/local_audiobook.dart';
import 'package:jabook/core/domain/library/entities/local_audiobook_group.dart';

/// Repository interface for library operations.
///
/// This repository provides methods for scanning and managing local audiobook library.
abstract class LibraryRepository {
  /// Scans the default audiobook directory for audio files.
  ///
  /// Returns a list of LocalAudiobook instances found in the default directory.
  Future<List<LocalAudiobook>> scanDefaultDirectory();

  /// Scans the default audiobook directory and groups files by folders.
  ///
  /// Returns a list of LocalAudiobookGroup instances found in the default directory.
  Future<List<LocalAudiobookGroup>> scanDefaultDirectoryGrouped();

  /// Scans a specific directory for audio files.
  ///
  /// The [directoryPath] parameter is the path to the directory to scan.
  /// The [recursive] parameter determines whether to scan subdirectories.
  ///
  /// Returns a list of LocalAudiobook instances found in the directory.
  Future<List<LocalAudiobook>> scanDirectory(
    String directoryPath, {
    bool recursive = false,
  });

  /// Scans a specific directory and groups files by folders.
  ///
  /// The [directoryPath] parameter is the path to the directory to scan.
  /// The [recursive] parameter determines whether to scan subdirectories.
  ///
  /// Returns a list of LocalAudiobookGroup instances found in the directory.
  Future<List<LocalAudiobookGroup>> scanDirectoryGrouped(
    String directoryPath, {
    bool recursive = false,
  });

  /// Scans multiple directories and combines results.
  ///
  /// The [directoryPaths] parameter is a list of directory paths to scan.
  /// The [recursive] parameter determines whether to scan subdirectories.
  ///
  /// Returns a list of LocalAudiobookGroup instances found in all directories.
  Future<List<LocalAudiobookGroup>> scanMultipleDirectories(
    List<String> directoryPaths, {
    bool recursive = true,
  });

  /// Scans all library folders configured in the app.
  ///
  /// Returns a list of LocalAudiobookGroup instances found in all library folders.
  Future<List<LocalAudiobookGroup>> scanAllLibraryFolders();

  /// Scans for audio files using MediaStore API (Android 10+).
  ///
  /// This method uses MediaStore to find audio files without requiring
  /// storage permissions. It's useful as a fallback when permissions don't work.
  ///
  /// Returns a list of LocalAudiobook instances found via MediaStore.
  Future<List<LocalAudiobook>> scanViaMediaStore();
}
