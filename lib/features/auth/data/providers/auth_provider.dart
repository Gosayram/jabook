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

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/core/di/providers/auth_providers.dart';
import 'package:jabook/core/domain/auth/entities/auth_status.dart';

/// Provider for authentication status.
final authStatusProvider = StreamProvider<AuthStatus>((ref) {
  final repository = ref.watch(authRepositoryProvider);
  return repository.authStatus;
});

/// Provider for checking if user is logged in.
final isLoggedInProvider = FutureProvider<bool>((ref) {
  final repository = ref.watch(authRepositoryProvider);
  return repository.isLoggedIn();
});

/// Provider for checking if stored credentials exist.
final hasStoredCredentialsProvider = FutureProvider<bool>((ref) {
  final repository = ref.watch(authRepositoryProvider);
  return repository.hasStoredCredentials();
});
