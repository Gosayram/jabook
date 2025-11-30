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

import 'package:jabook/core/auth/captcha_detector.dart';
import 'package:jabook/core/domain/auth/entities/auth_status.dart';

/// Repository interface for authentication operations.
abstract class AuthRepository {
  /// Checks if the user is currently authenticated.
  Future<bool> isLoggedIn();

  /// Logs in with the provided credentials.
  ///
  /// Returns [true] if login was successful, [false] otherwise.
  Future<bool> login(String username, String password);

  /// Logs in with the provided credentials and captcha code.
  ///
  /// Returns [true] if login was successful, [false] otherwise.
  Future<bool> loginWithCaptcha(
    String username,
    String password,
    String captchaCode,
    RutrackerCaptchaData captchaData,
  );

  /// Logs out the current user.
  Future<void> logout();

  /// Checks if stored credentials are available.
  Future<bool> hasStoredCredentials();

  /// Attempts to login using stored credentials with optional biometric authentication.
  ///
  /// Returns [true] if login was successful, [false] otherwise.
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

  /// Gets the current authentication status.
  Stream<AuthStatus> get authStatus;

  /// Refreshes the authentication status.
  Future<void> refreshAuthStatus();
}
