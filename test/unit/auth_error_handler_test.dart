import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:jabook/core/errors/failures.dart';
import 'package:jabook/core/session/auth_error_handler.dart';

void main() {
  group('AuthErrorHandler', () {
    test('getErrorType should return sessionExpired for expired session', () {
      const error = AuthFailure.sessionExpired();
      final errorType = AuthErrorHandler.getErrorType(error);
      expect(errorType, AuthErrorType.sessionExpired);
    });

    test('getErrorType should return invalidCredentials for invalid credentials',
        () {
      const error = AuthFailure.invalidCredentials();
      final errorType = AuthErrorHandler.getErrorType(error);
      expect(errorType, AuthErrorType.invalidCredentials);
    });

    test('getErrorType should return loginRequired for login required', () {
      const error = AuthFailure.loginRequired();
      final errorType = AuthErrorHandler.getErrorType(error);
      expect(errorType, AuthErrorType.loginRequired);
    });

    test('getErrorType should return sessionExpired for 401 DioException', () {
      final error = DioException(
        requestOptions: RequestOptions(path: '/test'),
        response: Response(
          requestOptions: RequestOptions(path: '/test'),
          statusCode: 401,
        ),
      );
      final errorType = AuthErrorHandler.getErrorType(error);
      expect(errorType, AuthErrorType.sessionExpired);
    });

    test('getErrorType should return sessionExpired for 403 DioException', () {
      final error = DioException(
        requestOptions: RequestOptions(path: '/test'),
        response: Response(
          requestOptions: RequestOptions(path: '/test'),
          statusCode: 403,
        ),
      );
      final errorType = AuthErrorHandler.getErrorType(error);
      expect(errorType, AuthErrorType.sessionExpired);
    });

    test('getErrorType should return networkError for connection timeout', () {
      final error = DioException(
        requestOptions: RequestOptions(path: '/test'),
        type: DioExceptionType.connectionTimeout,
      );
      final errorType = AuthErrorHandler.getErrorType(error);
      expect(errorType, AuthErrorType.networkError);
    });

    test('getErrorType should return networkError for receive timeout', () {
      final error = DioException(
        requestOptions: RequestOptions(path: '/test'),
        type: DioExceptionType.receiveTimeout,
      );
      final errorType = AuthErrorHandler.getErrorType(error);
      expect(errorType, AuthErrorType.networkError);
    });

    test('getErrorType should return unknown for unknown error', () {
      final error = Exception('Unknown error');
      final errorType = AuthErrorHandler.getErrorType(error);
      expect(errorType, AuthErrorType.unknown);
    });

    test('getErrorType should handle AuthFailure with custom message', () {
      const error = AuthFailure('Custom error message');
      final errorType = AuthErrorHandler.getErrorType(error);
      // Should return unknown for custom messages
      expect(errorType, AuthErrorType.unknown);
    });
  });

  group('AuthErrorHandler error type detection', () {
    test('should detect session expired from message', () {
      const error = AuthFailure('Session expired. Please login again.');
      final errorType = AuthErrorHandler.getErrorType(error);
      expect(errorType, AuthErrorType.sessionExpired);
    });

    test('should detect invalid credentials from message', () {
      const error = AuthFailure('Invalid username or password');
      final errorType = AuthErrorHandler.getErrorType(error);
      expect(errorType, AuthErrorType.invalidCredentials);
    });

    test('should detect login required from message', () {
      const error = AuthFailure('Authentication required');
      final errorType = AuthErrorHandler.getErrorType(error);
      expect(errorType, AuthErrorType.loginRequired);
    });
  });
}

