import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jabook/features/auth/domain/entities/auth_status.dart';
import 'package:jabook/features/auth/domain/repositories/auth_repository.dart';

/// Provider for AuthRepository instance.
final authRepositoryProvider = Provider<AuthRepository>((ref) {
  // This provider should be overridden in the widget tree with proper context
  throw Exception('AuthRepositoryProvider must be overridden with proper context');
});

/// Provider for authentication status.
final authStatusProvider = StreamProvider<AuthStatus>((ref) {
  final repository = ref.watch(authRepositoryProvider);
  return repository.authStatus;
}, dependencies: [authRepositoryProvider]);

/// Provider for checking if user is logged in.
final isLoggedInProvider = FutureProvider<bool>((ref) {
  final repository = ref.watch(authRepositoryProvider);
  return repository.isLoggedIn();
}, dependencies: [authRepositoryProvider]);

/// Provider for checking if stored credentials exist.
final hasStoredCredentialsProvider = FutureProvider<bool>((ref) {
  final repository = ref.watch(authRepositoryProvider);
  return repository.hasStoredCredentials();
}, dependencies: [authRepositoryProvider]);