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

/// Configuration class for environment-specific settings.
///
/// This class provides access to configuration values that change
/// based on the build flavor (dev, stage, prod).
class AppConfig {
  /// Private constructor for singleton pattern.
  AppConfig._();

  /// Factory constructor to get the instance.
  factory AppConfig() => _instance;

  static final AppConfig _instance = AppConfig._();

  /// Gets the current build flavor.
  String get flavor =>
      const String.fromEnvironment('FLAVOR', defaultValue: 'dev');

  /// Gets whether this is a debug build.
  bool get isDebug => flavor == 'dev';

  /// Gets whether this is a stage build.
  bool get isStage => flavor == 'stage';

  /// Gets whether this is a production build.
  bool get isProd => flavor == 'prod';

  /// Gets whether this is a beta build.
  bool get isBeta => flavor == 'beta';

  /// Gets the API base URL for the current environment.
  String get apiBaseUrl {
    switch (flavor) {
      case 'dev':
        return 'https://dev-api.jabook.com';
      case 'stage':
        return 'https://stage-api.jabook.com';
      case 'beta':
        return 'https://api.jabook.com';
      case 'prod':
        return 'https://api.jabook.com';
      default:
        return 'https://dev-api.jabook.com';
    }
  }

  /// Gets the RuTracker base URL.
  /// This method is deprecated - use EndpointManager instead for dynamic mirror selection.
  @Deprecated(
      'Use EndpointManager.getActiveEndpoint() for dynamic mirror selection')
  String get rutrackerUrl =>
      'https://rutracker.net'; // Default fallback, but EndpointManager should be used

  /// Gets the logging level for the current environment.
  String get logLevel {
    switch (flavor) {
      case 'dev':
        return 'all'; // All logs
      case 'stage':
        return 'info'; // Info and above
      case 'beta':
        return 'info'; // Info and above
      case 'prod':
        return 'error'; // Error and above only
      default:
        return 'all';
    }
  }

  /// Gets whether analytics is enabled.
  bool get analyticsEnabled {
    switch (flavor) {
      case 'dev':
        return false;
      case 'stage':
        return true;
      case 'beta':
        return true;
      case 'prod':
        return true;
      default:
        return false;
    }
  }

  /// Gets whether crash reporting is enabled.
  bool get crashReportingEnabled {
    switch (flavor) {
      case 'dev':
        return false;
      case 'stage':
        return true;
      case 'beta':
        return true;
      case 'prod':
        return true;
      default:
        return false;
    }
  }

  /// Gets the app version for the current environment.
  String get appVersion {
    const version = String.fromEnvironment('VERSION', defaultValue: '1.0.0');
    return '$version-$flavor';
  }

  /// Gets the app name for the current environment.
  String get appName {
    switch (flavor) {
      case 'dev':
        return 'JaBook Dev';
      case 'stage':
        return 'JaBook Stage';
      case 'beta':
        return 'jabook beta';
      case 'prod':
        return 'JaBook';
      default:
        return 'JaBook';
    }
  }

  /// Gets the application ID for the current environment.
  String get appId {
    switch (flavor) {
      case 'dev':
        return 'com.jabook.app.jabook.dev';
      case 'stage':
        return 'com.jabook.app.jabook.stage';
      case 'beta':
        return 'com.jabook.app.jabook';
      case 'prod':
        return 'com.jabook.app.jabook';
      default:
        return 'com.jabook.app.jabook.dev';
    }
  }

  /// Gets the download directory path.
  String get downloadDirectory {
    switch (flavor) {
      case 'dev':
        return '/data/user/0/com.jabook.app.jabook.dev/downloads';
      case 'stage':
        return '/data/user/0/com.jabook.app.jabook.stage/downloads';
      case 'beta':
        return '/data/user/0/com.jabook.app.jabook/downloads';
      case 'prod':
        return '/data/user/0/com.jabook.app.jabook/downloads';
      default:
        return '/data/user/0/com.jabook.app.jabook.dev/downloads';
    }
  }

  /// Gets whether debug features are enabled.
  bool get debugFeaturesEnabled {
    switch (flavor) {
      case 'dev':
        return true;
      case 'stage':
        return false;
      case 'beta':
        return false;
      case 'prod':
        return false;
      default:
        return true;
    }
  }

  /// Gets the maximum concurrent downloads.
  int get maxConcurrentDownloads {
    switch (flavor) {
      case 'dev':
        return 1; // Limit downloads in dev
      case 'stage':
        return 3;
      case 'beta':
        return 5;
      case 'prod':
        return 5;
      default:
        return 1;
    }
  }

  /// Gets the cache size in MB.
  int get cacheSizeMB {
    switch (flavor) {
      case 'dev':
        return 50; // Smaller cache in dev
      case 'stage':
        return 200;
      case 'beta':
        return 500;
      case 'prod':
        return 500;
      default:
        return 50;
    }
  }
}
