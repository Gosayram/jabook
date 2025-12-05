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

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:jabook/core/di/providers/auth_providers.dart';
import 'package:jabook/core/domain/auth/repositories/auth_repository.dart';
import 'package:jabook/core/infrastructure/logging/environment_logger.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:jabook/core/net/dio_client.dart';
import 'package:jabook/l10n/app_localizations.dart';

/// Result of login operation.
class LoginResult {
  /// Creates a new LoginResult.
  const LoginResult({
    required this.success,
    this.shouldClearAuthError = false,
    this.shouldPerformSearch = false,
  });

  /// Whether login was successful.
  final bool success;

  /// Whether auth error should be cleared.
  final bool shouldClearAuthError;

  /// Whether search should be performed after login.
  final bool shouldPerformSearch;
}

/// Helper class for handling login operations in search screen.
class SearchScreenLoginHandler {
  // Private constructor to prevent instantiation
  SearchScreenLoginHandler._();

  /// Handles login with stored credentials or opens auth screen.
  ///
  /// Returns LoginResult with information about what should be done next.
  static Future<LoginResult> handleLogin({
    required WidgetRef ref,
    required BuildContext context,
    required bool mounted,
    required String? errorKind,
    required void Function(void Function()) setState,
  }) async {
    final operationId = 'handle_login_${DateTime.now().millisecondsSinceEpoch}';
    try {
      await StructuredLogger().log(
        level: 'info',
        subsystem: 'search',
        message: 'Login button pressed, starting login flow',
        operationId: operationId,
        context: 'login_flow',
      );

      // Try to get repository, but handle errors gracefully
      AuthRepository? repository;
      try {
        repository = ref.read(authRepositoryProvider);
      } on Exception catch (e) {
        await StructuredLogger().log(
          level: 'error',
          subsystem: 'search',
          message:
              'Failed to get auth repository, opening auth screen directly',
          operationId: operationId,
          context: 'login_flow',
          cause: e.toString(),
        );
        // If repository is not available, just open auth screen
        if (!mounted) {
          await StructuredLogger().log(
            level: 'warning',
            subsystem: 'search',
            message: 'Widget not mounted, cannot navigate to auth screen',
            operationId: operationId,
            context: 'login_flow',
          );
          return const LoginResult(success: false);
        }

        await StructuredLogger().log(
          level: 'info',
          subsystem: 'search',
          message: 'Navigating to /auth screen (after repository error)',
          operationId: operationId,
          context: 'login_flow',
        );

        if (!mounted) return const LoginResult(success: false);

        try {
          // ignore: use_build_context_synchronously
          // context.push is safe here as we check mounted before and after async operation
          // ignore: use_build_context_synchronously
          final result = await context.push('/auth');
          await StructuredLogger().log(
            level: 'info',
            subsystem: 'search',
            message: 'Returned from /auth screen (after repository error)',
            operationId: operationId,
            context: 'login_flow',
            extra: {'result': result?.toString() ?? 'null'},
          );
          return LoginResult(
            success: result == true,
            shouldClearAuthError: result == true && errorKind == 'auth',
            shouldPerformSearch: result == true && errorKind == 'auth',
          );
        } on Exception catch (navError, stackTrace) {
          await StructuredLogger().log(
            level: 'error',
            subsystem: 'search',
            message: 'Navigation to /auth failed',
            operationId: operationId,
            context: 'login_flow',
            cause: navError.toString(),
            extra: {'stack_trace': stackTrace.toString()},
          );
          return const LoginResult(success: false);
        }
      }

      if (repository == null) {
        await StructuredLogger().log(
          level: 'warning',
          subsystem: 'search',
          message: 'Auth repository is null, opening auth screen directly',
          operationId: operationId,
          context: 'login_flow',
        );
        if (!mounted) return const LoginResult(success: false);
        // ignore: use_build_context_synchronously
        final result = await context.push('/auth');
        return LoginResult(
          success: result == true,
          shouldClearAuthError: result == true && errorKind == 'auth',
          shouldPerformSearch: result == true && errorKind == 'auth',
        );
      }

      // Check if stored credentials are available
      final hasStored = await repository.hasStoredCredentials();

      await StructuredLogger().log(
        level: 'info',
        subsystem: 'search',
        message: 'Checked for stored credentials',
        operationId: operationId,
        context: 'login_flow',
        extra: {'has_stored_credentials': hasStored},
      );

      if (hasStored) {
        // Try to login with stored credentials first
        try {
          final success = await repository.loginWithStoredCredentials();

          if (success) {
            // Validate cookies
            final isValid = await DioClient.validateCookies();

            if (isValid) {
              if (!mounted) return const LoginResult(success: false);
              // ignore: use_build_context_synchronously
              final localizations = AppLocalizations.of(context);
              // ignore: use_build_context_synchronously
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text(localizations?.authorizationSuccessful ??
                      'Authorization successful'),
                  backgroundColor: Colors.green,
                  duration: const Duration(seconds: 2),
                ),
              );

              // Clear auth errors if any
              if (errorKind == 'auth') {
                setState(() {
                  // Error clearing will be handled by caller
                });
                return const LoginResult(
                  success: true,
                  shouldClearAuthError: true,
                  shouldPerformSearch: true,
                );
              }
              return const LoginResult(success: true);
            }
          }
        } on Exception catch (e) {
          EnvironmentLogger().w('Login with stored credentials failed: $e');
          // Fall through to auth screen
        }
      }

