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
final accessLevelSyncProvider = Provider<void>((ref) {
  // Watch authentication status and sync with access level
  ref.watch(authStatusProvider).whenData((authStatus) {
    ref.read(accessProvider.notifier).initializeAccessLevel(
          authStatus.isAuthenticated,
        );
  });

  // Initialize guest mode on startup if no session exists
  ref.watch(isLoggedInProvider).whenData((isLoggedIn) {
    if (!isLoggedIn) {
      // If not logged in, ensure guest mode is set
      final accessNotifier = ref.read(accessProvider.notifier);
      final currentState = ref.read(accessProvider);
      if (currentState.accessLevel != UserAccessLevel.guest) {
        accessNotifier.setGuestMode();
      }
    }
  });
});
