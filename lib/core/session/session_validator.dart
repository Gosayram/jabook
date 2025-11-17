import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:jabook/core/endpoints/endpoint_manager.dart';
import 'package:jabook/core/errors/failures.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/data/db/app_database.dart';

/// Validates session cookies by making test requests to RuTracker.
///
/// This class provides methods to check if session cookies are valid
/// and authentication is active.
class SessionValidator {
  /// Creates a new SessionValidator instance.
  const SessionValidator();

  /// Validates session cookies by making a lightweight test request.
  ///
  /// The [dio] parameter is the Dio instance to use for the request.
  /// The [cookieJar] parameter is the cookie jar containing cookies to validate.
  ///
  /// Returns true if cookies are valid, false otherwise.
  ///
  /// Throws [AuthFailure] if validation fails due to network errors.
  Future<bool> validateCookies(
    Dio dio,
    CookieJar cookieJar,
  ) async {
    final operationId =
        'validate_session_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      await logger.log(
        level: 'debug',
        subsystem: 'session_validator',
        message: 'Starting session validation',
        operationId: operationId,
        context: 'session_validation',
      );

      final db = AppDatabase().database;
      final endpointManager = EndpointManager(db);
      final activeBase = await endpointManager.getActiveEndpoint();
      final testUrl = '$activeBase/index.php';

      // Make a lightweight test request
      final testRequestStartTime = DateTime.now();
      final response = await dio
          .get(
            testUrl,
            options: Options(
              validateStatus: (status) => status != null && status < 500,
              followRedirects: false,
            ),
          )
          .timeout(const Duration(seconds: 10));

      final testRequestDuration =
          DateTime.now().difference(testRequestStartTime).inMilliseconds;

      // Check if redirected to login page
      final location = response.headers.value('location');
      final isRedirectedToLogin = location != null &&
          (location.contains('login.php') || location.contains('login'));

      if (isRedirectedToLogin) {
        final totalDuration =
            DateTime.now().difference(startTime).inMilliseconds;
        await logger.log(
          level: 'info',
          subsystem: 'session_validator',
          message: 'Session validation failed - redirected to login',
          operationId: operationId,
          context: 'session_validation',
          durationMs: totalDuration,
          extra: {
            'test_url': testUrl,
            'status_code': response.statusCode,
            'redirect_location': location,
            'test_request_duration_ms': testRequestDuration,
          },
        );
        return false;
      }

      // Check if response is successful
      final isValid = response.statusCode != null &&
          response.statusCode! >= 200 &&
          response.statusCode! < 400;

      final totalDuration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: isValid ? 'info' : 'warning',
        subsystem: 'session_validator',
        message: 'Session validation ${isValid ? "succeeded" : "failed"}',
        operationId: operationId,
        context: 'session_validation',
        durationMs: totalDuration,
        extra: {
          'test_url': testUrl,
          'status_code': response.statusCode,
          'is_valid': isValid,
          'redirect_location': location,
          'test_request_duration_ms': testRequestDuration,
        },
      );

      return isValid;
    } on DioException catch (e) {
      final totalDuration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'warning',
        subsystem: 'session_validator',
        message: 'Session validation failed - network error',
        operationId: operationId,
        context: 'session_validation',
        durationMs: totalDuration,
        cause: e.toString(),
        extra: {
          'error_type': e.type.toString(),
          'status_code': e.response?.statusCode,
        },
      );

      // Network errors don't necessarily mean invalid session
      // Return false to be safe, but don't throw
      return false;
    } on Exception catch (e) {
      final totalDuration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'session_validator',
        message: 'Session validation failed - unexpected error',
        operationId: operationId,
        context: 'session_validation',
        durationMs: totalDuration,
        cause: e.toString(),
      );
      throw AuthFailure(
        'Session validation failed: ${e.toString()}',
        e,
      );
    }
  }

  /// Checks if cookies exist in the cookie jar for the given URI.
  ///
  /// The [cookieJar] parameter is the cookie jar to check.
  /// The [uri] parameter is the URI to check cookies for.
  ///
  /// Returns true if cookies exist, false otherwise.
  Future<bool> hasCookies(CookieJar cookieJar, Uri uri) async {
    try {
      final cookies = await cookieJar.loadForRequest(uri);
      return cookies.isNotEmpty;
    } on Exception {
      return false;
    }
  }
}

