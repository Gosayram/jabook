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

import 'package:dio/dio.dart';
import 'package:jabook/core/data/local/database/app_database.dart';
import 'package:jabook/core/data/remote/network/dio_client.dart';
import 'package:jabook/core/infrastructure/endpoints/endpoint_manager.dart';

/// Unified HTTP client for RuTracker API requests.
///
/// This class provides a single entry point for all RuTracker HTTP requests,
/// ensuring consistent configuration, cookie management, and error handling.
/// According to .plan-idea-docs.md, all RuTracker requests should go through
/// this client instead of directly using Dio.
///
/// The client automatically:
/// - Uses cookies from CookieJar (managed by DioClient)
/// - Applies correct User-Agent and headers
/// - Handles endpoint switching and retries
/// - Provides consistent error handling
class RutrackerClient {
  /// Private constructor to prevent direct instantiation.
  const RutrackerClient._();

  /// Gets the singleton RutrackerClient instance.
  ///
  /// This instance uses DioClient.instance internally, which is already
  /// configured with cookies, interceptors, and proper settings.
  static Future<RutrackerClient> get instance async {
    await DioClient.instance; // Ensure Dio is initialized
    return const RutrackerClient._();
  }

  /// Gets the underlying Dio instance.
  ///
  /// This is used internally and should not be accessed directly.
  /// Use the methods of RutrackerClient instead.
  Future<Dio> get _dio async => DioClient.instance;

  /// Gets the active RuTracker endpoint URL.
  Future<String> _getActiveEndpoint() async {
    final appDb = AppDatabase.getInstance();
    final db = await appDb.ensureInitialized();
    final endpointManager = EndpointManager(db, appDb);
    return endpointManager.getActiveEndpoint();
  }

  /// Makes a GET request to RuTracker.
  ///
  /// The [path] parameter is the path relative to the base URL (e.g., '/forum/index.php').
  /// The [query] parameter is optional query parameters.
  /// The [options] parameter allows customizing request options.
  ///
  /// Returns the response from the server.
  Future<Response> get(
    String path, {
    Map<String, dynamic>? query,
    Options? options,
  }) async {
    final dio = await _dio;
    final baseUrl = await _getActiveEndpoint();

    // Ensure path starts with / if it's not absolute
    final normalizedPath = path.startsWith('http')
        ? path
        : path.startsWith('/')
            ? path
            : '/$path';
    final url = normalizedPath.startsWith('http')
        ? normalizedPath
        : '$baseUrl$normalizedPath';

    return dio.get(
      url,
      queryParameters: query,
      options: options ?? Options(),
    );
  }

  /// Makes a POST request to RuTracker.
  ///
  /// The [path] parameter is the path relative to the base URL.
  /// The [data] parameter is the request body data.
  /// The [query] parameter is optional query parameters.
  /// The [options] parameter allows customizing request options.
  ///
  /// Returns the response from the server.
  Future<Response> post(
    String path, {
    dynamic data,
    Map<String, dynamic>? query,
    Options? options,
  }) async {
    final dio = await _dio;
    final baseUrl = await _getActiveEndpoint();

    // Ensure path starts with / if it's not absolute
    final normalizedPath = path.startsWith('http')
        ? path
        : path.startsWith('/')
            ? path
            : '/$path';
    final url = normalizedPath.startsWith('http')
        ? normalizedPath
        : '$baseUrl$normalizedPath';

    return dio.post(
      url,
      data: data,
      queryParameters: query,
      options: options ?? Options(),
    );
  }

  /// Makes a PUT request to RuTracker.
  ///
  /// The [path] parameter is the path relative to the base URL.
  /// The [data] parameter is the request body data.
  /// The [query] parameter is optional query parameters.
  /// The [options] parameter allows customizing request options.
  ///
  /// Returns the response from the server.
  Future<Response> put(
    String path, {
    dynamic data,
    Map<String, dynamic>? query,
    Options? options,
  }) async {
    final dio = await _dio;
    final baseUrl = await _getActiveEndpoint();

    // Ensure path starts with / if it's not absolute
    final normalizedPath = path.startsWith('http')
        ? path
        : path.startsWith('/')
            ? path
            : '/$path';
    final url = normalizedPath.startsWith('http')
        ? normalizedPath
        : '$baseUrl$normalizedPath';

    return dio.put(
      url,
      data: data,
      queryParameters: query,
      options: options ?? Options(),
    );
  }

