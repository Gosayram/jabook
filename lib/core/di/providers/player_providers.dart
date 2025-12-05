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
import 'package:jabook/core/data/player/datasources/player_local_datasource.dart';
import 'package:jabook/core/data/player/repositories/player_repository_impl.dart';
import 'package:jabook/core/di/providers/database_providers.dart';
import 'package:jabook/core/domain/player/repositories/player_repository.dart';
import 'package:jabook/core/domain/player/use_cases/pause_use_case.dart';
import 'package:jabook/core/domain/player/use_cases/play_use_case.dart';
import 'package:jabook/core/domain/player/use_cases/seek_use_case.dart';
import 'package:jabook/core/player/media3_player_service.dart';
import 'package:jabook/core/player/player_state_database_service.dart';
import 'package:jabook/core/player/player_state_persistence_service.dart';

/// Provider for PlayerStateDatabaseService instance.
///
/// This provider creates a PlayerStateDatabaseService instance that uses
/// AppDatabase for storing player state.
final playerStateDatabaseServiceProvider =
    Provider<PlayerStateDatabaseService>((ref) {
  final appDatabase = ref.watch(appDatabaseProvider);
  return PlayerStateDatabaseService(appDatabase: appDatabase);
});

/// Provider for PlayerStatePersistenceService instance.
///
/// This provider creates a PlayerStatePersistenceService instance that uses
/// database for reliable storage with SharedPreferences fallback.
final playerStatePersistenceServiceProvider =
    Provider<PlayerStatePersistenceService>((ref) {
  final databaseService = ref.watch(playerStateDatabaseServiceProvider);
  return PlayerStatePersistenceService(databaseService: databaseService);
});

/// Provider for Media3PlayerService instance.
///
/// This provider ensures a single instance of Media3PlayerService
/// is created and reused across the application (singleton pattern via provider).
/// The service is disposed when the provider is disposed.
final media3PlayerServiceProvider = Provider<Media3PlayerService>((ref) {
  // Use keepAlive to ensure the service persists across widget rebuilds
  // This is critical for player state consistency
  ref.keepAlive();

  final statePersistenceService =
      ref.watch(playerStatePersistenceServiceProvider);
  final service = Media3PlayerService(
    statePersistenceService: statePersistenceService,
  );
  ref.onDispose(() async {
    await service.dispose();
  });
  return service;
});

/// Provider for PlayerLocalDataSource instance.
final playerLocalDataSourceProvider = Provider<PlayerLocalDataSource>((ref) {
  final service = ref.watch(media3PlayerServiceProvider);
  return PlayerLocalDataSourceImpl(service);
});

/// Provider for PlayerRepository instance.
///
/// This provider creates a PlayerRepositoryImpl using local data source.
final playerRepositoryProvider = Provider<PlayerRepository>((ref) {
  final localDataSource = ref.watch(playerLocalDataSourceProvider);
  return PlayerRepositoryImpl(localDataSource);
});

/// Provider for PlayUseCase instance.
final playUseCaseProvider = Provider<PlayUseCase>((ref) {
  final repository = ref.watch(playerRepositoryProvider);
  return PlayUseCase(repository);
});

/// Provider for PauseUseCase instance.
final pauseUseCaseProvider = Provider<PauseUseCase>((ref) {
  final repository = ref.watch(playerRepositoryProvider);
  return PauseUseCase(repository);
});

/// Provider for SeekUseCase instance.
final seekUseCaseProvider = Provider<SeekUseCase>((ref) {
  final repository = ref.watch(playerRepositoryProvider);
  return SeekUseCase(repository);
});
