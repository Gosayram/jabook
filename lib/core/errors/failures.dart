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

/// @deprecated Use failures from core/infrastructure/errors/failures.dart instead.
/// This file is kept for backward compatibility and will be removed in a future version.
library;

export 'package:jabook/core/infrastructure/errors/failures.dart';

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
  const AuthFailure(
    super.message, [
    super.exception,
    this.captchaType,
    this.rutrackerCaptchaData,
    this.captchaUrl,
  ]);

  /// Creates an authentication failure that requires user action (login).
  const AuthFailure.loginRequired([Exception? exception])
      : captchaType = null,
        rutrackerCaptchaData = null,
        captchaUrl = null,
        super('Authentication required. Please login to RuTracker.', exception);

  /// Creates an authentication failure for expired session.
  const AuthFailure.sessionExpired([Exception? exception])
      : captchaType = null,
        rutrackerCaptchaData = null,
        captchaUrl = null,
        super('Session expired. Please login again.', exception);

  /// Creates an authentication failure for invalid credentials.
  const AuthFailure.invalidCredentials([Exception? exception])
      : captchaType = null,
        rutrackerCaptchaData = null,
        captchaUrl = null,
        super('Invalid username or password. Please try again.', exception);

  /// Creates an authentication failure that requires captcha verification.
  ///
  /// [captchaType] indicates the type of captcha required.
  /// [rutrackerCaptchaData] contains RuTracker captcha data if available.
  /// [captchaUrl] contains URL for CloudFlare challenge if applicable.
  const AuthFailure.captchaRequired(
    this.captchaType,
    this.rutrackerCaptchaData,
    this.captchaUrl, [
    Exception? exception,
  ]) : super('Site requires captcha verification', exception);

  /// Type of captcha required (if any).
  final dynamic captchaType;

  /// RuTracker captcha data (if captchaType is rutracker).
  final dynamic rutrackerCaptchaData;

  /// URL for CloudFlare challenge (if captchaType is cloudflare).
  final String? captchaUrl;
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
