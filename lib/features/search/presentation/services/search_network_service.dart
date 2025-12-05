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
import 'dart:convert';
import 'dart:io' as io;

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/core/auth/simple_cookie_manager.dart';
import 'package:jabook/core/constants/category_constants.dart';
import 'package:jabook/core/data/local/database/cookie_database_service.dart';
import 'package:jabook/core/di/providers/database_providers.dart'
    as db_providers;
import 'package:jabook/core/infrastructure/endpoints/endpoint_provider.dart';
import 'package:jabook/core/infrastructure/errors/failures.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/core/parse/rutracker_parser.dart';
import 'package:jabook/core/services/cookie_service.dart';
import 'package:jabook/features/search/presentation/screens/search_screen_utils.dart';
import 'package:jabook/features/search/presentation/services/search_filters.dart';
import 'package:windows1251/windows1251.dart';

/// Result of a network search operation.
class NetworkSearchResult {
  /// Creates a new NetworkSearchResult.
  const NetworkSearchResult({
    required this.results,
    this.errorKind,
    this.errorMessage,
    this.activeHost,
  });

  /// Search results as list of maps.
  final List<Map<String, dynamic>> results;

  /// Error kind if search failed: 'network' | 'auth' | 'mirror' | 'timeout' | null
  final String? errorKind;

  /// Error message if search failed.
  final String? errorMessage;

  /// Active host name extracted from endpoint.
  final String? activeHost;
}

/// Service for performing network searches on RuTracker.
class SearchNetworkService {
  /// Creates a new SearchNetworkService instance.
  const SearchNetworkService(this.ref);

  /// Riverpod reference for accessing providers.
  final WidgetRef ref;

