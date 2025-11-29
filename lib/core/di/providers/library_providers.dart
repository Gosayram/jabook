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

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/core/data/library/datasources/library_local_datasource.dart';
import 'package:jabook/core/data/library/repositories/library_repository_impl.dart';
import 'package:jabook/core/domain/library/repositories/library_repository.dart';
import 'package:jabook/core/library/audiobook_library_scanner.dart';

/// Provider for AudiobookLibraryScanner instance.
final audiobookLibraryScannerProvider =
    Provider<AudiobookLibraryScanner>((ref) => AudiobookLibraryScanner());

/// Provider for LibraryLocalDataSource instance.
final libraryLocalDataSourceProvider = Provider<LibraryLocalDataSource>((ref) {
  final scanner = ref.watch(audiobookLibraryScannerProvider);
  return LibraryLocalDataSourceImpl(scanner);
});

/// Provider for LibraryRepository instance.
///
/// This provider creates a LibraryRepositoryImpl using local data source.
final libraryRepositoryProvider = Provider<LibraryRepository>((ref) {
  final localDataSource = ref.watch(libraryLocalDataSourceProvider);
  return LibraryRepositoryImpl(localDataSource);
});
