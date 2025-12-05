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
import 'package:jabook/core/di/providers/library_providers.dart'
    as core_library;

/// Provider for GetLibraryGroupsUseCase instance.
///
/// This provider re-exports the use case from core library providers
/// for use in the library feature.
final getLibraryGroupsUseCaseProvider =
    Provider((ref) => ref.watch(core_library.getLibraryGroupsUseCaseProvider));

/// Provider for ScanLibraryUseCase instance.
final scanLibraryUseCaseProvider =
    Provider((ref) => ref.watch(core_library.scanLibraryUseCaseProvider));

/// Provider for ScanDirectoryUseCase instance.
final scanDirectoryUseCaseProvider =
    Provider((ref) => ref.watch(core_library.scanDirectoryUseCaseProvider));
