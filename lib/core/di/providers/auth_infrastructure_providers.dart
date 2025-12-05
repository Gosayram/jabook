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
import 'package:jabook/core/auth/credential_manager.dart';
import 'package:jabook/core/auth/rutracker_auth.dart';
import 'package:jabook/core/di/providers/auth_providers.dart';
import 'package:jabook/core/di/providers/cache_providers.dart';
import 'package:jabook/core/di/providers/database_providers.dart';
import 'package:jabook/core/session/session_manager.dart';

/// Provider for CredentialManager instance.
///
/// This provider creates a single instance of CredentialManager that is shared
/// throughout the application lifecycle. The instance is created lazily when
/// first accessed.
final credentialManagerProvider =
    Provider<CredentialManager>((ref) => CredentialManager());

/// Provider for SessionManager instance.
///
/// This provider creates a SessionManager instance that depends on RuTrackerAuth.
/// The SessionManager is created lazily when first accessed.
///
/// Note: RuTrackerAuth is optional - SessionManager can work without it for basic operations.
/// This prevents circular dependency issues during initialization.
final sessionManagerProvider = Provider<SessionManager>((ref) {
  // Try to get rutrackerAuth, but don't fail if it's not available
  // This allows SessionManager to work even if rutrackerAuthProvider is not initialized
  RuTrackerAuth? rutrackerAuth;
  try {
    rutrackerAuth = ref.read(rutrackerAuthProvider);
  } on Exception {
    // rutrackerAuthProvider not initialized yet - that's okay
    // SessionManager can work without it for basic operations like clearSession
  }

  final appDatabase = ref.watch(appDatabaseProvider);
  final cacheService = ref.watch(rutrackerCacheServiceProvider);
  return SessionManager(
    rutrackerAuth: rutrackerAuth,
    appDatabase: appDatabase,
    cacheService: cacheService,
  );
});
