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
import 'package:jabook/core/data/torrent/datasources/torrent_local_datasource.dart';
import 'package:jabook/core/data/torrent/repositories/torrent_repository_impl.dart';
import 'package:jabook/core/domain/torrent/repositories/torrent_repository.dart';
import 'package:jabook/core/domain/torrent/use_cases/cancel_torrent_use_case.dart';
import 'package:jabook/core/domain/torrent/use_cases/download_torrent_use_case.dart';
import 'package:jabook/core/domain/torrent/use_cases/get_active_tasks_use_case.dart';
import 'package:jabook/core/domain/torrent/use_cases/pause_torrent_use_case.dart';
import 'package:jabook/core/domain/torrent/use_cases/resume_torrent_use_case.dart';
import 'package:jabook/core/torrent/audiobook_torrent_manager.dart';

/// Provider for AudiobookTorrentManager instance.
final audiobookTorrentManagerProvider =
    Provider<AudiobookTorrentManager>((ref) => AudiobookTorrentManager());

/// Provider for TorrentLocalDataSource instance.
final torrentLocalDataSourceProvider = Provider<TorrentLocalDataSource>((ref) {
  final manager = ref.watch(audiobookTorrentManagerProvider);

  // Initialize manager with database
  // Note: This should be done once, but for now we'll do it here
  // In production, this should be handled more carefully
  final dataSource = TorrentLocalDataSourceImpl(manager);
  // Initialize is async, but we can't do async in provider
  // This should be handled by the repository or a separate initialization provider

  return dataSource;
});

/// Provider for TorrentRepository instance.
///
/// This provider creates a TorrentRepositoryImpl using local data source.
final torrentRepositoryProvider = Provider<TorrentRepository>((ref) {
  final localDataSource = ref.watch(torrentLocalDataSourceProvider);
  return TorrentRepositoryImpl(localDataSource);
});

/// Provider for DownloadTorrentUseCase instance.
final downloadTorrentUseCaseProvider = Provider<DownloadTorrentUseCase>((ref) {
  final repository = ref.watch(torrentRepositoryProvider);
  return DownloadTorrentUseCase(repository);
});

/// Provider for GetActiveTasksUseCase instance.
final getActiveTasksUseCaseProvider = Provider<GetActiveTasksUseCase>((ref) {
  final repository = ref.watch(torrentRepositoryProvider);
  return GetActiveTasksUseCase(repository);
});

/// Provider for PauseTorrentUseCase instance.
final pauseTorrentUseCaseProvider = Provider<PauseTorrentUseCase>((ref) {
  final repository = ref.watch(torrentRepositoryProvider);
  return PauseTorrentUseCase(repository);
});

/// Provider for ResumeTorrentUseCase instance.
final resumeTorrentUseCaseProvider = Provider<ResumeTorrentUseCase>((ref) {
  final repository = ref.watch(torrentRepositoryProvider);
  return ResumeTorrentUseCase(repository);
});

/// Provider for CancelTorrentUseCase instance.
final cancelTorrentUseCaseProvider = Provider<CancelTorrentUseCase>((ref) {
  final repository = ref.watch(torrentRepositoryProvider);
  return CancelTorrentUseCase(repository);
});
