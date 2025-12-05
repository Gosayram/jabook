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

import 'package:jabook/core/domain/auth/entities/auth_status.dart';
import 'package:jabook/core/domain/auth/entities/user_credentials.dart';
import 'package:jabook/core/domain/auth/repositories/auth_repository.dart';

/// Test implementation of AuthRepository.
///
/// This implementation provides a simple in-memory storage for testing
/// authentication flows without external dependencies. It follows the
/// Test Doubles pattern from Now In Android.
///
/// **Test Hooks**:
/// - `addTestSession()` - Add a test session
/// - `setShouldFail()` - Simulate failures
/// - `setLoggedIn()` - Set login state directly
class TestAuthRepository implements AuthRepository {
  /// Creates a new TestAuthRepository instance.
  TestAuthRepository();

  bool _isLoggedIn = false;
  bool _shouldFail = false;
  String? _storedUsername;
  String? _storedPassword;
  bool _hasStoredCredentials = false;
  final _statusController = StreamController<AuthStatus>.broadcast();

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
    if (_shouldFail) {
      throw Exception('Login with captcha failed');
    }
    if (username.isEmpty || password.isEmpty || captchaCode.isEmpty) {
      return false;
    }
    _isLoggedIn = true;
    _statusController.add(AuthStatus.authenticated);
    return true;
  }

  @override
  Future<void> logout() async {
    if (_shouldFail) {
      throw Exception('Logout failed');
    }
    _isLoggedIn = false;
    _statusController.add(AuthStatus.unauthenticated);
  }

  @override
  Future<bool> hasStoredCredentials() async {
    if (_shouldFail) {
      throw Exception('Failed to check stored credentials');
    }
    return _hasStoredCredentials;
  }

  @override
  Future<bool> loginWithStoredCredentials({bool useBiometric = false}) async {
    if (_shouldFail) {
      throw Exception('Login with stored credentials failed');
    }
    if (!_hasStoredCredentials || _storedUsername == null) {
      return false;
    }
    return login(_storedUsername!, _storedPassword ?? '');
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
    if (rememberMe) {
      _storedUsername = username;
      _storedPassword = password;
      _hasStoredCredentials = true;
    }
  }

  @override
  Future<void> clearStoredCredentials() async {
    if (_shouldFail) {
      throw Exception('Clear credentials failed');
    }
    _storedUsername = null;
    _storedPassword = null;
    _hasStoredCredentials = false;
  }

  @override
  Future<UserCredentials?> getStoredCredentials() async {
    if (_shouldFail) {
      throw Exception('Get stored credentials failed');
    }
    if (!_hasStoredCredentials ||
        _storedUsername == null ||
        _storedPassword == null) {
      return null;
    }
    return UserCredentials(
      username: _storedUsername!,
      password: _storedPassword!,
    );
  }

  @override
  Stream<AuthStatus> get authStatus => Stream.multi((controller) {
        // Emit initial status
        controller.add(
          _isLoggedIn ? AuthStatus.authenticated : AuthStatus.unauthenticated,
        );
        // Listen for changes
        _statusController.stream.listen(
          controller.add,
          onError: controller.addError,
          onDone: controller.close,
        );
      });

  @override
  Future<void> refreshAuthStatus() async {
    if (_shouldFail) {
      throw Exception('Refresh auth status failed');
    }
    // In test implementation, just emit current status
    _statusController.add(
      _isLoggedIn ? AuthStatus.authenticated : AuthStatus.unauthenticated,
    );
  }

  // Test hooks
  /// Sets whether operations should fail.
  set shouldFail(bool value) => _shouldFail = value;

  /// Sets the login state directly.
  set loggedIn(bool value) {
    _isLoggedIn = value;
    _statusController.add(
      value ? AuthStatus.authenticated : AuthStatus.unauthenticated,
    );
  }

  /// Sets stored credentials for testing.
  void setStoredCredentials(String username, String password) {
    _storedUsername = username;
    _storedPassword = password;
    _hasStoredCredentials = true;
  }

  /// Clears all test data.
  void clear() {
    _isLoggedIn = false;
    _storedUsername = null;
    _storedPassword = null;
    _hasStoredCredentials = false;
    _shouldFail = false;
  }

  /// Disposes the repository and releases resources.
  void dispose() => _statusController.close();
}
