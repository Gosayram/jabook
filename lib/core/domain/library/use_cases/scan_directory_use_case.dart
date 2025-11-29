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
import 'package:jabook/core/domain/library/repositories/library_repository.dart';

/// Use case for scanning a specific directory.
///
/// This use case scans a directory for audiobook files and groups them.
class ScanDirectoryUseCase {
  /// Creates a new ScanDirectoryUseCase instance.
  ScanDirectoryUseCase(this._repository);

  final LibraryRepository _repository;

  /// Executes the scan directory use case.
  ///
  /// The [directoryPath] parameter is the path to the directory to scan.
  /// The [recursive] parameter determines whether to scan subdirectories.
  ///
  /// Returns a list of LocalAudiobookGroup instances found in the directory.
  Future<List<LocalAudiobookGroup>> call(
    String directoryPath, {
    bool recursive = false,
  }) =>
      _repository.scanDirectoryGrouped(
        directoryPath,
        recursive: recursive,
      );
}
