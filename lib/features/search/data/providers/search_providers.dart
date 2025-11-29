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
import 'package:jabook/core/di/providers/search_providers.dart' as core_search;
import 'package:jabook/core/domain/search/use_cases/get_search_history_use_case.dart';
import 'package:jabook/core/domain/search/use_cases/search_use_case.dart';

/// Provider for SearchUseCase instance.
///
/// This provider creates a SearchUseCase using the SearchRepository from core.
final searchUseCaseProvider = Provider<SearchUseCase?>((ref) {
  final repository = ref.watch(core_search.searchRepositoryProvider);
  if (repository == null) return null;
  return SearchUseCase(repository);
});

/// Provider for GetSearchHistoryUseCase instance.
///
/// This provider creates a GetSearchHistoryUseCase using the SearchRepository from core.
final getSearchHistoryUseCaseProvider =
    Provider<GetSearchHistoryUseCase?>((ref) {
  final repository = ref.watch(core_search.searchRepositoryProvider);
  if (repository == null) return null;
  return GetSearchHistoryUseCase(repository);
});
