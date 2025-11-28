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
import 'package:jabook/core/auth/rutracker_auth.dart';
import 'package:jabook/core/data/auth/datasources/auth_local_datasource.dart';
import 'package:jabook/core/data/auth/datasources/auth_remote_datasource.dart';
import 'package:jabook/core/data/auth/repositories/auth_repository_impl.dart';
import 'package:jabook/core/domain/auth/entities/auth_status.dart';
import 'package:jabook/core/domain/auth/repositories/auth_repository.dart';

/// Provider for RuTrackerAuth instance.
///
/// This provider requires a BuildContext and should be overridden
/// in the widget tree with proper context.
final rutrackerAuthProvider = Provider<RuTrackerAuth>((ref) {
  throw Exception(
      'rutrackerAuthProvider must be overridden with proper context');
});

/// Provider for AuthRemoteDataSource instance.
final authRemoteDataSourceProvider =
    Provider<AuthRemoteDataSource>((ref) {
  final auth = ref.watch(rutrackerAuthProvider);
  return AuthRemoteDataSourceImpl(auth);
});

/// Provider for AuthLocalDataSource instance.
final authLocalDataSourceProvider = Provider<AuthLocalDataSource>((ref) {
  final auth = ref.watch(rutrackerAuthProvider);
  return AuthLocalDataSourceImpl(auth);
});

/// Provider for AuthRepository instance.
///
/// This provider creates an AuthRepositoryImpl using remote and local data sources.
final authRepositoryProvider = Provider<AuthRepository>((ref) {
  final remoteDataSource = ref.watch(authRemoteDataSourceProvider);
  final localDataSource = ref.watch(authLocalDataSourceProvider);
  return AuthRepositoryImpl(remoteDataSource, localDataSource);
});

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

