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
import 'package:jabook/core/data/local/cache/audiobooks_category_cache_service.dart';
import 'package:jabook/core/data/local/cache/cache_manager.dart';
import 'package:jabook/core/data/local/cache/rutracker_cache_service.dart';

/// Provider for CacheManager instance.
///
/// This provider creates a CacheManager instance that can be used
/// throughout the application for cache management.
final cacheManagerProvider = Provider<CacheManager>((ref) => CacheManager());

/// Provider for RuTrackerCacheService instance.
///
/// This provider creates a RuTrackerCacheService instance that can be used
/// throughout the application for RuTracker cache management.
final rutrackerCacheServiceProvider = Provider<RuTrackerCacheService>((ref) {
  final cacheManager = ref.read(cacheManagerProvider);
  return RuTrackerCacheService(cacheManager: cacheManager);
});

/// Provider for AudiobooksCategoryCacheService instance.
///
/// This provider creates an AudiobooksCategoryCacheService instance that can be used
/// throughout the application for audiobooks category cache management.
final audiobooksCategoryCacheServiceProvider =
    Provider<AudiobooksCategoryCacheService>(
        (ref) => AudiobooksCategoryCacheService());
