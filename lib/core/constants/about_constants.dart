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

/// Constants for the About screen.
///
/// This class contains all URLs, email addresses, and other external links
/// used in the About screen. All values are centralized here for easy maintenance.
class AboutConstants {
  /// Private constructor to prevent instantiation.
  AboutConstants._();

  /// Base GitHub repository URL.
  static const String _githubBaseUrl = 'https://github.com/Gosayram/jabook';

  /// GitHub repository URL.
  static const String githubUrl = _githubBaseUrl;

  /// GitHub contributors page URL.
  static const String githubContributorsUrl =
      '$_githubBaseUrl/graphs/contributors';

  /// GitHub changelog URL.
  static const String changelogUrl = '$_githubBaseUrl/blob/main/CHANGELOG.md';

  /// GitHub issues URL.
  static const String issuesUrl = '$_githubBaseUrl/issues';

  /// GitHub license URL.
  static const String licenseUrl = '$_githubBaseUrl/blob/main/LICENSE';

  /// Telegram channel URL.
  static const String telegramUrl = 'https://t.me/jabook_audiobook';

  /// Developer email address (optional).
  ///
  /// Set to null if no email is available.
  static const String? developerEmail = null;

  /// Support/donation URL (optional).
  ///
  /// Set to null if no support page is available.
  static const String? supportUrl = null;

  /// 4PDA forum topic URL (optional).
  ///
  /// Set to null if no 4PDA topic is available.
  static const String? forum4PdaUrl = null;
}