  /// Performs a network search for the given query.
  ///
  /// Returns [NetworkSearchResult] with results or error information.
  Future<NetworkSearchResult> performSearch({
    required String query,
    required int startOffset,
    required CancelToken cancelToken,
    required RuTrackerParser parser,
  }) async {
    final structuredLogger = StructuredLogger();

    // Get active endpoint for logging
    final endpointManager = ref.read(endpointManagerProvider);
    final activeEndpoint = await endpointManager.getActiveEndpoint();

    await structuredLogger.log(
      level: 'info',
      subsystem: 'search',
      message: 'Starting network search',
      context: 'search_request',
      extra: {
        'query': query,
        'start_offset': startOffset,
        'active_endpoint': activeEndpoint,
      },
    );

    // Sync cookies from all sources
    await _syncCookies(activeEndpoint, structuredLogger);

    // Validate cookies
    final cookieValidation =
        await _validateCookies(activeEndpoint, structuredLogger);
    if (!cookieValidation.hasValidCookies) {
      return NetworkSearchResult(
        results: [],
        errorKind: 'auth',
        errorMessage: cookieValidation.errorMessage,
      );
    }

    // Perform HTTP request
    try {
      final dio = await DioClient.instance;
      final endpoint = activeEndpoint;
      final searchUri = Uri.parse('$endpoint/forum/tracker.php');
      final cookieJar = await DioClient.getCookieJar();

      // Ensure cookies are available
      var finalCookies = cookieJar != null
          ? await cookieJar.loadForRequest(searchUri)
          : <io.Cookie>[];

      if (finalCookies.isEmpty && cookieJar != null) {
        finalCookies = await _loadCookiesFromSecureStorage(
          endpoint,
          searchUri,
          cookieJar,
          structuredLogger,
        );
      }

      // Make the search request
      final requestUri = '$endpoint/forum/tracker.php';
      final requestQueryParams = {
        'nm': query,
        'c': CategoryConstants.audiobooksCategoryId,
        'o': '1',
        'start': startOffset,
      };

      final response = await dio
          .get(
            requestUri,
            queryParameters: requestQueryParams,
            options: Options(
              responseType: ResponseType.bytes,
            ),
            cancelToken: cancelToken,
          )
          .timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        // Parse and filter results
        final results = await _parseAndFilterResults(
          response,
          endpoint,
          parser,
          structuredLogger,
        );

        // Extract active host
        final activeHost = _extractHost(endpoint);

        return NetworkSearchResult(
          results: results,
          activeHost: activeHost,
        );
      } else {
        return NetworkSearchResult(
          results: [],
          errorKind: 'network',
          errorMessage: 'HTTP ${response.statusCode}',
        );
      }
    } on TimeoutException {
      return const NetworkSearchResult(
        results: [],
        errorKind: 'timeout',
        errorMessage: 'Request timed out. Check your connection.',
      );
    } on DioException catch (e) {
      if (e.type == DioExceptionType.cancel) {
        return const NetworkSearchResult(results: []);
      }

      final isAuthError = e.response?.statusCode == 401 ||
          e.response?.statusCode == 403 ||
          (e.response?.data?.toString().toLowerCase().contains('login') ??
              false);

      final isServerError = e.response?.statusCode != null &&
          e.response!.statusCode! >= 500 &&
          e.response!.statusCode! < 600;

      final isDnsError = e.type == DioExceptionType.connectionError &&
          (e.message?.toLowerCase().contains('host lookup') ?? false);

      return NetworkSearchResult(
        results: [],
        errorKind: isAuthError
            ? 'auth'
            : isDnsError
                ? 'mirror'
                : isServerError
                    ? 'network'
                    : 'network',
        errorMessage: isAuthError
            ? 'Authentication required. Please log in.'
            : isDnsError
                ? 'Could not resolve domain. This may be due to network restrictions or an inactive mirror.'
                : isServerError
                    ? 'Server is temporarily unavailable. Please try again later or choose another mirror.'
                    : e.message ?? 'Network error occurred',
      );
    } on Exception catch (e) {
      return NetworkSearchResult(
        results: [],
        errorKind: 'network',
        errorMessage: e.toString(),
      );
    }
  }

  /// Syncs cookies from all available sources.
  Future<void> _syncCookies(
    String activeEndpoint,
    StructuredLogger structuredLogger,
  ) async {
    final syncStartTime = DateTime.now();
    var syncSuccessCount = 0;
    final syncSourceDetails = <String, dynamic>{};

    // Sync from database
    try {
      final cookieDbService =
          CookieDatabaseService(ref.read(db_providers.appDatabaseProvider));
      final cookieHeader =
          await cookieDbService.getCookiesForAnyEndpoint(activeEndpoint);

      if (cookieHeader != null && cookieHeader.isNotEmpty) {
        await DioClient.syncCookiesFromCookieService(
            cookieHeader, activeEndpoint);
        syncSuccessCount++;
        syncSourceDetails['Database'] = {'success': true};
      } else {
        syncSourceDetails['Database'] = {'success': false};
      }
    } on Exception catch (e) {
      await structuredLogger.log(
        level: 'warning',
        subsystem: 'search',
        message: 'Failed to sync cookies from database',
        context: 'search_request',
        cause: e.toString(),
      );
      syncSourceDetails['Database'] = {'success': false};
    }

    // Sync from CookieService
    try {
      final activeHost = Uri.parse(activeEndpoint).host;
      final url = 'https://$activeHost';
      await CookieService.flushCookies();
      final cookieHeader = await CookieService.getCookiesForUrl(url);

      if (cookieHeader != null &&
          cookieHeader.isNotEmpty &&
          cookieHeader.trim().contains('=')) {
        await DioClient.syncCookiesFromCookieService(cookieHeader, url);
        syncSuccessCount++;
        syncSourceDetails['CookieService'] = {'success': true};
      } else {
        syncSourceDetails['CookieService'] = {'success': false};
      }
    } on Exception catch (e) {
      await structuredLogger.log(
        level: 'warning',
        subsystem: 'search',
        message: 'Failed to sync cookies from CookieService',
        context: 'search_request',
        cause: e.toString(),
      );
      syncSourceDetails['CookieService'] = {'success': false};
    }

    // Sync from SecureStorage
    try {
      final simpleCookieManager = SimpleCookieManager();
      final cookieString = await simpleCookieManager.getCookie(activeEndpoint);

      if (cookieString != null && cookieString.isNotEmpty) {
        final uri = Uri.parse(activeEndpoint);
        final jar = await DioClient.getCookieJar();
        if (jar != null) {
          await simpleCookieManager.loadCookieToJar(activeEndpoint, jar);
          final cookies = await jar.loadForRequest(uri);
          if (cookies.isNotEmpty) {
            final cookieHeader =
                cookies.map((c) => '${c.name}=${c.value}').join('; ');
            await DioClient.syncCookiesFromCookieService(
                cookieHeader, activeEndpoint);
            syncSuccessCount++;
            syncSourceDetails['SecureStorage'] = {'success': true};
          }
        }
      } else {
        syncSourceDetails['SecureStorage'] = {'success': false};
      }
    } on Exception catch (e) {
      await structuredLogger.log(
        level: 'debug',
        subsystem: 'search',
        message: 'Failed to sync cookies from SecureStorage',
        context: 'search_request',
        cause: e.toString(),
      );
      syncSourceDetails['SecureStorage'] = {'success': false};
    }

    final syncDuration =
        DateTime.now().difference(syncStartTime).inMilliseconds;
    await structuredLogger.log(
      level: syncSuccessCount > 0 ? 'info' : 'warning',
      subsystem: 'search',
      message: 'Cookie synchronization completed before search',
      context: 'search_request',
      durationMs: syncDuration,
      extra: {
        'sync_success_count': syncSuccessCount,
        'sync_sources': syncSourceDetails,
      },
    );
  }

  /// Validates that cookies are available and valid.
  Future<({bool hasValidCookies, String? errorMessage})> _validateCookies(
    String activeEndpoint,
    StructuredLogger structuredLogger,
  ) async {
    try {
      final hasCookies = await DioClient.hasValidCookies();
      if (!hasCookies) {
        return (
          hasValidCookies: false,
          errorMessage: 'Please log in first to perform search'
        );
      }

      // Check for required session cookies
      final uri = Uri.parse(activeEndpoint);
      final jar = await DioClient.getCookieJar();
      if (jar != null) {
        final cookies = await jar.loadForRequest(uri);
        final hasRequiredCookies = cookies.any((c) =>
            c.name.toLowerCase() == 'bb_session' ||
            c.name.toLowerCase() == 'bb_data' ||
            c.name.toLowerCase().contains('session'));

        if (!hasRequiredCookies) {
          // Try CookieService as fallback
          try {
            final activeHost = Uri.parse(activeEndpoint).host;
            final url = 'https://$activeHost';
            final cookieHeader = await CookieService.getCookiesForUrl(url);
            if (cookieHeader != null && cookieHeader.isNotEmpty) {
              final hasRequired = cookieHeader.contains('bb_session=') ||
                  cookieHeader.contains('bb_data=');
              if (!hasRequired) {
                return (
                  hasValidCookies: false,
                  errorMessage: 'Please log in first to perform search'
                );
              }
            }
          } on Exception {
            return (
              hasValidCookies: false,
              errorMessage: 'Please log in first to perform search'
            );
          }
        }
      }

      return (hasValidCookies: true, errorMessage: null);
    } on Exception {
      return (hasValidCookies: true, errorMessage: null); // Continue anyway
    }
  }

  /// Loads cookies from SecureStorage if needed.
  Future<List<io.Cookie>> _loadCookiesFromSecureStorage(
    String endpoint,
    Uri searchUri,
    dynamic cookieJar,
    StructuredLogger structuredLogger,
  ) async {
    final simpleCookieManager = SimpleCookieManager();
    final cookieString = await simpleCookieManager.getCookie(endpoint);

    if (cookieString != null && cookieString.isNotEmpty) {
      await simpleCookieManager.loadCookieToJar(endpoint, cookieJar);
      final cookiesAfterLoad = await cookieJar.loadForRequest(searchUri);
      if (cookiesAfterLoad.isNotEmpty) {
        await structuredLogger.log(
          level: 'info',
          subsystem: 'search',
          message: 'Loaded cookies from SecureStorage to CookieJar',
          context: 'search_request',
          extra: {
            'cookie_count': cookiesAfterLoad.length,
            'cookie_names': cookiesAfterLoad.map((c) => c.name).toList(),
          },
        );
        return cookiesAfterLoad;
      }
    }
    return <io.Cookie>[];
  }

  /// Parses and filters search results from HTTP response.
  Future<List<Map<String, dynamic>>> _parseAndFilterResults(
    Response response,
    String endpoint,
    RuTrackerParser parser,
    StructuredLogger structuredLogger,
  ) async {
    // Validate response data
    if (response.data == null) {
      throw Exception('Response data is null');
    }

    final responseDataSize = response.data is List<int>
        ? (response.data as List<int>).length
        : (response.data is String ? (response.data as String).length : 0);

    if (responseDataSize == 0) {
      throw Exception('Response data is empty');
    }

    // Decode for login page check
    String? decodedTextForCheck;
    final contentType = response.headers.value('content-type') ?? '';

    if (response.data is List<int>) {
      final bytes = response.data as List<int>;
      if (bytes.isEmpty) {
        throw Exception('Bytes array is empty');
      }

      // Determine encoding
      String? detectedEncoding;
      if (contentType.isNotEmpty) {
        final charsetMatch = RegExp(r'charset=([^;\s]+)', caseSensitive: false)
            .firstMatch(contentType);
        if (charsetMatch != null) {
          detectedEncoding = charsetMatch.group(1)?.toLowerCase();
        }
      }

      try {
        if (detectedEncoding != null &&
            (detectedEncoding.contains('windows-1251') ||
                detectedEncoding.contains('cp1251') ||
                detectedEncoding.contains('1251'))) {
          decodedTextForCheck = windows1251.decode(bytes);
        } else {
          try {
            decodedTextForCheck = windows1251.decode(bytes);
          } on Exception {
            decodedTextForCheck = utf8.decode(bytes);
          }
        }
      } on Exception {
        try {
          decodedTextForCheck = utf8.decode(bytes);
        } on FormatException {
          decodedTextForCheck = String.fromCharCodes(bytes);
        }
      }
    } else {
      decodedTextForCheck = response.data?.toString() ?? '';
    }

    // Check for login page
    final responseText = decodedTextForCheck.toLowerCase();
    final isLoginPage = responseText.contains('action="https://rutracker') &&
        responseText.contains('login.php') &&
        responseText.contains('password');

    if (isLoginPage) {
      throw Exception('Received login page instead of search results');
    }

    // Parse results
    try {
      final parsedResults = await parser.parseSearchResults(
        response.data,
        contentType: contentType,
        baseUrl: endpoint,
      );

      final audiobookResults = filterAudiobookResults(parsedResults);

      return audiobookResults
          .map(audiobookToMap)
          .toList()
          .cast<Map<String, dynamic>>();
    } on ParsingFailure catch (e) {
      // Try recovery with alternative encoding
      if (response.data is List<int>) {
        final bytes = response.data as List<int>;
        final detectedEncoding =
            contentType.toLowerCase().contains('windows-1251') ||
                contentType.toLowerCase().contains('cp1251') ||
                contentType.toLowerCase().contains('1251');

        final alternativeContentType = detectedEncoding
            ? 'text/html; charset=utf-8'
            : 'text/html; charset=windows-1251';

        try {
          final recoveredResults = await parser.parseSearchResults(
            bytes,
            contentType: alternativeContentType,
            baseUrl: endpoint,
          );
          final audiobookResults = filterAudiobookResults(recoveredResults);
          return audiobookResults
              .map(audiobookToMap)
              .toList()
              .cast<Map<String, dynamic>>();
        } on Exception {
          // Try Latin-1 as last resort
          try {
            final latin1Results = await parser.parseSearchResults(
              bytes,
              contentType: 'text/html; charset=iso-8859-1',
              baseUrl: endpoint,
            );
            final audiobookResults = filterAudiobookResults(latin1Results);
            if (audiobookResults.isNotEmpty) {
              return audiobookResults
                  .map(audiobookToMap)
                  .toList()
                  .cast<Map<String, dynamic>>();
            }
          } on Exception {
            // Fall through
          }
        }
      }

      // If all recovery attempts failed, check if it's an auth error
      final errorMessageLower = e.message.toLowerCase();
      final isAuthError = errorMessageLower.contains('authentication') ||
          errorMessageLower.contains('log in') ||
          isLoginPage;

      if (isAuthError) {
        throw Exception('Authentication required. Please log in.');
      }

      throw Exception('Failed to parse search results');
    }
  }

  /// Extracts host name from endpoint URL.
  String? _extractHost(String endpoint) {
    final uri = Uri.tryParse(endpoint);
    if (uri != null &&
        uri.hasScheme &&
        uri.hasAuthority &&
        uri.host.isNotEmpty) {
      return uri.host;
    }
    try {
      return Uri.parse(endpoint).host;
    } on Exception {
      return null;
    }
  }
}
