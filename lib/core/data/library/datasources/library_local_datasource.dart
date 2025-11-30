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

import 'package:jabook/core/data/library/mappers/library_mapper.dart';
import 'package:jabook/core/domain/library/entities/local_audiobook.dart';
import 'package:jabook/core/domain/library/entities/local_audiobook_group.dart';
import 'package:jabook/core/library/audiobook_library_scanner.dart';

/// Local data source for library operations.
///
/// This class wraps AudiobookLibraryScanner to provide a clean interface
/// for library scanning operations that interact with the local file system.
abstract class LibraryLocalDataSource {
  /// Scans the default audiobook directory for audio files.
  Future<List<LocalAudiobook>> scanDefaultDirectory();

  /// Scans the default audiobook directory and groups files by folders.
  Future<List<LocalAudiobookGroup>> scanDefaultDirectoryGrouped();

  /// Scans a specific directory for audio files.
  Future<List<LocalAudiobook>> scanDirectory(
    String directoryPath, {
    bool recursive = false,
  });

  /// Scans a specific directory and groups files by folders.
  Future<List<LocalAudiobookGroup>> scanDirectoryGrouped(
    String directoryPath, {
    bool recursive = false,
  });

  /// Scans multiple directories and combines results.
  Future<List<LocalAudiobookGroup>> scanMultipleDirectories(
    List<String> directoryPaths, {
    bool recursive = true,
  });

  /// Scans all library folders configured in the app.
  Future<List<LocalAudiobookGroup>> scanAllLibraryFolders();

  /// Scans for audio files using MediaStore API (Android 10+).
  Future<List<LocalAudiobook>> scanViaMediaStore();
}

/// Implementation of LibraryLocalDataSource using AudiobookLibraryScanner.
class LibraryLocalDataSourceImpl implements LibraryLocalDataSource {
  /// Creates a new LibraryLocalDataSourceImpl instance.
  LibraryLocalDataSourceImpl(this._scanner);

  final AudiobookLibraryScanner _scanner;

  @override
  Future<List<LocalAudiobook>> scanDefaultDirectory() async {
    final oldList = await _scanner.scanDefaultDirectory();
    return LibraryMapper.toDomainList(oldList);
  }

  @override
  Future<List<LocalAudiobookGroup>> scanDefaultDirectoryGrouped() async {
    final oldList = await _scanner.scanDefaultDirectoryGrouped();
    return LibraryMapper.toDomainGroupList(oldList);
  }

  @override
  Future<List<LocalAudiobook>> scanDirectory(
    String directoryPath, {
    bool recursive = false,
  }) async {
    final oldList = await _scanner.scanDirectory(
      directoryPath,
      recursive: recursive,
    );
    return LibraryMapper.toDomainList(oldList);
  }

  @override
  Future<List<LocalAudiobookGroup>> scanDirectoryGrouped(
    String directoryPath, {
    bool recursive = false,
  }) async {
    final oldList = await _scanner.scanDirectoryGrouped(
      directoryPath,
      recursive: recursive,
    );
    return LibraryMapper.toDomainGroupList(oldList);
  }

  @override
  Future<List<LocalAudiobookGroup>> scanMultipleDirectories(
    List<String> directoryPaths, {
    bool recursive = true,
  }) async {
    final oldList = await _scanner.scanMultipleDirectories(
      directoryPaths,
      recursive: recursive,
    );
    return LibraryMapper.toDomainGroupList(oldList);
  }

  @override
  Future<List<LocalAudiobookGroup>> scanAllLibraryFolders() async {
    final oldList = await _scanner.scanAllLibraryFolders();
    return LibraryMapper.toDomainGroupList(oldList);
  }

  @override
  Future<List<LocalAudiobook>> scanViaMediaStore() async {
    final oldList = await _scanner.scanViaMediaStore();
    return LibraryMapper.toDomainList(oldList);
  }
}
