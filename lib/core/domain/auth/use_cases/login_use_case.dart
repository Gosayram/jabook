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

import 'package:jabook/core/domain/auth/repositories/auth_repository.dart';

/// Use case for logging in with username and password.
class LoginUseCase {
  /// Creates a new LoginUseCase instance.
  LoginUseCase(this._repository);

  final AuthRepository _repository;

  /// Executes the login use case.
  ///
  /// Returns [true] if login was successful, [false] otherwise.
  Future<bool> call(String username, String password) =>
      _repository.login(username, password);
}
