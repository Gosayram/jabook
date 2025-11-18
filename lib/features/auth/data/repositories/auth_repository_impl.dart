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
  Future<bool> login(String username, String password) async {
    // Try HTTP login first (faster and more reliable)
    try {
      final httpSuccess = await _auth.loginViaHttp(username, password);
      if (httpSuccess) {
        return true;
      }
    } on Exception {
      // HTTP login failed, fallback to WebView
    }

    // Fallback to WebView login if HTTP login failed
    return _auth.login(username, password);
  }

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
    // Initial status
    final loggedIn = await isLoggedIn();
    yield loggedIn ? AuthStatus.authenticated : AuthStatus.unauthenticated;

    // Listen for auth status changes
    await for (final isAuthenticated in _auth.authStatusChanges) {
      yield isAuthenticated
          ? AuthStatus.authenticated
          : AuthStatus.unauthenticated;
    }
  }

  @override
  Future<void> refreshAuthStatus() async {
    // Just check the status - the stream will update automatically
    // through auth status changes from RuTrackerAuth
    await isLoggedIn();
  }
}
