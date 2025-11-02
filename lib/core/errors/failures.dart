/// Base failure class for all domain errors.
abstract class Failure implements Exception {
  /// Creates a new Failure instance.
  ///
  /// [message] describes the failure; [exception] can hold an underlying cause.
  const Failure(this.message, [this.exception]);

  /// Human-readable description of the failure.
  final String message;

  /// Original exception that caused this failure, if available.
  final Exception? exception;

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is Failure &&
        other.message == message &&
        other.exception == exception;
  }

  @override
  int get hashCode => message.hashCode ^ (exception?.hashCode ?? 0);

  @override
  String toString() => 'Failure(message: $message, exception: $exception)';
}

/// Network-related failures.
class NetworkFailure extends Failure {
  /// Creates a new [NetworkFailure].
  const NetworkFailure(super.message, [super.exception]);
}

/// Authentication-related failures.
class AuthFailure extends Failure {
  /// Creates a new [AuthFailure].
  const AuthFailure(super.message, [super.exception]);

  /// Creates an authentication failure that requires user action (login).
  const AuthFailure.loginRequired([Exception? exception])
      : super('Authentication required. Please login to RuTracker.', exception);

  /// Creates an authentication failure for expired session.
  const AuthFailure.sessionExpired([Exception? exception])
      : super('Session expired. Please login again.', exception);

  /// Creates an authentication failure for invalid credentials.
  const AuthFailure.invalidCredentials([Exception? exception])
      : super('Invalid username or password. Please try again.', exception);
}

/// Parsing-related failures.
class ParsingFailure extends Failure {
  /// Creates a new [ParsingFailure].
  const ParsingFailure(super.message, [super.exception]);
}

/// Database-related failures.
class DatabaseFailure extends Failure {
  /// Creates a new [DatabaseFailure].
  const DatabaseFailure(super.message, [super.exception]);
}

/// Torrent-related failures.
class TorrentFailure extends Failure {
  /// Creates a new [TorrentFailure].
  const TorrentFailure(super.message, [super.exception]);
}

/// Audio-related failures.
class AudioFailure extends Failure {
  /// Creates a new [AudioFailure].
  const AudioFailure(super.message, [super.exception]);
}

/// Settings-related failures.
class SettingsFailure extends Failure {
  /// Creates a new [SettingsFailure].
  const SettingsFailure(super.message, [super.exception]);
}
