/// Base failure class for all domain errors
abstract class Failure implements Exception {
  /// Creates a new Failure instance.
  ///
  /// The [message] parameter describes the failure in detail.
  /// The optional [exception] parameter contains the original exception
  /// that caused this failure, if any.
  const Failure(this.message, [this.exception]);

  /// Human-readable description of the failure.
  final String message;

  /// Original exception that caused this failure, if available.
  final Exception? exception;

  @override
  /// Compares this Failure with another object for equality.
  ///
  /// Returns `true` if the other object is a Failure with the same
  /// message and exception, `false` otherwise.
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is Failure &&
        other.message == message &&
        other.exception == exception;
  }

  @override
  /// Computes a hash code for this Failure.
  ///
  /// The hash code is based on both the message and exception fields.
  int get hashCode => message.hashCode ^ (exception?.hashCode ?? 0);

  @override
  /// Returns a string representation of this Failure.
  ///
  /// The returned string includes the failure message and exception
  /// (if available) in a readable format.
  String toString() => 'Failure(message: $message, exception: $exception)';
}

/// Network-related failures
class NetworkFailure extends Failure {
  const NetworkFailure(super.message, [super.exception]);
}

/// Authentication-related failures
class AuthFailure extends Failure {
  const AuthFailure(super.message, [super.exception]);
}

/// Parsing-related failures
class ParsingFailure extends Failure {
  const ParsingFailure(super.message, [super.exception]);
}

/// Database-related failures
class DatabaseFailure extends Failure {
  const DatabaseFailure(super.message, [super.exception]);
}

/// Torrent-related failures
class TorrentFailure extends Failure {
  const TorrentFailure(super.message, [super.exception]);
}

/// Audio-related failures
class AudioFailure extends Failure {
  const AudioFailure(super.message, [super.exception]);
}

/// Settings-related failures
class SettingsFailure extends Failure {
  const SettingsFailure(super.message, [super.exception]);
}