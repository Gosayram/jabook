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

import 'package:flutter_test/flutter_test.dart';
import 'package:jabook/core/domain/auth/entities/auth_status.dart';
import 'package:jabook/core/domain/auth/repositories/auth_repository.dart';
import 'package:jabook/core/domain/auth/use_cases/login_use_case.dart';

/// Mock implementation of AuthRepository for testing.
class MockAuthRepository implements AuthRepository {
  bool _isLoggedIn = false;
  bool _shouldFail = false;
  String? _lastUsername;
  String? _lastPassword;

  @override
  Future<bool> isLoggedIn() async => _isLoggedIn;

  @override
  Future<bool> login(String username, String password) async {
    if (_shouldFail) {
      throw Exception('Login failed');
    }
    _lastUsername = username;
    _lastPassword = password;
    _isLoggedIn = true;
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
    _lastUsername = username;
    _lastPassword = password;
    _isLoggedIn = true;
    return true;
  }

  @override
  Future<void> logout() async {
    _isLoggedIn = false;
  }

  @override
  Future<bool> hasStoredCredentials() async => false;

  @override
  Future<bool> loginWithStoredCredentials({bool useBiometric = false}) async =>
      false;

  @override
  Future<bool> isBiometricAvailable() async => false;

  @override
  Future<void> saveCredentials({
    required String username,
    required String password,
    bool rememberMe = true,
  }) async {}

  @override
  Future<void> clearStoredCredentials() async {}

  @override
  Stream<AuthStatus> get authStatus => Stream.value(AuthStatus.unauthenticated);

  @override
  Future<void> refreshAuthStatus() async {}

  // Test helpers
  set shouldFail(bool value) => _shouldFail = value;
  String? get lastUsername => _lastUsername;
  String? get lastPassword => _lastPassword;
}

void main() {
  group('LoginUseCase', () {
    late MockAuthRepository mockRepository;
    late LoginUseCase loginUseCase;

    setUp(() {
      mockRepository = MockAuthRepository();
      loginUseCase = LoginUseCase(mockRepository);
    });

    test('should call repository login with correct credentials', () async {
      // Arrange
      const username = 'testuser';
      const password = 'testpass';

      // Act
      final result = await loginUseCase(username, password);

      // Assert
      expect(result, isTrue);
      expect(mockRepository.lastUsername, equals(username));
      expect(mockRepository.lastPassword, equals(password));
    });

    test('should throw exception when repository login fails', () async {
      // Arrange
      mockRepository.shouldFail = true;
      const username = 'testuser';
      const password = 'testpass';

      // Act & Assert
      expect(
        () => loginUseCase(username, password),
        throwsException,
      );
    });

    test('should return false when login fails', () async {
      // Arrange
      mockRepository.shouldFail = true;
      const username = 'testuser';
      const password = 'wrongpass';

      // Act & Assert
      expect(
        () => loginUseCase(username, password),
        throwsException,
      );
    });
  });
}
