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

import 'dart:convert';

import 'package:html/parser.dart' as html_parser;
import 'package:jabook/core/infrastructure/logging/structured_logger.dart';
import 'package:windows1251/windows1251.dart';

/// Type of captcha detected in the response.
enum CaptchaType {
  /// RuTracker's own captcha (image-based).
  rutracker,

  /// CloudFlare challenge (Turnstile or other interactive challenge).
  cloudflare,

  /// Unknown captcha type.
  unknown,
}

/// Data extracted from RuTracker captcha form.
class RutrackerCaptchaData {
  /// Creates a new RutrackerCaptchaData instance.
  RutrackerCaptchaData({
    required this.captchaImageUrl,
    required this.capSid,
    required this.capCodeFieldName,
  });

  /// URL of the captcha image.
  final String captchaImageUrl;

  /// Session ID for the captcha (hidden field cap_sid).
  final String capSid;

  /// Name of the captcha code input field (cap_code_*).
  final String capCodeFieldName;
}

/// Detects and extracts captcha information from RuTracker login page response.
class CaptchaDetector {
  /// Private constructor to prevent instantiation.
  CaptchaDetector._();

  /// Detects captcha type from response body.
  ///
  /// Returns [CaptchaType] based on the content of the response.
  static Future<CaptchaType> detectType(String htmlBody) async {
    final lowerBody = htmlBody.toLowerCase();

    // Check for CloudFlare challenge
    if (lowerBody.contains('cloudflare') ||
        lowerBody.contains('cf-challenge') ||
        lowerBody.contains('challenge-platform') ||
        lowerBody.contains('turnstile')) {
      return CaptchaType.cloudflare;
    }

    // Check for RuTracker captcha
    if (lowerBody.contains('cap_sid') ||
        lowerBody.contains('cap_code_') ||
        lowerBody.contains('static.rutracker.cc/captcha/')) {
      return CaptchaType.rutracker;
    }

    return CaptchaType.unknown;
  }

  /// Extracts RuTracker captcha data from HTML response.
  ///
  /// Parses the HTML to find:
  /// - Captcha image URL
  /// - cap_sid (session ID)
  /// - cap_code_* field name
  ///
  /// Returns [RutrackerCaptchaData] if found, null otherwise.
  static Future<RutrackerCaptchaData?> extractRutrackerCaptcha(
    dynamic responseBody,
    String baseUrl,
  ) async {
    final operationId =
        'captcha_extract_${DateTime.now().millisecondsSinceEpoch}';
    final logger = StructuredLogger();
    final startTime = DateTime.now();

    try {
      // Decode response body
      String htmlBody;
      if (responseBody is String) {
        htmlBody = responseBody;
      } else if (responseBody is List<int>) {
        try {
          htmlBody = windows1251.decode(responseBody);
        } on Exception {
          try {
            htmlBody = utf8.decode(responseBody);
          } on FormatException {
            htmlBody = String.fromCharCodes(responseBody);
          }
        }
      } else {
        htmlBody = responseBody.toString();
      }

      await logger.log(
        level: 'info',
        subsystem: 'captcha',
        message: 'Extracting RuTracker captcha data from HTML',
        operationId: operationId,
        context: 'captcha_extract',
        extra: {
          'html_length': htmlBody.length,
          'base_url': baseUrl,
        },
      );

      // Parse HTML
      final document = html_parser.parse(htmlBody);

      // Find captcha image
      final captchaImage = document.querySelector('img[src*="captcha/"]');
      String? captchaImageUrl;
      if (captchaImage != null) {
        final src = captchaImage.attributes['src'];
        if (src != null) {
          // Make absolute URL if relative
          if (src.startsWith('http://') || src.startsWith('https://')) {
            captchaImageUrl = src;
          } else if (src.startsWith('//')) {
            captchaImageUrl = 'https:$src';
          } else {
            captchaImageUrl = '$baseUrl$src';
          }
        }
      }

      // Find cap_sid hidden field
      final capSidInput = document.querySelector('input[name="cap_sid"]');
      String? capSid;
      if (capSidInput != null) {
        capSid = capSidInput.attributes['value'];
      }

      // Find cap_code_* input field
      final capCodeInputs =
          document.querySelectorAll('input[name^="cap_code_"]');
      String? capCodeFieldName;
      if (capCodeInputs.isNotEmpty) {
        capCodeFieldName = capCodeInputs.first.attributes['name'];
      }

      if (captchaImageUrl == null ||
          capSid == null ||
          capCodeFieldName == null) {
        await logger.log(
          level: 'warning',
          subsystem: 'captcha',
          message: 'Failed to extract all captcha data',
          operationId: operationId,
          context: 'captcha_extract',
          durationMs: DateTime.now().difference(startTime).inMilliseconds,
          extra: {
            'has_image': captchaImageUrl != null,
            'has_cap_sid': capSid != null,
            'has_cap_code_field': capCodeFieldName != null,
          },
        );
        return null;
      }

      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'info',
        subsystem: 'captcha',
        message: 'Successfully extracted RuTracker captcha data',
        operationId: operationId,
        context: 'captcha_extract',
        durationMs: duration,
        extra: {
          'captcha_image_url': captchaImageUrl,
          'cap_sid_length': capSid.length,
          'cap_code_field_name': capCodeFieldName,
        },
      );

      return RutrackerCaptchaData(
        captchaImageUrl: captchaImageUrl,
        capSid: capSid,
        capCodeFieldName: capCodeFieldName,
      );
    } on Exception catch (e) {
      final duration = DateTime.now().difference(startTime).inMilliseconds;
      await logger.log(
        level: 'error',
        subsystem: 'captcha',
        message: 'Failed to extract RuTracker captcha data',
        operationId: operationId,
        context: 'captcha_extract',
        durationMs: duration,
        cause: e.toString(),
      );
      return null;
    }
  }
}
