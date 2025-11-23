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

import 'dart:async';
import 'package:jabook/core/auth/rutracker_auth.dart';
import 'package:jabook/features/auth/domain/entities/auth_status.dart';
import 'package:jabook/features/auth/domain/repositories/auth_repository.dart';

/// Implementation of AuthRepository using RuTrackerAuth.
class AuthRepositoryImpl implements AuthRepository {
  /// Creates a new AuthRepositoryImpl instance.
  AuthRepositoryImpl(this._auth);

  final RuTrackerAuth _auth;

  @override
  Future<bool> isLoggedIn() => _auth.isLoggedIn;

  @override
  Future<bool> login(String username, String password) =>
      // Use direct HTTP authentication (no WebView)
      // The login() method in RuTrackerAuth now uses DirectAuthService
      _auth.login(username, password);

  @override
  Future<void> logout() => _auth.logout();

  @override
  Future<bool> hasStoredCredentials() => _auth.hasStoredCredentials();

  @override
  Future<bool> loginWithStoredCredentials({bool useBiometric = false}) =>
      _auth.loginWithStoredCredentials(useBiometric: useBiometric);

  @override
  Future<bool> isBiometricAvailable() => _auth.isBiometricAvailable();

  @override
  Future<void> saveCredentials({
    required String username,
    required String password,
    bool rememberMe = true,
  }) =>
      _auth.saveCredentials(
        username: username,
        password: password,
        rememberMe: rememberMe,
      );

  @override
  Future<void> clearStoredCredentials() => _auth.clearStoredCredentials();

  @override
  Stream<AuthStatus> get authStatus async* {
    // Initial status - always check current status first
    final loggedIn = await isLoggedIn();
    yield loggedIn ? AuthStatus.authenticated : AuthStatus.unauthenticated;

    // Listen for auth status changes
    // Always verify current status when we receive a change event
    // This ensures we have the latest status even if event was sent before subscription
    await for (final _ in _auth.authStatusChanges) {
      // Verify current status to ensure we have the latest value
      final currentStatus = await isLoggedIn();
      yield currentStatus
          ? AuthStatus.authenticated
          : AuthStatus.unauthenticated;
    }
  }

  @override
  Future<void> refreshAuthStatus() async {
    // Delegate to RuTrackerAuth to check status and update stream
    // This ensures the authStatusChanges stream emits the current status
    await _auth.refreshAuthStatus();
  }
}
