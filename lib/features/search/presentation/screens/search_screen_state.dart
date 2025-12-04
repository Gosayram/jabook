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

import 'dart:async';

import 'package:dio/dio.dart';
import 'package:jabook/features/search/presentation/widgets/recommended_audiobooks_widget.dart';

/// State class for managing SearchScreen state.
///
/// This class encapsulates all the state variables used in SearchScreen
/// to improve code organization and maintainability.
class SearchScreenState {
  /// Creates a new SearchScreenState instance with default values.
  SearchScreenState({
    this.searchResults = const [],
    this.searchHistory = const [],
    this.isLoading = false,
    this.hasSearched = false,
    this.isFromCache = false,
    this.cacheExpirationTime,
    this.isFromLocalDb = false,
    this.showHistory = false,
    this.errorKind,
    this.errorMessage,
    this.activeHost,
    this.cancelToken,
    this.debounce,
    this.startOffset = 0,
    this.isLoadingMore = false,
    this.hasMore = true,
    this.selectedCategories = const {},
    this.recommendedAudiobooksFuture,
  });

  /// Search results from RuTracker.
  final List<Map<String, dynamic>> searchResults;

  /// Search history queries.
  final List<String> searchHistory;

  /// Whether a search is currently in progress.
  final bool isLoading;

  /// Whether a search has been performed at least once.
  final bool hasSearched;

  /// Whether the current results are from cache.
  final bool isFromCache;

  /// Cache expiration time if results are from cache.
  final DateTime? cacheExpirationTime;

  /// Whether the current results are from local database.
  final bool isFromLocalDb;

  /// Whether to show search history.
  final bool showHistory;

  /// Error kind: 'network' | 'auth' | 'mirror' | 'timeout' | null
  final String? errorKind;

  /// Error message to display.
  final String? errorMessage;

  /// Active host name for display.
  final String? activeHost;

  /// Cancel token for canceling in-flight requests.
  final CancelToken? cancelToken;

  /// Debounce timer for search input.
  final Timer? debounce;

  /// Start offset for pagination.
  final int startOffset;

  /// Whether more results are being loaded.
  final bool isLoadingMore;

  /// Whether there are more results to load.
  final bool hasMore;

  /// Selected category filters.
  final Set<String> selectedCategories;

  /// Future for recommended audiobooks cache.
  final Future<List<RecommendedAudiobook>>? recommendedAudiobooksFuture;

  /// Creates a copy of this state with the given fields replaced with new values.
  SearchScreenState copyWith({
    List<Map<String, dynamic>>? searchResults,
    List<String>? searchHistory,
    bool? isLoading,
    bool? hasSearched,
    bool? isFromCache,
    DateTime? cacheExpirationTime,
    bool? isFromLocalDb,
    bool? showHistory,
    String? errorKind,
    String? errorMessage,
    String? activeHost,
    CancelToken? cancelToken,
    Timer? debounce,
    int? startOffset,
    bool? isLoadingMore,
    bool? hasMore,
    Set<String>? selectedCategories,
    Future<List<RecommendedAudiobook>>? recommendedAudiobooksFuture,
  }) =>
      SearchScreenState(
        searchResults: searchResults ?? this.searchResults,
        searchHistory: searchHistory ?? this.searchHistory,
        isLoading: isLoading ?? this.isLoading,
        hasSearched: hasSearched ?? this.hasSearched,
        isFromCache: isFromCache ?? this.isFromCache,
        cacheExpirationTime: cacheExpirationTime ?? this.cacheExpirationTime,
        isFromLocalDb: isFromLocalDb ?? this.isFromLocalDb,
        showHistory: showHistory ?? this.showHistory,
        errorKind: errorKind ?? this.errorKind,
        errorMessage: errorMessage ?? this.errorMessage,
        activeHost: activeHost ?? this.activeHost,
        cancelToken: cancelToken ?? this.cancelToken,
        debounce: debounce ?? this.debounce,
        startOffset: startOffset ?? this.startOffset,
        isLoadingMore: isLoadingMore ?? this.isLoadingMore,
        hasMore: hasMore ?? this.hasMore,
        selectedCategories: selectedCategories ?? this.selectedCategories,
        recommendedAudiobooksFuture:
            recommendedAudiobooksFuture ?? this.recommendedAudiobooksFuture,
      );
}
