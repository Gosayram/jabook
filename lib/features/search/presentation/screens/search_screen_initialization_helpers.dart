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
import 'package:jabook/core/di/providers/database_providers.dart'
    as db_providers;
import 'package:jabook/core/infrastructure/endpoints/endpoint_provider.dart';
import 'package:jabook/core/infrastructure/logging/environment_logger.dart';
import 'package:jabook/core/metadata/audiobook_metadata_service.dart';
import 'package:jabook/core/search/search_history_service.dart';

/// Helper class for search screen initialization.
class SearchScreenInitializationHelper {
  // Private constructor to prevent instantiation
  SearchScreenInitializationHelper._();

  /// Initializes metadata service and history service.
  static Future<
      ({
        AudiobookMetadataService? metadataService,
        SearchHistoryService? historyService,
      })> initializeMetadataService(WidgetRef ref) async {
    try {
      final appDatabase = ref.read(db_providers.appDatabaseProvider);
      final db = await appDatabase.ensureInitialized();
      final metadataService = AudiobookMetadataService(db);
      final historyService = SearchHistoryService(db);
      return (
        metadataService: metadataService,
        historyService: historyService,
      );
    } on Exception {
      // Metadata service optional - continue without it
      return (
        metadataService: null,
        historyService: null,
      );
    }
  }

  /// Loads search history from history service.
  static Future<List<String>> loadSearchHistory(
    SearchHistoryService? historyService,
  ) async {
    if (historyService == null) return [];
    try {
      return await historyService.getRecentSearches();
    } on Exception {
      // Ignore errors
      return [];
    }
  }

  /// Loads active host from endpoint manager.
  static Future<String?> loadActiveHost(WidgetRef ref) async {
    try {
      final endpointManager = ref.read(endpointManagerProvider);
      final base = await endpointManager.getActiveEndpoint();

      // Verify endpoint is available before displaying
      final isAvailable = await endpointManager.quickAvailabilityCheck(base);
      if (!isAvailable) {
        // If current endpoint is unavailable, try to get a better one
        EnvironmentLogger().w(
            'Current active endpoint $base is unavailable, trying to get available one');
        // getActiveEndpoint should already handle this, but we verify anyway
      }

      final uri = Uri.tryParse(base);
      if (uri != null && uri.hasScheme && uri.hasAuthority) {
        final host = uri.host;
        if (host.isNotEmpty) {
          return host;
        }
      } else {
        // Fallback: try to extract host from base string
        try {
          final host = Uri.parse(base).host;
          if (host.isNotEmpty) {
            return host;
          }
        } on Exception {
          // If parsing fails, return null
          return null;
        }
      }
      return null;
    } on Exception catch (e) {
      EnvironmentLogger().w('Failed to load active host: $e');
      return null;
    }
  }
}
