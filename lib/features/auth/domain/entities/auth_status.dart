/// Represents the authentication status of the user.
enum AuthStatus {
  /// User is not authenticated.
  unauthenticated,

  /// User is currently authenticating.
  authenticating,

  /// User is successfully authenticated.
  authenticated,

  /// Authentication failed.
  failed,
}

/// Extension methods for AuthStatus enum.
extension AuthStatusExtension on AuthStatus {
  /// Returns a user-friendly description of the authentication status.
  String get description {
    switch (this) {
      case AuthStatus.unauthenticated:
        return 'Not authenticated';
      case AuthStatus.authenticating:
        return 'Authenticating...';
      case AuthStatus.authenticated:
        return 'Authenticated';
      case AuthStatus.failed:
        return 'Authentication failed';
    }
  }

  /// Returns whether the user is authenticated.
  bool get isAuthenticated => this == AuthStatus.authenticated;

  /// Returns whether authentication is in progress.
  bool get isAuthenticating => this == AuthStatus.authenticating;

  /// Returns whether authentication has failed.
  bool get hasFailed => this == AuthStatus.failed;
}
