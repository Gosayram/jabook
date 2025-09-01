/// Base failure class for all domain errors
abstract class Failure {

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
  int get hashCode => message.hashCode ^ exception.hashCode;

  @override
  /// Returns a string representation of this Failure.
  ///
  /// The returned string includes the failure message and exception
  /// (if available) in a readable format.
  String toString() => 'Failure(message: $message, exception: $exception)';
}

/// Network-related failures
class NetworkFailure extends Failure {
  /// Creates a new NetworkFailure instance.
  ///
  /// The [message] parameter describes the network-related failure.
  /// The optional [exception] parameter contains the original exception
  /// that caused this failure, if any.
  const NetworkFailure(super.message, [super.exception]);
}

/// Authentication-related failures
class AuthFailure extends Failure {
  /// Creates a new AuthFailure instance.
  ///
  /// The [message] parameter describes the authentication-related failure.
  /// The optional [exception] parameter contains the original exception
  /// that caused this failure, if any.
  const AuthFailure(super.message, [super.exception]);
}

/// Parsing-related failures
class ParsingFailure extends Failure {
  /// Creates a new ParsingFailure instance.
  ///
  /// The [message] parameter describes the parsing-related failure.
  /// The optional [exception] parameter contains the original exception
  /// that caused this failure, if any.
  const ParsingFailure(super.message, [super.exception]);
}

/// Database-related failures
class DatabaseFailure extends Failure {
  /// Creates a new DatabaseFailure instance.
  ///
  /// The [message] parameter describes the database-related failure.
  /// The optional [exception] parameter contains the original exception
  /// that caused this failure, if any.
  const DatabaseFailure(super.message, [super.exception]);
}

/// Torrent-related failures
class TorrentFailure extends Failure {
  /// Creates a new TorrentFailure instance.
  ///
  /// The [message] parameter describes the torrent-related failure.
  /// The optional [exception] parameter contains the original exception
  /// that caused this failure, if any.
  const TorrentFailure(super.message, [super.exception]);
}

/// Audio-related failures
class AudioFailure extends Failure {
  /// Creates a new AudioFailure instance.
  ///
  /// The [message] parameter describes the audio-related failure.
  /// The optional [exception] parameter contains the original exception
  /// that caused this failure, if any.
  const AudioFailure(super.message, [super.exception]);
}

/// Settings-related failures
class SettingsFailure extends Failure {
  /// Creates a new SettingsFailure instance.
  ///
  /// The [message] parameter describes the settings-related failure.
  /// The optional [exception] parameter contains the original exception
  /// that caused this failure, if any.
  const SettingsFailure(super.message, [super.exception]);
}