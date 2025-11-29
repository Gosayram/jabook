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
import 'package:jabook/core/domain/auth/entities/auth_status.dart';
import 'package:jabook/core/domain/auth/repositories/auth_repository.dart';
import 'package:jabook/core/domain/auth/use_cases/check_auth_status_use_case.dart';
import 'package:jabook/core/domain/auth/use_cases/login_use_case.dart';
import 'package:jabook/core/domain/auth/use_cases/logout_use_case.dart';

/// Test implementation of AuthRepository for integration testing.
///
/// This implementation provides a simple in-memory storage for testing
/// authentication flows without external dependencies.
class TestAuthRepository implements AuthRepository {
  bool _isLoggedIn = false;
  String? _storedUsername;
  String? _storedPassword;
  final _statusController = StreamController<AuthStatus>.broadcast();

  @override
  Future<bool> isLoggedIn() async => _isLoggedIn;

  @override
  Future<bool> login(String username, String password) async {
    // Simple validation for testing
    if (username.isEmpty || password.isEmpty) {
      return false;
    }
    _isLoggedIn = true;
    _storedUsername = username;
    _storedPassword = password;
    _statusController.add(AuthStatus.authenticated);
    return true;
  }

  @override
  Future<bool> loginWithCaptcha(
    String username,
    String password,
    String captchaCode,
    dynamic captchaData,
  ) async {
    if (username.isEmpty || password.isEmpty || captchaCode.isEmpty) {
      return false;
    }
    _isLoggedIn = true;
    _statusController.add(AuthStatus.authenticated);
    return true;
  }

  @override
  Future<void> logout() async {
    _isLoggedIn = false;
    _storedUsername = null;
    _storedPassword = null;
    _statusController.add(AuthStatus.unauthenticated);
  }

  @override
  Future<bool> hasStoredCredentials() async =>
      _storedUsername != null && _storedPassword != null;

  @override
  Future<bool> loginWithStoredCredentials({bool useBiometric = false}) async {
    if (_storedUsername == null || _storedPassword == null) {
      return false;
    }
    return login(_storedUsername!, _storedPassword!);
  }

  @override
  Future<bool> isBiometricAvailable() async => false;

  @override
  Future<void> saveCredentials({
    required String username,
    required String password,
    bool rememberMe = true,
  }) async {
    if (rememberMe) {
      _storedUsername = username;
      _storedPassword = password;
    }
  }

  @override
  Future<void> clearStoredCredentials() async {
    _storedUsername = null;
    _storedPassword = null;
  }

  @override
  Stream<AuthStatus> get authStatus => _statusController.stream;

  @override
  Future<void> refreshAuthStatus() async {}

  void dispose() => _statusController.close();
}

void main() {
  group('Auth Flow Integration Tests', () {
    late TestAuthRepository testRepository;
    late LoginUseCase loginUseCase;
    late LogoutUseCase logoutUseCase;
    late CheckAuthStatusUseCase checkAuthStatusUseCase;

    setUp(() {
      testRepository = TestAuthRepository();
      loginUseCase = LoginUseCase(testRepository);
      logoutUseCase = LogoutUseCase(testRepository);
      checkAuthStatusUseCase = CheckAuthStatusUseCase(testRepository);
    });

    tearDown(() {
      testRepository.dispose();
    });

    test('complete login and logout flow', () async {
      // Arrange
      const username = 'testuser';
      const password = 'testpass';

      // Act & Assert - Initial state
      expect(await checkAuthStatusUseCase(), isFalse);

      // Act & Assert - Login
      final loginResult = await loginUseCase(username, password);
      expect(loginResult, isTrue);
      expect(await checkAuthStatusUseCase(), isTrue);

      // Act & Assert - Logout
      await logoutUseCase();
      expect(await checkAuthStatusUseCase(), isFalse);
    });

    test('login with stored credentials flow', () async {
      // Arrange
      const username = 'testuser';
      const password = 'testpass';

      // Act - Login and save credentials
      await loginUseCase(username, password);
      await testRepository.saveCredentials(
        username: username,
        password: password,
      );

      // Logout
      await logoutUseCase();
      expect(await checkAuthStatusUseCase(), isFalse);

      // Act - Login with stored credentials
      final result = await testRepository.loginWithStoredCredentials();
      expect(result, isTrue);
      expect(await checkAuthStatusUseCase(), isTrue);
    });

    test('auth status stream updates correctly', () async {
      // Arrange
      const username = 'testuser';
      const password = 'testpass';
      final statuses = <AuthStatus>[];
      final subscription = testRepository.authStatus.listen(statuses.add);

      // Act - Login
      await loginUseCase(username, password);
      await Future.delayed(const Duration(milliseconds: 50));

      // Act - Logout
      await logoutUseCase();
      await Future.delayed(const Duration(milliseconds: 50));

      // Assert
      expect(statuses.length, greaterThanOrEqualTo(2));
      expect(statuses.last, equals(AuthStatus.unauthenticated));

      await subscription.cancel();
    });
  });
}
