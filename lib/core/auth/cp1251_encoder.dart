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

import 'package:jabook/core/logging/structured_logger.dart';
import 'package:windows1251/windows1251.dart';

/// Utility class for encoding strings to Windows-1251 (CP1251) encoding.
///
/// This class provides methods to encode strings to CP1251 format,
/// which is required for RuTracker authentication requests.
class Cp1251Encoder {
  /// Private constructor to prevent instantiation (static-only class).
  Cp1251Encoder._();

  /// Encodes a string to Windows-1251 (CP1251) bytes.
  ///
  /// The [text] parameter is the string to encode.
  ///
  /// Returns a list of bytes representing the encoded string.
  ///
  /// Throws [Exception] if encoding fails.
  static List<int> encodeToCp1251(String text) {
    final operationId =
        'cp1251_encode_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      // Use windows1251 package to encode
      // The package provides encode method that converts String to List<int>
      final encoded = windows1251.encode(text);

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      logger.log(
        level: 'debug',
        subsystem: 'auth',
        message: 'String encoded to CP1251',
        operationId: operationId,
        context: 'cp1251_encoding',
        durationMs: duration,
        extra: {
          'text_length': text.length,
          'encoded_length': encoded.length,
          'has_cyrillic': _hasCyrillic(text),
        },
      );

      return encoded;
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      logger.log(
        level: 'error',
        subsystem: 'auth',
        message: 'Failed to encode string to CP1251',
        operationId: operationId,
        context: 'cp1251_encoding',
        durationMs: duration,
        cause: e.toString(),
        extra: {
          'text_length': text.length,
          'text_preview':
              text.length > 50 ? '${text.substring(0, 50)}...' : text,
        },
      );
      rethrow;
    }
  }

  /// Encodes a string to Windows-1251 bytes and returns as Uint8List.
  ///
  /// Convenience method for cases where Uint8List is needed.
  static List<int> encode(String text) => encodeToCp1251(text);

  /// Checks if a string contains Cyrillic characters.
  ///
  /// Used for logging purposes to identify if encoding is necessary.
  static bool _hasCyrillic(String text) =>
      text.runes.any((rune) => rune >= 0x0400 && rune <= 0x04FF);

  /// Pre-encoded "Вход" (Login) button value in CP1251.
  ///
  /// This is the value that RuTracker expects for the login button.
  /// In Python parser: b'\xe2\xf5\xee\xe4'
  static List<int> get loginButtonBytes => [0xe2, 0xf5, 0xee, 0xe4];
}
