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

/// Remote data source for authentication operations.
///
/// This class wraps RuTrackerAuth to provide a clean interface
/// for authentication operations that interact with remote servers.
abstract class AuthRemoteDataSource {
  /// Checks if the user is currently authenticated.
  Future<bool> isLoggedIn();

  /// Logs in with the provided credentials.
  Future<bool> login(String username, String password);

  /// Logs in with the provided credentials and captcha code.
  Future<bool> loginWithCaptcha(
    String username,
    String password,
    String captchaCode,
    dynamic captchaData,
  );

  /// Logs out the current user.
  Future<void> logout();

  /// Refreshes the authentication status.
  Future<void> refreshAuthStatus();

  /// Stream of authentication status changes.
  Stream<bool> get authStatusChanges;
}

/// Implementation of AuthRemoteDataSource using RuTrackerAuth.
class AuthRemoteDataSourceImpl implements AuthRemoteDataSource {
  /// Creates a new AuthRemoteDataSourceImpl instance.
  AuthRemoteDataSourceImpl(this._auth);

  final RuTrackerAuth _auth;

  @override
  Future<bool> isLoggedIn() => _auth.isLoggedIn;

  @override
  Future<bool> login(String username, String password) =>
      _auth.login(username, password);

  @override
  Future<bool> loginWithCaptcha(
    String username,
    String password,
    String captchaCode,
    dynamic captchaData,
  ) =>
      _auth.loginViaHttpWithCaptcha(
        username,
        password,
        captchaCode,
        captchaData,
      );

  @override
  Future<void> logout() => _auth.logout();

  @override
  Future<void> refreshAuthStatus() => _auth.refreshAuthStatus();

  @override
  Stream<bool> get authStatusChanges => _auth.authStatusChanges;
}
