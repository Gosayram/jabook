import 'package:dio/dio.dart';
import 'package:jabook/core/errors/failures.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/core/session/session_manager.dart';

/// Dio interceptor that automatically validates and refreshes session cookies.
///
/// This interceptor checks session validity before requests and automatically
/// refreshes the session if it has expired.
class SessionInterceptor extends Interceptor {
  /// Creates a new SessionInterceptor instance.
  ///
  /// The [sessionManager] parameter is required for session management.
  SessionInterceptor(this.sessionManager);

  /// Session manager instance for validating and refreshing sessions.
  final SessionManager sessionManager;

  @override
  Future<void> onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    final operationId =
        'session_interceptor_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();

    try {
      // Check if this is a request that requires authentication
      // Skip validation for certain paths (e.g., login, public pages)
      final path = options.path.toLowerCase();
      final skipValidation = path.contains('login') ||
          path.contains('register') ||
          path.contains('public');

      if (!skipValidation) {
        // Check session validity
        final isValid = await sessionManager.isSessionValid();
        if (!isValid) {
          // Try to refresh session
          final refreshed = await sessionManager.refreshSessionIfNeeded();
          if (!refreshed) {
            // Session cannot be refreshed, reject the request
            await logger.log(
              level: 'warning',
              subsystem: 'session_interceptor',
              message: 'Session invalid and cannot be refreshed, rejecting request',
              operationId: operationId,
              context: 'session_interceptor',
              extra: {
                'method': options.method,
                'path': options.path,
              },
            );

            handler.reject(
              DioException(
                requestOptions: options,
                error: const AuthFailure.sessionExpired(),
                type: DioExceptionType.badResponse,
                response: Response(
                  requestOptions: options,
                  statusCode: 401,
                  statusMessage: 'Session expired',
                ),
              ),
            );
            return;
          }

          await logger.log(
            level: 'info',
            subsystem: 'session_interceptor',
            message: 'Session refreshed automatically',
            operationId: operationId,
            context: 'session_interceptor',
            extra: {
              'method': options.method,
              'path': options.path,
            },
          );
        }
      }
    } on Exception catch (e) {
      await logger.log(
        level: 'error',
        subsystem: 'session_interceptor',
        message: 'Error in session interceptor onRequest',
        operationId: operationId,
        context: 'session_interceptor',
        cause: e.toString(),
        extra: {
          'method': options.method,
          'path': options.path,
        },
      );
      // Don't block the request on interceptor errors
    }

    handler.next(options);
  }

  @override
  Future<void> onError(
    DioException err,
    ErrorInterceptorHandler handler,
  ) async {
    final operationId =
        'session_interceptor_error_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();

    // If we got 401/403, try to refresh session and retry
    if (err.response?.statusCode == 401 || err.response?.statusCode == 403) {
      try {
        await logger.log(
          level: 'warning',
          subsystem: 'session_interceptor',
          message: 'Received 401/403, attempting to refresh session',
          operationId: operationId,
          context: 'session_interceptor',
          extra: {
            'status_code': err.response?.statusCode,
            'path': err.requestOptions.path,
          },
        );

        final refreshed = await sessionManager.refreshSessionIfNeeded();
        if (refreshed) {
          // Retry the request with refreshed session
          try {
            final dio = err.requestOptions.extra['dio'] as Dio?;
            if (dio != null) {
              final opts = err.requestOptions;
              final response = await dio.request(
                opts.path,
                options: Options(
                  method: opts.method,
                  headers: opts.headers,
                ),
                data: opts.data,
                queryParameters: opts.queryParameters,
              );

              await logger.log(
                level: 'info',
                subsystem: 'session_interceptor',
                message: 'Request retried successfully after session refresh',
                operationId: operationId,
                context: 'session_interceptor',
                extra: {
                  'path': opts.path,
                  'status_code': response.statusCode,
                },
              );

              handler.resolve(response);
              return;
            }
          } on Exception catch (retryError) {
            await logger.log(
              level: 'error',
              subsystem: 'session_interceptor',
              message: 'Failed to retry request after session refresh',
              operationId: operationId,
              context: 'session_interceptor',
              cause: retryError.toString(),
            );
          }
        } else {
          await logger.log(
            level: 'warning',
            subsystem: 'session_interceptor',
            message: 'Session refresh failed, cannot retry request',
            operationId: operationId,
            context: 'session_interceptor',
            extra: {
              'path': err.requestOptions.path,
            },
          );
        }
      } on Exception catch (e) {
        await logger.log(
          level: 'error',
          subsystem: 'session_interceptor',
          message: 'Error handling 401/403 in session interceptor',
          operationId: operationId,
          context: 'session_interceptor',
          cause: e.toString(),
        );
      }
    }

    handler.next(err);
  }
}

