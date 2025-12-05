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
import 'package:jabook/core/di/providers/downloads_providers.dart'
    as core_downloads;

/// Provider for GetDownloadsUseCase instance.
///
/// This provider re-exports the use case from core downloads providers
/// for use in the downloads feature.
final getDownloadsUseCaseProvider =
    Provider((ref) => ref.watch(core_downloads.getDownloadsUseCaseProvider));

/// Provider for PauseDownloadUseCase instance.
final pauseDownloadUseCaseProvider =
    Provider((ref) => ref.watch(core_downloads.pauseDownloadUseCaseProvider));

/// Provider for ResumeDownloadUseCase instance.
final resumeDownloadUseCaseProvider =
    Provider((ref) => ref.watch(core_downloads.resumeDownloadUseCaseProvider));

/// Provider for ResumeRestoredDownloadUseCase instance.
final resumeRestoredDownloadUseCaseProvider = Provider(
    (ref) => ref.watch(core_downloads.resumeRestoredDownloadUseCaseProvider));

/// Provider for RestartDownloadUseCase instance.
final restartDownloadUseCaseProvider =
    Provider((ref) => ref.watch(core_downloads.restartDownloadUseCaseProvider));

/// Provider for RedownloadUseCase instance.
final redownloadUseCaseProvider =
    Provider((ref) => ref.watch(core_downloads.redownloadUseCaseProvider));

/// Provider for RemoveDownloadUseCase instance.
final removeDownloadUseCaseProvider =
    Provider((ref) => ref.watch(core_downloads.removeDownloadUseCaseProvider));

/// Provider for GetDownloadProgressStreamUseCase instance.
final getDownloadProgressStreamUseCaseProvider = Provider((ref) =>
    ref.watch(core_downloads.getDownloadProgressStreamUseCaseProvider));