  /// Makes a DELETE request to RuTracker.
  ///
  /// The [path] parameter is the path relative to the base URL.
  /// The [query] parameter is optional query parameters.
  /// The [options] parameter allows customizing request options.
  ///
  /// Returns the response from the server.
  Future<Response> delete(
    String path, {
    Map<String, dynamic>? query,
    Options? options,
  }) async {
    final dio = await _dio;
    final baseUrl = await _getActiveEndpoint();

    // Ensure path starts with / if it's not absolute
    final normalizedPath = path.startsWith('http')
        ? path
        : path.startsWith('/')
            ? path
            : '/$path';
    final url = normalizedPath.startsWith('http')
        ? normalizedPath
        : '$baseUrl$normalizedPath';

    return dio.delete(
      url,
      queryParameters: query,
      options: options ?? Options(),
    );
  }

  /// Makes a HEAD request to RuTracker.
  ///
  /// The [path] parameter is the path relative to the base URL.
  /// The [query] parameter is optional query parameters.
  /// The [options] parameter allows customizing request options.
  ///
  /// Returns the response from the server.
  Future<Response> head(
    String path, {
    Map<String, dynamic>? query,
    Options? options,
  }) async {
    final dio = await _dio;
    final baseUrl = await _getActiveEndpoint();

    // Ensure path starts with / if it's not absolute
    final normalizedPath = path.startsWith('http')
        ? path
        : path.startsWith('/')
            ? path
            : '/$path';
    final url = normalizedPath.startsWith('http')
        ? normalizedPath
        : '$baseUrl$normalizedPath';

    return dio.head(
      url,
      queryParameters: query,
      options: options ?? Options(),
    );
  }

  /// Makes a PATCH request to RuTracker.
  ///
  /// The [path] parameter is the path relative to the base URL.
  /// The [data] parameter is the request body data.
  /// The [query] parameter is optional query parameters.
  /// The [options] parameter allows customizing request options.
  ///
  /// Returns the response from the server.
  Future<Response> patch(
    String path, {
    dynamic data,
    Map<String, dynamic>? query,
    Options? options,
  }) async {
    final dio = await _dio;
    final baseUrl = await _getActiveEndpoint();

    // Ensure path starts with / if it's not absolute
    final normalizedPath = path.startsWith('http')
        ? path
        : path.startsWith('/')
            ? path
            : '/$path';
    final url = normalizedPath.startsWith('http')
        ? normalizedPath
        : '$baseUrl$normalizedPath';

    return dio.patch(
      url,
      data: data,
      queryParameters: query,
      options: options ?? Options(),
    );
  }

  /// Makes a request with custom method.
  ///
  /// The [path] parameter is the path relative to the base URL.
  /// The [method] parameter is the HTTP method (GET, POST, etc.).
  /// The [data] parameter is the request body data.
  /// The [query] parameter is optional query parameters.
  /// The [options] parameter allows customizing request options.
  ///
  /// Returns the response from the server.
  Future<Response> request(
    String path, {
    required String method,
    dynamic data,
    Map<String, dynamic>? query,
    Options? options,
  }) async {
    final dio = await _dio;
    final baseUrl = await _getActiveEndpoint();

    // Ensure path starts with / if it's not absolute
    final normalizedPath = path.startsWith('http')
        ? path
        : path.startsWith('/')
            ? path
            : '/$path';
    final url = normalizedPath.startsWith('http')
        ? normalizedPath
        : '$baseUrl$normalizedPath';

    return dio.request(
      url,
      data: data,
      queryParameters: query,
      options: (options ?? Options()).copyWith(method: method),
    );
  }

  /// Downloads a file from RuTracker.
  ///
  /// The [path] parameter is the path relative to the base URL.
  /// The [savePath] parameter is the local path where the file should be saved.
  /// The [query] parameter is optional query parameters.
  /// The [options] parameter allows customizing request options.
  /// The [onReceiveProgress] callback is called with download progress.
  ///
  /// Returns the response from the server.
  Future<Response> download(
    String path,
    String savePath, {
    Map<String, dynamic>? query,
    Options? options,
    ProgressCallback? onReceiveProgress,
  }) async {
    final dio = await _dio;
    final baseUrl = await _getActiveEndpoint();

    // Ensure path starts with / if it's not absolute
    final normalizedPath = path.startsWith('http')
        ? path
        : path.startsWith('/')
            ? path
            : '/$path';
    final url = normalizedPath.startsWith('http')
        ? normalizedPath
        : '$baseUrl$normalizedPath';

    return dio.download(
      url,
      savePath,
      queryParameters: query,
      options: options ?? Options(),
      onReceiveProgress: onReceiveProgress,
    );
  }
}
