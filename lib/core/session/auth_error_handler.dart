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

      if (!context.mounted) return false;
      final localizations = AppLocalizations.of(context);
      String title;
      String message;
      String actionLabel;
      var showRetryButton = false;

      if (error is AuthFailure) {
        if (error.message.contains('Session expired') ||
            error.message.contains('expired')) {
          title = localizations?.sessionExpiredTitle ?? 'Session Expired';
          message = localizations?.sessionExpiredMessage ??
              'Your session has expired. Please log in again.';
          actionLabel = localizations?.loginButton ?? 'Login';
          showRetryButton = onRetry != null;
        } else if (error.message.contains('Invalid') ||
            error.message.contains('неверн')) {
          title =
              localizations?.invalidCredentialsTitle ?? 'Authorization Error';
          message = localizations?.invalidCredentialsMessage ??
              'Invalid username or password. Please check your credentials.';
          actionLabel = localizations?.retry ?? 'Retry';
          showRetryButton = onRetry != null;
        } else if (error.message.contains('Authentication required') ||
            error.message.contains('required')) {
          title =
              localizations?.loginRequiredTitle ?? 'Authentication Required';
          message = localizations?.loginRequiredMessage ??
              'You need to log in to perform this action.';
          actionLabel = localizations?.loginButton ?? 'Login';
          showRetryButton = onRetry != null;
        } else {
          title =
              localizations?.authorizationErrorTitle ?? 'Authorization Error';
          message = error.message;
          actionLabel = localizations?.retry ?? 'Retry';
          showRetryButton = onRetry != null;
        }
      } else if (error is DioException) {
        if (error.response?.statusCode == 401 ||
            error.response?.statusCode == 403) {
          title =
              localizations?.authorizationErrorTitle ?? 'Authorization Error';
          message = localizations?.accessDeniedMessage ??
              'Access denied. Please check your credentials or log in again.';
          actionLabel = localizations?.loginButton ?? 'Login';
          showRetryButton = onRetry != null;
        } else {
          title = localizations?.networkErrorTitle ?? 'Network Error';
          message = localizations?.networkRequestFailedMessage ??
              'Failed to complete the request. Please check your internet connection.';
          actionLabel = localizations?.retry ?? 'Retry';
          showRetryButton = onRetry != null;
        }
      } else {
        title = localizations?.error ?? 'Error';
        message = localizations?.errorOccurredMessage ??
            'An error occurred while performing the operation.';
        actionLabel = localizations?.retry ?? 'Retry';
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
    final localizations = AppLocalizations.of(context);
    String message;

    if (error is AuthFailure) {
      if (error.message.contains('Session expired')) {
        message = localizations?.sessionExpiredSnackBar ??
            'Session expired. Please log in again.';
      } else if (error.message.contains('Invalid')) {
        message = localizations?.invalidCredentialsSnackBar ??
            'Invalid username or password.';
      } else {
        message = error.message;
      }
    } else if (error is DioException) {
      if (error.response?.statusCode == 401 ||
          error.response?.statusCode == 403) {
        message = localizations?.authorizationErrorSnackBar ??
            'Authorization error. Please check your credentials.';
      } else {
        message = localizations?.networkErrorSnackBar ??
            'Network error. Please check your connection.';
      }
    } else {
      message = localizations?.errorOccurredMessage ??
          'An error occurred while performing the operation.';
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
