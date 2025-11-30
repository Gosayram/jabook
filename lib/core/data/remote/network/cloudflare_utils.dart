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

import 'package:dio/dio.dart';

/// Utility class for detecting CloudFlare protection.
///
/// This class provides methods to detect CloudFlare protection mechanisms
/// without attempting to bypass them.
class CloudFlareUtils {
  /// Private constructor to prevent instantiation.
  CloudFlareUtils._();

  /// Checks if a response indicates CloudFlare protection is active.
  ///
  /// Returns `true` if the response contains CloudFlare-specific indicators.
  static bool isCloudFlareProtected(Response response) {
    final headers = response.headers.map;
    final body = response.data.toString();

    // Check for CloudFlare-specific headers
    final hasCfHeader = headers.entries.any((entry) =>
        entry.key.toLowerCase().contains('cf') ||
        entry.key.toLowerCase().contains('cloudflare'));

    // Check for CloudFlare challenge page content
    final hasChallengeContent = body.contains('challenge') ||
        body.contains('CloudFlare') ||
        body.contains('cf-chl-b') ||
        body.contains('jschl_vc') ||
        body.contains('jschl_answer') ||
        body.contains('checking your browser') ||
        body.contains('please enable javascript') ||
        body.contains('attention required') ||
        body.contains('cf-chl-bypass');

    return hasCfHeader || hasChallengeContent;
  }

  /// Checks if HTML content indicates CloudFlare protection.
  ///
  /// Returns `true` if the HTML contains CloudFlare challenge indicators.
  static bool isCloudFlareHtml(String html) {
    final h = html.toLowerCase();
    return h.contains('checking your browser') ||
        h.contains('please enable javascript') ||
        h.contains('attention required') ||
        h.contains('cf-chl-bypass') ||
        h.contains('challenges.cloudflare.com') ||
        h.contains('cf-turnstile');
  }
}
