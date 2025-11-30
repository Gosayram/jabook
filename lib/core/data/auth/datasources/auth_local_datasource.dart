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

import 'package:jabook/core/auth/rutracker_auth.dart';

/// Local data source for authentication operations.
///
/// This class handles local storage of credentials and biometric authentication.
abstract class AuthLocalDataSource {
  /// Checks if stored credentials are available.
  Future<bool> hasStoredCredentials();

  /// Attempts to login using stored credentials with optional biometric authentication.
  Future<bool> loginWithStoredCredentials({bool useBiometric = false});

  /// Checks if biometric authentication is available on the device.
  Future<bool> isBiometricAvailable();

  /// Saves credentials for future automatic login.
  Future<void> saveCredentials({
    required String username,
    required String password,
    bool rememberMe = true,
  });

  /// Clears all stored credentials.
  Future<void> clearStoredCredentials();
}

/// Implementation of AuthLocalDataSource using RuTrackerAuth.
class AuthLocalDataSourceImpl implements AuthLocalDataSource {
  /// Creates a new AuthLocalDataSourceImpl instance.
  AuthLocalDataSourceImpl(this._auth);

  final RuTrackerAuth _auth;

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
}
