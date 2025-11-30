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
import 'package:jabook/core/domain/auth/use_cases/logout_use_case.dart';

/// Mock implementation of AuthRepository for testing.
class MockAuthRepository implements AuthRepository {
  bool _isLoggedIn = false;
  bool _shouldFail = false;
  bool _logoutCalled = false;

  @override
  Future<bool> isLoggedIn() async => _isLoggedIn;

  @override
  Future<bool> login(String username, String password) async {
    _isLoggedIn = true;
    return true;
  }

  @override
  Future<bool> loginWithCaptcha(
    String username,
    String password,
    String captchaCode,
    dynamic captchaData,
  ) async =>
      true;

  @override
  Future<void> logout() async {
    if (_shouldFail) {
      throw Exception('Logout failed');
    }
    _logoutCalled = true;
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
  set loggedIn(bool value) => _isLoggedIn = value;
  bool get logoutCalled => _logoutCalled;
}

void main() {
  group('LogoutUseCase', () {
    late MockAuthRepository mockRepository;
    late LogoutUseCase logoutUseCase;

    setUp(() {
      mockRepository = MockAuthRepository();
      logoutUseCase = LogoutUseCase(mockRepository);
    });

    test('should call repository logout', () async {
      // Arrange
      mockRepository.loggedIn = true;

      // Act
      await logoutUseCase();

      // Assert
      expect(mockRepository.logoutCalled, isTrue);
      expect(await mockRepository.isLoggedIn(), isFalse);
    });

    test('should throw exception when repository logout fails', () async {
      // Arrange
      mockRepository
        ..shouldFail = true
        ..loggedIn = true;

      // Act & Assert
      expect(
        () => logoutUseCase(),
        throwsException,
      );
    });
  });
}
