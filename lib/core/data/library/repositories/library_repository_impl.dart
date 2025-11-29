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

import 'package:jabook/core/data/library/datasources/library_local_datasource.dart';
import 'package:jabook/core/domain/library/entities/local_audiobook.dart';
import 'package:jabook/core/domain/library/entities/local_audiobook_group.dart';
import 'package:jabook/core/domain/library/repositories/library_repository.dart';

/// Implementation of LibraryRepository using data sources.
class LibraryRepositoryImpl implements LibraryRepository {
  /// Creates a new LibraryRepositoryImpl instance.
  LibraryRepositoryImpl(this._localDataSource);

  final LibraryLocalDataSource _localDataSource;

  @override
  Future<List<LocalAudiobook>> scanDefaultDirectory() =>
      _localDataSource.scanDefaultDirectory();

  @override
  Future<List<LocalAudiobookGroup>> scanDefaultDirectoryGrouped() =>
      _localDataSource.scanDefaultDirectoryGrouped();

  @override
  Future<List<LocalAudiobook>> scanDirectory(
    String directoryPath, {
    bool recursive = false,
  }) =>
      _localDataSource.scanDirectory(
        directoryPath,
        recursive: recursive,
      );

  @override
  Future<List<LocalAudiobookGroup>> scanDirectoryGrouped(
    String directoryPath, {
    bool recursive = false,
  }) =>
      _localDataSource.scanDirectoryGrouped(
        directoryPath,
        recursive: recursive,
      );

  @override
  Future<List<LocalAudiobookGroup>> scanMultipleDirectories(
    List<String> directoryPaths, {
    bool recursive = true,
  }) =>
      _localDataSource.scanMultipleDirectories(
        directoryPaths,
        recursive: recursive,
      );

  @override
  Future<List<LocalAudiobookGroup>> scanAllLibraryFolders() =>
      _localDataSource.scanAllLibraryFolders();

  @override
  Future<List<LocalAudiobook>> scanViaMediaStore() =>
      _localDataSource.scanViaMediaStore();
}
