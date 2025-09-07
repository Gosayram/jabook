import 'package:jabook/core/auth/rutracker_auth.dart';
import 'package:jabook/features/auth/domain/entities/auth_status.dart';
import 'package:jabook/features/auth/domain/repositories/auth_repository.dart';

/// Implementation of AuthRepository using RuTrackerAuth.
class AuthRepositoryImpl implements AuthRepository {
  /// Creates a new AuthRepositoryImpl instance.
  AuthRepositoryImpl(this._auth);

  final RuTrackerAuth _auth;

  @override
  Future<bool> isLoggedIn() => _auth.isLoggedIn;

  @override
  Future<bool> login(String username, String password) =>
      _auth.login(username, password);

  @override
  Future<void> logout() => _auth.logout();

  @override
  Future<bool> hasStoredCredentials() => _auth.hasStoredCredentials();

  @override
  Future<void> saveCredentials({
    required String username,
    required String password,
    bool rememberMe = true,
  }) =>
      _auth.saveCredentials(
        username: username,
        password: password,
        rememberMe: rememberMe,
      );

  @override
  Future<void> clearStoredCredentials() => _auth.clearStoredCredentials();

  @override
  Stream<AuthStatus> get authStatus async* {
    // Initial status
    final loggedIn = await isLoggedIn();
    yield loggedIn ? AuthStatus.authenticated : AuthStatus.unauthenticated;

    // TODO: Implement proper status streaming
    // This would require adding status change notifications to RuTrackerAuth
  }

  @override
  Future<void> refreshAuthStatus() async {
    // This method would trigger a status check and update the stream
    // For now, just check the status
    await isLoggedIn();
  }
}