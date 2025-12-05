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

/// Service for secure storage operations using FlutterSecureStorage.
///
/// This service provides a unified interface for storing and retrieving
/// sensitive data securely across the application.
///
/// Use this service for storing credentials, tokens, and other sensitive data.
class SecureStorageService {
  /// Creates a new SecureStorageService instance.
  const SecureStorageService();

  /// Secure storage instance.
  static const FlutterSecureStorage _storage = FlutterSecureStorage();

  /// Writes a value to secure storage.
  ///
  /// The [key] parameter is the storage key.
  /// The [value] parameter is the value to store.
  Future<void> write(String key, String value) async {
    await _storage.write(key: key, value: value);
  }

  /// Reads a value from secure storage.
  ///
  /// The [key] parameter is the storage key.
  ///
  /// Returns the stored value or null if not found.
  Future<String?> read(String key) async => _storage.read(key: key);

  /// Deletes a value from secure storage.
  ///
  /// The [key] parameter is the storage key.
  Future<void> delete(String key) async {
    await _storage.delete(key: key);
  }

  /// Deletes all values from secure storage.
  Future<void> deleteAll() async {
    await _storage.deleteAll();
  }

  /// Checks if a key exists in secure storage.
  ///
  /// The [key] parameter is the storage key.
  ///
  /// Returns true if the key exists, false otherwise.
  Future<bool> containsKey(String key) async {
    final value = await read(key);
    return value != null;
  }
}
