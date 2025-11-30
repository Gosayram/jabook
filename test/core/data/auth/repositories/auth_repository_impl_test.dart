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

import 'package:flutter_test/flutter_test.dart';
import 'package:jabook/core/data/auth/datasources/auth_local_datasource.dart';
import 'package:jabook/core/data/auth/datasources/auth_remote_datasource.dart';
import 'package:jabook/core/data/auth/repositories/auth_repository_impl.dart';
import 'package:jabook/core/domain/auth/entities/auth_status.dart';

/// Mock implementation of AuthRemoteDataSource for testing.
class MockAuthRemoteDataSource implements AuthRemoteDataSource {
  bool _isLoggedIn = false;
  bool _shouldFail = false;
  final _authStatusController = StreamController<bool>.broadcast();

  @override
  Future<bool> isLoggedIn() async {
    if (_shouldFail) {
      throw Exception('Failed to check login status');
    }
    return _isLoggedIn;
  }

  @override
  Future<bool> login(String username, String password) async {
    if (_shouldFail) {
      throw Exception('Login failed');
    }
    _isLoggedIn = true;
    _authStatusController.add(true);
    return true;
  }

  @override
  Future<bool> loginWithCaptcha(
    String username,
    String password,
    String captchaCode,
    dynamic captchaData,
  ) async {
    if (_shouldFail) {
      throw Exception('Login with captcha failed');
    }
    _isLoggedIn = true;
    _authStatusController.add(true);
    return true;
  }

  @override
  Future<void> logout() async {
    if (_shouldFail) {
      throw Exception('Logout failed');
    }
    _isLoggedIn = false;
    _authStatusController.add(false);
  }

  @override
  Future<void> refreshAuthStatus() async {}

  @override
  Stream<bool> get authStatusChanges => _authStatusController.stream;

  // Test helpers
  set shouldFail(bool value) => _shouldFail = value;
  set loggedIn(bool value) => _isLoggedIn = value;
  void emitAuthStatusChange(bool value) => _authStatusController.add(value);

  void dispose() => _authStatusController.close();
}

/// Mock implementation of AuthLocalDataSource for testing.
class MockAuthLocalDataSource implements AuthLocalDataSource {
  bool _hasCredentials = false;
  bool _shouldFail = false;

  @override
  Future<bool> hasStoredCredentials() async {
    if (_shouldFail) {
      throw Exception('Failed to check stored credentials');
    }
    return _hasCredentials;
  }

  @override
  Future<bool> loginWithStoredCredentials({bool useBiometric = false}) async {
    if (_shouldFail) {
      throw Exception('Login with stored credentials failed');
    }
    return _hasCredentials;
  }

  @override
  Future<bool> isBiometricAvailable() async => false;

  @override
  Future<void> saveCredentials({
    required String username,
    required String password,
    bool rememberMe = true,
  }) async {
    if (_shouldFail) {
      throw Exception('Save credentials failed');
    }
    _hasCredentials = true;
  }

  @override
  Future<void> clearStoredCredentials() async {
    if (_shouldFail) {
      throw Exception('Clear credentials failed');
    }
    _hasCredentials = false;
  }

  // Test helpers
  set shouldFail(bool value) => _shouldFail = value;
  set hasCredentials(bool value) => _hasCredentials = value;
}

void main() {
  group('AuthRepositoryImpl', () {
    late MockAuthRemoteDataSource mockRemoteDataSource;
    late MockAuthLocalDataSource mockLocalDataSource;
    late AuthRepositoryImpl repository;

    setUp(() {
      mockRemoteDataSource = MockAuthRemoteDataSource();
      mockLocalDataSource = MockAuthLocalDataSource();
      repository = AuthRepositoryImpl(
        mockRemoteDataSource,
        mockLocalDataSource,
      );
    });

    tearDown(() {
      mockRemoteDataSource.dispose();
    });

    test('should return login status from remote data source', () async {
      // Arrange
      mockRemoteDataSource.loggedIn = true;

      // Act
      final result = await repository.isLoggedIn();

      // Assert
      expect(result, isTrue);
    });

    test('should call remote data source login', () async {
      // Arrange
      const username = 'testuser';
      const password = 'testpass';

      // Act
      final result = await repository.login(username, password);

      // Assert
      expect(result, isTrue);
      expect(await mockRemoteDataSource.isLoggedIn(), isTrue);
    });

    test('should call local data source for stored credentials', () async {
      // Arrange
      mockLocalDataSource.hasCredentials = true;

      // Act
      final result = await repository.hasStoredCredentials();

      // Assert
      expect(result, isTrue);
    });

    test('should emit auth status changes', () async {
      // Arrange
      mockRemoteDataSource.loggedIn = false;

      // Act
      final statusStream = repository.authStatus;
      final statuses = <AuthStatus>[];
      final subscription = statusStream.listen(statuses.add);

      // Wait for initial status
      await Future.delayed(const Duration(milliseconds: 100));

      // Emit a change
      mockRemoteDataSource.emitAuthStatusChange(true);
      await Future.delayed(const Duration(milliseconds: 100));

      // Assert
      expect(statuses.length, greaterThanOrEqualTo(1));
      await subscription.cancel();
    });
  });
}
