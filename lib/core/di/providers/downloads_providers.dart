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
import 'package:jabook/core/data/downloads/datasources/downloads_local_datasource.dart';
import 'package:jabook/core/data/downloads/repositories/downloads_repository_impl.dart';
import 'package:jabook/core/domain/downloads/repositories/downloads_repository.dart';
import 'package:jabook/core/domain/downloads/use_cases/get_download_progress_stream_use_case.dart';
import 'package:jabook/core/domain/downloads/use_cases/get_downloads_use_case.dart';
import 'package:jabook/core/domain/downloads/use_cases/pause_download_use_case.dart';
import 'package:jabook/core/domain/downloads/use_cases/redownload_use_case.dart';
import 'package:jabook/core/domain/downloads/use_cases/remove_download_use_case.dart';
import 'package:jabook/core/domain/downloads/use_cases/restart_download_use_case.dart';
import 'package:jabook/core/domain/downloads/use_cases/resume_download_use_case.dart';
import 'package:jabook/core/domain/downloads/use_cases/resume_restored_download_use_case.dart';
import 'package:jabook/core/torrent/audiobook_torrent_manager.dart';

/// Provider for AudiobookTorrentManager instance.
///
/// This is a shared provider that can be used by both torrent and downloads features.
final audiobookTorrentManagerProvider =
    Provider<AudiobookTorrentManager>((ref) => AudiobookTorrentManager());

/// Provider for DownloadsLocalDataSource instance.
final downloadsLocalDataSourceProvider =
    Provider<DownloadsLocalDataSource>((ref) {
  final manager = ref.watch(audiobookTorrentManagerProvider);
  return DownloadsLocalDataSourceImpl(manager);
});

/// Provider for DownloadsRepository instance.
///
/// This provider creates a DownloadsRepositoryImpl using local data source.
final downloadsRepositoryProvider = Provider<DownloadsRepository>((ref) {
  final localDataSource = ref.watch(downloadsLocalDataSourceProvider);
  return DownloadsRepositoryImpl(localDataSource);
});

/// Provider for GetDownloadsUseCase instance.
final getDownloadsUseCaseProvider = Provider<GetDownloadsUseCase>((ref) {
  final repository = ref.watch(downloadsRepositoryProvider);
  return GetDownloadsUseCase(repository);
});

/// Provider for PauseDownloadUseCase instance.
final pauseDownloadUseCaseProvider = Provider<PauseDownloadUseCase>((ref) {
  final repository = ref.watch(downloadsRepositoryProvider);
  return PauseDownloadUseCase(repository);
});

/// Provider for ResumeDownloadUseCase instance.
final resumeDownloadUseCaseProvider = Provider<ResumeDownloadUseCase>((ref) {
  final repository = ref.watch(downloadsRepositoryProvider);
  return ResumeDownloadUseCase(repository);
});

/// Provider for ResumeRestoredDownloadUseCase instance.
final resumeRestoredDownloadUseCaseProvider =
    Provider<ResumeRestoredDownloadUseCase>((ref) {
  final repository = ref.watch(downloadsRepositoryProvider);
  return ResumeRestoredDownloadUseCase(repository);
});

/// Provider for RestartDownloadUseCase instance.
final restartDownloadUseCaseProvider = Provider<RestartDownloadUseCase>((ref) {
  final repository = ref.watch(downloadsRepositoryProvider);
  return RestartDownloadUseCase(repository);
});

/// Provider for RedownloadUseCase instance.
final redownloadUseCaseProvider = Provider<RedownloadUseCase>((ref) {
  final repository = ref.watch(downloadsRepositoryProvider);
  return RedownloadUseCase(repository);
});

/// Provider for RemoveDownloadUseCase instance.
final removeDownloadUseCaseProvider = Provider<RemoveDownloadUseCase>((ref) {
  final repository = ref.watch(downloadsRepositoryProvider);
  return RemoveDownloadUseCase(repository);
});

/// Provider for GetDownloadProgressStreamUseCase instance.
final getDownloadProgressStreamUseCaseProvider =
    Provider<GetDownloadProgressStreamUseCase>((ref) {
  final repository = ref.watch(downloadsRepositoryProvider);
  return GetDownloadProgressStreamUseCase(repository);
});
