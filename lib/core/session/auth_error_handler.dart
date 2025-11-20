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
import 'package:flutter/material.dart';
import 'package:jabook/core/errors/failures.dart';
import 'package:jabook/core/logging/structured_logger.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Centralized handler for authentication errors.
///
/// This class provides a unified way to handle and display
/// authentication errors to users with appropriate messages.
class AuthErrorHandler {
  /// Private constructor to prevent instantiation.
  /// All methods are static.
  const AuthErrorHandler._();

  /// Handles authentication errors and shows appropriate messages to the user.
  ///
  /// The [context] parameter is used to show dialogs and navigate.
  /// The [error] parameter is the error to handle.
  /// The [onRetry] parameter is an optional callback for retry action.
  ///
  /// Returns true if error was handled, false otherwise.
  static Future<bool> handleAuthError(
    BuildContext context,
    dynamic error, {
    Future<void> Function()? onRetry,
  }) async {
    final operationId =
        'auth_error_handler_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      await logger.log(
        level: 'warning',
        subsystem: 'auth_error_handler',
        message: 'Handling authentication error',
        operationId: operationId,
        context: 'auth_error_handling',
        extra: {
          'error_type': error.runtimeType.toString(),
          'error_message': error.toString(),
        },
      );

      String title;
      String message;
      String actionLabel;
      var showRetryButton = false;

      if (error is AuthFailure) {
        if (error.message.contains('Session expired') ||
            error.message.contains('expired')) {
          title = 'Сессия истекла';
          message = 'Ваша сессия истекла. Пожалуйста, войдите в систему снова.';
          actionLabel = 'Войти';
          showRetryButton = onRetry != null;
        } else if (error.message.contains('Invalid') ||
            error.message.contains('неверн')) {
          title = 'Ошибка авторизации';
          message = 'Неверный логин или пароль. Проверьте введенные данные.';
          actionLabel = 'Повторить';
          showRetryButton = onRetry != null;
        } else if (error.message.contains('Authentication required') ||
            error.message.contains('required')) {
          title = 'Требуется авторизация';
          message = 'Для выполнения этого действия необходимо войти в систему.';
          actionLabel = 'Войти';
          showRetryButton = onRetry != null;
        } else {
          title = 'Ошибка авторизации';
          message = error.message;
          actionLabel = 'Повторить';
          showRetryButton = onRetry != null;
        }
      } else if (error is DioException) {
        if (error.response?.statusCode == 401 ||
            error.response?.statusCode == 403) {
          title = 'Ошибка авторизации';
          message =
              'Доступ запрещен. Проверьте правильность учетных данных или войдите снова.';
          actionLabel = 'Войти';
          showRetryButton = onRetry != null;
        } else {
          title = 'Ошибка сети';
          message =
              'Не удалось выполнить запрос. Проверьте подключение к интернету.';
          actionLabel = 'Повторить';
          showRetryButton = onRetry != null;
        }
      } else {
        title = 'Ошибка';
        message = 'Произошла ошибка при выполнении операции.';
        actionLabel = 'Повторить';
        showRetryButton = onRetry != null;
      }

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'info',
        subsystem: 'auth_error_handler',
        message: 'Prepared error message for user',
        operationId: operationId,
        context: 'auth_error_handling',
        durationMs: duration,
        extra: {
          'title': title,
          'has_action': showRetryButton,
          'show_retry': showRetryButton,
        },
      );

      // Show dialog to user
      if (!context.mounted) return false;
      final result = await showDialog<bool>(
        context: context,
        builder: (ctx) {
          final cancelText = AppLocalizations.of(context)?.cancel ?? 'Отмена';
          return AlertDialog(
            title: Text(title),
            content: Text(message),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(ctx).pop(false),
                child: Text(cancelText),
              ),
              if (showRetryButton)
                ElevatedButton(
                  onPressed: () async {
                    Navigator.of(ctx).pop(true);
                    if (onRetry != null) {
                      await onRetry();
                    }
                  },
                  child: Text(actionLabel),
                ),
            ],
          );
        },
      );

      final totalDuration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'info',
        subsystem: 'auth_error_handler',
        message: 'Error dialog shown to user',
        operationId: operationId,
        context: 'auth_error_handling',
        durationMs: totalDuration,
        extra: {
          'user_action': (result ?? false) ? 'retry' : 'cancel',
        },
      );

      return result ?? false;
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'auth_error_handler',
        message: 'Failed to handle auth error',
        operationId: operationId,
        context: 'auth_error_handling',
        durationMs: duration,
        cause: e.toString(),
      );
      return false;
    }
  }

  /// Shows a snackbar with authentication error message.
  ///
  /// This is a lighter-weight alternative to showing a dialog.
  /// The [context] parameter is used to show the snackbar.
  /// The [error] parameter is the error to display.
  static void showAuthErrorSnackBar(BuildContext context, dynamic error) {
    String message;

    if (error is AuthFailure) {
      if (error.message.contains('Session expired')) {
        message = 'Сессия истекла. Пожалуйста, войдите снова.';
      } else if (error.message.contains('Invalid')) {
        message = 'Неверный логин или пароль.';
      } else {
        message = error.message;
      }
    } else if (error is DioException) {
      if (error.response?.statusCode == 401 ||
          error.response?.statusCode == 403) {
        message = 'Ошибка авторизации. Проверьте учетные данные.';
      } else {
        message = 'Ошибка сети. Проверьте подключение.';
      }
    } else {
      message = 'Произошла ошибка при выполнении операции.';
    }

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: Colors.red,
        action: SnackBarAction(
          label: 'OK',
          textColor: Colors.white,
          onPressed: () {},
        ),
      ),
    );
  }

  /// Determines the type of authentication error.
  ///
  /// Returns an enum value representing the error type.
  static AuthErrorType getErrorType(dynamic error) {
    if (error is AuthFailure) {
      if (error.message.contains('Session expired') ||
          error.message.contains('expired')) {
        return AuthErrorType.sessionExpired;
      } else if (error.message.contains('Invalid') ||
          error.message.contains('неверн')) {
        return AuthErrorType.invalidCredentials;
      } else if (error.message.contains('Authentication required') ||
          error.message.contains('required')) {
        return AuthErrorType.loginRequired;
      }
    } else if (error is DioException) {
      if (error.response?.statusCode == 401 ||
          error.response?.statusCode == 403) {
        return AuthErrorType.sessionExpired;
      } else if (error.type == DioExceptionType.connectionTimeout ||
          error.type == DioExceptionType.receiveTimeout) {
        return AuthErrorType.networkError;
      }
    }

    return AuthErrorType.unknown;
  }
}

/// Types of authentication errors.
enum AuthErrorType {
  /// Session has expired and needs to be renewed.
  sessionExpired,

  /// Invalid username or password.
  invalidCredentials,

  /// User needs to login.
  loginRequired,

  /// Network-related error.
  networkError,

  /// Server error.
  serverError,

  /// Unknown error type.
  unknown,
}
