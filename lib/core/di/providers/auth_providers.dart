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
import 'package:jabook/core/auth/access_provider.dart';
import 'package:jabook/core/auth/rutracker_auth.dart';
import 'package:jabook/core/domain/auth/entities/auth_status.dart';
import 'package:jabook/core/domain/auth/entities/user_access_level.dart';
import 'package:jabook/core/domain/auth/repositories/auth_repository.dart';
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:jabook/core/session/session_storage.dart';
import 'package:jabook/core/utils/safe_async.dart';
import 'package:jabook/features/auth/data/repositories/auth_repository_impl.dart';
import 'package:riverpod/legacy.dart';

/// Provider for RuTrackerAuth instance.
///
/// This provider requires a BuildContext and should be overridden
/// in the widget tree with proper context.
final rutrackerAuthProvider = Provider<RuTrackerAuth>((ref) {
  throw Exception(
      'rutrackerAuthProvider must be overridden with proper context');
});

/// Provider for AuthRepository instance.
///
/// This provider creates an AuthRepositoryImpl using RuTrackerAuth directly.
/// This is the preferred implementation as it simplifies the architecture
/// and works directly with RuTrackerAuth without intermediate data sources.
final authRepositoryProvider = Provider<AuthRepository>((ref) {
  // Use ref.read instead of ref.watch to avoid unnecessary rebuilds
  // and to handle errors more gracefully
  try {
    final auth = ref.read(rutrackerAuthProvider);
    return AuthRepositoryImpl(auth);
  } on Exception catch (e) {
    // Log error but rethrow - provider must be in error state
    // This allows callers to handle the error gracefully
    StructuredLogger().log(
      level: 'error',
      subsystem: 'auth',
      message: 'Failed to create AuthRepository - rutrackerAuthProvider error',
      cause: e.toString(),
    );
    rethrow;
  }
});

/// Provider for authentication status.
final authStatusProvider = StreamProvider<AuthStatus>((ref) {
  final repository = ref.watch(authRepositoryProvider);
  return repository.authStatus;
});

/// Provider for checking if user is logged in.
final isLoggedInProvider = FutureProvider<bool>((ref) {
  final repository = ref.watch(authRepositoryProvider);
  return repository.isLoggedIn();
});

/// Provider for checking if stored credentials exist.
final hasStoredCredentialsProvider = FutureProvider<bool>((ref) {
  final repository = ref.watch(authRepositoryProvider);
  return repository.hasStoredCredentials();
});

/// Provider for access control management.
///
/// This provider manages user access level (guest or full) and provides
/// methods to check feature access and upgrade access level.
final accessProvider = StateNotifierProvider<AccessNotifier, AccessState>(
    (ref) => AccessNotifier());

/// Provider that synchronizes access level with authentication status.
///
/// This provider watches the authentication status and automatically
/// updates the access level accordingly.
/// Also initializes guest mode if no session exists.
///
/// Important: This provider checks for saved session in storage before
/// setting guest mode to avoid resetting access level before session restoration.
final accessLevelSyncProvider = Provider<void>((ref) {
  // Watch authentication status and sync with access level
  ref.watch(authStatusProvider).whenData((authStatus) {
    ref.read(accessProvider.notifier).initializeAccessLevel(
          authStatus.isAuthenticated,
        );
  });

  // Initialize guest mode on startup if no session exists
  // But first check if session exists in storage to avoid resetting
  // access level before session restoration completes
  ref.watch(isLoggedInProvider).whenData((isLoggedIn) {
    if (!isLoggedIn) {
      // Before setting guest mode, check if session exists in storage
      // If session exists but not yet restored, don't set guest mode
      // Use safeUnawaited to perform async check without blocking
      safeUnawaited(
        () async {
          try {
            const sessionStorage = SessionStorage();
            final hasSession = await sessionStorage.hasSession();

            if (!hasSession) {
              // No session in storage - safe to set guest mode
              final accessNotifier = ref.read(accessProvider.notifier);
              final currentState = ref.read(accessProvider);
              if (currentState.accessLevel != UserAccessLevel.guest) {
                accessNotifier.setGuestMode();
              }
            } else {
              // Session exists in storage but not yet restored
              // Don't set guest mode - wait for session restoration
              // The access level will be updated when session is restored
              // and authStatusProvider emits authenticated status
              await StructuredLogger().log(
                level: 'debug',
                subsystem: 'access',
                message:
                    'Session exists in storage, waiting for restoration before setting access level',
              );
            }
          } on Exception catch (e) {
            // If check fails, err on the side of caution and don't set guest mode
            // This prevents accidentally resetting access level if there's a session
            await StructuredLogger().log(
              level: 'warning',
              subsystem: 'access',
              message:
                  'Failed to check session storage, not setting guest mode',
              cause: e.toString(),
            );
          }
        }(),
      );
    }
  });
});
