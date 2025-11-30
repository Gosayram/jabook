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

import 'package:jabook/core/auth/captcha_detector.dart';
import 'package:jabook/core/data/auth/datasources/auth_local_datasource.dart';
import 'package:jabook/core/data/auth/datasources/auth_remote_datasource.dart';
import 'package:jabook/core/domain/auth/entities/auth_status.dart';
import 'package:jabook/core/domain/auth/repositories/auth_repository.dart';

/// Implementation of AuthRepository using data sources.
class AuthRepositoryImpl implements AuthRepository {
  /// Creates a new AuthRepositoryImpl instance.
  AuthRepositoryImpl(
    this._remoteDataSource,
    this._localDataSource,
  );

  final AuthRemoteDataSource _remoteDataSource;
  final AuthLocalDataSource _localDataSource;

  @override
  Future<bool> isLoggedIn() => _remoteDataSource.isLoggedIn();

  @override
  Future<bool> login(String username, String password) =>
      _remoteDataSource.login(username, password);

  @override
  Future<bool> loginWithCaptcha(
    String username,
    String password,
    String captchaCode,
    RutrackerCaptchaData captchaData,
  ) =>
      _remoteDataSource.loginWithCaptcha(
        username,
        password,
        captchaCode,
        captchaData,
      );

  @override
  Future<void> logout() => _remoteDataSource.logout();

  @override
  Future<bool> hasStoredCredentials() =>
      _localDataSource.hasStoredCredentials();

  @override
  Future<bool> loginWithStoredCredentials({bool useBiometric = false}) =>
      _localDataSource.loginWithStoredCredentials(
        useBiometric: useBiometric,
      );

  @override
  Future<bool> isBiometricAvailable() =>
      _localDataSource.isBiometricAvailable();

  @override
  Future<void> saveCredentials({
    required String username,
    required String password,
    bool rememberMe = true,
  }) =>
      _localDataSource.saveCredentials(
        username: username,
        password: password,
        rememberMe: rememberMe,
      );

  @override
  Future<void> clearStoredCredentials() =>
      _localDataSource.clearStoredCredentials();

  @override
  Stream<AuthStatus> get authStatus async* {
    // Initial status - always check current status first
    final loggedIn = await isLoggedIn();
    yield loggedIn ? AuthStatus.authenticated : AuthStatus.unauthenticated;

    // Listen for auth status changes
    // Always verify current status when we receive a change event
    // This ensures we have the latest status even if event was sent before subscription
    await for (final _ in _remoteDataSource.authStatusChanges) {
      // Verify current status to ensure we have the latest value
      final currentStatus = await isLoggedIn();
      yield currentStatus
          ? AuthStatus.authenticated
          : AuthStatus.unauthenticated;
    }
  }

  @override
  Future<void> refreshAuthStatus() => _remoteDataSource.refreshAuthStatus();
}
