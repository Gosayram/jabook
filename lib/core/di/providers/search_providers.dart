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
import 'package:jabook/core/data/search/datasources/search_local_datasource.dart';
import 'package:jabook/core/data/search/datasources/search_remote_datasource.dart';
import 'package:jabook/core/data/search/repositories/search_repository_impl.dart';
import 'package:jabook/core/di/providers/cache_providers.dart';
import 'package:jabook/core/di/providers/database_providers.dart';
import 'package:jabook/core/di/providers/endpoint_providers.dart';
import 'package:jabook/core/domain/search/repositories/search_repository.dart';
import 'package:jabook/core/search/search_history_service.dart';
import 'package:jabook/core/search/smart_search_cache_service.dart';

/// Provider for SearchHistoryService instance.
final searchHistoryServiceProvider = Provider<SearchHistoryService?>((ref) {
  final db = ref.watch(appDatabaseProvider);
  if (db.isInitialized) {
    return SearchHistoryService(db.database);
  }
  return null;
});

/// Provider for SearchLocalDataSource instance.
final searchLocalDataSourceProvider = Provider<SearchLocalDataSource?>((ref) {
  final service = ref.watch(searchHistoryServiceProvider);
  if (service == null) return null;
  return SearchLocalDataSourceImpl(service);
});

/// Provider for SearchRemoteDataSource instance.
final searchRemoteDataSourceProvider = Provider<SearchRemoteDataSource>((ref) {
  final cacheService = ref.watch(rutrackerCacheServiceProvider);
  // Note: AudiobookMetadataService would need its own provider
  // For now, this is a placeholder
  return SearchRemoteDataSourceImpl(
    cacheService,
    null, // Would be provided via provider
  );
});

/// Provider for SearchRepository instance.
///
/// This provider creates a SearchRepositoryImpl using remote and local data sources.
final searchRepositoryProvider = Provider<SearchRepository?>((ref) {
  final remoteDataSource = ref.watch(searchRemoteDataSourceProvider);
  final localDataSource = ref.watch(searchLocalDataSourceProvider);
  if (localDataSource == null) return null;
  return SearchRepositoryImpl(remoteDataSource, localDataSource);
});

/// Provider for SmartSearchCacheService instance.
///
/// This provider creates a SmartSearchCacheService instance for managing
/// smart search cache functionality.
final smartSearchCacheServiceProvider =
    Provider<SmartSearchCacheService>((ref) {
  final db = ref.watch(appDatabaseProvider);
  final endpointManager = ref.watch(endpointManagerProvider);
  return SmartSearchCacheService(db, endpointManager);
});
