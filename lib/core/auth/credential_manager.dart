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

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:jabook/core/errors/failures.dart';
import 'package:local_auth/local_auth.dart';

/// Manages secure storage and retrieval of user credentials.
/// Supports biometric authentication for accessing stored credentials.
class CredentialManager {
  /// Private constructor for singleton pattern.
  CredentialManager._();

  /// Factory constructor to get the singleton instance.
  factory CredentialManager() => _instance;

  /// Singleton instance of the CredentialManager.
  static final CredentialManager _instance = CredentialManager._();

  /// Secure storage for credentials.
  final FlutterSecureStorage _secureStorage = const FlutterSecureStorage();

  /// Local authentication for biometric verification.
  final LocalAuth _localAuth = LocalAuth();

  /// Key for storing username in secure storage.
  static const String _usernameKey = 'rutracker_username';

  /// Key for storing password in secure storage.
  static const String _passwordKey = 'rutracker_password';

  /// Key for storing remember me preference.
  static const String _rememberMeKey = 'remember_credentials';

  /// Saves user credentials securely.
  ///
  /// If [rememberMe] is true, credentials will be stored for future use.
  /// If [useBiometric] is true, requires biometric authentication for access.
  Future<void> saveCredentials({
    required String username,
    required String password,
    bool rememberMe = true,
  }) async {
    try {
      if (rememberMe) {
        await _secureStorage.write(key: _usernameKey, value: username);
        await _secureStorage.write(key: _passwordKey, value: password);
        await _secureStorage.write(key: _rememberMeKey, value: 'true');
      } else {
        await clearCredentials();
      }
    } on Exception catch (e) {
      throw AuthFailure('Failed to save credentials: ${e.toString()}');
    }
  }

  /// Retrieves stored credentials with optional biometric authentication.
  ///
  /// Returns a map with 'username' and 'password' if credentials are available.
  /// Returns null if no credentials are stored or authentication fails.
  Future<Map<String, String>?> getCredentials(
      {bool requireBiometric = false}) async {
    try {
      final rememberMe = await _secureStorage.read(key: _rememberMeKey);

      if (rememberMe != 'true') {
        return null;
      }

      if (requireBiometric) {
        final canAuthenticate = await _localAuth.canCheckBiometrics();
        if (canAuthenticate) {
          final authenticated = await _localAuth.authenticate(
            localizedReason:
                'Authenticate to access your RuTracker credentials',
          );

          if (!authenticated) {
            return null;
          }
        }
      }

      final username = await _secureStorage.read(key: _usernameKey);
      final password = await _secureStorage.read(key: _passwordKey);

      if (username == null || password == null) {
        return null;
      }

      return {
        'username': username,
        'password': password,
      };
    } on Exception catch (e) {
      throw AuthFailure('Failed to retrieve credentials: ${e.toString()}');
    }
  }

  /// Clears all stored credentials.
  Future<void> clearCredentials() async {
    try {
      await _secureStorage.delete(key: _usernameKey);
      await _secureStorage.delete(key: _passwordKey);
      await _secureStorage.delete(key: _rememberMeKey);
    } on Exception catch (e) {
      throw AuthFailure('Failed to clear credentials: ${e.toString()}');
    }
  }

  /// Checks if credentials are stored and available.
  Future<bool> hasStoredCredentials() async {
    try {
      final rememberMe = await _secureStorage.read(key: _rememberMeKey);
      final username = await _secureStorage.read(key: _usernameKey);
      final password = await _secureStorage.read(key: _passwordKey);

      return rememberMe == 'true' && username != null && password != null;
    } on Exception {
      return false;
    }
  }

  /// Checks if biometric authentication is available on the device.
  Future<bool> isBiometricAvailable() async {
    try {
      return await _localAuth.canCheckBiometrics();
    } on Exception {
      return false;
    }
  }

  /// Exports credentials in standard formats (CSV, JSON).
  Future<String> exportCredentials({String format = 'json'}) async {
    final credentials = await getCredentials();
    if (credentials == null) {
      throw const AuthFailure('No credentials to export');
    }

    switch (format.toLowerCase()) {
      case 'csv':
        return 'username,password\n${credentials['username']},${credentials['password']}';
      case 'json':
        return '{"username":"${credentials['username']}","password":"${credentials['password']}"}';
      default:
        throw AuthFailure('Unsupported export format: $format');
    }
  }

  /// Imports credentials from standard formats.
  Future<void> importCredentials(String data, {String format = 'json'}) async {
    try {
      late Map<String, String> credentials;

      switch (format.toLowerCase()) {
        case 'csv':
          final lines = data.split('\n');
          if (lines.length < 2) {
            throw const FormatException('Invalid CSV format');
          }

          final values = lines[1].split(',');
          if (values.length != 2) {
            throw const FormatException('Invalid CSV data');
          }

          credentials = {
            'username': values[0],
            'password': values[1],
          };
          break;

        case 'json':
          final jsonData = data.replaceAllMapped(
            RegExp(r'"username":"([^"]+)","password":"([^"]+)"'),
            (match) => '"username":"${match[1]}","password":"${match[2]}"',
          );

          // Simple JSON parsing (for demonstration)
          final usernameMatch =
              RegExp(r'"username":"([^"]+)"').firstMatch(jsonData);
          final passwordMatch =
              RegExp(r'"password":"([^"]+)"').firstMatch(jsonData);

          if (usernameMatch == null || passwordMatch == null) {
            throw const FormatException('Invalid JSON format');
          }

          credentials = {
            'username': usernameMatch.group(1)!,
            'password': passwordMatch.group(1)!,
          };
          break;

        default:
          throw AuthFailure('Unsupported import format: $format');
      }

      await saveCredentials(
        username: credentials['username']!,
        password: credentials['password']!,
      );
    } on Exception catch (e) {
      throw AuthFailure('Failed to import credentials: ${e.toString()}');
    }
  }
}

/// Local authentication wrapper with error handling.
class LocalAuth {
  final LocalAuthentication _auth = LocalAuthentication();

  /// Checks if biometric authentication is available.
  Future<bool> canCheckBiometrics() async {
    try {
      final canCheck = await _auth.canCheckBiometrics;
      final isAvailable = await _auth.isDeviceSupported();
      return canCheck && isAvailable;
    } on Exception {
      return false;
    }
  }

  /// Authenticates user with biometrics.
  Future<bool> authenticate({
    required String localizedReason,
    bool biometricOnly = true,
    bool sensitiveTransaction = true,
    bool persistAcrossBackgrounding = true,
  }) async {
    try {
      return await _auth.authenticate(
        localizedReason: localizedReason,
        biometricOnly: biometricOnly,
        sensitiveTransaction: sensitiveTransaction,
        persistAcrossBackgrounding: persistAcrossBackgrounding,
      );
    } on Exception {
      return false;
    }
  }

  /// Gets available biometric types.
  Future<List<BiometricType>> getAvailableBiometrics() async {
    try {
      return await _auth.getAvailableBiometrics();
    } on Exception {
      return [];
    }
  }
}
