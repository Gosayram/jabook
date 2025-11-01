import 'package:jabook/features/auth/domain/entities/auth_status.dart';

/// Repository interface for authentication operations.
abstract class AuthRepository {
  /// Checks if the user is currently authenticated.
  Future<bool> isLoggedIn();

  /// Logs in with the provided credentials.
  ///
  /// Returns [true] if login was successful, [false] otherwise.
  Future<bool> login(String username, String password);

  /// Logs out the current user.
  Future<void> logout();

  /// Checks if stored credentials are available.
  Future<bool> hasStoredCredentials();

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
