/// Base failure class for all domain errors
abstract class Failure {
  final String message;
  final Exception? exception;

  const Failure(this.message, [this.exception]);

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is Failure &&
        other.message == message &&
        other.exception == exception;
  }

  @override
  int get hashCode => message.hashCode ^ exception.hashCode;

  @override
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