      // Open auth screen for manual login
      if (!mounted) return const LoginResult(success: false);

      await StructuredLogger().log(
        level: 'info',
        subsystem: 'search',
        message: 'Opening auth screen for manual login',
        operationId: operationId,
        context: 'login_flow',
      );

      if (!mounted) return const LoginResult(success: false);

      // Log before navigation
      await StructuredLogger().log(
        level: 'info',
        subsystem: 'search',
        message: 'Calling context.push(\'/auth\')',
        operationId: operationId,
        context: 'login_flow',
      );

      if (!mounted) return const LoginResult(success: false);

      dynamic result;
      try {
        // ignore: use_build_context_synchronously
        // context.push is safe here as we check mounted before and after async operation
        // ignore: use_build_context_synchronously
        result = await context.push('/auth');

        // Log after navigation
        await StructuredLogger().log(
          level: 'info',
          subsystem: 'search',
          message: 'Returned from context.push(\'/auth\')',
          operationId: operationId,
          context: 'login_flow',
          extra: {'result': result?.toString() ?? 'null'},
        );
      } on Exception catch (e, stackTrace) {
        await StructuredLogger().log(
          level: 'error',
          subsystem: 'search',
          message: 'Error during navigation to /auth',
          operationId: operationId,
          context: 'login_flow',
          cause: e.toString(),
          extra: {'stack_trace': stackTrace.toString()},
        );
        // Re-throw to show error to user
        rethrow;
      }

      await StructuredLogger().log(
        level: 'info',
        subsystem: 'search',
        message: 'Returned from auth screen',
        operationId: operationId,
        context: 'login_flow',
        extra: {'result': result?.toString() ?? 'null'},
      );

      // If login was successful, refresh and perform search
      if (result == true && mounted) {
        // Explicitly refresh auth status to update the provider
        await ref.read(authRepositoryProvider).refreshAuthStatus();

        // Validate cookies after returning from auth screen
        final isValid = await DioClient.validateCookies();
        if (isValid) {
          if (errorKind == 'auth') {
            setState(() {
              // Error clearing will be handled by caller
            });
            return const LoginResult(
              success: true,
              shouldClearAuthError: true,
              shouldPerformSearch: true,
            );
          }
          return const LoginResult(success: true);
        }
      }

      return LoginResult(
        success: result == true,
        shouldClearAuthError: result == true && errorKind == 'auth',
        shouldPerformSearch: result == true && errorKind == 'auth',
      );
    } on Exception catch (e, stackTrace) {
      EnvironmentLogger().e('Error in handleLogin: $e', stackTrace: stackTrace);
      if (!mounted) return const LoginResult(success: false);

      // ignore: use_build_context_synchronously
      final localizations = AppLocalizations.of(context);
      // ignore: use_build_context_synchronously
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(localizations?.authorizationCheckError(e.toString()) ??
              'Authorization check error: ${e.toString()}'),
          backgroundColor: Colors.orange,
          duration: const Duration(seconds: 3),
        ),
      );

      // Open auth screen as fallback
      if (mounted) {
        try {
          // ignore: use_build_context_synchronously
          await context.push('/auth');
          return const LoginResult(success: false);
        } on Exception {
          return const LoginResult(success: false);
        }
      }
      return const LoginResult(success: false);
    }
  }
}
